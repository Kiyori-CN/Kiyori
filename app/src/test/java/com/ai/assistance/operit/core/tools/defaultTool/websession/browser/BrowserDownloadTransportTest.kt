package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserDownloadTransportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `protocol list follows the HTTP2 setting`() {
        newTransport(enableHttp2 = true).use { transport ->
            assertEquals(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1), transport.protocols)
        }
        newTransport(enableHttp2 = false).use { transport ->
            assertEquals(listOf(Protocol.HTTP_1_1), transport.protocols)
        }
    }

    @Test
    fun `HEAD metadata is returned without a range probe`() {
        val requestCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/metadata") { exchange ->
                requestCount.incrementAndGet()
                exchange.respond(
                    status = 200,
                    headers =
                        mapOf(
                            "Content-Length" to "12",
                            "Accept-Ranges" to "bytes",
                            "Content-Type" to "video/mp4",
                            "ETag" to "\"metadata-v1\"",
                            "Last-Modified" to "Wed, 29 Jul 2026 09:00:00 GMT",
                        ),
                )
            }
            server.start()

            newTransport().use { transport ->
                val result = transport.probe(server.url("/metadata"))

                assertEquals(server.url("/metadata"), result.finalUrl)
                assertEquals(12L, result.contentLength)
                assertTrue(result.acceptsRanges)
                assertEquals("video/mp4", result.mimeType)
                assertEquals("\"metadata-v1\"", result.etag)
                assertEquals("Wed, 29 Jul 2026 09:00:00 GMT", result.lastModified)
                assertEquals(1, requestCount.get())
            }
        }
    }

    @Test
    fun `If-Range validator prefers a strong ETag and rejects a weak ETag`() {
        assertEquals(
            "\"resource-v2\"",
            resolveBrowserDownloadIfRangeValidator(
                etag = "\"resource-v2\"",
                lastModified = "Wed, 29 Jul 2026 09:00:00 GMT",
            ),
        )
        assertEquals(
            "Wed, 29 Jul 2026 09:00:00 GMT",
            resolveBrowserDownloadIfRangeValidator(
                etag = "W/\"resource-v2\"",
                lastModified = "Wed, 29 Jul 2026 09:00:00 GMT",
            ),
        )
        assertEquals(
            null,
            resolveBrowserDownloadIfRangeValidator(
                etag = "W/\"resource-v2\"",
                lastModified = null,
            ),
        )
    }

    @Test
    fun `probe retries transient HEAD failures before returning metadata`() {
        val requestCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/transient-head") { exchange ->
                if (requestCount.incrementAndGet() < 3) {
                    exchange.respond(status = 503)
                } else {
                    exchange.respond(
                        status = 200,
                        headers =
                            mapOf(
                                "Content-Length" to "7",
                                "Accept-Ranges" to "bytes",
                                "Content-Type" to "application/octet-stream",
                            ),
                    )
                }
            }
            server.start()

            newTransport(retryDelay = {}).use { transport ->
                val result = transport.probe(server.url("/transient-head"))

                assertEquals(7L, result.contentLength)
                assertTrue(result.acceptsRanges)
                assertEquals(3, requestCount.get())
            }
        }
    }

    @Test
    fun `probe permanent HTTP failure performs exactly five attempts`() {
        val requestCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/permanent-probe-failure") { exchange ->
                requestCount.incrementAndGet()
                exchange.respond(status = 503)
            }
            server.start()

            newTransport(retryDelay = {}).use { transport ->
                assertThrows(IOException::class.java) {
                    transport.probe(server.url("/permanent-probe-failure"))
                }
            }

            assertEquals(BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT, requestCount.get())
        }
    }

    @Test
    fun `HEAD 405 is followed by an exact zero byte range probe`() {
        val headCount = AtomicInteger()
        val rangeCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/head-not-allowed") { exchange ->
                when (exchange.requestMethod) {
                    "HEAD" -> {
                        headCount.incrementAndGet()
                        exchange.respond(status = 405)
                    }
                    "GET" -> {
                        rangeCount.incrementAndGet()
                        if (exchange.requestHeaders.getFirst("Range") != "bytes=0-0") {
                            exchange.respond(status = 400)
                        } else {
                            exchange.respond(
                                status = 206,
                                body = byteArrayOf(7),
                                headers =
                                    mapOf(
                                        "Content-Range" to "bytes 0-0/5",
                                        "Content-Type" to "application/octet-stream",
                                    ),
                            )
                        }
                    }
                    else -> exchange.respond(status = 405)
                }
            }
            server.start()

            newTransport().use { transport ->
                val result = transport.probe(server.url("/head-not-allowed"))

                assertEquals(5L, result.contentLength)
                assertTrue(result.acceptsRanges)
                assertEquals("application/octet-stream", result.mimeType)
                assertEquals(1, headCount.get())
                assertEquals(1, rangeCount.get())
            }
        }
    }

    @Test
    fun `probe reports redirect destination and destination MIME type`() {
        LoopbackServer().use { server ->
            server.handle("/redirect") { exchange ->
                exchange.respond(
                    status = 302,
                    headers = mapOf("Location" to server.url("/final")),
                )
            }
            server.handle("/final") { exchange ->
                exchange.respond(
                    status = 200,
                    headers =
                        mapOf(
                            "Content-Length" to "9",
                            "Accept-Ranges" to "bytes",
                            "Content-Type" to "audio/aac",
                        ),
                )
            }
            server.start()

            newTransport().use { transport ->
                val result = transport.probe(server.url("/redirect"))

                assertEquals(server.url("/final"), result.finalUrl)
                assertEquals(9L, result.contentLength)
                assertEquals("audio/aac", result.mimeType)
            }
        }
    }

    @Test
    fun `range download accepts a matching 206 response`() {
        val source = "0123456789".toByteArray(StandardCharsets.UTF_8)
        LoopbackServer().use { server ->
            server.handle("/range") { exchange ->
                if (exchange.requestHeaders.getFirst("Range") != "bytes=2-6") {
                    exchange.respond(status = 400)
                } else {
                    exchange.respond(
                        status = 206,
                        body = source.copyOfRange(2, 7),
                        headers = mapOf("Content-Range" to "bytes 2-6/${source.size}"),
                    )
                }
            }
            server.start()
            val destination = temporaryFolder.newFile("matching-range.part")
            destination.delete()
            var reportedBytes = 0L

            newTransport().use { transport ->
                val downloaded =
                    transport.downloadRangeWithRetry(
                        url = server.url("/range"),
                        headers = emptyMap(),
                        startInclusive = 2L,
                        endInclusive = 6L,
                        destination = destination,
                        append = false,
                        onChunk = { bytes -> reportedBytes += bytes.toLong() },
                    )

                assertEquals(5L, downloaded)
                assertEquals(5L, reportedBytes)
                assertArrayEquals(source.copyOfRange(2, 7), destination.readBytes())
            }
        }
    }

    @Test
    fun `range download sends the frozen If-Range validator`() {
        val source = "0123456789".toByteArray(StandardCharsets.UTF_8)
        val validator = "\"range-v1\""
        LoopbackServer().use { server ->
            server.handle("/if-range") { exchange ->
                if (
                    exchange.requestHeaders.getFirst("Range") != "bytes=2-6" ||
                        exchange.requestHeaders.getFirst("If-Range") != validator
                ) {
                    exchange.respond(status = 400)
                } else {
                    exchange.respond(
                        status = 206,
                        body = source.copyOfRange(2, 7),
                        headers =
                            mapOf(
                                "Content-Range" to "bytes 2-6/${source.size}",
                                "ETag" to validator,
                            ),
                    )
                }
            }
            server.start()
            val destination = temporaryFolder.newFile("if-range.part")
            destination.delete()

            newTransport().use { transport ->
                val downloaded =
                    transport.downloadRangeWithRetry(
                        url = server.url("/if-range"),
                        headers =
                            mapOf(
                                "Range" to "bytes=0-1",
                                "If-Range" to "\"stale\"",
                                "Accept-Encoding" to "gzip",
                            ),
                        startInclusive = 2L,
                        endInclusive = 6L,
                        destination = destination,
                        append = false,
                        expectedTotalBytes = source.size.toLong(),
                        ifRangeValidator = validator,
                    )

                assertEquals(5L, downloaded)
                assertArrayEquals(source.copyOfRange(2, 7), destination.readBytes())
            }
        }
    }

    @Test
    fun `changed If-Range representation stops after one request and removes partial output`() {
        val requestCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/changed-resource") { exchange ->
                requestCount.incrementAndGet()
                exchange.respond(status = 200, body = "new-resource".toByteArray())
            }
            server.start()
            val destination = temporaryFolder.newFile("changed-resource.part")
            destination.delete()

            newTransport(retryDelay = {}).use { transport ->
                assertThrows(BrowserDownloadRepresentationChangedException::class.java) {
                    transport.downloadRangeWithRetry(
                        url = server.url("/changed-resource"),
                        headers = emptyMap(),
                        startInclusive = 0L,
                        endInclusive = 3L,
                        destination = destination,
                        append = false,
                        ifRangeValidator = "\"old-resource\"",
                    )
                }
            }

            assertEquals(1, requestCount.get())
            assertFalse(destination.exists())
        }
    }

    @Test
    fun `range download rejects servers that ignore Range`() {
        val requestCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/ignored-range") { exchange ->
                requestCount.incrementAndGet()
                exchange.respond(status = 200, body = "whole-file".toByteArray())
            }
            server.start()
            val destination = temporaryFolder.newFile("ignored-range.part")
            destination.delete()

            newTransport(retryDelay = {}).use { transport ->
                assertThrows(IOException::class.java) {
                    transport.downloadRangeWithRetry(
                        url = server.url("/ignored-range"),
                        headers = emptyMap(),
                        startInclusive = 0L,
                        endInclusive = 3L,
                        destination = destination,
                        append = false,
                    )
                }
            }

            assertEquals(1, requestCount.get())
            assertFalse(destination.exists())
        }
    }

    @Test
    fun `transport errors do not expose signed download URLs`() {
        val requestCount = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/private-download") { exchange ->
                requestCount.incrementAndGet()
                exchange.respond(status = 404)
            }
            server.start()
            val signedUrl = server.url("/private-download?token=secret-value")

            newTransport(retryDelay = {}).use { transport ->
                val error =
                    assertThrows(BrowserDownloadNonRetryableException::class.java) {
                        transport.readTextWithRetry(signedUrl)
                    }

                assertFalse(error.message.orEmpty().contains("secret-value"))
                assertFalse(error.message.orEmpty().contains(signedUrl))
            }

            assertEquals(1, requestCount.get())
        }
    }

    @Test
    fun `range download rejects missing and mismatched Content-Range`() {
        LoopbackServer().use { server ->
            server.handle("/missing-content-range") { exchange ->
                exchange.respond(status = 206, body = byteArrayOf(1, 2, 3, 4))
            }
            server.handle("/mismatched-content-range") { exchange ->
                exchange.respond(
                    status = 206,
                    body = byteArrayOf(1, 2, 3, 4),
                    headers = mapOf("Content-Range" to "bytes 1-4/10"),
                )
            }
            server.handle("/wrong-total") { exchange ->
                exchange.respond(
                    status = 206,
                    body = byteArrayOf(1, 2, 3, 4),
                    headers = mapOf("Content-Range" to "bytes 0-3/11"),
                )
            }
            server.start()

            newTransport(retryDelay = {}).use { transport ->
                listOf(
                    "/missing-content-range" to null,
                    "/mismatched-content-range" to null,
                    "/wrong-total" to 10L,
                ).forEachIndexed { index, (path, expectedTotalBytes) ->
                    val destination = temporaryFolder.newFile("invalid-content-range-$index.part")
                    destination.delete()
                    assertThrows(IOException::class.java) {
                        transport.downloadRangeWithRetry(
                            url = server.url(path),
                            headers = emptyMap(),
                            startInclusive = 0L,
                            endInclusive = 3L,
                            destination = destination,
                            append = false,
                            expectedTotalBytes = expectedTotalBytes,
                        )
                    }
                    assertFalse(destination.exists())
                }
            }
        }
    }

    @Test
    fun `range download rejects short and overlong bodies and rolls progress back`() {
        LoopbackServer().use { server ->
            server.handle("/short-body") { exchange ->
                exchange.respond(
                    status = 206,
                    body = byteArrayOf(1, 2, 3),
                    headers = mapOf("Content-Range" to "bytes 0-3/10"),
                )
            }
            server.handle("/long-body") { exchange ->
                exchange.respond(
                    status = 206,
                    body = byteArrayOf(1, 2, 3, 4, 5),
                    headers = mapOf("Content-Range" to "bytes 0-3/10"),
                )
            }
            server.start()

            newTransport(retryDelay = {}).use { transport ->
                listOf("/short-body", "/long-body").forEachIndexed { index, path ->
                    val destination = temporaryFolder.newFile("invalid-body-$index.part")
                    destination.delete()
                    var reportedBytes = 0L
                    assertThrows(IOException::class.java) {
                        transport.downloadRangeWithRetry(
                            url = server.url(path),
                            headers = emptyMap(),
                            startInclusive = 0L,
                            endInclusive = 3L,
                            destination = destination,
                            append = false,
                            onChunk = { bytes -> reportedBytes += bytes.toLong() },
                        )
                    }
                    assertEquals(0L, reportedBytes)
                    assertFalse(destination.exists())
                }
            }
        }
    }

    @Test
    fun `fifth request succeeds after four HTTP failures`() {
        val requestCount = AtomicInteger()
        val delays = mutableListOf<Long>()
        LoopbackServer().use { server ->
            server.handle("/eventual-success") { exchange ->
                if (requestCount.incrementAndGet() < BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT) {
                    exchange.respond(status = 500)
                } else {
                    exchange.respond(
                        status = 200,
                        body = "ready".toByteArray(StandardCharsets.UTF_8),
                        headers = mapOf("Content-Type" to "text/plain"),
                    )
                }
            }
            server.start()

            newTransport(retryDelay = { delay -> delays.add(delay) }).use { transport ->
                val result = transport.readTextWithRetry(server.url("/eventual-success"))

                assertEquals("ready", result.content)
                assertEquals("text/plain", result.mimeType)
                assertEquals(BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT, requestCount.get())
                assertEquals(listOf(250L, 500L, 750L, 1000L), delays)
            }
        }
    }

    @Test
    fun `permanent HTTP failure performs exactly five attempts`() {
        val requestCount = AtomicInteger()
        val delays = mutableListOf<Long>()
        LoopbackServer().use { server ->
            server.handle("/permanent-failure") { exchange ->
                requestCount.incrementAndGet()
                exchange.respond(status = 503)
            }
            server.start()

            newTransport(retryDelay = { delay -> delays.add(delay) }).use { transport ->
                assertThrows(IOException::class.java) {
                    transport.readTextWithRetry(server.url("/permanent-failure"))
                }
            }

            assertEquals(BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT, requestCount.get())
            assertEquals(BROWSER_DOWNLOAD_TRANSPORT_RETRY_COUNT - 1, delays.size)
        }
    }

    @Test
    fun `M3U8 text and resource downloads preserve response content`() {
        val playlist = "#EXTM3U\n#EXTINF:4,\nsegment.ts"
        val segment = byteArrayOf(9, 8, 7, 6)
        LoopbackServer().use { server ->
            server.handle("/playlist.m3u8") { exchange ->
                exchange.respond(
                    status = 200,
                    body = playlist.toByteArray(StandardCharsets.UTF_8),
                    headers = mapOf("Content-Type" to "application/vnd.apple.mpegurl"),
                )
            }
            server.handle("/segment.ts") { exchange ->
                exchange.respond(
                    status = 200,
                    body = segment,
                    headers = mapOf("Content-Type" to "video/mp2t"),
                )
            }
            server.start()
            val destination = temporaryFolder.newFile("segment.ts.part")
            destination.delete()

            newTransport().use { transport ->
                val textResult = transport.readTextWithRetry(server.url("/playlist.m3u8"))
                val resourceBytes =
                    transport.downloadResourceWithRetry(
                        url = server.url("/segment.ts"),
                        headers = emptyMap(),
                        destination = destination,
                    )

                assertEquals(playlist, textResult.content)
                assertEquals("application/vnd.apple.mpegurl", textResult.mimeType)
                assertEquals(segment.size.toLong(), resourceBytes)
                assertArrayEquals(segment, destination.readBytes())
            }
        }
    }

    @Test
    fun `text responses stop at the configured byte limit`() {
        LoopbackServer().use { server ->
            server.handle("/oversized.txt") { exchange ->
                exchange.respond(
                    status = 200,
                    body = "123456789".toByteArray(StandardCharsets.UTF_8),
                    headers = mapOf("Content-Type" to "text/plain"),
                )
            }
            server.start()

            newTransport(retryDelay = {}).use { transport ->
                assertThrows(BrowserDownloadNonRetryableException::class.java) {
                    transport.readTextWithRetry(
                        url = server.url("/oversized.txt"),
                        maxBytes = 8L,
                    )
                }
            }
        }
    }

    @Test
    fun `failed append restores the original file and progress`() {
        LoopbackServer().use { server ->
            server.handle("/short-stream") { exchange ->
                exchange.respond(status = 200, body = byteArrayOf(1, 2))
            }
            server.start()
            val destination = temporaryFolder.newFile("append.part")
            destination.writeText("old", StandardCharsets.UTF_8)
            var reportedBytes = 0L

            newTransport(retryDelay = {}).use { transport ->
                assertThrows(IOException::class.java) {
                    transport.downloadStreamWithRetry(
                        url = server.url("/short-stream"),
                        headers = emptyMap(),
                        destination = destination,
                        bufferSizeBytes = 2,
                        append = true,
                        expectedLength = 4L,
                        onChunk = { bytes -> reportedBytes += bytes.toLong() },
                    )
                }
            }

            assertEquals("old", destination.readText(StandardCharsets.UTF_8))
            assertEquals(0L, reportedBytes)
        }
    }

    private fun newTransport(
        enableHttp2: Boolean = true,
        retryDelay: (Long) -> Unit = {},
    ): BrowserDownloadTransport =
        BrowserDownloadTransport(
            config =
                BrowserDownloadTransportConfig(
                    maxConcurrentTasks = 3,
                    normalThreadCount = 6,
                    m3u8ThreadCount = 16,
                    chunkSizeKb = DEFAULT_BROWSER_DOWNLOAD_CHUNK_SIZE_KB,
                    enableHttp2 = enableHttp2,
                ),
            retryDelay = retryDelay,
        )

    private class LoopbackServer : Closeable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        fun handle(path: String, handler: (HttpExchange) -> Unit) {
            server.createContext(path) { exchange -> handler(exchange) }
        }

        fun start() {
            server.start()
        }

        fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

        override fun close() {
            server.stop(0)
        }
    }
}

private fun HttpExchange.respond(
    status: Int,
    body: ByteArray = ByteArray(0),
    headers: Map<String, String> = emptyMap(),
) {
    headers.forEach { (name, value) -> responseHeaders.set(name, value) }
    if (requestMethod == "HEAD") {
        sendResponseHeaders(status, -1L)
        close()
        return
    }
    sendResponseHeaders(status, body.size.toLong())
    responseBody.use { output -> output.write(body) }
}
