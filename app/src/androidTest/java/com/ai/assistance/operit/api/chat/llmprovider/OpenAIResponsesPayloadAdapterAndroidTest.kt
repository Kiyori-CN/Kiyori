package com.ai.assistance.operit.api.chat.llmprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAIResponsesPayloadAdapterAndroidTest {
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
}
