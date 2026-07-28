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

- Browser Home、1×1 background anchor 和 AI 只转挂同一个活动 WebView，不在展示切换时调用 `loadUrl`、`reload` 或重建
- 完整浏览器 UI 只存在于 App Shell Browser Home；系统层最小 indicator 通过显式 action 打开 Browser Home，后台 anchor 不处理浏览器 Back、IME、cutout 或完整 chrome
- 软件首页全屏搜索创建新窗口；浏览器顶栏搜索继续导航当前窗口，两者不混用
- 普通窗口和无痕窗口不能互相转换；关闭窗口后其 Profile 语义不改变
- AI 可以操作无痕窗口，但无痕仅隔离 WebView 网站数据，不隔离本应用内已经获得授权的 AI；所有相关 UI 必须明确说明
- 不支持 AndroidX WebKit Multi-Profile 的设备明确禁用无痕，不使用共享 Cookie、手动清 Cookie或其他伪无痕实现
- 真机视觉、状态栏、系统 Back、WebView 多 Profile、输入法、窗口缩略图和播放器转场在用户验收前保持 `verification_pending`

## 当前优先级

1. [DONE] P0：悬浮浏览器系统 Back、状态栏背景和人工窗口 AI 接管；本地实现、定向测试与 Debug APK 已完成，真机验收待用户执行
2. [DONE] P0：软件首页与全屏搜索已接入共享 Browser Runtime；2026-07-28 完成搜索引擎图标、覆盖式淡蓝引擎面板、标题/网址双行操作区、自适应历史标签和显式删除提交，并按浏览器菜单基准压缩尺寸、补齐面板周围收起与当前网页区返回。历史清空现使用底部确认框且确认后立即执行，标签叉号仍由“完成”提交；本地定向测试、formal readiness、Kotlin 编译与 Debug APK 已验证，真机视觉、输入法和无痕 Profile 交互待验收
3. [DONE] P1：真无痕 Profile、窗口逻辑与网页缩略图；本地实现、定向测试与 Debug APK 已完成，真机 WebView Multi-Profile、缩略图和交互待用户验收
4. [DONE] P1：已按旧版 `5/5/2/5/6` 分组重新复刻网页浏览器设置；自定义主页、网页外部应用、网页定位和悬浮嗅探偏好接入唯一 owner，其他旧版未实现项为空占位；根页面已使用无描边分组卡和连续折叠吸顶标题，本地测试与门禁通过，最新 Debug APK 与真机视觉状态见第十阶段
5. [IN PROGRESS] P1：下载中心与文件下载器设置；完整 `5/3/3/1` 文件下载器设置页与十二项旧版真实 consumer 已接通，并与网页浏览器设置共用无描边卡片和连续折叠吸顶标题；仍待下载中心双筛选/批量操作复刻及真机综合验收
6. [IN PROGRESS] P1：负一屏与四行菜单真实能力；书签/下载共享抽屉和 UA 标识直达弹窗、全局模式、域名规则已完成，其他菜单能力按第七阶段继续串行推进
7. [DONE] P2：阶段 8 媒体 Intent、唯一 PlayerSession、全屏播放器、设置页与统一 native 栈，以及阶段 9 candidate、浏览器嗅探、现有下载 owner、同会话悬浮/全屏交接均已完成本地实现、定向测试和最终 Debug APK 审计；真机解码、手势、转场、性能与站点兼容待用户验收
8. [DONE] P0：阶段 11 的运行时、设置、浏览器候选与 native 门禁已完成；全屏播放器按用户 `2800x1260` 横屏和 `1260x2800` 竖屏截图继续收口，竖屏顶部沿用 legacy 固定标题/权重单元，底部九键等宽排列。顶部四个功能入口统一为字幕、弹幕、音轨、画面模式描边图标，投屏移入更多菜单并删除样式覆盖。浏览器悬浮播放器已按 legacy 改为内容区全宽、固定 `16:9`、零边距、只能在顶栏与底栏之间纵向平移，并恢复顶部、右侧、底部和锁定控制层。`2026-07-28 19:18:21 +08:00` 的必现闪退纠正了播放器启动契约：新媒体先创建并初始化唯一 mpv，首个有效 Surface 在 `mpv_initialize` 完成后 attach，完成当前 lease 的 native attach 后再执行 `loadfile`。播放器日志入口可复制、清空和关闭；自动检查和 Debug APK 证据以阶段 11 最新记录为准，真机像素、崩溃与交互验收保持 `verification_pending`

## 2026-07-28 方案 A 正式实现计划

最新 vivo Android 16 tombstone 的主链位于 RenderThread、Mali、Android System WebView Chromium `postDrawVk`
和 HWUI Vulkan。该证据没有出现 libmpv、Codec2 或 Kiyori 播放器业务帧，因此本轮以结构方式移除完整
系统 overlay 浏览器窗口，并把 Surface 交接改成可验证的唯一租约状态机；真机是否不再崩溃仍由后续同页面
复测决定。

### 里程碑 1：App Shell 浏览器 presentation

- 新增 `MainActivity` 显式 Browser Home action，冷启动和 `singleTask onNewIntent()` 使用同一一次性请求
- indicator 点击打开现有 App Shell Browser Home，不再改变 overlay 展开状态
- `WebSessionBrowserHost` 只保留共享 UI 状态、App presentation、1×1 background anchor 和 indicator
- 删除系统 overlay 中的 `BrowserContent`、悬浮播放器、expanded Back、cutout、IME 和全屏布局
- 保持 `StandardBrowserSessionTools.sessions`、`sessionOrder`、`activeSessionId`、`browserHost` 和实际 WebView
  为人工与 AI 的唯一事实源
- 通过浏览器定向 JVM 测试、Kotlin 编译、formal readiness、`git diff --check` 和独立 Debug APK 构建

本地状态：[DONE]。浏览器定向 JVM `46/46`、Kotlin 编译、formal readiness、`git diff --check`、
Debug 构建和 `verifyDebugPlayerRuntimePackaging` 均通过。里程碑 APK 时间为
`2026-07-28 17:27:04 +08:00`，大小 `468746121` 字节，SHA-256
`84D7067F361FB9A582DE649DC787B50849AC5D122B6813D2264CF6704F3C7739`；包名/版本、Android Debug v2
与 16 KB ZIP 对齐通过。真机状态保持 `verification_pending`。

### 里程碑 2：显式 Surface lease

- 增加 floating/fullscreen role、owner token、单调 generation、current/pending lease、native detach 状态和 transfer phase
- floating -> fullscreen 在旧 floating `surfaceDestroyed` 和 native detach 完成后才发出一次 Activity launch
- fullscreen -> floating 先发出一次 Activity finish，旧 fullscreen Surface 释放后才允许 Browser Home 重新组合并 attach
- config change、提前到达的新 Surface、过期 token/generation、close 和异常 Activity 退出均由同一 reducer 判定
- `MpvPlayerEngine.attachSurface()` 只连接空闲 native surface；detach 仅由 `PlayerSession` 状态机调用
- 通过纯逻辑 lease 测试、播放器定向测试、Kotlin 编译、formal readiness、`git diff --check` 和独立 Debug APK 构建

本地状态：[DONE]。`PlayerPolicyTest 12/12` 与 `PlayerSurfaceLeasePolicyTest 8/8` 共 `20/20` 通过；
Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和
`verifyDebugPlayerRuntimePackaging` 均通过。里程碑 APK 时间为
`2026-07-28 17:57:39 +08:00`，大小 `468746121` 字节，SHA-256
`08A443EAB2ED4FE1F2ACE99E5107AF791EB064EF0C82D53EAA2C413855764BAE`；包名/版本、Android Debug v2
与 16 KB ZIP 对齐通过。真机状态保持 `verification_pending`。

### 里程碑 3：清理、文档与终局门禁

- 删除 expanded overlay 旧符号、测试、文档契约和未使用命名
- 更新 `CONTEXT.md`、`README.md`、正式架构文档、阶段 1、阶段 11 和本 index
- 静态确认无完整浏览器 overlay、无第二 WebView/session/player owner、无 `TextureView`、无软件层和无转挂媒体重载
- 重新执行定向测试、Kotlin 编译、formal readiness、`git diff --check`、最终 Debug 构建和 APK 元数据/签名/16 KB 对齐审计

本地状态：[DONE]。浏览器与播放器综合 JVM `66/66`、Kotlin 编译、formal readiness、
静态反向检查、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 均通过。
最终 APK 时间为 `2026-07-28 18:15:33 +08:00`，大小 `468746121` 字节，SHA-256
`689A09C1DF4E23608A4E9E07A7B0E95645F9D5914E3CFB29BA0DFD1A34C2DBA0`；`com.kiyori / 45 / 0.1.0 /
min 26 / target 34`、Android Debug v2 与 16 KB ZIP 对齐通过。未执行安装或设备操作，目标 vivo
Android 16 的同页崩溃和交接现场验收仍为 `verification_pending`。

三个里程碑均不授权提交、推送、安装、ADB、MuMu 或 Release。最终状态保持 `verification_pending`，
直到用户在 vivo Android 16 上完成同一网页、同一视频的人工与 AI 共用浏览器以及悬浮/全屏转场复测。

## 2026-07-28 首轮真机 presentation 修正

首轮设备日志暴露 Browser Home 显式 release 与 Compose dispose 重复执行同一 presentation 释放：
第一次已把 WebView 转到 1×1 background anchor，第二次却再次要求它处于 detached 状态，因而触发
`Active WebView must be detached before browser presentation transfer`。

修正后的 presentation 使用一次性 lease；host 将重复当前 owner 请求解析为 no-op，并把
`APP_SHELL <-> BACKGROUND_ANCHOR` 实现为 detach、确认 null parent、移除旧 ViewRoot、等待渲染帧、
连接同一 WebView 的显式事务。Browser Home 活跃时不再保留空 anchor，窗口总览入口也不再提前制造
后台挂载。现有 WebSession、活动 WebView、网页历史和 AI `browser_*` 协议保持不变。

本地状态：[DONE]。相关 JVM 测试 `51/51`、Kotlin 编译、formal readiness、静态反向检查、
`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 均通过。最终 APK 时间
`2026-07-28 19:01:32 +08:00`，大小 `455956807` 字节，SHA-256
`8DD13C5D3AA81AEAF3102A341FAF485233FF50F1D14BC690C5A608FA3C96ABA6`；包名/版本、
Android Debug v2 与 16 KB ZIP 对齐通过。

同期 native `fdsan` 栈没有 Kiyori 或 mpv 业务帧，本地证据不能证明它已经根治。设备状态继续为
`verification_pending`，需要在原 vivo Android 16、同一网页和视频上复测浏览器退出、AI 往返、
indicator 恢复及 floating/fullscreen 转场。

## 2026-07-28 播放入口必现闪退纠正

`2026-07-28 19:18:21 +08:00` 的新 tombstone 证明崩溃不依赖 Browser presentation 退出。
源码复核确认所有播放入口此前共用一条错误启动序列：在 `MPVLib.init()` 前 attach Surface 并启用
`force-window`。该顺序与实际打包 mpvlibAndroid `168e0a5e` 及 legacy Kiyori 均相反。

当前实现先创建并初始化唯一 mpv core；有效 Surface 到达后由现有 lease 执行 native attach，随后才
消费 pending `loadfile`。Surface role、token、generation、identity、双向 transfer phase 和一次性
Activity request 均保留，不重建 player、WebView 或媒体 request。

本地状态：[DONE]。播放器资源门禁 `6/6`、播放器 JVM `20/20`、Kotlin 编译、formal readiness、
静态反向检查、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 均通过。
APK 时间 `2026-07-28 19:39:17 +08:00`，大小 `455956807` 字节，SHA-256
`91634B63C7D5D6EDC5FF20FBBE76663F7850D8A446D52874B556353E1174FB9E`；包名/版本、
Android Debug v2 和 16 KB ZIP 对齐通过。原 vivo Android 16 复测前仍为
`verification_pending`。

## 完成定义

- 十一个里程碑均有源码、文档、自动检查和 Debug APK 证据；提交、推送和远端 SHA 仅在另行授权时属于完成证据
- 所有已展示入口都有真实实现；仍受硬依赖限制的入口不伪造成功状态
- 人工和 AI 在普通与无痕窗口中共享唯一 Browser Runtime，并能从工具结果确认窗口 Profile
- 视频 `ACTION_VIEW` 不再进入 AI 附件链路
- 浏览器到播放器的全屏、悬浮、返回转场不刷新、重载或修改当前网页
- 用户醒来后只需安装最后一个 Debug APK，按真机清单完成现场验收
