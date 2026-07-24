package com.ai.assistance.operit.ui.main.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KiyoriAppShell(
    state: KiyoriShellState,
    onStateChange: (KiyoriShellState) -> Unit,
    aiHostIsRoot: Boolean,
    aiHomeGestureBlocked: Boolean,
    selectedAiEntryId: String?,
    aiDrawerEntries: List<NavigationEntrySpec>,
    isNetworkAvailable: Boolean,
    networkType: String,
    onAiDrawerEntrySelected: (NavigationEntrySpec) -> Unit,
    onOpenAiSettingsFromKiyoriSettings: () -> Unit,
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
        aiHostIsRoot,
    ) {
        if (
            state.primaryDestination != PrimaryDestination.SOFTWARE_HOME ||
                state.child != null ||
                state.isAiDrawerOpen ||
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
            }
    }

    BackHandler(enabled = aiHostIsRoot && !state.isAiDrawerOpen) {
        val transition = state.handleBack()
        when (transition.result) {
            KiyoriShellBackResult.CONSUMED -> onStateChange(transition.state)
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
                !state.isAiDrawerOpen

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            userScrollEnabled = pagerAcceptsInput,
            flingBehavior = pagerFlingBehavior,
            beyondViewportPageCount = 1,
            key = { page -> page },
        ) { page ->
            when (SoftwareHomePage.fromPagerIndex(page)) {
                SoftwareHomePage.MINUS_ONE -> KiyoriMinusOnePage()
                SoftwareHomePage.HOME ->
                    KiyoriSoftwareHomePage(
                        onSearchClick = {
                            onStateChange(
                                state.openChild(KiyoriShellChild.FULL_SCREEN_WEB_SEARCH),
                            )
                        },
                        onAiClick = {
                            onStateChange(
                                state.showSoftwareHomePage(SoftwareHomePage.AI_HOME),
                            )
                        },
                    )
                SoftwareHomePage.AI_HOME -> Unit
            }
        }

        if (state.primaryDestination != PrimaryDestination.SOFTWARE_HOME) {
            if (state.primaryDestination == PrimaryDestination.BROWSER_HOME) {
                browserHome(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                        .zIndex(4f),
                )
            } else {
                KiyoriPrimaryRootPage(
                    destination = state.primaryDestination,
                    onOpenAiSettings = onOpenAiSettingsFromKiyoriSettings,
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
            aiHost()
        }

        if (state.child == KiyoriShellChild.FULL_SCREEN_WEB_SEARCH && aiHostIsRoot) {
            KiyoriFullScreenWebSearchPage(
                onBack = { onStateChange(state.copy(child = null)) },
                modifier = Modifier.fillMaxSize().zIndex(12f),
            )
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
                    onStateChange(state.selectPrimary(destination))
                },
                modifier = Modifier.zIndex(10f),
            )
        }

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

internal fun calculateKiyoriAiHostTranslation(
    pageOffset: Float,
    viewportWidthPx: Float,
): Float = -pageOffset * viewportWidthPx

internal fun shouldReverseKiyoriPagerDrag(layoutDirection: LayoutDirection): Boolean =
    layoutDirection == LayoutDirection.Ltr

@OptIn(ExperimentalFoundationApi::class)
internal fun calculateKiyoriPagerPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
