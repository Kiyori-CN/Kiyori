package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallDescriptor
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolHistoryState
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolResultDescriptor
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.ChatMarkupRegex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkupManagerToolHistoryTest {
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
    }
}
