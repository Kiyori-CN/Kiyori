package com.ai.assistance.operit.api.chat.llmprovider

import java.security.MessageDigest
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

internal enum class LlmRequestSummaryStatus {
    PARSED_JSON,
    INVALID_JSON,
    UNAVAILABLE,
}

/**
 * A content-free summary of the exact request bytes handed to OkHttp.
 *
 * Only structural counters and an irreversible digest are retained. Message content, tool
 * definitions, credentials, headers, query parameters, and response content must never be added
 * to this model.
 */
internal data class LlmRequestLogSummary(
    val bodyBytes: Long,
    val requestDigest: String?,
    val roleCounts: Map<String, Int>,
    val toolCount: Int?,
    val status: LlmRequestSummaryStatus,
) {
    fun format(): String =
        buildString {
            append("bodyBytes=").append(bodyBytes)
            append(", requestDigest=").append(requestDigest ?: "unavailable")
            append(", roleCounts=").append(formatRoleCounts(roleCounts))
            append(", toolCount=").append(toolCount ?: "unknown")
            append(", summaryStatus=").append(status.name)
        }

    private fun formatRoleCounts(counts: Map<String, Int>): String =
        if (counts.isEmpty()) {
            "none"
        } else {
            counts.entries
                .sortedBy(Map.Entry<String, Int>::key)
                .joinToString(separator = "|") { (role, count) -> "$role:$count" }
        }
}

internal data class LlmTextLogSummary(
    val characters: Int,
    val bytes: Int,
    val digest: String,
    val empty: Boolean,
) {
    fun format(): String =
        "characters=$characters, bytes=$bytes, digest=$digest, empty=$empty"
}

internal data class LlmProviderErrorLogSummary(
    val bodyBytes: Int,
    val bodyDigest: String,
    val providerErrorType: String?,
    val providerErrorCode: String?,
    val providerMessage: String?,
) {
    fun format(statusCode: Int? = null): String =
        buildString {
            statusCode?.let { append("status=").append(it).append(", ") }
            append("bodyBytes=").append(bodyBytes)
            append(", bodyDigest=").append(bodyDigest)
            providerErrorType?.let { append(", providerErrorType=").append(it) }
            providerErrorCode?.let { append(", providerErrorCode=").append(it) }
            providerMessage?.let { append(", providerMessage=").append(it) }
        }

    fun exceptionDetail(): String =
        buildString {
            providerMessage?.let { append(it) }
            providerErrorType?.let {
                if (isNotEmpty()) append("; ")
                append("type=").append(it)
            }
            providerErrorCode?.let {
                if (isNotEmpty()) append("; ")
                append("code=").append(it)
            }
            if (isEmpty()) {
                append("provider error body omitted")
            }
        }
}

internal object LlmLogPrivacy {
    private val secretPatterns =
        listOf(
            Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""") to "Bearer <redacted>",
            Regex("""\bsk-[A-Za-z0-9_-]{4,}\b""") to "<redacted>",
            Regex(
                """(?i)\b(authorization|cookie)\b\s*[:=]\s*["']?[^,\s;}"']+(?:\s+[^,\s;}"']+)*"""
            ) to "\$1=<redacted>",
            Regex(
                """(?i)\b(api[_-]?key|token|secret)\b\s*[:=]\s*["']?[^,\s;}"']+"""
            ) to "\$1=<redacted>",
            Regex("""(?i)(https?://[^\s?#]+)\?[^\s#]+""") to "\$1?[omitted]",
        )

    fun summarizeRequestBody(requestBody: RequestBody): LlmRequestLogSummary {
        val declaredLength = runCatching { requestBody.contentLength() }.getOrDefault(-1L)
        val bytes =
            runCatching {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                buffer.readByteArray()
            }.getOrNull()
                ?: return LlmRequestLogSummary(
                    bodyBytes = declaredLength,
                    requestDigest = null,
                    roleCounts = emptyMap(),
                    toolCount = null,
                    status = LlmRequestSummaryStatus.UNAVAILABLE,
                )

        val parsed = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return LlmRequestLogSummary(
                bodyBytes = bytes.size.toLong(),
                requestDigest = digest(bytes),
                roleCounts = emptyMap(),
                toolCount = null,
                status = LlmRequestSummaryStatus.INVALID_JSON,
            )

        return LlmRequestLogSummary(
            bodyBytes = bytes.size.toLong(),
            requestDigest = digest(bytes),
            roleCounts = collectRoleCounts(parsed),
            toolCount = countTools(parsed),
            status = LlmRequestSummaryStatus.PARSED_JSON,
        )
    }

    fun summarizeText(content: CharSequence): LlmTextLogSummary {
        val text = content.toString()
        val bytes = text.toByteArray(Charsets.UTF_8)
        return LlmTextLogSummary(
            characters = text.length,
            bytes = bytes.size,
            digest = digest(bytes),
            empty = text.isBlank(),
        )
    }

    fun summarizeProviderError(responseBody: String): LlmProviderErrorLogSummary {
        val bytes = responseBody.toByteArray(Charsets.UTF_8)
        val parsed = runCatching { JSONObject(responseBody.trim()) }.getOrNull()
        val errorValue = parsed?.opt("error")
        val errorObject = errorValue as? JSONObject
        return LlmProviderErrorLogSummary(
            bodyBytes = bytes.size,
            bodyDigest = digest(bytes),
            providerErrorType =
                sanitizeDiagnostic(
                    errorObject?.nullableString("type") ?: parsed?.nullableString("type"),
                    MAX_ERROR_TYPE_CHARACTERS,
                ),
            providerErrorCode =
                sanitizeDiagnostic(
                    errorObject?.nullableString("code") ?: parsed?.nullableString("code"),
                    MAX_ERROR_CODE_CHARACTERS,
                ),
            providerMessage =
                sanitizeDiagnostic(
                    errorObject?.nullableString("message")
                        ?: parsed?.nullableString("message")
                        ?: (errorValue as? String)
                        ?: responseBody.takeIf { parsed == null },
                    MAX_ERROR_MESSAGE_CHARACTERS,
                ),
        )
    }

    private fun collectRoleCounts(root: JSONObject): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()

        fun addRole(rawRole: String?) {
            val role = rawRole?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return
            counts[role] = counts.getOrDefault(role, 0) + 1
        }

        fun visitRoleArray(array: JSONArray?) {
            if (array == null) return
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                addRole(item.optString("role", ""))
            }
        }

        visitRoleArray(root.optJSONArray("messages"))
        visitRoleArray(root.optJSONArray("input"))
        visitRoleArray(root.optJSONArray("contents"))

        val system = root.opt("system")
        if (system != null && system != JSONObject.NULL) {
            addRole("system")
        }
        val systemInstruction = root.opt("systemInstruction")
        if (systemInstruction != null && systemInstruction != JSONObject.NULL) {
            addRole("system")
        }

        return counts
    }

    private fun countTools(root: JSONObject): Int? {
        val tools = root.optJSONArray("tools") ?: return 0
        var count = 0
        for (index in 0 until tools.length()) {
            val item = tools.optJSONObject(index)
            val functionDeclarations = item?.optJSONArray("functionDeclarations")
            count += functionDeclarations?.length() ?: 1
        }
        return count
    }

    internal fun sanitizeDiagnostic(
        value: String?,
        maxCharacters: Int,
    ): String? {
        var sanitized =
            value
                ?.replace(Regex("""[\u0000-\u001F\u007F]+"""), " ")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null
        secretPatterns.forEach { (pattern, replacement) ->
            sanitized = sanitized.replace(pattern, replacement)
        }
        return sanitized.trim().take(maxCharacters).takeIf(String::isNotEmpty)
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .take(DIGEST_BYTES)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) {
            null
        } else {
            optString(name).trim().takeIf(String::isNotEmpty)
        }

    private const val DIGEST_BYTES = 8
    private const val MAX_ERROR_TYPE_CHARACTERS = 96
    private const val MAX_ERROR_CODE_CHARACTERS = 96
    private const val MAX_ERROR_MESSAGE_CHARACTERS = 384
}
