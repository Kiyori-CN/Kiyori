# 浏览器运行时契约

适用于网页会话、设置、脚本、广告和网页工具。Browser Home、AI 工具及下载抽屉共享唯一 `StandardBrowserSessionTools` / WebSession / 真实 WebView；关闭一个展示面不创建或释放第二个进程运行时。

## 会话、窗口与导航

- 普通网页的同站、跨站、`target="_blank"` 和用户 `window.open()` 默认在当前窗口导航。人工新建、AI 明确创建、跨 Profile 搜索及配置主页的真实用户跨站跳转可创建窗口。
- 配置主页的跨站子窗口保留相同 Profile；历史耗尽后关闭子窗口并返回仍有效的主页窗口。其他窗口历史耗尽后返回当前配置主页。
- `BrowserSessionSearchRecovery` 只在全屏文本搜索提交时记录来源；快捷搜索引擎条只在活动 URL 与已解析搜索结果 URL 等价时显示。直接网址、主页、普通子页和未稳定重定向不显示，用户对同结果页的关闭意图保留。
- 恢复开关只保存所需的普通窗口 URL、标题、顺序、活动窗口、创建原因与明确搜索来源；无痕窗口、Cookie、表单、网页正文、截图和密码不进入恢复记录。
- 关闭操作先提交内存会话状态，再观察新活动页；页面观察失败不能撤销已经成功的关闭。
- 视口参数是会话级 CSS 布局契约，仅 Host 按 Android density 转为 View 物理尺寸；返回真实 DOM 指标。实际点击与导航稳定分别报告，不把脚本赋值冒充真实点击。

## 浏览器设置

`WebSessionBrowserSettingsStore` 持有设置及完整域名禁用集合。广告规则、网站密码和代理分别属于各自所有者。

| 设置 | 契约 |
| --- | --- |
| 网页主页 | `CUSTOM_URL / BLANK`；当前新安装为 `https://web.gotab.cn/`，纯空白页为 `about:blank` |
| 主页切换 | 立即保存、刷新投影，但不导航正在查看的普通网页；清除 Profile 后自定义模式创建同 Profile 主页，空白模式留空 |
| 导航与恢复 | 返回不重载、边缘滑动历史、恢复上次搜索、恢复前询问、保留普通多窗口分别持有明确设置 |
| 快捷搜索条 | 默认开启，显示仍需满足该文档的结果页来源条件 |
| 文字缩放 | `50%..200%`，步长 `5%`，默认 `100%`，作用于现有和未来 WebSession |
| 强制缩放 | 当前文档可逆 viewport 覆盖，移除作者缩放限制，范围 `0.1..10.0`；PC UA 保持宽视口并停用强制缩放期间的 overview，移动 UA 适配独立保持 |
| 元素长按 | 默认开启，全局与站点切换同步现有 WebView，无须重载；输入框保留系统原生编辑与选区 |
| 自动悬浮播放阈值 | 默认 60 秒；固定选项或 `1..86400` 整数秒，仅影响自动播放，不限制手动候选播放 |

站点规则为空时删除该记录。当前域名禁用项只影响已声明能力，不产生第二份全局设置。

## 广告规则与缓存

- `BrowserAdBlockStore` 持有 schema-v3 总开关、放行域名、自定义 URL/元素规则与订阅 metadata。
- 原始订阅为权威输入，保存至 `browser_ad_block_subscriptions/<subscription-id>/<payload-sha256>.txt`；编译缓存位于 `noBackupFilesDir/browser_ad_block_compiled/<format>/<compiler-contract>/<subscription-id>/`，不进入备份。
- payload 与编译分区完整写入后才提交 SHA-256、字节数和版本。单订阅大小上限 32 MiB，正常 EOF 是正常完成。
- 五个内置订阅按“广告拦截器 Pro / Adblock Plus”分组；首次使用或显式启用缺失内容时同步，后续按自动更新设置与间隔检查。UI 区分“启用”和“本地就绪”。
- 只执行已有 WebView 证据覆盖的 ABP/AdGuard 子集：锚定、通配符、正则、例外、域名/第三方/资源类型约束、`important`、跨订阅 `badfilter`、页面例外、`##/#@#`。不支持语法计为忽略，不能扩张为普通阻断规则。
- 构造 Store 不在调用线程解析订阅。单一 IO 生命周期完成读取、缓存加载或规则编译，然后原子发布完整不可变 matcher；请求线程不等待其解析锁，主文档不直接被规则阻断。
- 每订阅持有独立 Engine 分区，自定义规则另有小分区；combine 在一个候选决策中保留跨源例外。变更订阅仅重建该分区，开关和放行只更新轻量 matcher。
- 缓存命中直接解码索引，不读大文本或重编译。内容变化时单次 UTF-8 行读取直接生成编译记录，不保留整份 String 或完整中间列表；域名驻留池限于本次操作。
- SHA-256 未变只更新同步 metadata，不替换 Engine、不推进 `ruleRevision`。初始化返回后再刷新到期订阅，避免大临时对象与新旧 Engine 同时达到峰值。
- 每请求归一化一次，页面策略与域名事实使用有界缓存；拦截计数及 UI 刷新合并发布，空闲后仍完成最后一次无竞态发布。
- DOM CSS 在后台组装为有界块，每 document token 和 `ruleRevision` 最多注入一次；Hiker 选择器独立使用观察器。规则变更通过唯一观察者重投影全部会话。

## 广告标记与元素

- 标记工作台是 WebView 下方固定高度的同级面板；页面占剩余实测高度，最后一项不被遮挡。
- 标记模式由原生触摸流持有：抬起选元素，拖动和 fling 滚动同一个 WebView；事件不进入 DOM，导航与弹窗均被消费。
- 规则、节点、HTML、父子/兄弟节点、预览、编辑与清除由 Host 执行；跨源 iframe 内部仍不可读。
- 退出标记后才应用“默认允许 / 跳转前询问 / 阻止跳转”策略；普通浏览的当前 host 可导航，跨 host 主文档与弹窗按策略决定。
- 域名清除只移除该归一化 host 的用户元素规则和明确域名 URL 规则，保留订阅和全局无域名规则。
- 网络目录的 `REQUEST` 与 `ELEMENT` 明确区分：后者是当前页的用户元素规则，不伪装为 HTTP 请求或服务端响应；订阅 CSS 不生成数千条虚假网络日志。

## 脚本、凭据与 Cookie

- `WebSessionUserscriptManager` 以 WebView identity 和单调 generation 登记 attach；主线程消费、旧 binding 清理、bridge/document-start 注册与发布均在 `pendingSessionAttachmentLock` 内。
- detach 先推进 generation、撤销 pending，再清理 binding；晚到 attach 不能为已关闭会话重新注册脚本。
- `BrowserCredentialVault` 仅对普通 Profile 的 HTTP(S) 登录捕获非空账户与唯一非空密码，按精确 origin 与账户建立记录；自动填写使用原字段选择器，不自动提交。
- 无痕不捕获、读取或填写凭据；完整记录以 Android Keystore AES-GCM 原子加密至 no-backup。查看、复制、编辑和删除均由本机用户主动执行，不记录凭据值。
- Cookie Reader 属于内置 Browser 插件，复用当前 Profile 的 CookieManager；关闭后不读取、不展示入口，读取只在本机内存中处理，不导航、不写磁盘。

## AI 网页工具

- `browser_development` 是 AI 左抽屉的预置开发脚本包；通过宿主 query/apply 管理同一浏览器的
  内置扩展开关、油猴脚本与独立 `.kbx` 页面扩展。安装/更新默认关闭，更新/启停/删除绑定 revision；
  扩展中心显示自定义扩展同级卡片。具体格式、隔离世界与测试闭环见
  [AI 扩展开发契约](../architecture/browser_extension_development.md)。

- `browser:fill_form` 每字段使用 string/number/boolean `value`，且 `ref/selector` 恰好一个；DOM 决定实际控件操作，checkbox/radio 需要 boolean，不接受调用方指定控件 type。
- `browser_run_code` 注入 JavaScript 函数源码，不使用 eval 或动态 Function；支持的 Page/Locator 子集以 [浏览器工具契约](../../TODO/kiyori_browser_product_completion/index.md) 和运行时实现为准。
- `keyboard.press` 支持单字符、Enter、Backspace、Delete；未实现成员与键明确返回 `Unsupported Playwright API`。文件选择由真实点击与 `browser:upload` 持有。

## 继续阅读

- [浏览器导航与恢复](../../TODO/kiyori_browser_navigation_and_session_restoration/index.md)
- [浏览器扩展平台](../architecture/browser_plugin_platform.md)
- [媒体与下载](media_downloads.md)
- [网络路由](network_proxy.md)
