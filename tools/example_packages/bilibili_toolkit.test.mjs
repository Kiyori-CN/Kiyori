import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { fixtureService, hostError, loadToolkit, memoryTools, readMetadata, repository, target, video } from "./bilibili_test_support.mjs";

test('artifact defaults are read per call and explicit destinations retain precedence', async () => {
  const { tools, files } = memoryTools();
  let root = '/storage/emulated/0/Documents/资料一';
  const api = await loadToolkit(fixtureService(), tools, { error() {} }, () => ({ android: root, linux: '/workspace' }));
  const first = await api.bilibili_capture({ target });
  assert.equal(first.success, true, JSON.stringify(first));
  assert.ok(first.root.startsWith(root + '/bilibili/'));
  root = '/storage/emulated/0/Documents/资料二';
  const second = await api.bilibili_capture({ target });
  assert.equal(second.success, true, JSON.stringify(second));
  assert.ok(second.root.startsWith(root + '/bilibili/'));
  assert.ok(files.has(first.manifest));
  const explicit = await api.bilibili_capture({ target, output_root: '/sdcard/chosen' });
  assert.ok(explicit.root.startsWith('/sdcard/chosen/'));
});

test("all 16 tool schemas expose implemented optional arguments", async () => {
  const meta = await readMetadata();
  assert.equal(meta.tools.length, 16);
  const capture = meta.tools.find(tool => tool.name === "bilibili_capture");
  for (const name of ["overwrite", "output_root", "media", "quality", "frame_count", "comment_limit"]) {
    assert.equal(capture.parameters.find(parameter => parameter.name === name)?.required, false, name);
  }
  assert.equal(meta.tools.find(tool => tool.name === "bilibili_download").parameters.find(p => p.name === "quality").required, false);
  const source = await fs.readFile(path.join(repository, "examples/bilibili_toolkit/src/lib/operations.ts"), "utf8");
  for (const tool of meta.tools) {
    const name = tool.name.replace("bilibili_", "");
    const signature = source.match(new RegExp("export async function " + name + "\\(params: (\\w+)"));
    if (!signature) continue;
    function fields(type) {
      const definition = source.match(new RegExp("export interface " + type + "(?: extends (\\w+))? \\{([\\s\\S]*?)\\n\\}"));
      return [...(definition[1] ? fields(definition[1]) : []), ...[...definition[2].matchAll(/(\w+)\??: (string|number|boolean);/g)].map(match => match[1])];
    }
    assert.deepEqual(tool.parameters.map(p => p.name).sort(), fields(signature[1]).sort(), name);
  }
});

test("capture resolves video and WBI keys once and can overwrite all five steps", async () => {
  const service = fixtureService();
  const { tools, files } = memoryTools();
  const api = await loadToolkit(service, tools);
  const first = await api.bilibili_capture({ target });
  assert.equal(first.success, true);
  assert.equal(first.steps.length, 5);
  assert.equal(first.succeeded_steps, 5);
  assert.equal(service.requests.filter(r => new URL(r.url).pathname === "/x/web-interface/view").length, 1);
  assert.equal(service.requests.filter(r => new URL(r.url).pathname === "/x/web-interface/nav").length, 1);
  assert.equal(JSON.parse(files.get(first.manifest)).manifest, first.manifest);
  const original = files.get(first.root + "/metadata/video.json");
  const conflict = await api.bilibili_capture({ target });
  assert.equal(conflict.success, false);
  assert.equal(conflict.error.code, "OUTPUT_EXISTS");
  assert.ok(conflict.message.length > 0);
  assert.equal(files.get(first.root + "/metadata/video.json"), original);
  const overwrite = await api.bilibili_capture({ target, overwrite: true });
  assert.equal(overwrite.success, true);
  assert.equal(overwrite.steps.length, 5);
});

test("partial capture retains structured errors through the host message/data protocol", async () => {
  const service = fixtureService({ "/x/web-interface/view/conclusion/get": () => { throw hostError("NOT_LOGGED_IN", "账号未登录", "https://api.bilibili.com/x/web-interface/view/conclusion/get", -101); } });
  const { tools } = memoryTools();
  const api = await loadToolkit(service, tools);
  const result = await api.bilibili_capture({ target });
  assert.equal(result.success, false);
  assert.equal(result.partial, true);
  assert.ok(result.message.includes("summary"));
  assert.equal(result.data.steps.length, 5);
  assert.equal(result.steps[4].error.api_code, -101);
  assert.equal(result.steps[4].error.http_status, 200);
  assert.ok(JSON.stringify(result).length > 0);
});

test("manifest write failure does not discard completed step data", async () => {
  const { tools } = memoryTools();
  const write = tools.Files.write;
  tools.Files.write = async (name, content) => name.endsWith("manifest.json") ? { successful: false, details: "disk full" } : write(name, content);
  const api = await loadToolkit(fixtureService(), tools);
  const result = await api.bilibili_capture({ target });
  assert.equal(result.success, false);
  assert.equal(result.data.steps.length, 5);
  assert.ok(result.manifest_error.message.includes("disk full"));
});

test("subtitles absent and noninteractive are explicit successful business results", async () => {
  const { tools } = memoryTools();
  const api = await loadToolkit(fixtureService(), tools);
  const subtitles = await api.bilibili_subtitles({ target });
  assert.equal(subtitles.success, true);
  assert.equal(subtitles.available, false);
  assert.equal(subtitles.track_count, 0);
  const graph = await api.bilibili_interactive({ target });
  assert.equal(graph.success, true);
  assert.equal(graph.reason, "NOT_INTERACTIVE");
});

test("comments consume opaque server cursors and deduplicate roots", async () => {
  const offsets = [];
  const service = fixtureService({ "/x/v2/reply/wbi/main": (url) => {
    offsets.push(JSON.parse(url.searchParams.get("pagination_str")).offset);
    return { code: 0, data: { cursor: { all_count: 100, is_end: offsets.length === 2, pagination_reply: { next_offset: '{"opaque":"server-value"}' } }, replies: [{ rpid: 1 }, { rpid: offsets.length + 1 }] } };
  } });
  const api = await loadToolkit(service, memoryTools().tools);
  const result = await api.bilibili_comments({ target, pages: 2, limit: 10 });
  assert.equal(result.success, true);
  assert.equal(result.root_count, 3);
  assert.equal(result.fetched_pages, 2);
  assert.deepEqual(offsets, ["", '{"opaque":"server-value"}']);
});

test("missing pagination cursor fails visibly rather than repeating page one", async () => {
  const service = fixtureService({ "/x/v2/reply/wbi/main": () => ({ code: 0, data: { cursor: { is_end: false }, replies: [{ rpid: 1 }] } }) });
  const api = await loadToolkit(service, memoryTools().tools);
  const result = await api.bilibili_comments({ target, pages: 2 });
  assert.equal(result.error.code, "INVALID_CURSOR");
});

function varint(value) {
  let current = BigInt(value);
  const bytes = [];
  while (current > 127n) { bytes.push(Number(current & 127n) | 128); current >>= 7n; }
  return [...bytes, Number(current)];
}
function block(field, value) { return [...varint(field * 8 + 2), ...varint(value.length), ...value]; }
function encodedSegment() {
  const entry = [...varint(8), ...varint(9007199254740993n), 16, ...varint(1250), 24, 1, 32, 25, ...block(7, Buffer.from("中文<&>")), ...block(12, Buffer.from("9007199254740993"))];
  return Buffer.from(block(1, entry)).toString("base64");
}

test("protobuf preserves 64-bit row IDs, UTF-8 and milliseconds", async () => {
  const api = await loadToolkit(fixtureService(), memoryTools().tools);
  const [entry] = api.decodeDanmakuSegment(encodedSegment());
  assert.equal(entry.row_id, "9007199254740993");
  assert.equal(entry.progress, 1.25);
  assert.equal(entry.text, "中文<&>");
  assert.ok(api.danmakuXml([entry]).includes("中文&lt;&amp;&gt;"));
  assert.equal(api.parseDanmakuXml(api.danmakuXml([entry]), 10)[0].text, entry.text);
  for (const malformed of ["not-base64!", Buffer.from([10, 100, 1]).toString("base64"), Buffer.from([0]).toString("base64"), Buffer.from([128]).toString("base64")]) {
    assert.throws(() => api.decodeDanmakuSegment(malformed));
  }
});

test("segmented danmaku honors limit and reports unfetched segments", async () => {
  const service = fixtureService({ "/x/web-interface/view": () => ({ code: 0, data: { ...video, pages: [{ ...video.pages[0], duration: 1000 }] } }) });
  const originalGet = service.get.bind(service);
  service.get = async request => request.url.includes("seg.so") ? { body: encodedSegment(), body_encoding: "base64" } : originalGet(request);
  const api = await loadToolkit(service, memoryTools().tools);
  const result = await api.bilibili_danmaku({ target, limit: 1, inline: true, format: "all" });
  assert.equal(result.success, true);
  assert.equal(result.count, 1);
  assert.equal(result.fetched_segments, 1);
  assert.equal(result.total_segments, 3);
  assert.equal(result.truncated, true);
  assert.equal(result.outputs.length, 4);
});

test("all subtitle formats preserve timing and text", async () => {
  const api = await loadToolkit(fixtureService(), memoryTools().tools);
  const cues = [{ from: 1.25, to: 2.5, content: "测试字幕" }];
  for (const format of ["json", "srt", "vtt", "txt", "ass"]) assert.ok(api.renderSubtitles(cues, format).includes("测试字幕"));
  assert.ok(api.renderSubtitles(cues, "srt").includes("00:00:01,250"));
});

test("invalid parameters and targets always produce nonempty structured failures", async () => {
  const api = await loadToolkit(fixtureService(), memoryTools().tools);
  for (const [name, params] of [["resolve", { target: "https://evil.example/" }], ["info", { target, part: 0 }], ["search", { keyword: "test", pages: 9999 }], ["frames", { input_path: "/sdcard/a.mp4", count: 0 }], ["danmaku", { target, source: "unknown" }], ["account", { kind: "post" }]]) {
    const result = await api["bilibili_" + name](params);
    assert.equal(result.success, false, name);
    assert.ok(result.message.length > 0, name);
    assert.ok(result.data.error.code, name);
  }
});

test("capture stops network after risk-control response but records every step", async () => {
  const service = fixtureService({ "/x/player/wbi/v2": () => { throw hostError("RISK_CONTROL", "风控", "https://api.bilibili.com/x/player/wbi/v2", -352); } });
  const api = await loadToolkit(service, memoryTools().tools);
  const result = await api.bilibili_capture({ target });
  assert.equal(result.success, false);
  assert.equal(result.steps.length, 5);
  assert.equal(result.failed_steps, 4);
  assert.equal(service.requests.length, 3);
  for (const step of result.steps.slice(1)) assert.equal(step.error.code, "RISK_CONTROL");
});

test("coin arrays and liked list objects use their distinct API contracts", async () => {
  const service = fixtureService({
    "/x/space/coin/video": () => ({ code: 0, data: [{ aid: 1, title: "coin" }] }),
    "/x/space/like/video": () => ({ code: 0, data: { list: [{ aid: 2, title: "like" }] } })
  });
  const api = await loadToolkit(service, memoryTools().tools);
  for (const kind of ["coins", "liked"]) {
    const result = await api.bilibili_account({ kind });
    assert.equal(result.success, true);
    assert.equal(result.count, 1);
  }
});

test("favorite next page reflects fetched pages, not the requested page budget", async () => {
  const service = fixtureService({ "/x/v3/fav/resource/list": () => ({ code: 0, data: { medias: [{ id: 1 }], has_more: true, info: { media_count: 100 } } }) });
  const api = await loadToolkit(service, memoryTools().tools);
  const result = await api.bilibili_account({ kind: "favorite_items", folder_id: 123, pages: 5, limit: 1 });
  assert.equal(result.next_cursor.page, 2);
});

function playback(drm = false) {
  return { code: 0, data: { has_drm: drm, quality: 16, timelength: 323000, accept_quality: [16], accept_description: ["360P"], dash: {
    video: [{ id: 16, codecid: 7, codecs: "avc1", baseUrl: "https://test.bilivideo.com/video", mimeType: "video/mp4" }],
    audio: [{ id: 30280, codecs: "mp4a", baseUrl: "https://test.bilivideo.com/audio", mimeType: "audio/mp4" }]
  } } };
}

test("download uses staging and does not delete original media on transfer failure", async () => {
  const service = fixtureService({ "/x/player/wbi/playurl": () => playback() });
  const { tools, files } = memoryTools();
  const api = await loadToolkit(service, tools);
  const result = await api.bilibili_download({ target, quality: "360p" });
  assert.equal(result.success, true);
  assert.ok(result.output.endsWith("video-360p.mp4"));
  files.set(result.output, "original-media");
  tools.Files.download = async () => { throw new Error("transfer failed"); };
  const failure = await api.bilibili_download({ target, quality: "360p", overwrite: true });
  assert.equal(failure.success, false);
  assert.equal(files.get(result.output), "original-media");
  assert.equal([...files.keys()].filter(name => name.includes("/.work-")).length, 0);
});

test("DRM and unavailable quality never silently select another stream", async () => {
  for (const drm of [false, true]) {
    const { tools, files } = memoryTools();
    const api = await loadToolkit(fixtureService({ "/x/player/wbi/playurl": () => playback(drm) }), tools);
    const result = await api.bilibili_download({ target, quality: drm ? "360p" : "1080p" });
    assert.equal(result.success, false);
    assert.equal(files.size, 0);
  }
});

test("frames verify files and remove only owned obsolete frames when overwriting", async () => {
  const { tools, files } = memoryTools();
  const api = await loadToolkit(fixtureService(), tools);
  const params = { input_path: "/sdcard/input.mp4", output_directory: "/sdcard/frames" };
  const first = await api.bilibili_frames({ ...params, count: 8 });
  assert.equal(first.actual_count, 8);
  files.set("/sdcard/frames/user-photo.jpg", "preserved");
  const second = await api.bilibili_frames({ ...params, count: 2, overwrite: true });
  assert.equal(second.actual_count, 2);
  assert.equal(files.has("/sdcard/frames/frame-003.jpg"), false);
  assert.equal(files.get("/sdcard/frames/user-photo.jpg"), "preserved");
  tools.FFmpeg.execute = async () => ({ returnCode: 0, output: "" });
  const failure = await api.bilibili_frames({ ...params, output_directory: "/sdcard/missing-frames", count: 3 });
  assert.equal(failure.success, false);
  assert.equal(failure.error.code, "INCOMPLETE_FRAMES");
});

test("resolve honors explicit part", async () => {
  const api = await loadToolkit(fixtureService(), memoryTools().tools);
  const result = await api.bilibili_resolve({ target, part: 2 });
  assert.equal(result.target.part, 2);
  assert.ok(result.target.url.endsWith("?p=2"));
});

test("full user profile exposes the additional endpoint payload without changing summary fields", async () => {
  const service = fixtureService({
    "/x/web-interface/card": () => ({ code: 0, data: { card: { name: "User", level_info: { current_level: 6 } } } }),
    "/x/relation/stat": () => ({ code: 0, data: { follower: 10 } }),
    "/x/space/wbi/acc/info": () => ({ code: 0, data: { name: "User", level: 6, live_room: { roomid: 123 }, school: { name: "School" } } })
  });
  const api = await loadToolkit(service, memoryTools().tools);
  const basic = await api.bilibili_user({ user: "2" });
  assert.equal(basic.profile, null);
  assert.equal(service.requests.length, 2);
  const full = await api.bilibili_user({ user: "2", full: true });
  assert.equal(full.success, true);
  assert.equal(full.profile.live_room.roomid, 123);
  assert.equal(full.profile_source, "/x/space/wbi/acc/info");
  assert.equal(full.user.level, basic.user.level);
});

test("capture without media creates no media directory and preserves earlier directories", async () => {
  const { tools, directories } = memoryTools();
  const api = await loadToolkit(fixtureService(), tools);
  const result = await api.bilibili_capture({ target, media: false });
  assert.equal(result.success, true);
  assert.equal([...directories].some(name => name.endsWith("/media")), false);
  directories.add(result.root + "/media");
  await api.bilibili_capture({ target, media: false, overwrite: true });
  assert.equal(directories.has(result.root + "/media"), true);
});

test("media failure keeps endpoint and header presence but removes signed queries", async () => {
  const { tools } = memoryTools();
  tools.Files.download = async () => { throw new Error("HTTP 403 https://test.bilivideo.com/video?secret=value"); };
  const api = await loadToolkit(fixtureService({ "/x/player/wbi/playurl": () => playback() }), tools);
  const result = await api.bilibili_download({ target, quality: "360p" });
  assert.equal(result.error.code, "MEDIA_DOWNLOAD_FAILED");
  assert.equal(result.error.endpoint, "https://test.bilivideo.com/video");
  assert.equal(result.error.cookie_sent, false);
  assert.equal(JSON.stringify(result).includes("secret"), false);
});
