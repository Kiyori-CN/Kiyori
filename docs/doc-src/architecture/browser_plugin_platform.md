---
status: accepted_detailed_design
implementation: phase_0_local_verified
next_gate: phase_0_device_acceptance
last_updated: 2026-07-31
---

# 浏览器插件平台与插件中心架构

## 1. 文档职责

本文是 Kiyori 浏览器插件平台的正式架构合同，回答以下问题：

- 浏览器下拉抽屉中的“插件”按钮管理什么
- 油猴脚本、Kiyori 原生浏览器扩展、Chrome / Edge WebExtension 和 AI ToolPkg 如何分域
- 插件包、权限、安装、更新、执行、诊断和 UI 由谁持有
- AI 如何创建和维护脚本或插件，同时不绕过安全审查
- 在 Android System WebView 上能真实实现什么，哪些能力必须明确拒绝
- 从现有里程碑一推进到可安装、可运行、可更新的真实浏览器扩展需要经过哪些门禁

实现进度、逐项任务和验收证据记录在
[浏览器插件平台实施计划](../../TODO/browser_plugin_platform/index.md)。本文定义长期不变量，不把计划写成
已经完成的事实。

## 2. 最终产品结论

浏览器下拉抽屉菜单中的“插件”按钮进入 Kiyori Browser Plugin Center。它是浏览器产品域的统一控制面，
负责发现、搜索、查看、安装、更新、启停、卸载、配置和诊断浏览器插件；它不是 AI 对话页的 ToolPkg、
Skill、MCP、工作流或包管理入口。

插件中心采用分层模型：

- “油猴脚本”是内置且不可卸载的浏览器插件，它管理多个 userscript
- 单个 userscript 是油猴脚本插件内部的脚本项目，不在顶层冒充 Chrome 扩展
- Kiyori 原生浏览器扩展是独立顶层插件，使用 `.kbx` 包和受控运行时
- WebExtension 导入是一条兼容性分析与转换通道，不直接执行 CRX、浏览器商店包或任意 ZIP
- 内置浏览器能力可以通过系统 Provider 投影到插件中心，但不能因此复制其状态和运行时

插件平台继续依附唯一 Browser Runtime：

- `StandardBrowserSessionTools.sessions`
- `StandardBrowserSessionTools.sessionOrder`
- `StandardBrowserSessionTools.activeSessionId`
- 每个 `BrowserToolSession` 持有的真实 WebView、Profile、Cookie、导航和页面状态
- 现有历史、书签、下载、媒体 candidate、userscript 和 AI `browser_*` 工具

禁止创建第二个浏览器、第二份标签页注册表、第二个活动页状态、第二个 userscript 仓库，或只供插件运行的
平行 WebSession。

## 3. 已验证事实

### 3.1 Kiyori 当前实现

进入 Phase 0 前的 2026-07-30 基线如下：

- 项目使用 Android System WebView 和 `androidx.webkit:webkit:1.12.1`
- 最低 Android API 为 26
- userscript 已实现 document-start 注入、WebMessage、匹配规则、三种 `run-at`、GM storage、
  GM XHR、Cookie、下载、通知、标签页、页面菜单、`@require`、`@resource` 和请求观察或修改
- userscript 当前通过 `setOf("*")` 注册注入和消息来源，消息只携带可由网页自行构造的 `scriptId`
- userscript 安装先覆盖正式脚本文件，再下载资源和更新注册表；脚本、注册表和日志均不是完整原子事务
- QuickJS 已存在单线程执行器、pending jobs 和 native interrupt，但 `NativeInterface` 使用反射分发，
  JNI 未设置 runtime memory limit 和 max stack size
- ToolPkg 有 manifest、ZIP 读取和 QuickJS 运行经验，但它属于 AI 产品域，现有路径规范化也不足以直接作为
  不可信浏览器插件安装器
- Browser Plugin Center 里程碑一只有 userscript 一种硬编码类型，当前 UI 仍需改成 Provider 驱动

### 3.2 hikerView 参考边界

`D:\10_Project\hikerView` 可确认以下产品能力：

- 插件列表、搜索、插件库、新增和编辑入口
- 单项启停、批量启停、删除、分享、排序和加载时机
- 插件跟随 WebView 页面生命周期注入
- 编辑器具有查找、撤销、重做、格式化和大文本限制

hikerView 开源版本缺少核心 `JSManager`、`JsPluginHelper` 等完整实现。旧 Activity、文件名协议、事件总线、
X5 WebView 和“按文件名隐含域名或加载时机”的做法不作为 Kiyori 的运行时设计。Kiyori 只吸收已经能从
调用点验证的能力边界。

### 3.3 当前平台能力

AndroidX WebKit 1.16.0 已于 2026-07-29 发布，新增按 frame 和 JavaScript execution world 注入脚本，
并允许在指定 execution world 中注册 WebMessage listener。隔离世界与网页共享 DOM，但 JavaScript
对象图相互隔离。

这使 System WebView 上的受控浏览器扩展成为可实施方案，但不等于获得桌面 Chrome 或 Edge 的完整扩展宿主。
Kiyori 仍需自己实现 manifest、权限、storage、消息、background worker、action 和生命周期。

Chrome Manifest V3 的稳定结构包括 content scripts、extension service worker、permissions、
host permissions、optional permissions、storage 和消息机制。Kiyori 采用相同字段形状的严格子集，
目的是减少转换成本，不宣称 API 等价。

Microsoft Edge for Android 已公开一组移动扩展 API，但这些 API 由 Edge 自身提供，不能嵌入 Kiyori 的
System WebView。该支持矩阵只用于确定未来兼容优先级。

GeckoView 提供正式的 WebExtension 接入能力。将浏览器引擎整体迁移到 GeckoView 能提升兼容上限，但会同时
影响现有 WebView Profile、Cookie、下载、历史、媒体嗅探、AI 页面操作、悬浮展示和所有页面注入。它不是
当前插件里程碑中的局部改动。

### 3.4 沉浸式翻译样本

2026-07-30 从沉浸式翻译官方 userscript 下载地址取得的样本为 4,511,139 字节，metadata 标记
`@inject-into content`，声明 GM storage、GM XHR、菜单、样式、元素创建和大量 `@connect` 域名。

这说明：

- 它更适合作为 userscript 隔离世界能力的首个高价值兼容目标
- userscript 运行时必须具备明确的 `@inject-into` 语义
- 安装器必须设置足够但有限的脚本、`@require` 和资源大小上限
- 只有真实安装、页面翻译、设置持久化、网络请求和更新检查通过后，才能宣称兼容

### 3.5 Phase 0 本地实施事实

截至 2026-07-31，本地工作树已经完成以下实现：

- `androidx.webkit` 升级到 `1.16.0`
- 页面世界只承载无特权 userscript，页面桥只接受 bootstrap
- 每个 WebView 使用一个生命周期固定的 userscript 隔离世界、一个 bridge 和一个 document-start handler；
  安装列表或授权变化不再移除、重建 Chromium 原生注册
- 每个 frame 只接受一次 bootstrap；共享隔离运行时中的每个脚本仍使用独立随机 capability token，并绑定
  真实 userscript ID 和 grant
- 导航、session 关闭、脚本禁用或权限撤销会清理旧 token、reply proxy、菜单和 webRequest 状态；
  WebView 关闭时由紧邻的 `WebView.destroy()` 一并退休 provider 注册
- document-start 匹配使用注入文档报告的 `location.href`；隔离世界可直接信任自身 URL，页面世界必须与
  WebMessage 的 `sourceOrigin` 一致，不再依赖可能指向上一文档的 `WebView.url`
- 无 `@match/@include` 的脚本按全页面匹配；`@include/@exclude` 支持正则形式，`@match` 不把 query 和
  fragment 混入路径判断
- storage、菜单、webRequest、标签页关闭通知和音频状态按目标 userscript 或明确 grant 分发
- 标签页控制、XHR 取消和 webRequest 注销增加对象所有权校验
- `@inject-into` 和未知 `@run-at` 使用严格解析；未实现的 `@sandbox`、`@run-in` 和 `@unwrap`
  会显示不兼容并保持禁用
- 内置油猴插件增加持久化的“允许用户脚本”总授权；关闭后现有 WebView 生命周期注册保持惰性且不返回
  脚本 payload，同时撤销 capability token、reply proxy、页面菜单、webRequest 与活动 GM 网络请求
- `unsafeWindow` 与特权 grant 在 `auto` 或 `content` 模式下继续运行于 WebView 的共享隔离运行时，通过不含
  native bridge 和 capability token 的同步页面对象桥访问主世界
- 页面对象桥支持属性读写、方法调用、构造、回调、Promise、普通对象、数组和 DOM 节点参数；单次
  请求或响应限制为 1 MiB，单文档页面对象引用限制为 4096
- Plugin Center 与 userscript 安全相关定向 JVM 测试为 46/46，通过 `:app:compileDebugKotlin`
- formal readiness、7 份本地化 XML 与 34 个 Markdown 相对链接检查通过
- `:app:assembleDebug` 成功；`app-debug.apk` 为 494,083,710 字节，SHA-256 为
  `21917BD1C1CF80E1B00128A621DABE6CDB9110B56067C6B29331C0F3EA29E90D`

这些是源码、单元测试和本地编译证据。Android 设备上的 WebViewFeature、真实 isolated world、导航时序、
iframe origin 和沉浸式翻译行为仍需单独验收。

## 4. 方案比较与定案

### 4.1 方案 A：只增强 userscript

优点：

- 改动小
- 能较快支持大量 Greasy Fork 和 ScriptCat 脚本
- 现有代码已经具备较多 GM API

缺点：

- 无法表达独立 background worker、浏览器 action、扩展级 storage 和更完整的生命周期
- 无法形成 Kiyori 自己的浏览器插件 SDK
- 用户要求的“油猴只是一个插件”无法完整成立

结论：userscript 是近期重点，但不能作为平台终点。

### 4.2 方案 B：System WebView 加 Kiyori 扩展运行时

优点：

- 保留唯一 Browser Runtime 和现有页面、下载、AI、历史、媒体、Profile 能力
- AndroidX WebKit 1.16.0 已提供隔离世界基础
- 可以采用 Manifest V3 字段形状，逐步扩大真实兼容面
- 可以为 AI 创作提供稳定 SDK、静态检查和测试合同

缺点：

- Kiyori 必须自行实现扩展 API
- System WebView 仍有明确能力上限
- 每个 API 都需要权限、生命周期、测试和设备验证

结论：采用此方案。

### 4.3 方案 C：整体迁移 GeckoView

优点：

- 已有正式 WebExtension 接入层
- 隔离世界、content script 和 background 能力更接近 Firefox

缺点：

- 属于浏览器引擎迁移，不是插件按钮迭代
- 会影响当前唯一 Browser Runtime 的几乎所有消费者
- 需要重新验证 Cookie、Profile、下载、历史、媒体、AI 操作、悬浮展示和页面截图

结论：保留为独立长期项目。只有未来明确把“广泛 WebExtension 兼容”提升为浏览器引擎级目标时再立项。

### 4.4 方案 D：WebView 与 GeckoView 双引擎

该方案会产生两份 Cookie、历史、页面状态、插件兼容矩阵、下载行为、AI 目标页和设备问题，直接破坏当前
唯一 Browser Runtime 不变量。

结论：明确拒绝。

### 4.5 方案 E：直接解压并运行 CRX 或商店扩展

CRX 或商店 ZIP 可能依赖未实现的 API、远程代码、扩展页面、service worker、DNR、side panel、
offscreen document、native messaging 或企业策略。

结论：明确拒绝直接执行。导入流程必须先形成兼容报告；只有转换为通过验证的 `.kbx` 包后才能安装。

## 5. 产品术语

### Browser Plugin

插件中心中的顶层能力单元。它具有稳定 ID、名称、版本、来源、状态、权限、支持动作和当前页投影。

### Built-in Plugin

由 Kiyori 提供、不可卸载但可配置的系统插件。油猴脚本管理器属于此类。

### Userscript Item

由油猴脚本插件管理的脚本项目。它具有独立 metadata、来源、版本、权限、匹配规则、运行状态和历史版本。

### Kiyori Browser Extension

使用 `.kbx` 包安装的顶层浏览器扩展。它采用 Manifest V3 字段形状的 Kiyori 严格子集。

### WebExtension Import

对 Chrome、Edge 或 Firefox 扩展包执行解析、兼容分析和转换的过程。导入结果可能为兼容、需要修改或拒绝。

### Provider

把某类插件的真实状态和命令映射到统一插件中心的适配器。Provider 不拥有 Browser Runtime。

### Runtime Coordinator

挂接 WebSession 生命周期、隔离世界、worker、消息和权限检查的运行时协调器。它由
`StandardBrowserSessionTools` 创建和持有。

### Draft

AI 或用户正在编辑但尚未安装的 userscript 或 `.kbx` 项目。Draft 与正式运行目录完全分离。

## 6. 总体分层

```text
Browser Plugin Center UI
	BrowserPluginCenterStateStore
	BrowserPluginCatalog
		UserscriptPluginProvider
		KiyoriExtensionProvider
		SystemBrowserPluginProvider
	BrowserPluginCommandBus

StandardBrowserSessionTools
	BrowserPluginRuntimeCoordinator
		WebSession lifecycle adapter
		Document lease registry
		Content script injector
		Extension message router
		Background worker supervisor
		Extension action dispatcher

Persistence
	UserscriptRepository
	BrowserExtensionRepository
	BrowserPluginPermissionStore
	BrowserPluginTransactionStore
	BrowserPluginDiagnosticsStore
	BrowserPluginDraftRepository

Installation
	BrowserPluginSourceResolver
	BrowserPluginPackageValidator
	WebExtensionCompatibilityAnalyzer
	BrowserPluginPermissionDiff
	BrowserPluginInstaller
	BrowserPluginUpdateCoordinator
```

### 6.1 唯一所有者

| 事实 | 唯一所有者 |
| --- | --- |
| 标签页、活动页、WebView、Profile、Cookie | `StandardBrowserSessionTools` 与 `BrowserToolSession` |
| userscript 列表、源码、资源、值和日志 | `UserscriptRepository` |
| userscript 页面执行 | `WebSessionUserscriptManager` |
| `.kbx` 包、版本和安装状态 | `BrowserExtensionRepository` |
| 插件授权 | `BrowserPluginPermissionStore` |
| 插件运行 | `BrowserPluginRuntimeCoordinator` |
| 插件统一投影 | `BrowserPluginCatalog` |
| 插件中心展示状态 | `BrowserPluginCenterStateStore` |
| AI 草稿 | `BrowserPluginDraftRepository` |

Catalog、Provider 和 UI 都不能直接写运行目录或注册表。

## 7. 顶层插件模型

统一投影使用强类型模型，不以字符串 Map 或 UI 判断代替状态：

```text
BrowserPluginDescriptor
	id
	providerId
	kind
	name
	description
	version
	icon
	source
	installationKind
	trustTier
	compatibility
	effectiveState
	declaredPermissions
	grantedPermissions
	hostAccess
	supportedActions
	installedAt
	updatedAt
	lastError

BrowserPluginPageProjection
	pluginId
	sessionId
	documentId
	matchState
	runtimeState
	actionBadge
	menuCommands
	lastError
```

### 7.1 插件类型

- `BUILT_IN_USERSCRIPT_MANAGER`
- `KIYORI_EXTENSION`
- `BUILT_IN_BROWSER_CAPABILITY`

WebExtension 不是运行时类型。兼容导入成功后，它成为 `KIYORI_EXTENSION`，并保存原始兼容来源信息。

### 7.2 安装类型

- `BUILT_IN`
- `USER_INSTALLED`
- `DEVELOPER_UNPACKED`
- `MANAGED`

### 7.3 有效状态

- `ENABLED`
- `DISABLED`
- `UPDATE_AVAILABLE`
- `PERMISSION_REQUIRED`
- `INCOMPATIBLE`
- `BROKEN`
- `UNSUPPORTED_RUNTIME`

UI 只依据 `supportedActions` 显示动作。没有真实 consumer 的动作不显示。

## 8. Provider 合同

Provider 注册到唯一 Catalog：

```text
BrowserPluginProvider
	providerId
	state
	listPlugins(context)
	getPlugin(pluginId, context)
	execute(command)
	observePageProjection(sessionId)
```

命令必须为 sealed 类型：

- `OpenPlugin`
- `SetPluginEnabled`
- `RequestPluginUpdate`
- `ConfirmPluginUpdate`
- `UninstallPlugin`
- `OpenPluginSettings`
- `InvokePluginAction`
- `GrantOptionalPermission`
- `RevokeOptionalPermission`
- `OpenDiagnostics`
- `OpenManagedItems`

Provider 返回结构化结果：

- 成功结果和新状态
- 需要用户确认的权限差异
- 不兼容原因
- 可定位的错误码、插件 ID、版本、session、document 和日志引用

Provider 不接受任意方法名，不允许反射调用。

## 9. `.kbx` 包格式

### 9.1 基本格式

- 文件扩展名：`.kbx`
- MIME：`application/vnd.kiyori.browser-extension+zip`
- 容器：标准 ZIP
- manifest：包根目录 `manifest.json`
- manifest 字符编码：UTF-8
- 可执行代码必须随包分发
- 远程地址只能用于数据、更新描述和已声明资源，不能在运行时下载新的可执行脚本

### 9.2 目录

```text
example.kbx
	manifest.json
	content/
		main.js
		main.css
	background/
		service-worker.js
	icons/
		16.png
		32.png
		48.png
		128.png
	locales/
		zh-CN.json
		en.json
	tests/
		manifest-tests.json
	META-INF/
		kiyori-signature.json
		kiyori-signature.bin
```

`tests/` 和 `META-INF/` 为可选目录。安装器忽略未声明资源，但仍计入大小和路径安全检查。

### 9.3 Manifest 顶层

Kiyori v1 使用 `manifest_version: 3`，并要求 `kiyori` 扩展字段：

```json
{
  "manifest_version": 3,
  "name": "Example",
  "version": "1.0.0",
  "description": "Example extension",
  "permissions": ["storage"],
  "host_permissions": ["https://example.com/*"],
  "optional_permissions": [],
  "optional_host_permissions": [],
  "content_scripts": [
    {
      "id": "main",
      "matches": ["https://example.com/*"],
      "exclude_matches": [],
      "js": ["content/main.js"],
      "css": ["content/main.css"],
      "run_at": "document_end",
      "world": "ISOLATED",
      "all_frames": false
    }
  ],
  "background": {
    "service_worker": "background/service-worker.js",
    "type": "module"
  },
  "action": {
    "default_title": "Run"
  },
  "kiyori": {
    "schema_version": 1,
    "id": "com.example.extension",
    "minimum_runtime_version": 1,
    "options_schema": "options.schema.json"
  }
}
```

### 9.4 ID 与版本

- `kiyori.id` 为稳定 ID，格式为小写反向域名或明确命名空间
- 长度为 3 至 128 个字符
- 只允许小写字母、数字、点、连字符和下划线
- 更新必须保持相同 ID
- `version` 使用四段以内的数字版本，比较规则在 SDK 中固定
- `minimum_runtime_version` 针对 Kiyori Browser Extension Runtime，不使用应用展示版本替代

### 9.5 v1 支持字段

- `manifest_version`
- `name`
- `version`
- `description`
- `icons`
- `permissions`
- `host_permissions`
- `optional_permissions`
- `optional_host_permissions`
- `content_scripts`
- `background.service_worker`
- `background.type`
- `action.default_title`
- `action.default_icon`
- `web_accessible_resources`
- `default_locale`
- `kiyori`

以下字段在 v1 兼容分析中明确报告为不支持：

- `action.default_popup`
- `options_ui`
- `side_panel`
- `offscreen_documents`
- `devtools_page`
- `chrome_url_overrides`
- `declarative_net_request`
- `externally_connectable`
- `native_messaging`
- `sandbox`
- `commands`
- `omnibox`

Kiyori 原生设置使用 `kiyori.options_schema` 生成 Compose 表单，不执行任意 options HTML。

### 9.6 静态兼容分析

分析器输出：

- manifest 字段支持情况
- 声明权限支持情况
- `chrome.*` 和 `browser.*` API 引用
- content script world、frame 和 run-at 支持情况
- background worker 导入图
- 远程可执行代码迹象
- CSP、动态 `eval`、`new Function` 和 WASM 使用情况
- 包大小、条目数、重复路径和大小写冲突
- 需要转换的字段
- 阻止安装的原因

静态分析不能证明功能完整。外部 WebExtension 只有经过目标功能测试并进入已验证兼容清单后，才显示
“已验证兼容”。

## 10. 持久化目录

浏览器插件的可执行代码、注册表、授权和私有数据使用应用内部目录，不写入 ToolPkg 目录，也不把
`Download/Kiyori` 当作正式运行目录：

```text
context.filesDir/
	browser-userscripts/
		registry/
		revisions/
		resources/
		values/
		diagnostics/
		staging/
	browser-plugins/
		registry/
			plugins.json
			transactions.json
		packages/
			<plugin-id>/
				<content-hash>/
		revisions/
			<plugin-id>/
		data/
			<plugin-id>/
				local.json
				session/
		cache/
		diagnostics/
		drafts/
		staging/

Download/Kiyori/
	browser-plugins/
		imports/
		exports/
```

正式实现时通过 `OperitPaths` 增加需要 `Context` 的内部目录入口，以及显式导入导出的公共目录入口。
UI 和 Provider 不拼接路径。现有 `Download/Kiyori/websession/userscripts` 只作为一次性迁移来源，迁移完成后
不再由 runtime 执行其中的源码。

### 10.1 不变量

- 已安装版本目录不可原地修改
- registry 只保存 active content hash 和元数据，不保存大段源码
- staging 与正式目录位于同一文件系统，保证最终目录切换可以原子完成
- draft 不可被 runtime 加载
- developer unpacked 插件记录真实目录和指纹，不复制到正式包目录
- 每个插件只能访问自己的 `data/<plugin-id>/`

## 11. 安装事务

### 11.1 状态

```text
CREATED
SOURCE_RESOLVED
DOWNLOADED
STAGED
VALIDATED
AWAITING_APPROVAL
COMMITTING
COMMITTED
ACTIVATED
FAILED
CANCELLED
```

### 11.2 流程

1. `BrowserPluginSourceResolver` 把 URL、文件、SAF Uri、catalog item 或 AI draft 解析为输入流
2. 下载到 `staging/<transaction-id>/source.part`
3. 同步计算 SHA-256、压缩大小和来源元数据
4. 安全解包到 `staging/<transaction-id>/package/`
5. 严格解析 manifest、路径、权限、资源和 API
6. 生成兼容报告、权限差异和代码摘要
7. 用户确认安装或更新
8. 在全局 installer mutex 和插件级 mutex 下写入不可变候选版本目录
9. 在候选版本上执行 runtime health check，不向现有 session 注入
10. 使用临时文件、flush、文件同步和同目录 rename 更新 registry
11. 激活新版本并发布状态；激活失败则标记 `BROKEN` 并保留诊断，不自动切换到其他版本

用户取消、验证失败或提交前异常时，正式版本不发生变化。

### 11.3 ZIP 安全

安装器必须拒绝：

- 绝对路径
- `..` 路径段
- Windows drive、UNC 和反斜杠混淆路径
- 符号链接、硬链接和设备文件
- 空路径和目录穿越
- Unicode 规范化后冲突
- 不区分大小写后冲突
- 重复条目
- 加密 ZIP
- 超过条目数、单项大小、展开大小或压缩比限制的包

v1 建议限制：

| 项目 | 上限 |
| --- | --- |
| 压缩包 | 50 MiB |
| 展开总量 | 100 MiB |
| 单条目 | 20 MiB |
| manifest | 256 KiB |
| 单 JS 文件 | 10 MiB |
| 条目数 | 4096 |
| 压缩比 | 100:1 |

限制作为 SDK 常量和测试合同实现，不能散落在 UI。

## 12. 更新与版本历史

### 12.1 更新来源

更新来源由安装记录持有，不允许插件运行时代码自行改写：

- Kiyori catalog
- 用户确认的 HTTPS update URL
- 本地文件
- developer unpacked directory

HTTP 更新地址不允许进入受信任自动检查。

### 12.2 更新门禁

更新必须比较：

- ID 和签名身份
- 版本
- manifest
- 必需权限
- 可选权限
- host permissions
- executable code hash
- 新增与删除文件
- 远程资源

权限扩大时进入 `AWAITING_APPROVAL`。权限未变化的更新仍显示版本和代码摘要，是否允许自动下载由用户设置；
激活始终经过完整验证。

### 12.3 历史版本

- 每个已安装版本保持不可变
- 默认保留最近 3 个成功版本，具体清理只删除未激活且不被事务引用的版本
- 用户可以从详情页显式选择历史版本并查看差异
- 不做安装失败后的自动版本切换
- 版本清理、数据清理和卸载互相独立

## 13. 信任与签名

### 13.1 信任层级

- `BUILT_IN_VERIFIED`
- `CATALOG_SIGNED`
- `SOURCE_PINNED`
- `LOCAL_UNSIGNED`
- `DEVELOPER_UNPACKED`

信任层级只描述来源和验证，不代表代码无风险。

### 13.2 签名

正式 catalog 包支持 Ed25519：

- 签名覆盖规范化 manifest、全部文件路径、大小和 SHA-256
- `META-INF/kiyori-signature.json` 保存算法、key ID 和文件清单
- 更新必须使用相同 signer，除非用户执行明确的身份迁移
- 本地 unsigned 包只允许在开发者模式或一次性高级确认后安装
- 签名校验失败直接拒绝安装

首个可运行阶段可以只实现 unsigned 本地包，但 schema、目录和 registry 必须预留 signer 字段，避免后续迁移
插件身份。

## 14. 权限模型

### 14.1 权限分类

低风险：

- `storage`
- `runtime`
- `i18n`

中风险：

- `notifications`
- `downloads`
- `clipboardWrite`
- `tabs`

高风险：

- `<all_urls>`
- `cookies`
- `webRequest`
- `scripting`
- `history`
- `bookmarks`

v1 明确不支持：

- `webRequestBlocking`
- `proxy`
- `nativeMessaging`
- 任意文件系统
- Android shell
- Kiyori AI 凭据
- 其他插件私有数据

### 14.2 Host permissions

Host permission 使用 Chrome match pattern 形状：

- `https://example.com/*`
- `https://*.example.com/*`
- `<all_urls>`

每次 host API 调用都重新检查：

- 插件是否启用
- 当前 session 与 document lease 是否仍有效
- URL 是否命中 host permission
- permission 是否已授权
- 调用是否来自正确 execution world

### 14.3 Optional permissions

- 只能由明确用户手势触发申请
- 申请弹窗显示权限含义、域名、触发插件和当前页面
- 拒绝后返回结构化错误
- 撤销后立即终止相关调用并通知 runtime
- 权限申请不能由 background worker 在不可见状态下弹出

### 14.4 `activeTab`

`activeTab` 是临时能力，不是永久 host permission：

- 由用户点击插件 action 或插件中心页面动作产生
- 绑定 plugin ID、session ID、document ID 和 origin
- 页面导航、标签关闭、插件禁用或权限撤销时失效
- 不能用于后台遍历所有标签

## 15. 内容脚本与隔离世界

### 15.1 原生扩展硬门槛

Kiyori 原生扩展要求以下 WebViewFeature：

- `JS_INJECTION_IN_FRAME_AND_WORLD`
- `WEB_MESSAGE_LISTENER`

项目升级到 AndroidX WebKit 1.16.0 后才能编译该运行时。设备当前 WebView 不支持上述 feature 时：

- userscript Provider 根据自己的能力继续显示真实状态
- Kiyori Extension Provider 显示 `UNSUPPORTED_RUNTIME`
- `.kbx` 可以完成静态检查，但不能启用
- 不把原生扩展改为页面主世界执行

### 15.2 Execution world

每个插件使用独立命名世界：

```text
kiyori-extension:<plugin-id>
```

同一插件的 content script 和消息桥在该世界中运行。不同插件之间不共享 JavaScript 对象。

### 15.3 run-at

运行时在 document start 注册最小 bootstrap：

- `document_start` 在 bootstrap 初始化后执行
- `document_end` 在 `DOMContentLoaded` 后执行
- `document_idle` 在 `load` 后进入空闲调度，并设置确定的最长等待时间

所有阶段都绑定 document ID。旧文档的异步结果不能写入新页面状态。

### 15.4 Frame

- v1 支持主 frame
- `all_frames: true` 只有在 API 能稳定标识 frame 和 source origin 后开放
- `match_about_blank` 和继承来源匹配在 v1 不支持
- frame 能力未通过设备测试时，兼容分析必须阻止依赖它的扩展安装

### 15.5 MAIN world

`world: MAIN` 允许执行页面互操作代码，但该代码不获得 Kiyori 特权 API。需要特权的逻辑放在
`ISOLATED` world 或 background worker，通过公开消息协议通信。

MAIN world 不能注册 native WebMessage listener，也不能取得 document capability token。

## 16. Document lease 与消息安全

每次主框架导航生成：

```text
DocumentLease
	sessionId
	navigationGeneration
	documentId
	pluginId
	executionWorld
	sourceOrigin
	capabilityToken
	createdAt
	revokedAt
```

消息格式：

```json
{
  "protocol": 1,
  "pluginId": "com.example.extension",
  "documentId": "uuid",
  "capabilityToken": "random-256-bit",
  "requestId": "uuid",
  "method": "storage.local.get",
  "payload": {}
}
```

Host 验证顺序：

1. session binding 仍存在
2. navigation generation 相同
3. document ID 与 lease 相同
4. plugin ID 与 execution world 相同
5. capability token 使用恒定时间比较
6. `sourceOrigin` 与 lease 及 host permission 相符
7. method 属于 API allowlist
8. payload 通过具体 schema
9. 权限仍有效

消息大小上限为 512 KiB。每个 document、插件和方法分别限流。错误返回稳定 error code，不返回内部路径、
Cookie、堆栈或其他插件信息。

## 17. userscript 安全模型

### 17.1 运行世界

userscript 增加 `@inject-into` 解析：

- `content` 对应隔离世界
- `page` 对应主世界
- `auto` 由 grant 和 metadata 按固定规则决定

规则：

- 声明 GM 特权 API的脚本默认进入隔离世界
- `@grant none` 进入主世界且没有 native bridge
- `@inject-into page` 与 GM 特权 grant 同时出现时标记不兼容
- `auto` 下只有 `unsafeWindow` 且没有特权 grant 时进入主世界并直接取得页面 `window`
- `auto` 下同时声明 `unsafeWindow` 与特权 grant 时进入 WebView 的共享隔离运行时，`unsafeWindow` 使用同步
  页面对象桥
- `content` 下声明 `unsafeWindow` 时保持隔离世界，`unsafeWindow` 使用同一页面对象桥
- 未知 `@inject-into` 和 `@run-at` 明确标记不兼容
- 当前未实现的 `@sandbox`、`@run-in` 和 `@unwrap` 明确标记不兼容并禁止启用
- 不在设备缺少隔离世界时把特权脚本放入主世界

### 17.2 “允许用户脚本”授权

Chrome 的 `userScripts` API 要求用户显式允许扩展运行用户脚本。Kiyori 将同类产品语义放在内置油猴
插件自身，不伪装成 Android 系统权限：

- 授权属于内置油猴插件，不属于单个网页、AI ToolPkg 或任意外部扩展
- 默认关闭，安装与更新仍可进行，但已启用脚本显示“需要授权”且不会注入页面
- 插件中心概览卡与油猴脚本管理页使用同一个持久化授权 owner
- 页面世界和共享隔离运行时在 WebView 创建时各注册一次，生命周期内不因授权、安装、更新、启停而动态
  移除或重建 Chromium handler/listener
- 关闭后原生注册保持惰性，不返回脚本 payload；同时清空授权、页面菜单和请求规则，并取消仍在进行的
  GM 网络请求
- 脚本自己的启用状态继续保留；重新授权后只恢复仍处于启用且兼容的脚本

“需要授权”和“不兼容”是两个独立状态。前者可由用户开关解决，后者由 metadata、grant 和设备能力
决定，不能通过权限开关绕过。

### 17.3 `unsafeWindow` 双世界合同

`unsafeWindow` 的正式语义是访问页面原始 `window`。特权脚本不能因此移入页面主世界，因为页面能够调用
同一世界中的 WebMessage 对象。Kiyori 使用双世界结构：

```text
页面主世界
	页面原始 window 和对象图
	同步 DOM 事件中继
	没有 native bridge
	没有 capability token

WebView 共享隔离运行时
	userscript 源码
	GM API
	单一 native bridge
	逐脚本 capability token 与 grant
	unsafeWindow 远程 Proxy
```

页面对象桥只执行页面本来就能执行的对象操作。它不接受 GM 方法名，不持有脚本授权，不读取 userscript
storage、Cookie、下载、通知或标签页能力。页面能够观察、延迟或篡改 `unsafeWindow` 操作，这是
`unsafeWindow` 自身的风险，不等于页面获得 native 权限。

当前桥接合同：

- 同步属性读取、写入、删除和 `in`
- 方法调用、构造和正确的页面对象接收者
- 普通对象、数组、日期、特殊数字和 BigInt
- 通过临时 DOM 引用传递页面节点
- 同步页面回调和 Promise `then` 链，因此支持常见 `await unsafeWindow.fetch(...)`
- 循环对象、Symbol、无法保真表示的隔离世界对象、超过 1 MiB 的消息和超过引用上限的文档明确报错

它不宣称 JavaScript realm 完全透明。对象 identity、prototype、`instanceof`、属性描述符和不可序列化
宿主对象仍需进入兼容样本测试。脚本详情必须展示“页面世界直连”或“隔离世界页面对象桥”，避免把两种
语义混为一谈。

### 17.4 现有桥整改

当前通配 origin 表示脚本可以按自身匹配规则运行在用户访问的不同页面，不表示宿主信任页面消息。
Phase 0 已实现：

- 每个 WebView 使用一个共享隔离世界、一个 bridge 和固定 document-start 注册
- 每个 frame 只接受一次 bootstrap，并签发随机 capability token
- 每个请求同时绑定 execution world、真实脚本 ID、token 和 grant
- Host 从当前已安装脚本和授权表重新解析 grant
- 页面主世界不能调用 storage、Cookie、下载、通知、标签页和 GM XHR
- 页面菜单和宿主事件只向共享世界中拥有对应 grant 的脚本 ID 分发
- 导航、session 关闭、脚本禁用和权限撤销清理旧授权；脚本列表变化不重建原生运行时
- 标签页、XHR 和 webRequest 对象操作校验创建者

通用 `.kbx` runtime 仍需要显式 document ID、navigation generation、`sourceOrigin` 与 host permission
绑定、消息大小限制和限流。userscript 的 Android instrumentation 也必须验证旧 document 消息在真实 WebView
导航时序中被拒绝。

### 17.5 userscript 安装

userscript 使用与 `.kbx` 相同的 transaction primitives：

- 原始脚本和 `@require`、`@resource` 先进入 staging
- 保存来源 URL、最终 URL、ETag、Last-Modified、SHA-256 和大小
- 所有可执行依赖在安装或更新时固定
- 验证全部成功后切换 active revision
- 未知 grant 或不支持 metadata 阻止启用
- 保存源码、权限和版本差异

建议限制：

| 项目 | 上限 |
| --- | --- |
| userscript 源码 | 10 MiB |
| 单个 `@require` | 5 MiB |
| 单个 `@resource` | 10 MiB |
| 远程依赖合计 | 50 MiB |
| metadata 行数 | 4096 |
| `@connect` 数量 | 512 |

## 18. Background worker

### 18.1 独立运行时

`.kbx` background worker 使用浏览器插件专用 QuickJS：

- 不复用 ToolPkg `JsEngine`
- 不绑定反射 `NativeInterface`
- 不暴露 Java class、文件系统、shell、ToolPkg、MCP 或 AI 工具
- Host API 只有显式 JSON-RPC allowlist
- 每个插件独立 runtime 和串行事件队列

### 18.2 Native 扩展

QuickJS JNI 需要增加：

- runtime memory limit
- max stack size
- deadline-aware interrupt
- pending job 上限
- host call 数量和嵌套深度限制
- runtime memory usage 诊断

v1 建议限制：

| 项目 | 上限 |
| --- | --- |
| QuickJS heap | 64 MiB |
| JS stack | 1 MiB |
| 单次同步执行 | 5 秒 |
| 单事件总时长 | 30 秒 |
| pending jobs | 1024 |
| 单条 host call payload | 512 KiB |
| 同一插件并发 host call | 8 |

### 18.3 生命周期

```text
STOPPED
STARTING
RUNNING
IDLE
STOPPING
CRASHED
DISABLED
```

启动事件：

- 安装完成
- 应用浏览器 runtime 启动
- action 点击
- content script 消息
- storage 变化
- tab 事件

worker 在事件完成且无 pending job、timer 或 host call 后进入 IDLE，再结束 runtime。状态必须持久化在 storage，
不能依赖 JS global 长期存在。

### 18.4 v1 API

优先实现：

- `runtime.id`
- `runtime.getManifest`
- `runtime.onInstalled`
- `runtime.onMessage`
- `runtime.sendMessage`
- `storage.local`
- `storage.session`
- `storage.onChanged`
- `tabs.get`
- `tabs.query`
- `tabs.sendMessage`
- `tabs.create`
- `tabs.update`
- `tabs.remove`
- `action.onClicked`

后续实现：

- `downloads`
- `notifications`
- `cookies`
- `history`
- `bookmarks`
- `webNavigation`
- observe-only `webRequest`

未实现 API 在安装兼容报告和运行时调用两处都必须明确报错。

## 19. 插件存储

### 19.1 storage.local

- 每插件独立 JSON key-value store
- 总量上限 10 MiB
- 单值上限 1 MiB
- 写入使用 mutex、临时文件和原子替换
- `storage.onChanged` 只发送实际变化的 key

### 19.2 storage.session

- 只存在于当前应用进程
- runtime 结束后仍可由 Host 保存到内存 owner
- 应用进程结束后清空
- 无痕 session 的数据在相关 session 全部关闭后清空

### 19.3 Profile 与隐私

- 插件授权默认跨普通 Profile 生效
- 无痕访问必须由插件明确声明 `incognito: split`
- 未允许无痕时，插件不在无痕 session 注入
- 无痕 content script 和 storage.session 与普通 Profile 分离
- 插件不能从 Host 获取普通与无痕的全量 Cookie

## 20. 插件中心 UI

### 20.1 顶层页面

插件中心使用现有可拖动 Browser Bottom Drawer，保持中性浏览器表面和 52dp 标题栏。

顶层结构：

```text
标题栏
	插件
	更新数量
	更多

搜索

快捷动作
	添加
	插件库

标签
	本页
	已安装
	更新
	发现

内容
	插件卡
	脚本搜索结果
	空态
	错误态
```

### 20.2 搜索

- 默认搜索本地插件、userscript、描述、ID、来源和权限
- userscript 搜索结果显示“油猴脚本 > 脚本名”，点击进入对应详情
- 本地搜索不发网络请求
- “发现”页搜索由 catalog 明确触发
- 搜索结果不改变插件排序和运行状态

### 20.3 添加菜单

- 从 URL 安装 userscript
- 导入 `.user.js`
- 新建 userscript
- 导入 `.kbx`
- 分析 WebExtension ZIP 或 CRX
- 打开 developer unpacked directory
- 使用 AI 创建

只有对应 Provider 可用时显示入口。

### 20.4 本页

按当前活动 session 和 document 投影：

- 命中的扩展
- userscript 运行状态
- extension action
- userscript 菜单命令
- 临时站点访问
- 最近错误

页面导航后旧 document 的状态立即失效。

### 20.5 已安装

分组：

- 内置
- Kiyori 扩展
- 开发者插件

卡片显示：

- 图标、名称、版本、来源
- 启用状态
- 当前页命中
- 权限或兼容警告
- 更新状态
- 最近错误

### 20.6 更新

- 可更新插件和 userscript
- 当前版本与目标版本
- 权限变化
- 文件和代码摘要
- 更新来源和 signer
- 单项确认与批量检查

批量更新只能自动处理权限未扩大且全部验证通过的项目；其余项目逐项确认。

### 20.7 发现

首期发现页只展示维护的来源和未来 Kiyori catalog：

脚本库：

- [Greasy Fork](https://greasyfork.org/)
- [ScriptCat](https://scriptcat.org/)
- [OpenUserJS](https://openuserjs.org/)
- [Userscript.Zone](https://www.userscript.zone/)
- [GitHub userscript topics](https://github.com/topics/userscript)

扩展：

- Kiyori catalog
- 本地 `.kbx`
- WebExtension 兼容分析

`GFMirror`、`GFork` 等名称目前没有可验证且稳定的官方 URL、运营主体和同步范围，不写入默认来源。

来源条目记录：

- ID
- 标题
- 类型
- URL
- 运营主体
- 信任说明
- 最近验证日期

来源快捷入口不代表 Kiyori 审核第三方代码。

## 21. 插件详情

详情页标签：

- 概览
- 权限与网站
- 设置
- 日志
- 版本
- 源码或文件

概览：

- 名称、版本、ID、来源、信任、安装时间
- 启停、检查更新、卸载
- 当前页状态和 action

权限与网站：

- 必需权限
- 可选权限
- 当前授权域名
- 临时 `activeTab`
- 无痕权限
- 最近权限调用

设置：

- 根据 `options_schema` 渲染
- 保存前验证类型、长度和枚举
- 敏感字段使用加密存储并默认遮挡

日志：

- runtime、content script、background、permission、install 和 update
- 按时间、级别、session、document 过滤
- 导出前清理 Cookie、Authorization、token 和私有 header

版本：

- 版本、hash、来源、signer、权限
- manifest 和源码差异
- 用户显式选择历史版本

## 22. userscript 管理 UI

油猴脚本插件内部页面：

```text
油猴脚本
	本页
	已安装
	更新
	日志
```

脚本详情：

- 概览
- 匹配规则
- 权限和 `@connect`
- `@require` 与 `@resource`
- 源码
- 日志
- 版本

脚本编辑器：

- 新建、复制、导入
- metadata 结构化编辑
- 代码编辑
- 查找、替换、撤销、重做、格式化
- 语法和 metadata 诊断
- 保存草稿
- 与已安装版本比较
- 安装或更新确认
- 离开前未保存提示

编辑器不直接改正式脚本文件。每次保存生成 draft revision，安装确认后进入 transaction。

## 23. WebExtension 兼容报告 UI

导入页依次显示：

- 包身份和 manifest
- 支持字段
- 不支持字段
- 支持 API
- 不支持 API
- content script world 和 frame
- background worker
- 权限
- 远程代码和动态执行
- 资源限制
- 转换建议

结果状态：

- `COMPATIBLE`
- `REQUIRES_CONVERSION`
- `INCOMPATIBLE`

只有 `COMPATIBLE` 可以继续安装。`REQUIRES_CONVERSION` 可以创建 AI 或手工 draft，但不能直接启用。

## 24. AI 与插件

### 24.1 两条独立关系

AI 与插件有两种关系，不能混为一体：

1. AI 作为作者和维护助手，帮助搜索、解释、生成、修改、测试和诊断插件
2. 插件调用 AI 能力

第一种纳入近期设计。第二种在 v1 不开放。

### 24.2 AI 能做什么

- 搜索本地插件和受信来源
- 读取用户明确选择的 manifest、源码和日志
- 创建 userscript draft
- 创建 `.kbx` draft
- 修改 draft
- 运行静态检查
- 生成权限说明
- 生成测试
- 比较版本
- 分析运行错误
- 发起安装确认请求

### 24.3 AI 不能做什么

- 直接写正式运行目录
- 直接改 registry
- 绕过权限弹窗
- 静默安装、更新、启用或卸载
- 读取其他插件私有数据
- 获取 Kiyori 模型密钥或账号凭据
- 把未授权源码、Cookie、日志或用户数据发送到外部服务
- 把静态检查通过表述为真实网页兼容通过

### 24.4 AI Draft 流程

```text
用户目标
	AI 创建或修改 draft
	本地 manifest 与语法检查
	权限和 host 范围分析
	离线单元测试
	用户选择当前页测试
	运行诊断
	源码与权限差异
	用户确认
	Installer transaction
```

### 24.5 Browser Capability API

为 AI 提供结构化工具：

- `browser_plugin_list`
- `browser_plugin_get`
- `browser_plugin_search`
- `browser_plugin_create_draft`
- `browser_plugin_update_draft`
- `browser_plugin_validate_draft`
- `browser_plugin_test_draft`
- `browser_plugin_diff`
- `browser_plugin_request_install`
- `browser_plugin_request_update`
- `browser_plugin_set_enabled`
- `browser_plugin_get_diagnostics`

读取、修改、安装、启停和卸载使用不同权限。安装、权限扩大、启用高风险插件和卸载必须由用户确认。

### 24.6 未来插件调用 AI

未来如开放 `kiyori.ai.invoke`：

- 它是高风险可选权限
- 每次调用经过 AI Broker
- UI 显示发送的数据、目标模型、预计消耗和插件身份
- 插件不能读取全局模型配置或密钥
- 默认不在 background 静默调用

该能力不进入 v1。

## 25. 诊断与可观察性

诊断事件统一结构：

```text
timestamp
	level
	category
	errorCode
	pluginId
	version
	sessionId
	documentId
	frame
	method
	origin
	message
	sanitizedDetails
```

分类：

- install
- update
- manifest
- permission
- content-script
- background
- message
- storage
- action
- compatibility

默认环形日志：

- 每插件 2000 条
- 全局 10000 条
- 单条 8 KiB
- 超限按时间删除最旧记录

日志不能保存：

- Cookie 值
- Authorization
- API key
- 完整请求体
- 用户输入框内容
- AI system prompt
- 其他插件数据

## 26. 并发与一致性

### 26.1 锁

- installer 全局 mutex：保护 registry generation
- plugin mutex：保护单插件安装、更新、启停和卸载
- storage mutex：每插件独立
- session runtime 使用主线程绑定 WebView
- background worker 使用独立串行 executor

锁顺序固定：

```text
installer global
	plugin
	permission
	storage
```

禁止反向获取。

### 26.2 Generation

- registry 每次提交增加 generation
- document 每次导航增加 navigation generation
- runtime 每次重启增加 worker generation
- UI 命令携带读取时 generation
- generation 不一致时返回 `STALE_STATE`

### 26.3 崩溃恢复

启动时：

1. 扫描 transaction journal
2. 清理未提交 staging
3. 核对 active hash 对应目录
4. 核对 manifest 与 registry
5. 把不一致插件标记 `BROKEN`
6. 不自动启用其他历史版本
7. 生成可操作诊断

## 27. 现有浏览器生命周期接入

`BrowserPluginRuntimeCoordinator` 接入：

- `createSessionOnMain`：在首次导航前 attach
- `onPageStarted`：撤销旧 document lease，生成新 generation
- `onPageCommitVisible`：同步主框架 URL
- `onPageFinished`：完成 document_end 和 idle 状态核对
- `shouldInterceptRequest`：只进入已实现且已授权的请求观察链
- `doUpdateVisitedHistory`：发送 SPA 与历史事件
- active session 变化：发送 tab activation
- `closeSession`：撤销 document、取消 host call、清理 session storage
- `onRenderProcessGone`：终止对应 content runtime 并记录 crash

插件不能替换现有 WebViewClient。协调器通过现有回调转发事件。

## 28. 请求拦截边界

v1 不提供 blocking webRequest。

未来请求处理顺序必须由单一 `BrowserRequestPipeline` 明确：

1. Kiyori 内部资源
2. 浏览器安全策略
3. 已验证的 declarative rules
4. userscript request rules
5. 网络日志和媒体观察

一个请求只能有一个最终响应 owner。多个插件声明互斥修改时，记录冲突并拒绝不确定结果。

## 29. 迁移

### 29.1 里程碑一 UI

当前旧 `USERSCRIPTS` 顶层路由已按未发布界面处理并替换为 `PLUGINS`。后续在现有插件中心上迭代，不保留
平行旧入口。

### 29.2 userscript 数据

现有 flat script 与 JSON registry 迁移到 revision 模型：

1. 读取旧 registry
2. 为每个脚本计算 hash
3. 在 `context.filesDir` 创建不可变 revision 目录
4. 写入 v2 registry staging
5. 验证源码、资源和值
6. 原子切换 schema version
7. 迁移日志记录数量和 hash

迁移完成前不删除 `Download/Kiyori/websession/userscripts` 中的旧文件。确认内部 v2 registry 完整后，
旧文件进入显式清理任务。

### 29.3 AndroidX WebKit

从 1.12.1 升级到 1.16.0 单独作为 Phase 0：

- 编译全部现有 WebView 调用
- 检查 API 签名
- 运行 userscript、Browser Home、Compose DSL WebView 定向测试
- 构建 APK
- 在设备检查实际 WebViewFeature

没有通过此门禁时不实现 `.kbx` content runtime。

## 30. 测试

### 30.1 纯 JVM

- manifest 严格解析
- ID 与版本
- match patterns
- permission diff
- optional permission
- API compatibility
- ZIP path、重复、大小写和 Unicode 冲突
- size 与 compression ratio
- transaction state
- registry generation
- source identity 和 signer
- catalog/provider 投影
- search 与 deep link
- UI routing 与 Back
- userscript metadata、`@inject-into`、grant 和 connect

### 30.2 QuickJS

- memory limit
- stack limit
- infinite loop deadline
- pending job limit
- host allowlist
- payload limit
- worker start、idle、stop、crash
- storage persistence
- runtime message

### 30.3 Android instrumentation

- execution world 隔离
- 页面不能调用 privileged bridge
- content script DOM 访问
- document_start、end、idle
- navigation 撤销旧 lease
- main frame 与 iframe
- renderer crash
- normal 与 incognito

### 30.4 真实扩展样本

- 最小 content-only `.kbx`
- content + background + storage
- action + activeTab
- userscript storage
- userscript GM XHR
- 沉浸式翻译 userscript
- 明确不兼容的 WebExtension

### 30.5 安全测试

- 网页伪造 plugin message
- 旧 document 重放
- plugin ID 冒充
- 越权 storage
- 未授权 host fetch
- ZIP traversal
- zip bomb
- signer 变化
- update permission expansion
- 恶意超大日志
- worker 无限循环

## 31. CI 与设备门禁

每个实现阶段至少执行：

- 对应 JVM tests
- `:app:compileDebugKotlin`
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`
- XML 和文档检查
- `git diff --check`
- `:app:assembleDebug --no-daemon --console=plain`
- APK 路径、大小、SHA-256、包名、版本和签名核验

高风险阶段增加：

- QuickJS native tests
- instrumentation compile
- WebViewFeature 设备报告
- 恶意包测试

真机验收：

- 插件中心窄屏与抽屉手势
- 安装、权限、启停、更新、卸载
- 普通与无痕
- 多标签与页面导航
- renderer crash
- 沉浸式翻译目标网站
- AI draft、差异和确认

构建通过不能代替真机兼容结论。

## 32. 实施阶段

### Phase 0：运行时安全和可行性

- AndroidX WebKit 1.16.0 升级与现有 WebView 回归
- isolated world 最小 proof
- userscript bridge capability token
- `@inject-into` 与 world policy
- userscript 专属单元测试
- QuickJS memory、stack 和 deadline API 设计验证

本地源码、定向单元测试、Kotlin 编译、formal readiness 和 Debug APK 核验已经完成。剩余门禁是 Android
设备上的 WebViewFeature、isolated world、恶意页面、旧 document 与 iframe origin 验收。

完成条件：恶意页面不能调用 userscript privileged host API；设备能报告并运行 isolated world。

### Phase 1：userscript 管理闭环

- 原子安装和 revision
- 源码编辑与 draft
- 权限、来源和差异
- 批量启停与更新检查
- 日志和当前页诊断
- 沉浸式翻译 userscript 验收

完成条件：油猴脚本作为内置插件具备完整的安装、编辑、更新、删除、禁用和诊断。

### Phase 2：Provider 驱动插件中心

- Catalog 和 Provider 正式接口
- 数据驱动卡片、搜索、更新和详情
- userscript 深层搜索结果
- 插件库 registry
- 添加菜单与兼容报告入口

完成条件：新增 Provider 不需要修改插件中心卡片和搜索核心逻辑。

### Phase 3：`.kbx` 包与安装器

- manifest v1
- ZIP 安全
- transaction journal
- registry 和 immutable revisions
- permission store
- 本地 unsigned 包
- developer unpacked

完成条件：可以安全安装、启停、卸载一个没有 runtime 代码的 `.kbx` 包，并完整恢复事务状态。

### Phase 4：content-only 扩展

- isolated world content script
- CSS
- match、exclude、run-at
- document lease
- runtime messaging
- storage.local
- 当前页 action

完成条件：最小 `.kbx` 在真实网页安全修改 DOM，页面不能调用其特权接口。

### Phase 5：background worker

- QuickJS 专用 worker
- runtime、storage、tabs、action
- memory、stack、deadline
- worker supervisor 和日志

完成条件：content script、background 和 action 形成真实闭环。

### Phase 6：签名、catalog 和更新

- Ed25519
- Kiyori catalog
- update metadata
- permission diff
- signer continuity
- 历史版本

完成条件：签名插件可从 catalog 安装和更新，权限扩大必须再次确认。

### Phase 7：AI 创作

- AI draft tools
- 模板
- 静态检查
- 测试
- 差异
- 安装确认

完成条件：AI 能创建可验证 draft，但不能绕过 Installer 和用户确认。

### Phase 8：WebExtension 兼容

- ZIP 与 CRX 分析
- API 扫描
- manifest 转换
- 目标扩展测试
- 已验证兼容清单

完成条件：只对真实通过的扩展显示兼容，不发布笼统的 Chrome / Edge 兼容声明。

## 33. 实现前门禁

进入业务代码前必须满足：

- 本文与实施 TODO 一致
- 唯一 Browser Runtime 不变量未改变
- UI 按当前未发布方案继续迭代
- `.kbx`、MV3 子集和 AndroidX WebKit 1.16.0 已定案
- userscript 安全整改排在功能扩展之前
- ToolPkg 与 Browser Plugin 协议保持分离
- 不支持 API、字段、设备能力和包必须明确拒绝
- 每阶段都有单元、构建和设备验收条件
- 未获得提交、推送、发布、部署或设备安装授权

## 34. 官方参考

- [AndroidX WebKit release notes](https://developer.android.com/jetpack/androidx/releases/webkit)
- [WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
- [JavaScriptExecutionWorld](https://developer.android.com/reference/androidx/webkit/JavaScriptExecutionWorld)
- [WebView native bridge security](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)
- [Chrome extensions architecture](https://developer.chrome.com/docs/extensions/develop/concepts/architecture)
- [Chrome userScripts API](https://developer.chrome.com/docs/extensions/reference/api/userScripts)
- [Chrome content scripts](https://developer.chrome.com/docs/extensions/develop/concepts/content-scripts)
- [Chrome permissions](https://developer.chrome.com/docs/extensions/develop/concepts/declare-permissions)
- [Chrome service worker lifecycle](https://developer.chrome.com/docs/extensions/develop/concepts/service-workers/lifecycle)
- [Microsoft Edge extension API support](https://learn.microsoft.com/en-us/microsoft-edge/extensions/developer-guide/api-support)
- [GeckoView WebExtensions](https://mozilla.github.io/geckoview/consumer/docs/web-extensions)
- [Tampermonkey documentation](https://www.tampermonkey.net/documentation.php)
- [Immersive Translate](https://github.com/immersive-translate/immersive-translate)
