package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiStateStore
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserScreen
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionFloatingTheme
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionMinimizedIndicator
import com.ai.assistance.operit.util.AppLogger
import kotlin.math.roundToInt
import org.json.JSONTokener

internal class WebSessionBrowserHost(
    private val appContext: Context,
    private val store: WebSessionHistoryStore,
    private val userscriptStore: WebSessionUserscriptUiStateStore,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onNavigate(url: String)
        fun onBack()
        fun onForward()
        fun onRefreshOrStop()
        fun onSelectTab(sessionId: String)
        fun onCloseTab(sessionId: String)
        fun onNewTab(profile: WebSessionProfile)
        fun onRequestTabThumbnails()
        fun onOpenAppShellBrowser()
        fun onExitBrowser()
        fun onOpenBrowserSettings()
        fun onOpenDownloadSettings()
        fun onCloseCurrentTab()
        fun onCloseAllTabs(profile: WebSessionProfile)
        fun onRemoveBookmark(url: String)
        fun onBookmarkMutation(mutation: WebSessionBookmarkMutation)
        fun onOpenBookmarkInTab(url: String, active: Boolean)
        fun onSelectSessionHistory(index: Int)
        fun onOpenUrl(url: String)
        fun onClearHistory()
        fun onClearNetworkLog()
        fun onSelectUserAgentMode(mode: WebSessionUserAgentMode)
        fun onSaveCustomGlobalUserAgent(userAgent: String)
        fun onSaveSiteUserAgentRule(domain: String, userAgent: String)
        fun onSetSearchEngine(engine: WebSessionSearchEngine)
        fun onSetDefaultSessionProfile(profile: WebSessionProfile): Boolean
        fun onSubmitSearch(
            query: String,
            engine: WebSessionSearchEngine,
            profile: WebSessionProfile,
        )
        fun onOpenSearchRecord(
            record: WebSessionSearchRecord,
            profile: WebSessionProfile,
        )
        fun onDeleteSearchHistory(id: Long)
        fun onClearSearchHistory()
        fun onCopyCurrentUrl()
        fun onOpenPageSource()
        fun onCopyPageSource()
        fun onOpenUserscripts()
        fun onImportUserscript()
        fun onInstallUserscriptFromUrl(url: String)
        fun onConfirmUserscriptInstall()
        fun onCancelUserscriptInstall()
        fun onSetUserscriptEnabled(scriptId: Long, enabled: Boolean)
        fun onDeleteUserscript(scriptId: Long)
        fun onCheckUserscriptUpdate(scriptId: Long)
        fun onInvokeUserscriptMenu(commandId: String)
        fun onPlayMediaCandidate(candidateId: String): Boolean
        fun onDownloadMediaCandidate(candidateId: String): Boolean
        fun onTogglePlayerPause()
        fun onOpenPlayerFullscreen()
        fun onLaunchPlayerFullscreen()
        fun onClosePlayer()
        fun onPauseDownload(taskId: String)
        fun onResumeDownload(taskId: String)
        fun onCancelDownload(taskId: String)
        fun onRetryDownload(taskId: String)
        fun onDeleteDownload(taskId: String, deleteFile: Boolean)
        fun onOpenDownloadedFile(taskId: String)
        fun onOpenDownloadFileManager()
        fun onStartManualDownload(
            fileName: String,
            url: String,
            suffix: String,
            engine: BrowserDownloadEngine,
        ): Boolean
        fun onRedownload(taskId: String)
        fun onRenameDownload(taskId: String, targetFileName: String, mode: BrowserDownloadRenameMode)
        fun onMoveDownload(taskId: String, treeUriString: String)
        fun onCopyDownloadUrl(taskId: String)
        fun onShareDownload(taskId: String)
        fun onCopyDownloadLocation(taskId: String)
        fun onTransferDownload(taskId: String)
        fun onMergeDownloadToMp4(taskId: String)
        fun onConfirmBrowserDownload(requestId: String)
        fun onCancelBrowserDownload(requestId: String)
        fun onConfirmExternalOpen(requestId: String)
        fun onCancelExternalOpen(requestId: String)
        fun onHandlePendingDialog(accept: Boolean, promptText: String?)
    }

    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val backgroundAnchor = BrowserBackgroundAnchor(appContext)
    private val browserSettingsStore = WebSessionBrowserSettingsStore.getInstance(appContext)
    private var appWebViewHost: WebSessionWebViewHost? = null
    private var activeWebView: WebView? = null

    private var indicatorView: ComposeView? = null
    private var indicatorParams: WindowManager.LayoutParams? = null
    private var indicatorLifecycleOwner: WebSessionOverlayLifecycleOwner? = null

    private var appPresentationActive by mutableStateOf(false)
    private var hostState by mutableStateOf(WebSessionBrowserHostState())
    private val choreographer = Choreographer.getInstance()
    private var requestedPresentationTarget = BrowserPresentationTarget.DETACHED
    private var attachedPresentationTarget = BrowserPresentationTarget.DETACHED
    private var pendingPresentationTarget: BrowserPresentationTarget? = null
    private var pendingPresentationFrameCallback: Choreographer.FrameCallback? = null
    private var presentationTransferGeneration = 0L

    fun ensureBackgroundAnchorCreated() {
        if (appPresentationActive) return
        requestPresentationTarget(BrowserPresentationTarget.BACKGROUND_ANCHOR)
    }

    fun destroy() {
        hideTextSelectionActionsOverlay()
        hideIndicator()
        cancelPendingPresentationTransfer()
        detachPresentationWebViews()
        backgroundAnchor.destroy()
        indicatorParams = null
        appWebViewHost?.clear()
        appWebViewHost = null
        activeWebView = null
        appPresentationActive = false
        requestedPresentationTarget = BrowserPresentationTarget.DETACHED
        attachedPresentationTarget = BrowserPresentationTarget.DETACHED
        pendingPresentationTarget = null
    }

    @Composable
    fun BrowserContent(
        webViewHost: WebSessionWebViewHost,
        onTopBarBack: () -> Unit,
        onOpenAiDialogue: () -> Unit,
        onOpenBrowserSettings: () -> Unit,
        onOpenDownloadSettings: () -> Unit,
        onExitBrowser: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val bookmarks by store.bookmarksFlow.collectAsState(initial = emptyList())
        val bookmarkFolders by store.bookmarkFoldersFlow.collectAsState(initial = emptyList())
        val history by store.historyFlow.collectAsState(initial = emptyList())
        val searchEngine by store.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
        val searchHistory by store.searchHistoryFlow.collectAsState(initial = emptyList())
        val userscriptUiState by userscriptStore.state.collectAsState()
        val browserSettings by browserSettingsStore.state.collectAsState()
        val playerSession = PlayerSession.getInstance(appContext)
        val playerState by playerSession.state.collectAsState()

        WebSessionBrowserScreen(
            hostState = hostState,
            bookmarks = bookmarks,
            bookmarkFolders = bookmarkFolders,
            globalHistory = history,
            searchEngine = searchEngine,
            searchHistory = searchHistory,
            userscriptUiState = userscriptUiState,
            webViewHost = webViewHost,
            onHostStateChange = ::updateHostState,
            onNavigate = callbacks::onNavigate,
            onBack = callbacks::onBack,
            onForward = callbacks::onForward,
            onRefreshOrStop = callbacks::onRefreshOrStop,
            onSelectTab = callbacks::onSelectTab,
            onCloseTab = callbacks::onCloseTab,
            onNewTab = callbacks::onNewTab,
            onRequestTabThumbnails = callbacks::onRequestTabThumbnails,
            onTopBarBack = onTopBarBack,
            onOpenAiDialogue = onOpenAiDialogue,
            onOpenBrowserSettings = onOpenBrowserSettings,
            onOpenDownloadSettings = onOpenDownloadSettings,
            onExitBrowser = onExitBrowser,
            onCloseCurrentTab = callbacks::onCloseCurrentTab,
            onCloseAllTabs = callbacks::onCloseAllTabs,
            onRemoveBookmark = callbacks::onRemoveBookmark,
            onBookmarkMutation = callbacks::onBookmarkMutation,
            onOpenBookmarkInTab = callbacks::onOpenBookmarkInTab,
            onSelectSessionHistory = callbacks::onSelectSessionHistory,
            onOpenUrl = callbacks::onOpenUrl,
            onClearHistory = callbacks::onClearHistory,
            onClearNetworkLog = callbacks::onClearNetworkLog,
            onSelectUserAgentMode = callbacks::onSelectUserAgentMode,
            onSaveCustomGlobalUserAgent = callbacks::onSaveCustomGlobalUserAgent,
            onSaveSiteUserAgentRule = callbacks::onSaveSiteUserAgentRule,
            onSetSearchEngine = callbacks::onSetSearchEngine,
            onSetDefaultSessionProfile = callbacks::onSetDefaultSessionProfile,
            onSubmitSearch = callbacks::onSubmitSearch,
            onOpenSearchRecord = callbacks::onOpenSearchRecord,
            onDeleteSearchHistory = callbacks::onDeleteSearchHistory,
            onClearSearchHistory = callbacks::onClearSearchHistory,
            onCopyCurrentUrl = callbacks::onCopyCurrentUrl,
            onOpenPageSource = callbacks::onOpenPageSource,
            onCopyPageSource = callbacks::onCopyPageSource,
            onOpenUserscripts = callbacks::onOpenUserscripts,
            onImportUserscript = callbacks::onImportUserscript,
            onInstallUserscriptFromUrl = callbacks::onInstallUserscriptFromUrl,
            onConfirmUserscriptInstall = callbacks::onConfirmUserscriptInstall,
            onCancelUserscriptInstall = callbacks::onCancelUserscriptInstall,
            onSetUserscriptEnabled = callbacks::onSetUserscriptEnabled,
            onDeleteUserscript = callbacks::onDeleteUserscript,
            onCheckUserscriptUpdate = callbacks::onCheckUserscriptUpdate,
            onInvokeUserscriptMenu = callbacks::onInvokeUserscriptMenu,
            playerSession = playerSession,
            playerState = playerState,
            onPlayMediaCandidate = callbacks::onPlayMediaCandidate,
            onDownloadMediaCandidate = callbacks::onDownloadMediaCandidate,
            onTogglePlayerPause = callbacks::onTogglePlayerPause,
            onOpenPlayerFullscreen = callbacks::onOpenPlayerFullscreen,
            onLaunchPlayerFullscreen = callbacks::onLaunchPlayerFullscreen,
            onClosePlayer = callbacks::onClosePlayer,
            onPauseDownload = callbacks::onPauseDownload,
            onResumeDownload = callbacks::onResumeDownload,
            onCancelDownload = callbacks::onCancelDownload,
            onRetryDownload = callbacks::onRetryDownload,
            onDeleteDownload = callbacks::onDeleteDownload,
            onOpenDownloadedFile = callbacks::onOpenDownloadedFile,
            onOpenDownloadFileManager = callbacks::onOpenDownloadFileManager,
            onStartManualDownload = callbacks::onStartManualDownload,
            onRedownload = callbacks::onRedownload,
            onRenameDownload = callbacks::onRenameDownload,
            onMoveDownload = callbacks::onMoveDownload,
            onCopyDownloadUrl = callbacks::onCopyDownloadUrl,
            onShareDownload = callbacks::onShareDownload,
            onCopyDownloadLocation = callbacks::onCopyDownloadLocation,
            onTransferDownload = callbacks::onTransferDownload,
            onMergeDownloadToMp4 = callbacks::onMergeDownloadToMp4,
            onConfirmBrowserDownload = callbacks::onConfirmBrowserDownload,
            onCancelBrowserDownload = callbacks::onCancelBrowserDownload,
            onConfirmExternalOpen = callbacks::onConfirmExternalOpen,
            onCancelExternalOpen = callbacks::onCancelExternalOpen,
            onHandlePendingDialog = callbacks::onHandlePendingDialog,
            onCopyTextSelection = ::copyActiveWebViewSelection,
            onSelectAllTextSelection = ::selectAllActiveWebViewText,
            onDismissTextSelection = ::dismissTextSelectionActions,
            homeUrl = browserSettings.homeUrl,
            modifier = modifier,
        )
    }

    fun updateHostProjection(
        browserState: WebSessionBrowserState,
        downloadUiState: BrowserDownloadUiState,
        externalOpenPrompt: ExternalOpenPromptState?,
        downloadPrompt: BrowserDownloadPromptState?,
    ) {
        hostState =
            hostState.copy(
                browserState = browserState,
                downloadUiState = downloadUiState,
                externalOpenPrompt = externalOpenPrompt,
                downloadPrompt = downloadPrompt,
            )
        updateIndicatorLayoutForCurrentState()
    }

    fun attachActiveWebView(webView: WebView?) {
        if (activeWebView === webView) {
            if (pendingPresentationFrameCallback == null) {
                requestPresentationTarget(requestedPresentationTarget)
            }
            return
        }

        detachPresentationWebViews()
        attachedPresentationTarget = BrowserPresentationTarget.DETACHED
        activeWebView = webView
        if (pendingPresentationFrameCallback == null) {
            requestPresentationTarget(requestedPresentationTarget)
        }
    }

    fun acquireAppPresentation(webViewHost: WebSessionWebViewHost) {
        if (appWebViewHost === webViewHost && appPresentationActive) {
            requestPresentationTarget(BrowserPresentationTarget.APP_SHELL)
            return
        }

        if (
            appWebViewHost !== webViewHost &&
                attachedPresentationTarget == BrowserPresentationTarget.APP_SHELL
        ) {
            appWebViewHost?.detachActiveWebView()
            verifyActiveWebViewDetached()
            attachedPresentationTarget = BrowserPresentationTarget.DETACHED
        }
        appWebViewHost = webViewHost
        appPresentationActive = true
        hideIndicator()
        requestPresentationTarget(BrowserPresentationTarget.APP_SHELL)
    }

    fun releaseAppPresentation(
        webViewHost: WebSessionWebViewHost,
        keepInBackgroundAnchor: Boolean,
    ): Boolean {
        if (appWebViewHost !== webViewHost) {
            return false
        }

        appPresentationActive = false
        hideTextSelectionActionsOverlay()
        requestPresentationTarget(
            if (keepInBackgroundAnchor) {
                BrowserPresentationTarget.BACKGROUND_ANCHOR
            } else {
                BrowserPresentationTarget.DETACHED
            },
        )
        appWebViewHost = null
        return true
    }

    fun hasAppPresentation(): Boolean = appPresentationActive

    fun hasBackgroundAnchorPresentation(): Boolean =
        requestedPresentationTarget == BrowserPresentationTarget.BACKGROUND_ANCHOR

    fun requestMediaCandidateDownload(candidateId: String): Boolean =
        callbacks.onDownloadMediaCandidate(candidateId)

    private fun detachPresentationWebViews() {
        appWebViewHost?.detachActiveWebView()
        backgroundAnchor.detachActiveWebView()
        verifyActiveWebViewDetached()
    }

    private fun requestPresentationTarget(target: BrowserPresentationTarget) {
        requestedPresentationTarget = target

        if (pendingPresentationFrameCallback != null) {
            if (target == BrowserPresentationTarget.DETACHED) {
                cancelPendingPresentationTransfer()
                detachPresentationWebViews()
                backgroundAnchor.detachWindow()
                attachedPresentationTarget = BrowserPresentationTarget.DETACHED
                hideIndicator()
            } else {
                pendingPresentationTarget = target
                if (target == BrowserPresentationTarget.APP_SHELL) {
                    hideIndicator()
                }
            }
            return
        }

        val plan =
            BrowserBackgroundAnchorPolicy.resolveTransferPlan(
                currentTarget = attachedPresentationTarget,
                requestedTarget = target,
                requestedTargetAlreadyOwnsActiveWebView =
                    presentationTargetIsAssignedToActiveWebView(target),
            )
        AppLogger.d(
            "WebSessionBrowserHost",
            "Browser presentation transfer current=$attachedPresentationTarget requested=$target plan=$plan",
        )
        when (plan) {
            BrowserPresentationTransferPlan.NO_OP -> Unit
            BrowserPresentationTransferPlan.DETACH_ONLY -> {
                detachPresentationWebViews()
                backgroundAnchor.detachWindow()
                attachedPresentationTarget = BrowserPresentationTarget.DETACHED
                hideIndicator()
            }
            BrowserPresentationTransferPlan.ATTACH_NOW -> {
                detachPresentationWebViews()
                attachedPresentationTarget = BrowserPresentationTarget.DETACHED
                attachPresentationNow(target)
            }
            BrowserPresentationTransferPlan.ATTACH_AFTER_FRAME -> {
                // Moving a hardware-rendered WebView between the Activity ViewRoot and a
                // WindowManager ViewRoot in one frame can overlap their Surface transactions.
                // Keep the strict null-parent check, remove the old anchor window, and let one
                // render frame complete before the target root receives the same WebView.
                detachPresentationWebViews()
                backgroundAnchor.detachWindow()
                attachedPresentationTarget = BrowserPresentationTarget.DETACHED
                hideIndicator()
                schedulePresentationAttachAfterFrame(target)
            }
        }
    }

    private fun presentationTargetIsAssignedToActiveWebView(
        target: BrowserPresentationTarget,
    ): Boolean =
        when (target) {
            BrowserPresentationTarget.APP_SHELL ->
                appWebViewHost?.isAssignedTo(activeWebView) == true
            BrowserPresentationTarget.BACKGROUND_ANCHOR ->
                backgroundAnchor.isAttached && backgroundAnchor.isAssignedTo(activeWebView)
            BrowserPresentationTarget.DETACHED ->
                attachedPresentationTarget == BrowserPresentationTarget.DETACHED &&
                    activeWebView?.parent == null
        }

    private fun attachPresentationNow(target: BrowserPresentationTarget): Boolean {
        if (target != requestedPresentationTarget) {
            return true
        }
        verifyActiveWebViewDetached()
        when (target) {
            BrowserPresentationTarget.DETACHED -> {
                attachedPresentationTarget = BrowserPresentationTarget.DETACHED
                hideIndicator()
            }
            BrowserPresentationTarget.APP_SHELL -> {
                if (backgroundAnchor.isWindowAttachedToRoot) {
                    return false
                }
                val appHost = appWebViewHost ?: return false
                appHost.setActiveWebView(activeWebView)
                attachedPresentationTarget = BrowserPresentationTarget.APP_SHELL
                hideIndicator()
            }
            BrowserPresentationTarget.BACKGROUND_ANCHOR -> {
                val position = indicatorParams?.let { it.x to it.y } ?: (dp(16) to dp(16))
                if (!backgroundAnchor.ensureAttached(position.first, position.second)) {
                    return false
                }
                backgroundAnchor.setActiveWebView(activeWebView)
                attachedPresentationTarget = BrowserPresentationTarget.BACKGROUND_ANCHOR
                showIndicator()
            }
        }
        AppLogger.d(
            "WebSessionBrowserHost",
            "Browser presentation attached target=$attachedPresentationTarget",
        )
        return true
    }

    private fun schedulePresentationAttachAfterFrame(target: BrowserPresentationTarget) {
        pendingPresentationTarget = target
        val generation = ++presentationTransferGeneration
        val callback =
            Choreographer.FrameCallback {
                if (generation != presentationTransferGeneration) {
                    return@FrameCallback
                }
                pendingPresentationFrameCallback = null
                val pendingTarget = pendingPresentationTarget ?: return@FrameCallback
                pendingPresentationTarget = null
                if (!attachPresentationNow(pendingTarget)) {
                    schedulePresentationAttachAfterFrame(pendingTarget)
                }
            }
        pendingPresentationFrameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun cancelPendingPresentationTransfer() {
        pendingPresentationFrameCallback?.let(choreographer::removeFrameCallback)
        pendingPresentationFrameCallback = null
        pendingPresentationTarget = null
        presentationTransferGeneration += 1
    }

    private fun verifyActiveWebViewDetached() {
        val parent = activeWebView?.parent
        check(parent == null) {
            "Active WebView must be detached before browser presentation transfer: $parent"
        }
    }

    fun handleBack(): Boolean {
        if (hostState.textSelectionActions != null) {
            hideTextSelectionActionsOverlay()
            return true
        }

        val browserState = hostState.browserState
        val pendingDialog = browserState.pendingDialog
        if (pendingDialog != null) {
            callbacks.onHandlePendingDialog(false, null)
            return true
        }

        val downloadPrompt = hostState.downloadPrompt
        if (downloadPrompt != null) {
            callbacks.onCancelBrowserDownload(downloadPrompt.requestId)
            return true
        }

        val externalPrompt = hostState.externalOpenPrompt
        if (externalPrompt != null) {
            callbacks.onCancelExternalOpen(externalPrompt.requestId)
            return true
        }

        if (hostState.sheetRoute != WebSessionBrowserSheetRoute.NONE) {
            updateHostState { current ->
                current.copy(sheetRoute = WebSessionBrowserSheetRoute.NONE)
            }
            return true
        }

        if (hostState.isSearchEnginePanelVisible) {
            updateHostState { current -> current.copy(isSearchEnginePanelVisible = false) }
            return true
        }

        if (hostState.isSearchVisible) {
            updateHostState { current ->
                current.copy(
                    isSearchVisible = false,
                    isSearchEnginePanelVisible = false,
                    searchDraft = "",
                )
            }
            return true
        }

        if (browserState.canGoBack) {
            callbacks.onBack()
            return true
        }

        return false
    }

    fun showTextSelectionActionsOverlay(anchorX: Double, anchorY: Double) {
        if (!appPresentationActive) return
        val webView = activeWebView ?: return
        hostState =
            hostState.copy(
                textSelectionActions =
                    WebSessionTextSelectionActionsState(
                        anchorXPx = (anchorX * webView.scale).roundToInt(),
                        anchorYPx = (anchorY * webView.scale).roundToInt(),
                    ),
            )
    }

    fun performTextSelectionHaptic() {
        activeWebView?.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    fun hideTextSelectionActionsOverlay() {
        if (hostState.textSelectionActions != null) {
            hostState = hostState.copy(textSelectionActions = null)
        }
    }

    fun setViewportSize(width: Int, height: Int) {
        updateHostState {
            it.copy(
                viewportWidthPx = width.coerceAtLeast(dp(240)),
                viewportHeightPx = height.coerceAtLeast(dp(320))
            )
        }
    }

    fun clearViewportSizeOverride() {
        updateHostState { it.copy(viewportWidthPx = null, viewportHeightPx = null) }
    }

    fun currentViewportSize(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        val width = hostState.viewportWidthPx ?: metrics.widthPixels
        val height = hostState.viewportHeightPx ?: metrics.heightPixels
        return width to height
    }

    fun currentBrowserAreaSize(): Pair<Int, Int> =
        hostState.browserAreaWidthPx.coerceAtLeast(0) to hostState.browserAreaHeightPx.coerceAtLeast(0)


    fun showSheet(route: WebSessionBrowserSheetRoute) {
        updateHostState { it.copy(sheetRoute = route) }
    }

    fun beginPageSourceRead() {
        updateHostState {
            it.copy(
                sheetRoute = WebSessionBrowserSheetRoute.PAGE_SOURCE,
                pageSource = WebSessionPageSourceState(isLoading = true),
            )
        }
        val webView = activeWebView
        if (webView == null) {
            updateHostState {
                it.copy(
                    pageSource = WebSessionPageSourceState(
                        error = appContext.getString(R.string.web_session_source_unavailable),
                    )
                )
            }
            return
        }
        webView.evaluateJavascript(
            """
            (function() {
                var root = document.documentElement;
                return root ? root.outerHTML : "";
            })();
            """.trimIndent()
        ) { rawValue ->
            try {
                val content = JSONTokener(rawValue).nextValue() as? String
                if (content.isNullOrBlank()) {
                    updateHostState {
                        it.copy(
                            pageSource = WebSessionPageSourceState(
                                error = appContext.getString(R.string.web_session_source_empty),
                            )
                        )
                    }
                } else {
                    updateHostState {
                        it.copy(
                            pageSource = WebSessionPageSourceState(content = content),
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("WebSessionBrowserHost", "Failed to read current page source", e)
                updateHostState {
                    it.copy(
                        pageSource = WebSessionPageSourceState(
                            error = appContext.getString(R.string.web_session_source_read_failed),
                        )
                    )
                }
            }
        }
    }

    fun copyCurrentUrlToClipboard() {
        val url = hostState.browserState.currentUrl
        if (url.isBlank()) {
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("web_url", url))
    }

    fun copyPageSourceToClipboard() {
        val content = hostState.pageSource.content
        if (content.isNullOrBlank()) {
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("page_source", content))
    }

    private fun updateHostState(transform: (WebSessionBrowserHostState) -> WebSessionBrowserHostState) {
        val updated = transform(hostState)
        if (updated == hostState) {
            return
        }
        hostState = updated
    }

    private fun copyActiveWebViewSelection() {
        evaluateActiveWebViewSelectedText { selectedText ->
            if (selectedText.isEmpty()) {
                Toast.makeText(appContext, appContext.getString(R.string.no_text_to_copy), Toast.LENGTH_SHORT).show()
                return@evaluateActiveWebViewSelectedText
            }
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("web_selection", selectedText))
            clearActiveWebViewSelection()
        }
    }

    private fun evaluateActiveWebViewSelectedText(onResult: (String) -> Unit) {
        val webView = activeWebView ?: return
        webView.evaluateJavascript(activeWebViewSelectionTextScript()) { rawValue ->
            try {
                val selectedText = JSONTokener(rawValue).nextValue() as String
                onResult(selectedText)
            } catch (e: Exception) {
                AppLogger.e("WebSessionBrowserHost", "Failed to read selected WebView text", e)
            }
        }
    }

    private fun activeWebViewSelectionTextScript(): String =
        """
        (function() {
            return String(window.__operitTextSelection.getText());
        })();
        """.trimIndent()

    private fun selectAllActiveWebViewText() {
        val webView = activeWebView ?: return
        webView.evaluateJavascript(
            """
            (function() {
                window.__operitTextSelection.selectAll();
            })();
            """.trimIndent(),
            null
        )
    }

    private fun dismissTextSelectionActions() {
        clearActiveWebViewSelection()
    }

    private fun clearActiveWebViewSelection() {
        val webView = activeWebView ?: return
        webView.evaluateJavascript(
            """
            (function() {
                window.__operitTextSelection.clear();
            })();
            """.trimIndent(),
            null
        )
        hideTextSelectionActionsOverlay()
    }

    private fun syncBackgroundAnchorWithIndicator() {
        val indicator = indicatorParams ?: return
        backgroundAnchor.moveTo(indicator.x, indicator.y)
    }

    private fun showIndicator() {
        if (appPresentationActive || !backgroundAnchor.isAttached || indicatorView != null) {
            return
        }

        val lifecycleOwner =
            WebSessionOverlayLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

        val params = indicatorParams ?: createIndicatorLayoutParams().also { indicatorParams = it }

        val indicator =
            ComposeView(appContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                installViewTreeOwners(this, lifecycleOwner)
                setContent {
                    WebSessionFloatingTheme {
                        WebSessionMinimizedIndicator(
                            contentDescription =
                                appContext.getString(R.string.web_session_accessibility_minimized_indicator),
                            activeDownloadCount = hostState.browserState.activeDownloadCount,
                            hasFailedDownloads = hostState.browserState.hasFailedDownloads,
                            downloadPrompt = hostState.downloadPrompt,
                            externalOpenPrompt = hostState.externalOpenPrompt,
                            onOpenBrowser = callbacks::onOpenAppShellBrowser,
                            onDragBy = { dx, dy -> moveIndicatorBy(dx, dy) },
                            onConfirmBrowserDownload = callbacks::onConfirmBrowserDownload,
                            onCancelBrowserDownload = callbacks::onCancelBrowserDownload,
                            onConfirmExternalOpen = callbacks::onConfirmExternalOpen,
                            onCancelExternalOpen = callbacks::onCancelExternalOpen
                        )
                    }
                }
            }

        indicatorLifecycleOwner = lifecycleOwner
        indicatorView = indicator
        windowManager.addView(indicator, params)
    }

    private fun hideIndicator() {
        val view = indicatorView ?: return
        indicatorLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        indicatorLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        indicatorLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        indicatorView = null
        indicatorLifecycleOwner = null
    }

    private fun moveIndicatorBy(dx: Int, dy: Int) {
        val indicator = indicatorView ?: return
        val params = indicatorParams ?: return
        val maxX = (appContext.resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (appContext.resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0)

        params.x = (params.x + dx).coerceIn(0, maxX)
        params.y = (params.y + dy).coerceIn(0, maxY)
        indicatorParams = params
        windowManager.updateViewLayout(indicator, params)
        syncBackgroundAnchorWithIndicator()
    }

    private fun updateIndicatorLayoutForCurrentState() {
        if (appPresentationActive) {
            return
        }
        val indicator = indicatorView ?: return
        val params = indicatorParams ?: return
        val newWidth = indicatorWidthPx()
        val newHeight = indicatorHeightPx()
        if (params.width == newWidth && params.height == newHeight) {
            return
        }
        params.width = newWidth
        params.height = newHeight
        indicatorParams = params
        if (indicator.windowToken != null) {
            windowManager.updateViewLayout(indicator, params)
        }
        syncBackgroundAnchorWithIndicator()
    }

    private fun installViewTreeOwners(
        view: View,
        lifecycleOwner: WebSessionOverlayLifecycleOwner
    ) {
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
    }

    private fun createIndicatorLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        return WindowManager.LayoutParams(
            indicatorWidthPx(),
            indicatorHeightPx(),
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(16)
        }
    }

    private fun indicatorWidthPx(): Int =
        if (hostState.downloadPrompt != null || hostState.externalOpenPrompt != null) {
            dp(248)
        } else {
            dp(40).coerceAtLeast(1)
        }

    private fun indicatorHeightPx(): Int =
        if (hostState.downloadPrompt != null || hostState.externalOpenPrompt != null) {
            dp(86)
        } else {
            dp(40).coerceAtLeast(1)
        }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).roundToInt()
}

private class WebSessionOverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreField = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            savedStateRegistryController.performRestore(null)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreField

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.handleLifecycleEvent(event)
        } else {
            Handler(Looper.getMainLooper()).post {
                lifecycleRegistry.handleLifecycleEvent(event)
            }
        }
    }
}
