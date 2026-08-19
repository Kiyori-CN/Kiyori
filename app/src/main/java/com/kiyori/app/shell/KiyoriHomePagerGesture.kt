package com.kiyori.app.shell

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.sign

internal const val KIYORI_HOME_PAGER_POSITION_THRESHOLD = 0.5f
internal const val KIYORI_HOME_PAGER_MIN_FLING_VELOCITY_DP_PER_SECOND = 400f

internal enum class KiyoriHomePagerSnapReason {
    VELOCITY,
    POSITION,
    RETURN_TO_ORIGIN,
    BOUNDARY,
    CANCELLED,
}

internal data class KiyoriHomePagerSnapInput(
    val originPage: Int,
    val pageCount: Int,
    val physicalDragX: Float,
    val physicalVelocityX: Float,
    val pageSizePx: Float,
    val minimumFlingVelocityPxPerSecond: Float,
    val layoutDirection: LayoutDirection,
    val cancelled: Boolean = false,
    val sessionComplete: Boolean = true,
)

internal data class KiyoriHomePagerSnapDecision(
    val targetPage: Int,
    val reason: KiyoriHomePagerSnapReason,
)

internal fun resolveKiyoriHomePagerSnapTarget(
    input: KiyoriHomePagerSnapInput,
): KiyoriHomePagerSnapDecision {
    require(input.pageCount > 0) { "Home pager requires at least one page." }
    require(input.originPage in 0 until input.pageCount) {
        "Home pager origin ${input.originPage} is outside 0 until ${input.pageCount}."
    }
    require(input.sessionComplete) { "Home pager snap requires a completed gesture session." }
    require(input.pageSizePx > 0f) { "Home pager snap requires a measured page size." }
    require(input.minimumFlingVelocityPxPerSecond > 0f) {
        "Home pager snap requires a positive fling velocity threshold."
    }
    if (input.cancelled) {
        return KiyoriHomePagerSnapDecision(
            targetPage = input.originPage,
            reason = KiyoriHomePagerSnapReason.CANCELLED,
        )
    }

    val directionSource: Float
    val reason: KiyoriHomePagerSnapReason
    if (abs(input.physicalVelocityX) >= input.minimumFlingVelocityPxPerSecond) {
        directionSource = input.physicalVelocityX
        reason = KiyoriHomePagerSnapReason.VELOCITY
    } else if (
        abs(input.physicalDragX) / input.pageSizePx > KIYORI_HOME_PAGER_POSITION_THRESHOLD
    ) {
        directionSource = input.physicalDragX
        reason = KiyoriHomePagerSnapReason.POSITION
    } else {
        return KiyoriHomePagerSnapDecision(
            targetPage = input.originPage,
            reason = KiyoriHomePagerSnapReason.RETURN_TO_ORIGIN,
        )
    }

    val physicalDirection = directionSource.sign.toInt()
    val pageDelta =
        when (input.layoutDirection) {
            LayoutDirection.Ltr -> -physicalDirection
            LayoutDirection.Rtl -> physicalDirection
        }
    val targetPage = (input.originPage + pageDelta).coerceIn(0, input.pageCount - 1)
    return KiyoriHomePagerSnapDecision(
        targetPage = targetPage,
        reason =
            if (targetPage == input.originPage) {
                KiyoriHomePagerSnapReason.BOUNDARY
            } else {
                reason
            },
    )
}

private data class KiyoriHomePagerGestureSnapshot(
    val originPage: Int,
    val downX: Float,
    var currentX: Float,
    val pageSizePx: Float,
    val minimumFlingVelocityPxPerSecond: Float,
    val layoutDirection: LayoutDirection,
    var cancelled: Boolean = false,
    var complete: Boolean = false,
)

internal class KiyoriHomePagerGestureSession {
    private var current: KiyoriHomePagerGestureSnapshot? = null

    val acceptsPagerDragDelta: Boolean
        get() = current?.let { snapshot -> !snapshot.complete && !snapshot.cancelled } == true

    fun begin(
        originPage: Int,
        downX: Float,
        pageSizePx: Float,
        minimumFlingVelocityPxPerSecond: Float,
        layoutDirection: LayoutDirection,
    ) {
        current =
            KiyoriHomePagerGestureSnapshot(
                originPage = originPage,
                downX = downX,
                currentX = downX,
                pageSizePx = pageSizePx,
                minimumFlingVelocityPxPerSecond = minimumFlingVelocityPxPerSecond,
                layoutDirection = layoutDirection,
            )
    }

    fun update(currentX: Float) {
        current?.currentX = currentX
    }

    fun finish(upX: Float) {
        current?.apply {
            currentX = upX
            complete = true
        }
    }

    fun cancel() {
        current?.apply {
            cancelled = true
            complete = true
        }
    }

    fun discard() {
        current = null
    }

    fun consumeCompleted(
        pageCount: Int,
        physicalVelocityX: Float,
    ): KiyoriHomePagerSnapInput? {
        val snapshot = current ?: return null
        current = null
        if (!snapshot.complete) {
            return null
        }
        return KiyoriHomePagerSnapInput(
            originPage = snapshot.originPage,
            pageCount = pageCount,
            physicalDragX = snapshot.currentX - snapshot.downX,
            physicalVelocityX = physicalVelocityX,
            pageSizePx = snapshot.pageSizePx,
            minimumFlingVelocityPxPerSecond = snapshot.minimumFlingVelocityPxPerSecond,
            layoutDirection = snapshot.layoutDirection,
            cancelled = snapshot.cancelled,
            sessionComplete = snapshot.complete,
        )
    }
}

/**
 * AI Home stays outside HorizontalPager, so it cannot use Pager's private down/up gesture metadata.
 * This bridge keeps the single PagerState for finger-following movement while owning only the AI
 * release session and settle decision.
 */
@Stable
@OptIn(ExperimentalFoundationApi::class)
internal class KiyoriAiHomePagerGestureBridge(
    private val pagerState: PagerState,
) {
    private val gestureSession = KiyoriHomePagerGestureSession()
    private var pageSizePx: Float = 0f
    private var minimumFlingVelocityPxPerSecond: Float = 0f
    private var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    private var settlingTargetPage: Int? = null
    private var externalGestureBlocked: Boolean = false
    private var ownedContentGestureBlocked: Boolean = false
    private val contentGestureBlocked: Boolean
        get() = externalGestureBlocked || ownedContentGestureBlocked
    // A child fling can outlive the pointer owner that began it. Without an unfinished real Home
    // drag or this bridge's own settle, accepting that unconsumed delta can strand Pager mid-page
    // because there is intentionally no Home snap decision for the child input.
    private val acceptsPagerDelta: Boolean
        get() =
            !contentGestureBlocked &&
                (gestureSession.acceptsPagerDragDelta || settlingTargetPage != null)

    val scrollableState: ScrollableState =
        object : ScrollableState by pagerState {
            // A down event must remain a click until horizontal touch slop is crossed. The real
            // PagerState still owns its scroll mutex, so an actual drag interrupts an old spring.
            override val isScrollInProgress: Boolean
                get() = false

            override suspend fun scroll(
                scrollPriority: MutatePriority,
                block: suspend ScrollScope.() -> Unit,
            ) {
                pagerState.scroll(scrollPriority) {
                    val pagerScrollScope = this
                    val contentAwareScope =
                        object : ScrollScope {
                            override fun scrollBy(pixels: Float): Float {
                                return if (!acceptsPagerDelta) {
                                    0f
                                } else {
                                    pagerScrollScope.scrollBy(pixels)
                                }
                            }
                        }
                    contentAwareScope.block()
                }
            }

            override fun dispatchRawDelta(delta: Float): Float {
                return if (!acceptsPagerDelta) {
                    0f
                } else {
                    pagerState.dispatchRawDelta(delta)
                }
            }
        }

    val flingBehavior: FlingBehavior =
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val configuredLayoutDirection = layoutDirection
                val physicalVelocity =
                    if (shouldReverseKiyoriPagerDrag(configuredLayoutDirection)) {
                        -initialVelocity
                    } else {
                        initialVelocity
                    }
                val input =
                    gestureSession.consumeCompleted(
                        pageCount = pagerState.pageCount,
                        physicalVelocityX = physicalVelocity,
                    )
                        // scrollable is also a nested-scroll ancestor. Child lists, tables and
                        // WebViews may dispatch post-fling without ever starting an AI Home drag;
                        // that input must stay outside the strict page-snap policy.
                        ?: return initialVelocity
                val decision = resolveKiyoriHomePagerSnapTarget(input)
                val targetPage = decision.targetPage
                val settleDistancePx =
                    pagerState.getOffsetDistanceInPages(targetPage) * input.pageSizePx
                if (abs(settleDistancePx) <= 0.01f) {
                    return 0f
                }

                settlingTargetPage = targetPage
                try {
                    with(pagerState) { updateTargetPage(targetPage) }
                    val lowerBound = minOf(0f, settleDistancePx)
                    val upperBound = maxOf(0f, settleDistancePx)
                    val settleVelocity =
                        if (initialVelocity.sign == settleDistancePx.sign) {
                            initialVelocity
                        } else {
                            0f
                        }
                    var consumedDistancePx = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = settleDistancePx,
                        initialVelocity = settleVelocity,
                        animationSpec =
                            spring(
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = Int.VisibilityThreshold.toFloat(),
                            ),
                    ) { animatedDistancePx, _ ->
                        val boundedDistancePx =
                            animatedDistancePx.coerceIn(lowerBound, upperBound)
                        val deltaPx = boundedDistancePx - consumedDistancePx
                        if (abs(deltaPx) > 0.01f) {
                            consumedDistancePx += scrollBy(deltaPx)
                        }
                    }
                    val finalDeltaPx = settleDistancePx - consumedDistancePx
                    if (abs(finalDeltaPx) > 0.01f) {
                        scrollBy(finalDeltaPx)
                    }
                } finally {
                    if (settlingTargetPage == targetPage) {
                        settlingTargetPage = null
                    }
                }
                return 0f
            }
        }

    fun updateConfiguration(
        pageSizePx: Float,
        minimumFlingVelocityPxPerSecond: Float,
        layoutDirection: LayoutDirection,
        externalGestureBlocked: Boolean,
    ) {
        this.pageSizePx = pageSizePx
        this.minimumFlingVelocityPxPerSecond = minimumFlingVelocityPxPerSecond
        this.layoutDirection = layoutDirection
        this.externalGestureBlocked = externalGestureBlocked
        if (contentGestureBlocked) {
            // Keep the ancestor scrollable node mounted for the whole pointer stream. Marking the
            // current page session cancelled prevents a child-owned gesture from settling Pager.
            gestureSession.cancel()
        }
    }

    fun updateContentGestureOwnership(isOwned: Boolean) {
        ownedContentGestureBlocked = isOwned
        if (isOwned) {
            // Content owners call this synchronously from DOWN, before their drag or nested fling
            // can move Pager. Discarding that candidate keeps child input outside page-snap policy.
            gestureSession.discard()
        }
    }

    fun beginGesture(downX: Float) {
        if (contentGestureBlocked) {
            return
        }
        val originPage =
            settlingTargetPage
                ?: if (pagerState.isScrollInProgress) {
                    pagerState.targetPage
                } else {
                    pagerState.settledPage
                }
        gestureSession.begin(
            originPage = originPage,
            downX = downX,
            pageSizePx = pageSizePx,
            minimumFlingVelocityPxPerSecond = minimumFlingVelocityPxPerSecond,
            layoutDirection = layoutDirection,
        )
    }

    fun updateGesture(currentX: Float) {
        gestureSession.update(currentX)
    }

    fun finishGesture(upX: Float) {
        gestureSession.finish(upX)
    }

    fun cancelGesture() {
        gestureSession.cancel()
    }
}

internal fun Modifier.observeKiyoriAiHomePagerGesture(
    bridge: KiyoriAiHomePagerGestureBridge,
    enabled: Boolean,
): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(bridge, enabled) {
        try {
            awaitEachGesture {
                val down =
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                bridge.beginGesture(down.position.x)
                var lastX = down.position.x
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val activeChange = event.changes.firstOrNull { change -> change.id == down.id }
                    if (activeChange != null) {
                        lastX = activeChange.position.x
                        bridge.updateGesture(lastX)
                    }
                    if (event.changes.count { change -> change.pressed } > 1) {
                        cancelled = true
                        bridge.cancelGesture()
                    }
                    if (event.changes.none { change -> change.pressed }) {
                        if (cancelled) {
                            bridge.cancelGesture()
                        } else {
                            bridge.finishGesture(lastX)
                        }
                        break
                    }
                }
            }
        } finally {
            bridge.cancelGesture()
        }
    }
}
