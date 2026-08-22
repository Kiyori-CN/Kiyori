package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.OperitArchivedConversationAudit
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditEvent
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditEventPayload
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditPayload
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditRoot
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditSeal
import com.ai.assistance.operit.data.model.OperitArchivedConversationMessageProjection
import com.ai.assistance.operit.data.model.OperitArchivedConversationMessageRevision
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.OperitArchivedChat
import com.ai.assistance.operit.data.model.OperitArchivedMessage
import com.ai.assistance.operit.data.model.OperitArchivedMessageVariant
import java.util.Base64
import java.time.Instant
import java.time.ZoneId

fun ConversationAuditExportSnapshot.toOperitArchivedConversationAudit():
    OperitArchivedConversationAudit =
    OperitArchivedConversationAudit(
        schemaVersion = ConversationAuditRepository.SCHEMA_VERSION,
        audit =
            OperitArchivedConversationAuditRoot(
                chatId = audit.chatId,
                schemaVersion = audit.schemaVersion,
                completenessStatus = audit.completenessStatus,
                eventCount = audit.eventCount,
                lastSequenceNumber = audit.lastSequenceNumber,
                chainHeadSha256 = audit.chainHeadSha256,
                latestSealSequenceNumber = audit.latestSealSequenceNumber,
                createdAt = audit.createdAt,
                updatedAt = audit.updatedAt,
                lastFailureCode = audit.lastFailureCode,
                legacyReconstructionLevel = audit.legacyReconstructionLevel,
            ),
        events =
            events.map { event ->
                OperitArchivedConversationAuditEvent(
                    eventId = event.eventId,
                    chatId = event.chatId,
                    sequenceNumber = event.sequenceNumber,
                    occurredAt = event.occurredAt,
                    recordedAt = event.recordedAt,
                    category = event.category,
                    eventType = event.eventType,
                    actor = event.actor,
                    summary = event.summary,
                    messageTimestamp = event.messageTimestamp,
                    variantIndex = event.variantIndex,
                    localExecutionId = event.localExecutionId,
                    providerCallId = event.providerCallId,
                    parentEventId = event.parentEventId,
                    sourceChatId = event.sourceChatId,
                    sourceEventId = event.sourceEventId,
                    previousEventSha256 = event.previousEventSha256,
                    eventSha256 = event.eventSha256,
                    visibility = event.visibility,
                    terminalState = event.terminalState,
                )
            },
        eventPayloads =
            events.flatMap { event ->
                eventPayloads[event.eventId].orEmpty().map { ref ->
                    OperitArchivedConversationAuditEventPayload(
                        eventId = ref.eventId,
                        payloadSha256 = ref.payloadSha256,
                        label = ref.label,
                        ordinal = ref.ordinal,
                        role = ref.role,
                    )
                }
            },
        revisions =
            revisions.map { revision ->
                OperitArchivedConversationMessageRevision(
                    revisionId = revision.revisionId,
                    chatId = revision.chatId,
                    messageTimestamp = revision.messageTimestamp,
                    variantIndex = revision.variantIndex,
                    revisionNumber = revision.revisionNumber,
                    sender = revision.sender,
                    contentPayloadSha256 = revision.contentPayloadSha256,
                    previousRevisionId = revision.previousRevisionId,
                    auditEventId = revision.auditEventId,
                    source = revision.source,
                    createdAt = revision.createdAt,
                )
            },
        projections =
            projections.map { projection ->
                OperitArchivedConversationMessageProjection(
                    chatId = projection.chatId,
                    messageTimestamp = projection.messageTimestamp,
                    variantIndex = projection.variantIndex,
                    currentRevisionId = projection.currentRevisionId,
                    estimatedTokenCount = projection.estimatedTokenCount,
                    updatedAt = projection.updatedAt,
                )
            },
        seals =
            seals.map { seal ->
                OperitArchivedConversationAuditSeal(
                    sealId = seal.sealId,
                    chatId = seal.chatId,
                    sequenceNumber = seal.sequenceNumber,
                    rootSha256 = seal.rootSha256,
                    signatureAlgorithm = seal.signatureAlgorithm,
                    signatureBase64 = seal.signatureBase64,
                    publicKeyBase64 = seal.publicKeyBase64,
                    reason = seal.reason,
                    createdAt = seal.createdAt,
                )
            },
        payloads =
            payloads
                .toSortedMap()
                .map { (payloadSha256, payload) ->
                    OperitArchivedConversationAuditPayload(
                        payloadSha256 = payloadSha256,
                        plainByteCount = payload.entity.plainByteCount,
                        mediaType = payload.entity.mediaType,
                        encoding = payload.entity.encoding,
                        createdAt = payload.entity.createdAt,
                        bytesBase64 = Base64.getEncoder().encodeToString(payload.bytes),
                    )
                },
    )

fun ConversationAuditExportSnapshot.toOperitArchivedChat(
    includeAudit: Boolean,
): OperitArchivedChat {
    val variantsByTimestamp = variants.groupBy { variant -> variant.messageTimestamp }
    val archivedMessages =
        messages.map { message ->
            val messageVariants = variantsByTimestamp[message.timestamp].orEmpty()
            OperitArchivedMessage(
                baseMessage =
                    message.toChatMessage().copy(
                        variantCount = messageVariants.size + 1,
                    ),
                variants = messageVariants.map(OperitArchivedMessageVariant::fromEntity),
            )
        }
    val history =
        ChatHistory(
            id = chat.id,
            title = chat.title,
            messages = archivedMessages.map { archived -> archived.baseMessage },
            createdAt =
                Instant.ofEpochMilli(chat.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime(),
            updatedAt =
                Instant.ofEpochMilli(chat.updatedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime(),
            inputTokens = chat.inputTokens,
            outputTokens = chat.outputTokens,
            currentWindowSize = chat.currentWindowSize,
            providerRequestCount = chat.providerRequestCount,
            providerUsageRequestCount = chat.providerUsageRequestCount,
            providerCacheMetricRequestCount = chat.providerCacheMetricRequestCount,
            providerCacheMetricPromptTokens = chat.providerCacheMetricPromptTokens,
            providerTotalInputTokens = chat.providerTotalInputTokens,
            providerUncachedInputTokens = chat.providerUncachedInputTokens,
            providerCacheReadTokens = chat.providerCacheReadTokens,
            providerCacheWriteTokens = chat.providerCacheWriteTokens,
            providerOutputTokens = chat.providerOutputTokens,
            providerReasoningTokens = chat.providerReasoningTokens,
            group = chat.group,
            displayOrder = chat.displayOrder,
            workspace = chat.workspace,
            workspaceEnv = chat.workspaceEnv,
            parentChatId = chat.parentChatId,
            characterCardName = chat.characterCardName,
            characterGroupId = chat.characterGroupId,
            locked = chat.locked,
            pinned = chat.pinned,
        )
    return OperitArchivedChat.fromChatHistory(
        history = history,
        messages = archivedMessages,
        conversationAudit =
            if (includeAudit) {
                toOperitArchivedConversationAudit()
            } else {
                null
            },
    )
}
