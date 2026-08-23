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
import org.junit.Assert.assertEquals
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
                    "GET" -> exchange.respond(status = 200, body = source)
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
            } catch (error: RuntimeException) {
                assertEquals("Multi-part download failed", error.message)
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
