package com.ai.assistance.operit.core.player.runtime

import com.ai.assistance.operit.core.player.PlayerDebugLogLevel
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

    @Test
    fun diagnosticLogLevelsHaveStableWireValues() {
        PlayerDebugLogLevel.entries.forEach { level ->
            assertEquals(level, PlayerDebugLogLevel.fromWireValue(level.wireValue))
        }
        assertEquals(null, PlayerDebugLogLevel.fromWireValue(Int.MIN_VALUE))
    }

    @Test
    fun mpvHeadersKeepRequestIdentityButLeaveRangeToTheTransport() {
        val plan =
            buildPlayerMpvHttpHeaderPlan(
                linkedMapOf(
                    "Origin" to "https://page.example",
                    "User-Agent" to "Kiyori/1",
                    "Referer" to "https://page.example/watch",
                    "Cookie" to "session=exact",
                    "rAnGe" to "bytes=19890176-",
                    "Accept" to "video/mp4",
                ),
            )

        assertTrue(plan.rangeHeaderObserved)
        assertEquals(
            linkedMapOf(
                "Origin" to "https://page.example",
                "User-Agent" to "Kiyori/1",
                "Referer" to "https://page.example/watch",
                "Cookie" to "session=exact",
                "Accept" to "video/mp4",
            ),
            plan.forwardedHeaders,
        )
        assertFalse(plan.forwardedHeaders.keys.any { it.equals("Range", ignoreCase = true) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun mpvHeadersRejectInjectedLineBreaksBeforeLoggingOrSerialization() {
        buildPlayerMpvHttpHeaderPlan(
            mapOf("Referer" to "https://page.example/watch\r\nRange: bytes=1-"),
        )
    }

    @Test
    fun endFileSchemaUsesStringReasonAndFileError() {
        val failed =
            resolvePlayerMpvEndFileState(
                reason = "error",
                fileError = "loading failed",
            )
        val completed =
            resolvePlayerMpvEndFileState(
                reason = "eof",
                fileError = null,
            )

        assertTrue(failed.failed)
        assertEquals("loading failed", failed.fileError)
        assertFalse(completed.failed)
        assertEquals("eof", completed.reason)
    }
}
