'use strict';

const { assert, assertEq, test, runTests } = require('../../../../../../../../../lib/harness');

const File = Java.type('java.io.File');
const FileOutputStream = Java.type('java.io.FileOutputStream');
const OutputStreamWriter = Java.type('java.io.OutputStreamWriter');
const Charset = Java.type('java.nio.charset.Charset');

const UTF8 = Charset.forName('UTF-8');
const BASE_DIR = '/sdcard/Download/Kiyori/browser_tool_suite';
const PAGE_PATH = BASE_DIR + '/browser_tool_suite.html';
const PAGE_URL = 'file://' + PAGE_PATH;
const STRICT_CSP_PAGE_PATH = BASE_DIR + '/browser_tool_strict_csp.html';
const STRICT_CSP_PAGE_URL = 'file://' + STRICT_CSP_PAGE_PATH;
const UPLOAD_PATH = BASE_DIR + '/upload_payload.txt';
const DELAYED_TEXT = 'READY_MARK_' + Date.now();

function ensureDir(path) {
  const dir = new File(String(path));
  if (!dir.exists()) {
    dir.mkdirs();
  }
  return dir;
}

function writeText(path, text) {
  const file = new File(String(path));
  const parent = file.getParentFile();
  if (parent != null) {
    ensureDir(parent.getAbsolutePath());
  }
  const writer = new OutputStreamWriter(new FileOutputStream(file, false), UTF8);
  try {
    writer.write(String(text));
  } finally {
    writer.close();
  }
  return file;
}

function fileState(path) {
  const file = new File(String(path));
  return {
    path: String(file.getAbsolutePath()),
    exists: file.exists(),
    length: file.exists() ? Number(file.length()) : 0,
  };
}

async function callBrowserTool(name, params) {
  return await toolCall('default', name, params || {});
}

async function callBrowserPackageTool(name, params) {
  return await toolCall('browser', name, params || {});
}

function countTabs(output) {
  const matches = String(output || '').match(/^- \[\d+\]/gm);
  return matches ? matches.length : 0;
}

function extractRef(snapshotOutput, marker) {
  const lines = String(snapshotOutput || '').split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (line.indexOf(marker) >= 0) {
      const match = line.match(/\[ref=([^\]]+)\]/);
      if (match) {
        return match[1];
      }
    }
  }
  throw new Error('ref not found for marker: ' + marker + '\nSnapshot:\n' + snapshotOutput);
}

function extractSavedPath(output) {
  const match = String(output || '').match(/Saved screenshot to ([^\r\n]+)/);
  return match ? match[1].trim() : null;
}

function extractNumericField(output, field) {
  const normalized = String(output || '').replace(/\\"/g, '"');
  const match = normalized.match(new RegExp('"' + String(field) + '"\\s*:\\s*(\\d+)'));
  if (!match) {
    throw new Error('numeric field not found: ' + field + '\nOutput:\n' + output);
  }
  return Number(match[1]);
}

function assertViewportMetrics(output, width, height, label) {
  const tolerance = 2;
  const innerWidth = extractNumericField(output, 'innerWidth');
  const innerHeight = extractNumericField(output, 'innerHeight');
  const clientWidth = extractNumericField(output, 'clientWidth');
  const clientHeight = extractNumericField(output, 'clientHeight');
  assert(Math.abs(innerWidth - width) <= tolerance, label + ' innerWidth mismatch: ' + innerWidth);
  assert(Math.abs(innerHeight - height) <= tolerance, label + ' innerHeight mismatch: ' + innerHeight);
  assert(Math.abs(clientWidth - width) <= tolerance, label + ' clientWidth mismatch: ' + clientWidth);
  assert(Math.abs(clientHeight - height) <= tolerance, label + ' clientHeight mismatch: ' + clientHeight);
}

async function tryBrowserTool(name, params) {
  try {
    return await callBrowserTool(name, params);
  } catch (_ignored) {
    return null;
  }
}

async function resetBrowserState() {
  await tryBrowserTool('browser_handle_dialog', { accept: true });
  await tryBrowserTool('browser_file_upload', {});
  await tryBrowserTool('browser_close_all', {});
}

async function ensureFixtureOpen() {
  const output = await callBrowserTool('browser_navigate', { url: PAGE_URL });
  assert(
    String(output).indexOf(PAGE_URL) >= 0 || String(output).indexOf('Browser Tool Test') >= 0,
    'fixture page should load'
  );
  return output;
}

async function ensureFixtureRefs(refs) {
  if (refs.clickBtn && refs.fileTrigger) {
    return refs;
  }
  await ensureFixtureOpen();
  const output = await callBrowserTool('browser_snapshot', { depth: 6 });
  refs.clickBtn = extractRef(output, 'Click Action');
  refs.logBtn = extractRef(output, 'Log Action');
  refs.alertBtn = extractRef(output, 'Alert Action');
  refs.hoverBox = extractRef(output, 'Hover Action');
  refs.dragSrc = extractRef(output, 'Drag Source');
  refs.dragDst = extractRef(output, 'Drag Target');
  refs.typeInput = extractRef(output, 'Type Input');
  refs.fillInput = extractRef(output, 'Fill Input');
  refs.checkInput = extractRef(output, 'Check Input');
  refs.selectInput = extractRef(output, 'Select Input');
  refs.fileTrigger = extractRef(output, 'File Trigger');
  return refs;
}

function makeSuiteHtml() {
  return [
    '<!doctype html>',
    '<html>',
    '<head>',
    '  <meta charset="utf-8" />',
    '  <title>Browser Tool Test</title>',
    '  <style>',
    '    body { font-family: sans-serif; padding: 16px; }',
    '    .box { width: 120px; height: 48px; border: 1px solid #333; margin: 8px 0; display: flex; align-items: center; justify-content: center; }',
    '  </style>',
    '</head>',
    '<body data-clicked="0" data-hovered="no" data-dropped="" data-lastkey="" data-upload="" data-dialog="idle" data-delayed="">',
    '  <button id="clickBtn">Click Action</button>',
    '  <button id="logBtn">Log Action</button>',
    '  <button id="alertBtn">Alert Action</button>',
    '  <a id="hashLink" href="#details">Hash Link</a>',
    '  <a id="blockedLink" href="https://example.com/blocked">Blocked Link</a>',
    '  <div id="details">Hash details</div>',
    '  <button id="hoverBox">Hover Action</button>',
    '  <button id="dragSrc" draggable="true">Drag Source</button>',
    '  <button id="dragDst">Drag Target</button>',
    '  <input id="typeInput" aria-label="Type Input" type="text" />',
    '  <input id="fillInput" aria-label="Fill Input" type="text" />',
    '  <input id="checkInput" aria-label="Check Input" type="checkbox" />',
    '  <select id="selectInput" aria-label="Select Input">',
    '    <option value="alpha">Alpha</option>',
    '    <option value="beta">Beta</option>',
    '    <option value="gamma">Gamma</option>',
    '  </select>',
    '  <button id="fileTrigger">File Trigger</button>',
    '  <input id="fileInput" aria-label="File Input" type="file" style="position:absolute;left:-9999px;top:-9999px;" />',
    '  <div id="fileState">no-file</div>',
    '  <div id="delayed">idle</div>',
    '  <script>',
    '    (function () {',
    '      const body = document.body;',
    '      const clickBtn = document.getElementById("clickBtn");',
    '      const logBtn = document.getElementById("logBtn");',
    '      const alertBtn = document.getElementById("alertBtn");',
    '      const blockedLink = document.getElementById("blockedLink");',
    '      const hoverBox = document.getElementById("hoverBox");',
    '      const dragSrc = document.getElementById("dragSrc");',
    '      const dragDst = document.getElementById("dragDst");',
    '      const fileTrigger = document.getElementById("fileTrigger");',
    '      const fileInput = document.getElementById("fileInput");',
    '      const fileState = document.getElementById("fileState");',
    '      const delayed = document.getElementById("delayed");',
    '      clickBtn.addEventListener("click", function () {',
    '        body.dataset.clicked = String(Number(body.dataset.clicked || "0") + 1);',
    '      });',
    '      logBtn.addEventListener("click", function () {',
    '        console.info("browser-tool-log-info");',
    '        console.warn("browser-tool-log-warn");',
    '      });',
    '      alertBtn.addEventListener("click", function () {',
    '        body.dataset.dialog = "opened";',
    '        alert("Browser dialog");',
    '        body.dataset.dialog = "handled";',
    '      });',
    '      blockedLink.addEventListener("click", function (event) {',
    '        event.preventDefault();',
    '        body.dataset.blocked = "prevented";',
    '      });',
    '      hoverBox.addEventListener("mouseover", function () {',
    '        body.dataset.hovered = "yes";',
    '      });',
    '      dragSrc.addEventListener("dragstart", function (event) {',
    '        if (event.dataTransfer) {',
    '          event.dataTransfer.setData("text/plain", "drag-source");',
    '        }',
    '      });',
    '      dragDst.addEventListener("dragover", function (event) {',
    '        event.preventDefault();',
    '      });',
    '      dragDst.addEventListener("drop", function (event) {',
    '        event.preventDefault();',
    '        const value = event.dataTransfer ? event.dataTransfer.getData("text/plain") : "missing";',
    '        body.dataset.dropped = value || "empty";',
    '      });',
    '      document.addEventListener("keydown", function (event) {',
    '        body.dataset.lastkey = String(event.key || "");',
    '      });',
    '      fileTrigger.addEventListener("click", function () {',
    '        fileInput.click();',
    '      });',
      '      fileInput.addEventListener("change", function () {',
    '        const names = Array.prototype.map.call(fileInput.files || [], function (file) { return file.name; }).join(",");',
    '        body.dataset.upload = names;',
    '        fileState.textContent = names || "empty";',
    '      });',
    '      window.scheduleDelayedText = function (text, delayMs) {',
    '        delayed.textContent = "waiting";',
    '        body.dataset.delayed = "waiting";',
    '        setTimeout(function () {',
    '          delayed.textContent = text;',
    '          body.dataset.delayed = text;',
    '        }, delayMs);',
    '        return "scheduled";',
    '      };',
    '    })();',
    '  </script>',
    '</body>',
    '</html>',
  ].join('\n');
}

function makeStrictCspHtml() {
  return [
    '<!doctype html>',
    '<html>',
    '<head>',
    '  <meta charset="utf-8" />',
    '  <meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'none\'; style-src \'unsafe-inline\'" />',
    '  <title>Strict CSP Browser Tool Test</title>',
    '  <style>body { font-family: sans-serif; padding: 16px; }</style>',
    '</head>',
    '<body>',
    '  <h1>Strict CSP Fixture</h1>',
    '  <input id="cspInput" aria-label="CSP Input" type="text" />',
    '  <select id="cspSelect" aria-label="CSP Select">',
    '    <option value="alpha">Alpha</option>',
    '    <option value="beta">Beta</option>',
    '  </select>',
    '</body>',
    '</html>',
  ].join('\n');
}

async function evaluateExpression(expression, ref) {
  return await callBrowserTool('browser_evaluate', {
    function: expression,
    ref: ref,
  });
}

exports.run = async function run() {
  const params = arguments.length > 0 ? (arguments[0] || {}) : {};
  ensureDir(BASE_DIR);
  writeText(PAGE_PATH, makeSuiteHtml());
  writeText(STRICT_CSP_PAGE_PATH, makeStrictCspHtml());
  writeText(UPLOAD_PATH, 'browser upload payload');
  await resetBrowserState();

  const refs = {};

  const tests = [
    test('browser_tabs list is callable', async () => {
      const output = await callBrowserTool('browser_tabs', { action: 'list' });
      assert(String(output).length > 0, 'tabs list should return output');
    }),
    test('browser_navigate loads example.com for network capture', async () => {
      const output = await callBrowserTool('browser_navigate', { url: 'https://example.com' });
      assert(String(output).indexOf('example.com') >= 0, 'navigate should mention example.com');
    }),
    test('browser_network_requests returns captured requests', async () => {
      const output = await callBrowserTool('browser_network_requests', {
        requestBody: false,
        requestHeaders: false,
        static: false,
      });
      assert(/example\.com|https?:\/\//.test(String(output)), 'network requests should contain example.com');
    }),
    test('browser_navigate loads local fixture page', async () => {
      const output = await callBrowserTool('browser_navigate', { url: PAGE_URL });
      assert(String(output).indexOf('Browser Tool Test') >= 0 || String(output).indexOf(PAGE_URL) >= 0, 'navigate should load local page');
    }),
    test('browser_navigate_back returns to previous page', async () => {
      const output = await callBrowserTool('browser_navigate_back', {});
      assert(String(output).indexOf('Navigated back.') >= 0 || String(output).indexOf('example.com') >= 0, 'navigate_back should succeed');
    }),
    test('browser_navigate reloads local fixture page after back', async () => {
      const output = await callBrowserTool('browser_navigate', { url: PAGE_URL });
      assert(String(output).indexOf(PAGE_URL) >= 0 || String(output).indexOf('Browser Tool Test') >= 0, 'fixture page should load again');
    }),
    test('browser_snapshot exposes refs for interactive elements', async () => {
      await ensureFixtureRefs(refs);
      assert(refs.clickBtn && refs.fileTrigger, 'expected fixture refs to be captured');
    }),
    test('browser_click triggers DOM click handlers', async () => {
      await ensureFixtureRefs(refs);
      const output = await callBrowserTool('browser_click', { ref: refs.clickBtn });
      const value = await evaluateExpression('() => document.body.dataset.clicked');
      assert(String(value).indexOf('1') >= 0, 'click should increment clicked counter');
      assert(String(output).indexOf('Click dispatched') >= 0, 'ordinary click should report dispatch without navigation');
    }),
    test('browser_hover triggers mouseover state', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_hover', { ref: refs.hoverBox });
      const value = await evaluateExpression('() => document.body.dataset.hovered');
      assert(String(value).indexOf('yes') >= 0, 'hover should update state');
    }),
    test('browser_drag transfers data to drop zone', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_drag', { startRef: refs.dragSrc, endRef: refs.dragDst });
      const value = await evaluateExpression('() => document.body.dataset.dropped');
      assert(String(value).indexOf('drag-source') >= 0, 'drag should update dropped state');
    }),
    test('browser_type fills text input and submit keeps state', async () => {
      await ensureFixtureRefs(refs);
      await evaluateExpression('() => { const el = document.getElementById("typeInput"); el.value = ""; el.focus(); return "ready"; }');
      await callBrowserTool('browser_type', { ref: refs.typeInput, text: 'typed-value', submit: false, slowly: false });
      const value = await evaluateExpression('() => document.getElementById("typeInput").value');
      assert(String(value).indexOf('typed-value') >= 0, 'type should fill text input');
    }),
    test('browser_press_key dispatches keyboard event', async () => {
      await ensureFixtureRefs(refs);
      await evaluateExpression('() => { document.getElementById("typeInput").focus(); return "focused"; }');
      await callBrowserTool('browser_press_key', { key: 'Enter' });
      const value = await evaluateExpression('() => document.body.dataset.lastkey');
      assert(String(value).indexOf('Enter') >= 0, 'press_key should record Enter');
    }),
    test('browser_fill_form updates text checkbox and select', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_fill_form', {
        fields: [
          { name: 'Fill Input', ref: refs.fillInput, value: 'filled-text' },
          { name: 'Check Input', ref: refs.checkInput, value: true },
          { name: 'Select Input', ref: refs.selectInput, value: 'Beta' },
        ],
      });
      const payload = await evaluateExpression('() => JSON.stringify({ fill: document.getElementById("fillInput").value, checked: document.getElementById("checkInput").checked, selected: document.getElementById("selectInput").value })');
      assert(String(payload).indexOf('filled-text') >= 0, 'fill_form should set input value');
      assert(String(payload).indexOf('true') >= 0, 'fill_form should set checkbox');
      assert(String(payload).indexOf('beta') >= 0, 'fill_form should select beta');
    }),
    test('browser package fill_form uses the public ToolPkg entry', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserPackageTool('fill_form', {
        fields: [
          { name: 'Public text', ref: refs.fillInput, value: 'public-filled-text' },
          { selector: '#checkInput', value: false },
          { selector: '#selectInput', value: 'Gamma' },
        ],
      });
      const payload = await evaluateExpression('() => JSON.stringify({ fill: document.getElementById("fillInput").value, checked: document.getElementById("checkInput").checked, selected: document.getElementById("selectInput").value })');
      assert(String(payload).indexOf('public-filled-text') >= 0, 'public fill_form should fill by ref');
      assert(String(payload).indexOf('false') >= 0, 'public fill_form should set checkbox by selector');
      assert(String(payload).indexOf('gamma') >= 0, 'public fill_form should select by selector');
    }),
    test('browser package fill_form rejects malformed public fields', async () => {
      let missingLocatorError = '';
      try {
        await callBrowserPackageTool('fill_form', {
          fields: [{ value: 'missing-locator' }],
        });
      } catch (error) {
        missingLocatorError = String(error);
      }
      assert(
        missingLocatorError.indexOf('requires exactly one of ref or selector') >= 0,
        'public fill_form should reject a missing locator'
      );

      let invalidValueError = '';
      try {
        await callBrowserPackageTool('fill_form', {
          fields: [{ selector: '#fillInput', value: { nested: true } }],
        });
      } catch (error) {
        invalidValueError = String(error);
      }
      assert(
        invalidValueError.indexOf('value must be a string, number, or boolean') >= 0,
        'public fill_form should reject object values'
      );
    }),
    test('browser_select_option changes selected option', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_select_option', { ref: refs.selectInput, values: ['gamma'] });
      const value = await evaluateExpression('() => document.getElementById("selectInput").value');
      assert(String(value).indexOf('gamma') >= 0, 'select_option should choose gamma');
    }),
    test('browser_evaluate works with ref argument', async () => {
      await ensureFixtureRefs(refs);
      const value = await callBrowserTool('browser_evaluate', {
        ref: refs.clickBtn,
        function: '(el) => el.textContent',
      });
      assert(String(value).indexOf('Click Action') >= 0, 'evaluate should return button text');
    }),
    test('browser_console_messages returns recent console output', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.logBtn });
      const value = await callBrowserTool('browser_console_messages', { level: 'info' });
      assert(String(value).indexOf('browser-tool-log-info') >= 0, 'console messages should include info log');
      assert(String(value).indexOf('userscript_permission_required') < 0, 'bridge diagnostics must not pollute page console');
    }),
    test('browser_resize keeps tool state and DOM layout viewport aligned', async () => {
      await ensureFixtureOpen();
      const output = await callBrowserTool('browser_resize', { width: 900, height: 600 });
      const metrics = await evaluateExpression(
        '() => JSON.stringify({ innerWidth, innerHeight, clientWidth: document.documentElement.clientWidth, clientHeight: document.documentElement.clientHeight })'
      );
      assert(/900x600/.test(String(output)), 'resize should report the requested viewport');
      assertViewportMetrics(metrics, 900, 600, 'direct resize');
    }),
    test('serial goto and resize settle on one viewport contract', async () => {
      await callBrowserTool('browser_navigate', { url: PAGE_URL });
      await callBrowserTool('browser_resize', { width: 640, height: 480 });
      const metrics = await evaluateExpression(
        '() => JSON.stringify({ url: location.href, innerWidth, innerHeight, clientWidth: document.documentElement.clientWidth, clientHeight: document.documentElement.clientHeight })'
      );
      assert(String(metrics).indexOf(PAGE_URL) >= 0, 'serial navigation should keep the fixture page active');
      assertViewportMetrics(metrics, 640, 480, 'serial goto and resize');
    }),
    test('concurrent goto and resize settle on the requested DOM viewport', async () => {
      await Promise.all([
        callBrowserTool('browser_navigate', { url: PAGE_URL }),
        callBrowserTool('browser_resize', { width: 700, height: 500 }),
      ]);
      const metrics = await evaluateExpression(
        '() => JSON.stringify({ innerWidth, innerHeight, clientWidth: document.documentElement.clientWidth, clientHeight: document.documentElement.clientHeight })'
      );
      assertViewportMetrics(metrics, 700, 500, 'concurrent goto and resize');
    }),
    test('each tab restores its own viewport after switching', async () => {
      await ensureFixtureOpen();
      await callBrowserTool('browser_resize', { width: 620, height: 420 });
      await callBrowserTool('browser_tabs', { action: 'create' });
      await callBrowserTool('browser_resize', { width: 460, height: 360 });
      await callBrowserTool('browser_tabs', { action: 'select', index: 0 });
      const first = await evaluateExpression(
        '() => JSON.stringify({ innerWidth, innerHeight, clientWidth: document.documentElement.clientWidth, clientHeight: document.documentElement.clientHeight })'
      );
      await callBrowserTool('browser_tabs', { action: 'select', index: 1 });
      const second = await evaluateExpression(
        '() => JSON.stringify({ innerWidth, innerHeight, clientWidth: document.documentElement.clientWidth, clientHeight: document.documentElement.clientHeight })'
      );
      assertViewportMetrics(first, 620, 420, 'first tab viewport');
      assertViewportMetrics(second, 460, 360, 'second tab viewport');
      await callBrowserTool('browser_close', {});
      await callBrowserTool('browser_tabs', { action: 'select', index: 0 });
    }),
    test('same-page link click completes navigation without href substitution', async () => {
      await ensureFixtureOpen();
      const output = await callBrowserTool('browser_click', { selector: '#hashLink' });
      const url = await evaluateExpression('() => location.href');
      assert(String(url).indexOf('#details') >= 0, 'real hash-link click should update the URL');
      assert(String(output).indexOf('Navigation completed') >= 0, 'click should report completed navigation');
    }),
    test('preventDefault link reports that navigation did not start', async () => {
      await ensureFixtureOpen();
      let output = '';
      try {
        output = String(await callBrowserTool('browser_click', { selector: '#blockedLink' }));
      } catch (error) {
        output = String(error);
      }
      assert(/did not start|prevented/.test(output), 'blocked navigation should return an explicit reason');
      const state = await evaluateExpression('() => document.body.dataset.blocked');
      assert(String(state).indexOf('prevented') >= 0, 'the real click handler should still run');
    }),
    test('example.com Learn more link performs cross-domain navigation', async () => {
      await callBrowserTool('browser_navigate', { url: 'https://example.com' });
      const snapshot = await callBrowserTool('browser_snapshot', { depth: 5 });
      const learnMore = extractRef(snapshot, 'Learn more');
      const output = await callBrowserTool('browser_click', { ref: learnMore });
      const url = await evaluateExpression('() => location.href');
      assert(/iana\.org/.test(String(url)), 'Learn more should navigate to IANA');
      assert(String(output).indexOf('Navigation completed') >= 0, 'cross-domain click should report completion');
    }),
    test('new tab bootstrap does not emit permission errors to page console', async () => {
      await ensureFixtureOpen();
      await callBrowserTool('browser_tabs', { action: 'create' });
      await callBrowserTool('browser_navigate', {
        url: 'data:text/html,<title>Bridge%20Isolation</title><p>new%20tab</p>',
      });
      const output = await callBrowserTool('browser_console_messages', { level: 'error' });
      assert(String(output).indexOf('userscript_permission_required') < 0, 'new tab should not expose bridge permission errors');
      await callBrowserTool('browser_close', {});
      await callBrowserTool('browser_tabs', { action: 'select', index: 0 });
    }),
    test('browser_wait_for resolves when delayed text appears', async () => {
      await ensureFixtureOpen();
      await evaluateExpression('() => window.scheduleDelayedText(' + JSON.stringify(DELAYED_TEXT) + ', 150)');
      const value = await callBrowserTool('browser_wait_for', { text: DELAYED_TEXT });
      assert(String(value).indexOf(DELAYED_TEXT) >= 0, 'wait_for should mention delayed text');
    }),
    test('browser_handle_dialog accepts pending alert', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.alertBtn });
      const handled = await callBrowserTool('browser_handle_dialog', { accept: true });
      assert(String(handled).indexOf('Handled alert dialog.') >= 0 || String(handled).indexOf('Handled alert.') >= 0, 'handle_dialog should succeed');
      const state = await evaluateExpression('() => document.body.dataset.dialog');
      assert(String(state).indexOf('handled') >= 0, 'dialog state should be handled');
    }),
    test('browser_file_upload resolves file chooser and updates file list', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.fileTrigger });
      await callBrowserTool('browser_file_upload', { paths: [UPLOAD_PATH] });
      const value = await evaluateExpression('() => document.body.dataset.upload');
      assert(String(value).indexOf('upload_payload.txt') >= 0, 'uploaded file name should be visible');
    }),
    test('browser_resize reports new viewport', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_resize', { width: 720, height: 1280 });
      assert(String(value).indexOf('720x1280') >= 0, 'resize should report requested viewport');
    }),
    test('browser_take_screenshot writes an image file', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_take_screenshot', { type: 'png' });
      const savedPath = extractSavedPath(value);
      assert(savedPath, 'screenshot output should contain saved path');
      const state = fileState(savedPath);
      assert(state.exists && state.length > 0, 'screenshot file should exist');
    }),
    test('browser_run_code still works on current page', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { return await page.title(); }',
      });
      assert(String(value).indexOf('Browser Tool Test') >= 0, 'run_code should return current page title');
    }),
    test('browser_run_code supports setContent and locator operations', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { await page.setContent("<main><input id=\\"runInput\\" type=\\"text\\"><select id=\\"runSelect\\"><option value=\\"alpha\\">Alpha</option><option value=\\"beta\\">Beta</option></select><p id=\\"content\\">set-content-ok</p></main>"); await page.locator("#runInput").fill("run-code-filled"); await page.locator("#runSelect").selectOption("beta"); return await page.evaluate(() => JSON.stringify({ text: document.getElementById("content").textContent, input: document.getElementById("runInput").value, selected: document.getElementById("runSelect").value })); }',
      });
      assert(String(value).indexOf('set-content-ok') >= 0, 'setContent should replace the current document');
      assert(String(value).indexOf('run-code-filled') >= 0, 'locator.fill should reuse the shared input runtime');
      assert(String(value).indexOf('"selected":"beta"') >= 0, 'locator.selectOption should reuse the shared input runtime');
    }),
    test('browser_run_code keyboard press shares package input semantics', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { const input = page.locator("#typeInput"); await input.fill(""); await input.click(); await page.keyboard.press("K"); await page.keyboard.press("Backspace"); await page.keyboard.press("Z"); return await page.evaluate(() => document.getElementById("typeInput").value); }',
      });
      assert(String(value).indexOf('Z') >= 0, 'keyboard.press should insert and delete text through the shared runtime');
    }),
    test('browser_run_code reports unsupported keyboard and locator APIs clearly', async () => {
      let methodOutput = '';
      try {
        methodOutput = String(await callBrowserTool('browser_run_code', {
          code: 'async (page) => page.keyboard.type("x")',
        }));
      } catch (error) {
        methodOutput = String(error);
      }
      assert(
        methodOutput.indexOf('Unsupported Playwright API: page.keyboard.type') >= 0,
        'unknown keyboard APIs should have a stable error'
      );

      let keyOutput = '';
      try {
        keyOutput = String(await callBrowserTool('browser_run_code', {
          code: 'async (page) => page.keyboard.press("Tab")',
        }));
      } catch (error) {
        keyOutput = String(error);
      }
      assert(
        keyOutput.indexOf('Unsupported Playwright API: page.keyboard.press("Tab")') >= 0,
        'keys without implemented behavior should be rejected'
      );

      let locatorOutput = '';
      try {
        locatorOutput = String(await callBrowserTool('browser_run_code', {
          code: 'async (page) => page.locator("body").focus()',
        }));
      } catch (error) {
        locatorOutput = String(error);
      }
      assert(
        locatorOutput.indexOf('Unsupported Playwright API: page.locator.focus') >= 0,
        'unknown locator APIs should have a stable error'
      );
    }),
    test('browser_run_code requires an explicit function source', async () => {
      let output = '';
      try {
        output = String(await callBrowserTool('browser_run_code', {
          code: 'return await page.title();',
        }));
      } catch (error) {
        output = String(error);
      }
      assert(
        output.indexOf('code must be a JavaScript function source') >= 0,
        'statement bodies should be rejected before WebView execution'
      );
    }),
    test('browser_run_code supports page once dialog registration', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { page.once("dialog", async dialog => { if (dialog.type() !== "prompt") throw new Error("wrong dialog type"); await dialog.accept("dialog-ok"); }); return await page.evaluate(() => prompt("Question", "default")); }',
      });
      assert(String(value).indexOf('dialog-ok') >= 0, 'page.once dialog handler should accept prompt text');
    }),
    test('browser_run_code supports alert confirm and prompt handlers', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { page.on("dialog", async dialog => { if (dialog.type() === "confirm") await dialog.accept(); else if (dialog.type() === "prompt") await dialog.accept("prompt-ok"); else await dialog.dismiss(); }); return await page.evaluate(() => { alert("a"); return JSON.stringify({ alert: "handled", confirm: confirm("c"), prompt: prompt("p", "d") }); }); }',
      });
      assert(String(value).indexOf('"alert"') >= 0, 'alert should be handled by the registered dialog listener');
      assert(String(value).indexOf('"confirm":true') >= 0, 'confirm should return the accepted result');
      assert(String(value).indexOf('prompt-ok') >= 0, 'prompt should return the accepted text');
    }),
    test('browser_run_code reports unsupported Page APIs clearly', async () => {
      let output = '';
      try {
        output = String(await callBrowserTool('browser_run_code', {
          code: 'async (page) => await page.screenshot()',
        }));
      } catch (error) {
        output = String(error);
      }
      assert(String(output).indexOf('Unsupported Playwright API: page.screenshot') >= 0, 'unsupported APIs should have a stable error');
    }),
    test('dynamic fetch and XHR remain visible in network requests', async () => {
      await callBrowserTool('browser_navigate', { url: 'https://example.com' });
      const marker = 'browser_dynamic_' + Date.now();
      await evaluateExpression(
        '() => { fetch("/?fetch=' + marker + '"); const xhr = new XMLHttpRequest(); xhr.open("GET", "/?xhr=' + marker + '"); xhr.send(); return "started"; }'
      );
      await callBrowserTool('browser_wait_for', { time: 0.8 });
      const output = await callBrowserTool('browser_network_requests', {
        requestBody: false,
        requestHeaders: false,
        static: false,
      });
      assert(String(output).indexOf(marker) >= 0, 'dynamic fetch and XHR should be captured');
    }),
    test('browser_tabs can create and select tabs', async () => {
      await ensureFixtureOpen();
      const created = await callBrowserTool('browser_tabs', { action: 'create' });
      assert(String(created).indexOf('Created tab') >= 0, 'tabs new should create a tab');
      const selected = await callBrowserTool('browser_tabs', { action: 'select', index: 0 });
      assert(String(selected).indexOf('Selected tab 0.') >= 0, 'tabs select should switch back to first tab');
      const firstTitle = await callBrowserTool('browser_run_code', {
        code: 'async (page) => await page.title()',
      });
      assert(String(firstTitle).indexOf('Browser Tool Test') >= 0, 'selected first tab should own run_code');
      await callBrowserTool('browser_tabs', { action: 'select', index: 1 });
      const secondUrl = await callBrowserTool('browser_evaluate', { function: '() => location.href' });
      assert(String(secondUrl).length > 0, 'selected second tab should own evaluate');
      await callBrowserTool('browser_close', {});
      await callBrowserTool('browser_tabs', { action: 'select', index: 0 });
    }),
    test('strict CSP keeps snapshot evaluate run_code input and close coherent', async () => {
      const navigated = await callBrowserTool('browser_navigate', { url: STRICT_CSP_PAGE_URL });
      assert(
        String(navigated).indexOf(STRICT_CSP_PAGE_URL) >= 0 ||
          String(navigated).indexOf('Strict CSP Browser Tool Test') >= 0,
        'strict CSP fixture should load'
      );
      const snapshot = await callBrowserTool('browser_snapshot', { depth: 5 });
      assert(String(snapshot).indexOf('CSP Input') >= 0, 'snapshot should work under strict CSP');
      const evaluated = await callBrowserTool('browser_evaluate', {
        function: '() => document.title',
      });
      assert(
        String(evaluated).indexOf('Strict CSP Browser Tool Test') >= 0,
        'evaluate should work under strict CSP'
      );
      const runCode = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { const input = page.locator("#cspInput"); await input.fill(""); await input.click(); await page.keyboard.press("C"); await page.locator("#cspSelect").selectOption("beta"); return await page.evaluate(() => JSON.stringify({ input: document.getElementById("cspInput").value, selected: document.getElementById("cspSelect").value })); }',
      });
      assert(String(runCode).indexOf('"input":"C"') >= 0, 'run_code input should work under strict CSP');
      assert(String(runCode).indexOf('"selected":"beta"') >= 0, 'run_code select should work under strict CSP');

      await callBrowserTool('browser_tabs', { action: 'create' });
      const tabsClosed = await callBrowserTool('browser_tabs', { action: 'close' });
      assert(String(tabsClosed).indexOf('### State change') >= 0, 'tabs close should report the completed state change');
      assert(String(tabsClosed).indexOf('### Page observation') >= 0, 'tabs close should report observation separately');
      assert(String(tabsClosed).indexOf('EvalError') < 0, 'tabs close should not depend on unsafe-eval');

      await callBrowserTool('browser_tabs', { action: 'create' });
      const closed = await callBrowserTool('browser_close', {});
      assert(String(closed).indexOf('### State change') >= 0, 'browser_close should report the completed state change');
      assert(String(closed).indexOf('### Page observation') >= 0, 'browser_close should report observation separately');
      assert(String(closed).indexOf('EvalError') < 0, 'browser_close should not depend on unsafe-eval');
    }),
    test('browser_close closes the current tab cleanly', async () => {
      await ensureFixtureOpen();
      await callBrowserTool('browser_tabs', { action: 'create' });
      await callBrowserTool('browser_tabs', { action: 'select', index: 1 });
      const closed = await callBrowserTool('browser_close', {});
      assert(/Closed/.test(String(closed)), 'browser_close should close current tab');
      const listed = await callBrowserTool('browser_tabs', { action: 'list' });
      assertEq(countTabs(listed), 1, 'only one tab should remain after close');
    }),
    test('browser_close_all closes all tabs cleanly', async () => {
      await ensureFixtureOpen();
      await callBrowserTool('browser_tabs', { action: 'create' });
      await callBrowserTool('browser_tabs', { action: 'create' });
      const closed = await callBrowserTool('browser_close_all', {});
      assert(String(closed).indexOf('Closed all browser tabs.') >= 0, 'browser_close_all should close every tab');
      const listed = await callBrowserTool('browser_tabs', { action: 'list' });
      assert(String(listed).indexOf('No open tabs.') >= 0, 'no tabs should remain after browser_close_all');
    }),
  ];

  const onlyName = params && params.only ? String(params.only) : '';
  const startAt = params && params.startAt ? String(params.startAt) : '';
  let selectedTests = tests;
  if (onlyName) {
    selectedTests = tests.filter((item) => item.name === onlyName);
    assert(selectedTests.length > 0, 'test not found: ' + onlyName);
  } else if (startAt) {
    const startIndex = tests.findIndex((item) => item.name === startAt);
    assert(startIndex >= 0, 'startAt test not found: ' + startAt);
    selectedTests = tests.slice(startIndex);
  }

  const result = await runTests(selectedTests);

  return {
    success: result.success,
    passed: result.passed,
    failed: result.failed,
    durationMs: result.durationMs,
    failures: result.failures,
    selectedCount: selectedTests.length,
    artifacts: {
      page: fileState(PAGE_PATH),
      strictCspPage: fileState(STRICT_CSP_PAGE_PATH),
      upload: fileState(UPLOAD_PATH),
    },
  };
};
