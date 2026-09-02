package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.terminal.CommandExecutionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalOutputCaptureTest {
    @Test
    fun preservesCompleteMultilineOutputWithinLimit() {
        val capture = TerminalOutputCapture(maxChars = 64)

        capture.append("step 1\n")
        capture.append("step 2\n")
        capture.append("step 3\n")

        val snapshot = capture.snapshot()
        assertEquals("step 1\nstep 2\nstep 3\n", snapshot.output)
        assertEquals(21L, snapshot.originalChars)
        assertFalse(snapshot.truncated)
    }

    @Test
    fun largeOutputKeepsHeadAndTailAndReportsOmission() {
        val capture = TerminalOutputCapture(maxChars = 20)
        val output = "HEAD-00000MIDDLE-11111TAIL-22222"

        output.chunked(3).forEach(capture::append)

        val snapshot = capture.snapshot()
        assertTrue(snapshot.truncated)
        assertEquals(output.length.toLong(), snapshot.originalChars)
        assertTrue(snapshot.output.startsWith(output.take(10)))
        assertTrue(snapshot.output.endsWith(output.takeLast(10)))
        assertTrue(snapshot.output.contains("characters omitted between head and tail"))
        assertTrue(snapshot.output.contains("read_file_part or grep_code"))
    }

    @Test
    fun exactLimitRemainsCompleteAndSingleOversizedChunkKeepsBothEdges() {
        val exact = TerminalOutputCapture(maxChars = 8)
        exact.append("1234")
        exact.append("5678")

        assertEquals("12345678", exact.snapshot().output)
        assertFalse(exact.snapshot().truncated)
        assertEquals(8L, exact.snapshot().originalChars)

        val oversized = TerminalOutputCapture(maxChars = 8)
        val input = "HEAD-middle-TAIL"
        oversized.append(input)

        val snapshot = oversized.snapshot()
        assertTrue(snapshot.truncated)
        assertEquals(input.length.toLong(), snapshot.originalChars)
        assertTrue(snapshot.output.startsWith(input.take(4)))
        assertTrue(snapshot.output.endsWith(input.takeLast(4)))
    }

    @Test
    fun completionSnapshotIsNotDuplicatedIntoIncrementalOutput() {
        val capture = TerminalCommandEventCapture(maxChars = 64)

        capture.accept(event(output = "line 1\n"))
        capture.accept(event(output = "line 2\n"))
        capture.accept(event(output = "line 1\nline 2", completed = true, exitCode = 0))

        val snapshot = capture.snapshot()
        assertEquals("line 1\nline 2\n", snapshot.output)
        assertEquals(14L, snapshot.originalChars)
        assertTrue(snapshot.hasCompleted)
        assertEquals(0, snapshot.exitCode)
    }

    @Test
    fun preservesAllFiveThousandIncrementalLinesInsteadOfCompletionTail() {
        val capture = TerminalCommandEventCapture(maxChars = 128 * 1024)
        val expected = buildString {
            for (lineNumber in 1..5_000) {
                val line = "LINE_$lineNumber\n"
                append(line)
                capture.accept(event(output = line))
            }
        }
        val completionTail = (4_001..5_000).joinToString("\n") { "LINE_$it" }
        capture.accept(event(output = completionTail, completed = true, exitCode = 0))

        val snapshot = capture.snapshot()
        assertEquals(expected, snapshot.output)
        assertFalse(snapshot.truncated)
        assertTrue(snapshot.output.startsWith("LINE_1\n"))
        assertTrue(snapshot.output.endsWith("LINE_5000\n"))
    }

    @Test
    fun timeoutSnapshotKeepsAlreadyCapturedNewlinesWithoutCompletionEvent() {
        val capture = TerminalCommandEventCapture(maxChars = 64)
        capture.accept(event(output = "probe start\n"))
        capture.accept(event(output = "step 1\n"))
        capture.accept(event(output = "step 2\n"))

        val snapshot = capture.snapshot()
        assertEquals("probe start\nstep 1\nstep 2\n", snapshot.output)
        assertFalse(snapshot.hasCompleted)
    }

    @Test
    fun completionAtDeadlineIsNotReportedAsTimeoutWhenCancellationMissesIt() {
        assertFalse(
            shouldReportCommandTimeout(
                deadlineExpired = true,
                completionObservedAtDeadline = false,
                cancellationCommandFound = false,
                completionObservedAfterDrain = true,
            )
        )
        assertTrue(
            shouldReportCommandTimeout(
                deadlineExpired = true,
                completionObservedAtDeadline = false,
                cancellationCommandFound = true,
                completionObservedAfterDrain = true,
            )
        )
        assertTrue(
            shouldReportCommandTimeout(
                deadlineExpired = true,
                completionObservedAtDeadline = false,
                cancellationCommandFound = null,
                completionObservedAfterDrain = false,
            )
        )
        assertFalse(
            shouldReportCommandTimeout(
                deadlineExpired = false,
                completionObservedAtDeadline = false,
                cancellationCommandFound = true,
                completionObservedAfterDrain = false,
            )
        )
        assertFalse(
            shouldReportCommandTimeout(
                deadlineExpired = true,
                completionObservedAtDeadline = true,
                cancellationCommandFound = null,
                completionObservedAfterDrain = true,
            )
        )
    }

    @Test
    fun explicitUnboundedPolicyDoesNotReuseForegroundDefaultDeadline() {
        assertEquals(
            null,
            resolveTerminalCommandTimeout(timeoutPolicy = "none", timeoutValue = null),
        )
        assertEquals(
            12_000L,
            resolveTerminalCommandTimeout(timeoutPolicy = "default", timeoutValue = "12000"),
        )
        assertEquals(
            1_800_000L,
            resolveTerminalCommandTimeout(timeoutPolicy = null, timeoutValue = null),
        )
    }

    private fun event(
        output: String,
        completed: Boolean = false,
        exitCode: Int? = null,
    ): CommandExecutionEvent = CommandExecutionEvent(
        commandId = "command-id",
        sessionId = "session-id",
        outputChunk = output,
        isCompleted = completed,
        exitCode = exitCode,
    )
}
