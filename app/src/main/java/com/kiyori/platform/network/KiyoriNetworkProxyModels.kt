package com.kiyori.platform.network

import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
enum class KiyoriNetworkModule {
    AI_SERVICES,
    AI_TOOLS,
    BROWSER,
    DOWNLOADS,
    PLAYER,
    SCRIPTS,
    APP_SERVICES,
}

@Serializable
enum class KiyoriNetworkConnectionMode {
    RULE,
    GLOBAL,
    DIRECT,
    /** Legacy serialized value from the unpublished two-state schema. Treat as GLOBAL. */
    PROXY,
}

@Serializable
enum class KiyoriNetworkOverrideMode {
    INHERIT,
    DIRECT,
    /** Uses the current top-level proxy mode. */
    PROXY,
}

@Serializable
enum class KiyoriNetworkRuleMode {
    DIRECT,
    PROXY,
}

/**
 * Explicit matcher semantics for a user-authored domain rule. Keeping the type in the
 * persisted model avoids inferring intent from punctuation in the input field.
 */
@Serializable
enum class KiyoriNetworkRuleType {
    DOMAIN,
    DOMAIN_SUFFIX,
    DOMAIN_KEYWORD,
}

@Serializable
data class KiyoriNetworkProxyRule(
    val id: String,
    val pattern: String,
    val mode: KiyoriNetworkRuleMode,
    val type: KiyoriNetworkRuleType = KiyoriNetworkRuleType.DOMAIN,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

@Serializable
enum class KiyoriSubscriptionSourceType {
    URL,
    LOCAL_FILE,
}

@Serializable
enum class MihomoNodeTestStatus {
    UNTESTED,
    SUCCESS,
    TIMEOUT,
    FAILED,
}

@Serializable
data class MihomoProxySummary(
    val name: String,
    val type: String,
)

@Serializable
data class MihomoProxyGroupSummary(
    val name: String,
    val type: String,
    val options: List<String> = emptyList(),
    val providerNames: List<String> = emptyList(),
    val manuallySelectable: Boolean = false,
)

@Serializable
data class MihomoSubscriptionSummary(
    val proxyCount: Int = 0,
    val providerCount: Int = 0,
    val groupCount: Int = 0,
    val isolatedProxyCount: Int = 0,
    val insecureProviderCount: Int = 0,
    val staticProxies: List<MihomoProxySummary> = emptyList(),
    val providerNames: List<String> = emptyList(),
    val groups: List<MihomoProxyGroupSummary> = emptyList(),
    val rootCandidates: List<String> = emptyList(),
    val ruleCount: Int = 0,
    val unsupportedRuleCount: Int = 0,
) {
    val staticProxyNames: List<String>
        get() = staticProxies.map(MihomoProxySummary::name)
}

@Serializable
data class MihomoSubscriptionUsage(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
)

@Serializable
data class MihomoNodeTestResult(
    val name: String,
    val type: String,
    val groupNames: List<String> = emptyList(),
    val delayMillis: Int? = null,
    val status: MihomoNodeTestStatus = MihomoNodeTestStatus.UNTESTED,
    val testedAtEpochMillis: Long = 0L,
)

@Serializable
data class KiyoriProxySubscription(
    val id: String,
    val displayName: String,
    val sourceType: KiyoriSubscriptionSourceType,
    val subscriptionUrl: String = "",
    val sourceLabel: String = "",
    val sanitizedYaml: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val summary: MihomoSubscriptionSummary,
    val usage: MihomoSubscriptionUsage? = null,
    val selectedGroupItems: Map<String, String> = emptyMap(),
    val nodeTests: List<MihomoNodeTestResult> = emptyList(),
    /** Rules retained from the subscription. They are replaced atomically on refresh. */
    val rules: List<String> = emptyList(),
)

@Serializable
data class KiyoriNetworkProxyConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val enabled: Boolean = false,
    val defaultMode: KiyoriNetworkConnectionMode = KiyoriNetworkConnectionMode.DIRECT,
    val scriptModes: Map<String, KiyoriNetworkOverrideMode> = emptyMap(),
    /** User-authored rules are independent from every subscription and survive updates. */
    val customRules: List<KiyoriNetworkProxyRule> = emptyList(),
    val subscriptions: List<KiyoriProxySubscription> = emptyList(),
    val activeSubscriptionId: String? = null,
    val proxyPrivateNetworks: Boolean = false,
    val allowConcurrentSystemVpn: Boolean = false,
    val testUrl: String = DEFAULT_TEST_URL,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 5
        const val DEFAULT_TEST_URL = "https://cp.cloudflare.com/generate_204"
        const val MAX_SUBSCRIPTIONS = 32
        const val MAX_SUBSCRIPTION_NAME_LENGTH = 80
        const val MAX_NODE_TEST_RESULTS = 2_000
        const val MAX_SCRIPT_RULES = 1_000
        const val MAX_SUBSCRIPTION_RULES = 20_000
        const val MAX_CUSTOM_RULES = 1_000
        const val MAX_RULE_PATTERN_LENGTH = 253
    }
}

enum class KiyoriNetworkErrorCode {
    CONFIG_MISSING,
    CONFIG_INVALID,
    SETTINGS_WRITE_FAILED,
    SUBSCRIPTION_FORMAT,
    SUBSCRIPTION_FAILED,
    SUBSCRIPTION_DUPLICATE,
    SUBSCRIPTION_NOT_FOUND,
    SUBSCRIPTION_IN_USE,
    VPN_CONFLICT,
    CORE_MISSING,
    CORE_START_FAILED,
    GROUP_SELECTION_INVALID,
    PROXY_CONNECT_FAILED,
    HTTP_FAILED,
    WEBVIEW_UNSUPPORTED,
}

open class KiyoriNetworkException(
    val code: KiyoriNetworkErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException("${code.name}: $message", cause)

class KiyoriNetworkSettingsAppliedException(
    val runtimeFailure: KiyoriNetworkException,
) : KiyoriNetworkException(
        code = runtimeFailure.code,
        message = "The settings were saved, but the embedded proxy runtime could not apply them.",
        cause = runtimeFailure,
    )

sealed interface KiyoriNetworkRoute {
    data object Direct : KiyoriNetworkRoute

    data object EmbeddedProxy : KiyoriNetworkRoute
}

data class KiyoriProxyEndpoint(
    val host: String,
    val port: Int,
)

object KiyoriNetworkProxyPolicy {
    fun effectiveModuleMode(
        config: KiyoriNetworkProxyConfig,
        module: KiyoriNetworkModule,
    ): KiyoriNetworkConnectionMode = config.defaultMode

    fun effectiveMode(
        config: KiyoriNetworkProxyConfig,
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
    ): KiyoriNetworkConnectionMode {
        if (!config.enabled) return KiyoriNetworkConnectionMode.DIRECT
        if (module != KiyoriNetworkModule.SCRIPTS || scriptPackageName.isNullOrBlank()) {
            return effectiveModuleMode(config, module)
        }
        return when (
            config.scriptModes[scriptPackageName.trim()] ?: KiyoriNetworkOverrideMode.INHERIT
        ) {
            KiyoriNetworkOverrideMode.INHERIT -> effectiveModuleMode(config, module)
            KiyoriNetworkOverrideMode.DIRECT -> KiyoriNetworkConnectionMode.DIRECT
            KiyoriNetworkOverrideMode.PROXY ->
                when (config.defaultMode) {
                    KiyoriNetworkConnectionMode.DIRECT,
                    KiyoriNetworkConnectionMode.PROXY,
                    -> KiyoriNetworkConnectionMode.GLOBAL
                    else -> config.defaultMode
                }
        }
    }

    fun runtimeMode(config: KiyoriNetworkProxyConfig): KiyoriNetworkConnectionMode {
        if (config.defaultMode != KiyoriNetworkConnectionMode.DIRECT) return config.defaultMode
        return if (config.scriptModes.values.any { it == KiyoriNetworkOverrideMode.PROXY }) {
            KiyoriNetworkConnectionMode.GLOBAL
        } else {
            KiyoriNetworkConnectionMode.DIRECT
        }
    }

    fun hasConfiguredProxyRoute(config: KiyoriNetworkProxyConfig): Boolean =
        config.defaultMode != KiyoriNetworkConnectionMode.DIRECT || config.scriptModes.keys.any { packageName ->
            effectiveMode(config, KiyoriNetworkModule.SCRIPTS, packageName) != KiyoriNetworkConnectionMode.DIRECT
        }

    fun requiresEmbeddedProxy(config: KiyoriNetworkProxyConfig): Boolean =
        config.enabled && hasConfiguredProxyRoute(config)

    fun activeSubscription(config: KiyoriNetworkProxyConfig): KiyoriProxySubscription? =
        config.activeSubscriptionId?.let { activeId ->
            config.subscriptions.firstOrNull { subscription -> subscription.id == activeId }
        }

    fun requireSubscription(
        config: KiyoriNetworkProxyConfig,
        subscriptionId: String,
    ): KiyoriProxySubscription =
        config.subscriptions.firstOrNull { subscription -> subscription.id == subscriptionId }
            ?: throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.SUBSCRIPTION_NOT_FOUND,
                "The selected subscription no longer exists.",
            )

    fun resolve(
        config: KiyoriNetworkProxyConfig,
        module: KiyoriNetworkModule,
        scriptPackageName: String? = null,
        isSystemVpnActive: Boolean,
    ): KiyoriNetworkRoute {
        validateSchema(config)
        return when (effectiveMode(config, module, scriptPackageName)) {
            KiyoriNetworkConnectionMode.DIRECT -> KiyoriNetworkRoute.Direct
            KiyoriNetworkConnectionMode.RULE,
            KiyoriNetworkConnectionMode.GLOBAL,
            KiyoriNetworkConnectionMode.PROXY,
            -> {
                validateEmbeddedStart(config, isSystemVpnActive)
                KiyoriNetworkRoute.EmbeddedProxy
            }
        }
    }

    fun validateEmbeddedStart(
        config: KiyoriNetworkProxyConfig,
        isSystemVpnActive: Boolean,
    ): KiyoriProxySubscription {
        validateSchema(config)
        if (isSystemVpnActive && !config.allowConcurrentSystemVpn) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.VPN_CONFLICT,
                "A system VPN is active and concurrent embedded proxy operation is not enabled.",
            )
        }
        val subscription =
            activeSubscription(config)
                ?: throw KiyoriNetworkException(
                    KiyoriNetworkErrorCode.CONFIG_MISSING,
                    "No active Clash or Mihomo subscription has been selected.",
                )
        if (subscription.sanitizedYaml.isBlank() || subscription.summary.rootCandidates.isEmpty()) {
            throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_MISSING,
                "The active Clash or Mihomo subscription has no usable route.",
            )
        }
        return subscription
    }

    fun validateSchema(config: KiyoriNetworkProxyConfig) {
        if (config.schemaVersion != KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION) {
            invalid("Unsupported network proxy schema: ${config.schemaVersion}")
        }
        if (config.subscriptions.size > KiyoriNetworkProxyConfig.MAX_SUBSCRIPTIONS) {
            invalid("The network proxy subscription limit was exceeded.")
        }
        if (config.scriptModes.size > KiyoriNetworkProxyConfig.MAX_SCRIPT_RULES) {
            invalid("The script network rule limit was exceeded.")
        }
        if (config.scriptModes.keys.any { packageName -> packageName.isBlank() || packageName.length > 200 }) {
            invalid("A script network rule has an invalid package name.")
        }
        val ids = linkedSetOf<String>()
        config.subscriptions.forEach { subscription ->
            if (subscription.id.isBlank() || subscription.id.length > 64 || !ids.add(subscription.id)) {
                invalid("Subscription identifiers must be non-blank and unique.")
            }
            if (
                subscription.displayName.isBlank() ||
                    subscription.displayName.length > KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH
            ) {
                invalid("A subscription has an invalid display name.")
            }
            if (subscription.sanitizedYaml.isBlank() || subscription.summary.rootCandidates.isEmpty()) {
                invalid("A saved subscription has no usable route.")
            }
            when (subscription.sourceType) {
                KiyoriSubscriptionSourceType.URL -> {
                    if (!isHttpsUrl(subscription.subscriptionUrl)) {
                        invalid("A URL subscription must use an absolute HTTPS URL.")
                    }
                }
                KiyoriSubscriptionSourceType.LOCAL_FILE -> {
                    if (subscription.subscriptionUrl.isNotBlank()) {
                        invalid("A local subscription cannot retain a remote URL.")
                    }
                }
            }
            if (subscription.nodeTests.size > KiyoriNetworkProxyConfig.MAX_NODE_TEST_RESULTS) {
                invalid("A subscription contains too many node test results.")
            }
            if (subscription.rules.size > KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_RULES) {
                invalid("A subscription contains too many retained rules.")
            }
        }
        if (config.customRules.size > KiyoriNetworkProxyConfig.MAX_CUSTOM_RULES) {
            invalid("The custom network rule limit was exceeded.")
        }
        val ruleIds = linkedSetOf<String>()
        config.customRules.forEach { rule ->
            if (rule.id.isBlank() || rule.id.length > 64 || !ruleIds.add(rule.id)) {
                invalid("Custom rule identifiers must be non-blank and unique.")
            }
            val pattern = rule.pattern.trim()
            if (pattern.isBlank() || pattern.length > KiyoriNetworkProxyConfig.MAX_RULE_PATTERN_LENGTH) {
                invalid("A custom rule has an invalid domain pattern.")
            }
            if (!isValidRulePattern(pattern, rule.type)) {
                invalid("A custom rule pattern does not match its selected rule type.")
            }
        }
        if (config.activeSubscriptionId != null && config.activeSubscriptionId !in ids) {
            invalid("The active subscription identifier is not present in the subscription library.")
        }
        if (!isHttpsUrl(config.testUrl)) {
            invalid("The network proxy test URL must use HTTPS.")
        }
    }

    private fun isHttpsUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    private fun invalid(message: String): Nothing =
        throw KiyoriNetworkException(KiyoriNetworkErrorCode.CONFIG_INVALID, message)

    private fun isValidRulePattern(value: String, type: KiyoriNetworkRuleType): Boolean {
        val pattern = value.trim().lowercase()
        if (pattern.isBlank() || pattern.length > KiyoriNetworkProxyConfig.MAX_RULE_PATTERN_LENGTH) return false
        return when (type) {
            KiyoriNetworkRuleType.DOMAIN -> pattern.matches(DOMAIN_PATTERN)
            KiyoriNetworkRuleType.DOMAIN_SUFFIX ->
                pattern.removePrefix("*.").removePrefix(".").matches(DOMAIN_PATTERN)
            KiyoriNetworkRuleType.DOMAIN_KEYWORD ->
                pattern.none { it == ',' || it.isWhitespace() } &&
                    pattern.any { it.isLetterOrDigit() } &&
                    pattern.all { it.isLetterOrDigit() || it in ".-_" }
        }
    }

    private val DOMAIN_PATTERN =
        Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
}

object KiyoriScriptNetworkCallIdentity {
    const val INTERNAL_PACKAGE_PARAMETER = "__operit_network_script_package"
    const val RUNTIME_ELIGIBLE_PARAMETER = "__operit_network_script_eligible"

    fun trustedParameters(packageName: String?, eligible: Boolean): Map<String, String> {
        val normalizedPackageName = packageName?.trim().orEmpty()
        if (!eligible || normalizedPackageName.isEmpty()) return emptyMap()
        return mapOf(INTERNAL_PACKAGE_PARAMETER to normalizedPackageName)
    }

    fun mergeTrustedParameters(
        parameters: MutableMap<String, String>,
        trustedParameters: Map<String, String>,
    ) {
        parameters.remove(INTERNAL_PACKAGE_PARAMETER)
        trustedParameters.forEach { (name, value) -> parameters[name] = value }
    }
}
