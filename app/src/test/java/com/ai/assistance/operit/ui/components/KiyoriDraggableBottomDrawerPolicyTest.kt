package com.ai.assistance.operit.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KiyoriDraggableBottomDrawerPolicyTest {
    @Test
    fun `content viewport follows the exposed drawer height`() {
        assertEquals(
            972f,
            resolveKiyoriBottomDrawerContentViewportHeight(
                drawerHeightDp = 1000f,
                offsetFraction = 0f,
            ),
            0f,
        )
        assertEquals(
            612f,
            resolveKiyoriBottomDrawerContentViewportHeight(
                drawerHeightDp = 1000f,
                offsetFraction = 0.36f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveKiyoriBottomDrawerContentViewportHeight(
                drawerHeightDp = 1000f,
                offsetFraction = 1f,
            ),
            0f,
        )
    }

    @Test
    fun `modal drawers share the browser partial-height policy`() {
        assertEquals(0.64f, resolveKiyoriBottomDrawerPartialFraction(390f, 844f), 0f)
        assertEquals(0.72f, resolveKiyoriBottomDrawerPartialFraction(844f, 390f), 0f)
        assertEquals(0.68f, resolveKiyoriBottomDrawerPartialFraction(700f, 1000f), 0f)
        assertEquals(0.72f, resolveKiyoriBottomDrawerPartialFraction(1000f, 700f), 0f)
    }
}
