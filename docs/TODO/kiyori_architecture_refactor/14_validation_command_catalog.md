---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-08-01
---

# 验证命令目录

## 使用边界

本文件列出正式实施时的命令。当前方案阶段不因文档出现命令而获得执行源码重构、设备、提交或推送权限。

命令从高信号、低成本到高成本串行执行。失败后停止，不跳过真实失败。

## 基线与仓库卫生

```powershell
git status --short --branch
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count HEAD...origin/main
git submodule status --recursive
git diff --check
```

正式准备：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py `
  --repository . `
  --require-main
```

新鲜克隆门禁只在需要验证 clone/submodule 可复现时执行：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_fresh_clone.py `
  --repository .
```

## 文档

已提交 candidate 的 Markdown 链接：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_markdown_links.py `
  --base <base-commit> `
  --candidate <candidate-commit>
```

未提交方案文档使用等价的本地相对链接扫描，并在提交后重新运行正式脚本。

## G-00 架构与稳定合同门禁

G-00 当前命令：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

对应单元测试：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries
```

G-00 当前基线必须通过；超范围文件、Manifest 多重集合、稳定合同计数、
AIDL/Room/ObjectBox 规范化文件哈希、terminal 变化和 M-01 非纯改名样例必须稳定失败。

## Application 与 App Shell

最窄编译：

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

定向测试示例：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest" `
  --tests "com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistryTest" `
  --tests "com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest" `
  --no-daemon --console=plain
```

Application 第一里程碑应新增或确定以下特征覆盖：

- manifest Application class
- main/crash/repair/player process `onCreate`
- `initializeMainUiPrerequisites`
- `initializeMainApplication`
- WorkManager provider
- global JSON 与 ImageLoader
- startup timestamp 和日志

M-02 额外执行：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries

rg -n "KiyoriApplication" app\src\main\java\com\ai\assistance\operit
rg -n "instance|appStartupTimeMs|globalImageLoader|lateinit var json" `
  app\src\main\java\com\ai\assistance\operit\core\application\KiyoriApplication.kt
rg -n "ServiceLocator|KiyoriPlatform|ApplicationServices" `
  app\src\main\java\com\kiyori\platform
```

反向搜索必须按
[M-02 Application 全局访问平台化精确清单](17_m02_application_platform_access_manifest.md)
解释；目标是 Operit concrete Application 依赖为 0，而不是删除 Application 自身、Manifest
入口或 M-01 历史文档。

M-04A1 宿主 CompositionLocal 门禁和定向检查：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries

rg -n "compositionLocalOf|LocalTopBarActions|LocalOpenBrowser|TopBarTitleContent|LocalAppNavigationModel" `
  app\src\main\java\com\ai\assistance\operit\ui\main

.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

M-04A2 根组合纯移动门禁和定向检查：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "\bOperitApp\b" app\src\main\java --glob "*.kt"

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --tests com.ai.assistance.operit.ui.main.PendingAiHomeActionHandlerTest `
  --no-daemon --console=plain

.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

ARCH020 还必须核对 `m04-root-sha256.txt`、`m04-root-operit-imports.txt`、唯一
`KiyoriApp` 声明、唯一 `MainActivity` import/call、旧根路径缺失和旧运行时符号为 0。

M-04B1 Operit navigation policy：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "^import com\.ai\.assistance\.operit" `
  app\src\main\java\com\ai\assistance\operit\ui\main\shell\KiyoriShellState.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --no-daemon --console=plain
```

ARCH021 必须检查 8 个 policy symbol 只由 integration 文件拥有、Shell state 的 Operit
import 为 0、root 只从 integration import 这些符号，并核对 policy/state/root SHA 与
Operit import snapshot。

M-04B2 Browser presentation contract 与纯 Shell state：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriBrowserExitPresentation" app\src\main\java app\src\test\java
rg -n "^import com\.ai\.assistance\.operit" `
  app\src\main\java\com\kiyori\app\shell\KiyoriShellState.kt
rg -n "^import com\.kiyori\.app\.shell" `
  app\src\main\java\com\kiyori\app\KiyoriApp.kt `
  app\src\main\java\com\ai\assistance\operit\ui\main

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH022 必须检查 Browser exit presentation enum 的 package、源码 SHA、唯一 owner、固定
三处 runtime 消费者和旧 import。ARCH023 必须检查旧 Shell state 路径缺失、新路径/package、
唯一 state symbol owner、唯一 capability project import、root 的 9 个精确 Shell import，
以及 MainActivity、KiyoriAppShell、KiyoriAiDrawer、KiyoriShellPages 四个文件级过渡桥。

M-04B3 App Shell host：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "^package |^import com\.ai\.assistance\.operit" `
  app\src\main\java\com\kiyori\app\shell\KiyoriAppShell.kt
rg -n "com\.ai\.assistance\.operit\.ui\.main\.shell\.KiyoriAppShell" `
  app\src\main\java app\src\test\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH024 必须检查旧 App Shell 路径缺失、新路径/package、package-independent normalized
SHA、M-04B3 完成时的 17 个精确 Operit import、12 个 host/helper 唯一 owner、root 的唯一 import/call、
旧 root import 为 0，以及 `KiyoriShellStateTest` 对 11 个 moved helper 的精确接线。
M-04B3 完成后，ARCH023 的 root Shell import 集合为 10，Operit-to-Shell-state 过渡桥为
MainActivity、KiyoriAiDrawer、KiyoriShellPages 三个文件。

M-04B4 Modal AI Drawer host：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "^package |^import com\.ai\.assistance\.operit" `
  app\src\main\java\com\kiyori\app\shell\KiyoriAiDrawer.kt
rg -n "com\.ai\.assistance\.operit\.ui\.main\.shell\.(KiyoriModalAiDrawer|resolveKiyoriAiDrawerTone)" `
  app\src\main\java app\src\test\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH025 必须检查旧 Drawer 路径缺失、新路径/package、package-independent normalized SHA、
13 个精确 Operit import、`KiyoriModalAiDrawer` 与 `resolveKiyoriAiDrawerTone` 的唯一
owner、App Shell 的唯一 call、旧 import 为 0，以及 tone test 的新 owner import。
M-04B4 完成后，App Shell 的过渡 Operit import 为 16，ARCH023 的 Operit-to-Shell-state
过渡桥只剩 MainActivity 与 KiyoriShellPages。

M-04B5 Primary destination presentation：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "\b(data class PrimaryDestinationVisual|val primaryDestinationVisuals|fun resolveKiyoriBottomNavigationSelected|fun KiyoriPrimaryRootPage|fun KiyoriBottomNavigation|fun KiyoriBottomNavigationIcon)\b" `
  app\src\main\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH026 必须检查新文件 package、完整源码 SHA、10 个精确 Operit import、视觉映射/三条
动画策略/primary root/bottom navigation/icon 的唯一声明组，App Shell 的两个唯一 call
和旧 import 为 0，以及三条策略测试的新 owner import。完成后
`KiyoriShellPages.kt` 不再 import `com.kiyori.app.shell`，ARCH023 的 Operit-to-Shell-state
过渡桥只剩 MainActivity；App Shell 的过渡 Operit import 为 14。

M-04B6 Software Home：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "\b(fun KiyoriSoftwareHomePage|enum class KiyoriSoftwareHome|const val KIYORI_HOME_|val KIYORI_HOME_|fun resolveKiyoriSoftwareHome|fun KiyoriHome|fun KiyoriSearchAiSegment|fun KiyoriWeatherButton|fun KiyoriBrowserWindowsButton|fun kiyoriWeatherIcon|fun Modifier\.kiyoriGradientSearchFrame)\b" `
  app\src\main\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriSoftwareHomeSearchTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --no-daemon --console=plain
```

ARCH027 必须检查新文件 package、完整源码 SHA、9 个精确 Operit import、Software Home
页面、四个 enum、19 个固定视觉参数、四个纯 resolver 与全部私有 UI/helper 的唯一声明，
App Shell 的唯一 call 和旧 import 为 0，以及现有 Home 策略测试对 27 个迁移符号的精确
新 owner import。完成后旧 `KiyoriShellPages.kt` 只能保留 Full-Screen Browser Search、
`KiyoriWebSearchRequest` 与 `resolveKiyoriWebSearchRequest`；App Shell 的过渡 Operit
import 为 13。

M-04B7 residual Browser Search：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "\b(fun KiyoriFullScreenWebSearchPage|data class KiyoriWebSearchRequest|fun resolveKiyoriWebSearchRequest)\b" `
  app\src\main\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriSoftwareHomeSearchTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH028 必须检查旧 `KiyoriShellPages.kt` 缺失、新文件 package、完整源码 SHA、8 个精确
Operit import、Full-Screen Search 页面/request/resolver 的唯一声明、App Shell 唯一挂载、
KiyoriApp 两个新 owner import 与旧 import 为 0，以及 Home/Search 测试接线。完成后
App Shell 的过渡 Operit import 为 11，KiyoriApp 的 Shell owner import 增加两个新
Browser Search owner import，Browser Runtime、history、profile 与搜索行为保持不变。

M-04C Operit navigation integration：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "AppRouteCatalog|PackageManager.getInstance|ToolPkgRuntimeChangeListener|AppRouterGateway|AppRouteDiscoveryGateway|findOperitNavigationRoot|toOperitExternalRouteEntry" `
  app\src\main\java\com\kiyori\app\KiyoriApp.kt `
  app\src\main\java\com\kiyori\integration\operit\navigation

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.integration.operit.navigation.OperitNavigationIntegrationTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriSettingsPagesTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH029 必须检查旧 AppRouteCatalog 路径缺失、新 catalog/runtime package、两个源码 SHA、
16/12 个精确 Operit import、catalog/runtime/effects/两条 helper 的唯一 owner、catalog
接收唯一 PackageManager 实例、listener 与两个 gateway 的 install/clear 精确各一次，
KiyoriApp 不再直接 import/调用已提取装配，以及 settings test 的新 catalog owner import。
稳定 `AppNavigationModels`、`AppRouterState`、`AppRouterGateway` 和
`AppRouteDiscoveryGateway` 合同继续留在 Operit 包供 JsEngine/Operit screens 使用。

M-04D1 MainActivity pending-request owner：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainPendingRequests|pendingRequests\.|pendingSharedFileUris|pendingBrowserUrl|pendingKiyoriShellDestination" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainPendingRequests.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainPendingRequestsTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH030 必须检查 owner path/package、LF-normalized SHA、两个精确项目 import、唯一
`KiyoriMainPendingRequests` 声明、MainActivity 唯一字段、13 个旧字段声明为 0、16 条
record/take/clear/consume/update 在 MainActivity、shared-content coordinator 与 content
host 组合边界中的接线，以及 JVM 合同测试。owner 不得调用
`System.currentTimeMillis`、`SharedFileHandler`、`GitHubOAuthCoordinator`、
`BrowserDownloadManager` 或依赖 `Intent`；这些 Android host 副作用继续由稳定
MainActivity 承担。

M-04D2 MainActivity Intent decoder：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "decodeKiyoriMainIntent|KiyoriMainIntentCommand|KiyoriMainIntentContract|getLongExtra|getStringExtra|getParcelableExtra|getParcelableArrayListExtra" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainIntentDecoder.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainIntentDecoderTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH031 必须检查 decoder path/package、LF-normalized SHA、3 个精确项目 import，
intent contract/command/decoding/decoder 与两条 resolver 的唯一 owner，Android extra/
parcelable/OAuth 解码存在，以及 MainActivity 唯一 decoder call、3 个精确 import 和
10 个公开常量桥接。MainActivity 的 payload getter 与共享 action 分支必须为 0；decoder
不得修改 Intent，也不得调用时间戳、Player、Download、Toast、日志、分享、OAuth
coordinator 或 lifecycle。

M-04D3 MainActivity display coordinator：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainDisplayCoordinator|setSustainedPerformanceMode|preferredDisplayModeId|preferredRefreshRate|FLAG_HARDWARE_ACCELERATED" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainDisplayCoordinator.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainDisplayCoordinatorTest `
  --no-daemon --console=plain
```

ARCH032 必须检查 coordinator path/package、LF-normalized SHA、唯一 `KiyoriLogger` import，
mode candidate/selection、两条纯选择函数和 coordinator 的唯一 owner，5 组 Android
display API 精确计数，以及 MainActivity 唯一 configure call。Activity 中三个旧 helper
和全部显示 API 必须为 0；coordinator 不得持有 Compose、pending、Intent、Player、
Browser、OAuth、share 或 lifecycle 职责。

M-04D4 MainActivity shared-content coordinator：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainSharedContentCoordinator|processPendingSharedFiles|processPendingSharedText|SharedFileHandler\.setShared" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainSharedContentCoordinator.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainSharedContentCoordinatorTest `
  --no-daemon --console=plain
```

ARCH033 必须检查 coordinator path/package、LF-normalized SHA、resource/SharedFileHandler/
`KiyoriLogger` 三个精确项目 import，两个纯 resolver、transfer model 和 coordinator 的唯一
owner，SharedFileHandler 与 pending clear 调用各一次，以及 Activity 唯一 lazy
coordinator。files/text 调用必须在 `onNewIntent` 所属 Activity 边界各一次、在
`KiyoriMainContentHost` 各一次，总数各两次。coordinator 不得创建 StateFlow/Compose
state 或吸收 Intent、Player、Browser、OAuth、setContent 职责；ARCH030 在 Activity、
该 coordinator 与 content host 的精确组合边界中继续锁定 pending owner API 接线。

M-04D5 MainActivity task-visibility coordinator：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainTaskVisibilityCoordinator|restoreRuntimeTaskViewVisibilityIfNeeded|ActivityManager|AIForegroundService|setExcludeFromRecents" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainTaskVisibilityCoordinator.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainTaskVisibilityCoordinatorTest `
  --no-daemon --console=plain
```

ARCH034 必须检查 coordinator path/package、LF-normalized SHA、AIForegroundService/
`KiyoriLogger` 两个精确项目 import、纯判定函数与无状态 coordinator 的唯一 owner，SDK、
foreground-runtime、ActivityManager/appTasks/setExcludeFromRecents API 各一次，以及
MainActivity 原两个调用时机。Activity 中旧 helper、ActivityManager、
AIForegroundService 与 setExcludeFromRecents 必须为 0；coordinator 不得吸收 Compose/
Flow 状态、pending、Intent、Player、Browser、OAuth、share、lifecycle 或 content host
职责。

M-04D6 MainActivity orientation coordinator：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainOrientationCoordinator|KiyoriMainOrientationDialog|showOrientationChangeDialog|lastOrientation|onConfigurationChanged|recreate" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainOrientationCoordinator.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainOrientationCoordinatorTest `
  --no-daemon --console=plain
```

ARCH035 必须检查 coordinator path/package、LF-normalized SHA、R/`KiyoriLogger` 两个精确
项目 import，orientation state、纯 reducer、唯一 Compose state owner 与对话框
presentation 的唯一声明，四个 string resource 与单一 AlertDialog，以及 MainActivity
唯一 coordinator 字段、初始化、configuration dispatch、dialog mount、两次 dismiss 和
单一 `recreate()`。Activity 旧方向字段、旧 Composable 和 presentation imports 必须为 0；
`onConfigurationChanged` 中 Plugin Loading hide 必须仍早于 orientation dispatch；
coordinator 不得执行 `recreate()` 或吸收 plugin、pending、Intent、Player、Browser、
OAuth、share、lifecycle/content host 职责。

M-04D7 MainActivity startup notification permission coordinator：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainNotificationPermissionCoordinator|notificationPermissionLauncher|checkNotificationPermission|POST_NOTIFICATIONS|RequestPermission|shouldShowRequestPermissionRationale" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainNotificationPermissionCoordinator.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainNotificationPermissionCoordinatorTest `
  --no-daemon --console=plain
```

ARCH036 必须检查 coordinator path/package、LF-normalized SHA、R/`KiyoriLogger` 两个精确
项目 import，action enum、纯 resolver 与 coordinator 的唯一 owner，
registerForActivityResult/RequestPermission/checkSelfPermission/rationale/两次 launch/
两次 Toast 和固定日志/资源计数。MainActivity 必须以非 lazy 直接字段持有 coordinator，
确保 launcher 在 onCreate 前注册，并只调用一次 `checkAndRequest()`；旧 launcher、
helper、Manifest/PackageManager/ActivityResultContracts/ContextCompat imports 与权限资源
token 必须为 0。coordinator 不得保存第二权限状态或吸收其他页面权限、plugin、pending、
Intent、Player、Browser、OAuth、lifecycle/content host 职责。

M-04D8 MainActivity agreement/permission-level startup gate coordinator：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainStartupGate|startupGateCoordinator|agreementPreferences|showPermissionGuide|checkPermissionLevelSet|AgreementScreen|PermissionGuideScreen" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainStartupGateCoordinator.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainStartupGateCoordinatorTest `
  --no-daemon --console=plain
```

ARCH037 必须检查 coordinator path/package、LF-normalized SHA、6 个精确项目 import，
`AGREEMENT / PERMISSION_GUIDE / CONTENT` 三态、纯 resolver、唯一
`showPermissionGuide` Compose UI 投影和唯一启动门禁 presentation。协议版本事实继续由
`AgreementPreferences` 持有，权限级别事实继续由 `AndroidPermissionPreferences`
DataStore 持有；coordinator 只能通过既有方法引用读取或记录，不得增加第二
SharedPreferences/DataStore/Flow。MainActivity 必须只持有一个在
`initializeComponents()` 创建的 coordinator，并保持：

1. 通知权限检查 -> 权限级别刷新 -> startup chat -> 条件插件加载；
2. 协议记录 -> `delay(300)` -> 权限级别刷新 -> 条件插件加载 -> `setAppContent()`；
3. 权限引导完成 -> 插件加载 -> `setAppContent()`。

Activity 中旧 `agreementPreferences`、`showPermissionGuide`、`checkPermissionLevelSet`、
Agreement/Permission 页面 import 与直接挂载必须为 0。coordinator 不得吸收
`delay`、lifecycle、PluginLoadingState、`startPluginLoading()`、`setContent()`、
`setAppContent()`、KiyoriApp、pending/Intent/Player/Browser/OAuth/share 职责。

M-04D9 MainActivity Kiyori content host：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriMainContentHost|KiyoriMainContentRequestProjection|projectKiyoriMainContentRequests|KiyoriApp|LocalPluginLoadingState|CompositionLocalProvider" `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt `
  app\src\main\java\com\kiyori\app\startup\KiyoriMainContentHost.kt

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.app.startup.KiyoriMainContentHostTest `
  --no-daemon --console=plain
```

ARCH038 必须检查 content host path/package、LF-normalized SHA、精确项目 import、唯一
`KiyoriMainContentRequestProjection`、唯一 `projectKiyoriMainContentRequests` 与唯一
`KiyoriMainContentHost`。投影只能读取现有 `KiyoriMainPendingRequests`，不得保存
`mutableStateOf`、StateFlow、remember cache、SharedPreferences、DataStore、repository
或 singleton。

host 必须保持 `processPendingSharedFiles()` -> `processPendingSharedText()` -> request
projection -> `CompositionLocalProvider(LocalPluginLoadingState provides pluginLoadingState)`
-> 唯一 `KiyoriApp` 的顺序，并逐项保持 shortcut、route、Browser、Shell request/ID、
route args、current navigation 和五条 handled/update callback。MainActivity 必须只挂载一次
`KiyoriMainContentHost`，不再直接 import/mount `KiyoriApp`、`CompositionLocalProvider`
或 `LocalPluginLoadingState`；`onNewIntent` 中 shared-files/text 的原调用各保留一次。

content host 不得吸收 `PluginLoadingState` 的事实所有权、`PluginLoadingStateRegistry`、
loading screen、show/hide/timeout/MCP 初始化、`setContent`、主题、startup gate、agreement/
permission 回调、插件启动、方向对话框、`recreate()`、lifecycle、Intent、OAuth、Player、
Browser download 或其他 Android/runtime side effect。

M-04E MainActivity 稳定兼容入口与精确 ownership：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_exact_ownership_overrides_broad_glob `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_overlapping_broad_ownership_is_rejected `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m04e_finalization_accepts_compatibility_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m04e_finalization_rejects_stale_exception

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "operit-main-activity-compatibility|M-04D-main-activity-host|MainActivity|KiyoriMainContentHost" `
  config\architecture\package-ownership.toml `
  config\architecture\m04e-main-activity-project-imports.txt `
  app\src\main\AndroidManifest.xml `
  app\src\main\java\com\ai\assistance\operit\ui\main\MainActivity.kt
```

ARCH039 必须先以当前缺少精确 owner/snapshot 且仍保留到期 exception 的真实工作树失败，
再进行生产配置修改。正向 fixture 必须证明精确文件 record 覆盖宽泛目录 glob，并按精确
record 的 allowed/forbidden roots 检查依赖；负向 fixture 必须证明两个宽泛 glob 的重叠
仍被拒绝。真实 gate 必须锁定：

1. ownership id/path/owner/sync zone/phase 与 allowed/forbidden roots 精确相等；
2. `expires_after = "M-04D-main-activity-host"` 的 ARCH001 exception 为 0；
3. source package 为 `com.ai.assistance.operit.ui.main`，稳定 FQCN 为
   `com.ai.assistance.operit.ui.main.MainActivity`；
4. Manifest 中该 Activity 仍持有唯一 MAIN/LAUNCHER 入口；
5. 项目 imports 与 `m04e-main-activity-project-imports.txt` 完全一致、无重复且
   `com.kiyori.feature` 为 0；
6. `KiyoriMainContentHost` import 与挂载各一次，直接 `KiyoriApp`/
   `CompositionLocalProvider`/`LocalPluginLoadingState` 装配仍为 0。

M-04E lint 切片只能修改 M-04 owner 中有明确 API/Compose 等价依据的问题，并同步受影响
源码 SHA snapshot。验收以临时 current lint 结构化交集为准；不得通过扩大
`app/lint-baseline.xml` 让结果变绿，不得顺带处理其余领域债务。

M-05A1 pure ColorScheme/Browser/Settings design：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05a1_design_theme_accepts_pure_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05a1_design_theme_rejects_owner_or_dependency_drift

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --require-main

rg -n "KiyoriColorSchemes|KiyoriBrowserTheme|KiyoriSettingsTheme|resolveKiyoriColorScheme|resolveThemeColorScheme" `
  app\src\main\java\com\kiyori\design\theme `
  app\src\main\java\com\ai\assistance\operit\ui\theme `
  app\src\test\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.design.theme.KiyoriDesignThemeTest `
  --tests com.ai.assistance.operit.ui.theme.KiyoriThemeTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --no-daemon --console=plain
```

ARCH040 必须先在真实仓库以新 `KiyoriColorSchemes.kt` 缺失失败。实现后锁定三份 design
source 的 package/SHA/import、唯一声明、旧路径清零、11 个消费者、新/旧测试分工和
`operit-ui -> com.kiyori.design` 唯一新增允许方向。design source 中 preference、
DataStore、SharedPreferences、Application、Activity、Window、system-bar、lifecycle、
permission、storage、repository、ViewModel 与 Operit import 均必须为 0。

M-05A1 还必须与 ARCH024 联合验证 App Shell 的完整项目 import snapshot。Theme owner
迁移后，该集合包含九条 Operit import 和两条 `com.kiyori.design.theme` import；输入解析
只接受 Kiyori/Operit 项目根，最终仍使用精确 multiset 比较拒绝新增、缺失或重复 import。
当前 M-05A1 已按本节完成 failure-first、完整 architecture、Python/JVM、formal readiness、
lint、Markdown、规定 Debug 构建和 APK 静态审计。后续里程碑必须重新运行与其影响面相称的
验证，不能把 A1 结果外推为 semantic、root theme 或 platform owner 已完成。

M-05A2 semantic design：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05a2_semantic_design_accepts_split_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05a2_semantic_design_rejects_contract_or_consumer_drift

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main

rg -n "KiyoriSemanticTone|kiyoriSemanticToneForStableId|KiyoriSemanticColors|resolveKiyoriSemanticColors|KiyoriBottomNavigationSelectedFillColor|resolveKiyoriWeatherSunColor|resolveColors|kiyoriWeatherSunColor" `
  app\src\main\java\com\kiyori\design\theme `
  app\src\main\java\com\ai\assistance\operit\ui `
  app\src\test\java

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.design.theme.KiyoriDesignThemeTest `
  --tests com.ai.assistance.operit.ui.theme.KiyoriThemeTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest `
  --tests com.ai.assistance.operit.ui.main.shell.KiyoriSettingsPagesTest `
  --tests com.ai.assistance.operit.ui.features.packages.screens.PackageManagerVisualPolicyTest `
  --no-daemon --console=plain
```

ARCH041 必须先在真实仓库只报告缺少
`app/src/main/java/com/kiyori/design/theme/KiyoriSemanticColors.kt`。实现后锁定两个 design
source、旧路径清零、唯一声明、固定色值/顺序/映射、两个 Compose adapter、58 个消费者、
102 条 import、新旧测试分工和 Operit UI-only 依赖方向。ARCH025/026/027 的定向正反向
测试必须与 ARCH041 一起通过；随后执行完整 architecture、Python/JVM、formal readiness、
lint、Markdown、diff、规定 Debug 构建与 APK 审计。

M-05A2 已于 2026-08-02 完成上述验证：受影响 ARCH025/026/027/040/041 真实 gate 和
10 个正反向 fixture 通过，完整 architecture `phase=m03`、Python `156/156`、JVM
`135 suites / 810 tests`、formal readiness 与 fresh-clone 通过。新鲜 full lint 为
`27 errors / 287 warnings / 2 hints`，结构化 baseline 交集为
`5792 retained / 0 stale / 316 current-only`；两个新 design 文件 0 命中，唯一受影响路径
命中是 `WebSessionUserscriptSheet.kt` 未改正文的既有诊断，因此 A2 改动行无 lint 回归，
但不得写成仓库 full lint 全绿。规定 Debug 构建为 `233 actionable tasks`、`28 executed`、
`205 up-to-date`；APK `471292358` bytes，SHA-256
`60D613A5F9BFBD019296E21E05547DF16B0789FCDE73FF2699AFBAC415A8BBC0`，身份、SDK、
Application/launcher/process、arm64 53 native、零重复 basename、v2 与 16 KB 对齐通过。

M-05A3 root theme/style 与 Application system-bar：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05a3_root_theme_accepts_split_owners `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05a3_root_theme_rejects_owner_style_or_player_drift

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main

rg -n "OperitTheme|Theme\.Operit|KiyoriTheme|Theme\.Kiyori|enableEdgeToEdge|applyFontFamilyToTypography" `
  app\src\main app\src\test config\architecture

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.design.theme.KiyoriDesignThemeTest `
  --tests com.ai.assistance.operit.ui.theme.KiyoriThemeTest `
  --tests com.ai.assistance.operit.ui.main.MainActivityBrowserActionTest `
  --no-daemon --console=plain
```

ARCH042 必须先在真实仓库只报告缺少
`app/src/main/java/com/kiyori/design/theme/KiyoriTheme.kt`。实现后锁定 design/app/platform
四个 owner、旧 Theme 路径与旧 symbol 清零、八个偏好 Flow、原 Glass 组合顺序、Typography
测试迁移、七条外部消费者 import、两个生产 host、6 个 `Theme.Kiyori` 声明、Manifest 6 个
引用、m03 semantic hash、ARCH039 import snapshot 和 PlayerActivity 原 hash。正反向
fixture 后继续完整
architecture、Python/JVM、formal/fresh-clone、lint、Markdown、diff、规定 Debug 构建与 APK
审计。

M-05A3 当前封板证据为：ARCH040/041/042 六个正反向测试通过，完整 architecture
`phase=m03`、Python `158/158`、JVM `134 suites / 810 tests`、formal/fresh-clone 与
`git diff --check` 通过。fresh full lint 保持既有 `27 errors / 287 warnings / 2 hints`，
四个新 owner 为 0 命中，baseline 交集为 `5792/0/316`。规定 Debug 构建为
`233 actionable tasks / 28 executed / 205 up-to-date`；APK 为 `477957302` bytes，SHA-256
`9373518AEB8FA8BD2C02DCFDE5741833653A2D76AFE270D2C27F4D2BF88C0074`，身份、SDK、
Application/launcher/process、arm64 53 native、零重复 basename、Debug v2 与 16 KB 对齐通过。

M-05B platform logging：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05b_platform_logging_accepts_single_owner_and_facade `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05b_platform_logging_rejects_state_or_compatibility_drift

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main

rg -n "KiyoriLogger|AppLogger|KiyoriLogTextFormatter|ThrowableTextFormatter|OperitAppLogger|operit.log|packageLogs" `
  app\src\main\java\com\kiyori `
  app\src\main\java\com\ai\assistance\operit\util `
  app\src\main\java\com\ai\assistance\operit\provider `
  app\src\test\java `
  config\architecture

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.logging.KiyoriLogTextFormatterTest `
  --no-daemon --console=plain
```

ARCH043 必须先在真实仓库只报告缺少
`app/src/main/java/com/kiyori/platform/logging/KiyoriLogger.kt`。实现后锁定 platform
logger/formatter、旧 facade、CrashReportStore 四个源码 hash，platform 的两个精确项目
import、priority/文件/目录/线程名/时间格式/上限/正则、唯一
executor/提前解析的内部 filesDir/file/switch state、logger 静态 Context 清零、旧 facade
全 API 委派、旧 formatter 清零和 CrashReportStore 新 import。

8 个 Kiyori app consumer 必须与 snapshot 精确一致，Kiyori source 旧 logger import 为 0；
Operit consumer 保持旧 facade。Application 与 Provider 的目录绑定入口、两个旧 FQCN
static-mock 测试、formatter 三条 JVM 合同测试和 `kiyori-platform` ownership 不得漂移。
Display coordinator 的 logging exception 必须删除；ARCH018/020/032～037 的源码
hash/import/调用次数同步使用 `KiyoriLogger` 并继续通过，不能以 M-05B 为由放宽旧行为门禁。

封板继续执行完整 architecture、完整 Python/JVM、formal/fresh-clone readiness、范围内
lint、Markdown、diff、规定 Debug 构建与 APK 审计。真实设备和多进程日志文件写入仍单独
标记 `verification_pending`。

M-05B 当前封板证据为：ARCH043 failure-first、正反向 fixture、真实 gate、受影响
ARCH018/020/032～037 与完整 architecture `phase=m03` 通过；完整 Python 为 `160/160`，
完整 JVM XML 为 `135 suites / 813 tests / 0 failures / 0 errors / 0 skipped`。
formal/fresh-clone readiness 通过。新鲜 full lint 保持既有
`27 errors / 287 warnings / 2 hints`；三个新 logging source/test owner 为 0 命中，唯一
受影响路径命中是 `KiyoriApp.kt` 未改正文中的既有
`ConfigurationScreenWidthHeight` warning。临时完整 baseline 与正式 baseline 的结构化
交集为 `5791 retained / 0 stale / 316 current-only`；只删除了迁移后失效的旧
`AppLogger.kt` `StaticFieldLeak`，未吸收 current-only 问题，归一化 SHA-256 为
`aeb75aee42cf985ac5a8f6a33de88c04d1b0c50580e952e32542fe8403aab8c0`。规定 Debug
构建为 `233 actionable tasks / 24 executed / 209 up-to-date`，末尾
`:app:verifyDebugPlayerRuntimePackaging` 通过。APK 为 `477957302` bytes，SHA-256
`3A9BCBC711DB1FB735C3828AA1751F80B347C8C1FC7E34E783F16FFD96D631A1`；身份、SDK、
Application/launcher/process、arm64 53 native、10 个播放器目标库各一份、零重复 basename、
Debug v2 和 16 KB 对齐通过。

M-05C platform lifecycle：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05c_platform_lifecycle_accepts_fact_and_side_effect_split `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05c_platform_lifecycle_rejects_state_or_compatibility_drift

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main

rg -n "registerActivityLifecycleCallbacks|WeakReference<Activity>|private var activityCount|private var startedActivityCount|private var isAppInForeground" `
  app\src\main\java

rg -l "com\.ai\.assistance\.operit\.core\.application\.ActivityLifecycleManager" `
  app\src\main\java `
  examples `
  -g "*.kt" -g "*.java" -g "*.md" -g "*.js" -g "*.ts"

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.lifecycle.KiyoriActivityLifecycleFactsTest `
  --no-daemon --console=plain
```

ARCH044 必须先在真实仓库只报告缺少
`app/src/main/java/com/kiyori/platform/lifecycle/KiyoriActivityLifecycle.kt`。实现后锁定 platform
facts、Operit integration 与旧 facade 三个 source hash，唯一 callback 注册/current Activity/
activity count/started count/foreground state，platform 零项目依赖和零 integration token，
以及旧 facade 的完整 `ActivityLifecycleCallbacks` ABI 与逐项委派。

13 个旧 FQCN consumer 必须与 snapshot 精确一致；`KiyoriApplication` 继续通过旧
`initialize(this)` 安装唯一 platform callback。integration 必须保持 keep-screen-on 两组计数、
八类 plugin event、External Chat reason、2500 ms microphone、PlayerCrash、VirtualDisplay
与 Shower 边界，但不得保存 Context 或 platform facts。四条 JVM 测试锁定 identity、
foreground 0/1、destroy count 和 callback owner。

封板继续执行完整 architecture、完整 Python/JVM、formal/fresh-clone readiness、fresh full
lint 与影响路径审计、Markdown、diff、规定 Debug 构建与 APK 审计。真实设备上的 callback、
前后台、keep-screen-on、plugin/AI/Player/VirtualDisplay/Shower 行为继续单独标记
`verification_pending`。

M-05C 当前封板证据为：ARCH044 failure-first、正反向 fixture、真实 gate 与完整
architecture `phase=m03` 通过；完整 Python 为 `162/162`，完整 JVM XML 为
`136 suites / 817 tests / 0 failures / 0 errors / 0 skipped`，formal/fresh-clone、
working-tree Markdown `256/0`、diff 与 lint baseline normalization 通过。fresh full lint
保持既有 `27 errors / 287 warnings / 2 hints`，过滤基线为
`1268 errors / 4373 warnings / 150 hints`，M-05C 四条 source/test 路径为 0 命中。

规定 Debug 构建稳定为 `233 actionable tasks / 24 executed / 209 up-to-date`，
`:app:verifyDebugPlayerRuntimePackaging` 通过。APK 为 `477957302` bytes，SHA-256
`3792DD58C87D1DDD4F977BB8CD4B4407458EB911EC17CB0CB48CB8876184D2D3`；包/版本/SDK、
Application、唯一 launcher、`:crash/:player/:repair`、arm64 53 native、10 个播放器目标库
各一份、零重复 basename、Debug v2 与 16 KB 对齐通过。

M-05D Android notification permission capability：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m04d_notification_permission_gate_accepts_early_registered_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m04d_notification_permission_gate_rejects_drift_and_activity_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05d_notification_permission_accepts_platform_and_resource_split `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05d_notification_permission_rejects_owner_scope_or_resource_drift

rg -n "Manifest\.permission\.POST_NOTIFICATIONS|registerForActivityResult|RequestPermission|shouldShowRequestPermissionRationale" `
  app\src\main\java `
  app\src\main\AndroidManifest.xml

rg -n "KiyoriMainNotificationPermissionAction|resolveKiyoriMainNotificationPermissionAction|AndroidPermissionPreferences|notification_permission_denied|notification_permission_rationale" `
  app\src\main `
  app\src\test `
  ci `
  config

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.permission.KiyoriNotificationPermissionCapabilityTest `
  --no-daemon --console=plain
```

ARCH045 必须先在真实工作树只报告缺少
`app/src/main/java/com/kiyori/platform/permission/KiyoriNotificationPermissionCapability.kt`。
实现后锁定 platform capability、Operit resource bridge、app coordinator 与未改
`AndroidPermissionPreferences` 的 hash；旧 coordinator 的一参数构造与
`checkAndRequest()` javap 必须逐项保持。三条直接 `POST_NOTIFICATIONS` Kotlin consumer、
一份 Manifest 声明、6 条日志、2 个 Toast、四 action 顺序、旧 app policy owner/test 清零和
到期 ARCH004 exception 清零必须精确成立。

封板结果：完整 architecture `phase=m03`、Python `164/164`、JVM
`136 suites / 817 tests / 0 failures / 0 errors / 0 skipped`、formal/fresh-clone
readiness、256 个 working-tree Markdown、diff、lint baseline normalization 与敏感签名扫描
均通过。fresh full lint 保持既有 `27 errors / 287 warnings / 2 hints`，baseline 另过滤
`1268 errors / 4373 warnings / 150 hints`，四条 M-05D source/test 路径均为 0 命中。

预封板规定 Debug 构建为 `233 actionable tasks / 28 executed / 205 up-to-date`，末尾
`:app:verifyDebugPlayerRuntimePackaging` 通过。APK 为 `477957302` bytes，SHA-256
`BB370BC2602880CA4DCE488F67DA4AB9F105C1CFF07A0E4D2FF31A3D3CC38B34`；包/版本/SDK、
Application、唯一 launcher、`:crash/:player/:repair`、arm64 53 native、10 个播放器目标库
各一份、零重复 basename、Debug v2、单 signer 与 16 KB 对齐通过。真实 Android 13+
rationale、系统权限框、授权/拒绝和通知到达继续单独标记 `verification_pending`。

M-05E storage paths：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05b_platform_logging_accepts_single_owner_and_facade `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05b_platform_logging_rejects_state_or_compatibility_drift `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05e_storage_paths_accept_unique_owner_and_facades `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05e_storage_paths_reject_duplicate_calculation_or_consumer_drift

.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --phase m03 `
  --require-main

rg -n "Environment\.getExternalStoragePublicDirectory|fun ensureDir|Regex\\(|rawSnapshotExcludedRootNames|Download|Kiyori" `
  app\src\main\java\com\kiyori\platform\storage `
  app\src\main\java\com\ai\assistance\operit\util\OperitPaths.kt `
  app\src\main\java\com\ai\assistance\operit\data\backup\OperitBackupDirs.kt

rg -l "KiyoriPaths|KiyoriBackupPaths|OperitPaths|OperitBackupDirs" `
  app\src\main\java `
  -g "*.kt"

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.storage.KiyoriPathsTest `
  --no-daemon --console=plain
```

ARCH046 必须先在真实工作树只报告缺少
`app/src/main/java/com/kiyori/platform/storage/KiyoriPaths.kt`。实现后锁定四个 source hash、
唯一目录/Environment/ensureDir/plugin ID/raw snapshot owner、两个旧 facade 的完整 public
JVM ABI 与纯委派、`9 / 7 / 37 / 0` consumer 集合、五条 JVM 路径合同、M-03/M-05B 与
critical backup snapshot。

封板继续执行完整 architecture、完整 Python/JVM XML 统计、formal/fresh-clone readiness、
fresh full lint 与 M-05E 影响路径审计、working-tree Markdown、diff、敏感内容、规定
Debug 构建与 APK 身份/签名/16 KB/native packaging 审计。目录读写、备份/恢复、
public Download、Browser/Player 导出与旧布局扫描的真实设备行为继续单独标记
`verification_pending`。

M-05E 封板结果：完整 architecture `phase=m03` 最终为 `errors=[]`，Python
`166/166`，JVM `137 suites / 822 tests / 0 failures / 0 errors / 0 skipped`，
formal/fresh-clone readiness、working-tree Markdown `256/0`、diff、敏感签名和 lint
baseline normalization 通过。fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints`，baseline 另过滤
`1268 errors / 4373 warnings / 150 hints`；17 条 M-05E Kotlin 路径只命中
`BrowserDownloadSupport.kt` 未改正文的既有 `UseKtx` warning。

既有 `SdCardPath` baseline 只从 `OperitPaths.kt` location 迁到唯一
`KiyoriPaths.kt` owner，仍为 5791 条，归一化 SHA-256
`b51d9da65832ff45b0e9b652d2a6632d0da380dc427a37988e5b90b9861b06bf`。
规定 Debug 构建为 `233 actionable / 28 executed / 205 up-to-date`，APK 为
`477957302` bytes，SHA-256
`E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`；身份、SDK、
Application、唯一 launcher、`:crash/:repair/:player`、arm64 53 native、10 个播放器目标库
各一份、零重复 basename、Debug v2、单 signer 与 16 KB 对齐通过。

M-01 基线计数：

```text
14 个 Kotlin 文件
42 次 Kotlin 出现
1 次 Manifest 出现
6 次 Lint baseline 路径
16 个实现文件
49 次总出现
```

计数和允许文件必须与
[M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)
一致。

## Browser

基础定向测试组：

```text
BrowserAddressResolverTest
BrowserPresentationReleaseGateTest
BrowserBackgroundAnchorPolicyTest
WebSessionBrowserBackPolicyTest
WebSessionProfilePolicyTest
WebSessionHistoryPolicyTest
WebSessionBookmarkPolicyTest
BrowserDownloadPolicyTest
BrowserDownloadRuntimePolicyTest
UserscriptMatcherTest
UserscriptManagementPolicyTest
UserscriptStorageTransactionTest
UserscriptWebRequestEngineOwnershipTest
WebSessionBrowserChromeLayoutTest
```

执行形式：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "<fully-qualified-test-class>" `
  --no-daemon --console=plain
```

Browser 领域完成前还需：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

## Player

定向测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.ai.assistance.operit.core.player.PlayerPolicyTest" `
  --tests "com.ai.assistance.operit.core.player.PlayerSurfaceLeasePolicyTest" `
  --tests "com.ai.assistance.operit.core.player.runtime.PlayerRuntimeProtocolPolicyTest" `
  --tests "com.ai.assistance.operit.ui.features.player.PlayerControlsPolicyTest" `
  --no-daemon --console=plain
```

native 与 instrumentation 编译：

```powershell
.\gradlew.bat :app:externalNativeBuildDebug --no-daemon --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

最终 player packaging gate 由 Debug 构建触发，也可按项目任务单独核验。

## 完整 JVM 与 Debug 构建

高风险代码里程碑：

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

文档里程碑至少执行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

## APK 审计

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

先解析 Android Build Tools 的绝对路径。自动化中不依赖当前 shell 是否恰好把工具加入 PATH：

```powershell
$buildTools = (Get-Command aapt.exe -ErrorAction Stop).Source | Split-Path
$aapt = Join-Path $buildTools "aapt.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
```

基本信息：

```powershell
& $aapt dump badging app\build\outputs\apk\debug\app-debug.apk
```

签名：

```powershell
& $apksigner verify --verbose app\build\outputs\apk\debug\app-debug.apk
```

16 KB 对齐：

```powershell
& $zipalign -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk
```

SHA-256：

```powershell
Get-FileHash -Algorithm SHA256 app\build\outputs\apk\debug\app-debug.apk
```

必须检查实际输出，不只检查退出码。

## 静态合同反向搜索

示例：

```powershell
rg -n "OperitApplication|KiyoriApplication" app
rg -n "com\.ai\.assistance\.operit" app/src/main examples tools ci
rg -n "classDiscriminator|Class\.forName|Java\.type|Java\.com\.ai\.assistance\.operit" app examples
rg -n "preferencesDataStore|getSharedPreferences|databaseBuilder|enqueueUnique" app/src/main/java
rg -n "Java_com_ai_assistance_operit|System\.loadLibrary" app/src/main
```

搜索结果必须按兼容合同清单分类，不能把零匹配当作唯一目标。

## 未来设备验收目录

方案 v3 当前范围不使用手机、模拟器或 ADB。以下目录只为未来需要真实 Android 行为证据的
里程碑保留；G-00 与 M-01 均未执行这些设备操作。

### Application/Shell

- 冷启动与热启动
- crash/repair/player 进程
- 协议、权限引导和插件加载
- Kiyori Home、AI Home、抽屉、Back、旋转
- 外部 HTTP/HTTPS、分享、快捷方式

### Browser

- 普通和无痕 Profile
- 标签、历史、书签、下载、userscript
- APP_SHELL 与 background anchor
- 人工页面和 AI 工具共用同一窗口
- 退出、最小化、进程恢复

### Player

- 任意网络视频
- floating/fullscreen 往返
- Surface transfer
- 播放设置、Anime4K、倍速、音量
- `:player` 进程异常隔离

### 系统入口

- 默认助手
- 通知监听
- Widget
- 静态快捷方式
- DocumentsProvider
- Tasker
- External Chat
- Workflow schedule

### 数据

- 旧聊天、设置、模型配置、角色卡和工作区
- Browser history/bookmarks/userscripts/download tasks
- raw snapshot export/restore
- `Download/Kiyori`
- WorkManager 已排队任务

## 证据记录格式

每条命令记录：

```text
command:
cwd:
start time:
end time:
exit code:
task/test count:
failure count:
artifact:
artifact hash:
evidence level:
remaining verification:
```

未来设备任务的记录还需要：

```text
device model:
Android version:
WebView provider/version:
APK SHA-256:
pre-existing data present:
result:
```
