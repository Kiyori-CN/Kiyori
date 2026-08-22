package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ChatMarkupRegex
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal class AnthropicProtocolException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class AnthropicContentBlockSnapshot(
    val modelName: String,
    val contentBlocks: JSONArray,
)

/**
 * Anthropic extended-thinking 与工具回合的持久化 replay codec。
 *
 * `<think>` 和工具 XML 只是 Kiyori 的可见投影，不能替代 Anthropic 原生 content blocks。
 * signature、redacted_thinking、原始 tool_use.id 以及 block 顺序必须跨工具 hop 和进程重建
 * 保持，因此把完整 assistant content 数组作为隐藏消息元数据保存。模型切换时只剥离
 * thinking 类 block，保留 text/tool_use 以闭合仍在历史中的工具事务。
 */
internal object AnthropicContentBlockReplayCodec {
    private val protocolSensitiveTypes =
        setOf(
            "thinking",
            "redacted_thinking",
            "tool_use",
        )

    fun createMetadataTag(
        modelName: String,
        contentBlocks: JSONArray,
    ): String? {
        validateModelName(modelName)
        val clonedBlocks = cloneAndValidateBlocks(contentBlocks)
        val hasProtocolSensitiveBlock =
            (0 until clonedBlocks.length()).any { index ->
                clonedBlocks.getJSONObject(index).getString("type") in protocolSensitiveTypes
            }
        if (!hasProtocolSensitiveBlock) {
            return null
        }

        val payload =
            JSONObject().apply {
                put("version", 1)
                put("model", modelName)
                put("content", clonedBlocks)
            }
        val payloadBase64 =
            Base64.getEncoder().encodeToString(
                payload.toString().toByteArray(Charsets.UTF_8)
            )
        return ChatMarkupRegex.anthropicContentBlocksMetaTag(payloadBase64)
    }

    fun extractSnapshot(content: String): AnthropicContentBlockSnapshot? {
        val payloads = ChatMarkupRegex.extractAnthropicContentBlocksPayloads(content)
        if (payloads.isEmpty()) {
            return null
        }

        val snapshots = payloads.map(::decodePayload)
        val first = snapshots.first()
        val firstCanonical = canonicalSnapshot(first)
        snapshots.drop(1).forEach { snapshot ->
            if (canonicalSnapshot(snapshot) != firstCanonical) {
                throw AnthropicProtocolException(
                    "Conflicting Anthropic content-block metadata in one assistant message"
                )
            }
        }
        return first
    }

    fun contentWithoutMetadata(content: String): String {
        return ChatMarkupRegex.removeAnthropicContentBlocksMeta(content)
    }

    fun replayBlocksForModel(
        snapshot: AnthropicContentBlockSnapshot,
        currentModelName: String,
    ): JSONArray {
        validateModelName(currentModelName)
        val sameModel = snapshot.modelName == currentModelName
        val replayBlocks = JSONArray()
        for (index in 0 until snapshot.contentBlocks.length()) {
            val block = snapshot.contentBlocks.getJSONObject(index)
            val type = block.getString("type")
            if (
                !sameModel &&
                    (type == "thinking" || type == "redacted_thinking")
            ) {
                continue
            }
            replayBlocks.put(JSONObject(block.toString()))
        }
        if (replayBlocks.length() == 0) {
            throw AnthropicProtocolException(
                "Anthropic assistant content became empty after applying the model-switch replay contract"
            )
        }
        return cloneAndValidateBlocks(
            contentBlocks = replayBlocks,
            requireThinkingSignature = sameModel,
        )
    }

    fun toolUseIds(contentBlocks: JSONArray): List<String> {
        val ids = mutableListOf<String>()
        for (index in 0 until contentBlocks.length()) {
            val block = contentBlocks.getJSONObject(index)
            if (block.getString("type") == "tool_use") {
                ids.add(block.getString("id"))
            }
        }
        return ids
    }

    private fun decodePayload(payloadBase64: String): AnthropicContentBlockSnapshot {
        val payload =
            try {
                val decoded =
                    String(
                        Base64.getDecoder().decode(payloadBase64),
                        Charsets.UTF_8,
                    )
                JSONObject(decoded)
            } catch (error: Exception) {
                throw AnthropicProtocolException(
                    "Anthropic content-block metadata cannot be decoded",
                    error,
                )
            }

        if (payload.optInt("version", -1) != 1) {
            throw AnthropicProtocolException(
                "Unsupported Anthropic content-block metadata version"
            )
        }
        val modelName = payload.optString("model", "").trim()
        validateModelName(modelName)
        val blocks =
            payload.optJSONArray("content")
                ?: throw AnthropicProtocolException(
                    "Anthropic content-block metadata has no content array"
                )
        return AnthropicContentBlockSnapshot(
            modelName = modelName,
            contentBlocks = cloneAndValidateBlocks(blocks),
        )
    }

    private fun cloneAndValidateBlocks(
        contentBlocks: JSONArray,
        requireThinkingSignature: Boolean = true,
    ): JSONArray {
        if (contentBlocks.length() == 0) {
            throw AnthropicProtocolException(
                "Anthropic assistant content-block metadata is empty"
            )
        }

        val clonedBlocks = JSONArray()
        val toolUseIds = mutableSetOf<String>()
        for (index in 0 until contentBlocks.length()) {
            val source =
                contentBlocks.optJSONObject(index)
                    ?: throw AnthropicProtocolException(
                        "Anthropic content block at index $index is not an object"
                    )
            val block = JSONObject(source.toString())
            when (val type = block.optString("type", "").trim()) {
                "text" -> {
                    if (!block.has("text") || block.opt("text") !is String) {
                        throw AnthropicProtocolException(
                            "Anthropic text block at index $index has no text string"
                        )
                    }
                }

                "thinking" -> {
                    if (!block.has("thinking") || block.opt("thinking") !is String) {
                        throw AnthropicProtocolException(
                            "Anthropic thinking block at index $index has no thinking string"
                        )
                    }
                    if (
                        requireThinkingSignature &&
                            block.optString("signature", "").isBlank()
                    ) {
                        throw AnthropicProtocolException(
                            "Anthropic thinking block at index $index has no signature"
                        )
                    }
                }

                "redacted_thinking" -> {
                    if (block.optString("data", "").isBlank()) {
                        throw AnthropicProtocolException(
                            "Anthropic redacted_thinking block at index $index has no data"
                        )
                    }
                }

                "tool_use" -> {
                    val id = block.optString("id", "").trim()
                    val name = block.optString("name", "").trim()
                    if (id.isEmpty() || name.isEmpty()) {
                        throw AnthropicProtocolException(
                            "Anthropic tool_use block at index $index has no stable id or name"
                        )
                    }
                    if (!toolUseIds.add(id)) {
                        throw AnthropicProtocolException(
                            "Anthropic assistant content contains duplicate tool_use id $id"
                        )
                    }
                    if (block.optJSONObject("input") == null) {
                        throw AnthropicProtocolException(
                            "Anthropic tool_use block $id has no input object"
                        )
                    }
                }

                else -> {
                    throw AnthropicProtocolException(
                        "Unsupported Anthropic assistant content block type '$type' at index $index"
                    )
                }
            }
            clonedBlocks.put(block)
        }
        return clonedBlocks
    }

    private fun canonicalSnapshot(snapshot: AnthropicContentBlockSnapshot): String {
        return snapshot.modelName +
            "\n" +
            ProviderToolCallIdentityContract.canonicalJsonText(
                snapshot.contentBlocks.toString()
            )
    }

    private fun validateModelName(modelName: String) {
        if (modelName.isBlank()) {
            throw AnthropicProtocolException(
                "Anthropic content-block metadata has no model identity"
            )
        }
    }
}

internal class AnthropicStreamingContentBlockAccumulator(
    private val modelName: String,
) {
    private data class BlockState(
        val block: JSONObject,
        val partialInputJson: StringBuilder = StringBuilder(),
        var stopped: Boolean = false,
    )

    private val blocksByIndex = sortedMapOf<Int, BlockState>()

    fun startBlock(
        index: Int,
        contentBlock: JSONObject,
    ) {
        if (index < 0) {
            throw AnthropicProtocolException(
                "Anthropic content_block_start has no valid index"
            )
        }
        if (blocksByIndex.containsKey(index)) {
            throw AnthropicProtocolException(
                "Anthropic content block index $index started more than once"
            )
        }

        val block = JSONObject(contentBlock.toString())
        when (val type = block.optString("type", "").trim()) {
            "text" -> {
                if (!block.has("text")) {
                    block.put("text", "")
                }
            }

            "thinking" -> {
                if (!block.has("thinking")) {
                    block.put("thinking", "")
                }
            }

            "redacted_thinking" -> Unit

            "tool_use" -> {
                if (block.optString("id", "").isBlank() ||
                    block.optString("name", "").isBlank()
                ) {
                    throw AnthropicProtocolException(
                        "Anthropic streaming tool_use at index $index has no stable id or name"
                    )
                }
                if (block.optJSONObject("input") == null) {
                    block.put("input", JSONObject())
                }
            }

            else -> {
                throw AnthropicProtocolException(
                    "Unsupported Anthropic streaming content block type '$type' at index $index"
                )
            }
        }
        blocksByIndex[index] = BlockState(block = block)
    }

    fun appendDelta(
        index: Int,
        delta: JSONObject,
    ) {
        val state =
            blocksByIndex[index]
                ?: throw AnthropicProtocolException(
                    "Anthropic delta references unopened content block index $index"
                )
        if (state.stopped) {
            throw AnthropicProtocolException(
                "Anthropic delta references closed content block index $index"
            )
        }

        when (val deltaType = delta.optString("type", "").trim()) {
            "text_delta" -> {
                requireBlockType(index, state.block, "text")
                state.block.put(
                    "text",
                    state.block.optString("text", "") + delta.optString("text", ""),
                )
            }

            "thinking_delta" -> {
                requireBlockType(index, state.block, "thinking")
                state.block.put(
                    "thinking",
                    state.block.optString("thinking", "") +
                        delta.optString("thinking", ""),
                )
            }

            "signature_delta" -> {
                requireBlockType(index, state.block, "thinking")
                state.block.put(
                    "signature",
                    state.block.optString("signature", "") +
                        delta.optString("signature", ""),
                )
            }

            "input_json_delta" -> {
                requireBlockType(index, state.block, "tool_use")
                state.partialInputJson.append(delta.optString("partial_json", ""))
            }

            else -> {
                throw AnthropicProtocolException(
                    "Unsupported Anthropic streaming delta type '$deltaType' at index $index"
                )
            }
        }
    }

    fun stopBlock(index: Int) {
        val state =
            blocksByIndex[index]
                ?: throw AnthropicProtocolException(
                    "Anthropic content_block_stop references unopened index $index"
                )
        if (state.stopped) {
            throw AnthropicProtocolException(
                "Anthropic content block index $index stopped more than once"
            )
        }
        if (state.block.optString("type") == "tool_use" && state.partialInputJson.isNotEmpty()) {
            val input =
                try {
                    JSONObject(state.partialInputJson.toString())
                } catch (error: Exception) {
                    throw AnthropicProtocolException(
                        "Anthropic tool_use input at index $index is not complete JSON",
                        error,
                    )
                }
            state.block.put("input", input)
        }
        state.stopped = true
    }

    fun createMetadataTag(): String? {
        if (blocksByIndex.isEmpty()) {
            return null
        }
        val expectedIndices = (0 until blocksByIndex.size).toList()
        if (blocksByIndex.keys.toList() != expectedIndices) {
            throw AnthropicProtocolException(
                "Anthropic streaming content block indices are not contiguous"
            )
        }
        val unstopped = blocksByIndex.filterValues { !it.stopped }.keys
        if (unstopped.isNotEmpty()) {
            throw AnthropicProtocolException(
                "Anthropic streaming content blocks are incomplete: $unstopped"
            )
        }

        val contentBlocks = JSONArray()
        blocksByIndex.values.forEach { state ->
            contentBlocks.put(JSONObject(state.block.toString()))
        }
        return AnthropicContentBlockReplayCodec.createMetadataTag(
            modelName = modelName,
            contentBlocks = contentBlocks,
        )
    }

    private fun requireBlockType(
        index: Int,
        block: JSONObject,
        expectedType: String,
    ) {
        val actualType = block.optString("type", "")
        if (actualType != expectedType) {
            throw AnthropicProtocolException(
                "Anthropic delta for '$expectedType' targets '$actualType' block at index $index"
            )
        }
    }
}
