package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserAdMarkingTouchPolicyTest {
    @Test
    fun `tap selects the release point without scrolling`() {
        val tracker = BrowserAdMarkingTouchTracker(touchSlopPx = 8f)

        assertEquals(BrowserAdMarkingTouchAction.None, tracker.onDown(20f, 30f))
        assertEquals(BrowserAdMarkingTouchAction.None, tracker.onMove(24f, 34f))
        assertEquals(
            BrowserAdMarkingTouchAction.Select(xPx = 24f, yPx = 34f),
            tracker.onUp(24f, 34f),
        )
    }

    @Test
    fun `vertical drag scrolls and never selects on release`() {
        val tracker = BrowserAdMarkingTouchTracker(touchSlopPx = 8f)

        tracker.onDown(30f, 120f)
        assertEquals(
            BrowserAdMarkingTouchAction.ScrollBy(deltaYPx = 20f),
            tracker.onMove(31f, 100f),
        )
        assertEquals(
            BrowserAdMarkingTouchAction.ScrollBy(deltaYPx = 25f),
            tracker.onMove(32f, 75f),
        )
        assertEquals(BrowserAdMarkingTouchAction.EndScroll, tracker.onUp(32f, 75f))
    }

    @Test
    fun `horizontal movement beyond slop prevents accidental selection`() {
        val tracker = BrowserAdMarkingTouchTracker(touchSlopPx = 8f)

        tracker.onDown(20f, 40f)
        assertEquals(BrowserAdMarkingTouchAction.None, tracker.onMove(40f, 42f))
        assertEquals(BrowserAdMarkingTouchAction.EndGesture, tracker.onUp(40f, 42f))
    }

    @Test
    fun `cancel clears the gesture before the next tap`() {
        val tracker = BrowserAdMarkingTouchTracker(touchSlopPx = 8f)

        tracker.onDown(10f, 90f)
        tracker.onMove(10f, 60f)
        tracker.cancel()
        assertEquals(BrowserAdMarkingTouchAction.None, tracker.onUp(10f, 60f))

        tracker.onDown(50f, 70f)
        assertEquals(
            BrowserAdMarkingTouchAction.Select(xPx = 50f, yPx = 70f),
            tracker.onUp(50f, 70f),
        )
    }
}
