---
name: kiyori-docx
description: Word/.docx 的论文与报告排版、创建、模板填充、读取和编辑。涉及 .docx 的生成或修改时使用。
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

- `docx_merge` 支持正文、表格、图片、OMML 与原生图表；图表及嵌入工作簿复制到独立部件，避免多个源文件使用相同部件名而串改数据。编号、批注、脚注、正文分节及未知嵌入关系仍明确拒绝；同名异义样式需确认后显式用 `style_mode=unified`。合并后仍须校验并预览。
- **Word 会把一句话拆成多个 `<w:r>`**：不要按 run 逐段匹配文本，`docx_find_replace` 已内置 run 合并；
  但如果你自己判断「文档里有这句话」，请以 `docx_outline` 的段落文本为准。
- 锚点文本命中多处会报 `E_ANCHOR_NOT_FOUND`：改用 `anchor.index` 精确定位；`allow_multiple=true` 仅选择首个匹配，不表示批量修改。
- `docx_edit(replace/delete)` 遇到域、公式、图片、书签等结构会拒绝整段操作并给出段落索引；删除分节段落也会拒绝。改普通文字时使用 `docx_find_replace`，它保留域代码与显示缓存，不穿过超链接、图片、公式或书签边界。
- `insert_before/insert_after` 继承段落及首 run 格式，不复制分节、书签或原对象；插入后核对章节归属和页码。
- 跨 run 替换保留首个命中格式与末尾未命中文字，仅合并命中文字组；不改写未命中段落。先核对实际 `replacements`，它不包括域缓存。
- `docx_insert_image` 省略 `width_cm` 时按最后一节正文宽高等比限制；显式宽度超出正文区域会报错，不能靠工具静默缩放修正布局意图。
- 模板填充覆盖嵌套表格及已有默认/首页/偶数页页眉页脚，共享区域只处理一次；所有变量基于原始文本单次替换，值中的 `{{...}}` 保持字面值。`used_variables` 和 `replacements` 只统计实际填充。
- 模板变量缺失且 `strict=true` 会失败；标记位于域/链接或横跨结构边界时也会拒绝发布并返回位置。`strict=false` 保留未处理标记并报告警告，不代表完整填充。

## 3. 交付检查

- `office_validate`：检查关系/内容类型、正文书签重复与配对、内部超链接目标；目录域只提示刷新，不能证明页码、引用缓存或修订语义已经正确。
- `office_render_preview`：逐页看分页、表格断行、图片位置、中文字形。
- 绝不覆盖用户源文件；`in_place=true` 只在用户明确要求时使用。

## 4. 论文与正式报告排版

先读取用户提供的院校、期刊或公司模板，按其要求设置纸张、字体、标题、行距与页码。下例只是可调整的排版起点，不代表通用论文规范。`docx_create` 的 `spec.layout` 和 `docx_style` 使用同一配置；`spec` 与 `markdown` 二选一。

```json
{
  "spec": {
    "layout": {
      "page_setup": {"width_cm": 21, "height_cm": 29.7, "header_distance_cm": 1.5, "footer_distance_cm": 1.5},
      "margins_cm": {"top": 2.54, "bottom": 2.54, "left": 3, "right": 2.5},
      "default_font": {"name": "Times New Roman", "east_asia": "宋体", "size_pt": 12},
      "paragraph_styles": {
        "Normal": {"alignment": "justify", "line_spacing": 1.5, "first_line_indent_cm": 0.85, "space_after_pt": 0, "widow_control": true},
        "Title": {"east_asia": "黑体", "size_pt": 22, "alignment": "center", "first_line_indent_cm": 0},
        "Heading 1": {"east_asia": "黑体", "size_pt": 16, "bold": true, "first_line_indent_cm": 0, "space_before_pt": 12, "space_after_pt": 6, "keep_with_next": true},
        "Heading 2": {"east_asia": "黑体", "size_pt": 14, "bold": true, "first_line_indent_cm": 0, "keep_with_next": true}
      },
      "header_footer": {"header": {"text": "研究论文"}, "footer": {"text": "", "page_number": true, "total_pages": true}}
    },
    "blocks": [
      {"type": "title", "text": "论文题目"},
      {"type": "heading", "level": 1, "text": "1 研究方法"},
      {"type": "paragraph", "runs": [{"text": "正文中的"}, {"text": "关键概念", "bold": true}, {"text": "与论证。"}]},
      {"type": "table", "header": true, "rows": [["指标", "结果"], ["样本数", "120"]], "column_widths_cm": [7, 7]}
    ]
  }
}
```

- `name` 指西文字体，`east_asia` 指中文字体；字体需在实际渲染环境可用，指定名字不等于已安装。不要虚构论文引用或研究数据。
- 段落支持 `line_spacing` 倍数或 `line_spacing_pt` 固定磅，不能同时提供；`space_before_pt/space_after_pt`、`first_line_indent_cm/left_indent_cm/right_indent_cm` 允许显式 0。标题通常设置 `keep_with_next`，需要另起页时才设置 `page_break_before`。
- 文本块可用 `runs` 指定局部字体、字号、粗体、斜体和 `color_rgb`；`runs` 与 `text` 互斥。`format` 接受段落排版字段；不要用连续空格模拟居中或缩进。
- 列表 `bullet` / `number` 可用 `text` 或非空 `runs` 创建单项，也可用 `items` 非空字符串数组创建多项，例如 `{"type":"bullet","items":["研究目标","研究方法"]}`；每项生成独立列表段落，块级 `format` 应用于每项。`items` 与 `text/runs` 互斥，不接受空数组、非字符串项或未知块字段。
- 表格 `header=true` 会加粗首行并设置跨页重复表头；明确列宽会关闭自动调整。结构化表格块可设 `border_style=three_line` 生成真实三线表，或 `grid`。线宽和字号仍按院校/期刊要求检查，不把默认值当作规范。
- `header_footer.header/footer` 会替换默认页眉/页脚区域，适用于新文档或用户明确要求替换时；不改变首页/偶数页专用区域。`PAGE/NUMPAGES` 是真实 Word 域，初始缓存为占位值，须经 Word/LibreOffice 布局刷新并逐页预览。不要把缓存的“1 / 1”当最终页码。
- 内置 Markdown 创建只处理基础标题、列表和简单表格；复杂 Markdown、引用、数学公式、自动目录/脚注/参考文献应走明确的 Pandoc 或模板流程，验证后交付。当前工具不会自动处理文献规范和公式编号。

## 5. 在已有论文中插入和修改对象

`docx_outline` 的段落条目包含 `objects`，给出公式 OMML、图片和图表的 `object_index`，图表还返回有界的缓存系列。
先读现有内容再定位；`anchor={index:...}` 与段落内同类对象的 `object_index`（默认 0）共同定位。

- `docx_edit(operation=insert_blocks_before/insert_blocks_after,anchor=...,blocks=[...])` 在锚点前后插入多个块，原段落和其他复杂对象保持。所有插入块使用与 `docx_create.spec.blocks` 相同格式；图片和图表按锚点所在节检查正文宽高。
- `image` 块：`image_path`、可选 `width_cm/alignment/alt_text/caption`，保持图片比例并让图片与题注相邻。
- `chart` 块：原生可编辑柱状/条形/折线/饼图，提供 `categories/series`、可选 `chart_type/width_cm/height_cm/title/chart_style/caption`；每个图表拥有独立嵌入工作簿。`chart_style` 使用 PPT 指引中的同一合同。
- `formula` 块：`omml` 需要以 `m:oMath` 为根的 XML，可加 `number`；编号用真实居中/右对齐制表位，不靠空格模拟排版。`caption` 块支持 `label/number/text`，编号为调用方显式值，并非自动交叉引用域。
- `docx_edit(operation=set_formula,omml=...)` 仅替换指定公式，保留段落和其他对象；`replace_image(image_path=...)` 仅替换图片内容并保留框和裁剪。
- `docx_edit(operation=set_chart,chart_data={categories,series},chart_style={...})` 更新选中图表的数据及样式，独立复制可变图表/数据源，不串改共享引用。数据编辑仅支持单一柱状/条形/折线/饼图和内部 XLSX；外部链接、宏数据源、组合图拒绝改写。
- OMML 中的分式使用 `m:f/m:num/m:den`，上下标使用对应原生数学节点；不将 LaTeX 源码或公式截图冒充可编辑公式。拒绝 DTD、实体、外部关系和嵌入对象。复杂公式应保留原文件并分别检查 Word 与转换后的 PDF。

```xml
<m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
  <m:f><m:num><m:r><m:t>a+b</m:t></m:r></m:num><m:den><m:r><m:t>c</m:t></m:r></m:den></m:f>
</m:oMath>
```
