import assert from "node:assert/strict";
import test from "node:test";
import { fixtureService, target, video, memoryTools, hostError, readMetadata } from "./bilibili_test_support.mjs";
import { hostRuntime } from "./bilibili_host_runtime_support.mjs";

test("packaged tools run through host bootstrap, module factory and native callbacks", async () => {
  const { tools } = memoryTools();
  const download = tools.Files.download;
  let mediaCalls = 0;
  tools.Files.download = async (url, destination, environment, headers) => {
    mediaCalls++;
    assert.equal(environment, "android");
    assert.equal(headers.Referer, "https://www.bilibili.com/");
    assert.equal(headers["User-Agent"], "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36");
    assert.equal(Object.keys(headers).some(name => name.toLowerCase() === "cookie"), false);
    return download(url, destination, environment, headers);
  };
  const host = await hostRuntime(fixtureService({
    "/x/web-interface/wbi/search/type": () => ({ code: 0, data: { result: [video], numResults: 1, numPages: 1 } }),
    "/pgc/view/web/season": () => ({ code: 0, result: { season_id: 1, title: "Season", episodes: [{ id: 1, bvid: target, aid: video.aid, cid: video.cid }] } }),
    "/x/web-interface/card": () => ({ code: 0, data: { card: { mid: 2, name: "User" } } }),
    "/x/relation/stat": () => ({ code: 0, data: { mid: 2, follower: 10 } }),
    "/x/web-interface/history/cursor": () => ({ code: 0, data: { list: [], cursor: { max: 0 } } }),
    "/x/player/wbi/playurl": () => ({ code: 0, data: { quality: 16, timelength: 323000, accept_quality: [16], accept_description: ["360P"], dash: {
      video: [{ id: 16, codecid: 7, codecs: "avc1", baseUrl: "https://test.bilivideo.com/video", mimeType: "video/mp4" }],
      audio: [{ id: 30280, codecs: "mp4a", baseUrl: "https://test.bilivideo.com/audio", mimeType: "audio/mp4" }]
    } } })
  }), tools);
  const cases = [
    ["bilibili_resolve", { target }],
    ["bilibili_info", { target }],
    ["bilibili_doctor", {}],
    ["bilibili_subtitles", { target }],
    ["bilibili_summary", { target }],
    ["bilibili_interactive", { target }],
    ["bilibili_comments", { target }],
    ["bilibili_danmaku", { target, inline: true }],
    ["bilibili_capture", { target, overwrite: true }],
    ["bilibili_frames", { input_path: "/sdcard/input.mp4", count: 2 }],
    ["bilibili_search", { keyword: "test" }],
    ["bilibili_season", { target: "ep1" }],
    ["bilibili_user", { user: "2" }],
    ["bilibili_account", { kind: "history" }],
    ["bilibili_formats", { target }],
    ["bilibili_download", { target, quality: "360p" }]
  ];
  assert.deepEqual(cases.map(([name]) => name).sort(), (await readMetadata()).tools.map(tool => tool.name).sort());
  for (const [name, params] of cases) {
    const response = await host.execute(name, params);
    assert.equal(response.method, "setCallResult", JSON.stringify({ name, response, logs: host.logs }));
    assert.equal(response.result.success, true, JSON.stringify({ name, response, logs: host.logs }));
  }
  assert.ok(host.calls.some(call => call.method === "bilibiliRequest"));
  assert.ok(host.calls.some(call => call.method === "callToolAsyncForExecution"));
  assert.equal(mediaCalls, 2);
});

test("module locals cannot collide with runtime aliases; cached calls retain context", async () => {
  const host = await hostRuntime(fixtureService());
  const script = '"use strict"; function _() { return 42; } const Tools = { local: true }; exports.run = () => ({ success: true, value: _(), local: Tools.local, strict: (function() { return this === undefined; })(), caller: getCallerName() });';
  for (const caller of ["first", "second"]) {
    const response = await host.execute("run", { __operit_package_caller_name: caller }, script);
    assert.equal(response.method, "setCallResult", JSON.stringify(response));
    assert.equal(response.result.value, 42);
    assert.equal(response.result.local, true);
    assert.equal(response.result.strict, true);
    assert.equal(response.result.caller, caller);
  }
});

test("native API errors survive the packaged runtime with code and diagnostics", async () => {
  const host = await hostRuntime(fixtureService({ "/x/web-interface/view": () => { throw hostError("RISK_CONTROL", "Request blocked", "https://api.bilibili.com/x/web-interface/view", -352); } }));
  const { result } = await host.execute("bilibili_info", { target });
  assert.equal(result.success, false);
  assert.equal(result.error.code, "RISK_CONTROL");
  assert.equal(result.error.api_code, -352);
  assert.ok(host.logs.some(message => message.includes("Request blocked")));
});

test("required CommonJS and JSON modules preserve exports and cache identity", async () => {
  const modules = {
    "lib/value.js": '"use strict"; function _() { return 7; } let calls = 0; module.exports = { value: () => _(), next: () => ++calls, lang: () => getLang() };',
    "lib/config.json": '{"count":3}'
  };
  const host = await hostRuntime(fixtureService(), memoryTools().tools, modules);
  const script = 'const child = require("./lib/value"); const config = require("./lib/config.json"); exports.run = () => ({ success: true, same: child === require("./lib/value"), value: child.value(), calls: child.next(), count: config.count, lang: child.lang() });';
  let expectedCalls = 0;
  for (const lang of ["zh", "en"]) {
    const { method, result } = await host.execute("run", { toolPkgId: "com.kiyori.bilibili_toolkit", __operit_script_screen: "main.js", __operit_package_lang: lang }, script);
    assert.equal(method, "setCallResult", JSON.stringify(result));
    assert.equal(result.value, 7);
    assert.equal(result.same, true);
    assert.equal(result.calls, ++expectedCalls);
    assert.equal(result.count, 3);
    assert.equal(result.lang, lang);
  }
});

test("missing exports and async failures remain explicit host errors", async () => {
  const host = await hostRuntime(fixtureService());
  const missing = await host.execute("missing", {}, "exports.present = () => 1;");
  assert.equal(missing.method, "setCallError");
  assert.match(missing.result.message, /Function 'missing' not found.*present/);
  const rejection = await host.execute("run", {}, 'exports.run = async () => { throw new Error("Deliberate failure"); };');
  assert.equal(rejection.method, "setCallError");
  assert.match(rejection.result.message, /Deliberate failure/);
});

test("native file errors preserve the host plain-object message", async () => {
  const { tools } = memoryTools();
  tools.Files.mkdir = async () => { throw new Error("Storage permission denied"); };
  const host = await hostRuntime(fixtureService(), tools);
  const { result } = await host.execute("bilibili_subtitles", { target });
  assert.equal(result.success, false);
  assert.equal(result.message, "Storage permission denied");
});

test("actual Tools bridge forwards desktop media headers without any Cookie", async () => {
  const { tools } = memoryTools();
  let observed = null;
  tools.Files.download = async (_url, _destination, environment, headers) => {
    observed = { environment, headers };
    return { successful: true, details: "ok" };
  };
  const host = await hostRuntime(fixtureService(), tools);
  await host.execute("run", {}, 'exports.run = async () => { await Tools.Files.download("https://test.bilivideo.com/file", "/sdcard/file", "android", { Referer: "https://www.bilibili.com/", "User-Agent": "Web test" }); return { success: true }; };');
  assert.equal(observed.environment, "android");
  assert.equal(observed.headers.Referer, "https://www.bilibili.com/");
  assert.equal(observed.headers["User-Agent"], "Web test");
  assert.equal(observed.headers.Cookie, undefined);
});
