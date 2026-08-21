package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointCompleterProtocolTest {
    @Test
    fun protocolCompletion_usesTheSelectedWireProtocol() {
        assertEquals(
            "https://proxy.example.com/v1/chat/completions",
            EndpointCompleter.completeEndpoint(
                "https://proxy.example.com",
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
        )
        assertEquals(
            "https://proxy.example.com/v1/responses",
            EndpointCompleter.completeEndpoint(
                "https://proxy.example.com",
                ApiProtocol.OPENAI_RESPONSES,
            ),
        )
        assertEquals(
            "https://proxy.example.com/v1/messages",
            EndpointCompleter.completeEndpoint(
                "https://proxy.example.com",
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
    }

    @Test
    fun protocolCompletion_preservesExplicitAnthropicPathAndNativeEndpoint() {
        assertEquals(
            "https://api.novita.ai/anthropic/v1/messages",
            EndpointCompleter.completeEndpoint(
                "https://api.novita.ai/anthropic",
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
        assertEquals(
            "https://provider.example.com/custom",
            EndpointCompleter.completeEndpoint(
                "https://provider.example.com/custom#",
                ApiProtocol.PROVIDER_NATIVE,
            ),
        )
    }
}
