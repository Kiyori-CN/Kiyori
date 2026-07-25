package com.ai.assistance.operit.ui.features.websession.browser

import android.net.Uri
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserHostState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserNetworkEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserPlaceholderPage
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSheetRoute
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionPendingDialogState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchRecord
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveSelectedProfileAfterRemoval
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.opposite
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomBar
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserBottomDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserMenuDrawer
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserTabOverview
import com.ai.assistance.operit.ui.features.websession.browser.chrome.resolveWebSessionBrowserChromeLayout
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserNetworkLog
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserPageSource
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserPlaceholderSheet
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserTopBar
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserUserAgent
import com.ai.assistance.operit.ui.theme.KiyoriBrowserTheme
import java.util.Locale
import kotlinx.coroutines.delay

private fun WebSessionBrowserSheetRoute.isBrowserDrawerRoute(): Boolean =
    this != WebSessionBrowserSheetRoute.NONE && this != WebSessionBrowserSheetRoute.TABS

private fun WebSessionBrowserSheetRoute.isBrowserChildDrawerRoute(): Boolean =
    isBrowserDrawerRoute() && this != WebSessionBrowserSheetRoute.MENU

@Composable
internal fun WebSessionBrowserScreen(
    hostState: WebSessionBrowserHostState,
    bookmarks: List<WebSessionBookmark>,
    globalHistory: List<WebSessionHistoryEntry>,
    searchEngine: WebSessionSearchEngine,
    searchHistory: List<WebSessionSearchRecord>,
    userscriptUiState: WebSessionUserscriptUiState,
    webViewHost: WebSessionWebViewHost,
    onHostStateChange: ((WebSessionBrowserHostState) -> WebSessionBrowserHostState) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (WebSessionProfile) -> Unit,
    onRequestTabThumbnails: () -> Unit,
    onTopBarBack: () -> Unit,
    onOpenAiDialogue: () -> Unit,
    onExitBrowser: () -> Unit,
    onCloseCurrentTab: () -> Unit,
    onCloseAllTabs: (WebSessionProfile) -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onSelectSessionHistory: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onSetSearchEngine: (WebSessionSearchEngine) -> Unit,
    onSetDefaultSessionProfile: (WebSessionProfile) -> Boolean,
    onSubmitSearch: (String, WebSessionSearchEngine, WebSessionProfile) -> Unit,
    onOpenSearchRecord: (WebSessionSearchRecord, WebSessionProfile) -> Unit,
    onDeleteSearchHistory: (Long) -> Unit,
    onClearSearchHistory: () -> Unit,
    onCopyCurrentUrl: () -> Unit,
    onOpenPageSource: () -> Unit,
    onCopyPageSource: () -> Unit,
    onOpenUserscripts: () -> Unit,
    onImportUserscript: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit,
    onConfirmExternalOpen: (String) -> Unit,
    onCancelExternalOpen: (String) -> Unit,
    onHandlePendingDialog: (Boolean, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val browserState = hostState.browserState
    var totalHeightPx by remember { mutableIntStateOf(0) }
    var browserAreaHeightPx by remember { mutableIntStateOf(0) }
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
    val isBookmarked =
        remember(browserState.currentUrl, bookmarks) {
            val normalizedUrl = normalizeLookupUrl(browserState.currentUrl)
            normalizedUrl != null && bookmarks.any { it.url == normalizedUrl }
        }
    val dismissSheet = {
        onHostStateChange { current ->
            current.copy(
                sheetRoute = WebSessionBrowserSheetRoute.NONE,
                placeholderPage = null,
            )
        }
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
    var tabOverviewMounted by remember { mutableStateOf(false) }
    var mountedDrawerRoute by remember { mutableStateOf(WebSessionBrowserSheetRoute.NONE) }
    var profileFeedback by remember { mutableStateOf<String?>(null) }
    val incognitoEnabledMessage = stringResource(R.string.web_session_incognito_enabled)
    val incognitoDisabledMessage = stringResource(R.string.web_session_incognito_disabled)
    LaunchedEffect(activeSheetRoute) {
        when {
            activeSheetRoute == WebSessionBrowserSheetRoute.TABS -> {
                tabOverviewMounted = true
                onRequestTabThumbnails()
            }
            activeSheetRoute.isBrowserDrawerRoute() -> mountedDrawerRoute = activeSheetRoute
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
                isLoading = browserState.isLoading,
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
                onRefreshOrStop = onRefreshOrStop,
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
                        AndroidView(
                            factory = { context ->
                                FrameLayout(context).apply {
                                    setBackgroundColor(browserHostBackgroundColor)
                                    webViewHost.attachContainer(this)
                                }
                            },
                            update = { container ->
                                container.setBackgroundColor(browserHostBackgroundColor)
                                webViewHost.attachContainer(container)
                            },
                            onRelease = { container ->
                                webViewHost.detachContainer(container)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            WebSessionBrowserBottomBar(
                canGoBack = browserState.canGoBack,
                canGoForward = browserState.canGoForward,
                tabCount = browserState.tabs.size,
                onBack = onBack,
                onForward = onForward,
                onHome = { onNavigate("about:blank") },
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
                }
            )
            }
        }

        if (hostState.isSearchVisible) {
            WebSessionBrowserSearchScreen(
                currentUrl = browserState.currentUrl,
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
                onEnginePanelVisibleChange = { visible ->
                    onHostStateChange { current -> current.copy(isSearchEnginePanelVisible = visible) }
                },
                onSubmit = {
                    val query = hostState.searchDraft.trim()
                    if (query.isNotBlank()) {
                        onSubmitSearch(query, searchEngine, hostState.searchProfile)
                        profileFeedback = null
                        onHostStateChange { current -> current.copy(isSearchVisible = false, isSearchEnginePanelVisible = false, searchDraft = "") }
                    }
                },
                onSelectEngine = onSetSearchEngine,
                onOpenSearchRecord = { record ->
                    onOpenSearchRecord(record, hostState.searchProfile)
                    profileFeedback = null
                    onHostStateChange { current -> current.copy(isSearchVisible = false, isSearchEnginePanelVisible = false, searchDraft = "") }
                },
                onDeleteSearchRecord = onDeleteSearchHistory,
                onClearSearchHistory = onClearSearchHistory,
                onCopyCurrentUrl = onCopyCurrentUrl,
                onOpenCurrentUrl = {
                    onOpenUrl(browserState.currentUrl)
                    profileFeedback = null
                    onHostStateChange { current -> current.copy(isSearchVisible = false, isSearchEnginePanelVisible = false, searchDraft = "") }
                },
                onUseCurrentUrl = {
                    onHostStateChange { current -> current.copy(searchDraft = browserState.currentUrl) }
                },
                selectedProfile = hostState.searchProfile,
                incognitoAvailability = browserState.incognitoAvailability,
                onToggleProfile = {
                    val requestedProfile = hostState.searchProfile.opposite()
                    if (onSetDefaultSessionProfile(requestedProfile)) {
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
                },
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
                    val remainingProfiles =
                        browserState.tabs
                            .filterNot { tab -> tab.profile == hostState.selectedProfile }
                            .map { tab -> tab.profile }
                    val selectedProfile =
                        resolveSelectedProfileAfterRemoval(
                            selectedProfile = hostState.selectedProfile,
                            remainingProfiles = remainingProfiles,
                        )
                    onCloseAllTabs(hostState.selectedProfile)
                    if (selectedProfile != hostState.selectedProfile) {
                        onHostStateChange { current ->
                            current.copy(selectedProfile = selectedProfile)
                        }
                    }
                },
                onProfileChange = { profile ->
                    onHostStateChange { current -> current.copy(selectedProfile = profile) }
                },
            )
        }

        if (mountedDrawerRoute.isBrowserDrawerRoute()) {
            // This drawer is composed in both the App Shell and TYPE_APPLICATION_OVERLAY host;
            // a dialog-backed Material sheet cannot safely obtain an Activity token there.
            if (mountedDrawerRoute == WebSessionBrowserSheetRoute.MENU) {
                WebSessionBrowserMenuDrawer(
                    isVisible = activeSheetRoute == WebSessionBrowserSheetRoute.MENU,
                    isBookmarked = isBookmarked,
                    canAddBookmark = browserState.activeSessionId != null && browserState.currentUrl != "about:blank",
                    onAddBookmark = {
                        onToggleBookmark(browserState.currentUrl, browserState.pageTitle)
                        dismissSheet()
                    },
                    onOpenBookmarks = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.BOOKMARKS) } },
                    onOpenHistory = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.HISTORY) } },
                    onOpenDownloads = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.DOWNLOADS) } },
                    onOpenUserscripts = {
                        onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.USERSCRIPTS) }
                        onOpenUserscripts()
                    },
                    onOpenFloatingSniffer = { openPlaceholder(WebSessionBrowserPlaceholderPage.FLOATING_SNIFFER) },
                    onOpenUserAgent = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.USER_AGENT) } },
                    onOpenNetworkLog = { onHostStateChange { it.copy(sheetRoute = WebSessionBrowserSheetRoute.NETWORK_LOG) } },
                    onOpenAiDialogue = {
                        dismissSheet()
                        onOpenAiDialogue()
                    },
                    onOpenToolbox = { openPlaceholder(WebSessionBrowserPlaceholderPage.TOOLBOX) },
                    onOpenIncognito = {
                        onHostStateChange { current ->
                            current.copy(
                                sheetRoute = WebSessionBrowserSheetRoute.TABS,
                                selectedProfile = WebSessionProfile.INCOGNITO,
                            )
                        }
                    },
                    onOpenReaderMode = { openPlaceholder(WebSessionBrowserPlaceholderPage.READER_MODE) },
                    onOpenPageSource = {
                        dismissSheet()
                        onOpenPageSource()
                    },
                    onOpenAdMarking = { openPlaceholder(WebSessionBrowserPlaceholderPage.AD_MARKING) },
                    onOpenSiteConfig = { openPlaceholder(WebSessionBrowserPlaceholderPage.SITE_CONFIG) },
                    onExitBrowser = {
                        dismissSheet()
                        onExitBrowser()
                    },
                    onCollapse = dismissSheet,
                )
            }
            if (mountedDrawerRoute.isBrowserChildDrawerRoute()) {
                WebSessionBrowserBottomDrawer(
                    isVisible = activeSheetRoute.isBrowserChildDrawerRoute(),
                    layout = chromeLayout,
                    onDismissRequest = dismissSheet,
                    onHidden = {
                        if (!activeSheetRoute.isBrowserDrawerRoute()) {
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
                            globalHistory = globalHistory,
                            userscriptUiState = userscriptUiState,
                            isBookmarked = isBookmarked,
                            onToggleBookmark = {
                                onToggleBookmark(browserState.currentUrl, browserState.pageTitle)
                            },
                            onDismiss = dismissSheet,
                            onRemoveBookmark = onRemoveBookmark,
                            onSelectSessionHistory = onSelectSessionHistory,
                            onOpenUrl = onOpenUrl,
                            onClearHistory = onClearHistory,
                            onToggleDesktopMode = onToggleDesktopMode,
                            onOpenUserAgent = {
                                onHostStateChange { current -> current.copy(sheetRoute = WebSessionBrowserSheetRoute.USER_AGENT) }
                            },
                            onOpenNetworkLog = {
                                onHostStateChange { current -> current.copy(sheetRoute = WebSessionBrowserSheetRoute.NETWORK_LOG) }
                            },
                            onReload = onRefreshOrStop,
                            onOpenPageSource = onOpenPageSource,
                            onCopyPageSource = onCopyPageSource,
                            onHostStateChange = onHostStateChange,
                            hostState = hostState,
                            onOpenUserscripts = onOpenUserscripts,
                            onImportUserscript = onImportUserscript,
                            onInstallUserscriptFromUrl = onInstallUserscriptFromUrl,
                            onConfirmUserscriptInstall = onConfirmUserscriptInstall,
                            onCancelUserscriptInstall = onCancelUserscriptInstall,
                            onSetUserscriptEnabled = onSetUserscriptEnabled,
                            onDeleteUserscript = onDeleteUserscript,
                            onCheckUserscriptUpdate = onCheckUserscriptUpdate,
                            onInvokeUserscriptMenu = onInvokeUserscriptMenu,
                            onPauseDownload = onPauseDownload,
                            onResumeDownload = onResumeDownload,
                            onCancelDownload = onCancelDownload,
                            onRetryDownload = onRetryDownload,
                            onDeleteDownload = onDeleteDownload,
                            onOpenDownloadedFile = onOpenDownloadedFile,
                            onOpenDownloadLocation = onOpenDownloadLocation,
                        )
                    }
                }
            }
        }

        hostState.externalOpenPrompt?.let { prompt ->
            ExternalOpenPromptBar(
                title = prompt.title,
                target = prompt.target,
                onConfirm = { onConfirmExternalOpen(prompt.requestId) },
                onCancel = { onCancelExternalOpen(prompt.requestId) },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 58.dp),
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
    globalHistory: List<WebSessionHistoryEntry>,
    userscriptUiState: WebSessionUserscriptUiState,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onSelectSessionHistory: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenUserAgent: () -> Unit,
    onOpenNetworkLog: () -> Unit,
    onReload: () -> Unit,
    onOpenPageSource: () -> Unit,
    onCopyPageSource: () -> Unit,
    onHostStateChange: ((WebSessionBrowserHostState) -> WebSessionBrowserHostState) -> Unit,
    hostState: WebSessionBrowserHostState,
    onOpenUserscripts: () -> Unit,
    onImportUserscript: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit
) {
    when (sheetRoute) {
        WebSessionBrowserSheetRoute.DOWNLOADS ->
            WebSessionDownloadSheet(
                uiState = hostState.downloadUiState,
                onFilterChange = { filter ->
                    onHostStateChange { current ->
                        current.copy(
                            downloadUiState =
                                current.downloadUiState.copy(selectedFilter = filter)
                        )
                    }
                },
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onCancelDownload = onCancelDownload,
                onRetryDownload = onRetryDownload,
                onDeleteDownload = onDeleteDownload,
                onOpenDownloadedFile = onOpenDownloadedFile,
                onOpenDownloadLocation = onOpenDownloadLocation,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.HISTORY ->
            WebSessionHistorySheet(
                sessionHistory = browserState.sessionHistory,
                globalHistory = globalHistory,
                onSelectSessionHistory = { index ->
                    onSelectSessionHistory(index)
                    onDismiss()
                },
                onOpenHistoryUrl = { url ->
                    onOpenUrl(url)
                    onDismiss()
                },
                onClearHistory = onClearHistory,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.BOOKMARKS ->
            WebSessionBookmarkSheet(
                bookmarks = bookmarks,
                onOpenBookmark = { url ->
                    onOpenUrl(url)
                    onDismiss()
                },
                onRemoveBookmark = onRemoveBookmark,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.USERSCRIPTS ->
            WebSessionUserscriptSheet(
                state = userscriptUiState,
                currentPageMenuCommands = browserState.userscriptMenuCommands,
                onInstallFromUrl = onInstallUserscriptFromUrl,
                onImportLocal = onImportUserscript,
                onConfirmInstall = onConfirmUserscriptInstall,
                onCancelInstall = onCancelUserscriptInstall,
                onSetScriptEnabled = onSetUserscriptEnabled,
                onDeleteScript = onDeleteUserscript,
                onCheckUpdate = onCheckUserscriptUpdate,
                onInvokeMenuCommand = onInvokeUserscriptMenu,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.USER_AGENT ->
            WebSessionBrowserUserAgent(
                userAgent = browserState.userAgent,
                isDesktopMode = browserState.isDesktopMode,
                onToggleDesktopMode = onToggleDesktopMode,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.NETWORK_LOG ->
            WebSessionBrowserNetworkLog(
                entries = browserState.networkEntries,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.PAGE_SOURCE ->
            WebSessionBrowserPageSource(
                isLoading = hostState.pageSource.isLoading,
                content = hostState.pageSource.content,
                error = hostState.pageSource.error,
                onCopy = onCopyPageSource,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.PLACEHOLDER ->
            WebSessionBrowserPlaceholderSheet(
                page = hostState.placeholderPage,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )

        WebSessionBrowserSheetRoute.NONE,
        WebSessionBrowserSheetRoute.TABS,
        WebSessionBrowserSheetRoute.MENU -> Unit
        }
    }

@Composable
private fun ExternalOpenPromptBar(
    title: String,
    target: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = target,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.web_session_external_open_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(R.string.web_session_external_open_allow_once))
                }
            }
        }
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

private fun normalizeLookupUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return null
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower.startsWith("about:") || lower.startsWith("blob:") || lower.startsWith("data:")) {
        return null
    }
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return null
    }

    return runCatching {
        val uri = Uri.parse(trimmed)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val portPart =
            when {
                uri.port < 0 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        val path = uri.encodedPath?.ifBlank { "/" } ?: "/"
        buildString {
            append(scheme)
            append("://")
            append(host)
            append(portPart)
            append(path)
            uri.encodedQuery?.takeIf { it.isNotBlank() }?.let {
                append('?')
                append(it)
            }
        }
    }.getOrElse { trimmed }
}
