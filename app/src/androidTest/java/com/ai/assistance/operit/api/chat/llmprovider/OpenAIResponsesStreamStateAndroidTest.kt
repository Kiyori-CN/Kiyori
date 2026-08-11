package com.ai.assistance.operit.api.chat.llmprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAIResponsesStreamStateAndroidTest {
    @Test
    fun createdOutputAndCompleted_advanceOneResponseToTerminalState() {
        val state = newState()

        apply(
            state,
            JSONObject()
                .put("type", "response.created")
                .put("sequence_number", 1)
                .put(
                    "response",
                    JSONObject()
                        .put("id", "resp-1")
                        .put("status", "in_progress"),
                ),
        )
        apply(
            state,
            JSONObject()
                .put("type", "response.output_item.done")
                .put("sequence_number", 2)
                .put("output_index", 0)
                .put(
                    "item",
                    JSONObject()
                        .put("type", "function_call")
                        .put("call_id", "call-1")
                        .put("name", "read_file")
                        .put("arguments", """{"path":"a.txt"}"""),
                ),
        )
        apply(
            state,
            JSONObject()
                .put("type", "response.completed")
                .put("sequence_number", 3)
                .put(
                    "response",
                    JSONObject()
                        .put("id", "resp-1")
                        .put("status", "completed")
                        .put(
                            "output",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "function_call")
                                    .put("call_id", "call-1")
                                    .put("name", "read_file")
                                    .put("arguments", """{"path":"a.txt"}"""),
                            ),
                        )
                        .put(
                            "usage",
                            JSONObject()
                                .put("input_tokens", 10)
                                .put("output_tokens", 4),
                        ),
                ),
        )

        assertEquals("resp-1", state.remoteResponseId)
        assertEquals(3L, state.lastAppliedSequence)
        assertEquals(ProviderExecutionStatus.COMPLETED, state.status)
        assertTrue(state.isTerminal)
        val persisted = state.toMessageProviderState(updatedAt = 2L)
        assertEquals("call-1", JSONArray(persisted.outputItemsJson).getJSONObject(0).getString("call_id"))
        assertEquals(10, JSONObject(persisted.usageJson!!).getInt("input_tokens"))
    }

    @Test
    fun sequenceGap_isRejectedBeforeCursorMoves() {
        val state = newState()
        apply(
            state,
            JSONObject()
                .put("type", "response.created")
                .put("sequence_number", 1)
                .put("response", JSONObject().put("id", "resp-1")),
        )

        val error =
            runCatching {
                apply(
                    state,
                    JSONObject()
                        .put("type", "response.output_text.delta")
                        .put("sequence_number", 3)
                        .put("delta", "late"),
                )
            }.exceptionOrNull()

        assertTrue(error is OpenAIResponsesProtocolException)
        assertEquals(1L, state.lastAppliedSequence)
        assertFalse(state.isTerminal)
    }

    private fun apply(
        state: OpenAIResponsesExecutionState,
        payload: JSONObject,
    ) {
        val event = state.parseEvent(payload)
        state.applyNewEvent(event, payload)
    }

    private fun newState(): OpenAIResponsesExecutionState =
        OpenAIResponsesExecutionState.create(
            requestContext =
                ProviderRequestContext(
                    localExecutionId = "exec-1",
                    chatId = "chat-1",
                    messageTimestamp = 100L,
                    variantIndex = 0,
                    hopOrdinal = 0,
                ),
            provider = "OPENAI_RESPONSES",
            modelName = "gpt-5.6-sol",
            createdAt = 1L,
        )
}
