package com.ai.assistance.operit.data.audit

import java.security.MessageDigest

object ConversationAuditHasher {
    val EMPTY_CHAIN_SHA256 = "0".repeat(64)

    fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    fun eventHash(input: ConversationAuditHashInput): String {
        val canonical =
            buildString {
                appendField(input.previousEventSha256)
                appendField(input.eventId)
                appendField(input.chatId)
                appendField(input.sequenceNumber.toString())
                appendField(input.occurredAt.toString())
                appendField(input.recordedAt.toString())
                appendField(input.category)
                appendField(input.eventType)
                appendField(input.actor)
                appendField(input.summary)
                appendField(input.messageTimestamp?.toString().orEmpty())
                appendField(input.variantIndex?.toString().orEmpty())
                appendField(input.localExecutionId.orEmpty())
                appendField(input.providerCallId.orEmpty())
                appendField(input.parentEventId.orEmpty())
                appendField(input.sourceChatId.orEmpty())
                appendField(input.sourceEventId.orEmpty())
                appendField(input.visibility)
                appendField(input.terminalState.orEmpty())
                input.payloadRefs
                    .sortedWith(
                        compareBy<ConversationAuditHashPayloadRef> { it.ordinal }
                            .thenBy { it.label }
                            .thenBy { it.payloadSha256 }
                    )
                    .forEach { ref ->
                        appendField(ref.label)
                        appendField(ref.ordinal.toString())
                        appendField(ref.role)
                        appendField(ref.payloadSha256)
                        appendField(ref.mediaType)
                        appendField(ref.encoding)
                        appendField(ref.plainByteCount.toString())
                    }
            }
        return sha256(canonical)
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.toByteArray(Charsets.UTF_8).size)
        append(':')
        append(value)
        append('\n')
    }
}

data class ConversationAuditHashInput(
    val previousEventSha256: String,
    val eventId: String,
    val chatId: String,
    val sequenceNumber: Long,
    val occurredAt: Long,
    val recordedAt: Long,
    val category: String,
    val eventType: String,
    val actor: String,
    val summary: String,
    val messageTimestamp: Long?,
    val variantIndex: Int?,
    val localExecutionId: String?,
    val providerCallId: String?,
    val parentEventId: String?,
    val sourceChatId: String?,
    val sourceEventId: String?,
    val visibility: String,
    val terminalState: String?,
    val payloadRefs: List<ConversationAuditHashPayloadRef>,
)

data class ConversationAuditHashPayloadRef(
    val label: String,
    val ordinal: Int,
    val role: String,
    val payloadSha256: String,
    val mediaType: String,
    val encoding: String,
    val plainByteCount: Long,
)
