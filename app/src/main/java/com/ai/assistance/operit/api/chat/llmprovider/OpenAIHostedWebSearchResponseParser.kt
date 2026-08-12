package com.ai.assistance.operit.api.chat.llmprovider

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses a completed non-streaming Responses object produced by the hosted Web Search tool.
 *
 * Citation offsets are kept against the original search answer. Source markers are inserted only
 * into a derived copy so the original annotation spans remain auditable.
 */
internal object OpenAIHostedWebSearchResponseParser {
    private data class RawSource(
        val url: String,
        val title: String?,
    )

    private data class RawFeedSource(
        val type: String,
        val title: String,
    )

    private data class RawCitation(
        val url: String,
        val title: String?,
        val startIndex: Int,
        val endIndex: Int,
    )

    private data class ActionSourceIssue(
        val outputIndex: Int,
        val sourceIndex: Int,
        val reason: String,
        val invalid: Boolean,
    ) {
        fun warning(): String =
            "${if (invalid) "ACTION_SOURCE_INVALID" else "ACTION_SOURCE_COMPATIBILITY"}:" +
                "output=$outputIndex,source=$sourceIndex,reason=$reason"
    }

    private enum class ActionSourceClassification {
        VALID_URL,
        STRUCTURED_FEED,
        INVALID,
    }

    internal data class Diagnostics(
        val totalActionSourceCount: Int,
        val validActionSourceUrlCount: Int,
        val structuredFeedSourceCount: Int,
        val invalidActionSourceCount: Int,
        val urlCitationCount: Int,
        val evidenceMode: OpenAIHostedWebSearchEvidenceMode,
        val citationMissingFromActionSourceCount: Int = 0,
        val missingSourceActionCount: Int = 0,
    )

    internal data class ParsedResponse(
        val result: OpenAIHostedWebSearchResult,
        val diagnostics: Diagnostics,
    )

    fun parse(
        responseJson: JSONObject,
        request: OpenAIHostedWebSearchEffectiveRequest,
        binding: OpenAIHostedWebSearchBinding,
    ): OpenAIHostedWebSearchResult =
        parseWithDiagnostics(
            responseJson = responseJson,
            request = request,
            binding = binding,
        ).result

    fun parseWithDiagnostics(
        responseJson: JSONObject,
        request: OpenAIHostedWebSearchEffectiveRequest,
        binding: OpenAIHostedWebSearchBinding,
    ): ParsedResponse {
        val responseId = responseJson.optString("id", "").trim()
        if (responseId.isEmpty()) {
            throw schemaFailure("OpenAI Web Search response is missing an ID.")
        }
        val responseStatus = responseJson.optString("status", "").trim()
        if (responseStatus != "completed") {
            throw schemaFailure("OpenAI Web Search response did not complete successfully.")
        }

        val output =
            responseJson.optJSONArray("output")
                ?: throw schemaFailure("OpenAI Web Search response is missing output items.")
        val actions = mutableListOf<OpenAIHostedWebSearchAction>()
        val actionSourcesByUrl = linkedMapOf<String, RawSource>()
        val feedSourcesByType = linkedMapOf<String, RawFeedSource>()
        val citationTitlesByUrl = linkedMapOf<String, String?>()
        val rawCitations = mutableListOf<RawCitation>()
        val actionSourceIssues = mutableListOf<ActionSourceIssue>()
        val openPageUrls = linkedSetOf<String>()
        val searchActionIndexes = linkedSetOf<Int>()
        val missingSourceActionIndexes = linkedSetOf<Int>()
        val answer = StringBuilder()
        var webSearchCalls = 0
        var totalActionSourceCount = 0
        var validActionSourceUrlCount = 0
        var structuredFeedSourceCount = 0

        for (outputIndex in 0 until output.length()) {
            val item = output.optJSONObject(outputIndex) ?: continue
            when (item.optString("type", "")) {
                "web_search_call" -> {
                    webSearchCalls += 1
                    parseSearchAction(
                        action = item.optJSONObject("action")
                            ?: throw schemaFailure(
                                "OpenAI Web Search call is missing its action object."
                            ),
                        outputIndex = outputIndex,
                        providerContract = binding.providerContract,
                        actions = actions,
                        actionSourcesByUrl = actionSourcesByUrl,
                        feedSourcesByType = feedSourcesByType,
                        actionSourceIssues = actionSourceIssues,
                        openPageUrls = openPageUrls,
                        searchActionIndexes = searchActionIndexes,
                        missingSourceActionIndexes = missingSourceActionIndexes,
                        onActionSource = { totalActionSourceCount += 1 },
                        onValidActionSourceUrl = { validActionSourceUrlCount += 1 },
                        onStructuredFeedSource = { structuredFeedSourceCount += 1 },
                    )
                }

                "message" -> {
                    parseMessage(
                        item = item,
                        answer = answer,
                        rawCitations = rawCitations,
                        citationTitlesByUrl = citationTitlesByUrl,
                    )
                }
            }
        }

        if (webSearchCalls == 0) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.SEARCH_TOOL_NOT_CALLED,
                message = "The Responses backend did not call the hosted Web Search tool.",
            )
        }
        val answerText = answer.toString()
        if (answerText.isBlank()) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.SEARCH_OUTPUT_EMPTY,
                message = "The hosted Web Search response did not contain answer text.",
            )
        }
        val hasUrlCitations = rawCitations.isNotEmpty()
        val hasActionSourceUrls = actionSourcesByUrl.isNotEmpty()
        val hasStructuredFeeds = feedSourcesByType.isNotEmpty()
        val actionSourceUrls = actionSourcesByUrl.keys.toList()
        val citationUrls = rawCitations.map(RawCitation::url).distinct()
        val citationsMissingFromActionSources =
            citationUrls.filterNot(actionSourcesByUrl::containsKey)
        val invalidActionSourceCount =
            actionSourceIssues.count(ActionSourceIssue::invalid)
        val actionSourceCoverage =
            when {
                searchActionIndexes.isEmpty() ->
                    OpenAIHostedWebSearchActionSourceCoverage.NOT_APPLICABLE

                invalidActionSourceCount > 0 ||
                    (
                        missingSourceActionIndexes.isNotEmpty() &&
                            missingSourceActionIndexes.size < searchActionIndexes.size
                    ) ->
                    OpenAIHostedWebSearchActionSourceCoverage.PARTIAL

                missingSourceActionIndexes.size == searchActionIndexes.size ->
                    OpenAIHostedWebSearchActionSourceCoverage.MISSING

                else -> OpenAIHostedWebSearchActionSourceCoverage.COMPLETE
            }
        val sourceDiagnostics =
            OpenAIHostedWebSearchSourceDiagnostics(
                responseId = responseId,
                actionSourceCoverage = actionSourceCoverage,
                actionSourceUrls = actionSourceUrls,
                citationUrls = citationUrls,
                citationsMissingFromActionSources = citationsMissingFromActionSources,
                openPageUrls = openPageUrls.toList(),
                missingSourceActionIndexes = missingSourceActionIndexes.toList(),
                invalidActionSourceCount = invalidActionSourceCount,
                allowedDomains = request.allowedDomains,
            )
        if (
            binding.providerContract ==
                OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL
        ) {
            if (!hasUrlCitations && (hasActionSourceUrls || !hasStructuredFeeds)) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
                    message = "The hosted Web Search response did not contain URL citations.",
                )
            }
        } else {
            if (!hasUrlCitations && !hasActionSourceUrls && !hasStructuredFeeds) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.RELAY_RESPONSE_TEXT_ONLY,
                    message =
                        "The relay returned Web Search answer text without URL citations, " +
                            "URL action sources, or a recognized structured feed.",
                )
            }
        }
        citationTitlesByUrl.forEach { (url, title) ->
            mergeSource(
                sourcesByUrl = actionSourcesByUrl,
                source = RawSource(url = url, title = title),
            )
        }

        val evidenceMode =
            when {
                hasUrlCitations && hasActionSourceUrls ->
                    OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES

                hasUrlCitations -> OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS
                hasActionSourceUrls -> OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES
                else -> OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS
            }
        val sourceIdsByUrl =
            actionSourcesByUrl.keys.mapIndexed { index, url -> url to "S${index + 1}" }.toMap()
        val urlSources =
            actionSourcesByUrl.values.map { source ->
                OpenAIHostedWebSearchSource(
                    sourceId = requireNotNull(sourceIdsByUrl[source.url]),
                    type = "url",
                    title = source.title?.takeIf(String::isNotBlank) ?: sourceHost(source.url),
                    url = source.url,
                )
            }
        val feedSources =
            feedSourcesByType.values.mapIndexed { index, source ->
                OpenAIHostedWebSearchSource(
                    sourceId = "S${urlSources.size + index + 1}",
                    type = source.type,
                    title = source.title,
                    url = null,
                )
            }
        val sources = urlSources + feedSources
        val citations =
            rawCitations.map { citation ->
                OpenAIHostedWebSearchCitation(
                    sourceId = requireNotNull(sourceIdsByUrl[citation.url]),
                    title =
                        citation.title?.takeIf(String::isNotBlank)
                            ?: actionSourcesByUrl.getValue(citation.url).title
                                ?.takeIf(String::isNotBlank)
                            ?: sourceHost(citation.url),
                    url = citation.url,
                    startIndex = citation.startIndex,
                    endIndex = citation.endIndex,
                )
            }

        val warnings = mutableListOf<String>()
        warnings += actionSourceIssues.map(ActionSourceIssue::warning)
        if (hasUrlCitations) {
            when (actionSourceCoverage) {
                OpenAIHostedWebSearchActionSourceCoverage.MISSING ->
                    warnings += "ACTION_SOURCES_MISSING"

                OpenAIHostedWebSearchActionSourceCoverage.PARTIAL ->
                    warnings += "ACTION_SOURCES_PARTIAL"

                OpenAIHostedWebSearchActionSourceCoverage.NOT_APPLICABLE,
                OpenAIHostedWebSearchActionSourceCoverage.COMPLETE -> Unit
            }
            if (
                hasActionSourceUrls &&
                    citationsMissingFromActionSources.isNotEmpty()
            ) {
                warnings += "CITATION_NOT_IN_ACTION_SOURCES"
            }
        }
        when (evidenceMode) {
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES -> Unit

            OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES ->
                warnings += "URL_CITATIONS_MISSING"

            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS ->
                warnings += "URL_EVIDENCE_NOT_APPLICABLE"
        }
        val parsedUsage =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                responseJson.optJSONObject("usage")
            )
        if (parsedUsage == null) {
            warnings += "USAGE_MISSING"
        }
        val usage =
            OpenAIHostedWebSearchUsage(
                inputTokens = parsedUsage?.totalInputTokens ?: 0,
                cachedInputTokens = parsedUsage?.cachedInputTokens ?: 0,
                outputTokens = parsedUsage?.outputTokens ?: 0,
                webSearchCalls = webSearchCalls,
            )

        return ParsedResponse(
            result =
                OpenAIHostedWebSearchResult(
                    requestId = request.requestId,
                    responseId = responseId,
                    query = request.query,
                    mode = binding.mode,
                    modelName = binding.modelName,
                    evidenceMode = evidenceMode,
                    answer = answerText,
                    answerWithSourceMarkers = insertSourceMarkers(answerText, citations),
                    searchActions = actions,
                    citations = citations,
                    sources = sources,
                    usage = usage,
                    warnings = warnings,
                    sourceDiagnostics = sourceDiagnostics,
                ),
            diagnostics =
                Diagnostics(
                    totalActionSourceCount = totalActionSourceCount,
                    validActionSourceUrlCount = validActionSourceUrlCount,
                    structuredFeedSourceCount = structuredFeedSourceCount,
                    invalidActionSourceCount = invalidActionSourceCount,
                    urlCitationCount = rawCitations.size,
                    evidenceMode = evidenceMode,
                    citationMissingFromActionSourceCount =
                        citationsMissingFromActionSources.size,
                    missingSourceActionCount = missingSourceActionIndexes.size,
                ),
        )
    }

    private fun parseSearchAction(
        action: JSONObject,
        outputIndex: Int,
        providerContract: OpenAIHostedWebSearchProviderContract,
        actions: MutableList<OpenAIHostedWebSearchAction>,
        actionSourcesByUrl: LinkedHashMap<String, RawSource>,
        feedSourcesByType: LinkedHashMap<String, RawFeedSource>,
        actionSourceIssues: MutableList<ActionSourceIssue>,
        openPageUrls: MutableSet<String>,
        searchActionIndexes: MutableSet<Int>,
        missingSourceActionIndexes: MutableSet<Int>,
        onActionSource: () -> Unit,
        onValidActionSourceUrl: () -> Unit,
        onStructuredFeedSource: () -> Unit,
    ) {
        val type = action.optString("type", "").trim()
        if (type.isEmpty()) {
            throw schemaFailure("OpenAI Web Search action is missing its type.")
        }

        val queries = mutableListOf<String>()
        action.optString("query", "").trim().takeIf(String::isNotEmpty)?.let(queries::add)
        action.optJSONArray("queries")?.let { queryArray ->
            for (index in 0 until queryArray.length()) {
                val query = queryArray.opt(index)
                if (query !is String) {
                    throw schemaFailure("OpenAI Web Search action queries must be strings.")
                }
                query.trim().takeIf(String::isNotEmpty)?.let(queries::add)
            }
        }
        val actionUrl =
            action.optString("url", "").trim().takeIf(String::isNotEmpty)?.let {
                normalizeOpenAIHostedWebSearchUrl(it)
            }
        if (type == "open_page" && actionUrl != null) {
            openPageUrls += actionUrl
        }
        val pattern = action.optString("pattern", "").trim().takeIf(String::isNotEmpty)

        if (queries.isEmpty()) {
            actions +=
                OpenAIHostedWebSearchAction(
                    type = type,
                    query = null,
                    url = actionUrl,
                    pattern = pattern,
                )
        } else {
            queries.distinct().forEach { query ->
                actions +=
                    OpenAIHostedWebSearchAction(
                        type = type,
                        query = query,
                        url = actionUrl,
                        pattern = pattern,
                    )
            }
        }

        val rawSources = action.opt("sources")
        val sourceArray = rawSources as? JSONArray
        if (type == "search") {
            searchActionIndexes += outputIndex
        }
        if (type == "search" && (sourceArray == null || sourceArray.length() == 0)) {
            missingSourceActionIndexes += outputIndex
        }
        if (
            rawSources != null &&
                rawSources != JSONObject.NULL &&
                sourceArray == null
        ) {
            actionSourceIssues +=
                ActionSourceIssue(
                    outputIndex = outputIndex,
                    sourceIndex = -1,
                    reason = "sources_not_array",
                    invalid = true,
                )
        }
        sourceArray?.let {
            for (index in 0 until sourceArray.length()) {
                onActionSource()
                val sourceValue = sourceArray.opt(index)
                if (sourceValue !is JSONObject) {
                    actionSourceIssues +=
                        ActionSourceIssue(
                            outputIndex = outputIndex,
                            sourceIndex = index,
                            reason = "not_object",
                            invalid = true,
                        )
                    continue
                }
                when (
                    collectSource(
                        sourceObject = sourceValue,
                        outputIndex = outputIndex,
                        sourceIndex = index,
                        providerContract = providerContract,
                        actionSourcesByUrl = actionSourcesByUrl,
                        feedSourcesByType = feedSourcesByType,
                        actionSourceIssues = actionSourceIssues,
                    )
                ) {
                    ActionSourceClassification.VALID_URL -> onValidActionSourceUrl()
                    ActionSourceClassification.STRUCTURED_FEED -> onStructuredFeedSource()
                    ActionSourceClassification.INVALID -> Unit
                }
            }
        }
    }

    private fun parseMessage(
        item: JSONObject,
        answer: StringBuilder,
        rawCitations: MutableList<RawCitation>,
        citationTitlesByUrl: LinkedHashMap<String, String?>,
    ) {
        val content = item.optJSONArray("content") ?: return
        for (contentIndex in 0 until content.length()) {
            val part = content.optJSONObject(contentIndex) ?: continue
            val partType = part.optString("type", "")
            if (partType != "output_text" && partType != "text") {
                continue
            }
            val text = part.optString("text", "")
            if (text.isEmpty()) {
                continue
            }
            if (answer.isNotEmpty()) {
                answer.append('\n')
            }
            val answerOffset = answer.length
            answer.append(text)
            parseAnnotations(
                annotations = part.optJSONArray("annotations"),
                partText = text,
                answerOffset = answerOffset,
                rawCitations = rawCitations,
                citationTitlesByUrl = citationTitlesByUrl,
            )
        }
    }

    private fun parseAnnotations(
        annotations: JSONArray?,
        partText: String,
        answerOffset: Int,
        rawCitations: MutableList<RawCitation>,
        citationTitlesByUrl: LinkedHashMap<String, String?>,
    ) {
        annotations ?: return
        for (annotationIndex in 0 until annotations.length()) {
            val annotation = annotations.optJSONObject(annotationIndex) ?: continue
            if (annotation.optString("type", "") != "url_citation") {
                continue
            }
            val rawUrl = annotation.optString("url", "").trim()
            if (rawUrl.isEmpty()) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
                    message = "OpenAI Web Search URL citation is missing its URL.",
                )
            }
            val startIndex = annotation.optInt("start_index", -1)
            val endIndex = annotation.optInt("end_index", -1)
            if (startIndex < 0 || endIndex <= startIndex || endIndex > partText.length) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
                    message = "OpenAI Web Search citation span is outside the answer text.",
                )
            }
            val url = normalizeOpenAIHostedWebSearchUrl(rawUrl)
            val title = annotation.optString("title", "").trim().takeIf(String::isNotEmpty)
            val existingTitle = citationTitlesByUrl[url]
            if (existingTitle.isNullOrBlank() && !title.isNullOrBlank()) {
                citationTitlesByUrl[url] = title
            } else if (!citationTitlesByUrl.containsKey(url)) {
                citationTitlesByUrl[url] = title
            }
            rawCitations +=
                RawCitation(
                    url = url,
                    title = title,
                    startIndex = answerOffset + startIndex,
                    endIndex = answerOffset + endIndex,
                )
        }
    }

    private fun collectSource(
        sourceObject: JSONObject,
        outputIndex: Int,
        sourceIndex: Int,
        providerContract: OpenAIHostedWebSearchProviderContract,
        actionSourcesByUrl: LinkedHashMap<String, RawSource>,
        feedSourcesByType: LinkedHashMap<String, RawFeedSource>,
        actionSourceIssues: MutableList<ActionSourceIssue>,
    ): ActionSourceClassification {
        val type = sourceObject.optString("type", "").trim()
        if (type in OpenAIHostedWebSearchContract.OFFICIAL_REALTIME_FEED_SOURCE_TYPES) {
            feedSourcesByType.putIfAbsent(
                type,
                RawFeedSource(
                    type = type,
                    title = realtimeFeedTitle(type),
                ),
            )
            return ActionSourceClassification.STRUCTURED_FEED
        }
        if (
            type in OpenAIHostedWebSearchContract.RELAY_STRUCTURED_FEED_SOURCE_TYPES &&
                providerContract ==
                    OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT
        ) {
            val name = sourceObject.optString("name", "").trim()
            if (name.isEmpty()) {
                actionSourceIssues +=
                    ActionSourceIssue(
                        outputIndex = outputIndex,
                        sourceIndex = sourceIndex,
                        reason = "structured_feed_missing_name",
                        invalid = true,
                    )
                return ActionSourceClassification.INVALID
            }
            feedSourcesByType.putIfAbsent(
                "$type\u0000$name",
                RawFeedSource(
                    type = type,
                    title = name,
                ),
            )
            return ActionSourceClassification.STRUCTURED_FEED
        }
        if (type != "url" && type.isNotEmpty()) {
            actionSourceIssues +=
                ActionSourceIssue(
                    outputIndex = outputIndex,
                    sourceIndex = sourceIndex,
                    reason = if (type.isEmpty()) "missing_type" else "unsupported_type",
                    invalid = true,
                )
            return ActionSourceClassification.INVALID
        }
        val rawUrl = sourceObject.optString("url", "").trim()
        if (rawUrl.isEmpty()) {
            actionSourceIssues +=
                ActionSourceIssue(
                    outputIndex = outputIndex,
                    sourceIndex = sourceIndex,
                    reason = "missing_url",
                    invalid = true,
                )
            return ActionSourceClassification.INVALID
        }
        if (
            type.isEmpty() &&
                providerContract ==
                    OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL
        ) {
            actionSourceIssues +=
                ActionSourceIssue(
                    outputIndex = outputIndex,
                    sourceIndex = sourceIndex,
                    reason = "missing_type",
                    invalid = true,
                )
            return ActionSourceClassification.INVALID
        }
        val normalizedUrl =
            try {
                normalizeOpenAIHostedWebSearchUrl(rawUrl)
            } catch (_: OpenAIHostedWebSearchException) {
                actionSourceIssues +=
                    ActionSourceIssue(
                        outputIndex = outputIndex,
                        sourceIndex = sourceIndex,
                        reason = "invalid_url",
                        invalid = true,
                    )
                return ActionSourceClassification.INVALID
            }
        mergeSource(
            sourcesByUrl = actionSourcesByUrl,
            source =
                RawSource(
                    url = normalizedUrl,
                    title =
                        sourceObject.optString("title", "").trim()
                            .takeIf(String::isNotEmpty),
                ),
        )
        if (type.isEmpty()) {
            actionSourceIssues +=
                ActionSourceIssue(
                    outputIndex = outputIndex,
                    sourceIndex = sourceIndex,
                    reason = "missing_type_accepted_for_relay",
                    invalid = false,
                )
        }
        return ActionSourceClassification.VALID_URL
    }

    private fun mergeSource(
        sourcesByUrl: LinkedHashMap<String, RawSource>,
        source: RawSource,
    ) {
        val existing = sourcesByUrl[source.url]
        if (existing == null) {
            sourcesByUrl[source.url] = source
        } else if (existing.title.isNullOrBlank() && !source.title.isNullOrBlank()) {
            sourcesByUrl[source.url] = existing.copy(title = source.title)
        }
    }

    private fun realtimeFeedTitle(type: String): String =
        when (type) {
            "oai-sports" -> "OpenAI Sports live feed"
            "oai-weather" -> "OpenAI Weather live feed"
            "oai-finance" -> "OpenAI Finance live feed"
            else -> throw IllegalArgumentException("Unsupported OpenAI real-time feed type")
        }

    private fun insertSourceMarkers(
        answer: String,
        citations: List<OpenAIHostedWebSearchCitation>,
    ): String {
        val markersByEndIndex =
            citations
                .groupBy(OpenAIHostedWebSearchCitation::endIndex)
                .mapValues { (_, groupedCitations) ->
                    groupedCitations
                        .map(OpenAIHostedWebSearchCitation::sourceId)
                        .distinct()
                        .joinToString(separator = "") { sourceId -> "[$sourceId]" }
                }
        val markedAnswer = StringBuilder(answer)
        markersByEndIndex.keys.sortedDescending().forEach { endIndex ->
            if (endIndex !in 0..markedAnswer.length) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
                    message = "OpenAI Web Search citation marker position is invalid.",
                )
            }
            markedAnswer.insert(endIndex, markersByEndIndex.getValue(endIndex))
        }
        return markedAnswer.toString()
    }

    private fun sourceHost(url: String): String {
        return runCatching { URI(url).host?.lowercase(Locale.ROOT) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "source"
    }

    private fun schemaFailure(message: String): OpenAIHostedWebSearchException =
        OpenAIHostedWebSearchException(
            code = OpenAIHostedWebSearchErrorCode.RESPONSE_SCHEMA_INVALID,
            message = message,
        )
}
