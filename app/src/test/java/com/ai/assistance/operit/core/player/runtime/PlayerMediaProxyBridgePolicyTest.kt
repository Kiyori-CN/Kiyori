package com.ai.assistance.operit.core.player.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class PlayerMediaProxyBridgePolicyTest {
    @Test
    fun `only capability URLs are recognized as loopback media bridges`() {
        assertTrue(isPlayerMediaProxyBridgeTarget("http://127.0.0.1:39123/_kiyori_player/01234567890123456789012345678901"))
        assertFalse(isPlayerMediaProxyBridgeTarget("https://127.0.0.1:39123/_kiyori_player/01234567890123456789012345678901"))
        assertFalse(isPlayerMediaProxyBridgeTarget("http://127.0.0.1:39123/media"))
        assertFalse(isPlayerMediaProxyBridgeTarget("http://localhost:39123/01234567890123456789012345678901"))
        assertFalse(isPlayerMediaProxyBridgeTarget("http://127.0.0.1:39123/_kiyori_player/01234567890123456789012345678901-extra"))
        assertFalse(isPlayerMediaProxyBridgeTarget("http://[::1]:39123/_kiyori_player/01234567890123456789012345678901"))
    }

    @Test
    fun `bridge accepts ipv4 loopback requests and closes its listener`() {
        val upstream =
            ServerSocket(
                0,
                1,
                InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)),
            )
        val upstreamDone = CountDownLatch(1)
        val upstreamThread =
            Thread {
                try {
                    upstream.accept().use { socket ->
                        readHttpRequest(socket)
                        val body = "bridge-ok".toByteArray()
                        socket.getOutputStream().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(),
                            )
                            output.write(body)
                            output.flush()
                        }
                    }
                } finally {
                    upstreamDone.countDown()
                }
            }
        upstreamThread.start()

        val bridge =
            PlayerMediaStreamBridge(
                targetUrl = "http://127.0.0.1:${upstream.localPort}/video.mp4",
                requestHeaders = emptyMap(),
                proxySelector = object : ProxySelector() {
                    override fun select(uri: java.net.URI?): List<java.net.Proxy> =
                        listOf(java.net.Proxy.NO_PROXY)

                    override fun connectFailed(
                        uri: java.net.URI?,
                        sa: java.net.SocketAddress?,
                        ioe: java.io.IOException?,
                    ) = Unit
                },
            )
        val bridgeUrl = bridge.start()
        try {
            val connection = URL(bridgeUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            assertTrue(isPlayerMediaProxyBridgeTarget(bridgeUrl))
            assertTrue(connection.inputStream.readBytes().contentEquals("bridge-ok".toByteArray()))
            assertTrue(upstreamDone.await(2, TimeUnit.SECONDS))
        } finally {
            bridge.close()
            upstream.close()
            upstreamThread.join(2_000)
        }

        val closedConnection = URL(bridgeUrl).openConnection() as java.net.HttpURLConnection
        closedConnection.connectTimeout = 500
        closedConnection.readTimeout = 500
        assertThrowsConnectionFailure { closedConnection.connect() }
    }

    @Test
    fun `bridge preserves upstream http status instead of collapsing it into eof`() {
        val upstream = ServerSocket(0, 1, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        val diagnostics = CopyOnWriteArrayList<String>()
        val upstreamThread = Thread {
            upstream.accept().use { socket ->
                readHttpRequest(socket)
                socket.getOutputStream().use { output ->
                    val body = "upstream-private-error".toByteArray()
                    output.write(
                        "HTTP/1.1 503 Service Unavailable\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            .toByteArray(),
                    )
                    output.write(body)
                    output.flush()
                }
            }
        }
        upstreamThread.start()
        val bridge =
            PlayerMediaStreamBridge(
                targetUrl = "http://127.0.0.1:${upstream.localPort}/video.mp4",
                requestHeaders = emptyMap(),
                proxySelector = noProxySelector(),
                diagnostic = { _, message -> diagnostics += message },
            )
        try {
            val connection = URL(bridge.start()).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            assertEquals(503, connection.responseCode)
            assertTrue(connection.errorStream == null || connection.errorStream.readBytes().isEmpty())
            assertTrue(diagnostics.any { message -> message.contains("code=503") })
        } finally {
            bridge.close()
            upstream.close()
            upstreamThread.join(2_000)
        }
    }

    @Test
    fun `bridge returns bad gateway when upstream cannot be connected`() {
        val probe = ServerSocket(0, 1, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        val unusedPort = probe.localPort
        probe.close()
        val diagnostics = CopyOnWriteArrayList<String>()
        val bridge =
            PlayerMediaStreamBridge(
                targetUrl = "http://127.0.0.1:$unusedPort/video.mp4",
                requestHeaders = emptyMap(),
                proxySelector = noProxySelector(),
                diagnostic = { _, message -> diagnostics += message },
            )
        try {
            val connection = URL(bridge.start()).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            assertEquals(502, connection.responseCode)
            assertTrue(diagnostics.any { message -> message.contains("stage=UPSTREAM_CONNECT") })
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `bridge rejects an oversized request line with bad request`() {
        val bridge =
            PlayerMediaStreamBridge(
                targetUrl = "http://127.0.0.1:1/video.mp4",
                requestHeaders = emptyMap(),
                proxySelector = noProxySelector(),
            )
        try {
            val bridgeUrl = java.net.URI(bridge.start())
            Socket("127.0.0.1", bridgeUrl.port).use { socket ->
                val oversizedPath = "/_kiyori_player/" + "a".repeat(16 * 1024)
                val output = socket.getOutputStream()
                output.write("GET $oversizedPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                output.flush()
                val response = socket.getInputStream().bufferedReader().readLine()
                assertEquals("HTTP/1.1 400 Bad Request", response)
            }
        } finally {
            bridge.close()
        }
    }

    private fun noProxySelector(): ProxySelector =
        object : ProxySelector() {
            override fun select(uri: java.net.URI?): List<java.net.Proxy> =
                listOf(java.net.Proxy.NO_PROXY)

            override fun connectFailed(
                uri: java.net.URI?,
                sa: java.net.SocketAddress?,
                ioe: java.io.IOException?,
            ) = Unit
        }

    private fun readHttpRequest(socket: Socket) {
        val input = socket.getInputStream()
        var previous = -1
        var current: Int
        while (true) {
            current = input.read()
            if (current < 0) return
            if (previous == '\r'.code && current == '\n'.code) {
                // The bridge sends a standard header block; this helper only needs to
                // consume enough for the test server to produce its response.
                if (input.read() == '\r'.code && input.read() == '\n'.code) return
            }
            previous = current
        }
    }

    private fun assertThrowsConnectionFailure(block: () -> Unit) {
        try {
            block()
        } catch (error: java.net.ConnectException) {
            return
        } catch (error: java.net.SocketException) {
            return
        }
        throw AssertionError("Expected the closed bridge listener to reject the connection")
    }
}
