"use strict";
/* METADATA
{
  "name": "pdf",
  "display_name": {
    "zh": "PDF 文档",
    "en": "PDF Documents"
  },
  "description": {
    "zh": "PDF 信息、提取、页级操作、表单、水印、加解密、转图与生成。",
    "en": "PDF info, extraction, page operations, forms, watermarks, encryption, images, and creation."
  },
  "enabledByDefault": false,
  "category": "Document",
  "tools": [
    {
      "name": "pdf_info",
      "description": {
        "zh": "页数、尺寸、元数据、是否加密、是否含文本层、表单字段数。",
        "en": "Page count, size, metadata, encryption, text layer, and form field count."
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
      "name": "pdf_extract",
      "description": {
        "zh": "文本/版面/表格提取，支持页范围；版面与表格需要 pdfplumber。",
        "en": "Extract text/layout/tables with page ranges; layout and tables need pdfplumber."
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
          "name": "range",
          "description": {
            "zh": "页范围，如 1-3,7",
            "en": "Page range such as 1-3,7"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "mode",
          "description": {
            "zh": "text/layout/tables",
            "en": "text/layout/tables"
          },
          "type": "string",
          "required": false
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
      "name": "pdf_merge",
      "description": {
        "zh": "按顺序合并多个 PDF。",
        "en": "Merge PDFs in order."
      },
      "parameters": [
        {
          "name": "paths",
          "description": {
            "zh": "至少两个 PDF 路径",
            "en": "At least two PDF paths"
          },
          "type": "array",
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
      "name": "pdf_split",
      "description": {
        "zh": "按页范围拆分成单页文件。",
        "en": "Split into single-page files by range."
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
          "name": "range",
          "description": {
            "zh": "页范围",
            "en": "Page range"
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
        }
      ]
    },
    {
      "name": "pdf_rotate",
      "description": {
        "zh": "按 90 度倍数旋转指定页。",
        "en": "Rotate selected pages by a multiple of 90 degrees."
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
          "name": "angle",
          "description": {
            "zh": "旋转角度",
            "en": "Rotation angle"
          },
          "type": "number",
          "required": true
        },
        {
          "name": "range",
          "description": {
            "zh": "页范围",
            "en": "Page range"
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
      "name": "pdf_reorder",
      "description": {
        "zh": "按完整排列重排页面。",
        "en": "Reorder pages using a complete permutation."
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
          "name": "order",
          "description": {
            "zh": "1..N 的排列",
            "en": "Permutation of 1..N"
          },
          "type": "array",
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
    },
    {
      "name": "pdf_delete_pages",
      "description": {
        "zh": "删除指定页，拒绝删除全部页面。",
        "en": "Delete pages; refuses to delete every page."
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
          "name": "range",
          "description": {
            "zh": "页范围",
            "en": "Page range"
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
    },
    {
      "name": "pdf_form_list",
      "description": {
        "zh": "枚举表单字段与取值。",
        "en": "Enumerate form fields and values."
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
      "name": "pdf_form_fill",
      "description": {
        "zh": "填写表单字段；strict=true 时未知字段直接失败。",
        "en": "Fill form fields; strict=true fails on unknown fields."
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
          "name": "values",
          "description": {
            "zh": "字段名到取值",
            "en": "Field name to value"
          },
          "type": "object",
          "required": true
        },
        {
          "name": "strict",
          "description": {
            "zh": "未知字段时失败",
            "en": "Fail on unknown fields"
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
      "name": "pdf_watermark",
      "description": {
        "zh": "添加水印；中文必须提供可用 CJK 字体，否则报 E_ENV_MISSING。",
        "en": "Add a watermark; Chinese text requires a usable CJK font or fails with E_ENV_MISSING."
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
          "name": "text",
          "description": {
            "zh": "水印文字",
            "en": "Watermark text"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "size",
          "description": {
            "zh": "字号",
            "en": "Font size"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "opacity",
          "description": {
            "zh": "透明度 0-1",
            "en": "Opacity 0-1"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "angle",
          "description": {
            "zh": "旋转角度",
            "en": "Rotation angle"
          },
          "type": "number",
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
      "name": "pdf_encrypt",
      "description": {
        "zh": "加密 PDF。",
        "en": "Encrypt a PDF."
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
          "name": "password",
          "description": {
            "zh": "密码",
            "en": "Password"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "algorithm",
          "description": {
            "zh": "加密算法",
            "en": "Encryption algorithm"
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
      "name": "pdf_decrypt",
      "description": {
        "zh": "解密 PDF；密码错误明确报错，不反复尝试。",
        "en": "Decrypt a PDF; a wrong password fails explicitly without retries."
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
          "name": "password",
          "description": {
            "zh": "密码",
            "en": "Password"
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
    },
    {
      "name": "pdf_to_images",
      "description": {
        "zh": "PDF 转 JPEG（pdftoppm）；扫描件优先出图后自己看。",
        "en": "Convert PDF to JPEG with pdftoppm; for scans prefer looking at rendered pages."
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
          "name": "range",
          "description": {
            "zh": "页范围",
            "en": "Page range"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "dpi",
          "description": {
            "zh": "DPI，默认 150",
            "en": "DPI; default 150"
          },
          "type": "number",
          "required": false
        },
        {
          "name": "max_pages",
          "description": {
            "zh": "最多页数",
            "en": "Max pages"
          },
          "type": "number",
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
        }
      ]
    },
    {
      "name": "pdf_create",
      "description": {
        "zh": "生成 PDF；engine 必须显式指定 reportlab/pandoc/weasyprint，中文会先探测 CJK 字体。",
        "en": "Create a PDF; engine must be explicitly reportlab/pandoc/weasyprint and CJK fonts are probed first."
      },
      "parameters": [
        {
          "name": "engine",
          "description": {
            "zh": "reportlab/pandoc/weasyprint",
            "en": "reportlab/pandoc/weasyprint"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "blocks",
          "description": {
            "zh": "ReportLab blocks：heading/title/paragraph/bullet/spacer/image/table；公式排版用显式 pandoc 源或 Word 转 PDF",
            "en": "ReportLab blocks: heading/title/paragraph/bullet/spacer/image/table; math via explicit pandoc source or Word-to-PDF conversion"
          },
          "type": "array",
          "required": false
        },
        {
          "name": "source_path",
          "description": {
            "zh": "外部引擎的源文件",
            "en": "Source file for external engines"
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
          "name": "cjk_font",
          "description": {
            "zh": "中文字体族；Pandoc 路线必须是 fonts.system_font_families 中的真实族名，不能传字体文件名或 STSong-Light",
            "en": "CJK font family; Pandoc requires an actual fonts.system_font_families entry, not a filename or STSong-Light"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "margin_cm",
          "description": {
            "zh": "页边距（cm）",
            "en": "Margin in cm"
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
 * 子包 pdf：仅做参数透传，格式逻辑全部在 Python 侧。
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
exports.pdf_info = bind("pdf_info");
exports.pdf_extract = bind("pdf_extract");
exports.pdf_merge = bind("pdf_merge");
exports.pdf_split = bind("pdf_split");
exports.pdf_rotate = bind("pdf_rotate");
exports.pdf_reorder = bind("pdf_reorder");
exports.pdf_delete_pages = bind("pdf_delete_pages");
exports.pdf_form_list = bind("pdf_form_list");
exports.pdf_form_fill = bind("pdf_form_fill");
exports.pdf_watermark = bind("pdf_watermark");
exports.pdf_encrypt = bind("pdf_encrypt");
exports.pdf_decrypt = bind("pdf_decrypt");
exports.pdf_to_images = bind("pdf_to_images");
exports.pdf_create = bind("pdf_create");
