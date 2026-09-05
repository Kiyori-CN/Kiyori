package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.stream.MessageFailureDiagnosticSource
import java.io.IOException

/**
 * Carries the real Chat Completions hop identity through the message failure boundary.
 *
 * Chat Completions has no response-id resume contract. Once the request body may have left the
 * process, the original IOException must remain the cause and the message layer must not create a
 * second POST. The wrapper only adds bounded local diagnostics so audit projection can associate
 * the failure with the last tool follow-up instead of the first hop of the turn.
 */
internal class OpenAIChatTransportFailure(
    val localExecutionId: String?,
    val transportDiagnostics: LlmTransportDiagnostics?,
    cause: IOException,
) : IOException(
    cause.message ?: "OpenAI Chat transport failed",
    cause,
), MessageFailureDiagnosticSource {
    override val messageFailureExecutionId: String?
        get() = localExecutionId

    override val messageFailureDiagnosticCode: String
        get() = transportDiagnostics?.diagnosticCode ?: "OPENAI_CHAT_TRANSPORT_FAILURE"

    override val messageFailurePhase: String
        get() = transportDiagnostics?.stage?.name ?: "TRANSPORT"
}
