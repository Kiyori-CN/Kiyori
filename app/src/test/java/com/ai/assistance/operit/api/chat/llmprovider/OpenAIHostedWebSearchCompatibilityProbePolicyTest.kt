package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchCompatibilityProbePolicyTest {
    @Test
    fun evidenceSchemaFailureIsReportedAsRelayIncompatible() {
        val sourceFailure =
            OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
                message = "No valid action source URL.",
            )

        val rewritten =
            OpenAIHostedWebSearchCompatibilityProbePolicy
                .rewriteEvidenceFailure(sourceFailure)

        assertEquals(OpenAIHostedWebSearchErrorCode.RELAY_INCOMPATIBLE, rewritten.code)
        assertTrue(rewritten.message.contains("[SOURCE_INVALID]"))
        assertTrue(rewritten.message.contains("No valid action source URL."))
    }

    @Test
    fun transportFailureKeepsItsOriginalClassification() {
        val transportFailure =
            OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.REQUEST_TIMEOUT,
                message = "Timed out.",
            )

        assertSame(
            transportFailure,
            OpenAIHostedWebSearchCompatibilityProbePolicy
                .rewriteEvidenceFailure(transportFailure),
        )
    }

    @Test
    fun compatibilityRequiresUrlCitationFromTheFixedPublicPageProbe() {
        val result =
            OpenAIHostedWebSearchResult(
                requestId = "ows_probe",
                responseId = "resp_probe",
                query = "probe",
                mode = OpenAIHostedWebSearchMode.LIVE,
                modelName = "gpt-5.6-sol",
                evidenceMode = OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS,
                answer = "Evidence",
                answerWithSourceMarkers = "Evidence",
                searchActions =
                    listOf(
                        OpenAIHostedWebSearchAction(
                            type = "search",
                            query = "probe",
                            url = null,
                            pattern = null,
                        )
                    ),
                citations = emptyList(),
                citedSources = emptyList(),
                allSources =
                    listOf(
                        OpenAIHostedWebSearchSource(
                            sourceId = "S1",
                            type = "api",
                            title = "time",
                            url = null,
                        )
                    ),
                sourceSummary =
                    OpenAIHostedWebSearchSourceSummary(
                        allSourceCount = 1,
                        citedSourceCount = 0,
                        uncitedSourceCount = 1,
                        urlSourceCount = 0,
                        structuredSourceCount = 1,
                    ),
                usage =
                    OpenAIHostedWebSearchUsage(
                        inputTokens = 1,
                        cachedInputTokens = 0,
                        outputTokens = 1,
                        webSearchCalls = 1,
                    ),
                warnings = emptyList(),
                sourceDiagnostics =
                    OpenAIHostedWebSearchSourceDiagnostics(
                        responseId = "resp_probe",
                        actionSourceCoverage =
                            OpenAIHostedWebSearchActionSourceCoverage.NOT_APPLICABLE,
                        actionSourceUrls = emptyList(),
                        citationUrls = emptyList(),
                        citationsMissingFromActionSources = emptyList(),
                        openPageUrls = emptyList(),
                        missingSourceActionIndexes = emptyList(),
                        invalidActionSourceCount = 0,
                        allowedDomains = emptyList(),
                    ),
            )
        val execution =
            OpenAIHostedWebSearchExecution(
                result = result,
                diagnostics =
                    OpenAIHostedWebSearchResponseParser.Diagnostics(
                        totalActionSourceCount = 1,
                        validActionSourceUrlCount = 0,
                        structuredFeedSourceCount = 1,
                        invalidActionSourceCount = 0,
                        urlCitationCount = 0,
                        evidenceMode = OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS,
                    ),
            )

        val error =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchCompatibilityProbePolicy.requireCompatible(execution)
            }
        assertEquals(OpenAIHostedWebSearchErrorCode.RELAY_INCOMPATIBLE, error.code)
        assertTrue(error.message.contains("structured_feed=1"))
    }

    @Test
    fun compatibilityAcceptsCitationOnlyRelayEvidence() {
        val result =
            OpenAIHostedWebSearchResult(
                requestId = "ows_probe",
                responseId = "resp_probe",
                query = "probe",
                mode = OpenAIHostedWebSearchMode.LIVE,
                modelName = "gpt-5.6-sol",
                evidenceMode = OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
                answer = "Evidence",
                answerWithSourceMarkers = "Evidence[S1]",
                searchActions =
                    listOf(
                        OpenAIHostedWebSearchAction(
                            type = "search",
                            query = "probe",
                            url = null,
                            pattern = null,
                        )
                    ),
                citations =
                    listOf(
                        OpenAIHostedWebSearchCitation(
                            sourceId = "S1",
                            title = "Example",
                            url = "https://example.com/",
                            startIndex = 0,
                            endIndex = 8,
                        )
                    ),
                citedSources =
                    listOf(
                        OpenAIHostedWebSearchSource(
                            sourceId = "S1",
                            type = "url",
                            title = "Example",
                            url = "https://example.com/",
                        )
                    ),
                allSources =
                    listOf(
                        OpenAIHostedWebSearchSource(
                            sourceId = "S1",
                            type = "url",
                            title = "Example",
                            url = "https://example.com/",
                        )
                    ),
                sourceSummary =
                    OpenAIHostedWebSearchSourceSummary(
                        allSourceCount = 1,
                        citedSourceCount = 1,
                        uncitedSourceCount = 0,
                        urlSourceCount = 1,
                        structuredSourceCount = 0,
                    ),
                usage =
                    OpenAIHostedWebSearchUsage(
                        inputTokens = 1,
                        cachedInputTokens = 0,
                        outputTokens = 1,
                        webSearchCalls = 1,
                    ),
                warnings = listOf("ACTION_SOURCES_MISSING"),
                sourceDiagnostics =
                    OpenAIHostedWebSearchSourceDiagnostics(
                        responseId = "resp_probe",
                        actionSourceCoverage =
                            OpenAIHostedWebSearchActionSourceCoverage.MISSING,
                        actionSourceUrls = emptyList(),
                        citationUrls = listOf("https://example.com/"),
                        citationsMissingFromActionSources =
                            listOf("https://example.com/"),
                        openPageUrls = emptyList(),
                        missingSourceActionIndexes = listOf(0),
                        invalidActionSourceCount = 0,
                        allowedDomains = emptyList(),
                    ),
            )
        val execution =
            OpenAIHostedWebSearchExecution(
                result = result,
                diagnostics =
                    OpenAIHostedWebSearchResponseParser.Diagnostics(
                        totalActionSourceCount = 0,
                        validActionSourceUrlCount = 0,
                        structuredFeedSourceCount = 0,
                        invalidActionSourceCount = 0,
                        urlCitationCount = 1,
                        evidenceMode = OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
                    ),
            )

        OpenAIHostedWebSearchCompatibilityProbePolicy.requireCompatible(execution)
    }

    @Test
    fun compatibilityRejectsZeroEvidenceMode() {
        val result =
            OpenAIHostedWebSearchResult(
                requestId = "ows_probe",
                responseId = "resp_probe",
                query = "probe",
                mode = OpenAIHostedWebSearchMode.LIVE,
                modelName = "gpt-5.6-sol",
                evidenceMode = OpenAIHostedWebSearchEvidenceMode.NONE,
                answer = "No matching evidence was returned.",
                answerWithSourceMarkers = "No matching evidence was returned.",
                searchActions =
                    listOf(
                        OpenAIHostedWebSearchAction(
                            type = "search",
                            query = "probe",
                            url = null,
                            pattern = null,
                        )
                    ),
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
                usage =
                    OpenAIHostedWebSearchUsage(
                        inputTokens = 1,
                        cachedInputTokens = 0,
                        outputTokens = 1,
                        webSearchCalls = 1,
                    ),
                warnings = listOf("NO_WEB_EVIDENCE"),
                sourceDiagnostics =
                    OpenAIHostedWebSearchSourceDiagnostics(
                        responseId = "resp_probe",
                        actionSourceCoverage =
                            OpenAIHostedWebSearchActionSourceCoverage.MISSING,
                        actionSourceUrls = emptyList(),
                        citationUrls = emptyList(),
                        citationsMissingFromActionSources = emptyList(),
                        openPageUrls = emptyList(),
                        missingSourceActionIndexes = listOf(0),
                        invalidActionSourceCount = 0,
                        allowedDomains = emptyList(),
                    ),
            )
        val execution =
            OpenAIHostedWebSearchExecution(
                result = result,
                diagnostics =
                    OpenAIHostedWebSearchResponseParser.Diagnostics(
                        totalActionSourceCount = 0,
                        validActionSourceUrlCount = 0,
                        structuredFeedSourceCount = 0,
                        invalidActionSourceCount = 0,
                        urlCitationCount = 0,
                        evidenceMode = OpenAIHostedWebSearchEvidenceMode.NONE,
                        missingSourceActionCount = 1,
                    ),
            )

        val error =
            assertThrows(OpenAIHostedWebSearchException::class.java) {
                OpenAIHostedWebSearchCompatibilityProbePolicy.requireCompatible(execution)
            }

        assertEquals(OpenAIHostedWebSearchErrorCode.RELAY_INCOMPATIBLE, error.code)
    }

    @Test
    fun persistedFailureMessageIsBoundedAndSingleLine() {
        val sanitized =
            OpenAIHostedWebSearchCompatibilityProbePolicy.sanitizeFailureMessage(
                "first\r\nsecond" + "x".repeat(1_000)
            )

        assertTrue(sanitized.startsWith("first second"))
        assertTrue(sanitized.length <= 512)
        assertTrue('\n' !in sanitized)
        assertTrue('\r' !in sanitized)
    }
}
