---
name: kiyori-pdf
description: PDF 的读取、提取、页级操作、表单、水印、加解密、转图与生成。涉及 .pdf 的任务先读本文。
---

# PDF 处理

## 1. 标准流程

1. `pdf_info`：页数、尺寸、元数据、是否加密、是否有文本层、表单字段。
2. 读取：`pdf_extract`（`text` / `layout` / `tables`），支持页范围。
3. 页级操作：`pdf_merge` / `pdf_split` / `pdf_rotate` / `pdf_reorder` / `pdf_delete_pages`。
4. 生成：`pdf_create`，`engine` 必须显式指定。

## 2. 扫描件的正确路线

- 不要在设备上跑 MinerU/docling 这类需要 GPU 与数百 MB 模型的方案（Android arm64 不现实）。
- 首选：`pdf_to_images` 出图后由你自己用多模态能力读图，质量更高且零安装成本。
- 需要可搜索文本时才用 T4 `tesseract`。

## 3. 高频坑

- **中文缺 CJK 字体**会渲染成方框：`pdf_create` / `pdf_watermark` 生成前会探测并拦截（`E_ENV_MISSING`）。
  优先从 Android `/system/fonts` 复制字体到 Linux `~/.fonts` 再 `fc-cache -f`，不要无脑 apt 装几百 MB。
- ReportLab 内置字体没有 Unicode 上下标字形：用 `<sub>` / `<super>`；中文需注册 CJK 字体（工具已处理）。
- 加密 PDF 密码错误只报一次错，不要反复尝试。
- 删除页面时必须至少保留一页；重排必须给出完整排列。

## 4. 交付检查

- `office_validate`：PDF 头、页数、文本层与加密状态。
- `office_render_preview` / `pdf_to_images`：检查中文字形、水印位置、页面方向。
