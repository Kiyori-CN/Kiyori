package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.URI
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class BrowserAdBlockRuleSource {
    CUSTOM,
    SUBSCRIPTION,
}

internal enum class BrowserAdBlockResourceType {
    DOCUMENT,
    SUBDOCUMENT,
    SCRIPT,
    STYLESHEET,
    IMAGE,
    MEDIA,
    FONT,
    OBJECT,
    XMLHTTPREQUEST,
    WEBSOCKET,
    PING,
    OTHER,
}

internal data class BrowserAdBlockRequestContext(
    val pageUrl: String,
    val requestUrl: String,
    val resourceType: BrowserAdBlockResourceType,
    val isMainFrame: Boolean = false,
)

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

internal data class BrowserAdBlockElementDecision(
    val selector: String,
    val ruleId: String,
    val source: BrowserAdBlockRuleSource,
    val sourceName: String,
)

internal data class BrowserAdBlockPagePolicy(
    val document: Boolean = false,
    val elementHide: Boolean = false,
    val genericHide: Boolean = false,
    val genericBlock: Boolean = false,
) {
    operator fun plus(other: BrowserAdBlockPagePolicy): BrowserAdBlockPagePolicy =
        BrowserAdBlockPagePolicy(
            document = document || other.document,
            elementHide = elementHide || other.elementHide,
            genericHide = genericHide || other.genericHide,
            genericBlock = genericBlock || other.genericBlock,
        )
}

internal data class BrowserAdBlockBadFilter(
    val ruleSetId: String,
    val source: BrowserAdBlockRuleSource,
    val canonicalRuleKey: String,
)

internal class BrowserAdBlockBadFilterIndex(
    filters: Collection<BrowserAdBlockBadFilter>,
    activeRuleSetIds: Set<String>,
) {
    private val customRuleKeys =
        filters
            .asSequence()
            .filter { filter -> filter.ruleSetId in activeRuleSetIds }
            .filter { filter -> filter.source == BrowserAdBlockRuleSource.CUSTOM }
            .map(BrowserAdBlockBadFilter::canonicalRuleKey)
            .toSet()
    private val subscriptionRuleKeys =
        filters
            .asSequence()
            .filter { filter -> filter.ruleSetId in activeRuleSetIds }
            .filter { filter -> filter.source == BrowserAdBlockRuleSource.SUBSCRIPTION }
            .map(BrowserAdBlockBadFilter::canonicalRuleKey)
            .toSet()

    fun disables(rule: CompiledBrowserAdBlockNetworkRule): Boolean {
        if (customRuleKeys.isEmpty() && subscriptionRuleKeys.isEmpty()) {
            return false
        }
        val canonicalRuleKey = browserAdBlockCanonicalRuleKey(rule.spec.rule)
        return canonicalRuleKey in customRuleKeys ||
            (
                rule.spec.source == BrowserAdBlockRuleSource.SUBSCRIPTION &&
                    canonicalRuleKey in subscriptionRuleKeys
                )
    }
}

internal class BrowserAdBlockCompiledRuleSet private constructor(
    internal val id: String,
    internal val networkRules: List<CompiledBrowserAdBlockNetworkRule>,
    internal val elementRules: List<CompiledBrowserAdBlockElementRule>,
    internal val badFilters: Set<BrowserAdBlockBadFilter>,
) {
    companion object {
        val EMPTY =
            BrowserAdBlockCompiledRuleSet(
                id = BROWSER_AD_BLOCK_DEFAULT_RULE_SET_ID,
                networkRules = emptyList(),
                elementRules = emptyList(),
                badFilters = emptySet(),
            )

        fun compile(
            id: String = BROWSER_AD_BLOCK_DEFAULT_RULE_SET_ID,
            networkRules: List<BrowserAdBlockNetworkRuleSpec>,
            elementRules: List<BrowserAdBlockElementRuleSpec>,
        ): BrowserAdBlockCompiledRuleSet {
            val enabledNetworkRules = networkRules.filter(BrowserAdBlockNetworkRuleSpec::enabled)
            return BrowserAdBlockCompiledRuleSet(
                id = id,
                networkRules =
                    enabledNetworkRules
                        .asSequence()
                        .filterNot { spec -> browserAdBlockRuleHasOption(spec.rule, "badfilter") }
                        .mapNotNull { spec ->
                            compileBrowserAdBlockNetworkRule(
                                spec = spec,
                                ruleSetId = id,
                            )
                        }
                        .toList(),
                elementRules =
                    elementRules
                        .asSequence()
                        .filter(BrowserAdBlockElementRuleSpec::enabled)
                        .mapNotNull { spec ->
                            compileBrowserAdBlockElementRule(
                                spec = spec,
                                ruleSetId = id,
                            )
                        }
                        .toList(),
                badFilters =
                    enabledNetworkRules
                        .asSequence()
                        .filter { spec -> browserAdBlockRuleHasOption(spec.rule, "badfilter") }
                        .mapNotNull { spec ->
                            browserAdBlockRuleWithoutOption(spec.rule, "badfilter")?.let { rule ->
                                BrowserAdBlockBadFilter(
                                    ruleSetId = id,
                                    source = spec.source,
                                    canonicalRuleKey = browserAdBlockCanonicalRuleKey(rule),
                                )
                            }
                        }
                        .toSet(),
            )
        }
    }
}

internal class BrowserAdBlockEngine private constructor(
    private val networkIndex: BrowserAdBlockNetworkIndex,
    private val elementIndex: BrowserAdBlockElementIndex,
    private val badFilters: Set<BrowserAdBlockBadFilter>,
) {
    fun createMatcher(
        enabled: Boolean,
        allowlistedDomains: Collection<String>,
        activeRuleSetIds: Collection<String>,
    ): BrowserAdBlockMatcher =
        BrowserAdBlockMatcher(
            enabled = enabled,
            allowlistedDomains =
                allowlistedDomains
                    .mapNotNull(::normalizeBrowserAdBlockDomainInput)
                    .toSet(),
            activeRuleSetIds = activeRuleSetIds.toSet(),
            networkIndex = networkIndex,
            elementIndex = elementIndex,
            badFilters = badFilters,
        )

    companion object {
        val EMPTY =
            BrowserAdBlockEngine(
                networkIndex = BrowserAdBlockNetworkIndex.fromCompiled(emptyList()),
                elementIndex = BrowserAdBlockElementIndex.fromCompiled(emptyList()),
                badFilters = emptySet(),
            )

        fun compile(ruleSets: Collection<BrowserAdBlockCompiledRuleSet>): BrowserAdBlockEngine =
            BrowserAdBlockEngine(
                networkIndex =
                    BrowserAdBlockNetworkIndex.fromCompiled(
                        ruleSets.flatMap(BrowserAdBlockCompiledRuleSet::networkRules),
                    ),
                elementIndex =
                    BrowserAdBlockElementIndex.fromCompiled(
                        ruleSets.flatMap(BrowserAdBlockCompiledRuleSet::elementRules),
                    ),
                badFilters =
                    ruleSets
                        .asSequence()
                        .flatMap { ruleSet -> ruleSet.badFilters.asSequence() }
                        .toSet(),
            )

        fun combine(engines: Collection<BrowserAdBlockEngine>): BrowserAdBlockEngine {
            if (engines.isEmpty()) {
                return EMPTY
            }
            return BrowserAdBlockEngine(
                networkIndex =
                    BrowserAdBlockNetworkIndex.combine(
                        engines.map(BrowserAdBlockEngine::networkIndex),
                    ),
                elementIndex =
                    BrowserAdBlockElementIndex.combine(
                        engines.map(BrowserAdBlockEngine::elementIndex),
                    ),
                badFilters =
                    engines
                        .asSequence()
                        .flatMap { engine -> engine.badFilters.asSequence() }
                        .toSet(),
            )
        }
    }
}

internal data class BrowserAdBlockPreparedRequest(
    val context: BrowserAdBlockRequestContext,
    val pageHost: String,
    val requestHost: String,
    val normalizedRequestUrl: String,
    val thirdParty: Boolean,
)

private class BrowserAdBlockLruCache<K, V>(
    private val maxSize: Int,
) {
    private val lock = Any()
    private val values =
        object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxSize
        }

    fun getOrPut(
        key: K,
        producer: () -> V,
    ): V =
        synchronized(lock) {
            // WebView 会并发请求同一页面的脚本、图片和接口。缓存 miss 时必须由第一个请求
            // 完成页面策略计算，否则首批子资源会重复扫描相同候选规则并制造 CPU 峰值。
            values.getOrPut(key) {
                producer()
            }
        }
}

internal class BrowserAdBlockMatcher internal constructor(
    private val enabled: Boolean,
    private val allowlistedDomains: Set<String>,
    private val activeRuleSetIds: Set<String>,
    private val networkIndex: BrowserAdBlockNetworkIndex,
    private val elementIndex: BrowserAdBlockElementIndex,
    badFilters: Set<BrowserAdBlockBadFilter>,
) {
    private val badFilterIndex =
        BrowserAdBlockBadFilterIndex(
            filters = badFilters,
            activeRuleSetIds = activeRuleSetIds,
        )
    private val pagePolicyCache =
        BrowserAdBlockLruCache<String, BrowserAdBlockPagePolicy>(
            BROWSER_AD_BLOCK_PAGE_CACHE_SIZE,
        )
    private val elementDecisionCache =
        BrowserAdBlockLruCache<String, List<BrowserAdBlockElementDecision>>(
            BROWSER_AD_BLOCK_ELEMENT_PAGE_CACHE_SIZE,
        )
    private val topPrivateDomainCache =
        BrowserAdBlockLruCache<String, String>(
            BROWSER_AD_BLOCK_SITE_CACHE_SIZE,
        )

    fun decide(
        pageUrl: String,
        requestUrl: String,
    ): BrowserAdBlockDecision? =
        decide(
            BrowserAdBlockRequestContext(
                pageUrl = pageUrl,
                requestUrl = requestUrl,
                resourceType = BrowserAdBlockResourceType.OTHER,
            ),
        )

    fun decide(context: BrowserAdBlockRequestContext): BrowserAdBlockDecision? {
        if (!enabled || context.isMainFrame) {
            return null
        }
        if (activeRuleSetIds.isEmpty()) {
            return null
        }
        val prepared = prepareRequest(context)
        if (
            prepared.pageHost.isNotBlank() &&
                isBrowserAdBlockDomainCovered(prepared.pageHost, allowlistedDomains)
        ) {
            return null
        }
        val pagePolicy = pagePolicyFor(prepared)
        if (pagePolicy.document) {
            return null
        }
        return networkIndex.decide(
            prepared = prepared,
            activeRuleSetIds = activeRuleSetIds,
            suppressGeneric = pagePolicy.genericBlock,
            badFilterIndex = badFilterIndex,
        )
    }

    fun elementDecisionsForPage(pageUrl: String): List<BrowserAdBlockElementDecision> {
        if (!enabled || activeRuleSetIds.isEmpty()) {
            return emptyList()
        }
        return elementDecisionCache.getOrPut(pageUrl) {
            val prepared =
                prepareRequest(
                    BrowserAdBlockRequestContext(
                        pageUrl = pageUrl,
                        requestUrl = pageUrl,
                        resourceType = BrowserAdBlockResourceType.DOCUMENT,
                        isMainFrame = true,
                    ),
                )
            if (
                prepared.pageHost.isBlank() ||
                    isBrowserAdBlockDomainCovered(prepared.pageHost, allowlistedDomains)
            ) {
                return@getOrPut emptyList()
            }
            val pagePolicy = pagePolicyFor(prepared)
            if (pagePolicy.document || pagePolicy.elementHide) {
                return@getOrPut emptyList()
            }
            elementIndex.decisions(
                pageHost = prepared.pageHost,
                suppressGeneric = pagePolicy.genericHide,
                activeRuleSetIds = activeRuleSetIds,
            )
        }
    }

    private fun pagePolicyFor(prepared: BrowserAdBlockPreparedRequest): BrowserAdBlockPagePolicy =
        pagePolicyCache.getOrPut(prepared.context.pageUrl) {
            val documentPrepared =
                if (
                    prepared.context.requestUrl == prepared.context.pageUrl &&
                        prepared.context.resourceType == BrowserAdBlockResourceType.DOCUMENT
                ) {
                    prepared
                } else {
                    prepareRequest(
                        BrowserAdBlockRequestContext(
                            pageUrl = prepared.context.pageUrl,
                            requestUrl = prepared.context.pageUrl,
                            resourceType = BrowserAdBlockResourceType.DOCUMENT,
                            isMainFrame = true,
                        ),
                    )
                }
            networkIndex.pagePolicy(
                prepared = documentPrepared,
                activeRuleSetIds = activeRuleSetIds,
                badFilterIndex = badFilterIndex,
            )
        }

    private fun prepareRequest(
        context: BrowserAdBlockRequestContext,
    ): BrowserAdBlockPreparedRequest {
        val pageHost = normalizeBrowserAdBlockDomain(context.pageUrl)
        val requestHost = normalizeBrowserAdBlockDomain(context.requestUrl)
        val thirdParty =
            if (
                networkIndex.requiresPartyClassification &&
                    pageHost.isNotBlank() &&
                    requestHost.isNotBlank()
            ) {
                topPrivateDomainCache.getOrPut(pageHost) {
                    browserAdBlockTopPrivateDomain(context.pageUrl, pageHost)
                } !=
                    topPrivateDomainCache.getOrPut(requestHost) {
                        browserAdBlockTopPrivateDomain(context.requestUrl, requestHost)
                    }
            } else {
                false
            }
        return BrowserAdBlockPreparedRequest(
            context = context,
            pageHost = pageHost,
            requestHost = requestHost,
            normalizedRequestUrl = context.requestUrl.lowercase(Locale.ROOT),
            thirdParty = thirdParty,
        )
    }

    fun selectorsForPage(pageUrl: String): List<String> =
        elementDecisionsForPage(pageUrl)
            .map(BrowserAdBlockElementDecision::selector)

    internal fun debugNetworkCandidateCount(
        context: BrowserAdBlockRequestContext,
    ): Int =
        networkIndex.candidateCount(
            prepareRequest(context),
        )

    companion object {
        val EMPTY =
            BrowserAdBlockEngine.EMPTY.createMatcher(
                enabled = false,
                allowlistedDomains = emptySet(),
                activeRuleSetIds = emptySet(),
            )

        fun compile(
            enabled: Boolean,
            allowlistedDomains: Collection<String>,
            networkRules: List<BrowserAdBlockNetworkRuleSpec>,
            elementRules: List<BrowserAdBlockElementRuleSpec>,
        ): BrowserAdBlockMatcher {
            val ruleSet =
                BrowserAdBlockCompiledRuleSet.compile(
                    networkRules = networkRules,
                    elementRules = elementRules,
                )
            return BrowserAdBlockEngine
                .compile(listOf(ruleSet))
                .createMatcher(
                    enabled = enabled,
                    allowlistedDomains = allowlistedDomains,
                    activeRuleSetIds = setOf(ruleSet.id),
                )
        }

        fun fromCompiled(
            enabled: Boolean,
            allowlistedDomains: Collection<String>,
            ruleSets: List<BrowserAdBlockCompiledRuleSet>,
        ): BrowserAdBlockMatcher =
            BrowserAdBlockEngine
                .compile(ruleSets)
                .createMatcher(
                    enabled = enabled,
                    allowlistedDomains = allowlistedDomains,
                    activeRuleSetIds = ruleSets.map(BrowserAdBlockCompiledRuleSet::id),
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
                line.startsWith("[")
        ) {
            return@forEachIndexed
        }

        if (line.length > BROWSER_AD_BLOCK_MAX_NETWORK_RULE_LENGTH) {
            ignoredLineCount += 1
            return@forEachIndexed
        }

        if (containsUnsupportedBrowserAdBlockCosmeticSyntax(line)) {
            ignoredLineCount += 1
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
            val spec =
                BrowserAdBlockElementRuleSpec(
                    id = "$subscriptionId:element:$index",
                    domainExpression = domainExpression,
                    selector = selector,
                    source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                    sourceName = subscriptionName,
                    exception = cosmeticSeparator == "#@#",
                )
            if (
                isValidBrowserAdBlockSubscriptionSelector(selector) &&
                    compileBrowserAdBlockElementRule(spec) != null
            ) {
                elementRules += spec
            } else {
                ignoredLineCount += 1
            }
            return@forEachIndexed
        }

        if (line.startsWith("#")) {
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

internal fun browserAdBlockRequestContext(
    pageUrl: String,
    requestUrl: String,
    requestHeaders: Map<String, String>,
    isMainFrame: Boolean,
): BrowserAdBlockRequestContext =
    BrowserAdBlockRequestContext(
        pageUrl = pageUrl,
        requestUrl = requestUrl,
        resourceType =
            classifyBrowserAdBlockResourceType(
                requestUrl = requestUrl,
                requestHeaders = requestHeaders,
                isMainFrame = isMainFrame,
            ),
        isMainFrame = isMainFrame,
    )

internal fun classifyBrowserAdBlockResourceType(
    requestUrl: String,
    requestHeaders: Map<String, String>,
    isMainFrame: Boolean,
): BrowserAdBlockResourceType {
    if (isMainFrame) {
        return BrowserAdBlockResourceType.DOCUMENT
    }
    val destination =
        requestHeaders.browserAdBlockHeader("Sec-Fetch-Dest")
            .lowercase(Locale.ROOT)
    when (destination) {
        "document", "iframe", "frame" -> return BrowserAdBlockResourceType.SUBDOCUMENT
        "script", "worker", "sharedworker", "serviceworker" ->
            return BrowserAdBlockResourceType.SCRIPT
        "style" -> return BrowserAdBlockResourceType.STYLESHEET
        "image" -> return BrowserAdBlockResourceType.IMAGE
        "audio", "video", "track" -> return BrowserAdBlockResourceType.MEDIA
        "font" -> return BrowserAdBlockResourceType.FONT
        "embed", "object" -> return BrowserAdBlockResourceType.OBJECT
    }
    if (
        requestHeaders
            .browserAdBlockHeader("X-Requested-With")
            .equals("XMLHttpRequest", ignoreCase = true)
    ) {
        return BrowserAdBlockResourceType.XMLHTTPREQUEST
    }
    val scheme =
        runCatching { URI(requestUrl).scheme.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault("")
    if (scheme == "ws" || scheme == "wss") {
        return BrowserAdBlockResourceType.WEBSOCKET
    }
    val accept =
        requestHeaders.browserAdBlockHeader("Accept")
            .lowercase(Locale.ROOT)
    val extension =
        requestUrl
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
    return when {
        accept.contains("javascript") || extension in BrowserAdBlockScriptExtensions ->
            BrowserAdBlockResourceType.SCRIPT
        accept.contains("text/css") || extension == "css" ->
            BrowserAdBlockResourceType.STYLESHEET
        accept.contains("image/") || extension in BrowserAdBlockImageExtensions ->
            BrowserAdBlockResourceType.IMAGE
        accept.contains("video/") ||
            accept.contains("audio/") ||
            extension in BrowserAdBlockMediaExtensions -> BrowserAdBlockResourceType.MEDIA
        accept.contains("font/") ||
            extension in BrowserAdBlockFontExtensions -> BrowserAdBlockResourceType.FONT
        accept.contains("text/html") ||
            extension in BrowserAdBlockDocumentExtensions -> BrowserAdBlockResourceType.SUBDOCUMENT
        accept.contains("json") ||
            accept.contains("xml") ||
            extension in BrowserAdBlockDataExtensions -> BrowserAdBlockResourceType.XMLHTTPREQUEST
        else -> BrowserAdBlockResourceType.OTHER
    }
}

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

private fun isValidBrowserAdBlockSubscriptionSelector(selector: String): Boolean {
    val trimmed = selector.trim()
    if (!isValidBrowserAdBlockSelector(trimmed)) {
        return false
    }
    val normalized = trimmed.lowercase(Locale.ROOT)
    return !normalized.startsWith("+js(") &&
        !normalized.startsWith("^") &&
        !normalized.contains(":has-text(") &&
        !normalized.contains(":matches-css") &&
        !normalized.contains(":xpath(") &&
        !normalized.contains(":upward(") &&
        !normalized.contains(":remove(") &&
        !normalized.contains(":style(") &&
        !normalized.contains(":-abp-contains(") &&
        !normalized.contains(":-abp-has(")
}

private fun containsUnsupportedBrowserAdBlockCosmeticSyntax(line: String): Boolean =
    BrowserAdBlockUnsupportedCosmeticSeparators.any(line::contains) ||
        line.contains("##+js", ignoreCase = true)

internal fun isValidBrowserAdBlockNetworkRule(rule: String): Boolean =
    compileBrowserAdBlockNetworkRule(
        BrowserAdBlockNetworkRuleSpec(
            id = "validation",
            rule = rule,
            source = BrowserAdBlockRuleSource.CUSTOM,
            sourceName = "validation",
        ),
    ) != null

internal fun isBrowserAdBlockNetworkRuleTargetedAtDomain(
    rule: String,
    domain: String,
): Boolean {
    val normalizedDomain = normalizeBrowserAdBlockDomainInput(domain) ?: return false
    val spec =
        BrowserAdBlockNetworkRuleSpec(
            id = "domain-clear-check",
            rule = rule,
            source = BrowserAdBlockRuleSource.CUSTOM,
            sourceName = "domain-clear-check",
        )
    val compiled = compileBrowserAdBlockNetworkRule(spec) ?: return false
    if (compiled.exception) {
        return false
    }
    if (
        compiled.domainIncludes.any { includedDomain ->
            browserAdBlockDomainMatches(normalizedDomain, includedDomain)
        }
    ) {
        return true
    }
    if (compiled.domainIncludes.isNotEmpty()) {
        return false
    }
    if (
        compiled.hostAnchor?.let { hostAnchor ->
            browserAdBlockDomainMatches(normalizedDomain, hostAnchor)
        } == true
    ) {
        return true
    }
    val explicitHost = explicitHostForBrowserAdBlockNetworkRule(rule)
    return explicitHost != null &&
        browserAdBlockDomainMatches(normalizedDomain, explicitHost)
}

internal class BrowserAdBlockNetworkIndex private constructor(
    private val ruleIndexes: List<CompiledBrowserAdBlockRuleIndex>,
    internal val requiresPartyClassification: Boolean,
) {

    internal fun pagePolicy(
        prepared: BrowserAdBlockPreparedRequest,
        activeRuleSetIds: Set<String>,
        badFilterIndex: BrowserAdBlockBadFilterIndex,
    ): BrowserAdBlockPagePolicy =
        candidates(prepared)
            .fold(BrowserAdBlockPagePolicy()) { current, rule ->
                if (
                    rule.ruleSetId in activeRuleSetIds &&
                        rule.exception &&
                        rule.pagePolicy != BrowserAdBlockPagePolicy() &&
                        !badFilterIndex.disables(rule) &&
                        rule.matches(prepared)
                ) {
                    current + rule.pagePolicy
                } else {
                    current
                }
            }

    internal fun decide(
        prepared: BrowserAdBlockPreparedRequest,
        activeRuleSetIds: Set<String>,
        suppressGeneric: Boolean,
        badFilterIndex: BrowserAdBlockBadFilterIndex,
    ): BrowserAdBlockDecision? {
        var importantBlock: CompiledBrowserAdBlockNetworkRule? = null
        var normalExceptionMatched = false
        var normalBlock: CompiledBrowserAdBlockNetworkRule? = null
        for (rule in candidates(prepared)) {
            if (
                rule.ruleSetId !in activeRuleSetIds ||
                    badFilterIndex.disables(rule) ||
                    (suppressGeneric && !rule.exception && rule.generic) ||
                    !rule.matches(prepared)
            ) {
                continue
            }
            when {
                rule.exception && rule.important -> return null
                !rule.exception && rule.important && importantBlock == null ->
                    importantBlock = rule
                rule.exception -> normalExceptionMatched = true
                normalBlock == null -> normalBlock = rule
            }
        }
        return when {
            importantBlock != null -> importantBlock.toDecision()
            normalExceptionMatched -> null
            else -> normalBlock?.toDecision()
        }
    }

    internal fun candidateCount(prepared: BrowserAdBlockPreparedRequest): Int =
        candidates(prepared).count()

    private fun candidates(
        prepared: BrowserAdBlockPreparedRequest,
    ): Sequence<CompiledBrowserAdBlockNetworkRule> {
        val candidates = LinkedHashSet<CompiledBrowserAdBlockNetworkRule>()
        ruleIndexes.forEach { ruleIndex ->
            ruleIndex.addCandidates(
                prepared = prepared,
                candidates = candidates,
            )
        }
        return candidates.asSequence()
    }

    companion object {
        fun fromCompiled(
            compiledRules: List<CompiledBrowserAdBlockNetworkRule>,
        ): BrowserAdBlockNetworkIndex =
            BrowserAdBlockNetworkIndex(
                ruleIndexes = listOf(CompiledBrowserAdBlockRuleIndex(compiledRules)),
                requiresPartyClassification =
                    compiledRules.any { rule -> rule.thirdParty != null },
            )

        fun combine(
            indexes: Collection<BrowserAdBlockNetworkIndex>,
        ): BrowserAdBlockNetworkIndex =
            BrowserAdBlockNetworkIndex(
                ruleIndexes = indexes.flatMap(BrowserAdBlockNetworkIndex::ruleIndexes),
                requiresPartyClassification =
                    indexes.any(BrowserAdBlockNetworkIndex::requiresPartyClassification),
            )
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
            .groupBy { rule -> checkNotNull(rule.indexKey).length }
            .mapValues { (_, rulesForLength) ->
                rulesForLength.groupBy { rule ->
                    browserAdBlockTokenHash(checkNotNull(rule.indexKey))
                }
            }
    private val unindexedRules =
        rules.filter { rule -> rule.hostAnchor == null && rule.indexKey == null }

    fun addCandidates(
        prepared: BrowserAdBlockPreparedRequest,
        candidates: MutableSet<CompiledBrowserAdBlockNetworkRule>,
    ) {
        if (prepared.requestHost.isNotBlank()) {
            browserAdBlockDomainSuffixes(prepared.requestHost).forEach { suffix ->
                hostAnchoredRules[suffix]?.let(candidates::addAll)
            }
        }
        tokenRules.forEach { (length, buckets) ->
            browserAdBlockTokenHashes(prepared.normalizedRequestUrl, length).forEach { hash ->
                buckets[hash]?.let(candidates::addAll)
            }
        }
        candidates.addAll(unindexedRules)
    }
}

internal data class CompiledBrowserAdBlockNetworkRule(
    val ruleSetId: String,
    val spec: BrowserAdBlockNetworkRuleSpec,
    val exception: Boolean,
    val hostAnchor: String?,
    val domainIncludes: Set<String>,
    val domainExcludes: Set<String>,
    val resourceIncludes: Set<BrowserAdBlockResourceType>,
    val resourceExcludes: Set<BrowserAdBlockResourceType>,
    val thirdParty: Boolean?,
    val denyAllowDomains: Set<String>,
    val matchCase: Boolean,
    val important: Boolean,
    val generic: Boolean,
    val pagePolicy: BrowserAdBlockPagePolicy,
    val indexKey: String?,
    val literalPattern: String?,
    val wildcardPattern: String?,
    val patternRegex: Regex?,
    val matchAll: Boolean,
) {
    fun matches(prepared: BrowserAdBlockPreparedRequest): Boolean {
        if (
            prepared.pageHost.isNotBlank() &&
                domainExcludes.any { domain ->
                    browserAdBlockDomainMatches(prepared.pageHost, domain)
                }
        ) {
            return false
        }
        if (
            domainIncludes.isNotEmpty() &&
                (
                    prepared.pageHost.isBlank() ||
                        domainIncludes.none { domain ->
                            browserAdBlockDomainMatches(prepared.pageHost, domain)
                        }
                )
        ) {
            return false
        }
        if (prepared.context.resourceType in resourceExcludes) {
            return false
        }
        if (
            resourceIncludes.isNotEmpty() &&
                prepared.context.resourceType !in resourceIncludes
        ) {
            return false
        }
        if (thirdParty != null && prepared.thirdParty != thirdParty) {
            return false
        }
        if (
            prepared.requestHost.isNotBlank() &&
                denyAllowDomains.any { domain ->
                    browserAdBlockDomainMatches(prepared.requestHost, domain)
                }
        ) {
            return false
        }
        if (
            hostAnchor != null &&
                (
                    prepared.requestHost.isBlank() ||
                        !browserAdBlockDomainMatches(prepared.requestHost, hostAnchor)
                )
        ) {
            return false
        }
        val candidate =
            if (matchCase) {
                prepared.context.requestUrl
            } else {
                prepared.normalizedRequestUrl
            }
        return when {
            matchAll -> true
            literalPattern != null -> candidate.contains(literalPattern)
            wildcardPattern != null -> browserAdBlockWildcardMatches(candidate, wildcardPattern)
            patternRegex != null -> patternRegex.containsMatchIn(candidate)
            else -> false
        }
    }

    fun toDecision(): BrowserAdBlockDecision =
        BrowserAdBlockDecision(
            blocked = true,
            ruleId = spec.id,
            rule = spec.rule,
            source = spec.source,
            sourceName = spec.sourceName,
        )
}

private class BrowserAdBlockElementPartition(
    compiledRules: List<CompiledBrowserAdBlockElementRule>,
) {
    val genericRules = compiledRules.filter(CompiledBrowserAdBlockElementRule::generic)
    val domainRules =
        buildMap<String, MutableList<CompiledBrowserAdBlockElementRule>> {
            compiledRules
                .filterNot(CompiledBrowserAdBlockElementRule::generic)
                .forEach { rule ->
                    rule.domainIncludes.forEach { domain ->
                        getOrPut(domain) { mutableListOf() }.add(rule)
                    }
                }
        }
}

internal class BrowserAdBlockElementIndex private constructor(
    private val partitions: List<BrowserAdBlockElementPartition>,
) {
    internal fun decisions(
        pageHost: String,
        suppressGeneric: Boolean,
        activeRuleSetIds: Set<String>,
    ): List<BrowserAdBlockElementDecision> {
        val candidates =
            candidates(
                pageHost = pageHost,
                suppressGeneric = suppressGeneric,
                activeRuleSetIds = activeRuleSetIds,
            )
        val exceptionSelectors =
            candidates
                .asSequence()
                .filter { rule -> rule.exception && rule.matches(pageHost) }
                .map(CompiledBrowserAdBlockElementRule::selector)
                .toSet()
        return candidates
            .asSequence()
            .filter { rule ->
                !rule.exception &&
                    rule.selector !in exceptionSelectors &&
                    rule.matches(pageHost)
            }
            .map(CompiledBrowserAdBlockElementRule::decision)
            .distinctBy(BrowserAdBlockElementDecision::selector)
            .toList()
    }

    private fun candidates(
        pageHost: String,
        suppressGeneric: Boolean,
        activeRuleSetIds: Set<String>,
    ): List<CompiledBrowserAdBlockElementRule> {
        val candidates = LinkedHashSet<CompiledBrowserAdBlockElementRule>()
        partitions.forEach { partition ->
            if (!suppressGeneric) {
                partition.genericRules
                    .asSequence()
                    .filter { rule -> rule.ruleSetId in activeRuleSetIds }
                    .forEach(candidates::add)
            }
            browserAdBlockDomainSuffixes(pageHost).forEach { suffix ->
                partition.domainRules[suffix]
                    ?.asSequence()
                    ?.filter { rule -> rule.ruleSetId in activeRuleSetIds }
                    ?.forEach(candidates::add)
            }
        }
        return candidates.toList()
    }

    companion object {
        fun fromCompiled(
            compiledRules: List<CompiledBrowserAdBlockElementRule>,
        ): BrowserAdBlockElementIndex =
            BrowserAdBlockElementIndex(
                partitions = listOf(BrowserAdBlockElementPartition(compiledRules)),
            )

        fun combine(
            indexes: Collection<BrowserAdBlockElementIndex>,
        ): BrowserAdBlockElementIndex =
            BrowserAdBlockElementIndex(
                partitions = indexes.flatMap(BrowserAdBlockElementIndex::partitions),
            )
    }
}

private fun explicitHostForBrowserAdBlockNetworkRule(rule: String): String? {
    val raw =
        splitBrowserAdBlockPatternAndOptions(rule.trim().removePrefix("@@"))
            .pattern
            .trim()
    val candidate =
        raw
            .removePrefix("|")
            .removeSuffix("|")
            .substringBefore('*')
            .substringBefore('^')
            .trim()
    if (
        !candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }
    return runCatching { URI(candidate).host }
        .getOrNull()
        ?.lowercase(Locale.ROOT)
        ?.trim('.')
        ?.takeIf(String::isNotBlank)
}

internal data class CompiledBrowserAdBlockElementRule(
    val ruleSetId: String,
    val spec: BrowserAdBlockElementRuleSpec,
    val selector: String,
    val exception: Boolean,
    val domainIncludes: Set<String>,
    val domainExcludes: Set<String>,
    val generic: Boolean,
) {
    val decision =
        BrowserAdBlockElementDecision(
            selector = selector,
            ruleId = spec.id,
            source = spec.source,
            sourceName = spec.sourceName,
        )

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
    ruleSetId: String = BROWSER_AD_BLOCK_DEFAULT_RULE_SET_ID,
): CompiledBrowserAdBlockNetworkRule? {
    val raw = spec.rule.trim()
    if (
        raw.isBlank() ||
            raw.length > BROWSER_AD_BLOCK_MAX_NETWORK_RULE_LENGTH ||
            raw.startsWith("!") ||
            raw.contains("##") ||
            raw.contains("#@#") ||
            containsUnsupportedBrowserAdBlockCosmeticSyntax(raw)
    ) {
        return null
    }
    val exception = raw.startsWith("@@")
    val withoutException = if (exception) raw.removePrefix("@@") else raw
    val patternAndOptions = splitBrowserAdBlockPatternAndOptions(withoutException)
    val pattern = patternAndOptions.pattern.trim()
    val options =
        parseBrowserAdBlockNetworkOptions(
            rawOptions = patternAndOptions.options,
            exception = exception,
        ) ?: return null
    if (
        pattern.isBlank() &&
            options.domainIncludes.isEmpty() &&
            options.resourceIncludes.isEmpty()
    ) {
        return null
    }

    fun compiled(
        hostAnchor: String?,
        indexKey: String?,
        literalPattern: String? = null,
        wildcardPattern: String? = null,
        patternRegex: Regex? = null,
        matchAll: Boolean = false,
    ): CompiledBrowserAdBlockNetworkRule =
        CompiledBrowserAdBlockNetworkRule(
            ruleSetId = ruleSetId,
            spec = spec,
            exception = exception,
            hostAnchor = hostAnchor,
            domainIncludes = options.domainIncludes,
            domainExcludes = options.domainExcludes,
            resourceIncludes = options.resourceIncludes,
            resourceExcludes = options.resourceExcludes,
            thirdParty = options.thirdParty,
            denyAllowDomains = options.denyAllowDomains,
            matchCase = options.matchCase,
            important = options.important,
            generic = options.domainIncludes.isEmpty(),
            pagePolicy = options.pagePolicy,
            indexKey = indexKey,
            literalPattern = literalPattern,
            wildcardPattern = wildcardPattern,
            patternRegex = patternRegex,
            matchAll = matchAll,
        )

    if (
        pattern.startsWith("/") &&
            pattern.endsWith("/") &&
            pattern.length > 2
    ) {
        val regex =
            runCatching {
                Regex(
                    pattern.substring(1, pattern.length - 1),
                    if (options.matchCase) {
                        emptySet()
                    } else {
                        setOf(RegexOption.IGNORE_CASE)
                    },
                )
            }.getOrNull() ?: return null
        return compiled(
            hostAnchor = null,
            indexKey = browserAdBlockIndexKey(longestBrowserAdBlockLiteralToken(pattern)),
            patternRegex = regex,
        )
    }

    if (pattern.startsWith("||")) {
        val rawHost =
            pattern
                .removePrefix("||")
                .takeWhile { character ->
                    character.isLetterOrDigit() || character == '.' || character == '-'
                }
        val normalizedHost = normalizeBrowserAdBlockDomainInput(rawHost) ?: return null
        val suffixPattern = pattern.removePrefix("||").removePrefix(rawHost)
        return compiled(
            hostAnchor = normalizedHost,
            indexKey = null,
            wildcardPattern =
                suffixPattern
                    .takeIf(String::isNotBlank)
                    ?.let { value ->
                        if (options.matchCase) value else value.lowercase(Locale.ROOT)
                    },
            matchAll = suffixPattern.isBlank(),
        )
    }

    if (pattern.isBlank()) {
        return compiled(
            hostAnchor = null,
            indexKey = null,
            matchAll = true,
        )
    }

    val hasSpecialSyntax =
        pattern.contains('*') ||
            pattern.contains('^') ||
            pattern.startsWith('|') ||
            pattern.endsWith('|')
    if (!hasSpecialSyntax) {
        val literal =
            if (options.matchCase) {
                pattern
            } else {
                pattern.lowercase(Locale.ROOT)
            }
        return compiled(
            hostAnchor = null,
            indexKey = browserAdBlockIndexKey(literal),
            literalPattern = literal,
        )
    }

    val regex =
        pattern
            .takeIf(String::isNotBlank)
            ?.let { value ->
                if (options.matchCase) value else value.lowercase(Locale.ROOT)
            } ?: return null
    val literalToken = longestBrowserAdBlockLiteralToken(pattern)
    return compiled(
        hostAnchor = null,
        indexKey = browserAdBlockIndexKey(literalToken),
        wildcardPattern = regex,
    )
}

private fun browserAdBlockWildcardMatches(
    candidate: String,
    pattern: String,
): Boolean {
    val anchoredAtStart = pattern.startsWith("|")
    val anchoredAtEnd = pattern.endsWith("|") && pattern.length > 1
    val body =
        pattern
            .removePrefix("|")
            .let { value -> if (anchoredAtEnd) value.dropLast(1) else value }
    if (body.isBlank()) {
        return false
    }

    fun matchesFrom(startIndex: Int): Boolean {
        var candidateIndex = startIndex
        var patternIndex = 0
        var wildcardPatternIndex = -1
        var wildcardCandidateIndex = -1
        while (candidateIndex < candidate.length) {
            if (patternIndex >= body.length) {
                return !anchoredAtEnd
            }
            when (val token = body[patternIndex]) {
                '*' -> {
                    wildcardPatternIndex = patternIndex
                    wildcardCandidateIndex = candidateIndex
                    patternIndex += 1
                }
                '^' -> {
                    if (isBrowserAdBlockSeparator(candidate[candidateIndex])) {
                        patternIndex += 1
                        candidateIndex += 1
                    } else if (wildcardPatternIndex >= 0) {
                        wildcardCandidateIndex += 1
                        candidateIndex = wildcardCandidateIndex
                        patternIndex = wildcardPatternIndex + 1
                    } else {
                        return false
                    }
                }
                else -> {
                    if (candidate[candidateIndex] == token) {
                        patternIndex += 1
                        candidateIndex += 1
                    } else if (wildcardPatternIndex >= 0) {
                        wildcardCandidateIndex += 1
                        candidateIndex = wildcardCandidateIndex
                        patternIndex = wildcardPatternIndex + 1
                    } else {
                        return false
                    }
                }
            }
        }
        while (patternIndex < body.length && body[patternIndex] == '*') {
            patternIndex += 1
        }
        while (patternIndex < body.length && body[patternIndex] == '^') {
            patternIndex += 1
        }
        return patternIndex == body.length && (!anchoredAtEnd || candidateIndex == candidate.length)
    }

    return if (anchoredAtStart) {
        matchesFrom(0)
    } else {
        (0..candidate.length).any(::matchesFrom)
    }
}

private fun isBrowserAdBlockSeparator(character: Char): Boolean =
    !character.isLetterOrDigit() &&
        character != '_' &&
        character != '.' &&
        character != '%' &&
        character != '-'

private data class BrowserAdBlockPatternAndOptions(
    val pattern: String,
    val options: String,
)

private data class BrowserAdBlockNetworkOptions(
    val domainIncludes: Set<String> = emptySet(),
    val domainExcludes: Set<String> = emptySet(),
    val resourceIncludes: Set<BrowserAdBlockResourceType> = emptySet(),
    val resourceExcludes: Set<BrowserAdBlockResourceType> = emptySet(),
    val thirdParty: Boolean? = null,
    val denyAllowDomains: Set<String> = emptySet(),
    val matchCase: Boolean = false,
    val important: Boolean = false,
    val pagePolicy: BrowserAdBlockPagePolicy = BrowserAdBlockPagePolicy(),
)

private fun splitBrowserAdBlockPatternAndOptions(
    raw: String,
): BrowserAdBlockPatternAndOptions {
    if (raw.startsWith("/")) {
        val closingSlash = raw.lastIndexOf('/')
        if (
            closingSlash > 0 &&
                closingSlash + 1 < raw.length &&
                raw[closingSlash + 1] == '$'
        ) {
            return BrowserAdBlockPatternAndOptions(
                pattern = raw.substring(0, closingSlash + 1),
                options = raw.substring(closingSlash + 2),
            )
        }
    }
    val separator = raw.indexOf('$')
    return if (separator < 0) {
        BrowserAdBlockPatternAndOptions(pattern = raw, options = "")
    } else {
        BrowserAdBlockPatternAndOptions(
            pattern = raw.substring(0, separator),
            options = raw.substring(separator + 1),
        )
    }
}

private fun parseBrowserAdBlockNetworkOptions(
    rawOptions: String,
    exception: Boolean,
): BrowserAdBlockNetworkOptions? {
    if (rawOptions.isBlank()) {
        return BrowserAdBlockNetworkOptions()
    }
    val domainIncludes = mutableSetOf<String>()
    val domainExcludes = mutableSetOf<String>()
    val resourceIncludes = mutableSetOf<BrowserAdBlockResourceType>()
    val resourceExcludes = mutableSetOf<BrowserAdBlockResourceType>()
    val denyAllowDomains = mutableSetOf<String>()
    var thirdParty: Boolean? = null
    var matchCase = false
    var important = false
    var pagePolicy = BrowserAdBlockPagePolicy()

    rawOptions.split(',').forEach { rawOption ->
        val normalizedOption = rawOption.trim()
        if (normalizedOption.isBlank()) {
            return@forEach
        }
        val excluded = normalizedOption.startsWith('~')
        val option = normalizedOption.removePrefix("~")
        val name = option.substringBefore('=').lowercase(Locale.ROOT)
        val value = option.substringAfter('=', "")
        when (name) {
            "domain", "from" -> {
                if (excluded || value.isBlank()) {
                    return null
                }
                for (rawDomain in value.split('|')) {
                    val domainToken = rawDomain.trim()
                    if (domainToken.isBlank()) {
                        return null
                    }
                    val domainExcluded = domainToken.startsWith('~')
                    val domain =
                        normalizeBrowserAdBlockDomainInput(domainToken.removePrefix("~"))
                            ?: return null
                    if (domainExcluded) {
                        domainExcludes += domain
                    } else {
                        domainIncludes += domain
                    }
                }
            }
            "denyallow" -> {
                if (excluded || value.isBlank()) {
                    return null
                }
                for (rawDomain in value.split('|')) {
                    val domainToken = rawDomain.trim()
                    val domain =
                        normalizeBrowserAdBlockDomainInput(domainToken)
                            ?: return null
                    denyAllowDomains += domain
                }
            }
            "third-party", "3p" -> {
                val requestedValue = !excluded
                if (thirdParty != null && thirdParty != requestedValue) {
                    return null
                }
                thirdParty = requestedValue
            }
            "first-party", "1p" -> {
                val requestedValue = excluded
                if (thirdParty != null && thirdParty != requestedValue) {
                    return null
                }
                thirdParty = requestedValue
            }
            "match-case" -> {
                if (excluded) return null
                matchCase = true
            }
            "important" -> {
                if (excluded) return null
                important = true
            }
            "document" -> {
                (if (excluded) resourceExcludes else resourceIncludes) +=
                    BrowserAdBlockResourceType.DOCUMENT
                if (exception && !excluded) {
                    pagePolicy = pagePolicy.copy(document = true)
                }
            }
            "elemhide", "shide" -> {
                if (!exception || excluded) return null
                pagePolicy = pagePolicy.copy(elementHide = true)
            }
            "generichide", "ghide" -> {
                if (!exception || excluded) return null
                pagePolicy = pagePolicy.copy(genericHide = true)
            }
            "genericblock" -> {
                if (!exception || excluded) return null
                pagePolicy = pagePolicy.copy(genericBlock = true)
            }
            "all", "network", "badfilter" -> {
                if (excluded) return null
            }
            else -> {
                val resourceType = browserAdBlockResourceTypeForOption(name)
                    ?: return null
                (if (excluded) resourceExcludes else resourceIncludes) += resourceType
            }
        }
    }
    return BrowserAdBlockNetworkOptions(
        domainIncludes = domainIncludes,
        domainExcludes = domainExcludes,
        resourceIncludes = resourceIncludes,
        resourceExcludes = resourceExcludes,
        thirdParty = thirdParty,
        denyAllowDomains = denyAllowDomains,
        matchCase = matchCase,
        important = important,
        pagePolicy = pagePolicy,
    )
}

private fun browserAdBlockResourceTypeForOption(
    option: String,
): BrowserAdBlockResourceType? =
    when (option) {
        "script" -> BrowserAdBlockResourceType.SCRIPT
        "stylesheet", "css" -> BrowserAdBlockResourceType.STYLESHEET
        "image" -> BrowserAdBlockResourceType.IMAGE
        "media" -> BrowserAdBlockResourceType.MEDIA
        "font" -> BrowserAdBlockResourceType.FONT
        "object", "object-subrequest" -> BrowserAdBlockResourceType.OBJECT
        "xmlhttprequest", "xhr" -> BrowserAdBlockResourceType.XMLHTTPREQUEST
        "subdocument" -> BrowserAdBlockResourceType.SUBDOCUMENT
        "websocket" -> BrowserAdBlockResourceType.WEBSOCKET
        "ping" -> BrowserAdBlockResourceType.PING
        "other" -> BrowserAdBlockResourceType.OTHER
        else -> null
    }

private fun browserAdBlockRuleHasOption(
    rule: String,
    optionName: String,
): Boolean {
    val withoutException = rule.trim().removePrefix("@@")
    val options = splitBrowserAdBlockPatternAndOptions(withoutException).options
    return options.split(',').any { rawOption ->
        val normalizedOption = rawOption.trim()
        !normalizedOption.startsWith('~') &&
            normalizedOption
                .substringBefore('=')
                .equals(optionName, ignoreCase = true)
    }
}

private fun browserAdBlockRuleWithoutOption(
    rule: String,
    optionName: String,
): String? {
    val trimmed = rule.trim()
    val exceptionPrefix = if (trimmed.startsWith("@@")) "@@" else ""
    val patternAndOptions =
        splitBrowserAdBlockPatternAndOptions(trimmed.removePrefix("@@"))
    val remainingOptions =
        patternAndOptions.options
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { rawOption ->
                !rawOption.startsWith('~') &&
                    rawOption
                        .substringBefore('=')
                        .equals(optionName, ignoreCase = true)
            }
    if (remainingOptions.size == patternAndOptions.options.split(',').count(String::isNotBlank)) {
        return null
    }
    return buildString {
        append(exceptionPrefix)
        append(patternAndOptions.pattern)
        if (remainingOptions.isNotEmpty()) {
            append('$')
            append(remainingOptions.joinToString(","))
        }
    }
}

private fun browserAdBlockCanonicalRuleKey(rule: String): String =
    rule
        .trim()
        .replace(Regex("\\s+"), "")
        .lowercase(Locale.ROOT)

private fun isThirdPartyBrowserAdBlockRequest(
    pageUrl: String,
    requestUrl: String,
): Boolean {
    val pageHost = normalizeBrowserAdBlockDomain(pageUrl)
    val requestHost = normalizeBrowserAdBlockDomain(requestUrl)
    if (pageHost.isBlank() || requestHost.isBlank()) {
        return false
    }
    val pageSite = browserAdBlockTopPrivateDomain(pageUrl, pageHost)
    val requestSite = browserAdBlockTopPrivateDomain(requestUrl, requestHost)
    return pageSite != requestSite
}

private fun browserAdBlockTopPrivateDomain(
    url: String,
    host: String,
): String =
    runCatching {
        url.toHttpUrlOrNull()?.topPrivateDomain()
            ?: "https://$host/".toHttpUrlOrNull()?.topPrivateDomain()
    }.getOrNull()
        ?.lowercase(Locale.ROOT)
        ?.trim('.')
        ?.takeIf(String::isNotBlank)
        ?: host

private fun Map<String, String>.browserAdBlockHeader(name: String): String =
    entries.firstOrNull { entry -> entry.key.equals(name, ignoreCase = true) }?.value.orEmpty()

private fun compileBrowserAdBlockElementRule(
    spec: BrowserAdBlockElementRuleSpec,
    ruleSetId: String = BROWSER_AD_BLOCK_DEFAULT_RULE_SET_ID,
): CompiledBrowserAdBlockElementRule? {
    if (!isValidBrowserAdBlockSelector(spec.selector)) {
        return null
    }
    val includes = mutableSetOf<String>()
    val excludes = mutableSetOf<String>()
    if (spec.domainExpression.isNotBlank()) {
        for (rawDomain in spec.domainExpression.split(',')) {
            val domainToken = rawDomain.trim()
            if (domainToken.isBlank()) {
                return null
            }
            val excluded = domainToken.startsWith('~')
            val normalized =
                normalizeBrowserAdBlockDomainInput(domainToken.removePrefix("~"))
                    ?: return null
            if (excluded) {
                excludes += normalized
            } else {
                includes += normalized
            }
        }
    }
    return CompiledBrowserAdBlockElementRule(
        ruleSetId = ruleSetId,
        spec = spec,
        selector = spec.selector.trim(),
        exception = spec.exception,
        domainIncludes = includes,
        domainExcludes = excludes,
        generic = includes.isEmpty(),
    )
}

private fun longestBrowserAdBlockLiteralToken(pattern: String): String? =
    pattern
        .lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9_%.-]+"))
        .maxByOrNull(String::length)
        ?.takeIf(String::isNotBlank)

private fun browserAdBlockIndexKey(token: String?): String? =
    token
        ?.lowercase(Locale.ROOT)
        ?.takeIf { value -> value.length >= BROWSER_AD_BLOCK_MIN_INDEX_KEY_LENGTH }
        ?.take(BROWSER_AD_BLOCK_MAX_INDEX_KEY_LENGTH)

private fun browserAdBlockTokenHash(value: String): Long {
    var hash = 0L
    value.forEach { character ->
        hash = hash * BROWSER_AD_BLOCK_TOKEN_HASH_BASE + character.code
    }
    return hash
}

private fun browserAdBlockTokenHashes(
    value: String,
    length: Int,
): Sequence<Long> = sequence {
    if (length <= 0 || value.length < length) {
        return@sequence
    }
    var highestPower = 1L
    repeat(length - 1) {
        highestPower *= BROWSER_AD_BLOCK_TOKEN_HASH_BASE
    }
    var hash = 0L
    for (index in 0 until length) {
        hash = hash * BROWSER_AD_BLOCK_TOKEN_HASH_BASE + value[index].code
    }
    yield(hash)
    for (start in 1..value.length - length) {
        hash =
            (hash - value[start - 1].code * highestPower) *
                BROWSER_AD_BLOCK_TOKEN_HASH_BASE +
                value[start + length - 1].code
        yield(hash)
    }
}

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
): Boolean = browserAdBlockDomainSuffixes(host).any(domains::contains)

private fun browserAdBlockDomainMatches(
    host: String,
    domain: String,
): Boolean = host == domain || host.endsWith(".$domain")

private val BrowserAdBlockUnsupportedCosmeticSeparators =
    listOf("#?#", "#$#", "#%#", "#^#", "#*#", "#@$#", "#@?#", "#@%#", "#@^#")

private val BrowserAdBlockScriptExtensions =
    setOf("js", "mjs", "cjs")

private val BrowserAdBlockImageExtensions =
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "avif", "heic")

private val BrowserAdBlockMediaExtensions =
    setOf("m3u8", "mpd", "mp4", "m4v", "mkv", "webm", "flv", "mov", "avi", "ts", "mp3", "aac", "m4a", "flac", "wav", "ogg", "opus")

private val BrowserAdBlockFontExtensions =
    setOf("woff", "woff2", "ttf", "otf", "eot")

private val BrowserAdBlockDocumentExtensions =
    setOf("html", "htm", "xhtml")

private val BrowserAdBlockDataExtensions =
    setOf("json", "xml")

private const val BROWSER_AD_BLOCK_DEFAULT_RULE_SET_ID = "default"
private const val BROWSER_AD_BLOCK_MIN_INDEX_KEY_LENGTH = 4
private const val BROWSER_AD_BLOCK_MAX_INDEX_KEY_LENGTH = 8
private const val BROWSER_AD_BLOCK_TOKEN_HASH_BASE = 257L
private const val BROWSER_AD_BLOCK_PAGE_CACHE_SIZE = 64
private const val BROWSER_AD_BLOCK_ELEMENT_PAGE_CACHE_SIZE = 16
private const val BROWSER_AD_BLOCK_SITE_CACHE_SIZE = 128
private const val BROWSER_AD_BLOCK_MAX_NETWORK_RULE_LENGTH = 65_536
private const val BROWSER_AD_BLOCK_MAX_SELECTOR_LENGTH = 2_048
