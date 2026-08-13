package com.ai.assistance.operit.api.chat.llmprovider

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
        val identity: OpenAIHostedWebSearchUrlIdentity,
        val title: String?,
    )

    private data class RawFeedSource(
        val type: String,
        val title: String,
    )

    private data class RawCitation(
        val identity: OpenAIHostedWebSearchUrlIdentity,
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
        val actionSourcesByIdentity = linkedMapOf<String, RawSource>()
        val feedSourcesByType = linkedMapOf<String, RawFeedSource>()
        val citationTitlesByIdentity = linkedMapOf<String, RawSource>()
        val rawCitations = mutableListOf<RawCitation>()
        val actionSourceIssues = mutableListOf<ActionSourceIssue>()
        val openPageUrlsByIdentity = linkedMapOf<String, String>()
        val reportedUrlsByIdentity =
            linkedMapOf<String, OpenAIHostedWebSearchUrlIdentity>()
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
                        actionSourcesByIdentity = actionSourcesByIdentity,
                        feedSourcesByType = feedSourcesByType,
                        actionSourceIssues = actionSourceIssues,
                        openPageUrlsByIdentity = openPageUrlsByIdentity,
                        reportedUrlsByIdentity = reportedUrlsByIdentity,
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
                        citationTitlesByIdentity = citationTitlesByIdentity,
                        reportedUrlsByIdentity = reportedUrlsByIdentity,
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
        val hasActionSourceUrls = actionSourcesByIdentity.isNotEmpty()
        val hasStructuredFeeds = feedSourcesByType.isNotEmpty()
        val actionSourceUrls =
            actionSourcesByIdentity.values.map { source -> source.identity.displayUrl }
        val citationUrls =
            rawCitations
                .distinctBy { citation -> citation.identity.identityKey }
                .map { citation -> citation.identity.displayUrl }
        val citationsMissingFromActionSources =
            rawCitations
                .distinctBy { citation -> citation.identity.identityKey }
                .filterNot { citation ->
                    actionSourcesByIdentity.containsKey(citation.identity.identityKey) ||
                        openPageUrlsByIdentity.containsKey(citation.identity.identityKey)
                }
                .map { citation -> citation.identity.displayUrl }
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
        val domainPolicyState =
            OpenAIHostedWebSearchDomainPolicy.auditReportedBehavior(
                providerContract = binding.providerContract,
                request = request,
                reportedUrls = reportedUrlsByIdentity.values,
                actionQueries = actions.mapNotNull(OpenAIHostedWebSearchAction::query),
            )
        val sourceDiagnostics =
            OpenAIHostedWebSearchSourceDiagnostics(
                responseId = responseId,
                actionSourceCoverage = actionSourceCoverage,
                actionSourceUrls = actionSourceUrls,
                citationUrls = citationUrls,
                citationsMissingFromActionSources = citationsMissingFromActionSources,
                openPageUrls = openPageUrlsByIdentity.values.toList(),
                missingSourceActionIndexes = missingSourceActionIndexes.toList(),
                invalidActionSourceCount = invalidActionSourceCount,
                allowedDomains = request.allowedDomains,
                domainPolicyState = domainPolicyState,
            )
        citationTitlesByIdentity.forEach { (_, source) ->
            mergeSource(
                sourcesByIdentity = actionSourcesByIdentity,
                source = source,
                preferDisplayUrl = true,
            )
        }

        val evidenceMode =
            when {
                hasUrlCitations && hasActionSourceUrls ->
                    OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES

                hasUrlCitations -> OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS
                hasActionSourceUrls -> OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES
                hasStructuredFeeds -> OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS
                else -> OpenAIHostedWebSearchEvidenceMode.NONE
            }
        val allSourcesByIdentity = linkedMapOf<String, RawSource>()
        actionSourcesByIdentity.forEach { (identityKey, source) ->
            allSourcesByIdentity[identityKey] = source
        }
        openPageUrlsByIdentity.forEach { (identityKey, _) ->
            val identity = reportedUrlsByIdentity.getValue(identityKey)
            mergeSource(
                sourcesByIdentity = allSourcesByIdentity,
                source = RawSource(identity = identity, title = null),
            )
        }
        citationTitlesByIdentity.forEach { (_, source) ->
            mergeSource(
                sourcesByIdentity = allSourcesByIdentity,
                source = source,
                preferDisplayUrl = true,
            )
        }
        val sourceIdsByIdentity =
            allSourcesByIdentity.keys
                .mapIndexed { index, identityKey -> identityKey to "S${index + 1}" }
                .toMap()
        val urlSources =
            allSourcesByIdentity.values.map { source ->
                OpenAIHostedWebSearchSource(
                    sourceId =
                        requireNotNull(
                            sourceIdsByIdentity[source.identity.identityKey]
                        ),
                    type = "url",
                    title =
                        source.title?.takeIf(String::isNotBlank)
                            ?: source.identity.normalizedHost,
                    url = source.identity.displayUrl,
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
        val allSources = urlSources + feedSources
        val citations =
            rawCitations.map { citation ->
                val source =
                    allSourcesByIdentity.getValue(citation.identity.identityKey)
                OpenAIHostedWebSearchCitation(
                    sourceId =
                        requireNotNull(
                            sourceIdsByIdentity[citation.identity.identityKey]
                        ),
                    title =
                        citation.title?.takeIf(String::isNotBlank)
                            ?: source.title?.takeIf(String::isNotBlank)
                            ?: source.identity.normalizedHost,
                    url = source.identity.displayUrl,
                    startIndex = citation.startIndex,
                    endIndex = citation.endIndex,
                )
            }
        val citedSourceIds =
            citations.map(OpenAIHostedWebSearchCitation::sourceId).toSet()
        val citedSources = allSources.filter { source -> source.sourceId in citedSourceIds }
        val normalizedAnswer =
            OpenAIHostedWebSearchAnswerNormalizer.normalize(
                answer = answerText,
                citations = citations,
            )
        val normalizedCitations =
            normalizedAnswer.citations.map { citation ->
                OpenAIHostedWebSearchCitation(
                    sourceId = citation.sourceId,
                    title = citation.title,
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

            OpenAIHostedWebSearchEvidenceMode.NONE ->
                warnings += "NO_WEB_EVIDENCE"
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
                    answer = normalizedAnswer.answer,
                    answerWithSourceMarkers =
                        insertSourceMarkers(normalizedAnswer.answer, normalizedCitations),
                    searchActions = actions,
                    citations = normalizedCitations,
                    citedSources = citedSources,
                    allSources = allSources,
                    sourceSummary =
                        OpenAIHostedWebSearchSourceSummary(
                            allSourceCount = allSources.size,
                            citedSourceCount = citedSources.size,
                            uncitedSourceCount = allSources.size - citedSources.size,
                            urlSourceCount = allSources.count { source -> source.type == "url" },
                            structuredSourceCount =
                                allSources.count { source -> source.type != "url" },
                        ),
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
        actionSourcesByIdentity: LinkedHashMap<String, RawSource>,
        feedSourcesByType: LinkedHashMap<String, RawFeedSource>,
        actionSourceIssues: MutableList<ActionSourceIssue>,
        openPageUrlsByIdentity: LinkedHashMap<String, String>,
        reportedUrlsByIdentity:
            LinkedHashMap<String, OpenAIHostedWebSearchUrlIdentity>,
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
        val actionUrlIdentity =
            action.optString("url", "").trim().takeIf(String::isNotEmpty)?.let {
                OpenAIHostedWebSearchUrlIdentity.parse(it)
            }
        actionUrlIdentity?.let { identity ->
            reportedUrlsByIdentity.putIfAbsent(identity.identityKey, identity)
        }
        if (type == "open_page" && actionUrlIdentity != null) {
            openPageUrlsByIdentity.putIfAbsent(
                actionUrlIdentity.identityKey,
                actionUrlIdentity.displayUrl,
            )
        }
        val pattern = action.optString("pattern", "").trim().takeIf(String::isNotEmpty)

        if (queries.isEmpty()) {
            actions +=
                OpenAIHostedWebSearchAction(
                    type = type,
                    query = null,
                    url = actionUrlIdentity?.displayUrl,
                    pattern = pattern,
                )
        } else {
            queries.distinct().forEach { query ->
                actions +=
                    OpenAIHostedWebSearchAction(
                        type = type,
                        query = query,
                        url = actionUrlIdentity?.displayUrl,
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
                        actionSourcesByIdentity = actionSourcesByIdentity,
                        feedSourcesByType = feedSourcesByType,
                        actionSourceIssues = actionSourceIssues,
                        reportedUrlsByIdentity = reportedUrlsByIdentity,
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
        citationTitlesByIdentity: LinkedHashMap<String, RawSource>,
        reportedUrlsByIdentity:
            LinkedHashMap<String, OpenAIHostedWebSearchUrlIdentity>,
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
                citationTitlesByIdentity = citationTitlesByIdentity,
                reportedUrlsByIdentity = reportedUrlsByIdentity,
            )
        }
    }

    private fun parseAnnotations(
        annotations: JSONArray?,
        partText: String,
        answerOffset: Int,
        rawCitations: MutableList<RawCitation>,
        citationTitlesByIdentity: LinkedHashMap<String, RawSource>,
        reportedUrlsByIdentity:
            LinkedHashMap<String, OpenAIHostedWebSearchUrlIdentity>,
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
            val identity = OpenAIHostedWebSearchUrlIdentity.parse(rawUrl)
            reportedUrlsByIdentity.putIfAbsent(identity.identityKey, identity)
            val title = annotation.optString("title", "").trim().takeIf(String::isNotEmpty)
            val existingSource = citationTitlesByIdentity[identity.identityKey]
            if (existingSource == null) {
                citationTitlesByIdentity[identity.identityKey] =
                    RawSource(identity = identity, title = title)
            } else if (existingSource.title.isNullOrBlank() && !title.isNullOrBlank()) {
                citationTitlesByIdentity[identity.identityKey] =
                    existingSource.copy(identity = identity, title = title)
            }
            rawCitations +=
                RawCitation(
                    identity = identity,
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
        actionSourcesByIdentity: LinkedHashMap<String, RawSource>,
        feedSourcesByType: LinkedHashMap<String, RawFeedSource>,
        actionSourceIssues: MutableList<ActionSourceIssue>,
        reportedUrlsByIdentity:
            LinkedHashMap<String, OpenAIHostedWebSearchUrlIdentity>,
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
        val identity =
            try {
                OpenAIHostedWebSearchUrlIdentity.parse(rawUrl)
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
        reportedUrlsByIdentity.putIfAbsent(identity.identityKey, identity)
        mergeSource(
            sourcesByIdentity = actionSourcesByIdentity,
            source =
                RawSource(
                    identity = identity,
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
        sourcesByIdentity: LinkedHashMap<String, RawSource>,
        source: RawSource,
        preferDisplayUrl: Boolean = false,
    ) {
        val identityKey = source.identity.identityKey
        val existing = sourcesByIdentity[identityKey]
        if (existing == null) {
            sourcesByIdentity[identityKey] = source
        } else if (
            preferDisplayUrl ||
                (existing.title.isNullOrBlank() && !source.title.isNullOrBlank())
        ) {
            sourcesByIdentity[identityKey] =
                existing.copy(
                    identity =
                        if (preferDisplayUrl) source.identity else existing.identity,
                    title =
                        if (existing.title.isNullOrBlank()) source.title else existing.title,
                )
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

    private fun schemaFailure(message: String): OpenAIHostedWebSearchException =
        OpenAIHostedWebSearchException(
            code = OpenAIHostedWebSearchErrorCode.RESPONSE_SCHEMA_INVALID,
            message = message,
        )
}
