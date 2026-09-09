import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import vm from 'node:vm';
import { createRequire } from 'node:module';

const source = readFileSync(new URL('../../examples/linux_ssh/dist/packages/linux_ssh.js', import.meta.url), 'utf8');
function load(execute = async () => ({ output: '', exitCode: 0, timedOut: false }), envChanges = {}) {
    const env = { LINUX_SSH_HOST: 'example.test', LINUX_SSH_USERNAME: 'tester', LINUX_SSH_PASSWORD: '  exact password  ', ...envChanges };
    const calls = [];
    const context = vm.createContext({ exports: {}, console, getEnv: key => env[key],
        Tools: { SoftwareSettings: { writeEnvironmentVariable: async (key, value) => { env[key] = value; } },
            System: { terminal: { hiddenExec: async (command, options) => { calls.push({ command, options }); return execute(command, options); } } } }
    });
    vm.runInContext(source, context);
    return { tools: context.exports, env, calls };
}

test('configuration rejects invalid target before persisting any field', async () => {
    for (const values of [{ host: 'bad;host' }, { port: 65536 }, { port: 22.5 }, { timeout_ms: -1 }, { username: '-option' }]) {
        const { tools, env } = load();
        const before = { ...env };
        assert.equal((await tools.linux_ssh_configure(values)).success, false);
        assert.deepEqual(env, before);
    }
});

test('password whitespace survives and is separate from command text; explicit local route', async () => {
    const { tools, calls } = load();
    const result = await tools.linux_ssh_exec({ command: 'printf ok' });
    assert.equal(result.success, true);
    assert.ok(calls.every(call => call.options.localOnly === true));
    const remote = calls.find(call => call.options.sshHost);
    assert.equal(remote.options.sshPassword, '  exact password  ');
    assert.ok(!remote.command.includes('exact password'));
    assert.match(remote.command, /StrictHostKeyChecking=accept-new/);
    assert.ok(!remote.command.includes('/dev/null -o'));
});

test('failed save propagates; failed test does not report configure success', async () => {
    const { tools } = load(async () => ({ output: 'offline', exitCode: 255, timedOut: false }));
    const result = await tools.linux_ssh_configure({ test_connection: true });
    assert.equal(result.success, false);
    assert.equal(result.saved, true);
});

test('missing exit status, timeout and truncation never report success or retry command', async () => {
    for (const response of [{ output: '' }, { exitCode: 0, timedOut: true, output: '' }, { exitCode: 0, outputTruncated: true, output: '' }]) {
        const { tools, calls } = load(async (_, options) => options.sshHost ? response : { exitCode: 0, output: '' });
        assert.equal((await tools.linux_ssh_exec({ command: 'modify-once' })).success, false);
        assert.equal(calls.filter(call => call.command.includes('modify-once')).length, 1);
    }
});

test('invalid window and line ranges fail before remote submission', async () => {
    const { tools, calls } = load();
    assert.equal((await tools.linux_ssh_tmux_close({ window_name: 'task:other' })).success, false);
    assert.equal((await tools.linux_ssh_read({ path: '/x', line_start: -1 })).success, false);
    assert.equal((await tools.linux_ssh_tmux_capture({ max_lines: '1; touch /oops' })).success, false);
    assert.equal(calls.length, 0);
});

const distro = process.env.KIYORI_PTY_WSL_DISTRO;
function bash(command) {
    const wrapper = "import json,subprocess,sys; p=subprocess.run(['bash','-c',sys.argv[1]],capture_output=True); print(json.dumps(dict(exitCode=p.returncode,output=p.stdout.decode('utf-8').strip('\\n')+p.stderr.decode('utf-8'),timedOut=False)))";
    const result = spawnSync('wsl.exe', ['-d', distro, '--exec', 'python3', '-c', wrapper, command], { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024, timeout: 20000 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    return JSON.parse(result.stdout);
}
function executeLocally(command) {
    // 模拟 SSH 边界，真实执行生成的远端 Shell；从不连接外部服务器。
    return bash(`ssh() { while [ "$1" != -- ]; do shift; done; shift 2; /bin/sh -c "$1"; }; sshpass() { shift; "$@"; }; ${command.replace('__KIYORI_SSH_PROXY_OPTION__', '-o ProxyCommand=none')}`);
}

test('real shell file round trip preserves whitespace, CRLF, Unicode and marker-like text', { skip: !distro }, async () => {
    const path = bash('mktemp /tmp/kiyori-ssh-test.XXXXXX').output;
    assert.match(path, /^\/tmp\/kiyori-ssh-test\.[a-zA-Z0-9]+$/);
    try {
        const { tools } = load(executeLocally);
        const content = '\n\n  中文😀\t\r\n__KIYORI_END__\nlast  \n\n';
        const written = await tools.linux_ssh_write({ path, content });
        assert.equal(written.success, true, JSON.stringify(written));
        const read = await tools.linux_ssh_read({ path });
        assert.equal(read.success, true, JSON.stringify(read));
        assert.equal(read.content, content);
        assert.equal((await tools.linux_ssh_edit({ path, old_text: 'last', new_text: 'changed' })).success, true);
        assert.equal((await tools.linux_ssh_read({ path })).content, content.replace('last', 'changed'));
        assert.equal((await tools.linux_ssh_edit({ path, old_text: 'absent', new_text: 'bad' })).success, false);
        assert.equal((await tools.linux_ssh_read({ path })).content, content.replace('last', 'changed'));
    } finally { bash(`rm -f -- '${path}' '${path}.kiyori-edit.lock'`); }
});

test('real shell missing file cannot be hidden by trailing markers', { skip: !distro }, async () => {
    const { tools } = load(executeLocally);
    assert.equal((await tools.linux_ssh_read({ path: '/nonexistent-kiyori-test/file' })).success, false);
});

function loadUi(callTool) {
    const path = new URL('../../examples/linux_ssh/dist/linux_ssh_setup/index.ui.js', import.meta.url);
    const state = new Map();
    const refs = new Map();
    const context = vm.createContext({ exports: {}, console, Icons: { Delete: 'delete', Sync: 'sync', Add: 'add' }, getLang: () => 'zh-CN', require: createRequire(path) });
    vm.runInContext(readFileSync(path, 'utf8'), context);
    const env = { LINUX_SSH_HOST: 'example.test', LINUX_SSH_USERNAME: 'tester', LINUX_SSH_PASSWORD: 'secret' };
    const ctx = {
        getEnv: key => env[key], getCurrentPackageName: () => 'linux_ssh', getCurrentToolPkgId: () => '',
        useState: (key, initial) => { if (!state.has(key)) state.set(key, initial); return [state.get(key), value => state.set(key, value)]; },
        useRef: (key, initial) => { if (!refs.has(key)) refs.set(key, { current: initial }); return refs.get(key); },
        UI: new Proxy({}, { get: (_, type) => (props, children = []) => ({ type, props, children }) }),
        isPackageImported: async () => true, usePackage: async () => '', callTool,
    };
    return { render: () => context.exports.default(ctx), state };
}
function findNode(node, predicate) {
    if (!node) return undefined;
    if (predicate(node)) return node;
    for (const child of node.children ?? []) { const found = findNode(child, predicate); if (found) return found; }
}

test('UI loads without network and failed tool result stays failed', async () => {
    const calls = [];
    const ui = loadUi(async (...args) => { calls.push(args); return { success: false, error: 'connection lost' }; });
    await ui.render().props.onLoad();
    assert.equal(calls.length, 0);
    ui.state.set('configExpanded', true);
    const button = findNode(ui.render(), n => n.props.text === '测试已保存的连接');
    await button.props.onClick();
    assert.equal(calls.length, 1);
    assert.match(ui.state.get('status'), /失败/);
    assert.equal(ui.state.get('output'), 'connection lost');
});

test('UI prevents double submission, never retries an exception under another tool name', async () => {
    let rejectCall;
    const calls = [];
    const ui = loadUi((...args) => { calls.push(args); return new Promise((_, reject) => { rejectCall = reject; }); });
    ui.render();
    ui.state.set('configExpanded', true);
    const button = findNode(ui.render(), n => n.props.text === '测试已保存的连接');
    const first = button.props.onClick();
    const second = button.props.onClick();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(calls.length, 1);
    rejectCall(new Error('response unknown'));
    await Promise.all([first, second]);
    assert.equal(calls.length, 1);
    assert.match(ui.state.get('output'), /response unknown/);
});

test('UI refuses unsaved target edits before tool dispatch', async () => {
    const calls = [];
    const ui = loadUi(async (...args) => calls.push(args));
    ui.render();
    ui.state.set('configExpanded', true);
    ui.state.set('host', 'other.test');
    await findNode(ui.render(), n => n.props.text === '测试已保存的连接').props.onClick();
    assert.equal(calls.length, 0);
    assert.match(ui.state.get('output'), /先保存/);
});
