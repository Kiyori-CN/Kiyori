package com.kiyori.platform.network

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class MihomoSubscriptionInputTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private val vless = "vless://$id@node.example.com:443?encryption=none&security=tls&type=ws&sni=tls.example.com&host=ws.example.com&path=%2Fpath%3Fx%3D1#%E8%93%9D%E8%89%B2%20A"
    private val hy2 = "hysteria2://test%2Bpassword@hy.example.com:8443?insecure=0&sni=tls.example.com&obfs=salamander&obfs-password=fixture#%E8%93%9D%E8%89%B2%20A"

    @Test fun `uri and both base64 alphabets preserve protocols unicode and unique names`() {
        val raw = "$vless\n\n$hy2\n"
        val variants = listOf(raw, Base64.getEncoder().encodeToString(raw.toByteArray()), Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray()))
        variants.forEachIndexed { index, input ->
            val result = MihomoSubscriptionInput.sanitize(input)
            assertEquals(if (index == 0) "URI" else "BASE64_URI", result.summary.inputFormat)
            assertEquals(2, result.summary.proxyCount)
            assertEquals(listOf("蓝色 A", "蓝色 A (2)"), result.summary.staticProxyNames)
            assertTrue(result.yaml.contains("/path?x=1"))
            assertTrue(result.yaml.contains("test+password"))
            assertTrue(result.yaml.contains("ws.example.com"))
            assertTrue(result.yaml.contains("salamander"))
            assertFalse(result.yaml.contains("mixed-port"))
        }
    }

    @Test fun `invalid unsupported and private entries have separate counters`() {
        val result = MihomoSubscriptionInput.sanitize(listOf(
            vless,
            vless.replace(id, "bad-uuid"),
            vless.replace(":443?", ":65536?"),
            "vmess://unsupported",
            vless.replace("type=ws", "type=xhttp"),
            hy2.replace("hy.example.com", "127.0.0.2"),
            hy2.replace("hy.example.com", "[fd00::1]"),
        ).joinToString("\n"))
        assertEquals(1, result.summary.proxyCount)
        assertEquals(2, result.summary.rejectedProxyCount)
        assertEquals(2, result.summary.unsupportedProxyCount)
        assertEquals(2, result.summary.isolatedProxyCount)
    }

    @Test fun `malformed utf8 base64 and zero usable nodes never import successfully`() {
        listOf("dmxlc3M6Ly8_+", "AAAA=AAA", Base64.getEncoder().encodeToString(byteArrayOf(-61, 40)), "vmess://not-supported", "vless://bad@node.example.com:443").forEach {
            assertThrows(KiyoriNetworkException::class.java) { MihomoSubscriptionInput.sanitize(it) }
        }
        assertThrows(KiyoriNetworkException::class.java) { MihomoSubscriptionInput.sanitize("a".repeat(MihomoConfigSanitizer.MAX_YAML_BYTES + 1)) }
    }

    @Test fun `reality and trojan retain tls configuration`() {
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
        val result = MihomoSubscriptionInput.sanitize("vless://$id@node.example.com:443?security=reality&type=tcp&pbk=$key&sid=abcd&flow=xtls-rprx-vision&fp=chrome#Reality\ntrojan://fixture@node.example.com:443?sni=tls.example.com#Trojan")
        assertEquals(2, result.summary.proxyCount)
        assertTrue(result.yaml.contains("reality-opts:"))
        assertTrue(result.yaml.contains("xtls-rprx-vision"))
        assertTrue(result.yaml.contains("type: trojan"))
    }

    @Test fun `legacy vless none transport options remain compatible`() {
        val input = vless.replace("#", "&headerType=none&quicSecurity=none#")
        val result = MihomoSubscriptionInput.sanitize(input)
        assertEquals(1, result.summary.proxyCount)
        assertEquals(0, result.summary.unsupportedProxyCount)
        assertTrue(result.yaml.contains("network: ws"))
    }

    @Test fun `unsupported legacy vless transport option is counted`() {
        val input = vless.replace("#", "&headerType=http#")
        assertThrows(KiyoriNetworkException::class.java) { MihomoSubscriptionInput.sanitize(input) }
    }

    @Test fun `percent encoded controls and duplicate query keys are rejected`() {
        val result = MihomoSubscriptionInput.sanitize("$vless\n${hy2.replace("insecure=0", "insecure=0&insecure=1")}\n${hy2.replace("fixture", "%0Asecret")}")
        assertEquals(1, result.summary.proxyCount)
        assertEquals(2, result.summary.rejectedProxyCount)
    }

    @Test fun `invalid shadowsocks credentials reject one node and preserve the valid list`() {
        val auth = Base64.getEncoder().encodeToString("aes-128-gcm:fixture".toByteArray())
        val invalidUtf8 = Base64.getEncoder().encodeToString(byteArrayOf(-61, 40))
        val input = "$vless\nss://$auth@ss.example.com:443#SS\nss://a@ss.example.com:443\nss://$invalidUtf8@ss.example.com:443"
        val result = MihomoSubscriptionInput.sanitize(input)
        assertEquals(2, result.summary.proxyCount)
        assertEquals(2, result.summary.rejectedProxyCount)
        assertEquals(0, result.summary.unsupportedProxyCount)
    }

    @Test fun `inapplicable transport parameters and conflicting tls aliases are not silently dropped`() {
        val invalidWs = vless.replace("#", "&serviceName=grpc-service#")
        val invalidGrpc = vless.replace("type=ws", "type=grpc")
        val conflictingTls = hy2.replace("insecure=0", "insecure=0&allowInsecure=1")
            .replace("hysteria2://", "trojan://")
            .replace("&obfs=salamander&obfs-password=fixture", "")
        val result = MihomoSubscriptionInput.sanitize("$vless\n$invalidWs\n$invalidGrpc\n$conflictingTls")
        assertEquals(1, result.summary.proxyCount)
        assertEquals(2, result.summary.unsupportedProxyCount)
        assertEquals(1, result.summary.rejectedProxyCount)
    }

    @Test fun `synthetic large yaml preserves rule order and reports external dependencies`() {
        val raw = buildString {
            appendLine("proxies:")
            appendLine("  - { name: 蓝色节点, type: vless, server: node.example.com, port: 443, uuid: $id, tls: true }")
            appendLine("  - { name: 高速节点, type: hysteria2, server: hy.example.com, port: 443, password: synthetic }")
            appendLine("proxy-groups:")
            repeat(80) { appendLine("  - { name: 策略组$it, type: select, proxies: [蓝色节点, 高速节点] }") }
            appendLine("rules:")
            repeat(9000) { appendLine("  - DOMAIN-SUFFIX,site$it.example.com,DIRECT") }
            appendLine("  - DOMAIN-KEYWORD,deepseek,DIRECT")
            appendLine("  - IP-CIDR,203.0.113.0/24,DIRECT,resolve")
            appendLine("  - IP-CIDR6,2001:db8::/32,DIRECT,no-resolve,src")
            appendLine("  - GEOIP,CN,DIRECT,resolve")
            appendLine("  - MATCH,策略组0")
        }
        val imported = MihomoSubscriptionInput.sanitize(raw)
        assertEquals(9004, imported.summary.ruleCount)
        assertEquals(1, imported.summary.unsupportedRuleCount)
        assertEquals(80, imported.summary.groupCount)
        val runtime = MihomoConfigSanitizer.buildRuntimeConfig(imported.yaml, 31001, 31002, "synthetic", KiyoriNetworkProxyConfig.DEFAULT_TEST_URL, KiyoriNetworkConnectionMode.RULE)
        imported.rules.forEach { assertTrue(runtime.yaml.contains(it)) }
        assertEquals("DOMAIN-SUFFIX,site0.example.com,DIRECT", imported.rules.first())
        assertEquals("MATCH,策略组0", imported.rules.last())
    }
}
