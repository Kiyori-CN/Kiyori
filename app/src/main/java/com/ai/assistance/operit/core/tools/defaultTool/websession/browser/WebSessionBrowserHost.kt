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
import androidx.compose.runtime.LaunchedEffect
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
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiStateStore
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserScreen
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionFloatingTheme
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionMinimizedCloseAction
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionMinimizedIndicator
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import com.kiyori.platform.network.KiyoriNetworkProxyStoreState
import kotlin.math.roundToInt
import org.json.JSONObject
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
        fun onRefresh()
        fun onSelectTab(sessionId: String)
        fun onCloseTab(sessionId: String)
        fun onNewTab(profile: WebSessionProfile)
        fun onRequestTabThumbnails()
        fun onOpenAppShellBrowser()
        fun onRestoreAppShellBrowserFromIndicator()
        fun onExitBrowser()
        fun onOpenBrowserSettings()
        fun onOpenDownloadSettings()
        fun onCloseCurrentTab()
        fun onCloseAllTabs(profile: WebSessionProfile)
        fun onRemoveBookmark(url: String)
        fun onBookmarkMutation(mutation: WebSessionBookmarkMutation)
        fun onOpenBookmarkInTab(url: String, active: Boolean)
        fun onOpenUrl(url: String)
        fun onOpenExternalUrl(url: String)
        fun onBuildImageRequestHeaders(url: String, pageUrl: String): Map<String, String>
        fun onOpenHistoryEntry(entry: WebSessionHistoryEntry): Boolean
        fun onDeleteHistory(category: WebSessionHistoryCategory?, cutoffTimeMillis: Long?)
        fun onDeleteHistoryEntries(entryKeys: Set<WebSessionHistoryEntryKey>)
        fun onClearNetworkLog()
        fun onClearDiagnosticLog(scope: BrowserDiagnosticScope)
        fun onAddNetworkBlockRule(url: String)
        fun onAddElementBlockRule(domain: String, selector: String)
        fun onSetExternalNavigationPolicy(policy: BrowserAdMarkingNavigationPolicy)
        fun onSetAdMarkingActive(active: Boolean)
        fun onClearAdBlockRulesForDomain(domain: String): BrowserAdBlockDomainClearResult
        fun onSelectUserAgentMode(mode: WebSessionUserAgentMode)
        fun onSaveCustomGlobalUserAgent(userAgent: String)
        fun onSaveSiteUserAgentRule(domain: String, userAgent: String)
        fun onSetSiteFeatureDisabled(
            domain: String,
            feature: WebSessionSiteFeature,
            disabled: Boolean,
        )
        fun onClearSiteSettings(domain: String)
        fun onSetSiteAdBlockingDisabled(domain: String, disabled: Boolean)
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
        fun onPageSourceApplied(sessionId: String)
        fun onOpenPlugins()
        fun onRefreshCookies()
        fun onSetCookieReaderEnabled(enabled: Boolean)
        fun onImportUserscript()
        fun onInstallUserscriptFromUrl(url: String)
        fun onConfirmUserscriptInstall()
        fun onCancelUserscriptInstall()
        fun onSetUserScriptsAllowed(allowed: Boolean)
        fun onSetUserscriptEnabled(scriptId: Long, enabled: Boolean)
        fun onDeleteUserscript(scriptId: Long)
        fun onCheckUserscriptUpdate(scriptId: Long)
        fun onCheckAllUserscriptUpdates()
        fun onApplyUserscriptUpdate(scriptId: Long)
        fun onApplyAllSafeUserscriptUpdates()
        fun onSetUserscriptsEnabled(scriptIds: Set<Long>, enabled: Boolean)
        fun onDeleteUserscripts(scriptIds: Set<Long>)
        fun onLoadUserscriptDetail(scriptId: Long)
        fun onOpenNewUserscriptEditor()
        fun onOpenExistingUserscriptEditor(scriptId: Long)
        fun onOpenUserscriptDraftEditor(draftId: String)
        fun onUpdateUserscriptEditorBuffer(draftId: String, source: String)
        fun onPersistUserscriptDraft(draftId: String, onComplete: (() -> Unit)? = null)
        fun onDiscardUserscriptDraft(draftId: String, onComplete: (() -> Unit)? = null)
        fun onValidateUserscriptDraft(draftId: String)
        fun onFormatUserscriptDraft(draftId: String)
        fun onApplyUserscriptDraft(draftId: String)
        fun onInvokeUserscriptMenu(commandId: String)
        fun onPlayMediaCandidate(candidateId: String): Boolean
        fun onPlayMediaCandidateFloating(candidateId: String): Boolean
        fun onDownloadMediaCandidate(
            candidateId: String,
            sourceSessionId: String?,
            destination: BrowserDownloadDestination,
        ): Boolean
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
        fun onHandlePendingDialog(accept: Boolean, promptText: String?)
    }

    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val backgroundAnchor = BrowserBackgroundAnchor(appContext)
    private val browserSettingsStore = WebSessionBrowserSettingsStore.getInstance(appContext)
    private val adBlockStore = BrowserAdBlockStore.getInstance(appContext)
    private val imageQrCodeRecognizer = BrowserImageQrCodeRecognizer(appContext)
    private val browserOperationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appWebViewHost: WebSessionWebViewHost? = null
    private var activeWebView: WebView? = null

    private var indicatorView: ComposeView? = null
    private var indicatorParams: WindowManager.LayoutParams? = null
    private var indicatorLifecycleOwner: WebSessionOverlayLifecycleOwner? = null
    private var indicatorCloseActionView: ComposeView? = null
    private var indicatorCloseActionParams: WindowManager.LayoutParams? = null
    private val indicatorHandler = Handler(Looper.getMainLooper())
    private var indicatorCloseState = BrowserMinimizedIndicatorCloseState()
    private var indicatorCloseHideRunnable: Runnable? = null

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
        browserOperationScope.cancel()
        exitAdMarking()
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
        notifyBrowserWorkspaceClosed()
    }

    @Composable
    fun BrowserContent(
        webViewHost: WebSessionWebViewHost,
        onTopBarBack: () -> Unit,
        onOpenAiDialogue: () -> Unit,
        onOpenSettingsHome: () -> Unit,
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
        val adBlockState by adBlockStore.state.collectAsState()
        val networkProxyStoreState by
            KiyoriNetworkProxyManager.getInstance(appContext).configState.collectAsState()
        val networkProxyEnabled =
            (networkProxyStoreState as? KiyoriNetworkProxyStoreState.Ready)?.config?.enabled == true
        val playerSession = PlayerSession.getInstance(appContext)
        val playerState by playerSession.state.collectAsState()
        val currentPageUrl = hostState.browserState.currentUrl
        val webElementLongPressMenuEnabled =
            resolveWebSessionSiteFeatureEnabled(
                settings = browserSettings,
                domainOrUrl = currentPageUrl,
                feature = WebSessionSiteFeature.WEB_ELEMENT_LONG_PRESS_MENU,
                globalEnabled = browserSettings.webElementLongPressMenuEnabled,
            )
        val showMediaCandidateBadge =
            resolveWebSessionSiteFeatureEnabled(
                settings = browserSettings,
                domainOrUrl = currentPageUrl,
                feature = WebSessionSiteFeature.MEDIA_CANDIDATE_BADGE,
                globalEnabled = browserSettings.showMediaCandidateBadge,
            )
        val automaticFloatingPlaybackEnabled =
            resolveWebSessionSiteFeatureEnabled(
                settings = browserSettings,
                domainOrUrl = currentPageUrl,
                feature = WebSessionSiteFeature.AUTOMATIC_FLOATING_PLAYBACK,
                globalEnabled = browserSettings.automaticFloatingPlaybackEnabled,
            )
        val swipeHistoryNavigationEnabled =
            resolveWebSessionSiteFeatureEnabled(
                settings = browserSettings,
                domainOrUrl = currentPageUrl,
                feature = WebSessionSiteFeature.SWIPE_HISTORY_NAVIGATION,
                globalEnabled = browserSettings.swipeHistoryNavigationEnabled,
            )
        LaunchedEffect(
            webElementLongPressMenuEnabled,
            browserSettings.siteSettingsRules,
            currentPageUrl,
        ) {
            applyWebElementLongPressMenuSetting(webElementLongPressMenuEnabled)
        }

        WebSessionBrowserScreen(
            hostState = hostState,
            browserSettings = browserSettings,
            adBlockState = adBlockState,
            networkProxyEnabled = networkProxyEnabled,
            bookmarks = bookmarks,
            bookmarkFolders = bookmarkFolders,
            globalHistory = history,
            searchEngine = searchEngine,
            searchHistory = searchHistory,
            userscriptUiState = userscriptUiState,
            webViewHost = webViewHost,
            onHostStateChange = ::updateHostState,
            onNavigate = callbacks::onNavigate,
            onBack = { handleBack() },
            onForward = callbacks::onForward,
            onRefresh = callbacks::onRefresh,
            onSelectTab = callbacks::onSelectTab,
            onCloseTab = callbacks::onCloseTab,
            onNewTab = callbacks::onNewTab,
            onRequestTabThumbnails = callbacks::onRequestTabThumbnails,
            onTopBarBack = onTopBarBack,
            onOpenAiDialogue = onOpenAiDialogue,
            onOpenSettingsHome = onOpenSettingsHome,
            onOpenDownloadSettings = onOpenDownloadSettings,
            onExitBrowser = onExitBrowser,
            onCloseCurrentTab = callbacks::onCloseCurrentTab,
            onCloseAllTabs = callbacks::onCloseAllTabs,
            onRemoveBookmark = callbacks::onRemoveBookmark,
            onBookmarkMutation = callbacks::onBookmarkMutation,
            onOpenBookmarkInTab = callbacks::onOpenBookmarkInTab,
            onOpenUrl = callbacks::onOpenUrl,
            onOpenExternalUrl = callbacks::onOpenExternalUrl,
            onOpenHistoryEntry = callbacks::onOpenHistoryEntry,
            onDeleteHistory = callbacks::onDeleteHistory,
            onDeleteHistoryEntries = callbacks::onDeleteHistoryEntries,
             onClearNetworkLog = callbacks::onClearNetworkLog,
             onClearDiagnosticLog = callbacks::onClearDiagnosticLog,
             onAddNetworkBlockRule = callbacks::onAddNetworkBlockRule,
             onSelectUserAgentMode = callbacks::onSelectUserAgentMode,
            onSaveCustomGlobalUserAgent = callbacks::onSaveCustomGlobalUserAgent,
            onSaveSiteUserAgentRule = callbacks::onSaveSiteUserAgentRule,
            onSetSiteFeatureDisabled = callbacks::onSetSiteFeatureDisabled,
            onClearSiteSettings = callbacks::onClearSiteSettings,
            onSetSiteAdBlockingDisabled = callbacks::onSetSiteAdBlockingDisabled,
            onSetSearchEngine = callbacks::onSetSearchEngine,
            onSetDefaultSessionProfile = callbacks::onSetDefaultSessionProfile,
            onSubmitSearch = callbacks::onSubmitSearch,
            onOpenSearchRecord = callbacks::onOpenSearchRecord,
            onDeleteSearchHistory = callbacks::onDeleteSearchHistory,
            onClearSearchHistory = callbacks::onClearSearchHistory,
            onCopyCurrentUrl = callbacks::onCopyCurrentUrl,
            onOpenPageSource = ::beginPageSourceRead,
            onUpdatePageSourceBuffer = ::updatePageSourceBuffer,
            onReloadPageSource = { beginPageSourceRead(forceReload = true) },
            onApplyPageSource = ::applyPageSource,
            onCopyPageSource = ::copyPageSourceToClipboard,
            onKeepPageSourceDraftAndClose = ::keepPageSourceDraftAndClose,
            onDiscardPageSourceDraftAndClose = ::discardPageSourceDraftAndClose,
            onDismissPageSourceExitPrompt = ::dismissPageSourceExitPrompt,
            onOpenPlugins = callbacks::onOpenPlugins,
            onRefreshCookies = callbacks::onRefreshCookies,
            onSetCookieReaderEnabled = callbacks::onSetCookieReaderEnabled,
            onImportUserscript = callbacks::onImportUserscript,
            onInstallUserscriptFromUrl = callbacks::onInstallUserscriptFromUrl,
            onConfirmUserscriptInstall = callbacks::onConfirmUserscriptInstall,
            onCancelUserscriptInstall = callbacks::onCancelUserscriptInstall,
            onSetUserScriptsAllowed = callbacks::onSetUserScriptsAllowed,
            onSetUserscriptEnabled = callbacks::onSetUserscriptEnabled,
            onDeleteUserscript = callbacks::onDeleteUserscript,
            onCheckUserscriptUpdate = callbacks::onCheckUserscriptUpdate,
            onCheckAllUserscriptUpdates = callbacks::onCheckAllUserscriptUpdates,
            onApplyUserscriptUpdate = callbacks::onApplyUserscriptUpdate,
            onApplyAllSafeUserscriptUpdates = callbacks::onApplyAllSafeUserscriptUpdates,
            onSetUserscriptsEnabled = callbacks::onSetUserscriptsEnabled,
            onDeleteUserscripts = callbacks::onDeleteUserscripts,
            onLoadUserscriptDetail = callbacks::onLoadUserscriptDetail,
            onOpenNewUserscriptEditor = callbacks::onOpenNewUserscriptEditor,
            onOpenExistingUserscriptEditor = callbacks::onOpenExistingUserscriptEditor,
            onOpenUserscriptDraftEditor = callbacks::onOpenUserscriptDraftEditor,
            onUpdateUserscriptEditorBuffer = callbacks::onUpdateUserscriptEditorBuffer,
            onPersistUserscriptDraft = callbacks::onPersistUserscriptDraft,
            onDiscardUserscriptDraft = callbacks::onDiscardUserscriptDraft,
            onValidateUserscriptDraft = callbacks::onValidateUserscriptDraft,
            onFormatUserscriptDraft = callbacks::onFormatUserscriptDraft,
            onApplyUserscriptDraft = callbacks::onApplyUserscriptDraft,
            onRequestPluginBack = ::requestPluginBack,
            onInvokeUserscriptMenu = callbacks::onInvokeUserscriptMenu,
            playerSession = playerSession,
            playerState = playerState,
            onPlayMediaCandidate = callbacks::onPlayMediaCandidate,
            onPlayMediaCandidateFloating = callbacks::onPlayMediaCandidateFloating,
            onDownloadMediaCandidate = { candidateId ->
                callbacks.onDownloadMediaCandidate(
                    candidateId,
                    null,
                    BrowserDownloadDestination.FollowSettings,
                )
            },
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
            onHandlePendingDialog = callbacks::onHandlePendingDialog,
            showMediaCandidateBadge = showMediaCandidateBadge,
            automaticFloatingPlaybackEnabled = automaticFloatingPlaybackEnabled,
            automaticFloatingMinimumDurationMillis =
                browserSettings.automaticFloatingMinimumDurationMillis,
            swipeHistoryNavigationEnabled = swipeHistoryNavigationEnabled,
            onCopyTextSelection = ::copyActiveWebViewSelection,
            onSelectAllTextSelection = ::selectAllActiveWebViewText,
            onDismissTextSelection = ::dismissTextSelectionActions,
             onStartAdMarking = ::startAdMarking,
             onStartAdMarkingFromCurrentElement = ::startAdMarkingFromCurrentElement,
             onMoveAdMarking = ::moveAdMarking,
             onSetAdMarkingPreview = ::setAdMarkingPreview,
             onSaveAdMarking = ::saveAdMarking,
             onResetAdMarking = ::resetAdMarking,
             onExitAdMarking = ::exitAdMarking,
             onOpenAdMarkingRuleEditor = ::openAdMarkingRuleEditor,
             onOpenAdMarkingHtmlEditor = ::beginAdMarkingHtmlEditor,
             onUpdateAdMarkingRuleDraft = ::updateAdMarkingRuleDraft,
             onPreviewAdMarkingRuleDraft = ::previewAdMarkingRuleDraft,
             onConfirmAdMarkingRuleEdit = ::confirmAdMarkingRuleEdit,
             onDismissAdMarkingOverlay = ::dismissAdMarkingOverlay,
             onOpenClearAdMarkingConfirmation = ::openClearAdMarkingConfirmation,
             onConfirmClearAdMarking = ::confirmClearAdMarking,
             onOpenAdMarkingNavigationPolicy = ::openAdMarkingNavigationPolicy,
             onSelectAdMarkingNavigationPolicy = ::selectAdMarkingNavigationPolicy,
             onCancelAdMarkingNavigationRequest = ::cancelAdMarkingNavigationRequest,
             onAllowAdMarkingNavigationRequest = ::allowAdMarkingNavigationRequest,
             onSelectElementText = ::selectElementText,
             onCopyWebElementText = ::copyCurrentWebElementText,
             onCopyWebElementUrl = ::copyWebElementUrl,
             onOpenWebElementImage = ::openCurrentWebElementImage,
             onOpenWebElementImageMode = ::openCurrentWebElementImageMode,
             onSaveWebElementImage = ::saveCurrentWebElementImage,
             onRecognizeWebElementQrCode = ::recognizeCurrentWebElementQrCode,
             onBlockCurrentWebElement = ::blockCurrentWebElement,
             onDismissImageViewer = ::dismissImageViewer,
             onSaveImageViewerItem = ::saveImageViewerItem,
             onDismissQrCode = ::dismissQrCode,
             onCopyQrCodeContent = ::copyQrCodeContent,
             onOpenQrCodeContent = ::openQrCodeContent,
            modifier = modifier,
        )
    }

    fun updateHostProjection(
        browserState: WebSessionBrowserState,
        downloadUiState: BrowserDownloadUiState,
        downloadPrompt: BrowserDownloadPromptState?,
        searchRecovery: BrowserSessionSearchRecovery?,
    ) {
        val activePageChanged =
            hostState.browserState.activeSessionId != browserState.activeSessionId ||
                hostState.browserState.currentUrl != browserState.currentUrl
        if (activePageChanged) {
            clearWebElementTransientState()
        }
        if (downloadPrompt != null) {
            applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.RESET)
        }
        hostState =
            hostState
                .hydrateProjectedSearchRecovery(
                    activeSessionId = browserState.activeSessionId,
                    currentPageUrl = browserState.currentUrl,
                    searchRecovery = searchRecovery,
                )
                .copy(
                browserState = browserState,
                downloadUiState = downloadUiState,
                downloadPrompt = downloadPrompt,
                cookieState =
                    if (activePageChanged) {
                        BrowserCookieUiState()
                    } else {
                        hostState.cookieState
                    },
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

        clearWebElementTransientState()
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
        currentHostLayoutSize().let { size ->
            webViewHost.setViewportSize(size?.width, size?.height)
        }
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

        clearWebElementTransientState()
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

    fun requestMediaCandidateDownload(
        candidateId: String,
        sourceSessionId: String?,
        destination: BrowserDownloadDestination,
    ): Boolean =
        callbacks.onDownloadMediaCandidate(candidateId, sourceSessionId, destination)

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
                currentHostLayoutSize().let { size ->
                    appHost.setViewportSize(size?.width, size?.height)
                }
                appHost.setActiveWebView(activeWebView)
                attachedPresentationTarget = BrowserPresentationTarget.APP_SHELL
                hideIndicator()
            }
            BrowserPresentationTarget.BACKGROUND_ANCHOR -> {
                val position = indicatorParams?.let { it.x to it.y } ?: (dp(16) to dp(16))
                currentHostLayoutSize().let { size ->
                    backgroundAnchor.setViewportSize(size?.width, size?.height)
                }
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
        // Top-bar Back and system Back both enter this single ordering so browser chrome,
        // transient UI, and WebView history cannot diverge.
        if (
            hostState.sheetRoute == WebSessionBrowserSheetRoute.PAGE_SOURCE &&
                hostState.pageSource.applySupported &&
                hostState.pageSource.hasChanges &&
                !hostState.pageSource.exitPromptVisible
        ) {
            updateHostState { current ->
                current.copy(
                    pageSource = current.pageSource.copy(exitPromptVisible = true),
                )
            }
            return true
        }
        val editorRoute = hostState.currentPluginRoute as? WebSessionBrowserPluginRoute.UserscriptEditor
        if (
            hostState.pluginEditorExitPromptDraftId == null &&
                editorRoute != null &&
                userscriptStore.state.value.editors[editorRoute.draftId]?.hasUnappliedChanges == true
        ) {
            updateHostState { current ->
                current.copy(pluginEditorExitPromptDraftId = editorRoute.draftId)
            }
            return true
        }
        return when (resolveWebSessionBrowserBackAction(hostState)) {
            WebSessionBrowserBackAction.DISMISS_IMAGE_VIEWER -> {
                dismissImageViewer()
                true
            }
            WebSessionBrowserBackAction.DISMISS_QR_CODE -> {
                dismissQrCode()
                true
            }
            WebSessionBrowserBackAction.DISMISS_AD_MARKING_NAVIGATION_REQUEST -> {
                cancelAdMarkingNavigationRequest()
                true
            }
            WebSessionBrowserBackAction.DISMISS_AD_MARKING_OVERLAY -> {
                dismissAdMarkingOverlay()
                true
            }
            WebSessionBrowserBackAction.EXIT_AD_MARKING -> {
                exitAdMarking()
                true
            }
            WebSessionBrowserBackAction.DISMISS_WEB_ELEMENT_ACTION -> {
                updateHostState { current -> current.copy(webElementAction = null) }
                true
            }
            WebSessionBrowserBackAction.DISMISS_TEXT_SELECTION -> {
                hideTextSelectionActionsOverlay()
                true
            }
            WebSessionBrowserBackAction.DISMISS_PENDING_DIALOG -> {
                callbacks.onHandlePendingDialog(false, null)
                true
            }
            WebSessionBrowserBackAction.CANCEL_DOWNLOAD_PROMPT -> {
                callbacks.onCancelBrowserDownload(requireNotNull(hostState.downloadPrompt).requestId)
                true
            }
            WebSessionBrowserBackAction.DISMISS_PAGE_SOURCE_EXIT_PROMPT -> {
                dismissPageSourceExitPrompt()
                true
            }
            WebSessionBrowserBackAction.DISMISS_PLUGIN_EDITOR_EXIT_PROMPT -> {
                updateHostState { current ->
                    current.copy(pluginEditorExitPromptDraftId = null)
                }
                true
            }
            WebSessionBrowserBackAction.POP_PLUGIN_ROUTE -> {
                updateHostState { current ->
                    current.copy(
                        pluginRouteStack =
                            popBrowserPluginRoute(current.pluginRouteStack),
                    )
                }
                true
            }
            WebSessionBrowserBackAction.CLOSE_SHEET -> {
                updateHostState { current ->
                    current.copy(
                        sheetRoute = WebSessionBrowserSheetRoute.NONE,
                        pluginRouteStack =
                            listOf(WebSessionBrowserPluginRoute.Overview),
                        placeholderPage = null,
                        siteConfigDomain = null,
                    )
                }
                true
            }
            WebSessionBrowserBackAction.CLOSE_SEARCH_ENGINE_PANEL -> {
                updateHostState { current -> current.copy(isSearchEnginePanelVisible = false) }
                true
            }
            WebSessionBrowserBackAction.CLOSE_SEARCH -> {
                updateHostState { current ->
                    current.copy(
                        isSearchVisible = false,
                        isSearchEnginePanelVisible = false,
                        searchDraft = "",
                    )
                }
                true
            }
            WebSessionBrowserBackAction.CLOSE_NATIVE_HOME -> {
                updateHostState { current ->
                    current.copy(
                        isNativeHomeVisible = false,
                        nativeHomeCanReturnToPage = false,
                    )
                }
                true
            }
            WebSessionBrowserBackAction.SHOW_NATIVE_HOME -> {
                showNativeHome(canReturnToPage = false)
                true
            }
            WebSessionBrowserBackAction.NAVIGATE_WEB_HISTORY -> {
                callbacks.onBack()
                true
            }
            WebSessionBrowserBackAction.RETURN_TO_HOME -> {
                callbacks.onBack()
                true
            }
            WebSessionBrowserBackAction.EXIT_BROWSER -> false
        }
    }

    fun showTextSelectionActionsOverlay(
        anchorX: Double,
        anchorY: Double,
        viewportWidth: Double,
        viewportHeight: Double
    ) {
        if (!appPresentationActive) return
        val webView = activeWebView ?: return
        require(viewportWidth > 0.0 && viewportHeight > 0.0) {
            "Text selection viewport must have positive dimensions"
        }
        hostState =
            hostState.copy(
                textSelectionActions =
                    WebSessionTextSelectionActionsState(
                        anchorXPx = (anchorX * webView.width / viewportWidth).roundToInt(),
                        anchorYPx = (anchorY * webView.height / viewportHeight).roundToInt(),
                    ),
            )
    }

    fun performTextSelectionHaptic() {
        activeWebView?.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    internal fun showNativeHome(canReturnToPage: Boolean) {
        updateHostState { current ->
            if (
                current.isNativeHomeVisible &&
                    current.nativeHomeCanReturnToPage == canReturnToPage
            ) {
                current
            } else {
                current.copy(
                    isNativeHomeVisible = true,
                    nativeHomeCanReturnToPage = canReturnToPage,
                )
            }
        }
    }

    fun hideTextSelectionActionsOverlay() {
        if (hostState.textSelectionActions != null) {
            hostState = hostState.copy(textSelectionActions = null)
        }
    }

    fun showWebElementActions(
        sessionId: String,
        payload: String,
    ) {
        if (
            hostState.adMarking.active ||
                !isWebElementLongPressMenuEnabled(hostState.browserState.currentUrl)
        ) {
            return
        }
        if (hostState.browserState.activeSessionId != sessionId) {
            AppLogger.w(
                "WebSessionBrowserHost",
                "Ignoring stale web-element action for session=$sessionId",
            )
            return
        }
        val action = parseWebElementActionPayload(sessionId, payload) ?: return
        hideTextSelectionActionsOverlay()
        updateHostState { current ->
            current.copy(webElementAction = action)
        }
    }

    fun applyWebElementLongPressMenuSetting(enabled: Boolean) {
        if (!enabled && hostState.webElementAction != null) {
            updateHostState { current -> current.copy(webElementAction = null) }
        }
        activeWebView?.evaluateJavascript(
            """
            (function() {
                if (
                    window.__kiyoriElementActions &&
                    typeof window.__kiyoriElementActions.setElementActionsEnabled === "function"
                ) {
                    window.__kiyoriElementActions.setElementActionsEnabled($enabled);
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun showWebElementImageViewer(
        sessionId: String,
        payload: String,
    ) {
        if (
            hostState.browserState.activeSessionId != sessionId ||
                !isWebElementLongPressMenuEnabled(hostState.browserState.currentUrl)
        ) {
            return
        }
        val snapshot =
            try {
                val json = JSONObject(payload)
                val pageUrl = json.getString("pageUrl").trim()
                val selectedUrl = json.getString("selectedUrl").trim()
                val currentPageIdentity =
                    normalizeBrowserResourceIdentityUrl(hostState.browserState.currentUrl)
                require(
                    currentPageIdentity.isNotBlank() &&
                        currentPageIdentity == normalizeBrowserResourceIdentityUrl(pageUrl),
                ) {
                    "Web-element image viewer page is no longer active"
                }
                val encodedImages = json.getJSONArray("images")
                val imageUrls =
                    buildList {
                        for (index in 0 until encodedImages.length()) {
                            add(encodedImages.getString(index))
                        }
                    }
                buildBrowserWebElementImageViewerSnapshot(
                    urls = imageUrls,
                    selectedUrl = selectedUrl,
                    requestHeadersFor = { url ->
                        callbacks.onBuildImageRequestHeaders(url, pageUrl)
                    },
                )
            } catch (error: Exception) {
                AppLogger.e(
                    "WebSessionBrowserHost",
                    "Failed to build web-element image viewer snapshot",
                    error,
                )
                null
            }
        if (snapshot == null) {
            Toast.makeText(appContext, "当前页面没有可查看的图片", Toast.LENGTH_SHORT).show()
            return
        }
        updateHostState {
            it.copy(
                webElementAction = null,
                imageViewer = snapshot,
                qrCode = null,
            )
        }
    }

    fun openCurrentWebElementImage() {
        val action = hostState.webElementAction ?: return
        val imageUrl = action.imageUrl() ?: return
        val snapshot =
            buildSingleBrowserImageViewerSnapshot(
                url = imageUrl,
                requestHeaders =
                    callbacks.onBuildImageRequestHeaders(imageUrl, action.pageUrl),
            ) ?: return
        updateHostState {
            it.copy(
                webElementAction = null,
                imageViewer = snapshot,
                qrCode = null,
            )
        }
    }

    fun openCurrentWebElementImageMode() {
        val action = hostState.webElementAction ?: return
        val imageUrl = action.imageUrl() ?: return
        val webView = activeWebView ?: return
        updateHostState { it.copy(webElementAction = null) }
        webView.evaluateJavascript(
            """
            (function() {
                if (
                    window.__kiyoriElementActions &&
                    typeof window.__kiyoriElementActions.openImageMode === "function"
                ) {
                    return window.__kiyoriElementActions.openImageMode(
                        ${JSONObject.quote(imageUrl)}
                    );
                }
                return false;
            })();
            """.trimIndent(),
        ) { result ->
            if (result != "true") {
                Toast.makeText(
                    appContext,
                    "当前页面没有可查看的图片",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun saveCurrentWebElementImage() {
        val imageUrl = hostState.webElementAction?.imageUrl() ?: return
        updateHostState { it.copy(webElementAction = null) }
        saveImageUrl(imageUrl)
    }

    fun saveImageViewerItem(item: BrowserImageViewerItem) {
        dismissImageViewer()
        saveImageUrl(item.url)
    }

    fun dismissImageViewer() {
        if (hostState.imageViewer != null) {
            updateHostState { it.copy(imageViewer = null) }
        }
    }

    fun recognizeCurrentWebElementQrCode() {
        val action = hostState.webElementAction ?: return
        val imageUrl = action.imageUrl() ?: return
        val requestHeaders = callbacks.onBuildImageRequestHeaders(imageUrl, action.pageUrl)
        updateHostState {
            it.copy(
                webElementAction = null,
                qrCode =
                    WebSessionQrCodeState(
                        sourceUrl = imageUrl,
                        status = BrowserQrCodeUiStatus.LOADING,
                    ),
            )
        }
        browserOperationScope.launch {
            val result =
                try {
                    imageQrCodeRecognizer.recognize(imageUrl, requestHeaders)
                } catch (error: Exception) {
                    AppLogger.e(
                        "WebSessionBrowserHost",
                        "Failed to recognize QR code from web image",
                        error,
                    )
                    BrowserQrCodeRecognitionResult.ImageLoadFailed
                }
            withContext(Dispatchers.Main) {
                val current = hostState.qrCode
                if (
                    current == null ||
                        current.sourceUrl != imageUrl ||
                        current.status != BrowserQrCodeUiStatus.LOADING
                ) {
                    return@withContext
                }
                updateHostState {
                    it.copy(
                        qrCode =
                            when (result) {
                                is BrowserQrCodeRecognitionResult.Success ->
                                    current.copy(
                                        status = BrowserQrCodeUiStatus.SUCCESS,
                                        content = result.content,
                                    )
                                BrowserQrCodeRecognitionResult.ImageLoadFailed ->
                                    current.copy(
                                        status = BrowserQrCodeUiStatus.IMAGE_LOAD_FAILED,
                                    )
                                BrowserQrCodeRecognitionResult.NotRecognized ->
                                    current.copy(
                                        status = BrowserQrCodeUiStatus.NOT_RECOGNIZED,
                                    )
                            },
                    )
                }
            }
        }
    }

    fun dismissQrCode() {
        if (hostState.qrCode != null) {
            updateHostState { it.copy(qrCode = null) }
        }
    }

    fun copyQrCodeContent() {
        val content =
            hostState.qrCode
                ?.takeIf { state -> state.status == BrowserQrCodeUiStatus.SUCCESS }
                ?.content
                .orEmpty()
        if (content.isBlank()) {
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("web_image_qr_code", content))
        Toast.makeText(appContext, "二维码内容已复制", Toast.LENGTH_SHORT).show()
    }

    fun openQrCodeContent() {
        val content =
            hostState.qrCode
                ?.takeIf { state -> state.status == BrowserQrCodeUiStatus.SUCCESS }
                ?.content
                ?.takeIf(::isHttpBrowserNetworkUrl)
                ?: return
        dismissQrCode()
        callbacks.onOpenUrl(content)
    }

    fun blockCurrentWebElement() {
        val action = hostState.webElementAction ?: return
        val domain = normalizeBrowserAdBlockDomain(action.pageUrl)
        if (domain.isBlank() || !isValidBrowserAdBlockSelector(action.selector)) {
            Toast.makeText(appContext, "当前元素无法生成有效拦截规则", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            callbacks.onAddElementBlockRule(domain, action.selector)
            updateHostState { it.copy(webElementAction = null) }
            Toast.makeText(appContext, "网页元素拦截规则已保存", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            AppLogger.e(
                "WebSessionBrowserHost",
                "Failed to save quick web-element block rule",
                error,
            )
            Toast.makeText(appContext, "网页元素拦截规则保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageUrl(url: String) {
        val fileName = resolveManualBrowserDownloadFileName("", url, "")
        val engine = BrowserDownloadSettingsStore.getInstance(appContext).current.defaultEngine
        if (callbacks.onStartManualDownload(fileName, url, "", engine)) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.download_started, fileName),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openAdMarkingRuleEditor() {
        if (!hostState.adMarking.active) {
            return
        }
        updateHostState {
            it.copy(
                adMarkingOverlay = WebSessionAdMarkingOverlay.EDIT_RULE,
                adMarking = it.adMarking.copy(ruleDraft = it.adMarking.selector),
            )
        }
    }

    fun updateAdMarkingRuleDraft(value: String) {
        if (!hostState.adMarking.active ||
            hostState.adMarkingOverlay != WebSessionAdMarkingOverlay.EDIT_RULE
        ) {
            return
        }
        updateHostState { it.copy(adMarking = it.adMarking.copy(ruleDraft = value)) }
    }

    fun dismissAdMarkingOverlay() {
        if (hostState.adMarkingOverlay != WebSessionAdMarkingOverlay.NONE) {
            updateHostState { it.copy(adMarkingOverlay = WebSessionAdMarkingOverlay.NONE) }
        }
    }

    fun confirmAdMarkingRuleEdit() {
        if (hostState.adMarkingOverlay != WebSessionAdMarkingOverlay.EDIT_RULE) {
            return
        }
        val draft = hostState.adMarking.ruleDraft.trim()
        if (!isValidBrowserAdBlockSelector(draft)) {
            Toast.makeText(appContext, "请输入有效的元素拦截规则", Toast.LENGTH_SHORT).show()
            return
        }
        if (hostState.adMarking.previewing) {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    if (window.__kiyoriElementActions) {
                        window.__kiyoriElementActions.clearPreview();
                    }
                })();
                """.trimIndent(),
                null,
            )
        }
        updateHostState {
            it.copy(
                adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                adMarking =
                    it.adMarking.copy(
                        selector = draft,
                        ruleDraft = draft,
                        previewing = false,
                    ),
            )
        }
    }

    fun previewAdMarkingRuleDraft() {
        if (hostState.adMarkingOverlay != WebSessionAdMarkingOverlay.EDIT_RULE) {
            return
        }
        val draft = hostState.adMarking.ruleDraft.trim()
        if (!isValidBrowserAdBlockSelector(draft)) {
            Toast.makeText(appContext, "请输入有效的元素拦截规则", Toast.LENGTH_SHORT).show()
            return
        }
        val webView = activeWebView ?: return
        webView.evaluateJavascript(
            """
            (function() {
                if (!window.__kiyoriElementActions) {
                    return false;
                }
                return window.__kiyoriElementActions.previewSelector(${JSONObject.quote(draft)});
            })();
            """.trimIndent(),
        ) { result ->
            if (result == "true") {
                updateHostState {
                    it.copy(
                        adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                        adMarking =
                            it.adMarking.copy(
                                selector = draft,
                                ruleDraft = draft,
                                previewing = true,
                            ),
                    )
                }
            } else {
                Toast.makeText(appContext, "当前页面没有匹配此规则的元素", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openClearAdMarkingConfirmation() {
        if (hostState.adMarking.active) {
            updateHostState {
                it.copy(adMarkingOverlay = WebSessionAdMarkingOverlay.CLEAR_CONFIRM)
            }
        }
    }

    fun confirmClearAdMarking() {
        if (hostState.adMarkingOverlay != WebSessionAdMarkingOverlay.CLEAR_CONFIRM) {
            return
        }
        val state = hostState.adMarking
        if (state.domain.isBlank()) {
            Toast.makeText(appContext, "未识别当前网页域名", Toast.LENGTH_SHORT).show()
            dismissAdMarkingOverlay()
            return
        }
        try {
            val result = callbacks.onClearAdBlockRulesForDomain(state.domain)
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    if (window.__kiyoriElementActions) {
                        window.__kiyoriElementActions.clearPreview();
                    }
                })();
                """.trimIndent(),
                null,
            )
            updateHostState {
                it.copy(
                    adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                    adMarking =
                        it.adMarking.copy(
                            tagName = "",
                            text = "",
                            selector = "",
                            html = "",
                            previewing = false,
                            ruleDraft = "",
                        ),
                )
            }
            val message =
                if (result.removedRuleCount == 0) {
                    "当前域名没有可清除的自定义广告规则"
                } else {
                    "已清除当前域名的 ${result.removedRuleCount} 条自定义广告规则"
                }
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            AppLogger.e("WebSessionBrowserHost", "Failed to clear domain ad-block rules", error)
            Toast.makeText(appContext, "清除当前域名广告规则失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAdMarkingNavigationPolicy() {
        if (hostState.adMarking.active) {
            updateHostState {
                it.copy(adMarkingOverlay = WebSessionAdMarkingOverlay.NAVIGATION_POLICY)
            }
        }
    }

    fun selectAdMarkingNavigationPolicy(policy: BrowserAdMarkingNavigationPolicy) {
        if (!hostState.adMarking.active) {
            return
        }
        updateHostState {
            it.copy(
                adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                adMarking = it.adMarking.copy(navigationPolicy = policy),
                browserState = it.browserState.copy(externalNavigationPolicy = policy),
            )
        }
        callbacks.onSetExternalNavigationPolicy(policy)
        val javascriptPolicy = policy.toJavascriptValue()
        activeWebView?.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.setNavigationPolicy("$javascriptPolicy");
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun showAdMarkingNavigationRequest(
        sessionId: String,
        payload: String,
    ) {
        if (hostState.browserState.activeSessionId != sessionId) {
            return
        }
        val effectivePolicy =
            if (hostState.adMarking.active && hostState.adMarking.sessionId == sessionId) {
                hostState.adMarking.navigationPolicy
            } else {
                hostState.browserState.externalNavigationPolicy
            }
        if (effectivePolicy != BrowserAdMarkingNavigationPolicy.ASK) {
            return
        }
        val json =
            runCatching { JSONObject(payload) }.getOrElse { error ->
                AppLogger.e("WebSessionBrowserHost", "Failed to parse ad-marking navigation request", error)
                return
            }
        val url = json.optString("url").trim()
        if (url.isBlank()) {
            return
        }
        updateHostState {
            it.copy(
                adMarkingNavigationRequest =
                    WebSessionAdMarkingNavigationRequest(
                        url = url,
                        text = json.optString("text").trim(),
                    ),
            )
        }
    }

    fun cancelAdMarkingNavigationRequest() {
        if (hostState.adMarkingNavigationRequest != null) {
            updateHostState { it.copy(adMarkingNavigationRequest = null) }
        }
    }

    fun allowAdMarkingNavigationRequest() {
        val request = hostState.adMarkingNavigationRequest ?: return
        updateHostState { it.copy(adMarkingNavigationRequest = null) }
        if (hostState.adMarking.active) {
            exitAdMarking()
        }
        callbacks.onNavigate(request.url)
    }

    fun updateAdMarkingSelection(
        sessionId: String,
        payload: String,
    ) {
        val current = hostState.adMarking
        if (!current.active || current.sessionId != sessionId) {
            return
        }
        val action = parseWebElementActionPayload(sessionId, payload) ?: return
        updateHostState {
            it.copy(
                adMarking =
                    current.copy(
                        pageUrl = action.pageUrl,
                        domain = normalizeBrowserAdBlockDomain(action.pageUrl),
                        tagName = action.tagName,
                        text = action.text,
                        selector = action.selector,
                        html = action.html,
                        previewing = payloadPreviewing(payload),
                        ruleDraft = action.selector,
                    ),
            )
        }
    }

    fun startAdMarking() {
        val sessionId = hostState.browserState.activeSessionId
        val webView = activeWebView
        if (sessionId == null || webView == null) {
            AppLogger.w("WebSessionBrowserHost", "Cannot start ad marking without an active WebSession")
            return
        }
        val pageUrl = hostState.browserState.currentUrl.trim()
        if (pageUrl.isBlank()) {
            AppLogger.w("WebSessionBrowserHost", "Cannot start ad marking on a blank page URL")
            return
        }
        hideTextSelectionActionsOverlay()
        updateHostState {
            it.copy(
                sheetRoute = WebSessionBrowserSheetRoute.NONE,
                placeholderPage = null,
                webElementAction = null,
                adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                adMarkingNavigationRequest = null,
                adMarking =
                    WebSessionAdMarkingState(
                        active = true,
                        sessionId = sessionId,
                        pageUrl = pageUrl,
                        domain = normalizeBrowserAdBlockDomain(pageUrl),
                        navigationPolicy = hostState.browserState.externalNavigationPolicy,
                    ),
            )
        }
        callbacks.onSetAdMarkingActive(true)
        webView.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.startMarking(false);
                    window.__kiyoriElementActions.setNavigationPolicy(
                        "${hostState.browserState.externalNavigationPolicy.toJavascriptValue()}"
                    );
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun startAdMarkingFromCurrentElement() {
        val action = hostState.webElementAction ?: return
        val sessionId = hostState.browserState.activeSessionId
        val webView = activeWebView
        if (sessionId == null || sessionId != action.sessionId || webView == null) {
            AppLogger.w(
                "WebSessionBrowserHost",
                "Cannot start element marking from a stale web-element action",
            )
            return
        }
        hideTextSelectionActionsOverlay()
        updateHostState {
            it.copy(
                sheetRoute = WebSessionBrowserSheetRoute.NONE,
                placeholderPage = null,
                webElementAction = null,
                adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                adMarkingNavigationRequest = null,
                adMarking =
                    WebSessionAdMarkingState(
                        active = true,
                        sessionId = sessionId,
                        pageUrl = action.pageUrl,
                        domain = normalizeBrowserAdBlockDomain(action.pageUrl),
                        tagName = action.tagName,
                        text = action.text,
                        selector = action.selector,
                        html = action.html,
                        ruleDraft = action.selector,
                        navigationPolicy = hostState.browserState.externalNavigationPolicy,
                    ),
            )
        }
        callbacks.onSetAdMarkingActive(true)
        webView.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.startMarking(true);
                    window.__kiyoriElementActions.setNavigationPolicy(
                        "${hostState.browserState.externalNavigationPolicy.toJavascriptValue()}"
                    );
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun moveAdMarking(move: BrowserAdMarkingMove) {
        if (!hostState.adMarking.active || activeWebView == null) {
            return
        }
        val direction =
            when (move) {
                BrowserAdMarkingMove.PARENT -> "parent"
                BrowserAdMarkingMove.PREVIOUS_SIBLING -> "previous"
                BrowserAdMarkingMove.NEXT_SIBLING -> "next"
                BrowserAdMarkingMove.FIRST_CHILD -> "child"
            }
        activeWebView?.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.moveSelection("$direction");
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun setAdMarkingPreview(enabled: Boolean) {
        if (!hostState.adMarking.active || activeWebView == null) {
            return
        }
        val enabledLiteral = enabled.toString()
        activeWebView?.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.setPreview($enabledLiteral);
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun saveAdMarking() {
        val state = hostState.adMarking
        if (!state.active || state.sessionId == null) {
            return
        }
        if (state.domain.isBlank() || state.selector.isBlank()) {
            Toast.makeText(
                appContext,
                "请先选择一个可拦截的网页元素",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        try {
            callbacks.onAddElementBlockRule(state.domain, state.selector)
            Toast.makeText(
                appContext,
                "网页元素拦截规则已保存",
                Toast.LENGTH_SHORT,
            ).show()
        } catch (error: Exception) {
            AppLogger.e("WebSessionBrowserHost", "Failed to save web-element ad-block rule", error)
            Toast.makeText(
                appContext,
                "网页元素拦截规则保存失败",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun resetAdMarking() {
        if (!hostState.adMarking.active) {
            return
        }
        activeWebView?.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.startMarking(true);
                }
            })();
            """.trimIndent(),
            null,
        )
        updateHostState {
            it.copy(
                adMarking =
                    it.adMarking.copy(
                        tagName = "",
                        text = "",
                        selector = "",
                        html = "",
                        previewing = false,
                        ruleDraft = "",
                    ),
                adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                adMarkingNavigationRequest = null,
            )
        }
    }

    fun exitAdMarking() {
        callbacks.onSetAdMarkingActive(false)
        if (hostState.adMarking.active || hostState.webElementAction != null) {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    if (window.__kiyoriElementActions) {
                        window.__kiyoriElementActions.finish();
                    }
                })();
                """.trimIndent(),
                null,
            )
        }
        updateHostState {
            it.copy(
                webElementAction = null,
                adMarking = WebSessionAdMarkingState(),
                adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                adMarkingNavigationRequest = null,
            )
        }
    }

    fun selectElementText(
        clientX: Double,
        clientY: Double,
    ) {
        val webView = activeWebView ?: return
        if (!clientX.isFinite() || !clientY.isFinite()) {
            AppLogger.w("WebSessionBrowserHost", "Ignoring invalid element text-selection point")
            return
        }
        updateHostState { it.copy(webElementAction = null) }
        webView.evaluateJavascript(
            """
            (function() {
                if (window.__kiyoriElementActions) {
                    window.__kiyoriElementActions.selectText($clientX, $clientY);
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    fun copyCurrentWebElementText() {
        val text = hostState.webElementAction?.text.orEmpty()
        if (text.isBlank()) {
            Toast.makeText(appContext, "当前元素没有可复制文本", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("web_element_text", text))
        Toast.makeText(appContext, "文本已复制", Toast.LENGTH_SHORT).show()
        updateHostState { it.copy(webElementAction = null) }
    }

    fun copyWebElementUrl(url: String) {
        if (!isHttpBrowserNetworkUrl(url)) {
            Toast.makeText(appContext, "当前元素没有可复制链接", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("web_element_url", url))
        Toast.makeText(appContext, "链接已复制", Toast.LENGTH_SHORT).show()
        updateHostState { it.copy(webElementAction = null) }
    }

    private fun clearWebElementTransientState() {
        if (hostState.adMarking.active) {
            callbacks.onSetAdMarkingActive(false)
        }
        if (hostState.adMarking.active || hostState.webElementAction != null) {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    if (window.__kiyoriElementActions) {
                        window.__kiyoriElementActions.finish();
                    }
                })();
                """.trimIndent(),
                null,
            )
        }
        if (
            hostState.adMarking.active ||
                hostState.webElementAction != null ||
                hostState.imageViewer != null ||
                hostState.qrCode != null
        ) {
            hostState =
                hostState.copy(
                    webElementAction = null,
                    imageViewer = null,
                    qrCode = null,
                    adMarking = WebSessionAdMarkingState(),
                    adMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
                    adMarkingNavigationRequest = null,
                )
        }
    }

    private fun parseWebElementActionPayload(
        sessionId: String,
        payload: String,
    ): WebSessionWebElementActionState? {
        return try {
            val json = JSONObject(payload)
            val pageUrl = json.optString("pageUrl").trim()
            val tagName = json.optString("tagName").trim().lowercase()
            val selector = json.optString("selector").trim()
            require(pageUrl.isNotBlank()) { "Web-element payload pageUrl is blank" }
            require(tagName.isNotBlank()) { "Web-element payload tagName is blank" }
            require(selector.isNotBlank()) { "Web-element payload selector is blank" }
            WebSessionWebElementActionState(
                sessionId = sessionId,
                pageUrl = pageUrl,
                tagName = tagName,
                text = json.optString("text").trim(),
                linkUrl = json.optString("linkUrl").trim().takeIf(String::isNotBlank),
                resourceUrl = json.optString("resourceUrl").trim().takeIf(String::isNotBlank),
                resourceKind =
                    BrowserWebElementResourceKind.fromWireValue(
                        json.optString("resourceKind"),
                    ),
                selector = selector,
                html = json.optString("html").trim(),
                clientX = json.optDouble("clientX", 0.0),
                clientY = json.optDouble("clientY", 0.0),
            )
        } catch (error: Exception) {
            AppLogger.e(
                "WebSessionBrowserHost",
                "Failed to parse web-element payload for session=$sessionId",
                error,
            )
            null
        }
    }

    private fun payloadPreviewing(payload: String): Boolean =
        try {
            JSONObject(payload).optBoolean("previewing", false)
        } catch (error: Exception) {
            AppLogger.e("WebSessionBrowserHost", "Failed to read ad-marking preview state", error)
            false
        }

    fun setViewportSize(width: Int?, height: Int?) {
        require((width == null) == (height == null)) {
            "Viewport width and height must be set together"
        }
        updateHostState {
            it.copy(
                viewportWidthCssPx = width,
                viewportHeightCssPx = height,
            )
        }
        val hostLayoutSize = currentHostLayoutSize()
        appWebViewHost?.setViewportSize(hostLayoutSize?.width, hostLayoutSize?.height)
        backgroundAnchor.setViewportSize(hostLayoutSize?.width, hostLayoutSize?.height)
    }

    fun clearViewportSizeOverride() {
        setViewportSize(null, null)
    }

    fun currentViewportSize(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        val width = hostState.viewportWidthCssPx ?: (metrics.widthPixels / metrics.density).toInt()
        val height = hostState.viewportHeightCssPx ?: (metrics.heightPixels / metrics.density).toInt()
        return width to height
    }

    private fun currentHostLayoutSize(): BrowserViewportSize? {
        val width = hostState.viewportWidthCssPx ?: return null
        val height = hostState.viewportHeightCssPx ?: return null
        return BrowserViewportPolicy.hostLayoutSize(
            requested = BrowserViewportPolicy.requestedSize(width, height),
            density = appContext.resources.displayMetrics.density,
        )
    }

    fun currentBrowserAreaSize(): Pair<Int, Int> =
        hostState.browserAreaWidthPx.coerceAtLeast(0) to hostState.browserAreaHeightPx.coerceAtLeast(0)

    private fun isWebElementLongPressMenuEnabled(pageUrl: String): Boolean {
        val settings = browserSettingsStore.current
        return resolveWebSessionSiteFeatureEnabled(
            settings = settings,
            domainOrUrl = pageUrl,
            feature = WebSessionSiteFeature.WEB_ELEMENT_LONG_PRESS_MENU,
            globalEnabled = settings.webElementLongPressMenuEnabled,
        )
    }

    fun showSheet(route: WebSessionBrowserSheetRoute) {
        updateHostState { it.copy(sheetRoute = route) }
    }

    fun isBrowserWorkspaceVisible(): Boolean =
        hostState.sheetRoute == WebSessionBrowserSheetRoute.PLUGINS

    fun showPluginRoute(route: WebSessionBrowserPluginRoute) {
        updateHostState {
            it.copy(
                sheetRoute = WebSessionBrowserSheetRoute.PLUGINS,
                pluginRouteStack = browserPluginRouteStackFor(route),
                pluginEditorExitPromptDraftId = null,
            )
        }
    }

    private fun requestPluginBack() {
        val editorRoute = hostState.currentPluginRoute as? WebSessionBrowserPluginRoute.UserscriptEditor
        if (
            editorRoute != null &&
                userscriptStore.state.value.editors[editorRoute.draftId]?.hasUnappliedChanges == true
        ) {
            updateHostState { current ->
                current.copy(pluginEditorExitPromptDraftId = editorRoute.draftId)
            }
            return
        }
        updateHostState { current ->
            current.copy(
                pluginRouteStack = popBrowserPluginRoute(current.pluginRouteStack),
                pluginEditorExitPromptDraftId = null,
            )
        }
    }

    fun beginPageSourceRead(
        forceReload: Boolean = false,
    ) {
        val sessionId = hostState.browserState.activeSessionId
        val retained = hostState.pageSource
        if (
            !forceReload &&
                retained.hasChanges &&
                retained.sessionId == sessionId &&
                retained.content != null
        ) {
            updateHostState { current ->
                current.copy(
                    sheetRoute = WebSessionBrowserSheetRoute.PAGE_SOURCE,
                    pageSource =
                        current.pageSource.copy(
                            isRetainedEdit = true,
                            exitPromptVisible = false,
                            statusMessage =
                                appContext.getString(
                                    R.string.web_session_source_retained_status,
                                ),
                        ),
                )
            }
            return
        }

        val documentToken = UUID.randomUUID().toString()
        updateHostState {
            it.copy(
                sheetRoute = WebSessionBrowserSheetRoute.PAGE_SOURCE,
                pageSource =
                    WebSessionPageSourceState(
                        sessionId = sessionId,
                        pageUrl = it.browserState.currentUrl,
                        pageTitle = it.browserState.pageTitle,
                        documentToken = documentToken,
                        isLoading = true,
                    ),
            )
        }
        val webView = activeWebView
        if (webView == null || sessionId == null) {
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
            buildBrowserPageSourceCaptureScript(documentToken),
        ) { rawValue ->
            val result = parseBrowserPageSourceCaptureResult(rawValue)
            val capture = result.capture
            updateHostState { current ->
                if (
                    current.pageSource.sessionId != sessionId ||
                        current.pageSource.documentToken != documentToken
                ) {
                    return@updateHostState current
                }
                if (capture == null) {
                    return@updateHostState current.copy(
                        pageSource =
                            current.pageSource.copy(
                                isLoading = false,
                                error =
                                    pageSourceCaptureError(
                                        errorCode = result.errorCode,
                                        sourceLength = result.sourceLength,
                                    ),
                            ),
                    )
                }
                if (capture.documentToken != documentToken) {
                    return@updateHostState current.copy(
                        pageSource =
                            current.pageSource.copy(
                                isLoading = false,
                                error =
                                    appContext.getString(
                                        R.string.web_session_source_read_failed,
                                    ),
                            ),
                    )
                }
                current.copy(
                    pageSource =
                        current.pageSource.copy(
                            pageUrl = capture.pageUrl,
                            pageTitle = capture.pageTitle,
                            documentToken = capture.documentToken,
                            isLoading = false,
                            baselineContent = capture.source,
                            content = capture.source,
                            error = null,
                            statusMessage = null,
                            isRetainedEdit = false,
                        ),
                )
            }
        }
    }

    fun beginAdMarkingHtmlEditor() {
        val marking = hostState.adMarking
        if (!marking.active || marking.sessionId == null || marking.html.isBlank()) {
            return
        }
        updateHostState { current ->
            current.copy(
                sheetRoute = WebSessionBrowserSheetRoute.PAGE_SOURCE,
                pageSource =
                    WebSessionPageSourceState(
                        sessionId = marking.sessionId,
                        pageUrl = marking.pageUrl,
                        pageTitle = "HTML · <${marking.tagName.ifBlank { "node" }}>",
                        applySupported = false,
                        baselineContent = marking.html,
                        content = marking.html,
                        statusMessage = "当前编辑的是选中节点 HTML 副本，不会替换整个网页。",
                    ),
            )
        }
    }

    fun updatePageSourceBuffer(
        source: String,
    ) {
        updateHostState { current ->
            current.copy(
                pageSource =
                    current.pageSource.copy(
                        content = source,
                        error = null,
                        statusMessage = null,
                        isRetainedEdit = false,
                    ),
            )
        }
    }

    fun applyPageSource() {
        val state = hostState.pageSource
        if (!state.applySupported) {
            updatePageSourceError("选中节点 HTML 副本不能应用为整个网页。")
            return
        }
        val source = state.content
        val sessionId = state.sessionId
        val expectedToken = state.documentToken
        val webView = activeWebView
        val validation =
            if (source == null) {
                BrowserPageSourceValidationFailure.EMPTY
            } else {
                validateBrowserPageSource(source)
            }
        if (validation != null) {
            updatePageSourceError(pageSourceValidationError(validation))
            return
        }
        if (
            sessionId == null ||
                expectedToken == null ||
                hostState.browserState.activeSessionId != sessionId ||
                webView == null
        ) {
            updatePageSourceError(
                appContext.getString(R.string.web_session_source_session_changed),
            )
            return
        }

        val nextToken = UUID.randomUUID().toString()
        updateHostState { current ->
            current.copy(
                pageSource =
                    current.pageSource.copy(
                        isApplying = true,
                        error = null,
                        statusMessage = null,
                        exitPromptVisible = false,
                    ),
            )
        }
        webView.evaluateJavascript(
            buildBrowserPageSourceApplyScript(
                source = requireNotNull(source),
                expectedDocumentToken = expectedToken,
                nextDocumentToken = nextToken,
            ),
        ) { rawValue ->
            val result = parseBrowserPageSourceApplyResult(rawValue)
            updateHostState { current ->
                if (
                    current.pageSource.sessionId != sessionId ||
                        current.pageSource.documentToken != expectedToken
                ) {
                    return@updateHostState current
                }
                if (!result.applied) {
                    return@updateHostState current.copy(
                        pageSource =
                            current.pageSource.copy(
                                isApplying = false,
                                error = pageSourceApplyError(result.errorCode),
                            ),
                    )
                }
                current.copy(
                    pageSource =
                        current.pageSource.copy(
                            pageUrl = result.pageUrl,
                            pageTitle = result.pageTitle,
                            documentToken = requireNotNull(result.documentToken),
                            isApplying = false,
                            baselineContent = source,
                            content = source,
                            error = null,
                            statusMessage =
                                appContext.getString(R.string.web_session_source_applied),
                            isRetainedEdit = false,
                        ),
                )
            }
            if (result.applied) {
                callbacks.onPageSourceApplied(sessionId)
            }
        }
    }

    fun currentPageSourceEditorSnapshot(
        sessionId: String,
    ): BrowserPageSourceEditorSnapshot? {
        val state = hostState.pageSource
        val source = state.content ?: return null
        if (state.sessionId != sessionId) {
            return null
        }
        return BrowserPageSourceEditorSnapshot(
            sessionId = sessionId,
            pageUrl = state.pageUrl,
            pageTitle = state.pageTitle,
            source = source,
            hasChanges = state.hasChanges,
        )
    }

    private fun keepPageSourceDraftAndClose() {
        updateHostState { current ->
            current.copy(
                sheetRoute = WebSessionBrowserSheetRoute.NONE,
                pageSource =
                    current.pageSource.copy(
                        isRetainedEdit = true,
                        exitPromptVisible = false,
                        statusMessage =
                            appContext.getString(
                                R.string.web_session_source_retained_status,
                            ),
                    ),
            )
        }
    }

    private fun discardPageSourceDraftAndClose() {
        updateHostState { current ->
            current.copy(
                sheetRoute = WebSessionBrowserSheetRoute.NONE,
                pageSource = WebSessionPageSourceState(),
            )
        }
    }

    private fun dismissPageSourceExitPrompt() {
        updateHostState { current ->
            current.copy(
                pageSource = current.pageSource.copy(exitPromptVisible = false),
            )
        }
    }

    private fun updatePageSourceError(
        message: String,
    ) {
        updateHostState { current ->
            current.copy(
                pageSource =
                    current.pageSource.copy(
                        isApplying = false,
                        error = message,
                        statusMessage = null,
                    ),
            )
        }
    }

    private fun pageSourceCaptureError(
        errorCode: String?,
        sourceLength: Int?,
    ): String =
        when (errorCode) {
            "empty_document" -> appContext.getString(R.string.web_session_source_empty)
            "source_too_large" if sourceLength != null ->
                appContext.getString(
                    R.string.web_session_source_too_large,
                    sourceLength,
                    BROWSER_PAGE_SOURCE_MAX_CHARS,
                )
            else -> appContext.getString(R.string.web_session_source_read_failed)
        }

    private fun pageSourceValidationError(
        failure: BrowserPageSourceValidationFailure,
    ): String =
        when (failure) {
            BrowserPageSourceValidationFailure.EMPTY ->
                appContext.getString(R.string.web_session_source_empty)
            BrowserPageSourceValidationFailure.TOO_LARGE ->
                appContext.getString(
                    R.string.web_session_source_too_large,
                    hostState.pageSource.content?.length ?: 0,
                    BROWSER_PAGE_SOURCE_MAX_CHARS,
                )
            BrowserPageSourceValidationFailure.CONTAINS_NULL ->
                appContext.getString(R.string.web_session_source_invalid)
        }

    private fun pageSourceApplyError(
        errorCode: String?,
    ): String =
        when (errorCode) {
            "document_changed" ->
                appContext.getString(R.string.web_session_source_document_changed)
            "empty_source" -> appContext.getString(R.string.web_session_source_empty)
            "source_too_large" ->
                appContext.getString(
                    R.string.web_session_source_too_large,
                    hostState.pageSource.content?.length ?: 0,
                    BROWSER_PAGE_SOURCE_MAX_CHARS,
                )
            "invalid_source" -> appContext.getString(R.string.web_session_source_invalid)
            else -> appContext.getString(R.string.web_session_source_apply_failed)
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

    fun updateBrowserCookieState(state: BrowserCookieUiState) {
        updateHostState { current -> current.copy(cookieState = state) }
    }

    private fun updateHostState(transform: (WebSessionBrowserHostState) -> WebSessionBrowserHostState) {
        val updated = transform(hostState)
        if (updated == hostState) {
            return
        }
        val closedPluginWorkspace =
            hostState.sheetRoute == WebSessionBrowserSheetRoute.PLUGINS &&
                updated.sheetRoute != WebSessionBrowserSheetRoute.PLUGINS
        hostState = updated
        if (closedPluginWorkspace) {
            notifyBrowserWorkspaceClosed()
        }
    }

    private fun notifyBrowserWorkspaceClosed() {
        val tools = StandardBrowserSessionTools.getSharedInstance(appContext)
        val listener = tools.browserWorkspaceClosedListener
        tools.browserWorkspaceClosedListener = null
        listener?.invoke()
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
                            onOpenBrowser = ::openBrowserFromIndicator,
                            onDragBy = { dx, dy -> moveIndicatorBy(dx, dy) },
                            onLongPress = ::showIndicatorCloseAction,
                            onLongPressGestureFinished = ::finishIndicatorLongPressGesture,
                            onConfirmBrowserDownload = callbacks::onConfirmBrowserDownload,
                            onCancelBrowserDownload = callbacks::onCancelBrowserDownload,
                        )
                    }
                }
            }

        indicatorLifecycleOwner = lifecycleOwner
        indicatorView = indicator
        windowManager.addView(indicator, params)
    }

    private fun hideIndicator() {
        applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.RESET)
        hideIndicatorCloseActionOverlay()
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

    private fun openBrowserFromIndicator() {
        applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.RESET)
        callbacks.onRestoreAppShellBrowserFromIndicator()
    }

    private fun showIndicatorCloseAction() {
        applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.LONG_PRESS_RECOGNIZED)
        indicatorView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun finishIndicatorLongPressGesture() {
        applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.GESTURE_FINISHED)
    }

    private fun closeBrowserFromIndicator() {
        applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.RESET)
        callbacks.onExitBrowser()
    }

    private fun applyIndicatorCloseEvent(event: BrowserMinimizedIndicatorCloseEvent) {
        val previousState = indicatorCloseState
        val transition =
            BrowserMinimizedIndicatorClosePolicy.reduce(
                previousState,
                event,
            )
        if (transition.cancelPendingHide) {
            cancelIndicatorCloseHide()
        }

        indicatorCloseState = transition.state

        when (
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolveUpdate(
                previousState,
                indicatorCloseState,
            )
        ) {
            BrowserMinimizedIndicatorCloseOverlayUpdate.SHOW ->
                showIndicatorCloseActionOverlay()

            BrowserMinimizedIndicatorCloseOverlayUpdate.HIDE ->
                hideIndicatorCloseActionOverlay()

            BrowserMinimizedIndicatorCloseOverlayUpdate.UPDATE_TOUCHABILITY ->
                updateIndicatorCloseActionTouchability()

            BrowserMinimizedIndicatorCloseOverlayUpdate.NONE -> Unit
        }

        transition.scheduleHideAfterMillis?.let { delayMillis ->
            // 必须从长按手势真正结束后开始计时，否则用户持续按住时叉号会提前消失。
            val hideRunnable =
                Runnable {
                    indicatorCloseHideRunnable = null
                    applyIndicatorCloseEvent(BrowserMinimizedIndicatorCloseEvent.HIDE_TIMEOUT)
                }
            indicatorCloseHideRunnable = hideRunnable
            indicatorHandler.postDelayed(hideRunnable, delayMillis)
        }
    }

    private fun cancelIndicatorCloseHide() {
        indicatorCloseHideRunnable?.let(indicatorHandler::removeCallbacks)
        indicatorCloseHideRunnable = null
    }

    private fun showIndicatorCloseActionOverlay() {
        if (
            appPresentationActive ||
                indicatorView == null ||
                indicatorCloseActionView != null
        ) {
            return
        }
        val lifecycleOwner = indicatorLifecycleOwner ?: return
        val params = createIndicatorCloseActionLayoutParams()
        // 单独的 WindowManager 根允许叉号越出 40dp 球体边界，同时不扩大常驻透明触控区域。
        val closeAction =
            ComposeView(appContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                installViewTreeOwners(this, lifecycleOwner)
                setContent {
                    WebSessionFloatingTheme {
                        WebSessionMinimizedCloseAction(
                            contentDescription =
                                appContext.getString(R.string.web_session_exit_browser),
                            onCloseBrowser = ::closeBrowserFromIndicator,
                        )
                    }
                }
            }

        windowManager.addView(closeAction, params)
        indicatorCloseActionView = closeAction
        indicatorCloseActionParams = params
    }

    private fun hideIndicatorCloseActionOverlay() {
        val closeAction = indicatorCloseActionView ?: return
        try {
            windowManager.removeView(closeAction)
        } catch (error: Exception) {
            AppLogger.w(
                "WebSessionBrowserHost",
                "Failed to remove minimized browser close action",
                error,
            )
        }
        indicatorCloseActionView = null
        indicatorCloseActionParams = null
    }

    private fun updateIndicatorCloseActionTouchability() {
        val closeAction = indicatorCloseActionView ?: return
        val params = indicatorCloseActionParams ?: return
        val newFlags =
            BrowserMinimizedIndicatorCloseOverlayPolicy.windowFlags(
                indicatorCloseState.isLongPressGestureActive,
            )
        if (params.flags == newFlags) {
            return
        }
        params.flags = newFlags
        indicatorCloseActionParams = params
        if (closeAction.windowToken != null) {
            windowManager.updateViewLayout(closeAction, params)
        }
    }

    private fun updateIndicatorCloseActionPosition() {
        val closeAction = indicatorCloseActionView ?: return
        val params = indicatorCloseActionParams ?: return
        val position = resolveIndicatorCloseActionPosition()
        if (params.x == position.x && params.y == position.y) {
            return
        }
        params.x = position.x
        params.y = position.y
        indicatorCloseActionParams = params
        if (closeAction.windowToken != null) {
            windowManager.updateViewLayout(closeAction, params)
        }
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
        updateIndicatorCloseActionPosition()
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
            updateIndicatorCloseActionPosition()
            return
        }
        params.width = newWidth
        params.height = newHeight
        indicatorParams = params
        if (indicator.windowToken != null) {
            windowManager.updateViewLayout(indicator, params)
        }
        syncBackgroundAnchorWithIndicator()
        updateIndicatorCloseActionPosition()
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
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

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

    private fun createIndicatorCloseActionLayoutParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val closeActionSize = dp(BROWSER_MINIMIZED_INDICATOR_CLOSE_ACTION_SIZE_DP)
        val position = resolveIndicatorCloseActionPosition()
        return WindowManager.LayoutParams(
            closeActionSize,
            closeActionSize,
            type,
            BrowserMinimizedIndicatorCloseOverlayPolicy.windowFlags(
                indicatorCloseState.isLongPressGestureActive,
            ),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
    }

    private fun resolveIndicatorCloseActionPosition(): BrowserMinimizedIndicatorCloseOverlayPosition {
        val indicator = requireNotNull(indicatorParams)
        val displayMetrics = appContext.resources.displayMetrics
        return BrowserMinimizedIndicatorCloseOverlayPolicy.resolvePosition(
            indicatorX = indicator.x,
            indicatorY = indicator.y,
            indicatorWidth = indicator.width,
            closeActionSize = dp(BROWSER_MINIMIZED_INDICATOR_CLOSE_ACTION_SIZE_DP),
            overlap = dp(BROWSER_MINIMIZED_INDICATOR_CLOSE_ACTION_OVERLAP_DP),
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
        )
    }

    private fun indicatorWidthPx(): Int =
        if (hostState.downloadPrompt != null) {
            dp(248)
        } else {
            dp(40).coerceAtLeast(1)
        }

    private fun indicatorHeightPx(): Int =
        if (hostState.downloadPrompt != null) {
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
