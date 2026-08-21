package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelListProtocolRoutingPolicyTest {
    @Test
    fun officialAnthropicMessages_usesAnthropicAuthenticationAndResponseParsing() {
        assertEquals(
            ApiProviderType.ANTHROPIC,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.ANTHROPIC,
                apiProtocol = ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
    }

    @Test
    fun compatibleAnthropicMessages_keepSupplierModelListAuthenticationAndParsing() {
        assertEquals(
            ApiProviderType.NOVITA,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.NOVITA,
                apiProtocol = ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
        assertEquals(
            ApiProviderType.DEEPSEEK,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.DEEPSEEK,
                apiProtocol = ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
    }

    @Test
    fun genericOpenAiChatCompatibility_usesOpenAiModelListShape() {
        assertEquals(
            ApiProviderType.OPENAI_GENERIC,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.ANTHROPIC,
                apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
        )
        assertEquals(
            ApiProviderType.OPENAI_GENERIC,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.GOOGLE,
                apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
        )
    }

    @Test
    fun openAiProtocols_keepTheSelectedSupplierIdentity() {
        assertEquals(
            ApiProviderType.OTHER,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.OTHER,
                apiProtocol = ApiProtocol.OPENAI_RESPONSES,
            ),
        )
        assertEquals(
            ApiProviderType.NOVITA,
            ModelListProtocolRoutingPolicy.requestProviderType(
                providerType = ApiProviderType.NOVITA,
                apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
        )
    }

    @Test
    fun knownCompatibleEndpoint_usesTheConfiguredSupplierModelListEndpoint() {
        val route =
            ModelListProtocolRoutingPolicy.resolve(
                providerType = ApiProviderType.DEEPSEEK,
                apiProtocol = ApiProtocol.ANTHROPIC_MESSAGES,
                apiEndpoint = "https://api.deepseek.com/anthropic/v1/messages",
            )

        assertEquals(ApiProviderType.DEEPSEEK, route.requestProviderType)
        assertEquals(
            "https://api.deepseek.com/v1/models",
            route.configuredModelListEndpoint,
        )
    }
}
