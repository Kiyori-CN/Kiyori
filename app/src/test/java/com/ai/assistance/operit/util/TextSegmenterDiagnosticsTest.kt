package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSegmenterDiagnosticsTest {
    @Test
    fun initializationAndSearch_observationsKeepOrderAndThreadFacts() {
        val diagnostics = TextSegmenterDiagnostics()
        val initializationStart = diagnostics.recordInitializeInvocation()

        diagnostics.recordInitializationLockAcquired(initializationStart)
        diagnostics.recordDictionaryLookup(2_000_000L)
        diagnostics.recordPrewarmCompleted(4_000_000L)
        diagnostics.recordInitializationCompleted(
            invocationStartNanos = initializationStart,
            threadName = "DefaultDispatcher-worker-1",
        )

        val searchStart = diagnostics.recordSearchStarted()
        diagnostics.recordSearchCompleted(
            searchStartNanos = searchStart,
            threadName = "DefaultDispatcher-worker-2",
        )

        val snapshot = diagnostics.snapshot()
        assertEquals(1, snapshot.initializeCallCount)
        assertEquals(1, snapshot.initializationCompletionCount)
        assertEquals(2L, snapshot.dictionaryLookupDurationMs)
        assertEquals(4L, snapshot.prewarmDurationMs)
        assertTrue(snapshot.prewarmCompleted)
        assertEquals(1, snapshot.searchCount)
        assertNotNull(snapshot.lastInitializationThreadName)
        assertEquals("DefaultDispatcher-worker-2", snapshot.firstSearchThreadName)
        assertEquals(false, snapshot.firstSearchBeforePrewarm)
        assertTrue(snapshot.firstSearchDurationMs != null)
        assertFalse(snapshot.summary().contains("搜索记忆"))
    }

    @Test
    fun firstSearchBeforePrewarm_isRecordedAsAnObservation() {
        val diagnostics = TextSegmenterDiagnostics()
        val searchStart = diagnostics.recordSearchStarted()

        diagnostics.recordSearchCompleted(
            searchStartNanos = searchStart,
            threadName = "main",
        )

        assertEquals(true, diagnostics.snapshot().firstSearchBeforePrewarm)
    }
}
