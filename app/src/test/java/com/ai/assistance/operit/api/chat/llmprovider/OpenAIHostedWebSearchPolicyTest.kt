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
    fun relayBindingRejectsConfiguredDomainFilters() {
        listOf(
            OpenAIHostedWebSearchTestFixtures.binding(
                providerContract =
                    OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                endpoint = "https://relay.example/v1/responses",
                allowedDomains = listOf("docs.example"),
            ),
            OpenAIHostedWebSearchTestFixtures.binding(
                providerContract =
                    OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                endpoint = "https://relay.example/v1/responses",
                blockedDomains = listOf("blocked.example"),
            ),
        ).forEach { binding ->
            val error =
                assertThrows(OpenAIHostedWebSearchException::class.java) {
                    OpenAIHostedWebSearchPolicy.validateBinding(
                        binding = binding,
                        compatibilityRecord = null,
                        requireRelayProbe = false,
                    )
                }
            assertEquals(
                OpenAIHostedWebSearchErrorCode.DOMAIN_FILTER_UNSUPPORTED_FOR_RELAY,
                error.code,
            )
            assertEquals("not_sent", error.submissionState)
        }
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

        listOf(
            "Authorization",
            "Content-Type",
            "Accept",
            "User-Agent",
            "Connection",
        ).forEach { headerName ->
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

        val expansionError =
            assertError(OpenAIHostedWebSearchErrorCode.INVALID_ARGUMENT) {
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
        assertEquals("allowed_domains", expansionError.field)
        assertEquals(
            OpenAIHostedWebSearchArgumentReason.INVALID_VALUE,
            expansionError.reason,
        )
        assertEquals("not_sent", expansionError.submissionState)
    }

    @Test
    fun toolCallValidationReportsStableFieldAndReasonBeforeSubmission() {
        val binding = OpenAIHostedWebSearchTestFixtures.binding()
        val cases =
            listOf(
                OpenAIHostedWebSearchRequest(
                    requestId = "ows_blank",
                    query = " ",
                    contextSize = null,
                    allowedDomains = emptyList(),
                    blockedDomains = emptyList(),
                    useConfiguredLocation = false,
                ) to
                    Triple(
                        "query",
                        OpenAIHostedWebSearchArgumentReason.MISSING,
                        OpenAIHostedWebSearchErrorCode.INVALID_ARGUMENT,
                    ),
                OpenAIHostedWebSearchRequest(
                    requestId = "ows_domain",
                    query = "q",
                    contextSize = null,
                    allowedDomains = listOf("https://example.com/path"),
                    blockedDomains = emptyList(),
                    useConfiguredLocation = false,
                ) to
                    Triple(
                        "allowed_domains",
                        OpenAIHostedWebSearchArgumentReason.INVALID_VALUE,
                        OpenAIHostedWebSearchErrorCode.INVALID_ARGUMENT,
                    ),
                OpenAIHostedWebSearchRequest(
                    requestId = "ows_conflict",
                    query = "q",
                    contextSize = null,
                    allowedDomains = listOf("example.com"),
                    blockedDomains = listOf("example.com"),
                    useConfiguredLocation = false,
                ) to
                    Triple(
                        "allowed_domains",
                        OpenAIHostedWebSearchArgumentReason.CONFLICT,
                        OpenAIHostedWebSearchErrorCode.INVALID_ARGUMENT,
                    ),
            )

        cases.forEach { (request, expected) ->
            val error =
                assertError(expected.third) {
                    OpenAIHostedWebSearchPolicy.compileEffectiveRequest(binding, request)
                }
            assertEquals(expected.first, error.field)
            assertEquals(expected.second, error.reason)
            assertEquals("not_sent", error.submissionState)
        }
    }

    private fun assertError(
        expectedCode: OpenAIHostedWebSearchErrorCode,
        block: () -> Unit,
    ): OpenAIHostedWebSearchException {
        val error = assertThrows(OpenAIHostedWebSearchException::class.java, block)
        assertEquals(expectedCode, error.code)
        return error
    }
}
