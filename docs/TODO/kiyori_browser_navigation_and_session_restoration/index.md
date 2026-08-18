---
document_type: implementation-plan
status: verification_pending
implementation: local_complete
device_verification: pending
---

# 设置返回、浏览器窗口与会话恢复方案

## 1. 文档定位

本专项冻结以下四类后续开发合同：

1. 设置首页及各级设置子页面的逐级返回；
2. 从浏览器或 AI 对话进入设置后，最终返回原来源页；
3. 浏览器窗口创建、网页历史、自定义主页和普通/无痕窗口的统一规则；
4. “滑屏前进后退 / 恢复上次的搜索结果 / 询问是否恢复页面 / 保留多窗口”四项设置。

当前状态为 `verification_pending`：主体实现、完整本地回归、Lint、最终 Debug APK 构建与
独立静态审计已经完成；授权的 `main` 提交推送是本轮最后的外部交付步骤。设备、Android System
WebView 真实站点、触控和冷启动现场矩阵尚未执行，不能由本地证据替代。

本文件是专项权威入口。当前实现事实以源码、自动检查、最终 APK 和 Git 交付证据为准；原
`design_ready` 方案保留为设计依据，但已由本文的“实现阶段对账”和根 `CONTEXT.md` 当前合同覆盖。

关联背景：

- [网页浏览器设置复刻](../kiyori_browser_product_completion/4_settings_home_and_browser_settings.md)
- [真无痕 Profile、窗口逻辑与网页缩略图](../kiyori_browser_product_completion/3_incognito_profiles_and_window_thumbnails.md)
- [无痕生命周期与窗口卡片](../kiyori_home_ui_refresh/6_incognito_lifecycle_and_window_cards.md)
- [抽屉、标签、转场与 Back](../kiyori_browser_ui_refactor/3_drawer_tabs_motion_and_back.md)
- [正式开发准备](../formal_development_readiness/index.md)

## 2. 任务合同

### 2.1 目标

- 任意设置分类和子页面都只沿进入顺序逐级返回。
- 底部导航进入设置时，设置详情最终回到设置首页。
- 浏览器菜单进入设置时，设置详情最终回到设置首页，再回到原 Browser Home 和原活动网页。
- AI 对话左抽屉进入设置时，设置详情最终回到设置首页，再回到进入前的 AI 页面和 AI 路由栈。
- 网页普通跳转不再因为同域、跨域、`target="_blank"` 或 `window.open()` 随意创建产品窗口。
- 每个产品窗口继续使用自己的 WebView 历史，按原导航顺序逐级后退和前进。
- 自定义主页发生真实用户跨站跳转时保留原主页窗口，并让新窗口返回到该主页窗口。
- 普通窗口和无痕窗口保持不可转换的 Profile 边界。
- 四项新设置拥有清晰默认值、组合优先级、持久化边界和可测试状态机。

### 2.2 非目标

- 不在本专项中重写 Browser Runtime、下载器、播放器、书签、历史或用户脚本所有权。
- 不创建第二套 WebSession registry、第二个 Browser Host 或第二份浏览器设置 store。
- 不把网页历史数据库当作窗口列表。
- 不跨进程恢复 DOM、表单、滚动位置、完整 WebView Back/Forward List、脚本内存或媒体播放状态。
- 不把普通窗口恢复能力扩展到无痕窗口。
- 不写入 Cookie、请求头、密码、网页正文、页面截图或无痕元数据。
- 不保留被新导航模型替代的 `childBackTarget`、浏览器 `subPageName` 或并行路由实现。
- 不安装 APK、不操作 ADB/MuMu/真机、不构建 Release/AAB、不部署或调用真实业务远端。

### 2.3 产品发布边界

当前 Kiyori 仍是未发布产品。本次属于内部导航和浏览器运行时方案替换，后续实现应直接删除被替代
的旧状态和测试，不引入旧新并行路径。已有公开兼容标识、Intent 常量、ToolPkg/AI 工具协议和
持久化数据仍按各自合同保留。

## 3. 实现前已验证现状与根因

### 3.1 设置返回由三套状态共同承担

实现前的设置相关导航分散在：

- `KiyoriShellState.child / childBackTarget`
- `KiyoriBrowserSettingsPage.subPageName`
- `AppRouterState.backStack` 与 `RouteEntrySource.KIYORI_SETTINGS`

`childBackTarget` 只能表达一个父级，因此只能稳定覆盖：

```text
来源页
└── 设置首页
    └── 一个设置分类页
```

浏览器设置内部的主页自定义、插件权限、文字大小和密码管理又由 `subPageName` 单独持有；AI 助手、
界面和数据设置则进入另一套 Router。新增第三层、跨 Browser Runtime 工作台或进程重建后，三套状态
很容易出现标题返回、系统 Back 和来源页恢复不一致。

### 3.2 实现前来源保持语义已经存在，但只覆盖两级特例

当前源码和 `KiyoriShellStateTest` 已明确表达：

- Browser Home → 设置首页 → 浏览器设置 → 设置首页 → Browser Home
- AI 页面 → 设置首页 → 浏览器设置 → 设置首页 → AI 页面
- 来源保持型设置首页打开时隐藏底部导航

因此后续不是重新定义来源语义，而是把单级特例升级为完整设置会话和路由栈。

### 3.3 实现前插件中心存在跨导航域缺口

`KiyoriBrowserSettingsPage.openBrowserPluginRoute()` 当前先执行一次 `onBack()`，再打开插件中心或
用户脚本抽屉。来源保持型场景是：

```text
Browser Home
└── 设置首页
    └── 浏览器设置
```

一次 `onBack()` 只会回到设置首页，并不会关闭整个设置覆盖层。随后插件抽屉可能已经在设置首页
背后的 Browser Runtime 中打开，用户看不到目标工作台。这不是动画问题，而是跨导航域没有显式
返回令牌。

### 3.4 实现前网页弹窗会直接创建产品窗口

`BrowserWebViewSupport.configureWebView()` 当前同时设置：

```text
setSupportMultipleWindows(true)
javaScriptCanOpenWindowsAutomatically = true
```

`WebChromeClient.onCreateWindow()` 随后直接调用 `createPopupSessionOnMain(parentSession)`。它没有
判断：

- 是否为真实用户手势；
- 当前页面是否为配置主页根；
- 目标是否与主页同站；
- 请求是否只是 `_blank`；
- 请求是否由广告或脚本自动触发；
- 目标应在当前窗口还是新窗口打开。

因此窗口增长来自明确的产品策略缺失，不是 WebView 历史本身失效。

### 3.5 普通网页历史路径已经正确

HTTP(S) 主框架导航通常由当前 WebView 继续加载。`navigateSessionBackOnMain()` 已实现：

```text
有 WebView 历史
→ WebView.goBack()

无 WebView 历史且不在配置主页
→ 当前窗口导航到配置主页

已在配置主页根
→ 不再处理，由 Browser Home 决定是否退出
```

`BrowserHomeNavigationState` 还能跟踪配置主页请求、主页重定向后的真实 URL 和加载中的主页根；
主页完成后使用 `clearHistory()` 建立该窗口自己的主页根；实现继续复用这一历史所有权，不重写第二套
网页历史。

### 3.6 实现前窗口只存在于进程内

`StandardBrowserSessionTools.sessions`、`sessionOrder`、`activeSessionId` 和
`defaultSessionProfile` 都是进程内状态。Browser Home 没有活动窗口时会立即创建主页窗口。

当前没有：

- 普通窗口冷启动快照；
- 窗口创建原因；
- 主页跨站子窗口与主页窗口的关系；
- 每个窗口的明确搜索来源；
- 启动恢复的一次性决策状态。

### 3.7 无痕边界已经成立

当前无痕使用 AndroidX WebKit Profile 代际：

- 同一批活动无痕窗口共享一个代际 Profile；
- 最后一个无痕窗口关闭后退休该代际；
- 下次冷启动删除退休代际 Profile；
- 无痕不写普通浏览历史和搜索历史；
- 普通与无痕窗口不能相互转换。

新增恢复功能必须继续遵守该边界，不能把无痕 URL、标题、窗口顺序、搜索来源或缩略图写入磁盘。

### 3.8 生命周期不能成为唯一写入点

`MainActivity.onStop()` 当前只记录进程后台状态和播放器生命周期。Android 进程可能在没有完整退出
回调的情况下被终止，因此窗口快照不能只依赖 `onStop()` 或 `onDestroy()`。快照必须随运行时状态
变更增量写入，生命周期回调只请求一次额外刷新。

## 4. 统一术语与不变量

### 4.1 术语

| 术语 | 定义 |
| --- | --- |
| Browser Runtime | 唯一 `StandardBrowserSessionTools`、session registry、Browser Host 和活动 WebView 所有权 |
| 产品窗口 | 出现在窗口总览、计数和 AI `browser_tabs` 中的一个 WebSession |
| 弹窗目标解析器 | 只用于解析 `_blank/window.open()` 目标的临时 WebView；不注册为产品窗口 |
| 配置主页根 | `WebSessionBrowserSettings.homeUrl` 及其已确认重定向结果 |
| 同站 | 两个 HTTP(S) URL 的 `topPrivateDomain` 相同；IP、localhost 或无可注册域名主机按规范化 host 精确比较 |
| 未关闭窗口 | 最近一次成功快照中仍存在，且用户没有显式关闭的普通产品窗口 |
| 冷启动恢复 | 当前进程尚未应用恢复决策，且 Browser Runtime 尚未建立新的人工浏览状态 |
| 设置会话 | 一次从设置入口开始、携带来源和 route stack 的导航上下文 |

### 4.2 不变量

1. `StandardBrowserSessionTools` 始终是唯一 Browser Runtime。
2. 每个 WebSession 只绑定一个不可变 `WebSessionProfile`。
3. 网页导航默认改变当前窗口，不默认增加产品窗口。
4. 产品窗口只能由明确的创建原因产生。
5. 设置来源页由原 Shell/AI 状态继续持有，不复制第二份页面状态。
6. 系统 Back、标题返回和同一页面的返回按钮必须进入同一个 dispatcher。
7. 无痕状态永不进入恢复快照。
8. 恢复只重建 URL 级窗口，不声称恢复跨进程 WebView 历史。
9. 恢复设置全部关闭时，不保留浏览恢复文件。
10. Browser Home、AI 工具和设置页不能各自实现一套窗口恢复判断。

## 5. 设置导航实现

### 5.1 用显式设置会话替代单级 child

已新增单一状态所有者：

```kotlin
data class KiyoriSettingsNavigationState(
    val sessionId: String,
    val origin: KiyoriSettingsOrigin,
    val routes: List<KiyoriSettingsRoute>,
    val presentation: KiyoriSettingsPresentation,
)
```

实际来源：

```kotlin
enum class KiyoriSettingsOrigin {
    BOTTOM_NAVIGATION,
    BROWSER_HOME,
    AI_HOST,
    EXTERNAL_BROWSER_PRESENTATION,
}
```

实际展示状态：

```kotlin
enum class KiyoriSettingsPresentation {
    PRIMARY_ROOT,
    SOURCE_OVERLAY,
    SUSPENDED_FOR_BROWSER_WORKSPACE,
    OPERIT_ROUTE_DETAIL,
}
```

`origin` 只用于约束返回和验证，不复制 Browser Home、AI route 或底部导航的真实状态。

### 5.2 实际路由

```kotlin
enum class KiyoriSettingsRoute {
    HOME,
    BROWSER,
    BROWSER_HOME_CUSTOMIZATION,
    BROWSER_PLUGIN_PERMISSIONS,
    BROWSER_TEXT_SIZE,
    BROWSER_PASSWORD_MANAGER,
    DOWNLOAD,
    PLAYER,
    AD_BLOCK_OVERVIEW,
    AD_BLOCK_URL_RULES,
    AD_BLOCK_ELEMENT_RULES,
    AD_BLOCK_ALLOW_LIST,
    AD_BLOCK_SUBSCRIPTIONS,
}
```

Operit/AI 设置详情继续由 `AppRouterState` 渲染，但必须绑定同一次设置会话的 `sessionId`，不再把
`RouteEntrySource.KIYORI_SETTINGS` 当作足以恢复来源的唯一信息。

### 5.3 三个主要入口

#### 底部导航进入设置

```text
primaryDestination = SETTINGS_HOME
settingsSession = {
  origin = BOTTOM_NAVIGATION
  presentation = PRIMARY_ROOT
  routes = [Home]
}
```

返回顺序：

```text
设置子页
→ 设置分类页
→ 设置首页
→ 软件首页
```

“设置首页”是底部设置入口的完成边界。只有用户在设置首页再次按系统 Back，才回软件首页。

#### 浏览器菜单进入设置

```text
primaryDestination = BROWSER_HOME
settingsSession = {
  origin = BROWSER_HOME
  presentation = SOURCE_OVERLAY
  routes = [Home]
}
```

返回顺序：

```text
设置子页
→ 设置分类页
→ 设置首页
→ 原 Browser Home
→ 原活动网页和 WebView 历史保持不变
```

#### AI 对话左抽屉进入设置

```text
primaryDestination / softwareHomePage = 原 AI owner
routerState = 原 AI route stack
settingsSession = {
  origin = AI_HOST
  presentation = SOURCE_OVERLAY
  routes = [Home]
}
```

返回顺序：

```text
设置子页
→ 设置分类页
→ 设置首页
→ 原 AI 页面和原 Router 栈
```

打开设置时不得 `resetTo(AiChat)`，也不得在离开设置时无条件选择软件首页。

#### 外部浏览器 presentation 进入设置

后台 indicator 或 Browser Host 通过现有
`com.kiyori.action.OPEN_BROWSER_SETTINGS` 拉起 MainActivity 时，保留该 Intent 常量作为兼容入口，
但内部语义改为：

1. 恢复同一个 Browser Home owner；
2. 建立 `EXTERNAL_BROWSER_PRESENTATION` 设置会话；
3. 初始 route 为 `[Home]`；
4. 最终返回原 Browser Home，而不是以 Settings Home 作为新的永久 owner。

### 5.4 Back 统一优先级

| 优先级 | 状态 | 结果 |
| --- | --- | --- |
| 1 | 当前设置页有确认框、选择面板、编辑草稿或删除对话框 | 关闭或请求确认 |
| 2 | 当前 Operit 设置详情可 pop | pop 一层 Operit route |
| 3 | 浏览器插件工作台有内部 route 或未保存编辑 | 交给 Browser Host 处理 |
| 4 | `settingsSession.routes.size > 1` | pop 一个设置 route |
| 5 | 设置首页为来源覆盖层 | 关闭设置会话，恢复 Browser/AI 来源 |
| 6 | 设置首页为底部主页面 | 回软件首页 |

标题返回和系统 Back 不得分别调用 `closeChild()`、`subPageName = null` 或 `routerState.pop()`；
它们都调用同一个 `requestSettingsBack()`。

实际返回宿主按 `KiyoriSettingsPresentation` 唯一确定：

| presentation | 系统 Back owner |
| --- | --- |
| `PRIMARY_ROOT` | Shell |
| `SOURCE_OVERLAY` | Shell |
| `OPERIT_ROUTE_DETAIL` | App Router |
| `SUSPENDED_FOR_BROWSER_WORKSPACE` | Browser Host |

`isKiyoriSettingsBackOwnedByShell()` 是 Shell reducer 与 Shell `BackHandler` 的共同判断。
实际 `BackHandler` 的注册位置也必须与视觉宿主一致：

- Shell 设置处理器注册在 Browser/AI 宿主之后、具体 Shell 设置页面之前，覆盖
  `PRIMARY_ROOT / SOURCE_OVERLAY`，并让页面内确认框和选择面板保持最高优先级；
- Operit 设置处理器注册在 AI Host 内、Browser Host 之后、具体 Operit 页面之前，只覆盖
  `OPERIT_ROUTE_DETAIL`；
- App 与 Shell 根处理器在活动设置会话期间全部让位；
- `SUSPENDED_FOR_BROWSER_WORKSPACE` 只由 Browser Host 处理。

该顺序同时覆盖设置首页、网页浏览器、文件下载器、视频播放器、广告拦截器及其全部子页，以及
账号连接、AI 助手、语音服务、界面定制、数据管理和后续 Operit 深层页。否则视觉上位于前景的
设置页仍可能被底层 Browser/AI 更晚注册的处理器越过，错误进入软件首页或原 AI 根页。

#### 2026-08-18 Browser 来源设置返回二次实测修正

目标设备复测确认 AI 左抽屉来源已经正确，但 Browser 菜单来源仍存在稳定的两步错误序列：
账号连接、AI 助手和语音服务等 Operit 分类第一次系统 Back 没有可见变化，第二次直接退到软件首页；
除“网页浏览器”外的 Shell 设置子页也可能绕过设置首页。前一轮只修正 AI Host 与设置 overlay 的
`zIndex`，没有覆盖 Browser 首次动态创建后 `BackHandler` 注册顺序变化，因此“已完全修复”的
结论无效。

源码取证确认根因是返回所有权重叠：

- `KiyoriBrowserHome` 在 presentation lease 存在时始终启用系统 Back，并长期保留 Browser
  WebView 与页面历史；
- Shell 设置与 Operit 设置的 host 回调虽然按 presentation 切换 `enabled`，但它们通常在
  Browser 创建前就已注册；后创建的 Browser 回调会先收到系统 Back；
- “网页浏览器”页面自己注册了更晚的页面级 Back，所以它恰好正常；其他页面的差异与目标设备
  现象一致；
- AppContent 返回动画会短暂继续组合旧 route，“用户偏好”旧页面的未保存确认也必须随
  `LocalIsCurrentScreen` 失去 Back 所有权。

本次修正门禁：

1. [DONE] `KiyoriAppShell` 明确计算 Browser 是否拥有系统 Back，不再依赖回调注册时间；
2. [DONE] 设置首页、任一 Shell 设置子页和 `OPERIT_ROUTE_DETAIL` 可见时，Browser 根、搜索、
   书签和历史子树统一禁用 Back；`SUSPENDED_FOR_BROWSER_WORKSPACE` 明确重新交给 Browser；
3. [DONE] `AppRouterState.pop()` 与离开设置 session 时的 `SOURCE_OVERLAY` 恢复由同一生产事务
   提交；五个 Operit 分类测试直接执行真实 Router push/pop，而不是手工拼接理想恢复结果；
4. [DONE] 缓存转场中的用户偏好页仅在当前 route 启用未保存确认 Back；
5. [DONE] AI Host 前景层级继续按 `KiyoriSettingsPresentation` 决定，恢复出的设置首页不会被
   保留 AI route 遮挡；
6. [DONE] 不重置或改写 AI Router，不修改 Browser WebSession、当前网页、窗口返回目标或网页
   历史；
7. [DONE] 专项与完整 JVM、architecture、formal readiness、文档链接、差异检查和 Debug APK
   均已通过；目标设备仍保持 `verification_pending`。

本次本地证据：

- `KiyoriShellStateTest 67/67`；五个 Operit 分类直接使用 `AppRouterState.navigate()` 和生产
  `popKiyoriRouterBackStack()`，验证第一次 Back 恢复 `SOURCE_OVERLAY + 设置首页`，第二次
  `KiyoriShellState.handleBack()` 才回到原 Browser owner；
- 完整 `:app:testDebugUnitTest` 为 `230 suites / 1379 tests`，失败、错误和跳过均为 `0`；
- architecture `phase=m03`、formal readiness、Markdown 本地链接
  `errors=0 / warnings=0` 和 `git diff --check` 通过；
- `:app:assembleDebug --no-daemon --console=plain` 为
  `232 actionable tasks: 23 executed, 209 up-to-date`，零失败；
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`494168730` bytes，
  SHA-256 `A8EE926FCD4C68B6B4B50DB689112936376ADFA53ED5577D8BD21174A0C155ED`；
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、唯一
  `com.ai.assistance.operit.ui.main.MainActivity` launcher、arm64-only、Android Debug V2
  单 signer 和 16 KB ZIP 对齐通过。

#### 2026-08-18 软件首页 Full-Screen Search Back owner 崩溃修正

软件首页搜索 child 恢复可见后，现场崩溃堆栈确认它复用了
`WebSessionBrowserSearchScreen`，但该 Shell child 不位于 `KiyoriBrowserHome` 的 retained
Browser provider 内。搜索组件此前直接读取 strict
`LocalWebSessionBrowserSystemBackEnabled`，因此在首帧组合时抛出
`IllegalStateException`，而不是进入全屏搜索页。

当前修正把 `systemBackEnabled` 设为 `WebSessionBrowserSearchScreen` 的必填宿主参数：

- Full-Screen Search 作为当前可见 Shell child 显式传入 `true`；
- Browser Home 内搜索显式传入共享 Browser CompositionLocal owner；
- strict CompositionLocal 不恢复隐式值，Browser 根、搜索、书签和历史仍共享同一 owner；
- JVM 源码结构合同锁定两个生产宿主及 strict Local；Compose Android smoke test 直接在没有
  Browser provider 的测试宿主中组合搜索页。

修正后的本地证据为专项 `85/85`、完整 App JVM `239 suites / 1404 tests`、AndroidTest
Kotlin/Java 编译、architecture `phase=m03`、formal readiness 和规定 Debug 构建通过。APK 为
`467107608` bytes，SHA-256
`67F8F4D73367981591A2C0C73C25CB4F3B698C658238C682DA040E8E4533B16D`；目标设备点击、IME、
系统 Back 和 Browser 来源设置返回仍保持 `verification_pending`。

#### 2026-08-18 设置 surface 返回转场残影修正

目标设备继续复测发现，两类设置入口与两类详情宿主组成稳定的反相四象限：

| 设置入口与详情宿主 | 返回时的 Settings overlay 变化 | 旧结果 |
| --- | --- | --- |
| 底部入口 + Shell 设置详情 | `true -> false` | 退出层读取已恢复的 `HOME`，与 Primary Root 设置首页重复绘制 |
| 底部入口 + Operit 设置详情 | `false -> false` | 正常 |
| Browser/AI 来源 + Shell 设置详情 | `true -> true` | 正常 |
| Browser/AI 来源 + Operit 设置详情 | `false -> true` | 设置首页重新执行 `fadeIn + slideIn` |

这组现象证明根因不在九个设置页面自身，也不能只由 Browser WebView、Back owner 或
`AppContent` route 转场解释。`KiyoriSettingsRoute` 与
`KiyoriSettingsPresentation` 会在同一状态事务中变化；当 Settings 与 Shell child 共用
`AnimatedVisibility` 时，动画退出内容可以读取新 route，来源恢复也会把最终设置首页误当成
新进入的 child。

本次冻结并实现以下宿主不变量：

1. Shell child `AnimatedVisibility` 只承载 `KiyoriShellState.child`，当前即
   Full-Screen Search，并保留原 `fade + vertical slide`；
2. Settings surface 不参加任何 Shell child enter/exit，直接在 `zIndex(12f)` 以最终不透明状态
   呈现；
3. `BOTTOM_NAVIGATION + PRIMARY_ROOT + HOME` 只由 `KiyoriPrimaryRootPage` 绘制设置首页；
4. `SOURCE_OVERLAY + HOME` 和 Shell 设置详情只由直接 Settings surface 绘制；
5. `OPERIT_ROUTE_DETAIL` 只由 App Router 绘制，返回事务恢复 presentation 后再直接显示设置首页；
6. 继续复用现有设置主题边界、Shell/Operit/Browser Back owner、AI Host 前景规则、
   `popKiyoriRouterBackStack()`、唯一 Browser Runtime 与 WebSession，不增加延迟、截图、
   第二状态源或逐页特殊分支。

自动测试必须同时锁定六个代表状态都不进入 animated Shell child host，并保留原 Settings
surface 四象限。目标设备仍需覆盖三类入口、系统 Back、标题返回、九个设置根、深层子页、
浅深主题和快速重复返回；完成前保持 `verification_pending`。

本次本地证据：

- `KiyoriShellStateTest 69/69`；完整 `:app:testDebugUnitTest` 为
  `239 suites / 1405 tests`，失败、错误和跳过均为 `0`；
- `:app:compileDebugAndroidTestKotlin`、`:app:compileDebugAndroidTestJavaWithJavac`、
  architecture `phase=m03`、formal readiness 与 `git diff --check` 通过；
- `:app:assembleDebug --no-daemon --console=plain` 为
  `232 actionable tasks: 22 executed, 210 up-to-date`，零失败；
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`467107608` bytes，
  SHA-256 `BFA17A75523E74EF7C0A44651443D52D660D9EDB15EB458314A3317E76DBB0F6`；
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、唯一
  `com.ai.assistance.operit.ui.main.MainActivity` launcher、arm64-only、51 个 `.so` 无重复
  basename、Android Debug V2 单 signer 和 16 KB ZIP 对齐通过。

### 5.5 Operit Router 集成

为 `RouteEntry` 增加明确的设置导航上下文，例如：

```kotlin
val navigationContextId: String?
```

从设置打开 Operit 页面时：

- `source = RouteEntrySource.KIYORI_SETTINGS`
- `navigationContextId = settingsSession.sessionId`
- 不改变 settings route stack
- `presentation = OPERIT_ROUTE_DETAIL`

设置详情内部继续调用统一 `navigateTo()` 时，默认 `RouteEntrySource.DEFAULT` 必须继承当前
`KIYORI_SETTINGS + sessionId`。显式传入的其他 source 或 context 保持调用方语义。该继承覆盖
AI 助手、界面定制、数据备份、语音服务及其全部后续子页面，确保深层页持续显示返回按钮，并按
原进入顺序逐层 pop，而不是把导航按钮重新解释为 AI 抽屉菜单。

同一 `settingsSessionId` 的 Router 条目形成连续设置链。返回时：

1. 当前条目与前一条目都属于同一设置 session 时，只 pop 当前 Operit 子页，继续显示父级；
2. 当前条目是该 session 的首个分类根条目时，pop 后才恢复 Shell 设置 presentation；
3. 恢复后的设置首页或 Shell 分类页继续沿 `KiyoriSettingsNavigationState.routes` 返回；
4. 不调用现有 `returnFromKiyoriAiSettings()` 的无条件 `selectPrimary(SETTINGS_HOME)`。

因此 `模型与 API → MNN 模型下载`、`提示词 → 标签市场/人设卡/聊天历史`、
`界面定制 → 语言/主题/全局显示/布局调整`、`数据备份 → 备份/聊天历史` 和
`语音服务 → TextToSpeech` 均逐级返回，不会从任一深层页直接恢复设置首页。

### 5.6 浏览器插件工作台返回令牌

插件中心、脚本诊断和脚本详情属于 Browser Runtime，不应复制到设置 Router。新增：

```kotlin
data class BrowserWorkspaceReturnToken(
    val settingsSessionId: String,
    val settingsRoutes: List<KiyoriSettingsRoute>,
    val sourceBrowserSessionId: String?,
    val initialPluginRoute: WebSessionBrowserPluginRoute,
)
```

打开流程：

1. 保存 token；
2. 将设置会话标记为 `SUSPENDED_FOR_BROWSER_WORKSPACE`；
3. 显示唯一 Browser Home presentation；
4. 在 Browser Host 打开目标插件 route；
5. 不关闭、不重建、不刷新来源 WebSession。

离开插件工作台时：

1. Browser Host 先处理编辑草稿、插件 route 和抽屉；
2. 抽屉完全关闭后消费 token；
3. 恢复同一个设置会话及原 route stack；
4. 浏览器来源仍显示原网页；AI/底部设置来源仍回原设置上下文。

这会替代当前“先执行一次 onBack 再打开抽屉”的顺序特例。

### 5.7 保存与重建

- 设置会话和 route stack 进入 `KiyoriShellStateSaver`。
- `sessionId`、`origin`、`presentation` 和 route 参数必须可序列化。
- 页面临时编辑状态继续由对应 route 的 `rememberSaveable` 或 ViewModel 持有。
- 旋转、窗口尺寸变化和 Activity 重建不得把深层设置页重置为设置首页。
- 如果恢复数据包含未知 route，按显式无效状态处理并记录错误，不猜测最近页面。

### 5.8 已删除的旧状态

当前业务路径已经删除：

- `KiyoriShellState.childBackTarget`
- `openNestedChild()`
- 设置页面对 `KiyoriShellChild` 的依赖
- `KiyoriBrowserSettingsPage.subPageName`
- `runBrowserPluginRouteFromSettings()`
- `returnFromKiyoriAiSettings()` 的旧返回语义
- 只验证两级 child 的旧测试

`KiyoriShellChild` 当前只承载非设置的 `FULL_SCREEN_WEB_SEARCH`，不再作为设置栈。

## 6. 浏览器窗口与网页导航实现

### 6.1 产品窗口创建原因

新增不可变创建原因：

```kotlin
enum class BrowserWindowCreationReason {
    MANUAL_NEW_WINDOW,
    OPEN_IN_NEW_WINDOW,
    SOFTWARE_HOME_SEARCH,
    PROFILE_BOUNDARY_SEARCH,
    AI_EXPLICIT_CREATE,
    HOME_CROSS_SITE_USER_NAVIGATION,
    RESTORED_NORMAL_WINDOW,
}
```

只有这些原因可以进入 `createSessionTabOnMain()`。普通网页导航和弹窗回调不能直接创建产品窗口。

### 6.2 统一决策表

| 场景 | 产品行为 |
| --- | --- |
| 非主页中的同站普通链接 | 当前窗口加载 |
| 非主页中的跨站普通链接 | 当前窗口加载 |
| 非主页中的用户 `_blank` | 解析目标后在当前窗口加载 |
| 非主页中的用户 `window.open(url)` | 解析目标后在当前窗口加载 |
| 非用户手势脚本弹窗 | 拒绝，不创建窗口 |
| 配置主页根中的同站用户链接 | 当前主页窗口加载，可按历史回到主页 |
| 配置主页根中的跨站用户链接 | 创建同 Profile 产品窗口，保留主页窗口 |
| 配置主页加载过程中的服务端重定向 | 当前主页窗口继续加载 |
| 配置主页中的无用户手势脚本跳转 | 当前主页窗口继续加载 |
| 窗口总览“新建窗口” | 创建当前选择 Profile 的主页窗口 |
| 用户明确“在新窗口打开” | 创建来源 Profile 的窗口 |
| 软件首页提交搜索 | 创建明确搜索窗口 |
| 浏览器内搜索，Profile 与当前窗口相同 | 当前窗口加载 |
| 浏览器内搜索，Profile 与当前窗口不同 | 创建请求 Profile 的窗口 |
| AI `browser_tabs action=create` | 创建明确 Profile 的窗口 |
| AI `browser_navigate` | 当前活动窗口加载 |
| 外部 HTTP(S) Intent，已有活动窗口 | 当前活动窗口加载 |
| 外部 HTTP(S) Intent，无活动窗口 | 创建一个普通窗口 |

### 6.3 弹窗目标解析器

`onCreateWindow()` 不再返回注册到 `sessions` 的产品 WebView。实现保留
`setSupportMultipleWindows(true)` 以观察真实 popup 请求，并设置：

```text
javaScriptCanOpenWindowsAutomatically = false
```

`onCreateWindow()` 处理：

1. `isUserGesture == false`：拒绝；
2. 创建一个临时目标解析 WebView；
3. 绑定与父窗口相同的 WebSession Profile；
4. 不注册到 `sessions`、`sessionOrder`、窗口计数或 AI tab 列表；
5. 不安装 Kiyori JS bridge、下载 bridge、用户脚本或凭据能力；
6. 保持 WebView 默认禁用 JavaScript，只接收 Chromium 已提交的主框架目标；
7. 在首次 HTTP(S) 主框架目标出现时消费该导航；
8. 将 URL 交给统一窗口决策器；
9. 停止并销毁解析 WebView；
10. 超时、空目标、`about:blank` 内容型弹窗或非稳定 URL 记为明确拒绝。

固定参考 `kiyori-android@24a2dfa9` 已使用临时 WebView 捕获 popup URL 后调用当前控制器
`loadUrl()`，证明该方向可以避免把网页 `_blank` 直接映射为产品窗口；当前 Kiyori 需要在此基础上
增加 Profile、主页跨站和 Browser Runtime 所有权。

### 6.4 同站判定

项目已经直接依赖 OkHttp，并在广告拦截策略中使用 `HttpUrl.topPrivateDomain()`。抽取统一
`BrowserSiteIdentity`：

```text
HTTP(S) 且存在 topPrivateDomain
→ 使用小写 topPrivateDomain

IP、localhost 或无可注册域名主机
→ 使用规范化小写 host

无有效 HTTP(S) host
→ 不参与主页跨站自动新窗
```

该规则能把 `www.example.com`、`shop.example.com` 和 `example.com` 识别为同站，也能正确处理
`example.co.uk`。不要继续复用只比较父子 host 的 `isThirdPartyBrowserNetworkRequest()`。

### 6.5 自定义主页跨站窗口关系

只有同时满足以下条件才创建主页跨站窗口：

1. 当前 session 已确认处于配置主页根；
2. 请求为 HTTP(S) 主框架；
3. 请求由真实用户手势发起；
4. 目标与配置主页不是同站；
5. 当前没有未完成的导航确认或广告标记锁定；
6. 目标不是用户脚本安装 URL。

新窗口记录：

```kotlin
creationReason = HOME_CROSS_SITE_USER_NAVIGATION
openerHomeSessionId = parentSession.id
profile = parentSession.profile
```

新窗口中的返回顺序：

```text
当前页 N
→ 当前页 N-1
→ 首个跨站目标
→ 关闭该自动子窗口
→ 激活 openerHomeSessionId 对应的主页窗口
```

只有当 opener 仍存在、Profile 相同且仍是当前配置主页根时才执行最后一步。opener 已被关闭或已离开
主页时，当前窗口按普通规则导航到当前配置主页。

手动创建窗口、搜索窗口和 AI 创建窗口不使用该 opener 关系。

### 6.6 普通与无痕规则

- 普通窗口创建普通子窗口。
- 无痕窗口创建无痕子窗口。
- Profile 不能在导航中改变。
- 关闭最后一个无痕窗口后继续退休当前无痕代际。
- 进程内无痕窗口继续出现在窗口总览和 AI `browser_tabs`。
- 冷启动不创建、导入或提示恢复无痕窗口。
- 默认新窗口 Profile 在新进程中回到普通模式，不从磁盘恢复上次无痕选择。

### 6.7 网页历史边界

- 同一进程内继续使用 WebView Back/Forward List。
- 当前窗口的普通同站、跨站和 popup 目标都进入该窗口原有历史。
- `clearHistory()` 只在明确完成配置主页根导航后执行。
- 系统 Back 继续先处理 Browser Host 临时层，再处理网页历史，再处理主页根。
- 浏览器顶栏返回仍是 App Shell 返回，不等同于网页 Back。
- 冷启动 URL 恢复后没有进程死亡前的完整 Back/Forward List；第一次网页 Back 返回当前配置主页。

## 7. 四项新设置

### 7.1 页面分组

浏览器设置已经从五组十五项调整为六组十九项：

| 分组 | 项目 |
| --- | --- |
| 网页插件与脚本 | 保持现有 4 项 |
| 主页与导航 | 网页主页自定义、返回不重载、滑屏前进后退 |
| 启动与窗口 | 恢复上次的搜索结果、询问是否恢复页面、保留多窗口 |
| 网页显示 | 保持现有 2 项 |
| 网站权限与数据 | 保持现有 4 项 |
| 音视频嗅探 | 保持现有 3 项 |

### 7.2 设置字段与默认值

```kotlin
val swipeHistoryNavigationEnabled: Boolean = false
val restoreLastSearchResultEnabled: Boolean = false
val askBeforeRestoringPagesEnabled: Boolean = false
val retainMultipleWindowsEnabled: Boolean = false
```

现有安装缺少新 key 时全部读取为 `false`。不增加旧字段别名。

### 7.3 实际文案与语义

#### 滑屏前进后退

说明：

> 开启后从屏幕左右边缘滑动可在当前网页历史中前进或后退。

#### 恢复上次的搜索结果

说明：

> 开启后打开浏览器会自动打开上次未关闭的搜索结果页。

#### 询问是否恢复页面

说明：

> 开启后打开浏览器会先询问是否打开上次未关闭的页面。

#### 保留多窗口

说明：

> 开启后保留上次未关闭的普通窗口；无痕窗口不会写入恢复记录。

### 7.4 设置变化时的磁盘规则

| 设置变化 | 处理 |
| --- | --- |
| 四项全部关闭 | 删除普通窗口恢复文件 |
| 仅开启滑屏前进后退 | 不创建恢复文件 |
| 开启询问恢复 | 保存可询问的普通活动页面投影 |
| 开启恢复搜索结果 | 保存仍属于活动普通窗口的最近搜索候选 |
| 开启保留多窗口 | 保存全部普通窗口投影 |
| 关闭保留多窗口但其他恢复开关仍开 | 立即重写为更小的候选范围 |
| 关闭最后一个恢复相关开关 | 删除恢复文件 |

## 8. 滑屏前进后退

### 8.1 手势方向

- 左边缘向右：网页 Back。
- 右边缘向左：网页 Forward。
- 在配置主页根执行 Back 手势时不退出 Browser Home。
- 没有对应历史时不触发动作。

### 8.2 手势所有者

实现没有在 Compose 层覆盖整个 WebView，而是把 AndroidView 宿主容器改为
`BrowserGestureNavigationFrameLayout`：

- `WebSessionWebViewHost` 继续只负责附着唯一活动 WebView；
- 父容器只在边缘、水平意图明确后拦截；
- 父容器开始拦截时由 Android View 体系向 WebView 发送取消；
- 手势完成后调用现有 Browser Runtime Back/Forward；
- 不建立第二份历史。

### 8.3 初始参数

当前可测试常量为：

```text
边缘激活宽度：48dp
最小提交距离：72dp
水平/垂直优势比：1.5
最小快速滑动速度：800dp/s
```

这些数值已由 JVM 测试锁定方向、取消和只提交一次的状态机；真机仍需验证冲突与手感。

### 8.4 禁用条件

以下任一状态成立时不接管网页手势：

- 设置关闭；
- 搜索页、搜索引擎面板、窗口总览或任一 Browser sheet 打开；
- JS 对话框、下载确认、页面源码退出确认或插件编辑退出确认打开；
- 广告标记、网页元素菜单或文本选择打开；
- 多指触控、缩放手势或无障碍触摸探索开启；
- 当前方向没有 WebView 历史、主页返回或 opener 主页关系；
- Browser Home 尚未获得前台 presentation lease。

### 8.5 系统手势导航

- 不设置大面积 `systemGestureExclusionRects`。
- 让系统继续拥有最外侧系统 Back 手势。
- 应用手势使用较宽的内部边缘带，用户可以从系统边缘稍内侧开始。
- 必须分别验证 Android 三键导航和系统手势导航。

## 9. 普通窗口恢复模型

### 9.1 为什么只恢复安全投影

跨进程长期序列化 `WebView.saveState(Bundle)` 会把实现细节、历史和潜在大对象带入持久层，也不适合
长期产品数据库。恢复只保存明确、可审计的 URL 级投影。

恢复后：

- 重新创建 WebView；
- 重新加载 URL；
- 不恢复 DOM、表单、滚动、脚本执行内存或跨进程 WebView 历史；
- 页面登录状态仍由普通 Profile 自身 Cookie/WebStorage 决定。

### 9.2 实际 schema

```kotlin
@Serializable
data class BrowserSessionRecoveryEnvelope(
    val snapshot: BrowserSessionRecoverySnapshot? = null,
)

@Serializable
data class BrowserSessionRecoverySnapshot(
    val schemaVersion: Int = 1,
    val snapshotId: String,
    val capturedAt: Long,
    val activeWindowId: String,
    val windows: List<BrowserSessionRecoveryWindow>,
)

@Serializable
data class BrowserSessionRecoveryWindow(
    val windowId: String,
    val currentUrl: String,
    val title: String,
    val createdAt: Long,
    val lastActivatedAt: Long,
    val creationReason: BrowserWindowCreationReason,
    val openerHomeWindowId: String? = null,
    val lastSearch: BrowserSessionSearchRecovery? = null,
)

@Serializable
data class BrowserSessionSearchRecovery(
    val query: String,
    val engineId: String,
    val source: KiyoriBrowserSearchSource,
    val requestedUrl: String,
    val resolvedResultUrl: String,
    val submittedAt: Long,
)
```

窗口列表顺序本身就是恢复顺序。所有持久化 window 都隐含 `profile = NORMAL`；schema 中不出现
Profile 字段，因此无法编码无痕窗口或无痕元数据。

### 9.3 持久化位置

新增唯一 `BrowserSessionRecoveryStore`：

- 文件位于 `context.noBackupFilesDir`；
- 使用 DataStore 的原子更新能力和自定义严格 serializer；
- 不进入 Android 自动备份；
- 不与 `WebSessionHistoryStore` 共用 key；
- 不与 `WebSessionBrowserSettingsStore` 共用文件；
- 不写窗口缩略图。

### 9.4 允许持久化的数据

- 普通窗口稳定 ID；
- 普通窗口相对顺序；
- 当前 URL；
- 页面标题；
- 活动普通窗口；
- 创建时间与最后激活时间；
- 受控创建原因；
- 主页跨站 opener 关系；
- 明确搜索来源。

### 9.5 禁止持久化的数据

- 任意无痕窗口字段；
- Cookie、Authorization、请求头；
- 表单值、DOM、网页正文；
- 下载响应体和网络日志；
- 用户脚本运行内存；
- WebView、Bitmap、Bundle；
- 页面截图或窗口缩略图；
- 密码、剪贴板和网站凭据；
- 外部应用 Intent 内容。

### 9.6 URL 与字段校验

- 只接受 HTTP(S) URL；固定默认主页哨兵按现有主页合同处理。
- URL 上限 `8192` 字符、标题上限 `512` 字符、query 上限 `2048` 字符，窗口上限 `64`。
- `windowId` 必须唯一，窗口列表顺序直接作为恢复顺序。
- `activeWindowId` 和 opener 必须指向 envelope 中存在的窗口。
- `creationReason` 只接受当前 schema 枚举。
- JSON 不允许未知字段。解析失败、关系成环或字段越界时拒绝完整快照并清空恢复文件，不创建部分窗口。

### 9.7 搜索来源不能靠 URL 猜测

在以下入口写入明确来源：

- 软件首页提交真实文本搜索；
- Browser Home 搜索页提交搜索；
- 点击搜索历史记录重新打开结果。

对直接输入 URL、收藏、历史或普通网页链接，不写搜索来源。软件首页天气入口当前会构造真实搜索
query，因此继续按搜索来源记录。

`KiyoriWebSearchRequest` 已包含 `engineId` 和 capability-level `KiyoriBrowserSearchSource`；
`WebSession` 记录最近搜索候选。搜索结果首次加载和重定向期间更新 `resolvedResultUrl`。

“恢复上次的搜索结果”选择最近一次仍属于未关闭普通窗口、且当前仍停留在已加载结果页的明确
搜索候选。搜索请求创建时立即写入候选，首次加载和重定向期间更新稳定结果 URL；结果页完成后，
一旦该窗口开始导航到另一页面，立即清除搜索恢复资格并重写最小投影，不保留已经离开的旧结果 URL。

显式关闭窗口时必须同步删除该窗口的搜索候选。

## 10. 启动恢复状态机

### 10.1 一次性状态

已新增进程内状态：

```kotlin
sealed interface BrowserLaunchRestorationState {
    data object Uninitialized
    data class WaitingForDecision(
        val plan: BrowserLaunchRestorationPlan,
        val snapshotId: String,
    )
    data class Applying(val snapshotId: String)
    data object Ready
}
```

同一进程只对同一个 `snapshotId` 决策一次。从 Browser Home 离开再回来不重复询问。

### 10.2 候选选择顺序

```text
保留多窗口开启且存在普通窗口
→ 全部普通窗口

否则，恢复搜索结果开启且存在有效搜索候选
→ 最近搜索结果窗口

否则，询问恢复开启且存在活动普通页面
→ 活动普通页面

否则
→ 当前配置主页
```

### 10.3 开关组合矩阵

| 询问恢复 | 保留多窗口 | 恢复搜索结果 | 冷启动行为 |
| --- | --- | --- | --- |
| 关 | 关 | 关 | 打开当前配置主页 |
| 关 | 关 | 开 | 自动恢复最近仍未关闭的普通搜索结果；没有候选则打开主页 |
| 关 | 开 | 任意 | 自动恢复全部普通窗口；搜索结果自然包含在窗口状态中 |
| 开 | 关 | 关 | 询问是否恢复上次活动普通页面 |
| 开 | 关 | 开 | 优先询问最近搜索结果；没有搜索候选时询问活动普通页面 |
| 开 | 开 | 任意 | 询问是否恢复全部普通窗口，并显示窗口数量 |

“询问是否恢复页面”优先于自动恢复。不能先发起网络加载再显示询问。

### 10.4 启动步骤

1. `StandardBrowserSessionTools` 初始化 Profile 管理和恢复元数据，但不自动创建窗口。
2. Browser Home acquisition 调用 `prepareBrowserHumanLaunch()`；确认结果调用
   `resolveBrowserHumanLaunch()`。
3. 当前进程已有 live session 时直接使用 live runtime，不导入旧快照。
4. runtime 为空时读取并验证普通窗口快照。
5. 根据开关计算候选。
6. 需要询问时先显示不可重复的恢复对话框。
7. 用户选择“恢复”后按顺序创建普通 WebSession，再激活目标窗口。
8. 用户选择“不恢复”后先删除旧快照，再创建当前配置主页。
9. Browser workspace 已经显示时直接渲染工作台，不为底层 Browser Home 创建配置主页窗口。
10. live runtime 建立后由增量写入器接管新的快照。

### 10.5 人工启动请求

当前没有建立第二个 `BrowserHumanLaunchIntent` 队列。直接进入空 Browser Home 时执行恢复状态机；
软件首页搜索、外部 URL 或用户/AI 明确创建窗口会直接建立 live runtime，并以该显式新状态取代旧
磁盘候选。Browser plugin workspace 是独立例外：若工作台已由设置 return token 打开，
`KiyoriBrowserHome` 直接显示该工作台，不执行普通人工启动恢复，也不创建隐藏主页窗口。

AI 工具不触发人工恢复询问，也不从磁盘导入旧页面。AI 在人工恢复前显式创建 live session 时，
该进程以新的 live runtime 为准，旧快照被视为已被新运行状态取代。

### 10.6 恢复对话框

对话框只提供明确决策：

- 标题：`恢复上次页面？`
- 单页面摘要：标题和安全裁剪后的 host
- 多窗口摘要：`上次有 N 个普通窗口未关闭`
- 主操作：`恢复`
- 次操作：`不恢复`

系统 Back、点击遮罩或 Activity 离开按“不恢复本次启动”处理，不能留下半初始化 Browser Home。

### 10.7 写入时机

在主线程生成不可变普通窗口投影，交给单一 IO writer 串行写入。实现使用原子 revision 与单一
writer，将并发变化合并到最新 revision，不为每个网络请求写文件。

触发点：

- 普通窗口创建、关闭和激活；
- 窗口顺序改变；
- 主框架页面提交和同文档 URL 改变；
- 标题稳定更新；
- 搜索来源创建、更新和删除；
- 主页跨站 opener 关系创建或解除；
- 恢复设置变化；
- `MainActivity.onStop()` 请求一次最新 revision 刷新。

以下事件不触发写入：

- 子资源网络请求；
- console/network log 更新；
- 缩略图刷新；
- 媒体候选变化；
- 无痕窗口任何变化；
- Compose 重组和 presentation 转挂。

## 11. 实现阶段对账

阶段 A 至阶段 G 均已完成本地实现和自动验证；设备验收保持独立。

### 阶段 A：纯策略与测试骨架 `[LOCAL DONE]`

- 新增设置 route、origin、presentation 和 stack reducer。
- 新增窗口创建决策、同站 identity 和 opener 返回策略。
- 新增恢复候选矩阵、schema 校验和设置组合策略。
- 先写纯 JVM 测试，不接 UI。

完成信号：

- 所有策略不依赖 Android View；
- 当前行为和新目标的差异被测试明确锁定；
- 没有第二份 Browser Runtime 状态。

### 阶段 B：设置导航栈 `[LOCAL DONE]`

- 在 `KiyoriShellState` 接入 `KiyoriSettingsNavigationState`。
- 改造 `KiyoriAppShell` 和 `KiyoriApp` 的设置入口、Back 和 Operit Router bridge。
- 统一 `PRIMARY_ROOT / SOURCE_OVERLAY / OPERIT_ROUTE_DETAIL /
  SUSPENDED_FOR_BROWSER_WORKSPACE` 四种展示状态的返回宿主。
- 让 Shell/Operit 设置处理器分别在 Browser/AI 宿主之后、具体设置页面之前注册。
- 只在底部来源的设置首页显示底部五按钮，全部分类页、子页和 Browser/AI 来源隐藏。
- 让 Operit 设置详情的默认子导航继承同一 `KIYORI_SETTINGS` session。
- 同 session 的 Operit 深层页面逐级 pop，只在离开分类根条目时恢复 Shell 设置页面。
- 将浏览器设置内部子页面迁入统一 route stack。
- 将广告拦截器内部实际页面迁入设置 route。
- 删除 `childBackTarget`、`openNestedChild()`、`subPageName` 和旧返回函数。

完成信号：

- 底部、浏览器、AI 三个入口逐级返回测试全部通过；
- 标题返回与系统 Back 使用同一 reducer；
- Activity 重建后 route stack 不丢失。

### 阶段 C：插件工作台返回令牌 `[LOCAL DONE]`

- 新增 `BrowserWorkspaceReturnToken`。
- 改造插件中心、脚本诊断、权限详情的打开和退出。
- Browser Host 在工作台关闭后恢复设置会话。
- 删除 `runBrowserPluginRouteFromSettings()`。

完成信号：

- Browser/AI/底部设置三个来源都能进入插件工作台并返回原浏览器设置 route；
- 来源 WebSession 不刷新、不重建。

### 阶段 D：窗口创建与 popup `[LOCAL DONE]`

- 抽取 `BrowserSiteIdentity`。
- 新增 `BrowserWindowCreationReason` 和 opener 元数据。
- 用临时目标解析器替换 `createPopupSessionOnMain()` 的直接调用。
- 将主页跨站真实用户导航接入统一决策器。
- 接入 opener 主页返回规则。

完成信号：

- 普通同站、跨站、`_blank` 和 `window.open()` 不增加产品窗口；
- 主页跨站用户导航只增加一个同 Profile 窗口；
- 自动弹窗不增加产品窗口；
- 当前窗口历史顺序正确。

### 阶段 E：滑屏导航 `[LOCAL DONE]`

- 新增 Browser Gesture 容器和纯手势 tracker。
- 接入 Browser Runtime Back/Forward。
- 接入所有禁用条件、无障碍和系统手势边界。

完成信号：

- 设置关闭时零行为变化；
- 方向、阈值、取消、多指和一次提交测试通过；
- 主页根 Back 手势不退出 Browser Home。

### 阶段 F：普通窗口恢复 `[LOCAL DONE]`

- 新增 recovery schema、store、writer、candidate resolver 和 launch coordinator。
- 改造软件首页搜索和外部 URL 请求，使其先进入 launch coordinator。
- 接入一次性询问对话框。
- 接入普通窗口恢复和设置变化清理。
- 保持无痕完全不落盘。

完成信号：

- 六种开关矩阵全部通过；
- 显式关闭窗口后不再恢复；
- 进程死亡后只恢复 URL 级普通窗口；
- 无痕快照扫描为零。

### 阶段 G：文档、全量复核和 Debug APK `[LOCAL DONE / DEVICE VERIFICATION PENDING]`

- 同步 `CONTEXT.md`、`README.md`、`docs/TODO/README.md` 和相关浏览器文档。
- 更新设置组、导航和窗口语义测试。
- 串行运行必要测试和 `:app:assembleDebug`。
- 核验 Debug APK。
- 真机验收保持独立状态。

本地证据：

- 项目 Python 门禁 `220/220` 通过；formal readiness 与 architecture
  `phase=m03` 通过。
- 完整 App JVM 汇总为 `229 suites / 1362 tests`，失败、错误和跳过均为 `0`；
  AndroidTest Kotlin/Java 编译通过。
- `:app:lintDebug` 为 `BUILD SUCCESSFUL`，最终可见 `23 warnings`：
  `GradleDependency 5 / NewerVersionAvailable 15 / UseKtx 3`。popup 解析器新增的
  `SetJavaScriptEnabled` 已通过保持 JavaScript 默认关闭消除；未新增 suppress，也未扩大
  `lint-baseline.xml`。
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 16s`，
  `232 actionable tasks: 23 executed, 209 up-to-date`；唯一 launcher 与
  `verifyDebugPlayerRuntimePackaging` 通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，宿主生成时间
  `2026-08-18 07:01:32 +08:00`，大小 `494168730` bytes，SHA-256
  `1A03F576811C482F4F1CB366532564B340553DCA2267CAB394B86F5067630E2F`。
- APK 为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，
  `debuggable=true`，唯一 `MainActivity` launcher；Android Debug V2 单 signer 和
  `zipalign -c -P 16 -v 4` 通过。
- APK 仅含 `arm64-v8a`，`51` 个 `.so` basename 无重复；包含
  `liboperit_ripgrep.so` 与 `assets/operit_shell_exec`，不含 `libsudo.so`。`51` 个
  `.so` 加 shell launcher 共 `52/52` 个 ELF64/AArch64，`153` 个 `PT_LOAD` 的最小
  alignment 为 `0x4000`，分布为 `0x4000 × 151 / 0x10000 × 2`。

2026-08-18 设置返回回归修正证据：

- 完整 App JVM 为 `230 suites / 1376 tests`，失败、错误和跳过均为 `0`；
- Shell 矩阵覆盖底部、Browser、AI 三类来源与 12 条完整路径：浏览器分类及四个子页、
  文件下载器、视频播放器、广告拦截器分类及四个子页；每条路径均验证逐级返回设置首页和原来源；
- Operit 矩阵覆盖账号连接、AI 助手、语音服务、界面定制、数据管理五个分类根与
  GitHub 账号、用户偏好、模型/API、MNN 下载、功能模型、提示词、人设卡、分句回复、自定义表情、
  标签市场、上下文、工具授权、Token 统计、外部 HTTP、主题、全局显示、布局、聊天历史、备份、
  语言和 TextToSpeech 等全部现有子页面；
- `check_formal_readiness.py --require-main`、
  `check_architecture_boundaries.py --require-main` 和 `git diff --check` 均通过；
- ARCH020/021/024 的精确源码 snapshot 已按
  [M-04 根组合与 Shell 精确实施清单](../kiyori_architecture_refactor/19_m04_root_composition_and_shell_manifest.md)
  记录的旧值、新值和维护原因更新，检查器规则与 import 许可未改变；
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 50s`，
  `232 actionable tasks: 22 executed, 210 up-to-date`；
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-18 15:35:38 +08:00`，大小 `494168730` bytes，SHA-256
  `98B59B9BA408ABC3373AAD17BDE3141671B9314EDA778E07D26C6F92EC0D12D0`；
- APK 为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一
  `MainActivity` launcher，Android Debug V2 单 signer，`zipalign -P 16` 通过。

## 12. 实际主要文件

### Shell 与设置

- `app/src/main/java/com/kiyori/app/shell/KiyoriShellState.kt`
- `app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt`
- `app/src/main/java/com/kiyori/app/KiyoriApp.kt`
- `app/src/main/java/com/kiyori/app/startup/KiyoriMainIntentDecoder.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriBrowserSettingsPage.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriAdBlockSettingsPage.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriApplicationSettingsPages.kt`
- `KiyoriSettingsNavigationState.kt`
- `com/kiyori/capability/settings/navigation/KiyoriSettingsRoute.kt`

### Browser Runtime

- `BrowserPresentationCoordinator.kt`
- `StandardBrowserSessionTools.kt`
- `BrowserWebViewSupport.kt`
- `WebSessionBrowserHostState.kt`
- `WebSessionBrowserHost.kt`
- `WebSessionWebViewHost.kt`
- `WebSessionBrowserScreen.kt`
- `WebSessionProfile.kt`
- `WebSessionBrowserSettingsStore.kt`
- `WebSessionHistoryStore.kt`
- `BrowserNavigationPolicy.kt`
- `BrowserPopupTargetResolver`（位于 `BrowserWebViewSupport.kt`，不建立第二产品窗口类层）
- `BrowserGestureNavigationFrameLayout.kt`
- `BrowserGestureNavigationPolicy.kt`
- `BrowserSessionRecoveryModels.kt`
- `BrowserSessionRecoveryStore.kt`
- `BrowserSessionRecoverySupport.kt`

### 资源、测试与文档

- `app/src/main/res/values*/strings.xml`
- `KiyoriShellStateTest.kt`
- `KiyoriSettingsPagesTest.kt`
- `WebSessionProfilePolicyTest.kt`
- 新的设置 stack、窗口策略、popup、gesture、recovery 和 schema 测试
- 相关 AndroidTest
- `CONTEXT.md`
- `README.md`
- `docs/TODO/README.md`
- 相关浏览器专项文档

## 13. 测试矩阵

### 13.1 设置导航 JVM 测试

- 底部设置：首页 → 浏览器 → 主页自定义 → 浏览器 → 首页。
- 浏览器设置：首页 → 广告拦截订阅 → 首页 → 原 Browser Home。
- AI 设置：首页 → Operit 设置详情 → 首页 → 原 AI route。
- Browser/AI 来源的设置首页隐藏底部导航。
- 标题返回和系统 Back 生成相同 transition。
- route-local 对话框先于 route pop。
- 旋转保存和恢复完整 route stack。
- 未知 route 数据被拒绝。
- 插件工作台 token 在三种来源下恢复同一设置 route。
- AI 在设置打开期间改变 Browser tab 后，退出设置使用当前 runtime，不恢复陈旧 WebView 引用。

### 13.2 窗口策略 JVM 测试

- 非主页同站与跨站都返回 `CURRENT_SESSION`。
- 主页同站用户链接返回 `CURRENT_SESSION`。
- 主页跨站用户链接返回 `CREATE_CHILD_SESSION`。
- 主页服务端重定向和无手势脚本跳转返回 `CURRENT_SESSION`。
- 非用户弹窗返回 `REJECT`。
- `topPrivateDomain` 覆盖根域、兄弟子域、多段公共后缀、IP 和 localhost。
- 普通/无痕 Profile 原样继承。
- opener 有效时关闭自动子窗口并激活主页。
- opener 无效时当前窗口返回当前配置主页。
- 手动窗口和搜索窗口不使用 opener 规则。

### 13.3 Popup 测试

- `_blank` 目标只创建临时解析器，不增加 registry 数量。
- 首个 HTTP(S) 目标只提交一次。
- 解析器销毁后延迟回调不会导航。
- 空目标、自动 popup、内容型 `about:blank` 和超时被拒绝。
- 用户脚本安装 URL 仍进入现有安装流程。
- 广告标记和外部导航策略继续先于 popup 决策。

### 13.4 手势测试

- 左边缘向右只触发一次 Back。
- 右边缘向左只触发一次 Forward。
- 距离不足、速度不足、纵向占优、反向和取消不触发。
- 多指和无障碍触摸探索不触发。
- Browser sheet、搜索、文本选择、广告标记和对话框打开时不触发。
- 主页根 Back 手势不退出 Browser Home。
- opener 子窗口 Back 能回到原主页窗口。

### 13.5 恢复 JVM 测试

- 六种设置矩阵。
- 搜索候选不存在时的明确行为。
- 多窗口顺序、活动窗口和稳定 ID。
- 显式关闭窗口后快照删除。
- 恢复后 writer 用 live runtime 替换旧快照。
- “不恢复”后旧 `snapshotId` 不再询问。
- 同一进程重复进入 Browser Home 不重复询问。
- 所有恢复开关关闭后文件删除。
- schema 字段越界、重复 ID、无效 opener、循环关系和未知版本被拒绝。
- 无痕窗口无法构造持久化 snapshot。

### 13.6 Android/Robolectric 测试

- Activity 重建后的设置 stack 和来源恢复。
- DataStore 原子更新、文件位置和删除。
- MainActivity `onStop()` 请求最新 revision。
- Browser Home acquisition 在询问决定前不创建 WebView 网络加载。
- 恢复 N 个普通 WebView 后只激活指定窗口。
- 无痕 Profile 初始化、退休和恢复 store 完全独立。

### 13.7 真机测试

至少覆盖：

- Android 三键导航和系统手势导航；
- 手机、平板、横屏和分屏；
- 自定义主页同站、兄弟子域、跨站、服务端重定向和脚本跳转；
- `_blank`、用户 `window.open()`、自动广告 popup；
- 普通窗口、无痕窗口和混合窗口顺序；
- 手动关闭窗口、强制停止应用、系统回收进程和重新打开；
- 四项设置所有组合；
- 恢复询问的恢复、不恢复、系统 Back 和 Activity 重建；
- Browser/AI/底部设置入口的完整返回链；
- 插件中心、脚本详情和未保存编辑；
- AI `browser_tabs` 与人工窗口仍共享同一 runtime。

## 14. 实现阶段验证命令

按改动阶段先运行定向测试，再扩展验证。最终至少包括：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\check_markdown_links.py `
  --base <base-commit> `
  --candidate <candidate-commit>
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
git diff --check
```

未提交方案文档先运行等价的本地相对链接扫描，形成候选提交后再运行正式链接脚本。实际测试应按
阶段使用 `--tests` 收窄首轮反馈。Gradle 必须串行执行，且不能与其他任务共用同一构建通道。

## 15. 风险与显式处理

### 15.1 WebView popup 目标不可直接从 onCreateWindow 参数获得

使用临时目标解析器，并对无法得到稳定 HTTP(S) URL 的 popup 明确拒绝。不能再次把解析 WebView
注册为产品窗口。

### 15.2 自定义主页重定向误判

只有已确认在主页根、主框架、真实用户手势和跨站同时成立才创建产品窗口。主页加载重定向留在当前
窗口。

### 15.3 恢复设置扩大隐私范围

三个恢复设置默认关闭；只有用户开启至少一个恢复相关设置才写入普通窗口投影。文件位于
`noBackupFilesDir`，无痕和敏感运行时数据始终排除。

### 15.4 进程死亡时写入不完整

状态变化时增量写入，DataStore 负责原子文件替换。恢复只读取完整且通过 schema 校验的 envelope。

### 15.5 恢复 URL 与当前主页配置变化

恢复窗口继续打开用户明确保留的 URL；窗口后退根使用启动时最新 `homeUrl`。旧 opener 关系只有在
对应窗口仍是最新配置主页根时有效。

### 15.6 AI 与人工恢复顺序

AI 不读取磁盘恢复页。人工 Browser Home 尚未决策前，AI 显式创建 live session 会建立本进程新的
runtime；后续人工进入时使用 live runtime，不把旧磁盘页混入 AI 已建立的状态。

### 15.7 手势与网页横向交互冲突

设置默认关闭，使用边缘父容器拦截、方向阈值和完整禁用条件。必须用轮播、地图、视频进度、
Canvas 和文本选择页面真机验证。

### 15.8 并行任务冲突

当前工作树另有未跟踪的广告拦截并行文档
`docs/TODO/kiyori_browser_product_completion/15_adblock_compiled_runtime_cache.md`。本专项不得修改、
暂存或提交该文件；共享权威文档只写入本任务的设置导航、窗口和恢复合同，提交前必须以精确
allowlist 审计 staged tree。

## 16. 完成标准

只有同时满足以下条件才能把本专项实现标记为完成：

- 设置三个来源的逐级返回全部通过自动测试；
- Browser/AI 来源最终恢复原页面，不跳到软件首页；
- popup 不再直接创建产品窗口；
- 普通网页同站和跨站跳转都保持当前窗口；
- 自定义主页真实用户跨站跳转只创建一个同 Profile 子窗口；
- opener 主页返回语义通过；
- 滑屏功能只在开关开启和允许状态下工作；
- 三个恢复设置的组合矩阵通过；
- 无痕恢复文件审计为零；
- 文档、字符串和测试同步；
- 正式开发准备门禁、相关测试、`git diff --check` 和 Debug APK 构建通过；
- 设备上的返回、手势、popup、普通/无痕和冷启动恢复完成实测。

在设备验收完成前，交付状态只能是 `verification_pending`，不能把本地构建或 JVM 测试描述为真实
网页和设备行为已经通过。

## 17. 研究依据

### 17.1 当前仓库

- `KiyoriSettingsNavigationState.kt`：设置 `sessionId`、来源、route stack、presentation 和
  Browser workspace return token。
- `KiyoriSettingsRoute.kt`：设置能力层的稳定 route 枚举。
- `KiyoriShellState.kt`：Shell owner、设置会话、来源返回目标和 save/restore。
- `KiyoriAppShell.kt`：Shell Back、设置覆盖层和 Browser Home 挂载。
- `KiyoriApp.kt`：AI Router、设置入口、软件首页搜索和来源恢复。
- `KiyoriBrowserSettingsPage.kt`：六组 19 项浏览器设置、统一设置 route 与插件工作台跳转。
- `BrowserPresentationCoordinator.kt`：唯一 Browser Runtime presentation 适配器。
- `StandardBrowserSessionTools.kt`：共享 session registry、窗口顺序和 AI 浏览器工具。
- `BrowserWebViewSupport.kt`：WebView、临时 popup 目标解析、窗口决策、历史和恢复触发。
- `WebSessionBrowserHostState.kt`：Browser Host Back 优先级。
- `WebSessionBrowserSettingsStore.kt`：现有浏览器设置唯一 owner。
- `WebSessionHistoryStore.kt`：浏览历史、搜索历史和搜索引擎。
- `WebSessionProfile.kt`：普通/无痕 Profile 和无痕代际。
- `BrowserAdBlockPolicy.kt`：项目现有 `HttpUrl.topPrivateDomain()` 用法。
- `KiyoriActivityLifecycle.kt`：唯一 Android callback owner 与 Activity stopped 事实订阅。
- `BrowserSessionRecoveryModels/Store/Support.kt`：普通窗口 schema、DataStore 和启动恢复状态机。

### 17.2 固定旧版参考

- `D:\10_Project\kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766`
- `BrowserWindowRepository.kt`：普通窗口持久化、无痕只在内存、窗口列表与浏览历史分离。
- `BrowserWebViewController.kt`：使用临时 WebView 捕获 popup 目标并回到当前控制器导航。

旧版只提供行为证据；当前 Kiyori 继续使用唯一 `StandardBrowserSessionTools` 和 Compose/App Shell
架构，不复制旧 Activity、单 WebView 控制器或旧窗口仓库。

### 17.3 第一手技术资料

- [Android WebSettings.setSupportMultipleWindows](https://developer.android.com/reference/android/webkit/WebSettings#setSupportMultipleWindows(boolean))
- [Android WebChromeClient.onCreateWindow](https://developer.android.com/reference/android/webkit/WebChromeClient#onCreateWindow(android.webkit.WebView,boolean,boolean,android.os.Message))
- [Android WebView.saveState](https://developer.android.com/reference/android/webkit/WebView#saveState(android.os.Bundle))
- [AndroidX ProfileStore.deleteProfile](https://developer.android.com/reference/androidx/webkit/ProfileStore#deleteProfile(java.lang.String))
- [OkHttp 4.12.0 对 HttpUrl.topPrivateDomain 的公共后缀说明](https://github.com/square/okhttp/blob/master/docs/changelogs/changelog_4x.md#version-4120)
