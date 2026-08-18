package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** 一条已保存聊天对应的审计根状态。 */
@Entity(
    tableName = "conversation_audits",
    primaryKeys = ["chatId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("completenessStatus"), Index("updatedAt")],
)
data class ConversationAuditEntity(
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
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(completenessStatus.isNotBlank()) { "completenessStatus must not be blank" }
        require(eventCount >= 0L) { "eventCount must not be negative" }
        require(lastSequenceNumber >= 0L) { "lastSequenceNumber must not be negative" }
        require(
            chainHeadSha256.isEmpty() || LOWERCASE_SHA256.matches(chainHeadSha256)
        ) {
            "chainHeadSha256 must be empty or lowercase SHA-256"
        }
        require(latestSealSequenceNumber in 0L..lastSequenceNumber) {
            "latestSealSequenceNumber must be within the current event range"
        }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot precede createdAt" }
    }
}

/** 不可覆盖的对话审计事件。正文通过 payload 引用保存。 */
@Entity(
    tableName = "conversation_audit_events",
    primaryKeys = ["eventId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId", "sequenceNumber"], unique = true),
        Index(value = ["chatId", "category", "sequenceNumber"]),
        Index(value = ["chatId", "messageTimestamp", "variantIndex"]),
        Index("localExecutionId"),
        Index("providerCallId"),
        Index("parentEventId"),
        Index(value = ["sourceChatId", "sourceEventId"]),
    ],
)
data class ConversationAuditEventEntity(
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
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(sequenceNumber > 0L) { "sequenceNumber must be positive" }
        require(occurredAt > 0L) { "occurredAt must be positive" }
        require(recordedAt >= occurredAt) { "recordedAt cannot precede occurredAt" }
        require(category.isNotBlank()) { "category must not be blank" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(actor.isNotBlank()) { "actor must not be blank" }
        require(summary.isNotBlank()) { "summary must not be blank" }
        require(messageTimestamp == null || messageTimestamp > 0L) {
            "messageTimestamp must be positive"
        }
        require(variantIndex == null || variantIndex >= 0) { "variantIndex must not be negative" }
        require(LOWERCASE_SHA256.matches(previousEventSha256)) {
            "previousEventSha256 must be lowercase SHA-256"
        }
        require(LOWERCASE_SHA256.matches(eventSha256)) {
            "eventSha256 must be lowercase SHA-256"
        }
        require(visibility.isNotBlank()) { "visibility must not be blank" }
    }
}

/** 加密、内容寻址的审计正文或附件元数据。 */
@Entity(tableName = "conversation_audit_payloads")
data class ConversationAuditPayloadEntity(
    @androidx.room.PrimaryKey val payloadSha256: String,
    val relativePath: String,
    val plainByteCount: Long,
    val storedByteCount: Long,
    val mediaType: String,
    val encoding: String,
    val compression: String,
    val encryptionAlgorithm: String,
    val keyAlias: String,
    val nonceBase64: String,
    val createdAt: Long,
) {
    init {
        require(LOWERCASE_SHA256.matches(payloadSha256)) {
            "payloadSha256 must be lowercase SHA-256"
        }
        require(relativePath.isNotBlank()) { "relativePath must not be blank" }
        require(plainByteCount >= 0L) { "plainByteCount must not be negative" }
        require(storedByteCount > 0L) { "storedByteCount must be positive" }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
        require(encoding.isNotBlank()) { "encoding must not be blank" }
        require(compression.isNotBlank()) { "compression must not be blank" }
        require(encryptionAlgorithm.isNotBlank()) { "encryptionAlgorithm must not be blank" }
        require(keyAlias.isNotBlank()) { "keyAlias must not be blank" }
        require(nonceBase64.isNotBlank()) { "nonceBase64 must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
    }
}

/** 一个事件可以关联多个正文、附件、异常或差异 payload。 */
@Entity(
    tableName = "conversation_audit_event_payloads",
    primaryKeys = ["eventId", "label", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationAuditEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationAuditPayloadEntity::class,
            parentColumns = ["payloadSha256"],
            childColumns = ["payloadSha256"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("eventId"), Index("payloadSha256")],
)
data class ConversationAuditEventPayloadEntity(
    val eventId: String,
    val payloadSha256: String,
    val label: String,
    val ordinal: Int,
    val role: String,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(LOWERCASE_SHA256.matches(payloadSha256)) {
            "payloadSha256 must be lowercase SHA-256"
        }
        require(label.isNotBlank()) { "label must not be blank" }
        require(ordinal >= 0) { "ordinal must not be negative" }
        require(role.isNotBlank()) { "role must not be blank" }
    }
}

/** 用户或 AI 可见消息的不可覆盖修订历史。 */
@Entity(
    tableName = "conversation_message_revisions",
    primaryKeys = ["revisionId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationAuditEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["auditEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationAuditPayloadEntity::class,
            parentColumns = ["payloadSha256"],
            childColumns = ["contentPayloadSha256"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["chatId", "messageTimestamp", "variantIndex", "revisionNumber"], unique = true),
        Index("auditEventId"),
        Index("contentPayloadSha256"),
    ],
)
data class ConversationMessageRevisionEntity(
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
) {
    init {
        require(revisionId.isNotBlank()) { "revisionId must not be blank" }
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(messageTimestamp > 0L) { "messageTimestamp must be positive" }
        require(variantIndex >= 0) { "variantIndex must not be negative" }
        require(revisionNumber >= 0) { "revisionNumber must not be negative" }
        require(sender.isNotBlank()) { "sender must not be blank" }
        require(LOWERCASE_SHA256.matches(contentPayloadSha256)) {
            "contentPayloadSha256 must be lowercase SHA-256"
        }
        require(auditEventId.isNotBlank()) { "auditEventId must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
    }
}

/** 当前聊天气泡使用的修订指针与估算 Token。 */
@Entity(
    tableName = "conversation_message_projections",
    primaryKeys = ["chatId", "messageTimestamp", "variantIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationMessageRevisionEntity::class,
            parentColumns = ["revisionId"],
            childColumns = ["currentRevisionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("currentRevisionId", unique = true)],
)
data class ConversationMessageProjectionEntity(
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val currentRevisionId: String,
    val estimatedTokenCount: Int,
    val updatedAt: Long,
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(messageTimestamp > 0L) { "messageTimestamp must be positive" }
        require(variantIndex >= 0) { "variantIndex must not be negative" }
        require(currentRevisionId.isNotBlank()) { "currentRevisionId must not be blank" }
        require(estimatedTokenCount >= 0) { "estimatedTokenCount must not be negative" }
        require(updatedAt > 0L) { "updatedAt must be positive" }
    }
}

/** 当前审计链头的签名封印。 */
@Entity(
    tableName = "conversation_audit_seals",
    primaryKeys = ["sealId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chatId", "sequenceNumber"], unique = true)],
)
data class ConversationAuditSealEntity(
    val sealId: String,
    val chatId: String,
    val sequenceNumber: Long,
    val rootSha256: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
    val publicKeyBase64: String,
    val reason: String,
    val createdAt: Long,
) {
    init {
        require(sealId.isNotBlank()) { "sealId must not be blank" }
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(sequenceNumber > 0L) { "sequenceNumber must be positive" }
        require(LOWERCASE_SHA256.matches(rootSha256)) {
            "rootSha256 must be lowercase SHA-256"
        }
        require(signatureAlgorithm.isNotBlank()) { "signatureAlgorithm must not be blank" }
        require(signatureBase64.isNotBlank()) { "signatureBase64 must not be blank" }
        require(publicKeyBase64.isNotBlank()) { "publicKeyBase64 must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
    }
}

enum class ConversationAuditCompletenessStatus {
    COMPLETE,
    IN_PROGRESS,
    PARTIAL,
    BASIC,
    RECORDING_INTERRUPTED,
    CONTENT_CORRUPTED,
    SOURCE_UNVERIFIED,
    KEY_UNAVAILABLE,
}

enum class ConversationAuditVisibility {
    TIMELINE,
    RAW_ONLY,
    TOMBSTONE,
}

internal val LOWERCASE_SHA256 = Regex("[0-9a-f]{64}")
