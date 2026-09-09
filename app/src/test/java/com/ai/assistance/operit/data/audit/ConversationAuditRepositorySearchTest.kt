package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.dao.ConversationAuditDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class ConversationAuditRepositorySearchTest {
    private val dao = mock<ConversationAuditDao>()
    private val store = mock<ConversationAuditPayloadStore>()
    private val database = mock<AppDatabase>().also { whenever(it.conversationAuditDao()).thenReturn(dao) }
    private val repository = ConversationAuditRepository::class.java.declaredConstructors.single { it.parameterCount == 3 }.let {
        it.isAccessible = true
        it.newInstance(database, store, mock<ConversationAuditCrypto>()) as ConversationAuditRepository
    }
    private val hash = "a".repeat(64)
    private fun event(sequence: Long) = ConversationAuditEventEntity(
        eventId = "event-$sequence", chatId = "chat", sequenceNumber = sequence,
        occurredAt = 1, recordedAt = 1, category = "TOOL", eventType = "TOOL_RESULT_EMITTED",
        actor = "KIYORI", summary = "ssh command result", previousEventSha256 = hash, eventSha256 = hash, visibility = "TIMELINE",
    )

    @Test fun findsBodyOnlyMatchBeyondTheInitialUiPageAndUsesOldestCursor() = runTest {
        whenever(dao.getEventPage("chat", 102, 100)).thenReturn((101L downTo 2).map(::event))
        whenever(dao.getEventPayloads(any())).thenReturn(emptyList())
        val ref = ConversationAuditEventPayloadEntity(eventId = "event-2", payloadSha256 = hash, label = "remote_output", ordinal = 0, role = "tool")
        whenever(dao.getEventPayloads("event-2")).thenReturn(listOf(ref))
        val payload = ConversationAuditPayloadEntity(hash, "payload", 7, 100, "text/plain", "utf-8", "none", "AES", "key", "nonce", 1)
        whenever(dao.getPayload(hash)).thenReturn(payload)
        whenever(store.read(payload)).thenReturn("remote timeout".toByteArray())
        val page = repository.searchEventPage("chat", "ssh timeout", 102)
        assertEquals(100, page.scannedCount)
        assertEquals(2L, page.nextBeforeSequence)
        assertEquals(listOf("event-2"), page.matches.map { it.event.eventId })
        assertEquals("remote timeout", page.matches.single().excerpt)
    }

    @Test fun finalPageAndEmptyPageTerminateSearch() = runTest {
        whenever(dao.getEventPage("chat", 2, 100)).thenReturn(listOf(event(1)))
        whenever(dao.getEventPayloads(any())).thenReturn(emptyList())
        assertNull(repository.searchEventPage("chat", "ssh", 2).nextBeforeSequence)
        whenever(dao.getEventPage("chat", 1, 100)).thenReturn(emptyList())
        assertEquals(0, repository.searchEventPage("chat", "ssh", 1).scannedCount)
    }

    @Test fun payloadCancellationIsNotAnEmptySuccessfulSearch() = runTest {
        whenever(dao.getEventPage("chat", null, 100)).thenReturn(listOf(event(1)))
        whenever(dao.getEventPayloads("event-1")).thenThrow(CancellationException("closed"))
        val failure = runCatching { repository.searchEventPage("chat", "timeout", null) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }
}
