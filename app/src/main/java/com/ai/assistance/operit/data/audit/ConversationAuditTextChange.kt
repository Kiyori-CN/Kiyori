package com.ai.assistance.operit.data.audit

/**
 * 根据前后两个可见快照确定流式审计应保存增量还是完整修订。
 *
 * 仅比较长度会漏掉“前文被改写但总长度不变或继续增长”的 Provider 修订；只有新快照确实
 * 以前一快照为前缀时，新增后缀才是可独立复放的文本增量。
 */
internal fun resolveConversationAuditTextChange(
    previous: String,
    current: String,
): ConversationAuditTextChange? {
    if (current == previous) {
        return null
    }
    return if (current.startsWith(previous)) {
        ConversationAuditTextChange(
            eventType = "PROVIDER_TEXT_DELTA",
            payloadLabel = "text_delta",
            value = current.substring(previous.length),
            isRevision = false,
        )
    } else {
        ConversationAuditTextChange(
            eventType = "PROVIDER_TEXT_REVISION",
            payloadLabel = "text_revision",
            value = current,
            isRevision = true,
        )
    }
}

internal data class ConversationAuditTextChange(
    val eventType: String,
    val payloadLabel: String,
    val value: String,
    val isRevision: Boolean,
)
