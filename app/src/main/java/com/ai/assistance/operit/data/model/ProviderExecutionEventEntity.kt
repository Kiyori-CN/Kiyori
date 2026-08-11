package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 已确认应用的 provider 事件账本。 */
@Entity(
    tableName = "provider_execution_events",
    foreignKeys = [
        ForeignKey(
            entity = ProviderExecutionEntity::class,
            parentColumns = ["localExecutionId"],
            childColumns = ["localExecutionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("localExecutionId"),
        Index(value = ["localExecutionId", "sequenceNumber"], unique = true),
        Index(value = ["remoteResponseId", "sequenceNumber"], unique = true),
    ],
)
data class ProviderExecutionEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0L,
    val localExecutionId: String,
    val remoteResponseId: String,
    val sequenceNumber: Long,
    val eventType: String,
    val payloadJson: String,
    val payloadSha256: String,
    val receivedAt: Long,
) {
    init {
        require(localExecutionId.isNotBlank()) { "localExecutionId must not be blank" }
        require(remoteResponseId.isNotBlank()) { "remoteResponseId must not be blank" }
        require(sequenceNumber >= 0L) { "sequenceNumber must not be negative" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(payloadJson.isNotBlank()) { "payloadJson must not be blank" }
        require(SHA256_PATTERN.matches(payloadSha256)) { "payloadSha256 must be lowercase SHA-256" }
        require(receivedAt > 0L) { "receivedAt must be positive" }
    }

    companion object {
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
