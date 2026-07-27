package com.ai.assistance.operit.ui.main.shell

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
import com.ai.assistance.operit.ui.theme.KiyoriBrowserTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

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
    onOpenBookmark: (String) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    onOpenAiSettingsFromKiyoriSettings: () -> Unit,
    onOpenBrowserSettingsFromKiyoriSettings: () -> Unit,
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
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
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

    BackHandler(
        enabled =
            shouldEnableKiyoriShellBackHandler(
                aiHostIsRoot = aiHostIsRoot,
                isAiDrawerOpen = state.isAiDrawerOpen,
                isBookmarkDrawerOpen = state.isBookmarkDrawerOpen,
                isDownloadDrawerOpen = state.isDownloadDrawerOpen,
            ),
    ) {
        val transition = latestState.handleBack()
        when (transition.result) {
            KiyoriShellBackResult.CONSUMED -> latestOnStateChange(transition.state)
            KiyoriShellBackResult.REQUEST_EXIT -> onRequestExit()
        }
    }

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
        val homeBottomBarAlpha = 1f - abs(centerPageOffset).coerceIn(0f, 1f)
        val pagerAcceptsInput =
            aiHostIsRoot &&
                state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                state.child == null &&
                !state.isAiDrawerOpen &&
                !state.isBookmarkDrawerOpen &&
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
                        onOpenDownloadDrawer = {
                            latestOnStateChange(latestState.openDownloadDrawer())
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
                    onOpenAiSettings = onOpenAiSettingsFromKiyoriSettings,
                    onOpenBrowserSettings = onOpenBrowserSettingsFromKiyoriSettings,
                    onOpenDownloadSettings = {
                        onStateChange(state.openChild(KiyoriShellChild.DOWNLOAD_SETTINGS))
                    },
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

        AnimatedVisibility(
            visible = state.child != null && aiHostIsRoot,
            modifier = Modifier.fillMaxSize().zIndex(12f),
            enter = fadeIn() + slideInVertically(initialOffsetY = { height -> height / 18 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { height -> height / 24 }),
        ) {
            KiyoriBrowserTheme {
                when (state.child) {
                    KiyoriShellChild.FULL_SCREEN_WEB_SEARCH ->
                        KiyoriFullScreenWebSearchPage(
                            onBack = { onStateChange(state.closeChild()) },
                            onSubmitSearch = onSubmitWebSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriShellChild.BROWSER_SETTINGS ->
                        KiyoriBrowserSettingsPage(
                            onBack = { onStateChange(state.closeChild()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriShellChild.DOWNLOAD_SETTINGS ->
                        KiyoriDownloadSettingsPage(
                            onBack = { onStateChange(state.closeChild()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    null -> Unit
                }
            }
        }

        val bottomBarAlpha =
            when {
                !aiHostIsRoot || state.child != null || !state.showsBottomBar -> 0f
                state.primaryDestination == PrimaryDestination.SOFTWARE_HOME -> homeBottomBarAlpha
                else -> 1f
            }
        if (bottomBarAlpha > 0.01f) {
                KiyoriBottomNavigation(
                    selectedDestination = state.primaryDestination,
                    alpha = bottomBarAlpha,
                    onDestinationSelected = { destination ->
                    onStateChange(
                        if (destination == PrimaryDestination.BROWSER_HOME) {
                            state.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME)
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
                        .openChild(KiyoriShellChild.DOWNLOAD_SETTINGS),
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
                onOpenBookmark(url)
                latestOnStateChange(
                    latestState.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME),
                )
            },
            onOpenBookmarkInTab = { url, active ->
                onOpenBookmarkInTab(url, active)
                if (active) {
                    latestOnStateChange(
                        latestState.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME),
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

internal fun shouldEnableKiyoriShellBackHandler(
    aiHostIsRoot: Boolean,
    isAiDrawerOpen: Boolean,
    isBookmarkDrawerOpen: Boolean,
    isDownloadDrawerOpen: Boolean,
): Boolean = isBookmarkDrawerOpen || isDownloadDrawerOpen || (aiHostIsRoot && !isAiDrawerOpen)

@OptIn(ExperimentalFoundationApi::class)
internal fun calculateKiyoriPagerPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
