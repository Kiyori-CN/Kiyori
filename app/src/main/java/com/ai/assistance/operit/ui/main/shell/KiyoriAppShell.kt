package com.ai.assistance.operit.ui.main.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
    selectedAiRouteId: String,
    pluginEntries: List<NavigationEntrySpec>,
    onAiCenterDestinationSelected: (AiCenterDestination) -> Unit,
    onPluginEntrySelected: (NavigationEntrySpec) -> Unit,
    onRequestExit: () -> Unit,
    aiHost: @Composable () -> Unit,
) {
    val pagerState =
        rememberPagerState(
            initialPage = state.softwareHomePage.pagerIndex,
            pageCount = { SoftwareHomePage.entries.size },
        )
    val latestState by rememberUpdatedState(state)
    val latestOnStateChange by rememberUpdatedState(onStateChange)

    LaunchedEffect(state.primaryDestination, state.softwareHomePage) {
        if (
            state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                pagerState.settledPage != state.softwareHomePage.pagerIndex
        ) {
            pagerState.animateScrollToPage(state.softwareHomePage.pagerIndex)
        }
    }

    LaunchedEffect(pagerState, state.primaryDestination, state.child, aiHostIsRoot) {
        if (
            state.primaryDestination != PrimaryDestination.SOFTWARE_HOME ||
                state.child != null ||
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

    BackHandler(enabled = aiHostIsRoot) {
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
                state.child == null

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            userScrollEnabled = pagerAcceptsInput,
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
            KiyoriPrimaryRootPage(
                destination = state.primaryDestination,
                modifier = Modifier.fillMaxSize().zIndex(4f),
            )
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
                    .kiyoriAiHomeSwipeToCenter(
                        enabled =
                            aiHostIsRoot &&
                                !aiHomeGestureBlocked &&
                                state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                                state.softwareHomePage == SoftwareHomePage.AI_HOME &&
                                state.child == null,
                        onNavigate = {
                            onStateChange(
                                latestState.showSoftwareHomePage(SoftwareHomePage.HOME),
                            )
                        },
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

        if (state.child == KiyoriShellChild.AI_CENTER && aiHostIsRoot) {
            KiyoriAiCenterPage(
                selectedRouteId = selectedAiRouteId,
                pluginEntries = pluginEntries,
                onDestinationSelected = onAiCenterDestinationSelected,
                onPluginEntrySelected = onPluginEntrySelected,
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
    }
}

internal fun calculateKiyoriAiHostTranslation(
    pageOffset: Float,
    viewportWidthPx: Float,
): Float = -pageOffset * viewportWidthPx

private fun Modifier.kiyoriAiHomeSwipeToCenter(
    enabled: Boolean,
    onNavigate: () -> Unit,
): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(onNavigate) {
        val horizontalThreshold = viewConfiguration.touchSlop * 3f
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var totalX = 0f
            var totalY = 0f
            var navigated = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull() ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (
                    !navigated &&
                        !change.isConsumed &&
                        totalX > horizontalThreshold &&
                        totalX > abs(totalY) * 1.25f
                ) {
                    navigated = true
                    onNavigate()
                }
            } while (event.changes.any { change -> change.pressed })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun calculateKiyoriPagerPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
