# 办公文档套件专项

## Claude 方案复核与修复（2026-09-11）

目标：落实用户提供方案中的 D1–D10，核验真实文件状态、错误协议与最终 APK，并按本轮授权全部提交推送 `origin/main`。
基线 `702e1ba0b4afa95259dc17574ebf40f1034ca8ba`，父仓库和 terminal 干净；不修改子模块，不安装或操作设备。
D11 题注重复提示按方案保留可选未实施。回滚以本轮独立提交的逆向变更处理。

1. 复核并应用修复：PDF 表单/表格、转换引擎、Tier2、PPT 备注及布局、错误码与参数诊断。
2. 补全单选组歧义、全空表格告警、无正文备注页等边界；同步 JS 错误码与随包指引。
3. 执行完整办公 Python 回归、JS 与协议契约检查，再串行构建并核验 APK 内 ToolPkg。
4. 审计精确文件清单、候选提交和远端状态，提交推送并对账。

风险：PDF 字段树/外观状态、文本表格识别及不同 Office 渲染器存在差异；文件输出继续原子发布。
状态：D1–D10 源码修复、本地回归和 Debug 构建完成；PRoot 外部引擎、实际 Office/WPS 视觉效果保持 `verification_pending`。

## 回归报告后续优化（2026-09-12）

本次接手工作区已包含 N1–N3 后续优化；本轮继续复核实现与本地回归，设备效果独立验收：

- N1：`docx_outline` 和 `office_read` 读取并统计 Word `w:txbxContent` 文本框；当存在文本框时返回 `DOCX_TEXT_MAY_BE_IN_TEXTBOXES`，避免 PDF→DOCX 产物被误报为空文档。
- N2：LibreOffice 与公共外部引擎执行器的失败细节统一使用 `stderr_tail` 字段语义，保留末尾诊断内容，帮助区分源格式不支持与运行环境故障。
- N3：PPT 几何字段缺失返回结构化 `MISSING` issue；`xlsx_chart.data_range` 明确要求 `A1:C8` 形式字符串。已有 PDF/Pandoc `cjk_font` 的不同语义已在随包指引并列说明。

本轮新增边界回归覆盖文本框、引擎 stderr、缺失字段、图表范围和失败不发布；D11 与 N4 未纳入实现。

## 长会话论文诊断修复（2026-09-12）

目标：闭环真实论文测试中的工具历史中断、总结失败可观察性、长期运行性能、诊断导出与工具错误。
基线 `main@5d74c4f59`，接手时 30 个未提交文件，包含上述办公优化和前轮未交付修复。
范围覆盖 AI 历史投影、总结、审计与办公/代码运行器；不改子模块、外部协议或模型重试策略，
不安装操作设备。回滚使用交付提交的逆向变更，原始诊断不进入仓库。

| 阶段 | 内容与验收 | 状态 |
| --- | --- | --- |
| 证据复核 | 原文件 131,591,431 bytes；2,364 事件、171 工具调用、11 工具失败 | DONE |
| 本地修复 | 警告历史类型、作废调用最终投影、总结失败审计、后台工作、导出预算、源码字节写入、目录拒绝 | DONE |
| 本地验收 | Kotlin/办公 Python/JS/真实 PTY/SQLite 回归、文档检查、Debug APK 与资产核对 | DONE |
| 交付 | 精确 38 文件允许清单，排除诊断、日志、APK 和子模块变动 | 具体提交及远端 ref 以 Git 与本轮交付报告为准 |
| 现场验收 | 相同设备长会话、设置响应、总结断流、导出耗时及 Office 版面 | verification_pending |

已确认与修复：

- 事件 1156 的宿主作废警告被误标 `TOOL_RESULT`，没有结构化结果；改成普通反馈并从后续请求中
  移除作废调用。补齐最终持久化路径：只对精确匹配且未执行的调用组应用显式作废记录，后续正文不丢失。
- 总结有两次请求、一次生成/提交，缺少首次失败事实；为总结接入原聊天审计和可见失败，并将
  历史清理与摘要组装移出主线程。原文件没有用户所报传输异常的完整记录，不能认定远端根因已修复。
- 审计 payload 写盘改为后台执行；导出批量查询替代逐事件 Room 往返，复用校验读取的字节。
  Markdown 每项 64 KiB、正文合计 8 MiB，长消息展示首尾；完整审计、事件和 seal 保持。
  原样本 1,650 个唯一 payload 中，预算模拟保留 1,492 项、省略 158 项，内嵌 payload 从
  126,746,469 降至 2,779,874 bytes（减少约 97.8%）。这是正文预算模拟，不是设备导出耗时或最终文件大小。
- `code_runner` 大段源码通过 PTY 的 ANSI-C 包装后膨胀并截断，shell 等待未结束的 heredoc。
  改用既有 Linux 文件提供者写 UTF-8，PTY 负责执行；大段中文源码纳入真实 PTY 回归。
- `office_read` 对目录的输入提前返回 `E_PATH_INVALID` 和选择文件指引，避免误导性的复制报错。
  `in_place` 环境、工作表不存在、占位工具名、缺少 operation、字体/题注字段误用、列表缺少 type、列宽和 PPT
  越界属于合理输入校验，继续保留；随包 DOCX 指引已有 runs/format 与 caption 的正确写法。

风险与下一门槛：本地验证不能量化目标手机卡顿是否完全消失；完整签名包仍保存全部正文，大小
随审计增长。现场需在相同设备重复长会话，记录设置操作与导出耗时、峰值内存、错误及 APK SHA-256。

本地验证（2026-09-12）：

- Kotlin 定向回归 19 个 suite、124 项，零失败/错误/跳过，覆盖审计、Provider 工具历史、最终
  replay 投影、compaction 与宿主警告。最终复跑 `BUILD SUCCESSFUL in 1m22s`。
- 办公 Python、ToolPkg 契约及 SQLite 查询回归：316 passed、52 subtests passed；办公 JS
  53/53；WSL `Ubuntu-26.04` 的真实 PTY 13/13，含大段中文源码、错误和超时后清理。
- 办公 TypeScript 编译、文档检查（518 文件、0 问题）、formal readiness 和 `git diff --check` 通过。
- 最终串行 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 46s`，
  238 个任务；产物 `app/build/outputs/apk/debug/app-debug.apk`，487,787,852 bytes，SHA-256
  `ad4f507a7d9b1f9dbb413b1edfac7bb765598eff142b8f53ccbc63da0ac213e0`。
  包名 `com.kiyori`、版本 `45 / 0.1.0`、min/target/compile SDK `26 / 34 / 37`、仅 `arm64-v8a`；
  V2 单 signer 和 `zipalign -c -P 16 4` 通过。APK 内办公 ToolPkg 80 个文件与源码一致，代码运行器
  JS 与两份源码/资产一致。未安装或操作设备，未调用真实模型复测，也未将本地结果描述为远端 CI 通过。

### 修复结果与方案校正

- PDF 表单使用字段声明的状态与 `NameObject` 写入值和外观，支持布尔/裸状态名及完整嵌套字段名。
  自定义状态优先于布尔别名，多选项单选组的 true、pushbutton 或无可写控件字段显式拒绝。
  `pdf_form_list.available_states` 给出合法状态，`filled` 排除 non-strict 忽略的未知字段。
- 全空网格按方案尝试文本策略，成功切换告警；仍为空也告警，不把空文本当作原页无内容。
  本轮真实生成的错位网格 PDF 验证了两种策略差异；用户原报告附带 PDF 未提供，未宣称复测该原件。
- PDF→DOCX/ODT/HTML 指定 Writer 导入并先检查 Writer 组件；动态引擎返回实际 engine。
  Tier2 计划补全 weasyprint。外部引擎测试使用替身核验命令、产物交付及结果信封，不冒充真实转换或 apt 安装。
- PPT 备注参与模板替换及严格缺失检查，保留无备注页/无正文占位符的合法文件。
  形状默认居中，文本框默认贴顶，显式值优先；表格默认每行 0.8cm，显式行高保留，真实尺寸越界拒绝。
  0.8cm 是工具默认值，不采纳方案中“PowerPoint 默认且保证自动撑高”的未经本轮验证说法。
- Python、TypeScript 和 dist 同步新错误码，契约测试阻止再次漂移；未知字段诊断列出当前层级合法字段。
  同步三份随包指引与扩展契约。D11 重复题注提示按方案不实施。

### 本轮验证与交付证据

- 项目 `.venv` 使用本专项已有的本机附加依赖入口，所有 Python 命令使用 `-B`；未安装依赖。
  基线办公测试 254 项通过；最终办公运行时 283 项、工具/schema/错误码契约 13 项，共 296 项通过，0 失败/跳过。
  新增 29 项运行时回归，覆盖真实 PDF/OOXML 保存回读、状态歧义、失败不覆盖、表格策略及布局边界。
- TypeScript 编译成功；`node --test tools/example_packages/office_suite.test.mjs tools/example_packages/office_console.test.mjs`：49 项通过。
  文档检查 518 文件、0 问题；三份随包 Skill 校验通过，formal readiness 与 `git diff --check` 通过。
- 串行 `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 49s`，238 项任务（24 执行）。
  APK 为 `app/build/outputs/apk/debug/app-debug.apk`，495,731,084 bytes，2026-09-11 22:59:47（Asia/Shanghai），`com.kiyori / 0.1.0 / 45`。
  SHA-256：`c14222aaf77e5358a0ec6cb005d9b3179b4d718e7c0e2dbef0df593df956166c`。
- APK 内办公 ToolPkg 为 278,898 bytes、80 文件，全部与工作区逐字节一致，无 pyc 或 Python 缓存；V2 签名、单签名者和 16 KB ZIP 对齐通过。
- 交付候选为 21 个办公相关文件，敏感与异常文件扫描无命中，terminal 保持干净；构建与审计记录保留在忽略的 `work/office-fix-plan-20260911`。
  候选提交、新鲜克隆和提交推送对账在形成提交后执行，结果见本次交付回复与任务日记。
  不操作设备、不运行 Release/Lint 或无关 JVM 测试，设备与真实外部引擎验收保持待验证。

## 测评修复（2026-09-11）

后续 AI 稳定性修复已生成包含本节办公修改的新 APK；最新产物哈希及 79 个随包文件核验见
[AI 稳定性修复记录](../ai_interrupted_turn_recovery/index.md)。
本节下方 APK 哈希保留为当轮历史构建证据。

目标：修复测评暴露的图表合并、字体选择、打印布局设置与公式缓存提示，并联动修复多模态
预览结果导致的 AI 历史闭合异常。基线 `main` / `f5319136040bf294935006cc7c86bbce44aa88b7`，工作区干净。
范围限于现有工具与重放投影，不引入 OCR、新状态所有者或自动重试；不操作设备、不提交推送。

1. 修复并行/流式结果之间的页图附件投影，验证最终文本完整保留及真正缺失结果仍失败。
2. 复制 DOCX 原生图表关系及独立工作簿，保留图片与 OMML，核验源文件不变及输出关系有效。
3. 统一 ReportLab 字体注册，明确不支持字体；增加显式 XLSX 打印设置及公式缺缓存提示。
4. 执行相关 JVM/Python/JS 回归、生成工具描述、文档检查，串行构建并核验 APK。

风险：Office 渲染器分页及 CFF/TTC 字体支持存在差异；打印区必须由调用方覆盖图表。
原文件继续默认另存，失败不覆盖输入。状态：实现、本地回归与 Debug 构建完成；Android/PRoot 和现场长对话
复测保持 `verification_pending`。历史报告的“图片/OMML 全部不能合并”不作为当前能力结论，
应分别以部件回归和现场样本验证。

2026-09-11 验证证据：

- 办公运行时 `python -m pytest examples/office_suite/resources/runtime/kiyori_office/tests -q`：254 passed；最终字体注册调整后重跑 PDF、字体和测评回归：39 passed。
- `node --test tools/example_packages/office_suite.test.mjs tools/example_packages/office_console.test.mjs`：47 passed；`test_office_suite_contract.py`：13 passed；工具描述生成和 TypeScript 编译通过。
- AI 投影、工具结果格式化、压缩契约三组 JVM 测试：63 passed。文档检查 517 文件、0 问题；三个随包 Skill 校验通过；正式准备检查 PASS。
- 首次打包因 pytest 产生 `__pycache__` 被正确拒绝；缓存移动到忽略的 `work/`，没有放宽打包规则。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：BUILD SUCCESSFUL（51 秒）。标准 Debug APK 为 487,349,430 bytes，SHA-256 `c91d1aa48957cf18d1d99b0f1e04f0a2a38a3129171e929b8611e899d2607982`。
- APK 中 `office_suite.toolpkg` 为 267,125 bytes；49 个运行时/描述/指引文件与工作区逐字节一致，未包含 pyc 或缓存目录。未安装或执行设备/PRoot 验收，未提交推送。

## 本轮计划（2026-09-10）

目标：页面图像直接进入多模态上下文；增量编辑既有 Word/PPT，完善论文对象与原生设计能力。
沿用现有 ImagePool、ToolPkg 搬运、Python 原子保存；不改变协议身份、不操作设备。

1. 视觉：Linux direct_image、预览自动注册图片、分页与版面诊断。
2. PPT：共用元素插入、对象样式、图层/分组、主题、可解释文本测量、动画和转场。
3. Word：锚点插入结构化内容，公式、图片与论文排版；保留现有复杂对象。
4. 回归：结构与保真测试、JS 搬运测试、真实渲染检查、串行 Debug APK。
5. 审计允许清单后提交推送 main，核对远端；PowerPoint 放映与 Android 多模态现场保持独立验收。

风险：Office 私有扩展及渲染器差异；静态图不能验证动效，几何估算不能证明字形真实溢出。
回滚点为本轮基线提交 `968481bbb7badcb631a757bb935028ec52da52e1`，原文件编辑继续默认另存。

状态：本轮源码与本地构建已完成，提交推送交付按本轮授权执行；最新能力与证据见第 20 节。Android / PRoot 多模态与完整放映验收保持 `verification_pending`。

## 1. 目标

让 Kiyori 的 Agent 能高质量生成与精确处理 Word / Excel / PowerPoint / PDF：

- 能力层：`com.kiyori.office_suite` ToolPkg，JS 只做参数校验、跨环境搬运、命令拼装与信封解析；
- 运行时层：可脱离 Kiyori 独立测试的 `kiyori_office` Python 包，全部格式逻辑在此；
- 知识层：五个随 ToolPkg 分发且可独立导入的 Skill（`kiyori-office-core` / `-docx` / `-xlsx` / `-pptx` / `-pdf`）；通过 `office_read_guide` 按需读取；
- 验收层：`office_validate` + `office_render_preview` + `direct_image` 视觉闭环。

## 2. 范围（第一期）

- ToolPkg：`manifest.json`（schema v2）+ `dist/main.js` + 五个子包（`office` / `docx` / `xlsx` / `pptx` / `pdf`）+ 目录资源 `resources/runtime/kiyori_office`。
- Python 运行时：`protocol.py` / `argspec.py` / `paths.py` / `budget.py` / `env.py` / `readers/` / `ops_*/` / `validate.py` / `render.py` / `convert.py` / `schemas/commands.json` / `tests/`。
- 工作区模板：`app/src/main/assets/templates/office/` 升级为骨架 + 规则说明。
- 内置打包接线：`tools/example_packages/packages_whitelist.txt` 已包含 `office_suite`。

## 3. 非目标（第一期）

- 本轮开发不操作设备：产品中的 `office_env_setup` 默认返回计划，`confirm=true` 才由可见 PTY 执行并复检；Python 层只生成计划。
- 第一阶段原计划不含控制台；第 14 节已增量实现 Compose DSL 办公控制台，复用现有 ToolPkg UI 注册入口。
- 不做文件管理长按菜单接入（第二期）。
- 不修改 `terminal` 子模块，不新增宿主 API。
- 不做 OCR（T4）与 LaTeX 级排版。

## 4. 与设计文档的差异（源码核对结论）

| 设计文档 | 实际实现 | 原因 |
| --- | --- | --- |
| `schemas/*.json` 每个命令一个文件 | `schemas/commands.json` 集中登记 + `argspec.py` 自研校验 | 48 个碎片文件难以审计；校验器只覆盖实际用到的关键字，不引入 `jsonschema` 依赖 |
| `examples/office_suite/src/main.ts` + `packages/*.ts` | 已实现，`dist/` 为 tsc 产物 | 与 `examples/linux_ssh` 等现有包一致 |
| 内置 Skill 随 APK 预置 | 随 ToolPkg 资源打包，通过 `office_read_guide` 读取，也保留独立导入目录 | 复用现有资源加载，不新增 SkillManager registry 或扫描所有者 |
| `office_env_setup` 实际执行安装 | 只返回计划，`executed: false` | Python 侧不调用 apt/pip；执行由 JS 层可见 PTY 负责，保证可独立测试 |
| `pdf_ocr`（T4） | 未实现，`pdf_to_images` 已可用 | 设计文档把 T4 列为第三期 |

## 5. 已实现命令（51 条 Python 命令，53 个 ToolPkg 工具条目）

- `office`：`office_workflow_guide`（advice）、`office_read_guide`（随包资源读取）、`office_env_check`、`office_env_setup`、`office_read`、`office_convert`、`office_render_preview`、`office_validate`、`office_diff`、`office_workspace_init`、`office_workspace_status`、`office_workspace_clean`。
- `docx`：`docx_outline`、`docx_create`、`docx_from_template`、`docx_edit`、`docx_find_replace`、`docx_table`、`docx_insert_image`、`docx_style`、`docx_merge`、`docx_extract_media`。
- `xlsx`：`xlsx_info`、`xlsx_read`、`xlsx_write`、`xlsx_format`、`xlsx_sheet`、`xlsx_recalc`、`xlsx_table`、`xlsx_chart`。
- `pptx`：`pptx_outline`、`pptx_create`、`pptx_template_fill`、`pptx_slide`、`pptx_edit`、`pptx_notes`、`pptx_media`、`pptx_clean`、`pptx_measure_text`。
- `pdf`：`pdf_info`、`pdf_extract`、`pdf_merge`、`pdf_split`、`pdf_rotate`、`pdf_reorder`、`pdf_delete_pages`、`pdf_form_list`、`pdf_form_fill`、`pdf_watermark`、`pdf_encrypt`、`pdf_decrypt`、`pdf_to_images`、`pdf_create`。

设计文档中的 `xlsx_import`/`xlsx_export`、`xlsx_aggregate`、`docx_comment`、`docx_track_changes`、`pptx_thumbnail`、`pdf_stamp`、`pdf_compress`、`pdf_ocr` 属第二/三期，尚未实现。`xlsx_chart` 已实现五种基础图表，组合图表与数据透视编辑尚未实现。

## 6. 关键源码核对结论

- `Tools.System.terminal.hiddenExec(command, { executorKey, timeoutMs, localOnly })`：`localOnly` 表示显式本地 Ubuntu 启动器，office 运行时只存在于本地，因此固定 `localOnly: true`。
- `Tools.Files.copy(source, destination, recursive, sourceEnvironment, destEnvironment)` 支持跨环境复制，Android → Linux 与 Linux → Android 均已使用。
- `Tools.Files.write(path, content, append, environment)` 用于写 args.json 与 bootstrap 脚本；参数一律走 JSON 文件，不使用 `python -c`。
- `ToolPkg.readResource(key, outputFileName, internal)` 对 `mime: inode/directory` 的资源会先打 zip 再返回路径，因此运行时以目录资源分发，JS 侧解压到 `~/kiyori_office/runtime`。
- `Tools.Files.read({ path, direct_image: true })` 是视觉通道；预览按 artifact 的实际环境自动注册图像，返回图片链接，仍要求模型逐页审阅。
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
- 当前建议顺序：控制台与新增保护真机验收 → 真实模板与复杂文档保真扩展 → 缩略图网格与批注/修订 → 文件管理长按接入。

## 11. 真机测试问题修复（2026-09-09 第二轮）

首轮真机测试报告「套件基本不可用」，定位到三条根因并已修复：

### 根因 1：宿主注入参数污染 Python 校验（P0-1 / P0-2 / P0-3 共同根因）

`JsToolManager.buildRuntimeParams` 会向 ToolPkg 函数注入 `__operit_package_name`、
`__operit_package_state`、`__operit_toolpkg_subpackage_id`、`containerPackageName`、
`toolPkgId` 等内部参数。JS 层原先用 `Object.entries(input)` 全量转发，被 Python 侧
`additionalProperties: false` 拒绝——这解释了「`office_env_check` 任何参数都失败」，
以及「同一参数时好时坏」（注入项只在部分调用上下文出现）。

修复：`runtime.ts` 新增 `HOST_INJECTED_PARAMS` 与 `collectBusinessParams`，
只转发工具声明的字段与协议字段；`argspec.py` 对 `__` 前缀字段与 `task_id`/`allow_roots`
一律放行，两层职责不再交叉。

### 根因 2：单一来源漂移（P0-3 / P1）

`xlsx_write.path` 在 METADATA 中标为必填，但文档与实现都支持「省略时新建工作簿」；
`office_env_setup.tier`、`office_read.with_anchors`、`office_convert.in_place/timeout_ms`
等字段在工具与 schema 之间不一致。

修复：`spec/tools.json` 作为唯一来源重新生成；新增 CI 契约测试
`test_tool_params_match_python_schema_properties` 与
`test_required_params_match_python_schema_required`，任何后续漂移都会失败。

### 根因 3：文件搬运静默失败（P0-2）

`Tools.Files.copy` 返回结构化失败而不是抛异常，原实现忽略返回值，导致后续步骤
报出无法定位的 `Failed to read source file`；`/sdcard` 与 `/storage/emulated/0`
别名也未做探测。

修复：新增 `assertFileOperation` 把所有文件操作失败转成带路径的 `E_PATH_INVALID`；
`androidPathCandidates` 按「原样 → 真实路径 → 兼容别名」顺序探测并报告全部候选。

### 诊断信息改进（P1）

包层错误消息现在直接包含字段名与原因，例如
`参数不符合 office_env_check 的 JSON Schema：args.task_id 不是已登记字段`，
并在 `data.issues` 给出 `field`/`reason`/`expected`/`actual` 结构化清单；
`env` 统一大小写归一，消除「系统层放行、业务层报错」。

### 本轮验证

```text
python -m pytest examples/office_suite/resources/runtime/kiyori_office/tests -q
76 passed（新增 test_host_injection.py 9 项）

node --test tools/example_packages/office_suite.test.mjs
12 passed（JS 薄层注入过滤、别名探测、结构化失败）

python -m unittest discover -s ci\test -p "test_office_suite_contract.py"
11 passed（新增参数/必填同源门禁）
```

## 12. 真机复测问题修复（2026-09-09 第三轮）

第二轮真机复测结论：Linux 路径下 docx/xlsx/pptx/pdf 读写基本打通，但**写 Android 路径的
二进制产物全部损坏且无法读回**，另有 tier3 误报与路径白名单不一致。逐条定位并修复如下。

### 根因 1（P0）：跨环境复制把二进制当文本解码

`StandardFileSystemTools.copyFileCrossEnvironment` 在 Linux→Android 分支用
`FileSystemProvider.readFile(sourcePath)`（内部 `file.readText()`）读取源文件，
再 `content.toByteArray(Charsets.UTF_8)` 写出。非法 UTF-8 字节被替换为 U+FFFD，
docx/xlsx/pptx/jpg 因此体积变大且 ZIP/JPEG 签名失效——工具仍返回 success，
报告字节数还是「损坏前」的值，用户侧产物完全打不开。

修复：改用二进制 API `readFileBytes(sourcePath)`，Linux 目标写 `writeFileBytes`；
失败信息从 `Failed to read source file` 改为带路径的 `Failed to read source file bytes: <path>`。
新增门禁 `ci/test/test_cross_environment_copy_binary_safety.py` 锁死该实现不得回退到文本 API。

交付自检：`runtime.ts` 回搬后调用 `Tools.Files.info` 比对 `artifact.bytes`，
不一致时报 `E_PATH_INVALID: 交付产物大小不一致（疑似二进制损坏）`，让损坏不再静默通过。

### 根因 2（P1）：tier3 只检测可执行文件，漏检 Calc/Impress 组件

`env.detect` 对 `libreoffice` 只做 `shutil.which("soffice")`。只装
`libreoffice-writer` 时 soffice 存在，`office_env_check` 误报 `tier3.complete=true`，
随后 `xlsx_recalc` 与 xlsx/pptx 预览都失败在引擎层。

修复：新增 `detect_libreoffice()`，按 `libswlo.so` / `libsclo.so` / `libsdlo.so`
检测 Writer/Calc/Impress 组件库；`tier3.complete` 要求三者齐全。
新增 `require_libreoffice(component=...)` 与按后缀映射的
`require_libreoffice_for_source()`：`xlsx_recalc` 要求 calc，
`office_convert`/`office_render_preview` 按源文件后缀要求对应组件，
缺失时报 `E_ENV_MISSING` + 安装 remedy，而不是落到 `E_ENGINE_FAILED`。

### 根因 3（P1）：路径参数漏声明 inputPaths

`office_convert.from_path`、`office_diff.left/right`、`office_validate.original_path`、
`pptx_create.template_path`、`pdf_create.source_path` 未在 `spec/tools.json` 的
`inputPaths` 中声明。JS 薄层只对 `inputPaths` 做 Android→Linux 搬运与 `allow_roots`
注入，因此 Python 侧报「越出允许根目录」，表现为「同一路径有的工具能读、有的不能」。

修复：补齐上述声明；`office_workspace_init` 的 `dir` 指向待创建目录，不走输入暂存，
改由 JS 层加入 `allow_roots`。同时修复 `office_diff` 两个同名输入会互相覆盖的问题：
暂存文件名改为 `<参数名>-<基名>`，数组输入用 `<参数名><索引>-<基名>`。
新增契约门禁 `test_file_input_params_are_declared_for_staging` 与
`test_workspace_dir_is_not_a_staged_input`。

### 根因 4（P1）：file_converter 环境判定与执行命名空间不一致

`convert_file` 在 Linux/PRoot 终端执行转换，但存在性检查
`Tools.Files.exists(input_path)` 未传 environment，被宿主当作 Android 校验，
导致 `/tmp/...` 下的文件误报 `Input file not found`。

修复：存在性检查改为 `environment: "linux"`（PRoot 已 bind `/sdcard` 与
`/storage/emulated/0`，同时覆盖 Android 存储路径）；PDF→其他格式给出可执行建议
（改用 `pdf_extract` 或从原始源文件转换），不再只抛 pandoc 的原始错误。

### 本轮验证

```text
$env:PYTHONPATH='examples/office_suite/resources/runtime'; python -m pytest examples/office_suite/resources/runtime/kiyori_office/tests -q
80 passed（新增 tier3 组件探测 4 项）

node --test tools/example_packages/office_suite.test.mjs
17 passed（新增双输入暂存、convert 暂存、workspace dir 白名单、交付体积自检 5 项）

python -B -m unittest discover -s ci\test -p "test_office_suite_contract.py"
13 passed（新增 inputPaths 声明门禁 2 项）

python -B -m unittest discover -s ci\test -p "test_cross_environment_copy_binary_safety.py"
2 passed

.\node_modules\.bin\tsc.cmd -p examples\office_suite\tsconfig.json
exit 0

.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
BUILD SUCCESSFUL；app/build/outputs/apk/debug/app-debug.apk 494,450,656 bytes（2026-09-09 23:22:18）
APK 内 assets/packages/office_suite.toolpkg 133,818 bytes / 53 条目
产物核对：runtime.js 含 verifyDeliveredSize 与 slot 暂存；specs.js 的
office_convert/office_diff/office_validate/pptx_create/pdf_create 已带 inputPaths；
classes23.dex 同时含 copyFileCrossEnvironment 与 readFileBytes 符号

python -B ci\script\check_formal_readiness.py --repository . --require-main
Formal development readiness: PASS

python -B ci\script\check_documentation.py --repository .
512 个文件，0 个问题
```

第 8 节的「仓库级 2 个既有失败」在上一轮已记录，本轮未触碰相关文件。

## 13. 真机复测问题修复（2026-09-09 第四轮）

第四轮真机复测：四类文档链路 40+ 工具基本可用，`office_validate strict=true` 全 0 issue；
另报 5 个问题，逐条修复如下。

### 问题 1：xlsx_write 新建工作簿不能指定 sheet_name

`xlsx_write` 无条件用 `_resolve_sheet(workbook, sheet_name)` 查找工作表。新建工作簿时
默认表名是 `Sheet`，传入的 `sheet_name` 被当成「要查找的表」，直接报
`E_INPUT_SCHEMA: 工作表不存在`。

修复：新建分支改为重命名默认表（`sheet_name` 表达「新表叫什么」），已有工作簿仍保持
「必须已存在」的查找语义；新增 `_validate_sheet_title` 按 Excel 约束校验空值/超 31 字符/
`[ ] : * ? / \` 非法字符，避免 openpyxl 抛出难以诊断的异常。

### 问题 2：pdf_create 不支持 bullet 块

`ops_pdf/ops.py` 只处理 `heading/title/paragraph/spacer`，`{"type":"bullet"}` 报
「不支持的 blocks 类型」。

修复：新增 `bullet`/`bullets`/`list` 类型，接受 `items` 数组或单个 `text`，可用 `bullet`
字段自定义符号（默认 `•`）；CJK 探测同步扫描 `items`，否则「只有列表的中文文档」会用
Helvetica 渲染成方框。`spec/tools.json` 的 `blocks` 描述也列出支持类型。

### 问题 3：office_convert 的 pandoc 路线全部不可用（根因是后缀比对错位）

`convert()` 已把 `to_format` 规范化为不带点（`lstrip(".")`），但
`PANDOC_OUTPUTS`/`PANDOC_INPUTS` 存的是带点后缀（`.md`/`.html`…），两者永不相等，
因此 pandoc 对 md/markdown/html/txt/pdf 一律报「不支持该目标格式」。

修复：白名单改为「无点键 → pandoc 读写器名」映射，覆盖
`md/markdown/html/htm/txt/plain/docx/odt/rst/tex/latex/pdf/epub/csv`；
新增 `PANDOC_OUTPUT_EXTENSIONS` 保证 `txt`/`plain`/`latex` 这类别名写出正确后缀；
当后缀与读写器名不同时显式传 `-t`，避免 pandoc 按后缀推断失败。

### 问题 4（严重）：缺 poppler-data 导致中文 PDF 预览静默变空白

reportlab 生成的 CJK PDF 使用 Adobe-GB1 CID 字体；poppler 缺 CMap 数据包时
`pdftoppm` 只打印 `Missing language pack for 'Adobe-GB1' mapping` 并以 **退出码 0**
返回，产出的 JPEG 只有背景、文字与水印全部消失，多份不同文档的图 md5 相同——
靠退出码完全无法发现。

修复：Tier2 组件新增 `poppler-data`；`detect_poppler_data()` 探测
`/usr/share/poppler/cMap/**/Adobe-GB1*` 与 `cidToUnicode/Adobe-GB1`；
`require_poppler_data()` 在 `office_render_preview` 与 `pdf_to_images` 转图前拦截并报
`E_ENV_MISSING` + 安装 remedy；`office_env_setup(tier=2)` 的 apt 计划自动带上
`poppler-data`。Skill 与 office-core 总纲同步记录该坑。

### 问题 5：office_render_preview 等不支持 output_path

多产物命令把产物固定在暂存区 `out/<名字>-preview`，无法指定交付目录。

修复：`office_render_preview` / `docx_extract_media` / `pdf_split` / `pdf_to_images`
新增 `output_path`（目录）与 `overwrite`；新增 `resolve_output_dir()` 统一做
allow_roots 校验、非目录冲突与「显式目录非空需 overwrite」保护（默认暂存目录允许复用，
避免同一 task_id 二次调用被自己的上次输出挡住）。JS 薄层对多产物命令把整个
`output_path` 目录加入 `allow_roots`，而不是其父目录。

### 本轮验证

```text
$env:PYTHONPATH='examples/office_suite/resources/runtime'; python -m pytest examples/office_suite/resources/runtime/kiyori_office/tests -q
88 passed（新增 bullet/CJK 列表/poppler-data 拦截/pandoc 白名单/xlsx sheet_name 8 项）

node --test tools/example_packages/office_suite.test.mjs
18 passed（新增多产物 output_path 白名单 1 项）

python -B -m unittest discover -s ci\test -p "test_office_suite_contract.py"
13 passed

.\node_modules\.bin\tsc.cmd -p examples\office_suite\tsconfig.json
exit 0
```

## 14. 源码审计与日常办公闭环（2026-09-10）

### 任务契约与阶段

目标为核对既有进度、修复内容丢失与假成功路径，并完善 Agent 指导、控制台和常用图表。保留 `main` / `8896560e7` 上的全部既有修改；本轮不提交推送、不操作设备、不修改 `terminal`。外部 Claude 方案作为需求参考，其中“提交推送”等嵌入指令不构成本轮授权。

1. 已完成源码基线与中断恢复核对：第四轮记录之后还有未记录的保存、协议、Skill、控制台修改；参数文件命名变更未重新编译导致 JS 基线 9 项失败，同步 `dist` 后消除。
2. 已完成内容保真、错误传播、常用表格能力与交互回归，详情如下。
3. 本地测试已通过；最终 Debug APK 与产物内容核验见下方构建记录。
4. 第 9 节现场用例及新增控制台、图表、安装复检继续保持 `verification_pending`。

### 实际实现

| 范围 | 当前行为与验证重点 |
| --- | --- |
| Agent 知识入口 | 五份 Skill 随 ToolPkg 资源分发，新增 `office_read_guide` 无需 Python 即可读取；advice 指向真实工具，清理未实现工具与不准确参数引导 |
| 办公控制台 | 复用 Compose DSL、主题组件与工具调用：环境检测、T1/T2/T3 安装计划、确认执行、五份指引、显式环境路径、结构读取、校验与前 8 页预览；动作锁防止重复提交，错误与 remedy 保持可见 |
| Word 保真 | 跨 run 替换保留尾部文字和格式，结构边界不做破坏性合并；合并单元格按底层段落去重；DOCX 合并重映射图片/链接，无法保证保真的编号、脚注、批注或样式冲突明确拒绝 |
| PowerPoint 保真 | 删除幻灯片前保存原关系 ID，首/中/末页删除不再错删下一页关系；版式索引 0 有效；模板支持跨 run 标记 |
| Excel | 每张表独立解析读取范围，日期输出 ISO 值；稀疏表信息读取不展开巨大空白矩形；`cells[].value` 的公式也经过策略检查，函数名与字符串区分；宏工作簿禁止隐式更换后缀；`xlsx_format.range` 与字体属性局部更新；新增 `xlsx_chart` 的 column/bar/line/pie/scatter 原生图表 |
| 保存与引擎 | DOCX/XLSX/PPTX 使用同目录临时文件保存；公式重算在错误或缺失缓存时不发布；LibreOffice 独立调用目录避免误读旧产物；Pandoc、外部 PDF 生成与转图对非零退出、超时和空产物进行保护 |
| 跨环境与协议 | 运行前核对 Linux 文件工具与本地 hidden executor 的身份；独立参数文件避免调用参数覆盖；失败信封保留 code/detail/remedy/data；结构化大输出受预算限制；Android 回搬先校验临时副本体积，再调用宿主 move，失败清理本次唯一临时文件 |
| 安装 | Python 计划绑定实际解释器，venv 不传 `--user`；AES 缺少 `cryptography` 明确报环境缺失；确认后在可见终端执行本地身份保护命令；失败停止，复检既检查组件也检查进程退出码 |

### 本地验证

- `tsc -p examples/office_suite/tsconfig.json`：通过，生成源码和 `dist` 已同步。
- `node --test tools/example_packages/office_suite.test.mjs`：29 通过。
- `node --test tools/example_packages/office_console.test.mjs`：3 通过；为模拟 Compose DSL 交互测试，不是设备视觉验收。
- Python runtime pytest：116 通过；包含五种图表真实保存/回读/关系引用、区域格式、宏后缀保护、稀疏表、Word 合并单元格、重算失败保护与外部引擎失败保护。
- `python -B -m unittest discover -s ci/test -p test_office_suite_contract.py`：13 通过。
- `python -B ci/script/check_documentation.py --repository .`：512 文件，0 问题。

本机项目 `.venv` 没有 `cryptography`，直接 pytest 的 AES 用例报 `E_ENV_MISSING`。完整测试使用项目解释器，临时追加本机已安装的 Python-3.14.7 site-packages 来读取 AES 依赖；没有安装软件或修改 `.venv`。本机复现命令如下（该路径是开发机专用）：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
.\.venv\Scripts\python.exe -B -c "import sys; sys.path.append(r'D:\01_Environment\Toolchains\Python\Python-3.14.7\Lib\site-packages'); import pytest; raise SystemExit(pytest.main(['examples/office_suite/resources/runtime/kiyori_office/tests','-q']))"
```

首次 Debug 打包被 `resources/runtime/kiyori_office/__pycache__` 阻止，属于本地测试缓存混入资源目录；已核对并仅清理该运行时下 7 个生成缓存目录，再串行构建。复现测试使用上面的字节码禁写设置，避免缓存再次进入 ToolPkg 分发范围。

随后 APK 内容核验发现：内置 `GenerateBundledToolPkgAssetsTask` 使用固定目录清单，并不直接按 manifest 的 `distribution.include` 收集新目录，导致 `skills/**` 被漏打包。已在 `buildSrc` 的既有任务中补齐 `skills` 收集，并让 Markdown 进入文本体积/私钥检查；增加归档内容、确定性与私钥拒绝行为测试。`ci/script/pr_check.py` 已将整个 `buildSrc` 路径映射到完整 Android 检查，无需新增并行 CI 入口。

### 最终构建与产物核验

- `:buildSrc:test --tests com.kiyori.buildlogic.tasks.GenerateBundledToolPkgAssetsTaskTest`：7 项通过，0 失败/跳过。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 9m 39s`；238 项任务，含单 launcher、脚本代理与播放器打包验证。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，477,151,027 bytes，2026-09-10 00:58:04（Asia/Shanghai）；`com.kiyori` / versionCode 45 / versionName 0.1.0。
- SHA-256：`1635305f45177ac9533292781812294487eb1512b2009608e047452f9d6e1847`。
- APK 内 `assets/packages/office_suite.toolpkg`：166,406 bytes / 63 个文件；全部与当前工作区逐字节一致，包含 5 份 Skill、办公控制台、图表运行时与同源 schema，无 pyc/cache。工作区模板 README 与内置转换脚本也与源码一致。
- 跨环境二进制复制契约：2 项通过；5 份 Skill 的 `quick_validate.py` 均通过；formal readiness PASS，文档检查 512 文件 / 0 问题，`git diff --check` 通过。
- 未形成候选提交，因此未运行验证旧 HEAD 的 fresh-clone 检查；未运行无关 Lint/Release/全仓库测试，未操作设备或远端。

### 剩余边界与下一验收

- 现有自动测试不证明任意复杂 OOXML、所有金融公式和所有模板均保真。批注/修订、OCR、组合图表等仍按第 5 节记录未实现，不宣称“所有办公操作无问题”。
- 预览、PDF split 等多产物按文件发布，不是整个目录的事务；中途失败时先核对已生成页，不能把失败后的目录当完整交付。Android 宿主 move 在某些后端可能采用复制删除，其最终原子性与同尺寸字节损坏不由体积检查证明。
- 同一 `task_id` 的工作区需要顺序使用；默认任务 ID 隔离不同调用，显式复用同一任务并发修改同一文件仍需现场验证。
- 下一步在本地 Ubuntu/PRoot 真机验证：控制台进入与触控、仅查看计划不安装、确认后安装与复检、五类图表在 WPS/Excel 打开、中文预览及 Android 临时产物失败清理。设备操作需另行授权。

## 15. 实测复核与专业排版完善（2026-09-10）

本轮目标：修复同批 Excel 写入覆盖及严格校验诊断，深入检查内容保真，完善 Agent 可执行的论文排版、PPT 布局和控制台流程。基线 `main / 8896560e7`，保留全部已有工作区修改；不提交推送、不安装依赖、不操作设备或 terminal 子模块。

1. 正确性：复现 rows/cells 冲突、错误信息丢失、图片越界、表头未生效、复制页共享可变关系等缺陷，补真实文件回读回归。
2. 排版：复用现有 Python 命令，补 Word 页面/段落/东亚字体和 PPT 显式布局能力；同步同源参数与格式指引。
3. 交互：复用 Compose DSL 主题与动作锁，错误可定位、产物可继续检查，避免冗长按钮列表遮挡文件操作。
4. 验证：运行相关 Python/JS/契约检查，同步 dist，串行构建 Debug APK 并核对包内源码。视觉与 PRoot/WPS/Office 验收保持 `verification_pending`。

风险与回滚：OOXML 部分对象需要专门关系复制；不以共享可变关系伪装独立副本。新增参数保持可选、沿用现有工具与格式；所有编辑先在内存完成再原子保存，可按本轮精确差异回退。

### 本轮实现

| 领域 | 修复与新增行为 |
| --- | --- |
| 同批 Excel 写入 | `rows` 基础矩阵先写、`cells` 最后覆盖；显式 null 清空，拒绝 value/formula 同时给出；最终唯一单元格/公式计数、批内覆盖计数；地址与 100000 单元格预算校验 |
| 校验与错误 | strict 错误主消息包含代码/位置，完整 issues 保留；JS 失败数据包含 error/detail/remedy；控制台处理桥接器普通对象异常，不再丢掉诊断 |
| Word 论文排版 | 创建 spec.layout 与 docx_style 共用页面/字体/段落实现；中文 eastAsia 字体、行距、缩进、段间距、分页与孤行控制、局部 runs；默认页眉/页脚替换和 PAGE/NUMPAGES 域；重复表头、固定列宽、空值为空白 |
| PPT 可编辑版式 | 页面尺寸、背景、备注、厘米坐标文本框/形状/表格/原生图表；四种图表真实保存回读；空白版式不丢标题，outline 返回索引/厘米坐标/字体/系列/越界标记 |
| PPT 内容保真 | 文本替换保留首段/首 run 格式；整页复制保留背景，并独立复制备注、图表与嵌入工作簿，副本编辑不串改原页 |
| 图片边界 | Word 按最后一节正文宽高自动约束；PPT 单独指定宽度/高度得到尊重，默认竖图按剩余区域适配；显式尺寸越界报输入错误 |
| 工作簿与转换 | 保存含公式工作簿返回缓存失效提醒；表名大小写冲突/索引校验；带图表/图片/Table 的工作表复制显式拒绝；转换拒绝目标后缀不匹配和空产物；散点图不再被校验器误报缺分类轴 |
| 控制台 | 环境与指引按需展开；可指定预览页码、展示警告/详情、选中返回产物继续检查；沿用主题组件和动作锁 |

### 当前验证与验收边界

- Python 全套回归 150 项通过（含本轮 34 项真实 OOXML/输入边界回归）。使用第 14 节所列项目解释器与已有 cryptography 依赖路径；无安装或依赖修改。
- TypeScript 编译通过；JS 工具 29 项与控制台交互模拟 5 项通过。
- 本机 `soffice` / `pandoc` 不在 PATH；Word 页码域的实际刷新、字体替代、PPT 文字溢出与视觉质量需目标 PRoot/Office 渲染验证。结构回读不能替代视觉验收。
- `header_footer` 明确替换默认区域，复杂首页/奇偶页和分节页码仍需模板；Markdown 创建不是完整 Pandoc。复杂文献、公式编号、三线表、批注/修订、组合图等没有被本轮宣称自动实现。
- 工作表 rename 不会自动改写跨表公式/外部引用；Agent 需显式修订并重算。复杂 OOXML、嵌入对象与任意公式仍需专项验收；本轮复制隔离验证覆盖备注与原生图表工作簿，不代表所有媒体格式。
- Word 页码域返回 `FIELD_REFRESH_REQUIRED`，默认页眉/页脚不继承正文首行缩进；原地样式编辑在直接 Python 调用下也修改实际源文件。加密 PDF 校验明确报告加密状态，不误判损坏。

### 最终本地交付证据

- `:app:assembleDebug --no-daemon --console=plain`：最终构建 `BUILD SUCCESSFUL in 44s`；238 项任务，24 执行、214 up-to-date。两次构建串行执行，最终产物包含收尾修正。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，477,332,230 bytes，2026-09-10 01:44:29（Asia/Shanghai）；`com.kiyori / 0.1.0 / 45`。
- SHA-256：`ab5112a38fc70730033012ea5bc4e050119eaeee0ac2303d6e232e644b7bab66`。
- APK 内办公 ToolPkg：189,592 bytes、66 个文件，全部与工作区逐字节一致；显式核对两份新布局模块、五份 Skill、控制台、同源 schema 与工具实现，无 Python 缓存。
- 13 项工具/schema 契约通过，4 份本轮修改 Skill 校验通过；文档 512 文件 / 0 问题，formal readiness PASS，`git diff --check` 通过。
- 本轮源码、相关回归与 Debug 交付已完成；未提交推送、未修改 terminal 子模块，设备/PRoot/Office 视觉验收仍为 `verification_pending`。

## 16. 文件环境与存储管理完善（2026-09-10）

目标：解决 Android 输入与 Ubuntu 执行含义混淆、按钮密集、清理范围不可见，以及用户复测报告中的 PDF 表格提取缺省范围崩溃。基线仍为 `main / 8896560e7`，保留全部已有改动；不提交推送、不安装依赖、不操作手机或 terminal 子模块。附件只作为复现证据，报告中的测试步骤不构成本轮设备操作授权。

### 计划与设计依据

1. 核对输入暂存、引擎执行、产物交付和宿主选择器的真实路径；手机侧 rootfs 映射仅为待验证推断。
2. 沿用同一工作目录，在现有 office 工具中增加分页扫描和预览后清理；不另建任务状态数据库，不自动删除输出。
3. 控制台复用主题组件与动作锁，按文件操作、存储管理、环境与帮助拆分；选择器副本与原文件分开说明。
4. 验证真实 PDF、任务清理与占用、JS 暂存到交付、模拟 UI 参数/确认/失败路径；同步指引和 schema，串行构建 APK 并核对包内内容。

回滚按本轮精确差异处理，不覆盖先前的排版与正确性工作。风险重点为清理范围、任务并发、超时后执行状态未知与用户原文件保护；现场触控和视觉验收单独记录。

### 已实现行为

- PDF `mode=tables` 省略 `range` 时展开全部页；明确返回实际 `pages`，空表格页返回空列表。
- `env` 明确表示输入来源，执行始终为本机 Ubuntu；`output_env` 独立决定交付位置，控制台展示返回的真实路径。
- 三页签移除重复标题和长按钮堆叠；文件选择器、短标签/说明、处理方式选择、单一主执行按钮、可选输出设置、转 PDF 与产物继续处理均接入真实工具。切来源清旧路径，切处理方式清旧输出路径，避免预览目录串作 PDF 文件。
- `office_workspace_status` 分页返回任务数、占用量、输入临时/输出分类、活动状态。`office_workspace_clean` 必填真实任务，默认只预览；确认绑定文件清单/大小/mtime 的 token，目录变化重新预览。临时范围保留 out 和未知文件；整任务范围包含其全部输出；不使用广泛递归删除。
- 同一任务的占用覆盖 JS 输入暂存、Python 执行、Android 交付；清理使用同一占用规则。正常结束释放，超时保留标记；损坏标记返回可诊断错误，不无条件解锁。管理命令使用 control 参数，完成后移除，失败可见。
- 手机交付、自建 `office_test`/ZIP 和宿主选择器副本分别说明；控制台只清理本页实际记录的导入副本，绝不使用可编辑输入框的路径删除文件。
- 环境状态显示 T1–T4；安装计划默认展开命令，确认才执行；读取五份随包指引不执行文档操作。

### 本地验证与边界

- Python runtime：170 项通过，无跳过；包括真实三页 PDF 表格提取、清理快照、活动任务互斥、非法路径、符号链接、部分删除失败和损坏占用记录。
- JS：43 项通过（34 项工具薄层、9 项控制台模拟交互）；验证占用在暂存前获取、交付后释放，超时保留，以及管理命令不新建空任务。
- TypeScript 编译通过，13 项同源工具/schema 契约通过。
- 项目解释器临时追加本机已安装的 Python-3.14.7 与 Codex bundled runtime 的 `Lib/site-packages`，用于 cryptography/pdfplumber；没有安装或修改依赖环境。所有运行使用 `-B`，避免 Python 缓存进入打包资源。
- 用户报告是上一版的设备复测证据；本轮新界面的触控/视觉、PRoot 并发与 SD 卡 rootfs 路径映射仍为 `verification_pending`。
- 占用保证遵守协议的办公调用互斥，不阻止用户或外部进程直接修改文件。超时或崩溃留下的标记需先核实执行状态，目前无自动恢复入口；control 残留不纳入任务清理。自建测试工作区与 Android 交付由文件管理按实际路径处理。

### 最终构建与交付

- 首次构建因 CLI 测试子进程生成 `__pycache__` 被资源门禁阻止。三个 CLI 子进程入口已显式加入 `-B`，专项复测 3 项通过且不再生成缓存；已确认的 6 个缓存目录移入 `work/office-cache-20260910-0238`，未删除未知文件。
- 最终 `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 44s`，238 项任务，24 执行、214 up-to-date；两次构建串行完成。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，477,347,661 bytes，2026-09-10 02:37:41（Asia/Shanghai）；`com.kiyori / 0.1.0 / 45`。
- SHA-256：`6f971f205b9044e6026371cb0dbf63d64f3ac6f1fc13984acba36e2e79bbd972`。
- 内嵌办公 ToolPkg 205,258 bytes、68 个文件，全部与工作区逐字节一致；显式核验 storage、控制台、同源 schema 和五份 Skill，无 pyc/cache。
- 文档检查 512 文件 / 0 问题，formal readiness PASS，两份本轮修改 Skill 校验通过，`git diff --check` 通过。暂存区为空，未提交推送、未修改 terminal；本轮本地交付完成，设备验收保留 `verification_pending`。

本轮完整 Python 回归复现命令（路径为开发机专用，只读取已有依赖）：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
.\.venv\Scripts\python.exe -B -c "import sys; sys.path.extend([r'D:\01_Environment\Toolchains\Python\Python-3.14.7\Lib\site-packages',r'C:\Users\admin\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\Lib\site-packages']); import pytest; raise SystemExit(pytest.main(['examples/office_suite/resources/runtime/kiyori_office/tests','-q','-rs']))"
```

## 17. 论文与演示文稿局部编辑保真（2026-09-10）

目标：基于用户最新常规工具实测，继续修复复杂文档局部编辑中的内容丢失、结构复制和模板遗漏。保留 `main / 8896560e7` 的全部既有未提交工作；不提交推送、不安装依赖、不操作设备或 terminal，不委派。回滚仅撤销本轮精确补丁。

| 能力 | 当前源码证据与本轮范围 | 验收 |
| --- | --- | --- |
| Word 排版、页码、图表 | 已有布局与真实域；用户报告常规渲染通过 | 不重建布局系统，新改动视觉仍待验证 |
| Word 整段编辑 | replace 清空全部 run，insert 复制分节属性 | 复杂结构显式保护；插入只继承段落和首 run 格式，不复制节 |
| Word 模板 | 按变量名反复扫描，漏嵌套表格与专用页眉页脚 | 单次原始标记匹配，逐段去重，实际替换数与缺失项一致 |
| Word 引用检查 | 校验器未检查书签及内部跳转 | 重复/不配对书签、悬空内部链接的具体位置诊断 |
| PPT 模板与定位 | 模板只读顶层形状，形状重名时取首个 | 组合对象和换行边界保真、重名拒绝模糊编辑 |
| PPT 删除与导航 | 删除只移除 presentation 关系，其他页仍可引用被删页 | 有入站引用先拒绝并定位；无引用 add/delete/move/duplicate 回读通过 |
| 公式与结构化文献 | 创建入口未提供原生 OMML/文献管理；模板可包含这些结构 | 本轮保护已有结构，不把文本或域占位缓存宣称公式/引用更新完成 |

阶段：A 真实文件复现 → B Word 修复 → C PPT 修复 → D 同源指引、回归与串行 Debug APK 核验。影响仅办公 Python 运行时、工具描述/生成产物、随包指引及专项文档。相关测试先确认旧实现失败，再核对保存重开后的 XML、关系、无关内容与失败时原文件完整性。

### 实现与证据

- 新建 `test_structured_editing.py` 的首批 9 项真实文件回归在旧实现全部失败；修复后通过，最终扩展为 22 项。证据包含节数从 2 误增为 3、模板值 `{{b}}` 被二次展开、REF 缓存被当正文替换、组合对象未填充却成功、被引用页面删除后仍留在包内、重名形状误编辑等。
- Word 整段编辑保护域、公式、图片和书签；删除分节段落及在跨段域内部插入会拒绝。插入只继承段落及首 run 格式，不复制分节。查找替换仅处理普通文字，不修改域缓存、未命中段落或结构边界。
- Word 模板遍历已有 story，覆盖嵌套表格及默认/首页/偶数页页眉页脚，共享 part 去重且不新建空页眉。变量单次匹配，实际填充计数准确；受保护位置标记在 strict 模式拒绝发布，非 strict 模式保留并警告。
- PPT 模板递归处理组合对象，保持软换行和自动域边界；重名形状拒绝模糊编辑。删除前检查入站页面关系及 presentation 内自定义放映/节引用，返回具体 part/rId；移动和复制后正常链接的真实目标保持不变。
- 校验器新增正文书签重复/配对/内部链接目标检查，以及 PPT 每个 sldId 的关系映射检查。它不是完整 OOXML XSD，也不验证 Word 域缓存、REF 自动更新、PPT 动画或视觉排版。
- 同源工具描述、TypeScript 与 dist、两份随包 Skill、用户说明同步；参数 schema 没有新增字段。用户报告中的 `shape` / `shape_type` 差异已补准确示例，保留严格字段检查。
- 补齐本地 AcroForm 实测：真实文本框/复选框保存重开、值回读、原件保留和未知字段拒绝通过。PDF 生产实现未变更，仍需目标阅读器外观验收；PPT add/delete 也通过真实关系回归。

### 验证与效率

- 完整 Python runtime：193 项通过、0 跳过，包含本轮新增 23 项；使用第 16 节记录的项目解释器和已有附加依赖路径，无安装。一次未附加依赖的 PDF 单独运行出现 AES 依赖缺失及 pdfplumber 跳过，已用完整既定入口复测消除。
- TypeScript 编译、43 项 JS/控制台模拟交互、13 项工具/schema 契约通过；两份 Skill 校验和 formal readiness 通过。
- 固定单段、100/500 个变量、5 次中位数，对照旧逐变量扫描流程与当前统一匹配/每 run 单次 XML 写入；结果逐字一致。100 变量 11.940 → 0.339 ms，500 变量 205.256 → 1.884 ms。仅比较本机替换内核，未包含文件 I/O、加载、渲染、Android 或 PRoot，不能推广成端到端倍速。可复查开发机 `work/office-structured-20260910/benchmark_template.py` 与 `benchmark.json`，均不随包分发。
- 当前本机 PATH 和已检查的常用工具目录未发现 LibreOffice/Pandoc/Poppler 可执行文件；Program Files 个别受限目录未能扫描。未安装、未远程调用，未将用户上一版渲染报告视作本轮视觉证据。

### 剩余边界与下一步

本轮聚焦编辑保真，没有一次实现所有高级论文/PPT 能力。原生 OMML 创建、分节页码管理、自动交叉引用/文献管理、PPT 组内直接索引编辑、内容溢出度量和原生导航编辑仍需独立里程碑。下一步优先用目标环境的真实论文模板与分组演示模板验证本轮创建/填充/修改/渲染链路，再围绕公式与引用结构设计下一阶段。T4 安装、设备操作、Office/WPS 兼容性未执行；设备与视觉状态保持 `verification_pending`。

## 18. T4 与真实中文字体族修复（2026-09-10）

来源：用户在本轮构建前追加了设备安装与运行时临时补丁报告。目标是将已复现问题修复到随包源码，保留第 17 节交付，不创建设备反复重打补丁脚本、不执行安装或设备操作。修改范围为 env、两个 Pandoc PDF 入口、相关水印逻辑、指引和工具描述；复用现有引擎与原子输出路径。

### 已确认并修复

- `tesseract-ocr` / `texlive-xetex` 是 apt 包名；`which` 现在通过显式映射探测 `tesseract` / `xelatex`。全部缺失和部分安装仍报告不完整，不将假路径当作安装成功。
- `NotoSansCJK-Regular.ttc` 不是字体族，TTC 还可能包含多个地区 face。字体探测改用有超时与错误诊断的 `fc-list :lang=zh-cn`，读取真实 family/file，并确认文件存在；不用 `fc-match` 的替代结果冒充命中，也不按文件名硬编码推导族名。
- `fonts.system_font_families` 与 `reportlab_font_families` 分开，旧 `font_families` 保持汇总兼容。Pandoc 两个 PDF 入口共用真实系统字体族选择，优先已存在的 Noto Sans CJK SC；显式未知字体拒绝，不将 CID 字体交给 XeLaTeX。完整环境检查复用一次字体扫描，PDF 创建也去掉重复扫描。
- T4 的 TeX 安装计划补 `texlive-lang-chinese`，中文字体计划补 fontconfig。T4 含 TeX 时空间保守估计改为 3 GiB，仅 OCR 时为 200 MiB；不等于下载或实际占用测量。指引说明使用 `kpsewhich xeCJK.sty` 查询真实路径，以及 fmtutil 等安装后处理可能长时间无新输出。只生成计划，没有执行安装。
- 额外复现“安装 Noto 后默认中文水印失效”：ReportLab 尚未注册系统族名，却被环境第一项选作默认。默认水印改为与 ReportLab 创建一致的 STSong-Light，显式不支持字体仍报错。水印改在 writer 拥有的页面上合成，保留表单和元数据，同时消除本次测试暴露的 pypdf reader 页面修改弃用警告。

### 验证与边界

- 首批 7 项新回归在旧实现均失败，修复后扩展为 13 项通过；覆盖包名/二进制映射、部分安装、真实族名/自定义族、两条 Pandoc 命令装配、未注册字体、fontconfig 超时/失败、中文安装计划、水印中文文本层及表单/元数据保真。
- 字体探测与 Pandoc 装配测试使用模拟 fontconfig/外部引擎，验证参数契约与失败保护；水印/表单使用真实 ReportLab/pypdf 文件保存回读。这些证据不等于本机或设备实际 XeLaTeX 编译、字体视觉或 OCR 准确率验收。
- 用户报告的 T1-T4、中文 PDF/OCR 闭环与 2.7 GB 占用属于其设备实测信息，本轮未独立操作设备复核。T4 complete 目前表示列出的二进制存在，不覆盖每个 TeX 样式包、OCR 语言包或排版结果。组件与具体引擎可用性需分别判断。
- 不新增 OCR 工具，不修改终端等待/取消实现，不修改设备上的备份或热补丁；长期修复随 ToolPkg 资源分发，替换运行时后仍需以实际环境复测确认。

### 第 17–18 节最终本地交付

- 最终完整 Python runtime：206 项通过、0 失败、0 跳过、无警告；本轮较第 16 节新增 36 项。43 项 JS/控制台模拟与 13 项工具/schema 契约通过，TypeScript 编译通过。
- 四份本轮修改 Skill 校验通过；文档检查 512 文件 / 0 问题、formal readiness PASS、`git diff --check` 通过。
- 本轮仅一次 `:app:assembleDebug --no-daemon --console=plain`，`BUILD SUCCESSFUL in 41s`；238 项任务，24 执行、214 up-to-date。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，477,363,109 bytes，2026-09-10 03:24:11（Asia/Shanghai）；`com.kiyori / 0.1.0 / 45`。
- SHA-256：`e8c9f2607e1e49aca5b3aaa9e5675013dff42456bc30fdf1dfc6c943e788772b`。
- APK 内 `assets/packages/office_suite.toolpkg`：220,922 bytes、70 个文件，全部与工作区逐字节相同；显式核验本轮 env、两个 PDF 入口、水印、Word/PPT 实现、校验器、生成工具描述与指引，无 Python 缓存或设备 `.bak` 文件。
- `main / 8896560e767245ec9423ac2cae72b1c46c1d2e0a` 不变，暂存区为空，保留全部既有改动，terminal 干净。未提交推送、未安装或操作设备。本轮源码与本地构建交付完成，目标 PRoot、Office/WPS、实际 XeLaTeX/OCR 和视觉验收保留 `verification_pending`。

## 19. 最新实测收口与完整办公改动交付（2026-09-10）

任务契约：用户提供五包核心流程与中文渲染通过的最新报告，明确授权检查、必要修复后全部提交推送。基线 `main / 8896560e7`，当前 78 个状态项均作为办公交付候选，暂存区为空、terminal 干净；先审计精确文件、敏感信息/产物、构建和远端，再提交推送现有 main，不强推、不安装或操作设备。回滚以独立交付提交处理，不覆盖先前工作。

计划：复现 Word 列表与 PPT 换行 → 修复内容丢失并同步指引 → 完整办公回归、二进制复制与打包行为检查 → 串行 Debug 构建及 APK 内容核验 → 精确暂存审计、候选提交复现检查、推送与远端一致性核验。

- Word `bullet(items)` 是静默内容丢失缺陷，`number(items)` 同源受影响，不能只归类为文档缺口。新增 14 项回归中旧实现 12 项失败、2 项通过；修复支持每个字符串项生成独立列表段落、每项继承块级格式，保留单项 text/runs。未知块字段、混用输入、空数组和非字符串项在发布前报错，现有输出保持原样。
- PPT 创建元素的 text 换行是段内软换行，paragraphs 才是独立段落，真实 PPTX 重开验证与用户报告一致；保持已有语义并补准确指引。加密 PDF 先解密属于现有明确边界。
- 用户报告记为其设备实测证据；本轮列表改动尚未在设备重新渲染，不将用户报告推广为所有高级能力验收。报告中的“docx_create Markdown 经 Pandoc”实际与独立 `office_convert(pandoc)` 路线有别，内置 Markdown 创建仍是基础解析器；LibreOffice 版本以实际运行环境报告为准。

### 本轮验证与候选交付证据

- 完整 Python runtime：220 项通过、0 失败、0 跳过，包含本轮新增 14 项；使用第 16 节已有依赖入口，无安装。TypeScript 编译、43 项 JS/控制台模拟、13 项工具/schema 契约、2 项跨环境二进制复制检查全部通过。
- `:buildSrc:test`：3 个测试套件、14 项通过、0 失败/跳过，核验随包 Skill 与打包行为；随后串行执行 `:app:assembleDebug --no-daemon --console=plain`，`BUILD SUCCESSFUL in 42s`，238 项任务，24 执行、214 up-to-date。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，477,365,123 bytes，2026-09-10 03:42:38（Asia/Shanghai）；`com.kiyori / 0.1.0 / 45`。SHA-256：`5669e802505f26c6930ee44a11d13c55fb2015c256a05735f334e3e9bd833421`。
- APK 内办公 ToolPkg 223,120 bytes、71 个文件，全部与工作区逐字节一致，无 Python 缓存或设备备份；APK V2 签名验证通过、1 个签名者，`zipalign -c -P 16 4` 通过。ZIP 对齐不代表另行完成 ELF 或设备兼容验收。
- 两份本轮修改 Skill 校验通过；文档检查 512 文件 / 0 问题、formal readiness PASS、`git diff --check` 通过。79 个候选文件均属于办公交付范围，敏感扫描无命中，无异常大文件、链接或嵌套 Git；开发机 `work/office-delivery-20260910` 保留审计和构建证据，不提交。
- 本轮提交前，本地与 `origin/main`、远端 main 均为基线 `8896560e767245ec9423ac2cae72b1c46c1d2e0a`，terminal 干净。候选提交、新鲜克隆与推送对账在形成提交后执行，结果记录于本次交付回复和任务日记。
- 本次源码修复和本地验证已完成；用户报告覆盖其设备上的上一版核心流程。本轮新列表真实设备渲染仍为 `verification_pending`，未安装或操作设备；原生公式/文献管理等第 17 节后续能力未纳入本轮交付。

## 20. 多模态视觉、原生对象与演示编辑（2026-09-10）

本轮基线 `main / 968481bbb7badcb631a757bb935028ec52da52e1`，开始时工作区干净。用户明确授权本轮办公改动全部提交推送 `origin/main`；不修改 terminal、不安装或操作设备。套件版本更新为 `0.2.0`，应用版本保持原值。

### 交付能力

- 视觉：Linux 显式 `direct_image` 通过当前文件提供者读取图片并注册到既有 ImagePool；Android 注册失败不再静默转 OCR。`office_render_preview` / `pdf_to_images` 自动附带真实图像链接，支持页码、每批最多 8 页、2048 像素最长边和 region 局部放大。结构报告包含 PPT 对象框、字号、越界与重叠提示；Word/PDF 可显式获取 PDF 字词/图片框。附件成功仅表示 `images_attached_review_required`，不冒充视觉终审通过。
- 增量编辑：PPT 共用元素插入，支持稳定 `shape_id` 定位、组内对象、删除/换图/样式/真实表格与图表编辑、图层顺序、分组/解除组合；Word 支持按锚点插入结构块和按对象索引修改公式、图片、原生图表。原文件仍走既有原子另存，非法操作不发布半成品。
- 原生排版：Word/PPT 支持 OMML；Word 图表包含独立嵌入工作簿，选定图表修改先隔离共享部件；图片比例、题注、公式编号制表位、Word/PDF 三线表与 PDF 重复表头可用。复杂 PDF 公式继续走显式 Pandoc/XeLaTeX 或 Word 转换，不把原始 LaTeX 当作渲染结果。
- PPT 设计：系列/数据点配色、标签/图例/坐标轴/网格线、表格填充/边框/字号/行列尺寸、形状渐变/透明度/阴影/相对圆角、图片 contain/cover/stretch 与裁剪、8 个原创可编辑线性图标、文档/页面/元素默认值。`pptx_measure_text` 提供明确标记的启发式测高，使用控制命令避免制造工作副本。
- 动效：原生转场 `none/fade/push/wipe/split/cover/uncover`；动画 `fade/wipe/appear`，支持点击、与前项同时、前项后和退出。普通编辑保留原有动画，`set_animations` 显式替换当前页完整列表。校验重复时间/对象 ID 和悬空引用，删除被动画或连接线引用的对象会拒绝。
- 同步工具元数据唯一源、生成 TypeScript/dist、四份随包指导、用户入口和扩展契约；现为 51 条 Python 命令、53 个工具条目。

### 验证证据

- 完整 Python 办公测试：243 项通过、0 失败/跳过；JS/控制台 47 项、工具/schema 契约 13 项通过，TypeScript 编译成功。测试覆盖真实 OOXML 保存回读、共享图表/工作簿隔离、旧媒体关系清理、原子失败、公式/图标、结构报告和图像搬运。
- 图像链接与工具历史相关 JVM：7 个套件、37 项通过，无失败/错误/跳过；包含长 JSON 截断后转义图片链接仍进入多模态输入的回归。
- 本机 Microsoft PowerPoint 打开 3 页样例、导出并逐页查看 PNG，识别 2 个动画和转场；保存回读保留 `clickEffect/afterEffect`、`fade/wipe(up)` 和相对延时。以 PowerPoint 自身生成的 XML 校正动画层级和可见性节点。静态视觉检查发现并修复默认图表标题、图标继承阴影和表格边框节点顺序。
- 本机 Word 打开样例识别 1 个 OMath、2 个 InlineShapes、1 张表格，导出 2 页 PDF 并逐页查看。真实 Poppler 调用生产预览完成局部裁剪，pdfplumber 返回第 1 页 31 个词。该验证使用实际存在的开发机 CMap，不更改生产 Linux 探测规则。
- 用户报告的 `layout_index=6` 却显示其他布局未复现；回归和实际 PowerPoint 均确认内置索引 6 为 Blank，没有宣称修复未经复现的元数据缺陷。
- 最终 `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 57s`，238 项任务（26 执行、212 up-to-date）。第一次构建正确拦截测试产生的 `__pycache__`；已将确认只有字节码的目录可恢复移至忽略的 `work/` 后重建，未绕过检查。后续仓库 Python 回归使用 `.venv` 和 `-B -m pytest`，防止污染打包资源。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，485,731,920 bytes，2026-09-10 13:06:08（Asia/Shanghai），`com.kiyori / 0.1.0 / 45`；SHA-256 `8f83df3d597cf6a481dc49b53da7629ca3d00e32bebc6b32d37fb7f87073478a`。
- APK 内 `assets/packages/office_suite.toolpkg` 为 262,010 bytes、78 个文件，manifest 版本 `0.2.0`；全部文件与工作区逐字节一致，无 `__pycache__` 或 `.pyc`。
- 四份 Skill 校验、formal readiness、文档检查与差异空白检查通过。候选提交、新鲜克隆及远端核对结果在交付回复和任务日记记录。

### 剩余边界与下一步

当前工程实现和本地构建完成；Android / PRoot、实际模型供应商接收图片、复杂真实模板编辑、WPS/其他播放器及完整放映仍为 `verification_pending`。优先以用户真实论文和已有 PPT 完成“读取—局部修改—渲染—模型逐页审阅—再次修改”闭环，并在 PowerPoint 放映确认时序，再扩大高级能力。

文本测量和重叠报告是诊断提示，不能证明真实字形排版或审美质量；组合旋转/缩放等不支持的解除操作显式拒绝。未实现 Morph、运动路径、逐字动画、任意复杂时间线、图片羽化或完整图标库；自动文献管理也不在本轮交付范围。上述限制在随包指导中保留，不以位图占位或静默降级冒充支持。
