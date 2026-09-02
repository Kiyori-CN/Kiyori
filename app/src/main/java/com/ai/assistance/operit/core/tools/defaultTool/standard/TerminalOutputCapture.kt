package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.terminal.CommandExecutionEvent

internal data class TerminalOutputSnapshot(
    val output: String,
    val originalChars: Long,
    val truncated: Boolean,
)

/**
 * Bounded, newline-preserving capture for output returned to an AI tool caller.
 *
 * Terminal UI history and tool output have different responsibilities. The UI may discard old
 * pages for rendering, while the tool must retain the complete result for ordinary commands and
 * explicitly expose any safety truncation. Once the cap is crossed, this collector keeps both the
 * beginning (diagnostics and headers) and the end (summary and final errors) instead of silently
 * returning only the tail.
 */
internal class TerminalOutputCapture(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
) {
    init {
        require(maxChars >= 2) { "Terminal output limit must be at least 2 characters" }
    }

    private val headLimit = maxChars / 2
    private val tailLimit = maxChars - headLimit
    private val captured = StringBuilder(minOf(maxChars, 16 * 1024))
    private var head: String? = null
    private val tail = StringBuilder(tailLimit)
    private var totalChars = 0L

    val originalChars: Long
        get() = synchronized(this) { totalChars }

    @Synchronized
    fun append(chunk: String) {
        if (chunk.isEmpty()) return
        totalChars += chunk.length.toLong()

        if (head == null && captured.length + chunk.length <= maxChars) {
            captured.append(chunk)
            return
        }

        if (head == null) {
            head = buildString(headLimit) {
                append(captured, 0, minOf(captured.length, headLimit))
                if (length < headLimit) {
                    append(chunk, 0, minOf(chunk.length, headLimit - length))
                }
            }
            replaceTail(
                if (chunk.length >= tailLimit) {
                    chunk.substring(chunk.length - tailLimit)
                } else {
                    val retainedCapturedChars = minOf(captured.length, tailLimit - chunk.length)
                    captured.substring(captured.length - retainedCapturedChars) + chunk
                }
            )
            captured.clear()
            return
        }

        if (chunk.length >= tailLimit) {
            replaceTail(chunk.substring(chunk.length - tailLimit))
        } else {
            tail.append(chunk)
            if (tail.length > tailLimit) {
                tail.delete(0, tail.length - tailLimit)
            }
        }
    }

    @Synchronized
    fun snapshot(): TerminalOutputSnapshot {
        val first = head
        if (first == null) {
            return TerminalOutputSnapshot(
                output = captured.toString(),
                originalChars = totalChars,
                truncated = false,
            )
        }

        val omitted = (totalChars - first.length - tail.length).coerceAtLeast(0L)
        val marker =
            "\n\n[Terminal output truncated: $omitted characters omitted between head and tail. " +
                "Rerun the command with output redirected to a file, then use read_file_part or " +
                "grep_code.]\n\n"
        return TerminalOutputSnapshot(
            output = first + marker + tail,
            originalChars = totalChars,
            truncated = true,
        )
    }

    private fun replaceTail(value: String) {
        tail.clear()
        tail.append(value)
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 4 * 1024 * 1024
    }
}

internal data class CapturedTerminalChunk(
    val output: String,
    val receivedChars: Long,
)

internal data class TerminalCommandCaptureSnapshot(
    val output: String,
    val originalChars: Long,
    val truncated: Boolean,
    val hasCompleted: Boolean,
    val exitCode: Int,
)

/** Keeps completion signals out of the incremental command transcript. */
internal class TerminalCommandEventCapture(
    maxChars: Int = TerminalOutputCapture.DEFAULT_MAX_CHARS,
) {
    private val outputCapture = TerminalOutputCapture(maxChars)
    private var hasCompleted = false
    private var exitCode = -1

    @Synchronized
    fun accept(event: CommandExecutionEvent): CapturedTerminalChunk? {
        if (event.isCompleted) {
            hasCompleted = true
            exitCode = event.exitCode ?: -1
            return null
        }
        if (event.outputChunk.isEmpty()) return null

        outputCapture.append(event.outputChunk)
        return CapturedTerminalChunk(
            output = event.outputChunk,
            receivedChars = outputCapture.originalChars,
        )
    }

    @Synchronized
    fun snapshot(): TerminalCommandCaptureSnapshot {
        val output = outputCapture.snapshot()
        return TerminalCommandCaptureSnapshot(
            output = output.output,
            originalChars = output.originalChars,
            truncated = output.truncated,
            hasCompleted = hasCompleted,
            exitCode = exitCode,
        )
    }
}
