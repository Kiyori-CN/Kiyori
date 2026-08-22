package com.ai.assistance.operit.core.tools.javascript.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ScriptNetworkPolicyTest {
    @Test
    fun `script override wins over global mode`() {
        val config =
            ScriptNetworkConfig(
                globalMode = ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION,
                scriptModes = mapOf("local_script" to ScriptNetworkScriptMode.DIRECT),
                embeddedProxy =
                    ScriptEmbeddedProxyConfig(
                        sanitizedYaml = "proxies: []",
                        selectedProxyName = "node",
                    ),
            )

        assertEquals(
            ScriptNetworkRoute.Direct,
            ScriptNetworkPolicy.resolve(config, "local_script", isSystemVpnActive = false),
        )
        assertEquals(
            ScriptNetworkRoute.EmbeddedSubscription("node"),
            ScriptNetworkPolicy.resolve(config, "remote_script", isSystemVpnActive = false),
        )
    }

    @Test
    fun `external proxy requires a complete endpoint and paired credentials`() {
        val incomplete =
            ScriptNetworkConfig(
                globalMode = ScriptNetworkGlobalMode.EXTERNAL_PROXY,
                externalProxy =
                    ScriptExternalProxyConfig(
                        host = "127.0.0.1",
                        port = 7890,
                        username = "user",
                    ),
            )

        val error =
            assertThrows(ScriptNetworkException::class.java) {
                ScriptNetworkPolicy.resolve(incomplete, "script", isSystemVpnActive = false)
            }
        assertEquals(ScriptNetworkErrorCode.CONFIG_INVALID, error.code)
    }

    @Test
    fun `embedded route is blocked by an unconfirmed system vpn`() {
        val config =
            ScriptNetworkConfig(
                globalMode = ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION,
                embeddedProxy =
                    ScriptEmbeddedProxyConfig(
                        sanitizedYaml = "proxies: []",
                        selectedProxyName = "node",
                    ),
            )

        val error =
            assertThrows(ScriptNetworkException::class.java) {
                ScriptNetworkPolicy.resolve(config, "script", isSystemVpnActive = true)
            }
        assertEquals(ScriptNetworkErrorCode.VPN_CONFLICT, error.code)
    }

    @Test
    fun `embedded route requires imported yaml and an explicit node`() {
        val missingConfig =
            ScriptNetworkConfig(globalMode = ScriptNetworkGlobalMode.EMBEDDED_SUBSCRIPTION)
        val configError =
            assertThrows(ScriptNetworkException::class.java) {
                ScriptNetworkPolicy.resolve(missingConfig, "script", isSystemVpnActive = false)
            }
        assertEquals(ScriptNetworkErrorCode.CONFIG_MISSING, configError.code)

        val missingNode =
            missingConfig.copy(
                embeddedProxy = ScriptEmbeddedProxyConfig(sanitizedYaml = "proxies: []"),
            )
        val nodeError =
            assertThrows(ScriptNetworkException::class.java) {
                ScriptNetworkPolicy.resolve(missingNode, "script", isSystemVpnActive = false)
            }
        assertEquals(ScriptNetworkErrorCode.NODE_NOT_SELECTED, nodeError.code)
    }

    @Test
    fun `embedded discovery validates vpn but does not require a selected node`() {
        val config =
            ScriptNetworkConfig(
                embeddedProxy = ScriptEmbeddedProxyConfig(sanitizedYaml = "proxies: []"),
            )

        assertEquals(
            config.embeddedProxy,
            ScriptNetworkPolicy.validateEmbeddedStart(
                config,
                isSystemVpnActive = false,
                requireNode = false,
            ),
        )
        val error =
            assertThrows(ScriptNetworkException::class.java) {
                ScriptNetworkPolicy.validateEmbeddedStart(
                    config,
                    isSystemVpnActive = true,
                    requireNode = false,
                )
            }
        assertEquals(ScriptNetworkErrorCode.VPN_CONFLICT, error.code)
    }

    @Test
    fun `external proxy host accepts normalized host forms and rejects url syntax`() {
        fun resolveHost(host: String): String =
            (ScriptNetworkPolicy.resolve(
                    ScriptNetworkConfig(
                        globalMode = ScriptNetworkGlobalMode.EXTERNAL_PROXY,
                        externalProxy = ScriptExternalProxyConfig(host = host, port = 7890),
                    ),
                    "script",
                    isSystemVpnActive = false,
                ) as ScriptNetworkRoute.ExternalProxy)
                .host

        assertEquals("127.0.0.1", resolveHost("127.000.000.001"))
        assertEquals("2001:db8:0:0:0:0:0:1", resolveHost("[2001:db8::1]"))
        assertEquals("xn--bcher-kva.example", resolveHost("Bücher.example"))

        listOf("http://127.0.0.1", "127.0.0.1/path", "999.1.1.1", "bad host").forEach { host ->
            val error =
                assertThrows(ScriptNetworkException::class.java) { resolveHost(host) }
            assertEquals(ScriptNetworkErrorCode.CONFIG_INVALID, error.code)
        }
    }

    @Test
    fun `trusted script identity strips forged package values`() {
        val untrusted =
            mutableMapOf(
                ScriptNetworkCallIdentity.INTERNAL_PACKAGE_PARAMETER to "forged",
                "url" to "https://example.com",
            )
        ScriptNetworkCallIdentity.mergeTrustedParameters(untrusted, emptyMap())
        assertTrue(ScriptNetworkCallIdentity.INTERNAL_PACKAGE_PARAMETER !in untrusted)

        val trusted = ScriptNetworkCallIdentity.trustedParameters(" package-a ", eligible = true)
        ScriptNetworkCallIdentity.mergeTrustedParameters(untrusted, trusted)
        assertEquals("package-a", untrusted[ScriptNetworkCallIdentity.INTERNAL_PACKAGE_PARAMETER])
        assertTrue(ScriptNetworkCallIdentity.trustedParameters("toolpkg", eligible = false).isEmpty())
    }
}
