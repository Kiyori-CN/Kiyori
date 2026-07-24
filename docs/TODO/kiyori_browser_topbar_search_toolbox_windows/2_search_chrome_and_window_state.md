# 顶栏、搜索、窗口与 Back 状态机

## 状态分层

浏览器展示状态与 Browser Runtime 状态分开：

- Runtime 继续由 `StandardBrowserSessionTools` 持有，包含 session、活动 WebView、Cookie、导航、历史、书签、下载、userscript、AI 快照和网络请求
- Host presentation state 只记录当前界面是否在搜索、窗口概览、工具抽屉或子 route，以及输入法和异步源码请求状态
- 任何展示层切换都不得创建、复制、销毁或重新导航活动 WebView

计划新增的 presentation 字段：

- `isSearchVisible: Boolean`
- `searchDraft: String`
- `pageSourceState: Loading | Ready(String) | Error(String)`
- `sheetRoute`: `NETWORK_LOG`、`USER_AGENT`、`PAGE_SOURCE` 以及现有 route

搜索引擎与搜索记录由 `WebSessionHistoryStore` 持久化并通过 Flow 投影给 Compose：

- `WebSessionSearchEngine` 使用参考版九个引擎及各自 URL 前缀，默认百度
- `WebSessionSearchRecord` 保存 `id/query/targetUrl/createdAt`，最多保留最近 12 条，按最新在前
- 选择引擎立即持久化；提交输入时把输入和解析出的目标 URL写入记录，然后调用现有 `openUrlOnMain`
- 直接 URL、域名和关键词继续由 `BrowserAddressResolver` 判定；关键词使用当前引擎，不能继续硬编码百度

## 顶栏

沉浸式内容页从左到右固定为：

1. 返回：优先返回 WebView 上一页；无页面历史时交给浏览器 Host/App Shell
2. 搜索框：白色/主题 surface 上的黑色细边框圆角卡片，展示当前页面标题或 URL，右侧显示搜索图标；点击后覆盖进入全屏搜索
3. 刷新：加载中为停止，空闲为刷新

顶部继续应用 status-bar inset，不能把当前地址编辑控件和新的全屏搜索并行渲染。

## 全屏搜索页

全屏搜索覆盖层复刻参考版结构：

- 白色/主题背景填满浏览器窗口，顶端 status-bar inset
- 第一行是返回箭头、自动获得焦点的搜索输入卡片和右侧占位宽度，避免布局跳动
- 输入卡片包含搜索引擎选择入口、`搜索或输入网址` 占位、清空按钮和提交按钮
- 引擎面板在输入卡片下方展开，当前引擎有选中样式；点击面板外关闭面板而不关闭搜索页
- 当前页面存在有效 URL 时显示当前网址行：可把 URL 放回输入框、复制 URL、关闭搜索页继续当前页面
- 下方显示搜索记录两列卡片，单条可打开或删除，列表末尾提供一键清空
- 搜索页不改变活动 WebView，只有提交或打开记录才触发导航

搜索提交动作：

1. 读取并 trim `searchDraft`
2. 使用选中引擎解析为 URL
3. 通过 `onNavigate` 进入唯一共享 Runtime
4. 持久化搜索记录和当前引擎
5. 关闭搜索页、清空焦点和输入法

空输入不发起导航；不增加任何“没有输入时回退到某个页面”的兜底动作。

## 普通窗口总览

第四底栏按钮仍然是窗口/会话总览，而不是访问历史：

- 全屏覆盖当前 WebView，进入和退出使用短 fade + vertical slide
- 标题区显示“普通窗口”和当前数量；卡片保留当前标签序号、标题、URL、活动边框和关闭按钮
- 2/3/4 列继续沿用当前自适应布局，避免手机横屏、平板和折叠布局重新引入固定宽度
- 新建按钮调用现有 `onNewTab`，内部仍创建一个新的 WebSession session；AI `browser_tabs` 能看到相同 session
- 打开卡片只调用 `onSelectTab`，由 Runtime 重新挂载同一 WebView，不刷新页面
- 单项关闭调用现有 `onCloseTab`；关闭当前卡片后由 Runtime 选择剩余活动 session
- 底部返回只关闭总览；底部清理调用现有 `onCloseAllTabs`，清理后显示无标签状态

无痕窗口 selector、无痕新建和模式清理本轮不提供。原因是当前 `CookieManager.getInstance()` 和 WebView data store 由整个进程共享；除非先建立可验证的 profile 隔离和 AI session 合同，否则不能从 UI 暗示它已经是无痕。

## 抽屉、转场、Back、输入法与旋转

- 第五按钮打开现有 Hidden -> Partial 抽屉；向上拖到 Expanded，向下回到 Partial/Hidden，scrim 点击关闭
- 抽屉 route 内容使用现有 `AnimatedContent`，搜索页、窗口页和抽屉互斥
- Back 优先级：页面对话/外部打开确认 → 搜索引擎面板 → 全屏搜索/源码/窗口页 → 抽屉 route → 抽屉 → URL/UA 输入焦点 → WebView 后退 → App Shell 退出
- 搜索输入使用 URI 键盘和 Search/Go action；提交后清除 focus，Back 先交给系统输入法，关闭搜索页时显式清理焦点
- 旋转和窗口尺寸变化只重新计算 chrome layout；搜索 draft、选中引擎、route 和活动 session 保持不变，WebView 不重新导航

