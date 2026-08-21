package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject

/**
 * 将 Responses 的 reasoning summary 增量、分段完成和终态快照收敛为一条单调可见文本流。
 *
 * 同一 summary part 可能同时出现 delta、text.done、part.done 和 response.completed。这里按
 * output/summary 索引维护已确认前缀，避免重复投影；终态只能补齐已确认文本的尾部，不能把
 * 分叉内容拼接成看似成功的推理摘要。
 */
internal class OpenAIResponsesReasoningProjection {
    private val textByPartKey = linkedMapOf<String, StringBuilder>()
    private val outputIndexByPartKey = mutableMapOf<String, Int>()
    private val textByOutputIndex = linkedMapOf<Int, StringBuilder>()
    private val emittedText = StringBuilder()

    val hasEmittedText: Boolean
        get() = emittedText.isNotEmpty()

    fun acceptDelta(
        partKey: String,
        outputIndex: Int,
        delta: String,
    ): String {
        if (delta.isEmpty()) {
            return ""
        }
        return appendPartText(
            partKey = partKey,
            outputIndex = outputIndex,
            text = delta,
        )
    }

    fun acceptCompletedPart(
        partKey: String,
        outputIndex: Int,
        completedText: String,
    ): String {
        if (completedText.isEmpty()) {
            return ""
        }
        requireStablePartOutputIndex(partKey, outputIndex)
        val partText = textByPartKey.getOrPut(partKey, ::StringBuilder)
        val missingText =
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = partText.toString(),
                terminalText = completedText,
            )
        if (missingText.isEmpty()) {
            return ""
        }
        return appendPartText(
            partKey = partKey,
            outputIndex = outputIndex,
            text = missingText,
        )
    }

    fun acceptCompletedOutputItem(
        outputIndex: Int,
        completedText: String,
    ): String {
        if (completedText.isEmpty()) {
            return ""
        }
        val outputText = textByOutputIndex.getOrPut(outputIndex, ::StringBuilder)
        val missingText =
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = outputText.toString(),
                terminalText = completedText,
            )
        if (missingText.isEmpty()) {
            return ""
        }
        val separator =
            if (outputText.isEmpty() && emittedText.isNotEmpty()) {
                "\n\n"
            } else {
                ""
            }
        outputText.append(missingText)
        emittedText.append(separator).append(missingText)
        return separator + missingText
    }

    private fun appendPartText(
        partKey: String,
        outputIndex: Int,
        text: String,
    ): String {
        requireStablePartOutputIndex(partKey, outputIndex)
        val partText = textByPartKey.getOrPut(partKey, ::StringBuilder)
        val outputText = textByOutputIndex.getOrPut(outputIndex, ::StringBuilder)
        val isNewPart = partText.isEmpty()
        val outputSeparator =
            if (isNewPart && outputText.isNotEmpty()) {
                "\n\n"
            } else {
                ""
            }
        val emittedSeparator =
            if (isNewPart && emittedText.isNotEmpty()) {
                "\n\n"
            } else {
                ""
            }
        partText.append(text)
        outputText.append(outputSeparator).append(text)
        emittedText.append(emittedSeparator).append(text)
        return emittedSeparator + text
    }

    private fun requireStablePartOutputIndex(
        partKey: String,
        outputIndex: Int,
    ) {
        val previousOutputIndex = outputIndexByPartKey.putIfAbsent(partKey, outputIndex)
        if (previousOutputIndex != null && previousOutputIndex != outputIndex) {
            throw OpenAIResponsesProtocolException(
                "Responses reasoning part $partKey changed output index " +
                    "from $previousOutputIndex to $outputIndex"
            )
        }
    }

    fun acceptTerminalSnapshot(completedText: String): String {
        val missingText =
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = emittedText.toString(),
                terminalText = completedText,
            )
        if (missingText.isNotEmpty()) {
            emittedText.append(missingText)
        }
        return missingText
    }
}

internal object OpenAIResponsesReasoningEventPolicy {
    fun isReasoningLifecycleEvent(
        eventType: String,
        payload: JSONObject,
    ): Boolean {
        return when (eventType) {
            "response.reasoning_summary_part.added",
            "response.reasoning_summary_part.done",
            "response.reasoning_summary_text.delta",
            "response.reasoning_summary_text.done",
            "response.reasoning_text.delta",
            "response.reasoning_text.done",
            -> true

            "response.output_item.added",
            "response.output_item.done",
            -> payload.optJSONObject("item")?.optString("type", "") == "reasoning"

            else -> false
        }
    }

    fun partKey(
        eventType: String,
        payload: JSONObject,
    ): String {
        val outputIndex = payload.optInt("output_index", -1)
        return when {
            eventType.startsWith("response.reasoning_summary") ->
                "summary:$outputIndex:${payload.optInt("summary_index", -1)}"
            eventType.startsWith("response.reasoning_text") ->
                "reasoning:$outputIndex:${payload.optInt("content_index", -1)}"
            else -> "event:$eventType:$outputIndex"
        }
    }
}
