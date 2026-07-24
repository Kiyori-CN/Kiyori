# 顶栏、全屏搜索、窗口与返回状态机

## 状态分层

Browser Runtime 与 presentation state 必须分开：

- Runtime 继续由 `StandardBrowserSessionTools` 持有：sessions、sessionOrder、activeSessionId、活动 WebView、Cookie、导航、历史、书签、下载、userscript、AI snapshot 和 network entries
- Host presentation 只记录搜索、窗口总览、主菜单/子抽屉、说明页、源码读取、窗口模式和输入法状态
- 展示位置切换只转挂同一个 WebView；不创建第二个 WebView、第二个 CookieManager、第二个 active session，也不 reload 当前网页

计划新增或调整的 presentation 字段：

- `sheetRoute` 增加 `PLACEHOLDER`
- `placeholderPage`：悬浮嗅探、浏览器工具箱、无痕、阅读、标记广告、网站配置、浏览器设置
- `windowMode`：`NORMAL`、`INCOGNITO`
- 现有 `isSearchVisible`、`searchDraft`、`isSearchEnginePanelVisible`、`pageSource`
- Host 继续通过 `appPresentationActive` 区分 `APP_SHELL` 与 `OVERLAY`；不把 owner 写入 AI 协议

## 顶栏合同

沉浸式网页页从左到右固定为：

1. 返回：它是浏览器展示层退出动作，不是网页历史动作
2. 搜索框：展示当前页面标题或 URL，点击后进入全屏搜索
3. 刷新/停止：加载中显示停止，空闲显示刷新

返回动作按 presentation owner 解释：

- `OVERLAY`：展开悬浮球后的左上角返回只调用 `setExpanded(false)`，恢复 indicator；不能调用 `WebView.goBack()`，因此不会打断 AI 正在进行的浏览器操作
- `APP_SHELL`：左上角返回退出 Browser Home；进入来源为 AI Home 时回到 AI Home，普通根入口时回到 Software Home。释放 app presentation 后，已有 overlay 恢复成 indicator
- 网页历史后退仍由底栏后退和 AI `browser_navigate_back` 负责

顶栏、底栏、系统栏和网页共用 edge-to-edge 背景策略：

- 移除顶栏/网页、网页/底栏之间的 1dp 分隔线
- 移除 chrome surface 的 tonal/shadow elevation
- 顶栏与底栏使用透明或共享浏览器背景，WebView 区域使用同一根背景层
- status bar 与 navigation bar 不额外绘制独立色块；交互内容继续应用对应 inset
- 这不是页面颜色采样协议。WebView 页面颜色不能稳定同步到 Compose，故不新增颜色猜测状态

## 全屏搜索页

搜索页是 Host overlay，不是 WebView 导航：

- 背景填满浏览器窗口，第一行应用 status-bar inset
- 第一行固定为返回、自动聚焦的 URI 输入卡片和右侧占位宽度，避免刷新按钮造成跳动
- 输入卡片包含当前搜索引擎、`搜索或输入网址` 占位、清空按钮和搜索/前往按钮
- 引擎面板从输入卡片下方展开，当前引擎高亮；点击面板外只关闭引擎面板
- 当前 URL 有效时显示当前网址卡片，提供打开、复制和回填输入框三个动作
- 搜索记录使用两列自适应卡片，单条可打开或删除，标题区域提供一键清空
- 打开搜索页、切换引擎、删除记录和清空记录都不触碰活动 WebView

提交顺序：

1. trim `searchDraft`
2. 交给 `BrowserAddressResolver.resolve` 和当前 `WebSessionSearchEngine`
3. URL 或搜索 URL 通过现有 `openUrlOnMain`
4. 关键词记录写入 `WebSessionHistoryStore`，当前引擎写入同一 store
5. 关闭搜索页、清空 focus、收起输入法

空输入不导航，也不改变当前页面。打开记录直接导航其已持久化的 `targetUrl`。

## 普通/无痕窗口总览

底栏第四按钮命名为窗口总览入口，不再解释为访问历史。窗口页使用参考版 selector 和卡片网格：

- 顶部有“普通窗口”和“无痕窗口”两个 mode tab；当前 mode 有选中线和数量
- 普通窗口展示 `browserState.tabs`，沿用 2/3/4 列自适应布局、活动边框、标题、URL、单卡关闭
- 打开普通卡片只调用 `onSelectTab`，重新挂载同一个 WebView，不刷新页面
- 新建普通窗口调用现有 `onNewTab`，AI `browser_tabs` 立即看到相同 session
- 普通窗口底部保留返回、新建、清空三个 icon-only 动作；清空调用现有 close-all session 链路
- 无痕窗口 mode 显示隔离能力说明和空列表，不创建共享 Cookie 的 session；其新建动作进入同一说明页，不产生假窗口
- 无痕的真正实现需要独立的 WebView data directory、Cookie/cache/WebStorage 隔离、AI session profile 字段和清晰的持久化/关闭合同，另立任务完成

## 抽屉与返回优先级

主菜单与子页使用两个不同 host：

- `MENU` 使用固定高度、全宽、不可拖动的主菜单 host
- `HISTORY`、`DOWNLOADS`、`BOOKMARKS`、`USERSCRIPTS`、`USER_AGENT`、`NETWORK_LOG`、`PAGE_SOURCE`、`PLACEHOLDER` 使用三态可拖动子抽屉
- 所有 host 的 Surface 左右贴屏，宽屏不再使用 `drawerMaxWidthDp` 形成缝隙
- 主菜单切换到子页时，主菜单 host 离开，子页 host 从 Partial 状态进入；子页返回回到主菜单，不重建 WebView
- 主菜单第四行“收起抽屉”和遮罩点击只关闭主菜单

系统 Back 的优先级：

1. JS dialog 或外部打开确认
2. 搜索引擎面板
3. 全屏搜索
4. 标签/窗口总览
5. 子抽屉 route
6. 主菜单
7. overlay 展开态收缩为 indicator
8. App Shell 浏览器模式下的 WebView 历史后退
9. App Shell 退出 Browser Home 并按来源返回

顶栏返回是 owner-specific 的显式动作，不依赖这条系统 Back 链，以确保 AI overlay 不会被网页历史后退打断。

## IME、旋转与尺寸变化

- 搜索输入使用 URI 键盘和 Search/Go action
- 搜索页关闭时显式清 focus；系统 Back 先让 IME 收起，再处理页面 Back
- 旋转、横竖屏、平板和折叠窗口只重新计算 chrome layout，保留 search draft、engine、route、window mode 和 active session
- WebView 不因为窗口尺寸变化而重新导航；AI `browser_resize` 继续只改变 runtime viewport projection
