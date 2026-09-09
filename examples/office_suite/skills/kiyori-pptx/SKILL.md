---
name: kiyori-pptx
description: PowerPoint/.pptx 的生成、模板填充、结构读取与形状编辑。涉及 .pptx 的任务先读本文。
---

# PPTX 处理

## 1. 标准流程

1. `pptx_outline` 看幻灯片、版式、形状、占位符与坐标。
2. 结构性操作（增/删/复制/重排）先做完：`pptx_slide`。
3. 再编辑内容：`pptx_edit` / `pptx_notes` / `pptx_media`。
4. 最后 `pptx_clean` → `office_validate` → `office_render_preview` → 逐页看图。

## 2. 顺序规则（工具会强制提示）

- **先结构、后内容**：复制幻灯片是逐字节复制，先编辑再复制会克隆已编辑内容。
- `pptx_clean` 会删除不在 `<p:sldIdLst>` 中的 slide，包括刚写的那张——必须在结构性操作之后调用。
- 有模板时优先 `pptx_template_fill`，保留模板设计；无模板才 `pptx_create`。

## 3. 质量规则

- 版式多样化，不要每页同一个 layout。
- 每页至少一个视觉元素；标题 36pt+ 与正文 14–16pt 形成尺寸对比。
- 0.5" 最小边距；文字不得溢出容器。
- 不用装饰性色条/标题下划线（AI 生成幻灯片的典型特征）。
- 不要残留空占位符、`{{变量}}`、Lorem ipsum。

## 4. 交付检查

- `office_validate`：`<p:sldIdLst>` 与 slide 数量一致、关系完整、图表轴成对。
- `office_render_preview`：逐页检查溢出、重叠、字号与图片比例。
