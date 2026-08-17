---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
date: 2026-08-15
updated: 2026-08-16
---

# 在线视频播放可靠性、兼容性与快速启动方案

## 状态与权威边界

本文是阶段 13 的设计与验收合同。它依据用户提供的九份播放器诊断报告、当前 `main` 源码、
固定 mpv AAR、`Riteshp2001/mpvlibAndroid@168e0a5e43b37c85509050cddcb5eaddc2e313c0`
源码、当前已有 Debug APK 和上游正式资料形成。

当前状态是
`[LOCAL IMPLEMENTATION, AUTOMATED GATES AND DEBUG APK AUDIT COMPLETE / DEVICE, REAL-NETWORK AND NATIVE REFRESH VERIFICATION PENDING]`：

- 前一轮已经完成当前文档静态资源目录、图片缩略图/查看器、音视频资源嗅探、多证据媒体身份、播放器
  主进程/`:player` 脱敏网络快照、默认网络变化观察、结构化网络失败、快速启动默认值、seek、
  header、普通/完整缓存、Surface 拉伸、MediaCodec 设置、bounded runtime capability 和实际
  demux/track 记录
- `2026-08-16` 的六份后续报告又确认了 capability 初始化修复、两条真实成功播放、VPN/静态代理下
  的两条 TCP refused、伪装 `.mp3` 的 MP4/HEVC 视频成功播放，以及完整缓存资格和 Surface 转挂
  seek 仍需修正
- 最终优化把在线播放缓存收敛为唯一四档策略，修复对不存在稳定合同的 `stream-start` 依赖、
  cache-state 吞错、Surface 内部 seek 误投影和稳定 `VIDEO_RECONFIG` 后媒体身份缺失；代码、自动
  检查、Debug APK 构建与静态产物审计已完成
- 没有访问报告中的真实媒体地址，没有额外 HEAD/GET/Range 探测
- 没有安装 APK、使用 ADB/MuMu、操作设备或发起真实播放请求
- 没有创建第二 WebView、第二播放器、第二 mpv core、第二下载器、第二网络日志 owner、本地代理、
  自动重试、URL 改写或 VPN 绕过
- Mbed TLS `3.6.7` 和 FFmpeg `9.0.1` 仍属于可复现 native closure 门禁；当前仓库只有固定二进制
  AAR 和转换脚本，没有本轮可审计的完整 source/build 输入，因此没有伪造 native 更新
- 本次最终 Debug APK 的时间、大小、SHA-256、签名、16 KiB、AAR payload、ELF、符号闭包、版本
  marker 和 DEX descriptor 证据已重新生成并写入本文末尾
- 真实 VPN/站点/设备、完整缓存断网 seek、硬解和 Surface 转挂视觉行为继续保持
  `verification_pending`

Kiyori 当前仍按未发布产品处理。正式实现时可以删除被本文替代且没有继续用途的开发期设置语义和
实现，不保留并行 UI、旧播放器路径、自动重试、URL 改写、代理播放、第二播放内核或其它运行时替代
路径。稳定的兼容标识、浏览器状态、媒体 request、下载 owner、AIDL/JNI 名称和持久化数据边界仍按
`CONTEXT.md` 与项目规则处理。

## 目标

本阶段必须同时解决以下问题：

1. 让网络媒体失败显示真实、可验证的失败阶段和原因，不再只剩 `loading failed`
2. 重点识别 VPN、分应用 VPN、Private DNS、系统代理、IPv4/IPv6 和默认路由对 `:player`
   原生网络连接的影响
3. 降低新媒体从点击到可播放状态的应用侧开销，避免默认超分、重渲染和诊断日志拖慢启动
4. 让普通 VOD、HLS/DASH 和可 seek 的直接文件拥有响应式、可观测、不会伪造完成状态的进度跳转
5. 明确浏览器捕获 headers、mpv/FFmpeg transport headers 和跨域媒体身份的所有权
6. 以实际打包 runtime 能力建立协议、manifest、container、codec、硬解和 seekability 支持矩阵
7. 在不破坏唯一 `PlayerSession`、唯一 `:player` mpv core 和唯一 Browser Runtime 的前提下，
   提升可维护性、稳定性和现场诊断能力
8. 把“在线播放缓存”收敛为“省流模式 / 智能均衡 / 流畅优先 / 完整缓存”唯一四档策略；
   “完整缓存”使用同一 mpv request 的会话磁盘 cache，具有真实完成证据、空间边界和关闭即释放合同
9. 修复“拉伸画面”读取 display metrics 的根因，按当前 Surface 几何自动适配横屏、竖屏、悬浮、
   自由窗口和折叠屏
10. 冻结 mpv、FFmpeg、Mbed TLS 和 mpvlibAndroid 更新决策，把必要的 native 安全刷新与功能修复
    分为可独立归因的里程碑

## 非目标

- 不把普通网页地址交给播放器解析；`ytdl=no` 保持不变
- 不宣称支持 `blob:`、MSE、WebRTC、DRM、Widevine、网页播放器脚本或受保护页面视频
- 不新增 ExoPlayer 接管、第二 `PlayerSession`、第二下载器、本地 HTTP 代理或媒体转发服务
- 不改写、补全、重签或替换浏览器嗅探到的媒体 URL
- 不关闭 TLS 校验，不接受任意证书，不把 HTTPS 改成 HTTP
- 不在播放前额外发送 Java Socket、HEAD、GET 或 DNS 探测请求
- 不自动重试、自动换协议、自动换地址、自动换播放内核或在失败后静默改变用户设置
- 不以扩展名列表代替实际协议、demuxer、codec 和设备能力验证
- 不把本地单元测试、静态 APK 检查或 `PLAYBACK_RESTART` 事件写成用户已看到首帧

## 最终统一设计：静态资源目录与资源嗅探

### 1. “网络日志”的产品语义

浏览器菜单名称继续是“网络日志”，但其内容模型是当前活动 WebSession、当前文档的静态资源目录，
不是时间事件流：

```text
WebSession
  credentialDocumentToken = document generation
  networkEntries = current-document resource directory

resource identity =
  documentToken
  + normalize(originalUrl)
```

URL 规范化规则：

1. 原始 URL 仍作为用户操作、播放、下载和诊断的唯一执行地址，不改写
2. `#fragment` 不参与资源 identity
3. query 完整保留；不同签名、token、清晰度或 CDN query 是不同资源
4. scheme/host 仅为 identity 做小写归一
5. HTTP `80` 与 HTTPS `443` 默认端口不重复计入 identity
6. 空 path 归一为 `/`
7. 不能安全解析的 URL 只移除 fragment，不猜测或补全

同一 generation、同一 identity 的重复请求只保留一个目录项，并合并：

```text
requestCount
firstSeenAt
lastSeenAt
request headers identity
blocked evidence
category evidence
main-frame/static facts
media candidate id
```

列表按类别、host 和 URL 稳定排列，不反向按时间排列，不在每行右侧显示当前时间。首次/末次发现时间
只保留在资源详情中作为调试事实。`onPageStarted()` 先生成新 `credentialDocumentToken`，再清理资源
目录和媒体候选；后台拦截线程在写入前、持锁后都复核 token，导航竞态中的旧 generation 结果不会
污染新页面。资源目录使用独立的 `2,000` 个 identity 上限，不再与控制台事件的 `500` 条上限共用；
超过上限时移除最早进入目录且尚未再次命中的 identity，避免复杂页面因历史控制台容量过早丢项，
同时保持 headers 和 Compose 列表内存有界。

### 2. 资源分类

当前分类冻结为：

| 类别 | 证据 |
| --- | --- |
| `VIDEO` | video MIME、已知视频/manifest suffix、视频媒体候选 |
| `AUDIO` | audio MIME、已知音频 suffix、音频媒体候选 |
| `IMAGE` | image MIME、ico/jpg/jpeg/png/gif/webp/bmp/svg/avif/heic |
| `WEB` | 主文档、HTML/XHTML |
| `SCRIPT` | JavaScript/MJS/JSX/TS/TSX |
| `STYLE` | CSS/SCSS/LESS |
| `DATA` | JSON/XML/TEXT/CSV/YAML |
| `FONT` | WOFF/WOFF2/TTF/OTF |
| `OTHER` | 尚无足够证据的其它资源 |

同一资源出现多个类别证据时，媒体与图片证据优先于文档/脚本/其它；与媒体候选 identity 相同的
资源最终由媒体候选的 `BrowserMediaKind` 修正为 `VIDEO` 或 `AUDIO`。

### 3. 图片缩略图与内置查看器

图片目录项左侧显示固定 `52dp`、请求尺寸 `160x160` 的可见项缩略图。缩略图和内置图片查看器使用
项目已有 Coil owner，只转发当前 WebSession 已捕获且与图片读取有关的：

```text
Accept
Cookie
Origin
Referer
User-Agent
```

不转发 Range、Sec-Fetch、Sec-CH-UA、Host、Connection 或 Accept-Encoding，不创建浏览器下载任务，
不持久化另一份资源数据库。LazyColumn 只为可见目录项组合请求；Coil 继续负责有界内存/磁盘图片
缓存。内置查看器支持适配显示、双击放大/恢复和双指缩放/平移。

### 4. 资源动作矩阵

| 资源 | 动作 |
| --- | --- |
| 图片 | 查看图片、复制链接、下载资源、外部打开、查看详情、拦截 |
| 视频 | 使用同一媒体候选 ID 进入唯一 `PlayerSession`、下载、复制、外部打开、详情、拦截 |
| 音频 | 识别、展示、下载、复制、外部打开、详情；“播放音乐”明确显示尚无内置 owner，不调用视频入口 |
| 网页/HTML | 复制、下载、外部打开、详情、拦截；后续源码工作台继续复用现有 page-source owner |
| JS/CSS/JSON/TEXT | 复制、下载、外部打开、资源详情；不在本阶段创建第二文本数据库或第二 HTTP client |
| 字体/其它 | 复制、下载、外部打开、详情、拦截 |
| DOM 元素拦截 | 保留 selector、规则来源和拦截事实，不伪造成网络请求 |

### 5. “资源嗅探”统一模型

用户可见名称统一为“资源嗅探”。DOM observer 同时扫描：

```text
HTMLVideoElement
HTMLAudioElement
currentSrc
src
<source>
play
loadedmetadata
durationchange
resize
```

统一媒体身份为：

```text
BrowserMediaKind =
  VIDEO
  AUDIO
  UNKNOWN_MEDIA
```

浏览器路由证据优先级：

1. 当前文档 `HTMLVideoElement` / `HTMLAudioElement`
2. 受控 intercepted response MIME
3. 受控 response `Content-Disposition` 的 `filename` / `filename*`
4. request Accept / declared MIME
5. URL suffix

其中 video DOM 是强视频证据，优先于 `.mp3` suffix 和 `audio/mpeg`。因此以下地址不会因后缀在
浏览器阶段被排除：

```text
https://example.invalid/base64-name.mp3?signature=redacted
```

若网页把它交给 `<video>`，候选为 `VIDEO + OTHER_VIDEO`，原始 URL 与当前请求 headers 原样进入
`PlayerSession`，mpv 自行 demux；`FILE_LOADED` 后 runtime 记录 `file-format`、video-track count、
video codec 和 audio codec。该 mpv 结果是播放后诊断的最高事实，但不反向改写浏览器原始候选或
URL。若只有真实 `<audio>` 证据，则候选为 `AUDIO`，显示和下载可用，但播放音乐保持禁用。无扩展
URL 只有在当前受控拦截响应明确给出媒体 MIME 或 `Content-Disposition` 文件名时才成为候选；没有
额外网络探测、响应正文读取或站点特例。

### 6. 与播放器可靠性方案的串行关系

统一后的本地实现顺序为：

```text
R1  current-document resource identity and aggregation
R2  category projection, thumbnail and image viewer
R3  video/audio DOM observation and disguised-media evidence
R4  browser-only header removal and original request ownership
R5  passive VPN/effective-network observation and structured native failure
R6  fast defaults, explicit hwdec, seek/thumbnail and PLAYBACK_RESTART
R7  ordinary/full cache and Surface geometry
R8  bounded runtime capability and actual demux/track/codec evidence
R9  native Mbed TLS/FFmpeg closure qualification
R10 device/real-network acceptance
```

R1 至 R8 已本地实现；R9 需要可复现 source/build 输入，R10 需要用户授权设备和真实媒体矩阵。

## 证据基线

### 用户现场报告

首批三份报告均来自 `Kiyori 0.1.0 (45)`、`vivo V2507A`、Android 16 / SDK 36，并使用同一个
`:player` PID。为避免在项目文档中保存真实媒体地址，本文只记录报告时间、哈希和脱敏结论：

| 报告 | 生成时间 | SHA-256 | 媒体形态 |
| --- | --- | --- | --- |
| A | `2026-08-15T16:37:25.935888Z` | `1063082786E9169BE2BCAE570E3363544BB3E8F3AEF48B704C969DB7EC64E55E` | HLS 历史重播 |
| B | `2026-08-15T16:37:35.489293Z` | `29AEB8E3EE491C9AB3423A72F883A76851D0827260992FBD93500F912BA9D007` | 浏览器直接 MP4 候选 |
| C | `2026-08-15T16:37:52.732322Z` | `223AEE716035A52F462A0A7A639A760A106C210D4C43C752F89C6CD1DB4178CF` | 带签名查询的 MP4；悬浮转全屏 |

报告导出时间使用 UTC，报告中的应用日志使用 `Asia/Shanghai`，二者描述的是同一批现场事件。

后续六份现场证据继续来自同一型号和系统：

| 报告 | 生成时间 | SHA-256 | 脱敏结论 |
| --- | --- | --- | --- |
| D | `2026-08-16T06:46:28.591835Z` | 直接粘贴，无独立附件哈希 | 旧 capability 代码把可选 `option-info/hwdec/choices` 当作必需数组，runtime 在 Surface/loadfile 前失败 |
| E | `2026-08-16T08:17:48.627866Z` | `CEE2618FC0BC4309E208388BBA2EED1E666DE8ACDEE1EBB4B7DD6E1D019BEDF1` | 普通 Wi-Fi 的短 direct MP4 成功；整段和整文件几乎已缓存，但因 `stream-start` 为空仍被判为不具备整片资格 |
| F | `2026-08-16T08:18:18.990709Z` | `267B3B415FE0A0483B89D0773F6C64F4E9A6B4F9D578107A614C33D34088428F` | VPN + Wi-Fi + 静态代理下 direct MP4 成功；floating 转 fullscreen 保持同一 PID/load，但活动播放期间出现内部 seek、H.264 恢复告警和 audio underrun |
| G | `2026-08-16T08:31:17.592517Z` | `6A5215BC5264F23466234C5AD5C7FAB77FB1A25AF4FC756ED63E9ED34AC8F051` | VPN + Wi-Fi + 静态代理；九个输入 header、四个转发 header；首次 TCP 443 connect 立即 refused |
| H | `2026-08-16T08:31:41.550602Z` | `A63E64B00C72FA860EB2F9451A354629E917100A9CF8143F441153BC202E0A5B` | 同一网络和主机；只有一个 User-Agent header；仍在首次 TCP 443 connect 立即 refused，排除 header 数量为本批根因 |
| I | `2026-08-16T08:32:13.559299Z` | `B4AAEC9DE4DF23055D818EA28545F3A40B153C2B3BF47BB9F3A17F446587E126` | 普通 Wi-Fi；`.mp3` 后缀资源实际解封装为 MP4、HEVC、AAC 并成功到达 `FILE_LOADED`/`PLAYBACK_RESTART` |

G/H 都在 HTTP 响应、TLS 握手、demux 和 decoder 之前失败；I 证明 URL suffix 不能决定播放器最终媒体
身份。网络快照用于定位 VPN、静态代理和路由事实，但这些报告仍不能证明拒绝由 VPN、代理、目标服务
或中间网络中的哪一方产生。

### 当前固定 runtime

当前源码与本地二进制审计确认：

- `app/libs/mpv-player-arm64.aar` SHA-256：
  `FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C`
- AAR 内 `libmpv.so` 报告：
  `mpv v0.41.0-dev-g2339eb727`
- mpv 使用 FFmpeg `n8.1.2`
- `libmpformat.so` 包含 `--enable-mbedtls`、Mbed TLS 3.6.6 和 HTTPS/TLS 标记
- `libmpv.so` 只包含 lavf 网络输入路径的证据，没有当前构建启用 curl stream backend 的证据
- 当前已有 Debug APK SHA-256：
  `64FE05CB67BBCC040923C476AE7E7825407BA60D49EC55DE5282C3F7E8C4E5B7`
- AAR 与该 APK 中的 `libmpv.so`、`libmpformat.so`、`libplayer.so` SHA-256 逐项相等
- APK 为 `com.kiyori 0.1.0 (45)`、`arm64-v8a`、Android Debug V2 签名，16 KiB ZIP
  alignment 检查通过

以上 APK 是调查时已经存在的本地产物，不是本设计阶段重新构建的结果。

### 上游版本与 `2026-07-18` AAR 静态对照

截至本方案核验时：

- mpv 最新正式稳定版仍是
  [`v0.41.0`](https://github.com/mpv-player/mpv/releases/tag/v0.41.0)；当前
  `v0.41.0-dev-g2339eb727` 是稳定版之后的固定开发快照，不是落后一个正式大版本
- FFmpeg 官网已于 `2026-08-12` 发布最新稳定版
  [`9.0.1`](https://ffmpeg.org/download.html)；当前播放器的 `n8.1.2` 是 8.1 分支最新点版本，
  但不再是全局最新稳定版
- Mbed TLS 全局最新正式版已经进入 `4.2.0`，而当前兼容 LTS 分支的最新点版本是
  [`3.6.7`](https://github.com/Mbed-TLS/mbedtls/releases/tag/mbedtls-3.6.7)。本阶段只把
  `3.6.7` 作为最小 native refresh 候选：它保留 3.6 API/ABI 迁移面并包含安全与 TLS 1.2
  RSA-PSS 修正；`4.2.0` 属于独立 major 迁移，必须另做 PSA/配置/API/证书矩阵，不能与播放器网络
  根因修复混入同一 closure
- `Riteshp2001/mpvlibAndroid` 最新 release 为
  [`2026-07-18`](https://github.com/Riteshp2001/mpvlibAndroid/releases/tag/2026-07-18)，
  新于 Kiyori 固定的 `2026-06-25`
- 官方 `mpv-android` 的
  [`2026-08-11`](https://github.com/mpv-android/mpv-android/releases/tag/2026-08-11)
  构建已经组合 mpv `f4d13e1`、FFmpeg 9.0、Mbed TLS 3.6.7 和 curl，并记录了部分 TLS
  服务端连接修正；这证明 Android 上的新一代 closure 可构建，但不证明它可直接替换 Kiyori

已下载并静态审计官方 `2026-07-18` AAR：

```text
input SHA-256 =
325AA918322A2B97D4119C678D272723000BA2CC0E82820827C153BDE4A1977B

mpv       = 0.41.0-dev-g94335ab87-dirty
FFmpeg    = 8.1.2
Mbed TLS  = 3.6.6
NDK       = r29
Clang/LLD = 21.0.0 / 21.0.0
```

arm64 的十个 native ELF 全部满足 `PT_LOAD >= 0x4000`；`libc++_shared.so` 与当前输入
逐字节相同。`MPVLib` 的公开 JNI 方法集合保持一致，`Utils.Versions` 从四个字段扩展为十七个字段，
资产 `cacert.pem`、`subfont.ttf` 和 AAR manifest 与当前输入相同。

但该 release 不能作为 Kiyori 的直接替换输入：

1. mpv 变成带 `dirty` 标记的更新快照，并全局修改了内建 shader，影响所有视频而不只是用户显式选择
   的 Anime4K
2. FFmpeg 和 Mbed TLS 没有升级，仍是 `8.1.2 / 3.6.6`
3. 它通过关闭 `MBEDTLS_X509_RSASSA_PSS_SUPPORT` 规避 Mbed TLS 3.6.6 的 TLS 1.2
   RSA-PSS 问题，会缩小可接受证书的范围
4. 没有证据表明这些变化能修复本批首次 TCP connect 阶段的 `Connection refused`

版本决策冻结如下：

| 组件 | 本阶段决策 | 原因 |
| --- | --- | --- |
| mpv | M1 至 M8 保持 `2339eb727` | 当前正式稳定版仍为 0.41.0；先避免把功能修复与 mpv master 变化混在一起 |
| FFmpeg | M1 至 M8 保持 `n8.1.2`；M9 资格评估 `n9.0.1` | 9.0.1 已是最新稳定版，但 libavformat/libavcodec 主版本从 62 升到 63，必须重建并回归整个 closure |
| Mbed TLS | 当前固定 `3.6.6`；独立资格评估 `3.6.7`；不直接跳到 `4.2.0` | 3.6.7 是同 LTS 线最小安全/RSA-PSS refresh；4.2.0 是独立 major 迁移 |
| mpvlibAndroid `2026-07-18` | 不采用 | 全局渲染变化和 RSA-PSS 禁用不符合 Kiyori 合同 |
| curl stream backend | 不进入 M8 交付；只在 M9 单独测量 | 会改变 HTTP/header/proxy/seek transport 行为，不能与 FFmpeg 主版本同时切换后再猜收益来源 |

当前实际打包并可本地审计的 closure 为：

```text
mpv commit       = 2339eb727
FFmpeg           = n8.1.2
Mbed TLS         = 3.6.6
curl             = disabled
input            = fixed 2026-06-25 binary AAR
```

M8 的最小安全维护候选仍是同一 mpv/FFmpeg 基线只把 Mbed TLS 更新为 `3.6.7`，但当前仓库缺少
mpv、FFmpeg、Mbed TLS 和 `libplayer.so` 的完整 source/build closure、配置、补丁、toolchain lock、
法律材料和播放资格 fixtures，无法在本轮产生可审计 AAR。M8 因输入缺失保持
`verification_pending`；没有下载、替换或修改二进制 marker。

M9 另建 FFmpeg 重大版本资格候选，不与 M8 混合：

```text
reference mpv    = f4d13e1（仅为已知 Android/FFmpeg 9 可构建参考）
Kiyori mpv       = 经源码审计后固定的非 dirty commit
FFmpeg           = n9.0.1
Mbed TLS         = 3.6.7
curl             = disabled in the first qualification build
NDK              = r29
```

FFmpeg 9.0.1 的 `libavutil/libavcodec/libavformat` 分别是 `61/63/63`，当前 8.1.2 是
`60/62/62`。即使 ELF basename 保持 `libav*.so`，这也是 native ABI 和 API 的重大版本变化；
必须重新编译 mpv、FFmpeg、`libplayer.so` 和同 closure 的全部库，不能只替换七个 `.so`。

M9 只有同时满足以下条件才可把 FFmpeg 9.0.1 选为最终输入：

1. 固定、非 dirty 的 mpv commit 能以 Kiyori 配置完整编译
2. 不带全局 shader 修改，不关闭 RSA-PSS，不改变 Anime4K 所有权
3. current 与 candidate 的协议、demuxer、decoder、header、redirect、seek、cache、Surface、
   MediaCodec、RSS、启动 P50/P95 和 TLS 样本全部对照
4. 至少存在可验证的兼容性、安全或性能收益，且没有当前支持样本退化
5. AAR/API/ELF/16 KiB/许可证/对应源码和 Debug APK 门禁全部通过

若这些条件没有同时满足，M9 保持“资格输入未满足并保留证据”；当前产品 closure 继续使用固定
FFmpeg 8.1.2 + Mbed TLS 3.6.6。产品运行时始终只打包一个选定 closure，不存在双版本切换。

这是一项供应链、安全和 TLS 兼容性维护，不得写成本批 TCP refused 的修复。只有重建后的
AAR hash、source commit、patch、许可证、JNI、ELF、16 KiB、全部播放器样本和目标设备矩阵通过后，
才替换当前固定输入。

### 上游语义依据

方案以当前打包版本对应的上游语义为准：

- [mpv 固定提交的 builtin profiles](https://raw.githubusercontent.com/mpv-player/mpv/2339eb727/etc/builtin.conf)
- [mpv 固定提交的 client API events](https://raw.githubusercontent.com/mpv-player/mpv/2339eb727/include/mpv/client.h)
- [mpv stable manual](https://mpv.io/manual/stable/)
- [FFmpeg n8.1.2 HTTP implementation](https://raw.githubusercontent.com/FFmpeg/FFmpeg/n8.1.2/libavformat/http.c)
- [Android ConnectivityManager](https://developer.android.com/reference/android/net/ConnectivityManager)
- [Android NetworkCapabilities](https://developer.android.com/reference/android/net/NetworkCapabilities)
- [Android LinkProperties](https://developer.android.com/reference/android/net/LinkProperties)

## 三次失败的确认链路

三份报告均完成了以下步骤：

```text
PlayerSession.open
  -> bind 已存在的非导出 :player runtime
  -> 创建并初始化唯一 mpv core
  -> 验证 FFmpeg n8.1.2 / Mbed TLS / CA bundle / tls-verify=yes
  -> Android Surface attach sent
  -> :player native attach
  -> Surface attach ACK
  -> loadfile sent
  -> FFmpeg 打开脱敏 HTTPS 地址
  -> 首次 TCP 443 connect
  -> Connection refused
  -> END_FILE reason=error, file_error=loading failed
```

报告 C 在加载失败后又完整完成：

```text
FLOATING detach sent
  -> native detach
  -> detach ACK
  -> FULLSCREEN attach sent
  -> native attach
  -> attach ACK
```

因此 Surface 转挂和本次 TCP 失败是两个独立问题。

## 根因分层

### 已确认事实

1. 三个不同 HTTPS 主机都在第一次 TCP 443 连接时返回 `Connection refused`
2. 失败发生在 HTTP 响应头、TLS 握手、manifest、demux、codec、decoder、首帧和 seek 之前
3. `INTERNET` 与 `ACCESS_NETWORK_STATE` 权限均已声明
4. `PlayerRuntimeService` 使用同一应用 UID 的 `:player` 进程，不是 `isolatedProcess`
5. `PlayerMediaResolver` 对 HTTP/HTTPS URL 保持原样，没有 URL 改写或代理
6. 当前 APK 使用正确的 namespaced mpv FFmpeg，不是旧 FFmpegKit 无 TLS 输入
7. 浏览器观察到的静态 `Range` 已被移除，mpv/FFmpeg 继续拥有实际 open/seek byte range

### 已排除为本次首因

- HLS 或 MP4 格式本身不受支持
- TLS CA bundle 缺失
- TLS 证书验证失败
- Surface attach 失败
- Anime4K shader 编译失败
- 视频或音频 decoder 不受支持
- HTTP 401/403/404/416/429/5xx
- 浏览器 `Range` 覆盖了 FFmpeg 当前 offset
- 进度条或 thumbnail 请求导致本次首次连接失败

这些项目不是被永久证明没有问题，而是三份报告在到达对应阶段前已经失败，不能用它们解释本批
`Connection refused`。

### 高可信推断

三个不同主机在十几秒内都出现即时拒绝，比“三个远端源同时以相同方式故障”更符合以下一类共同
网络条件：

- VPN 当前没有承载 Kiyori 的 `:player` native socket
- 分应用 VPN 排除了 Kiyori，或 VPN 只代理浏览器/WebView 流量
- Private DNS、广告过滤、家长控制、防火墙或本地 VPN 把域名导向拒绝连接的地址
- 系统代理/PAC 被 WebView 使用，但 native FFmpeg 的 HTTPS 不走同一代理路径
- IPv4/IPv6 地址选择、默认路由或运营商网络策略与 WebView 实际使用路径不同

这仍是推断，不能在没有同机、同 URL、同时间的单变量对照前写成已确认设备根因。

### 未知项

- 拒绝来自远端服务器、设备本地网络层、VPN、DNS 重定向还是运营商
- WebView/系统浏览器在相同时间能否成功访问同一媒体地址
- `:player` 观察到的默认网络是否带 `TRANSPORT_VPN`
- 设备是否存在系统代理、PAC、Private DNS 或 Data Saver 限制
- DNS 结果是否同时含 IPv4/IPv6，失败连接使用了哪一族
- VPN 是否按应用允许 `com.kiyori`、是否允许绕过、是否只接管部分协议

## VPN 专项设计

### 为什么 VPN 必须单独建模

`:player` 和主进程使用同一 UID。通常情况下，按应用配置的 Android VPN 应同时包含或排除两个进程；
但以下差异仍可能出现：

- 某个进程被显式 `bindProcessToNetwork()` 绑定到其它网络
- WebView 使用系统代理、PAC、自有 DNS/连接栈，而 FFmpeg 使用 native lavf socket
- VPN 是全设备 TUN、局部代理、浏览器扩展或仅支持特定协议，实际覆盖面不同
- VPN 或过滤器通过 Private DNS、hosts、透明代理或拒绝端口实现拦截
- VPN 的 IPv4/IPv6 路由不对称

当前仓库定向搜索没有发现播放器或浏览器调用 `bindProcessToNetwork()`、
`setProcessDefaultNetwork()`、`Network.bindSocket()` 或同类显式网络绑定 API。因此现有源码没有证据
表明 Kiyori 主动把 `:player` 绕出应用默认网络；正式诊断仍需读取
`getBoundNetworkForProcess()`，用于发现依赖、OEM 或未来代码产生的实际绑定状态。

mpv 官方手册还明确限制 `http-proxy` 只用于 HTTP，不用于 HTTPS。三份样本都是 HTTPS，因此“系统里
已经配置代理”不能证明 mpv 的 HTTPS 会使用该代理。

### 被动网络快照

正式实现使用只读、脱敏的 `PlayerNetworkSnapshot`。它不是第二网络状态 owner，不驱动网络切换：

- `:player` runtime 初始化时在唯一 `runtimeHandler` 上注册 `registerDefaultNetworkCallback()`，
  立即记录 initial 快照
- available、lost、capabilities 和 link-properties callback 在 `250 ms` 内合并；只有脱敏事实
  确实变化时才追加一条 `PlayerNetwork` 诊断
- 每次 `:player` 准备发送 `loadfile` 前再记录 request 起点快照
- 导出报告时记录主进程当时的快照，并保留同一有序日志中的 `:player` initial/change/load 快照
- `FILE_LOADED`、seek、`PLAYBACK_RESTART`、full cache phase 和第一条 native 错误继续作为相邻的
  有序诊断事件；它们不为制造日志而重复采集相同网络快照

快照字段：

| 字段 | 记录方式 |
| --- | --- |
| active/default network | 是否存在；不记录 network handle、对象文本或派生 identity |
| process-bound network | 是否存在、是否等于 active network，以及 effective source 是 `bound / active / absent` |
| transports | active 与 effective 分开记录 Wi-Fi、Cellular、Ethernet、VPN、Bluetooth 布尔集合 |
| capabilities | 从 effective network 读取 INTERNET、VALIDATED、NOT_SUSPENDED、CAPTIVE_PORTAL |
| VPN | active/effective transports 是否包含 `TRANSPORT_VPN`；不猜测 VPN 应用名称 |
| Private DNS | 是否启用、服务器名是否存在；不保存名称或哈希 |
| DNS | IPv4/IPv6 DNS server 数量；不保存地址 |
| route | IPv4/IPv6 默认路由数量；不保存网关 |
| proxy | absent / static / PAC；不保存 host、PAC URL、用户名或密码 |
| metered | 从 effective capabilities 的 `NOT_METERED` 反向投影；无 effective network 时为 unknown |
| restriction | 当前进程的 Data Saver/background restriction 状态 |

进程、runtime generation、事件顺序和时间由现有诊断记录承载，不伪装成网络快照字段。网络快照只
进入现有有序诊断事件和导出报告，不进入 SharedPreferences、数据库或新的长期状态仓库。

默认网络 callback 也只记录有界变化，不驱动 `loadfile`、seek、socket binding、重连或配置改变。
同一过渡在 `250 ms` 内重复的 capabilities/link-properties 更新合并为一个事件；VPN transport、
validated、proxy 类型、Private DNS、IPv4/IPv6 默认路由数量或 bound/effective 事实变化时会保留。
这样才能把“VPN 开关前后”收敛为具体网络事实，而不是主观描述。

### VPN 结果解释

| 观察 | 可以得出的结论 | 不能得出的结论 |
| --- | --- | --- |
| `:player` active network 带 VPN，且无 process binding | native socket 应使用应用默认 VPN 网络 | VPN 内部一定放行目标地址 |
| 主进程带 VPN、`:player` 不带 VPN | 两进程网络路径发生异常差异 | 远端服务器一定正常 |
| 两进程都不带 VPN，但用户认为 VPN 已开启 | Kiyori 当前未观察到 VPN 作为默认网络 | VPN 应用为何排除 Kiyori |
| VPN 关闭后成功、开启后拒绝 | VPN 条件与失败强相关 | 是 DNS、路由、规则还是远端出口封禁 |
| WebView 成功、mpv 失败，系统代理存在 | 两套网络栈可能使用不同代理路径 | 只靠增加 User-Agent 就能修复 |
| IPv6 默认路由存在，IPv4 不存在 | 地址族和路由值得作为单变量验证 | 失败一定由 IPv6 引起 |

### 明确禁止的 VPN 处理

- 不自动关闭或绕过 VPN
- 不请求用户授予 VPN 控制权限
- 不把播放器进程绑定到某个物理网络以绕开应用默认网络
- 不复制 VPN 应用配置
- 不在失败后自动改用系统代理或 Java 网络栈
- 不把 `Connection refused` 翻译成“请关闭 VPN”；UI 只说明 VPN/网络/服务端均可能，并展示已观察事实

## 必须保持的架构不变量

- 主进程唯一 `PlayerSession` 是 request、presentation、UI state、queue、seek state 和 Surface lease owner
- 非导出 `:player` `PlayerRuntimeService` 持有唯一 `MpvPlayerEngine`
- `MpvPlayerEngine` 继续是唯一允许导入 `MPVLib` / `MPVNode` 的源码 owner
- presentation 仍然只有 `BROWSER_ONLY`、`FLOATING_PLAYER`、`FULLSCREEN_PLAYER`
- presentation 转换不增加 `loadfile`
- `mpv_initialize` 先于任何 Surface attach
- `loadfile` 只在当前 Surface attach ACK 后发送
- floating/fullscreen 转挂不调用 WebView `loadUrl`、`reload`、重建、重嗅探或网页媒体 JavaScript
- 网络 URL 原样传入 mpv，不改写、不补签、不代理
- 浏览器候选继续保存原始 URL、来源页面、Profile/Cookie scope 和捕获身份
- `Range` 继续只由 mpv/FFmpeg 生成
- native fatal failure 继续由 `:player` 隔离，并只允许用户显式重新启动
- 不增加第二播放器、第二网络下载路径、第二设置 owner 或第二媒体数据库

## 当前实现的确认问题

### 1. 新安装默认配置与快速启动目标冲突

当前 `FRESH_INSTALL_PLAYER_SETTINGS` 使用：

```text
decoderPreset = HIGH_QUALITY
anime4KMode = A_PLUS
rememberAnime4KMode = true
networkCachePolicy = LARGE
preciseSeeking = true
seekbarThumbnailEnabled = true
```

三份报告也实际记录：

```text
decoder=high-quality
anime4k=A_PLUS
shaderCount=7
cache=large
hr-seek=yes
hr-seek-framedrop=no
```

该组合在网络还未连接前不是失败原因，但会在真实播放时增加 shader 验证/编译、GPU 压力、内存占用和
精确 seek 解码成本，不适合作为“先快速稳定播放”的新安装默认值。

`CONTEXT.md` 还存在内部漂移：播放器产品表写新安装 decoder 为 `fast`，后部不变量和当前代码却写
`HIGH_QUALITY + A_PLUS + LARGE`。正式开发必须让代码、设置 UI、`CONTEXT.md` 和开发文档在同一个
里程碑内统一。

### 2. “解码预设”没有实际选择 decoder backend

当前 runtime 只执行：

```text
profile=<fast/default/high-quality/gpu-hq/low-latency/sw-fast>
hwdec-codecs=all
```

没有设置 `hwdec`。mpv 默认 `hwdec=no`，而 `hwdec-codecs=all` 只限制已启用硬解时允许哪些 codec，
不会自行启用硬解。

固定 mpv 的 builtin profile 语义为：

| profile | 上游实际语义 |
| --- | --- |
| `fast` | bilinear 缩放和低成本软件缩放参数 |
| `high-quality` | `ewa_lanczossharp`、去条带和高质量缩放 |
| `gpu-hq` | `high-quality` 的兼容别名 |
| `low-latency` | 低缓存、低探测、音视频同步和延迟策略 |
| `sw-fast` | 低成本软件缩放参数 |

因此当前 UI 的“硬解”“强制软解”描述没有真实 owner。正式实现必须把“渲染/延迟 profile”和
“decoder backend”拆开，不能继续用一个枚举同时描述两个事实。

### 3. 精确 seek 使用最慢组合

当前普通拖动和按钮跳转最终都会使用：

```text
absolute+exact
hr-seek=yes
hr-seek-framedrop=no
```

mpv 官方语义表明，exact seek 会从目标之前的关键帧开始解码；禁止 framedrop 会强制完整解码中间帧。
远程 HLS、长 GOP、高码率或弱 CPU 视频会显著拖慢跳转。

当前 UI 在命令成功发送后立即把 `positionSeconds` 改成目标值，但 `mpv_command("seek")` 返回不等于
新位置已经恢复播放。真正的 seek 生命周期需要 `MPV_EVENT_SEEK` 和后续
`MPV_EVENT_PLAYBACK_RESTART`。

### 4. 在线进度缩略图打开第二条无 headers 网络路径

当前拖动 120 ms 后执行：

```text
PlayerSession.requestSeekPreview
  -> PlayerRuntimeService.requestThumbnail
  -> MpvPlayerEngine.grabThumbnail
  -> MPVLib.grabThumbnailFast(source, ...)
```

该路径：

- 只接收原始 URL
- 不接收 Cookie、Referer、Origin、Authorization 或捕获 headers
- 不复用当前 mpv demux/cache
- 使用独立 executor 和独立媒体打开
- 会与主播放争用网络、CPU、解码器和内存
- 对签名 URL、HLS/DASH、跨域 segment 和鉴权资源不可靠

这不是允许继续优化的在线 thumbnail 架构。正式实现前，在线媒体必须停止使用该路径。

### 5. `FILE_LOADED` 被误用为“加载结束”

当前 `PlayerSession.onFileLoaded()` 立即设置：

```text
loading = false
buffering = false
```

mpv client API 中，`FILE_LOADED` 只表示文件已加载、解码即将开始；它不表示用户已看到首帧。
`PLAYBACK_RESTART` 表示开始播放或 seek 后播放重新建立，也仍不能单独证明 Surface 上的像素已被用户
看到。

正式实现应把以下概念分开：

- file loaded
- playback pipeline ready
- first playback position available
- user-visible first frame，只有设备视觉验收可以关闭

### 6. header policy 只移除了 `Range`

报告 B/C 实际转发了：

```text
sec-ch-ua
sec-ch-ua-mobile
sec-ch-ua-platform
Accept
Accept-Encoding
User-Agent
Referer
Origin
```

当前策略保留所有非 `Range` 字段。风险包括：

- `Host`、`Connection`、`Content-Length` 等 framing/hop-by-hop 字段不应由浏览器 capture 固定
- WebView 的 `Accept-Encoding` 可能包含 `br` 或 `zstd`；固定 FFmpeg HTTP 实现只明确处理 gzip
- `sec-ch-*` / `Sec-Fetch-*` 描述的是浏览器导航上下文，不是 mpv 的网络合同
- 机械 allowlist 又可能误删站点自定义认证头

header policy 必须改成“明确禁止 transport/browser-owned 字段，其余端到端身份字段保留”的
case-insensitive 规则，并继续拒绝 CR/LF 注入。

### 7. 真实错误被泛化为 `loading failed`

当前 `END_FILE` 提供的 `file_error` 只有 `loading failed`。真正的 `Connection refused` 只存在于
verbose log，最终 UI 和 `PlayerSessionState.error` 丢失了因果信息。

### 8. 常态 `all=v` 产生持续 Binder 与 UI 压力

每条 mpv verbose 都从 `:player` 经有序 AIDL 回传主进程，并进入最多 2,000 条缓冲。三份失败报告在
极短时间内已经产生 138 至 213 条记录。真实播放时 demux、cache、GPU、track 和 shader 信息会继续
增加 IPC 和 Compose 刷新成本。

诊断能力必须保留，但常态不应使用 `all=v`。

### 9. cache policy 只定义容量，没有定义启动与缓冲合同

当前只设置：

```text
cache=yes
demuxer-max-bytes
demuxer-max-back-bytes
cache-secs
```

`cache-secs` 是 readahead 上限，不是首播前必须填满的时长；当前大缓存不是本批连接失败原因，但
`LARGE` 最高允许约 384 MiB 前后向缓存，默认用于移动设备缺少必要证据。

必须显式冻结：

- 是否初始暂停等待缓存
- underrun 时是否暂停
- 重新开始所需缓存时间
- network timeout
- VOD 和 live 的 seekable range 表达

不得通过减少 demux probe 或强制低延迟 profile 来换取表面首帧速度，因为这会损坏长尾格式识别和
弱网稳定性。

### 10. 支持格式列表没有绑定实际 runtime 能力

`BrowserMediaCandidate` 当前可识别 M3U8、MPD、MP4、MOV、WebM、MKV、AVI、FLV、WMV、3GP、OGV、
MPEG、ASF、RMVB 等候选，但扩展名只是发现证据，不等于：

- 当前 AAR 含对应 demuxer
- 当前 FFmpeg 含对应 decoder
- 设备 MediaCodec 支持该 profile/level
- 服务器支持 Range 或正确 MIME
- manifest 的所有 segment、key、audio rendition 和 subtitle 均可访问
- 资源没有 DRM

mpv 可公开 `protocol-list`、`demuxer-lavf-list`、`decoder-list`、`hwdec`、`seekable`、
`partially-seekable` 和 `demuxer-cache-state`。当前 runtime 没有形成能力快照。

### 11. 在线播放缓存需要唯一策略所有者和真实完整缓存证据

“设置 → 视频播放器 → 窗口与在线”原有“在线播放缓存”只有三档容量入口。开发期曾增加一个独立
“完整缓存整个视频”开关，但它与三档普通缓存形成两个状态所有者和六种组合：用户无法判断“大缓存”
与“完整缓存”谁拥有 forward/backward/time、磁盘写入和当前 request。该方案尚未发布，最终设计
必须删除独立 boolean、独立 UI 行和并行 runtime 字段。

现有 cache 还存在以下语义缺口：

- 只使用内存 demuxer cache，没有 app-private 会话磁盘 cache
- `cache-secs=60/180/300` 明确限制向前预读，不能表达“读取到有限 VOD 的 EOF”
- 没有区分 direct VOD、HLS/DASH VOD、live、不可 seek 和未知总大小
- 没有可用空间、硬性保留空间、最长时长和最大文件限制
- 没有从 `demuxer-cache-state` 判断整条媒体是否真的在唯一 mpv cache 中
- 没有定义 floating/fullscreen 交接、下一媒体、进程崩溃和关闭播放器时的清理

完整缓存不能复用 `BrowserDownloadManager`，不能把媒体另存为离线文件，不能切换到本地文件重播，
也不能发起 HEAD、Range probe 或第二次下载。它只能是唯一 `PlayerNetworkCachePolicy` 的第四个值，
扩展当前 `:player` 的同一 request、同一 headers、同一 demuxer 和同一 cache。

### 12. “拉伸画面”使用了错误的几何事实

当前 `MpvPlayerEngine.applyVideoFitMode(STRETCH)` 使用：

```text
appContext.resources.displayMetrics.widthPixels /
appContext.resources.displayMetrics.heightPixels
```

该值属于 `:player` 进程的 display metrics，不是当前 `PlayerSurfaceView` 的实际 Surface 尺寸。
横屏 Activity、浏览器固定 `16:9` 悬浮 Surface、自由窗口、折叠屏、旋转或 OEM metrics 更新时，
它可能仍保留竖屏比例，这与现场“横屏点击拉伸后仍按竖屏方向拉伸”完全一致。

实际 Surface 几何已经沿以下现有链路到达 native owner：

```text
PlayerSurfaceView.surfaceCreated / surfaceChanged
-> PlayerSession.attachSurface / updateSurface
-> PlayerRuntimeConnection
-> PlayerRuntimeService
-> MpvPlayerEngine.updateSurfaceSize(width, height)
```

但 `updateSurfaceSize()` 当前只设置 `android-surface-size=<width>x<height>`，没有保存有效宽高，也没有在
当前模式为 `STRETCH` 时重新应用 `video-aspect-override`。该问题不需要新 UI 模式或媒体重载，只需要
让唯一 engine 使用已经存在、与当前 Surface generation 匹配的几何事实。

## 目标运行时设计

### 1. request-scoped 加载时间线

新增 `PlayerLoadTelemetry`，由唯一 `PlayerSession` 持有当前 request 的投影，`:player` 只发送事件。
所有耗时使用 `SystemClock.elapsedRealtimeNanos()`：

```text
OPEN_ACCEPTED
BIND_STARTED
RUNTIME_READY
SURFACE_ATTACH_SENT
SURFACE_ATTACH_ACK
LOAD_SENT
MPV_START_FILE
NETWORK_OPEN_OBSERVED
HTTP_RESPONSE_OBSERVED
MPV_FILE_LOADED
MPV_VIDEO_RECONFIG
MPV_PLAYBACK_RESTART
FIRST_POSITION_AVAILABLE
BUFFERING_STARTED
BUFFERING_ENDED
SEEK_SENT
MPV_SEEK
SEEK_PLAYBACK_RESTART
END_FILE
RUNTIME_ERROR
```

规则：

- 同一 request ID 只允许单调推进
- 迟到 runtime generation、event sequence 或 load command ID 被拒绝
- `FILE_LOADED` 不再关闭全部 loading UI
- video request 在 `PLAYBACK_RESTART` 后进入 `PLAYBACK_READY`
- `FIRST_POSITION_AVAILABLE` 只代表属性可读，不命名为 first frame
- 设备上的真实首帧仍由人工/自动视觉验收记录

### 2. 结构化失败分类

新增稳定枚举：

```text
DNS_RESOLUTION_FAILED
TCP_CONNECTION_REFUSED
TCP_CONNECT_TIMEOUT
NETWORK_UNREACHABLE
TLS_CERTIFICATE_FAILED
TLS_HANDSHAKE_FAILED
HTTP_AUTH_REQUIRED
HTTP_FORBIDDEN
HTTP_NOT_FOUND
HTTP_RANGE_REJECTED
HTTP_RATE_LIMITED
HTTP_SERVER_ERROR
REDIRECT_FAILED
UNSUPPORTED_CONTENT_ENCODING
UNSUPPORTED_PROTOCOL
MANIFEST_FAILED
DEMUX_FAILED
UNSUPPORTED_CODEC
DECODER_FAILED
SURFACE_FAILED
RUNTIME_LINKAGE_FAILED
RUNTIME_NATIVE_EXIT
UNKNOWN_LOADING_FAILED
```

分类器只消费当前 request 内、经过脱敏的 mpv log module、severity、message 和事件顺序：

- 优先保留首次具体 transport/TLS/HTTP/demux/decoder 证据
- 后到的通用 `loading failed` 不覆盖具体原因
- 只有命中严格、带测试夹具的模式才映射枚举
- 无可靠模式时必须保持 `UNKNOWN_LOADING_FAILED`
- UI 同时显示“事实”和“可能检查项”，不能把 VPN 或服务端写成确认结论

本批报告应稳定分类为：

```text
TCP_CONNECTION_REFUSED
stage=TCP_CONNECT
httpStatus=none
tlsEstablished=false
manifestOpened=false
```

### 3. header ownership

`buildPlayerMpvHttpHeaderPlan()` 改为三类输出：

```text
forwardedHeaders
removedFieldsByReason
sensitiveIdentityPresent
```

始终移除：

```text
Range
Host
Connection
Proxy-Connection
Keep-Alive
TE
Trailer
Transfer-Encoding
Upgrade
Content-Length
Expect
Accept-Encoding
Proxy-Authorization
```

浏览器上下文字段也移除：

```text
Sec-CH-UA*
Sec-Fetch-*
Purpose
Sec-Purpose
```

继续保留：

```text
User-Agent
Referer
Origin
Cookie
Authorization
Accept
Accept-Language
站点自定义端到端认证字段
```

规则：

- 字段名大小写不敏感
- 仍拒绝空名、冒号和 CR/LF
- 不记录任何值
- 每次加载记录输入/转发/移除数量、字段名和移除原因
- 不把 browser `Accept-Encoding` 替换成自造能力列表；让 FFmpeg 自主管理 representation encoding
- 不在 redirect 或跨域 segment 时动态补发另一套 headers

已知限制：mpv `http-header-fields` 是本次媒体打开的全局字段，无法为跨域 HLS/DASH 的每个子请求建立
WebView 等价的逐域 Cookie policy。阶段 13 必须在设备矩阵中验证常见跨域 manifest；无法安全表达的
站点保持明确不支持，不引入代理。

### 4. 快速启动默认值

新安装候选合同：

| 设置 | 目标默认 |
| --- | --- |
| rendering profile | `fast` |
| Anime4K | `OFF` |
| 记忆 Anime4K | 关闭 |
| precise seek | 关闭 |
| seek preview | `LOCAL_ONLY` |
| network cache | 重新标定后的 balanced |
| initial cache pause | `cache-pause-initial=no` |
| underrun pause | `cache-pause=yes` |
| resume wait | 明确、经设备测试的短值 |
| network timeout | 明确有限值，初始候选 `30s` |
| routine diagnostics | 结构化 info/warn/error |

这些值不代表失败后自动切换。用户显式选择 High Quality、Anime4K、精确 seek 或详细诊断后，runtime
按该选择运行；发生错误时只报告，不自动改回其它配置。

现有 `PlayerSettings` 基础默认已经接近 `FAST / OFF / BALANCED`，但
`FRESH_INSTALL_PLAYER_SETTINGS` 覆盖了它。正式实现应删除重复且冲突的默认来源，使新安装只从一个
权威常量建立。

### 5. decoder backend 与 rendering profile 分离

新增两个独立概念：

```text
PlayerDecoderBackend
  SOFTWARE
  MEDIACODEC
  MEDIACODEC_COPY

PlayerRenderingProfile
  FAST
  DEFAULT
  HIGH_QUALITY
  LOW_LATENCY
  SW_FAST
```

规则：

- `hwdec` 只由 `PlayerDecoderBackend` 设置
- `profile` 只由 `PlayerRenderingProfile` 设置
- `hwdec-codecs=all` 只在明确启用硬解时设置
- runtime 读取 `hwdec-current`、video decoder、pixel format 和 codec profile，报告实际结果
- `hwdec-current` 的空值、`no` 和 `none` 统一表示没有活动硬解，不能在报告中形成两个软件解码状态
- codec profile 从当前选中视频轨道的 `track-list/N/codec-profile` 读取；固定 mpv 手册没有
  `video-params/codec-profile` 合同，不能继续读取一个不存在的属性
- `FILE_LOADED` 发送完整轨道列表和第一份媒体身份；后续稳定 `VIDEO_RECONFIG` 只读取轻量媒体身份，
  与上一份相同则不跨 Binder 重复发送，发生变化时通过独立 callback 更新 hwdec、pixel format 和
  codec profile，不重新运行完整缓存资格、不重建轨道列表
- UI 不再把 profile 描述成硬解或软解
- 不提供 `auto` 作为静默改变 backend 的产品语义
- 默认 backend 必须通过 vivo Android 16 与至少一台其它 arm64 设备的矩阵后冻结

第一轮实现应先完成语义拆分和实际 decoder 观测，保持当前真实软件解码行为；硬解默认值在独立设备
里程碑中决定，不能凭 UI 文案或单台设备推断。

### 6. seek 状态机

普通 VOD 拖动：

1. 拖动期间只更新 UI draft、目标时间和章节
2. 松手只发送一次 `absolute+keyframes`
3. `PlayerSession` 只在成功发送当前 load 的显式用户 seek 后登记 pending user seek
4. 收到同一 load 的 `MPV_EVENT_SEEK` 且存在 pending user seek 时才进入用户可见 `SEEKING`
5. Surface/VO 重配置、解码器内部恢复或其它未对应用户命令的 `MPV_EVENT_SEEK` 只记录为内部 seek，
   不改变进度条、自动隐藏或手势层的用户 seeking 状态
6. 收到后续 `MPV_EVENT_PLAYBACK_RESTART` 后清理同一 load 的 pending user seek 并标记完成
7. 若 runtime 返回明确错误，显示失败并保持 mpv 的真实位置

用户显式开启精确 seek：

```text
absolute+exact
hr-seek=yes
hr-seek-framedrop=yes
```

不再使用 `hr-seek-framedrop=no`。精确模式仍只发送一个 seek，不先做 keyframe seek 再做 exact seek。

live/DVR：

- 读取 `seekable`、`partially-seekable` 和 `demuxer-cache-state.seekable-ranges`
- 有明确 DVR range 时，进度条映射到当前 range
- 无 seekable range 时禁用拖动并显示“直播”
- 不把未知 duration 伪造成零长度 VOD
- range 随 manifest 更新时保持用户 draft 与当前窗口一致

### 7. seek preview

用新枚举替换当前全局 boolean：

```text
OFF
LOCAL_ONLY
```

- 本地 seekable 文件可以继续使用 `grabThumbnailFast`
- HTTP/HTTPS、HLS、DASH 和其它网络媒体不调用该接口
- 在线拖动显示时间、章节、DVR/live 状态和 pending seek 状态
- 不为在线缩略图增加 header-aware 第二连接
- 不从主播放 core 跳到其它位置截帧，因为这会改变唯一会话

如果未来 mpv binding 提供不改变播放位置、复用同一 demux/cache 的正式接口，必须另开设计里程碑；
阶段 13 不预留隐式网络路径。

### 8. 唯一四档在线播放缓存策略

候选 UX 与状态模型比较：

| 方案 | 优点 | 失败模式 | 结论 |
| --- | --- | --- | --- |
| 三档普通缓存 + 独立完整缓存开关 | 两个维度可分别选择 | 产生六种组合、两个设置 owner；完整缓存需要解释继承哪个普通档位 | 拒绝 |
| 保留三档并由播放器自动决定是否全片缓存 | UI 最少 | 行为不可预测，用户无法知道磁盘占用和关闭是否释放 | 拒绝 |
| 单一四档策略 | 一个持久化值、一个 runtime owner、状态可解释 | 完整档必须内建稳定开播的基础缓存参数 | 采用 |

最终唯一 `PlayerNetworkCachePolicy` 为：

| 设置项 | persisted ID | 初始 forward | 初始 backward | 初始 `cache-secs` | 磁盘策略 |
| --- | --- | ---: | ---: | ---: | --- |
| 省流模式 | `compact` | 64 MiB | 32 MiB | 60s | 关闭 |
| 智能均衡 | `balanced` | 128 MiB | 64 MiB | 180s | 关闭；新安装默认 |
| 流畅优先 | `large` | 256 MiB | 128 MiB | 300s | 关闭 |
| 完整缓存 | `full_video` | 256 MiB | 128 MiB | 300s | request 开始时准备会话磁盘 cache，资格通过后扩展到全片 |

“省流模式”减少用户提前退出时的无效预读；它不改变视频码率。“完整缓存”内建“流畅优先”的基础
缓冲，因此不需要第二个档位共同决定参数。四档策略都在新媒体 request 创建时快照；设置变化不重载、
不重建、不中途删除当前媒体的 cache，下一 request 使用新策略。这样保持同一 request 只有一个
cache owner，并避免播放中改变磁盘生命周期。

正式实现为每个网络 request 显式设置并记录：

```text
cache=yes
cache-on-disk=no
cache-pause-initial=no
cache-pause=yes
cache-pause-wait=1.0
demuxer-max-bytes=<policy.forwardBytes>
demuxer-max-back-bytes=<policy.backwardBytes>
cache-secs=<policy.cacheSeconds>
```

`cache-pause-initial=no` 保证开始播放不等待预填；`cache-pause=yes` 只在真实 underrun 时暂停，
`cache-pause-wait=1.0` 使用固定 mpv 的默认恢复等待值，避免等待过长又立即再次 underrun。

本阶段不设置全局 `network-timeout`。固定 mpv 的该选项会跨协议生效，而当前没有按 HTTP/HTTPS、
RTSP 和其它输入分别验证的目标设备矩阵；为 HTTP 快速失败设置一个全局值会扩大到尚未验证的协议。
`stream-buffer-size` 同样保持固定 mpv 默认值，不能为了单个 MP4 改变所有协议的底层读缓冲。

明确不设置：

- FFmpeg reconnect 系列选项
- 全局 `network-timeout`
- 自动改变 transport/backend 的选项
- 全局缩小 `demuxer-lavf-probesize` 或 `analyzeduration`
- 全局 `profile=low-latency`
- 播放前 active probe
- `cache-pause-initial=yes`
- `demuxer-cache-wait=yes`

`multiple_requests`、短 seek 和协议专用 lavf 选项只有在固定测试资源证明收益、没有兼容性退化后，
才能作为单独的显式策略进入后续里程碑；本阶段默认不启用。

### 9. “完整缓存”策略

#### 设置与用户合同

“设置 → 视频播放器 → 窗口与在线”只保留一行“在线播放缓存”，选择面板固定显示四项：

```text
省流模式
智能均衡
流畅优先
完整缓存
```

“完整缓存”的说明固定为：

```text
对符合条件的 HTTP/HTTPS 直链点播边播边缓存整个文件；
会占用较多内部存储，关闭播放器或切换视频后释放；
直播、分段媒体、大小未知或空间不足时不会标记为整片完成。
```

持久化只保存 `PlayerSettings.networkCachePolicy=FULL_VIDEO`，并随唯一 `PlayerRuntimeConfig` 进入
`:player`。删除开发期 `fullVideoCacheEnabled` boolean、独立设置行和对应 key。该策略不创建下载
任务、不新增 URL、headers、Cookie、网络 client、媒体文件 owner 或播放 core。

#### 唯一会话磁盘 cache

选择 `FULL_VIDEO` 且启动前 app-private 存储剩余空间至少为 `1 GiB` 时，当前 mpv request 从一开始
使用：

```text
cache=yes
cache-on-disk=yes
demuxer-cache-dir=<noBackupFilesDir>/player/mpv-session-cache/<runtimeGeneration>/<loadGeneration>
demuxer-cache-unlink-files=immediate
demuxer-max-bytes=256 MiB
demuxer-max-back-bytes=128 MiB
cache-secs=300
```

目录必须在 `loadfile` 前创建、解析 canonical path、确认位于 `noBackupFilesDir`、可写且不是符号
链接。任一检查失败时，本次策略进入 `INELIGIBLE / PREPARATION_ERROR`，不依赖 mpv 对无效目录的
内部替代行为；当前 request 继续使用该策略固定的 256/128 MiB、300 秒内存播放缓冲。

选择 `demuxer-cache-unlink-files=immediate` 的原因：

- Android/Linux 支持打开后立即 unlink
- cache 仍由当前 mpv file descriptor 持有
- `:player` 正常退出或崩溃时内核关闭句柄并释放空间
- floating/fullscreen 转挂继续复用同一 core 和同一 cache
- 不留下可被误认为离线视频的命名媒体文件

下一 request、显式关闭播放器和 runtime 销毁都必须先关闭当前媒体，再删除空的 generation 目录；
进程启动时只清理该固定根下不属于当前 generation 的空目录或已关闭残留，不扫描其它应用目录。

#### 完整缓存资格

`FILE_LOADED` 后只使用同一 mpv request 已公开的属性判定。必须同时满足：

```text
demuxer-via-network=yes
file-format not in {hls, dash}
seekable=yes
partially-seekable=no
duration is finite and 0 < duration <= 14400 seconds
file-size is known and 0 < file-size <= 20 GiB
availableBytes >= file-size + max(1 GiB, ceil(file-size * 0.15))
```

固定 mpv 的稳定手册合同公开 `file-size`、`stream-end` 和 `demuxer-cache-state`，但不保证
`stream-start`；真机固定 runtime 也返回空值。资格不再依赖 `stream-start/stream-end`。空间门禁按
完整 `file-size` 加保留空间计算，不读取 cache-state 猜测已经占用的字节；这会略微保守，但能够在
状态 node 尚未稳定时仍给出确定、可验证的安全结论。

因此完整缓存正式支持的是可确认总大小的 direct HTTP/HTTPS VOD，例如 MP4/MOV、WebM/MKV、
MPEG-TS、FLV、AVI、Ogg、ASF/WMV 和 3GP，只要实际 demuxer、decoder、服务器 Range 和 seek
能力满足。

以下资源不会标记为完整缓存：

- HLS/DASH VOD：`file-size` 可能只代表 manifest，不能证明全部 segment 总大小
- HLS/DASH live、普通 live：没有有限 EOF
- `partially-seekable=yes` 的 DVR/滚动窗口
- 服务器不支持完整 seek 的 direct stream
- 时长、总字节或空间事实未知
- 超过四小时、超过 20 GiB 或可用空间门禁的资源

这些媒体进入 `INELIGIBLE`，关闭 `cache-on-disk` 并释放本 request 的会话目录；该 request 继续使用
`FULL_VIDEO` 自身固定的 256/128 MiB、300 秒基础播放缓冲，不切换成另一个策略。设置页和播放器状态
必须明确显示没有进入完整缓存，不能把“向前缓存较多”或“已经播放到结尾”写成“整个视频已缓存”。
启动前空间不足 `1 GiB` 时磁盘 cache 根本不创建。

#### 完整 cache 参数

通过资格检查后，`:player` 在同一 core、同一 load generation 内更新：

```text
cache-secs = ceil(durationSeconds) + 60
metadataBudget =
  clamp(
    roundUpTo16MiB(ceil(durationSeconds / 3600) * 64 MiB),
    128 MiB,
    256 MiB
  )
demuxer-max-bytes = metadataBudget
demuxer-max-back-bytes = metadataBudget
```

实际设置不得缩小 `FULL_VIDEO` 固定的基础 cache：

```text
demuxer-max-bytes =
  max(256 MiB, metadataBudget)
demuxer-max-back-bytes =
  max(128 MiB, metadataBudget)
```

固定 mpv 把 disk cache 的 packet data 写入磁盘，但 packet metadata 仍在内存；官方经验值约为每小时
`50 MB` metadata，因此使用 `64 MiB / 小时` 并限制到四小时。前向和后向都需要足够预算，才能在
播放开始时读到 EOF，并在播放接近结尾时仍保留 BOF。正式实现必须记录实际 player RSS，不能通过
`largeHeap` 扩大风险边界。

`cache-secs` 使用媒体时长加 60 秒的有限值；不使用 `inf`。固定 mpv 的该选项是范围
`0..DBL_MAX` 的 double，正无穷不属于合法配置，也无法作为可测试的产品合同。

#### 状态、进度和完成判据

新增有界 phase：

```text
DISABLED
PREPARING
INELIGIBLE
ACTIVE
COMPLETE
TERMINATED
```

另设可空 reason，不把“正在做什么”和“为什么未完成”混成一个字段：

```text
NONE
NOT_NETWORK
NOT_VIDEO
SEGMENTED_MANIFEST
NOT_FINITE
NOT_FULLY_SEEKABLE
SIZE_UNKNOWN
SIZE_LIMIT_REACHED
SPACE_INSUFFICIENT
PREPARATION_ERROR
STORAGE_FLOOR_REACHED
FILE_LIMIT_REACHED
```

另设 cache-state evidence：

```text
NOT_APPLICABLE
AVAILABLE
UNAVAILABLE
MALFORMED
```

`:player` 最多每 `1 Hz` 直接读取一次 `demuxer-cache-state`，禁止
`runCatching { ... }.getOrNull()` 吞掉 native/property 错误。属性返回空值时记录 `UNAVAILABLE`；
node 或必需字段类型错误时记录 `MALFORMED` 并在 evidence 变化时追加一条 warning；两者都不能形成
完成证据，也不能使普通播放失败。并把压缩结果并入已有
`PlayerRuntimePlaybackSnapshot`；250 ms 播放进度回调可重复最近一次 cache 状态，但不会以 250 ms
频率重新读取 node 或执行文件系统检查。当前跨进程字段为：

```text
phase
reason
rangeStartSeconds
rangeEndSeconds
fileCacheBytes
expectedFileBytes
active
complete
stateEvidence
```

不把完整 `seekable-ranges`、路径或 URL 传过 Binder。缓存百分比只有在唯一 range 从 BOF 连续延伸、
duration 有限时才可显示；多 range 或未知总量只显示可验证状态和已缓存到的时间，不用
`file-cache-bytes / file-size` 伪造进度。

完整缓存的唯一判据沿用 mpv 官方定义：

```text
bof-cached=yes
AND eof-cached=yes
AND seekable-ranges exactly one range
AND current media is finite and fully seekable
```

`fw-bytes` 只是当前位置之后的缓存字节，`file-cache-bytes` 只是当前磁盘 cache 的字节，都不能独立证明
完整。达到 `COMPLETE` 后，floating/fullscreen 交接不改变状态；新媒体必须建立新的 cache state。
对外展示的单一 range 起点允许把小于零的时间戳规范化为 `0`；内部 mpv 时间线和完成判据不被改写。

若完整缓存过程中空闲空间低于 `512 MiB` 硬性安全线，立即停止当前媒体、关闭 core 持有的 cache
文件并报告 `STORAGE_FLOOR_REACHED`；`file-cache-bytes` 超过 `20 GiB` 时同样停止并报告
`FILE_LIMIT_REACHED`。不重新加载媒体、不改写 URL，也不静默改变缓存配置。若有界 metadata 预算
不足以读到 EOF，`bof/eof/range` 完成条件不会成立，状态保持非完成；设备 RSS 与完成率矩阵继续作为
资格证据，不能从一个间接字节值伪造 `COMPLETE`。

VPN、Private DNS、代理或默认网络在缓存完成前变化时，只记录 network snapshot change 和后续首次
真实错误；不重连、不绑定物理网络。缓存完成后的离线 seek 验收必须证明跳到 10%/50%/90% 不再产生
网络读取，才能声称完整 cache 对网络中断有效。

### 10. 横竖屏自适应“拉伸画面”

`MpvPlayerEngine` 继续是唯一 native fit owner，并保存：

```text
currentVideoFitMode
currentSurfaceWidth
currentSurfaceHeight
```

`PlayerRuntimeService` 继续用现有 `remoteSurfaceGeneration` 拒绝旧 Surface 的 resize；只有当前
generation 的有效 `width > 0 && height > 0` 才进入 engine。

参数合同冻结为：

```text
FIT:
  panscan=0
  video-aspect-override=-1

CROP:
  video-aspect-override=-1
  panscan=1

STRETCH:
  panscan=0
  video-aspect-override=surfaceWidth / surfaceHeight
```

应用顺序：

1. `attachSurface` 保存有效几何并设置 `android-surface-size`
2. 立即按当前 fit mode 应用属性
3. `surfaceChanged` 更新几何；如果当前为 `STRETCH`，用新比例重新应用
4. 用户选择 `STRETCH` 时使用当前有效 Surface；几何尚未有效时只保存 mode，等待 attach/resize
5. `detachSurface` 清除几何，不猜 display size

横屏 `2696x1260` 使用约 `2.1397`，画面向左右填满实际 Surface；竖屏 `1260x2696` 使用约
`0.4674`，画面按竖屏 Surface 填满；浏览器悬浮 `1260x709` 使用约 `1.7772`。自由窗口、方形窗口和
折叠屏同样只按实际 Surface，不写死横屏、竖屏或 `16:9`。

不采用 `keepaspect=no`：固定 mpv 手册说明它在 fullscreen mode 会被忽略，并依赖窗口语义；
`video-aspect-override=<actual Surface ratio>` 与 Kiyori 已有 `android-surface-size` 和 Surface
generation 合同一致，行为更可验证。

固定 `2026-06-25` binding 的 `BaseMPVView` 在 Surface 销毁时正式执行
`vo=null -> force-window=no -> wid=0`，创建时恢复 `force-window=yes -> vo=gpu`。因此本轮不得凭
日志直接删除 VO 重建；保持既有 init/attach/detach 顺序，并把该过程产生的 mpv 内部 seek 与用户
seek 状态隔离。替换 binding 生命周期必须进入独立 native source-build 与目标设备里程碑。

该修复不得调用 `loadfile`、用户 seek、重建 core、改变 headers、cache、播放位置或 Surface lease。

### 11. 诊断日志等级

新增诊断模式：

```text
NORMAL
VERBOSE_CAPTURE
```

`NORMAL`：

- 保留 request、runtime、Surface、load、网络阶段、HTTP/TLS/transport 错误、file events、seek 和 decoder
- mpv 默认模块使用 warning/error
- 对能提供失败根因的 stream/ffmpeg 模块使用经过验证的最低必要等级
- 不回传每条无关 verbose

`VERBOSE_CAPTURE`：

- 由用户在播放器日志页显式开启
- 只作用于后续新 request
- 显示额外 IPC/性能成本提示
- 保持 2,000 条有界缓冲、脱敏和导出合同

降低日志量不能删除首个具体失败原因。结构化 failure evidence 独立于日志 UI 过滤器保存。

### 12. runtime 能力快照

runtime 在 `MPVLib.init()` 成功后只查询一次目标能力：

- required protocols：file、http、https、hls/dash 相关
- required demuxers：mov/mp4、matroska/webm、mpegts、hls、dash、flv、avi、ogg、asf 等
- required decoders：H.264、HEVC、VP9、AV1、MPEG-4 Part 2、MPEG-2、AAC、Opus、Vorbis、MP3 等
- hwdec methods：mediacodec、mediacodec-copy
- renderer：gpu/gpu-next、android/androidvk

实现严格读取 mpv 的 `mpv-version`、`ffmpeg-version`、`protocol-list`、
`demuxer-lavf-list` 和 `decoder-list`。`hwdec` 单独读取 `option-info/hwdec` Node map，再解析
可选的 `choices` 字段：固定 runtime 中 `hwdec` 是 string-list option，合法情况下可以没有
`choices`，因此缺少该字段不能中止 mpv 初始化。只有真实暴露 choices 时才投影为已确认支持/不支持；
字段缺失、父属性不可用或结构异常均投影为 `UNKNOWN` 并携带有界 evidence，结构异常同时写入 warning。
不把完整列表高频传过 Binder；`:player` 形成一次有界 `PlayerRuntimeCapabilitySnapshot` 并写入现有
有序诊断，包含：

```text
mpvVersion
ffmpegVersion
protocol booleans
demuxer booleans
decoder booleans
hwdec method AVAILABLE / UNAVAILABLE / UNKNOWN states
hwdec metadata evidence and option type
capability digest
```

capability digest 必须包含 hwdec evidence，使“choices 明确为空”和“runtime 不提供 choices
自省”产生不同摘要。支持矩阵只能声称“runtime 暴露的能力证据”。真实设备是否能解码某
profile/level、是否能稳定输出、是否能拖动仍由样本矩阵决定。

## 格式与播放能力合同

### 目标支持

| 类别 | 目标 |
| --- | --- |
| direct VOD | HTTPS/HTTP MP4/MOV、WebM/MKV、MPEG-TS、FLV、AVI、Ogg、ASF/WMV、3GP 等实际 demuxer 支持格式 |
| adaptive VOD | HLS VOD、DASH VOD |
| live | HLS live、DASH live；按实际 seekable/DVR range 展示 |
| authentication | User-Agent、Referer、Origin、Cookie、Authorization、站点自定义端到端头 |
| redirect | 有界 HTTP redirect；错误必须分类 |
| seek | Range VOD、manifest segment VOD、明确 DVR window |
| local | content/file SAF 与本地路径；保持当前 resolver owner |

### 明确不支持或需单独里程碑

| 形态 | 状态 |
| --- | --- |
| 普通网页 URL | 不支持 |
| `blob:` / MSE | 不支持 |
| WebRTC | 不支持 |
| Widevine/DRM | 不支持 |
| YouTube 等需要 yt-dlp 的页面 | 不支持 |
| 过期签名 URL | 不支持；显示 HTTP/transport 真实原因 |
| 只有 MIME、没有可执行 URL 的 API | 不支持 |
| 单个 `.m4s`、`.ts`、`init.mp4` fragment | 不作为独立视频候选 |
| 需要逐域动态 Cookie 注入的复杂跨域 manifest | 先设备验证；无法安全表达时明确不支持 |

## 分阶段实施计划

每个里程碑都必须独立审阅、测试和构建 Debug APK。不得并行启动多个 Gradle 构建；不得在一个里程碑
中夹带后续实现。

### M1：结构化加载时间线与 VPN 网络证据

影响范围：

- `PlayerNetworkSnapshot.kt`
- `PlayerRuntimeService.kt`
- `MpvPlayerEngine.kt`
- `PlayerDebugLogReport.kt`
- `PlayerRuntimeServiceAndroidTest.kt`
- 播放器架构、语义与验收文档

交付：

- 现有有序诊断流中的 runtime generation、event sequence、load command 与 mpv event 时间线
- 主进程报告时快照和 `:player` initial/change/load 被动网络快照
- VPN、Private DNS、proxy、IPv4/IPv6、active/effective transport 和 process binding 字段
- `250 ms` callback 合并与完整快照去重
- 导出报告新增脱敏 Network 区并引用有序 `PlayerNetwork` 日志

验收：

- 不发额外网络请求
- 不保存 IP、DNS 地址、proxy host、VPN 应用名、完整接口名
- 三份报告 fixture 可重建到 TCP_CONNECT 阶段
- 两进程网络快照不会成为第二网络 owner

变更撤销边界：只撤销新增 telemetry/snapshot 协议，不改变媒体 request、URL、headers 或播放行为。

### M2：结构化错误与 header ownership

影响范围：

- `MpvPlayerEngine.kt`
- `PlayerRuntimeProtocolPolicy.kt`
- `PlayerRuntimeProtocolPolicyTest.kt`
- `PlayerDebugLogBuffer.kt`
- `PlayerSession.kt`

交付：

- request-scoped failure classifier
- 具体错误优先于 `loading failed`
- transport/browser-owned header deny set
- header removed reason
- UI 可见错误包含阶段、分类和检查项

验收：

- 报告 A/B/C 均分类为 `TCP_CONNECTION_REFUSED`
- Range、Accept-Encoding、hop-by-hop、client hints 不进入 mpv
- Cookie、Referer、Origin、Authorization 和未知 `X-*` 认证字段保持
- 所有 header 值继续从日志、错误和报告中消失

变更撤销边界：header policy 和分类器可作为一个提交单元撤销，不保留旧/新双实现开关。

### M3：seek 状态机与在线 thumbnail 清理

影响范围：

- `PlayerModels.kt`
- `PlayerSettingsStore.kt`
- `PlayerSession.kt`
- `PlayerRuntimeConnection.kt`
- `PlayerRuntimeService.kt`
- `MpvPlayerEngine.kt`
- `PlayerControls.kt`
- `PlayerGestureLayer.kt`
- 对应 JVM/Android tests

交付：

- `MPV_EVENT_SEEK` / `PLAYBACK_RESTART` 回调
- pending seek 与完成状态
- 默认 keyframe seek
- exact seek 使用 framedrop
- `seekable` / `partially-seekable` / DVR ranges
- `PlayerSeekPreviewPolicy.OFF/LOCAL_ONLY`
- 删除网络 `grabThumbnailFast`

验收：

- 拖动期间不发送 seek
- 松手只发送一次
- UI 不在 command sent 时伪造完成
- live 无 DVR 时禁用拖动
- 网络媒体拖动不产生第二连接

变更撤销边界：seek reducer、UI draft 和 preview policy 同步撤销，不恢复网络 thumbnail 路径。

### M4：快速启动默认值、普通 cache、完整缓存和诊断模式

影响范围：

- `PlayerModels.kt`
- `PlayerSettingsStore.kt`
- `PlayerRuntimeModels.kt`
- `IPlayerRuntimeCallback.aidl`
- `PlayerRuntimeConnection.kt`
- `PlayerRuntimeService.kt`
- `KiyoriPlayerSettingsPage.kt`
- `MpvPlayerEngine.kt`
- `PlayerDebugLogBuffer.kt`
- `PlayerDebugLogReport.kt`
- `CONTEXT.md`
- `PLAYER_ARCHITECTURE.md`

交付：

- 单一新安装默认来源
- fast / Anime4K OFF / non-precise / local-only preview
- 在线播放缓存收敛为 64/32/60、128/64/180、256/128/300 与 `FULL_VIDEO`
  256/128/300 四档单 owner 策略
- HTTP/HTTPS 显式 cache-pause；全局 `network-timeout` 保持未设置
- 删除开发期独立完整缓存开关；“完整缓存”作为第四档且仅对下一媒体 request 生效
- app-private、immediate-unlink、同 request 的临时磁盘 cache
- direct VOD 资格、空间、metadata、四小时/20 GiB、安全线和完成状态
- `demuxer-cache-state` 有界 snapshot 与完成判据
- NORMAL / VERBOSE_CAPTURE
- 启动阶段耗时显示

验收：

- 新安装与开发期已有设置语义按未发布产品合同完成清理
- 正常日志仍能保留 DNS/TCP/TLS/HTTP 具体失败
- 无自动重试或配置自动改变
- 完整缓存不创建下载任务、第二 request、离线文件或第二状态 owner
- live、HLS/DASH 大小未知、不可 seek、空间不足不显示 `COMPLETE`
- 只有 `bof-cached + eof-cached + 单一 range` 才显示完成
- 完成后同会话离线 seek 到 10%/50%/90% 不产生网络读取
- 关闭播放器、切换媒体和 runtime exit 后不残留会话媒体文件
- 受控样本的 load-to-playback-ready 不劣于变更前基线

变更撤销边界：默认值、普通 cache、完整缓存 state machine 和日志等级按独立配置单元审阅；
完整缓存整体撤销时仍保留普通 cache，不保留半接线 AIDL 字段或临时目录。

### M5：横竖屏自适应“拉伸画面”

影响范围：

- `MpvPlayerEngine.kt`
- `PlayerRuntimeService.kt`
- `PlayerSession.kt`
- `PlayerSurfaceView.kt`
- `PlayerControls.kt`
- 对应 JVM/Android tests

交付：

- engine 保存当前 fit mode 与有效 Surface 宽高
- `STRETCH` 使用当前 Surface ratio
- attach/resize/转挂时按当前 generation 重新应用
- 删除 display metrics 作为视频拉伸几何来源

验收：

- `2696x1260` 横屏向左右填满
- `1260x2696` 竖屏按竖屏 Surface 填满
- `1260x709` 悬浮播放器按实际 `16:9` Surface 填满
- square/foldable/freeform 使用实际 ratio
- 旧 generation resize 不影响新 Surface
- 切换 FIT/STRETCH/CROP 与旋转不增加 load generation
- 不改变位置、headers、cache、Surface lease 或 WebView

变更撤销边界：只撤销 engine 的 Surface 几何 fit 逻辑与对应测试，不改变既有 fit mode 枚举和 UI 顺序。

### M6：decoder backend 与 rendering profile 正名

影响范围：

- `PlayerModels.kt`
- `PlayerSettingsStore.kt`
- `PlayerRuntimeModels.kt`
- `MpvPlayerEngine.kt`
- `KiyoriPlayerSettingsPage.kt`
- `PlayerDebugLogReport.kt`
- `CONTEXT.md`

交付：

- decoder backend 与 profile 拆分
- `hwdec` 明确设置
- 实际 decoder/hwdec/pixel format 观测
- 删除错误 UI 描述
- 设备矩阵后冻结新安装 backend

验收：

- 每个 UI 选项与一个真实 mpv property/profile 对应
- runtime 日志显示请求 backend 和实际 backend
- 不使用 product-level `auto`
- MediaCodec 失败不自动改变 backend

变更撤销边界：backend/profile schema 作为一个完整迁移单元撤销，不保留旧枚举兼容分支。

### M7：runtime capability 与支持矩阵

影响范围：

- `MpvPlayerEngine.kt`
- `PlayerRuntimeProtocolPolicy.kt`
- `PlayerRuntimeProtocolPolicyTest.kt`
- `PlayerRuntimeServiceAndroidTest.kt`
- 播放器开发文档和测试资源

交付：

- 有界 capability snapshot
- 协议/demuxer/decoder/hwdec 支持表
- 本文媒体/错误/设备矩阵作为 fixture catalog，不提交有版权或敏感的真实媒体
- APK 静态审计继续验证固定 native closure

验收：

- 静态候选格式与 runtime 能力差异可见
- UI/文档不宣称 runtime 没有的能力
- DRM、blob/MSE、网页 URL 边界保持明确

### M8：固定 native closure 必需安全刷新（`verification_pending`）

影响范围：

- `ci/script/prepare_mpv_player_dependency.py`
- `ci/test/test_android_dependencies.py`
- `app/build.gradle.kts`
- `app/libs/mpv-player-arm64.aar`
- `NOTICE`
- `docs/legal/`
- `docs/doc-src/dev-core/PLAYER_NATIVE_STACK.md`
- native source/patch 可复现材料

候选输入：

```text
mpv=2339eb727
FFmpeg=n8.1.2
Mbed TLS=3.6.7
RSA-PSS=enabled
curl=disabled
NDK/Clang=current fixed toolchain
```

交付：

- 可复现的 arm64 thin AAR
- 新 input/output SHA-256 与完整 source identity
- Mbed TLS 3.6.7 许可证、安全说明和 build markers
- 不包含 `MBEDTLS_X509_RSASSA_PSS_SUPPORT` 禁用补丁
- mpv/FFmpeg 行为和 Java/JNI API 保持当前合同

验收：

- 不采用 `2026-07-18` release 的 dirty mpv 和全局 shader 修改
- `MPVLib`、`MPVNode`、`Utils` 现有 Kiyori 调用全部编译并通过测试
- namespaced FFmpeg SONAME/`DT_NEEDED` 闭包完整
- `libc++_shared.so` 唯一且 C++ 符号合同通过
- arm64 native 全部 `PT_LOAD >= 0x4000`
- TLS marker、Mbed TLS 3.6.7 与 RSA-PSS 能力可静态核验
- direct MP4、HLS/DASH、headers、redirect、seek、Surface 和 crash isolation 全部回归
- 本批三份 TCP refused 仍正确分类，不能因版本变化改写首因

变更撤销边界：native input、hash、准备脚本、测试、NOTICE 和法律材料作为一个原子单元处理。

官方 Mbed TLS `3.6.7` release notes 已于 `2026-08-16` 复核：该版本修复五项安全问题并建议更新，
其中多项 affected-version 范围包含当前 `3.6.6`。因此 M8 是必须完成的 3.6 LTS 安全维护，不是仅因
版本号较新的可选优化。当前仓库没有上述 source-build 输入，本轮只能完成资格审计，不修改 AAR、
准备脚本、NOTICE 或法律材料，不把固定 `3.6.6` payload 写成已经升级，也不通过未知二进制破坏
可追溯性或 RSA-PSS 能力。

### M9：FFmpeg 9.0.1 与新 mpv closure 资格评估（`verification_pending`）

影响范围：

- M8 的全部 native 输入、准备、验证和法律文件
- 固定 mpv candidate commit 与对应 source archive
- FFmpeg `n9.0.1` source、配置和 library version markers
- 候选 AAR 与对照 Debug APK
- 播放 fixture catalog、性能和 RSS 报告

第一份 qualification build 固定：

```text
mpv=<audited non-dirty commit compatible with FFmpeg n9.0.1>
reference=mpv-android 2026-08-11 / mpv f4d13e1
FFmpeg=n9.0.1
Mbed TLS=3.6.7
RSA-PSS=enabled
curl=disabled
NDK=r29
```

交付：

- current M8 closure 与 FFmpeg 9.0.1 candidate 的逐项能力、ABI、体积、启动、RSS 和样本对照
- libavutil/libavcodec/libavformat `60/62/62 -> 61/63/63` 迁移审计
- mpv source/API 变化清单和 Kiyori 实际受影响调用
- “采用”或“资格未通过”的单一证据结论

验收：

- candidate 可全新重建，不复制官方 APK/AAR 中不可审计的混合 patch
- Java/JNI、namespaced FFmpeg、C++ runtime、TLS、16 KiB 和许可证全部通过
- direct、HLS、DASH、header、redirect、seek、完整缓存、MediaCodec、Surface、Anime4K 和 crash
  isolation 对照无退化
- load-to-playback-ready、seek、RSS、AAR/APK 体积和网络错误分类有同机 P50/P95
- curl 未进入第一份 candidate；若未来单独评估，必须形成另一份唯一变量对照
- 没有收益或出现回归时，记录资格未通过，不把 candidate 打入产品

M9 是构建期选择门禁，不产生运行时双版本、自动切换或第二播放路径。官方 FFmpeg 记录已于
`2026-08-16` 复核：`9.0.1` 是当前 9.0 稳定维护版；它仍是跨 libav major 的独立资格候选，不是
能够替换当前 closure 中单个 `.so` 的就地补丁。

### M10：目标设备现场验收

只有 M1 至 M9 的本地实现、测试、Debug APK、静态审计和 closure 选择结论通过后，才进入设备矩阵。
设备结果不能被
自动重试掩盖，必须记录第一次真实失败。

## 自动验证矩阵

### JVM

- 三份脱敏报告 fixture 的错误分类
- 错误 precedence：具体原因不被 `loading failed` 覆盖
- header 大小写、移除原因、CR/LF、未知认证字段
- telemetry 单调事件、旧 generation、迟到 callback
- VPN/network snapshot 脱敏
- keyframe/exact seek command
- `MPV_EVENT_SEEK -> PLAYBACK_RESTART`
- live DVR range reducer
- network preview 禁止、本地 preview 允许
- settings 的单一默认来源
- decoder backend/profile 映射
- full cache 设置持久化和 runtime config 映射
- full cache direct/live/HLS/DASH/size/space/duration 资格 reducer
- `bof-cached` / `eof-cached` / 单一 range 完成判据
- metadata budget、空间保留和硬性安全线边界值
- cache snapshot 去重、`1 Hz` 上限、旧 load generation 丢弃
- FIT/STRETCH/CROP 属性序列
- 横屏、竖屏、悬浮、方形和自由窗口 Surface ratio
- 无有效尺寸时不读取 display metrics、不写入猜测比例

### Android instrumentation

- `ConnectivityManager` 快照在无网络、Wi-Fi、Cellular、VPN 条件下不崩溃
- `getBoundNetworkForProcess()` 与 active network 比较
- LinkProperties 缺字段、OEM 返回空值和 API 26 至当前 target 的兼容
- AIDL parcel 大小和 event ordering
- `noBackupFilesDir` cache root canonical path、符号链接拒绝和不可写目录错误
- `StatFs` 空间门禁和 session generation 清理
- `demuxer-cache-unlink-files=immediate` 的同进程生命周期
- `surfaceChanged` 只允许当前 generation 更新 STRETCH
- `content://` 本地播放、seek 和本地 thumbnail
- runtime crash isolation 继续保持

### 构建与 APK

- `ci.test.test_player_assets`
- 播放器相关 JVM suites
- `:app:compileDebugKotlin`
- `compileDebugAndroidTestKotlin`
- formal readiness
- Markdown links
- `git diff --check`
- 串行 `:app:assembleDebug --no-daemon --console=plain`
- `verifyDebugPlayerRuntimePackaging`
- AAR/APK hash、member list、DT_NEEDED、TLS marker、ABI、ELF `PT_LOAD >= 0x4000`
- M8：mpv `2339eb727`、FFmpeg `n8.1.2`、Mbed TLS `3.6.7` 和 RSA-PSS 能力标记
- M9：FFmpeg `n9.0.1`、libav 主版本、固定 mpv candidate、Mbed TLS `3.6.7` 和 RSA-PSS
  能力标记
- current/candidate 两份 AAR 与 APK 的独立 native closure 对照
- 禁止 `2026-07-18` dirty mpv、全局 shader patch 和 RSA-PSS 禁用补丁进入固定输入
- package/version、唯一 launcher、Debug V2 signature、16 KiB ZIP alignment

## 设备与网络验收矩阵

### 网络变量

每组只改变一个变量，并使用同一时间窗口、同一 URL、同一媒体 request：

| 组 | 条件 |
| --- | --- |
| N1 | Wi-Fi，无 VPN，无系统代理，Private DNS 自动 |
| N2 | Cellular，无 VPN |
| N3 | 全设备 VPN，明确包含 Kiyori |
| N4 | 分应用 VPN，明确包含 Kiyori |
| N5 | 分应用 VPN，明确排除 Kiyori |
| N6 | VPN 开启，系统代理/PAC absent |
| N7 | VPN 关闭，系统代理/PAC present |
| N8 | Private DNS 自动 |
| N9 | Private DNS 指定 provider |
| N10 | 过滤/广告拦截 VPN 开启 |
| N11 | 过滤/广告拦截 VPN 关闭 |
| N12 | IPv4-only 可控网络 |
| N13 | IPv6-capable / dual-stack 网络 |
| N14 | Data Saver / background restriction |
| N15 | 同一 full-cache request 缓存未完成时改变 VPN 状态 |
| N16 | 同一 full-cache request 显示完成后改变 VPN 状态 |

每次记录：

- WebView/系统浏览器是否能读取同一 URL
- Kiyori mpv 的 active/bound network 快照
- VPN transport、validated、proxy、Private DNS、IPv4/IPv6 default route
- 首个 native 网络错误
- 是否到达 HTTP/TLS/FILE_LOADED/PLAYBACK_RESTART
- full cache state、完成前后是否产生新的网络读取

禁止仅记录“开/关 VPN后好了”。必须说明哪一个网络事实发生变化。

### 媒体变量

| 组 | 样本 |
| --- | --- |
| M1 | 本地 MP4，H.264/AAC |
| M2 | 直接 HTTPS MP4，支持 Range |
| M3 | 直接 HTTPS MP4，不支持 Range |
| M4 | `moov` 在文件头的 MP4 |
| M5 | `moov` 在文件尾的 MP4 |
| M6 | HLS VOD |
| M7 | HLS live，无 DVR |
| M8 | HLS live，有 DVR |
| M9 | DASH VOD |
| M10 | DASH live |
| M11 | Cookie + Referer |
| M12 | Origin + 自定义认证头 |
| M13 | 同源 redirect |
| M14 | 跨源 redirect |
| M15 | manifest 与 segment 跨域 |
| M16 | HLS AES-128；只验证 runtime 实际能力 |
| M17 | WebM/VP9 |
| M18 | MKV/H.264、多音轨、内封字幕 |
| M19 | HEVC |
| M20 | AV1 |
| M21 | 已知大小、完整可 seek、两小时以内的 direct VOD |
| M22 | 已知大小、三至四小时 direct VOD |
| M23 | 超过四小时或超过 20 GiB 的 direct VOD |
| M24 | file-size 未知的 direct VOD |
| M25 | HLS/DASH VOD 的完整缓存负向资格 |

DRM、blob/MSE、WebRTC 和普通网页 URL 用负向样本验证明确拒绝，不作为播放成功目标。

### 错误变量

- DNS 不可解析
- TCP refused
- connect timeout
- network unreachable
- TLS CA/hostname failure
- HTTP 401、403、404、416、429、5xx
- redirect 超限
- unsupported content encoding
- manifest 无效
- segment 缺失
- unsupported codec
- decoder init failure
- Surface detach/attach 期间发生网络错误
- `:player` native exit

每个错误都必须：

1. UI 显示正确类别与阶段
2. 导出报告含第一具体证据
3. 不泄露 URL query、header value、Cookie、Authorization、IP 或私人路径
4. 不自动重试或改变 backend

### seek 验收

- 本地 VOD keyframe seek
- 本地 VOD exact seek
- HTTPS Range VOD 前跳/后跳
- HLS VOD 跨 segment seek
- DASH VOD seek
- HLS live 无 DVR 禁用
- HLS/DASH DVR range 内 seek
- seek 期间 buffering
- 连续快速拖动只提交最后一次 release
- floating/fullscreen 交接中不重复 seek、不增加 load generation
- seek 前后 headers 保持，Range 仍由 FFmpeg 生成

### 完整缓存验收

- 省流模式、智能均衡和流畅优先：`cache-on-disk=no`，分别使用固定的基础 cache 参数
- 完整缓存、空间小于 1 GiB：不启动磁盘完整缓存，明确显示空间条件
- direct VOD 通过资格后边播边缓存，不等待全文件后才开始播放
- HLS/DASH VOD、live、不可 seek、大小未知均不显示 `COMPLETE`
- 完成前 `bof/eof/range` 任一条件缺失都保持非完成状态
- 显示完成后关闭 Wi-Fi/VPN 数据路径，跳到 10%/50%/90% 均能恢复播放且无网络请求
- floating -> fullscreen -> floating 的 Surface 转挂保持同一 cache generation
- 新媒体使用新 generation，旧 cache 状态和目录不污染新 request
- 正常关闭、runtime crash 和 storage-floor stop 后空间释放
- 有界 metadata 预算不足时保持非完成，诊断保留 phase/reason/range/字节事实，不伪造完整

### 画面比例验收

- 横屏 `2696x1260` 的 STRETCH 设置约 `2.1397`
- 竖屏 `1260x2696` 的 STRETCH 设置约 `0.4674`
- 悬浮 `1260x709` 的 STRETCH 设置约 `1.7772`
- FIT 恢复 `video-aspect-override=-1 / panscan=0`
- CROP 恢复 `video-aspect-override=-1 / panscan=1`
- 旋转、自由窗口 resize 和 floating/fullscreen 转挂都不调用 `loadfile`

## 性能指标

所有指标按 request、媒体类型、网络条件和设备分别统计 P50/P95，不混合成一个平均值：

```text
open -> runtime ready
runtime ready -> Surface attach ACK
Surface ACK -> load sent
load sent -> network open
network open -> FILE_LOADED
FILE_LOADED -> PLAYBACK_RESTART
load sent -> PLAYBACK_RESTART
seek sent -> MPV_EVENT_SEEK
MPV_EVENT_SEEK -> PLAYBACK_RESTART
buffering count / total buffering duration
normal diagnostic events per minute
player process RSS before/after cache
full cache file bytes / metadata RSS / completion time
Surface resize -> new aspect property applied
```

首轮成功条件：

- 不增加任何播放前网络请求
- 常态日志事件量显著低于 `all=v`，同时错误分类完整
- fast / Anime4K OFF 相比当前新安装默认值，受控样本的启动 P50/P95 不退化
- keyframe seek 的完成时间显著优于当前 exact + no framedrop
- 网络拖动不产生 thumbnail 第二连接
- balanced cache 不触发目标设备内存压力或 runtime death
- full cache 启用不增加首播前网络 request，播放开始时间不等待整个文件
- 两小时与四小时样本的 native RSS、磁盘占用和完成时间在记录的门限内
- Surface resize 到新 ratio 生效不超过一个 UI frame 和一个串行 runtime command 周期
- 任何性能结论都附测试设备、网络、样本和阶段时间，不使用主观“更快”

## 文档同步

正式实现时必须同步：

- `CONTEXT.md`：唯一默认值、decoder/profile、seek、普通/full cache、Surface fit、network snapshot、
  diagnostic mode
- `docs/doc-src/dev-core/PLAYER_ARCHITECTURE.md`：新事件、错误、seek、cache state、Surface geometry
  和 network evidence
- `docs/doc-src/dev-core/PLAYER_NATIVE_STACK.md`：M8/M9 固定 AAR、FFmpeg major、Mbed TLS 3.6.7、
  RSA-PSS、closure 选择结论和 APK 证据
- `NOTICE` 与法律材料：native source identity、hash、版本和许可证
- `KiyoriPlayerSettingsPage`：“在线播放缓存”唯一四档选择及每档用户说明
- 本文每个里程碑的本地/设备状态

不得把本方案中的计划提前写成 `README.md` 已有功能。

## 本地实施与验证证据

本方案起始于 `2026-08-15`，当前最终收口会话日期为 `2026-08-16`。下面先记录当前源码已完成的
本地验证和最终产物证据。更早的 APK 观察点不代表当前源码。

代码与自动检查：

- `ci.test.test_player_assets`：`6/6`
- `2026-08-16 14:46 +08:00` 的 vivo Android 16 报告确认固定 runtime 已完成 `MPVLib.init()`，
  但旧实现把合法缺失的 `option-info/hwdec/choices` 当作必需数组，导致 capability 诊断在
  Surface attach 和 `loadfile` 前中止初始化。当前源码改为解析 `option-info/hwdec` 父 Node map，
  以三态 hwdec 证据区分已确认空列表与不可自省状态；必需版本、协议、demuxer、decoder 合同仍严格。
  新 APK 的 runtime ready、Surface attach、loadfile 和真实播放仍需目标设备复测
- 本轮播放器策略/协议/设置定向 JVM：`61/61`，`BUILD SUCCESSFUL in 1m 16s`
- 完整 `:app:testDebugUnitTest`：`1310/1310`，失败、错误和跳过均为 `0`，
  `BUILD SUCCESSFUL in 3m 15s`
- `:app:compileDebugAndroidTestKotlin`：`BUILD SUCCESSFUL in 1m 33s`，`146` 个任务
- 项目 Python `ci/test` 全量：`186/186`
- formal readiness：`PASS`；architecture：`phase=m03 / errors=[]`
- 工作树只读等价门禁：Markdown `306` 份、localization `7` 份、repo hygiene `45` 个变更文件，
  全部 `0` error；真实报告 URL、query、附件标识和签名片段反向扫描为 `0`
- 完整 `:app:lintDebug` 当前为 `4 errors / 29 warnings`，耗时 `10m 54s`。阶段 13 播放器新增/
  修改代码没有 Lint 条目；剩余四个 error 仅位于
  无当前 diff 的 `WebSessionHistorySheet.kt:399/408/437/450`，没有扩张 baseline、增加
  suppression 或禁用检查
- `git diff --check`：无 whitespace error；只保留连续工作树的 CRLF -> LF 提示
- 真实报告 URL、query、附件路径、Cookie/Authorization 值和网络地址反向扫描未进入本轮变更

最终构建与 APK：

- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 3m 54s`，`232 actionable tasks: 23 executed, 209 up-to-date`；
  唯一 Debug launcher 与构建内 player runtime packaging 通过
- 独立 `:app:verifyDebugPlayerRuntimePackaging`：
  `BUILD SUCCESSFUL in 47s`
- 当前 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-16 18:13:41 +08:00`，大小 `471063551` bytes，SHA-256
  `9A02093155A717EF5018CD6A41DE192A2497F810618F51D4F38DA8C14882CF2A`
- APK 为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`
- APK 使用 Android Debug V2 单 signer，证书 SHA-256
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`；
  V1/V3/V4 未启用，`zipalign -c -P 16 -v 4` 为 `Verification successful`
- APK 仅含 `arm64-v8a` 的 `51` 个 `.so`，basename 无重复；包含
  `assets/operit_shell_exec`，不包含 `libsudo.so`
- `51` 个 `.so` 加 shell launcher 共 `52` 个文件全部为 ELF64/AArch64；`160` 个 `PT_LOAD`
  的全局最小 alignment 为 `0x4000`，低于 `0x4000` 的数量为 `0`
- `app/libs/mpv-player-arm64.aar` 与 `app/libs/ffmpeg-kit-player-arm64.aar` 的 SHA-256 分别为
  `FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C` 和
  `1A30A94226BF2157927EC6EDBB20154F9A1C1C53580F59CF55EFE46DB87A5AB3`；
  AAR 到 APK 的 `10 + 9` 个播放器 native payload 字节差异为 `0`
- `libmpv.so` / `libplayer.so` 对正常名 FFmpeg 的 `DT_NEEDED` 为 `0`；版本化 FFmpeg 导入为
  `251 / 34`，唯一并集 `256`，缺失 `0`；C++ 导入唯一并集 `99`，缺失 `0`；
  `__from_chars_floating_point` float/double 导出为 `2`
- 该阶段 APK 静态确认 mpv `v0.41.0-dev-g2339eb727`、FFmpeg `n8.1.2`、
  `--enable-mbedtls`、Mbed TLS `3.6.6` 和 `mbedtls_ssl_handshake`；新增
  `PlayerRuntimeMediaIdentitySnapshot`、`PlayerFullVideoCacheStateEvidence`、
  `PlayerNetworkSnapshotObserver`、`PlayerRuntimeCapabilitySnapshot` 和
  `PlayerRuntimeHardwareDecoderEvidence` 的 DEX descriptor 存在。44 个 DEX 中同时存在
  `onMediaIdentityChanged`、`fullVideoCacheStateEvidence`、`FULL_VIDEO`、
  `demuxer-cache-state`、`hwdecEvidence=` 与 `option-info/hwdec`，不包含旧错误路径
  `option-info/hwdec/choices`

本轮没有安装 APK、调用真实媒体 URL、操作 ADB/MuMu/设备、提交或推送。真实 VPN、Private DNS、
代理、IPv4/IPv6、复杂站点、全片缓存完成/断网 seek、硬解兼容和横竖屏触控继续保持
`verification_pending`。该阶段封板时，Mbed TLS `3.6.7` 是必需安全刷新，FFmpeg `9.0.1` 是独立
重大升级资格候选；后续实施状态由
[`14_player_native_dependency_upgrade.md`](14_player_native_dependency_upgrade.md) 接管。Mbed TLS
`4.2.0` 仍是另一项 major 迁移，不属于当前 M8 closure。

## 风险与控制

| 风险 | 控制 |
| --- | --- |
| 日志降级后丢失根因 | 先建立结构化 failure evidence，再降低普通 verbose |
| network snapshot 泄露隐私 | 只保存存在性、能力布尔值和数量；反向扫描导出报告 |
| header 移除导致站点认证失败 | 保留未知端到端认证字段；用 authenticated fixtures 验证 |
| exact seek 仍慢 | 默认 keyframe；精确模式显式且 framedrop=yes |
| 在线 thumbnail 移除后 UI 信息减少 | 保留时间、章节、DVR 和 pending 状态，不创建新网络路径 |
| 硬解引入 vivo 驱动问题 | 先分离语义和观测；默认 backend 必须通过设备矩阵 |
| cache 过大触发内存压力 | 重新标定容量并记录 RSS；不以 `largeHeap` 代替约束 |
| disk cache 填满内部存储 | 资格前空间保留、20 GiB 上限、512 MiB 硬线与停止当前 request |
| `file-cache-bytes` 被误作完整证据 | 只接受 `bof-cached + eof-cached + 单一 range` |
| HLS/DASH manifest 大小被误作总大小 | 分段媒体不进入完整缓存资格 |
| session cache 被误作下载 | immediate unlink、noBackup 私有目录、关闭即释放、不提供离线入口 |
| VPN 诊断被误写成因果 | UI/报告分开“观察事实”“可能检查项”“未确认原因” |
| Surface 优化破坏历史约束 | 不改变 init -> attach ACK -> loadfile 顺序 |
| STRETCH 使用过期几何 | 只接受当前 Surface generation 的有效 width/height |
| Mbed TLS 3.6.7 source refresh 引入构建或能力退化 | M8 固定 mpv/FFmpeg、RSA-PSS enabled、curl disabled，并用 source/thin/APK 同一 closure 审计 |
| native refresh 缩小证书兼容性 | Mbed TLS 3.6.7、RSA-PSS enabled、拒绝 2026-07-18 workaround |
| native refresh 被误认作 TCP refused 修复 | 版本维护与现场网络根因分别验收 |
| 只替换 FFmpeg `.so` 造成 ABI 混合 | FFmpeg 9.0.1 必须重建 mpv、libplayer 和完整 closure |
| FFmpeg 9 与 curl 同时变化无法归因 | 第一份 M9 candidate 固定 `curl=disabled` |
| 因版本号较新直接采用 candidate | 必须有明确收益、全矩阵无退化和单一 closure 选择结论 |
| capability 列表过大 | 只传目标能力状态、hwdec evidence 和 digest，一次性发送 |
| 可选 hwdec choices 被误当成必需属性 | 读取父 Node map；缺失为 UNKNOWN，结构异常 warning，必需属性继续严格 |

## 完成定义

阶段 13 只有同时满足以下条件才能标记完成：

1. M1 至 M7 代码、测试、文档、Debug APK 和静态审计通过；M8/M9 有可复现输入时完成资格，
   缺少输入时明确保持 `verification_pending`
2. 三份现有报告能够稳定得到 `TCP_CONNECTION_REFUSED / TCP_CONNECT`
3. VPN、Private DNS、proxy、IPv4/IPv6 和 bound network 被动证据进入脱敏报告
4. 普通加载错误不再只显示 `loading failed`
5. 新安装默认值不再启用 High Quality + Anime4K A+ + exact/no-framedrop + network thumbnail
6. decoder backend 与 rendering profile 有独立真实 owner
7. 网络媒体拖动不再打开无 headers thumbnail 连接
8. VOD keyframe/exact seek 与 live DVR 状态机按真实 mpv events 工作
9. header ownership 通过 authenticated、redirect 和 Range 测试
10. 在线播放缓存是唯一四档策略、新安装默认 `BALANCED`；选择 `FULL_VIDEO` 时仍使用唯一 mpv
    request，完成证据真实且关闭后释放
11. live、HLS/DASH、大小未知、空间不足、文件上限和 metadata 预算不足不显示完整缓存完成
12. 横屏、竖屏、悬浮和自由窗口 STRETCH 全部使用当前 Surface ratio，且不增加 `loadfile`
13. runtime capability 与文档支持矩阵一致；hwdec UNKNOWN 不得冒充 confirmed unsupported，
    可选 choices 缺失不得中止 runtime 初始化
14. 阶段 13 封板的 mpv `2339eb727`、FFmpeg `n8.1.2`、Mbed TLS `3.6.6` closure 保留历史
    hash、member、TLS marker、ELF 和符号证据；当前 M8 选择与 M9 资格由阶段 14 接管
15. M8/M9 不下载、替换或伪造单个 ELF；只允许完整 source closure 给出采用或资格未通过结论
16. 最终 APK 始终只包含一个选定 closure；任何将来 refresh 都必须具备非 dirty source、完整
    source/license、RSA-PSS 能力和同机回归证据
17. 唯一 PlayerSession、`:player`、Browser Runtime、Surface lease 和 loadfile 不变量未退化
18. vivo Android 16 的 VPN/无 VPN、Wi-Fi/Cellular、direct MP4、HLS、seek、cache、
    floating/fullscreen 和横竖屏
    现场矩阵完成

如果本地实现、构建和静态审计完成，但设备矩阵或 native source-build 资格仍缺少输入，状态必须保持
`verification_pending`；如果设备证据仍只能确定 TCP refused 而不能确定 VPN/DNS/服务端来源，必须
保留该未知项，不能为了关闭任务选择一个未经证明的根因。

## 后续续接与现场门禁

后续执行者继续开发或进入现场矩阵前，必须重新确认：

- 当前 `main` / `origin/main` / HEAD 与工作树
- 并行任务是否又产生新改动
- `AGENTS.md`、formal readiness 六份入口、`CONTEXT.md` 和播放器架构文档
- 三份报告哈希与本文一致
- 阶段 13 固定 AAR hash、mpv/FFmpeg version 与本文历史证据一致；当前产品 identity 以阶段 14 为准
- `2026-07-18` AAR 静态对照结论保留为历史；M8 已由完整 source/build closure 选入，不再描述为缺失
- M9 的 candidate commit、FFmpeg 9.0.1、curl 隔离和采用门禁必须在独立 source closure 到位后重审
- full cache 数值、目录、安全线、完成判据和 Surface ratio 合同未被并行改动推翻
- 本轮授权是否仍禁止设备、真实 URL、提交和推送

后续改动必须从当前结构化网络和错误证据继续，不得直接以参数堆叠声称提高兼容性。M8 native refresh
已由阶段 14 的完整 source/build closure 实施；M9 必须以唯一选定的 M8 closure 为对照，只改变重大
native closure，不能把依赖变化与网络、cache、seek 或 Surface 行为混在同一个不可归因批次。目标
设备现场验收继续独立记录。
