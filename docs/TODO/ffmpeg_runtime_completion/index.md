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
- FFmpegKit wrapper 为 `8.1.7-kiyori-n9.0.1-r6`；
- r3 首包前进程死亡的高置信 Binder 根因已由 r4 源码 patch 修复；
- 2026-08-17 的 r5 真实 AI 使用复测确认 `ffmpeg_execute` 已可完成 MP4→MKV/MOV/TS 流复制、
  `libopenh264`/MediaCodec H.264/HEVC、VP8/VP9/AV1/Theora/MPEG-2、14 类视频滤镜、音频转换、
  截图/GIF/九宫格、剪辑、拼接、循环和 metadata/faststart；该证据证明 r5 核心媒体处理链正常，
  但不能替代 r6 增量的重新安装验收；
- 同一 r5 复测确认 `ffmpeg_convert` 的转码和 FFprobe 已成功，但 H.264 constrained baseline
  以数值 `578` 返回时被 Kotlin 输出合同误拒绝；普通 baseline `66` 仍必须拒绝；
- r5 复测还确认 `-encoders/-filters/-formats` 等命令 return code 成功但输出为空。固定 FFmpeg
  n9.0.1 `opt_common.c` 直接向 stdout 写能力列表，而嵌入式 Android 进程没有 stdout consumer；
- r5 产品已经具备 HarfBuzz/`drawtext`，真实 `drawtext` 在显式使用
  `/system/fonts/Roboto-Regular.ttf` 时通过；`eq/boxblur` 因 FFmpeg GPL gate 未进入产品；
- Kiyori 固定 FFprobe 操作已使用私有 `-o` JSON，已公开 `ffmpeg_probe`、扩展
  `ffmpeg_info(section)`、明确三执行面和 signed AVERROR 语义；
- r6 已接受 `Constrained Baseline` 与数值 `578`、拒绝未被引号或反斜杠保护的 Shell
  operator/重定向和前缀 `ffmpeg`，并把 FFmpeg help/capability stdout 路由到有界
  FFmpegKit log callback；
- r6 closure 已显式启用 `--enable-gpl`、HarfBuzz、`drawtext`、`eq`、`boxblur`，AAR 内携带
  与仓库 `LICENSE` 字节一致的 `res/raw/license_gplv3.txt`；source/thin/product exact-hash
  patch-level promotion 和双 closure native audit 已通过；
- API/runtime/调用方实现、TypeScript 同步、FFmpeg/native Python 合同、Gradle native input 和
  定向 JVM 回归已通过；当前定向 Python 为 `52/52`；
- 全量 Python `218/218`、Gradle/JVM/AndroidTest/Lint `431` tasks、WebChat、ToolPkg、
  formal readiness、fresh clone、architecture `phase=m03`、最终 Debug APK 构建和独立静态
  审计均已通过；候选提交后的 fresh-clone 与 Git 精确交付仍以最终实时结果为准；
- vivo V2507A / Android 16 上的 r5 核心媒体矩阵已经通过；r6 的能力 stdout、`eq/boxblur`、
  profile `578`、Shell 校验、GPL 资源，以及完整取消/process-death 和播放器矩阵仍为
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
| Android FFmpegKit | 非导出 `com.kiyori:ffmpeg` | `FFmpegRuntimeService` | AI 工具、探测、转码、remux | 当前 FFmpeg `n9.0.1` / wrapper r6；本地闭环完成 |
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
- `ffmpeg_execute` 保留高级字符串参数 API，不改写合法 FFmpeg 参数；工具边界拒绝未被引号或
  反斜杠保护的 `|/||/&&/;/&`、重定向和前缀 `ffmpeg`，并明确引导能力列表使用
  `ffmpeg_info(section)`、显式 Ubuntu Shell 使用 `super_admin:terminal`。

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

### 5.8 FFprobe 的 `5,000 ms` 不是执行超时

反编译产品 AAR 已确认，`getMediaInformationExecute(session, 5000)` 的整数用于 native FFprobe
返回后的 `getAllLogs(timeout)`，不是终止 FFprobe 的执行超时。

r5 起已删除对该 media-information helper 的调用，直接执行 `FFprobeSession + ffprobeExecute`
并读取 request-scoped `-o` JSON，因此 runtime 中不再保留该常量。普通 FFmpeg 和 FFprobe
当前都没有默认执行超时。

### 5.9 native profile 与公开 API 缺少机器门禁

`tools/ffmpegkit_native_build/closure_manifest.json` 已增加
`qualified_conversion_profiles`。AAR/APK 审计要求：

- `libopenh264enc`；
- `AAC encoder`；
- `libavcodec/aacenc.c`；
- `libavformat/movenc.c`；
- MP4 muxer marker。

这证明构建包含当前 profile 所需实现；目标设备实际编码回读仍是独立验收层。

r6 还把以下内容纳入机器合同：

- `--enable-gpl`；
- `--enable-filter=eq`；
- `--enable-filter=boxblur`；
- `libavfilter.so` 中 exact `drawtext/eq/boxblur`；
- `res/raw/license_gplv3.txt` 与仓库 `LICENSE` SHA-256/正文一致。

### 5.10 FFprobe n9 stdout 与 FFmpegKit 8.1.7 日志解析失配

已确认根因：

- `FFmpegKitConfig.getMediaInformationExecute()` 只收集 `Level.AV_LOG_STDERR`；
- FFmpeg n9.0.1 的 `ffprobe` 使用 `AVTextWriter` 创建 stdout writer；
- 固定媒体探测 native return code 可以为成功，但 wrapper 没有 JSON 可解析，
  `MediaInformationSession.mediaInformation` 因而为 `null`；
- `ffmpeg_convert` 在 FFmpeg 已生成非空隐藏 `.partial.mp4` 后进入该失败路径，`finally`
  随后按合同删除临时文件。普通目录轮询看不到点号开头文件，不能据此推断转码从未开始。

冻结修复：

1. `PROBE_MEDIA` 不再依赖 wrapper 从日志重建 JSON；
2. FFprobe 使用官方 `-o` 参数写入 `:ffmpeg` 私有、request-scoped JSON 文件；
3. Kiyori 自有解析器只投影受控 format/stream 字段，限制文件字节、流数量和字符串长度；
4. JSON 在成功、失败和 service 重建清理路径中删除，不进入 Binder 或公共存储；
5. FFprobe 非零 return code、JSON 缺失、超限、语法错误和字段错误分别形成可诊断失败；
6. `ffmpeg_convert` 记录明确 pipeline stage，不能把转码、探测、回读验证和原子提交混成一个错误。

### 5.11 AI 工具缺少执行面路由和公开 FFprobe

旧问题：

- package、system prompt 和结果只写“FFmpeg 系统信息”“转换视频”，没有声明 Android
  `:ffmpeg`、Ubuntu `/usr/bin/ffmpeg` 和播放器 closure 的隔离关系；
- `ffmpeg_info` 只有无参数 `-codecs` 文本，模型为了查询 encoder/filter 容易把 Shell 管道
  传给 `ffmpeg_execute`；
- 没有公开 Android FFprobe，模型只能用 `ffmpeg_execute -i` 或 Ubuntu `ffprobe` 旁证；
- ToolPkg 成功结果只返回 `result.output`，丢失 output file、媒体结构和运行时字段。

冻结修复：

1. package、双语 system prompt、TypeScript、Kotlin 和文档共享同一执行面说明；
2. 新增 `ffmpeg_probe(input_path)`，只调用 Android `:ffmpeg` 的 FFprobe；
3. `ffmpeg_info(section)` 支持 `summary/codecs/encoders/decoders/filters/formats/muxers/`
   `demuxers/protocols/hwaccels/buildconf`，不要求 Shell 管道；
4. `summary` 明确版本、进程、ABI、qualified profile、工具选择顺序和不自动切换规则；
5. ToolPkg 返回完整 `FFmpegResultData`，错误保留唯一用户消息和结构化诊断；
6. `ffmpeg_execute` 在 native 前拒绝未被引号或反斜杠保护的 Shell operator、重定向和前缀
   executable；合法滤镜值中的字面 operator 必须正确引用或转义，原始字符串保持不变；
7. Android `drawtext` 在 prompt、summary 和 API 文档中要求绝对 `fontfile`；
8. Android 测试只证明 Android 执行面；Ubuntu 和播放器必须独立标注。

### 5.12 signed FFmpeg AVERROR 被误判为封装错误

FFmpegKit 返回的是 native FFmpeg signed return value，而不是 Shell 进程只能观察到的
`0..255` exit status。`-2` 可表示底层 `ENOENT`，编码器不存在也可以返回四字符编码的负
`AVERROR`。本轮保留原值，不做有损映射；结果、工具说明和文档统一标注
`signed_ffmpeg_averror`，并要求结合 terminal state 和日志判断。

MediaCodec GOP 与 image2 单图 warning 属于调用参数语义，不由 raw `ffmpeg_execute` 静默注入
`-g` 或 `-update 1`。示例可以给出正确参数，但运行时不改写用户命令。

### 5.13 constrained baseline 数值 `578` 被误拒绝

FFmpeg 的 H.264 constrained baseline profile 可以表示为：

```text
AV_PROFILE_H264_BASELINE | AV_PROFILE_H264_CONSTRAINED = 66 | 512 = 578
```

r5 真实设备输出使用 `578`，旧 Kotlin 合同只接受规范化后的
`Constrained Baseline` 文本，因此把成功转码误判为 output contract failure。r6 同时接受：

```text
Constrained Baseline
578
```

普通 baseline `66` 不带 constrained flag，继续拒绝。FFprobe 私有 JSON parser 同时接受
profile 的 JSON string 与 integer 表示，并统一投影为字符串。

## 6. 主要实现文件

### Runtime

- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeClient.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeService.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeModels.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeProtocol.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeMediaInformationParser.kt`

### API 与输出事务

- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFFmpegTool.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFFmpegProbeTool.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/FFmpegToolResultContract.kt`
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

### 7.1 r5 设备基线与当前 r6 本地证据

下列数字是本轮 r6 重新运行的实际本地结果，不复用 r4 APK 或测试数字：

- [DONE] 完整 `ci/test`：`218/218`，失败和错误均为 `0`
- [DONE] `FFmpegRuntimeProtocolTest`
- [DONE] `FFmpegConversionContractTest`
- [DONE] `AtomicFileCommitTest`
- [DONE] 根级 `testDebugUnitTest + compileDebugAndroidTestKotlin +
  compileDebugAndroidTestJavaWithJavac + lintDebug`：
  `BUILD SUCCESSFUL in 7m54s`，`431` 个任务中 `18` 个执行、`413` 个 up-to-date
- [DONE] `examples/tsconfig.json` TypeScript 编译
- [DONE] WebChat TypeScript 检查、Vite production build 与 Android asset sync
- [DONE] ToolPkg test mode `73/73`，随后 normal mode 恢复生产白名单 `43/43`
- [DONE] `ci.test.test_toolpkg_sync`：`7/7`
- [DONE] formal readiness、fresh clone、architecture `phase=m03` 与 `git diff --check`
- [DONE] 当前 FFmpegKit r6 source/thin/product audit：
  source `0BD7ADDAE2D17960DB940A17A3E2450ACB83EECB46BE0D3800D051BB2075C1CE`
  / `39,632,341` bytes，thin/product
  `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394`
  / `30,486,441` bytes；wrapper r6、FFmpeg `n9.0.1`、GPL/HarfBuzz、
  `drawtext/eq/boxblur`、GPLv3 resource、`h264_aac_mp4` 和 9 个 16 KiB ELF 均通过
- [DONE] mpv thin AAR audit：
  `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`，
  mpv `2339eb727`、FFmpeg `n9.0.1` 和 10 个 16 KiB native payload 均通过
- [DONE] 九模块 Lint：`app` 为 `0 errors / 23 warnings`，`terminal` 为
  `0 errors / 1 warning`，其余模块无诊断；`app/lint-baseline.xml` 未修改
- [DONE r6] 串行 `:app:assembleDebug`：
  `BUILD SUCCESSFUL in 1m49s`，`232` 个任务中 `32` 个执行、`200` 个 up-to-date；
  唯一 launcher、`verifyPlayerNativeInputs` 与 `verifyDebugPlayerRuntimePackaging` 通过
- [DONE r6] Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，`494168730` bytes，
  SHA-256 `5C12152E9F180B1454AFAF8FD72F370840B409E8703902E9BEDE6A434250285E`；
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`
- [DONE] APK 使用 Android Debug V2 单 signer，`zipalign -c -P 16 -v 4` 通过；
  仅含 `arm64-v8a` 的 51 个 `.so`，无重复 basename
- [DONE] FFmpegKit/mpv 的 19 个 APK native payload 与两个产品 AAR 逐字节一致；51 个 `.so`
  加 shell launcher 共 52 个文件全部为 ELF64/AArch64，153 个 `PT_LOAD` 的最小对齐为
  `0x4000`，分布为 `0x4000 × 151 / 0x10000 × 2`；两套 closure 无 RPATH/RUNPATH，
  normal/namespaced basename 零交集
- [DONE] APK 内 `libffmpegkit.so` 包含 wrapper r6、FFmpegKit log callback 与 Binder threadpool
  patch marker；`libavutil.so` 包含 `n9.0.1`、GPL、HarfBuzz、`eq/boxblur` configure marker，
  `libavfilter.so` 包含 `drawtext/eq/boxblur`，旧 FFmpeg 8 marker 不存在
- [DONE] APK 的 `res/raw/license_gplv3.txt` 与仓库 `LICENSE`、`assets/packages/ffmpeg.js`
  与源码逐字节一致；44 个 DEX 包含 runtime service、media parser 和 probe executor；
  `assets/packages/` 与生产白名单 43 项完全一致，无 test-mode 资产残留

以上 r6 APK 是本地静态/JVM/编译证据，不是目标设备运行证据。

当前可见 Lint 诊断已经逐条分类：

- `app` 的 20 条依赖版本提示属于 AndroidX/Compose/WebView、网络、加密、native runtime、
  MCP、Retrofit、SSH 等独立迁移；不能为了清单归零在 FFmpeg 收尾中混入未审计升级；
- `OpenAIHostedWebSearchCompatibilityRepository` 的 3 条 `UseKtx` 建议有意保留：
  `SharedPreferences.edit(commit=true)` 会丢弃 `commit()` 的 Boolean 返回值，不能表达当前
  “同步落盘失败必须抛错”的已测试合同；
- `terminal` 的 1 条 `ChromeOsAbiSupport` 来自明确的 `arm64-v8a` 产品边界；当前 terminal、
  FFmpegKit 和 mpv native closure 都没有 x86_64 产物，不能虚构 ABI 支持；
- 本轮没有新增 suppress、关闭检查、扩大 baseline、自动降级或其它掩盖诊断的处理。

### 7.2 本轮 r6 本地交付门禁

- [DONE] FFmpegKit 与 mpv native closure audit
- [DONE] TypeScript normal mode 与 APK asset 同步
- [DONE] FFmpeg/native 定向 Python 单元矩阵：`52/52`
- [DONE] Gradle native input 与 FFmpeg 定向 JVM
- [DONE] ToolPkg test mode `73/73` 与 normal mode `43/43` 恢复
- [DONE] 完整 `ci/test` Python 单元矩阵：`218/218`
- [DONE] 九模块 JVM / AndroidTest 编译 / Lint 根级矩阵：`431` tasks
- [DONE] formal readiness / fresh clone / architecture / repository diff hygiene
- [DONE] 串行 `:app:assembleDebug`：`232` tasks
- [DONE] Debug APK package/version/signature/zipalign/ABI/native payload/ELF/marker/ToolPkg 审计
- [DONE] WebChat、GitHub 示例、WASM ToolPkg 与 production ToolPkg 检查
- [PENDING] 候选提交后的 fresh-clone
- [PENDING] 候选 Git 树、敏感内容、构建产物、子模块和远端竞争预审及正常推送

`main` 提交、正常推送和 local/tracking/remote 对账属于本轮交付动作，不能由承载本文件的候选
提交提前自证；最终交付报告必须给出实时 Git 结果。

完成本轮本地门禁后，把本节更新为实际命令、结果和产物摘要；不记录未发生的设备结果。

### 7.3 真实 AI 使用缺陷后续修复

- [DONE] 恢复 `main@697a0dbe`、远端、工作树、历史日记和用户真实对话基线
- [DONE] 反编译 r4 AAR 并定位 FFmpegKit media-information 日志解析合同
- [DONE] 核对 FFmpeg n9.0.1 `ffprobe` stdout writer 与 r4 filter configure 结果
- [DONE] 实现私有 FFprobe JSON、公开 probe、分区 info、结果语义和 pipeline 错误
- [DONE] 显式启用 HarfBuzz/drawtext，重建、审计并 promotion r5 AAR
- [DONE] 解析最新 r5 真实设备矩阵，纠正“Android FFmpeg 整体崩溃”的旧结论
- [DONE] 修复 constrained baseline `578`、能力 stdout、Shell token、`eq/boxblur` GPL gate、
  GPLv3 resource 和 product promotion 单一 manifest 合同
- [DONE] 重建、审计并 patch-level promotion r6 AAR；mpv 产品哈希保持不变
- [DONE] 补齐 AndroidTest、ToolPkg、文档和最终 APK marker 测试
- [DONE] 运行完整 Python/Gradle/Lint/WebChat/ToolPkg/formal/fresh-clone/architecture 矩阵，
  复核并保留范围外或有意保留的 Lint 诊断
- [DONE] 串行构建并独立审计 Debug APK
- [PENDING] 审计、提交、推送 `origin/main` 并核对 local/tracking/remote

### 7.4 目标设备 FFmpegKit

已由用户对 r5 安装包实测：

- [DONE r5] MP4→MKV/MOV/TS 流复制
- [DONE r5] `libopenh264`、H.264/HEVC MediaCodec、`libkvazaar`、VP8/VP9/AV1/Theora/MPEG-2
- [DONE r5] 常用视频滤镜、音频提取/转换、截图、GIF、九宫格、剪辑、拼接、循环和 metadata
- [DONE r5] `drawtext` 使用 `/system/fonts/Roboto-Regular.ttf`
- [DONE r5] `ffmpeg_convert` 转码与 FFprobe 成功；最终只因 profile `578` 旧合同失败
- [CONFIRMED r5 DEFECT] `-encoders/-filters/-formats` 输出未被工具捕获
- [CONFIRMED r5 DEFECT] `eq/boxblur` 未编译

r6 重新安装后必须验证：

- [PENDING r6] `ffmpeg_info`
- [PENDING r6] `ffmpeg_info(section=encoders/filters/formats/hwaccels)` 输出非空且不带 Shell
- [PENDING r6] `ffmpeg_execute` 对 `|`、重定向和前缀 `ffmpeg` 返回明确 validation failure
- [PENDING r6] `ffmpeg_probe`
- [PENDING r6] `eq`、`boxblur`、`drawtext`（绝对 fontfile）
- [PENDING r6] `ffmpeg_convert(profile=h264_aac_mp4)` 接受 profile `578` 并原子提交
- [PENDING r6] GPLv3 notice 在最终安装包资源中存在
- [PENDING] 排队取消无 started
- [PENDING] 活动取消
- [PENDING] callback replacement/disconnect
- [PENDING] process death 日志与恢复
- [PENDING] 长时间编码与日志截断/保留
- [PENDING] 输出文件大小和 FFprobe codec/profile/pixel format/resolution/audio 回读

### 7.5 目标设备播放器

继续按播放器专项执行 direct MP4、HLS/DASH、header、redirect、Range/seek、cache、Surface
转挂、软件/MediaCodec、GPU/Vulkan/Anime4K、字幕/音轨/倍速和长时间播放矩阵。播放器结果不能
替代第 7.3 节。

## 8. 风险与回滚点

### 8.1 当前剩余风险

- r6 已构建、promotion 并通过本地 native/定向 JVM 门禁，但尚未在原 vivo Android 16 现场复测；
- Android 外部/模拟存储是否支持当前同目录 `ATOMIC_MOVE` 需设备验证；
- callback/process death 的真实调度时序仍需设备日志；
- r5 已证明实际 OpenH264 profile 会出现 `578`；r6 对该表示的接受与最终原子提交需设备确认；
- Ubuntu `n9.0.1` package chain 尚不存在。

### 8.2 回滚点

“回滚点”只表示可恢复的工程边界，不授权加入运行时降级：

- 本轮后续修复前 Git 基线为 `main@697a0dbee1a85732cd3d219a7df64e95c6a57256`；
- r5 product AAR 及其 qualified hash 是 r6 promotion 前的历史可恢复输入；
- player AAR 不随本轮 FFmpegKit r6 重建改变；
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
