package com.kiyori.platform.network

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkRouteGuardTest {
    @Test
    fun `rejecting a stale pooled route preserves another stream on the same HTTP2 socket`() {
        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
            val content = "data: already streaming\n\ndata: completed\n\n"
            server.enqueue(MockResponse().setBody(content).throttleBody(1, 20, TimeUnit.MILLISECONDS))
            server.enqueue(MockResponse().setBody("fresh connection"))
            val expected = AtomicReference(Proxy.NO_PROXY)
            val connections = mutableListOf<Connection>()
            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                .proxy(Proxy.NO_PROXY)
                .retryOnConnectionFailure(false)
                .readTimeout(5, TimeUnit.SECONDS)
                .addNetworkInterceptor(networkRouteGuard { expected.get() })
                .eventListener(object : EventListener() {
                    override fun connectionAcquired(call: Call, connection: Connection) { connections.add(connection) }
                }).build()
            try {
                client.newCall(Request.Builder().url(server.url("/stream")).build()).execute().use { first ->
                    val body = first.body!!.source()
                    assertEquals('d'.code.toByte(), body.readByte())
                    expected.set(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))
                    assertThrows(IOException::class.java) {
                        client.newCall(Request.Builder().url(server.url("/must-not-send")).build()).execute().close()
                    }
                    assertEquals(2, connections.size)
                    assertSame(connections[0], connections[1])
                    assertFalse(connections[0].socket().isClosed)
                    assertEquals(1, server.requestCount)
                    // 单纯抛出 IOException 会让 H2 旧连接继续驻留池中，后续请求反复命中并失败。
                    // 退役必须同时保证下一请求获取新连接，不能以保护在途流为由留下不可用的池项。
                    expected.set(Proxy.NO_PROXY)
                    client.newCall(Request.Builder().url(server.url("/fresh")).build()).execute().use {
                        assertEquals("fresh connection", it.body!!.string())
                    }
                    assertEquals(3, connections.size)
                    assertNotSame(connections[0], connections[2])
                    assertEquals(content.substring(1), body.readUtf8())
                    assertEquals(2, server.requestCount)
                }
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }
}
