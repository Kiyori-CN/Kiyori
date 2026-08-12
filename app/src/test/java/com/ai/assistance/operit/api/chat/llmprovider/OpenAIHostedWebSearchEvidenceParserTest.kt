package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchEvidenceParserTest {
    @Test
    fun `parser accepts the exact structured evidence contract`() {
        val evidence =
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = evidenceJson(),
            )

        requireNotNull(evidence)
        assertEquals("ows_test", evidence.requestId)
        assertEquals("resp_test", evidence.responseId)
        assertEquals("live", evidence.mode)
        assertEquals("gpt-5.6-luna", evidence.model)
        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES,
            evidence.evidenceMode,
        )
        assertEquals("OpenAI.[S1]", evidence.answerWithSourceMarkers)
        assertEquals(1, evidence.searchActions.size)
        assertEquals(1, evidence.citations.size)
        assertEquals("S1", evidence.sources.single().sourceId)
        assertEquals(1, evidence.usage.webSearchCalls)
        assertTrue(evidence.warnings.isEmpty())
        assertEquals(
            OpenAIHostedWebSearchActionSourceCoverage.COMPLETE,
            evidence.sourceDiagnostics.actionSourceCoverage,
        )
    }

    @Test
    fun `parser ignores other tools and rejects incomplete evidence`() {
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = "other:search",
                resultJson = evidenceJson(),
            )
        )

        val incomplete = JSONObject(evidenceJson()).remove("sources").let { removed ->
            assertTrue(removed is JSONArray)
            JSONObject(evidenceJson()).apply { remove("sources") }.toString()
        }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = incomplete,
            )
        )
    }

    @Test
    fun `parser rejects citation offsets and non-http source URLs`() {
        val invalidOffset =
            JSONObject(evidenceJson()).apply {
                getJSONArray("citations").getJSONObject(0).put("end_index", 100)
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                invalidOffset.toString(),
            )
        )

        val invalidUrl =
            JSONObject(evidenceJson()).apply {
                getJSONArray("sources").getJSONObject(0).put("url", "javascript:alert(1)")
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                invalidUrl.toString(),
            )
        )
    }

    @Test
    fun `parser accepts known realtime feed metadata without a clickable url`() {
        val withFeed =
            JSONObject(evidenceJson()).apply {
                getJSONArray("sources")
                    .put(
                        JSONObject()
                            .put("source_id", "S2")
                            .put("type", "oai-weather")
                            .put("title", "OpenAI Weather live feed")
                            .put("url", JSONObject.NULL)
                    )
            }

        val evidence =
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = withFeed.toString(),
            )

        requireNotNull(evidence)
        assertEquals(listOf("url", "oai-weather"), evidence.sources.map { it.type })
        assertNull(evidence.sources[1].url)
    }

    @Test
    fun `parser accepts complete action coverage with a citation outside action urls`() {
        val citationOutsideActionUrls =
            JSONObject(evidenceJson()).apply {
                getJSONArray("sources")
                    .put(
                        JSONObject()
                            .put("source_id", "S2")
                            .put("type", "url")
                            .put("title", "Search index")
                            .put("url", "https://openai.com/news/")
                    )
                getJSONObject("source_diagnostics")
                    .put(
                        "action_source_urls",
                        JSONArray().put("https://openai.com/news/"),
                    )
                    .put(
                        "citations_missing_from_action_sources",
                        JSONArray().put("https://openai.com/"),
                    )
                put(
                    "warnings",
                    JSONArray().put("CITATION_NOT_IN_ACTION_SOURCES"),
                )
            }

        val evidence =
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = citationOutsideActionUrls.toString(),
            )

        requireNotNull(evidence)
        assertEquals(
            OpenAIHostedWebSearchActionSourceCoverage.COMPLETE,
            evidence.sourceDiagnostics.actionSourceCoverage,
        )
        assertEquals(
            listOf("https://openai.com/"),
            evidence.sourceDiagnostics.citationsMissingFromActionSources,
        )
    }

    @Test
    fun `parser accepts citation only evidence and structured relay feeds without invented spans`() {
        val citationOnly =
            JSONObject(evidenceJson()).apply {
                put("evidence_mode", "url_citations")
                put("warnings", JSONArray().put("ACTION_SOURCES_MISSING"))
                put(
                    "source_diagnostics",
                    sourceDiagnostics(
                        actionSourceCoverage = "missing",
                        actionSourceUrls = emptyList(),
                        citationUrls = listOf("https://openai.com/"),
                        citationsMissingFromActionSources =
                            listOf("https://openai.com/"),
                        missingSourceActionIndexes = listOf(0),
                    ),
                )
            }
        val citationEvidence =
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = citationOnly.toString(),
            )
        requireNotNull(citationEvidence)
        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
            citationEvidence.evidenceMode,
        )

        val structuredFeed =
            JSONObject(evidenceJson()).apply {
                put("evidence_mode", "structured_feeds")
                put("answer_with_source_markers", "OpenAI.")
                put("citations", JSONArray())
                put(
                    "sources",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("source_id", "S1")
                                .put("type", "api")
                                .put("title", "time")
                                .put("url", JSONObject.NULL)
                        )
                )
                put("warnings", JSONArray().put("URL_EVIDENCE_NOT_APPLICABLE"))
                put(
                    "source_diagnostics",
                    sourceDiagnostics(
                        actionSourceCoverage = "complete",
                        actionSourceUrls = emptyList(),
                        citationUrls = emptyList(),
                        citationsMissingFromActionSources = emptyList(),
                    ),
                )
            }
        val feedEvidence =
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = structuredFeed.toString(),
            )
        requireNotNull(feedEvidence)
        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS,
            feedEvidence.evidenceMode,
        )
        assertTrue(feedEvidence.citations.isEmpty())
        assertEquals(feedEvidence.answer, feedEvidence.answerWithSourceMarkers)
    }

    @Test
    fun `parser rejects unknown feed types and citations bound to non-url sources`() {
        val unknownFeed =
            JSONObject(evidenceJson()).apply {
                getJSONArray("sources")
                    .getJSONObject(0)
                    .put("type", "oai-unknown")
                    .put("url", JSONObject.NULL)
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                unknownFeed.toString(),
            )
        )

        val citationBoundToFeed =
            JSONObject(evidenceJson()).apply {
                getJSONArray("sources")
                    .getJSONObject(0)
                    .put("type", "oai-finance")
                    .put("url", JSONObject.NULL)
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                citationBoundToFeed.toString(),
            )
        )

        val unsupportedNormalization =
            JSONObject(evidenceJson()).apply {
                getJSONObject("source_diagnostics")
                    .put("url_normalization", "unverified")
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                unsupportedNormalization.toString(),
            )
        )

        val incompleteCitationDifference =
            JSONObject(evidenceJson()).apply {
                getJSONObject("source_diagnostics")
                    .put(
                        "action_source_urls",
                        JSONArray().put("https://openai.com/news/"),
                    )
                    .put(
                        "citations_missing_from_action_sources",
                        JSONArray(),
                    )
                getJSONArray("sources")
                    .put(
                        JSONObject()
                            .put("source_id", "S2")
                            .put("type", "url")
                            .put("title", "Search index")
                            .put("url", "https://openai.com/news/")
                    )
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                incompleteCitationDifference.toString(),
            )
        )

        val inventedOpenPage =
            JSONObject(evidenceJson()).apply {
                getJSONObject("source_diagnostics")
                    .put(
                        "open_page_urls",
                        JSONArray().put("https://openai.com/news/"),
                    )
            }
        assertNull(
            OpenAIHostedWebSearchEvidenceParser.parseOrNull(
                OpenAIHostedWebSearchContract.TOOL_NAME,
                inventedOpenPage.toString(),
            )
        )
    }

    private fun evidenceJson(): String =
        JSONObject()
            .put("success", true)
            .put("schema_version", OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION)
            .put("request_id", "ows_test")
            .put("response_id", "resp_test")
            .put("provider", "openai")
            .put("backend", "responses_web_search")
            .put("query", "What is OpenAI?")
            .put("mode", "live")
            .put("model", "gpt-5.6-luna")
            .put("evidence_mode", "url_citations_and_action_sources")
            .put("answer", "OpenAI.")
            .put("answer_with_source_markers", "OpenAI.[S1]")
            .put(
                "search_actions",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "search")
                            .put("query", "OpenAI")
                            .put("url", JSONObject.NULL)
                            .put("pattern", JSONObject.NULL)
                    )
            )
            .put(
                "citations",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("source_id", "S1")
                            .put("title", "OpenAI")
                            .put("url", "https://openai.com/")
                            .put("start_index", 0)
                            .put("end_index", 7)
                    )
            )
            .put(
                "sources",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("source_id", "S1")
                            .put("type", "url")
                            .put("title", "OpenAI")
                            .put("url", "https://openai.com/")
                    )
            )
            .put(
                "usage",
                JSONObject()
                    .put("input_tokens", 10)
                    .put("cached_input_tokens", 2)
                    .put("output_tokens", 4)
                    .put("web_search_calls", 1)
            )
            .put("warnings", JSONArray())
            .put(
                "source_diagnostics",
                sourceDiagnostics(
                    actionSourceCoverage = "complete",
                    actionSourceUrls = listOf("https://openai.com/"),
                    citationUrls = listOf("https://openai.com/"),
                    citationsMissingFromActionSources = emptyList(),
                ),
            )
            .toString()

    private fun sourceDiagnostics(
        actionSourceCoverage: String,
        actionSourceUrls: List<String>,
        citationUrls: List<String>,
        citationsMissingFromActionSources: List<String>,
        missingSourceActionIndexes: List<Int> = emptyList(),
    ): JSONObject =
        JSONObject()
            .put("response_id", "resp_test")
            .put("action_source_coverage", actionSourceCoverage)
            .put("action_source_urls", JSONArray(actionSourceUrls))
            .put("citation_urls", JSONArray(citationUrls))
            .put(
                "citations_missing_from_action_sources",
                JSONArray(citationsMissingFromActionSources),
            )
            .put("open_page_urls", JSONArray())
            .put(
                "missing_source_action_indexes",
                JSONArray(missingSourceActionIndexes),
            )
            .put("invalid_action_source_count", 0)
            .put("allowed_domains", JSONArray())
            .put("url_normalization", "http_https_uri")
}
