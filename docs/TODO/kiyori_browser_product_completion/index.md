---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: active
baseline: 6704fa2539e5cbcacacf91a9b0550b935fbe7718
legacy_design_reference: 24a2dfa91f0a4166dc58e5c4732d11861173f766
player_reference: 32f5f16988c1b2d5979eef692695bdef7232b7eb
hikerview_reference: 5de8809049e4710471f9f42642e54550ecf5dbe3
---

# 浏览器产品能力连续完善

## 任务定位

本计划承接已经完成的 [浏览器首页与 WebSession 共用计划](../kiyori_browser_home_websession/index.md)、[浏览器沉浸式 UI 重构](../kiyori_browser_ui_refactor/index.md) 和 [浏览器顶栏、全屏搜索、浏览器菜单与窗口重构](../kiyori_browser_topbar_search_toolbox_windows/index.md)。前三轮已经建立唯一 Browser Runtime、沉浸式 Browser Home、搜索与菜单框架；本轮继续解决悬浮窗口系统行为、人工窗口的 AI 接管、软件首页、真无痕、窗口缩略图、设置、下载、负一屏、菜单真实能力和播放器。

Kiyori 从未发布。本轮被替代且无继续用途的旧 UI、占位状态和伪能力直接删除，不保留并行界面、兼容开关或回退路径。`com.ai.assistance.operit`、`operit://`、ToolPkg、MCP、Intent action、数据库和持久化格式等兼容标识继续遵守 `CONTEXT.md`。

## 用户目标

- 悬浮球展开浏览器时，浏览器背景延伸到状态栏区域，系统 Back 与左上角返回都能收起网页为悬浮球
- 人工创建和导航的浏览器窗口始终能被 Operit AI 发现、读取和操作
- 软件首页、全屏网页搜索、普通/无痕窗口、设置和下载形成现代、清晰、适配手机与平板的产品界面
- 浏览器窗口总览显示真实网页缩略图，普通与无痕窗口拥有真实隔离语义
- 负一屏接入书签、历史和下载；浏览器四行菜单逐步移除说明页并接入真实能力
- 最后建立 Kiyori 播放器、媒体 Intent、浏览器资源嗅探和无刷新悬浮播放

## 依赖顺序

```text
kiyori_browser_product_completion/
	index.md
	1_overlay_back_insets_and_ai_adoption.md
	2_software_home_and_fullscreen_search.md
	3_incognito_profiles_and_window_thumbnails.md
	4_settings_home_and_browser_settings.md
	5_download_center_and_settings.md
	6_minus_one_browser_library.md
	7_browser_menu_capabilities.md
	8_media_intent_and_player_foundation.md
	9_browser_sniffer_and_floating_playback.md
	10_validation_build_git_and_device_acceptance.md
```

实施顺序不可交换：播放器和嗅探依赖稳定的 session profile、窗口生命周期、设置 owner、下载 owner 和菜单路由；无痕必须先于缩略图和 AI tab 输出完成，以免后续再次更改窗口模型。

## 里程碑门禁

每一个编号都是独立里程碑，必须严格串行：

1. 只实施该编号的单一主题，不夹带后续功能
2. 更新对应 TODO、`CONTEXT.md` 和用户可见文档
3. 先运行最窄的定向测试，再运行正式开发门禁与 `git diff --check`
4. 成功构建 Debug APK，核对时间、大小、SHA-256、包名、版本和签名
5. 审计 staged allowlist，提交到唯一 `main`
6. 推送 `origin/main`，使用远端 ref 确认 SHA 与本地 HEAD 一致
7. 记录任务日记后才进入下一编号

任何构建仍在运行时不得启动第二个 Gradle 构建。Release、部署、APK 安装、ADB、MuMu 和设备自动化不在本计划授权范围内。

## 全局状态所有权

| 状态 | 唯一 owner | 人工 UI | AI |
| --- | --- | --- | --- |
| 标签、活动标签和 WebView | `StandardBrowserSessionTools` | 直接操作 | `browser_*` 操作同一实例 |
| 窗口 Profile | `WebSession.profile` | 搜索页和窗口页选择 | `browser_tabs` 读取并可显式创建 |
| 新窗口默认 Profile | Browser Runtime | 无痕按钮改变 | `browser_tabs list` 可观察 |
| 历史、书签和搜索记录 | `WebSessionHistoryStore` | 浏览器、负一屏和全屏页复用 | 浏览器工具不复制存储 |
| 下载任务 | `BrowserDownloadManager` | 浏览器抽屉和全屏下载中心复用 | 下载事件进入浏览器结果 |
| 浏览器设置 | 新的 browser settings store | 设置页和浏览器菜单复用 | 只通过明确能力读取或修改 |
| 播放会话 | 后续唯一 player session | 全屏、悬浮和浏览器共用 | 后续 capability adapter 操作 |

## 全局交互合同

- Browser Home、overlay 和 AI 只转挂同一个活动 WebView，不在展示切换时调用 `loadUrl`、`reload` 或重建
- 系统 Back 先关闭当前最上层浏览器状态，再按 presentation owner 解释退出；overlay 展开态永远先收缩为悬浮球
- 软件首页全屏搜索创建新窗口；浏览器顶栏搜索继续导航当前窗口，两者不混用
- 普通窗口和无痕窗口不能互相转换；关闭窗口后其 Profile 语义不改变
- AI 可以操作无痕窗口，但无痕仅隔离 WebView 网站数据，不隔离本应用内已经获得授权的 AI；所有相关 UI 必须明确说明
- 不支持 AndroidX WebKit Multi-Profile 的设备明确禁用无痕，不使用共享 Cookie、手动清 Cookie或其他伪无痕实现
- 真机视觉、状态栏、系统 Back、WebView 多 Profile、输入法、窗口缩略图和播放器转场在用户验收前保持 `verification_pending`

## 当前优先级

1. [DONE] P0：悬浮浏览器系统 Back、状态栏背景和人工窗口 AI 接管；本地实现、定向测试与 Debug APK 已完成，真机验收待用户执行
2. [DONE] P0：软件首页与全屏搜索已接入共享 Browser Runtime，本地测试与 Debug APK 已验证，真机视觉和输入法待验收；真无痕窗口模型属于下一独立里程碑
3. P1：窗口缩略图、设置和下载中心
4. P1：负一屏与四行菜单真实能力
5. P2：媒体 Intent、播放器、嗅探和悬浮播放

## 完成定义

- 十个里程碑均有源码、文档、自动检查、Debug APK、提交、推送和远端 SHA 证据
- 所有已展示入口都有真实实现；仍受硬依赖限制的入口不伪造成功状态
- 人工和 AI 在普通与无痕窗口中共享唯一 Browser Runtime，并能从工具结果确认窗口 Profile
- 视频 `ACTION_VIEW` 不再进入 AI 附件链路
- 浏览器到播放器的全屏、悬浮、返回转场不刷新、重载或修改当前网页
- 用户醒来后只需安装最后一个 Debug APK，按真机清单完成现场验收
