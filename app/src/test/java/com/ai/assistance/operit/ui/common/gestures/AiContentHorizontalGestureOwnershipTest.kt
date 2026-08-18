package com.ai.assistance.operit.ui.common.gestures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContentHorizontalGestureOwnershipTest {
    @Test
    fun `one owner cannot release another active horizontal interaction`() {
        val ownership = AiContentHorizontalGestureOwnership()
        val tableOwner = Any()
        val codeOwner = Any()

        ownership.claim(tableOwner)
        ownership.claim(codeOwner)
        assertTrue(ownership.isOwned)

        ownership.release(tableOwner)
        assertTrue(ownership.isOwned)

        ownership.release(codeOwner)
        assertFalse(ownership.isOwned)
    }

    @Test
    fun `claiming the same owner is idempotent`() {
        val ownership = AiContentHorizontalGestureOwnership()
        val owner = Any()

        ownership.claim(owner)
        ownership.claim(owner)
        ownership.release(owner)

        assertFalse(ownership.isOwned)
    }
}
