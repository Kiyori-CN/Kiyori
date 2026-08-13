package com.ai.assistance.operit.api.chat.llmprovider

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal object OpenAIHostedWebSearchContract {
    const val TOOLPKG_ID = "com.kiyori.openai_web_search"
    const val TOOLPKG_VERSION = "1.0.6"
    const val SUBPACKAGE_NAME = "openai_web_search"
    const val TOOL_NAME = "openai_web_search:search"
    const val RESPONSE_SCHEMA_REVISION = 7
    const val MAX_QUERY_CHARACTERS = 8_000
    const val MAX_ADDITIONAL_INSTRUCTIONS_CHARACTERS = 4_000
    const val MAX_DOMAIN_COUNT = 100
    const val MAX_OUTPUT_TOKENS = 64_000
    const val MAX_TIMEOUT_SECONDS = 300
    const val MAX_CONCURRENT_REQUESTS = 8
    const val MAX_REQUESTS_PER_MINUTE = 600
    const val MAX_RESPONSE_BYTES = 4L * 1024L * 1024L
    const val MAX_ERROR_RESPONSE_BYTES = 64L * 1024L

    const val ENV_PROVIDER_CONTRACT = "OPENAI_WEB_SEARCH_PROVIDER_CONTRACT"
    const val ENV_RESPONSES_ENDPOINT = "OPENAI_WEB_SEARCH_RESPONSES_ENDPOINT"
    const val ENV_MODEL = "OPENAI_WEB_SEARCH_MODEL"
    const val ENV_API_KEY = "OPENAI_WEB_SEARCH_API_KEY"
    const val ENV_AUTH_HEADER_NAME = "OPENAI_WEB_SEARCH_AUTH_HEADER_NAME"
    const val ENV_AUTH_SCHEME = "OPENAI_WEB_SEARCH_AUTH_SCHEME"
    const val ENV_EXTRA_HEADERS_JSON = "OPENAI_WEB_SEARCH_EXTRA_HEADERS_JSON"
    const val ENV_REASONING_EFFORT = "OPENAI_WEB_SEARCH_REASONING_EFFORT"
    const val ENV_MAX_OUTPUT_TOKENS = "OPENAI_WEB_SEARCH_MAX_OUTPUT_TOKENS"
    const val ENV_RETURN_TOKEN_BUDGET = "OPENAI_WEB_SEARCH_RETURN_TOKEN_BUDGET"
    const val ENV_ADDITIONAL_INSTRUCTIONS = "OPENAI_WEB_SEARCH_ADDITIONAL_INSTRUCTIONS"
    const val ENV_EXTERNAL_WEB_ACCESS = "OPENAI_WEB_SEARCH_EXTERNAL_WEB_ACCESS"
    const val ENV_CONTEXT_SIZE = "OPENAI_WEB_SEARCH_CONTEXT_SIZE"
    const val ENV_ALLOWED_DOMAINS_JSON = "OPENAI_WEB_SEARCH_ALLOWED_DOMAINS_JSON"
    const val ENV_BLOCKED_DOMAINS_JSON = "OPENAI_WEB_SEARCH_BLOCKED_DOMAINS_JSON"
    const val ENV_LOCATION_JSON = "OPENAI_WEB_SEARCH_LOCATION_JSON"
    const val ENV_QUEUE_TIMEOUT_SECONDS = "OPENAI_WEB_SEARCH_QUEUE_TIMEOUT_SECONDS"
    const val ENV_TIMEOUT_SECONDS = "OPENAI_WEB_SEARCH_TIMEOUT_SECONDS"
    const val ENV_MAX_CONCURRENT_REQUESTS = "OPENAI_WEB_SEARCH_MAX_CONCURRENT_REQUESTS"
    const val ENV_REQUESTS_PER_MINUTE = "OPENAI_WEB_SEARCH_REQUESTS_PER_MINUTE"

    val ENVIRONMENT_NAMES: List<String> =
        listOf(
            ENV_PROVIDER_CONTRACT,
            ENV_RESPONSES_ENDPOINT,
            ENV_MODEL,
            ENV_API_KEY,
            ENV_AUTH_HEADER_NAME,
            ENV_AUTH_SCHEME,
            ENV_EXTRA_HEADERS_JSON,
            ENV_REASONING_EFFORT,
            ENV_MAX_OUTPUT_TOKENS,
            ENV_RETURN_TOKEN_BUDGET,
            ENV_ADDITIONAL_INSTRUCTIONS,
            ENV_EXTERNAL_WEB_ACCESS,
            ENV_CONTEXT_SIZE,
            ENV_ALLOWED_DOMAINS_JSON,
            ENV_BLOCKED_DOMAINS_JSON,
            ENV_LOCATION_JSON,
            ENV_QUEUE_TIMEOUT_SECONDS,
            ENV_TIMEOUT_SECONDS,
            ENV_MAX_CONCURRENT_REQUESTS,
            ENV_REQUESTS_PER_MINUTE,
        )

    val OFFICIAL_MODEL_ALLOWLIST: Set<String> =
        setOf(
            "gpt-5.6",
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-5.6-luna",
        )

    val OFFICIAL_REALTIME_FEED_SOURCE_TYPES: Set<String> =
        setOf(
            "oai-sports",
            "oai-weather",
            "oai-finance",
        )

    val RELAY_STRUCTURED_FEED_SOURCE_TYPES: Set<String> =
        setOf(
            "api",
        )
}

internal enum class OpenAIHostedWebSearchProviderContract {
    RESPONSES_HOSTED_OFFICIAL,
    RESPONSES_RELAY_STRICT;

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchProviderContract =
            entries.firstOrNull { contract ->
                contract.name == value.trim().uppercase(Locale.ROOT)
            } ?: throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "OpenAI Web Search provider contract must be " +
                        "RESPONSES_HOSTED_OFFICIAL or RESPONSES_RELAY_STRICT.",
            )
    }
}

internal enum class OpenAIHostedWebSearchMode(val externalWebAccess: Boolean, val wireValue: String) {
    LIVE(true, "live"),
    INDEXED(false, "indexed");

    companion object {
        fun fromExternalWebAccess(value: Boolean): OpenAIHostedWebSearchMode =
            if (value) LIVE else INDEXED
    }
}

internal enum class OpenAIHostedWebSearchContextSize(val wireValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchContextSize =
            entries.firstOrNull { size -> size.wireValue == value.trim().lowercase(Locale.ROOT) }
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "Web Search context size must be low, medium, or high.",
                )
    }
}

internal enum class OpenAIHostedWebSearchReturnTokenBudget(val wireValue: String) {
    DEFAULT("default"),
    UNLIMITED("unlimited");

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchReturnTokenBudget =
            entries.firstOrNull { budget ->
                budget.wireValue == value.trim().lowercase(Locale.ROOT)
            } ?: throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "Web Search return token budget must be default or unlimited.",
            )
    }
}

internal enum class OpenAIHostedWebSearchReasoningEffort(val wireValue: String?) {
    OMIT(null),
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchReasoningEffort =
            entries.firstOrNull { effort ->
                effort.name == value.trim().uppercase(Locale.ROOT)
            } ?: throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "Web Search reasoning effort must be omit, none, low, medium, high, xhigh, or max.",
            )
    }
}

internal data class OpenAIHostedWebSearchApproximateLocation(
    val country: String?,
    val city: String?,
    val region: String?,
    val timezone: String?,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("type", "approximate")
            country?.let { put("country", it) }
            city?.let { put("city", it) }
            region?.let { put("region", it) }
            timezone?.let { put("timezone", it) }
        }
}

internal data class OpenAIHostedWebSearchBinding(
    val toolPkgId: String,
    val providerContract: OpenAIHostedWebSearchProviderContract,
    val endpoint: String,
    val modelName: String,
    val apiKey: String,
    val authHeaderName: String,
    val authScheme: String,
    val extraHeaders: Map<String, String>,
    val reasoningEffort: OpenAIHostedWebSearchReasoningEffort,
    val maxOutputTokens: Int?,
    val returnTokenBudget: OpenAIHostedWebSearchReturnTokenBudget,
    val additionalInstructions: String,
    val mode: OpenAIHostedWebSearchMode,
    val contextSize: OpenAIHostedWebSearchContextSize,
    val allowedDomains: List<String>,
    val blockedDomains: List<String>,
    val location: OpenAIHostedWebSearchApproximateLocation?,
    val queueTimeoutSeconds: Int,
    val timeoutSeconds: Int,
    val maxConcurrentRequests: Int,
    val requestsPerMinute: Int,
) {
    fun credentialRevision(): String =
        openAIHostedWebSearchCredentialRevision(apiKey)

    fun authSchemeKind(): String =
        when {
            authScheme.isBlank() -> "direct"
            authScheme.equals("Bearer", ignoreCase = true) -> "bearer"
            else -> "custom"
        }

    fun compatibilityFingerprint(): OpenAIHostedWebSearchCompatibilityFingerprint =
        OpenAIHostedWebSearchCompatibilityFingerprint(
            endpoint = endpoint,
            modelName = modelName,
            authMode = "$authHeaderName\u0000$authScheme",
            credentialDigest =
                sha256Hex(
                    "openai-hosted-web-search-credential\u0000${apiKey.trim()}"
                ),
            nonSecretHeaderNames = extraHeaders.keys.sortedWith(String.CASE_INSENSITIVE_ORDER),
            reasoningEffort = reasoningEffort.name,
            externalWebAccess = mode.externalWebAccess,
            responseSchemaRevision = OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION,
        )

}

internal data class OpenAIHostedWebSearchRequest(
    val requestId: String = "ows_${UUID.randomUUID().toString().replace("-", "")}",
    val query: String,
    val contextSize: OpenAIHostedWebSearchContextSize?,
    val allowedDomains: List<String>,
    val blockedDomains: List<String>,
    val useConfiguredLocation: Boolean,
)

internal data class OpenAIHostedWebSearchEffectiveRequest(
    val requestId: String,
    val query: String,
    val contextSize: OpenAIHostedWebSearchContextSize,
    val allowedDomains: List<String>,
    val blockedDomains: List<String>,
    val location: OpenAIHostedWebSearchApproximateLocation?,
    val locationRequested: Boolean = false,
    val locationConfigured: Boolean = location != null,
    val locationPrecision: String =
        openAIHostedWebSearchLocationPrecision(location),
)

internal fun openAIHostedWebSearchLocationPrecision(
    location: OpenAIHostedWebSearchApproximateLocation?,
): String {
    location ?: return "none"
    val populated =
        listOfNotNull(
            location.country?.let { "country" },
            location.region?.let { "region" },
            location.city?.let { "city" },
            location.timezone?.let { "timezone" },
        )
    return if (populated.size == 1) populated.single() else "mixed"
}

internal data class OpenAIHostedWebSearchCompatibilityFingerprint(
    val endpoint: String,
    val modelName: String,
    val authMode: String,
    val credentialDigest: String,
    val nonSecretHeaderNames: List<String>,
    val reasoningEffort: String,
    val externalWebAccess: Boolean,
    val responseSchemaRevision: Int,
) {
    val digest: String
        get() {
            val canonical =
                buildString {
                    append(endpoint).append('\n')
                    append(modelName).append('\n')
                    append(authMode).append('\n')
                    append(credentialDigest).append('\n')
                    nonSecretHeaderNames.forEach { header -> append(header.lowercase()).append('\n') }
                    append(reasoningEffort).append('\n')
                    append(externalWebAccess).append('\n')
                    append(responseSchemaRevision)
                }
            return sha256Hex(canonical)
        }
}

internal data class OpenAIHostedWebSearchCompatibilityRecord(
    val fingerprintDigest: String,
    val testedAtEpochMillis: Long,
    val responseId: String,
    val evidenceMode: OpenAIHostedWebSearchEvidenceMode,
    val schemaRevision: Int,
)

internal data class OpenAIHostedWebSearchCompatibilityFailureRecord(
    val fingerprintDigest: String,
    val testedAtEpochMillis: Long,
    val errorCode: OpenAIHostedWebSearchErrorCode,
    val httpStatus: Int?,
    val sanitizedMessage: String,
    val providerErrorType: String?,
    val providerErrorCode: String?,
    val providerRequestId: String?,
    val schemaRevision: Int,
)

internal enum class OpenAIHostedWebSearchErrorCode {
    BINDING_MISSING,
    CONFIG_SOURCE_INVALID,
    INVALID_ARGUMENT,
    PACKAGE_ENV_MISSING,
    ENDPOINT_INVALID,
    ENDPOINT_NOT_HTTPS,
    MODEL_NOT_ALLOWED,
    API_KEY_MISSING,
    RELAY_PROBE_REQUIRED,
    RELAY_PROBE_STALE,
    RELAY_INCOMPATIBLE,
    AUTH_REJECTED,
    RATE_LIMITED,
    QUEUE_TIMEOUT,
    REQUEST_TIMEOUT,
    REQUEST_CANCELLED,
    NETWORK_FAILURE,
    OPENAI_HTTP_FAILURE,
    RESPONSE_TOO_LARGE,
    RESPONSE_SCHEMA_INVALID,
    SEARCH_TOOL_NOT_CALLED,
    SEARCH_OUTPUT_EMPTY,
    CITATION_INVALID,
    SOURCE_INVALID,
    RELAY_RESPONSE_TEXT_ONLY,
    DOMAIN_FILTER_UNSUPPORTED_FOR_RELAY,
    DOMAIN_POLICY_VIOLATION,
    CALLER_NOT_AUTHORIZED,
}

internal enum class OpenAIHostedWebSearchArgumentReason {
    MISSING,
    INVALID_TYPE,
    INVALID_VALUE,
    CONFLICT,
    TOO_LARGE,
}

internal enum class OpenAIHostedWebSearchEvidenceMode(val wireValue: String) {
    URL_CITATIONS_AND_ACTION_SOURCES("url_citations_and_action_sources"),
    URL_CITATIONS("url_citations"),
    ACTION_SOURCES("action_sources"),
    STRUCTURED_FEEDS("structured_feeds"),
    NONE("none");

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchEvidenceMode =
            entries.firstOrNull { mode -> mode.wireValue == value.trim().lowercase(Locale.ROOT) }
                ?: throw IllegalArgumentException("Unsupported OpenAI Web Search evidence mode")
    }
}

internal class OpenAIHostedWebSearchException(
    val code: OpenAIHostedWebSearchErrorCode,
    override val message: String,
    val field: String? = null,
    val reason: OpenAIHostedWebSearchArgumentReason? = null,
    val httpStatus: Int? = null,
    val providerErrorType: String? = null,
    val providerErrorCode: String? = null,
    val providerRequestId: String? = null,
    val sourceDiagnostics: OpenAIHostedWebSearchSourceDiagnostics? = null,
    val phase: String? = null,
    val cancelOwner: String? = null,
    val submissionState: String? = null,
    val elapsedMs: Long? = null,
    val configuredTimeoutMs: Long? = null,
    val queueWaitMs: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    fun toJson(requestId: String?): JSONObject =
        JSONObject()
            .put("success", false)
            .put(
                "error",
                JSONObject()
                    .put("code", code.name)
                    .put("field", field ?: JSONObject.NULL)
                    .put("reason", reason?.name ?: JSONObject.NULL)
                    .put("message", "[$code] $message")
                    .put("http_status", httpStatus ?: JSONObject.NULL)
                    .put("provider_error_type", providerErrorType ?: JSONObject.NULL)
                    .put("provider_error_code", providerErrorCode ?: JSONObject.NULL)
                    .put("provider_request_id", providerRequestId ?: JSONObject.NULL)
                    .put("phase", phase ?: JSONObject.NULL)
                    .put("cancel_owner", cancelOwner ?: JSONObject.NULL)
                    .put("submission_state", submissionState ?: JSONObject.NULL)
                    .put("elapsed_ms", elapsedMs ?: JSONObject.NULL)
                    .put("configured_timeout_ms", configuredTimeoutMs ?: JSONObject.NULL)
                    .put("queue_wait_ms", queueWaitMs ?: JSONObject.NULL)
                    .put(
                        "source_diagnostics",
                        sourceDiagnostics?.toJson() ?: JSONObject.NULL,
                    )
                    .put("request_id", requestId ?: JSONObject.NULL),
            )
}

internal fun openAIHostedWebSearchInvalidArgument(
    field: String,
    reason: OpenAIHostedWebSearchArgumentReason,
    message: String,
    cause: Throwable? = null,
): OpenAIHostedWebSearchException =
    OpenAIHostedWebSearchException(
        code = OpenAIHostedWebSearchErrorCode.INVALID_ARGUMENT,
        field = field,
        reason = reason,
        message = message,
        submissionState = "not_sent",
        cause = cause,
    )

internal data class OpenAIHostedWebSearchAction(
    val type: String,
    val query: String?,
    val url: String?,
    val pattern: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("type", type)
            .put("query", query ?: JSONObject.NULL)
            .put("url", url ?: JSONObject.NULL)
            .put("pattern", pattern ?: JSONObject.NULL)
}

internal enum class OpenAIHostedWebSearchActionSourceCoverage(val wireValue: String) {
    NOT_APPLICABLE("not_applicable"),
    MISSING("missing"),
    PARTIAL("partial"),
    COMPLETE("complete");

    companion object {
        fun parse(value: String): OpenAIHostedWebSearchActionSourceCoverage =
            entries.firstOrNull { coverage ->
                coverage.wireValue == value.trim().lowercase(Locale.ROOT)
            } ?: throw IllegalArgumentException(
                "Unsupported OpenAI Web Search action-source coverage"
            )
    }
}

/**
 * Cross-channel source diagnostics for one completed Responses Web Search result.
 *
 * Every URL in this structure is already normalized by
 * [normalizeOpenAIHostedWebSearchUrl]. Citation annotations remain the authority for inline
 * citation spans; action sources are a separate audit channel and may be incomplete on relays.
 */
internal data class OpenAIHostedWebSearchSourceDiagnostics(
    val responseId: String,
    val actionSourceCoverage: OpenAIHostedWebSearchActionSourceCoverage,
    val actionSourceUrls: List<String>,
    val citationUrls: List<String>,
    val citationsMissingFromActionSources: List<String>,
    val openPageUrls: List<String>,
    val missingSourceActionIndexes: List<Int>,
    val invalidActionSourceCount: Int,
    val allowedDomains: List<String>,
    val domainPolicyState: OpenAIHostedWebSearchDomainPolicyState =
        OpenAIHostedWebSearchDomainPolicyState.NOT_REQUESTED,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("response_id", responseId)
            .put("action_source_coverage", actionSourceCoverage.wireValue)
            .put("action_source_urls", JSONArray(actionSourceUrls))
            .put("citation_urls", JSONArray(citationUrls))
            .put(
                "citations_missing_from_action_sources",
                JSONArray(citationsMissingFromActionSources),
            )
            .put("open_page_urls", JSONArray(openPageUrls))
            .put("missing_source_action_indexes", JSONArray(missingSourceActionIndexes))
            .put("invalid_action_source_count", invalidActionSourceCount)
            .put("allowed_domains", JSONArray(allowedDomains))
            .put("domain_policy_state", domainPolicyState.wireValue)
            .put("url_normalization", "http_https_identity")
}

internal data class OpenAIHostedWebSearchSourceSummary(
    val allSourceCount: Int,
    val citedSourceCount: Int,
    val uncitedSourceCount: Int,
    val urlSourceCount: Int,
    val structuredSourceCount: Int,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("all_source_count", allSourceCount)
            .put("cited_source_count", citedSourceCount)
            .put("uncited_source_count", uncitedSourceCount)
            .put("url_source_count", urlSourceCount)
            .put("structured_source_count", structuredSourceCount)
}

internal data class OpenAIHostedWebSearchLocationDiagnostics(
    val requested: Boolean,
    val configured: Boolean,
    val applied: Boolean,
    val precision: String,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("location_requested", requested)
            .put("location_configured", configured)
            .put("location_applied", applied)
            .put("location_precision", precision)
}

internal data class OpenAIHostedWebSearchExecutionDiagnostics(
    val totalElapsedMs: Long,
    val queueWaitMs: Long?,
    val httpElapsedMs: Long?,
    val responseHeaderWaitMs: Long?,
    val responseBodyReadMs: Long?,
    val parseMs: Long?,
    val callbackDeliveryMs: Long?,
    val providerRequestId: String?,
    val submissionState: OpenAIHostedWebSearchSubmissionState,
    val location: OpenAIHostedWebSearchLocationDiagnostics,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("total_elapsed_ms", totalElapsedMs)
            .put("queue_wait_ms", queueWaitMs ?: JSONObject.NULL)
            .put("http_elapsed_ms", httpElapsedMs ?: JSONObject.NULL)
            .put("response_header_wait_ms", responseHeaderWaitMs ?: JSONObject.NULL)
            .put("response_body_read_ms", responseBodyReadMs ?: JSONObject.NULL)
            .put("parse_ms", parseMs ?: JSONObject.NULL)
            .put("callback_delivery_ms", callbackDeliveryMs ?: JSONObject.NULL)
            .put("provider_request_id", providerRequestId ?: JSONObject.NULL)
            .put("submission_state", submissionState.wireValue)
            .put("location", location.toJson())
}

internal data class OpenAIHostedWebSearchSource(
    val sourceId: String,
    val type: String,
    val title: String,
    val url: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("source_id", sourceId)
            .put("type", type)
            .put("title", title)
            .put("url", url ?: JSONObject.NULL)
}

internal data class OpenAIHostedWebSearchCitation(
    val sourceId: String,
    val title: String,
    val url: String,
    val startIndex: Int,
    val endIndex: Int,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("source_id", sourceId)
            .put("title", title)
            .put("url", url)
            .put("start_index", startIndex)
            .put("end_index", endIndex)
}

internal data class OpenAIHostedWebSearchUsage(
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val webSearchCalls: Int,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("input_tokens", inputTokens)
            .put("cached_input_tokens", cachedInputTokens)
            .put("output_tokens", outputTokens)
            .put("web_search_calls", webSearchCalls)
}

internal data class OpenAIHostedWebSearchResult(
    val requestId: String,
    val responseId: String,
    val query: String,
    val mode: OpenAIHostedWebSearchMode,
    val modelName: String,
    val evidenceMode: OpenAIHostedWebSearchEvidenceMode,
    val answer: String,
    val answerWithSourceMarkers: String,
    val searchActions: List<OpenAIHostedWebSearchAction>,
    val citations: List<OpenAIHostedWebSearchCitation>,
    val citedSources: List<OpenAIHostedWebSearchSource>,
    val allSources: List<OpenAIHostedWebSearchSource>,
    val sourceSummary: OpenAIHostedWebSearchSourceSummary,
    val usage: OpenAIHostedWebSearchUsage,
    val warnings: List<String>,
    val sourceDiagnostics: OpenAIHostedWebSearchSourceDiagnostics,
    val executionDiagnostics: OpenAIHostedWebSearchExecutionDiagnostics =
        OpenAIHostedWebSearchExecutionDiagnostics(
            totalElapsedMs = 0L,
            queueWaitMs = null,
            httpElapsedMs = null,
            responseHeaderWaitMs = null,
            responseBodyReadMs = null,
            parseMs = null,
            callbackDeliveryMs = null,
            providerRequestId = null,
            submissionState = OpenAIHostedWebSearchSubmissionState.RESPONSE_STARTED,
            location =
                OpenAIHostedWebSearchLocationDiagnostics(
                    requested = false,
                    configured = false,
                    applied = false,
                    precision = "none",
                ),
        ),
) {
    fun toJson(
        executionDiagnosticsOverride: OpenAIHostedWebSearchExecutionDiagnostics? = null,
    ): JSONObject =
        JSONObject()
            .put("success", true)
            .put("schema_version", OpenAIHostedWebSearchContract.RESPONSE_SCHEMA_REVISION)
            .put("request_id", requestId)
            .put("response_id", responseId)
            .put("provider", "openai")
            .put("backend", "responses_web_search")
            .put("query", query)
            .put("mode", mode.wireValue)
            .put("model", modelName)
            .put("evidence_mode", evidenceMode.wireValue)
            .put("answer", answer)
            .put("answer_with_source_markers", answerWithSourceMarkers)
            .put(
                "search_actions",
                JSONArray().apply { searchActions.forEach { action -> put(action.toJson()) } },
            )
            .put(
                "citations",
                JSONArray().apply { citations.forEach { citation -> put(citation.toJson()) } },
            )
            .put(
                "cited_sources",
                JSONArray().apply { citedSources.forEach { source -> put(source.toJson()) } },
            )
            .put(
                "all_sources",
                JSONArray().apply { allSources.forEach { source -> put(source.toJson()) } },
            )
            .put("source_summary", sourceSummary.toJson())
            .put("usage", usage.toJson())
            .put("warnings", JSONArray(warnings))
            .put("source_diagnostics", sourceDiagnostics.toJson())
            .put(
                "execution_diagnostics",
                (executionDiagnosticsOverride ?: executionDiagnostics).toJson(),
            )
}

internal fun openAIHostedWebSearchCredentialRevision(apiKey: String): String =
    sha256Hex(
        "openai-hosted-web-search-credential\u0000${apiKey.trim()}"
    ).take(CREDENTIAL_REVISION_CHARACTERS)

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

private const val CREDENTIAL_REVISION_CHARACTERS = 12
