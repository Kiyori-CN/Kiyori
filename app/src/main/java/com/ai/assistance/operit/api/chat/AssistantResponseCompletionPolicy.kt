package com.ai.assistance.operit.api.chat

internal object AssistantTurnDiagnostics {
    const val STREAM_EMPTY_TERMINATION = "AI_STREAM_EMPTY_TERMINATION"
    const val TURN_TERMINAL_MISSING = "AI_TURN_TERMINAL_MISSING"
}

internal class AssistantResponseEmptyTerminationException(
    message: String,
) : IllegalStateException(message)

internal enum class AssistantTurnFailureKind(
    val terminalOutcome: String,
    val diagnosticCode: String?,
) {
    EMPTY_OUTPUT(
        terminalOutcome = "empty_output",
        diagnosticCode = AssistantTurnDiagnostics.STREAM_EMPTY_TERMINATION,
    ),
    PROVIDER_FAILURE(
        terminalOutcome = "provider_failure",
        diagnosticCode = null,
    ),
    TERMINAL_MISSING(
        terminalOutcome = "terminal_missing",
        diagnosticCode = AssistantTurnDiagnostics.TURN_TERMINAL_MISSING,
    ),
}

internal object AssistantTurnFailurePolicy {
    fun classify(error: Throwable): AssistantTurnFailureKind {
        return if (error is AssistantResponseEmptyTerminationException) {
            AssistantTurnFailureKind.EMPTY_OUTPUT
        } else {
            AssistantTurnFailureKind.PROVIDER_FAILURE
        }
    }
}

/**
 * 普通发送只有在当前响应轮确实产生内容时才能进入完成收尾。
 *
 * Provider 明确终态只能证明远端执行结束，不能把零正文投影成一条成功的空消息。
 */
internal object AssistantResponseCompletionPolicy {
    fun requireContent(
        content: String,
        emptyResponseMessage: String,
    ) {
        if (content.isBlank()) {
            throw AssistantResponseEmptyTerminationException(emptyResponseMessage)
        }
    }
}
