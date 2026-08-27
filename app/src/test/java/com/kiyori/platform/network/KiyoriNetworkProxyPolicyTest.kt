package com.kiyori.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KiyoriNetworkProxyPolicyTest {
    @Test
    fun `startup reconciliation waits for the real browser WebView`() {
        assertFalse(
            shouldStartStartupProxyReconciliation(
                startupScheduled = true,
                browserWebViewRuntimeReady = false,
                startupAlreadyLaunched = false,
            ),
        )
        assertTrue(
            shouldStartStartupProxyReconciliation(
                startupScheduled = true,
                browserWebViewRuntimeReady = true,
                startupAlreadyLaunched = false,
            ),
        )
        assertFalse(
            shouldStartStartupProxyReconciliation(
                startupScheduled = true,
                browserWebViewRuntimeReady = true,
                startupAlreadyLaunched = true,
            ),
        )
    }

    @Test
    fun `schema four payload drops only removed module overrides during migration`() {
        val rawJson =
            """
            {
              "schemaVersion":4,
              "enabled":true,
              "defaultMode":"RULE",
              "moduleModes":{"BROWSER":"PROXY"},
              "scriptModes":{"legacy.script":"DIRECT"},
              "customRules":[],
              "subscriptions":[],
              "activeSubscriptionId":null,
              "proxyPrivateNetworks":false,
              "allowConcurrentSystemVpn":false,
              "testUrl":"https://cp.cloudflare.com/generate_204"
            }
            """.trimIndent()
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val decoded = migrateKiyoriNetworkProxyConfig(decodeKiyoriNetworkProxyConfigJson(rawJson, json))

        assertEquals(KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(KiyoriNetworkConnectionMode.RULE, decoded.defaultMode)
        assertEquals(
            KiyoriNetworkOverrideMode.DIRECT,
            decoded.scriptModes["legacy.script"],
        )
        assertFalse(json.encodeToString(decoded).contains("moduleModes"))
    }

    @Test
    fun `strict config decoding still rejects unrelated unknown fields`() {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val rawJson = json.encodeToString(KiyoriNetworkProxyConfig())
            .removeSuffix("}") + ",\"unexpectedField\":true}"

        assertThrows(Exception::class.java) {
            decodeKiyoriNetworkProxyConfigJson(rawJson, json)
        }
    }

    @Test
    fun `legacy schema two and three retain their documented migrations`() {
        val schemaTwo =
            migrateKiyoriNetworkProxyConfig(
                KiyoriNetworkProxyConfig(
                    schemaVersion = 2,
                    customRules =
                        listOf(
                            KiyoriNetworkProxyRule(
                                id = "legacy",
                                pattern = "*.example.com",
                                mode = KiyoriNetworkRuleMode.PROXY,
                            ),
                        ),
                ),
            )
        val schemaThree =
            migrateKiyoriNetworkProxyConfig(
                KiyoriNetworkProxyConfig(
                    schemaVersion = 3,
                    customRules =
                        listOf(
                            KiyoriNetworkProxyRule(
                                id = "legacy",
                                pattern = "*.example.com",
                                mode = KiyoriNetworkRuleMode.PROXY,
                            ),
                        ),
                ),
            )

        assertEquals(KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION, schemaTwo.schemaVersion)
        assertEquals(emptyList<KiyoriNetworkProxyRule>(), schemaTwo.customRules)
        assertEquals(KiyoriNetworkProxyConfig.CURRENT_SCHEMA_VERSION, schemaThree.schemaVersion)
        assertEquals("example.com", schemaThree.customRules.single().pattern)
        assertEquals(KiyoriNetworkRuleType.DOMAIN_SUFFIX, schemaThree.customRules.single().type)
    }
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
    fun `every module follows the top level mode`() {
        val subscription = usableSubscription()
        val config =
            KiyoriNetworkProxyConfig(
                enabled = true,
                defaultMode = KiyoriNetworkConnectionMode.RULE,
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
    fun `script override is applied over the top level mode`() {
        val config =
            KiyoriNetworkProxyConfig(
                enabled = true,
                defaultMode = KiyoriNetworkConnectionMode.PROXY,
                scriptModes = mapOf("remote-script" to KiyoriNetworkOverrideMode.PROXY),
                subscriptions = listOf(usableSubscription()),
                activeSubscriptionId = "subscription-1",
            )
        assertEquals(
            KiyoriNetworkRoute.EmbeddedProxy,
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

    @Test
    fun `custom rule validation follows explicit matcher type`() {
        val base = KiyoriNetworkProxyConfig()
        KiyoriNetworkProxyPolicy.validateSchema(
            base.copy(
                customRules =
                    listOf(
                        KiyoriNetworkProxyRule("domain", "api.example.com", KiyoriNetworkRuleMode.PROXY, KiyoriNetworkRuleType.DOMAIN),
                        KiyoriNetworkProxyRule("suffix", "example.com", KiyoriNetworkRuleMode.DIRECT, KiyoriNetworkRuleType.DOMAIN_SUFFIX),
                        KiyoriNetworkProxyRule("keyword", "bilibili", KiyoriNetworkRuleMode.PROXY, KiyoriNetworkRuleType.DOMAIN_KEYWORD),
                    ),
            ),
        )
        assertEquals(
            KiyoriNetworkErrorCode.CONFIG_INVALID,
            assertThrows(KiyoriNetworkException::class.java) {
                KiyoriNetworkProxyPolicy.validateSchema(
                    base.copy(
                        customRules =
                            listOf(
                                KiyoriNetworkProxyRule("bad", "https://example.com", KiyoriNetworkRuleMode.PROXY, KiyoriNetworkRuleType.DOMAIN_KEYWORD),
                            ),
                    ),
                )
            }.code,
        )
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
