import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import test from 'node:test';

// Execute the shipped JS implementation, including its per-call version lookup.
const sourcePath = new URL('../../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgApiRuntime.kt', import.meta.url);
const kotlin = readFileSync(fileURLToPath(sourcePath), 'utf8');
const source = kotlin.slice(kotlin.indexOf('return """') + 10, kotlin.lastIndexOf('""".trimIndent()'));

function runtime() {
  const calls = new Map();
  const context = vm.createContext({
    __operitGetCallState: id => calls.get(id),
  });
  context.__operitExpose = (name, value) => { context[name] = value; };
  vm.runInContext(source, context);
  const select = (id, version) => {
    calls.set(id, { toolPkgApi: { apiVersion: version } });
    context.__operitCurrentCallId = id;
  };
  return { api: context.__operitToolPkgApi, select };
}

test('same facade selects the implementation for the current package on every call', () => {
  const { api, select } = runtime();
  const facade = api.namespace('Tools.Example', {
    call: api.method().since('1.0.1', value => `new:${value}`).since('1.0.0', value => `old:${value}`),
  });
  select('legacy', '1.0.0');
  assert.equal(facade.call('x'), 'old:x');
  select('modern', '1.0.1');
  assert.equal(facade.call('y'), 'new:y');
  select('legacy-again', '1.0.0');
  assert.equal(facade.call('z'), 'old:z');
});

test('missing context and older API reject before executing a new native operation', () => {
  const { api, select } = runtime();
  let executions = 0;
  const facade = api.namespace('Tools.Chat', { call: api.method().since('1.0.1', () => ++executions) });
  assert.throws(() => facade.call(), /execution context/);
  select('old', '1.0.0');
  assert.throws(() => facade.call(), /requires ToolPkg API 1.0.1/);
  assert.equal(executions, 0);
});

test('declarations reject malformed and duplicate variants', () => {
  const { api } = runtime();
  assert.throws(() => api.namespace('A', { b: api.method().since('1.0', () => {}) }), /major.minor.patch/);
  assert.throws(() => api.namespace('A', { b: api.method().since('1.0.0', () => {}).since('1.0.0', () => {}) }), /duplicate/);
  assert.throws(() => api.namespace('A..B', {}), /empty path/);
  assert.throws(() => api.namespace('A', { b: api.method().since('1.0.0', () => {}).since('01.0.0', () => {}) }), /duplicate/);
  assert.throws(() => api.namespace('A', { b: api.method().since('999999999999999999999.0.0', () => {}) }), /major.minor.patch/);
});

test('dispatch preserves receiver, arguments, and asynchronous completion', async () => {
  const { api, select } = runtime();
  const facade = api.namespace('A', { call: api.method().since('1.0.1', async function(value) { return this.prefix + value; }) });
  select('request', '1.0.1');
  assert.equal(await facade.call.call({ prefix: 'ok:' }, 'result'), 'ok:result');
});
