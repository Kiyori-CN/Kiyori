package com.kiyori.app.shell

import androidx.compose.runtime.saveable.listSaver
import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation

enum class PrimaryDestination {
    SOFTWARE_HOME,
    BROWSER_HOME,
    MINI_APP_HOME,
    FILE_MANAGEMENT_HOME,
    SETTINGS_HOME,
}

enum class KiyoriBrowserReturnTarget {
    MINUS_ONE_PAGE,
    SOFTWARE_HOME,
    AI_HOME,
    MINI_APP_HOME,
    FILE_MANAGEMENT_HOME,
    SETTINGS_HOME,
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
    FILE_MANAGER,
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
    val settingsNavigation: KiyoriSettingsNavigationState? = null,
    val fileManagerSessionOpen: Boolean = false,
    val fileManagerMinimized: Boolean = false,
    val fileManagerParentSettings: KiyoriSettingsNavigationState? = null,
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
            child == null && settingsNavigation?.origin != KiyoriSettingsOrigin.FILE_MANAGER && !isAiDrawerOpen && !isBookmarkDrawerOpen && !isHistoryDrawerOpen &&
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
            settingsNavigation =
                if (destination == PrimaryDestination.SETTINGS_HOME) {
                    KiyoriSettingsNavigationState.start(
                        origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                    )
                } else {
                    null
                },
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
            settingsNavigation = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
            browserReturnTarget = returnTarget,
            browserExitPresentation = exitPresentation,
        )

    fun exitBrowser(): KiyoriShellState {
        val returnTarget =
            checkNotNull(browserReturnTarget) {
                "Browser Home must record its app return target before exit."
            }
        val targetPrimaryDestination =
            when (returnTarget) {
                KiyoriBrowserReturnTarget.MINUS_ONE_PAGE,
                KiyoriBrowserReturnTarget.SOFTWARE_HOME,
                KiyoriBrowserReturnTarget.AI_HOME,
                -> PrimaryDestination.SOFTWARE_HOME
                KiyoriBrowserReturnTarget.MINI_APP_HOME -> PrimaryDestination.MINI_APP_HOME
                KiyoriBrowserReturnTarget.FILE_MANAGEMENT_HOME ->
                    PrimaryDestination.FILE_MANAGEMENT_HOME
                KiyoriBrowserReturnTarget.SETTINGS_HOME -> PrimaryDestination.SETTINGS_HOME
            }
        val targetSoftwareHomePage =
            when (returnTarget) {
                KiyoriBrowserReturnTarget.MINUS_ONE_PAGE -> SoftwareHomePage.MINUS_ONE
                KiyoriBrowserReturnTarget.SOFTWARE_HOME -> SoftwareHomePage.HOME
                KiyoriBrowserReturnTarget.AI_HOME -> SoftwareHomePage.AI_HOME
                KiyoriBrowserReturnTarget.MINI_APP_HOME,
                KiyoriBrowserReturnTarget.FILE_MANAGEMENT_HOME,
                KiyoriBrowserReturnTarget.SETTINGS_HOME,
                -> softwareHomePage
            }
        return copy(
            primaryDestination = targetPrimaryDestination,
            softwareHomePage = targetSoftwareHomePage,
            child = null,
            settingsNavigation =
                if (returnTarget == KiyoriBrowserReturnTarget.SETTINGS_HOME) {
                    settingsNavigation?.restoreSettingsPresentation()
                } else {
                    null
                },
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
            settingsNavigation = null,
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
            settingsNavigation = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    /**
     * File management is a Shell child reached from multiple product surfaces. Keep the active
     * Settings session intact when Settings opens it, so closing the child returns to that exact
     * Settings route instead of silently moving the user to another primary destination.
     */
    fun openFileManager(): KiyoriShellState =
        copy(
            child = KiyoriShellChild.FILE_MANAGER,
            fileManagerSessionOpen = true,
            fileManagerMinimized = false,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    fun closeChild(): KiyoriShellState =
        copy(child = null,
            fileManagerSessionOpen = if (child == KiyoriShellChild.FILE_MANAGER) false else fileManagerSessionOpen,
            fileManagerMinimized = if (child == KiyoriShellChild.FILE_MANAGER) false else fileManagerMinimized)

    fun minimizeFileManager(): KiyoriShellState =
        showSoftwareHomePage(SoftwareHomePage.AI_HOME).copy(fileManagerSessionOpen = true, fileManagerMinimized = true)

    fun closeMinimizedFileManager(): KiyoriShellState =
        copy(fileManagerSessionOpen = false, fileManagerMinimized = false)


    fun openSettings(
        origin: KiyoriSettingsOrigin,
        initialRoute: KiyoriSettingsRoute = KiyoriSettingsRoute.HOME,
    ): KiyoriShellState =
        copy(
            primaryDestination =
                if (origin == KiyoriSettingsOrigin.BOTTOM_NAVIGATION) {
                    PrimaryDestination.SETTINGS_HOME
                } else {
                    primaryDestination
                },
            child = null,
            fileManagerParentSettings = if (origin == KiyoriSettingsOrigin.FILE_MANAGER && settingsNavigation?.origin != KiyoriSettingsOrigin.FILE_MANAGER) settingsNavigation else fileManagerParentSettings,
            settingsNavigation =
                KiyoriSettingsNavigationState.start(
                    origin = origin,
                    initialRoute = initialRoute,
                ),
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    fun openSettingsRoute(route: KiyoriSettingsRoute): KiyoriShellState =
        copy(
            settingsNavigation =
                checkNotNull(settingsNavigation) {
                    "Settings route requires an active settings session."
                }.push(route),
        )

    /**
     * Settings Home can be restored as a primary surface without its transient session after an
     * older process state or a browser handoff. Re-establish the session at this boundary before
     * pushing a child route so a user action never reaches the strict route-stack contract with a
     * null owner.
     */
    fun openSettingsSurfaceRoute(
        route: KiyoriSettingsRoute,
        origin: KiyoriSettingsOrigin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
    ): KiyoriShellState =
        settingsNavigation?.let { openSettingsRoute(route) }
            ?: openSettings(origin = origin, initialRoute = route)

    fun showSettingsOperitRoute(): KiyoriShellState =
        copy(
            settingsNavigation =
                checkNotNull(settingsNavigation) {
                    "Operit settings detail requires an active settings session."
                }.showOperitRoute(),
        )

    fun restoreSettingsAfterOperitRoute(): KiyoriShellState =
        copy(
            settingsNavigation =
                checkNotNull(settingsNavigation) {
                    "Restoring an Operit settings route requires an active settings session."
                }.restoreSettingsPresentation(),
        )

    fun suspendSettingsForBrowserWorkspace(): KiyoriShellState =
        copy(
            settingsNavigation =
                checkNotNull(settingsNavigation) {
                    "Browser workspace requires an active settings session."
                }.suspendForBrowserWorkspace(),
            primaryDestination = PrimaryDestination.BROWSER_HOME,
            browserReturnTarget =
                browserReturnTarget
                    ?: when (settingsNavigation.origin) {
                        KiyoriSettingsOrigin.BOTTOM_NAVIGATION ->
                            KiyoriBrowserReturnTarget.SETTINGS_HOME
                        KiyoriSettingsOrigin.BROWSER_HOME,
                        KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
                        -> KiyoriBrowserReturnTarget.SOFTWARE_HOME
                        KiyoriSettingsOrigin.AI_HOST -> KiyoriBrowserReturnTarget.AI_HOME
                        KiyoriSettingsOrigin.FILE_MANAGER -> KiyoriBrowserReturnTarget.FILE_MANAGEMENT_HOME
                    },
        )

    fun restoreSettingsFromBrowserWorkspace(): KiyoriShellState {
        val navigation =
            checkNotNull(settingsNavigation) {
                "Restoring a settings workspace requires an active settings session."
            }
        val restoredPrimary =
            when (navigation.origin) {
                KiyoriSettingsOrigin.BOTTOM_NAVIGATION -> PrimaryDestination.SETTINGS_HOME
                KiyoriSettingsOrigin.BROWSER_HOME,
                KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
                -> PrimaryDestination.BROWSER_HOME
                KiyoriSettingsOrigin.AI_HOST -> PrimaryDestination.SOFTWARE_HOME
                KiyoriSettingsOrigin.FILE_MANAGER -> PrimaryDestination.FILE_MANAGEMENT_HOME
            }
        return copy(
            primaryDestination = restoredPrimary,
            softwareHomePage =
                if (navigation.origin == KiyoriSettingsOrigin.AI_HOST) {
                    SoftwareHomePage.AI_HOME
                } else {
                    softwareHomePage
                },
            settingsNavigation = navigation.restoreSettingsPresentation(),
            browserReturnTarget =
                if (restoredPrimary == PrimaryDestination.BROWSER_HOME) {
                    browserReturnTarget
                } else {
                    null
                },
        )
    }

    fun closeSettingsRoute(): KiyoriShellState {
        val navigation =
            checkNotNull(settingsNavigation) {
                "Closing a settings route requires an active settings session."
            }
        if (navigation.canPopRoute) {
            return copy(settingsNavigation = navigation.popRoute())
        }
        return when (navigation.origin) {
            KiyoriSettingsOrigin.FILE_MANAGER -> copy(settingsNavigation = fileManagerParentSettings, fileManagerParentSettings = null).openFileManager()
            KiyoriSettingsOrigin.BOTTOM_NAVIGATION ->
                showSoftwareHomePage(SoftwareHomePage.HOME)
            KiyoriSettingsOrigin.BROWSER_HOME,
            KiyoriSettingsOrigin.AI_HOST,
            KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
            -> copy(settingsNavigation = null)
        }
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
            settingsNavigation = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = true,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = false,
        )

    fun closeBookmarkDrawer(): KiyoriShellState = copy(isBookmarkDrawerOpen = false)

    fun openHistoryDrawer(): KiyoriShellState =
        copy(
            child = null,
            settingsNavigation = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = true,
            isDownloadDrawerOpen = false,
        )

    fun closeHistoryDrawer(): KiyoriShellState = copy(isHistoryDrawerOpen = false)

    fun openDownloadDrawer(): KiyoriShellState =
        copy(
            child = null,
            settingsNavigation = null,
            isAiDrawerOpen = false,
            isBookmarkDrawerOpen = false,
            isHistoryDrawerOpen = false,
            isDownloadDrawerOpen = true,
        )

    fun closeDownloadDrawer(): KiyoriShellState = copy(isDownloadDrawerOpen = false)

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
            isKiyoriSettingsBackOwnedByShell(settingsNavigation) ->
                KiyoriShellBackTransition(
                    state = closeSettingsRoute(),
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
        isAiDrawerOpen,
        isBookmarkDrawerOpen,
        isHistoryDrawerOpen,
        isDownloadDrawerOpen,
        browserReturnTarget?.name.orEmpty(),
        browserExitPresentation.name,
        settingsNavigation?.sessionId.orEmpty(),
        settingsNavigation?.origin?.name.orEmpty(),
        settingsNavigation?.routes?.joinToString(ROUTE_SEPARATOR) { route -> route.name }.orEmpty(),
        settingsNavigation?.presentation?.name.orEmpty(),
        fileManagerSessionOpen,
        fileManagerMinimized,
        fileManagerParentSettings?.sessionId.orEmpty(),
        fileManagerParentSettings?.origin?.name.orEmpty(),
        fileManagerParentSettings?.routes?.joinToString(ROUTE_SEPARATOR) { it.name }.orEmpty(),
        fileManagerParentSettings?.presentation?.name.orEmpty(),
    )

internal fun restoreKiyoriShellState(values: List<Any>): KiyoriShellState =
    KiyoriShellState(
        primaryDestination = PrimaryDestination.valueOf(values[0] as String),
        softwareHomePage = SoftwareHomePage.valueOf(values[1] as String),
        child =
            (values[2] as String)
                .takeIf { name -> name.isNotEmpty() }
                ?.let(KiyoriShellChild::valueOf),
        fileManagerSessionOpen = values.getOrNull(13) as? Boolean ?: (values[2] == KiyoriShellChild.FILE_MANAGER.name),
        fileManagerMinimized = values.getOrNull(14) as? Boolean ?: false,
        fileManagerParentSettings = (values.getOrNull(15) as? String)?.takeIf { it.isNotBlank() }?.let { id ->
            KiyoriSettingsNavigationState(id, KiyoriSettingsOrigin.valueOf(values[16] as String),
                (values[17] as String).split(ROUTE_SEPARATOR).map(KiyoriSettingsRoute::valueOf),
                KiyoriSettingsPresentation.valueOf(values[18] as String))
        },
        isAiDrawerOpen = values[3] as Boolean,
        isBookmarkDrawerOpen = values[4] as Boolean,
        isHistoryDrawerOpen = values[5] as Boolean,
        isDownloadDrawerOpen = values[6] as Boolean,
        browserReturnTarget =
            (values[7] as String)
                .takeIf { name -> name.isNotEmpty() }
                ?.let(KiyoriBrowserReturnTarget::valueOf),
        browserExitPresentation =
            KiyoriBrowserExitPresentation.valueOf(values[8] as String),
        settingsNavigation =
            (values[9] as String)
                .takeIf { it.isNotEmpty() }
                ?.let { sessionId ->
                    val origin = KiyoriSettingsOrigin.valueOf(values[10] as String)
                    val routes =
                        (values[11] as String)
                            .split(ROUTE_SEPARATOR)
                            .filter(String::isNotEmpty)
                            .map(KiyoriSettingsRoute::valueOf)
                    KiyoriSettingsNavigationState(
                        sessionId = sessionId,
                        origin = origin,
                        routes = routes,
                        presentation =
                            KiyoriSettingsPresentation.valueOf(values[12] as String),
                    )
                },
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
            KiyoriShellChild.FILE_MANAGER -> PrimaryDestination.FILE_MANAGEMENT_HOME
        }
    return selectPrimary(owner).openChild(destination)
}

internal fun KiyoriShellState.openExternalDestination(
    destination: KiyoriShellExternalDestination,
): KiyoriShellState =
    when (destination) {
        KiyoriShellExternalDestination.BROWSER_HOME -> {
            val returnTarget = resolveExternalBrowserReturnTarget()
            val nextState =
                openBrowser(
                    returnTarget = returnTarget,
                    exitPresentation = resolveExternalBrowserExitPresentation(),
                )
            if (
                returnTarget == KiyoriBrowserReturnTarget.SETTINGS_HOME &&
                    settingsNavigation != null
            ) {
                nextState.copy(settingsNavigation = settingsNavigation.suspendForBrowserHome())
            } else {
                nextState
            }
        }
        KiyoriShellExternalDestination.BROWSER_HOME_FROM_MINIMIZED_INDICATOR ->
            openBrowser(
                returnTarget = resolveExternalBrowserReturnTarget(),
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        KiyoriShellExternalDestination.DOWNLOADS ->
            openDownloadDrawer()
        KiyoriShellExternalDestination.BROWSER_SETTINGS ->
            openBrowser(
                returnTarget = resolveExternalBrowserReturnTarget(),
                exitPresentation = resolveExternalBrowserExitPresentation(),
            ).openSettings(
                    origin = KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
                    initialRoute = KiyoriSettingsRoute.BROWSER,
                )
        KiyoriShellExternalDestination.DOWNLOAD_SETTINGS ->
            openBrowser(
                returnTarget = resolveExternalBrowserReturnTarget(),
                exitPresentation = resolveExternalBrowserExitPresentation(),
            ).openSettings(
                    origin = KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
                    initialRoute = KiyoriSettingsRoute.DOWNLOAD,
                )
    }

internal fun KiyoriShellState.resolveExternalBrowserExitPresentation():
    KiyoriBrowserExitPresentation =
        if (primaryDestination == PrimaryDestination.BROWSER_HOME) {
            browserExitPresentation
        } else {
            KiyoriBrowserExitPresentation.CLOSE
        }

internal fun KiyoriShellState.resolveExternalBrowserReturnTarget(): KiyoriBrowserReturnTarget =
    when (primaryDestination) {
        PrimaryDestination.BROWSER_HOME ->
            checkNotNull(browserReturnTarget) {
                "Browser Home must preserve its app return target."
            }
        PrimaryDestination.SOFTWARE_HOME ->
            when (softwareHomePage) {
                SoftwareHomePage.MINUS_ONE -> KiyoriBrowserReturnTarget.MINUS_ONE_PAGE
                SoftwareHomePage.HOME -> KiyoriBrowserReturnTarget.SOFTWARE_HOME
                SoftwareHomePage.AI_HOME -> KiyoriBrowserReturnTarget.AI_HOME
            }
        PrimaryDestination.MINI_APP_HOME -> KiyoriBrowserReturnTarget.MINI_APP_HOME
        PrimaryDestination.FILE_MANAGEMENT_HOME ->
            KiyoriBrowserReturnTarget.FILE_MANAGEMENT_HOME
        PrimaryDestination.SETTINGS_HOME -> KiyoriBrowserReturnTarget.SETTINGS_HOME
    }

internal fun calculateKiyoriAiDrawerWidthDp(
    windowWidthDp: Float,
    separatingFoldLeftDp: Float? = null,
): Float {
    return com.kiyori.design.theme.calculateKiyoriDrawerWidthDp(windowWidthDp, separatingFoldLeftDp)
}

private const val ROUTE_SEPARATOR = "\u001F"
