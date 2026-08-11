package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.dao.ProviderEventAppendOutcome
import com.ai.assistance.operit.data.model.MessageProviderStateEntity
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import com.ai.assistance.operit.data.repository.ProviderExecutionRepository

/**
 * 可恢复 Responses 协调器使用的持久化端口。
 *
 * 生产环境的唯一实现仍然委托 [ProviderExecutionRepository]。这个边界只用于让本地传输故障
 * 注入能够直接驱动生产协调器并观察它要求持久化的状态；它不持有第二份执行状态。
 */
internal interface OpenAIResponsesExecutionPersistence {
    suspend fun createExecution(
        execution: ProviderExecutionEntity,
        initialMessageState: MessageProviderStateEntity,
    )

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
    ): ProviderEventAppendOutcome

    suspend fun updateStatus(
        localExecutionId: String,
        status: ProviderExecutionStatus,
        lastErrorCode: String? = null,
        lastErrorMessage: String? = null,
        completedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis(),
    )

    suspend fun beginResume(
        localExecutionId: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): ProviderExecutionEntity
}

internal class RepositoryOpenAIResponsesExecutionPersistence(
    private val repository: ProviderExecutionRepository,
) : OpenAIResponsesExecutionPersistence {
    override suspend fun createExecution(
        execution: ProviderExecutionEntity,
        initialMessageState: MessageProviderStateEntity,
    ) {
        repository.createExecution(execution, initialMessageState)
    }

    override suspend fun appendEvent(
        localExecutionId: String,
        remoteResponseId: String,
        sequenceNumber: Long,
        eventType: String,
        payloadJson: String,
        nextMessageState: MessageProviderStateEntity,
        terminalEventType: String?,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        completedAt: Long?,
        receivedAt: Long,
    ): ProviderEventAppendOutcome =
        repository.appendEvent(
            localExecutionId = localExecutionId,
            remoteResponseId = remoteResponseId,
            sequenceNumber = sequenceNumber,
            eventType = eventType,
            payloadJson = payloadJson,
            nextMessageState = nextMessageState,
            terminalEventType = terminalEventType,
            lastErrorCode = lastErrorCode,
            lastErrorMessage = lastErrorMessage,
            completedAt = completedAt,
            receivedAt = receivedAt,
        )

    override suspend fun updateStatus(
        localExecutionId: String,
        status: ProviderExecutionStatus,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        completedAt: Long?,
        updatedAt: Long,
    ) {
        repository.updateStatus(
            localExecutionId = localExecutionId,
            status = status,
            lastErrorCode = lastErrorCode,
            lastErrorMessage = lastErrorMessage,
            completedAt = completedAt,
            updatedAt = updatedAt,
        )
    }

    override suspend fun beginResume(
        localExecutionId: String,
        updatedAt: Long,
    ): ProviderExecutionEntity =
        repository.beginResume(
            localExecutionId = localExecutionId,
            updatedAt = updatedAt,
        )
}
