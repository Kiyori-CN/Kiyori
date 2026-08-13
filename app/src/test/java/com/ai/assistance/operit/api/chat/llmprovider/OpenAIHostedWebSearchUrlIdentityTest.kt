package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchUrlIdentityTest {
    @Test
    fun preservesPercentEncodedDisplayPathWithoutDoubleEncoding() {
        val identity =
            OpenAIHostedWebSearchUrlIdentity.parse(
                "HTTPS://Docs.Example:443/wiki/123_%28number%29?q=a%2Bb#section"
            )

        assertEquals(
            "https://docs.example/wiki/123_%28number%29?q=a%2Bb#section",
            identity.displayUrl,
        )
        assertEquals(
            "https://docs.example/wiki/123_%28number%29?q=a%2Bb",
            identity.identityKey,
        )
        assertFalse(identity.displayUrl.contains("%2528"))
        assertFalse(identity.displayUrl.contains("%252B"))
    }

    @Test
    fun trackingParametersAndFragmentDoNotChangeIdentity() {
        val base =
            OpenAIHostedWebSearchUrlIdentity.parse(
                "https://docs.example/article?id=7"
            )
        val tracked =
            OpenAIHostedWebSearchUrlIdentity.parse(
                "https://docs.example/article?id=7&utm_source=relay&gclid=abc#result"
            )

        assertEquals(base.identityKey, tracked.identityKey)
        assertTrue(tracked.displayUrl.contains("utm_source=relay"))
        assertTrue(tracked.displayUrl.endsWith("#result"))
    }

    @Test
    fun decodesOnlyUnreservedIdentityBytesAndKeepsQueryPlusLiteral() {
        val identity =
            OpenAIHostedWebSearchUrlIdentity.parse(
                "https://docs.example/%7Euser/a%2Fb?q=a+b&name=%41"
            )

        assertEquals(
            "https://docs.example/~user/a%2Fb?q=a+b&name=A",
            identity.identityKey,
        )
    }

    @Test
    fun rejectsUserInfoAndNonHttpUrls() {
        listOf(
            "https://user@docs.example/private",
            "file:///private/source",
            "javascript:alert(1)",
        ).forEach { rawUrl ->
            val error =
                assertThrows(OpenAIHostedWebSearchException::class.java) {
                    OpenAIHostedWebSearchUrlIdentity.parse(rawUrl)
                }
            assertEquals(OpenAIHostedWebSearchErrorCode.SOURCE_INVALID, error.code)
        }
    }

    @Test
    fun domainPolicyMatchesExactAndSubdomainsAndFailsClosed() {
        val request =
            OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                allowedDomains = listOf("docs.example"),
                blockedDomains = listOf("private.docs.example"),
            )

        assertEquals(
            OpenAIHostedWebSearchDomainPolicyState.REPORTED_ACTIONS_COMPLIANT,
            OpenAIHostedWebSearchDomainPolicy.auditReportedBehavior(
                providerContract =
                    OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL,
                request = request,
                reportedUrls =
                    listOf(
                        OpenAIHostedWebSearchUrlIdentity.parse(
                            "https://guide.docs.example/page"
                        )
                    ),
                actionQueries = listOf("site:docs.example guide"),
            ),
        )

        listOf(
            listOf(
                OpenAIHostedWebSearchUrlIdentity.parse(
                    "https://private.docs.example/page"
                )
            ) to emptyList(),
            emptyList<OpenAIHostedWebSearchUrlIdentity>() to
                listOf("guide site:private.docs.example"),
            listOf(
                OpenAIHostedWebSearchUrlIdentity.parse(
                    "https://outside.example/page"
                )
            ) to emptyList(),
        ).forEach { (reportedUrls, actionQueries) ->
            val error =
                assertThrows(OpenAIHostedWebSearchException::class.java) {
                    OpenAIHostedWebSearchDomainPolicy.auditReportedBehavior(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL,
                        request = request,
                        reportedUrls = reportedUrls,
                        actionQueries = actionQueries,
                    )
                }
            assertEquals(
                OpenAIHostedWebSearchErrorCode.DOMAIN_POLICY_VIOLATION,
                error.code,
            )
        }
    }

    @Test
    fun relayDomainFiltersAreRejectedBeforeSubmission() {
        val error =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchDomainPolicy.requireRequestSupported(
                    providerContract =
                        OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    request =
                        OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                            blockedDomains = listOf("blocked.example")
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
