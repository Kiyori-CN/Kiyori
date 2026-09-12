import assert from 'node:assert/strict';
import vm from 'node:vm';

// JVM 传入生产 generator 的四种模式，夹具仅模拟 DOM/观察器，不复制待测算法。
let input = '';
for await (const chunk of process.stdin) input += chunk;
const scripts = JSON.parse(input);
function page({content, name = 'viewport', headReady = true, screenWidth = 390, child = false} = {}) {
    const observers = new Set();
    let queries = 0, writes = 0;
    const mutation = record => {
        for (const observer of observers) if (observer.active) observer.records.push(record);
    };
    class Element {
        constructor(tag) { this.localName = tag; this.nodeType = 1; this.children = []; this.parent = null; this.attrs = new Map(); }
        get isConnected() { return this === document || Boolean(this.parent?.isConnected); }
        appendChild(node) {
            node.remove(); this.children.push(node); node.parent = this;
            mutation({type: 'childList', target: this, addedNodes: [node], removedNodes: []});
            return node;
        }
        remove() {
            if (!this.parent) return;
            const parent = this.parent;
            parent.children.splice(parent.children.indexOf(this), 1); this.parent = null;
            mutation({type: 'childList', target: parent, addedNodes: [], removedNodes: [this]});
        }
        setAttribute(key, value) { this.attrs.set(key, String(value)); writes++; mutation({type: 'attributes', target: this, attributeName: key}); }
        removeAttribute(key) { this.attrs.delete(key); mutation({type: 'attributes', target: this, attributeName: key}); }
        getAttribute(key) { return this.attrs.get(key) ?? null; }
        querySelectorAll(selector) {
            assert.equal(selector, 'meta[name]');
            return this.children.flatMap(node => [
                ...(node instanceof Meta && node.attrs.has('name') ? [node] : []), ...node.querySelectorAll(selector),
            ]);
        }
    }
    class Meta extends Element { constructor() { super('meta'); } }
    const document = new Element('document');
    document.createElement = tag => tag === 'meta' ? new Meta() : new Element(tag);
    const query = document.querySelectorAll.bind(document);
    document.querySelectorAll = selector => { queries++; return query(selector); };
    function addHead() {
        document.head = document.appendChild(new Element('head'));
    }
    if (headReady) addHead();
    function addMeta(value, metaName = 'viewport') {
        const meta = new Meta(); meta.setAttribute('name', metaName);
        if (value !== null) meta.setAttribute('content', value);
        document.head.appendChild(meta); return meta;
    }
    const meta = content === undefined ? null : addMeta(content, name);
    class MutationObserver {
        constructor(callback) { this.callback = callback; this.active = false; this.records = []; observers.add(this); }
        observe() { this.active = true; }
        disconnect() { this.active = false; this.records = []; }
    }
    const sandbox = {document, HTMLMetaElement: Meta, MutationObserver, screen: {width: screenWidth}};
    sandbox.window = sandbox; sandbox.top = child ? {} : sandbox;
    const context = vm.createContext(sandbox);
    const run = mode => vm.runInContext(scripts[mode], context, {timeout: 2000});
    function flush() {
        for (let turn = 0; turn < 10; turn++) {
            let delivered = false;
            for (const observer of observers) {
                if (!observer.records.length) continue;
                const records = observer.records.splice(0); observer.callback(records); delivered = true;
            }
            if (!delivered) return;
        }
        assert.fail('Mutation observer did not converge');
    }
    return {document, meta, run, flush, addMeta, addHead, sandbox,
        metas: () => query('meta[name]').filter(meta => meta.getAttribute('name').toLowerCase() === 'viewport'),
        queries: () => queries, writes: () => writes,
        activeObservers: () => [...observers].filter(observer => observer.active).length};
}
const mobile = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover';
for (const mode of ['desktop', 'both']) {
    const p = page({content: mobile}); p.run(mode);
    assert.match(p.meta.getAttribute('content'), /width=980/);
    assert.match(p.meta.getAttribute('content'), /minimum-scale=0\.1/);
    assert.match(p.meta.getAttribute('content'), /viewport-fit=cover/);
    assert.doesNotMatch(p.meta.getAttribute('content'), /initial-scale|device-width/);
    const writes = p.writes(); p.run(mode); p.flush();
    assert.equal(p.writes(), writes, 'Repeated lifecycle application must not rewrite viewport');
    assert.equal(p.activeObservers(), 1);
    p.run('off'); assert.equal(p.meta.getAttribute('content'), mobile); assert.equal(p.activeObservers(), 0);
}
const zoom = page({content: mobile}); zoom.run('zoom');
assert.match(zoom.meta.getAttribute('content'), /width=device-width/);
assert.match(zoom.meta.getAttribute('content'), /initial-scale=1/);
zoom.run('off'); assert.equal(zoom.meta.getAttribute('content'), mobile);
const dynamic = page({content: mobile, name: 'VIEWPORT'}); dynamic.run('desktop');
dynamic.meta.setAttribute('content', 'width=420; initial-scale=2; viewport-fit=contain'); dynamic.flush();
assert.match(dynamic.meta.getAttribute('content'), /width=980/);
dynamic.run('zoom'); assert.match(dynamic.meta.getAttribute('content'), /width=420/);
dynamic.run('off'); assert.equal(dynamic.meta.getAttribute('content'), 'width=420; initial-scale=2; viewport-fit=contain');
const pending = page({content: mobile}); pending.run('desktop');
pending.meta.setAttribute('content', 'width=700'); pending.run('off');
assert.equal(pending.meta.getAttribute('content'), 'width=700', 'Preserve author change before observer callback');
const empty = page({headReady: false}); empty.run('both'); empty.addHead(); empty.flush();
assert.equal(empty.metas().length, 1); assert.match(empty.metas()[0].getAttribute('content'), /width=980/);
const author = empty.addMeta('width=device-width'); empty.flush(); assert.equal(empty.metas().length, 1);
assert.equal(empty.metas()[0], author); empty.run('off'); assert.equal(author.getAttribute('content'), 'width=device-width');
const removed = page({content: mobile}); removed.run('desktop'); removed.meta.remove(); removed.flush();
assert.equal(removed.meta.getAttribute('content'), mobile); removed.run('off'); assert.equal(removed.metas().length, 0);
const renamed = page({content: mobile}); renamed.run('desktop'); renamed.meta.setAttribute('name', 'description'); renamed.flush();
assert.equal(renamed.meta.getAttribute('content'), mobile); renamed.run('off');
const adopted = page(); adopted.run('desktop'); const created = adopted.metas()[0];
created.setAttribute('content', 'width=800'); adopted.flush(); adopted.run('off');
assert.equal(created.isConnected, true); assert.equal(created.getAttribute('content'), 'width=800');
const large = page({content: mobile, screenWidth: 1280}); large.run('desktop');
assert.match(large.meta.getAttribute('content'), /width=1280/);
const queries = large.queries(); large.document.appendChild(large.document.createElement('div')); large.flush();
assert.equal(large.queries(), queries, 'Unrelated DOM additions must not rescan document');
const frame = page({content: mobile, child: true}); frame.run('desktop');
assert.equal(frame.meta.getAttribute('content'), mobile); assert.equal(frame.activeObservers(), 0);
process.stdout.write('PASS: desktop, zoom, combined, restore, idempotence, dynamic authors, pending changes, late head, replacement, removal, rename, adopted meta, wide screen, mutation filtering, iframe\n');
