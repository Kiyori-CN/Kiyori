package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicEndpointContractTest {
    @Test
    fun officialContract_requiresTheExactAnthropicMessagesEndpoint() {
        listOf(
            "https://api.anthropic.com",
            "https://api.anthropic.com/v1",
            "https://api.anthropic.com/v1/messages",
            "https://api.anthropic.com/v1/messages/",
        ).forEach { endpoint ->
            assertTrue(AnthropicEndpointContract.isOfficial(endpoint))
        }

        listOf(
            "https://api.anthropic.com.evil.example/v1/messages",
            "http://api.anthropic.com/v1/messages",
            "https://api.anthropic.com:8443/v1/messages",
            "https://user@api.anthropic.com/v1/messages",
            "https://api.anthropic.com/v1/messages?proxy=1",
            "https://api.novita.ai/anthropic/v1/messages",
        ).forEach { endpoint ->
            assertFalse(AnthropicEndpointContract.isOfficial(endpoint))
        }
    }
}
