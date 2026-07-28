# 浏览器资源嗅探与悬浮播放

## 实现状态（2026-07-28）

- 已在现有 `StandardBrowserSessionTools.WebSession` 内加入有界 `mediaCandidates`；导航开始时清空当前
  session 候选，不改 network log、其他窗口或 WebView 生命周期。
- 已实现 request、userscript 实际响应和只读 DOM 三路观察，候选合并保留原始 URL、实际 headers、
  Cookie/profile scope、页面与首次/最近发现时间；API MIME、blob 与直链判定由纯逻辑测试约束。
- `shouldInterceptRequest` 后台线程只读 `WebSession.appliedUserAgent` 缓存和 CookieManager；任何
  `WebView.settings` 访问都留在主线程 UA 应用路径，避免违反 Android WebView 线程约束。
- 浏览器菜单“悬浮嗅探”已进入真实 `MEDIA_CANDIDATES` drawer；播放进入唯一 `PlayerSession`，下载进入
  现有 `PendingBrowserDownloadRequest` 与 `BrowserDownloadManager`。
- 浏览器内容区已实现 16:9 悬浮 `PlayerSurfaceView`、边界拖动、双指缩放、播放/暂停、全屏与关闭；
  `PlayerActivity` 往返只切 presentation 和 surface owner。
- 顶部搜索框 28dp 橙色候选数字圆圈、稳定 direct candidate 自动小窗、关闭后本页抑制、网络日志精确
  candidate 在线播放与保留 headers 的下载动作均已接入。
- 全量 JVM 测试 `555/555`、Python native/assets/Intent 门禁 `17/17`、formal readiness、
  `git diff --check` 与最终 `assembleDebug` 均通过；最终 APK、native/许可证复审见阶段 10。真机项目仍为
  `verification_pending`。

## 状态模型与不变量

浏览器与播放器共用 `PlayerSession` 的显式状态机：

- `BROWSER_ONLY`：不存在活动 player media，网页保持自身状态
- `FLOATING_PLAYER`：player media 已加载，浏览器内 Surface 是唯一 owner
- `FULLSCREEN_PLAYER`：同一 media/core 转挂到全屏 Surface

三态转换只改变 presentation 与 surface owner。小窗 -> 全屏 -> 小窗 -> 关闭全过程不得调用
`WebView.loadUrl`、`reload`、重新嗅探、重建 WebView、再次 `loadfile` 或任何会 pause/resume 网页 media 的
JavaScript。全屏返回直接 finish `PlayerActivity`；当前唯一 WebSession、滚动、表单和网页媒体状态原样保留。

## WebSession candidate owner

每个现有 `StandardBrowserSessionTools.WebSession` 自己持有有界的 `mediaCandidates`，不创建全局第二浏览器
状态。candidate 至少保存：

- 稳定 candidate ID 与未经 Kiyori 重写的 `originalUrl`
- 实际请求中的 `Origin`、`User-Agent`、`Referer`、`Range`、`Accept`
- 从该 WebSession Profile CookieManager 读取的 `Cookie` 及其 `cookieScopeUrl`
- 页面 URL、页面标题、WebSession ID/Profile、首次发现与最近发现时间
- 来源集合：网络请求、DOM `currentSrc/src/source`、只读 play 事件、MSE/blob 线索
- DOM 声明 MIME 与已确认的响应 MIME；两者不得混写
- 可信度证据与 `directPlaybackReady`，而不是只有一个猜测格式

Cookie 和 headers 只在内存中交给 player/download owner，不进入普通日志、Compose 文本、任务日记或
外部服务。

## 被动收集

- `shouldInterceptRequest` 继续先记录现有 network log，并把原始 request 与真实 headers 交给 candidate
  分类器。只有 userscript 拦截确实返回 `WebResourceResponse` 时才记录其响应 MIME。
- 页面完成后注入幂等、只读 bridge：观察已有 `video` 的 `currentSrc`、`src`、`source.src` 和后续
  DOM/play 事件。bridge 不设置 media 属性，不调用 `play()`、`pause()` 或 seek。
- HTTP/HTTPS 扩展、manifest 扩展、`Accept`、DOM media 元素和真实响应 MIME只形成证据；不发主动
  HEAD/GET probe，不解析接口，不跟随另一个 resolver，不替换 URL。
- MIME 可以提高可信度，但只有 MIME、没有直链/DOM 证据的 API URL 必须保持
  `directPlaybackReady=false`。
- `blob:`/MSE 可以展示为线索；没有真实网络 URL 时播放与下载动作禁用，不伪造直链。
- URL 相同的网络与 DOM 证据合并；首次发现时间不改写，实际 headers 只用同 URL 的新证据补齐。

## candidate 选择与下载

- 浏览器菜单“悬浮嗅探”进入真实 `MEDIA_CANDIDATES` drawer，显示来源、格式证据、发现时间和可执行动作。
- 播放动作把 candidate 原始 URL 与实际 headers 交给唯一 `PlayerSession`。浏览器设置允许悬浮时进入
  `FLOATING_PLAYER`，否则直接进入 `FULLSCREEN_PLAYER`；两者都只加载一次。
- 下载动作创建 `PendingBrowserDownloadRequest`，完整使用 candidate 已保存 headers，并继续交给现有
  `BrowserDownloadManager`、默认 engine、确认页和任务数据库。不得重新猜 Referer 或创建第二下载系统。

## 浏览器内悬浮播放器

- Surface 位于现有 Browser Screen 的 WebView 内容区域之上、浏览器顶底栏之间；初始宽高保持 16:9。
- 支持边界内拖动与双指缩放，尺寸和 offset 使用稳定约束，控制层不会改变 WebView 布局。
- 控制包括播放/暂停、进全屏和关闭；全屏按钮先切换 session presentation，再启动复用 session 的
  `PlayerActivity`。
- Activity 返回时先把同一 session 切回 `FLOATING_PLAYER`，随后 finish；原 Compose overlay 重新 attach
  自己仍存活/新建的 Surface。
- 关闭只执行 `PlayerSession.close()`，不导航、刷新或改变网页。
- 浏览器系统 Back 先处理 candidate drawer/菜单；player 全屏 Back 按设置返回悬浮或关闭；普通浏览器 Back
  仍由现有 owner 处理。

## 验收门禁

- 网络与 DOM 分类、证据合并、API MIME 不冒充直链、blob 不可执行均有纯逻辑测试。
- candidate 选择保留原始 URL 与六类 request 身份字段；下载进入同一 manager。
- 小窗 -> 全屏 -> 小窗 -> 关闭的状态机测试证明 media request ID 与 load generation 不变。
- 源码检查确认新增 bridge 没有 `pause()`、`play()`、`load()`、`loadUrl` 或 `reload` 调用。
- 阶段 9 定向测试、formal readiness、`git diff --check` 与最终 `assembleDebug` 通过；最终 APK 再执行
  manifest、签名、zipalign、native 清单、动态依赖和全部 arm64 ELF 16 KB 审计。
- 真机解码、性能、拖动/缩放手势、悬浮转场和站点兼容保持 `verification_pending`。
