package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchMainModelProjectionTest {
    @Test
    fun `model projection keeps cited evidence and counts without uncited sources`() {
        val serializedResult = resultWithUncitedSource().toJson().toString()
        val toolResult =
            ToolResult(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                success = true,
                result = StringResultData(serializedResult),
            )

        val uiMessage = ConversationMarkupManager.formatToolResultForMessage(toolResult)
        val modelMessage = ConversationMarkupManager.formatToolResultForModel(toolResult)
        val projection = JSONObject(extractContent(modelMessage))

        assertTrue(serializedResult.contains("\"all_sources\""))
        assertTrue(serializedResult.contains("Uncited source"))
        assertEquals("main_model", projection.getString("projection"))
        assertEquals(1, projection.getJSONArray("cited_sources").length())
        assertEquals(
            2,
            projection.getJSONObject("source_summary").getInt("all_source_count"),
        )
        assertEquals(
            1,
            projection.getJSONObject("source_summary").getInt("uncited_source_count"),
        )
        assertFalse(projection.has("all_sources"))
        assertFalse(projection.has("search_actions"))
        assertFalse(projection.has("citations"))
        assertFalse(projection.has("query"))
        assertFalse(projection.has("usage"))
        assertTrue(uiMessage.contains("\"all_sources\""))
        assertTrue(uiMessage.contains("Uncited source"))
        assertTrue(uiMessage.contains("https://uncited.example.test/"))

        val uiEvidence =
            OpenAIHostedWebSearchEvidenceParser.parse(
                toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                resultJson = extractContent(uiMessage),
            )
        assertTrue(uiEvidence is OpenAIHostedWebSearchEvidenceParseResult.Parsed)
    }

    @Test
    fun `zero evidence success remains a success projection with explicit warning`() {
        val projection =
            JSONObject(
                requireNotNull(
                    OpenAIHostedWebSearchMainModelProjection.project(
                        toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                        serializedResult = zeroEvidenceResult().toJson().toString(),
                    )
                )
            )

        assertTrue(projection.getBoolean("success"))
        assertEquals("none", projection.getString("evidence_mode"))
        assertEquals(0, projection.getJSONArray("cited_sources").length())
        assertEquals(
            "NO_WEB_EVIDENCE",
            projection.getJSONArray("warnings").getString(0),
        )
        assertFalse(projection.has("all_sources"))
    }

    @Test
    fun `invalid schema is summarized without forwarding raw source collections`() {
        val raw =
            resultWithUncitedSource()
                .toJson()
                .put(
                    "schema_version",
                    OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION - 1,
                )
                .toString()

        val projection =
            JSONObject(
                requireNotNull(
                    OpenAIHostedWebSearchMainModelProjection.project(
                        toolName = OpenAIHostedWebSearchContract.TOOL_NAME,
                        serializedResult = raw,
                    )
                )
            )

        assertFalse(projection.getBoolean("success"))
        assertEquals(
            "SCHEMA_REVISION_MISMATCH",
            projection.getJSONObject("error").getString("reason"),
        )
        assertFalse(projection.has("all_sources"))
        assertFalse(projection.toString().contains("Uncited source"))
    }

    @Test
    fun `ordinary tool result is not projected`() {
        assertEquals(
            null,
            OpenAIHostedWebSearchMainModelProjection.project(
                toolName = "other:search",
                serializedResult = """{"all_sources":["unchanged"]}""",
            ),
        )
    }

    private fun resultWithUncitedSource(): OpenAIHostedWebSearchResult {
        val citedSource =
            OpenAIHostedWebSearchSource(
                sourceId = "S1",
                type = "url",
                title = "Cited source",
                url = "https://cited.example.test/",
            )
        val uncitedSource =
            OpenAIHostedWebSearchSource(
                sourceId = "S2",
                type = "url",
                title = "Uncited source",
                url = "https://uncited.example.test/",
            )
        return result(
            evidenceMode =
                OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES,
            answer = "Evidence",
            answerWithSourceMarkers = "Evidence[S1]",
            citations =
                listOf(
                    OpenAIHostedWebSearchCitation(
                        sourceId = "S1",
                        title = citedSource.title,
                        url = requireNotNull(citedSource.url),
                        startIndex = 0,
                        endIndex = 8,
                    )
                ),
            citedSources = listOf(citedSource),
            allSources = listOf(citedSource, uncitedSource),
            sourceSummary =
                OpenAIHostedWebSearchSourceSummary(
                    allSourceCount = 2,
                    citedSourceCount = 1,
                    uncitedSourceCount = 1,
                    urlSourceCount = 2,
                    structuredSourceCount = 0,
                ),
            warnings = emptyList(),
            sourceDiagnostics =
                sourceDiagnostics(
                    coverage = OpenAIHostedWebSearchActionSourceCoverage.COMPLETE,
                    actionSourceUrls =
                        listOf(
                            requireNotNull(citedSource.url),
                            requireNotNull(uncitedSource.url),
                        ),
                    citationUrls = listOf(requireNotNull(citedSource.url)),
                ),
        )
    }

    private fun zeroEvidenceResult(): OpenAIHostedWebSearchResult =
        result(
            evidenceMode = OpenAIHostedWebSearchEvidenceMode.NONE,
            answer = "No verifiable web evidence was returned.",
            answerWithSourceMarkers = "No verifiable web evidence was returned.",
            citations = emptyList(),
            citedSources = emptyList(),
            allSources = emptyList(),
            sourceSummary =
                OpenAIHostedWebSearchSourceSummary(
                    allSourceCount = 0,
                    citedSourceCount = 0,
                    uncitedSourceCount = 0,
                    urlSourceCount = 0,
                    structuredSourceCount = 0,
                ),
            warnings = listOf("NO_WEB_EVIDENCE"),
            sourceDiagnostics =
                sourceDiagnostics(
                    coverage = OpenAIHostedWebSearchActionSourceCoverage.MISSING,
                    actionSourceUrls = emptyList(),
                    citationUrls = emptyList(),
                    missingSourceActionIndexes = listOf(0),
                ),
        )

    private fun result(
        evidenceMode: OpenAIHostedWebSearchEvidenceMode,
        answer: String,
        answerWithSourceMarkers: String,
        citations: List<OpenAIHostedWebSearchCitation>,
        citedSources: List<OpenAIHostedWebSearchSource>,
        allSources: List<OpenAIHostedWebSearchSource>,
        sourceSummary: OpenAIHostedWebSearchSourceSummary,
        warnings: List<String>,
        sourceDiagnostics: OpenAIHostedWebSearchSourceDiagnostics,
    ): OpenAIHostedWebSearchResult =
        OpenAIHostedWebSearchResult(
            requestId = "ows_projection",
            responseId = "resp_projection",
            query = "private query must not be projected",
            mode = OpenAIHostedWebSearchMode.LIVE,
            modelName = "gpt-test",
            evidenceMode = evidenceMode,
            answer = answer,
            answerWithSourceMarkers = answerWithSourceMarkers,
            searchActions =
                listOf(
                    OpenAIHostedWebSearchAction(
                        type = "search",
                        query = "private provider search action",
                        url = null,
                        pattern = null,
                    )
                ),
            citations = citations,
            citedSources = citedSources,
            allSources = allSources,
            sourceSummary = sourceSummary,
            usage =
                OpenAIHostedWebSearchUsage(
                    inputTokens = 100,
                    cachedInputTokens = 20,
                    outputTokens = 30,
                    webSearchCalls = 1,
                ),
            warnings = warnings,
            sourceDiagnostics = sourceDiagnostics,
            executionDiagnostics =
                OpenAIHostedWebSearchExecutionDiagnostics(
                    totalElapsedMs = 123L,
                    queueWaitMs = 3L,
                    httpElapsedMs = 100L,
                    responseHeaderWaitMs = 50L,
                    responseBodyReadMs = 20L,
                    parseMs = 5L,
                    callbackDeliveryMs = null,
                    providerRequestId = "provider-request-id",
                    submissionState =
                        OpenAIHostedWebSearchSubmissionState.RESPONSE_STARTED,
                    location =
                        OpenAIHostedWebSearchLocationDiagnostics(
                            requested = false,
                            configured = false,
                            applied = false,
                            precision = "none",
                        ),
                ),
        )

    private fun sourceDiagnostics(
        coverage: OpenAIHostedWebSearchActionSourceCoverage,
        actionSourceUrls: List<String>,
        citationUrls: List<String>,
        missingSourceActionIndexes: List<Int> = emptyList(),
    ): OpenAIHostedWebSearchSourceDiagnostics =
        OpenAIHostedWebSearchSourceDiagnostics(
            responseId = "resp_projection",
            actionSourceCoverage = coverage,
            actionSourceUrls = actionSourceUrls,
            citationUrls = citationUrls,
            citationsMissingFromActionSources = emptyList(),
            openPageUrls = emptyList(),
            missingSourceActionIndexes = missingSourceActionIndexes,
            invalidActionSourceCount = 0,
            allowedDomains = emptyList(),
            domainPolicyState = OpenAIHostedWebSearchDomainPolicyState.NOT_REQUESTED,
        )

    private fun extractContent(message: String): String =
        message
            .substringAfter("<content>")
            .substringBefore("</content>")
}
