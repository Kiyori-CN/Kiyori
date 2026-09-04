import { encodeQueryPart } from "./crypto";
import { requireString } from "./json";
import type { BilibiliTargetKind, ResolvedTarget } from "./types";
import { BilibiliClient } from "./client";

const TRACKING_KEYS = new Set([
  "access_key", "access_token", "bbid", "bili_jct", "buvid", "csrf", "from",
  "from_spmid", "sessdata", "share_from", "share_medium", "sign", "token",
  "share_plat", "share_session_id", "share_source", "share_tag", "spm_id_from",
  "timestamp", "unique_k", "vd_source", "w_rid", "wts"
]);

interface ParsedUrl {
  host: string;
  path: string;
  query: Map<string, string>;
}

function parseHttpsUrl(value: string): ParsedUrl {
  const match = value.match(/^https:\/\/([^/?#]+)([^?#]*)(?:\?([^#]*))?(?:#.*)?$/i);
  if (match === null) {
    throw new Error("Bilibili target URL must use HTTPS.");
  }
  const authority = match[1].toLowerCase().replace(/\.$/, "");
  if (authority.includes("@") || (authority.includes(":") && !authority.endsWith(":443"))) {
    throw new Error("Bilibili target URL must not contain credentials or a non-default port.");
  }
  const host = authority.endsWith(":443") ? authority.slice(0, -4) : authority;
  const query = new Map<string, string>();
  const queryText = match[3] || "";
  if (queryText.length > 0) {
    queryText.split("&").forEach((pair) => {
      const separator = pair.indexOf("=");
      const rawKey = separator < 0 ? pair : pair.slice(0, separator);
      const rawValue = separator < 0 ? "" : pair.slice(separator + 1);
      const key = decodeURIComponent(rawKey.replace(/\+/g, " ")).toLowerCase();
      const decoded = decodeURIComponent(rawValue.replace(/\+/g, " "));
      if (!TRACKING_KEYS.has(key) && !query.has(key)) {
        query.set(key, decoded);
      }
    });
  }
  return { host, path: match[2] || "/", query };
}

function canonicalQuery(query: Map<string, string>, allowed: Set<string>): string {
  const pairs: string[] = [];
  Array.from(query.keys()).sort().forEach((key) => {
    if (allowed.has(key)) {
      const value = query.get(key);
      if (value !== undefined) {
        pairs.push(encodeQueryPart(key) + "=" + encodeQueryPart(value));
      }
    }
  });
  return pairs.length === 0 ? "" : "?" + pairs.join("&");
}

function result(
  url: string,
  kind: BilibiliTargetKind,
  identifier: string | null,
  part: number | null,
  playlistLike: boolean,
  shortLinkResolved: boolean
): ResolvedTarget {
  return {
    url,
    kind,
    identifier,
    part,
    playlist_like: playlistLike,
    short_link_resolved: shortLinkResolved
  };
}

export async function resolveTarget(
  client: BilibiliClient,
  rawValue: string
): Promise<ResolvedTarget> {
  let candidate = extractCandidate(requireString(rawValue, "target"));
  let shortLinkResolved = false;
  if (/^https:\/\/b23\.tv\//i.test(candidate)) {
    candidate = await client.resolveShortLink(candidate);
    shortLinkResolved = true;
  }
  return resolveTargetWithoutNetwork(candidate, shortLinkResolved);
}

export function resolveTargetWithoutNetwork(
  rawValue: string,
  shortLinkResolved = false
): ResolvedTarget {
  const candidate = extractCandidate(rawValue);
  if (/^https:\/\//i.test(candidate)) {
    const parsed = parseHttpsUrl(candidate);
    if (parsed.host !== "bilibili.com" && !parsed.host.endsWith(".bilibili.com")) {
      throw new Error("Target URL must use bilibili.com or one of its subdomains.");
    }
    const partText = parsed.query.get("p");
    const part = partText === undefined ? null : positiveInteger(partText, "p");
    const video = parsed.path.match(/(?:^\/|\/)(BV[0-9A-Za-z]{10}|av[0-9]+)(?:\/|$)/i);
    if (video !== null) {
      const id = canonicalVideoId(video[1]);
      const suffix = part === null ? "" : "?p=" + part;
      return result(
        "https://www.bilibili.com/video/" + id + suffix,
        "video",
        id,
        part,
        false,
        shortLinkResolved
      );
    }
    const pgc = parsed.path.match(/\/(?:bangumi|cheese)\/play\/(ep|ss)([0-9]+)/i);
    if (pgc !== null) {
      const prefix = pgc[1].toLowerCase();
      const id = prefix + String(Number(pgc[2]));
      return result(
        "https://www.bilibili.com/bangumi/play/" + id,
        prefix === "ep" ? "episode" : "season",
        id,
        null,
        prefix === "ss",
        shortLinkResolved
      );
    }
    const media = parsed.path.match(/\/bangumi\/media\/md([0-9]+)/i);
    if (media !== null) {
      const id = "md" + String(Number(media[1]));
      return result(
        "https://www.bilibili.com/bangumi/media/" + id,
        "media",
        id,
        null,
        true,
        shortLinkResolved
      );
    }
    if (parsed.host === "space.bilibili.com") {
      const uid = parsed.path.match(/^\/([0-9]+)/);
      const identifier = uid === null ? null : "UID:" + uid[1];
      const favorite = parsed.path.toLowerCase().includes("/favlist") || parsed.query.has("fid");
      const kept = canonicalQuery(parsed.query, new Set(["fid"]));
      return result(
        "https://space.bilibili.com" + parsed.path + kept,
        favorite ? "favorites" : "space",
        identifier,
        null,
        true,
        shortLinkResolved
      );
    }
    const lowered = parsed.path.toLowerCase().replace(/\/+$/, "");
    let kind: BilibiliTargetKind = "bilibili_url";
    let playlistLike = false;
    if (lowered.includes("watchlater")) {
      kind = "watch_later";
      playlistLike = true;
    } else if (lowered.includes("/medialist/") || lowered.includes("/list/")) {
      kind = "playlist";
      playlistLike = true;
    } else if (lowered.includes("collection")) {
      kind = "collection";
      playlistLike = true;
    } else if (lowered.includes("series")) {
      kind = "series";
      playlistLike = true;
    } else if (lowered.includes("/v/channel/")) {
      kind = "category";
      playlistLike = true;
    } else if (lowered.includes("/search")) {
      kind = "search";
      playlistLike = true;
    } else if (lowered.includes("/opus/") || lowered.includes("/dynamic/")) {
      kind = "dynamic";
    }
    return result(
      "https://" + parsed.host + parsed.path,
      kind,
      null,
      part,
      playlistLike,
      shortLinkResolved
    );
  }

  const looseVideo = candidate.match(/\b(BV[0-9A-Za-z]{10}|av[0-9]+)\b/i);
  if (looseVideo !== null) {
    const id = canonicalVideoId(looseVideo[1]);
    const pageMatch = candidate.match(/(?:[?&]p=|\bp)([0-9]+)\b/i);
    const part = pageMatch === null ? null : positiveInteger(pageMatch[1], "p");
    return result(
      "https://www.bilibili.com/video/" + id + (part === null ? "" : "?p=" + part),
      "video",
      id,
      part,
      false,
      false
    );
  }
  const pgc = candidate.match(/^(ep|ss|md)([0-9]+)$/i);
  if (pgc !== null) {
    const prefix = pgc[1].toLowerCase();
    const id = prefix + String(Number(pgc[2]));
    const kind: BilibiliTargetKind =
      prefix === "ep" ? "episode" : prefix === "ss" ? "season" : "media";
    const url =
      prefix === "md"
        ? "https://www.bilibili.com/bangumi/media/" + id
        : "https://www.bilibili.com/bangumi/play/" + id;
    return result(url, kind, id, null, prefix !== "ep", false);
  }
  const numeric = candidate.match(/^([0-9]+)(?:\?p=([0-9]+))?$/);
  if (numeric !== null) {
    const id = "av" + numeric[1];
    const part = numeric[2] === undefined ? null : positiveInteger(numeric[2], "p");
    return result(
      "https://www.bilibili.com/video/" + id + (part === null ? "" : "?p=" + part),
      "video",
      id,
      part,
      false,
      false
    );
  }
  throw new Error("Unsupported Bilibili target.");
}

function extractCandidate(value: string): string {
  const url = value.match(/https:\/\/[^\s<>"']+/i);
  return (url === null ? value : url[0]).trim().replace(/[.,;，。；)]+$/g, "");
}

function canonicalVideoId(value: string): string {
  return value.slice(0, 2).toLowerCase() === "bv"
    ? "BV" + value.slice(2)
    : "av" + String(Number(value.slice(2)));
}

function positiveInteger(value: string, name: string): number {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(name + " must be a positive integer.");
  }
  return parsed;
}
