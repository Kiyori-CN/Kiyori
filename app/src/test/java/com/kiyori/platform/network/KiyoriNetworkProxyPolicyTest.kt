package com.kiyori.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KiyoriNetworkProxyPolicyTest {
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
