package com.kiyori.platform.network

import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIResponsesResumeUrl
import org.junit.Assert.*
import org.junit.Test

class KiyoriNetworkProxyMatrixTest {
    private val sanitized = MihomoSubscriptionInput.sanitize("proxies:\n  - { name: node, type: socks5, server: proxy.example.com, port: 1080 }")
    private val subscription = KiyoriProxySubscription("fixture", "fixture", KiyoriSubscriptionSourceType.LOCAL_FILE, sanitizedYaml = sanitized.yaml, createdAtEpochMillis = 0, updatedAtEpochMillis = 0, summary = sanitized.summary)
    private val base = KiyoriNetworkProxyConfig(enabled = true, subscriptions = listOf(subscription), activeSubscriptionId = subscription.id)

    @Test fun `deepseek responses endpoint and resume preserve the official host and path`() {
        val endpoint = "https://api.deepseek.com/v1/responses"
        assertEquals(endpoint, EndpointCompleter.completeEndpoint(endpoint))
        val resume = OpenAIResponsesResumeUrl.build(endpoint, "resp_fixture", 42L)
        assertEquals("api.deepseek.com", resume.host)
        assertEquals("/v1/responses/resp_fixture", resume.encodedPath)
        assertEquals("42", resume.queryParameter("starting_after"))
    }

    @Test fun `all seven modules share enabled mode and vpn semantics`() {
        for (module in KiyoriNetworkModule.entries) for (mode in KiyoriNetworkConnectionMode.entries) {
            for (enabled in listOf(false, true)) for (vpn in listOf(false, true)) for (coexist in listOf(false, true)) {
                val config = base.copy(enabled = enabled, defaultMode = mode, allowConcurrentSystemVpn = coexist)
                if (enabled && mode != KiyoriNetworkConnectionMode.DIRECT && vpn && !coexist) {
                    assertEquals(KiyoriNetworkErrorCode.VPN_CONFLICT, assertThrows(KiyoriNetworkException::class.java) { KiyoriNetworkProxyPolicy.resolve(config, module, isSystemVpnActive = vpn) }.code)
                } else {
                    assertEquals(if (enabled && mode != KiyoriNetworkConnectionMode.DIRECT) KiyoriNetworkRoute.EmbeddedProxy else KiyoriNetworkRoute.Direct, KiyoriNetworkProxyPolicy.resolve(config, module, isSystemVpnActive = vpn))
                }
            }
        }
    }

    @Test fun `enable requires selected root even in direct mode`() {
        for (mode in KiyoriNetworkConnectionMode.entries) {
            val missing = KiyoriNetworkProxyConfig(enabled = true, defaultMode = mode)
            assertFalse(KiyoriNetworkProxyPolicy.canEnable(missing))
            assertThrows(KiyoriNetworkException::class.java) { KiyoriNetworkProxyPolicy.validateEnabledConfig(missing) }
            assertThrows(KiyoriNetworkException::class.java) { KiyoriNetworkProxyPolicy.validateEnabledConfig(base.copy(subscriptions = listOf(subscription.copy(summary = MihomoSubscriptionSummary())))) }
            KiyoriNetworkProxyPolicy.validateEnabledConfig(base.copy(defaultMode = mode))
            KiyoriNetworkProxyPolicy.validateEnabledConfig(missing.copy(enabled = false))
        }
    }

    @Test fun `trusted script overrides never alter other modules`() {
        for (override in KiyoriNetworkOverrideMode.entries) for (mode in KiyoriNetworkConnectionMode.entries) {
            val config = base.copy(defaultMode = mode, scriptModes = mapOf("script" to override))
            val script = KiyoriNetworkProxyPolicy.effectiveMode(config, KiyoriNetworkModule.SCRIPTS, "script")
            when (override) {
                KiyoriNetworkOverrideMode.INHERIT -> assertEquals(mode, script)
                KiyoriNetworkOverrideMode.DIRECT -> assertEquals(KiyoriNetworkConnectionMode.DIRECT, script)
                KiyoriNetworkOverrideMode.PROXY -> assertEquals(if (mode == KiyoriNetworkConnectionMode.RULE) mode else KiyoriNetworkConnectionMode.GLOBAL, script)
            }
            assertEquals(mode, KiyoriNetworkProxyPolicy.effectiveMode(config, KiyoriNetworkModule.AI_TOOLS, "script"))
        }
    }

    @Test fun `address bypass covers loopback multicast and optional private ranges`() {
        listOf("127.0.0.2", "::1", "localhost", "test.localhost", "224.0.0.1", "ff02::1", "0.0.0.0", "::").forEach { host ->
            for (privateNetworks in listOf(false, true)) assertTrue(host, shouldBypassKiyoriProxy(host, privateNetworks))
        }
        listOf("10.1.1.1", "172.31.1.1", "192.168.1.1", "169.254.1.1", "fe80::1", "fc00::1", "fd00::1").forEach { host ->
            assertTrue(host, shouldBypassKiyoriProxy(host, false))
            assertFalse(host, shouldBypassKiyoriProxy(host, true))
        }
        assertTrue(kiyoriWebViewProxyBypassRules(false).containsAll(listOf("fc00::/7", "fe80::/10", "169.254.0.0/16")))
        assertFalse(kiyoriWebViewProxyBypassRules(true).contains("fc00::/7"))
        assertFalse(kiyoriWebViewProxyBypassRules(true).contains("<local>"))
    }
}
