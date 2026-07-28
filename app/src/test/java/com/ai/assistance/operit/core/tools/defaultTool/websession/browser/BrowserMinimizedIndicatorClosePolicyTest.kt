package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMinimizedIndicatorClosePolicyTest {
    @Test
    fun `long press shows close action without scheduling its removal`() {
        val transition =
            BrowserMinimizedIndicatorClosePolicy.reduce(
                BrowserMinimizedIndicatorCloseState(),
                BrowserMinimizedIndicatorCloseEvent.LONG_PRESS_RECOGNIZED,
            )

        assertTrue(transition.state.isCloseActionVisible)
        assertTrue(transition.state.isLongPressGestureActive)
        assertTrue(transition.cancelPendingHide)
        assertNull(transition.scheduleHideAfterMillis)
    }

    @Test
    fun `long press release keeps close action visible for three seconds`() {
        val pressed =
            BrowserMinimizedIndicatorClosePolicy.reduce(
                BrowserMinimizedIndicatorCloseState(),
                BrowserMinimizedIndicatorCloseEvent.LONG_PRESS_RECOGNIZED,
            ).state

        val released =
            BrowserMinimizedIndicatorClosePolicy.reduce(
                pressed,
                BrowserMinimizedIndicatorCloseEvent.GESTURE_FINISHED,
            )

        assertTrue(released.state.isCloseActionVisible)
        assertFalse(released.state.isLongPressGestureActive)
        assertEquals(
            BROWSER_MINIMIZED_INDICATOR_CLOSE_TIMEOUT_MILLIS,
            released.scheduleHideAfterMillis,
        )
    }

    @Test
    fun `ordinary gesture finish does not show or schedule close action`() {
        val transition =
            BrowserMinimizedIndicatorClosePolicy.reduce(
                BrowserMinimizedIndicatorCloseState(),
                BrowserMinimizedIndicatorCloseEvent.GESTURE_FINISHED,
            )

        assertFalse(transition.state.isCloseActionVisible)
        assertFalse(transition.state.isLongPressGestureActive)
        assertFalse(transition.cancelPendingHide)
        assertNull(transition.scheduleHideAfterMillis)
    }

    @Test
    fun `timeout and reset both clear close action state`() {
        val visible =
            BrowserMinimizedIndicatorCloseState(
                isCloseActionVisible = true,
                isLongPressGestureActive = false,
            )

        listOf(
            BrowserMinimizedIndicatorCloseEvent.HIDE_TIMEOUT,
            BrowserMinimizedIndicatorCloseEvent.RESET,
        ).forEach { event ->
            val transition =
                BrowserMinimizedIndicatorClosePolicy.reduce(
                    visible,
                    event,
                )

            assertEquals(BrowserMinimizedIndicatorCloseState(), transition.state)
            assertTrue(transition.cancelPendingHide)
            assertNull(transition.scheduleHideAfterMillis)
        }
    }

    @Test
    fun `close overlay accepts touch only after the long press gesture ends`() {
        val holdingFlags =
            BrowserMinimizedIndicatorCloseOverlayPolicy.windowFlags(
                isLongPressGestureActive = true,
            )
        val releasedFlags =
            BrowserMinimizedIndicatorCloseOverlayPolicy.windowFlags(
                isLongPressGestureActive = false,
            )

        assertTrue(holdingFlags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertTrue(holdingFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(holdingFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertTrue(holdingFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertFalse(releasedFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }

    @Test
    fun `close overlay lifecycle follows visibility and gesture phase changes`() {
        val hidden = BrowserMinimizedIndicatorCloseState()
        val holding =
            BrowserMinimizedIndicatorCloseState(
                isCloseActionVisible = true,
                isLongPressGestureActive = true,
            )
        val released = holding.copy(isLongPressGestureActive = false)

        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayUpdate.SHOW,
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolveUpdate(hidden, holding),
        )
        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayUpdate.UPDATE_TOUCHABILITY,
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolveUpdate(holding, released),
        )
        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayUpdate.HIDE,
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolveUpdate(released, hidden),
        )
        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayUpdate.NONE,
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolveUpdate(released, released),
        )
    }

    @Test
    fun `close overlay sits outside the indicator upper right when space is available`() {
        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayPosition(x = 130, y = 82),
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolvePosition(
                indicatorX = 100,
                indicatorY = 100,
                indicatorWidth = 40,
                closeActionSize = 28,
                overlap = 10,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    @Test
    fun `close overlay remains inside top and right screen edges`() {
        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayPosition(x = 1052, y = 0),
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolvePosition(
                indicatorX = 1040,
                indicatorY = 0,
                indicatorWidth = 40,
                closeActionSize = 28,
                overlap = 10,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    @Test
    fun `close overlay handles a screen smaller than its own touch target`() {
        assertEquals(
            BrowserMinimizedIndicatorCloseOverlayPosition(x = 0, y = 0),
            BrowserMinimizedIndicatorCloseOverlayPolicy.resolvePosition(
                indicatorX = 0,
                indicatorY = 0,
                indicatorWidth = 10,
                closeActionSize = 28,
                overlap = 10,
                screenWidth = 20,
                screenHeight = 20,
            ),
        )
    }
}
