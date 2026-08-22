package com.kiyori.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MihomoConfigSanitizerTest {
    @Test
    fun `sanitizer keeps outbound sources and removes inbound control planes`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                mixed-port: 7890
                allow-lan: true
                bind-address: '*'
                external-controller: 0.0.0.0:9090
                secret: leaked
                tun:
                  enable: true
                proxies:
                  - name: node-a
                    type: ss
                    server: example.com
                    port: 443
                    cipher: aes-128-gcm
                    password: test-password
                proxy-providers:
                  provider-a:
                    type: http
                    url: https://example.com/subscription.yaml
                    path: ../../escape.yaml
                dns:
                  enable: true
                  listen: 0.0.0.0:53
                """.trimIndent(),
            )

        assertEquals(listOf("node-a"), sanitized.summary.staticProxyNames)
        assertEquals(listOf("provider-a"), sanitized.summary.providerNames)
        assertEquals(0, sanitized.summary.insecureProviderCount)
        assertTrue(sanitized.yaml.contains("node-a"))
        assertFalse(sanitized.yaml.contains("../../escape.yaml"))
        assertFalse(sanitized.yaml.contains("mixed-port"))
        assertFalse(sanitized.yaml.contains("external-controller"))
        assertFalse(sanitized.yaml.contains("0.0.0.0:53"))
    }

    @Test
    fun `runtime config owns loopback listeners and application route`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - name: node-a
                    type: vmess
                    server: proxy.example.com
                    port: 443
                    uuid: 00000000-0000-4000-8000-000000000000
                """.trimIndent(),
            )

        val runtime =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml = sanitized.yaml,
                mixedPort = 31001,
                controllerPort = 31002,
                controllerSecret = "controller-secret",
                testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
            )

        assertTrue(runtime.yaml.contains("mixed-port: 31001"))
        assertTrue(runtime.yaml.contains("bind-address: 127.0.0.1"))
        assertTrue(runtime.yaml.contains(MihomoConfigSanitizer.ROUTE_GROUP_NAME))
        assertTrue(runtime.yaml.contains("MATCH,${MihomoConfigSanitizer.ROUTE_GROUP_NAME}"))
    }

    @Test
    fun `duplicate names and invalid providers are rejected while local nodes are isolated`() {
        val duplicate =
            """
            proxies:
              - { name: same, type: ss, server: one.example.com, port: 443, cipher: aes-128-gcm, password: a }
              - { name: same, type: ss, server: two.example.com, port: 443, cipher: aes-128-gcm, password: b }
            """.trimIndent()
        assertThrows(KiyoriNetworkException::class.java) { MihomoConfigSanitizer.sanitize(duplicate) }

        val local =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: loop, type: socks5, server: 127.0.0.1, port: 1080 }
                  - { name: node, type: socks5, server: example.com, port: 1080 }
                """.trimIndent(),
            )
        assertEquals(1, local.summary.isolatedProxyCount)
        assertEquals(listOf("node"), local.summary.staticProxyNames)
    }

    @Test
    fun `multiple documents and invalid utf8 are rejected without exposing secrets`() {
        assertThrows(KiyoriNetworkException::class.java) {
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: one, type: ss, server: one.example.com, port: 443 }
                ---
                proxies:
                  - { name: two, type: ss, server: two.example.com, port: 443 }
                """.trimIndent(),
            )
        }

        val utf8Error =
            assertThrows(KiyoriNetworkException::class.java) {
                MihomoConfigSanitizer.decodeUtf8(
                    byteArrayOf(0xc3.toByte(), 0x28),
                    KiyoriNetworkErrorCode.SUBSCRIPTION_FAILED,
                )
            }
        assertEquals(KiyoriNetworkErrorCode.SUBSCRIPTION_FAILED, utf8Error.code)

        val secret = "never-show-this-password"
        val parseError =
            assertThrows(KiyoriNetworkException::class.java) {
                MihomoConfigSanitizer.sanitize(
                    """
                    proxies:
                      - name: broken
                        type: ss
                        password: $secret
                        invalid: [
                    """.trimIndent(),
                )
            }
        assertFalse(parseError.message.orEmpty().contains(secret))
    }
}
