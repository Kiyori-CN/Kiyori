# 真无痕 Profile、窗口逻辑与网页缩略图

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
