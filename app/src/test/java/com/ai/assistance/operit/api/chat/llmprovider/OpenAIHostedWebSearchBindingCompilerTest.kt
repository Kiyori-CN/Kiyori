package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAIHostedWebSearchBindingCompilerTest {
    @Test
    fun packageEnvironmentUsesOnlyPackageTransportAndSearchSettings() {
        val binding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://api.openai.com/v1/responses",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "package-key",
                    OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-sol",
                    OpenAIHostedWebSearchContract.ENV_REASONING_EFFORT to "xhigh",
                    OpenAIHostedWebSearchContract.ENV_EXTERNAL_WEB_ACCESS to "false",
                )
            )

        assertEquals(OpenAIHostedWebSearchConfigSource.PACKAGE_ENV, binding.configSource)
        assertNull(binding.modelConfigId)
        assertEquals("package-key", binding.apiKey)
        assertEquals("gpt-5.6-sol", binding.modelName)
        assertEquals(OpenAIHostedWebSearchReasoningEffort.XHIGH, binding.reasoningEffort)
        assertEquals(OpenAIHostedWebSearchMode.INDEXED, binding.mode)
    }

    @Test
    fun modelConfigUsesFixedConfigTransportButKeepsPluginSearchPolicy() {
        val config =
            ModelConfigData(
                id = "responses-config",
                name = "Responses",
                apiKey = "model-key",
                apiEndpoint = "https://api.openai.com",
                modelName = "gpt-5.6-sol,gpt-5.6-luna",
                apiProviderType = ApiProviderType.OPENAI_RESPONSES,
                apiProviderTypeId = ApiProviderType.OPENAI_RESPONSES.name,
                customHeaders = """{"OpenAI-Project":"project"}""",
                requestLimitPerMinute = 7,
                maxConcurrentRequests = 2,
            )
        val binding =
            OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                environment =
                    baseEnvironment(
                        OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE to "MODEL_CONFIG",
                        OpenAIHostedWebSearchContract.ENV_MODEL_CONFIG_ID to config.id,
                        OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-sol",
                        OpenAIHostedWebSearchContract.ENV_CONTEXT_SIZE to "high",
                    ),
                modelConfig = config,
                selectedApiKey = "selected-key",
            )

        assertEquals(OpenAIHostedWebSearchConfigSource.MODEL_CONFIG, binding.configSource)
        assertEquals(config.id, binding.modelConfigId)
        assertEquals("https://api.openai.com/v1/responses", binding.endpoint)
        assertEquals("selected-key", binding.apiKey)
        assertEquals(mapOf("OpenAI-Project" to "project"), binding.extraHeaders)
        assertEquals(OpenAIHostedWebSearchContextSize.HIGH, binding.contextSize)
        assertEquals(7, binding.modelConfigRequestsPerMinute)
        assertEquals(2, binding.modelConfigMaxConcurrentRequests)
    }

    @Test
    fun modelConfigRejectsProviderContractMismatchAndMissingExactModel() {
        val generic =
            ModelConfigData(
                id = "generic",
                name = "Generic",
                apiEndpoint = "https://relay.example/v1/responses",
                modelName = "relay-search-model",
                apiProviderType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
                apiProviderTypeId = ApiProviderType.OPENAI_RESPONSES_GENERIC.name,
            )
        assertError(OpenAIHostedWebSearchErrorCode.PROVIDER_NOT_RESPONSES) {
            OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                environment =
                    baseEnvironment(
                        OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE to "MODEL_CONFIG",
                        OpenAIHostedWebSearchContract.ENV_MODEL to "relay-search-model",
                    ),
                modelConfig = generic,
                selectedApiKey = "key",
            )
        }

        val official = generic.copy(
            apiProviderType = ApiProviderType.OPENAI_RESPONSES,
            apiProviderTypeId = ApiProviderType.OPENAI_RESPONSES.name,
            apiEndpoint = "https://api.openai.com/v1/responses",
        )
        assertError(OpenAIHostedWebSearchErrorCode.MODEL_NOT_IN_CONFIG) {
            OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                environment =
                    baseEnvironment(
                        OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE to "MODEL_CONFIG",
                        OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-luna",
                    ),
                modelConfig = official,
                selectedApiKey = "key",
            )
        }
    }

    @Test
    fun keyPoolSelectionHasNoSingleKeySubstitutionPath() {
        val config =
            ModelConfigData(
                id = "pool",
                name = "Pool",
                apiKey = "single-key-must-not-be-used",
                useMultipleApiKeys = true,
                currentKeyIndex = 3,
                apiKeyPool =
                    listOf(
                        ApiKeyInfo(
                            id = "disabled",
                            key = "disabled-key",
                            isEnabled = false,
                        ),
                        ApiKeyInfo(
                            id = "available",
                            key = "available-key",
                            availabilityStatus = ApiKeyAvailabilityStatus.AVAILABLE,
                        ),
                        ApiKeyInfo(
                            id = "unavailable",
                            key = "unavailable-key",
                            availabilityStatus = ApiKeyAvailabilityStatus.UNAVAILABLE,
                        ),
                    ),
            )
        val selection = OpenAIHostedWebSearchModelConfigKeySelector.select(config)

        assertEquals("available-key", selection.apiKey)
        assertEquals(0, selection.nextIndex)

        assertError(OpenAIHostedWebSearchErrorCode.API_KEY_MISSING) {
            OpenAIHostedWebSearchModelConfigKeySelector.select(
                config.copy(
                    apiKeyPool =
                        listOf(
                            ApiKeyInfo(
                                id = "unavailable",
                                key = "unavailable-key",
                                availabilityStatus = ApiKeyAvailabilityStatus.UNAVAILABLE,
                            )
                        )
                )
            )
        }
    }

    @Test
    fun relayStrictAcceptsBothResponsesProviderEnumsButOfficialStillRequiresOfficialEndpoint() {
        listOf(
            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
        ).forEach { providerType ->
            val config =
                ModelConfigData(
                    id = providerType.name,
                    name = providerType.name,
                    apiEndpoint = "https://relay.example/v1/responses",
                    modelName = "gpt-5.6-sol",
                    apiProviderType = providerType,
                    apiProviderTypeId = providerType.name,
                )
            val relayBinding =
                OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                    environment =
                        baseEnvironment(
                            OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE to "MODEL_CONFIG",
                            OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                                "RESPONSES_RELAY_STRICT",
                            OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-sol",
                        ),
                    modelConfig = config,
                    selectedApiKey = "relay-key",
                )

            assertEquals(
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                relayBinding.providerContract,
            )
            assertEquals("https://relay.example/v1/responses", relayBinding.endpoint)
        }

        val officialProviderWithRelayEndpoint =
            ModelConfigData(
                id = "official-enum-relay-endpoint",
                name = "Official enum relay endpoint",
                apiEndpoint = "https://relay.example/v1/responses",
                modelName = "gpt-5.6-sol",
                apiProviderType = ApiProviderType.OPENAI_RESPONSES,
                apiProviderTypeId = ApiProviderType.OPENAI_RESPONSES.name,
            )
        val officialBinding =
            OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                environment =
                    baseEnvironment(
                        OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE to "MODEL_CONFIG",
                        OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-sol",
                    ),
                modelConfig = officialProviderWithRelayEndpoint,
                selectedApiKey = "key",
            )
        assertError(OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID) {
            OpenAIHostedWebSearchPolicy.validateBinding(
                binding = officialBinding,
                compatibilityRecord = null,
                requireRelayProbe = false,
            )
        }

        val nonResponsesConfig =
            officialProviderWithRelayEndpoint.copy(
                id = "deepseek",
                apiProviderType = ApiProviderType.DEEPSEEK,
                apiProviderTypeId = ApiProviderType.DEEPSEEK.name,
            )
        assertError(OpenAIHostedWebSearchErrorCode.PROVIDER_NOT_RESPONSES) {
            OpenAIHostedWebSearchBindingCompiler.compileModelConfig(
                environment =
                    baseEnvironment(
                        OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE to "MODEL_CONFIG",
                        OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                            "RESPONSES_RELAY_STRICT",
                        OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-sol",
                    ),
                modelConfig = nonResponsesConfig,
                selectedApiKey = "key",
            )
        }
    }

    private fun baseEnvironment(
        vararg entries: Pair<String, String>,
    ): Map<String, String> =
        buildMap {
            put(OpenAIHostedWebSearchContract.ENV_CONFIG_SOURCE, "PACKAGE_ENV")
            put(
                OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT,
                "RESPONSES_HOSTED_OFFICIAL",
            )
            entries.forEach { (name, value) -> put(name, value) }
        }

    private fun assertError(
        expectedCode: OpenAIHostedWebSearchErrorCode,
        block: () -> Unit,
    ) {
        val error = assertThrows(OpenAIHostedWebSearchException::class.java, block)
        assertEquals(expectedCode, error.code)
    }
}
