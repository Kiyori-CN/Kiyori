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

## 2026-09-04 GoTab 原生浏览器主页迁移

状态：`官网主页验证阶段已接入 / 原生主页首个 Dashboard 已接入 / 设备验收待执行`。

本任务按“最终选择 Kiyori 原生浏览器主页、立即验证 GoTab 官网、明确排除直接运行 CRX”执行。第一切片将
新安装浏览器主页设为 `https://web.gotab.cn/`，用于验证 GoTab 网页版在 Kiyori 共享 WebSession/WebView
中的首帧、触摸、登录、存储、导航和网络行为；用户仍可在浏览器设置中改回其他 HTTP/HTTPS 地址或
`about:blank`。这不是原生主页完成，也不表示 GoTab 的 Chrome 扩展 API 已在 Android WebView 中兼容。

明确边界：

- GoTab CRX 不进入 Kiyori 扩展中心，不直接解压执行，不创建第二个 Browser Runtime、WebView 或标签页注册表。
- 官网 URL 只作为当前验证入口；远程站点、账号、服务端 API、网页版本功能和可用性不成为 Kiyori 的持久状态 owner。
- 原生主页阶段复用 `StandardBrowserSessionTools`、`WebSessionHistoryStore`、现有地址解析、搜索、标签和窗口
  导航 owner；GoTab 的卡片、分类、背景和小组件只作为功能/视觉参考，按 Kiyori 本地数据与权限合同重新实现。

阶段门槛：

1. [DONE] 将新安装主页切换到 `https://web.gotab.cn/`，同步设置测试、README、CONTEXT 和本 TODO。
2. [PENDING] 在目标 Android WebView 验证官网页面首帧、登录态/localStorage、触摸拖拽、文件/壁纸入口、
   Kiyori Back/主页/新标签行为，以及无网络和代理错误表现。
3. [DONE] 已形成 `BrowserHomeDashboard` 的页面 owner、共享数据投影和搜索/快捷访问/最近访问交互，并完成
   三模式主页设置 UI 与模式摘要；原生主页自己的本地导入/导出数据仍不在本切片范围内。
4. [IN PROGRESS] 原生 Dashboard 已接入唯一 Browser Runtime，复用搜索、书签、历史、标签和窗口回调；三模式
   数据迁移、主页/新标签/Back 入口和定向 JVM 已完成，官网、触摸、真实 WebView 和最终原生默认初值仍待验收，
   完成前状态保持 `verification_pending`。

本轮详细计划与风险见 [`20_gotab_native_browser_home_migration.md`](20_gotab_native_browser_home_migration.md)。

## 2026-08-28 最新日志复核：runtime readiness 与诊断噪声

状态：`LOCAL IMPLEMENTATION VERIFIED / TARGET DEVICE VERIFICATION PENDING`。

最新 Browser Diagnostics 中 7 个播放器 handoff 均使用唯一 candidate/request，没有新的重复交接、
渲染器退出或应用 FATAL；`go.itab.link` 的两条 `SSL_ERROR primaryError=3` 仍需作为独立 TLS 证据保留。
代理日志确认 generation 1/2/3 均自然退出并自动恢复，退出来源尚未定位。当前实现让自然退出立即失效
旧 WebView proxy readiness，复用相同端点与旁路策略时跳过重复 `ProxyController` 安装；诊断 console
事件允许跨 session 交错聚合并保留有界 `repeatCount`。完整方案、风险、非目标和验收矩阵见
[`19_latest_log_runtime_readiness_and_diagnostic_noise.md`](19_latest_log_runtime_readiness_and_diagnostic_noise.md)。
本地自动证据完成后，vivo Android 16 的现场复测仍保持 `verification_pending`。

## 2026-08-28 Android 16 播放器关闭与重复交接修复

播放器关闭的主线程网络回收崩溃、重复媒体 handoff 和 bridge TLS 错误投影的详细根因、合同与验证矩阵见
[`18_android16_player_close_and_handoff_repair.md`](18_android16_player_close_and_handoff_repair.md)。
本轮实现与自动化验证完成，真实设备关闭、重播、HTTPS/Range/HLS 和旋转验收仍保持
`verification_pending`。

## 2026-08-27 日志驱动的浏览器、播放器与代理链路修复

本轮根据 vivo Android 16 导出的 Browser、Player 和主进程代理日志，继续处理真实媒体交接失败。
已证实主进程 Mihomo 使用 `mixedPort=40877` 后，独立 `:player` 又创建了
`mixedPort=42071` 的第二个进程内 runtime；目标节点同时对媒体域名返回 `503`，而当前
`PlayerMediaStreamBridge` 把上游失败折叠成 mpv 的 EOF 与 `loading failed`。完整根因、唯一主进程
代理 owner、bridge 生命周期、IPC、错误投影、影响文件、风险和验收矩阵见
[`17_log_driven_browser_player_proxy_repair.md`](17_log_driven_browser_player_proxy_repair.md)。主进程唯一代理 owner、bridge 错误语义、IPC transport 边界、自动验证、Debug APK、提交与推送均已完成；目标设备真实网络复测仍待执行。

## 2026-08-27 浏览器运行时诊断与菜单优化

本轮专项方案、影响范围、风险、里程碑和验收矩阵见
[`16_browser_runtime_diagnostics_and_menu_optimization.md`](16_browser_runtime_diagnostics_and_menu_optimization.md)。
目标是保持唯一 Android System WebView/Chromium provider 边界，移除未实现的阅读模式菜单入口，
新增浏览器专用结构化诊断抽屉，并将 AndroidX WebKit 从 `1.16.0` 升级到官方稳定 `1.17.0`。
本轮实现、自动验证、正式门禁与 Debug APK 静态审计已完成；真实 provider、网页性能、菜单视觉、
系统 Back 和无痕 Profile 仍按项目规则保持 `verification_pending`。

## 任务定位

本计划承接已经完成的 [浏览器首页与 WebSession 共用计划](../kiyori_browser_home_websession/index.md)、[浏览器沉浸式 UI 重构](../kiyori_browser_ui_refactor/index.md) 和 [浏览器顶栏、全屏搜索、浏览器菜单与窗口重构](../kiyori_browser_topbar_search_toolbox_windows/index.md)。前三轮已经建立唯一 Browser Runtime、沉浸式 Browser Home、搜索与菜单框架；本轮继续解决悬浮窗口系统行为、人工窗口的 AI 接管、软件首页、真无痕、窗口缩略图、设置、下载、负一屏、菜单真实能力和播放器。

Kiyori 从未发布。本轮被替代且无继续用途的旧 UI、占位状态和伪能力直接删除，不保留并行界面、兼容开关或回退路径。`com.ai.assistance.operit`、`operit://`、ToolPkg、MCP、Intent action、数据库和持久化格式等兼容标识继续遵守 `CONTEXT.md`。

> 架构归属说明：本计划的 Browser/Player 行为合同和历史验收证据继续有效；未来源码所有权、
> 目标包路径和 Operit 兼容岛由
> [Kiyori 项目架构与 Operit 命名重构方案 v3](../kiyori_architecture_refactor/index.md)
> 接管。本文及分项文档中的历史路径不追溯改写，只有真实迁移完成的里程碑才更新当前入口。

2026-07-26 用户确认以 `GPL-3.0-or-later` 作为完整 mpv 播放器移植的分发边界。该决定只授权本地开发中的许可证与依赖设计，不授权公开发布；播放器依赖进入仓库时必须同时完成完整 GPL 正文、第三方 NOTICE、来源、版本、哈希、ABI、对应源码和动态/静态链接义务审计。旧 mpv AAR 与当前 FFmpegKit 的七个同名 `libav*.so` 不允许通过 packaging 选取规则掩盖，必须建立无重复 native 库的统一栈。

当前 Goal 只复刻 `kiyori-android@24a2dfa9` 已有的 UI 和真实运行时能力。旧项目没有消费者的入口保留空页面或不可交互状态，不在 Kiyori 另行发明实现；旧项目已有消费者的状态接入当前唯一 Browser Runtime、Download Manager 或后续唯一 PlayerSession。

### 2026-08-20 页面源码工作台交互与长源码性能优化

状态：`LOCAL DONE / TARGET DEVICE VERIFICATION PENDING`。本增量继续复用唯一
`WebSessionPageSourceEditor`、`NativeCodeEditor`、`CanvasCodeEditorView` 和 Browser Runtime。
Kiyori 尚未发布，因此默认模式与工作台按钮属于现有方案迭代；不保留旧默认模式的并行实现。

冻结的实现合同：

- “查看源码”进入时默认 `horizontal browse`，而不是 `soft wrap`
- 两种模式的初始视口均为源码左上角；模式切换也回到 `(0,0)`，不会自动跟随旧光标
- 查找栏保留循环查找，新增 `previous`；查找状态同时记录匹配起点和终点，替换后使用实际
  替换长度推进下一次查找
- 横向长行的绘制从可见 cell 附近开始，使用行内 checkpoint 映射源码 offset，避免每一帧从行首
  扫描到横向视口
- 双指缩放不在每个 scale event 中清空并重建自动换行布局；软换行布局在缩放结束时统一失效，
  视口边界由渲染线程在新布局可用后校正

执行门禁：

1. [DONE] 读取项目规则、formal readiness、源码工作台、编辑器渲染链路和历史合同
2. [DONE] 完成行为与性能方案，跑通现有源码工作台定向基线
3. [DONE] 修改工作台、Canvas 编辑器、查找测试、语言资源和相关文档
4. [DONE] 定向测试、编译、formal readiness、architecture boundaries、Debug APK
   与产物审计
5. [PENDING DEVICE] 候选树按本轮授权精确提交推送 `main`；设备现场验收保持
   `verification_pending`

### 2026-08-16 阶段 14：播放器原生依赖升级

状态：`R6 LOCAL DONE / TARGET DEVICE VERIFICATION PENDING`。阶段 13 的播放器行为、Surface、缓存、网络诊断和现有 native packaging
合同继续有效；阶段 14 专门处理 native source/build closure 与 `:ffmpeg` 运行时，不把依赖版本变化
混入播放器状态机或网络回退逻辑。

- M8：固定 mpv `2339eb727` 与 FFmpeg `n8.1.2`，将 Mbed TLS `3.6.6` 刷新为 `3.6.7` 的安全
  refresh 已完成并保留为历史可归因/构建链基线，不再是运行时选入 closure
- 双 M9：`:player` 的 namespaced mpv closure 与 `:ffmpeg` 的 normal-name FFmpegKit closure 均已固定
  FFmpeg `n9.0.1@bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa` 并成对 promotion；产品 AAR
  SHA-256 分别为 `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4` 和
  `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394`。`:ffmpeg` 当前 wrapper 为
  `8.1.7-kiyori-n9.0.1-r6`，OpenH264 为 `v2.6.0`，并启用 GPL/HarfBuzz 与
  `drawtext`/`eq`/`boxblur`；r6 保留 r4 的 Binder threadpool 修复和 r5 的私有 JSON FFprobe，
  增加 capability stdout 与 AI 工具/profile 合同修复
- r6 source/thin/product qualified audit、patch-level exact-hash promotion、native input 和定向
  JVM 已通过；r5 安装包的核心真实媒体矩阵已经通过，但 r6 增量、最终 Debug APK 和全量门禁仍以
  FFmpeg 专项状态文档为准，目标设备状态保持 `verification_pending`
- 权威方案：[播放器原生依赖升级与 closure 迁移](14_player_native_dependency_upgrade.md)

## 用户目标

- 浏览器顶栏左侧返回直接回到打开 Browser Home 前的应用入口页；系统 Back 与底栏左下角返回负责
  临时界面、网页历史和每窗口主页根
- 人工创建和导航的浏览器窗口始终能被 Operit AI 发现、读取和操作
- 软件首页、全屏网页搜索、普通/无痕窗口、设置和下载形成现代、清晰、适配手机与平板的产品界面
- 浏览器窗口总览显示真实网页缩略图，普通与无痕窗口拥有真实隔离语义
- 负一屏接入书签、历史和下载；浏览器菜单以三行 `5/5/5` 工具网格加底部三动作接入真实能力
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
	12_player_process_crash_isolation.md
	13_player_online_playback_reliability_and_compatibility.md
	14_player_native_dependency_upgrade.md
	15_adblock_compiled_runtime_cache.md
```

实施顺序不可交换：播放器和嗅探依赖稳定的 session profile、窗口生命周期、设置 owner、下载 owner 和菜单路由；无痕必须先于缩略图和 AI tab 输出完成，以免后续再次更改窗口模型。

## 里程碑门禁

每一个编号都是独立里程碑，必须严格串行：

1. 只实施该编号的单一主题，不夹带后续功能
2. 更新对应 TODO、`CONTEXT.md` 和用户可见文档
3. 先运行最窄的定向测试，再运行正式开发门禁与 `git diff --check`
4. 成功构建 Debug APK，核对时间、大小、SHA-256、包名、版本和签名
5. 记录任务日记和 APK 证据后才进入下一编号

提交和推送不是里程碑默认动作。只有用户在当前任务中另行明确授权时，才审计 staged allowlist、提交到唯一 `main` 并核对远端 SHA；未获授权的历史里程碑状态不能替代当前任务权限。

任何构建仍在运行时不得启动第二个 Gradle 构建。Release、部署、APK 安装、ADB、MuMu 和设备自动化不在本计划授权范围内。

## 全局状态所有权

| 状态 | 唯一 owner | 人工 UI | AI |
| --- | --- | --- | --- |
| 标签、活动标签和 WebView | `StandardBrowserSessionTools` | 直接操作 | `browser_*` 操作同一实例 |
| 窗口 Profile | `WebSession.profile` | 搜索页和窗口页选择 | `browser_tabs` 读取并可显式创建 |
| 新窗口默认 Profile | Browser Runtime | 无痕按钮改变 | `browser_tabs list` 可观察 |
| 历史、书签和搜索记录 | `WebSessionHistoryStore` | 浏览器与负一屏共享抽屉，全屏搜索复用搜索记录 | 浏览器工具不复制存储 |
| 下载任务 | `BrowserDownloadManager` | 浏览器抽屉和全屏下载中心复用 | 下载事件进入浏览器结果 |
| 浏览器运行偏好 | 搜索与历史由 `WebSessionHistoryStore` 持有；Profile 由 Browser Runtime 持有；UA 与浏览器通用设置由 `WebSessionBrowserSettingsStore` 持有 | 浏览器主流程直接使用唯一 owner | 只通过明确能力读取或修改 |
| 设置导航 | `KiyoriSettingsNavigationState` + capability-level `KiyoriSettingsRoute` | 底部设置、Browser Menu 与 AI 左抽屉共用同一来源保持 route stack | Operit 设置 route 只携带同一 settings session context |
| 普通窗口启动恢复 | `BrowserSessionRecoveryStore` + Browser Runtime 的最小普通窗口投影 | 按三个启动恢复开关自动恢复或询问 | AI 不从磁盘导入旧页面，只操作当前 live runtime |
| 播放会话 | 唯一 `PlayerSession` / mpv core | 全屏、悬浮和浏览器共用同一媒体与 Surface owner 状态 | 后续 capability adapter 只能调用该 owner |

## 全局交互合同

- Browser Home、1×1 background anchor 和 AI 只转挂同一个活动 WebView，不在展示切换时调用 `loadUrl`、`reload` 或重建
- 完整浏览器 UI 只存在于 App Shell Browser Home。离开 Browser Home 必须显式选择“关闭展示”或“最小化到 indicator”：底部浏览器入口、软件首页搜索、书签和外部网址等普通入口关闭时不显示 indicator；浏览器菜单进入 AI 对话、AI 首页顶栏进入浏览器后返回，以及点击已有 indicator 恢复浏览器后再次返回时，才把同一活动 WebView 转挂到 background anchor 并显示 indicator。AI `browser_*` 在 Browser Home 未挂载时主动使用浏览器，也可按现有唯一 WebSession 路径创建 background anchor 并显示 indicator
- 浏览器顶栏左侧返回直接按 Shell 记录的入口页和 presentation 方式离开 Browser Home，不调用
  WebView Back。系统 Back、底栏左下角返回和 AI `browser_navigate_back` 共用逐级回退状态机：
  先关闭文本选择、网页弹窗、下载确认、菜单或子抽屉、搜索引擎面板和全屏搜索，再执行当前窗口
  主页根之后的 WebView 历史后退；历史耗尽且当前窗口尚未位于自定义主页时先进入主页并建立新的
  历史根，只有已经位于主页根时才按本次入口的离开方式退出 Browser Home
- 普通网页的同站、跨站、用户 `_blank` 和用户 `window.open()` 都在当前 WebSession 中导航；
  popup 只使用不注册到窗口列表的同 Profile 临时解析 WebView。只有配置主页根上的真实用户跨站
  跳转会创建同 Profile 子窗口并保留 opener 主页；子窗口历史耗尽后关闭并回到仍有效的主页窗口
- 底部设置、Browser Menu 与 AI 左抽屉共用 `KiyoriSettingsNavigationState`。详情按
  `KiyoriSettingsRoute` 栈逐级返回，Browser/AI 来源在设置首页关闭后恢复原 Browser Home/WebSession
  或原 AI 页面/路由栈；扩展中心和脚本工作台通过一次性 return token 恢复同一设置会话
- “滑屏前进后退”默认关闭，开启后只从左右边缘请求当前窗口 Back/Forward，并在抽屉、搜索、
  文本选择、广告标记、对话框和无障碍触摸探索等冲突状态下停用
- 普通窗口启动恢复只保存最小 URL 级投影。`保留多窗口`、`恢复上次的搜索结果` 和
  `询问是否恢复页面` 按多窗口、搜索候选、活动普通页的顺序组合；无痕窗口及 Cookie、请求头、
  DOM、表单、正文、截图和密码始终不落盘。搜索结果窗口离开已加载结果页后立即失去搜索恢复资格
- 系统层最小 indicator 单击时通过专用恢复 action 打开 Browser Home，长按时消费进入动作并创建球体右上角外围的透明 `28dp` 临时关闭窗口，其中只绘制 `16dp` 红色叉号。该窗口按住期间不可触摸，松手后保留 3 秒，随拖动同步且不越出屏幕，点击复用菜单 `onExitBrowser`；后台 anchor 不处理浏览器 Back、IME、cutout 或完整 chrome
- 软件首页全屏搜索创建新窗口；浏览器顶栏搜索继续导航当前窗口，两者不混用
- 普通窗口和无痕窗口不能互相转换；关闭窗口后其 Profile 语义不改变
- AI 可以操作无痕窗口，但无痕仅隔离 WebView 网站数据，不隔离本应用内已经获得授权的 AI；所有相关 UI 必须明确说明
- 不支持 AndroidX WebKit Multi-Profile 的设备明确禁用无痕，不使用共享 Cookie、手动清 Cookie或其他伪无痕实现
- 真机视觉、状态栏、系统 Back、WebView 多 Profile、输入法、窗口缩略图和播放器转场在用户验收前保持 `verification_pending`

## 当前优先级

1. [DONE] P0：悬浮浏览器系统 Back、状态栏背景和人工窗口 AI 接管；本地实现、定向测试与 Debug APK 已完成，真机验收待用户执行
2. [DONE] P0：软件首页与全屏搜索已接入共享 Browser Runtime；2026-07-28 完成搜索引擎图标、覆盖式淡蓝引擎面板、标题/网址双行操作区、自适应历史标签和显式删除提交，并按浏览器菜单基准压缩尺寸、补齐面板周围收起与当前网页区返回。历史清空现使用底部确认框且确认后立即执行，标签叉号仍由“完成”提交；此前本地定向测试、formal readiness、Kotlin 编译与 Debug APK 已验证。最新历史区放大与复制/编辑图标缩小按用户要求未运行 Gradle 或 APK 构建，真机视觉、输入法和无痕 Profile 交互继续待验收
3. [DONE] P1：真无痕 Profile、窗口逻辑与网页缩略图；本地实现、定向测试与 Debug APK 已完成，真机 WebView Multi-Profile、缩略图和交互待用户验收
4. [LOCAL DONE / VERIFICATION PENDING] P1：网页浏览器设置当前为 `2/4/3/3/2/1/5/3` 八组 23 行：“内容过滤 / 网页扩展与脚本 / 主页与导航 / 启动与窗口 / 网页显示 / 网页交互 / 网站权限与数据 / 音视频嗅探”。页面顶部明确全局能力上限；内容过滤复用唯一 `BrowserAdBlockStore` 的总开关与管理入口，网站密码总开关和管理入口职责分离。浏览器菜单第三行第五个“网站配置”已成为冻结当前 HTTP(S) 完整 host 的真实可拖动抽屉，以 `globalEnabled && !siteDisabled` 为唯一优先级，提供广告拦截、用户脚本、返回缓存、左右滑动前进后退、缩放、长按、外部应用、定位、密码、嗅探入口和自动悬浮十一项负向开关并持久化；广告继续使用原白名单 owner，其余十项由 `WebSessionBrowserSettingsStore` 精确 host 规则持有。主源码编译和定向 JVM 已通过，最终门禁与本轮 Debug APK 证据以 `docs/TODO/README.md` 为准。目标设备上的布局、抽屉拖动、系统 Back、真实站点行为、重启持久化、普通/无痕和后台导航现场矩阵保持 `verification_pending`
5. [IN PROGRESS] P1：下载中心与文件下载器设置；设置页已按播放器标准重排为 `5/2/3/1` 四组 11 行，“默认保存位置”统一选择应用目录、公开目录或 SAF 自定义目录，继续复用唯一 `BrowserDownloadSettingsStore` 和 `BrowserDownloadManager`；系统下载器生效时内置引擎专属项目明确禁用。仍待下载中心双筛选/批量操作复刻及真机综合验收
6. [IN PROGRESS] P1：负一屏与四行菜单真实能力；书签/下载共享抽屉和 UA 标识直达弹窗、全局模式、域名规则已完成。2026-07-30 已完成统一历史抽屉：扩展现有 `WebSessionHistoryStore`，普通网页访问与唯一 `PlayerSession` 分别写入网页/视频记录，视频区分在线与本地，浏览器菜单与负一屏共享搜索、六分类和分时段删除抽屉；定向测试、Debug APK 和新版历史界面用户验收已通过，完整设备场景仍按第六阶段清单继续验证
7. [DONE] P2：阶段 8 媒体 Intent、唯一 PlayerSession、全屏播放器、设置页与 native 边界，以及阶段 9 candidate、浏览器嗅探、现有下载 owner、同会话悬浮/全屏入口均已完成本地实现；2026-07-28 又完成精确视频格式、被动时长、推荐排序、动态格式筛选、双开关与结果动作弹窗。人工播放固定进入横向全屏，自动推荐才进入悬浮；该里程碑保留为嗅探入口完成记录，后续在线播放 native 修复见阶段 8、10、11 的 `2026-07-29` 补充证据
8. [DONE] P0：阶段 11 的运行时、设置、浏览器候选与 native 门禁已完成；全屏播放器保留现有横竖屏结构与播放器专用图标，并统一为深色圆角菜单、半透明按钮和现代加载/错误/手势反馈。浏览器悬浮播放器为内容区全宽、固定 `16:9`、零边距，并与全屏共用唯一 session 和快进快退设置。`2026-07-28 19:18:21 +08:00` 的必现闪退纠正了播放器启动契约；`2026-07-29 14:31 +08:00` 的用户报告进一步确认 HTTPS MP4 已能进入 `ACTIVE` 并持续播放。稳定 pointer detector 统一协调单击、双击、长按升档、seek、亮度、音量、弹窗、进度拖动、三秒自动隐藏与锁定解锁；亮度提示位于右侧，音量提示位于左侧。没有真实 owner 的弹幕显示为禁用，更多菜单保留真实自动旋转开关和查看日志；阶段 11 当时的设置页为 `4/7/5/2/2/4` 六组 24 项，阶段 13 拆分“解码方式 / 渲染预设”并把在线播放缓存收敛为单一四档后当前为 `4/7/6/2/2/4` 六组 25 项真实 `PlayerSettingsStore` 配置。Anime4K 严格使用 `mpv-android-anime4k@32f5f169` 的关/A/B/C/A+/B+/C+ Balanced/M 链，并校验资产、私有缓存和 MPV 属性。播放器日志汇总主进程和独立 runtime 的有界 MPV/FFmpeg/demux、命令与错误，并使用屏幕内固定分区、最新在前结构化列表和 8 个单行横滑分类，关闭固定在标题栏，清空/复制/导出固定在底部。自动检查和 Debug APK 证据以阶段 11 最新记录为准；本地/在线视频的控制层真机交互、HLS 与更多站点仍保持 `verification_pending`
9. [LOCAL DONE] P0：阶段 12 已完成结构化崩溃报告、唯一非导出 `:player` AIDL service、串行 MPV owner、Surface ACK、Binder death、退出证据、前后台 `:crash` 展示和用户明确重启。主进程 `PlayerSession` 不再构造 engine/resolver 或同步读取 MPV，death 路径不会自动 bind、load 或刷新 WebView。自动检查和各里程碑 Debug APK 构建通过；设备安装、真实进程终止和 vivo Android 16 验收未授权，最终状态保持 `verification_pending`。详细证据见 [播放器进程崩溃隔离与诊断页](12_player_process_crash_isolation.md)
10. [LOCAL DONE / VERIFICATION PENDING] P0：阶段 13 已完成当前文档静态资源目录、图片缩略图/查看器、“资源嗅探”音视频统一、`.mp3` 伪装视频 DOM 证据、原始 URL/headers 唯一播放入口，以及在线播放快速启动、seek、header、四档缓存、横竖屏 Surface 拉伸、actual demux/track、bounded runtime capability、MediaCodec、结构化网络失败和 VPN/Private DNS/代理/IPv4/IPv6 active/effective network 观察。`2026-08-16` 六份后续 vivo Android 16 报告确认 capability 初始化修复、两条实际成功播放、VPN/静态代理下两条首次 TCP 443 refused，以及伪装 `.mp3` 的 MP4/HEVC 视频成功播放；同时证明完整缓存错误依赖固定 runtime 不保证的 `stream-start`，活动播放 Surface 转挂会产生内部 seek，媒体身份需要在稳定 `VIDEO_RECONFIG` 后刷新。最终实现把在线播放缓存收敛为唯一“省流模式 / 智能均衡 / 流畅优先 / 完整缓存”四档策略，删除未发布的独立完整缓存开关，闭环 cache-state evidence、direct VOD 资格、空间/文件门禁、会话目录清理、用户 seek 所有权和 `VIDEO_RECONFIG` 身份刷新，并保留固定 binding 生命周期。完整 App JVM `1310/1310`、项目 Python `186/186`、formal readiness、architecture、AndroidTest 编译、Debug APK 构建和播放器 packaging/native/签名/16 KiB 审计已通过；最终 APK SHA-256 为 `9A02093155A717EF5018CD6A41DE192A2497F810618F51D4F38DA8C14882CF2A`。完整 Lint 仍有无当前 diff 的 `WebSessionHistorySheet.kt:399/408/437/450` 四个既有错误，未扩 baseline 或 suppression。该阶段封板闭包为 mpv `2339eb727`、FFmpeg `n8.1.2` 和 Mbed TLS `3.6.6`；阶段 14 已把同一 mpv 快照、Mbed TLS `3.6.7` 的播放器 closure 与 FFmpegKit closure 成对升级到 FFmpeg `n9.0.1`，完成 `:ffmpeg` 进程隔离、r2 产品 promotion、最终本地 APK 构建与独立静态审计。真实缓存断网 seek、VPN 单变量、硬解和 Surface 视觉矩阵保持 `verification_pending`。详细设计与证据见 [在线播放可靠性、兼容性与快速启动方案](13_player_online_playback_reliability_and_compatibility.md)

`2026-07-29` 在线播放修正已经把 `:player` 进程切换到固定 mpv 输入自带、启用 Mbed TLS 的
`libmp*.so` FFmpeg 命名空间；主进程 FFmpegKit 工具栈保持不变。用户已于
`2026-07-29 14:31 +08:00` 确认 HTTPS MP4 真机播放成功；HLS、更多请求头站点、证书错误路径和本轮
控制层交互仍为 `verification_pending`。

### 2026-08-08 浏览器顶栏返回入口页

- Browser Home 顶栏左侧返回从逐级网页 Back 状态机中拆出，直接释放当前 App presentation，
  并通过现有 `KiyoriShellState.exitBrowser()` 回到记录的应用入口页
- Shell 返回目标覆盖负一屏、软件首页、AI 首页、微应用、文件管理和设置首页；底部浏览器入口、
  书签/历史共享抽屉和普通外部浏览器请求均从当前 Shell 状态解析来源
- `CLOSE` 仍只销毁浏览器 presentation，`MINIMIZED_INDICATOR` 仍转挂同一活动 WebView；
  WebSession、窗口顺序、Profile、网页历史、下载和用户脚本状态保持不变
- 系统 Back 与底栏左下角返回继续复用 `WebSessionBrowserHost` 的临时界面、网页历史和主页根状态机
- 本地策略与 Shell 测试 `56/56`、formal readiness、Markdown `7/7` 与 257 个工作树文件链接检查、
  `git diff --check`、规定 Debug 构建、唯一 Launcher、Player runtime packaging、V2 Debug 单签名和
  16 KB ZIP 对齐均通过。APK 为 `471581414` 字节，SHA-256
  `B274A738017A4F371A42F4003A503F7137A3964BDC4510334EF48095C802F7E3`
- 完整架构门禁只报告任务开始前已记录的 ARCH046 `UserscriptSourceExportHelper.kt` 消费者快照漂移；
  本轮触及的 ARCH020、ARCH021、ARCH023、ARCH024 均通过。目标设备交互继续保持
  `verification_pending`

### 2026-08-08 浏览器插件与负一屏入口配色调整

> 以下为当时的历史映射；2026-08-27 删除 `READER_MODE` 后，负一屏“手册”改用
> `DIAGNOSTICS`，其余历史构建与配色证据不变。

- 浏览器菜单 `PLUGINS` 从高饱和洋红改为低饱和深梅紫：浅色
  `#5E3A8A / #EEE8F4`，深色 `#CBB8E2 / #2D2238`
- 扩展中心、用户脚本“本页 / 已安装 / 更新 / 日志”、详情与编辑器的主扩展图标统一复用
  `PLUGINS`；错误、成功、警告和更新安全性继续使用独立状态色
- 负一屏“收藏 / 书签 / 历史 / 下载”分别复用浏览器菜单
  `ADD_BOOKMARK / BOOKMARKS / HISTORY / DOWNLOADS`；收藏仍为零计数和空点击，专用于未来
  小程序服务，不连接网页书签或其他当前能力
- “新版 / 手册 / 版本 / 搜索 / 工具箱 / 清理 / 备份 / 退出”八个空动作快捷工具分别复用
  `PAGE_SOURCE / READER_MODE / PLUGINS / AI_DIALOGUE / TOOLBOX / AD_MARKING / DOWNLOADS /
  EXIT_BROWSER`，颜色两两不同且不新增第二套主题 owner
- 颜色策略与负一屏结构定向 JVM 两个 suite、插件与导航相关回归合计 9 个 suite 共 `89/89`
  通过；ARCH041 消费者正反向测试 `2/2`、formal readiness、Markdown `7/7`、257 个工作树
  Markdown 文件缺失本地链接 `0` 和 `git diff --check` 通过
- 最终 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 52s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `471581414` 字节，生成时间 `2026-08-08 20:08:41 +08:00`，SHA-256
  `B585A865CE7C349EB41897F1B568E78B8802F8FF13DC95F22DB23C3DCEDC9084`；
  `com.kiyori / 45 / 0.1.0 / arm64-v8a`，Android Debug V2 单签名和 16 KB ZIP 对齐通过
- 完整架构门禁只报告任务开始前已记录的 ARCH046 `UserscriptSourceExportHelper.kt` 消费者快照
  漂移；目标设备视觉验收继续为 `verification_pending`

### 2026-08-08 首页返回与浏览器彩色 UI 统一

- 软件首页 Pager 拖动成为拖动期间底栏显隐的事实源；从负一屏或 AI 首页向中心页移动后，底部五入口
  按实时偏移立即进入组合与透明度变化，不等待 `softwareHomePage` 在吸附结束后才更新
- 浏览器窗口总览顶部保持同一行的普通/无痕文字标签，不绘制按钮容器；普通标签下方为蓝线，
  无痕标签下方为紫线。空态、新建按钮、活动窗口卡片、缩略图占位和身份标记继续使用对应语义色
- 配置自定义首页时，清空当前 Profile 后通过现有新建窗口链路创建同 Profile 主页并关闭总览；
  默认空白首页继续显示剩余 Profile 或空总览
- 下载抽屉使用绿色标题徽标与操作弹窗，UA 三级弹窗使用蓝色标题，媒体候选长按及链接查看使用青色
  标题；三者复用一个圆角、描边、分隔线和遮罩一致的语义弹窗容器。下载四竖线使用标题后动作槽位，
  视觉紧邻“我的下载”，新增与清理动作继续位于右侧
- 页面源码超长行信息条增加右侧关闭按钮；关闭只影响当前 session 与 Document token 的展示状态，
  重新抓取新文档后重新显示，不改变源码内容和软换行
- 本地源码已在 JDK 21 下通过 `:app:compileDebugKotlin`；上一批 7 个定向 suite 共 `99/99` 项，
  用户复核增量 5 个 suite 共 `29/29` 项，formal readiness、Markdown/差异检查和 Debug APK
  构建通过。最终 APK 为 `471581414` 字节，
  SHA-256 `8E1C37CBD651B36417DDF075741802E58FD086AD82CBA884C600F1FE866785C3`，
  `com.kiyori / 45 / 0.1.0 / arm64-v8a`，唯一 launcher、Android Debug V2 单签名和
  16 KB ZIP 对齐通过；目标设备视觉验收继续为 `verification_pending`

### 2026-07-28 浏览器搜索与菜单追加小步

- Browser Home 文本搜索后显示 legacy Kiyori 风格的横向九引擎切换条，切换后以同一 query 重搜
- 一次性外部应用确认状态和 UI 已删除；持久权限开启时仅显式主框架手势可启动外部 Intent
- 菜单第 4 行采用 `2:1:2` 槽位，两侧按钮向内移动而中间收起按钮保持居中
- 搜索解析、外部导航与菜单布局定向测试 `15/15`、formal readiness 与 Debug APK 构建已通过；
  真机交互继续保持 `verification_pending`

### 2026-07-28 浏览器悬浮球长按关闭追加小步

- indicator 单击继续打开现有 Browser Home；长按由同一 detector 消费并创建球体右上角外围的
  透明 `28dp` 独立临时关闭窗口，其中只绘制 `16dp` 红色叉号
- 按住期间叉号不可触摸，松手后才允许点击并保留 3 秒；叉号随拖动同步并约束在屏幕内
- 点击叉号复用菜单第 4 行第 1 个按钮的 `onExitBrowser` 展示层关闭流程，临时窗口不持有 Browser Runtime
- 关联 JVM 测试 `16/16`、Kotlin 编译、formal readiness、`git diff --check` 与 Debug APK 构建通过
- APK SHA-256 为 `689A7EA123EC0107C7A29E8A82138BE6B383374EBC1365325A3F1816D6BFD463`；
  真机外围视觉、长按不中断、计时、边缘钳制、拖动同步和关闭结果保持
  `verification_pending`

### 2026-07-30 浏览器退出、AI 往返、逐级 Back 与无痕菜单收口

- Shell 同时记录浏览器返回目标和离开后的 presentation 方式，避免“返回 AI”与“是否显示 indicator”
  被一个布尔条件混在一起
- 底部第二个浏览器入口、软件首页搜索、书签和普通外部入口使用“关闭展示”；浏览器窗口和 WebSession
  保留，退出后不显示 indicator
- 浏览器菜单“AI 对话”直接进入 AI 首页并请求真实输入框焦点，同时把当前浏览器最小化为 indicator；
  AI 首页右上角浏览器按钮进入 Browser Home 后，最终返回也恢复 indicator
- indicator 使用独立恢复入口；点击已有 indicator 进入 Browser Home 后，最终返回继续恢复同一个
  indicator，不把这次恢复误当作普通底栏入口
- AI `browser_*` 在 Browser Home 未挂载时继续通过唯一 WebSession 主动请求 background anchor；
  不新增第二 WebView、第二浏览器运行时或并行悬浮球 owner
- 浏览器顶栏返回、底栏返回与系统 Back 共用 `WebSessionBrowserHost` 的逐级状态机；网页有当前
  窗口主页根之后的历史时先后退，历史耗尽且尚未位于自定义主页时先进入主页并重建该窗口历史根，
  已位于主页根后才按本次入口退出
- 上述顶栏接线是 2026-07-30 的历史合同；2026-08-08 起，顶栏左侧返回改为直接回到记录的
  App Shell 入口页，逐级状态机仅保留给系统 Back、底栏左下角返回和 AI 浏览器后退
- 菜单“无痕模式”不再打开窗口总览；它与全屏搜索右上角按钮共用默认 Profile 切换和短时提示，
  不切换当前不可变 Profile 标签，也不关闭仍在显示的菜单
- 用户实测发现首次实现从 Browser Home 切回软件 Shell 时，Pager 同步协程会先消费旧的
  `HOME` 已稳定页并覆盖显式 `AI_HOME` 请求。现已只忽略启动时未变化的旧页快照；目标 AI 页
  真正稳定后才同步状态，若 AI 页本来已经稳定则立即标记可用并继续执行输入焦点/IME 请求
- 本地验收：8 个定向 JVM suite 共 79 项测试全部通过；其中包含 AI 首页一次性输入焦点和
  已稳定目标页回归；
  Kotlin 编译、formal readiness、
  `git diff --check`、`:app:assembleDebug` 和 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`com.kiyori`，
  `versionCode=45`，`versionName=0.1.0`，大小 `482617522` 字节，SHA-256
  `8C095478ED9D5CD4F0D6D6546D20BF8404EA7D611BB0F13CFE4900BF53CC71B4`；V2 Debug 签名与
  `zipalign -c -P 16 4` 通过
- 设备上的 IME、系统 Back、indicator、WindowManager 转挂和菜单提示继续保持
  `verification_pending`

### 2026-08-03 自定义主页确认与每窗口回退根修复

- “恢复为空白页”改为确认后才把唯一 `WebSessionBrowserSettingsStore.homeUrl` 写为
  `about:blank`；取消和弹窗外关闭不修改当前自定义主页
- 每个现有 `WebSession` 记录自己的主页请求、重定向后的有效主页和待完成导航；软件首页搜索、
  书签、用户脚本、AI 与网页 popup 直接创建的目标窗口在历史耗尽后先进入当前主页
- 主页完成后只清理该窗口主页根之前的 WebView 历史，防止再次穿越旧目标页形成循环；窗口切换和
  关闭继续由 `activeSessionId` 与现有窗口顺序选择新活动 `WebSession`
- 系统 Back、底栏返回和 AI `browser_navigate_back` 复用同一每窗口页面回退函数；临时界面、
  有效历史、主页根与 Browser Home 离开方式保持明确分层。顶栏返回已在 2026-08-08 改为
  App Shell 入口页返回，不再调用该页面回退函数
- 本地相关 JVM 测试 `94/94`、主代码 Kotlin 编译、formal readiness、工作树 Markdown 链接、
  `git diff --check` 和新增代码禁用兜底扫描通过。规定 Debug 构建及唯一 launcher、Player runtime
  packaging 通过；APK 为 `465765311` 字节，SHA-256
  `7157434F8938FCB4BD4608098948086C580CC1FDCB5B8910CD7490EF1DF3A732`，包名/版本
  `com.kiyori / 45 / 0.1.0`，V2 Debug 签名、单 signer 与 16 KB ZIP 对齐通过
- 系统 Back、顶栏/底栏点击、普通/无痕多窗口切换、主页重定向和旋转恢复继续保持
  `verification_pending`

### 2026-07-28 浏览器视频嗅探抽屉优化

- 媒体候选 UI 只接收可执行视频，不展示音频、`blob:`/MSE、只有 MIME 的 API URL 或媒体分片
- “搜索栏嗅探入口”和“自动悬浮播放”在浏览器设置的“音视频嗅探”组分别控制搜索框资源嗅探入口
  与推荐候选自动悬浮；候选抽屉本身不再显示设置开关
- 横向筛选只显示“全部”和本页实际嗅探到的具体格式；每项展示原始链接、被动时长或直播/未知状态
- 候选按网页播放状态、当前视频元素、主视口、格式、分辨率、时长、直播、多来源与噪声惩罚统一排序
- 点击或长按结果显示播放、下载、复制和查看链接动作；右下角保留紧凑下载/播放按钮
- 人工播放固定进入已有 `sensorLandscape` 全屏 Activity，自动推荐才进入悬浮；`2026-07-29` 的
  原生 HTTP/HTTPS 修正沿用同一 `PlayerSession` 与候选请求上下文
- 定向 JVM 测试已通过；formal readiness、最终 Debug APK 与真机字体/交互验收见本轮任务记录

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

## 2026-08-03 页面源码工作台

浏览器菜单第 3 行第 3 项保留现有蓝色源码图标和“查看源码”名称，旧只读源码子抽屉已经由浏览器
宿主内全屏原生源码工作台替换。工作台读取活动 WebSession 的实时 DOM，保留 doctype、过滤 Kiyori
文本选择临时节点，并把基线、编辑缓冲区、session 与 Document token 绑定。现有
`NativeCodeEditor` 提供 HTML 高亮、行号、补全、查找替换、撤销重做、格式化和输入法符号栏；
页面源码默认使用横向浏览，并可切换不修改源码字符的视觉软换行；两种模式
和模式切换均从源码左上角开始。紧凑工具条提供准确历史状态、
复制、跳转行与行列/选区信息，超长行提示显示数量和最长字符数；顶栏及底部动作区分别遵守状态栏
和导航栏 Insets。

应用源码必须经用户确认，并只允许写入捕获时的同一 session 与 Document。运行时使用
`document.open/write/close` 重建该页面，随后刷新同一 Browser Runtime 的 userscript 状态、
下载、文本选择和媒体观察助手；不创建新 WebView、历史项、网络源码仓库或持久网页副本。
`browser_page_source` 为 AI 提供 `live/editor` 两种显式读取范围，大源码进入既有浏览器临时输出
目录；人工工作台的应用确认不交给 AI 静默执行。完整 UI、状态、AI 与验收合同见
[`7_browser_menu_capabilities.md`](7_browser_menu_capabilities.md)。

本地实现已完成：定向 `22/22`、完整 Debug JVM `853/853`、Kotlin 编译、formal readiness、
七语种资源、ARCH041 契约测试和 `git diff --check` 通过；Debug APK 的 V2 签名、16 KB 对齐、
单 launcher 和 Player runtime packaging 已重新核验。APK 为 `471581414` 字节，SHA-256
`F2D7683FE6EE114B7171F635B58A0FB24EECF32DB1B0E445C6C2D4B5547FA97E`。完整架构门禁仍受
当前 HEAD 既有 ARCH046 userscript 导出路径消费者快照漂移影响。状态栏、输入法、复杂页面脚本重建、
刷新恢复、跨标签保护和 AI 协作仍需目标设备验收。

## 2026-08-20 页面源码工作台交互与长源码性能增量

状态：`LOCAL DONE / TARGET DEVICE VERIFICATION PENDING`。

本增量保持上述唯一源码工作台和编辑器 owner，完成默认横向浏览、两种模式左上角初始化、
查找栏“上一个”、替换游标修复，以及横向长行 checkpoint 和缩放期间布局延迟重建方案。
定向回归 `25/25` 覆盖查找循环/替换长度、模式起点、长行 checkpoint、Tab 和宽字符映射；
完整 Debug JVM 为 `247 suites / 1458 tests` 且零失败，Kotlin 编译、formal readiness、
architecture `phase=m03`、七语种 XML 和 `git diff --check` 通过。规定的 Debug 构建为
`232` tasks，APK 为 `472652738` bytes，SHA-256
`563E98259762B2C6F152EF02C075FEDBACB58018168289FCABD98FBB61F517CE`；包身份、唯一
launcher、Android Debug v2 单 signer、16 KB ZIP 对齐和 `52` 个 ELF64/AArch64 的
`PT_LOAD >= 0x4000` 审计通过。真机视觉、双指缩放帧率、超长网页实际触摸/输入法和应用源码
后的复杂脚本行为不由本地自动证据替代。

## 完成定义

- 十一个里程碑均有源码、文档、自动检查和 Debug APK 证据；提交、推送和远端 SHA 仅在另行授权时属于完成证据
- 所有已展示入口都有真实实现；仍受硬依赖限制的入口不伪造成功状态
- 人工和 AI 在普通与无痕窗口中共享唯一 Browser Runtime，并能从工具结果确认窗口 Profile
- 视频 `ACTION_VIEW` 不再进入 AI 附件链路
- 浏览器到播放器的全屏、悬浮、返回转场不刷新、重载或修改当前网页
- 用户醒来后只需安装最后一个 Debug APK，按真机清单完成现场验收
