package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract

internal data class OpenAIWebSearchResultPresentationKey(
    val renderInstanceKey: Any?,
    val requestId: String,
    val schemaRevision: Int = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
)

internal data class OpenAIWebSearchResultPresentationState(
    val summaryExpanded: Boolean = false,
    val sourcesExpanded: Boolean = false,
    val searchTraceExpanded: Boolean = false,
    val diagnosticsExpanded: Boolean = false,
    val visibleSourceCount: Int = 0,
)

internal data class OpenAIWebSearchFailurePresentation(
    val code: String?,
    val message: String,
)

internal enum class OpenAIWebSearchWarningSeverity {
    INFO,
    WARNING,
    ERROR,
}

internal object OpenAIWebSearchResultPresentationPolicy {
    const val SOURCE_BATCH_SIZE = 8

    fun initial(sourceCount: Int): OpenAIWebSearchResultPresentationState {
        require(sourceCount >= 0)
        return OpenAIWebSearchResultPresentationState(
            visibleSourceCount = minOf(sourceCount, SOURCE_BATCH_SIZE),
        )
    }

    fun toggleSummary(
        state: OpenAIWebSearchResultPresentationState,
    ): OpenAIWebSearchResultPresentationState =
        state.copy(summaryExpanded = !state.summaryExpanded)

    fun toggleSources(
        state: OpenAIWebSearchResultPresentationState,
        sourceCount: Int,
    ): OpenAIWebSearchResultPresentationState {
        require(sourceCount >= 0)
        return state.copy(
            sourcesExpanded = !state.sourcesExpanded,
            visibleSourceCount =
                state.visibleSourceCount.coerceIn(
                    minimumValue = minOf(sourceCount, SOURCE_BATCH_SIZE),
                    maximumValue = sourceCount,
                ),
        )
    }

    fun showMoreSources(
        state: OpenAIWebSearchResultPresentationState,
        sourceCount: Int,
    ): OpenAIWebSearchResultPresentationState {
        require(sourceCount >= 0)
        return state.copy(
            visibleSourceCount =
                minOf(
                    sourceCount,
                    state.visibleSourceCount + SOURCE_BATCH_SIZE,
                ),
        )
    }

    fun toggleSearchTrace(
        state: OpenAIWebSearchResultPresentationState,
    ): OpenAIWebSearchResultPresentationState =
        state.copy(searchTraceExpanded = !state.searchTraceExpanded)

    fun toggleDiagnostics(
        state: OpenAIWebSearchResultPresentationState,
    ): OpenAIWebSearchResultPresentationState =
        state.copy(diagnosticsExpanded = !state.diagnosticsExpanded)

    fun warningSeverity(code: String): OpenAIWebSearchWarningSeverity {
        if (code.startsWith("ACTION_SOURCE_INVALID:")) {
            return OpenAIWebSearchWarningSeverity.ERROR
        }
        if (code.startsWith("ACTION_SOURCE_COMPATIBILITY:")) {
            return OpenAIWebSearchWarningSeverity.INFO
        }
        return when (code) {
            "CITATION_NOT_IN_ACTION_SOURCES",
            "URL_EVIDENCE_NOT_APPLICABLE" -> OpenAIWebSearchWarningSeverity.INFO

            "ACTION_SOURCES_MISSING",
            "ACTION_SOURCES_PARTIAL",
            "URL_CITATIONS_MISSING",
            "USAGE_MISSING" -> OpenAIWebSearchWarningSeverity.WARNING

            else -> OpenAIWebSearchWarningSeverity.WARNING
        }
    }

    fun failurePresentation(errorText: String): OpenAIWebSearchFailurePresentation {
        val normalized = errorText.trim()
        val match = ERROR_CODE_PREFIX.matchEntire(normalized)
        return if (match == null) {
            OpenAIWebSearchFailurePresentation(
                code = null,
                message = normalized,
            )
        } else {
            OpenAIWebSearchFailurePresentation(
                code = match.groupValues[1],
                message = match.groupValues[2].trim(),
            )
        }
    }

    private val ERROR_CODE_PREFIX =
        Regex(
            pattern = """\[([A-Z][A-Z0-9_]*)]\s*([\s\S]*)""",
        )
}
