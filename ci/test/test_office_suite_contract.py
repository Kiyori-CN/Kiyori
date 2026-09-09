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
from kiyori_office.argspec import load_schema  # noqa: E402


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

    def test_advice_tools_have_no_command(self) -> None:
        for tool in self.tools:
            if tool["advice"]:
                self.assertEqual(tool["command"], "", tool["name"])

    def test_write_formula_tools_point_to_recalc(self) -> None:
        by_name = {tool["name"]: tool for tool in self.tools}
        self.assertIn("xlsx_write", by_name)
        self.assertIn("xlsx_recalc", by_name)
        self.assertIn("xlsx_recalc", by_name["xlsx_write"].get("nextActions", []))

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
            self.assertEqual(resource["mime"], "inode/directory")

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
