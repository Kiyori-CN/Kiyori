package com.kiyori.app.shell

import androidx.compose.runtime.saveable.listSaver
import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation

enum class PrimaryDestination {
    SOFTWARE_HOME,
    BROWSER_HOME,
    MINI_APP_HOME,
    FILE_MANAGEMENT_HOME,
    SETTINGS_HOME,
}

enum class KiyoriBrowserReturnTarget {
    SOFTWARE_HOME,
    AI_HOME,
}

enum class SoftwareHomePage(val pagerIndex: Int) {
    MINUS_ONE(0),
    HOME(1),
    AI_HOME(2),
    ;

    companion object {
        fun fromPagerIndex(index: Int): SoftwareHomePage =
            entries.single { page -> page.pagerIndex == index }
    }
}

enum class KiyoriShellChild {
    FULL_SCREEN_WEB_SEARCH,
    BROWSER_SETTINGS,
    DOWNLOAD_SETTINGS,
    PLAYER_SETTINGS,
}

enum class KiyoriShellExternalDestination {
    BROWSER_HOME,
    BROWSER_HOME_FROM_MINIMIZED_INDICATOR,
    DOWNLOADS,
    BROWSER_SETTINGS,
    DOWNLOAD_SETTINGS,
}

enum class KiyoriShellBackResult {
    CONSUMED,
    REQUEST_EXIT,
}

data class KiyoriShellBackTransition(
    val state: KiyoriShellState,
    val result: KiyoriShellBackResult,
)

data class KiyoriShellState(
    val primaryDestination: PrimaryDestination = PrimaryDestination.SOFTWARE_HOME,
    val softwareHomePage: SoftwareHomePage = SoftwareHomePage.HOME,
    val child: KiyoriShellChild? = null,
    val childBackTarget: KiyoriShellChild? = null,
    val isAiDrawerOpen: Boolean = false,
    val isBookmarkDrawerOpen: Boolean = false,
    val isHistoryDrawerOpen: Boolean = false,
    val isDownloadDrawerOpen: Boolean = false,
    val browserReturnTarget: KiyoriBrowserReturnTarget? = null,
    val browserExitPresentation: KiyoriBrowserExitPresentation =
        KiyoriBrowserExitPresentation.CLOSE,
) {
    val showsBottomBar: Boolean
        get() =
            child == null && !isAiDrawerOpen && !isBookmarkDrawerOpen && !isHistoryDrawerOpen &&
                !isDownloadDrawerOpen &&
                primaryDestination != PrimaryDestination.BROWSER_HOME &&
                (primaryDestination != PrimaryDestination.SOFTWARE_HOME ||
                    softwareHomePage == SoftwareHomePage.HOME)

    fun selectPrimary(destination: PrimaryDestination): KiyoriShellState =
        copy(
            primaryDestination = destination,
            softwareHomePage =
                if (destination == PrimaryDestination.SOFTWARE_HOME) {
                    SoftwareHomePage.HOME
                } else {
                    softwareHomePage
                },
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
            browserReturnTarget = null,
            browserExitPresentation = KiyoriBrowserExitPresentation.CLOSE,
        )

    fun openBrowser(
        returnTarget: KiyoriBrowserReturnTarget,
        exitPresentation: KiyoriBrowserExitPresentation = KiyoriBrowserExitPresentation.CLOSE,
    ): KiyoriShellState =
        copy(
            primaryDestination = PrimaryDestination.BROWSER_HOME,
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
            browserReturnTarget = returnTarget,
            browserExitPresentation = exitPresentation,
        )

    fun exitBrowser(): KiyoriShellState =
        when (browserReturnTarget) {
            KiyoriBrowserReturnTarget.AI_HOME ->
                copy(
                    primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                    softwareHomePage = SoftwareHomePage.AI_HOME,
                    child = null,
                    childBackTarget = null,
                    isAiDrawerOpen = false,
                    isBookmarkDrawerOpen = false,
                    isHistoryDrawerOpen = false,
                    isDownloadDrawerOpen = false,
                    browserReturnTarget = null,
                    browserExitPresentation = KiyoriBrowserExitPresentation.CLOSE,
                )
            KiyoriBrowserReturnTarget.SOFTWARE_HOME,
            null ->
                copy(
                    primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                    softwareHomePage = SoftwareHomePage.HOME,
                    child = null,
                    childBackTarget = null,
                    isAiDrawerOpen = false,
                    isBookmarkDrawerOpen = false,
                    isHistoryDrawerOpen = false,
                    isDownloadDrawerOpen = false,
                    browserReturnTarget = null,
                    browserExitPresentation = KiyoriBrowserExitPresentation.CLOSE,
                )
        }

    fun showSoftwareHomePage(page: SoftwareHomePage): KiyoriShellState =
        copy(
            primaryDestination = PrimaryDestination.SOFTWARE_HOME,
            softwareHomePage = page,
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
            browserReturnTarget = null,
            browserExitPresentation = KiyoriBrowserExitPresentation.CLOSE,
        )

    fun openChild(destination: KiyoriShellChild): KiyoriShellState =
        copy(
            child = destination,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    fun openNestedChild(destination: KiyoriShellChild): KiyoriShellState {
        val currentChild = requireNotNull(child) { "A nested child requires an active parent child." }
        require(currentChild != destination) { "A child cannot use itself as its Back target." }
        return copy(
            child = destination,
            childBackTarget = currentChild,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )
    }

    fun closeChild(): KiyoriShellState =
        if (childBackTarget == null) {
            copy(child = null)
        } else {
            copy(child = childBackTarget, childBackTarget = null)
        }

    fun openAiDrawer(): KiyoriShellState =
        copy(
            isAiDrawerOpen = true,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    fun closeAiDrawer(): KiyoriShellState = copy(isAiDrawerOpen = false)

    fun openBookmarkDrawer(): KiyoriShellState =
        copy(
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = true,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    fun closeBookmarkDrawer(): KiyoriShellState = copy(isBookmarkDrawerOpen = false)

    fun openHistoryDrawer(): KiyoriShellState =
        copy(
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = true,
            isDownloadDrawerOpen = false,
        )

    fun closeHistoryDrawer(): KiyoriShellState = copy(isHistoryDrawerOpen = false)

    fun openDownloadDrawer(): KiyoriShellState =
        copy(
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = true,
        )

    fun closeDownloadDrawer(): KiyoriShellState = copy(isDownloadDrawerOpen = false)

    fun returnFromKiyoriAiSettings(): KiyoriShellState =
        selectPrimary(PrimaryDestination.SETTINGS_HOME)

    fun handleBack(): KiyoriShellBackTransition =
        when {
            isAiDrawerOpen ->
                KiyoriShellBackTransition(
                    state = closeAiDrawer(),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            isBookmarkDrawerOpen ->
                KiyoriShellBackTransition(
                    state = closeBookmarkDrawer(),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            isHistoryDrawerOpen ->
                KiyoriShellBackTransition(
                    state = closeHistoryDrawer(),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            isDownloadDrawerOpen ->
                KiyoriShellBackTransition(
                    state = closeDownloadDrawer(),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            child != null ->
                KiyoriShellBackTransition(
                    state = closeChild(),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                softwareHomePage != SoftwareHomePage.HOME ->
                KiyoriShellBackTransition(
                    state = copy(softwareHomePage = SoftwareHomePage.HOME),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            primaryDestination != PrimaryDestination.SOFTWARE_HOME ->
                KiyoriShellBackTransition(
                    state =
                        copy(
                            primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                            softwareHomePage = SoftwareHomePage.HOME,
                        ),
                    result = KiyoriShellBackResult.CONSUMED,
                )
            else ->
                KiyoriShellBackTransition(
                    state = this,
                    result = KiyoriShellBackResult.REQUEST_EXIT,
                )
        }
}

// Keep every Shell field behind one saveable owner. Splitting fields across the root composition caused
// newly added overlays to be silently discarded when their field was omitted from the bridge.
internal fun KiyoriShellState.toKiyoriShellSaveableValues(): List<Any> =
    listOf(
        primaryDestination.name,
        softwareHomePage.name,
        child?.name.orEmpty(),
        childBackTarget?.name.orEmpty(),
        isAiDrawerOpen,
        isBookmarkDrawerOpen,
        isHistoryDrawerOpen,
        isDownloadDrawerOpen,
        browserReturnTarget?.name.orEmpty(),
        browserExitPresentation.name,
    )

internal fun restoreKiyoriShellState(values: List<Any>): KiyoriShellState =
    KiyoriShellState(
        primaryDestination = PrimaryDestination.valueOf(values[0] as String),
        softwareHomePage = SoftwareHomePage.valueOf(values[1] as String),
        child =
            (values[2] as String)
                .takeIf { name -> name.isNotEmpty() }
                ?.let(KiyoriShellChild::valueOf),
        childBackTarget =
            (values[3] as String)
                .takeIf { name -> name.isNotEmpty() }
                ?.let(KiyoriShellChild::valueOf),
        isAiDrawerOpen = values[4] as Boolean,
        isBookmarkDrawerOpen = values[5] as Boolean,
        isHistoryDrawerOpen = values[6] as Boolean,
        isDownloadDrawerOpen = values[7] as Boolean,
        browserReturnTarget =
            (values[8] as String)
                .takeIf { name -> name.isNotEmpty() }
                ?.let(KiyoriBrowserReturnTarget::valueOf),
        browserExitPresentation =
            KiyoriBrowserExitPresentation.valueOf(values[9] as String),
    )

internal val KiyoriShellStateSaver =
    listSaver<KiyoriShellState, Any>(
        save = { state -> state.toKiyoriShellSaveableValues() },
        restore = ::restoreKiyoriShellState,
    )

internal fun KiyoriShellState.openExternalChild(
    destination: KiyoriShellChild,
): KiyoriShellState {
    val owner =
        when (destination) {
            KiyoriShellChild.FULL_SCREEN_WEB_SEARCH -> PrimaryDestination.SOFTWARE_HOME
            KiyoriShellChild.BROWSER_SETTINGS -> PrimaryDestination.SETTINGS_HOME
            KiyoriShellChild.DOWNLOAD_SETTINGS -> PrimaryDestination.SETTINGS_HOME
            KiyoriShellChild.PLAYER_SETTINGS -> PrimaryDestination.SETTINGS_HOME
        }
    return selectPrimary(owner).openChild(destination)
}

internal fun KiyoriShellState.openExternalDestination(
    destination: KiyoriShellExternalDestination,
): KiyoriShellState =
    when (destination) {
        KiyoriShellExternalDestination.BROWSER_HOME ->
            openBrowser(
                returnTarget = resolveExternalBrowserReturnTarget(),
                exitPresentation = resolveExternalBrowserExitPresentation(),
            )
        KiyoriShellExternalDestination.BROWSER_HOME_FROM_MINIMIZED_INDICATOR ->
            openBrowser(
                returnTarget = resolveExternalBrowserReturnTarget(),
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        KiyoriShellExternalDestination.DOWNLOADS ->
            openDownloadDrawer()
        KiyoriShellExternalDestination.BROWSER_SETTINGS ->
            openExternalChild(KiyoriShellChild.BROWSER_SETTINGS)
        KiyoriShellExternalDestination.DOWNLOAD_SETTINGS ->
            openExternalChild(KiyoriShellChild.DOWNLOAD_SETTINGS)
    }

internal fun KiyoriShellState.resolveExternalBrowserExitPresentation():
    KiyoriBrowserExitPresentation =
        if (primaryDestination == PrimaryDestination.BROWSER_HOME) {
            browserExitPresentation
        } else {
            KiyoriBrowserExitPresentation.CLOSE
        }

internal fun KiyoriShellState.resolveExternalBrowserReturnTarget(): KiyoriBrowserReturnTarget =
    when {
        primaryDestination == PrimaryDestination.BROWSER_HOME ->
            browserReturnTarget ?: KiyoriBrowserReturnTarget.SOFTWARE_HOME
        primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
            softwareHomePage == SoftwareHomePage.AI_HOME ->
            KiyoriBrowserReturnTarget.AI_HOME
        else -> KiyoriBrowserReturnTarget.SOFTWARE_HOME
    }

internal fun calculateKiyoriAiDrawerWidthDp(
    windowWidthDp: Float,
    separatingFoldLeftDp: Float? = null,
): Float {
    require(windowWidthDp > 0f) { "windowWidthDp must be positive" }
    val contractWidth =
        when {
            windowWidthDp < 600f -> windowWidthDp * 0.75f
            windowWidthDp < 840f -> 320f
            else -> 360f
        }
    val leftPhysicalRegionWidth =
        separatingFoldLeftDp?.takeIf { foldLeft -> foldLeft > 0f }
    return if (leftPhysicalRegionWidth == null) {
        contractWidth
    } else {
        minOf(contractWidth, leftPhysicalRegionWidth)
    }
}
