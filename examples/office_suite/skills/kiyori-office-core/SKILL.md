---
name: kiyori-office-core
description: 处理 Word/Excel/PPT/PDF 的总入口。任何涉及 .docx/.xlsx/.pptx/.pdf 的创建、读取、编辑、转换、校对任务，先读本文再动手。
---

# 办公文档处理总纲

## 0. 先做这三件事

1. `office_env_check` —— 确认所需 Tier 已就绪；缺失就先 `office_env_setup`，不要硬上。
2. 判断格式 → `office_read_guide(format=docx/xlsx/pptx/pdf)` 读取随插件分发的对应 Skill，无需手动导入。
3. 输入是已有文件 → 先 `*_outline` 或 `office_read(mode=outline)` 拿到结构和锚点，再决定改哪里。**不要凭猜测编辑。**

## 1. 任务路由

| 用户要什么 | 走哪条路 |
| --- | --- |
| 新建正式文档（报告/合同/方案） | 有模板 → `docx_from_template`；无模板 → `docx_create` |
| 修改已有 Word | `docx_outline` → `docx_edit` / `docx_find_replace` |
| 从 Markdown 出 PDF | `pdf_create(engine=reportlab)` 直接排版；`engine=pandoc` 需 T2+T4 与 CJK 字体 |
| 数据表/财务模型 | `xlsx_write` → `xlsx_format` → 按需 `xlsx_chart` → **`xlsx_recalc`** |
| 做汇报 PPT | 有模板 → `pptx_template_fill`；无 → `pptx_create` |
| 读长 PDF | `office_read(mode=outline)` 拿导航，再按 `range` 精读 |
| 扫描件 | `pdf_to_images` 后用 `direct_image` 查看；当前套件未提供 OCR 工具，不把图像识别推断当成准确转录 |
| 格式互转 | `office_convert`，`engine` 必须显式指定 |

## 2. 交付前必做（不可跳过）

1. 含公式的 xlsx：全部编辑完成后 `xlsx_recalc`，`total_errors` 与 `missing_cache_count` 必须为 0。
2. `office_validate(strict=true)` —— 有 issue 就按代码和位置修复；完整清单在 `data.issues`，失败主消息也提供定位。
3. `office_render_preview` 自动附加 `data.visual_pages[].image` 多模态图像，按 `page` 逐页看一遍。
   重点看：文字溢出/被截断、元素重叠、中文方框、空占位符、残留 `{{变量}}` 或 Lorem ipsum。
   返回 `images_attached_review_required` 只表示图片已附加，必须实际看图才能宣称视觉检查通过；OCR、字符画、颜色均值和几何报告都不等于看过图。
4. 产物复制到用户可见位置（默认 AI 产物保存位置的 `office/<task_id>/`，两端各自取本环境根目录），
  用 `Tools.Files.share` 或 `open` 交付，并在回答中给出完整路径。

## 路径、安装与真实状态

- 文件工具显式传 `env=android` 或 `env=linux`，它指定输入文件位置，**引擎始终运行在本机 Ubuntu**。手机输入先暂存到 Ubuntu；`output_env` 独立决定输出位置。返回的 `artifacts[].env/path` 是后续调用依据。多步编辑优先 `output_env=linux` 并指定工作区输出路径，最终交付才搬到 Android。
- `office_env_setup(tier=...)` 先返回计划；只有用户授权安装后才传 `confirm=true`。确认会在可见终端执行并复检，检查 `success` 与 `data.completed`；失败保留终端，禁止自动重试。
- T4 的 apt 包名是 `tesseract-ocr` / `texlive-xetex`，实际二进制是 `tesseract` / `xelatex`。Tier 完整表示所列组件探测通过，不等于全部语言包、OCR 准确率或中文 TeX 已验收。中文 Pandoc PDF 还需要 `texlive-lang-chinese` 提供 `xeCJK.sty`，T4 安装计划已包含它；包内路径可能是小写 `tex/xelatex/xecjk/`，应使用 `kpsewhich xeCJK.sty` 查找，不猜路径。
- TeX 安装的 `mktexlsr` / `updmap-sys` / `fmtutil --all` 可能持续数分钟；无新输出不表示死锁。先只读查看进程、锁和日志；不要用可能取消前台命令的超时等待来代替进度检查，不自动终止 apt/dpkg 或重装。安装空间是保守估计，以实际计划和磁盘状态为准。
- `fonts.system_font_families` 是 fontconfig 确认的系统中文字体族，供 XeLaTeX 选择；`fonts.reportlab_font_families` 是 ReportLab CID 字体。`font_families` 是兼容汇总，不可直接把第一项当作所有引擎的通用字体。只发现文件、尚未注册字体时按 `fontconfig_detail` 排查，不能从文件名猜族名。
- 结构校验不证明版式美观。预览默认最多 8 页，长文档使用 `pages` 分批，记录已检查页码；缺少引擎或现场检查时标为待验证。
- 预览图最长边限制为 2048。公式或密集图表细看时，指定 `region={left,top,width,height}`（0-1 页面归一化坐标）和较高 `dpi`，获取局部图；分页与局部图分别记录覆盖范围。`remaining_pages` 表示本次未覆盖页，不是整个任务累计未检查页。
- `Tools.Files.read({path,environment:"linux",direct_image:true})` 可以直接读取 Ubuntu/当前 Linux 文件提供者的图片；Android 使用 `environment:"android"`。显式视觉注册失败会报错，不替换成 OCR。模型本身及其当前配置必须支持图像输入。
- PPT 预览默认附版面报告；Word/PDF 可显式 `layout_report=true` 获取 PDF 字词坐标、字号与图片框（需要 pdfplumber），不把 PDF 字词框误认为原 Word 文本框。
- 不把 `xlsx_format` 放到最后一次重算之后：openpyxl 保存会清除已有公式缓存。所有写入完成后再重算，并检查错误数和缺失缓存数。
- 当前不支持原生批注/修订、OCR、组合图表或数据透视编辑。先说明能力边界；不要调用不存在的工具。
- 工具箱“办公文档”分为文件操作、存储管理、环境与帮助。支持手机文件选择、结构/校验/预览/转 PDF 和独立输出设置；预览可指定页码（一次最多 8 页），返回文档可继续选中检查；复杂生成与编辑通过 AI 工具完成。

## 工作文件与清理

- 实际根目录来自 Ubuntu `$HOME`，通常为 `/root/kiyori_office`。`runtime`、`runtime.zip`、`unzip_runtime.py` 是程序文件；`work/<task_id>/in` 是输入副本、`tmp` 是中间文件、`out` 是输出；参数文件也可能包含任务内容。
- 用 `office_workspace_status(offset=0, limit=20)` 扫描任务、占用量和真实路径，按 `next_offset` 翻页。不得将手机侧 rootfs 挂载路径直接当作 Ubuntu 路径。
- `office_workspace_clean` 必须传真实 `task_id`；默认 `scope=temporary`、不删除，只返回计划。先核对 `path/file_count/bytes/sample_files`，在已授权范围内传同一 `task_id/scope`、`confirm=true` 和返回的 `plan_token` 执行。`scope=temporary` 保留 out 与未知文件；`scope=task` 包含该任务全部输出。预览后文件变化须重新预览。
- 清理只覆盖工作任务，不删除 runtime、Android 已交付文件或 `office_workspace_init` 创建的 source/output/templates/assets 工作区。自建测试目录、ZIP 和交付文件需要另行按真实路径检查与清理。
- 同一任务从输入暂存到交付持有占用，不能并发处理或清理。超时/异常中断可能保留 `.leases/<task_id>`；先核实终端进程及产物状态，不直接删除占用标记或重试同一任务。目前不提供自动解锁入口。
- 环境检测、存储扫描和清理的调用参数放在 `control`，正常完成后移除；超时或移除失败返回路径/警告。文件选择器副本由宿主暂存，控制台只能移除本页记录的副本，不是原始文件。

## 3. 绝对不要做

- 不要用 QuickJS 手写/手改 OOXML XML。
- 不要在没跑 render preview 的情况下说「已完成」。
- 不要在工具报 `E_ENV_MISSING` 时改用「差不多的替代方案」蒙混过去——明确告诉用户缺什么、装什么、多大。
- 不要忽略预览图：T2 缺 `poppler-data` 时 `pdftoppm` 退出码为 0 却产出空白图，
   工具已在 `office_render_preview` / `pdf_to_images` 前置拦截；出现该错误先装 poppler-data。
- 不要原地修改用户的原始文件，除非用户明确要求 `in_place=true`。

## 4. 常见错误码处理

| 错误码 | 处理方式 |
| --- | --- |
| `E_ENV_MISSING` | 按 `remedy` 安装缺失 Tier；不要静默降级 |
| `E_PATH_INVALID` | 路径越界或不存在；确认 `env` 与文件位置后重试 |
| `E_PATH_EXISTS` | 目标已存在；改名或显式 `overwrite=true` |
| `E_ANCHOR_NOT_FOUND` | 重新 `*_outline` 取锚点，不要盲改 |
| `E_TEMPLATE_VAR_MISSING` | DOCX/PPTX 模板缺少变量；补齐 variables，或显式 strict=false 保留未填内容 |
| `E_FORM_FIELD_NOT_FOUND` | PDF 字段不存在；先 pdf_form_list 核对字段名，strict=false 仅忽略未知字段 |
| `E_VALIDATION_FAILED` | 按 `data.issues` 修正后重新生成 |
| `E_TIMEOUT` | 先检查进程、暂存区和产物；安装任务先确认 apt/dpkg 是否仍在运行，不能直接重试或取消 |
