package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchRequestCompilerTest {
    @Test
    fun compilesFixedHostedSearchContractAndLiveMode() {
        val payload =
            OpenAIHostedWebSearchRequestCompiler.compile(
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        reasoningEffort = OpenAIHostedWebSearchReasoningEffort.XHIGH,
                        maxOutputTokens = 4096,
                        returnTokenBudget =
                            OpenAIHostedWebSearchReturnTokenBudget.UNLIMITED,
                        additionalInstructions = "Prefer primary sources.",
                    ),
                request =
                    OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                        contextSize = OpenAIHostedWebSearchContextSize.HIGH,
                        allowedDomains = listOf("developers.openai.com"),
                        blockedDomains = listOf("spam.example"),
                        location =
                            OpenAIHostedWebSearchApproximateLocation(
                                country = "US",
                                city = "New York",
                                region = "New York",
                                timezone = "America/New_York",
                            ),
                    ),
            )

        assertEquals("gpt-5.6-luna", payload.getString("model"))
        assertEquals("required", payload.getString("tool_choice"))
        assertEquals(
            "web_search_call.action.sources",
            payload.getJSONArray("include").getString(0),
        )
        assertFalse(payload.getBoolean("store"))
        assertFalse(payload.getBoolean("stream"))
        assertEquals("xhigh", payload.getJSONObject("reasoning").getString("effort"))
        assertEquals(4096, payload.getInt("max_output_tokens"))
        assertTrue(payload.getString("instructions").contains("Prefer primary sources."))

        val tool = payload.getJSONArray("tools").getJSONObject(0)
        assertEquals("web_search", tool.getString("type"))
        assertTrue(tool.getBoolean("external_web_access"))
        assertEquals("high", tool.getString("search_context_size"))
        assertEquals("unlimited", tool.getString("return_token_budget"))
        assertEquals(
            "developers.openai.com",
            tool
                .getJSONObject("filters")
                .getJSONArray("allowed_domains")
                .getString(0),
        )
        assertEquals(
            "approximate",
            tool.getJSONObject("user_location").getString("type"),
        )
    }

    @Test
    fun indexedModeAndOmittedReasoningStayExplicit() {
        val payload =
            OpenAIHostedWebSearchRequestCompiler.compile(
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        mode = OpenAIHostedWebSearchMode.INDEXED,
                        reasoningEffort = OpenAIHostedWebSearchReasoningEffort.OMIT,
                    ),
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
            )

        assertFalse(
            payload
                .getJSONArray("tools")
                .getJSONObject(0)
                .getBoolean("external_web_access")
        )
        assertFalse(payload.has("reasoning"))
        assertFalse(payload.has("max_output_tokens"))
    }

    @Test
    fun serializesOnlyNonEmptyDomainFilterDirections() {
        val allowedOnly =
            OpenAIHostedWebSearchRequestCompiler.compile(
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
                request =
                    OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                        allowedDomains = listOf("developers.openai.com"),
                    ),
            )
        val allowedFilters =
            allowedOnly
                .getJSONArray("tools")
                .getJSONObject(0)
                .getJSONObject("filters")
        assertEquals(
            "developers.openai.com",
            allowedFilters.getJSONArray("allowed_domains").getString(0),
        )
        assertFalse(allowedFilters.has("blocked_domains"))

        val blockedOnly =
            OpenAIHostedWebSearchRequestCompiler.compile(
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
                request =
                    OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                        blockedDomains = listOf("spam.example"),
                    ),
            )
        val blockedFilters =
            blockedOnly
                .getJSONArray("tools")
                .getJSONObject(0)
                .getJSONObject("filters")
        assertFalse(blockedFilters.has("allowed_domains"))
        assertEquals(
            "spam.example",
            blockedFilters.getJSONArray("blocked_domains").getString(0),
        )
    }

    @Test
    fun relayDomainFiltersFailBeforePayloadCreation() {
        val error =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchRequestCompiler.compile(
                    binding =
                        OpenAIHostedWebSearchTestFixtures.binding(
                            providerContract =
                                OpenAIHostedWebSearchProviderContract
                                    .RESPONSES_RELAY_STRICT,
                            endpoint = "https://relay.example/v1/responses",
                        ),
                    request =
                        OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                            allowedDomains = listOf("docs.example")
                        ),
                )
            }

        assertEquals(
            OpenAIHostedWebSearchErrorCode.DOMAIN_FILTER_UNSUPPORTED_FOR_RELAY,
            error.code,
        )
        assertEquals("not_sent", error.submissionState)
    }
}
