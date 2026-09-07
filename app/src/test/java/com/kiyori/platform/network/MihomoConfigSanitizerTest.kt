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
                routingMode = KiyoriNetworkConnectionMode.RULE,
            )

        assertTrue(runtime.yaml.contains("mixed-port: 31001"))
        assertTrue(runtime.yaml.contains("bind-address: 127.0.0.1"))
        assertTrue(runtime.yaml.contains(MihomoConfigSanitizer.ROUTE_GROUP_NAME))
        assertTrue(runtime.yaml.contains("MATCH,${MihomoConfigSanitizer.ROUTE_GROUP_NAME}"))
    }

    @Test
    fun `sanitizer retains supported subscription rules and runtime puts custom rules first`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                rules:
                  - DOMAIN-SUFFIX,example.com,DIRECT
                  - DOMAIN,api.example.com,KIYORI_APP_PROXY
                  - RULE-SET,ads,REJECT
                """.trimIndent(),
            )
        assertEquals(listOf("DOMAIN-SUFFIX,example.com,DIRECT", "DOMAIN,api.example.com,KIYORI_APP_PROXY"), sanitized.rules)
        assertEquals(2, sanitized.summary.ruleCount)
        assertEquals(1, sanitized.summary.unsupportedRuleCount)
        val runtime =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml = sanitized.yaml,
                mixedPort = 31001,
                controllerPort = 31002,
                controllerSecret = "controller-secret",
                testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
                routingMode = KiyoriNetworkConnectionMode.RULE,
                customRules =
                    listOf(
                        KiyoriNetworkProxyRule(
                            id = "rule-1",
                            pattern = "example.com",
                            mode = KiyoriNetworkRuleMode.DIRECT,
                            type = KiyoriNetworkRuleType.DOMAIN_SUFFIX,
                        ),
                    ),
            )
        val customIndex = runtime.yaml.indexOf("DOMAIN-SUFFIX,example.com,DIRECT")
        val subscriptionIndex = runtime.yaml.indexOf("DOMAIN-SUFFIX,example.com,DIRECT", customIndex + 1)
        assertTrue(customIndex >= 0)
        assertTrue(subscriptionIndex > customIndex)
    }

    @Test
    fun `sanitizer keeps cidr options and nested logic rules`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                rules:
                  - IP-CIDR,10.0.0.0/8,DIRECT,no-resolve
                  - IP-CIDR6,2001:db8::/32,KIYORI_APP_PROXY,no-resolve
                  - GEOIP,CN,DIRECT
                  - PROCESS-NAME,com.example.app,KIYORI_APP_PROXY
                  - AND,((DOMAIN,example.com),(NETWORK,udp)),DIRECT
                  - OR,((DOMAIN,example.net),(NETWORK,tcp)),KIYORI_APP_PROXY
                  - NOT,((DOMAIN,blocked.example)),DIRECT
                """.trimIndent(),
            )

        assertEquals(6, sanitized.rules.size)
        assertTrue(sanitized.rules.contains("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve"))
        assertTrue(sanitized.rules.contains("AND,((DOMAIN,example.com),(NETWORK,udp)),DIRECT"))
        assertEquals(1, sanitized.summary.unsupportedRuleCount)
    }

    @Test
    fun `sanitizer removes external geodata providers and nested dependencies`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                rule-providers:
                  ads:
                    type: http
                    url: https://rules.example.com/ads.yaml
                rules:
                  - GEOSITE,cn,DIRECT
                  - GEOIP,CN,DIRECT
                  - IP-ASN,1234,DIRECT
                  - SRC-IP-ASN,1234,DIRECT
                  - RULE-SET,ads,REJECT
                  - AND,((GEOSITE,cn),(DOMAIN,example.com)),DIRECT
                  - DOMAIN-SUFFIX,example.com,DIRECT
                """.trimIndent(),
            )

        assertEquals(listOf("DOMAIN-SUFFIX,example.com,DIRECT"), sanitized.rules)
        assertEquals(6, sanitized.summary.unsupportedRuleCount)
        assertFalse(sanitized.yaml.contains("rule-providers:"))
        assertFalse(sanitized.yaml.contains("GEOSITE"))
        assertFalse(sanitized.yaml.contains("GEOIP"))
        assertFalse(sanitized.yaml.contains("RULE-SET"))

        val runtimeFromLegacyYaml =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml =
                    """
                    proxies:
                      - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                    rule-providers:
                      ads:
                        type: http
                        url: https://rules.example.com/ads.yaml
                    rules:
                      - GEOSITE,cn,DIRECT
                      - RULE-SET,ads,REJECT
                      - DOMAIN,example.com,DIRECT
                    """.trimIndent(),
                mixedPort = 31001,
                controllerPort = 31002,
                controllerSecret = "controller-secret",
                testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
                routingMode = KiyoriNetworkConnectionMode.RULE,
            )
        assertFalse(runtimeFromLegacyYaml.yaml.contains("rule-providers:"))
        assertFalse(runtimeFromLegacyYaml.yaml.contains("GEOSITE"))
        assertFalse(runtimeFromLegacyYaml.yaml.contains("RULE-SET"))
        assertTrue(runtimeFromLegacyYaml.yaml.contains("DOMAIN,example.com,DIRECT"))
    }

    @Test
    fun `runtime config rejects enabled custom rules that require external data`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                """.trimIndent(),
            )

        val error =
            assertThrows(KiyoriNetworkException::class.java) {
                MihomoConfigSanitizer.buildRuntimeConfig(
                    sanitizedYaml = sanitized.yaml,
                    mixedPort = 31001,
                    controllerPort = 31002,
                    controllerSecret = "controller-secret",
                    testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
                    customRules =
                        listOf(
                            KiyoriNetworkProxyRule(
                                id = "geoip",
                                pattern = "CN",
                                mode = KiyoriNetworkRuleMode.PROXY,
                                type = KiyoriNetworkRuleType.GEOIP,
                            ),
                        ),
                )
            }
        assertEquals(KiyoriNetworkErrorCode.CONFIG_INVALID, error.code)
        assertTrue(error.message.orEmpty().contains("external"))
    }

    @Test
    fun `sanitizer keeps sub-rules and validates their references`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                sub-rules:
                  blocked:
                    - DOMAIN-SUFFIX,blocked.example,DIRECT
                    - MATCH,KIYORI_APP_PROXY
                rules:
                  - SUB-RULE,(NETWORK,tcp),blocked
                  - SUB-RULE,(NETWORK,tcp),missing
                """.trimIndent(),
            )

        assertEquals(listOf("SUB-RULE,(NETWORK,tcp),blocked"), sanitized.rules)
        assertEquals(1, sanitized.summary.unsupportedRuleCount)
        assertTrue(sanitized.yaml.contains("sub-rules:"))
        assertTrue(sanitized.yaml.contains("SUB-RULE,(NETWORK,tcp),blocked"))

        val runtime =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml = sanitized.yaml,
                mixedPort = 31001,
                controllerPort = 31002,
                controllerSecret = "controller-secret",
                testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
            )
        assertTrue(runtime.yaml.contains("sub-rules:"))
        assertTrue(runtime.yaml.contains("DOMAIN-SUFFIX,blocked.example,DIRECT"))
    }

    @Test
    fun `subscription rule replacement validates and preserves the normalized rule list`() {
        val imported =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                rules:
                  - DOMAIN-SUFFIX,example.com,DIRECT
                  - IP-CIDR,203.0.113.0/24,KIYORI_APP_PROXY,no-resolve
                """.trimIndent(),
            )
        val updated =
            MihomoConfigSanitizer.replaceSubscriptionRule(
                sanitizedYaml = imported.yaml,
                ruleIndex = 1,
                rawRule = "IP-CIDR,198.51.100.0/24,DIRECT,no-resolve",
            )
        assertEquals(
            listOf(
                "DOMAIN-SUFFIX,example.com,DIRECT",
                "IP-CIDR,198.51.100.0/24,DIRECT,no-resolve",
            ),
            updated.rules,
        )
    }

    @Test
    fun `dns sanitizer removes external geodata and rule set dependencies`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                dns:
                  enable: true
                  listen: 0.0.0.0:53
                  enhanced-mode: fake-ip
                  nameserver: [https://dns.example.com/dns-query]
                  fallback-filter:
                    geoip: true
                    geoip-code: CN
                    geosite: [gfw]
                    domain: [+.example.org]
                    ipcidr: [203.0.113.0/24]
                  fake-ip-filter: [geosite:private, rule-set:lan, +.local.example]
                  nameserver-policy:
                    geosite:cn: [https://cn.example.com/dns-query]
                    rule-set:private: [system]
                    +.explicit.example: [https://policy.example.com/dns-query]
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

        assertFalse(runtime.yaml.contains("listen:"))
        assertTrue(runtime.yaml.contains("respect-rules: false"))
        assertFalse(runtime.yaml.contains("proxy-server-nameserver:"))
        assertFalse(runtime.yaml.contains("geoip: true"))
        assertTrue(runtime.yaml.contains("geoip: false"))
        assertFalse(runtime.yaml.contains("geoip-code:"))
        assertFalse(runtime.yaml.contains("geosite:"))
        assertFalse(runtime.yaml.contains("rule-set:"))
        assertTrue(runtime.yaml.contains("+.example.org"))
        assertTrue(runtime.yaml.contains("203.0.113.0/24"))
        assertTrue(runtime.yaml.contains("+.local.example"))
        assertTrue(runtime.yaml.contains("+.explicit.example"))
        assertTrue(runtime.yaml.contains("https://dns.example.com/dns-query"))
    }

    @Test
    fun `custom matcher types become explicit Mihomo rules with specific rules first`() {
        val sanitized =
            MihomoConfigSanitizer.sanitize(
                """
                proxies:
                  - { name: node-a, type: socks5, server: proxy.example.com, port: 1080 }
                """.trimIndent(),
            )
        val runtime =
            MihomoConfigSanitizer.buildRuntimeConfig(
                sanitizedYaml = sanitized.yaml,
                mixedPort = 31001,
                controllerPort = 31002,
                controllerSecret = "controller-secret",
                testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
                routingMode = KiyoriNetworkConnectionMode.RULE,
                customRules =
                    listOf(
                        KiyoriNetworkProxyRule("keyword", "video", KiyoriNetworkRuleMode.PROXY, KiyoriNetworkRuleType.DOMAIN_KEYWORD, createdAtEpochMillis = 1),
                        KiyoriNetworkProxyRule("suffix", "example.com", KiyoriNetworkRuleMode.DIRECT, KiyoriNetworkRuleType.DOMAIN_SUFFIX, createdAtEpochMillis = 2),
                        KiyoriNetworkProxyRule("domain", "api.example.com", KiyoriNetworkRuleMode.PROXY, KiyoriNetworkRuleType.DOMAIN, createdAtEpochMillis = 3),
                    ),
            )
        val exact = runtime.yaml.indexOf("DOMAIN,api.example.com,KIYORI_APP_PROXY")
        val suffix = runtime.yaml.indexOf("DOMAIN-SUFFIX,example.com,DIRECT")
        val keyword = runtime.yaml.indexOf("DOMAIN-KEYWORD,video,KIYORI_APP_PROXY")
        assertTrue(exact >= 0)
        assertTrue(suffix > exact)
        assertTrue(keyword > suffix)
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
