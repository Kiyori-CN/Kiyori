package com.ai.assistance.operit.core.player.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRuntimeProtocolPolicyTest {
    @Test
    fun eventGate_acceptsOnlyCurrentGenerationAndIncreasingSequence() {
        val initial = PlayerRuntimeEventCursor(runtimeGeneration = 7L)
        val first = acceptPlayerRuntimeEvent(initial, runtimeGeneration = 7L, eventSequence = 1L)
        val duplicate =
            acceptPlayerRuntimeEvent(first.cursor, runtimeGeneration = 7L, eventSequence = 1L)
        val stale =
            acceptPlayerRuntimeEvent(first.cursor, runtimeGeneration = 6L, eventSequence = 2L)
        val second =
            acceptPlayerRuntimeEvent(first.cursor, runtimeGeneration = 7L, eventSequence = 2L)

        assertTrue(first.accepted)
        assertFalse(duplicate.accepted)
        assertFalse(stale.accepted)
        assertTrue(second.accepted)
        assertEquals(2L, second.cursor.lastEventSequence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun eventCursor_rejectsNonPositiveGeneration() {
        PlayerRuntimeEventCursor(runtimeGeneration = 0L)
    }
}
