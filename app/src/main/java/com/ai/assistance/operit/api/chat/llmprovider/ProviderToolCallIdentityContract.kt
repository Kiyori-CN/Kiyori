package com.ai.assistance.operit.api.chat.llmprovider

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.util.Locale

internal data class ProviderToolCallKey(
    val providerName: String,
    val callId: String,
)

internal data class ProviderToolCallSignature(
    val toolName: String,
    val canonicalArguments: String,
)

internal class ProviderToolCallIdentityConflictException(
    key: ProviderToolCallKey,
) : IllegalStateException(
    "Provider tool call identity conflict for ${key.providerName.ifEmpty { "UNKNOWN" }}/${key.callId}"
)

/**
 * Provider 原生工具调用的稳定身份合同。
 *
 * XML 标签名和数组位置只用于本地流式投影；同一 Provider 回合中的真实身份由原始 call_id
 * 决定。相同身份只能对应同一个工具名和同一组参数，否则必须在执行副作用前显式失败。
 */
internal object ProviderToolCallIdentityContract {
    fun key(
        providerName: String?,
        callId: String?,
    ): ProviderToolCallKey? {
        val normalizedCallId = callId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ProviderToolCallKey(
            providerName = providerName?.trim()?.uppercase(Locale.ROOT).orEmpty(),
            callId = normalizedCallId,
        )
    }

    fun signature(
        toolName: String,
        argumentsJson: String,
    ): ProviderToolCallSignature =
        ProviderToolCallSignature(
            toolName = toolName.trim(),
            canonicalArguments = canonicalJsonText(argumentsJson.ifBlank { "{}" }),
        )

    fun requireSame(
        key: ProviderToolCallKey,
        existing: ProviderToolCallSignature,
        incoming: ProviderToolCallSignature,
    ) {
        if (existing != incoming) {
            throw ProviderToolCallIdentityConflictException(key)
        }
    }

    fun canonicalJsonText(json: String): String {
        val trimmed = json.trim()
        val normalized = if (trimmed.isEmpty()) "{}" else trimmed
        return canonicalJsonValue(JsonParser.parseString(normalized))
    }

    private fun canonicalJsonValue(value: JsonElement): String =
        when {
            value.isJsonNull -> "null"
            value.isJsonObject -> {
                val entries = value.asJsonObject.entrySet().sortedBy { it.key }
                entries.joinToString(separator = ",", prefix = "{", postfix = "}") { entry ->
                    "${JsonPrimitive(entry.key)}:${canonicalJsonValue(entry.value)}"
                }
            }

            value.isJsonArray ->
                value.asJsonArray.joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
                    canonicalJsonValue(item)
                }

            else -> value.toString()
        }
}
