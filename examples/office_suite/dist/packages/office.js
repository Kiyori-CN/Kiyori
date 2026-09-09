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
  "category": "Document",
  "tools": [
    {
      "name": "office_workflow_guide",
      "description": {
        "zh": "办公文档强制工作流（仅提示，无需实现）：先 office_env_check，再读对应 Skill；已有文件先 outline/read 取锚点；写公式的 xlsx 必须 xlsx_recalc；交付前 office_validate + office_render_preview 并用 direct_image 看图；不原地覆盖用户源文件；E_ENV_MISSING 时不得改用「差不多」的替代方案。",
        "en": "Mandatory office workflow (advice only): office_env_check first, then read the matching Skill; outline/read existing files before editing; formulas require xlsx_recalc; before delivery run office_validate + office_render_preview and inspect pages with direct_image; never overwrite user sources; never substitute an approximate engine after E_ENV_MISSING."
      },
      "parameters": [],
      "advice": true
    },
    {
      "name": "office_env_check",
      "description": {
        "zh": "探测 T1-T4 组件、版本、CJK 字体与磁盘余量；缺失组件按 remedy 处理。",
        "en": "Probe T1-T4 components, versions, CJK fonts, and free disk; follow remedy for missing parts."
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
            "zh": "环境分层 1-4",
            "en": "Tier 1-4"
          },
          "type": "number",
          "required": true
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
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
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
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
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
            "zh": "CJK 字体族",
            "en": "CJK font family"
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
    },
    {
      "name": "office_render_preview",
      "description": {
        "zh": "产物 → PDF → 分页 JPEG，返回 Android 路径；必须再用 direct_image 逐页看图。",
        "en": "Render output to PDF then per-page JPEG and return Android paths; inspect every page with direct_image."
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
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
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
            "zh": "路径所属环境，必须显式传入 android 或 linux，禁止推断",
            "en": "Path environment; must be explicitly android or linux, never inferred"
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
        }
      ]
    },
    {
      "name": "office_workspace_clean",
      "description": {
        "zh": "清理指定 task_id 的 Linux 暂存区。",
        "en": "Clean the Linux staging directory for a task_id."
      },
      "parameters": [
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
exports.office_env_check = bind("office_env_check");
exports.office_env_setup = bind("office_env_setup");
exports.office_read = bind("office_read");
exports.office_convert = bind("office_convert");
exports.office_render_preview = bind("office_render_preview");
exports.office_validate = bind("office_validate");
exports.office_diff = bind("office_diff");
exports.office_workspace_init = bind("office_workspace_init");
exports.office_workspace_clean = bind("office_workspace_clean");
