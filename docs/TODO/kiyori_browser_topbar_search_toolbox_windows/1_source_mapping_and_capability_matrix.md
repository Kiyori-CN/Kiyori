# 证据矩阵与能力映射

## 参考实现证据

参考仓库固定为 `D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766`。

| 主题 | 参考文件与符号 | 已确认行为 |
| --- | --- | --- |
| 顶栏 | `app/src/main/java/com/android/kiyori/browser/ui/BrowserTopBar.kt:87-141` | 返回、可点击地址卡片、刷新；地址卡片显示标题并进入搜索覆盖页 |
| 全屏搜索 | `BrowserTopBar.kt:144-277, 405-519` | 全屏白色覆盖层、自动聚焦输入、搜索引擎切换、当前网址行、搜索/网址提交 |
| 搜索引擎 | `BrowserTopBar.kt:633-680`、`domain/BrowserSearchEngine.kt:7-78` | 九个搜索引擎，当前项高亮，关键词可直接转搜索 URL |
| 搜索记录 | `BrowserTopBar.kt:682-839`、`data/BrowserSearchHistoryRepository.kt:8-96` | 最近记录两列卡片、单条删除和一键清空；记录单独持久化 |
| 主菜单 | `ui/BrowserToolboxSheet.kt:35-186` | 三行五列按钮，第四行三个图标动作；主菜单本身没有拖动状态 |
| 窗口总览 | `ui/BrowserBottomBar.kt:25-169`、`ui/BrowserScreen.kt:1754-2018` | 第四入口表示长期窗口/会话；普通/无痕 selector、卡片网格、新建、打开、关闭和清理 |
| 窗口状态 | `domain/BrowserPageState.kt:20-58`、`data/BrowserWindowRepository.kt:15-488` | 窗口记录与访问历史分离；参考版把普通窗口持久化，无痕窗口记录留在内存 |

## 当前 Kiyori 证据

| 主题 | 当前文件与符号 | 已确认事实与实施影响 |
| --- | --- | --- |
| App Shell | `ui/main/shell/KiyoriShellState.kt:58-140`、`ui/main/OperitApp.kt:263-275,608-616` | Browser Home 目前没有返回来源；需要新增浏览器来源/返回状态，AI Home 浏览器入口不能再直接回普通 Software Home |
| AI Home 顶栏 | `ui/features/chat/screens/AIChatScreen.kt:820-868` | 当前 action 只有终端和工作区；浏览器 action 应通过 Shell 提供的显式回调插在终端左侧 |
| 浏览器装配 | `ui/features/browser/appshell/KiyoriBrowserHome.kt:21-59`、`core/browser/presentation/BrowserPresentationCoordinator.kt:19-49` | App Shell 与 overlay 通过同一 host lease 转挂同一 WebView；退出时可按来源返回并重新显示 indicator |
| Host presentation | `core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt:100-205,286-383` | `appPresentationActive` 区分 APP_SHELL/OVERLAY；overlay 当前 top bar 会按 `canGoBack` 调用 WebView 后退，需要改为 owner-specific 顶栏动作 |
| 菜单/子页 | `ui/features/websession/browser/WebSessionBrowserScreen.kt:390-780` | MENU 与历史/下载/书签等 route 目前共用同一可拖动抽屉；需要将 MENU 拆成固定高度 host，子 route 保持拖动 |
| 浏览器菜单 | `ui/features/websession/browser/chrome/WebSessionBrowserMenuDrawer.kt`、`WebSessionBrowserScreen.kt` | 固定四行全宽主菜单；关闭当前/全部只保留在窗口总览，不再进入主菜单 |
| 窗口总览 | `ui/features/websession/browser/chrome/WebSessionBrowserTabOverview.kt:42-279` | 已有全屏卡片、2/3/4 列、单项关闭、新建和清空；需要加入普通/无痕 selector 与无痕说明态 |
| 抽屉 | `ui/features/websession/browser/chrome/WebSessionBrowserBottomDrawer.kt:64-261` | 三态动画和拖动已存在，但宽度在宽屏受 `drawerMaxWidthDp` 限制；本轮所有抽屉改为 `fillMaxWidth` |
| Runtime | `core/tools/defaultTool/standard/StandardBrowserSessionTools.kt:87-183`、`BrowserWebViewSupport.kt:811-896` | sessions、sessionOrder、activeSessionId、活动 WebView、历史、下载、userscript 与 AI browser_* 同源；UI 只能调用现有 callbacks |
| WebView profile | `BrowserWebViewSupport.kt:41-130,1030-1045` | Cookie 使用 `CookieManager.getInstance()`，session 没有 profile 字段；不能把无痕 selector 接成共享 Cookie 的假无痕 |
| 搜索持久化 | `WebSessionHistoryStore.kt:15-250`、`WebSessionSearchEngine.kt` | 搜索引擎和搜索记录已有 DataStore/Flow；搜索页只维护 draft/焦点/route 临时状态 |
| 网络/源码 | `BrowserWebViewSupport.kt:recordNetworkRequest`、`WebSessionBrowserHost.kt:548-606` | 网络日志来自真实 `networkEntries`；源码通过活动 WebView 异步执行 DOM 读取，不重新加载页面 |

## 全量按钮能力映射

| 菜单按钮 | 结果 | 真实状态来源或边界 |
| --- | --- | --- |
| 加书签 | 接通当前页 toggle | `WebSessionHistoryStore.toggleBookmark` |
| 书签 | 接通子页 | `WebSessionBookmarkSheet` |
| 历史 | 接通子页并使用“历史”文案 | `WebSessionHistorySheet` 与当前 session history |
| 下载 | 接通子页 | `WebSessionDownloadSheet` |
| 插件 | 接通 userscript 子页 | 内部继续保留 userscript 标识，用户文案为“插件” |
| 悬浮嗅探 | 进入真实能力说明页 | 当前没有 candidate/player/sniffer runtime，不改变网页 |
| UA标识 | 接通现有 UA 页面 | 继续切换电脑/手机 User-Agent；不改内部模式 key |
| 网络日志 | 接通只读子页 | 当前 session 的 method、URL、主框架、静态资源和时间戳 |
| AI对话 | 返回 AI Home | overlay 场景收缩 indicator，App Shell 场景释放 app presentation 后回到 AI Home |
| 工具箱 | 进入说明页 | 第五按钮已经是浏览器菜单 owner，不再复制第二套工具箱 |
| 无痕模式 | 进入隔离能力说明页 | 没有 per-profile Cookie/cache/WebStorage 隔离，禁止创建伪无痕 session |
| 阅读模式 | 进入能力说明页 | 当前没有 reader state/转换管线 |
| 查看源码 | 接通真实源码页 | 活动 WebView 异步读取 `document.documentElement.outerHTML` |
| 标记广告 | 进入能力说明页 | 当前没有页面级广告规则和注入状态 |
| 网站配置 | 进入能力说明页 | 当前没有 per-site store 与 owner |
| 退出浏览器 | 移除浏览器展示层 | 保留 sessions、activeSessionId 和下次 AI 工具可复用的 runtime |
| 收起抽屉 | 关闭主菜单 | 不改 WebView/session |
| 浏览器设置 | 进入设置能力说明页 | 当前没有独立 Browser Settings owner，后续另立能力任务 |

窗口总览中的普通窗口使用真实 session；无痕页仅提供视觉 selector、空状态说明和不可误解的隔离边界。清空普通窗口仍调用现有 `closeSession` 链路，删除所有 session 后移除悬浮球。

## 不改变的兼容合同

- `StandardBrowserSessionTools` 仍是进程级共享单例
- `browser_*` 工具名、参数、返回结构和 `browser_tabs` 语义不变
- `userscript`、`WebSessionHistoryStore` 的持久化 key、CookieManager 和 Operit 兼容 namespace 不改名
- presentation/UI state 的新增字段不进入 AI tool protocol，不复制 runtime state
