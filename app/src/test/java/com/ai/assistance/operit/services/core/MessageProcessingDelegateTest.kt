package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.api.chat.AssistantTurnFailureKind
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ProviderUsageAggregate
import com.ai.assistance.operit.data.model.toProviderUsageAggregate
import com.ai.assistance.operit.data.model.withProviderUsageAggregate
import com.ai.assistance.operit.util.stream.emptyStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MessageProcessingDelegateTest {
    @Test
    fun completeInterruptedMessage_appliesTurnSnapshotAndCompletesPartialContent() {
        val message =
            ChatMessage(
                sender = "ai",
                content = "stale",
                timestamp = 10L,
                sentAt = 20L,
                contentStream = emptyStream(),
            )
        val snapshot =
            MessageProcessingDelegate.TurnCancellationSnapshot(
                inputTokens = 120,
                outputTokens = 34,
                cachedInputTokens = 56,
                sentAt = 30L,
                outputDurationMs = 4_000L,
                waitDurationMs = 500L,
            )

        val result =
            MessageProcessingDelegate.completeInterruptedMessage(
                streamingMessage = message,
                finalContent = "partial response",
                snapshot = snapshot,
                completedAt = 5_000L,
            )

        assertEquals("partial response", result.content)
        assertNull(result.contentStream)
        assertEquals(120, result.inputTokens)
        assertEquals(34, result.outputTokens)
        assertEquals(56, result.cachedInputTokens)
        assertEquals(30L, result.sentAt)
        assertEquals(4_000L, result.outputDurationMs)
        assertEquals(500L, result.waitDurationMs)
        assertEquals(5_000L, result.completedAt)
    }

    @Test
    fun completeInterruptedMessage_removesOpenToolTransactionFromReplaySurface() {
        val openToolCall =
            "<tool_exec name=\"run\" provider_call_id=\"call-1\">" +
                "<param name=\"command\">pwd</param></tool_exec>"
        val message =
            ChatMessage(
                sender = "ai",
                content = "stale",
                timestamp = 10L,
                contentStream = emptyStream(),
            )

        val result =
            MessageProcessingDelegate.completeInterruptedMessage(
                streamingMessage = message,
                finalContent = "visible prefix\n$openToolCall",
                snapshot = null,
                completedAt = 5_000L,
            )

        assertEquals("visible prefix\n", result.content)
        assertNull(result.contentStream)
        assertEquals(5_000L, result.completedAt)
    }

    @Test
    fun failedAssistantProjection_preservesReplaySafePrefixAndNeverKeepsOpenToolCall() {
        val message =
            ChatMessage(
                sender = "ai",
                contentStream = emptyStream(),
                timestamp = 10L,
            )
        val result =
            MessageProcessingDelegate.projectFailedAssistantMessage(
                streamingMessage = message,
                finalContent =
                    "received\n" +
                        "<tool_exec name=\"run\" provider_call_id=\"call-1\"><param name=\"command\">pwd</param></tool_exec>",
                snapshot = null,
                completedAt = 5_000L,
            )

        assertNotNull(result)
        assertEquals("received\n", result!!.content)
        assertNull(result.contentStream)
        assertEquals(5_000L, result.completedAt)
    }

    @Test
    fun failedAssistantProjection_doesNotCreateEmptySuccessMessage() {
        val message = ChatMessage(sender = "ai", contentStream = emptyStream(), timestamp = 10L)

        val result =
            MessageProcessingDelegate.projectFailedAssistantMessage(
                streamingMessage = message,
                finalContent = "<tool_exec name=\"run\" provider_call_id=\"call-1\"><param name=\"command\">pwd</param></tool_exec>",
                snapshot = null,
                completedAt = 5_000L,
            )

        assertNull(result)
    }

    @Test
    fun failedAssistantProjection_closesInterruptedThinkingMarkupWithoutClaimingProviderCompletion() {
        val message = ChatMessage(sender = "ai", contentStream = emptyStream(), timestamp = 10L)

        val result =
            MessageProcessingDelegate.projectFailedAssistantMessage(
                streamingMessage = message,
                finalContent = "<think>already received reasoning",
                snapshot = null,
                completedAt = 5_000L,
            )

        assertNotNull(result)
        assertEquals("<think>already received reasoning</think>", result!!.content)
        assertEquals(5_000L, result.completedAt)
    }

    @Test
    fun providerFailureIsRetainedAsFinalErrorAfterRuntimeCleanup() {
        val terminal =
            MessageProcessingDelegate.createTurnFailureTerminal(
                message = "Failed to send message: upstream failed",
                failureKind = AssistantTurnFailureKind.PROVIDER_FAILURE,
            )

        assertEquals("provider_failure", terminal.terminalOutcome)
        assertEquals(
            InputProcessingState.Error("Failed to send message: upstream failed"),
            terminal.finalInputState,
        )
        assertFalse(terminal.shouldNotifyTurnComplete)
    }

    @Test
    fun emptyOutputAndMissingTerminalHaveDistinctStableOutcomes() {
        val emptyOutput =
            MessageProcessingDelegate.createTurnFailureTerminal(
                message = "AI_STREAM_EMPTY_TERMINATION",
                failureKind = AssistantTurnFailureKind.EMPTY_OUTPUT,
            )
        val missingTerminal =
            MessageProcessingDelegate.createTurnFailureTerminal(
                message = "AI_TURN_TERMINAL_MISSING",
                failureKind = AssistantTurnFailureKind.TERMINAL_MISSING,
            )

        assertEquals("empty_output", emptyOutput.terminalOutcome)
        assertEquals("terminal_missing", missingTerminal.terminalOutcome)
        assertEquals(
            InputProcessingState.Error("AI_STREAM_EMPTY_TERMINATION"),
            emptyOutput.finalInputState,
        )
        assertEquals(
            InputProcessingState.Error("AI_TURN_TERMINAL_MISSING"),
            missingTerminal.finalInputState,
        )
        assertFalse(emptyOutput.shouldNotifyTurnComplete)
        assertFalse(missingTerminal.shouldNotifyTurnComplete)
    }

    @Test
    fun waifuSegmentsKeepOneProviderUsageOwnerForTheWholeTurn() {
        val usage =
            ProviderUsageAggregate(
                requestCount = 2,
                providerUsageRequestCount = 2,
                providerCacheMetricRequestCount = 2,
                providerCacheMetricPromptTokens = 100L,
                providerTotalInputTokens = 100L,
                providerUncachedInputTokens = 20L,
                providerCacheReadTokens = 70L,
                providerCacheWriteTokens = 10L,
                providerOutputTokens = 30L,
                providerReasoningTokens = 8L,
            )
        val sourceMessage =
            ChatMessage(
                sender = "ai",
                inputTokens = 120,
                outputTokens = 30,
                cachedInputTokens = 70,
                sentAt = 1_000L,
                outputDurationMs = 4_000L,
                waitDurationMs = 500L,
                completedAt = 6_000L,
            ).withProviderUsageAggregate(usage)
        val segments =
            listOf("第一段", "第二段", "第三段").mapIndexed { index, content ->
                ChatMessage(
                    sender = "ai",
                    content = content,
                    timestamp = 100L + index,
                ).withProviderUsageAggregate(usage)
            }

        val result =
            MessageProcessingDelegate.applyWaifuTurnMetrics(
                messages = segments,
                sourceMessage = sourceMessage,
            )

        result.forEach { message ->
            assertEquals(sourceMessage.inputTokens, message.inputTokens)
            assertEquals(sourceMessage.outputTokens, message.outputTokens)
            assertEquals(sourceMessage.cachedInputTokens, message.cachedInputTokens)
            assertEquals(sourceMessage.sentAt, message.sentAt)
            assertEquals(sourceMessage.outputDurationMs, message.outputDurationMs)
            assertEquals(sourceMessage.waitDurationMs, message.waitDurationMs)
            assertEquals(sourceMessage.completedAt, message.completedAt)
        }
        assertEquals(ProviderUsageAggregate(), result[0].toProviderUsageAggregate())
        assertEquals(ProviderUsageAggregate(), result[1].toProviderUsageAggregate())
        assertEquals(usage, result[2].toProviderUsageAggregate())
        assertEquals(
            usage,
            result.fold(ProviderUsageAggregate()) { aggregate, message ->
                aggregate + message.toProviderUsageAggregate()
            },
        )
    }
}
