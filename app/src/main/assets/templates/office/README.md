# Kiyori Office 工作区

办公文档由 `com.kiyori.office_suite` ToolPkg + Linux `kiyori_office` Python 运行时处理。
格式逻辑一律在 Python 侧，QuickJS 只做参数校验与跨环境搬运。

## 目录骨架

```text
workspace/
├── source/       # 用户输入（只读；不要原地覆盖）
├── output/       # 产物（默认交付目录）
├── templates/    # 模板（docx/pptx/xlsx）
├── assets/       # 图片、字体、素材
└── AGENTS.md     # 工作区规则片段
```

## 使用顺序

1. `office_env_check` —— 确认 T1-T4 组件、CJK 字体与磁盘余量；缺失时先 `office_env_setup`。
2. 调用 `office_read_guide(format=core)`，再按格式读取 `docx/xlsx/pptx/pdf`。指引随插件内置，读取无需 Python 或手动导入 Skill。
3. 已有文件先 `docx_outline` / `xlsx_info` / `pptx_outline` / `pdf_info` 或 `office_read(mode=outline)` 取锚点。
4. 产物写 `output/`，不要原地覆盖 `source/`；需要原地编辑时显式传 `in_place=true`（仅 Linux 工作区）。
5. 表格按需用 `xlsx_format(range=...)` 分区排版、`xlsx_chart` 添加原生图表；全部编辑结束后，含公式的 xlsx 必须 `xlsx_recalc`，`total_errors` 与 `missing_cache_count` 均为 0 才可交付。
6. 交付前 `office_validate` + `office_render_preview`，再用 `Tools.Files.read({ path, direct_image: true })` 逐页看图。

## 路径与环境

- 所有路径工具必须显式传 `env`（`android` / `linux`），禁止推断。
- `output_env` 默认 `android`：产物回搬到 AI 产物保存位置的 `<android 根>/office/<task_id>/`；显式传 `linux` 时交付到 `<Ubuntu 根>/office/<task_id>/`。两端根目录以「AI 设置 → AI 产物保存位置」为准。
- 暂存区位于 Linux `~/kiyori_office/work/<task_id>/`，可用 `office_workspace_clean` 清理。
- 工具箱“办公文档”可查看环境、阅读指引、读取与预览文件；安装先查看计划，经授权后点击确认，执行过程保留在可见终端。

## 常见错误

| 错误码 | 处理 |
| --- | --- |
| `E_ENV_MISSING` | 按 `remedy` 安装对应 Tier；不要静默降级 |
| `E_PATH_INVALID` | 路径越界或不存在；确认 `env` 与文件位置 |
| `E_PATH_EXISTS` | 改名或显式 `overwrite=true` |
| `E_ANCHOR_NOT_FOUND` | 重新 outline 取锚点，不要盲改 |
| `E_VALIDATION_FAILED` | 按 `data.issues` 修正后重新生成 |
