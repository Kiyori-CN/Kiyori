# 播放器原生依赖升级与 closure 迁移

状态：`R4 LOCAL DONE / TARGET DEVICE VERIFICATION PENDING`

FFmpeg 三执行面、AI/内部调用边界、Binder 运行时、失败语义、Ubuntu 版本策略和后续开发门禁以
[Kiyori FFmpeg 架构与开发指南](../../doc-src/dev-core/FFMPEG_ARCHITECTURE.md) 为正式权威；
本文继续负责阶段进度、历史证据与未完成验收。

## 目标、范围与非目标

本阶段把播放器 native 依赖从“固定二进制 AAR 的裁剪器”升级为“可审计源码 closure 的构建与选择流程”，
最终交付同时更新两个进程隔离、命名互斥的 FFmpeg owner：

1. `:player`：固定 mpv `v0.41.0-dev-g2339eb727`、Mbed TLS `3.6.7`、RSA-PSS enabled、
   curl disabled，只选择 FFmpeg `n9.0.1` 的完整 namespaced closure。
2. `:ffmpeg`：固定 FFmpegKit maintained framework 与完整工具功能集合，使用 FFmpeg `n9.0.1`
   重建 normal-name FFmpeg、`libffmpegkit.so` 和 `libffmpegkit_abidetect.so` closure。主进程只通过
   非导出 Binder runtime 提交命令、取消请求并读取同 UID 私有结果，不直接加载或执行 FFmpegKit JNI。

本阶段不升级 mpv 快照，不直接采用 `Riteshp2001/mpvlibAndroid` 的 dirty release，不复制官方
APK/AAR 中无法从 Kiyori manifest 重建的 `.so`，不关闭 RSA-PSS，不启用 curl，不引入第二个播放器
runtime，不增加运行时版本切换。当前工作区已有的播放器代码、测试和文档改动属于既有用户工作，
本阶段只在其上增量补齐 native 供应链。

## 当前产品状态与最终目标

当前 `app/libs/` 已完成双 M9 exact-hash promotion：

```text
app/libs/mpv-player-arm64.aar
  mpv      = v0.41.0-dev-g2339eb727
  FFmpeg   = n9.0.1
  Mbed TLS = 3.6.7
  SHA-256  = F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4

app/libs/ffmpeg-kit-player-arm64.aar
  wrapper  = 8.1.7-kiyori-n9.0.1-r4
  FFmpeg   = n9.0.1
  OpenH264 = v2.6.0@652bdb7719f30b52b08e506645a7322ff1b2cc6f + locked runtime patches
  SHA-256  = 86D97CC0174FF44A8057899BEF7B8E66BD976E5CFA7BBA7D2A9FC819CB8EFCA7
```

播放器 M9 的 source 与最终 16 KiB-aligned thin/product 证据为：

```text
source AAR SHA-256 = 7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0
thin AAR SHA-256   = F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4
```

FFmpegKit M9 的 source 与最终 16 KiB-aligned thin/product 证据为：

```text
source AAR SHA-256 = DF332D8F2FECA7508541A2F20EDB348A3BFBC2AD5AE6EEFDEDF2889D679C96CC
thin AAR SHA-256   = 86D97CC0174FF44A8057899BEF7B8E66BD976E5CFA7BBA7D2A9FC819CB8EFCA7
```

当前产品让两套 closure 都报告 FFmpeg `n9.0.1`，且保持：

```text
mpv       = v0.41.0-dev-g2339eb727
Mbed TLS  = 3.6.7
ABI       = arm64-v8a
Android   = API 24+
NDK       = 29.0.14206865
PT_LOAD   >= 0x4000
RPATH     = absent
RUNPATH   = absent
```

新源码输入记录在 [`tools/player_native_build/closure_manifest.json`](../../../tools/player_native_build/closure_manifest.json)：

| 组件 | 固定源码身份 |
| --- | --- |
| mpv binding/build scripts | `Riteshp2001/mpvlibAndroid@168e0a5e43b37c85509050cddcb5eaddc2e313c0` |
| mpv | `mpv-player/mpv@2339eb72767517fc5a113283939f59076946fbc1` |
| Mbed TLS | `Mbed-TLS/mbedtls@068ff080b369adfac81509f9b57b2afabaf82dc5`，framework `dde0c4a0e448a0552f18817dcea633bb851fd288` |
| M8 FFmpeg | `FFmpeg/FFmpeg@n8.1.2`，peeled commit `38b88335f99e76ed89ff3c93f877fdefce736c13` |
| M9 FFmpeg | `FFmpeg/FFmpeg@n9.0.1`，peeled commit `bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa` |
| FFmpegKit framework | `ffmpegkit-maintained/ffmpeg@62b07bf097baf26b416c815aea514e05c9ad6d63` |

Manifest 中的 commit、tag、ABI、API level、RSA-PSS、curl、唯一选入 closure、qualified artifact 和
selected product 约束属于机器可读合同。FFmpegKit 的固定 framework、FFmpeg、外部库集合、overlay、
source-lock、toolchain 和输出合同记录在
[`tools/ffmpegkit_native_build/closure_manifest.json`](../../../tools/ffmpegkit_native_build/closure_manifest.json)
与同目录 `source_lock.json`；文档只解释决策和验收边界，不另建第二套版本来源。

## 实施顺序

### 1. 源码构建入口

`ci/script/build_player_native_closure.py` 生成隔离的 upstream build workspace：

- 固定 checkout binding、mpv、FFmpeg、Mbed TLS，并初始化 Mbed TLS framework 子模块；
- 使用 manifest 生成 `depinfo.sh`，不读取远端 branch 的当前状态作为版本；
- 通过 `fetch_player_native_source.py` 为上游脚本提供受限的源码流下载入口；
- 记录 transitive source checkout 到忽略的 `work/player-native-build/<profile>/source-lock.json`；
- 顺序构建 Mbed TLS、FFmpeg、`libplayer.so` 依赖、mpv 和 AAR；
- 只接受 arm64、Android API 24+、`PT_LOAD >= 0x4000` 的单一源构建输出。

这个入口只生成并审计 source AAR，不直接修改 product AAR。`prepare_mpv_player_dependency.py`
先在 `work/player-native-outputs/<profile>/` 生成 thin candidate；promotion 必须提供精确候选哈希，
并在复制前、`app/libs` 同目录临时文件和最终产品文件三个阶段执行统一 thin closure 审计。成对
promotion 合同只接受固定 `m9_ffmpeg_major_candidate` 及两份精确产品哈希，禁止单栈替换。
`work/` 只保存恢复和构建产物，不进入 Git。

### 2. M8 安全刷新基线

M8 已完成并保留为构建链、安全刷新和 major 对照基线：

- mpv source commit 仍为 `2339eb727...`；
- FFmpeg source commit 仍为 `n8.1.2`；
- Mbed TLS 为 `3.6.7`；
- RSA-PSS 配置保持启用，不使用 2026-07-18 release 的禁用修复；
- curl 保持 disabled；
- `libmpv.so`、`libplayer.so`、七个 namespaced FFmpeg ELF 和唯一 C++ runtime 同一批生成；
- 更新 `prepare_mpv_player_dependency.py`、Gradle hash/成员合同、NOTICE 和对应源码/许可证记录；
- M8 source/thin closure 审计曾通过并作为当时的本地 product AAR 完成播放器 JVM/AndroidTest、
  formal readiness、Gradle、Debug APK 和静态打包审计；它现已被双 M9 产品替代，设备与真实媒体
  验收继续独立保留。

M8 不再是本阶段最终交付版本；其历史产物、哈希和验证结果继续保留为可归因证据。

### 3. 播放器 M9 product closure

M9 不在 M8 上替换单个 `.so`。它已经从源码完整重建：

- 只改变 FFmpeg `n8.1.2 -> n9.0.1`；
- 重编 mpv、`libplayer.so` 和七个 namespaced FFmpeg ELF；
- 审计 `libavutil/libavcodec/libavformat` major 变化、符号版本、`DT_NEEDED`、JNI 和 C++ runtime；
- promotion 前后重复执行 exact member、ELF、version、TLS、RSA-PSS、symbol closure、
  `RPATH/RUNPATH` 与 16 KiB 审计；
- 主进程 FFmpegKit 9 closure 完成后，播放器 M9 与 FFmpegKit M9 已成对进入产品 AAR 和最终
  Gradle/APK 验证。

本地 M9 source/thin qualification 已完成：

- source AAR：`23380329` bytes，SHA-256
  `7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0`
- 最终 aligned thin/product：`50926119` bytes，SHA-256
  `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`
- 十个 native ELF 均为 AArch64，`PT_LOAD >= 0x4000`，`RPATH/RUNPATH` 为零；
  FFmpeg namespace 为 `.63/.63/.12/.63/.61/.7/.10`，AAR native payload offset 均为
  `0 mod 0x4000`
- 产品 AAR 与 aligned thin candidate 逐字节一致；M8 只保留为历史可归因基线
- 该结果证明源码闭包和静态 ABI/链接合同成立，不证明目标设备、真实网络、解码器、Surface 或性能矩阵

### 4. FFmpegKit 9 normal-name closure

`:ffmpeg` 运行时不能复用播放器的精简 FFmpeg ELF。它必须从固定 maintained framework 独立构建
完整工具栈：

- framework 固定为 `62b07bf097baf26b416c815aea514e05c9ad6d63`，FFmpeg 固定为
  `n9.0.1@bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`
- 使用 framework 的 SAF protocol patch，把 `av_set_saf_open`、`av_set_saf_close` 与
  `saf:` protocol 保持在 FFmpegKit JNI 合同内
- 在 JNI `cpp/fftools9/` 下保留 FFmpeg 9 官方目录层级，完整同步 decoder、encoder、
  scheduler、graph、textformat、resources、ffmpeg 和 ffprobe 源码；禁止继续局部修补 FFmpeg 8
  命令层来冒充 FFmpeg 9
- 只在 `main()`→`ffmpeg_execute()`/`ffprobe_execute()`、JNI 日志、thread-local session、
  cancellation、statistics/report、`setjmp/longjmp` 和完整 cleanup 等集成点偏离官方源码
- 保持现有 FFmpegKit Java/JNI API、请求级多 session 语义和 normal-name native namespace；
  `:ffmpeg` 以单一 FIFO worker 持有顶层 native execution，避免两个完整 `ffmpeg_execute()` 共享
  进程级回调与日志状态；FFmpeg 命令内部 worker 线程和独立 request/session/result 状态不受限制
- 完整 closure 保留当前 full 工具功能集合及 MediaCodec/zlib，不把播放器精简配置当成工具箱配置
- 使用 arm64/API 24/NDK r29 构建 `libffmpegkit.so`、`libffmpegkit_abidetect.so` 和七个
  normal-name FFmpeg ELF，审计 JNI exports、SAF symbols、undefined/provider closure、
  FFmpeg major、`DT_NEEDED`、`RPATH/RUNPATH` 与 `PT_LOAD`

本地 FFmpegKit 9 完整 closure 已完成：

- 完整 external closure、FFmpegKit JNI 与 Gradle archive/JVM 单元测试均构建通过；
- framework overlay 为 33 个文件，tree SHA-256
  `65BA3D32364BB1ECBD34951301C8F64D1823B925078057263DD12426378AE527`；
- framework patch 使用 Git top-level 加 `--directory=android-8.1-lts` 应用到真实构建副本，
  避免子目录 prefix 导致的 no-op 假阳性；OpenH264 helper 在 reset 后按锁定 patch SHA 重应用；
- r4 source AAR 为 `17101059` bytes，SHA-256
  `DF332D8F2FECA7508541A2F20EDB348A3BFBC2AD5AE6EEFDEDF2889D679C96CC`；
- r4 最终 aligned thin/product 为 `30133322` bytes，SHA-256
  `86D97CC0174FF44A8057899BEF7B8E66BD976E5CFA7BBA7D2A9FC819CB8EFCA7`；
- 九个 native ELF 为 AArch64，FFmpeg majors 为 `.63/.63/.12/.63/.61/.7/.10`，
  `PT_LOAD >= 0x4000`，`RPATH/RUNPATH` 为零，native payload offset 为 `0 mod 0x4000`；
- `libffmpegkit.so` 保持 Java/JNI、SAF、取消、统计、report、并发 session 所需导出，不导入
  `exit`、`_exit` 或 `quick_exit`。

## Closure 与打包合同

最终 APK 同时包含两个进程隔离、native basename 互斥的 closure：

```text
:ffmpeg : FFmpegKit normal libav*.so
:player : libmpv.so + libplayer.so + libmp*.so + one libc++_shared.so
```

必须满足：

- 只有 arm64-v8a；
- `libmpv.so` 和 `libplayer.so` 不依赖 normal-name FFmpeg；
- 七个 `libmp*.so` 的 SONAME 与 `DT_NEEDED` 等长改名保持一致；
- FFmpegKit 的七个 normal-name FFmpeg ELF、`libffmpegkit.so` 和
  `libffmpegkit_abidetect.so` 全部来自同一 FFmpeg 9 source closure；
- FFmpeg symbols、C++ symbols、`__from_chars_floating_point`、TLS markers 和 RSA-PSS 能力均能从同一 closure
  提供；
- 所有 native ELF 的 `PT_LOAD` alignment 不低于 `0x4000`；
- Java/JNI API 仍提供 Kiyori 当前调用的 `MPVLib`、`MPVNode`、`Utils` 和 `libplayer`；
- `cacert.pem`、许可证、源码身份、构建 flags 和输出 SHA-256 可追溯；
- 没有 `pickFirst`、`exclude`、同名覆盖、第二 runtime 或运行时回退。

## 2026-08-17 FFmpegKit 运行时加固

### 已确认事实

- 本轮开始时 `app/libs/ffmpeg-kit-player-arm64.aar` 是 wrapper `8.1.7-kiyori-n9.0.1-r3`、
  FFmpeg `n9.0.1`、OpenH264 `v2.6.0`；现场问题不是 FFmpeg major 或编码器缺失。当前选入产品已是
  r4，同一 FFmpeg/OpenH264 closure 仅增加 Binder 根因补丁与 wrapper/runtime 合同修复。
- 工具箱使用 Compose `rememberCoroutineScope()` 直接调用同步 `AIToolHandler.executeTool()`，
  最终进入 `FFmpegKit.execute()` 同步 JNI；这是界面卡死的确定性调用链。
- 原实现中 AI、MNN、媒体信息和浏览器 M3U8 合并即使从后台线程进入，仍在主进程加载 normal-name
  FFmpegKit closure；native fatal signal 会终止主界面进程。当前实现已迁移到非导出 `:ffmpeg`。
- 新现场已提供原始能力边界：`ffmpeg_info` 的 `-codecs` 与 FFprobe 媒体探测成功；音频提取、
  720p 转码、`-c copy`、`-t 1 -c copy` 均在输出文件建立后、首个数据包写入前终止独立
  `:ffmpeg` 进程。客户端只在 Binder death 路径生成“FFmpeg 运行时进程已终止”，普通 FFmpeg
  非零返回码和连接超时均不会产生该错误。
- 原始日志中的 `-1414549496` 对应把 shell 管道错误传入 `ffmpeg_execute` 后产生的
  `Unrecognized option 'iE'`，不是进程死亡码；真正的进程死亡没有 FFmpeg return code，而是
  `FFmpegRuntimeProcessDiedException`。`ffmpeg_execute` 接收 FFmpeg 参数，不是 shell；工具提示现已
  明确禁止管道、重定向和命令链，但不按单个字符拦截合法滤镜表达式。
- `+pgo/+bolt/+lto/+mlgo` 来自 Android Clang 身份字符串；固定 FFmpeg 配置只明确包含
  `--enable-lto`。当前没有证据支持 BOLT/MLGO/PGO 组合、NEON 汇编或 wrapper 结构体不兼容是本次
  首包前进程死亡根因，也不通过禁用优化、回退 FFmpeg 核心或切换 Ubuntu 执行通道规避问题。
- FFmpeg `n9.0.1` 的 `fftools/ffmpeg.c` 在输入/输出打开后、进入 `transcode()` 前无条件调用
  `android_binder_threadpool_init_if_required()`；`-codecs` 与 FFprobe 不进入该路径。API 35+
  实现随后调用 `ABinderProcess_setThreadPoolMaxThreadCount(1)`。Android 应用进程在
  `FFmpegRuntimeService` 创建前已经启动 Binder threadpool，而 AOSP `ProcessState` 明确禁止在
  threadpool 启动后把最大线程数从当前值缩小并执行 fatal。因此，该调用可以完整解释现场的
  Android 15/16 条件、成功/失败命令边界、`0` 字节输出和 Binder process death。
- 本现场仍缺少 logcat、tombstone 与 native backtrace。首份 FFmpeg 报告只写 Android arm64；
  后续用户报告已明确设备为 vivo V2507A、Android 16、arm64/aarch64，因此 Android 16 是本次现场
  的直接条件证据，但不能把尚未取得的 fatal 文案、tombstone 或 native backtrace伪造成已取得。
- host ASan/UBSan 矩阵已确认两个独立根因：FFmpeg constrained-baseline profile 值 `578` 不能直接
  传给 OpenH264 `EProfileIdc`，以及 OpenH264 `v2.3.1` 的空 `BsFlush` 执行 32 位移位。固定源码
  patch 后，`320x240 / 1280x720 × default/1/2/4 threads` 共 `20/20` 编码、非零 MP4 与回读通过。
- OpenH264 `v2.6.0@652bdb7719f30b52b08e506645a7322ff1b2cc6f` 的提交日期早于上游
  `BsFlush` 修复 `40555ec684ec0fede3948c8f272c04d88d05189d`，且前者是后者祖先，因此 r4 必须继续保留
  BsFlush patch、FFmpeg profile 映射 patch 与 framework 重应用 patch。r3 的同合同 host
  ASan/UBSan 编码与完整回读矩阵再次为 `20/20 PASS`。

### 运行时合同

1. 新增非导出 `:ffmpeg` Binder service，normal-name FFmpegKit closure 只允许在该进程初始化和执行。
2. 主进程请求使用稳定 request ID；`:ffmpeg` 为 FFmpeg/FFprobe session 记录 session ID、终态、
   duration、return code、fail stack 和最后一份 statistics。
3. FFmpeg 日志按 request ID 写入同 UID app-private 文件。Binder 只传递有界元数据和日志文件身份，
   避免大日志触发 Binder transaction 上限；调用方在终态后读取完整日志。
4. Binder death 原子失败当前连接上的全部 pending 请求，并明确报告 native-process-death。当前命令
   不自动重启、不自动重试、不替换编码器、不降级；下一次用户显式执行才允许建立新进程连接。
5. 取消通过 request ID 定位当前 session 并调用 FFmpegKit session cancel；取消、完成与进程死亡只允许
   一个终态。
6. `:ffmpeg` 只允许一个顶层 FFmpeg/FFprobe native execution owner；并发请求保留各自
   request/session/log/statistics 状态并进入同一 FIFO worker。FFmpeg 自身的 codec/filter/scheduler
   内部线程继续按命令需要运行，但两个完整 `ffmpeg_execute()` 不在同一进程内并发。
7. 专用 worker 直接同步调用 `FFmpegKitConfig.ffmpegExecute()` /
   `getMediaInformationExecute()`；不再把已经位于 worker 的请求再次提交到同一个 executor。
   `onRequestStarted` 紧邻真实 JNI 执行，取消仍由 Binder 线程直接命中当前 session。
8. FFmpegKit 内部线程产生的 `sessionId=0` 日志归属到当前唯一 native owner；发送终态和关闭日志前，
   有界等待当前 session 与 session `0` 的回调队列排空，避免尾部日志或内部线程诊断丢失。
9. FFmpeg `compat/android/binder.c` 必须先查询 `ABinderProcess_isThreadPoolStarted()`：已启动时保持
   Android 应用现有 Binder threadpool，不再次缩小或启动；仅在线程池尚未启动时设置上限并启动。
   MediaCodec 仍保持编译和运行时能力，不通过禁用 MediaCodec 规避问题。
10. AI 工具名、参数、`ToolResult`、浏览器下载 commit point、MNN 临时文件和媒体信息用户可见合同保持；
   工具箱在后台 dispatcher 等待结果，不阻塞 Compose 主线程。

### 实施与验收

- [DONE] 完成主线程调用链、全部直接 FFmpegKit/FFprobeKit/FFmpegKitConfig 调用点、Manifest owner、
  固定 OpenH264 与上游修复范围调查。
- [DONE] 建立 `IFFmpegRuntime` / callback / Parcelable 合同、`:ffmpeg` service、主进程连接、
  Binder death、取消、日志文件和 statistics 聚合。
- [DONE] 迁移 AI 三个 FFmpeg 工具、`FFmpegUtil`、MNN、MediaPool、文件媒体信息和浏览器 M3U8 合并。
- [DONE] 完成 host ASan/UBSan 根因矩阵、固定源码 r2 全量重建、source/thin qualified audit、成对
  promotion 与完整 `verifyPlayerNativeInputs`；没有手工替换单个 native 文件。
- [DONE] 在全新 `/home/kiyori/build/kiyori-ffmpegkit9-r3` 中升级 OpenH264 `v2.6.0`，保留四个
  锁定补丁，完成 source/thin qualified audit 与 paired promotion；修复 host 审计错误接受 WSL
  `llvm-readelf` 路径的问题，Windows 审计现在只接受 host `llvm-readelf.exe` 并在 workspace 操作前失败。
- [DONE] 完成 JVM/AndroidTest、architecture、formal readiness、Debug APK、DEX/native namespace、
  AAR→APK 与 ELF/16 KiB 本地静态审计。
- [DONE] 使用现场最小矩阵和 Android/AOSP/FFmpeg 固定源码收敛 API 35+ Binder threadpool fatal
  根因；host scheduler sanitizer 夹具的流复制、AAC 提取和 OpenH264 缩放转码为 `3/3 PASS`，
  排除 r3 scheduler bridge 自身足以在宿主环境复现首包前终止。
- [DONE] 新增 FFmpeg Binder threadpool 固定源码补丁，建立顶层串行 native owner、同步 worker
  执行、session `0` 日志归属与终态前 callback drain；升级 wrapper 为 r4。
- [DONE] 在全新 `/home/kiyori/build/kiyori-ffmpegkit9-r4` workspace 重建 r4 source/thin AAR，
  完成 qualified audit 与 exact-hash paired promotion。source 为 `17101059` bytes /
  `DF332D8F2FECA7508541A2F20EDB348A3BFBC2AD5AE6EEFDEDF2889D679C96CC`，thin/product 为
  `30133322` bytes / `86D97CC0174FF44A8057899BEF7B8E66BD976E5CFA7BBA7D2A9FC819CB8EFCA7`。
- [DONE] r4 Python 合同 `46/46`、`verifyPlayerNativeInputs`、FFmpeg runtime/MediaPool 定向 JVM 与
  AndroidTest Kotlin 编译通过。
- [DONE] 新增正式 FFmpeg 架构与开发指南，明确 Ubuntu、`:ffmpeg`、`:player` 三执行面，
  Android 双 native closure、r3/r4/OpenH264 证据边界、AI 工具合同、构建/验收和 Ubuntu
  `n9.0.1` 独立升级门禁。
- [DONE] 完成其余文档/架构哈希同步、formal readiness、最终 Debug APK、签名/16 KiB 对齐、
  product AAR closure 与独立 APK/ELF/AAR→APK 静态审计。
- [PENDING] 在目标设备使用原始 `-c copy`、音频提取和 720p 转码命令复测实际执行、取消、排队、
  Binder death 与用户可见错误路径。

## 验证门禁

顺序从高信号到高风险：

1. `closure_manifest.json` schema、commit、tag、RSA-PSS/curl/ABI 合同；
2. 播放器 M9 source-lock、source/thin AAR、version/TLS/RSA-PSS、native symbol 与 exact-hash
   promotion 审计；
3. FFmpegKit 9 的完整 `fftools9` 窄编译、固定 source-lock、完整外部库构建、source/thin AAR 与
   exact-hash promotion 审计；
4. 两套 native `DT_NEEDED`、versioned FFmpeg imports/exports、C++/JNI/SAF symbol closure、
   ABI、`RPATH/RUNPATH` 和 `PT_LOAD`；
5. `ci.test.test_android_dependencies`、`ci.test.test_player_assets`、播放器与 FFmpegKit JVM suites、
   AndroidTest 编译、formal readiness、fresh clone、architecture、Markdown/link/diff；
6. `verifyPlayerNativeInputs`、`verifyDebugPlayerRuntimePackaging` 与串行
   `./gradlew :app:assembleDebug --no-daemon --console=plain`；
7. 独立 APK/ELF/DEX/签名/16 KiB/AAR→APK 字节一致审计和目标设备矩阵。

最终 source closure 统一使用已安装的 WSL2 Ubuntu、NDK r29 与 SDK 35 构建环境。项目脚本必须显式
接收工具链路径并验证 revision；主机环境不是隐式版本来源。

## 当前状态与完成定义

播放器 M8/M9 使用固定 NDK r29、Android SDK、MSYS2 Autotools、完整 WinLibs host compiler 和固定源码
identity 完成全链构建；FFmpegKit M9 使用固定 WSL2 Ubuntu-22.04、NDK r29、Android SDK、framework、
FFmpeg 与 external source-lock 完成全链构建。Windows host 的部分工具链解包问题在构建前通过标准头
门禁暴露并修复；`.S` 规则、
FFmpeg response file、长 header 安装、libass 并发、
Lua 参数转换、Shaderc 短路径、mpv Android sourceSet 和 Meson host RUNPATH 问题均在生成 workspace
阶段以保护性失败方式修复；没有关闭功能、复制未知二进制或事后修改产品 ELF。source AAR、thin
candidate 和选入后的 product AAR 已通过统一 closure 审计。

只有同时满足以下条件，阶段 14 的本地实现才能标记 `LOCAL DONE`：

1. [DONE] M8 安全刷新基线和播放器 M9 source/thin closure 均完成固定源码构建与审计；
2. [DONE] FFmpegKit 9 完整命令层通过窄编译，并固化可复现完整 closure 构建、审计和
   exact-hash promotion 合同；
3. [DONE] 播放器 M9 与 FFmpegKit M9 成对 promotion 到产品 AAR；两份产品与最终 aligned
   candidate 逐字节一致，`verifyPlayerNativeInputs` 已通过；
4. [DONE] r3 Python/JVM/AndroidTest/native input、formal readiness、architecture、最终 Debug APK
   和独立静态审计已按 r3 产品 AAR 重新执行并通过；
5. [DONE] r4 Binder 根因修复、串行 native owner、callback drain、source/thin qualified
   audit、paired promotion、定向 Python/JVM/AndroidTest、正式 FFmpeg 架构文档、formal
   readiness、最终 Debug APK 和独立静态审计均已完成；
6. [PENDING] 真机 direct/HLS/DASH、TLS、seek、cache、Surface、硬解、FFmpeg transcode 和 crash
   isolation 单独验收。

当前 r4 双 M9 Debug APK 为 `486200460` bytes，SHA-256
`14CBE55B2BF1FC11B769D9E14267F474E41C3EF40FC115210E7A7A0CB6CC28C6`，文件时间为
`2026-08-17 19:48:41 +08:00`。本轮规定构建重新验证为 `BUILD SUCCESSFUL in 59s`，
`232` 个任务中 `19` 个 executed、`213` 个 up-to-date；`verifyPlayerNativeInputs`、唯一 launcher
和 `verifyDebugPlayerRuntimePackaging` 均通过。包名/版本/SDK 为
`com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，Android Debug V2 单 signer 与
`zipalign -c -P 16 -v 4` 通过。APK 仅含 arm64-v8a：51 个 `.so` basename 零重复，加 shell
launcher 共 52 个 ELF64/AArch64，153 个 `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`。
播放器 10 个和
FFmpegKit 9 个 native payload 与产品 AAR 逐字节一致；两套 FFmpeg 都是 `n9.0.1`，FFmpeg 8
markers/majors、normal/namespaced 交叉依赖和相关 19 个 ELF 的 `RPATH/RUNPATH` 均为 0；
`44` 个 DEX、r4 wrapper marker 与 `ABinderProcess_isThreadPoolStarted` patch marker 均存在。
全 APK 汇总另观察到 `libonnxruntime.so` 与 `libsherpa-mnn-jni.so` 两个非播放器/FFmpegKit owner
带有 `RPATH/RUNPATH`；它们不属于本阶段 19 个 closure ELF，本阶段未改动，后续由各自 native
owner 单独处理。

包含 r3 / OpenH264 `v2.6.0` 的上一份双 M9 Debug APK 为 `486200429` bytes，SHA-256
`A6B3D11B47BB8B9A87F0A0B696C5129845C434E959CDE7EF8A0B0B98A3D4DBFF`，只保留为现场归因前的
历史构建证据。r2 APK 继续保留为更早的根因修复历史基线。

`471063551` bytes、SHA-256
`6656ABC96C32A0E74FD00098478C2ACBA866EC272447F20A8CE3AAA395F6FA43` 的 Debug APK 只保留为 M8 +
FFmpegKit n8.1.2 的历史构建证据。

阶段 14 在设备或用户验收缺失时保持 `verification_pending`。
