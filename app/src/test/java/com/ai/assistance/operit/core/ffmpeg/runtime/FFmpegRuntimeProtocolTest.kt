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

    @Test
    fun requestPayloadLimitsRejectOversizedBinderWorkBeforeDispatch() {
        val requestId = "2".repeat(32)
        assertThrows(IllegalArgumentException::class.java) {
            FFmpegRuntimeRequest(
                requestId = requestId,
                operationWireValue = FFmpegRuntimeOperation.EXECUTE_COMMAND.wireValue,
                command = "x".repeat(FFMPEG_RUNTIME_MAX_COMMAND_CHARS + 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FFmpegRuntimeRequest(
                requestId = requestId,
                operationWireValue = FFmpegRuntimeOperation.EXECUTE_ARGUMENTS.wireValue,
                arguments = List(FFMPEG_RUNTIME_MAX_ARGUMENT_COUNT + 1) { "-version" },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FFmpegRuntimeRequest(
                requestId = requestId,
                operationWireValue = FFmpegRuntimeOperation.EXECUTE_ARGUMENTS.wireValue,
                arguments = listOf("x".repeat(FFMPEG_RUNTIME_MAX_ARGUMENT_CHARS + 1)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FFmpegRuntimeRequest(
                requestId = requestId,
                operationWireValue = FFmpegRuntimeOperation.PROBE_MEDIA.wireValue,
                inputPath = "/" + "x".repeat(FFMPEG_RUNTIME_MAX_PATH_CHARS),
            )
        }
    }

    @Test
    fun queuedCancellationMayCompleteWithoutCreatingANativeSession() {
        val result =
            FFmpegRuntimeResult(
                requestId = "3".repeat(32),
                operationWireValue = FFmpegRuntimeOperation.EXECUTE_ARGUMENTS.wireValue,
                processId = 123,
                sessionId = 0L,
                terminalStateWireValue = FFmpegRuntimeTerminalState.CANCELLED.wireValue,
                returnCode = FFMPEG_RUNTIME_CANCEL_RETURN_CODE,
                durationMillis = 0L,
                outputLogPath = File("queued-cancel.log").absolutePath,
                failStackTrace = null,
                statistics = null,
                mediaInformation = null,
                runtimeInformation = null,
            )
        assertEquals(FFmpegRuntimeTerminalState.CANCELLED, result.terminalState)
        assertEquals(0L, result.sessionId)

        assertThrows(IllegalArgumentException::class.java) {
            result.copy(
                terminalStateWireValue = FFmpegRuntimeTerminalState.SUCCEEDED.wireValue,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(durationMillis = 1L)
        }
    }

    @Test
    fun runtimeLogWriterBoundsOneVerboseRequestByUtf8Bytes() {
        val root = Files.createTempDirectory("ffmpeg-runtime-log-bound").toFile()
        try {
            val file = root.resolve("4".repeat(32) + ".log")
            val writer = FFmpegRuntimeLogWriter(file)
            writer.append("界".repeat(FFMPEG_RUNTIME_MAX_LOG_BYTES / 3 + 100))
            writer.append("late")
            writer.close()

            val text = file.readText(Charsets.UTF_8)
            assertTrue(text.endsWith(FFMPEG_RUNTIME_LOG_TRUNCATION_MARKER))
            assertFalse(text.endsWith("late"))
            assertTrue(file.length() <= FFMPEG_RUNTIME_MAX_LOG_BYTES.toLong())
            assertTrue(file.length() >= FFMPEG_RUNTIME_MAX_LOG_BYTES.toLong() - 3L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun runtimeLogRetentionProtectsActiveFilesAndPrunesDeterministically() {
        val root = Files.createTempDirectory("ffmpeg-runtime-retention").toFile()
        try {
            val directory = ffmpegRuntimeLogDirectory(root)
            assertTrue(directory.mkdirs())
            val now = 1_000_000L
            val protected =
                ffmpegRuntimeLogFile(root, "5".repeat(32)).apply {
                    writeText("protected")
                    setLastModified(1L)
                }
            val expired =
                ffmpegRuntimeLogFile(root, "6".repeat(32)).apply {
                    writeText("expired")
                    setLastModified(1L)
                }
            val newest =
                ffmpegRuntimeLogFile(root, "7".repeat(32)).apply {
                    writeText("newest")
                    setLastModified(now)
                }
            root.resolve("unrelated.txt").writeText("keep")

            val retention =
                pruneFfmpegRuntimeLogs(
                    cacheDir = root,
                    protectedLogPaths = setOf(protected.absolutePath),
                    nowMillis = now,
                    maxAgeMillis = 100L,
                    maxFiles = 1,
                    maxTotalBytes = Long.MAX_VALUE,
                )

            assertTrue(protected.isFile)
            assertFalse(expired.exists())
            assertFalse(newest.exists())
            assertEquals(2, retention.deletedFiles)
            assertEquals(1, retention.retainedFiles)
            assertTrue(root.resolve("unrelated.txt").isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
