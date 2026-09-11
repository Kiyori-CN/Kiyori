"use strict";
/* METADATA
{
  "name": "office",
  "display_name": {
    "zh": "办公通用",
    "en": "Office Common"
  },
  "description": {
    "zh": "环境探测、统一读取、转换、预览、校验、差异与工作区管理。",
    "en": "Environment probing, unified reading, conversion, preview, validation, diff, and workspace management."
  },
  "enabledByDefault": false,
  "category": "File",
  "tools": [
    {
      "name": "office_workflow_guide",
      "description": {
        "zh": "办公任务先调用 office_read_guide(format=core)，再读取对应格式的内置 Skill。先检查环境与输入结构，编辑后校验、预览并看图；按真实验收状态交付。",
        "en": "Start office tasks with office_read_guide(format=core), then the format-specific built-in Skill. Check environment and inputs, validate and preview edits, inspect images, and report verified delivery status."
      },
      "parameters": [],
      "advice": true
    },
    {
      "name": "office_read_guide",
      "description": {
        "zh": "读取随插件内置的办公 Skill，无需安装 Python；format=core/docx/xlsx/pptx/pdf。先读 core，再按实际格式读取。",
        "en": "Read the bundled office Skill without Python: format=core/docx/xlsx/pptx/pdf. Read core first, then the relevant format."
      },
      "parameters": [
        {
          "name": "format",
          "description": {
            "zh": "core/docx/xlsx/pptx/pdf，默认 core",
            "en": "core/docx/xlsx/pptx/pdf; default core"
          },
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "office_env_check",
      "description": {
        "zh": "探测 Python、T1-T4 组件与磁盘。T4 按实际二进制探测；fonts.system_font_families 为 fontconfig 确认的中文系统字体族，ReportLab CID 字体单独列出。",
        "en": "Inspect Python, Tier1-Tier4 components and disk space. Tier4 probes executable names; fonts.system_font_families contains fontconfig-confirmed Chinese families, separate from ReportLab CID fonts."
      },
      "parameters": [
        {
          "name": "verbose",
          "description": {
            "zh": "是否返回 PATH 与组件清单",
            "en": "Return PATH and component list"
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "office_env_setup",
      "description": {
        "zh": "返回安装计划；confirm=true 时用可见终端流式执行，失败保留现场不回滚。",
        "en": "Return an install plan; with confirm=true execute it in a visible terminal, keeping failures in place."
      },
      "parameters": [
        {
          "name": "tier",
          "description": {
            "zh": "环境分层 1-4，默认 1",
            "en": "Tier 1-4; default 1"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "components",
          "description": {
            "zh": "只安装指定组件",
            "en": "Install only these components"
          },
          "type": "array",
          "required": false
        },
        {
          "name": "confirm",
          "description": {
            "zh": "是否确认执行",
            "en": "Confirm execution"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "visible",
          "description": {
            "zh": "是否使用可见终端",
            "en": "Use a visible terminal"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "timeout_ms",
          "description": {
            "zh": "安装超时毫秒，默认 600000",
            "en": "Install timeout in ms; default 600000"
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "office_read",
      "description": {
        "zh": "统一读取 docx/xlsx/pptx/pdf/csv/md/html/txt；mode=outline 返回导航，禁止一次读爆上下文。",
        "en": "Read docx/xlsx/pptx/pdf/csv/md/html/txt uniformly; mode=outline returns navigation to protect context."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "mode",
          "description": {
            "zh": "outline/full/range",
            "en": "outline/full/range"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "range",
          "description": {
            "zh": "页码范围，如 1-3,7",
            "en": "Page range such as 1-3,7"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "max_chars",
          "description": {
            "zh": "输出字符预算，默认 20000",
            "en": "Character budget; default 20000"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "max_rows",
          "description": {
            "zh": "表格最大行数",
            "en": "Max table rows"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "sheet_name",
          "description": {
            "zh": "工作表名",
            "en": "Sheet name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "layout",
          "description": {
            "zh": "PDF 是否保留版面（需要 pdfplumber）",
            "en": "Keep PDF layout (requires pdfplumber)"
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
        },
        {
          "name": "with_anchors",
          "description": {
            "zh": "是否返回导航锚点，默认 true",
            "en": "Return navigation anchors; default true"
          },
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "office_convert",
      "description": {
        "zh": "格式转换；engine 必须显式指定 libreoffice 或 pandoc，缺失时明确报错不切换引擎。",
        "en": "Convert formats; engine must be explicitly libreoffice or pandoc; missing engines fail instead of switching."
      },
      "parameters": [
        {
          "name": "from_path",
          "description": {
            "zh": "源文件路径",
            "en": "Source file path"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "to_format",
          "description": {
            "zh": "目标格式，如 pdf/docx",
            "en": "Target format such as pdf/docx"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "engine",
          "description": {
            "zh": "libreoffice 或 pandoc",
            "en": "libreoffice or pandoc"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
          },
          "type": "string",
          "required": true
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
          "name": "options",
          "description": {
            "zh": "额外引擎参数数组",
            "en": "Extra engine options"
          },
          "type": "array",
          "required": false
        },
        {
          "name": "cjk_font",
          "description": {
            "zh": "中文字体族；Pandoc 路线必须是 fonts.system_font_families 中的真实族名，不能传字体文件名或 STSong-Light",
            "en": "CJK font family; Pandoc requires an actual fonts.system_font_families entry, not a filename or STSong-Light"
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
          "name": "timeout_ms",
          "description": {
            "zh": "转换超时毫秒，默认 600000",
            "en": "Conversion timeout in ms; default 600000"
          },
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "office_render_preview",
      "description": {
        "zh": "Word/PPT/PDF 转分页图片，直接附加多模态图像和页码；每次最多8页，需真正看图后验收。PPT 附结构版面诊断。",
        "en": "Render Word/PPT/PDF pages and attach multimodal images with page numbers, at most 8 per call; actual image review is required. PPT includes layout diagnostics."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
          "name": "pages",
          "description": {
            "zh": "页码范围，如 1-3",
            "en": "Page range such as 1-3"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "dpi",
          "description": {
            "zh": "渲染 DPI，默认 150",
            "en": "Render DPI; default 150"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "max_pages",
          "description": {
            "zh": "最多渲染页数，默认 8",
            "en": "Max pages; default 8"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "output_path",
          "description": {
            "zh": "产物目录；省略时写入交付目录",
            "en": "Output directory; defaults to the delivery directory"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "overwrite",
          "description": {
            "zh": "目标目录非空时是否覆盖，默认 false",
            "en": "Overwrite a non-empty target directory; default false"
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
        },
        {
          "name": "layout_report",
          "type": "boolean",
          "required": false,
          "description": {
            "zh": "默认 true，PPT 返回结构版面诊断",
            "en": "Default true: include structural layout diagnostics for PPT"
          }
        },
        {
          "name": "region",
          "type": "object",
          "required": false,
          "description": {
            "zh": "局部细看：left/top/width/height 为页面归一化 0-1 坐标；返回裁剪图，最长边2048",
            "en": "Detail crop: left/top/width/height normalized to 0-1 of page; image long edge capped at 2048"
          }
        }
      ]
    },
    {
      "name": "office_validate",
      "description": {
        "zh": "校验 OOXML 关系/内容类型/媒体引用/公式缓存/pptx sldIdLst/PDF 结构；strict=true 有问题直接失败。",
        "en": "Validate OOXML relationships, content types, media, formula caches, pptx sldIdLst, and PDF structure; strict=true fails on issues."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "original_path",
          "description": {
            "zh": "模板派生场景的基线文件",
            "en": "Baseline file for template-derived output"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "strict",
          "description": {
            "zh": "有问题时返回 E_VALIDATION_FAILED",
            "en": "Fail with E_VALIDATION_FAILED on issues"
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
      "name": "office_diff",
      "description": {
        "zh": "两个文档转文本后的差异对比。",
        "en": "Text-level diff between two documents."
      },
      "parameters": [
        {
          "name": "left",
          "description": {
            "zh": "左侧文件",
            "en": "Left file"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "right",
          "description": {
            "zh": "右侧文件",
            "en": "Right file"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "max_chars",
          "description": {
            "zh": "输出预算",
            "en": "Character budget"
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
      "name": "office_workspace_init",
      "description": {
        "zh": "创建 source/output/templates/assets 工作区骨架与 AGENTS.md 规则片段。",
        "en": "Create the source/output/templates/assets workspace skeleton plus an AGENTS.md rule snippet."
      },
      "parameters": [
        {
          "name": "dir",
          "description": {
            "zh": "工作区目录",
            "en": "Workspace directory"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "env",
          "description": {
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
        }
      ]
    },
    {
      "name": "office_workspace_status",
      "description": {
        "zh": "列出 Ubuntu 办公任务的真实路径、临时/输出占用和使用状态；不修改文件。区分引擎runtime、work任务和Android交付目录。",
        "en": "List Ubuntu office task paths, temporary/output usage and active state without modifying tasks. Distinguishes runtime, work and Android delivery."
      },
      "parameters": [
        {
          "name": "offset",
          "type": "number",
          "required": false,
          "description": {
            "zh": "分页起点，默认0",
            "en": "Page offset, default 0"
          }
        },
        {
          "name": "limit",
          "type": "number",
          "required": false,
          "description": {
            "zh": "每页任务数1-50，默认20",
            "en": "Page size 1-50, default 20"
          }
        }
      ]
    },
    {
      "name": "office_workspace_clean",
      "description": {
        "zh": "清理指定 Ubuntu 办公任务；默认仅预览。scope=temporary 保留输出，task 删除整个任务含输出；先预览，再传 confirm=true 与原 plan_token。不能清理运行时、Android交付或任意目录。",
        "en": "Preview cleanup of an Ubuntu office task. temporary preserves outputs; task deletes the whole task including outputs. Confirm using confirm=true and the returned plan_token. Runtime and external/Android outputs are excluded."
      },
      "parameters": [
        {
          "name": "task_id",
          "type": "string",
          "required": true,
          "description": {
            "zh": "待清理的真实任务ID；先用 office_workspace_status 查看",
            "en": "Existing task ID from office_workspace_status"
          }
        },
        {
          "name": "scope",
          "type": "string",
          "required": false,
          "description": {
            "zh": "temporary（默认）：清理in/tmp/参数；task：删除整个任务含out输出",
            "en": "temporary (default): inputs/tmp/args; task: whole task including out"
          }
        },
        {
          "name": "confirm",
          "type": "boolean",
          "required": false,
          "description": {
            "zh": "默认 false，只返回清理计划；用户确认精确清单后传 true",
            "en": "Defaults false: preview. True executes the reviewed plan"
          }
        },
        {
          "name": "plan_token",
          "type": "string",
          "required": false,
          "description": {
            "zh": "预览返回的 plan_token，确认时必需；目录变化需重新预览",
            "en": "Required when confirming; token from unchanged preview"
          }
        }
      ]
    }
  ]
}
*/
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 子包 office：仅做参数透传，格式逻辑全部在 Python 侧。
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
exports.office_read_guide = bind("office_read_guide");
exports.office_env_check = bind("office_env_check");
exports.office_env_setup = bind("office_env_setup");
exports.office_read = bind("office_read");
exports.office_convert = bind("office_convert");
exports.office_render_preview = bind("office_render_preview");
exports.office_validate = bind("office_validate");
exports.office_diff = bind("office_diff");
exports.office_workspace_init = bind("office_workspace_init");
exports.office_workspace_status = bind("office_workspace_status");
exports.office_workspace_clean = bind("office_workspace_clean");
