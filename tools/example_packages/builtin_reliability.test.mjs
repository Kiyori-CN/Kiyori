import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

// 在没有 Android、账号或真实网络的情况下验证脚本边界；模拟结果不代表现场功能验收。
async function load(name, globals = {}) {
    const completions = [];
    const logs = [];
    const context = vm.createContext({
        exports: {},
        console: Object.fromEntries(['log', 'warn', 'error'].map(level => [level, (...args) => logs.push(args)])),
        complete: value => completions.push(value),
        getLang: () => 'zh',
        getCallerName: () => '测试角色',
        ...globals,
    });
    const source = await readFile(new URL(`../../examples/${name}.ts`, import.meta.url), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText;
    vm.runInContext(compiled, context, { filename: `${name}.js`, timeout: 1000 });
    return {
        logs,
        async invoke(tool, params = {}) {
            completions.length = 0;
            const returned = await context.exports[tool](params);
            assert.ok(completions.length === 1 || (completions.length === 0 && returned !== undefined), `${tool} must produce one terminal result`);
            return JSON.parse(JSON.stringify(completions.length ? completions[0] : returned));
        },
    };
}

test('Cookie operations consume the native string result rather than an HTTP status code', async () => {
    for (const action of ['get', 'set', 'clear']) {
        const calls = [];
        const script = await load('extended_http_tools', { toolCall: async call => { calls.push(call); return 'Cookie operation completed'; } });
        const result = await script.invoke('manage_cookies', { action, domain: 'example.test' });
        assert.equal(result.success, true);
        assert.equal(result.data, 'Cookie operation completed');
        assert.equal(calls[0].name, 'manage_cookies');
        assert.equal(calls.length, 1);
    }
});

test('missing file is a successful existence query, preserving the negative answer', async () => {
    const script = await load('extended_file_tools', { Tools: { Files: { exists: async () => ({ exists: false, path: '/missing', env: 'android' }) } } });
    const result = await script.invoke('file_exists', { path: '/missing' });
    assert.equal(result.success, true);
    assert.equal(result.data.exists, false);
});

test('daily tools preserve business failures and always finish invalid alarm input', async () => {
    const script = await load('daily_life');
    for (const params of [{}, { hour: NaN, minute: 1 }, { hour: 1, minute: Infinity }, { hour: 1.5, minute: 1 }]) {
        const result = await script.invoke('set_alarm', params);
        assert.equal(result.success, false);
        assert.ok(result.message);
    }
    assert.equal((await script.invoke('set_reminder', { title: '' })).success, false);
});

test('calendar edit is reported as requiring confirmation and private content is not logged', async () => {
    class Intent {
        setData() {} putExtra() {} addFlag() {}
        async start() { return { launched: true }; }
    }
    const script = await load('daily_life', { Intent, IntentAction: { ACTION_INSERT: 'insert' }, IntentFlag: { ACTIVITY_NEW_TASK: 1 } });
    const result = await script.invoke('set_reminder', { title: 'PRIVATE_EVENT_SENTINEL', description: 'PRIVATE_BODY_SENTINEL' });
    assert.equal(result.success, true);
    assert.equal(result.requires_user_confirmation, true);
    assert.doesNotMatch(JSON.stringify(script.logs), /PRIVATE_(EVENT|BODY)_SENTINEL/);
    assert.equal((await script.invoke('set_reminder', { title: 'event', due_date: 'not-a-date' })).success, false);
});

function chatHost(sendMessage = async () => ({ message: 'done' })) {
    return {
        listCharacterCards: async () => ({ cards: [{ name: '助手', id: 'card-1' }] }),
        startService: async () => ({}),
        findChat: async () => ({ chat: { characterCardName: '助手' } }),
        sendMessage,
    };
}

test('agent messaging uses only the host deadline and does not allocate a competing timer', async () => {
    const calls = [];
    const script = await load('extended_chat', {
        Tools: { Chat: chatHost(async (...args) => { calls.push(args); return { response: 'reply' }; }) },
        setTimeout: () => assert.fail('script must not own a second send deadline'),
    });
    const result = await script.invoke('chat_with_agent', { message: 'hello', character_card_name: '助手', chat_id: 'chat-1', timeout: 0.5 });
    assert.equal(result.success, true);
    assert.equal(calls.length, 1);
    assert.equal(calls[0][4].timeout_ms, 500);
});

test('agent messaging validates invalid deadlines before any host side effect', async () => {
    const script = await load('extended_chat', { Tools: { Chat: new Proxy({}, { get: () => () => assert.fail('unexpected host call') }) } });
    for (const timeout of [0, -1, NaN, Infinity, 0.0001]) {
        const result = await script.invoke('chat_with_agent', { message: 'hello', character_card_name: '助手', timeout });
        assert.equal(result.success, false);
    }
});

test('failed agent send retains target identity and never claims success or retries', async () => {
    let sends = 0;
    const script = await load('extended_chat', { Tools: { Chat: chatHost(async () => { sends++; throw new Error('private transport details'); }) } });
    const result = await script.invoke('chat_with_agent', { message: 'hello', character_card_name: '助手', chat_id: 'chat-1' });
    assert.equal(result.success, false);
    assert.equal(result.data.chat_id, 'chat-1');
    assert.equal(result.data.submission_state, 'unknown');
    assert.equal(sends, 1);
    assert.doesNotMatch(JSON.stringify(script.logs), /private transport details/);
});

test('failed chat service startup prevents creation or sending', async () => {
    const host = chatHost(() => assert.fail('must not send'));
    host.startService = async () => { throw new Error('service unavailable'); };
    const script = await load('extended_chat', { Tools: { Chat: host } });
    assert.equal((await script.invoke('chat_with_agent', { message: 'hello', character_card_name: '助手' })).success, false);
});

function httpHost(responses = []) {
    const requests = [];
    const client = {
        newRequest() {
            const request = {};
            const builder = Object.fromEntries(['url', 'method', 'headers', 'body'].map(key => [key, value => { request[key] = value; return builder; }]));
            builder.build = () => ({ execute: async () => {
                requests.push(request);
                assert.ok(responses.length, 'unexpected HTTP request');
                const response = responses.shift();
                if (response instanceof Error) throw response;
                const content = typeof response === 'string' ? response : JSON.stringify(response);
                return { content, headers: {}, isSuccessful: () => true, statusCode: 200, text: () => content, json: () => JSON.parse(content) };
            } });
            return builder;
        },
    };
    const builder = Object.fromEntries(['connectTimeout', 'readTimeout', 'writeTimeout', 'followRedirects', 'retryOnConnectionFailure'].map(key => [key, () => builder]));
    builder.build = () => client;
    return {
        requests,
        globals: {
            OkHttp: { newClient: () => client, newBuilder: () => builder },
            getPluginConfigDir: () => '/test-output',
            getEnv: name => name.includes('KEY') ? 'test-placeholder' : undefined,
            Tools: {
                Files: { mkdir: async () => ({ successful: true }) },
                System: { sleep: async () => assert.fail('terminal failure must not poll again') },
            },
        },
    };
}

test('all seven drawing providers stop before submission when output storage fails', async () => {
    for (const provider of ['openai', 'qwen', 'nanobanana', 'xai', 'siliconflow', 'minimax', 'zhipu']) {
        const host = httpHost();
        host.globals.Tools.Files.mkdir = async () => ({ successful: false });
        const script = await load(`${provider}_draw`, host.globals);
        const result = await script.invoke('draw_image', { prompt: 'test image' });
        assert.equal(result.success, false, provider);
        assert.equal(host.requests.length, 0, provider);
        assert.match(result.message, /输出目录/, provider);
    }
});

test('drawing polling options are validated before task creation', async () => {
    for (const [provider, tool] of [['qwen', 'draw_image'], ['nanobanana', 'draw_image'], ['xai', 'draw_video'], ['siliconflow', 'draw_video']]) {
        const host = httpHost();
        host.globals.Tools.Files.mkdir = async () => assert.fail('invalid polling input must not touch files');
        const script = await load(`${provider}_draw`, host.globals);
        for (const value of [-1, Infinity, 0.5, '12invalid']) {
            assert.equal((await script.invoke(tool, { prompt: 'test', poll_interval_ms: value })).success, false, provider);
        }
        assert.equal(host.requests.length, 0, provider);
    }
});

test('Nano Banana terminal failure returns promptly with the submitted task identity', async () => {
    for (const terminal of [{ code: 0, data: { status: 'failed', progress: 0 } }, { code: 401 }, 'not-json']) {
        const host = httpHost([{ code: 0, data: { id: 'existing-task-17' } }, terminal]);
        const script = await load('nanobanana_draw', host.globals);
        const result = await script.invoke('draw_image', { prompt: 'test' });
        assert.equal(result.success, false);
        assert.match(result.message, /existing-task-17/);
        assert.equal(host.requests.length, 2, 'only one submission and one failed status lookup');
    }
});

const stations = "var station_names ='@bjb|北京北|VAP|beijingbei|bjb|0|0|北京||';";

test('12306 station lookup does not depend on transfer initialization', async () => {
    const host = httpHost([stations]);
    const script = await load('12306', host.globals);
    const result = await script.invoke('get_stations_code_in_city', { city: '北京' });
    assert.equal(result.success, true);
    assert.equal(result.data[0].station_code, 'VAP');
    assert.equal(host.requests.length, 1);
});

test('12306 same-station transfer request is rejected before any network call', async () => {
    const host = httpHost([]);
    const script = await load('12306', host.globals);
    const result = await script.invoke('get_interline_tickets', { date: '2099-01-01', from_station: 'VAP', to_station: 'VAP' });
    assert.equal(result.success, false);
    assert.equal(result.error.code, 'INVALID_ARGUMENT');
    assert.equal(host.requests.length, 0);
});

test('HTTP upload and request bodies share the bounded response-file contract', async () => {
    for (const tool of ['http_request', 'multipart_request']) {
        const writes = [];
        const script = await load('extended_http_tools', {
            KIYORI_CLEAN_ON_EXIT_DIR: '/responses',
            toolCall: async () => ({ statusCode: 200, content: 'x'.repeat(15000), contentType: 'text/plain' }),
            Tools: { Files: { mkdir: async () => ({ successful: true }), write: async (...args) => { writes.push(args); return { successful: true }; } } },
        });
        const result = await script.invoke(tool, { url: 'https://example.test', method: 'POST' });
        assert.equal(result.success, true);
        assert.equal(writes.length, 1);
        assert.equal(writes[0][1].length, 15000);
        assert.equal(result.data.content_saved_to, writes[0][0]);
        assert.ok(JSON.stringify(result).length < 2000);
    }
});

test('response persistence failure retains the completed HTTP identity and bounded preview', async () => {
    let calls = 0;
    const script = await load('extended_http_tools', {
        KIYORI_CLEAN_ON_EXIT_DIR: '/responses',
        toolCall: async () => { calls++; return { statusCode: 201, content: 'x'.repeat(15000), contentType: 'text/plain' }; },
        Tools: { Files: { mkdir: async () => ({ successful: false }), write: async () => assert.fail('must not write') } },
    });
    const result = await script.invoke('http_request', { url: 'https://example.test', method: 'POST' });
    assert.equal(result.success, false);
    assert.equal(result.data.response_received, true);
    assert.equal(result.data.statusCode, 201);
    assert.equal(result.data.content.length, 12000);
    assert.equal(result.data.content_truncated, true);
    assert.equal(calls, 1);
});

test('simple wrappers complete even when the native bridge rejects with null', async () => {
    for (const [name, tool, globals] of [
        ['extended_http_tools', 'manage_cookies', { toolCall: async () => { throw null; } }],
        ['extended_file_tools', 'file_exists', { Tools: { Files: { exists: async () => { throw null; } } } }],
        ['extended_memory_tools', 'create_memory', { toolCall: async () => { throw null; } }],
        ['system_tools', 'get_device_info', { Tools: { System: { getDeviceInfo: async () => { throw null; } } } }],
    ]) {
        const script = await load(name, globals);
        const result = await script.invoke(tool, { path: '/test', title: 'test', content: 'test' });
        assert.equal(result.success, false, name);
        assert.ok(result.message, name);
    }
});

test('worldbook rejects damaged storage without replacing it with an empty list', async () => {
    for (const content of ['', '{broken', '{}']) {
        const script = await load('worldbook/src/shared/worldbook_storage', {
            ToolPkg: { getConfigDir: () => '/worldbook' },
            Tools: { Files: {
                mkdir: async () => ({ successful: true }), exists: async () => ({ exists: true }),
                read: async () => ({ content }), write: async () => assert.fail('original file must remain untouched'),
            } },
        });
        await assert.rejects(script.invoke('readWorldBookEntries'), /世界书/);
    }
});

test('worldbook preserves valid empty data and rejects unsuccessful writes', async () => {
    const script = await load('worldbook/src/shared/worldbook_storage', {
        ToolPkg: { getConfigDir: () => '/worldbook' },
        Tools: { Files: { mkdir: async () => ({ successful: true }), exists: async () => ({ exists: true }), read: async () => ({ content: '[]' }), write: async () => ({ successful: false }) } },
    });
    assert.deepEqual(await script.invoke('readWorldBookEntries'), []);
    await assert.rejects(script.invoke('writeWorldBookEntries', []), /保存失败/);
});

test('DuckDuckGo finishes empty and shorter-than-limit result pages', async () => {
    for (const html of ['', '<h2 class="result__title"><a href="https://example.test">Example</a></h2><a class="result__snippet">Readable result</a>']) {
        const host = httpHost([html]);
        const script = await load('duckduckgo_search', host.globals);
        const result = await script.invoke('search', { query: 'PRIVATE_QUERY_SENTINEL' });
        assert.equal(result.success, true);
        assert.match(result.data, html ? /Example/ : /没有/);
        assert.doesNotMatch(JSON.stringify(script.logs), /PRIVATE_QUERY_SENTINEL/);
        assert.equal(host.requests.length, 1);
    }
});

test('DuckDuckGo rejects invalid limits without a request and reports fetch failures as failures', async () => {
    const host = httpHost([new Error('network unavailable'), null]);
    const script = await load('duckduckgo_search', host.globals);
    for (const max_results of ['0', '-1', '2garbage', '1.5', '51']) {
        assert.equal((await script.invoke('search', { query: 'test', max_results })).success, false);
    }
    assert.equal(host.requests.length, 0);
    assert.equal((await script.invoke('fetch_content', { url: 'https://example.test' })).success, false);
});
