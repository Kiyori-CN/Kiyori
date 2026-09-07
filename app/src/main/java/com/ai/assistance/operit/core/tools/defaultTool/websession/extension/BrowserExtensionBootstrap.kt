package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import org.json.JSONArray
import org.json.JSONObject

/** 只生成隔离页面代码；bridge 仅接收诊断，不包含任何宿主文件、Cookie 或 AI 工具能力。 */
internal object BrowserExtensionBootstrap {
    fun source(bundle: BrowserExtensionPackage, bridgeName: String): String {
        val entries = JSONArray()
        bundle.manifest.content_scripts.forEach { entry ->
            entries.put(JSONObject()
                .put("matches", JSONArray(entry.matches.map(::patternRegex)))
                .put("excludes", JSONArray(entry.exclude_matches.map(::patternRegex)))
                .put("runAt", entry.run_at)
                .put("js", JSONArray(entry.js.map { bundle.files.getValue(it) }))
                .put("css", JSONArray(entry.css.map { bundle.files.getValue(it) })))
        }
        return """
            (() => {
              'use strict';
              if (window.top !== window) return;
              const bridge = globalThis[${JSONObject.quote(bridgeName)}];
              if (!bridge) return;
              const doc = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
              const href = location.href;
              const entries = $entries;
              let stopped = false;
              const cleanups = [], actions = [], styles = [];
              const emit = (state, detail = '') => {
                if (!stopped) bridge.postMessage(JSON.stringify({doc, href, state, detail: String(detail).slice(0, 2000)}));
              };
              const reportError = error => emit('ERROR', error && error.message || 'Extension execution failed');
              const api = Object.freeze({
                id: ${JSONObject.quote(bundle.id)},
                version: ${JSONObject.quote(bundle.manifest.version)},
                log: message => emit('LOG', message),
                onCleanup: fn => { if (typeof fn !== 'function') throw new TypeError('onCleanup requires function'); cleanups.push(fn); },
                onAction: fn => { if (typeof fn !== 'function') throw new TypeError('onAction requires function'); actions.push(fn); }
              });
              bridge.onmessage = event => {
                let command;
                try { command = JSON.parse(event.data); } catch (_) { return; }
                if (command.doc !== doc || stopped) return;
                if (command.type === 'start') {
                  startSelected();
                } else if (command.type === 'stop') {
                  window.removeEventListener('error', onWindowError);
                  window.removeEventListener('unhandledrejection', onUnhandledRejection);
                  for (const fn of cleanups.splice(0).reverse()) { try { fn(); } catch (error) { reportError(error); } }
                  styles.splice(0).forEach(style => style.remove());
                  stopped = true;
                } else if (command.type === 'action') {
                  if (!actions.length) { emit('ACTION_ERROR', 'No action handler registered'); return; }
                  emit('ACTION_RUNNING');
                  Promise.all(actions.map(fn => Promise.resolve().then(() => fn())))
                    .then(() => emit('ACTION_SUCCESS'), error => emit('ACTION_ERROR', error && error.message || 'Action failed'));
                }
              };
              const url = href.split('#')[0];
              const selected = entries.filter(entry => entry.matches.some(rule => new RegExp(rule).test(url)) &&
                !entry.excludes.some(rule => new RegExp(rule).test(url)));
              if (!selected.length) return;
              let started = false;
              let remaining = selected.length, failed = false;
              const run = entry => {
                if (stopped) return;
                emit('RUNNING');
                try {
                  for (const css of entry.css) {
                    const style = document.createElement('style'); style.textContent = css;
                    (document.head || document.documentElement).appendChild(style); styles.push(style);
                  }
                  for (const source of entry.js) { new Function('kiyori', '"use strict";\n' + source)(api); }
                } catch (error) { failed = true; reportError(error); }
                if (--remaining === 0 && !failed) emit('SUCCESS');
              };
              function startSelected() {
                if (started || stopped) return;
                started = true;
                emit('MATCHED');
                for (const entry of selected) {
                  if (entry.runAt === 'document_start') run(entry);
                  else {
                    const ready = () => entry.runAt === 'document_idle' ? setTimeout(() => run(entry), 0) : run(entry);
                    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, {once: true});
                    else ready();
                  }
                }
              }
              // 异步异常与同步完成分开记录；SUCCESS 仅证明入口返回，不证明业务正确。
              const onWindowError = event => reportError(event.error || event.message);
              const onUnhandledRejection = event => reportError(event.reason);
              window.addEventListener('error', onWindowError);
              window.addEventListener('unhandledrejection', onUnhandledRejection);
              emit('READY');
            })();
        """.trimIndent()
    }

    fun matches(pattern: String, url: String): Boolean = Regex(patternRegex(pattern)).matches(url.substringBefore('#'))

    fun patternRegex(pattern: String): String {
        val scheme = pattern.substringBefore("://").let { if (it == "*") "https?" else it }
        val rest = pattern.substringAfter("://")
        val authority = rest.substringBefore('/')
        val host = authority.substringBefore(':')
        val explicitPort = authority.substring(host.length)
        fun escape(text: String): String = buildString {
            var previousWildcard = false
            text.forEach { char ->
                if (char == '*') {
                    if (!previousWildcard) append(".*")
                    previousWildcard = true
                } else {
                    previousWildcard = false
                    when (char) {
                        '.', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\' -> append("\\$char")
                        else -> append(char)
                    }
                }
            }
        }
        val hostRegex = when {
            host == "*" -> "[^/:]+"
            host.startsWith("*.") -> "(?:[^/.:]+\\.)*" + escape(host.removePrefix("*."))
            else -> escape(host)
        }
        val portRegex = explicitPort.takeIf(String::isNotEmpty)?.let(::escape) ?: "(?::[0-9]+)?"
        return "^$scheme://$hostRegex$portRegex" +
            escape("/" + rest.substringAfter('/')) + "$"
    }
}
