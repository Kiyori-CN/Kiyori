export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | JsonRecord;

export interface JsonRecord {
  [key: string]: JsonValue;
}

export type QueryValue = string | number | boolean | undefined;
export interface QueryParams {
  [key: string]: QueryValue;
}

export type BilibiliTargetKind =
  | "video"
  | "episode"
  | "season"
  | "media"
  | "space"
  | "favorites"
  | "watch_later"
  | "playlist"
  | "collection"
  | "series"
  | "category"
  | "search"
  | "dynamic"
  | "bilibili_url";

export interface ResolvedTarget extends JsonRecord {
  url: string;
  kind: BilibiliTargetKind;
  identifier: string | null;
  part: number | null;
  playlist_like: boolean;
  short_link_resolved: boolean;
}

export interface VideoPart extends JsonRecord {
  page: number;
  cid: number;
  part: string;
  duration: number;
}

export interface VideoContext {
  target: ResolvedTarget;
  info: JsonRecord;
  bvid: string;
  aid: number;
  cid: number;
  part: number;
  partInfo: VideoPart;
  episodeId: number | null;
  season: JsonRecord | null;
}

export interface SubtitleCue extends JsonRecord {
  from: number;
  to: number;
  content: string;
}

export interface SubtitleTrack extends JsonRecord {
  id: number;
  language: string;
  language_doc: string;
  source_url: string;
  ai_type: number;
  ai_status: number;
}

export interface DanmakuEntry extends JsonRecord {
  progress: number;
  mode: number;
  font_size: number;
  color: number;
  ctime: number;
  pool: number;
  sender_hash: string;
  row_id: string;
  text: string;
}

export type MediaStreamKind = "video" | "audio";

export interface MediaStream {
  kind: MediaStreamKind;
  id: number;
  quality: number;
  qualityLabel: string;
  codec: string;
  codecId: number;
  width: number | null;
  height: number | null;
  frameRate: string | null;
  bandwidth: number | null;
  mimeType: string;
  baseUrl: string;
}

export interface ProgressiveSegment {
  order: number;
  length: number | null;
  size: number | null;
  url: string;
}

export interface PlayFormats {
  quality: number | null;
  durationMs: number | null;
  acceptQualities: number[];
  acceptDescriptions: string[];
  video: MediaStream[];
  audio: MediaStream[];
  progressive: ProgressiveSegment[];
  preview: boolean;
  drm: boolean;
}

export interface ToolSuccess extends JsonRecord {
  success: true;
}
