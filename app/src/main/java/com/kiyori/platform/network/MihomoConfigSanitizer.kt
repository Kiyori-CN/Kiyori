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

data class SanitizedMihomoSubscription(
    val yaml: String,
    val summary: MihomoSubscriptionSummary,
    val usage: MihomoSubscriptionUsage? = null,
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

        val sanitized = linkedMapOf<String, Any?>()
        if (proxyResult.accepted.isNotEmpty()) {
            sanitized["proxies"] = proxyResult.accepted.map(NamedMapping::mapping)
        }
        if (providers.isNotEmpty()) {
            sanitized["proxy-providers"] = providers.associate { it.name to it.mapping }
        }
        if (groups.isNotEmpty()) sanitized["proxy-groups"] = groups.map(NamedGroup::mapping)
        sanitizeDns(root["dns"])?.let { sanitized["dns"] = it }

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
                ),
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
        runtime["rules"] = listOf("MATCH,$ROUTE_GROUP_NAME")

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

    private data class NamedMapping(
        val name: String,
        val mapping: Map<String, Any?>,
    )

    private data class ProxySanitizeResult(
        val accepted: List<NamedMapping>,
        val isolatedCount: Int,
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
        if (raw == null) return null
        val dns = deepCopyMap(stringKeyMap(raw, "dns")).toMutableMap()
        dns.remove("listen")
        return dns.takeIf(Map<String, Any?>::isNotEmpty)
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
    private val SUPPORTED_GROUP_TYPES = setOf("select", "url-test", "fallback", "load-balance")
}
