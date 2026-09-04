import { BilibiliClient } from "./client";
import {
  arrayAt,
  integerAt,
  numberAt,
  optionalRecord,
  recordAt,
  stringAt
} from "./json";
import type {
  DanmakuEntry,
  JsonRecord,
  SubtitleCue,
  SubtitleTrack,
  VideoContext
} from "./types";

export async function subtitleTracks(
  client: BilibiliClient,
  context: VideoContext
): Promise<SubtitleTrack[]> {
  const payload = await client.api(
    "/x/player/wbi/v2",
    { aid: context.aid, cid: context.cid },
    true
  );
  const data = recordAt(payload, "data");
  const subtitle = data === null ? null : recordAt(data, "subtitle");
  if (subtitle === null) {
    return [];
  }
  return arrayAt(subtitle, "subtitles")
    .map((value) => optionalRecord(value))
    .filter((value): value is JsonRecord => value !== null)
    .map((track) => {
      const source = stringAt(track, "subtitle_url");
      if (source === null) {
        throw new Error("Subtitle track is missing subtitle_url.");
      }
      return {
        id: integerAt(track, "id") || 0,
        language: stringAt(track, "lan") || "unknown",
        language_doc: stringAt(track, "lan_doc") || stringAt(track, "lan") || "unknown",
        source_url: normalizePublicUrl(source),
        ai_type: integerAt(track, "ai_type") || 0,
        ai_status: integerAt(track, "ai_status") || 0
      };
    });
}

export async function subtitleCues(
  client: BilibiliClient,
  track: SubtitleTrack
): Promise<SubtitleCue[]> {
  const payload = await client.publicJson(track.source_url);
  return arrayAt(payload, "body")
    .map((value) => optionalRecord(value))
    .filter((value): value is JsonRecord => value !== null)
    .map((cue) => {
      const from = numberAt(cue, "from");
      const to = numberAt(cue, "to");
      const content = stringAt(cue, "content");
      if (from === null || to === null || content === null || to < from) {
        throw new Error("Subtitle cue has an invalid time range or content.");
      }
      return { from, to, content };
    });
}

export function renderSubtitles(cues: SubtitleCue[], format: string): string {
  if (format === "json") {
    return JSON.stringify({ body: cues }, null, 2) + "\n";
  }
  if (format === "txt") {
    return cues.map((cue) => cue.content).join("\n") + "\n";
  }
  if (format === "vtt") {
    const blocks = cues.map((cue, index) => {
      return (
        String(index + 1) +
        "\n" +
        subtitleTimestamp(cue.from, false) +
        " --> " +
        subtitleTimestamp(cue.to, false) +
        "\n" +
        cue.content
      );
    });
    return "WEBVTT\n\n" + blocks.join("\n\n") + "\n";
  }
  if (format === "srt") {
    return (
      cues
        .map((cue, index) => {
          return (
            String(index + 1) +
            "\n" +
            subtitleTimestamp(cue.from, true) +
            " --> " +
            subtitleTimestamp(cue.to, true) +
            "\n" +
            cue.content
          );
        })
        .join("\n\n") + "\n"
    );
  }
  if (format === "ass") {
    const lines = cues.map((cue) => {
      return (
        "Dialogue: 0," +
        assTimestamp(cue.from) +
        "," +
        assTimestamp(cue.to) +
        ",Default,,0,0,0,," +
        assEscape(cue.content)
      );
    });
    return subtitleAssHeader() + lines.join("\n") + "\n";
  }
  throw new Error("Subtitle format must be json, srt, vtt, txt, or ass.");
}

export function parseDanmakuXml(xml: string, limit: number): DanmakuEntry[] {
  const entries: DanmakuEntry[] = [];
  const expression = /<d\s+p="([^"]+)">([\s\S]*?)<\/d>/g;
  let match: RegExpExecArray | null;
  while ((match = expression.exec(xml)) !== null && entries.length < limit) {
    const fields = match[1].split(",");
    if (fields.length < 8) {
      continue;
    }
    const progress = Number(fields[0]);
    const mode = Number(fields[1]);
    const fontSize = Number(fields[2]);
    const color = Number(fields[3]);
    const ctime = Number(fields[4]);
    const pool = Number(fields[5]);
    if (![progress, mode, fontSize, color, ctime, pool].every(Number.isFinite)) {
      continue;
    }
    entries.push({
      progress,
      mode,
      font_size: fontSize,
      color,
      ctime,
      pool,
      sender_hash: fields[6],
      row_id: fields[7],
      text: decodeXml(match[2])
    });
  }
  return entries;
}

export function renderDanmaku(
  entries: DanmakuEntry[],
  format: string,
  width = 1920,
  height = 1080
): string {
  if (format === "json") {
    return JSON.stringify(entries, null, 2) + "\n";
  }
  if (format === "txt") {
    return (
      entries
        .map((entry) => "[" + subtitleTimestamp(entry.progress, false) + "] " + entry.text)
        .join("\n") + "\n"
    );
  }
  if (format !== "ass") {
    throw new Error("Danmaku format must be xml, json, txt, or ass.");
  }
  const laneHeight = 42;
  const laneCount = Math.max(1, Math.floor((height - 80) / laneHeight));
  const laneEnds = new Array<number>(laneCount).fill(0);
  const lines = entries.map((entry, index) => {
    const start = entry.progress;
    const duration = entry.mode === 4 || entry.mode === 5 ? 4 : 8;
    let lane = index % laneCount;
    for (let candidate = 0; candidate < laneCount; candidate += 1) {
      if (laneEnds[candidate] <= start) {
        lane = candidate;
        break;
      }
    }
    laneEnds[lane] = start + duration * 0.65;
    const y = 40 + lane * laneHeight;
    const color = assColor(entry.color);
    const position =
      entry.mode === 5
        ? "{\\an8\\pos(" + Math.floor(width / 2) + "," + y + ")}"
        : entry.mode === 4
          ? "{\\an2\\pos(" + Math.floor(width / 2) + "," + (height - y) + ")}"
          : "{\\move(" + (width + 40) + "," + y + ",-400," + y + ")}";
    return (
      "Dialogue: 0," +
      assTimestamp(start) +
      "," +
      assTimestamp(start + duration) +
      ",Danmaku,,0,0,0,," +
      "{\\c&H" +
      color +
      "&}" +
      position +
      assEscape(entry.text)
    );
  });
  return danmakuAssHeader(width, height) + lines.join("\n") + "\n";
}

export function normalizePublicUrl(value: string): string {
  if (value.startsWith("//")) {
    return "https:" + value;
  }
  if (!value.startsWith("https://")) {
    throw new Error("Bilibili public resource URL must use HTTPS.");
  }
  return value;
}

function subtitleTimestamp(seconds: number, srt: boolean): string {
  const milliseconds = Math.max(0, Math.round(seconds * 1_000));
  const hours = Math.floor(milliseconds / 3_600_000);
  const minutes = Math.floor((milliseconds % 3_600_000) / 60_000);
  const secs = Math.floor((milliseconds % 60_000) / 1_000);
  const millis = milliseconds % 1_000;
  const separator = srt ? "," : ".";
  return (
    String(hours).padStart(2, "0") +
    ":" +
    String(minutes).padStart(2, "0") +
    ":" +
    String(secs).padStart(2, "0") +
    separator +
    String(millis).padStart(3, "0")
  );
}

function assTimestamp(seconds: number): string {
  const centiseconds = Math.max(0, Math.round(seconds * 100));
  const hours = Math.floor(centiseconds / 360_000);
  const minutes = Math.floor((centiseconds % 360_000) / 6_000);
  const secs = Math.floor((centiseconds % 6_000) / 100);
  const fraction = centiseconds % 100;
  return (
    String(hours) +
    ":" +
    String(minutes).padStart(2, "0") +
    ":" +
    String(secs).padStart(2, "0") +
    "." +
    String(fraction).padStart(2, "0")
  );
}

function assEscape(value: string): string {
  return value.replace(/\\/g, "＼").replace(/[{}]/g, "").replace(/\r?\n/g, "\\N");
}

function assColor(rgb: number): string {
  const red = (rgb >> 16) & 0xff;
  const green = (rgb >> 8) & 0xff;
  const blue = rgb & 0xff;
  return [blue, green, red]
    .map((value) => value.toString(16).padStart(2, "0").toUpperCase())
    .join("");
}

function decodeXml(value: string): string {
  return value
    .replace(/&quot;/g, "\"")
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&")
    .replace(/&#([0-9]+);/g, (_, digits: string) => String.fromCodePoint(Number(digits)))
    .replace(/&#x([0-9a-f]+);/gi, (_, digits: string) =>
      String.fromCodePoint(Number.parseInt(digits, 16))
    );
}

function subtitleAssHeader(): string {
  return (
    "[Script Info]\nScriptType: v4.00+\nPlayResX: 1920\nPlayResY: 1080\n" +
    "[V4+ Styles]\n" +
    "Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour," +
    "Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow," +
    "Alignment,MarginL,MarginR,MarginV,Encoding\n" +
    "Style: Default,Noto Sans CJK SC,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000," +
    "0,0,0,0,100,100,0,0,1,2,1,2,60,60,45,1\n" +
    "[Events]\n" +
    "Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n"
  );
}

function danmakuAssHeader(width: number, height: number): string {
  return (
    "[Script Info]\nScriptType: v4.00+\nPlayResX: " +
    width +
    "\nPlayResY: " +
    height +
    "\n" +
    "[V4+ Styles]\n" +
    "Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour," +
    "Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow," +
    "Alignment,MarginL,MarginR,MarginV,Encoding\n" +
    "Style: Danmaku,Noto Sans CJK SC,36,&H00FFFFFF,&H000000FF,&H00000000,&H80000000," +
    "0,0,0,0,100,100,0,0,1,2,0,7,0,0,0,1\n" +
    "[Events]\n" +
    "Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n"
  );
}
