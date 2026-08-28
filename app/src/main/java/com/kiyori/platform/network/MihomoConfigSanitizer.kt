package com.kiyori.platform.network

import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle

private val EXTERNAL_DATA_RULE_TYPES =
    setOf(
        "GEOSITE",
        "GEOIP",
        "SRC-GEOIP",
        "IP-ASN",
        "SRC-IP-ASN",
        "RULE-SET",
    )

private val NESTED_EXTERNAL_DATA_RULE_PATTERN =
    Regex("(?i)(?:^|[,(])\\s*(?:GEOSITE|GEOIP|SRC-GEOIP|IP-ASN|SRC-IP-ASN|RULE-SET)\\s*,")

internal fun isMihomoRuleExternalDataDependent(
    type: KiyoriNetworkRuleType,
    pattern: String,
): Boolean =
    isMihomoRuleExternalDataDependent(type.wireName, pattern)

private fun isMihomoRuleExternalDataDependent(
    type: String,
    expression: String,
): Boolean =
    type.uppercase() in EXTERNAL_DATA_RULE_TYPES ||
        expression.contains(NESTED_EXTERNAL_DATA_RULE_PATTERN)

data class SanitizedMihomoSubscription(
    val yaml: String,
    val summary: MihomoSubscriptionSummary,
    val usage: MihomoSubscriptionUsage? = null,
    val rules: List<String> = emptyList(),
)

data class MihomoRuntimeConfig(
    val yaml: String,
    val groupSummaries: List<MihomoProxyGroupSummary>,
)

object MihomoConfigSanitizer {
    const val ROUTE_GROUP_NAME = "KIYORI_APP_PROXY"
    const val MAX_YAML_BYTES = 4 * 1024 * 1024

    private val loadSettings =
        LoadSettings.builder()
            .setLabel("Kiyori Mihomo subscription")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(32)
            .setCodePointLimit(MAX_YAML_BYTES)
            .build()
    private val dumpSettings =
        DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .setIndicatorIndent(0)
            .build()

    fun sanitize(
        rawYaml: String,
        rootErrorCode: KiyoriNetworkErrorCode = KiyoriNetworkErrorCode.CONFIG_INVALID,
    ): SanitizedMihomoSubscription {
        requireYamlSize(rawYaml)
        val root = loadSingleRoot(rawYaml, rootErrorCode)
        val proxyResult = sanitizeProxies(root["proxies"])
        val providers = sanitizeProviders(root["proxy-providers"])
        val groups =
            sanitizeGroups(
                raw = root["proxy-groups"],
                proxyNames = proxyResult.accepted.mapTo(linkedSetOf(), NamedMapping::name),
                providerNames = providers.mapTo(linkedSetOf(), NamedMapping::name),
            )
        if (proxyResult.accepted.isEmpty() && providers.isEmpty()) {
            invalid("The subscription contains no usable outbound proxies or HTTP proxy providers.")
        }

        val rootCandidates =
            buildList {
                addAll(groups.map(NamedGroup::name))
                addAll(proxyResult.accepted.map(NamedMapping::name))
            }.distinct()
        if (rootCandidates.isEmpty() && providers.isEmpty()) {
            invalid("The subscription contains no usable root route candidates.")
        }
        val ruleProviders = sanitizeRuleProviders(root["rule-providers"])
        val ruleTargets =
            (groups.map(NamedGroup::name) +
                    proxyResult.accepted.map(NamedMapping::name) +
                    providers.map(NamedMapping::name) +
                    ruleProviders.keys +
                    BUILTIN_OUTBOUNDS + ROUTE_GROUP_NAME)
                .toSet()
        val subRules =
            sanitizeSubRules(
                raw = root["sub-rules"],
                validTargets = ruleTargets,
                ruleProviderNames = ruleProviders.keys,
            )
        val rules =
            sanitizeRules(
                raw = root["rules"],
                validTargets = ruleTargets,
                ruleProviderNames = ruleProviders.keys,
                subRuleNames = subRules.keys,
            )

        val sanitized = linkedMapOf<String, Any?>()
        if (proxyResult.accepted.isNotEmpty()) {
            sanitized["proxies"] = proxyResult.accepted.map(NamedMapping::mapping)
        }
        if (providers.isNotEmpty()) {
            sanitized["proxy-providers"] = providers.associate { it.name to it.mapping }
        }
        if (subRules.isNotEmpty()) {
            sanitized["sub-rules"] = subRules.mapValues { (_, result) -> result.accepted }
        }
        if (groups.isNotEmpty()) sanitized["proxy-groups"] = groups.map(NamedGroup::mapping)
        sanitizeDns(root["dns"])?.let { sanitized["dns"] = it }
        if (rules.accepted.isNotEmpty()) sanitized["rules"] = rules.accepted

        val groupSummaries = groups.map(NamedGroup::summary)
        return SanitizedMihomoSubscription(
            yaml = dump(sanitized),
            summary =
                MihomoSubscriptionSummary(
                    proxyCount = proxyResult.accepted.size,
                    providerCount = providers.size,
                    groupCount = groups.size,
                    isolatedProxyCount = proxyResult.isolatedCount,
                    insecureProviderCount =
                        providers.count { provider ->
                            provider.mapping["url"]
                                ?.toString()
                                ?.startsWith("http://", ignoreCase = true) == true
                        },
                    staticProxies =
                        proxyResult.accepted.map { proxy ->
                            MihomoProxySummary(
                                name = proxy.name,
                                type = proxy.mapping["type"].toString(),
                            )
                        },
                    providerNames = providers.map(NamedMapping::name),
                    groups = groupSummaries,
                    rootCandidates = rootCandidates.ifEmpty { providers.map(NamedMapping::name) },
                    ruleCount = rules.accepted.size,
                    unsupportedRuleCount = rules.unsupportedCount + subRules.values.sumOf { it.unsupportedCount },
                ),
            rules = rules.accepted,
        )
    }

    fun decodeUtf8(
        bytes: ByteArray,
        errorCode: KiyoriNetworkErrorCode,
    ): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            throw KiyoriNetworkException(
                errorCode,
                "The Mihomo YAML must be valid UTF-8.",
                error,
            )
        }

    fun buildRuntimeConfig(
        sanitizedYaml: String,
        mixedPort: Int,
        controllerPort: Int,
        controllerSecret: String,
        testUrl: String,
        routingMode: KiyoriNetworkConnectionMode = KiyoriNetworkConnectionMode.GLOBAL,
        customRules: List<KiyoriNetworkProxyRule> = emptyList(),
    ): MihomoRuntimeConfig {
        require(mixedPort in 1..65535) { "Invalid Mihomo mixed port" }
        require(controllerPort in 1..65535) { "Invalid Mihomo controller port" }
        require(controllerSecret.isNotBlank()) { "Mihomo controller secret is required" }
        val safeTestUrl = requirePublicHttpUrl(testUrl, "test URL")

        val root = loadSingleRoot(sanitizedYaml, KiyoriNetworkErrorCode.CONFIG_INVALID)
        val proxyResult = sanitizeProxies(root["proxies"])
        val providers = sanitizeProviders(root["proxy-providers"])
        val groups =
            sanitizeGroups(
                raw = root["proxy-groups"],
                proxyNames = proxyResult.accepted.mapTo(linkedSetOf(), NamedMapping::name),
                providerNames = providers.mapTo(linkedSetOf(), NamedMapping::name),
                testUrl = safeTestUrl,
            )
        val rootCandidates =
            buildList {
                addAll(groups.map(NamedGroup::name))
                addAll(proxyResult.accepted.map(NamedMapping::name))
            }.distinct()
        if (rootCandidates.isEmpty() && providers.isEmpty()) {
            invalid("The sanitized subscription has no usable outbound source.")
        }
        val ruleProviders = sanitizeRuleProviders(root["rule-providers"])
        val ruleTargets =
            (groups.map(NamedGroup::name) +
                    proxyResult.accepted.map(NamedMapping::name) +
                    providers.map(NamedMapping::name) +
                    ruleProviders.keys +
                    BUILTIN_OUTBOUNDS + ROUTE_GROUP_NAME)
                .toSet()
        val subRules =
            sanitizeSubRules(
                raw = root["sub-rules"],
                validTargets = ruleTargets,
                ruleProviderNames = ruleProviders.keys,
            )
        val subscriptionRules =
            sanitizeRules(
                raw = root["rules"],
                validTargets = ruleTargets,
                ruleProviderNames = ruleProviders.keys,
                subRuleNames = subRules.keys,
            ).accepted

        customRules
            .asSequence()
            .filter(KiyoriNetworkProxyRule::enabled)
            .firstOrNull { rule -> isMihomoRuleExternalDataDependent(rule.type, rule.pattern) }
            ?.let { rule ->
                invalid(
                    "The enabled custom rule ${rule.type.wireName} requires external GeoIP, GeoSite, ASN, or rule-provider data.",
                )
            }

        val runtime =
            linkedMapOf<String, Any?>(
                "mixed-port" to mixedPort,
                "allow-lan" to false,
                "bind-address" to "127.0.0.1",
                "mode" to "rule",
                "log-level" to "warning",
                "external-controller" to "127.0.0.1:$controllerPort",
                "secret" to controllerSecret,
                "find-process-mode" to "off",
                "geo-auto-update" to false,
            )
        if (proxyResult.accepted.isNotEmpty()) {
            runtime["proxies"] = proxyResult.accepted.map(NamedMapping::mapping)
        }
        if (providers.isNotEmpty()) {
            runtime["proxy-providers"] = providers.associate { it.name to it.mapping }
        }
        if (subRules.isNotEmpty()) runtime["sub-rules"] = subRules.mapValues { it.value.accepted }
        sanitizeDns(root["dns"])?.let { runtime["dns"] = it }

        val routeGroup =
            linkedMapOf<String, Any?>(
                "name" to ROUTE_GROUP_NAME,
                "type" to "select",
            )
        if (rootCandidates.isNotEmpty()) routeGroup["proxies"] = rootCandidates
        if (rootCandidates.isEmpty() && providers.isNotEmpty()) {
            routeGroup["use"] = providers.map(NamedMapping::name)
        }
        runtime["proxy-groups"] = listOf(routeGroup) + groups.map(NamedGroup::mapping)
        runtime["rules"] =
            when (routingMode) {
                KiyoriNetworkConnectionMode.RULE ->
                    buildList {
                        addAll(
                            customRules
                                .filter(KiyoriNetworkProxyRule::enabled)
                                .sortedWith(
                                    compareBy<KiyoriNetworkProxyRule> { ruleSpecificity(it.type) }
                                        .thenBy { it.createdAtEpochMillis },
                                ).map(::toMihomoRule),
                        )
                        addAll(subscriptionRules)
                        add("MATCH,$ROUTE_GROUP_NAME")
                    }
                KiyoriNetworkConnectionMode.GLOBAL,
                KiyoriNetworkConnectionMode.PROXY,
                KiyoriNetworkConnectionMode.DIRECT,
                -> listOf("MATCH,$ROUTE_GROUP_NAME")
            }

        val rootSummary =
            MihomoProxyGroupSummary(
                name = ROUTE_GROUP_NAME,
                type = "select",
                options = rootCandidates,
                providerNames =
                    if (rootCandidates.isEmpty()) providers.map(NamedMapping::name) else emptyList(),
                manuallySelectable = true,
            )
        return MihomoRuntimeConfig(
            yaml = dump(runtime),
            groupSummaries = listOf(rootSummary) + groups.map(NamedGroup::summary),
        )
    }

    fun replaceSubscriptionRule(
        sanitizedYaml: String,
        ruleIndex: Int,
        rawRule: String,
    ): SanitizedMihomoSubscription {
        val root = loadSingleRoot(sanitizedYaml, KiyoriNetworkErrorCode.CONFIG_INVALID)
        val existingRules =
            (root["rules"] as? List<*>)
                ?.mapIndexed { index, value ->
                    value as? String ?: invalid("Subscription rule ${index + 1} must be a string.")
                }
                ?: invalid("The selected subscription has no editable rules.")
        if (ruleIndex !in existingRules.indices) {
            invalid("The selected subscription rule no longer exists.")
        }
        val normalizedRule = rawRule.trim()
        if (normalizedRule.isBlank() || normalizedRule.any(Char::isISOControl)) {
            invalid("A subscription rule must not be blank or contain control characters.")
        }
        val updatedRules = existingRules.toMutableList().apply { this[ruleIndex] = normalizedRule }
        if (updatedRules.toSet().size != updatedRules.size) {
            invalid("Duplicate subscription rules are not allowed.")
        }
        val updatedRoot = linkedMapOf<String, Any?>().apply {
            putAll(root)
            put("rules", updatedRules)
        }
        val sanitized = sanitize(dump(updatedRoot))
        if (sanitized.rules.size != updatedRules.size) {
            invalid("The subscription rule uses an unsupported type, matcher, target, or dependency.")
        }
        return sanitized
    }

    private data class NamedMapping(
        val name: String,
        val mapping: Map<String, Any?>,
    )

    private data class ProxySanitizeResult(
        val accepted: List<NamedMapping>,
        val isolatedCount: Int,
    )

    private data class RuleSanitizeResult(
        val accepted: List<String>,
        val unsupportedCount: Int,
    )

    private data class SubRuleSanitizeResult(
        val accepted: List<String>,
        val unsupportedCount: Int,
    )

    private data class GroupDraft(
        val name: String,
        val type: String,
        val source: Map<String, Any?>,
        val rawOptions: List<String>,
        val rawProviders: List<String>,
        val includeAll: Boolean,
        val includeAllProxies: Boolean,
        val includeAllProviders: Boolean,
    )

    private data class NamedGroup(
        val name: String,
        val mapping: Map<String, Any?>,
        val summary: MihomoProxyGroupSummary,
    )

    private fun sanitizeProxies(raw: Any?): ProxySanitizeResult {
        if (raw == null) return ProxySanitizeResult(emptyList(), 0)
        val entries = raw as? List<*> ?: invalid("The proxies field must be a YAML list.")
        val seen = linkedSetOf<String>()
        val accepted = mutableListOf<NamedMapping>()
        var isolatedCount = 0
        entries.forEachIndexed { index, entry ->
            val mapping = stringKeyMap(entry, "proxy ${index + 1}")
            val name = mapping["name"]?.toString()?.trim().orEmpty()
            if (name.isEmpty()) invalid("Proxy ${index + 1} has no name.")
            if (name == ROUTE_GROUP_NAME || name in BUILTIN_OUTBOUNDS || !seen.add(name)) {
                invalid("Proxy name is reserved or duplicated: $name")
            }
            val type = mapping["type"]?.toString()?.trim().orEmpty()
            if (type.isEmpty()) invalid("Proxy $name has no type.")
            val server = mapping["server"]?.toString()?.trim().orEmpty()
            if (server.isNotEmpty() && isForbiddenUpstreamServer(server)) {
                isolatedCount += 1
            } else {
                accepted += NamedMapping(name, deepCopyMap(mapping))
            }
        }
        return ProxySanitizeResult(accepted, isolatedCount)
    }

    private fun sanitizeProviders(raw: Any?): List<NamedMapping> {
        if (raw == null) return emptyList()
        val providers = stringKeyMap(raw, "proxy-providers")
        val seen = linkedSetOf<String>()
        return providers.entries.map { (rawName, rawValue) ->
            val name = rawName.trim()
            if (name.isEmpty() || name == ROUTE_GROUP_NAME || !seen.add(name)) {
                invalid("Proxy provider name is blank, reserved, or duplicated.")
            }
            val source = stringKeyMap(rawValue, "proxy provider $name")
            if (source["type"]?.toString()?.trim()?.lowercase() != "http") {
                invalid("Proxy provider $name must use type http.")
            }
            val url = source["url"]?.toString()?.trim().orEmpty()
            if (!isHttpUrl(url)) invalid("Proxy provider $name must use an absolute HTTP(S) URL.")
            val mapping = deepCopyMap(source).toMutableMap()
            mapping["type"] = "http"
            mapping["url"] = url
            mapping["path"] = "providers/${sha256(name).take(24)}.yaml"
            mapping.remove("proxy")
            NamedMapping(name, mapping)
        }
    }

    private fun sanitizeRuleProviders(raw: Any?): Map<String, Map<String, Any?>> {
        if (raw == null) return emptyMap()
        val providers = stringKeyMap(raw, "rule-providers")
        val seen = linkedSetOf<String>()
        providers.entries.forEach { (rawName, rawValue) ->
            val name = rawName.trim()
            if (name.isBlank() || !seen.add(name)) {
                invalid("Rule provider name is blank or duplicated.")
            }
            val source = stringKeyMap(rawValue, "rule provider $name")
            if (source["type"]?.toString()?.trim()?.lowercase() != "http") {
                invalid("Rule provider $name must use type http.")
            }
            val url = source["url"]?.toString()?.trim().orEmpty()
            if (!isHttpUrl(url)) invalid("Rule provider $name must use an absolute HTTP(S) URL.")
        }
        // Rule providers are remote inputs. Keeping them in the runtime config makes Mihomo
        // fetch them during startup, before Kiyori has an application proxy route. Validate the
        // shape above for a useful import error, then omit the provider and any RULE-SET that
        // references it so the generated config remains self-contained.
        return emptyMap()
    }

    private fun sanitizeSubRules(
        raw: Any?,
        validTargets: Set<String>,
        ruleProviderNames: Set<String>,
    ): Map<String, SubRuleSanitizeResult> {
        if (raw == null) return emptyMap()
        val subRules = stringKeyMap(raw, "sub-rules")
        val names = subRules.keys.map(String::trim).toSet()
        if (names.any(String::isBlank)) invalid("A sub-rule name must not be blank.")
        return subRules.entries.mapNotNull { (rawName, rawRules) ->
            val name = rawName.trim()
            if (name.length > KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH) {
                invalid("The sub-rule name is too long: $name")
            }
            val result =
                sanitizeRules(
                    raw = rawRules,
                    validTargets = validTargets,
                    ruleProviderNames = ruleProviderNames,
                    subRuleNames = names,
                )
            result.takeIf { it.accepted.isNotEmpty() }?.let { sanitized ->
                name to SubRuleSanitizeResult(sanitized.accepted, sanitized.unsupportedCount)
            }
        }.toMap(linkedMapOf())
    }

    private fun sanitizeRules(
        raw: Any?,
        validTargets: Set<String>,
        ruleProviderNames: Set<String> = emptySet(),
        subRuleNames: Set<String> = emptySet(),
    ): RuleSanitizeResult {
        if (raw == null) return RuleSanitizeResult(emptyList(), 0)
        val entries = raw as? List<*> ?: invalid("The rules field must be a YAML list.")
        var unsupported = 0
        val accepted = entries.mapNotNull { entry ->
            val value = entry as? String ?: invalid("A subscription rule must be a string.")
            val normalized = value.trim()
            val parts = splitRuleParts(normalized)
            val kind = parts.firstOrNull()?.uppercase().orEmpty()
            val targetIndex =
                parts
                    .asReversed()
                    .indexOfFirst { part -> part.isNotBlank() && part.lowercase() !in RULE_OPTIONS }
                    .let { reversedIndex ->
                        if (reversedIndex < 0) -1 else parts.lastIndex - reversedIndex
                    }
            val target = parts.getOrNull(targetIndex).orEmpty()
            val matcher =
                if (targetIndex > 1) {
                    parts.subList(1, targetIndex).joinToString(",")
                } else {
                    ""
                }
            val supported =
                kind in SUPPORTED_RULE_TYPES &&
                    !isMihomoRuleExternalDataDependent(kind, matcher) &&
                    parts.size >= 2 &&
                    targetIndex >= 1 &&
                    target in validTargets &&
                    when (kind) {
                        "MATCH" -> targetIndex == 1
                        "RULE-SET" -> targetIndex > 1 && matcher in ruleProviderNames
                        "SUB-RULE" -> targetIndex > 1 && matcher in subRuleNames
                        else -> targetIndex > 1 && isRuleMatcher(matcher)
                    }
            if (!supported) {
                unsupported += 1
                null
            } else {
                parts.joinToString(",")
            }
        }.distinct()
        return RuleSanitizeResult(accepted, unsupported)
    }

    private fun isRuleMatcher(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= KiyoriNetworkProxyConfig.MAX_RULE_PATTERN_LENGTH &&
            value.none(Char::isISOControl)

    private fun splitRuleParts(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        value.forEach { character ->
            when (character) {
                '(' -> depth += 1
                ')' -> depth = (depth - 1).coerceAtLeast(0)
                ',' ->
                    if (depth == 0) {
                        parts += current.toString().trim()
                        current.clear()
                        return@forEach
                    }
            }
            current.append(character)
        }
        parts += current.toString().trim()
        return parts
    }

    private fun toMihomoRule(rule: KiyoriNetworkProxyRule): String {
        val pattern = rule.pattern.trim()
        val matcher =
            when (rule.type) {
                KiyoriNetworkRuleType.DOMAIN,
                KiyoriNetworkRuleType.DOMAIN_SUFFIX,
                KiyoriNetworkRuleType.DOMAIN_KEYWORD,
                -> pattern.lowercase().removePrefix("*.").removePrefix(".")
                else -> pattern
            }
        val kind = rule.type.wireName
        val target =
            when (rule.mode) {
                KiyoriNetworkRuleMode.DIRECT -> "DIRECT"
                KiyoriNetworkRuleMode.PROXY -> ROUTE_GROUP_NAME
            }
        return "$kind,$matcher,$target"
    }

    private fun ruleSpecificity(type: KiyoriNetworkRuleType): Int =
        when (type) {
            KiyoriNetworkRuleType.DOMAIN -> 0
            KiyoriNetworkRuleType.DOMAIN_SUFFIX -> 1
            KiyoriNetworkRuleType.DOMAIN_KEYWORD -> 2
            else -> 3
        }

    private fun sanitizeGroups(
        raw: Any?,
        proxyNames: Set<String>,
        providerNames: Set<String>,
        testUrl: String = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
    ): List<NamedGroup> {
        if (raw == null) return emptyList()
        val entries = raw as? List<*> ?: invalid("The proxy-groups field must be a YAML list.")
        val drafts = mutableListOf<GroupDraft>()
        val groupNames = linkedSetOf<String>()
        entries.forEachIndexed { index, entry ->
            val source = stringKeyMap(entry, "proxy group ${index + 1}")
            val name = source["name"]?.toString()?.trim().orEmpty()
            val type = source["type"]?.toString()?.trim()?.lowercase().orEmpty()
            if (
                name.isEmpty() ||
                    type.isEmpty() ||
                    name == ROUTE_GROUP_NAME ||
                    name in proxyNames ||
                    name in providerNames ||
                    !groupNames.add(name)
            ) {
                invalid("Proxy group ${index + 1} has a blank, reserved, colliding, or duplicate name.")
            }
            if (type !in SUPPORTED_GROUP_TYPES) return@forEachIndexed
            drafts +=
                GroupDraft(
                    name = name,
                    type = type,
                    source = source,
                    rawOptions = stringList(source["proxies"], "proxy group $name proxies"),
                    rawProviders = stringList(source["use"], "proxy group $name providers"),
                    includeAll = booleanValue(source["include-all"], "proxy group $name include-all"),
                    includeAllProxies =
                        booleanValue(
                            source["include-all-proxies"],
                            "proxy group $name include-all-proxies",
                        ),
                    includeAllProviders =
                        booleanValue(
                            source["include-all-providers"],
                            "proxy group $name include-all-providers",
                        ),
                )
        }

        val usableGroups = linkedSetOf<String>()
        var changed: Boolean
        do {
            changed = false
            drafts.forEach { draft ->
                if (draft.name in usableGroups) return@forEach
                val hasTerminalOption =
                    draft.rawOptions.any { option ->
                        option in proxyNames || option in BUILTIN_OUTBOUNDS || option in usableGroups
                    }
                val hasProvider = draft.rawProviders.any(providerNames::contains)
                val hasDynamicSource =
                    (draft.includeAll && (proxyNames.isNotEmpty() || providerNames.isNotEmpty())) ||
                        (draft.includeAllProxies && proxyNames.isNotEmpty()) ||
                        (draft.includeAllProviders && providerNames.isNotEmpty())
                if (hasTerminalOption || hasProvider || hasDynamicSource) {
                    usableGroups += draft.name
                    changed = true
                }
            }
        } while (changed)

        return drafts.mapNotNull { draft ->
            if (draft.name !in usableGroups) return@mapNotNull null
            val options =
                draft.rawOptions
                    .filter { option ->
                        option != draft.name &&
                            (option in proxyNames ||
                                option in BUILTIN_OUTBOUNDS ||
                                option in usableGroups)
                    }.distinct()
            val providers = draft.rawProviders.filter(providerNames::contains).distinct()
            if (
                options.isEmpty() &&
                    providers.isEmpty() &&
                    !draft.includeAll &&
                    !draft.includeAllProxies &&
                    !draft.includeAllProviders
            ) {
                return@mapNotNull null
            }

            val mapping = linkedMapOf<String, Any?>("name" to draft.name, "type" to draft.type)
            if (options.isNotEmpty()) mapping["proxies"] = options
            if (providers.isNotEmpty()) mapping["use"] = providers
            GROUP_SCALAR_KEYS.forEach { key ->
                draft.source[key]?.let { value ->
                    if (value is String || value is Boolean || value is Number) mapping[key] = value
                }
            }
            draft.source["empty-fallback"]?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
                    emptyFallback ->
                if (emptyFallback !in proxyNames && emptyFallback !in BUILTIN_OUTBOUNDS) {
                    invalid("Proxy group ${draft.name} has an invalid empty-fallback.")
                }
                mapping["empty-fallback"] = emptyFallback
            }
            if (draft.type != "select" && draft.type != "relay") {
                mapping["url"] = testUrl
            }
            NamedGroup(
                name = draft.name,
                mapping = mapping,
                summary =
                    MihomoProxyGroupSummary(
                        name = draft.name,
                        type = draft.type,
                        options = options,
                        providerNames = providers,
                        manuallySelectable = draft.type == "select",
                    ),
            )
        }
    }

    private fun stringList(raw: Any?, label: String): List<String> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: invalid("The $label value must be a YAML list.")
        return list.mapIndexed { index, value ->
            value as? String ?: invalid("The $label item ${index + 1} must be a string.")
        }.map(String::trim).filter(String::isNotEmpty)
    }

    private fun booleanValue(raw: Any?, label: String): Boolean =
        when (raw) {
            null -> false
            is Boolean -> raw
            else -> invalid("The $label value must be a boolean.")
        }

    private fun sanitizeDns(raw: Any?): Map<String, Any?>? {
        if (raw == null) {
            return linkedMapOf(
                "enable" to true,
                "ipv6" to false,
                "respect-rules" to false,
                "default-nameserver" to DEFAULT_DNS_SERVERS,
                "nameserver" to DEFAULT_DNS_SERVERS,
            )
        }
        val dns = deepCopyMap(stringKeyMap(raw, "dns")).toMutableMap()
        dns.remove("listen")
        // DNS resolution must not re-enter the application rule graph. In rule mode that
        // creates a resolver -> rule -> resolver cycle and surfaces as "dns resolve failed"
        // for otherwise valid DIRECT domains. Mihomo's loopback mixed-port remains the only
        // application entry point; DNS is kept as an internal resolver service.
        dns["enable"] = true
        dns["respect-rules"] = false
        dns["ipv6"] = false
        dns.remove("proxy-server-nameserver")
        sanitizeDnsFallbackFilter(dns.remove("fallback-filter"))?.let { sanitized ->
            dns["fallback-filter"] = sanitized
        }
        sanitizeDnsMatcherList(dns.remove("fake-ip-filter"), "dns fake-ip-filter")?.let {
                sanitized ->
            dns["fake-ip-filter"] = sanitized
        }
        sanitizeDnsNameserverPolicy(dns.remove("nameserver-policy"))?.let { sanitized ->
            dns["nameserver-policy"] = sanitized
        }
        return dns.takeIf(Map<String, Any?>::isNotEmpty)
    }

    private fun sanitizeDnsFallbackFilter(raw: Any?): Map<String, Any?>? {
        if (raw == null) return null
        val filter = deepCopyMap(stringKeyMap(raw, "dns fallback-filter")).toMutableMap()
        // The runtime directory intentionally contains no external GeoSite/GeoIP databases.
        // Retaining these selectors makes `mihomo -t` perform an unowned network download
        // before Kiyori has a working proxy route, so the generated configuration is not
        // self-contained and cannot start on the target device.
        // Mihomo defaults fallback-filter.geoip to true when the key is absent. An explicit
        // false is required; removing the key would still download geoip.metadb during `-t`.
        filter["geoip"] = false
        filter.remove("geoip-code")
        filter.remove("geosite")
        return filter
    }

    private fun sanitizeDnsMatcherList(raw: Any?, label: String): List<String>? {
        if (raw == null) return null
        return stringList(raw, label)
            .filterNot(::isExternalDnsMatcher)
            .takeIf(List<String>::isNotEmpty)
    }

    private fun sanitizeDnsNameserverPolicy(raw: Any?): Map<String, Any?>? {
        if (raw == null) return null
        val policy = stringKeyMap(raw, "dns nameserver-policy")
        return policy
            .filterKeys { matcher -> !isExternalDnsMatcher(matcher) }
            .mapValues { (_, value) -> deepCopyValue(value, 1) }
            .takeIf(Map<String, Any?>::isNotEmpty)
    }

    private fun isExternalDnsMatcher(raw: String): Boolean {
        val matcher = raw.trim().lowercase()
        return matcher.startsWith("geosite:") || matcher.startsWith("rule-set:")
    }

    private fun loadSingleRoot(
        yaml: String,
        rootErrorCode: KiyoriNetworkErrorCode,
    ): Map<String, Any?> {
        requireYamlSize(yaml)
        val documents =
            try {
                Load(loadSettings).loadAllFromString(yaml).toList()
            } catch (error: StackOverflowError) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "The Mihomo YAML exceeds the nesting-depth limit.",
                    error,
                )
            } catch (error: Exception) {
                throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_INVALID,
                    "Unable to parse the Mihomo YAML.",
                    error,
                )
            }
        if (documents.size != 1) invalid("The Mihomo configuration must contain one YAML document.")
        val root = documents.single() as? Map<*, *>
            ?: throw KiyoriNetworkException(
                rootErrorCode,
                "The subscription must be a Clash or Mihomo YAML mapping.",
            )
        return stringKeyMap(root, "root")
    }

    private fun requireYamlSize(yaml: String) {
        if (yaml.isBlank()) invalid("The Mihomo YAML is empty.")
        if (yaml.toByteArray(Charsets.UTF_8).size > MAX_YAML_BYTES) {
            invalid("The Mihomo YAML exceeds the 4 MiB limit.")
        }
    }

    private fun stringKeyMap(raw: Any?, label: String): Map<String, Any?> {
        val source = raw as? Map<*, *> ?: invalid("The $label value must be a YAML mapping.")
        return buildMap(source.size) {
            source.forEach { (rawKey, value) ->
                val key = rawKey as? String
                    ?: invalid("The $label mapping contains a non-string key.")
                put(key, value)
            }
        }
    }

    private fun deepCopyMap(
        source: Map<String, Any?>,
        depth: Int = 0,
    ): Map<String, Any?> {
        if (depth > MAX_NESTING_DEPTH) invalid("The YAML exceeds the nesting-depth limit.")
        return source.mapValues { (_, value) -> deepCopyValue(value, depth + 1) }
    }

    private fun deepCopyValue(value: Any?, depth: Int): Any? =
        when (value) {
            null,
            is String,
            is Boolean,
            is Number,
            -> value
            is List<*> -> {
                if (depth > MAX_NESTING_DEPTH) invalid("The YAML exceeds the nesting-depth limit.")
                value.map { item -> deepCopyValue(item, depth + 1) }
            }
            is Map<*, *> -> deepCopyMap(stringKeyMap(value, "nested"), depth)
            else -> invalid("The YAML contains an unsupported value type: ${value::class.java.name}")
        }

    private fun isForbiddenUpstreamServer(server: String): Boolean {
        val normalized = server.removePrefix("[").removeSuffix("]")
        if (normalized.equals("localhost", ignoreCase = true)) return true
        val address =
            when {
                ':' in normalized -> runCatching { InetAddress.getByName(normalized) }.getOrNull()
                normalized.matches(IPV4_LITERAL) ->
                    runCatching {
                        val parts = normalized.split('.').map(String::toInt)
                        if (parts.any { part -> part !in 0..255 }) return@runCatching null
                        InetAddress.getByAddress(parts.map(Int::toByte).toByteArray())
                    }.getOrNull()
                else -> null
            } ?: return false
        return address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    private fun requirePublicHttpUrl(value: String, label: String): String {
        if (!isHttpUrl(value)) invalid("The $label must be an absolute HTTP(S) URL.")
        val uri = URI(value)
        if (uri.scheme?.lowercase() != "https") invalid("The $label must use HTTPS.")
        val host = uri.host.removePrefix("[").removeSuffix("]")
        if (isForbiddenUpstreamServer(host)) invalid("The $label cannot target a private address.")
        return value
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun dump(value: Map<String, Any?>): String = Dump(dumpSettings).dumpToString(value)

    private fun invalid(message: String): Nothing =
        throw KiyoriNetworkException(KiyoriNetworkErrorCode.CONFIG_INVALID, message)

    private const val MAX_NESTING_DEPTH = 64
    private val IPV4_LITERAL = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
    private val GROUP_SCALAR_KEYS =
        listOf(
            "interval",
            "timeout",
            "tolerance",
            "lazy",
            "strategy",
            "expected-status",
            "max-failed-times",
            "disable-udp",
            "filter",
            "exclude-filter",
            "exclude-type",
            "include-all",
            "include-all-proxies",
            "include-all-providers",
            "hidden",
            "icon",
        )
    private val BUILTIN_OUTBOUNDS =
        setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE")
    private val DEFAULT_DNS_SERVERS = listOf("223.5.5.5", "119.29.29.29")
    private val SUPPORTED_GROUP_TYPES = setOf("select", "url-test", "fallback", "load-balance")
    private val SUPPORTED_RULE_TYPES =
        setOf(
            "DOMAIN",
            "DOMAIN-SUFFIX",
            "DOMAIN-KEYWORD",
            "DOMAIN-WILDCARD",
            "DOMAIN-REGEX",
            "GEOSITE",
            "IP-CIDR",
            "IP-CIDR6",
            "IP-SUFFIX",
            "IP-ASN",
            "GEOIP",
            "SRC-GEOIP",
            "SRC-IP-ASN",
            "SRC-IP-CIDR",
            "SRC-IP-SUFFIX",
            "DST-PORT",
            "SRC-PORT",
            "IN-PORT",
            "IN-TYPE",
            "IN-USER",
            "IN-NAME",
            "REMATCH-NAME",
            "PROCESS-PATH",
            "PROCESS-PATH-WILDCARD",
            "PROCESS-PATH-REGEX",
            "PROCESS-NAME",
            "PROCESS-NAME-WILDCARD",
            "PROCESS-NAME-REGEX",
            "UID",
            "NETWORK",
            "DSCP",
            "RULE-SET",
            "AND",
            "OR",
            "NOT",
            "SUB-RULE",
            "MATCH",
        )
    private val RULE_OPTIONS = setOf("no-resolve", "src")
}
