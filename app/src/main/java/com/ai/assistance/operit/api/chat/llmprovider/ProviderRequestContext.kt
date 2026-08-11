package com.ai.assistance.operit.api.chat.llmprovider

import java.util.UUID

/**
 * 消息层分配给一次 provider exchange 的稳定本地身份。
 *
 * provider 不得从全局当前聊天、可见 XML 或进程内单例状态推断这些字段。
 */
data class ProviderRequestContext(
    val localExecutionId: String,
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val hopOrdinal: Int,
) {
    init {
        require(localExecutionId.isNotBlank()) { "localExecutionId must not be blank" }
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(messageTimestamp > 0L) { "messageTimestamp must be positive" }
        require(variantIndex >= 0) { "variantIndex must not be negative" }
        require(hopOrdinal >= 0) { "hopOrdinal must not be negative" }
    }

    fun forHop(targetHopOrdinal: Int): ProviderRequestContext {
        require(targetHopOrdinal >= 0) { "targetHopOrdinal must not be negative" }
        return if (targetHopOrdinal == hopOrdinal) {
            this
        } else {
            copy(
                localExecutionId = newLocalExecutionId(),
                hopOrdinal = targetHopOrdinal,
            )
        }
    }

    companion object {
        fun create(
            chatId: String,
            messageTimestamp: Long,
            variantIndex: Int,
        ): ProviderRequestContext =
            ProviderRequestContext(
                localExecutionId = newLocalExecutionId(),
                chatId = chatId,
                messageTimestamp = messageTimestamp,
                variantIndex = variantIndex,
                hopOrdinal = 0,
            )

        private fun newLocalExecutionId(): String = UUID.randomUUID().toString()
    }
}
