"""office_suite ToolPkg 与 Python 运行时的契约一致性检查。

这一层不依赖设备：它保证「工具名 → Python 命令 → Schema」三者不会漂移，
并检查 manifest 的分发清单与资源声明。
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
PACKAGE_ROOT = REPO_ROOT / "examples" / "office_suite"
RUNTIME_ROOT = PACKAGE_ROOT / "resources" / "runtime"
SPEC_FILE = PACKAGE_ROOT / "spec" / "tools.json"
MANIFEST_FILE = PACKAGE_ROOT / "manifest.json"

sys.path.insert(0, str(RUNTIME_ROOT))

from kiyori_office import protocol  # noqa: E402
from kiyori_office.argspec import INTERNAL_FIELDS, load_schema  # noqa: E402

# env / output_env 由 JS 薄层消费（决定跨环境搬运），不会进入 Python args，
# 因此它们只出现在工具元数据，不出现在 Python schema。
JS_LAYER_FIELDS = frozenset({"env", "output_env"})


class OfficeSuiteContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = json.loads(SPEC_FILE.read_text(encoding="utf-8"))
        cls.manifest = json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
        cls.tools = cls.spec["tools"]

    def test_every_tool_command_exists_in_python_runtime(self) -> None:
        registered = set(protocol.commands())
        for tool in self.tools:
            command = tool["command"]
            if not command:
                continue
            self.assertIn(
                command,
                registered,
                "工具 %s 映射的 Python 命令不存在：%s" % (tool["name"], command),
            )

    def test_every_python_command_is_exposed_by_a_tool(self) -> None:
        exposed = {tool["command"] for tool in self.tools if tool["command"]}
        missing = sorted(set(protocol.commands()) - exposed)
        self.assertEqual(
            missing,
            [],
            "以下 Python 命令没有任何 ToolPkg 工具暴露：%s" % ", ".join(missing),
        )

    def test_every_command_has_a_registered_schema(self) -> None:
        for name, entry in protocol.commands().items():
            self.assertIsNotNone(
                load_schema(entry.schema), "命令 %s 缺少 JSON Schema" % name
            )

    def test_tool_descriptions_are_bilingual(self) -> None:
        for tool in self.tools:
            self.assertTrue(tool["description"]["zh"].strip(), tool["name"])
            self.assertTrue(tool["description"]["en"].strip(), tool["name"])
            for param in tool["params"]:
                self.assertTrue(param["description"]["zh"].strip(), tool["name"])
                self.assertTrue(param["description"]["en"].strip(), tool["name"])

    def test_path_tools_require_explicit_env(self) -> None:
        for tool in self.tools:
            if not tool["requiresEnv"] or tool["advice"]:
                continue
            names = [param["name"] for param in tool["params"]]
            self.assertIn("env", names, "工具 %s 缺少 env 参数" % tool["name"])

    def test_file_input_params_are_declared_for_staging(self) -> None:
        """所有接收文件路径的参数都必须在 inputPaths 中声明。

        JS 薄层只对 inputPaths 声明的字段做 Android→Linux 搬运与 allow_roots
        白名单注入；漏声明会让 Python 侧报「越出允许根目录」，真机表现为
        「同一路径有的工具能读、有的不能」。
        """

        path_param_names = {
            "path",
            "paths",
            "image_path",
            "from_path",
            "source_path",
            "template_path",
            "original_path",
            "left",
            "right",
        }
        for tool in self.tools:
            declared = set(tool.get("inputPaths") or [])
            for param in tool["params"]:
                name = param["name"]
                if name not in path_param_names:
                    continue
                self.assertIn(
                    name,
                    declared,
                    "工具 %s 的路径参数 %s 未在 inputPaths 中声明" % (tool["name"], name),
                )

    def test_workspace_dir_is_not_a_staged_input(self) -> None:
        by_name = {tool["name"]: tool for tool in self.tools}
        workspace = by_name["office_workspace_init"]
        # dir 指向「待创建」的目录，不能走输入暂存（暂存要求源文件已存在）；
        # Linux 工作区由 JS 层把 dir 加进 allow_roots 后交给 Python 创建。
        self.assertNotIn("dir", workspace.get("inputPaths") or [])
        self.assertIn("dir", [param["name"] for param in workspace["params"]])

    def test_advice_tools_have_no_command(self) -> None:
        for tool in self.tools:
            if tool["advice"]:
                self.assertEqual(tool["command"], "", tool["name"])

    def test_write_formula_tools_point_to_recalc(self) -> None:
        by_name = {tool["name"]: tool for tool in self.tools}
        self.assertIn("xlsx_write", by_name)
        self.assertIn("xlsx_recalc", by_name)
        self.assertIn("xlsx_recalc", by_name["xlsx_write"].get("nextActions", []))

    def test_tool_params_match_python_schema_properties(self) -> None:
        """工具声明的参数必须与 Python schema 完全一致，防止两层定义漂移。"""

        for tool in self.tools:
            command = tool["command"]
            if not command:
                continue
            schema = load_schema(protocol.commands()[command].schema)
            properties = set((schema or {}).get("properties", {}))
            declared = {
                param["name"]
                for param in tool["params"]
                if param["name"] not in JS_LAYER_FIELDS
            }
            self.assertEqual(
                declared - properties,
                set(),
                "工具 %s 声明了 schema 未登记的字段：%s"
                % (
                    tool["name"],
                    sorted(declared - properties),
                ),
            )
            extra = properties - declared - set(INTERNAL_FIELDS)
            self.assertEqual(
                extra,
                set(),
                "schema %s 存在工具未声明的字段：%s" % (command, sorted(extra)),
            )

    def test_required_params_match_python_schema_required(self) -> None:
        for tool in self.tools:
            command = tool["command"]
            if not command:
                continue
            schema = load_schema(protocol.commands()[command].schema) or {}
            declared_required = {
                param["name"]
                for param in tool["params"]
                if param["required"] and param["name"] not in JS_LAYER_FIELDS
            }
            schema_required = set(schema.get("required", []))
            self.assertEqual(
                declared_required,
                schema_required,
                "工具 %s 的 required 与 schema 不一致：仅工具=%s 仅 schema=%s"
                % (
                    tool["name"],
                    sorted(declared_required - schema_required),
                    sorted(schema_required - declared_required),
                ),
            )

    def test_manifest_distribution_and_resources(self) -> None:
        self.assertEqual(self.manifest["schema_version"], 2)
        includes = self.manifest["distribution"]["include"]
        for required in ("manifest.json", "dist/**", "resources/**"):
            self.assertIn(required, includes)
        self.assertTrue(self.manifest["subpackages"])
        for subpackage in self.manifest["subpackages"]:
            self.assertTrue((PACKAGE_ROOT / subpackage["entry"]).is_file(), subpackage["entry"])
        for resource in self.manifest["resources"]:
            self.assertTrue((PACKAGE_ROOT / resource["path"]).exists(), resource["path"])
            expected_mime = "inode/directory" if (PACKAGE_ROOT / resource["path"]).is_dir() else "text/markdown"
            self.assertEqual(resource["mime"], expected_mime)

    def test_error_codes_match_design_table(self) -> None:
        expected = {
            "E_ENV_MISSING",
            "E_PATH_INVALID",
            "E_PATH_EXISTS",
            "E_INPUT_SCHEMA",
            "E_FORMAT_UNSUPPORTED",
            "E_DOC_CORRUPT",
            "E_ANCHOR_NOT_FOUND",
            "E_VALIDATION_FAILED",
            "E_ENGINE_FAILED",
            "E_TIMEOUT",
            "E_BUDGET_EXCEEDED",
        }
        self.assertTrue(expected.issubset(set(protocol.ERROR_CODES)))


if __name__ == "__main__":
    unittest.main()
