# 自问自答、实现清单与验证

## 自问自答

### AI 展开网页时，左上角返回会不会让 AI 失去当前页面？

不会。overlay owner 的顶栏返回只执行 `setExpanded(false)`，保留 indicator、session、active WebView 和 AI snapshot。网页历史后退只由底栏后退或 AI `browser_navigate_back` 执行。

### 从 AI Home 顶栏打开浏览器，再点击顶栏返回会去哪？

回到 AI Home。Shell 在进入 Browser Home 时记录 `AI_HOME` 来源；退出 app presentation 后 indicator 恢复，网页没有 reload。若从普通根入口进入，则回到 Software Home。

### “退出浏览器”会不会关闭标签或让 AI 找不到 session？

不会。它只移除浏览器展示层和 indicator，保留 `sessions/sessionOrder/activeSessionId`。AI 后续 `browser_tabs` 仍能读取原有标签；真正清理 session 只由窗口总览清空或 AI `browser_close_all` 完成。

### 为什么主菜单的未接通按钮必须进入说明页？

这是用户本轮明确要求的“先全部落实入口”。说明页能证明点击、路由、Back 和转场都真实存在，同时明确说明当前 runtime 边界，避免用假功能、Toast 或共享 Cookie 冒充能力。

### 主菜单和子抽屉为什么要拆成两个组件？

主菜单是固定高度的四行网格，拖动会破坏第五按钮的确定性布局；历史、下载、UA、网络日志等子页需要阅读长内容，因此继续使用 Hidden/Partial/Expanded。两个 host 分离后，route 转场不会把主菜单的固定尺寸和子页拖动状态混在一起。

### 为什么所有抽屉都要 `fillMaxWidth`？

用户要求左右贴边；旧实现用宽屏最大宽度居中，导致两侧缝隙。主菜单和子抽屉都改为全宽，卡片内部再使用 padding 控制密度。

### 无痕窗口为什么仍显示在窗口页？

本轮要复刻普通/无痕 selector 的页面结构，但当前 WebView 使用系统单例 CookieManager，没有 profile 字段，也没有 cache/WebStorage 隔离。无痕 tab 显示真实说明和空状态，不创建共享 Cookie 的伪无痕。

### 点击搜索框会不会刷新当前页面？

不会。搜索页是 presentation overlay；只有提交、打开搜索记录或明确打开当前 URL 才调用 `openUrlOnMain`。活动 WebView 不重建、不 reload。

### 搜索记录与访问历史是否混在一起？

不会。搜索记录由 `WebSessionHistoryStore` 的搜索 key 持久化，访问历史继续由原 history key 持有；搜索页提供两列搜索记录和独立清空动作。

### 人和 AI 是否仍操作同一个窗口？

是。人类窗口页选择、关闭或新建 session 后，AI 读取同一 registry；AI 导航、刷新、点击、网络请求和源码读取后，Host 通过同一 projection 更新。没有第二套 UI runtime。

### Back、IME、旋转和 overlay 的顺序是否可解释？

系统 Back 先处理 dialog、搜索、窗口/抽屉，再按 owner 处理 overlay 收缩、WebView history 和 App Shell 退出；搜索关闭清理 focus；旋转只更新布局，保留 draft、route、window mode 和 session。

## 预计修改文件

- `ui/main/shell/KiyoriShellState.kt`、`KiyoriAppShell.kt`、`OperitApp.kt`
- `ui/main/components/AppContent.kt`、`ui/features/chat/screens/AIChatScreen.kt`
- `ui/features/browser/appshell/KiyoriBrowserHome.kt`
- `core/browser/presentation/BrowserPresentationCoordinator.kt`
- `core/tools/defaultTool/websession/browser/WebSessionBrowserHostState.kt`
- `core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt`
- `core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt`
- `ui/features/websession/browser/WebSessionBrowserScreen.kt`
- `ui/features/websession/browser/WebSessionBrowserTopBar.kt`
- `ui/features/websession/browser/chrome/WebSessionBrowserMenuDrawer.kt`
- `ui/features/websession/browser/chrome/WebSessionBrowserBottomDrawer.kt`
- `ui/features/websession/browser/chrome/WebSessionBrowserTabOverview.kt`
- 新增能力说明页/窗口模式状态和对应 Compose contract 测试
- `app/src/main/res/values/strings.xml` 及现有语言资源

## 验证顺序

1. 使用项目 `.venv`：`\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
2. `git diff --check`
3. 运行受影响的 Kotlin/JVM 单测、Shell 状态测试和 chrome layout/route contract 检查
4. `\.gradlew.bat :app:assembleDebug --no-daemon --console=plain`
5. 核对 APK 绝对路径、大小、SHA-256、包名、版本、Android Debug V2 签名和 ZIP 16KB 对齐
6. 不运行 Release、安装、ADB、MuMu 或设备自动化；真机视觉、拖动、系统 Back、IME、旋转、网页刷新和 AI 并发操控保持 `verification_pending`

## 交付

- 源码、测试和文档改动保持未提交、未推送
- 任务日记记录 Goal、源码证据、验证证据和剩余风险，并以 `verification_pending` 关闭
- 最终报告提供 Debug APK、SHA-256、Git 状态、跳过的设备边界和日记路径
