package com.ai.assistance.operit.ui.main.shell

import androidx.compose.ui.unit.LayoutDirection
import com.kiyori.app.shell.KiyoriHomePagerGestureSession
import com.kiyori.app.shell.KiyoriHomePagerSnapInput
import com.kiyori.app.shell.KiyoriHomePagerSnapReason
import com.kiyori.app.shell.resolveKiyoriHomePagerSnapTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class KiyoriHomePagerGesturePolicyTest {
    @Test
    fun `position threshold is strictly greater than half a page`() {
        assertDecision(
            physicalDragX = 50f,
            expectedPage = 1,
            expectedReason = KiyoriHomePagerSnapReason.RETURN_TO_ORIGIN,
        )
        assertDecision(
            physicalDragX = 50.01f,
            expectedPage = 0,
            expectedReason = KiyoriHomePagerSnapReason.POSITION,
        )
        assertDecision(
            physicalDragX = -50.01f,
            expectedPage = 2,
            expectedReason = KiyoriHomePagerSnapReason.POSITION,
        )
    }

    @Test
    fun `velocity threshold is inclusive and overrides net drag direction`() {
        assertDecision(
            physicalDragX = 70f,
            physicalVelocityX = -399.99f,
            expectedPage = 0,
            expectedReason = KiyoriHomePagerSnapReason.POSITION,
        )
        assertDecision(
            physicalDragX = 70f,
            physicalVelocityX = -400f,
            expectedPage = 2,
            expectedReason = KiyoriHomePagerSnapReason.VELOCITY,
        )
    }

    @Test
    fun `rtl reverses physical direction without changing thresholds`() {
        assertDecision(
            physicalDragX = 51f,
            layoutDirection = LayoutDirection.Rtl,
            expectedPage = 2,
            expectedReason = KiyoriHomePagerSnapReason.POSITION,
        )
        assertDecision(
            physicalDragX = -51f,
            layoutDirection = LayoutDirection.Rtl,
            expectedPage = 0,
            expectedReason = KiyoriHomePagerSnapReason.POSITION,
        )
    }

    @Test
    fun `boundary and cancelled sessions stay on their origin`() {
        assertDecision(
            originPage = 0,
            physicalDragX = 51f,
            expectedPage = 0,
            expectedReason = KiyoriHomePagerSnapReason.BOUNDARY,
        )
        assertDecision(
            cancelled = true,
            physicalDragX = -90f,
            physicalVelocityX = -900f,
            expectedPage = 1,
            expectedReason = KiyoriHomePagerSnapReason.CANCELLED,
        )
    }

    @Test
    fun `invalid sessions expose the broken bridge contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveKiyoriHomePagerSnapTarget(
                KiyoriHomePagerSnapInput(
                    originPage = 1,
                    pageCount = 3,
                    physicalDragX = 0f,
                    physicalVelocityX = 0f,
                    pageSizePx = 0f,
                    minimumFlingVelocityPxPerSecond = 400f,
                    layoutDirection = LayoutDirection.Ltr,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveKiyoriHomePagerSnapTarget(
                KiyoriHomePagerSnapInput(
                    originPage = 1,
                    pageCount = 3,
                    physicalDragX = 0f,
                    physicalVelocityX = 0f,
                    pageSizePx = 100f,
                    minimumFlingVelocityPxPerSecond = 400f,
                    layoutDirection = LayoutDirection.Ltr,
                    sessionComplete = false,
                ),
            )
        }
    }

    @Test
    fun `each gesture session is consumed once and a new down replaces old evidence`() {
        val session = KiyoriHomePagerGestureSession()
        session.begin(
            originPage = 2,
            downX = 500f,
            pageSizePx = 100f,
            minimumFlingVelocityPxPerSecond = 400f,
            layoutDirection = LayoutDirection.Ltr,
        )
        session.update(430f)

        session.begin(
            originPage = 2,
            downX = 300f,
            pageSizePx = 100f,
            minimumFlingVelocityPxPerSecond = 400f,
            layoutDirection = LayoutDirection.Ltr,
        )
        session.finish(360f)

        val input = session.consume(pageCount = 3, physicalVelocityX = 0f)
        val decision = resolveKiyoriHomePagerSnapTarget(requireNotNull(input))
        assertEquals(1, decision.targetPage)
        assertEquals(KiyoriHomePagerSnapReason.POSITION, decision.reason)
        assertNull(session.consume(pageCount = 3, physicalVelocityX = 0f))
    }

    private fun assertDecision(
        originPage: Int = 1,
        physicalDragX: Float = 0f,
        physicalVelocityX: Float = 0f,
        pageSizePx: Float = 100f,
        minimumFlingVelocityPxPerSecond: Float = 400f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        cancelled: Boolean = false,
        sessionComplete: Boolean = true,
        expectedPage: Int,
        expectedReason: KiyoriHomePagerSnapReason,
    ) {
        val decision =
            resolveKiyoriHomePagerSnapTarget(
                KiyoriHomePagerSnapInput(
                    originPage = originPage,
                    pageCount = 3,
                    physicalDragX = physicalDragX,
                    physicalVelocityX = physicalVelocityX,
                    pageSizePx = pageSizePx,
                    minimumFlingVelocityPxPerSecond =
                        minimumFlingVelocityPxPerSecond,
                    layoutDirection = layoutDirection,
                    cancelled = cancelled,
                    sessionComplete = sessionComplete,
                ),
            )
        assertEquals(expectedPage, decision.targetPage)
        assertEquals(expectedReason, decision.reason)
    }
}
