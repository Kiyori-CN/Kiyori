package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.common.gestures.AiContentHorizontalGestureOwnership
import com.ai.assistance.operit.ui.common.gestures.LocalAiContentHorizontalGestureOwnership
import com.ai.assistance.operit.ui.common.gestures.ownAiContentHorizontalGestureOnTouch
import com.ai.assistance.operit.ui.common.gestures.rememberAiContentHorizontalGestureOwner
import com.kiyori.app.shell.KIYORI_HOME_PAGER_MIN_FLING_VELOCITY_DP_PER_SECOND
import com.kiyori.app.shell.KiyoriAiHomePagerGestureBridge
import com.kiyori.app.shell.observeKiyoriAiHomePagerGesture
import com.kiyori.app.shell.shouldReverseKiyoriPagerDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val HOST_TAG = "kiyori-pager-bridge-host"
private const val CONTENT_TAG = "kiyori-horizontal-content"

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class KiyoriHomePagerGestureAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun slowDragUsesStrictHalfPagePositionThreshold() {
        lateinit var pagerState: PagerState

        composeTestRule.setContent {
            MaterialTheme {
                val state = rememberPagerState(initialPage = 1, pageCount = { 3 })
                pagerState = state
                KiyoriPagerBridgeTestHost(
                    pagerState = state,
                    contentOwnsGesture = false,
                )
            }
        }
        composeTestRule.waitForIdle()

        val host = composeTestRule.onNodeWithTag(HOST_TAG)
        val hostWidth = host.fetchSemanticsNode().boundsInRoot.width
        host.performTouchInput {
            swipe(
                start = Offset(hostWidth * 0.70f, centerY),
                end = Offset(hostWidth * 0.30f, centerY),
                durationMillis = 1_500L,
            )
        }
        waitForPagerIdle(pagerState)
        composeTestRule.runOnIdle {
            assertEquals(1, pagerState.settledPage)
        }

        host.performTouchInput {
            swipe(
                start = Offset(hostWidth * 0.80f, centerY),
                end = Offset(hostWidth * 0.20f, centerY),
                durationMillis = 1_500L,
            )
        }
        waitForPagerIdle(pagerState)
        composeTestRule.runOnIdle {
            assertEquals(2, pagerState.settledPage)
        }
    }

    @Test
    fun activeHorizontalContentOwnerPreventsHomePagerMovement() {
        lateinit var pagerState: PagerState
        var contentScrollValue = 0

        composeTestRule.setContent {
            MaterialTheme {
                val state = rememberPagerState(initialPage = 1, pageCount = { 3 })
                pagerState = state
                KiyoriPagerBridgeTestHost(
                    pagerState = state,
                    contentOwnsGesture = true,
                    onContentScrollValue = { contentScrollValue = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        val content = composeTestRule.onNodeWithTag(CONTENT_TAG)
        val contentWidth = content.fetchSemanticsNode().boundsInRoot.width
        content.performTouchInput {
            swipe(
                start = Offset(contentWidth * 0.80f, centerY),
                end = Offset(contentWidth * 0.20f, centerY),
                durationMillis = 1_500L,
            )
        }
        waitForPagerIdle(pagerState)
        composeTestRule.runOnIdle {
            assertEquals(1, pagerState.settledPage)
            assertTrue(contentScrollValue > 0)
        }
    }

    @Test
    fun outwardFlingAtContentStartCannotLeavePagerBetweenPages() {
        lateinit var pagerState: PagerState
        var contentScrollValue = 0

        composeTestRule.setContent {
            MaterialTheme {
                val state = rememberPagerState(initialPage = 1, pageCount = { 3 })
                pagerState = state
                KiyoriPagerBridgeTestHost(
                    pagerState = state,
                    contentOwnsGesture = true,
                    onContentScrollValue = { contentScrollValue = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        val content = composeTestRule.onNodeWithTag(CONTENT_TAG)
        val contentWidth = content.fetchSemanticsNode().boundsInRoot.width
        content.performTouchInput {
            swipe(
                start = Offset(contentWidth * 0.20f, centerY),
                end = Offset(contentWidth * 0.85f, centerY),
                durationMillis = 150L,
            )
        }
        composeTestRule.waitForIdle()
        waitForPagerIdle(pagerState)
        composeTestRule.runOnIdle {
            assertEquals(0, contentScrollValue)
            assertEquals(1, pagerState.settledPage)
            assertEquals(0f, pagerState.currentPageOffsetFraction, 0.001f)
        }
    }

    private fun waitForPagerIdle(pagerState: PagerState) {
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            !pagerState.isScrollInProgress
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun KiyoriPagerBridgeTestHost(
    pagerState: PagerState,
    contentOwnsGesture: Boolean,
    onContentScrollValue: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val bridge = remember(pagerState) { KiyoriAiHomePagerGestureBridge(pagerState) }
    val ownership =
        remember(bridge) {
            AiContentHorizontalGestureOwnership(
                onOwnershipChanged = bridge::updateContentGestureOwnership,
            )
        }
    val bridgeEnabled = pagerState.layoutInfo.pageSize > 0

    SideEffect {
        bridge.updateConfiguration(
            pageSizePx = pagerState.layoutInfo.pageSize.toFloat(),
            minimumFlingVelocityPxPerSecond =
                with(density) {
                    KIYORI_HOME_PAGER_MIN_FLING_VELOCITY_DP_PER_SECOND.dp.toPx()
                },
            layoutDirection = layoutDirection,
            externalGestureBlocked = false,
        )
    }

    Box(
        modifier =
            Modifier
                .width(300.dp)
                .height(200.dp)
                .testTag(HOST_TAG)
                .observeKiyoriAiHomePagerGesture(
                    bridge = bridge,
                    enabled = bridgeEnabled,
                )
                .scrollable(
                    state = bridge.scrollableState,
                    orientation = Orientation.Horizontal,
                    reverseDirection = shouldReverseKiyoriPagerDrag(layoutDirection),
                    enabled = bridgeEnabled,
                    flingBehavior = bridge.flingBehavior,
                ),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }

        if (contentOwnsGesture) {
            CompositionLocalProvider(
                LocalAiContentHorizontalGestureOwnership provides ownership,
            ) {
                val owner = rememberAiContentHorizontalGestureOwner()
                val contentScrollState = rememberScrollState()
                SideEffect {
                    onContentScrollValue(contentScrollState.value)
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag(CONTENT_TAG)
                            .ownAiContentHorizontalGestureOnTouch(
                                owner = owner,
                                enabled = true,
                            )
                            .horizontalScroll(contentScrollState),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(600.dp)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}
