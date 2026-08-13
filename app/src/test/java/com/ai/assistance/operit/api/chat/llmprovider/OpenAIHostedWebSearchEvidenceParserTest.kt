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
        val evidence = parseEvidence(evidenceJson())

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
        assertEquals(
            OpenAIHostedWebSearchEvidenceActionType.SEARCH,
            evidence.searchActions.single().type,
        )
        assertEquals(1, evidence.citations.size)
        assertEquals("S1", evidence.citedSources.single().sourceId)
        assertEquals("S1", evidence.allSources.single().sourceId)
        assertEquals(1, evidence.sourceSummary.citedSourceCount)
        assertEquals(1, evidence.sourceSummary.allSourceCount)
        assertEquals(1, evidence.usage.webSearchCalls)
        assertTrue(evidence.warnings.isEmpty())
        assertEquals(
            OpenAIHostedWebSearchActionSourceCoverage.COMPLETE,
            evidence.sourceDiagnostics.actionSourceCoverage,
        )
    }

    @Test
    fun `parser ignores other tools and rejects incomplete evidence`() {
        assertEquals(
            OpenAIHostedWebSearchEvidenceParseResult.NotApplicable,
            OpenAIHostedWebSearchEvidenceParser.parse(
                toolName = "other:search",
                resultJson = evidenceJson(),
            ),
        )

        val incomplete = JSONObject(evidenceJson()).remove("all_sources").let { removed ->
            assertTrue(removed is JSONArray)
            JSONObject(evidenceJson()).apply { remove("all_sources") }.toString()
        }
        val invalid =
            assertInvalid(
                resultJson = incomplete,
                expectedCode =
                    OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                expectedFieldName = "all_sources",
            )
        assertEquals(
            OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
            invalid.schemaRevision,
        )
        assertTrue(invalid.hasRequestId)
        assertTrue(!invalid.sanitizedSummary.contains("What is OpenAI?"))
    }

    @Test
    fun `parser rejects citation offsets and non-http source URLs`() {
        val invalidOffset =
            JSONObject(evidenceJson()).apply {
                getJSONArray("citations").getJSONObject(0).put("end_index", 100)
            }
        assertInvalid(
            resultJson = invalidOffset.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
            expectedFieldName = "citations.start_index",
        )

        val invalidUrl =
            JSONObject(evidenceJson()).apply {
                getJSONArray("all_sources").getJSONObject(0).put("url", "javascript:alert(1)")
            }
        assertInvalid(
            resultJson = invalidUrl.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
            expectedFieldName = "all_sources.url",
        )
    }

    @Test
    fun `parser accepts known realtime feed metadata without a clickable url`() {
        val withFeed =
            JSONObject(evidenceJson()).apply {
                getJSONArray("all_sources")
                    .put(
                        JSONObject()
                            .put("source_id", "S2")
                            .put("type", "oai-weather")
                            .put("title", "OpenAI Weather live feed")
                            .put("url", JSONObject.NULL)
                    )
                synchronizeSourceSummary(this)
            }

        val evidence = parseEvidence(withFeed.toString())

        assertEquals(listOf("url", "oai-weather"), evidence.allSources.map { it.type })
        assertNull(evidence.allSources[1].url)
    }

    @Test
    fun `parser accepts complete action coverage with a citation outside action urls`() {
        val citationOutsideActionUrls =
            JSONObject(evidenceJson()).apply {
                getJSONArray("all_sources")
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
                synchronizeSourceSummary(this)
            }

        val evidence = parseEvidence(citationOutsideActionUrls.toString())

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
        val citationEvidence = parseEvidence(citationOnly.toString())
        assertEquals(
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
            citationEvidence.evidenceMode,
        )

        val structuredFeed =
            JSONObject(evidenceJson()).apply {
                put("evidence_mode", "structured_feeds")
                put("answer_with_source_markers", "OpenAI.")
                put("citations", JSONArray())
                put("cited_sources", JSONArray())
                put(
                    "all_sources",
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
                synchronizeSourceSummary(this)
            }
        val feedEvidence = parseEvidence(structuredFeed.toString())
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
                getJSONArray("all_sources")
                    .getJSONObject(0)
                    .put("type", "oai-unknown")
                    .put("url", JSONObject.NULL)
            }
        assertInvalid(
            resultJson = unknownFeed.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            expectedFieldName = "all_sources.type",
        )

        val citationBoundToFeed =
            JSONObject(evidenceJson()).apply {
                getJSONArray("all_sources")
                    .getJSONObject(0)
                    .put("type", "oai-finance")
                    .put("url", JSONObject.NULL)
                getJSONArray("cited_sources")
                    .getJSONObject(0)
                    .put("type", "oai-finance")
                    .put("url", JSONObject.NULL)
            }
        assertInvalid(
            resultJson = citationBoundToFeed.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
            expectedFieldName = "citations.source_id",
        )

        val unsupportedNormalization =
            JSONObject(evidenceJson()).apply {
                getJSONObject("source_diagnostics")
                    .put("url_normalization", "unverified")
            }
        assertInvalid(
            resultJson = unsupportedNormalization.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            expectedFieldName = "source_diagnostics.url_normalization",
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
                getJSONArray("all_sources")
                    .put(
                        JSONObject()
                            .put("source_id", "S2")
                            .put("type", "url")
                            .put("title", "Search index")
                            .put("url", "https://openai.com/news/")
                    )
                synchronizeSourceSummary(this)
            }
        assertInvalid(
            resultJson = incompleteCitationDifference.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            expectedFieldName =
                "source_diagnostics.citations_missing_from_action_sources",
        )

        val inventedOpenPage =
            JSONObject(evidenceJson()).apply {
                getJSONObject("source_diagnostics")
                    .put(
                        "open_page_urls",
                        JSONArray().put("https://openai.com/news/"),
                    )
            }
        assertInvalid(
            resultJson = inventedOpenPage.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            expectedFieldName = "source_diagnostics.open_page_urls",
        )
    }

    @Test
    fun `parser exposes bounded diagnostics for malformed target results`() {
        val malformed =
            OpenAIHostedWebSearchEvidenceParser.parse(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = "{",
            )
        require(malformed is OpenAIHostedWebSearchEvidenceParseResult.Invalid)
        assertEquals(
            OpenAIHostedWebSearchEvidenceInvalidCode.INVALID_JSON,
            malformed.code,
        )
        assertNull(malformed.schemaRevision)
        assertTrue(!malformed.hasRequestId)
        assertNull(malformed.fieldName)

        val revisionMismatch =
            assertInvalid(
                resultJson =
                    JSONObject(evidenceJson())
                        .put(
                            "schema_version",
                            OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION - 1,
                        )
                        .toString(),
                expectedCode =
                    OpenAIHostedWebSearchEvidenceInvalidCode.SCHEMA_REVISION_MISMATCH,
                expectedFieldName = "schema_version",
            )
        assertEquals(
            OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION - 1,
            revisionMismatch.schemaRevision,
        )
        assertTrue(revisionMismatch.hasRequestId)
    }

    @Test
    fun `parser rejects unknown search action types explicitly`() {
        val unknownAction =
            JSONObject(evidenceJson()).apply {
                getJSONArray("search_actions")
                    .getJSONObject(0)
                    .put("type", "browse")
            }

        assertInvalid(
            resultJson = unknownAction.toString(),
            expectedCode = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            expectedFieldName = "search_actions.type",
        )
    }

    private fun parseEvidence(resultJson: String): OpenAIHostedWebSearchEvidence {
        val result =
            OpenAIHostedWebSearchEvidenceParser.parse(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = resultJson,
            )
        require(result is OpenAIHostedWebSearchEvidenceParseResult.Parsed)
        return result.evidence
    }

    private fun assertInvalid(
        resultJson: String,
        expectedCode: OpenAIHostedWebSearchEvidenceInvalidCode,
        expectedFieldName: String?,
    ): OpenAIHostedWebSearchEvidenceParseResult.Invalid {
        val result =
            OpenAIHostedWebSearchEvidenceParser.parse(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = resultJson,
            )
        require(result is OpenAIHostedWebSearchEvidenceParseResult.Invalid)
        assertEquals(expectedCode, result.code)
        assertEquals(expectedFieldName, result.fieldName)
        return result
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
                "cited_sources",
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
                "all_sources",
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
                "source_summary",
                JSONObject()
                    .put("all_source_count", 1)
                    .put("cited_source_count", 1)
                    .put("uncited_source_count", 0)
                    .put("url_source_count", 1)
                    .put("structured_source_count", 0)
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
            .put(
                "execution_diagnostics",
                executionDiagnostics(),
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
            .put("domain_policy_state", "not_requested")
            .put("url_normalization", "http_https_identity")

    private fun executionDiagnostics(): JSONObject =
        JSONObject()
            .put("total_elapsed_ms", 100)
            .put("queue_wait_ms", 5)
            .put("http_elapsed_ms", 80)
            .put("response_header_wait_ms", 40)
            .put("response_body_read_ms", 10)
            .put("parse_ms", 5)
            .put("callback_delivery_ms", 1)
            .put("provider_request_id", "provider-request")
            .put("submission_state", "response_started")
            .put(
                "location",
                JSONObject()
                    .put("location_requested", false)
                    .put("location_configured", false)
                    .put("location_applied", false)
                    .put("location_precision", "none"),
            )

    private fun synchronizeSourceSummary(root: JSONObject) {
        val allSources = root.getJSONArray("all_sources")
        val citedSources = root.getJSONArray("cited_sources")
        var urlSourceCount = 0
        for (index in 0 until allSources.length()) {
            if (allSources.getJSONObject(index).getString("type") == "url") {
                urlSourceCount += 1
            }
        }
        root.put(
            "source_summary",
            JSONObject()
                .put("all_source_count", allSources.length())
                .put("cited_source_count", citedSources.length())
                .put(
                    "uncited_source_count",
                    allSources.length() - citedSources.length(),
                )
                .put("url_source_count", urlSourceCount)
                .put(
                    "structured_source_count",
                    allSources.length() - urlSourceCount,
                ),
        )
    }
}
