package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchResponseParserTest {
    @Test
    fun parsesActionsMultipleTextPartsDeduplicatedSourcesCitationsAndUsage() {
        val response =
            JSONObject()
                .put("id", "resp_web_search")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "search")
                                        .put(
                                            "queries",
                                            JSONArray()
                                                .put("Kiyori")
                                                .put("Kiyori Android"),
                                        )
                                        .put(
                                            "sources",
                                            JSONArray()
                                                .put(
                                                    JSONObject()
                                                        .put("type", "url")
                                                        .put(
                                                            "url",
                                                            "HTTPS://Example.COM:443/first",
                                                        )
                                                        .put("title", "First source"),
                                                )
                                                .put(
                                                    JSONObject()
                                                        .put("type", "url")
                                                        .put(
                                                            "url",
                                                            "https://example.com/first",
                                                        ),
                                                )
                                                .put(
                                                    JSONObject()
                                                        .put("type", "url")
                                                        .put(
                                                            "url",
                                                            "https://second.example/report",
                                                        )
                                                        .put("title", "Second source"),
                                                ),
                                        ),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            outputText(
                                                text = "Alpha",
                                                url = "https://example.com/first",
                                                title = "",
                                                startIndex = 0,
                                                endIndex = 5,
                                            ),
                                        )
                                        .put(
                                            outputText(
                                                text = "Beta",
                                                url = "https://second.example/report",
                                                title = "Second source",
                                                startIndex = 0,
                                                endIndex = 4,
                                            ),
                                        ),
                                ),
                        ),
                )
                .put(
                    "usage",
                    JSONObject()
                        .put("input_tokens", 100)
                        .put(
                            "input_tokens_details",
                            JSONObject().put("cached_tokens", 25),
                        )
                        .put("output_tokens", 12),
                )

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )

        assertEquals("Alpha\nBeta", result.answer)
        assertEquals("Alpha[S1]\nBeta[S2]", result.answerWithSourceMarkers)
        assertEquals(listOf("Kiyori", "Kiyori Android"), result.searchActions.map { it.query })
        assertEquals(listOf("S1", "S2"), result.allSources.map { it.sourceId })
        assertEquals("First source", result.allSources[0].title)
        assertEquals("Second source", result.allSources[1].title)
        assertEquals(0, result.citations[0].startIndex)
        assertEquals(5, result.citations[0].endIndex)
        assertEquals(6, result.citations[1].startIndex)
        assertEquals(10, result.citations[1].endIndex)
        assertEquals(100, result.usage.inputTokens)
        assertEquals(25, result.usage.cachedInputTokens)
        assertEquals(12, result.usage.outputTokens)
        assertEquals(1, result.usage.webSearchCalls)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun relayKeepsValidUrlAfterMalformedSourceAndReportsDiagnostics() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                val action =
                    getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONObject("action")
                action.put(
                    "sources",
                    JSONArray()
                        .put(JSONObject().put("type", "url"))
                        .put(
                            JSONObject()
                                .put("type", "url")
                                .put("url", "https://example.com/source")
                                .put("title", "Example")
                        ),
                )
                put(
                    "usage",
                    JSONObject()
                        .put("input_tokens", 10)
                        .put("output_tokens", 4),
                )
            }

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(2, parsed.diagnostics.totalActionSourceCount)
        assertEquals(1, parsed.diagnostics.validActionSourceUrlCount)
        assertEquals(1, parsed.diagnostics.invalidActionSourceCount)
        assertEquals(
            listOf(
                "ACTION_SOURCE_INVALID:output=0,source=0,reason=missing_url",
                "ACTION_SOURCES_PARTIAL",
            ),
            parsed.result.warnings,
        )
        assertEquals("https://example.com/source", parsed.result.allSources.single().url)
    }

    @Test
    fun relayAcceptsUrlSourceWithMissingTypeAsWarning() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONObject("action")
                    .getJSONArray("sources")
                    .getJSONObject(0)
                    .remove("type")
                put(
                    "usage",
                    JSONObject()
                        .put("input_tokens", 10)
                        .put("output_tokens", 4),
                )
            }

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(1, parsed.diagnostics.validActionSourceUrlCount)
        assertEquals(
            listOf(
                "ACTION_SOURCE_COMPATIBILITY:output=0,source=0," +
                    "reason=missing_type_accepted_for_relay",
            ),
            parsed.result.warnings,
        )
    }

    @Test
    fun officialRetainsValidCitationWhenReturnedActionSourceIsMalformed() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONObject("action")
                    .getJSONArray("sources")
                    .getJSONObject(0)
                    .remove("type")
            }

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )

        assertEquals(OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS, result.evidenceMode)
        assertEquals("https://example.com/source", result.citations.single().url)
        assertEquals(
            listOf(
                "ACTION_SOURCE_INVALID:output=0,source=0,reason=missing_type",
                "ACTION_SOURCES_PARTIAL",
                "USAGE_MISSING",
            ),
            result.warnings,
        )
    }

    @Test
    fun knownRealtimeFeedSourceIsRetainedWithoutInventingUrl() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONObject("action")
                    .getJSONArray("sources")
                    .put(JSONObject().put("type", "oai-weather"))
            }

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )

        assertEquals(2, parsed.diagnostics.totalActionSourceCount)
        assertEquals(1, parsed.diagnostics.validActionSourceUrlCount)
        assertEquals(1, parsed.diagnostics.structuredFeedSourceCount)
        assertEquals(listOf("url", "oai-weather"), parsed.result.allSources.map { it.type })
        assertEquals(null, parsed.result.allSources[1].url)
    }

    @Test
    fun relayKeepsCitationEvidenceWhenReturnedActionSourcesAreAllInvalid() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                val sources =
                    getJSONArray("output")
                        .getJSONObject(0)
                        .getJSONObject("action")
                        .getJSONArray("sources")
                sources.remove(0)
                sources.put(JSONObject().put("type", "url"))
                sources.put(JSONObject().put("type", "oai-unknown"))
                getJSONArray("output")
                    .getJSONObject(1)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getJSONArray("annotations")
                    .getJSONObject(0)
                    .put("url", "https://example.com/source")
            }

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS, parsed.result.evidenceMode)
        assertEquals("Evidence[S1]", parsed.result.answerWithSourceMarkers)
        assertEquals("https://example.com/source", parsed.result.allSources.single().url)
        assertEquals(2, parsed.diagnostics.invalidActionSourceCount)
        assertTrue(parsed.result.warnings.contains("ACTION_SOURCES_PARTIAL"))
    }

    @Test
    fun relayAcceptsCitationOnlyResponseWithoutInventingActionSources() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONObject("action")
                    .remove("sources")
            }

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS, result.evidenceMode)
        assertEquals("Evidence[S1]", result.answerWithSourceMarkers)
        assertEquals(1, result.citations.size)
        assertEquals(1, result.allSources.size)
        assertTrue(result.warnings.contains("ACTION_SOURCES_MISSING"))
    }

    @Test
    fun relayRetainsCitationsWhenActionSourcesDoNotContainEveryCitation() {
        val response =
            JSONObject()
                .put("id", "resp_partial_action_sources")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "search")
                                        .put(
                                            "queries",
                                            JSONArray()
                                                .put("latest official announcement")
                                                .put("official news"),
                                        )
                                        .put(
                                            "sources",
                                            JSONArray()
                                                .put(
                                                    JSONObject()
                                                        .put("type", "url")
                                                        .put(
                                                            "url",
                                                            "https://example.com/news/",
                                                        )
                                                        .put("title", "News"),
                                                )
                                                .put(
                                                    JSONObject()
                                                        .put("type", "url")
                                                        .put(
                                                            "url",
                                                            "https://example.com/archive/",
                                                        )
                                                        .put("title", "Archive"),
                                                ),
                                        ),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "open_page")
                                        .put("url", "https://example.com/news/"),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            outputText(
                                                text = "Latest announcement",
                                                url =
                                                    "https://example.com/" +
                                                        "latest-announcement/",
                                                title = "Latest",
                                                startIndex = 0,
                                                endIndex = 19,
                                            ),
                                        )
                                        .put(
                                            outputText(
                                                text = "News index",
                                                url = "https://example.com/news/",
                                                title = "News",
                                                startIndex = 0,
                                                endIndex = 10,
                                            ),
                                        ),
                                ),
                        ),
                )

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES,
            parsed.result.evidenceMode,
        )
        assertEquals(2, parsed.result.citations.size)
        assertEquals(
            listOf(
                "CITATION_NOT_IN_ACTION_SOURCES",
                "USAGE_MISSING",
            ),
            parsed.result.warnings,
        )
        assertEquals(
            OpenAIHostedWebSearchActionSourceCoverage.COMPLETE,
            parsed.result.sourceDiagnostics.actionSourceCoverage,
        )
        assertEquals(
            listOf("https://example.com/latest-announcement/"),
            parsed.result.sourceDiagnostics.citationsMissingFromActionSources,
        )
        assertEquals(
            listOf("https://example.com/news/"),
            parsed.result.sourceDiagnostics.openPageUrls,
        )
        assertEquals(
            emptyList<String>(),
            parsed.result.sourceDiagnostics.allowedDomains,
        )
        assertEquals(1, parsed.diagnostics.citationMissingFromActionSourceCount)
    }

    @Test
    fun relayReportsMissingSourcesOnOnlyTheSearchActionsThatOmitThem() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                val output = getJSONArray("output")
                output.put(
                        JSONObject()
                            .put("type", "web_search_call")
                            .put(
                                "action",
                                JSONObject()
                                    .put("type", "search")
                                    .put("query", "second search"),
                            )
                    )
                output.put(output.remove(1))
            }

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(
            OpenAIHostedWebSearchActionSourceCoverage.PARTIAL,
            parsed.result.sourceDiagnostics.actionSourceCoverage,
        )
        assertEquals(
            listOf(1),
            parsed.result.sourceDiagnostics.missingSourceActionIndexes,
        )
        assertEquals(1, parsed.diagnostics.missingSourceActionCount)
        assertTrue(parsed.result.warnings.contains("ACTION_SOURCES_PARTIAL"))
    }

    @Test
    fun canonicalIdentityPreservesDisplayUrlAndRemovesTrackingOnlyMismatch() {
        val actionUrl = "https://docs.example/wiki/123_%28number%29"
        val citationUrl =
            "$actionUrl?utm_source=relay&gclid=fixture#answer"
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = actionUrl,
                sourceTitle = "Example",
            ).apply {
                getJSONArray("output")
                    .getJSONObject(1)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getJSONArray("annotations")
                    .getJSONObject(0)
                    .put("url", citationUrl)
            }

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )

        assertEquals(1, result.allSources.size)
        assertFalse(result.allSources.single().url.orEmpty().contains("%2528"))
        assertFalse(result.warnings.contains("CITATION_NOT_IN_ACTION_SOURCES"))
        assertTrue(result.sourceDiagnostics.citationsMissingFromActionSources.isEmpty())
    }

    @Test
    fun openPageIdentitySatisfiesCitationCrossChannelComparison() {
        val citationUrl = "https://docs.example/article?utm_source=relay"
        val response =
            JSONObject()
                .put("id", "resp_open_page")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "search")
                                        .put("query", "fixture"),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "open_page")
                                        .put("url", "https://docs.example/article"),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            outputText(
                                                text = "Evidence",
                                                url = citationUrl,
                                                title = "Example",
                                                startIndex = 0,
                                                endIndex = 8,
                                            )
                                        ),
                                ),
                        ),
                )

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertFalse(result.warnings.contains("CITATION_NOT_IN_ACTION_SOURCES"))
        assertTrue(result.sourceDiagnostics.citationsMissingFromActionSources.isEmpty())
        assertEquals(
            listOf("https://docs.example/article"),
            result.sourceDiagnostics.openPageUrls,
        )
    }

    @Test
    fun officialDomainPolicyRejectsReportedUrlAndSiteConstraintViolations() {
        val request =
            OpenAIHostedWebSearchTestFixtures.effectiveRequest(
                allowedDomains = listOf("docs.example"),
                blockedDomains = listOf("private.docs.example"),
            )
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://private.docs.example/source",
                sourceTitle = "Private",
            )

        val urlError =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchResponseParser.parse(
                    responseJson = response,
                    request = request,
                    binding = OpenAIHostedWebSearchTestFixtures.binding(),
                )
            }
        assertEquals(
            OpenAIHostedWebSearchErrorCode.DOMAIN_POLICY_VIOLATION,
            urlError.code,
        )

        response
            .getJSONArray("output")
            .getJSONObject(0)
            .getJSONObject("action")
            .put("query", "guide site:private.docs.example")
            .getJSONArray("sources")
            .getJSONObject(0)
            .put("url", "https://docs.example/source")
        response
            .getJSONArray("output")
            .getJSONObject(1)
            .getJSONArray("content")
            .getJSONObject(0)
            .getJSONArray("annotations")
            .getJSONObject(0)
            .put("url", "https://docs.example/source")

        val queryError =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchResponseParser.parse(
                    responseJson = response,
                    request = request,
                    binding = OpenAIHostedWebSearchTestFixtures.binding(),
                )
            }
        assertEquals(
            OpenAIHostedWebSearchErrorCode.DOMAIN_POLICY_VIOLATION,
            queryError.code,
        )
    }

    @Test
    fun relayAcceptsActionSourcesWithoutCitationAndDoesNotInventMarkers() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                getJSONArray("output")
                    .getJSONObject(1)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .put("annotations", JSONArray())
            }

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES, result.evidenceMode)
        assertEquals("Evidence", result.answerWithSourceMarkers)
        assertTrue(result.citations.isEmpty())
        assertEquals("https://example.com/source", result.allSources.single().url)
        assertTrue(result.warnings.contains("URL_CITATIONS_MISSING"))
    }

    @Test
    fun relayAcceptsNamedStructuredApiFeedWithoutUrlEvidence() {
        val response =
            JSONObject()
                .put("id", "resp_api_feed")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "search")
                                        .put("query", "current UTC date")
                                        .put(
                                            "sources",
                                            JSONArray()
                                                .put(
                                                    JSONObject()
                                                        .put("type", "api")
                                                        .put("name", "time"),
                                                ),
                                        ),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("type", "output_text")
                                                .put("text", "Evidence")
                                                .put("annotations", JSONArray()),
                                        ),
                                ),
                        ),
                )

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS, result.evidenceMode)
        assertEquals("Evidence", result.answerWithSourceMarkers)
        assertTrue(result.citations.isEmpty())
        assertEquals("api", result.allSources.single().type)
        assertEquals("time", result.allSources.single().title)
        assertEquals(null, result.allSources.single().url)
        assertTrue(result.warnings.contains("URL_EVIDENCE_NOT_APPLICABLE"))
    }

    @Test
    fun relayReturnsExplicitNoneModeWithoutAnySearchEvidenceChannel() {
        val response =
            JSONObject()
                .put("id", "resp_text_only")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "web_search_call")
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "search")
                                        .put("query", "fixture"),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("type", "output_text")
                                                .put("text", "Evidence")
                                                .put("annotations", JSONArray()),
                                        ),
                                ),
                        ),
                )

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(OpenAIHostedWebSearchEvidenceMode.NONE, result.evidenceMode)
        assertEquals("Evidence", result.answer)
        assertEquals("Evidence", result.answerWithSourceMarkers)
        assertTrue(result.citations.isEmpty())
        assertTrue(result.citedSources.isEmpty())
        assertTrue(result.allSources.isEmpty())
        assertEquals(0, result.sourceSummary.allSourceCount)
        assertTrue(result.warnings.contains("NO_WEB_EVIDENCE"))
    }

    @Test
    fun nonObjectUnknownTypeAndNonHttpSourcesAreCountedAsInvalid() {
        val response =
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "https://example.com/source",
                sourceTitle = "Example",
            ).apply {
                val sources =
                    getJSONArray("output")
                        .getJSONObject(0)
                        .getJSONObject("action")
                        .getJSONArray("sources")
                sources.put("not-an-object")
                sources.put(JSONObject().put("type", "oai-unknown"))
                sources.put(
                    JSONObject()
                        .put("type", "url")
                        .put("url", "file:///private/source")
                )
                put(
                    "usage",
                    JSONObject()
                        .put("input_tokens", 10)
                        .put("output_tokens", 4),
                )
            }

        val parsed =
            OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding =
                    OpenAIHostedWebSearchTestFixtures.binding(
                        providerContract =
                            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT,
                    ),
            )

        assertEquals(4, parsed.diagnostics.totalActionSourceCount)
        assertEquals(1, parsed.diagnostics.validActionSourceUrlCount)
        assertEquals(3, parsed.diagnostics.invalidActionSourceCount)
        assertEquals(
            listOf(
                "ACTION_SOURCE_INVALID:output=0,source=1,reason=not_object",
                "ACTION_SOURCE_INVALID:output=0,source=2,reason=unsupported_type",
                "ACTION_SOURCE_INVALID:output=0,source=3,reason=invalid_url",
                "ACTION_SOURCES_PARTIAL",
            ),
            parsed.result.warnings,
        )
    }

    @Test
    fun missingTitleUsesNormalizedUrlHostAndMissingUsageProducesWarning() {
        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson =
                    baseResponse(
                        text = "Evidence",
                        startIndex = 0,
                        endIndex = 8,
                        sourceUrl = "https://News.Example/path",
                        sourceTitle = "",
                    ),
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )

        assertEquals("news.example", result.allSources.single().title)
        assertEquals(listOf("USAGE_MISSING"), result.warnings)
    }

    @Test
    fun overlappingCitationsInsertStableMarkersAtOriginalOffsets() {
        val text = "abcdef"
        val message =
            JSONObject()
                .put("type", "message")
                .put(
                    "content",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "output_text")
                                .put("text", text)
                                .put(
                                    "annotations",
                                    JSONArray()
                                        .put(
                                            citation(
                                                url = "https://one.example",
                                                title = "One",
                                                startIndex = 0,
                                                endIndex = 4,
                                            ),
                                        )
                                        .put(
                                            citation(
                                                url = "https://two.example",
                                                title = "Two",
                                                startIndex = 2,
                                                endIndex = 4,
                                            ),
                                        ),
                                ),
                        ),
                )
        val response =
            JSONObject()
                .put("id", "resp_overlap")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(
                            searchCall("https://one.example", "One").apply {
                                getJSONObject("action")
                                    .getJSONArray("sources")
                                    .put(
                                        JSONObject()
                                            .put("type", "url")
                                            .put("url", "https://two.example")
                                            .put("title", "Two"),
                                    )
                            }
                        )
                        .put(message),
                )

        val result =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson = response,
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )

        assertEquals("abcd[S1][S2]ef", result.answerWithSourceMarkers)
    }

    @Test
    fun rejectsMissingSearchCallTextAndInvalidCitationSpanWhileAcceptingActionEvidence() {
        val validMessage =
            JSONObject()
                .put("type", "message")
                .put(
                    "content",
                    JSONArray()
                        .put(
                            outputText(
                                text = "Evidence",
                                url = "https://example.com",
                                title = "Example",
                                startIndex = 0,
                                endIndex = 8,
                            ),
                        ),
                )

        assertParseError(
            OpenAIHostedWebSearchErrorCode.SEARCH_TOOL_NOT_CALLED,
            JSONObject()
                .put("id", "resp_no_search")
                .put("status", "completed")
                .put("output", JSONArray().put(validMessage)),
        )
        assertParseError(
            OpenAIHostedWebSearchErrorCode.SEARCH_OUTPUT_EMPTY,
            JSONObject()
                .put("id", "resp_no_text")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray().put(searchCall("https://example.com", "Example")),
                ),
        )
        val actionEvidence =
            OpenAIHostedWebSearchResponseParser.parse(
                responseJson =
                    JSONObject()
                .put("id", "resp_no_citation")
                .put("status", "completed")
                .put(
                    "output",
                    JSONArray()
                        .put(searchCall("https://example.com", "Example"))
                        .put(
                            JSONObject()
                                .put("type", "message")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("type", "output_text")
                                                .put("text", "Evidence"),
                                        ),
                                ),
                        ),
                ),
                request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                binding = OpenAIHostedWebSearchTestFixtures.binding(),
            )
        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES,
            actionEvidence.evidenceMode,
        )
        assertTrue(actionEvidence.citations.isEmpty())
        assertEquals(1, actionEvidence.allSources.size)
        assertTrue(actionEvidence.warnings.contains("URL_CITATIONS_MISSING"))
        assertParseError(
            OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 99,
                sourceUrl = "https://example.com",
                sourceTitle = "Example",
            ),
        )
    }

    @Test
    fun rejectsNonHttpSourceUrl() {
        assertParseError(
            OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
            baseResponse(
                text = "Evidence",
                startIndex = 0,
                endIndex = 8,
                sourceUrl = "file:///private/source",
                sourceTitle = "Local",
            ),
        )
    }

    private fun baseResponse(
        text: String,
        startIndex: Int,
        endIndex: Int,
        sourceUrl: String,
        sourceTitle: String,
    ): JSONObject =
        JSONObject()
            .put("id", "resp_fixture")
            .put("status", "completed")
            .put(
                "output",
                JSONArray()
                    .put(searchCall(sourceUrl, sourceTitle))
                    .put(
                        JSONObject()
                            .put("type", "message")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        outputText(
                                            text = text,
                                            url = sourceUrl,
                                            title = sourceTitle,
                                            startIndex = startIndex,
                                            endIndex = endIndex,
                                        ),
                                    ),
                            ),
                    ),
            )

    private fun searchCall(url: String, title: String): JSONObject =
        JSONObject()
            .put("type", "web_search_call")
            .put(
                "action",
                JSONObject()
                    .put("type", "search")
                    .put("query", "fixture")
                    .put(
                        "sources",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "url")
                                    .put("url", url)
                                    .put("title", title),
                            ),
                    ),
            )

    private fun outputText(
        text: String,
        url: String,
        title: String,
        startIndex: Int,
        endIndex: Int,
    ): JSONObject =
        JSONObject()
            .put("type", "output_text")
            .put("text", text)
            .put(
                "annotations",
                JSONArray()
                    .put(
                        citation(
                            url = url,
                            title = title,
                            startIndex = startIndex,
                            endIndex = endIndex,
                        ),
                    ),
            )

    private fun citation(
        url: String,
        title: String,
        startIndex: Int,
        endIndex: Int,
    ): JSONObject =
        JSONObject()
            .put("type", "url_citation")
            .put("url", url)
            .put("title", title)
            .put("start_index", startIndex)
            .put("end_index", endIndex)

    private fun assertParseError(
        expectedCode: OpenAIHostedWebSearchErrorCode,
        response: JSONObject,
    ) {
        val error =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchResponseParser.parse(
                    responseJson = response,
                    request = OpenAIHostedWebSearchTestFixtures.effectiveRequest(),
                    binding = OpenAIHostedWebSearchTestFixtures.binding(),
                )
            }
        assertEquals(expectedCode, error.code)
    }
}
