---
name: kiyori-docx
description: Word/.docx 的创建、模板填充、读取、编辑、查找替换与合并。涉及 .docx 的任务先读本文。
---

# DOCX 处理

## 1. 标准流程

1. `docx_outline`：拿到段落索引、样式、标题层级、表格坐标、章节与图片。
2. 选择编辑路径：
   - 改具体文字 → `docx_edit`（锚点 = `index` 或唯一 `text`）
   - 批量替换 → `docx_find_replace`（自动做跨 run 合并）
   - 模板填充 → `docx_from_template`（`{{变量}}`）
3. `office_validate` → `office_render_preview` → 用 `direct_image` 看图。

## 2. 高频坑

- **Word 会把一句话拆成多个 `<w:r>`**：不要按 run 逐段匹配文本，`docx_find_replace` 已内置 run 合并；
  但如果你自己判断「文档里有这句话」，请以 `docx_outline` 的段落文本为准。
- 锚点文本命中多处会报 `E_ANCHOR_NOT_FOUND`：改用 `anchor.index`，或显式 `allow_multiple=true`。
- 列宽要 `column_widths_cm` 与单元格宽度同时设置，否则 Word 只认其中一处。
- 插入图片的 `width_cm` 超过页宽时工具会交回 Word 默认缩放，不要期待「强行拉伸」。
- 模板变量缺失且 `strict=true` 会直接失败；确认业务字段齐全再调用。

## 3. 交付检查

- `office_validate`：确认关系/内容类型完整，目录域已刷新，修订标记完整。
- `office_render_preview`：逐页看分页、表格断行、图片位置、中文字形。
- 绝不覆盖用户源文件；`in_place=true` 只在用户明确要求时使用。
