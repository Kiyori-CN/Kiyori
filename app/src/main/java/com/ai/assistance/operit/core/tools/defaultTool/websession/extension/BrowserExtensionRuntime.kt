package com.ai.assistance.operit.core.tools.defaultTool.websession.extension

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal data class BrowserExtensionDiagnostic(
    val extensionId: String,
    val sessionId: String,
    val revision: String,
    val documentId: String,
    val url: String,
    val state: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** 所有 WebView 与 binding 操作由 Browser Runtime 在主线程串行调用。 */
internal class BrowserExtensionRuntime(
    private val repository: BrowserExtensionRepository,
    private val isSiteAllowed: (String) -> Boolean,
) {
    private data class Binding(
        val item: InstalledBrowserExtension,
        val bridgeName: String,
        val handler: ScriptHandler,
        val generation: String,
        var reply: JavaScriptReplyProxy? = null,
        var document: String = "",
        var url: String = "",
    )
    private data class Session(val view: WebView, val regularProfile: Boolean, val bindings: MutableMap<String, Binding> = linkedMapOf())
    private val sessions = linkedMapOf<String, Session>()
    private val mutableDiagnostics = MutableStateFlow<List<BrowserExtensionDiagnostic>>(emptyList())
    val diagnostics = mutableDiagnostics.asStateFlow()
    val supported: Boolean
        get() =
            WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    fun attach(sessionId: String, view: WebView, regularProfile: Boolean) {
        detach(sessionId)
        sessions[sessionId] = Session(view, regularProfile)
        reconcile(failOnError = false)
    }

    fun pageStarted(sessionId: String) {
        sessions[sessionId]?.bindings?.values?.forEach {
            it.reply = null
            it.document = ""
            it.url = ""
        }
        mutableDiagnostics.value = diagnostics.value.filterNot { it.sessionId == sessionId }
    }

    fun detach(sessionId: String) {
        sessions.remove(sessionId)?.let { session ->
            session.bindings.values.forEach { remove(sessionId, session, it) }
        }
        mutableDiagnostics.value = diagnostics.value.filterNot { it.sessionId == sessionId }
    }

    fun refreshSiteSettings() {
        sessions.forEach { (sessionId, session) ->
            session.bindings.values.forEach { binding ->
                if (binding.document.isNotEmpty() && !isSiteAllowed(binding.url)) {
                    stopDocument(sessionId, binding, "SITE_PERMISSION_REVOKED")
                }
            }
        }
    }

    fun reconcile(failOnError: Boolean = true) {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            return
        }
        val enabled = repository.state.value.filter { it.enabled }.associateBy { it.id }
        sessions.forEach { (sessionId, session) ->
            session.bindings.values.toList().forEach { binding ->
                if (enabled[binding.item.id]?.revision != binding.item.revision) {
                    session.bindings.remove(binding.item.id)
                    remove(sessionId, session, binding)
                }
            }
            enabled.values.forEach { item ->
                if (session.bindings.containsKey(item.id) || !session.regularProfile) return@forEach
                try {
                    val bridgeName = "__kiyoriExtension_" + item.id.replace('.', '_').replace('-', '_')
                    // 世界名称使用原始唯一 ID，不能因规范化 bridge 名称把两个扩展放进同一个世界。
                    val world = WebViewCompat.getExecutionWorld(session.view, "kiyori.extension.${item.id}")
                    val generation = java.util.UUID.randomUUID().toString()
                    WebViewCompat.addWebMessageListener(session.view, bridgeName, setOf("*"), world) {
                        view, message, origin, mainFrame, reply ->
                        val binding = session.bindings[item.id]
                        if (!mainFrame || view !== session.view || binding?.generation != generation) return@addWebMessageListener
                        val current = repository.state.value.firstOrNull { installed -> installed.id == item.id }
                        if (current?.enabled != true || current.revision != item.revision) return@addWebMessageListener
                        if (message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
                        val raw = message.data ?: return@addWebMessageListener
                        if (raw.length > 8192) return@addWebMessageListener
                        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
                        val href = json.optString("href")
                        if (!BrowserExtensionMessagePolicy.acceptsPage(href, origin.toString(), view.url, mainFrame)) return@addWebMessageListener
                        if (item.bundle.manifest.content_scripts.none { entry -> matches(entry, href) }) return@addWebMessageListener
                        val doc = json.optString("doc")
                        val state = json.optString("state")
                        if (state !in STATES) return@addWebMessageListener
                        if (!BrowserExtensionMessagePolicy.acceptsDocument(binding.document, doc, state)) return@addWebMessageListener
                        binding.reply = reply
                        binding.document = doc
                        binding.url = href
                        if (!isSiteAllowed(href)) {
                            record(BrowserExtensionDiagnostic(item.id, sessionId, item.revision, doc, href, "SITE_PERMISSION_REQUIRED", ""))
                            stopDocument(sessionId, binding)
                            return@addWebMessageListener
                        }
                        record(BrowserExtensionDiagnostic(item.id, sessionId, item.revision, doc, href, state, json.optString("detail").take(2000)))
                        if (
                            state == "READY" &&
                                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                        ) {
                            reply.postMessage(JSONObject().put("type", "start").put("doc", doc).toString())
                        }
                    }
                    try {
                        val handler = WebViewCompat.addJavaScriptOnEvent(
                            session.view,
                            BrowserExtensionBootstrap.source(item.bundle, bridgeName),
                            WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                            setOf("*"),
                            world,
                        )
                        session.bindings[item.id] = Binding(item, bridgeName, handler, generation)
                    } catch (error: Exception) {
                        WebViewCompat.removeWebMessageListener(session.view, world, bridgeName)
                        throw error
                    }
                } catch (error: Exception) {
                    record(
                        BrowserExtensionDiagnostic(
                            item.id,
                            sessionId,
                            item.revision,
                            "",
                            session.view.url.orEmpty(),
                            "REGISTRATION_ERROR",
                            error.javaClass.simpleName,
                        ),
                    )
                    // 扩展注册失败必须可见，但不能使普通浏览器标签无法创建。
                    if (failOnError) throw error
                }
            }
        }
    }

    fun invokeAction(sessionId: String, id: String): String {
        val binding = sessions[sessionId]?.bindings?.get(id) ?: error("EXTENSION_NOT_RUNNING")
        require(binding.item.bundle.manifest.action != null) { "ACTION_NOT_DECLARED" }
        val reply = binding.reply ?: error("RELOAD_REQUIRED: no current extension document")
        require(isSiteAllowed(binding.url)) { "SITE_PERMISSION_REQUIRED" }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            require(repository.get(id).let { it.enabled && it.revision == binding.item.revision }) { "EXTENSION_NOT_RUNNING" }
            record(BrowserExtensionDiagnostic(id, sessionId, binding.item.revision, binding.document, binding.url, "ACTION_DISPATCHED", ""))
            reply.postMessage(JSONObject().put("type", "action").put("doc", binding.document).toString())
            return binding.document
        }
        error("UNSUPPORTED_RUNTIME: WEB_MESSAGE_LISTENER")
    }

    private fun stopDocument(sessionId: String, binding: Binding, state: String? = null) {
        val document = binding.document
        val url = binding.url
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                binding.reply?.postMessage(JSONObject().put("type", "stop").put("doc", document).toString())
            }
        } catch (error: IllegalStateException) {
            record(
                BrowserExtensionDiagnostic(
                    binding.item.id,
                    sessionId,
                    binding.item.revision,
                    document,
                    url,
                    "CLEANUP_UNAVAILABLE",
                    error.javaClass.simpleName,
                ),
            )
        } finally {
            binding.reply = null
            binding.document = ""
            binding.url = ""
            if (state != null) {
                record(BrowserExtensionDiagnostic(binding.item.id, sessionId, binding.item.revision, document, url, state, ""))
            }
        }
    }

    private fun remove(sessionId: String, session: Session, binding: Binding) {
        stopDocument(sessionId, binding)
        binding.handler.remove()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
            val world = WebViewCompat.getExecutionWorld(session.view, "kiyori.extension.${binding.item.id}")
            WebViewCompat.removeWebMessageListener(session.view, world, binding.bridgeName)
        }
    }

    private fun record(item: BrowserExtensionDiagnostic) {
        mutableDiagnostics.value = (diagnostics.value + item).takeLast(200)
    }

    companion object {
        private val STATES = setOf("READY", "MATCHED", "RUNNING", "SUCCESS", "ERROR", "LOG", "ACTION_RUNNING", "ACTION_SUCCESS", "ACTION_ERROR")
        fun matches(entry: BrowserExtensionContent, url: String): Boolean =
            entry.matches.any { BrowserExtensionBootstrap.matches(it, url) } &&
                entry.exclude_matches.none { BrowserExtensionBootstrap.matches(it, url) }
    }
}
