package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import kotlinx.serialization.Serializable

internal const val BROWSER_SESSION_RECOVERY_SCHEMA_VERSION = 1
internal const val MAX_BROWSER_RECOVERY_WINDOWS = 64
internal const val MAX_BROWSER_RECOVERY_URL_LENGTH = 8_192
internal const val MAX_BROWSER_RECOVERY_TITLE_LENGTH = 512
internal const val MAX_BROWSER_RECOVERY_QUERY_LENGTH = 2_048

@Serializable
internal data class BrowserSessionSearchRecovery(
    val query: String,
    val engineId: String,
    val source: KiyoriBrowserSearchSource,
    val requestedUrl: String,
    val resolvedResultUrl: String,
    val submittedAt: Long,
)

@Serializable
internal data class BrowserSessionRecoveryWindow(
    val windowId: String,
    val currentUrl: String,
    val title: String,
    val createdAt: Long,
    val lastActivatedAt: Long,
    val creationReason: BrowserWindowCreationReason,
    val openerHomeWindowId: String? = null,
    val lastSearch: BrowserSessionSearchRecovery? = null,
)

@Serializable
internal data class BrowserSessionRecoverySnapshot(
    val schemaVersion: Int = BROWSER_SESSION_RECOVERY_SCHEMA_VERSION,
    val snapshotId: String,
    val capturedAt: Long,
    val activeWindowId: String,
    val windows: List<BrowserSessionRecoveryWindow>,
)

@Serializable
internal data class BrowserSessionRecoveryEnvelope(
    val snapshot: BrowserSessionRecoverySnapshot? = null,
)

internal data class BrowserRecoverySettings(
    val restoreLastSearchResultEnabled: Boolean = false,
    val askBeforeRestoringPagesEnabled: Boolean = false,
    val retainMultipleWindowsEnabled: Boolean = false,
)

internal data class BrowserSessionRecoveryCandidate(
    val profile: WebSessionProfile,
    val window: BrowserSessionRecoveryWindow,
)

internal fun shouldPersistBrowserRecovery(settings: BrowserRecoverySettings): Boolean =
    settings.restoreLastSearchResultEnabled ||
        settings.askBeforeRestoringPagesEnabled ||
        settings.retainMultipleWindowsEnabled

internal fun buildBrowserSessionRecoverySnapshot(
    settings: BrowserRecoverySettings,
    snapshotId: String,
    capturedAt: Long,
    activeWindowId: String?,
    candidates: List<BrowserSessionRecoveryCandidate>,
): BrowserSessionRecoverySnapshot? {
    if (!shouldPersistBrowserRecovery(settings)) {
        return null
    }
    val normalWindows =
        candidates
            .asSequence()
            .filter { candidate -> candidate.profile == WebSessionProfile.NORMAL }
            .map(BrowserSessionRecoveryCandidate::window)
            .filter { window ->
                browserSiteIdentity(window.currentUrl) != null ||
                    window.currentUrl.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true)
            }
            .toList()
    if (normalWindows.isEmpty()) {
        return null
    }
    val activeNormalWindow =
        normalWindows.singleOrNull { window -> window.windowId == activeWindowId }
            ?: normalWindows.maxBy(BrowserSessionRecoveryWindow::lastActivatedAt)
    val projectedWindows =
        when {
            settings.retainMultipleWindowsEnabled -> normalWindows
            settings.restoreLastSearchResultEnabled -> {
                val searchWindow =
                    normalWindows
                        .filter { window -> window.lastSearch != null }
                        .maxByOrNull { window ->
                            checkNotNull(window.lastSearch).submittedAt
                        }
                when {
                    searchWindow != null -> listOf(searchWindow)
                    settings.askBeforeRestoringPagesEnabled -> listOf(activeNormalWindow)
                    else -> emptyList()
                }
            }
            settings.askBeforeRestoringPagesEnabled -> listOf(activeNormalWindow)
            else -> emptyList()
        }
    if (projectedWindows.isEmpty()) {
        return null
    }
    val projectedIds =
        projectedWindows.mapTo(mutableSetOf(), BrowserSessionRecoveryWindow::windowId)
    val normalizedWindows =
        projectedWindows.map { window ->
            if (window.openerHomeWindowId in projectedIds) {
                window
            } else {
                window.copy(openerHomeWindowId = null)
            }
        }
    val projectedActiveWindowId =
        activeNormalWindow.windowId.takeIf(projectedIds::contains)
            ?: normalizedWindows.single().windowId
    return BrowserSessionRecoverySnapshot(
        snapshotId = snapshotId,
        capturedAt = capturedAt,
        activeWindowId = projectedActiveWindowId,
        windows = normalizedWindows,
    ).also(::requireBrowserSessionRecoverySnapshot)
}

internal sealed interface BrowserLaunchRestorationPlan {
    data object OpenConfiguredHome : BrowserLaunchRestorationPlan

    data class RestoreSearchResult(
        val window: BrowserSessionRecoveryWindow,
        val search: BrowserSessionSearchRecovery,
        val requiresConfirmation: Boolean,
    ) : BrowserLaunchRestorationPlan

    data class RestoreActivePage(
        val window: BrowserSessionRecoveryWindow,
    ) : BrowserLaunchRestorationPlan

    data class RestoreAllWindows(
        val snapshot: BrowserSessionRecoverySnapshot,
        val requiresConfirmation: Boolean,
    ) : BrowserLaunchRestorationPlan
}

internal sealed interface BrowserLaunchRestorationState {
    data object Uninitialized : BrowserLaunchRestorationState

    data class WaitingForDecision(
        val plan: BrowserLaunchRestorationPlan,
        val snapshotId: String,
    ) : BrowserLaunchRestorationState

    data class Applying(
        val snapshotId: String,
    ) : BrowserLaunchRestorationState

    data object Ready : BrowserLaunchRestorationState
}

internal data class BrowserLaunchRestorationPrompt(
    val snapshotId: String,
    val title: String,
    val summary: String,
)

internal fun resolveBrowserLaunchRestorationPlan(
    settings: BrowserRecoverySettings,
    snapshot: BrowserSessionRecoverySnapshot?,
): BrowserLaunchRestorationPlan {
    if (!shouldPersistBrowserRecovery(settings) || snapshot == null) {
        return BrowserLaunchRestorationPlan.OpenConfiguredHome
    }
    requireBrowserSessionRecoverySnapshot(snapshot)
    if (settings.retainMultipleWindowsEnabled) {
        return BrowserLaunchRestorationPlan.RestoreAllWindows(
            snapshot = snapshot,
            requiresConfirmation = settings.askBeforeRestoringPagesEnabled,
        )
    }
    if (settings.restoreLastSearchResultEnabled) {
        val searchWindow =
            snapshot.windows
                .mapNotNull { window -> window.lastSearch?.let { search -> window to search } }
                .maxByOrNull { (_, search) -> search.submittedAt }
        if (searchWindow != null) {
            return BrowserLaunchRestorationPlan.RestoreSearchResult(
                window = searchWindow.first,
                search = searchWindow.second,
                requiresConfirmation = settings.askBeforeRestoringPagesEnabled,
            )
        }
    }
    if (settings.askBeforeRestoringPagesEnabled) {
        val activeWindow =
            snapshot.windows.single { window -> window.windowId == snapshot.activeWindowId }
        return BrowserLaunchRestorationPlan.RestoreActivePage(activeWindow)
    }
    return BrowserLaunchRestorationPlan.OpenConfiguredHome
}

internal fun requireBrowserSessionRecoverySnapshot(
    snapshot: BrowserSessionRecoverySnapshot,
) {
    require(snapshot.schemaVersion == BROWSER_SESSION_RECOVERY_SCHEMA_VERSION) {
        "Unsupported browser recovery schema version: ${snapshot.schemaVersion}"
    }
    require(snapshot.snapshotId.isNotBlank()) {
        "Browser recovery snapshot id must not be blank"
    }
    require(snapshot.capturedAt > 0L) {
        "Browser recovery capture time must be positive"
    }
    require(snapshot.windows.isNotEmpty()) {
        "Browser recovery snapshot must contain at least one normal window"
    }
    require(snapshot.windows.size <= MAX_BROWSER_RECOVERY_WINDOWS) {
        "Browser recovery snapshot contains too many windows"
    }
    val windowIds = snapshot.windows.map(BrowserSessionRecoveryWindow::windowId)
    require(windowIds.all(String::isNotBlank)) {
        "Browser recovery window id must not be blank"
    }
    require(windowIds.distinct().size == windowIds.size) {
        "Browser recovery window ids must be unique"
    }
    require(snapshot.activeWindowId in windowIds) {
        "Browser recovery active window must exist in the window list"
    }
    snapshot.windows.forEach { window ->
        require(window.currentUrl.length in 1..MAX_BROWSER_RECOVERY_URL_LENGTH) {
            "Browser recovery URL length is invalid for window ${window.windowId}"
        }
        require(browserSiteIdentity(window.currentUrl) != null ||
            window.currentUrl.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true)) {
            "Browser recovery URL is unsupported for window ${window.windowId}"
        }
        require(window.title.length <= MAX_BROWSER_RECOVERY_TITLE_LENGTH) {
            "Browser recovery title is too long for window ${window.windowId}"
        }
        require(window.createdAt > 0L && window.lastActivatedAt >= window.createdAt) {
            "Browser recovery timestamps are invalid for window ${window.windowId}"
        }
        require(window.creationReason != BrowserWindowCreationReason.AI_EXPLICIT_CREATE ||
            window.openerHomeWindowId == null) {
            "AI-created windows cannot carry a home opener relation"
        }
        window.openerHomeWindowId?.let { openerId ->
            require(window.creationReason ==
                BrowserWindowCreationReason.HOME_CROSS_SITE_USER_NAVIGATION) {
                "Only home cross-site windows may carry an opener relation"
            }
            require(openerId in windowIds && openerId != window.windowId) {
                "Browser recovery opener must refer to another retained window"
            }
        }
        window.lastSearch?.let { search ->
            require(search.query.length in 1..MAX_BROWSER_RECOVERY_QUERY_LENGTH) {
                "Browser recovery search query length is invalid"
            }
            require(search.engineId.isNotBlank()) {
                "Browser recovery search engine id must not be blank"
            }
            require(browserSiteIdentity(search.requestedUrl) != null) {
                "Browser recovery requested search URL is unsupported"
            }
            require(browserSiteIdentity(search.resolvedResultUrl) != null) {
                "Browser recovery resolved search URL is unsupported"
            }
            require(search.submittedAt > 0L) {
                "Browser recovery search submission time must be positive"
            }
        }
    }
    snapshot.windows.forEach { start ->
        val visited = mutableSetOf<String>()
        var current: BrowserSessionRecoveryWindow? = start
        while (current?.openerHomeWindowId != null) {
            require(visited.add(current.windowId)) {
                "Browser recovery opener relation contains a cycle"
            }
            val openerId = current.openerHomeWindowId
            current = snapshot.windows.single { candidate -> candidate.windowId == openerId }
        }
    }
}
