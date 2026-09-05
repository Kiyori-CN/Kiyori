package com.ai.assistance.operit.data.mcp.plugins

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MCPBridgeConnectionTest {
    @Test
    fun `exchange writes one JSON line and parses the response`() = runBlocking {
        withServer(handler = { reader, writer ->
            val request = reader.readLine()
            assertNotNull(request)
            assertEquals("list", JSONObject(request).getString("command"))
            writer.println(JSONObject().put("success", true).toString())
            writer.flush()
        }, client = { port ->
            MCPBridgeConnection("127.0.0.1", port).use { connection ->
                val response = connection.exchange(JSONObject().put("command", "list"))
                assertTrue(response.getBoolean("success"))
            }
        })
    }

    @Test
    fun `one connection can exchange multiple commands in order`() = runBlocking {
        withServer(handler = { reader, writer ->
            repeat(2) {
                val request = JSONObject(reader.readLine())
                writer.println(JSONObject().put("sequence", request.getInt("sequence")).toString())
                writer.flush()
            }
        }, client = { port ->
            MCPBridgeConnection("127.0.0.1", port).use { connection ->
                assertEquals(1, connection.exchange(JSONObject().put("sequence", 1)).getInt("sequence"))
                assertEquals(2, connection.exchange(JSONObject().put("sequence", 2)).getInt("sequence"))
            }
        })
    }

    @Test
    fun `EOF is surfaced and the connection becomes closed`() = runBlocking {
        withServer(handler = { reader, _ ->
            reader.readLine()
        }, client = { port ->
            val connection = MCPBridgeConnection("127.0.0.1", port)
            assertThrows(Exception::class.java) {
                runBlocking { connection.exchange(JSONObject().put("command", "list")) }
            }
            assertTrue(!connection.canReuse("127.0.0.1", port))
            connection.close()
        })
    }

    @Test
    fun `cancellation closes the socket and interrupts a blocked read`() = runBlocking {
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val serverJob = async(Dispatchers.IO) {
            server.use { listener ->
                listener.accept().use { socket ->
                    accepted.countDown()
                    val reader = socket.getInputStream().bufferedReader()
                    reader.readLine()
                    reader.readLine()
                }
            }
        }
        val connection = MCPBridgeConnection("127.0.0.1", server.localPort)
        val pending = async(Dispatchers.IO) {
            connection.exchange(JSONObject().put("command", "list"))
        }
        assertTrue(accepted.await(5, TimeUnit.SECONDS))
        pending.cancelAndJoin()
        assertTrue(serverJob.await().let { true })
        assertTrue(!connection.canReuse("127.0.0.1", server.localPort))
        connection.close()
    }

    private suspend fun withServer(
        handler: (java.io.BufferedReader, java.io.PrintWriter) -> Unit,
        client: suspend (Int) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            ServerSocket(0).use { server ->
                val serverJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                        handler(reader, writer)
                    }
                }
                client(server.localPort)
                serverJob.await()
            }
        }
    }
}
