---
name: kiyori-pptx
description: PowerPoint/.pptx 的高质量可编辑布局、原生图表、生成、模板填充与形状编辑。制作或修改演示文稿时使用。
---

# PPTX 处理

## 1. 标准流程

1. `pptx_outline` 看幻灯片、版式、形状、占位符与坐标。
2. 结构性操作（增/删/复制/重排）先做完：`pptx_slide`。
3. 再编辑内容：`pptx_edit` / `pptx_notes` / `pptx_media`。
4. 最后 `pptx_clean` → `office_validate` → `office_render_preview` → 逐页看图。

## 2. 顺序规则（工具会强制提示）

- **先结构、后内容**：复制会保留源页当前内容。备注、图表及其嵌入工作簿使用独立副本，图片/母版等资源共享；复制后仍应回读确认复杂媒体与链接。
- `pptx_clean` 重新保存演示包，不会修复错误关系或补回被删内容；正常注册的幻灯片应保持数量和顺序。
- 有模板时优先 `pptx_template_fill`，保留模板设计；无模板才 `pptx_create`。
- 删除页若被其他页、母版、自定义放映或节列表引用，会返回 `E_FORMAT_UNSUPPORTED` 和 `incoming_references`，文件不变。先在支持导航编辑的软件中明确移除或重定向引用，再删除；工具不会自动改跳转目标。无入站引用的增删、移动和复制可以正常进行。
- 模板填充递归覆盖组合对象中的文本框/表格；软换行和自动域是边界，不会把边界两侧拼成变量。替换值中的 `{{...}}` 不递归展开；返回实际 `replacements`。

## 3. 质量规则

- 版式多样化，不要每页同一个 layout。
- 根据内容选择文本、图表或图片，不强行为每页添加装饰。字号以实际观看距离和预览可读性为准。
- 0.5" 最小边距；文字不得溢出容器。
- 沿用用户模板的颜色、字体与版式，避免无关装饰。
- 不要残留空占位符、`{{变量}}`、Lorem ipsum。

## 4. 交付检查

- `office_validate`：检查 `<p:sldIdLst>` 数量以及每个条目到实际页面的关系映射、重复页面目标和图表轴配对；不代表所有动画、复杂媒体或文字溢出已验收。
- `office_render_preview`：逐页检查溢出、重叠、字号与图片比例。

## 5. 从空白页构建可编辑设计

`pptx_create(slide_size_cm={width:33.867,height:19.05}, slides=[...])` 可指定 16:9 页面。每页可提供 `background_rgb`、`notes` 与 `elements`；`layout_index=6` 是内置空白版式，使用外部模板时先确认其版式索引。一个分析页示例：

```json
{
  "layout_index": 6,
  "background_rgb": "F5F7FA",
  "notes": "先说明口径，再解释趋势及限制。",
  "elements": [
    {"type": "text", "name": "title", "left_cm": 1.5, "top_cm": 1, "width_cm": 30, "height_cm": 2.2, "text": "核心结论应写在标题中", "font_name": "Noto Sans CJK SC", "size_pt": 30, "bold": true, "color_rgb": "18324A"},
    {"type": "text", "name": "insights", "left_cm": 1.5, "top_cm": 4, "width_cm": 12, "height_cm": 11, "font_name": "Noto Sans CJK SC", "size_pt": 22, "color_rgb": "223344", "space_after_pt": 12, "paragraphs": [{"text": "用具体证据支持结论", "bullet": true}, {"text": "写出适用条件与局限", "bullet": true}]},
    {"type": "chart", "name": "evidence", "left_cm": 15, "top_cm": 4, "width_cm": 17, "height_cm": 11, "chart_type": "column", "title": "示例数据：正式制作时替换为真实来源", "font_name": "Noto Sans CJK SC", "size_pt": 16, "categories": ["第一季", "第二季"], "series": [{"name": "指标", "values": [4, 7]}]}
  ]
}
```

- 元素位置尺寸使用 `left_cm/top_cm/width_cm/height_cm`，显式越界会报错；`name` 为页内唯一名称，后续 `pptx_edit(shape_name=...)` 精确定位。`pptx_outline` 返回索引、厘米/EMU 坐标、字体、图表系列与 `out_of_bounds`，但不能仅靠几何信息证明文字未溢出。
- `text`/`shape` 支持 `font_name/size_pt/bold/italic/color_rgb/alignment`；`paragraphs` 可用字符串或 `{text,bullet,level,...字体字段}`。支持 `space_before_pt/space_after_pt/line_spacing`、`margin_cm`、`vertical_alignment=top/middle/bottom`。不会自动缩小字号或删字；长内容拆页或重新分配区域。
- 创建元素时，`text:"第一行\n第二行"` 生成同一段内的 `<a:br>` 软换行，outline 以 `\u000b` 表示；需要两段或分别设置项目符号时使用 `paragraphs:["第一段","第二段"]`。这与 `pptx_edit(set_text)` 按 `\n` 生成独立段落的语义不同，调用时按所需结构选择。
- 形状元素写成 `{"type":"shape","shape":"rounded_rectangle",...}`；字段是 `shape`，不接受 `shape_type`。取值支持 `rectangle/rounded_rectangle/oval`，可设 `fill_rgb/line_rgb`；按信息关系选择图形。
- `table` 提供非空矩形 `rows`；`chart` 支持 `column/bar/line/pie`，`categories` 与各 `series[].values` 长度一致，饼图仅一个系列；均为 Office 可编辑对象。组合图、数据透视和复杂图表格式仍不在此入口范围。
- `pptx_media` 只指定宽或高时按比例补另一维；全省略时适配剩余页面区域。EMU 参数与厘米换算为 `1 cm = 360000 EMU`；显式越界会报错。
- `pptx_edit(set_text)` 保留首段/首个 run 的字体与外观，换行生成真实段落；这不等于保留多种混合 run 样式，需要精确保留时优先模板填充。
- 外部模板可能有重名形状；`shape_name` 多处命中会拒绝编辑并返回候选索引，改用 `shape_index` 定位顶层对象。组合对象内文本可用模板填充；当前 `pptx_edit` 不提供组内索引路径。
- 每页只承载一个清晰结论；统一内容边距、字号层级和颜色语义。图表标明单位、统计口径与来源；备注容纳细节。完成结构校验后逐页看预览，检查中文字体、遮挡、表格行高和图例可读性，再交付可编辑源文件与按需生成的 PDF。
