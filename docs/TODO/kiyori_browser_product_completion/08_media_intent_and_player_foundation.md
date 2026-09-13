# 媒体 Intent 与播放器基础

## 已确认根因

`MainActivity` 的 `ACTION_VIEW` wildcard filter 接收 `*/*`，非 HTTP/HTTPS URI 一律写入
`pendingSharedFileUris`，随后进入 `SharedFileHandler`。因此系统把视频交给 Kiyori 时，视频会被解释为
AI 附件。旧 Kiyori 的 `MpvSeamlessHandoff.Entry` 只临时转交 `PlaybackEngine`；消费失败或生命周期交错时，
普通 autoplay 路径仍可能再次执行 `loadRemote`。当前实现不得沿用该模型。

## 固定 native 方案

本阶段只接受一个播放器 owner，并允许应用工具与播放器在不同进程使用两个明确隔离的 FFmpeg
native 命名空间：

| owner | 固定来源 | 输入 SHA-256 | 进入 arm64 APK 的库 |
| --- | --- | --- | --- |
| 主进程 FFmpegKit 工具 | `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`，源码 tag `v8.1.7` / `62b07bf097baf26b416c815aea514e05c9ad6d63` | `C3CBC81D498175FD2AA69EE2DFE7DAFBF519052A96283C2568FE5B3B16618456` | 正常名称的七个 `libav*` / `libsw*`、`libffmpegkit.so`、`libffmpegkit_abidetect.so` |
| `:player` libmpv + HTTPS FFmpeg + C++ runtime | `Riteshp2001/mpvlibAndroid` release/tag `2026-06-25`，commit `168e0a5e43b37c85509050cddcb5eaddc2e313c0` | `CA0D1C60DDFE5BAD46C369D0D0BF6C2D1A8BB5EC5A3AB916C33F892D0263A75E` | `libmpv.so`、`libplayer.so`、唯一 `libc++_shared.so`、必要 assets，以及启用 Mbed TLS 的七个等长改名 `libmp*.so` FFmpeg ELF |

- 两个上游制品均使用 FFmpeg `n8.1.2`，但编译能力不同：FFmpegKit 输入没有 TLS 后端，不能再作为
  `libmpv.so` 的在线播放协议 owner；mpv 输入启用静态 Mbed TLS 3.6.6。
- 准备脚本只对固定 mpv 输入中的 ELF 动态字符串做等长改名：`libav*.so` / `libsw*.so` 映射为
  `libmp*.so`，并同步重写 `libmpv.so`、`libplayer.so` 和七个 FFmpeg ELF 的 SONAME /
  `DT_NEEDED`。不得用 `pickFirst`、同名覆盖或运行时代理代替隔离。
- 旧 `app/libs/ffmpeg-kit-local.aar`、旧 `mpv-android-lib-v0.1.10.aar` 和手工
  `app/src/main/jniLibs/**/libc++_shared.so` 不再是 owner，依赖准备阶段必须删除。
- 两个生成 AAR 都是固定输入的可复现结构变换，不是 Gradle packaging 选取。
- 输入、输出、ABI、依赖符号、APK native 清单及每个 arm64 ELF 的 `PT_LOAD >= 0x4000` 证据进入
  `docs/doc-src/dev-core/PLAYER_NATIVE_STACK.md`；编译成功不能代替该审计。
- 根 `LICENSE` 保存 GPLv3 正文，根 `NOTICE` 保存 GPL-3.0-or-later 分发边界、mpv、FFmpegKit、
  FFmpeg 和 Anime4K 的来源与对应源码义务。当前授权只允许本地开发，不允许公开发布。

## 唯一 PlayerSession

进程内只有一个 `PlayerSession`，并且它直接拥有唯一 MPV core。Activity、Compose、浏览器和 AI
都只能通过 session 命令和状态流操作，不持有第二个 engine。

`PlayerSession` 必须持有：

- 不可变 request/session ID、原始 URI、原始 HTTP headers、Cookie scope、标题、来源 WebSession ID
- 播放位置、时长、暂停、加载/缓冲/错误、默认和当前倍速
- 音轨、字幕轨及当前选择
- 硬件解码参数、Anime4K shader 模式和实际传给 mpv 的 shader 路径
- `BROWSER_ONLY`、`FLOATING_PLAYER`、`FULLSCREEN_PLAYER` presentation 状态
- 当前 surface owner token；任何时刻最多一个 owner 可以 attach
- `content://` 所需的仍存活 `ParcelFileDescriptor`，直到该 session 关闭或显式更换媒体

以下是不可破坏的不变量：

1. `loadfile` 只发生在创建新 request ID 或用户显式更换媒体时。
2. Surface attach/detach、Activity 重建、方向变化、悬浮与全屏切换不得执行 `loadfile`。
3. owner token 不匹配的 detach 不得拆掉新 owner 的 Surface。
4. 新媒体加载前显式清理上一媒体的 headers、文件描述符、音轨和字幕状态。
5. `close` 才销毁 core；展示层离开只 detach Surface。

## Intent 与 URI

- 新建独立 `PlayerActivity`，显式接收 `video/*`、`application/vnd.apple.mpegurl`、
  `application/x-mpegURL` 与 `application/dash+xml`。
- `MainActivity` 文件打开 filter 收窄到实际 AI 附件类型；`ACTION_SEND` 与
  `ACTION_SEND_MULTIPLE` 继续属于聊天分享。
- HTTP/HTTPS 普通链接继续进入 Browser Runtime；只有命中播放器 MIME 的系统 Intent 或用户明确点击
  candidate 的播放动作进入播放器。
- `content://` 使用授予的 URI 权限并在 session 内保留只读文件描述符；`file://` 使用原始本地路径；
  网络 URI 原样传给 mpv。不得复制到聊天附件目录。
- `onNewIntent` 生成新 request ID 并更新同一 session；配置重建继续使用 Intent 中已有 request ID，
  不创建重叠 core。

## MPV 运行参数

- 初始化前固定 `vo=gpu`、`gpu-context=android`、所选 `hwdec`、AudioTrack 输出、缓存、字幕字体目录与
  默认倍速；不写隐式播放器替代路径。
- 网络 request 保留真实 `User-Agent`、`Referer`、`Origin`、`Cookie`、`Range` 与 `Accept`；
  缺失字段保持缺失，不根据页面 URL 猜测。浏览器捕获的 `Range` 只描述当次网络分段，player runtime
  在写入 `http-header-fields` 前将其排除，由 mpv/FFmpeg 根据当前 open/seek 偏移生成实际 Range。
- `Anime4KMode.OFF` 清空 mpv `glsl-shaders`；其他模式先核验参考仓库锁定的授权文本和 shader
  SHA-256，再把匹配模式的文件复制到版本化应用私有目录并二次核验，最后把绝对路径设置给
  `glsl-shaders` 并读取同一属性确认。初始化、复制、缓存、设置或回读失败必须进入可见错误状态，不能把
  开关显示为已生效。
- ExoPlayer 继续只服务现有背景视频，不参与播放器媒体加载。

## 首期播放器 UI

- 黑色沉浸背景；顶部返回、标题；中央播放/暂停；底部进度、时间、倍速、音轨、字幕。
- 横向拖动 seek；左侧纵向调屏幕亮度并在右侧显示提示；右侧纵向调媒体音量并在左侧显示提示。
  手势开始后锁定轴向，控制层有明确反馈。开启长按加速时，同一 detector 在长按阈值后临时升档，
  松手或取消时恢复原速。
- 手机横屏、竖屏和大屏复用同一 session，不因配置变化重新加载。
- 错误层显示具体错误与 URI 类型；错误写入 `AppLogger`，不得显示成功控制状态。

## 设置 owner 与页面

`PlayerSettingsStore` 是唯一持久化 owner，首期字段全部由 session 真实消费：

- 硬件解码策略：自动、MediaCodec 直通、MediaCodec copy、软件解码，对应单一 mpv `hwdec` 值
- 默认倍速：仅允许页面提供的固定值，新媒体加载时应用
- 后台行为：Activity/浏览器 host 真正进入后台时暂停或继续
- 全屏返回行为：来自浏览器的 session 返回悬浮或关闭
- Anime4K 模式：关、A、B、C、A+、B+、C+，分别约束锁定的 Balanced/M shader 链

设置首页“视频播放器”进入活动设置会话的 `KiyoriSettingsRoute.PLAYER`。页面复用
`KiyoriCollapsingSettingsPage`、`KiyoriSettingsGroupCard` 及浏览器/下载设置已有行组件、卡片间距和折叠
标题，不建立新设计系统。

## 阶段 8 验收门禁

- 系统“用 Kiyori 打开”视频进入 `PlayerActivity`，不进入 AI 附件。
- 本地 `content://`、`file://` 和网络 URI 的单一 session 可开始、暂停、seek、改速、切音轨/字幕。
- Activity 重建、方向变化和 Surface 更换不增加 `loadfile` 计数。
- 全部设置均有纯逻辑映射或静态链路测试，Anime4K 非关闭模式对应 SHA-256 锁定的 shader 文件和
  经回读确认的 mpv property。
- native 输入/输出哈希、动态符号、ABI、许可证和 16 KB 证据完整。
- 定向测试、formal readiness、`git diff --check` 与第一次 `assembleDebug` 全部通过，并单独记录阶段 8 APK。
- 真机解码、性能、手势与方向行为保持 `verification_pending`；阶段 8 APK 未通过时禁止进入阶段 9。

## 2026-07-28 阶段 8 本地结果

- [DONE] 唯一 `PlayerSession`、MPV core、surface owner token、URI/headers/文件描述符、播放/轨道/渲染状态已落地；相同 request ID 的 presentation 转换不增加 `loadGeneration`。
- [DONE] `PlayerActivity` 接收视频与 HLS/DASH MIME；`MainActivity` 的 `ACTION_VIEW` 不再以 `*/*` 接收视频，`ACTION_SEND`/`ACTION_SEND_MULTIPLE` 兼容保持。
- [DONE] 设置首页入口、折叠设置页和 `PlayerSettingsStore` 已接入硬解、默认倍速、后台、全屏返回与真实 Anime4K shader。
- [DONE] `PlayerPolicyTest`、`KiyoriSettingsPagesTest`、`KiyoriShellStateTest` 通过；native/Intent/assets Python 门禁 15/15 通过；formal readiness 与 `git diff --check` 通过。
- [DONE] 阶段 8 `assembleDebug` 成功。APK 时间 `2026-07-28 01:49:07 +08:00`，大小 `468740920` 字节，SHA-256 `E92FF610D80F9F632EEC3AE08FA857760CD8E65D9FB807DE2829AAE968C2D6C6`。
- [DONE] APK 为 `com.kiyori`、`45`/`0.1.0`、min 26/target 34、Android Debug v2；`zipalign -c -P 16 -v 4` 通过。
- [DONE] APK 仅 `arm64-v8a`，46 个 native 文件名无重复；45 个 ELF 全部 16 KB `PT_LOAD`，既有 2 字节 `libsudo.so` 是唯一非 ELF；播放器所需 254 个 FFmpeg 符号缺失为 0，字符串实证 mpv `v0.41.0-dev-g2339eb727` 与 FFmpeg `n8.1.2`。
- [PENDING] 真机 `content://`/网络解码、硬解策略、Anime4K 输出、亮度/音量/seek 手势与方向变化仍为 `verification_pending`。

## 2026-07-29 原生 HTTP/HTTPS 修正

- [DONE] 使用真机导出报告把直接 HTTPS MP4 失败定位到 FFmpegKit `libavformat.so` 没有 TLS 后端，
  排除浏览器 URL/headers、Surface、GLES、VO 和解码器
- [DONE] 固定 mpv 输入的 FFmpeg `n8.1.2` 启用 Mbed TLS 3.6.6；七个 ELF 通过等长 SONAME /
  `DT_NEEDED` 改名进入 `libmp*.so` 命名空间，`libmpv.so` / `libplayer.so` 不再解析 FFmpegKit
- [DONE] 主进程 FFmpegKit 工具继续使用正常 `libav*.so` 名称；未新增代理、第二播放器、URL 改写或
  其他网络 owner
- [DONE] `MpvPlayerEngine` 复制固定 `cacert.pem`，设置 `tls-ca-file`、`tls-verify=yes` 和
  `ytdl=no`，并把 TLS 后端、证书校验和 ytdl 状态写入播放器日志
- [DONE] 依赖脚本、Python 测试、Gradle 输入门禁、Kotlin 编译、播放器 JVM 测试、formal readiness、
  最终 APK 打包门禁和独立 NDK ELF/符号审计通过
- [PENDING] 在目标设备安装 `2026-07-29 14:22:10 +08:00` Debug APK 后复测 HTTPS MP4、HLS、
  请求头、重定向、证书失败、网速/缓冲和日志导出
