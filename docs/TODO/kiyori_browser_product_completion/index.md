---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: active
baseline: 166c941344dbcfb8317e12e6f45ff712ca80f125
legacy_design_reference: 24a2dfa91f0a4166dc58e5c4732d11861173f766
player_reference: 32f5f16988c1b2d5979eef692695bdef7232b7eb
hikerview_reference: 5de8809049e4710471f9f42642e54550ecf5dbe3
---

# 浏览器产品能力连续完善

## 任务定位

本计划承接已经完成的 [浏览器首页与 WebSession 共用计划](../kiyori_browser_home_websession/index.md)、[浏览器沉浸式 UI 重构](../kiyori_browser_ui_refactor/index.md) 和 [浏览器顶栏、全屏搜索、浏览器菜单与窗口重构](../kiyori_browser_topbar_search_toolbox_windows/index.md)。前三轮已经建立唯一 Browser Runtime、沉浸式 Browser Home、搜索与菜单框架；本轮继续解决悬浮窗口系统行为、人工窗口的 AI 接管、软件首页、真无痕、窗口缩略图、设置、下载、负一屏、菜单真实能力和播放器。

Kiyori 从未发布。本轮被替代且无继续用途的旧 UI、占位状态和伪能力直接删除，不保留并行界面、兼容开关或回退路径。`com.ai.assistance.operit`、`operit://`、ToolPkg、MCP、Intent action、数据库和持久化格式等兼容标识继续遵守 `CONTEXT.md`。

2026-07-26 用户确认以 `GPL-3.0-or-later` 作为完整 mpv 播放器移植的分发边界。该决定只授权本地开发中的许可证与依赖设计，不授权公开发布；播放器依赖进入仓库时必须同时完成完整 GPL 正文、第三方 NOTICE、来源、版本、哈希、ABI、对应源码和动态/静态链接义务审计。旧 mpv AAR 与当前 FFmpegKit 的七个同名 `libav*.so` 不允许通过 packaging 选取规则掩盖，必须建立无重复 native 库的统一栈。

当前 Goal 只复刻 `kiyori-android@24a2dfa9` 已有的 UI 和真实运行时能力。旧项目没有消费者的入口保留空页面或不可交互状态，不在 Kiyori 另行发明实现；旧项目已有消费者的状态接入当前唯一 Browser Runtime、Download Manager 或后续唯一 PlayerSession。

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
	11_player_runtime_ui_and_browser_completion.md
```

实施顺序不可交换：播放器和嗅探依赖稳定的 session profile、窗口生命周期、设置 owner、下载 owner 和菜单路由；无痕必须先于缩略图和 AI tab 输出完成，以免后续再次更改窗口模型。

## 里程碑门禁

每一个编号都是独立里程碑，必须严格串行：

1. 只实施该编号的单一主题，不夹带后续功能
2. 更新对应 TODO、`CONTEXT.md` 和用户可见文档
3. 先运行最窄的定向测试，再运行正式开发门禁与 `git diff --check`
4. 成功构建 Debug APK，核对时间、大小、SHA-256、包名、版本和签名
5. 记录任务日记和 APK 证据后才进入下一编号

提交和推送不是里程碑默认动作。只有用户在当前任务中另行明确授权时，才审计 staged allowlist、提交到唯一 `main` 并核对远端 SHA；当前 Goal 未授权提交或推送。

任何构建仍在运行时不得启动第二个 Gradle 构建。Release、部署、APK 安装、ADB、MuMu 和设备自动化不在本计划授权范围内。

## 全局状态所有权

| 状态 | 唯一 owner | 人工 UI | AI |
| --- | --- | --- | --- |
| 标签、活动标签和 WebView | `StandardBrowserSessionTools` | 直接操作 | `browser_*` 操作同一实例 |
| 窗口 Profile | `WebSession.profile` | 搜索页和窗口页选择 | `browser_tabs` 读取并可显式创建 |
| 新窗口默认 Profile | Browser Runtime | 无痕按钮改变 | `browser_tabs list` 可观察 |
| 历史、书签和搜索记录 | `WebSessionHistoryStore` | 浏览器、负一屏和全屏页复用 | 浏览器工具不复制存储 |
| 下载任务 | `BrowserDownloadManager` | 浏览器抽屉和全屏下载中心复用 | 下载事件进入浏览器结果 |
| 浏览器运行偏好 | 搜索与历史由 `WebSessionHistoryStore` 持有；Profile 由 Browser Runtime 持有；UA 与浏览器通用设置由 `WebSessionBrowserSettingsStore` 持有 | 浏览器主流程直接使用唯一 owner | 只通过明确能力读取或修改 |
| 播放会话 | 唯一 `PlayerSession` / mpv core | 全屏、悬浮和浏览器共用同一媒体与 Surface owner 状态 | 后续 capability adapter 只能调用该 owner |

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
3. [DONE] P1：真无痕 Profile、窗口逻辑与网页缩略图；本地实现、定向测试与 Debug APK 已完成，真机 WebView Multi-Profile、缩略图和交互待用户验收
4. [DONE] P1：已按旧版 `5/5/2/5/6` 分组重新复刻网页浏览器设置；自定义主页、网页外部应用、网页定位和悬浮嗅探偏好接入唯一 owner，其他旧版未实现项为空占位；根页面已使用无描边分组卡和连续折叠吸顶标题，本地测试与门禁通过，最新 Debug APK 与真机视觉状态见第十阶段
5. [IN PROGRESS] P1：下载中心与文件下载器设置；完整 `5/3/3/1` 文件下载器设置页与十二项旧版真实 consumer 已接通，并与网页浏览器设置共用无描边卡片和连续折叠吸顶标题；仍待下载中心双筛选/批量操作复刻及真机综合验收
6. [IN PROGRESS] P1：负一屏与四行菜单真实能力；书签/下载共享抽屉和 UA 标识直达弹窗、全局模式、域名规则已完成，其他菜单能力按第七阶段继续串行推进
7. [DONE] P2：阶段 8 媒体 Intent、唯一 PlayerSession、全屏播放器、设置页与统一 native 栈，以及阶段 9 candidate、浏览器嗅探、现有下载 owner、同会话悬浮/全屏交接均已完成本地实现、定向测试和最终 Debug APK 审计；真机解码、手势、转场、性能与站点兼容待用户验收
8. [DONE] P0：阶段 11 已完成播放器运行时显式 AAR/DEX 门禁、`MPVLib`/JNI 可见错误边界、固定参考控件布局、十项真实设置、浏览器候选数字圆圈、自动小窗与网络日志播放；本地测试、formal readiness、最终 Debug APK/native/许可证审计通过，真机验收保持 `verification_pending`

## 完成定义

- 十一个里程碑均有源码、文档、自动检查和 Debug APK 证据；提交、推送和远端 SHA 仅在另行授权时属于完成证据
- 所有已展示入口都有真实实现；仍受硬依赖限制的入口不伪造成功状态
- 人工和 AI 在普通与无痕窗口中共享唯一 Browser Runtime，并能从工具结果确认窗口 Profile
- 视频 `ACTION_VIEW` 不再进入 AI 附件链路
- 浏览器到播放器的全屏、悬浮、返回转场不刷新、重载或修改当前网页
- 用户醒来后只需安装最后一个 Debug APK，按真机清单完成现场验收
