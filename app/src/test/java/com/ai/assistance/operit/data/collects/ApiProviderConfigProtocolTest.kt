package com.ai.assistance.operit.data.collects

import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProviderConfigProtocolTest {
    @Test
    fun novitaAnthropicProtocol_selectsTheAnthropicEndpoint() {
        assertEquals(
            "https://api.novita.ai/anthropic/v1/messages",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.NOVITA,
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
        assertEquals(
            listOf(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
            ApiProviderConfigs.getSupportedProtocols(ApiProviderType.NOVITA),
        )
    }

    @Test
    fun openAiCanonicalSupplier_selectsTheProtocolSpecificDefaults() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.OPENAI,
                protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
        )
        assertEquals(
            "https://api.openai.com/v1/responses",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.OPENAI,
                protocol = ApiProtocol.OPENAI_RESPONSES,
            ),
        )
        assertTrue(
            ApiProviderConfigs.getEndpointOptions(
                providerType = ApiProviderType.OPENAI,
                protocol = ApiProtocol.OPENAI_RESPONSES,
            ) == null,
        )
    }

    @Test
    fun everyBuiltInProvider_declaresAConcreteDefaultProtocol() {
        ApiProviderType.entries.forEach { providerType ->
            val supportedProtocols = ApiProviderConfigs.getSupportedProtocols(providerType)
            assertTrue("${providerType.name} has no protocol", supportedProtocols.isNotEmpty())
            assertTrue(
                "${providerType.name} default protocol is not supported",
                ApiProviderConfigs.getDefaultProtocol(providerType) in supportedProtocols,
            )
        }
    }

    @Test
    fun everyDeclaredDefaultEndpoint_alreadyMatchesItsProtocolContract() {
        ApiProviderType.entries.forEach { providerType ->
            ApiProviderConfigs.getSupportedProtocols(providerType).forEach { protocol ->
                val endpoint =
                    ApiProviderConfigs.getDefaultApiEndpoint(
                        providerType = providerType,
                        protocol = protocol,
                    )
                if (endpoint.isNotBlank()) {
                    assertEquals(
                        "${providerType.name}/${protocol.name} endpoint was incomplete",
                        endpoint,
                        EndpointCompleter.completeEndpoint(endpoint, protocol),
                    )
                }
            }
        }
    }

    @Test
    fun legacyProtocolInference_matchesEachBuiltInProviderDefault() {
        ApiProviderType.entries.forEach { providerType ->
            assertEquals(
                "${providerType.name} legacy protocol differs from its catalog default",
                ApiProviderConfigs.getDefaultProtocol(providerType),
                ApiProtocol.fromProviderType(providerType),
            )
        }
    }

    @Test
    fun deepSeek_declaresChatResponsesAndAnthropicWithProtocolSpecificEndpoints() {
        assertEquals(
            listOf(
                ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                ApiProtocol.OPENAI_RESPONSES,
                ApiProtocol.ANTHROPIC_MESSAGES,
            ),
            ApiProviderConfigs.getSupportedProtocols(ApiProviderType.DEEPSEEK),
        )
        assertEquals(
            "https://api.deepseek.com/v1/responses",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.DEEPSEEK,
                protocol = ApiProtocol.OPENAI_RESPONSES,
            ),
        )
        assertEquals(
            "https://api.deepseek.com/anthropic/v1/messages",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.DEEPSEEK,
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
            ),
        )
    }

    @Test
    fun multiProtocolProviders_matchTheReviewedProtocolMatrix() {
        val expected =
            linkedMapOf(
                ApiProviderType.OPENAI to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                    ),
                ApiProviderType.ANTHROPIC to
                    listOf(
                        ApiProtocol.ANTHROPIC_MESSAGES,
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                    ),
                ApiProviderType.GOOGLE to
                    listOf(
                        ApiProtocol.PROVIDER_NATIVE,
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                    ),
                ApiProviderType.XAI to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                    ),
                ApiProviderType.OPENROUTER to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                    ),
                ApiProviderType.NOVITA to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.DEEPSEEK to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.ALIYUN to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                    ),
                ApiProviderType.ZHIPU to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.MIMO to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.SILICONFLOW to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.INFINIAI to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.DOUBAO to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                    ),
                ApiProviderType.LMSTUDIO to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.OLLAMA to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
                ApiProviderType.OPENAI_LOCAL to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                    ),
                ApiProviderType.OTHER to
                    listOf(
                        ApiProtocol.OPENAI_CHAT_COMPLETIONS,
                        ApiProtocol.OPENAI_RESPONSES,
                        ApiProtocol.ANTHROPIC_MESSAGES,
                    ),
            )

        val actual =
            ApiProviderType.entries
                .associateWith(ApiProviderConfigs::getSupportedProtocols)
                .filterValues { protocols -> protocols.size > 1 }
        assertEquals(expected, actual)
    }

    @Test
    fun automaticDetection_prefersKnownEndpointsAndExplicitPaths() {
        assertEquals(
            ProviderProtocolDetectionResult.Resolved(
                protocol = ApiProtocol.OPENAI_RESPONSES,
                source = ProviderProtocolDetectionSource.KNOWN_ENDPOINT,
            ),
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.DEEPSEEK,
                apiEndpoint = "https://api.deepseek.com/v1/responses",
            ),
        )
        assertEquals(
            ProviderProtocolDetectionResult.Resolved(
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                source = ProviderProtocolDetectionSource.EXPLICIT_ENDPOINT_PATH,
            ),
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.OTHER,
                apiEndpoint = "https://gateway.example.com/custom/messages",
            ),
        )
        assertEquals(
            ProviderProtocolDetectionResult.RequiresManualSelection,
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.OTHER,
                apiEndpoint = "https://gateway.example.com/v1",
            ),
        )
    }

    @Test
    fun automaticDetection_usesTheProviderDefaultWhenEndpointIsEmpty() {
        assertEquals(
            ProviderProtocolDetectionResult.Resolved(
                protocol = ApiProtocol.PROVIDER_NATIVE,
                source = ProviderProtocolDetectionSource.PROVIDER_DEFAULT,
            ),
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.GOOGLE,
                apiEndpoint = "",
            ),
        )
    }

    @Test
    fun automaticDetection_rejectsSharedBaseEndpointsAndAcceptsUniqueBaseEvidence() {
        assertEquals(
            ProviderProtocolDetectionResult.RequiresManualSelection,
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.OPENAI,
                apiEndpoint = "https://api.openai.com/v1",
            ),
        )
        assertEquals(
            ProviderProtocolDetectionResult.RequiresManualSelection,
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.XAI,
                apiEndpoint = "https://api.x.ai/v1/",
            ),
        )
        assertEquals(
            ProviderProtocolDetectionResult.Resolved(
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                source = ProviderProtocolDetectionSource.KNOWN_BASE_ENDPOINT,
            ),
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.DEEPSEEK,
                apiEndpoint = "https://api.deepseek.com/anthropic/v1",
            ),
        )
        assertEquals(
            ProviderProtocolDetectionResult.Resolved(
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                source = ProviderProtocolDetectionSource.KNOWN_BASE_ENDPOINT,
            ),
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.DEEPSEEK,
                apiEndpoint = "https://api.deepseek.com/anthropic/v1/#",
            ),
        )
        assertEquals(
            ProviderProtocolDetectionResult.RequiresManualSelection,
            ApiProviderConfigs.detectProtocol(
                providerType = ApiProviderType.OTHER,
                apiEndpoint = "https://gateway.example.com/v1",
            ),
        )
    }

    @Test
    fun xAi_declaresChatResponsesAndModelListEndpoints() {
        assertEquals(
            "https://api.x.ai/v1/chat/completions",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.XAI,
                protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            ),
        )
        assertEquals(
            "https://api.x.ai/v1/responses",
            ApiProviderConfigs.getDefaultApiEndpoint(
                providerType = ApiProviderType.XAI,
                protocol = ApiProtocol.OPENAI_RESPONSES,
            ),
        )
        assertEquals(
            "https://api.x.ai/v1/models",
            ApiProviderConfigs.getModelListEndpoint(
                providerType = ApiProviderType.XAI,
                protocol = ApiProtocol.OPENAI_RESPONSES,
                apiEndpoint = "https://api.x.ai/v1/responses",
            ),
        )
    }

    @Test
    fun modelListEndpoint_isProtocolSpecificForKnownSupplierEndpoints() {
        assertEquals(
            "https://api.deepseek.com/v1/models",
            ApiProviderConfigs.getModelListEndpoint(
                providerType = ApiProviderType.DEEPSEEK,
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                apiEndpoint = "https://api.deepseek.com/anthropic/v1/messages",
            ),
        )
        assertNull(
            ApiProviderConfigs.getModelListEndpoint(
                providerType = ApiProviderType.OTHER,
                protocol = ApiProtocol.ANTHROPIC_MESSAGES,
                apiEndpoint = "https://gateway.example.com/v1/messages",
            )
        )
    }
}
