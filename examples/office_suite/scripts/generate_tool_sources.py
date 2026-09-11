"""从 spec/tools.json 生成 src/shared/specs.ts 与 src/packages/*.ts。

单一事实来源是 `spec/tools.json`（工具名、双语描述、参数、命令映射与搬运规则）。
修改工具清单后执行：

    python -B examples/office_suite/scripts/generate_tool_sources.py

然后运行 tsc 与 `ci/test/test_office_suite_contract.py`。
"""

from __future__ import annotations

import json
import pathlib
import sys

PACKAGE_ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC_FILE = PACKAGE_ROOT / "spec" / "tools.json"
SRC_ROOT = PACKAGE_ROOT / "src"

GROUP_META = {
    "office": {
        "zh": "办公通用",
        "en": "Office Common",
        "desc_zh": "环境探测、统一读取、转换、预览、校验、差异与工作区管理。",
        "desc_en": "Environment probing, unified reading, conversion, preview, validation, diff, and workspace management.",
    },
    "docx": {
        "zh": "Word 文档",
        "en": "Word Documents",
        "desc_zh": "DOCX 结构读取、创建、模板填充、跨 run 查找替换与表格/图片/样式编辑。",
        "desc_en": "DOCX outline, creation, template fill, cross-run find/replace, tables, images, and styles.",
    },
    "xlsx": {
        "zh": "Excel 工作簿",
        "en": "Excel Workbooks",
        "desc_zh": "XLSX 读取、写入、格式、工作表操作与 LibreOffice 公式重算。",
        "desc_en": "XLSX read/write, formatting, sheet operations, and LibreOffice recalculation.",
    },
    "pptx": {
        "zh": "PowerPoint 演示",
        "en": "PowerPoint Decks",
        "desc_zh": "PPTX 结构读取、生成、模板填充、幻灯片结构维护与形状编辑。",
        "desc_en": "PPTX outline, generation, template fill, slide structure maintenance, and shape editing.",
    },
    "pdf": {
        "zh": "PDF 文档",
        "en": "PDF Documents",
        "desc_zh": "PDF 信息、提取、页级操作、表单、水印、加解密、转图与生成。",
        "desc_en": "PDF info, extraction, page operations, forms, watermarks, encryption, images, and creation.",
    },
}


def load_tools() -> list[dict]:
    payload = json.loads(SPEC_FILE.read_text(encoding="utf-8"))
    tools = payload.get("tools")
    if not isinstance(tools, list) or not tools:
        raise SystemExit("spec/tools.json 缺少 tools 数组")
    return tools


def render_specs(tools: list[dict]) -> str:
    lines = [
        "/**",
        " * 由 spec/tools.json 生成，请勿手改；修改后执行",
        " *   python -B examples/office_suite/scripts/generate_tool_sources.py",
        " */",
        "",
        'import { ToolSpec } from "./runtime";',
        "",
        "export type ParamMeta = {",
        "  name: string;",
        "  zh: string;",
        "  en: string;",
        '  type: "string" | "number" | "boolean" | "array" | "object";',
        "  required: boolean;",
        "};",
        "",
        "export type ToolMeta = {",
        "  name: string;",
        "  zh: string;",
        "  en: string;",
        "  params: ParamMeta[];",
        "  advice?: boolean;",
        "};",
        "",
        "export type ToolEntry = { meta: ToolMeta; spec: ToolSpec };",
        "",
        "export const OFFICE_TOOLS: Record<string, ToolEntry> = {",
    ]
    for tool in tools:
        lines.append("  %s: {" % json.dumps(tool["name"]))
        lines.append("    meta: {")
        lines.append("      name: %s," % json.dumps(tool["name"]))
        lines.append("      zh: %s," % json.dumps(tool["description"]["zh"]))
        lines.append("      en: %s," % json.dumps(tool["description"]["en"]))
        if tool.get("advice"):
            lines.append("      advice: true,")
        lines.append("      params: [")
        for param in tool["params"]:
            lines.append(
                "        { name: %s, zh: %s, en: %s, type: %s, required: %s },"
                % (
                    json.dumps(param["name"]),
                    json.dumps(param["description"]["zh"]),
                    json.dumps(param["description"]["en"]),
                    json.dumps(param["type"]),
                    "true" if param["required"] else "false",
                )
            )
        lines.append("      ]")
        lines.append("    },")
        lines.append("    spec: {")
        lines.append("      command: %s," % json.dumps(tool["command"]))
        if tool.get("params"):
            lines.append(
                "      params: [%s],"
                % ", ".join(json.dumps(param["name"]) for param in tool["params"])
            )
        if tool.get("inputPaths"):
            lines.append(
                "      inputPaths: [%s]," % ", ".join(json.dumps(item) for item in tool["inputPaths"])
            )
        if tool.get("outputKind") and tool["outputKind"] != "single":
            lines.append("      outputKind: %s," % json.dumps(tool["outputKind"]))
        lines.append("      defaultOutputName: %s," % json.dumps(tool["defaultOutputName"]))
        if not tool.get("requiresEnv", True):
            lines.append("      requiresEnv: false,")
        if tool.get("timeoutMs"):
            lines.append("      timeoutMs: %d," % int(tool["timeoutMs"]))
        lines.append("    }")
        lines.append("  },")
    lines.append("};")
    lines.append("")
    lines.append("export function toolNames(): string[] {")
    lines.append("  return Object.keys(OFFICE_TOOLS);")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def render_package(group: str, tools: list[dict]) -> str:
    meta = GROUP_META[group]
    metadata = {
        "name": group,
        "display_name": {"zh": meta["zh"], "en": meta["en"]},
        "description": {"zh": meta["desc_zh"], "en": meta["desc_en"]},
        "enabledByDefault": False,
        "category": "File",
        "tools": [
            {
                "name": tool["name"],
                "description": tool["description"],
                "parameters": tool["params"],
                **({"advice": True} if tool.get("advice") else {}),
            }
            for tool in tools
        ],
    }
    lines = [
        "/* METADATA",
        json.dumps(metadata, ensure_ascii=False, indent=2),
        "*/",
        "",
        "/**",
        " * 子包 %s：仅做参数透传，格式逻辑全部在 Python 侧。" % group,
        " * 本文件由 scripts/generate_tool_sources.py 生成，请勿手改。",
        " */",
        "",
        'import { OFFICE_TOOLS } from "../shared/specs";',
        'import { safeRunOfficeTool } from "../shared/runtime";',
        "",
        "function bind(name: string) {",
        "  const entry = OFFICE_TOOLS[name];",
        "  if (!entry) {",
        "    throw new Error(`E_PROTOCOL: 未登记的工具 ${name}`);",
        "  }",
        "  return async (params?: Record<string, unknown>) => await safeRunOfficeTool(entry.spec, params);",
        "}",
        "",
    ]
    for tool in tools:
        if tool.get("advice"):
            continue
        lines.append('exports.%s = bind("%s");' % (tool["name"], tool["name"]))
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    tools = load_tools()
    grouped: dict[str, list[dict]] = {}
    for tool in tools:
        grouped.setdefault(tool["group"], []).append(tool)
    unknown = sorted(set(grouped) - set(GROUP_META))
    if unknown:
        print("未知子包分组：%s" % ", ".join(unknown), file=sys.stderr)
        return 1

    (SRC_ROOT / "shared").mkdir(parents=True, exist_ok=True)
    (SRC_ROOT / "shared" / "specs.ts").write_text(
        render_specs(tools), encoding="utf-8", newline="\n"
    )
    (SRC_ROOT / "packages").mkdir(parents=True, exist_ok=True)
    for group, group_tools in grouped.items():
        (SRC_ROOT / "packages" / ("%s.ts" % group)).write_text(
            render_package(group, group_tools), encoding="utf-8", newline="\n"
        )
    print("生成完成：%d 个工具 / %d 个子包" % (len(tools), len(grouped)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
