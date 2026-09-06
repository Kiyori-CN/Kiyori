# 播放器架构

Kiyori 只有一套播放器运行时。全屏、Browser 悬浮、外部视频、设置、媒体候选与下载动作都调用既有所有者，不创建第二个 Browser、播放器内核或下载数据库。

## 参考来源与取舍

| 固定参考 | 采用内容 | 不采用内容 |
| --- | --- | --- |
| `kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766` | 视觉与候选展示 | 在多个所有者间转移可变引擎、重新加载远程媒体、页面暂停/恢复钩子 |
| `mpv-android-anime4k@32f5f16988c1b2d5979eef692695bdef7232b7eb` | 经验证的 mpv 选项、着色器、精确 seek 与有界缓存 | 关闭 TLS 验证、静默尝试多个解码策略、第二媒体库或下载存储 |
| `hikerView@5de8809049e4710471f9f42642e54550ecf5dbe3` | 原 URL、请求头、Range 与 HLS 证据 | 独立代理/缓存体系；Browser 已有下载所有者，mpv 已有缓存所有者 |

捕获的 Range 是某次浏览器传输证据，不是可以重放到播放器的固定请求身份。Kiyori 自己的应用代理桥接见 [网络路由](../contracts/network_proxy.md)。

## 进程与所有权

| 所有者 | 职责 |
| --- | --- |
| 主进程 `PlayerSession` | 活动请求、展示状态、播放快照、显式队列、章节、预览、Surface lease；不构造 mpv、不打开媒体 descriptor |
| 主进程 transport resolver / `PlayerMediaStreamBridge` | HTTP(S) 路由与 loopback 传输 |
| 非导出 `:player` 的 `PlayerRuntimeService` | 一个 HandlerThread、MpvPlayerEngine、PlayerMediaResolver、content descriptor 与远端 Surface wrapper |
| `MpvPlayerEngine` | 唯一可导入 `MPVLib / MPVNode` 的生产源文件，管理所有 mpv/JNI 调用 |
| `PlayerSettingsStore` | 播放器偏好，不由设置页面直接写 mpv property |

oneway AIDL 消息携带 runtime generation、command ID 与单调 event sequence。`FILE_LOADED` 发布轨道/容器 metadata，只有 `PLAYBACK_RESTART` 才标记实际输出就绪。

### 预览提取

独立单线程 thumbnail executor 仅对非网络媒体直接调用 `grabThumbnailFast`。网络视频只有同一 demuxer 已证明 `FULL_VIDEO / COMPLETE` 后，才通过 `dump-cache` 导出小片段到 `cacheDir/player-seek-preview`，把本地片段交给提取器；普通 HTTP(S) URL 不开启无请求头的独立连接。

最多保留一个活动提取和一个最新 pending，每秒两个位置桶，AIDL 返回最大 320px Bitmap。过期、活动、关闭及异常退出残留都按规范私有目录清理。

## 设置应用与目录

- 初始化与 load 先应用 broad rendering profile，再应用独立解码、seek 等属性，使专用设置保持最终所有权。
- 初始化记录已应用值，首 load 不重复写相同属性。更换 profile 使包括 shaders 的属性缓存失效，再应用专用值。
- 活跃设置可更新渲染、解码、seek、字幕、音量、shader；网络缓存按请求快照，播放中不能替换缓存所有者。
- 设置分组依次为播放/队列、手势/进度、画面/Anime4K、音频/字幕、保存/下载、窗口/在线。
- 截图与视频目录是可选 SAF tree。未单独配置时，动作执行时解析实际 Browser 下载目录，不复制第二份设置。
- 单独视频 tree 冻结到请求并使用既有内部引擎，因为系统 DownloadManager 不支持任意 SAF tree；直链 staging 完成后移动，M3U8 包留在应用下载目录。
- 释放持久 tree 权限前同时检查 Browser 设置、两个播放器目录与所有保留任务。
- 重力旋转使用 `FULL_SENSOR`，关闭后恢复默认 sensor-landscape；开启时手动旋转禁用并显示“自动”。横屏 Anime4K 控件固定左下宽度，不漂移到中央播放控制。

## 展示与 Surface 状态机

展示状态只有 `BROWSER_ONLY / FLOATING_PLAYER / FULLSCREEN_PLAYER`。

一个新 request ID 最多执行一次 `loadfile`。悬浮与全屏切换保留同一请求、进度、时长、pending seek、暂停/速度、完整缓存状态、load/runtime generation、PID、内核和 demuxer 缓存；只解绑旧 Surface 并绑定新代际。owner token 防止旧 Surface 的迟到销毁影响新 Surface。

悬浮 → 全屏 → 悬浮 → 关闭不调用 WebView loadUrl/reload、页面重建、重新嗅探或 JavaScript 媒体控制。网页自己的媒体继续独立运行。关闭立即投影 `BROWSER_ONLY`，原生解绑和关闭随后结束。

### 自动悬浮的文档身份

WebSession 持有 credentialDocumentToken、应用导航 pending-start token、最近 onPageStarted URL 与自动悬浮 consumption token。接受候选时记录文档身份与手动/自动来源；自动启动要求页面完全加载且候选属于同一 token。

关闭、自然完成或全屏 CLOSE 不重新消费旧文档候选；手动播放仍可用。导航前轮换 token 并清候选，匹配的 onPageStarted 消费 pending 后才接受完成回调，后续回调还需匹配最近 URL。

`onReceivedSslError` 没有主帧身份，可能早于 onPageStarted，只负责取消无效证书；文档 SSL/loading 状态由匹配主帧的 onReceivedError 持有。

### Surface 与适配

固定 mpvlibAndroid 生命周期：detach 写 `vo=null / force-window=no` 并释放 native window，attach 恢复 VO 与 `force-window=yes`。没有可复现 native 源码制品证据时不替换该顺序。

FIT 重置 aspect override 与 panscan，CROP 使用 panscan，STRETCH 使用当前已接受 Surface 的宽高比；resize 重应用 STRETCH，不用显示器尺寸猜测实际窗口。

### Seek 与反馈

拖动在 UI 保留草稿，抬起提交一次，取消不提交。当前 runtime/load 接受命令后 PlayerSession 立即投影目标，旧周期进度不能覆盖。

一个 load 最多一个 seek 在途。后续动作合并为最新目标，匹配 `MPV_EVENT_SEEK → MPV_EVENT_PLAYBACK_RESTART` 完成后再提交；Surface/VO 内部 seek 只记录诊断，不进入用户 seeking 或消费未来目标。新媒体、关闭、死亡和错误清理 pending。

长按加速与恢复反馈各显示一秒，不写速度记忆。准备指示延迟 160ms，避免快速首帧闪烁。完整手势契约见 [媒体与下载](../contracts/media_downloads.md)。

## 媒体请求

- 保留原网络 URL 和观察身份；`:player` 写 `http-header-fields` 前不区分大小写移除 Range、Accept-Encoding、hop-by-hop、Sec-CH-UA 与 Sec-Fetch-*。
- Origin、UA、Referer、Cookie、Accept 和未知 end-to-end 认证字段存在时保留；活动 byte offset、编码、连接 framing 与 seek Range 由 mpv/FFmpeg 管理。
- content URI 只打开一个只读 ParcelFileDescriptor，生命周期与请求一致；file URI 解析原路径。
- 外部 ACTION_VIEW 创建新请求，Activity 重建复用原 ID。本地视频仅对同目录、规范化系列名一致的条目建立自然数字排序队列；网络默认单项，除非调用方明确给出有序队列。
- MIME、后缀是证据，真实 video DOM 高于误导音频后缀。blob、MSE、WebRTC、DRM 仅为不可执行线索。
- FILE_LOADED 发布格式、视频/音频 codec、视频轨数、hwdec、像素格式和所选 codec-profile。稳定 VIDEO_RECONFIG 只刷新轻量身份，相同快照不重发，不重建轨列表或重做缓存资格。
- 空白、no、none 的 hwdec-current 表示无活动硬解。实际 demux/轨道是诊断权威，不能由 URL 推断。

## 诊断

`PlayerDebugLogBuffer` 是唯一全屏日志视图所有者。新媒体清旧段，主进程记录 session/command/Surface；引擎在初始化前设置 `msg-level=all=warn,ffmpeg=info,demux=info`，原生日志经有序 AIDL 返回，不逐 250ms 记录进度。

- 上限 2,000 条，记录丢弃数量；追加时赋 sequence 与 topics，UI 有界采样 revision、倒序懒加载，过滤不重分类全缓存。
- 对话框固定头、过滤与底栏，日志区使用剩余高度；清空二次点击确认，复制/导出按时间正序，输出到 `Download/Kiyori/exports`。
- 保留所需 scheme、host、port、path shape；移除 query 值、header 值、Cookie、Authorization、标题与私有本地路径。
- 报告包含应用/设备版本、generation/PID、展示、Surface lease、解码、Anime4K、轨道与可见错误。
- 播放器在串行 Handler 注册一次网络回调，250ms 合并突发，只发布变化事实；每 load 记录请求开始快照，导出记录主进程当前快照。
- 区分 active 与 process-bound/effective 网络，记录 transport 类型、验证/计费/门户/后台限制、Private DNS 有无、IPv4/IPv6 DNS 与路由数量、absent/static/PAC 类型；不记录 IP、DNS 名、代理地址、接口名或 network handle，也不绕过 VPN。
- 请求诊断记录头数量、字段名及 Range 是否交给 mpv，不记录值。DNS/TCP/TLS/HTTP 原生证据优先于泛化 loading failed；END_FILE 使用字符串 reason 与可选 file_error。

### 能力探测

初始化成功后只查询一次必需的 mpv-version、ffmpeg-version、protocol-list、demuxer-lavf-list、decoder-list。hwdec 从 `option-info/hwdec` Node 读取，choices 可选；缺失时如实表示 unknown，结构错误额外告警，不把未知解释为已确认空列表。摘要保留该证据，完整列表不经 Binder 传输。

## 在线缓存

所有策略显式使用 `cache=yes / cache-pause-initial=no / cache-pause=yes / cache-pause-wait=1.0`。

| 策略 | 前向 | 后向 | 时间 | 会话磁盘缓存 |
| --- | ---: | ---: | ---: | --- |
| COMPACT / 省流模式 | 64 MiB | 32 MiB | 60s | 无 |
| BALANCED / 智能均衡 | 128 MiB | 64 MiB | 180s | 无，新安装默认 |
| LARGE / 流畅优先 | 256 MiB | 128 MiB | 300s | 无 |
| FULL_VIDEO / 完整缓存 | 256 MiB | 128 MiB | 初始 300s | loadfile 前准备 |

### 完整缓存资格与完成

同一 mpv 请求使用私有、打开即 unlink 的会话缓存。FILE_LOADED 后须同时满足：网络 demuxer、有真实视频轨、实际格式非 HLS/DASH、有限时长不超过四小时、完全可 seek、file-size 为正且不超过 20 GiB、可用空间至少 `file-size + max(1 GiB, ceil(file-size * 0.15))`。

资格不读取 stream-start/end、URL 后缀或 demuxer-cache-state。不合格时关闭磁盘缓存、只清理已验证会话目录并记录原因；保留该策略自身 256/128 MiB、300s 基础缓冲，不更换策略。

激活后 cache-secs 为时长加 60s，packet metadata 预算按时长限制在 128..256 MiB 且不缩减原基础值。demuxer-cache-state 最多 1Hz，区分 AVAILABLE/UNAVAILABLE/MALFORMED，后两者不是完成证据。

实际 file-cache-bytes 独立受 20 GiB 上限约束；剩余空间最多每五秒检查一次，512 MiB 为硬停止条件。只有 `bof-cached=yes + eof-cached=yes + 恰好一个 seekable range` 能首次证明 COMPLETE；同会话后续短暂未知或不完整快照不能撤销已成立事实。

替换、关闭、销毁和下次初始化只清理规范 `noBackupFilesDir/player/mpv-session-cache`，拒绝越界或 symlink；预览片段同样限制在 `cacheDir/player-seek-preview`。缓存不是离线库。

## Native 与错误传播

mpv AAR 是显式 Gradle 输入，构建前验证 classes.jar 与完整 native 集，构建后扫描 DEX 中的 binding 与 engine。精确成员与符号见 [native 栈](PLAYER_NATIVE_STACK.md)。

每个 JNI 入口将 LinkageError 转为 MpvRuntimeException 并显示命令失败；native 信号退出由 Binder death 与 ApplicationExitInfo 诊断。主进程和 WebView 不在播放器进程内，不自动换内核或重连。

自然完成读取 eof-reached，loop-file 保持关闭，由 PlayerSession 决定队列下一项与末项动作。END_FILE 也可能因替换/停止产生，不能直接视为自然完成。
