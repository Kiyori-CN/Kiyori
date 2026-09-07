import assert from 'node:assert/strict';
import vm from 'node:vm';

// stdin 是 JVM 测试直接调用生产 Kotlin generator 得到的源码，避免复制一份待测 bootstrap。
let source = '';
for await (const chunk of process.stdin) source += chunk;
function page(url) {
    const events = [];
    const nodes = [];
    const listeners = new Map();
    const bridge = {postMessage(raw) { events.push(JSON.parse(raw)); }};
    const parent = {appendChild(node) { nodes.push(node); }};
    const document = {
        readyState: 'complete', head: parent, documentElement: parent, body: parent,
        createElement(tag) { return {tag, hidden: false, remove() { const i = nodes.indexOf(this); if (i >= 0) nodes.splice(i, 1); }}; },
        addEventListener(name, callback) { listeners.set(name, callback); },
    };
    const sandbox = {document, location: {href: url}, __testBridge: bridge,
        addEventListener(name, callback) { listeners.set(name, callback); },
        removeEventListener(name, callback) { if (listeners.get(name) === callback) listeners.delete(name); },
        setTimeout, clearTimeout};
    sandbox.window = sandbox; sandbox.top = sandbox;
    vm.runInNewContext(source, sandbox, {timeout: 2000});
    const doc = events[0]?.doc;
    const send = (type, documentId = doc) => bridge.onmessage({data: JSON.stringify({type, doc: documentId})});
    return {events, nodes, send, listeners};
}
const main = page('https://example.com/article');
assert.equal(main.events[0].state, 'READY');
assert.equal(main.nodes.length, 0, 'No code executes before host authorization');
main.send('start', 'stale-document');
assert.equal(main.nodes.length, 0);
main.send('start');
assert.equal(main.nodes.length, 2, 'CSS and marker are installed');
assert.equal(main.events.at(-1).state, 'SUCCESS');
main.send('start');
assert.equal(main.nodes.length, 2, 'Duplicate authorization cannot run twice');
main.send('action');
await new Promise(setImmediate);
assert.equal(main.events.at(-1).state, 'ACTION_SUCCESS');
assert.equal(main.nodes.find(node => node.tag === 'div').hidden, true);
main.send('stop');
assert.equal(main.nodes.length, 0, 'Lifecycle cleanup removes marker and stylesheet');
assert.equal(main.listeners.size, 0, 'Lifecycle cleanup removes runtime error listeners');
const count = main.events.length;
main.send('action');
assert.equal(main.events.length, count, 'Disabled document cannot invoke actions');
assert.equal(page('https://example.com.evil.test/article').events.length, 0);
const errorPage = page('https://example.com/article');
errorPage.send('start');
errorPage.listeners.get('unhandledrejection')({reason: new Error('async failure')});
assert.equal(errorPage.events.at(-1).state, 'ERROR');
assert.equal(errorPage.events.at(-1).detail, 'async failure');
process.stdout.write('PASS: authorization, matching, execution, action, cleanup, duplicate/stale commands, async errors\n');
