package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class OpenAIHostedWebSearchEvidence(
    val requestId: String,
    val responseId: String,
    val query: String,
    val mode: String,
    val model: String,
    val evidenceMode: OpenAIHostedWebSearchEvidenceMode,
    val answer: String,
    val answerWithSourceMarkers: String,
    val searchActions: List<OpenAIHostedWebSearchEvidenceAction>,
    val citations: List<OpenAIHostedWebSearchEvidenceCitation>,
    val sources: List<OpenAIHostedWebSearchEvidenceSource>,
    val usage: OpenAIHostedWebSearchEvidenceUsage,
    val warnings: List<String>,
    val sourceDiagnostics: OpenAIHostedWebSearchEvidenceSourceDiagnostics,
)

internal data class OpenAIHostedWebSearchEvidenceAction(
    val type: String,
    val query: String?,
    val url: String?,
    val pattern: String?,
)

internal data class OpenAIHostedWebSearchEvidenceCitation(
    val sourceId: String,
    val title: String,
    val url: String,
    val startIndex: Int,
    val endIndex: Int,
)

internal data class OpenAIHostedWebSearchEvidenceSource(
    val sourceId: String,
    val type: String,
    val title: String,
    val url: String?,
)

internal data class OpenAIHostedWebSearchEvidenceUsage(
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val webSearchCalls: Int,
)

internal data class OpenAIHostedWebSearchEvidenceSourceDiagnostics(
    val responseId: String,
    val actionSourceCoverage: OpenAIHostedWebSearchActionSourceCoverage,
    val actionSourceUrls: List<String>,
    val citationUrls: List<String>,
    val citationsMissingFromActionSources: List<String>,
    val openPageUrls: List<String>,
    val missingSourceActionIndexes: List<Int>,
    val invalidActionSourceCount: Int,
    val allowedDomains: List<String>,
)

internal object OpenAIHostedWebSearchEvidenceParser {
    fun parseOrNull(toolName: String, resultJson: String): OpenAIHostedWebSearchEvidence? {
        if (toolName != OpenAIHostedWebSearchContract.TOOL_NAME) {
            return null
        }
        return runCatching { parse(resultJson) }.getOrNull()
    }

    private fun parse(resultJson: String): OpenAIHostedWebSearchEvidence {
        val root =
            JSONTokener(resultJson.trim()).nextValue() as? JSONObject
                ?: throw IllegalArgumentException("Web Search evidence must be a JSON object")
        require(root.requireBoolean("success"))
        require(root.requireInt("schema_version") == OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION)
        require(root.requireString("provider") == "openai")
        require(root.requireString("backend") == "responses_web_search")

        val answer = root.requireString("answer")
        val mode = root.requireString("mode")
        require(mode == "live" || mode == "indexed")
        val evidenceMode =
            OpenAIHostedWebSearchEvidenceMode.parse(
                root.requireString("evidence_mode")
            )

        val sources =
            root.requireArray("sources").mapObjects { source ->
                val type = source.requireNonBlankString("type")
                val url =
                    when {
                        type == "url" ->
                            normalizeOpenAIHostedWebSearchUrl(
                                source.requireNonBlankString("url")
                            )

                        type in
                            OpenAIHostedWebSearchContract.OFFICIAL_REALTIME_FEED_SOURCE_TYPES -> {
                            require(source.isNull("url"))
                            null
                        }

                        type in
                            OpenAIHostedWebSearchContract.RELAY_STRUCTURED_FEED_SOURCE_TYPES -> {
                            require(source.isNull("url"))
                            null
                        }

                        else -> throw IllegalArgumentException("Unsupported source type")
                    }
                OpenAIHostedWebSearchEvidenceSource(
                    sourceId = source.requireNonBlankString("source_id"),
                    type = type,
                    title = source.requireNonBlankString("title"),
                    url = url,
                )
            }
        require(sources.isNotEmpty())
        require(sources.map(OpenAIHostedWebSearchEvidenceSource::sourceId).distinct().size == sources.size)
        val sourcesById = sources.associateBy(OpenAIHostedWebSearchEvidenceSource::sourceId)

        val citations =
            root.requireArray("citations").mapObjects { citation ->
                val sourceId = citation.requireNonBlankString("source_id")
                val url = normalizeOpenAIHostedWebSearchUrl(citation.requireString("url"))
                val startIndex = citation.requireInt("start_index")
                val endIndex = citation.requireInt("end_index")
                val source = requireNotNull(sourcesById[sourceId])
                require(source.type == "url")
                require(url == source.url)
                require(startIndex >= 0 && endIndex > startIndex && endIndex <= answer.length)
                OpenAIHostedWebSearchEvidenceCitation(
                    sourceId = sourceId,
                    title = citation.requireNonBlankString("title"),
                    url = url,
                    startIndex = startIndex,
                    endIndex = endIndex,
                )
            }
        when (evidenceMode) {
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES,
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS -> require(citations.isNotEmpty())

            OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES,
            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS -> require(citations.isEmpty())
        }
        when (evidenceMode) {
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES,
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
            OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES ->
                require(sources.any { source -> source.type == "url" })

            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS ->
                require(sources.none { source -> source.type == "url" })
        }

        val actions =
            root.requireArray("search_actions").mapObjects { action ->
                val actionUrl =
                    action.readNullableString("url")
                        ?.let(::normalizeOpenAIHostedWebSearchUrl)
                OpenAIHostedWebSearchEvidenceAction(
                    type = action.requireNonBlankString("type"),
                    query = action.readNullableString("query"),
                    url = actionUrl,
                    pattern = action.readNullableString("pattern"),
                )
            }
        require(actions.isNotEmpty())

        val usageObject = root.requireObject("usage")
        val usage =
            OpenAIHostedWebSearchEvidenceUsage(
                inputTokens = usageObject.requireNonNegativeInt("input_tokens"),
                cachedInputTokens = usageObject.requireNonNegativeInt("cached_input_tokens"),
                outputTokens = usageObject.requireNonNegativeInt("output_tokens"),
                webSearchCalls = usageObject.requireNonNegativeInt("web_search_calls"),
            )
        require(usage.webSearchCalls > 0)

        val sourceDiagnosticsObject = root.requireObject("source_diagnostics")
        val sourceDiagnostics =
            OpenAIHostedWebSearchEvidenceSourceDiagnostics(
                responseId = sourceDiagnosticsObject.requireNonBlankString("response_id"),
                actionSourceCoverage =
                    OpenAIHostedWebSearchActionSourceCoverage.parse(
                        sourceDiagnosticsObject.requireNonBlankString(
                            "action_source_coverage"
                        )
                    ),
                actionSourceUrls =
                    sourceDiagnosticsObject.requireArray("action_source_urls")
                        .mapStrings()
                        .map(::normalizeOpenAIHostedWebSearchUrl)
                        .distinct(),
                citationUrls =
                    sourceDiagnosticsObject.requireArray("citation_urls")
                        .mapStrings()
                        .map(::normalizeOpenAIHostedWebSearchUrl)
                        .distinct(),
                citationsMissingFromActionSources =
                    sourceDiagnosticsObject
                        .requireArray("citations_missing_from_action_sources")
                        .mapStrings()
                        .map(::normalizeOpenAIHostedWebSearchUrl)
                        .distinct(),
                openPageUrls =
                    sourceDiagnosticsObject.requireArray("open_page_urls")
                        .mapStrings()
                        .map(::normalizeOpenAIHostedWebSearchUrl)
                        .distinct(),
                missingSourceActionIndexes =
                    sourceDiagnosticsObject.requireArray("missing_source_action_indexes")
                        .mapNonNegativeInts()
                        .distinct(),
                invalidActionSourceCount =
                    sourceDiagnosticsObject.requireNonNegativeInt(
                        "invalid_action_source_count"
                    ),
                allowedDomains =
                    sourceDiagnosticsObject.requireArray("allowed_domains")
                        .mapStrings()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct(),
            )
        require(sourceDiagnostics.responseId == root.requireNonBlankString("response_id"))
        require(
            sourceDiagnosticsObject.requireString("url_normalization") ==
                "http_https_uri"
        )
        val sourceUrls = sources.mapNotNull(OpenAIHostedWebSearchEvidenceSource::url).toSet()
        val citationUrls = citations.map(OpenAIHostedWebSearchEvidenceCitation::url).distinct()
        require(sourceDiagnostics.citationUrls == citationUrls)
        require(sourceDiagnostics.actionSourceUrls.all(sourceUrls::contains))
        require(
            sourceDiagnostics.citationsMissingFromActionSources ==
                sourceDiagnostics.citationUrls.filterNot(
                    sourceDiagnostics.actionSourceUrls::contains
                )
        )
        require(
            sourceDiagnostics.openPageUrls ==
                actions
                    .asSequence()
                    .filter { action -> action.type == "open_page" }
                    .mapNotNull(OpenAIHostedWebSearchEvidenceAction::url)
                    .distinct()
                    .toList()
        )
        val hasSearchActions =
            actions.any { action -> action.type == "search" }
        val expectedCoverage =
            when {
                !hasSearchActions ->
                    OpenAIHostedWebSearchActionSourceCoverage.NOT_APPLICABLE

                sourceDiagnostics.invalidActionSourceCount > 0 ||
                    (
                        sourceDiagnostics.missingSourceActionIndexes.isNotEmpty() &&
                            sourceDiagnostics.missingSourceActionIndexes.size <
                                actions.count { action -> action.type == "search" }
                    ) ->
                    OpenAIHostedWebSearchActionSourceCoverage.PARTIAL

                sourceDiagnostics.actionSourceUrls.isEmpty() &&
                    sourceDiagnostics.missingSourceActionIndexes.isNotEmpty() ->
                    OpenAIHostedWebSearchActionSourceCoverage.MISSING

                else -> OpenAIHostedWebSearchActionSourceCoverage.COMPLETE
            }
        require(sourceDiagnostics.actionSourceCoverage == expectedCoverage)

        return OpenAIHostedWebSearchEvidence(
            requestId = root.requireNonBlankString("request_id"),
            responseId = root.requireNonBlankString("response_id"),
            query = root.requireNonBlankString("query"),
            mode = mode,
            model = root.requireNonBlankString("model"),
            evidenceMode = evidenceMode,
            answer = answer,
            answerWithSourceMarkers = root.requireString("answer_with_source_markers"),
            searchActions = actions,
            citations = citations,
            sources = sources,
            usage = usage,
            warnings = root.requireArray("warnings").mapStrings(),
            sourceDiagnostics = sourceDiagnostics,
        )
    }

    private fun JSONObject.requireObject(key: String): JSONObject =
        opt(key) as? JSONObject
            ?: throw IllegalArgumentException("$key must be an object")

    private fun JSONObject.requireArray(key: String): JSONArray =
        opt(key) as? JSONArray
            ?: throw IllegalArgumentException("$key must be an array")

    private fun JSONObject.requireString(key: String): String =
        opt(key) as? String
            ?: throw IllegalArgumentException("$key must be a string")

    private fun JSONObject.requireNonBlankString(key: String): String =
        requireString(key).also { value -> require(value.isNotBlank()) }

    private fun JSONObject.requireBoolean(key: String): Boolean =
        opt(key) as? Boolean
            ?: throw IllegalArgumentException("$key must be a boolean")

    private fun JSONObject.requireInt(key: String): Int {
        val value = opt(key)
        require(value is Int)
        return value
    }

    private fun JSONObject.requireNonNegativeInt(key: String): Int =
        requireInt(key).also { value -> require(value >= 0) }

    private fun JSONObject.readNullableString(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return requireString(key).takeIf(String::isNotBlank)
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList {
            for (index in 0 until length()) {
                val value =
                    opt(index) as? JSONObject
                        ?: throw IllegalArgumentException("Array item $index must be an object")
                add(transform(value))
            }
        }

    private fun JSONArray.mapStrings(): List<String> =
        buildList {
            for (index in 0 until length()) {
                val value =
                    opt(index) as? String
                        ?: throw IllegalArgumentException("Array item $index must be a string")
                add(value)
            }
        }

    private fun JSONArray.mapNonNegativeInts(): List<Int> =
        buildList {
            for (index in 0 until length()) {
                val value =
                    opt(index) as? Int
                        ?: throw IllegalArgumentException("Array item $index must be an integer")
                require(value >= 0)
                add(value)
            }
        }
}
