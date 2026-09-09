---
name: kiyori-xlsx
description: Excel/.xlsx 的读取、写入、区域格式化、原生图表、工作表操作与公式重算。涉及 .xlsx/.xlsm 的任务先读本文。
---

# XLSX 处理

## 1. 标准流程

1. `xlsx_info` 看工作表、命名区域、外部链接、是否含宏。
2. 读数据用 `xlsx_read`：同时返回公式与缓存值，`cached_value=null` 说明公式尚未重算。
3. 写入用 `xlsx_write`（值或公式）→ `xlsx_format` → 按需 `xlsx_chart` → **`xlsx_recalc`**。
   新建工作簿时 `sheet_name` 直接作为默认表名（不必先建再改名）；已有工作簿上
   `sheet_name` 必须已存在，否则报 `E_INPUT_SCHEMA`。
4. 交付前 `office_validate`，再 `office_render_preview` 看图。

## 2. 公式三条硬规则

1. **openpyxl 写出的公式没有缓存值。** 未经 `xlsx_recalc`，`data_only`、pandas 和大多数预览器读到的都是 `None`。
   写公式后必须 `xlsx_recalc`，`total_errors` 必须为 0，否则不得交付。
2. **溢出数组函数禁止使用**：`XLOOKUP/FILTER/SORT/UNIQUE/SEQUENCE/SORTBY/...`。
   openpyxl 产物没有 spill 元数据，LibreOffice 重算后只有左上角有值而错误计数仍为 0——这是静默错误。
   改用 `SUMIFS/INDEX/MATCH/IFERROR/SUMPRODUCT` 等 Excel 2007 级函数。
3. `TEXTJOIN/CONCAT/IFS/SWITCH/MAXIFS/MINIFS` 会写成 `_xlfn.` 前缀，工具已自动处理。

## 3. 其他高频坑

- 同批 `rows` 和 `cells` 的顺序固定为先行矩阵、后显式单元格；`cells` 覆盖矩阵的同位置值，`value:null` 可明确清空。`value` 与 `formula` 二选一；计数表示最终写入的唯一单元格，`overwritten_in_batch` 显示批内覆盖数。无需为避免 null 冲掉公式而分两次调用。
- 含图表、图片或 Excel Table 的工作表复制暂不支持，会在输出前明确拒绝；先复制基础表再向目标表添加对象。工作表重命名不自动重写跨表公式或外部引用；涉及依赖时先核对引用并显式修订，重算后检查结果。

- `.xlsm` 必须 `keep_vba=True`，否则宏丢失（工具已处理）；编辑输出保留 `.xlsm` 后缀。当前 `xlsx_recalc` 只处理 `.xlsx`，含宏模型不可宣称已重算验收。
- `data_only=True` 载入后保存会永久丢掉全部公式，保存路径已拦截。
- 表头/数据区分别调用 `xlsx_format(range="A1:D1", ...)`，避免整表使用一种数字格式或填充。`font` 只更新指定属性；列宽/行高/冻结窗格/自动筛选仍作用于工作表。
- `xlsx_chart` 原生图表支持 `column/bar/line/pie/scatter`；`data_range="A1:C8"` 第一行是系列表头，第一列是分类（散点图为数值 X），其余列为数值系列。`anchor="E2"` 决定位置；饼图只接受一个数值系列。通过 `title/width_cm/height_cm/style` 调整展示。
- 不要调用尚未实现的 `xlsx_aggregate`；先在授权的代码环境预聚合，再用 `xlsx_write` 写值。
- 最后一次保存（包括格式、工作表、图表修改）之后再重算；openpyxl 保存会清除已有公式缓存。
- `FORMULA_CACHE_INVALIDATED` 表示本次保存需要重新计算；它不是公式已被验证的信号。严格校验失败的主消息包含问题代码与位置，完整清单在 `data.issues`，不必为取详情重复跑 `strict=false`。

## 4. 交付检查

- `xlsx_recalc`：`total_errors == 0` 且 `missing_cache_count == 0`。
- `office_render_preview`：检查列宽截断、数字格式、图表与打印区域。
