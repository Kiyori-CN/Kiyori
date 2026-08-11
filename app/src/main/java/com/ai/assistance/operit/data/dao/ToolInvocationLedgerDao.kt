package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ai.assistance.operit.data.model.ToolInvocationLedgerEntity
import com.ai.assistance.operit.data.model.ToolInvocationStatus

@Dao
interface ToolInvocationLedgerDao {
    @Query(
        """
        SELECT * FROM tool_invocation_ledger
        WHERE provider = :provider
            AND remoteResponseId = :remoteResponseId
            AND callId = :callId
        LIMIT 1
        """
    )
    suspend fun getInvocation(
        provider: String,
        remoteResponseId: String,
        callId: String,
    ): ToolInvocationLedgerEntity?

    @Query(
        """
        SELECT * FROM tool_invocation_ledger
        WHERE localExecutionId = :localExecutionId
        ORDER BY createdAt ASC
        """
    )
    suspend fun getInvocationsForExecution(
        localExecutionId: String,
    ): List<ToolInvocationLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(invocation: ToolInvocationLedgerEntity): Long

    @Query(
        """
        UPDATE tool_invocation_ledger
        SET status = :runningStatus,
            startedAt = :startedAt,
            updatedAt = :startedAt
        WHERE provider = :provider
            AND remoteResponseId = :remoteResponseId
            AND callId = :callId
            AND status = :pendingStatus
        """
    )
    suspend fun markRunning(
        provider: String,
        remoteResponseId: String,
        callId: String,
        startedAt: Long,
        pendingStatus: String = ToolInvocationStatus.PENDING.name,
        runningStatus: String = ToolInvocationStatus.RUNNING.name,
    ): Int

    @Query(
        """
        UPDATE tool_invocation_ledger
        SET status = :completedStatus,
            resultJson = :resultJson,
            errorMessage = NULL,
            completedAt = :completedAt,
            updatedAt = :completedAt
        WHERE provider = :provider
            AND remoteResponseId = :remoteResponseId
            AND callId = :callId
            AND status = :runningStatus
        """
    )
    suspend fun markCompleted(
        provider: String,
        remoteResponseId: String,
        callId: String,
        resultJson: String,
        completedAt: Long,
        runningStatus: String = ToolInvocationStatus.RUNNING.name,
        completedStatus: String = ToolInvocationStatus.COMPLETED.name,
    ): Int

    @Query(
        """
        UPDATE tool_invocation_ledger
        SET status = :failedStatus,
            errorMessage = :errorMessage,
            completedAt = :completedAt,
            updatedAt = :completedAt
        WHERE provider = :provider
            AND remoteResponseId = :remoteResponseId
            AND callId = :callId
            AND status = :runningStatus
        """
    )
    suspend fun markFailed(
        provider: String,
        remoteResponseId: String,
        callId: String,
        errorMessage: String,
        completedAt: Long,
        runningStatus: String = ToolInvocationStatus.RUNNING.name,
        failedStatus: String = ToolInvocationStatus.FAILED.name,
    ): Int

    @Transaction
    suspend fun registerInvocation(
        invocation: ToolInvocationLedgerEntity,
    ): ToolInvocationRegistration {
        require(invocation.status == ToolInvocationStatus.PENDING.name) {
            "New tool invocation must start in PENDING"
        }
        val existing =
            getInvocation(
                provider = invocation.provider,
                remoteResponseId = invocation.remoteResponseId,
                callId = invocation.callId,
            )
        if (existing != null) {
            validateSameInvocation(existing, invocation)
            return ToolInvocationRegistration.Existing(existing)
        }

        val insertedId = insertIgnoringDuplicate(invocation)
        if (insertedId == -1L) {
            val concurrent =
                getInvocation(
                    provider = invocation.provider,
                    remoteResponseId = invocation.remoteResponseId,
                    callId = invocation.callId,
                ) ?: throw ToolInvocationLedgerCorruptionException(
                    "Tool invocation insert conflicted but no ledger row exists"
                )
            validateSameInvocation(concurrent, invocation)
            return ToolInvocationRegistration.Existing(concurrent)
        }
        return ToolInvocationRegistration.Created(invocation)
    }

    private fun validateSameInvocation(
        existing: ToolInvocationLedgerEntity,
        incoming: ToolInvocationLedgerEntity,
    ) {
        if (
            existing.localExecutionId != incoming.localExecutionId ||
                existing.toolName != incoming.toolName ||
                existing.argumentsSha256 != incoming.argumentsSha256 ||
                existing.argumentsJson != incoming.argumentsJson
        ) {
            throw ToolInvocationConflictException(
                provider = incoming.provider,
                remoteResponseId = incoming.remoteResponseId,
                callId = incoming.callId,
            )
        }
    }
}

sealed interface ToolInvocationRegistration {
    val invocation: ToolInvocationLedgerEntity

    data class Created(override val invocation: ToolInvocationLedgerEntity) :
        ToolInvocationRegistration

    data class Existing(override val invocation: ToolInvocationLedgerEntity) :
        ToolInvocationRegistration
}

class ToolInvocationConflictException(
    provider: String,
    remoteResponseId: String,
    callId: String,
) : IllegalStateException(
    "Tool invocation identity conflict for $provider/$remoteResponseId/$callId"
)

class ToolInvocationLedgerCorruptionException(message: String) : IllegalStateException(message)
