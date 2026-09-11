---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Operit v1.12.1 后续更新与最新插件市场适配

## 2026-09-11 加六完整适配与交付

本轮用户明确授权连同既有修改一起审查、验证、提交并推送 `origin/main`。
冻结基线 `main@934ee7978e`、上游 `b2c76100e5960a82ec154b62f201d89db099fadc`，
开始时有 241 项未提交路径。历史章节的验证与授权只描述各自轮次。

实施顺序：

1. 补齐 ToolPkg API 版本分发、依赖排序及配套聊天/Compose/市场接口，保留现有
   扫描代际、私有存储、执行审计和共享浏览器所有者。
2. 核验 MCP 发现注册、计划执行防重和上游其余适用修复，主动覆盖失败与并发边界。
3. 审查全部候选差异，执行风险相称的回归、文档及工程检查，串行构建 Debug APK；
   审计精确提交树后正常推送，并独立核对远端 ref。

产品身份、数据库 schema、签名与发布链不随上游版本覆盖；设备、真实账号及服务验收
继续单独标记 `verification_pending`。回滚以本轮起点快照和明确差异为单位，不重置工作树。
当前阶段：核心适配、模型/媒体修复、全量 JVM 与 Debug APK 验证完成，设备与真实服务待验证；
详细矩阵、主动修正和本轮证据见 [加六交付记录](02_plus6_delivery.md)。

## 2026-09-11 修复增量

当前轮次为本地选择性移植与主动根因审查，不继承下文历史轮次的提交推送授权。
基线为 `main@934ee7978e05bce1094bae16843d20193edd944a`，上游冻结在
`b2c76100e`（产品 `1.12.1+6`）。开始时存在 198 项既有工作区变更，保留其内容。

计划：先核查最近三个正式版本与 `f323d6c50..b2c76100e` 提交，再完成可独立验证的
数据读取、插件解析与输入、权限并发、角色偏好、工具渲染及工作流状态修复；以源码行为
和测试判断是否等价，不能机械复制上游。详细取舍及本轮证据见
[九月修复审计](01_september_bugfix_audit.md)。

验收：回归测试能覆盖原缺陷及新增边界；既有用户改动保留；串行 Debug 构建并核对 APK。
设备输入法、真实插件、网络和长对话现场验收单独保持 `verification_pending`。
本轮不改产品身份、插件兼容版本、数据库 schema、发布链或子模块；不提交、不推送。

阶段：调查、八组修复、本地自动验证与 Debug APK 构建完成；设备验收保持 `verification_pending`。
37 项 JVM 回归全部通过，正式开发、架构、文档与相关 Markdown 链接检查通过。
本轮精确增量为 26 文件；原有源码改动逐文件哈希保持，仅自动文档目录与既有修改交叠。
回滚只针对本轮精确差异，不恢复整棵脏工作树。产物和验收边界见下方九月审计记录。

## 任务合同

本专项审计 Operit 最新正式版本和 `main` 未发布增量，把适合 Kiyori 的功能与修复按现有
owner 逐项适配，并完成最新 Operit Market v2 与 ToolPkg 运行时兼容。实现完成后执行风险相称
的自动验证、规定的 Debug APK 构建、候选树审计、`main` 提交和 `origin/main` 推送。

范围内的“合并”指源级选择性适配，不创建 merge commit，不整分支 cherry-pick。Kiyori 已经
独立演进的产品壳、浏览器、播放器、应用代理、存储、对话审计和正式开发门禁继续由现有唯一
owner 持有。

## 冻结基线与证据

| 对象 | 2026-08-29 观测值 | 含义 |
| --- | --- | --- |
| Kiyori | `main@2e0571b66d9427962d2f125a74c8d91dfa03e351` | 与 `origin/main` 对齐，开始时工作树干净 |
| Operit 最新 Release | `v1.12.1@4faa5cd2ae0b5ee2ffa94f21d6e43c5ca011f84a` | GitHub 最新正式发布，发布时间 `2026-08-08T20:33:14Z` |
| Operit 当前主线 | `upstream/main@f323d6c50fa661837fad06d4618462861779b562` | 相对标签 211 个提交、480 个路径、约 38619 行新增/5760 行删除 |
| 共同祖先 | `ef00abc5099187b4665957e9697cb743c81fa154` | Kiyori 与 Operit 已长期分叉，不能用提交拓扑代替语义对齐 |
| 实时市场 | `generatedAt=2026-08-29T04:08:10.644Z`、1355 项 | 121 个 `toolpkg_v2` 最新条目；19 项要求 `1.12.1`、2 项要求 `1.12.1+3` |
| 市场资产 | 22/22 SHA-256 匹配 | 只下载到内存并读取 manifest/JS 文本；未安装、未加载、未执行 |
| 开发门禁 | `check_formal_readiness.py --require-main` 通过 | 当前身份、子模块、native lock、敏感产物和 CI SDK 基线有效 |

上游当前 `versionName=1.12.1+3`，它仍是 Operit 产品版本，不直接替代 Kiyori 的
`versionName=0.1.0`。Kiyori 的 `BuildConfig.OPERIT_MARKET_COMPAT_VERSION` 是单独的插件运行时
兼容轴；M1 公共能力已落地后，该值已从 `1.12.0+9` 提升到 `1.12.1+3`，而 Kiyori 的
`com.kiyori / 45 / 0.1.0` 产品身份保持不变。

## 迁移原则

1. 先比较最终语义，不因上游提交存在就覆盖 Kiyori 的更完整实现。
2. 每个功能只接入现有状态、存储、导航、网络和生命周期 owner；禁止并行 registry、第二数据库、
   第二市场客户端或第二 ToolPkg runtime。
3. 保留 namespace、`operit://`、市场 wire type、ToolPkg/MCP/插件 ID、数据和备份格式等兼容标识。
4. 不增加错误吞噬、协议猜测、静默重试、自动直连、旧路径扫描或其他回退逻辑。
5. 数据库、偏好和 manifest 变化必须先定义兼容读取/迁移，再写入新格式；未发布的纯内部方案可以
   在同一提交内彻底清理旧 owner。
6. 代码、TypeScript 声明、示例、正式文档和结构测试保持同一合同；市场版本字符串最后更新。

## 上游功能矩阵

| 功能簇 | 主要上游证据 | Kiyori 决策 | 关键边界 |
| --- | --- | --- | --- |
| Market 撤回条目再发布 | `621d5023` | 采纳 | 仅 owner 可从私有详情提交原 entry ID 的新版本，审批仍由服务端恢复公开状态 |
| 贡献者随版本提交说明 | `9c37b6b2` | 采纳 | contributor 只能提交变化后的 description/detail；title/category/policy 仍由原作者控制 |
| ToolPkg logo | `a7fc0107`、`386150de`、`334e64c4`、`5512ae52`、`b111de41`、`5ce3442a` | 采纳最终语义 | `manifest.logo` 只能引用包内静态图片 resource；市场 logo 只渲染服务端 `logoUrl`，客户端不上传 logo |
| 损坏市场安装标记隔离 | `dcebba00` | 采纳 | 单个 `.operit/market.json` 损坏可观察地跳过，不得中止全部条目扫描或伪造成已安装 |
| Compose DSL bridge/file picker | `94274d41`、`c849e5b4`、`8ddcf991`、`91f46fe5`、`2097619b` | 采纳最终六模式合同 | document/image/video/media/directory/camera 各自使用正确 Activity Result 合同；持久 URI 只适用于文档/目录 |
| ToolPkg ChatInfo 标识 | `4960e85c` | 采纳 | 返回角色卡 ID 与群组 ID，不改变聊天继续按名称绑定的持久化格式 |
| 大对话导出和选择导出 | `529a0b83`、`77ba98c5` | 采纳 | 流式写出避免整份文档常驻内存；选择范围必须与当前多选状态一致 |
| 计算器公开 API | `927d7b4f` | 采纳 | 对齐已公开的 qualified API，并保留解析错误可观察性 |
| 备份与恢复原子性 | `acdcdcfc`、`9b10c636`、`c13d251a`、`5235840b`、`8e9bdf67` | 采纳并与审计库复核 | 替换失败不得破坏现库；恢复期间数据库/统计访问串行化；接受明确的 Operit 历史包变体 |
| 窗口化记忆重建 | `51ea090a`、`b9e7ec73` | 采纳 | 复用现有 MemoryLibrary/聊天历史，不新增第二记忆库；失败保留可恢复进度 |
| 独立语音服务配置 | `9793e816`、`10b35b49` | 采纳 | TTS/STT profile 独立持久化，迁移现值，不破坏 Kiyori Settings 信息架构 |
| 聊天多选、断流与输入修复 | `c263fd0c`、`e1f9cacd`、`05a90642`、`794364cc`、`7e13dbb7` 至 `c8f63593`、`7f6cd1c1`、`f3c7bef0` | 采纳语义 | 保留已生成的中断文本；多选动作使用 Kiyori 当前工具栏；Agent 输入避开 IME |
| Markdown/编辑器/媒体小修 | `87581d1a`、`a0e450e9`、`8097dc5b`、`8755837e`、`b5044a46`、`837c0817` | 采纳 | 表格链接区分点击与拖动；相对链接不外发；编辑器 fling 在主线程推进；SAF 无授权时明确失败；附件顺序不重排 |
| 横竖屏窗口状态 | `d785dc8f` | 语义适配 | 上游 `PhoneLayout` 不是 Kiyori owner；只把配置保持规则接入 `KiyoriAppShell`/现有窗口状态 |
| Token 统计 | `19691c05` 至 `e4a39b47`、`87a69e4a` 至 `6e1c9d0e` | 采纳最终 Room 方案 | 与现有 AppDatabase、备份锁和 Provider identity 一次迁移；不得保留已被上游废弃的 spool/双 ledger |
| 新 Provider 与动态思考配置 | MiniMax/OpenCode/xAI/Codex、`ada96e37`、`b9a0ce01`、`0038ff53`、`089d4b05` | 分项采纳 | 复用 Kiyori 的显式协议选择和统一设置 owner；不引入协议自动探测、静默协议切换或 Operit 产品版本 |
| MNN、native 与 JVM JSON | `16634451`、`9de2edfe`、`9a39af02`、`f406656f` | 等价核验后选择 | Kiyori 已有更强 exact-commit/native closure；只补真实缺口，不放松现有构建合同 |
| CI、开发分支和发布资料 | `1bc12ed4`、`9ee3d3c4`、`35a8c8aa`、`93756846`、`1f4787e4`、README/星图提交 | 排除 | Kiyori 只持续开发 `main`，远端 Actions 关闭状态和独立发布链不在本轮改变 |
| DeepSeek Harness 示例/ADB 工具 | `a4a92641`、`0a4443ed` | 排除产品示例，核验通用 runtime | 不预置 Operit 调试侧栏或复制专用 ADB 流程；市场包依赖的公共 Compose/ToolPkg 能力必须可用 |
| 已撤销 chat view slot | `30d6c012`、`8302ba19` | 不实施 | 上游最终状态已经回滚，不能移植中间态 |
| Operit 产品壳、主题、A2A、更新器和品牌资料 | 多个主线提交 | 排除 | 与 Kiyori 产品所有权、隐私与独立浏览器/播放器边界冲突 |

## 实施阶段

### M1：ToolPkg 与 Market v2 兼容闭环

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE AND LIVE MARKET VERIFICATION PENDING`。

1. 将 `manifest.logo` 解析、文件 resource 校验、缓存读取和本地原色渲染接入唯一 ToolPkg runtime。
2. 接入服务端 `logoUrl`、市场列表/详情显示、发布预览最终语义；不发送或上传 logo 数据。
3. 接入撤回 owner 再发布、贡献者 partial entry patch、损坏 marker 隔离及对应状态测试。
4. 对齐 Compose DSL 六种 picker、Kotlin/TypeScript bridge 和 ChatInfo IDs。
5. 添加 market JSON、manifest parser、marker、发布 request 和 Compose picker 定向测试。
6. 完成后把 `OPERIT_MARKET_COMPAT_VERSION` 提升到 `1.12.1+3`，同步 `CONTEXT.md`、格式指南和
   正式准备文档；不得提前改版本。

### M2：通用可靠性与用户工作流

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。

按依赖顺序适配导出、计算器、备份锁/迁移、记忆重建、语音 profiles、聊天多选与断流、
Markdown、编辑器、SAF、附件顺序和 Kiyori 横竖屏状态。每个子簇先运行定向 JVM/Python 检查，
再进入下一子簇；与 Kiyori 对话审计、设置路由或浏览器 editor 冲突时以现有 owner 为准。

### M3：Provider、思考配置与 Token 统计

状态：`DONE BY EXISTING KIYORI OWNERS`。

对照确认 Kiyori 已由现有设置与数据库 owner 提供显式 Provider/协议选择、动态思考配置、统一
Room token usage、Responses at-most-once 提交边界、Hosted Web Search 和审计链。上游新增的平行
Provider/统计 owner 不再复制，避免协议自动探测、未知提交重试和双 ledger；本阶段没有新增数据库
schema 或第二偏好源。

### M4：完整验证与交付

状态：`GIT DELIVERY COMPLETE / DEVICE AND LIVE MARKET VERIFICATION PENDING`。

1. 审阅最终差异、数据库/偏好迁移、并发和错误路径；运行 `git diff --check`。
2. 运行新增/受影响 JVM 测试、完整 `:app:testDebugUnitTest`、必要 AndroidTest 编译、项目 Python
   门禁、architecture、formal readiness、Markdown 链接和 fresh-clone 检查。
3. 串行执行 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，核验 APK 路径、时间、
   SHA-256、包名/版本、唯一 launcher、签名、16 KiB ZIP/ELF 和 ToolPkg 生产资产。
4. 审计 staged allowlist、敏感内容、私有配置、生成物、大文件、Git mode、子模块和远端竞争状态。
5. 创建一个范围明确的 `main` 提交，正常推送 `origin/main`，独立核对 local/tracking/remote ref 与
   工作树清洁度。

本地验证证据（2026-08-29）：

- `:app:lintDebug`：`0 errors / 35 warnings / 0 hints`；35 条可见 warning 为 21 条
  `NewerVersionAvailable`、9 条 `GradleDependency`、2 条 `AndroidGradlePluginVersion` 和 3 条
  必须检查同步提交结果的 `SharedPreferences.commit()` `UseKtx`。baseline 结构化交集为
  `retained=5163 / stale=1 / current-only=35`，没有吸收 current-only 项。
- `:app:testDebugUnitTest`：315 个 suite、1875 个 test，`0 failure / 0 error / 0 skipped`；TypeScript
  declarations、formal readiness、architecture boundaries、fresh-clone 与 `git diff --check` 通过，
  architecture failure-first 测试为 `110/110`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL in 50s`。APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，生成时间 `2026-08-29 21:16:48 +08:00`，大小
  `503686789` bytes，SHA-256
  `FBCD24F29EF7E051AA0F74F1ACA19B2AAFEE2198CE00DD51F463A60A92D2C4E7`。
- APK 身份为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，只有
  `com.ai.assistance.operit.ui.main.MainActivity` 一个 launcher；Android Debug V2 单 signer 与
  `zipalign -c -P 16 -v 4` 通过。
- APK 共 5512 个无重复 ZIP entry、44 个 DEX；仅包含 `arm64-v8a` 的 53 个 `.so`，basename
  无重复。53 个 native library 加 `assets/operit_shell_exec` 共 54/54 个 ELF64/AArch64，161 个
  `PT_LOAD` 为 `0x4000 x 159 + 0x10000 x 2`，没有低于 16 KiB 的 segment。
- 49 项生产 ToolPkg 白名单与 APK 的 `assets/packages/*` 名称集合完全一致；构建内的 player、
  script-proxy、唯一 launcher 与 ToolPkg 生成门禁全部通过。

尚未执行且不能由本地证据替代：真机 document/image/video/media/directory/camera 六模式选择与
camera URI 生命周期、Android 市场动态写入、真实插件 logo 与第三方插件 UI、TTS/STT provider
实际调用、长聊天记忆重建、真实大导出，以及 Market owner/contributor 真实账号工作流。

## 回滚点与失败模式

- 每个里程碑开始前以已验证的 `main` commit 为回滚点；不使用 `reset --hard`、`checkout --` 或
  `clean`。未提交阶段出现错误时只修复当前差异，不覆盖未知文件。
- Market request 模型若与服务端 wire type 不一致，必须让序列化测试失败，不保留旧/new 双请求路径。
- ToolPkg logo 读取失败只影响该 logo 的可用状态并记录错误；不得把损坏图片当成安装失败，也不得
  让无 logo 的旧包失效。
- 数据库或偏好迁移失败必须保持原数据可读并暴露失败；禁止清空、重建或静默使用默认值。
- 自动验证不能代替 Operit Market 动态写入、GitHub Release、真实设备 picker/旋转/IME、Provider
  账号登录和插件 UI 验收；这些现场项在本轮结束后仍可保持 `verification_pending`。

## 完成定义

- 本矩阵所有“采纳”项完成实现与相称自动验证，所有“语义适配”项有 Kiyori owner 证据；排除项
  具有明确的不兼容理由且没有被间接带入。
- 市场兼容版本与真实公共运行时能力一致，实时市场中 `minAppVer <= 1.12.1+3` 的条目不再因宿主
  版本判断被错误拒绝；高于上游当前产品版本的声明不被虚假放行。
- Kiyori 产品版本、品牌、application ID、浏览器/播放器/代理/存储 owner 和 main-only 策略不变。
- 必需自动检查和 Debug APK 核验通过，提交已推送且本地 `main`、`origin/main`、远端 ref 一致。
- 未执行的设备、账号、市场写入或 Release 验收在最终报告和本 TODO 中明确标记。
