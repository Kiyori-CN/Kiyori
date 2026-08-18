package com.ai.assistance.operit.data.audit

/** AI 审阅 Markdown 的额外隐私假名化，不改变本机完整审计包中的已脱敏事实。 */
object ConversationAuditExternalShareRedactor {
    private val email =
        Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val windowsPrivatePath =
        Regex(
            "(?i)\\b[A-Z]:\\\\+(?:Users|Documents and Settings)\\\\+[^\\s\"'<>]+"
        )
    private val unixHomePath =
        Regex("(?i)(?<![A-Za-z0-9_])/(?:home|Users)/[^/\\s\"'<>]+/[^\\s\"'<>]*")
    private val androidPrivatePath =
        Regex(
            "(?i)(?<![A-Za-z0-9_])/(?:data/(?:data|user/\\d+)|" +
                "storage/emulated/\\d+/Android/(?:data|media))/[^\\s\"'<>]+"
        )
    private val identityAssignment =
        Regex(
            "(?i)([\"']?(?:username|user_name|userId|user_id|accountId|account_id|" +
                "deviceId|device_id|deviceSerial|device_serial)[\"']?\\s*[:=]\\s*[\"'])" +
                "([^\"'\\r\\n]+)([\"'])"
        )

    fun redact(
        value: String,
        mediaType: String = "text/plain",
    ): ConversationAuditExternalShareRedactionResult {
        val credentialRedaction =
            ConversationAuditRedactor.redactText(
                value = value,
                mediaType = mediaType,
            )
        var replacementCount = credentialRedaction.replacementCount
        var redacted =
            identityAssignment.replace(credentialRedaction.value) { match ->
                replacementCount++
                match.groupValues[1] +
                    pseudonym("IDENTITY", match.groupValues[2]) +
                    match.groupValues[3]
            }
        redacted =
            email.replace(redacted) { match ->
                replacementCount++
                pseudonym("ACCOUNT", match.value)
            }
        redacted =
            windowsPrivatePath.replace(redacted) { match ->
                replacementCount++
                pseudonym("PRIVATE_PATH", match.value)
            }
        redacted =
            unixHomePath.replace(redacted) { match ->
                replacementCount++
                pseudonym("PRIVATE_PATH", match.value)
            }
        redacted =
            androidPrivatePath.replace(redacted) { match ->
                replacementCount++
                pseudonym("PRIVATE_PATH", match.value)
            }
        return ConversationAuditExternalShareRedactionResult(
            value = redacted,
            replacementCount = replacementCount,
        )
    }

    private fun pseudonym(
        kind: String,
        value: String,
    ): String = "[$kind:${ConversationAuditHasher.sha256(value).take(12)}]"
}

data class ConversationAuditExternalShareRedactionResult(
    val value: String,
    val replacementCount: Int,
)
