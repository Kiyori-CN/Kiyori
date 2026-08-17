package com.ai.assistance.operit.core.ffmpeg.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegRuntimeProtocolTest {
    @Test
    fun terminalStateUsesExactFfmpegReturnCodes() {
        assertEquals(
            FFmpegRuntimeTerminalState.SUCCEEDED,
            resolveFfmpegRuntimeTerminalState(FFMPEG_RUNTIME_SUCCESS_RETURN_CODE),
        )
        assertEquals(
            FFmpegRuntimeTerminalState.CANCELLED,
            resolveFfmpegRuntimeTerminalState(FFMPEG_RUNTIME_CANCEL_RETURN_CODE),
        )
        assertEquals(
            FFmpegRuntimeTerminalState.FAILED,
            resolveFfmpegRuntimeTerminalState(1),
        )
        assertEquals(
            FFmpegRuntimeTerminalState.FAILED,
            resolveFfmpegRuntimeTerminalState(Int.MIN_VALUE),
        )
    }

    @Test
    fun diagnosticBoundingIsExplicitAndDeterministic() {
        assertNull(boundFfmpegRuntimeDiagnostic(null))
        assertNull(boundFfmpegRuntimeDiagnostic(""))
        assertEquals("failure", boundFfmpegRuntimeDiagnostic("failure"))

        val bounded = requireNotNull(boundFfmpegRuntimeDiagnostic("x".repeat(20_000)))
        assertTrue(bounded.startsWith("x".repeat(FFMPEG_RUNTIME_MAX_DIAGNOSTIC_CHARS)))
        assertTrue(bounded.contains("diagnostic truncated"))
    }

    @Test
    fun logReaderRejectsPathsOutsidePrivateRuntimeDirectory() {
        val root = Files.createTempDirectory("ffmpeg-runtime-test").toFile()
        try {
            val requestId = "1".repeat(32)
            val log = ffmpegRuntimeLogFile(root, requestId)
            val logDirectory = requireNotNull(log.parentFile)
            assertTrue(logDirectory.mkdirs() || logDirectory.isDirectory)
            log.writeText("runtime output", Charsets.UTF_8)

            assertEquals("runtime output", readFfmpegRuntimeOutput(root, log.absolutePath))

            val outside = File(root, "outside.log").apply { writeText("outside") }
            assertThrows(IllegalArgumentException::class.java) {
                readFfmpegRuntimeOutput(root, outside.absolutePath)
            }
            assertTrue(outside.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun eventCursorRejectsStaleGenerationAndOutOfOrderCallbacks() {
        val cursor = FFmpegRuntimeEventCursor(runtimeGeneration = 7L)

        assertFalse(cursor.accept(eventGeneration = 6L, eventSequence = 1L))
        assertFalse(cursor.accept(eventGeneration = 7L, eventSequence = 0L))
        assertTrue(cursor.accept(eventGeneration = 7L, eventSequence = 1L))
        assertFalse(cursor.accept(eventGeneration = 7L, eventSequence = 1L))
        assertTrue(cursor.accept(eventGeneration = 7L, eventSequence = 2L))
        assertFalse(cursor.accept(eventGeneration = 8L, eventSequence = 3L))
    }

    @Test
    fun operationStateAndFailureWireValuesRemainUnique() {
        assertEquals(
            FFmpegRuntimeOperation.entries.size,
            FFmpegRuntimeOperation.entries.map { operation -> operation.wireValue }.toSet().size,
        )
        assertEquals(
            FFmpegRuntimeTerminalState.entries.size,
            FFmpegRuntimeTerminalState.entries.map { state -> state.wireValue }.toSet().size,
        )
        assertEquals(
            FFmpegRuntimeFailureCode.entries.size,
            FFmpegRuntimeFailureCode.entries.map { failure -> failure.wireValue }.toSet().size,
        )
        assertEquals(4, FFmpegRuntimeFailureCode.CALLBACK_REPLACED.wireValue)
        assertEquals(5, FFmpegRuntimeFailureCode.CALLBACK_DISCONNECTED.wireValue)
        assertEquals(6, FFmpegRuntimeFailureCode.SERVICE_DESTROYED.wireValue)
    }
}
