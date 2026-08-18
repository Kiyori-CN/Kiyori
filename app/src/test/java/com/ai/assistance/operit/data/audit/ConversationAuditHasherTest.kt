package com.ai.assistance.operit.data.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConversationAuditHasherTest {
    @Test
    fun `event hash is deterministic and binds payload order`() {
        val input =
            ConversationAuditHashInput(
                previousEventSha256 = ConversationAuditHasher.EMPTY_CHAIN_SHA256,
                eventId = "event-1",
                chatId = "chat-1",
                sequenceNumber = 1L,
                occurredAt = 100L,
                recordedAt = 101L,
                category = "USER",
                eventType = "USER_INPUT_SUBMITTED",
                actor = "USER",
                summary = "submitted",
                messageTimestamp = 100L,
                variantIndex = 0,
                localExecutionId = null,
                providerCallId = null,
                parentEventId = null,
                sourceChatId = null,
                sourceEventId = null,
                visibility = "TIMELINE",
                terminalState = null,
                payloadRefs =
                    listOf(
                        ConversationAuditHashPayloadRef(
                            label = "input",
                            ordinal = 0,
                            role = "user",
                            payloadSha256 = "1".repeat(64),
                            mediaType = "text/plain",
                            encoding = "utf-8",
                            plainByteCount = 5L,
                        )
                    ),
            )

        assertEquals(
            ConversationAuditHasher.eventHash(input),
            ConversationAuditHasher.eventHash(input),
        )
        assertNotEquals(
            ConversationAuditHasher.eventHash(input),
            ConversationAuditHasher.eventHash(
                input.copy(
                    payloadRefs =
                        input.payloadRefs.map { ref ->
                            ref.copy(payloadSha256 = "2".repeat(64))
                        }
                )
            ),
        )
        assertNotEquals(
            ConversationAuditHasher.eventHash(input),
            ConversationAuditHasher.eventHash(input.copy(eventId = "event-2")),
        )
        assertNotEquals(
            ConversationAuditHasher.eventHash(input),
            ConversationAuditHasher.eventHash(input.copy(recordedAt = 102L)),
        )
        assertNotEquals(
            ConversationAuditHasher.eventHash(input),
            ConversationAuditHasher.eventHash(
                input.copy(
                    payloadRefs =
                        input.payloadRefs.map { ref ->
                            ref.copy(mediaType = "application/json")
                        }
                )
            ),
        )
    }
}
