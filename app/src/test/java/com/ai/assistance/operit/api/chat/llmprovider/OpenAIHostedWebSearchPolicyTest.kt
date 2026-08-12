package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAIHostedWebSearchPolicyTest {
    @Test
    fun officialContract_acceptsOnlyFrozenGpt56Allowlist() {
        OpenAIHostedWebSearchContract.OFFICIAL_MODEL_ALLOWLIST.forEach { modelName ->
            OpenAIHostedWebSearchPolicy.validateBinding(
                binding = OpenAIHostedWebSearchTestFixtures.binding(modelName = modelName),
                compatibilityRecord = null,
                requireRelayProbe = true,
            )
        }

        assertError(OpenAIHostedWebSearchErrorCode.MODEL_NOT_ALLOWED) {
            OpenAIHostedWebSearchPolicy.validateBinding(
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(modelName = "gpt-5.6-cyber"),
                compatibilityRecord = null,
                requireRelayProbe = true,
            )
        }
    }

    @Test
    fun officialContract_requiresExactOfficialResponsesEndpoint() {
        listOf(
            "https://api.openai.com.evil.example/v1/responses",
            "http://api.openai.com/v1/responses",
            "https://api.openai.com:8443/v1/responses",
            "https://user@api.openai.com/v1/responses",
            "https://api.openai.com/v1/responses?proxy=1",
        ).forEach { endpoint ->
            val error =
                assertThrows(OpenAIHostedWebSearchException::class.java) {
                    OpenAIHostedWebSearchPolicy.validateBinding(
                        binding =
                            OpenAIHostedWebSearchTestFixtures.binding(endpoint = endpoint),
                        compatibilityRecord = null,
                        requireRelayProbe = true,
                    )
                }
            assertEquals(
                if (endpoint.startsWith("http://")) {
                    OpenAIHostedWebSearchErrorCode.ENDPOINT_NOT_HTTPS
                } else {
                    OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID
                },
                error.code,
            )
        }
    }

    @Test
    fun relayRequiresMatchingExplicitCompatibilityProbe() {
        val binding =
            OpenAIHostedWebSearchTestFixtures.binding(
                providerContract =
                    OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                endpoint = "https://relay.example/v1/responses",
            )

        assertError(OpenAIHostedWebSearchErrorCode.RELAY_PROBE_REQUIRED) {
            OpenAIHostedWebSearchPolicy.validateBinding(
                binding = binding,
                compatibilityRecord = null,
                requireRelayProbe = true,
            )
        }
        assertError(OpenAIHostedWebSearchErrorCode.RELAY_PROBE_STALE) {
            OpenAIHostedWebSearchPolicy.validateBinding(
                binding = binding,
                compatibilityRecord =
                    OpenAIHostedWebSearchCompatibilityRecord(
                        fingerprintDigest = "stale",
                        testedAtEpochMillis = 1L,
                        responseId = "resp_stale",
                        evidenceMode =
                            OpenAIHostedWebSearchEvidenceMode
                                .URL_CITATIONS_AND_ACTION_SOURCES,
                        schemaRevision =
                            OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
                    ),
                requireRelayProbe = true,
            )
        }

        OpenAIHostedWebSearchPolicy.validateBinding(
            binding = binding,
            compatibilityRecord =
                OpenAIHostedWebSearchCompatibilityRecord(
                    fingerprintDigest = binding.compatibilityFingerprint().digest,
                    testedAtEpochMillis = 1L,
                    responseId = "resp_compatible",
                    evidenceMode =
                        OpenAIHostedWebSearchEvidenceMode
                            .URL_CITATIONS_AND_ACTION_SOURCES,
                    schemaRevision =
                        OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
                ),
            requireRelayProbe = true,
        )
    }

    @Test
    fun apiKeyAndHeaderValidationRejectsCredentialInjection() {
        listOf("", " \t", "test-key\r\nInjected: value").forEach { apiKey ->
            assertError(OpenAIHostedWebSearchErrorCode.API_KEY_MISSING) {
                OpenAIHostedWebSearchPolicy.validateBinding(
                    binding = OpenAIHostedWebSearchTestFixtures.binding(apiKey = apiKey),
                    compatibilityRecord = null,
                    requireRelayProbe = false,
                )
            }
        }

        listOf("Authorization", "Content-Type", "Accept", "Connection").forEach { headerName ->
            assertError(OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID) {
                OpenAIHostedWebSearchPolicy.validateBinding(
                    binding =
                        OpenAIHostedWebSearchTestFixtures.binding(
                            extraHeaders = mapOf(headerName to "override"),
                        ),
                    compatibilityRecord = null,
                    requireRelayProbe = false,
                )
            }
        }
    }

    @Test
    fun toolCallDomainsMayNarrowButCannotExpandConfiguredAllowlist() {
        val binding =
            OpenAIHostedWebSearchTestFixtures.binding(
                allowedDomains = listOf("example.com"),
                blockedDomains = listOf("ads.example.net"),
            )
        val narrowed =
            OpenAIHostedWebSearchPolicy.compileEffectiveRequest(
                binding = binding,
                request =
                    OpenAIHostedWebSearchRequest(
                        requestId = "ows_domains",
                        query = "news",
                        contextSize = null,
                        allowedDomains = listOf("news.example.com"),
                        blockedDomains = listOf("tracking.example.net"),
                        useConfiguredLocation = false,
                    ),
            )

        assertEquals(listOf("news.example.com"), narrowed.allowedDomains)
        assertEquals(
            listOf("ads.example.net", "tracking.example.net"),
            narrowed.blockedDomains,
        )

        assertError(OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID) {
            OpenAIHostedWebSearchPolicy.compileEffectiveRequest(
                binding = binding,
                request =
                    OpenAIHostedWebSearchRequest(
                        requestId = "ows_expand",
                        query = "news",
                        contextSize = null,
                        allowedDomains = listOf("unrelated.example"),
                        blockedDomains = emptyList(),
                        useConfiguredLocation = false,
                    ),
            )
        }
    }

    private fun assertError(
        expectedCode: OpenAIHostedWebSearchErrorCode,
        block: () -> Unit,
    ) {
        val error = assertThrows(OpenAIHostedWebSearchException::class.java, block)
        assertEquals(expectedCode, error.code)
    }
}
