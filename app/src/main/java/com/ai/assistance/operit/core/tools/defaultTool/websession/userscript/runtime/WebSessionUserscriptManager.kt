package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ParsedUserscriptMetadata
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptCapabilityRegistry
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallPreview
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptManagementPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptMatcher
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageStatusPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptRuntimeCapabilities
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptSupportState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.toParsedMetadata
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.install.UserscriptImportCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage.UserscriptRepository
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.storage.UserscriptStorageLayout
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.UserscriptDetailUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.UserscriptEditorUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiStateStore
import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class WebSessionUserscriptManager(
    private val context: Context,
    private val onOpenUserscriptUi: () -> Unit,
    private val onOpenUserscriptDetail: (Long) -> Unit,
    private val onOpenUserscriptEditor: (draftId: String, userscriptId: Long?) -> Unit,
    private val onOpenTab: (sourceSessionId: String, url: String, active: Boolean) -> String?,
    private val onActivateSession: (sessionId: String) -> Unit,
    private val onCloseSession: (sessionId: String) -> Boolean,
    private val onDownload: (sessionId: String, url: String, fileName: String?) -> Unit,
    private val onMenuCommandsChanged: (sessionId: String?) -> Unit,
    private val onToast: (message: String) -> Unit
) {
    private enum class BridgeScope {
        PAGE,
        ISOLATED,
    }

    private data class IsolatedRuntimeBinding(
        val bridgeName: String,
        val world: JavaScriptExecutionWorld,
        val scriptAuthorizations: ConcurrentHashMap<String, UserscriptBridgeAuthorization> =
            ConcurrentHashMap(),
        val scriptGrants: ConcurrentHashMap<Long, Set<String>> = ConcurrentHashMap(),
        val replyProxies: MutableSet<JavaScriptReplyProxy> = ConcurrentHashMap.newKeySet(),
    )

    private data class SessionBinding(
        val sessionId: String,
        val webView: WebView,
        val cookieScope: String,
        val cookieManager: CookieManager,
        val cookieService: UserscriptCookieService,
        val scriptHandlers: List<ScriptHandler>,
        val isolatedRuntime: IsolatedRuntimeBinding?,
        val pageBootstrapReplyProxies: MutableSet<JavaScriptReplyProxy> =
            ConcurrentHashMap.newKeySet(),
        val menuCommands: LinkedHashMap<String, UserscriptPageMenuCommand> = linkedMapOf(),
    )

    private data class OpenedTabOwner(
        val sessionId: String,
        val userscriptId: Long,
    )

    private data class SessionPageState(
        var pageUrl: String = "about:blank",
        val scriptStatuses: ConcurrentHashMap<Long, UserscriptPageRuntimeStatus> = ConcurrentHashMap()
    )

    private data class UserscriptConnectAuthorization(
        val metadata: ParsedUserscriptMetadata,
        val pageUrl: String,
    )

    companion object {
        private const val TAG = "WebSessionUserscript"
        private const val ISOLATED_WORLD_NAME = "kiyori-userscript-runtime"
        private const val NOTIFICATION_CHANNEL_ID = "userscript_notifications"
        private const val NOTIFICATION_ID = 50142
    }

    private val repository = UserscriptRepository.getInstance(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val storageNotifier = UserscriptStorageNotifier()
    private val tabStateStore = UserscriptTabStateStore()
    private val webRequestEngine = UserscriptWebRequestEngine()
    private val secureRandom = SecureRandom()
    private val requestClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            // Network interceptors run once per concrete network hop. The request tag is copied by
            // OkHttp's follow-up Request.Builder, so every redirect target is checked against the
            // same script metadata instead of inheriting authorization from only the first URL.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val authorization =
                    request.tag(UserscriptConnectAuthorization::class.java)
                        ?: return@addNetworkInterceptor chain.proceed(request)
                val targetUrl = request.url.toString()
                if (
                    !UserscriptMatcher.isConnectAllowed(
                        metadata = authorization.metadata,
                        pageUrl = authorization.pageUrl,
                        targetUrl = targetUrl,
                    )
                ) {
                    throw IOException("GM_xmlhttpRequest blocked by @connect: $targetUrl")
                }
                chain.proceed(request)
            }
            .build()

    private val sessionBindings = ConcurrentHashMap<String, SessionBinding>()
    private val cookieServices = ConcurrentHashMap<String, UserscriptCookieService>()
    private val sessionPageStates = ConcurrentHashMap<String, SessionPageState>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val abortedRequestKeys = ConcurrentHashMap.newKeySet<String>()
    private val openedTabOwners = ConcurrentHashMap<String, OpenedTabOwner>()
    private val audioMuteStates = ConcurrentHashMap<String, Boolean>()
    private val webViewProviderLogged = AtomicBoolean(false)
    @Volatile
    private var visibleSessionId: String? = null
    private val runtimeCapabilities = UserscriptRuntimeCapabilities.current()
    private val supportState =
        UserscriptSupportState(
            isSupported = runtimeCapabilities.pageWorldSupported,
            reason =
                when {
                    !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ->
                        "Current WebView does not support document-start script injection"
                    !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ->
                        "Current WebView does not support native userscript messaging"
                    else -> null
                }
        )

    val uiStore = WebSessionUserscriptUiStateStore(initialSupportState = supportState)

    init {
        scope.launch {
            repository.userScriptsAllowedFlow.collectLatest { allowed ->
                uiStore.updateUserScriptsAllowed(allowed)
                if (!allowed) {
                    revokeActiveNetworkCalls()
                    clearActiveRuntimeAuthorizations()
                }
                rebuildAllSessionBaselines()
            }
        }
        scope.launch {
            repository.installedScriptsFlow.collectLatest { scripts ->
                uiStore.updateScripts(scripts)
                uiStore.retainInstalledState(scripts.mapTo(linkedSetOf(), UserscriptListItem::id))
                reconcileActiveRuntimeAuthorizations(scripts)
                rebuildAllSessionBaselines()
            }
        }
        scope.launch {
            repository.observeRecentLogs().collectLatest { logs ->
                uiStore.updateLogs(logs)
            }
        }
        refreshDrafts()
    }

    fun supportState(): UserscriptSupportState = supportState

    fun updateVisibleSession(
        sessionId: String?,
        pageUrl: String?
    ) {
        visibleSessionId = sessionId
        if (!sessionId.isNullOrBlank() && !pageUrl.isNullOrBlank()) {
            val state = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
            if (state.pageUrl != pageUrl) {
                state.pageUrl = pageUrl
                rebuildSessionBaseline(sessionId)
                return
            }
            if (state.scriptStatuses.isEmpty() && pageUrl != "about:blank") {
                rebuildSessionBaseline(sessionId)
                return
            }
        }
        publishVisibleStatuses()
    }

    fun onPageChanged(
        sessionId: String,
        pageUrl: String,
        forceReset: Boolean = false
    ) {
        val state = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
        if (!forceReset && state.pageUrl == pageUrl && state.scriptStatuses.isNotEmpty()) {
            return
        }
        sessionBindings[sessionId]?.let { binding ->
            binding.pageBootstrapReplyProxies.clear()
            binding.isolatedRuntime?.scriptAuthorizations?.clear()
            binding.isolatedRuntime?.scriptGrants?.clear()
            binding.isolatedRuntime?.replyProxies?.clear()
            if (binding.menuCommands.isNotEmpty()) {
                binding.menuCommands.clear()
                onMenuCommandsChanged(sessionId)
            }
        }
        state.pageUrl = pageUrl
        state.scriptStatuses.clear()
        webRequestEngine.clearSession(sessionId)
        rebuildSessionBaseline(sessionId)
    }

    fun syncUrlChange(
        sessionId: String,
        pageUrl: String
    ) {
        val state = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
        state.pageUrl = pageUrl
        rebuildSessionBaseline(sessionId)
    }

    fun attachSession(
        sessionId: String,
        webView: WebView,
        cookieScope: String,
        cookieManager: CookieManager,
    ) {
        if (!supportState.isSupported) {
            return
        }
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            AppLogger.e(TAG, "Userscript runtime capabilities changed before session attachment")
            return
        }
        val existing = sessionBindings[sessionId]
        if (existing?.webView === webView) {
            return
        }
        sessionPageStates.putIfAbsent(sessionId, SessionPageState())
        val attachNow: () -> Unit = attachNow@{
            logWebViewProviderOnce()
            existing?.let { binding ->
                clearRuntimeBindingState(binding)
            }
            val cookieService =
                cookieServices.computeIfAbsent(cookieScope) {
                    UserscriptCookieService(cookieManager)
                }
            val scriptHandlers = mutableListOf<ScriptHandler>()
            val pageScriptHandler =
                runCatching {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        WebViewCompat.addWebMessageListener(
                            webView,
                            UserscriptBootstrapScript.BRIDGE_NAME,
                            setOf("*"),
                            bridgeListener(sessionId, BridgeScope.PAGE),
                        )
                    } else {
                        error("Web message listener support changed during userscript attachment")
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(
                            webView,
                            UserscriptBootstrapScript.documentStartScript(),
                            setOf("*"),
                        )
                    } else {
                        error("Document-start script support changed during userscript attachment")
                    }
                }.getOrElse { error ->
                    AppLogger.e(TAG, "Failed to add page-world userscript runtime", error)
                    null
                }
            if (pageScriptHandler != null) {
                scriptHandlers += pageScriptHandler
            }

            val isolatedRuntime =
                if (!runtimeCapabilities.isolatedWorldSupported) {
                    null
                } else if (
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD,
                    )
                ) {
                    val bridgeName = UserscriptBootstrapScript.ISOLATED_BRIDGE_NAME
                    val world =
                        runCatching {
                            WebViewCompat.getExecutionWorld(
                                webView,
                                ISOLATED_WORLD_NAME,
                            )
                        }.getOrElse { error ->
                            AppLogger.e(
                                TAG,
                                "Failed to create the userscript isolated world",
                                error,
                            )
                            null
                        }
                    world?.let { executionWorld ->
                        val isolatedScriptHandler =
                            runCatching {
                                WebViewCompat.addWebMessageListener(
                                    webView,
                                    bridgeName,
                                    setOf("*"),
                                    executionWorld,
                                    bridgeListener(
                                        sessionId = sessionId,
                                        bridgeScope = BridgeScope.ISOLATED,
                                    ),
                                )
                                WebViewCompat.addJavaScriptOnEvent(
                                    webView,
                                    UserscriptBootstrapScript.documentStartScript(bridgeName),
                                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                                    setOf("*"),
                                    executionWorld,
                                )
                            }.getOrElse { error ->
                                AppLogger.e(
                                    TAG,
                                    "Failed to add the userscript isolated runtime",
                                    error,
                                )
                                null
                            }
                        isolatedScriptHandler?.let { handler ->
                            scriptHandlers += handler
                            IsolatedRuntimeBinding(
                                bridgeName = bridgeName,
                                world = executionWorld,
                            )
                        }
                    }
                } else {
                    error("Isolated userscript WebView feature changed after capability discovery")
                }

            sessionBindings[sessionId] =
                SessionBinding(
                    sessionId = sessionId,
                    webView = webView,
                    cookieScope = cookieScope,
                    cookieManager = cookieManager,
                    cookieService = cookieService,
                    scriptHandlers = scriptHandlers,
                    isolatedRuntime = isolatedRuntime,
                )
            AppLogger.i(
                TAG,
                "Attached stable userscript runtime: session=$sessionId, " +
                    "page=${pageScriptHandler != null}, isolated=${isolatedRuntime != null}",
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            attachNow()
        } else {
            mainHandler.post(attachNow)
        }
    }

    private fun clearRuntimeBindingState(binding: SessionBinding) {
        binding.pageBootstrapReplyProxies.clear()
        binding.menuCommands.clear()
        binding.isolatedRuntime?.scriptAuthorizations?.clear()
        binding.isolatedRuntime?.scriptGrants?.clear()
        binding.isolatedRuntime?.replyProxies?.clear()
    }

    private fun logWebViewProviderOnce() {
        if (!webViewProviderLogged.compareAndSet(false, true)) {
            return
        }
        val provider = WebViewCompat.getCurrentWebViewPackage(context)
        AppLogger.i(
            TAG,
            "WebView provider: package=${provider?.packageName.orEmpty()}, " +
                "versionName=${provider?.versionName.orEmpty()}, " +
                "versionCode=${provider?.let(PackageInfoCompat::getLongVersionCode) ?: 0L}, " +
                "isolatedWorld=${runtimeCapabilities.isolatedWorldSupported}",
        )
    }

    private fun revokeActiveNetworkCalls() {
        activeCalls.entries.toList().forEach { entry ->
            abortedRequestKeys.add(entry.key)
            if (activeCalls.remove(entry.key, entry.value)) {
                runCatching { entry.value.cancel() }
            }
        }
    }

    private fun clearActiveRuntimeAuthorizations() {
        val clearNow = {
            sessionBindings.values.forEach { binding ->
                binding.pageBootstrapReplyProxies.clear()
                binding.isolatedRuntime?.scriptAuthorizations?.clear()
                binding.isolatedRuntime?.scriptGrants?.clear()
                binding.isolatedRuntime?.replyProxies?.clear()
                webRequestEngine.clearSession(binding.sessionId)
                if (binding.menuCommands.isNotEmpty()) {
                    binding.menuCommands.clear()
                    onMenuCommandsChanged(binding.sessionId)
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            clearNow()
        } else {
            mainHandler.post(clearNow)
        }
    }

    private fun reconcileActiveRuntimeAuthorizations(scripts: List<UserscriptListItem>) {
        val validGrants =
            scripts
                .asSequence()
                .filter { script -> script.enabled && script.blockedReasons.isEmpty() }
                .associate { script ->
                    script.id to
                        UserscriptCapabilityRegistry
                            .knownGrants(script.grants)
                            .toSet()
                }
        val reconcileNow = {
            sessionBindings.values.forEach { binding ->
                val isolated = binding.isolatedRuntime
                isolated?.scriptAuthorizations?.entries?.removeIf { entry ->
                    validGrants[entry.value.scriptId] != entry.value.grants
                }
                val invalidScriptIds =
                    isolated
                        ?.scriptGrants
                        ?.entries
                        ?.filter { entry -> validGrants[entry.key] != entry.value }
                        ?.map { entry -> entry.key }
                        .orEmpty()
                invalidScriptIds.forEach { scriptId ->
                    isolated?.scriptGrants?.remove(scriptId)
                    webRequestEngine.clearScript(binding.sessionId, scriptId)
                }
                val menuChanged =
                    binding.menuCommands.entries.removeIf { entry ->
                        entry.value.userscriptId !in validGrants
                    }
                if (menuChanged) {
                    onMenuCommandsChanged(binding.sessionId)
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            reconcileNow()
        } else {
            mainHandler.post(reconcileNow)
        }
    }

    fun detachSession(sessionId: String) {
        val binding = sessionBindings.remove(sessionId) ?: return
        if (sessionBindings.values.none { remaining -> remaining.cookieScope == binding.cookieScope }) {
            cookieServices.remove(binding.cookieScope)
        }
        sessionPageStates.remove(sessionId)
        if (visibleSessionId == sessionId) {
            visibleSessionId = null
        }
        val removeNow = {
            // The owning browser closes and destroys this WebView immediately after detachSession.
            // Explicitly removing document-start scripts or execution-world listeners while
            // Chromium still has navigation tasks in flight can race its native registration
            // state. Clear Kiyori-owned authorization state and let WebView.destroy() retire the
            // provider-owned registrations together with the WebView.
            clearRuntimeBindingState(binding)
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeNow()
        } else {
            mainHandler.post(removeNow)
        }
        activeCalls.entries.removeAll { entry ->
            val remove = entry.key.startsWith("$sessionId:")
            if (remove) {
                runCatching { entry.value.cancel() }
            }
            remove
        }
        webRequestEngine.clearSession(sessionId)
        tabStateStore.clearSession(sessionId)
        audioMuteStates.remove(sessionId)
        openedTabOwners.remove(sessionId)?.let { owner ->
            dispatchHostEvent(
                sessionId = owner.sessionId,
                userscriptId = owner.userscriptId,
                eventType = "open_tab_closed",
                payload = JSONObject().put("sessionId", sessionId),
            )
        }
        openedTabOwners.forEach { (openedSessionId, owner) ->
            if (owner.sessionId == sessionId) {
                openedTabOwners.remove(openedSessionId, owner)
            }
        }
        onMenuCommandsChanged(sessionId)
        publishVisibleStatuses()
    }

    fun getMenuCommands(sessionId: String?): List<UserscriptPageMenuCommand> {
        if (!uiStore.state.value.userScriptsAllowed || sessionId.isNullOrBlank()) {
            return emptyList()
        }
        return sessionBindings[sessionId]?.menuCommands?.values?.toList().orEmpty()
    }

    fun invokeMenuCommand(
        sessionId: String?,
        commandId: String
    ) {
        if (sessionId.isNullOrBlank() || commandId.isBlank()) {
            return
        }
        val command = sessionBindings[sessionId]?.menuCommands?.get(commandId) ?: return
        dispatchHostEvent(
            sessionId = sessionId,
            userscriptId = command.userscriptId,
            eventType = "menu_command",
            payload = JSONObject().put("commandId", command.runtimeCommandId),
        )
    }

    fun beginUrlInstall(rawUrl: String, sourceType: UserscriptInstallSourceType = UserscriptInstallSourceType.REMOTE_URL) {
        if (!supportState.isSupported) {
            mainHandler.post(onOpenUserscriptUi)
            return
        }
        val normalizedUrl = rawUrl.trim()
        val scheme =
            runCatching { android.net.Uri.parse(normalizedUrl).scheme?.lowercase() }.getOrNull()
        if (normalizedUrl.isBlank() || (scheme != "http" && scheme != "https")) {
            onToast(context.getString(R.string.web_session_userscript_invalid_url))
            return
        }
        scope.launch {
            runCatching {
                repository.fetchRemotePreview(normalizedUrl, sourceType)
            }.onSuccess { preview ->
                uiStore.setPendingInstall(preview)
                mainHandler.post(onOpenUserscriptUi)
            }.onFailure { error ->
                repository.log(null, "error", normalizedUrl, error.message ?: "userscript install preview failed")
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun beginLocalImport() {
        if (!supportState.isSupported) {
            mainHandler.post(onOpenUserscriptUi)
            return
        }
        scope.launch {
            val result = UserscriptImportCoordinator.requestImport(context.applicationContext)
            if (result == null) {
                return@launch
            }
            runCatching {
                repository.prepareInstallPreview(
                    rawSource = result.rawSource,
                    sourceType = UserscriptInstallSourceType.LOCAL_FILE,
                    sourceUrl = result.sourceUri,
                    sourceDisplay = result.displayName
                )
            }.onSuccess { preview ->
                uiStore.setPendingInstall(preview)
                mainHandler.post(onOpenUserscriptUi)
            }.onFailure { error ->
                repository.log(null, "error", result.sourceUri, error.message ?: "userscript local import failed")
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun confirmPendingInstall() {
        if (!supportState.isSupported) {
            mainHandler.post(onOpenUserscriptUi)
            return
        }
        val preview = uiStore.state.value.pendingInstall ?: return
        scope.launch {
            runCatching {
                repository.install(preview)
            }.onSuccess { installed ->
                uiStore.setPendingInstall(null)
                uiStore.removeUpdateCandidate(installed.id)
                loadScriptDetail(installed.id)
                mainHandler.post {
                    onToast(
                        context.getString(
                            R.string.web_session_userscript_installed,
                            installed.name
                        )
                    )
                    onOpenUserscriptUi()
                }
            }.onFailure { error ->
                repository.log(preview.existingScriptId, "error", preview.sourceUrl, error.message ?: "userscript install failed")
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun cancelPendingInstall() {
        uiStore.setPendingInstall(null)
    }

    fun setScriptEnabled(
        scriptId: Long,
        enabled: Boolean
    ) {
        scope.launch {
            repository.setEnabled(scriptId, enabled)
        }
    }

    fun setUserScriptsAllowed(allowed: Boolean) {
        scope.launch {
            repository.setUserScriptsAllowed(allowed)
        }
    }

    fun deleteScript(scriptId: Long) {
        scope.launch {
            repository.deleteUserscript(scriptId)
            mainHandler.post {
                onToast(context.getString(R.string.web_session_userscript_deleted))
            }
        }
    }

    fun checkForUpdate(scriptId: Long) {
        uiStore.setUpdateChecking(scriptId, true)
        scope.launch {
            try {
                val current = repository.getInstalledScript(scriptId)
                val preview = repository.checkForUpdate(scriptId)
                if (current != null && preview != null) {
                    uiStore.setUpdateCandidate(
                        UserscriptManagementPolicy.buildUpdateCandidate(current, preview),
                    )
                } else {
                    uiStore.removeUpdateCandidate(scriptId)
                    mainHandler.post {
                        onToast(context.getString(R.string.web_session_userscript_no_update))
                    }
                }
            } catch (error: Throwable) {
                repository.log(
                    userscriptId = scriptId,
                    level = "error",
                    pageUrl = null,
                    message = error.message ?: "userscript update check failed",
                )
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            } finally {
                uiStore.setUpdateChecking(scriptId, false)
            }
        }
    }

    fun checkAllUpdates() {
        if (uiStore.state.value.isCheckingAllUpdates) {
            return
        }
        uiStore.setCheckingAllUpdates(true)
        scope.launch {
            var availableCount = 0
            var failureCount = 0
            try {
                uiStore.state.value.installedScripts.forEach { current ->
                    if (
                        current.updateUrl.isNullOrBlank() &&
                            current.downloadUrl.isNullOrBlank() &&
                            current.sourceUrl.isNullOrBlank()
                    ) {
                        return@forEach
                    }
                    uiStore.setUpdateChecking(current.id, true)
                    try {
                        val preview = repository.checkForUpdate(current.id)
                        if (preview == null) {
                            uiStore.removeUpdateCandidate(current.id)
                        } else {
                            uiStore.setUpdateCandidate(
                                UserscriptManagementPolicy.buildUpdateCandidate(current, preview),
                            )
                            availableCount += 1
                        }
                    } catch (error: Throwable) {
                        failureCount += 1
                        repository.log(
                            userscriptId = current.id,
                            level = "error",
                            pageUrl = null,
                            message = error.message ?: "userscript update check failed",
                        )
                    } finally {
                        uiStore.setUpdateChecking(current.id, false)
                    }
                }
            } finally {
                uiStore.setCheckingAllUpdates(false)
            }
            mainHandler.post {
                onToast(
                    when {
                        failureCount > 0 ->
                            context.resources.getQuantityString(
                                R.plurals.web_session_userscript_update_check_summary_failed,
                                availableCount,
                                availableCount,
                                failureCount,
                            )
                        else ->
                            context.resources.getQuantityString(
                                R.plurals.web_session_userscript_update_check_summary,
                                availableCount,
                                availableCount,
                            )
                    },
                )
            }
        }
    }

    fun applyUpdate(scriptId: Long) {
        val candidate = uiStore.state.value.updateCandidates[scriptId] ?: return
        if (!candidate.safeToAutoApply) {
            uiStore.setPendingInstall(candidate.preview)
            return
        }
        scope.launch {
            runCatching {
                repository.install(candidate.preview)
            }.onSuccess { installed ->
                uiStore.removeUpdateCandidate(installed.id)
                loadScriptDetail(installed.id)
                mainHandler.post {
                    onToast(
                        context.getString(
                            R.string.web_session_userscript_updated,
                            installed.name,
                        ),
                    )
                }
            }.onFailure { error ->
                repository.log(
                    userscriptId = scriptId,
                    level = "error",
                    pageUrl = null,
                    message = error.message ?: "userscript update failed",
                )
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun applyAllSafeUpdates() {
        if (uiStore.state.value.isApplyingSafeUpdates) {
            return
        }
        val safeCandidates =
            uiStore.state.value.updateCandidates.values.filter { candidate ->
                candidate.safeToAutoApply
            }
        if (safeCandidates.isEmpty()) {
            mainHandler.post {
                onToast(context.getString(R.string.web_session_userscript_no_safe_updates))
            }
            return
        }
        uiStore.setApplyingSafeUpdates(true)
        scope.launch {
            var completedCount = 0
            var failureCount = 0
            try {
                safeCandidates.forEach { candidate ->
                    runCatching {
                        repository.install(candidate.preview)
                    }.onSuccess { installed ->
                        completedCount += 1
                        uiStore.removeUpdateCandidate(installed.id)
                        loadScriptDetail(installed.id)
                    }.onFailure { error ->
                        failureCount += 1
                        repository.log(
                            userscriptId = candidate.scriptId,
                            level = "error",
                            pageUrl = null,
                            message = error.message ?: "userscript batch update failed",
                        )
                    }
                }
            } finally {
                uiStore.setApplyingSafeUpdates(false)
            }
            mainHandler.post {
                onToast(
                    context.resources.getQuantityString(
                        R.plurals.web_session_userscript_update_apply_summary,
                        completedCount,
                        completedCount,
                        failureCount,
                    ),
                )
            }
        }
    }

    fun setScriptsEnabled(
        scriptIds: Set<Long>,
        enabled: Boolean,
    ) {
        if (scriptIds.isEmpty()) {
            return
        }
        scope.launch {
            scriptIds.forEach { scriptId ->
                repository.setEnabled(scriptId, enabled)
            }
        }
    }

    fun deleteScripts(scriptIds: Set<Long>) {
        if (scriptIds.isEmpty()) {
            return
        }
        scope.launch {
            scriptIds.forEach { scriptId ->
                repository.deleteUserscript(scriptId)
            }
            mainHandler.post {
                onToast(
                    context.resources.getQuantityString(
                        R.plurals.web_session_userscript_deleted_count,
                        scriptIds.size,
                        scriptIds.size,
                    ),
                )
            }
        }
    }

    fun loadScriptDetail(scriptId: Long) {
        uiStore.updateDetail(
            UserscriptDetailUiState(
                userscriptId = scriptId,
                isLoading = true,
            ),
        )
        scope.launch {
            runCatching {
                val activeSource =
                    repository.readSource(scriptId)
                        ?: throw IllegalArgumentException("Userscript $scriptId does not exist")
                val draftId = UserscriptStorageLayout.draftIdForScript(scriptId)
                UserscriptDetailUiState(
                    userscriptId = scriptId,
                    activeSource = activeSource,
                    draft = repository.readDraft(draftId),
                    revisions = repository.listRevisions(scriptId),
                )
            }.onSuccess(uiStore::updateDetail)
                .onFailure { error ->
                    uiStore.updateDetail(
                        UserscriptDetailUiState(
                            userscriptId = scriptId,
                            error = error.message ?: "Unable to load userscript details",
                        ),
                    )
                }
        }
    }

    fun openNewEditor() {
        scope.launch {
            runCatching {
                repository.createDraft()
            }.onSuccess { draft ->
                refreshDrafts()
                uiStore.updateEditor(
                    UserscriptEditorUiState(
                        draftId = draft.draftId,
                        userscriptId = null,
                        buffer = draft.source,
                        persistedSourceHash = draft.sourceHash,
                    ),
                )
                mainHandler.post {
                    onOpenUserscriptEditor(draft.draftId, null)
                }
            }.onFailure { error ->
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun openExistingEditor(scriptId: Long) {
        val draftId = UserscriptStorageLayout.draftIdForScript(scriptId)
        uiStore.updateEditor(
            UserscriptEditorUiState(
                draftId = draftId,
                userscriptId = scriptId,
                isLoading = true,
            ),
        )
        scope.launch {
            runCatching {
                val activeSource =
                    repository.readSource(scriptId)
                        ?: throw IllegalArgumentException("Userscript $scriptId does not exist")
                val draft = repository.readDraft(draftId)
                UserscriptEditorUiState(
                    draftId = draftId,
                    userscriptId = scriptId,
                    activeSource = activeSource,
                    buffer = draft?.source ?: activeSource,
                    persistedSourceHash = draft?.sourceHash ?: sha256(activeSource),
                )
            }.onSuccess { editor ->
                uiStore.updateEditor(editor)
                mainHandler.post {
                    onOpenUserscriptEditor(draftId, scriptId)
                }
            }.onFailure { error ->
                uiStore.updateEditor(
                    UserscriptEditorUiState(
                        draftId = draftId,
                        userscriptId = scriptId,
                        error = error.message ?: "Unable to open userscript editor",
                    ),
                )
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun updateEditorBuffer(
        draftId: String,
        source: String,
    ) {
        uiStore.updateEditor(draftId) { editor ->
            // 格式化和应用都基于一个已持久化快照；期间接收输入会让旧结果覆盖新缓冲区。
            if (editor.isFormatting || editor.isApplying) {
                return@updateEditor editor
            }
            editor.copy(
                buffer = source,
                hasUnpersistedChanges = true,
                review = null,
                error = null,
            )
        }
    }

    fun persistEditorDraft(
        draftId: String,
        onComplete: (() -> Unit)? = null,
    ) {
        uiStore.updateEditor(draftId) { editor -> editor.copy(isPersisting = true, error = null) }
        scope.launch {
            runCatching {
                persistEditorDraftNow(draftId)
            }.onSuccess { draft ->
                refreshDrafts()
                uiStore.updateEditor(draftId) { editor ->
                    editor.copy(
                        persistedSourceHash = draft.sourceHash,
                        hasUnpersistedChanges = editor.buffer != draft.source,
                        isPersisting = false,
                    )
                }
                onComplete?.let { callback -> mainHandler.post(callback) }
            }.onFailure { error ->
                uiStore.updateEditor(draftId) { editor ->
                    editor.copy(
                        isPersisting = false,
                        error = error.message ?: "Unable to save userscript draft",
                    )
                }
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun discardEditorDraft(
        draftId: String,
        onComplete: (() -> Unit)? = null,
    ) {
        scope.launch {
            runCatching {
                repository.discardDraft(draftId)
            }.onSuccess {
                refreshDrafts()
                uiStore.removeEditor(draftId)
                onComplete?.let { callback -> mainHandler.post(callback) }
            }.onFailure { error ->
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    fun validateEditorDraft(draftId: String) {
        uiStore.updateEditor(draftId) { editor -> editor.copy(isValidating = true, error = null) }
        scope.launch {
            runCatching {
                val persisted = persistEditorDraftNow(draftId)
                persisted to repository.reviewDraft(draftId)
            }.onSuccess { (persisted, review) ->
                refreshDrafts()
                uiStore.updateEditor(draftId) { editor ->
                    val sourceUnchanged = editor.buffer == persisted.source
                    editor.copy(
                        persistedSourceHash = persisted.sourceHash,
                        hasUnpersistedChanges = !sourceUnchanged,
                        review = review.takeIf { sourceUnchanged },
                        isValidating = false,
                    )
                }
            }.onFailure { error ->
                uiStore.updateEditor(draftId) { editor ->
                    editor.copy(
                        isValidating = false,
                        error = error.message ?: "Unable to validate userscript draft",
                    )
                }
            }
        }
    }

    fun formatEditorDraft(draftId: String) {
        val sourceToFormat = uiStore.state.value.editors[draftId]?.buffer ?: return
        uiStore.updateEditor(draftId) { editor -> editor.copy(isFormatting = true, error = null) }
        scope.launch {
            runCatching {
                persistEditorDraftNow(draftId, sourceToFormat)
                repository.formatDraftSource(draftId)
            }.onSuccess { draft ->
                refreshDrafts()
                uiStore.updateEditor(draftId) { editor ->
                    if (editor.buffer == sourceToFormat) {
                        editor.copy(
                            buffer = draft.source,
                            persistedSourceHash = draft.sourceHash,
                            hasUnpersistedChanges = false,
                            review = null,
                            isFormatting = false,
                        )
                    } else {
                        editor.copy(
                            persistedSourceHash = draft.sourceHash,
                            hasUnpersistedChanges = true,
                            review = null,
                            isFormatting = false,
                        )
                    }
                }
            }.onFailure { error ->
                uiStore.updateEditor(draftId) { editor ->
                    editor.copy(
                        isFormatting = false,
                        error = error.message ?: "Unable to format userscript source",
                    )
                }
            }
        }
    }

    fun applyEditorDraft(draftId: String) {
        val sourceToApply = uiStore.state.value.editors[draftId]?.buffer ?: return
        uiStore.updateEditor(draftId) { editor -> editor.copy(isApplying = true, error = null) }
        scope.launch {
            runCatching {
                val persisted = persistEditorDraftNow(draftId, sourceToApply)
                uiStore.updateEditor(draftId) { editor ->
                    editor.copy(
                        persistedSourceHash = persisted.sourceHash,
                        hasUnpersistedChanges = editor.buffer != persisted.source,
                    )
                }
                val review = repository.reviewDraft(draftId)
                require(review.canApply) {
                    review.syntaxError
                        ?: review.preview?.blockedReasons?.joinToString()
                        ?: review.preview?.unknownGrants?.joinToString()
                        ?: "Userscript draft validation failed"
                }
                require(uiStore.state.value.editors[draftId]?.buffer == sourceToApply) {
                    "Userscript source changed during application"
                }
                repository.installDraft(draftId)
            }.onSuccess { installed ->
                refreshDrafts()
                uiStore.removeEditor(draftId)
                uiStore.removeUpdateCandidate(installed.id)
                loadScriptDetail(installed.id)
                mainHandler.post {
                    onToast(
                        context.getString(
                            R.string.web_session_userscript_draft_applied,
                            installed.name,
                        ),
                    )
                    onOpenUserscriptDetail(installed.id)
                }
            }.onFailure { error ->
                uiStore.updateEditor(draftId) { editor ->
                    editor.copy(
                        isApplying = false,
                        error = error.message ?: "Unable to apply userscript draft",
                    )
                }
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    private suspend fun persistEditorDraftNow(
        draftId: String,
        source: String =
            uiStore.state.value.editors[draftId]?.buffer
                ?: throw IllegalStateException("Userscript editor $draftId is not open"),
    ) =
        repository.saveDraft(
            draftId = draftId,
            source = source,
        )

    fun openDraftEditor(draftId: String) {
        uiStore.updateEditor(
            UserscriptEditorUiState(
                draftId = draftId,
                userscriptId = null,
                isLoading = true,
            ),
        )
        scope.launch {
            runCatching {
                val draft =
                    repository.readDraft(draftId)
                        ?: throw IllegalArgumentException("Userscript draft $draftId does not exist")
                val activeSource =
                    draft.userscriptId?.let { scriptId ->
                        repository.readSource(scriptId)
                    }
                UserscriptEditorUiState(
                    draftId = draft.draftId,
                    userscriptId = draft.userscriptId,
                    activeSource = activeSource,
                    buffer = draft.source,
                    persistedSourceHash = draft.sourceHash,
                )
            }.onSuccess { editor ->
                uiStore.updateEditor(editor)
                mainHandler.post {
                    onOpenUserscriptEditor(editor.draftId, editor.userscriptId)
                }
            }.onFailure { error ->
                uiStore.removeEditor(draftId)
                mainHandler.post {
                    onToast(error.message ?: context.getString(R.string.web_session_userscript_install_failed))
                }
            }
        }
    }

    private fun refreshDrafts() {
        scope.launch {
            runCatching {
                repository.listDrafts()
            }.onSuccess(uiStore::updateDrafts)
                .onFailure { error ->
                    AppLogger.e(TAG, "Failed to refresh userscript drafts", error)
                }
        }
    }

    private fun sha256(value: String): String {
        val digest =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun currentInstalledScript(scriptId: Long, callback: (UserscriptListItem?) -> Unit) {
        scope.launch {
            val item = repository.getInstalledScript(scriptId)
            mainHandler.post { callback(item) }
        }
    }

    fun interceptWebRequest(
        sessionId: String,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (!uiStore.state.value.userScriptsAllowed) {
            return null
        }
        val url = request.url?.toString().orEmpty()
        if (url.isBlank()) {
            return null
        }
        val resolution =
            webRequestEngine.resolve(
                sessionId = sessionId,
                url = url,
                requestType = resolveWebRequestType(request)
            )
        if (resolution.matches.isEmpty()) {
            return null
        }
        resolution.matches.forEach { match ->
            dispatchHostEvent(
                sessionId = sessionId,
                userscriptId = match.scriptId,
                eventType = "web_request_event",
                payload =
                    JSONObject()
                        .put("registrationId", match.registrationId)
                        .put("scriptId", match.scriptId)
                        .put("url", url)
                        .put("type", resolveWebRequestType(request))
                        .put("source", match.source),
            )
        }
        val action = resolution.mergedAction
        if (!action.requiresInterception()) {
            return null
        }
        if (action.cancel) {
            return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        val targetUrl = action.redirectUrl ?: url
        return runCatching {
            val requestBuilder = Request.Builder().url(targetUrl)
            request.requestHeaders.orEmpty().forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            action.requestHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            val response = requestClient.newCall(requestBuilder.get().build()).execute()
            val bodyBytes =
                action.responseBody?.toByteArray(Charsets.UTF_8)
                    ?: response.body?.bytes()
                    ?: ByteArray(0)
            val responseHeaders =
                response.headers.toMultimap()
                    .mapValues { (_, values) -> values.joinToString(", ") }
                    .toMutableMap()
                    .apply { putAll(action.responseHeaders) }
            val mimeType =
                response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.ifBlank { "application/octet-stream" }
                    ?: "application/octet-stream"
            val encoding =
                response.header("Content-Type")
                    ?.substringAfter("charset=", "")
                    ?.substringBefore(';')
                    ?.ifBlank { "utf-8" }
                    ?: "utf-8"
            WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message,
                responseHeaders,
                ByteArrayInputStream(bodyBytes)
            ).also {
                response.close()
            }
        }.getOrElse { error ->
            AppLogger.w(TAG, "userscript webRequest intercept failed: ${error.message}")
            null
        }
    }

    private fun newAuthorizationToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun isBridgeMessageAuthorized(
        sessionId: String,
        messageType: String,
        payload: JSONObject,
    ): Boolean {
        if (messageType == "bootstrap_request") {
            return true
        }
        val isolated = sessionBindings[sessionId]?.isolatedRuntime ?: return false
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            return false
        }
        val token = payload.optString("authorizationToken", "")
        if (token.isBlank()) {
            return false
        }
        val authorization = isolated.scriptAuthorizations[token] ?: return false
        return UserscriptBridgeAuthorizationPolicy.isAuthorized(
            expectedUserscriptId = authorization.scriptId,
            messageType = messageType,
            payloadUserscriptId = scriptId,
            presentedToken = token,
            authorization = authorization,
        )
    }

    private fun bridgeListener(
        sessionId: String,
        bridgeScope: BridgeScope,
    ): WebViewCompat.WebMessageListener =
        object : WebViewCompat.WebMessageListener {
            override fun onPostMessage(
                view: WebView,
                message: WebMessageCompat,
                sourceOrigin: android.net.Uri,
                isMainFrame: Boolean,
                replyProxy: JavaScriptReplyProxy,
            ) {
                if (sessionBindings[sessionId]?.webView !== view) {
                    return
                }
                handleBridgeMessage(
                    sessionId = sessionId,
                    rawMessage = message.data.orEmpty(),
                    replyProxy = replyProxy,
                    isMainFrame = isMainFrame,
                    bridgeScope = bridgeScope,
                    sourceOrigin = sourceOrigin.toString(),
                )
            }
        }

    private fun handleBridgeMessage(
        sessionId: String,
        rawMessage: String,
        replyProxy: JavaScriptReplyProxy,
        isMainFrame: Boolean,
        bridgeScope: BridgeScope,
        sourceOrigin: String,
    ) {
        val message = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return
        val type = message.optString("type", "")
        val requestId = message.optString("requestId", "")
        val payload = message.optJSONObject("payload") ?: JSONObject()
        if (!uiStore.state.value.userScriptsAllowed) {
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "userscript_permission_required")
            }
            return
        }
        if (bridgeScope == BridgeScope.PAGE && type != "bootstrap_request") {
            return
        }
        if (
            bridgeScope == BridgeScope.ISOLATED &&
                !isBridgeMessageAuthorized(
                    sessionId = sessionId,
                    messageType = type,
                    payload = payload,
                )
        ) {
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "userscript_bridge_not_authorized")
            }
            return
        }
        when (type) {
            "bootstrap_request" -> {
                val binding = sessionBindings[sessionId]
                val isolated = binding?.isolatedRuntime
                val registered =
                    when (bridgeScope) {
                        BridgeScope.PAGE -> binding?.pageBootstrapReplyProxies?.add(replyProxy) == true
                        BridgeScope.ISOLATED -> isolated?.replyProxies?.add(replyProxy) == true
                    }
                if (!registered) {
                    if (requestId.isNotBlank()) {
                        postRpcError(replyProxy, requestId, "userscript_bootstrap_already_initialized")
                    }
                    return
                }
                val pageUrl =
                    UserscriptBootstrapUrlPolicy.resolve(
                        runtimeHref = payload.optString("href", ""),
                        sourceOrigin = sourceOrigin,
                        isolatedWorld = bridgeScope == BridgeScope.ISOLATED,
                    )
                if (pageUrl == null) {
                    if (requestId.isNotBlank()) {
                        postRpcError(replyProxy, requestId, "userscript_bootstrap_page_url_unavailable")
                    }
                    return
                }
                scope.launch {
                    runCatching {
                        val executionWorld =
                            when (bridgeScope) {
                                BridgeScope.PAGE -> UserscriptExecutionWorld.PAGE
                                BridgeScope.ISOLATED -> UserscriptExecutionWorld.ISOLATED
                            }
                        val bootstrapPayload =
                            repository.buildBootstrapPayload(
                                sessionId = sessionId,
                                pageUrl = pageUrl,
                                isTopFrame = isMainFrame,
                                executionWorld = executionWorld,
                                runtimeCapabilities = runtimeCapabilities,
                            )
                        val authorizedPayload =
                            if (bridgeScope == BridgeScope.ISOLATED && isolated != null) {
                                bootstrapPayload.copy(
                                    scripts =
                                        bootstrapPayload.scripts.map { script ->
                                            val token = newAuthorizationToken()
                                            isolated.scriptAuthorizations[token] =
                                                UserscriptBridgeAuthorization(
                                                    scriptId = script.scriptId,
                                                    token = token,
                                                    grants =
                                                        UserscriptCapabilityRegistry
                                                            .knownGrants(script.grants)
                                                            .toSet(),
                                                )
                                            isolated.scriptGrants[script.scriptId] =
                                                UserscriptCapabilityRegistry
                                                    .knownGrants(script.grants)
                                                    .toSet()
                                            script.copy(authorizationToken = token)
                                        },
                                )
                            } else {
                                bootstrapPayload
                            }
                        if (isMainFrame) {
                            val state = sessionPageStates.getOrPut(sessionId) { SessionPageState(pageUrl = pageUrl) }
                            state.pageUrl = pageUrl
                            authorizedPayload.scripts.forEach { script ->
                                state.scriptStatuses[script.scriptId] =
                                    UserscriptPageRuntimeStatus(
                                        state =
                                            if (bridgeScope == BridgeScope.ISOLATED) {
                                                UserscriptPageRuntimeState.QUEUED
                                            } else {
                                                UserscriptPageRuntimeState.MATCHED
                                            },
                                        detail =
                                            if (bridgeScope == BridgeScope.ISOLATED) {
                                                script.runAt
                                            } else {
                                                "page · ${script.runAt}"
                                            },
                                    )
                            }
                            publishVisibleStatuses()
                        }
                        postRpcSuccess(
                            replyProxy = replyProxy,
                            requestId = requestId,
                            payload =
                                JSONObject().put(
                                    "payloadJson",
                                    json.encodeToString(authorizedPayload),
                                )
                        )
                    }.onFailure { error ->
                        postRpcError(replyProxy, requestId, error.message ?: "bootstrap_request_failed")
                    }
                }
            }

            "script_status" -> {
                if (!isMainFrame) {
                    return
                }
                val scriptId = payload.optLong("scriptId")
                if (scriptId > 0L) {
                    upsertRuntimeStatus(
                        sessionId = sessionId,
                        scriptId = scriptId,
                        rawState = payload.optString("state", ""),
                        detail = payload.optString("message", "").ifBlank { null }
                    )
                }
            }

            "runtime_log" -> {
                val pageUrl = trustedPageUrl(sessionId)
                scope.launch {
                    repository.log(
                        userscriptId = payload.optLong("scriptId").takeIf { it > 0L },
                        level = payload.optString("level", "info"),
                        pageUrl = pageUrl.ifBlank { null },
                        message = payload.optString("message", "userscript runtime message")
                    )
                }
            }

            "register_menu_command" -> {
                if (!isMainFrame) {
                    return
                }
                val commandId = payload.optString("commandId", "").trim()
                val title = payload.optString("title", "").trim()
                val userscriptId = payload.optLong("scriptId")
                val binding = sessionBindings[sessionId]
                if (binding != null && commandId.isNotBlank() && title.isNotBlank()) {
                    val hostCommandId = "$userscriptId:$commandId"
                    binding.menuCommands[hostCommandId] =
                        UserscriptPageMenuCommand(
                            commandId = hostCommandId,
                            title = title,
                            userscriptId = userscriptId,
                            runtimeCommandId = commandId,
                        )
                    onMenuCommandsChanged(sessionId)
                }
            }

            "unregister_menu_command" -> {
                if (!isMainFrame) {
                    return
                }
                val commandId = payload.optString("commandId", "").trim()
                val userscriptId = payload.optLong("scriptId")
                sessionBindings[sessionId]?.menuCommands?.remove("$userscriptId:$commandId")
                onMenuCommandsChanged(sessionId)
            }

            "storage_set" -> {
                val scriptId = payload.optLong("scriptId")
                val key = payload.optString("key", "")
                val valueJson = payload.optString("valueJson", "null")
                if (scriptId > 0L && key.isNotBlank()) {
                    scope.launch {
                        persistValueAndBroadcast(
                            sourceSessionId = sessionId,
                            scriptId = scriptId,
                            key = key,
                            valueJson = valueJson
                        )
                    }
                }
            }

            "storage_set_many" -> {
                val scriptId = payload.optLong("scriptId")
                val values = payload.optJSONObject("values") ?: JSONObject()
                if (scriptId > 0L) {
                    scope.launch {
                        values.keys().forEach { key ->
                            persistValueAndBroadcast(
                                sourceSessionId = sessionId,
                                scriptId = scriptId,
                                key = key,
                                valueJson = values.optString(key, "null")
                            )
                        }
                    }
                }
            }

            "storage_delete" -> {
                val scriptId = payload.optLong("scriptId")
                val key = payload.optString("key", "")
                if (scriptId > 0L && key.isNotBlank()) {
                    scope.launch {
                        deleteValueAndBroadcast(
                            sourceSessionId = sessionId,
                            scriptId = scriptId,
                            key = key
                        )
                    }
                }
            }

            "storage_delete_many" -> {
                val scriptId = payload.optLong("scriptId")
                val keys = payload.optJSONArray("keys") ?: org.json.JSONArray()
                if (scriptId > 0L) {
                    scope.launch {
                        for (index in 0 until keys.length()) {
                            val key = keys.optString(index).trim()
                            if (key.isNotBlank()) {
                                deleteValueAndBroadcast(
                                    sourceSessionId = sessionId,
                                    scriptId = scriptId,
                                    key = key
                                )
                            }
                        }
                    }
                }
            }

            "gm_open_in_tab" -> {
                val url = payload.optString("url", "").trim()
                if (url.isBlank()) {
                    if (requestId.isNotBlank()) {
                        postRpcError(replyProxy, requestId, "open_tab_url_is_empty")
                    }
                    return
                }
                mainHandler.post {
                    val openedSessionId =
                        onOpenTab(sessionId, url, payload.optBoolean("active", true))
                    if (!openedSessionId.isNullOrBlank()) {
                        openedTabOwners[openedSessionId] =
                            OpenedTabOwner(
                                sessionId = sessionId,
                                userscriptId = payload.optLong("scriptId"),
                            )
                        if (requestId.isNotBlank()) {
                            postRpcSuccess(
                                replyProxy,
                                requestId,
                                JSONObject().put("sessionId", openedSessionId)
                            )
                        }
                    } else if (requestId.isNotBlank()) {
                        postRpcError(replyProxy, requestId, "open_tab_failed")
                    }
                }
            }

            "gm_focus_tab" -> {
                val userscriptId = payload.optLong("scriptId")
                val targetSessionId = payload.optString("sessionId", sessionId).trim()
                val owner = openedTabOwners[targetSessionId]
                val grants =
                    sessionBindings[sessionId]
                        ?.isolatedRuntime
                        ?.scriptGrants
                        ?.get(userscriptId)
                        .orEmpty()
                val canControl =
                    UserscriptTabControlPolicy.canControl(
                        sourceSessionId = sessionId,
                        targetSessionId = targetSessionId,
                        userscriptId = userscriptId,
                        grants = grants,
                        controlKind = payload.optString("controlKind", ""),
                        currentSessionGrant = "window.focus",
                        ownerSessionId = owner?.sessionId,
                        ownerUserscriptId = owner?.userscriptId,
                    )
                mainHandler.post {
                    if (
                        targetSessionId.isBlank() ||
                            !canControl ||
                            !sessionBindings.containsKey(targetSessionId)
                    ) {
                        if (requestId.isNotBlank()) {
                            postRpcError(replyProxy, requestId, "focus_tab_failed")
                        }
                        return@post
                    }
                    onActivateSession(targetSessionId)
                    if (requestId.isNotBlank()) {
                        postRpcSuccess(replyProxy, requestId, JSONObject().put("sessionId", targetSessionId))
                    }
                }
            }

            "gm_close_tab" -> {
                val userscriptId = payload.optLong("scriptId")
                val targetSessionId = payload.optString("sessionId", sessionId).trim()
                val owner = openedTabOwners[targetSessionId]
                val grants =
                    sessionBindings[sessionId]
                        ?.isolatedRuntime
                        ?.scriptGrants
                        ?.get(userscriptId)
                        .orEmpty()
                val canControl =
                    UserscriptTabControlPolicy.canControl(
                        sourceSessionId = sessionId,
                        targetSessionId = targetSessionId,
                        userscriptId = userscriptId,
                        grants = grants,
                        controlKind = payload.optString("controlKind", ""),
                        currentSessionGrant = "window.close",
                        ownerSessionId = owner?.sessionId,
                        ownerUserscriptId = owner?.userscriptId,
                    )
                mainHandler.post {
                    val closed =
                        if (targetSessionId.isNotBlank() && canControl) {
                            onCloseSession(targetSessionId)
                        } else {
                            false
                        }
                    if (requestId.isNotBlank()) {
                        if (closed) {
                            postRpcSuccess(replyProxy, requestId, JSONObject().put("sessionId", targetSessionId))
                        } else {
                            postRpcError(replyProxy, requestId, "close_tab_failed")
                        }
                    }
                }
            }

            "gm_get_tab" -> handleGetTab(sessionId, payload, replyProxy, requestId)
            "gm_save_tab" -> handleSaveTab(sessionId, payload, replyProxy, requestId)
            "gm_get_tabs" -> handleGetTabs(payload, replyProxy, requestId)
            "gm_cookie" -> handleCookie(sessionId, payload, replyProxy, requestId)
            "gm_audio" -> handleAudio(sessionId, payload, replyProxy, requestId)
            "gm_web_request_register" -> handleRegisterWebRequest(sessionId, payload, replyProxy, requestId)
            "gm_web_request_unregister" -> handleUnregisterWebRequest(payload, replyProxy, requestId)
            "gm_set_clipboard" -> handleSetClipboard(payload, replyProxy, requestId)
            "gm_notification" -> handleNotification(payload, replyProxy, requestId)
            "gm_download" -> handleDownload(sessionId, payload, replyProxy, requestId)
            "gm_xmlhttp_request" -> handleXmlHttpRequest(sessionId, payload, replyProxy, requestId)
            "gm_abort_request" -> {
                val gmRequestId = payload.optString("requestId", "").trim()
                val userscriptId = payload.optLong("scriptId")
                if (gmRequestId.isNotBlank()) {
                    val requestKey = "$sessionId:$userscriptId:$gmRequestId"
                    abortedRequestKeys.add(requestKey)
                    activeCalls.remove(requestKey)?.cancel()
                }
            }
        }
    }

    private fun handleSetClipboard(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val text = payload.optString("text", "")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "clipboard_service_unavailable")
            }
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("userscript", text))
        if (requestId.isNotBlank()) {
            postRpcSuccess(replyProxy, requestId, JSONObject().put("text", text))
        }
    }

    private fun handleNotification(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val title = payload.optString("title", "").ifBlank { context.getString(R.string.web_session_userscript_notification_title) }
        val text = payload.optString("text", "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onToast(context.getString(R.string.web_session_userscript_notification_denied))
                if (requestId.isNotBlank()) {
                    postRpcError(replyProxy, requestId, "notification_permission_denied")
                }
                return
            }
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null) {
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "notification_manager_unavailable")
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.web_session_userscript_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_kiyori_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID + payload.optLong("scriptId").toInt(), notification)
        if (requestId.isNotBlank()) {
            postRpcSuccess(
                replyProxy,
                requestId,
                JSONObject()
                    .put("shown", true)
                    .put("title", title)
                    .put("text", text)
            )
        }
    }

    private fun handleDownload(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val url = payload.optString("url", "").trim()
        if (url.isBlank()) {
            onToast(context.getString(R.string.web_session_userscript_download_failed))
            if (requestId.isNotBlank()) {
                postRpcError(replyProxy, requestId, "download_url_is_empty")
            }
            return
        }
        mainHandler.post {
            onDownload(sessionId, url, payload.optString("fileName", "").ifBlank { null })
            if (requestId.isNotBlank()) {
                postRpcSuccess(
                    replyProxy,
                    requestId,
                    JSONObject()
                        .put("started", true)
                        .put("url", url)
                        .put("fileName", payload.optString("fileName", ""))
                )
            }
        }
    }

    private suspend fun persistValueAndBroadcast(
        sourceSessionId: String,
        scriptId: Long,
        key: String,
        valueJson: String
    ) {
        val oldValueJson = repository.readValueJson(scriptId, key)
        repository.persistValue(scriptId, key, valueJson)
        broadcastStorageChange(
            sourceSessionId = sourceSessionId,
            change =
                UserscriptStorageChange(
                    scriptId = scriptId,
                    key = key,
                    oldValueJson = oldValueJson,
                    newValueJson = valueJson,
                    remote = true
                )
        )
    }

    private suspend fun deleteValueAndBroadcast(
        sourceSessionId: String,
        scriptId: Long,
        key: String
    ) {
        val oldValueJson = repository.readValueJson(scriptId, key)
        repository.deleteValue(scriptId, key)
        broadcastStorageChange(
            sourceSessionId = sourceSessionId,
            change =
                UserscriptStorageChange(
                    scriptId = scriptId,
                    key = key,
                    oldValueJson = oldValueJson,
                    newValueJson = null,
                    remote = true
                )
        )
    }

    private fun broadcastStorageChange(
        sourceSessionId: String,
        change: UserscriptStorageChange
    ) {
        val payload = storageNotifier.toPayload(change)
        sessionBindings.keys.forEach { targetSessionId ->
            if (targetSessionId != sourceSessionId) {
                dispatchHostEvent(
                    sessionId = targetSessionId,
                    userscriptId = change.scriptId,
                    eventType = "storage_changed",
                    payload = payload,
                )
            }
        }
    }

    private fun handleGetTab(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        postRpcSuccess(
            replyProxy,
            requestId,
            JSONObject().put("tabJson", tabStateStore.getTab(scriptId, sessionId))
        )
    }

    private fun handleSaveTab(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        val tabJson = payload.optString("tabJson", "{}")
        tabStateStore.saveTab(scriptId, sessionId, tabJson)
        postRpcSuccess(replyProxy, requestId, JSONObject().put("tabJson", tabJson))
    }

    private fun handleGetTabs(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        val jsonPayload =
            JSONObject()
        tabStateStore.getTabs(scriptId).forEach { (sessionId, tabJson) ->
            jsonPayload.put(sessionId, tabJson)
        }
        postRpcSuccess(replyProxy, requestId, JSONObject().put("tabsJson", jsonPayload.toString()))
    }

    private fun handleCookie(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val cookieService = sessionBindings[sessionId]?.cookieService
        if (cookieService == null) {
            postRpcError(replyProxy, requestId, "session_not_found")
            return
        }
        val details = payload.optJSONObject("details") ?: JSONObject()
        val pageUrl = trustedPageUrl(sessionId)
        if (pageUrl.isBlank()) {
            postRpcError(replyProxy, requestId, "trusted_page_url_unavailable")
            return
        }
        runCatching {
            when (payload.optString("action", "").trim()) {
                "list" -> {
                    val cookiesJson = org.json.JSONArray()
                    cookieService.list(details, pageUrl).forEach { cookie ->
                        cookiesJson.put(cookie.toJson())
                    }
                    postRpcSuccess(replyProxy, requestId, JSONObject().put("cookiesJson", cookiesJson.toString()))
                }
                "set" -> {
                    val cookie = cookieService.set(details, pageUrl)
                    postRpcSuccess(replyProxy, requestId, JSONObject().put("cookieJson", cookie.toJson().toString()))
                }
                "delete" -> {
                    cookieService.delete(details, pageUrl)
                    postRpcSuccess(replyProxy, requestId, JSONObject().put("deleted", true))
                }
                else -> postRpcError(replyProxy, requestId, "unsupported_cookie_action")
            }
        }.onFailure { error ->
            postRpcError(replyProxy, requestId, error.message ?: "gm_cookie_failed")
        }
    }

    private fun handleAudio(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val binding = sessionBindings[sessionId]
        if (binding == null) {
            postRpcError(replyProxy, requestId, "session_not_found")
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            postRpcError(replyProxy, requestId, "mute_audio_not_supported")
            return
        }
        val action = payload.optString("action", "").trim()
        mainHandler.post {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
                postRpcError(replyProxy, requestId, "mute_audio_not_supported")
                return@post
            }
            runCatching {
                when (action) {
                    "get_state" -> {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
                            val muted = WebViewCompat.isAudioMuted(binding.webView)
                            audioMuteStates[sessionId] = muted
                            postRpcSuccess(
                                replyProxy,
                                requestId,
                                JSONObject().put("stateJson", JSONObject().put("muted", muted).toString())
                            )
                        } else {
                            error("WebView audio-mute support changed while reading state")
                        }
                    }
                    "set_mute" -> {
                        val muted = payload.optBoolean("muted", false)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
                            WebViewCompat.setAudioMuted(binding.webView, muted)
                            audioMuteStates[sessionId] = muted
                            dispatchHostEventToGrant(
                                sessionId = sessionId,
                                requiredGrant = "GM.audio",
                                eventType = "audio_state_changed",
                                payload = JSONObject().put("muted", muted),
                            )
                            postRpcSuccess(
                                replyProxy,
                                requestId,
                                JSONObject().put("stateJson", JSONObject().put("muted", muted).toString())
                            )
                        } else {
                            error("WebView audio-mute support changed while setting state")
                        }
                    }
                    else -> postRpcError(replyProxy, requestId, "unsupported_audio_action")
                }
            }.onFailure { error ->
                postRpcError(replyProxy, requestId, error.message ?: "gm_audio_failed")
            }
        }
    }

    private fun handleRegisterWebRequest(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val scriptId = payload.optLong("scriptId")
        val rulesJson = payload.optString("rulesJson", "[]")
        if (scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_script_id")
            return
        }
        runCatching {
            val registrationId =
                webRequestEngine.register(
                    scriptId = scriptId,
                    sessionId = sessionId,
                    rulesJson = rulesJson,
                    source = payload.optString("source", "runtime").ifBlank { "runtime" }
                )
            postRpcSuccess(replyProxy, requestId, JSONObject().put("registrationId", registrationId))
        }.onFailure { error ->
            postRpcError(replyProxy, requestId, error.message ?: "gm_web_request_register_failed")
        }
    }

    private fun handleUnregisterWebRequest(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val registrationId = payload.optString("registrationId", "").trim()
        val scriptId = payload.optLong("scriptId")
        if (registrationId.isBlank() || scriptId <= 0L) {
            postRpcError(replyProxy, requestId, "invalid_registration_id")
            return
        }
        val removed = webRequestEngine.unregister(registrationId, scriptId)
        if (removed) {
            postRpcSuccess(replyProxy, requestId, JSONObject().put("registrationId", registrationId))
        } else {
            postRpcError(replyProxy, requestId, "registration_not_found")
        }
    }

    private fun handleXmlHttpRequest(
        sessionId: String,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        requestId: String
    ) {
        val gmRequestId = payload.optString("requestId", "").trim()
        val scriptId = payload.optLong("scriptId")
        val targetUrl = payload.optString("url", "").trim()
        val pageUrl = trustedPageUrl(sessionId)
        if (gmRequestId.isBlank() || scriptId <= 0L || targetUrl.isBlank() || pageUrl.isBlank()) {
            postRpcError(replyProxy, requestId, "invalid_xhr_request")
            return
        }
        scope.launch {
            val installed = repository.getInstalledScript(scriptId)
            if (installed == null) {
                postRpcError(replyProxy, requestId, "userscript_not_found")
                return@launch
            }
            val metadata = installed.toParsedMetadata()
            if (!UserscriptMatcher.isConnectAllowed(metadata, pageUrl, targetUrl)) {
                repository.log(scriptId, "error", pageUrl, "GM_xmlhttpRequest blocked by @connect: $targetUrl")
                postRpcError(replyProxy, requestId, "connect_not_allowed")
                return@launch
            }
            runCatching {
                startXmlHttpRequest(
                    sessionId = sessionId,
                    gmRequestId = gmRequestId,
                    scriptId = scriptId,
                    pageUrl = pageUrl,
                    metadata = metadata,
                    payload = payload,
                    replyProxy = replyProxy,
                )
                postRpcSuccess(replyProxy, requestId, JSONObject().put("accepted", true))
            }.onFailure { error ->
                repository.log(scriptId, "error", pageUrl, error.message ?: "GM_xmlhttpRequest failed")
                postRpcError(replyProxy, requestId, error.message ?: "xhr_start_failed")
            }
        }
    }

    private fun startXmlHttpRequest(
        sessionId: String,
        gmRequestId: String,
        scriptId: Long,
        pageUrl: String,
        metadata: ParsedUserscriptMetadata,
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy
    ) {
        val cookieService =
            sessionBindings[sessionId]?.cookieService
                ?: throw IllegalStateException("Userscript session is not attached")
        val method = payload.optString("method", "GET").uppercase()
        val url = payload.optString("url", "")
        val responseType = payload.optString("responseType", "text")
        val timeoutMs = payload.optLong("timeoutMs", 0L).coerceAtLeast(0L)
        val anonymous = payload.optBoolean("anonymous", false)
        val bodyData = payload.opt("data")
        val requestType = payload.optString("requestType", "xhr").ifBlank { "xhr" }
        val headers = jsonObjectToMap(payload.optJSONObject("headers")).toMutableMap()
        val webRequestResolution = webRequestEngine.resolve(sessionId, url, requestType)
        webRequestResolution.matches.forEach { match ->
            dispatchHostEvent(
                sessionId = sessionId,
                userscriptId = match.scriptId,
                eventType = "web_request_event",
                payload =
                    JSONObject()
                        .put("registrationId", match.registrationId)
                        .put("scriptId", match.scriptId)
                        .put("url", url)
                        .put("type", requestType)
                        .put("source", match.source),
            )
        }
        val action = webRequestResolution.mergedAction
        if (action.cancel) {
            throw IllegalStateException("GM_webRequest canceled request")
        }
        val targetUrl = action.redirectUrl ?: url
        headers.putAll(action.requestHeaders)

        val requestBuilder =
            Request.Builder()
                .url(targetUrl)
                .tag(
                    UserscriptConnectAuthorization::class.java,
                    UserscriptConnectAuthorization(
                        metadata = metadata,
                        pageUrl = pageUrl,
                    ),
                )
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        if (!anonymous) {
            cookieService.requestHeader(targetUrl)?.let { cookie ->
                if (!headers.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                    requestBuilder.header("Cookie", cookie)
                }
            }
        }

        val requestBody =
            if (method == "GET" || method == "HEAD") {
                null
            } else {
                val bodyText =
                    if (bodyData == null || bodyData === JSONObject.NULL) {
                        ""
                    } else {
                        bodyData.toString()
                    }
                val mediaType = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.toMediaTypeOrNull()
                bodyText.toRequestBody(mediaType)
            }
        requestBuilder.method(method, requestBody)

        val call = requestClient.newCall(requestBuilder.build())
        if (timeoutMs > 0L) {
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val requestKey = "$sessionId:$scriptId:$gmRequestId"
        activeCalls[requestKey] = call
        postXhrEvent(
            replyProxy = replyProxy,
            requestId = gmRequestId,
            eventType = "readystatechange",
            terminal = false,
            payload =
                JSONObject()
                    .put("readyState", 1)
                    .put("status", 0)
                    .put("statusText", "")
                    .put("finalUrl", targetUrl)
        )
        postXhrEvent(
            replyProxy = replyProxy,
            requestId = gmRequestId,
            eventType = "loadstart",
            terminal = false,
            payload =
                JSONObject()
                    .put("readyState", 1)
                    .put("status", 0)
                    .put("statusText", "")
                    .put("finalUrl", targetUrl)
        )

        scope.launch {
            try {
                val response = call.execute()
                if (!anonymous) {
                    cookieService.acceptResponseCookies(
                        response.request.url.toString(),
                        response.headers("Set-Cookie"),
                    )
                }
                val body = response.body
                val total = body?.contentLength()?.takeIf { it >= 0L } ?: 0L
                val output = ByteArrayOutputStream()
                if (body != null) {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var loaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) {
                                break
                            }
                            output.write(buffer, 0, count)
                            loaded += count
                            postXhrEvent(
                                replyProxy = replyProxy,
                                requestId = gmRequestId,
                                eventType = "readystatechange",
                                terminal = false,
                                payload =
                                    JSONObject()
                                        .put("loaded", loaded)
                                        .put("total", total)
                                        .put("readyState", 3)
                            )
                            postXhrEvent(
                                replyProxy = replyProxy,
                                requestId = gmRequestId,
                                eventType = "progress",
                                terminal = false,
                                payload =
                                    JSONObject()
                                        .put("loaded", loaded)
                                        .put("total", total)
                                        .put("readyState", 3)
                            )
                        }
                    }
                }
                val bytes = output.toByteArray()
                val headersJson = JSONObject()
                response.headers.toMultimap().forEach { (key, values) ->
                    headersJson.put(key, values.joinToString(", "))
                }
                val payloadJson =
                    JSONObject()
                        .put("status", response.code)
                        .put("statusText", response.message)
                        .put("readyState", 4)
                        .put("headers", headersJson)
                        .put("finalUrl", response.request.url.toString())
                        .put("loaded", bytes.size.toLong())
                        .put("total", total)
                when (responseType.lowercase()) {
                    "arraybuffer", "blob" -> {
                        val resolvedBytes =
                            action.responseBody?.toByteArray(Charsets.UTF_8)
                                ?: bytes
                        payloadJson.put(
                            "response",
                            Base64.encodeToString(resolvedBytes, Base64.NO_WRAP)
                        )
                        payloadJson.put("responseEncoding", "base64")
                        payloadJson.put("responseType", responseType.lowercase())
                    }
                    else -> {
                        val resolvedText = action.responseBody ?: bytes.toString(Charsets.UTF_8)
                        payloadJson.put("responseText", resolvedText)
                        payloadJson.put("response", resolvedText)
                    }
                }
                action.responseHeaders.forEach { (key, value) ->
                    headersJson.put(key, value)
                }
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "readystatechange",
                    terminal = false,
                    payload = JSONObject(payloadJson.toString())
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "load",
                    terminal = false,
                    payload = payloadJson
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "loadend",
                    terminal = true,
                    payload = payloadJson
                )
                response.close()
            } catch (error: Throwable) {
                if (abortedRequestKeys.remove(requestKey)) {
                    return@launch
                }
                val eventType = if (error is java.io.InterruptedIOException) "timeout" else "error"
                val errorPayload =
                    JSONObject()
                        .put("status", 0)
                        .put("statusText", error.message ?: eventType)
                        .put("readyState", 4)
                        .put("finalUrl", payload.optString("url", ""))
                        .put("responseText", "")
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "readystatechange",
                    terminal = false,
                    payload = JSONObject(errorPayload.toString())
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = eventType,
                    terminal = false,
                    payload = errorPayload
                )
                postXhrEvent(
                    replyProxy = replyProxy,
                    requestId = gmRequestId,
                    eventType = "loadend",
                    terminal = true,
                    payload = errorPayload
                )
                repository.log(scriptId, "error", pageUrl, error.message ?: "GM_xmlhttpRequest failed")
            } finally {
                activeCalls.remove(requestKey)
                abortedRequestKeys.remove(requestKey)
            }
        }
    }

    private fun jsonObjectToMap(raw: JSONObject?): Map<String, String> {
        if (raw == null) {
            return emptyMap()
        }
        return buildMap {
            raw.keys().forEach { key ->
                put(key, raw.optString(key, ""))
            }
        }
    }

    private fun rebuildAllSessionBaselines() {
        sessionPageStates.keys.forEach { sessionId ->
            rebuildSessionBaseline(sessionId)
        }
    }

    private fun rebuildSessionBaseline(sessionId: String) {
        val pageState = sessionPageStates[sessionId] ?: return
        val scripts = uiStore.state.value.installedScripts
        val currentPageUrl = pageState.pageUrl
        val existing = LinkedHashMap(pageState.scriptStatuses)
        pageState.scriptStatuses.clear()
        scripts.forEach { script ->
            pageState.scriptStatuses[script.id] = baselineStatus(script, currentPageUrl)
        }
        existing.forEach { (scriptId, status) ->
            val baseline = pageState.scriptStatuses[scriptId] ?: return@forEach
            if (baseline.state == UserscriptPageRuntimeState.MATCHED ||
                baseline.state == UserscriptPageRuntimeState.QUEUED ||
                baseline.state == UserscriptPageRuntimeState.RUNNING ||
                baseline.state == UserscriptPageRuntimeState.SUCCESS ||
                baseline.state == UserscriptPageRuntimeState.ERROR
            ) {
                pageState.scriptStatuses[scriptId] = status
            }
        }
        publishVisibleStatuses()
    }

    private fun baselineStatus(
        script: UserscriptListItem,
        pageUrl: String
    ): UserscriptPageRuntimeStatus =
        UserscriptPageStatusPolicy.resolve(
            script = script,
            userScriptsAllowed = uiStore.state.value.userScriptsAllowed,
            pageUrl = pageUrl,
            runtimeSupported = supportState.isSupported,
            runtimeUnsupportedReason = supportState.reason,
        )

    private fun upsertRuntimeStatus(
        sessionId: String,
        scriptId: Long,
        rawState: String,
        detail: String?
    ) {
        val mappedState =
            when (rawState.trim().lowercase()) {
                "running" -> UserscriptPageRuntimeState.RUNNING
                "success" -> UserscriptPageRuntimeState.SUCCESS
                "error" -> UserscriptPageRuntimeState.ERROR
                else -> UserscriptPageRuntimeState.QUEUED
            }
        val pageState = sessionPageStates.getOrPut(sessionId) { SessionPageState() }
        pageState.scriptStatuses[scriptId] =
            UserscriptPageRuntimeStatus(
                state = mappedState,
                detail = detail
            )
        publishVisibleStatuses()
    }

    private fun publishVisibleStatuses() {
        val visibleState = visibleSessionId?.let(sessionPageStates::get)
        uiStore.updateCurrentPageSnapshot(
            pageUrl = visibleState?.pageUrl,
            statuses = visibleState?.scriptStatuses?.toMap().orEmpty(),
        )
    }

    private fun trustedPageUrl(sessionId: String): String =
        sessionPageStates[sessionId]?.pageUrl.orEmpty().trim()

    private fun dispatchHostEventToGrant(
        sessionId: String,
        requiredGrant: String,
        eventType: String,
        payload: JSONObject,
    ) {
        val binding = sessionBindings[sessionId] ?: return
        binding.isolatedRuntime
            ?.scriptGrants
            ?.filterValues { grants -> requiredGrant in grants }
            ?.keys
            ?.forEach { userscriptId ->
                dispatchHostEvent(
                    sessionId = sessionId,
                    userscriptId = userscriptId,
                    eventType = eventType,
                    payload = payload,
                )
            }
    }

    private fun dispatchHostEvent(
        sessionId: String,
        userscriptId: Long,
        eventType: String,
        payload: JSONObject,
    ) {
        val isolated =
            sessionBindings[sessionId]
                ?.isolatedRuntime
                ?: return
        if (!isolated.scriptGrants.containsKey(userscriptId)) {
            return
        }
        val rawMessage =
            JSONObject()
                .put("type", eventType)
                .put("payload", payload)
                .toString()
        isolated.replyProxies.toList().forEach { replyProxy ->
            mainHandler.post {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    runCatching { replyProxy.postMessage(rawMessage) }
                        .onFailure { error ->
                            AppLogger.w(
                                TAG,
                                "Failed to dispatch userscript host event: ${error.message}",
                            )
                        }
                } else {
                    error("Userscript WebView message feature changed after bridge attachment")
                }
            }
        }
    }

    private fun resolveWebRequestType(request: WebResourceRequest): String {
        if (request.isForMainFrame) {
            return "main_frame"
        }
        val lowerUrl = request.url?.toString().orEmpty().lowercase()
        val accept = request.requestHeaders?.entries?.firstOrNull {
            it.key.equals("Accept", ignoreCase = true)
        }?.value?.lowercase().orEmpty()
        return when {
            accept.contains("text/css") || lowerUrl.endsWith(".css") -> "stylesheet"
            accept.contains("javascript") || lowerUrl.endsWith(".js") -> "script"
            accept.contains("image/") || lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".svg") -> "image"
            accept.contains("font/") || lowerUrl.endsWith(".woff") || lowerUrl.endsWith(".woff2") || lowerUrl.endsWith(".ttf") -> "font"
            accept.contains("video/") || accept.contains("audio/") -> "media"
            accept.contains("application/json") || accept.contains("text/plain") -> "fetch"
            else -> "other"
        }
    }

    private fun postRpcSuccess(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        payload: JSONObject
    ) {
        postBridgeMessage(
            replyProxy,
            JSONObject()
                .put("type", "rpc_response")
                .put("requestId", requestId)
                .put("payload", payload)
        )
    }

    private fun postRpcError(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        error: String
    ) {
        postBridgeMessage(
            replyProxy,
            JSONObject()
                .put("type", "rpc_response")
                .put("requestId", requestId)
                .put("error", error)
        )
    }

    private fun postXhrEvent(
        replyProxy: JavaScriptReplyProxy,
        requestId: String,
        eventType: String,
        terminal: Boolean,
        payload: JSONObject
    ) {
        postBridgeMessage(
            replyProxy,
            JSONObject()
                .put("type", "xhr_event")
                .put("requestId", requestId)
                .put("eventType", eventType)
                .put("terminal", terminal)
                .put("payload", payload)
        )
    }

    private fun postBridgeMessage(
        replyProxy: JavaScriptReplyProxy,
        payload: JSONObject
    ) {
        mainHandler.post {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                runCatching { replyProxy.postMessage(payload.toString()) }
                    .onFailure {
                        AppLogger.w(
                            TAG,
                            "Failed to post userscript bridge message: ${it.message}",
                        )
                    }
            } else {
                error("Userscript WebView message feature changed after bridge attachment")
            }
        }
    }
}
