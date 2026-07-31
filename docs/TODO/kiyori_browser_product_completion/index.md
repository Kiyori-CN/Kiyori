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

> 架构归属说明：本计划的 Browser/Player 行为合同和历史验收证据继续有效；未来源码所有权、
> 目标包路径和 Operit 兼容岛由
> [Kiyori 项目架构与 Operit 命名重构方案 v3](../kiyori_architecture_refactor/index.md)
> 接管。本文及分项文档中的历史路径不追溯改写，只有真实迁移完成的里程碑才更新当前入口。

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
	12_player_process_crash_isolation.md
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
| 历史、书签和搜索记录 | `WebSessionHistoryStore` | 浏览器与负一屏共享抽屉，全屏搜索复用搜索记录 | 浏览器工具不复制存储 |
| 下载任务 | `BrowserDownloadManager` | 浏览器抽屉和全屏下载中心复用 | 下载事件进入浏览器结果 |
| 浏览器运行偏好 | 搜索与历史由 `WebSessionHistoryStore` 持有；Profile 由 Browser Runtime 持有；UA 与浏览器通用设置由 `WebSessionBrowserSettingsStore` 持有 | 浏览器主流程直接使用唯一 owner | 只通过明确能力读取或修改 |
| 播放会话 | 唯一 `PlayerSession` / mpv core | 全屏、悬浮和浏览器共用同一媒体与 Surface owner 状态 | 后续 capability adapter 只能调用该 owner |

## 全局交互合同

- Browser Home、1×1 background anchor 和 AI 只转挂同一个活动 WebView，不在展示切换时调用 `loadUrl`、`reload` 或重建
- 完整浏览器 UI 只存在于 App Shell Browser Home。离开 Browser Home 必须显式选择“关闭展示”或“最小化到 indicator”：底部浏览器入口、软件首页搜索、书签和外部网址等普通入口关闭时不显示 indicator；浏览器菜单进入 AI 对话、AI 首页顶栏进入浏览器后返回，以及点击已有 indicator 恢复浏览器后再次返回时，才把同一活动 WebView 转挂到 background anchor 并显示 indicator。AI `browser_*` 在 Browser Home 未挂载时主动使用浏览器，也可按现有唯一 WebSession 路径创建 background anchor 并显示 indicator
- 系统 Back 与浏览器顶栏返回共用同一逐级回退状态机：先关闭文本选择、网页弹窗、下载确认、菜单或子抽屉、搜索引擎面板和全屏搜索，再执行当前 WebView 历史后退，历史耗尽后才按本次入口的离开方式退出 Browser Home
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
4. [DONE] P1：网页浏览器设置已按播放器标准重排为 `3/5/4/5/6` 五组，统一分组说明、双行设置项、Material Switch 和禁用态；自定义主页、搜索栏嗅探入口、自动悬浮播放、网页外部应用和网页定位接入唯一 `WebSessionBrowserSettingsStore`，其余无本页 consumer 的项目明确禁用且不再打开空页。两个嗅探开关只位于“音视频嗅探”组，媒体候选抽屉不再承载设置开关
5. [IN PROGRESS] P1：下载中心与文件下载器设置；设置页已按播放器标准重排为 `5/2/3/1` 四组 11 行，“默认保存位置”统一选择应用目录、公开目录或 SAF 自定义目录，继续复用唯一 `BrowserDownloadSettingsStore` 和 `BrowserDownloadManager`；系统下载器生效时内置引擎专属项目明确禁用。仍待下载中心双筛选/批量操作复刻及真机综合验收
6. [IN PROGRESS] P1：负一屏与四行菜单真实能力；书签/下载共享抽屉和 UA 标识直达弹窗、全局模式、域名规则已完成。2026-07-30 已完成统一历史抽屉：扩展现有 `WebSessionHistoryStore`，普通网页访问与唯一 `PlayerSession` 分别写入网页/视频记录，视频区分在线与本地，浏览器菜单与负一屏共享搜索、六分类和分时段删除抽屉；定向测试、Debug APK 和新版历史界面用户验收已通过，完整设备场景仍按第六阶段清单继续验证
7. [DONE] P2：阶段 8 媒体 Intent、唯一 PlayerSession、全屏播放器、设置页与 native 边界，以及阶段 9 candidate、浏览器嗅探、现有下载 owner、同会话悬浮/全屏入口均已完成本地实现；2026-07-28 又完成精确视频格式、被动时长、推荐排序、动态格式筛选、双开关与结果动作弹窗。人工播放固定进入横向全屏，自动推荐才进入悬浮；该里程碑保留为嗅探入口完成记录，后续在线播放 native 修复见阶段 8、10、11 的 `2026-07-29` 补充证据
8. [DONE] P0：阶段 11 的运行时、设置、浏览器候选与 native 门禁已完成；全屏播放器保留现有横竖屏结构与播放器专用图标，并统一为深色圆角菜单、半透明按钮和现代加载/错误/手势反馈。浏览器悬浮播放器为内容区全宽、固定 `16:9`、零边距，并与全屏共用唯一 session 和快进快退设置。`2026-07-28 19:18:21 +08:00` 的必现闪退纠正了播放器启动契约；`2026-07-29 14:31 +08:00` 的用户报告进一步确认 HTTPS MP4 已能进入 `ACTIVE` 并持续播放。稳定 pointer detector 统一协调单击、双击、长按升档、seek、亮度、音量、弹窗、进度拖动、三秒自动隐藏与锁定解锁；亮度提示位于右侧，音量提示位于左侧。没有真实 owner 的弹幕显示为禁用，更多菜单保留真实自动旋转开关和查看日志；播放器设置页为 `4/7/5/2/2/4` 六组 24 项真实 `PlayerSettingsStore` 配置。Anime4K 严格使用 `mpv-android-anime4k@32f5f169` 的关/A/B/C/A+/B+/C+ Balanced/M 链，并校验资产、私有缓存和 MPV 属性。播放器日志汇总主进程和独立 runtime 的 MPV verbose、命令与错误，并使用屏幕内固定分区、最新在前结构化列表和 8 个单行横滑分类，关闭固定在标题栏，清空/复制/导出固定在底部。自动检查和 Debug APK 证据以阶段 11 最新记录为准；本地/在线视频的控制层真机交互、HLS 与更多站点仍保持 `verification_pending`
9. [LOCAL DONE] P0：阶段 12 已完成结构化崩溃报告、唯一非导出 `:player` AIDL service、串行 MPV owner、Surface ACK、Binder death、退出证据、前后台 `:crash` 展示和用户明确重启。主进程 `PlayerSession` 不再构造 engine/resolver 或同步读取 MPV，death 路径不会自动 bind、load 或刷新 WebView。自动检查和各里程碑 Debug APK 构建通过；设备安装、真实进程终止和 vivo Android 16 验收未授权，最终状态保持 `verification_pending`。详细证据见 [播放器进程崩溃隔离与诊断页](12_player_process_crash_isolation.md)

`2026-07-29` 在线播放修正已经把 `:player` 进程切换到固定 mpv 输入自带、启用 Mbed TLS 的
`libmp*.so` FFmpeg 命名空间；主进程 FFmpegKit 工具栈保持不变。用户已于
`2026-07-29 14:31 +08:00` 确认 HTTPS MP4 真机播放成功；HLS、更多请求头站点、证书错误路径和本轮
控制层交互仍为 `verification_pending`。

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
- 浏览器顶栏返回与系统 Back 共用 `WebSessionBrowserHost` 的逐级状态机；网页有历史时先后退，
  历史耗尽后才按本次入口退出
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

### 2026-07-28 浏览器视频嗅探抽屉优化

- 媒体候选 UI 只接收可执行视频，不展示音频、`blob:`/MSE、只有 MIME 的 API URL 或媒体分片
- “搜索栏嗅探入口”和“自动悬浮播放”在浏览器设置的“音视频嗅探”组分别控制搜索框视频资源球
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

## 完成定义

- 十一个里程碑均有源码、文档、自动检查和 Debug APK 证据；提交、推送和远端 SHA 仅在另行授权时属于完成证据
- 所有已展示入口都有真实实现；仍受硬依赖限制的入口不伪造成功状态
- 人工和 AI 在普通与无痕窗口中共享唯一 Browser Runtime，并能从工具结果确认窗口 Profile
- 视频 `ACTION_VIEW` 不再进入 AI 附件链路
- 浏览器到播放器的全屏、悬浮、返回转场不刷新、重载或修改当前网页
- 用户醒来后只需安装最后一个 Debug APK，按真机清单完成现场验收
