import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';

export const stationSource = "var station_names='@bjn|北京南|VNP|beijingnan|bjn|1|1|北京|||@bj|北京|BJP|beijing|bj|2|1|北京|||@shhq|上海虹桥|AOH|shanghaihongqiao|shhq|3|2|上海|||@cd|成都东|ICW|chengdudong|cdd|4|3|成都|||@nn|南宁|NNZ|nanning|nn|5|4|南宁|||@test|测试站|TST|ceshizhan|csz|6|5|测试|||';";
export const directInit = "var CLeftTicketUrl = 'leftTicket/queryG';";
export const transferInit = "var lc_search_url = '/otn/lcQuery/query';";
export const date = '2099-01-02';
export const query = { date, from_station: 'VNP', to_station: 'AOH' };

export function ticket(overrides = {}) {
    const fields = Array(58).fill('');
    Object.assign(fields, { 2: '240000G53106', 3: 'G531', 4: 'VNP', 5: 'AOH', 6: 'VNP', 7: 'AOH', 8: '06:08', 9: '12:04', 10: '05:56', 11: 'Y', 13: '20990101', 30: '有', 31: '无', 32: '6' }, overrides);
    return fields.join('|');
}
export function directBody(rows = [ticket()]) { return { status: true, data: { result: rows, map: { VNP: '北京南', AOH: '上海虹桥' } } }; }
export function transferRow(overrides = {}, legOverrides = []) {
    const leg = { train_no: '240000G53106', station_train_code: 'G531', from_station_telecode: 'VNP', to_station_telecode: 'BJP', from_station_name: '北京南', to_station_name: '北京', start_time: '08:00', arrive_time: '10:00', lishi: '02:00', start_train_date: '20990101', ze_num: '有' };
    return { all_lishi: '5小时', all_lishi_minutes: 300, train_date: date, middle_date: date, same_station: '0', wait_time: '1小时', fullList: [{ ...leg, ...legOverrides[0] }, { ...leg, train_no: '240000G53306', station_train_code: 'G533', from_station_telecode: 'BJP', to_station_telecode: 'AOH', from_station_name: '北京', to_station_name: '上海虹桥', start_time: '11:00', arrive_time: '13:00', ...legOverrides[1] }], ...overrides };
}
export function transferBody(rows, cursor = 1, more = false) { return { status: true, data: { middleList: rows, result_index: cursor, can_query: more ? 'Y' : 'N' } }; }

export async function loadRailway(responses = [], options = {}) {
    const calls = [], completions = [], logs = [], clients = [];
    const host = {
        newBuilder() {
            const settings = {};
            const builder = Object.fromEntries(['connectTimeout', 'readTimeout', 'writeTimeout', 'followRedirects', 'retryOnConnectionFailure'].map(key => [key, value => { settings[key] = value; return builder; }]));
            builder.build = () => {
                clients.push(settings);
                return { newRequest() {
                    const request = {};
                    const requestBuilder = Object.fromEntries(['url', 'method', 'headers'].map(key => [key, value => { request[key] = value; return requestBuilder; }]));
                    requestBuilder.build = () => ({ execute: async () => {
                        calls.push(request);
                        if (options.execute) return options.execute(request);
                        assert.ok(responses.length, 'unexpected HTTP request');
                        const next = responses.shift();
                        if (next instanceof Error || next === null) throw next;
                        const body = next?.httpBody ?? next;
                        const content = typeof body === 'string' ? body : JSON.stringify(body);
                        const statusCode = next?.httpStatus ?? 200;
                        return { content, statusCode, headers: next?.httpHeaders ?? {}, isSuccessful: () => statusCode >= 200 && statusCode < 300 };
                    } });
                    return requestBuilder;
                } };
            };
            return builder;
        },
    };
    const context = vm.createContext({ exports: {}, OkHttp: host,
        complete: value => { completions.push(value); if (options.completeThrows) throw new Error('callback failed'); },
        console: Object.fromEntries(['log', 'warn', 'error'].map(key => [key, (...args) => logs.push(args)])),
        ...(options.now === undefined ? {} : { Date: class extends Date { static now() { return options.now; } } }),
    });
    const source = await readFile(new URL('../../examples/12306.ts', import.meta.url), 'utf8');
    vm.runInContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText, context, { timeout: 1000 });
    return { calls, clients, logs, completions,
        async invoke(name, params = {}) {
            const before = completions.length;
            await context.exports[name](params);
            assert.equal(completions.length, before + 1, 'one terminal result per sequential call');
            return JSON.parse(JSON.stringify(completions.at(-1)));
        },
        exports: context.exports,
    };
}
