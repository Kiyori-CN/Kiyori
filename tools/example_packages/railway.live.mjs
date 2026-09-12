// 显式运行的官网只读验收。无账号、无购票；不在普通测试中自动联网。
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import ts from 'typescript';

if (!process.argv.includes('--live')) throw new Error('Use --live to explicitly run public read-only 12306 queries.');
const temporary = await mkdtemp(path.join(tmpdir(), 'kiyori-railway-live-'));
const calls = [], completions = [], results = [];
let counter = 0;
function command(args) {
    return new Promise((resolve, reject) => {
        const child = spawn(process.platform === 'win32' ? 'curl.exe' : 'curl', args, { windowsHide: true });
        let output = '';
        child.stdout.on('data', data => output += data);
        child.stderr.resume();
        child.on('error', reject);
        child.on('close', code => code === 0 ? resolve(output) : reject(new Error(`curl failed (${code})`)));
    });
}
try {
    const context = vm.createContext({ exports: {}, console: { log() {}, warn() {}, error() {} }, complete: value => completions.push(value), toolCall: async ({ name, params }) => {
        assert.equal(name, 'http_request');
        const url = new URL(params.url);
        assert.equal(url.origin, 'https://kyfw.12306.cn');
        assert.equal(params.method, 'GET');
        assert.equal(params.follow_redirects, false);
        assert.equal(params.retry_on_connection_failure, false);
        const bodyFile = path.join(temporary, `body-${counter}`), headerFile = path.join(temporary, `headers-${counter++}`);
        const args = ['--silent', '--show-error', '--max-time', '25', '--connect-timeout', '10', '--output', bodyFile, '--dump-header', headerFile, '--write-out', '%{http_code}'];
        for (const [key, value] of Object.entries(JSON.parse(params.headers))) args.push('--header', `${key}: ${value}`);
        args.push(params.url);
        const statusCode = Number(await command(args));
        const content = await readFile(bodyFile, 'utf8');
        const rawHeaders = await readFile(headerFile, 'utf8');
        const headers = {};
        for (const line of rawHeaders.split(/\r?\n/)) {
            const separator = line.indexOf(':');
            if (separator <= 0) continue;
            const key = line.slice(0, separator).toLowerCase(), value = line.slice(separator + 1).trim();
            headers[key] = headers[key] ? `${headers[key]}, ${value}` : value;
        }
        calls.push({ path: url.pathname, status: statusCode, bytes: Buffer.byteLength(content) });
        return { url: params.url, statusCode, statusMessage: '', headers, content, contentType: headers['content-type'] ?? '' };
    } });
    // 使用 APK 内同一 OkHttp facade，避免模拟 builder 掩盖参数映射错误。
    vm.runInContext(await readFile(new URL('../../app/src/main/assets/js/OkHttp3.js', import.meta.url), 'utf8'), context);
    const source = await readFile(new URL('../../examples/12306.ts', import.meta.url), 'utf8');
    vm.runInContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText, context);
    async function invoke(tool, params = {}) {
        const before = completions.length;
        await context.exports[tool](params);
        assert.equal(completions.length, before + 1);
        const result = completions.at(-1);
        results.push({ tool, success: result.success, error: result.error?.code ?? null, count: result.details?.result_count ?? (Array.isArray(result.data) ? result.data.length : undefined) });
        return result;
    }
    const current = await invoke('get_current_date');
    assert.equal(current.success, true);
    const date = new Date(`${current.data}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + 1);
    const departure = date.toISOString().slice(0, 10);
    for (const [tool, params] of [
        ['get_stations_code_in_city', { city: '北京' }],
        ['get_station_code_of_citys', { citys: '北京|上海|成都' }],
        ['get_station_code_by_names', { station_names: '北京南|上海虹桥|成都东' }],
        ['get_station_by_telecode', { station_telecode: 'VNP' }],
    ]) assert.equal((await invoke(tool, params)).success, true, tool);
    const direct = await invoke('get_tickets', { date: departure, from_station: 'VNP', to_station: 'AOH', limited_num: 3, sort_flag: 'startTime' });
    assert.equal(direct.success, true, direct.message);
    assert.ok(direct.details.trains.length > 0);
    const selected = direct.details.trains[0];
    assert.equal((await invoke('get_train_route_stations', { train_no: selected.train_no, from_station_telecode: selected.from_station_telecode, to_station_telecode: selected.to_station_telecode, depart_date: departure })).success, true);
    const transfer = await invoke('get_interline_tickets', { date: departure, from_station: 'VNP', to_station: 'AOH', limited_num: 1 });
    assert.ok(transfer.success || transfer.error?.code === 'AUTH_REQUIRED', transfer.message);
    const report = { observed_at: new Date().toISOString(), departure, results, calls, device_verified: false, transfer_data_verified: transfer.success };
    const output = process.argv.find(arg => arg.startsWith('--report='))?.slice('--report='.length);
    if (output) await writeFile(output, JSON.stringify(report, null, 2), 'utf8');
    console.log(JSON.stringify(report, null, 2));
} finally {
    // 仅清理本次创建的临时目录；不保留匿名会话 Cookie 或请求原始正文。
    await rm(temporary, { recursive: true, force: true });
}
