---
name: kiyori-office-core
description: 处理 Word/Excel/PPT/PDF 的总入口。任何涉及 .docx/.xlsx/.pptx/.pdf 的创建、读取、编辑、转换、校对任务，先读本文再动手。
---

# 办公文档处理总纲

## 0. 先做这三件事

1. `office_env_check` —— 确认所需 Tier 已就绪；缺失就先 `office_env_setup`，不要硬上。
2. 判断格式 → 读对应 Skill（`kiyori-docx` / `kiyori-xlsx` / `kiyori-pptx` / `kiyori-pdf`）。
3. 输入是已有文件 → 先 `*_outline` 或 `office_read(mode=outline)` 拿到结构和锚点，再决定改哪里。**不要凭猜测编辑。**

## 1. 任务路由

| 用户要什么 | 走哪条路 |
| --- | --- |
| 新建正式文档（报告/合同/方案） | 有模板 → `docx_from_template`；无模板 → `docx_create` |
| 修改已有 Word | `docx_outline` → `docx_edit` / `docx_find_replace` |
| 从 Markdown 出 PDF | `pdf_create(engine=pandoc)`，先确认 CJK 字体 |
| 数据表/财务模型 | `xlsx_write` → `xlsx_format` → **`xlsx_recalc`** |
| 做汇报 PPT | 有模板 → `pptx_template_fill`；无 → `pptx_create` |
| 读长 PDF | `office_read(mode=outline)` 拿导航，再按 `range` 精读 |
| 扫描件 | `pdf_to_images` 后自己看图（首选），或 T4 `pdf_ocr` |
| 格式互转 | `office_convert`，`engine` 必须显式指定 |

## 2. 交付前必做（不可跳过）

1. `office_validate` —— 有 issue 就修，不要交付。
2. 含公式的 xlsx：`xlsx_recalc`，`total_errors` 必须为 0。
3. `office_render_preview` 出图，然后用 `Tools.Files.read({ path, direct_image: true })` 逐页看一遍。
   重点看：文字溢出/被截断、元素重叠、中文方框、空占位符、残留 `{{变量}}` 或 Lorem ipsum。
4. 产物复制到用户可见位置（默认 `${KIYORI_DOWNLOAD_DIR}/Office/`），
   用 `Tools.Files.share` 或 `open` 交付，并在回答中给出完整路径。

## 3. 绝对不要做

- 不要用 QuickJS 手写/手改 OOXML XML。
- 不要在没跑 render preview 的情况下说「已完成」。
- 不要在工具报 `E_ENV_MISSING` 时改用「差不多的替代方案」蒙混过去——明确告诉用户缺什么、装什么、多大。
- 不要原地修改用户的原始文件，除非用户明确要求 `in_place=true`。

## 4. 常见错误码处理

| 错误码 | 处理方式 |
| --- | --- |
| `E_ENV_MISSING` | 按 `remedy` 安装缺失 Tier；不要静默降级 |
| `E_PATH_INVALID` | 路径越界或不存在；确认 `env` 与文件位置后重试 |
| `E_PATH_EXISTS` | 目标已存在；改名或显式 `overwrite=true` |
| `E_ANCHOR_NOT_FOUND` | 重新 `*_outline` 取锚点，不要盲改 |
| `E_VALIDATION_FAILED` | 按 `data.issues` 修正后重新生成 |
| `E_TIMEOUT` | 缩小范围或提高 `timeoutMs`；先检查暂存区状态 |
