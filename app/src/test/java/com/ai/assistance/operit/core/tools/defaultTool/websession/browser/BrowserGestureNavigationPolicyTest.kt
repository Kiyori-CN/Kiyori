package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserGestureNavigationPolicyTest {
    private fun tracker() =
        BrowserGestureNavigationTracker(
            BrowserGestureNavigationConfiguration(
                edgeWidthPx = 48f,
                commitDistancePx = 72f,
                fastSwipeVelocityPxPerSecond = 800f,
            ),
        )

    @Test
    fun `left and right edges commit Back and Forward once`() {
        val back = tracker()
        back.onDown(x = 20f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.BACK,
            back.onUp(x = 110f, y = 105f, velocityXPxPerSecond = 100f),
        )
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            back.onUp(x = 140f, y = 105f, velocityXPxPerSecond = 100f),
        )

        val forward = tracker()
        forward.onDown(x = 485f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.FORWARD,
            forward.onUp(x = 390f, y = 104f, velocityXPxPerSecond = -100f),
        )
    }

    @Test
    fun `swiping toward the matching edge commits the same history action`() {
        val back = tracker()
        back.onDown(x = 220f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.BACK,
            back.onUp(x = 470f, y = 105f, velocityXPxPerSecond = 100f),
        )

        val forward = tracker()
        forward.onDown(x = 280f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.FORWARD,
            forward.onUp(x = 30f, y = 104f, velocityXPxPerSecond = -100f),
        )
    }

    @Test
    fun `interior horizontal swipes that do not reach an edge stay page owned`() {
        val tracker = tracker()
        tracker.onDown(x = 220f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            tracker.onUp(x = 360f, y = 104f, velocityXPxPerSecond = 100f),
        )
    }

    @Test
    fun `distance velocity direction and vertical dominance gate gestures`() {
        val short = tracker()
        short.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            short.onUp(x = 50f, y = 105f, velocityXPxPerSecond = 300f),
        )

        val fast = tracker()
        fast.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.BACK,
            fast.onUp(x = 55f, y = 105f, velocityXPxPerSecond = 900f),
        )

        val vertical = tracker()
        vertical.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            vertical.onUp(x = 100f, y = 180f, velocityXPxPerSecond = 1_000f),
        )

        val reverse = tracker()
        reverse.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            reverse.onUp(x = 0f, y = 100f, velocityXPxPerSecond = -1_000f),
        )
    }

    @Test
    fun `disabled multi-pointer and cancelled gestures do nothing`() {
        val disabled = tracker()
        disabled.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = false, pointerCount = 1)
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            disabled.onUp(x = 120f, y = 100f, velocityXPxPerSecond = 1_000f),
        )

        val multiPointer = tracker()
        multiPointer.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        multiPointer.onPointerCountChanged(2)
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            multiPointer.onUp(x = 120f, y = 100f, velocityXPxPerSecond = 1_000f),
        )

        val cancelled = tracker()
        cancelled.onDown(x = 10f, y = 100f, viewportWidthPx = 500f, enabled = true, pointerCount = 1)
        cancelled.cancel()
        assertEquals(
            BrowserGestureNavigationAction.NONE,
            cancelled.onUp(x = 120f, y = 100f, velocityXPxPerSecond = 1_000f),
        )
    }
}
