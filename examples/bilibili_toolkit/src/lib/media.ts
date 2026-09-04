import { contextRoot, ensureDirectory, removeTemporaryPath, writeTextArtifact } from "./artifacts";
import { BilibiliClient } from "./client";
import { BilibiliError, failureDetails } from "./errors";
import {
  arrayAt,
  booleanAt,
  integerAt,
  numberAt,
  optionalRecord,
  recordAt,
  requireSafeAbsoluteAndroidPath,
  stringAt
} from "./json";
import type {
  JsonRecord,
  MediaStream,
  PlayFormats,
  ProgressiveSegment,
  VideoContext
} from "./types";

const MEDIA_HEADERS = {
  Referer: "https://www.bilibili.com/",
  "User-Agent":
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
};

const QUALITY_BY_NAME: Record<string, number> = {
  "360p": 16,
  "480p": 32,
  "720p": 64,
  "720p60": 74,
  "1080p": 80,
  "1080p+": 112,
  "1080p60": 116,
  "4k": 120,
  "hdr": 125,
  "dolby": 126,
  "8k": 127
};

const QUALITY_LABELS: Record<number, string> = {
  6: "240P",
  16: "360P",
  32: "480P",
  64: "720P",
  74: "720P60",
  80: "1080P",
  112: "1080P+",
  116: "1080P60",
  120: "4K",
  125: "HDR",
  126: "Dolby Vision",
  127: "8K"
};

export interface DownloadMediaOptions {
  quality: string;
  videoCodec: string | null;
  container: "mp4" | "mkv";
  audioOnly: boolean;
  audioFormat: "m4a" | "mp3" | "opus" | "flac" | "wav";
  outputRoot: string;
  overwrite: boolean;
  clipStart: number | null;
  clipEnd: number | null;
}

export async function fetchPlayFormats(
  client: BilibiliClient,
  context: VideoContext,
  requestedQuality: string
): Promise<PlayFormats> {
  const requestedQn = requestedQualityNumber(requestedQuality);
  const common = {
    qn: requestedQn === null ? 127 : requestedQn,
    fnval: 4048,
    fnver: 0,
    fourk: 1
  };
  const payload =
    context.episodeId === null
      ? await client.api(
          "/x/player/wbi/playurl",
          { ...common, bvid: context.bvid, cid: context.cid },
          true
        )
      : await client.api(
          "/pgc/player/web/v2/playurl",
          { ...common, ep_id: context.episodeId }
        );
  const play = extractPlayInfo(payload);
  const dash = recordAt(play, "dash");
  const video =
    dash === null
      ? []
      : arrayAt(dash, "video")
          .map((value) => optionalRecord(value))
          .filter((value): value is JsonRecord => value !== null)
          .map((value) => mediaStream(value, "video"));
  const audioRecords: JsonRecord[] = [];
  if (dash !== null) {
    arrayAt(dash, "audio").forEach((value) => {
      const record = optionalRecord(value);
      if (record !== null) {
        audioRecords.push(record);
      }
    });
    const dolby = recordAt(dash, "dolby");
    if (dolby !== null) {
      arrayAt(dolby, "audio").forEach((value) => {
        const record = optionalRecord(value);
        if (record !== null) {
          audioRecords.push(record);
        }
      });
    }
    const flac = recordAt(dash, "flac");
    const flacAudio = flac === null ? null : recordAt(flac, "audio");
    if (flacAudio !== null) {
      audioRecords.push(flacAudio);
    }
  }
  const audio = deduplicateAudio(audioRecords.map((value) => mediaStream(value, "audio")));
  const progressive =
    arrayAt(play, "durl")
      .map((value) => optionalRecord(value))
      .filter((value): value is JsonRecord => value !== null)
      .map((value, index) => progressiveSegment(value, index + 1));
  const acceptQualities = arrayAt(play, "accept_quality")
    .filter((value): value is number => typeof value === "number" && Number.isInteger(value));
  const acceptDescriptions = arrayAt(play, "accept_description")
    .filter((value): value is string => typeof value === "string");
  const formats: PlayFormats = {
    quality: integerAt(play, "quality"),
    durationMs: numberAt(play, "timelength"),
    acceptQualities,
    acceptDescriptions,
    video,
    audio,
    progressive,
    preview:
      booleanAt(play, "is_preview") === true ||
      stringAt(play, "play_video_type") === "preview",
    drm: play.has_drm === true || play.has_drm === 1 ||
      (typeof play.drm_tech_type === "number" && play.drm_tech_type > 0) || recordAt(play, "drm_tech_type") !== null
  };
  if (formats.video.length === 0 && formats.progressive.length === 0) {
    const restriction =
      formats.preview
        ? "Only a preview was returned for this account."
        : "No playable stream was returned. The video may require membership, be region-limited, removed, or protected.";
    throw new BilibiliError("NO_PLAYABLE_STREAM", restriction, { preview: formats.preview, drm: formats.drm });
  }
  return formats;
}

export function sanitizePlayFormats(formats: PlayFormats): JsonRecord {
  return {
    quality: formats.quality,
    duration_ms: formats.durationMs,
    accept_qualities: formats.acceptQualities,
    accept_descriptions: formats.acceptDescriptions,
    preview: formats.preview,
    drm: formats.drm,
    video: formats.video.map(sanitizeStream),
    audio: formats.audio.map(sanitizeStream),
    progressive: formats.progressive.map((segment) => ({
      order: segment.order,
      length: segment.length,
      size: segment.size
    }))
  };
}

export async function downloadMedia(
  client: BilibiliClient,
  context: VideoContext,
  options: DownloadMediaOptions
): Promise<JsonRecord> {
  validateClip(options.clipStart, options.clipEnd);
  const formats = await fetchPlayFormats(client, context, options.quality);
  if (formats.drm) {
    throw new BilibiliError("DRM_PROTECTED", "该媒体受 DRM 保护，不支持下载。");
  }
  if (formats.preview) throw new BilibiliError("PREVIEW_ONLY", "当前账号仅返回试看内容，不能作为完整视频下载。", { preview: true });
  if (options.clipEnd !== null && formats.durationMs !== null && options.clipEnd > formats.durationMs / 1000) {
    throw new BilibiliError("CLIP_OUT_OF_RANGE", "剪辑结束时间超过媒体时长。", { duration_seconds: formats.durationMs / 1000 });
  }
  const root = contextRoot(context, options.outputRoot);
  const mediaDir = root + "/media";
  const workDir = mediaDir + "/.work-" + String(Date.now()) + "-" + Math.random().toString(36).slice(2);
  await ensureDirectory(mediaDir);
  const finalName = mediaFileName(options);
  const publishedPath = mediaDir + "/" + finalName;
  await prepareFinalPath(publishedPath, options.overwrite);
  await ensureDirectory(workDir);
  // 下载与转码只写临时目录，成功核验后才发布，失败不破坏原有媒体。
  const finalPath = workDir + "/" + finalName;

  try {
    let sourcePath: string;
    let selected: JsonRecord;
    if (options.audioOnly) {
      const audio = selectAudio(formats.audio);
      const input = workDir + "/audio.m4s";
      await downloadUrl(audio.baseUrl, input);
      sourcePath = options.clipStart === null ? finalPath : workDir + "/audio-full." + options.audioFormat;
      await executeFfmpeg(audioCommand(input, sourcePath, options.audioFormat));
      selected = sanitizeStream(audio);
    } else if (formats.video.length > 0) {
      const video = selectVideo(formats.video, options.quality, options.videoCodec);
      const audio = formats.audio.length === 0 ? null : selectAudio(formats.audio);
      const videoPath = workDir + "/video.m4s";
      const fullPath =
        options.clipStart === null ? finalPath : workDir + "/video-full." + options.container;
      await downloadUrl(video.baseUrl, videoPath);
      if (audio === null) {
        await executeFfmpeg(
          "-y -i " + ffmpegArg(videoPath) + " -map 0:v:0 -c copy " + ffmpegArg(fullPath)
        );
      } else {
        const audioPath = workDir + "/audio.m4s";
        await downloadUrl(audio.baseUrl, audioPath);
        await executeFfmpeg(
          "-y -i " +
            ffmpegArg(videoPath) +
            " -i " +
            ffmpegArg(audioPath) +
            " -map 0:v:0 -map 1:a:0 -c copy " +
            ffmpegArg(fullPath)
        );
      }
      sourcePath = fullPath;
      selected = {
        video: sanitizeStream(video),
        audio: audio === null ? null : sanitizeStream(audio)
      };
    } else {
      const fullPath =
        options.clipStart === null ? finalPath : workDir + "/video-full." + options.container;
      await downloadProgressive(formats.progressive, workDir, fullPath);
      sourcePath = fullPath;
      selected = { progressive_segments: formats.progressive.length };
    }

    if (options.clipStart !== null && options.clipEnd !== null) {
      await executeFfmpeg(
        "-y -ss " +
          options.clipStart +
          " -to " +
          options.clipEnd +
          " -i " +
          ffmpegArg(sourcePath) +
          " -c copy " +
          ffmpegArg(finalPath)
      );
    }
    const info = await Tools.Files.info(finalPath, "android");
    if (!info.exists || info.size <= 0) {
      throw new Error("Media output was not created or is empty.");
    }
    const probe = await Tools.FFmpeg.probe(finalPath);
    if (probe.returnCode !== 0 || probe.mediaInfo === undefined) throw new BilibiliError("INVALID_MEDIA", "已生成文件，但 FFprobe 无法验证媒体。");
    const duration = Number(probe.mediaInfo.duration);
    if (!Number.isFinite(duration) || duration <= 0) throw new BilibiliError("INVALID_MEDIA", "生成媒体缺少有效时长。");
    await prepareFinalPath(publishedPath, options.overwrite);
    const moved = await Tools.Files.move(finalPath, publishedPath, "android");
    if (!moved.successful) throw new BilibiliError("OUTPUT_PUBLISH_FAILED", "媒体已处理，但发布文件失败。", { path: publishedPath });
    return {
      success: true,
      output: publishedPath,
      bytes: info.size,
      duration_seconds: duration,
      clip: options.clipStart === null ? null : {
        requested_start: options.clipStart,
        requested_end: options.clipEnd,
        mode: "keyframe_aligned_stream_copy",
        note: "无重编码剪辑按关键帧边界处理，实际时长可能与请求区间不同。"
      },
      selected,
      built_in_ffmpeg: true,
      cookie_exposed_to_javascript: false
    };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.error("Bilibili media download failed: " + detail);
    throw error;
  } finally {
    try {
      await removeTemporaryPath(workDir);
    } catch (cleanupError) {
      const detail =
        cleanupError instanceof Error ? cleanupError.message : String(cleanupError);
      console.error("Bilibili temporary media cleanup failed: " + detail);
    }
  }
}

export async function extractFrames(
  inputPath: string,
  outputDirectory: string,
  count: number,
  width: number,
  overwrite: boolean
): Promise<JsonRecord> {
  const input = requireSafeAbsoluteAndroidPath(inputPath, "input_path");
  const output = requireSafeAbsoluteAndroidPath(outputDirectory, "output_directory");
  if (!Number.isInteger(count) || count < 1 || count > 30) {
    throw new Error("count must be an integer from 1 to 30.");
  }
  if (!Number.isInteger(width) || width < 160 || width > 3840) {
    throw new Error("width must be an integer from 160 to 3840.");
  }
  const probe = await Tools.FFmpeg.probe(input);
  if (probe.returnCode !== 0 || probe.mediaInfo === undefined) {
    throw new Error("Built-in FFprobe could not inspect the input media.");
  }
  const duration = Number(probe.mediaInfo.duration);
  if (!Number.isFinite(duration) || duration <= 0) {
    throw new Error("Input media duration is unavailable.");
  }
  await ensureDirectory(output);
  const existingFrames = (await Tools.Files.list(output, "android")).entries
    .filter((entry) => /^frame-[0-9]{3}\.jpg$/.test(entry.name));
  if (existingFrames.some((entry) => entry.isDirectory)) throw new BilibiliError("INVALID_OUTPUT", "抽帧文件名被目录占用。");
  if (existingFrames.length > 0 && !overwrite) {
    throw new BilibiliError("OUTPUT_EXISTS", "抽帧产物已存在；设置 overwrite=true 可覆盖重跑。", { path: output, action: "set_overwrite_true" });
  }
  const interval = duration / (count + 1);
  const pattern = output + "/frame-%03d.jpg";
  await executeFfmpeg(
    "-y -ss " +
      interval.toFixed(3) +
      " -i " +
      ffmpegArg(input) +
      " -vf " +
      ffmpegArg("fps=1/" + interval.toFixed(6) + ",scale=" + width + ":-2") +
      " -frames:v " +
      count +
      " -q:v 2 " +
      ffmpegArg(pattern)
  );
  const outputs: string[] = [];
  for (let frame = 1; frame <= count; frame += 1) {
    const path = output + "/frame-" + String(frame).padStart(3, "0") + ".jpg";
    const info = await Tools.Files.info(path, "android");
    if (!info.exists || info.size <= 0) throw new BilibiliError("INCOMPLETE_FRAMES", "抽帧进程结束，但未生成全部非空图片。", { expected_count: count, actual_count: outputs.length, path });
    outputs.push(path);
  }
  if (overwrite) {
    for (const previous of existingFrames) {
      const path = output + "/" + previous.name;
      if (!outputs.includes(path)) {
        const removed = await Tools.Files.deleteFile(path, false, "android");
        if (!removed.successful) throw new BilibiliError("OUTPUT_CLEANUP_FAILED", "无法清理上一次抽帧的多余图片。", { path });
      }
    }
  }
  return {
    success: true,
    input,
    output_directory: output,
    output_pattern: pattern,
    requested_count: count,
    actual_count: outputs.length,
    outputs,
    interval_seconds: interval,
    built_in_ffmpeg: true
  };
}

export function requestedQualityNumber(value: string): number | null {
  const normalized = value.trim().toLowerCase();
  if (normalized === "best") {
    return null;
  }
  const quality = QUALITY_BY_NAME[normalized];
  if (quality === undefined) {
    throw new Error(
      "quality must be best, 360p, 480p, 720p, 720p60, 1080p, 1080p+, 1080p60, 4k, hdr, dolby, or 8k."
    );
  }
  return quality;
}

function extractPlayInfo(payload: JsonRecord): JsonRecord {
  const data = recordAt(payload, "data");
  const result = recordAt(payload, "result");
  const dataResult = data === null ? null : recordAt(data, "result");
  const raw = recordAt(payload, "raw");
  const rawData = raw === null ? null : recordAt(raw, "data");
  const rawResult = rawData === null ? null : recordAt(rawData, "result");
  const candidates = [
    dataResult === null ? null : recordAt(dataResult, "video_info"),
    data === null ? null : recordAt(data, "video_info"),
    result === null ? null : recordAt(result, "video_info"),
    rawResult === null ? null : recordAt(rawResult, "video_info"),
    dataResult,
    data,
    result
  ];
  const play = candidates.find((value): value is JsonRecord => value !== null);
  if (play === undefined) {
    throw new Error("Bilibili playurl response has an unsupported structure.");
  }
  return play;
}

function mediaStream(value: JsonRecord, kind: "video" | "audio"): MediaStream {
  const baseUrl = stringAt(value, "baseUrl") || stringAt(value, "base_url");
  const id = integerAt(value, "id");
  if (baseUrl === null || id === null || !baseUrl.startsWith("https://")) {
    throw new Error("Bilibili DASH stream is missing an HTTPS URL or format id.");
  }
  const codecId = integerAt(value, "codecid") || 0;
  return {
    kind,
    id,
    quality: kind === "video" ? id : 0,
    qualityLabel: kind === "video" ? qualityLabel(id) : "audio",
    codec: stringAt(value, "codecs") || codecName(codecId),
    codecId,
    width: integerAt(value, "width"),
    height: integerAt(value, "height"),
    frameRate: stringAt(value, "frameRate") || stringAt(value, "frame_rate"),
    bandwidth: integerAt(value, "bandwidth"),
    mimeType: stringAt(value, "mimeType") || stringAt(value, "mime_type") || "",
    baseUrl
  };
}

function progressiveSegment(value: JsonRecord, order: number): ProgressiveSegment {
  const url = stringAt(value, "url");
  if (url === null || !url.startsWith("https://")) {
    throw new Error("Bilibili progressive segment is missing an HTTPS URL.");
  }
  return {
    order,
    length: integerAt(value, "length"),
    size: integerAt(value, "size"),
    url
  };
}

function sanitizeStream(stream: MediaStream): JsonRecord {
  return {
    kind: stream.kind,
    id: stream.id,
    quality: stream.quality,
    quality_label: stream.qualityLabel,
    codec: stream.codec,
    codec_id: stream.codecId,
    width: stream.width,
    height: stream.height,
    frame_rate: stream.frameRate,
    bandwidth: stream.bandwidth,
    mime_type: stream.mimeType
  };
}

function selectVideo(
  streams: MediaStream[],
  qualityName: string,
  codecNameValue: string | null
): MediaStream {
  const requested = requestedQualityNumber(qualityName);
  const quality =
    requested === null
      ? Math.max(...streams.map((stream) => stream.quality))
      : requested;
  const atQuality = streams.filter((stream) => stream.quality === quality);
  if (atQuality.length === 0) {
    const available = Array.from(new Set(streams.map((stream) => stream.quality)))
      .sort((left, right) => right - left)
      .map(qualityLabel)
      .join(", ");
    throw new BilibiliError("QUALITY_UNAVAILABLE", "请求的清晰度不可用。可用清晰度：" + available, { requested_quality: qualityName, available_qualities: available });
  }
  if (codecNameValue === null) {
    return atQuality[0];
  }
  const normalized = codecNameValue.trim().toLowerCase();
  if (!["avc", "hevc", "av1"].includes(normalized)) {
    throw new Error("video_codec must be avc, hevc, or av1.");
  }
  const selected = atQuality.find((stream) => codecFamily(stream) === normalized);
  if (selected === undefined) {
    throw new Error(
      "Requested codec " +
        normalized +
        " is unavailable at " +
        qualityLabel(quality) +
        "."
    );
  }
  return selected;
}

function selectAudio(streams: MediaStream[]): MediaStream {
  if (streams.length === 0) {
    throw new Error("Bilibili playurl response did not provide a downloadable audio stream.");
  }
  return [...streams].sort(
    (left, right) => (right.bandwidth || 0) - (left.bandwidth || 0)
  )[0];
}

function deduplicateAudio(streams: MediaStream[]): MediaStream[] {
  const seen = new Set<string>();
  return streams.filter((stream) => {
    const key = stream.id + ":" + stream.codec + ":" + stream.bandwidth;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

async function downloadUrl(url: string, destination: string): Promise<void> {
  try {
    const result = await Tools.Files.download(url, destination, "android", MEDIA_HEADERS);
    if (!result.successful) throw new Error(result.details);
  } catch (error) {
    const detail = failureDetails(error);
    const message = typeof detail.message === "string" ? detail.message.replace(/https?:\/\/[^\s"'<>]+/g, value => value.split("?", 1)[0]) : "Media download failed.";
    console.error("Bilibili media transfer failed: " + message);
    throw new BilibiliError("MEDIA_DOWNLOAD_FAILED", message, {
      phase: "media_download", endpoint: url.split("?", 1)[0],
      referer_present: true, user_agent_present: true, cookie_sent: false
    });
  }
}

async function downloadProgressive(
  segments: ProgressiveSegment[],
  workDir: string,
  outputPath: string
): Promise<void> {
  if (segments.length === 0) {
    throw new Error("No progressive segments are available.");
  }
  const paths: string[] = [];
  for (const segment of segments) {
    const path = workDir + "/segment-" + String(segment.order).padStart(3, "0") + ".flv";
    await downloadUrl(segment.url, path);
    paths.push(path);
  }
  if (paths.length === 1) {
    await executeFfmpeg("-y -i " + ffmpegArg(paths[0]) + " -c copy " + ffmpegArg(outputPath));
    return;
  }
  const listPath = workDir + "/segments.ffconcat";
  const list =
    "ffconcat version 1.0\n" +
    paths.map((path) => "file '" + path + "'").join("\n") +
    "\n";
  await writeTextArtifact(listPath, list, true);
  await executeFfmpeg(
    "-y -f concat -safe 0 -i " + ffmpegArg(listPath) + " -c copy " + ffmpegArg(outputPath)
  );
}

async function executeFfmpeg(command: string): Promise<void> {
  const result = await Tools.FFmpeg.execute(command);
  if (result.returnCode !== 0) {
    const output = result.output
      .replace(/https:\/\/[^\s]+/g, (url) => url.split("?", 1)[0])
      .slice(-2_000);
    throw new Error(
      "Kiyori built-in FFmpeg failed with return code " + result.returnCode + ": " + output
    );
  }
}

async function prepareFinalPath(path: string, overwrite: boolean): Promise<void> {
  const existing = await Tools.Files.exists(path, "android");
  if (!existing.exists) {
    return;
  }
  if (!overwrite) {
    throw new BilibiliError("OUTPUT_EXISTS", "产物已存在；设置 overwrite=true 可覆盖重跑。", { path, action: "set_overwrite_true" });
  }
  if (existing.isDirectory) throw new BilibiliError("INVALID_OUTPUT", "输出路径是目录，不能覆盖。", { path });
}

function mediaFileName(options: DownloadMediaOptions): string {
  const clip =
    options.clipStart === null
      ? ""
      : "-clip-" +
        String(options.clipStart).replace(".", "_") +
        "-" +
        String(options.clipEnd).replace(".", "_");
  if (options.audioOnly) {
    return "audio" + clip + "." + options.audioFormat;
  }
  const quality = options.quality === "best" ? "" : "-" + options.quality.replace("+", "plus");
  return "video" + quality + clip + "." + options.container;
}

function audioCommand(
  input: string,
  output: string,
  format: DownloadMediaOptions["audioFormat"]
): string {
  const codec =
    format === "m4a"
      ? "-c:a copy"
      : format === "mp3"
        ? "-c:a libmp3lame -q:a 2"
        : format === "opus"
          ? "-c:a libopus -b:a 160k"
          : format === "flac"
            ? "-c:a flac"
            : "-c:a pcm_s16le";
  return "-y -i " + ffmpegArg(input) + " -vn " + codec + " " + ffmpegArg(output);
}

function ffmpegArg(value: string): string {
  if (value.includes("\"") || value.includes("\r") || value.includes("\n")) {
    throw new Error("FFmpeg argument contains unsupported control characters.");
  }
  return "\"" + value.replace(/\\/g, "\\\\") + "\"";
}

function validateClip(start: number | null, end: number | null): void {
  if (start === null && end === null) {
    return;
  }
  if (
    start === null ||
    end === null ||
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    start < 0 ||
    end <= start
  ) {
    throw new Error("clip_start and clip_end must define a positive increasing range in seconds.");
  }
}

function codecFamily(stream: MediaStream): string {
  const codecs = stream.codec.toLowerCase();
  if (stream.codecId === 13 || codecs.includes("av01")) {
    return "av1";
  }
  if (stream.codecId === 12 || codecs.includes("hev") || codecs.includes("hvc")) {
    return "hevc";
  }
  return "avc";
}

function codecName(codecId: number): string {
  if (codecId === 13) {
    return "av01";
  }
  if (codecId === 12) {
    return "hev1";
  }
  if (codecId === 7) {
    return "avc1";
  }
  return "unknown";
}

function qualityLabel(quality: number): string {
  return QUALITY_LABELS[quality] || "QN" + quality;
}
