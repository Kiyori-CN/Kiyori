package com.ai.assistance.operit.util

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpMultiPartDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadUsesTheInjectedConnectionFactoryForProbeAndEveryRange() {
        val source = "0123456789".toByteArray(StandardCharsets.UTF_8)
        val factoryCalls = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/file") { exchange ->
                when (exchange.requestMethod) {
                    "HEAD" ->
                        exchange.respond(
                            status = 200,
                            headers =
                                mapOf(
                                    "Content-Length" to source.size.toString(),
                                    "Accept-Ranges" to "bytes",
                                ),
                        )
                    "GET" -> {
                        val range = exchange.requestHeaders.getFirst("Range")
                        val bounds = parseRange(range)
                        exchange.respond(
                            status = 206,
                            body = source.copyOfRange(bounds.first, bounds.last + 1),
                            headers =
                                mapOf(
                                    "Content-Range" to
                                        "bytes ${bounds.first}-${bounds.last}/${source.size}",
                                ),
                        )
                    }
                    else -> exchange.respond(status = 405)
                }
            }
            server.start()
            val destination = temporaryFolder.newFile("download.bin")
            destination.delete()
            val connectionFactory: (URL) -> URLConnection = { url ->
                factoryCalls.incrementAndGet()
                url.openConnection(Proxy.NO_PROXY)
            }

            HttpMultiPartDownloader.download(
                url = server.url("/file"),
                dest = destination,
                connectionFactory = connectionFactory,
                threadCount = 2,
            )

            assertEquals(String(source, StandardCharsets.UTF_8), destination.readText(StandardCharsets.UTF_8))
            assertEquals(3, factoryCalls.get())
        }
    }

    @Test
    fun multiPartDownloadRejectsAFullResponseToARangeRequest() {
        val source = "0123456789".toByteArray(StandardCharsets.UTF_8)
        LoopbackServer().use { server ->
            server.handle("/file") { exchange ->
                when (exchange.requestMethod) {
                    "HEAD" ->
                        exchange.respond(
                            status = 200,
                            headers =
                                mapOf(
                                    "Content-Length" to source.size.toString(),
                                    "Accept-Ranges" to "bytes",
                                ),
                        )
                    "GET" -> if (exchange.requestHeaders.getFirst("Range") == "bytes=0-0") {
                        exchange.respond(status = 206, body = source.copyOfRange(0, 1), headers = mapOf("Content-Range" to "bytes 0-0/10"))
                    } else exchange.respond(status = 200, body = source)
                    else -> exchange.respond(status = 405)
                }
            }
            server.start()
            val destination = temporaryFolder.newFile("range-ignored.bin")
            destination.delete()

            try {
                HttpMultiPartDownloader.download(
                    url = server.url("/file"),
                    dest = destination,
                    connectionFactory = { url -> url.openConnection(Proxy.NO_PROXY) },
                    threadCount = 2,
                )
                fail("A server that ignores Range must not produce a multi-part file")
            } catch (error: HttpTransferException) {
                assertEquals("INVALID_HTTP_RESPONSE", error.code)
                assertFalse(destination.exists())
            }
        }
    }

    @Test(timeout = 5000)
    fun tinyFileUsesActualSegmentCountAndPreservesHeaders() {
        LoopbackServer().use { server ->
            server.handle("/tiny") { exchange ->
                assertEquals("GET", exchange.requestMethod)
                assertEquals("https://www.bilibili.com/", exchange.requestHeaders.getFirst("Referer"))
                assertEquals("test-browser", exchange.requestHeaders.getFirst("User-Agent"))
                exchange.respond(206, byteArrayOf(42), mapOf("Content-Range" to "bytes 0-0/1"))
            }
            server.start()
            val destination = temporaryFolder.newFile("tiny.bin")
            HttpMultiPartDownloader.download(server.url("/tiny"), destination,
                mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to "test-browser"),
                { it.openConnection(Proxy.NO_PROXY) }, threadCount = 8)
            assertEquals(1, destination.length())
            assertEquals(42, destination.readBytes()[0].toInt())
        }
    }

    @Test
    fun forbiddenProbeStopsWithoutRetryAndRedactsSignedQuery() {
        val requests = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/denied") { exchange -> requests.incrementAndGet(); exchange.respond(403) }
            server.start()
            try {
                HttpMultiPartDownloader.probeDownload(server.url("/denied?token=private"), connectionFactory = { it.openConnection(Proxy.NO_PROXY) })
                fail("Expected 403")
            } catch (error: HttpTransferException) {
                assertEquals("HTTP_FORBIDDEN", error.code)
                assertEquals(403, error.httpStatus)
                assertEquals("probe", error.phase)
                assertFalse(error.message!!.contains("private"))
                assertEquals(1, requests.get())
            }
        }
    }

    @Test
    fun retryableRangeFailureRetainsRangeAndProducesExactBytes() {
        val attempts = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/retry") { exchange ->
                assertEquals("bytes=2-5", exchange.requestHeaders.getFirst("Range"))
                if (attempts.incrementAndGet() == 1) exchange.respond(503)
                else exchange.respond(206, "2345".toByteArray(), mapOf("Content-Range" to "bytes 2-5/10"))
            }
            server.start()
            val destination = temporaryFolder.newFile("retry.bin")
            HttpMultiPartDownloader.downloadSegment(server.url("/retry"), destination, connectionFactory = { it.openConnection(Proxy.NO_PROXY) }, startInclusive = 2, endInclusive = 5)
            assertEquals("2345", destination.readText())
            assertEquals(2, attempts.get())
        }
    }

    @Test
    fun incorrectContentRangeFailsWithoutRetry() {
        LoopbackServer().use { server ->
            server.handle("/mismatch") { it.respond(206, "2345".toByteArray(), mapOf("Content-Range" to "bytes 3-6/10")) }
            server.start()
            try {
                HttpMultiPartDownloader.downloadSegment(server.url("/mismatch"), temporaryFolder.newFile(), connectionFactory = { it.openConnection(Proxy.NO_PROXY) }, startInclusive = 2, endInclusive = 5)
                fail("Expected range mismatch")
            } catch (error: HttpTransferException) {
                assertEquals("INVALID_HTTP_RESPONSE", error.code)
                assertEquals(1, error.attempt)
                assertTrue(error.message!!.contains("Content-Range mismatch"))
            }
        }
    }

    @Test
    fun truncatedAttemptRollsBackProgressAndAppendedBytesBeforeRetry() {
        val attempts = AtomicInteger()
        val progress = AtomicLong()
        LoopbackServer().use { server ->
            server.handle("/truncated") { exchange ->
                val body = if (attempts.incrementAndGet() == 1) "2" else "2345"
                exchange.respond(206, body.toByteArray(), mapOf("Content-Range" to "bytes 2-5/10"))
            }
            server.start()
            val destination = temporaryFolder.newFile("append.bin")
            destination.writeText("01")
            HttpMultiPartDownloader.downloadSegment(server.url("/truncated"), destination,
                connectionFactory = { it.openConnection(Proxy.NO_PROXY) }, startInclusive = 2,
                endInclusive = 5, append = true, onChunk = { progress.addAndGet(it.toLong()) })
            assertEquals("012345", destination.readText())
            assertEquals(4L, progress.get())
            assertEquals(2, attempts.get())
        }
    }

    @Test
    fun serverWithoutRangesUsesOneWholeBodyAfterProbe() {
        val requests = AtomicInteger()
        LoopbackServer().use { server ->
            server.handle("/whole") { exchange ->
                requests.incrementAndGet()
                exchange.respond(200, "whole".toByteArray())
            }
            server.start()
            val destination = temporaryFolder.newFile("whole.bin")
            HttpMultiPartDownloader.download(server.url("/whole"), destination,
                connectionFactory = { it.openConnection(Proxy.NO_PROXY) })
            assertEquals("whole", destination.readText())
            assertEquals(2, requests.get())
        }
    }

    @Test
    fun zeroLengthResourceAcceptsAnExplicitEmptyRangeResponse() {
        LoopbackServer().use { server ->
            server.handle("/empty") { exchange ->
                if (exchange.requestHeaders.getFirst("Range") != null) {
                    exchange.respond(416, headers = mapOf("Content-Range" to "bytes */0"))
                } else exchange.respond(200)
            }
            server.start()
            val destination = temporaryFolder.newFile("empty.bin")
            destination.writeText("previous")
            HttpMultiPartDownloader.download(server.url("/empty"), destination,
                connectionFactory = { it.openConnection(Proxy.NO_PROXY) })
            assertEquals(0L, destination.length())
        }
    }

    @Test(timeout = 5000)
    fun interruptedMultiPartWaitsForWorkersAndCleansPartialFiles() {
        val caller = Thread.currentThread()
        LoopbackServer().use { server ->
            server.handle("/cancel") { exchange ->
                val bounds = parseRange(exchange.requestHeaders.getFirst("Range"))
                exchange.respond(206, ByteArray(bounds.count()), mapOf("Content-Range" to "bytes ${bounds.first}-${bounds.last}/10"))
            }
            server.start()
            val destination = temporaryFolder.newFile("cancel.bin")
            try {
                HttpMultiPartDownloader.download(server.url("/cancel"), destination,
                    connectionFactory = { it.openConnection(Proxy.NO_PROXY) }, threadCount = 2,
                    onProgress = { _, _ -> caller.interrupt() })
                fail("Expected interruption")
            } catch (_: InterruptedException) {
                assertTrue(caller.isInterrupted)
                assertFalse(destination.exists())
                assertFalse(temporaryFolder.root.listFiles().orEmpty().any { it.name.contains(".part.") })
            } finally {
                Thread.interrupted()
            }
        }
    }

    private fun parseRange(value: String?): IntRange {
        requireNotNull(value)
        val bounds = value.removePrefix("bytes=").split('-')
        return bounds[0].toInt()..bounds[1].toInt()
    }

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
