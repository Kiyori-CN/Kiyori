---
status: verification_pending
ui_contract_superseded_by: ../kiyori_browser_ui_refactor/index.md
---

# 界面、导航与 source-port

## Browser Home 页面结构

```text
Kiyori App Shell/
	Browser Home/
		WebSessionBrowserTopBar
		ExternalOpenPrompt 或 DownloadSummary
		活动 WebView
		WebSessionBottomToolbar
	KiyoriBottomNavigation
```

本节记录首期接入时的界面基线。首期让浏览器工具栏位于 Kiyori 五项底栏上方；该临时界面合同已由 [浏览器沉浸式 UI 重构](../kiyori_browser_ui_refactor/index.md) 取代。共享 runtime、地址解析、Back 与 presentation owner 结论继续有效。

## 按钮语义

| 控件 | App Shell 行为 | Overlay 行为 |
| --- | --- | --- |
| 地址栏 | 编辑并提交当前标签 | 相同 |
| 收藏 | 切换当前 URL 书签 | 相同 |
| 刷新/停止 | 依据加载状态执行 | 相同 |
| 最小化 | 返回软件首页，保留标签 | 最小化为 indicator |
| 后退/前进 | 操作活动 WebView 历史 | 相同 |
| 新标签 | 创建并激活 `about:blank` | 相同 |
| 标签 | 打开共享标签 sheet | 相同 |
| 菜单 | 历史、书签、下载、脚本、UA 与关闭操作 | 相同 |

首次进入 Browser Home 且没有标签时创建一个空白标签。关闭最后标签后仍停留当前页面并显示现有无标签状态。

## 地址与网页搜索

- 完整 `http://`、`https://` 与允许的 `about:` 地址直接使用
- 无空格的常规主机名补全 `https://`
- 普通文本转换为固定搜索引擎 URL
- 提交复用活动标签，不暗中新建标签
- 软件首页全屏网页搜索后续调用同一解析器和打开命令

## Back 优先级

| 优先级 | 当前状态 | Back 结果 |
| --- | --- | --- |
| 1 | 网页 JS dialog | 拒绝并关闭对话框 |
| 2 | 外部应用打开确认 | 取消本次请求 |
| 3 | tabs/menu/history/bookmarks/downloads/userscripts sheet | 关闭 sheet |
| 4 | 地址编辑 | 取消编辑并恢复当前 URL |
| 5 | WebView 可后退 | 网页后退 |
| 6 | Browser Home 根 | 返回 Software Home |

底部主入口切换不执行 Back，不关闭标签，不重置页面。首期不增加根页面转场；WebView owner 切换必须无导航副作用。

## 外部入口

`ACTION_VIEW` 的 HTTP/HTTPS URL 是浏览器打开请求：复用活动标签；没有标签时创建一个；Shell 切到 Browser Home。它不能再写入 `pendingSharedText`。

`ACTION_SEND` 与 `ACTION_SEND_MULTIPLE` 的文本和文件仍属于聊天分享，不改变既有语义。

## 旧 Kiyori 对照

| 旧源 | 保留语义 | 首期处理 |
| --- | --- | --- |
| `BrowserActivity` | 外部 URL、恢复、Back | 适配进 MainActivity 与 App Shell |
| `BrowserProcessLaunchPolicy` | 冷启动不重复创建窗口 | 首期用无标签才建空白标签 |
| `BrowserWindowRepository` | 稳定窗口、预览、隐私窗口 | 后续 source-port |
| `BrowserScreen` | 覆盖层优先 Back、浏览器底栏 | 先映射到现有 WebSession 状态 |
| `BrowserTopBar` | 地址、搜索引擎、搜索记录 | 首期只迁移网址/搜索解析 |
| history/bookmark/download/permission | 单一 owner | 复用当前 WebSession owner |
| X5 与媒体能力 | 内核、嗅探、播放交接 | 不进入首期 |

旧页面的硬编码视觉、Activity 宿主和 X5 类型不迁移。后续 UI source-port 必须保留本轮共享运行时与 Capability API，不能重新建立页面私有状态。
