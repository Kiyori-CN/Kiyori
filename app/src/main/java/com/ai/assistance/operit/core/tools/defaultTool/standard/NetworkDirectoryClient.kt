package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.DirectoryListingData.FileEntry
import com.jcraft.jsch.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.*
import java.io.*
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.Vector
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** 连接器仅返回目录事实，不持有第二套文件页或导航状态。远程写入不隐式映射成本地操作。 */
internal object NetworkDirectoryClient {
    private const val MAX_RESPONSE = 8 * 1024 * 1024
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun list(profile: NetworkStorageProfile, path: String, secret: String, checkActive: () -> Unit = {}): List<FileEntry> {
        val remotePath = networkRemotePath(profile.rootPath, path)
        val deadline = System.nanoTime() + 30_000_000_000L
        val active = {
            checkActive()
            check(System.nanoTime() < deadline) { "网络目录读取超时" }
        }
        active()
        val entries = when (profile.protocol) {
            NetworkStorageProtocol.FTP, NetworkStorageProtocol.FTPS -> ftp(profile, remotePath, secret, active)
            NetworkStorageProtocol.SFTP -> sftp(profile, remotePath, secret, active)
            NetworkStorageProtocol.WEBDAV -> webdav(profile, remotePath, secret)
            NetworkStorageProtocol.S3 -> s3(profile, remotePath, secret, active)
        }
        active()
        require(entries.size <= 100_000) { "远程目录超过 100000 项" }
        require(entries.all { validNetworkName(it.name) } && entries.map { it.name }.distinct().size == entries.size) { "服务器返回了无效或重复文件名" }
        return entries
    }

    private fun ftp(p: NetworkStorageProfile, path: String, secret: String, active: () -> Unit): List<FileEntry> {
        val uri = URI(p.endpoint)
        val host = requireNotNull(uri.host) { "服务器地址无效" }
        val port = if (uri.port > 0) uri.port else 21
        var socket: Socket = Socket().apply { connect(InetSocketAddress(host, port), 15_000); soTimeout = 20_000 }
        fun tls(raw: Socket, tlsPort: Int): Socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(raw, host, tlsPort, true).let { it as SSLSocket }.apply {
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                soTimeout = 20_000
                startHandshake()
            }
        var reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        var writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
        fun reply(): Pair<Int, String> {
            active()
            val first = readControlLine(reader)
            val code = first.take(3).toIntOrNull() ?: error("FTP 响应无效")
            if (first.getOrNull(3) == '-') {
                var lines = 0
                while (true) {
                    active(); require(++lines <= 1000) { "FTP 响应过长" }
                    val line = readControlLine(reader)
                    if (line.startsWith("$code ")) break
                }
            }
            return code to first
        }
        fun command(value: String): Pair<Int, String> {
            require('\r' !in value && '\n' !in value) { "命令参数包含非法换行" }
            writer.write(value + "\r\n"); writer.flush(); return reply()
        }
        try {
            check(reply().first == 220) { "FTP 服务器未就绪" }
            if (p.protocol == NetworkStorageProtocol.FTPS) {
                check(command("AUTH TLS").first == 234) { "服务器不支持显式 FTPS" }
                socket = tls(socket, port)
                reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            }
            val login = command("USER ${p.username.ifBlank { "anonymous" }}").first
            check(login == 230 || (login == 331 && command("PASS $secret").first == 230)) { "FTP 登录失败" }
            if (p.protocol == NetworkStorageProtocol.FTPS) {
                check(command("PBSZ 0").first == 200 && command("PROT P").first == 200) { "服务器不支持加密数据通道" }
            }
            check(command("TYPE I").first == 200) { "FTP 无法选择传输模式" }
            val epsv = command("EPSV")
            check(epsv.first == 229) { "服务器不支持 EPSV" }
            val dataPort = Regex("\\(\\|\\|\\|(\\d+)\\|\\)").find(epsv.second)?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { it in 1..65535 } ?: error("FTP 数据端口无效")
            val raw = Socket().apply { connect(InetSocketAddress(socket.inetAddress, dataPort), 15_000); soTimeout = 20_000 }
            raw.use {
                check(command("MLSD $path").first in setOf(125, 150)) { "服务器不支持 MLSD 或目录不可访问" }
                val data = if (p.protocol == NetworkStorageProtocol.FTPS) tls(raw, dataPort) else raw
                val text = data.use { readBounded(it.getInputStream()).toString(Charsets.UTF_8) }
                check(reply().first in setOf(226, 250)) { "FTP 目录传输未完成" }
                return text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                    active()
                    val split = line.indexOf(' ')
                    require(split > 0) { "MLSD 条目无效" }
                    val facts = line.substring(0, split).split(';').filter { '=' in it }.associate {
                        it.substringBefore('=').lowercase(Locale.ROOT) to it.substringAfter('=')
                    }
                    val type = facts["type"]?.lowercase(Locale.ROOT)
                    if (type in setOf("cdir", "pdir")) null else {
                        val modified = facts["modify"]?.take(14)?.let {
                            LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toInstant(ZoneOffset.UTC).toEpochMilli()
                        } ?: 0L
                        FileEntry(line.substring(split + 1), type == "dir", facts["size"]?.toLongOrNull() ?: 0L, "", modified.toString())
                    }
                }.toList()
            }
        } finally { socket.close() }
    }

    private fun sftp(p: NetworkStorageProfile, path: String, secret: String, active: () -> Unit): List<FileEntry> {
        require(p.hostKeySha256.startsWith("SHA256:")) { "请先配置服务器 SHA256 主机公钥指纹" }
        val uri = URI(p.endpoint)
        val jsch = JSch()
        jsch.setHostKeyRepository(object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val digest = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
                return if (MessageDigest.isEqual(digest.toByteArray(), p.hostKeySha256.trim().toByteArray())) HostKeyRepository.OK else HostKeyRepository.CHANGED
            }
            override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
            override fun remove(host: String?, type: String?) = Unit
            override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID(): String = "Kiyori pinned server key"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        })
        val session = jsch.getSession(p.username, requireNotNull(uri.host), if (uri.port > 0) uri.port else 22)
        session.setPassword(secret); session.setConfig("StrictHostKeyChecking", "yes"); session.timeout = 20_000
        try {
            session.connect(15_000)
            val channel = session.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(15_000)
                val values = mutableListOf<FileEntry>()
                channel.ls(path, ChannelSftp.LsEntrySelector { entry ->
                    active()
                    if (entry.filename != "." && entry.filename != "..") {
                        require(values.size < 100_000) { "SFTP 目录超过 100000 项" }
                        values += FileEntry(entry.filename, entry.attrs.isDir, entry.attrs.size, entry.attrs.permissionsString, (entry.attrs.mTime.toLong() * 1000).toString())
                    }
                    ChannelSftp.LsEntrySelector.CONTINUE
                })
                return values
            } finally { channel.disconnect() }
        } finally { session.disconnect() }
    }

    private fun webdav(p: NetworkStorageProfile, path: String, secret: String): List<FileEntry> {
        val url = p.endpoint.toHttpUrl().newBuilder().addPathSegments(path.trim('/')).addPathSegment("").build()
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        val builder = Request.Builder().url(url).method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType())).header("Depth", "1")
        if (p.username.isNotEmpty()) builder.header("Authorization", Credentials.basic(p.username, secret))
        return http.newCall(builder.build()).execute().use { response ->
            check(response.code == 207) { "WebDAV 目录读取失败（HTTP ${response.code}）" }
            val document = xml(readBounded(requireNotNull(response.body).byteStream()))
            val nodes = document.getElementsByTagNameNS("*", "response")
            buildList {
                for (i in 0 until nodes.length) {
                    val entry = nodes.item(i) as Element
                    val href = URI(url.toString()).resolve(entry.text("href"))
                    require(href.host == url.host && href.port == URI(url.toString()).port) { "WebDAV 返回了其他服务器路径" }
                    val entryPath = href.path.trimEnd('/')
                    val basePath = URI(url.toString()).path.trimEnd('/')
                    if (entryPath == basePath) continue
                    require(entryPath.substringBeforeLast('/') == basePath) { "WebDAV 返回了非直接子项目" }
                    val propstats = entry.getElementsByTagNameNS("*", "propstat")
                    val valid = (0 until propstats.length).map { propstats.item(it) as Element }.firstOrNull { it.text("status").contains(" 200 ") }
                        ?: continue
                    val modified = valid.text("getlastmodified").takeIf { it.isNotBlank() }?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() } ?: 0
                    add(FileEntry(entryPath.substringAfterLast('/'), valid.getElementsByTagNameNS("*", "collection").length > 0,
                        valid.text("getcontentlength").toLongOrNull() ?: 0L, "", modified.toString()))
                }
            }
        }
    }

    private fun s3(p: NetworkStorageProfile, path: String, secret: String, active: () -> Unit): List<FileEntry> {
        require(p.bucket.isNotBlank() && p.username.isNotBlank() && secret.isNotBlank()) { "请填写存储桶和访问密钥" }
        val prefix = path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val results = linkedMapOf<String, FileEntry>()
        val seenTokens = hashSetOf<String>()
        var token: String? = null
        do {
            active()
            val builder = p.endpoint.toHttpUrl().newBuilder().addPathSegment(p.bucket)
                .addQueryParameter("list-type", "2").addQueryParameter("delimiter", "/").addQueryParameter("prefix", prefix)
            token?.let { builder.addQueryParameter("continuation-token", it) }
            val request = signedS3Request(builder.build(), p.username, secret, p.region)
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "对象存储读取失败（HTTP ${response.code}）" }
                val doc = xml(readBounded(requireNotNull(response.body).byteStream()))
                val dirs = doc.getElementsByTagNameNS("*", "CommonPrefixes")
                for (i in 0 until dirs.length) {
                    val key = (dirs.item(i) as Element).text("Prefix")
                    require(key.startsWith(prefix))
                    val name = key.removePrefix(prefix).trimEnd('/')
                    results[name] = FileEntry(name, true, 0, "", "0")
                }
                val files = doc.getElementsByTagNameNS("*", "Contents")
                for (i in 0 until files.length) {
                    val entry = files.item(i) as Element
                    val key = entry.text("Key")
                    require(key.startsWith(prefix))
                    if (key == prefix) continue
                    val name = key.removePrefix(prefix)
                    require('/' !in name)
                    results[name] = FileEntry(name, false, entry.text("Size").toLong(), "", Instant.parse(entry.text("LastModified")).toEpochMilli().toString())
                }
                require(results.size <= 100_000) { "对象存储目录超过 100000 项" }
                token = if (doc.documentElement.text("IsTruncated") == "true") doc.documentElement.text("NextContinuationToken").also {
                    require(it.isNotEmpty() && seenTokens.add(it)) { "对象存储分页令牌无效" }
                } else null
            }
        } while (token != null)
        return results.values.toList()
    }

    internal fun signedS3Request(url: HttpUrl, accessKey: String, secret: String, region: String, now: Instant = Instant.now()): Request {
        val date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now)
        val day = date.take(8)
        val emptyHash = sha256(ByteArray(0))
        val host = url.host + if (url.port != (if (url.isHttps) 443 else 80)) ":${url.port}" else ""
        val headers = "host:$host\nx-amz-content-sha256:$emptyHash\nx-amz-date:$date\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val query = url.encodedQuery.orEmpty().split('&').filter { it.isNotEmpty() }.sorted().joinToString("&")
        val canonical = "GET\n${url.encodedPath}\n$query\n$headers\n$signedHeaders\n$emptyHash"
        val scope = "$day/$region/s3/aws4_request"
        fun hmac(key: ByteArray, message: String) = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(message.toByteArray(Charsets.UTF_8))
        }
        val key = hmac(hmac(hmac(hmac(("AWS4" + secret).toByteArray(), day), region), "s3"), "aws4_request")
        val signature = hmac(key, "AWS4-HMAC-SHA256\n$date\n$scope\n${sha256(canonical.toByteArray())}").joinToString("") { "%02x".format(it) }
        return Request.Builder().url(url).header("x-amz-date", date).header("x-amz-content-sha256", emptyHash)
            .header("Authorization", "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature").build()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun readControlLine(reader: Reader): String = buildString {
        while (true) {
            val value = reader.read()
            check(value >= 0) { "FTP 响应不完整" }
            if (value == 10) break
            require(length < 8192) { "FTP 响应过长" }
            if (value != 13) append(value.toChar())
        }
    }
    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= MAX_RESPONSE) { "服务器响应超过 8 MiB" }; output.write(buffer, 0, n) }
        return output.toByteArray()
    }
    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true; isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun Element.text(name: String): String = getElementsByTagNameNS("*", name).item(0)?.textContent.orEmpty()
}

internal fun validNetworkName(name: String): Boolean = name.isNotBlank() && name !in setOf(".", "..") && name.none { it == '/' || it == '\\' || it == '\u0000' || it == '\r' || it == '\n' }
internal fun networkRemotePath(root: String, path: String): String {
    require(root.startsWith('/') && path.startsWith('/')) { "网络路径必须是绝对路径" }
    require((root + path).split('/').none { it == "." || it == ".." || '\u0000' in it || '\r' in it || '\n' in it || '\\' in it }) { "网络路径无效" }
    return (root.trimEnd('/') + "/" + path.trimStart('/')).ifEmpty { "/" }
}
