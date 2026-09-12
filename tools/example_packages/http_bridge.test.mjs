import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../../app/src/main/assets/js/OkHttp3.js', import.meta.url), 'utf8');
function bridge(handler = async () => ({ statusCode: 200, content: 'ok', headers: {} })) {
    const calls = [];
    const context = vm.createContext({ toolCall: async request => { calls.push(request); return handler(request); } });
    vm.runInContext(source, context);
    return { api: context.OkHttp, calls };
}

test('ordinary and streamed HTTP requests preserve retry, redirect and timeout settings', async () => {
    const { api, calls } = bridge();
    const client = api.newBuilder().retryOnConnectionFailure(false).followRedirects(false)
        .connectTimeout(1200).readTimeout(2300).writeTimeout(3400).build();
    await client.post('https://example.test/action', 'payload');
    const onIntermediateResult = () => {};
    await client.newRequest().url('https://example.test/stream').build().execute({ onIntermediateResult });
    for (const call of calls) {
        assert.equal(call.name, 'http_request');
        assert.equal(call.params.retry_on_connection_failure, false);
        assert.equal(call.params.follow_redirects, false);
        assert.deepEqual([call.params.connect_timeout, call.params.read_timeout, call.params.write_timeout], [2, 3, 4]);
    }
    assert.equal(calls[0].params.body, 'payload');
    assert.equal(calls[1].params.stream, true);
    assert.equal(calls[1].onIntermediateResult, onIntermediateResult);
    await api.newClient().get('https://example.test/defaults');
    assert.equal(calls[2].params.retry_on_connection_failure, true);
});

test('HTTP transport failures are propagated after exactly one bridge submission', async () => {
    const { api, calls } = bridge(async () => { throw new Error('transport lost'); });
    await assert.rejects(api.newBuilder().retryOnConnectionFailure(false).build().post('https://example.test/action', 'body'), /transport lost/);
    assert.equal(calls.length, 1);
});

test('multipart maps text and files to the host contract and applies the same client policy', async () => {
    const { api, calls } = bridge();
    const client = api.newBuilder().retryOnConnectionFailure(false).followRedirects(false).readTimeout(4100)
        .addInterceptor(request => ({ ...request, headers: { ...request.headers, 'X-Fixture': 'applied' } })).build();
    await client.newRequest().url('https://example.test/upload').method('POST')
        .formParam('before', '中文').multipartParam('caption', 'hello')
        .multipartParam('document', '/storage/emulated/0/test.pdf', 'application/pdf')
        .formParam('after', '尾部').build().execute();
    const { name, params } = calls[0];
    assert.equal(name, 'multipart_request');
    assert.deepEqual(JSON.parse(params.form_data), { before: '中文', after: '尾部', caption: 'hello' });
    assert.deepEqual(JSON.parse(params.files), [{ field_name: 'document', file_path: '/storage/emulated/0/test.pdf', content_type: 'application/pdf' }]);
    assert.deepEqual(JSON.parse(params.headers), { 'X-Fixture': 'applied' });
    assert.equal(params.retry_on_connection_failure, false);
    assert.equal(params.follow_redirects, false);
    assert.equal(params.read_timeout, 5);
    assert.equal(params.fields, undefined);
    assert.equal(params.body, undefined);
});

test('multipart rejects unsupported streaming and ambiguous text fields before submission', async () => {
    const { api, calls } = bridge();
    const request = api.newClient().newRequest().url('https://example.test/upload').method('POST');
    await assert.rejects(request.multipartParam('name', 'first').build().execute({ onIntermediateResult() {} }), /do not support streaming/);
    await assert.rejects(request.multipartParam('name', 'second').build().execute(), /Duplicate multipart/);
    assert.equal(calls.length, 0);
});

test('response header values retain URLs, timestamps and empty values', async () => {
    const { api } = bridge(async () => ({ statusCode: 302, content: '', headers: 'Location: https://example.test:8443/login\r\nDate: Sat, 12 Sep 2026 04:00:00 GMT\r\nX-Empty:\r\nmalformed' }));
    const response = await api.newClient().get('https://example.test');
    assert.equal(response.headers.Location, 'https://example.test:8443/login');
    assert.equal(response.headers.Date, 'Sat, 12 Sep 2026 04:00:00 GMT');
    assert.equal(response.headers['X-Empty'], '');
    assert.equal(response.isSuccessful(), false);
});
