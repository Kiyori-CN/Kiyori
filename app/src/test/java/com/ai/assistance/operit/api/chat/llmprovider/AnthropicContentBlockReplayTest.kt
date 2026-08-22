package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicContentBlockReplayTest {
    @Test
    fun streamingAccumulatorPreservesThinkingSignatureToolIdentityAndOrder() {
        val accumulator =
            AnthropicStreamingContentBlockAccumulator("claude-opus-4-6")

        accumulator.startBlock(
            index = 0,
            contentBlock =
                JSONObject()
                    .put("type", "thinking")
                    .put("thinking", ""),
        )
        accumulator.appendDelta(
            index = 0,
            delta =
                JSONObject()
                    .put("type", "thinking_delta")
                    .put("thinking", "reasoning"),
        )
        accumulator.appendDelta(
            index = 0,
            delta =
                JSONObject()
                    .put("type", "signature_delta")
                    .put("signature", "signed"),
        )
        accumulator.stopBlock(0)

        accumulator.startBlock(
            index = 1,
            contentBlock =
                JSONObject()
                    .put("type", "text")
                    .put("text", ""),
        )
        accumulator.appendDelta(
            index = 1,
            delta =
                JSONObject()
                    .put("type", "text_delta")
                    .put("text", "answer"),
        )
        accumulator.stopBlock(1)

        accumulator.startBlock(
            index = 2,
            contentBlock =
                JSONObject()
                    .put("type", "tool_use")
                    .put("id", "toolu_123")
                    .put("name", "read_file")
                    .put("input", JSONObject()),
        )
        accumulator.appendDelta(
            index = 2,
            delta =
                JSONObject()
                    .put("type", "input_json_delta")
                    .put("partial_json", """{"path":"one.txt"}"""),
        )
        accumulator.stopBlock(2)

        val metadataTag = requireNotNull(accumulator.createMetadataTag())
        val snapshot =
            requireNotNull(
                AnthropicContentBlockReplayCodec.extractSnapshot(metadataTag)
            )
        val blocks = snapshot.contentBlocks

        assertEquals("claude-opus-4-6", snapshot.modelName)
        assertEquals(listOf("thinking", "text", "tool_use"), blockTypes(blocks))
        assertEquals("reasoning", blocks.getJSONObject(0).getString("thinking"))
        assertEquals("signed", blocks.getJSONObject(0).getString("signature"))
        assertEquals("toolu_123", blocks.getJSONObject(2).getString("id"))
        assertEquals(
            "one.txt",
            blocks.getJSONObject(2).getJSONObject("input").getString("path"),
        )
    }

    @Test
    fun modelSwitchRemovesOnlyThinkingBlocks() {
        val blocks =
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", "thinking")
                        .put("thinking", "reasoning")
                        .put("signature", "signed")
                )
                .put(
                    JSONObject()
                        .put("type", "redacted_thinking")
                        .put("data", "redacted")
                )
                .put(
                    JSONObject()
                        .put("type", "tool_use")
                        .put("id", "toolu_123")
                        .put("name", "read_file")
                        .put("input", JSONObject().put("path", "one.txt"))
                )
                .put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", "answer")
                )
        val metadataTag =
            requireNotNull(
                AnthropicContentBlockReplayCodec.createMetadataTag(
                    modelName = "claude-opus-4-6",
                    contentBlocks = blocks,
                )
            )
        val snapshot =
            requireNotNull(
                AnthropicContentBlockReplayCodec.extractSnapshot(metadataTag)
            )

        val sameModel =
            AnthropicContentBlockReplayCodec.replayBlocksForModel(
                snapshot,
                "claude-opus-4-6",
            )
        val changedModel =
            AnthropicContentBlockReplayCodec.replayBlocksForModel(
                snapshot,
                "claude-sonnet-4-6",
            )

        assertEquals(
            listOf("thinking", "redacted_thinking", "tool_use", "text"),
            blockTypes(sameModel),
        )
        assertEquals(listOf("tool_use", "text"), blockTypes(changedModel))
    }

    @Test
    fun conflictingOrUnsignedMetadataFailsExplicitly() {
        val first =
            requireNotNull(
                AnthropicContentBlockReplayCodec.createMetadataTag(
                    modelName = "claude-opus-4-6",
                    contentBlocks =
                        JSONArray().put(
                            JSONObject()
                                .put("type", "thinking")
                                .put("thinking", "one")
                                .put("signature", "signed-one")
                        ),
                )
            )
        val second =
            requireNotNull(
                AnthropicContentBlockReplayCodec.createMetadataTag(
                    modelName = "claude-opus-4-6",
                    contentBlocks =
                        JSONArray().put(
                            JSONObject()
                                .put("type", "thinking")
                                .put("thinking", "two")
                                .put("signature", "signed-two")
                        ),
                )
            )

        assertThrows(AnthropicProtocolException::class.java) {
            AnthropicContentBlockReplayCodec.extractSnapshot(first + second)
        }
        assertThrows(AnthropicProtocolException::class.java) {
            AnthropicContentBlockReplayCodec.createMetadataTag(
                modelName = "claude-opus-4-6",
                contentBlocks =
                    JSONArray().put(
                        JSONObject()
                            .put("type", "thinking")
                            .put("thinking", "unsigned")
                    ),
            )
        }
    }

    @Test
    fun providerMetadataFilteringRetainsOnlyDeclaredProtocolState() {
        val content =
            "answer" +
                ChatMarkupRegex.geminiThoughtSignatureMetaTag("gemini") +
                ChatMarkupRegex.openAiResponsesReasoningMetaTag("openai") +
                ChatMarkupRegex.anthropicContentBlocksMetaTag("anthropic")

        val filtered =
            ChatUtils.stripProviderReplayMetadata(
                content = content,
                retainedKinds =
                    setOf(ProviderReplayMetadataKind.ANTHROPIC_CONTENT_BLOCKS),
            )

        assertTrue(filtered.contains("answer"))
        assertTrue(filtered.contains("anthropic:content_blocks"))
        assertFalse(filtered.contains("gemini:thought_signature"))
        assertFalse(filtered.contains("openai:responses_reasoning"))
    }

    private fun blockTypes(blocks: JSONArray): List<String> {
        return (0 until blocks.length()).map { index ->
            blocks.getJSONObject(index).getString("type")
        }
    }
}
