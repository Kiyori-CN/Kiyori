package com.ai.assistance.operit.core.tools.javascript.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MihomoConfigSanitizerTest {
    @Test
    fun `sanitizer keeps outbound sources and removes inbound control planes`() {
        val raw =
            """
            mixed-port: 7890
            allow-lan: true
            bind-address: '*'
            external-controller: 0.0.0.0:9090
            secret: leaked
            tun:
              enable: true
            listeners:
              - name: unsafe
                type: http
                port: 9999
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
                interval: 3600
            dns:
              enable: true
              listen: 0.0.0.0:53
              nameserver:
                - 1.1.1.1
            rules:
              - MATCH,DIRECT
            """.trimIndent()

        val sanitized = MihomoConfigSanitizer.sanitize(raw)

        assertEquals(listOf("node-a"), sanitized.summary.staticProxyNames)
        assertEquals(listOf("provider-a"), sanitized.summary.providerNames)
        assertEquals(0, sanitized.summary.insecureProviderCount)
        assertTrue(sanitized.yaml.contains("node-a"))
        assertTrue(sanitized.yaml.contains("providers/"))
        assertFalse(sanitized.yaml.contains("../../escape.yaml"))
        assertFalse(sanitized.yaml.contains("mixed-port"))
        assertFalse(sanitized.yaml.contains("external-controller"))
        assertFalse(sanitized.yaml.contains("0.0.0.0:53"))
        assertFalse(sanitized.yaml.contains("MATCH,DIRECT"))
    }

    @Test
    fun `runtime config owns listener authentication controller and route`() {
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
                proxyUsername = "runtime-user",
                proxyPassword = "runtime-password",
                controllerSecret = "controller-secret",
            )

        assertTrue(runtime.yaml.contains("mixed-port: 31001"))
        assertTrue(runtime.yaml.contains("bind-address: 127.0.0.1"))
        assertTrue(runtime.yaml.contains("runtime-user:runtime-password"))
        assertTrue(runtime.yaml.contains("KIYORI_SCRIPT_PROXY"))
        assertTrue(runtime.yaml.contains("MATCH,KIYORI_SCRIPT_PROXY"))
        assertEquals(listOf("node-a"), runtime.staticProxyNames)
    }

    @Test
    fun `duplicate names and local upstreams are rejected`() {
        val duplicate =
            """
            proxies:
              - { name: same, type: ss, server: one.example.com, port: 443, cipher: aes-128-gcm, password: a }
              - { name: same, type: ss, server: two.example.com, port: 443, cipher: aes-128-gcm, password: b }
            """.trimIndent()
        assertThrows(ScriptNetworkException::class.java) {
            MihomoConfigSanitizer.sanitize(duplicate)
        }

        val local =
            """
            proxies:
              - { name: loop, type: socks5, server: 127.0.0.1, port: 1080 }
            """.trimIndent()
        assertThrows(ScriptNetworkException::class.java) {
            MihomoConfigSanitizer.sanitize(local)
        }
    }

    @Test
    fun `multiple yaml documents and file providers are rejected`() {
        assertThrows(ScriptNetworkException::class.java) {
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: one, type: ss, server: one.example.com, port: 443, cipher: aes-128-gcm, password: a }
                ---
                proxies:
                  - { name: two, type: ss, server: two.example.com, port: 443, cipher: aes-128-gcm, password: b }
                """.trimIndent(),
            )
        }
        assertThrows(ScriptNetworkException::class.java) {
            MihomoConfigSanitizer.sanitize(
                """
                proxy-providers:
                  local:
                    type: file
                    path: /sdcard/unsafe.yaml
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `invalid utf8 and parser details never expose subscription secrets`() {
        val utf8Error =
            assertThrows(ScriptNetworkException::class.java) {
                MihomoConfigSanitizer.decodeUtf8(
                    byteArrayOf(0xc3.toByte(), 0x28),
                    ScriptNetworkErrorCode.SUBSCRIPTION_FAILED,
                )
            }
        assertEquals(ScriptNetworkErrorCode.SUBSCRIPTION_FAILED, utf8Error.code)

        val secret = "never-show-this-password"
        val parseError =
            assertThrows(ScriptNetworkException::class.java) {
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

    @Test
    fun `deeply nested proxy options are rejected`() {
        val nested = buildString {
            appendLine("proxies:")
            appendLine("  - name: deep")
            appendLine("    type: ss")
            appendLine("    server: example.com")
            appendLine("    port: 443")
            appendLine("    cipher: aes-128-gcm")
            appendLine("    password: test")
            append("    options: ")
            repeat(70) { append("[") }
            append("value")
            repeat(70) { append("]") }
        }

        val error =
            assertThrows(ScriptNetworkException::class.java) {
                MihomoConfigSanitizer.sanitize(nested)
            }
        assertEquals(ScriptNetworkErrorCode.CONFIG_INVALID, error.code)
    }

    @Test
    fun `plain http providers remain explicit in the sanitized summary`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxy-providers:
                  provider-a:
                    type: http
                    url: http://example.com/subscription.yaml
                """.trimIndent(),
            )

        assertEquals(1, sanitized.summary.insecureProviderCount)
    }
}
