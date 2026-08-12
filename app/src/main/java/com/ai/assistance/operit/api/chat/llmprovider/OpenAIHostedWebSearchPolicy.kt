package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import java.net.URI
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

internal object OpenAIHostedWebSearchPolicy {
    private val HEADER_NAME = Regex("""^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$""")
    private val DOMAIN_LABEL =
        Regex("""^(?!-)[A-Za-z0-9-]{1,63}(?<!-)$""")
    private val BLOCKED_HEADER_NAMES =
        setOf(
            "authorization",
            "accept",
            "content-type",
            "host",
            "content-length",
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
        )

    fun validateBinding(
        binding: OpenAIHostedWebSearchBinding,
        compatibilityRecord: OpenAIHostedWebSearchCompatibilityRecord?,
        requireRelayProbe: Boolean,
    ) {
        if (binding.toolPkgId != OpenAIHostedWebSearchContract.TOOLPKG_ID) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CALLER_NOT_AUTHORIZED,
                message = "OpenAI Web Search binding belongs to an unexpected ToolPkg.",
            )
        }
        validateResponsesEndpoint(binding.endpoint)
        requireNonBlank(binding.modelName, OpenAIHostedWebSearchErrorCode.MODEL_NOT_ALLOWED) {
            "OpenAI Web Search model must not be blank."
        }
        requireNonBlank(binding.apiKey, OpenAIHostedWebSearchErrorCode.API_KEY_MISSING) {
            "OpenAI Web Search API key is missing."
        }
        if (binding.apiKey.any { character -> character == '\r' || character == '\n' }) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.API_KEY_MISSING,
                message = "OpenAI Web Search API key is invalid.",
            )
        }
        if (binding.configSource == OpenAIHostedWebSearchConfigSource.MODEL_CONFIG) {
            requireNonBlank(
                binding.modelConfigId,
                OpenAIHostedWebSearchErrorCode.MODEL_CONFIG_NOT_FOUND,
            ) {
                "MODEL_CONFIG requires a fixed model config ID."
            }
        } else if (!binding.modelConfigId.isNullOrBlank()) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "PACKAGE_ENV must not include a model config ID.",
            )
        }
        validateAuth(binding.authHeaderName, binding.authScheme)
        validateHeaders(binding.extraHeaders, binding.authHeaderName)
        validateDomains(binding.allowedDomains, binding.blockedDomains)
        validateLocation(binding.location)
        if (binding.additionalInstructions.length >
            OpenAIHostedWebSearchContract.MAX_ADDITIONAL_INSTRUCTIONS_CHARACTERS
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search additional instructions are too long.",
            )
        }
        binding.maxOutputTokens?.let { maxOutputTokens ->
            if (maxOutputTokens !in 1..OpenAIHostedWebSearchContract.MAX_OUTPUT_TOKENS) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message =
                        "OpenAI Web Search max output tokens must be between 1 and " +
                            "${OpenAIHostedWebSearchContract.MAX_OUTPUT_TOKENS}.",
                )
            }
        }
        if (binding.timeoutSeconds !in 1..OpenAIHostedWebSearchContract.MAX_TIMEOUT_SECONDS) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "OpenAI Web Search timeout must be between 1 and " +
                        "${OpenAIHostedWebSearchContract.MAX_TIMEOUT_SECONDS} seconds.",
            )
        }
        if (
            binding.maxConcurrentRequests !in
                1..OpenAIHostedWebSearchContract.MAX_CONCURRENT_REQUESTS
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "OpenAI Web Search max concurrent requests must be between 1 and " +
                        "${OpenAIHostedWebSearchContract.MAX_CONCURRENT_REQUESTS}.",
            )
        }
        if (
            binding.requestsPerMinute !in
                0..OpenAIHostedWebSearchContract.MAX_REQUESTS_PER_MINUTE
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "OpenAI Web Search requests per minute must be between 0 and " +
                        "${OpenAIHostedWebSearchContract.MAX_REQUESTS_PER_MINUTE}.",
            )
        }
        if (
            binding.modelConfigMaxConcurrentRequests < 0 ||
                binding.modelConfigRequestsPerMinute < 0
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search model config request limits must not be negative.",
            )
        }

        when (binding.providerContract) {
            OpenAIHostedWebSearchProviderContract.RESPONSES_HOSTED_OFFICIAL -> {
                val authority =
                    OpenAiEndpointContract.resolve(
                        providerType = ApiProviderType.OPENAI_RESPONSES,
                        apiEndpoint = binding.endpoint,
                    )
                if (authority != ProviderContractAuthority.OPENAI_OFFICIAL) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID,
                        message =
                            "RESPONSES_HOSTED_OFFICIAL requires the exact official " +
                                "https://api.openai.com/v1/responses endpoint.",
                    )
                }
                if (binding.modelName !in OpenAIHostedWebSearchContract.OFFICIAL_MODEL_ALLOWLIST) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.MODEL_NOT_ALLOWED,
                        message =
                            "The official OpenAI Web Search model must be one of: " +
                                OpenAIHostedWebSearchContract.OFFICIAL_MODEL_ALLOWLIST
                                    .sorted()
                                    .joinToString(", "),
                    )
                }
            }

            OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT -> {
                if (requireRelayProbe) {
                    val record =
                        compatibilityRecord
                            ?: throw OpenAIHostedWebSearchException(
                                code = OpenAIHostedWebSearchErrorCode.RELAY_PROBE_REQUIRED,
                                message =
                                    "This Responses-compatible relay requires an explicit paid " +
                                        "Web Search compatibility probe.",
                            )
                    if (record.fingerprintDigest != binding.compatibilityFingerprint().digest) {
                        throw OpenAIHostedWebSearchException(
                            code = OpenAIHostedWebSearchErrorCode.RELAY_PROBE_STALE,
                            message =
                                "The relay endpoint, model, auth mode, headers, reasoning, or " +
                                    "web access setting changed after the last compatibility probe.",
                        )
                    }
                }
            }
        }
    }

    fun compileEffectiveRequest(
        binding: OpenAIHostedWebSearchBinding,
        request: OpenAIHostedWebSearchRequest,
    ): OpenAIHostedWebSearchEffectiveRequest {
        val query = request.query.trim()
        if (query.isEmpty()) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search query must not be blank.",
            )
        }
        if (query.length > OpenAIHostedWebSearchContract.MAX_QUERY_CHARACTERS) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search query is too long.",
            )
        }

        val requestedAllowed = normalizeDomains(request.allowedDomains)
        val requestedBlocked = normalizeDomains(request.blockedDomains)
        val effectiveAllowed =
            if (binding.allowedDomains.isEmpty()) {
                requestedAllowed
            } else if (requestedAllowed.isEmpty()) {
                binding.allowedDomains
            } else {
                if (
                    requestedAllowed.any { requestedDomain ->
                        binding.allowedDomains.none { configuredDomain ->
                            requestedDomain == configuredDomain ||
                                requestedDomain.endsWith(".$configuredDomain")
                        }
                    }
                ) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                        message =
                            "Tool-call allowed domains may only narrow the configured allowlist.",
                    )
                }
                requestedAllowed
            }
        val effectiveBlocked =
            (binding.blockedDomains + requestedBlocked).distinct().sorted()
        validateDomains(effectiveAllowed, effectiveBlocked)

        return OpenAIHostedWebSearchEffectiveRequest(
            requestId = request.requestId,
            query = query,
            contextSize = request.contextSize ?: binding.contextSize,
            allowedDomains = effectiveAllowed,
            blockedDomains = effectiveBlocked,
            location = if (request.useConfiguredLocation) binding.location else null,
        )
    }

    fun validateResponsesEndpoint(endpoint: String): URI {
        val trimmed = endpoint.trim()
        val uri =
            runCatching { URI(trimmed) }.getOrNull()
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID,
                    message = "OpenAI Web Search endpoint is not a valid URI.",
                )
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.ENDPOINT_NOT_HTTPS,
                message = "OpenAI Web Search endpoint must use HTTPS.",
            )
        }
        if (
            uri.host.isNullOrBlank() ||
                uri.userInfo != null ||
                uri.rawQuery != null ||
                uri.rawFragment != null
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID,
                message =
                    "OpenAI Web Search endpoint must have a valid host and no userinfo, query, " +
                        "or fragment.",
            )
        }
        val normalizedPath = uri.path.orEmpty().removeSuffix("/")
        if (!normalizedPath.endsWith("/responses", ignoreCase = true)) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.ENDPOINT_INVALID,
                message = "OpenAI Web Search endpoint must be a complete Responses URL.",
            )
        }
        return uri
    }

    fun parseHeadersJson(rawJson: String): Map<String, String> {
        if (rawJson.isBlank()) return emptyMap()
        val jsonObject =
            runCatching { JSONObject(rawJson) }.getOrElse { error ->
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search extra headers must be a JSON object.",
                    cause = error,
                )
            }
        return buildMap {
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.opt(key)
                if (value !is String) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                        message = "OpenAI Web Search extra header values must be strings.",
                    )
                }
                put(key, value)
            }
        }
    }

    fun parseDomainArrayJson(rawJson: String): List<String> {
        if (rawJson.isBlank()) return emptyList()
        val array =
            runCatching { JSONArray(rawJson) }.getOrElse { error ->
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search domains must be a JSON string array.",
                    cause = error,
                )
            }
        val values =
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.opt(index)
                    if (value !is String) {
                        throw OpenAIHostedWebSearchException(
                            code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                            message = "OpenAI Web Search domains must be strings.",
                        )
                    }
                    add(value)
                }
            }
        return normalizeDomains(values)
    }

    fun parseLocationJson(rawJson: String): OpenAIHostedWebSearchApproximateLocation? {
        if (rawJson.isBlank()) return null
        val value =
            runCatching { JSONObject(rawJson) }.getOrElse { error ->
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search location must be a JSON object.",
                    cause = error,
                )
            }
        if (value.optString("type", "") != "approximate") {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search location type must be approximate.",
            )
        }
        val allowedKeys = setOf("type", "country", "city", "region", "timezone")
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowedKeys) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search location contains an unsupported field.",
                )
            }
            if (key != "type" && value.opt(key) !is String) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search location fields must be strings.",
                )
            }
        }
        return OpenAIHostedWebSearchApproximateLocation(
            country = value.optString("country", "").trim().ifEmpty { null },
            city = value.optString("city", "").trim().ifEmpty { null },
            region = value.optString("region", "").trim().ifEmpty { null },
            timezone = value.optString("timezone", "").trim().ifEmpty { null },
        ).also(::validateLocation)
    }

    private fun validateAuth(headerName: String, authScheme: String) {
        val normalizedHeaderName = headerName.trim()
        if (!HEADER_NAME.matches(normalizedHeaderName)) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search auth header name is invalid.",
            )
        }
        if (
            normalizedHeaderName.lowercase(Locale.ROOT) in
                BLOCKED_HEADER_NAMES - "authorization"
        ) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search auth header name is not allowed.",
            )
        }
        if (authScheme.any { character -> character == '\r' || character == '\n' }) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search auth scheme is invalid.",
            )
        }
    }

    private fun validateHeaders(
        headers: Map<String, String>,
        authHeaderName: String,
    ) {
        headers.forEach { (headerName, value) ->
            val normalizedName = headerName.trim()
            if (!HEADER_NAME.matches(normalizedName)) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search extra header name is invalid.",
                )
            }
            if (
                normalizedName.equals(authHeaderName, ignoreCase = true) ||
                    normalizedName.lowercase(Locale.ROOT) in BLOCKED_HEADER_NAMES
            ) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message =
                        "OpenAI Web Search extra headers cannot override auth or hop-by-hop headers.",
                )
            }
            if (value.any { character -> character == '\r' || character == '\n' }) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search extra header value is invalid.",
                )
            }
        }
    }

    private fun normalizeDomains(domains: List<String>): List<String> {
        if (domains.size > OpenAIHostedWebSearchContract.MAX_DOMAIN_COUNT) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message = "OpenAI Web Search domain list is too large.",
            )
        }
        return domains
            .map { rawDomain ->
                val domain = rawDomain.trim().trimEnd('.').lowercase(Locale.ROOT)
                if (
                    domain.isEmpty() ||
                        "://" in domain ||
                        domain.any { character ->
                            character == '/' ||
                                character == ':' ||
                                character == '?' ||
                                character == '#' ||
                                character == '@'
                        } ||
                        domain.split('.').any { label -> !DOMAIN_LABEL.matches(label) }
                ) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                        message = "OpenAI Web Search domain is invalid: $rawDomain",
                    )
                }
                domain
            }
            .distinct()
            .sorted()
    }

    private fun validateDomains(
        allowedDomains: List<String>,
        blockedDomains: List<String>,
    ) {
        val allowed = normalizeDomains(allowedDomains)
        val blocked = normalizeDomains(blockedDomains)
        val overlap = allowed.toSet().intersect(blocked.toSet())
        if (overlap.isNotEmpty()) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                message =
                    "OpenAI Web Search allowed and blocked domains overlap: " +
                        overlap.sorted().joinToString(", "),
            )
        }
    }

    private fun validateLocation(location: OpenAIHostedWebSearchApproximateLocation?) {
        location ?: return
        location.country?.let { country ->
            if (!Regex("""^[A-Z]{2}$""").matches(country)) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search location country must be an ISO two-letter code.",
                )
            }
        }
        listOf(location.city, location.region).forEach { value ->
            if (value != null && (value.length > 200 || value.any { it == '\r' || it == '\n' })) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search approximate location text is invalid.",
                )
            }
        }
        location.timezone?.let { timezone ->
            if (!TimeZone.getAvailableIDs().contains(timezone)) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.CONFIG_SOURCE_INVALID,
                    message = "OpenAI Web Search location timezone must be a known IANA timezone.",
                )
            }
        }
    }

    private inline fun requireNonBlank(
        value: String?,
        code: OpenAIHostedWebSearchErrorCode,
        message: () -> String,
    ) {
        if (value.isNullOrBlank()) {
            throw OpenAIHostedWebSearchException(code = code, message = message())
        }
    }
}
