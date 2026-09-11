import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { PlanSubmissionCoordinator } = require('../../examples/plan_mode/dist/shared/plan_mode_submission.js');
const binding = { chatId: 'one', runtime: 'main', workspacePath: '/workspace', workspaceEnv: 'linux' };
const dependencies = overrides => ({ read: async () => null, write: async () => {}, disable: async () => {}, send: async () => {}, ...overrides });

test('concurrent clicks and a rebuilt view submit once, after acknowledgement', async () => {
  const coordinator = new PlanSubmissionCoordinator();
  let acknowledge;
  let sends = 0;
  const deps = dependencies({ send: () => { sends++; return new Promise(resolve => { acknowledge = resolve; }); } });
  const first = coordinator.start(binding, 'plan\r\n', deps);
  assert.equal((await coordinator.start(binding, 'plan', deps)).status, 'preparing');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(coordinator.status(binding, 'plan').status, 'preparing');
  acknowledge();
  assert.equal((await first).success, true);
  assert.equal((await coordinator.start(binding, 'plan', deps)).status, 'submitted');
  assert.equal(sends, 1);
});

test('ambiguous send failure remains blocked and is never reported as submitted', async () => {
  const coordinator = new PlanSubmissionCoordinator();
  let sends = 0;
  const deps = dependencies({ send: async () => { sends++; throw new Error('connection lost'); } });
  assert.equal((await coordinator.start(binding, 'plan', deps)).status, 'unknown');
  assert.equal((await coordinator.start(binding, 'plan', deps)).success, false);
  assert.equal(sends, 1);
});

test('a pre-existing same plan after runtime recreation has an unknown receipt', async () => {
  const coordinator = new PlanSubmissionCoordinator();
  let writes = 0;
  const result = await coordinator.start(binding, 'plan', dependencies({ read: async () => '\nplan\r\n', write: async () => { writes++; } }));
  assert.equal(result.status, 'unknown');
  assert.equal(writes, 0);
});

test('read-only failure releases the claim and different chats have independent receipts', async () => {
  const coordinator = new PlanSubmissionCoordinator();
  assert.equal((await coordinator.start(binding, 'plan', dependencies({ read: async () => { throw new Error('read'); } }))).status, 'idle');
  assert.equal((await coordinator.start(binding, 'plan', dependencies())).success, true);
  assert.equal((await coordinator.start({ ...binding, chatId: 'two' }, 'plan', dependencies())).success, true);
});

test('freezes destination and prevents different plans racing over one workspace file', async () => {
  const coordinator = new PlanSubmissionCoordinator();
  const mutable = { ...binding };
  let release;
  let sent;
  const first = coordinator.start(mutable, 'first', dependencies({ read: () => new Promise(resolve => { release = resolve; }), send: async target => { sent = target; } }));
  mutable.chatId = 'other';
  mutable.workspaceEnv = 'android';
  assert.equal((await coordinator.start(binding, 'second', dependencies())).status, 'preparing');
  release(null);
  await first;
  assert.equal(sent.chatId, 'one');
  assert.equal(sent.workspaceEnv, 'linux');
});
