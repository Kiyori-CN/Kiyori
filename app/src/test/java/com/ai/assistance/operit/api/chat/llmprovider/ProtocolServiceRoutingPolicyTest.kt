package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolServiceRoutingPolicyTest {
    @Test
    fun otherResponses_keepsSupplierIdentityAndUsesCompatibleResponsesCapabilities() {
        val route =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.OTHER,
                apiProtocol = ApiProtocol.OPENAI_RESPONSES,
            )

        assertEquals(ProtocolServiceKind.OPENAI_RESPONSES, route.serviceKind)
        assertEquals(ApiProviderType.OTHER, route.identityProviderType)
        assertEquals(ApiProviderType.OPENAI_RESPONSES_GENERIC, route.capabilityProviderType)
        assertEquals(ApiProviderType.OPENAI_RESPONSES, route.endpointProviderType)
    }

    @Test
    fun xAiRoutes_keepSupplierIdentityForChatAndResponses() {
        val chatRoute =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.XAI,
                apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            )
        assertEquals(ProtocolServiceKind.OPENAI_CHAT_GENERIC, chatRoute.serviceKind)
        assertEquals(ApiProviderType.XAI, chatRoute.identityProviderType)
        assertEquals(ApiProviderType.OPENAI_GENERIC, chatRoute.capabilityProviderType)

        val responsesRoute =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.XAI,
                apiProtocol = ApiProtocol.OPENAI_RESPONSES,
            )
        assertEquals(ProtocolServiceKind.OPENAI_RESPONSES, responsesRoute.serviceKind)
        assertEquals(ApiProviderType.XAI, responsesRoute.identityProviderType)
        assertEquals(
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            responsesRoute.capabilityProviderType,
        )
    }

    @Test
    fun everyCatalogResponsesProviderUsesTheSharedResponsesService() {
        val responsesProviders =
            ApiProviderType.entries.filter { providerType ->
                ApiProtocol.OPENAI_RESPONSES in
                    ApiProviderConfigs.getSupportedProtocols(providerType)
            }

        responsesProviders.forEach { providerType ->
            val route =
                ProtocolServiceRoutingPolicy.resolve(
                    providerType = providerType,
                    apiProtocol = ApiProtocol.OPENAI_RESPONSES,
                )

            assertEquals(
                "${providerType.name} bypassed the shared Responses service",
                ProtocolServiceKind.OPENAI_RESPONSES,
                route.serviceKind,
            )
            assertEquals(providerType, route.identityProviderType)
        }
    }

    @Test
    fun novitaAnthropic_keepsSupplierIdentityAndUsesAnthropicEndpointRules() {
        val route =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.NOVITA,
                apiProtocol = ApiProtocol.ANTHROPIC_MESSAGES,
            )

        assertEquals(ProtocolServiceKind.ANTHROPIC_MESSAGES, route.serviceKind)
        assertEquals(ApiProviderType.NOVITA, route.identityProviderType)
        assertEquals(ApiProviderType.ANTHROPIC, route.capabilityProviderType)
        assertEquals(ApiProviderType.ANTHROPIC, route.endpointProviderType)
    }

    @Test
    fun providerNative_keepsTheExistingGoogleRoute() {
        val route =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.GOOGLE,
                apiProtocol = ApiProtocol.PROVIDER_NATIVE,
            )

        assertEquals(ProtocolServiceKind.PROVIDER_ROUTED, route.serviceKind)
        assertEquals(ApiProviderType.GOOGLE, route.identityProviderType)
        assertEquals(ApiProviderType.GOOGLE, route.capabilityProviderType)
        assertEquals(ApiProviderType.GOOGLE, route.endpointProviderType)
    }

    @Test
    fun deepSeekChat_keepsTheSpecializedProviderRoute() {
        val route =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.DEEPSEEK,
                apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            )

        assertEquals(ProtocolServiceKind.DEEPSEEK_CHAT, route.serviceKind)
        assertEquals(ApiProviderType.DEEPSEEK, route.identityProviderType)
        assertEquals(ApiProviderType.DEEPSEEK, route.capabilityProviderType)
    }

    @Test
    fun deepSeekResponses_keepsSupplierIdentityButUsesCompatibleAtMostOnceCapabilities() {
        val route =
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.DEEPSEEK,
                apiProtocol = ApiProtocol.OPENAI_RESPONSES,
            )

        assertEquals(ProtocolServiceKind.OPENAI_RESPONSES, route.serviceKind)
        assertEquals(ApiProviderType.DEEPSEEK, route.identityProviderType)
        assertEquals(ApiProviderType.OPENAI_RESPONSES_GENERIC, route.capabilityProviderType)
        assertEquals(ApiProviderType.OPENAI_RESPONSES, route.endpointProviderType)

        val profile =
            ModelCapabilityResolver.resolve(
                providerType = route.capabilityProviderType,
                providerIdentityType = route.identityProviderType,
                modelName = "deepseek-reasoner",
                apiEndpoint = "https://api.deepseek.com/v1/responses",
            )
        assertEquals(
            ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE,
            profile.executionPersistence,
        )
    }

    @Test
    fun anthropicAndGoogleOpenAiChat_useTheGenericOpenAiChatService() {
        listOf(ApiProviderType.ANTHROPIC, ApiProviderType.GOOGLE).forEach { providerType ->
            val route =
                ProtocolServiceRoutingPolicy.resolve(
                    providerType = providerType,
                    apiProtocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                )

            assertEquals(ProtocolServiceKind.OPENAI_CHAT_GENERIC, route.serviceKind)
            assertEquals(providerType, route.identityProviderType)
            assertEquals(ApiProviderType.OPENAI_GENERIC, route.capabilityProviderType)
        }
    }

    @Test
    fun unsupportedProviderProtocolCombination_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolServiceRoutingPolicy.resolve(
                providerType = ApiProviderType.DEEPSEEK,
                apiProtocol = ApiProtocol.PROVIDER_NATIVE,
            )
        }
    }
}
