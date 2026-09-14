---
status: completed
updated: 2026-09-14
---

# 文档体系整理与开发入口优化

## 2026-09-14 全仓文档治理与排版完善

### 任务契约与基线

- 目标：审阅父仓自有文档，规范职责、命名、目录和排版，纠正有源码依据的事实错误，防止 README 再次堆积内部进度或逐控件说明。
- 范围：父仓全部 Markdown 的结构与导航检查，根入口、用户指南、正式协议/架构、专项与历史引用，以及必要的文档检查器和 CI。
- 非目标：修改产品运行时、重写第三方许可证或协议夹具、设备安装、正式发行、改变远端设置。子模块保留独立文档所有权。
- 基线：`main@9f2c1fb92e83f073c2662c80ad3552fdabc8ce76`；工作区干净，terminal 固定 `bc4aeed3e791f0a6157496f3859f70357766f90f`。
- 授权与恢复：用户明确要求全部提交推送；交付仅包含本轮审阅的文件。按基线和 Git 重命名记录恢复，不执行破坏性重置。

### README 变更说明

- 目标读者：首次获取 Kiyori 的用户，以及需要了解当前能力与限制的新贡献者。
- 变更理由：用户明确要求文档深度整理；README 已重复积累文件、终端和审计操作细节，且把未实现的统一风险确认设计写成现有保证，需要精简与纠错。
- 权威来源：`ToolPermissionSystem.checkToolPermission`、`KiyoriFileManagementPage`、`FileManagerHistoryStore`、Manifest/Receiver、正式用户指南与仍为 `design_required` 的授权专项；安装入口沿用当前内测预发布。
- 排版验证：521 篇结构/导航检查为 0 问题；505 篇自有正文以 Marked GFM 和本地 Edge 在 1280/390 px 下完成 1010 次渲染结构检查，抽查根入口、指南与表格截图。复杂 Mermaid 和外部徽章不由该离线预览验证。
- 双语核对：两版保留同一产品愿景、安装入口、关键能力与限制；同步移除历史数量与未实现的统一风险确认保证，详细中文步骤通过指南链接提供。

### 阶段状态

| 阶段 | 交付与证据 | 状态 |
| --- | --- | --- |
| 调查 | 521 篇 Markdown 基线；按自有正文、历史、协议夹具、运行时资源与子模块区分 | 已完成 |
| 内容与结构 | README 分层、指南纠错、协议与架构核对、160 处命名迁移、45 篇长文导航 | 已完成 |
| 自动验证 | 结构、引用、README 说明、检查器回归、CI 接线与历史正文对账 | 已完成 |
| 渲染与构建 | 宽屏/窄屏预览、串行标准 Debug APK 与元数据 | 已完成 |
| 提交推送 | 精确允许清单、候选树、fresh clone、远端 main 一致 | 主体已完成；本节收尾记录随附提交 |

### 关键纠错与边界

- 现有工具调用权限不等于统一 R0–R3 动作授权；后者仍为目标设计，未由本轮文档维护实现。
- Intent 示例使用 Kiyori application ID 和完整 Operit Receiver 类名；兼容 Action、数据格式与类名保持不变。
- HTTP 设置入口修正为“设置 → AI 助手 → 服务与用量 → 局域网与自动化”。
- 流式 Markdown 主入口使用 native 分块并保留收集文本，移除旧文档的 KMP 主路径与无界低内存保证。
- 用户指南接受内测 Debug APK 与自行构建两种来源；“尚未正式发行”不等于没有内测附件。
- 文档命名只统一编号与描述性名称，保留稳定主题目录、公开脚本指南和历史日期；旧路径与新路径由 Git 重命名差异追溯。
- 自动检查不证明所有 API、设备行为或外部 URL 均可用；历史验收状态与原哈希不重新背书。现场、远端 Actions 与正式发行仍分别验收。

### 本轮验证与交付证据

2026-09-14 本地证据：

- `check_documentation.py --repository . --base 9f2c1fb92e83f073c2662c80ad3552fdabc8ce76`：521 篇，0 问题；已标记页内目录由 `--write-catalogs` 同步。
- `python -B -m unittest ci.test.test_documentation ci.test.test_markdown_links ci.test.test_pr_check ci.test.test_formal_readiness`：68 项全部通过；包括 README 旧说明拒绝、命名碰撞、表格、围栏、中文/重复锚点和导航幂等回归。
- formal readiness 为 PASS，`git diff --check` 通过。CI 增加同基线文档结构检查；本地审阅 YAML 差异，未运行远端 Actions 和本机不可用的 actionlint。
- 159 篇编号迁移正文在排除路径更新和派生导航后与基线一致，另外 1 篇为重新组织的维护规范。22 篇历史记录只更新引用，原正文对账无差异；无旧文件名引用残留。
- 全量裸源码路径核对中，5 处不在本仓的路径属于明确链接到固定旧 `kiyori-android` 提交的参考源码，保留其来源语义。
- 本地 GFM 预览覆盖 505 篇、1010 个视口，未发现页面级横向溢出、一级标题数量错误、表格列数不一致或缺失的本地图片；宽表与代码独立横向滚动。其余 16 篇为运行时模板/内容资源或协议夹具，不按开发文档重排。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 2m 24s`，238 项任务中 22 执行、216 up-to-date；唯一 launcher、脚本代理和播放器 runtime packaging 验证通过。

APK 为 `app/build/outputs/apk/debug/app-debug.apk`，488291558 bytes，SHA-256：
`897250d95726978d0d25765d99e44577f51d68125237a2cb1b8f2ab7440ba52e`。
本轮只改文档与检查工具，打包任务复用已有产物，文件写入时间为 2026-09-14 06:05:02 +08:00；
不能把本轮构建结束时间写成 APK 重新生成时间。独立核对 `com.kiyori / 45 / 0.1.0`、
minSdk 26 / targetSdk 34 / compileSdk 37、arm64、V2 单 signer 与 16 KiB ZIP 对齐通过。

内测标签 `v0.1.0-internal` 经 GitHub 只读核对为 `isPrerelease=true`。本轮不更新预发布附件，
不执行设备安装、服务调用验收或正式发行。

### Git 交付与最终边界

主体提交为 `8ffa328f11ae7ff331fb42162d304aac2d072a28`，包含 290 个变更条目，
2374 行新增、828 行删除（Git 已识别编号迁移，不把完整移动正文算作删除）。
相对本轮起点执行候选树 Markdown 链接与仓库卫生检查，均为 0 errors / 0 warnings；
没有旧断链抵扣，卫生检查覆盖 289 个新增或修改文件。新鲜克隆对主体候选 PASS，
terminal 固定提交可获取，克隆工作区干净。

主体已推送 `origin/main`，本地 HEAD、跟踪分支和 `git ls-remote` 的远端 main 一致。
首次 HTTPS 推送因环境 Token 缺少 workflow 权限被拒绝；随后在单次 Git 进程中使用
同账号既有 keyring 授权成功，没有更换远端或修改全局凭据配置。未提交私密配置、运行产物、
子模块变更或产品运行时代码。本页与总索引的收尾提交只回填已取得证据，最终 SHA 以 Git 与交付报告为准。

本轮文档与本地验证交付已完成，无新增阻塞。统一风险分级仍待其专项实施；
设备交互、实际网络/模型、远端 Actions、复杂 Mermaid 和外部徽章保留各自验证边界。
后续文档变更遵循维护规范，在必要入口记录新事实，避免再次把详细操作和单次结果堆入根 README。

## 2026-09-12 深度整理与全仓质量审查

本轮在 `main@b8a0caa1f425da613fd579f957deac3555164ff1` 的干净工作区开始；
`terminal` 固定为 `3a5f22da2afc3b83f47c05907420b8fbd891e76e`，工作区干净。
以下记录本轮范围；后文 2026-09-06 的已交付结果保留为历史证据。

### 任务契约

- 目标：规范全仓文档的阅读路径、职责、命名、语言和事实边界，恢复可持续开发的质量基线。
- 范围：父仓维护文档、CI 检查、所有 Android 模块与自有脚本的本地测试；修复实际诊断和源码审查证实的缺陷。
- 非目标：无证据的重构、机械依赖升级、稳定协议改名、设备安装、正式发行与远端配置变更。
- 授权：本轮验证和审计后的全部交付提交并推送 `main`；子模块如有修改，独立审查与先行交付。
- 恢复边界：以起点提交及精确变更清单回溯，保护本机输入和并发改动，不用破坏性重置。

### 阶段与验收

| 阶段 | 交付与影响面 | 验收证据 | 状态 |
| --- | --- | --- | --- |
| 01 文档调查 | README / CONTEXT / docs / 模块与工具说明；区分现行事实、历史和第三方材料 | 全量清单、格式/链接扫描、关键源码与历史核对 | 已完成 |
| 02 文档实施 | 精简入口、详细用户指南、定位一致性、CI 验证路径及历史分层 | 工作区文档检查、候选树链接检查、相关检查器回归 | 已完成；候选树复核在阶段 04 |
| 03 全仓质量 | Python CI、Node 离线测试与类型检查、ToolPkg 重建、buildSrc 和九模块 JVM/Lint/AndroidTest 编译 | 实际报告、失败修复及必要回归，保留有依据的警告说明 | 已完成 |
| 04 交付 | 最终 Debug APK、候选提交、固定子模块及远端 ref | APK 元数据/签名/对齐，fresh clone，精确提交与推送核对 | 已完成；设备与远端 Actions 分别验收 |

性能和稳定性审查重点为主线程工作量、无界数据、取消与代际、资源释放及错误可观察性。
源码与本地测试只能证明对应路径；流畅度、系统权限、真实网络和设备生命周期继续保留
`verification_pending`，不承诺未经测量的性能提升或零缺陷。

### 本轮基线证据

- 工作区文档检查：518 个 Markdown 文件，0 个问题；当前检查覆盖编码、标题、基本空白与文件链接。
- 已发现入口职责漂移：README 堆积详细页面操作；CONTEXT 产品定义与 README 的长期协作愿景不一致；CI 指南内存在长篇历史 baseline 记录。
- 历史测试数量、APK 和提交不能作为本轮通过证据。

### 本轮交付内容

- README 中英文入口统一职责，详细文件操作迁至用户指南；CONTEXT 与已有产品愿景和未发布边界对齐。
- 新增全仓质量验证指南；CI 历史 baseline 记录单独保存；构建指南统一中文说明，保持现行命令与 native 输入边界。
- 修正 AI 产物、Office 和 Bilibili 默认目录说明，明确修改默认根不搬移现有文件。
- Windows 控制包将产品版本和协议版本分离：认证握手显式返回 `protocolVersion`，保留旧 `1.1.x` 服务兼容，拒绝显式无效协议。
- 临时目录清理复用应用 IO scope，改用不跟随链接的遍历，拒绝符号链接根并传播删除失败；审阅后仅更新 Application 对应架构哈希。
- 网络观察请求为 API 26–29 移除默认 capability，API 30 起使用 `clearCapabilities()`，避免旧 Android 调用新 API。
- Compose 使用可观察资源/语言状态，移除未使用的约束子组合，将开关动画位置读取推迟到布局阶段，并使用整数状态容器。
- 补齐五种语言各 194 条缺失翻译；清理 141 个确认无消费者的字符串键（app 139、terminal 2）
  和一个未使用图标，保留格式参数及兼容标识。
- 修复过时的首启、发布资产和网络文案断言；浏览器 Node 夹具退出独立测试发现，终端输入测试验证文件桥的真实返回合同。
- Bilibili TypeScript 检查使用 `noEmit`，正式分发仍由既有构建入口生成，避免测试同步在源码旁遗留 JavaScript。

### 本轮已取得证据

2026-09-12：文档工作区 521 文件、0 问题；独立本地片段链接 70 处、0 未解析；Python CI
340/340、办公引擎 287/287、Node 220/220（显式使用本机 Ubuntu WSL 运行 PTY 夹具）通过。
WebChat 类型/构建、ToolPkg 类型/WASM/测试同步通过，生产白名单已恢复 52 项。
App Lint 的完整临时结果经交集删除 74 条失效记录，保留 5089 条旧记录，新增签名为 0。
详细数量、环境与摘要见 [基线历史](02_lint_baseline_history.md)。最终 Gradle 完整矩阵
`BUILD SUCCESSFUL in 10m`，444 个任务中 61 个执行、383 个 up-to-date；App 在正式 baseline
之外为 0 errors / 65 warnings，terminal 为 0 errors / 16 warnings，其余七模块无诊断。
AndroidTest Kotlin / Java 仅完成编译，未在设备执行。

最终 JVM XML（2026-09-12）：App 2755 项、0 失败/错误、1 跳过（未指定真实媒体输入）；
terminal 92 项、0 失败/错误/跳过；buildSrc 14 项、0 失败/错误/跳过。终端两项真实 Bash / PTY
测试使用 `KIYORI_PTY_WSL_DISTRO=Ubuntu-26.04` 强制复跑，未启用环境安装 smoke。
terminal AAR 与 Lint 通过，子模块提交 `bc4aeed3e791f0a6157496f3859f70357766f90f`
已推送 `origin/main`，本地、跟踪和远端 ref 一致，子模块工作区干净。

最终 Debug 构建使用标准命令 `:app:assembleDebug --no-daemon --console=plain`，
`BUILD SUCCESSFUL in 1m 8s`；238 个任务中 28 个执行、210 个 up-to-date。
APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间 2026-09-12 03:11:48 +08:00，
485554475 bytes，SHA-256：
`e2e79d584e3fd17e0f00c40f4aec60fafce84c3340b43f4c75d4fa2cd1954ab4`。

独立 APK 审计确认 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，
唯一 MainActivity launcher、arm64、V2 单签名与 16 KiB ZIP 对齐通过。54 个 `.so` 和 shell
launcher 的 164 个 ELF `PT_LOAD` 均至少 16 KiB；无重复 ZIP 项、重复 native basename 或
`libsudo.so`，模板 AAPT2 只有共享位置的一份。Windows 控制 / Bilibili / Office ToolPkg 分别
核对 51 / 3 / 80 个包内文件，与当前源文件一致。NDK `llvm-readelf` 独立复核 55 个 ELF 文件、
164 个 LOAD 段全部至少 16 KiB 对齐。

### 本轮 Git 交付

主体提交 `7f8d695237c3139d1bab1aa1ed4fe8ba274805d3` 相对本轮起点运行候选树卫生、
Markdown 链接及本地化检查，均为 0 errors / 0 warnings；无旧断链抵扣。新鲜克隆检查通过，
固定的 terminal 提交可从远端初始化，克隆后工作区干净。

主体提交已推送 `origin/main`；本地 HEAD、跟踪分支和 `git ls-remote` 的远端 main 一致。
本页与总索引的后续收尾提交只记录已取得的证据，最终 SHA 以 Git 与交付报告为准。
父仓与 terminal 已核实均为 Kiyori 独立公开仓库（非 GitHub fork）；仓库公开不等于产品已发布。
本轮本地维护与交付目标已完成，设备、真实服务和远端 Actions 的待验收状态没有由本轮关闭。

### 保留诊断与验收边界

- App 的 65 条可见 warning：40 条依赖/插件版本建议、22 条 `UseKtx`、3 条 `SdCardPath`。
  版本建议没有单独证明当前依赖存在缺陷；本轮不机械升级依赖族。KTX 建议不改变现有
  同步 `commit()` 返回值检查等合同；`/sdcard` 是 `ArtifactPathRules` 的兼容输入别名，
  最终路径由传入的真实共享存储根解析。
- `terminal` 的 15 条 KTX 与 1 条 arm64-only ChromeOS 建议保持可见；当前产品只交付 arm64。
- 网络观察的两处 `ConnectivityManager.allNetworks` 废弃提示保留：初始和全网络快照仍需
  包括默认网络以外的对象，不能简单替换为 `activeNetwork` 或新增平行网络状态表。
- 历史 baseline 仍含已有资源、样式、Compose 和平台诊断。基线收缩不表示所有历史诊断
  已逐项消除，也不证明所有路径零缺陷。
- 本轮没有设备安装、真实媒体服务输入、远端环境配置或发布；UI、系统权限、真实网络、
  解码、帧率与设备生命周期保留 `verification_pending`，不报告未经测量的性能提升。


## 2026-09-06 历史整理记录

### 目标与范围

整理父仓库维护的全部开发文档，重点重构根 README、CONTEXT、AGENTS 与 docs。中文作为开发文档主语言；英文用户入口与明确用于协议、测试、上游归属的原文保留其用途。同步审查并交付任务开始时已有的开源许可页面、测试及准备检查改动。

不改变无关产品行为，不修改子模块和第三方原始文档，不执行设备安装或正式发布。

### 基线与问题

- 分支：`main`；起点：`145f378900d663b812d9df3c73de15fbad56139c`。
- 已有修改：AGENTS、两份开源许可页面、一份许可测试及 `check_formal_readiness.py`；用户已明确授权合并审查、验证并提交。
- CONTEXT 混用语言、超长段落和阶段证据，不适合默认上下文注入。
- TODO 总索引超过六千行，重复承载详细历史与当前状态，检索和维护成本过高。
- README 堆积内部契约，用户上手路径被实现细节打断；AGENTS 存在重复和冲突规则。

### 实施计划

| 阶段 | 工作 | 验收 | 状态 |
| --- | --- | --- | --- |
| 01 调查 | 全量文档清单、格式与链接基线、引用和源码事实核对 | 区分自有文档、协议样例、子模块及第三方材料 | 已完成 |
| 02 入口 | 重写 README、精简 CONTEXT、规范 AGENTS | 用户路径完整、职责不重复、规则无冲突 | 已完成 |
| 03 分层 | 提取领域契约、拆分 TODO 历史、补齐分类导航 | 重要契约与验收证据可追溯，旧引用同步迁移 | 已完成 |
| 04 一致性 | 统一标题、排版、术语，修正失效链接与文档检查缺陷 | 全量自有文档扫描无新增缺陷，检查器回归通过 | 已完成 |
| 05 验证交付 | 审查许可修复、串行 Debug 构建、产物核验、提交推送 | 精确变更清单、父仓与远端 ref 一致 | 已完成 |

### 决策与风险

- 保留已被代码、检查脚本和外部链接使用的稳定目录名；通过分层索引改善结构，迁移必要的超长入口。
- 详细契约迁入正式主题文档；历史计划与验收证据留在 TODO，不能因整理而标记成已完成。
- 文档格式化必须识别代码围栏、协议示例与许可证原文，避免修改机器消费的样例语义。
- 许可归属允许显示上游名称，但检查器不得因此放行整份文件中的产品身份或业务 URL 错误。
- 恢复基点为上述 Git 提交和任务开始时的工作区快照；不使用破坏性重置。

### 已交付内容

- 重写中英文用户入口，新增首次使用、扩展、网络媒体与隐私指南。
- CONTEXT 收敛为 63 行高频契约，七份领域文档承接详细所有权与生命周期；AGENTS 消除重复与发布状态冲突。
- TODO 总入口收敛为 54 行导航；122 个旧章节按日期保留，规范化相对链接和空白后正文对账无差异。
- 正式开发准备分离当前清单与历史 APK/门禁证据；架构控制面、播放器、示例与工具说明统一中文正文。
- 修复断链、示例文件缺失引用、供应商显示名错误、Windows 命令路径和错误的 Operit 上游检查基线；接口示例转向真实类型权威入口。
- 新增确定性全文档/专项目录与检查器；修复 HTML 标题提取、围栏隔离、空白行与非法 UTF-8 处理，保留协议样例和第三方原文。
- 许可页补齐四个项目归属；品牌与 URL 例外限定到已审阅的精确语句，普通业务源码继续检查。

### 验证记录

已取得的验证记录（2026-09-06）：

- 文档检查：`.\.venv\Scripts\python.exe -B ci/script/check_documentation.py --repository . --write-catalogs`，扫描 491 个文件，0 个问题；独立核对 8 处显式 Markdown 本地锚点，无失效目标。
- Python 回归：`.\.venv\Scripts\python.exe -B -m unittest ci.test.test_documentation ci.test.test_formal_readiness ci.test.test_markdown_links`，41/41 通过。
- 正式准备：`.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`，PASS。
- 许可 JVM：`.\gradlew.bat :app:testDebugUnitTest --tests com.ai.assistance.operit.ui.features.about.screens.OpenSourceLicensesTest --no-daemon --console=plain`，BUILD SUCCESSFUL；测试任务复用 UP-TO-DATE 结果，2026-09-06 XML 记录 4 个测试、0 失败/错误/跳过。
- 最终 Debug：`.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，`BUILD SUCCESSFUL in 1m 44s`，235 个任务中 24 个执行、211 个 up-to-date；launcher、脚本代理运行时和播放器运行时打包门禁通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，写入时间 `2026-09-06 14:17:24 +08:00`，502,520,560 bytes，SHA-256 `55A38A0C25B3FD7C33547D22C38F695325C2A6AA0162B28D2DD46EB13850B908`；`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、仅 arm64、V2 单 signer 与 16 KiB ZIP 对齐通过。
- 主体候选 `c9232124e11b398e5b379446ed7ec4a206e5c298`：以起点 `145f378900d663b812d9df3c73de15fbad56139c` 运行 `check_markdown_links.py --base <起点> --candidate HEAD` 和 `check_repo_hygiene.py --base <起点> --candidate HEAD`，均为 0 errors / 0 warnings；检查 213 个变更文件，无遗留断链抵扣。
- 新鲜克隆：`.\.venv\Scripts\python.exe -B ci/script/check_fresh_clone.py --repository .` 对主体候选 PASS，固定 terminal gitlink 可从远端初始化，克隆工作区干净。
- Git 交付：主体提交已推送 `origin/main`，本地 HEAD、`origin/main` 和 `git ls-remote origin refs/heads/main` 均为上述 SHA，父仓工作区与 terminal 干净。此后的收尾提交只回填本页和总索引的完成记录；最终 SHA 由 Git 与本次交付报告确认。

设备、远端 Actions 和正式发行的历史待验收状态不由本轮文档整理关闭。
