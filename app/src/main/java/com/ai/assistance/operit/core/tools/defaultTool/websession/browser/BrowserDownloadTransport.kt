package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.net.Network
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

internal const val BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT = 5
internal const val BROWSER_DOWNLOAD_TRANSPORT_RESOURCE_BUFFER_BYTES = 64 * 1024
internal const val BROWSER_DOWNLOAD_MAX_TEXT_RESPONSE_BYTES = 4L * 1024L * 1024L

internal data class BrowserDownloadTransportConfig(
    val maxConcurrentTasks: Int,
    val normalThreadCount: Int,
    val m3u8ThreadCount: Int,
    val chunkSizeKb: Int,
    val enableHttp2: Boolean,
)

internal data class BrowserDownloadProbeResult(
    val finalUrl: String,
    val contentLength: Long,
    val acceptsRanges: Boolean,
    val mimeType: String,
    val etag: String?,
    val lastModified: String?,
)

internal fun resolveBrowserDownloadIfRangeValidator(
    etag: String?,
    lastModified: String?,
): String? {
    val normalizedEtag = etag?.trim().orEmpty()
    if (
        normalizedEtag.length >= 2 &&
            normalizedEtag.startsWith("\"") &&
            normalizedEtag.endsWith("\"") &&
            !normalizedEtag.startsWith("W/", ignoreCase = true)
    ) {
        return normalizedEtag
    }
    return lastModified?.trim()?.takeIf { value -> value.isNotBlank() }
}

internal open class BrowserDownloadNonRetryableException(
    message: String,
) : IOException(message)

internal class BrowserDownloadRepresentationChangedException :
    BrowserDownloadNonRetryableException(
        "The remote download resource changed while ranged data was being retrieved.",
    )

internal data class BrowserDownloadTextResult(
    val finalUrl: String,
    val mimeType: String,
    val content: String,
)

private fun validateBrowserDownloadTransportConfig(
    config: BrowserDownloadTransportConfig,
): BrowserDownloadTransportConfig {
    require(isSupportedBrowserDownloadConcurrency(config.maxConcurrentTasks)) {
        "Unsupported browser download concurrency: ${config.maxConcurrentTasks}"
    }
    require(isSupportedBrowserDownloadSegmentThreadCount(config.normalThreadCount)) {
        "Unsupported browser download segment thread count: ${config.normalThreadCount}"
    }
    require(isSupportedBrowserDownloadM3u8ThreadCount(config.m3u8ThreadCount)) {
        "Unsupported browser download M3U8 thread count: ${config.m3u8ThreadCount}"
    }
    require(isSupportedBrowserDownloadChunkSizeKb(config.chunkSizeKb)) {
        "Unsupported browser download chunk size: ${config.chunkSizeKb}"
    }
    require(
        config.maxConcurrentTasks <=
            resolveBrowserDownloadMaxConcurrentTasksLimit(
                normalThreadCount = config.normalThreadCount,
                m3u8ThreadCount = config.m3u8ThreadCount,
            ),
    ) {
        "Browser download concurrency exceeds the active thread limit: ${config.maxConcurrentTasks}"
    }
    return config
}

internal class BrowserDownloadTransport(
    config: BrowserDownloadTransportConfig,
    network: Network? = null,
    private val retryDelay: (Long) -> Unit = { delayMillis -> Thread.sleep(delayMillis) },
) : Closeable {
    private val config = validateBrowserDownloadTransportConfig(config)
    private val dispatcher =
        Dispatcher().apply {
            maxRequests = config.maxConcurrentTasks * maxOf(config.normalThreadCount, config.m3u8ThreadCount)
            maxRequestsPerHost = maxOf(config.normalThreadCount, config.m3u8ThreadCount)
        }
    private val connectionPool =
        ConnectionPool(dispatcher.maxRequests, 30L, TimeUnit.SECONDS)
    private val clientBuilder =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(90L, TimeUnit.SECONDS)
            .writeTimeout(90L, TimeUnit.SECONDS)
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(
                if (config.enableHttp2) {
                    listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
                } else {
                    listOf(Protocol.HTTP_1_1)
                },
            )
            .apply {
                network?.let { assignedNetwork ->
                    socketFactory(assignedNetwork.socketFactory)
                    dns(
                        object : Dns {
                            override fun lookup(hostname: String): List<java.net.InetAddress> =
                                assignedNetwork.getAllByName(hostname).toList()
                        },
                    )
                }
            }
    private val client = clientBuilder.build()

    internal val protocols: List<Protocol>
        get() = client.protocols

    internal fun probe(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): BrowserDownloadProbeResult = executeWithRetry { probeOnce(url, headers) }

    private fun probeOnce(
        url: String,
        headers: Map<String, String>,
    ): BrowserDownloadProbeResult {
        val head = executeHead(url, headers)
        if (head.code == 405 || head.code == 501) {
            head.close()
            return probeWithRange(url, headers, headResult = null)
        }
        if (!head.isSuccessful) {
            val code = head.code
            head.close()
            throw browserDownloadHttpException(code, "probing download metadata")
        }

        val headResult =
            BrowserDownloadProbeResult(
                finalUrl = head.request.url.toString(),
                contentLength = head.header("Content-Length")?.toLongOrNull() ?: -1L,
                acceptsRanges = head.header("Accept-Ranges").equals("bytes", ignoreCase = true),
                mimeType = head.header("Content-Type").orEmpty(),
                etag = head.header("ETag"),
                lastModified = head.header("Last-Modified"),
            )
        head.close()
        if (headResult.contentLength > 0L && headResult.acceptsRanges) {
            return headResult
        }
        return probeWithRange(url, headers, headResult)
    }

    internal fun downloadRangeWithRetry(
        url: String,
        headers: Map<String, String>,
        startInclusive: Long,
        endInclusive: Long,
        destination: File,
        append: Boolean,
        expectedTotalBytes: Long? = null,
        ifRangeValidator: String? = null,
        onChunk: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Long {
        require(startInclusive >= 0L) { "Browser download range start is negative: $startInclusive" }
        require(endInclusive >= startInclusive) {
            "Browser download range end precedes start: $startInclusive-$endInclusive"
        }
        val initialLength = if (append && destination.exists()) destination.length() else 0L
        var attemptBytes = 0L
        return executeWithRetry(
            onAttemptFailure = {
                rollbackFile(destination, initialLength, append)
                emitNegativeChunkDelta(attemptBytes, onChunk)
            },
        ) {
            attemptBytes = 0L
            executeRangeOnce(
                url = url,
                headers = headers,
                startInclusive = startInclusive,
                endInclusive = endInclusive,
                destination = destination,
                append = append,
                expectedTotalBytes = expectedTotalBytes,
                ifRangeValidator = ifRangeValidator,
                onChunk = { bytes ->
                    attemptBytes += bytes.toLong()
                    onChunk(bytes)
                },
                isCancelled = isCancelled,
            )
        }
    }

    internal fun downloadStreamWithRetry(
        url: String,
        headers: Map<String, String>,
        destination: File,
        bufferSizeBytes: Int,
        append: Boolean = false,
        expectedLength: Long? = null,
        onChunk: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Long {
        require(bufferSizeBytes > 0) { "Browser download buffer size must be positive: $bufferSizeBytes" }
        val initialLength = if (append && destination.exists()) destination.length() else 0L
        var attemptBytes = 0L
        return executeWithRetry(
            onAttemptFailure = {
                rollbackFile(destination, initialLength, append)
                emitNegativeChunkDelta(attemptBytes, onChunk)
            },
        ) {
            attemptBytes = 0L
            executeStreamOnce(
                url = url,
                headers = headers,
                destination = destination,
                bufferSizeBytes = bufferSizeBytes,
                append = append,
                expectedLength = expectedLength,
                onChunk = { bytes ->
                    attemptBytes += bytes.toLong()
                    onChunk(bytes)
                },
                isCancelled = isCancelled,
            )
        }
    }

    internal fun downloadResourceWithRetry(
        url: String,
        headers: Map<String, String>,
        destination: File,
        onChunk: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Long =
        downloadStreamWithRetry(
            url = url,
            headers = headers,
            destination = destination,
            bufferSizeBytes = BROWSER_DOWNLOAD_TRANSPORT_RESOURCE_BUFFER_BYTES,
            onChunk = onChunk,
            isCancelled = isCancelled,
        )

    internal fun readTextWithRetry(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = BROWSER_DOWNLOAD_MAX_TEXT_RESPONSE_BYTES,
    ): BrowserDownloadTextResult =
        executeWithRetry {
            require(maxBytes > 0L) { "Browser download text limit must be positive" }
            val request = buildRequest(url, headers, range = null)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw browserDownloadHttpException(response.code, "reading download content")
                }
                val body =
                    response.body
                        ?: throw BrowserDownloadNonRetryableException(
                            "The download response did not contain a body.",
                        )
                if (body.contentLength() > maxBytes) {
                    throw BrowserDownloadNonRetryableException(
                        "The download text response exceeded $maxBytes bytes.",
                    )
                }
                val source = body.source()
                if (source.request(maxBytes + 1L)) {
                    throw BrowserDownloadNonRetryableException(
                        "The download text response exceeded $maxBytes bytes.",
                    )
                }
                val charset =
                    body.contentType()?.charset(StandardCharsets.UTF_8)
                        ?: StandardCharsets.UTF_8
                BrowserDownloadTextResult(
                    finalUrl = response.request.url.toString(),
                    mimeType = response.header("Content-Type") ?: body.contentType()?.toString().orEmpty(),
                    content = String(source.readByteArray(), charset),
                )
            }
        }

    override fun close() {
        dispatcher.cancelAll()
        dispatcher.executorService.shutdownNow()
        connectionPool.evictAll()
    }

    private fun executeHead(url: String, headers: Map<String, String>): Response =
        client.newCall(buildRequest(url, headers, range = null, method = "HEAD")).execute()

    private fun probeWithRange(
        url: String,
        headers: Map<String, String>,
        headResult: BrowserDownloadProbeResult?,
    ): BrowserDownloadProbeResult {
        client.newCall(buildRequest(url, headers, range = 0L to 0L)).execute().use { response ->
            val finalUrl = response.request.url.toString()
            return when (response.code) {
                206 -> {
                    val contentRange = parseContentRange(response.header("Content-Range"))
                    if (contentRange.startInclusive != 0L || contentRange.endInclusive != 0L) {
                        throw IOException(
                            "Range probe returned an unexpected Content-Range: ${response.header("Content-Range")}",
                        )
                    }
                    BrowserDownloadProbeResult(
                        finalUrl = finalUrl,
                        contentLength = contentRange.totalBytes,
                        acceptsRanges = true,
                        mimeType = response.header("Content-Type") ?: headResult?.mimeType.orEmpty(),
                        etag = response.header("ETag") ?: headResult?.etag,
                        lastModified = response.header("Last-Modified") ?: headResult?.lastModified,
                    )
                }
                200 -> {
                    BrowserDownloadProbeResult(
                        finalUrl = finalUrl,
                        contentLength =
                            response.body?.contentLength()?.takeIf { it >= 0L }
                                ?: headResult?.contentLength
                                ?: -1L,
                        acceptsRanges = false,
                        mimeType = response.header("Content-Type") ?: headResult?.mimeType.orEmpty(),
                        etag = response.header("ETag") ?: headResult?.etag,
                        lastModified = response.header("Last-Modified") ?: headResult?.lastModified,
                    )
                }
                else -> throw browserDownloadHttpException(response.code, "probing byte-range support")
            }
        }
    }

    private fun executeRangeOnce(
        url: String,
        headers: Map<String, String>,
        startInclusive: Long,
        endInclusive: Long,
        destination: File,
        append: Boolean,
        expectedTotalBytes: Long?,
        ifRangeValidator: String?,
        onChunk: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        client.newCall(
            buildRequest(
                url = url,
                headers = headers,
                range = startInclusive to endInclusive,
                ifRangeValidator = ifRangeValidator,
            ),
        ).execute().use { response ->
            if (response.code == 200 && !ifRangeValidator.isNullOrBlank()) {
                throw BrowserDownloadRepresentationChangedException()
            }
            if (response.code != 206) {
                throw BrowserDownloadNonRetryableException(
                    "Expected HTTP 206 for range $startInclusive-$endInclusive, received ${response.code}",
                )
            }
            if (
                !ifRangeValidator.isNullOrBlank() &&
                    !responseMatchesIfRangeValidator(response, ifRangeValidator)
            ) {
                throw BrowserDownloadRepresentationChangedException()
            }
            val contentRange = parseContentRange(response.header("Content-Range"))
            if (
                contentRange.startInclusive != startInclusive ||
                    contentRange.endInclusive != endInclusive
            ) {
                throw BrowserDownloadNonRetryableException(
                    "Range response does not match requested bytes: ${response.header("Content-Range")}",
                )
            }
            if (expectedTotalBytes != null && contentRange.totalBytes != expectedTotalBytes) {
                throw BrowserDownloadRepresentationChangedException()
            }
            val expectedLength = endInclusive - startInclusive + 1L
            val body =
                response.body
                    ?: throw BrowserDownloadNonRetryableException(
                        "The ranged download response did not contain a body.",
                    )
            return writeBody(
                body = body,
                destination = destination,
                bufferSizeBytes = BROWSER_DOWNLOAD_TRANSPORT_RESOURCE_BUFFER_BYTES,
                append = append,
                expectedLength = expectedLength,
                onChunk = onChunk,
                isCancelled = isCancelled,
            )
        }
    }

    private fun responseMatchesIfRangeValidator(
        response: Response,
        expectedValidator: String,
    ): Boolean {
        val actualValidator =
            if (expectedValidator.startsWith("\"")) {
                response.header("ETag")
            } else {
                response.header("Last-Modified")
            }
        return actualValidator.isNullOrBlank() || actualValidator.trim() == expectedValidator
    }

    private fun executeStreamOnce(
        url: String,
        headers: Map<String, String>,
        destination: File,
        bufferSizeBytes: Int,
        append: Boolean,
        expectedLength: Long?,
        onChunk: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        client.newCall(buildRequest(url, headers, range = null)).execute().use { response ->
            if (!response.isSuccessful) {
                throw browserDownloadHttpException(response.code, "downloading content")
            }
            val body =
                response.body
                    ?: throw BrowserDownloadNonRetryableException(
                        "The download response did not contain a body.",
                    )
            return writeBody(
                body = body,
                destination = destination,
                bufferSizeBytes = bufferSizeBytes,
                append = append,
                expectedLength = expectedLength,
                onChunk = onChunk,
                isCancelled = isCancelled,
            )
        }
    }

    private fun writeBody(
        body: okhttp3.ResponseBody,
        destination: File,
        bufferSizeBytes: Int,
        append: Boolean,
        expectedLength: Long?,
        onChunk: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        destination.parentFile?.mkdirs()
        var downloadedBytes = 0L
        val buffer = ByteArray(bufferSizeBytes)
        body.byteStream().use { input ->
            FileOutputStream(destination, append).buffered().use { output ->
                while (true) {
                    if (isCancelled()) {
                        throw CancellationException("Browser download cancelled")
                    }
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    if (read == 0) {
                        continue
                    }
                    downloadedBytes += read.toLong()
                    if (expectedLength != null && downloadedBytes > expectedLength) {
                        throw IOException(
                            "Downloaded body exceeded expected length: $downloadedBytes > $expectedLength",
                        )
                    }
                    output.write(buffer, 0, read)
                    onChunk(read)
                }
                output.flush()
            }
        }
        if (expectedLength != null && downloadedBytes != expectedLength) {
            throw IOException(
                "Downloaded body length does not match expected length: $downloadedBytes != $expectedLength",
            )
        }
        return downloadedBytes
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        range: Pair<Long, Long>?,
        ifRangeValidator: String? = null,
        method: String = "GET",
    ): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (
                name.isNotBlank() &&
                    value.isNotBlank() &&
                    !name.equals("Range", ignoreCase = true) &&
                    !name.equals("If-Range", ignoreCase = true) &&
                    !name.equals("Accept-Encoding", ignoreCase = true)
            ) {
                builder.header(name, value)
            }
        }
        builder.header("Accept-Encoding", "identity")
        if (range != null) {
            builder.header("Range", "bytes=${range.first}-${range.second}")
            ifRangeValidator?.takeIf { value -> value.isNotBlank() }?.let { value ->
                builder.header("If-Range", value)
            }
        }
        return when (method) {
            "GET" -> builder.get().build()
            "HEAD" -> builder.head().build()
            else -> throw IllegalArgumentException("Unsupported browser download method: $method")
        }
    }

    private fun <T> executeWithRetry(
        onAttemptFailure: () -> Unit = {},
        operation: () -> T,
    ): T {
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT) {
            try {
                return operation()
            } catch (error: CancellationException) {
                onAttemptFailure()
                throw error
            } catch (error: BrowserDownloadNonRetryableException) {
                onAttemptFailure()
                throw error
            } catch (error: IOException) {
                lastError = error
                attempt += 1
                onAttemptFailure()
                if (attempt < BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT) {
                    retryDelay(250L * attempt)
                }
            }
        }
        throw lastError ?: IOException("Browser download transport failed")
    }

    private fun rollbackFile(destination: File, initialLength: Long, append: Boolean) {
        if (!destination.exists()) {
            return
        }
        if (!append || initialLength == 0L) {
            destination.delete()
            return
        }
        RandomAccessFile(destination, "rw").use { file -> file.setLength(initialLength) }
    }

    private fun emitNegativeChunkDelta(downloadedBytes: Long, onChunk: (Int) -> Unit) {
        var remaining = downloadedBytes
        while (remaining > 0L) {
            val delta = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            onChunk(-delta)
            remaining -= delta.toLong()
        }
    }

    private data class ContentRange(
        val startInclusive: Long,
        val endInclusive: Long,
        val totalBytes: Long,
    )

    private fun parseContentRange(value: String?): ContentRange {
        val match =
            CONTENT_RANGE_PATTERN.matchEntire(value?.trim().orEmpty())
                ?: throw BrowserDownloadNonRetryableException(
                    "Missing or invalid Content-Range: $value",
                )
        val totalBytes =
            match.groups[3]?.value?.toLongOrNull()
                ?: throw BrowserDownloadNonRetryableException(
                    "Content-Range total length is unknown: $value",
                )
        val startInclusive = match.groups[1]!!.value.toLong()
        val endInclusive = match.groups[2]!!.value.toLong()
        if (endInclusive < startInclusive || totalBytes <= endInclusive) {
            throw BrowserDownloadNonRetryableException(
                "Content-Range bounds are invalid: $value",
            )
        }
        return ContentRange(
            startInclusive = startInclusive,
            endInclusive = endInclusive,
            totalBytes = totalBytes,
        )
    }

    companion object {
        private val CONTENT_RANGE_PATTERN = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
    }
}

private fun browserDownloadHttpException(
    statusCode: Int,
    operation: String,
): IOException {
    val message = "HTTP $statusCode while $operation"
    return if (statusCode in RETRYABLE_BROWSER_DOWNLOAD_HTTP_STATUS_CODES) {
        IOException(message)
    } else {
        BrowserDownloadNonRetryableException(message)
    }
}

private val RETRYABLE_BROWSER_DOWNLOAD_HTTP_STATUS_CODES =
    setOf(408, 425, 429, 500, 502, 503, 504)
