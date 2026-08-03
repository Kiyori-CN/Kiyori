package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import java.net.URI
import java.util.Locale
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

@Immutable
internal enum class WebSessionUserscriptWorkbenchTab {
    CURRENT_PAGE,
    INSTALLED,
    UPDATES,
    LOGS,
}

@Immutable
internal sealed interface WebSessionBrowserPluginRoute {
    @Immutable
    data object Overview : WebSessionBrowserPluginRoute

    @Immutable
    data class Userscripts(
        val initialTab: WebSessionUserscriptWorkbenchTab = WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
        val initialSearchQuery: String = "",
    ) : WebSessionBrowserPluginRoute

    @Immutable
    data class UserscriptDetail(
        val scriptId: Long,
        val returnTab: WebSessionUserscriptWorkbenchTab =
            WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
    ) : WebSessionBrowserPluginRoute

    @Immutable
    data class UserscriptEditor(
        val draftId: String,
        val scriptId: Long?,
    ) : WebSessionBrowserPluginRoute
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
internal data class BrowserHomeNavigationState(
    val rootRequestedUrl: String? = null,
    val rootResolvedUrl: String? = null,
    val pendingRequestedUrl: String? = null,
) {
    fun begin(requestedUrl: String): BrowserHomeNavigationState =
        copy(pendingRequestedUrl = requestedUrl)

    fun cancelPending(): BrowserHomeNavigationState =
        copy(pendingRequestedUrl = null)

    fun complete(resolvedUrl: String): BrowserHomeNavigationState {
        val requestedUrl = pendingRequestedUrl ?: return this
        return BrowserHomeNavigationState(
            rootRequestedUrl = requestedUrl,
            rootResolvedUrl = resolvedUrl,
        )
    }
}

private data class BrowserHomeUrlIdentity(
    val scheme: String,
    val userInfo: String?,
    val host: String?,
    val port: Int,
    val path: String,
    val query: String?,
    val fragment: String?,
)

internal fun areBrowserHomeUrlsEquivalent(
    first: String?,
    second: String?,
): Boolean {
    val firstValue = first?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val secondValue = second?.trim()?.takeIf(String::isNotEmpty) ?: return false
    if (
        firstValue.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true) ||
            secondValue.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true)
    ) {
        return firstValue.equals(secondValue, ignoreCase = true)
    }
    val firstIdentity = browserHomeUrlIdentity(firstValue)
    val secondIdentity = browserHomeUrlIdentity(secondValue)
    return if (firstIdentity != null && secondIdentity != null) {
        firstIdentity == secondIdentity
    } else {
        firstValue == secondValue
    }
}

private fun browserHomeUrlIdentity(url: String): BrowserHomeUrlIdentity? {
    val uri = runCatching { URI(url).normalize() }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "http" && scheme != "https") return null
    val normalizedPort =
        when {
            uri.port < 0 -> -1
            scheme == "http" && uri.port == 80 -> -1
            scheme == "https" && uri.port == 443 -> -1
            else -> uri.port
        }
    return BrowserHomeUrlIdentity(
        scheme = scheme,
        userInfo = uri.rawUserInfo,
        host = uri.host?.lowercase(Locale.ROOT),
        port = normalizedPort,
        path = uri.rawPath.orEmpty().ifEmpty { "/" },
        query = uri.rawQuery,
        fragment = uri.rawFragment,
    )
}

internal fun isAtConfiguredBrowserHome(
    currentUrl: String,
    configuredHomeUrl: String,
    navigationState: BrowserHomeNavigationState,
): Boolean {
    if (areBrowserHomeUrlsEquivalent(currentUrl, configuredHomeUrl)) {
        return true
    }
    if (
        areBrowserHomeUrlsEquivalent(
            navigationState.pendingRequestedUrl,
            configuredHomeUrl,
        )
    ) {
        return true
    }
    return areBrowserHomeUrlsEquivalent(
        navigationState.rootRequestedUrl,
        configuredHomeUrl,
    ) &&
        areBrowserHomeUrlsEquivalent(
            currentUrl,
            navigationState.rootResolvedUrl,
        )
}

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
    val canReturnToHome: Boolean = false,
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
    val pluginRouteStack: List<WebSessionBrowserPluginRoute> =
        listOf(WebSessionBrowserPluginRoute.Overview),
    val pluginEditorExitPromptDraftId: String? = null,
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
    DISMISS_PLUGIN_EDITOR_EXIT_PROMPT,
    POP_PLUGIN_ROUTE,
    CLOSE_SHEET,
    CLOSE_SEARCH_ENGINE_PANEL,
    CLOSE_SEARCH,
    NAVIGATE_WEB_HISTORY,
    RETURN_TO_HOME,
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
        state.pluginEditorExitPromptDraftId != null ->
            WebSessionBrowserBackAction.DISMISS_PLUGIN_EDITOR_EXIT_PROMPT
        state.sheetRoute == WebSessionBrowserSheetRoute.PLUGINS &&
            state.pluginRouteStack.size > 1 ->
            WebSessionBrowserBackAction.POP_PLUGIN_ROUTE
        state.sheetRoute != WebSessionBrowserSheetRoute.NONE ->
            WebSessionBrowserBackAction.CLOSE_SHEET
        state.isSearchEnginePanelVisible ->
            WebSessionBrowserBackAction.CLOSE_SEARCH_ENGINE_PANEL
        state.isSearchVisible ->
            WebSessionBrowserBackAction.CLOSE_SEARCH
        state.browserState.canGoBack ->
            WebSessionBrowserBackAction.NAVIGATE_WEB_HISTORY
        state.browserState.canReturnToHome ->
            WebSessionBrowserBackAction.RETURN_TO_HOME
        else ->
            WebSessionBrowserBackAction.EXIT_BROWSER
    }

internal val WebSessionBrowserHostState.currentPluginRoute: WebSessionBrowserPluginRoute
    get() = pluginRouteStack.lastOrNull() ?: WebSessionBrowserPluginRoute.Overview

internal fun browserPluginRouteStackFor(
    route: WebSessionBrowserPluginRoute,
): List<WebSessionBrowserPluginRoute> =
    when (route) {
        WebSessionBrowserPluginRoute.Overview ->
            listOf(WebSessionBrowserPluginRoute.Overview)
        is WebSessionBrowserPluginRoute.Userscripts ->
            listOf(
                WebSessionBrowserPluginRoute.Overview,
                route,
            )
        is WebSessionBrowserPluginRoute.UserscriptDetail ->
            listOf(
                WebSessionBrowserPluginRoute.Overview,
                WebSessionBrowserPluginRoute.Userscripts(route.returnTab),
                route,
            )
        is WebSessionBrowserPluginRoute.UserscriptEditor ->
            buildList {
                add(WebSessionBrowserPluginRoute.Overview)
                add(WebSessionBrowserPluginRoute.Userscripts())
                route.scriptId?.let { scriptId ->
                    add(WebSessionBrowserPluginRoute.UserscriptDetail(scriptId))
                }
                add(route)
            }
    }

internal fun pushBrowserPluginRoute(
    stack: List<WebSessionBrowserPluginRoute>,
    route: WebSessionBrowserPluginRoute,
): List<WebSessionBrowserPluginRoute> {
    val normalized =
        stack.takeIf(List<WebSessionBrowserPluginRoute>::isNotEmpty)
            ?: listOf(WebSessionBrowserPluginRoute.Overview)
    return if (normalized.last() == route) {
        normalized
    } else {
        normalized + route
    }
}

internal fun popBrowserPluginRoute(
    stack: List<WebSessionBrowserPluginRoute>,
): List<WebSessionBrowserPluginRoute> =
    if (stack.size <= 1) {
        listOf(WebSessionBrowserPluginRoute.Overview)
    } else {
        stack.dropLast(1)
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
