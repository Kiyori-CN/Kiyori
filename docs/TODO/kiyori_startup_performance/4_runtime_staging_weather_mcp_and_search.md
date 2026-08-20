---
status: verification_pending
date: 2026-08-20
device_scope: excluded
---

# 启动调度、首页天气、MCP 提示与首次搜索联合优化

## 目标与权威边界

本增量继续复用唯一 `KiyoriApplication`、`KiyoriApp`、`KiyoriAppShell`、
`PluginLoadingState`、`KiyoriWeatherRepository`、`BrowserPresentationCoordinator`、
`StandardBrowserSessionTools` 和 `WebSessionBrowserHost`。它解决软件首页冷启动阶段的资源争用、
天气首显延迟、MCP 提示跨页面显示、软件启动后第一次关键词搜索缺少搜索引擎切换条，以及广告订阅
自动刷新产生的高峰值堆内存崩溃。

Kiyori 尚未公开发布。本轮是现有内部方案迭代，直接删除重复启动工作和错误的全局展示接线，不保留
旧启动调度、旧天气状态或旧 MCP 提示路径。

本文件记录跨模块设计和验收；天气交互细节仍以
[`../kiyori_home_ui_refresh/2_weather_and_browser_windows.md`](../kiyori_home_ui_refresh/2_weather_and_browser_windows.md)
为准，搜索交互细节仍以
[`../kiyori_browser_product_completion/2_software_home_and_fullscreen_search.md`](../kiyori_browser_product_completion/2_software_home_and_fullscreen_search.md)
为准。

## 已验证根因

### 首帧与模块加载

- `KiyoriApp` 首次组合立即构造 `BrowserPresentationCoordinator`。这会创建共享
  `StandardBrowserSessionTools`，同步初始化 AndroidX WebKit Profile，并读取浏览器下载任务、
  启动广告规则状态生命周期；软件首页首帧只需要窗口数量，不需要这些 Browser Runtime 工作。
- `rememberOperitNavigationIntegration` 首次组合立即请求 ToolPkg UI route 和导航项。
  `PackageManager.ensureInitialized()` 在主线程不等待扫描完成，但会立刻启动 assets、外部包和缓存
  初始化，因而与首帧后的数据库、分词、缓存和插件工作争用 CPU 与磁盘。
- `KiyoriApplication.initializeMainApplicationLocked()` 在首帧后仍同步构造全局 Coil
  `ImageLoader`；该实例已有 `ImageLoaderFactory` 的按需创建入口，不属于插件加载或主内容可用门禁。
- `KiyoriApp` 在第二帧后调用 `MCPRepository.syncInstalledStatus()`；启动插件链随后调用
  `refreshPluginList()`，后者已经重新读取 MCP 配置并发布完整安装列表，前一条扫描没有独立消费者。

### 首页天气

- Repository 只保存进程内状态，冷启动初始值为权限态；已授权设备会先短暂显示错误语义图标，
  然后进入 Loading。
- 每次刷新严格串行等待当前位置、`Geocoder` 城市解析和 Open-Meteo 请求；其中定位与城市解析的
  抖动会放大整体首显时间。
- 没有已验证的持久天气快照，也没有短时 last-known 定位选择；任何刷新失败都会把当前可用天气
  覆盖为 `Unavailable`。

### MCP 提示

- `PluginLoadingState` 是正确的加载状态 owner，但 `MainActivity` 把
  `PluginLoadingScreenWithState` 永久放在应用最上层，因此折叠态 `0/0` 会覆盖软件首页、Browser、
  Mini App、文件管理和 Kiyori Settings。
- 展示范围没有读取当前 Shell/AI route。运行状态和可见页面状态被错误地绑定成同一个全局条件。

### 第一次关键词搜索

- 软件首页搜索通过 `BrowserPresentationCoordinator.openSearchResultInNewSession()` 把
  `BrowserSessionSearchRecovery` 写入新建 session。
- 新 Browser Host 的 `WebSessionBrowserHostState` 仍以空 `lastSearchQuery` 和隐藏切换条开始；
  `createSessionTabOnMain()` 会先投影并激活新 session，coordinator 随后才写入
  `BrowserSessionSearchRecovery`。旧 `updateHostProjection()` 既不接收 recovery，也无法识别
  同一 session 在首次投影后新到达的 recovery。
- 后续从 Browser 内部搜索会直接修改 Host transient state，所以只有第一次软件首页搜索缺失切换条。

### 搜索条错误页面范围

- 首轮 hydration 只比较活动 session ID 和 recovery key，没有比较
  `browserState.currentUrl` 与 `BrowserSessionSearchRecovery.resolvedResultUrl`，因此 session
  仍带 recovery 时，自定义主页或普通子页面可能继承横向条。
- Browser 内全屏搜索提交和搜索记录打开回调还会直接把
  `isSearchEngineQuickSwitchBarVisible` 设为 `true`。这个写入发生在 WebView 进入结果 URL
  之前，是横向条短暂覆盖旧页面或自定义主页的第二条独立根因。

### 广告订阅刷新 OOM

- 现场报告 `f462782c-9749-4a17-b694-e39004004aba` 在
  `BrowserAdBlockStore.refreshDueBuiltInSubscriptions()` 中触发 512 MiB 堆上限 OOM，栈顶位于
  元素规则域名规范化。
- 内容变化或编译快照缺失时，旧路径同时保留下载 `ByteArray`、完整 UTF-8 `String`、完整网络和
  元素 spec 列表、parser 有效性编译结果，以及第二次正式编译产生的 compiled 列表；刷新时旧
  subscription partition 还必须保持可用，进一步放大峰值。
- 崩溃不是某一条域名格式错误，也不能通过捕获 `OutOfMemoryError`、关闭自动更新或裁剪规则数量
  解决；必须消除完整中间表示和重复编译。

## 冻结设计

### 1. 首帧与运行时分段

1. Operit host route 和静态 AI 导航在首帧立即可用。
2. ToolPkg 动态 route 初始化等待两个帧信号，在 IO 调度器完成唯一 `PackageManager`
   初始化后一次发布完整导航模型。
3. 初始化完成前，外部 ToolPkg route 请求保持未消费；完成后存在的 route 正常打开，不存在的
   route 才按未知请求处理。
4. 软件首页通过 Browser coordinator 的进程级只读窗口数量 Flow 观察现有 runtime；该 Flow
   不创建 Browser Runtime。Browser 页面、搜索、窗口、书签或 AI browser 工具第一次真实使用时，
   才取得唯一 coordinator 实例。
5. 全局 Coil `ImageLoader` 不再阻塞 `mainApplicationReady`；它进入已有首帧后串行预热队列，
   任一真实图片请求仍可通过唯一 `ImageLoaderFactory` 同步取得同一实例。
6. 删除根 Compose 的 MCP 安装状态扫描；插件启动链中的 `refreshPluginList()` 是启动期唯一
   MCP 配置刷新。

### 2. 天气首显与刷新状态机

1. 每次成功天气保存一个 schema 化小型快照：城市、摄氏温度、WMO 视觉类型和观测时间。
2. 已授权定位时，Repository 初始状态直接进入 Loading；后台先读取仍在展示时限内的已验证快照，
   读取成功立即发布 `Available(isRefreshing=true)`，随后继续实时刷新。
3. 定位先从启用 provider 中选择短时有效的 last-known 位置；没有符合时限的位置时请求一次当前
   位置，并由明确超时取消该请求。
4. 取得位置后并行执行城市解析与 Open-Meteo 请求；两项都成功才形成新的可持久快照。
5. 有有效 `Available` 时刷新失败只结束 refreshing 状态并保留当前显示；没有可显示快照时才进入
   `Unavailable`。
6. 强制刷新、权限拒绝、定位关闭和无权限继续沿用明确状态，不虚构城市、温度或定位。

### 3. MCP 提示展示范围

1. `PluginLoadingState` 增加由 Kiyori Shell 投影写入的 `presentationAllowed`，不复制 MCP
   运行进度、插件列表或日志。
2. `PluginLoadingScreenWithState` 只在 `isVisible && presentationAllowed` 时绘制；非 AI
   页面只订阅这两个轻量 Flow，不订阅 MCP 进度、消息、插件列表、展开状态和日志。
3. AI Home、可见 AI root/child route 和 AI drawer 允许显示；软件首页、负一屏、全屏网页搜索、
   Browser、Mini App、文件管理、Kiyori Settings、书签、历史和下载抽屉不显示。
4. 从非 AI 页面返回 AI 页面时，仍在运行的 MCP 状态恢复显示；后台加载不因隐藏而取消。
5. Kiyori 根组合卸载时撤销 `presentationAllowed`，避免 Activity 内容重建后残留旧页面权限。

### 4. 首次搜索切换条

1. `WebSessionBrowserHost.updateHostProjection()` 接收当前活动 session 的
   `BrowserSessionSearchRecovery` 和当前 `browserState.currentUrl`。
2. session 或 recovery 变化时，只有当前 URL 与 `resolvedResultUrl` 精确规范化等价才初始化
   `lastSearchQuery` 并显示横向条；域名相同但 path/query 不同的子页面不具备显示资格。
3. 同一 recovery 从结果页进入其他 URL 时立即隐藏；仍在同一结果 URL 的普通投影保留用户手动
   关闭状态。
4. 全屏搜索提交与搜索记录打开只关闭搜索层并保持横向条隐藏；结果页 `onPageStarted` /
   `onPageFinished` 形成准确 URL 投影后才允许显示。
5. 切换到没有 search recovery 的窗口时隐藏；切回仍位于其结果 URL 的窗口时恢复对应 query。
6. 不增加新的搜索记录 Store，不改变软件首页“新建窗口”或 Browser 顶栏“当前窗口导航”的语义。

### 5. 广告订阅低峰值编译

1. 保留 `BrowserAdBlockStore`、schema-v3 内容寻址载荷、逐订阅 compiled cache 和
   `BrowserAdBlockEngine.combine()` 的唯一所有权。
2. 快照缺失和内容变化统一通过 `BufferedReader` 逐行读取 UTF-8 载荷。
3. 每行只构造当前 spec，一次调用现有 network 或 element compiler，并直接把 compiled rule
   写入最终 rule-set 列表；同时累计阻断、例外、元素和 ignored 五类计数。
4. `badfilter` 继续计入网络规则元数据，但只生成 `BrowserAdBlockBadFilter`，不进入普通 compiled
   network list；跨订阅禁用语义仍由现有全局 matcher 决策。
5. 运行时刷新和缓存缺失路径不再构造完整订阅 `String`、完整 parsed spec 列表或执行第二轮规则
   编译；原有 parser API 仅保留给小型纯模型调用和兼容测试。
6. 新 partition 完成后仍按原事务写入内容寻址载荷与 compiled cache，最后提交状态并原子替换；
   不吞异常，不改变规则上限，不关闭订阅或自动更新。

## 预计文件

- `app/src/main/java/com/kiyori/app/KiyoriApplication.kt`
- `app/src/main/java/com/kiyori/app/KiyoriApp.kt`
- `app/src/main/java/com/kiyori/integration/operit/navigation/AppRouteCatalog.kt`
- `app/src/main/java/com/kiyori/integration/operit/navigation/OperitNavigationIntegration.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManager.kt`
- `app/src/main/java/com/ai/assistance/operit/core/browser/presentation/BrowserPresentationCoordinator.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/startup/screens/PluginLoadingScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/main/weather/KiyoriWeatherModels.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/main/weather/KiyoriWeatherRepository.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockPolicy.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockStore.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionBrowserScreen.kt`
- 对应 JVM 测试、架构快照、持久化清单和正式文档

## 风险与停止条件

- 任何实现不得创建第二 Browser Runtime、第二 PackageManager listener、第二 MCP 状态或第二天气
  Repository。
- Browser Runtime 延迟构造后，外部 Browser Intent、软件首页搜索、窗口总览和 AI browser 工具
  必须仍取得同一实例。
- ToolPkg 初始化前的动态 route 请求不得丢失；初始化失败必须有明确日志与终态，不能伪装成完成。
- 天气快照只接受 schema、时间、城市、温度和视觉枚举全部有效的数据；损坏快照必须拒绝并记录。
- 切换条 hydration 只能发生在活动 session 变化或新的 recovery key 到达时；同一 key 的刷新
  且仍处于同一结果页的投影不能重新打开用户已经关闭的切换条。
- 自定义主页、结果页子页面、普通 URL 和尚未稳定的重定向都不得显示切换条。
- 广告订阅修复不得捕获 OOM、限制有效规则数量、关闭自动更新或创建第二规则 owner。
- 任何 architecture、formal readiness、定向测试、Debug 构建或 APK 静态审计失败都停止提交。

## 分阶段计划

1. [DONE] 读取项目规则、正式开发门禁、历史启动计划和当前 Git 基线。
2. [DONE] 映射 Application、MainActivity、根 Compose、Browser、ToolPkg、MCP、天气和搜索状态链。
3. [DONE] 冻结本文件中的单 owner、调度顺序、状态机、验收和停止条件。
4. [DONE] 实现首帧后 ToolPkg 初始化、Browser Runtime 惰性构造、Coil 预热移动和 MCP
   重复扫描清理。
5. [DONE] 实现天气持久快照、last-known 定位、并行请求和有效数据显示保持。
6. [DONE] 实现 MCP AI 页面可见性与首次搜索 Host hydration。
7. [DONE] 增加纯策略、状态、快照、导航初始化和首次搜索回归测试；同步架构与持久化快照。
8. [DONE] 运行定向 JVM、Kotlin 编译、formal readiness、architecture boundaries、
   `git diff --check`、规定的串行 Debug APK 构建及 APK/native/player 静态审计。
9. [DONE] 审计 Markdown、候选树、敏感内容、构建产物、文件模式与子模块状态。
10. [DONE] 根据现场搜索条范围反馈增加精确结果 URL 门禁，并删除全屏搜索提交时直接显示横向条的
    UI 写入。
11. [DONE] 根据真实 OOM 栈把广告订阅刷新和缓存缺失改为单遍直接编译，增加旧/新语义等价、
    大批量规则和源码合同回归。
12. [DONE] 重跑完整定向 JVM、正式门禁、Debug APK 和本地产物审计。
13. [PENDING DEVICE] 冷启动首帧、首页天气、AI/非 AI MCP 提示、搜索条页面范围和广告订阅更新
    内存由目标设备验收。

## 当前实现与定向证据

- 启动调度与 Browser 轻量窗口投影测试通过：
  `OperitNavigationIntegrationTest`、`BrowserWindowCountStateTest`。
- 天气模型测试通过：快照往返、损坏/未来/过期/越界数据拒绝、30 分钟定位选择和刷新失败保留。
- MCP/搜索测试通过：`KiyoriShellStateTest` 的页面矩阵与
  `BrowserSearchRecoveryProjectionTest` 的首次晚到 recovery、同 session 关闭保持和窗口切换恢复。
- 分批定向 Gradle 与最终扩展定向集合均通过实际 `:app:compileDebugKotlin`；最终集合为
  `14` 个 suite、`158/158`，零 failure/error/skip。天气首次运行暴露的
  `suspendCancellableCoroutine` 泛型推断错误已按 `Location` 根因修正并复跑通过。
- `check_architecture_boundaries.py --require-main` 已通过，新增
  `kiyori_home_weather`、受保护源码哈希、Shell helper、导航日志消费者和 import 集合均已锁定。
- architecture Python fixture 为 `109/109`；formal readiness 与 `git diff --check` 通过。
- `:app:assembleDebug --no-daemon --console=plain` 成功完成 `232` 个任务，其中 `22` 个执行、
  `210` 个为最新状态；唯一 Debug launcher 与 player runtime packaging 校验通过。
- 最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-20 17:58:16 +08:00`，大小 `472652738` bytes，SHA-256
  `D6F038C09FDCD041E037A6862AB7C1BB15B3AB87C42D676CDB0BFB57AA529FD4`。
- APK 为 `com.kiyori 45 / 0.1.0`、min `26`、target `34`、compile `37`，
  Application 为 `com.kiyori.app.KiyoriApplication`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer 与
  `zipalign -c -P 16 -v 4` 通过。
- APK 共 `5504` 个 ZIP entry，无重复项；仅含 `arm64-v8a`，`51` 个 `.so` 与
  `assets/operit_shell_exec` 共 `52` 个 ELF 全部为 ELF64/AArch64；`153` 个 `PT_LOAD`
  为 `0x4000 × 151`、`0x10000 × 2`，
  无低于 `0x4000`、无重复 ZIP 条目或 native basename、含 `liboperit_ripgrep.so`、
  不含 `libsudo.so`。
- mpv 与 FFmpegKit 两份受控 M9 AAR 静态 closure 审计通过，共 `19` 个 native 成员与 APK
  逐字节一致且无 `RPATH/RUNPATH`；Git/远端交付继续按现有提交推送门禁执行。

## 验收矩阵

| 级别 | 验收 |
| --- | --- |
| 纯逻辑 | ToolPkg 启动 gate、AI 提示可见性、天气快照时限、last-known 选择、首次 search recovery hydration |
| JVM | 相关测试类全部通过，零 failure/error/skip |
| 编译 | `:app:compileDebugKotlin` 通过 |
| 项目门禁 | formal readiness、architecture boundaries、Markdown links、资源解析、`git diff --check` |
| 构建 | `:app:assembleDebug --no-daemon --console=plain` 串行成功 |
| APK | 路径、时间、大小、SHA-256、包名、版本、SDK、唯一 launcher、V2 签名、16 KB 对齐 |
| Git/远端 | 精确 staged allowlist、敏感内容与产物审计，`main` 提交和 `origin/main` ref 对账 |
| 设备 | 冷启动流畅度、天气首次可见时间、页面切换期间 MCP 提示、首次搜索切换条与实际重搜 |
