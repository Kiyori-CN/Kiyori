# 浏览器运行时诊断与菜单优化

> 状态：`LOCAL IMPLEMENTATION AND AUTOMATED VERIFICATION COMPLETE / TARGET DEVICE VERIFICATION PENDING`
>
> 本专项承接 2026-08-27 的浏览器深度审查，目标是让 Android System WebView 运行时在不更换
> Chromium provider 的前提下具备可复核的诊断证据，并收拢浏览器菜单的信息架构。当前 Kiyori
> 尚未公开发行，因此移除未实现的“阅读模式”入口不需要旧版本兼容开关；兼容的 WebSession、
> Profile、Cookie、导航、用户脚本、广告拦截和播放器合同继续保持不变。

## 1. 范围与非目标

### 范围

1. 将浏览器菜单固定为用户确认的三行 5/5/5 顺序：
   - 第一行：加书签、书签、历史、下载、插件
   - 第二行：UA 标识、资源嗅探、网络日志、诊断日志、工具箱
   - 第三行：无痕模式、查看源码、标记广告、网站配置、AI 对话
2. 删除浏览器主菜单中的未实现“阅读模式”入口、占位路由、占位图标和无消费者的多语言资源；
   负一屏的“手册”快捷工具改用诊断入口的独立语义色，不再依赖阅读模式身份。
3. 新增浏览器专用结构化诊断日志：由唯一 `StandardBrowserSessionTools` 持有有界环形缓冲区，
   每条记录保留 session、document token 和 Profile 上下文，按当前/全部会话、级别、类别与查询词
   筛选，支持范围清空，以及当前筛选结果的脱敏复制与 SAF 文本导出。
4. 在 WebView 创建、Profile 绑定、provider/feature 快照、导航生命周期、网页弹窗、权限、文件
   选择、SSL/主文档错误、渲染进程退出、userscript attach 和播放器交接等边界记录真实事件；
   `AppLogger` 仍可记录同一事件，但 UI 不解析通用 logcat。
5. 将 `androidx.webkit` 从 `1.16.0` 升级到官方稳定 `1.17.0`。只采用已编译且有测试覆盖的
   API；本轮不把 `WebView.loadUrl` 全量替换为 `WebViewCompat.navigate`，不改变请求头、历史、
   搜索恢复或播放器无刷新交接语义。

### 非目标

- 不把 Android System WebView 替换成 X5/TBS、GeckoView、APK 内嵌 Chromium 或第二个 WebView
  runtime。
- 不在本轮改变 Cookie 策略、UA 计算、缓存模式、provider 选择、页面缩放、广告规则、网络代理、
  下载器或播放器的业务行为。
- 不把网页 console 全量复制成诊断日志，不记录 Cookie、Authorization、Bearer、API key、POST
  body、密码、网页正文、完整私有路径或未脱敏查询凭据。
- 不把用户可见诊断记录持久化到恢复快照、历史数据库或无痕 Profile；进程结束后诊断环形缓冲区
  自然消失。

## 2. 已验证事实

| 事实 | 证据与影响 |
| --- | --- |
| 实际内核边界 | `BrowserWebViewSupport.kt` 直接创建 `android.webkit.WebView`；实际 Chromium 版本由设备 WebView provider 决定，仓库不打包独立 Chromium。 |
| AndroidX WebKit 版本 | 本专项基线为 `1.16.0`，当前候选 `gradle/libs.versions.toml` 已改为 `1.17.0`；Google Maven `androidx/webkit/webkit/maven-metadata.xml` 于 2026-08-27 返回稳定 `1.17.0`，`lastUpdated=20260812171216`。 |
| 菜单现状 | `WebSessionBrowserMenuDrawer.kt` 当前为 5/5/5 加底部 3 个动作，第二行仍把资源嗅探、UA、网络日志、AI、工具箱混排，第三行含阅读模式占位。 |
| 状态所有者 | `WebSessionBrowserHostState` 负责 route/back；`StandardBrowserSessionTools.WebSession` 负责单 session WebView、console 和网络资源目录；没有浏览器进程级诊断缓冲区。 |
| 日志边界 | `clearEventLogs` 在新文档开始时清空 console 与网络条目；网络日志是当前文档资源目录。渲染进程退出会关闭 session，因此诊断若放入 session 会随崩溃信息一起丢失。 |
| provider 诊断现状 | `WebSessionUserscriptManager` 只在 userscript attach 流程调用 `WebViewCompat.getCurrentWebViewPackage`，不能代表所有浏览器启动，也不能由菜单直接查看。 |
| 现有能力 | 运行时已使用 `MULTI_PROFILE`、`DOCUMENT_START_SCRIPT`、`WEB_MESSAGE_LISTENER`、`JS_INJECTION_IN_FRAME_AND_WORLD`、`MUTE_AUDIO` 和 `PROXY_OVERRIDE` 能力判断；这些结果应在诊断快照中集中展示。 |

## 3. 设计决策

### 3.1 菜单与返回链

- `WebSessionBrowserMenuTone` 继续是一组固定 18 个入口身份，保持 5/5/5/3 的稳定几何；新增
  `DIAGNOSTICS` 占据第二行第四列，`AI_DIALOGUE` 移至第三行第五列，原阅读模式身份被删除。
- `WebSessionBrowserSheetRoute.DIAGNOSTICS` 是普通浏览器 child drawer，打开/关闭只修改 Host 状态，
  不触碰 WebView、不刷新页面、不重建 session。Back 顺序沿用现有 `CLOSE_SHEET`，诊断抽屉关闭后回到
  菜单，再回到网页历史/主页根。
- “工具箱”仍是当前浏览器的明确占位入口；以后实现阅读模式时从工具箱的浏览器页面工具进入，
  需要显式携带活动 `sessionId` 与 `documentToken`，不能猜测网页或创建第二 WebView。

### 3.2 诊断数据模型

每条 `BrowserDiagnosticEntry` 至少包含：`timestamp`、进程内单调 `sequence`、`level`（INFO/WARNING/ERROR）、`category`、
`event`、`sessionId`、`profile`、`documentToken`、规范化 `host`、短消息和有界结构化详情。

- 进程级缓冲区最多保留 1,000 条，按时间追加、超限从最旧端移除；只保留内存，不写入 Room、恢复
  快照或无痕数据目录。
- 详情值先经过统一脱敏：URL 移除 user info 和 fragment，仅保留 scheme/host/非敏感 path，敏感 path
  段与查询值替换为 `<redacted>`；header、Cookie、Authorization、token、密码、签名和私有路径值
  不进入投影；长消息按 UTF-16 长度截断，换行归一化。网页 console、JS 对话和页面标题正文不进入
  诊断消息。
- 事件类别固定为 `PROVIDER`、`CAPABILITY`、`SESSION`、`NAVIGATION`、`WEBVIEW`、`PERMISSION`、
  `POPUP`、`USERSCRIPT`、`MEDIA`、`DOWNLOAD`，未知类别不能由网页输入创建。
- 诊断日志与网络日志职责分离：网络日志记录当前文档真实资源请求；诊断日志记录浏览器运行时状态
  转移与错误证据，不复制资源列表或网页内容。

### 3.3 抽屉交互

诊断抽屉顶部提供当前范围真实条目数、复制、SAF 导出与范围清空；下方依次为
`当前标签 / 全部会话`、`全部 / 警告 / 错误`、类别、搜索。列表按时间与 sequence 倒序显示等级、
类别、事件、host、session/profile/document 摘要和全部有界脱敏详情。

- 默认筛选当前活动 session；切换“全部会话”才能观察后台标签或已经关闭 session 的渲染崩溃记录。
- 清空当前标签只删除匹配的 `sessionId`，清空全部才删除整个进程缓冲区；两者都不影响网络日志、历史、
  Cookie、网页和 AppLogger。
- 复制/导出使用同一脱敏投影；UI 不显示或恢复原始详情。
- 空态、无筛选结果和诊断错误均是明确终态，不以空字符串伪装成功。

### 3.4 WebView 1.17.0 使用边界

- 先升级并编译现有 `WebViewCompat` 调用，确认 `MULTI_PROFILE`、隔离世界、WebMessage、音频静音和
  proxy override 行为没有 API 断裂。
- 使用 1.17.0 可用的能力查询为诊断快照提供事实；只有实际支持时才记录为 `supported=true`，不把
  版本号推断成能力。
- favicon、prerender、preconnect、profile cache quota 和 `WebViewCompat.navigate` 只在本轮文档中
  作为评估项，不直接改变运行时策略。后续若要启用，必须建立独立性能/导航合同和设备矩阵。

## 4. 实施里程碑

### M0 方案与语义同步

- 在本文件、`docs/TODO/README.md`、`docs/TODO/kiyori_browser_product_completion/index.md`、
  `CONTEXT.md`、浏览器架构文档中记录上述边界、菜单顺序、日志 owner 和 1.17.0 依赖事实。
- 复核当前 `main`、无 dirty diff、正式门禁和回滚点；第一处代码写入前重新检查状态。

### M1 菜单与路由

- 修改 `WebSessionBrowserMenuDrawer`、`WebSessionBrowserScreen`、`WebSessionBrowserHostState`、
  `WebSessionBrowserMenuColors` 和 Host callback wiring。
- 删除 `READER_MODE` route/placeholder branch、菜单参数、reader drawable 与多语言 reader 文案；
  保留通用 Toolbox 占位。
- 新增诊断 route、入口图标、中文与现有语言资源，更新负一屏手册 tone 以及菜单/色彩/设置页测试。

### M2 诊断 owner 与 WebView 接线

- 新建纯 Kotlin 的模型、脱敏和过滤策略；在 `StandardBrowserSessionTools` 增加唯一环形缓冲区与
  snapshot/clear API。
- 在 WebView 生命周期和现有 userscript/provider 入口调用同一记录函数；记录失败时同时写入
  `AppLogger`，但不吞异常、不改变原错误路径。
- 将 provider 包名、版本名/version code 和能力矩阵在 WebView 创建后立即记录；渲染进程退出先记录
  再执行既有 close session。

### M3 抽屉与自动验证

- 新建 `WebSessionBrowserDiagnosticSheet`，接入共享 drawer/back/Insets/主题合同。
- 增加模型、脱敏、过滤、环形缓冲、菜单排列和 Back 路由的定向 JVM 测试；验证并发追加、超限淘汰、
  当前 session 清空和 URL/header 脱敏。

### M4 WebKit 升级与验证

- 把 version catalog 改为 `1.17.0`，运行浏览器/userscript/网络代理相关 Kotlin 编译与定向测试。
- 运行正式开发门禁、`git diff --check`、Markdown 链接检查；串行执行规定的
  `./gradlew :app:assembleDebug --no-daemon --console=plain`，核验 APK 身份、签名、16 KB 对齐、
  native/runtime packaging 和产物路径。
- 设备真实 WebView provider、网页导航、普通/无痕 Profile、网页权限、渲染崩溃提示、菜单视觉与 Back
  仍标记 `verification_pending`，不以本地构建代替现场证据。

### M5 提交与推送

- 审阅最终 diff、敏感内容、生成产物、子模块和只允许本专项的文件清单；不混入 build/work/cache。
- 提交到唯一 `main`，推送 `origin/main`，随后核对 `HEAD`、tracking ref 与远端 `main` 一致。

## 5. 风险与完成信号

| 风险 | 控制 |
| --- | --- |
| 诊断追加过于频繁造成主线程刷新风暴 | 只记录有意义的状态边界；唯一 `AtomicBoolean` 合并待处理主线程投影刷新，诊断缓冲区有硬上限。 |
| URL/header 泄露凭据 | 所有 UI 与导出只消费脱敏快照；原始详情不离开记录函数。 |
| session 关闭后诊断无法筛选 | 记录保留 `sessionId`、Profile 和 document token；“全部会话”按进程缓冲区读取。 |
| 1.17.0 API 行为改变导航 | 本轮不迁移导航 API，不启用新缓存策略；只编译现有调用并做能力快照。 |
| 删除阅读模式误伤未来入口 | 仅删除未实现的浏览器占位 route；Toolbox 占位保留，未来阅读模式从工具箱重新设计。 |

本专项的本地完成信号是：菜单/route/diagnostic 定向测试零失败，`1.17.0` 编译通过，正式门禁与
Debug APK 审计通过，提交和 `origin/main` ref 对账完成。真实 provider、真实网页性能和设备视觉/手势
仍需用户现场验收后才能从 `verification_pending` 变为完成。

## 6. 2026-08-27 本地验收证据

- `:app:testDebugUnitTest`：`301` 个 XML suite、`1766` 项测试，`failures=0`、`errors=0`、
  `skipped=0`；诊断模型的脱敏、并发、环形上限、筛选和报告测试在最终修订后再次通过。
- `check_architecture_boundaries.py --phase m03`、`check_formal_readiness.py --require-main`、
  `check_fresh_clone.py` 与 `git diff --check` 均通过；新鲜克隆结果基于提交前 HEAD
  `e4bb10b1ab3087771c468fb9801fc4a7c8d7170b`，候选提交形成后仍需重新执行。
- 规定的 `:app:assembleDebug --no-daemon --console=plain` 最终通过：`235` 个任务中 `27` 个执行、
  `208` 个为最新状态；唯一 launcher、脚本代理 runtime 与播放器 runtime packaging 门禁通过。
- 最终本地 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`503669293` 字节，
  SHA-256 `45DA19C00E7AE46099D0A59EEC0F195400097D29CB2B1C7FDF4713279CE309EF`；包名/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，Android Debug V2 单 signer 与
  `zipalign -c -P 16 -v 4` 通过。
- APK 仅含 `arm64-v8a` 的 `53` 个 `.so`，basename 无重复，不含 `libsudo.so`，包含生成式
  `assets/operit_shell_exec`；本轮 ELF 静态审计覆盖这 `54` 个 AArch64 文件，`PT_LOAD` 最小对齐
  为 `0x4000`。生产 ToolPkg 同步测试 `7/7` 通过，APK 内 WebKit 版本标记为 `1.17.0`。
- 上述均为本地源码、构建和静态产物证据。真实 Android System WebView provider、网页导航/重定向、
  普通/无痕 Profile 隔离、权限、renderer crash、菜单视觉/手势、播放器交接和远端 CI 仍保持
  `verification_pending`。
