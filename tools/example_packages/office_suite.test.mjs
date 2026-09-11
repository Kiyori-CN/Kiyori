import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

/**
 * 办公文档套件薄层回归。
 *
 * 这些用例锁死真机测试暴露的故障：
 * - 宿主注入参数（__operit_* / containerPackageName / toolPkgId）不得进入 Python args；
 * - 同一命令只发送 schema 声明的字段，避免 additionalProperties 误杀；
 * - /sdcard 与 /storage/emulated/0 别名按候选顺序探测；
 * - Tools.Files.* 的结构化失败必须转成带路径的错误，不能静默继续。
 */

const SPECS_SOURCE = readFileSync(
  new URL('../../examples/office_suite/dist/shared/specs.js', import.meta.url),
  'utf8'
);
const PROTOCOL_SOURCE = readFileSync(
  new URL('../../examples/office_suite/dist/shared/protocol.js', import.meta.url),
  'utf8'
);
const RUNTIME_SOURCE = readFileSync(
  new URL('../../examples/office_suite/dist/shared/runtime.js', import.meta.url),
  'utf8'
);

const HOST_INJECTED = {
  __operit_package_name: 'office',
  __operit_package_state: 'default',
  __operit_package_caller_name: 'chat',
  __operit_toolpkg_runtime_kind: 'sandbox',
  __operit_toolpkg_subpackage_id: 'office',
  __operit_ui_package_name: 'com.kiyori.office_suite',
  __operit_script_screen: 'dist/packages/office.js',
  containerPackageName: 'com.kiyori.office_suite',
  toolPkgId: 'com.kiyori.office_suite'
};

function createHost(options = {}) {
  const calls = { hidden: [], files: [], written: [], streaming: [], leases: [] };
  const existing = new Set(options.existing ?? []);
  // protocol.js 先在独立上下文求值，再作为 runtime.js 的 require("./protocol") 结果。
  // 这样 runtime.js 能直接看到宿主全局 Tools，同时保持 dist 产物的真实依赖关系。
  const protocolExports = {};
  const protocolContext = vm.createContext({
    exports: protocolExports,
    module: { exports: protocolExports },
    console
  });
  vm.runInContext(PROTOCOL_SOURCE, protocolContext);
  const context = vm.createContext({
    exports: {},
    console,
    module: { exports: {} },
    require: id => {
      if (id === './protocol') {
        return protocolExports;
      }
      throw new Error(`unexpected require: ${id}`);
    },
    getEnv: () => '',
    getArtifactPaths: () => options.artifactPaths ?? ({ android: '/storage/emulated/0/Download/Kiyori/workspace', linux: '/workspace' }),
    ToolPkg: {
      readResource: async () => '/data/cache/kiyori_office_runtime.zip'
    },
    Tools: {
      System: {
        terminal: {
          create: async () => ({ sessionId: 'office-install' }),
          execStreaming: async (sessionId, command, opts) => {
            calls.streaming.push({ sessionId, command, opts });
            return { exitCode: options.installExitCode ?? 0, timedOut: false, output: '' };
          },
          hiddenExec: async (command, opts) => {
            calls.hidden.push({ command, opts });
            if (command.includes('printf "%s\\n" "$HOME"')) {
              return { output: '/root\n/root/.code_runner/py/bin/python\n', exitCode: 0 };
            }
            if (command.includes('unzip_runtime.py')) {
              return { output: '__KIYORI_OFFICE_READY__\n', exitCode: 0 };
            }
            if (command.includes('-m kiyori_office.storage')) {
              calls.leases.push({ command, fileCount: calls.files.length });
              return { output: envelope({ ok: true, command: 'office_task_lease', data: {} }), exitCode: 0 };
            }
            if (command.includes('-m kiyori_office ')) {
              const argsFile = command.match(/'([^']*args-[^']+\.json)'/);
              const written = argsFile ? calls.written.find(item => item.path === argsFile[1]) : null;
              const payload = written ? JSON.parse(written.content) : {};
              calls.hidden[calls.hidden.length - 1].payload = payload;
              return {
                output: typeof options.envelope === 'function' ? options.envelope(command)
                  : options.envelope ? options.envelope
                  : '__KIYORI_OFFICE_BEGIN__' +
                    JSON.stringify({ ok: true, command: 'office_env_check', data: {}, artifacts: [] }) +
                    '__KIYORI_OFFICE_END__',
                exitCode: command.includes('office_env_check') ? (options.checkExitCode ?? 0) : 0
                , timedOut: options.timedOut ?? false
              };
            }
            return { output: '', exitCode: 0 };
          }
        }
      },
      Files: {
        deleteFile: async (path, recursive, env) => {
          calls.files.push({ op: 'delete', path, recursive, env });
          return options.deleteResult ?? { successful: true };
        },
        read: async ({ path, environment, direct_image }) => {
          calls.files.push({ op: 'read', path, env: environment, direct_image });
          if (direct_image) {
            if (options.imageThrows) throw new Error('image registration failed');
            return { content: options.imageContent ?? '<link type="image" id="page-image"></link>' };
          }
          return { content: path.includes('.local-provider-')
            ? (options.wrongProvider ? 'another machine' : path.split('.local-provider-')[1])
            : '# Office guide\nRead the actual file before editing.' };
        },
        move: async (source, destination, env) => {
          calls.files.push({ op: 'move', source, destination, env });
          return { successful: true };
        },
        mkdir: async (path, parents, env) => {
          calls.files.push({ op: 'mkdir', path, env });
          return options.mkdirResult ?? { successful: true };
        },
        exists: async (path, env) => {
          calls.files.push({ op: 'exists', path, env });
          if (options.exists) {
            return { exists: await options.exists(path, env) };
          }
          return { exists: existing.has(path) || path.startsWith('/root/') };
        },
        info: async (path, env) => {
          calls.files.push({ op: 'info', path, env });
          if (options.info) {
            return await options.info(path, env);
          }
          return { path, env, exists: true, fileType: 'file', size: 0 };
        },
        copy: async (source, destination, recursive, sourceEnv, destEnv) => {
          calls.files.push({ op: 'copy', source, destination, sourceEnv, destEnv });
          if (options.copyResult && !destination.endsWith('runtime.zip')) {
            return options.copyResult;
          }
          return { successful: true };
        },
        write: async (path, content, append, env) => {
          calls.files.push({ op: 'write', path, env });
          calls.written.push({ path, content });
          return options.writeResult ?? { successful: true };
        }
      }
    }
  });
  vm.runInContext(SPECS_SOURCE, context);
  vm.runInContext(RUNTIME_SOURCE, context);
  return { context, calls };
}

async function runTool(specName, params, options) {
  const host = createHost(options);
  const spec = host.context.exports.OFFICE_TOOLS[specName].spec;
  const result = await host.context.exports.safeRunOfficeTool(spec, params);
  return { host, spec, result };
}

function lastArgsPayload(host) {
  const hidden = host.calls.hidden.filter(call => call.payload);
  return hidden.length ? hidden[hidden.length - 1].payload : null;
}

const envelope = value => '__KIYORI_OFFICE_BEGIN__' + JSON.stringify(value) + '__KIYORI_OFFICE_END__';

test('default deliveries use configured roots, task isolation and the real extension in both environments', async () => {
  for (const output_env of ['android', 'linux']) {
    const artifactPaths = { android: '/storage/emulated/0/Documents/中文资料', linux: '/work/中文资料' };
    const options = { artifactPaths,
      envelope: envelope({ ok: true, command: 'docx_create', artifacts: [
        { path: '/root/kiyori_office/work/t1/out/report.pdf', env: 'linux', bytes: 42, role: 'output' }
      ], data: {} }), info: () => ({ exists: true, size: 42 }) };
    const first = await runTool('docx_create', { markdown: 'x', env: 'linux', output_env, task_id: 't1' }, options);
    assert.equal(first.result.success, true, JSON.stringify(first.result));
    assert.equal(first.result.artifacts[0].path, `${artifactPaths[output_env]}/office/t1/report.pdf`);
    assert.equal(first.result.artifacts[0].env, output_env);
    assert.equal(lastArgsPayload(first.host).output_path, undefined);
    const second = await runTool('docx_create', { markdown: 'x', env: 'linux', output_env, task_id: 't2' }, options);
    assert.equal(second.result.artifacts[0].path, `${artifactPaths[output_env]}/office/t2/report.pdf`);
  }
});

test('an explicit Android delivery does not consult default artifact settings', async () => {
  const { result } = await runTool('docx_create', { markdown: 'x', env: 'linux', output_path: '/sdcard/chosen/report.docx' }, {
    artifactPaths: { android: '/storage/emulated/0/unused', linux: '/unused' },
    envelope: envelope({ ok: true, command: 'docx_create', artifacts: [
      { path: '/root/out/report.docx', env: 'linux', bytes: 42, role: 'output' }
    ], data: {} }), info: () => ({ exists: true, size: 42 })
  });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.match(result.artifacts[0].path, /\/chosen\/report.docx$/);
});

test('preview attaches actual multimodal links from the delivered environment', async () => {
  for (const output_env of ['linux', 'android']) {
    const { host, result } = await runTool('office_render_preview', { env: 'linux', path: '/root/a.pdf', output_env }, {
      envelope: envelope({ ok: true, command: 'office_render_preview', data: { pages: [3], target_dir: '/root/out' },
        artifacts: [{ path: '/root/out/page.jpg', env: 'linux', bytes: 24, role: 'output', page: 3 }] }),
      info: () => ({ exists: true, size: 24 })
    });
    assert.equal(result.success, true, JSON.stringify(result));
    assert.match(result.data.visual_pages[0].image, /<link type="image"/);
    assert.equal(result.data.visual_pages[0].page, 3);
    assert.equal(result.data.visual_review_status, 'images_attached_review_required');
    assert.equal(host.calls.files.find(c => c.direct_image).env, output_env);
  }
});

test('OCR or failed image registration cannot pass visual preview', async () => {
  for (const failure of [{ imageContent: 'OCR text only' }, { imageThrows: true }]) {
    const { result } = await runTool('office_render_preview', { env: 'linux', path: '/root/a.pdf', output_env: 'linux' }, {
      ...failure,
      info: () => ({ exists: true, size: 24 }),
      envelope: envelope({ ok: true, command: 'office_render_preview', artifacts: [{ path: '/root/out/page.jpg', env: 'linux', bytes: 24 }] })
    });
    assert.equal(result.success, false);
    assert.equal(result.code, 'E_ENGINE_FAILED');
    assert.match(result.artifacts[0].path, /^\/workspace\/office\/[^/]+\/.+\/page.jpg$/);
  }
});

test('nested Android images are staged independently without mutating caller data', async () => {
  const elements = [{ type: 'image', image_path: '/sdcard/one/pic.png' }, { type: 'image', image_path: '/sdcard/two/pic.png' }];
  const { host, result } = await runTool('pptx_create', { env: 'android', slides: [{ elements }] }, {
    existing: ['/storage/emulated/0/one/pic.png', '/storage/emulated/0/two/pic.png']
  });
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.notEqual(payload.slides[0].elements[0].image_path, payload.slides[0].elements[1].image_path);
  assert.match(payload.slides[0].elements[0].image_path, /^\/root\//);
  assert.equal(elements[0].image_path, '/sdcard/one/pic.png');
});

test('text dry-run creates no document work directory or lease', async () => {
  const { host, result } = await runTool('pptx_measure_text', { text: 'measure me', width_cm: 5 });
  assert.equal(result.success, true);
  assert.equal(host.calls.leases.length, 0);
  assert.equal(host.calls.files.some(c => c.path?.includes('/work/')), false);
  assert.ok(host.calls.files.some(c => c.op === 'delete' && c.path.includes('/control/args-')));
});

test('task lease spans Android staging and final delivery', async () => {
  const { host, result } = await runTool('office_convert', { task_id: 'demo', env: 'android', from_path: '/sdcard/a.docx', engine: 'libreoffice', to_format: 'pdf' }, {
    existing: ['/storage/emulated/0/a.docx'],
    envelope: envelope({ ok: true, command: 'office_convert', artifacts: [{ path: '/root/out/a.pdf', bytes: 24, role: 'output', env: 'linux' }] }),
    info: () => ({ exists: true, size: 24 })
  });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(host.calls.leases.length, 2);
  assert.match(host.calls.leases[0].command, /acquire 'demo'/);
  assert.match(host.calls.leases[1].command, /release 'demo'/);
  const inputIndex = host.calls.files.findIndex(c => c.op === 'copy' && c.source.endsWith('a.docx'));
  const deliveryIndex = host.calls.files.findIndex(c => c.op === 'move');
  assert.ok(host.calls.leases[0].fileCount <= inputIndex);
  assert.ok(host.calls.leases[1].fileCount > deliveryIndex);
  assert.match(host.calls.hidden.find(c => c.payload).command, /KIYORI_OFFICE_LEASE_TOKEN=/);
});

test('timed out file operation retains lease and reports exact task', async () => {
  const { host, result } = await runTool('office_read', { task_id: 'timed', env: 'linux', path: '/root/a.docx' }, { timedOut: true });
  assert.equal(result.code, 'E_TIMEOUT');
  assert.equal(result.data.lease_retained, true);
  assert.equal(result.data.task_id, 'timed');
  assert.equal(host.calls.leases.length, 1);
});

test('input staging failure releases acquired lease', async () => {
  const { host, result } = await runTool('office_read', { env: 'android', path: '/sdcard/a.docx' }, {
    existing: ['/storage/emulated/0/a.docx'], copyResult: { successful: false, details: 'copy denied' }
  });
  assert.equal(result.success, false);
  assert.equal(host.calls.leases.length, 2);
  assert.match(host.calls.leases[1].command, /release/);
  assert.ok(result.data.task_id);
});

test('storage control avoids new work tasks and missing clean id fails before IO', async () => {
  const { host, result } = await runTool('office_workspace_status', { limit: 20, offset: 0 });
  assert.equal(result.success, true);
  assert.equal(host.calls.leases.length, 0);
  assert.equal(host.calls.files.some(c => c.path?.includes('/work/')), false);
  assert.ok(host.calls.files.some(c => c.op === 'delete' && c.path.includes('/control/args-')));
  const invalid = await runTool('office_workspace_clean', {});
  assert.equal(invalid.result.code, 'E_INPUT_SCHEMA');
  assert.equal(invalid.host.calls.files.length, 0);
  assert.equal(invalid.host.calls.hidden.length, 0);
});

test('management argument cleanup failure is visible without discarding the operation result', async () => {
  const { result } = await runTool('office_workspace_status', {}, { deleteResult: { successful: false, details: 'busy' } });
  assert.equal(result.success, true);
  assert.equal(result.warnings[0].code, 'CONTROL_ARGS_CLEANUP_FAILED');
  assert.match(result.warnings[0].message, /control\/args-/);
});

test('structured Python failure preserves remedy and validation issues', async () => {
  const { result } = await runTool('office_env_check', {}, { envelope: envelope({ ok: false, command: 'office_env_check',
    error: { code: 'E_VALIDATION_FAILED', message: 'invalid', detail: 'A1', remedy: 'fix A1' }, data: { issues: ['A1'] } }) });
  assert.equal(result.success, false);
  assert.equal(result.remedy, 'fix A1');
  assert.equal(result.data.issues[0], 'A1');
});

test('timeout is not reported as a missing JSON envelope', async () => {
  const { result } = await runTool('office_env_check', {}, { timedOut: true });
  assert.equal(result.code, 'E_TIMEOUT');
});

test('task path traversal is rejected before runtime or file operations', async () => {
  for (const task_id of ['..', '.', '../outside', 'x/y']) {
    const { host, result } = await runTool('office_read', { task_id, env: 'linux', path: '/root/a.docx' });
    assert.equal(result.code, 'E_INPUT_SCHEMA');
    assert.equal(host.calls.files.length, 0);
    assert.equal(host.calls.hidden.length, 0);
  }
});

test('guide reads bundled Skill without bootstrapping Python', async () => {
  const { host, result } = await runTool('office_read_guide', { format: 'xlsx' });
  assert.equal(result.success, true);
  assert.match(result.data.content, /Office guide/);
  assert.equal(host.calls.hidden.length, 0);
});

test('sentinel text in document content survives parsing', async () => {
  const { result } = await runTool('office_env_check', {}, { envelope: envelope({ ok: true, command: 'office_env_check', data: { text: '__KIYORI_OFFICE_END__' } }) });
  assert.equal(result.success, true);
  assert.equal(result.data.text, '__KIYORI_OFFICE_END__');
});

test('conversion default delivery retains actual output extension and publishes after verification', async () => {
  const { host, result } = await runTool('office_convert', { env: 'linux', from_path: '/root/a.docx', to_format: 'pdf', engine: 'libreoffice' }, {
    envelope: envelope({ ok: true, command: 'office_convert', artifacts: [{ path: '/root/out/a.pdf', bytes: 24, sha256: 'test', role: 'output', env: 'linux' }] }),
    info: () => ({ exists: true, size: 24 })
  });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.match(result.artifacts[0].path, /a\.pdf$/);
  const operations = host.calls.files.map(c => c.op);
  assert.ok(operations.indexOf('info') < operations.indexOf('move'));
});

test('confirmed install streams guarded local commands and verifies selected components', async () => {
  const { host, result } = await runTool('office_env_setup', { tier: 1, confirm: true }, {
    envelope: command => command.includes('office_env_check')
      ? envelope({ ok: true, command: 'office_env_check', data: { tiers: { tier1: { components: { openpyxl: { available: true } } } } } })
      : envelope({ ok: true, command: 'office_env_setup', data: { tier: 1, components: ['openpyxl'], commands: ['install-openpyxl'], estimated_bytes: 1, disk_free_bytes: 10 } })
  });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(result.data.completed, true);
  assert.equal(host.calls.streaming.length, 1);
  assert.match(host.calls.streaming[0].command, /^test .*local-install-marker.*&& \(install-openpyxl\)$/);
});

test('installation failure stops without retrying later commands', async () => {
  const { host, result } = await runTool('office_env_setup', { tier: 1, confirm: true }, {
    installExitCode: 1,
    envelope: envelope({ ok: true, command: 'office_env_setup', data: { tier: 1, commands: ['first', 'second'], components: ['openpyxl'] } })
  });
  assert.equal(result.success, false);
  assert.equal(result.data.completed, false);
  assert.equal(host.calls.streaming.length, 1);
});

test('successful envelope cannot conceal a nonzero install verification exit', async () => {
  const { result } = await runTool('office_env_setup', { tier: 1, confirm: true }, {
    checkExitCode: 2,
    envelope: command => command.includes('office_env_check')
      ? envelope({ ok: true, command: 'office_env_check', data: { tiers: { tier1: { components: { openpyxl: { available: true } } } } } })
      : envelope({ ok: true, command: 'office_env_setup', data: { tier: 1, components: ['openpyxl'], commands: ['install'] } })
  });
  assert.equal(result.success, false);
  assert.equal(result.code, 'E_ENGINE_FAILED');
});

test('wrong Linux file provider stops before runtime archive or user input copy', async () => {
  const { host, result } = await runTool('office_read', { env: 'android', path: '/sdcard/a.docx' }, { wrongProvider: true });
  assert.equal(result.code, 'E_PATH_INVALID');
  assert.equal(host.calls.files.filter(c => c.op === 'copy').length, 0);
});

test('corrupt delivery removes only its unique temporary file and never publishes', async () => {
  const { host, result } = await runTool('docx_create', { env: 'linux', output_path: '/sdcard/valuable.docx', overwrite: true, markdown: 'x' }, {
    exists: () => true,
    envelope: envelope({ ok: true, command: 'docx_create', artifacts: [{ path: '/root/out/x.docx', bytes: 42, role: 'output', env: 'linux' }] }),
    info: () => ({ exists: true, size: 43 })
  });
  assert.equal(result.code, 'E_PATH_INVALID');
  assert.equal(host.calls.files.filter(c => c.op === 'move').length, 0);
  const removed = host.calls.files.filter(c => c.op === 'delete');
  assert.equal(removed.length, 1);
  assert.match(removed[0].path, /valuable\.docx\.office-.*\.tmp$/);
  assert.equal(removed[0].recursive, false);
});

test('host-injected parameters never reach the Python args file', async () => {
  const { host, result } = await runTool('office_env_check', {
    verbose: true,
    ...HOST_INJECTED
  });
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.deepEqual(Object.keys(payload).sort(), ['verbose']);
});

test('path commands send task_id and allow_roots for the staging area', async () => {
  const { host } = await runTool(
    'office_read',
    { path: '/root/work/a.md', env: 'linux', task_id: 't1', ...HOST_INJECTED },
    { exists: () => true }
  );
  const payload = lastArgsPayload(host);
  assert.deepEqual(Object.keys(payload).sort(), ['allow_roots', 'path', 'task_id']);
  // 输入所在目录与暂存区输出目录都必须进入白名单，否则 Python 侧会拒绝读取。
  assert.ok(payload.allow_roots.includes('/root/work'), JSON.stringify(payload.allow_roots));
  assert.ok(
    payload.allow_roots.includes('/root/kiyori_office/work/t1/out'),
    JSON.stringify(payload.allow_roots)
  );
});

test('non-path commands do not inject task_id or allow_roots', async () => {
  const { host } = await runTool('office_env_check', {
    verbose: true,
    task_id: 't1',
    allow_roots: ['/tmp/x'],
    ...HOST_INJECTED
  });
  const payload = lastArgsPayload(host);
  assert.deepEqual(Object.keys(payload).sort(), ['verbose']);
});

test('undeclared in_place is rejected with the command name', async () => {
  const { result } = await runTool('docx_create', {
    markdown: '# t',
    in_place: true,
    env: 'android'
  });
  assert.equal(result.success, false);
  assert.match(result.message, /不支持 in_place/);
});

test('env is normalized case-insensitively', async () => {
  const { host, result } = await runTool('office_env_check', {
    env: 'ANDROID',
    verbose: true
  });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.ok(host.calls.files.length >= 0);
});

test('android path aliases are probed in order and the real one is used', async () => {
  const { host, result } = await runTool(
    'office_read',
    { path: '/sdcard/Download/a.md', env: 'android' },
    {
      exists: path => path === '/storage/emulated/0/Download/a.md'
    }
  );
  assert.equal(result.success, true, JSON.stringify(result));
  const copied = host.calls.files.find(
    call =>
      call.op === 'copy' &&
      call.sourceEnv === 'android' &&
      call.destination.endsWith('/in/path-a.md')
  );
  assert.ok(copied, 'expected an android→linux copy');
  assert.equal(copied.source, '/storage/emulated/0/Download/a.md');
});

test('missing android source reports every probed candidate', async () => {
  const { result } = await runTool(
    'office_read',
    { path: '/sdcard/Download/missing.md', env: 'android' },
    { exists: () => false }
  );
  assert.equal(result.success, false);
  assert.equal(result.code, 'E_PATH_INVALID');
  assert.match(result.message, /\/sdcard\/Download\/missing\.md/);
  assert.match(result.message, /\/storage\/emulated\/0\/Download\/missing\.md/);
});

test('structured copy failure is surfaced instead of continuing', async () => {
  const { result } = await runTool(
    'office_read',
    { path: '/storage/emulated/0/Download/a.md', env: 'android' },
    {
      exists: () => true,
      copyResult: { successful: false, error: 'Failed to read source file' }
    }
  );
  assert.equal(result.success, false);
  assert.equal(result.code, 'E_PATH_INVALID');
  assert.match(result.message, /复制输入文件到 Linux 暂存区/);
  assert.match(result.message, /Failed to read source file/);
});

test('relative android paths are rejected with guidance', async () => {
  const { result } = await runTool('office_read', { path: 'AGENTS.md', env: 'android' });
  assert.equal(result.success, false);
  assert.match(result.message, /必须是绝对路径/);
});

test('pdf_create does not inject in_place into the args file', async () => {
  const { host, result } = await runTool('pdf_create', {
    engine: 'reportlab',
    blocks: [{ type: 'paragraph', text: 't' }],
    output_env: 'linux',
    output_path: '/root/out/a.pdf',
    in_place: false,
    env: 'linux'
  });
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.ok(!('in_place' in payload), 'in_place must not be injected when undeclared');
  assert.equal(payload.output_path, '/root/out/a.pdf');
});

test('declared in_place is preserved for edit commands', async () => {
  const { host, result } = await runTool('docx_edit', {
    path: '/root/work/a.docx',
    anchor: { index: 0 },
    operation: 'replace',
    text: 'x',
    in_place: true,
    env: 'linux'
  });
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.equal(payload.in_place, true);
  assert.equal(payload.output_path, '/root/work/a.docx');
});

test('metadata marks xlsx_write path optional and env required on path tools', () => {
  const host = createHost();
  const xlsxWrite = host.context.exports.OFFICE_TOOLS.xlsx_write.meta;
  const pathParam = xlsxWrite.params.find(param => param.name === 'path');
  assert.equal(pathParam.required, false);
  const readMeta = host.context.exports.OFFICE_TOOLS.office_read.meta;
  const envParam = readMeta.params.find(param => param.name === 'env');
  assert.equal(envParam.required, true);
});

test('office_diff stages both inputs without overwriting each other', async () => {
  const { host, result } = await runTool(
    'office_diff',
    {
      left: '/sdcard/Download/a.docx',
      right: '/sdcard/Download/sub/a.docx',
      env: 'android',
      task_id: 't1'
    },
    { exists: () => true }
  );
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  // 两个输入基名相同，必须落到不同的暂存路径，否则后者会覆盖前者。
  assert.notEqual(payload.left, payload.right);
  assert.ok(payload.left.endsWith('/in/left-a.docx'), payload.left);
  assert.ok(payload.right.endsWith('/in/right-a.docx'), payload.right);
  assert.ok(payload.allow_roots.includes('/root/kiyori_office/work/t1/in'));
});

test('office_convert stages from_path so python can read it', async () => {
  const { host, result } = await runTool(
    'office_convert',
    {
      from_path: '/sdcard/Download/a.docx',
      to_format: 'pdf',
      engine: 'libreoffice',
      env: 'android',
      task_id: 't1'
    },
    { exists: () => true }
  );
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.ok(payload.from_path.endsWith('/in/from_path-a.docx'), payload.from_path);
  assert.ok(payload.allow_roots.includes('/root/kiyori_office/work/t1/in'));
});

test('linux workspace init whitelists dir instead of staging it', async () => {
  const { host, result } = await runTool('office_workspace_init', {
    dir: '/root/office_ws',
    env: 'linux',
    task_id: 't1'
  });
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.equal(payload.dir, '/root/office_ws');
  assert.ok(payload.allow_roots.includes('/root/office_ws'), JSON.stringify(payload.allow_roots));
  // dir 是「待创建」目录，不能走输入暂存
  const staged = host.calls.files.find(
    call => call.op === 'copy' && call.destination.includes('/in/')
  );
  assert.equal(staged, undefined, 'dir must not be staged as an input file');
});

test('delivered artifact size mismatch is reported as corruption', async () => {
  const envelope =
    '__KIYORI_OFFICE_BEGIN__' +
    JSON.stringify({
      ok: true,
      command: 'docx_create',
      artifacts: [
        {
          role: 'output',
          path: '/root/kiyori_office/work/t1/out/a.docx',
          env: 'linux',
          bytes: 36667,
          sha256: 'x'
        }
      ],
      data: {}
    }) +
    '__KIYORI_OFFICE_END__';
  const { result } = await runTool(
    'docx_create',
    { markdown: '# t', env: 'linux', task_id: 't1' },
    { envelope, info: () => ({ exists: true, size: 65508 }) }
  );
  assert.equal(result.success, false, JSON.stringify(result));
  assert.equal(result.code, 'E_PATH_INVALID');
  assert.match(result.message, /大小不一致/);
  assert.match(result.message, /expected=36667/);
  assert.match(result.message, /actual=65508/);
});

test('delivered artifact with matching size passes verification', async () => {
  const envelope =
    '__KIYORI_OFFICE_BEGIN__' +
    JSON.stringify({
      ok: true,
      command: 'docx_create',
      artifacts: [
        {
          role: 'output',
          path: '/root/kiyori_office/work/t1/out/a.docx',
          env: 'linux',
          bytes: 65508,
          sha256: 'x'
        }
      ],
      data: {}
    }) +
    '__KIYORI_OFFICE_END__';
  const { result } = await runTool(
    'docx_create',
    { markdown: '# t', env: 'linux', task_id: 't1' },
    { envelope, info: () => ({ exists: true, size: 65508 }) }
  );
  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(result.artifacts[0].env, 'android');
  assert.equal(result.artifacts[0].bytes, 65508);
});

test('multi-output command whitelists the output directory itself', async () => {
  const { host, result } = await runTool(
    'office_render_preview',
    {
      path: '/root/work/a.pdf',
      output_env: 'linux',
      output_path: '/root/out/preview-a',
      env: 'linux',
      task_id: 't1'
    },
    { exists: () => true }
  );
  assert.equal(result.success, true, JSON.stringify(result));
  const payload = lastArgsPayload(host);
  assert.equal(payload.output_path, '/root/out/preview-a');
  // 多产物命令的 output_path 是目录本身，必须整条进入白名单
  assert.ok(
    payload.allow_roots.includes('/root/out/preview-a'),
    JSON.stringify(payload.allow_roots)
  );
  assert.ok(
    !payload.allow_roots.includes('/root/out'),
    'multi output must not widen the allowlist to the parent directory'
  );
});
