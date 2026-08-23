package com.kiyori.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KiyoriNetworkProxyPolicyTest {
    @Test
    fun `rule and global modes both use embedded route while direct bypasses`() {
        val subscription = usableSubscription()
        val ruleConfig =
            KiyoriNetworkProxyConfig(
                enabled = true,
                defaultMode = KiyoriNetworkConnectionMode.RULE,
                subscriptions = listOf(subscription),
                activeSubscriptionId = subscription.id,
            )
        assertEquals(
            KiyoriNetworkRoute.EmbeddedProxy,
            KiyoriNetworkProxyPolicy.resolve(ruleConfig, KiyoriNetworkModule.BROWSER, isSystemVpnActive = false),
        )
        val globalConfig = ruleConfig.copy(defaultMode = KiyoriNetworkConnectionMode.GLOBAL)
        assertEquals(
            KiyoriNetworkRoute.EmbeddedProxy,
            KiyoriNetworkProxyPolicy.resolve(globalConfig, KiyoriNetworkModule.BROWSER, isSystemVpnActive = false),
        )
        val directConfig = ruleConfig.copy(defaultMode = KiyoriNetworkConnectionMode.DIRECT)
        assertEquals(
            KiyoriNetworkRoute.Direct,
            KiyoriNetworkProxyPolicy.resolve(directConfig, KiyoriNetworkModule.BROWSER, isSystemVpnActive = false),
        )
    }

    @Test
    fun `proxy override keeps top level rule semantics`() {
        val subscription = usableSubscription()
        val config =
            KiyoriNetworkProxyConfig(
                enabled = true,
                defaultMode = KiyoriNetworkConnectionMode.RULE,
                moduleModes = mapOf(KiyoriNetworkModule.BROWSER to KiyoriNetworkOverrideMode.PROXY),
                subscriptions = listOf(subscription),
                activeSubscriptionId = subscription.id,
            )
        assertEquals(
            KiyoriNetworkConnectionMode.RULE,
            KiyoriNetworkProxyPolicy.effectiveMode(config, KiyoriNetworkModule.BROWSER),
        )
    }
    @Test
    fun `disabled application is always direct`() {
        val config = KiyoriNetworkProxyConfig(enabled = false, defaultMode = KiyoriNetworkConnectionMode.PROXY)
        assertEquals(
            KiyoriNetworkRoute.Direct,
            KiyoriNetworkProxyPolicy.resolve(config, KiyoriNetworkModule.AI_SERVICES, isSystemVpnActive = true),
        )
    }

    @Test
    fun `module and script overrides are applied in order`() {
        val config =
            KiyoriNetworkProxyConfig(
                enabled = true,
                defaultMode = KiyoriNetworkConnectionMode.PROXY,
                moduleModes = mapOf(KiyoriNetworkModule.SCRIPTS to KiyoriNetworkOverrideMode.DIRECT),
                scriptModes = mapOf("remote-script" to KiyoriNetworkOverrideMode.PROXY),
                subscriptions = listOf(usableSubscription()),
                activeSubscriptionId = "subscription-1",
            )
        assertEquals(
            KiyoriNetworkRoute.Direct,
            KiyoriNetworkProxyPolicy.resolve(config, KiyoriNetworkModule.SCRIPTS, "local-script", false),
        )
        assertEquals(
            KiyoriNetworkRoute.EmbeddedProxy,
            KiyoriNetworkProxyPolicy.resolve(config, KiyoriNetworkModule.SCRIPTS, "remote-script", false),
        )
    }

    @Test
    fun `proxy route requires subscription and rejects unconfirmed vpn`() {
        val missing =
            KiyoriNetworkProxyConfig(
                enabled = true,
                defaultMode = KiyoriNetworkConnectionMode.PROXY,
            )
        assertEquals(
            KiyoriNetworkErrorCode.CONFIG_MISSING,
            assertThrows(KiyoriNetworkException::class.java) {
                KiyoriNetworkProxyPolicy.resolve(missing, KiyoriNetworkModule.BROWSER, isSystemVpnActive = false)
            }.code,
        )
        val vpnError =
            assertThrows(KiyoriNetworkException::class.java) {
                KiyoriNetworkProxyPolicy.resolve(
                    missing.copy(
                        subscriptions = listOf(usableSubscription()),
                        activeSubscriptionId = "subscription-1",
                    ),
                    KiyoriNetworkModule.BROWSER,
                    isSystemVpnActive = true,
                )
            }
        assertEquals(KiyoriNetworkErrorCode.VPN_CONFLICT, vpnError.code)
    }

    private fun usableSubscription(): KiyoriProxySubscription =
        KiyoriProxySubscription(
            id = "subscription-1",
            displayName = "Test subscription",
            sourceType = KiyoriSubscriptionSourceType.LOCAL_FILE,
            sourceLabel = "test.yaml",
            sanitizedYaml = "proxies:\n  - name: node\n    type: ss\n    server: example.com\n    port: 443",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            summary =
                MihomoSubscriptionSummary(
                    proxyCount = 1,
                    staticProxies = listOf(MihomoProxySummary(name = "node", type = "ss")),
                    rootCandidates = listOf("node"),
                ),
        )
}
