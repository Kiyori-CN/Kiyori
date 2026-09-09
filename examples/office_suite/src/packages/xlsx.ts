/* METADATA
{
  "name": "xlsx",
  "display_name": {
    "zh": "Excel 工作簿",
    "en": "Excel Workbooks"
  },
  "description": {
    "zh": "XLSX 读取、写入、格式、工作表操作与 LibreOffice 公式重算。",
    "en": "XLSX read/write, formatting, sheet operations, and LibreOffice recalculation."
  },
  "enabledByDefault": false,
  "category": "Document",
  "tools": [
    {
      "name": "xlsx_info",
      "description": {
        "zh": "工作表列表、使用范围、命名区域、外部链接、是否含宏。",
        "en": "Sheet list, used ranges, named ranges, external links, and macro presence."
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
      "name": "xlsx_read",
      "description": {
        "zh": "按范围读，同时返回公式与缓存值（两次 load）。",
        "en": "Read a range and return both formulas and cached values (two loads)."
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
          "name": "sheet_name",
          "description": {
            "zh": "工作表名",
            "en": "Sheet name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "range",
          "description": {
            "zh": "单元格范围，如 A1:C10",
            "en": "Cell range such as A1:C10"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "max_rows",
          "description": {
            "zh": "最大行数",
            "en": "Max rows"
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
      "name": "xlsx_write",
      "description": {
        "zh": "批量写单元格/区域；写入公式后必须调用 xlsx_recalc（溢出数组函数会被拒绝）。",
        "en": "Write cells/ranges in bulk; formulas require xlsx_recalc afterwards (spill functions are rejected)."
      },
      "parameters": [
        {
          "name": "path",
          "description": {
            "zh": "已有工作簿路径；省略时新建工作簿",
            "en": "Existing workbook path; omitted creates a new workbook"
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
          "name": "sheet_name",
          "description": {
            "zh": "工作表名",
            "en": "Sheet name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "cells",
          "description": {
            "zh": "[{cell,value|formula}]",
            "en": "[{cell,value|formula}]"
          },
          "type": "array",
          "required": false
        },
        {
          "name": "rows",
          "description": {
            "zh": "二维数组",
            "en": "2-D array"
          },
          "type": "array",
          "required": false
        },
        {
          "name": "start_cell",
          "description": {
            "zh": "rows 起点，默认 A1",
            "en": "Start cell for rows; default A1"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "file_name",
          "description": {
            "zh": "新建工作簿的默认文件名",
            "en": "Default file name for a new workbook"
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
      "name": "xlsx_format",
      "description": {
        "zh": "数字格式、字体、填充、列宽行高、冻结窗格、自动筛选。",
        "en": "Number formats, fonts, fills, widths/heights, freeze panes, and auto-filter."
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
          "name": "sheet_name",
          "description": {
            "zh": "工作表名",
            "en": "Sheet name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "number_format",
          "description": {
            "zh": "数字格式串",
            "en": "Number format string"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "font",
          "description": {
            "zh": "{name,size,bold,italic,color}",
            "en": "{name,size,bold,italic,color}"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "fill",
          "description": {
            "zh": "{type,color}",
            "en": "{type,color}"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "column_widths",
          "description": {
            "zh": "{列:宽度}",
            "en": "{column: width}"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "row_heights",
          "description": {
            "zh": "{行:高度}",
            "en": "{row: height}"
          },
          "type": "object",
          "required": false
        },
        {
          "name": "freeze_panes",
          "description": {
            "zh": "冻结窗格单元格",
            "en": "Freeze panes cell"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "auto_filter",
          "description": {
            "zh": "自动筛选范围",
            "en": "Auto-filter range"
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
      "name": "xlsx_sheet",
      "description": {
        "zh": "增/删/改名/排序/复制工作表。",
        "en": "Create, delete, rename, move, or copy sheets."
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
            "zh": "create/delete/rename/move/copy",
            "en": "create/delete/rename/move/copy"
          },
          "type": "string",
          "required": true
        },
        {
          "name": "name",
          "description": {
            "zh": "目标工作表名",
            "en": "Target sheet name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "new_name",
          "description": {
            "zh": "新名称",
            "en": "New name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "index",
          "description": {
            "zh": "位置索引",
            "en": "Position index"
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
      "name": "xlsx_recalc",
      "description": {
        "zh": "LibreOffice 重算并回写；total_errors 必须为 0 才可交付。",
        "en": "Recalculate with LibreOffice and write back; total_errors must be 0 before delivery."
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
    },
    {
      "name": "xlsx_table",
      "description": {
        "zh": "把区域转成 Excel Table 并设置样式。",
        "en": "Convert a range into an Excel Table with a style."
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
          "name": "sheet_name",
          "description": {
            "zh": "工作表名",
            "en": "Sheet name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "range",
          "description": {
            "zh": "表格范围",
            "en": "Table range"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "table_name",
          "description": {
            "zh": "表名",
            "en": "Table name"
          },
          "type": "string",
          "required": false
        },
        {
          "name": "style",
          "description": {
            "zh": "表格样式",
            "en": "Table style"
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
    }
  ]
}
*/

/**
 * 子包 xlsx：仅做参数透传，格式逻辑全部在 Python 侧。
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

exports.xlsx_info = bind("xlsx_info");
exports.xlsx_read = bind("xlsx_read");
exports.xlsx_write = bind("xlsx_write");
exports.xlsx_format = bind("xlsx_format");
exports.xlsx_sheet = bind("xlsx_sheet");
exports.xlsx_recalc = bind("xlsx_recalc");
exports.xlsx_table = bind("xlsx_table");
