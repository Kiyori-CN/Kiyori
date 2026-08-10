package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.AppLogger
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private const val WEBVIEW_SUPPORT_TAG = "BrowserSessionTools"
private const val TAB_THUMBNAIL_MIN_REFRESH_MS = 1_000L

internal enum class BrowserSessionBackResult {
    WEB_HISTORY,
    BROWSER_HOME,
    NONE,
}

internal fun StandardBrowserSessionTools.createSessionOnMain(
    appContext: Context,
    sessionId: String,
    sessionName: String?,
    customUserAgent: String?,
    profile: WebSessionProfile,
): BrowserToolSession {
    // Profile binding must precede settings, bridges, userscripts, and navigation. Binding later
    // would let the new WebView touch the default profile before an incognito session is isolated.
    profileManager.requireProfileAvailable(profile)
    val webView = WebView(resolveWebViewContext(appContext))
    try {
        profileManager.bindProfileBeforeConfiguration(webView, profile)
    } catch (error: Exception) {
        webView.destroy()
        throw error
    }
    val session =
        BrowserToolSession(
            id = sessionId,
            webView = webView,
            cookieManager = profileManager.cookieManagerFor(webView, profile),
            sessionName = sessionName,
            profile = profile,
            customUserAgent = customUserAgent
        )
    configureWebView(
        session = session,
        resolvedUserAgent = resolveSessionUserAgent(session, targetUrl = "about:blank"),
    )
    userscriptManager.attachSession(
        sessionId = session.id,
        webView = session.webView,
        cookieScope = session.profile.wireName,
        cookieManager = session.cookieManager,
    )
    return session
}

internal fun StandardBrowserSessionTools.resolveWebViewContext(fallbackContext: Context): Context {
    val currentActivity = ActivityLifecycleManager.getCurrentActivity()
    return if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
        currentActivity
    } else {
        fallbackContext
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun StandardBrowserSessionTools.configureWebView(
    session: BrowserToolSession,
    resolvedUserAgent: WebSessionResolvedUserAgent,
) {
    with(session.webView.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        textZoom = browserSettingsStore.current.webTextZoomPercent
        allowFileAccess = false
        allowContentAccess = false
        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        setGeolocationEnabled(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                safeBrowsingEnabled = true
            } catch (e: Throwable) {
                AppLogger.w(WEBVIEW_SUPPORT_TAG, "Failed to enable safe browsing: ${e.message}")
            }
        }
    }
    applySessionUserAgent(session, resolvedUserAgent)
    configureCookiePolicy(session)

    session.webView.apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = false
        isHapticFeedbackEnabled = false
        contentDescription = context.getString(R.string.web_session_accessibility_web_content)
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.isLongClickable = false
                    view.isHapticFeedbackEnabled = false
                    if (!view.hasFocus()) {
                        view.requestFocus()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        addJavascriptInterface(BrowserWebDownloadBridge(this@configureWebView, session), "OperitWebDownloadBridge")
        addJavascriptInterface(BrowserAsyncBridge(), "OperitAsyncBridge")
        addJavascriptInterface(BrowserTextSelectionBridge(this@configureWebView), "OperitTextSelectionBridge")
        addJavascriptInterface(
            BrowserMediaCandidateBridge(this@configureWebView, session),
            "OperitMediaCandidateBridge",
        )
        addJavascriptInterface(
            BrowserCredentialBridge(this@configureWebView, session),
            BROWSER_CREDENTIAL_BRIDGE_NAME,
        )
        setDownloadListener(createDownloadListener(session))
        setOnLongClickListener { true }
        isLongClickable = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isScreenReaderFocusable = true
        }
    }

    session.webView.webChromeClient =
        object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val message = resultMsg ?: return false
                val transport = message.obj as? WebView.WebViewTransport ?: return false
                val popupSession = runCatching { createPopupSessionOnMain(session) }.getOrNull() ?: return false
                transport.webView = popupSession.webView
                message.sendToTarget()
                refreshSessionUiOnMain(popupSession.id)
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                val popupSession = window?.let(::findSessionByWebView)
                if (popupSession != null) {
                    closeSession(popupSession.id)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                session.pageTitle = title.orEmpty()
                refreshSessionUiOnMain(session.id)
                if (session.profile.shouldPersistBrowserHistory) {
                    ioScope.launch {
                        historyStore.updateTitle(session.currentUrl, session.pageTitle)
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    appendConsoleEntry(
                        session,
                        BrowserConsoleEntry(
                            level = consoleMessage.messageLevel().name.lowercase(Locale.ROOT),
                            message = consoleMessage.message().orEmpty(),
                            sourceId = consoleMessage.sourceId(),
                            lineNumber = consoleMessage.lineNumber()
                        )
                    )
                }
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) {
                    return false
                }

                session.pendingFileChooserCallback?.onReceiveValue(null)
                session.pendingFileChooserCallback = filePathCallback
                session.lastFileChooserRequestAt = System.currentTimeMillis()
                notifySessionStateChanged(session)

                AppLogger.d(
                    WEBVIEW_SUPPORT_TAG,
                    "Captured file chooser request for session=${session.id}, " +
                        "mode=${fileChooserParams?.mode}, multiple=${fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE}"
                )
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) {
                    return
                }
                handleWebPermissionRequest(request)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin.isNullOrBlank() || callback == null) {
                    callback?.invoke(origin.orEmpty(), false, false)
                    return
                }
                handleGeolocationPermissionRequest(origin, callback)
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                session.pendingDialog =
                    PendingDialog(
                        type = "alert",
                        message = message.orEmpty(),
                        url = url,
                        jsResult = result
                    )
                notifySessionStateChanged(session)
                refreshSessionUiOnMain(session.id)
                AppLogger.d(WEBVIEW_SUPPORT_TAG, "web_session js alert pending: ${message.orEmpty()}")
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                session.pendingDialog =
                    PendingDialog(
                        type = "confirm",
                        message = message.orEmpty(),
                        url = url,
                        jsResult = result
                    )
                notifySessionStateChanged(session)
                refreshSessionUiOnMain(session.id)
                AppLogger.d(WEBVIEW_SUPPORT_TAG, "web_session js confirm pending: ${message.orEmpty()}")
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: android.webkit.JsPromptResult?
            ): Boolean {
                session.pendingDialog =
                    PendingDialog(
                        type = "prompt",
                        message = message.orEmpty(),
                        defaultValue = defaultValue,
                        url = url,
                        jsPromptResult = result
                    )
                notifySessionStateChanged(session)
                refreshSessionUiOnMain(session.id)
                AppLogger.d(WEBVIEW_SUPPORT_TAG, "web_session js prompt pending: ${message.orEmpty()}")
                return true
            }
        }

    session.webView.webViewClient =
        object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Redirects do not pass through navigateSessionOnMain; update before their
                // subresources inherit the previous page's site-specific identity.
                applySessionUserAgent(session, resolveSessionUserAgent(session, url))
                session.currentUrl = url
                session.credentialDocumentToken = UUID.randomUUID().toString()
                session.pageLoaded = false
                session.isLoading = true
                session.hasSslError = false
                session.lastSnapshot = null
                // A navigation invalidates both the image and pending visual-state callbacks;
                // otherwise the window grid could display content captured from the previous URL.
                clearSessionThumbnail(session)
                clearEventLogs(session)
                clearMediaCandidates(session)
                session.pendingDialog = null
                notifySessionStateChanged(session)
                userscriptManager.onPageChanged(session.id, url, forceReset = true)
                syncNavigationStateUi(session)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                session.currentUrl = url
                session.lastSnapshot = null
                restoreReturnWithoutReloadOnMain(session)
                notifySessionStateChanged(session)
                userscriptManager.onPageChanged(session.id, url)
                refreshNavigationStateFromWebView(view, session)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                session.currentUrl = url
                restoreReturnWithoutReloadOnMain(session)
                userscriptManager.onPageChanged(session.id, url)
                session.pageTitle = view.title ?: ""
                session.pageLoaded = true
                session.isLoading = false
                completeBrowserHomeNavigationOnMain(view, session, url)
                notifySessionStateChanged(session)
                applyViewportOverride(session)
                applyBrowserDisplaySettingsOnPage(session)
                refreshNavigationStateFromWebView(view, session)
                injectDownloadHelper(view)
                injectTextSelectionHelper(view)
                injectBrowserCredentialSupport(session)
                // This observer only reads video URLs and reports them to the owning WebSession.
                // Calling webpage media controls here would mutate site state during presentation changes.
                injectMediaCandidateObserver(view)
                if (session.profile.shouldPersistBrowserHistory) {
                    ioScope.launch {
                        historyStore.updateTitle(url, session.pageTitle)
                    }
                }
                requestSessionThumbnailOnMain(session, force = true)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                if (scheme == "blob") {
                    injectBlobDownloaderScript(session, uri.toString())
                    return true
                }
                if (scheme == "data") {
                    handleInlineDownload(
                        session = session,
                        base64Data = uri.toString(),
                        fileName = "download_${System.currentTimeMillis()}",
                        mimeType = guessMimeTypeFromDataUrl(uri.toString()),
                        type = "data",
                        sourceUrl = uri.toString()
                    )
                    return true
                }
                if (scheme == "http" || scheme == "https" || scheme == "about") {
                    applySessionUserAgent(
                        session,
                        resolveSessionUserAgent(session, uri.toString()),
                    )
                }
                return handleNavigationOverrideOnMain(request)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                recordNetworkRequest(session, request)
                recordRequestMediaCandidate(session, request)
                val interceptedResponse = userscriptManager.interceptWebRequest(session.id, request)
                if (interceptedResponse != null) {
                    recordInterceptedResponseMediaCandidate(session, request, interceptedResponse)
                    return interceptedResponse
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                session.currentUrl = url
                userscriptManager.syncUrlChange(session.id, url)
                val pageTitle = view.title.orEmpty()
                restoreReturnWithoutReloadOnMain(session)
                notifySessionStateChanged(session)
                refreshNavigationStateFromWebView(view, session)
                if (session.profile.shouldPersistBrowserHistory) {
                    ioScope.launch {
                        historyStore.recordVisit(url, pageTitle, isReload)
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                AppLogger.w(
                    WEBVIEW_SUPPORT_TAG,
                    "web_session SSL error, cancelling load. " +
                        "session=${session.id}, url=${error.url}, primaryError=${error.primaryError}"
                )
                handler.cancel()
                restoreReturnWithoutReloadOnMain(session)
                session.pageLoaded = false
                session.isLoading = false
                session.hasSslError = true
                completeBrowserHomeNavigationOnMain(view, session, error.url)
                notifySessionStateChanged(session)
                updateNavigationState(session)
                refreshSessionUiOnMain(session.id)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    restoreReturnWithoutReloadOnMain(session)
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                AppLogger.e(
                    WEBVIEW_SUPPORT_TAG,
                    "web_session render process gone: session=${session.id}, " +
                        "didCrash=${detail.didCrash()}, priority=${detail.rendererPriorityAtExit()}"
                )
                session.pageLoaded = false
                session.isLoading = false
                restoreReturnWithoutReloadOnMain(session)
                session.lastSnapshot = null
                session.pendingDialog?.jsPromptResult?.cancel()
                session.pendingDialog?.jsResult?.cancel()
                session.pendingDialog = null
                session.pendingFileChooserCallback?.onReceiveValue(null)
                session.pendingFileChooserCallback = null
                notifySessionStateChanged(session)
                closeSession(session.id)
                showToast(
                    if (detail.didCrash()) {
                        context.getString(R.string.web_session_render_process_crashed)
                    } else {
                        context.getString(R.string.web_session_render_process_gone)
                    }
                )
                return true
            }
        }
}

internal fun StandardBrowserSessionTools.ensureBackgroundAnchorOnMain(
    appContext: Context,
): WebSessionBrowserHost {
    val host = ensureBrowserPresentationOnMain(appContext)
    host.ensureBackgroundAnchorCreated()
    refreshSessionUiOnMain()
    return host
}

internal fun StandardBrowserSessionTools.ensureBrowserPresentationOnMain(
    appContext: Context,
): WebSessionBrowserHost {
    browserHost?.let { return it }

    synchronized(StandardBrowserSessionTools.presentationLock) {
        browserHost?.let { return it }

        val host =
            WebSessionBrowserHost(
                appContext = appContext,
                store = historyStore,
                userscriptStore = userscriptManager.uiStore,
                callbacks = createBrowserHostCallbacks(appContext)
            )
        browserHost = host
        return host
    }
}

internal fun StandardBrowserSessionTools.createBrowserHostCallbacks(
    appContext: Context
): WebSessionBrowserHost.Callbacks =
    object : WebSessionBrowserHost.Callbacks {
        override fun onNavigate(url: String) {
            runOnMainSync<Unit> {
                openUrlOnMain(appContext, url)
            }
        }

        override fun onBack() {
            runOnMainSync<Unit> {
                val session = getActiveSessionOnMain() ?: return@runOnMainSync
                navigateSessionBackOnMain(session)
            }
        }

        override fun onForward() {
            runOnMainSync<Unit> {
                val session = getActiveSessionOnMain() ?: return@runOnMainSync
                ensureSessionAttachedOnMain(session.id)
                if (session.webView.canGoForward()) {
                    applyHistoryTargetUserAgent(session, delta = 1)
                    session.webView.goForward()
                }
                refreshNavigationStateAsync(session)
            }
        }

        override fun onRefresh() {
            runOnMainSync<Unit> {
                val session = getActiveSessionOnMain() ?: return@runOnMainSync
                ensureSessionAttachedOnMain(session.id)
                session.pageLoaded = false
                session.isLoading = true
                session.webView.reload()
                refreshNavigationStateAsync(session)
            }
        }

        override fun onSelectTab(sessionId: String) {
            runOnMainSync<Unit> {
                ensureSessionAttachedOnMain(sessionId)
            }
        }

        override fun onCloseTab(sessionId: String) {
            closeSession(sessionId)
        }

        override fun onNewTab(profile: WebSessionProfile) {
            runOnMainSync<Unit> {
                try {
                    createSessionTabOnMain(
                        appContext = appContext,
                        initialUrl = browserSettingsStore.current.homeUrl,
                        profile = profile,
                    )
                } catch (error: IllegalStateException) {
                    if (profile != WebSessionProfile.INCOGNITO) {
                        throw error
                    }
                    AppLogger.e(WEBVIEW_SUPPORT_TAG, "Unable to create browser profile tab", error)
                    showToast(context.getString(R.string.web_session_incognito_reset_failed))
                    refreshSessionUiOnMain()
                }
            }
        }

        override fun onRequestTabThumbnails() {
            runOnMainSync<Unit> {
                requestAllSessionThumbnailsOnMain()
            }
        }

        override fun onOpenAppShellBrowser() {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_KIYORI_BROWSER
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }

        override fun onRestoreAppShellBrowserFromIndicator() {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }

        override fun onExitBrowser() {
            runOnMainSync<Unit> {
                destroyBrowserPresentationOnMain()
            }
        }

        override fun onOpenBrowserSettings() {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_KIYORI_BROWSER_SETTINGS
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }

        override fun onOpenDownloadSettings() {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }

        override fun onCloseCurrentTab() {
            resolvePreferredSessionId()?.let { closeSession(it) }
        }

        override fun onCloseAllTabs(profile: WebSessionProfile) {
            val ids =
                orderedSessionIds().filter { sessionId ->
                    sessionById(sessionId)?.profile == profile
                }
            ids.forEach { closeSession(it) }
        }

        override fun onRemoveBookmark(url: String) {
            ioScope.launch {
                historyStore.removeBookmark(url, secret = false)
            }
        }

        override fun onBookmarkMutation(mutation: WebSessionBookmarkMutation) {
            ioScope.launch {
                historyStore.applyBookmarkMutation(mutation)
            }
        }

        override fun onOpenBookmarkInTab(url: String, active: Boolean) {
            runOnMainSync<Unit> {
                val sourceSession = getActiveSessionOnMain() ?: return@runOnMainSync
                val sourceSessionId = sourceSession.id
                createSessionTabOnMain(
                    appContext = appContext,
                    initialUrl = url,
                    profile = sourceSession.profile,
                )
                if (!active) {
                    activateSessionOnMain(sourceSessionId)
                }
            }
        }

        override fun onOpenUrl(url: String) {
            runOnMainSync<Unit> {
                openUrlOnMain(appContext, url)
            }
        }

        override fun onOpenHistoryEntry(entry: WebSessionHistoryEntry): Boolean =
            when (entry.category) {
                WebSessionHistoryCategory.WEB,
                WebSessionHistoryCategory.NOVEL,
                WebSessionHistoryCategory.OTHER,
                -> {
                    runOnMainSync<Unit> {
                        openUrlOnMain(appContext, entry.url)
                    }
                    true
                }
                WebSessionHistoryCategory.VIDEO,
                WebSessionHistoryCategory.MUSIC,
                -> playHistoryMedia(entry)
            }

        override fun onDeleteHistory(
            category: WebSessionHistoryCategory?,
            cutoffTimeMillis: Long?,
        ) {
            ioScope.launch {
                historyStore.deleteHistory(category, cutoffTimeMillis)
            }
        }

        override fun onClearNetworkLog() {
            val session = getActiveSessionOnMain() ?: return
            // Network-log clearing is session-scoped so another live window and console evidence
            // are not erased by a presentation action in the current drawer.
            clearNetworkRequests(session)
            refreshSessionUiOnMain(session.id)
        }

        override fun onSelectUserAgentMode(mode: WebSessionUserAgentMode) {
            removeActiveSiteUserAgentRule()
            browserSettingsStore.setUserAgentMode(mode)
            applyBrowserUserAgentSettingsOnMain()
        }

        override fun onSaveCustomGlobalUserAgent(userAgent: String) {
            removeActiveSiteUserAgentRule()
            browserSettingsStore.setCustomGlobalUserAgentAndSelect(userAgent)
            applyBrowserUserAgentSettingsOnMain()
        }

        override fun onSaveSiteUserAgentRule(domain: String, userAgent: String) {
            if (userAgent.isBlank()) {
                browserSettingsStore.removeSiteUserAgentRule(domain)
            } else {
                browserSettingsStore.setSiteUserAgentRule(domain, userAgent)
            }
            applyBrowserUserAgentSettingsOnMain()
        }

        override fun onSetSearchEngine(engine: WebSessionSearchEngine) {
            ioScope.launch {
                runCatching {
                    historyStore.setSearchEngine(engine)
                }.onFailure { error ->
                    AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to persist browser search engine", error)
                }
            }
        }

        override fun onSetDefaultSessionProfile(profile: WebSessionProfile): Boolean =
            runOnMainSync {
                if (
                    profile == WebSessionProfile.INCOGNITO &&
                        !profileManager.incognitoAvailability.isAvailable
                ) {
                    false
                } else {
                    defaultSessionProfile = profile
                    refreshSessionUiOnMain()
                    true
                }
            }

        override fun onSubmitSearch(
            query: String,
            engine: WebSessionSearchEngine,
            profile: WebSessionProfile,
        ) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) {
                return
            }
            val targetUrl = BrowserAddressResolver.resolve(normalizedQuery, engine)
            if (targetUrl == engine.buildSearchUrl(normalizedQuery)) {
                ioScope.launch {
                    runCatching {
                        historyStore.setSearchEngine(engine)
                        if (profile.shouldPersistBrowserHistory) {
                            historyStore.addSearchHistory(normalizedQuery, targetUrl)
                        }
                    }.onFailure { error ->
                        AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to persist browser search history", error)
                    }
                }
            } else {
                ioScope.launch {
                    runCatching {
                        historyStore.setSearchEngine(engine)
                    }.onFailure { error ->
                        AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to persist browser search engine", error)
                    }
                }
            }
            openSearchTarget(targetUrl, profile)
        }

        override fun onOpenSearchRecord(
            record: WebSessionSearchRecord,
            profile: WebSessionProfile,
        ) {
            openSearchTarget(record.targetUrl, profile)
        }

        private fun openSearchTarget(
            targetUrl: String,
            profile: WebSessionProfile,
        ) {
            runOnMainSync<Unit> {
                val activeSession = getActiveSessionOnMain()
                if (!shouldCreateSessionForSearch(activeSession?.profile, profile)) {
                    openUrlOnMain(appContext, targetUrl)
                    return@runOnMainSync
                }
                try {
                    createSessionTabOnMain(
                        appContext = appContext,
                        initialUrl = targetUrl,
                        profile = profile,
                    )
                } catch (error: IllegalStateException) {
                    AppLogger.e(
                        WEBVIEW_SUPPORT_TAG,
                        "Unable to open search target in requested browser profile",
                        error,
                    )
                    showToast(context.getString(R.string.web_session_incognito_reset_failed))
                }
            }
        }

        override fun onDeleteSearchHistory(id: Long) {
            ioScope.launch {
                runCatching {
                    historyStore.deleteSearchHistory(id)
                }.onFailure { error ->
                    AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to delete browser search history", error)
                }
            }
        }

        override fun onClearSearchHistory() {
            ioScope.launch {
                runCatching {
                    historyStore.clearSearchHistory()
                }.onFailure { error ->
                    AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to clear browser search history", error)
                }
            }
        }

        override fun onCopyCurrentUrl() {
            browserHost?.copyCurrentUrlToClipboard()
        }

        override fun onPageSourceApplied(sessionId: String) {
            runOnMainSync<Unit> {
                val session = sessionById(sessionId) ?: return@runOnMainSync
                session.currentUrl =
                    session.webView.url
                        ?.takeIf(String::isNotBlank)
                        ?: session.currentUrl
                session.pageTitle = session.webView.title.orEmpty()
                session.pageLoaded = true
                session.isLoading = false
                session.lastSnapshot = null
                clearSessionThumbnail(session)
                userscriptManager.onPageChanged(
                    sessionId = session.id,
                    pageUrl = session.currentUrl,
                    forceReset = true,
                )
                notifySessionStateChanged(session)
                applyViewportOverride(session)
                refreshNavigationStateFromWebView(session.webView, session)
                injectDownloadHelper(session.webView)
                injectTextSelectionHelper(session.webView)
                injectMediaCandidateObserver(session.webView)
                requestSessionThumbnailOnMain(session, force = true)
            }
        }

        override fun onOpenPlugins() {
            runOnMainSync<Unit> {
                openPluginCenterOnMain()
            }
        }

        override fun onImportUserscript() {
            userscriptManager.beginLocalImport()
        }

        override fun onInstallUserscriptFromUrl(url: String) {
            userscriptManager.beginUrlInstall(url)
        }

        override fun onConfirmUserscriptInstall() {
            userscriptManager.confirmPendingInstall()
        }

        override fun onCancelUserscriptInstall() {
            userscriptManager.cancelPendingInstall()
        }

        override fun onSetUserScriptsAllowed(allowed: Boolean) {
            userscriptManager.setUserScriptsAllowed(allowed)
        }

        override fun onSetUserscriptEnabled(scriptId: Long, enabled: Boolean) {
            userscriptManager.setScriptEnabled(scriptId, enabled)
        }

        override fun onDeleteUserscript(scriptId: Long) {
            userscriptManager.deleteScript(scriptId)
        }

        override fun onCheckUserscriptUpdate(scriptId: Long) {
            userscriptManager.checkForUpdate(scriptId)
        }

        override fun onCheckAllUserscriptUpdates() {
            userscriptManager.checkAllUpdates()
        }

        override fun onApplyUserscriptUpdate(scriptId: Long) {
            userscriptManager.applyUpdate(scriptId)
        }

        override fun onApplyAllSafeUserscriptUpdates() {
            userscriptManager.applyAllSafeUpdates()
        }

        override fun onSetUserscriptsEnabled(scriptIds: Set<Long>, enabled: Boolean) {
            userscriptManager.setScriptsEnabled(scriptIds, enabled)
        }

        override fun onDeleteUserscripts(scriptIds: Set<Long>) {
            userscriptManager.deleteScripts(scriptIds)
        }

        override fun onLoadUserscriptDetail(scriptId: Long) {
            userscriptManager.loadScriptDetail(scriptId)
        }

        override fun onOpenNewUserscriptEditor() {
            userscriptManager.openNewEditor()
        }

        override fun onOpenExistingUserscriptEditor(scriptId: Long) {
            userscriptManager.openExistingEditor(scriptId)
        }

        override fun onOpenUserscriptDraftEditor(draftId: String) {
            userscriptManager.openDraftEditor(draftId)
        }

        override fun onUpdateUserscriptEditorBuffer(draftId: String, source: String) {
            userscriptManager.updateEditorBuffer(draftId, source)
        }

        override fun onPersistUserscriptDraft(
            draftId: String,
            onComplete: (() -> Unit)?,
        ) {
            userscriptManager.persistEditorDraft(draftId, onComplete)
        }

        override fun onDiscardUserscriptDraft(
            draftId: String,
            onComplete: (() -> Unit)?,
        ) {
            userscriptManager.discardEditorDraft(draftId, onComplete)
        }

        override fun onValidateUserscriptDraft(draftId: String) {
            userscriptManager.validateEditorDraft(draftId)
        }

        override fun onFormatUserscriptDraft(draftId: String) {
            userscriptManager.formatEditorDraft(draftId)
        }

        override fun onApplyUserscriptDraft(draftId: String) {
            userscriptManager.applyEditorDraft(draftId)
        }

        override fun onInvokeUserscriptMenu(commandId: String) {
            userscriptManager.invokeMenuCommand(resolvePreferredSessionId(), commandId)
        }

        override fun onPlayMediaCandidate(candidateId: String): Boolean =
            runCatching { playMediaCandidate(candidateId) }
                .fold(
                    onSuccess = { accepted -> accepted },
                    onFailure = { error ->
                        AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to play browser media candidate", error)
                        showToast(error.toString())
                        false
                    },
                )

        override fun onPlayMediaCandidateFloating(candidateId: String): Boolean =
            runCatching { playMediaCandidateFloating(candidateId) }
                .fold(
                    onSuccess = { accepted -> accepted },
                    onFailure = { error ->
                        AppLogger.e(
                            WEBVIEW_SUPPORT_TAG,
                            "Failed to open floating browser media candidate",
                            error,
                        )
                        showToast(error.toString())
                        false
                    },
                )

        override fun onDownloadMediaCandidate(
            candidateId: String,
            sourceSessionId: String?,
            destination: BrowserDownloadDestination,
        ): Boolean =
            runCatching {
                downloadMediaCandidate(
                    candidateId = candidateId,
                    sourceSessionId = sourceSessionId,
                    destination = destination,
                )
            }
                .fold(
                    onSuccess = { accepted -> accepted },
                    onFailure = { error ->
                        showBrowserDownloadFailure(
                            logMessage = "Failed to download browser media candidate",
                            userMessage = "无法添加媒体下载任务",
                            error = error,
                        )
                        false
                    },
                )

        override fun onTogglePlayerPause() {
            toggleBrowserPlayerPause()
        }

        override fun onOpenPlayerFullscreen() {
            openBrowserPlayerFullscreen()
        }

        override fun onLaunchPlayerFullscreen() {
            launchBrowserPlayerFullscreenActivity()
        }

        override fun onClosePlayer() {
            closeBrowserPlayer()
        }

        override fun onPauseDownload(taskId: String) {
            performBrowserDownloadAction(taskId, BrowserDownloadAction.PAUSE)
        }

        override fun onResumeDownload(taskId: String) {
            performBrowserDownloadAction(taskId, BrowserDownloadAction.RESUME)
        }

        override fun onCancelDownload(taskId: String) {
            performBrowserDownloadAction(taskId, BrowserDownloadAction.CANCEL)
        }

        override fun onRetryDownload(taskId: String) {
            performBrowserDownloadAction(taskId, BrowserDownloadAction.RETRY)
        }

        override fun onDeleteDownload(taskId: String, deleteFile: Boolean) {
            performBrowserDownloadDelete(taskId, deleteFile)
        }

        override fun onOpenDownloadedFile(taskId: String) {
            if (!openDownloadedFile(taskId)) {
                showToast(appContext.getString(R.string.web_session_download_open_failed))
            }
        }

        override fun onOpenDownloadFileManager() {
            if (!openDownloadLocation()) {
                showToast(appContext.getString(R.string.web_session_download_location_open_failed))
            }
        }

        override fun onStartManualDownload(
            fileName: String,
            url: String,
            suffix: String,
            engine: BrowserDownloadEngine,
        ): Boolean {
            val result = runCatching {
                runOnMainSync {
                    startManualBrowserDownload(
                        url = url,
                        requestedFileName = fileName,
                        requestedSuffix = suffix,
                        engine = engine,
                    )
                }
            }
            return result.fold(
                onSuccess = { accepted -> accepted },
                onFailure = { error ->
                    showBrowserDownloadFailure(
                        logMessage = "Failed to start manual browser download",
                        userMessage = "无法添加下载任务，请检查链接和文件名",
                        error = error,
                    )
                    false
                },
            )
        }

        override fun onRedownload(taskId: String) {
            browserDownloadManager().redownloadCompletedTask(taskId)
                .onSuccess { task ->
                    showToast(context.getString(R.string.download_started, task.fileName))
                }
                .onFailure { error ->
                    showBrowserDownloadFailure(
                        logMessage = "Failed to redownload completed task",
                        userMessage = "无法重新下载该文件",
                        error = error,
                    )
                }
        }

        override fun onRenameDownload(
            taskId: String,
            targetFileName: String,
            mode: BrowserDownloadRenameMode,
        ) {
            ioScope.launch {
                browserDownloadManager().renameDownloadedFile(taskId, targetFileName)
                    .onSuccess {
                        showToast(
                            if (mode == BrowserDownloadRenameMode.SUFFIX) {
                                "后缀修改成功"
                            } else {
                                "重命名成功"
                            },
                        )
                    }
                    .onFailure { error ->
                        showBrowserDownloadFailure(
                            logMessage = "Failed to rename downloaded file",
                            userMessage = "无法修改文件名",
                            error = error,
                        )
                    }
            }
        }

        override fun onMoveDownload(taskId: String, treeUriString: String) {
            ioScope.launch {
                browserDownloadManager().moveDownloadedFileToDirectory(taskId, treeUriString)
                    .onSuccess { showToast("文件移动成功") }
                    .onFailure { error ->
                        showBrowserDownloadFailure(
                            logMessage = "Failed to move downloaded file",
                            userMessage = "无法移动到所选文件夹",
                            error = error,
                        )
                    }
            }
        }

        override fun onCopyDownloadUrl(taskId: String) {
            val url = browserDownloadManager().copyDownloadUrl(taskId)
            if (url == null) {
                showToast("当前下载链接不可用")
                return
            }
            copyBrowserDownloadText(context, "download_url", url)
            showToast("已复制下载链接")
        }

        override fun onShareDownload(taskId: String) {
            if (!browserDownloadManager().shareDownloadedFile(taskId)) {
                showToast("当前文件暂时无法分享")
            }
        }

        override fun onCopyDownloadLocation(taskId: String) {
            val location = browserDownloadManager().copyDownloadLocation(taskId)
            if (location == null) {
                showToast("当前文件路径不可用")
                return
            }
            copyBrowserDownloadText(context, "download_path", location)
            showToast("已复制文件路径")
        }

        override fun onTransferDownload(taskId: String) {
            ioScope.launch {
                browserDownloadManager().transferDownloadedFileToPublicDirectory(taskId)
                    .onSuccess { showToast("已转存到公开目录") }
                    .onFailure { error ->
                        showBrowserDownloadFailure(
                            logMessage = "Failed to transfer downloaded file",
                            userMessage = "无法转存到公开目录",
                            error = error,
                        )
                    }
            }
        }

        override fun onMergeDownloadToMp4(taskId: String) {
            showToast("正在合并为 MP4")
            ioScope.launch {
                browserDownloadManager().mergeM3u8PackageToMp4(taskId)
                    .onSuccess { task -> showToast("已合并为 ${task.fileName}") }
                    .onFailure { error ->
                        showBrowserDownloadFailure(
                            logMessage = "Failed to merge M3U8 package to MP4",
                            userMessage = "无法合并为 MP4",
                            error = error,
                        )
                    }
            }
        }

        override fun onConfirmBrowserDownload(requestId: String) {
            runOnMainSync<Unit> {
                confirmBrowserDownloadRequest(requestId)
            }
        }

        override fun onCancelBrowserDownload(requestId: String) {
            runOnMainSync<Unit> {
                cancelBrowserDownloadRequest(requestId)
            }
        }

        override fun onHandlePendingDialog(accept: Boolean, promptText: String?) {
            runOnMainSync<Unit> {
                val session = getActiveSessionOnMain() ?: return@runOnMainSync
                resolvePendingDialogOnMain(
                    session = session,
                    accept = accept,
                    promptText = promptText
                )
            }
        }
    }

private fun copyBrowserDownloadText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

internal fun StandardBrowserSessionTools.destroyBackgroundPresentationOnMain() {
    browserHost?.destroy()
    browserHost = null
    StandardBrowserSessionTools.activeSessionId = null
}

/**
 * Removes only the browser presentation. Sessions remain available to AI browser tools and can
 * be mounted again by a later Browser Home or background-anchor request.
 */
internal fun StandardBrowserSessionTools.destroyBrowserPresentationOnMain() {
    browserHost?.destroy()
    browserHost = null
}

internal fun StandardBrowserSessionTools.openPluginCenterOnMain() {
    val host = ensureBrowserPresentationOnMain(context.applicationContext)
    if (!host.hasAppPresentation()) {
        host.showPluginRoute(WebSessionBrowserPluginRoute.Overview)
        createBrowserHostCallbacks(context.applicationContext).onOpenAppShellBrowser()
        refreshSessionUiOnMain()
        return
    }
    host.showPluginRoute(WebSessionBrowserPluginRoute.Overview)
    refreshSessionUiOnMain()
}

internal fun StandardBrowserSessionTools.openUserscriptManagerOnMain(
    initialTab: WebSessionUserscriptWorkbenchTab = WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
    initialSearchQuery: String = "",
) {
    val host = ensureBrowserPresentationOnMain(context.applicationContext)
    if (!host.hasAppPresentation()) {
        host.showPluginRoute(
            WebSessionBrowserPluginRoute.Userscripts(
                initialTab = initialTab,
                initialSearchQuery = initialSearchQuery,
            ),
        )
        createBrowserHostCallbacks(context.applicationContext).onOpenAppShellBrowser()
        refreshSessionUiOnMain()
        return
    }
    host.showPluginRoute(
        WebSessionBrowserPluginRoute.Userscripts(
            initialTab = initialTab,
            initialSearchQuery = initialSearchQuery,
        ),
    )
    refreshSessionUiOnMain()
}

internal fun StandardBrowserSessionTools.openUserscriptDetailOnMain(scriptId: Long) {
    val host = ensureBrowserPresentationOnMain(context.applicationContext)
    if (!host.hasAppPresentation()) {
        host.showPluginRoute(
            WebSessionBrowserPluginRoute.UserscriptDetail(
                scriptId = scriptId,
                returnTab = WebSessionUserscriptWorkbenchTab.INSTALLED,
            ),
        )
        createBrowserHostCallbacks(context.applicationContext).onOpenAppShellBrowser()
        refreshSessionUiOnMain()
        return
    }
    host.showPluginRoute(
        WebSessionBrowserPluginRoute.UserscriptDetail(
            scriptId = scriptId,
            returnTab = WebSessionUserscriptWorkbenchTab.INSTALLED,
        ),
    )
    refreshSessionUiOnMain()
}

internal fun StandardBrowserSessionTools.createSessionTabOnMain(
    appContext: Context,
    initialUrl: String,
    sessionName: String? = null,
    customUserAgent: String? = null,
    profile: WebSessionProfile = defaultSessionProfile,
): BrowserToolSession {
    StandardBrowserSessionTools.activeSessionId
        ?.let(::sessionById)
        ?.let { previous -> requestSessionThumbnailOnMain(previous, force = false) }
    val sessionId = UUID.randomUUID().toString()
    val session =
        createSessionOnMain(appContext, sessionId, sessionName, customUserAgent, profile)
    StandardBrowserSessionTools.sessions[sessionId] = session
    addSessionOrder(sessionId)
    StandardBrowserSessionTools.activeSessionId = sessionId
    ensureBrowserPresentationOnMain(appContext)
    navigateSessionOnMain(session, initialUrl)
    ensureSessionAttachedOnMain(sessionId)
    return session
}

internal fun StandardBrowserSessionTools.openUserscriptTabOnMain(
    appContext: Context,
    sourceSessionId: String,
    url: String,
    active: Boolean
): String {
    val sourceSession = sessionById(sourceSessionId) ?: return ""
    val previousActiveId = StandardBrowserSessionTools.activeSessionId
    val newSession =
        createSessionTabOnMain(
            appContext = appContext,
            initialUrl = url,
            profile = sourceSession.profile,
        )
    if (!active && !previousActiveId.isNullOrBlank() && previousActiveId != newSession.id) {
        activateSessionOnMain(previousActiveId)
    }
    return newSession.id
}

internal fun StandardBrowserSessionTools.navigateSessionOnMain(
    session: BrowserToolSession,
    targetUrl: String,
    headers: Map<String, String> = emptyMap()
) {
    val configuredHomeUrl = browserSettingsStore.current.homeUrl
    session.browserHomeNavigationState =
        if (areBrowserHomeUrlsEquivalent(targetUrl, configuredHomeUrl)) {
            // 主页是每个 WebSession 自己的浏览根。先标记 pending，避免旧历史在主页加载期间
            // 暂时重新启用 Back；页面完成后再清掉根之前的历史。
            session.browserHomeNavigationState.begin(configuredHomeUrl)
        } else {
            session.browserHomeNavigationState.cancelPending()
        }
    applySessionUserAgent(session, resolveSessionUserAgent(session, targetUrl))
    session.pageLoaded = false
    session.isLoading = true
    session.currentUrl = targetUrl
    session.hasSslError = false
    session.lastSnapshot = null
    updateNavigationState(session)
    refreshSessionUiOnMain(session.id)
    if (headers.isNotEmpty()) {
        session.webView.loadUrl(targetUrl, headers)
    } else {
        session.webView.loadUrl(targetUrl)
    }
    refreshNavigationStateAsync(session)
}

internal fun StandardBrowserSessionTools.navigateSessionBackOnMain(
    session: BrowserToolSession,
): BrowserSessionBackResult {
    ensureSessionAttachedOnMain(session.id)
    updateNavigationState(session)
    val result =
        when {
            session.canGoBack -> {
                applyHistoryTargetUserAgent(session, delta = -1)
                try {
                    prepareReturnWithoutReloadOnMain(session)
                    session.webView.goBack()
                } catch (error: Exception) {
                    restoreReturnWithoutReloadOnMain(session)
                    throw error
                }
                BrowserSessionBackResult.WEB_HISTORY
            }
            !isAtConfiguredBrowserHome(
                currentUrl = session.currentUrl,
                configuredHomeUrl = browserSettingsStore.current.homeUrl,
                navigationState = session.browserHomeNavigationState,
            ) -> {
                navigateSessionOnMain(
                    session = session,
                    targetUrl = browserSettingsStore.current.homeUrl,
                )
                BrowserSessionBackResult.BROWSER_HOME
            }
            else -> BrowserSessionBackResult.NONE
        }
    refreshNavigationStateAsync(session)
    return result
}

private fun StandardBrowserSessionTools.applyHistoryTargetUserAgent(
    session: BrowserToolSession,
    delta: Int,
) {
    // Applying after goBackOrForward would send the document request with the page we are
    // leaving's site rule, which can trigger the version-redirect loop this setting prevents.
    val history = session.webView.copyBackForwardList()
    val target = history.getItemAtIndex(history.currentIndex + delta) ?: return
    applySessionUserAgent(session, resolveSessionUserAgent(session, target.url))
}

private fun StandardBrowserSessionTools.completeBrowserHomeNavigationOnMain(
    view: WebView,
    session: BrowserToolSession,
    resolvedUrl: String,
) {
    if (session.browserHomeNavigationState.pendingRequestedUrl == null) return
    session.browserHomeNavigationState =
        session.browserHomeNavigationState.complete(resolvedUrl)
    // 直接目标窗口第一次回到主页时，目标页仍可能位于 WebView 根之前。清理只发生在
    // 明确的主页导航完成后，保证下一次 Back 在主页退出，而不会重新穿越到旧目标页。
    runCatching(view::clearHistory).onFailure { error ->
        AppLogger.w(
            WEBVIEW_SUPPORT_TAG,
            "Unable to establish browser home history root for session=${session.id}",
            error,
        )
    }
}

internal fun StandardBrowserSessionTools.openUrlOnMain(appContext: Context, url: String) {
    val existingSession = getActiveSessionOnMain()
    val session = existingSession ?: createSessionTabOnMain(appContext, initialUrl = url)
    if (existingSession != null) {
        navigateSessionOnMain(session, url)
    }
    ensureSessionAttachedOnMain(session.id)
}

internal fun StandardBrowserSessionTools.handleUserscriptDownloadOnMain(
    sessionId: String,
    url: String,
    fileName: String?
) {
    val session = sessionById(sessionId) ?: getActiveSessionOnMain()
    if (session == null) {
        showToast(context.getString(R.string.web_session_userscript_download_failed))
        return
    }
    handleRegularDownload(
        session = session,
        url = url,
        userAgent = session.webView.settings.userAgentString.orEmpty(),
        contentDisposition =
            fileName?.takeIf { it.isNotBlank() }?.let { "attachment; filename=\"$it\"" },
        mimeType = null,
        contentLength = -1L,
    )
}

internal fun StandardBrowserSessionTools.activateSessionOnMain(sessionId: String) {
    val session = sessionById(sessionId) ?: return
    ensureBrowserPresentationOnMain(context.applicationContext)
    StandardBrowserSessionTools.activeSessionId
        ?.takeIf { activeId -> activeId != sessionId }
        ?.let(::sessionById)
        ?.let { previous -> requestSessionThumbnailOnMain(previous, force = false) }
    StandardBrowserSessionTools.activeSessionId = sessionId
    updateNavigationState(session)
    syncProjectedBrowserStateOnMain()
}

internal fun StandardBrowserSessionTools.ensureSessionAttachedOnMain(sessionId: String) {
    val session = sessionById(sessionId) ?: return
    val appContext = context.applicationContext
    val host = ensureBrowserPresentationOnMain(appContext)
    if (!host.hasAppPresentation() && !host.hasBackgroundAnchorPresentation()) {
        check(Settings.canDrawOverlays(appContext)) {
            "Overlay permission is required for browser tools while Browser Home is not mounted."
        }
        ensureBackgroundAnchorOnMain(appContext)
    }
    StandardBrowserSessionTools.activeSessionId
        ?.takeIf { activeId -> activeId != sessionId }
        ?.let(::sessionById)
        ?.let { previous -> requestSessionThumbnailOnMain(previous, force = false) }
    StandardBrowserSessionTools.activeSessionId = sessionId
    runCatching {
        session.webView.onResume()
        session.webView.resumeTimers()
        session.webView.visibility = View.VISIBLE
        session.webView.alpha = 1f
    }
    updateNavigationState(session)
    syncProjectedBrowserStateOnMain()
}

internal fun StandardBrowserSessionTools.refreshSessionUiOnMain(sessionId: String? = null) {
    sessionId?.let { id ->
        sessionById(id)?.let(::updateNavigationState)
    }
    syncProjectedBrowserStateOnMain()
}

internal fun StandardBrowserSessionTools.syncProjectedBrowserStateOnMain() {
    val registry = buildPageRegistry()
    publishBrowserWindowCount(registry.orderedSessionIds.size)
    val resolvedActiveId = registry.activeSessionId
    val activeSession = resolvedActiveId?.let(::sessionById)
    StandardBrowserSessionTools.activeSessionId = resolvedActiveId
    browserHost?.setViewportSize(
        activeSession?.viewportWidthCssPx,
        activeSession?.viewportHeightCssPx,
    )
    browserHost?.attachActiveWebView(activeSession?.webView)
    activeSession?.let(::applyViewportOverride)
    userscriptManager.updateVisibleSession(
        sessionId = resolvedActiveId,
        pageUrl = activeSession?.currentUrl
    )
    browserHost?.updateHostProjection(
        browserState = buildBrowserState(registry, buildBrowserDownloadSummary()),
        downloadUiState = buildBrowserDownloadUiState(),
        downloadPrompt =
            StandardBrowserSessionTools.browserDownloadConfirmationQueue.peek()?.toUiState(),
    )
}

internal fun StandardBrowserSessionTools.buildBrowserState(
    registry: BrowserPageRegistry,
    downloadSummary: BrowserDownloadSummary
): WebSessionBrowserState {
    val activeId = registry.activeSessionId
    val activeSession = activeId?.let(::sessionById)
    val orderedIds = registry.orderedSessionIds
    val activeMediaCandidates = activeSession?.let(::snapshotMediaCandidates).orEmpty()
    val configuredHomeUrl = browserSettingsStore.current.homeUrl
    val activeSessionIsAtHome =
        activeSession?.let { session ->
            isAtConfiguredBrowserHome(
                currentUrl = session.currentUrl,
                configuredHomeUrl = configuredHomeUrl,
                navigationState = session.browserHomeNavigationState,
            )
        } == true

    return WebSessionBrowserState(
        activeSessionId = activeId,
        activeProfile = activeSession?.profile,
        defaultSessionProfile = defaultSessionProfile,
        incognitoAvailability = profileManager.incognitoAvailability,
        pageTitle = activeSession?.pageTitle.orEmpty(),
        currentUrl = activeSession?.currentUrl?.ifBlank { "about:blank" } ?: "about:blank",
        canGoBack = activeSession?.canGoBack == true,
        canReturnToHome = activeSession != null && !activeSessionIsAtHome,
        canGoForward = activeSession?.canGoForward == true,
        isLoading = activeSession?.isLoading == true,
        hasSslError = activeSession?.hasSslError == true,
        userAgentMode = browserSettingsStore.current.userAgentMode,
        customGlobalUserAgent = browserSettingsStore.current.customGlobalUserAgent,
        activeSiteUserAgentRule =
            activeSession?.let { session ->
                resolveWebSessionSiteUserAgentRule(
                    browserSettingsStore.current.siteUserAgentRules,
                    session.currentUrl,
                )
            },
        activeDownloadCount = downloadSummary.activeCount,
        hasFailedDownloads = downloadSummary.failedCount > 0,
        failedDownloadCount = downloadSummary.failedCount,
        latestCompletedDownloadName = downloadSummary.latestCompletedFileName,
        overallDownloadProgress = downloadSummary.overallProgress,
        pendingDialog =
            activeSession?.pendingDialog?.let { dialog ->
                WebSessionPendingDialogState(
                    type = dialog.type,
                    message = dialog.message,
                    defaultValue = dialog.defaultValue,
                    url = dialog.url
                )
            },
        tabs =
            orderedIds.mapNotNull { id ->
                sessionById(id)?.let { session ->
                    WebSessionBrowserTab(
                        sessionId = session.id,
                        title = sessionDisplayTitle(session),
                        url = session.currentUrl.ifBlank { "about:blank" },
                        isActive = session.id == activeId,
                        hasSslError = session.hasSslError,
                        profile = session.profile,
                        thumbnail = session.thumbnail,
                        thumbnailUpdatedAt = session.thumbnailUpdatedAt,
                    )
                }
            },
        userscriptMenuCommands = userscriptManager.getMenuCommands(activeId),
        networkEntries =
            activeSession?.let { session ->
                synchronized(session.networkEntries) {
                    session.networkEntries.map { entry ->
                        WebSessionBrowserNetworkEntry(
                            method = entry.method,
                            url = entry.url,
                            isMainFrame = entry.isMainFrame,
                            isStatic = entry.isStatic,
                            category = entry.category,
                            timestamp = entry.timestamp,
                            mediaCandidateId =
                                findDirectMediaCandidateIdForNetworkEntry(
                                    activeMediaCandidates,
                                    entry.url,
                                ),
                        )
                    }
                }
            } ?: emptyList(),
        mediaCandidates =
            activeMediaCandidates.filter(BrowserMediaCandidate::isActionableVideo).map { candidate ->
                val ranking = rankBrowserMediaCandidate(candidate)
                WebSessionBrowserMediaCandidate(
                    id = candidate.id,
                    url = candidate.url,
                    pageUrl = candidate.pageUrl,
                    mimeType = candidate.displayMimeType,
                    urlEvidence = candidate.urlEvidence,
                    videoFormat = requireNotNull(candidate.videoFormat),
                    discoverySources = candidate.discoverySources,
                    firstDiscoveredAt = candidate.firstDiscoveredAt,
                    lastDiscoveredAt = candidate.lastDiscoveredAt,
                    durationMillis = candidate.durationMillis,
                    isLive = candidate.isLive,
                    qualityHeight = ranking.qualityHeight,
                    qualityLabel = ranking.qualityLabel,
                    rankingScore = ranking.score,
                    rankingSummary = ranking.summary,
                    isRecommended = ranking.isRecommended,
                    automaticFloatingEligible = ranking.automaticFloatingEligible,
                    directPlaybackReady = candidate.directPlaybackReady,
                    downloadReady = candidate.downloadReady,
                    isBlob = candidate.isBlob,
                )
            },
    )
}

internal fun StandardBrowserSessionTools.requestAllSessionThumbnailsOnMain() {
    orderedSessionIds().forEach { sessionId ->
        sessionById(sessionId)?.let { session ->
            requestSessionThumbnailOnMain(session, force = true)
        }
    }
}

internal fun StandardBrowserSessionTools.requestSessionThumbnailOnMain(
    session: BrowserToolSession,
    force: Boolean,
) {
    val webView = session.webView
    val currentUrl = session.currentUrl
    if (
        !session.pageLoaded ||
            session.isLoading ||
            currentUrl.isBlank() ||
            currentUrl == "about:blank" ||
            webView.width <= 0 ||
            webView.height <= 0
    ) {
        return
    }
    val now = System.currentTimeMillis()
    if (!force && now - session.thumbnailUpdatedAt < TAB_THUMBNAIL_MIN_REFRESH_MS) {
        return
    }

    val generation = session.thumbnailRequestGeneration + 1L
    session.thumbnailRequestGeneration = generation
    try {
        webView.postVisualStateCallback(
            generation,
            object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (
                    requestId != session.thumbnailRequestGeneration ||
                        sessionById(session.id) !== session ||
                        session.currentUrl != currentUrl ||
                        !session.pageLoaded ||
                        session.isLoading
                ) {
                    return
                }
                try {
                    val sourceWidth = webView.width
                    val sourceHeight = webView.height
                    if (sourceWidth <= 0 || sourceHeight <= 0) {
                        return
                    }
                    val bitmap =
                        createBitmap(
                            BROWSER_TAB_THUMBNAIL_WIDTH_PX,
                            BROWSER_TAB_THUMBNAIL_HEIGHT_PX,
                            Bitmap.Config.ARGB_8888,
                        )
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val transform =
                        resolveBrowserThumbnailTransform(
                            sourceWidth = sourceWidth,
                            sourceHeight = sourceHeight,
                        ) ?: return
                    canvas.withTranslation(transform.offsetX, transform.offsetY) {
                        scale(transform.scale, transform.scale)
                        webView.draw(this)
                    }
                    session.thumbnail = bitmap
                    session.thumbnailUpdatedAt = System.currentTimeMillis()
                    refreshSessionUiOnMain(session.id)
                } catch (error: Exception) {
                    AppLogger.w(
                        WEBVIEW_SUPPORT_TAG,
                        "Unable to capture WebSession thumbnail: session=${session.id}, error=${error.message}",
                    )
                }
            }
            },
        )
    } catch (error: Exception) {
        AppLogger.w(
            WEBVIEW_SUPPORT_TAG,
            "Unable to request WebSession visual state: session=${session.id}, error=${error.message}",
        )
    }
}

internal fun StandardBrowserSessionTools.clearSessionThumbnail(session: BrowserToolSession) {
    session.thumbnailRequestGeneration += 1L
    session.thumbnail = null
    session.thumbnailUpdatedAt = 0L
}

internal fun StandardBrowserSessionTools.sessionDisplayTitle(
    session: BrowserToolSession
): String {
    val base =
        when {
            session.pageTitle.isNotBlank() -> session.pageTitle
            !session.sessionName.isNullOrBlank() -> session.sessionName
            session.currentUrl.isNotBlank() -> session.currentUrl
            else -> "about:blank"
        }
    val sslBadge = context.getString(R.string.web_ssl_error_badge)
    return if (session.hasSslError) "$sslBadge · $base" else base
}

internal fun StandardBrowserSessionTools.getActiveSessionOnMain(): BrowserToolSession? =
    buildPageRegistry().activeSessionId?.let(::sessionById)

internal fun StandardBrowserSessionTools.updateNavigationState(session: BrowserToolSession) {
    val isAtHome =
        isAtConfiguredBrowserHome(
            currentUrl = session.currentUrl,
            configuredHomeUrl = browserSettingsStore.current.homeUrl,
            navigationState = session.browserHomeNavigationState,
        )
    session.canGoBack =
        !isAtHome &&
            runCatching { session.webView.canGoBack() }.getOrDefault(false)
    session.canGoForward = runCatching { session.webView.canGoForward() }.getOrDefault(false)
    notifySessionStateChanged(session)
}

internal fun StandardBrowserSessionTools.syncNavigationStateUi(session: BrowserToolSession) {
    updateNavigationState(session)
    refreshSessionUiOnMain(session.id)
}

internal fun StandardBrowserSessionTools.refreshNavigationStateFromWebView(
    view: WebView,
    session: BrowserToolSession
) {
    syncNavigationStateUi(session)
    view.post {
        if (session.webView === view) {
            syncNavigationStateUi(session)
        }
    }
}

internal fun StandardBrowserSessionTools.refreshNavigationStateAsync(session: BrowserToolSession) {
    session.webView.post {
        syncNavigationStateUi(session)
    }
}

internal fun StandardBrowserSessionTools.resolveSessionUserAgent(
    session: BrowserToolSession,
    targetUrl: String,
): WebSessionResolvedUserAgent =
    resolveWebSessionUserAgent(
        settings = browserSettingsStore.current,
        targetUrl = targetUrl,
        sessionUserAgent = session.customUserAgent,
    )

internal fun StandardBrowserSessionTools.applySessionUserAgent(
    session: BrowserToolSession,
    resolvedUserAgent: WebSessionResolvedUserAgent,
) {
    session.usesDesktopUserAgentLayout = resolvedUserAgent.usesDesktopLayout
    with(session.webView.settings) {
        userAgentString = resolvedUserAgent.userAgent
        useWideViewPort =
            resolvedUserAgent.usesDesktopLayout && session.viewportWidthCssPx == null
        loadWithOverviewMode =
            resolvedUserAgent.usesDesktopLayout && session.viewportWidthCssPx == null
    }
    session.appliedUserAgent = resolvedUserAgent.userAgent
}

internal fun StandardBrowserSessionTools.applyViewportOverride(session: BrowserToolSession) {
    val requestedWidth = session.viewportWidthCssPx
    val requestedHeight = session.viewportHeightCssPx
    session.webView.settings.useWideViewPort =
        session.usesDesktopUserAgentLayout && requestedWidth == null
    session.webView.settings.loadWithOverviewMode =
        session.usesDesktopUserAgentLayout && requestedWidth == null
    browserHost?.setViewportSize(requestedWidth, requestedHeight)
    session.webView.requestLayout()
}

internal fun StandardBrowserSessionTools.configureCookiePolicy(session: BrowserToolSession) {
    val webView = session.webView
    val cookieManager = profileManager.cookieManagerFor(webView, session.profile)
    cookieManager.setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }
}

internal fun StandardBrowserSessionTools.createPopupSessionOnMain(
    parentSession: BrowserToolSession
): BrowserToolSession {
    val popupSession =
        createSessionOnMain(
            appContext = parentSession.webView.context ?: context.applicationContext,
            sessionId = UUID.randomUUID().toString(),
            sessionName = parentSession.sessionName,
            customUserAgent = parentSession.customUserAgent,
            profile = parentSession.profile,
        )
    StandardBrowserSessionTools.sessions[popupSession.id] = popupSession
    addSessionOrder(popupSession.id)
    StandardBrowserSessionTools.activeSessionId = popupSession.id
    ensureBrowserPresentationOnMain(context.applicationContext)
    syncProjectedBrowserStateOnMain()
    return popupSession
}

internal fun StandardBrowserSessionTools.findSessionByWebView(
    webView: WebView
): BrowserToolSession? = StandardBrowserSessionTools.sessions.values.firstOrNull { it.webView === webView }

internal fun StandardBrowserSessionTools.handleNavigationOverrideOnMain(
    request: WebResourceRequest
): Boolean {
    val uri = request.url
    val rawUrl = uri.toString()
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
    if (
        scheme != "http" &&
            scheme != "https" &&
            scheme != "about" &&
            !browserSettingsStore.current.allowWebPageOpenApp
    ) {
        // Disabling this setting is an explicit deny policy. Consuming the navigation here keeps
        // WebView from attempting an unsupported external scheme or creating an external prompt.
        return true
    }
    if (
        scheme != "http" &&
            scheme != "https" &&
            scheme != "about" &&
            !shouldLaunchBrowserExternalNavigation(
                isMainFrame = request.isForMainFrame,
                hasUserGesture = request.hasGesture(),
            )
    ) {
        // Automatic redirects must not be able to pull the user out of the browser. Consuming the
        // request here also removes the repeated prompt without weakening the saved permission.
        return true
    }
    return when (scheme) {
        "http", "https" -> {
            if (isUserscriptInstallUri(uri)) {
                userscriptManager.beginUrlInstall(rawUrl, UserscriptInstallSourceType.PAGE_LINK)
                openUserscriptManagerOnMain()
                true
            } else {
                false
            }
        }
        "about" -> false
        "intent" -> handleIntentSchemeOnMain(rawUrl)
        else -> {
            val externalIntent =
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            // The persistent browser setting is the only authorization owner. A second one-shot
            // prompt repeatedly interrupts normal browsing and can disagree with that saved choice.
            if (!launchBrowserExternalIntent(externalIntent)) {
                showToast(context.getString(R.string.web_session_external_open_failed, rawUrl))
            }
            true
        }
    }
}

internal fun shouldLaunchBrowserExternalNavigation(
    isMainFrame: Boolean,
    hasUserGesture: Boolean,
): Boolean = isMainFrame && hasUserGesture

internal fun StandardBrowserSessionTools.isUserscriptInstallUri(uri: Uri): Boolean =
    uri.path?.endsWith(".user.js", ignoreCase = true) == true

internal fun StandardBrowserSessionTools.handleIntentSchemeOnMain(
    rawUrl: String
): Boolean {
    val intent =
        runCatching { Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME) }.getOrElse { error ->
            AppLogger.w(WEBVIEW_SUPPORT_TAG, "Failed to parse intent url: ${error.message}")
            showToast(context.getString(R.string.web_session_external_open_failed, rawUrl))
            return true
        }

    val sanitizedIntent =
        intent.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
        }
    val target = sanitizedIntent.`package`?.takeIf { it.isNotBlank() } ?: rawUrl
    if (!launchBrowserExternalIntent(sanitizedIntent)) {
        showToast(context.getString(R.string.web_session_external_open_failed, target))
    }
    return true
}

internal fun StandardBrowserSessionTools.handleWebPermissionRequest(request: PermissionRequest) {
    val requestedResources = request.resources?.distinct().orEmpty()
    if (requestedResources.isEmpty()) {
        request.deny()
        return
    }

    val requiredPermissions =
        requestedResources
            .flatMap(::androidPermissionsForWebResource)
            .toCollection(LinkedHashSet())

    if (requiredPermissions.isEmpty()) {
        request.grant(requestedResources.toTypedArray())
        return
    }

    ioScope.launch {
        val permissionResults = ensureAndroidPermissions(requiredPermissions)
        val grantableResources =
            requestedResources
                .filter { resource ->
                    val required = androidPermissionsForWebResource(resource)
                    required.isEmpty() || required.all { permissionResults[it] == true }
                }
                .toTypedArray()

        StandardBrowserSessionTools.mainHandler.post {
            if (grantableResources.isNotEmpty()) {
                request.grant(grantableResources)
            } else {
                request.deny()
                showToast(context.getString(R.string.web_session_permission_denied))
            }
        }
    }
}

internal fun StandardBrowserSessionTools.handleGeolocationPermissionRequest(
    origin: String,
    callback: GeolocationPermissions.Callback
) {
    if (!browserSettingsStore.current.allowWebPageGeolocation) {
        callback.invoke(origin, false, false)
        return
    }
    ioScope.launch {
        val permissionResults =
            ensureAndroidPermissions(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        val granted =
            permissionResults[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissionResults[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        StandardBrowserSessionTools.mainHandler.post {
            callback.invoke(origin, granted, false)
            if (!granted) {
                showToast(context.getString(R.string.web_session_location_permission_denied))
            }
        }
    }
}

internal suspend fun StandardBrowserSessionTools.ensureAndroidPermissions(
    permissions: Collection<String>
): Map<String, Boolean> {
    val requested =
        permissions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    if (requested.isEmpty()) {
        return emptyMap()
    }

    val currentResults =
        requested.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    val missingPermissions = currentResults.filterValues { granted -> !granted }.keys
    if (missingPermissions.isEmpty()) {
        return currentResults
    }

    val requestedResults =
        WebSessionPermissionRequestCoordinator.requestPermissions(
            context = context.applicationContext,
            permissions = missingPermissions
        )

    return requested.associateWith { permission ->
        currentResults[permission] == true || requestedResults[permission] == true
    }
}

internal fun StandardBrowserSessionTools.androidPermissionsForWebResource(
    resource: String
): List<String> =
    when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(Manifest.permission.RECORD_AUDIO)
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(Manifest.permission.CAMERA)
        else -> emptyList()
    }

internal fun StandardBrowserSessionTools.showToast(message: String) {
    if (message.isBlank()) {
        return
    }
    StandardBrowserSessionTools.mainHandler.post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

internal fun StandardBrowserSessionTools.applyBrowserUserAgentSettingsOnMain() {
    StandardBrowserSessionTools.sessions.values.forEach { session ->
        if (session.customUserAgent == null) {
            applySessionUserAgent(session, resolveSessionUserAgent(session, session.currentUrl))
        }
    }

    val activeSession = getActiveSessionOnMain()
    if (activeSession != null && activeSession.customUserAgent == null) {
        activeSession.pageLoaded = false
        activeSession.isLoading = true
        activeSession.webView.reload()
        refreshNavigationStateAsync(activeSession)
    } else {
        refreshSessionUiOnMain(activeSession?.id)
    }
}

private fun StandardBrowserSessionTools.removeActiveSiteUserAgentRule() {
    val activeUrl = getActiveSessionOnMain()?.currentUrl ?: return
    val activeRule =
        resolveWebSessionSiteUserAgentRule(
            browserSettingsStore.current.siteUserAgentRules,
            activeUrl,
        ) ?: return
    browserSettingsStore.removeSiteUserAgentRule(activeRule.domain)
}

internal fun StandardBrowserSessionTools.resolvePendingDialogOnMain(
    session: BrowserToolSession,
    accept: Boolean,
    promptText: String? = null
): PendingDialog? {
    val pending = session.pendingDialog ?: return null
    if (pending.jsPromptResult != null) {
        if (accept) {
            pending.jsPromptResult.confirm(promptText ?: pending.defaultValue.orEmpty())
        } else {
            pending.jsPromptResult.cancel()
        }
    } else if (pending.jsResult != null) {
        if (accept) {
            pending.jsResult.confirm()
        } else {
            pending.jsResult.cancel()
        }
    }
    session.pendingDialog = null
    notifySessionStateChanged(session)
    refreshSessionUiOnMain(session.id)
    return pending
}

internal fun StandardBrowserSessionTools.closeSession(sessionId: String): Boolean {
    val orderedBeforeClose =
        orderedSessionIds().mapNotNull { id ->
            sessionById(id)?.let { session ->
                BrowserSessionProfileEntry(sessionId = id, profile = session.profile)
            }
        }
    val wasActive = StandardBrowserSessionTools.activeSessionId == sessionId
    val previouslyActiveId = StandardBrowserSessionTools.activeSessionId
    val session = StandardBrowserSessionTools.sessions.remove(sessionId) ?: return false
    removeSessionOrder(sessionId)

    runOnMainSync<Unit> {
        closePlayerOwnedByBrowserSession(sessionId)
        userscriptManager.detachSession(sessionId)
        if (wasActive) {
            StandardBrowserSessionTools.activeSessionId = null
            browserHost?.attachActiveWebView(null)
        }

        val parent = session.webView.parent
        if (parent is ViewGroup) {
            parent.removeView(session.webView)
        }
        session.pendingFileChooserCallback?.onReceiveValue(null)
        session.pendingFileChooserCallback = null
        session.pendingDialog?.jsPromptResult?.cancel()
        session.pendingDialog?.jsResult?.cancel()
        session.pendingDialog = null
        notifySessionStateChanged(session)
        clearSessionThumbnail(session)
        cleanupWebViewOnMain(session.webView)
        if (
            session.profile == WebSessionProfile.INCOGNITO &&
                StandardBrowserSessionTools.sessions.values.none { remaining ->
                    remaining.profile == WebSessionProfile.INCOGNITO
                }
        ) {
            profileManager.retireIncognitoProfileAfterLastWebView()
        }

        val remainingIds = orderedSessionIds().filter { sessionById(it) != null }
        val nextSessionId =
            resolveSessionAfterClose(
                orderedBeforeClose = orderedBeforeClose,
                closedSessionId = sessionId,
                remainingSessionIds = remainingIds.toSet(),
                previouslyActiveSessionId = previouslyActiveId,
                wasActive = wasActive,
            )
        if (nextSessionId != null) {
            if (wasActive) {
                activateSessionOnMain(nextSessionId)
            } else {
                refreshSessionUiOnMain(nextSessionId)
            }
        } else {
            val host = browserHost
            if (host?.hasAppPresentation() == true) {
                host.attachActiveWebView(null)
            } else {
                destroyBackgroundPresentationOnMain()
            }
        }
        refreshSessionUiOnMain()
    }

    return true
}

internal fun StandardBrowserSessionTools.cleanupWebViewOnMain(webView: WebView) {
    // Loading about:blank starts another navigation in the retiring Profile. Clear only the
    // WebView-owned runtime state here; Profile data is isolated and retired separately.
    listOf<Pair<String, () -> Unit>>(
        "stop loading" to { webView.stopLoading() },
        "pause" to { webView.onPause() },
        "clear history" to { webView.clearHistory() },
        "clear SSL preferences" to { webView.clearSslPreferences() },
        "remove child views" to { webView.removeAllViews() },
        "destroy" to { webView.destroy() },
    ).forEach { (operation, action) ->
        runCatching(action).onFailure { error ->
            AppLogger.w(WEBVIEW_SUPPORT_TAG, "Failed to $operation during WebView cleanup", error)
        }
    }
}

private fun StandardBrowserSessionTools.showBrowserDownloadFailure(
    logMessage: String,
    userMessage: String,
    error: Throwable,
) {
    AppLogger.e(WEBVIEW_SUPPORT_TAG, logMessage, error)
    showToast(userMessage)
}
