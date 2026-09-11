package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallDescriptor
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolHistoryState
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolResultDescriptor
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkupManagerToolHistoryTest {
    @Test fun invalidatedRoundDoesNotDiscardLaterSuccessfulAnswerAtCompletion() {
        val calls = "<tool_a name=\"read_file\"><param name=\"path\">a</param></tool_a>\n" +
            "<tool_b name=\"write_file\"><param name=\"path\"></param></tool_b>"
        val original = "前文。" + calls
        val marker = AssistantReplayHistoryProjector.invalidatedToolRoundMarker(original)
        val nextRound = "\n<status type=\"warning\">本轮调用作废，未执行</status>\n" +
            "<tool_c name=\"read_file\"></tool_c>" +
            "<tool_result_c name=\"read_file\"><content>ok</content></tool_result_c>最终论文。"
        val raw = original + marker + nextRound
        val durable = AssistantReplayHistoryProjector.requireClosed(raw, "assistant_completion")
        assertEquals("前文。" + nextRound, durable)
        assertTrue(raw.contains(calls))
        assertEquals(durable, AssistantReplayHistoryProjector.requireClosed(durable, "next_send"))
        val twice = raw + original + marker + nextRound
        assertEquals(durable + durable, AssistantReplayHistoryProjector.requireClosed(twice, "two_recoveries"))
    }

    @Test fun invalidationCannotHideExecutedOrChangedCalls() {
        val calls = "<tool_a name=\"read_file\"></tool_a><tool_b name=\"write_file\"></tool_b>"
        val marker = AssistantReplayHistoryProjector.invalidatedToolRoundMarker(calls)
        assertTrue(AssistantReplayHistoryProjector.project(calls.replace("read_file", "delete_file") + marker).truncated)
        assertTrue(AssistantReplayHistoryProjector.project(calls +
            "<tool_result_a name=\"read_file\"><content>already executed</content></tool_result_a>" + marker).truncated)
        assertTrue(AssistantReplayHistoryProjector.project(calls + "<status type=\"warning\">未执行</status>").truncated)
    }

    @Test fun invalidatedToolRoundAndWarningCompileWithoutInventedToolResults() {
        val original = "将继续编辑论文。<tool_cut name=\"package_proxy\"><param name=\"params\"></param></tool_cut>"
        val warning = "<status type=\"warning\">本轮工具作废，未执行</status>"
        val projected = AssistantReplayHistoryProjector.project(original)
        assertTrue(projected.truncated)
        val feedback = ConversationMarkupManager.feedbackHistoryTurn(emptyList(), warning)
        assertEquals(com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.USER, feedback.kind)
        val compiled = com.ai.assistance.operit.api.chat.llmprovider.StructuredToolCallBridge.buildMessagesJson(
            listOf(com.ai.assistance.operit.core.chat.hooks.PromptTurn(
                kind = com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.ASSISTANT, content = projected.content), feedback), false)
        assertTrue(compiled.contains("本轮工具作废"))
        org.junit.Assert.assertFalse(compiled.contains("tool_calls"))
        org.junit.Assert.assertFalse(compiled.contains("tool_call_id"))
    }

    @Test fun thinkingWarningIsUserFeedbackAndRealResultsRemainTyped() {
        val feedback = ConversationMarkupManager.feedbackHistoryTurn(emptyList(), "<status type=\"warning\">请输出正文</status>")
        val compiled = com.ai.assistance.operit.api.chat.llmprovider.StructuredToolCallBridge.buildMessagesJson(listOf(feedback), false)
        assertEquals("user", org.json.JSONArray(compiled).getJSONObject(0).getString("role"))
        val result = ToolResult(toolName = "read_file", success = true, result = StringResultData("ok"))
        assertEquals(com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.TOOL_RESULT,
            ConversationMarkupManager.feedbackHistoryTurn(listOf(result), "result").kind)
    }

    @org.junit.Test
    fun officePreviewJson_keepsMultimodalPagesOutsideTruncatedToolPayload() {
        val raw = """{"data":{"report":"${"x".repeat(80_000)}","visual_pages":[{"page":3,"image":"<link type=\"image\" id=\"office-page-3\"></link>"}]}}"""
        val message = ConversationMarkupManager.buildBoundedToolResultMessage(listOf(
            ToolResult(toolName = "office_render_preview", success = true, result = StringResultData(raw))
        ))
        org.junit.Assert.assertTrue(message.contains("<link type=\"image\" id=\"office-page-3\"></link>"))
    }
    @Test
    fun boundedParallelResultsRetainEveryCompleteProtocolEnvelope() {
        val results =
            (1..4).map { index ->
                ToolResult(
                    toolName = "tool_$index",
                    success = true,
                    result = StringResultData("result-$index:" + "x".repeat(40_000)),
                )
            }

        val message = ConversationMarkupManager.buildBoundedToolResultMessage(results)
        val resultBlocks = ChatMarkupRegex.toolOrToolResultBlock.findAll(message).toList()

        assertTrue(message.length <= ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS)
        assertEquals(4, resultBlocks.size)
        assertEquals(
            results.map { it.toolName },
            resultBlocks.map { block ->
                ChatMarkupRegex.extractToolResultProtocolName(block.value)
            },
        )
        resultBlocks.forEachIndexed { index, block ->
            assertTrue(block.value.contains("result-${index + 1}:"))
        }
        assertTrue(
            ChatMarkupRegex.toolOrToolResultBlock
                .replace(message, "")
                .isBlank()
        )

        val providerHistory = ProviderToolHistoryState()
        providerHistory.acceptToolCallDescriptors(
            calls =
                results.mapIndexed { index, result ->
                    ProviderToolCallDescriptor(
                        callId = "call-${index + 1}",
                        toolName = result.toolName,
                    )
                },
            boundary = "parallel_calls",
        )
        providerHistory.acceptNamedToolResults(
            results =
                resultBlocks.map { block ->
                    ProviderToolResultDescriptor(
                        callId = null,
                        toolName = ChatMarkupRegex.extractToolResultProtocolName(block.value),
                    )
                },
            boundary = "parallel_results",
        )
        providerHistory.requireClosed("user_boundary")
    }

    @Test
    fun boundedParallelImageResultRetainsWholeLinkMarkup() {
        val imageLink = "<link type=\"image\" id=\"image-1\">preview</link>"
        val results =
            listOf(
                ToolResult(
                    toolName = "image_tool",
                    success = true,
                    result = StringResultData("x".repeat(40_000) + imageLink),
                ),
                ToolResult(
                    toolName = "text_tool",
                    success = true,
                    result = StringResultData("y".repeat(40_000)),
                ),
            )

        val message = ConversationMarkupManager.buildBoundedToolResultMessage(results)

        assertTrue(message.length <= ToolExecutionLimits.MAX_FINAL_TOOL_RESULT_MESSAGE_CHARS)
        assertEquals(2, ChatMarkupRegex.toolOrToolResultBlock.findAll(message).count())
        assertTrue(message.contains("<link type=\"image\" id=\"image-1\"></link>"))
        val calls = results.joinToString("\n") {
            "<tool name=\"${it.toolName}\"></tool>"
        }
        val completed = AssistantReplayHistoryProjector.requireClosed(
            calls + message + "\n最终测评报告", "assistant_completion"
        )
        assertTrue(completed.endsWith("\n最终测评报告"))
        assertTrue(completed.contains("<link type=\"image\" id=\"image-1\"></link>"))
        assertEquals(completed, AssistantReplayHistoryProjector.project(completed).content)
    }
}
