package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.OperitArchivedConversationAudit
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditEvent
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditEventPayload
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditPayload
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditRoot
import com.ai.assistance.operit.data.model.OperitArchivedConversationAuditSeal
import com.ai.assistance.operit.data.model.OperitArchivedConversationMessageProjection
import com.ai.assistance.operit.data.model.OperitArchivedConversationMessageRevision
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationAuditArchiveSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `portable audit archive round trips every contract collection`() {
        val payloadBytes = "hello".toByteArray()
        val payloadSha256 = ConversationAuditHasher.sha256(payloadBytes)
        val eventSha256 = "a".repeat(64)
        val archive =
            OperitArchivedConversationAudit(
                schemaVersion = 1,
                audit =
                    OperitArchivedConversationAuditRoot(
                        chatId = "chat-1",
                        schemaVersion = 1,
                        completenessStatus = "COMPLETE",
                        eventCount = 1L,
                        lastSequenceNumber = 1L,
                        chainHeadSha256 = eventSha256,
                        latestSealSequenceNumber = 1L,
                        createdAt = 1L,
                        updatedAt = 2L,
                    ),
                events =
                    listOf(
                        OperitArchivedConversationAuditEvent(
                            eventId = "event-1",
                            chatId = "chat-1",
                            sequenceNumber = 1L,
                            occurredAt = 1L,
                            recordedAt = 1L,
                            category = "REVISION",
                            eventType = "MESSAGE_REVISED",
                            actor = "USER",
                            summary = "revision",
                            messageTimestamp = 10L,
                            variantIndex = 0,
                            previousEventSha256 = ConversationAuditHasher.EMPTY_CHAIN_SHA256,
                            eventSha256 = eventSha256,
                            visibility = "TIMELINE",
                        )
                    ),
                eventPayloads =
                    listOf(
                        OperitArchivedConversationAuditEventPayload(
                            eventId = "event-1",
                            payloadSha256 = payloadSha256,
                            label = "content",
                            ordinal = 0,
                            role = "user",
                        )
                    ),
                revisions =
                    listOf(
                        OperitArchivedConversationMessageRevision(
                            revisionId = "revision-1",
                            chatId = "chat-1",
                            messageTimestamp = 10L,
                            variantIndex = 0,
                            revisionNumber = 0,
                            sender = "user",
                            contentPayloadSha256 = payloadSha256,
                            auditEventId = "event-1",
                            source = "IMPORT",
                            createdAt = 1L,
                        )
                    ),
                projections =
                    listOf(
                        OperitArchivedConversationMessageProjection(
                            chatId = "chat-1",
                            messageTimestamp = 10L,
                            variantIndex = 0,
                            currentRevisionId = "revision-1",
                            estimatedTokenCount = 1,
                            updatedAt = 2L,
                        )
                    ),
                seals =
                    listOf(
                        OperitArchivedConversationAuditSeal(
                            sealId = "seal-1",
                            chatId = "chat-1",
                            sequenceNumber = 1L,
                            rootSha256 = eventSha256,
                            signatureAlgorithm = "SHA256withECDSA",
                            signatureBase64 = "signature",
                            publicKeyBase64 = "public-key",
                            reason = "TEST",
                            createdAt = 2L,
                        )
                    ),
                payloads =
                    listOf(
                        OperitArchivedConversationAuditPayload(
                            payloadSha256 = payloadSha256,
                            plainByteCount = payloadBytes.size.toLong(),
                            mediaType = "text/plain",
                            encoding = "utf-8",
                            createdAt = 1L,
                            bytesBase64 = Base64.getEncoder().encodeToString(payloadBytes),
                        )
                    ),
            )

        val encoded = json.encodeToString(archive)
        val decoded = json.decodeFromString<OperitArchivedConversationAudit>(encoded)

        assertEquals(archive, decoded)
    }
}
