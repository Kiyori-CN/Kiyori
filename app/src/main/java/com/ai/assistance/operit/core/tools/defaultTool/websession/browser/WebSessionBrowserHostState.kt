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
    USERSCRIPTS,
    USER_AGENT,
    NETWORK_LOG,
    PAGE_SOURCE,
    PLACEHOLDER,
}

internal enum class WebSessionBrowserPlaceholderPage {
    FLOATING_SNIFFER,
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
internal data class WebSessionSessionHistoryItem(
    val index: Int,
    val title: String,
    val url: String,
    val isCurrent: Boolean
)

@Immutable
internal data class WebSessionBrowserNetworkEntry(
    val method: String,
    val url: String,
    val isMainFrame: Boolean,
    val isStatic: Boolean,
    val category: BrowserNetworkRequestCategory,
    val timestamp: Long,
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
    val sessionHistory: List<WebSessionSessionHistoryItem> = emptyList(),
    val userscriptMenuCommands: List<UserscriptPageMenuCommand> = emptyList(),
    val networkEntries: List<WebSessionBrowserNetworkEntry> = emptyList(),
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
internal data class ExternalOpenPromptState(
    val requestId: String,
    val title: String,
    val target: String
)

@Immutable
internal data class BrowserDownloadPromptState(
    val requestId: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long,
    val engine: BrowserDownloadEngine,
)

@Immutable
internal data class WebSessionBrowserHostState(
    val browserState: WebSessionBrowserState = WebSessionBrowserState(),
    val sheetRoute: WebSessionBrowserSheetRoute = WebSessionBrowserSheetRoute.NONE,
    val selectedProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val placeholderPage: WebSessionBrowserPlaceholderPage? = null,
    val isSearchVisible: Boolean = false,
    val isSearchEnginePanelVisible: Boolean = false,
    val searchDraft: String = "",
    val searchProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val pageSource: WebSessionPageSourceState = WebSessionPageSourceState(),
    val externalOpenPrompt: ExternalOpenPromptState? = null,
    val downloadPrompt: BrowserDownloadPromptState? = null,
    val downloadUiState: BrowserDownloadUiState = BrowserDownloadUiState(),
    val viewportWidthPx: Int? = null,
    val viewportHeightPx: Int? = null,
    val chromeHeightPx: Int = 0,
    val browserAreaWidthPx: Int = 0,
    val browserAreaHeightPx: Int = 0
)

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
    val visitedAt: Long
)
