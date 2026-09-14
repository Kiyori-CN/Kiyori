package com.ai.assistance.operit.api.chat.llmprovider

import java.io.IOException

/** HTTP/SSE 完结只描述传输；只有协议的生成终态才能允许工具结果进入下一轮。 */
internal object ProviderGenerationTerminalPolicy {
    fun requireLocalSuccess(provider: String, success: Boolean, cancelled: Boolean) {
        if (cancelled) throw com.ai.assistance.operit.util.exceptions.UserCancellationException("$provider generation cancelled")
        if (!success) throw IOException("$provider generation failed")
    }

    fun requireGeminiSuccess(reason: String?) {
        if (reason != "STOP") {
            throw IOException("Gemini generation did not complete successfully: ${reason?.take(80) ?: "missing finishReason"}")
        }
    }

    fun requireAnthropicSuccess(reason: String?) {
        when (reason) {
            "end_turn", "tool_use", "stop_sequence", "refusal" -> Unit
            else -> throw AnthropicProtocolException(
                "Anthropic generation did not complete successfully: ${reason?.take(80) ?: "missing stop_reason"}"
            )
        }
    }
}
