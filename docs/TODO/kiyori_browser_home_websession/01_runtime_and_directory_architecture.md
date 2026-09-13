---
status: verification_pending
---

# 运行时与目录架构

## 内核决策

首期继续使用 `android.webkit.WebView`。实际 Chromium 版本由设备的 Android System WebView 或 Chrome provider 决定，桌面和移动 UA 只是站点身份字符串。

旧 `kiyori-android` 的 X5/TBS 不能直接移植。当前 AI 浏览器自动化、截图、事件、WebViewClient、文件选择和 Compose 宿主都依赖 `android.webkit.WebView`；增加 X5 会形成第二内核或要求重写 AI 合同。本轮只迁移旧项目的页面与状态语义。

## 长期目录

```text
app/src/main/java/com/ai/assistance/operit/
	core/
		browser/
			domain/
			session/
			engine/
			navigation/
			permissions/
			downloads/
			history/
			userscript/
			presentation/
		capability/
			browser/
				api/
				adapter/
		tools/
			defaultTool/
				websession/
					browser/
						ai/
						compat/
	ui/
		features/
			browser/
				appshell/
				components/
				sheets/
				overlay/
```

首期只新建 `core/browser/navigation/`、`core/browser/presentation/` 和 `ui/features/browser/appshell/`。现有数千行 AI 自动化保留原位，通过窄协调器接入，后续再按能力边界迁移。

## 验证目录

```text
app/src/test/java/com/ai/assistance/operit/
	core/
		browser/
			navigation/
			session/
			presentation/
		capability/
			browser/
app/src/androidTest/java/com/ai/assistance/operit/
	core/
		browser/
			engine/
	ui/
		features/
			browser/
				appshell/
```

- JVM 测试验证地址解析、session 命令、owner 状态机和 Capability API，不创建 Android `WebView`
- instrumentation 测试验证真实 WebView attach/detach、页面历史、文件选择和 App Shell 展示边界
- 真机清单验证设备 WebView provider、系统权限、下载存储、输入法、媒体状态与 AI 同标签操作
- 首期没有引入新测试任务；本轮只执行用户明确授权的 Debug 构建，测试目录随对应模块迁移时建立

## 依赖规则

- `domain` 只定义稳定 ID、状态、命令、结果和错误，不依赖 Compose、Activity、WindowManager 或 AI 工具
- `engine` 包装 Android WebView 配置和回调，不拥有产品导航
- `session` 拥有标签集合、活动标签和 WebView 生命周期
- `history`、`downloads`、`permissions` 与 `userscript` 各有唯一数据 owner
- `presentation` 只协调 App Shell 与 overlay 的展示租约
- `capability/browser/api` 面向人和 AI 提供相同命令，不暴露页面组件
- App Shell UI 与 overlay UI 依赖 presentation；AI 工具只依赖 capability/compat adapter

## 首期唯一状态

| 状态 | 唯一 owner | App Shell | Overlay | AI 工具 |
| --- | --- | --- | --- | --- |
| 标签与活动标签 | 共享 WebSession runtime | 观察和操作 | 观察和操作 | 通过同一实例操作 |
| 活动 WebView | 共享 session | 持有展示租约时挂载 | 持有展示租约时挂载 | 不创建副本 |
| Cookie 与网页缓存 | Android WebView profile | 共用 | 共用 | 共用 |
| 历史与书签 | `WebSessionHistoryStore` | 共用 | 共用 | 共用 |
| 下载 | `BrowserDownloadManager` | 共用 | 共用 | 共用 |
| 用户脚本 | `WebSessionUserscriptManager` | 共用 | 共用 | 共用 |
| 页面 sheet 与 prompt | 共享 host state | 当前展示 | 当前展示 | 只触发状态 |

## Presentation 状态机

| 当前 owner | 事件 | 下一 owner | 要求 |
| --- | --- | --- | --- |
| 无 | 进入 Browser Home | APP_SHELL | 不创建 overlay |
| OVERLAY | 进入 Browser Home | APP_SHELL | 隐藏 indicator，WebView 转挂 |
| APP_SHELL | 切换底部入口 | OVERLAY 或无 | 只在 overlay 原本存在时恢复 |
| APP_SHELL | AI 操作 | APP_SHELL | 不检查悬浮窗权限 |
| 无或 OVERLAY | AI 新建/导航 | OVERLAY | 保持既有权限合同 |

WebView 转挂只允许 `removeView` 与 `addView`，禁止同时拥有两个父 View，禁止在 owner 切换中调用 `loadUrl`、`reload`、`destroy` 或重建 WebView。

## 数据与恢复

- 历史、书签和桌面模式继续写入 `web_session_browser_store`
- 下载任务继续写入应用内部 `browser_download_tasks.json`
- 新文件下载到 `Download/Kiyori/browser/downloads/`
- 标签和 WebView 首期保持进程内存语义，进程死亡恢复列入后续窗口 source-port
- 普通窗口恢复、预览和隐私窗口不能通过复制现有标签状态临时实现

## 安全门禁

- SSL 错误取消加载，不允许 `SslErrorHandler.proceed()`
- 外部 Intent 继续要求单次确认
- 页面权限继续经透明权限 Activity 与 Android runtime permission
- JS bridge、WebView debugging、file access 和 renderer crash 行为进入后续安全审计
