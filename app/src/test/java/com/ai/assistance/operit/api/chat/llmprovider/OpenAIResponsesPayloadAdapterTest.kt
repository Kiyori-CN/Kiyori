package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAIResponsesPayloadAdapterTest {
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
    fun parallelFunctionCallsKeepStableAdjacentOutputPairs() {
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
                "function_call_output",
                "function_call",
                "function_call_output",
            ),
            inputTypes(input),
        )
        assertEquals("call-a", input.getJSONObject(1).getString("call_id"))
        assertEquals("call-a", input.getJSONObject(2).getString("call_id"))
        assertEquals("call-b", input.getJSONObject(3).getString("call_id"))
        assertEquals("call-b", input.getJSONObject(4).getString("call_id"))
    }

    @Test
    fun unrelatedMessagesCannotSplitAFunctionCallFromItsOutput() {
        val input =
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

        assertEquals(
            listOf("function_call", "function_call_output", "message"),
            inputTypes(input),
        )
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
