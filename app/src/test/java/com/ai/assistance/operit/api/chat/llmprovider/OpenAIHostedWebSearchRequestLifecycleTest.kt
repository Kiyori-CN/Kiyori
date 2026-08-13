package com.ai.assistance.operit.api.chat.llmprovider

import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAIHostedWebSearchRequestLifecycleTest {
    @Test
    fun `execution diagnostics use monotonic phase durations without inventing callback time`() {
        val nowNs = AtomicLong(0L)
        val lifecycle =
            OpenAIHostedWebSearchRequestLifecycle(
                requestId = "ows_lifecycle",
                nanoTime = nowNs::get,
            )
        lifecycle.configureLocation(
            requested = true,
            configured = true,
            applied = true,
            precision = "mixed",
        )
        lifecycle.recordQueueWait(7)

        nowNs.set(10_000_000L)
        lifecycle.markResponseBodyReadStarted()
        nowNs.set(25_000_000L)
        lifecycle.markResponseBodyReadCompleted()
        nowNs.set(30_000_000L)
        lifecycle.markParseStarted()
        nowNs.set(42_000_000L)
        lifecycle.markParseCompleted()

        val diagnostics = lifecycle.executionDiagnostics()

        assertEquals(42L, diagnostics.totalElapsedMs)
        assertEquals(7L, diagnostics.queueWaitMs)
        assertEquals(15L, diagnostics.responseBodyReadMs)
        assertEquals(12L, diagnostics.parseMs)
        assertNull(diagnostics.callbackDeliveryMs)
        assertEquals("mixed", diagnostics.location.precision)
        assertEquals(true, diagnostics.location.applied)
    }
}
