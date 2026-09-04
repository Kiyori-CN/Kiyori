import { BilibiliClient } from "./client";
import { BilibiliError } from "./errors";
import {
  arrayAt,
  booleanAt,
  boundedInteger,
  integerAt,
  numberAt,
  optionalRecord,
  recordAt,
  requireRecord,
  requireString,
  stringAt
} from "./json";
import type { JsonRecord, JsonValue, QueryParams, VideoContext } from "./types";
import { compactVideoContext, getVideoContext } from "./video";

export type AccountListKind =
  | "history"
  | "watch_later"
  | "favorites"
  | "favorite_items"
  | "liked"
  | "coins"
  | "followed_bangumi"
  | "followed_cinema"
  | "submissions";

export interface AccountListOptions {
  kind: AccountListKind;
  mid?: number;
  folderId?: number;
  page: number;
  pages: number;
  pageSize: number;
  limit: number;
  keyword?: string;
  historyType: "all" | "archive" | "pgc" | "live" | "article";
}

export interface CommentOptions {
  sort: "time" | "hot";
  pages: number;
  limit: number;
  includeReplies: boolean;
  replyPages: number;
  replyLimit: number;
}

export async function searchVideos(
  client: BilibiliClient,
  keywordValue: string,
  startPage: number,
  pages: number,
  limit: number,
  order: "totalrank" | "click" | "pubdate" | "dm" | "stow",
  duration: 0 | 1 | 2 | 3 | 4
): Promise<JsonRecord> {
  const keyword = requireString(keywordValue, "keyword");
  const firstPage = boundedInteger(startPage, "page", 1, 1_000, 1);
  const pageCount = boundedInteger(pages, "pages", 1, 5, 1);
  const resultLimit = boundedInteger(limit, "limit", 1, 100, 20);
  const results: JsonRecord[] = [];
  let total: number | null = null;
  let totalPages: number | null = null;
  for (let offset = 0; offset < pageCount && results.length < resultLimit; offset += 1) {
    const page = firstPage + offset;
    const payload = await client.api(
      "/x/web-interface/wbi/search/type",
      {
        search_type: "video",
        keyword,
        page,
        page_size: Math.min(50, resultLimit - results.length),
        order,
        duration
      },
      true
    );
    const data = recordAt(payload, "data");
    if (data === null) {
      throw new Error("Bilibili search response is missing data.");
    }
    total = integerAt(data, "numResults");
    totalPages = integerAt(data, "numPages");
    const pageItems = records(arrayAt(data, "result"));
    for (const item of pageItems) {
      if (results.length >= resultLimit) {
        break;
      }
      results.push(compactSearchResult(item));
    }
    if (pageItems.length === 0 || (totalPages !== null && page >= totalPages)) {
      break;
    }
  }
  return {
    success: true,
    query: keyword,
    start_page: firstPage,
    requested_pages: pageCount,
    count: results.length,
    total,
    total_pages: totalPages,
    results
  };
}

export async function fetchComments(
  client: BilibiliClient,
  context: VideoContext,
  options: CommentOptions
): Promise<JsonRecord> {
  const pageCount = boundedInteger(options.pages, "pages", 1, 5, 1);
  const limit = boundedInteger(options.limit, "limit", 1, 100, 20);
  const replyPages = boundedInteger(options.replyPages, "reply_pages", 0, 2, 0);
  const replyLimit = boundedInteger(options.replyLimit, "reply_limit", 0, 100, 50);
  const roots: JsonRecord[] = [];
  const seenRoots = new Set<number>();
  let nextOffset = "";
  const seenOffsets = new Set<string>();
  let exhausted = false;
  let fetchedPages = 0;
  let reportedTotal: number | null = null;
  for (let page = 1; page <= pageCount && roots.length < limit; page += 1) {
    const payload = await client.api(
      "/x/v2/reply/wbi/main",
      {
        oid: context.aid,
        type: 1,
        mode: options.sort === "hot" ? 3 : 2,
        pagination_str: JSON.stringify({ offset: nextOffset }),
        plat: 1,
        seek_rpid: "",
        web_location: 1315875
      },
      true
    );
    const data = recordAt(payload, "data");
    if (data === null) {
      throw new Error("Bilibili comments response is missing data.");
    }
    const cursor = recordAt(data, "cursor");
    fetchedPages += 1;
    if (reportedTotal === null && cursor !== null) {
      reportedTotal = integerAt(cursor, "all_count");
    }
    const pageReplies = records(arrayAt(data, "replies"));
    for (const reply of pageReplies) {
      if (roots.length >= limit) {
        break;
      }
      const id = integerAt(reply, "rpid");
      if (id !== null && !seenRoots.has(id)) {
        seenRoots.add(id);
        roots.push(compactComment(reply));
      }
    }
    if (pageReplies.length === 0 || booleanAt(cursor === null ? {} : cursor, "is_end") === true) {
      exhausted = true;
      break;
    }
    // offset 是服务端不透明游标，不是按页大小计算的数字。
    const pagination = cursor === null ? null : recordAt(cursor, "pagination_reply");
    const offset = pagination === null ? null : stringAt(pagination, "next_offset");
    if (page < pageCount && roots.length < limit) {
      if (offset === null || offset.length === 0 || seenOffsets.has(offset)) {
        throw new BilibiliError("INVALID_CURSOR", "评论接口未提供可推进的分页游标。", { fetched_pages: fetchedPages });
      }
      seenOffsets.add(offset);
      nextOffset = offset;
    }
  }

  let childCount = roots.reduce((sum, root) => sum + arrayAt(root, "replies").length, 0);
  if (options.includeReplies && replyPages > 0 && childCount < replyLimit) {
    // 扩展回复会按根评论产生额外请求；只扩展前 10 条可避免一次 Agent 调用制造请求风暴。
    for (const root of roots.slice(0, 10)) {
      const rootId = integerAt(root, "rpid");
      if (rootId === null || childCount >= replyLimit) {
        continue;
      }
      const existing = records(arrayAt(root, "replies"));
      for (let page = 1; page <= replyPages && childCount < replyLimit; page += 1) {
        const payload = await client.api("/x/v2/reply/reply", {
          oid: context.aid,
          type: 1,
          root: rootId,
          pn: page,
          ps: Math.min(20, replyLimit - childCount)
        });
        const data = recordAt(payload, "data");
        if (data === null) {
          throw new Error("Bilibili child replies response is missing data.");
        }
        const replies = records(arrayAt(data, "replies"));
        for (const reply of replies) {
          if (childCount >= replyLimit) {
            break;
          }
          const compact = compactComment(reply);
          if (!existing.some((item) => integerAt(item, "rpid") === integerAt(compact, "rpid"))) {
            existing.push(compact);
            childCount += 1;
          }
        }
        if (replies.length === 0) {
          break;
        }
      }
      root.replies = existing;
      root.reply_count_returned = existing.length;
    }
  }

  if (!options.includeReplies) {
    roots.forEach((root) => {
      root.replies = [];
      root.reply_count_returned = 0;
    });
    childCount = 0;
  }
  return {
    success: true,
    video: compactVideoContext(context),
    sort: options.sort,
    root_count: roots.length,
    reply_count: childCount,
    reported_total: reportedTotal,
    fetched_pages: fetchedPages,
    exhausted,
    complete: false,
    roots
  };
}

export async function publicUser(
  client: BilibiliClient,
  userValue: string,
  full: boolean
): Promise<JsonRecord> {
  const match = userValue.match(/[0-9]+/);
  if (match === null) {
    throw new Error("user must contain a positive numeric UID.");
  }
  const mid = Number(match[0]);
  if (!Number.isInteger(mid) || mid < 1) {
    throw new Error("user must contain a positive numeric UID.");
  }
  const cardPayload = await client.api("/x/web-interface/card", { mid });
  const cardData = recordAt(cardPayload, "data");
  if (cardData === null) {
    throw new Error("Bilibili user card response is missing data.");
  }
  const card = recordAt(cardData, "card");
  const relationPayload = await client.api("/x/relation/stat", { vmid: mid });
  const relation = recordAt(relationPayload, "data");
  let profile: JsonRecord | null = null;
  if (full) {
    const profilePayload = await client.api("/x/space/wbi/acc/info", { mid }, true);
    profile = recordAt(profilePayload, "data");
    if (profile === null) {
      throw new Error("Bilibili full user profile response is missing data.");
    }
  }
  const preferred = profile === null ? card : profile;
  const cardLevel = card === null ? null : recordAt(card, "level_info");
  const vip = firstRecord(profile === null ? undefined : profile.vip, card === null ? undefined : card.vip);
  const label = vip === null ? null : recordAt(vip, "label");
  return {
    success: true,
    full_requested: full,
    profile_source: full ? "/x/space/wbi/acc/info" : "/x/web-interface/card",
    profile: full ? profile : null,
    user: {
      mid: firstInteger(profile, card, "mid") === null ? mid : firstInteger(profile, card, "mid"),
      name: firstString(profile, card, "name"),
      sex: firstString(profile, card, "sex"),
      face: firstString(profile, card, "face"),
      sign: firstString(profile, card, "sign"),
      birthday: firstString(profile, card, "birthday"),
      level:
        (preferred === null ? null : integerAt(preferred, "level")) === null
          ? cardLevel === null ? null : integerAt(cardLevel, "current_level")
          : preferred === null ? null : integerAt(preferred, "level"),
      official: firstRecord(profile === null ? undefined : profile.official, card === null ? undefined : card.Official),
      vip:
        vip === null
          ? null
          : {
              type: integerAt(vip, "type"),
              status: integerAt(vip, "status"),
              due_date: numberAt(vip, "due_date"),
              label: label === null ? null : stringAt(label, "text")
            },
      following: relation === null ? integerAt(cardData, "following") : integerAt(relation, "following"),
      followers: relation === null ? integerAt(cardData, "follower") : integerAt(relation, "follower"),
      archive_count: integerAt(cardData, "archive_count"),
      article_count: integerAt(cardData, "article_count")
    }
  };
}

export async function currentIdentity(client: BilibiliClient): Promise<JsonRecord> {
  const payload = await client.nav();
  const data = recordAt(payload, "data");
  if (data === null) {
    throw new Error("Bilibili nav response is missing account data.");
  }
  const loggedIn = booleanAt(data, "isLogin") === true;
  if (!loggedIn) {
    throw new BilibiliError("NOT_LOGGED_IN", "当前账号未登录；请检查插件环境变量 BILIBILI_COOKIE。", { action: "configure_cookie" });
  }
  const mid = integerAt(data, "mid");
  if (mid === null || mid < 1) {
    throw new Error("Logged-in Bilibili account has no valid UID.");
  }
  const level = recordAt(data, "level_info");
  return {
    mid,
    name: stringAt(data, "uname"),
    face: stringAt(data, "face"),
    level: level === null ? null : integerAt(level, "current_level"),
    cookie_configured: client.cookieConfigured,
    logged_in: true
  };
}

export async function accountList(
  client: BilibiliClient,
  options: AccountListOptions
): Promise<JsonRecord> {
  const identity = await currentIdentity(client);
  const loggedInMid = integerAt(identity, "mid");
  if (loggedInMid === null) {
    throw new Error("Logged-in account UID is unavailable.");
  }
  const requestedMid = options.mid === undefined ? loggedInMid : positiveInteger(options.mid, "mid");
  if ((options.kind === "history" || options.kind === "watch_later") && requestedMid !== loggedInMid) {
    throw new Error(options.kind + " can only be read for the logged-in account.");
  }
  const page = boundedInteger(options.page, "page", 1, 1_000, 1);
  const pages = boundedInteger(options.pages, "pages", 1, 5, 1);
  const pageSize = boundedInteger(options.pageSize, "page_size", 1, 50, 20);
  const limit = boundedInteger(options.limit, "limit", 1, 200, 50);
  let items: JsonRecord[] = [];
  let total: number | null = null;
  let nextCursor: JsonRecord | null = null;

  if (options.kind === "history") {
    const state = { max: 0, view_at: 0 };
    const seen = new Set<string>();
    for (let iteration = 0; iteration < pages && items.length < limit; iteration += 1) {
      const params: QueryParams = { max: state.max, view_at: state.view_at, ps: pageSize };
      if (options.historyType !== "all") {
        params.type = options.historyType;
      }
      const payload = await client.api("/x/web-interface/history/cursor", params);
      const data = requireData(payload, "Account history");
      const pageItems = records(arrayAt(data, "list"));
      items.push(...pageItems.map(compactAccountVideo));
      const cursor = recordAt(data, "cursor");
      const nextMax = cursor === null ? 0 : integerAt(cursor, "max") || 0;
      const nextViewAt = cursor === null ? 0 : integerAt(cursor, "view_at") || 0;
      nextCursor = { max: nextMax, view_at: nextViewAt, business: cursor === null ? null : stringAt(cursor, "business") };
      const key = nextMax + ":" + nextViewAt;
      if (pageItems.length === 0 || seen.has(key)) {
        break;
      }
      seen.add(key);
      state.max = nextMax;
      state.view_at = nextViewAt;
    }
  } else if (options.kind === "watch_later") {
    const data = requireData(await client.api("/x/v2/history/toview/web"), "Watch later");
    items = records(arrayAt(data, "list")).map(compactAccountVideo);
    total = integerAt(data, "count");
  } else if (options.kind === "favorites") {
    const data = requireData(
      await client.api("/x/v3/fav/folder/created/list-all", { up_mid: requestedMid }),
      "Favorite folders"
    );
    items = records(arrayAt(data, "list")).map((item) => ({
      id: integerAt(item, "id"),
      fid: integerAt(item, "fid"),
      mid: integerAt(item, "mid"),
      title: stringAt(item, "title"),
      media_count: integerAt(item, "media_count"),
      favorite_state: integerAt(item, "fav_state"),
      url: "https://space.bilibili.com/" + requestedMid + "/favlist?fid=" + integerAt(item, "id")
    }));
    total = integerAt(data, "count");
  } else if (options.kind === "favorite_items") {
    const folderId = options.folderId === undefined ? null : positiveInteger(options.folderId, "folder_id");
    if (folderId === null) {
      throw new Error("favorite_items requires folder_id.");
    }
    let hasMore = false;
    let nextPage = page;
    for (let current = page; current < page + pages && items.length < limit; current += 1) {
      const data = requireData(
        await client.api("/x/v3/fav/resource/list", {
          media_id: folderId,
          pn: current,
          ps: pageSize,
          platform: "web",
          order: "mtime",
          type: 0,
          tid: 0
        }),
        "Favorite items"
      );
      const pageItems = records(arrayAt(data, "medias"));
      nextPage = current + 1;
      items.push(...pageItems.map(compactAccountVideo));
      hasMore = booleanAt(data, "has_more") === true;
      const info = recordAt(data, "info");
      total = info === null ? integerAt(data, "ttl") : integerAt(info, "media_count");
      if (!hasMore || pageItems.length === 0) {
        break;
      }
    }
    nextCursor = hasMore ? { page: nextPage } : null;
  } else if (options.kind === "liked" || options.kind === "coins") {
    const endpoint = options.kind === "liked" ? "/x/space/like/video" : "/x/space/coin/video";
    const payload = await client.api(endpoint, { vmid: requestedMid });
    let list: JsonValue[];
    if (options.kind === "coins") {
      if (!Array.isArray(payload.data)) throw new BilibiliError("INVALID_RESPONSE", "投币列表接口的 data 应为数组。");
      list = payload.data;
    } else {
      list = arrayAt(requireData(payload, options.kind), "list");
    }
    items = records(list).map(compactAccountVideo);
    total = items.length;
  } else if (options.kind === "followed_bangumi" || options.kind === "followed_cinema") {
    const followType = options.kind === "followed_bangumi" ? 1 : 2;
    for (let current = page; current < page + pages && items.length < limit; current += 1) {
      const data = requireData(
        await client.api(
          "/x/space/bangumi/follow/list",
          { vmid: requestedMid, type: followType, pn: current, ps: pageSize, platform: "web" },
          true
        ),
        "Followed PGC"
      );
      const pageItems = records(arrayAt(data, "list"));
      items.push(...pageItems.map(compactFollowedSeason));
      total = integerAt(data, "total");
      if (pageItems.length === 0 || (total !== null && current * pageSize >= total)) {
        break;
      }
    }
  } else {
    for (let current = page; current < page + pages && items.length < limit; current += 1) {
      const params: QueryParams = {
        mid: requestedMid,
        pn: current,
        ps: pageSize,
        order: "pubdate",
        platform: "web"
      };
      const keyword = options.keyword === undefined ? "" : options.keyword.trim();
      if (keyword.length > 0) {
        params.keyword = keyword;
      }
      const data = requireData(
        await client.api("/x/space/wbi/arc/search", params, true),
        "Account submissions"
      );
      const list = recordAt(data, "list");
      const pageItems = list === null ? [] : records(arrayAt(list, "vlist"));
      items.push(...pageItems.map(compactAccountVideo));
      const pageData = recordAt(data, "page");
      total = pageData === null ? null : integerAt(pageData, "count");
      if (pageItems.length === 0 || (total !== null && current * pageSize >= total)) {
        break;
      }
    }
  }

  items = items.slice(0, limit);
  return {
    success: true,
    kind: options.kind,
    account: identity,
    mid: requestedMid,
    count: items.length,
    total,
    next_cursor: nextCursor,
    items
  };
}

export async function interactiveGraph(
  client: BilibiliClient,
  context: VideoContext,
  startEdge: number,
  maxNodes: number
): Promise<JsonRecord> {
  const edge = boundedInteger(startEdge, "start_edge", 0, 2_147_483_647, 0);
  const nodeLimit = boundedInteger(maxNodes, "max_nodes", 1, 100, 50);
  const player = requireData(
    await client.api("/x/player/wbi/v2", { bvid: context.bvid, cid: context.cid }, true),
    "Interactive player metadata"
  );
  const interaction = recordAt(player, "interaction");
  const rawGraphVersion = interaction === null ? undefined : interaction.graph_version;
  const graphVersion =
    typeof rawGraphVersion === "string" || typeof rawGraphVersion === "number"
      ? rawGraphVersion
      : null;
  if (graphVersion === null || graphVersion === "" || graphVersion === 0 || graphVersion === "0") {
    return { success: true, available: false, reason: "NOT_INTERACTIVE", message: "该视频分P不是互动视频。", node_count: 0, nodes: [], links: [] };
  }
  const pending: number[] = [edge];
  const queued = new Set<number>([edge]);
  const nodes: JsonRecord[] = [];
  const links: JsonRecord[] = [];
  while (pending.length > 0 && nodes.length < nodeLimit) {
    const current = pending.shift();
    if (current === undefined) {
      break;
    }
    const data = requireData(
      await client.api("/x/stein/edgeinfo_v2", {
        bvid: context.bvid,
        graph_version: graphVersion,
        edge_id: current
      }),
      "Interactive edge"
    );
    const compact = compactInteractiveNode(data, current);
    nodes.push(compact.node);
    for (const child of compact.children) {
      links.push({ from: integerAt(compact.node, "edge_id"), to: child });
      if (!queued.has(child)) {
        queued.add(child);
        pending.push(child);
      }
    }
  }
  return {
    success: true,
    video: compactVideoContext(context),
    graph_version: graphVersion,
    start_edge: edge,
    node_count: nodes.length,
    discovered_node_count: queued.size,
    truncated: pending.length > 0,
    nodes,
    links
  };
}

export async function aiSummary(
  client: BilibiliClient,
  context: VideoContext,
  includeTranscript: boolean
): Promise<JsonRecord> {
  const owner = recordAt(context.info, "owner");
  const upMid = owner === null ? null : integerAt(owner, "mid");
  if (upMid === null) {
    throw new Error("Video metadata is missing uploader UID required by Bilibili AI summary.");
  }
  const data = requireData(
    await client.api(
      "/x/web-interface/view/conclusion/get",
      { bvid: context.bvid, cid: context.cid, up_mid: upMid },
      true
    ),
    "Bilibili AI summary"
  );
  const model = recordAt(data, "model_result");
  const transcript = model === null ? [] : arrayAt(model, "subtitle");
  let cueCount = 0;
  records(transcript).forEach((part) => {
    cueCount += arrayAt(part, "part_subtitle").length;
  });
  const summary: JsonRecord = { ...data, transcript_available: transcript.length > 0, transcript_cue_count: cueCount };
  if (!includeTranscript && model !== null) {
    const compactModel: JsonRecord = { ...model };
    delete compactModel.subtitle;
    summary.model_result = compactModel;
  }
  return { success: true, video: compactVideoContext(context), summary };
}

export async function resolveVideoForData(
  client: BilibiliClient,
  target: string,
  part?: number
): Promise<VideoContext> {
  return getVideoContext(client, target, part);
}

function compactSearchResult(value: JsonRecord): JsonRecord {
  const bvid = stringAt(value, "bvid");
  return {
    bvid,
    aid: integerAt(value, "aid"),
    title: stripHtml(stringAt(value, "title")),
    description: stripHtml(stringAt(value, "description")),
    author: stringAt(value, "author"),
    mid: integerAt(value, "mid"),
    duration: stringAt(value, "duration"),
    published_at: integerAt(value, "pubdate"),
    play: numberAt(value, "play"),
    danmaku: numberAt(value, "danmaku"),
    favorites: numberAt(value, "favorites"),
    cover: stringAt(value, "pic"),
    url: bvid === null ? null : "https://www.bilibili.com/video/" + bvid
  };
}

function compactComment(value: JsonRecord): JsonRecord {
  const member = recordAt(value, "member");
  const content = recordAt(value, "content");
  const embedded = records(arrayAt(value, "replies")).map(compactComment);
  return {
    rpid: integerAt(value, "rpid"),
    parent: integerAt(value, "parent"),
    root: integerAt(value, "root"),
    created_at: integerAt(value, "ctime"),
    likes: integerAt(value, "like"),
    reply_count_reported: integerAt(value, "rcount"),
    reply_count_returned: embedded.length,
    user:
      member === null
        ? null
        : {
            mid: integerAt(member, "mid"),
            name: stringAt(member, "uname"),
            avatar: stringAt(member, "avatar")
          },
    message: content === null ? null : stringAt(content, "message"),
    replies: embedded
  };
}

function compactAccountVideo(value: JsonRecord): JsonRecord {
  const history = recordAt(value, "history");
  const bvid = firstString(value, history, "bvid") || stringAt(value, "bv_id");
  const episodeId = history === null ? integerAt(value, "ep_id") : integerAt(history, "epid");
  const aid = firstInteger(value, history, "aid") === null
    ? history === null ? null : integerAt(history, "oid")
    : firstInteger(value, history, "aid");
  const owner = firstRecord(value.owner, value.upper);
  return {
    bvid,
    aid,
    ep_id: episodeId,
    cid: firstInteger(value, history, "cid"),
    title: stringAt(value, "title"),
    description: (stringAt(value, "intro") || stringAt(value, "description") || "").slice(0, 1_000),
    cover: stringAt(value, "cover") || stringAt(value, "pic"),
    duration: numberAt(value, "duration"),
    published_at: integerAt(value, "pubdate") || integerAt(value, "ctime"),
    viewed_at: integerAt(value, "view_at"),
    page: history === null ? integerAt(value, "page") : integerAt(history, "page"),
    part: history === null ? null : stringAt(history, "part"),
    favorite_at: integerAt(value, "fav_time"),
    owner:
      owner === null
        ? null
        : { mid: integerAt(owner, "mid"), name: stringAt(owner, "name") || stringAt(owner, "uname") },
    url:
      bvid !== null
        ? "https://www.bilibili.com/video/" + bvid
        : episodeId === null ? null : "https://www.bilibili.com/bangumi/play/ep" + episodeId
  };
}

function compactFollowedSeason(value: JsonRecord): JsonRecord {
  const seasonId = integerAt(value, "season_id");
  const mediaId = integerAt(value, "media_id");
  return {
    season_id: seasonId,
    media_id: mediaId,
    title: stringAt(value, "title"),
    cover: stringAt(value, "cover"),
    evaluate: (stringAt(value, "evaluate") || "").slice(0, 1_000),
    follow_status: integerAt(value, "follow_status"),
    new_ep: recordAt(value, "new_ep"),
    url:
      seasonId !== null
        ? "https://www.bilibili.com/bangumi/play/ss" + seasonId
        : mediaId === null ? null : "https://www.bilibili.com/bangumi/media/md" + mediaId
  };
}

function compactInteractiveNode(
  data: JsonRecord,
  requestedEdge: number
): { node: JsonRecord; children: number[] } {
  const edges = recordAt(data, "edges");
  const questions: JsonRecord[] = [];
  const children: number[] = [];
  if (edges !== null) {
    records(arrayAt(edges, "questions")).forEach((question) => {
      const choices = records(arrayAt(question, "choices")).map((choice) => {
        const child = integerAt(choice, "id");
        if (child !== null && child >= 0) {
          children.push(child);
        }
        return {
          edge_id: child,
          cid: integerAt(choice, "cid"),
          label: stringAt(choice, "option"),
          condition: stringAt(choice, "condition"),
          is_default: booleanAt(choice, "is_default") === true,
          native_action: choice.native_action === undefined ? null : choice.native_action,
          platform_action: choice.platform_action === undefined ? null : choice.platform_action
        };
      });
      questions.push({
        id: integerAt(question, "id"),
        title: stringAt(question, "title"),
        type: integerAt(question, "type"),
        start_time_ms: numberAt(question, "start_time_r"),
        duration_ms: numberAt(question, "duration"),
        pause_video: booleanAt(question, "pause_video") === true,
        choices
      });
    });
  }
  const story = records(arrayAt(data, "story_list")).map((item) => ({
    edge_id: integerAt(item, "edge_id"),
    node_id: integerAt(item, "node_id"),
    cid: integerAt(item, "cid"),
    title: stringAt(item, "title"),
    cover: stringAt(item, "cover"),
    start_position: numberAt(item, "start_pos"),
    is_current: booleanAt(item, "is_current") === true
  }));
  const preload = recordAt(data, "preload");
  const videos = preload === null ? [] : records(arrayAt(preload, "video"));
  return {
    node: {
      edge_id: integerAt(data, "edge_id") === null ? requestedEdge : integerAt(data, "edge_id"),
      title: stringAt(data, "title"),
      is_leaf: booleanAt(data, "is_leaf") === true,
      questions,
      story,
      preload: videos.map((item) => ({ aid: integerAt(item, "aid"), cid: integerAt(item, "cid") }))
    },
    children
  };
}

function requireData(payload: JsonRecord, label: string): JsonRecord {
  const data = recordAt(payload, "data");
  if (data === null) {
    throw new Error(label + " response is missing data.");
  }
  return data;
}

function records(values: JsonValue[]): JsonRecord[] {
  return values
    .map((value) => optionalRecord(value))
    .filter((value): value is JsonRecord => value !== null);
}

function stripHtml(value: string | null): string | null {
  if (value === null) {
    return null;
  }
  return value
    .replace(/<[^>]+>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'");
}

function firstString(left: JsonRecord | null, right: JsonRecord | null, key: string): string | null {
  const fromLeft = left === null ? null : stringAt(left, key);
  return fromLeft === null ? right === null ? null : stringAt(right, key) : fromLeft;
}

function firstInteger(left: JsonRecord | null, right: JsonRecord | null, key: string): number | null {
  const fromLeft = left === null ? null : integerAt(left, key);
  return fromLeft === null ? right === null ? null : integerAt(right, key) : fromLeft;
}

function firstRecord(left: JsonValue | undefined, right: JsonValue | undefined): JsonRecord | null {
  const fromLeft = left === undefined ? null : optionalRecord(left);
  return fromLeft === null ? right === undefined ? null : optionalRecord(right) : fromLeft;
}

function positiveInteger(value: number, name: string): number {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(name + " must be a positive integer.");
  }
  return value;
}
