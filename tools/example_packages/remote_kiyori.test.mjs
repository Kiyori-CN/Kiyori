import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

// This mock covers the script/Java bridge boundary, not Android's URI implementation.
class URI {
    constructor(raw) {
        this.url = new URL(raw);
        this.authority = raw.match(/^[^:]+:\/\/([^/?#]*)/)?.[1] ?? "";
        this.port = this.authority.match(/:(\d+)$/)?.[1];
    }
    getScheme() { return this.url.protocol.slice(0, -1); }
    getHost() { return this.url.hostname; }
    getPort() { return this.port === undefined ? -1 : Number(this.port); }
    getRawAuthority() { return this.authority; }
    getRawUserInfo() { return this.authority.includes("@") ? this.url.username : null; }
    getRawQuery() { return this.url.search ? this.url.search.slice(1) : null; }
    getRawFragment() { return this.url.hash ? this.url.hash.slice(1) : null; }
    getRawPath() { return this.url.pathname; }
}

async function loadRemote({ env = {}, statusCode = 200, content, failWrite, httpError } = {}) {
    const writes = [];
    const requests = [];
    const logs = [];
    const variables = { REMOTE_KIYORI_BASE_URL: "192.168.1.2", REMOTE_KIYORI_TOKEN: "test-token",
        REMOTE_KIYORI_TIMEOUT_MS: "60000", ...env };
    const context = vm.createContext({
        exports: {}, Error,
        getEnv: key => variables[key],
        Java: { type: name => { assert.equal(name, "java.net.URI"); return URI; } },
        console: { error: (...args) => logs.push(args) },
        Tools: {
            SoftwareSettings: { writeEnvironmentVariable: async (key, value) => {
                if (key === failWrite) throw new Error("write rejected");
                writes.push({ key, value }); variables[key] = value;
            } },
            Net: { http: async request => {
                requests.push(JSON.parse(JSON.stringify(request)));
                if (httpError) throw httpError;
                return { statusCode, content: content ?? JSON.stringify({ status: "ok", enabled: true,
                    service_running: true, version_name: "0.1.0", port: 8094 }), url: request.url };
            } },
        },
    });
    vm.runInContext(await readFile(new URL("../../examples/remote_kiyori/dist/packages/remote_kiyori.js", import.meta.url), "utf8"), context);
    return { tools: context.exports, writes, requests, logs };
}

for (const [base_url, expected] of [
    ["192.168.1.2", "http://192.168.1.2:8094"],
    ["HTTPS://example.test:443/kiyori/", "https://example.test:443/kiyori"],
    ["http://[::1]:8094/", "http://[::1]:8094"],
]) {
    test(`valid remote endpoint is normalized and persisted: ${base_url}`, async () => {
        const { tools, writes } = await loadRemote();
        const result = await tools.remote_kiyori_configure({ base_url, token: "new-token", timeout_ms: 45000 });
        assert.equal(result.success, true, JSON.stringify(result));
        assert.equal(result.packageVersion, "1.0.0");
        assert.equal(result.remoteBaseUrl, expected);
        assert.deepEqual(writes.map(write => write.key), ["REMOTE_KIYORI_BASE_URL", "REMOTE_KIYORI_TOKEN", "REMOTE_KIYORI_TIMEOUT_MS"]);
        assert.equal(JSON.stringify(result).includes("new-token"), false);
    });
}

for (const invalid of [
    { token: " " }, { timeout_ms: 0 }, { timeout_ms: 1.5 }, { timeout_ms: "bad" }, { timeout_ms: null },
    { test_connection: "yes" }, { base_url: "ftp://host" }, { base_url: "http://host:70000" },
    { base_url: "http://host:" }, { base_url: "http://host:0" }, { base_url: "http://host?token=x" },
    { base_url: "http://host#fragment" }, { base_url: "http://user:secret@host" },
]) {
    test(`invalid configuration cannot change existing settings: ${JSON.stringify(invalid)}`, async () => {
        const { tools, writes, requests } = await loadRemote();
        const result = await tools.remote_kiyori_configure({ base_url: "192.168.1.3", token: "new-token", ...invalid });
        assert.equal(result.success, false);
        assert.equal(result.configured, false);
        assert.deepEqual(writes, []);
        assert.deepEqual(requests, []);
    });
}

test("partial persistence is reported without retrying writes", async () => {
    const { tools, writes } = await loadRemote({ failWrite: "REMOTE_KIYORI_TOKEN" });
    const result = await tools.remote_kiyori_configure({ base_url: "192.168.1.3", token: "new-token" });
    assert.equal(result.success, false);
    assert.equal(result.configured, false);
    assert.equal(writes.length, 1);
    assert.deepEqual(Array.from(result.persistedKeys), ["REMOTE_KIYORI_BASE_URL"]);
});

test("a health failure after saving still reports that configuration was saved", async () => {
    const { tools } = await loadRemote({ httpError: new Error("unreachable") });
    const result = await tools.remote_kiyori_configure({ base_url: "192.168.1.3", token: "new-token", test_connection: true });
    assert.equal(result.success, false);
    assert.equal(result.configured, true);
});

for (const options of [{ statusCode: 302 }, { content: "" }, { content: "null" }, { content: "[]" }, { content: "{}" }]) {
    test(`health rejects invalid or incomplete responses: ${JSON.stringify(options)}`, async () => {
        const { tools } = await loadRemote(options);
        const result = await tools.remote_kiyori_test_connection();
        assert.equal(result.success, false);
    });
}

test("chat forwards execution timeout and explicit false flags exactly once", async () => {
    const { tools, requests } = await loadRemote({ content: JSON.stringify({ success: true, request_id: "r1", chat_id: "c1", ai_response: "answer" }) });
    const result = await tools.remote_kiyori_chat({ message: "hello", request_id: "r1", timeout_ms: 42000, stop_after: false, create_new_chat: false });
    assert.equal(result.success, true);
    assert.equal(result.aiResponse, "answer");
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, "http://192.168.1.2:8094/api/external-chat");
    assert.deepEqual(requests[0].body, { message: "hello", response_mode: "sync", request_id: "r1", stop_after: false, create_new_chat: false, timeout_ms: 42000 });
    assert.equal(requests[0].read_timeout, 47);
});

for (const options of [{ statusCode: 500, content: '{"success":true}' }, { content: '{}' }]) {
    test(`chat cannot turn invalid HTTP/protocol results into success: ${JSON.stringify(options)}`, async () => {
        const { tools, requests } = await loadRemote(options);
        assert.equal((await tools.remote_kiyori_chat({ message: "hello" })).success, false);
        assert.equal(requests.length, 1);
    });
}

test("invalid stored timeout and empty chat are rejected before HTTP", async () => {
    for (const options of [{ env: { REMOTE_KIYORI_TIMEOUT_MS: "bad" } }, {}]) {
        const { tools, requests } = await loadRemote(options);
        assert.equal((await tools.remote_kiyori_chat({ message: " " })).success, false);
        assert.deepEqual(requests, []);
    }
});

async function loadSetup(callTool, timeout = "45000") {
    const states = new Map();
    const nodes = [];
    const logs = [];
    const context = vm.createContext({
        exports: {}, Error, getLang: () => "en-US",
        Icons: new Proxy({}, { get: (_, key) => key }),
        console: { error: (...args) => logs.push(args) },
        require: name => {
            assert.equal(name, "../../i18n");
            return { resolveRemoteKiyoriSetupI18n: () => new Proxy({}, { get: (_, key) => key }) };
        },
    });
    vm.runInContext(await readFile(new URL("../../examples/remote_kiyori/dist/ui/remote_kiyori_setup/index.ui.js", import.meta.url), "utf8"), context);
    context.exports.default({
        getEnv: key => ({ REMOTE_KIYORI_BASE_URL: "192.168.1.2", REMOTE_KIYORI_TOKEN: "test-token", REMOTE_KIYORI_TIMEOUT_MS: timeout })[key],
        useState: (key, initial) => { states.set(key, initial); return [initial, value => states.set(key, value)]; },
        isPackageImported: async () => true,
        usePackage: async () => "ready",
        resolveToolName: async ({ packageName, toolName }) => {
            assert.equal(packageName, "remote_kiyori");
            return `imported_remote:${toolName}`;
        },
        callTool,
        UI: new Proxy({}, { get: (_, type) => (props, children) => {
            const node = { type, props, children }; nodes.push(node); return node;
        } }),
    });
    return { states, logs, save: nodes.find(node => node.type === "Button" && node.props.text === "applyButton").props.onClick };
}

test("setup resolves a tool once and sends numeric timeout", async () => {
    const calls = [];
    const { save, states } = await loadSetup(async (name, params) => {
        calls.push({ name, params });
        return { success: true, remoteBaseUrl: "http://192.168.1.2:8094", timeoutMs: 45000 };
    });
    await save();
    assert.equal(calls.length, 1);
    assert.equal(calls[0].name, "imported_remote:remote_kiyori_configure");
    assert.equal(calls[0].params.timeout_ms, 45000);
    assert.equal(states.get("successMessage"), "statusSaved");
});

test("setup does not repeat a failed write with other tool names", async () => {
    const calls = [];
    const { save, states, logs } = await loadSetup(async name => {
        calls.push(name);
        throw { message: "write rejected" };
    });
    await save();
    assert.equal(calls.length, 1);
    assert.equal(states.get("successMessage"), "");
    assert.match(states.get("errorMessage"), /write rejected/);
    assert.equal(logs.length, 1);
});

test("setup cannot report an empty response as a successful save", async () => {
    const { save, states } = await loadSetup(async () => ({}));
    await save();
    assert.equal(states.get("successMessage"), "");
    assert.notEqual(states.get("errorMessage"), "");
});

test("setup rejects an invalid numeric field before serialization or host calls", async () => {
    let calls = 0;
    const { save, states } = await loadSetup(async () => { calls++; }, "invalid");
    await save();
    assert.equal(calls, 0);
    assert.match(states.get("errorMessage"), /Invalid timeout_ms/);
});
