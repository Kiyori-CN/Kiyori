package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次本地 provider exchange 的持久化身份。
 *
 * UI 消息正文不承担远端 response ID、续流游标或终态的所有权；这些协议状态只由本实体推进。
 */
@Entity(
    tableName = "provider_executions",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index(value = ["chatId", "messageTimestamp", "variantIndex"]),
        Index(value = ["provider", "remoteResponseId"], unique = true),
        Index(value = ["status", "updatedAt"]),
    ],
)
data class ProviderExecutionEntity(
    @PrimaryKey val localExecutionId: String,
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val hopOrdinal: Int,
    val provider: String,
    val modelName: String,
    val transportKind: String,
    val requestFingerprint: String,
    val status: String,
    val remoteResponseId: String? = null,
    val lastAppliedSequence: Long = NO_APPLIED_SEQUENCE,
    val terminalEventType: String? = null,
    val resumeCount: Int = 0,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
) {
    init {
        require(localExecutionId.isNotBlank()) { "localExecutionId must not be blank" }
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(messageTimestamp > 0L) { "messageTimestamp must be positive" }
        require(variantIndex >= 0) { "variantIndex must not be negative" }
        require(hopOrdinal >= 0) { "hopOrdinal must not be negative" }
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(modelName.isNotBlank()) { "modelName must not be blank" }
        require(transportKind.isNotBlank()) { "transportKind must not be blank" }
        require(requestFingerprint.isNotBlank()) { "requestFingerprint must not be blank" }
        require(status.isNotBlank()) { "status must not be blank" }
        require(lastAppliedSequence >= NO_APPLIED_SEQUENCE) {
            "lastAppliedSequence cannot be lower than $NO_APPLIED_SEQUENCE"
        }
        require(resumeCount >= 0) { "resumeCount must not be negative" }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot precede createdAt" }
        require(completedAt == null || completedAt >= createdAt) {
            "completedAt cannot precede createdAt"
        }
    }

    companion object {
        const val NO_APPLIED_SEQUENCE = -1L
        const val FIRST_EVENT_SEQUENCE = 1L

        /**
         * OpenAI Responses 的 sequence_number 从 1 开始。
         *
         * 不能把尚未应用事件的 -1 哨兵直接加一，否则首个 response.created 会被误判为
         * sequence gap，且失败发生在任何可见正文到达之前。
         */
        fun expectedNextSequence(lastAppliedSequence: Long): Long {
            require(lastAppliedSequence >= NO_APPLIED_SEQUENCE) {
                "lastAppliedSequence cannot be lower than $NO_APPLIED_SEQUENCE"
            }
            return if (lastAppliedSequence == NO_APPLIED_SEQUENCE) {
                FIRST_EVENT_SEQUENCE
            } else {
                Math.addExact(lastAppliedSequence, 1L)
            }
        }
    }
}

enum class ProviderExecutionStatus {
    COMPILING,
    SUBMITTING,
    SUBMISSION_UNKNOWN,
    QUEUED,
    IN_PROGRESS,
    DISCONNECTED,
    RESUMING,
    WAITING_TOOL,
    SUBMITTING_TOOL_OUTPUT,
    COMPLETED,
    FAILED,
    INCOMPLETE,
    CANCELLING,
    CANCELLED,
    EXPIRED,
}

enum class ProviderTransportKind {
    RESPONSES,
    CHAT_COMPLETIONS,
    PROVIDER_NATIVE,
    LOCAL,
}
