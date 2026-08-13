package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnrMonitorObservationTest {
    @Test
    fun startupAndReadyPhases_areSeparatedFromUserOperation() {
        val state = AnrMonitorObservationState()

        assertEquals(
            AnrMonitorObservationState.PHASE_STARTUP_BEFORE_FIRST_FRAME,
            state.snapshot().phase,
        )
        state.markFirstFrameRendered()
        assertEquals(
            AnrMonitorObservationState.PHASE_STARTUP_AFTER_FIRST_FRAME,
            state.snapshot().phase,
        )
        state.markApplicationReady()
        assertEquals(
            AnrMonitorObservationState.PHASE_USER_OPERATION,
            state.snapshot().phase,
        )
    }

    @Test
    fun delayedSamples_trackConsecutiveWarningsAndResetOnHealthySample() {
        val state = AnrMonitorObservationState()
        state.markProcessLifecycleState(
            AnrMonitorObservationState.PROCESS_STATE_FOREGROUND
        )

        val first = state.recordDelayedSample(510L, isPotentialAnr = false)
        val second = state.recordDelayedSample(1_100L, isPotentialAnr = true)

        assertEquals(1, first.consecutiveDelayedSamples)
        assertEquals(2, second.consecutiveDelayedSamples)
        assertEquals(2, second.totalWarningCount)
        assertEquals(1, second.totalAnrCount)
        assertEquals(1_100L, second.maxBlockDurationMs)
        assertEquals(
            AnrMonitorObservationState.PROCESS_STATE_FOREGROUND,
            second.processLifecycleState,
        )

        state.markHealthy()
        val afterHealthy = state.snapshot()
        assertEquals(0, afterHealthy.consecutiveDelayedSamples)
        assertEquals(2, afterHealthy.totalWarningCount)
        assertEquals(1, afterHealthy.totalAnrCount)
        assertFalse(afterHealthy.firstFrameRendered)
        assertTrue(afterHealthy.maxBlockDurationMs == 1_100L)
    }

    @Test
    fun destroyedLifecycleState_isRecordedWithoutChangingCounters() {
        val state = AnrMonitorObservationState()
        state.recordDelayedSample(700L, isPotentialAnr = false)

        state.markDestroyed()

        val snapshot = state.snapshot()
        assertEquals(
            AnrMonitorObservationState.PROCESS_STATE_DESTROYED,
            snapshot.processLifecycleState,
        )
        assertEquals(1, snapshot.totalWarningCount)
        assertEquals(700L, snapshot.maxBlockDurationMs)
    }
}
