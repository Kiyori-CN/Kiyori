package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.dao.ProviderEventAppendOutcome
import com.ai.assistance.operit.data.dao.ToolInvocationRegistration
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MessageProviderStateEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEventEntity
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import com.ai.assistance.operit.data.model.ToolInvocationLedgerEntity
import com.ai.assistance.operit.data.model.ToolInvocationStatus
import java.security.MessageDigest

/**
 * provider execution、事件游标、消息私有状态和工具账本的唯一写入入口。
 *
 * 该仓储不持有进程内游标；每次判断都读取 Room，进程重建后仍能继续同一远端 response。
 */
class ProviderExecutionRepository private constructor(
    private val database: AppDatabase,
) {
    private val executionDao = database.providerExecutionDao()
    private val messageStateDao = database.messageProviderStateDao()
    private val toolLedgerDao = database.toolInvocationLedgerDao()

    suspend fun createExecution(
        execution: ProviderExecutionEntity,
        initialMessageState: MessageProviderStateEntity,
    ) {
        require(initialMessageState.latestExecutionId == execution.localExecutionId) {
            "Initial message state must reference ${execution.localExecutionId}"
        }
        require(initialMessageState.chatId == execution.chatId) {
            "Initial message state chatId must match the execution"
        }
        require(initialMessageState.messageTimestamp == execution.messageTimestamp) {
            "Initial message state messageTimestamp must match the execution"
        }
        require(initialMessageState.variantIndex == execution.variantIndex) {
            "Initial message state variantIndex must match the execution"
        }
        require(initialMessageState.provider == execution.provider) {
            "Initial message state provider must match the execution"
        }
        require(initialMessageState.modelName == execution.modelName) {
            "Initial message state modelName must match the execution"
        }
        require(initialMessageState.remoteResponseId == null) {
            "A newly compiled execution cannot already own a remote response"
        }
        require(
            initialMessageState.lastAppliedSequence ==
                ProviderExecutionEntity.NO_APPLIED_SEQUENCE
        ) {
            "A newly compiled execution cannot have an applied provider event"
        }

        database.withTransaction {
            executionDao.insertExecution(execution)
            executionDao.insertMessageProviderState(initialMessageState)
        }
    }

    suspend fun appendEvent(
        localExecutionId: String,
        remoteResponseId: String,
        sequenceNumber: Long,
        eventType: String,
        payloadJson: String,
        nextMessageState: MessageProviderStateEntity,
        terminalEventType: String? = null,
        lastErrorCode: String? = null,
        lastErrorMessage: String? = null,
        completedAt: Long? = null,
        receivedAt: Long = System.currentTimeMillis(),
    ): ProviderEventAppendOutcome {
        require(payloadJson.isNotBlank()) { "Provider event payload must not be blank" }
        val event =
            ProviderExecutionEventEntity(
                localExecutionId = localExecutionId,
                remoteResponseId = remoteResponseId,
                sequenceNumber = sequenceNumber,
                eventType = eventType,
                payloadJson = payloadJson,
                payloadSha256 = sha256(payloadJson),
                receivedAt = receivedAt,
            )
        return executionDao.appendEventAndAdvance(
            event = event,
            nextMessageState = nextMessageState,
            terminalEventType = terminalEventType,
            lastErrorCode = lastErrorCode,
            lastErrorMessage = lastErrorMessage,
            completedAt = completedAt,
        )
    }

    suspend fun getExecution(localExecutionId: String): ProviderExecutionEntity? =
        executionDao.getExecution(localExecutionId)

    suspend fun getExecutionByRemoteResponse(
        provider: String,
        remoteResponseId: String,
    ): ProviderExecutionEntity? =
        executionDao.getExecutionByRemoteResponse(provider, remoteResponseId)

    suspend fun getMessageState(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): MessageProviderStateEntity? =
        messageStateDao.getState(chatId, messageTimestamp, variantIndex)

    suspend fun getMessageStateForExecution(
        localExecutionId: String,
    ): MessageProviderStateEntity? =
        executionDao.getMessageProviderStateForExecution(localExecutionId)

    suspend fun getEvents(
        localExecutionId: String,
    ): List<ProviderExecutionEventEntity> =
        executionDao.getEvents(localExecutionId)

    suspend fun getRecoverableExecutions(): List<ProviderExecutionEntity> =
        executionDao.getExecutionsByStatuses(
            listOf(
                ProviderExecutionStatus.SUBMITTING.name,
                ProviderExecutionStatus.SUBMISSION_UNKNOWN.name,
                ProviderExecutionStatus.QUEUED.name,
                ProviderExecutionStatus.IN_PROGRESS.name,
                ProviderExecutionStatus.DISCONNECTED.name,
                ProviderExecutionStatus.RESUMING.name,
                ProviderExecutionStatus.WAITING_TOOL.name,
                ProviderExecutionStatus.SUBMITTING_TOOL_OUTPUT.name,
                ProviderExecutionStatus.CANCELLING.name,
            )
        )

    suspend fun updateStatus(
        localExecutionId: String,
        status: ProviderExecutionStatus,
        lastErrorCode: String? = null,
        lastErrorMessage: String? = null,
        completedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        database.withTransaction {
            val updated =
                executionDao.updateExecutionStatus(
                    localExecutionId = localExecutionId,
                    status = status.name,
                    lastErrorCode = lastErrorCode,
                    lastErrorMessage = lastErrorMessage,
                    updatedAt = updatedAt,
                    completedAt = completedAt,
                )
            if (updated != 1) {
                error("Provider execution not found: $localExecutionId")
            }
            executionDao.updateMessageProviderStateStatus(
                localExecutionId = localExecutionId,
                status = status.name,
                updatedAt = updatedAt,
            )
        }
    }

    suspend fun beginResume(
        localExecutionId: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): ProviderExecutionEntity {
        database.withTransaction {
            val updated =
                executionDao.incrementResumeCount(
                    localExecutionId = localExecutionId,
                    status = ProviderExecutionStatus.RESUMING.name,
                    updatedAt = updatedAt,
                )
            if (updated != 1) {
                error(
                    "Provider execution $localExecutionId cannot resume before a remote response ID is known"
                )
            }
            executionDao.updateMessageProviderStateStatus(
                localExecutionId = localExecutionId,
                status = ProviderExecutionStatus.RESUMING.name,
                updatedAt = updatedAt,
            )
        }
        return requireNotNull(executionDao.getExecution(localExecutionId))
    }

    suspend fun registerToolInvocation(
        invocation: ToolInvocationLedgerEntity,
    ): ToolInvocationRegistration =
        toolLedgerDao.registerInvocation(invocation)

    suspend fun claimToolInvocation(
        provider: String,
        remoteResponseId: String,
        callId: String,
        startedAt: Long = System.currentTimeMillis(),
    ): ToolInvocationClaim {
        val updated =
            toolLedgerDao.markRunning(
                provider = provider,
                remoteResponseId = remoteResponseId,
                callId = callId,
                startedAt = startedAt,
            )
        if (updated == 1) {
            val running =
                requireNotNull(
                    toolLedgerDao.getInvocation(provider, remoteResponseId, callId)
                )
            return ToolInvocationClaim.Claimed(running)
        }

        val existing =
            toolLedgerDao.getInvocation(provider, remoteResponseId, callId)
                ?: error("Tool invocation not found: $provider/$remoteResponseId/$callId")
        return when (existing.status) {
            ToolInvocationStatus.COMPLETED.name -> ToolInvocationClaim.Completed(existing)
            ToolInvocationStatus.RUNNING.name -> ToolInvocationClaim.AlreadyRunning(existing)
            ToolInvocationStatus.FAILED.name -> ToolInvocationClaim.Failed(existing)
            ToolInvocationStatus.PENDING.name ->
                error("Unable to claim pending tool invocation $provider/$remoteResponseId/$callId")
            else -> error("Unknown tool invocation status: ${existing.status}")
        }
    }

    suspend fun completeToolInvocation(
        provider: String,
        remoteResponseId: String,
        callId: String,
        resultJson: String,
        completedAt: Long = System.currentTimeMillis(),
    ): ToolInvocationLedgerEntity {
        require(resultJson.isNotBlank()) { "Tool result must not be blank" }
        val updated =
            toolLedgerDao.markCompleted(
                provider = provider,
                remoteResponseId = remoteResponseId,
                callId = callId,
                resultJson = resultJson,
                completedAt = completedAt,
            )
        if (updated != 1) {
            error("Tool invocation is not running: $provider/$remoteResponseId/$callId")
        }
        return requireNotNull(toolLedgerDao.getInvocation(provider, remoteResponseId, callId))
    }

    suspend fun failToolInvocation(
        provider: String,
        remoteResponseId: String,
        callId: String,
        errorMessage: String,
        completedAt: Long = System.currentTimeMillis(),
    ): ToolInvocationLedgerEntity {
        require(errorMessage.isNotBlank()) { "Tool failure message must not be blank" }
        val updated =
            toolLedgerDao.markFailed(
                provider = provider,
                remoteResponseId = remoteResponseId,
                callId = callId,
                errorMessage = errorMessage,
                completedAt = completedAt,
            )
        if (updated != 1) {
            error("Tool invocation is not running: $provider/$remoteResponseId/$callId")
        }
        return requireNotNull(toolLedgerDao.getInvocation(provider, remoteResponseId, callId))
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        fun from(context: Context): ProviderExecutionRepository =
            ProviderExecutionRepository(
                AppDatabase.getDatabase(context.applicationContext)
            )
    }
}

sealed interface ToolInvocationClaim {
    val invocation: ToolInvocationLedgerEntity

    data class Claimed(override val invocation: ToolInvocationLedgerEntity) :
        ToolInvocationClaim

    data class Completed(override val invocation: ToolInvocationLedgerEntity) :
        ToolInvocationClaim

    data class AlreadyRunning(override val invocation: ToolInvocationLedgerEntity) :
        ToolInvocationClaim

    data class Failed(override val invocation: ToolInvocationLedgerEntity) :
        ToolInvocationClaim
}
