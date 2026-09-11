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
  "category": "File",
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
        },
        {
          "name": "layout_report",
          "type": "boolean",
          "required": false,
          "description": {
            "zh": "返回分组、图层、越界、可能溢出与遮挡报告；不等于视觉验收",
            "en": "Report groups, z-order, bounds and possible overflow/overlap; not visual acceptance"
          }
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
            "zh": "元素支持 text/shape/image/table/chart/formula(omml)/icon；style/table_style/chart_style、theme、transition/animations 详见 office_read_guide(format=pptx)",
            "en": "Elements: text/shape/image/table/chart/formula(omml)/icon. See office_read_guide(format=pptx) for styling, theme, transitions and animations."
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
        },
        {
          "name": "slide_size_cm",
          "type": "object",
          "required": false,
          "description": {
            "zh": "幻灯片尺寸厘米 {width,height}；16:9 示例 {width:33.867,height:19.05}。",
            "en": "Slide dimensions in cm {width,height}; 16:9 example {width:33.867,height:19.05}."
          }
        },
        {
          "name": "theme",
          "type": "object",
          "required": false,
          "description": {
            "zh": "元素默认字体：font_name/size_pt/color_rgb/bold，元素字段覆盖默认值",
            "en": "Element font defaults: font_name/size_pt/color_rgb/bold; explicit element fields win"
          }
        }
      ]
    },
    {
      "name": "pptx_template_fill",
      "description": {
        "zh": "填充模板变量，递归覆盖组合对象和表格，保留 run 格式与软换行边界；替换值不递归展开。",
        "en": "Fill template variables recursively in groups and tables, preserving run formatting and soft-break boundaries without recursively expanding values."
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
      "name": "pptx_slide",
      "description": {
        "zh": "增/删/复制/重排幻灯片，维护关系与 <p:sldIdLst>。删除仍被页面或自定义放映引用的页会拒绝并定位；先完成结构操作再编辑内容。",
        "en": "Add/delete/duplicate/reorder slides while maintaining relationships. Deletion rejects and locates incoming slide or custom-show references. Finish structure before content edits."
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
        "zh": "增量编辑既有页面：添加元素、稳定ID定位、表格图表样式、图层分组、转场动画；无需重建整份。",
        "en": "Edit existing slides incrementally: add elements, stable IDs, table/chart styles, grouping, z-order, transitions and animations."
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
            "zh": "顶层唯一形状名；多处命中时报错，使用 shape_index 消除歧义",
            "en": "Unique top-level shape name; use shape_index if the name is ambiguous"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "operation",
          "description": {
            "zh": "set_text/append_text/set_position/set_size/set_font/add_elements/delete/set_style/set_table/set_chart/z_order/group/ungroup/set_transition/set_animations/replace_image",
            "en": "set_text/append_text/set_position/set_size/set_font/add_elements/delete/set_style/set_table/set_chart/z_order/group/ungroup/set_transition/set_animations/replace_image"
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
        },
        {
          "name": "theme",
          "type": "object",
          "required": false,
          "description": {
            "zh": "元素默认字体：font_name/size_pt/color_rgb/bold，元素字段覆盖默认值",
            "en": "Element font defaults: font_name/size_pt/color_rgb/bold; explicit element fields win"
          }
        },
        {
          "name": "shape_id",
          "type": "number",
          "required": false,
          "description": {
            "zh": "稳定对象ID，可定位组内对象；由 outline 返回",
            "en": "Stable object ID, including grouped children; returned by outline"
          }
        },
        {
          "name": "elements",
          "type": "array",
          "required": false,
          "description": {
            "zh": "元素支持 text/shape/image/table/chart/formula(omml)/icon；style/table_style/chart_style、theme、transition/animations 详见 office_read_guide(format=pptx)",
            "en": "Elements: text/shape/image/table/chart/formula(omml)/icon. See office_read_guide(format=pptx) for styling, theme, transitions and animations."
          }
        },
        {
          "name": "style",
          "type": "object",
          "required": false,
          "description": {
            "zh": "set_style：fill_rgb/gradient/opacity/shadow/line_rgb/line_width_pt/corner_radius",
            "en": "set_style: fill_rgb/gradient/opacity/shadow/line_rgb/line_width_pt/corner_radius"
          }
        },
        {
          "name": "table_style",
          "type": "object",
          "required": false,
          "description": {
            "zh": "set_table：表头/正文/斑马纹填充、边框、字体与行列尺寸；详见 pptx 指导",
            "en": "set_table: header/body/band fills, borders, fonts and row/column sizes; see pptx guide"
          }
        },
        {
          "name": "cells",
          "type": "array",
          "required": false,
          "description": {
            "zh": "set_table：[{row,column,text}]，0-based，仅修改指定单元格",
            "en": "set_table: [{row,column,text}], zero-based; updates only selected cells"
          }
        },
        {
          "name": "chart_style",
          "type": "object",
          "required": false,
          "description": {
            "zh": "set_chart：series_colors/point_colors/legend_position/data_labels/gridlines/number_format/minimum_scale/maximum_scale",
            "en": "set_chart: series_colors/point_colors/legend_position/data_labels/gridlines/number_format/minimum_scale/maximum_scale"
          }
        },
        {
          "name": "z_index",
          "type": "number",
          "required": false,
          "description": {
            "zh": "z_order：同一组内图层索引，0为底层",
            "en": "z_order: sibling stacking index, zero is the bottom"
          }
        },
        {
          "name": "shape_ids",
          "type": "array",
          "required": false,
          "description": {
            "zh": "group：连续顶层对象ID数组",
            "en": "group: IDs of consecutive top-level shapes"
          }
        },
        {
          "name": "group_name",
          "type": "string",
          "required": false,
          "description": {
            "zh": "group：新组合名称",
            "en": "group: new group name"
          }
        },
        {
          "name": "transition",
          "type": "object",
          "required": false,
          "description": {
            "zh": "set_transition：effect/speed/direction/advance_on_click/advance_after_ms",
            "en": "set_transition: effect/speed/direction/advance_on_click/advance_after_ms"
          }
        },
        {
          "name": "animations",
          "type": "array",
          "required": false,
          "description": {
            "zh": "set_animations：替换该页动画，空数组清除；fade/wipe/appear，详见指导",
            "en": "set_animations: replace slide animation list, empty clears; fade/wipe/appear; see guide"
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
          "name": "image_path",
          "type": "string",
          "required": false,
          "description": {
            "zh": "replace_image：替换图片内容，保留位置尺寸裁剪和对象身份",
            "en": "replace_image: replace content while retaining position, size, crop and identity"
          }
        },
        {
          "name": "font_name",
          "type": "string",
          "required": false,
          "description": {
            "zh": "set_font：中西文字体名",
            "en": "set_font: Latin and East Asian font family"
          }
        },
        {
          "name": "italic",
          "type": "boolean",
          "required": false,
          "description": {
            "zh": "set_font：斜体",
            "en": "set_font: italic"
          }
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "输入文件所在环境：android=手机文件，linux=Ubuntu路径；仅决定如何读取文件，办公引擎始终在本机Ubuntu执行。",
            "en": "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu."
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
            "zh": "图片宽度 EMU；只给宽度时等比推算高度，显式尺寸越界报错；1 cm=360000 EMU。",
            "en": "Width in EMU; height inferred proportionally if omitted. Explicit out-of-slide bounds rejected. 1 cm=360000 EMU."
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
      "name": "pptx_measure_text",
      "description": {
        "zh": "创建前估算文字高度与溢出风险；不读取字形，不能代替真实渲染。",
        "en": "Estimate text height and overflow risk before creation; heuristic, not font shaping or rendered proof."
      },
      "parameters": [
        {
          "name": "text",
          "type": "string",
          "required": true,
          "description": {
            "zh": "测量文字",
            "en": "text"
          }
        },
        {
          "name": "width_cm",
          "type": "number",
          "required": true,
          "description": {
            "zh": "文本框宽度 cm",
            "en": "width_cm"
          }
        },
        {
          "name": "height_cm",
          "type": "number",
          "required": false,
          "description": {
            "zh": "可选高度 cm",
            "en": "height_cm"
          }
        },
        {
          "name": "size_pt",
          "type": "number",
          "required": false,
          "description": {
            "zh": "字号 pt，默认18",
            "en": "size_pt"
          }
        },
        {
          "name": "margin_cm",
          "type": "number",
          "required": false,
          "description": {
            "zh": "内边距 cm，默认0.1",
            "en": "margin_cm"
          }
        },
        {
          "name": "line_spacing",
          "type": "number",
          "required": false,
          "description": {
            "zh": "行距倍数，默认1.2",
            "en": "line_spacing"
          }
        }
      ]
    }
  ]
}
*/

/**
 * 子包 pptx：仅做参数透传，格式逻辑全部在 Python 侧。
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

exports.pptx_outline = bind("pptx_outline");
exports.pptx_create = bind("pptx_create");
exports.pptx_template_fill = bind("pptx_template_fill");
exports.pptx_slide = bind("pptx_slide");
exports.pptx_edit = bind("pptx_edit");
exports.pptx_notes = bind("pptx_notes");
exports.pptx_media = bind("pptx_media");
exports.pptx_clean = bind("pptx_clean");
exports.pptx_measure_text = bind("pptx_measure_text");
