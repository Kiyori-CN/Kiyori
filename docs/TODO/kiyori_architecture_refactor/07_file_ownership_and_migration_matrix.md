---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-08-01
---

# 源码所有权与文件迁移矩阵

## 使用方法

本表是迁移前的路径级决策，不是“把整个目录搬走”的授权。

v4 的全域迁移与当前状态见 [全域设计](22_full_project_design_and_execution.md) 和
[总计划](index.md)。2026-09-05 起，三个导航展示文件的历史整文件 SHA 由 ARCH049
状态/回调/生命周期语义检查替代，原唯一 owner、依赖与测试接线继续执行。

每个文件在进入源码变更前必须拥有以下字段：

- 当前路径
- 当前真实 owner
- 目标路径或保留路径
- 迁移动作
- 上游同步等级
- 稳定合同影响
- 前置里程碑
- 必须验证的行为

没有出现在矩阵中的文件不得因为包名相似而批量移动。

## 动作定义

| 动作 | 含义 |
| --- | --- |
| `KEEP-OPERIT` | 留在 Operit 兼容岛，保持路径和公开类型可被上游识别 |
| `KEEP-CONTRACT` | 作为稳定外部入口保留旧 FQCN，内部实现可委派到 Kiyori |
| `MOVE-KIYORI` | 所有权已经明确，按纯移动里程碑迁入 `com.kiyori` |
| `SPLIT` | 当前文件混合两个 owner，先拆职责，再移动其中一侧 |
| `ADAPTER` | 旧入口只保留单一委派，真实状态和实现位于唯一新 owner |
| `REVIEW` | 需要逐文件确认，不能由目录规则自动决定 |
| `EXCLUDE` | 明确不在本次重构范围，例如 terminal |

## 非 Kotlin/Java 资产矩阵

包路径移动不能只检查 `app/src/main/java`。以下路径必须和源码迁移一起审计，但不能因为
出现 `Operit` 字符串就批量改名：

| 路径/资产 | 首选动作 | 保护重点 |
| --- | --- | --- |
| `app/src/main/AndroidManifest.xml` | `KEEP-CONTRACT` / `REVIEW` | application、导出组件、进程、权限、intent-filter、authority 和相对类名 |
| `app/src/main/aidl/` | `KEEP-CONTRACT` | AIDL package、Parcelable 字段、callback 顺序和 transaction 语义 |
| `app/src/main/cpp/`、`CMakeLists.txt`、`*.cpp`、`*.h` | `KEEP-CONTRACT` / `REVIEW` | JNI 导出符号、注册表、ABI、库名、编译定义和 native 资源 |
| `app/src/main/res/xml/` | `KEEP-CONTRACT` / `REVIEW` | `provider_paths`、快捷方式、备份规则、文件选择器和组件引用 |
| `app/src/main/res/values*/` | `REVIEW` | 用户可见品牌文案与协议/系统识别字符串分离；不把资源 key 当作显示文案批量改名 |
| `app/src/main/res/drawable*/`、`mipmap*/`、`font/` | `REVIEW` | 资源 ID、主题引用、启动图标和 UI 视觉零变化；纯目录移动不得夹入设计改版 |
| `app/proguard-rules.pro`、`consumer-rules.pro` | `KEEP-CONTRACT` / `REVIEW` | FQCN keep、反射、JNI、WorkManager、ToolPkg bridge 和序列化类型 |
| `app/build.gradle.kts`、`gradle/`、`buildSrc/` | `REVIEW` | namespace、applicationId、source set、任务、native packaging 和上游同步热点 |
| `app/src/test/`、`app/src/androidTest/` | `MOVE-KIYORI` / `KEEP-CONTRACT` | 测试包镜像真实 owner；测试迁移不能改变断言语义或掩盖旧入口缺失 |
| `examples/`、`assets/`、ToolPkg/Skill 脚本 | `KEEP-CONTRACT` / `REVIEW` | `Java.type`、`Class.forName`、JSON FQCN、协议样例和公开脚本兼容性 |
| `ci/`、`tools/`、项目文档 | `REVIEW` | 检查规则、构建入口、路径说明和协作者工作流同步；不把临时日志写入正式文档 |
| `app/build/`、`.gradle/`、`.idea/`、`local.properties`、签名/密钥 | `EXCLUDE` | 生成物、机器状态和私密配置不得进入迁移提交 |
| `terminal` gitlink 与其未初始化内容 | `EXCLUDE` | 本轮不初始化、不改动、不备份为源码副本、不进入提交 |

每个非 Kotlin/Java 资产都必须能追溯到对应源码里程碑、稳定合同、验证命令和回滚点。
Manifest、AIDL、native、资源或 CI 的变化如果不是纯路径引用修正，必须从普通文件移动提交中
拆成独立风险批次。

## 一级目录矩阵

| 当前路径 | 规模 | 当前真实内容 | 首选动作 | 目标或保留位置 | 前置条件 |
| --- | ---: | --- | --- | --- | --- |
| `api/` | 76 | AI provider、语音、外部 HTTP 和聊天服务 | `KEEP-OPERIT` / `SPLIT` | Operit AI runtime；Kiyori 外部入口由 integration 适配 | 先区分 AI API 与产品平台 API |
| `core/application/` | 3 | Application、生命周期和前台服务辅助 | `SPLIT` | `com.kiyori.app` 与 `com.kiyori.platform.lifecycle` | 先收口全局 Application 访问 |
| `core/avatar/` | 35 | AI 角色与渲染实现 | `KEEP-OPERIT` | Operit AI UI/runtime | 不与 Kiyori design system 混合 |
| `core/browser/` | 2 | Browser address/presentation policy | `MOVE-KIYORI` | `feature/browser/domain`、`presentation` | capability 和状态 owner 先冻结 |
| `core/chat/` | 6 | AI 消息与 hooks | `KEEP-OPERIT` | Operit AI runtime | 保护 hooks FQCN 和 ToolPkg 入口 |
| `core/config/` | 4 | AI prompt/config | `KEEP-OPERIT` | Operit AI runtime | 不把 Kiyori 设置页误当 owner |
| `core/player/` | 16 | PlayerSession、Surface lease、mpv runtime | `SPLIT` | Kiyori player domain/runtime；AIDL/service 保留兼容入口 | 先完成跨进程和 native contract inventory |
| `core/subpack/` | 5 | APK/EXE/export 工具 | `KEEP-OPERIT` / `REVIEW` | Operit 工具 runtime 或独立 export feature | 保护 ToolPkg/脚本 Java bridge |
| `core/tools/` | 191 | AI tool registry、system tools、browser adapters、JS/WASM | `KEEP-OPERIT` / `SPLIT` | AI tool runtime；Kiyori capability adapter | 不能把整个目录改成 product feature |
| `core/workflow/` | 4 | Workflow worker/scheduler/executor | `KEEP-CONTRACT` | Operit workflow；worker FQCN 稳定入口 | 先完成 WorkManager 恢复验证 |
| `data/` | 130 | AI 数据、偏好、数据库、备份、市场和浏览器存储 | `REVIEW` | 按 owner 分到 Operit data、Kiyori feature/data 或 platform/storage | 先依据合同清单逐文件审查 |
| `integrations/` | 17 | HTTP、Tasker、外部 Intent 等 | `SPLIT` | `integration/operit`、`integration/tasker`、`integration/http` | action、extra 和外部调用保持稳定 |
| `plugins/` | 14 | PluginRegistry、ToolPkg lifecycle | `KEEP-OPERIT` | Operit plugin ecosystem | Kiyori 只通过 integration 调用 |
| `provider/` | 3 | DocumentsProvider 与外部文件入口 | `KEEP-CONTRACT` | 旧 FQCN 或明确 provider adapter | authority 和 URI 不变 |
| `services/` | 23 | AI、浮窗、通知、语音和服务入口 | `SPLIT` / `KEEP-CONTRACT` | Kiyori platform/service 或 Operit AI service | 先确认导出状态和系统保存关系 |
| `ui/main/` | 30 | Kiyori Shell、MainActivity、AI route 和 root composition | `SPLIT` | `com.kiyori.app`、`integration/operit/navigation` | 不可整体移动 |
| `ui/features/chat/` | 164 | Operit AI 对话和输入 | `KEEP-OPERIT` | Operit AI UI | 保持 AI 会话和渲染合同 |
| `ui/features/websession/` | 27 | Browser chrome、drawer、userscript UI | `MOVE-KIYORI` | `feature/browser/ui` | UI 只观察同一 Browser owner |
| `ui/features/player/` | 7 | Player Activity、controls、fullscreen | `MOVE-KIYORI` / `KEEP-CONTRACT` | `feature/player/ui`；Activity FQCN 单独审查 | 保持 Surface lease 和 Activity entry |
| `ui/features/settings/` | 53 | AI 设置、Kiyori 设置、市场和主题设置 | `SPLIT` | `feature/settings` 与 Operit AI UI | 以 persisted owner 而非页面标题分类 |
| `ui/features/toolbox/` | 47 | AI 工具箱和各类工具页 | `KEEP-OPERIT` / `SPLIT` | Operit AI tools；Kiyori product pages only when owner exists | 不把空页面接入错误领域 |
| `ui/theme/` | 11 | 共享主题、Kiyori semantic theme、utility theme | `SPLIT` | `design/theme` 与 Operit AI local theme | 先保证视觉快照和偏好语义不变 |
| `ui/common/` | 38 | Markdown、Compose DSL、通用显示和组件 | `SPLIT` / `REVIEW` | AI renderer、design component、platform display | 特殊渲染域不能被普通主题覆盖 |
| `ui/floating/` | 29 | 浮动聊天、语音、屏幕识别、窗口 | `KEEP-CONTRACT` / `SPLIT` | Kiyori platform presentation 与 Operit AI UI | 保持 overlay、权限和生命周期 |
| `util/` | 76 | 路径、序列化、网络、缓存、native、日志和文本 | `SPLIT` | platform、feature data、Operit compatibility | 禁止整体迁入 `com.kiyori.util` |
| `widget/` | 7 | Android widget 与动态 route | `KEEP-CONTRACT` / `ADAPTER` | 旧组件入口 + Kiyori route adapter | 保持 widget host 偏好和组件 FQCN |

## 重点文件组

### Application 与 Shell

| 文件组 | 第一阶段 | 后续目标 |
| --- | --- | --- |
| `com/kiyori/app/KiyoriApplication.kt` | M-01 原包改名、M-02 全局访问收口与 M-03 包迁移已完成 | M-07 把剩余 Operit 启动装配移入 integration 并删除文件精确 exception |
| `com/kiyori/app/KiyoriApp.kt` | M-04A1/A2 与 M-04C 完成；catalog/PackageManager/listener/gateway lifecycle 已移出，剩余 25 个 Operit host/Browser/AI/platform import 由 ARCH020 锁定 | M-04D/M-06/M-07 随各 owner 迁移继续删除过渡 import |
| `ui/main/MainActivity.kt` | `KEEP-CONTRACT`；M-04D1-D9 已把 pending、Intent decode、display、shared content、task visibility、orientation、notification permission、startup gate 与 content assembly 移到唯一 Kiyori owner；M-04E 已用精确 ownership record 替代到期 ARCH001 例外，并由 ARCH039 锁定稳定 package/FQCN/Manifest launcher、精确 imports、feature 零依赖和唯一 content host | 保持稳定 Android 兼容入口、lifecycle、`recreate()` 与剩余 runtime side-effect 执行点；随 M-05/M-06/M-07 owner 迁移逐项收窄 imports，不移动、不复制 Activity |
| `com/kiyori/app/startup/KiyoriMainPendingRequests.kt` | M-04D1 新建；唯一 pending files/text、Browser、OAuth、shortcut、route、Shell destination 与 current navigation owner，SHA/import/API 接线由 ARCH030 锁定 | M-07 删除临时 `NavItem` 过渡依赖；不得吸收 Intent、时间戳或平台副作用 |
| `com/kiyori/app/startup/KiyoriMainIntentDecoder.kt` | M-04D2 新建；唯一 Intent payload/action decoder、密封 command 与稳定常量字面值 owner，SHA/import/Activity 接线由 ARCH031 锁定 | M-07 删除 GitHub/ToolPkg 临时过渡依赖；不得吸收 Intent mutation、request-ID 或 runtime/UI side effect |
| `com/kiyori/app/startup/KiyoriMainDisplayCoordinator.kt` | M-04D3 新建；M-05B 已把唯一日志依赖迁入 `KiyoriLogger` 并删除到期 exception；SHA/import/API 接线由 ARCH032/043 锁定 | 保持无状态 sustained-performance/refresh-rate/hardware-acceleration owner；不得吸收 Activity 状态或其他 host 职责 |
| `com/kiyori/app/startup/KiyoriMainSharedContentCoordinator.kt` | M-04D4 新建；唯一 pending→SharedFileHandler 生命周期绑定转交 owner，SHA/import/清理与 Activity 接线由 ARCH033 锁定 | M-06 files 迁移时删除 resource/SharedFileHandler 过渡依赖；不得创建第二 StateFlow/store |
| `com/kiyori/app/startup/KiyoriMainTaskVisibilityCoordinator.kt` | M-04D5 新建；无状态唯一 recent-task visibility 恢复 owner，SHA/import/API 判定与 Activity 两个调用点由 ARCH034 锁定 | M-07 收口 AI foreground-runtime bridge；不得持有 Activity/task 状态或吸收其他 lifecycle 职责 |
| `com/kiyori/app/startup/KiyoriMainOrientationCoordinator.kt` | M-04D6 新建；M-05B 已迁入 `KiyoriLogger`，唯一 orientation Compose state、纯 reducer 与确认对话框 presentation owner继续由 ARCH035/043 锁定 | 后续只迁移 `R` bridge；不得执行 `recreate()` 或吸收 Plugin Loading/其他 lifecycle 职责 |
| `com/kiyori/app/startup/KiyoriMainNotificationPermissionCoordinator.kt` | M-05D 已收敛为启动通知权限日志/Toast projection；保留原 FQCN、一参数构造、`checkAndRequest()`、6 条日志、2 个 Toast 与 MainActivity 早注册/单调用，ARCH036/043/045 锁定 | 保持 app projection；不得回流 system permission、resource ID、持久状态或其他页面权限 |
| `com/kiyori/app/startup/KiyoriMainStartupGateCoordinator.kt` | M-04D8 新建；M-05B 已迁入 `KiyoriLogger`，三态 resolver、`showPermissionGuide` UI 投影与启动门禁 presentation 继续由 ARCH037/043 锁定 | M-07 删除 preference/screen bridge；不得复制 SharedPreferences/DataStore/Flow 或吸收 lifecycle/plugin/content host 副作用 |
| `com/kiyori/app/startup/KiyoriMainContentHost.kt` | M-04D9 新建并封板；唯一 MainActivity content request projection、shared-content 交接、Plugin Loading CompositionLocal provider 与 `KiyoriApp` 参数/消费回调装配边界，由 ARCH020/ARCH030/ARCH033/ARCH038 共同锁定 | M-06/M-07 随 files、navigation 与 Operit UI contract 迁移删除过渡 import；不得持有第二 pending/plugin 状态、缓存、持久化或 Activity/runtime side effect |
| `com/kiyori/app/shell/KiyoriShellState.kt` | M-04B2 纯移动完成；唯一 state/back owner 与 Browser capability import 已锁定 | 保持在 `com.kiyori.app.shell` |
| `com/kiyori/app/shell/KiyoriAppShell.kt` | M-04B3 纯移动完成；后续 Shell 拆分与 M-05A1 theme owner 迁移后，规范化源码、9 个 Operit import、2 个 Kiyori design import 与唯一 root/test 接线由 ARCH024 精确锁定 | 保持在 `com.kiyori.app.shell`，随产品领域迁移删除过渡 import |
| `com/kiyori/app/shell/KiyoriAiDrawer.kt` | ARCH025 保护包、项目依赖、唯一 host/test；ARCH049 保护传入 registry、选择/dismiss 回调与可见期 Back | 保持在 `com.kiyori.app.shell`，按 C/D 计划收口 Operit integration |
| `com/kiyori/app/shell/KiyoriPrimaryNavigation.kt` | ARCH026 保护项目依赖、唯一声明与 host/test；ARCH049 保护 caller selection/click 和文件/设置分发 | 保持在 app shell，随 product-domain 迁移删除旧 page/chrome import |
| `com/kiyori/design/theme/KiyoriColorSchemes.kt` | M-05A1 新建并封板；唯一固定 application/Browser ColorScheme 与 bool resolver owner，SHA/声明/禁用依赖由 ARCH040 锁定 | 保持纯 design；不得读取 preference、持有状态或执行 Android/platform 副作用 |
| `com/kiyori/design/theme/KiyoriTheme.kt` | M-05A3 新建并封板；唯一纯 MaterialTheme/background 组合 owner，接收已解析 ColorScheme、Typography 与标准 `modifier` | 不读取 preference，不创建 Glass 状态，不执行 system-bar/window/lifecycle 副作用；源码 hash 与组合顺序由 ARCH042 锁定 |
| `com/kiyori/design/theme/KiyoriTypography.kt` | M-05A3 新建并封板；唯一固定零 tracking Typography 与纯 `applyFontFamilyToTypography` owner | 不读取文件、Context、UserPreferences 或 AppLogger；配置字体适配继续留在旧 Type owner |
| `com/kiyori/app/theme/KiyoriTheme.kt` | M-05A3 新建并封板；唯一应用偏好、全局字体和 Glass 状态 host，对外保持 `KiyoriTheme(content)` | 通过精确 ARCH004 例外读取现有 UserPreferences/Type/Glass owner；不拥有第二 ColorScheme、字体算法或 system-bar 实现 |
| `com/kiyori/platform/window/KiyoriApplicationSystemBars.kt` | M-05A3 新建并封板；唯一 Application edge-to-edge、status-bar 可见性与 navigation-bar contrast owner | 不读取 preference，不 import Operit，不吸收 Player fullscreen system-bar |
| `ui/theme/ThemeColorSchemeResolver.kt` | M-05A1 `SPLIT` 已完成；固定 Kiyori ColorScheme 已迁入 `com.kiyori.design.theme`，旧文件只保留 preference snapshot 到 dark/light 的 adapter | 不读取第二 preference、不拥有第二 ColorScheme；M-05A3 root theme host 已由 app owner 收口 |
| `ui/theme/KiyoriBrowserTheme.kt` | M-05A1 `MOVE-KIYORI` 已完成，旧路径删除 | `com.kiyori.design.theme.KiyoriBrowserTheme`；保持中性 chrome、parent Typography/Shapes |
| `ui/theme/KiyoriSettingsTheme.kt` | M-05A1 `MOVE-KIYORI` 已完成，旧路径删除 | `com.kiyori.design.theme.KiyoriSettingsTheme`；保持固定浅深页面/卡片/文字/分隔线与 CompositionLocal |
| `ui/theme/KiyoriSemanticTheme.kt` | M-05A2 `SPLIT` + `MOVE-KIYORI` 已完成；旧 89 行混合 owner 已删除 | 纯合同由 `com/kiyori/design/theme/KiyoriSemanticColors.kt` 唯一拥有，Compose 投影由 `com/kiyori/design/theme/KiyoriSemanticTheme.kt` 唯一拥有；当前 55 个生产与 4 个测试消费者、102 条 import 已精确迁移，不保留 facade/typealias/fallback |
| `ui/theme/Theme.kt` | M-05A3 `SPLIT` 已完成；旧 144 行混合 owner 已删除 | 职责已拆入 design/app/platform 三层，不保留 facade、typealias、fallback 或旧 `OperitTheme` |
| `ui/theme/Type.kt` | M-05A3 `SPLIT` 已完成 | 固定 Typography 与纯字体应用已迁入 `KiyoriTypography.kt`；配置字体、文件 I/O、UserPreferences 常量与日志适配保持旧 owner |
| `com/kiyori/platform/logging/KiyoriLogger.kt` | M-05B 新建并封板；唯一 executor、提前解析的内部 filesDir、root provider、内部/package log 文件引用与 `enableFileLogging` owner；进程 Context 继续只由 `ApplicationContextAccess` 持有 | 保持 system/file/ToolPkg 日志合同；platform 不导入 Operit，hash/状态/API 由 ARCH043 锁定 |
| `com/kiyori/platform/logging/KiyoriLogTextFormatter.kt` | M-05B 从旧 util formatter 迁移并封板；唯一 cause/stack/circular/truncation owner | `CrashReportStore` 直接复用；旧 formatter 路径删除，不复制实现 |
| `util/AppLogger.kt` | M-05B `ADAPTER` 已封板 | 旧 FQCN 兼容 facade，只保留常量、属性与 `@JvmStatic` 委派；禁止第二 executor/context/file state |
| `com/kiyori/platform/lifecycle/KiyoriActivityLifecycle.kt` | M-05C 新建；唯一 callback 注册、current Activity 弱引用、activity/started count 与 foreground facts owner，ARCH044 锁定 package/hash/零项目依赖/状态与 JVM tests | 保持纯 Android lifecycle facts；不得吸收 Operit plugin、AI、Player、窗口或 preference 副作用 |
| `core/application/OperitActivityLifecycleIntegration.kt` | M-05C 新建；唯一 keep-screen-on、plugin lifecycle、External Chat、microphone、PlayerCrash、VirtualDisplay 与 Shower 副作用 owner | 随 M-07 Operit integration 迁移审查；不得保存 Context 或 lifecycle facts |
| `core/application/ActivityLifecycleManager.kt` | M-05C `ADAPTER` 已实现；保留旧 FQCN、object、`ActivityLifecycleCallbacks`、4 个业务方法与 7 个 callback，并委派 platform/integration | 13 个旧 FQCN consumer 与完整 JVM ABI 由 ARCH044 锁定；禁止第二注册、状态或副作用实现 |
| `com/kiyori/platform/permission/KiyoriNotificationPermissionCapability.kt` | M-05D 新建；唯一 API 33 guard、system grant/rationale、RequestPermission launcher 与 action resolver owner，ARCH045 锁定零项目依赖/API/范围/test | 保持纯 Android capability；不得持有 Toast、资源、日志、持久状态或其他权限 |
| `com/kiyori/integration/operit/permission/OperitNotificationPermissionResources.kt` | M-05D 新建；唯一桥接现有 denied/rationale 两个 `R.string` ID，不复制文案 | 保持无状态 resource bridge；不得读取权限或吸收 app/platform 行为 |
| `data/preferences/AndroidPermissionPreferences.kt` | M-05D hash 证明未改，继续 `KEEP-OPERIT` / M-06 settings review | 持久设置/执行模式 owner，不是 platform permission fact，不得被新 capability 读取 |
| `com/kiyori/platform/storage/KiyoriPaths.kt` | M-05E 新建；唯一 public/internal/cache/files/backup 目录字面量、Environment、ensureDir、plugin ID 与 raw snapshot 排除 owner，ARCH046 锁定 hash/API/算法/消费者 | 保持稳定目录、大小写、层级和创建语义；不得 import 项目代码或吸收 Browser/Player/Backup 流程 |
| `com/kiyori/platform/storage/KiyoriBackupPaths.kt` | M-05E 新建；无状态 backup 领域命名投影，只委派 `KiyoriPaths` | 不声明目录字面量、排除集合、Environment 或创建逻辑 |
| `util/OperitPaths.kt`、`data/backup/OperitBackupDirs.kt` | M-05E `ADAPTER` 已实现；保留旧 FQCN、object、常量与完整方法 JVM ABI，分别委派 `KiyoriPaths` / `KiyoriBackupPaths` | 37 个旧 path consumer 保持上游局部性，旧 backup external consumer 为 0；禁止第二路径计算、Regex、hash、Environment 或 ensureDir |
| `com/kiyori/app/shell/KiyoriSoftwareHome.kt` | ARCH027 保护依赖、唯一声明与 host/test；ARCH049 保护壳回调、共享天气事实与 STARTED 刷新 | 保持在 app shell，随 weather、AI action、Browser-window 与 design owner 迁移删除过渡 import |
| `com/kiyori/app/shell/KiyoriBrowserSearch.kt` | M-04B7 提取完成；源码 SHA、8 个过渡 Operit import、页面/request/resolver 唯一 owner 与 App Shell/KiyoriApp/test 接线已锁定 | M-06 Browser domain migration 时移入 Browser presentation owner |
| `ui/main/shell/KiyoriShellPages.kt` | M-04B7 已删除 residual 混合页面文件 | 不恢复；Browser Search 由 `KiyoriBrowserSearch` 过渡 owner 持有 |
| `com/kiyori/integration/operit/navigation/AppRouteCatalog.kt` | M-04C 从 Operit UI 包移动完成；接收唯一 PackageManager 引用，源码 SHA 与 16 个 Operit import 已锁定 | 保持唯一 route catalog owner |
| `com/kiyori/integration/operit/navigation/OperitNavigationIntegration.kt` | M-04C 新建；唯一 navigation revision/listener/gateway lifecycle、catalog facade 与 route-root helper owner | M-07 随 Operit integration 封板删除临时过渡边 |
| `ui/main/navigation/*` | 逐文件分类 | Kiyori navigation 或 `integration.operit.navigation` |
| `ui/main/screens/OperitScreens.kt` | 保留 | Operit AI screen contract |

### Browser

| 当前文件组 | 目标 owner | 不变量 |
| --- | --- | --- |
| `core/browser/*` | Browser domain/presentation | 不创建第二 session |
| `core/tools/defaultTool/websession/browser/*` | Browser runtime/data 或 Operit adapter | 一个 WebView、一个 registry、一个 download owner |
| `core/tools/defaultTool/websession/userscript/*` | Browser userscript runtime/data | 一个 repository、一个 execution world owner |
| `ui/features/websession/browser/*` | Browser UI | 只投影 runtime，不建立事实状态 |
| Browser tests | 与实现同步移动 | 测试包名必须反映新 owner |

### Player

| 当前文件组 | 目标 owner | 不变量 |
| --- | --- | --- |
| `core/player/PlayerSession*` | Kiyori player domain | 单一 session 和 request |
| `core/player/runtime/*` | Kiyori runtime + stable IPC adapter | AIDL/native 名称先不变 |
| `ui/features/player/*` | Kiyori player UI | floating/fullscreen 只转移 Surface |
| `app/src/main/aidl/*player/runtime*` | `KEEP-CONTRACT` | package、parcelable 和 callback 顺序不变 |
| `PlayerRuntimeService` | `KEEP-CONTRACT` | `:player` 进程与 manifest 入口稳定 |

## 文件级审查规则

一个文件同时满足以下任一条件时必须进入 `SPLIT`：

- 同时 import Kiyori Shell 与 Operit AI route
- 同时读写两个领域的持久化状态
- 同时实现 UI、runtime 和 external Intent
- 同时被上游近期修改且由 Kiyori 变更过
- 同时拥有用户可见名称和兼容协议常量

`SPLIT` 完成前不进行目录重命名。拆分提交必须先建立行为等价测试，再移动各自实现。

## 完成判断

本矩阵只有在以下条件满足后才能从“准备”进入“执行中”：

- 所有 `REVIEW` 文件已经逐文件登记
- 所有 `KEEP-CONTRACT` 类型都有稳定入口说明
- 每个 `MOVE-KIYORI` 组都有 capability owner 和测试组
- 每个 `ADAPTER` 只有一个委派目标
- CI 能根据矩阵检查新文件是否越过边界
