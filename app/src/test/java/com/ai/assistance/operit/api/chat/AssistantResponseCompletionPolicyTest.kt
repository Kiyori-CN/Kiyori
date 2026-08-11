package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantResponseCompletionPolicyTest {
    @Test
    fun nonEmptyRoundCanComplete() {
        AssistantResponseCompletionPolicy.requireContent(
            content = "visible response",
            emptyResponseMessage = "empty response",
        )
    }

    @Test
    fun emptyRoundFailsInsteadOfCompletingSilently() {
        val message =
            "模型请求在收到任何可见输出前结束。" +
                "\n诊断码：${AssistantTurnDiagnostics.STREAM_EMPTY_TERMINATION}"
        val failure =
            runCatching {
                AssistantResponseCompletionPolicy.requireContent(
                    content = "",
                    emptyResponseMessage = message,
                )
            }.exceptionOrNull()

        assertTrue(failure is AssistantResponseEmptyTerminationException)
        assertEquals(message, failure?.message)
        assertEquals(
            AssistantTurnFailureKind.EMPTY_OUTPUT,
            AssistantTurnFailurePolicy.classify(failure!!),
        )
    }

    @Test
    fun whitespaceOnlyRoundIsNotVisibleContent() {
        val failure =
            runCatching {
                AssistantResponseCompletionPolicy.requireContent(
                    content = " \n\t",
                    emptyResponseMessage = AssistantTurnDiagnostics.STREAM_EMPTY_TERMINATION,
                )
            }.exceptionOrNull()

        assertTrue(failure is AssistantResponseEmptyTerminationException)
    }

    @Test
    fun toolAndThinkingMarkupRemainValidCompletionContent() {
        AssistantResponseCompletionPolicy.requireContent(
            content = """<tool name="read_file"></tool>""",
            emptyResponseMessage = AssistantTurnDiagnostics.STREAM_EMPTY_TERMINATION,
        )
        AssistantResponseCompletionPolicy.requireContent(
            content = "<think>reasoning summary</think>",
            emptyResponseMessage = AssistantTurnDiagnostics.STREAM_EMPTY_TERMINATION,
        )
    }

    @Test
    fun ordinaryFailureKeepsProviderFailureClassification() {
        assertEquals(
            AssistantTurnFailureKind.PROVIDER_FAILURE,
            AssistantTurnFailurePolicy.classify(IllegalStateException("upstream failed")),
        )
    }
}
