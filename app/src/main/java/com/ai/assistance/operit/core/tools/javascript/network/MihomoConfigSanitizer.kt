package com.ai.assistance.operit.core.tools.javascript.network

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
)

data class MihomoRuntimeConfig(
    val yaml: String,
    val staticProxyNames: List<String>,
    val providerNames: List<String>,
)

object MihomoConfigSanitizer {
    const val ROUTE_GROUP_NAME = "KIYORI_SCRIPT_PROXY"
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

    fun sanitize(rawYaml: String): SanitizedMihomoSubscription {
        requireYamlSize(rawYaml)
        val root = loadSingleRoot(rawYaml)
        val proxies = sanitizeProxies(root["proxies"])
        val providers = sanitizeProviders(root["proxy-providers"])
        if (proxies.isEmpty() && providers.isEmpty()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "The subscription contains no outbound proxies or HTTP proxy providers.",
            )
        }

        val sanitized = linkedMapOf<String, Any?>()
        if (proxies.isNotEmpty()) sanitized["proxies"] = proxies.map { it.mapping }
        if (providers.isNotEmpty()) {
            sanitized["proxy-providers"] = providers.associate { it.name to it.mapping }
        }
        sanitizeDns(root["dns"])?.let { sanitized["dns"] = it }

        val staticNames = proxies.map { it.name }
        val providerNames = providers.map { it.name }
        return SanitizedMihomoSubscription(
            yaml = dump(sanitized),
            summary =
                MihomoSubscriptionSummary(
                    proxyCount = staticNames.size,
                    providerCount = providerNames.size,
                    insecureProviderCount =
                        providers.count { provider ->
                            provider.mapping["url"]
                                ?.toString()
                                ?.startsWith("http://", ignoreCase = true) == true
                        },
                    staticProxyNames = staticNames,
                    providerNames = providerNames,
                ),
        )
    }

    fun decodeUtf8(
        bytes: ByteArray,
        errorCode: ScriptNetworkErrorCode,
    ): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            throw ScriptNetworkException(
                errorCode,
                "The Mihomo YAML must be valid UTF-8.",
                error,
            )
        }

    fun buildRuntimeConfig(
        sanitizedYaml: String,
        mixedPort: Int,
        controllerPort: Int,
        proxyUsername: String,
        proxyPassword: String,
        controllerSecret: String,
    ): MihomoRuntimeConfig {
        require(mixedPort in 1..65535) { "Invalid mihomo mixed port" }
        require(controllerPort in 1..65535) { "Invalid mihomo controller port" }
        require(proxyUsername.isNotBlank() && proxyPassword.isNotBlank()) {
            "Mihomo listener authentication is required"
        }
        require(controllerSecret.isNotBlank()) { "Mihomo controller secret is required" }

        val root = loadSingleRoot(sanitizedYaml)
        val proxies = sanitizeProxies(root["proxies"])
        val providers = sanitizeProviders(root["proxy-providers"])
        if (proxies.isEmpty() && providers.isEmpty()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "The sanitized subscription has no usable outbound source.",
            )
        }

        val runtime = linkedMapOf<String, Any?>(
            "mixed-port" to mixedPort,
            "allow-lan" to false,
            "bind-address" to "127.0.0.1",
            "authentication" to listOf("$proxyUsername:$proxyPassword"),
            "mode" to "rule",
            "log-level" to "warning",
            "external-controller" to "127.0.0.1:$controllerPort",
            "secret" to controllerSecret,
            "find-process-mode" to "off",
            "geo-auto-update" to false,
        )
        if (proxies.isNotEmpty()) runtime["proxies"] = proxies.map { it.mapping }
        if (providers.isNotEmpty()) {
            runtime["proxy-providers"] = providers.associate { it.name to it.mapping }
        }
        sanitizeDns(root["dns"])?.let { runtime["dns"] = it }

        val routeGroup = linkedMapOf<String, Any?>(
            "name" to ROUTE_GROUP_NAME,
            "type" to "select",
        )
        if (proxies.isNotEmpty()) routeGroup["proxies"] = proxies.map { it.name }
        if (providers.isNotEmpty()) routeGroup["use"] = providers.map { it.name }
        runtime["proxy-groups"] = listOf(routeGroup)
        runtime["rules"] = listOf("MATCH,$ROUTE_GROUP_NAME")

        return MihomoRuntimeConfig(
            yaml = dump(runtime),
            staticProxyNames = proxies.map { it.name },
            providerNames = providers.map { it.name },
        )
    }

    private data class NamedMapping(
        val name: String,
        val mapping: Map<String, Any?>,
    )

    private fun sanitizeProxies(raw: Any?): List<NamedMapping> {
        if (raw == null) return emptyList()
        val entries = raw as? List<*>
            ?: invalid("The proxies field must be a YAML list.")
        val seen = linkedSetOf<String>()
        return entries.mapIndexed { index, entry ->
            val mapping = stringKeyMap(entry, "proxy ${index + 1}")
            val name = mapping["name"]?.toString()?.trim().orEmpty()
            if (name.isEmpty()) invalid("Proxy ${index + 1} has no name.")
            if (name == ROUTE_GROUP_NAME || !seen.add(name)) {
                invalid("Proxy name is reserved or duplicated: $name")
            }
            val type = mapping["type"]?.toString()?.trim().orEmpty()
            if (type.isEmpty()) invalid("Proxy $name has no type.")
            mapping["server"]?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let { server ->
                if (isForbiddenServer(server)) {
                    invalid("Proxy $name uses a forbidden local or multicast server address.")
                }
            }
            NamedMapping(name, deepCopyMap(mapping))
        }
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
            NamedMapping(name, mapping)
        }
    }

    private fun sanitizeDns(raw: Any?): Map<String, Any?>? {
        if (raw == null) return null
        val dns = deepCopyMap(stringKeyMap(raw, "dns")).toMutableMap()
        dns.remove("listen")
        return dns.takeIf(Map<String, Any?>::isNotEmpty)
    }

    private fun loadSingleRoot(yaml: String): Map<String, Any?> {
        requireYamlSize(yaml)
        val documents =
            try {
                Load(loadSettings).loadAllFromString(yaml).toList()
            } catch (error: StackOverflowError) {
                throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CONFIG_INVALID,
                    "The Mihomo YAML exceeds the nesting-depth limit.",
                    error,
                )
            } catch (error: Exception) {
                throw ScriptNetworkException(
                    ScriptNetworkErrorCode.CONFIG_INVALID,
                    "Unable to parse the Mihomo YAML.",
                    error,
                )
            }
        if (documents.size != 1) invalid("The Mihomo configuration must contain one YAML document.")
        return stringKeyMap(documents.single(), "root")
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
                val key = rawKey as? String ?: invalid("The $label mapping contains a non-string key.")
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
            is Number -> value
            is List<*> -> {
                if (depth > MAX_NESTING_DEPTH) invalid("The YAML exceeds the nesting-depth limit.")
                value.map { item -> deepCopyValue(item, depth + 1) }
            }
            is Map<*, *> -> deepCopyMap(stringKeyMap(value, "nested"), depth)
            else -> invalid("The YAML contains an unsupported value type: ${value::class.java.name}")
        }

    private fun isForbiddenServer(server: String): Boolean {
        val normalized = server.removePrefix("[").removeSuffix("]")
        if (normalized.equals("localhost", ignoreCase = true)) return true
        val address =
            when {
                ':' in normalized -> runCatching { InetAddress.getByName(normalized) }.getOrNull()
                normalized.matches(IPV4_LITERAL) ->
                    runCatching {
                        InetAddress.getByAddress(
                            normalized.split('.').map(String::toInt).map(Int::toByte).toByteArray(),
                        )
                    }.getOrNull()
                else -> null
            } ?: return false
        return address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isMulticastAddress
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun dump(value: Map<String, Any?>): String = Dump(dumpSettings).dumpToString(value)

    private fun invalid(message: String): Nothing =
        throw ScriptNetworkException(ScriptNetworkErrorCode.CONFIG_INVALID, message)

    private const val MAX_NESTING_DEPTH = 64
    private val IPV4_LITERAL = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
}
