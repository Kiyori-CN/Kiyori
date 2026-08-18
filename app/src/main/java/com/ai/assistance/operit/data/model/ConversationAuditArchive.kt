package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * 普通聊天归档 v3 携带的完整审计数据。
 *
 * payload 保存已经脱敏的明文字节 Base64；导入时必须重新验证 SHA-256，并使用目标设备
 * Android Keystore 重新加密。相对路径、nonce 和本机 key alias 不属于可移植合同。
 */
@Serializable
data class OperitArchivedConversationAudit(
    val schemaVersion: Int,
    val audit: OperitArchivedConversationAuditRoot,
    val events: List<OperitArchivedConversationAuditEvent>,
    val eventPayloads: List<OperitArchivedConversationAuditEventPayload>,
    val revisions: List<OperitArchivedConversationMessageRevision>,
    val projections: List<OperitArchivedConversationMessageProjection>,
    val seals: List<OperitArchivedConversationAuditSeal>,
    val payloads: List<OperitArchivedConversationAuditPayload>,
)

@Serializable
data class OperitArchivedConversationAuditRoot(
    val chatId: String,
    val schemaVersion: Int,
    val completenessStatus: String,
    val eventCount: Long,
    val lastSequenceNumber: Long,
    val chainHeadSha256: String,
    val latestSealSequenceNumber: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val lastFailureCode: String? = null,
    val legacyReconstructionLevel: String? = null,
)

@Serializable
data class OperitArchivedConversationAuditEvent(
    val eventId: String,
    val chatId: String,
    val sequenceNumber: Long,
    val occurredAt: Long,
    val recordedAt: Long,
    val category: String,
    val eventType: String,
    val actor: String,
    val summary: String,
    val messageTimestamp: Long? = null,
    val variantIndex: Int? = null,
    val localExecutionId: String? = null,
    val providerCallId: String? = null,
    val parentEventId: String? = null,
    val sourceChatId: String? = null,
    val sourceEventId: String? = null,
    val previousEventSha256: String,
    val eventSha256: String,
    val visibility: String,
    val terminalState: String? = null,
)

@Serializable
data class OperitArchivedConversationAuditEventPayload(
    val eventId: String,
    val payloadSha256: String,
    val label: String,
    val ordinal: Int,
    val role: String,
)

@Serializable
data class OperitArchivedConversationAuditPayload(
    val payloadSha256: String,
    val plainByteCount: Long,
    val mediaType: String,
    val encoding: String,
    val createdAt: Long,
    val bytesBase64: String,
)

@Serializable
data class OperitArchivedConversationMessageRevision(
    val revisionId: String,
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val revisionNumber: Int,
    val sender: String,
    val contentPayloadSha256: String,
    val previousRevisionId: String? = null,
    val auditEventId: String,
    val source: String,
    val createdAt: Long,
)

@Serializable
data class OperitArchivedConversationMessageProjection(
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val currentRevisionId: String,
    val estimatedTokenCount: Int,
    val updatedAt: Long,
)

@Serializable
data class OperitArchivedConversationAuditSeal(
    val sealId: String,
    val chatId: String,
    val sequenceNumber: Long,
    val rootSha256: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
    val publicKeyBase64: String,
    val reason: String,
    val createdAt: Long,
)
