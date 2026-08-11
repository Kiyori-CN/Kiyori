package com.ai.assistance.operit.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageProviderStateEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEventEntity
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import com.ai.assistance.operit.data.model.ProviderTransportKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderExecutionDaoAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ProviderExecutionDao

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.providerExecutionDao()
        database.chatDao().insertChat(
            ChatEntity(
                id = "chat-1",
                title = "test",
                createdAt = 1L,
                updatedAt = 1L,
            )
        )
        val execution =
            ProviderExecutionEntity(
                localExecutionId = "exec-1",
                chatId = "chat-1",
                messageTimestamp = 100L,
                variantIndex = 0,
                hopOrdinal = 0,
                provider = "OPENAI_RESPONSES",
                modelName = "gpt-5.6-sol",
                transportKind = ProviderTransportKind.RESPONSES.name,
                requestFingerprint = "fingerprint",
                status = ProviderExecutionStatus.SUBMITTING.name,
                createdAt = 1L,
                updatedAt = 1L,
            )
        dao.insertExecution(execution)
        dao.insertMessageProviderState(
            state(
                sequenceNumber = ProviderExecutionEntity.NO_APPLIED_SEQUENCE,
                remoteResponseId = null,
                status = ProviderExecutionStatus.SUBMITTING,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun appendEventAndAdvance_isIdempotentAndRejectsSequenceGap() = runBlocking {
        val createdEvent = event(sequenceNumber = 1L, type = "response.created")
        val appended =
            dao.appendEventAndAdvance(
                event = createdEvent,
                nextMessageState =
                    state(
                        sequenceNumber = 1L,
                        remoteResponseId = "resp-1",
                        status = ProviderExecutionStatus.IN_PROGRESS,
                    ),
                terminalEventType = null,
                lastErrorCode = null,
                lastErrorMessage = null,
                completedAt = null,
            )
        assertTrue(appended is ProviderEventAppendOutcome.Appended)
        assertEquals(1L, dao.getExecution("exec-1")?.lastAppliedSequence)

        val duplicate =
            dao.appendEventAndAdvance(
                event = createdEvent,
                nextMessageState =
                    state(
                        sequenceNumber = 1L,
                        remoteResponseId = "resp-1",
                        status = ProviderExecutionStatus.IN_PROGRESS,
                    ),
                terminalEventType = null,
                lastErrorCode = null,
                lastErrorMessage = null,
                completedAt = null,
            )
        assertTrue(duplicate is ProviderEventAppendOutcome.Duplicate)
        assertEquals(1, dao.getEvents("exec-1").size)

        val gapError =
            runCatching {
                dao.appendEventAndAdvance(
                    event = event(sequenceNumber = 3L, type = "response.output_text.delta"),
                    nextMessageState =
                        state(
                            sequenceNumber = 3L,
                            remoteResponseId = "resp-1",
                            status = ProviderExecutionStatus.IN_PROGRESS,
                        ),
                    terminalEventType = null,
                    lastErrorCode = null,
                    lastErrorMessage = null,
                    completedAt = null,
                )
            }.exceptionOrNull()
        assertTrue(gapError is ProviderSequenceGapException)
        assertEquals(1L, dao.getExecution("exec-1")?.lastAppliedSequence)
        assertEquals(1, dao.getEvents("exec-1").size)
    }

    private fun state(
        sequenceNumber: Long,
        remoteResponseId: String?,
        status: ProviderExecutionStatus,
    ): MessageProviderStateEntity =
        MessageProviderStateEntity(
            chatId = "chat-1",
            messageTimestamp = 100L,
            variantIndex = 0,
            latestExecutionId = "exec-1",
            provider = "OPENAI_RESPONSES",
            modelName = "gpt-5.6-sol",
            remoteResponseId = remoteResponseId,
            status = status.name,
            lastAppliedSequence = sequenceNumber,
            createdAt = 1L,
            updatedAt = 2L,
        )

    private fun event(
        sequenceNumber: Long,
        type: String,
    ): ProviderExecutionEventEntity =
        ProviderExecutionEventEntity(
            localExecutionId = "exec-1",
            remoteResponseId = "resp-1",
            sequenceNumber = sequenceNumber,
            eventType = type,
            payloadJson = """{"type":"$type","sequence_number":$sequenceNumber}""",
            payloadSha256 =
                if (sequenceNumber == 1L) {
                    "0".repeat(64)
                } else {
                    "2".repeat(64)
                },
            receivedAt = 2L + sequenceNumber,
        )
}
