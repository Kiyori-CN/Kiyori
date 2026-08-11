package com.ai.assistance.operit.api.chat.llmprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ApiProviderType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAIResponsesPayloadAdapterAndroidTest {
    @Test
    fun compatibleEndpointFeatureCompiler_emitsOnlyFiveLevelReasoningEffort() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES,
                modelName = "gpt-5.6-sol",
                apiEndpoint = "https://pipio.io/v1/responses",
            )
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = true, thinkingQualityLevel = 4),
            )
        val request = JSONObject().put("model", "gpt-5.6-sol").put("stream", true)

        OpenAIResponsesRequestFeatureCompiler.apply(
            requestJson = request,
            compiledRequest = compiled,
            stream = true,
        )

        val reasoning = request.getJSONObject("reasoning")
        assertEquals("xhigh", reasoning.getString("effort"))
        assertFalse(reasoning.has("summary"))
        assertFalse(request.has("include"))
        assertFalse(request.has("background"))
        assertFalse(request.has("store"))
    }

    @Test
    fun officialEndpointFeatureCompiler_emitsBackgroundAndReasoningReplayContract() {
        val profile =
            ModelCapabilityResolver.resolve(
                providerType = ApiProviderType.OPENAI_RESPONSES,
                modelName = "gpt-5.6-sol",
                apiEndpoint = "https://api.openai.com/v1/responses",
            )
        val compiled =
            ModelRequestCompiler.compile(
                profile = profile,
                intent = UserExecutionIntent(enableThinking = true, thinkingQualityLevel = 4),
            )
        val request = JSONObject().put("model", "gpt-5.6-sol").put("stream", true)

        OpenAIResponsesRequestFeatureCompiler.apply(
            requestJson = request,
            compiledRequest = compiled,
            stream = true,
        )

        val reasoning = request.getJSONObject("reasoning")
        assertEquals("xhigh", reasoning.getString("effort"))
        assertEquals("auto", reasoning.getString("summary"))
        assertEquals(
            "reasoning.encrypted_content",
            request.getJSONArray("include").getString(0),
        )
        assertTrue(request.getBoolean("background"))
        assertFalse(request.getBoolean("store"))
    }

    @Test
    fun reasoningReplay_keepsExplicitEmptySummaryArray() {
        val metadataTag =
            requireNotNull(
                OpenAIResponsesPayloadAdapter.createReasoningMetadataTag(
                    JSONObject()
                        .put("type", "reasoning")
                        .put("id", "reasoning-1")
                        .put("encrypted_content", "encrypted"),
                ),
            )
        val request =
            JSONObject().put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", metadataTag),
                ),
            )

        val input = OpenAIResponsesPayloadAdapter.toResponsesRequest(request).getJSONArray("input")
        val reasoning = input.getJSONObject(0)

        assertEquals("reasoning", reasoning.getString("type"))
        assertEquals("reasoning-1", reasoning.getString("id"))
        assertTrue(reasoning.has("summary"))
        assertEquals(0, reasoning.getJSONArray("summary").length())
    }

    @Test
    fun reasoningReplay_preservesNonEmptySummaryArray() {
        val summary =
            JSONArray().put(
                JSONObject()
                    .put("type", "summary_text")
                    .put("text", "reasoning summary"),
            )
        val metadataTag =
            requireNotNull(
                OpenAIResponsesPayloadAdapter.createReasoningMetadataTag(
                    JSONObject()
                        .put("type", "reasoning")
                        .put("id", "reasoning-2")
                        .put("encrypted_content", "encrypted")
                        .put("summary", summary),
                ),
            )
        val request =
            JSONObject().put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", metadataTag),
                ),
            )

        val reasoning =
            OpenAIResponsesPayloadAdapter
                .toResponsesRequest(request)
                .getJSONArray("input")
                .getJSONObject(0)

        assertEquals(
            "reasoning summary",
            reasoning.getJSONArray("summary").getJSONObject(0).getString("text"),
        )
    }

    @Test
    fun completedSnapshot_exposesTextAndFunctionCall() {
        val response =
            JSONObject()
                .put(
                    "output",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("type", "output_text")
                                            .put("text", "snapshot text")
                                    )
                                )
                        )
                        .put(
                            JSONObject()
                                .put("type", "function_call")
                                .put("id", "fc-1")
                                .put("call_id", "call-1")
                                .put("name", "read_file")
                                .put("arguments", """{"path":"notes.txt"}""")
                        )
                )

        val parsed = OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(response)

        assertEquals(listOf("snapshot text"), parsed.textChunks)
        val toolCall = parsed.toolCalls.getJSONObject(0)
        assertEquals("call-1", toolCall.getString("id"))
        assertEquals("read_file", toolCall.getJSONObject("function").getString("name"))
        assertEquals(
            """{"path":"notes.txt"}""",
            toolCall.getJSONObject("function").getString("arguments"),
        )
    }

    @Test
    fun historyReplay_emitsOneFunctionCallAndOneOutputPerCallId() {
        val duplicateToolCalls =
            JSONArray()
                .put(functionCall("call-1", "read_file", """{"path":"notes.txt"}"""))
                .put(functionCall("call-1", "read_file", """{ "path": "notes.txt" }"""))
        val messages =
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", JSONObject.NULL)
                        .put("tool_calls", duplicateToolCalls)
                )
                .put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", "call-1")
                        .put("content", "file contents")
                )
                .put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", "call-1")
                        .put("content", "file contents")
                )

        val input =
            OpenAIResponsesPayloadAdapter
                .toResponsesRequest(JSONObject().put("messages", messages))
                .getJSONArray("input")

        assertEquals(1, countInputItems(input, "function_call"))
        assertEquals(1, countInputItems(input, "function_call_output"))
    }

    @Test
    fun historyReplay_rejectsConflictingFunctionCallsWithSameCallId() {
        val messages =
            JSONArray().put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", JSONObject.NULL)
                    .put(
                        "tool_calls",
                        JSONArray()
                            .put(functionCall("call-1", "read_file", """{"path":"a.txt"}"""))
                            .put(functionCall("call-1", "read_file", """{"path":"b.txt"}"""))
                    )
            )

        val failure =
            runCatching {
                OpenAIResponsesPayloadAdapter.toResponsesRequest(
                    JSONObject().put("messages", messages)
                )
            }.exceptionOrNull()

        assertTrue(failure is ProviderToolCallIdentityConflictException)
    }

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
                    .put("arguments", arguments)
            )

    private fun countInputItems(
        input: JSONArray,
        type: String,
    ): Int =
        (0 until input.length()).count { index ->
            input.optJSONObject(index)?.optString("type", "") == type
        }
}
