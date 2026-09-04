import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { repository, target, hostError } from "./bilibili_test_support.mjs";
import { loadHostToolkit } from "./bilibili_host_runtime_support.mjs";

const args = process.argv.slice(2);
function argument(name) {
  const index = args.indexOf(name);
  if (index < 0 || !args[index + 1]) throw new Error("Required argument: " + name);
  return args[index + 1];
}
const envFile = argument("--cookie-env");
const ffmpeg = argument("--ffmpeg");
const ffprobe = argument("--ffprobe");
const workspace = path.join(repository, "work");
await fs.mkdir(workspace, { recursive: true });
const output = await fs.mkdtemp(path.join(workspace, "bilibili-live-"));
const virtualRoot = "/sdcard/Download/Kiyori/Bilibili";
function local(name) {
  if (name !== virtualRoot && !name.startsWith(virtualRoot + "/")) throw new Error("Live test path is outside the dedicated output root");
  const result = path.resolve(output, "." + name.slice(virtualRoot.length));
  if (result !== output && !result.startsWith(output + path.sep)) throw new Error("Live test path traversal rejected");
  return result;
}
const env = await fs.readFile(envFile, "utf8");
const line = env.split(/\r?\n/).find(value => /^BILIBILI_COOKIE=/.test(value));
if (!line) throw new Error("BILIBILI_COOKIE is missing from the explicitly supplied env file");
let cookie = line.slice(line.indexOf("=") + 1).trim();
if (cookie.startsWith('"')) cookie = JSON.parse(cookie);
else if (cookie.startsWith("'") && cookie.endsWith("'")) cookie = cookie.slice(1, -1);
if (!cookie || /[\r\n]/.test(cookie)) throw new Error("Invalid Cookie header");
const headers = { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36", Referer: "https://www.bilibili.com/" };
const bridgeSource = await fs.readFile(path.join(repository, "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/ToolPkgBilibiliBridge.kt"), "utf8");
const allowedPaths = new Set([...bridgeSource.matchAll(/"(\/(?:x|pgc)\/[^"?]+)"/g)].map(match => match[1]));
let riskBlocked = null;
let lastRequest = 0;
const requests = [];
const mediaRequests = [];
const authorizedMediaUrls = new Set();
const services = {
  async get(request) {
    if (riskBlocked) throw riskBlocked;
    await new Promise(resolve => setTimeout(resolve, Math.max(0, 350 - (Date.now() - lastRequest))));
    lastRequest = Date.now();
    let url = new URL(request.url);
    const api = request.mode === "api" || request.mode === "api_anonymous";
    const binary = url.hostname === "api.bilibili.com" && url.pathname === "/x/v2/dm/web/seg.so";
    const publicResource = binary || (url.hostname === "comment.bilibili.com" && /^\/\d+\.xml$/.test(url.pathname)) || (url.hostname.endsWith(".hdslb.com") && url.pathname.startsWith("/bfs/"));
    if (url.protocol !== "https:" || url.username || url.password || url.port || (api && (url.hostname !== "api.bilibili.com" || !allowedPaths.has(url.pathname))) || (request.mode === "public" && !publicResource) || (request.mode === "resolve" && url.hostname !== "b23.tv")) throw new Error("Live adapter URL rejected");
    for (let redirect = 0; redirect <= 5; redirect++) {
      const response = await fetch(url, { headers: { ...headers, ...(request.mode === "api" ? { Cookie: cookie } : {}) }, redirect: "manual", signal: AbortSignal.timeout(75000) });
      const endpoint = url.origin + url.pathname;
      requests.push({ mode: request.mode, endpoint, http_status: response.status });
      if (request.mode === "resolve" && response.status >= 300 && response.status < 400) {
        url = new URL(response.headers.get("location"), url);
        if (url.protocol !== "https:" || url.username || url.password || url.port || !(url.hostname === "b23.tv" || url.hostname === "bilibili.com" || url.hostname.endsWith(".bilibili.com"))) throw new Error("Unsafe short-link redirect");
        if (url.hostname !== "b23.tv") return { success: true, final_url: url.href, body: "", cookie_configured: false, http_status: response.status };
        continue;
      }
      if (!response.ok) {
        const error = hostError(response.status === 412 || response.status === 429 ? "RISK_CONTROL" : "HTTP_ERROR", "HTTP " + response.status, endpoint);
        error.details.error.http_status = response.status;
        if (response.status === 412 || response.status === 429) riskBlocked = error;
        throw error;
      }
      const bytes = Buffer.from(await response.arrayBuffer());
      if (bytes.length > 16 * 1024 * 1024) throw new Error("Live response exceeds host limit");
      const body = bytes.toString("utf8");
      if (api || (binary && response.headers.get("content-type")?.includes("json"))) {
        const payload = JSON.parse(body);
        const navKeys = url.pathname === "/x/web-interface/nav" && payload.code === -101 && payload.data?.isLogin === false && [payload.data?.wbi_img?.img_url, payload.data?.wbi_img?.sub_url].every(value => typeof value === "string" && /\/[a-f0-9]{32}\.[a-z]+$/.test(value));
        if (payload.code !== 0 && !navKeys) {
          const code = payload.code === -101 ? "NOT_LOGGED_IN" : [-352, -412].includes(payload.code) ? "RISK_CONTROL" : "API_ERROR";
          const error = hostError(code, payload.message || payload.msg || "API error", endpoint, payload.code);
          if (code === "RISK_CONTROL") riskBlocked = error;
          throw error;
        }
        if (url.pathname.endsWith("/playurl")) {
          const collect = value => {
            if (Array.isArray(value)) value.forEach(collect);
            else if (value && typeof value === "object") {
              for (const [key, child] of Object.entries(value)) {
                if (["baseUrl", "base_url", "url"].includes(key) && typeof child === "string" && child.startsWith("https://")) {
                  const media = new URL(child);
                  if (!media.username && !media.password && /^\/(v1\/resource\/)?upgcxcode\//.test(media.pathname)) authorizedMediaUrls.add(media.href);
                } else collect(child);
              }
            }
          };
          collect(payload);
        }
      }
      return { success: true, body: binary ? bytes.toString("base64") : body, body_encoding: binary ? "base64" : "utf8", final_url: url.href, cookie_configured: true, http_status: response.status };
    }
    throw new Error("Short-link redirect limit exceeded");
  }
};
async function command(executable, commandArgs) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, commandArgs, { windowsHide: true, shell: false });
    const buffers = [];
    child.stdout.on("data", chunk => buffers.push(chunk));
    child.stderr.on("data", chunk => buffers.push(chunk));
    child.on("error", reject);
    child.on("close", code => resolve({ returnCode: code, output: Buffer.concat(buffers).toString("utf8"), executionPlane: "desktop-live-adapter" }));
  });
}
const ok = { successful: true, details: "ok" };
const tools = {
  Files: {
    exists: async name => { try { const stat = await fs.stat(local(name)); return { exists: true, isDirectory: stat.isDirectory() }; } catch (error) { if (error.code === "ENOENT") return { exists: false }; throw error; } },
    mkdir: async name => { await fs.mkdir(local(name), { recursive: true }); return ok; },
    write: async (name, content) => { await fs.writeFile(local(name), content); return ok; },
    info: async name => { const stat = await fs.stat(local(name)); return { exists: true, size: stat.size }; },
    list: async name => ({ entries: await Promise.all((await fs.readdir(local(name), { withFileTypes: true })).map(async entry => ({ name: entry.name, isDirectory: entry.isDirectory(), size: (await fs.stat(path.join(local(name), entry.name))).size }))) }),
    move: async (source, destination) => { await fs.rename(local(source), local(destination)); return ok; },
    deleteFile: async (name, recursive) => { await fs.rm(local(name), { recursive, force: false }); return ok; },
    download: async (url, destination, _environment, downloadHeaders) => {
      const parsed = new URL(url);
      if (!authorizedMediaUrls.has(parsed.href)) throw new Error("Media URL was not returned by the authorized playurl API");
      if (!downloadHeaders?.Referer || !downloadHeaders?.["User-Agent"] || downloadHeaders.Cookie) throw new Error("Invalid media request identity");
      if (args.includes("--native-samples")) {
        mediaRequests.push({ url, headers: downloadHeaders });
        await fs.writeFile(path.join(output, "native-media-input.json"), JSON.stringify(mediaRequests));
      }
      const response = await fetch(url, { headers: downloadHeaders, redirect: "error", signal: AbortSignal.timeout(180000) });
      if (!response.ok) {
        const error = hostError([412, 429].includes(response.status) ? "RISK_CONTROL" : "HTTP_ERROR", "Media HTTP " + response.status, parsed.origin + parsed.pathname);
        if ([412, 429].includes(response.status)) riskBlocked = error;
        throw error;
      }
      const file = await fs.open(local(destination), "wx");
      let bytes = 0;
      try { for await (const chunk of response.body) { bytes += chunk.length; if (bytes > 200 * 1024 * 1024) throw new Error("Live download exceeds 200 MiB bound"); await file.write(chunk); } } finally { await file.close(); }
      return ok;
    }
  },
  FFmpeg: {
    info: async () => command(ffmpeg, ["-version"]),
    probe: async name => { const result = await command(ffprobe, ["-v", "quiet", "-show_format", "-show_streams", "-of", "json", local(name)]); const data = JSON.parse(result.output); return { ...result, mediaInfo: { ...data.format, streams: data.streams } }; },
    execute: async text => command(ffmpeg, [...text.matchAll(/"([^"\n]*)"|([^\s"]+)/g)].map(match => { const value = match[1] === undefined ? match[2] : match[1]; return value.startsWith(virtualRoot) ? local(value) : value; }))
  }
};
const api = await loadHostToolkit(services, tools);
const report = { evidence_level: "packaged-dist-host-js-runtime-real-network-desktop-media-not-android", started_at: new Date().toISOString(), output, results: [], requests };
async function run(name, params, label = name) {
  if (riskBlocked) throw new Error("Live regression stopped after risk control");
  const started = Date.now();
  const result = await api["bilibili_" + name](params);
  const safe = { label, success: result.success, elapsed_ms: Date.now() - started, error: result.error ?? null, count: result.count ?? result.track_count ?? result.root_count ?? result.node_count ?? null, bytes: result.bytes ?? null, available: result.available ?? null, failed_steps: result.failed_steps ?? null };
  if (name === "doctor") safe.logged_in = result.logged_in;
  if (name === "search") safe.contains_target = result.results?.some(item => item.bvid === target) ?? false;
  if (name === "formats") safe.qualities = result.formats?.accept_qualities ?? [];
  if (name === "download") safe.duration_seconds = result.duration_seconds ?? null;
  if (name === "frames") safe.actual_count = result.actual_count ?? null;
  if (name === "capture") safe.steps = result.steps?.map(step => ({ name: step.name, success: step.success, error: step.error ?? null }));
  if (name === "user") { safe.full_requested = result.full_requested; safe.profile_source = result.profile_source; safe.profile_fields = Object.keys(result.profile ?? {}); }
  if (name === "danmaku") { safe.historical_complete = result.historical_complete; safe.completeness_note = result.completeness_note; }
  report.results.push(safe);
  console.log(JSON.stringify(safe));
  await fs.writeFile(path.join(output, "report.json"), JSON.stringify(report, null, 2));
  return result;
}
await run("doctor", {});
if (args.includes("--diagnostics-only")) {
  const basic = await run("user", { user: "2" }, "user-basic");
  const full = await run("user", { user: "2", full: true }, "user-full");
  if (basic.success && full.success && (basic.profile !== null || !full.profile || !full.full_requested || full.profile_source !== "/x/space/wbi/acc/info")) throw new Error("Full profile contract failed");
  const danmaku = await run("danmaku", { target, source: "xml", limit: 20, inline: true });
  if (danmaku.success && (danmaku.historical_complete !== false || !danmaku.completeness_note.includes("XML"))) throw new Error("XML snapshot contract failed");
  const capture = await run("capture", { target, media: false, subtitles: false, danmaku: false, comments: false, summary: false });
  if (capture.success && (await tools.Files.exists(capture.root + "/media")).exists) throw new Error("Disabled media unexpectedly created a directory");
} else if (args.includes("--media-matrix")) {
  for (const mediaTarget of args.includes("--target") ? [argument("--target")] : ["BV1GJ411x7h7", "BV1dS421Q7mS", "BV1mfuV6kE4U", "ep5137672"]) {
    const options = { target: mediaTarget, quality: mediaTarget.startsWith("ep") ? "480p" : "360p" };
    const media = await run("download", options, mediaTarget + "-full");
    if (media.success) {
      await run("download", options, mediaTarget + "-conflict");
      await run("frames", { input_path: media.output, count: 4 }, mediaTarget + "-frames");
    }
    await run("download", { ...options, clip_start: 0, clip_end: 8 }, mediaTarget + "-clip");
    await run("download", { ...options, audio_only: true, audio_format: "mp3" }, mediaTarget + "-mp3");
  }
} else if (!args.includes("--extended-only")) {
await run("resolve", { target: "https://b23.tv/NYbi1B4" });
for (let index = 1; index <= 10; index++) await run("info", { target }, "info-" + index);
await run("search", { keyword: "转到人工智能是我这辈子做过的最正确的决定", limit: 20 });
await run("subtitles", { target, format: "all", overwrite: true });
await run("danmaku", { target, format: "all", overwrite: true });
await run("comments", { target, output: true, pages: 2, limit: 30, overwrite: true });
await run("summary", { target });
await run("formats", { target });
await run("user", { user: "441167301" }, "user-uid");
await run("user", { user: "https://space.bilibili.com/441167301" }, "user-url");
await run("account", { kind: "history", limit: 2 }, "account-history");
await run("account", { kind: "favorites", limit: 2 }, "account-favorites");
await run("interactive", { target });
await run("season", { target: "ep779990" });
const media = await run("download", { target, quality: "360p", overwrite: true });
if (media.success) await run("frames", { input_path: media.output, count: 8, overwrite: true });
const captureRoot = virtualRoot + "/capture-check";
await run("capture", { target, output_root: captureRoot }, "capture-first");
await run("capture", { target, output_root: captureRoot, overwrite: true }, "capture-overwrite");
await run("capture", { target, output_root: captureRoot }, "capture-conflict");
} else {
  const favorites = await run("account", { kind: "favorites", limit: 1 }, "account-folder-discovery");
  if (favorites.items?.[0]?.id) await run("account", { kind: "favorite_items", folder_id: favorites.items[0].id, limit: 2 });
  for (const kind of ["watch_later", "liked", "coins", "followed_bangumi", "followed_cinema", "submissions"]) await run("account", { kind, limit: 2 }, "account-" + kind);
  await run("download", { target, quality: "360p", audio_only: true, audio_format: "m4a" }, "audio-m4a");
  await run("download", { target, quality: "360p", clip_start: 2, clip_end: 6 }, "clip-mp4");
}
report.finished_at = new Date().toISOString();
report.passed = report.results.every(result => result.label.endsWith("-conflict") ? result.error?.code === "OUTPUT_EXISTS" : result.success === true);
await fs.writeFile(path.join(output, "report.json"), JSON.stringify(report, null, 2));
console.log(JSON.stringify({ report: path.join(output, "report.json"), passed: report.passed, requests: requests.length }));
if (!report.passed) process.exitCode = 1;
