---
document_type: implementation-plan
status: verification_pending
last_updated: 2026-08-17
---

# FFmpeg 运行时完善计划

## 1. 目标与状态

本专项把 Kiyori 的三条 FFmpeg 执行面、Android `:ffmpeg` Binder runtime、AI/ToolPkg API、
应用内部消费者、native closure、构建门禁、诊断、测试和文档收敛为可长期维护的开发基线。

当前状态：

- Android FFmpegKit 与 mpv closure 已固定在 FFmpeg `n9.0.1`；
- FFmpegKit wrapper 为 `8.1.7-kiyori-n9.0.1-r4`；
- r3 首包前进程死亡的高置信 Binder 根因已由 r4 源码 patch 修复；
- 本专项的 API/runtime/调用方实现已经完成；
- 完整 Python、九模块 Gradle、AndroidTest 编译、Lint、WebChat、ToolPkg、formal readiness、
  fresh-clone、architecture、差异卫生和双 Android native AAR closure 审计已经通过；
- Debug APK 构建、签名、ZIP/ELF 16 KiB、ABI、payload 和 namespace 静态审计已经通过；
- Markdown 与最终 Git 候选树审计已经通过；本文件不自证其所在提交的远端状态，提交/推送结果
  必须在交付时通过 local/tracking/remote 实时对账；
- vivo V2507A / Android 16 上的实际 r4 转码、取消、process death 和播放器矩阵仍为
  `verification_pending`。

长期架构权威是
[FFmpeg 架构与开发指南](../../doc-src/dev-core/FFMPEG_ARCHITECTURE.md)。本文件只记录本专项的
实施状态、验收矩阵和后续门禁，不复制第二套架构定义。

## 2. 非目标与禁止项

本专项不实施：

- FFmpeg 命令失败后的自动重试；
- 自动切换 encoder、codec、container 或参数；
- Android FFmpegKit 与 Ubuntu `/usr/bin/ffmpeg` 之间的备用执行通道；
- 播放器和 FFmpegKit closure 之间的运行时切换；
- 非原子 copy/rename 提交；
- 单独替换 AAR 内某个 `.so`；
- 在未完成双 closure 重建和成对审计时升级 Android FFmpeg major；
- 未授权的 ADB、设备、模拟器、APK 安装、Release、发布或部署；
- 把宿主、静态或 Debug 构建结果表述为真机通过。

## 3. 三执行面边界

| 执行面 | 运行位置 | 所有者 | 用途 | 版本策略 |
| --- | --- | --- | --- | --- |
| Ubuntu CLI | Ubuntu/proot | 终端 rootfs/package manager | Shell 管道、脚本和通用 CLI | 当前 `6.1.1`；独立升级工程 |
| Android FFmpegKit | 非导出 `com.kiyori:ffmpeg` | `FFmpegRuntimeService` | AI 工具、探测、转码、remux | FFmpeg `n9.0.1` / wrapper r4 |
| Android mpv FFmpeg | 非导出 `com.kiyori:player` | `PlayerSession` + player runtime | 播放、网络、解码、渲染 | mpv + namespaced FFmpeg `n9.0.1` |

三条链互不自动切换。Ubuntu 成功不能证明 Android FFmpegKit 或播放器成功；播放器成功也不能
证明 `ffmpeg_execute`、`ffmpeg_convert` 或 FFprobe 成功。

## 4. 版本决策

### 4.1 Android

本轮不升级 Android FFmpeg core，原因：

- `:ffmpeg` 与 `:player` 已对齐 FFmpeg `n9.0.1` 固定 commit；
- 产品 AAR 已具有 exact hash、16 KiB、ABI、namespace、symbol closure 和 promotion 门禁；
- 当前剩余问题位于 API 合同、Binder 生命周期、日志、输出事务和调用方错误传播，不是 core
  版本落后；
- 无证据的依赖升级会扩大 native 风险，且不能替代目标设备验收。

### 4.2 Ubuntu

Ubuntu `/usr/bin/ffmpeg 6.1.1` 不属于 Android 产品 runtime。升级到 `n9.0.1` 必须单独建立：

1. Linux/aarch64 source lock 与机器可读 manifest；
2. glibc/compiler/binutils/external library 基线；
3. `ffmpeg` 和 `ffprobe` CLI 构建；
4. 可审计 `.deb` 和 package ownership；
5. rootfs install/upgrade/remove/reinstall 生命周期；
6. codec/filter/protocol/Shell/Android storage 回归；
7. GPL/LGPL、external codec、source-offer 和哈希审计。

Android AAR 不能复制到 `/usr/bin`，也不能手工覆盖 apt 管理文件。

## 5. 已确认缺陷与修复

### 5.1 简化转换 API 把 codec family 当作 encoder

旧问题：

- `h264`、`hevc`、`vp9`、`av1` 被直接传给 `-c:v`；
- `wav` 被放进 audio codec；
- 公开 `libx265`，但产品 manifest 明确禁止；
- ToolPkg、prompt、TypeScript 和 Kotlin 各自维护错误枚举。

修复：

- 删除 `video_codec`、`audio_codec`、`format`、`bitrate`；
- 当前只公开 `profile=h264_aac_mp4`；
- 固定 `libopenh264 + constrained_baseline + yuv420p + aac + mp4 + faststart`；
- 不支持值在 native 前拒绝；
- 不自动换 encoder。

### 5.2 字符串命令和路径二次解析

旧问题：

- 简化转换和 MNN 预处理手工拼接带引号命令；
- 路径中的空格、引号和特殊字符可能被再次解释；
- `FFmpegUtil` 只返回 Boolean，丢失 terminal state、return code 和日志。

修复：

- 内部调用统一使用 `executeArguments(List<String>)`；
- `FFmpegUtil` 返回完整 `FFmpegRuntimeResponse`；
- MNN、MediaPool、文件媒体信息和 M3U8 merge 显式记录结构化失败；
- `ffmpeg_execute` 保留高级字符串参数 API，但文档和 prompt 明确它不是 Shell。

### 5.3 输出污染、覆盖和提交点不清晰

旧问题：

- 简化转换直接写最终文件；
- 输出存在时没有稳定策略；
- 成功 return code 之外没有完整产物验证；
- M3U8 使用 `File.renameTo()`，原子性和失败语义不明确。

修复：

- 简化转换要求最终 `.mp4` 不存在；
- 同目录唯一 `.partial.mp4` 使用 `-n`；
- FFprobe 回读正时长、MP4、唯一 H.264 视频、Constrained Baseline、`yuv420p`、请求分辨率和
  最多一条 AAC 音频；
- 回读成功后才 `ATOMIC_MOVE`；
- 同一进程对相同目标使用固定锁条带，提交时再次检查目标；
- 文件系统不支持原子移动时明确失败；
- M3U8 merge 使用同一原子提交函数；
- 失败删除本次临时文件，不覆盖已有目标。

### 5.4 Binder payload 无显式上限

修复后的边界：

```text
command                  <= 65,536 chars
argument count           <= 1,024
single argument          <= 16,384 chars
total argument chars     <= 65,536
probe path               <= 4,096 chars
```

Parcelable 构造时即拒绝超限数据，避免 oversized Binder transaction 和无界内存使用。

### 5.5 排队请求取消仍可能进入执行

修复后的合同：

- session 尚未创建时取消，不发送 started，不进入 FFmpegKit native；
- 唯一终态为 `CANCELLED`；
- queued cancel 使用 `sessionId=0 / returnCode=255 / duration=0`；
- 不携带 native statistics、media information 或 runtime information；
- 活动请求仍使用真实 session ID 调用 FFmpegKit cancel；
- result model 强制 terminal state 与 return code 一致。

### 5.6 重复 request ID 可能截断已有日志

修复：

- request ID 在任何日志写入前预留；
- 日志使用独占文件创建，不截断已有文件；
- active 与 reserved 请求日志路径都受 retention 清理保护；
- 重复/历史 request ID 被拒绝，不触碰原日志。

### 5.7 日志无单请求和目录上限

修复后的合同：

```text
single request log       <= 4 MiB UTF-8 bytes, including marker
diagnostic excerpt       <= 16,384 chars
failure message          <= 4,096 chars
directory maximum age    = 48 hours
directory maximum files  = 32
directory maximum bytes  = 32 MiB
```

- UTF-8 截断不切断多字节字符；
- 只写一次 truncation marker；
- 只清理 `[0-9a-f]{32}.log`；
- 活跃和预留日志受保护；
- 正常终态读取后删除；
- coroutine 取消后的迟到终态日志异步删除；
- process death 日志保留诊断路径并受 retention 管理。

### 5.8 FFprobe 的 `5,000 ms` 命名错误

反编译产品 AAR 已确认，`getMediaInformationExecute(session, 5000)` 的整数用于 native FFprobe
返回后的 `getAllLogs(timeout)`，不是终止 FFprobe 的执行超时。

实现已改名为：

```text
MEDIA_INFORMATION_LOG_DRAIN_TIMEOUT_MILLIS
```

普通 FFmpeg 和 FFprobe 当前都没有默认执行超时。

### 5.9 native profile 与公开 API 缺少机器门禁

`tools/ffmpegkit_native_build/closure_manifest.json` 已增加
`qualified_conversion_profiles`。AAR/APK 审计要求：

- `libopenh264enc`；
- `AAC encoder`；
- `libavcodec/aacenc.c`；
- `libavformat/movenc.c`；
- MP4 muxer marker。

这证明构建包含当前 profile 所需实现；目标设备实际编码回读仍是独立验收层。

## 6. 主要实现文件

### Runtime

- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeClient.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeService.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeModels.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeProtocol.kt`

### API 与输出事务

- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFFmpegTool.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/FFmpegConversionContract.kt`
- `app/src/main/java/com/ai/assistance/operit/util/AtomicFileCommit.kt`

### 内部消费者

- `app/src/main/java/com/ai/assistance/operit/util/FFmpegUtil.kt`
- `app/src/main/java/com/ai/assistance/operit/util/MediaPoolManager.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/MNNProvider.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFileSystemTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserDownloadSupport.kt`

### ToolPkg、prompt 与 native gate

- `examples/ffmpeg.ts`
- `examples/types/ffmpeg.d.ts`
- `app/src/main/assets/packages/ffmpeg.js`
- `app/src/main/java/com/ai/assistance/operit/core/config/SystemToolPromptsInternal.kt`
- `tools/ffmpegkit_native_build/closure_manifest.json`
- `ci/script/audit_ffmpegkit_native_closure.py`
- `app/build.gradle.kts`

## 7. 验证矩阵

### 7.1 当前已通过

- [DONE] 完整 `ci/test`：`215/215`，失败和错误均为 `0`
- [DONE] `FFmpegRuntimeProtocolTest`
- [DONE] `FFmpegConversionContractTest`
- [DONE] `AtomicFileCommitTest`
- [DONE] 根级 `testDebugUnitTest`：`225 suites / 1337 tests`，失败、错误和跳过均为 `0`
- [DONE] 根级 `compileDebugAndroidTestKotlin` 与 `compileDebugAndroidTestJavaWithJavac`
- [DONE] `examples/tsconfig.json` TypeScript 编译
- [DONE] WebChat TypeScript 检查、Vite production build 与 Android asset sync
- [DONE] ToolPkg test mode `73/73`，随后 normal mode 恢复生产白名单 `43/43`
- [DONE] formal readiness、fresh clone、architecture `phase=m03` 与 `git diff --check`
- [DONE] FFmpegKit thin AAR audit：
  `86D97CC0174FF44A8057899BEF7B8E66BD976E5CFA7BBA7D2A9FC819CB8EFCA7`，
  wrapper r4、FFmpeg `n9.0.1`、`h264_aac_mp4` 和 9 个 16 KiB ELF 均通过
- [DONE] mpv thin AAR audit：
  `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`，
  mpv `2339eb727`、FFmpeg `n9.0.1` 和 10 个 16 KiB native payload 均通过
- [DONE] 九模块 Lint：`app` 为 `0 errors / 23 warnings`，`terminal` 为
  `0 errors / 1 warning`，其余模块无诊断；`app/lint-baseline.xml` 未修改
- [DONE] 串行 `:app:assembleDebug`：
  `BUILD SUCCESSFUL in 1m 20s`，`232` 个任务中 `28` 个执行、`204` 个 up-to-date；
  唯一 launcher、`verifyPlayerNativeInputs` 与 `verifyDebugPlayerRuntimePackaging` 通过
- [DONE] Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，`494163375` bytes，
  SHA-256 `73D1125473FACAA574C5EEC8053CCB372950F5F5B63D621C2A84752107DDFA27`；
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`
- [DONE] APK 使用 Android Debug V2 单 signer，`zipalign -c -P 16 -v 4` 通过；
  仅含 `arm64-v8a` 的 51 个 `.so`，无重复 basename
- [DONE] FFmpegKit/mpv 的 19 个 APK native payload 与两个产品 AAR 逐字节一致；51 个 `.so`
  加 shell launcher 共 52 个文件全部为 ELF64/AArch64，153 个 `PT_LOAD` 的最小对齐为
  `0x4000`；两套 closure 无 RPATH/RUNPATH 和 normal/namespaced 交叉依赖

以上是本地静态/JVM/编译证据，不是设备运行证据。

当前可见 Lint 诊断已经逐条分类：

- `app` 的 20 条依赖版本提示属于 AndroidX/Compose/WebView、网络、加密、native runtime、
  MCP、Retrofit、SSH 等独立迁移；不能为了清单归零在 FFmpeg 收尾中混入未审计升级；
- `OpenAIHostedWebSearchCompatibilityRepository` 的 3 条 `UseKtx` 建议有意保留：
  `SharedPreferences.edit(commit=true)` 会丢弃 `commit()` 的 Boolean 返回值，不能表达当前
  “同步落盘失败必须抛错”的已测试合同；
- `terminal` 的 1 条 `ChromeOsAbiSupport` 来自明确的 `arm64-v8a` 产品边界；当前 terminal、
  FFmpegKit 和 mpv native closure 都没有 x86_64 产物，不能虚构 ABI 支持；
- 本轮没有新增 suppress、关闭检查、扩大 baseline、自动降级或其它掩盖诊断的处理。

### 7.2 本轮本地交付门禁

- [DONE] FFmpegKit 与 mpv native closure audit
- [DONE] ToolPkg test mode 与 normal mode 恢复
- [DONE] 完整 `ci/test` Python 单元矩阵
- [DONE] 九模块 JVM / AndroidTest 编译 / Lint 根级矩阵
- [DONE] formal readiness / fresh clone / architecture / repository diff hygiene
- [DONE] 串行 `:app:assembleDebug`
- [DONE] Debug APK package/version/signature/zipalign/ABI/native payload/ELF 审计
- [DONE] Markdown 候选 Git 树检查：`errors=0 / warnings=0`
- [DONE] 候选 Git 树、敏感内容、构建产物、子模块和远端竞争预审

`main` 提交、正常推送和 local/tracking/remote 对账属于本轮交付动作，不能由承载本文件的候选
提交提前自证；最终交付报告必须给出实时 Git 结果。

完成本轮本地门禁后，把本节更新为实际命令、结果和产物摘要；不记录未发生的设备结果。

### 7.3 目标设备 FFmpegKit r4

- [PENDING] `ffmpeg_info`
- [PENDING] `-encoders`，不带 Shell 管道
- [PENDING] FFprobe
- [PENDING] lavfi `testsrc -> null`
- [PENDING] 实际文件解码到 null
- [PENDING] 1 秒和完整 `-c copy`
- [PENDING] MP3 音频提取
- [PENDING] `libopenh264` 720p 转码
- [PENDING] `ffmpeg_convert(profile=h264_aac_mp4)`
- [PENDING] 排队取消无 started
- [PENDING] 活动取消
- [PENDING] callback replacement/disconnect
- [PENDING] process death 日志与恢复
- [PENDING] 长时间编码与日志截断/保留
- [PENDING] 输出文件大小和 FFprobe codec/profile/pixel format/resolution/audio 回读

### 7.4 目标设备播放器

继续按播放器专项执行 direct MP4、HLS/DASH、header、redirect、Range/seek、cache、Surface
转挂、软件/MediaCodec、GPU/Vulkan/Anime4K、字幕/音轨/倍速和长时间播放矩阵。播放器结果不能
替代第 7.3 节。

## 8. 风险与回滚点

### 8.1 当前剩余风险

- r4 尚未在原 vivo Android 16 现场复测；
- Android 外部/模拟存储是否支持当前同目录 `ATOMIC_MOVE` 需设备验证；
- callback/process death 的真实调度时序仍需设备日志；
- `h264_aac_mp4` 的实际 OpenH264 profile 和 pixel format 回读需设备确认；
- Ubuntu `n9.0.1` package chain 尚不存在。

### 8.2 回滚点

“回滚点”只表示可恢复的工程边界，不授权加入运行时降级：

- 项目修改前 Git 基线为 `main@358030359d95f7425562ebb3e5e1235c1e17fcd0`；
- Android 产品 AAR 未在本专项中改写；
- player 和 FFmpegKit product AAR hash 保持不变；
- 任何 native 升级必须通过 source/candidate/product 成对 promotion，不能局部撤换 `.so`；
- 输出事务在最终原子移动前可以删除临时文件；移动完成后最终文件才成为产品结果。

## 9. 后续开发优先级

### P0：设备验收

在授权后使用目标设备执行第 7.3 和 7.4 节。发生 native death 时采集 request ID、runtime
generation、session/process ID、return code、日志路径、logcat、tombstone 和 native backtrace。

### P1：结构化 runtime capability snapshot

让 `ffmpeg_info` 提供有 schema 版本和 digest 的受控能力快照，包括：

- wrapper/FFmpeg/source identity；
- encoder/decoder/filter/protocol allowlist；
- qualified conversion profile；
- MediaCodec；
- ABI/API；
- manifest/profile digest。

该功能不得从 `-codecs` 文本猜测 encoder，也不得成为第二份 profile 真相源。

### P1：Ubuntu FFmpeg `n9.0.1`

按第 4.2 节建立独立 Linux/aarch64 `.deb` 工程。完成前继续如实报告 Ubuntu `6.1.1`。

### P2：新增转换 profile

每个新 profile 必须具备唯一 encoder、profile/pixel format/container、资源上限、manifest
marker、AAR/APK gate、JVM 参数测试、Android 编码和 FFprobe 回读。不得先扩大 TypeScript 枚举，
再让运行时“尽量执行”。

## 10. 完成定义

本专项只有在以下条件同时满足时，才能对“本地开发基线”标记完成：

1. 代码、ToolPkg、prompt、类型、manifest、测试和文档无旧合同漂移；
2. Python、JVM、AndroidTest 编译、Lint、formal readiness、architecture 和 Markdown 门禁通过；
3. FFmpegKit/product AAR 和 Debug APK native gate 通过；
4. Debug APK 完成签名、16 KiB、ABI、payload、ELF 和版本审计；
5. 候选树无敏感内容、运行产物、异常子模块或无关改动；
6. 本轮提交正常推送，local/tracking/remote 一致且工作树干净。

目标设备未执行时，专项总状态继续保持 `verification_pending`；这不阻止本地开发基线提交，但必须
在交付说明中明确最短设备验收动作。
