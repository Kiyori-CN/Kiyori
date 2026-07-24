# 自问自答、实现清单与验证

## 自问自答

### 点击搜索框会不会刷新当前页面？

不会。搜索页只是 Host presentation overlay；只有提交输入、打开搜索记录或明确选择当前网址动作才调用 Runtime 导航。活动 WebView 不重新创建、不 reload。

### 搜索引擎和搜索记录由谁持有？

`WebSessionHistoryStore` 持有持久化源，Compose 通过 Flow 读取。搜索草稿和是否显示搜索页属于 Host 临时状态；它们不能进入 AI tool protocol。

### AI 还能不能操作浏览器？

能。搜索提交最终进入现有 `openUrlOnMain`，新建/选择/关闭窗口仍调用 session registry；不在 UI 里创建第二个 WebView、第二个 Cookie 管理器或第二个活动 session。

### 关闭全部标签页会不会让 AI 找到旧标签？

不会。复用现有 `closeSession`，逐一从 `sessions` 和 `sessionOrder` 移除并重新选择活动 session；`browser_tabs` 与 UI 同源。

### 无痕窗口为什么不做？

因为当前 WebView 的 Cookie 使用系统单例，且没有 profile 字段。仅不写访问历史仍会共享 Cookie、缓存和 WebStorage，属于错误的隐私承诺。本轮不显示入口。

### Back、IME、旋转和 overlay 返回是否一致？

统一由 `WebSessionBrowserHost.handleBack()` 先处理对话、搜索、route 和抽屉，再处理 WebView history；搜索页清理焦点，尺寸变化只更新布局，不动 Runtime。

### 未接通的按钮怎么办？

不显示。没有占位页面、无动作按钮、伪造 toast、兼容开关或 fallback；在本 TODO 的能力边界中留下后续任务入口。

## 预计修改文件

- `core/tools/defaultTool/websession/browser/WebSessionBrowserHostState.kt`
- `core/tools/defaultTool/websession/browser/WebSessionHistoryStore.kt`
- `core/tools/defaultTool/websession/browser/BrowserAddressResolver.kt`
- `core/tools/defaultTool/websession/browser/BrowserToolSupport.kt`
- `core/tools/defaultTool/websession/browser/BrowserWebViewSupport.kt`
- `core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt`
- `ui/features/websession/browser/WebSessionBrowserScreen.kt`
- `ui/features/websession/browser/WebSessionBrowserTopBar.kt`
- `ui/features/websession/browser/WebSessionBrowserSearchScreen.kt`
- `ui/features/websession/browser/chrome/WebSessionBrowserToolbox.kt`
- `ui/features/websession/browser/chrome/WebSessionBrowserTabOverview.kt` 或新的窗口总览组件
- 新增搜索页、UA、网络日志和页面源码 Compose 组件
- `app/src/main/res/values/strings.xml` 及其现有翻译资源
- 对应 resolver、状态和 UI contract 测试

## 验证顺序

按项目门禁只执行与本轮风险相称的检查：

1. `.\\.venv\\Scripts\\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
2. `git diff --check`
3. 受影响的 Kotlin/JVM 单元测试或静态 contract 检查
4. `\\.gradlew.bat assembleDebug --no-daemon --console=plain`
5. 核对 Debug APK 路径、大小、SHA-256、包名和版本；不运行 Release、安装、ADB、MuMu 或设备自动化

构建通过只能证明本地 Debug 工程链路；顶栏视觉、抽屉拖动、窗口切换、Back、IME、旋转、真实网页刷新和 AI 并发操作继续标记 `verification_pending`，等待用户真机实测。

## 交付

- 本轮改动保持未提交、未推送
- 日记以 `verification_pending` 关闭，保留源码证据、Goal、构建证据和待实测清单
- 最终报告提供 Debug APK 绝对路径、SHA-256、Git 状态、未验证边界和日记路径
