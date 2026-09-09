---
name: kiyori-pdf
description: PDF 的读取、提取、页级操作、表单、水印、加解密、转图与生成。涉及 .pdf 的任务先读本文。
---

# PDF 处理

## 1. 标准流程

1. `pdf_info`：页数、尺寸、元数据、是否加密、是否有文本层、表单字段。
2. 读取：`pdf_extract`（`text` / `layout` / `tables`），`range` 省略时处理全部页，传 `1-3,8` 可限定范围。`tables` 需要 pdfplumber，返回 `pages`、`tables` 与 `table_count`；没有识别到表格时返回空列表，不等于页面没有表格，复杂布局仍需看图核对。
3. 页级操作：`pdf_merge` / `pdf_split` / `pdf_rotate` / `pdf_reorder` / `pdf_delete_pages`。
4. 生成：`pdf_create`，`engine` 必须显式指定。`blocks` 支持
   `heading` / `title` / `paragraph` / `bullet`（用 `items` 数组，或单个 `text`）/ `spacer`。

## 2. 扫描件的正确路线

- 不要在设备上跑 MinerU/docling 这类需要 GPU 与数百 MB 模型的方案（Android arm64 不现实）。
- 首选：`pdf_to_images` 出图后由你自己用多模态能力读图，质量更高且零安装成本。
- 需要可搜索文本时才用 T4 `tesseract`。

## 3. 高频坑

- **中文缺 CJK 字体**会渲染成方框：`pdf_create` / `pdf_watermark` 生成前会探测并拦截（`E_ENV_MISSING`）。
  优先从 Android `/system/fonts` 复制字体到 Linux `~/.fonts` 再 `fc-cache -f`，不要无脑 apt 装几百 MB。
- **缺 poppler-data 会让中文 PDF 预览静默变空白图**：`pdftoppm` 只打印
  `Missing language pack for 'Adobe-GB1' mapping` 并以 0 退出，产出的 JPEG 只有背景。
  `office_render_preview` / `pdf_to_images` 会在转图前拦截并报 `E_ENV_MISSING`；
  按 remedy 安装 `poppler-data`（Tier2）后重跑 `office_env_check`。
- ReportLab 内置字体没有 Unicode 上下标字形：用 `<sub>` / `<super>`；中文需注册 CJK 字体（工具已处理）。
- `office_convert(engine=pandoc,to_format=pdf)` 与 `pdf_create(engine=pandoc)` 共用已确认的系统中文字体族选择。自动选择优先 Noto Sans CJK SC；显式 `cjk_font` 必须来自 `office_env_check` 的 `fonts.system_font_families`。缺少 fontconfig 或可用字体族会报依赖错误；不要传 `NotoSansCJK-Regular.ttc` 等文件名，也不要传 CID 字体 `STSong-Light` 给 XeLaTeX。
- ReportLab 的中文创建和水印默认使用 `STSong-Light`；安装 Noto 不会自动改变它。系统可用字体不等于已注册的 ReportLab 字体，TTC/CFF 集合也不能直接当普通 TTF 使用；显式选择其他字体需核对该入口的支持及真实渲染，不承诺自动注册任意字体。
- 中文 Pandoc 路线需要 `xelatex`、`xeCJK.sty` 和实际中文字体。T4 计划包含 `texlive-lang-chinese`；排查样式包使用 `kpsewhich xeCJK.sty`。`tesseract` 安装后可由用户授权的脚本执行 OCR，但套件尚无原生 OCR 工具。
- 加密 PDF 密码错误只报一次错，不要反复尝试。
- 删除页面时必须至少保留一页；重排必须给出完整排列。

## 4. 交付检查

- `office_validate`：PDF 头、页数、文本层与加密状态。
- `office_render_preview` / `pdf_to_images`：检查中文字形、水印位置、页面方向。
