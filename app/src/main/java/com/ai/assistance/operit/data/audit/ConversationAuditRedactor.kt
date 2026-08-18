package com.ai.assistance.operit.data.audit

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 凭据在进入加密 payload 之前必须经过的唯一脱敏器。 */
object ConversationAuditRedactor {
    private val credentialKey =
        Regex(
            "(?i)^(?:x[-_])?(authorization|proxy-authorization|cookie|set-cookie|password|passwd|" +
                "api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|" +
                "auth[_-]?token|private[_-]?key|request[_-]?signature|signature|token)$"
        )
    private val assignment =
        Regex(
            "(?i)\\b((?:(?:x|proxy)[-_])?(?:authorization|cookie|set[-_]?cookie|" +
                "password|passwd|api[_-]?key|" +
                "access[_-]?token|refresh[_-]?token|auth[_-]?token|client[_-]?secret|" +
                "private[_-]?key|request[_-]?signature))" +
                "[\"']?\\s*([:=])\\s*[\"']?((?:(?:Bearer|Basic)\\s+)?[^\\s,;\"'}]+)"
        )
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+")
    private val basicAuthorization = Regex("(?i)\\bBasic\\s+[A-Za-z0-9+/=]+")
    private val privateKeyBlock =
        Regex(
            "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----[\\s\\S]*?" +
                "-----END(?: [A-Z0-9]+)? PRIVATE KEY-----",
            RegexOption.IGNORE_CASE,
        )
    private val basicAuthUrl =
        Regex("(?i)\\b(https?://)[^/@\\s:]+:[^/@\\s]+@")
    private val credentialUrlParameter =
        Regex(
            "(?i)([?&](?:(?:x[-_])?authorization|cookie|password|passwd|api[_-]?key|" +
                "access[_-]?token|refresh[_-]?token|auth[_-]?token|client[_-]?secret|" +
                "private[_-]?key|request[_-]?signature|signature)=)[^&#\\s]+"
        )

    fun redactText(
        value: String,
        mediaType: String = "text/plain",
    ): ConversationAuditRedactionResult =
        if (mediaType.equals("application/json", ignoreCase = true)) {
            redactJson(value)
        } else {
            redactTextWithoutJson(value)
        }

    private fun redactJson(value: String): ConversationAuditRedactionResult {
        val parsed = JSONTokener(value).nextValue()
        if (parsed !is JSONObject && parsed !is JSONArray) {
            throw IllegalArgumentException("Conversation audit JSON payload has an invalid root")
        }
        var replacements = 0

        fun redactNode(node: Any?): Any? =
            when (node) {
                is JSONObject -> {
                    val keys = node.keys().asSequence().toList()
                    for (key in keys) {
                        val current = node.opt(key)
                        if (credentialKey.matches(key)) {
                            node.put(key, "[REDACTED:credential]")
                            replacements++
                        } else {
                            node.put(key, redactNode(current))
                        }
                    }
                    node
                }
                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        node.put(index, redactNode(node.opt(index)))
                    }
                    node
                }
                is String -> {
                    val result = redactTextWithoutJson(node)
                    replacements += result.replacementCount
                    result.value
                }
                else -> node
            }

        return ConversationAuditRedactionResult(
            value = redactNode(parsed).toString(),
            replacementCount = replacements,
        )
    }

    private fun redactTextWithoutJson(value: String): ConversationAuditRedactionResult {
        var replacements = 0
        var redacted =
            assignment.replace(value) { match ->
                replacements++
                "${match.groupValues[1]}${match.groupValues[2]}[REDACTED:credential]"
            }
        redacted =
            bearer.replace(redacted) {
                replacements++
                "Bearer [REDACTED:credential]"
            }
        redacted =
            basicAuthorization.replace(redacted) {
                replacements++
                "Basic [REDACTED:credential]"
            }
        redacted =
            privateKeyBlock.replace(redacted) {
                replacements++
                "[REDACTED:private-key]"
            }
        redacted =
            basicAuthUrl.replace(redacted) { match ->
                replacements++
                "${match.groupValues[1]}[REDACTED:credential]@"
            }
        return ConversationAuditRedactionResult(
            value =
                credentialUrlParameter.replace(redacted) { match ->
                    replacements++
                    "${match.groupValues[1]}%5BREDACTED:credential%5D"
                },
            replacementCount = replacements,
        )
    }
}

data class ConversationAuditRedactionResult(
    val value: String,
    val replacementCount: Int,
)
