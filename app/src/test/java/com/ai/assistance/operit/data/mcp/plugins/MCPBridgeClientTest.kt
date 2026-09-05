package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class MCPBridgeClientTest {
    private val commands = mutableListOf<JSONObject>()
    private val logMessages = mutableListOf<String>()
    private lateinit var logs: MockedStatic<KiyoriLogger>
    private val context = Mockito.mock(Context::class.java) { invocation ->
        if (invocation.method.name == "getString") "localized error"
        else Mockito.RETURNS_DEFAULTS.answer(invocation)
    }

    @Before
    fun setUp() {
        logs = Mockito.mockStatic(KiyoriLogger::class.java) { invocation ->
            logMessages.addAll(invocation.arguments.filterIsInstance<String>())
            null
        }
    }

    @After
    fun tearDown() {
        logs.close()
    }

    @Test
    fun `success preserves the first response and exact tool payload`() = runBlocking {
        val response = JSONObject().put("success", true).put("result", JSONObject().put("text", "result"))
        val client = client { if (it.getString("command") == "list") ready() else response }
        val params = JSONObject().put("query", "private-query")
        assertSame(response, client.callTool("namespace:write", params))
        assertEquals(listOf("list", "toolcall"), commandTypes())
        val command = commands.last()
        assertTrue(command.getString("id").isNotBlank())
        val call = command.getJSONObject("params")
        assertEquals("service", call.getString("name"))
        assertEquals("namespace:write", call.getString("method"))
        assertSame(params, call.getJSONObject("params"))
        assertNotEquals(command.getString("id"), call.getString("id"))
        assertTrue(client.isConnected())
    }

    @Test
    fun `connection error responses never reconnect or replay a submitted tool`() = runBlocking {
        for (message in listOf("not available", "not connected", "connection closed", "timeout")) {
            commands.clear()
            val response = failure(message)
            val client = client { if (it.getString("command") == "list") ready() else response }
            assertSame(response, client.callTool("write", JSONObject()))
            assertEquals(listOf("list", "toolcall"), commandTypes())
            assertFalse(client.isConnected())
        }
    }

    @Test
    fun `ordinary tool failure retains connection and preserves response`() = runBlocking {
        val response = failure("invalid input")
        val client = client { if (it.getString("command") == "list") ready() else response }
        assertSame(response, client.callTool("write", JSONObject()))
        assertEquals(listOf("list", "toolcall"), commandTypes())
        assertTrue(client.isConnected())
    }

    @Test
    fun `a later explicit call can reconnect and gets new request identifiers`() = runBlocking {
        var calls = 0
        val client = client {
            if (it.getString("command") == "list") ready()
            else if (calls++ == 0) failure("timeout") else JSONObject().put("success", true)
        }
        assertFalse(client.callTool("write", JSONObject())!!.getBoolean("success"))
        assertEquals(listOf("list", "toolcall"), commandTypes())
        assertTrue(client.callTool("write", JSONObject())!!.getBoolean("success"))
        assertEquals(listOf("list", "toolcall", "list", "toolcall"), commandTypes())
        assertNotEquals(commands[1].getString("id"), commands[3].getString("id"))
    }

    @Test
    fun `missing response reports failure and does not replay`() = runBlocking {
        val client = client { if (it.getString("command") == "list") ready() else null }
        assertFalse(client.callTool("write", JSONObject())!!.getBoolean("success"))
        assertEquals(listOf("list", "toolcall"), commandTypes())
        assertFalse(client.isConnected())
    }

    @Test
    fun `transport exception reports failure without replay or private logs`() = runBlocking {
        val client = client {
            if (it.getString("command") == "list") ready()
            else throw IllegalStateException("private-exception-body")
        }
        assertFalse(client.callTool("write", JSONObject())!!.getBoolean("success"))
        assertEquals(listOf("list", "toolcall"), commandTypes())
        assertFalse(client.isConnected())
        assertFalse(logMessages.any { it.contains("private-exception-body") })
    }

    @Test
    fun `connection preparation failure never sends a tool`() = runBlocking {
        val client = client { failure("unavailable") }
        assertFalse(client.callTool("write", JSONObject())!!.getBoolean("success"))
        assertEquals(listOf("list", "list"), commandTypes())
        assertFalse(client.isConnected())
    }

    @Test
    fun `registered inactive service is spawned before one tool request`() = runBlocking {
        val client = client {
            when (it.getString("command")) {
                "list" -> if (it.has("params")) ready(active = false) else services(active = false)
                "spawn" -> JSONObject().put("success", true).put("result", JSONObject().put("ready", true))
                "toolcall" -> JSONObject().put("success", true)
                else -> error("Unexpected command")
            }
        }
        assertTrue(client.callTool("write", JSONObject())!!.getBoolean("success"))
        assertEquals(listOf("list", "list", "spawn", "toolcall"), commandTypes())
        assertEquals(180000L, commands[2].getJSONObject("params").getLong("timeoutMs"))
    }

    @Test
    fun `ping clears an obsolete connected flag`() = runBlocking {
        var active = true
        val client = client { ready(active) }
        assertTrue(client.ping())
        active = false
        assertFalse(client.ping())
        assertFalse(client.isConnected())
        assertEquals("service", commands.last().getJSONObject("params").getString("name"))
    }

    @Test
    fun `service information keeps the list all response contract`() = runBlocking {
        val client = client { services(active = true) }
        val info = client.getServiceInfo()!!
        assertEquals("service", info.name)
        assertEquals(listOf("write"), info.toolNames)
        assertEquals(1, info.toolCount)
        assertTrue(info.active && info.ready)
        assertFalse(commands.single().has("params"))
    }

    @Test
    fun `synchronous map call preserves nested JSON types`() {
        val client = client { if (it.getString("command") == "list") ready() else JSONObject().put("success", true) }
        client.callToolSync("write", mapOf("items" to listOf(1, null, mapOf("enabled" to true))))
        val items = commands.last().getJSONObject("params").getJSONObject("params").getJSONArray("items")
        assertEquals(1, items.getInt(0))
        assertTrue(items.isNull(1))
        assertTrue(items.getJSONObject(2).getBoolean("enabled"))
    }

    @Test
    fun `all suspend command entrypoints propagate cancellation`() {
        val operations = listOf<suspend (MCPBridgeClient) -> Any?>(
            { it.connect() }, { it.ping() }, { it.spawnBlocking() }, { it.unspawn() },
            { it.getTools() }, { it.getServiceInfo() }, { it.getToolDescriptions() }, { it.isActive() },
            { it.callTool("write", JSONObject()) },
        )
        for (operation in operations) {
            commands.clear()
            val cancellation = CancellationException("cancelled")
            val client = client { throw cancellation }
            val thrown = assertThrows(CancellationException::class.java) { runBlocking { operation(client) } }
            // 协程堆栈恢复可能复制异常，但必须保留原取消作为原因。
            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cancellation })
            assertEquals(1, commands.size)
            assertFalse(client.isConnected())
        }
    }

    @Test
    fun `cancellation after submitting a tool propagates without replay`() = runBlocking {
        val cancellation = CancellationException("cancelled after submission")
        val client = client {
            if (it.getString("command") == "list") ready() else throw cancellation
        }
        assertTrue(client.connect())
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { client.callTool("write", JSONObject()) }
        }
        assertSame(cancellation, thrown)
        assertEquals(listOf("list", "toolcall"), commandTypes())
        assertFalse(client.isConnected())
    }

    @Test
    fun `error response and parameter bodies are absent from client logs`() = runBlocking {
        val response = failure("timeout private-response-body")
        val client = client { if (it.getString("command") == "list") ready() else response }
        assertSame(response, client.callTool("write", JSONObject().put("input", "private-input-body")))
        assertFalse(logMessages.any { it.contains("private-response-body") || it.contains("private-input-body") })
        assertTrue(logMessages.any { it.contains("MCP") })
    }

    private fun client(sender: suspend (JSONObject) -> JSONObject?) = MCPBridgeClient(
        context = context,
        serviceName = "service",
        commandSender = { command -> commands.add(command); sender(command) },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun commandTypes() = commands.map { it.getString("command") }

    private fun ready(active: Boolean = true) = JSONObject().put("success", true)
        .put("result", JSONObject().put("active", active).put("ready", active))

    private fun services(active: Boolean) = JSONObject().put("success", true).put(
        "result", JSONObject().put("services", JSONArray().put(
            JSONObject().put("name", "service").put("active", active).put("ready", active)
                .put("toolCount", 1).put("tools", JSONArray().put(JSONObject().put("name", "write")))
        ))
    )

    private fun failure(message: String) = JSONObject().put("success", false)
        .put("error", JSONObject().put("code", 500).put("message", message))
}
