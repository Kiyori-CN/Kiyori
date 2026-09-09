"use strict";
/* METADATA
{
  "name": "docx",
  "display_name": {
    "zh": "Word 文档",
    "en": "Word Documents"
  },
  "description": {
    "zh": "DOCX 结构读取、创建、模板填充、跨 run 查找替换与表格/图片/样式编辑。",
    "en": "DOCX outline, creation, template fill, cross-run find/replace, tables, images, and styles."
  },
  "enabledByDefault": false,
  "category": "Document",
  "tools": [
    {
      "name": "docx_outline",
      "description": {
        "zh": "返回段落索引/样式/层级/表格坐标/章节/图片；所有 DOCX 编辑的前置步骤。",
        "en": "Return paragraph indices, styles, levels, table coordinates, sections, and images; prerequisite for any DOCX edit."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "max_items",
          "description": {
            "zh": "最多返回段落数",
            "en": "Max paragraphs"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_create",
      "description": {
        "zh": "从结构化 spec 或 Markdown 生成 DOCX（python-docx）。",
        "en": "Create a DOCX from a structured spec or Markdown (python-docx)."
      },
      "parameters": [
        {
          "name": "spec",
          "description": {
            "zh": "结构化 blocks 数组",
            "en": "Structured blocks array"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "markdown",
          "description": {
            "zh": "Markdown 文本",
            "en": "Markdown text"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "file_name",
          "description": {
            "zh": "默认文件名",
            "en": "Default file name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_from_template",
      "description": {
        "zh": "{{变量}} 模板填充（业务文档首选路径）；strict=true 时缺失变量直接失败。",
        "en": "Fill {{variable}} placeholders (preferred for business documents); strict=true fails on missing variables."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "variables",
          "description": {
            "zh": "变量对象",
            "en": "Variable object"
          },
          "type": "object",
          "required": true
        },
        {
          "name": "strict",
          "description": {
            "zh": "缺失变量时失败",
            "en": "Fail on missing variables"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_edit",
      "description": {
        "zh": "基于锚点的 replace/insert_before/insert_after/delete；必须先 docx_outline 取锚点。",
        "en": "Anchor-based replace/insert_before/insert_after/delete; call docx_outline first."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "anchor",
          "description": {
            "zh": "{index} 或 {text}",
            "en": "{index} or {text}"
          },
          "type": "object",
          "required": true
        },
        {
          "name": "operation",
          "description": {
            "zh": "replace/insert_before/insert_after/delete",
            "en": "replace/insert_before/insert_after/delete"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "text",
          "description": {
            "zh": "写入文本",
            "en": "Text to write"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "in_place",
          "description": {
            "zh": "原地编辑（仅 Linux 工作区，仍需临时文件原子替换）",
            "en": "Edit in place (Linux workspace only; still atomic replacement)"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_find_replace",
      "description": {
        "zh": "跨 run 合并后的查找替换，保留格式；支持正则与表格范围。",
        "en": "Find/replace after merging runs, preserving formatting; supports regex and table scope."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "find",
          "description": {
            "zh": "查找内容",
            "en": "Find text"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "replace",
          "description": {
            "zh": "替换内容",
            "en": "Replacement"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "use_regex",
          "description": {
            "zh": "按正则解释 find",
            "en": "Treat find as regex"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "ignore_case",
          "description": {
            "zh": "忽略大小写",
            "en": "Ignore case"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "scope",
          "description": {
            "zh": "all/paragraphs/tables",
            "en": "all/paragraphs/tables"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "in_place",
          "description": {
            "zh": "原地编辑（仅 Linux 工作区，仍需临时文件原子替换）",
            "en": "Edit in place (Linux workspace only; still atomic replacement)"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_table",
      "description": {
        "zh": "追加表格；列宽与单元格宽度同单位同时设置。",
        "en": "Append a table; column and cell widths are set together in the same unit."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "rows",
          "description": {
            "zh": "二维数组",
            "en": "2-D array"
          },
          "type": "array",
          "required": true
        },
        {
          "name": "header",
          "description": {
            "zh": "首行是否为表头",
            "en": "Whether the first row is a header"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "style",
          "description": {
            "zh": "表格样式名",
            "en": "Table style name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "column_widths_cm",
          "description": {
            "zh": "列宽数组（cm）",
            "en": "Column widths in cm"
          },
          "type": "array",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "in_place",
          "description": {
            "zh": "原地编辑（仅 Linux 工作区，仍需临时文件原子替换）",
            "en": "Edit in place (Linux workspace only; still atomic replacement)"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_insert_image",
      "description": {
        "zh": "插入图片，自动按页宽约束。",
        "en": "Insert an image, constrained to the page width."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "image_path",
          "description": {
            "zh": "图片路径",
            "en": "Image path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "width_cm",
          "description": {
            "zh": "宽度（cm）",
            "en": "Width in cm"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "alignment",
          "description": {
            "zh": "left/center/right",
            "en": "left/center/right"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "in_place",
          "description": {
            "zh": "原地编辑（仅 Linux 工作区，仍需临时文件原子替换）",
            "en": "Edit in place (Linux workspace only; still atomic replacement)"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_style",
      "description": {
        "zh": "页面尺寸/页边距/默认字体/段落样式。",
        "en": "Page size, margins, default font, and paragraph styles."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "margins_cm",
          "description": {
            "zh": "{top,bottom,left,right}",
            "en": "{top,bottom,left,right}"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "default_font",
          "description": {
            "zh": "{name,size_pt}",
            "en": "{name,size_pt}"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "paragraph_styles",
          "description": {
            "zh": "样式名到配置的映射",
            "en": "Style name to config map"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "in_place",
          "description": {
            "zh": "原地编辑（仅 Linux 工作区，仍需临时文件原子替换）",
            "en": "Edit in place (Linux workspace only; still atomic replacement)"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_merge",
      "description": {
        "zh": "合并多个文档；style_mode 显式指定 preserve 或 unified。",
        "en": "Merge documents; style_mode must be explicitly preserve or unified."
      },
      "parameters": [
        {
          "name": "paths",
          "description": {
            "zh": "至少两个文件路径",
            "en": "At least two file paths"
          },
          "type": "array",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "style_mode",
          "description": {
            "zh": "preserve/unified",
            "en": "preserve/unified"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "file_name",
          "description": {
            "zh": "默认文件名",
            "en": "Default file name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物路径；省略时写入交付目录",
            "en": "Output path; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标已存在时是否覆盖，默认 false",
            "en": "Overwrite an existing target; default false"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "docx_extract_media",
      "description": {
        "zh": "导出内嵌图片到交付目录。",
        "en": "Export embedded images to the delivery directory."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "Linux 或 Android 文件路径",
            "en": "Linux or Android file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "output_env",
          "description": {
            "zh": "产物目标环境，默认 android（交付目录）；写入 Linux 时必须显式传 linux",
            "en": "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "target_dir_name",
          "description": {
            "zh": "输出目录名",
            "en": "Output directory name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "task_id",
          "description": {
            "zh": "复用同一个 Linux 暂存区",
            "en": "Reuse the same Linux staging directory"
          },
          "type": "string",
          "required": false
        }
      ]
    }
  ]
}
*/
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 子包 docx：仅做参数透传，格式逻辑全部在 Python 侧。
 * 本文件由 scripts/generate_tool_sources.py 生成，请勿手改。
 */
const specs_1 = require("../shared/specs");
const runtime_1 = require("../shared/runtime");
function bind(name) {
    const entry = specs_1.OFFICE_TOOLS[name];
    if (!entry) {
        throw new Error(`E_PROTOCOL: 未登记的工具 ${name}`);
    }
    return async (params) => await (0, runtime_1.safeRunOfficeTool)(entry.spec, params);
}
exports.docx_outline = bind("docx_outline");
exports.docx_create = bind("docx_create");
exports.docx_from_template = bind("docx_from_template");
exports.docx_edit = bind("docx_edit");
exports.docx_find_replace = bind("docx_find_replace");
exports.docx_table = bind("docx_table");
exports.docx_insert_image = bind("docx_insert_image");
exports.docx_style = bind("docx_style");
exports.docx_merge = bind("docx_merge");
exports.docx_extract_media = bind("docx_extract_media");
