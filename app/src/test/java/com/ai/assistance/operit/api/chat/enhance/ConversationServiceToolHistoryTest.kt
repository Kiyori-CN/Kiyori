package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationServiceToolHistoryTest {
    @Test
    fun titleRemovesReasoningMetadataBeforeLengthLimit() {
        val title = "论文自我介绍与办公套件测试"
        val metadata = "<meta provider=\"openai:responses_reasoning\">${"a".repeat(200)}</meta>"
        assertEquals(title, ConversationService.sanitizeConversationTitle("<think>reason</think>" + title + metadata))
        assertEquals(title, ConversationService.sanitizeConversationTitle(metadata + "\n" + title))
        assertEquals("", ConversationService.sanitizeConversationTitle(metadata))
    }

    @Test
    fun parallelResultsAreMergedIntoOneProviderTurnWhileCallsRemainDistinct() {
        val firstCall = PromptTurn(PromptTurnKind.TOOL_CALL, "<tool name=\"one\"></tool>")
        val secondCall = PromptTurn(PromptTurnKind.TOOL_CALL, "<tool name=\"two\"></tool>")
        val firstResult =
            PromptTurn(
                PromptTurnKind.TOOL_RESULT,
                "<tool_result name=\"one\"><content>1</content></tool_result>",
            )
        val secondResult =
            PromptTurn(
                PromptTurnKind.TOOL_RESULT,
                "<tool_result name=\"two\"><content>2</content></tool_result>",
            )

        val merged =
            ConversationService.mergePreparedToolSegments(
                listOf(firstCall, secondCall, firstResult, secondResult)
            )

        assertEquals(
            listOf(
                PromptTurnKind.TOOL_CALL,
                PromptTurnKind.TOOL_CALL,
                PromptTurnKind.TOOL_RESULT,
            ),
            merged.map { it.kind },
        )
        assertTrue(merged.last().content.contains(firstResult.content))
        assertTrue(merged.last().content.contains(secondResult.content))
    }
}
