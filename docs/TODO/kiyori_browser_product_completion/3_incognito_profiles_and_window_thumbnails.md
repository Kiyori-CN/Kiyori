# 真无痕 Profile、窗口逻辑与网页缩略图

## 实现说明

- `WebSessionProfileManager` 在共享 Browser Runtime 初始化时检查 `WebViewFeature.MULTI_PROFILE`，清理 Kiyori 无痕前缀的异常退出遗留 Profile，并在 WebView 的 settings、JS bridge、userscript 与导航之前绑定 Profile
- 普通和无痕分别使用默认 CookieManager 与 AndroidX Profile CookieManager；`GM_cookie`、`GM_xmlhttpRequest` 的 Cookie/Set-Cookie、浏览器下载、popup 与 `GM_openInTab` 均跟随来源 WebSession Profile
- 同时存在的无痕窗口共享 `kiyori-incognito-session-v1`；最后一个无痕 WebView detach、destroy 后删除该 Profile。删除或绑定失败时将无痕标记为不可用，不创建普通窗口代替
- 全屏搜索页的无痕按钮修改 Browser Runtime 默认新窗口 Profile；窗口页的新建按钮使用当前 selector Profile；`browser_tabs create` 支持显式 `profile=normal|incognito`，未指定时使用 runtime 默认值
- 窗口页已删除大号“窗口”标题，顶部只保留普通/无痕 selector 与数量；网格沿用 `<600dp / 600–839dp / >=840dp` 的 2/3/4 列规则，底部返回、新建、清空只作用于当前 Profile
- 缩略图固定为 `320x180` 内存 Bitmap。导航开始会使旧图和未完成回调失效；页面完成、切换离开和打开窗口页时通过 `WebView.postVisualStateCallback` 请求已提交画面，不写磁盘、不改变持久化格式
- AI 仍可选择、snapshot、点击、输入和关闭无痕窗口；UI 与工具说明明确无痕隔离网站数据，但不隔离用户已经授权的 Kiyori AI 操作

## Profile 模型

新增明确的 `WebSessionProfile`：

- `NORMAL`：继续使用现有默认 WebView profile，保留已有 Cookie、WebStorage 和站点权限
- `INCOGNITO`：使用 AndroidX WebKit Multi-Profile 的 Kiyori 私有 profile

每个 `WebSession` 在构造时写入不可变 Profile。WebView 创建后、任何设置、JS bridge、userscript 或导航前完成 profile 绑定。普通与无痕窗口不能互相转换。

## 隔离生命周期

- 所有同时存在的无痕窗口共享一次无痕浏览会话 profile，便于同一无痕会话内登录和开新窗口
- 最后一个无痕 WebView 完成 detach、停止加载和 destroy 后删除私有 profile
- 应用进程初始化时删除带 Kiyori 无痕前缀的异常退出遗留 profile
- 删除 profile 前必须确认没有对应 WebView 存活，不以清全局 Cookie 代替 profile 删除
- 设备不支持 `WebViewFeature.MULTI_PROFILE` 时，无痕按钮和无痕新建动作禁用并解释原因

## 窗口页 UI

- 删除顶部大号“窗口”标题；状态栏下方第一行只有“普通窗口”和“无痕窗口”两个 selector
- selector 显示各自窗口数量和选中指示线
- 网格按窗口宽度使用 2、3 或 4 列；手机卡片保持可读标题、URL、Profile 标识和关闭按钮
- 底部固定返回、新建、清空三动作；新建使用当前 selector Profile，清空只清当前 selector 的窗口
- 关闭活动窗口后选择同 Profile 的相邻窗口；该 Profile 已空时切换到另一个仍有窗口的 Profile；全部为空则保持空总览

## 缩略图

- 每个 session 保存一张约 320x180 的内存缩略图，不写磁盘、不加入持久化格式
- 页面完成、切换离开活动窗口和打开窗口总览时请求刷新
- 使用 `WebView.postVisualStateCallback` 等待已提交画面后在主线程绘制，不使用固定延迟
- capture 失败时显示稳定的站点首字母与主题背景，不伪造旧截图
- 关闭 session 时释放其 Bitmap；限制尺寸和更新频率，避免大量窗口造成内存峰值

## AI 合同

- `browser_tabs list` 每个窗口输出 `profile=normal|incognito`
- `browser_tabs create` 增加可选 `profile`；未提供时使用当前新窗口默认 Profile
- AI 可 snapshot、点击、输入和关闭无痕窗口，因为它操作本应用内授权的同一 runtime
- 工具描述明确“无痕隔离网站数据，不隔离 Kiyori 内已授权 AI”，避免错误隐私预期

## 自问自答

### 为什么不为每个无痕窗口创建独立 profile？

浏览器的同一次无痕会话通常允许窗口之间共享登录，同时在最后一个窗口关闭后统一销毁。每窗口 profile 会让用户在同一无痕模式下无法延续页面状态，也增加 profile 删除竞态。

### 为什么缩略图不持久化？

当前标签和 WebView 本身不跨进程恢复。单独持久化截图会制造已不存在窗口的陈旧状态，并扩大隐私和存储范围。

## 预计文件

- `StandardBrowserSessionTools.kt`
- `BrowserWebViewSupport.kt`
- 新的 WebView profile manager
- `WebSessionBrowserHostState.kt`
- `WebSessionBrowserTabOverview.kt`
- 工具 prompt、窗口与 profile 单测
- `CONTEXT.md` 和浏览器文档

## 验收

- 普通与无痕窗口的 Cookie、WebStorage 和 profile 实例隔离
- 最后一个无痕窗口关闭后 profile 被删除，普通窗口数据不受影响
- 窗口页无大标题，两个 selector、缩略图、新建、关闭和清空均工作
- UI 与 AI 创建、选择和关闭窗口后看到相同 Profile 与 active session
- Debug APK、提交、推送和远端 SHA 门禁通过

## 本地验收结果

[DONE]

- `WebSessionProfilePolicyTest`、`BrowserTabDiscoveryFormatterTest`、`KiyoriSoftwareHomeSearchTest` 与 `WebSessionBrowserChromeLayoutTest` 共 16 项 JVM 测试通过，零失败、零错误
- `ci/script/check_formal_readiness.py --repository . --require-main` 与 `git diff --check` 通过
- `:app:assembleDebug` 完成 230 个任务，零失败
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 生成时间：`2026-07-25 00:31:06 +08:00`
- 大小：`449473202` 字节
- SHA-256：`36D887E28AD05F92EBF3F6F951B89974F626B0DEFABF58FF3D88D8AAFA615AB0`
- application ID：`com.kiyori`
- 版本：`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`
- 签名：Android Debug certificate，APK Signature Scheme v2 通过
- 对齐：`zipalign -c -P 16 4` 通过
- 真机 WebView Multi-Profile、普通与无痕数据隔离、缩略图视觉和窗口交互仍为 `verification_pending`
