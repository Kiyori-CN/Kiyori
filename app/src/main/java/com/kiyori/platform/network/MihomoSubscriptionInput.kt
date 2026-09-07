package com.kiyori.platform.network

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.common.FlowStyle

/** 输入格式只在导入边界解释一次；持久化及 runtime 始终消费清洗后的 mapping。 */
object MihomoSubscriptionInput {
    private val uriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val base64Text = Regex("[A-Za-z0-9+/_=\\s-]+")
    private const val MAX_NODES = 20_000

    fun sanitize(raw: String): SanitizedMihomoSubscription {
        if (raw.length > MihomoConfigSanitizer.MAX_YAML_BYTES ||
            raw.toByteArray(Charsets.UTF_8).size > MihomoConfigSanitizer.MAX_YAML_BYTES
        ) fail("订阅超过 4 MiB 限制。")
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) fail("订阅内容为空。")
        val format: String
        val decoded: String
        when {
            uriScheme.containsMatchIn(text) -> { format = "URI"; decoded = text }
            base64Text.matches(text) -> {
                format = "BASE64_URI"
                decoded = decodeBase64(text).trim()
                if (!uriScheme.containsMatchIn(decoded)) fail("Base64 内容不是支持的 URI 节点列表，请提供 Clash/Mihomo 配置或节点订阅。")
            }
            else -> return MihomoConfigSanitizer.sanitize(text, KiyoriNetworkErrorCode.SUBSCRIPTION_FORMAT)
        }
        val proxies = mutableListOf<Map<String, Any?>>()
        val names = mutableSetOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", MihomoConfigSanitizer.ROUTE_GROUP_NAME)
        var rejected = 0
        var unsupported = 0
        var count = 0
        decoded.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            if (++count > MAX_NODES) fail("节点列表超过 20000 条限制。")
            try {
                val node = parseNode(line).toMutableMap()
                val requested = node["name"] as String
                var name = requested
                var suffix = 2
                while (!names.add(name)) name = "${requested.take(190)} (${suffix++})"
                node["name"] = name
                proxies += node
            } catch (_: UnsupportedNode) {
                unsupported++
            } catch (_: IllegalArgumentException) {
                // 不记录 URI 或底层异常：其中可能包含密码、UUID、查询参数和原始节点名。
                rejected++
            }
        }
        if (proxies.isEmpty()) fail("$format 没有可用节点：拒绝 $rejected，不支持 $unsupported。支持 VLESS、Hysteria2、Trojan 和无插件 Shadowsocks；请检查协议、参数、UUID 和端口。")
        val yaml = Dump(DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).build())
            .dumpToString(mapOf("proxies" to proxies))
        val sanitized = MihomoConfigSanitizer.sanitize(yaml)
        return sanitized.copy(summary = sanitized.summary.copy(
            inputFormat = format,
            rejectedProxyCount = rejected,
            unsupportedProxyCount = unsupported,
        ))
    }

    private fun parseNode(line: String): Map<String, Any?> {
        require(line.none(Char::isISOControl))
        val scheme = line.substringBefore("://").lowercase()
        if (scheme !in setOf("vless", "hysteria2", "hy2", "trojan", "ss")) throw UnsupportedNode()
        val uri = try { URI(line) } catch (_: Exception) { throw IllegalArgumentException() }
        val host = uri.host?.removeSurrounding("[", "]") ?: throw IllegalArgumentException()
        require(host.isNotBlank() && '%' !in host)
        require(uri.port in 1..65535)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        val query = linkedMapOf<String, String>()
        uri.rawQuery?.takeIf(String::isNotEmpty)?.split('&')?.forEach {
            val key = percent(it.substringBefore('='))
            val value = percent(it.substringAfter('=', ""))
            require(key.isNotBlank() && key !in query)
            query[key] = value
        }
        val allowed = when (scheme) {
            "vless" -> setOf("encryption", "security", "type", "sni", "fp", "pbk", "sid", "flow", "path", "host", "alpn", "allowInsecure", "insecure", "serviceName", "packetEncoding", "headerType", "quicSecurity")
            "hysteria2", "hy2" -> setOf("sni", "insecure", "obfs", "obfs-password", "alpn", "pinSHA256", "mport")
            "trojan" -> setOf("security", "type", "sni", "fp", "path", "host", "alpn", "allowInsecure", "insecure", "serviceName")
            else -> emptySet()
        }
        if (query.keys.any { it !in allowed }) throw UnsupportedNode()
        val name = percent(uri.rawFragment.orEmpty()).ifBlank { "$scheme node" }
        require(name.length <= 200)
        val node = linkedMapOf<String, Any?>("name" to name, "type" to if (scheme == "hy2") "hysteria2" else scheme, "server" to host, "port" to uri.port, "udp" to true)
        val credential = percent(uri.rawUserInfo ?: throw IllegalArgumentException())
        require(credential.isNotBlank())
        if (scheme == "ss") {
            // 单节点凭据损坏属于拒绝该节点，不能让 Base64 解码异常中断其余合法节点。
            val auth = if (':' in credential) credential else try {
                decodeBase64(credential)
            } catch (_: KiyoriNetworkException) {
                throw IllegalArgumentException()
            }
            val cipher = auth.substringBefore(':')
            if (cipher !in setOf("aes-128-gcm", "aes-192-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305")) throw UnsupportedNode()
            require(':' in auth && auth.substringAfter(':').isNotBlank())
            node["cipher"] = cipher
            node["password"] = auth.substringAfter(':')
            return node
        }
        if (scheme == "vless") {
            require(credential.matches(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")))
            UUID.fromString(credential)
            node["uuid"] = credential
            if (query["encryption"].orEmpty() !in setOf("", "none")) throw UnsupportedNode()
            if (query["headerType"].orEmpty() !in setOf("", "none") ||
                query["quicSecurity"].orEmpty() !in setOf("", "none")
            ) throw UnsupportedNode()
        } else node["password"] = credential

        if (query["insecure"] != null && query["allowInsecure"] != null) {
            require(boolean(query.getValue("insecure")) == boolean(query.getValue("allowInsecure")))
        }
        val insecure = query["insecure"] ?: query["allowInsecure"]
        insecure?.let { node["skip-cert-verify"] = boolean(it) }
        query["alpn"]?.takeIf(String::isNotBlank)?.let { node["alpn"] = it.split(',').also { values -> require(values.all(String::isNotBlank)) } }
        query["sni"]?.takeIf(String::isNotBlank)?.let { node[if (scheme == "vless") "servername" else "sni"] = it }
        if (scheme in setOf("hysteria2", "hy2")) {
            query["obfs"]?.takeIf(String::isNotBlank)?.let {
                if (it != "salamander") throw UnsupportedNode()
                node["obfs"] = it
                node["obfs-password"] = query["obfs-password"]?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException()
            }
            if (query["obfs-password"] != null && node["obfs"] == null) throw IllegalArgumentException()
            query["pinSHA256"]?.let { node["fingerprint"] = it }
            // 端口跳跃的格式由核心最终校验，未知参数绝不静默忽略。
            query["mport"]?.let { ports ->
                require(ports.matches(Regex("[0-9,:-]+")))
                node["ports"] = ports
            }
            return node
        }
        val security = query["security"] ?: if (scheme == "trojan") "tls" else "none"
        if (security !in setOf("none", "tls", "reality")) throw UnsupportedNode()
        if (scheme == "trojan" && security != "tls") throw UnsupportedNode()
        if (scheme == "vless") node["tls"] = security != "none"
        query["fp"]?.takeIf(String::isNotBlank)?.let { node["client-fingerprint"] = it }
        val transport = query["type"].orEmpty().ifBlank { "tcp" }
        if (transport !in setOf("tcp", "ws", "grpc")) throw UnsupportedNode()
        if (security == "reality") {
            if (transport != "tcp") throw UnsupportedNode()
            val key = query["pbk"] ?: throw IllegalArgumentException()
            require(runCatching { Base64.getUrlDecoder().decode(key).size == 32 }.getOrDefault(false))
            val shortId = query["sid"].orEmpty()
            require(shortId.matches(Regex("(?:[0-9a-fA-F]{2}){0,8}")))
            node["reality-opts"] = mapOf("public-key" to key, "short-id" to shortId)
        } else if (!query["pbk"].isNullOrBlank() || !query["sid"].isNullOrBlank()) throw UnsupportedNode()
        query["flow"]?.takeIf(String::isNotBlank)?.let {
            if (it != "xtls-rprx-vision" || transport != "tcp" || security == "none") throw UnsupportedNode()
            node["flow"] = it
        }
        when (transport) {
            "ws" -> {
                if (!query["serviceName"].isNullOrBlank()) throw UnsupportedNode()
                node["network"] = "ws"
                node["ws-opts"] = buildMap<String, Any> {
                    put("path", query["path"].orEmpty().ifBlank { "/" })
                    query["host"]?.takeIf(String::isNotBlank)?.let { put("headers", mapOf("Host" to it)) }
                }
            }
            "grpc" -> {
                if (!query["path"].isNullOrBlank() || !query["host"].isNullOrBlank()) throw UnsupportedNode()
                node["network"] = "grpc"
                node["grpc-opts"] = mapOf("grpc-service-name" to query["serviceName"].orEmpty())
            }
            else -> if (!query["path"].isNullOrBlank() || !query["host"].isNullOrBlank() || !query["serviceName"].isNullOrBlank()) throw UnsupportedNode()
        }
        query["packetEncoding"]?.takeIf(String::isNotBlank)?.let {
            if (it !in setOf("xudp", "packetaddr")) throw UnsupportedNode()
            node["packet-encoding"] = it
        }
        return node
    }

    private fun percent(raw: String): String {
        // URLDecoder 的 '+' 是表单语义；URI 中必须保留字面加号。
        val value = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        require(value.none(Char::isISOControl) && '\uFFFD' !in value)
        return value
    }

    private fun boolean(value: String): Boolean = when (value.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> throw IllegalArgumentException()
    }

    private fun decodeBase64(raw: String): String {
        val compact = raw.filterNot(Char::isWhitespace)
        if (!compact.matches(Regex("[A-Za-z0-9+/_-]*={0,2}")) || compact.length % 4 == 1 ||
            (compact.any { it == '+' || it == '/' } && compact.any { it == '-' || it == '_' })
        ) fail("Base64 编码无效，请检查订阅内容是否完整。")
        val bytes = try { Base64.getDecoder().decode(compact.replace('-', '+').replace('_', '/')) }
        catch (_: IllegalArgumentException) { fail("Base64 编码无效，请检查填充和字符。") }
        return MihomoConfigSanitizer.decodeUtf8(bytes, KiyoriNetworkErrorCode.SUBSCRIPTION_FORMAT)
    }

    private class UnsupportedNode : IllegalArgumentException()
    private fun fail(message: String): Nothing = throw KiyoriNetworkException(KiyoriNetworkErrorCode.SUBSCRIPTION_FORMAT, message)
}
