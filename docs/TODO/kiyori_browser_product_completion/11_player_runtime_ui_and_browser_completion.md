# 播放器运行时、界面与浏览器播放收口

## 本轮定位

阶段 8 与阶段 9 已经建立唯一 `PlayerSession`、统一 FFmpeg/libmpv 栈、媒体 Intent、浏览器 candidate
和同会话悬浮/全屏转挂。本轮不推翻这些所有权，而是针对真实运行时反馈和直接源码对照完成第二次收口：

- 根治 `java.lang.NoClassDefFoundError: is.xyz.mpv.MPVLib`
- 把播放器源文件拆成清晰的 session、mpv engine、媒体解析、Surface 和 Compose UI 职责
- 让全屏播放器的控件层级、尺寸和位置对齐 `kiyori-android@24a2dfa9`
- 扩展只有 `PlayerSession` 会真实消费的播放器设置
- 补齐浏览器候选数字圆圈、自动小窗和网络日志在线播放

本轮仍只允许本地修改、测试与 Debug APK。提交、推送、发布、安装设备、ADB、MuMu、Release 和部署均不在
授权范围内。

## 2026-07-28 全屏播放器 1:1 界面复刻计划

用户确认 Kiyori 始终未发布。本轮直接替换当前 Compose 控制层，不保留旧 Material 图标、旧进度条或并行
播放器界面；唯一 `PlayerSession`、mpv core、Surface owner、浏览器下载 owner 和三态 presentation 契约保持不变。

视觉权威依次为用户提供的 `2800x1260` 横屏截图、`1260x2800` 竖屏截图、`kiyori-android@24a2dfa9` 的
`activity_video_player.xml`、`PlayerControlsManager`、`PlayerDialogManager` 和对应 drawable。不得用 Material
图标或重新设计的间距替代原素材；本轮用户明确指定重新选择的字幕、弹幕、音轨和画面模式四个顶部图标统一使用
`24dp` viewport、`2dp` 圆角线帽的无填充描边 Vector，其余图标不得改动。

本轮按以下单一切片实施：

1. 复制截图实际使用的返回、字幕、弹幕、比例、投屏、更多、播放控制、倍速、截图、锁定、下载、亮度和音量原始 drawable
2. 按旧版 `dp`、`sp`、渐变、颜色和相对位置重建顶部、底部、右侧操作栏、双侧解锁、加载与手势反馈
3. 用 3dp 轨道、12dp 蓝紫色滑块和旧版时间位置替换 Material Slider；上一项与下一项保留旧版禁用视觉
4. 恢复字幕、画面比例、倍速和更多选项弹窗的旧版浅紫背景、8dp 圆角、44dp 选项行和固定高度滚动提示
5. 新增有界播放器日志缓冲；mpv 初始化、加载、Surface、文件事件和可见错误进入缓冲，更多菜单“查看日志”支持复制、清空和关闭
6. 弹幕、投屏和片头片尾等当前没有真实 owner 的项目只复刻原入口与文字，不创建第二套运行时或伪造成功状态
7. 更新 `CONTEXT.md`、`README.md` 和本文件，运行定向 JVM 测试、formal readiness、`git diff --check` 和 Debug APK 构建

真机上的像素、触摸热区、自动隐藏、旋转、弹窗锚点、手势和 OEM 系统栏仍由最终 APK 安装验收关闭。

### 本地结果

- [DONE] 用户确认 Kiyori 始终未发布；当前 Material 播放器图标和 Material Slider 已彻底移除，没有保留兼容层或平行 UI
- [DONE] 20 个播放器 drawable 与 `kiyori-android@24a2dfa9` 源文件 SHA-256 逐一相等，并全部进入最终 APK resource table
- [DONE] 顶部 70dp、底部 100dp、五个顶部图标、七键播放组、两侧文字按钮、右侧三键、双侧解锁和亮度/音量反馈按固定参考重建
- [DONE] 画面比例恢复“适应屏幕 / 拉伸 / 裁剪”；进度条恢复 3dp 半透明白色轨道、12dp `#667EEA` 滑块和旧版时间位置
- [DONE] 更多菜单恢复旧版文字与固定高度滚动提示；“查看日志”读取最多 600 行播放器专用日志，并支持复制、清空和关闭
- [DONE] `PlayerPolicyTest` 为 `10/10`，零失败、零错误、零跳过；`compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- [DONE] `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 54s`，233 个任务零失败；构建后 `verifyDebugPlayerRuntimePackaging` 通过
- [DONE] 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 11:54:02 +08:00`，大小 `468745015` 字节，SHA-256 `4AE0B814119D458711416B778CA4A88B89049273889FC00AE7FF44C336F487D4`
- [DONE] APK 为 `com.kiyori`、`45`/`0.1.0`、min 26/target 34、Android Debug v2；`zipalign -c -P 16 -v 4` 为 `Verification successful`
- [PENDING] 目标设备上的截图逐像素对照、按钮触摸热区、弹窗锚点、三秒自动隐藏、锁定、旋转、seek/亮度/音量手势、日志复制与真实视频播放仍为 `verification_pending`

### 竖屏布局、顶部入口与 Codec2 修正

- [DONE] 竖屏顶部沿用 legacy `applyPortraitSizing`：返回按钮后固定 `64dp / 11sp / 单行` 标题，右侧网络状态、电量时间和五个按钮分别按 `1.15 / 1 / 1 / 1 / 1 / 1 / 1` 权重占满剩余宽度，图标为 `28dp`、内部 padding 为 `4dp`
- [DONE] 竖屏底部不再叠加左右悬浮文字按钮；“超分 / 弹幕 / 上一项 / 后退 / 播放 / 前进 / 下一项 / 倍速 / 旋转”改为九个等宽单元，普通图标 `32dp`、播放图标 `36dp`
- [DONE] 横屏进度区域恢复 legacy `800dp` 最大宽度，横屏中央七键组、超分/旋转两侧定位与右侧截图/锁定/下载图标不变
- [DONE] 顶部功能入口改为字幕、弹幕、音轨、画面模式四个统一描边图标；音轨弹窗直接选择 `PlayerSession` 的真实 audio track；投屏从顶部移除
- [CORRECTED] 早期更多菜单曾复刻“解码 / 投屏 / 听视频 / 片头片尾 / 自动旋转 / 查看日志”；当前已清理无 owner 项，只保留连接真实 `PlayerSettingsStore` 的自动旋转开关和查看日志
- [DONE] 顶部网速改为 legacy `TrafficStats` 总接收/发送字节差值，电量和 `HH:mm` 时间持续读取 Android 系统状态
- [DONE] `PlayerSession` 把新媒体保存为 pending request，只在 `PlayerSurfaceView` 已成功连接有效 Surface 后执行 `loadfile`；该顺序与 legacy `CustomMPVView.initialize -> loadVideo` 一致，避免 Android 16 Codec2 在 VO 替换期间交换 `AHardwareBuffer` fd 所有权
- [DONE] `compileDebugKotlin` 通过；`PlayerPolicyTest` 为 `11/11`，其中新增门禁证明 pending media 与 attached Surface 同时成立时才允许加载
- [DONE] 最终 formal readiness、`git diff --check` 和 `:app:assembleDebug` 通过；`verifyDebugPlayerRuntimePackaging` 通过，APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `468746121` 字节，SHA-256 `69B8CC545AE55174FF9E13FD9A3749C8CA45F7E0FC0E3BA353EAAE10B2690DF2`，新四个 outline drawable 均进入 resource table；`zipalign -P 16` 与 Android Debug v2 签名验证通过
- [PENDING] vivo Android 16 现场复测：真实嗅探视频、Codec2 崩溃是否消失，以及横竖屏像素、按钮热区、弹窗锚点、三秒自动隐藏、锁定、旋转、seek/亮度/音量手势和日志复制

### 2026-07-29 顶部状态与在线播放诊断日志增强

- [DONE] 字幕、弹幕、音轨和画面模式四个按钮保持横竖屏原有外部尺寸、权重和位置，只把内部 padding
  从横屏 `6dp`、竖屏 `4dp` 分别减为 `4dp`、`2dp`，实际图标统一从 `20dp` 增大到 `24dp`
- [DONE] `PlayerStatusColumn` 删除固定 `32dp` 裁切高度，改为最小高度，并为两行文字明确
  `11sp lineHeight`、`maxLines=1`、`softWrap=false`；横竖屏共用该实现，网速单位和电量下方时间不再被
  字体缩放或窄宽换行裁掉
- [DONE] `MpvPlayerEngine` 注册唯一 `MPVLib.LogObserver`，在 `mpv_initialize` 前设置
  `msg-level=all=v`；独立 `:player` 进程把 runtime 命令、Surface、媒体加载、文件事件、MPV verbose
  和全部命令/运行时错误通过现有有序 AIDL callback 汇入主进程 `PlayerDebugLogBuffer`
- [DONE] 日志缓冲扩大为最多 2,000 条分级时间线，并记录丢弃条数；在线播放诊断保留协议、host、端口和
  path 结构，统一删除查询参数值、request header 值、Cookie、Authorization、标题和私人路径
- [DONE] 查看日志提供“全部 / 警告+错误 / 仅错误”三级过滤，默认全部；界面、复制和导出始终使用当前
  过滤结果，报告附加应用、设备、Android、runtime PID/generation、会话、Surface、解码器、轨道和可见错误
- [DONE] 新增“导出”按钮，文本文件写入 `Download/Kiyori/exports`；保留复制、清空和关闭
- [DONE] `PlayerPolicyTest` 与 `PlayerRuntimeProtocolPolicyTest` 共 `18/18` 通过，播放器 Python
  资源门禁 `6/6` 通过；`compileDebugKotlin`、`compileDebugAndroidTestKotlin`、formal readiness 与
  `git diff --check` 通过
- [DONE] `:app:assembleDebug` 通过，233 个任务零失败，`verifyDebugPlayerRuntimePackaging` 通过；
  APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 12:27:11 +08:00`，
  大小 `463725699` 字节，SHA-256
  `FDF25649812F22F2C4406D5334E5D84DC4117933B165A911BA4EE9DA6662E50F`
- [DONE] APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名和
  `zipalign -c -P 16 4` 验证通过
- [PENDING] 目标设备上的横竖屏双行状态、四图标视觉、在线播放错误完整性、三级过滤、复制和导出路径验收

### 2026-07-29 真机反馈后的日志弹窗重构

- [DONE] 用户 `2026-07-29 12:39 +08:00` 的横屏截图确认顶部两行已经完整显示，但上行 `Bold`、
  下行 `Normal`；现已把网速/单位和电量/时间全部统一为 `10sp`、`11sp lineHeight`、`Bold`
- [DONE] 截图同时确认平台默认 Dialog、等权筛选 Row 和最大 `360dp` 正文组合后超过横屏可用高度，
  “警告+错误”发生换行，底部复制、导出、清空和关闭被挤出窗口
- [DONE] 日志弹窗改为屏幕内 `94%` 宽、`90%` 高且最大 `720dp × 680dp` 的 Surface；标题、分类、
  统计、加权日志正文和底部操作区分别占位，关闭固定在标题栏
- [DONE] 分类改为永不换行的横向滑动单行条：全部、错误、警告及错误、网络与加载、播放控制、
  画面与 Surface、音轨与字幕、运行时与 MPV
- [DONE] 缓冲条目增加稳定 ID、结构化字段、写入时多主题分类和 revision；弹窗以约 `300ms` 节奏刷新，
  使用最新在前的 `LazyColumn`，不会在每条 MPV verbose 到达时重新分类全部 2,000 条记录
- [DONE] 底部固定“清空 / 复制日志 / 导出文件”，清空需要 3 秒内再次确认，导出进行中禁止重复触发；
  复制和导出继续生成当前分类、时间正序、完整上下文且经过脱敏的诊断报告
- [DONE] `PlayerPolicyTest` 与 `PlayerRuntimeProtocolPolicyTest` 共 `20/20` 通过，播放器 Python
  资源门禁 `6/6` 与 `compileDebugKotlin` 通过
- [DONE] `compileDebugAndroidTestKotlin`、formal readiness、隐私反向扫描与 `git diff --check` 通过；
  `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 17s`，233 个任务零失败，
  `verifyDebugPlayerRuntimePackaging` 通过
- [DONE] APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 13:00:54 +08:00`，
  大小 `463725699` 字节，SHA-256
  `652ECA3B6A6DED8EEB16B77AF72C2F28BE6BC49C81701B0FDE3D13DE12C6C17E`；
  `com.kiyori 45 / 0.1.0`、min 26/target 34、Android Debug v2 签名和 16KB ZIP 对齐通过
- [PENDING] 目标设备上的横竖屏尺寸、分类横滑、最新日志刷新、底部按钮可见性、二次清空、复制和导出验收

### 2026-07-29 在线播放成功后的控制层与真实设置收口

- [DONE] 用户 `2026-07-29 14:31 +08:00` 的 vivo V2507A / Android 16 报告确认此前 Debug APK 已
  成功播放在线 MP4：runtime 为 `ACTIVE`，Surface、首帧、播放位置、时长和网速持续更新；当前故障与
  native 在线播放链路无关
- [DONE] 根因定位为 `PlayerGestureLayer` 把高频变化的 `state.positionSeconds` 用作两个
  `pointerInput` key，播放进度更新会取消正在等待 ACTION_UP 的触摸协程
- [DONE] 手势层改为单一稳定 detector；每次手势开始读取最新 state，单击、双击、水平 seek、左侧亮度
  和右侧音量由同一序列判定，播放进度更新不会重启 detector
- [DONE] 控制层自动隐藏开始观察暂停、加载、弹窗、日志、进度拖动和全屏手势；任意真实按钮交互重置
  三秒计时。锁定后只显示左右解锁按钮，按钮自动隐藏后可通过单击视频区域重新显示
- [CORRECTED] 弹幕在没有真实 owner 前改为明确禁用态；更多菜单删除解码、投屏、听视频和片头片尾等
  活跃空动作。当前自动旋转已连接真实设置 owner，并与“查看日志”共同保留
- [CORRECTED] 播放器设置页删除没有 owner 的静态伪设置，当前为六组 24 项真实配置，完整覆盖
  `PlayerSettingsStore` 的播放、交接、在线缓存、字幕和解码渲染字段；GPU Next 与 Vulkan 明确标注
  下次创建播放器内核生效
- [DONE] 浏览器悬浮播放器读取同一 `PlayerSettingsStore.seekStepSeconds`，不再把显示用步长固定为
  `10`
- [DONE] `PlayerPolicyTest 17/17`、`PlayerControlsPolicyTest 2/2` 与
  `KiyoriSettingsPagesTest 8/8` 共 `27/27` 通过；播放器 Python 资源门禁 `6/6` 与
  `:app:compileDebugKotlin` 通过
- [DONE] 更宽播放器回归 `58/58`、播放器 Python 门禁 `6/6`、AndroidTest Kotlin 编译、
  formal readiness、`git diff --check` 和最终 Debug APK 构建均通过；差异检查只有工作树既有的
  CRLF -> LF 提示，没有 whitespace error
- [DONE] 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 15:22:40 +08:00`，大小 `474822245` 字节，SHA-256
  `96726F63381FA7AEDF4AE212E162D3999BF057C2A3AB9EE1C0B6CA95B578788F`；
  `com.kiyori 45 / 0.1.0`、min 26/target 34、`arm64-v8a`、Android Debug v2 签名和
  16 KiB ZIP 对齐均通过
- [PENDING] 目标设备复测本地/在线视频隐藏后单击恢复、双击、滑动、按钮热区、弹窗计时、锁定、
  横竖屏和设置实时/下次启动生效语义

### vivo Android 16 WebView 与悬浮视频合成隔离

- [DONE] 用户在 `2026-07-28 12:51:30 +08:00` 提供的 SIGABRT 不再是 Codec2 binder 栈：崩溃线程为 `RenderThread`，`libwebviewchromium.so` 随后进入 `drawVk` 和 HWUI `SkiaVulkanPipeline`，Abort 仍为 `fdsan` fd ownership exchange
- [DONE] 中间版曾把悬浮画面改成 `PlayerTextureView`。用户在 `2026-07-28 13:03:09 +08:00` 的再次复测中得到 `RenderThread -> vendor Mali -> VulkanManager::fenceWait -> ASurfaceTexture_dequeueBuffer`，Abort 为 `fdsan: attempted to close file descriptor`，证明 TextureView 自身仍进入 vivo Android 16 的 SurfaceTexture/Vulkan fd 故障路径；该实现及源码已删除
- [DONE] AOSP [OpenGLRenderer configuration](https://source.android.com/docs/core/graphics/renderer) 将 HWUI renderer 属性定义为设备产品配置；应用不使用隐藏 API、系统属性或设备修改来强制本进程切换 renderer
- [DONE] 浏览器悬浮恢复唯一 `PlayerSurfaceView(mediaOverlay=true)`；`WebSessionBrowserHost` 在悬浮画面启用前保存当前唯一 WebView 的原始 `layerType` 并切换为 `View.LAYER_TYPE_SOFTWARE`，从合成侧移除 Chromium Vulkan `drawVk`
- [DONE] 活动标签切换先恢复旧 WebView，再对新 WebView 应用当前合成模式；进入全屏、关闭播放器、关闭所属 browser session 和销毁 Host 均同步恢复原始 layer type，全屏返回悬浮时重新启用隔离
- [DONE] 本轮 `compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；`PlayerPolicyTest` 为 `11/11`，零失败、零错误、零跳过
- [DONE] `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 46s`，233 个任务零失败，`verifyDebugPlayerRuntimePackaging` 通过；APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 13:25:38 +08:00`，大小 `468746121` 字节，SHA-256 `7AEC2022EE86B7B2DD53DB90997B04EF1165494C991509238AAC29089CE9B142`，包名/版本为 `com.kiyori / 45 / 0.1.0`，Android Debug v2 签名与 `zipalign -P 16` 验证通过
- [PENDING] vivo Android 16 同一网页现场复测：触发自动/手动悬浮播放，确认 RenderThread `fdsan` SIGABRT 消失，再验证标签切换、悬浮到全屏、全屏返回和关闭播放器后的网页渲染

### 旧版悬浮播放器复刻与 Surface 初始化时序收口

- [DONE] `kiyori-android@24a2dfa9` 的 `BrowserScreen` 明确把悬浮播放器放在浏览器内容区 `BottomCenter`：宽度为内容区全宽，高度固定为宽度的 `9/16`，Y 偏移范围为 `-(内容区高度 - 播放器高度)..0`，只注册纵向拖动
- [DONE] 当前自由缩放小窗已彻底删除：不再保留 `12dp` 边距、`66%` 基础宽度、`180..360dp` 限制、`8dp` 圆角、双指缩放或 X 轴位移；悬浮画面固定全宽、方角和 `16:9`，上下边界分别精确接触浏览器顶栏下边与底栏上边
- [DONE] 悬浮播放器控制层按旧版源码重建：顶部返回、两行标题、实时网速、电量时间、字幕/弹幕/音轨/画面模式/更多；右侧截图、锁定、下载；底部时间、3dp 进度、弹幕/上一项/后退/播放/前进/下一项/倍速、超分与旋转；锁定后使用左右双解锁按钮
- [DONE] 控制层继续消费唯一 `PlayerSession`：播放暂停、进度跳转、前后跳、倍速、截图、Anime4K 和浏览器 candidate 下载都调用现有 owner；功能入口进入同一全屏 presentation，不创建另一套播放器状态
- [DONE] 用户在 `2026-07-28 14:28:20 +08:00` 提供的新 tombstone 仍回到 `binder` 线程，Abort 为 `fdsan: failed to exchange ownership of file descriptor`；此前软件解码与 WebView 软件层隔离均未改变该现场，因此它们不是根因
- [CORRECTED] Kiyori 始终未发布，播放器恢复原有 `AUTOMATIC` 解码基线并使用原 `hardware_decoding` 设置键。`2026-07-28 19:18:21 +08:00` 的必现闪退证明“等待 Surface 后创建 mpv，并在 `mpv_initialize` 前绑定 `wid`”违反实际打包 mpvlibAndroid 的生命周期；现已改为先初始化唯一 mpv，Surface 到达后再 attach，完成 lease 的 native attach 后才允许 `loadfile`
- [DONE] 固定全宽悬浮层只更新 SurfaceControl 的纵向位置，不再通过缩放持续改变 `PlayerSurfaceView` 尺寸；保留唯一 media-overlay SurfaceView 以保证视频位于实时 WebView 上方，移除未能解决崩溃的 WebView 软件层隔离
- [DONE] `:app:compileDebugKotlin` 通过；`PlayerPolicyTest` 为 `12/12`，零失败、零错误、零跳过，覆盖自动解码基线、pending media 与 attached Surface 的加载门禁
- [DONE] formal readiness、播放器资源测试 `5/5` 与 `git diff --check` 通过；源码中 `LAYER_TYPE_SOFTWARE`、`setFloatingPlayerCompositionActive`、`hardware_decoding_policy_v2` 均为零引用
- [DONE] `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 12s`，233 个任务零失败，`verifyDebugPlayerRuntimePackaging` 通过；APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 14:57:07 +08:00`，大小 `468746121` 字节，SHA-256 `DBF9E838C7FEC8EE868B968371C7775D4B9D89E1698098E694F207858F20769A`
- [DONE] 最终 APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名通过，`zipalign -c -P 16 -v 4` 为 `Verification successful`
- [PENDING] 目标 vivo Android 16 安装本轮最终 APK 后确认日志中的 `decoder=automatic`，并复测自动/手动悬浮、上下边界、按钮点击、横向 seek、标签切换、悬浮/全屏往返及 `binder`/RenderThread `fdsan`；真机通过前继续保持 `verification_pending`

### 方案 A：显式 Surface lease 与 Activity 交接

- [DONE] `PlayerSessionState.surfaceLease` 取代单一 `surfaceOwner` 字符串，明确记录 floating/fullscreen role、owner-instance token、单调 generation、current owner、pending target、native attach/detach 状态、transfer target 和一次性 Activity launch/finish request
- [DONE] `PlayerSurfaceView` 在每次真实 `surfaceCreated` 时登记 lease generation；`surfaceChanged` 与 `surfaceDestroyed` 始终携带 role、token、generation 和 Surface identity，过期 View 回调不会更新或释放当前 owner
- [DONE] floating -> fullscreen 先把 presentation 切为 fullscreen，使 Browser Home 移除旧 floating Surface；只有旧 View 报告 `surfaceDestroyed` 且 `PlayerSession` 同步完成 native detach 后，状态机才发出一次 fullscreen Activity launch request
- [DONE] fullscreen -> floating 由 session 发出一次 finish request；`PlayerActivity` 不再按 `presentation == FULLSCREEN_PLAYER` 无条件关闭 session，只有 fullscreen Surface 释放并完成 native detach 后，presentation 才切回 floating 并允许 Browser Home 挂载下一代 Surface
- [DONE] config change 或新 View 提前创建时，新 Surface 只登记为 pending；旧 owner 未释放前 `activatePendingPlayerSurface` 会拒绝 attach。普通 Surface 重建也会分配新 generation，不复用旧 lease
- [DONE] closing 会撤销 pending target 和 Activity request，精确执行一次 native detach 后才销毁唯一 mpv core 与媒体描述符；关闭后的迟到注册、attach、change 和 destroy 只被拒绝或忽略
- [DONE] `MpvPlayerEngine.attachSurface()` 已删除内部隐式 `detachSurface()`，attach 前断言 engine 没有 Surface；`destroy()` 同样断言 detach 已由 session 完成。转挂路径不调用 `loadfile`、`loadUrl`、`reload`、重新嗅探或网页媒体 JavaScript
- [DONE] `PlayerPolicyTest 12/12` 与 `PlayerSurfaceLeasePolicyTest 8/8` 通过，覆盖首次 Surface 加载门禁、双向转挂、提前到达、过期 token/generation、config replacement、普通 Surface 重建、closing 和 handoff 不增加 `loadGeneration`
- [DONE] `:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过；`git diff --check` 只有已有工作树的 CRLF -> LF 提示，没有 whitespace error
- [DONE] 独立里程碑 `:app:assembleDebug` 通过，233 个任务中 28 executed / 205 up-to-date，`verifyDebugPlayerRuntimePackaging` 通过
- [DONE] 里程碑 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 17:57:39 +08:00`，大小 `468746121` 字节，SHA-256 `08A443EAB2ED4FE1F2ACE99E5107AF791EB064EF0C82D53EAA2C413855764BAE`；`com.kiyori / 45 / 0.1.0 / min 26 / target 34`、Android Debug v2 和 16 KB ZIP alignment 均通过
- [PENDING] 以上是本地代码、测试、构建和 APK 证据；vivo Android 16 同一网页的 floating/fullscreen 往返、页面不刷新、无媒体重载及 `fdsan` 崩溃是否消失仍需真机确认，终态保持 `verification_pending`

### 方案 A 终局综合门禁

- [DONE] 浏览器 `BrowserBackgroundAnchorPolicyTest 2/2`、`BrowserTabDiscoveryFormatterTest 2/2`、`MainActivityBrowserActionTest 1/1`、`KiyoriShellStateTest 41/41` 与播放器 `PlayerPolicyTest 12/12`、`PlayerSurfaceLeasePolicyTest 8/8` 共 `66/66`，零失败、零错误、零跳过
- [DONE] `:app:compileDebugKotlin` 与 formal readiness 通过
- [DONE] 静态反向检查确认旧 `BrowserOverlayWindowPolicy`、expanded overlay 符号、overlay `MATCH_PARENT`、系统 overlay `BrowserContent`/`PlayerSurfaceView`、`TextureView`、`LAYER_TYPE_SOFTWARE`、第二 Browser Runtime、第二 `PlayerSession`、第二 `MPVLib.create` 和 `MpvPlayerEngine.attachSurface()` 内隐式 detach 均不存在
- [DONE] `git diff --check` 通过；输出只包含已有工作树文件的 CRLF -> LF 提示，没有 whitespace error
- [DONE] 综合代码构建的 `:app:assembleDebug --no-daemon --console=plain` 通过，233 个任务中 27 executed / 206 up-to-date；文档写回后的收尾构建再次通过，233 个任务中 24 executed / 209 up-to-date。两次 `verifyDebugPlayerRuntimePackaging` 均通过，最终 APK 未变化
- [DONE] 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 18:15:33 +08:00`，大小 `468746121` 字节，SHA-256 `689A09C1DF4E23608A4E9E07A7B0E95645F9D5914E3CFB29BA0DFD1A34C2DBA0`；`com.kiyori / 45 / 0.1.0 / min 26 / target 34`、Android Debug v2 和 16 KB ZIP alignment 通过
- [PENDING] 本轮未安装 APK、未使用 ADB/MuMu、未操作目标设备。只有用户在 vivo Android 16 上完成同一网页、同一视频的人工/AI 窗口共用、后台 anchor、indicator、floating/fullscreen 双向转挂和崩溃复测后，才能关闭 `verification_pending`

### 2026-07-28 播放入口必现闪退纠正

- [DONE] 新 tombstone 时间为 `2026-07-28 19:18:21 +08:00`，应用 uptime 仅 `12s`，在首次视频渲染的 `Surface.dequeueBuffer -> HWUI Vulkan CanvasContext::draw` 路径触发 `fdsan`，没有 Browser presentation 退出前提
- [DONE] 精确检查当前 AAR、mpvlibAndroid `168e0a5e` 和 legacy Kiyori：两份权威实现都先 `MPVLib.init()`，随后只在真实 Surface 回调中 attach；当前代码此前执行了相反顺序
- [DONE] `MpvPlayerEngine.initialize()` 不再接收或 attach Surface，也不在初始化前设置 `force-window=yes`
- [DONE] `PlayerSession.open()` 先创建并初始化唯一 engine，再保存 pending media；当前 lease 的有效 Surface 到达后执行 attach，随后才消费一次 pending `loadfile`
- [DONE] Surface role、token、generation、identity、双向 transfer phase 和一次性 Activity request 保持不变；未增加第二播放器、媒体重载或 URL 改写
- [DONE] `ci.test.test_player_assets` 新增启动顺序门禁，明确拒绝 initialize 内的 `MPVLib.attachSurface` 和 `force-window=yes`
- [DONE] `ci.test.test_player_assets 6/6`、`PlayerPolicyTest 12/12`、
  `PlayerSurfaceLeasePolicyTest 8/8`、Kotlin 编译、formal readiness、静态反向检查与
  `git diff --check` 均通过
- [DONE] `:app:assembleDebug` 通过，233 个任务中 27 executed / 206 up-to-date；
  `verifyDebugPlayerRuntimePackaging` 通过
- [DONE] APK 时间 `2026-07-28 19:39:17 +08:00`，大小 `455956807` 字节，SHA-256
  `91634B63C7D5D6EDC5FF20FBBE76663F7850D8A446D52874B556353E1174FB9E`；
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34`、Android Debug v2 与 16 KB ZIP 对齐通过
- [PENDING] 原 vivo Android 16 必须复测任意网络视频、browser floating、fullscreen 及双向转挂；
  本地证据不替代设备 `fdsan` 验收

## 本地结果

- [DONE] `shouldInterceptRequest` 候选采集使用 volatile WebSession UA 快照，用户提供的
  `ThreadPoolForeg` / `WebView.getSettings()` 崩溃路径已移除并有静态门禁
- [DONE] `MpvPlayerEngine` 是唯一 MPV binding import owner，全部 JNI 入口把 `LinkageError` 转为可见错误；
  自然结束使用 `eof-reached`，不会把 replace/stop 的 `END_FILE` 误判成播完
- [DONE] 播放器默认横屏、浏览器悬浮后台行为、Android 26-28 截图保存、累计亮度/音量手势和候选精确
  下载/网络日志播放已收口
- [DONE] 全量 JVM `555/555`、本轮 native Python `18/18`、formal readiness、`git diff --check` 与 Debug 构建通过
- [DONE] 用户真机报告的 Clang 21 `libmpv.so` 与 Clang 18 C++ runtime 符号不匹配已修正；最终 APK 中
  121 个唯一 C++ 符号缺失 0，现场两个 `__from_chars_floating_point` 导出均存在
- [DONE] 最终 APK 时间 `2026-07-28 10:59:29 +08:00`，大小 `468740360`，SHA-256
  `44642BF9A4EE713D2566B6FEF2E77865067E7CB2D030F705D139075003B1ED03`；native/许可证证据见阶段 10 与
  `PLAYER_NATIVE_STACK.md`
- [PENDING] 安装该新 APK 后的真实播放复测、Anime4K 性能/温度、手势、截图、后台、自动小窗、
  悬浮/全屏往返和站点兼容
  仍为 `verification_pending`

## 固定参考与差异

### `kiyori-android@24a2dfa9`

采用：

- `activity_video_player.xml` 的顶部信息栏、底部进度和中央按钮组、两侧手势反馈、锁定与超分入口
- `BrowserTopBar` 内 28dp 橙色视频数量圆圈和 `99+` 上限
- `BrowserFloatingSniffPolicy` 的“设置开启、候选稳定后自动小窗、关闭后本页不重复启动”语义
- `VideoSnifferManager` 与 `UrlDetector` 对 HLS、DASH、普通视频和 DOM 来源分层展示的思路

拒绝：

- `MpvSeamlessHandoff` 在多个 `PlaybackEngine` 之间传递 engine 的临时 owner 模型
- handoff 失败后重新 `loadRemote`、代理播放、URL resolver 和其他重载路径
- 浏览器小窗前后调用 JavaScript pause/resume 网页媒体
- MIME、探测结果或解析器输出替换原始媒体 URL

### `mpv-android-anime4k@32f5f169`

采用：

- `MPVLib.create -> setOptionString -> init` 的初始化顺序
- 精确 seek、缓存上限、字幕样式和实时 `glsl-shaders` 参数
- Anime4K shader 延迟复制、模式链和失败可见语义

拒绝：

- `mediacodec,mediacodec-copy,no` 形式的隐式解码降级链
- `tls-verify=no`
- 独立媒体库、播放历史、下载数据库和第二播放器导航

### `hikerView@5de88090`

采用：

- 浏览器候选和下载必须保留 Range 证据、原始请求 headers、HLS manifest 与普通文件下载身份；
  player runtime 不重放捕获的静态 Range，而由 mpv/FFmpeg 按当前 open/seek 偏移生成
- 网络日志和下载动作不应只保存裸 URL

拒绝：

- 本地 HTTP 代理缓存、URL 重写、M3U8 二次解析和第二套缓存任务 owner
- 把接口 MIME 或响应猜测直接改写为可播放文件

## 运行时修复设计

`MPVLib` 薄 AAR 必须成为显式 Gradle 依赖，不再依赖 `app/libs/*.aar` 通配符的隐式发现。构建前检查不仅核对
AAR member 和 native 哈希，还必须打开 `classes.jar` 并确认：

- `is/xyz/mpv/MPVLib.class`
- `is/xyz/mpv/MPVLib$EventObserver.class`
- `is/xyz/mpv/MPVLib$LogObserver.class`
- `is/xyz/mpv/MPVNode.class`
- `is/xyz/mpv/Utils.class`

Debug APK 构建后必须扫描全部 `classes*.dex`，确认 `Lis/xyz/mpv/MPVLib;`、`Lis/xyz/mpv/MPVNode;` 和
Kiyori 的 mpv engine descriptor 同时存在。该门禁与 `libmpv.so`、`libplayer.so`、`DT_NEEDED`、ABI、符号和
16 KB `PT_LOAD` 审计并列，不能用编译成功代替。

真机在 `2026-07-28 10:06` 暴露了另一个独立装载错误：Clang 21 构建的 `libmpv.so` 引用 float/double
两个 `std::__ndk1::__from_chars_floating_point`，而先前由 Clang 18 FFmpegKit 输入提供的
`libc++_shared.so` 不导出它们。修正后的 mpv AAR 必须保留固定 mpv 输入中的同工具链 C++ runtime；
FFmpegKit 输入确定性转换为只含 arm64 Java/资源/许可证与九个正常名称 native 库的 AAR。
`2026-07-29` 在线播放诊断又确认该 FFmpegKit `libavformat.so` 没有 TLS 后端，因此固定 mpv 输入自带、
启用 Mbed TLS 的七个 FFmpeg ELF 必须通过等长 SONAME / `DT_NEEDED` 改名进入 `libmp*.so` 播放器
命名空间。构建前和最终 APK 门禁必须同时确认唯一 C++ runtime、两个 C++ 符号、旧 FFmpeg 依赖名清零、
Mbed TLS 构建证据与 16 KB 对齐。

`PlayerSession` 不再直接实现 `MPVLib.EventObserver`。唯一 session 私有持有一个 `MpvPlayerEngine`，所有
`MPVLib` 类型只出现在 engine 文件中。engine 创建时若发生 `ClassNotFoundException`、
`NoClassDefFoundError`、`UnsatisfiedLinkError` 或其他 `LinkageError`，session 进入包含具体原因的错误状态，
不得崩溃 Activity，也不得创建替代播放器或重试另一个内核。正确 APK 中该路径必须由 DEX/native 门禁证明
不会因为缺少打包输入而触发。

### 2026-07-29 在线播放补充结果

- [DONE] mpv AAR 输出改为 `50543589` 字节、SHA-256
  `FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C`
- [DONE] 最终 APK 同时包含正常 FFmpegKit 九库与播放器七个 `libmp*.so`；53 个 native basename
  无重复，52 个 ELF 全部 `PT_LOAD >= 0x4000`
- [DONE] `libmpv.so` / `libplayer.so` 对旧 FFmpeg 名称的 `DT_NEEDED` 为零，256 个版本化 FFmpeg
  符号去重后缺失 0
- [DONE] `libmpformat.so` 保留 `--enable-mbedtls`、Mbed TLS 3.6.6 与 HTTPS 证据；engine 使用固定
  CA 包、严格证书校验并禁用未分发 ytdl
- [DONE] 用户 `2026-07-29 14:31 +08:00` 的报告已确认真机 HTTPS MP4 播放成功、首帧和网速正常；
  HLS、更多带请求头站点、长时 seek、证书错误分类以及复制/导出路径继续待覆盖

## 唯一会话与文件职责

```text
core/player/
	PlayerModels.kt
	PlayerSettingsStore.kt
	PlayerSession.kt
	MpvPlayerEngine.kt
	PlayerMediaResolver.kt
	Anime4KShaderManager.kt
ui/features/player/
	PlayerActivity.kt
	PlayerScreen.kt
	PlayerControls.kt
	PlayerGestureLayer.kt
	PlayerSurfaceView.kt
```

- `PlayerSession`：唯一状态机、request、文件描述符、设置快照、presentation 和 surface owner
- `MpvPlayerEngine`：唯一 `MPVLib` observer、core 初始化、mpv property/command 和 Surface 调用
- `PlayerMediaResolver`：原样解析 `content://`、`file://`、裸本地路径和允许的网络 scheme
- `PlayerActivity`：Intent、系统栏、生命周期和返回语义
- `PlayerScreen`、`PlayerControls`、`PlayerGestureLayer`：只消费 state 和 session command

任何拆分都不能增加第二个 core、第二份状态或可绕过 session 的 UI 直写入口。

## 播放器界面

以旧版布局为视觉权威，首期真实消费者包括：

- 顶部：返回、横屏两行/竖屏单行标题、实时网络速度、电量与时间、字幕、弹幕、音轨、画面模式、更多
- 横屏底部：时间和当前章节位于最大 `800dp` 的进度条上方；中央依次为弹幕、上一项、后退、
  播放/暂停、前进、下一项、倍速，两侧为 Anime4K 与旋转
- 竖屏底部：Anime4K、七个主控与旋转使用九个等宽单元，不再互相覆盖；上一项和下一项只在
  真实队列存在相邻媒体时启用
- 右侧：截图、锁定、下载；只有来源为浏览器 candidate 时下载可用
- 中央与两侧：加载、seek、亮度、音量、双击前后跳和锁定反馈

旧版弹幕和投屏依赖当前项目没有的完整真实 owner；弹幕保留顶部和底部位置但使用明确禁用态，投屏等
没有 owner 的更多菜单项已经删除，不创建第二套运行时或伪造成功状态。更多菜单只提供连接
`PlayerSettingsStore.followGravityRotation` 的自动旋转开关和真实播放日志。Anime4K 入口严格按
`mpv-android-anime4k@32f5f169` 顺序显示关、A、B、C、A+、B+、C+，使用 Balanced/M shader 链并
立即应用；资产、缓存和 MPV 属性均有校验证据。进度条按设置绘制 MPV 章节节点，显示当前章节，并在
拖动时展示由当前 `:player` runtime 提取的画面预览。按钮、锚定菜单、加载/错误卡片和手势提示共用
同一深色现代视觉，同时保留现有播放器专用图标与唯一控制层。

## 真实设置

`PlayerSettingsStore` 新增以下字段，全部由唯一 session 或 UI command 消费：

- 解码器预设：直接映射 mpv `fast/default/high-quality/gpu-hq/low-latency/sw-fast` profile
- GPU Next 与 Vulkan：在唯一 mpv core 下次创建时分别约束 `vo=gpu-next` 与
  `gpu-context=androidvk`
- 记忆播放倍速：关闭时新媒体使用默认倍速，开启时使用并更新上次实际选择的倍速
- 记忆超分模式：关闭时新媒体从 `OFF` 开始；开启后可独立选择新视频使用的默认 Anime4K 模式
- 音量增强：约束 mpv 软件音量 `150%` 与 `volume-max=300%`
- 精确 seek：约束 `hr-seek`、`hr-seek-framedrop` 和 seek command mode
- 双击手势：可选择任意位置暂停/播放或左右半屏快退/快进；左右跳转使用独立秒数
- 长按加速：开启后按住画面从当前倍速临时提升到下一合法档，松手或手势取消恢复原速，不写入倍速记忆
- 按钮快进/快退时长：只约束播放器按钮
- 章节进度条：消费 MPV `chapter-list`，绘制节点并显示当前章节
- 进度条缩略图：经唯一 runtime 调用 `grabThumbnailFast`，使用单线程、时间分桶和最新请求覆盖
- 自动播放下一集：只在真实队列存在下一项时切换；本地队列使用同目录、同系列名和自然序
- 队列播完行为：最后一项结束后停留、关闭或由 `PlayerSession` 从头播放当前项；MPV
  `loop-file` 固定为 `no`
- 网络缓存：约束 `demuxer-max-bytes`、`demuxer-max-back-bytes` 与 `cache-secs`
- 字幕缩放：约束 mpv `sub-scale`
- 截图保存位置：默认按文件下载器真实目标解析，也可直接写入独立 SAF 目录
- 视频下载位置：默认跟随文件下载器；独立 SAF 目录冻结到当前 candidate 请求并使用唯一内置下载器，
  普通文件写入所选目录，M3U8 离线包继续保留在应用目录
- 跟随重力自动旋转：开启时使用 `FULL_SENSOR` 并禁用手动旋转，关闭后恢复默认横屏与手动切换

设置页继续复用 `KiyoriCollapsingSettingsPage` 与 `KiyoriSettingsGroupCard`。Kiyori 尚未发布，原
hikerView 参考页中没有当前 owner 的静态伪设置已经删除；当前按“播放与连播、手势与进度、画面与
超分、音频与字幕、保存与下载、窗口与在线”使用 `4/7/5/2/2/4` 六组 24 项，只展示上列真实
`PlayerSettingsStore` 字段。选择页显示当前值和参数说明，条件项使用明确禁用态，GPU Next 与 Vulkan
明确标注下次创建唯一 mpv core 时生效。

## 浏览器收口

- 顶部搜索框右侧在候选数大于零时显示 28dp 橙色圆圈，点击直接打开当前 WebSession 的候选抽屉
- 自动小窗只选择 `directPlaybackReady=true` 的原始 URL；优先 DOM play/currentSrc、HLS/DASH 和普通视频文件
- 候选变化先短暂去抖再选择，同一页面关闭后不重复启动；全屏和悬浮交接期间不选择新媒体
- 自动小窗不执行网页 JavaScript，不暂停网页媒体，不主动 probe，不解析或替换 URL
- 网络日志只有在条目 URL 与当前 direct candidate 精确相同时显示“播放”；播放和下载都复用 candidate ID，
  从而保留 headers、Cookie scope 和原始 URL
- MIME-only 接口和 blob/MSE 的播放、下载保持禁用
- `shouldInterceptRequest` 只读取 `WebResourceRequest`、CookieManager 和 volatile WebSession 快照，严禁从该
  Chromium 工作线程调用 `WebView.settings`；用户提供的 `ThreadPoolForeg` 崩溃由此门禁覆盖

## 验收

1. AAR class、显式依赖和最终 DEX descriptor 门禁通过
2. `PlayerSession` 状态机、设置映射、seek/结束/画面比例和 engine 事件桥测试通过
3. 顶栏 candidate badge、自动选择、关闭抑制、网络日志 candidate 映射测试通过
4. 源码静态门禁确认 WebView 后台线程零访问、无网页媒体 JavaScript、无播放器回退、无 native 选取规则
5. formal readiness、`git diff --check` 和 `:app:assembleDebug` 通过
6. 最终 APK 重新核对 DEX、包信息、签名、zipalign、ABI、native 清单、动态依赖、符号和 16 KB 对齐
7. 真机解码、Anime4K 性能与温度、截图、手势、自动小窗、悬浮/全屏转场、后台行为和站点兼容保持
   `verification_pending`
