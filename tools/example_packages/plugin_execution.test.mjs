import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { build } from 'esbuild';

async function load(entry, globals) {
    const code = await build({ entryPoints: [new URL(`../../examples/${entry}.ts`, import.meta.url).pathname.replace(/^\/(?=[A-Za-z]:)/, '')], bundle: true, write: false, platform: 'neutral', format: 'cjs', target: 'es2020', logLevel: 'silent' });
    const results = [], logs = [];
    const exported = {};
    const context = vm.createContext({ exports: exported, module: { exports: exported }, complete: value => results.push(value), console: { log() {}, warn() {}, error: (...args) => logs.push(args) }, ...globals });
    vm.runInContext(code.outputFiles[0].text, context);
    return { results, logs, exports: context.module.exports, async invoke(name, params) {
        const before = results.length;
        await context.module.exports[name](params);
        assert.equal(results.length, before + 1);
        return JSON.parse(JSON.stringify(results.at(-1)));
    } };
}
function uiHost(runSubAgent) {
    return { getState: () => 'virtual_display', Tools: { UI: { runSubAgent }, System: { listApps: async () => ({ packages: ['App A (a.test)', 'App B (b.test)'] }) } } };
}
const parallel = { intent_1: 'task A', target_app_1: 'App A', agent_id_1: 'one', intent_2: 'task B', target_app_2: 'App B', agent_id_2: 'two' };

test('UI completion callback failures never submit a second terminal result', async () => {
    let completions = 0;
    const host = await load('automatic_ui_subagent', { ...uiHost(async () => ({ executionSuccess: true, agentId: 'one' })), complete() { completions++; throw new Error('callback failed'); } });
    await assert.rejects(host.exports.run_subagent_virtual({ intent: 'task', agent_id: 'one' }), /callback failed/);
    assert.equal(completions, 1);
});

test('UI subagent preserves host business failure and target session identity', async () => {
    const host = await load('automatic_ui_subagent', uiHost(async () => ({ executionSuccess: false, executionError: 'maximum steps reached', executionMessage: '', agentId: 'one', executionSteps: 20 })));
    const result = await host.invoke('run_subagent_virtual', { intent: 'task', agent_id: 'one' });
    assert.equal(result.success, false);
    assert.equal(result.data.agentId, 'one');
    assert.match(result.message, /maximum steps/);
});
test('UI parallel execution preserves partial results and never claims all-success on a failed branch', async () => {
    for (const failure of ['returned', 'thrown']) {
        const calls = [];
        const host = await load('automatic_ui_subagent', uiHost(async (intent, steps, agentId, target) => {
            calls.push(target);
            if (target === 'b.test' && failure === 'thrown') throw new Error('target unavailable');
            return { executionSuccess: target === 'a.test', executionMessage: 'finished', agentId, executionSteps: 1 };
        }));
        const result = await host.invoke('run_subagent_parallel_virtual', parallel);
        assert.equal(result.success, false);
        assert.equal(result.data.completed_count, 1);
        assert.equal(result.data.failed_count, 1);
        assert.equal(result.data.results.length, 2);
        assert.equal(calls.length, 2);
    }
});
test('UI parallel input validation prevents empty runs and shared-screen collisions before host execution', async () => {
    const calls = [];
    const globals = uiHost(async () => { calls.push('run'); });
    globals.Tools.System.listApps = async () => { calls.push('list'); return { packages: [] }; };
    const host = await load('automatic_ui_subagent', globals);
    for (const params of [{}, { ...parallel, agent_id_2: 'one' }, { ...parallel, max_steps_2: 1.5 }, { ...parallel, max_steps_1: NaN }]) {
        assert.equal((await host.invoke('run_subagent_parallel_virtual', params)).success, false);
    }
    assert.equal(calls.length, 0);
    assert.equal((await host.invoke('run_subagent_virtual', { intent: 'task', agent_id: 'one', max_steps: Infinity })).success, false);
    assert.equal(calls.length, 0);
});
test('context limiter rejects malformed counts and awaits the real persistence outcome', async () => {
    const writes = [];
    let release;
    const host = await load('context_limiter_c/src/packages/ctx_limiter_c', { Tools: { SoftwareSettings: { writeEnvironmentVariable: async (...args) => { writes.push(args); await new Promise(resolve => release = resolve); } } }, getEnv: () => '5abc' });
    for (const n of [0, -1, 1.5, '5abc', true, false, null, [5], Infinity, NaN, Number.MAX_SAFE_INTEGER + 1]) {
        assert.equal((await host.invoke('set_floor_limit', { n })).success, false);
    }
    assert.equal(writes.length, 0);
    const before = host.results.length;
    let finished = false;
    const pending = host.exports.set_floor_limit({ n: 12 }).then(() => finished = true);
    await Promise.resolve();
    assert.equal(finished, false);
    assert.equal(host.results.length, before);
    release();
    await pending;
    assert.equal(host.results.length, before + 1);
    assert.equal(host.results.at(-1).floor_limit, 12);
    assert.equal((await host.invoke('get_floor_limit')).floor_limit, 5);
});
test('context limiter persistence rejection returns one diagnostic failure without logging private details', async () => {
    const host = await load('context_limiter_c/src/packages/ctx_limiter_c', { Tools: { SoftwareSettings: { writeEnvironmentVariable: async () => { throw new Error('PRIVATE_SETTING_SENTINEL'); } } } });
    const result = await host.invoke('set_floor_limit', { n: 12 });
    assert.equal(result.success, false);
    assert.equal(result.error, 'WRITE_FAILED');
    assert.doesNotMatch(JSON.stringify(host.logs), /PRIVATE_SETTING_SENTINEL/);
});
