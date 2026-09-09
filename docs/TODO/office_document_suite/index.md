# 办公文档套件专项

状态：进行中（第一期 T1 闭环已实现并通过本地验证；T2/T3 依赖安装与真机验收待完成）。

## 1. 目标

让 Kiyori 的 Agent 能高质量生成与精确处理 Word / Excel / PowerPoint / PDF：

- 能力层：`com.kiyori.office_suite` ToolPkg，JS 只做参数校验、跨环境搬运、命令拼装与信封解析；
- 运行时层：可脱离 Kiyori 独立测试的 `kiyori_office` Python 包，全部格式逻辑在此；
- 知识层：五个可导入 Skill（`kiyori-office-core` / `-docx` / `-xlsx` / `-pptx` / `-pdf`）；
- 验收层：`office_validate` + `office_render_preview` + `direct_image` 视觉闭环。

## 2. 范围（第一期）

- ToolPkg：`manifest.json`（schema v2）+ `dist/main.js` + 五个子包（`office` / `docx` / `xlsx` / `pptx` / `pdf`）+ 目录资源 `resources/runtime/kiyori_office`。
- Python 运行时：`protocol.py` / `argspec.py` / `paths.py` / `budget.py` / `env.py` / `readers/` / `ops_*/` / `validate.py` / `render.py` / `convert.py` / `schemas/commands.json` / `tests/`。
- 工作区模板：`app/src/main/assets/templates/office/` 升级为骨架 + 规则说明。
- 内置打包接线：`tools/example_packages/packages_whitelist.txt` 已包含 `office_suite`。

## 3. 非目标（第一期）

- 不在设备上执行 apt/pip 安装：`office_env_setup` 只返回安装计划，实际执行由可见 PTY 完成。
- 不做 Compose DSL 控制台 UI（设计文档 §9 第 ④ 项，第二期）。
- 不做文件管理长按菜单接入（第二期）。
- 不修改 `terminal` 子模块，不新增宿主 API。
- 不做 OCR（T4）与 LaTeX 级排版。

## 4. 与设计文档的差异（源码核对结论）

| 设计文档 | 实际实现 | 原因 |
| --- | --- | --- |
| `schemas/*.json` 每个命令一个文件 | `schemas/commands.json` 集中登记 + `argspec.py` 自研校验 | 48 个碎片文件难以审计；校验器只覆盖实际用到的关键字，不引入 `jsonschema` 依赖 |
| `examples/office_suite/src/main.ts` + `packages/*.ts` | 已实现，`dist/` 为 tsc 产物 | 与 `examples/linux_ssh` 等现有包一致 |
| 内置 Skill 随 APK 预置 | 以 `examples/office_suite/skills/` 可导入目录交付 | `SkillManager` 只扫描用户 `Kiyori/skills`，未发现 APK 内置 Skill 扫描入口（见 §6） |
| `office_env_setup` 实际执行安装 | 只返回计划，`executed: false` | Python 侧不调用 apt/pip；执行由 JS 层可见 PTY 负责，保证可独立测试 |
| `pdf_ocr`（T4） | 未实现，`pdf_to_images` 已可用 | 设计文档把 T4 列为第三期 |

## 5. 已实现命令（48 条 Python 命令，49 个 ToolPkg 工具）

- `office`：`office_workflow_guide`（advice）、`office_env_check`、`office_env_setup`、`office_read`、`office_convert`、`office_render_preview`、`office_validate`、`office_diff`、`office_workspace_init`、`office_workspace_clean`。
- `docx`：`docx_outline`、`docx_create`、`docx_from_template`、`docx_edit`、`docx_find_replace`、`docx_table`、`docx_insert_image`、`docx_style`、`docx_merge`、`docx_extract_media`。
- `xlsx`：`xlsx_info`、`xlsx_read`、`xlsx_write`、`xlsx_format`、`xlsx_sheet`、`xlsx_recalc`、`xlsx_table`。
- `pptx`：`pptx_outline`、`pptx_create`、`pptx_template_fill`、`pptx_slide`、`pptx_edit`、`pptx_notes`、`pptx_media`、`pptx_clean`。
- `pdf`：`pdf_info`、`pdf_extract`、`pdf_merge`、`pdf_split`、`pdf_rotate`、`pdf_reorder`、`pdf_delete_pages`、`pdf_form_list`、`pdf_form_fill`、`pdf_watermark`、`pdf_encrypt`、`pdf_decrypt`、`pdf_to_images`、`pdf_create`。

设计文档中的 `xlsx_chart`、`xlsx_import`/`xlsx_export`、`xlsx_aggregate`、`docx_comment`、`docx_track_changes`、`pptx_thumbnail`、`pdf_stamp`、`pdf_compress`、`pdf_ocr` 属第二/三期，尚未实现。

## 6. 关键源码核对结论

- `Tools.System.terminal.hiddenExec(command, { executorKey, timeoutMs, localOnly })`：`localOnly` 表示显式本地 Ubuntu 启动器，office 运行时只存在于本地，因此固定 `localOnly: true`。
- `Tools.Files.copy(source, destination, recursive, sourceEnvironment, destEnvironment)` 支持跨环境复制，Android → Linux 与 Linux → Android 均已使用。
- `Tools.Files.write(path, content, append, environment)` 用于写 args.json 与 bootstrap 脚本；参数一律走 JSON 文件，不使用 `python -c`。
- `ToolPkg.readResource(key, outputFileName, internal)` 对 `mime: inode/directory` 的资源会先打 zip 再返回路径，因此运行时以目录资源分发，JS 侧解压到 `~/kiyori_office/runtime`。
- `Tools.Files.read({ path, direct_image: true })` 是视觉验收通道；`office_render_preview` 返回 Android 路径供其使用。
- `SkillManager` 扫描 `OperitPaths.skillsDir()`（用户 `Kiyori/skills`），只支持目录/ZIP/手动导入；本期不改宿主扫描机制。
- ToolPkg 资产生成任务为 `:app:generateBundledToolPkgAssets`，由 `tools/example_packages/packages_whitelist.txt` 驱动，`app/build.gradle.kts` 已接入 debug/release 变体。
- `linux_ssh` 等既有包使用 `Tools.System.terminal.hiddenExec`；office 额外固定 `localOnly: true`，避免文档被路由到未安装运行时的 SSH 目标。

## 7. 必须解决的已知真实故障

- DOCX 跨 run 文本：`ops_docx/runs.py` 先合并相邻同格式 run，再做整段匹配替换。
- XLSX 公式无缓存值：`xlsx_recalc` 用 LibreOffice 重算并回写，`total_errors` 非 0 时不给出交付建议。
- 溢出数组函数：`ops_xlsx/formula.py` 直接拒绝 `XLOOKUP/FILTER/SORT/UNIQUE/SEQUENCE` 等，并自动补 `_xlfn.` 前缀。
- `data_only=True` 保存丢公式：`ops_xlsx/write.py` 的 `open_workbook_for_write(data_only=True)` 直接报 `E_INPUT_SCHEMA`。
- `.xlsm` 宏丢失：`keep_vba=True`。
- PPTX 顺序约束：`pptx_slide` 返回 `ordering_note`，`pptx_clean` 说明会丢弃不在 `<p:sldIdLst>` 中的 slide。
- 中文 PDF 方框：`env.require_cjk_font_files` 在生成前拦截，优先从 `/system/fonts` 复制字体。
- ReportLab 上下标：文档要求用 `<sub>` / `<super>`；`pdf_create` 自动注册 CJK 字体。

## 8. 验证证据（2026-09-09）

```text
$env:PYTHONPATH='examples/office_suite/resources/runtime'; python -m pytest examples/office_suite/resources/runtime/kiyori_office/tests -q
67 passed

.\node_modules\.bin\tsc.cmd -p examples\office_suite\tsconfig.json
exit 0

.\gradlew.bat :app:generateBundledToolPkgAssets --no-daemon --console=plain
BUILD SUCCESSFUL（office_suite.toolpkg 共 51 个条目）

python -B -m unittest discover -s ci\test -p "test_office_suite_contract.py"
Ran 9 tests ... OK

.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
BUILD SUCCESSFUL
app/build/outputs/apk/debug/app-debug.apk 494,450,656 bytes（2026-09-09 22:19:29）
APK 内 assets/packages/office_suite.toolpkg 123,604 bytes

python -B ci/script/check_formal_readiness.py --repository . --require-main
Formal development readiness: PASS
```

`pytest` 用例覆盖：sentinel 解析与噪声容忍、错误码固定表、路径逃逸/符号链接/中文 emoji 文件名、输出预算与 `full_output_path`、DOCX 跨 run 查找替换、模板变量缺失、XLSX 溢出函数拒绝与 `_xlfn.` 前缀、`data_only` 保存拦截、PPTX 复制后 `sldIdLst` 一致性、PDF 合并/拆分/加密解密/中文生成、`office_validate` 的 strict 行为、真实 CLI 入口与宿主目录资源 zip 布局。

仓库级 `python -B -m unittest discover -s ci\test -p "test_*.py"` 当前有 2 个与本次改动无关的既有失败
（`test_github_forge_publish_asset_preservation`、`test_application_network_proxy_contract`）；
两者所在文件本次未修改，已记录为既有问题，不计入本专项交付。

## 9. 待验证（verification_pending）

以下项目在无设备环境无法完成，逐条保留复现步骤：

1. 全新环境 `office_env_check` → `office_env_setup(tier=1)`：计划先返回、确认后可见终端有输出、复检通过。
2. CJK 字体缺失时出中文 PDF：生成前报 `E_ENV_MISSING` + remedy；按 remedy 复制字体后中文无方框。
3. 30 页中文 docx 跨 run 查找替换：全部命中、格式保留、render preview 目视无异常。
4. 含 80+ 公式的 xlsx：`xlsx_recalc` 报 0 错误，Excel/WPS 打开无 `#NAME?`。
5. 基于用户 pptx 模板生成 12 页：版式多样、无残留占位符、无文字溢出。
6. 300 页 PDF 读取：outline 不爆上下文，range 精读命中正确页。
7. 加密 PDF / 损坏 docx：明确报错，不崩溃，不产出半成品。
8. 中文+空格+emoji 文件名全链路：读、写、跨环境复制、交付全部正常。
9. 超时场景：报 `E_TIMEOUT`，交付区无半成品残留。
10. 并发调用两个不同格式工具：`executorKey` 复用正常，输出不串扰。

## 10. 风险与下一步

- LibreOffice 在 PRoot arm64 下体积大、首启慢：`office_env_check` 返回磁盘余量与预计体积，安装走可见终端；失败明确报错不降级。
- T2/T3 安装需要网络与用户确认：`office_env_setup` 只给计划，`confirm=true` 由 JS 层流式执行。
- 第二期建议顺序：`office_render_preview` 真机验收 → `xlsx_chart` / `pptx_thumbnail` → Compose 控制台 UI → 文件管理长按接入。
