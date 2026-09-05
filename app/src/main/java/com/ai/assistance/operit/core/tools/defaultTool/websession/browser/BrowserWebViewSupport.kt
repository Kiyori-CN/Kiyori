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
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
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
import androidx.core.net.toUri
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptInstallSourceType
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.RenderProcessSafeWebViewClient
import com.ai.assistance.operit.util.handleWebViewRenderProcessGone
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import com.kiyori.platform.network.KiyoriNetworkProxyLogStore
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val WEBVIEW_SUPPORT_TAG = "BrowserSessionTools"
private const val TAB_THUMBNAIL_MIN_REFRESH_MS = 1_000L
private const val BROWSER_AD_BLOCK_CSS_CHUNK_CHAR_LIMIT = 64 * 1024

private data class BrowserCookieReadRequest(
    val sessionId: String,
    val pageUrl: String,
    val cookieManager: CookieManager,
)

internal enum class BrowserSessionBackResult {
    WEB_HISTORY,
    BROWSER_HOME,
    NATIVE_HOME,
    OPENER_HOME,
    NONE,
}

internal fun StandardBrowserSessionTools.createSessionOnMain(
    appContext: Context,
    sessionId: String,
    sessionName: String?,
    customUserAgent: String?,
    profile: WebSessionProfile,
    createdAt: Long = System.currentTimeMillis(),
    creationReason: BrowserWindowCreationReason,
    openerHomeSessionId: String? = null,
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
            creationReason = creationReason,
            openerHomeSessionId = openerHomeSessionId,
            customUserAgent = customUserAgent,
            createdAt = createdAt,
        )
    configureWebView(
        session = session,
        resolvedUserAgent = resolveSessionUserAgent(session, targetUrl = "about:blank"),
    )
    recordBrowserWebViewRuntimeSnapshot(session)
    userscriptManager.attachSession(
        sessionId = session.id,
        webView = session.webView,
        cookieScope = session.profile.wireName,
        cookieManager = session.cookieManager,
    )
    recordBrowserDiagnostic(
        level = BrowserDiagnosticLevel.INFO,
        category = BrowserDiagnosticCategory.USERSCRIPT,
        event = "USERSCRIPT_SESSION_ATTACHED",
        session = session,
    )
    KiyoriNetworkProxyManager.getInstance(appContext).setBrowserSiteProxyPolicy(
        disabledHostProvider = { host ->
            browserSettingsStore.current.isNetworkProxyDisabledForSite(host)
        },
        disabledDomainsProvider = {
            browserSettingsStore.current.siteSettingsRules
                .filter { rule ->
                    WebSessionSiteFeature.DISABLE_NETWORK_PROXY in rule.disabledFeatures
                }.mapTo(linkedSetOf()) { rule -> rule.domain }
        },
    )
    // The process-wide ProxyController only becomes usable after the real WebView provider and
    // its support-library bridge have both been initialized. Signal readiness after configuration
    // and diagnostics, before the caller can schedule the first remote navigation.
    KiyoriNetworkProxyManager.getInstance(appContext).notifyBrowserWebViewRuntimeReady()
    return session
}

private fun StandardBrowserSessionTools.recordBrowserWebViewRuntimeSnapshot(
    session: BrowserToolSession,
) {
    val provider =
        runCatching { WebViewCompat.getCurrentWebViewPackage(context) }
            .onFailure { error ->
                AppLogger.w(WEBVIEW_SUPPORT_TAG, "Failed to read the active WebView provider", error)
            }
            .getOrNull()
    recordBrowserDiagnostic(
        level = BrowserDiagnosticLevel.INFO,
        category = BrowserDiagnosticCategory.PROVIDER,
        event = "PROVIDER_SNAPSHOT",
        session = session,
        details =
            linkedMapOf<String, String>().apply {
                put("package", provider?.packageName ?: "unavailable")
                put("versionName", provider?.versionName ?: "unavailable")
                put("versionCode", provider?.let(PackageInfoCompat::getLongVersionCode)?.toString() ?: "unavailable")
            },
    )
    val featureSupport =
        linkedMapOf(
            "MULTI_PROFILE" to WebViewFeature.MULTI_PROFILE,
            "DOCUMENT_START_SCRIPT" to WebViewFeature.DOCUMENT_START_SCRIPT,
            "WEB_MESSAGE_LISTENER" to WebViewFeature.WEB_MESSAGE_LISTENER,
            "JS_INJECTION_IN_FRAME_AND_WORLD" to WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD,
            "MUTE_AUDIO" to WebViewFeature.MUTE_AUDIO,
            "PROXY_OVERRIDE" to WebViewFeature.PROXY_OVERRIDE,
        )
    recordBrowserDiagnostic(
        level = BrowserDiagnosticLevel.INFO,
        category = BrowserDiagnosticCategory.CAPABILITY,
        event = "WEBKIT_FEATURES",
        session = session,
        details = featureSupport.mapValues { (_, feature) -> WebViewFeature.isFeatureSupported(feature).toString() },
    )
    recordBrowserDiagnostic(
        level = BrowserDiagnosticLevel.INFO,
        category = BrowserDiagnosticCategory.SESSION,
        event = "SESSION_CREATED",
        session = session,
        message = "Browser WebSession created",
        details = mapOf("creationReason" to session.creationReason.name),
    )
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
        // Popup requests remain observable through onCreateWindow, but automatic scripts do not
        // receive a product window. The temporary target resolver below only promotes a stable
        // HTTP(S) target after WebView reports a real user gesture.
        javaScriptCanOpenWindowsAutomatically = false
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

    val adMarkingViewConfiguration = ViewConfiguration.get(session.webView.context)
    val textSelectionBridge = BrowserTextSelectionBridge(this@configureWebView)
    val adMarkingTouchTracker =
        BrowserAdMarkingTouchTracker(
            touchSlopPx = adMarkingViewConfiguration.scaledTouchSlop.toFloat(),
        )
    var adMarkingVelocityTracker: VelocityTracker? = null
    session.webView.apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        // 编辑控件的长按必须交给 WebView 自己处理，系统才能创建真实的
        // ActionMode、选区手柄和剪切/复制/粘贴动作；普通网页元素仍由下面的
        // OnLongClickListener 消费，继续进入 Kiyori 网页元素操作链。
        isLongClickable = true
        isHapticFeedbackEnabled = false
        contentDescription = context.getString(R.string.web_session_accessibility_web_content)
        setOnTouchListener { view, event ->
            if (session.adMarkingActive) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        if (!view.hasFocus()) {
                            view.requestFocus()
                        }
                        adMarkingTouchTracker.onDown(event.x, event.y)
                        adMarkingVelocityTracker?.recycle()
                        adMarkingVelocityTracker = VelocityTracker.obtain()
                        adMarkingVelocityTracker?.addMovement(event)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        adMarkingVelocityTracker?.addMovement(event)
                        when (val action = adMarkingTouchTracker.onMove(event.x, event.y)) {
                            is BrowserAdMarkingTouchAction.ScrollBy -> {
                                val deltaYPx = action.deltaYPx.roundToInt()
                                if (deltaYPx != 0) {
                                    view.scrollBy(0, deltaYPx)
                                }
                            }
                            BrowserAdMarkingTouchAction.None,
                            BrowserAdMarkingTouchAction.EndScroll,
                            BrowserAdMarkingTouchAction.EndGesture,
                            is BrowserAdMarkingTouchAction.Select,
                            -> Unit
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        adMarkingVelocityTracker?.addMovement(event)
                        when (val action = adMarkingTouchTracker.onUp(event.x, event.y)) {
                            is BrowserAdMarkingTouchAction.Select ->
                                session.webView.evaluateJavascript(
                                    """
                                    (function() {
                                        if (window.__kiyoriElementActions) {
                                            return window.__kiyoriElementActions.selectAtViewPoint(${action.xPx}, ${action.yPx});
                                        }
                                        return false;
                                    })();
                                    """.trimIndent(),
                                    null,
                                )
                            BrowserAdMarkingTouchAction.EndScroll -> {
                                val velocityTracker = adMarkingVelocityTracker
                                if (velocityTracker != null) {
                                    velocityTracker.computeCurrentVelocity(
                                        1_000,
                                        adMarkingViewConfiguration.scaledMaximumFlingVelocity.toFloat(),
                                    )
                                    val pointerId = event.getPointerId(event.actionIndex)
                                    val velocityYPxPerSecond = velocityTracker.getYVelocity(pointerId)
                                    if (
                                        abs(velocityYPxPerSecond) >=
                                            adMarkingViewConfiguration.scaledMinimumFlingVelocity
                                    ) {
                                        session.webView.flingScroll(
                                            0,
                                            (-velocityYPxPerSecond).roundToInt(),
                                        )
                                    }
                                }
                            }
                            BrowserAdMarkingTouchAction.EndGesture,
                            BrowserAdMarkingTouchAction.None,
                            is BrowserAdMarkingTouchAction.ScrollBy,
                            -> Unit
                        }
                        adMarkingVelocityTracker?.recycle()
                        adMarkingVelocityTracker = null
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        adMarkingTouchTracker.cancel()
                        adMarkingVelocityTracker?.recycle()
                        adMarkingVelocityTracker = null
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                // 标记模式由原生层区分轻点和纵向拖动：轻点在抬手时选择，拖动直接滚动
                // 唯一 WebView。整个手势始终不进入 DOM，广告无法在 pointer/touch 阶段跳转。
                true
            } else {
                adMarkingTouchTracker.cancel()
                adMarkingVelocityTracker?.recycle()
                adMarkingVelocityTracker = null
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
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
        }
        addJavascriptInterface(BrowserWebDownloadBridge(this@configureWebView, session), "OperitWebDownloadBridge")
        addJavascriptInterface(BrowserAsyncBridge(), "OperitAsyncBridge")
        addJavascriptInterface(textSelectionBridge, "OperitTextSelectionBridge")
        addJavascriptInterface(
            BrowserWebElementBridge(this@configureWebView, session),
            "OperitWebElementBridge",
        )
        addJavascriptInterface(
            BrowserMediaCandidateBridge(this@configureWebView, session),
            "OperitMediaCandidateBridge",
        )
        addJavascriptInterface(
            BrowserCredentialBridge(this@configureWebView, session),
            BROWSER_CREDENTIAL_BRIDGE_NAME,
        )
        setDownloadListener(createDownloadListener(session))
        setOnLongClickListener {
            !textSelectionBridge.isEditableLongPressTarget() &&
                hitTestResult.type != WebView.HitTestResult.EDIT_TEXT_TYPE
        }
        isLongClickable = true
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
                if (session.adMarkingActive) {
                    // 标记元素期间禁止网页创建第二窗口；否则 _blank、window.open 或广告 SDK
                    // 可以绕过当前 WebView 的主框架导航拦截。
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.WARNING,
                        category = BrowserDiagnosticCategory.POPUP,
                        event = "POPUP_BLOCKED_AD_MARKING",
                        session = session,
                        message = "Popup rejected while ad marking owns the gesture",
                    )
                    return false
                }
                if (!isUserGesture || isDialog) {
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.INFO,
                        category = BrowserDiagnosticCategory.POPUP,
                        event = "POPUP_REJECTED",
                        session = session,
                        message = "Popup requires a user gesture and a non-dialog target",
                        details = mapOf("userGesture" to isUserGesture.toString(), "dialog" to isDialog.toString()),
                    )
                    return false
                }
                val message = resultMsg ?: run {
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.WARNING,
                        category = BrowserDiagnosticCategory.POPUP,
                        event = "POPUP_REJECTED_NO_MESSAGE",
                        session = session,
                    )
                    return false
                }
                val transport = message.obj as? WebView.WebViewTransport ?: run {
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.WARNING,
                        category = BrowserDiagnosticCategory.POPUP,
                        event = "POPUP_REJECTED_INVALID_TRANSPORT",
                        session = session,
                    )
                    return false
                }
                val resolver =
                    runCatching {
                        BrowserPopupTargetResolver(
                            tools = this@configureWebView,
                            parentSession = session,
                        )
                    }.onFailure { error ->
                        AppLogger.w(WEBVIEW_SUPPORT_TAG, "Unable to create popup target resolver", error)
                    }.getOrNull() ?: run {
                        recordBrowserDiagnostic(
                            level = BrowserDiagnosticLevel.WARNING,
                            category = BrowserDiagnosticCategory.POPUP,
                            event = "POPUP_REJECTED_RESOLVER",
                            session = session,
                        )
                        return false
                    }
                transport.webView = resolver.webView
                message.sendToTarget()
                resolver.start()
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.POPUP,
                    event = "POPUP_ACCEPTED",
                    session = session,
                )
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                val popupSession = window?.let(::findSessionByWebView)
                if (popupSession != null) {
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.INFO,
                        category = BrowserDiagnosticCategory.POPUP,
                        event = "POPUP_CLOSED",
                        session = session,
                        details = mapOf("popupSessionId" to popupSession.id),
                    )
                    closeSession(popupSession.id)
                } else {
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.INFO,
                        category = BrowserDiagnosticCategory.POPUP,
                        event = "POPUP_TARGET_CLOSED",
                        session = session,
                    )
                    window?.destroy()
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                session.pageTitle = title.orEmpty()
                refreshSessionUiOnMain(session.id)
                scheduleBrowserRecoverySnapshotWrite()
                if (session.profile.shouldPersistBrowserHistory) {
                    ioScope.launch {
                        historyStore.updateTitle(session.currentUrl, session.pageTitle)
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val level = consoleMessage.messageLevel().name.lowercase(Locale.ROOT)
                    appendConsoleEntry(
                        session,
                        BrowserConsoleEntry(
                            level = level,
                            message = consoleMessage.message().orEmpty(),
                            sourceId = consoleMessage.sourceId(),
                            lineNumber = consoleMessage.lineNumber()
                        )
                    )
                    if (level == "warning" || level == "error") {
                        recordBrowserDiagnostic(
                            level =
                                if (level == "error") {
                                    BrowserDiagnosticLevel.ERROR
                                } else {
                                    BrowserDiagnosticLevel.WARNING
                                },
                            category = BrowserDiagnosticCategory.WEBVIEW,
                            event = "CONSOLE_${level.uppercase(Locale.ROOT)}",
                            session = session,
                            message = "WebView console reported a ${level.lowercase(Locale.ROOT)}",
                            details =
                                mapOf(
                                    "sourceUrl" to consoleMessage.sourceId().orEmpty(),
                                    "lineNumber" to consoleMessage.lineNumber().toString(),
                                ),
                        )
                    }
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.PERMISSION,
                    event = "FILE_CHOOSER_REQUESTED",
                    session = session,
                    details =
                        mapOf(
                            "mode" to (fileChooserParams?.mode?.toString() ?: "unknown"),
                            "multiple" to (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE).toString(),
                        ),
                )

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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.PERMISSION,
                    event = "WEB_PERMISSION_REQUESTED",
                    session = session,
                    message = "Web permission request received",
                    details = mapOf("resourceCount" to (request.resources?.size ?: 0).toString()),
                )
                handleWebPermissionRequest(request, session)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin.isNullOrBlank() || callback == null) {
                    callback?.invoke(origin.orEmpty(), false, false)
                    return
                }
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.PERMISSION,
                    event = "GEOLOCATION_REQUESTED",
                    session = session,
                    details = mapOf("origin" to origin),
                )
                handleGeolocationPermissionRequest(origin, callback, session)
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "JS_ALERT",
                    session = session,
                    details = mapOf("url" to url.orEmpty()),
                )
                AppLogger.d(WEBVIEW_SUPPORT_TAG, "web_session js alert pending: session=${session.id}")
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "JS_CONFIRM",
                    session = session,
                    details = mapOf("url" to url.orEmpty()),
                )
                AppLogger.d(WEBVIEW_SUPPORT_TAG, "web_session js confirm pending: session=${session.id}")
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "JS_PROMPT",
                    session = session,
                    details = mapOf("url" to url.orEmpty()),
                )
                AppLogger.d(WEBVIEW_SUPPORT_TAG, "web_session js prompt pending: session=${session.id}")
                return true
            }
        }

    session.webView.webViewClient =
        object : RenderProcessSafeWebViewClient(WEBVIEW_SUPPORT_TAG) {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                session.finishedBrowserDocumentToken = null
                session.finishedBrowserDocumentUrl = ""
                val pendingDocumentToken = session.pendingBrowserDocumentStartToken
                if (pendingDocumentToken != null) {
                    check(pendingDocumentToken == session.credentialDocumentToken) {
                        "Pending Browser document token does not match the active document"
                    }
                    session.pendingBrowserDocumentStartToken = null
                } else {
                    session.credentialDocumentToken = UUID.randomUUID().toString()
                    session.automaticFloatingConsumedDocumentToken = null
                }
                session.browserDocumentStartedUrl = url
                // Redirects do not pass through navigateSessionOnMain; update before their
                // subresources inherit the previous page's site-specific identity.
                applySessionUserAgent(
                    session,
                    resolveSessionUserAgent(session, url),
                    targetUrl = url,
                )
                if (
                    shouldClearBrowserSearchRecoveryOnNavigation(
                        pageLoaded = session.pageLoaded,
                        searchRecoveryPending = session.searchRecoveryPending,
                        resolvedResultUrl = session.lastSearchRecovery?.resolvedResultUrl,
                        targetUrl = url,
                    )
                ) {
                    session.lastSearchRecovery = null
                    scheduleBrowserRecoverySnapshotWrite()
                }
                session.currentUrl = url
                session.adMarkingActive = false
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.NAVIGATION,
                    event = "NAVIGATION_STARTED",
                    session = session,
                    details = mapOf("url" to url),
                )
                injectBrowserElementInteractionHelper(
                    webView = view,
                    navigationPolicy = session.externalNavigationPolicy,
                    elementActionsEnabled =
                        isBrowserWebElementLongPressMenuEnabledForPage(url),
                )
                notifySessionStateChanged(session)
                userscriptManager.onPageChanged(session.id, url, forceReset = true)
                syncNavigationStateUi(session)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                if (
                    !isCurrentBrowserDocumentCompletion(
                        pendingDocumentStartToken = session.pendingBrowserDocumentStartToken,
                        startedDocumentUrl = session.browserDocumentStartedUrl,
                        callbackUrl = url,
                    )
                ) {
                    return
                }
                session.currentUrl = url
                session.lastSnapshot = null
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.NAVIGATION,
                    event = "NAVIGATION_COMMITTED",
                    session = session,
                    details = mapOf("url" to url),
                )
                restoreReturnWithoutReloadOnMain(session)
                notifySessionStateChanged(session)
                userscriptManager.onPageChanged(session.id, url)
                refreshNavigationStateFromWebView(view, session)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (
                    !isCurrentBrowserDocumentCompletion(
                        pendingDocumentStartToken = session.pendingBrowserDocumentStartToken,
                        startedDocumentUrl = session.browserDocumentStartedUrl,
                        callbackUrl = url,
                    )
                ) {
                    return
                }
                if (
                    isDuplicateBrowserDocumentCompletion(
                        finishedDocumentToken = session.finishedBrowserDocumentToken,
                        finishedDocumentUrl = session.finishedBrowserDocumentUrl,
                        currentDocumentToken = session.credentialDocumentToken,
                        callbackUrl = url,
                    )
                ) {
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.WARNING,
                        category = BrowserDiagnosticCategory.NAVIGATION,
                        event = "NAVIGATION_FINISHED_DUPLICATE",
                        session = session,
                        details = mapOf("url" to url),
                    )
                    return
                }
                session.finishedBrowserDocumentToken = session.credentialDocumentToken
                session.finishedBrowserDocumentUrl = url
                session.currentUrl = url
                if (session.searchRecoveryPending) {
                    session.lastSearchRecovery =
                        session.lastSearchRecovery?.copy(resolvedResultUrl = url)
                    session.searchRecoveryPending = false
                    scheduleBrowserRecoverySnapshotWrite()
                }
                restoreReturnWithoutReloadOnMain(session)
                userscriptManager.onPageChanged(session.id, url)
                session.pageTitle = view.title ?: ""
                session.pageLoaded = true
                session.isLoading = false
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.NAVIGATION,
                    event = "NAVIGATION_FINISHED",
                    session = session,
                    details = mapOf("url" to url),
                )
                completeBrowserHomeNavigationOnMain(view, session, url)
                notifySessionStateChanged(session)
                applyViewportOverride(session)
                applyBrowserDisplaySettingsOnPage(session)
                refreshNavigationStateFromWebView(view, session)
                injectDownloadHelper(view)
                injectBrowserElementInteractionHelper(
                    webView = view,
                    navigationPolicy = session.externalNavigationPolicy,
                    elementActionsEnabled =
                        isBrowserWebElementLongPressMenuEnabledForPage(session.currentUrl),
                )
                injectTextSelectionHelper(view)
                injectBrowserAdBlockElementRules(session)
                injectBrowserCredentialSupport(session)
                // This observer only reads video URLs and reports them to the owning WebSession.
                // Calling webpage media controls here would mutate site state during presentation changes.
                injectMediaCandidateObserver(view, session.credentialDocumentToken)
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
                        targetUrl = uri.toString(),
                    )
                }
                return handleNavigationOverrideOnMain(request, session)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                val documentToken = session.credentialDocumentToken
                val blockDecision =
                    adBlockStore.decide(
                        browserAdBlockRequestContext(
                            pageUrl = session.currentUrl,
                            requestUrl = request.url?.toString().orEmpty(),
                            requestHeaders = request.requestHeaders,
                            isMainFrame = request.isForMainFrame,
                        ),
                    )
                recordNetworkRequest(session, request, blockDecision, documentToken)
                if (blockDecision != null) {
                    adBlockStore.recordBlockedRequest()
                    return browserAdBlockBlockedResponse()
                }
                recordRequestMediaCandidate(session, request, documentToken)
                val interceptedResponse = userscriptManager.interceptWebRequest(session.id, request)
                if (interceptedResponse != null) {
                    recordInterceptedResponseMediaCandidate(
                        session,
                        request,
                        interceptedResponse,
                        documentToken,
                    )
                    return interceptedResponse
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (
                    !isCurrentBrowserHistoryUpdate(
                        pendingDocumentStartToken = session.pendingBrowserDocumentStartToken,
                        callbackUrl = url,
                        webViewUrl = view.url.orEmpty(),
                    )
                ) {
                    return
                }
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.ERROR,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "SSL_ERROR",
                    session = session,
                    message = "SSL error; load cancelled",
                    details =
                        mapOf(
                            "url" to error.url.orEmpty(),
                            "primaryError" to error.primaryError.toString(),
                        ),
                )
                // This callback has no main-frame flag and may run before onPageStarted. Cancel the
                // invalid certificate here; the matching main-frame onReceivedError owns page state.
                handler.cancel()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (
                    request.isForMainFrame &&
                        isCurrentBrowserDocumentCompletion(
                            pendingDocumentStartToken = session.pendingBrowserDocumentStartToken,
                            startedDocumentUrl = session.browserDocumentStartedUrl,
                            callbackUrl = request.url.toString(),
                        )
                ) {
                    val runtimeState =
                        KiyoriNetworkProxyManager.getInstance(context).runtimeState.value
                    KiyoriNetworkProxyLogStore.warning(
                        "WebView 网络",
                        "主文档加载失败 session=${session.id} url=${request.url} " +
                            "errorCode=${error.errorCode} description=${error.description} " +
                            "runtimePhase=${runtimeState.phase} " +
                            "runtimeGeneration=${runtimeState.runtimeGeneration ?: "none"} " +
                            "endpointPort=${runtimeState.mixedPort ?: "none"} " +
                            "controllerHealthy=${runtimeState.controllerHealthy ?: "unknown"} " +
                            "mixedPortListening=${runtimeState.mixedPortListening ?: "unknown"}",
                    )
                    recordBrowserDiagnostic(
                        level = BrowserDiagnosticLevel.ERROR,
                        category = BrowserDiagnosticCategory.WEBVIEW,
                        event = "MAIN_DOCUMENT_ERROR",
                        session = session,
                        message = error.description?.toString().orEmpty(),
                        details = mapOf("url" to request.url.toString(), "errorCode" to error.errorCode.toString()),
                    )
                    restoreReturnWithoutReloadOnMain(session)
                    if (error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE) {
                        session.pageLoaded = false
                        session.isLoading = false
                        session.hasSslError = true
                        completeBrowserHomeNavigationOnMain(view, session, request.url.toString())
                        notifySessionStateChanged(session)
                        updateNavigationState(session)
                        refreshSessionUiOnMain(session.id)
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?,
            ): Boolean {
                val didCrash = detail?.didCrash() == true
                val rendererPriorityAtExit = detail?.rendererPriorityAtExit()
                AppLogger.e(
                    WEBVIEW_SUPPORT_TAG,
                    "web_session render process gone: session=${session.id}, " +
                        "didCrash=$didCrash, priority=$rendererPriorityAtExit"
                )
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.ERROR,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "RENDERER_GONE",
                    session = session,
                    message = "WebView renderer process exited",
                    details =
                        mapOf(
                            "didCrash" to didCrash.toString(),
                            "priority" to rendererPriorityAtExit.toString(),
                        ),
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
                    if (didCrash) {
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
                    navigateSessionHistoryOnMain(session, delta = 1)
                }
                refreshNavigationStateAsync(session)
            }
        }

        override fun onRefresh() {
            runOnMainSync<Unit> {
                val session = getActiveSessionOnMain() ?: return@runOnMainSync
                ensureSessionAttachedOnMain(session.id)
                reloadSessionOnMain(session)
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
                        initialUrl =
                            browserHomeSeedUrl(
                                mode = browserSettingsStore.current.homeMode,
                                customHomeUrl = browserSettingsStore.current.customHomeUrl,
                            ),
                        profile = profile,
                        creationReason = BrowserWindowCreationReason.MANUAL_NEW_WINDOW,
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
                    creationReason = BrowserWindowCreationReason.OPEN_IN_NEW_WINDOW,
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

        override fun onOpenExternalUrl(url: String) {
            runOnMainSync<Unit> {
                val uri =
                    runCatching { url.trim().toUri() }.getOrNull()
                        ?: run {
                            showToast(context.getString(R.string.web_session_external_open_failed, url))
                            return@runOnMainSync
                        }
                val scheme = uri.scheme?.lowercase(Locale.ROOT)
                if (scheme.isNullOrBlank()) {
                    showToast(context.getString(R.string.web_session_external_open_failed, url))
                    return@runOnMainSync
                }
                val intent =
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                if (!launchBrowserExternalIntent(intent)) {
                    showToast(context.getString(R.string.web_session_external_open_failed, url))
                }
            }
        }

        override fun onBuildImageRequestHeaders(
            url: String,
            pageUrl: String,
        ): Map<String, String> {
            val session = getActiveSessionOnMain() ?: return emptyMap()
            val appliedUserAgent =
                session.appliedUserAgent.takeIf(String::isNotBlank)
                    ?: session.webView.settings.userAgentString.orEmpty()
            val cookie = session.cookieManager.getCookie(url)
            return buildBrowserNetworkRequestHeaders(
                observedHeaders = emptyMap(),
                appliedUserAgent = appliedUserAgent,
                cookie = cookie,
                pageUrl = pageUrl,
            )
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

        override fun onDeleteHistoryEntries(entryKeys: Set<WebSessionHistoryEntryKey>) {
            ioScope.launch {
                historyStore.deleteHistoryEntries(entryKeys)
            }
        }

        override fun onClearNetworkLog() {
            val session = getActiveSessionOnMain() ?: return
            // Network-log clearing is session-scoped so another live window and console evidence
            // are not erased by a presentation action in the current drawer.
            clearNetworkRequests(session)
            refreshSessionUiOnMain(session.id)
        }

        override fun onClearDiagnosticLog(scope: BrowserDiagnosticScope) {
            browserDiagnosticLog.clear(scope, StandardBrowserSessionTools.activeSessionId)
            refreshSessionUiOnMain()
        }

        override fun onAddNetworkBlockRule(url: String) {
            try {
                adBlockStore.addOrUpdateNetworkRule(
                    id = null,
                    rule = suggestBrowserAdBlockNetworkRule(url),
                )
                showToast("网址过滤规则已添加")
            } catch (error: Exception) {
                AppLogger.e(
                    WEBVIEW_SUPPORT_TAG,
                    "Failed to add network ad-block rule",
                    error,
                )
                showToast("网址过滤规则添加失败")
            }
        }

        override fun onAddElementBlockRule(domain: String, selector: String) {
            adBlockStore.addOrUpdateElementRule(
                id = null,
                domain = domain,
                selector = selector,
            )
        }

        override fun onSetExternalNavigationPolicy(
            policy: BrowserAdMarkingNavigationPolicy,
        ) {
            getActiveSessionOnMain()?.let { session ->
                session.externalNavigationPolicy = policy
                session.webView.evaluateJavascript(
                    """
                    (function() {
                        if (window.__kiyoriElementActions) {
                            window.__kiyoriElementActions.setNavigationPolicy("${policy.toJavascriptValue()}");
                        }
                    })();
                    """.trimIndent(),
                    null,
                )
            }
        }

        override fun onSetAdMarkingActive(active: Boolean) {
            getActiveSessionOnMain()?.adMarkingActive = active
        }

        override fun onClearAdBlockRulesForDomain(
            domain: String,
        ): BrowserAdBlockDomainClearResult =
            try {
                adBlockStore.clearCustomRulesForDomain(domain)
            } catch (error: Exception) {
                AppLogger.e(
                    WEBVIEW_SUPPORT_TAG,
                    "Failed to clear domain ad-block rules",
                    error,
                )
                throw error
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

        override fun onSetSiteFeatureDisabled(
            domain: String,
            feature: WebSessionSiteFeature,
            disabled: Boolean,
        ) {
            runOnMainSync<Unit> {
                browserSettingsStore.setSiteFeatureDisabled(
                    domain = domain,
                    feature = feature,
                    disabled = disabled,
                )
                when (feature) {
                    WebSessionSiteFeature.USER_SCRIPTS ->
                        userscriptManager.refreshSiteSettings()
                    WebSessionSiteFeature.DISABLE_NETWORK_PROXY ->
                        ioScope.launch {
                            runCatching {
                                KiyoriNetworkProxyManager.getInstance(context.applicationContext)
                                    .refreshBrowserProxyOverride()
                            }.onSuccess {
                                StandardBrowserSessionTools.mainHandler.post { getActiveSessionOnMain()?.webView?.reload() }
                            }.onFailure { error ->
                                AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to refresh browser proxy after site rule change", error)
                            }
                        }
                    WebSessionSiteFeature.FORCE_PAGE_ZOOM ->
                        applyBrowserDisplaySettingsOnMain()
                    WebSessionSiteFeature.WEB_ELEMENT_LONG_PRESS_MENU ->
                        applyBrowserWebElementLongPressMenuSettingOnMain()
                    WebSessionSiteFeature.WEBSITE_PASSWORD_SAVING ->
                        applyWebsitePasswordSavingSettingOnMain()
                    WebSessionSiteFeature.RETURN_WITHOUT_RELOAD,
                    WebSessionSiteFeature.SWIPE_HISTORY_NAVIGATION,
                    WebSessionSiteFeature.WEB_PAGE_OPEN_APP,
                    WebSessionSiteFeature.WEB_PAGE_GEOLOCATION,
                    WebSessionSiteFeature.MEDIA_CANDIDATE_BADGE,
                    WebSessionSiteFeature.AUTOMATIC_FLOATING_PLAYBACK,
                    -> Unit
                }
                refreshSessionUiOnMain()
            }
        }

        override fun onClearSiteSettings(domain: String) {
            runOnMainSync<Unit> {
                browserSettingsStore.clearSiteSettings(domain)
                applyBrowserDisplaySettingsOnMain()
                applyBrowserWebElementLongPressMenuSettingOnMain()
                applyWebsitePasswordSavingSettingOnMain()
                userscriptManager.refreshSiteSettings()
                ioScope.launch {
                    runCatching {
                        KiyoriNetworkProxyManager.getInstance(context.applicationContext)
                            .refreshBrowserProxyOverride()
                    }.onSuccess {
                        StandardBrowserSessionTools.mainHandler.post { getActiveSessionOnMain()?.webView?.reload() }
                    }.onFailure { error ->
                        AppLogger.e(WEBVIEW_SUPPORT_TAG, "Failed to refresh browser proxy after clearing site settings", error)
                    }
                }
                refreshSessionUiOnMain()
            }
        }

        override fun onSetSiteAdBlockingDisabled(
            domain: String,
            disabled: Boolean,
        ) {
            val normalizedDomain =
                requireNotNull(normalizeBrowserAdBlockDomainInput(domain)) {
                    "Invalid browser site ad-block domain: $domain"
                }
            val isExactlyAllowlisted =
                normalizedDomain in adBlockStore.current.allowlistedDomains
            when {
                disabled && !isExactlyAllowlisted ->
                    adBlockStore.addAllowlistedDomain(normalizedDomain)
                !disabled && isExactlyAllowlisted ->
                    adBlockStore.removeAllowlistedDomain(normalizedDomain)
            }
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
                            historyStore.addSearchHistory(
                                query = normalizedQuery,
                                targetUrl = targetUrl,
                                engineId = engine.id,
                                source = KiyoriBrowserSearchSource.BROWSER_HOME,
                            )
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
            openSearchTarget(
                targetUrl = targetUrl,
                profile = profile,
                searchRecovery =
                    if (targetUrl == engine.buildSearchUrl(normalizedQuery)) {
                        BrowserSessionSearchRecovery(
                            query = normalizedQuery,
                            engineId = engine.id,
                            source = KiyoriBrowserSearchSource.BROWSER_HOME,
                            requestedUrl = targetUrl,
                            resolvedResultUrl = targetUrl,
                            submittedAt = System.currentTimeMillis(),
                        )
                    } else {
                        null
                    },
            )
        }

        override fun onOpenSearchRecord(
            record: WebSessionSearchRecord,
            profile: WebSessionProfile,
        ) {
            openSearchTarget(
                targetUrl = record.targetUrl,
                profile = profile,
                searchRecovery =
                    BrowserSessionSearchRecovery(
                        query = record.query,
                        engineId = record.engineId,
                        source = KiyoriBrowserSearchSource.SEARCH_HISTORY,
                        requestedUrl = record.targetUrl,
                        resolvedResultUrl = record.targetUrl,
                        submittedAt = record.createdAt,
                    ),
            )
        }

        private fun openSearchTarget(
            targetUrl: String,
            profile: WebSessionProfile,
            searchRecovery: BrowserSessionSearchRecovery?,
        ) {
            runOnMainSync<Unit> {
                val activeSession = getActiveSessionOnMain()
                if (!shouldCreateSessionForSearch(activeSession?.profile, profile)) {
                    activeSession?.let { session ->
                        session.lastSearchRecovery = searchRecovery
                        session.searchRecoveryPending = searchRecovery != null
                        scheduleBrowserRecoverySnapshotWrite()
                    }
                    openUrlOnMain(appContext, targetUrl)
                    return@runOnMainSync
                }
                try {
                    createSessionTabOnMain(
                        appContext = appContext,
                        initialUrl = targetUrl,
                        profile = profile,
                        creationReason =
                            if (activeSession?.profile == profile) {
                                BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH
                            } else {
                                BrowserWindowCreationReason.PROFILE_BOUNDARY_SEARCH
                            },
                    )
                    sessionById(StandardBrowserSessionTools.activeSessionId.orEmpty())?.let { session ->
                        session.lastSearchRecovery = searchRecovery
                        session.searchRecoveryPending = searchRecovery != null
                        scheduleBrowserRecoverySnapshotWrite()
                    }
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
                injectBrowserElementInteractionHelper(
                    webView = session.webView,
                    navigationPolicy = session.externalNavigationPolicy,
                    elementActionsEnabled =
                        isBrowserWebElementLongPressMenuEnabledForPage(session.currentUrl),
                )
                injectTextSelectionHelper(session.webView)
                injectBrowserAdBlockElementRules(session)
                injectMediaCandidateObserver(session.webView, session.credentialDocumentToken)
                requestSessionThumbnailOnMain(session, force = true)
            }
        }

        override fun onOpenPlugins() {
            runOnMainSync<Unit> {
                openPluginCenterOnMain()
            }
        }

        override fun onRefreshCookies() {
            val request =
                runOnMainSync<BrowserCookieReadRequest?> {
                    val session = getActiveSessionOnMain()
                    val pageUrl = session?.currentUrl?.trim().orEmpty()
                    if (
                        !browserSettingsStore.current.cookieReaderEnabled ||
                            session == null ||
                            !isSupportedBrowserCookieUrl(pageUrl)
                    ) {
                        browserHost?.updateBrowserCookieState(
                            BrowserCookieUiState(targetUrl = pageUrl),
                        )
                        return@runOnMainSync null
                    }
                    browserHost?.updateBrowserCookieState(
                        BrowserCookieUiState(targetUrl = pageUrl),
                    )
                    BrowserCookieReadRequest(
                        sessionId = session.id,
                        pageUrl = pageUrl,
                        cookieManager = session.cookieManager,
                    )
                } ?: return

            ioScope.launch {
                try {
                    val header = request.cookieManager.getCookie(request.pageUrl).orEmpty()
                    StandardBrowserSessionTools.mainHandler.post {
                        val currentSession = getActiveSessionOnMain()
                        if (
                            !browserSettingsStore.current.cookieReaderEnabled ||
                                currentSession == null ||
                                currentSession.id != request.sessionId ||
                                currentSession.currentUrl.trim() != request.pageUrl
                        ) {
                            return@post
                        }
                        browserHost?.updateBrowserCookieState(
                            BrowserCookieUiState(
                                targetUrl = request.pageUrl,
                                header = header,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                } catch (error: Exception) {
                    AppLogger.e(
                        WEBVIEW_SUPPORT_TAG,
                        "Failed to read browser cookies for Cookie Reader",
                        error,
                    )
                    StandardBrowserSessionTools.mainHandler.post {
                        val currentSession = getActiveSessionOnMain()
                        if (
                            !browserSettingsStore.current.cookieReaderEnabled ||
                                currentSession == null ||
                                currentSession.id != request.sessionId ||
                                currentSession.currentUrl.trim() != request.pageUrl
                        ) {
                            return@post
                        }
                        browserHost?.updateBrowserCookieState(
                            BrowserCookieUiState(
                                targetUrl = request.pageUrl,
                                errorMessage = "读取 Cookie 时发生错误",
                            ),
                        )
                    }
                }
            }
        }

        override fun onSetCookieReaderEnabled(enabled: Boolean) {
            runOnMainSync<Unit> {
                browserSettingsStore.setCookieReaderEnabled(enabled)
                if (!enabled) {
                    browserHost?.updateBrowserCookieState(BrowserCookieUiState())
                }
                refreshSessionUiOnMain()
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
    sessionId: String = UUID.randomUUID().toString(),
    sessionName: String? = null,
    customUserAgent: String? = null,
    profile: WebSessionProfile = defaultSessionProfile,
    createdAt: Long = System.currentTimeMillis(),
    creationReason: BrowserWindowCreationReason,
    openerHomeSessionId: String? = null,
): BrowserToolSession {
    StandardBrowserSessionTools.activeSessionId
        ?.let(::sessionById)
        ?.let { previous -> requestSessionThumbnailOnMain(previous, force = false) }
    require(sessionById(sessionId) == null) {
        "Browser session id is already active: $sessionId"
    }
    val session =
        createSessionOnMain(
            appContext = appContext,
            sessionId = sessionId,
            sessionName = sessionName,
            customUserAgent = customUserAgent,
            profile = profile,
            createdAt = createdAt,
            creationReason = creationReason,
            openerHomeSessionId = openerHomeSessionId,
        )
    StandardBrowserSessionTools.sessions[sessionId] = session
    addSessionOrder(sessionId)
    StandardBrowserSessionTools.activeSessionId = sessionId
    ensureBrowserPresentationOnMain(appContext)
    navigateSessionOnMain(session, initialUrl)
    ensureSessionAttachedOnMain(sessionId)
    scheduleBrowserRecoverySnapshotWrite()
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
            creationReason = BrowserWindowCreationReason.OPEN_IN_NEW_WINDOW,
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
    val proxyManager = KiyoriNetworkProxyManager.getInstance(context.applicationContext)
    if (
        shouldAwaitStartupProxyBeforeBrowserNavigation(
            targetUrl = targetUrl,
            readiness = proxyManager.startupProxyReadiness(),
        )
    ) {
        deferNavigationUntilStartupProxyReady(session, targetUrl, headers, proxyManager)
        return
    }
    val homeSettings = browserSettingsStore.current
    val configuredHomeUrl = homeSettings.homeUrl
    session.browserHomeNavigationState =
        if (areBrowserHomeUrlsEquivalent(targetUrl, configuredHomeUrl)) {
            // 主页是每个 WebSession 自己的浏览根。先标记 pending，避免旧历史在主页加载期间
            // 暂时重新启用 Back；页面完成后再清掉根之前的历史。
            session.browserHomeNavigationState.begin(configuredHomeUrl)
        } else {
            session.browserHomeNavigationState.cancelPending()
        }
    applySessionUserAgent(
        session,
        resolveSessionUserAgent(session, targetUrl),
        targetUrl = targetUrl,
    )
    session.currentUrl = targetUrl
    session.hasSslError = false
    session.lastSnapshot = null
    beginBrowserDocumentNavigation(session)
    updateNavigationState(session)
    refreshSessionUiOnMain(session.id)
    if (headers.isNotEmpty()) {
        session.webView.loadUrl(targetUrl, headers)
    } else {
        session.webView.loadUrl(targetUrl)
    }
    refreshNavigationStateAsync(session)
    scheduleBrowserRecoverySnapshotWrite()
}

private fun StandardBrowserSessionTools.deferNavigationUntilStartupProxyReady(
    session: BrowserToolSession,
    targetUrl: String,
    headers: Map<String, String>,
    proxyManager: KiyoriNetworkProxyManager,
    reload: Boolean = false,
) {
    val generation = session.networkReadyNavigationGeneration + 1L
    session.networkReadyNavigationGeneration = generation
    ioScope.launch {
        var failure: Exception? = null
        try {
            proxyManager.awaitStartupReconciliation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = error
        }
        StandardBrowserSessionTools.mainHandler.post {
            if (
                sessionById(session.id) !== session ||
                    session.networkReadyNavigationGeneration != generation
            ) {
                return@post
            }
            if (failure != null) {
                val startupFailure = requireNotNull(failure)
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.ERROR,
                    category = BrowserDiagnosticCategory.NAVIGATION,
                    event = "STARTUP_PROXY_RECONCILIATION_FAILED",
                    session = session,
                    message = startupFailure.message ?: startupFailure.javaClass.simpleName,
                    details =
                        mapOf(
                            "readinessGeneration" to
                                proxyManager.startupProxyReadinessGeneration().toString(),
                        ),
                )
                showToast("网络代理启动失败，网页未开始加载")
                return@post
            }
            if (reload) {
                beginBrowserDocumentNavigation(session)
                session.webView.reload()
            } else {
                navigateSessionOnMain(session, targetUrl, headers)
            }
        }
    }
}

private fun StandardBrowserSessionTools.navigateSessionHistoryOnMain(
    session: BrowserToolSession,
    delta: Int,
) {
    val history = session.webView.copyBackForwardList()
    val target = history.getItemAtIndex(history.currentIndex + delta) ?: return
    if (
        shouldAwaitStartupProxyBeforeBrowserNavigation(
            targetUrl = target.url,
            readiness = KiyoriNetworkProxyManager.getInstance(context.applicationContext).startupProxyReadiness(),
        )
    ) {
        deferHistoryNavigationUntilStartupProxyReady(session, delta)
        return
    }
    applyHistoryTargetUserAgent(session, delta)
    performSessionHistoryNavigationOnMain(session, delta)
}

private fun StandardBrowserSessionTools.reloadSessionOnMain(session: BrowserToolSession) {
    val targetUrl = session.currentUrl.ifBlank { session.webView.url.orEmpty() }
    val proxyManager = KiyoriNetworkProxyManager.getInstance(context.applicationContext)
    if (
        shouldAwaitStartupProxyBeforeBrowserNavigation(
            targetUrl = targetUrl,
            readiness = proxyManager.startupProxyReadiness(),
        )
    ) {
        deferNavigationUntilStartupProxyReady(session, targetUrl, emptyMap(), proxyManager, reload = true)
        return
    }
    beginBrowserDocumentNavigation(session)
    session.webView.reload()
}

private fun StandardBrowserSessionTools.performSessionHistoryNavigationOnMain(
    session: BrowserToolSession,
    delta: Int,
) {
    try {
        if (delta < 0) prepareReturnWithoutReloadOnMain(session)
        beginBrowserDocumentNavigation(session)
        if (delta < 0) session.webView.goBack() else session.webView.goForward()
    } catch (error: Exception) {
        if (delta < 0) restoreReturnWithoutReloadOnMain(session)
        throw error
    }
}

private fun StandardBrowserSessionTools.deferHistoryNavigationUntilStartupProxyReady(
    session: BrowserToolSession,
    delta: Int,
) {
    val history = session.webView.copyBackForwardList()
    history.getItemAtIndex(history.currentIndex + delta) ?: return
    val manager = KiyoriNetworkProxyManager.getInstance(context.applicationContext)
    val generation = session.networkReadyNavigationGeneration + 1L
    session.networkReadyNavigationGeneration = generation
    ioScope.launch {
        var failure: Exception? = null
        try {
            manager.awaitStartupReconciliation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = error
        }
        StandardBrowserSessionTools.mainHandler.post {
            if (sessionById(session.id) !== session || session.networkReadyNavigationGeneration != generation) return@post
            if (failure != null) {
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.ERROR,
                    category = BrowserDiagnosticCategory.NAVIGATION,
                    event = "STARTUP_PROXY_RECONCILIATION_FAILED",
                    session = session,
                    message = requireNotNull(failure).message ?: requireNotNull(failure).javaClass.simpleName,
                    details =
                        mapOf(
                            "readinessGeneration" to
                                manager.startupProxyReadinessGeneration().toString(),
                        ),
                )
                showToast("网络代理启动失败，网页未开始加载")
                return@post
            }
            applyHistoryTargetUserAgent(session, delta)
            performSessionHistoryNavigationOnMain(session, delta)
        }
    }
}

internal fun StandardBrowserSessionTools.navigateSessionBackOnMain(
    session: BrowserToolSession,
): BrowserSessionBackResult {
    ensureSessionAttachedOnMain(session.id)
    updateNavigationState(session)
    val history = session.webView.copyBackForwardList()
    val backTargetUrl =
        history
            .takeIf { it.currentIndex > 0 }
            ?.getItemAtIndex(history.currentIndex - 1)
            ?.url
    val useWebHistory =
        shouldUseBrowserHistoryBack(
            creationReason = session.creationReason,
            canGoBack = session.canGoBack,
            backTargetUrl = backTargetUrl,
            backTargetIsInitialSyntheticEntry = history.currentIndex == 1,
        )
    if (session.lastSearchRecovery != null || session.searchRecoveryPending) {
        // Back is the explicit boundary that ends the search-result chrome. Clear it before
        // WebView callbacks begin; otherwise an intermediate history callback can re-project the
        // old query while the configured home is loading.
        session.lastSearchRecovery = null
        session.searchRecoveryPending = false
        scheduleBrowserRecoverySnapshotWrite()
        refreshSessionUiOnMain(session.id)
    }
    var shouldRefreshNavigation = true
    val result =
        when {
            useWebHistory -> {
                navigateSessionHistoryOnMain(session, delta = -1)
                BrowserSessionBackResult.WEB_HISTORY
            }
            browserSettingsStore.current.homeMode == BrowserHomeMode.NATIVE -> {
                browserHost?.showNativeHome(canReturnToPage = false)
                BrowserSessionBackResult.NATIVE_HOME
            }
            !isAtConfiguredBrowserHome(
                currentUrl = session.currentUrl,
                configuredHomeUrl = browserSettingsStore.current.homeUrl,
                navigationState = session.browserHomeNavigationState,
            ) -> {
                val opener = session.openerHomeSessionId?.let(::sessionById)
                when (
                    resolveBrowserSessionRootBackAction(
                        creationReason = session.creationReason,
                        openerHomeSessionExists = opener != null,
                        openerProfileMatches = opener?.profile == session.profile,
                        openerStillAtConfiguredHome =
                            opener?.let { openerSession ->
                                isAtConfiguredBrowserHome(
                                    currentUrl = openerSession.currentUrl,
                                    configuredHomeUrl = browserSettingsStore.current.homeUrl,
                                    navigationState = openerSession.browserHomeNavigationState,
                                )
                            } == true,
                    )
                ) {
                    BrowserSessionRootBackAction.CLOSE_AND_ACTIVATE_OPENER_HOME -> {
                        closeSession(session.id)
                        checkNotNull(opener).let { openerSession ->
                            activateSessionOnMain(openerSession.id)
                        }
                        shouldRefreshNavigation = false
                        BrowserSessionBackResult.OPENER_HOME
                    }
                    BrowserSessionRootBackAction.NAVIGATE_TO_CONFIGURED_HOME -> {
                        navigateSessionOnMain(
                            session = session,
                            targetUrl = browserSettingsStore.current.homeUrl,
                        )
                        BrowserSessionBackResult.BROWSER_HOME
                    }
                }
            }
            else -> BrowserSessionBackResult.NONE
        }
    if (shouldRefreshNavigation) {
        refreshNavigationStateAsync(session)
    }
    return result
}

private fun StandardBrowserSessionTools.beginBrowserDocumentNavigation(
    session: BrowserToolSession,
) {
    // Any concrete navigation supersedes a remote navigation still waiting for startup proxy
    // readiness. Without this generation change, an older deferred URL could replace the page the
    // user selected more recently as soon as the process-wide override finishes installing.
    session.networkReadyNavigationGeneration += 1L
    // Invalidate the old candidate snapshot before WebView reports onPageStarted. This closes the
    // window where automatic playback could select media from the page being left.
    session.credentialDocumentToken = UUID.randomUUID().toString()
    session.pendingBrowserDocumentStartToken = session.credentialDocumentToken
    session.browserDocumentStartedUrl = ""
    session.automaticFloatingConsumedDocumentToken = null
    session.pageLoaded = false
    session.isLoading = true
    clearMediaCandidates(session)
    refreshSessionUiOnMain(session.id)
}

internal fun isCurrentBrowserDocumentCompletion(
    pendingDocumentStartToken: String?,
    startedDocumentUrl: String,
    callbackUrl: String,
): Boolean =
    pendingDocumentStartToken == null &&
        startedDocumentUrl.isNotBlank() &&
        callbackUrl == startedDocumentUrl

internal fun isCurrentBrowserHistoryUpdate(
    pendingDocumentStartToken: String?,
    callbackUrl: String,
    webViewUrl: String,
): Boolean =
    pendingDocumentStartToken == null &&
        callbackUrl.isNotBlank() &&
        callbackUrl == webViewUrl

private fun StandardBrowserSessionTools.applyHistoryTargetUserAgent(
    session: BrowserToolSession,
    delta: Int,
) {
    // Applying after goBackOrForward would send the document request with the page we are
    // leaving's site rule, which can trigger the version-redirect loop this setting prevents.
    val history = session.webView.copyBackForwardList()
    val target = history.getItemAtIndex(history.currentIndex + delta) ?: return
    applySessionUserAgent(
        session,
        resolveSessionUserAgent(session, target.url),
        targetUrl = target.url,
    )
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
    val session =
        existingSession
            ?: createSessionTabOnMain(
                appContext = appContext,
                initialUrl = url,
                creationReason = BrowserWindowCreationReason.MANUAL_NEW_WINDOW,
            )
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
    session.lastActivatedAt = System.currentTimeMillis()
    updateNavigationState(session)
    syncProjectedBrowserStateOnMain()
    scheduleBrowserRecoverySnapshotWrite()
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
    session.lastActivatedAt = System.currentTimeMillis()
    runCatching {
        session.webView.onResume()
        session.webView.resumeTimers()
        session.webView.visibility = View.VISIBLE
        session.webView.alpha = 1f
    }
    updateNavigationState(session)
    syncProjectedBrowserStateOnMain()
    scheduleBrowserRecoverySnapshotWrite()
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
        searchRecovery = activeSession?.lastSearchRecovery,
    )
}

internal fun StandardBrowserSessionTools.buildBrowserState(
    registry: BrowserPageRegistry,
    downloadSummary: BrowserDownloadSummary
): WebSessionBrowserState {
    val activeId = registry.activeSessionId
    val activeSession = activeId?.let(::sessionById)
    val orderedIds = registry.orderedSessionIds
    val activeMediaCandidates =
        activeSession
            ?.let { session ->
                snapshotMediaCandidates(session).filter { candidate ->
                    candidate.documentToken == session.credentialDocumentToken
                }
            }
            .orEmpty()
    val homeSettings = browserSettingsStore.current
    val configuredHomeUrl = homeSettings.homeUrl
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
        activeDocumentToken = activeSession?.credentialDocumentToken.orEmpty(),
        automaticFloatingConsumedDocumentToken =
            activeSession?.automaticFloatingConsumedDocumentToken,
        activeProfile = activeSession?.profile,
        defaultSessionProfile = defaultSessionProfile,
        incognitoAvailability = profileManager.incognitoAvailability,
        pageTitle = activeSession?.pageTitle.orEmpty(),
        currentUrl = activeSession?.currentUrl?.ifBlank { "about:blank" } ?: "about:blank",
        homeMode = homeSettings.homeMode,
        externalNavigationPolicy =
            activeSession?.externalNavigationPolicy
                ?: BrowserAdMarkingNavigationPolicy.DEFAULT,
        canGoBack = activeSession?.canGoBack == true,
        canShowNativeHome =
            homeSettings.homeMode == BrowserHomeMode.NATIVE &&
                activeSession != null &&
                !areBrowserHomeUrlsEquivalent(
                    activeSession.currentUrl,
                    DEFAULT_BROWSER_HOME_URL,
                ),
        canReturnToHome =
            homeSettings.homeMode != BrowserHomeMode.NATIVE &&
                activeSession != null &&
                !activeSessionIsAtHome,
        canGoForward = activeSession?.canGoForward == true,
        pageLoaded = activeSession?.pageLoaded == true,
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
                    session.networkEntries
                        .filter { entry ->
                            entry.documentToken == null ||
                                entry.documentToken == session.credentialDocumentToken
                        }
                        .map { entry ->
                        val mediaCandidate =
                            activeMediaCandidates.singleOrNull { candidate ->
                                normalizeBrowserResourceIdentityUrl(candidate.url) ==
                                    entry.resourceIdentity &&
                                    candidate.isActionableMedia
                            }
                        val resolvedCategory =
                            when (mediaCandidate?.mediaKind) {
                                BrowserMediaKind.VIDEO -> BrowserNetworkRequestCategory.VIDEO
                                BrowserMediaKind.AUDIO -> BrowserNetworkRequestCategory.AUDIO
                                BrowserMediaKind.UNKNOWN_MEDIA,
                                null -> entry.category
                            }
                        WebSessionBrowserNetworkEntry(
                            method = entry.method,
                            url = entry.url,
                            isMainFrame = entry.isMainFrame,
                            isStatic = entry.isStatic,
                            category = resolvedCategory,
                            timestamp = entry.timestamp,
                            kind = entry.kind,
                            mediaCandidateId = mediaCandidate?.id,
                            blocked = entry.blocked,
                            blockingRule = entry.blockingRule,
                            blockingSourceName = entry.blockingSourceName,
                            elementSelector = entry.elementSelector,
                            documentToken = entry.documentToken,
                            resourceIdentity = entry.resourceIdentity,
                            requestCount = entry.requestCount,
                            firstSeenAt = entry.firstSeenAt,
                            lastSeenAt = entry.lastSeenAt,
                            requestHeaders = entry.headers,
                        )
                    }
                }
            } ?: emptyList(),
        diagnosticEntries = browserDiagnosticLog.snapshot(),
        mediaCandidates =
            activeMediaCandidates.filter(BrowserMediaCandidate::isActionableMedia).map { candidate ->
                val ranking = rankBrowserMediaCandidate(candidate)
                WebSessionBrowserMediaCandidate(
                    id = candidate.id,
                    url = candidate.url,
                    pageUrl = candidate.pageUrl,
                    documentToken = candidate.documentToken,
                    mimeType = candidate.displayMimeType,
                    urlEvidence = candidate.urlEvidence,
                    videoFormat =
                        candidate.videoFormat ?: BrowserMediaCandidateVideoFormat.OTHER_VIDEO,
                    mediaKind = candidate.mediaKind,
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
                    directPlaybackReady = candidate.isActionableVideo,
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
    targetUrl: String = session.currentUrl,
) {
    val currentUserAgent = session.webView.settings.userAgentString.orEmpty()
    val userAgentChanged = currentUserAgent != resolvedUserAgent.userAgent
    val layoutChanged = session.usesDesktopUserAgentLayout != resolvedUserAgent.usesDesktopLayout
    session.usesDesktopUserAgentLayout = resolvedUserAgent.usesDesktopLayout
    if (userAgentChanged) {
        session.webView.settings.userAgentString = resolvedUserAgent.userAgent
    }
    session.appliedUserAgent = resolvedUserAgent.userAgent
    applyBrowserViewportSettings(session, domainOrUrl = targetUrl)
    if (userAgentChanged || layoutChanged) {
        recordBrowserDiagnostic(
            level = BrowserDiagnosticLevel.INFO,
            category = BrowserDiagnosticCategory.NAVIGATION,
            event = "USER_AGENT_APPLIED",
            session = session,
            details =
                mapOf(
                    "changed" to userAgentChanged.toString(),
                    "desktopLayout" to resolvedUserAgent.usesDesktopLayout.toString(),
                    "length" to resolvedUserAgent.userAgent.length.toString(),
                    "fingerprint" to
                        MessageDigest.getInstance("SHA-256")
                            .digest(resolvedUserAgent.userAgent.toByteArray())
                            .joinToString("") { byte -> "%02x".format(byte) }
                            .take(16),
                    "target" to browserNetworkHost(targetUrl),
                ),
        )
    }
}

internal fun StandardBrowserSessionTools.applyViewportOverride(session: BrowserToolSession) {
    val requestedWidth = session.viewportWidthCssPx
    val requestedHeight = session.viewportHeightCssPx
    applyBrowserViewportSettings(session)
    browserHost?.setViewportSize(requestedWidth, requestedHeight)
    session.webView.requestLayout()
}

internal fun StandardBrowserSessionTools.injectBrowserAdBlockElementRules(
    session: BrowserToolSession,
) {
    val pageUrl = session.currentUrl
    val documentToken = session.credentialDocumentToken
    val ruleRevision = adBlockStore.current.ruleRevision
    if (
        (
            session.appliedAdBlockDocumentToken == documentToken &&
                session.appliedAdBlockRuleRevision == ruleRevision
            ) ||
            (
                session.pendingAdBlockDocumentToken == documentToken &&
                    session.pendingAdBlockRuleRevision == ruleRevision
                )
    ) {
        return
    }
    session.pendingAdBlockDocumentToken = documentToken
    session.pendingAdBlockRuleRevision = ruleRevision
    // 元素规则决策、去重和大型 CSS 文本组装不能占用 WebView 主线程；Hiker 同样把
    // 当前页面的 element-hiding stylesheet 放在独立线程生成。
    ioScope.launch {
        try {
            val payload =
                buildBrowserAdBlockElementInjectionPayload(
                    adBlockStore.elementDecisionsForPage(pageUrl),
                )
            StandardBrowserSessionTools.mainHandler.post {
                if (
                    session.pendingAdBlockDocumentToken != documentToken ||
                        session.pendingAdBlockRuleRevision != ruleRevision ||
                        session.credentialDocumentToken != documentToken ||
                        session.currentUrl != pageUrl ||
                        adBlockStore.current.ruleRevision != ruleRevision
                ) {
                    return@post
                }
                applyBrowserAdBlockElementRulesOnMain(
                    session = session,
                    pageUrl = pageUrl,
                    documentToken = documentToken,
                    ruleRevision = ruleRevision,
                    payload = payload,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e(
                WEBVIEW_SUPPORT_TAG,
                "Failed to prepare browser ad-block element rules",
                error,
            )
            StandardBrowserSessionTools.mainHandler.post {
                if (
                    session.pendingAdBlockDocumentToken == documentToken &&
                        session.pendingAdBlockRuleRevision == ruleRevision
                ) {
                    session.pendingAdBlockDocumentToken = null
                    session.pendingAdBlockRuleRevision = -1L
                }
            }
        }
    }
}

private data class BrowserAdBlockElementInjectionPayload(
    val decisions: List<BrowserAdBlockElementDecision>,
    val encodedCssChunks: String,
    val encodedHikerSelectors: String,
)

private fun buildBrowserAdBlockElementInjectionPayload(
    decisions: List<BrowserAdBlockElementDecision>,
): BrowserAdBlockElementInjectionPayload {
    val cssChunks = mutableListOf<String>()
    val hikerSelectors = mutableListOf<String>()
    var css = StringBuilder()
    decisions.forEach { decision ->
        val selector = decision.selector
        if (selector.contains("&&")) {
            hikerSelectors += selector
        } else {
            val rule = "$selector { display: none !important; }\n"
            if (
                css.isNotEmpty() &&
                    css.length + rule.length > BROWSER_AD_BLOCK_CSS_CHUNK_CHAR_LIMIT
            ) {
                cssChunks += css.toString()
                css = StringBuilder()
            }
            css.append(rule)
        }
    }
    if (css.isNotEmpty()) {
        cssChunks += css.toString()
    }
    return BrowserAdBlockElementInjectionPayload(
        decisions = decisions,
        encodedCssChunks = JSONArray(cssChunks).toString(),
        encodedHikerSelectors = JSONArray(hikerSelectors).toString(),
    )
}

private fun StandardBrowserSessionTools.applyBrowserAdBlockElementRulesOnMain(
    session: BrowserToolSession,
    pageUrl: String,
    documentToken: String,
    ruleRevision: Long,
    payload: BrowserAdBlockElementInjectionPayload,
) {
    replaceElementBlockLogEntries(
        session = session,
        pageUrl = pageUrl,
        decisions = payload.decisions,
    )
    session.webView.evaluateJavascript(
        """
        (function(cssChunks, hikerSelectors) {
            const styleSelector = "style[data-kiyori-adblock-style]";
            if (
                window.__kiyoriAdBlockRuntime &&
                typeof window.__kiyoriAdBlockRuntime.destroy === "function"
            ) {
                window.__kiyoriAdBlockRuntime.destroy();
            }
            document.querySelectorAll(styleSelector).forEach(function(style) {
                if (style.parentNode) {
                    style.parentNode.removeChild(style);
                }
            });
            if (!Array.isArray(cssChunks)) {
                cssChunks = [];
            }
            if (!Array.isArray(hikerSelectors)) {
                hikerSelectors = [];
            }
            cssChunks.forEach(function(cssText) {
                const style = document.createElement("style");
                style.setAttribute("data-kiyori-runtime-ui", "adblock");
                style.setAttribute("data-kiyori-adblock-style", "true");
                style.textContent = String(cssText || "");
                (document.head || document.documentElement).appendChild(style);
            });

            const changedElements = new Map();

            function parseIndexedPart(part) {
                const separator = part.lastIndexOf(",");
                if (separator <= 0) {
                    return null;
                }
                const index = Number(part.slice(separator + 1));
                if (!Number.isInteger(index) || index < 0) {
                    return null;
                }
                return {
                    token: part.slice(0, separator),
                    index: index
                };
            }

            function matchingChildren(parent, token) {
                const children = Array.from(parent.children || []).filter(function(child) {
                    const tag = String(child.localName || "").toLowerCase();
                    return tag !== "script" && tag !== "style";
                });
                if (token.startsWith(".")) {
                    const className = token.slice(1);
                    return children.filter(function(child) {
                        return child.classList && child.classList.contains(className);
                    });
                }
                return children.filter(function(child) {
                    return String(child.localName || "").toLowerCase() === token.toLowerCase();
                });
            }

            function resolveHikerSelector(selector) {
                const parts = String(selector || "")
                    .split("&&")
                    .map(function(part) {
                        return part.trim();
                    })
                    .filter(Boolean);
                if (parts.length === 0) {
                    return null;
                }
                let current = null;
                for (let index = 0; index < parts.length; index += 1) {
                    const part = parts[index];
                    if (part.startsWith("#")) {
                        const identified = document.getElementById(part.slice(1));
                        if (!identified) {
                            return null;
                        }
                        if (current && identified.parentElement !== current) {
                            return null;
                        }
                        current = identified;
                        continue;
                    }
                    if (part === "body") {
                        if (index !== 0 || !document.body) {
                            return null;
                        }
                        current = document.body;
                        continue;
                    }
                    const parsed = parseIndexedPart(part);
                    if (!parsed) {
                        return null;
                    }
                    if (!current) {
                        const rootMatches =
                            parsed.token.startsWith(".")
                                ? Array.from(document.getElementsByClassName(parsed.token.slice(1)))
                                : Array.from(document.getElementsByTagName(parsed.token));
                        current = rootMatches[parsed.index] || null;
                    } else {
                        current = matchingChildren(current, parsed.token)[parsed.index] || null;
                    }
                    if (!current) {
                        return null;
                    }
                }
                return current;
            }

            function hideElement(element) {
                if (!element || changedElements.has(element)) {
                    return;
                }
                changedElements.set(element, {
                    value: element.style.getPropertyValue("display"),
                    priority: element.style.getPropertyPriority("display")
                });
                element.style.setProperty("display", "none", "important");
            }

            function applyHikerRules() {
                hikerSelectors.forEach(function(selector) {
                    hideElement(resolveHikerSelector(selector));
                });
            }

            const observer =
                hikerSelectors.length > 0
                    ? new MutationObserver(applyHikerRules)
                    : null;
            if (observer && document.documentElement) {
                observer.observe(document.documentElement, {
                    childList: true,
                    subtree: true
                });
            }
            applyHikerRules();

            window.__kiyoriAdBlockRuntime = {
                destroy: function() {
                    if (observer) {
                        observer.disconnect();
                    }
                    changedElements.forEach(function(original, element) {
                        if (!element || !element.style) {
                            return;
                        }
                        if (original.value) {
                            element.style.setProperty(
                                "display",
                                original.value,
                                original.priority
                            );
                        } else {
                            element.style.removeProperty("display");
                        }
                    });
                    changedElements.clear();
                    document.querySelectorAll(styleSelector).forEach(function(style) {
                        if (style.parentNode) {
                            style.parentNode.removeChild(style);
                        }
                    });
                }
            };
        })(${payload.encodedCssChunks}, ${payload.encodedHikerSelectors});
        """.trimIndent(),
        null,
    )
    session.pendingAdBlockDocumentToken = null
    session.pendingAdBlockRuleRevision = -1L
    session.appliedAdBlockDocumentToken = documentToken
    session.appliedAdBlockRuleRevision = ruleRevision
}

internal fun StandardBrowserSessionTools.applyBrowserAdBlockRulesToAllSessionsOnMain() {
    runOnMainSync<Unit> {
        StandardBrowserSessionTools.sessions.values.forEach { session ->
            injectBrowserAdBlockElementRules(session)
        }
    }
}

private fun browserAdBlockBlockedResponse(): android.webkit.WebResourceResponse =
    android.webkit.WebResourceResponse(
        "text/plain",
        "UTF-8",
        204,
        "No Content",
        mapOf(
            "Cache-Control" to "no-store",
            "X-Kiyori-AdBlock" to "blocked",
        ),
        ByteArrayInputStream(ByteArray(0)),
    )

internal fun StandardBrowserSessionTools.configureCookiePolicy(session: BrowserToolSession) {
    val webView = session.webView
    val cookieManager = profileManager.cookieManagerFor(webView, session.profile)
    cookieManager.setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }
}

private class BrowserPopupTargetResolver(
    private val tools: StandardBrowserSessionTools,
    private val parentSession: BrowserToolSession,
) {
    private var settled = false
    private val timeoutRunnable = Runnable { finish() }
    val webView: WebView =
        WebView(tools.resolveWebViewContext(parentSession.webView.context)).also { target ->
            tools.profileManager.requireProfileAvailable(parentSession.profile)
            tools.profileManager.bindProfileBeforeConfiguration(target, parentSession.profile)
            // 解析器只接收 Chromium 提交的主框架目标。执行弹窗页面脚本会在站点策略完成
            // 判定前扩大不受控代码执行面，因此保持 WebView 默认禁用 JavaScript。
            target.settings.domStorageEnabled = false
            target.settings.setSupportMultipleWindows(false)
            target.settings.javaScriptCanOpenWindowsAutomatically = false
            target.settings.allowFileAccess = false
            target.settings.allowContentAccess = false
            target.webViewClient =
                object : RenderProcessSafeWebViewClient(WEBVIEW_SUPPORT_TAG) {
                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?,
                    ): Boolean {
                        val handled = handleWebViewRenderProcessGone(view, detail, WEBVIEW_SUPPORT_TAG)
                        finish()
                        return handled
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (request?.isForMainFrame == true) {
                            resolveTarget(url)
                        }
                        return false
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        resolveTarget(url.orEmpty())
                    }
                }
        }

    fun start() {
        StandardBrowserSessionTools.mainHandler.postDelayed(
            timeoutRunnable,
            POPUP_TARGET_RESOLUTION_TIMEOUT_MILLIS,
        )
    }

    private fun resolveTarget(rawUrl: String) {
        if (settled) {
            return
        }
        val targetUrl = rawUrl.trim()
        if (browserSiteIdentity(targetUrl) == null) {
            return
        }
        if (tools.isUserscriptInstallUri(targetUrl.toUri())) {
            tools.userscriptManager.beginUrlInstall(
                targetUrl,
                UserscriptInstallSourceType.PAGE_LINK,
            )
            tools.openUserscriptManagerOnMain()
            finish()
            return
        }
        if (
            parentSession.externalNavigationPolicy !=
                BrowserAdMarkingNavigationPolicy.DEFAULT
        ) {
            when (
                resolveBrowserExternalNavigationDecision(
                    policy = parentSession.externalNavigationPolicy,
                    pageUrl = parentSession.currentUrl,
                    targetUrl = targetUrl,
                )
            ) {
                BrowserExternalNavigationDecision.ALLOW -> Unit
                BrowserExternalNavigationDecision.ASK -> {
                    tools.browserHost?.showAdMarkingNavigationRequest(
                        sessionId = parentSession.id,
                        payload =
                            JSONObject()
                                .put("url", targetUrl)
                                .put("text", "")
                                .toString(),
                    )
                    finish()
                    return
                }
                BrowserExternalNavigationDecision.BLOCK -> {
                    finish()
                    return
                }
            }
        }
        val sourceAtConfiguredHome =
            isAtConfiguredBrowserHome(
                currentUrl = parentSession.currentUrl,
                configuredHomeUrl = tools.browserSettingsStore.current.homeUrl,
                navigationState = parentSession.browserHomeNavigationState,
            )
        when (
            resolveBrowserWindowNavigationDecision(
                BrowserWindowNavigationRequest(
                    sourceUrl = parentSession.currentUrl,
                    targetUrl = targetUrl,
                    isMainFrame = true,
                    hasUserGesture = true,
                    isPopup = true,
                    sourceAtConfiguredHome = sourceAtConfiguredHome,
                ),
            )
        ) {
            BrowserWindowNavigationDecision.CURRENT_SESSION -> {
                tools.navigateSessionOnMain(parentSession, targetUrl)
                tools.ensureSessionAttachedOnMain(parentSession.id)
            }
            BrowserWindowNavigationDecision.CREATE_CHILD_SESSION -> {
                tools.createSessionTabOnMain(
                    appContext = parentSession.webView.context ?: tools.context.applicationContext,
                    initialUrl = targetUrl,
                    sessionName = parentSession.sessionName,
                    customUserAgent = parentSession.customUserAgent,
                    profile = parentSession.profile,
                    creationReason =
                        BrowserWindowCreationReason.HOME_CROSS_SITE_USER_NAVIGATION,
                    openerHomeSessionId = parentSession.id,
                )
            }
            BrowserWindowNavigationDecision.REJECT -> Unit
        }
        finish()
    }

    private fun finish() {
        if (settled) {
            return
        }
        settled = true
        StandardBrowserSessionTools.mainHandler.removeCallbacks(timeoutRunnable)
        webView.stopLoading()
        webView.destroy()
    }
}

private const val POPUP_TARGET_RESOLUTION_TIMEOUT_MILLIS = 2_000L

internal fun StandardBrowserSessionTools.findSessionByWebView(
    webView: WebView
): BrowserToolSession? = StandardBrowserSessionTools.sessions.values.firstOrNull { it.webView === webView }

internal fun StandardBrowserSessionTools.handleNavigationOverrideOnMain(
    request: WebResourceRequest,
    session: BrowserToolSession,
): Boolean {
    val uri = request.url
    val rawUrl = uri.toString()
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
    if (session.adMarkingActive) {
        // 标记模式必须先于任何 scheme、iframe 或弹窗导航分支完成硬锁定。原生触摸层负责
        // 把点击转换为元素选择，这里消费页面脚本和媒体组件仍可能直接发起的导航。
        return true
    }
    if (request.isForMainFrame && (scheme == "http" || scheme == "https")) {
        when (
            resolveBrowserExternalNavigationDecision(
                policy = session.externalNavigationPolicy,
                pageUrl = session.currentUrl,
                targetUrl = rawUrl,
            )
        ) {
            BrowserExternalNavigationDecision.ALLOW -> Unit
            BrowserExternalNavigationDecision.ASK -> {
                browserHost?.showAdMarkingNavigationRequest(
                    sessionId = session.id,
                    payload =
                        JSONObject()
                            .put("url", rawUrl)
                            .put("text", "")
                            .toString(),
                )
                return true
            }
            BrowserExternalNavigationDecision.BLOCK -> return true
        }
    }
    if (
        scheme != "http" &&
            scheme != "https" &&
            scheme != "about" &&
            !resolveWebSessionSiteFeatureEnabled(
                settings = browserSettingsStore.current,
                domainOrUrl = session.currentUrl,
                feature = WebSessionSiteFeature.WEB_PAGE_OPEN_APP,
                globalEnabled = browserSettingsStore.current.allowWebPageOpenApp,
            )
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
            } else if (request.isForMainFrame) {
                val decision =
                    resolveBrowserWindowNavigationDecision(
                        BrowserWindowNavigationRequest(
                            sourceUrl = session.currentUrl,
                            targetUrl = rawUrl,
                            isMainFrame = true,
                            hasUserGesture = request.hasGesture(),
                            isPopup = false,
                            sourceAtConfiguredHome =
                                isAtConfiguredBrowserHome(
                                    currentUrl = session.currentUrl,
                                    configuredHomeUrl = browserSettingsStore.current.homeUrl,
                                    navigationState = session.browserHomeNavigationState,
                                ),
                        ),
                    )
                when (decision) {
                    BrowserWindowNavigationDecision.CURRENT_SESSION -> false
                    BrowserWindowNavigationDecision.REJECT -> true
                    BrowserWindowNavigationDecision.CREATE_CHILD_SESSION -> {
                        createSessionTabOnMain(
                            appContext = session.webView.context ?: context.applicationContext,
                            initialUrl = rawUrl,
                            sessionName = session.sessionName,
                            customUserAgent = session.customUserAgent,
                            profile = session.profile,
                            creationReason =
                                BrowserWindowCreationReason
                                    .HOME_CROSS_SITE_USER_NAVIGATION,
                            openerHomeSessionId = session.id,
                        )
                        true
                    }
                }
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

internal fun StandardBrowserSessionTools.handleWebPermissionRequest(
    request: PermissionRequest,
    session: BrowserToolSession,
) {
    val requestedResources = request.resources?.distinct().orEmpty()
    if (requestedResources.isEmpty()) {
        request.deny()
        recordBrowserDiagnostic(
            level = BrowserDiagnosticLevel.WARNING,
            category = BrowserDiagnosticCategory.PERMISSION,
            event = "WEB_PERMISSION_DENIED_EMPTY",
            session = session,
        )
        return
    }

    val requiredPermissions =
        requestedResources
            .flatMap(::androidPermissionsForWebResource)
            .toCollection(LinkedHashSet())

    if (requiredPermissions.isEmpty()) {
        request.grant(requestedResources.toTypedArray())
        recordBrowserDiagnostic(
            level = BrowserDiagnosticLevel.INFO,
            category = BrowserDiagnosticCategory.PERMISSION,
            event = "WEB_PERMISSION_GRANTED",
            session = session,
            details = mapOf("resourceCount" to requestedResources.size.toString()),
        )
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
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.INFO,
                    category = BrowserDiagnosticCategory.PERMISSION,
                    event = "WEB_PERMISSION_GRANTED",
                    session = session,
                    details =
                        mapOf(
                            "requestedResourceCount" to requestedResources.size.toString(),
                            "grantedResourceCount" to grantableResources.size.toString(),
                        ),
                )
            } else {
                request.deny()
                recordBrowserDiagnostic(
                    level = BrowserDiagnosticLevel.WARNING,
                    category = BrowserDiagnosticCategory.PERMISSION,
                    event = "WEB_PERMISSION_DENIED",
                    session = session,
                    details = mapOf("resourceCount" to requestedResources.size.toString()),
                )
                showToast(context.getString(R.string.web_session_permission_denied))
            }
        }
    }
}

internal fun StandardBrowserSessionTools.handleGeolocationPermissionRequest(
    origin: String,
    callback: GeolocationPermissions.Callback,
    session: BrowserToolSession,
) {
    val settings = browserSettingsStore.current
    if (
        !resolveWebSessionSiteFeatureEnabled(
            settings = settings,
            domainOrUrl = origin,
            feature = WebSessionSiteFeature.WEB_PAGE_GEOLOCATION,
            globalEnabled = settings.allowWebPageGeolocation,
        )
    ) {
        callback.invoke(origin, false, false)
        recordBrowserDiagnostic(
            level = BrowserDiagnosticLevel.INFO,
            category = BrowserDiagnosticCategory.PERMISSION,
            event = "GEOLOCATION_DENIED_BY_POLICY",
            session = session,
            details = mapOf("origin" to origin),
        )
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
            recordBrowserDiagnostic(
                level =
                    if (granted) {
                        BrowserDiagnosticLevel.INFO
                    } else {
                        BrowserDiagnosticLevel.WARNING
                    },
                category = BrowserDiagnosticCategory.PERMISSION,
                event = if (granted) "GEOLOCATION_GRANTED" else "GEOLOCATION_DENIED",
                session = session,
                details = mapOf("origin" to origin),
            )
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
        reloadSessionOnMain(activeSession)
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
    val session = StandardBrowserSessionTools.sessions[sessionId] ?: return false
    recordBrowserDiagnostic(
        level = BrowserDiagnosticLevel.INFO,
        category = BrowserDiagnosticCategory.SESSION,
        event = "SESSION_CLOSING",
        session = session,
    )
    StandardBrowserSessionTools.sessions.remove(sessionId)
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

    scheduleBrowserRecoverySnapshotWrite()
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
