---
name: kiyori-xlsx
description: Excel/.xlsx 的读取、写入、格式化、工作表操作与公式重算。涉及 .xlsx/.xlsm 的任务先读本文。
---

# XLSX 处理

## 1. 标准流程

1. `xlsx_info` 看工作表、命名区域、外部链接、是否含宏。
2. 读数据用 `xlsx_read`：同时返回公式与缓存值，`cached_value=null` 说明公式尚未重算。
3. 写入用 `xlsx_write`（值或公式）→ `xlsx_format` → **`xlsx_recalc`**。
4. 交付前 `office_validate`，再 `office_render_preview` 看图。

## 2. 公式三条硬规则

1. **openpyxl 写出的公式没有缓存值。** 未经 `xlsx_recalc`，`data_only`、pandas 和大多数预览器读到的都是 `None`。
   写公式后必须 `xlsx_recalc`，`total_errors` 必须为 0，否则不得交付。
2. **溢出数组函数禁止使用**：`XLOOKUP/FILTER/SORT/UNIQUE/SEQUENCE/SORTBY/...`。
   openpyxl 产物没有 spill 元数据，LibreOffice 重算后只有左上角有值而错误计数仍为 0——这是静默错误。
   改用 `SUMIFS/INDEX/MATCH/IFERROR/SUMPRODUCT` 等 Excel 2007 级函数。
3. `TEXTJOIN/CONCAT/IFS/SWITCH/MAXIFS/MINIFS` 会写成 `_xlfn.` 前缀，工具已自动处理。

## 3. 其他高频坑

- `.xlsm` 必须 `keep_vba=True`，否则宏丢失（工具已处理）。
- `data_only=True` 载入后保存会永久丢掉全部公式，保存路径已拦截。
- 列宽/行高/冻结窗格/自动筛选走 `xlsx_format`，不要自己拼 XML。
- 需要聚合时优先 `xlsx_aggregate` 思路（预聚合后写值），而不是动态数组公式。

## 4. 交付检查

- `xlsx_recalc`：`total_errors == 0` 且 `missing_cache_count == 0`。
- `office_render_preview`：检查列宽截断、数字格式、图表与打印区域。
