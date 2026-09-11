package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageMenuCommand
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
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
    DIAGNOSTICS,
    MEDIA_CANDIDATES,
    SITE_CONFIG,
    PAGE_SOURCE,
    TOOLBOX,
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
    data object CookieReader : WebSessionBrowserPluginRoute

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

@Immutable
internal data class BrowserCookieUiState(
    val targetUrl: String = "",
    val header: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Long? = null,
)

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
    val kind: BrowserNetworkLogEntryKind = BrowserNetworkLogEntryKind.REQUEST,
    val mediaCandidateId: String? = null,
    val blocked: Boolean = false,
    val blockingRule: String? = null,
    val blockingSourceName: String? = null,
    val elementSelector: String? = null,
    val documentToken: String? = null,
    val resourceIdentity: String = url,
    val requestCount: Int = 1,
    val firstSeenAt: Long = timestamp,
    val lastSeenAt: Long = timestamp,
    val requestHeaders: Map<String, String> = emptyMap(),
)

@Immutable
internal data class WebSessionBrowserMediaCandidate(
    val id: String,
    val url: String,
    val pageUrl: String,
    val documentToken: String = "",
    val mimeType: String?,
    val urlEvidence: BrowserMediaCandidateUrlEvidence,
    val videoFormat: BrowserMediaCandidateVideoFormat,
    val mediaKind: BrowserMediaKind = BrowserMediaKind.VIDEO,
    val discoverySources: Set<BrowserMediaCandidateDiscoverySource>,
    val firstDiscoveredAt: Long,
    val lastDiscoveredAt: Long,
    val durationMillis: Long?,
    val isLive: Boolean,
    val qualityHeight: Int?,
    val qualityLabel: String?,
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
    val engineId: String = WebSessionSearchEngine.DEFAULT.id,
    val source: KiyoriBrowserSearchSource = KiyoriBrowserSearchSource.BROWSER_HOME,
)

@Immutable
internal data class WebSessionPageSourceState(
    val sessionId: String? = null,
    val pageUrl: String = "",
    val pageTitle: String = "",
    val documentToken: String? = null,
    val applySupported: Boolean = true,
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val baselineContent: String? = null,
    val content: String? = null,
    val error: String? = null,
    val statusMessage: String? = null,
    val isRetainedEdit: Boolean = false,
    val exitPromptVisible: Boolean = false,
) {
    val hasChanges: Boolean
        get() = baselineContent != null && content != null && baselineContent != content
}

@Immutable
internal data class WebSessionTextSelectionActionsState(
    val anchorXPx: Int,
    val anchorYPx: Int,
)

@Immutable
internal data class WebSessionWebElementActionState(
    val sessionId: String,
    val pageUrl: String,
    val tagName: String,
    val text: String,
    val linkUrl: String?,
    val resourceUrl: String?,
    val resourceKind: BrowserWebElementResourceKind,
    val selector: String,
    val html: String,
    val clientX: Double,
    val clientY: Double,
)

internal enum class BrowserQrCodeUiStatus {
    LOADING,
    SUCCESS,
    IMAGE_LOAD_FAILED,
    NOT_RECOGNIZED,
}

@Immutable
internal data class WebSessionQrCodeState(
    val sourceUrl: String,
    val status: BrowserQrCodeUiStatus = BrowserQrCodeUiStatus.LOADING,
    val content: String = "",
)

internal enum class BrowserAdMarkingMove {
    PARENT,
    PREVIOUS_SIBLING,
    NEXT_SIBLING,
    FIRST_CHILD,
}

internal enum class WebSessionAdMarkingOverlay {
    NONE,
    EDIT_RULE,
    CLEAR_CONFIRM,
    NAVIGATION_POLICY,
}

internal enum class BrowserAdMarkingNavigationPolicy {
    DEFAULT,
    ASK,
    BLOCK,
}

internal fun browserAdMarkingSelectablePolicies(): List<BrowserAdMarkingNavigationPolicy> =
    listOf(
        BrowserAdMarkingNavigationPolicy.DEFAULT,
        BrowserAdMarkingNavigationPolicy.ASK,
        BrowserAdMarkingNavigationPolicy.BLOCK,
    )

internal fun BrowserAdMarkingNavigationPolicy.toJavascriptValue(): String =
    when (this) {
        BrowserAdMarkingNavigationPolicy.DEFAULT -> "allow"
        BrowserAdMarkingNavigationPolicy.ASK -> "ask"
        BrowserAdMarkingNavigationPolicy.BLOCK -> "block"
    }

@Immutable
internal data class WebSessionAdMarkingNavigationRequest(
    val url: String,
    val text: String,
)

@Immutable
internal data class WebSessionAdMarkingState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val pageUrl: String = "",
    val domain: String = "",
    val tagName: String = "",
    val text: String = "",
    val selector: String = "",
    val html: String = "",
    val previewing: Boolean = false,
    val ruleDraft: String = "",
    val navigationPolicy: BrowserAdMarkingNavigationPolicy = BrowserAdMarkingNavigationPolicy.DEFAULT,
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
    val activeDocumentToken: String = "",
    val automaticFloatingConsumedDocumentToken: String? = null,
    val activeProfile: WebSessionProfile? = null,
    val defaultSessionProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val incognitoAvailability: WebSessionIncognitoAvailability =
        WebSessionIncognitoAvailability.UNSUPPORTED,
    val pageTitle: String = "",
    val currentUrl: String = "about:blank",
    val homeMode: BrowserHomeMode = BrowserHomeMode.BLANK,
    val externalNavigationPolicy: BrowserAdMarkingNavigationPolicy =
        BrowserAdMarkingNavigationPolicy.DEFAULT,
    val canGoBack: Boolean = false,
    val canReturnToHome: Boolean = false,
    val canGoForward: Boolean = false,
    val pageLoaded: Boolean = false,
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
    val diagnosticEntries: List<BrowserDiagnosticEntry> = emptyList(),
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
internal data class BrowserSearchRecoveryProjectionKey(
    val query: String,
    val engineId: String,
    val source: KiyoriBrowserSearchSource,
    val requestedUrl: String,
    val submittedAt: Long,
)

@Immutable
internal data class WebSessionBrowserHostState(
    val browserState: WebSessionBrowserState = WebSessionBrowserState(),
    val sheetRoute: WebSessionBrowserSheetRoute = WebSessionBrowserSheetRoute.NONE,
    val pluginRouteStack: List<WebSessionBrowserPluginRoute> =
        listOf(WebSessionBrowserPluginRoute.Overview),
    val pluginEditorExitPromptDraftId: String? = null,
    val cookieState: BrowserCookieUiState = BrowserCookieUiState(),
    val selectedProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val siteConfigDomain: String? = null,
    val isSearchVisible: Boolean = false,
    val isSearchEnginePanelVisible: Boolean = false,
    val searchDraft: String = "",
    val searchProfile: WebSessionProfile = WebSessionProfile.NORMAL,
    val lastSearchQuery: String = "",
    val isSearchEngineQuickSwitchBarVisible: Boolean = false,
    val projectedSearchRecoverySessionId: String? = null,
    val projectedSearchRecoveryKey: BrowserSearchRecoveryProjectionKey? = null,
    val projectedSearchRecoveryPageIsResult: Boolean = false,
    val pageSource: WebSessionPageSourceState = WebSessionPageSourceState(),
    val textSelectionActions: WebSessionTextSelectionActionsState? = null,
    val webElementAction: WebSessionWebElementActionState? = null,
    val imageViewer: BrowserImageViewerSnapshot? = null,
    val qrCode: WebSessionQrCodeState? = null,
    val adMarking: WebSessionAdMarkingState = WebSessionAdMarkingState(),
    val adMarkingOverlay: WebSessionAdMarkingOverlay = WebSessionAdMarkingOverlay.NONE,
    val adMarkingNavigationRequest: WebSessionAdMarkingNavigationRequest? = null,
    val downloadPrompt: BrowserDownloadPromptState? = null,
    val downloadUiState: BrowserDownloadUiState = BrowserDownloadUiState(),
    val viewportWidthCssPx: Int? = null,
    val viewportHeightCssPx: Int? = null,
    val chromeHeightPx: Int = 0,
    val browserAreaWidthPx: Int = 0,
    val browserAreaHeightPx: Int = 0
)

internal enum class WebSessionBrowserBackAction {
    DISMISS_IMAGE_VIEWER,
    DISMISS_QR_CODE,
    DISMISS_AD_MARKING_NAVIGATION_REQUEST,
    DISMISS_AD_MARKING_OVERLAY,
    EXIT_AD_MARKING,
    DISMISS_WEB_ELEMENT_ACTION,
    DISMISS_TEXT_SELECTION,
    DISMISS_PENDING_DIALOG,
    CANCEL_DOWNLOAD_PROMPT,
    DISMISS_PAGE_SOURCE_EXIT_PROMPT,
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
        state.imageViewer != null ->
            WebSessionBrowserBackAction.DISMISS_IMAGE_VIEWER
        state.qrCode != null ->
            WebSessionBrowserBackAction.DISMISS_QR_CODE
        state.adMarkingNavigationRequest != null ->
            WebSessionBrowserBackAction.DISMISS_AD_MARKING_NAVIGATION_REQUEST
        state.pageSource.exitPromptVisible ->
            WebSessionBrowserBackAction.DISMISS_PAGE_SOURCE_EXIT_PROMPT
        state.sheetRoute == WebSessionBrowserSheetRoute.PAGE_SOURCE ->
            WebSessionBrowserBackAction.CLOSE_SHEET
        state.adMarkingOverlay != WebSessionAdMarkingOverlay.NONE ->
            WebSessionBrowserBackAction.DISMISS_AD_MARKING_OVERLAY
        state.adMarking.active ->
            WebSessionBrowserBackAction.EXIT_AD_MARKING
        state.webElementAction != null ->
            WebSessionBrowserBackAction.DISMISS_WEB_ELEMENT_ACTION
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
        WebSessionBrowserPluginRoute.CookieReader ->
            listOf(
                WebSessionBrowserPluginRoute.Overview,
                WebSessionBrowserPluginRoute.CookieReader,
            )
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
