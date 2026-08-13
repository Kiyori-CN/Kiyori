package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIWebSearchResultPresentationPolicyTest {
    @Test
    fun `initial result keeps every layer collapsed and prepares one source batch`() {
        val state = OpenAIWebSearchResultPresentationPolicy.initial(sourceCount = 19)

        assertFalse(state.summaryExpanded)
        assertFalse(state.sourcesExpanded)
        assertFalse(state.searchTraceExpanded)
        assertFalse(state.diagnosticsExpanded)
        assertEquals(8, state.visibleSourceCount)
    }

    @Test
    fun `summary and level three sections transition independently`() {
        val initial = OpenAIWebSearchResultPresentationPolicy.initial(sourceCount = 4)
        val summary =
            OpenAIWebSearchResultPresentationPolicy.toggleSummary(initial)
        val sources =
            OpenAIWebSearchResultPresentationPolicy.toggleSources(summary, sourceCount = 4)
        val trace =
            OpenAIWebSearchResultPresentationPolicy.toggleSearchTrace(sources)
        val diagnostics =
            OpenAIWebSearchResultPresentationPolicy.toggleDiagnostics(trace)

        assertTrue(summary.summaryExpanded)
        assertTrue(sources.sourcesExpanded)
        assertTrue(trace.searchTraceExpanded)
        assertTrue(diagnostics.diagnosticsExpanded)
        assertTrue(diagnostics.summaryExpanded)
        assertTrue(diagnostics.sourcesExpanded)
        assertTrue(diagnostics.searchTraceExpanded)
    }

    @Test
    fun `sources are revealed in deterministic batches without losing order capacity`() {
        var state = OpenAIWebSearchResultPresentationPolicy.initial(sourceCount = 19)
        state = OpenAIWebSearchResultPresentationPolicy.toggleSources(state, sourceCount = 19)
        state = OpenAIWebSearchResultPresentationPolicy.showMoreSources(state, sourceCount = 19)
        assertEquals(16, state.visibleSourceCount)

        state = OpenAIWebSearchResultPresentationPolicy.showMoreSources(state, sourceCount = 19)
        assertEquals(19, state.visibleSourceCount)

        state = OpenAIWebSearchResultPresentationPolicy.showMoreSources(state, sourceCount = 19)
        assertEquals(19, state.visibleSourceCount)
    }

    @Test
    fun `presentation key isolates renderer request and schema identities`() {
        val base =
            OpenAIWebSearchResultPresentationKey(
                renderInstanceKey = "message-1",
                requestId = "ows-1",
            )

        assertNotEquals(base, base.copy(renderInstanceKey = "message-2"))
        assertNotEquals(base, base.copy(requestId = "ows-2"))
        assertNotEquals(
            base,
            base.copy(schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION + 1),
        )
    }

    @Test
    fun `audit differences never use error severity`() {
        assertEquals(
            OpenAIWebSearchWarningSeverity.INFO,
            OpenAIWebSearchResultPresentationPolicy.warningSeverity(
                "CITATION_NOT_IN_ACTION_SOURCES"
            ),
        )
        assertEquals(
            OpenAIWebSearchWarningSeverity.WARNING,
            OpenAIWebSearchResultPresentationPolicy.warningSeverity(
                "ACTION_SOURCES_MISSING"
            ),
        )
        assertEquals(
            OpenAIWebSearchWarningSeverity.WARNING,
            OpenAIWebSearchResultPresentationPolicy.warningSeverity(
                "ACTION_SOURCES_PARTIAL"
            ),
        )
        assertEquals(
            OpenAIWebSearchWarningSeverity.ERROR,
            OpenAIWebSearchResultPresentationPolicy.warningSeverity(
                "ACTION_SOURCE_INVALID:output=0,source=0,reason=missing_url"
            ),
        )
    }

    @Test
    fun `failure presentation extracts only the structured code prefix`() {
        assertEquals(
            OpenAIWebSearchFailurePresentation(
                code = "REQUEST_TIMEOUT",
                message = "The request timed out.",
            ),
            OpenAIWebSearchResultPresentationPolicy.failurePresentation(
                "[REQUEST_TIMEOUT] The request timed out."
            ),
        )
        assertEquals(
            OpenAIWebSearchFailurePresentation(
                code = null,
                message = "context_size must be low, medium, or high",
            ),
            OpenAIWebSearchResultPresentationPolicy.failurePresentation(
                "context_size must be low, medium, or high"
            ),
        )
    }

    @Test
    fun `parse failure log gate deduplicates and stays bounded`() {
        val gate = OpenAIWebSearchEvidenceParseFailureLogGate(maxEntries = 2)
        val first = logKey(identity = 1)
        val second = logKey(identity = 2)
        val third = logKey(identity = 3)

        assertTrue(gate.claim(first))
        assertFalse(gate.claim(first))
        assertTrue(gate.claim(second))
        assertTrue(gate.claim(third))
        assertTrue(gate.claim(first))
    }

    private fun logKey(
        identity: Int,
    ): OpenAIWebSearchEvidenceParseFailureLogKey =
        OpenAIWebSearchEvidenceParseFailureLogKey(
            renderInstanceKey = identity,
            code = "SCHEMA_REVISION_MISMATCH",
            schemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION - 1,
            hasRequestId = true,
            fieldName = "schema_version",
        )
}
