import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

export const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
export const target = "BV1Bxtf6qEmz";
export const video = {
  bvid: target, aid: 117207879124237, cid: 41558281036, title: "测试视频", duration: 323,
  owner: { mid: 441167301, name: "测试UP" }, stat: { danmaku: 86, reply: 884 },
  pages: [{ page: 1, cid: 41558281036, part: "测试分P", duration: 323 }]
};
export const wbi = { img_url: "https://i0.hdslb.com/bfs/wbi/0123456789abcdef0123456789abcdef.png", sub_url: "https://i0.hdslb.com/bfs/wbi/abcdef0123456789abcdef0123456789.png" };
export function hostError(code, message, endpoint, apiCode = null) {
  const error = new Error(message);
  error.details = { success: false, error: { code, message, url: endpoint, http_status: 200, api_code: apiCode, api_message: message, attempts: 1 } };
  return error;
}
export async function loadToolkit(services, tools, logger = { error() {} }) {
  const output = await build({
    stdin: { contents: 'export * from "./examples/bilibili_toolkit/src/packages/bilibili"; export * from "./examples/bilibili_toolkit/src/lib/danmaku"; export * from "./examples/bilibili_toolkit/src/lib/content"; export * from "./examples/bilibili_toolkit/src/lib/crypto";', resolveDir: repository },
    bundle: true, format: "cjs", platform: "node", target: "es2020", write: false
  });
  const module = { exports: {} };
  vm.runInNewContext(output.outputFiles[0].text, { module, exports: module.exports, Error, console: logger, ToolPkg: { services: { bilibili: services } }, Tools: tools });
  return module.exports;
}
export function memoryTools() {
  const files = new Map();
  const directories = new Set();
  const ok = { successful: true, details: "ok" };
  const tools = {
    Files: {
      exists: async (name) => ({ exists: files.has(name) || directories.has(name), isDirectory: directories.has(name) }),
      mkdir: async (name) => { directories.add(name); return ok; },
      write: async (name, content) => { files.set(name, content); return ok; },
      info: async (name) => ({ exists: files.has(name), size: files.has(name) ? Buffer.byteLength(files.get(name)) : 0 }),
      list: async (name) => ({ entries: [...files.keys()].filter(key => path.posix.dirname(key) === name).map(key => ({ name: path.posix.basename(key), isDirectory: false, size: files.get(key).length })) }),
      move: async (source, destination) => { files.set(destination, files.get(source)); files.delete(source); return ok; },
      download: async (_url, name) => { files.set(name, "media-segment"); return ok; },
      deleteFile: async (name, recursive) => { for (const key of files.keys()) if (key === name || (recursive && key.startsWith(name + "/"))) files.delete(key); directories.delete(name); return ok; }
    },
    FFmpeg: {
      info: async () => ({ returnCode: 0, executionPlane: "test" }),
      probe: async () => ({ returnCode: 0, mediaInfo: { duration: "323" } }),
      execute: async (command) => {
        const destination = command.match(/"([^"\n]+)"$/)?.[1];
        if (!destination) throw new Error("Missing command output");
        if (destination.includes("%03d")) {
          const count = Number(command.match(/-frames:v (\d+)/)[1]);
          for (let frame = 1; frame <= count; frame++) files.set(destination.replace("%03d", String(frame).padStart(3, "0")), "jpeg");
        } else files.set(destination, "processed-media");
        return { returnCode: 0, output: "", executionPlane: "test" };
      }
    }
  };
  return { tools, files, directories };
}
export function fixtureService(overrides = {}) {
  const requests = [];
  return {
    requests,
    async get(request) {
      requests.push(request);
      const url = new URL(request.url);
      const endpoint = url.pathname;
      let body;
      if (overrides[endpoint]) body = await overrides[endpoint](url, request);
      else if (endpoint === "/x/web-interface/nav") body = { code: request.mode === "api_anonymous" ? -101 : 0, data: { isLogin: request.mode !== "api_anonymous", wbi_img: wbi, mid: 1, uname: "test" } };
      else if (endpoint === "/x/web-interface/view") body = { code: 0, data: video };
      else if (endpoint === "/x/player/wbi/v2") body = { code: 0, data: { subtitle: { subtitles: [] } } };
      else if (endpoint === "/x/v2/dm/web/seg.so") return { success: true, body: "", body_encoding: "base64", cookie_configured: true, http_status: 200, final_url: request.url };
      else if (endpoint === "/x/v2/reply/wbi/main") body = { code: 0, data: { cursor: { all_count: 884, is_end: true }, replies: [{ rpid: 1, content: { message: "测试评论" } }] } };
      else if (endpoint === "/x/web-interface/view/conclusion/get") body = { code: 0, data: { code: 0, model_result: { summary: "测试摘要" } } };
      else throw new Error("Unexpected test endpoint: " + endpoint);
      return { success: true, body: JSON.stringify(body), body_encoding: "utf8", cookie_configured: true, http_status: 200, final_url: request.url };
    }
  };
}
export async function readMetadata() {
  const text = await fs.readFile(path.join(repository, "examples/bilibili_toolkit/src/packages/bilibili.ts"), "utf8");
  return JSON.parse(text.match(/\/\* METADATA\s*([\s\S]*?)\*\//)[1]);
}
