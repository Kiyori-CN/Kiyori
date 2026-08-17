---
document_type: package-api
status: active
verification: verification_pending
last_verified: 2026-08-17
---

# API 文档：`ffmpeg.d.ts`

`ffmpeg.d.ts` 为包内脚本提供 `Tools.FFmpeg` 命名空间。它包含原始 FFmpeg 参数执行、Android
运行时信息查询和一个受资格约束的简化视频转换接口。

## 运行时边界

三个 API 都调用 Android 非导出 `com.kiyori:ffmpeg` 进程中的 FFmpegKit/FFmpeg：

- 不调用 Ubuntu `/usr/bin/ffmpeg`；
- 不调用 `:player` 进程中的 mpv FFmpeg closure；
- 不经过 Shell；
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
- 不传 `|`、`>`、`2>&1`、`&&`、`;` 等 Shell 管道、重定向或命令链；
- 不调用 `grep`、`ffprobe` 或其它外部程序；
- 字符串由 FFmpegKit 参数解析器拆分，路径中的空格需要正确引号；
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

### `Tools.FFmpeg.info()`

```ts
info(): Promise<FFmpegResultData>
```

返回 Android `:ffmpeg` 运行时的：

- FFmpegKit wrapper version；
- FFmpeg version；
- build date；
- `-codecs` 文本输出。

它不返回 Ubuntu `/usr/bin/ffmpeg` 信息。`-codecs` 是能力概览，也不等于已经通过
`ffmpeg_convert` 资格化的 encoder/profile 列表。

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

目标设备上的实际 r4 编码、排队/活动取消和进程死亡矩阵仍是 `verification_pending`，不能由
JVM、静态 AAR 审计或 Debug APK 构建替代。

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

## 返回值与失败

三个 API 都返回 `FFmpegResultData`。该类型定义在 `results.d.ts` 中，包含命令展示、return code、
运行时输出、耗时、输出文件和媒体信息。

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
- `app/src/main/java/com/ai/assistance/operit/util/AtomicFileCommit.kt`
- [FFmpeg 架构与开发指南](../dev-core/FFMPEG_ARCHITECTURE.md)
- [Player native stack](../dev-core/PLAYER_NATIVE_STACK.md)
