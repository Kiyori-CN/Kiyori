package com.ai.assistance.operit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.audit.ConversationAuditHasher
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ConversationAuditEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventPayloadEntity
import com.ai.assistance.operit.data.model.ConversationAuditPayloadEntity
import com.ai.assistance.operit.data.model.ConversationAuditSealEntity
import com.ai.assistance.operit.data.model.ConversationMessageProjectionEntity
import com.ai.assistance.operit.data.model.ConversationMessageRevisionEntity
import com.ai.assistance.operit.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationAuditDaoAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ConversationAuditDao

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.conversationAuditDao()
        database.chatDao().insertChat(
            ChatEntity(
                id = "chat-1",
                title = "test",
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
        dao.insertAuditIfAbsent(
            ConversationAuditEntity(
                chatId = "chat-1",
                schemaVersion = 1,
                completenessStatus = "IN_PROGRESS",
                eventCount = 0L,
                lastSequenceNumber = 0L,
                chainHeadSha256 = "",
                latestSealSequenceNumber = 0L,
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cursorPayloadRevisionProjectionAndSealRemainConsistent() = runBlocking {
        val payloadSha = "1".repeat(64)
        dao.insertPayloadIfAbsent(
            ConversationAuditPayloadEntity(
                payloadSha256 = payloadSha,
                relativePath = "payloads/1.audit",
                plainByteCount = 4L,
                storedByteCount = 16L,
                mediaType = "text/plain",
                encoding = "utf-8",
                compression = "gzip",
                encryptionAlgorithm = "AES/GCM/NoPadding",
                keyAlias = "key",
                nonceBase64 = "nonce",
                createdAt = 1L,
            )
        )
        val event =
            ConversationAuditEventEntity(
                eventId = "event-1",
                chatId = "chat-1",
                sequenceNumber = 1L,
                occurredAt = 2L,
                recordedAt = 2L,
                category = "REVISION",
                eventType = "MESSAGE_REVISED",
                actor = "USER",
                summary = "revised",
                messageTimestamp = 100L,
                variantIndex = 0,
                previousEventSha256 = ConversationAuditHasher.EMPTY_CHAIN_SHA256,
                eventSha256 = "2".repeat(64),
                visibility = "TIMELINE",
            )
        dao.insertEvent(event)
        dao.insertEventPayloads(
            listOf(
                ConversationAuditEventPayloadEntity(
                    eventId = event.eventId,
                    payloadSha256 = payloadSha,
                    label = "after",
                    ordinal = 0,
                    role = "user",
                )
            )
        )
        assertEquals(
            1,
            dao.advanceAudit(
                chatId = "chat-1",
                expectedSequenceNumber = 0L,
                expectedChainHeadSha256 = "",
                newSequenceNumber = 1L,
                newChainHeadSha256 = event.eventSha256,
                completenessStatus = "COMPLETE",
                updatedAt = 2L,
                lastFailureCode = null,
            )
        )
        assertEquals(
            0,
            dao.advanceAudit(
                chatId = "chat-1",
                expectedSequenceNumber = 0L,
                expectedChainHeadSha256 = "",
                newSequenceNumber = 1L,
                newChainHeadSha256 = event.eventSha256,
                completenessStatus = "COMPLETE",
                updatedAt = 2L,
                lastFailureCode = null,
            )
        )

        val revision =
            ConversationMessageRevisionEntity(
                revisionId = "revision-1",
                chatId = "chat-1",
                messageTimestamp = 100L,
                variantIndex = 0,
                revisionNumber = 1,
                sender = "user",
                contentPayloadSha256 = payloadSha,
                auditEventId = event.eventId,
                source = "USER_EDIT",
                createdAt = 2L,
            )
        dao.insertRevision(revision)
        dao.upsertProjection(
            ConversationMessageProjectionEntity(
                chatId = "chat-1",
                messageTimestamp = 100L,
                variantIndex = 0,
                currentRevisionId = revision.revisionId,
                estimatedTokenCount = 1,
                updatedAt = 2L,
            )
        )
        dao.insertSeal(
            ConversationAuditSealEntity(
                sealId = "seal-1",
                chatId = "chat-1",
                sequenceNumber = 1L,
                rootSha256 = event.eventSha256,
                signatureAlgorithm = "SHA256withECDSA",
                signatureBase64 = "signature",
                publicKeyBase64 = "public-key",
                reason = "MESSAGE_REVISED",
                createdAt = 3L,
            )
        )
        assertEquals(1, dao.markSealed("chat-1", 1L, 3L))

        val audit = requireNotNull(dao.getAudit("chat-1"))
        assertEquals(1L, audit.eventCount)
        assertEquals(1L, audit.lastSequenceNumber)
        assertEquals(event.eventSha256, audit.chainHeadSha256)
        assertEquals(1L, audit.latestSealSequenceNumber)
        assertEquals(listOf(event), dao.getAllEvents("chat-1"))
        assertEquals(listOf(revision), dao.getRevisions("chat-1", 100L, 0))
        assertNotNull(dao.getProjection("chat-1", 100L, 0))
        assertEquals("seal-1", dao.getLatestSeal("chat-1")?.sealId)
        assertEquals(0, dao.getUnreferencedPayloads().size)
    }

    @Test
    fun eventPagesAndStoredBytesUseStableSequenceCursors() = runBlocking {
        val payloadOne = "3".repeat(64)
        val payloadTwo = "4".repeat(64)
        listOf(payloadOne to 10L, payloadTwo to 20L).forEach { (sha256, storedBytes) ->
            dao.insertPayloadIfAbsent(
                ConversationAuditPayloadEntity(
                    payloadSha256 = sha256,
                    relativePath = "payloads/$sha256.audit",
                    plainByteCount = 4L,
                    storedByteCount = storedBytes,
                    mediaType = "text/plain",
                    encoding = "utf-8",
                    compression = "gzip",
                    encryptionAlgorithm = "AES/GCM/NoPadding",
                    keyAlias = "key",
                    nonceBase64 = "nonce-$storedBytes",
                    createdAt = 1L,
                )
            )
        }
        val events =
            (1L..5L).map { sequence ->
                ConversationAuditEventEntity(
                    eventId = "event-$sequence",
                    chatId = "chat-1",
                    sequenceNumber = sequence,
                    occurredAt = sequence + 1L,
                    recordedAt = sequence + 1L,
                    category = "TEST",
                    eventType = "EVENT_$sequence",
                    actor = "TEST",
                    summary = "event $sequence",
                    previousEventSha256 =
                        if (sequence == 1L) {
                            ConversationAuditHasher.EMPTY_CHAIN_SHA256
                        } else {
                            (sequence - 1L).toString(16).padStart(64, '0')
                        },
                    eventSha256 = sequence.toString(16).padStart(64, '0'),
                    visibility = "TIMELINE",
                ).also { event -> dao.insertEvent(event) }
            }
        dao.insertEventPayloads(
            listOf(
                ConversationAuditEventPayloadEntity(
                    eventId = events.first().eventId,
                    payloadSha256 = payloadOne,
                    label = "first",
                    ordinal = 0,
                    role = "test",
                ),
                ConversationAuditEventPayloadEntity(
                    eventId = events.last().eventId,
                    payloadSha256 = payloadTwo,
                    label = "last",
                    ordinal = 0,
                    role = "test",
                ),
            )
        )

        assertEquals(listOf(5L, 4L), dao.getEventPage("chat-1", null, 2).map { it.sequenceNumber })
        assertEquals(listOf(3L, 2L), dao.getEventPage("chat-1", 4L, 2).map { it.sequenceNumber })
        assertEquals(
            listOf(4L, 5L),
            dao.getEventsAfterSequence("chat-1", 3L).map { it.sequenceNumber },
        )
        assertEquals(5L, dao.observeLastEvent("chat-1").first()?.sequenceNumber)
        assertEquals(30L, dao.getStoredPayloadBytesForChat("chat-1"))
    }
}
