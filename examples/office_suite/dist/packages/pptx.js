"use strict";
/* METADATA
{
  "name": "pptx",
  "display_name": {
    "zh": "PowerPoint 演示",
    "en": "PowerPoint Decks"
  },
  "description": {
    "zh": "PPTX 结构读取、生成、模板填充、幻灯片结构维护与形状编辑。",
    "en": "PPTX outline, generation, template fill, slide structure maintenance, and shape editing."
  },
  "enabledByDefault": false,
  "category": "Document",
  "tools": [
    {
      "name": "pptx_outline",
      "description": {
        "zh": "幻灯片/形状/占位符/坐标/文本/母版与版式。",
        "en": "Slides, shapes, placeholders, coordinates, text, masters, and layouts."
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
          "name": "max_slides",
          "description": {
            "zh": "最多返回页数",
            "en": "Max slides"
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
      "name": "pptx_create",
      "description": {
        "zh": "从大纲生成 PPTX；可基于模板版式。",
        "en": "Create a PPTX from an outline, optionally based on a template."
      },
      "parameters": [
        {
          "name": "slides",
          "description": {
            "zh": "[{title,bullets,layout_index}]",
            "en": "[{title,bullets,layout_index}]"
          },
          "type": "array",
          "required": true
        },
        {
          "name": "template_path",
          "description": {
            "zh": "模板路径",
            "en": "Template path"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "layout_index",
          "description": {
            "zh": "默认版式索引",
            "en": "Default layout index"
          },
          "type": "number",
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
      "name": "pptx_template_fill",
      "description": {
        "zh": "基于模板填充 {{变量}}，保留模板设计。",
        "en": "Fill {{variables}} in a template while preserving its design."
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
      "name": "pptx_slide",
      "description": {
        "zh": "增/删/复制/重排幻灯片，维护关系与 <p:sldIdLst>。结构性操作必须在内容编辑之前完成。",
        "en": "Add/delete/duplicate/reorder slides while maintaining rels and <p:sldIdLst>. Finish structural changes before editing content."
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
          "name": "operation",
          "description": {
            "zh": "add/delete/duplicate/move",
            "en": "add/delete/duplicate/move"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "index",
          "description": {
            "zh": "0-based 页索引",
            "en": "0-based slide index"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "target_index",
          "description": {
            "zh": "move 目标位置",
            "en": "Target index for move"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "layout_index",
          "description": {
            "zh": "add 使用的版式",
            "en": "Layout for add"
          },
          "type": "number",
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
      "name": "pptx_edit",
      "description": {
        "zh": "编辑指定形状的文本/位置/尺寸/字体；先 pptx_outline 取锚点。",
        "en": "Edit a shape's text/position/size/font; call pptx_outline first."
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
          "name": "slide_index",
          "description": {
            "zh": "0-based 页索引",
            "en": "0-based slide index"
          },
          "type": "number",
          "required": true
        },
        {
          "name": "shape_index",
          "description": {
            "zh": "形状索引",
            "en": "Shape index"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "shape_name",
          "description": {
            "zh": "形状名",
            "en": "Shape name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "operation",
          "description": {
            "zh": "set_text/append_text/set_position/set_size/set_font",
            "en": "set_text/append_text/set_position/set_size/set_font"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "text",
          "description": {
            "zh": "文本",
            "en": "Text"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "size_pt",
          "description": {
            "zh": "字号",
            "en": "Font size"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "bold",
          "description": {
            "zh": "是否加粗",
            "en": "Bold"
          },
          "type": "boolean",
          "required": false
        },
        {
          "name": "color_rgb",
          "description": {
            "zh": "RGB 颜色",
            "en": "RGB color"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "left_emu",
          "description": {
            "zh": "左边距 EMU",
            "en": "Left EMU"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "top_emu",
          "description": {
            "zh": "上边距 EMU",
            "en": "Top EMU"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "width_emu",
          "description": {
            "zh": "宽度 EMU",
            "en": "Width EMU"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "height_emu",
          "description": {
            "zh": "高度 EMU",
            "en": "Height EMU"
          },
          "type": "number",
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
      "name": "pptx_notes",
      "description": {
        "zh": "演讲者备注读写。",
        "en": "Read or write speaker notes."
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
          "name": "operation",
          "description": {
            "zh": "read/write",
            "en": "read/write"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "slide_index",
          "description": {
            "zh": "0-based 页索引",
            "en": "0-based slide index"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "text",
          "description": {
            "zh": "备注文本",
            "en": "Notes text"
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
      "name": "pptx_media",
      "description": {
        "zh": "插入图片，未给尺寸时按页宽 80% 等比缩放。",
        "en": "Insert an image; without explicit size it scales to 80% of slide width."
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
          "name": "slide_index",
          "description": {
            "zh": "0-based 页索引",
            "en": "0-based slide index"
          },
          "type": "number",
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
          "name": "left_emu",
          "description": {
            "zh": "左边距 EMU",
            "en": "Left EMU"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "top_emu",
          "description": {
            "zh": "上边距 EMU",
            "en": "Top EMU"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "width_emu",
          "description": {
            "zh": "宽度 EMU",
            "en": "Width EMU"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "height_emu",
          "description": {
            "zh": "高度 EMU",
            "en": "Height EMU"
          },
          "type": "number",
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
      "name": "pptx_clean",
      "description": {
        "zh": "清理无引用内容；会删除不在 <p:sldIdLst> 中的 slide，必须在结构性操作之后调用。",
        "en": "Clean unreferenced content; deletes slides absent from <p:sldIdLst>, so call it after structural operations."
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
    }
  ]
}
*/
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 子包 pptx：仅做参数透传，格式逻辑全部在 Python 侧。
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
exports.pptx_outline = bind("pptx_outline");
exports.pptx_create = bind("pptx_create");
exports.pptx_template_fill = bind("pptx_template_fill");
exports.pptx_slide = bind("pptx_slide");
exports.pptx_edit = bind("pptx_edit");
exports.pptx_notes = bind("pptx_notes");
exports.pptx_media = bind("pptx_media");
exports.pptx_clean = bind("pptx_clean");
