# 悬浮浏览器系统 Back、系统栏与 AI 接管

## 旧实现

- `WebSessionBrowserHost.handleBack()` 已覆盖对话框、外部打开确认、抽屉、搜索、overlay 收缩和网页历史
- App Shell 通过 Compose `BackHandler` 调用该方法
- `TYPE_APPLICATION_OVERLAY` 的根 View 没有 Back dispatcher 或 `KEYCODE_BACK` 接线，因此手机系统 Back 不会到达状态机
- 展开窗口只设置 `FLAG_LAYOUT_IN_SCREEN`，根 View 为透明背景，顶部背景未稳定延伸到状态栏和挖孔区域
- 人工 UI 和 AI 已使用 `StandardBrowserSessionTools.getSharedInstance()`，但工具描述和回归测试没有证明人工窗口可被发现和接管

## 目标实现

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

## 实施结果

- `BrowserOverlayWindowPolicy` 固定展开态的 physical-edge flags、最小化态的不可聚焦/不可触摸 flags，并把旧版 Back 按键判定提取为纯逻辑
- `WebSessionBrowserHost` 在 Android 13+ 随展开/最小化和 attach/detach 生命周期注册或注销 `OnBackInvokedCallback`；Android 12 及以下由 overlay 根 View 的 `dispatchKeyEvent()` 处理未取消的 Back 抬起事件
- 两个平台入口都只调用现有 `handleBack()`，没有复制对话框、外部打开确认、抽屉、搜索、overlay 收缩和网页历史的优先级
- 展开窗口加入 `FLAG_LAYOUT_NO_LIMITS`、short-edges cutout 和 Android 11+ `setFitInsetsTypes(0)`；Compose 根背景继续负责绘制，顶栏继续使用 status-bar inset
- `browser_tabs list` 通过 `BrowserTabDiscoveryFormatter` 输出共享 Browser Runtime 声明、稳定 `session_id`、标题、URL、索引和活动状态；中英文 browser 工具描述要求先发现并选择人工窗口
- 定向 JVM 测试已覆盖窗口 flags、Android 13 Back 路由边界、旧版 Back 输入和可机器读取的 tab 列表

## 本地验证证据

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
