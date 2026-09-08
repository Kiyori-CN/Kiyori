import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import http from "node:http";
import vm from "node:vm";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { once } from "node:events";
import { startAgentFixture } from "./windows_agent_fixture.mjs";

const require = createRequire(import.meta.url);
const root = fileURLToPath(new URL("../../", import.meta.url));
const agent = path.join(root, "examples/windows_control/resources/pc_agent/kiyori-pc-agent");
const { createFileService } = require(path.join(agent, "src/services/file-service.js"));
const { createProcessService } = require(path.join(agent, "src/services/process-service.js"));
const { createApiHandler } = require(path.join(agent, "src/handlers/api-handler.js"));
const { allowManagementRequest } = require(path.join(agent, "src/lib/request-policy.js"));
const { normalizeAgentUrl, validateConnectionConfig } = require(path.join(root, "examples/windows_control/dist/connection.js"));
const { createConfigStore } = require(path.join(agent, "src/stores/config-store.js"));
const { DEFAULT_CONFIG, PRESET_COMMANDS } = require(path.join(agent, "src/config/constants.js"));
const { getNetworkSnapshot, validateBindAddress } = require(path.join(agent, "src/services/network-service.js"));
const { createListenerService } = require(path.join(agent, "src/services/listener-service.js"));
const logger = { info() {}, warn() {}, error() {} };

function temporary(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "kiyori-windows-test-"));
  t.after(() => {
    assert.ok(path.dirname(directory) === os.tmpdir() && path.basename(directory).startsWith("kiyori-windows-test-"));
    fs.rmSync(directory, { recursive: true, force: true });
  });
  return directory;
}
function loadTools(httpCall, env = {}) {
  const context = vm.createContext({ exports: {}, require: createRequire(path.join(root, "examples/windows_control/dist/packages/windows_control.js")), console: logger,
    getEnv: key => ({ WINDOWS_AGENT_BASE_URL: "https://pc.example.com/bridge", WINDOWS_AGENT_TOKEN: "test-credential", ...env })[key],
    Tools: { Net: { http: httpCall } } });
  vm.runInContext(fs.readFileSync(path.join(root, "examples/windows_control/dist/packages/windows_control.js"), "utf8"), context);
  return context.exports;
}
const ok = data => ({ statusCode: 200, content: JSON.stringify(data), url: "https://pc.example.com/bridge" });
const agentVersion = JSON.parse(fs.readFileSync(path.join(agent, "package.json"), "utf8")).version;
const packageVersion = JSON.parse(fs.readFileSync(path.join(root, "examples/windows_control/manifest.json"), "utf8")).version;
const authenticated = () => ok({ ok: true, mode: "http-agent", version: agentVersion });

async function terminalHarness(t, overrides = {}) {
  const timeouts = new Map(), intervals = new Map(), events = new Map(), notices = [];
  let timerId = 0, input, onWrite, onReset;
  const node = () => ({ value: "", disabled: false, textContent: "", hidden: false, append() {}, replaceChildren() {}, addEventListener() {}, removeEventListener() {} });
  const refs = new Proxy({}, { get: (target, key) => target[key] ??= node() });
  refs.manageIncludeExitedInput.checked=true;
  const source = fs.readFileSync(path.join(agent, "public/scripts/features/processes-page.js"), "utf8").replace(/export function /g, "function ");
  const context = vm.createContext({ console: logger, document: { createElement: node },
    setTimeout: (fn, ms) => { const id = ++timerId; timeouts.set(id, {fn,ms}); return id; }, clearTimeout: id => timeouts.delete(id),
    setInterval: fn => { const id = ++timerId; intervals.set(id, fn); return id; }, clearInterval: id => intervals.delete(id),
    window: { addEventListener: (key, fn) => events.set(key, fn), removeEventListener() {},
      requestAnimationFrame: fn => { const id = ++timerId; timeouts.set(id, {fn,ms:0}); return id; }, cancelAnimationFrame: id => timeouts.delete(id) } });
  vm.runInContext(source + ";globalThis.createController=createProcessesController", context);
  const api = { listProcessSessions: async () => ({items:[{sessionId:"s1",status:"running",shell:"cmd"}]}),
    readProcessSession: async p => ({sessionId:p.session_id,status:"running",stdout:"",stderr:"",stdoutOffset:0,stderrOffset:0}),
    resizeProcessSession: async () => ({}), writeProcessSession: async () => ({}), terminateProcessSession: async () => ({removed:true}), ...overrides };
  const controller = context.createController({ api, refs, t: (key, params = {}) => `${key} ${JSON.stringify(params)}`,
    helpers: { setBusy() {}, setNotice: (tone, message) => notices.push({tone,message}), asErrorMessage: error => error.message },
    terminalLoader: async () => ({ Terminal: class { cols=120; rows=30; loadAddon() {} open() {} onData(fn) {input=fn;} reset() {onReset?.();}
      write(data, callback) { onWrite?.(data); callback?.(); } scrollToBottom() {} dispose() {} }, FitAddon: class { fit() {} } }) });
  const settle = () => new Promise(resolve => setImmediate(resolve));
  const tick = async ms => { for (const [id, task] of [...timeouts]) if (task.ms === ms) { timeouts.delete(id); task.fn(); } await settle(); };
  t.after(() => events.get("beforeunload")?.());
  await controller.refreshSessions(); await settle();
  return { controller, api, refs, notices, timeouts, tick, settle, input: data => input(data), replay: fn => {onWrite=fn;}, reset: fn => {onReset=fn;}, stop: () => events.get("beforeunload")() };
}

test("terminal missing session clears queued input once, without retries or refresh", async t => {
  let writes = 0;
  const h = await terminalHarness(t, {writeProcessSession: async () => {writes++; throw Object.assign(new Error("Session not found"), {code:"SESSION_NOT_FOUND"});}});
  h.input("echo one\r"); await h.tick(32);
  for (let i=0;i<5;i++) { h.input("never\r"); await h.tick(32); }
  assert.equal(writes,1); assert.equal(h.refs.currentSessionIdValue.textContent,"-");
  assert.equal(h.refs.closeSessionButton.disabled,true);
  assert.equal(h.notices.filter(n=>n.message.includes("sessionMissing")).length,1);
});

test("terminal unknown input outcome pauses, discards tail and only accepts new input after explicit refresh", async t => {
  const sent=[]; let rejectWrite;
  const h = await terminalHarness(t, {writeProcessSession: p => {sent.push(p.input); return new Promise((_,reject)=>{rejectWrite=reject;});}});
  h.input("first\r"); await h.tick(32); h.input("queued tail\r");
  rejectWrite(new Error("socket closed")); await h.settle(); await h.tick(32);
  h.input("ignored\r"); await h.tick(32);
  assert.deepEqual(sent,["first\r"]); assert.match(h.refs.terminalHint.textContent,/inputPaused/);
  h.api.writeProcessSession=async p=>{sent.push(p.input);return {};};
  await h.controller.refreshSessions(); h.input("new\r"); await h.tick(32);
  assert.deepEqual(sent,["first\r","new\r"]);
});

test("terminal can remove exited or already-missing sessions; late failed writes cannot revive them", async t => {
  let rejectWrite;
  const h=await terminalHarness(t,{writeProcessSession:()=>new Promise((_,reject)=>{rejectWrite=reject;})});
  h.input("inflight"); await h.tick(32);
  h.api.terminateProcessSession=async()=>{throw new Error("Session not found");};
  await h.controller.closeSelectedSession(); rejectWrite(new Error("late failure")); await h.settle();
  assert.equal(h.refs.currentSessionIdValue.textContent,"-"); assert.ok(!h.notices.some(n=>n.message.includes("late failure")));
  h.api.listProcessSessions=async()=>({items:[{sessionId:"exited",status:"exited"}]});
  h.api.readProcessSession=async()=>({sessionId:"exited",status:"exited"});
  h.api.terminateProcessSession=async()=>({removed:true});
  await h.controller.refreshSessions(); await h.controller.selectSession("exited"); await h.settle(); assert.equal(h.refs.closeSessionButton.disabled,false);
  await h.controller.closeSelectedSession(); assert.equal(h.refs.currentSessionIdValue.textContent,"-");
});

test("terminal missing reads stop polling; old list responses cannot resurrect a closed session", async t => {
  const h=await terminalHarness(t);
  let resolveList; h.api.listProcessSessions=()=>new Promise(resolve=>{resolveList=resolve;});
  const refreshing=h.controller.refreshSessions();
  h.api.readProcessSession=async()=>{throw Object.assign(new Error("gone"),{code:"SESSION_NOT_FOUND"});};
  await h.controller.selectSession("s1"); await h.settle();
  resolveList({items:[{sessionId:"s1",status:"running"}]}); await refreshing;
  assert.equal(h.refs.currentSessionIdValue.textContent,"-");
  assert.equal(h.notices.filter(n=>n.message.includes("sessionMissing")).length,1);
});

test("terminal snapshot protocol replies and unload callbacks never send input", async t => {
  let reads=0,writes=0;
  const h=await terminalHarness(t,{readProcessSession:async()=>({sessionId:"s1",status:"running",stdout:++reads===1?"history":""}),
    writeProcessSession:async()=>{writes++;return {};}});
  h.replay(()=>h.input("historical terminal response"));
  await h.controller.selectSession("s1"); await h.tick(32); assert.equal(writes,0);
  h.input("pending"); h.stop(); await h.tick(32); assert.equal(writes,0);
});

test("termination pending is distinct from closed and explicit refresh permits a new close attempt", async t => {
  let closes=0;
  const h=await terminalHarness(t,{terminateProcessSession:async()=>{closes++;return {wasRunning:true,signalSent:true,removed:false};}});
  await h.controller.closeSelectedSession();
  assert.equal(h.refs.closeSessionButton.disabled,true);
  assert.match(h.refs.terminalHint.textContent,/sessionClosing/);
  assert.ok(!h.notices.some(n=>n.message.includes("sessionClosed")));
  await h.controller.closeSelectedSession(); assert.equal(closes,1);
  await h.controller.refreshSessions(); assert.equal(h.refs.closeSessionButton.disabled,false);
  await h.controller.closeSelectedSession(); assert.equal(closes,2);
});

test("filtering exited sessions does not permanently forget them", async t => {
  const h=await terminalHarness(t,{listProcessSessions:async()=>({items:[{sessionId:"exited",status:"exited"}]}),readProcessSession:async()=>({sessionId:"exited",status:"exited"})});
  h.refs.manageIncludeExitedInput.checked=false; await h.controller.refreshSessions();
  assert.equal(h.refs.currentSessionIdValue.textContent,"-");
  h.refs.manageIncludeExitedInput.checked=true; await h.controller.refreshSessions();
  await h.controller.selectSession("exited"); assert.equal(h.refs.currentSessionIdValue.textContent,"exited");
});

test("PTY synchronous exit removes session and termination failure never reports success", () => {
  let exit, fail=false;
  const serviceRequire=createRequire(path.join(agent,"src/services/process-service.js"));
  const context=vm.createContext({module:{exports:{}},require:name=>name==="node-pty"?{spawn:()=>({pid:123,onData(){},onExit(fn){exit=fn;},kill(){if(fail)throw new Error("denied");exit({exitCode:0});}})}:
    name==="@xterm/headless"?{Terminal:class {dispose() {}}}:name==="child_process"?{spawn(){throw new Error("Unexpected subprocess in PTY unit test");}}:serviceRequire(name),process,console:logger,setTimeout,clearTimeout,Buffer});
  vm.runInContext(fs.readFileSync(path.join(agent,"src/services/process-service.js"),"utf8"),context);
  const service=context.module.exports.createProcessService({projectRoot:root,logger});
  const session=service.startSession("cmd","");
  assert.equal(service.terminateSession(session.sessionId,{remove:true}).removed,true);
  assert.throws(()=>service.terminateSession(session.sessionId),error=>error.code==="SESSION_NOT_FOUND");
  const second=service.startSession("cmd",""); fail=true;
  assert.throws(()=>service.terminateSession(second.sessionId,{remove:true}),error=>error.code==="SESSION_TERMINATE_FAILED");
  assert.equal(service.listSessions().items.length,1);
  fail=false; service.terminateAllSessions();
});

function setupScreen(callTool, options = {}) {
  const context = vm.createContext({ exports: {}, require: createRequire(path.join(root, "examples/windows_control/dist/ui/windows_setup/index.ui.js")), console: logger, getLang: () => "zh" });
  vm.runInContext(fs.readFileSync(path.join(root, "examples/windows_control/dist/ui/windows_setup/index.ui.js"), "utf8"), context);
  const states = new Map(), refs = new Map();
  const env = { WINDOWS_AGENT_BASE_URL: "https://pc.example.com", WINDOWS_AGENT_TOKEN: "fixture-token" };
  const ctx = { UI: new Proxy({}, { get: (_, type) => (props, children = []) => ({ type, props, children }) }),
    useState: (key, initial) => { if (!states.has(key)) states.set(key, initial); return [states.get(key), value => states.set(key, value)]; },
    useRef: (key, initial) => { if (!refs.has(key)) refs.set(key, { current: initial }); return refs.get(key); },
    getEnv: key => env[key], setEnvs: values => Object.assign(env, values), resolveToolName: () => "windows_test_connection",
    callTool, isPackageImported: () => true, importPackage: () => "ok", usePackage: () => "ok", ...options };
  const render = () => context.exports.default(ctx);
  const flatten = node => [node, ...node.children.flatMap(flatten)];
  return { states, render, click: label => flatten(render()).find(n => n.type === "Button" && n.props.text === label).props.onClick() };
}

test("patch compatibility is independent of package version and rejects unreviewed protocols", async () => {
  for (const version of ["1.1.0", "1.1.1", "1.1.2", "1.1.99"]) {
    const result = await loadTools(async () => ok({ ok: true, mode: "http-agent", version })).windows_test_connection();
    assert.equal(result.success, true, version);
    assert.equal(result.packageVersion, packageVersion);
  }
  for (const version of ["1.0.0", "1.2.0", "2.1.0", "1.1.02", "1.1.2-beta", "1.1.2+dev", "garbage"]) {
    const result = await loadTools(async () => ok({ ok: true, mode: "http-agent", version })).windows_test_connection();
    assert.equal(result.success, false, version);
    assert.match(result.error, /PROTOCOL_INCOMPATIBLE/);
  }
});

test("HTTP status diagnostics survive non-JSON proxy pages", async () => {
  for (const [statusCode, code] of [[401, "UNAUTHORIZED"], [404, "ENDPOINT_NOT_FOUND"], [502, "CONNECTION_HTTP_ERROR"]]) {
    const result = await loadTools(async () => ({ statusCode, content: "<html>proxy</html>" })).windows_test_connection();
    assert.equal(result.success, false); assert.match(result.error, new RegExp(code));
  }
  assert.equal((await loadTools(async () => ok(null)).windows_test_connection()).success, false);
});

test("phone renders actual host bridge errors, string errors and cross-realm Errors with redaction", async () => {
  const source = fs.readFileSync(path.join(root, "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsInitRuntimeScriptBuilder.kt"), "utf8");
  const parser = vm.runInNewContext(source.slice(source.indexOf("function createUserFacingError("), source.indexOf("function nextToolCallbackId(")) + ";parseToolResult", { asString: value => String(value ?? "") });
  for (const error of ["CONNECTION_FAILED fixture-token", new Error("UNAUTHORIZED fixture-token"), { message: "PROTOCOL_INCOMPATIBLE fixture-token" }, { data: { error: "details fixture-token" } }]) {
    const screen = setupScreen(async () => { throw error; });
    await screen.render().props.onLoad();
    const status = screen.states.get("connectionStatus");
    assert.equal(status.state, "failed"); assert.ok(!status.detail.includes("fixture-token"));
    assert.match(status.detail, /\[redacted\]/); assert.match(status.detail, /正在验证/);
  }
  const screen = setupScreen(async () => parser({ success: false, message: "PROTOCOL_INCOMPATIBLE fixture-token" }, false));
  await screen.render().props.onLoad();
  assert.match(screen.states.get("connectionStatus").detail, /PROTOCOL_INCOMPATIBLE \[redacted\]/);
  const activation = setupScreen(async () => ({}), { usePackage: () => { throw { message: "registry unavailable" }; } });
  await activation.click("保存并连接");
  assert.match(activation.states.get("connectionStatus").detail, /启用 Windows 工具\nregistry unavailable/);
});

test("compiled phone tools and setup connect to real bundled PC and perform file workflow", async t => {
  const fixture = await startAgentFixture(); t.after(fixture.stop);
  const tools = loadTools(async request => {
    const response = await fetch(request.url, { method: request.method, redirect: "manual", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request.body) });
    return { statusCode: response.status, content: await response.text(), url: request.url };
  }, { WINDOWS_AGENT_BASE_URL: fixture.executionUrl, WINDOWS_AGENT_TOKEN: "isolated-fixture-token" });
  const result = await tools.windows_test_connection();
  assert.equal(result.success, true, result.error); assert.equal(result.agentVersion, agentVersion); assert.equal(result.packageVersion, packageVersion);
  const screen = setupScreen(async () => JSON.stringify(await tools.windows_test_connection()));
  await screen.render().props.onLoad(); assert.equal(screen.states.get("connectionStatus").state, "success");
  for (const [name, params] of [["windows_mkdir", {path:"工作"}], ["write", {path:"工作/计划.txt",content:"你好\r\n"}],
    ["edit", {path:"工作/计划.txt",old_text:"你好",new_text:"Kiyori"}], ["windows_copy", {path:"工作/计划.txt",destination:"工作/copy.txt"}],
    ["windows_move", {path:"工作/copy.txt",destination:"工作/moved.txt"}], ["read",{path:"工作/moved.txt"}], ["windows_list",{path:"工作"}]]) {
    const response = await tools[name](params); assert.equal(response.success, true, `${name}: ${response.error}`);
  }
  assert.equal(fs.readFileSync(path.join(fixture.directory, "工作/moved.txt"), "utf8"), "Kiyori\r\n");
  assert.equal(fs.existsSync(path.join(fixture.directory, "工作/copy.txt")), false);
});

test("URL semantics preserve HTTPS, FRP prefix, explicit ports and IPv6", () => {
  for (const [input, expected] of [
    ["https://pc.example.com/", "https://pc.example.com"], ["HTTPS://pc.example.com/bridge/", "https://pc.example.com/bridge"],
    ["192.168.1.8", "http://192.168.1.8:58321"], ["http://pc.example.com", "http://pc.example.com"],
    ["pc.example.com:8080", "http://pc.example.com:8080"], ["http://[::1]:58321/bridge", "http://[::1]:58321/bridge"]
  ]) assert.equal(normalizeAgentUrl(input), expected);
  for (const invalid of ["", "ftp://host", "https://u:p@host", "https://host:0", "https://host:65536", "https://host:abc", "https://host?x=1", "https://host#x", "http://ho st", "https://host\\x", "https://host/../x"]) assert.throws(() => normalizeAgentUrl(invalid), undefined, invalid);
  assert.throws(() => validateConnectionConfig("https://host", "", "cmd", "30000"));
  assert.throws(() => validateConnectionConfig("https://host", "x", "bash", "30000"));
  assert.throws(() => validateConnectionConfig("https://host", "x", "cmd", "30000.5"));
});

test("connection probe authenticates without executing commands; no redirect/cookie/retry", async () => {
  const calls = [];
  const tools = loadTools(async request => { calls.push(request); return authenticated(); });
  assert.equal((await tools.windows_test_connection({ timeout_ms: 22000 })).success, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://pc.example.com/bridge/api/connection/test");
  assert.equal(calls[0].body.token, "test-credential");
  assert.equal(calls[0].read_timeout, 22);
  assert.equal(calls[0].follow_redirects, false);
  assert.equal(calls[0].use_cookies, false);
  assert.equal(calls[0].retry_on_connection_failure, false);
});

test("bad auth, redirects, old agents and malformed payloads never pass connection", async () => {
  for (const response of [ { statusCode: 401, content: '{}' }, { statusCode: 302, content: '{}' },
    { statusCode: 404, content: '{}' }, ok({ ok: true, version: "1.0.0", mode: "http-agent" }), ok({}), {statusCode:200,content:'<html>proxy</html>'} ]) {
    assert.equal((await loadTools(async () => response).windows_test_connection()).success, false);
  }
});

test("lost mutation response is not retried and reports unknown execution", async () => {
  const calls = [];
  const tools = loadTools(async request => {
    calls.push(request);
    if (request.url.endsWith("/test")) return authenticated();
    throw new Error("Socket closed");
  });
  const result = await tools.write({ path: "D:\\Work\\file.txt", content: "" });
  assert.equal(result.success, false);
  assert.match(result.error, /SUBMISSION_UNKNOWN/);
  assert.equal(calls.filter(c => c.url.endsWith("/write")).length, 1);
});

test("proxy 5xx and malformed mutation responses preserve unknown execution state", async () => {
  for (const response of [{statusCode:502,content:'bad gateway'}, {statusCode:200,content:'<html>proxy</html>'}, ok({})]) {
    const tools=loadTools(async request=>request.url.endsWith("/test") ? authenticated() : response);
    const result=await tools.write({path:"D:\\Work\\target.txt",content:"new content"});
    assert.equal(result.success,false);
    assert.match(result.error,/SUBMISSION_UNKNOWN/);
  }
});

test("file workflow preserves Unicode/CRLF, rejects conflicts and supports empty writes", t => {
  const directory = temporary(t), files = createFileService({ projectRoot: directory });
  files.makeDirectory("工作 目录");
  files.writeTextFile("工作 目录/计划.txt", "你好😀\r\nalpha alpha\r\n", "utf8");
  assert.throws(() => files.editTextFile("工作 目录/计划.txt", "alpha", "beta", 1), /mismatch/);
  files.editTextFile("工作 目录/计划.txt", "alpha", "beta", 2);
  assert.equal(files.readTextFile("工作 目录/计划.txt").content, "你好😀\r\nbeta beta\r\n");
  files.copyFile("工作 目录/计划.txt", "copy.txt");
  assert.throws(() => files.copyFile("copy.txt", "copy.txt"), /already exists/);
  files.movePath("copy.txt", "moved.txt");
  assert.equal(fs.existsSync(path.join(directory, "copy.txt")), false);
  assert.throws(() => files.movePath("moved.txt", "工作 目录/计划.txt"), /already exists/);
  assert.equal(files.statPath("moved.txt").type, "file");
  assert.equal(files.listDirectory(".", 2).items.length, 2);
  files.writeTextFile("empty.txt", "");
  assert.equal(files.readTextFile("empty.txt").sizeBytes, 0);
  assert.equal(files.makeDirectory("工作 目录").created, false);
  assert.throws(() => files.movePath("工作 目录", "工作 目录/child"), /itself/);
  assert.equal(fs.readdirSync(directory).filter(p => p.endsWith(".tmp")).length, 0);
});

test("file size/integer/character boundaries are enforced", t => {
  const directory = temporary(t), files = createFileService({ projectRoot: directory });
  files.writeTextFile("text.txt", "a你好😀z");
  let offset = 0, content = "";
  while (true) {
    const chunk = files.readTextSegment("text.txt", { offset, length: 4 });
    content += chunk.content; offset += chunk.length;
    if (chunk.eof) break;
  }
  assert.equal(content, "a你好😀z");
  assert.throws(() => files.readTextSegment("text.txt", { offset: 2, length: 4 }), /inside/);
  assert.throws(() => files.listDirectory(".", 1.5), /integer/);
  assert.throws(() => files.editTextFile("text.txt", "a", "b", 1.5), /integer/);
  fs.writeFileSync(path.join(directory, "large.txt"), Buffer.alloc(4 * 1024 * 1024 + 1, 65));
  assert.throws(() => files.readTextLines("large.txt"), /large/);
  assert.throws(() => files.editTextFile("large.txt", "A", "B", 1), /large/);
  assert.equal(files.readTextSegment("large.txt", { length: 16 }).length, 16);
});

test("configuration persists FRP choice and preserves malformed original", t => {
  const directory = temporary(t), configPath = path.join(directory, "config.json");
  const store = createConfigStore({ dataDir: directory, configPath, defaultConfig: DEFAULT_CONFIG, presetCommands: PRESET_COMMANDS });
  const config = store.loadConfig();
  store.saveConfig({ ...config, connectionMode: "frp", publicUrl: "https://pc.example.com/bridge" });
  assert.equal(store.loadConfig().publicUrl, "https://pc.example.com/bridge");
  fs.writeFileSync(configPath, "{broken");
  assert.throws(() => store.loadConfig(), /original file preserved/);
  assert.equal(fs.readFileSync(configPath, "utf8"), "{broken");
});

test("process output decodes split UTF8 and does not log command arguments", async t => {
  const events = [], directory = temporary(t);
  const service = createProcessService({ projectRoot: directory, logger: { ...logger, info: (event, data) => events.push({ event, data }) } });
  const result = await service.runProcess(process.execPath, ["-e", "const b=Buffer.from('你好😀');process.stdout.write(b.subarray(0,2));setTimeout(()=>process.stdout.write(b.subarray(2)),20)"], 5000);
  assert.equal(result.stdout, "你好😀");
  assert.equal(result.exitCode, 0);
  assert.equal(events.find(x => x.event === "runProcess.start").data.args, undefined);
  const large = await service.runProcess(process.execPath, ["-e", "process.stdout.write('x'.repeat(3*1024*1024))"], 5000);
  assert.ok(large.stdout.length <= 2 * 1024 * 1024);
  assert.match(large.stderr, /OUTPUT_TRUNCATED/);
});

test("HTTP and FRP-shaped proxy keep management private and execute file workflow", async t => {
  const directory = temporary(t), fileService = createFileService({ projectRoot: directory });
  const state = { config: { ...DEFAULT_CONFIG, apiToken: "fixture-token" } };
  const handler = createApiHandler({ state, fileService, logger, configStore: { saveConfig() {}, ensureApiToken: x => x },
    presetCommands: PRESET_COMMANDS, processService: { getNetworkSnapshot: () => ({}), getUserSnapshot: () => ({}) },
    runtimeInfo: { pid: () => 1, host: () => "fixture", uptimeSec: () => 1 }, versionInfo: { agentVersion: "1.1.0" } });
  const server = http.createServer((req, res) => handler.handleApiRequest(req, res, new URL(req.url, "http://localhost")));
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  const proxy = http.createServer((req, res) => {
    const forward = http.request({ hostname: "127.0.0.1", port: server.address().port, method: req.method, path: req.url.replace(/^\/bridge/, ""), headers: req.headers }, response => { res.writeHead(response.statusCode, response.headers); response.pipe(res); });
    forward.on("error", () => res.destroy()); req.pipe(forward);
  });
  proxy.listen(0, "127.0.0.1"); await once(proxy, "listening");
  t.after(() => { proxy.closeAllConnections(); proxy.close(); server.closeAllConnections(); server.close(); });
  const url = `http://127.0.0.1:${proxy.address().port}/bridge`;
  const post = (route, data) => fetch(url + route, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
  assert.equal((await fetch(url + "/api/config")).status, 403);
  assert.equal((await post("/api/config", { apiToken: "changed" })).status, 403);
  const health = await (await fetch(url + "/api/health")).json();
  assert.equal(health.user, undefined); assert.equal(health.network, undefined);
  assert.equal((await post("/api/connection/test", { token: "wrong" })).status, 401);
  assert.equal((await post("/api/connection/test", { token: state.config.apiToken })).status, 200);
  const auth = { token: state.config.apiToken };
  for (const [route, params] of [["write", {path:"first.txt",content:"你好"}], ["edit", {path:"first.txt",old_text:"你好",new_text:"Kiyori"}],
    ["copy", {path:"first.txt",destination:"second.txt"}], ["move", {path:"second.txt",destination:"third.txt"}]]) {
    const response = await post(`/api/file/${route}`, { ...auth, ...params });
    assert.equal(response.status, 200, JSON.stringify(await response.json()));
  }
  assert.equal((await (await post("/api/file/read", {...auth,path:"third.txt"})).json()).content, "Kiyori");
  assert.equal((await post("/api/file/write", {token:"wrong",path:"third.txt",content:"oops"})).status, 401);
});

test("management origin rejects remote Host, cross-site and non-JSON writes", () => {
  const req = { method: "GET", socket: { localPort: 45678 }, headers: {host:"127.0.0.1:45678"} };
  assert.equal(allowManagementRequest(req), true);
  assert.equal(allowManagementRequest({...req, headers:{host:"evil.example:45678"}}), false);
  assert.equal(allowManagementRequest({...req, headers:{...req.headers,origin:"https://evil.example"}}), false);
  assert.equal(allowManagementRequest({...req, method:"POST"}), false);
  assert.equal(allowManagementRequest({...req, method:"POST",headers:{...req.headers,"content-type":"application/json"}}), true);
});

test("real PC server starts separate listeners, isolates console and reports active port", async t => {
  const fixture = await startAgentFixture();
  t.after(fixture.stop);
  assert.equal((await fetch(fixture.managementUrl)).status, 200);
  assert.equal((await fetch(fixture.executionUrl)).status, 404);
  const config = await (await fetch(fixture.managementUrl + "/api/config")).json();
  assert.equal(config.connectionMode, "frp");
  assert.equal(config.publicUrl, "https://pc.example.com/bridge");
  assert.equal((await fetch(fixture.executionUrl + "/api/config")).status, 403);
  assert.equal((await fetch(fixture.managementUrl + "/api/config", {headers:{Origin:"https://evil.example"}})).status, 403);
  const reservation = http.createServer(); reservation.listen(0,"127.0.0.1"); await once(reservation,"listening");
  const nextPort = reservation.address().port; await new Promise(resolve=>reservation.close(resolve));
  const changed = await fetch(fixture.managementUrl + "/api/config", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({port:nextPort})});
  assert.equal(changed.status,200);
  assert.equal((await changed.json()).restartRequired,false);
  const health = await (await fetch(fixture.managementUrl + "/api/health")).json();
  assert.equal(health.port,nextPort);
  assert.equal(health.listener.listening,true);
  assert.equal((await (await fetch(`http://127.0.0.1:${nextPort}/api/health`)).json()).port,nextPort);
  const runtime = JSON.parse(fs.readFileSync(path.join(fixture.directory,"data/runtime.json"),"utf8"));
  assert.equal(runtime.managementUrl,fixture.managementUrl);
  assert.equal(runtime.port,nextPort);
});

test("Mihomo, fake-IP and virtual adapters never become automatic LAN recommendations", () => {
  const row = address => [{address,family:"IPv4",internal:false}];
  const network={Mihomo:row("198.18.0.1"),"vEthernet (Default Switch)":row("172.31.224.1"),WLAN:row("192.168.86.6"),vpn:row("10.0.0.2"),offline:row("169.254.1.2")};
  const snap=getNetworkSnapshot(network);
  assert.equal(snap.preferredLan,"192.168.86.6");
  assert.equal(snap.proxyInterfaceDetected,true);
  assert.equal(snap.rankedIpv4Candidates.find(x=>x.interfaceName==="Mihomo").isVirtual,true);
  assert.equal(getNetworkSnapshot({Mihomo:network.Mihomo,vpn:network.vpn}).recommendedHost,"");
  assert.equal(getNetworkSnapshot({renamed:row("198.19.0.1")}).recommendedHost,"");
  assert.throws(()=>validateBindAddress("192.168.32.1",network),/UNAVAILABLE/);
  validateBindAddress("192.168.86.6",network);
  validateBindAddress("198.18.0.1",network); // 明确手动选择不是自动推荐。
});

test("unavailable startup IP keeps management accessible and can be repaired without restart", async t => {
  const fixture=await startAgentFixture({bindAddress:"192.0.2.123"}); t.after(fixture.stop);
  const health=await (await fetch(fixture.managementUrl+"/api/health")).json();
  assert.equal(health.listener.listening,false); assert.equal(health.port,null);
  assert.match(health.startupIssue.error,/UNAVAILABLE/);
  const response=await fetch(fixture.managementUrl+"/api/config",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({bindAddress:"127.0.0.1"})});
  assert.equal(response.status,200);
  const after=await (await fetch(fixture.managementUrl+"/api/health")).json();
  assert.equal(after.pid,health.pid); assert.equal(after.listener.listening,true); assert.equal(after.startupIssue,null);
  assert.equal((await fetch(fixture.executionUrl+"/api/health")).status,200);
});

test("listener switches same-port wildcard binding and preserves old config on occupied port", async t => {
  const fixture=await startAgentFixture(); t.after(fixture.stop);
  const post=body=>fetch(fixture.managementUrl+"/api/config",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
  assert.equal((await post({bindAddress:"0.0.0.0"})).status,200);
  assert.equal((await post({bindAddress:"127.0.0.1"})).status,200);
  const occupied=Number(new URL(fixture.managementUrl).port);
  const failed=await post({port:occupied,apiToken:"must-not-persist"});
  assert.equal(failed.status,400); assert.match((await failed.json()).error,/EADDRINUSE.*preserved/);
  const config=await (await fetch(fixture.managementUrl+"/api/config")).json();
  assert.equal(config.port,fixture.port); assert.equal(config.apiToken,"isolated-fixture-token");
  assert.equal((await fetch(fixture.executionUrl+"/api/health")).status,200);
  assert.equal((await post({bindAddress:"192.0.2.123"})).status,400);
  assert.equal((await post({bindAddress:""})).status,400);
});

test("persistence failure restores listener and concurrent configuration updates are rejected", async t => {
  const service=createListenerService({handler:(_req,res)=>res.end("ok")}); t.after(()=>service.close());
  const reservation=http.createServer(); reservation.listen(0,"127.0.0.1"); await once(reservation,"listening");
  const port=reservation.address().port; await new Promise(resolve=>reservation.close(resolve));
  await service.apply({bindAddress:"127.0.0.1",port});
  await assert.rejects(service.apply({bindAddress:"0.0.0.0",port},()=>{throw new Error("disk-full");}),/disk-full.*preserved/);
  assert.equal(service.snapshot().bindAddress,"127.0.0.1");
  assert.equal(await (await fetch(`http://127.0.0.1:${port}`)).text(),"ok");
  const changing=service.apply({bindAddress:"0.0.0.0",port});
  await assert.rejects(service.apply({bindAddress:"127.0.0.1",port}),/BUSY/);
  await changing;
  const stopping=service.apply({bindAddress:"127.0.0.1",port});
  service.close();
  await assert.rejects(stopping,/STOPPING/);
  assert.equal(service.snapshot().listening,false);
});

test("mobile malformed connection is failed; edits invalidate status and saves are batched", async () => {
  const context = vm.createContext({ exports:{}, require:createRequire(path.join(root,"examples/windows_control/dist/ui/windows_setup/index.ui.js")), console:logger, getLang:()=>"zh" });
  vm.runInContext(fs.readFileSync(path.join(root,"examples/windows_control/dist/ui/windows_setup/index.ui.js"),"utf8"),context);
  const states=new Map(), refs=new Map(), writes=[];
  const env={WINDOWS_AGENT_BASE_URL:"https://pc.example.com",WINDOWS_AGENT_TOKEN:"fixture-token"};
  let result={};
  const ctx={ UI:new Proxy({}, {get:(_,type)=>(props,children=[])=>({type,props,children})}),
    useState:(key,initial)=>{if(!states.has(key))states.set(key,initial);return [states.get(key),value=>states.set(key,value)];},
    useRef:(key,initial)=>{if(!refs.has(key))refs.set(key,{current:initial});return refs.get(key);},
    getEnv:key=>env[key],setEnvs:values=>{writes.push(values);Object.assign(env,values);},resolveToolName:()=>"imported:windows_test_connection",
    callTool:async()=>result,isPackageImported:()=>true,importPackage:()=>"ok",usePackage:()=>"ok"};
  const render=()=>context.exports.default(ctx);
  await render().props.onLoad();
  assert.equal(states.get("connectionStatus").state,"failed");
  const flatten=node=>[node,...node.children.flatMap(flatten)];
  const nodes=flatten(render());
  nodes.find(n=>n.type==="TextField" && n.props.label==="连接地址").props.onValueChange("https://new.example.com");
  assert.equal(states.get("connectionStatus").state,"idle");
  result={success:true,agentVersion:"1.1.0",durationMs:5};
  await flatten(render()).find(n=>n.type==="Button" && n.props.text==="保存并连接").props.onClick();
  assert.equal(writes.length,1);
  assert.equal(writes[0].WINDOWS_AGENT_BASE_URL,"https://new.example.com");
  assert.equal(states.get("connectionStatus").state,"success");
});

test("desktop LAN/FRP selection preserves target URL and masks configuration preview", async () => {
  const source=fs.readFileSync(path.join(agent,"public/scripts/features/wizard-page.js"),"utf8");
  const {createWizardController}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
  const values=new Map();
  const refs=new Proxy({}, {get:(_,key)=>{
    if(!values.has(key)) values.set(key,{value:"",textContent:"",hidden:false,classList:{toggle(){}}});
    return values.get(key);
  }});
  const state={connectionMode:"lan",config:{bindAddress:"127.0.0.1",port:58321,apiToken:"private-fixture-token",maxCommandMs:30000},
    health:{network:{preferredLan:"192.168.1.8"},runtimeBindAddress:"127.0.0.1",port:58321}};
  const saved=[];
  const controller=createWizardController({refs,state,t:key=>key,api:{updateConfig:async payload=>{saved.push(payload);return {restartRequired:false};}},
    helpers:{setBusy(){},setNotice(){},setJsonOutput(){},asErrorMessage:error=>error.message}});
  controller.fillWizardStep1Form(state.config);
  controller.syncFromState();
  assert.equal(refs.publicUrlField.hidden,true);
  assert.equal(refs.mobileBaseUrlInput.value,"http://192.168.1.8:58321");
  refs.connectionModeInput.value="frp";
  controller.handleConnectionMode();
  assert.equal(refs.publicUrlField.hidden,false);
  assert.equal(refs.wizardBindAddressInput.value,"127.0.0.1");
  assert.equal(refs.mobileBaseUrlInput.value,"");
  refs.publicUrlInput.value="https://pc.example.com/bridge";
  controller.handleMobileSnippetInput();
  assert.equal(refs.mobileBaseUrlInput.value,"https://pc.example.com/bridge");
  assert.ok(!refs.wizardMobileJsonOutput.textContent.includes("private-fixture-token"));
  await controller.handleWizardStep1SaveNext();
  assert.equal(saved[0].connectionMode,"frp");
  assert.equal(saved[0].publicUrl,"https://pc.example.com/bridge");
});

test("every declared Windows tool has an exported implementation", () => {
  const source=fs.readFileSync(path.join(root,"examples/windows_control/src/packages/windows_control.ts"),"utf8");
  const metadata=JSON.parse(source.match(/\/\* METADATA\s*([\s\S]*?)\*\//)[1]);
  const tools=loadTools(authenticated);
  for (const tool of metadata.tools.filter(t=>!t.advice)) assert.equal(typeof tools[tool.name],"function",tool.name);
});

test("desktop save applies before advancing, clears stale pending state and copies actual bound adapter", async t => {
  const source=fs.readFileSync(path.join(agent,"public/scripts/features/wizard-page.js"),"utf8");
  const {createWizardController}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
  const values=new Map(),notices=[],copies=[];
  const refs=new Proxy({}, {get:(_,key)=>{if(!values.has(key))values.set(key,{value:"",textContent:"",hidden:false,classList:{toggle(){}}});return values.get(key);}});
  const state={wizardStep:0,connectionMode:"lan",pendingRestart:true,
    config:{bindAddress:"192.168.32.1",port:58321,apiToken:"fixture-token",maxCommandMs:30000,publicUrl:"",connectionMode:"lan"},
    health:{runtimeBindAddress:"",port:null,listener:{listening:false,error:"BIND_ADDRESS_UNAVAILABLE"},network:{preferredLan:"192.168.86.6",ipv4Candidates:["192.168.86.6","192.168.99.2"]}}};
  let fail=false,latestToken="fixture-token";
  const controller=createWizardController({refs,state,t:key=>key,
    api:{updateConfig:async payload=>{if(fail)throw new Error("EADDRINUSE");Object.assign(state.config,payload);state.health={...state.health,runtimeBindAddress:payload.bindAddress,port:payload.port,listener:{listening:true}};return {restartRequired:false};},
      getConfig:async()=>({...state.config,apiToken:latestToken}),getHealth:async()=>state.health},
    helpers:{setBusy(){},setNotice:(tone,text)=>notices.push({tone,text}),setJsonOutput(){},asErrorMessage:error=>error.message}});
  const descriptor=Object.getOwnPropertyDescriptor(globalThis,"navigator");
  Object.defineProperty(globalThis,"navigator",{configurable:true,value:{clipboard:{writeText:async text=>copies.push(JSON.parse(text))}}});
  t.after(()=>{if(descriptor)Object.defineProperty(globalThis,"navigator",descriptor);else delete globalThis.navigator;});
  controller.fillWizardStep1Form(state.config); controller.syncFromState();
  assert.equal(refs.wizardBindAddressInput.value,"192.168.86.6");
  refs.wizardBindAddressInput.value="192.168.99.2"; refs.wizardPortInput.value="58322";
  await controller.handleWizardStep1SaveNext();
  assert.equal(state.pendingRestart,false); assert.equal(state.wizardStep,1);
  assert.equal(refs.mobileBaseUrlInput.value,"http://192.168.99.2:58322");
  await controller.handleWizardCopyPayload();
  assert.equal(copies.length,1); assert.equal(copies[0].WINDOWS_AGENT_BASE_URL,"http://192.168.99.2:58322");
  latestToken="rotated-elsewhere"; await controller.handleWizardCopyPayload();
  assert.equal(copies.length,1); assert.equal(notices.at(-1).tone,"error");
  fail=true; controller.setWizardStep(0); await controller.handleWizardStep1SaveNext();
  assert.equal(state.wizardStep,0);
});
