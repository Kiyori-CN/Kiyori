package com.ai.assistance.operit.api.chat.llmprovider

import java.net.URI
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One parsed Web Search URL with separate display and identity contracts.
 *
 * The display URL remains suitable for user navigation. The identity key exists only for
 * cross-channel comparison, source deduplication, and reported-domain policy checks. Keeping these
 * values separate prevents a canonicalization rule from silently changing the URL a user opens.
 */
internal data class OpenAIHostedWebSearchUrlIdentity(
    val displayUrl: String,
    val identityKey: String,
    val normalizedHost: String,
) {
    companion object {
        fun parse(rawUrl: String): OpenAIHostedWebSearchUrlIdentity {
            val trimmed = rawUrl.trim()
            val uri =
                runCatching { URI(trimmed) }.getOrNull()
                    ?: throw invalidSourceUrl()
            if (uri.userInfo != null) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
                    message = "Web Search source URL has an invalid authority.",
                )
            }
            val httpUrl = trimmed.toHttpUrlOrNull() ?: throw invalidSourceUrl()
            if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
                    message = "Web Search source URL must use HTTP or HTTPS.",
                )
            }
            if (httpUrl.host.isBlank()) {
                throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
                    message = "Web Search source URL has an invalid authority.",
                )
            }

            return OpenAIHostedWebSearchUrlIdentity(
                displayUrl = httpUrl.toString(),
                identityKey = buildIdentityKey(httpUrl),
                normalizedHost = httpUrl.host.lowercase(Locale.ROOT),
            )
        }

        private fun buildIdentityKey(httpUrl: HttpUrl): String =
            buildString {
                append(httpUrl.scheme.lowercase(Locale.ROOT))
                append("://")
                append(httpUrl.host.lowercase(Locale.ROOT))
                if (!httpUrl.usesDefaultPort()) {
                    append(':')
                    append(httpUrl.port)
                }
                append(canonicalizePercentEncoding(httpUrl.encodedPath))
                canonicalIdentityQuery(httpUrl.encodedQuery)?.let { query ->
                    append('?')
                    append(query)
                }
            }

        private fun HttpUrl.usesDefaultPort(): Boolean =
            (scheme == "http" && port == 80) || (scheme == "https" && port == 443)

        private fun canonicalIdentityQuery(encodedQuery: String?): String? {
            encodedQuery ?: return null
            val retainedParts =
                encodedQuery
                    .split('&')
                    .filterNot(::isTrackingQueryPart)
                    .map(::canonicalizePercentEncoding)
            return retainedParts.joinToString("&").takeIf(String::isNotEmpty)
        }

        private fun isTrackingQueryPart(encodedPart: String): Boolean {
            val encodedName = encodedPart.substringBefore('=')
            val name =
                canonicalizePercentEncoding(encodedName)
                    .lowercase(Locale.ROOT)
            return name.startsWith("utm_") || name == "gclid" || name == "fbclid"
        }

        /**
         * Decode only RFC 3986 unreserved ASCII bytes. Reserved delimiters stay encoded so identity
         * comparison cannot merge distinct path or query structures. Query '+' is intentionally
         * left unchanged and is never interpreted as a form-encoded space.
         */
        private fun canonicalizePercentEncoding(value: String): String =
            buildString(value.length) {
                var index = 0
                while (index < value.length) {
                    val character = value[index]
                    if (
                        character == '%' &&
                            index + 2 < value.length &&
                            value[index + 1].isHexDigit() &&
                            value[index + 2].isHexDigit()
                    ) {
                        val byteValue =
                            (value[index + 1].hexValue() shl 4) or value[index + 2].hexValue()
                        val decoded = byteValue.toChar()
                        if (decoded.isRfc3986Unreserved()) {
                            append(decoded)
                        } else {
                            append('%')
                            append(HEX_DIGITS[byteValue ushr 4])
                            append(HEX_DIGITS[byteValue and 0x0F])
                        }
                        index += 3
                    } else {
                        append(character)
                        index += 1
                    }
                }
            }

        private fun Char.isRfc3986Unreserved(): Boolean =
            this in 'A'..'Z' ||
                this in 'a'..'z' ||
                this in '0'..'9' ||
                this == '-' ||
                this == '.' ||
                this == '_' ||
                this == '~'

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private fun Char.hexValue(): Int =
            when (this) {
                in '0'..'9' -> code - '0'.code
                in 'a'..'f' -> code - 'a'.code + 10
                in 'A'..'F' -> code - 'A'.code + 10
                else -> error("Non-hexadecimal character")
            }

        private fun invalidSourceUrl(): OpenAIHostedWebSearchException =
            OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.SOURCE_INVALID,
                message = "Web Search source URL is invalid.",
            )

        private const val HEX_DIGITS = "0123456789ABCDEF"
    }
}

internal enum class OpenAIHostedWebSearchDomainPolicyState(val wireValue: String) {
    NOT_REQUESTED("not_requested"),
    REPORTED_ACTIONS_COMPLIANT("reported_actions_compliant"),
}

internal object OpenAIHostedWebSearchDomainPolicy {
    private val SITE_CONSTRAINT =
        Regex("""(?i)(?:^|[\s(])site:([A-Za-z0-9.-]+)""")

    fun requireRequestSupported(
        providerContract: OpenAIHostedWebSearchProviderContract,
        request: OpenAIHostedWebSearchEffectiveRequest,
    ) {
        if (
            providerContract ==
                OpenAIHostedWebSearchProviderContract.RESPONSES_RELAY_STRICT &&
                request.hasDomainFilters()
        ) {
            throw OpenAIHostedWebSearchException(
                code =
                    OpenAIHostedWebSearchErrorCode
                        .DOMAIN_FILTER_UNSUPPORTED_FOR_RELAY,
                message =
                    "Domain filters require the official OpenAI hosted Responses contract.",
                submissionState = "not_sent",
            )
        }
    }

    fun auditReportedBehavior(
        providerContract: OpenAIHostedWebSearchProviderContract,
        request: OpenAIHostedWebSearchEffectiveRequest,
        reportedUrls: Collection<OpenAIHostedWebSearchUrlIdentity>,
        actionQueries: Collection<String>,
    ): OpenAIHostedWebSearchDomainPolicyState {
        if (!request.hasDomainFilters()) {
            return OpenAIHostedWebSearchDomainPolicyState.NOT_REQUESTED
        }
        requireRequestSupported(providerContract, request)

        val reportedHostViolation =
            reportedUrls.any { identity ->
                !hostComplies(
                    host = identity.normalizedHost,
                    allowedDomains = request.allowedDomains,
                    blockedDomains = request.blockedDomains,
                )
            }
        val siteConstraintViolation =
            actionQueries
                .asSequence()
                .flatMap(::siteConstraints)
                .any { host ->
                    !hostComplies(
                        host = host,
                        allowedDomains = request.allowedDomains,
                        blockedDomains = request.blockedDomains,
                    )
                }
        if (reportedHostViolation || siteConstraintViolation) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.DOMAIN_POLICY_VIOLATION,
                message =
                    "The hosted Web Search response reported behavior that violates the " +
                        "effective domain policy.",
            )
        }
        return OpenAIHostedWebSearchDomainPolicyState.REPORTED_ACTIONS_COMPLIANT
    }

    private fun OpenAIHostedWebSearchEffectiveRequest.hasDomainFilters(): Boolean =
        allowedDomains.isNotEmpty() || blockedDomains.isNotEmpty()

    private fun siteConstraints(query: String): Sequence<String> =
        SITE_CONSTRAINT
            .findAll(query)
            .map { match ->
                match.groupValues[1]
                    .trim()
                    .trimEnd('.')
                    .lowercase(Locale.ROOT)
            }
            .filter(String::isNotEmpty)

    private fun hostComplies(
        host: String,
        allowedDomains: List<String>,
        blockedDomains: List<String>,
    ): Boolean {
        if (blockedDomains.any { domain -> hostMatchesDomain(host, domain) }) {
            return false
        }
        return allowedDomains.isEmpty() ||
            allowedDomains.any { domain -> hostMatchesDomain(host, domain) }
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")
}

internal fun normalizeOpenAIHostedWebSearchUrl(rawUrl: String): String =
    OpenAIHostedWebSearchUrlIdentity.parse(rawUrl).displayUrl
