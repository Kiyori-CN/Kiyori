package com.ai.assistance.operit.core.chat

/**
 * 助手回合的取消来源。
 *
 * 只有明确的用户停止和破坏性历史修改属于预期静默取消。配置刷新、生命周期失效和未知内部
 * 取消都必须在消息层投影为可见错误，否则 Provider 或协程生命周期故障会被伪装成正常结束。
 */
enum class AssistantTurnCancellationSource {
    USER_STOP,
    DESTRUCTIVE_HISTORY_MUTATION,
    APPLICATION_EXIT,
    CONFIGURATION_REFRESH,
    LIFECYCLE_INVALIDATION,
    UNEXPECTED,
}

internal enum class AssistantTurnCancellationTerminal {
    IDLE,
    ERROR,
}

internal data class AssistantTurnCancellationDecision(
    val source: AssistantTurnCancellationSource,
    val terminal: AssistantTurnCancellationTerminal,
)

internal object AssistantTurnCancellationPolicy {
    fun resolve(
        source: AssistantTurnCancellationSource?,
    ): AssistantTurnCancellationDecision {
        val resolvedSource = source ?: AssistantTurnCancellationSource.UNEXPECTED
        val terminal =
            when (resolvedSource) {
                AssistantTurnCancellationSource.USER_STOP,
                AssistantTurnCancellationSource.DESTRUCTIVE_HISTORY_MUTATION,
                -> AssistantTurnCancellationTerminal.IDLE

                AssistantTurnCancellationSource.APPLICATION_EXIT,
                AssistantTurnCancellationSource.CONFIGURATION_REFRESH,
                AssistantTurnCancellationSource.LIFECYCLE_INVALIDATION,
                AssistantTurnCancellationSource.UNEXPECTED,
                -> AssistantTurnCancellationTerminal.ERROR
            }
        return AssistantTurnCancellationDecision(
            source = resolvedSource,
            terminal = terminal,
        )
    }
}

internal data class AssistantTurnCancellationRequest(
    val operationId: Long,
    val source: AssistantTurnCancellationSource,
)

/**
 * 按聊天和回合身份保存一次性取消请求。
 *
 * 新回合开始时只保留与当前 operationId 精确匹配的请求，因此旧回合的取消标记不能污染下一轮。
 */
internal class AssistantTurnCancellationRegistry {
    private val requests = mutableMapOf<String, AssistantTurnCancellationRequest>()

    @Synchronized
    fun startOperation(chatKey: String, operationId: Long?) {
        val existing = requests[chatKey]
        if (operationId == null || existing?.operationId != operationId) {
            requests.remove(chatKey)
        }
    }

    @Synchronized
    fun request(
        chatKey: String,
        operationId: Long?,
        source: AssistantTurnCancellationSource,
    ) {
        if (operationId == null) {
            return
        }
        requests[chatKey] =
            AssistantTurnCancellationRequest(
                operationId = operationId,
                source = source,
            )
    }

    @Synchronized
    fun consume(chatKey: String, operationId: Long): AssistantTurnCancellationSource? {
        val request = requests[chatKey] ?: return null
        if (request.operationId != operationId) {
            return null
        }
        requests.remove(chatKey)
        return request.source
    }
}
