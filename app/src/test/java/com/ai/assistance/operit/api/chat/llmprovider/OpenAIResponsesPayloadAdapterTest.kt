package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesPayloadAdapterTest {
    @Test
    fun savedAssistantPreviewImagesRemainUsableWhenSendingAnyNextMessage() {
        for (nextMessage in listOf("继续", "你好", "查看论文")) {
            val history = JSONArray()
            repeat(84) { history.put(JSONObject().put("role", "assistant").put("content", "history $it")) }
            history.put(assistantMessage("", JSONArray().put(functionCall("preview", "render", "{}"))))
            history.put(toolMessage("preview", "four preview pages"))
            val parts = JSONArray()
            repeat(4) { parts.put(imagePart("data:image/png;base64,page$it")) }
            parts.put(JSONObject().put("type", "text").put("text", "Original final answer"))
            history.put(JSONObject().put("role", "assistant").put("content", parts))
            history.put(JSONObject().put("role", "user").put("content", nextMessage))
            val original = history.toString()
            val input = convert(history)
            assertEquals("function_call", input.getJSONObject(84).getString("type"))
            assertEquals("function_call_output", input.getJSONObject(85).getString("type"))
            val attachments = input.getJSONObject(86)
            assertEquals("user", attachments.getString("role"))
            assertTrue(attachments.getJSONArray("content").getJSONObject(0).getString("text").contains("assistant history"))
            assertEquals((0..3).map { "data:image/png;base64,page$it" }, imageUrls(input))
            assertEquals("assistant", input.getJSONObject(87).getString("role"))
            assertEquals("Original final answer", input.getJSONObject(87).getJSONArray("content").getJSONObject(0).getString("text"))
            assertEquals(nextMessage, input.getJSONObject(88).getString("content"))
            assertEquals(original, history.toString())
            assertEquals(input.toString(), convert(history).toString())
        }
    }

    @Test
    fun interleavedAssistantTextAndImagesKeepOrderBeforeToolCalls() {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", "before"))
            .put(imagePart("https://example.test/one.png"))
            .put(JSONObject().put("type", "text").put("text", "between"))
            .put(imagePart("https://example.test/two.png"))
            .put(JSONObject().put("type", "text").put("text", "after"))
        val message = assistantMessage("", JSONArray().put(functionCall("call", "inspect", "{}")))
            .put("content", parts)
        val input = convert(JSONArray().put(message).put(toolMessage("call", "done")))
        assertEquals(listOf("assistant", "user", "assistant", "user", "assistant"),
            (0..4).map { input.getJSONObject(it).getString("role") })
        assertEquals(listOf("before", "between", "after"), (0..4 step 2).map {
            input.getJSONObject(it).getJSONArray("content").getJSONObject(0).getString("text")
        })
        assertEquals("function_call", input.getJSONObject(5).getString("type"))
        assertEquals("function_call_output", input.getJSONObject(6).getString("type"))
    }

    @Test
    fun imageOnlyAssistantAndUserImagesKeepPayloadAndDetail() {
        for (role in listOf("assistant", "user")) {
            val images = JSONArray().put(imagePart("https://example.test/image.png"))
                .put(JSONObject().put("type", "input_image").put("file_id", "file-test").put("detail", "low"))
            val input = convert(JSONArray().put(JSONObject().put("role", role).put("content", images)))
            assertEquals(1, input.length())
            val message = input.getJSONObject(0)
            assertEquals("user", message.getString("role"))
            val content = message.getJSONArray("content")
            val offset = if (role == "assistant") 1 else 0
            assertEquals("high", content.getJSONObject(offset).getString("detail"))
            assertEquals("file-test", content.getJSONObject(offset + 1).getString("file_id"))
            assertEquals("low", content.getJSONObject(offset + 1).getString("detail"))
        }
    }

    @Test
    fun imagesDoNotPermitCrossingAnUnclosedToolTransaction() {
        assertThrows(ProviderToolHistoryProtocolException::class.java) {
            convert(JSONArray()
                .put(assistantMessage("", JSONArray().put(functionCall("call", "render", "{}"))))
                .put(JSONObject().put("role", "assistant").put("content", JSONArray().put(imagePart("https://example.test/page.png")))))
        }
    }

    private fun imagePart(url: String) = JSONObject().put("type", "image_url")
        .put("image_url", JSONObject().put("url", url).put("detail", "high"))

    private fun imageUrls(input: JSONArray): List<String> = buildList {
        for (i in 0 until input.length()) {
            val message = input.getJSONObject(i)
            val content = message.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                if (part.optString("type") == "input_image") {
                    assertEquals("user", message.getString("role"))
                    add(part.getString("image_url"))
                }
            }
        }
    }

    @Test
    fun assistantTextPrecedesTheAdjacentFunctionCallAndOutputPair() {
        val input =
            convert(
                JSONArray()
                    .put(
                        assistantMessage(
                            text = "我先读取文件。",
                            functionCalls =
                                JSONArray().put(
                                    functionCall(
                                        callId = "call-1",
                                        name = "read_file",
                                        arguments = """{"path":"notes.txt"}""",
                                    )
                                ),
                        )
                    )
                    .put(toolMessage(callId = "call-1", output = "file contents"))
            )

        assertEquals(
            listOf("message", "function_call", "function_call_output"),
            inputTypes(input),
        )
        assertEquals("call-1", input.getJSONObject(1).getString("call_id"))
        assertEquals("call-1", input.getJSONObject(2).getString("call_id"))
    }

    @Test
    fun parallelFunctionCallsAndOutputsKeepOriginalHistoryOrder() {
        val input =
            convert(
                JSONArray()
                    .put(
                        assistantMessage(
                            text = "并行检查两个文件。",
                            functionCalls =
                                JSONArray()
                                    .put(
                                        functionCall(
                                            callId = "call-a",
                                            name = "read_file",
                                            arguments = """{"path":"a.txt"}""",
                                        )
                                    )
                                    .put(
                                        functionCall(
                                            callId = "call-b",
                                            name = "read_file",
                                            arguments = """{"path":"b.txt"}""",
                                        )
                                    ),
                        )
                    )
                    .put(toolMessage(callId = "call-b", output = "B"))
                    .put(toolMessage(callId = "call-a", output = "A"))
            )

        assertEquals(
            listOf(
                "message",
                "function_call",
                "function_call",
                "function_call_output",
                "function_call_output",
            ),
            inputTypes(input),
        )
        assertEquals("call-a", input.getJSONObject(1).getString("call_id"))
        assertEquals("call-b", input.getJSONObject(2).getString("call_id"))
        assertEquals("call-b", input.getJSONObject(3).getString("call_id"))
        assertEquals("call-a", input.getJSONObject(4).getString("call_id"))
    }

    @Test
    fun unrelatedMessagesCannotCrossAnOpenFunctionCallBoundary() {
        assertThrows(ProviderToolHistoryProtocolException::class.java) {
            convert(
                JSONArray()
                    .put(
                        assistantMessage(
                            text = "",
                            functionCalls =
                                JSONArray().put(
                                    functionCall(
                                        callId = "call-1",
                                        name = "read_file",
                                        arguments = """{"path":"notes.txt"}""",
                                    )
                                ),
                        )
                    )
                    .put(JSONObject().put("role", "assistant").put("content", "中间消息"))
                    .put(toolMessage(callId = "call-1", output = "file contents"))
            )
        }
    }

    @Test
    fun duplicateFunctionCallAndOutputAreEmittedOnce() {
        val duplicateCalls =
            JSONArray()
                .put(
                    functionCall(
                        callId = "call-1",
                        name = "read_file",
                        arguments = """{"path":"notes.txt"}""",
                    )
                )
                .put(
                    functionCall(
                        callId = "call-1",
                        name = "read_file",
                        arguments = """{ "path": "notes.txt" }""",
                    )
                )
        val input =
            convert(
                JSONArray()
                    .put(assistantMessage(text = "", functionCalls = duplicateCalls))
                    .put(toolMessage(callId = "call-1", output = "file contents"))
                    .put(toolMessage(callId = "call-1", output = "file contents"))
            )

        assertEquals(
            listOf("function_call", "function_call_output"),
            inputTypes(input),
        )
    }

    @Test
    fun conflictingOutputsForTheSameCallIdRemainAProtocolError() {
        val messages =
            JSONArray()
                .put(
                    assistantMessage(
                        text = "",
                        functionCalls =
                            JSONArray().put(
                                functionCall(
                                    callId = "call-1",
                                    name = "read_file",
                                    arguments = """{"path":"notes.txt"}""",
                                )
                            ),
                    )
                )
                .put(toolMessage(callId = "call-1", output = "first"))
                .put(toolMessage(callId = "call-1", output = "second"))

        assertThrows(OpenAIResponsesProtocolException::class.java) {
            convert(messages)
        }
    }

    @Test
    fun missingFunctionCallOutputFailsBeforeRequestSubmission() {
        val messages =
            JSONArray().put(
                assistantMessage(
                    text = "",
                    functionCalls =
                        JSONArray().put(
                            functionCall(
                                callId = "call-1",
                                name = "read_file",
                                arguments = """{"path":"notes.txt"}""",
                            )
                        ),
                )
            )

        assertThrows(ProviderToolHistoryProtocolException::class.java) {
            convert(messages)
        }
    }

    @Test
    fun orphanFunctionCallOutputFailsBeforeRequestSubmission() {
        assertThrows(ProviderToolHistoryProtocolException::class.java) {
            convert(
                JSONArray().put(
                    toolMessage(callId = "call-orphan", output = "unexpected")
                )
            )
        }
    }

    @Test
    fun deepSeekCacheHitFieldIsSeparatedFromUncachedInput() {
        val usage =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject()
                    .put("prompt_tokens", 100)
                    .put("prompt_cache_hit_tokens", 76)
                    .put("completion_tokens", 12)
            )

        requireNotNull(usage)
        assertEquals(100, usage.totalInputTokens)
        assertEquals(24, usage.actualInputTokens)
        assertEquals(76, usage.cachedInputTokens)
        assertEquals(
            ProviderCacheMetricState.REPORTED,
            usage.cacheMetricState,
        )
    }

    @Test
    fun inconsistentNestedCachedTokensAreClampedAndMarkedInvalid() {
        val usage =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject()
                    .put("input_tokens", 20)
                    .put(
                        "input_tokens_details",
                        JSONObject().put("cached_tokens", 99),
                    )
            )

        requireNotNull(usage)
        assertEquals(20, usage.totalInputTokens)
        assertEquals(20, usage.cachedInputTokens)
        assertEquals(0, usage.actualInputTokens)
        assertEquals(
            ProviderCacheMetricState.INVALID,
            usage.cacheMetricState,
        )
    }

    @Test
    fun explicitZeroUsageWithCacheFieldIsReportedNotMissing() {
        val usage =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject()
                    .put("prompt_tokens", 0)
                    .put("prompt_cache_hit_tokens", 0)
                    .put("completion_tokens", 0)
            )

        requireNotNull(usage)
        assertEquals(0, usage.totalInputTokens)
        assertEquals(0, usage.cachedInputTokens)
        assertEquals(
            ProviderCacheMetricState.REPORTED,
            usage.cacheMetricState,
        )
    }

    private fun convert(messages: JSONArray): JSONArray =
        OpenAIResponsesPayloadAdapter
            .toResponsesRequest(JSONObject().put("messages", messages))
            .getJSONArray("input")

    private fun assistantMessage(
        text: String,
        functionCalls: JSONArray,
    ): JSONObject =
        JSONObject()
            .put("role", "assistant")
            .put("content", text)
            .put("tool_calls", functionCalls)

    private fun functionCall(
        callId: String,
        name: String,
        arguments: String,
    ): JSONObject =
        JSONObject()
            .put("id", callId)
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("arguments", arguments),
            )

    private fun toolMessage(
        callId: String,
        output: String,
    ): JSONObject =
        JSONObject()
            .put("role", "tool")
            .put("tool_call_id", callId)
            .put("content", output)

    private fun inputTypes(input: JSONArray): List<String> =
        (0 until input.length()).map { index ->
            input.getJSONObject(index).getString("type")
        }
}
