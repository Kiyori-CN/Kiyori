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
const authenticated = () => ok({ ok: true, mode: "http-agent", version: "1.1.0" });

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
  const changed = await fetch(fixture.managementUrl + "/api/config", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({port:fixture.port === 65535 ? 65534 : fixture.port + 1})});
  assert.equal((await changed.json()).restartRequired,true);
  const health = await (await fetch(fixture.managementUrl + "/api/health")).json();
  assert.equal(health.port,fixture.port);
  const runtime = JSON.parse(fs.readFileSync(path.join(fixture.directory,"data/runtime.json"),"utf8"));
  assert.equal(runtime.managementUrl,fixture.managementUrl);
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
