package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStreamSessionGateTest {
    @Test
    fun staleCompletionCannotClearNewSessionHandles() {
        val gate = ProviderStreamSessionGate()
        val oldSession = gate.begin()
        var oldCancelled = false
        gate.bindCall(oldSession, owner = "old") { oldCancelled = true }

        val newSession = gate.begin()
        var newCancelled = false
        gate.bindCall(newSession, owner = "new") { newCancelled = true }
        gate.complete(oldSession)

        assertTrue(oldCancelled)
        assertFalse(newCancelled)
        assertTrue(gate.cancelActive())
        assertTrue(newCancelled)
    }

    @Test
    fun cancellationStateBelongsOnlyToItsGeneration() {
        val gate = ProviderStreamSessionGate()
        val oldSession = gate.begin()
        gate.cancelActive()

        val newSession = gate.begin()

        assertTrue(gate.isCancelled(oldSession))
        assertFalse(gate.isCancelled(newSession))
    }

    @Test
    fun lateBindingFromCancelledSessionIsRejectedAndCancelled() {
        val gate = ProviderStreamSessionGate()
        val oldSession = gate.begin()
        gate.cancelActive()
        var lateHandleCancelled = false

        gate.bindResponse(oldSession, owner = "late") { lateHandleCancelled = true }

        assertTrue(lateHandleCancelled)
    }

    @Test
    fun replacingCallWithinSessionCancelsPreviousHandle() {
        val gate = ProviderStreamSessionGate()
        val session = gate.begin()
        var firstCancellationCount = 0
        var secondCancellationCount = 0

        gate.bindCall(session, owner = "first") { firstCancellationCount += 1 }
        gate.bindCall(session, owner = "second") { secondCancellationCount += 1 }

        assertEquals(1, firstCancellationCount)
        assertEquals(0, secondCancellationCount)
        assertTrue(gate.cancelActive())
        assertEquals(1, firstCancellationCount)
        assertEquals(1, secondCancellationCount)
    }

    @Test
    fun cancelActiveClosesResponseAndCancelsCallExactlyOnce() {
        val gate = ProviderStreamSessionGate()
        val session = gate.begin()
        var callCancellationCount = 0
        var responseCancellationCount = 0
        gate.bindCall(session, owner = "call") { callCancellationCount += 1 }
        gate.bindResponse(session, owner = "response") { responseCancellationCount += 1 }

        assertTrue(gate.cancelActive())
        assertFalse(gate.cancelActive())

        assertEquals(1, callCancellationCount)
        assertEquals(1, responseCancellationCount)
    }

    @Test
    fun staleResponseClearCannotClearNewResponse() {
        val gate = ProviderStreamSessionGate()
        val oldSession = gate.begin()
        var oldCancellationCount = 0
        gate.bindResponse(oldSession, owner = "old") { oldCancellationCount += 1 }

        val newSession = gate.begin()
        var newCancellationCount = 0
        gate.bindResponse(newSession, owner = "new") { newCancellationCount += 1 }
        gate.clearResponse(oldSession, owner = "old")

        assertEquals(1, oldCancellationCount)
        assertEquals(0, newCancellationCount)
        assertTrue(gate.cancelActive())
        assertEquals(1, newCancellationCount)
    }

    @Test
    fun completedSessionRejectsLateBinding() {
        val gate = ProviderStreamSessionGate()
        val session = gate.begin()
        gate.complete(session)
        var lateCancellationCount = 0

        gate.bindCall(session, owner = "late") { lateCancellationCount += 1 }

        assertEquals(1, lateCancellationCount)
        assertFalse(gate.cancelActive())
    }
}
