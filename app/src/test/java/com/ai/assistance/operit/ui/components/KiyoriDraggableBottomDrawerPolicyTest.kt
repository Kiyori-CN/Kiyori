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
}
