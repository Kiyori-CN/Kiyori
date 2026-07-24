package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

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
}

@Immutable
internal data class WebSessionBrowserTab(
    val sessionId: String,
    val title: String,
    val url: String,
    val isActive: Boolean,
    val hasSslError: Boolean
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
    val pageTitle: String = "",
    val currentUrl: String = "about:blank",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val hasSslError: Boolean = false,
    val isDesktopMode: Boolean = true,
    val userAgent: String = "",
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

internal enum class BrowserDownloadFilter {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

@Immutable
internal data class BrowserDownloadItem(
    val id: String,
    val fileName: String,
    val status: String,
    val type: String,
    val progress: Float?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val destinationPath: String,
    val errorMessage: String?,
    val canPause: Boolean,
    val canResume: Boolean,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val canDelete: Boolean,
    val canDeleteFile: Boolean,
    val canOpenFile: Boolean,
    val canOpenLocation: Boolean
)

@Immutable
internal data class BrowserDownloadUiState(
    val tasks: List<BrowserDownloadItem> = emptyList(),
    val selectedFilter: BrowserDownloadFilter = BrowserDownloadFilter.IN_PROGRESS
)

@Immutable
internal data class ExternalOpenPromptState(
    val requestId: String,
    val title: String,
    val target: String
)

@Immutable
internal data class WebSessionBrowserHostState(
    val browserState: WebSessionBrowserState = WebSessionBrowserState(),
    val sheetRoute: WebSessionBrowserSheetRoute = WebSessionBrowserSheetRoute.NONE,
    val isSearchVisible: Boolean = false,
    val isSearchEnginePanelVisible: Boolean = false,
    val searchDraft: String = "",
    val pageSource: WebSessionPageSourceState = WebSessionPageSourceState(),
    val externalOpenPrompt: ExternalOpenPromptState? = null,
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
    val updatedAt: Long
)

@Serializable
internal data class WebSessionHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long
)
