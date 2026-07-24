---
status: completed
---

# 源映射与目录架构

## 设计来源优先级

1. 当前 Kiyori 产品合同决定 Browser Runtime、App Shell、Back 和人与 AI 共用语义。
2. Operit 原版主题决定颜色、排版、形状、图标、surface 层级和交互密度。
3. `kiyori-android@24a2dfa9` 决定浏览器页面组成、底栏顺序、标签总览和工具菜单的信息架构。

旧项目代码只读。复制布局意图时必须删除 Activity、X5、硬编码主题和页面私有数据 owner 的假设。

## 旧源到当前目标

| 旧源 | 可迁移布局 | 当前目标 | 刻意差异 |
| --- | --- | --- | --- |
| `browser/ui/BrowserBottomBar.kt` | 后退、前进、主页、窗口数、工具箱 | `ui/features/websession/browser/chrome/WebSessionBrowserBottomBar.kt` | 使用 Operit 主题与 Material 图标；窗口数对应共享标签总数 |
| `browser/ui/BrowserScreen.kt` | 页面分层、全屏窗口页、覆盖层 Back 顺序 | `WebSessionBrowserScreen.kt` 与 `chrome/` 子包 | 不迁移媒体、无痕、预览、X5 或 Activity 状态 |
| `BrowserWindowPage` | 顶部标题、卡片网格、底部返回/新建/清理 | `chrome/WebSessionBrowserTabOverview.kt` | 卡片不伪造网页截图；直接观察现有 tabs 投影 |
| `browser/ui/BrowserToolboxSheet.kt` | 五列网格、底部三动作区 | `chrome/WebSessionBrowserToolbox.kt` | 只展示当前已实现功能；使用主题错误色表达关闭全部 |
| `ui/compose/KiyoriBottomDrawer.kt` | Partial、Expanded、Hidden 三态与拖动手柄 | `chrome/WebSessionBrowserBottomDrawer.kt` | 只允许手柄纵向拖动，避免和历史、下载、脚本列表滚动争抢 |
| `BrowserTopBar.kt` | 顶部返回、搜索与页面动作的空间关系 | `WebSessionBrowserTopBar.kt` 与 `WebSessionBrowserSearchScreen.kt` | 当前 Kiyori 已接入全屏搜索、引擎选择和搜索记录；仍使用共享地址解析与收藏逻辑 |
| `BrowserActivity.kt` | 根退出、系统 Back | `KiyoriBrowserHome.kt` 与 `KiyoriAppShell.kt` | 不新增 Activity；App Shell presentation 继续复用活动 WebView |

## 当前唯一 owner

| 数据或状态 | owner | 本轮允许的 UI 行为 | 禁止行为 |
| --- | --- | --- | --- |
| 标签集合与活动标签 | `StandardBrowserSessionTools` | 观察投影并调用现有 callback | Compose 内复制列表并作为事实源 |
| 活动 WebView | Browser Runtime session | 由 `WebSessionWebViewHost` 转挂 | 新建 UI WebView 或同时挂到两个父 View |
| presentation 租约 | `BrowserPresentationCoordinator`、`WebSessionBrowserHost` | APP_SHELL 与 OVERLAY 互斥展示 | UI 自行切换或重建 session |
| Cookie、缓存和网页历史栈 | Android WebView profile | 通过活动 WebView 正常浏览 | 为 AI 或 Browser Home 建第二 profile |
| 全局历史与收藏 | `WebSessionHistoryStore` | 打开、删除、清空和导航 | 新建浏览器首页数据库 |
| 下载 | 现有 Browser download owner | 展示状态并调用 pause、resume、retry 等 callback | 复制下载任务或改目录协议 |
| Userscripts | `WebSessionUserscriptManager` | 展示、安装、启停、更新和执行页面命令 | UI 注入另一套脚本 runtime |
| 抽屉和标签总览路由 | `WebSessionBrowserHostState.sheetRoute` | 控制当前覆盖层 | 写入长期业务状态或数据库 |
| 抽屉拖动偏移 | 抽屉 Composable | 仅作为瞬态动画状态 | 影响活动标签、WebView 或 AI 工具 |

## 目标目录

```text
app/src/main/java/com/ai/assistance/operit/
	core/
		browser/
			navigation/
			presentation/
		tools/defaultTool/websession/browser/
			WebSessionBrowserHost.kt
			WebSessionBrowserHostState.kt
			WebSessionWebViewHost.kt
			...
	ui/features/browser/
		appshell/
			KiyoriBrowserHome.kt
	ui/features/websession/browser/
		WebSessionBrowserScreen.kt
		WebSessionBrowserTopBar.kt
		WebSessionBrowserSearchScreen.kt
		WebSessionHistorySheet.kt
		WebSessionBookmarkSheet.kt
		WebSessionDownloadSheet.kt
		WebSessionUserscriptSheet.kt
		WebSessionSheetDecor.kt
		chrome/
			WebSessionBrowserBottomBar.kt
			WebSessionBrowserBottomDrawer.kt
			WebSessionBrowserToolbox.kt
			WebSessionBrowserTabOverview.kt
			WebSessionBrowserChromeLayout.kt
```

## 迁移与删除规则

- `WebSessionBrowserScreen.kt` 保持共享页面装配器，确保 App Shell 和 overlay 使用同一 chrome。
- 新的 `chrome/` 只处理浏览器视觉结构和瞬态交互，不依赖 AI 对话或 App Shell 具体页面。
- 现有 `WebSessionBottomToolbar.kt`、`WebSessionMenuSheet.kt` 和 `WebSessionTabSheet.kt` 在新组件接线完成后删除，不保留两套入口。
- 历史、收藏、下载和 Userscripts 详情组件保留原文件和逻辑，只调整受约束高度下的布局能力。
- `core/tools/defaultTool/websession/browser/` 不接收主题、尺寸或 Compose 动画逻辑。
- `KiyoriShellState.showsBottomBar` 明确排除 Browser Home；App Shell 删除 Browser Home 的 `80dp` 底部 padding。

## 内核与 capability 决策

本轮继续使用 `android.webkit.WebView`。设备 Android System WebView 或 Chrome provider 决定实际 Chromium 能力，`androidx.webkit` 提供 userscript 所需能力检测。X5/TBS 旧源不进入本轮依赖图。

AI 工具通过 `ToolRegistration` 调用 `ToolGetter.getBrowserSessionTools(context).invoke(tool)`；Browser Home 的 coordinator 取得相同共享实例。UI 重构不引入 AI 专用 adapter 分支，也不让 AI 直接操作 Composable。

## 依赖方向

```text
Kiyori App Shell
	-> Browser presentation lease
		-> shared WebSession BrowserScreen
			-> chrome and feature drawers

AI browser tools
	-> shared StandardBrowserSessionTools
		-> session registry and active WebView
		-> the same host-state projection observed by BrowserScreen
```

依赖只能向共享 runtime 汇合，不能从 AI 工具或 Browser Home 各自长出第二套 session。
