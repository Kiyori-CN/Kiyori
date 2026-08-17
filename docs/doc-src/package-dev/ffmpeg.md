# API 文档：`ffmpeg.d.ts`

`ffmpeg.d.ts` 为包内脚本提供了 `Tools.FFmpeg` 命名空间，以及若干编解码参数类型。

运行时边界：

- 这三个 API 调用 Android 非导出 `:ffmpeg` 进程中的 FFmpegKit/FFmpeg，不调用 Ubuntu
  `/usr/bin/ffmpeg`，也不调用 mpv 播放器 closure。
- `execute()` 直接传递 FFmpeg 参数，不经过 Shell。
- 进程、线程、日志、失败语义、双 native closure 和后续编码器合同见
  [FFmpeg 架构与开发指南](../dev-core/FFMPEG_ARCHITECTURE.md)。

## 作用

适合处理以下任务：

- 执行原始 FFmpeg 命令。
- 查询当前 FFmpeg 环境信息。
- 用简化参数完成常见视频转码。

## 类型别名

### `FFmpegVideoCodec`

当前类型声明的视频编码枚举值包括：

- `h264`
- `hevc`
- `vp8`
- `vp9`
- `av1`
- `libx265`
- `libvpx`
- `libaom`
- `mpeg4`
- `mjpeg`
- `prores`

这些值是现有公开类型声明，不代表当前 Android AAR 已经为每个值建立了可执行 encoder 映射。
当前实现仍会把值直接传给 `-c:v`；codec family、具体 encoder 和当前构建能力尚未闭环。开发者不得
根据该列表推断 `h264`、`hevc`、`vp9` 或 `av1` 一定是有效 encoder 名称，也不得把
`libx265` 视为当前产品能力；当前 FFmpegKit manifest 明确禁止 `libx265`。

### `FFmpegAudioCodec`

当前类型声明的音频编码枚举值包括：

- `aac`
- `mp3`
- `opus`
- `vorbis`
- `flac`
- `pcm`
- `wav`
- `ac3`
- `eac3`

`wav` 是容器语义，`pcm` 还缺少 sample format；当前列表同样不能直接当作已经资格化的 encoder
allowlist。

### `FFmpegResolution`

支持：

- 预设值：`1280x720`、`1920x1080`、`3840x2160`、`7680x4320`
- 自定义值：`${number}x${number}`

### `FFmpegBitrate`

支持：

- 预设值：`500k`、`1000k`、`2000k`、`4000k`、`8000k`
- 自定义值：`${number}k`、`${number}M`

## 运行时 API

### `Tools.FFmpeg.execute(command)`

```ts
execute(command: string): Promise<FFmpegResultData>
```

说明：

- `command` 只传 FFmpeg 参数本身。
- 不要把前缀 `ffmpeg` 也写进去。
- 不要传 Shell 管道、重定向、命令链或外部程序，例如 `| grep`、`2>&1`、`&& ffprobe`。
- 需要 Shell 语义时应显式使用终端工具；它与 `Tools.FFmpeg` 是不同执行面。

示例：

```ts
await Tools.FFmpeg.execute(
  '-y -i "/sdcard/input.mp4" -vf scale=1280:720 "/sdcard/output.mp4"'
);
```

### `Tools.FFmpeg.info()`

```ts
info(): Promise<FFmpegResultData>
```

返回 Android `:ffmpeg` 运行时的 FFmpegKit wrapper version、FFmpeg version、build date 和
`-codecs` 输出。它不返回 Ubuntu `/usr/bin/ffmpeg` 信息；`-codecs` 也不等于已经验证的
`ffmpeg_convert` encoder allowlist。

### `Tools.FFmpeg.convert(inputPath, outputPath, options?)`

```ts
convert(
  inputPath: string,
  outputPath: string,
  options?: {
    video_codec?: FFmpegVideoCodec;
    audio_codec?: FFmpegAudioCodec;
    resolution?: FFmpegResolution;
    bitrate?: FFmpegBitrate;
  }
): Promise<FFmpegResultData>
```

适合最常见的“输入文件 → 输出文件”转码场景。

当前实现状态：

- 输入/输出路径、resolution、bitrate 和 codec 值会被拼接成一个参数字符串；
- codec family 尚未映射到唯一、经过验证的 encoder；
- 输出已存在时没有固定覆盖合同；
- 该 API 尚不能作为“所有枚举值均已支持”的证据。

后续实现必须改为结构化参数列表、确定性 encoder 映射、执行前参数拒绝和明确输出策略；执行失败后
不得自动切换 encoder。完成该合同前，设备验收应使用 `execute()` 与构建中已经确认的具体 encoder，
例如当前 H.264 测试路径使用的 `libopenh264`，并保留完整 return code、日志和输出回读证据。

## 示例

### 简单转码

```ts
const result = await Tools.FFmpeg.convert(
  '/sdcard/input.mp4',
  '/sdcard/output.mp4',
  {
    video_codec: 'h264',
    audio_codec: 'aac',
    resolution: '1920x1080',
    bitrate: '4000k'
  }
);
complete(result);
```

### 查询环境

```ts
const info = await Tools.FFmpeg.info();
console.log(info.toString());
```

## 返回值

三个 API 都返回 `FFmpegResultData`。该类型定义在 `results.d.ts` 中，包含执行结果与可读化输出。

## 相关文件

- `examples/types/ffmpeg.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
- [FFmpeg 架构与开发指南](../dev-core/FFMPEG_ARCHITECTURE.md)
- [Player native stack](../dev-core/PLAYER_NATIVE_STACK.md)
