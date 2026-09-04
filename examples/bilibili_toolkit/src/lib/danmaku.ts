import { BilibiliClient } from "./client";
import { BilibiliError } from "./errors";
import type { DanmakuEntry, VideoContext } from "./types";

class ProtoReader {
  private position = 0;
  constructor(private readonly bytes: Uint8Array) {}
  get done(): boolean { return this.position === this.bytes.length; }
  varint(): bigint {
    let value = 0n;
    for (let shift = 0n; shift < 70n; shift += 7n) {
      if (this.position >= this.bytes.length) throw new Error("弹幕 protobuf varint 被截断。");
      const byte = this.bytes[this.position++];
      if (shift === 63n && byte > 1) throw new Error("弹幕 protobuf varint 溢出。");
      value |= BigInt(byte & 127) << shift;
      if (byte < 128) return value;
    }
    throw new Error("弹幕 protobuf varint 过长。");
  }
  number(): number {
    const value = Number(this.varint());
    if (!Number.isSafeInteger(value)) throw new Error("弹幕 protobuf 数值超过安全整数范围。");
    return value;
  }
  take(length: number): Uint8Array {
    if (length < 0 || length > this.bytes.length - this.position) throw new Error("弹幕 protobuf 字段越界。");
    const result = this.bytes.subarray(this.position, this.position + length);
    this.position += length;
    return result;
  }
  block(): Uint8Array { return this.take(this.number()); }
  text(): string {
    const bytes = this.block();
    if (bytes.length > 65_536) throw new Error("弹幕文本字段过大。");
    return decodeURIComponent(Array.from(bytes, (byte) => "%" + byte.toString(16).padStart(2, "0")).join(""));
  }
  skip(wire: number): void {
    if (wire === 0) this.varint();
    else if (wire === 1) this.take(8);
    else if (wire === 2) this.block();
    else if (wire === 5) this.take(4);
    else throw new Error("不支持的弹幕 protobuf wire type。");
  }
}

function decodeBase64(value: string): Uint8Array {
  const padding = value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0;
  if (value.length % 4 !== 0 || /[^A-Za-z0-9+/]/.test(value.slice(0, value.length - padding))) {
    throw new Error("弹幕响应不是有效的 base64。");
  }
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  const bytes = new Uint8Array(value.length / 4 * 3 - padding);
  let offset = 0;
  for (let index = 0; index < value.length; index += 4) {
    const word = (alphabet.indexOf(value[index]) << 18) | (alphabet.indexOf(value[index + 1]) << 12) |
      (Math.max(0, alphabet.indexOf(value[index + 2])) << 6) | Math.max(0, alphabet.indexOf(value[index + 3]));
    for (const shift of [16, 8, 0]) if (offset < bytes.length) bytes[offset++] = (word >>> shift) & 255;
  }
  return bytes;
}

function decodeEntry(bytes: Uint8Array): DanmakuEntry {
  const reader = new ProtoReader(bytes);
  const entry: DanmakuEntry = { progress: 0, mode: 0, font_size: 0, color: 0, ctime: 0, pool: 0, sender_hash: "", row_id: "", text: "" };
  let numericId = "0";
  while (!reader.done) {
    const tag = reader.number();
    const field = Math.floor(tag / 8);
    const wire = tag % 8;
    if (field === 0) throw new Error("弹幕 protobuf 字段编号不能为零。");
    if (field === 1 && wire === 0) numericId = reader.varint().toString();
    else if (field === 2 && wire === 0) entry.progress = reader.number() / 1000;
    else if (field === 3 && wire === 0) entry.mode = reader.number();
    else if (field === 4 && wire === 0) entry.font_size = reader.number();
    else if (field === 5 && wire === 0) entry.color = reader.number();
    else if (field === 6 && wire === 2) entry.sender_hash = reader.text();
    else if (field === 7 && wire === 2) entry.text = reader.text();
    else if (field === 8 && wire === 0) entry.ctime = reader.number();
    else if (field === 11 && wire === 0) entry.pool = reader.number();
    else if (field === 12 && wire === 2) entry.row_id = reader.text();
    else reader.skip(wire);
  }
  if (entry.row_id.length === 0) entry.row_id = numericId;
  return entry;
}

export function decodeDanmakuSegment(base64: string): DanmakuEntry[] {
  const reader = new ProtoReader(decodeBase64(base64));
  const entries: DanmakuEntry[] = [];
  while (!reader.done) {
    const tag = reader.number();
    if (tag === 10) {
      if (entries.length >= 100_000) throw new Error("单段弹幕条目超过安全上限。");
      entries.push(decodeEntry(reader.block()));
    } else if (tag === 16) {
      if (reader.number() !== 0) throw new BilibiliError("DANMAKU_CLOSED", "平台已关闭此视频弹幕。");
    } else {
      if (Math.floor(tag / 8) === 0) throw new Error("弹幕 protobuf 字段编号不能为零。");
      reader.skip(tag % 8);
    }
  }
  return entries;
}

export async function fetchDanmakuSegments(client: BilibiliClient, context: VideoContext, limit: number) {
  if (!Number.isFinite(context.partInfo.duration) || context.partInfo.duration <= 0) {
    throw new BilibiliError("SEGMENT_COUNT_UNAVAILABLE", "视频时长不可用，无法确定当前弹幕分段范围。");
  }
  const totalSegments = Math.max(1, Math.ceil(context.partInfo.duration / 360));
  const segmentLimit = Math.min(totalSegments, 50);
  const entries: DanmakuEntry[] = [];
  const seen = new Set<string>();
  let fetchedSegments = 0;
  for (let segment = 1; segment <= segmentLimit; segment += 1) {
    const body = await client.publicBinary("https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=" + context.cid + "&segment_index=" + segment);
    for (const entry of decodeDanmakuSegment(body)) {
      if (!seen.has(entry.row_id)) { seen.add(entry.row_id); entries.push(entry); }
    }
    fetchedSegments += 1;
    if (entries.length >= limit) break;
  }
  entries.sort((left, right) => left.progress - right.progress);
  return { entries, fetchedSegments, totalSegments, truncated: fetchedSegments < totalSegments || entries.length > limit };
}

export function danmakuXml(entries: DanmakuEntry[]): string {
  const escape = (text: string) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  return '<?xml version="1.0" encoding="UTF-8"?><i>' + entries.map((entry) =>
    '<d p="' + escape([entry.progress, entry.mode, entry.font_size, entry.color, entry.ctime, entry.pool, entry.sender_hash, entry.row_id].join(",")) + '">' + escape(entry.text) + '</d>'
  ).join("") + '</i>\n';
}
