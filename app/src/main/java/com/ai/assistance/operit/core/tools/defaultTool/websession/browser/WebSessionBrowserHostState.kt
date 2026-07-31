package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import kotlinx.serialization.Serializable

internal enum class WebSessionBrowserSheetRoute {
    NONE,
    TABS,
    MENU,
    DOWNLOADS,
    HISTORY,
    BOOKMARKS,
    PLUGINS,
    USER_AGENT,
    NETWORK_LOG,
    MEDIA_CANDIDATES,
    PAGE_SOURCE,
    PLACEHOLDER,
}

internal enum class WebSessionBrowserPluginPage {
    OVERVIEW,
    USERSCRIPTS,
}

internal enum class WebSessionBrowserPlaceholderPage {
    TOOLBOX,
    READER_MODE,
    AD_MARKING,
    SITE_CONFIG,
}

@Immutable
internal data class WebSessionBrowserTab(
    val sessionId: String,
    val title: String,
    val url: String,
    val isActive: Boolean,
    val hasSslError: Boolean,
    val profile: WebSessionProfile,
    val thumbnail: Bitmap?,
    val thumbnailUpdatedAt: Long,
)

@Immutable
internal data class WebSessionBrowserNetworkEntry(
    val method: String,
    val url: String,
    val isMainFrame: Boolean,
    val isStatic: Boolean,
    val category: BrowserNetworkRequestCategory,
    val timestamp: Long,
    val mediaCandidateId: String? = null,
)

@Immutable
internal data class WebSessionBrowserMediaCandidate(
    val id: String,
    val url: String,
    val pageUrl: String,
    val mimeType: String?,
    val urlEvidence: BrowserMediaCandidateUrlEvidence,
    val videoFormat: BrowserMediaCandidateVideoFormat,
    val discoverySources: Set<BrowserMediaCandidateDiscoverySource>,
    val firstDiscoveredAt: Long,
    val lastDiscoveredAt: Long,
    val durationMillis: Long?,
    val isLive: Boolean,
    val rankingScore: Int,
    val rankingSummary: String,
    val isRecommended: Boolean,
    val automaticFloatingEligible: Boolean,
    val directPlaybackReady: Boolean,
    val downloadReady: Boolean,
    val isBlob: Boolean,
)

@Immutable
internal data class WebSessionPendingDialogState(
    val type: String,
    val message: String,
    val defaultValue: String? = null,
    val url: String? = null
)

@Immutable
@Serializable
internal data class WebSessionSearchRecord(
    val id: Long,
    val query: String,
    val targetUrl: String,
    val createdAt: Long,
)

@Immutable
internal data class WebSessionPageSourceState(
    val isLoading: Boolean = false,
    val content: String? = null,
    val error: String? = null,
)

@Immutable
internal data class WebSessionTextSelectionActionsState(
    val anchorXPx: Int,
    val anchorYPx: Int,
)

@Immutable
internal data class WebSessionBrowserState(
    val activeSessionId: String? = null,
    val activeProfile: WebSessionProfile? = null,
    val defaultSessionProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val incognitoAvailability: WebSessionIncognitoAvailability =
        WebSessionIncognitoAvailability.UNSUPPORTED,
    val pageTitle: String = "",
    val currentUrl: String = "about:blank",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val hasSslError: Boolean = false,
    val userAgentMode: WebSessionUserAgentMode = WebSessionUserAgentMode.ANDROID,
    val customGlobalUserAgent: String = "",
    val activeSiteUserAgentRule: WebSessionSiteUserAgentRule? = null,
    val activeDownloadCount: Int = 0,
    val hasFailedDownloads: Boolean = false,
    val failedDownloadCount: Int = 0,
    val latestCompletedDownloadName: String? = null,
    val overallDownloadProgress: Float? = null,
    val pendingDialog: WebSessionPendingDialogState? = null,
    val tabs: List<WebSessionBrowserTab> = emptyList(),
    val userscriptMenuCommands: List<UserscriptPageMenuCommand> = emptyList(),
    val networkEntries: List<WebSessionBrowserNetworkEntry> = emptyList(),
    val mediaCandidates: List<WebSessionBrowserMediaCandidate> = emptyList(),
)

@Immutable
internal data class BrowserDownloadItem(
    val id: String,
    val fileName: String,
    val sourceUrl: String?,
    val mimeType: String?,
    val status: String,
    val type: String,
    val progress: Float?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val destinationPath: String,
    val createdAt: Long,
    val completedAt: Long?,
    val isM3u8Package: Boolean,
    val errorMessage: String?,
    val canPause: Boolean,
    val canResume: Boolean,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val canDelete: Boolean,
    val canDeleteFile: Boolean,
    val canOpenFile: Boolean,
    val canOpenLocation: Boolean,
    val canRedownload: Boolean,
    val canMergeToMp4: Boolean,
)

@Immutable
internal data class BrowserDownloadUiState(
    val tasks: List<BrowserDownloadItem> = emptyList(),
)

@Immutable
internal data class BrowserDownloadPromptState(
    val requestId: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long,
    val engine: BrowserDownloadEngine,
    val destinationName: String?,
)

@Immutable
internal data class WebSessionBrowserHostState(
    val browserState: WebSessionBrowserState = WebSessionBrowserState(),
    val sheetRoute: WebSessionBrowserSheetRoute = WebSessionBrowserSheetRoute.NONE,
    val pluginPage: WebSessionBrowserPluginPage = WebSessionBrowserPluginPage.OVERVIEW,
    val selectedProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val placeholderPage: WebSessionBrowserPlaceholderPage? = null,
    val isSearchVisible: Boolean = false,
    val isSearchEnginePanelVisible: Boolean = false,
    val searchDraft: String = "",
    val searchProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val lastSearchQuery: String = "",
    val isSearchEngineQuickSwitchBarVisible: Boolean = false,
    val pageSource: WebSessionPageSourceState = WebSessionPageSourceState(),
    val textSelectionActions: WebSessionTextSelectionActionsState? = null,
    val downloadPrompt: BrowserDownloadPromptState? = null,
    val downloadUiState: BrowserDownloadUiState = BrowserDownloadUiState(),
    val viewportWidthPx: Int? = null,
    val viewportHeightPx: Int? = null,
    val chromeHeightPx: Int = 0,
    val browserAreaWidthPx: Int = 0,
    val browserAreaHeightPx: Int = 0
)

internal enum class WebSessionBrowserBackAction {
    DISMISS_TEXT_SELECTION,
    DISMISS_PENDING_DIALOG,
    CANCEL_DOWNLOAD_PROMPT,
    SHOW_PLUGIN_OVERVIEW,
    CLOSE_SHEET,
    CLOSE_SEARCH_ENGINE_PANEL,
    CLOSE_SEARCH,
    NAVIGATE_WEB_HISTORY,
    EXIT_BROWSER,
}

internal fun resolveWebSessionBrowserBackAction(
    state: WebSessionBrowserHostState,
): WebSessionBrowserBackAction =
    when {
        state.textSelectionActions != null ->
            WebSessionBrowserBackAction.DISMISS_TEXT_SELECTION
        state.browserState.pendingDialog != null ->
            WebSessionBrowserBackAction.DISMISS_PENDING_DIALOG
        state.downloadPrompt != null ->
            WebSessionBrowserBackAction.CANCEL_DOWNLOAD_PROMPT
        state.sheetRoute == WebSessionBrowserSheetRoute.PLUGINS &&
            state.pluginPage == WebSessionBrowserPluginPage.USERSCRIPTS ->
            WebSessionBrowserBackAction.SHOW_PLUGIN_OVERVIEW
        state.sheetRoute != WebSessionBrowserSheetRoute.NONE ->
            WebSessionBrowserBackAction.CLOSE_SHEET
        state.isSearchEnginePanelVisible ->
            WebSessionBrowserBackAction.CLOSE_SEARCH_ENGINE_PANEL
        state.isSearchVisible ->
            WebSessionBrowserBackAction.CLOSE_SEARCH
        state.browserState.canGoBack ->
            WebSessionBrowserBackAction.NAVIGATE_WEB_HISTORY
        else ->
            WebSessionBrowserBackAction.EXIT_BROWSER
    }

@Serializable
internal data class WebSessionBookmark(
    val url: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val id: Long = createdAt,
    val iconUrl: String = "",
    val folderId: Long? = null,
    val order: Long = createdAt,
    val secret: Boolean = false,
)

@Serializable
internal data class WebSessionBookmarkFolder(
    val id: Long,
    val title: String,
    val parentId: Long? = null,
    val createdAt: Long,
    val order: Long = createdAt,
    val secret: Boolean = false,
)

@Serializable
internal data class WebSessionHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long,
    val category: WebSessionHistoryCategory = WebSessionHistoryCategory.WEB,
    val mediaOrigin: WebSessionHistoryMediaOrigin? = null,
    val sourcePageUrl: String = "",
)
