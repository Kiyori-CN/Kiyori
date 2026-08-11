package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 一个用户可见消息 variant 对应的 provider-private 输出状态。
 *
 * output items、function call outputs 与 usage 保存为 provider 原生 JSON，供进程重建和多 hop
 * 重放使用；它们不会混入用户可见消息正文。
 */
@Entity(
    tableName = "message_provider_states",
    primaryKeys = ["chatId", "messageTimestamp", "variantIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProviderExecutionEntity::class,
            parentColumns = ["localExecutionId"],
            childColumns = ["latestExecutionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index("latestExecutionId", unique = true),
        Index(value = ["provider", "remoteResponseId"]),
    ],
)
data class MessageProviderStateEntity(
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val latestExecutionId: String,
    val provider: String,
    val modelName: String,
    val remoteResponseId: String?,
    val status: String,
    val lastAppliedSequence: Long = ProviderExecutionEntity.NO_APPLIED_SEQUENCE,
    val terminalEventType: String? = null,
    val outputItemsJson: String = "[]",
    val functionCallOutputsJson: String = "[]",
    val usageJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(messageTimestamp > 0L) { "messageTimestamp must be positive" }
        require(variantIndex >= 0) { "variantIndex must not be negative" }
        require(latestExecutionId.isNotBlank()) { "latestExecutionId must not be blank" }
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(modelName.isNotBlank()) { "modelName must not be blank" }
        require(status.isNotBlank()) { "status must not be blank" }
        require(lastAppliedSequence >= ProviderExecutionEntity.NO_APPLIED_SEQUENCE) {
            "lastAppliedSequence is invalid"
        }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot precede createdAt" }
    }
}
