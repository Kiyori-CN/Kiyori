package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.ConversationAuditEventEntity

data class ConversationAuditSearchMatch(
    val event: ConversationAuditEventEntity,
    val excerpt: String,
)

data class ConversationAuditSearchPage(
    val matches: List<ConversationAuditSearchMatch>,
    val scannedCount: Int,
    val nextBeforeSequence: Long?,
)

/** 字面量多词 AND 搜索；只消费已脱敏事实，不建立额外的明文索引。 */
internal class ConversationAuditSearchQuery(query: String) {
    private val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank).distinct()

    fun match(fields: List<String>): String? {
        if (terms.isEmpty() || !terms.all { term -> fields.any { it.contains(term, ignoreCase = true) } }) {
            return null
        }
        val field = fields.lastOrNull { value -> terms.any { value.contains(it, ignoreCase = true) } }.orEmpty()
        val position = terms.map { field.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (position - 60).coerceAtLeast(0)
        val end = (start + 240).coerceAtMost(field.length)
        return (if (start > 0) "…" else "") +
            field.substring(start, end).replace(Regex("\\s+"), " ") +
            (if (end < field.length) "…" else "")
    }
}

internal fun ConversationAuditEventEntity.searchFields(): List<String> = listOfNotNull(
    sequenceNumber.toString(), eventId, category, eventType, actor, summary,
    localExecutionId, providerCallId, parentEventId, terminalState, visibility,
    messageTimestamp?.toString(), variantIndex?.toString(), sourceChatId, sourceEventId,
    eventSha256, previousEventSha256,
)
