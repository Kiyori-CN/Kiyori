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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "layout 与 blocks；支持段落、标题、表格(border_style=three_line)、image、chart(原生可编辑)、formula(omml)、caption",
            "en": "layout and blocks: paragraphs, headings, table(border_style=three_line), image, native editable chart, formula(omml), caption"
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
    },
    {
      "name": "docx_from_template",
      "description": {
        "zh": "单次填充 {{变量}}，保留跨 run 格式，覆盖嵌套表格及已有页眉页脚。值不递归展开；strict 拒绝缺失变量和受保护结构内的标记。",
        "en": "Fill {{variables}} once across runs, nested tables and existing headers/footers. Values are literal; strict mode rejects missing values and protected markers."
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
        "zh": "按段落锚点替换/插入/删除。整段操作保护域、公式、书签等结构；插入继承格式但不复制分节。普通局部文字优先 docx_find_replace。",
        "en": "Replace/insert/delete at a paragraph anchor. Whole-paragraph edits protect fields, equations and bookmarks; insertion inherits formatting without copying sections. Prefer docx_find_replace for local text."
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
            "zh": "replace/insert_before/insert_after/delete/insert_blocks_before/insert_blocks_after/set_formula/replace_image/set_chart",
            "en": "replace/insert_before/insert_after/delete/insert_blocks_before/insert_blocks_after/set_formula/replace_image/set_chart"
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
        },
        {
          "name": "blocks",
          "type": "array",
          "required": false,
          "description": {
            "zh": "锚点前/后插入结构化块：正文、标题、表格、image、formula(omml)、caption",
            "en": "Structured blocks before/after anchor: paragraphs, headings, tables, image, formula(omml), caption"
          }
        },
        {
          "name": "chart_data",
          "type": "object",
          "required": false,
          "description": {
            "zh": "set_chart：{categories,series:[{name,values}]}，更新缓存与嵌入工作簿",
            "en": "set_chart: {categories,series:[{name,values}]}; updates cache and embedded workbook"
          }
        },
        {
          "name": "object_index",
          "type": "number",
          "required": false,
          "description": {
            "zh": "段落内同类对象的0-based索引，默认0",
            "en": "object_index"
          }
        },
        {
          "name": "omml",
          "type": "string",
          "required": false,
          "description": {
            "zh": "set_formula：原生 m:oMath XML",
            "en": "omml"
          }
        },
        {
          "name": "image_path",
          "type": "string",
          "required": false,
          "description": {
            "zh": "replace_image：替换图片内容，保留位置和大小",
            "en": "image_path"
          }
        },
        {
          "name": "chart_style",
          "type": "object",
          "required": false,
          "description": {
            "zh": "set_chart：与PPT相同图表样式字段",
            "en": "chart_style"
          }
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "图片宽度厘米；省略时按最后一节正文宽高等比约束；显式宽度超出正文时报错。",
            "en": "Width in cm. Omitted size fits final section content area; explicit overflowing sizes are rejected."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "默认字体 {name,east_asia,size_pt,bold,italic,color_rgb}；name 为西文，east_asia 为中文字体。",
            "en": "Default font {name,east_asia,size_pt,bold,italic,color_rgb}; east_asia sets the CJK typeface."
          },
          "type": "object",
          "required": false
        },
        {
          "name": "paragraph_styles",
          "description": {
            "zh": "样式名到配置：字体字段及 alignment、line_spacing 倍数或 line_spacing_pt 固定磅、space_before_pt/space_after_pt、first_line_indent_cm/left_indent_cm/right_indent_cm、keep_with_next/keep_together/page_break_before/widow_control。",
            "en": "Style-name map: font, alignment, line_spacing OR line_spacing_pt, space_before_pt/space_after_pt, first_line_indent_cm/left_indent_cm/right_indent_cm, keep_with_next/keep_together/page_break_before/widow_control."
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
        },
        {
          "name": "page_setup",
          "type": "object",
          "required": false,
          "description": {
            "zh": "页面厘米参数：width_cm/height_cm/header_distance_cm/footer_distance_cm；A4 为 21×29.7。",
            "en": "Page dimensions/distances in cm: width_cm, height_cm, header_distance_cm, footer_distance_cm; A4 21×29.7."
          }
        },
        {
          "name": "header_footer",
          "type": "object",
          "required": false,
          "description": {
            "zh": "替换默认页眉/页脚区域（不改首页与偶数页区域）：{header:{text,alignment},footer:{text,alignment,page_number,total_pages}}；页码为 Word 域，须在排版引擎更新并预览。",
            "en": "Replace default header/footer regions only: {header:{text,alignment},footer:{text,alignment,page_number,total_pages}}. Page fields require layout-engine refresh and preview."
          }
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
          "name": "target_dir_name",
          "description": {
            "zh": "输出目录名",
            "en": "Output directory name"
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
    }
  ]
}
*/

/**
 * 子包 docx：仅做参数透传，格式逻辑全部在 Python 侧。
 * 本文件由 scripts/generate_tool_sources.py 生成，请勿手改。
 */

import { OFFICE_TOOLS } from "../shared/specs";
import { safeRunOfficeTool } from "../shared/runtime";

function bind(name: string) {
  const entry = OFFICE_TOOLS[name];
  if (!entry) {
    throw new Error(`E_PROTOCOL: 未登记的工具 ${name}`);
  }
  return async (params?: Record<string, unknown>) => await safeRunOfficeTool(entry.spec, params);
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
