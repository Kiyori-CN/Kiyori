package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ai.assistance.operit.data.model.MessageProviderStateEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEventEntity

@Dao
interface ProviderExecutionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExecution(execution: ProviderExecutionEntity)

    @Upsert
    suspend fun insertMessageProviderState(state: MessageProviderStateEntity)

    @Query("SELECT * FROM provider_executions WHERE localExecutionId = :localExecutionId")
    suspend fun getExecution(localExecutionId: String): ProviderExecutionEntity?

    @Query(
        """
        SELECT * FROM provider_executions
        WHERE provider = :provider AND remoteResponseId = :remoteResponseId
        LIMIT 1
        """
    )
    suspend fun getExecutionByRemoteResponse(
        provider: String,
        remoteResponseId: String,
    ): ProviderExecutionEntity?

    @Query(
        """
        SELECT * FROM provider_executions
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        ORDER BY hopOrdinal DESC, createdAt DESC
        """
    )
    suspend fun getExecutionsForMessage(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): List<ProviderExecutionEntity>

    @Query(
        """
        SELECT * FROM provider_executions
        WHERE status IN (:statuses)
        ORDER BY updatedAt ASC
        """
    )
    suspend fun getExecutionsByStatuses(statuses: List<String>): List<ProviderExecutionEntity>

    @Query(
        """
        SELECT * FROM provider_execution_events
        WHERE remoteResponseId = :remoteResponseId AND sequenceNumber = :sequenceNumber
        LIMIT 1
        """
    )
    suspend fun getEvent(
        remoteResponseId: String,
        sequenceNumber: Long,
    ): ProviderExecutionEventEntity?

    @Query(
        """
        SELECT * FROM provider_execution_events
        WHERE localExecutionId = :localExecutionId
        ORDER BY sequenceNumber ASC
        """
    )
    suspend fun getEvents(localExecutionId: String): List<ProviderExecutionEventEntity>

    @Query(
        """
        SELECT * FROM message_provider_states
        WHERE latestExecutionId = :localExecutionId
        LIMIT 1
        """
    )
    suspend fun getMessageProviderStateForExecution(
        localExecutionId: String,
    ): MessageProviderStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventIgnoringDuplicate(event: ProviderExecutionEventEntity): Long

    @Upsert
    suspend fun upsertMessageProviderState(state: MessageProviderStateEntity)

    @Query(
        """
        UPDATE provider_executions
        SET remoteResponseId = :remoteResponseId,
            lastAppliedSequence = :newSequence,
            status = :status,
            terminalEventType = COALESCE(:terminalEventType, terminalEventType),
            lastErrorCode = :lastErrorCode,
            lastErrorMessage = :lastErrorMessage,
            updatedAt = :updatedAt,
            completedAt = COALESCE(:completedAt, completedAt)
        WHERE localExecutionId = :localExecutionId
            AND lastAppliedSequence = :expectedPreviousSequence
            AND (remoteResponseId IS NULL OR remoteResponseId = :remoteResponseId)
        """
    )
    suspend fun advanceExecutionCursor(
        localExecutionId: String,
        remoteResponseId: String,
        expectedPreviousSequence: Long,
        newSequence: Long,
        status: String,
        terminalEventType: String?,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        updatedAt: Long,
        completedAt: Long?,
    ): Int

    @Query(
        """
        UPDATE provider_executions
        SET status = :status,
            lastErrorCode = :lastErrorCode,
            lastErrorMessage = :lastErrorMessage,
            updatedAt = :updatedAt,
            completedAt = COALESCE(:completedAt, completedAt)
        WHERE localExecutionId = :localExecutionId
        """
    )
    suspend fun updateExecutionStatus(
        localExecutionId: String,
        status: String,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        updatedAt: Long,
        completedAt: Long?,
    ): Int

    @Query(
        """
        UPDATE message_provider_states
        SET status = :status,
            updatedAt = :updatedAt
        WHERE latestExecutionId = :localExecutionId
        """
    )
    suspend fun updateMessageProviderStateStatus(
        localExecutionId: String,
        status: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE provider_executions
        SET resumeCount = resumeCount + 1,
            status = :status,
            updatedAt = :updatedAt
        WHERE localExecutionId = :localExecutionId
            AND remoteResponseId IS NOT NULL
        """
    )
    suspend fun incrementResumeCount(
        localExecutionId: String,
        status: String,
        updatedAt: Long,
    ): Int

    /**
     * 一个 SSE 事件只有在事件账本、message provider state 和 execution cursor 全部提交后才算应用。
     * 否则进程崩溃可能造成 UI 已显示内容但续流游标尚未推进，从而重复应用同一事件。
     */
    @Transaction
    suspend fun appendEventAndAdvance(
        event: ProviderExecutionEventEntity,
        nextMessageState: MessageProviderStateEntity,
        terminalEventType: String?,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        completedAt: Long?,
    ): ProviderEventAppendOutcome {
        val execution =
            getExecution(event.localExecutionId)
                ?: throw ProviderExecutionNotFoundException(event.localExecutionId)

        if (
            execution.remoteResponseId != null &&
                execution.remoteResponseId != event.remoteResponseId
        ) {
            throw ProviderRemoteResponseMismatchException(
                localExecutionId = event.localExecutionId,
                expectedRemoteResponseId = execution.remoteResponseId,
                actualRemoteResponseId = event.remoteResponseId,
            )
        }

        if (event.sequenceNumber <= execution.lastAppliedSequence) {
            val existing =
                getEvent(event.remoteResponseId, event.sequenceNumber)
                    ?: throw ProviderEventLedgerCorruptionException(
                        "Execution ${event.localExecutionId} cursor is ${execution.lastAppliedSequence}, " +
                            "but sequence ${event.sequenceNumber} is absent from the event ledger"
                    )
            if (
                existing.localExecutionId != event.localExecutionId ||
                    existing.eventType != event.eventType ||
                    existing.payloadSha256 != event.payloadSha256 ||
                    existing.payloadJson != event.payloadJson
            ) {
                throw ProviderEventConflictException(
                    remoteResponseId = event.remoteResponseId,
                    sequenceNumber = event.sequenceNumber,
                )
            }
            return ProviderEventAppendOutcome.Duplicate(existing)
        }

        val expectedSequence =
            ProviderExecutionEntity.expectedNextSequence(execution.lastAppliedSequence)
        if (event.sequenceNumber != expectedSequence) {
            throw ProviderSequenceGapException(
                localExecutionId = event.localExecutionId,
                expectedSequence = expectedSequence,
                actualSequence = event.sequenceNumber,
            )
        }

        require(nextMessageState.latestExecutionId == event.localExecutionId) {
            "Message provider state must belong to ${event.localExecutionId}"
        }
        require(nextMessageState.remoteResponseId == event.remoteResponseId) {
            "Message provider state remoteResponseId must match the event"
        }
        require(nextMessageState.lastAppliedSequence == event.sequenceNumber) {
            "Message provider state cursor must match the event sequence"
        }

        val insertedId = insertEventIgnoringDuplicate(event)
        if (insertedId == -1L) {
            throw ProviderEventConflictException(
                remoteResponseId = event.remoteResponseId,
                sequenceNumber = event.sequenceNumber,
            )
        }

        upsertMessageProviderState(nextMessageState)
        val advanced =
            advanceExecutionCursor(
                localExecutionId = event.localExecutionId,
                remoteResponseId = event.remoteResponseId,
                expectedPreviousSequence = execution.lastAppliedSequence,
                newSequence = event.sequenceNumber,
                status = nextMessageState.status,
                terminalEventType = terminalEventType,
                lastErrorCode = lastErrorCode,
                lastErrorMessage = lastErrorMessage,
                updatedAt = nextMessageState.updatedAt,
                completedAt = completedAt,
            )
        if (advanced != 1) {
            throw ProviderEventLedgerCorruptionException(
                "Failed to advance execution ${event.localExecutionId} from " +
                    "${execution.lastAppliedSequence} to ${event.sequenceNumber}"
            )
        }

        return ProviderEventAppendOutcome.Appended(event)
    }
}

sealed interface ProviderEventAppendOutcome {
    val event: ProviderExecutionEventEntity

    data class Appended(override val event: ProviderExecutionEventEntity) :
        ProviderEventAppendOutcome

    data class Duplicate(override val event: ProviderExecutionEventEntity) :
        ProviderEventAppendOutcome
}

class ProviderExecutionNotFoundException(localExecutionId: String) :
    IllegalStateException("Provider execution not found: $localExecutionId")

class ProviderRemoteResponseMismatchException(
    localExecutionId: String,
    expectedRemoteResponseId: String,
    actualRemoteResponseId: String,
) : IllegalStateException(
    "Provider execution $localExecutionId is bound to $expectedRemoteResponseId, " +
        "not $actualRemoteResponseId"
)

class ProviderSequenceGapException(
    val localExecutionId: String,
    val expectedSequence: Long,
    val actualSequence: Long,
) : IllegalStateException(
    "Provider event sequence gap for $localExecutionId: expected $expectedSequence, " +
        "received $actualSequence"
)

class ProviderEventConflictException(
    remoteResponseId: String,
    sequenceNumber: Long,
) : IllegalStateException(
    "Conflicting provider event for response $remoteResponseId sequence $sequenceNumber"
)

class ProviderEventLedgerCorruptionException(message: String) : IllegalStateException(message)
