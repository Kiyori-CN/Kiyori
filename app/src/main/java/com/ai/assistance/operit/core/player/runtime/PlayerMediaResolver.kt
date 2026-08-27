package com.ai.assistance.operit.core.player.runtime

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import com.ai.assistance.operit.core.player.PlayerDebugLogBuffer
import com.ai.assistance.operit.core.player.PlayerDebugLogLevel

internal fun isPlayerMediaProxyBridgeTarget(target: String): Boolean {
    val uri = runCatching { URI(target) }.getOrNull() ?: return false
    if (
        !uri.scheme.equals("http", ignoreCase = true) ||
            uri.host != PLAYER_MEDIA_BRIDGE_HOST ||
            uri.port !in 1..65_535
    ) {
        return false
    }
    val token =
        uri.path
            ?.removePrefix(PLAYER_MEDIA_BRIDGE_PATH)
            ?.substringBefore('/')
            .orEmpty()
    return token.length == PLAYER_MEDIA_BRIDGE_TOKEN_LENGTH &&
        token.all { it in '0'..'9' || it in 'a'..'f' }
}

internal class PlayerMediaResolver(context: Context) {
    private val appContext = context.applicationContext
    private var contentFileDescriptor: ParcelFileDescriptor? = null

    fun resolveLocal(uriText: String): String {
        close()
        val uri = uriText.toUri()
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> {
                val descriptor =
                    requireNotNull(appContext.contentResolver.openFileDescriptor(uri, "r")) {
                        "Cannot open content URI"
                    }
                contentFileDescriptor = descriptor
                "fd://${descriptor.fd}"
            }
            "file" -> requireNotNull(uri.path) { "File URI has no path" }
            "http", "https" -> uriText
            "rtsp", "rtmp", "rtmps" -> uriText
            null -> File(uriText).absolutePath
            else -> error("Unsupported player URI scheme: ${uri.scheme}")
        }
    }

    fun close() {
        runCatching { contentFileDescriptor?.close() }
            .onFailure { error ->
                Log.w(TAG, "Failed to close player content descriptor", error)
            }
        contentFileDescriptor = null
    }

    private companion object {
        const val TAG = "PlayerMediaResolver"
    }
}

/**
 * mpv's HTTP proxy option does not proxy HTTPS media. This loopback-only bridge gives mpv a
 * normal HTTP stream while the actual request is made through the existing PLAYER route.
 * The random path is the only capability; the server never accepts non-loopback connections.
 */
internal class PlayerMediaStreamBridge(
    private val targetUrl: String,
    private val requestHeaders: Map<String, String>,
    proxySelector: ProxySelector,
    private val diagnostic: (PlayerDebugLogLevel, String) -> Unit = { level, message ->
        PlayerDebugLogBuffer.append(level, "PlayerMediaBridge", message)
    },
) : AutoCloseable {
    private val server =
        ServerSocket().apply {
            reuseAddress = true
            bind(
                InetSocketAddress(
                    InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)),
                    0,
                ),
                8,
            )
        }
    private val token = UUID.randomUUID().toString().replace("-", "")
    private val path = "$PLAYER_MEDIA_BRIDGE_PATH$token"
    private val resources = LinkedHashMap<String, MediaResource>(128, 0.75f, true)
    private val resourceIdsByUrl = HashMap<String, String>()
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val client =
        OkHttpClient.Builder()
            .proxySelector(proxySelector)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    @Volatile private var closed = false
    @Volatile private var started = false

    private data class MediaResource(
        val url: String,
        val headers: Map<String, String>,
    )

    fun start(): String {
        check(!closed) { "Player media stream bridge is closed" }
        check(!started) { "Player media stream bridge has already started" }
        started = true
        executor.execute {
            while (!closed) {
                val socket =
                    try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                executor.execute { handle(socket) }
            }
        }
        return bridgeUrl()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { server.close() }
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        synchronized(resources) {
            resources.clear()
            resourceIdsByUrl.clear()
        }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use { connection ->
            connection.soTimeout = 30_000
            var responseHeadersCommitted = false
            val output = BufferedOutputStream(connection.getOutputStream())
            try {
                val input = BufferedInputStream(connection.getInputStream())
                val requestLine = readLine(input) ?: return
                val requestParts = requestLine.split(' ', limit = 3)
                if (requestParts.size != 3 || requestParts[2] != "HTTP/1.1" && requestParts[2] != "HTTP/1.0") {
                    writeError(output, 400, "Bad Request")
                    return
                }
                val method = requestParts[0].uppercase(Locale.ROOT)
                if (method != "GET" && method != "HEAD") {
                    writeError(output, 405, "Method Not Allowed")
                    return
                }
                val path = requestParts[1].substringBefore('?')
                if (
                    path != this@PlayerMediaStreamBridge.path &&
                        !path.startsWith("${this@PlayerMediaStreamBridge.path}/")
                ) {
                    writeError(output, 404, "Not Found")
                    return
                }
                val resourceId = path.removePrefix(this@PlayerMediaStreamBridge.path).removePrefix("/")
                val resource =
                    if (resourceId.isEmpty()) {
                        MediaResource(targetUrl, requestHeaders)
                    } else {
                        resource(resourceId)
                            ?: run {
                                writeError(output, 404, "Not Found")
                                return
                            }
                    }
                val incomingHeaders = linkedMapOf<String, String>()
                var headerBytes = 0
                while (true) {
                    val line = readLine(input) ?: throw BridgeProtocolException("Incomplete request headers")
                    headerBytes += line.toByteArray(Charsets.ISO_8859_1).size + 2
                    if (headerBytes > MAX_HEADER_BYTES) {
                        throw BridgeProtocolException("Bridge request headers exceed $MAX_HEADER_BYTES bytes")
                    }
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator <= 0) continue
                    incomingHeaders[line.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                        line.substring(separator + 1).trim()
                }
                val request =
                    Request.Builder().url(resource.url).method(method, null).apply {
                        resource.headers.forEach { (name, value) ->
                            if (isForwardableHeader(name)) header(name, value)
                        }
                        header("Accept-Encoding", "identity")
                        incomingHeaders["range"]?.let { header("Range", it) }
                        incomingHeaders["if-range"]?.let { header("If-Range", it) }
                        incomingHeaders["if-none-match"]?.let { header("If-None-Match", it) }
                        incomingHeaders["if-modified-since"]?.let { header("If-Modified-Since", it) }
                    }.build()
                diagnostic(
                    PlayerDebugLogLevel.INFO,
                    "桥接请求开始 method=$method range=${incomingHeaders.containsKey("range")}",
                )
                client.newCall(request).execute().use { response ->
                    diagnostic(
                        if (response.isSuccessful) PlayerDebugLogLevel.INFO else PlayerDebugLogLevel.ERROR,
                        "上游响应 code=${response.code} method=$method range=${incomingHeaders.containsKey("range")}",
                    )
                    writeResponse(
                        output = output,
                        response = response,
                        headOnly = method == "HEAD",
                        onHeadersCommitted = { responseHeadersCommitted = true },
                    )
                }
            } catch (error: BridgeProtocolException) {
                if (!closed) {
                    diagnostic(
                        PlayerDebugLogLevel.ERROR,
                        "桥接请求协议无效 stage=REQUEST_PROTOCOL type=${error.javaClass.simpleName}",
                    )
                    if (!responseHeadersCommitted) {
                        writeError(output, 400, "Bad Request")
                    }
                }
            } catch (error: Exception) {
                if (!closed) {
                    diagnostic(
                        PlayerDebugLogLevel.ERROR,
                        "桥接请求失败 stage=${if (responseHeadersCommitted) "UPSTREAM_BODY" else "UPSTREAM_CONNECT"} " +
                            "type=${error.javaClass.simpleName}",
                    )
                    if (!responseHeadersCommitted) {
                        writeError(output, 502, "Bad Gateway")
                    }
                    Log.w(TAG, "Player media stream bridge request failed", error)
                }
            }
        }
    }

    private fun writeResponse(
        output: BufferedOutputStream,
        response: Response,
        headOnly: Boolean,
        onHeadersCommitted: () -> Unit,
    ) {
        val forwardBody = response.isSuccessful && !headOnly
        val manifestBody =
            if (forwardBody && isManifest(response)) {
                response.body?.bytes()?.let { bytes -> rewriteHlsManifest(response, bytes) }
            } else {
                null
            }
        val reason = response.message.replace(Regex("[\\r\\n]"), " ").trim().take(64).ifBlank { "OK" }
        output.write("HTTP/1.1 ${response.code} $reason\r\n".toByteArray(Charsets.ISO_8859_1))
        response.headers.forEach { (name, value) ->
            if (name.equals("Transfer-Encoding", ignoreCase = true) ||
                name.equals("Connection", ignoreCase = true) ||
                name.equals("Content-Length", ignoreCase = true) ||
                (manifestBody != null && name.equals("Content-Encoding", ignoreCase = true))
            ) return@forEach
            output.write("$name: $value\r\n".toByteArray(Charsets.ISO_8859_1))
        }
        val responseBodyLength =
            when {
                !response.isSuccessful -> 0L
                manifestBody != null -> manifestBody.size.toLong()
                headOnly -> response.body?.contentLength()
                else -> response.body?.contentLength()
            }
        responseBodyLength?.takeIf { it >= 0L }?.let { length ->
            output.write("Content-Length: $length\r\n".toByteArray(Charsets.ISO_8859_1))
        }
        output.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.flush()
        onHeadersCommitted()
        if (!forwardBody) return
        if (manifestBody != null) {
            output.write(manifestBody)
            output.flush()
            return
        }
        val body = response.body ?: return
        try {
            body.byteStream().use { source ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    output.flush()
                }
            }
        } catch (error: IOException) {
            diagnostic(
                PlayerDebugLogLevel.ERROR,
                "上游响应体读取失败 stage=UPSTREAM_BODY type=${error.javaClass.simpleName}",
            )
        }
    }

    private fun isManifest(response: Response): Boolean {
        val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
        return contentType.contains("mpegurl") ||
            contentType.contains("vnd.apple.mpegurl") ||
            response.request.url.encodedPath.endsWith(".m3u8", ignoreCase = true)
    }

    private fun rewriteHlsManifest(response: Response, bytes: ByteArray): ByteArray {
        val manifest = bytes.toString(Charsets.UTF_8)
        if (!manifest.trimStart().startsWith("#EXTM3U")) return bytes
        val baseUrl = response.request.url
        return manifest
            .lineSequence()
            .map { line ->
                rewriteHlsAttributeUris(line, baseUrl).let { rewritten ->
                    if (rewritten.trim().isEmpty() || rewritten.trimStart().startsWith("#")) {
                        rewritten
                    } else {
                        bridgeResourceUrl(resolveMediaUrl(baseUrl, rewritten.trim()))
                    }
                }
            }
            .joinToString("\n")
            .toByteArray(Charsets.UTF_8)
    }

    private fun rewriteHlsAttributeUris(line: String, baseUrl: okhttp3.HttpUrl): String {
        var rewritten = line
        val uriPattern = Regex("URI=\\\"([^\\\"]+)\\\"")
        rewritten = uriPattern.replace(rewritten) { match ->
            val target = resolveMediaUrl(baseUrl, match.groupValues[1])
            "URI=\\\"${bridgeResourceUrl(target)}\\\""
        }
        return rewritten
    }

    private fun resolveMediaUrl(baseUrl: okhttp3.HttpUrl, raw: String): String =
        baseUrl.resolve(raw)?.toString()
            ?: throw IOException("HLS manifest contains an invalid media URL")

    private fun bridgeResourceUrl(url: String): String {
        if (url.startsWith("data:", ignoreCase = true)) return url
        val resourceId = synchronized(resources) {
            resourceIdsByUrl[url]?.takeIf(resources::containsKey)
                ?: UUID.randomUUID().toString().replace("-", "").take(16).also { newId ->
                    while (resources.size >= MAX_RESOURCES) {
                        val eldest = resources.entries.iterator().next()
                        resources.remove(eldest.key)
                        resourceIdsByUrl.remove(eldest.value.url)
                    }
                    resources[newId] = MediaResource(url, requestHeaders)
                    resourceIdsByUrl[url] = newId
                }
        }
        return bridgeUrl(resourceId)
    }

    private fun resource(resourceId: String): MediaResource? =
        synchronized(resources) { resources[resourceId] }

    private fun bridgeUrl(resourceId: String? = null): String =
        buildString {
            append("http://")
            append(PLAYER_MEDIA_BRIDGE_HOST)
            append(':')
            append(server.localPort)
            append(path)
            resourceId?.let {
                append('/')
                append(it)
            }
        }

    private fun writeError(output: BufferedOutputStream, code: Int, reason: String) {
        val body = "$code $reason\n".toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 $code $reason\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < MAX_LINE_BYTES) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.ISO_8859_1.name())
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        if (bytes.size() >= MAX_LINE_BYTES) {
            throw BridgeProtocolException("Bridge request line exceeds $MAX_LINE_BYTES bytes")
        }
        return bytes.toString(Charsets.ISO_8859_1.name())
    }

    private fun isForwardableHeader(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return normalized !in setOf("host", "connection", "proxy-connection", "proxy-authorization", "accept-encoding")
    }

    private companion object {
        const val TAG = "PlayerMediaStreamBridge"
        const val MAX_RESOURCES = 4_096
        const val MAX_LINE_BYTES = 16 * 1024
        const val MAX_HEADER_BYTES = 64 * 1024
    }
}

private class BridgeProtocolException(message: String) : IOException(message)

private const val PLAYER_MEDIA_BRIDGE_HOST = "127.0.0.1"
private const val PLAYER_MEDIA_BRIDGE_PATH = "/_kiyori_player/"
private const val PLAYER_MEDIA_BRIDGE_TOKEN_LENGTH = 32
