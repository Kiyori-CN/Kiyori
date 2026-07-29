package com.ai.assistance.operit.ui.main.shell

import androidx.compose.runtime.saveable.listSaver
import com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteSpec

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
    DOWNLOADS,
    BROWSER_SETTINGS,
    DOWNLOAD_SETTINGS,
}

enum class AiDrawerSelectionEffect {
    CLOSE_ONLY,
    REPLACE_PRIMARY,
}

enum class AiTopBarMode {
    DRAWER,
    BACK,
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
    val isDownloadDrawerOpen: Boolean = false,
    val browserReturnTarget: KiyoriBrowserReturnTarget? = null,
) {
    val showsBottomBar: Boolean
        get() =
            child == null && !isAiDrawerOpen && !isBookmarkDrawerOpen && !isDownloadDrawerOpen &&
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
            isDownloadDrawerOpen = false,
            browserReturnTarget = null,
        )

    fun openBrowser(returnTarget: KiyoriBrowserReturnTarget): KiyoriShellState =
        copy(
            primaryDestination = PrimaryDestination.BROWSER_HOME,
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isDownloadDrawerOpen = false,
            browserReturnTarget = returnTarget,
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
                    isDownloadDrawerOpen = false,
                    browserReturnTarget = null,
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
                    isDownloadDrawerOpen = false,
                    browserReturnTarget = null,
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
            isDownloadDrawerOpen = false,
            browserReturnTarget = null,
        )

    fun openChild(destination: KiyoriShellChild): KiyoriShellState =
        copy(
            child = destination,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
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
        copy(isAiDrawerOpen = true, isBookmarkDrawerOpen = false, isDownloadDrawerOpen = false)

    fun closeAiDrawer(): KiyoriShellState = copy(isAiDrawerOpen = false)

    fun openBookmarkDrawer(): KiyoriShellState =
        copy(
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = true,
            isDownloadDrawerOpen = false,
        )

    fun closeBookmarkDrawer(): KiyoriShellState = copy(isBookmarkDrawerOpen = false)

    fun openDownloadDrawer(): KiyoriShellState =
        copy(
            child = null,
            childBackTarget = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
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

// Keep every Shell field behind one saveable owner. Splitting the fields across OperitApp caused
// newly added overlays to be silently discarded when their field was omitted from the bridge.
internal fun KiyoriShellState.toKiyoriShellSaveableValues(): List<Any> =
    listOf(
        primaryDestination.name,
        softwareHomePage.name,
        child?.name.orEmpty(),
        childBackTarget?.name.orEmpty(),
        isAiDrawerOpen,
        isBookmarkDrawerOpen,
        isDownloadDrawerOpen,
        browserReturnTarget?.name.orEmpty(),
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
        isDownloadDrawerOpen = values[6] as Boolean,
        browserReturnTarget =
            (values[7] as String)
                .takeIf { name -> name.isNotEmpty() }
                ?.let(KiyoriBrowserReturnTarget::valueOf),
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
            openBrowser(resolveExternalBrowserReturnTarget())
        KiyoriShellExternalDestination.DOWNLOADS ->
            openDownloadDrawer()
        KiyoriShellExternalDestination.BROWSER_SETTINGS ->
            openExternalChild(KiyoriShellChild.BROWSER_SETTINGS)
        KiyoriShellExternalDestination.DOWNLOAD_SETTINGS ->
            openExternalChild(KiyoriShellChild.DOWNLOAD_SETTINGS)
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

internal fun resolveAiDrawerSelection(
    currentEntryId: String?,
    targetEntryId: String,
): AiDrawerSelectionEffect =
    if (currentEntryId == targetEntryId) {
        AiDrawerSelectionEffect.CLOSE_ONLY
    } else {
        AiDrawerSelectionEffect.REPLACE_PRIMARY
    }

internal fun resolveAiTopBarMode(routeEntry: RouteEntry): AiTopBarMode =
    if (
        routeEntry.navigationRootEntryId != null &&
            routeEntry.source != RouteEntrySource.KIYORI_SETTINGS
    ) {
        AiTopBarMode.DRAWER
    } else {
        AiTopBarMode.BACK
    }

internal fun hasSameAiSettingsSourceFamily(
    currentSource: RouteEntrySource,
    targetSource: RouteEntrySource,
): Boolean =
    (currentSource == RouteEntrySource.KIYORI_SETTINGS) ==
        (targetSource == RouteEntrySource.KIYORI_SETTINGS)

private const val AI_NAVIGATION_ROOT_INSTANCE_PREFIX = "kiyori.ai.root:"

internal fun NavigationEntrySpec.toAiPrimaryRouteEntry(source: RouteEntrySource): RouteEntry {
    val routeEntry =
        RouteEntry(
            routeId = routeId,
            args = routeArgs,
            source = source,
            navigationRootEntryId = entryId,
        )
    return when (kind) {
        NavigationEntryKind.HOST ->
            routeEntry.copy(instanceId = "$AI_NAVIGATION_ROOT_INSTANCE_PREFIX$entryId")
        NavigationEntryKind.PLUGIN -> routeEntry
    }
}

internal fun NavigationEntrySpec.preservesAiPrimaryStack(routeSpec: RouteSpec): Boolean {
    require(routeSpec.routeId == routeId) {
        "Route spec ${routeSpec.routeId} does not belong to navigation entry $entryId"
    }
    return kind == NavigationEntryKind.HOST || routeSpec.keepAlive
}

internal fun buildAiPrimaryStack(
    targetRoot: RouteEntry,
    savedStack: List<RouteEntry>?,
    restoreChildren: Boolean,
): List<RouteEntry> {
    if (savedStack == null || !restoreChildren) {
        return listOf(targetRoot)
    }
    return buildList {
        add(targetRoot)
        addAll(savedStack.drop(1))
    }
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
