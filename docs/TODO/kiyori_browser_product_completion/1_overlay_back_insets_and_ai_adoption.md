# 悬浮浏览器系统 Back、系统栏与 AI 接管

## 2026-07-28 方案 A 替换结果

本文件下方的完整 expanded overlay 设计与历史验证只保留为迁移证据，不再是当前架构合同。Kiyori
尚未发布，旧方案已由 MainActivity/App Shell 唯一完整浏览器 presentation 直接替换：

- `KiyoriBrowserHome` 和 `WebSessionBrowserHost.BrowserContent(...)` 只在 Activity Compose tree 中挂载
- `WindowManager` 浏览器控制面固定为同一活动 WebView 的 1×1、不可见、不可触摸、不可聚焦 background anchor
- 最小 indicator 继续显示下载/外部打开提示；普通点击通过
  `MainActivity.ACTION_OPEN_KIYORI_BROWSER` 进入现有 Browser Home
- `WebSessionBrowserHost` 不再创建完整 overlay Compose tree、播放器 Surface、expanded layout、cutout/IME
  策略或 overlay Back owner
- App Shell 与 background anchor 转换先从旧 host 明确移除 WebView、确认 parent 为空，再把同一实例加入目标 host
- 文本选择操作条已进入 Browser Home 的 Activity Compose tree，不再创建额外系统 overlay
- `BrowserPageRegistry.overlayExpanded` 没有序列化或外部消费者，已随旧 expanded presentation 删除；
  `browser_*` 工具名、参数、标签索引、稳定 `session_id` 和响应主体保持不变

### 本地门禁

- 浏览器定向 JVM：`46/46`，零失败、零错误、零跳过
- `:app:compileDebugKotlin --no-daemon --console=plain`：PASS
- formal readiness：PASS
- `git diff --check`：PASS
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务，零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 17:27:04 +08:00`，
  `468746121` 字节，SHA-256
  `84D7067F361FB9A582DE649DC787B50849AC5D122B6813D2264CF6704F3C7739`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2；
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- 未安装 APK，人工与 AI 共用窗口、后台 anchor、indicator、Back 和目标设备崩溃复测仍为
  `verification_pending`

## 历史旧实现（已由方案 A 删除）

- `WebSessionBrowserHost.handleBack()` 已覆盖对话框、外部打开确认、抽屉、搜索、overlay 收缩和网页历史
- App Shell 通过 Compose `BackHandler` 调用该方法
- `TYPE_APPLICATION_OVERLAY` 的根 View 没有 Back dispatcher 或 `KEYCODE_BACK` 接线，因此手机系统 Back 不会到达状态机
- 展开窗口只设置 `FLAG_LAYOUT_IN_SCREEN`，根 View 为透明背景，顶部背景未稳定延伸到状态栏和挖孔区域
- 人工 UI 和 AI 已使用 `StandardBrowserSessionTools.getSharedInstance()`，但工具描述和回归测试没有证明人工窗口可被发现和接管

## 历史目标实现（已废止）

### Back 输入

- Android 13 及以上从 overlay 根 View 的 `OnBackInvokedDispatcher` 注册 callback
- Android 12 及以下由可聚焦根 View 接收 `KEYCODE_BACK` 的按键抬起事件
- 两条平台入口只调用 `WebSessionBrowserHost.handleBack()`，不复制返回优先级
- overlay 最小化、转为 App Shell presentation 或销毁时同步注销 callback
- 输入法显示时由系统先处理 IME；浏览器 callback 只处理应用收到的 Back

### 系统栏与背景

- 展开窗口使用允许绘制到物理屏幕边缘的 overlay layout contract，并明确 display cutout 模式
- 浏览器根背景由 `WebSessionBrowserScreen` 的单一 background surface 绘制到顶端；顶栏内容继续应用 status-bar inset
- 状态栏图标明暗根据浏览器 chrome 的实际主题设置，不在顶栏添加假色条
- 悬浮 indicator 和最小化的 1x1 host 不改变现有坐标、拖动和触摸合同

### AI 接管

- `browser_tabs list` 明确列出所有人工和 AI 创建的窗口、活动窗口、URL、标题和 Profile
- 中英文工具描述明确要求 AI 先使用 `browser_tabs list` 发现既有人工窗口，再用 `select`、`browser_snapshot`、点击和输入操作同一窗口
- 增加纯 JVM 状态测试，证明 UI 创建、选择、导航后工具 registry 看到相同 session ID；不通过创建第二套 runtime 来修复
- 如工具调用时 presentation 尚未挂载，只挂载现有 session；禁止新建重复窗口或覆盖人工页面

## 自问自答

### 为什么不在 Activity 的 Back callback 中处理 overlay？

overlay 可以在 Activity 不可见时独立存在，Activity dispatcher 不是它的窗口输入 owner。Back 必须在 overlay ViewRoot 接收，再进入共享 host 状态机。

### 系统 Back 是否应该先让网页后退？

overlay 展开时不应该。它的第一退出语义是收缩为悬浮球，网页历史由底栏后退或 AI `browser_navigate_back` 控制，避免用户隐藏窗口时打断 AI 当前页面。

### AI 是否需要新的“接管窗口”工具？

不需要。`browser_tabs select` 已是接管动作，真实缺口是可发现性、工具说明和生命周期验证。新增同义工具只会形成第二套协议。

## 预计文件

- `core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt`
- `core/tools/defaultTool/standard/StandardBrowserSessionTools.kt`
- `core/tools/defaultTool/websession/browser/BrowserToolSupport.kt`
- `core/config/SystemToolPromptsInternal.kt`
- Browser Runtime 与 host 的定向测试
- `CONTEXT.md`、相关浏览器 TODO 和字符串资源

## 验收

- overlay 展开态系统 Back 调用一次后变为 indicator，session、URL 和网页历史不变
- overlay 内抽屉或搜索打开时，系统 Back 先关闭最上层状态
- Android 13+ 与旧 Back 分发均有代码级测试或可验证契约
- `browser_tabs list` 能列出人工创建窗口，随后 snapshot、点击和输入继续使用同一 session
- Debug APK 构建、提交和远端 SHA 门禁通过；真机状态栏和实体 Back 保持待验证

## 历史实施结果（已废止）

- `BrowserOverlayWindowPolicy` 固定展开态的 physical-edge flags、最小化态的不可聚焦/不可触摸 flags，并把旧版 Back 按键判定提取为纯逻辑
- `WebSessionBrowserHost` 在 Android 13+ 随展开/最小化和 attach/detach 生命周期注册或注销 `OnBackInvokedCallback`；Android 12 及以下由 overlay 根 View 的 `dispatchKeyEvent()` 处理未取消的 Back 抬起事件
- 两个平台入口都只调用现有 `handleBack()`，没有复制对话框、外部打开确认、抽屉、搜索、overlay 收缩和网页历史的优先级
- 展开窗口加入 `FLAG_LAYOUT_NO_LIMITS`、short-edges cutout 和 Android 11+ `setFitInsetsTypes(0)`；Compose 根背景继续负责绘制，顶栏继续使用 status-bar inset
- `browser_tabs list` 通过 `BrowserTabDiscoveryFormatter` 输出共享 Browser Runtime 声明、稳定 `session_id`、标题、URL、索引和活动状态；中英文 browser 工具描述要求先发现并选择人工窗口
- 定向 JVM 测试已覆盖窗口 flags、Android 13 Back 路由边界、旧版 Back 输入和可机器读取的 tab 列表

### 后续搜索页崩溃修正

悬浮 `ComposeView` 原先只安装 Lifecycle、ViewModelStore 与 SavedState owner，搜索页组合 `BackHandler` 时会因缺少 `OnBackPressedDispatcherOwner` 崩溃。`WebSessionOverlayLifecycleOwner` 现已持有真实 `OnBackPressedDispatcher` 并安装到 overlay View tree；Compose callback 未消费时才进入既有 `WebSessionBrowserHost.handleBack()`。Android 13 `OnBackInvokedCallback` 和 Android 12 及以下按键 Back 也通过同一 dispatcher 分发，不再绕过 Compose 搜索页的返回处理。

## 历史本地验证证据

- `:app:testDebugUnitTest --tests BrowserOverlayWindowPolicyTest --tests BrowserTabDiscoveryFormatterTest`：PASS
- `.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main`：PASS
- `git diff --check`：PASS
- `:app:assembleDebug --no-daemon --console=plain`：PASS，230 个任务，零失败
- APK：`app/build/outputs/apk/debug/app-debug.apk`，2026-07-24 22:53:33 +08:00，449465146 bytes，SHA-256 `D9FAC64CDB3B446B4018131E96D1B6B4C463B0E929F3D82158FAE2D78B0EE7DE`
- APK 元数据：`com.kiyori`，versionCode `45`，versionName `0.1.0`，minSdk `26`，targetSdk `34`
- Android Debug v2 签名：通过；ZIP 16 KB 对齐：通过
- 未运行 Release、安装、ADB、MuMu 或设备自动化；状态栏、挖孔、系统 Back 与人工窗口 AI 接管保持真机 `verification_pending`

[DONE]

第一里程碑的代码、文档、自动检查和 Debug 构建已完成。用户完成目标设备验收前，不把本地证据表述为真机行为已经通过。

## 2026-07-28 方案 A 首轮真机 presentation 崩溃修正

首轮 vivo Android 16 验收确认 Browser Home 显式退出完成
`APP_SHELL -> BACKGROUND_ANCHOR` 后，Compose `DisposableEffect.onDispose` 会再次释放同一
presentation。旧 coordinator 在确认 presentation owner 前先创建 background anchor，导致已经挂在
1×1 anchor `FrameLayout` 的活动 WebView 再次进入“必须已 detach”断言，触发
`IllegalStateException`。

本轮保持现有 `StandardBrowserSessionTools`、session registry、活动 WebView 和网页状态为唯一事实源：

- `BrowserAppPresentationLease` 通过一次性 release gate 保证显式退出和 Compose dispose 只有首个
  terminal action 生效
- `WebSessionBrowserHost` 先核对 presentation identity，再根据纯策略决定 no-op、detach、立即 attach
  或跨帧 attach；重复请求当前 owner 时不再转挂
- `APP_SHELL` 与 `BACKGROUND_ANCHOR` 跨 ViewRoot 转移严格执行 detach、确认 `parent == null`、
  移除旧 anchor window、等待 `Choreographer` 帧、连接同一 WebView
- Browser Home 活跃时不保留空 background anchor；窗口总览入口直接请求 Browser Home，不再预热一次
  无意义的后台挂载
- 转移过程不调用 `loadUrl`、`reload`，不重建 WebView，不增加第二浏览器 runtime 或导航状态

### 本地验证

- release gate、anchor policy 及关联浏览器状态 JVM 测试 `51/51` 通过；测试任务包含
  `:app:compileDebugKotlin`
- 旧预热入口和 coordinator 提前创建 anchor 均为 `0`；严格 null-parent 断言保留
- formal readiness、`git diff --check`、Debug 构建和
  `verifyDebugPlayerRuntimePackaging` 均通过
- 最终 APK：`2026-07-28 19:01:32 +08:00`，`455956807` 字节，SHA-256
  `8DD13C5D3AA81AEAF3102A341FAF485233FF50F1D14BC690C5A608FA3C96ABA6`
- 包名/版本、Android Debug v2 签名和 16 KB ZIP 对齐均通过

Java 重复释放异常已按可复现调用链修复。同期 RenderThread `fdsan` tombstone 只能证明
GraphicBuffer/Surface 分配链中止，不能证明业务根因；本轮已消除同帧重复跨 ViewRoot 转挂这一高风险
条件，仍需在原 vivo Android 16、同一网页上复测退出、AI 往返、indicator 恢复和悬浮播放。

[DONE - LOCAL / VERIFICATION_PENDING - DEVICE]

## 历史：2026-07-27 搜索页 Back owner 修正

- `:app:compileDebugKotlin`：PASS，overlay dispatcher owner 与 Compose `BackHandler` API 接线通过
- `BrowserOverlayWindowPolicyTest`：PASS
- `:app:assembleDebug --no-daemon --console=plain`：PASS
- 最终 APK SHA-256：`89CADC2171FC9B07202F9CB084CBC87F2A48ECB7AA17BBA830978C38AF5C924C`
- 未操作设备；悬浮搜索页打开、软键盘显示和系统 Back 的现场行为保持 `verification_pending`
