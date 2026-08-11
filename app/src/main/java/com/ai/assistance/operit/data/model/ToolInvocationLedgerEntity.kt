package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Responses 原生 function call 的 exactly-once 执行账本。 */
@Entity(
    tableName = "tool_invocation_ledger",
    primaryKeys = ["provider", "remoteResponseId", "callId"],
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
        Index(value = ["status", "updatedAt"]),
    ],
)
data class ToolInvocationLedgerEntity(
    val provider: String,
    val remoteResponseId: String,
    val callId: String,
    val localExecutionId: String,
    val toolName: String,
    val argumentsJson: String,
    val argumentsSha256: String,
    val status: String,
    val resultJson: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
) {
    init {
        require(provider.isNotBlank()) { "provider must not be blank" }
        require(remoteResponseId.isNotBlank()) { "remoteResponseId must not be blank" }
        require(callId.isNotBlank()) { "callId must not be blank" }
        require(localExecutionId.isNotBlank()) { "localExecutionId must not be blank" }
        require(toolName.isNotBlank()) { "toolName must not be blank" }
        require(argumentsJson.isNotBlank()) { "argumentsJson must not be blank" }
        require(SHA256_PATTERN.matches(argumentsSha256)) {
            "argumentsSha256 must be lowercase SHA-256"
        }
        require(status.isNotBlank()) { "status must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot precede createdAt" }
        require(startedAt == null || startedAt >= createdAt) { "startedAt cannot precede createdAt" }
        require(completedAt == null || completedAt >= createdAt) {
            "completedAt cannot precede createdAt"
        }
    }

    companion object {
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

enum class ToolInvocationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}
