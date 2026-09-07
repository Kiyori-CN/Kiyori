import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

const source = readFileSync(new URL('../../examples/browser_development.js', import.meta.url), 'utf8');
function load(net) {
    const context = {Tools: {Net: net}, exports: {}};
    vm.runInNewContext(source, context);
    return context.exports;
}

test('metadata exposes complete help and typed mutation parameters', () => {
    const metadata = JSON.parse(source.match(/\/\* METADATA\s*([\s\S]*?)\*\//)[1]);
    assert.equal(metadata.name, 'browser_development');
    assert.deepEqual(metadata.tools.map(tool => tool.name), ['help', 'query', 'apply']);
    assert.equal(metadata.tools.find(tool => tool.name === 'apply').parameters.find(param => param.name === 'enabled').type, 'boolean');
    assert.equal(metadata.enabledByDefault, true);
});

test('help and source reads delegate to the shared host query', async () => {
    const calls = [];
    const api = load({browserDevelopmentQuery: async params => { calls.push(params); return 'host-result'; }});
    assert.equal(await api.help(), 'host-result');
    assert.equal(calls[0].action, 'help');
    const input = {action: 'read', target: 'extension', id: 'com.example.marker', file: 'main.js', offset: 16000, expected_revision: 'sha'};
    assert.equal(await api.query(input), 'host-result');
    assert.equal(calls[1], input);
});

test('apply preserves false and expected revision, and never swallows host failures', async () => {
    const calls = [];
    const api = load({browserDevelopmentApply: async params => { calls.push(params); throw new Error('REVISION_CONFLICT'); }});
    const input = {action: 'set_enabled', target: 'extension', id: 'com.example.marker', enabled: false, expected_revision: 'old'};
    await assert.rejects(api.apply(input), /REVISION_CONFLICT/);
    assert.equal(calls.length, 1);
    assert.equal(calls[0], input);
});

test('APK bundled package equals compiled source and is production-whitelisted', () => {
    assert.equal(readFileSync(new URL('../../app/src/main/assets/packages/browser_development.js', import.meta.url), 'utf8'), source);
    assert.match(readFileSync(new URL('packages_whitelist.txt', import.meta.url), 'utf8'), /^browser_development\.js$/m);
});
