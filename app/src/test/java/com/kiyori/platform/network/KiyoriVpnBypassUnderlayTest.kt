package com.kiyori.platform.network

import android.net.Network
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriVpnBypassUnderlayTest {
    @Test
    fun `ipv4 udp datagram round trips address port and payload`() {
        val payload = byteArrayOf(9, 8, 7, 6, 5)
        val encoded =
            buildKiyoriSocksUdpDatagram(
                address = InetAddress.getByName("203.0.113.9"),
                port = 8443,
                payload = payload,
                offset = 0,
                length = payload.size,
            )
        checkNotNull(encoded)

        val parsed = parseKiyoriSocksUdpDatagram(encoded, 0, encoded.size)
        checkNotNull(parsed)
        assertEquals("203.0.113.9", parsed.host)
        assertEquals(8443, parsed.port)
        assertArrayEquals(
            payload,
            encoded.copyOfRange(parsed.payloadOffset, parsed.payloadOffset + parsed.payloadLength),
        )
    }

    @Test
    fun `ipv6 udp datagram round trips address port and payload`() {
        val payload = byteArrayOf(1, 2, 3)
        val encoded =
            buildKiyoriSocksUdpDatagram(
                address = InetAddress.getByName("2001:db8::1"),
                port = 53,
                payload = payload,
                offset = 0,
                length = payload.size,
            )
        checkNotNull(encoded)

        val parsed = parseKiyoriSocksUdpDatagram(encoded, 0, encoded.size)
        checkNotNull(parsed)
        assertEquals(53, parsed.port)
        assertEquals(InetAddress.getByName(parsed.host), InetAddress.getByName("2001:db8::1"))
        assertArrayEquals(
            payload,
            encoded.copyOfRange(parsed.payloadOffset, parsed.payloadOffset + parsed.payloadLength),
        )
    }

    @Test
    fun `domain udp datagram keeps the name for underlay resolution`() {
        val host = "node.example.com"
        val encoded =
            byteArrayOf(0, 0, 0, 0x03) +
                byteArrayOf(host.length.toByte()) +
                host.toByteArray(Charsets.UTF_8) +
                byteArrayOf(0x01, 0xBB.toByte()) +
                byteArrayOf(42)

        val parsed = parseKiyoriSocksUdpDatagram(encoded, 0, encoded.size)
        checkNotNull(parsed)
        assertEquals(host, parsed.host)
        assertEquals(443, parsed.port)
        assertEquals(1, parsed.payloadLength)
        assertEquals(42, encoded[parsed.payloadOffset].toInt())
    }

    @Test
    fun `fragmented and malformed udp datagrams are rejected`() {
        val host = byteArrayOf(203.toByte(), 0, 113, 9)
        val fragmented = byteArrayOf(0, 0, 1, 0x01) + host + byteArrayOf(0x01, 0xBB.toByte(), 7)
        assertNull(parseKiyoriSocksUdpDatagram(fragmented, 0, fragmented.size))

        val reservedSet = byteArrayOf(0, 1, 0, 0x01) + host + byteArrayOf(0x01, 0xBB.toByte(), 7)
        assertNull(parseKiyoriSocksUdpDatagram(reservedSet, 0, reservedSet.size))

        val zeroPort = byteArrayOf(0, 0, 0, 0x01) + host + byteArrayOf(0, 0, 7)
        assertNull(parseKiyoriSocksUdpDatagram(zeroPort, 0, zeroPort.size))

        val truncated = byteArrayOf(0, 0, 0, 0x01, 203.toByte(), 0)
        assertNull(parseKiyoriSocksUdpDatagram(truncated, 0, truncated.size))

        val unsupportedType = byteArrayOf(0, 0, 0, 0x02) + host + byteArrayOf(0x01, 0xBB.toByte(), 7)
        assertNull(parseKiyoriSocksUdpDatagram(unsupportedType, 0, unsupportedType.size))
    }

    @Test
    fun `replies carry the negotiated bind address and fall back to the unspecified one`() {
        val bound =
            buildKiyoriSocksReply(
                KIYORI_SOCKS_REPLY_SUCCEEDED,
                InetSocketAddress(InetAddress.getByName("127.0.0.1"), 41234),
            )
        assertEquals(KIYORI_SOCKS_VERSION, bound[0])
        assertEquals(KIYORI_SOCKS_REPLY_SUCCEEDED, bound[1].toInt())
        assertEquals(0x01, bound[3].toInt())
        assertEquals(41234, ((bound[8].toInt() and 0xff) shl 8) or (bound[9].toInt() and 0xff))

        val failure = buildKiyoriSocksReply(KIYORI_SOCKS_REPLY_HOST_UNREACHABLE, null)
        assertEquals(KIYORI_SOCKS_REPLY_HOST_UNREACHABLE, failure[1].toInt())
        assertEquals(0x01, failure[3].toInt())
        assertEquals(10, failure.size)
        assertEquals(0, failure.drop(4).sumOf { byte -> byte.toInt() and 0xff })
    }

    @Test
    fun `validated networks outrank faster transports and recency breaks ties`() {
        val candidates =
            linkedMapOf(
                "cellular-validated" to
                    KiyoriUnderlayNetworkCandidate(
                        order = 1L,
                        validated = true,
                        transportRank = 2,
                        label = "cellular",
                    ),
                "wifi-pending" to
                    KiyoriUnderlayNetworkCandidate(
                        order = 2L,
                        validated = false,
                        transportRank = 1,
                        label = "wifi",
                    ),
            )
        assertEquals("cellular-validated", selectKiyoriUnderlayNetwork(candidates))

        val wifiValidated =
            candidates + ("wifi-validated" to KiyoriUnderlayNetworkCandidate(3L, true, 1, "wifi"))
        assertEquals("wifi-validated", selectKiyoriUnderlayNetwork(wifiValidated))

        val newerWifi =
            wifiValidated + ("wifi-newer" to KiyoriUnderlayNetworkCandidate(4L, true, 1, "wifi"))
        assertEquals("wifi-newer", selectKiyoriUnderlayNetwork(newerWifi))

        assertNull(selectKiyoriUnderlayNetwork(emptyMap<String, KiyoriUnderlayNetworkCandidate>()))
    }

    @Test
    fun `underlay listens on exactly the endpoint the generated configuration hands the core`() {
        // Android 的 InetAddress.getLoopbackAddress() 是 ::1，绑在那里的监听不接受 127.0.0.1，
        // 核心只会看到 connection refused，而端口分配看起来完全正常。
        assertEquals("127.0.0.1", KiyoriVpnBypassUnderlay.LOOPBACK_ADDRESS.hostAddress)

        withUnderlay { underlay ->
            val sanitized =
                MihomoConfigSanitizer.sanitize(
                    """
                    proxies:
                      - { name: node-a, type: socks5, server: a.example.com, port: 1080 }
                    """.trimIndent(),
                )
            val runtime =
                MihomoConfigSanitizer.buildRuntimeConfig(
                    sanitizedYaml = sanitized.yaml,
                    mixedPort = 31001,
                    controllerPort = 31002,
                    controllerSecret = "controller-secret",
                    testUrl = KiyoriNetworkProxyConfig.DEFAULT_TEST_URL,
                    routingMode = KiyoriNetworkConnectionMode.RULE,
                    vpnBypass = underlay.binding,
                )

            assertTrue(runtime.yaml.contains("server: 127.0.0.1"))
            assertTrue(runtime.yaml.contains("port: " + underlay.port))
        }
    }

    @Test
    fun `relay carries a socks5 connect over the ipv4 loopback the core is told to use`() {
        withEchoServer { echoPort ->
            withUnderlay { underlay ->
                Socket().use { client ->
                    client.connect(
                        InetSocketAddress(KiyoriVpnBypassUnderlay.LOOPBACK_ADDRESS, underlay.port),
                        HANDSHAKE_TIMEOUT,
                    )
                    client.soTimeout = HANDSHAKE_TIMEOUT
                    val output = client.getOutputStream()
                    val input = client.getInputStream()

                    output.write(byteArrayOf(5, 2, 0, 2))
                    output.flush()
                    assertArrayEquals(byteArrayOf(5, 2), readExactlyOrFail(input, 2))

                    output.write(credentialFrame(USERNAME, PASSWORD))
                    output.flush()
                    assertArrayEquals(byteArrayOf(1, 0), readExactlyOrFail(input, 2))

                    output.write(connectRequest(echoPort))
                    output.flush()
                    val reply = readExactlyOrFail(input, 10)
                    assertEquals(KIYORI_SOCKS_VERSION, reply[0])
                    assertEquals(KIYORI_SOCKS_REPLY_SUCCEEDED, reply[1].toInt())

                    val payload = "kiyori-underlay".toByteArray(Charsets.UTF_8)
                    output.write(payload)
                    output.flush()
                    assertArrayEquals(payload, readExactlyOrFail(input, payload.size))
                }
            }
        }
    }

    @Test
    fun `anonymous and wrong credentials cannot borrow the loopback port`() {
        withUnderlay { underlay ->
            Socket().use { anonymous ->
                anonymous.connect(
                    InetSocketAddress(KiyoriVpnBypassUnderlay.LOOPBACK_ADDRESS, underlay.port),
                    HANDSHAKE_TIMEOUT,
                )
                anonymous.soTimeout = HANDSHAKE_TIMEOUT
                anonymous.getOutputStream().write(byteArrayOf(5, 1, 0))
                anonymous.getOutputStream().flush()
                assertArrayEquals(
                    byteArrayOf(5, 0xFF.toByte()),
                    readExactlyOrFail(anonymous.getInputStream(), 2),
                )
            }

            Socket().use { wrong ->
                wrong.connect(
                    InetSocketAddress(KiyoriVpnBypassUnderlay.LOOPBACK_ADDRESS, underlay.port),
                    HANDSHAKE_TIMEOUT,
                )
                wrong.soTimeout = HANDSHAKE_TIMEOUT
                val output = wrong.getOutputStream()
                output.write(byteArrayOf(5, 2, 0, 2))
                output.flush()
                assertArrayEquals(byteArrayOf(5, 2), readExactlyOrFail(wrong.getInputStream(), 2))
                output.write(credentialFrame(USERNAME, "not-the-password"))
                output.flush()
                val response = readExactlyOrFail(wrong.getInputStream(), 2)
                assertEquals(1, response[0].toInt())
                assertTrue("rejected credentials must not report success", response[1].toInt() != 0)
            }
        }
    }

    private fun <T> withUnderlay(block: (KiyoriVpnBypassUnderlay) -> T): T {
        val underlay =
            KiyoriVpnBypassUnderlay.startWithNetworkSource(
                networks = SystemRouteNetworkSource(),
                credential = KiyoriUnderlayCredential(USERNAME, PASSWORD),
            )
        return try {
            block(underlay)
        } finally {
            underlay.close()
        }
    }

    private fun <T> withEchoServer(block: (Int) -> T): T {
        val server = ServerSocket(0, 4, KiyoriVpnBypassUnderlay.LOOPBACK_ADDRESS)
        thread(isDaemon = true) {
            runCatching {
                while (true) {
                    val peer = server.accept()
                    thread(isDaemon = true) {
                        runCatching {
                            peer.use { socket ->
                                socket.getInputStream().copyTo(socket.getOutputStream())
                            }
                        }
                    }
                }
            }
        }
        return try {
            block(server.localPort)
        } finally {
            server.close()
        }
    }

    private fun credentialFrame(username: String, password: String): ByteArray {
        val user = username.toByteArray(Charsets.UTF_8)
        val secret = password.toByteArray(Charsets.UTF_8)
        return byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(secret.size.toByte()) + secret
    }

    private fun connectRequest(port: Int): ByteArray =
        byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1) +
            byteArrayOf(((port shr 8) and 0xff).toByte(), (port and 0xff).toByte())

    private fun readExactlyOrFail(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val read = input.read(buffer, filled, length - filled)
            if (read < 0) throw AssertionError("stream ended after $filled of $length bytes")
            filled += read
        }
        return buffer
    }

    private class SystemRouteNetworkSource : KiyoriUnderlayNetworkSource {
        override fun current(): Network? = null

        override fun describeCurrent(): String = "system-default"

        override fun close() = Unit
    }

    private companion object {
        private const val USERNAME = "underlay-user"
        private const val PASSWORD = "underlay-password"
        private const val HANDSHAKE_TIMEOUT = 5_000
    }
}
