package com.kiyori.platform.network

import org.junit.Assert.*
import org.junit.Test

class MihomoRuleCompatibilityTest {
    private fun sanitize(vararg rules: String) = MihomoConfigSanitizer.sanitize(
        "proxies:\n  - { name: node, type: socks5, server: proxy.example.com, port: 1080 }\nrules:\n" + rules.joinToString("\n") { "  - $it" },
    )

    @Test fun `rule kinds normalize while payload and options retain their meaning`() {
        val rules = listOf("domain,example.com,DIRECT", "domain_suffix,*.example.com,DIRECT", "DOMAIN-KEYWORD,.deepseek,DIRECT", "DOMAIN-WILDCARD,*.example.com,node", "DOMAIN-REGEX,^api[0-9]+[.]example[.]com$,node", "IP-CIDR,203.0.113.0/24,DIRECT,resolve", "IP-CIDR6,2001:db8::/32,DIRECT,no-resolve,src", "IP-SUFFIX,8.8.8.8/24,DIRECT", "DST-PORT,443,node", "NETWORK,tcp,node")
        val result = sanitize(*rules.toTypedArray())
        assertEquals(rules.size, result.summary.ruleCount)
        assertEquals("DOMAIN,example.com,DIRECT", result.rules[0])
        assertEquals("DOMAIN-SUFFIX,example.com,DIRECT", result.rules[1])
        assertEquals(rules[2], result.rules[2])
        assertEquals(rules.drop(2), result.rules.drop(2))
    }

    @Test fun `logic cannot hide malformed brackets unknown options or external data`() {
        val result = sanitize(
            "AND,((DOMAIN,example.com),(OR,((NETWORK,tcp),(DST-PORT,443)))),DIRECT",
            "AND,((AND,(DOMAIN,example.com),(NETWORK,tcp)),(DST-PORT,443)),DIRECT",
            "NOT,((DOMAIN,example.com)),DIRECT",
            "AND,((DOMAIN,example.com),(NETWORK,tcp))),DIRECT",
            "AND,((DOMAIN,example.com),(NETWORK,tcp)),DIRECT,unknown",
            "AND,((geoip,CN),(NETWORK,tcp)),DIRECT",
            "AND,((src_ip_asn,123),(NETWORK,tcp)),DIRECT",
            "NOT,((DOMAIN,example.com),(NETWORK,tcp)),DIRECT",
        )
        assertEquals(3, result.summary.ruleCount)
        assertEquals(5, result.summary.unsupportedRuleCount)
    }

    @Test fun `duplicates remain ordered and count accurately`() {
        val result = sanitize("DOMAIN,example.com,DIRECT", "DOMAIN,example.com,DIRECT", "MATCH,node")
        assertEquals(3, result.rules.size)
        assertEquals(3, result.summary.ruleCount)
    }

    @Test fun `global config omits domain rules and rule config preserves deepseek host`() {
        val result = sanitize("DOMAIN,api.deepseek.com,DIRECT", "DOMAIN-SUFFIX,deepseek.com,DIRECT", "DOMAIN-SUFFIX,doubao.com,DIRECT", "MATCH,node")
        fun runtime(mode: KiyoriNetworkConnectionMode) = MihomoConfigSanitizer.buildRuntimeConfig(result.yaml, 31001, 31002, "fixture", KiyoriNetworkProxyConfig.DEFAULT_TEST_URL, mode).yaml
        assertTrue(runtime(KiyoriNetworkConnectionMode.RULE).contains("DOMAIN,api.deepseek.com,DIRECT"))
        assertFalse(runtime(KiyoriNetworkConnectionMode.GLOBAL).contains("DOMAIN,api.deepseek.com,DIRECT"))
        assertTrue(runtime(KiyoriNetworkConnectionMode.GLOBAL).contains("MATCH,KIYORI_APP_PROXY"))
    }
}
