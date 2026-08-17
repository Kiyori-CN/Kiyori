---
document_type: package-api
status: active
verification: verification_pending
last_verified: 2026-08-17
---

# API 文档：`ffmpeg.d.ts`

`ffmpeg.d.ts` 为包内脚本提供 `Tools.FFmpeg` 命名空间。它包含原始 FFmpeg 参数执行、Android
运行时分区查询、Android FFprobe 结构化探测和一个受资格约束的简化视频转换接口。

## 运行时边界

四个 API 都调用 Android 非导出 `com.kiyori:ffmpeg` 进程中的 FFmpegKit/FFmpeg：

- 不调用 Ubuntu `/usr/bin/ffmpeg`；
- 不调用 `:player` 进程中的 mpv FFmpeg closure；
- 不经过 Shell，raw execute 会拒绝未被引号或反斜杠保护的 operator、重定向和前缀 executable；
- 不改写参数、自动重试、切换编码器或切换执行面；
- 不把一个执行面的成功结果当作另一个执行面的验证。

进程、Binder、日志、取消、失败语义、native closure 和升级策略见
[FFmpeg 架构与开发指南](../dev-core/FFMPEG_ARCHITECTURE.md)。

## 类型别名

### `FFmpegConversionProfile`

```ts
export type FFmpegConversionProfile = 'h264_aac_mp4';
```

`h264_aac_mp4` 是当前唯一资格化的简化转换 profile。它不是可自由组合的 codec 名称，而是一个
完整、确定的输出合同：

| 项目 | 固定值 |
| --- | --- |
| 视频编码器 | `libopenh264` |
| H.264 profile | `constrained_baseline` |
| 像素格式 | `yuv420p` |
| 音频编码器 | `aac` |
| 容器 | `mp4` |
| MP4 标志 | `+faststart` |

HEVC、VP8、VP9、AV1、MP3、Opus 等能力不能由库名或 `-codecs` 输出自动推断为简化 API
支持项。只有在 native manifest、AAR/APK 门禁、参数映射、输出回读和目标设备编码测试全部
闭环后，才能新增 profile。

### `FFmpegResolution`

```ts
export type FFmpegResolution =
  | '1280x720'
  | '1920x1080'
  | '3840x2160'
  | '7680x4320'
  | `${number}x${number}`;
```

TypeScript 模板只能表达字符串形状；Android 运行时还会强制：

- 宽和高都在 `16..8192`；
- 宽和高都必须是偶数；
- 不接受小数、负数、空白或附加参数。

### `FFmpegVideoBitrate`

```ts
export type FFmpegVideoBitrate =
  | '500k'
  | '1000k'
  | '2000k'
  | '4000k'
  | '8000k'
  | `${number}k`
  | `${number}M`;
```

Android 运行时接受 `k`/`K`/`m`/`M`，规范化为 `k` 或 `M`，并限制在 `64k..100M`。

## 运行时 API

### `Tools.FFmpeg.execute(command)`

```ts
execute(command: string): Promise<FFmpegResultData>
```

`command` 是 FFmpeg 参数字符串，不是 Shell 命令。

约束：

- 不写开头的 `ffmpeg`；
- 不传未被引号或反斜杠保护的 `|`、`>`、`2>&1`、`&&`、`;` 等 Shell 管道、重定向或命令链；
- 不调用 `grep`、`ffprobe` 或其它外部程序；
- 字符串由 FFmpegKit 参数解析器拆分，路径中的空格需要正确引号；
- 合法滤镜值中的 `|`、`;` 等字符必须由引号或反斜杠保护；校验按词法位置识别 Shell
  operator，原始字符串保持不变，不做命令重写；
- `drawtext` 必须显式使用 Android 可读的绝对 `fontfile`，例如
  `/system/fonts/Roboto-Regular.ttf`；
- 是否覆盖输出由调用者显式传 `-n` 或 `-y`，不会自动改写；
- 执行失败后不会自动重试、换编码器或切换到 Ubuntu。

示例：

```ts
await Tools.FFmpeg.execute(
  '-n -i "/storage/emulated/0/Download/input.mp4" ' +
  '-c copy "/storage/emulated/0/Download/output.mp4"'
);
```

应用内部 Kotlin 调用方应使用结构化 `executeArguments(List<String>)`，避免路径被二次解析。

### `Tools.FFmpeg.info(section?)`

```ts
export type FFmpegInformationSection =
  | 'summary'
  | 'codecs'
  | 'encoders'
  | 'decoders'
  | 'filters'
  | 'formats'
  | 'muxers'
  | 'demuxers'
  | 'protocols'
  | 'hwaccels'
  | 'buildconf';

info(section?: FFmpegInformationSection): Promise<FFmpegResultData>
```

默认 `section='summary'`。固定分区与 native 参数一一对应，不经过 Shell：

| section | native 参数 | 用途 |
| --- | --- | --- |
| `summary` | `-version` | 执行面、进程、ABI/API、wrapper、FFmpeg、build date、资格化 profile 和路由规则 |
| `codecs` | `-codecs` | codec 能力概览 |
| `encoders` | `-encoders` | encoder 列表 |
| `decoders` | `-decoders` | decoder 列表 |
| `filters` | `-filters` | filter 列表；r6 产品必须包含 `drawtext`、`eq`、`boxblur` |
| `formats` | `-formats` | muxer/demuxer 总览 |
| `muxers` | `-muxers` | muxer 列表 |
| `demuxers` | `-demuxers` | demuxer 列表 |
| `protocols` | `-protocols` | protocol 列表 |
| `hwaccels` | `-hwaccels` | 硬件加速列表 |
| `buildconf` | `-buildconf` | FFmpeg configure 参数 |

它不返回 Ubuntu `/usr/bin/ffmpeg` 信息。原始能力文本不等于已经通过
`ffmpeg_convert` 资格化的 encoder/profile 列表。

### `Tools.FFmpeg.probe(inputPath)`

```ts
probe(inputPath: string): Promise<FFmpegResultData>
```

`probe` 使用与 `ffmpeg_convert` 输出验证相同的 Android `:ffmpeg` FFprobe 执行面。输入必须是
存在、非空、绝对路径的普通文件。

FFmpeg n9.0.1 通过 `AVTextWriter` 向 stdout 写结构化 FFprobe 输出，而 FFmpegKit 8.1.7 的旧
media-information helper 只从日志中的 `AV_LOG_STDERR` 重建 JSON。Kiyori 因此不再调用该 helper：

1. native FFprobe 使用官方 `-o` 写入 request-scoped 私有 JSON；
2. JSON 仅位于 `cacheDir/ffmpeg-runtime-probes/`；
3. `-show_entries` 从 native 端只生成合同字段，不输出 tags、side data 等无关元数据；
4. 自有解析器使用严格 UTF-8，只投影 format、duration、bitrate 和受控 stream 字段；
5. 文件最大 `1 MiB`，最多 `128` 条流，单字符串最多 `4096` 字符；
6. 成功、失败、取消和 service 重建路径都会清理文件；
7. JSON 不进入 Binder，也不写入公共存储。

### `Tools.FFmpeg.convert(inputPath, outputPath, options?)`

```ts
convert(
  inputPath: string,
  outputPath: string,
  options?: {
    profile?: FFmpegConversionProfile;
    resolution?: FFmpegResolution;
    video_bitrate?: FFmpegVideoBitrate;
  }
): Promise<FFmpegResultData>
```

默认 `profile` 为 `h264_aac_mp4`。

输入合同：

- `inputPath` 和 `outputPath` 必须是绝对路径；
- 输入必须是存在、非空的普通文件；
- 输入与输出解析后的 canonical path 必须不同；
- 两条路径最长 `4096` 字符；
- 输出父目录必须已经存在。

输出合同：

- 输出扩展名必须是 `.mp4`；
- 最终输出必须不存在；
- 简化 API 永不覆盖；
- 需要覆盖的高级场景只能由调用者显式使用 `execute(... -y ...)`；
- 不自动创建父目录；
- 不自动换 encoder、容器或执行通道。

执行与提交顺序：

1. 在最终输出同目录分配唯一 `.partial.mp4`；
2. 使用结构化参数和 `-nostdin -hide_banner -n` 执行一次 FFmpeg；
3. 固定映射主视频 `0:V:0` 和可选首音频 `0:a:0?`，排除字幕、数据流和 attached picture；
4. 要求临时文件存在且非空；
5. 使用 FFprobe 回读；
6. 验证正时长、MP4、唯一 H.264 视频流、Constrained Baseline、`yuv420p`、请求分辨率，以及
   最多一条 AAC 音频流；
7. 只有全部验证通过才在同目录执行原子移动；
8. 同一进程对相同目标串行提交；目标已经存在、文件系统不支持原子移动或任何验证失败时，
   保留原目标并删除本次临时文件。

Constrained Baseline 可以由 FFprobe 表示为文本或数值 `578`；两者都接受。普通 baseline
数值 `66` 不含 constrained flag，继续拒绝。

r5 目标设备已经完成核心编码、FFprobe、`drawtext`、滤镜、音频、截图/GIF、拼接和流复制矩阵；
r6 的能力 stdout、profile `578` 最终提交、`eq/boxblur`、Shell validation、GPL notice，以及
排队/活动取消和进程死亡矩阵仍是 `verification_pending`，不能由 JVM、静态 AAR 审计或 Debug
APK 构建替代。

## 示例

### 默认确定性转换

```ts
const result = await Tools.FFmpeg.convert(
  '/storage/emulated/0/Download/input.mp4',
  '/storage/emulated/0/Download/output.mp4'
);
complete(result);
```

### 指定分辨率和视频比特率

```ts
const result = await Tools.FFmpeg.convert(
  '/storage/emulated/0/Download/input.mp4',
  '/storage/emulated/0/Download/output-720p.mp4',
  {
    profile: 'h264_aac_mp4',
    resolution: '1280x720',
    video_bitrate: '4000k'
  }
);
complete(result);
```

### 查询 Android FFmpeg 环境

```ts
const info = await Tools.FFmpeg.info();
console.log(info.toString());
```

### 查询 Android encoder 与 filter

```ts
const encoders = await Tools.FFmpeg.info('encoders');
const filters = await Tools.FFmpeg.info('filters');
console.log(encoders.output);
console.log(filters.output);
```

### 使用 Android FFprobe

```ts
const probe = await Tools.FFmpeg.probe(
  '/storage/emulated/0/Download/input.mp4'
);
console.log(probe.mediaInfo);
```

## 返回值与失败

四个 API 都返回 `FFmpegResultData`。该类型定义在 `results.d.ts` 中，包含：

- `executionPlane='android_ffmpegkit'`；
- `terminalState`；
- `returnCode`，仅在 native session 真正提供时存在；
- `returnCodeSemantics='signed_ffmpeg_averror'`；
- `processId`、`sessionId`；
- `pipelineStage`、`failureCode`；
- process death 时的 `diagnosticLogPath`；
- 运行时输出、耗时、最终输出文件和 FFprobe 媒体信息。

负返回码保留 FFmpegKit 返回的 signed native AVERROR，不压缩到 Shell 的 `0..255` exit status。
protocol failure 或 process death 没有 native return code，`returnCode` 为 `null`；不得伪造数值。

需要区分：

- FFmpeg 非零 return code；
- `CANCELLED`；
- Binder/runtime protocol failure；
- `:ffmpeg` process death；
- FFprobe 回读失败；
- 最终原子提交失败。

process death 的异常只内嵌有界诊断摘录；完整的有界日志文件路径保留在异常诊断字段中，并受
运行时保留策略管理。

## 相关文件

- `examples/ffmpeg.ts`
- `examples/types/ffmpeg.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/FFmpegConversionContract.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/FFmpegToolResultContract.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/StandardFFmpegProbeTool.kt`
- `app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/FFmpegRuntimeMediaInformationParser.kt`
- `app/src/main/java/com/ai/assistance/operit/util/AtomicFileCommit.kt`
- [FFmpeg 架构与开发指南](../dev-core/FFMPEG_ARCHITECTURE.md)
- [Player native stack](../dev-core/PLAYER_NATIVE_STACK.md)
