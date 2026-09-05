package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.kiyori.platform.logging.KiyoriLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MCPToolExecutorTest {
    private val context: Context = mock()
    private val client: MCPBridgeClient = mock()
    private val manager = MCPManager { client }
    private val executor = MCPToolExecutor(context, manager)
    private val tool = AITool("service:write", emptyList())
    private val messages = mutableListOf<String>()
    private lateinit var logs: MockedStatic<KiyoriLogger>

    @Before
    fun setUp(): Unit = runBlocking {
        logs = Mockito.mockStatic(KiyoriLogger::class.java) { invocation ->
            messages.addAll(invocation.arguments.filterIsInstance<String>())
            null
        }
        manager.registerServer("service", "mcp://plugin/service")
        whenever(client.isConnected()).thenReturn(true)
        whenever(client.isActive()).thenReturn(true)
        whenever(client.getTools()).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        Thread.interrupted()
        logs.close()
    }

    @Test
    fun `tool information cancellation prevents tool submission`(): Unit = runBlocking {
        val cancellation = CancellationException("cancelled during metadata")
        whenever(client.getTools()).thenAnswer { throw cancellation }
        assertSame(cancellation, assertThrows(CancellationException::class.java) { executor.invoke(tool) })
        verify(client, never()).callToolSync(any(), any<Map<String, Any>>())
    }

    @Test
    fun `tool information interruption is restored and prevents submission`() {
        runBlocking {
            whenever(client.getTools()).thenAnswer { throw InterruptedException("interrupted metadata") }
        }
        assertThrows(InterruptedException::class.java) { executor.invoke(tool) }
        assertTrue(Thread.currentThread().isInterrupted)
        verify(client, never()).callToolSync(any(), any<Map<String, Any>>())
    }

    @Test
    fun `service status interruption is restored and prevents submission`() {
        runBlocking {
            whenever(client.isActive()).thenAnswer { throw InterruptedException("interrupted status") }
        }
        assertThrows(InterruptedException::class.java) { executor.invoke(tool) }
        assertTrue(Thread.currentThread().isInterrupted)
        verify(client, never()).callToolSync(any(), any<Map<String, Any>>())
    }

    @Test
    fun `submitted tool cancellation propagates`() {
        val cancellation = CancellationException("cancelled tool")
        whenever(client.callToolSync(any(), any<Map<String, Any>>())).thenAnswer { throw cancellation }
        assertSame(cancellation, assertThrows(CancellationException::class.java) { executor.invoke(tool) })
        verify(client).callToolSync("write", emptyMap<String, Any>())
    }

    @Test
    fun `submitted tool interruption is restored`() {
        whenever(client.callToolSync(any(), any<Map<String, Any>>())).thenAnswer { throw InterruptedException("interrupted tool") }
        assertThrows(InterruptedException::class.java) { executor.invoke(tool) }
        assertTrue(Thread.currentThread().isInterrupted)
        verify(client).callToolSync("write", emptyMap<String, Any>())
    }

    @Test
    fun `tool error remains in caller result but is absent from logs`() {
        whenever(client.callToolSync(any(), any<Map<String, Any>>())).thenReturn(
            JSONObject().put("success", false).put("error", JSONObject().put("code", 403).put("message", "private-error-body"))
        )
        val result = executor.invoke(tool)
        assertFalse(result.success)
        assertEquals("[403] private-error-body", result.error)
        assertFalse(messages.any { it.contains("private-error-body") })
    }

    @Test
    fun `parameter conversion keeps types without logging values`() = runBlocking {
        whenever(client.getTools()).thenReturn(listOf(JSONObject()
            .put("name", "write")
            .put("inputSchema", JSONObject().put("properties", JSONObject()
                .put("input", JSONObject().put("type", "array"))
            ))
        ))
        whenever(client.callToolSync(any(), any<Map<String, Any>>())).thenReturn(JSONObject().put("success", true))
        val result = executor.invoke(AITool("service:write", listOf(ToolParameter("input", "[\"private-input-body\"]"))))
        assertTrue(result.success)
        assertFalse(messages.any { it.contains("private-input-body") })
        assertTrue(messages.any { it.contains("input") && it.contains("转换") })
    }
}
