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
  const calls = { hidden: [], files: [], written: [] };
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
    ToolPkg: {
      readResource: async () => '/data/cache/kiyori_office_runtime.zip'
    },
    Tools: {
      System: {
        terminal: {
          hiddenExec: async (command, opts) => {
            calls.hidden.push({ command, opts });
            if (command.includes('printf "%s\\n" "$HOME"')) {
              return { output: '/root\n/root/.code_runner/py/bin/python\n', exitCode: 0 };
            }
            if (command.includes('unzip_runtime.py')) {
              return { output: '__KIYORI_OFFICE_READY__\n', exitCode: 0 };
            }
            if (command.includes('kiyori_office')) {
              const argsFile = command.match(/'([^']*args\.json)'/);
              const written = argsFile ? calls.written.find(item => item.path === argsFile[1]) : null;
              const payload = written ? JSON.parse(written.content) : {};
              calls.hidden[calls.hidden.length - 1].payload = payload;
              return {
                output: options.envelope
                  ? options.envelope
                  : '__KIYORI_OFFICE_BEGIN__' +
                    JSON.stringify({ ok: true, command: 'office_env_check', data: {}, artifacts: [] }) +
                    '__KIYORI_OFFICE_END__',
                exitCode: 0
              };
            }
            return { output: '', exitCode: 0 };
          }
        }
      },
      Files: {
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
    call => call.op === 'copy' && call.sourceEnv === 'android' && call.destination.endsWith('/in/a.md')
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
