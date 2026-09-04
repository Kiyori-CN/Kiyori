---
status: implemented
plan_version: 4
last_reviewed: 2026-09-05
---

# 架构门禁与机器可读所有权规范

## 目的

文档只能解释架构，自动门禁负责阻止新代码重新越过边界。

v4 当前进度见 [总计划](index.md)。ARCH047 为 Browser Runtime 增加构造边界检查：
私有 constructor、唯一 `getSharedInstance` factory、volatile/synchronized 单实例发布，
以及禁止其他生产页面调用非共享 `create`。它保护实例级 profile、host、下载监听器和规则
订阅与静态会话注册表的一致生命周期，不使用源码哈希限制领域正常演进。
`ci/test/test_browser_runtime_ownership.py` 覆盖合法共享调用、额外 factory、公开构造、
缺少同步/发布、注释与字符串伪造、缺失 owner；实际 Android/WebView 生命周期仍须设备验收。

方案 v3 把该实现定义为 G-00，已经在 M-01 前完成首版实现：

```text
config/architecture/
├── README.md
├── package-ownership.toml
├── stable-identifiers.txt
├── manifest-components.txt
├── manifest-structure-hashes.txt
├── debug-manifest-components.txt
├── debug-manifest-structure-hashes.txt
├── persistence-names.txt
├── persistence-api-calls.txt
├── native-ipc-identifiers.txt
└── critical-file-hashes.txt

ci/script/
└── check_architecture_boundaries.py

ci/test/
└── test_architecture_boundaries.py
```

脚本必须使用项目 `.venv`，只读源码和 Git 索引，不修改文件。

## ownership schema

每条 ownership record 至少包含：

```toml
[[ownership]]
id = "browser-runtime"
path = "app/src/main/java/com/kiyori/feature/browser/runtime/**"
owner = "kiyori-browser"
sync_zone = "C"
phase = "browser"
allowed_import_roots = [
  "com.kiyori.capability.browser",
  "com.kiyori.platform",
]
forbidden_import_roots = [
  "com.kiyori.feature.player.ui",
  "com.ai.assistance.operit.ui.main",
]
stable_contracts = [
  "web_session_browser_store",
  "browser_download_settings",
]
required_tests = [
  "BrowserPresentationReleaseGateTest",
  "WebSessionBrowserBackPolicyTest",
]
```

字段规则：

- `id` 全局唯一且稳定
- `path` 必须匹配至少一个受管文件，除非 phase 尚未开始并显式声明 `planned = true`
- 当一个源文件同时命中一个无 glob 元字符的精确 `path` 和一个或多个宽泛 glob 时，只由
  该精确 record 管理；没有精确 record 时，多个宽泛 glob 命中仍是 multiple-owner error
- `owner` 对应文档中的唯一状态 owner
- `sync_zone` 只能是 `A/B/C/D`
- `allowed_import_roots` 使用包前缀，同时约束普通/static import 与源码中的完全限定项目引用
- `stable_contracts` 必须能在兼容合同清单中找到
- `required_tests` 必须映射到真实测试文件或明确的 future test
- 临时 exception 必须有 expiry milestone 和理由

规划中的 `com.kiyori` 不能只用一个宽泛 `feature/**` 或 `integration/**` owner。当前
machine schema 已按 browser、player、files、downloads、miniapp、home、settings、backup、
recovery、Operit integration、Shower、Shizuku 和 Tasker 分开登记；新增未登记领域直接
触发 unmanaged source。feature 只允许 capability、design、自身 feature 与 platform，
仅 `integration.operit` 可以直接依赖 `com.ai.assistance.operit`。仓库内 vendored
AndroidX/Sherpa/UUID 源码只允许保持自身包依赖，不得反向导入 Kiyori 或 Operit 产品代码。
依赖提取同时覆盖普通/static import 和源码中的完全限定 `com.ai.assistance.operit`/
`com.kiyori` 引用；注释、字符串、package 声明不会形成依赖边。

## 已实现的诊断规则

| 代码 | 规则 | 严重性 |
| --- | --- | --- |
| `ARCH001` | `com.ai.assistance.operit` 直接依赖 `com.kiyori.app` | error |
| `ARCH002` | Operit AI 直接依赖 Kiyori feature UI/ViewModel | error |
| `ARCH003` | capability 依赖具体 feature/runtime/UI | error |
| `ARCH004` | feature 直接依赖另一 feature 的内部实现 | error |
| `ARCH005` | design/platform 拥有业务状态或依赖 feature | error |
| `ARCH006` | 新 `Operit*` 产品所有权标识未登记 | 后续实现 |
| `ARCH007` | 新硬编码 `com.ai.assistance.operit.*` FQCN 未登记 | 后续实现 |
| `ARCH008` | Manifest component/action/authority 与 snapshot 无解释差异 | error |
| `ARCH009` | DataStore、SharedPreferences、数据库、备份或路径合同变化 | error |
| `ARCH010` | AIDL/JNI/native 名称在非专项里程碑变化 | error |
| `ARCH011` | 同一 capability 出现多个 runtime/store owner | 后续实现 |
| `ARCH012` | source path 与 package declaration 不一致 | error |
| `ARCH013` | ownership path 无文件或文件未被任何 owner 覆盖 | warning/error by phase |
| `ARCH014` | terminal gitlink 或内容变化 | error |
| `ARCH015` | build/APK/bundle/仓库外备份/private config 进入 Git 索引 | error |
| `ARCH016` | M-01 超出精确允许文件或出现非规范化命名差异 | error |
| `ARCH017` | M-02 contract 不完整、Operit concrete Application 依赖残留、第二 owner 或 Service Locator | error |
| `ARCH018` | M-03 Application 移动不纯、import snapshot/Lint path/Manifest phase 或过渡 exception 漂移 | error |
| `ARCH019` | M-04 根组合与 Operit host CompositionLocal 出现重复 owner、旧 import 或 app 反向依赖 | error |
| `ARCH020` | M-04A2 根 Composable 路径、package、源码 SHA、Operit import 集合、唯一 owner、M-04D9 content host 或旧运行时符号漂移 | error |
| `ARCH021` | M-04B Operit navigation policy 未由 integration 唯一拥有、Shell state 仍依赖 Operit navigation、源码 SHA/import 集合或 KiyoriApp 接线漂移 | error |
| `ARCH022` | M-04B Browser exit presentation capability contract 的 package、源码 SHA、唯一 owner、固定消费者或旧 import 漂移 | error |
| `ARCH023` | M-04B 纯 Shell state 路径/package/唯一 owner、Browser exit 与 Settings route capability import、KiyoriApp 接线或当前 MainActivity 过渡桥接漂移 | error |
| `ARCH024` | M-04B3 App Shell host 路径/package/规范化源码、15 个精确项目 import、唯一 helper owner、KiyoriApp host 或测试接线漂移 | error |
| `ARCH025` | M-04B4 AI Drawer host 路径/package/规范化源码、精确 Operit import、唯一 owner、App Shell host 或测试接线漂移 | error |
| `ARCH026` | M-04B5 primary destination presentation 拆分后的源码 SHA、精确 import、唯一声明组、App Shell host 或测试接线漂移 | error |
| `ARCH027` | M-04B6 Software Home 拆分后的路径/package、源码 SHA、9 个精确 Operit import、完整唯一声明组、App Shell host 或 27 个策略测试 import 漂移 | error |
| `ARCH028` | M-04B7 residual Browser Search 旧路径、源码 SHA、8 个 Operit import 加 1 个 Browser search-source capability import、页面/request/resolver 唯一 owner、App Shell/KiyoriApp/test 接线漂移 | error |
| `ARCH029` | M-04C route catalog 旧路径、新 catalog/runtime 源码 SHA 与精确 import、唯一 assembly owner、PackageManager/listener/gateway lifecycle、KiyoriApp/test 接线漂移 | error |
| `ARCH030` | M-04D1 pending-request owner 缺失、package/源码 SHA/精确项目 import/唯一声明漂移，MainActivity 第二状态 owner、Activity/shared-content/content-host 组合边界中的消费 API 接线缺失、owner 吸收平台副作用或合同测试缺失 | error |
| `ARCH031` | M-04D2 Intent decoder 缺失、package/源码 SHA/精确项目 import/唯一 symbol 漂移，decoder 吸收 host side effect、MainActivity 继续直接解析 payload、稳定常量桥接或合同测试漂移 | error |
| `ARCH032` | M-04D3 display coordinator 缺失、package/源码 SHA/精确项目 import/唯一 symbol/API 计数漂移，coordinator 吸收无关职责、MainActivity 残留显示配置或策略测试缺失 | error |
| `ARCH033` | M-04D4 shared-content coordinator 缺失、package/源码 SHA/精确项目 import/唯一 symbol/转交计数漂移，第二状态 owner、MainActivity 残留转交实现、Activity/content-host 分层调用数、总调用数或策略测试缺失 | error |
| `ARCH034` | M-04D5 task-visibility coordinator 缺失、package/源码 SHA/精确项目 import/唯一 symbol/API 判定漂移，coordinator 吸收状态或无关职责、MainActivity 残留最近任务实现、两个调用点或策略测试缺失 | error |
| `ARCH035` | M-04D6 orientation coordinator 缺失、package/源码 SHA/精确项目 import/唯一 state/reducer/dialog owner 漂移，coordinator 吸收无关职责或 `recreate()`，MainActivity 残留方向状态/presentation、Plugin Loading hide 顺序、调用数或行为测试缺失 | error |
| `ARCH036` | M-04D7 notification-permission coordinator 缺失、package/源码 SHA/精确 platform/resource bridge import、日志/Toast 投影计数或 MainActivity 早注册/单调用漂移，coordinator 回流系统权限 API、第二权限状态或 platform 策略测试缺失 | error |
| `ARCH037` | M-04D8 startup-gate coordinator 缺失、package/源码 SHA/精确项目 import/三态 resolver/唯一 UI 投影或 presentation owner 漂移，coordinator 复制协议/权限持久状态或吸收 lifecycle/plugin/content side effect，MainActivity 残留旧字段/import/页面分发、调用数、初始检查与两条完成回调顺序或策略测试缺失 | error |
| `ARCH038` | M-04D9 content host 缺失、package/源码 SHA/精确项目 import/唯一 request projection 或 host 漂移，host 复制 pending/plugin 状态或吸收 `setContent`/主题/startup/lifecycle/runtime side effect，MainActivity 残留直接 KiyoriApp/provider/参数装配、shared-content 调用数、执行顺序或合同测试缺失 | error |
| `ARCH039` | M-04E 精确 ownership 优先级未实现或允许重叠宽泛 glob，MainActivity 精确 owner 字段、稳定 package/FQCN/Manifest launcher、精确项目 import snapshot、`com.kiyori.feature` 零依赖、唯一 content host 或到期 ARCH001 例外清零发生漂移 | error |
| `ARCH040` | M-05A1 纯 ColorScheme/Browser/Settings design owner 缺失，旧/new 双 owner、preference/platform/Operit 依赖混入 design、consumer import/adapter/test/ownership 允许方向或固定视觉合同漂移 | error |
| `ARCH041` | M-05A2 semantic color/Compose adapter owner 缺失，旧/new 双 owner、枚举/色值/稳定 ID/底栏/天气/luminance 合同、55 个显式 import 消费文件、98 条 import、测试所有权或 ARCH025/026/027 snapshot 发生漂移 | error |
| `ARCH042` | M-05A3 pure root theme/Typography、app preference host、Application system-bar owner 或 `Theme.Kiyori` style 缺失，旧 `OperitTheme`/`Theme.Operit` 残留，偏好/Glass/字体/system-bar 职责重复，MainActivity/widget/ARCH039/Manifest snapshot 漂移，或 Player fullscreen system-bar 被吸收 | error |
| `ARCH043` | M-05B platform logger/formatter 或旧 AppLogger facade 缺失，executor/内部 filesDir/文件/开关状态出现第二 owner，logger 重新静态持有 Context，日志文件/格式/ToolPkg 合同、8 个 Kiyori consumer、旧 static-mock 兼容、formatter test、ownership exception 或 ARCH018/020/032～037 snapshot 漂移 | error |
| `ARCH044` | M-05C platform Activity lifecycle facts owner、Operit side-effect owner 或旧 ActivityLifecycleManager facade 缺失，callback 注册/弱引用/activity count/started count/foreground 状态出现第二 owner，旧完整 ABI、13 个旧 FQCN consumer、plugin/AI/Player/窗口副作用、JVM facts test 或 ownership 漂移 | error |
| `ARCH045` | M-05D platform notification permission capability、Operit resource bridge 或 app projection 缺失，API 33/grant/rationale/launcher 出现第二 owner，资源或持久偏好混入 platform，旧 coordinator ABI/早注册/日志/Toast、三条直接 consumer、Manifest 声明、policy tests 或 ownership exception 清理漂移 | error |
| `ARCH046` | M-05E Kiyori paths 或 backup projection 缺失，目录字面量/Environment/ensureDir/plugin ID/raw snapshot 排除出现第二 owner，旧 OperitPaths/OperitBackupDirs ABI 或纯委派漂移，9/7/37/0 consumer 集合、路径合同测试、M-03/M-05B/critical snapshot 漂移 | error |

`ARCH017` 在四个 M-02 platform contract 尚未出现时不改变 M-01 后基线。任一 contract
出现后，四个文件必须闭合，`com.ai.assistance.operit` 除唯一 Application 实现外不得再引用
`KiyoriApplication`，Application companion 不得继续持有 `instance`、`json`、
`appStartupTimeMs` 或无消费者的 `globalImageLoader`。process 安装入口只能由唯一
Application 调用，platform 中不得建立统一 registry 或按 key/class 解析的 Service Locator。
完整规则和精确消费者见
[M-02 Application 全局访问平台化精确清单](17_m02_application_platform_access_manifest.md)。

M-04A1 的宿主 CompositionLocal 合同、唯一声明和消费者迁移，以及 M-04A2 根组合纯移动
的 SHA/import/唯一 owner 锁定见
[M-04 根组合与 Shell 精确实施清单](19_m04_root_composition_and_shell_manifest.md)。

M-04E 不延长 `MainActivity.kt` 的 ARCH001 例外。稳定 Android launcher 使用精确
ownership record `operit-main-activity-compatibility` 管理，允许
`com.ai.assistance.operit`、`com.kiyori.app` 与 `com.kiyori.platform`，禁止
`com.kiyori.feature`。checker 只能在精确 path 与宽泛 glob 同时命中时选择精确 record；
两个宽泛 glob 同时命中必须继续报 multiple-owner，避免用新增宽泛规则掩盖所有权重叠。
ARCH039 还必须直接验证旧 exception 不存在、MainActivity 声明 package 与稳定 FQCN
一致、Manifest 中该 launcher 的 MAIN/LAUNCHER 入口保持、项目 imports 与
`m04e-main-activity-project-imports.txt` 完全一致，以及 `KiyoriMainContentHost` 仍只
import 和挂载一次。

M-05A1 的 design owner、精确路径、消费者、非目标和失败优先要求见
[M-05 Design 与 Platform 精确实施清单](20_m05_design_and_platform_manifest.md)。
ARCH040 必须在 production source 出现前先稳定报告
`M-05A1 design theme missing`。实现后只有 `com.ai.assistance.operit.ui` ownership 可以
新增 `com.kiyori.design` allowed root；其他 Operit records 不得变化。新 design source
不得 import Operit 或读取 preference，也不得出现 Activity/Window/system-bar/lifecycle/
permission/storage/repository/ViewModel。旧 preference resolver 只保留 snapshot/context
决策并委派唯一 bool design resolver，旧 Browser/Settings theme 与 ColorScheme 声明必须
清零。

M-05A1 实现后，ARCH024 的 App Shell import snapshot 继续表示完整项目 import 集合，而不是
只允许 Operit 根。snapshot 条目必须全部属于 `com.ai.assistance.operit` 或 `com.kiyori`
项目根、不得重复，并与源码的项目 import `Counter` 精确相等。当前集合为 11 个 Operit UI
import、2 个 capability import（Browser workspace 与 Settings route）和 2 个
`com.kiyori.design.theme` import；这些批准项不能掩盖新增、缺失或重复依赖。M-05A1 的
ARCH024/ARCH040 正反向测试与完整 architecture 已通过，最终封板仍以
全量测试、构建和 APK 审计为准。当前 M-05A1 已通过这些封板证据；后续 M-05A2 不得复用
ARCH040 代替独立的 semantic design failure-first gate。

M-05A2 使用 ARCH041 独立封板。新纯合同文件
`com/kiyori/design/theme/KiyoriSemanticColors.kt` 唯一拥有：

- `KiyoriSemanticTone` 的 `BLUE/GREEN/PURPLE/ORANGE/RED/CYAN/PINK` 固定顺序
- `kiyoriSemanticToneForStableId` 的 `Math.floorMod(hashCode, entries.size)` 映射
- `KiyoriSemanticColors`、14 组浅深 icon/container pair 与纯 resolver
- `KiyoriBottomNavigationSelectedFillColor = #FFC153`
- `resolveKiyoriWeatherSunColor` 的浅色 `#C57C00` 与深色 `#FFD166`

`com/kiyori/design/theme/KiyoriSemanticTheme.kt` 只保留两个 Compose adapter：
`KiyoriSemanticTone.resolveColors()` 与 `kiyoriWeatherSunColor()`；二者只能按
`MaterialTheme.colorScheme.background.luminance() < 0.5f` 委派纯 resolver，不得持有色值、
preference、状态、Android window/lifecycle/permission/storage 或 Operit 依赖。旧
`ui/theme/KiyoriSemanticTheme.kt` 必须删除，全部声明只能有一个 owner。

ARCH041 必须读取精确 consumer-import snapshot，锁定 51 个生产消费者、4 个测试消费者和
99 条 `com.kiyori.design.theme` import；任何旧 package import、完全限定旧 FQCN、缺失、
新增或重复边都失败。Operit 消费者只能位于已有 `operit-ui` ownership，不能为 core/data/
services 扩大 design permission。A2 同时把三个 M-04B snapshot 从 Operit-only 输入校验改为
完整项目 import multiset：AI Drawer、Primary Navigation 与 Software Home 分别只接受批准的
semantic design import 变化，ARCH025/026/027 的其余依赖、源码 hash、owner 和测试接线继续
精确锁定。

M-05A2 已于 2026-08-02 通过 ARCH041 正反向 fixture、failure-first 缺失源证据、真实
ARCH025/026/027/040/041、完整 architecture、全量 Python/JVM、formal/fresh-clone、
范围内 lint、规定 Debug 构建与 APK 静态审计。仓库 full lint 仍报告既有 current-only
问题，但两个新 design source 为 0 命中，唯一受影响路径命中不在 A2 改动行；该事实不放宽
ARCH041，也不把范围外 lint 债务伪装为本切片失败。M-05A3 必须建立独立后续门禁，不能
复用 ARCH041 代替 root theme、preference host 或 system-bar ownership 验证。

M-05A3 使用 ARCH042 独立封板。必须新增：

- `com/kiyori/design/theme/KiyoriTheme.kt`：只接收已解析 `ColorScheme`、标准命名的背景
  effect `modifier` 与 `Typography`，保持
  `fillMaxSize -> background -> effect modifier -> content` 顺序；不得读取 Context、
  UserPreferences、Flow、Activity、Window、system-bar 或 Operit
- `com/kiyori/design/theme/KiyoriTypography.kt`：唯一固定零 tracking Typography 与纯
  `applyFontFamilyToTypography`；不得读取字体文件、Context、UserPreferences 或日志
- `com/kiyori/app/theme/KiyoriTheme.kt`：唯一 `KiyoriTheme(content)` app host，精确订阅现有
  `useSystemTheme/themeMode/statusBarHidden/useCustomFont/fontType/systemFontName/customFontPath/fontScale`
  八个 Flow，以既有 initial 值计算 dark theme、调用现有配置字体 adapter、创建现有 Glass
  状态，并委派 pure design theme 与 platform system-bar
- `com/kiyori/platform/window/KiyoriApplicationSystemBars.kt`：唯一 Application
  `enableEdgeToEdge`、透明 status bar、navigation background、status-bar hide/show、
  transient-by-swipe 与 API 29 navigation contrast owner；不得读取 preference 或 Operit

旧 `ui/theme/Theme.kt` 必须删除。MainActivity 与
`ToolPkgDesktopWidgetConfigActivity` 只能各导入一次 app host；后者通过文件精确 ARCH001 例外
复用唯一 host，不能放宽整个 `operit-widget` 到 `com.kiyori.app`。app host 对旧
UserPreferences/Type/Glass 的过渡依赖同样只允许文件精确 ARCH004 例外并由 ARCH042 锁定。

固定 `Typography` 与 `applyFontFamilyToTypography` 从旧 Type owner 移入 design，旧 Type 只
保留配置字体、文件读取、UserPreferences 常量与日志适配；`OperitUtilityTheme`、
`FloatingWindowTheme` 和三个纯字体应用消费者改为 design import，七条外部 import 由
consumer snapshot 精确锁定。旧 `KiyoriThemeTest` 删除，零 tracking 断言迁入
`KiyoriDesignThemeTest`。

资源层必须把 6 个 values 变体声明和 Manifest 6 个引用从 `Theme.Operit` 精确改为
`Theme.Kiyori`，保持 `KiyoriThemeBase`、全部 item、parent、浅深/v27/v29 行为和 Manifest
组件集合不变，并更新 m03 semantic Manifest hash。ARCH039 MainActivity 完整项目 import
snapshot 只把旧 theme import 改为 `com.kiyori.app.theme.KiyoriTheme`。

M-05A3 封板时 PlayerActivity 的 LF-normalized SHA-256 为
`AEF88E8F34DD08098D858E4E5D3F36CBF1867AE36E6C44B96346F6A0BC11A756`。QD-04
在不改变 fullscreen system-bar owner 的前提下，把 Manifest 中重复的固定方向声明收口到
既有 PlayerActivity policy，并把静态 browser host 访问改为 Application-scoped session
owner；ARCH042 与 `m05a3-root-theme-sha256.txt` 当时的保护值因此更新为
`0958C96D76C5C30E98EA84F08AC29CA576FA263C497AD1976BFEF9B4E326B7CA`。2026-08-28 的
默认视频播放器增量在不改变 fullscreen system-bar owner 的前提下增加外部 `ACTION_VIEW`
播放器分流；当前保护值同步为
`61247CB0F9C67385C39574C2F11A48A810AD97A6A119FDF6B851850CF0354CA9`。
`setDecorFitsSystemWindows(false)`、transient system-bars 与 hide(systemBars) 仍只能由
PlayerActivity 拥有。LiquidGlass/WaterGlass 算法、CompositionLocal 和 capability 判断保持
原 owner；A3 与 QD-04 均不新增 alias、facade、旧 style、fallback、第二偏好流或第二
system-bar owner。

M-05A3 已按该合同封板：ARCH042 failure-first、正反向 fixture、真实工作树检查与完整
architecture `phase=m03` 均通过。四个新 owner、七条 consumer import、两个文件精确 ownership
exception、6 个 style 声明、Manifest 6 个引用、m03/ARCH039 snapshot 和 PlayerActivity
当前保护值均由门禁锁定；旧 Theme owner、旧 symbol、旧 style 和旧测试清零。

M-05B 使用 ARCH043 独立封板。`com/kiyori/platform/logging/KiyoriLogger.kt` 是唯一
executor、提前解析的内部 filesDir、package-log root provider、`logFile`、`packageLogFile`
和 `enableFileLogging` owner；进程 Context 继续只由 `ApplicationContextAccess` 持有，它只能
导入 `ApplicationContextAccess` 与
`ApplicationStartupTime` 两个项目内 platform contract，不得导入 Operit、feature、preference
或 lifecycle manager。`KiyoriLogTextFormatter` 唯一持有 cause/stack/circular/truncation
实现，旧 `ThrowableTextFormatter.kt` 必须删除。

旧 `com.ai.assistance.operit.util.AppLogger` 必须继续存在并保留 priority、property 与全部
`@JvmStatic` API，但只能委派 `KiyoriLogger`。facade 中 `@Volatile`、executor、FileWriter、
formatter、正则、Context 和文件引用都必须为 0。Provider 的 `bindContext` 只把
`applicationContext.filesDir` 解析为 `File` 后交给 logger，日志导出的
`getLogFile/resetLogFile` 以及两个 `Mockito.mockStatic(AppLogger::class.java)` 测试继续锁定
旧入口可解析。

ARCH043 读取 `m05b-platform-logging-sha256.txt` 与
`m05b-kiyori-logger-consumers.txt`，精确锁定四个 logging/Crash source 和 8 个 Kiyori app
消费者。Kiyori source 旧 logger import 与完全限定引用必须为 0；Operit 消费者不批量迁移。
`KiyoriApplication` 和 `MemoryDocumentsProvider` 分别保持 Application/Provider 时序下的
早期目录绑定。M-05B 封板时 package-log 根目录由 `OperitPaths::kiyoriRootDir` 提供；
M-05E 后两个绑定点均改为 `KiyoriPaths::kiyoriRootDir`，旧 facade 继续可解析但 platform
logger 不复制 `Download/Kiyori` 目录计算。Display coordinator 的 logging 例外必须清零，
其余精确例外只保留仍存在的 resource、state 或 Operit integration bridge。

M-05B 的 failure-first 真实证据必须只报告缺少
`app/src/main/java/com/kiyori/platform/logging/KiyoriLogger.kt`。正反向 fixture 必须覆盖
platform 导入 Operit、facade 重新持有状态、文件名漂移、旧 formatter 残留、consumer 回流、
static-mock/formatter test 删除和过期 exception 回流。完整合同与非目标见
[M-05 Design 与 Platform 精确实施清单](20_m05_design_and_platform_manifest.md)。

M-05C 使用 ARCH044 独立封板。`KiyoriActivityLifecycle.kt` 必须是唯一
`Application.ActivityLifecycleCallbacks` 注册 owner，并由唯一
`KiyoriActivityLifecycleFacts` 保存 current Activity 弱引用、activity/started count 与
foreground boolean。业务能力可以通过 `registerActivityStoppedListener()` 订阅只读的 stopped
事实，但不能自行向 `Application` 注册第二个 callback，也不能把业务状态写回 platform owner；
platform 文件不得导入任何项目代码，也不得出现 ApiPreferences、
AppLogger、plugin、AI、Player、VirtualDisplay、Shower、WindowManager、CoroutineScope 或
keep-screen-on 状态。

`OperitActivityLifecycleIntegration.kt` 只保留原 keep-screen-on 偏好/计数和
plugin/External Chat/microphone/PlayerCrash/VirtualDisplay/Shower 副作用，不得注册 callback、
保存 Context、WeakReference、activity/started count 或 foreground state。plugin 的六个
Activity event 与 application foreground/background event、`application_foreground` reason、
`2500L` 阈值、`FLAG_KEEP_SCREEN_ON` 和最后 Activity `<= 0` 清理边界必须保持。

旧 `ActivityLifecycleManager` 必须继续实现 `Application.ActivityLifecycleCallbacks`，保留
`INSTANCE`、4 个业务方法和 7 个 callback 的 JVM ABI，并逐项委派 platform/integration；
facade 中不得出现注册、Context、协程、偏好、状态或运行时副作用。ARCH044 读取
`m05c-platform-lifecycle-sha256.txt` 与 `m05c-legacy-lifecycle-consumers.txt`，锁定三个生产
owner 和 13 个旧 FQCN 引用路径；`KiyoriApplication` 继续唯一调用旧
`initialize(this)`。四条 JVM 测试锁定 current Activity identity、前后台零边界、destroy
count 与唯一 callback 注册。failure-first 必须先只报告缺少
`com/kiyori/platform/lifecycle/KiyoriActivityLifecycle.kt`；正反向 fixture 必须拒绝状态回流、
platform 反向依赖、第二注册、旧 consumer 丢失和测试断言删除。

M-05C 已按该合同封板：ARCH044 failure-first、正反向 fixture、真实工作树与完整 architecture
`phase=m03` 通过；三个生产 owner hash、13 个旧 FQCN consumer、完整旧 JVM ABI、唯一
callback/facts owner、Operit side-effect owner、stopped 事实订阅边界与 4 条 JVM facts test
均由门禁锁定。浏览器恢复协调器只订阅稳定 `MainActivity.onStop` 事实，并在
`isChangingConfigurations=false` 时请求最新普通窗口投影；它不是第二 lifecycle owner。

M-05D 使用 ARCH045 独立封板。`KiyoriNotificationPermissionCapability.kt` 必须是启动阶段
`POST_NOTIFICATIONS` API 33 guard、system grant/rationale、`RequestPermission` launcher 与
纯 action resolver 的唯一 owner；platform 不得 import 项目代码，不得出现 Toast、资源、
日志、DataStore、SharedPreferences、其他权限或持久状态。

`OperitNotificationPermissionResources.kt` 只允许桥接现有 denied/rationale 两个
`R.string` ID，不复制文案、不读取权限、不持有状态。旧
`KiyoriMainNotificationPermissionCoordinator` 保留 FQCN、
`KiyoriMainNotificationPermissionCoordinator(ComponentActivity)` 和 `checkAndRequest()` 的
精确 JVM ABI，继续拥有 6 条日志与 2 个 Toast 投影，但不得再出现 Build/Manifest/
PackageManager/RequestPermission/ContextCompat/rationale/launcher 或直接 `R` import。
`MainActivity` 继续一参数直接字段构造和一次 startup 调用，不接收 platform 或 resource
实现。

ARCH045 读取 `m05d-notification-permission-sha256.txt` 与
`m05d-direct-notification-permission-consumers.txt`，锁定四个 owner/non-owner hash、
三条直接 Kotlin consumer、一份 Manifest 声明、未改 `AndroidPermissionPreferences` hash、
新 platform 4 条 JVM policy tests、旧 app action/resolver/test 清零以及 coordinator
ARCH004 例外清零。failure-first 必须先只报告缺少
`com/kiyori/platform/permission/KiyoriNotificationPermissionCapability.kt`；正反向 fixture
必须拒绝反向依赖、状态/权限范围扩张、资源或 Activity 回流、consumer/preference/test 漂移
和旧 exception 回流。

M-05D 已按该合同封板：ARCH045 failure-first、ARCH036/045 正反向 fixture、真实工作树与
完整 architecture `phase=m03` 通过；四个 hash、三条直接 consumer、一份 Manifest 声明、
旧 coordinator 精确 ABI/日志/Toast、4 条 platform policy tests、未改 preference owner 与
到期 exception 清零均由门禁锁定。

M-05E 使用 ARCH046 独立封板。`com/kiyori/platform/storage/KiyoriPaths.kt` 必须是全部
Kiyori public/internal/cache/files/backup 路径字面量、唯一 `Environment` public-download
读取、唯一 `ensureDir` 和 plugin ID trim/Regex/hash 算法 owner；不得 import 项目代码，不得
读取业务状态或执行 Browser/Player/Backup 流程。

`KiyoriBackupPaths.kt` 只允许无状态委派 `KiyoriPaths`，不得声明目录字面量、排除集合、
`Environment`、`mkdirs` 或 `ensureDir`。旧 `OperitPaths` 与 `OperitBackupDirs` 必须保留
原 object、公开常量和方法 JVM ABI，但只能逐项委派；facade 中目录字符串、Regex、hash、
排除集合和创建逻辑必须清零。

ARCH046 读取 `m05e-storage-paths-sha256.txt`、
`m05e-direct-kiyori-path-consumers.txt`、
`m05e-direct-kiyori-backup-path-consumers.txt` 与
`m05e-legacy-operit-path-consumers.txt`，精确锁定四个 owner/facade hash 和
`9 direct path / 7 direct backup / 37 legacy path / 0 legacy backup external`
consumer 集合。`KiyoriPathsTest` 必须保留 public 路径、raw snapshot 排除、plugin ID、
backup hierarchy 和 pool hierarchy 五类断言。M-03 Application、M-05B logging 与
`critical-file-hashes.txt` 中三个备份实现必须随合法 import 迁移更新 snapshot，而不得
放宽旧行为合同。

failure-first 必须先只报告缺少
`com/kiyori/platform/storage/KiyoriPaths.kt`；正反向 fixture 必须拒绝 platform 反向依赖、
projection/facade 第二计算、consumer 集合漂移、旧 backup consumer 回流和合同测试删除。
完整范围、兼容边界与验证证据见
[M-05 Design 与 Platform 精确实施清单](20_m05_design_and_platform_manifest.md)。

M-05E 已按该合同封板：ARCH046 failure-first、M-05B/M-05E 正反向 fixture、真实
M-03/M-05B/M-05E/critical 检查与最终完整 architecture `phase=m03` 均通过；四个 source
hash、`9 / 7 / 37 / 0` consumer 集合、旧 facade 完整 ABI、五条 JVM 路径合同和唯一
calculation owner 均由门禁锁定。

## 旧标识登记

`Operit*` 名称必须属于以下集合之一：

```text
product_rename
split_before_rename
operit_subsystem
ecosystem_contract
serialization_contract
android_component_contract
native_or_ipc_contract
historical_attribution
```

门禁不能使用“源码中不允许出现 Operit”作为规则。它应拒绝未分类的新标识，而不是误删合法生态名称。

## stable contract snapshot

当前已生成并审阅：

```text
config/architecture/
├── package-ownership.toml
├── stable-identifiers.txt
├── manifest-components.txt
├── manifest-structure-hashes.txt
├── debug-manifest-components.txt
├── debug-manifest-structure-hashes.txt
├── persistence-names.txt
├── persistence-api-calls.txt
├── native-ipc-identifiers.txt
└── critical-file-hashes.txt
```

这些文件由人工批准后进入 Git。脚本重新提取当前源码状态并以精确出现次数与
snapshot 比较，因此新增和删除同类字面量都会触发检查。Main 与 Debug Manifest 分别由
`manifest-components.txt` / `manifest-structure-hashes.txt` 和
`debug-manifest-components.txt` / `debug-manifest-structure-hashes.txt` 锁定；
Debug 专用的导出 QA receiver 及其 `android.permission.DUMP` 限制不会混入 main/release。
两组 snapshot 均保留 component、action、category、authority、scheme、host、MIME type、
process 和 permission 的重复次数，并保存完整 XML 语义树哈希；哈希忽略格式、属性顺序
和同级元素顺序，但保留节点层级与全部属性值，因此权限删除、`exported`/`launchMode`/
备份配置变化和 intent-filter 归属漂移都会失败。关键文件 snapshot
直接核对 AIDL、Room schema/entity、ObjectBox model/目录映射、已持久化 WorkManager
worker/scheduler 与备份/恢复实现
经 CRLF-to-LF 规范化后的 SHA-256，确保 Windows/Linux checkout 一致。
`persistence-api-calls.txt` 同时固定 71 个持久化 API 调用记录，扫描时忽略源码字符串、
注释和排版差异，保存文件路径、API、目标参数与重复次数，防止只新增新名称而旧字面量
计数不变时绕过 ARCH009；`preferencesDataStore` alias 和 `Room.databaseBuilder` 直接导入
会被拒绝，避免改写调用名绕过扫描。当前提取器已正式支持
`DataStoreFactory.create(..., produceFile = <stable expression>)`，浏览器恢复 store 以
`browserSessionRecoveryFileProducer` 登记；未登记的 DataStore 合同继续由 ARCH009 拒绝，
`PreferenceDataStoreFactory.create` 仍属于未审查 API。其他尚未建模的 DataStore/default
SharedPreferences/SQLite 创建 API 也会失败，必须先增加提取器和合同设计。

snapshot 更新要求：

1. 当前任务明确包含对应合同变化
2. 相关正式文档先更新
3. diff 中显示旧值、新值和迁移理由
4. 对应测试与当前任务授权范围内的运行验证完成
5. 不能只更新 snapshot 让检查变绿

M-01 还需要一份 candidate rule，引用
[M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)：

- 允许 16 个实现文件
- 允许 49 次旧符号到新符号的映射
- 规范化名称后 Application 文件内容一致
- Manifest 只改变 Application 类名
- Lint baseline 只改变 6 个文件路径
- 任何计数漂移都要求重新生成影响清单并重新批准，不能扩大允许范围

## 命令接口

当前 CLI：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

可选参数：

```text
--base <commit>
--phase <auto|baseline|m01|post-m01>
--json
```

默认行为：

- 检查当前工作树或 candidate tree
- 输出稳定诊断 code、文件、行号和规则
- 任一 error 返回非零
- warning 不隐藏 error
- 不自动修复源码

`auto` 同时检查 base tree 与当前 tree：只有 base 仍包含
`OperitApplication.kt`、当前 tree 已包含 `KiyoriApplication.kt` 时才进入 `m01`。
M-01 完成后的 main 构建和未来 PR 使用 `post-m01`，继续核对新 Manifest 入口，但不会重复
套用一次性的 16 文件纯改名清单。

## CI 接入

已接入：

- `check_formal_readiness.py` 继续负责身份、子模块、品牌和仓库卫生
- `check_architecture_boundaries.py` 负责 package、owner、合同和依赖方向
- `pr_check.py` 按 scope 触发 architecture gate
- Android build lane 在 Gradle 前运行同一检查

本地与 CI 必须调用同一脚本，不在 workflow 复制规则。

## exception 规则

exception 只允许处理真实的过渡边界：

```toml
[[exception]]
rule = "ARCH001"
path = "..."
reason = "..."
expires_after = "M-03"
owner = "..."
```

禁止：

- 无期限 exception
- 通配符或目录级宽泛忽略
- 未被实际诊断使用的 exception
- 因检查失败扩大 allowlist
- 使用 exception 隐藏第二状态 owner
- 在完成里程碑后留下过期 exception

G-00 历史基线有 6 条 ARCH012 文件级例外，记录当时的 package/path
错位或 vendored UUID 来源。v4 已移除修正 package 后的 NewFolderDialog 失效例外；当前
有效项以 `package-ownership.toml` 和 unused-exception 检查为准。

## G-00 验收证据

- 架构专测试：36 项通过，包含正向、负向、Windows 路径、例外、未暂存改名、
  Git ignored dependency tree、Java static import、重复 Manifest component 和关键文件
  hash drift、完整 Manifest 语义漂移、JNI 完整符号替换、新持久化调用识别，
  未建模持久化 API 拒绝、持久化 alias 绕过拒绝、vendored 反向依赖拒绝，
  普通/static import 与完全限定项目引用绕过拒绝，以及 feature/Operit 双向依赖拒绝场景
- 全量 `ci/test`：102 项通过
- 当前工作树架构检查：`phase=post-m01` PASS
- formal readiness：PASS
- fresh clone reproducibility：PASS
- 最终 bundle 恢复演练促使扫描范围收口为 Git tracked + non-ignored untracked，
  恢复克隆与开发工作树的 stable literal 计数一致
- 深度合同审计补齐 Manifest Intent/authority/process、全部已登记 SharedPreferences、
  ObjectBox 目录、provider AIDL、备份格式、WorkManager 名称/worker 入口，以及
  AIDL/Room/ObjectBox/backup 文件哈希
- 未修改 Android 运行时代码、Manifest、资源、AIDL、native 或 terminal

## 门禁自身验收

- 正常文件通过
- 每条 error 规则至少有一个失败测试
- 路径分隔符在 Windows/Linux 一致
- 未跟踪文件和 candidate tree 都可检查
- 非 UTF-8 或生成文件边界明确
- 诊断顺序稳定
- JSON 输出可被 CI 和后续可视化工具读取
- 不访问网络、不读取 `.env`、不输出私密内容
