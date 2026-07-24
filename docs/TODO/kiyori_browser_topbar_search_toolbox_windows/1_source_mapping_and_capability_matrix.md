# 证据矩阵与能力映射

## 参考实现证据

参考仓库固定为 `kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766`。

| 主题 | 参考文件与符号 | 已确认行为 |
| --- | --- | --- |
| 顶栏 | `app/src/main/java/com/android/kiyori/browser/ui/BrowserTopBar.kt:87-141` | 返回、可点击地址卡片、刷新；地址卡片在沉浸式页面显示标题并进入搜索覆盖页 |
| 全屏搜索 | `BrowserTopBar.kt:144-277, 405-519` | 全屏白色覆盖层、自动聚焦输入、搜索引擎切换、当前网址行、搜索/网址提交 |
| 搜索引擎 | `BrowserTopBar.kt:633-680`、`domain/BrowserSearchEngine.kt:7-78` | 九个搜索引擎，当前项高亮，输入可直接转搜索 URL |
| 搜索记录 | `BrowserTopBar.kt:682-839`、`data/BrowserSearchHistoryRepository.kt:8-96` | 最近记录两列卡片、单条删除和一键清空；记录持久化而不是由访问历史推导 |
| 第五按钮 | `ui/BrowserToolboxSheet.kt:35-186` | 3 行 × 5 列工具网格，底部另有关闭/收起/设置动作 |
| 第四按钮 | `ui/BrowserBottomBar.kt:25-169`、`ui/BrowserScreen.kt:1754-2018` | 第四入口表示长期窗口/会话，不是访问历史；窗口页为卡片网格，支持新建、打开、关闭和清理当前模式窗口 |
| 窗口状态 | `domain/BrowserPageState.kt:20-58`、`data/BrowserWindowRepository.kt:15-488` | 窗口记录与访问历史分离；参考版把普通窗口持久化，无痕窗口记录留在内存 |

## 当前 Kiyori 证据

| 主题 | 当前文件与符号 | 已确认事实 |
| --- | --- | --- |
| 顶栏（实施前基线） | `app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionBrowserTopBar.kt`、已删除的 `WebSessionTopUrlBar.kt` | 原地址卡片内联编辑已替换为返回、搜索框和刷新/停止；搜索框进入全屏搜索覆盖层 |
| 页面装配 | `ui/features/websession/browser/WebSessionBrowserScreen.kt:68-457` | 一份 Compose 页面同时装配 WebView、顶栏、下载条、底栏、标签概览、三态抽屉和 JS 对话层 |
| 工具箱 | `ui/features/websession/browser/chrome/WebSessionBrowserToolbox.kt:47-291` | 当前只有历史、书签、下载、Userscripts、电脑/手机模式和关闭动作 |
| 标签概览 | `ui/features/websession/browser/chrome/WebSessionBrowserTabOverview.kt:53-319` | 当前已有全屏卡片概览、2/3/4 列自适应、单项关闭、新建和关闭全部 |
| 抽屉 | `ui/features/websession/browser/chrome/WebSessionBrowserBottomDrawer.kt:64-261` | 已有 Hidden/Partial/Expanded、垂直拖动、遮罩点击和 route 转场；本轮保持这份 motion 合同 |
| Host 状态 | `core/tools/defaultTool/websession/browser/WebSessionBrowserHostState.kt:6-134` | 有 session、导航、下载、历史、userscript 和 sheet 状态；没有搜索、网络日志、源码或窗口模式字段 |
| Host 回调 | `core/tools/defaultTool/websession/browser/WebSessionBrowserHost.kt:50-80`、`BrowserWebViewSupport.kt:453-689` | 人类 UI 回调最终调用同一 `StandardBrowserSessionTools`；新增回调不能绕过该路径 |
| 状态投影 | `BrowserWebViewSupport.kt:811-896` | `buildBrowserState` 从 session 注册表投影活动 WebView、标签和导航状态；AI 操作也经由同一投影刷新 UI |
| Runtime | `core/tools/defaultTool/standard/StandardBrowserSessionTools.kt:87-183` | `sessions`、`sessionOrder`、`activeSessionId`、历史、下载、userscript 和 AI 浏览器动作均为共享状态 |
| WebView profile | `BrowserWebViewSupport.kt:41-130, 1030-1045` | 每个 session 创建 Android `WebView`，Cookie 使用系统 `CookieManager.getInstance()`；没有 per-session data directory/profile |
| 持久化 | `WebSessionHistoryStore.kt:15-204` | 现有 DataStore 持久化书签、访问历史、电脑模式；可以在同一 store 增加搜索引擎和搜索记录 |

## 逐项能力映射

| 参考标签 | 本轮处理 | 运行时接线 |
| --- | --- | --- |
| 加书签 | 实现 | 当前页 URL/title -> `WebSessionHistoryStore.toggleBookmark` |
| 书签 | 实现 | 现有 `WebSessionBookmarkSheet` |
| 历史 | 实现并改名 | 现有 `WebSessionHistorySheet`；只改用户可见文字“历史记录”→“历史” |
| 下载 | 实现 | 现有 `WebSessionDownloadSheet` |
| 插件 | 实现并改名 | 现有 userscript 管理页；只改用户可见文字 `Userscripts` → `插件`，保留内部 userscript 标识 |
| 悬浮嗅探 | 本轮不显示 | 当前 WebSession 没有可接入的嗅探/播放器状态机；不放入口冒充可用 |
| UA标识 | 实现 | 新 UA 选择内容只切换现有电脑模式/手机模式，继续使用 `setDesktopModeEnabled` |
| 网络日志 | 实现 | 从当前 session 的 `networkEntries` 构建只读列表；AI 请求也进入同一列表 |
| 刷新 | 实现 | 现有 `onRefreshOrStop`，加载中显示停止 |
| 工具箱 | 不重复显示 | 第五底栏按钮本身就是当前浏览器工具箱；没有第二个浏览器内工具箱 owner |
| 无痕模式 | 本轮不显示 | 不能证明 Cookie、缓存、WebStorage 和 WebView profile 隔离；不以少写历史冒充无痕 |
| 阅读模式 | 本轮不显示 | 当前没有 reader runtime 或页面转换契约 |
| 查看源码 | 实现 | 活动 WebView 异步执行 `document.documentElement.outerHTML`，UI 显示真实结果并可复制 |
| 标记广告 | 本轮不显示 | 当前没有 WebSession 广告规则/注入能力 |
| 网站配置 | 本轮不显示 | 当前没有 per-site 配置 store 或页面 |

## 实施后核对

- `WebSessionBrowserTopBar`、`WebSessionBrowserSearchScreen`、`WebSessionBrowserToolbox` 和 `WebSessionBrowserTabOverview` 已装配到 `WebSessionBrowserHost` 的统一状态与回调
- 搜索引擎选择、最近记录、网络日志和页面源码均有真实状态来源；未接通能力继续不渲染
- 第四按钮仍只操作普通 WebSession session；无痕入口没有加入工具网格或窗口选择器

不显示的项目不是“待点击占位”，而是本轮明确的能力边界。只有新增真实 runtime 合同后才能进入工具网格。
