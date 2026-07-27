package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

internal const val BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT = 5
internal const val BROWSER_DOWNLOAD_TRANSPORT_RESOURCE_BUFFER_BYTES = 64 * 1024

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
    private val client =
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
            .build()

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
            throw IOException("HTTP $code while probing $url")
        }

        val headResult =
            BrowserDownloadProbeResult(
                finalUrl = head.request.url.toString(),
                contentLength = head.header("Content-Length")?.toLongOrNull() ?: -1L,
                acceptsRanges = head.header("Accept-Ranges").equals("bytes", ignoreCase = true),
                mimeType = head.header("Content-Type").orEmpty(),
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
    ): BrowserDownloadTextResult =
        executeWithRetry {
            val request = buildRequest(url, headers, range = null)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} while reading $url")
                }
                val body = response.body ?: throw IOException("Empty response body while reading $url")
                BrowserDownloadTextResult(
                    finalUrl = response.request.url.toString(),
                    mimeType = response.header("Content-Type") ?: body.contentType()?.toString().orEmpty(),
                    content = body.string(),
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
                    )
                }
                else -> throw IOException("HTTP ${response.code} while probing range for $url")
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
        onChunk: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        client.newCall(
            buildRequest(
                url = url,
                headers = headers,
                range = startInclusive to endInclusive,
            ),
        ).execute().use { response ->
            if (response.code != 206) {
                throw IOException("Expected HTTP 206 for range $startInclusive-$endInclusive, received ${response.code}")
            }
            val contentRange = parseContentRange(response.header("Content-Range"))
            if (
                contentRange.startInclusive != startInclusive ||
                    contentRange.endInclusive != endInclusive
            ) {
                throw IOException(
                    "Range response does not match requested bytes: ${response.header("Content-Range")}",
                )
            }
            if (expectedTotalBytes != null && contentRange.totalBytes != expectedTotalBytes) {
                throw IOException(
                    "Range response total length does not match the probed resource: " +
                        "${contentRange.totalBytes} != $expectedTotalBytes",
                )
            }
            val expectedLength = endInclusive - startInclusive + 1L
            val body = response.body ?: throw IOException("Empty range response body for $url")
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
                throw IOException("HTTP ${response.code} while downloading $url")
            }
            val body = response.body ?: throw IOException("Empty download response body for $url")
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
        method: String = "GET",
    ): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank() && !name.equals("Range", ignoreCase = true)) {
                builder.header(name, value)
            }
        }
        builder.header("Accept-Encoding", "identity")
        if (range != null) {
            builder.header("Range", "bytes=${range.first}-${range.second}")
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
        val match = CONTENT_RANGE_PATTERN.matchEntire(value?.trim().orEmpty())
            ?: throw IOException("Missing or invalid Content-Range: $value")
        val totalBytes = match.groups[3]?.value?.toLongOrNull()
            ?: throw IOException("Content-Range total length is unknown: $value")
        val startInclusive = match.groups[1]!!.value.toLong()
        val endInclusive = match.groups[2]!!.value.toLong()
        if (endInclusive < startInclusive || totalBytes <= endInclusive) {
            throw IOException("Content-Range bounds are invalid: $value")
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
