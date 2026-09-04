package com.ai.assistance.operit.util

import java.io.File
import java.io.EOFException
import java.io.IOException
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

object HttpMultiPartDownloader {
    data class ProbeResult(
        val contentLength: Long,
        val acceptRanges: Boolean
    )

    data class SegmentPlan(
        val index: Int,
        val startInclusive: Long,
        val endInclusive: Long
    )

    fun download(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
        connectionFactory: (URL) -> URLConnection,
        threadCount: Int = 4,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val safeThreads = threadCount.coerceIn(1, 8)

        val meta = probeDownload(url, headers, connectionFactory)
        val total = meta.contentLength
        val supportsRanges = meta.acceptRanges

        if (total <= 0L || !supportsRanges || safeThreads == 1) {
            downloadSingle(url, dest, headers, connectionFactory, total, onProgress)
            return
        }

        downloadMulti(url, dest, headers, connectionFactory, total, safeThreads, onProgress)
    }

    fun probeDownload(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectionFactory: (URL) -> URLConnection,
    ): ProbeResult {
        return transfer(url, headers, "probe", "bytes=0-0", connectionFactory) { connection, attempt ->
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) {
                ProbeResult(connection.getHeaderFieldLong("Content-Length", -1L), false)
            } else if (status == HttpURLConnection.HTTP_PARTIAL) {
                val range = requireContentRange(connection, attempt, headers)
                if (range.first != 0L || range.second != 0L) {
                    throw protocolFailure(connection, attempt, "Invalid probe Content-Range", headers)
                }
                ProbeResult(range.third, true)
            } else if (status == 416 && connection.getHeaderField("Content-Range") == "bytes */0") {
                ProbeResult(0L, false)
            } else {
                throw HttpTransferException.http(connection.url, "probe", status, attempt, "bytes=0-0", headers)
            }
        }
    }

    fun buildSegmentPlan(totalBytes: Long, threadCount: Int): List<SegmentPlan> {
        if (totalBytes <= 0L) {
            return emptyList()
        }
        val safeThreads = threadCount.coerceIn(1, 8)
        val partSize = (totalBytes + safeThreads - 1) / safeThreads
        return buildList(safeThreads) {
            for (part in 0 until safeThreads) {
                val start = part * partSize
                val end = minOf(totalBytes - 1, (part + 1) * partSize - 1)
                if (start <= end) {
                    add(
                        SegmentPlan(
                            index = part,
                            startInclusive = start,
                            endInclusive = end
                        )
                    )
                }
            }
        }
    }

    fun downloadSegment(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
        connectionFactory: (URL) -> URLConnection,
        startInclusive: Long = 0L,
        endInclusive: Long? = null,
        append: Boolean = false,
        onChunk: ((chunkBytes: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ) {
        require(startInclusive >= 0L && (endInclusive == null || endInclusive >= startInclusive))
        val rangeValue = if (startInclusive > 0L || endInclusive != null) {
            "bytes=$startInclusive-${endInclusive ?: ""}"
        } else null
        val originalLength = if (append && dest.exists()) dest.length() else 0L
        transfer(url, headers, "download", rangeValue, connectionFactory, isCancelled) { conn, attempt ->
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw HttpTransferException.http(conn.url, "download", code, attempt, rangeValue, headers)
            }
            val expectedLength = if (rangeValue != null) {
                if (code != HttpURLConnection.HTTP_PARTIAL) throw protocolFailure(conn, attempt, "Range ignored", headers)
                val range = requireContentRange(conn, attempt, headers)
                if (range.first != startInclusive || (endInclusive != null && range.second != endInclusive)) {
                    throw protocolFailure(conn, attempt, "Content-Range mismatch", headers)
                }
                range.second - range.first + 1
            } else {
                if (code != HttpURLConnection.HTTP_OK) throw protocolFailure(conn, attempt, "Unexpected partial response", headers)
                conn.getHeaderFieldLong("Content-Length", -1L)
            }
            dest.parentFile?.mkdirs()
            if (append) RandomAccessFile(dest, "rw").use { it.setLength(originalLength) }
            var received = 0L
            try {
                conn.inputStream.use { input ->
                    FileOutputStream(dest, append).buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (Thread.currentThread().isInterrupted || isCancelled?.invoke() == true) {
                                throw InterruptedException("Download cancelled")
                            }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            if (expectedLength >= 0 && received + read > expectedLength) throw protocolFailure(conn, attempt, "Body exceeds declared range", headers)
                            output.write(buffer, 0, read)
                            received += read
                            onChunk?.invoke(read)
                        }
                        output.flush()
                    }
                }
                if (expectedLength >= 0 && received != expectedLength) throw EOFException("Incomplete HTTP body")
            } catch (error: Exception) {
                if (received > 0L) {
                    var remaining = received
                    while (remaining > 0L) {
                        val chunk = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
                        onChunk?.invoke(-chunk)
                        remaining -= chunk
                    }
                }
                throw error
            }
        }
    }

    private fun requireContentRange(connection: HttpURLConnection, attempt: Int, headers: Map<String, String>): Triple<Long, Long, Long> {
        val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
        val values = match?.groupValues?.drop(1)?.mapNotNull { it.toLongOrNull() }
        if (values == null || values.size != 3 || values[0] > values[1] || values[1] >= values[2]) {
            throw protocolFailure(connection, attempt, "Invalid Content-Range", headers)
        }
        return Triple(values[0], values[1], values[2])
    }

    private fun protocolFailure(connection: HttpURLConnection, attempt: Int, reason: String, headers: Map<String, String>) =
        HttpTransferException("INVALID_HTTP_RESPONSE", HttpTransferException.endpoint(connection.url), reason, attempt, false, "ProtocolValidation", headers = headers)

    private fun <Result> transfer(
        url: String,
        headers: Map<String, String>,
        phase: String,
        range: String?,
        connectionFactory: (URL) -> URLConnection,
        isCancelled: (() -> Boolean)? = null,
        operation: (HttpURLConnection, Int) -> Result,
    ): Result {
        for (attempt in 1..3) {
            if (Thread.currentThread().isInterrupted || isCancelled?.invoke() == true) throw InterruptedException("Download cancelled")
            var connection: HttpURLConnection? = null
            try {
                connection = (connectionFactory(URL(url)) as HttpURLConnection).apply {
                    requestMethod = "GET"
                    applyHeaders(this, headers)
                    setRequestProperty("Accept-Encoding", "identity")
                    if (range != null) setRequestProperty("Range", range)
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                return operation(connection, attempt)
            } catch (error: IOException) {
                val failure = HttpTransferException.from(error, connection?.url ?: URL(url), phase, attempt, range, headers)
                if (!failure.retryable || attempt == 3) throw failure
            } finally {
                connection?.disconnect()
            }
            Thread.sleep(350L * (1L shl (attempt - 1)))
        }
        error("Transfer retry loop exhausted")
    }

    private fun downloadSingle(
        url: String,
        dest: File,
        headers: Map<String, String>,
        connectionFactory: (URL) -> URLConnection,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        val total = AtomicLong(totalBytes)
        val downloaded = AtomicLong(0L)
        downloadSegment(
            url = url,
            dest = dest,
            headers = headers,
            connectionFactory = connectionFactory,
            startInclusive = 0L,
            endInclusive = null,
            append = false,
            onChunk = { chunk ->
                val now = downloaded.addAndGet(chunk.toLong())
                onProgress?.invoke(now, total.get())
            }
        )
    }

    private fun downloadMulti(
        url: String,
        dest: File,
        headers: Map<String, String>,
        connectionFactory: (URL) -> URLConnection,
        totalBytes: Long,
        threadCount: Int,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        dest.parentFile?.mkdirs()

        // Pre-allocate file
        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val downloaded = AtomicLong(0L)
        val firstError = AtomicReference<Throwable?>(null)
        val pool = Executors.newFixedThreadPool(threadCount)
        val plans = buildSegmentPlan(totalBytes, threadCount)
        val latch = CountDownLatch(plans.size)

        for (segment in plans) {
            val start = segment.startInclusive
            val end = segment.endInclusive

            pool.execute {
                val partFile = File(dest.parentFile, "${dest.name}.part.${segment.index}")
                try {
                    partFile.delete()
                    downloadSegment(
                        url = url,
                        dest = partFile,
                        headers = headers,
                        connectionFactory = connectionFactory,
                        startInclusive = start,
                        endInclusive = end,
                        append = false,
                        isCancelled = { firstError.get() != null },
                        onChunk = { chunk ->
                            val now = downloaded.addAndGet(chunk.toLong())
                            onProgress?.invoke(now, totalBytes)
                        }
                    )
                    RandomAccessFile(dest, "rw").use { raf ->
                        raf.seek(start)
                        partFile.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                raf.write(buffer, 0, read)
                            }
                        }
                    }
                    partFile.delete()
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                } finally {
                    partFile.delete()
                    latch.countDown()
                }
            }
        }

        var interrupted = false
        try {
            while (true) {
                try {
                    latch.await()
                    break
                } catch (error: InterruptedException) {
                    interrupted = true
                    firstError.compareAndSet(null, error)
                    pool.shutdownNow()
                }
            }
        } finally {
            val err = firstError.get()
            if (err != null) {
                pool.shutdownNow()
            } else {
                pool.shutdown()
            }
            if (interrupted) Thread.currentThread().interrupt()
        }

        val err = firstError.get()
        if (err != null) {
            try {
                dest.delete()
            } catch (_: Exception) {
            }
            throw err
        }

        onProgress?.invoke(totalBytes, totalBytes)
    }

    private fun applyHeaders(conn: HttpURLConnection, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        headers.forEach { (rawName, rawValue) ->
            val name = sanitizeHeaderName(rawName) ?: return@forEach
            val value = sanitizeHeaderValue(rawValue)
            conn.setRequestProperty(name, value)
        }
    }

    private fun sanitizeHeaderName(name: String?): String? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("\r") || trimmed.contains("\n")) return null
        if (trimmed.contains(":")) return null
        return trimmed
    }

    private fun sanitizeHeaderValue(value: String?): String {
        return value
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?: ""
    }
}
