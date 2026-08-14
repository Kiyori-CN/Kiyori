package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.URI
import java.util.LinkedHashSet
import java.util.Locale

internal enum class BrowserAdBlockRuleSource {
    CUSTOM,
    SUBSCRIPTION,
}

internal data class BrowserAdBlockNetworkRuleSpec(
    val id: String,
    val rule: String,
    val source: BrowserAdBlockRuleSource,
    val sourceName: String,
    val enabled: Boolean = true,
)

internal data class BrowserAdBlockElementRuleSpec(
    val id: String,
    val domainExpression: String,
    val selector: String,
    val source: BrowserAdBlockRuleSource,
    val sourceName: String,
    val exception: Boolean = false,
    val enabled: Boolean = true,
)

internal data class BrowserAdBlockSubscriptionParseResult(
    val networkRules: List<BrowserAdBlockNetworkRuleSpec>,
    val elementRules: List<BrowserAdBlockElementRuleSpec>,
    val ignoredLineCount: Int,
)

internal data class BrowserAdBlockDecision(
    val blocked: Boolean,
    val ruleId: String,
    val rule: String,
    val source: BrowserAdBlockRuleSource,
    val sourceName: String,
)

internal class BrowserAdBlockMatcher private constructor(
    private val enabled: Boolean,
    private val allowlistedDomains: Set<String>,
    networkRules: List<BrowserAdBlockNetworkRuleSpec>,
    elementRules: List<BrowserAdBlockElementRuleSpec>,
) {
    private val networkIndex = BrowserAdBlockNetworkIndex(networkRules)
    private val compiledElementRules =
        elementRules
            .asSequence()
            .filter(BrowserAdBlockElementRuleSpec::enabled)
            .mapNotNull(::compileBrowserAdBlockElementRule)
            .toList()

    fun decide(
        pageUrl: String,
        requestUrl: String,
    ): BrowserAdBlockDecision? {
        if (!enabled) {
            return null
        }
        val pageHost = normalizeBrowserAdBlockDomain(pageUrl)
        if (pageHost.isNotBlank() && isBrowserAdBlockDomainCovered(pageHost, allowlistedDomains)) {
            return null
        }
        return networkIndex.decide(pageHost = pageHost, requestUrl = requestUrl)
    }

    fun selectorsForPage(pageUrl: String): List<String> {
        if (!enabled) {
            return emptyList()
        }
        val pageHost = normalizeBrowserAdBlockDomain(pageUrl)
        if (pageHost.isBlank() || isBrowserAdBlockDomainCovered(pageHost, allowlistedDomains)) {
            return emptyList()
        }
        val exceptions =
            compiledElementRules
                .asSequence()
                .filter { rule -> rule.exception && rule.matches(pageHost) }
                .map(CompiledBrowserAdBlockElementRule::selector)
                .toSet()
        return compiledElementRules
            .asSequence()
            .filter { rule -> !rule.exception && rule.matches(pageHost) }
            .map(CompiledBrowserAdBlockElementRule::selector)
            .filterNot(exceptions::contains)
            .distinct()
            .toList()
    }

    companion object {
        val EMPTY =
            BrowserAdBlockMatcher(
                enabled = false,
                allowlistedDomains = emptySet(),
                networkRules = emptyList(),
                elementRules = emptyList(),
            )

        fun compile(
            enabled: Boolean,
            allowlistedDomains: Collection<String>,
            networkRules: List<BrowserAdBlockNetworkRuleSpec>,
            elementRules: List<BrowserAdBlockElementRuleSpec>,
        ): BrowserAdBlockMatcher =
            BrowserAdBlockMatcher(
                enabled = enabled,
                allowlistedDomains =
                    allowlistedDomains
                        .mapNotNull(::normalizeBrowserAdBlockDomainInput)
                        .toSet(),
                networkRules = networkRules,
                elementRules = elementRules,
            )
    }
}

internal fun parseBrowserAdBlockSubscription(
    text: String,
    subscriptionId: String,
    subscriptionName: String,
): BrowserAdBlockSubscriptionParseResult {
    val networkRules = mutableListOf<BrowserAdBlockNetworkRuleSpec>()
    val elementRules = mutableListOf<BrowserAdBlockElementRuleSpec>()
    var ignoredLineCount = 0

    text.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (
            line.isBlank() ||
                line.startsWith("!") ||
                line.startsWith("[") ||
                line.startsWith("#")
        ) {
            return@forEachIndexed
        }

        val cosmeticSeparator =
            when {
                line.contains("#@#") -> "#@#"
                line.contains("##") -> "##"
                else -> null
            }
        if (cosmeticSeparator != null) {
            val domainExpression = line.substringBefore(cosmeticSeparator).trim()
            val selector = line.substringAfter(cosmeticSeparator).trim()
            if (isValidBrowserAdBlockSelector(selector)) {
                elementRules +=
                    BrowserAdBlockElementRuleSpec(
                        id = "$subscriptionId:element:$index",
                        domainExpression = domainExpression,
                        selector = selector,
                        source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                        sourceName = subscriptionName,
                        exception = cosmeticSeparator == "#@#",
                    )
            } else {
                ignoredLineCount += 1
            }
            return@forEachIndexed
        }

        val spec =
            BrowserAdBlockNetworkRuleSpec(
                id = "$subscriptionId:network:$index",
                rule = line,
                source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                sourceName = subscriptionName,
            )
        if (compileBrowserAdBlockNetworkRule(spec) == null) {
            ignoredLineCount += 1
        } else {
            networkRules += spec
        }
    }

    return BrowserAdBlockSubscriptionParseResult(
        networkRules = networkRules,
        elementRules = elementRules,
        ignoredLineCount = ignoredLineCount,
    )
}

internal fun normalizeBrowserAdBlockDomainInput(value: String): String? {
    val trimmed = value.trim().lowercase(Locale.ROOT).trim('.')
    if (trimmed.isBlank()) {
        return null
    }
    val host =
        if (trimmed.contains("://")) {
            runCatching { URI(trimmed).host }.getOrNull()
        } else {
            trimmed.substringBefore('/').substringBefore(':')
        }?.lowercase(Locale.ROOT)?.trim('.')
    if (
        host.isNullOrBlank() ||
            host.length > 253 ||
            host.split('.').any { label ->
                label.isBlank() ||
                    label.length > 63 ||
                    label.first() == '-' ||
                    label.last() == '-' ||
                    label.any { character ->
                        !character.isLetterOrDigit() && character != '-'
                    }
            }
    ) {
        return null
    }
    return host
}

internal fun normalizeBrowserAdBlockDomain(url: String): String =
    runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT).trim('.') }
        .getOrDefault("")

internal fun suggestBrowserAdBlockNetworkRule(url: String): String {
    val uri = runCatching { URI(url.trim()) }.getOrNull()
    val host = uri?.host?.lowercase(Locale.ROOT)?.trim('.').orEmpty()
    if (host.isBlank()) {
        val normalized = url.trim().substringBefore('#').substringBefore('?')
        require(normalized.isNotBlank()) { "Cannot create an ad-block rule from a blank URL" }
        return normalized
    }
    val path = uri?.rawPath.orEmpty()
    return if (path.isBlank() || path == "/") {
        "||$host^"
    } else {
        "||$host$path"
    }
}

internal fun isValidBrowserAdBlockSelector(selector: String): Boolean {
    val trimmed = selector.trim()
    return trimmed.isNotBlank() &&
        trimmed.length <= BROWSER_AD_BLOCK_MAX_SELECTOR_LENGTH &&
        !trimmed.contains('\u0000') &&
        !trimmed.contains('{') &&
        !trimmed.contains('}')
}

internal fun isValidBrowserAdBlockNetworkRule(rule: String): Boolean =
    compileBrowserAdBlockNetworkRule(
        BrowserAdBlockNetworkRuleSpec(
            id = "validation",
            rule = rule,
            source = BrowserAdBlockRuleSource.CUSTOM,
            sourceName = "validation",
        ),
    ) != null

private class BrowserAdBlockNetworkIndex(
    rules: List<BrowserAdBlockNetworkRuleSpec>,
) {
    private val blockIndex = indexRules(rules, exception = false)
    private val exceptionIndex = indexRules(rules, exception = true)

    fun decide(
        pageHost: String,
        requestUrl: String,
    ): BrowserAdBlockDecision? {
        val normalizedUrl = requestUrl.lowercase(Locale.ROOT)
        if (normalizedUrl.isBlank()) {
            return null
        }
        val requestHost = normalizeBrowserAdBlockDomain(normalizedUrl)
        val exception =
            exceptionIndex.candidates(normalizedUrl, requestHost)
                .firstOrNull { rule -> rule.matches(normalizedUrl, pageHost) }
        if (exception != null) {
            return null
        }
        val blockingRule =
            blockIndex.candidates(normalizedUrl, requestHost)
                .firstOrNull { rule -> rule.matches(normalizedUrl, pageHost) }
                ?: return null
        return BrowserAdBlockDecision(
            blocked = true,
            ruleId = blockingRule.spec.id,
            rule = blockingRule.spec.rule,
            source = blockingRule.spec.source,
            sourceName = blockingRule.spec.sourceName,
        )
    }

    private fun indexRules(
        rules: List<BrowserAdBlockNetworkRuleSpec>,
        exception: Boolean,
    ): CompiledBrowserAdBlockRuleIndex {
        val compiled =
            rules
                .asSequence()
                .filter(BrowserAdBlockNetworkRuleSpec::enabled)
                .mapNotNull(::compileBrowserAdBlockNetworkRule)
                .filter { rule -> rule.exception == exception }
                .toList()
        return CompiledBrowserAdBlockRuleIndex(compiled)
    }
}

private class CompiledBrowserAdBlockRuleIndex(
    rules: List<CompiledBrowserAdBlockNetworkRule>,
) {
    private val hostAnchoredRules =
        rules
            .filter { rule -> rule.hostAnchor != null }
            .groupBy { rule -> checkNotNull(rule.hostAnchor) }
    private val tokenRules =
        rules
            .filter { rule -> rule.hostAnchor == null && rule.indexKey != null }
            .groupBy { rule -> checkNotNull(rule.indexKey) }
    private val unindexedRules =
        rules.filter { rule -> rule.hostAnchor == null && rule.indexKey == null }

    fun candidates(
        normalizedUrl: String,
        requestHost: String,
    ): Sequence<CompiledBrowserAdBlockNetworkRule> {
        val candidates = LinkedHashSet<CompiledBrowserAdBlockNetworkRule>()
        if (requestHost.isNotBlank()) {
            browserAdBlockDomainSuffixes(requestHost).forEach { suffix ->
                hostAnchoredRules[suffix]?.let(candidates::addAll)
            }
        }
        if (normalizedUrl.length >= BROWSER_AD_BLOCK_INDEX_KEY_LENGTH) {
            for (index in 0..normalizedUrl.length - BROWSER_AD_BLOCK_INDEX_KEY_LENGTH) {
                tokenRules[
                    normalizedUrl.substring(
                        index,
                        index + BROWSER_AD_BLOCK_INDEX_KEY_LENGTH,
                    )
                ]?.let(candidates::addAll)
            }
        }
        candidates.addAll(unindexedRules)
        return candidates.asSequence()
    }
}

private data class CompiledBrowserAdBlockNetworkRule(
    val spec: BrowserAdBlockNetworkRuleSpec,
    val exception: Boolean,
    val hostAnchor: String?,
    val domainIncludes: Set<String>,
    val domainExcludes: Set<String>,
    val indexKey: String?,
    val matcher: (String) -> Boolean,
) {
    fun matches(
        normalizedUrl: String,
        pageHost: String,
    ): Boolean {
        if (
            pageHost.isNotBlank() &&
                domainExcludes.any { domain -> browserAdBlockDomainMatches(pageHost, domain) }
        ) {
            return false
        }
        if (
            domainIncludes.isNotEmpty() &&
                (
                    pageHost.isBlank() ||
                        domainIncludes.none { domain ->
                            browserAdBlockDomainMatches(pageHost, domain)
                        }
                    )
        ) {
            return false
        }
        return matcher(normalizedUrl)
    }
}

private data class CompiledBrowserAdBlockElementRule(
    val selector: String,
    val exception: Boolean,
    val domainIncludes: Set<String>,
    val domainExcludes: Set<String>,
) {
    fun matches(pageHost: String): Boolean {
        if (domainExcludes.any { domain -> browserAdBlockDomainMatches(pageHost, domain) }) {
            return false
        }
        return domainIncludes.isEmpty() ||
            domainIncludes.any { domain -> browserAdBlockDomainMatches(pageHost, domain) }
    }
}

private fun compileBrowserAdBlockNetworkRule(
    spec: BrowserAdBlockNetworkRuleSpec,
): CompiledBrowserAdBlockNetworkRule? {
    val raw = spec.rule.trim()
    if (raw.isBlank() || raw.startsWith("!") || raw.contains("##") || raw.contains("#@#")) {
        return null
    }
    val exception = raw.startsWith("@@")
    val withoutException = if (exception) raw.removePrefix("@@") else raw
    val pattern = withoutException.substringBefore('$').trim()
    if (pattern.isBlank() || pattern.length > BROWSER_AD_BLOCK_MAX_NETWORK_RULE_LENGTH) {
        return null
    }
    val (domainIncludes, domainExcludes) =
        parseBrowserAdBlockDomainOption(withoutException.substringAfter('$', ""))

    if (
        pattern.startsWith("/") &&
            pattern.endsWith("/") &&
            pattern.length > 2
    ) {
        val regex = runCatching { Regex(pattern.substring(1, pattern.length - 1)) }.getOrNull()
            ?: return null
        return CompiledBrowserAdBlockNetworkRule(
            spec = spec,
            exception = exception,
            hostAnchor = null,
            domainIncludes = domainIncludes,
            domainExcludes = domainExcludes,
            indexKey = longestBrowserAdBlockLiteralToken(pattern)?.take(BROWSER_AD_BLOCK_INDEX_KEY_LENGTH),
            matcher = regex::containsMatchIn,
        )
    }

    if (pattern.startsWith("||")) {
        val host =
            pattern
                .removePrefix("||")
                .takeWhile { character ->
                    character.isLetterOrDigit() || character == '.' || character == '-'
                }
                .lowercase(Locale.ROOT)
                .trim('.')
        val normalizedHost = normalizeBrowserAdBlockDomainInput(host) ?: return null
        val suffixPattern = pattern.removePrefix("||").removePrefix(host)
        val suffixRegex =
            suffixPattern
                .takeIf(String::isNotBlank)
                ?.let(::browserAdBlockPatternRegex)
        return CompiledBrowserAdBlockNetworkRule(
            spec = spec,
            exception = exception,
            hostAnchor = normalizedHost,
            domainIncludes = domainIncludes,
            domainExcludes = domainExcludes,
            indexKey = null,
            matcher = { normalizedUrl ->
                val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return@CompiledBrowserAdBlockNetworkRule false
                val requestHost = uri.host.orEmpty().lowercase(Locale.ROOT).trim('.')
                if (!browserAdBlockDomainMatches(requestHost, normalizedHost)) {
                    false
                } else {
                    suffixRegex?.containsMatchIn(normalizedUrl) ?: true
                }
            },
        )
    }

    val hasSpecialSyntax =
        pattern.contains('*') ||
            pattern.contains('^') ||
            pattern.startsWith('|') ||
            pattern.endsWith('|')
    if (!hasSpecialSyntax) {
        val literal = pattern.lowercase(Locale.ROOT)
        return CompiledBrowserAdBlockNetworkRule(
            spec = spec,
            exception = exception,
            hostAnchor = null,
            domainIncludes = domainIncludes,
            domainExcludes = domainExcludes,
            indexKey =
                literal
                    .takeIf { value -> value.length >= BROWSER_AD_BLOCK_INDEX_KEY_LENGTH }
                    ?.take(BROWSER_AD_BLOCK_INDEX_KEY_LENGTH),
            matcher = { normalizedUrl -> normalizedUrl.contains(literal) },
        )
    }

    val regex = browserAdBlockPatternRegex(pattern) ?: return null
    val literalToken = longestBrowserAdBlockLiteralToken(pattern)
    return CompiledBrowserAdBlockNetworkRule(
        spec = spec,
        exception = exception,
        hostAnchor = null,
        domainIncludes = domainIncludes,
        domainExcludes = domainExcludes,
        indexKey =
            literalToken
                ?.takeIf { token -> token.length >= BROWSER_AD_BLOCK_INDEX_KEY_LENGTH }
                ?.take(BROWSER_AD_BLOCK_INDEX_KEY_LENGTH),
        matcher = regex::containsMatchIn,
    )
}

private fun browserAdBlockPatternRegex(pattern: String): Regex? {
    val anchoredAtStart = pattern.startsWith("|")
    val anchoredAtEnd = pattern.endsWith("|") && pattern.length > 1
    val body =
        pattern
            .removePrefix("|")
            .let { value -> if (anchoredAtEnd) value.dropLast(1) else value }
    if (body.isBlank()) {
        return null
    }
    val regex =
        buildString {
            if (anchoredAtStart) {
                append('^')
            }
            body.forEach { character ->
                when (character) {
                    '*' -> append(".*")
                    '^' -> append("(?:[^A-Za-z0-9_.%-]|$)")
                    else -> append(Regex.escape(character.toString()))
                }
            }
            if (anchoredAtEnd) {
                append('$')
            }
        }
    return runCatching { Regex(regex, RegexOption.IGNORE_CASE) }.getOrNull()
}

private fun parseBrowserAdBlockDomainOption(
    rawOptions: String,
): Pair<Set<String>, Set<String>> {
    val domainOption =
        rawOptions
            .split(',')
            .firstOrNull { option -> option.startsWith("domain=", ignoreCase = true) }
            ?.substringAfter('=')
            .orEmpty()
    if (domainOption.isBlank()) {
        return emptySet<String>() to emptySet()
    }
    val includes = mutableSetOf<String>()
    val excludes = mutableSetOf<String>()
    domainOption.split('|').forEach { rawDomain ->
        val excluded = rawDomain.startsWith('~')
        val normalized = normalizeBrowserAdBlockDomainInput(rawDomain.removePrefix("~"))
            ?: return@forEach
        if (excluded) {
            excludes += normalized
        } else {
            includes += normalized
        }
    }
    return includes to excludes
}

private fun compileBrowserAdBlockElementRule(
    spec: BrowserAdBlockElementRuleSpec,
): CompiledBrowserAdBlockElementRule? {
    if (!isValidBrowserAdBlockSelector(spec.selector)) {
        return null
    }
    val includes = mutableSetOf<String>()
    val excludes = mutableSetOf<String>()
    spec.domainExpression
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { rawDomain ->
            val excluded = rawDomain.startsWith('~')
            val normalized = normalizeBrowserAdBlockDomainInput(rawDomain.removePrefix("~"))
                ?: return@forEach
            if (excluded) {
                excludes += normalized
            } else {
                includes += normalized
            }
        }
    return CompiledBrowserAdBlockElementRule(
        selector = spec.selector.trim(),
        exception = spec.exception,
        domainIncludes = includes,
        domainExcludes = excludes,
    )
}

private fun longestBrowserAdBlockLiteralToken(pattern: String): String? =
    pattern
        .lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9_%.-]+"))
        .maxByOrNull(String::length)
        ?.takeIf(String::isNotBlank)

private fun browserAdBlockDomainSuffixes(host: String): Sequence<String> = sequence {
    var current = host
    while (current.isNotBlank()) {
        yield(current)
        val separator = current.indexOf('.')
        if (separator < 0) {
            break
        }
        current = current.substring(separator + 1)
    }
}

private fun isBrowserAdBlockDomainCovered(
    host: String,
    domains: Set<String>,
): Boolean = domains.any { domain -> browserAdBlockDomainMatches(host, domain) }

private fun browserAdBlockDomainMatches(
    host: String,
    domain: String,
): Boolean = host == domain || host.endsWith(".$domain")

private const val BROWSER_AD_BLOCK_INDEX_KEY_LENGTH = 4
private const val BROWSER_AD_BLOCK_MAX_NETWORK_RULE_LENGTH = 2_048
private const val BROWSER_AD_BLOCK_MAX_SELECTOR_LENGTH = 2_048
