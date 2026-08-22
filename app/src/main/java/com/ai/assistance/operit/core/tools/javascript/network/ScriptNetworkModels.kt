package com.ai.assistance.operit.core.tools.javascript.network

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
enum class ScriptNetworkGlobalMode {
    DIRECT,
    EXTERNAL_PROXY,
    EMBEDDED_SUBSCRIPTION,
}

@Serializable
enum class ScriptNetworkScriptMode {
    INHERIT,
    DIRECT,
    EXTERNAL_PROXY,
    EMBEDDED_SUBSCRIPTION,
}

@Serializable
data class ScriptExternalProxyConfig(
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
)

@Serializable
data class MihomoSubscriptionSummary(
    val proxyCount: Int = 0,
    val providerCount: Int = 0,
    val insecureProviderCount: Int = 0,
    val staticProxyNames: List<String> = emptyList(),
    val providerNames: List<String> = emptyList(),
)

@Serializable
data class ScriptEmbeddedProxyConfig(
    val subscriptionUrl: String = "",
    val sanitizedYaml: String = "",
    val selectedProxyName: String = "",
    val updatedAtEpochMillis: Long = 0L,
    val summary: MihomoSubscriptionSummary = MihomoSubscriptionSummary(),
)

@Serializable
data class ScriptNetworkConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val globalMode: ScriptNetworkGlobalMode = ScriptNetworkGlobalMode.DIRECT,
    val scriptModes: Map<String, ScriptNetworkScriptMode> = emptyMap(),
    val externalProxy: ScriptExternalProxyConfig = ScriptExternalProxyConfig(),
    val embeddedProxy: ScriptEmbeddedProxyConfig = ScriptEmbeddedProxyConfig(),
    val proxyPrivateNetworks: Boolean = false,
    val allowEmbeddedUnderVpn: Boolean = false,
    val testUrl: String = DEFAULT_TEST_URL,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_TEST_URL = "https://cp.cloudflare.com/generate_204"
    }
}

enum class ScriptNetworkErrorCode {
    CONFIG_MISSING,
    CONFIG_INVALID,
    VPN_CONFLICT,
    CORE_MISSING,
    CORE_START_FAILED,
    NODE_NOT_SELECTED,
    PROXY_CONNECT_FAILED,
    SUBSCRIPTION_FAILED,
    HTTP_FAILED,
}

class ScriptNetworkException(
    val code: ScriptNetworkErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException("${code.name}: $message", cause)

sealed interface ScriptNetworkRoute {
    data object Direct : ScriptNetworkRoute

    data class ExternalProxy(
        val host: String,
        val port: Int,
        val username: String?,
        val password: String?,
    ) : ScriptNetworkRoute

    data class EmbeddedSubscription(
        val selectedProxyName: String,
    ) : ScriptNetworkRoute
}

object ScriptNetworkPolicy {
    fun effectiveMode(
        config: ScriptNetworkConfig,
        packageName: String,
    ): ScriptNetworkGlobalMode =
        when (config.scriptModes[packageName] ?: ScriptNetworkScriptMode.INHERIT) {
            ScriptNetworkScriptMode.INHERIT -> config.globalMode
            ScriptNetworkScriptMode.DIRECT -> ScriptNetworkGlobalMode.DIRECT
            ScriptNetworkScriptMode.EXTERNAL_PROXY -> ScriptNetworkGlobalMode.EXTERNAL_PROXY
            ScriptNetworkScriptMode.EMBEDDED_SUBSCRIPTION ->
                ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION
        }

    fun resolve(
        config: ScriptNetworkConfig,
        packageName: String,
        isSystemVpnActive: Boolean,
    ): ScriptNetworkRoute {
        if (config.schemaVersion != ScriptNetworkConfig.CURRENT_SCHEMA_VERSION) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "Unsupported script network schema: ${config.schemaVersion}",
            )
        }
        if (packageName.isBlank()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "Script package name is required.",
            )
        }

        return when (effectiveMode(config, packageName)) {
            ScriptNetworkGlobalMode.DIRECT -> ScriptNetworkRoute.Direct
            ScriptNetworkGlobalMode.EXTERNAL_PROXY -> resolveExternal(config.externalProxy)
            ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION -> {
                val embedded = validateEmbeddedStart(config, isSystemVpnActive, requireNode = true)
                ScriptNetworkRoute.EmbeddedSubscription(embedded.selectedProxyName)
            }
        }
    }

    fun validateEmbeddedStart(
        config: ScriptNetworkConfig,
        isSystemVpnActive: Boolean,
        requireNode: Boolean,
    ): ScriptEmbeddedProxyConfig {
        if (isSystemVpnActive && !config.allowEmbeddedUnderVpn) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.VPN_CONFLICT,
                "A system VPN is active. Confirm nested proxy operation in script settings.",
            )
        }
        val embedded = config.embeddedProxy
        if (embedded.sanitizedYaml.isBlank()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_MISSING,
                "No embedded subscription has been imported.",
            )
        }
        if (requireNode && embedded.selectedProxyName.isBlank()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.NODE_NOT_SELECTED,
                "Select an embedded proxy node before running this script.",
            )
        }
        return embedded
    }

    fun validateTestUrl(rawUrl: String): String {
        val value = rawUrl.trim()
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "The test URL is invalid.",
            )
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "The test URL must be an absolute HTTP(S) URL.",
            )
        }
        return value
    }

    private fun resolveExternal(config: ScriptExternalProxyConfig): ScriptNetworkRoute.ExternalProxy {
        val host = normalizeProxyHost(config.host)
        if (config.port !in 1..65535) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "The external proxy port must be between 1 and 65535.",
            )
        }
        val username = config.username.trim().ifBlank { null }
        val password = config.password.ifBlank { null }
        if ((username == null) != (password == null)) {
            throw ScriptNetworkException(
                ScriptNetworkErrorCode.CONFIG_INVALID,
                "External proxy username and password must be filled together.",
            )
        }
        return ScriptNetworkRoute.ExternalProxy(
            host = host,
            port = config.port,
            username = username,
            password = password,
        )
    }

    private fun normalizeProxyHost(rawHost: String): String {
        val host = rawHost.trim().removePrefix("[").removeSuffix("]")
        val invalid =
            host.isBlank() ||
                host.any(Char::isWhitespace) ||
                host.any { character ->
                    character == '/' ||
                        character == '\\' ||
                        character == '@' ||
                        character == '?' ||
                        character == '#'
                }
        if (invalid) {
            invalidProxyHost()
        }

        if (':' in host) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            if (address !is Inet6Address) invalidProxyHost()
            return (address.hostAddress ?: invalidProxyHost()).substringBefore('%')
        }

        val numericParts = host.split('.')
        if (numericParts.size == 4 && numericParts.all { part -> part.all(Char::isDigit) }) {
            val values = numericParts.map { part -> part.toIntOrNull() ?: invalidProxyHost() }
            if (numericParts.any(String::isEmpty) || values.any { value -> value !in 0..255 }) {
                invalidProxyHost()
            }
            return values.joinToString(".")
        }

        val asciiHost =
            runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }
                .getOrElse { invalidProxyHost() }
                .lowercase()
                .removeSuffix(".")
        val labels = asciiHost.split('.')
        if (
            asciiHost.length !in 1..253 ||
                labels.any { label ->
                    label.isEmpty() ||
                        label.length > 63 ||
                        label.startsWith('-') ||
                        label.endsWith('-')
                }
        ) {
            invalidProxyHost()
        }
        return asciiHost
    }

    private fun invalidProxyHost(): Nothing =
        throw ScriptNetworkException(
            ScriptNetworkErrorCode.CONFIG_INVALID,
            "The external proxy host must be a valid hostname or IP address without a scheme or path.",
        )
}

object ScriptNetworkCallIdentity {
    const val INTERNAL_PACKAGE_PARAMETER = "__operit_script_network_package"
    const val RUNTIME_ELIGIBLE_PARAMETER = "__operit_script_network_eligible"

    fun trustedParameters(packageName: String?, eligible: Boolean): Map<String, String> {
        val normalizedPackageName = packageName?.trim().orEmpty()
        if (!eligible || normalizedPackageName.isEmpty()) return emptyMap()
        return mapOf(INTERNAL_PACKAGE_PARAMETER to normalizedPackageName)
    }

    fun mergeTrustedParameters(
        parameters: MutableMap<String, String>,
        trustedParameters: Map<String, String>,
    ) {
        // The internal package identity is host-owned even when no trusted identity exists.
        parameters.remove(INTERNAL_PACKAGE_PARAMETER)
        trustedParameters.forEach { (name, value) -> parameters[name] = value }
    }
}
