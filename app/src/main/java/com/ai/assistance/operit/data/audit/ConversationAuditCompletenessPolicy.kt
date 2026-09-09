package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus

/**
 * 完整性描述整条已保存链，而不是最后一个事件。
 *
 * 历史缺失、记录中断、来源未验证、密钥不可用或内容损坏一旦成为链事实，后续成功事件不能
 * 把整条链重新标成完整；只有原生完整链允许在 COMPLETE 与 IN_PROGRESS 之间切换。
 */
object ConversationAuditCompletenessPolicy {
    fun merge(
        current: ConversationAuditCompletenessStatus,
        requested: ConversationAuditCompletenessStatus,
        preserveCurrent: Boolean = false,
    ): ConversationAuditCompletenessStatus {
        if (preserveCurrent) return current
        if (
            current != ConversationAuditCompletenessStatus.COMPLETE &&
                current != ConversationAuditCompletenessStatus.IN_PROGRESS
        ) {
            return if (severity(requested) > severity(current)) {
                requested
            } else {
                current
            }
        }
        return requested
    }

    private fun severity(status: ConversationAuditCompletenessStatus): Int =
        when (status) {
            ConversationAuditCompletenessStatus.COMPLETE -> 0
            ConversationAuditCompletenessStatus.IN_PROGRESS -> 1
            ConversationAuditCompletenessStatus.PARTIAL -> 2
            ConversationAuditCompletenessStatus.BASIC -> 3
            ConversationAuditCompletenessStatus.SOURCE_UNVERIFIED -> 4
            ConversationAuditCompletenessStatus.RECORDING_INTERRUPTED -> 5
            ConversationAuditCompletenessStatus.KEY_UNAVAILABLE -> 6
            ConversationAuditCompletenessStatus.CONTENT_CORRUPTED -> 7
        }
}
