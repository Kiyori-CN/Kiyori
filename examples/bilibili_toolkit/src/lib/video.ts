import { BilibiliClient } from "./client";
import {
  arrayAt,
  integerAt,
  numberAt,
  optionalRecord,
  recordAt,
  stringAt
} from "./json";
import { resolveTarget } from "./target";
import type {
  JsonRecord,
  JsonValue,
  ResolvedTarget,
  VideoContext,
  VideoPart
} from "./types";

export async function getVideoContext(
  client: BilibiliClient,
  targetValue: string,
  partOverride?: number
): Promise<VideoContext> {
  const target = await resolveTarget(client, targetValue);
  if (target.kind === "episode") {
    return episodeContext(client, target);
  }
  if (target.kind !== "video") {
    throw new Error("This operation requires a video BV/av target or an EP target.");
  }
  const identifier = target.identifier;
  if (identifier === null) {
    throw new Error("Resolved video target has no identifier.");
  }
  const params =
    identifier.startsWith("BV")
      ? { bvid: identifier }
      : { aid: Number(identifier.slice(2)) };
  const payload = await client.api("/x/web-interface/view", params);
  const info = requireRecordField(payload, "data", "Video metadata");
  const bvid = stringAt(info, "bvid");
  const aid = integerAt(info, "aid");
  if (bvid === null || aid === null) {
    throw new Error("Video metadata is missing bvid or aid.");
  }
  const requestedPart = partOverride === undefined ? target.part || 1 : partOverride;
  if (!Number.isInteger(requestedPart) || requestedPart < 1) {
    throw new Error("part must be a positive integer.");
  }
  const pages = arrayAt(info, "pages")
    .map((value) => optionalRecord(value))
    .filter((value): value is JsonRecord => value !== null);
  const selected = pages.find((page) => integerAt(page, "page") === requestedPart);
  if (selected === undefined) {
    throw new Error(
      "Requested part " + requestedPart + " is unavailable; video has " + pages.length + " parts."
    );
  }
  const partInfo = compactPart(selected);
  return {
    target,
    info,
    bvid,
    aid,
    cid: partInfo.cid,
    part: requestedPart,
    partInfo,
    episodeId: null,
    season: null
  };
}

export async function getSeasonData(
  client: BilibiliClient,
  targetValue: string
): Promise<{ target: ResolvedTarget; season: JsonRecord; selectedEpisode: JsonRecord | null }> {
  const target = await resolveTarget(client, targetValue);
  if (target.kind !== "episode" && target.kind !== "season" && target.kind !== "media") {
    throw new Error("Season metadata requires an EP, SS, or MD target.");
  }
  let params: { ep_id?: number; season_id?: number };
  if (target.kind === "episode") {
    params = { ep_id: numericIdentifier(target, "ep") };
  } else if (target.kind === "season") {
    params = { season_id: numericIdentifier(target, "ss") };
  } else {
    const mediaId = numericIdentifier(target, "md");
    const review = await client.api("/pgc/review/user", { media_id: mediaId });
    const result = requireRecordField(review, "result", "PGC media review");
    const media = recordAt(result, "media");
    const seasonId = media === null ? null : integerAt(media, "season_id");
    if (seasonId === null) {
      throw new Error("PGC media response is missing season_id.");
    }
    params = { season_id: seasonId };
  }
  const payload = await client.api("/pgc/view/web/season", params);
  const season =
    recordAt(payload, "result") ||
    recordAt(payload, "data");
  if (season === null) {
    throw new Error("PGC season response is missing result data.");
  }
  const selectedEpisode =
    target.kind === "episode"
      ? findEpisode(season, numericIdentifier(target, "ep"))
      : null;
  if (target.kind === "episode" && selectedEpisode === null) {
    throw new Error("The requested episode is absent from the season response.");
  }
  return { target, season, selectedEpisode };
}

export function compactVideoContext(context: VideoContext): JsonRecord {
  const owner = recordAt(context.info, "owner");
  const stat = recordAt(context.info, "stat");
  const pages = arrayAt(context.info, "pages")
    .map((value) => optionalRecord(value))
    .filter((value): value is JsonRecord => value !== null)
    .map(compactPart);
  return {
    bvid: context.bvid,
    aid: context.aid,
    cid: context.cid,
    part: context.part,
    title: stringAt(context.info, "title"),
    description: (stringAt(context.info, "desc") || "").slice(0, 2_000),
    duration: numberAt(context.info, "duration"),
    published_at: integerAt(context.info, "pubdate"),
    cover: stringAt(context.info, "pic"),
    owner:
      owner === null
        ? null
        : {
            mid: integerAt(owner, "mid"),
            name: stringAt(owner, "name")
          },
    stat,
    selected_part: context.partInfo,
    part_count: pages.length,
    pages,
    episode_id: context.episodeId,
    url: "https://www.bilibili.com/video/" + context.bvid + "?p=" + context.part
  };
}

export function compactSeason(season: JsonRecord, selectedEpisodeId: number | null): JsonRecord {
  const episodes = allEpisodes(season);
  return {
    season_id: integerAt(season, "season_id"),
    media_id: integerAt(season, "media_id"),
    title: stringAt(season, "title"),
    evaluate: (stringAt(season, "evaluate") || "").slice(0, 2_000),
    cover: stringAt(season, "cover"),
    status: integerAt(season, "status"),
    episode_count: episodes.length,
    selected_episode_id: selectedEpisodeId,
    episodes: episodes.slice(0, 200).map(compactEpisode)
  };
}

export function allEpisodes(season: JsonRecord): JsonRecord[] {
  const result: JsonRecord[] = [];
  arrayAt(season, "episodes").forEach((value) => {
    const episode = optionalRecord(value);
    if (episode !== null) {
      result.push(episode);
    }
  });
  arrayAt(season, "section").forEach((value) => {
    const section = optionalRecord(value);
    if (section !== null) {
      arrayAt(section, "episodes").forEach((episodeValue) => {
        const episode = optionalRecord(episodeValue);
        if (episode !== null) {
          result.push(episode);
        }
      });
    }
  });
  const seen = new Set<number>();
  return result.filter((episode) => {
    const id = integerAt(episode, "id");
    if (id === null || seen.has(id)) {
      return false;
    }
    seen.add(id);
    return true;
  });
}

export function compactEpisode(episode: JsonRecord): JsonRecord {
  const dimension = recordAt(episode, "dimension");
  return {
    ep_id: integerAt(episode, "id"),
    aid: integerAt(episode, "aid"),
    bvid: stringAt(episode, "bvid"),
    cid: integerAt(episode, "cid"),
    title: stringAt(episode, "title"),
    long_title: stringAt(episode, "long_title"),
    duration: numberAt(episode, "duration"),
    badge: stringAt(episode, "badge"),
    badge_info: recordAt(episode, "badge_info"),
    dimension,
    url:
      integerAt(episode, "id") === null
        ? stringAt(episode, "link")
        : "https://www.bilibili.com/bangumi/play/ep" + integerAt(episode, "id")
  };
}

function episodeContext(
  client: BilibiliClient,
  target: ResolvedTarget
): Promise<VideoContext> {
  return getSeasonData(client, target.url).then(({ season, selectedEpisode }) => {
    if (selectedEpisode === null) {
      throw new Error("Episode metadata is unavailable.");
    }
    const bvid = stringAt(selectedEpisode, "bvid");
    const aid = integerAt(selectedEpisode, "aid");
    const cid = integerAt(selectedEpisode, "cid");
    const episodeId = integerAt(selectedEpisode, "id");
    if (bvid === null || aid === null || cid === null || episodeId === null) {
      throw new Error("Episode metadata is missing bvid, aid, cid, or episode id.");
    }
    const title =
      [stringAt(season, "title"), stringAt(selectedEpisode, "title"), stringAt(selectedEpisode, "long_title")]
        .filter((value): value is string => value !== null && value.length > 0)
        .join(" ");
    const info: JsonRecord = {
      ...selectedEpisode,
      bvid,
      aid,
      title,
      desc: stringAt(season, "evaluate"),
      pic: stringAt(selectedEpisode, "cover") || stringAt(season, "cover"),
      pages: [
        {
          page: 1,
          cid,
          part: title,
          duration: normalizedEpisodeDuration(selectedEpisode)
        }
      ]
    };
    const partInfo = compactPart(requireRecordField(info, "pages", "Episode pages", 0));
    return {
      target,
      info,
      bvid,
      aid,
      cid,
      part: 1,
      partInfo,
      episodeId,
      season
    };
  });
}

function findEpisode(season: JsonRecord, episodeId: number): JsonRecord | null {
  return allEpisodes(season).find((episode) => integerAt(episode, "id") === episodeId) || null;
}

function compactPart(value: JsonRecord): VideoPart {
  const page = integerAt(value, "page");
  const cid = integerAt(value, "cid");
  if (page === null || cid === null) {
    throw new Error("Video part metadata is missing page or cid.");
  }
  return {
    page,
    cid,
    part: stringAt(value, "part") || "P" + page,
    duration: numberAt(value, "duration") || 0
  };
}

function requireRecordField(
  value: JsonRecord,
  key: string,
  label: string,
  arrayIndex?: number
): JsonRecord {
  if (arrayIndex !== undefined) {
    const item = arrayAt(value, key)[arrayIndex];
    const record = item === undefined ? null : optionalRecord(item);
    if (record === null) {
      throw new Error(label + " is missing.");
    }
    return record;
  }
  const record = recordAt(value, key);
  if (record === null) {
    throw new Error(label + " is missing.");
  }
  return record;
}

function numericIdentifier(target: ResolvedTarget, prefix: string): number {
  const identifier = target.identifier;
  if (identifier === null || !identifier.startsWith(prefix)) {
    throw new Error("Resolved target identifier is invalid.");
  }
  const value = Number(identifier.slice(prefix.length));
  if (!Number.isInteger(value) || value < 1) {
    throw new Error("Resolved target identifier is invalid.");
  }
  return value;
}

function normalizedEpisodeDuration(episode: JsonRecord): number {
  const duration = numberAt(episode, "duration") || 0;
  return duration > 100_000 ? duration / 1_000 : duration;
}
