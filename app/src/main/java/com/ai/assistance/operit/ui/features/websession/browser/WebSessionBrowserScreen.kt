package com.ai.assistance.operit.ui.features.websession.browser

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerPresentation
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.player.PlayerSessionState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadPromptState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadRenameMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdMarkingMove
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdMarkingNavigationPolicy
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.DEFAULT_BROWSER_HOME_URL
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkDraft
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkFolder
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmarkMutation
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHostState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionAdMarkingOverlay
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserNetworkEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPlaceholderPage
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPluginRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSheetRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserscriptWorkbenchTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.currentPluginRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.popBrowserPluginRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.pushBrowserPluginRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryCategory
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntryKey
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionPendingDialogState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchRecord
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserAgentMode
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserGestureNavigationFrameLayout
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.areBrowserHomeUrlsEquivalent
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.buildWebSessionBookmarkFolderTree
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.automaticFloatingCandidateStabilityDelayMillis
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveSelectedProfileAfterRemoval
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveWebSessionProfileToggleTarget
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.normalizeWebSessionBookmarkUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.selectAutomaticFloatingMediaCandidate
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomBar
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserMenuDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserTabOverview
import com.ai.assistance.operit.ui.features.websession.browser.chrome.resolveWebSessionBrowserChromeLayout
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserNetworkLog
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserPlaceholderSheet
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserTopBar
import com.kiyori.design.theme.KiyoriBrowserTheme
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal fun WebSessionBrowserSheetRoute.isWebSessionBrowserDrawerRoute(): Boolean =
    this != WebSessionBrowserSheetRoute.NONE &&
        this != WebSessionBrowserSheetRoute.TABS &&
        this != WebSessionBrowserSheetRoute.USER_AGENT &&
        this != WebSessionBrowserSheetRoute.PAGE_SOURCE

private fun WebSessionBrowserSheetRoute.isBrowserChildDrawerRoute(): Boolean =
    isWebSessionBrowserDrawerRoute() && this != WebSessionBrowserSheetRoute.MENU

internal fun shouldOpenConfiguredHomeAfterClearingWindows(homeUrl: String): Boolean =
    !areBrowserHomeUrlsEquivalent(homeUrl, DEFAULT_BROWSER_HOME_URL)

@Composable
internal fun WebSessionBrowserScreen(
    hostState: WebSessionBrowserHostState,
    bookmarks: List<WebSessionBookmark>,
    bookmarkFolders: List<WebSessionBookmarkFolder>,
    globalHistory: List<WebSessionHistoryEntry>,
    searchEngine: WebSessionSearchEngine,
    searchHistory: List<WebSessionSearchRecord>,
    userscriptUiState: WebSessionUserscriptUiState,
    webViewHost: WebSessionWebViewHost,
    onHostStateChange: ((WebSessionBrowserHostState) -> WebSessionBrowserHostState) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (WebSessionProfile) -> Unit,
    onRequestTabThumbnails: () -> Unit,
    onTopBarBack: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onOpenSettingsHome: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onExitBrowser: () -> Unit,
    onCloseCurrentTab: () -> Unit,
    onCloseAllTabs: (WebSessionProfile) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onBookmarkMutation: (WebSessionBookmarkMutation) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenHistoryEntry: (WebSessionHistoryEntry) -> Boolean,
    onDeleteHistory: (WebSessionHistoryCategory?, Long?) -> Unit,
    onDeleteHistoryEntries: (Set<WebSessionHistoryEntryKey>) -> Unit,
    onClearNetworkLog: () -> Unit,
    onAddNetworkBlockRule: (String) -> Unit,
    onSelectUserAgentMode: (WebSessionUserAgentMode) -> Unit,
    onSaveCustomGlobalUserAgent: (String) -> Unit,
    onSaveSiteUserAgentRule: (String, String) -> Unit,
    onSetSearchEngine: (WebSessionSearchEngine) -> Unit,
    onSetDefaultSessionProfile: (WebSessionProfile) -> Boolean,
    onSubmitSearch: (String, WebSessionSearchEngine, WebSessionProfile) -> Unit,
    onOpenSearchRecord: (WebSessionSearchRecord, WebSessionProfile) -> Unit,
    onDeleteSearchHistory: (Long) -> Unit,
    onClearSearchHistory: () -> Unit,
    onCopyCurrentUrl: () -> Unit,
    onOpenPageSource: () -> Unit,
    onUpdatePageSourceBuffer: (String) -> Unit,
    onReloadPageSource: () -> Unit,
    onApplyPageSource: () -> Unit,
    onCopyPageSource: () -> Unit,
    onKeepPageSourceDraftAndClose: () -> Unit,
    onDiscardPageSourceDraftAndClose: () -> Unit,
    onDismissPageSourceExitPrompt: () -> Unit,
    onOpenPlugins: () -> Unit,
    onImportUserscript: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onCheckAllUserscriptUpdates: () -> Unit,
    onApplyUserscriptUpdate: (Long) -> Unit,
    onApplyAllSafeUserscriptUpdates: () -> Unit,
    onSetUserscriptsEnabled: (Set<Long>, Boolean) -> Unit,
    onDeleteUserscripts: (Set<Long>) -> Unit,
    onLoadUserscriptDetail: (Long) -> Unit,
    onOpenNewUserscriptEditor: () -> Unit,
    onOpenExistingUserscriptEditor: (Long) -> Unit,
    onOpenUserscriptDraftEditor: (String) -> Unit,
    onUpdateUserscriptEditorBuffer: (String, String) -> Unit,
    onPersistUserscriptDraft: (String, (() -> Unit)?) -> Unit,
    onDiscardUserscriptDraft: (String, (() -> Unit)?) -> Unit,
    onValidateUserscriptDraft: (String) -> Unit,
    onFormatUserscriptDraft: (String) -> Unit,
    onApplyUserscriptDraft: (String) -> Unit,
    onRequestPluginBack: () -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    playerSession: PlayerSession,
    playerState: PlayerSessionState,
    onPlayMediaCandidate: (String) -> Boolean,
    onPlayMediaCandidateFloating: (String) -> Boolean,
    onDownloadMediaCandidate: (String) -> Boolean,
    onTogglePlayerPause: () -> Unit,
    onOpenPlayerFullscreen: () -> Unit,
    onLaunchPlayerFullscreen: () -> Unit,
    onClosePlayer: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadFileManager: () -> Unit,
    onStartManualDownload: (String, String, String, BrowserDownloadEngine) -> Boolean,
    onRedownload: (String) -> Unit,
    onRenameDownload: (String, String, BrowserDownloadRenameMode) -> Unit,
    onMoveDownload: (String, String) -> Unit,
    onCopyDownloadUrl: (String) -> Unit,
    onShareDownload: (String) -> Unit,
    onCopyDownloadLocation: (String) -> Unit,
    onTransferDownload: (String) -> Unit,
    onMergeDownloadToMp4: (String) -> Unit,
    onConfirmBrowserDownload: (String) -> Unit,
    onCancelBrowserDownload: (String) -> Unit,
    onHandlePendingDialog: (Boolean, String?) -> Unit,
    showMediaCandidateBadge: Boolean,
    automaticFloatingPlaybackEnabled: Boolean,
    automaticFloatingMinimumDurationMillis: Long,
    swipeHistoryNavigationEnabled: Boolean,
    onCopyTextSelection: () -> Unit,
    onSelectAllTextSelection: () -> Unit,
    onDismissTextSelection: () -> Unit,
    onStartAdMarking: () -> Unit,
    onStartAdMarkingFromCurrentElement: () -> Unit,
    onMoveAdMarking: (BrowserAdMarkingMove) -> Unit,
    onSetAdMarkingPreview: (Boolean) -> Unit,
    onSaveAdMarking: () -> Unit,
    onResetAdMarking: () -> Unit,
    onExitAdMarking: () -> Unit,
    onOpenAdMarkingRuleEditor: () -> Unit,
    onOpenAdMarkingHtmlEditor: () -> Unit,
    onUpdateAdMarkingRuleDraft: (String) -> Unit,
    onPreviewAdMarkingRuleDraft: () -> Unit,
    onConfirmAdMarkingRuleEdit: () -> Unit,
    onDismissAdMarkingOverlay: () -> Unit,
    onOpenClearAdMarkingConfirmation: () -> Unit,
    onConfirmClearAdMarking: () -> Unit,
    onOpenAdMarkingNavigationPolicy: () -> Unit,
    onSelectAdMarkingNavigationPolicy: (BrowserAdMarkingNavigationPolicy) -> Unit,
    onCancelAdMarkingNavigationRequest: () -> Unit,
    onAllowAdMarkingNavigationRequest: () -> Unit,
    onSelectElementText: (Double, Double) -> Unit,
    onCopyWebElementText: () -> Unit,
    onCopyWebElementUrl: () -> Unit,
    homeUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val browserState = hostState.browserState
    val automaticFloatingPageKey = "${browserState.activeSessionId.orEmpty()}|${browserState.currentUrl}"
    val automaticFloatingCandidate =
        remember(
            browserState.mediaCandidates,
            automaticFloatingMinimumDurationMillis,
        ) {
            selectAutomaticFloatingMediaCandidate(
                candidates = browserState.mediaCandidates,
                minimumDurationMillis = automaticFloatingMinimumDurationMillis,
            )
        }
    val latestMediaCandidates by rememberUpdatedState(browserState.mediaCandidates)
    var dismissedAutomaticFloatingPageKey by remember { mutableStateOf<String?>(null) }
    var totalHeightPx by remember { mutableIntStateOf(0) }
    var browserAreaHeightPx by remember { mutableIntStateOf(0) }
    var floatingOffsetYPx by remember(playerState.request?.requestId) { mutableFloatStateOf(0f) }
    LaunchedEffect(totalHeightPx, browserAreaHeightPx) {
        val chromeHeightPx = (totalHeightPx - browserAreaHeightPx).coerceAtLeast(0)
        if (
            chromeHeightPx != hostState.chromeHeightPx ||
                browserAreaHeightPx != hostState.browserAreaHeightPx
        ) {
            onHostStateChange { current ->
                if (
                    current.chromeHeightPx == chromeHeightPx &&
                        current.browserAreaHeightPx == browserAreaHeightPx
                ) {
                    current
                } else {
                    current.copy(
                        chromeHeightPx = chromeHeightPx,
                        browserAreaHeightPx = browserAreaHeightPx
                    )
                }
            }
        }
    }
    val currentBookmarkUrl =
        remember(browserState.currentUrl) { normalizeWebSessionBookmarkUrl(browserState.currentUrl) }
    val isBookmarked =
        remember(currentBookmarkUrl, bookmarks) {
            currentBookmarkUrl != null &&
                bookmarks.any { bookmark -> !bookmark.secret && bookmark.url == currentBookmarkUrl }
        }
    val bookmarkFolderOptions =
        remember(bookmarkFolders) {
            buildWebSessionBookmarkFolderTree(bookmarkFolders, secret = false).map { entry ->
                WebSessionBookmarkFolderOption(entry.id, entry.title, entry.depth)
            }
        }
    var addBookmarkDraft by remember { mutableStateOf<WebSessionBookmarkDraft?>(null) }
    val dismissSheet = {
        onHostStateChange { current ->
            current.copy(
                sheetRoute = WebSessionBrowserSheetRoute.NONE,
                pluginRouteStack = listOf(WebSessionBrowserPluginRoute.Overview),
                placeholderPage = null,
            )
        }
    }
    LaunchedEffect(
        automaticFloatingPageKey,
        automaticFloatingPlaybackEnabled,
        automaticFloatingMinimumDurationMillis,
        automaticFloatingCandidate?.id,
        playerState.request?.requestId,
        playerState.presentation,
    ) {
        if (!automaticFloatingPlaybackEnabled) return@LaunchedEffect
        if (dismissedAutomaticFloatingPageKey == automaticFloatingPageKey) return@LaunchedEffect
        if (playerState.hasMedia || playerState.presentation != PlayerPresentation.BROWSER_ONLY) return@LaunchedEffect
        val selected = automaticFloatingCandidate ?: return@LaunchedEffect
        delay(automaticFloatingCandidateStabilityDelayMillis(selected))
        if (dismissedAutomaticFloatingPageKey == automaticFloatingPageKey) return@LaunchedEffect
        val stableSelection =
            selectAutomaticFloatingMediaCandidate(
                candidates = latestMediaCandidates,
                minimumDurationMillis = automaticFloatingMinimumDurationMillis,
            )
        if (stableSelection?.id == selected.id) {
            onPlayMediaCandidateFloating(selected.id)
        }
    }
    val fullscreenLaunchRequestId = playerState.surfaceLease.fullscreenLaunchRequestId
    LaunchedEffect(fullscreenLaunchRequestId) {
        val requestId = fullscreenLaunchRequestId ?: return@LaunchedEffect
        // The floating Surface has already reported surfaceDestroyed and native detach has
        // completed before this one-shot request is emitted.
        onLaunchPlayerFullscreen()
        playerSession.acknowledgeFullscreenLaunchRequest(requestId)
    }
    val openPlaceholder: (WebSessionBrowserPlaceholderPage) -> Unit = { page ->
        onHostStateChange { current ->
            current.copy(
                sheetRoute = WebSessionBrowserSheetRoute.PLACEHOLDER,
                placeholderPage = page,
            )
        }
    }
    val activeSheetRoute = hostState.sheetRoute
    val pluginEditorRoute =
        hostState.currentPluginRoute as? WebSessionBrowserPluginRoute.UserscriptEditor
    var tabOverviewMounted by remember { mutableStateOf(false) }
    var mountedDrawerRoute by remember { mutableStateOf(WebSessionBrowserSheetRoute.NONE) }
    var profileFeedback by remember { mutableStateOf<String?>(null) }
    val incognitoEnabledMessage = stringResource(R.string.web_session_incognito_enabled)
    val incognitoDisabledMessage = stringResource(R.string.web_session_incognito_disabled)

    fun toggleDefaultProfile(currentProfile: WebSessionProfile) {
        val requestedProfile =
            resolveWebSessionProfileToggleTarget(
                currentProfile = currentProfile,
                incognitoAvailability = browserState.incognitoAvailability,
            ) ?: return
        if (!onSetDefaultSessionProfile(requestedProfile)) {
            return
        }
        onHostStateChange { current ->
            current.copy(searchProfile = requestedProfile)
        }
        profileFeedback =
            if (requestedProfile == WebSessionProfile.INCOGNITO) {
                incognitoEnabledMessage
            } else {
                incognitoDisabledMessage
            }
    }

    LaunchedEffect(activeSheetRoute) {
        when {
            activeSheetRoute == WebSessionBrowserSheetRoute.TABS -> {
                tabOverviewMounted = true
                onRequestTabThumbnails()
            }
            activeSheetRoute.isWebSessionBrowserDrawerRoute() -> mountedDrawerRoute = activeSheetRoute
        }
    }
    LaunchedEffect(profileFeedback) {
        if (profileFeedback != null) {
            delay(1_200)
            profileFeedback = null
        }
    }
    var promptDraft by remember(browserState.pendingDialog?.message, browserState.pendingDialog?.defaultValue) {
        mutableStateOf(browserState.pendingDialog?.defaultValue.orEmpty())
    }

    KiyoriBrowserTheme {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
        ) {
        val chromeLayout =
            resolveWebSessionBrowserChromeLayout(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
            )
        val floatingPlayerHeightPx = with(density) { maxWidth.toPx() * 9f / 16f }
        val floatingMinOffsetYPx =
            -(browserAreaHeightPx.toFloat() - floatingPlayerHeightPx).coerceAtLeast(0f)
        LaunchedEffect(floatingMinOffsetYPx, playerState.request?.requestId) {
            floatingOffsetYPx = floatingOffsetYPx.coerceIn(floatingMinOffsetYPx, 0f)
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { totalHeightPx = it.height }
            ) {
            WebSessionBrowserTopBar(
                currentUrl = browserState.currentUrl.ifBlank { "about:blank" },
                pageTitle = browserState.pageTitle,
                detectedVideoCount = browserState.mediaCandidates.size,
                showDetectedVideoBadge = showMediaCandidateBadge,
                searchEngine = searchEngine,
                lastSearchQuery = hostState.lastSearchQuery,
                isSearchEngineQuickSwitchBarVisible =
                    hostState.isSearchEngineQuickSwitchBarVisible,
                onBack = onTopBarBack,
                onOpenSearch = {
                    profileFeedback = null
                    onHostStateChange { current ->
                        current.copy(
                            isSearchVisible = true,
                            isSearchEnginePanelVisible = false,
                            searchDraft = "",
                            searchProfile =
                                browserState.activeProfile
                                    ?: browserState.defaultSessionProfile,
                        )
                    }
                },
                onShowDetectedVideos = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.MEDIA_CANDIDATES)
                    }
                },
                onRefresh = onRefresh,
                onSelectQuickSearchEngine = { engine ->
                    val query = hostState.lastSearchQuery.trim()
                    if (query.isNotBlank()) {
                        onSetSearchEngine(engine)
                        onSubmitSearch(
                            query,
                            engine,
                            browserState.activeProfile ?: browserState.defaultSessionProfile,
                        )
                    }
                },
                onDismissQuickSearchEngineBar = {
                    onHostStateChange { current ->
                        current.copy(isSearchEngineQuickSwitchBarVisible = false)
                    }
                },
                modifier = Modifier
            )

            if (browserState.activeDownloadCount > 0) {
                BrowserDownloadSummaryBar(
                    activeCount = browserState.activeDownloadCount,
                    overallProgress = browserState.overallDownloadProgress,
                    onClick = {
                        onHostStateChange { current ->
                            current.copy(sheetRoute = WebSessionBrowserSheetRoute.DOWNLOADS)
                        }
                    }
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged {
                            browserAreaHeightPx = it.height
                            if (it.width != hostState.browserAreaWidthPx) {
                                onHostStateChange { current ->
                                    if (current.browserAreaWidthPx == it.width) {
                                        current
                                    } else {
                                        current.copy(browserAreaWidthPx = it.width)
                                    }
                                }
                            }
                        }
                        .background(MaterialTheme.colorScheme.background)
            ) {
                if (browserState.activeSessionId == null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.web_session_no_tabs),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.web_session_new_tab),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    val browserHostBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        val isGestureNavigationBlocked =
                            hostState.sheetRoute != WebSessionBrowserSheetRoute.NONE ||
                                hostState.isSearchVisible ||
                                hostState.isSearchEnginePanelVisible ||
                                hostState.pageSource.exitPromptVisible ||
                                hostState.textSelectionActions != null ||
                                hostState.webElementAction != null ||
                                hostState.adMarking.active ||
                                hostState.adMarkingOverlay != WebSessionAdMarkingOverlay.NONE ||
                                hostState.adMarkingNavigationRequest != null ||
                                hostState.downloadPrompt != null ||
                                browserState.pendingDialog != null
                        AndroidView(
                            factory = { context ->
                                BrowserGestureNavigationFrameLayout(context).apply {
                                    setBackgroundColor(browserHostBackgroundColor)
                                    gestureNavigationEnabled =
                                        swipeHistoryNavigationEnabled
                                    gestureNavigationBlocked =
                                        isGestureNavigationBlocked
                                    canNavigateBack =
                                        browserState.canGoBack ||
                                            browserState.canReturnToHome
                                    canNavigateForward = browserState.canGoForward
                                    onNavigateBack = onBack
                                    onNavigateForward = onForward
                                    webViewHost.attachContainer(this)
                                }
                            },
                            update = { container ->
                                container.setBackgroundColor(browserHostBackgroundColor)
                                container.gestureNavigationEnabled =
                                    swipeHistoryNavigationEnabled
                                container.gestureNavigationBlocked =
                                    isGestureNavigationBlocked
                                container.canNavigateBack =
                                    browserState.canGoBack ||
                                        browserState.canReturnToHome
                                container.canNavigateForward = browserState.canGoForward
                                container.onNavigateBack = onBack
                                container.onNavigateForward = onForward
                                webViewHost.attachContainer(container)
                            },
                            onRelease = { container ->
                                webViewHost.detachContainer(container)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (
                    playerState.presentation == PlayerPresentation.FLOATING_PLAYER &&
                        playerState.request?.source == PlayerMediaSource.BROWSER_CANDIDATE
                ) {
                    WebSessionFloatingPlayer(
                        session = playerSession,
                        state = playerState,
                        onTogglePause = onTogglePlayerPause,
                        onFullscreen = onOpenPlayerFullscreen,
                        onDownload = onDownloadMediaCandidate,
                        onClose = {
                            dismissedAutomaticFloatingPageKey = automaticFloatingPageKey
                            onClosePlayer()
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = floatingOffsetYPx.roundToInt(),
                                    )
                                }
                                .pointerInput(
                                    playerState.request.requestId,
                                    floatingMinOffsetYPx,
                                ) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        floatingOffsetYPx =
                                            (floatingOffsetYPx + dragAmount)
                                                .coerceIn(floatingMinOffsetYPx, 0f)
                                    }
                                }
                                .fillMaxWidth(),
                    )
                }
                hostState.textSelectionActions?.let { selection ->
                    val actionWidthPx = with(density) { 220.dp.roundToPx() }
                    val actionHeightPx = with(density) { 46.dp.roundToPx() }
                    val marginPx = with(density) { 10.dp.roundToPx() }
                    val maximumX =
                        (hostState.browserAreaWidthPx - actionWidthPx - marginPx)
                            .coerceAtLeast(marginPx)
                    val maximumY =
                        (hostState.browserAreaHeightPx - actionHeightPx - marginPx)
                            .coerceAtLeast(marginPx)
                    val targetX =
                        (selection.anchorXPx - actionWidthPx / 2)
                            .coerceIn(marginPx, maximumX)
                    val targetY =
                        (selection.anchorYPx - actionHeightPx - marginPx)
                            .takeIf { it >= marginPx }
                            ?: (selection.anchorYPx + marginPx).coerceIn(marginPx, maximumY)

                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .offset { IntOffset(targetX, targetY) }
                                .widthIn(max = 280.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Row {
                            TextButton(onClick = onCopyTextSelection) {
                                Text(
                                    text = stringResource(R.string.copy),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }
                            TextButton(onClick = onSelectAllTextSelection) {
                                Text(
                                    text = stringResource(android.R.string.selectAll),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }
                            TextButton(onClick = onDismissTextSelection) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }
                        }
                    }
                }
            }

            if (hostState.adMarking.active) {
                WebSessionAdMarkingWorkbench(
                    state = hostState.adMarking,
                    onMove = onMoveAdMarking,
                    onSetPreview = onSetAdMarkingPreview,
                    onSave = onSaveAdMarking,
                    onReset = onResetAdMarking,
                    onExit = onExitAdMarking,
                    onEditRule = onOpenAdMarkingRuleEditor,
                    onOpenHtmlEditor = onOpenAdMarkingHtmlEditor,
                    onClearIntercept = onOpenClearAdMarkingConfirmation,
                    onOpenNavigationPolicy = onOpenAdMarkingNavigationPolicy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                WebSessionBrowserBottomBar(
                    canNavigateBack =
                        browserState.canGoBack || browserState.canReturnToHome,
                    canGoForward = browserState.canGoForward,
                    tabCount = browserState.tabs.size,
                    onBack = onBack,
                    onForward = onForward,
                    onHome = { onNavigate(homeUrl) },
                    onTabs = {
                        onHostStateChange { current ->
                            current.copy(
                                sheetRoute = WebSessionBrowserSheetRoute.TABS,
                                selectedProfile =
                                    browserState.activeProfile
                                        ?: browserState.defaultSessionProfile,
                            )
                        }
                    },
                    onToolbox = {
                        onHostStateChange { current ->
                            current.copy(sheetRoute = WebSessionBrowserSheetRoute.MENU)
                        }
                    },
                )
            }
            }
        }

        if (hostState.isSearchVisible) {
            WebSessionBrowserSearchScreen(
                currentUrl = browserState.currentUrl,
                currentTitle = browserState.pageTitle,
                searchEngine = searchEngine,
                searchHistory = searchHistory,
                draft = hostState.searchDraft,
                isEnginePanelVisible = hostState.isSearchEnginePanelVisible,
                onDraftChange = { draft ->
                    onHostStateChange { current -> current.copy(searchDraft = draft) }
                },
                onBack = {
                    profileFeedback = null
                    onHostStateChange {
                        it.copy(isSearchVisible = false, isSearchEnginePanelVisible = false, searchDraft = "")
                    }
                },
                systemBackEnabled = LocalWebSessionBrowserSystemBackEnabled.current,
                onEnginePanelVisibleChange = { visible ->
                    onHostStateChange { current -> current.copy(isSearchEnginePanelVisible = visible) }
                },
                onSubmit = {
                    val query = hostState.searchDraft.trim()
                    if (query.isNotBlank()) {
                        val isTextSearch = BrowserAddressResolver.isSearchQuery(query)
                        onSubmitSearch(query, searchEngine, hostState.searchProfile)
                        profileFeedback = null
                        onHostStateChange { current ->
                            current.copy(
                                isSearchVisible = false,
                                isSearchEnginePanelVisible = false,
                                searchDraft = "",
                                lastSearchQuery = if (isTextSearch) query else "",
                                isSearchEngineQuickSwitchBarVisible = isTextSearch,
                            )
                        }
                    }
                },
                onSelectEngine = onSetSearchEngine,
                onOpenSearchRecord = { record ->
                    onOpenSearchRecord(record, hostState.searchProfile)
                    profileFeedback = null
                    val isTextSearch = BrowserAddressResolver.isSearchQuery(record.query)
                    onHostStateChange { current ->
                        current.copy(
                            isSearchVisible = false,
                            isSearchEnginePanelVisible = false,
                            searchDraft = "",
                            lastSearchQuery = if (isTextSearch) record.query else "",
                            isSearchEngineQuickSwitchBarVisible = isTextSearch,
                        )
                    }
                },
                onDeleteSearchRecord = onDeleteSearchHistory,
                onClearSearchHistory = onClearSearchHistory,
                onCopyCurrentUrl = onCopyCurrentUrl,
                onOpenCurrentUrl = {
                    profileFeedback = null
                    onHostStateChange { current ->
                        current.copy(
                            isSearchVisible = false,
                            isSearchEnginePanelVisible = false,
                            searchDraft = "",
                        )
                    }
                },
                onEditCurrentUrl = {
                    onHostStateChange { current -> current.copy(searchDraft = browserState.currentUrl) }
                },
                selectedProfile = hostState.searchProfile,
                incognitoAvailability = browserState.incognitoAvailability,
                onToggleProfile = { toggleDefaultProfile(hostState.searchProfile) },
                profileFeedback = profileFeedback,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (tabOverviewMounted) {
            WebSessionBrowserTabOverview(
                isVisible = activeSheetRoute == WebSessionBrowserSheetRoute.TABS,
                tabs = browserState.tabs,
                selectedProfile = hostState.selectedProfile,
                incognitoAvailability = browserState.incognitoAvailability,
                columnCount = chromeLayout.tabColumnCount,
                onDismissRequest = dismissSheet,
                onHidden = {
                    if (activeSheetRoute != WebSessionBrowserSheetRoute.TABS) {
                        tabOverviewMounted = false
                    }
                },
                onSelectTab = { sessionId ->
                    onSelectTab(sessionId)
                    dismissSheet()
                },
                onCloseTab = { sessionId ->
                    val remainingProfiles =
                        browserState.tabs
                            .filterNot { tab -> tab.sessionId == sessionId }
                            .map { tab -> tab.profile }
                    val selectedProfile =
                        resolveSelectedProfileAfterRemoval(
                            selectedProfile = hostState.selectedProfile,
                            remainingProfiles = remainingProfiles,
                        )
                    onCloseTab(sessionId)
                    if (selectedProfile != hostState.selectedProfile) {
                        onHostStateChange { current ->
                            current.copy(selectedProfile = selectedProfile)
                        }
                    }
                },
                onNewTab = {
                    onNewTab(hostState.selectedProfile)
                    dismissSheet()
                },
                onCloseAllTabs = {
                    val clearedProfile = hostState.selectedProfile
                    onCloseAllTabs(clearedProfile)
                    if (shouldOpenConfiguredHomeAfterClearingWindows(homeUrl)) {
                        onNewTab(clearedProfile)
                        dismissSheet()
                    } else {
                        val remainingProfiles =
                            browserState.tabs
                                .filterNot { tab -> tab.profile == clearedProfile }
                                .map { tab -> tab.profile }
                        val selectedProfile =
                            resolveSelectedProfileAfterRemoval(
                                selectedProfile = clearedProfile,
                                remainingProfiles = remainingProfiles,
                            )
                        if (selectedProfile != clearedProfile) {
                            onHostStateChange { current ->
                                current.copy(selectedProfile = selectedProfile)
                            }
                        }
                    }
                },
                onProfileChange = { profile ->
                    onHostStateChange { current -> current.copy(selectedProfile = profile) }
                },
            )
        }

        if (mountedDrawerRoute.isWebSessionBrowserDrawerRoute()) {
            // BrowserContent now exists only in the App Shell. The custom drawer keeps the
            // browser-owned drag and viewport contract shared by its child routes.
            if (mountedDrawerRoute == WebSessionBrowserSheetRoute.MENU) {
                WebSessionBrowserMenuDrawer(
                    isVisible = activeSheetRoute == WebSessionBrowserSheetRoute.MENU,
                    isBookmarked = isBookmarked,
                    canAddBookmark = browserState.activeSessionId != null && currentBookmarkUrl != null,
                    onAddBookmark = {
                        if (isBookmarked) {
                            onRemoveBookmark(browserState.currentUrl)
                        } else {
                            currentBookmarkUrl?.let { url ->
                                addBookmarkDraft =
                                    WebSessionBookmarkDraft(
                                        title = browserState.pageTitle,
                                        url = url,
                                        iconUrl = buildWebSessionFaviconUrl(url),
                                        folderId = null,
                                    )
                            }
                        }
                        dismissSheet()
                    },
                    onOpenBookmarks = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.BOOKMARKS) } },
                    onOpenHistory = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.HISTORY) } },
                    onOpenDownloads = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.DOWNLOADS) } },
                    onOpenPlugins = {
                        onHostStateChange {
                            it.copy(
                                sheetRoute = WebSessionBrowserSheetRoute.PLUGINS,
                                pluginRouteStack =
                                    listOf(WebSessionBrowserPluginRoute.Overview),
                            )
                        }
                        onOpenPlugins()
                    },
                    onOpenFloatingSniffer = {
                        onHostStateChange {
                            it.copy(sheetRoute = WebSessionBrowserSheetRoute.MEDIA_CANDIDATES)
                        }
                    },
                    onOpenUserAgent = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.USER_AGENT) } },
                    onOpenNetworkLog = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.NETWORK_LOG) } },
                    onOpenAiDialogue = {
                        dismissSheet()
                        onOpenAiDialogue()
                    },
                    onOpenToolbox = { openPlaceholder(WebSessionBrowserPlaceholderPage.TOOLBOX) },
                    incognitoEnabled =
                        browserState.defaultSessionProfile == WebSessionProfile.INCOGNITO ||
                            browserState.incognitoAvailability.isAvailable,
                    onToggleIncognito = {
                        toggleDefaultProfile(browserState.defaultSessionProfile)
                    },
                    onOpenReaderMode = { openPlaceholder(WebSessionBrowserPlaceholderPage.READER_MODE) },
                    onOpenPageSource = {
                        dismissSheet()
                        onOpenPageSource()
                    },
                    onOpenAdMarking = {
                        dismissSheet()
                        onStartAdMarking()
                    },
                    onOpenSiteConfig = { openPlaceholder(WebSessionBrowserPlaceholderPage.SITE_CONFIG) },
                    onOpenSettingsHome = {
                        dismissSheet()
                        onOpenSettingsHome()
                    },
                    onExitBrowser = {
                        dismissSheet()
                        onExitBrowser()
                    },
                    onCollapse = dismissSheet,
                )
                if (activeSheetRoute == WebSessionBrowserSheetRoute.MENU) {
                    profileFeedback?.let { message ->
                        WebSessionBrowserProfileFeedback(message)
                    }
                }
            }
            if (
                mountedDrawerRoute.isBrowserChildDrawerRoute() &&
                    pluginEditorRoute == null
            ) {
                WebSessionBrowserBottomDrawer(
                    isVisible = activeSheetRoute.isBrowserChildDrawerRoute(),
                    layout = chromeLayout,
                    onDismissRequest = dismissSheet,
                    onHidden = {
                        if (!activeSheetRoute.isWebSessionBrowserDrawerRoute()) {
                            mountedDrawerRoute = WebSessionBrowserSheetRoute.NONE
                        }
                    },
                ) {
                    AnimatedContent(
                        targetState = mountedDrawerRoute,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            (fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 24 })
                                .togetherWith(
                                    fadeOut(tween(110)) +
                                        slideOutVertically(tween(110)) { -it / 30 },
                                )
                        },
                        label = "WebSessionBrowserDrawerRoute",
                    ) { drawerRoute ->
                        WebSessionBrowserDrawerContent(
                            sheetRoute = drawerRoute,
                            browserState = browserState,
                            bookmarks = bookmarks,
                            bookmarkFolders = bookmarkFolders,
                            globalHistory = globalHistory,
                            userscriptUiState = userscriptUiState,
                            onDismiss = dismissSheet,
                            onBookmarkMutation = onBookmarkMutation,
                            onOpenBookmarkInTab = onOpenBookmarkInTab,
                            onOpenUrl = onOpenUrl,
                            onOpenHistoryEntry = onOpenHistoryEntry,
                            onDeleteHistory = onDeleteHistory,
                            onDeleteHistoryEntries = onDeleteHistoryEntries,
                            onClearNetworkLog = onClearNetworkLog,
                            onAddNetworkBlockRule = onAddNetworkBlockRule,
                            onHostStateChange = onHostStateChange,
                            hostState = hostState,
                            onImportUserscript = onImportUserscript,
                            onInstallUserscriptFromUrl = onInstallUserscriptFromUrl,
                            onConfirmUserscriptInstall = onConfirmUserscriptInstall,
                            onCancelUserscriptInstall = onCancelUserscriptInstall,
                            onSetUserScriptsAllowed = onSetUserScriptsAllowed,
                            onSetUserscriptEnabled = onSetUserscriptEnabled,
                            onDeleteUserscript = onDeleteUserscript,
                            onCheckUserscriptUpdate = onCheckUserscriptUpdate,
                            onCheckAllUserscriptUpdates = onCheckAllUserscriptUpdates,
                            onApplyUserscriptUpdate = onApplyUserscriptUpdate,
                            onApplyAllSafeUserscriptUpdates = onApplyAllSafeUserscriptUpdates,
                            onSetUserscriptsEnabled = onSetUserscriptsEnabled,
                            onDeleteUserscripts = onDeleteUserscripts,
                            onLoadUserscriptDetail = onLoadUserscriptDetail,
                            onOpenNewUserscriptEditor = onOpenNewUserscriptEditor,
                            onOpenExistingUserscriptEditor = onOpenExistingUserscriptEditor,
                            onOpenUserscriptDraftEditor = onOpenUserscriptDraftEditor,
                            onRequestPluginBack = onRequestPluginBack,
                            onInvokeUserscriptMenu = onInvokeUserscriptMenu,
                            onPlayMediaCandidate = onPlayMediaCandidate,
                            onDownloadMediaCandidate = onDownloadMediaCandidate,
                            onOpenPageSource = onOpenPageSource,
                            onPauseDownload = onPauseDownload,
                            onResumeDownload = onResumeDownload,
                            onCancelDownload = onCancelDownload,
                            onRetryDownload = onRetryDownload,
                            onDeleteDownload = onDeleteDownload,
                            onOpenDownloadedFile = onOpenDownloadedFile,
                            onOpenDownloadFileManager = onOpenDownloadFileManager,
                            onStartManualDownload = onStartManualDownload,
                            onRedownload = onRedownload,
                            onRenameDownload = onRenameDownload,
                            onMoveDownload = onMoveDownload,
                            onCopyDownloadUrl = onCopyDownloadUrl,
                            onShareDownload = onShareDownload,
                            onCopyDownloadLocation = onCopyDownloadLocation,
                            onTransferDownload = onTransferDownload,
                            onMergeDownloadToMp4 = onMergeDownloadToMp4,
                            onOpenDownloadSettings = {
                                dismissSheet()
                                onOpenDownloadSettings()
                            },
                        )
                    }
                }
            }
        }

        if (activeSheetRoute == WebSessionBrowserSheetRoute.PAGE_SOURCE) {
            WebSessionPageSourceEditor(
                state = hostState.pageSource,
                onRequestBack = onBack,
                onOpenAiDialogue = onOpenAiDialogue,
                onBufferChanged = onUpdatePageSourceBuffer,
                onCopy = onCopyPageSource,
                onReload = onReloadPageSource,
                onApply = onApplyPageSource,
                onKeepAndClose = onKeepPageSourceDraftAndClose,
                onDiscardAndClose = onDiscardPageSourceDraftAndClose,
                onDismissExitPrompt = onDismissPageSourceExitPrompt,
                modifier = Modifier.fillMaxSize(),
            )
        }

        pluginEditorRoute?.let { editorRoute ->
            WebSessionUserscriptEditorPage(
                draftId = editorRoute.draftId,
                userscriptId = editorRoute.scriptId,
                userscriptState = userscriptUiState,
                onRequestBack = onRequestPluginBack,
                onBufferChanged = onUpdateUserscriptEditorBuffer,
                onPersistDraft = onPersistUserscriptDraft,
                onValidateDraft = onValidateUserscriptDraft,
                onFormatDraft = onFormatUserscriptDraft,
                onApplyDraft = onApplyUserscriptDraft,
                modifier = Modifier.fillMaxSize(),
            )
        }

        hostState.pluginEditorExitPromptDraftId?.let { draftId ->
            AlertDialog(
                onDismissRequest = {
                    onHostStateChange { current ->
                        current.copy(pluginEditorExitPromptDraftId = null)
                    }
                },
                title = {
                    Text(stringResource(R.string.web_session_userscript_editor_leave_title))
                },
                text = {
                    Text(stringResource(R.string.web_session_userscript_editor_leave_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onPersistUserscriptDraft(draftId) {
                                onHostStateChange { current ->
                                    current.copy(
                                        pluginRouteStack =
                                            popBrowserPluginRoute(current.pluginRouteStack),
                                        pluginEditorExitPromptDraftId = null,
                                    )
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.web_session_userscript_editor_keep_draft))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                onDiscardUserscriptDraft(draftId) {
                                    onHostStateChange { current ->
                                        current.copy(
                                            pluginRouteStack =
                                                popBrowserPluginRoute(current.pluginRouteStack),
                                            pluginEditorExitPromptDraftId = null,
                                        )
                                    }
                                }
                            },
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.web_session_userscript_editor_discard_draft,
                                    ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = {
                                onHostStateChange { current ->
                                    current.copy(pluginEditorExitPromptDraftId = null)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                },
            )
        }

        hostState.downloadPrompt?.let { prompt ->
            BrowserDownloadConfirmationOverlay(
                prompt = prompt,
                onConfirm = { onConfirmBrowserDownload(prompt.requestId) },
                onCancel = { onCancelBrowserDownload(prompt.requestId) },
            )
        }

        browserState.pendingDialog?.let { pendingDialog ->
            PendingDialogOverlay(
                dialog = pendingDialog,
                promptValue = promptDraft,
                onPromptValueChange = { promptDraft = it },
                onConfirm = {
                    onHandlePendingDialog(true, promptDraft)
                },
                onDismiss = {
                    onHandlePendingDialog(false, null)
                }
            )
        }
        }
        if (activeSheetRoute == WebSessionBrowserSheetRoute.USER_AGENT) {
            WebSessionBrowserUserAgentDialog(
                globalMode = browserState.userAgentMode,
                customGlobalUserAgent = browserState.customGlobalUserAgent,
                currentUrl = browserState.currentUrl,
                activeSiteRule = browserState.activeSiteUserAgentRule,
                onSelectGlobalMode = { mode ->
                    onSelectUserAgentMode(mode)
                    dismissSheet()
                },
                onSaveCustomGlobalUserAgent = { userAgent ->
                    onSaveCustomGlobalUserAgent(userAgent)
                    dismissSheet()
                },
                onSaveSiteUserAgentRule = { domain, userAgent ->
                    onSaveSiteUserAgentRule(domain, userAgent)
                    dismissSheet()
                },
                onDismiss = dismissSheet,
            )
        }
        addBookmarkDraft?.let { draft ->
            WebSessionBookmarkEditorDialog(
                title = "新增书签",
                tone = WebSessionBrowserMenuTone.ADD_BOOKMARK,
                initialDraft = draft,
                folderOptions = bookmarkFolderOptions,
                onDismiss = { addBookmarkDraft = null },
                onConfirm = { confirmed ->
                    if (normalizeWebSessionBookmarkUrl(confirmed.url) == null) {
                        Toast.makeText(context, "请输入有效的 HTTP 或 HTTPS 网址", Toast.LENGTH_SHORT).show()
                    } else {
                        onBookmarkMutation(WebSessionBookmarkMutation.SaveBookmark(confirmed, secret = false))
                        addBookmarkDraft = null
                        Toast.makeText(context, "书签已保存", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        when (hostState.adMarkingOverlay) {
            WebSessionAdMarkingOverlay.NONE -> Unit
            WebSessionAdMarkingOverlay.EDIT_RULE ->
                WebSessionAdMarkingRuleEditor(
                    ruleDraft = hostState.adMarking.ruleDraft,
                    onRuleDraftChange = onUpdateAdMarkingRuleDraft,
                    onDismiss = onDismissAdMarkingOverlay,
                    onPreview = onPreviewAdMarkingRuleDraft,
                    onConfirm = onConfirmAdMarkingRuleEdit,
                )
            WebSessionAdMarkingOverlay.CLEAR_CONFIRM ->
                WebSessionAdMarkingClearConfirmation(
                    domain = hostState.adMarking.domain,
                    onDismiss = onDismissAdMarkingOverlay,
                    onConfirm = onConfirmClearAdMarking,
                )
            WebSessionAdMarkingOverlay.NAVIGATION_POLICY ->
                WebSessionAdMarkingNavigationPolicySheet(
                    selectedPolicy = hostState.adMarking.navigationPolicy,
                    onDismiss = onDismissAdMarkingOverlay,
                    onSelect = onSelectAdMarkingNavigationPolicy,
                )
        }
        hostState.adMarkingNavigationRequest?.let { request ->
            WebSessionAdMarkingNavigationRequestDialog(
                request = request,
                onDismiss = onCancelAdMarkingNavigationRequest,
                onAllow = onAllowAdMarkingNavigationRequest,
            )
        }
        hostState.webElementAction?.let { element ->
            WebSessionWebElementActionDialog(
                state = element,
                onDismiss = {
                    onHostStateChange { current -> current.copy(webElementAction = null) }
                },
                onOpenInNewTab = onOpenBookmarkInTab,
                onCopyUrl = onCopyWebElementUrl,
                onCopyText = onCopyWebElementText,
                onOpenExternal = onOpenExternalUrl,
                onSelectText = onSelectElementText,
                onBlockElement = onStartAdMarkingFromCurrentElement,
                onBlockUrl = onAddNetworkBlockRule,
            )
        }
    }
}

@Composable
private fun BrowserDownloadConfirmationOverlay(
    prompt: BrowserDownloadPromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val engineLabel =
        when (prompt.engine) {
            BrowserDownloadEngine.INTERNAL -> stringResource(R.string.web_session_download_engine_internal)
            BrowserDownloadEngine.SYSTEM -> stringResource(R.string.web_session_download_engine_system)
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.web_session_download_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.web_session_download_confirm_file, prompt.fileName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                prompt.mimeType?.let { mimeType ->
                    Text(
                        text = stringResource(R.string.web_session_download_confirm_type, mimeType),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (prompt.contentLength > 0L) {
                    Text(
                        text =
                            stringResource(
                                R.string.web_session_download_confirm_size,
                                Formatter.formatFileSize(context, prompt.contentLength),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.web_session_download_confirm_engine, engineLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                prompt.destinationName?.let { destinationName ->
                    Text(
                        text =
                            stringResource(
                                R.string.web_session_download_confirm_destination,
                                destinationName,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.web_session_download_confirm_cancel))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.web_session_download_confirm_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingDialogOverlay(
    dialog: WebSessionPendingDialogState,
    promptValue: String,
    onPromptValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text =
                        when (dialog.type.lowercase(Locale.ROOT)) {
                            "confirm" -> stringResource(R.string.web_session_dialog_title_confirm)
                            "prompt" -> stringResource(R.string.web_session_dialog_title_prompt)
                            else -> stringResource(R.string.web_session_dialog_title_alert)
                        },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dialog.message.ifBlank { stringResource(R.string.web_session_dialog_empty_message) },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (dialog.type.equals("prompt", ignoreCase = true)) {
                    OutlinedTextField(
                        value = promptValue,
                        onValueChange = onPromptValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!dialog.type.equals("alert", ignoreCase = true)) {
                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    }
                    TextButton(onClick = onConfirm) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSessionBrowserDrawerContent(
    sheetRoute: WebSessionBrowserSheetRoute,
    browserState: com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserState,
    bookmarks: List<WebSessionBookmark>,
    bookmarkFolders: List<WebSessionBookmarkFolder>,
    globalHistory: List<WebSessionHistoryEntry>,
    userscriptUiState: WebSessionUserscriptUiState,
    onDismiss: () -> Unit,
    onBookmarkMutation: (WebSessionBookmarkMutation) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenHistoryEntry: (WebSessionHistoryEntry) -> Boolean,
    onDeleteHistory: (WebSessionHistoryCategory?, Long?) -> Unit,
    onDeleteHistoryEntries: (Set<WebSessionHistoryEntryKey>) -> Unit,
    onClearNetworkLog: () -> Unit,
    onAddNetworkBlockRule: (String) -> Unit,
    onHostStateChange: ((WebSessionBrowserHostState) -> WebSessionBrowserHostState) -> Unit,
    hostState: WebSessionBrowserHostState,
    onImportUserscript: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onCheckAllUserscriptUpdates: () -> Unit,
    onApplyUserscriptUpdate: (Long) -> Unit,
    onApplyAllSafeUserscriptUpdates: () -> Unit,
    onSetUserscriptsEnabled: (Set<Long>, Boolean) -> Unit,
    onDeleteUserscripts: (Set<Long>) -> Unit,
    onLoadUserscriptDetail: (Long) -> Unit,
    onOpenNewUserscriptEditor: () -> Unit,
    onOpenExistingUserscriptEditor: (Long) -> Unit,
    onOpenUserscriptDraftEditor: (String) -> Unit,
    onRequestPluginBack: () -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    onPlayMediaCandidate: (String) -> Boolean,
    onDownloadMediaCandidate: (String) -> Boolean,
    onOpenPageSource: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadFileManager: () -> Unit,
    onStartManualDownload: (String, String, String, BrowserDownloadEngine) -> Boolean,
    onRedownload: (String) -> Unit,
    onRenameDownload: (String, String, BrowserDownloadRenameMode) -> Unit,
    onMoveDownload: (String, String) -> Unit,
    onCopyDownloadUrl: (String) -> Unit,
    onShareDownload: (String) -> Unit,
    onCopyDownloadLocation: (String) -> Unit,
    onTransferDownload: (String) -> Unit,
    onMergeDownloadToMp4: (String) -> Unit,
    onOpenDownloadSettings: () -> Unit,
) {
    when (sheetRoute) {
        WebSessionBrowserSheetRoute.DOWNLOADS ->
            WebSessionDownloadSheet(
                uiState = hostState.downloadUiState,
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onCancelDownload = onCancelDownload,
                onRetryDownload = onRetryDownload,
                onDeleteDownload = onDeleteDownload,
                onOpenDownloadedFile = onOpenDownloadedFile,
                onOpenDownloadFileManager = onOpenDownloadFileManager,
                onStartManualDownload = onStartManualDownload,
                onRedownload = onRedownload,
                onRenameDownload = onRenameDownload,
                onMoveDownload = onMoveDownload,
                onCopyDownloadUrl = onCopyDownloadUrl,
                onShareDownload = onShareDownload,
                onCopyDownloadLocation = onCopyDownloadLocation,
                onTransferDownload = onTransferDownload,
                onMergeDownloadToMp4 = onMergeDownloadToMp4,
                onOpenDownloadSettings = onOpenDownloadSettings,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.HISTORY ->
            WebSessionHistorySheet(
                entries = globalHistory,
                bookmarkFolders = bookmarkFolders,
                systemBackEnabled = LocalWebSessionBrowserSystemBackEnabled.current,
                onOpenEntry = { entry ->
                    onOpenHistoryEntry(entry).also { accepted ->
                        if (accepted) onDismiss()
                    }
                },
                onOpenWebUrl = { url ->
                    onOpenUrl(url)
                    onDismiss()
                },
                onBookmarkMutation = onBookmarkMutation,
                onDeleteHistory = onDeleteHistory,
                onDeleteHistoryEntries = onDeleteHistoryEntries,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.BOOKMARKS ->
            WebSessionBookmarkSheet(
                folders = bookmarkFolders,
                bookmarks = bookmarks,
                systemBackEnabled = LocalWebSessionBrowserSystemBackEnabled.current,
                onMutation = onBookmarkMutation,
                onOpenBookmark = { url ->
                    onOpenUrl(url)
                    onDismiss()
                },
                onOpenBookmarkInTab = { url, active ->
                    onOpenBookmarkInTab(url, active)
                    if (active) onDismiss()
                },
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.PLUGINS ->
            WebSessionBrowserPluginSheet(
                route = hostState.currentPluginRoute,
                userscriptState = userscriptUiState,
                currentPageMenuCommands = browserState.userscriptMenuCommands,
                onOpenUserscriptManager = { initialTab, initialSearchQuery ->
                    onHostStateChange { current ->
                        current.copy(
                            pluginRouteStack =
                                pushBrowserPluginRoute(
                                    current.pluginRouteStack,
                                    WebSessionBrowserPluginRoute.Userscripts(
                                        initialTab = initialTab,
                                        initialSearchQuery = initialSearchQuery,
                                    ),
                                ),
                        )
                    }
                },
                onOpenUserscriptDetail = { scriptId ->
                    onHostStateChange { current ->
                        current.copy(
                            pluginRouteStack =
                                pushBrowserPluginRoute(
                                    current.pluginRouteStack,
                                    WebSessionBrowserPluginRoute.UserscriptDetail(scriptId),
                                ),
                        )
                    }
                },
                onOpenPluginLibrarySource = { url ->
                    onOpenBookmarkInTab(url, true)
                    onDismiss()
                },
                onNavigateToOverview = onRequestPluginBack,
                onInstallUserscriptFromUrl = onInstallUserscriptFromUrl,
                onImportUserscript = onImportUserscript,
                onConfirmUserscriptInstall = onConfirmUserscriptInstall,
                onCancelUserscriptInstall = onCancelUserscriptInstall,
                onSetUserScriptsAllowed = onSetUserScriptsAllowed,
                onSetUserscriptEnabled = onSetUserscriptEnabled,
                onDeleteUserscript = onDeleteUserscript,
                onCheckUserscriptUpdate = onCheckUserscriptUpdate,
                onCheckAllUserscriptUpdates = onCheckAllUserscriptUpdates,
                onApplyUserscriptUpdate = onApplyUserscriptUpdate,
                onApplyAllSafeUserscriptUpdates = onApplyAllSafeUserscriptUpdates,
                onSetUserscriptsEnabled = onSetUserscriptsEnabled,
                onDeleteUserscripts = onDeleteUserscripts,
                onLoadUserscriptDetail = onLoadUserscriptDetail,
                onOpenNewUserscriptEditor = onOpenNewUserscriptEditor,
                onOpenExistingUserscriptEditor = onOpenExistingUserscriptEditor,
                onOpenUserscriptDraftEditor = onOpenUserscriptDraftEditor,
                onInvokeUserscriptMenu = onInvokeUserscriptMenu,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.NETWORK_LOG ->
            WebSessionBrowserNetworkLog(
                entries = browserState.networkEntries,
                currentPageUrl = browserState.currentUrl,
                onClear = onClearNetworkLog,
                onBlockUrl = onAddNetworkBlockRule,
                onStartDownload = onStartManualDownload,
                onPlayMediaCandidate = onPlayMediaCandidate,
                onDownloadMediaCandidate = onDownloadMediaCandidate,
                onOpenPageSource = {
                    onDismiss()
                    onOpenPageSource()
                },
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.MEDIA_CANDIDATES ->
            WebSessionMediaCandidateSheet(
                candidates = browserState.mediaCandidates,
                onPlay = { candidateId ->
                    onPlayMediaCandidate(candidateId).also { accepted ->
                        if (accepted) onDismiss()
                    }
                },
                onDownload = { candidateId ->
                    onDownloadMediaCandidate(candidateId).also { accepted ->
                        if (accepted) onDismiss()
                    }
                },
                onDismiss = onDismiss,
            )

        WebSessionBrowserSheetRoute.PLACEHOLDER ->
            WebSessionBrowserPlaceholderSheet(
                page = hostState.placeholderPage,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.NONE,
        WebSessionBrowserSheetRoute.TABS,
        WebSessionBrowserSheetRoute.MENU,
        WebSessionBrowserSheetRoute.USER_AGENT,
        WebSessionBrowserSheetRoute.PAGE_SOURCE -> Unit
        }
    }

@Composable
private fun BrowserDownloadSummaryBar(
    activeCount: Int,
    overallProgress: Float?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.web_session_downloads_active_bar, activeCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text =
                    overallProgress?.let { progress ->
                        "${(progress * 100f).toInt()}%"
                    } ?: stringResource(R.string.web_session_downloads_active_unknown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

internal fun buildWebSessionFaviconUrl(url: String): String =
    Uri.parse(url)
        .buildUpon()
        .encodedPath("/favicon.ico")
        .encodedQuery(null)
        .fragment(null)
        .build()
        .toString()
