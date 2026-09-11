package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.ChatCallTurnInfo
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.util.ChatMarkupRegex
import java.util.Locale
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Tools.Chat.call 的无副作用输入/输出协议；与服务租约、计费和聊天 UI 生命周期分离。 */
internal object ChatCallCodec {
        private val metaProviderAttrRegex =
            Regex("""\bprovider\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val metaBodyRegex =
            Regex(
                """<meta\b[^>]*>([\s\S]*?)</meta>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        private val toolNameAttrRegex =
            Regex("""\bname\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    data class ChatCallOutputParts(
        val text: String,
        val turns: List<ChatCallTurnInfo>,
        val finishReason: String,
        val metadata: Map<String, JsonElement>
    )

    private data class ChatCallToolXmlMatch(
        val start: Int,
        val endExclusive: Int,
        val content: String,
        val toolName: String?
    )

    fun parseChatCallBoolean(
        value: String?,
        parameterName: String,
        defaultValue: Boolean
    ): Boolean {
        val normalized = value?.trim()?.lowercase(Locale.ROOT)
        return when (normalized) {
            null, "" -> defaultValue
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> throw IllegalArgumentException("$parameterName must be true/false")
        }
    }

    fun parseChatCallFunctionType(value: String?): FunctionType {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("functionType is required")
        }
        return FunctionType.values().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid functionType: $normalized")
    }

    fun parseChatCallPromptTurns(value: String?): List<PromptTurn> {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) {
            throw IllegalArgumentException("turns is required")
        }

        val tokener = JSONTokener(raw)
        val decoded = tokener.nextValue()
        require(tokener.nextClean() == '\u0000') { "turns contains trailing JSON content" }
        if (decoded !is JSONArray) {
            throw IllegalArgumentException("turns must be a JSON array")
        }
        if (decoded.length() == 0) {
            throw IllegalArgumentException("turns must contain at least one PromptTurn")
        }

        return buildList {
            for (index in 0 until decoded.length()) {
                val item = decoded.opt(index)
                if (item !is JSONObject) {
                    throw IllegalArgumentException("turns[$index] must be an object")
                }

                val kindRaw = item.opt("kind")
                if (kindRaw !is String || kindRaw.isBlank()) {
                    throw IllegalArgumentException("turns[$index].kind is required")
                }
                val kind =
                    PromptTurnKind.values().firstOrNull {
                        it.name.equals(kindRaw.trim(), ignoreCase = true)
                    } ?: throw IllegalArgumentException("Invalid turns[$index].kind: $kindRaw")

                val contentRaw = item.opt("content")
                if (contentRaw !is String) {
                    throw IllegalArgumentException("turns[$index].content must be a string")
                }

                val toolNameRaw = item.opt("toolName")
                val toolName =
                    when (toolNameRaw) {
                        null, JSONObject.NULL -> null
                        is String -> toolNameRaw.trim().takeIf { it.isNotEmpty() }
                        else -> throw IllegalArgumentException("turns[$index].toolName must be a string")
                    }

                val metadataRaw = item.opt("metadata")
                val metadata =
                    when (metadataRaw) {
                        null, JSONObject.NULL -> emptyMap()
                        is JSONObject -> jsonObjectToPlainMap(metadataRaw)
                        else -> throw IllegalArgumentException("turns[$index].metadata must be an object")
                    }

                add(
                    PromptTurn(
                        kind = kind,
                        content = contentRaw,
                        toolName = toolName,
                        metadata = metadata
                    )
                )
            }
        }
    }

    private fun jsonObjectToPlainMap(raw: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValueToPlainValue(raw.opt(key))
        }
        return result
    }

    private fun jsonArrayToPlainList(raw: JSONArray): List<Any?> {
        return buildList {
            for (index in 0 until raw.length()) {
                add(jsonValueToPlainValue(raw.opt(index)))
            }
        }
    }

    private fun jsonValueToPlainValue(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> jsonObjectToPlainMap(value)
            is JSONArray -> jsonArrayToPlainList(value)
            else -> value
        }
    }

    private fun jsonValueToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null, JSONObject.NULL -> JsonNull
            is JSONObject -> JsonObject(jsonObjectToJsonElementMap(value))
            is JSONArray -> JsonArray(jsonArrayToJsonElementList(value))
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun jsonObjectToJsonElementMap(raw: JSONObject): Map<String, JsonElement> {
        val result = linkedMapOf<String, JsonElement>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValueToJsonElement(raw.opt(key))
        }
        return result
    }

    private fun jsonArrayToJsonElementList(raw: JSONArray): List<JsonElement> {
        return buildList {
            for (index in 0 until raw.length()) {
                add(jsonValueToJsonElement(raw.opt(index)))
            }
        }
    }

    private fun extractMetaProvider(tagContent: String): String? {
        return metaProviderAttrRegex
            .find(tagContent.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun extractMetaBody(tagContent: String): String {
        return metaBodyRegex
            .find(tagContent)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    }

    private fun extractChatCallProtocolMetadata(content: String): Map<String, JsonElement> {
        val protocolMeta =
            ChatMarkupRegex.metaTag.findAll(content).mapNotNull { match ->
                val provider = extractMetaProvider(match.value) ?: return@mapNotNull null
                JsonObject(
                    mapOf(
                        "provider" to JsonPrimitive(provider),
                        "payload" to JsonPrimitive(extractMetaBody(match.value))
                    )
                )
            }.toList()

        return if (protocolMeta.isEmpty()) {
            emptyMap()
        } else {
            mapOf("protocolMeta" to JsonArray(protocolMeta))
        }
    }

    private fun stripChatCallProtocolMetadata(content: String): String {
        return ChatMarkupRegex.metaTag.replace(content) { match ->
            if (extractMetaProvider(match.value) == null) {
                match.value
            } else {
                ""
            }
        }.trim()
    }

    private fun extractToolName(tagContent: String): String? {
        return toolNameAttrRegex
            .find(tagContent.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun findChatCallToolXmlMatches(content: String): List<ChatCallToolXmlMatch> {
        val matches = mutableListOf<ChatCallToolXmlMatch>()

        ChatMarkupRegex.toolCallPattern.findAll(content).forEach { match ->
            matches.add(
                ChatCallToolXmlMatch(
                    start = match.range.first,
                    endExclusive = match.range.last + 1,
                    content = match.value.trim(),
                    toolName = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }

        ChatMarkupRegex.toolSelfClosingTag.findAll(content).forEach { match ->
            matches.add(
                ChatCallToolXmlMatch(
                    start = match.range.first,
                    endExclusive = match.range.last + 1,
                    content = match.value.trim(),
                    toolName = extractToolName(match.value)
                )
            )
        }

        return matches.sortedBy { it.start }
    }

    fun parseChatCallOutput(rawContent: String): ChatCallOutputParts {
        val metadata = extractChatCallProtocolMetadata(rawContent)
        val content = stripChatCallProtocolMetadata(rawContent)
        val turns = mutableListOf<ChatCallTurnInfo>()
        var cursor = 0

        fun appendAssistantSegment(segment: String) {
            val text = segment.trim()
            if (text.isNotEmpty()) {
                turns.add(
                    ChatCallTurnInfo(
                        kind = PromptTurnKind.ASSISTANT.name,
                        content = text
                    )
                )
            }
        }

        findChatCallToolXmlMatches(content).forEach { match ->
            // 工具参数中可能包含另一个示例 tool 标签；内层不能被重复识别为独立调用。
            if (match.start < cursor) return@forEach
            if (match.start > cursor) {
                appendAssistantSegment(content.substring(cursor, match.start))
            }
            turns.add(
                ChatCallTurnInfo(
                    kind = PromptTurnKind.TOOL_CALL.name,
                    content = match.content,
                    toolName = match.toolName
                )
            )
            cursor = match.endExclusive
        }

        if (cursor < content.length) {
            appendAssistantSegment(content.substring(cursor))
        }

        val text =
            ChatMarkupRegex.toolSelfClosingTag
                .replace(ChatMarkupRegex.toolTag.replace(content, ""), "")
                .trim()
        val finishReason =
            if (turns.any { it.kind == PromptTurnKind.TOOL_CALL.name }) {
                "tool_call"
            } else {
                "stop"
            }

        return ChatCallOutputParts(
            text = text,
            turns = turns,
            finishReason = finishReason,
            metadata = metadata
        )
    }

}
