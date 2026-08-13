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
    val citedSources: List<OpenAIHostedWebSearchEvidenceSource>,
    val allSources: List<OpenAIHostedWebSearchEvidenceSource>,
    val sourceSummary: OpenAIHostedWebSearchSourceSummary,
    val usage: OpenAIHostedWebSearchEvidenceUsage,
    val warnings: List<String>,
    val sourceDiagnostics: OpenAIHostedWebSearchEvidenceSourceDiagnostics,
    val executionDiagnostics: OpenAIHostedWebSearchExecutionDiagnostics,
)

internal data class OpenAIHostedWebSearchEvidenceAction(
    val type: OpenAIHostedWebSearchEvidenceActionType,
    val query: String?,
    val url: String?,
    val pattern: String?,
)

internal enum class OpenAIHostedWebSearchEvidenceActionType(
    val wireValue: String,
) {
    SEARCH("search"),
    OPEN_PAGE("open_page"),
    FIND_IN_PAGE("find_in_page");

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchEvidenceActionType =
            entries.firstOrNull { actionType -> actionType.wireValue == value }
                ?: throw IllegalArgumentException("Unsupported Web Search action type")
    }
}

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
    val domainPolicyState: OpenAIHostedWebSearchDomainPolicyState,
)

internal sealed interface OpenAIHostedWebSearchEvidenceParseResult {
    data object NotApplicable : OpenAIHostedWebSearchEvidenceParseResult

    data class Parsed(
        val evidence: OpenAIHostedWebSearchEvidence,
    ) : OpenAIHostedWebSearchEvidenceParseResult

    data class Invalid(
        val code: OpenAIHostedWebSearchEvidenceInvalidCode,
        val sanitizedSummary: String,
        val schemaRevision: Int?,
        val hasRequestId: Boolean,
        val fieldName: String?,
    ) : OpenAIHostedWebSearchEvidenceParseResult
}

internal enum class OpenAIHostedWebSearchEvidenceInvalidCode(
    val sanitizedSummary: String,
) {
    INVALID_JSON("The Web Search result is not valid JSON."),
    ROOT_NOT_OBJECT("The Web Search result root is not an object."),
    REQUIRED_FIELD_INVALID("A required Web Search result field is missing or invalid."),
    SCHEMA_REVISION_MISMATCH("The Web Search result schema revision is not supported."),
    UNSUPPORTED_VALUE("The Web Search result contains an unsupported value."),
    SOURCE_INVALID("A Web Search source is invalid."),
    CITATION_INVALID("A Web Search citation is invalid."),
    EVIDENCE_INCONSISTENT("The Web Search evidence fields are inconsistent."),
}

internal object OpenAIHostedWebSearchEvidenceParser {
    fun parse(
        toolName: String,
        resultJson: String,
    ): OpenAIHostedWebSearchEvidenceParseResult {
        if (toolName != OpenAIHostedWebSearchContract.TOOL_NAME) {
            return OpenAIHostedWebSearchEvidenceParseResult.NotApplicable
        }

        val rootValue =
            try {
                JSONTokener(resultJson.trim()).nextValue()
            } catch (_: Exception) {
                return invalid(OpenAIHostedWebSearchEvidenceInvalidCode.INVALID_JSON)
            }
        val root =
            rootValue as? JSONObject
                ?: return invalid(OpenAIHostedWebSearchEvidenceInvalidCode.ROOT_NOT_OBJECT)
        val schemaRevision = root.opt("schema_version") as? Int
        val hasRequestId =
            (root.opt("request_id") as? String)
                ?.isNotBlank() == true

        return try {
            OpenAIHostedWebSearchEvidenceParseResult.Parsed(parseEvidence(root))
        } catch (error: EvidenceValidationException) {
            invalid(
                code = error.code,
                schemaRevision = schemaRevision,
                hasRequestId = hasRequestId,
                fieldName = error.fieldName,
            )
        } catch (_: Exception) {
            invalid(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
                schemaRevision = schemaRevision,
                hasRequestId = hasRequestId,
            )
        }
    }

    private fun parseEvidence(root: JSONObject): OpenAIHostedWebSearchEvidence {
        validate(
            condition = root.requireBoolean("success"),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "success",
        )
        validate(
            condition =
                root.requireInt("schema_version") ==
                    OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.SCHEMA_REVISION_MISMATCH,
            fieldName = "schema_version",
        )
        validate(
            condition = root.requireString("provider") == "openai",
            code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            fieldName = "provider",
        )
        validate(
            condition = root.requireString("backend") == "responses_web_search",
            code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            fieldName = "backend",
        )

        val answer = root.requireString("answer")
        val mode = root.requireString("mode")
        validate(
            condition = mode == "live" || mode == "indexed",
            code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            fieldName = "mode",
        )
        val evidenceMode =
            parseEvidenceMode(root.requireString("evidence_mode"))

        val allSources =
            root.requireArray("all_sources").mapObjects { source ->
                val type = source.requireNonBlankString("type")
                val url =
                    when {
                        type == "url" ->
                            normalizeUrl(
                                rawUrl = source.requireNonBlankString("url"),
                                fieldName = "all_sources.url",
                            )

                        type in
                            OpenAIHostedWebSearchContract.OFFICIAL_REALTIME_FEED_SOURCE_TYPES -> {
                            validate(
                                condition = source.isNull("url"),
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
                                fieldName = "all_sources.url",
                            )
                            null
                        }

                        type in
                            OpenAIHostedWebSearchContract.RELAY_STRUCTURED_FEED_SOURCE_TYPES -> {
                            validate(
                                condition = source.isNull("url"),
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
                                fieldName = "all_sources.url",
                            )
                            null
                        }

                        else ->
                            throw EvidenceValidationException(
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                                fieldName = "all_sources.type",
                            )
                    }
                OpenAIHostedWebSearchEvidenceSource(
                    sourceId = source.requireNonBlankString("source_id"),
                    type = type,
                    title = source.requireNonBlankString("title"),
                    url = url,
                )
            }
        val citedSources =
            root.requireArray("cited_sources").mapObjects { source ->
                val type = source.requireNonBlankString("type")
                val url =
                    when {
                        type == "url" ->
                            normalizeUrl(
                                rawUrl = source.requireNonBlankString("url"),
                                fieldName = "cited_sources.url",
                            )

                        type in OpenAIHostedWebSearchContract.OFFICIAL_REALTIME_FEED_SOURCE_TYPES ||
                            type in OpenAIHostedWebSearchContract.RELAY_STRUCTURED_FEED_SOURCE_TYPES -> {
                            validate(
                                condition = source.isNull("url"),
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
                                fieldName = "cited_sources.url",
                            )
                            null
                        }

                        else ->
                            throw EvidenceValidationException(
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                                fieldName = "cited_sources.type",
                            )
                    }
                OpenAIHostedWebSearchEvidenceSource(
                    sourceId = source.requireNonBlankString("source_id"),
                    type = type,
                    title = source.requireNonBlankString("title"),
                    url = url,
                )
            }
        validate(
            condition =
                allSources.map(OpenAIHostedWebSearchEvidenceSource::sourceId)
                    .distinct()
                    .size == allSources.size,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "all_sources.source_id",
        )
        validate(
            condition =
                citedSources.map(OpenAIHostedWebSearchEvidenceSource::sourceId)
                    .distinct()
                    .size == citedSources.size,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "cited_sources.source_id",
        )
        val allSourcesById = allSources.associateBy(OpenAIHostedWebSearchEvidenceSource::sourceId)
        val citedSourcesById =
            citedSources.associateBy(OpenAIHostedWebSearchEvidenceSource::sourceId)
        validate(
            condition =
                citedSources.all { source ->
                    allSourcesById[source.sourceId] == source
                },
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "cited_sources",
        )

        val citations =
            root.requireArray("citations").mapObjects { citation ->
                val sourceId = citation.requireNonBlankString("source_id")
                val url =
                    normalizeUrl(
                        rawUrl = citation.requireString("url"),
                        fieldName = "citations.url",
                        code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                    )
                val startIndex = citation.requireInt("start_index")
                val endIndex = citation.requireInt("end_index")
                val source =
                    allSourcesById[sourceId]
                        ?: throw EvidenceValidationException(
                            code =
                                OpenAIHostedWebSearchEvidenceInvalidCode
                                    .EVIDENCE_INCONSISTENT,
                            fieldName = "citations.source_id",
                        )
                validate(
                    condition =
                        source.type == "url" &&
                            source.url != null &&
                            urlIdentityKey(url) == urlIdentityKey(source.url),
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                    fieldName = "citations.source_id",
                )
                validate(
                    condition = citedSourcesById.containsKey(sourceId),
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
                    fieldName = "citations.source_id",
                )
                validate(
                    condition =
                        startIndex >= 0 &&
                            endIndex > startIndex &&
                            endIndex <= answer.length,
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                    fieldName = "citations.start_index",
                )
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
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS ->
                validate(
                    condition = citations.isNotEmpty(),
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                    fieldName = "citations",
                )

            OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES,
            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS ->
                validate(
                    condition = citations.isEmpty(),
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                    fieldName = "citations",
                )

            OpenAIHostedWebSearchEvidenceMode.NONE ->
                validate(
                    condition = citations.isEmpty(),
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                    fieldName = "citations",
                )
        }
        when (evidenceMode) {
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS_AND_ACTION_SOURCES,
            OpenAIHostedWebSearchEvidenceMode.URL_CITATIONS,
            OpenAIHostedWebSearchEvidenceMode.ACTION_SOURCES ->
                validate(
                    condition = allSources.any { source -> source.type == "url" },
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
                    fieldName = "all_sources",
                )

            OpenAIHostedWebSearchEvidenceMode.STRUCTURED_FEEDS ->
                validate(
                    condition =
                        allSources.isNotEmpty() &&
                            allSources.none { source -> source.type == "url" },
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
                    fieldName = "all_sources",
                )

            OpenAIHostedWebSearchEvidenceMode.NONE ->
                validate(
                    condition = allSources.isEmpty() && citedSources.isEmpty(),
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
                    fieldName = "all_sources",
                )
        }
        val citationSourceIds =
            citations.map(OpenAIHostedWebSearchEvidenceCitation::sourceId).toSet()
        validate(
            condition =
                citedSources.map(OpenAIHostedWebSearchEvidenceSource::sourceId).toSet() ==
                    citationSourceIds,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "cited_sources",
        )

        val actions =
            root.requireArray("search_actions").mapObjects { action ->
                val actionUrl =
                    action.readNullableString("url")
                        ?.let { rawUrl ->
                            normalizeUrl(
                                rawUrl = rawUrl,
                                fieldName = "search_actions.url",
                            )
                        }
                OpenAIHostedWebSearchEvidenceAction(
                    type =
                        parseActionType(
                            action.requireNonBlankString("type")
                        ),
                    query = action.readNullableString("query"),
                    url = actionUrl,
                    pattern = action.readNullableString("pattern"),
                )
            }
        validate(
            condition = actions.isNotEmpty(),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "search_actions",
        )

        val usageObject = root.requireObject("usage")
        val usage =
            OpenAIHostedWebSearchEvidenceUsage(
                inputTokens = usageObject.requireNonNegativeInt("input_tokens"),
                cachedInputTokens = usageObject.requireNonNegativeInt("cached_input_tokens"),
                outputTokens = usageObject.requireNonNegativeInt("output_tokens"),
                webSearchCalls = usageObject.requireNonNegativeInt("web_search_calls"),
            )
        validate(
            condition = usage.webSearchCalls > 0,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "usage.web_search_calls",
        )

        val sourceDiagnosticsObject = root.requireObject("source_diagnostics")
        val sourceDiagnostics =
            OpenAIHostedWebSearchEvidenceSourceDiagnostics(
                responseId = sourceDiagnosticsObject.requireNonBlankString("response_id"),
                actionSourceCoverage =
                    parseActionSourceCoverage(
                        sourceDiagnosticsObject.requireNonBlankString(
                            "action_source_coverage"
                        )
                    ),
                actionSourceUrls =
                    sourceDiagnosticsObject.requireArray("action_source_urls")
                        .mapStrings()
                        .map { rawUrl ->
                            normalizeUrl(
                                rawUrl = rawUrl,
                                fieldName = "source_diagnostics.action_source_urls",
                            )
                        }
                        .distinct(),
                citationUrls =
                    sourceDiagnosticsObject.requireArray("citation_urls")
                        .mapStrings()
                        .map { rawUrl ->
                            normalizeUrl(
                                rawUrl = rawUrl,
                                fieldName = "source_diagnostics.citation_urls",
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                            )
                        }
                        .distinct(),
                citationsMissingFromActionSources =
                    sourceDiagnosticsObject
                        .requireArray("citations_missing_from_action_sources")
                        .mapStrings()
                        .map { rawUrl ->
                            normalizeUrl(
                                rawUrl = rawUrl,
                                fieldName =
                                    "source_diagnostics.citations_missing_from_action_sources",
                                code = OpenAIHostedWebSearchEvidenceInvalidCode.CITATION_INVALID,
                            )
                        }
                        .distinct(),
                openPageUrls =
                    sourceDiagnosticsObject.requireArray("open_page_urls")
                        .mapStrings()
                        .map { rawUrl ->
                            normalizeUrl(
                                rawUrl = rawUrl,
                                fieldName = "source_diagnostics.open_page_urls",
                            )
                        }
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
                domainPolicyState =
                    parseDomainPolicyState(
                        sourceDiagnosticsObject.requireNonBlankString(
                            "domain_policy_state"
                        )
                    ),
            )
        validate(
            condition =
                sourceDiagnostics.responseId ==
                    root.requireNonBlankString("response_id"),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_diagnostics.response_id",
        )
        validate(
            condition =
                sourceDiagnosticsObject.requireString("url_normalization") ==
                    "http_https_identity",
            code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
            fieldName = "source_diagnostics.url_normalization",
        )
        val sourceUrls = allSources.mapNotNull(OpenAIHostedWebSearchEvidenceSource::url).toSet()
        val citationUrls = citations.map(OpenAIHostedWebSearchEvidenceCitation::url).distinct()
        val sourceUrlIdentityKeys = sourceUrls.map(::urlIdentityKey).toSet()
        val actionSourceIdentityKeys =
            sourceDiagnostics.actionSourceUrls.map(::urlIdentityKey).toSet()
        val citationIdentityKeys = citationUrls.map(::urlIdentityKey)
        val openPageIdentityKeys =
            sourceDiagnostics.openPageUrls.map(::urlIdentityKey)
        validate(
            condition =
                sourceDiagnostics.citationUrls.map(::urlIdentityKey) ==
                    citationIdentityKeys,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_diagnostics.citation_urls",
        )
        validate(
            condition = actionSourceIdentityKeys.all(sourceUrlIdentityKeys::contains),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_diagnostics.action_source_urls",
        )
        validate(
            condition =
                sourceDiagnostics.citationsMissingFromActionSources.map(::urlIdentityKey) ==
                    citationIdentityKeys.filterNot { identityKey ->
                        identityKey in actionSourceIdentityKeys ||
                            identityKey in openPageIdentityKeys
                    },
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_diagnostics.citations_missing_from_action_sources",
        )
        validate(
            condition =
                openPageIdentityKeys ==
                    actions
                        .asSequence()
                        .filter { action ->
                            action.type ==
                                OpenAIHostedWebSearchEvidenceActionType.OPEN_PAGE
                        }
                        .mapNotNull(OpenAIHostedWebSearchEvidenceAction::url)
                        .map(::urlIdentityKey)
                        .distinct()
                        .toList(),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_diagnostics.open_page_urls",
        )
        val hasSearchActions =
            actions.any { action ->
                action.type == OpenAIHostedWebSearchEvidenceActionType.SEARCH
            }
        val expectedCoverage =
            when {
                !hasSearchActions ->
                    OpenAIHostedWebSearchActionSourceCoverage.NOT_APPLICABLE

                sourceDiagnostics.invalidActionSourceCount > 0 ||
                    (
                        sourceDiagnostics.missingSourceActionIndexes.isNotEmpty() &&
                            sourceDiagnostics.missingSourceActionIndexes.size <
                                actions.count { action ->
                                    action.type ==
                                        OpenAIHostedWebSearchEvidenceActionType.SEARCH
                                }
                    ) ->
                    OpenAIHostedWebSearchActionSourceCoverage.PARTIAL

                sourceDiagnostics.actionSourceUrls.isEmpty() &&
                    sourceDiagnostics.missingSourceActionIndexes.isNotEmpty() ->
                    OpenAIHostedWebSearchActionSourceCoverage.MISSING

                else -> OpenAIHostedWebSearchActionSourceCoverage.COMPLETE
            }
        validate(
            condition = sourceDiagnostics.actionSourceCoverage == expectedCoverage,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_diagnostics.action_source_coverage",
        )

        val sourceSummaryObject = root.requireObject("source_summary")
        val sourceSummary =
            OpenAIHostedWebSearchSourceSummary(
                allSourceCount =
                    sourceSummaryObject.requireNonNegativeInt("all_source_count"),
                citedSourceCount =
                    sourceSummaryObject.requireNonNegativeInt("cited_source_count"),
                uncitedSourceCount =
                    sourceSummaryObject.requireNonNegativeInt("uncited_source_count"),
                urlSourceCount =
                    sourceSummaryObject.requireNonNegativeInt("url_source_count"),
                structuredSourceCount =
                    sourceSummaryObject.requireNonNegativeInt("structured_source_count"),
            )
        validate(
            condition =
                sourceSummary.allSourceCount == allSources.size &&
                    sourceSummary.citedSourceCount == citedSources.size &&
                    sourceSummary.uncitedSourceCount ==
                        allSources.size - citedSources.size &&
                    sourceSummary.urlSourceCount ==
                        allSources.count { source -> source.type == "url" } &&
                    sourceSummary.structuredSourceCount ==
                        allSources.count { source -> source.type != "url" },
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "source_summary",
        )

        val executionDiagnostics =
            parseExecutionDiagnostics(root.requireObject("execution_diagnostics"))
        val warnings = root.requireArray("warnings").mapStrings()
        if (evidenceMode == OpenAIHostedWebSearchEvidenceMode.NONE) {
            validate(
                condition = "NO_WEB_EVIDENCE" in warnings,
                code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
                fieldName = "warnings",
            )
        }

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
            citedSources = citedSources,
            allSources = allSources,
            sourceSummary = sourceSummary,
            usage = usage,
            warnings = warnings,
            sourceDiagnostics = sourceDiagnostics,
            executionDiagnostics = executionDiagnostics,
        )
    }

    private fun parseExecutionDiagnostics(
        value: JSONObject,
    ): OpenAIHostedWebSearchExecutionDiagnostics {
        val locationObject = value.requireObject("location")
        val location =
            OpenAIHostedWebSearchLocationDiagnostics(
                requested = locationObject.requireBoolean("location_requested"),
                configured = locationObject.requireBoolean("location_configured"),
                applied = locationObject.requireBoolean("location_applied"),
                precision =
                    locationObject.requireNonBlankString("location_precision")
                        .also { precision ->
                            validate(
                                condition = precision in LOCATION_PRECISIONS,
                                code =
                                    OpenAIHostedWebSearchEvidenceInvalidCode
                                        .UNSUPPORTED_VALUE,
                                fieldName =
                                    "execution_diagnostics.location.location_precision",
                            )
                        },
            )
        validate(
            condition =
                !location.applied ||
                    (location.requested && location.configured),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "execution_diagnostics.location.location_applied",
        )
        validate(
            condition =
                (location.precision == "none") == !location.configured,
            code = OpenAIHostedWebSearchEvidenceInvalidCode.EVIDENCE_INCONSISTENT,
            fieldName = "execution_diagnostics.location.location_precision",
        )

        return OpenAIHostedWebSearchExecutionDiagnostics(
            totalElapsedMs = value.requireNonNegativeLong("total_elapsed_ms"),
            queueWaitMs = value.readNullableNonNegativeLong("queue_wait_ms"),
            httpElapsedMs = value.readNullableNonNegativeLong("http_elapsed_ms"),
            responseHeaderWaitMs =
                value.readNullableNonNegativeLong("response_header_wait_ms"),
            responseBodyReadMs =
                value.readNullableNonNegativeLong("response_body_read_ms"),
            parseMs = value.readNullableNonNegativeLong("parse_ms"),
            callbackDeliveryMs =
                value.readNullableNonNegativeLong("callback_delivery_ms"),
            providerRequestId =
                value.readNullableString("provider_request_id"),
            submissionState =
                parseSubmissionState(
                    value.requireNonBlankString("submission_state")
                ),
            location = location,
        )
    }

    private fun JSONObject.requireObject(key: String): JSONObject =
        opt(key) as? JSONObject
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )

    private fun JSONObject.requireArray(key: String): JSONArray =
        opt(key) as? JSONArray
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )

    private fun JSONObject.requireString(key: String): String =
        opt(key) as? String
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )

    private fun JSONObject.requireNonBlankString(key: String): String =
        requireString(key).also { value ->
            validate(
                condition = value.isNotBlank(),
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )
        }

    private fun JSONObject.requireBoolean(key: String): Boolean =
        opt(key) as? Boolean
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )

    private fun JSONObject.requireInt(key: String): Int {
        return opt(key) as? Int
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )
    }

    private fun JSONObject.requireNonNegativeInt(key: String): Int =
        requireInt(key).also { value ->
            validate(
                condition = value >= 0,
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )
        }

    private fun JSONObject.requireNonNegativeLong(key: String): Long =
        requireIntegralLong(key).also { value ->
            validate(
                condition = value >= 0L,
                code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                fieldName = key,
            )
        }

    private fun JSONObject.readNullableNonNegativeLong(key: String): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return requireNonNegativeLong(key)
    }

    private fun JSONObject.requireIntegralLong(key: String): Long {
        val number =
            opt(key) as? Number
                ?: throw EvidenceValidationException(
                    code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
                    fieldName = key,
                )
        val value = number.toLong()
        validate(
            condition = number.toDouble() == value.toDouble(),
            code = OpenAIHostedWebSearchEvidenceInvalidCode.REQUIRED_FIELD_INVALID,
            fieldName = key,
        )
        return value
    }

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
                        ?: throw EvidenceValidationException(
                            code =
                                OpenAIHostedWebSearchEvidenceInvalidCode
                                    .REQUIRED_FIELD_INVALID,
                        )
                add(transform(value))
            }
        }

    private fun JSONArray.mapStrings(): List<String> =
        buildList {
            for (index in 0 until length()) {
                val value =
                    opt(index) as? String
                        ?: throw EvidenceValidationException(
                            code =
                                OpenAIHostedWebSearchEvidenceInvalidCode
                                    .REQUIRED_FIELD_INVALID,
                        )
                add(value)
            }
        }

    private fun JSONArray.mapNonNegativeInts(): List<Int> =
        buildList {
            for (index in 0 until length()) {
                val value =
                    opt(index) as? Int
                        ?: throw EvidenceValidationException(
                            code =
                                OpenAIHostedWebSearchEvidenceInvalidCode
                                    .REQUIRED_FIELD_INVALID,
                        )
                validate(
                    condition = value >= 0,
                    code =
                        OpenAIHostedWebSearchEvidenceInvalidCode
                            .REQUIRED_FIELD_INVALID,
                )
                add(value)
            }
        }

    private fun parseEvidenceMode(value: String): OpenAIHostedWebSearchEvidenceMode =
        try {
            OpenAIHostedWebSearchEvidenceMode.parse(value)
        } catch (_: IllegalArgumentException) {
            throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                fieldName = "evidence_mode",
            )
        }

    private fun parseActionSourceCoverage(
        value: String,
    ): OpenAIHostedWebSearchActionSourceCoverage =
        try {
            OpenAIHostedWebSearchActionSourceCoverage.parse(value)
        } catch (_: IllegalArgumentException) {
            throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                fieldName = "source_diagnostics.action_source_coverage",
            )
        }

    private fun parseActionType(
        value: String,
    ): OpenAIHostedWebSearchEvidenceActionType =
        try {
            OpenAIHostedWebSearchEvidenceActionType.parse(value)
        } catch (_: IllegalArgumentException) {
            throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                fieldName = "search_actions.type",
            )
        }

    private fun parseDomainPolicyState(
        value: String,
    ): OpenAIHostedWebSearchDomainPolicyState =
        OpenAIHostedWebSearchDomainPolicyState.entries
            .firstOrNull { state -> state.wireValue == value }
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                fieldName = "source_diagnostics.domain_policy_state",
            )

    private fun parseSubmissionState(
        value: String,
    ): OpenAIHostedWebSearchSubmissionState =
        OpenAIHostedWebSearchSubmissionState.entries
            .firstOrNull { state -> state.wireValue == value }
            ?: throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.UNSUPPORTED_VALUE,
                fieldName = "execution_diagnostics.submission_state",
            )

    private fun normalizeUrl(
        rawUrl: String,
        fieldName: String,
        code: OpenAIHostedWebSearchEvidenceInvalidCode =
            OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
    ): String =
        try {
            normalizeOpenAIHostedWebSearchUrl(rawUrl)
        } catch (_: OpenAIHostedWebSearchException) {
            throw EvidenceValidationException(
                code = code,
                fieldName = fieldName,
            )
        }

    private fun urlIdentityKey(url: String): String =
        try {
            OpenAIHostedWebSearchUrlIdentity.parse(url).identityKey
        } catch (_: OpenAIHostedWebSearchException) {
            throw EvidenceValidationException(
                code = OpenAIHostedWebSearchEvidenceInvalidCode.SOURCE_INVALID,
            )
        }

    private fun validate(
        condition: Boolean,
        code: OpenAIHostedWebSearchEvidenceInvalidCode,
        fieldName: String? = null,
    ) {
        if (!condition) {
            throw EvidenceValidationException(
                code = code,
                fieldName = fieldName,
            )
        }
    }

    private fun invalid(
        code: OpenAIHostedWebSearchEvidenceInvalidCode,
        schemaRevision: Int? = null,
        hasRequestId: Boolean = false,
        fieldName: String? = null,
    ): OpenAIHostedWebSearchEvidenceParseResult.Invalid =
        OpenAIHostedWebSearchEvidenceParseResult.Invalid(
            code = code,
            sanitizedSummary = code.sanitizedSummary,
            schemaRevision = schemaRevision,
            hasRequestId = hasRequestId,
            fieldName = fieldName,
        )

    private class EvidenceValidationException(
        val code: OpenAIHostedWebSearchEvidenceInvalidCode,
        val fieldName: String? = null,
    ) : IllegalArgumentException()

    private val LOCATION_PRECISIONS =
        setOf(
            "none",
            "country",
            "region",
            "city",
            "timezone",
            "mixed",
        )
}
