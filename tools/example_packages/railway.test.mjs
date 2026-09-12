import assert from 'node:assert/strict';
import test from 'node:test';
import { loadRailway, stationSource, directInit, transferInit, date, query, ticket, directBody, transferRow, transferBody } from './railway_test_support.mjs';

test('Shanghai date has no network dependency and crosses the UTC day correctly', async () => {
    const host = await loadRailway([], { now: Date.parse('2026-09-12T18:00:00Z') });
    assert.equal((await host.invoke('get_current_date')).data, '2026-09-13');
    assert.equal(host.calls.length, 0);
});
test('station catalog is independent of transfer login and serves all lookup APIs', async () => {
    const host = await loadRailway([stationSource]);
    assert.equal((await host.invoke('get_stations_code_in_city', { city: ' 北京市 ' })).data.length, 2);
    const names = await host.invoke('get_station_code_by_names', { station_names: ' 北京南站 |上海虹桥 |测试站' });
    assert.equal(names.data['北京南'].station_code, 'VNP');
    assert.equal(names.data['测试站'].station_code, 'TST');
    const cities = await host.invoke('get_station_code_of_citys', { citys: '北京|成都|不存在' });
    assert.equal(cities.data['北京'].scope, 'station');
    assert.equal(cities.data['成都'].candidates[0].station_code, 'ICW');
    assert.ok(cities.data['不存在'].error);
    assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: ' vnp ' })).data.station_name, '北京南');
    assert.equal(host.calls.length, 1);
});
test('catalog records use @ boundaries and never inject an invented station', async () => {
    const source = stationSource.replace('北京|||@', '北京|extra|new|another|@');
    const host = await loadRailway([source]);
    assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: 'ICW' })).success, true);
    assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: 'WEI' })).error.code, 'STATION_NOT_FOUND');
});
test('concurrent station lookups share initialization and failures can be explicitly retried', async () => {
    const host = await loadRailway([new Error('PRIVATE_TRANSPORT'), stationSource]);
    await Promise.all([host.exports.get_station_by_telecode({ station_telecode: 'VNP' }), host.exports.get_station_by_telecode({ station_telecode: 'AOH' })]);
    assert.equal(host.calls.length, 1);
    assert.equal(host.completions.length, 2);
    assert.ok(host.completions.every(x => x.error.code === 'NETWORK_ERROR'));
    assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: 'VNP' })).success, true);
    assert.doesNotMatch(JSON.stringify(host.logs), /PRIVATE_TRANSPORT/);
});
test('malformed or empty station data is not cached as a successful catalog', async () => {
    for (const source of ['<html>login</html>', "var station_names='';", "var station_names='@broken';"]) {
        const host = await loadRailway([source, stationSource]);
        assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: 'VNP' })).error.code, 'INVALID_RESPONSE');
        assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: 'VNP' })).success, true);
    }
});
test('all query input errors fail before touching network or creating a session', async () => {
    const host = await loadRailway();
    for (const patch of [{ date: '2099-02-30' }, { date: 'yesterday' }, { date: '2000-01-01' }, { from_station: '北京' }, { from_station: 'AOH' }, { train_filter_flags: 'XYZ' }, { sort_flag: 'magic' }, { sort_reverse: 'false' }, { limited_num: -1 }, { limited_num: 1.5 }, { limited_num: NaN }, { limited_num: 1001 }]) {
        assert.equal((await host.invoke('get_tickets', { ...query, ...patch })).error.code, 'INVALID_ARGUMENT');
    }
    assert.equal((await host.invoke('get_interline_tickets', { ...query, middle_station: '北京' })).error.code, 'INVALID_ARGUMENT');
    assert.equal((await host.invoke('get_interline_tickets', { ...query, show_wz: 'false' })).error.code, 'INVALID_ARGUMENT');
    assert.equal((await host.invoke('get_station_code_by_names', { station_names: '北京||上海' })).error.code, 'INVALID_ARGUMENT');
    assert.equal(host.calls.length, 0);
});
test('direct query follows official endpoint and preserves session cookies without logging them', async () => {
    const host = await loadRailway([stationSource, { httpBody: directInit, httpHeaders: { 'Set-Cookie': 'a=abc==; Path=/, b=two; Expires=Wed, 21 Oct 2099 07:28:00 GMT; Path=/' } }, directBody()]);
    const result = await host.invoke('get_tickets', { ...query, train_filter_flags: '' });
    assert.equal(result.success, true);
    assert.match(host.calls[2].url, /leftTicket\/queryG\?/);
    assert.equal(host.calls[2].headers.Cookie, 'a=abc==; b=two');
    assert.ok(host.clients.every(x => x.followRedirects === false && x.retryOnConnectionFailure === false && x.readTimeout <= 20000));
    assert.doesNotMatch(JSON.stringify(host.logs), /abc==|b=two/);
});
test('missing price data preserves seat availability and uses the queried boarding date', async () => {
    const host = await loadRailway([stationSource, directInit, directBody([ticket({ 8: '23:20', 9: '01:20', 10: '02:00' })])]);
    const result = await host.invoke('get_tickets', query);
    assert.equal(result.details.trains[0].start_date, date);
    assert.equal(result.details.trains[0].arrive_date, '2099-01-03');
    assert.equal(result.details.trains[0].seats[0].price, null);
    assert.match(result.data, /二等座: 有票 票价未提供/);
    assert.doesNotMatch(result.data, /NaN|undefined/);
});
test('official 10-character seat prices and no-seat records decode separately', async () => {
    const host = await loadRailway([stationSource, directInit, directBody([ticket({ 26: '有', 39: '9187000006M088300021O052500021O052503026', 54: '90068M0070O0066W0066' })])]);
    const result = await host.invoke('get_tickets', query);
    assert.deepEqual(result.details.trains[0].seats.map(x => [x.seat_name, x.price]), [['商务座', 1870], ['一等座', 883], ['二等座', 525], ['无座', 525]]);
});
test('date and duration sorting works across midnight without parsing local date strings', async () => {
    const rows = [ticket({ 2: 'lateTrain001', 8: '23:00', 9: '01:00', 10: '02:00' }), ticket({ 2: 'earlyTrain01', 8: '04:00', 9: '10:00', 10: '06:00' })];
    const host = await loadRailway([stationSource, directInit, directBody(rows), directInit, directBody(rows)]);
    assert.equal((await host.invoke('get_tickets', { ...query, sort_flag: 'arriveTime', limited_num: 1 })).details.trains[0].train_no, 'earlyTrain01');
    const result = await host.invoke('get_tickets', { ...query, sort_flag: 'duration', sort_reverse: true });
    assert.equal(result.details.trains[0].train_no, 'earlyTrain01');
});
test('C intercity belongs to G compatibility group but not other trains', async () => {
    const host = await loadRailway([stationSource, directInit, directBody([ticket({ 3: 'C123' })]), directInit, directBody([ticket({ 3: 'C123' })])]);
    assert.equal((await host.invoke('get_tickets', { ...query, train_filter_flags: 'G' })).details.result_count, 1);
    assert.equal((await host.invoke('get_tickets', { ...query, train_filter_flags: 'O' })).details.result_count, 0);
});
test('empty lists succeed; service errors, HTML, truncated rows and null rejections fail explicitly', async () => {
    for (const [response, expected] of [[directBody([]), null], [{ status: false, messages: ['denied'] }, 'SERVICE_REJECTED'], ['<html>blocked</html>', 'INVALID_RESPONSE'], [directBody(['short|row']), 'INVALID_RESPONSE'], [null, 'NETWORK_ERROR'], [{ httpStatus: 429, httpBody: '' }, 'HTTP_ERROR']]) {
        const host = await loadRailway([stationSource, directInit, response]);
        const result = await host.invoke('get_tickets', query);
        assert.equal(result.success, expected === null);
        if (expected) assert.equal(result.error.code, expected);
        else assert.equal(result.details.result_count, 0);
        assert.equal(host.calls.length, 3);
    }
});
test('c_url routing is explicit and limited to one same-origin official endpoint', async () => {
    const host = await loadRailway([stationSource, directInit, { c_url: 'leftTicket/queryZ' }, directBody()]);
    assert.equal((await host.invoke('get_tickets', query)).success, true);
    assert.match(host.calls[3].url, /queryZ\?/);
    for (const c_url of ['https://other.test/steal', '../login', 'leftTicket/queryG']) {
        const bad = await loadRailway([stationSource, directInit, { c_url }]);
        assert.equal((await bad.invoke('get_tickets', query)).error.code, 'INVALID_RESPONSE');
        assert.equal(bad.calls.length, 3);
    }
});
test('transfer login does not disable station or direct queries and never follows auth redirects', async () => {
    for (const login of ['<html>passport login 登录</html>', { httpStatus: 302, httpBody: '', httpHeaders: { Location: 'https://kyfw.12306.cn/otn/resources/login.html' } }]) {
        const host = await loadRailway([stationSource, login, directInit, directBody()]);
        assert.equal((await host.invoke('get_interline_tickets', query)).error.code, 'AUTH_REQUIRED');
        assert.equal((await host.invoke('get_station_by_telecode', { station_telecode: 'VNP' })).success, true);
        assert.equal((await host.invoke('get_tickets', query)).success, true);
        assert.equal(host.calls.length, 4);
    }
});
test('transfer filters every leg before limiting and continues to a matching later page', async () => {
    const first = transferRow({}, [{ station_train_code: 'K1', train_no: '2400000K1000' }]);
    const host = await loadRailway([stationSource, transferInit, transferBody([first], 1, true), transferBody([transferRow()], 2)]);
    const result = await host.invoke('get_interline_tickets', { ...query, train_filter_flags: 'G', limited_num: 1 });
    assert.equal(result.success, true);
    assert.equal(result.details.pages, 2);
    assert.equal(result.details.transfers[0].ticketList[0].start_train_code, 'G531');
    assert.equal(result.details.scan_complete, true);
});
test('transfer sorting scans beyond the first requested result and parses exact-hour durations', async () => {
    const host = await loadRailway([stationSource, transferInit, transferBody([transferRow()], 1, true), transferBody([transferRow({ all_lishi: '3小时', all_lishi_minutes: undefined }, [{ train_no: 'another00001', arrive_time: '09:00', lishi: '01:00' }, { start_time: '09:00', arrive_time: '11:00' }])], 2)]);
    const result = await host.invoke('get_interline_tickets', { ...query, limited_num: 1, sort_flag: 'duration' });
    assert.equal(result.details.transfers[0].lishi, '03:00');
    assert.equal(result.details.available_count, 2);
    assert.equal(result.details.truncated, true);
});
test('transfer pagination detects repeated cursors and repeated routes', async () => {
    for (const pages of [[transferBody([transferRow()], 0, true)], [transferBody([transferRow()], 1, true), transferBody([transferRow()], 2, true)]]) {
        const host = await loadRailway([stationSource, transferInit, ...pages]);
        assert.equal((await host.invoke('get_interline_tickets', { ...query, limited_num: 10 })).error.code, 'PAGINATION_STALLED');
    }
});
test('transfer scan is bounded and exposes incomplete coverage', async () => {
    const pages = Array.from({ length: 10 }, (_, index) => transferBody([transferRow({}, [{ train_no: `train${index}000000` }])], index + 1, true));
    const host = await loadRailway([stationSource, transferInit, ...pages]);
    const result = await host.invoke('get_interline_tickets', { ...query, sort_flag: 'duration' });
    assert.equal(result.success, true);
    assert.equal(result.details.pages, 10);
    assert.equal(result.details.scan_complete, false);
    assert.equal(host.calls.length, 12);
});

test('transfer second-leg boarding date is required and temporal order is validated', async () => {
    for (const middle_date of ['', '2099-01-01']) {
        const host = await loadRailway([stationSource, transferInit, transferBody([transferRow({ middle_date })])]);
        assert.equal((await host.invoke('get_interline_tickets', query)).error.code, 'INVALID_RESPONSE');
    }
    const host = await loadRailway([stationSource, transferInit, transferBody([transferRow({ middle_date: '20990103', all_lishi_minutes: 1740 })])]);
    const result = await host.invoke('get_interline_tickets', query);
    assert.equal(result.details.transfers[0].ticketList[1].start_date, '2099-01-03');
});

test('unexpected redirects and inconsistent train timelines cannot masquerade as login or valid schedules', async () => {
    const redirect = await loadRailway([stationSource, { httpStatus: 302, httpBody: '', httpHeaders: { Location: 'https://example.test/unexpected' } }]);
    assert.equal((await redirect.invoke('get_tickets', query)).error.code, 'REDIRECT_REJECTED');
    assert.equal(redirect.calls.length, 2);
    const direct = await loadRailway([stationSource, directInit, directBody([ticket({ 10: '01:00' })])]);
    assert.equal((await direct.invoke('get_tickets', query)).error.code, 'INVALID_RESPONSE');
    const transfer = await loadRailway([stationSource, transferInit, transferBody([transferRow({ all_lishi_minutes: 1 })])]);
    assert.equal((await transfer.invoke('get_interline_tickets', query)).error.code, 'INVALID_RESPONSE');
});

test('transfer refuses oversized server pages before building unbounded results', async () => {
    const host = await loadRailway([stationSource, transferInit, transferBody(Array(501).fill({}))]);
    assert.equal((await host.invoke('get_interline_tickets', query)).error.code, 'RESPONSE_TOO_LARGE');
});
test('route lookup preserves real arrival/departure times and validates its input first', async () => {
    const stop = { station_name: '北京南', station_no: '01', arrive_time: '----', start_time: '06:08', stopover_time: '----' };
    const host = await loadRailway([stationSource, directInit, { status: true, data: { data: [stop] } }]);
    const params = { train_no: '240000G53106', from_station_telecode: 'VNP', to_station_telecode: 'AOH', depart_date: date };
    assert.equal((await host.invoke('get_train_route_stations', { ...params, train_no: 'G531' })).error.code, 'INVALID_ARGUMENT');
    assert.equal(host.calls.length, 0);
    const result = await host.invoke('get_train_route_stations', params);
    assert.deepEqual(result.data[0], { ...stop, station_no: 1 });
});
test('completion callback failure never causes a second terminal callback', async () => {
    const host = await loadRailway([], { completeThrows: true });
    await assert.rejects(host.exports.get_current_date({}), /callback failed/);
    assert.equal(host.completions.length, 1);
});
