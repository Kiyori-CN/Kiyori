package com.kiyori.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.main.AiHomeQuickAction
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.shell.KiyoriBookmarkDrawerHost
import com.ai.assistance.operit.ui.main.shell.KiyoriBrowserSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriAdBlockSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriDownloadDrawerHost
import com.ai.assistance.operit.ui.main.shell.KiyoriDownloadSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriHistoryDrawerHost
import com.ai.assistance.operit.ui.main.shell.KiyoriMinusOnePage
import com.ai.assistance.operit.ui.main.shell.KiyoriPlayerSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsHomePage
import com.kiyori.capability.browser.presentation.KiyoriBrowserWorkspaceRoute
import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import com.kiyori.design.theme.KiyoriBrowserTheme
import com.kiyori.design.theme.KiyoriSettingsTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
private fun KiyoriShellChildThemeBoundary(
    settingsRoute: KiyoriSettingsRoute?,
    content: @Composable () -> Unit,
) {
    KiyoriBrowserTheme {
        // 设置页的选择面板与折叠列表是同级节点。主题边界必须包住完整子页，
        // 否则面板会在列表内部主题退出后读取不到 LocalKiyoriSettingsColors。
        if (shouldProvideKiyoriSettingsTheme(settingsRoute)) {
            KiyoriSettingsTheme(content)
        } else {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KiyoriAppShell(
    state: KiyoriShellState,
    onStateChange: (KiyoriShellState) -> Unit,
    aiHostIsRoot: Boolean,
    aiHomeGestureBlocked: Boolean,
    selectedAiEntryId: String?,
    aiDrawerEntries: List<NavigationEntrySpec>,
    isNetworkAvailable: Boolean,
    networkType: String,
    browserWindowCount: Int,
    onAiDrawerEntrySelected: (NavigationEntrySpec) -> Unit,
    onOpenAiHome: () -> Unit,
    onAiQuickAction: (AiHomeQuickAction) -> Unit,
    onAiHomeSettled: () -> Unit,
    onWeatherSearch: (String) -> Unit,
    onOpenBrowserWindows: () -> Unit,
    onQueueForegroundBrowserUrl: (String) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    onOpenAccountConnectionsFromKiyoriSettings: () -> Unit,
    onOpenAiAssistantFromKiyoriSettings: () -> Unit,
    onOpenSpeechServicesFromKiyoriSettings: () -> Unit,
    onOpenBrowserSettingsFromKiyoriSettings: () -> Unit,
    onOpenAppearanceSettingsFromKiyoriSettings: () -> Unit,
    onOpenDataSettingsFromKiyoriSettings: () -> Unit,
    onOpenBrowserWorkspace: (KiyoriBrowserWorkspaceRoute) -> Unit,
    onSubmitWebSearch: (KiyoriWebSearchRequest) -> Unit,
    onRequestExit: () -> Unit,
    browserHome: @Composable (Modifier) -> Unit,
    aiHost: @Composable () -> Unit,
) {
    val pagerState =
        rememberPagerState(
            initialPage = state.softwareHomePage.pagerIndex,
            pageCount = { SoftwareHomePage.entries.size },
        )
    val latestState by rememberUpdatedState(state)
    val latestOnStateChange by rememberUpdatedState(onStateChange)
    val latestOnAiHomeSettled by rememberUpdatedState(onAiHomeSettled)
    val latestOnRequestExit by rememberUpdatedState(onRequestExit)
    val pagerFlingBehavior = PagerDefaults.flingBehavior(state = pagerState)
    val aiHostPagerGestureState =
        remember(pagerState) {
            object : ScrollableState by pagerState {
                // A tap must reach AI Home while the previous fling is settling. Reporting the
                // pager animation here makes scrollable intercept immediately before touch slop.
                override val isScrollInProgress: Boolean
                    get() = false
            }
        }

    var startupPreloadReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // LaunchedEffect 会在首帧绘制前启动；第二个帧信号到来时，首页首帧已经完成，
        // 此时再恢复相邻页和屏幕外 AI 页面的常驻预组合。
        withFrameNanos { }
        withFrameNanos { }
        startupPreloadReady = true
    }

    LaunchedEffect(state.primaryDestination, state.softwareHomePage) {
        if (
            state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                pagerState.settledPage != state.softwareHomePage.pagerIndex
        ) {
            pagerState.animateScrollToPage(state.softwareHomePage.pagerIndex)
        }
    }

    LaunchedEffect(
        pagerState,
        state.primaryDestination,
        state.child,
        state.isAiDrawerOpen,
        state.isBookmarkDrawerOpen,
        state.isDownloadDrawerOpen,
        aiHostIsRoot,
    ) {
        if (
            state.primaryDestination != PrimaryDestination.SOFTWARE_HOME ||
                state.child != null ||
                state.isAiDrawerOpen ||
                state.isBookmarkDrawerOpen ||
                state.isDownloadDrawerOpen ||
                !aiHostIsRoot
        ) {
            return@LaunchedEffect
        }
        val initialSettledPage = SoftwareHomePage.fromPagerIndex(pagerState.settledPage)
        if (
            shouldNotifyKiyoriAiHomeSettledForInitialPage(
                initialSettledPage = initialSettledPage,
                requestedPage = latestState.softwareHomePage,
            )
        ) {
            latestOnAiHomeSettled()
        }
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            // When Browser Home is released, this collector restarts before the requested
            // software-home animation settles. Its first value can still be the old page;
            // ignore only that unchanged snapshot so a newly settled AI page is never discarded.
            .dropWhile { pageIndex -> pageIndex == initialSettledPage.pagerIndex }
            .collect { pageIndex ->
                val page = SoftwareHomePage.fromPagerIndex(pageIndex)
                if (latestState.softwareHomePage != page) {
                    latestOnStateChange(latestState.showSoftwareHomePage(page))
                }
                if (page == SoftwareHomePage.AI_HOME) {
                    latestOnAiHomeSettled()
                }
            }
    }

    val settingsOverlayVisible = shouldPresentKiyoriSettingsOverlay(state)
    val dispatchShellBack: () -> Unit = {
        val transition = latestState.handleBack()
        when (transition.result) {
            KiyoriShellBackResult.CONSUMED -> latestOnStateChange(transition.state)
            KiyoriShellBackResult.REQUEST_EXIT -> latestOnRequestExit()
        }
    }

    BackHandler(
        enabled =
            shouldEnableKiyoriShellBackHandler(
                aiHostIsRoot = aiHostIsRoot,
                isAiDrawerOpen = state.isAiDrawerOpen,
                isBookmarkDrawerOpen = state.isBookmarkDrawerOpen,
                isHistoryDrawerOpen = state.isHistoryDrawerOpen,
                isDownloadDrawerOpen = state.isDownloadDrawerOpen,
                settingsNavigation = state.settingsNavigation,
            ),
        onBack = dispatchShellBack,
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val pagerReverseDirection =
            shouldReverseKiyoriPagerDrag(LocalLayoutDirection.current)
        val aiPageOffset by remember(pagerState) {
            derivedStateOf {
                calculateKiyoriPagerPageOffset(
                    pagerState = pagerState,
                    page = SoftwareHomePage.AI_HOME.pagerIndex,
                )
            }
        }
        val centerPageOffset by remember(pagerState) {
            derivedStateOf {
                calculateKiyoriPagerPageOffset(
                    pagerState = pagerState,
                    page = SoftwareHomePage.HOME.pagerIndex,
                )
            }
        }
        val pagerAcceptsInput =
            aiHostIsRoot &&
                state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                state.child == null &&
                !state.isAiDrawerOpen &&
                !state.isBookmarkDrawerOpen &&
                !state.isHistoryDrawerOpen &&
                !state.isDownloadDrawerOpen

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            userScrollEnabled = pagerAcceptsInput,
            flingBehavior = pagerFlingBehavior,
            beyondViewportPageCount = kiyoriStartupBeyondViewportPageCount(startupPreloadReady),
            key = { page -> page },
        ) { page ->
            when (SoftwareHomePage.fromPagerIndex(page)) {
                SoftwareHomePage.MINUS_ONE ->
                    KiyoriMinusOnePage(
                        onOpenBookmarkDrawer = {
                            latestOnStateChange(latestState.openBookmarkDrawer())
                        },
                        onOpenHistoryDrawer = {
                            latestOnStateChange(latestState.openHistoryDrawer())
                        },
                        onOpenDownloadDrawer = {
                            latestOnStateChange(latestState.openDownloadDrawer())
                        },
                        onClose = {
                            latestOnStateChange(
                                latestState.showSoftwareHomePage(SoftwareHomePage.HOME),
                            )
                        },
                    )
                SoftwareHomePage.HOME ->
                    KiyoriSoftwareHomePage(
                        browserWindowCount = browserWindowCount,
                        onSearchClick = {
                            onStateChange(
                                state.openChild(KiyoriShellChild.FULL_SCREEN_WEB_SEARCH),
                            )
                        },
                        onAiClick = onOpenAiHome,
                        onAiQuickAction = onAiQuickAction,
                        onWeatherSearch = onWeatherSearch,
                        onWindowsClick = onOpenBrowserWindows,
                    )
                SoftwareHomePage.AI_HOME -> Unit
            }
        }

        if (state.primaryDestination != PrimaryDestination.SOFTWARE_HOME) {
            if (state.primaryDestination == PrimaryDestination.BROWSER_HOME) {
                browserHome(
                    Modifier
                        .fillMaxSize()
                        .zIndex(4f),
                )
            } else {
                KiyoriPrimaryRootPage(
                    destination = state.primaryDestination,
                    onOpenAccountConnections = onOpenAccountConnectionsFromKiyoriSettings,
                    onOpenAiAssistant = onOpenAiAssistantFromKiyoriSettings,
                    onOpenSpeechServices = onOpenSpeechServicesFromKiyoriSettings,
                    onOpenBrowserSettings = onOpenBrowserSettingsFromKiyoriSettings,
                    onOpenDownloadSettings = {
                        onStateChange(
                            state.openSettings(
                                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                                initialRoute = KiyoriSettingsRoute.DOWNLOAD,
                            ),
                        )
                    },
                    onOpenPlayerSettings = {
                        onStateChange(
                            state.openSettings(
                                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                                initialRoute = KiyoriSettingsRoute.PLAYER,
                            ),
                        )
                    },
                    onOpenAdBlockSettings = {
                        onStateChange(
                            state.openSettings(
                                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                                initialRoute = KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                            ),
                        )
                    },
                    onOpenAppearanceSettings = onOpenAppearanceSettingsFromKiyoriSettings,
                    onOpenDataSettings = onOpenDataSettingsFromKiyoriSettings,
                    modifier = Modifier.fillMaxSize().zIndex(4f),
                )
            }
        }

        val forceAiHostFullscreen = !aiHostIsRoot
        val aiHostTranslationX =
            if (forceAiHostFullscreen) {
                0f
            } else {
                calculateKiyoriAiHostTranslation(
                    pageOffset = aiPageOffset,
                    viewportWidthPx = viewportWidthPx,
                )
            }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .offset { IntOffset(aiHostTranslationX.roundToInt(), 0) }
                    // Sharing PagerState lets a reverse drag cancel an in-flight home-page fling.
                    .scrollable(
                        state = aiHostPagerGestureState,
                        orientation = Orientation.Horizontal,
                        // HorizontalPager reverses LTR drag deltas before dispatching them to
                        // PagerState. The AI host sits above the pager, so it must use the same
                        // direction or a rightward drag is consumed against the last-page edge.
                        reverseDirection = pagerReverseDirection,
                        enabled =
                            pagerAcceptsInput &&
                                !aiHomeGestureBlocked,
                        flingBehavior = pagerFlingBehavior,
                    )
                    .zIndex(if (forceAiHostFullscreen) 20f else 2f),
        ) {
            if (
                shouldComposeKiyoriAiHost(
                    aiHostIsRoot = aiHostIsRoot,
                    softwareHomePage = state.softwareHomePage,
                    startupPreloadReady = startupPreloadReady,
                )
            ) {
                aiHost()
            }
        }

        // 该处理器在 Browser/AI 宿主之后、具体设置页面之前注册。这样所有 Shell 设置页面
        // 都能压住底层宿主，页面内弹窗和未保存确认仍可在随后注册并取得最高优先级。
        BackHandler(
            enabled =
                shouldEnableKiyoriSettingsHostBackHandler(
                    settingsNavigation = state.settingsNavigation,
                ),
            onBack = dispatchShellBack,
        )

        AnimatedVisibility(
            visible = settingsOverlayVisible,
            modifier = Modifier.fillMaxSize().zIndex(12f),
            enter = fadeIn() + slideInVertically(initialOffsetY = { height -> height / 18 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { height -> height / 24 }),
        ) {
            KiyoriShellChildThemeBoundary(
                settingsRoute = state.settingsNavigation?.currentRoute,
            ) {
                when (state.child) {
                    KiyoriShellChild.FULL_SCREEN_WEB_SEARCH ->
                        KiyoriFullScreenWebSearchPage(
                            onBack = { onStateChange(state.closeChild()) },
                            onSubmitSearch = onSubmitWebSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    null -> Unit
                }
                when (state.settingsNavigation?.currentRoute) {
                    KiyoriSettingsRoute.HOME ->
                        KiyoriSettingsHomePage(
                            onOpenAccountConnections =
                                onOpenAccountConnectionsFromKiyoriSettings,
                            onOpenAiAssistant = onOpenAiAssistantFromKiyoriSettings,
                            onOpenSpeechServices = onOpenSpeechServicesFromKiyoriSettings,
                            onOpenBrowserSettings = {
                                onStateChange(state.openSettingsRoute(KiyoriSettingsRoute.BROWSER))
                            },
                            onOpenDownloadSettings = {
                                onStateChange(state.openSettingsRoute(KiyoriSettingsRoute.DOWNLOAD))
                            },
                            onOpenPlayerSettings = {
                                onStateChange(state.openSettingsRoute(KiyoriSettingsRoute.PLAYER))
                            },
                            onOpenAdBlockSettings = {
                                onStateChange(
                                    state.openSettingsRoute(
                                        KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                                    ),
                                )
                            },
                            onOpenAppearanceSettings =
                                onOpenAppearanceSettingsFromKiyoriSettings,
                            onOpenDataSettings = onOpenDataSettingsFromKiyoriSettings,
                            onBack = { onStateChange(state.closeSettingsRoute()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriSettingsRoute.BROWSER ->
                        KiyoriBrowserSettingsPage(
                            route = KiyoriSettingsRoute.BROWSER,
                            onBack = { onStateChange(state.closeSettingsRoute()) },
                            onNavigate = { route ->
                                onStateChange(state.openSettingsRoute(route))
                            },
                            onOpenBrowserWorkspace = onOpenBrowserWorkspace,
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriSettingsRoute.DOWNLOAD ->
                        KiyoriDownloadSettingsPage(
                            onBack = { onStateChange(state.closeSettingsRoute()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriSettingsRoute.PLAYER ->
                        KiyoriPlayerSettingsPage(
                            onBack = { onStateChange(state.closeSettingsRoute()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                    KiyoriSettingsRoute.AD_BLOCK_URL_RULES,
                    KiyoriSettingsRoute.AD_BLOCK_ELEMENT_RULES,
                    KiyoriSettingsRoute.AD_BLOCK_ALLOW_LIST,
                    KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS,
                    -> KiyoriAdBlockSettingsPage(
                        route = state.settingsNavigation.currentRoute,
                        onBack = { onStateChange(state.closeSettingsRoute()) },
                        onNavigate = { route ->
                            onStateChange(state.openSettingsRoute(route))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION,
                    KiyoriSettingsRoute.BROWSER_PLUGIN_PERMISSIONS,
                    KiyoriSettingsRoute.BROWSER_TEXT_SIZE,
                    KiyoriSettingsRoute.BROWSER_PASSWORD_MANAGER,
                    -> KiyoriBrowserSettingsPage(
                        route = state.settingsNavigation.currentRoute,
                        onBack = { onStateChange(state.closeSettingsRoute()) },
                        onNavigate = { route ->
                            onStateChange(state.openSettingsRoute(route))
                        },
                        onOpenBrowserWorkspace = onOpenBrowserWorkspace,
                        modifier = Modifier.fillMaxSize(),
                    )
                    null -> Unit
                }
            }
        }

        val bottomBarAlpha =
            resolveKiyoriBottomBarAlpha(
                state = state,
                aiHostIsRoot = aiHostIsRoot,
                centerPageOffset = centerPageOffset,
            )
        if (bottomBarAlpha > 0.01f) {
                KiyoriBottomNavigation(
                    selectedDestination = state.primaryDestination,
                    alpha = bottomBarAlpha,
                    onDestinationSelected = { destination ->
                    onStateChange(
                        if (destination == PrimaryDestination.BROWSER_HOME) {
                            state.openExternalDestination(
                                KiyoriShellExternalDestination.BROWSER_HOME,
                            )
                        } else {
                            state.selectPrimary(destination)
                        }
                    )
                    },
                    modifier = Modifier.zIndex(10f),
                )
        }

        KiyoriDownloadDrawerHost(
            isVisible =
                shouldPresentKiyoriDownloadDrawer(
                    isDownloadDrawerOpen = state.isDownloadDrawerOpen,
                    aiHostIsRoot = aiHostIsRoot,
                ),
            onDismissRequest = {
                latestOnStateChange(latestState.closeDownloadDrawer())
            },
            onOpenDownloadSettings = {
                latestOnStateChange(
                    latestState
                        .closeDownloadDrawer()
                        .openSettings(
                            origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                            initialRoute = KiyoriSettingsRoute.DOWNLOAD,
                        ),
                )
            },
            modifier = Modifier.fillMaxSize().zIndex(30f),
        )

        KiyoriHistoryDrawerHost(
            isVisible =
                shouldPresentKiyoriHistoryDrawer(
                    isHistoryDrawerOpen = state.isHistoryDrawerOpen,
                    aiHostIsRoot = aiHostIsRoot,
                ),
            onDismissRequest = {
                latestOnStateChange(latestState.closeHistoryDrawer())
            },
            onOpenWebHistory = { url ->
                onQueueForegroundBrowserUrl(url)
                latestOnStateChange(
                    latestState
                        .closeHistoryDrawer()
                        .openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME),
                )
            },
            modifier = Modifier.fillMaxSize().zIndex(30f),
        )

        KiyoriBookmarkDrawerHost(
            isVisible =
                shouldPresentKiyoriBookmarkDrawer(
                    isBookmarkDrawerOpen = state.isBookmarkDrawerOpen,
                    aiHostIsRoot = aiHostIsRoot,
                ),
            onDismissRequest = {
                latestOnStateChange(latestState.closeBookmarkDrawer())
            },
            onOpenBookmark = { url ->
                onQueueForegroundBrowserUrl(url)
                latestOnStateChange(
                    latestState
                        .closeBookmarkDrawer()
                        .openExternalDestination(
                            KiyoriShellExternalDestination.BROWSER_HOME,
                        ),
                )
            },
            onOpenBookmarkInTab = { url, active ->
                onOpenBookmarkInTab(url, active)
                if (active) {
                    latestOnStateChange(
                        latestState.openExternalDestination(
                            KiyoriShellExternalDestination.BROWSER_HOME,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(30f),
        )

        KiyoriModalAiDrawer(
            isOpen = state.isAiDrawerOpen,
            selectedEntryId = selectedAiEntryId,
            navigationEntries = aiDrawerEntries,
            isNetworkAvailable = isNetworkAvailable,
            networkType = networkType,
            onDismiss = { onStateChange(state.closeAiDrawer()) },
            onEntrySelected = onAiDrawerEntrySelected,
            modifier = Modifier.fillMaxSize().zIndex(40f),
        )
    }
}

internal fun kiyoriStartupBeyondViewportPageCount(startupPreloadReady: Boolean): Int =
    if (startupPreloadReady) 1 else 0

internal fun shouldComposeKiyoriAiHost(
    aiHostIsRoot: Boolean,
    softwareHomePage: SoftwareHomePage,
    startupPreloadReady: Boolean,
): Boolean =
    startupPreloadReady || !aiHostIsRoot || softwareHomePage == SoftwareHomePage.AI_HOME

internal fun shouldNotifyKiyoriAiHomeSettledForInitialPage(
    initialSettledPage: SoftwareHomePage,
    requestedPage: SoftwareHomePage,
): Boolean =
    initialSettledPage == SoftwareHomePage.AI_HOME &&
        requestedPage == SoftwareHomePage.AI_HOME

internal fun shouldProvideKiyoriSettingsTheme(route: KiyoriSettingsRoute?): Boolean =
    route != null && route != KiyoriSettingsRoute.HOME

internal fun shouldPresentKiyoriSettingsOverlay(state: KiyoriShellState): Boolean {
    val navigation = state.settingsNavigation ?: return false
    if (
        navigation.presentation == KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL ||
            navigation.presentation ==
            KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE
    ) {
        return false
    }
    return !(
        navigation.origin == KiyoriSettingsOrigin.BOTTOM_NAVIGATION &&
            navigation.presentation == KiyoriSettingsPresentation.PRIMARY_ROOT &&
            navigation.currentRoute == KiyoriSettingsRoute.HOME
    )
}

internal fun calculateKiyoriAiHostTranslation(
    pageOffset: Float,
    viewportWidthPx: Float,
): Float = -pageOffset * viewportWidthPx

internal fun shouldReverseKiyoriPagerDrag(layoutDirection: LayoutDirection): Boolean =
    layoutDirection == LayoutDirection.Ltr

internal fun shouldPresentKiyoriDownloadDrawer(
    isDownloadDrawerOpen: Boolean,
    aiHostIsRoot: Boolean,
): Boolean {
    // The download drawer belongs to the Shell. The retained AI route may be a child screen while
    // Minus-One is visible, so AI route depth must never cancel or hide this explicit user action.
    return isDownloadDrawerOpen
}

internal fun shouldPresentKiyoriBookmarkDrawer(
    isBookmarkDrawerOpen: Boolean,
    aiHostIsRoot: Boolean,
): Boolean = isBookmarkDrawerOpen

internal fun shouldPresentKiyoriHistoryDrawer(
    isHistoryDrawerOpen: Boolean,
    aiHostIsRoot: Boolean,
): Boolean = isHistoryDrawerOpen

internal fun shouldEnableKiyoriShellBackHandler(
    aiHostIsRoot: Boolean,
    isAiDrawerOpen: Boolean,
    isBookmarkDrawerOpen: Boolean,
    isHistoryDrawerOpen: Boolean,
    isDownloadDrawerOpen: Boolean,
    settingsNavigation: KiyoriSettingsNavigationState?,
): Boolean =
    settingsNavigation == null &&
        (
            isBookmarkDrawerOpen ||
                isHistoryDrawerOpen ||
                isDownloadDrawerOpen ||
                (aiHostIsRoot && !isAiDrawerOpen)
        )

internal fun shouldEnableKiyoriSettingsHostBackHandler(
    settingsNavigation: KiyoriSettingsNavigationState?,
): Boolean = isKiyoriSettingsBackOwnedByShell(settingsNavigation)

@OptIn(ExperimentalFoundationApi::class)
internal fun calculateKiyoriPagerPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

internal fun resolveKiyoriBottomBarAlpha(
    state: KiyoriShellState,
    aiHostIsRoot: Boolean,
    centerPageOffset: Float,
): Float =
    when {
        !aiHostIsRoot ||
            state.child != null ||
            (
                state.settingsNavigation != null &&
                    !isKiyoriBottomNavigationSettingsHome(state)
            ) ||
            state.isAiDrawerOpen ||
            state.isBookmarkDrawerOpen ||
            state.isHistoryDrawerOpen ||
            state.isDownloadDrawerOpen ->
            0f
        state.primaryDestination == PrimaryDestination.SOFTWARE_HOME ->
            // The pager is the source of truth during a drag. Waiting for softwareHomePage to
            // settle would keep the navigation tree absent until the gesture has already ended.
            (1f - abs(centerPageOffset)).coerceIn(0f, 1f)
        !state.showsBottomBar -> 0f
        else -> 1f
    }

internal fun isKiyoriBottomNavigationSettingsHome(state: KiyoriShellState): Boolean {
    val navigation = state.settingsNavigation ?: return false
    return state.primaryDestination == PrimaryDestination.SETTINGS_HOME &&
        navigation.origin == KiyoriSettingsOrigin.BOTTOM_NAVIGATION &&
        navigation.presentation == KiyoriSettingsPresentation.PRIMARY_ROOT &&
        navigation.currentRoute == KiyoriSettingsRoute.HOME
}
