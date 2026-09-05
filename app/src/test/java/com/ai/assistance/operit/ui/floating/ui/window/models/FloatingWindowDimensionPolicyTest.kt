package com.ai.assistance.operit.ui.floating.ui.window.models

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowDimensionPolicyTest {
    @Test
    fun `compose dimensions are bounded before fixed constraints`() {
        assertEquals(0, boundFloatingWindowDimensionPx(-1))
        assertEquals(1036, boundFloatingWindowDimensionPx(1036))
        assertEquals(MAX_COMPOSE_DIMENSION_PX, boundFloatingWindowDimensionPx(262527))
    }
}
