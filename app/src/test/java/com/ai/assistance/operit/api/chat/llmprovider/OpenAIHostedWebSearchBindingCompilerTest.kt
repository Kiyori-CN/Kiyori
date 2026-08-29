package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
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

        assertEquals("package-key", binding.apiKey)
        assertEquals("gpt-5.6-sol", binding.modelName)
        assertEquals(OpenAIHostedWebSearchReasoningEffort.XHIGH, binding.reasoningEffort)
        assertEquals(OpenAIHostedWebSearchMode.INDEXED, binding.mode)
        assertEquals(60, binding.queueTimeoutSeconds)
        assertEquals(300, binding.timeoutSeconds)
    }

    @Test
    fun packageEnvironmentRequiresItsOwnEndpointAndCredential() {
        assertError(OpenAIHostedWebSearchErrorCode.PACKAGE_ENV_MISSING) {
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "package-key",
                )
            )
        }
        assertError(OpenAIHostedWebSearchErrorCode.API_KEY_MISSING) {
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://api.openai.com/v1/responses",
                )
            )
        }
    }

    @Test
    fun relayStrictUsesThePackageEndpointWithoutProviderConfigIndirection() {
        val binding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                        "RESPONSES_RELAY_STRICT",
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://relay.example/v1/responses",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "relay-key",
                    OpenAIHostedWebSearchContract.ENV_MODEL to "relay-search-model",
                    OpenAIHostedWebSearchContract.ENV_EXTRA_HEADERS_JSON to
                        """{"OpenAI-Project":"project"}""",
                )
            )

        assertEquals(
            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
            binding.providerContract,
        )
        assertEquals("https://relay.example/v1/responses", binding.endpoint)
        assertEquals("relay-search-model", binding.modelName)
        assertEquals(mapOf("OpenAI-Project" to "project"), binding.extraHeaders)
    }

    @Test
    fun relayEndpointAcceptsRootAndV1BaseForms() {
        val rootBinding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                        "RESPONSES_RELAY_STRICT",
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://speed.ai-pixel.online/",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "relay-key",
                    OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-terra",
                )
            )
        val v1Binding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                        "RESPONSES_RELAY_STRICT",
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://speed.ai-pixel.online/v1/",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "relay-key",
                    OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-terra",
                )
            )

        assertEquals("https://speed.ai-pixel.online/v1/responses", rootBinding.endpoint)
        assertEquals(rootBinding.endpoint, v1Binding.endpoint)
    }

    @Test
    fun relayEndpointPreservesCompleteResponsesPathWithoutTrailingSlash() {
        val binding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_PROVIDER_CONTRACT to
                        "RESPONSES_RELAY_STRICT",
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://speed.ai-pixel.online/custom/responses/",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "relay-key",
                    OpenAIHostedWebSearchContract.ENV_MODEL to "gpt-5.6-terra",
                )
            )

        assertEquals(
            "https://speed.ai-pixel.online/custom/responses",
            binding.endpoint,
        )
    }

    @Test
    fun endpointNormalizationRejectsAmbiguousOrUnsafeForms() {
        listOf(
            "https://speed.ai-pixel.online/v1/chat/completions",
            "https://user:pass@speed.ai-pixel.online",
            "https://speed.ai-pixel.online/v1/responses?proxy=1",
            "https://speed.ai-pixel.online/v1/responses#fragment",
        ).forEach { endpoint ->
            assertError(OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID) {
                OpenAIHostedWebSearchPolicy.normalizeResponsesEndpoint(endpoint)
            }
        }
        assertError(OpenAIHostedWebSearchErrorCode.ENDPOINT_NOT_HTTPS) {
            OpenAIHostedWebSearchPolicy.normalizeResponsesEndpoint("http://speed.ai-pixel.online")
        }
    }

    @Test
    fun officialContractStillRequiresTheExactOfficialEndpoint() {
        val binding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://relay.example/v1/responses",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "key",
                )
            )

        assertError(OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID) {
            OpenAIHostedWebSearchPolicy.validateBinding(
                binding = binding,
                compatibilityRecord = null,
                requireRelayProbe = false,
            )
        }
    }

    @Test
    fun compilerUsesSeparateQueueAndHttpBudgets() {
        val binding =
            OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment(
                baseEnvironment(
                    OpenAIHostedWebSearchContract.ENV_RESPONSES_ENDPOINT to
                        "https://api.openai.com/v1/responses",
                    OpenAIHostedWebSearchContract.ENV_API_KEY to "key",
                    OpenAIHostedWebSearchContract.ENV_QUEUE_TIMEOUT_SECONDS to "17",
                    OpenAIHostedWebSearchContract.ENV_TIMEOUT_SECONDS to "241",
                )
            )

        assertEquals(17, binding.queueTimeoutSeconds)
        assertEquals(241, binding.timeoutSeconds)
    }

    private fun baseEnvironment(
        vararg entries: Pair<String, String>,
    ): Map<String, String> =
        buildMap {
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
