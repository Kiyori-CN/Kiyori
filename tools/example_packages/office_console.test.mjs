import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

const source = readFileSync(new URL('../../examples/office_suite/dist/office_console/index.ui.js', import.meta.url), 'utf8');
function screenHost(callTool = async () => ({ success: true, data: {} }), options = {}) {
  const state = new Map(), refs = new Map(), calls = [], deleted = [];
  const context = vm.createContext({ exports: {}, getLang: () => 'zh', console, Tools: { Files: {
    deleteFile: async (...args) => { deleted.push(args); return { successful: true }; },
    open: async () => options.openResult ?? { successful: true }
  } } });
  vm.runInContext(source, context);
  const ctx = {
    useState(key, initial) {
      if (!state.has(key)) state.set(key, initial);
      return [state.get(key), value => state.set(key, value)];
    },
    useRef(key, initial) {
      if (!refs.has(key)) refs.set(key, { current: initial });
      return refs.get(key);
    },
    UI: new Proxy({}, { get: (_, type) => (props, children = []) => ({ type, props, children }) }),
    openFilePicker: async () => { if (options.pickerError) throw options.pickerError; return options.selection ?? { cancelled: true, files: [] }; },
    callTool: async (tool, params) => { calls.push({ tool, params }); return await callTool(tool, params); }
  };
  const render = () => context.exports.default(ctx);
  const nodes = root => [root, ...[...root.children, ...(Array.isArray(root.props.label) ? root.props.label : []), ...(root.props.supportingText ?? [])].flatMap(nodes)];
  const button = label => nodes(render()).find(n => ['Button', 'OutlinedButton', 'FilterChip'].includes(n.type) &&
    (n.props.text === label || nodes(n).some(c => c.type === 'Text' && c.props.text === label)));
  const click = async label => { const node = button(label); assert.ok(node, `missing button: ${label}`); assert.notEqual(node.props.enabled, false, `disabled: ${label}`); await node.props.onClick(); };
  const texts = () => nodes(render()).filter(n => n.type === 'Text').map(n => n.props.text).join('\n');
  return { state, calls, deleted, render, nodes, button, click, texts };
}

test('file source changes clear stale paths and one primary action sends explicit env', async () => {
  const host = screenHost();
  assert.equal(host.button('读取结构').props.enabled, false);
  host.state.set('path', '/sdcard/old.docx');
  await host.click('Ubuntu 文件');
  assert.equal(host.state.get('path'), '');
  host.state.set('path', ' /root/report.docx ');
  await host.click('读取结构');
  assert.equal(host.calls[0].params.env, 'linux');
  assert.equal(host.calls[0].params.path, '/root/report.docx');
  assert.equal(host.nodes(host.render()).filter(n => n.type === 'Button').length, 1);
});

test('install plans display commands before explicit confirmation and tier status is accurate', async () => {
  const host = screenHost(async tool => ({ success: true, data: tool.endsWith('office_env_check')
    ? { tiers: { tier1: { complete: true }, tier2: { complete: false } } }
    : { tier: 2, commands: ['apt-get install poppler-utils'] } }));
  await host.click('环境与帮助');
  await host.click('检查办公环境');
  assert.match(host.texts(), /T1 · 已就绪/);
  assert.match(host.texts(), /T2 · 有缺失项/);
  await host.click('查看 T2 安装计划');
  assert.equal(host.calls[1].params.confirm, undefined);
  assert.match(host.texts(), /apt-get install poppler-utils/);
  await host.click('确认执行安装计划');
  assert.equal(host.calls[2].params.confirm, true);
  assert.equal(host.calls[2].params.tier, 2);
  assert.equal(host.button('确认执行安装计划'), undefined);
});

test('shared lock prevents duplicate calls and retains actionable failures', async () => {
  let finish;
  const host = screenHost(() => new Promise(resolve => { finish = resolve; }));
  await host.click('环境与帮助');
  const click = host.button('检查办公环境').props.onClick;
  const running = click();
  await click();
  assert.equal(host.calls.length, 1);
  assert.equal(host.button('检查办公环境').props.enabled, false);
  finish({ success: false, message: '缺少字体', remedy: '安装 poppler-data' });
  await running;
  assert.match(host.texts(), /缺少字体/);
  assert.match(host.texts(), /安装 poppler-data/);
});

test('preview forwards pages and output settings; plain object errors retain issue details', async () => {
  const host = screenHost(async () => { throw { message: '校验失败', data: { issues: [{ code: 'FORMULA_NOT_RECALCULATED', target: 'D2' }] } }; });
  host.state.set('path', '/sdcard/report.xlsx');
  await host.click('预览');
  host.state.set('pages', '2-4');
  host.state.set('outputEnv', 'linux');
  host.state.set('outputPath', '/root/previews');
  await host.click('生成预览');
  assert.equal(host.calls[0].params.pages, '2-4');
  assert.equal(host.calls[0].params.output_env, 'linux');
  assert.equal(host.calls[0].params.output_path, '/root/previews');
  assert.match(host.texts(), /FORMULA_NOT_RECALCULATED/);
  assert.match(host.texts(), /D2/);
  await host.click('转 PDF');
  assert.equal(host.state.get('outputPath'), '');
  assert.equal(host.state.get('result'), null);
  await host.click('转换为 PDF');
  assert.equal(host.calls[1].params.from_path, '/sdcard/report.xlsx');
  assert.equal(host.calls[1].params.engine, 'libreoffice');
  assert.equal(host.calls[1].params.to_format, 'pdf');
});

test('invalid pages and PDF-to-PDF are disabled before invoking tools', async () => {
  const host = screenHost();
  host.state.set('path', '/sdcard/report.pdf');
  await host.click('预览');
  host.state.set('pages', '0,x');
  assert.equal(host.button('生成预览').props.enabled, false);
  await host.click('转 PDF');
  assert.equal(host.button('转换为 PDF').props.enabled, false);
  assert.equal(host.calls.length, 0);
});

test('picker cancellation and failure preserve input, imported cleanup never deletes editable input', async () => {
  const cancelled = screenHost();
  cancelled.state.set('path', '/sdcard/original.docx');
  await cancelled.click('选择文件');
  assert.equal(cancelled.state.get('path'), '/sdcard/original.docx');
  const failed = screenHost(undefined, { pickerError: { message: 'picker failed' } });
  await failed.click('选择文件');
  assert.match(failed.texts(), /picker failed/);
  const host = screenHost(undefined, { selection: { cancelled: false, files: [{ path: '/cache/copy.docx', name: 'original.docx' }] } });
  await host.click('选择文件');
  assert.equal(host.state.get('path'), '/cache/copy.docx');
  host.state.set('path', '/sdcard/do-not-delete.docx');
  await host.click('存储管理');
  await host.click('移除本页导入副本');
  assert.equal(host.deleted.length, 0);
  await host.click('确认移除副本');
  assert.deepEqual(host.deleted, [['/cache/copy.docx', false, 'android']]);
  assert.equal(host.state.get('path'), '/sdcard/do-not-delete.docx');
});

test('storage preview can be cancelled and confirmation forwards exact task scope and token', async () => {
  const host = screenHost(async (tool, params) => ({ success: true, data: tool.endsWith('office_workspace_status')
    ? { tasks: [{ task_id: 'demo', path: '/root/work/demo', cleanable: true }], offset: 0, total_tasks: 1 }
    : { ...params, path: '/root/work/demo', plan_token: 'reviewed-token', file_count: 2, bytes: 20, sample_files: ['tmp/a.png'] } }));
  await host.click('存储管理');
  await host.click('扫描工作文件');
  await host.click('清理临时');
  assert.equal(host.calls[1].params.confirm, undefined);
  assert.match(host.texts(), /保留 out 输出/);
  await host.click('取消');
  assert.equal(host.calls.length, 2);
  await host.click('删除任务');
  assert.match(host.texts(), /包含 out 输出/);
  await host.click('确认执行清理');
  assert.equal(host.calls[3].params.task_id, 'demo');
  assert.equal(host.calls[3].params.scope, 'task');
  assert.equal(host.calls[3].params.plan_token, 'reviewed-token');
  assert.equal(host.calls[3].params.confirm, true);
  assert.equal(host.state.get('storage'), null);
});

test('active tasks cannot be cleaned; storage paging and tab changes discard stale plans', async () => {
  const host = screenHost(async () => ({ success: true, data: { tasks: [{ task_id: 'active', path: '/root/work/active', active: true, cleanable: false }], offset: 0, next_offset: 20 } }));
  await host.click('存储管理');
  await host.click('扫描工作文件');
  assert.equal(host.button('清理临时').props.enabled, false);
  assert.equal(host.button('删除任务').props.enabled, false);
  await host.click('下一页');
  assert.equal(host.calls[1].params.offset, 20);
  await host.click('文件操作');
  assert.equal(host.state.get('result'), null);
});

test('artifact follow-up preserves exact env; open failure retains delivered path', async () => {
  const host = screenHost(async () => ({ success: true, artifacts: [{ path: '/sdcard/final.pptx', env: 'android' }] }),
    { openResult: { successful: false, details: 'no viewer' } });
  host.state.set('path', '/sdcard/input.pptx');
  await host.click('读取结构');
  await host.click('打开文件');
  assert.match(host.texts(), /no viewer/);
  assert.equal(host.state.get('result').artifacts[0].path, '/sdcard/final.pptx');
  await host.click('作为输入继续处理');
  assert.equal(host.state.get('path'), '/sdcard/final.pptx');
  assert.equal(host.state.get('env'), 'android');
});
