---
status: completed
plan_version: 3
milestone: M-04
submilestone: M-04E completed
baseline: 176f803e683307aa8e182fb357f35f84755c5f8b + M-03 working tree
device_scope: excluded
last_reviewed: 2026-08-01
---

# M-04 根组合与 Shell 精确实施清单

## 当前结论

M-04 是 M-03 之后的根组合与宿主职责拆分，不是一次性把 `ui/main` 整体移动到
`com.kiyori`。M-04A1 已拆出宿主 CompositionLocal 合同，M-04A2 已把唯一根组合移动为
`com.kiyori.app.KiyoriApp`；M-04B1 已拆出 Operit navigation policy，M-04B2 已把
Browser exit presentation contract 与纯 Shell state/back owner 移入 Kiyori 包，
M-04B3 已把唯一 App Shell host 纯移动到 `com.kiyori.app.shell.KiyoriAppShell`，
M-04B4 已把唯一 Modal AI Drawer host 纯移动到
`com.kiyori.app.shell.KiyoriAiDrawer`，M-04B5 已把 primary destination visual、
root dispatch 与 bottom navigation 从混合页面文件提取到
`com.kiyori.app.shell.KiyoriPrimaryNavigation`，M-04B6 已把 Software Home 完整声明组
提取到 `com.kiyori.app.shell.KiyoriSoftwareHome`。旧 `KiyoriShellPages.kt` 现在只剩
Full-Screen Browser Search、request model 与 resolver；M-04B7 已将其提取到
`com.kiyori.app.shell.KiyoriBrowserSearch` 并删除旧路径。App Shell 仍连接 settings/files
页面、下载抽屉、系统窗口和历史外部入口，因此必须继续按独立声明组、Operit navigation
integration 和 MainActivity 内部 host 推进。M-04C 已把 route catalog、唯一
PackageManager-backed navigation revision、ToolPkg listener、gateway lifecycle 与
route-root helper 收口到 `com.kiyori.integration.operit.navigation`；当前进入 M-04D。
M-04D1 已把全部 pending external request state 收口到唯一
`com.kiyori.app.startup.KiyoriMainPendingRequests` 并完成封板。M-04D2 已把 Intent
payload/action 解码提取到 `com.kiyori.app.startup.KiyoriMainIntentDecoder` 并完成封板；
M-04D3 已把显示性能与刷新率配置提取到无状态
`com.kiyori.app.startup.KiyoriMainDisplayCoordinator` 并完成封板。M-04D4 已把 pending
shared-content 转交提取到 `com.kiyori.app.startup.KiyoriMainSharedContentCoordinator`
并完成封板。M-04D5 已把最近任务可见性恢复提取到无状态
`com.kiyori.app.startup.KiyoriMainTaskVisibilityCoordinator` 并完成封板。M-04D6 已把
方向状态、纯 reducer 与确认对话框提取到唯一
`com.kiyori.app.startup.KiyoriMainOrientationCoordinator` 并完成封板。M-04D7 已把启动
通知权限 launcher、纯决策与 request/result 处理提取到唯一
`com.kiyori.app.startup.KiyoriMainNotificationPermissionCoordinator` 并完成封板。
M-04D8 已把协议/权限级别/内容三态启动门禁提取到唯一
`com.kiyori.app.startup.KiyoriMainStartupGateCoordinator` 并完成封板；M-04D9 已完成
ARCH038、剩余 Kiyori 内容装配及完整封板验证。M-04E 已完成 ARCH039、精确 compatibility
ownership、到期例外清零、行为保持型 lint 收口与 M-04 总封板。当前进入 M-05
design/theme/platform。

## 2026-08-18 Stage 4 设置返回维护批准

设置返回专项在真实页面复测中确认：`KiyoriApp` 的外层 `BackHandler` 与
`KiyoriAppShell` 的 Shell `BackHandler` 只根据当前页面类型或设置 overlay 可见性判断，未按
`KiyoriSettingsPresentation` 划分返回宿主；同时 Operit 设置详情中的默认子导航没有继承活动
设置会话的 `RouteEntrySource.KIYORI_SETTINGS` 与 `sessionId`，且弹出任一同 session 子页后会
过早恢复 Shell 设置页面。结果是 Browser/AI 来源的 Shell 设置页与 Operit 设置深层页都可能被
底层 Browser/AI `BackHandler` 抢先消费，越过父级进入软件首页，或从深层子页直接跳到设置首页。

本次 Stage 4 维护继续保留唯一 `KiyoriShellState`、唯一 `AppRouterState`、唯一 Browser Host
和原 Shell/Operit package owner，只批准以下精确合同变化：

- `KiyoriSettingsNavigationState.kt` 定义 Shell 返回所有权纯函数；
- `KiyoriShellState.handleBack()` 与 `KiyoriAppShell` 共用该函数；
- `KiyoriAppShell` 在 Browser/AI 宿主之后、具体设置页面之前注册唯一 Shell 设置
  `BackHandler`；根 Shell `BackHandler` 在活动设置会话期间让位；
- `KiyoriApp` 在 AI Host 内注册 `OPERIT_ROUTE_DETAIL` 专用 `BackHandler`，其位置晚于
  Browser Host、早于具体 Operit 页面；App 根 `BackHandler` 在活动设置会话期间让位；
- `KiyoriApp.navigateTo()` 让活动 Operit 设置详情中的默认子导航继承同一设置 session；
- `KiyoriApp.performGoBack()` 只在离开该 session 的首个 Operit 分类条目时恢复 Shell 设置
  presentation；同 session 子页面继续按 Router 原顺序逐级 pop；
- 底部导航只在 `BOTTOM_NAVIGATION + PRIMARY_ROOT + HOME + SETTINGS_HOME` 的设置首页显示；
  Browser/AI 来源、分类页、子页、Operit 详情和 Browser workspace 均隐藏；
- `KiyoriApp` 的 Shell import 集合、`KiyoriAppShell` 的完整项目 import 集合、所有 package、
  route、状态 owner 和持久化合同均不增加或减少。

对应精确 SHA 更新：

| 合同 | 旧值 | 新值 |
| --- | --- | --- |
| ARCH020 `KiyoriApp.kt` LF-normalized SHA-256 | `BBCA5C235E381F5EE98CACE173FCCD9A121FB5612799AF00D978B3D872EA87F6` | `BD3DED71AFA01F15595B1CB05A803AF1B6E93D5A5552D462F2FA573AE37ECD67` |
| ARCH021 `KiyoriShellState.kt` LF-normalized SHA-256 | `665D491ED1EF59251A5CA25F0617BF48C1F3E3BEF05A527E5FAACDE392372A3C` | `412F5C0F9C5FA00BF0F031B87E2FFFCE69129670E101867FB5922B471B0DB712` |
| ARCH024 `KiyoriAppShell.kt` package-normalized SHA-256 | `E06BB0F22DB33000B7ADE38C49BA591A7B9365A6B168D7E137EA54B34FEBB591` | `4011FBE44CA6AA3B9B6C1B4498B7475AB53225947F85999A7D3A8088985868A6` |

这些 snapshot 只批准上述返回契约修复；后续其他根组合、Shell state 或 App Shell 变化仍必须形成
新的正式说明和精确验证，不能复用本次哈希更新。

## 2026-08-18 Stage 5 Browser 来源设置展示与 Back owner 维护批准

Stage 4 后，AI 左抽屉来源的设置返回已经正确；Browser 菜单来源仍会在 Operit 分类第一次系统
Back 时没有可见变化，第二次直接进入软件首页。“网页浏览器”页正常而其他页异常。目标设备证据
否定了最初“仅由 AI Host `zIndex` 导致”的结论：前景层级确实需要修正，但稳定触发两次返回的
直接原因是 Browser 动态创建后，其 `BackHandler` 比长期挂载的 Shell/Operit host 回调更新，
从而先消费隐藏网页历史。

本次 Stage 5 维护批准以下闭环变化：

- 非根 AI route 继续保持原实例和 Router 栈；AI Host 前景层级同时由路由深度与
  `KiyoriSettingsPresentation` 决定；
- `KiyoriAppShell` 把 Browser 系统 Back 所有权作为显式参数传给唯一 Browser Home；
- 设置首页、Shell 设置子页和 `OPERIT_ROUTE_DETAIL` 可见时，Browser 根及搜索、书签、历史
  子树共同让出 Back；`SUSPENDED_FOR_BROWSER_WORKSPACE` 明确仍归 Browser；
- `KiyoriApp` 把 Router pop 与离开设置 session 时的 Shell presentation 恢复封装为同一事务；
- AppContent 缓存的旧用户偏好页在退出转场中不再消费下一次 Back；
- 不创建第二 Router、Shell state、Browser Runtime 或 WebView，不重置 AI Router，不修改当前
  网页、网页历史、窗口、Browser return target 或设置 route stack。

对应架构 snapshot：

- ARCH020 `KiyoriApp.kt` LF-normalized SHA-256 从
  `BD3DED71AFA01F15595B1CB05A803AF1B6E93D5A5552D462F2FA573AE37ECD67` 更新为
  `2873212F7A1105F04B664995775E3731342407824F7158FC6ADD91F4255166B9`；
- ARCH024 `KiyoriAppShell.kt` package-normalized SHA-256 从
  `E42F9E81E50FA19E92C849D78FDAD00562DBD5AE3F42A970A5F1E4595DCE951A` 更新为
  `7D80D713C5D8A069EB58592939182E356ACAE2F876F812FA9161CFA415387039`。

这些 snapshot 只批准上述展示与 Back owner 修复；后续其他根组合或 App Shell 变化仍需新的正式
说明和精确验证。

本地封板证据：`KiyoriShellStateTest 67/67`、完整 App JVM
`230 suites / 1379 tests`、architecture `phase=m03`、formal readiness、Markdown 本地链接
`errors=0 / warnings=0` 与 `git diff --check` 均通过；规定 Debug 构建为
`232 actionable tasks: 23 executed, 209 up-to-date`，零失败。APK 为 `494168730` bytes，
SHA-256 `A8EE926FCD4C68B6B4B50DB689112936376ADFA53ED5577D8BD21174A0C155ED`，包元数据、唯一
launcher、arm64-only、Android Debug V2 单 signer 和 16 KB ZIP 对齐通过。目标设备复测仍是
`verification_pending`，不能由本地证据替代。

## 2026-08-18 Stage 6 首页手势、搜索 overlay 与 AI 横向内容维护批准

目标设备复现和 Compose Foundation `1.11.4` 源码核对确认：永久 AI 根虽然与原生
`HorizontalPager` 共用 `PagerState`，但旧普通 `scrollable` 直接复用默认 Pager fling 时没有
本次 down/up 位移元数据，释放结果会受零值或上一轮原生 Pager 手势影响。同时 Full-Screen Web
Search child 已使底栏隐藏，但 Shell overlay 可见性只读取 Settings navigation，导致搜索页没有
实际显示；AI 消息内宽表格、代码、公式和 WebView 预览也缺少对首页横滑的显式所有权。

本次 Stage 6 只批准以下闭环变化：

- 原生 Minus-One/Software Home 继续使用唯一 `HorizontalPager`、唯一 `PagerState` 和默认
  fling；永久 AI 根删除旧默认 fling 接线，改用 `KiyoriAiHomePagerGestureBridge`；
- bridge 为每次按下建立独立会话，按严格 `> 0.5`、`>= 400dp/s`、最多一页、LTR/RTL、边界和
  `Spring.StiffnessMediumLow` 合同吸附，不读取、反射或复制 Pager 私有状态；
- `shouldAcceptKiyoriHomePagerInput` 作为唯一 Shell 输入 gate，排除 child、Settings、
  Browser、非根 AI route 和所有共享 drawer；真实 page size 未建立或 AI 内容 owner 活跃时
  同样不启用 AI bridge；
- Full-Screen Web Search child 与 Settings overlay 使用独立可见性判定并共享原承载层；Back
  仍只关闭 child 并恢复软件首页与底栏；
- `WebSessionBrowserSearchScreen` 的系统 Back owner 改为必填宿主参数；Full-Screen Search
  作为当前可见 Shell child 传入 `true`，Browser 内搜索传入 retained Browser 子树的共享
  CompositionLocal owner，避免独立搜索宿主在组合时读取不存在的 Browser provider；
- AI 表格、关闭自动换行的 Canvas 代码、横向公式、Mermaid 与 HTML WebView 通过 Operit UI
  内部多 owner 状态声明当前横向交互，并与聊天历史快速滚动 owner 合并后沿既有
  `onGestureConsumed` 边界上报；
- 删除失效的 `currentDrag`、`verticalDrag`、`dragThreshold` 参数和旧 AI fling 双路径；不
  新建第二 PagerState、第二 AI Host、运行时开关、回退或持久化手势状态。

本阶段对应架构快照：

- ARCH020 `KiyoriApp.kt` LF-normalized SHA-256 保持
  `2873212F7A1105F04B664995775E3731342407824F7158FC6ADD91F4255166B9`；
- ARCH024 `KiyoriAppShell.kt` package-normalized SHA-256 从
  `7D80D713C5D8A069EB58592939182E356ACAE2F876F812FA9161CFA415387039` 更新为
  `6698094CB59519FB8124A67216B70DA5CFD8E7A18B92C92300794E3748E34391`。
- ARCH028 `KiyoriBrowserSearch.kt` LF-normalized SHA-256 从
  `C343F80E19F513039530C228D19A8FD82BA1715975EE504717E413DCB46F23D0` 更新为
  `89CB261ECC04BBE38CCB6EB133F3D1AA0CF5BCB40A3BC3259CAE26B17F315EEF`。

专项本地证据为 `KiyoriShellStateTest 68/68`、Pager policy `6/6`、内容 owner `2/2`，
Software Home Search `9/9`，合计 `85/85`；完整 App JVM 为
`239 suites / 1404 tests`，零 failure/error/skip；
AndroidTest Kotlin/Java 编译、architecture `phase=m03`、formal readiness 与
`git diff --check` 通过。规定 Debug 构建在全部源码、文档和架构快照修改后通过，唯一 launcher
与 player runtime packaging gate 均通过；APK 为 `467107608` bytes，SHA-256
`67F8F4D73367981591A2C0C73C25CB4F3B698C658238C682DA040E8E4533B16D`，包/版本/SDK、唯一
launcher、arm64-only、Android Debug V2 单 signer 和 16 KB ZIP 对齐通过。新增 Compose
Android 手势与 Full-Screen Search smoke test 未在设备执行；修复后目标设备手感、内容滚动、
IME、TalkBack 和系统边缘 Back 保持 `verification_pending`。

## 2026-08-18 Stage 7 Settings surface 转场宿主维护批准

Stage 4/5 已修复设置 route、presentation、Back owner 与 Router 恢复事务，Stage 6 又让
Full-Screen Search 和 Settings 使用独立可见性判定，但两者仍共享同一个
`AnimatedVisibility` 内容宿主。目标设备的反相四象限证明该共享宿主本身会产生两条视觉竞态：

- 底部入口从 Shell 设置详情返回时，Settings overlay 从可见变为不可见；退出内容读取已经恢复的
  `HOME` route，与 `KiyoriPrimaryRootPage` 同时绘制设置首页；
- Browser Menu/AI 抽屉来源从 Operit 设置详情返回时，Settings overlay 从不可见变为可见；
  已恢复的设置首页被当作新 surface 再次执行 `fadeIn + slideIn`。

本次 Stage 7 只批准以下闭环变化：

- 原 Shell child `AnimatedVisibility` 继续保留动画参数，但只承载
  `KiyoriShellState.child`，当前即 Full-Screen Search；
- Settings route 从 animated child host 中移出，使用同一 `zIndex(12f)` 的直接、不透明
  Settings surface；
- 底部设置首页仍只由 Primary Root 绘制，来源设置首页和 Shell 设置详情只由直接 Settings
  surface 绘制，Operit 设置详情只由 App Router 绘制；
- 主题边界从 child 专用命名改为 surface 通用命名，设置页主题选择规则保持不变；
- 新增纯判定 `shouldAnimateKiyoriShellChildOverlay()`，测试锁定底部/来源入口与
  Shell/Operit 详情的六个代表状态都不能进入 animated child host，同时保留原 Settings
  surface 四象限；
- 不修改 `KiyoriSettingsNavigationState`、`KiyoriShellState.handleBack()`、
  `popKiyoriRouterBackStack()`、Browser Back owner、AI Host z-index、Browser Runtime、
  WebSession、页面级 Back guard 或任何持久化状态。

本阶段对应架构快照：

- ARCH020 `KiyoriApp.kt` LF-normalized SHA-256 保持
  `2873212F7A1105F04B664995775E3731342407824F7158FC6ADD91F4255166B9`；
- ARCH024 `KiyoriAppShell.kt` package-normalized SHA-256 从
  `6698094CB59519FB8124A67216B70DA5CFD8E7A18B92C92300794E3748E34391` 更新为
  `C004F429D9F15D4F39CA9B66C7FE4DAF72152A37A819A508E366F67B11448EA8`。

本地封板证据：`KiyoriShellStateTest 69/69`、完整 App JVM
`239 suites / 1405 tests`、AndroidTest Kotlin/Java 编译、architecture `phase=m03`、
formal readiness 与 `git diff --check` 均通过；规定 Debug 构建为
`232 actionable tasks: 22 executed, 210 up-to-date`，零失败。APK 为 `467107608` bytes，
SHA-256 `BFA17A75523E74EF7C0A44651443D52D660D9EDB15EB458314A3317E76DBB0F6`，包/版本/SDK、
唯一 launcher、arm64-only、Android Debug V2 单 signer 和 16 KB ZIP 对齐通过。

本地自动验证与 Debug APK 只能证明代码、宿主合同和静态产物；三类入口、九个设置根、深层子页、
快速重复返回、浅深主题和系统/标题 Back 的目标设备视觉矩阵仍保持
`verification_pending`。

因此 M-04 按可编译的子里程碑串行完成：

1. **M-04A1：宿主 CompositionLocal 合同拆分（已完成）**
   - 5 个宿主通道移动到 Operit UI navigation contract 文件。
   - Operit feature screen 只依赖该合同，不依赖根 Composable。
   - 不改 route、Pager、Shell state、Back、窗口 inset 或 ToolPkg 行为。
2. **M-04A2：根 Composable 改名与移入 `com.kiyori.app`（已完成）**
   - `OperitApp -> KiyoriApp`。
   - 保留同一状态 owner、route state、CompositionLocal provider 顺序和请求消费 ID。
   - 对原同包符号补显式 import；不建立第二根组合。
3. **M-04B：Shell core owner 迁移（已完成）**
   - M-04B1 已把 `KiyoriShellState` 中的 AI route/stack policy 移入 Operit integration。
   - M-04B2 已拆 Browser presentation contract，并移动纯 Shell state/back owner。
   - M-04B3 已移动 `KiyoriAppShell` 宿主。
   - M-04B4 已移动 `KiyoriAiDrawer` 宿主。
   - M-04B5 已提取 primary destination presentation 声明组。
   - M-04B6 已提取 Software Home 完整声明组。
   - M-04B7 已提取 residual Browser Search 声明组并删除旧混合页面文件。
4. **M-04C：Operit navigation integration 收口（已完成）**
   - `AppRouteCatalog`、ToolPkg route discovery、PackageManager listener 和根组合装配进入
     `com.kiyori.integration.operit.navigation`。
   - `AppNavigationModels` 中被 JsEngine/Operit screens 使用的稳定 route/gateway contract
     暂留原包，避免 Operit AI 反向依赖 Kiyori app。
5. **M-04D：MainActivity 内部 host 提取（已完成）**
   - Manifest 现有 `.ui.main.MainActivity` 作为稳定兼容入口保持不变。
   - M-04D1 已提取唯一 pending-request owner，不复制请求状态或移动平台副作用。
   - M-04D2 已提取无副作用 Intent decoder，Activity 仍执行 Android/runtime side effect。
   - 启动、Intent、权限、插件加载、共享文件、显示策略和 player handoff 分别提取到
     `com.kiyori.app.startup` 的单一 coordinator/host。
   - 不引入第二 Activity、第二 startup owner 或并行 pending-request store。
6. **M-04E：清理与封板（已完成）**
   - 删除已经到期的 MainActivity ARCH001 精确 exception，不延长到期时间。
   - 新增精确 ownership 优先级：精确文件 record 覆盖宽泛目录 owner；宽泛 glob
     之间的重叠仍必须失败。
   - 用 ARCH039 锁定稳定 package/FQCN/Manifest launcher、精确 imports、
     `com.kiyori.feature` 零依赖、唯一 content host 与旧例外清零。
   - 只清理 M-04 owner 内可证明行为不变的 lint，再运行完整 M-04 封板验证。
   - ARCH019 至 ARCH039 与后续 feature tests 共同锁定旧路径、唯一 provider、route ID、
     ToolPkg registration order、Back 优先级、Pager page order 和 MainActivity contract。

## M-04E 精确 ownership 与总封板设计

### 已验证事实

- 当前全部 Kiyori ARCH004 exception 仍绑定 M-05/M-06/M-07 的真实过渡 import，不能在
  M-04E 提前删除。
- 唯一到期的 M-04 exception 是
  `app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt` 的 ARCH001；
  到期里程碑为 `M-04D-main-activity-host`。
- M-04D9 已提取唯一 `KiyoriMainContentHost`，但 MainActivity 仍是 Manifest
  MAIN/LAUNCHER、lifecycle 与 Android/runtime side-effect 的稳定兼容入口。移动或复制
  Activity 会扩大稳定组件和 userspace 风险，不属于 M-04E。
- 直接删除旧 exception 会真实产生 14 条 ARCH001；新增精确 ownership record 而 checker
  仍把所有 glob 命中视为 owner，则会与 `operit-ui/**` 产生 multiple-owner。缺失点是
  ownership specificity 语义，不是新的例外。

### E1：ARCH039 与精确兼容入口

新增唯一 record：

```toml
[[ownership]]
id = "operit-main-activity-compatibility"
path = "app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt"
owner = "kiyori-app-entry-compatibility"
sync_zone = "D"
phase = "m04e"
allowed_import_roots = [
  "com.ai.assistance.operit",
  "com.kiyori.app",
  "com.kiyori.platform",
]
forbidden_import_roots = [
  "com.kiyori.feature",
]
```

checker 选择规则：

1. 精确 path 是不含 `*`、`?`、`[`、`]` 的完整受管源码路径。
2. 一个文件命中精确 record 时，只使用该 record 做 owner 计数与依赖检查。
3. 没有精确 record 时，沿用全部 glob 匹配；多个宽泛 record 命中继续报
   `ARCH013 source file has multiple owners`。
4. 重复 id、重复 path、无文件 record、schema/allowed/forbidden 校验保持不变。

ARCH039 使用
`config/architecture/m04e-main-activity-project-imports.txt` 锁定 MainActivity 当前项目
imports；不锁定整个 Activity 源码 SHA，因为 M-05/M-06/M-07 会在保持稳定入口的同时
继续迁移内部职责。ARCH039 必须直接验证 record 字段、旧 exception 缺失、source
package、Manifest MAIN/LAUNCHER、import snapshot、零 `com.kiyori.feature` import、
唯一 `KiyoriMainContentHost` import/call，以及直接 KiyoriApp/provider assembly 为 0。

### E2：M-04 owner lint 收口

E2 与 E1 分开验证，只处理下列可由现有 API guard、Compose primitive state 或等价
resource 读取证明行为不变的问题：

- Main display coordinator 的 API annotation 与 minSdk 26 后冗余 SDK 分支；
- Main Intent decoder 的 API 33 typed parcelable getter 边界；
- Main notification permission coordinator 的 API 33 permission 常量边界；
- Kiyori root 与 pending-request owner 的 primitive Long state；
- Kiyori root 中可由 Compose resource API 保持同一资源值的读取。

`MainActivity` 的 `AppBundleLocaleChanges` 以及其他领域的 strict lint 债务不在该切片中
修改。每个受影响源码的 LF-normalized SHA snapshot 必须同步更新；lint 验收使用临时
current result 与既有 baseline 的结构化交集，不修改 baseline 来吸收 current-only
问题。

## M-04A1 精确合同

### 唯一宿主合同文件

```text
app/src/main/java/com/ai/assistance/operit/ui/main/navigation/
    OperitHostCompositionLocals.kt
```

package：

```text
com.ai.assistance.operit.ui.main.navigation
```

唯一声明：

```text
LocalTopBarActions
LocalOpenBrowser
TopBarTitleContent
LocalTopBarTitleContent
LocalAppNavigationModel
```

这些声明属于 Operit UI host contract，而不是 `com.kiyori.app` 实现。根组合只提供
回调和当前模型；AIChat、TokenConfig、Market、ToolPkg Compose DSL 和 Toolbox 仍通过
同一 CompositionLocal 读取。

### 允许的 import 变化

下列 8 个实现文件只把旧 import：

```text
com.ai.assistance.operit.ui.main.LocalTopBarActions
com.ai.assistance.operit.ui.main.LocalOpenBrowser
com.ai.assistance.operit.ui.main.TopBarTitleContent
com.ai.assistance.operit.ui.main.LocalTopBarTitleContent
com.ai.assistance.operit.ui.main.LocalAppNavigationModel
```

改为 `com.ai.assistance.operit.ui.main.navigation` 下的对应合同。声明、调用顺序、默认
值、CompositionLocal provider 顺序和 UI 行为不变。

## M-04A2 根组合边界

目标：

```text
app/src/main/java/com/ai/assistance/operit/ui/main/OperitApp.kt
    -> app/src/main/java/com/kiyori/app/KiyoriApp.kt
OperitApp
    -> KiyoriApp
```

必须保持：

- `AppRouterState` 唯一实例和 back stack；
- `KiyoriShellState` 唯一 `rememberSaveable` owner；
- `savedAiPrimaryStacks`、route request ID、browser request ID 和 Shell request ID；
- `AppRouterGateway` / `AppRouteDiscoveryGateway` 的安装与清理时机；
- `LocalAppNavigationModel`、`LocalRouteBackGuardRegistry`、top-bar 回调和 browser 回调
  的 provider 顺序；
- AI Home 的稳定挂载、AI drawer route root、ToolPkg `keepAlive` / `stableScreenKey`；
- Browser Runtime、`BrowserPresentationCoordinator`、`WebSessionHistoryStore` 和
  `BrowserDownloadManager` 的唯一 owner。

M-04A2 允许一条由编译证据驱动的变化：旧 `ui.main` 同包可见的
`PendingAiHomeActionHandler` / `AiHomeQuickAction` 必须变成显式 import。任何其他源码
差异都必须进入新的子里程碑清单，不能混入根组合移动。

稳定 Manifest 入口 `MainActivity` 在 M-04D 前是唯一允许 import
`com.kiyori.app.KiyoriApp` 的 Operit 路径。该单文件 `ARCH001` exception 与
`ARCH020` 的精确 import/调用计数共同锁定，M-04D 提取内部 app host 后必须删除。

## M-04B Shell core 目标

`com.kiyori.app.shell` 最终只拥有 Shell 编排和纯 Shell state；Browser/player/settings/
files 的具体页面按后续领域迁移。Shell 不实现 Browser Runtime、播放器状态、下载任务库、
数据库、AI tool 或 ToolPkg registry。

首批拆分原则：

- state transition 保持现有 `KiyoriShellState` 函数和测试；
- [DONE] AI route helper 进入 Operit navigation integration，Shell state 的 Operit import
  已降为 0；
- [DONE] Browser exit presentation 已进入跨根 capability contract，Operit Browser screen
  不反向依赖 `com.kiyori.app.shell`；
- [DONE] 纯 Shell state/back owner 已移动到 `com.kiyori.app.shell`，旧路径已删除；
- [DONE] `KiyoriAppShell` 使用同一状态 owner、Pager、Back 和 drawer 接线纯移动到
  `com.kiyori.app.shell`；
- [DONE] `KiyoriAiDrawer` 使用同一入口、动画、fold/inset、状态投影和导航回调纯移动到
  `com.kiyori.app.shell`；
- [DONE] primary destination visual、root dispatch、底部导航与三条动画策略已提取到
  `com.kiyori.app.shell`；
- [DONE] Software Home 页面、策略、固定视觉参数与全部 helper 已提取到
  `com.kiyori.app.shell.KiyoriSoftwareHome`；
- [DONE] residual Full-Screen Browser Search 页面、request model 与 resolver 已提取到
  `com.kiyori.app.shell.KiyoriBrowserSearch`，旧 `KiyoriShellPages.kt` 已删除；
- [DONE] route catalog、唯一 PackageManager-backed navigation revision、ToolPkg listener、
  gateway lifecycle 与 route-root helper 已收口到 `com.kiyori.integration.operit.navigation`；
- [DONE M-04D1] 提取唯一 pending-request owner，不改稳定 Activity 入口、Intent 顺序或副作用；
- [DONE M-04D2] 提取纯 Intent decoder，不建立第二 request store 或移动 host side effect；
- [DONE M-04D3] 提取无状态 display-performance coordinator；
- [DONE M-04D4] 提取 lifecycle-bound shared-content transfer coordinator；
- [DONE M-04D5] 提取无状态 recent-task visibility coordinator；
- [DONE M-04D6] 提取唯一 orientation state/reducer/dialog owner；
- [DONE M-04D7] 提取 startup notification permission owner；
- [DONE M-04D8] 提取权限级别与协议接受后的三态启动门禁；
- [DONE M-04D9] 提取一次性 content request projection、shared-content 交接、
  `LocalPluginLoadingState` provider 与唯一 `KiyoriApp` 挂载；
- `KiyoriAppShell` 继续使用同一 `HorizontalPager`、唯一 `PagerState`、同一产品吸附合同、
  `BackHandler`、window inset 和 drawer surface；AI 永久覆盖层使用公开 API bridge，
  不读取或复制 Pager 私有手势状态；
- 不恢复永久 tablet sidebar，不引入旧 PhoneLayout 的 page transform 或透明 scrim。

## M-04C navigation integration 目标

`com.kiyori.integration.operit.navigation` 是 app 与 Operit route/ToolPkg 的唯一装配边界：

- 它可以依赖 `com.ai.assistance.operit`、`com.kiyori.capability`、`com.kiyori.platform`；
- 它可以创建 `AppRouteCatalog` 的唯一实例和 PackageManager listener；
- 它不得持有第二份 route state、ToolPkg registry、Browser Runtime、download store 或
  player state；
- `AppRouterGateway` 与 `AppRouteDiscoveryGateway` 仍只有一个进程内 owner；
- `com.ai.assistance.operit` 不得依赖 `com.kiyori.app`、`com.kiyori.feature` 或具体
  Kiyori Composable。

## M-04D MainActivity 目标

Manifest 入口、action、extra、URI、process、exported 状态和 `MainActivity` 对外常量保持
稳定。提取只改变内部调用位置：

```text
Intent -> MainActivity coordinator -> Kiyori app host
Application initialization -> MainApplicationInitialization
setContent -> one KiyoriApp host
```

以下状态不能复制：

- pending shared files/text；
- pending browser URL/request ID；
- pending GitHub OAuth URI；
- pending shortcut/route/Shell destination request；
- `PluginLoadingState`；
- `AgreementPreferences` / permission state；
- player restart generation。

### M-04D1 pending-request 唯一 owner

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainPendingRequests.kt
```

`MainActivity` 只持有：

```kotlin
private val pendingRequests = KiyoriMainPendingRequests()
```

owner 接收已经由 Activity 解析完成的值和由 Activity 生成的 request ID，只负责：

- 记录 shared files/text、Browser URL、GitHub OAuth URI、shortcut、route 和 Shell destination
- shortcut 记录时同步 current main navigation
- OAuth URI 的一次 take-and-clear
- shared-files 成功时同时清除 files/text，失败时只清 files
- shortcut、route、Browser 和 Shell request 只在 handled ID 精确匹配时清除

owner 明确不依赖 Intent，不生成时间戳，不调用下载、OAuth、分享、Toast、日志或 lifecycle。
这样消除了 Activity 内 13 个分散事实字段，同时没有创建第二 store 或改变事件顺序。

### M-04D2 side-effect-free Intent decoder

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainIntentDecoder.kt
```

该文件唯一拥有：

- `KiyoriMainIntentContract` 的 10 个 action/extra 字面值
- `KiyoriMainIntentCommand` 密封命令
- `KiyoriMainIntentDecoding` 的 command 与 pending-share 处理信号
- `decodeKiyoriMainIntent`
- Shell destination 与 download task ID 两条纯 resolver

`MainActivity` companion 的原 10 个 `const val` 继续存在并桥接该合同，因此调用方字段和
字面值不变。decoder 按原优先级读取 restart、download、Shell、settings、widget route、
GitHub OAuth、VIEW、SEND 与 SEND_MULTIPLE，并保留 API 33 前后 parcelable 读取路径。
它不修改 `intent.action`，不生成 request ID，不执行 Player、download、OAuth、share、
Toast、日志或 lifecycle；这些动作继续由 Activity 在原分支和原顺序执行。

### M-04D3 stateless display coordinator

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainDisplayCoordinator.kt
```

该对象不保存 Activity 或显示状态，每次只操作传入 Activity 的当前 Window/Display：

- API 31+ 请求 sustained performance，保留原异常日志
- API 30+ 只在刷新率严格高于 60 Hz 时选择最高 display mode ID
- API 23–29 只在刷新率严格高于 60 Hz 时写 preferred refresh rate
- 所有 API 级别继续设置 hardware-acceleration window flags

两条纯函数锁定 60 Hz 基线、最高值和同最高刷新率时保留首个 mode 的既有循环语义。
MainActivity 只保留一次 `KiyoriMainDisplayCoordinator.configure(this)`。

### M-04D4 lifecycle-bound shared-content transfer

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainSharedContentCoordinator.kt
```

该 coordinator 持有当前 Activity 与唯一 `KiyoriMainPendingRequests` 引用，只负责把尚未
转交的内容送入原 `SharedFileHandler`：

- pending files 存在时，text 等待与 files 一并转交
- standalone text trim 后为空则不转交
- files 转交使用 Activity lifecycleScope，保持原协程时机
- 成功清 files/text；失败记录日志、显示原 Toast、只清 files，随后 text 独立处理

`SharedFileHandler` 的三个 StateFlow 和 AIChat clear callback 均未移动或复制。MainActivity
只保留一个 lazy coordinator 和 files/text 各两个调用点。

### M-04D5 stateless recent-task visibility restore

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainTaskVisibilityCoordinator.kt
```

该 coordinator 不保存 Activity、task 或服务状态，只执行原有最近任务可见性恢复：

- 保留 API 21+ 判定
- 保留 `AIForegroundService.isRunning` 为 true 时不修改 task 的语义
- 读取当前 ActivityManager appTasks，并逐项执行 `setExcludeFromRecents(false)`
- 保留单 task 失败与整体查询失败的两层 AppLogger 错误记录

MainActivity 不再直接依赖 `ActivityManager`、`AIForegroundService` 或
`setExcludeFromRecents`，只在主初始化完成后与 `onNewIntent` 入口保留原两个 coordinator
调用时机。

### M-04D6 orientation state and dialog presentation

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainOrientationCoordinator.kt
```

该文件包含四层合同：

- `KiyoriMainOrientationState`：last orientation 与 dialog visibility 的不可变快照
- `resolveKiyoriMainOrientationChange`：纯状态转换
- `KiyoriMainOrientationCoordinator`：唯一 `mutableStateOf` owner
- `KiyoriMainOrientationDialog`：唯一确认对话框 presentation

当前实际行为由测试锁定：orientation 不变时返回同一 state；发生变化时记录新值并显示
对话框；对话框已显示时再次发生方向变化，仍保持显示并更新 orientation。现有“转回去”
注释所描述的隐藏分支按旧赋值顺序不可达，本里程碑只迁移实际行为，不夹带行为修复。

MainActivity 只保留：

- onCreate 初始化当前 orientation
- `onConfigurationChanged` 中先隐藏 Plugin Loading，再分发新 orientation
- dialog confirm/dismiss 各调用一次 coordinator dismiss
- confirm 后唯一执行 `recreate()`

application edge-to-edge/system-bar owner 实际位于 Theme，Player 全屏 owner 位于
PlayerActivity；二者不在本切片迁移。

### M-04D7 startup notification permission request

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainNotificationPermissionCoordinator.kt
```

该文件包含：

- `KiyoriMainNotificationPermissionAction`：not-required、already-granted、
  show-rationale-and-request、request 四种动作
- `resolveKiyoriMainNotificationPermissionAction`：API/授权/rationale 纯决策
- `KiyoriMainNotificationPermissionCoordinator`：唯一 launcher/request/result owner

coordinator 作为 MainActivity 的非 lazy 直接字段构造，保持 `registerForActivityResult`
在 onCreate 前完成。Android permission 仍由系统唯一持有；coordinator 每次检查都读取
当前 SDK、授权与 rationale 状态，不保存第二份事实。原 granted/denied/rationale/direct
request 日志与两条 Toast 资源保持。

MainActivity 只在 `performInitialChecks` 原位置调用一次 `checkAndRequest()`；AI Chat、
Browser download、Userscript 与其他 feature 自己的权限请求不在本切片移动。

### M-04D8 agreement/permission-level startup gate

唯一实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainStartupGateCoordinator.kt
```

该文件只负责：

- `KiyoriMainStartupDestination`：`AGREEMENT`、`PERMISSION_GUIDE`、`CONTENT` 三态；
- `resolveKiyoriMainStartupDestination`：协议优先、权限引导次之、内容最后的纯决策；
- `KiyoriMainStartupGateCoordinator`：唯一 `showPermissionGuide` Compose UI 投影；
- `KiyoriMainStartupGate`：唯一 MainActivity 启动门禁页面分发。

`AgreementPreferences` 继续唯一持有 `agreement_preferences`、接受版本与
`agreementAcceptedFlow`；`AndroidPermissionPreferences` 继续唯一持有
`android_permission_preferences` DataStore 与 permission-level Flow。coordinator 每次
刷新都调用既有 `getPreferredPermissionLevel()`，并复用既有协议读写方法，不保存第二份
持久事实、Flow 或缓存。

必须保持的 Activity 顺序：

1. 初始检查：notification permission -> permission-level refresh -> startup chat ->
   agreement/permission 均通过时插件加载；
2. 协议接受：记录当前协议 -> Activity lifecycle coroutine -> `delay(300)` ->
   permission-level refresh -> 条件插件加载 -> `setAppContent()`；
3. 权限引导完成：清 UI 投影 -> 插件加载 -> `setAppContent()`。

`delay(300)`、`startPluginLoading()`、PluginLoadingState、lifecycle、`setContent()`、
`setAppContent()` 与 KiyoriApp 内容继续由 Activity 持有。AgreementScreen 的 5 秒按钮
计时、PermissionGuideScreen 的 500 ms 完成延迟、ViewModel、页面、Android 权限请求与
permission-level 持久化均不在本切片修改。

### M-04D9 Kiyori content host

目标实现：

```text
app/src/main/java/com/kiyori/app/startup/KiyoriMainContentHost.kt
```

该文件只负责三层无持久状态装配：

- `KiyoriMainContentRequestProjection`：当前一次 composition 使用的不可持久化参数投影；
- `projectKiyoriMainContentRequests`：从唯一 `KiyoriMainPendingRequests` 读取 shortcut、
  route、Browser、Shell request/ID、route args 与 current navigation；
- `KiyoriMainContentHost`：按现有顺序转交 pending shared content，提供既有
  `LocalPluginLoadingState`，并挂载唯一 `KiyoriApp`。

`initialNavItem` 继续使用：

```kotlin
pendingRequests.shortcutNavItem ?: pendingRequests.currentMainNavItem
```

必须保持的 content 顺序：

1. `sharedContentCoordinator.processPendingSharedFiles()`；
2. `sharedContentCoordinator.processPendingSharedText()`；
3. 创建一次 request projection；
4. `CompositionLocalProvider(LocalPluginLoadingState provides pluginLoadingState)`；
5. 挂载唯一 `KiyoriApp`；
6. shortcut、route、Browser、Shell handled ID 和 current navigation callback 继续委托给
   `KiyoriMainPendingRequests` 的原消费 API。

投影不是状态 owner、缓存、store 或 repository，不得新增 `mutableStateOf`、StateFlow、
remember、SharedPreferences、DataStore、singleton 或第二 request 清理逻辑。

MainActivity 只把 startup gate 的 content lambda 替换为一次
`KiyoriMainContentHost(...)` 调用。Activity 仍持有唯一 `PluginLoadingState`、`setContent`、
主题、startup gate 完成回调、插件启动时序、方向对话框、lifecycle 和全部 Android/runtime
side effect；`onNewIntent` 中 shared-files/text 的原转交调用各保留一次。

M-04D9 不修改 `KiyoriApp` 内部导航、Pager、Browser/Player owner、request ID 生成/匹配、
Intent、OAuth、下载、持久化、Manifest、namespace、native、terminal 或用户可见行为。

当前实现与定向验证证据：

- 已新增唯一 `KiyoriMainContentRequestProjection`、`projectKiyoriMainContentRequests`
  与 `KiyoriMainContentHost`，LF-normalized SHA-256 为
  `E80C04A5A129C44E83BB34B8F3B2F9D60E691894F44CDD8C676ECDA56B61E12C`；
- host 的项目 import 精确为 `NavItem`、`LocalPluginLoadingState`、
  `PluginLoadingState`、`KiyoriApp` 与 `KiyoriShellExternalDestination`；
- MainActivity 已移除直接 `KiyoriApp`、`CompositionLocalProvider` 和
  `LocalPluginLoadingState` 装配，只保留一次 `KiyoriMainContentHost` 挂载；
- ARCH038 真实仓库门禁通过，正反向 fixture `2/2` 通过；
- `KiyoriMainContentHostTest` 新增 3 条 projection 合同测试并通过；
- ARCH019 至 ARCH038、完整 Python/JVM、formal readiness、lint baseline 结构化交集、
  working-tree Markdown、diff、Debug APK 与制品审计均已完成；严格 lint 的既有
  current-only 债务未写入 baseline，转入 M-04E/最终质量收口。

## ARCH019

M-04A1 gate 必须检查：

1. 合同文件 package 正确；
2. 5 个声明恰好各有一个 owner；
3. 根 Composable 不再声明 `compositionLocalOf`；
4. 旧 `com.ai.assistance.operit.ui.main.*` CompositionLocal import 为 0；
5. M-04 host contract 文件未被 `com.kiyori.app` 反向替代；
6. architecture、Kotlin compile 和定向 host tests 通过。

ARCH020 已锁定 M-04A2 的 root move。ARCH021 锁定 Operit navigation policy，
ARCH022 锁定 Browser exit presentation capability，ARCH023 锁定纯 Shell state owner、
KiyoriApp 精确接线和当前两个仍位于 Operit 路径的文件级过渡桥。ARCH024 锁定 App Shell
host 的规范化源码、当前 13 个精确 Operit import、唯一 helper owner、root 与测试接线。
ARCH025 锁定 AI Drawer 的规范化源码、13 个精确 Operit import、唯一 owner、App Shell
挂载与 tone 测试接线。ARCH026 锁定 primary destination presentation 源码、10 个精确
Operit import、唯一声明组、App Shell 与测试接线。ARCH027 锁定 Software Home 源码、
9 个精确 Operit import、页面/策略/参数/helper 的完整唯一声明组、App Shell 唯一挂载
和 27 个测试 import。ARCH023 的 Operit-to-Shell-state 过渡桥现在只剩 `MainActivity`。
ARCH028 锁定 Browser Search 源码、8 个精确 Operit import、页面/request/resolver 唯一
声明、App Shell 唯一挂载、KiyoriApp 与测试接线，并删除旧 `KiyoriShellPages.kt`。
ARCH029 锁定新 route catalog/runtime 源码、16/12 个精确 Operit import、唯一 assembly
owner、PackageManager/listener/gateway lifecycle 与 KiyoriApp/test 接线。M-04B 页面拆分和
M-04C navigation integration 已完成。ARCH030 锁定 M-04D1 pending-request owner 的
源码 SHA、两个精确项目 import、唯一 owner、MainActivity 单一持有字段、旧字段清除、
16 条状态 API 接线、禁止的平台副作用与合同测试。ARCH031 锁定 M-04D2 decoder 的
源码 SHA、3 个精确项目 import、唯一 symbol、payload getter、10 个稳定常量桥接、
MainActivity 单一 decode call、禁止的 host side effect 与合同测试。后续切片不能扩大
pending owner 或 decoder 职责。ARCH032 锁定 M-04D3 coordinator 的源码 SHA、唯一
AppLogger import、纯策略 owner、Android API 计数、MainActivity 唯一调用和旧 helper
缺失。ARCH033 锁定 M-04D4 coordinator 的源码 SHA、3 个精确项目 import、唯一 transfer
owner、SharedFileHandler/pending clear 计数、Activity lazy owner 与四个调用点；ARCH030
同时在 Activity 与该 coordinator 的精确组合边界中锁定 pending API。ARCH034 锁定
M-04D5 coordinator 的源码 SHA、AIForegroundService/AppLogger 两个精确项目 import、
纯判定函数、无状态唯一 owner、平台 API 精确计数、MainActivity 原两个调用时机和旧
helper/API 缺失。ARCH035 锁定 M-04D6 coordinator 的源码 SHA、R/AppLogger 两个精确
项目 import、orientation state/reducer/Compose owner/dialog 的唯一声明、资源/API
计数、MainActivity 初始化与 configuration/dialog 接线、Plugin Loading hide 顺序、
单一 `recreate()` 和旧 state/presentation 缺失。ARCH036 锁定 M-04D7 coordinator 的
源码 SHA、R/AppLogger 两个精确项目 import、action/resolver/launcher 的唯一 owner、
permission API/资源/日志计数、MainActivity 非 lazy 早期字段注册、单一检查调用和旧
launcher/helper/import 缺失。ARCH037 锁定 M-04D8 coordinator 的源码 SHA、6 个精确
项目 import、三态 resolver、唯一 `showPermissionGuide` UI 投影与启动门禁 presentation，
同时锁定两类既有持久 owner 的方法引用、MainActivity 单一 coordinator 初始化、初始检查
和两条完成回调顺序，以及旧字段/import/页面分发缺失；coordinator 不得吸收 lifecycle、
plugin loading、content host 或任何第二持久状态。ARCH038 锁定 M-04D9 content host 的
源码 SHA、精确项目 import、唯一 request projection/host、shared-content 转交和 provider/
KiyoriApp 顺序、全部 request 参数与消费回调、MainActivity 单一挂载和旧直接装配缺失；
host 不得复制 pending/plugin 状态或吸收 Activity/runtime side effect。

## 当前 M-04A1 验收

- [DONE] 影响清单、状态 owner 和依赖方向确认。
- [DONE] ARCH019 失败测试与通过测试。
- [DONE] 5 个 CompositionLocal 唯一声明文件。
- [DONE] 8 个消费者 import 更新。
- [DONE] `:app:compileDebugKotlin`。
- [DONE] 定向 host JVM 与完整 JVM。
- [DONE] `:app:assembleDebug` 和 APK 审计。

### M-04A1 完成证据

- ARCH019 architecture gate：PASS，`phase=m03`。
- Python：111 tests，零失败。
- Kotlin compile：PASS。
- 定向 host JVM：PASS。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness：PASS。
- Markdown local links：0 errors。
- `:app:assembleDebug`：PASS，233 tasks，24 executed、209 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 大小：469777645 bytes
  - SHA-256：`66908B74DBA4537F137CC7C69FA71B3AB5682CCF31E502DE0D22EEFD708C333C`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries
  - APK Signature Scheme v2：true
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；terminal 未改变；device/ADB 仍为 `verification_pending`。

## M-04A2 验收

- [DONE] 唯一根组合移动到 `app/src/main/java/com/kiyori/app/KiyoriApp.kt`。
- [DONE] `OperitApp -> KiyoriApp`，旧运行时符号计数为 0。
- [DONE] 只补充 `AiHomeQuickAction` 与 `PendingAiHomeActionHandler` 两个原同包显式 import。
- [DONE] M-04A2 完成时 `MainActivity` 只 import 并调用一个 `KiyoriApp`；M-04D9 已把该
  唯一装配点迁入 `KiyoriMainContentHost`，Activity 只挂载一次 content host。
- [DONE] 根组合源码 SHA 与 53 个 Operit import 由 ARCH020 精确锁定。
- [DONE] `KiyoriApplication` 与 `KiyoriApp` 的 app -> Operit 过渡依赖分别由文件精确
  ARCH004 exception 管理；`MainActivity` 的兼容 host 边由单文件 ARCH001 exception
  管理。
- [DONE] Kotlin compile、定向 JVM、完整 JVM、Python、architecture、formal readiness、
  Debug APK 和制品审计通过。

### M-04A2 完成证据

- ARCH020 architecture gate：PASS，`phase=m03`。
- ARCH020 Python gate tests：47 tests，零失败。
- 完整 Python：113 tests，零失败。
- 根组合 LF-normalized SHA-256：
  `0E7D6870E2CB451FEE9998E264E555CA35381F51594B1E72796FEE7C50909F73`。
- 过渡 Operit imports：53，重复项 0。
- 旧 `OperitApp` runtime symbol：0。
- Kotlin compile：PASS；没有新增编译错误。
- 定向 JVM：`KiyoriShellStateTest`、`MainActivityBrowserActionTest`、
  `PendingAiHomeActionHandlerTest` 均通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness：PASS。
- `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 14:24:02 +08:00`
  - 大小：469777645 bytes
  - SHA-256：`CCB49D00F314FDCDFAB1CF1DBF65462A9DF676588C743244E6A0FE4A480BCAE0`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04B1 Operit navigation policy 验收

- [DONE] 新建
  `com.kiyori.integration.operit.navigation.OperitNavigationStackPolicy`。
- [DONE] 唯一迁移 8 个符号：`AiDrawerSelectionEffect`、`AiTopBarMode`、
  `resolveAiDrawerSelection`、`resolveAiTopBarMode`、
  `hasSameAiSettingsSourceFamily`、`toAiPrimaryRouteEntry`、
  `preservesAiPrimaryStack`、`buildAiPrimaryStack`。
- [DONE] `KiyoriShellState.kt` 的 Operit import 从 5 降到 0；状态字段、Back 顺序、
  saveable value 顺序和外部 destination 转移不变。
- [DONE] `KiyoriApp.kt` 的过渡 Operit import 从 53 降到 45，新增 8 个
  `com.kiyori.integration.operit.navigation` import。
- [DONE] ARCH021 锁定 integration policy 唯一 owner、5 个 Operit import、Shell state
  零 Operit import、两份源码 SHA 与 KiyoriApp 精确接线。

### M-04B1 完成证据

- ARCH019/ARCH020/ARCH021：PASS，`phase=m03`。
- ARCH021 gate tests：2 tests，零失败。
- 完整 Python：115 tests，零失败。
- integration policy LF-normalized SHA-256：
  `BA0EDA23DEBAE444F7CF1EE3C2E1CB6072458B3A61024EF16508368F01F70FFC`。
- Shell state LF-normalized SHA-256：
  `13C700360E615AA3C8F538578F4063939246E226F558CE474C1C92BBF0D8107A`。
- 当前 root LF-normalized SHA-256：
  `35003074E6D849EAE43E84C6401DE135CF923BC1D41C7E1CF70A8B2352847206`。
- Kotlin compile 与完整 `KiyoriShellStateTest`：PASS。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、`git diff --check`、本地 Markdown links：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 时间：`2026-08-01 14:46:20 +08:00`
  - 大小：469777645 bytes
  - SHA-256：`32FC9EA69CEC8772CADF69F40E37A575DF220B211FD9266B68796E8CF28504F8`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - ABI：`arm64-v8a`
  - APK Signature Scheme v2：true
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；terminal 与 device/ADB 未改变。

## M-04B2 Browser presentation contract 与纯 Shell state 验收

- [DONE] 新建
  `com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation`，只包含
  `CLOSE` 与 `MINIMIZED_INDICATOR` 两个既有 presentation 结果，不引入 Browser Runtime
  或第二状态 owner。
- [DONE] `KiyoriShellState.kt` 从
  `com.ai.assistance.operit.ui.main.shell` 纯移动到 `com.kiyori.app.shell`；状态字段、
  Saver value 顺序、Back 优先级、外部 destination 与 Browser return target 语义不变。
- [DONE] 旧 Shell state 路径删除，所有 state symbol 只剩一个 owner；新文件唯一项目
  import 为 Browser exit presentation capability。
- [DONE] Browser exit presentation 固定由 `KiyoriApp`、`KiyoriShellState` 和
  `KiyoriBrowserHome` 三处消费；Operit Browser UI 不依赖 `com.kiyori.app.shell`。
- [DONE] `KiyoriApp.kt` 的过渡 Operit import 从 45 降到 35，并以 9 个精确
  `com.kiyori.app.shell` import 连接同一 Shell state。
- [DONE] 四个仍位于 Operit 路径的过渡桥按文件精确锁定：
  `MainActivity.kt` 使用 external destination，
  `KiyoriAppShell.kt` 使用 6 个 state contract，
  `KiyoriAiDrawer.kt` 使用 drawer width policy，
  `KiyoriShellPages.kt` 使用 `PrimaryDestination`。
- [DONE] ARCH022/ARCH023 锁定 capability/state 的 package、源码 SHA、唯一 owner、固定
  消费者、旧路径清理、KiyoriApp 接线和四处过渡桥。

### M-04B2 完成证据

- ARCH019/ARCH020/ARCH021/ARCH022/ARCH023：PASS，`phase=m03`。
- ARCH022/ARCH023 gate tests：2 tests，零失败。
- 完整 Python：117 tests，零失败。
- Browser exit presentation LF-normalized SHA-256：
  `18BA032D7C4DBCDAC9569108B3638CC28BDE389A04583E386332FB0C18C66B08`。
- Shell state LF-normalized SHA-256：
  `1DF5B2FDBBAE6A1D986A5D2F60C2D40CC88971195F1D2E4542F6A60B50A7C2A7`。
- 当前 root LF-normalized SHA-256：
  `B19861E6B4C0570426D5D9FB221F937363E00487E9CFCEB2BFA61D67CA8E3215`。
- root 过渡 Operit imports：35，重复项 0。
- 定向 JVM：`KiyoriShellStateTest` 与 `MainActivityBrowserActionTest` 通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、`git diff --check`、本地 Markdown links：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 大小：469118205 bytes
  - SHA-256：`04C7BEE47A784B8A3C142BD0B614AB76A2EAE013CCEDE78E89D8FC6EDDA4C055`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04B3 App Shell host 验收

- [DONE] `KiyoriAppShell.kt` 从
  `com.ai.assistance.operit.ui.main.shell` 纯移动到 `com.kiyori.app.shell`，旧路径删除。
- [DONE] 548 行 host 的 Composable 方法体、PagerState、fling behavior、预组合时机、
  BackHandler、drawer z-index、Browser host、AI host translation 与动画参数不变。
- [DONE] 仅把原同包 13 个页面/drawer 符号变成显式 Operit import，并移除变为同包的
  6 个 Shell-state import；加上原有 AI quick action、navigation entry 与两层主题边界，
  最终项目 import 恰为 17 个 Operit import。
- [DONE] `KiyoriApp.kt` 只 import/call 新 `com.kiyori.app.shell.KiyoriAppShell`；
  root 的过渡 Operit import 从 35 降到 34，`com.kiyori.app.shell` import 从 9 增到 10。
- [DONE] `KiyoriShellStateTest` 只增加 11 个 moved helper 的显式 import，测试 package、
  断言和测试方法体不变。
- [DONE] 删除旧 App Shell 的 ARCH001 exception；新增一个文件精确 ARCH004 exception，
  由 ARCH024 锁定并在产品领域迁移阶段到期。

### M-04B3 完成证据

- ARCH019/ARCH020/ARCH021/ARCH022/ARCH023/ARCH024：PASS，`phase=m03`。
- ARCH024 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：119 tests，零失败。
- App Shell package-independent normalized SHA-256：
  `DB6A6DD7505CD64E925D53769097CA79326BEFC3EF1A0112909F09215E7AD949`，
  与移动前完全一致。
- App Shell 最终 LF-normalized SHA-256：
  `704DF7904E6030FE537738F05E94204B469857D371C6A17B8E348F222BDB77C6`。
- 当前 root LF-normalized SHA-256：
  `65E6A13E662C3D6A47D615EBFFEB9A7A2ED2F9688D8D557C5FBC7207E77CA7CC`。
- App Shell Operit imports：17，重复项 0；root 过渡 Operit imports：34，重复项 0。
- 定向 JVM：`KiyoriShellStateTest` 与 `MainActivityBrowserActionTest` 通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、`git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 16:37:43 +08:00`
  - 大小：470447784 bytes
  - SHA-256：`F7FAE589513E46A4EF4D955293D3DE2279C810DC61B117D2B2CFB61FED36B7D7`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04B4 Modal AI Drawer host 验收

- [DONE] `KiyoriAiDrawer.kt` 从
  `com.ai.assistance.operit.ui.main.shell` 纯移动到 `com.kiyori.app.shell`，旧路径删除。
- [DONE] 642 行 Drawer 的 animation duration、scrim、status-bar inset、fold cap、
  BackHandler、权限/网络/ToolPkg/Workflow 状态投影和 navigation callback 不变。
- [DONE] 只修改 package，并删除变为同包的 `calculateKiyoriAiDrawerWidthDp` import；
  最终项目 import 恰为 13 个 Operit import。
- [DONE] `KiyoriAppShell` 保持唯一 `KiyoriModalAiDrawer` call，并删除旧 Drawer import；
  App Shell 的过渡 Operit import 从 17 降到 16，规范化源码哈希仍与 M-04B3 移动前一致。
- [DONE] `KiyoriShellStateTest` 只增加 `resolveKiyoriAiDrawerTone` 的显式新 owner import，
  tone 断言和测试方法体不变。
- [DONE] 删除旧 Drawer 的 ARCH001 exception；新增文件精确 ARCH004 exception，由
  ARCH025 锁定并在 Operit integration 收口阶段到期。

### M-04B4 完成证据

- ARCH019 至 ARCH025：PASS，`phase=m03`。
- ARCH025 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：121 tests，零失败。
- AI Drawer package-independent normalized SHA-256：
  `6C681785B1D65E8A595BFD08B35DB4FB15F55A6A5B2643714BDA29E3BE1E1ED9`，
  与移动前完全一致。
- AI Drawer 最终 LF-normalized SHA-256：
  `0C23DAEA699C2E96B07A54E6C7A8C0A27B12D9DEC0D61614615E46F616D3E60C`。
- App Shell package-independent normalized SHA-256：
  `DB6A6DD7505CD64E925D53769097CA79326BEFC3EF1A0112909F09215E7AD949`。
- App Shell 当前 LF-normalized SHA-256：
  `EF09C202EA39529F3769C463BC37A368E87FA55079622DFF714202FFBC7F6250`。
- AI Drawer Operit imports：13；App Shell Operit imports：16；均无重复。
- 定向 JVM：`KiyoriShellStateTest` 与 `MainActivityBrowserActionTest` 通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、`git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 17:02:37 +08:00`
  - 大小：470447780 bytes
  - SHA-256：`00E0DCDF8FEB80BA1382DBF4ADA509CAC24F17105A738F60F677BB9AD9F0FCD5`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04B5 Primary destination presentation 验收

- [DONE] 新建 `com.kiyori.app.shell.KiyoriPrimaryNavigation`，从
  `KiyoriShellPages.kt` 原样提取 `PrimaryDestinationVisual`、视觉映射、三条选中动画策略、
  `KiyoriPrimaryRootPage`、`KiyoriBottomNavigation` 与 icon animation。
- [DONE] 提取组保留原 destination 顺序、资源 ID、center icon size、占位 root、
  settings/files 分发、navigation-bar padding、touch target、semantic role、Crossfade、
  70ms 压缩与 spring 参数。
- [DONE] `KiyoriShellPages.kt` 从 1234 行缩减到 912 行，只删除提取声明及其专属 import；
  Software Home 与 Browser 搜索代码未移动。
- [DONE] `KiyoriAppShell` 保持 primary root 与 bottom navigation 各一个 call，删除两个旧
  Operit import；过渡 Operit import 从 16 降到 14，规范化源码哈希保持不变。
- [DONE] `KiyoriShellStateTest` 只增加三条动画策略的新 owner import，断言不变。
- [DONE] 删除 `KiyoriShellPages.kt` 的 ARCH001 exception；新增 primary navigation 的
  文件精确 ARCH004 exception，由 ARCH026 锁定。

### M-04B5 完成证据

- ARCH019 至 ARCH026：PASS，`phase=m03`。
- ARCH026 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：123 tests，零失败。
- Primary navigation LF-normalized SHA-256：
  `C4790D3DF31E28F450F24A2E80ED7B180403CC20E580D6E652A4A1A1BB072DE7`。
- Primary navigation Operit imports：10，重复项 0。
- App Shell package-independent normalized SHA-256：
  `DB6A6DD7505CD64E925D53769097CA79326BEFC3EF1A0112909F09215E7AD949`。
- App Shell 当前 LF-normalized SHA-256：
  `A87DB83C88D1937158C57B3ADD78B92FE106372BCECB07B48188288D480456B1`；
  Operit imports：14。
- `KiyoriShellPages.kt` 当前 LF-normalized SHA-256：
  `FB4C67C2EB0D6E76C3C9D216B5DA2428F43E46BE7281E8DE5B8717A394FE8C39`。
- 定向 JVM：`KiyoriShellStateTest` 与 `MainActivityBrowserActionTest` 通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、`git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 17:33:35 +08:00`
  - 大小：470449373 bytes
  - SHA-256：`75C53A4031FE0DFBA3A0A81008A66945B69B55E5B1BB439BBFC327324DF23E37`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04B6 Software Home 验收

- [DONE] 新建 `com.kiyori.app.shell.KiyoriSoftwareHome`，从
  `KiyoriShellPages.kt` 原样提取 Software Home 页面、四个策略 enum、19 个固定视觉参数、
  四个纯 resolver、天气与 Browser 窗口动作、Search/AI frame 和全部私有 helper。
- [DONE] 保留原 Pager host 参数、`rememberSaveable` mode、黄金分割定位、三档宽度、
  short-height 规则、固定颜色/尺寸、location permission、weather refresh、AI quick action、
  Browser window count 与语义属性；不改可见行为或状态 owner。
- [DONE] `KiyoriShellPages.kt` 从 912 行缩减到 161 行，只剩
  `KiyoriFullScreenWebSearchPage`、`KiyoriWebSearchRequest` 和
  `resolveKiyoriWebSearchRequest`。
- [DONE] `KiyoriAppShell` 保持唯一 Software Home call，删除旧 Operit import；过渡
  Operit import 从 14 降到 13，package-independent normalized SHA 保持不变。
- [DONE] `KiyoriSoftwareHomeSearchTest` 只增加 27 个迁移符号的新 owner import；
  Browser request/resolver 断言仍使用旧 Browser Search owner，测试逻辑不变。
- [DONE] 新增 Software Home 文件精确 ARCH004 exception；ARCH027 锁定源码 SHA、9 个
  精确 Operit import、完整唯一声明组、App Shell 挂载与测试接线。

### M-04B6 完成证据

- ARCH019 至 ARCH027：PASS，`phase=m03`。
- ARCH027 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：125 tests，零失败。
- Software Home LF-normalized SHA-256：
  `891D60B0D36D7F1ED6C9BF407B957F4C7435E2BFA6EB4AC18602F41BA24CE869`。
- Software Home Operit imports：9，重复项 0。
- App Shell package-independent normalized SHA-256：
  `DB6A6DD7505CD64E925D53769097CA79326BEFC3EF1A0112909F09215E7AD949`。
- App Shell 当前 LF-normalized SHA-256：
  `ED3B1E08174F9C77E0DCB3CE25D02C09DB4E1BBA45053D06E68F926C491AAD4B`；
  Operit imports：13。
- residual `KiyoriShellPages.kt` 当前 LF-normalized SHA-256：
  `2DCBF452885CDCD6441F3BF122CB441AFF55A6BCA9D67F3346E824AFF02A7FDF`。
- 定向 JVM：`KiyoriSoftwareHomeSearchTest` 与 `KiyoriShellStateTest` 通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、`git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，24 executed、209 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 18:09:43 +08:00`
  - 大小：470446833 bytes
  - SHA-256：`E4DDCF661685E0A49A0010E2BF27110312403C477503E647FBFE08391288A040`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04B7 residual Browser Search 验收

- [DONE] 将 residual 161 行 `KiyoriShellPages.kt` 纯移动为
  `com.kiyori.app.shell.KiyoriBrowserSearch`，保留
  `KiyoriFullScreenWebSearchPage`、`KiyoriWebSearchRequest` 与
  `resolveKiyoriWebSearchRequest` 三个声明，旧路径已删除。
- [DONE] 保留同一 `BrowserPresentationCoordinator`、`WebSessionHistoryStore`、
  `WebSessionProfile`、search engine flow、history actions、incognito availability、
  1.2 秒 feedback、address resolver、blank-input 规则和新 session request contract；
  不改 Browser Runtime、history/profile owner 或搜索行为。
- [DONE] `KiyoriAppShell` 保持唯一 Full-Screen Browser Search call，删除页面/request
  两条旧 Operit import；过渡 Operit import 从 13 降到 11，package-independent
  normalized SHA 保持不变。
- [DONE] `KiyoriApp` 的 request/resolver import 改接同一 Shell owner，Operit imports
  从 34 降到 32；weather search 与 submitted search 的调用体不变。
- [DONE] `KiyoriSoftwareHomeSearchTest` 只增加 request/resolver 的新 owner import，
  原 8 个测试及断言不变。
- [DONE] 新增 Browser Search 文件精确 ARCH004 exception；ARCH028 锁定旧路径缺失、
  源码 SHA、8 个精确 Operit import、三个唯一声明、App Shell/KiyoriApp/test 接线。

### M-04B7 完成证据

- ARCH019 至 ARCH028：PASS，`phase=m03`。
- ARCH028 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：127 tests，零失败。
- Browser Search LF-normalized SHA-256：
  `FEE88AC0C8BBF9FB2E859B0693D8902A27DD80DD7D3B857510AE62E39A8B42C8`。
- Browser Search Operit imports：8，重复项 0。
- App Shell package-independent normalized SHA-256：
  `DB6A6DD7505CD64E925D53769097CA79326BEFC3EF1A0112909F09215E7AD949`。
- App Shell 当前 LF-normalized SHA-256：
  `9DCF5C2CEC7B8386B9FEB0EF7A4FE8D452FD9FB8E33CD2089C592F4AF377F698`；
  Operit imports：11。
- KiyoriApp 当前 LF-normalized SHA-256：
  `39F16369B49DC43BB83181101DBF3965C4FDF292CEF34E90C10EF00756987071`；
  Operit imports：32。
- 旧 `app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriShellPages.kt`：
  absent。
- 定向 JVM：`KiyoriSoftwareHomeSearchTest`、`KiyoriShellStateTest` 与
  `MainActivityBrowserActionTest` 通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、`git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，24 executed、209 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 19:20:05 +08:00`
  - 大小：470447848 bytes
  - SHA-256：`F104A5778C17FA518350FA22420E1073DF1A5FDC0540BF0119C7D3CDD8BBD0BF`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04C Operit navigation integration 验收

- [DONE] 将 `AppRouteCatalog` 从 Operit UI navigation 包移动到
  `com.kiyori.integration.operit.navigation`，旧路径删除；稳定
  `AppNavigationModels`、`AppRouterState`、`AppRouterGateway` 与
  `AppRouteDiscoveryGateway` 合同继续留在原包供 JsEngine/Operit screens 使用。
- [DONE] 新建 `OperitNavigationIntegration`，复用唯一 `PackageManager` singleton，
  将 navigation revision、ToolPkg runtime listener、catalog build/facade、gateway
  install/clear 和 ToolPkg navigation action delegation 收口到同一 integration owner。
- [DONE] catalog 改为接收同一个 `PackageManager` 引用，不再自行解析第二次引用；
  route/plugin 构建、排序、Screen resolve、initial entry 和 screen-to-entry 行为不变。
- [DONE] `findOperitNavigationRoot` 与 `toOperitExternalRouteEntry` 移入 integration，
  host route 接受任意 args、plugin route 要求精确 args 的既有语义由新增 JVM 测试锁定。
- [DONE] `KiyoriApp` 删除 `DisposableEffect`、AIToolHandler、PackageManager、
  AppNavigationModel、AppRouteCatalog、两个 gateway 与旧 helper import/call，保留唯一
  `AppRouterState`、Compose state、provider 顺序和业务 callback；文件从 793 行缩减为
  733 行，Operit imports 从 32 降到 25。
- [DONE] `KiyoriSettingsPagesTest` 改接新 catalog owner；新增
  `OperitNavigationIntegrationTest` 两个 route-root 行为测试。
- [DONE] 更新 KiyoriApp ARCH004 exception 的真实剩余范围与 M-07 到期点；ARCH029
  锁定两个新 owner、唯一 lifecycle、KiyoriApp/test 接线和旧路径缺失。

### M-04C 完成证据

- ARCH019 至 ARCH029：PASS，`phase=m03`。
- ARCH029 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：129 tests，零失败。
- Route catalog LF-normalized SHA-256：
  `054441C0AE6008BA59DFF2C08C6E0DC7ADEFCED74D81EC12309F7074A40A8F41`；
  Operit imports：16，重复项 0。
- Navigation integration LF-normalized SHA-256：
  `6CFAFA431C60E7490F8A186E37F714D272567E35F2ABE85E7EFA927D007E63B1`；
  Operit imports：12，重复项 0。
- KiyoriApp LF-normalized SHA-256：
  `845C1EF2FDCC271F6E0DC7513ACE00593640BA7455446D6495742201FC1ACA8B`；
  733 行，Operit imports：25。
- 旧 `app/src/main/java/com/ai/assistance/operit/ui/main/navigation/AppRouteCatalog.kt`：
  absent。
- 定向 JVM：`OperitNavigationIntegrationTest`、`KiyoriSettingsPagesTest`、
  `KiyoriShellStateTest` 与 `MainActivityBrowserActionTest` 通过。
- 完整 JVM：125 suites，`775/775`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、`git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，24 executed、209 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 20:12:17 +08:00`
  - 大小：468991581 bytes
  - SHA-256：`71A860FF9957FCAAB290669449159F8269F145C6C904FB94E8E2E3E0942FC402`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD/origin/terminal 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D1 MainActivity pending-request owner 验收

- [DONE] 新建唯一 `com.kiyori.app.startup.KiyoriMainPendingRequests`，接收已经解析的
  shared files/text、Browser、OAuth、shortcut、route 和 Shell destination 请求。
- [DONE] `MainActivity` 从 13 个分散字段收口为一个
  `private val pendingRequests = KiyoriMainPendingRequests()`；稳定 Activity FQCN、action、
  extra、URI、Manifest、lifecycle 和 `setContent` host 保持不变。
- [DONE] request ID 继续由 Activity 在原调用位置通过 `System.currentTimeMillis()` 生成；
  owner 只在 handled ID 精确匹配时清除 shortcut、route、Browser 和 Shell request。
- [DONE] 保持 shared files 成功时同时清 files/text、失败时只清 files 并让 text 后续
  独立处理的既有顺序；OAuth 保持 take-and-clear，shortcut 消费不清 current navigation。
- [DONE] 下载打开、GitHub OAuth、`SharedFileHandler`、Toast、日志、Intent 解析和
  Android lifecycle 副作用均留在 Activity，owner 不建立第二 runtime/store/coordinator。
- [DONE] ARCH030 锁定 owner package、源码 SHA、两个精确项目 import、唯一声明、
  MainActivity 单一持有字段、旧字段缺失、16 条状态 API 接线、禁止副作用和合同测试。

### M-04D1 完成证据

- ARCH019 至 ARCH030：PASS，`phase=m03`。
- ARCH030 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：131 tests，零失败。
- `KiyoriMainPendingRequests` LF-normalized SHA-256：
  `70B2E5F29C645A7DBE3F8C34277F59B1F7B50137955923291A5B56601B022734`；
  158 行，项目 imports：2，重复项 0。
- `MainActivity`：916 行；`KiyoriMainPendingRequests` 持有字段 1 个，13 个旧字段声明 0。
- 定向 JVM：`KiyoriMainPendingRequestsTest` 3 tests，零失败。
- 完整 JVM：126 suites，`778/778`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、working-tree Markdown links、
  `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 20:46:30 +08:00`
  - 大小：470030285 bytes
  - SHA-256：`3C0FA7301EF846C90D24CCCB0A4A0492D3A2E7374F9B24182640B17A89423F96`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D2 MainActivity Intent decoder 验收

- [DONE] 新建唯一 `KiyoriMainIntentDecoder`，按原优先级读取 player restart、download
  task、Shell destination、settings shortcut、widget route、GitHub OAuth、VIEW、SEND
  与 SEND_MULTIPLE。
- [DONE] 新建密封 `KiyoriMainIntentCommand` 和 `KiyoriMainIntentDecoding`；共享 action
  的 pending-content 处理信号与 command 分离，保持 OAuth/route 等高优先级分支先返回，
  Browser VIEW 仍触发原 pending-share 处理序列。
- [DONE] `KiyoriMainIntentContract` 唯一持有 10 个 action/extra 字面值；MainActivity
  companion 原 10 个公开 `const val` 继续桥接同值。
- [DONE] `MainActivity` 只调用一次 decoder，直接 long/string/parcelable/data payload
  getter 为 0；resolver 从旧 Activity 文件移动到 decoder owner，既有测试改接新路径。
- [DONE] decoder 不修改 Intent、不生成时间戳、不调用 Player、download、OAuth
  coordinator、share handler、Toast、日志或 lifecycle；这些副作用继续留在 Activity
  原时点。
- [DONE] ARCH031 锁定 decoder package、源码 SHA、3 个精确项目 import、唯一 symbol、
  必需 payload 解码、禁止副作用、MainActivity 接线、稳定常量桥和合同测试。

### M-04D2 完成证据

- ARCH019 至 ARCH031：PASS，`phase=m03`。
- ARCH031 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：133 tests，零失败。
- `KiyoriMainIntentDecoder` LF-normalized SHA-256：
  `319B6719325228E98140B781FC6C008B534134C46A3E797F73298D3506A0B16A`；
  256 行，项目 imports：3，重复项 0。
- `MainActivity`：887 行；decoder call 1，直接 payload getter 0，稳定常量桥 10。
- 定向 JVM：`KiyoriMainIntentDecoderTest` 5 tests 与
  `MainActivityBrowserActionTest` 1 test，零失败。
- 完整 JVM：127 suites，`783/783`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、working-tree Markdown links、
  `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 21:13:44 +08:00`
  - 大小：470037414 bytes
  - SHA-256：`E4F53F4646191A935453252D8B0ACF0093F46F1F609648B437462771018B411F`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D3 MainActivity display coordinator 验收

- [DONE] 新建无状态唯一 `KiyoriMainDisplayCoordinator`，接收当前 Activity 并只操作其
  Window/Display，不保存 Activity 或显示状态。
- [DONE] 保持 API 31+ sustained-performance 请求与异常日志，API 30+ display mode
  选择、API 23–29 preferred refresh rate 和所有版本 hardware-acceleration flags。
- [DONE] 新增纯 `selectHighestRefreshRateMode` 与 `selectHighestRefreshRate`，锁定
  60 Hz 基线、严格大于比较、最高值和 tie 时首个 mode 保持的原循环语义。
- [DONE] `MainActivity` 删除 `configureDisplaySettings`、`getHighestRefreshRate` 和
  `getDeviceRefreshRate`，显示 API token 为 0，只保留一次 coordinator configure 调用。
- [DONE] ARCH032 锁定 coordinator package、源码 SHA、唯一 AppLogger import、纯策略
  owner、Android API 精确计数、Activity 接线、旧 helper 缺失与合同测试。

### M-04D3 完成证据

- ARCH019 至 ARCH032：PASS，`phase=m03`。
- ARCH032 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：135 tests，零失败。
- `KiyoriMainDisplayCoordinator` LF-normalized SHA-256：
  `C7E32CE7A2A6E34FDF9207DCAB0D4000FADAFABEFF4D187EE760ED8E434AE1E8`；
  141 行，项目 imports：1。
- `MainActivity`：802 行；coordinator call 1，Activity display API token 0。
- 定向 JVM：`KiyoriMainDisplayCoordinatorTest` 3 tests，零失败。
- 完整 JVM：128 suites，`786/786`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、working-tree Markdown links、
  `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 21:34:40 +08:00`
  - 大小：470041779 bytes
  - SHA-256：`53038237D9B31087CBE6BB1172B7943E988F62809366AA3C2798C716EE848E16`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D4 MainActivity shared-content coordinator 验收

- [DONE] 新建唯一 `KiyoriMainSharedContentCoordinator`，持有当前 Activity 与唯一
  `KiyoriMainPendingRequests`，不保存第二份 files/text 事实状态。
- [DONE] 保持 files 存在时 text 等待并随 files 转交、standalone text trim/blank 忽略、
  Activity `lifecycleScope` 时机、成功清 files/text，以及失败时记录日志、显示原 Toast、
  只清 files 后继续处理 standalone text 的既有行为。
- [DONE] 原 `SharedFileHandler` 继续唯一持有三个 AIChat StateFlow 与 clear callback；
  coordinator 只执行 pending→handler 转交，不移动或复制该状态 owner。
- [DONE] `MainActivity` 删除原 shared-content 转交实现，只保留一个 lazy coordinator，
  files/text 各两个调用点。
- [DONE] ARCH033 锁定 coordinator package、源码 SHA、3 个精确项目 import、唯一 transfer
  owner、SharedFileHandler/pending clear 计数、Activity 接线、旧实现缺失与合同测试；
  ARCH030 只在 Activity 与该 coordinator 的精确组合边界内锁定 pending API。

### M-04D4 完成证据

- ARCH019 至 ARCH033：PASS，`phase=m03`。
- ARCH033 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：137 tests，零失败。
- `KiyoriMainSharedContentCoordinator` LF-normalized SHA-256：
  `0F1873799A4DA29E3CB74960115E91FF21D45DFC5CDEE4B4750B36D08DA61E4D`；
  114 行，项目 imports：3。
- `MainActivity`：760 行；lazy coordinator owner 1，files/text 调用点 `2/2`，
  原 `processPendingSharedFiles` / `processPendingSharedText` 实现 0。
- 定向 JVM：`KiyoriMainSharedContentCoordinatorTest` 3 tests，零失败。
- 完整 JVM：129 suites，`789/789`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、working-tree Markdown links、
  `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，24 executed、209 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 22:01:12 +08:00`
  - 大小：470045143 bytes
  - SHA-256：`AE11A63373B55A7B7A65F59C07C00886ECFEF1D23083CED92CC8D96C498DFC45`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D5 MainActivity task-visibility coordinator 验收

- [DONE] 新建无状态唯一 `KiyoriMainTaskVisibilityCoordinator`，不保存 Activity、task
  或 foreground-runtime 状态。
- [DONE] 保持 API 21+ 与 `AIForegroundService.isRunning` 判定，以及遍历当前
  ActivityManager appTasks 执行 `setExcludeFromRecents(false)` 的既有行为。
- [DONE] 保持单 task 失败与整体 ActivityManager 查询失败的两层 AppLogger 记录。
- [DONE] `MainActivity` 删除原 helper 与 ActivityManager/AIForegroundService/
  setExcludeFromRecents 直接依赖，只在主初始化完成后和 `onNewIntent` 入口保留两个调用。
- [DONE] ARCH034 锁定 coordinator package、源码 SHA、2 个精确项目 import、纯判定函数、
  无状态唯一 owner、平台 API 计数、Activity 两个调用点、旧实现缺失与合同测试。

### M-04D5 完成证据

- ARCH019 至 ARCH034：PASS，`phase=m03`。
- ARCH034 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：139 tests，零失败。
- `KiyoriMainTaskVisibilityCoordinator` LF-normalized SHA-256：
  `D63061ECBBCD2351396E83745F9F4F080E844739A21A5ED8497166E9E258BEF6`；
  50 行，项目 imports：2。
- `MainActivity`：741 行；coordinator 调用 2，原 helper、ActivityManager、
  AIForegroundService 与 setExcludeFromRecents token 均为 0。
- 定向 JVM：`KiyoriMainTaskVisibilityCoordinatorTest` 3 tests，零失败。
- 完整 JVM：130 suites，`792/792`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、working-tree Markdown links、
  `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 22:31:29 +08:00`
  - 大小：470045623 bytes
  - SHA-256：`F246315DBCCF89522C7AB2C14EA96E67234A79D8F76B84A05F5352409AE743BB`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D6 MainActivity orientation coordinator 验收

- [DONE] 新建唯一 `KiyoriMainOrientationCoordinator`，集中持有 last orientation 与
  dialog visibility 的单一 Compose state。
- [DONE] 新增纯 `resolveKiyoriMainOrientationChange`，锁定 orientation 不变时返回同一
  state、变化时更新值并显示 dialog，以及 dialog 已显示时再次变化仍保持显示的当前语义。
- [DONE] 方向确认对话框 presentation 移出 MainActivity；四个 string resource、单一
  AlertDialog 与 confirm/dismiss 回调保持。
- [DONE] Activity 继续在 configuration 回调中先隐藏 Plugin Loading，再分发 orientation；
  confirm 后仍由 Activity 唯一执行 `recreate()`。
- [DONE] ARCH035 锁定 coordinator package、源码 SHA、2 个精确项目 import、唯一
  state/reducer/Compose owner/dialog、资源/API 计数、Activity 接线、调用顺序、旧实现
  缺失与行为测试。

### M-04D6 完成证据

- ARCH019 至 ARCH035：PASS，`phase=m03`。
- ARCH035 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：141 tests，零失败。
- `KiyoriMainOrientationCoordinator` LF-normalized SHA-256：
  `F281BF8814248969DF8BF95D7E987E362FF7404A0B9B870C5E4ADBD36432D34F`；
  105 行，项目 imports：2。
- `MainActivity`：697 行；coordinator field/init/config/dialog/dismiss/recreate 接线为
  `1/1/1/1/2/1`，旧 show/last/dialog owner 均为 0。
- 定向 JVM：`KiyoriMainOrientationCoordinatorTest` 4 tests，零失败。
- 完整 JVM：131 suites，`796/796`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、working-tree Markdown links、
  `git diff --check`：PASS。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 23:02:46 +08:00`
  - 大小：470049571 bytes
  - SHA-256：`8E8BA68316543E9685743C3C0D893CC74484E3C2243B0DA71F103195C4209149`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04D7 MainActivity startup notification permission coordinator 验收

- [DONE] 新建唯一 `KiyoriMainNotificationPermissionCoordinator`，以 Activity 直接字段
  构造并在 `onCreate` 前完成 Activity Result launcher 注册。
- [DONE] 新增纯 `resolveKiyoriMainNotificationPermissionAction`，锁定 API 33 以下、
  已授权、需要 rationale 与直接请求四条分支；系统权限状态仍是唯一事实源。
- [DONE] 保持 rationale Toast、拒绝 Toast、六条 AppLogger 记录、两处 permission
  launch，以及 result granted/denied 处理的既有行为。
- [DONE] `MainActivity` 删除原 launcher/helper 与 Manifest/PackageManager/
  ActivityResultContracts/ContextCompat 直接依赖，只保留一个非 lazy coordinator 字段与
  原初始化位置的一次 `checkAndRequest()`。
- [DONE] ARCH036 锁定 coordinator package、源码 SHA、2 个精确项目 import、唯一
  action/resolver/launcher owner、权限 API/资源/日志计数、Activity 早期注册接线、旧实现
  缺失与纯策略测试。
- [DONE] 完整 lint 再生成后按结构化交集删除 51 条当前工具链不再报告的历史记录，保留
  5792 条且不吸收 328 条 current-only 问题；其中原 M-03 的
  `AppBundleLocaleChanges` 已失效，ARCH018 当前锁定 Application lint 路径旧 0、新 5，
  并新增防止第 6 条失效记录回流的反向测试。

### M-04D7 完成证据

- ARCH019 至 ARCH036：PASS，`phase=m03`。
- ARCH036 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：144 tests，零失败。
- `KiyoriMainNotificationPermissionCoordinator` LF-normalized SHA-256：
  `7E551030415B89011CBD88609C8116B2C1B3FA16C9F114C80444D2CE81EAB844`；
  110 行，项目 imports：2。
- `MainActivity`：653 行；非 lazy coordinator field/check call 为 `1/1`，旧
  launcher/helper/Manifest permission token 均为 0。
- 定向 JVM：`KiyoriMainNotificationPermissionCoordinatorTest` 4 tests，零失败。
- 完整 JVM：132 suites，`800/800`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、255 个 working-tree Markdown 文件、
  `git diff --check`：PASS。
- lint baseline：5792 records，stale 0，current-only 328，SHA-256
  `A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 时间：`2026-08-01 23:37:04 +08:00`
  - 大小：470051071 bytes
  - SHA-256：`555D0222EC1DDF9B58C69E1A6EF4CE931CADD4BDCC32C1BC157B9A47625E0D0B`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

### M-04D8 完成证据

- ARCH019 至 ARCH037：PASS，`phase=m03`。
- ARCH037 正向/失败 gate tests：2 tests，零失败。
- 完整 Python：146 tests，零失败。
- `KiyoriMainStartupGateCoordinator` SHA-256：
  `DF2ACE9B63CD511B20B8375AD955BA432E49B525AA3A27669B4D0555951D63A4`；
  136 行，项目 imports：6。
- `MainActivity`：623 行；coordinator field/init、permission refresh、content-ready read、
  startup gate mount 分别为 `1/1/2/2/1`，旧 agreement owner、`showPermissionGuide`
  字段与 `checkPermissionLevelSet` helper 均为 0。
- 定向 JVM：`KiyoriMainStartupGateCoordinatorTest` 7 tests，零失败。
- 完整 JVM：133 suites，`807/807`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、255 个 working-tree Markdown 文件、
  `git diff --check`：PASS。
- lint baseline：5792 records，stale 0，current-only 328，SHA-256
  `A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 权威里程碑日期：`2026-08-01`
  - 文件系统 mtime：`2026-08-02 00:53:05 +08:00`；该值晚于当前日期，只记录为
    本机时钟异常，不作为完成日期
  - 大小：470053617 bytes
  - SHA-256：`1F5835C63B6946182E3F004EF5EC75CC930E9C8F54A380C67237E9587600DDDF`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

### M-04D9 完成证据

- ARCH019 至 ARCH038：PASS，`phase=m03`。
- ARCH038 正向/失败 fixture：`2/2`；ARCH020/ARCH030/ARCH033 owner 迁移相关正反向
  回归：`6/6`。
- 完整 Python：148 tests，零失败。
- `KiyoriMainContentHost` LF-normalized SHA-256：
  `E80C04A5A129C44E83BB34B8F3B2F9D60E691894F44CDD8C676ECDA56B61E12C`；
  93 行，项目 imports：5，重复项 0。
- `MainActivity`：579 行；content host 挂载 1，直接 `KiyoriApp` /
  `CompositionLocalProvider` / `LocalPluginLoadingState` 装配 0；Activity 与 content
  host 的 shared-files/text 调用分别为 `1/1`，总数各 2。
- 定向 JVM：`KiyoriMainContentHostTest` 3 tests，零失败。
- 完整 JVM：134 suites，`810/810`，零失败、零错误、零跳过。
- formal readiness、lint baseline normalization、255 个 working-tree Markdown 文件、
  `git diff --check`：PASS。
- lint baseline：5792 records，stale 0，current-only 328，SHA-256
  `A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`。
- 严格 `:app:lintDebug` 额外执行后仍报告既有未基线化债务
  `31 errors / 289 warnings / 8 hints`；D9 source/test 的 current-only 为 0，
  MainActivity 只有既有 `AppBundleLocaleChanges` warning。该结果未写入 baseline，转入
  M-04E/最终质量收口。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 权威里程碑日期：`2026-08-01`
  - 大小：470058476 bytes
  - SHA-256：`A568EBDF498CD531E3E799BBD6A763E36269E6C1008A524F45B8E5B510ABE26F`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定入口：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

## M-04E 与 M-04 总封板完成证据

### E1：精确 compatibility ownership

- [DONE] ownership checker 实现确定性的精确 path 优先级；精确文件 record 覆盖命中的
  宽泛 glob，没有精确 record 时重叠宽泛 glob 仍报 multiple-owner。
- [DONE] 新增唯一 `operit-main-activity-compatibility` record，owner 为
  `kiyori-app-entry-compatibility`、sync zone `D`、phase `m04e`，只允许 Operit、
  Kiyori app 与 Kiyori platform，禁止 Kiyori feature。
- [DONE] 删除到期 `M-04D-main-activity-host` ARCH001 exception，不延长期限、不新增替代
  exception。
- [DONE] 新增 `m04e-main-activity-project-imports.txt`，锁定 32 条项目 import；ARCH039
  同时锁定 source package、稳定 FQCN、Manifest MAIN/LAUNCHER、feature 零依赖、唯一
  `KiyoriMainContentHost` import/mount 和旧直接根组合为 0。
- [DONE] ARCH039 失败优先真实证据为精确 owner 缺失、旧 exception 仍存在和 import
  snapshot 缺失；生产实现后 4 个正反向 fixture、direct ARCH039、ownership gate 和完整
  architecture 均通过。

### E2：M-04 owner 行为保持型 lint 收口

- [DONE] `KiyoriMainDisplayCoordinator` 使用 API 30 annotation，并删除 minSdk 26 后不可能
  到达的 API 23 以下分支；显示选择与窗口行为不变。
- [DONE] `KiyoriMainIntentDecoder` 直接以真实 SDK 选择 API 33 typed parcelable getter，
  不注入或缓存第二 SDK 事实。
- [DONE] `KiyoriMainNotificationPermissionCoordinator` 以真实 API 33 guard 隔离权限
  helper，保持 onCreate 前 launcher 注册、rationale/request/result 顺序和系统权限唯一
  事实源。
- [DONE] Kiyori root 与 pending request ID 改用 primitive Long Compose state；root
  初始网络字符串改由现有 `LocalResources` 读取同一资源值。
- [DONE] 严格 lint 从 D9 的 `31 errors / 289 warnings / 8 hints` 降为
  `27 errors / 287 warnings / 2 hints`。M-04 owner 只剩 MainActivity
  `AppBundleLocaleChanges` 与 KiyoriApp `ConfigurationScreenWidthHeight` 两条 warning；
  M-04 errors=0、hints=0。前者等待明确 bundle/locale 发布策略，后者进入 M-05
  design/platform，因为直接替换会改变 inset 与舍入后的宽屏语义。

### 总封板验证

- ARCH019 至 ARCH039：PASS，`phase=m03`。
- ARCH039 精确 ownership/封板 fixture：`4/4`；受影响 M-04 architecture fixture 与真实
  定向 gate 全部通过。
- 完整 Python：152 tests，零失败。
- 完整 JVM XML：134 suites、810 tests、0 failures、0 errors、0 skipped。
- formal readiness：PASS。
- lint baseline 结构化交集：5792 retained、0 stale、316 current-only；baseline
  SHA-256：
  `A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`，未吸收新问题。
- working-tree Markdown：255 files、0 issues。
- `git diff --check`：PASS；只输出既有 CRLF 到 LF 提示，无 whitespace error。
- `:app:assembleDebug`：PASS，233 tasks，27 executed、206 up-to-date；
  `verifyDebugPlayerRuntimePackaging` PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 权威里程碑日期：`2026-08-01`
  - 大小：468983182 bytes
  - SHA-256：`81F6BA9436031AB20CBFB30C23F111A927FF0913D422A4BC602BBDD1CDEC38D5`
  - package/version/label：`com.kiyori` / `45` / `0.1.0` / `Kiyori`
  - min/target/compile SDK：26 / 34 / 36
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 稳定 launcher：`com.ai.assistance.operit.ui.main.MainActivity`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI/native：`arm64-v8a` / 53 entries / 0 duplicate basename
  - APK Signature Scheme v2：true；Android Debug certificate
  - 16 KB zipalign：Verification successful
- Git：未提交、未推送；HEAD
  `176f803e683307aa8e182fb357f35f84755c5f8b`、origin/main
  `62464b054f6de00b70c5596295bc216eb8edf63d`、terminal gitlink
  `8d5c2c224317c0176c14520facbb202a8be5993f` 未改变；device/ADB 仍为
  `verification_pending`。

M-04 已完成。下一里程碑为 M-05 design/theme/platform：先建立精确 owner/路径矩阵、视觉与
系统行为特征测试、ARCH040 失败优先门禁和回滚点，再分别处理主题命名与 platform owner；
不得整体移动 `util`，不得复制主题偏好、权限、生命周期或路径事实，也不得在该阶段改变
Player system-bar、namespace、数据、协议、Manifest 稳定组件或 terminal。

## 停止条件

- Operit screen 需要 import `com.kiyori.app`；
- CompositionLocal 出现第二 owner 或默认值改变；
- Pager 页序、Back 优先级、drawer inset、AI Home 挂载、route ID、ToolPkg order 或
  keepAlive 语义变化；
- 需要第二 router、第二 PackageManager listener、第二 Browser/Player/download owner；
- 需要 namespace、Manifest 组件、Intent/URI、AIDL/JNI/native、数据或 terminal 变化；
- compile、定向测试、architecture 或 Debug APK 失败。
