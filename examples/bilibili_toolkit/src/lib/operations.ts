import { contextRoot, DEFAULT_OUTPUT_ROOT, ensureDirectory, writeJsonArtifact, writeTextArtifact } from "./artifacts";
import { BilibiliClient } from "./client";
import { BilibiliError, failureDetails } from "./errors";
import { danmakuXml, fetchDanmakuSegments } from "./danmaku";
import { parseDanmakuXml, renderDanmaku, renderSubtitles, subtitleCues, subtitleTracks } from "./content";
import {
  accountList,
  aiSummary,
  fetchComments,
  interactiveGraph,
  publicUser,
  resolveVideoForData,
  searchVideos
} from "./data";
import { boundedInteger, integerAt, recordAt, requireSafeAbsoluteAndroidPath, safePathComponent, stringAt } from "./json";
import { downloadMedia, extractFrames, fetchPlayFormats, sanitizePlayFormats } from "./media";
import { resolveTarget } from "./target";
import type { AccountListKind } from "./data";
import type { DanmakuEntry, JsonRecord, JsonValue, SubtitleTrack, VideoContext } from "./types";
import { compactSeason, compactVideoContext, getSeasonData } from "./video";

export interface TargetParams {
  target: string;
  part?: number;
}

export interface SearchParams {
  keyword: string;
  page?: number;
  pages?: number;
  limit?: number;
  order?: string;
  duration?: number;
}

export interface SubtitlesParams extends TargetParams {
  language?: string;
  all_languages?: boolean;
  format?: string;
  output_root?: string;
  overwrite?: boolean;
  inline_limit?: number;
}

export interface DanmakuParams extends TargetParams {
  source?: string;
  format?: string;
  limit?: number;
  output_root?: string;
  overwrite?: boolean;
  inline?: boolean;
  width?: number;
  height?: number;
}

export interface CommentsParams extends TargetParams {
  sort?: string;
  pages?: number;
  limit?: number;
  include_replies?: boolean;
  reply_pages?: number;
  reply_limit?: number;
  output?: boolean;
  output_root?: string;
  overwrite?: boolean;
}

export interface UserParams {
  user: string;
  full?: boolean;
}

export interface AccountParams {
  kind: string;
  mid?: number;
  folder_id?: number;
  page?: number;
  pages?: number;
  page_size?: number;
  limit?: number;
  keyword?: string;
  history_type?: string;
  output?: boolean;
  output_root?: string;
  overwrite?: boolean;
}

export interface InteractiveParams extends TargetParams {
  start_edge?: number;
  max_nodes?: number;
  output?: boolean;
  output_root?: string;
  overwrite?: boolean;
}

export interface SummaryParams extends TargetParams {
  include_transcript?: boolean;
  output?: boolean;
  output_root?: string;
  overwrite?: boolean;
}

export interface FormatsParams extends TargetParams {
  quality?: string;
}

export interface DownloadParams extends TargetParams {
  quality?: string;
  video_codec?: string;
  container?: string;
  audio_only?: boolean;
  audio_format?: string;
  output_root?: string;
  overwrite?: boolean;
  clip_start?: number;
  clip_end?: number;
}

export interface FramesParams {
  input_path: string;
  output_directory?: string;
  count?: number;
  width?: number;
  overwrite?: boolean;
}

export interface CaptureParams extends TargetParams {
  subtitles?: boolean;
  subtitle_format?: string;
  danmaku?: boolean;
  danmaku_format?: string;
  danmaku_limit?: number;
  comments?: boolean;
  comment_limit?: number;
  summary?: boolean;
  media?: boolean;
  quality?: string;
  video_codec?: string;
  container?: string;
  frames?: boolean;
  frame_count?: number;
  output_root?: string;
  overwrite?: boolean;
}

export async function doctor(): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const nav = await client.nav();
  const data = recordAt(nav, "data");
  const ffmpeg = await Tools.FFmpeg.info("summary");
  const loggedIn = data !== null && data.isLogin === true;
  return {
    success: ffmpeg.returnCode === 0,
    plugin: "com.kiyori.bilibili_toolkit",
    script: "bilibili",
    cookie_configured: client.cookieConfigured,
    logged_in: loggedIn,
    account:
      !loggedIn || data === null
        ? null
        : {
            mid: integerAt(data, "mid"),
            name: stringAt(data, "uname"),
            face: stringAt(data, "face")
          },
    built_in_ffmpeg: {
      available: ffmpeg.returnCode === 0,
      execution_plane: ffmpeg.executionPlane,
      terminal_state: ffmpeg.terminalState === undefined ? null : ffmpeg.terminalState
    },
    yt_dlp_present: false,
    cookie_exposed_to_javascript: false,
    note:
      loggedIn
        ? "Cookie is valid for the current account."
        : "Public tools are available; configure BILIBILI_COOKIE for account lists and entitled formats."
  };
}

export async function resolve(params: TargetParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const target = await resolveTarget(client, params.target);
  if (params.part !== undefined) {
    if (target.kind !== "video" || !Number.isSafeInteger(params.part) || params.part < 1) {
      throw new BilibiliError("INVALID_ARGUMENT", "part 只适用于视频，且必须为正整数。");
    }
    target.part = params.part;
    target.url = target.url.split("?", 1)[0] + "?p=" + params.part;
  }
  return { success: true, target };
}

export async function search(params: SearchParams): Promise<JsonRecord> {
  const order = parseSearchOrder(params.order);
  const duration = parseDuration(params.duration);
  return searchVideos(
    new BilibiliClient(),
    params.keyword,
    params.page === undefined ? 1 : params.page,
    params.pages === undefined ? 1 : params.pages,
    params.limit === undefined ? 20 : params.limit,
    order,
    duration
  );
}

export async function info(params: TargetParams): Promise<JsonRecord> {
  const context = await resolveVideoForData(new BilibiliClient(), params.target, params.part);
  return { success: true, data: compactVideoContext(context) };
}

export async function season(params: TargetParams): Promise<JsonRecord> {
  const data = await getSeasonData(new BilibiliClient(), params.target);
  return {
    success: true,
    target: data.target,
    season: compactSeason(
      data.season,
      data.selectedEpisode === null ? null : integerAt(data.selectedEpisode, "id")
    )
  };
}

export async function subtitles(params: SubtitlesParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  return subtitlesForContext(client, context, params);
}

async function subtitlesForContext(client: BilibiliClient, context: VideoContext, params: SubtitlesParams): Promise<JsonRecord> {
  const tracks = selectTracks(
    await subtitleTracks(client, context),
    params.language,
    params.all_languages === true
  );
  const formats = parseFormats(params.format, ["json", "srt", "vtt", "txt", "ass"]);
  const root = contextRoot(context, outputRoot(params.output_root)) + "/subtitles";
  const overwrite = params.overwrite === true;
  const inlineLimit = boundedInteger(
    params.inline_limit === undefined ? 0 : params.inline_limit,
    "inline_limit",
    0,
    200,
    0
  );
  await ensureDirectory(root);
  const outputs: string[] = [];
  const responseTracks: JsonRecord[] = [];
  for (const track of tracks) {
    const cues = await subtitleCues(client, track);
    const language = safePathComponent(track.language, 24);
    for (const format of formats) {
      const output = root + "/" + language + "." + format;
      await writeTextArtifact(output, renderSubtitles(cues, format), overwrite);
      outputs.push(output);
    }
    responseTracks.push({
      id: track.id,
      language: track.language,
      language_doc: track.language_doc,
      ai_type: track.ai_type,
      ai_status: track.ai_status,
      cue_count: cues.length,
      cues: inlineLimit === 0 ? [] : cues.slice(0, inlineLimit)
    });
  }
  if (tracks.length === 0) {
    const status = root + "/status.json";
    await writeJsonArtifact(
      status,
      {
        success: true,
        available: false,
        note: "No subtitles were exposed for this video and account."
      },
      overwrite
    );
    outputs.push(status);
  }
  return {
    success: true,
    video: compactVideoContext(context),
    formats,
    track_count: responseTracks.length,
    available: responseTracks.length > 0,
    tracks: responseTracks,
    outputs,
    note: tracks.length === 0 ? "该视频在当前账号下没有可用 CC 字幕。" : null
  };
}

export async function danmaku(params: DanmakuParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  return danmakuForContext(client, context, params);
}

async function danmakuForContext(client: BilibiliClient, context: VideoContext, params: DanmakuParams): Promise<JsonRecord> {
  const limit = boundedInteger(params.limit === undefined ? 1_000 : params.limit, "limit", 1, 5_000, 1_000);
  const width = boundedInteger(params.width === undefined ? 1920 : params.width, "width", 320, 7680, 1920);
  const height = boundedInteger(params.height === undefined ? 1080 : params.height, "height", 240, 4320, 1080);
  const formats = parseFormats(params.format, ["xml", "json", "txt", "ass"]);
  const source = params.source ?? "segments";
  if (source !== "segments" && source !== "xml") throw new BilibiliError("INVALID_ARGUMENT", "source 必须是 segments 或 xml。");
  const segmentResult = source === "segments" ? await fetchDanmakuSegments(client, context, limit) : null;
  const snapshotXml = source === "xml" ? await client.publicText("https://comment.bilibili.com/" + context.cid + ".xml") : null;
  let allEntries: DanmakuEntry[];
  if (segmentResult !== null) allEntries = segmentResult.entries;
  else {
    if (snapshotXml === null) throw new Error("弹幕来源与响应不一致。");
    allEntries = parseDanmakuXml(snapshotXml, 100_000);
  }
  const entries = allEntries.slice(0, limit);
  const xml = danmakuXml(entries);
  const root = contextRoot(context, outputRoot(params.output_root)) + "/danmaku";
  await ensureDirectory(root);
  const outputs: string[] = [];
  for (const format of formats) {
    const output = root + "/current." + format;
    const content = format === "xml" ? xml : renderDanmaku(entries, format, width, height);
    await writeTextArtifact(output, content, params.overwrite === true);
    outputs.push(output);
  }
  const platformStat = recordAt(context.info, "stat");
  return {
    success: true,
    video: compactVideoContext(context),
    cid: context.cid,
    count: entries.length,
    snapshot_count: allEntries.length,
    truncated: segmentResult === null ? entries.length < allEntries.length : segmentResult.truncated,
    limit,
    scope: source === "segments" ? "current_protobuf_segments" : "current_xml_snapshot",
    fetched_segments: segmentResult === null ? null : segmentResult.fetchedSegments,
    total_segments: segmentResult === null ? null : segmentResult.totalSegments,
    empty_reason: entries.length === 0 ? "当前接口返回为空，不代表平台累计弹幕为零；历史、删除和过滤弹幕不在承诺范围。" : null,
    historical_complete: false,
    platform_cumulative_count: platformStat === null ? null : integerAt(platformStat, "danmaku"),
    completeness_note:
      "仅导出当前可播放弹幕；XML也是当前快照，不是历史全集。平台累计计数不是当前接口条数。达到条数或50段上限时明确标记 truncated。",
    outputs,
    danmaku: params.inline === true ? entries : []
  };
}

export async function comments(params: CommentsParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  return commentsForContext(client, context, params);
}

async function commentsForContext(client: BilibiliClient, context: VideoContext, params: CommentsParams): Promise<JsonRecord> {
  const result = await fetchComments(client, context, {
    sort: parseCommentSort(params.sort),
    pages: params.pages === undefined ? 1 : params.pages,
    limit: params.limit === undefined ? 20 : params.limit,
    includeReplies: params.include_replies === true,
    replyPages: params.reply_pages === undefined ? 0 : params.reply_pages,
    replyLimit: params.reply_limit === undefined ? 50 : params.reply_limit
  });
  if (params.output !== true) {
    return result;
  }
  const output = contextRoot(context, outputRoot(params.output_root)) + "/comments/comments.json";
  await ensureDirectory(contextRoot(context, outputRoot(params.output_root)) + "/comments");
  await writeJsonArtifact(output, result, params.overwrite === true);
  return compactWrittenResult(result, output, ["root_count", "reply_count", "reported_total", "sort", "video"]);
}

export async function user(params: UserParams): Promise<JsonRecord> {
  return publicUser(new BilibiliClient(), params.user, params.full === true);
}

export async function account(params: AccountParams): Promise<JsonRecord> {
  const kind = parseAccountKind(params.kind);
  const result = await accountList(new BilibiliClient(), {
    kind,
    mid: params.mid,
    folderId: params.folder_id,
    page: params.page === undefined ? 1 : params.page,
    pages: params.pages === undefined ? 1 : params.pages,
    pageSize: params.page_size === undefined ? 20 : params.page_size,
    limit: params.limit === undefined ? 50 : params.limit,
    keyword: params.keyword,
    historyType: parseHistoryType(params.history_type)
  });
  if (params.output !== true) {
    return result;
  }
  const root = outputRoot(params.output_root) + "/account";
  await ensureDirectory(root);
  const output = root + "/" + kind + ".json";
  await writeJsonArtifact(output, result, params.overwrite === true);
  return compactWrittenResult(result, output, ["kind", "mid", "count", "total", "next_cursor"]);
}

export async function interactive(params: InteractiveParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  const result = await interactiveGraph(
    client,
    context,
    params.start_edge === undefined ? 0 : params.start_edge,
    params.max_nodes === undefined ? 50 : params.max_nodes
  );
  if (params.output !== true) {
    return result;
  }
  const root = contextRoot(context, outputRoot(params.output_root)) + "/interactive";
  await ensureDirectory(root);
  const output = root + "/graph.json";
  await writeJsonArtifact(output, result, params.overwrite === true);
  return compactWrittenResult(result, output, ["video", "graph_version", "node_count", "discovered_node_count", "truncated"]);
}

export async function summary(params: SummaryParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  return summaryForContext(client, context, params);
}

async function summaryForContext(client: BilibiliClient, context: VideoContext, params: SummaryParams): Promise<JsonRecord> {
  const result = await aiSummary(client, context, params.include_transcript === true);
  if (params.output !== true) {
    return result;
  }
  const root = contextRoot(context, outputRoot(params.output_root)) + "/summary";
  await ensureDirectory(root);
  const output = root + "/summary.json";
  await writeJsonArtifact(output, result, params.overwrite === true);
  return compactWrittenResult(result, output, ["video"]);
}

export async function formats(params: FormatsParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  const result = await fetchPlayFormats(client, context, params.quality === undefined ? "best" : params.quality);
  return { success: true, video: compactVideoContext(context), formats: sanitizePlayFormats(result) };
}

export async function download(params: DownloadParams): Promise<JsonRecord> {
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  return downloadForContext(client, context, params);
}

async function downloadForContext(client: BilibiliClient, context: VideoContext, params: DownloadParams): Promise<JsonRecord> {
  return downloadMedia(client, context, {
    quality: params.quality === undefined ? "best" : params.quality,
    videoCodec: normalizedOptional(params.video_codec),
    container: parseContainer(params.container),
    audioOnly: params.audio_only === true,
    audioFormat: parseAudioFormat(params.audio_format),
    outputRoot: outputRoot(params.output_root),
    overwrite: params.overwrite === true,
    clipStart: params.clip_start === undefined ? null : params.clip_start,
    clipEnd: params.clip_end === undefined ? null : params.clip_end
  });
}

export async function frames(params: FramesParams): Promise<JsonRecord> {
  const input = requireSafeAbsoluteAndroidPath(params.input_path, "input_path");
  const output =
    params.output_directory === undefined
      ? input.replace(/\.[^/.]+$/, "") + "-frames"
      : requireSafeAbsoluteAndroidPath(params.output_directory, "output_directory");
  return extractFrames(
    input,
    output,
    params.count === undefined ? 8 : params.count,
    params.width === undefined ? 1280 : params.width,
    params.overwrite === true
  );
}

export async function capture(params: CaptureParams): Promise<JsonRecord> {
  const output = outputRoot(params.output_root);
  const overwrite = params.overwrite === true;
  const client = new BilibiliClient();
  const context = await resolveVideoForData(client, params.target, params.part);
  const root = contextRoot(context, output);
  const steps: JsonRecord[] = [];
  const metadataDirectory = root + "/metadata";
  await ensureDirectory(metadataDirectory);
  const metadataPath = metadataDirectory + "/video.json";
  const manifestPath = root + "/manifest.json";
  if (!overwrite) {
    for (const path of [metadataPath, manifestPath]) {
      if ((await Tools.Files.exists(path, "android")).exists) {
        throw new BilibiliError("OUTPUT_EXISTS", "抓取产物已存在；设置 overwrite=true 可覆盖重跑。", { path, action: "set_overwrite_true" });
      }
    }
  }
  await captureStep(steps, "metadata", async () => {
    await writeJsonArtifact(metadataPath, { success: true, data: compactVideoContext(context) }, overwrite);
    return { success: true, outputs: [metadataPath] };
  });

  if (params.subtitles !== false) {
    await captureStep(steps, "subtitles", async () =>
      subtitlesForContext(client, context, {
        target: context.target.url,
        part: context.part,
        all_languages: true,
        format: params.subtitle_format === undefined ? "all" : params.subtitle_format,
        output_root: output,
        overwrite
      })
    );
  }
  if (params.danmaku !== false) {
    await captureStep(steps, "danmaku", async () =>
      danmakuForContext(client, context, {
        target: context.target.url,
        part: context.part,
        format: params.danmaku_format === undefined ? "all" : params.danmaku_format,
        limit: params.danmaku_limit === undefined ? 1_000 : params.danmaku_limit,
        output_root: output,
        overwrite
      })
    );
  }
  if (params.comments !== false) {
    await captureStep(steps, "comments", async () =>
      commentsForContext(client, context, {
        target: context.target.url,
        part: context.part,
        limit: params.comment_limit === undefined ? 20 : params.comment_limit,
        include_replies: true,
        output: true,
        output_root: output,
        overwrite
      })
    );
  }
  if (params.summary !== false) {
    await captureStep(steps, "summary", async () =>
      summaryForContext(client, context, {
        target: context.target.url,
        part: context.part,
        output: true,
        output_root: output,
        overwrite
      })
    );
  }

  let mediaOutput: string | null = null;
  if (params.media === true) {
    const result = await captureStep(steps, "media", async () =>
      downloadForContext(client, context, {
        target: context.target.url,
        part: context.part,
        quality: params.quality,
        video_codec: params.video_codec,
        container: params.container,
        output_root: output,
        overwrite
      })
    );
    mediaOutput = result === null ? null : stringAt(result, "output");
  }
  if (params.frames === true) {
    if (mediaOutput === null) {
      steps.push({
        name: "frames",
        success: false,
        error: "frames=true requires media=true and a successful media output."
      });
    } else {
      await captureStep(steps, "frames", async () =>
        frames({
          input_path: mediaOutput,
          output_directory: root + "/frames",
          count: params.frame_count === undefined ? 8 : params.frame_count,
          overwrite
        })
      );
    }
  }
  const succeeded = steps.filter((step) => step.success === true).length;
  const manifest: JsonRecord = {
    success: succeeded === steps.length,
    partial: succeeded > 0 && succeeded < steps.length,
    message: succeeded === steps.length ? "全部抓取步骤完成。" : "抓取部分失败：" + steps.filter((step) => step.success !== true).map((step) => step.name).join("、"),
    captured_at: new Date().toISOString(),
    target: context.target,
    video: compactVideoContext(context),
    root,
    step_count: steps.length,
    succeeded_steps: succeeded,
    failed_steps: steps.length - succeeded,
    steps
  };
  manifest.manifest = manifestPath;
  try {
    await writeJsonArtifact(manifestPath, manifest, overwrite);
  } catch (error) {
    const detail = failureDetails(error);
    console.error("Bilibili manifest 写入失败：" + JSON.stringify(detail));
    manifest.success = false;
    manifest.message = "抓取结束，但步骤清单写入失败。";
    manifest.manifest_error = detail;
  }
  return manifest;
}

async function captureStep(
  steps: JsonRecord[],
  name: string,
  action: () => Promise<JsonRecord>
): Promise<JsonRecord | null> {
  try {
    const result = await action();
    steps.push({ name, success: result.success !== false, result });
    return result.success === false ? null : result;
  } catch (error) {
    const detail = failureDetails(error);
    console.error("Bilibili capture step " + name + " failed: " + JSON.stringify(detail));
    steps.push({ name, success: false, error: detail });
    return null;
  }
}

function selectTracks(
  tracks: SubtitleTrack[],
  languageValue: string | undefined,
  allLanguages: boolean
): SubtitleTrack[] {
  if (tracks.length === 0 || allLanguages) {
    return tracks;
  }
  const language = normalizedOptional(languageValue);
  if (language === null) {
    return [tracks[0]];
  }
  const normalized = language.toLowerCase();
  const selected = tracks.filter(
    (track) =>
      track.language.toLowerCase() === normalized ||
      track.language_doc.toLowerCase().includes(normalized)
  );
  if (selected.length === 0) {
    throw new Error(
      "Requested subtitle language is unavailable. Available languages: " +
        tracks.map((track) => track.language).join(", ") +
        "."
    );
  }
  return selected;
}

function parseFormats(value: string | undefined, supported: string[]): string[] {
  const normalized = value === undefined ? "json" : value.trim().toLowerCase();
  if (normalized === "all") {
    return [...supported];
  }
  if (!supported.includes(normalized)) {
    throw new Error("format must be " + supported.join(", ") + ", or all.");
  }
  return [normalized];
}

function outputRoot(value: string | undefined): string {
  return value === undefined
    ? DEFAULT_OUTPUT_ROOT
    : requireSafeAbsoluteAndroidPath(value, "output_root");
}

function compactWrittenResult(
  source: JsonRecord,
  output: string,
  keys: string[]
): JsonRecord {
  const result: JsonRecord = { success: true, output };
  keys.forEach((key) => {
    if (source[key] !== undefined) {
      result[key] = source[key];
    }
  });
  return result;
}

function parseSearchOrder(
  value: string | undefined
): "totalrank" | "click" | "pubdate" | "dm" | "stow" {
  const normalized = value === undefined ? "totalrank" : value.trim().toLowerCase();
  if (normalized === "totalrank" || normalized === "click" || normalized === "pubdate" || normalized === "dm" || normalized === "stow") {
    return normalized;
  }
  throw new Error("order must be totalrank, click, pubdate, dm, or stow.");
}

function parseDuration(value: number | undefined): 0 | 1 | 2 | 3 | 4 {
  if (value === undefined || value === 0 || value === 1 || value === 2 || value === 3 || value === 4) {
    return value === undefined ? 0 : value;
  }
  throw new Error("duration must be 0, 1, 2, 3, or 4.");
}

function parseCommentSort(value: string | undefined): "time" | "hot" {
  const normalized = value === undefined ? "hot" : value.trim().toLowerCase();
  if (normalized === "time" || normalized === "hot") {
    return normalized;
  }
  throw new Error("sort must be time or hot.");
}

function parseAccountKind(value: string): AccountListKind {
  const normalized = value.trim().toLowerCase().replace(/-/g, "_");
  if (
    normalized === "history" ||
    normalized === "watch_later" ||
    normalized === "favorites" ||
    normalized === "favorite_items" ||
    normalized === "liked" ||
    normalized === "coins" ||
    normalized === "followed_bangumi" ||
    normalized === "followed_cinema" ||
    normalized === "submissions"
  ) {
    return normalized;
  }
  throw new Error(
    "kind must be history, watch_later, favorites, favorite_items, liked, coins, followed_bangumi, followed_cinema, or submissions."
  );
}

function parseHistoryType(value: string | undefined): "all" | "archive" | "pgc" | "live" | "article" {
  const normalized = value === undefined ? "all" : value.trim().toLowerCase();
  if (normalized === "all" || normalized === "archive" || normalized === "pgc" || normalized === "live" || normalized === "article") {
    return normalized;
  }
  throw new Error("history_type must be all, archive, pgc, live, or article.");
}

function parseContainer(value: string | undefined): "mp4" | "mkv" {
  const normalized = value === undefined ? "mp4" : value.trim().toLowerCase();
  if (normalized === "mp4" || normalized === "mkv") {
    return normalized;
  }
  throw new Error("container must be mp4 or mkv.");
}

function parseAudioFormat(value: string | undefined): "m4a" | "mp3" | "opus" | "flac" | "wav" {
  const normalized = value === undefined ? "m4a" : value.trim().toLowerCase();
  if (normalized === "m4a" || normalized === "mp3" || normalized === "opus" || normalized === "flac" || normalized === "wav") {
    return normalized;
  }
  throw new Error("audio_format must be m4a, mp3, opus, flac, or wav.");
}

function normalizedOptional(value: string | undefined): string | null {
  if (value === undefined) {
    return null;
  }
  const normalized = value.trim();
  return normalized.length === 0 ? null : normalized;
}
