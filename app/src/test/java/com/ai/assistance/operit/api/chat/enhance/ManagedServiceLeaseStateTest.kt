package com.ai.assistance.operit.api.chat.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedServiceLeaseStateTest {
    @Test
    fun retireWithActiveLease_waitsForLeaseReturn() {
        val state = ManagedServiceLeaseState()
        state.acquire()

        assertFalse(state.retire())
        assertTrue(state.retired)
        assertEquals(1, state.activeLeases)
        assertFalse(state.released)

        assertTrue(state.releaseLease())
        assertTrue(state.markReleased())
        assertTrue(state.released)
    }

    @Test
    fun retireWithoutLease_releasesImmediately() {
        val state = ManagedServiceLeaseState()

        assertTrue(state.retire())
        assertTrue(state.markReleased())
        assertTrue(state.released)
    }

    @Test
    fun releaseOwnership_isGrantedOnlyOnce() {
        val state = ManagedServiceLeaseState()
        assertTrue(state.retire())

        assertTrue(state.markReleased())
        assertFalse(state.markReleased())
    }

    @Test(expected = IllegalStateException::class)
    fun retiredService_cannotBeAcquiredByNewRequest() {
        val state = ManagedServiceLeaseState()
        state.retire()

        state.acquire()
    }
}
