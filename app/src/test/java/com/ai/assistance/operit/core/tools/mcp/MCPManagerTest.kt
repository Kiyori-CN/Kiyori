package com.ai.assistance.operit.core.tools.mcp

import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.kiyori.platform.logging.KiyoriLogger
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MCPManagerTest {
    private val controls = mutableListOf<ControlledClient>()
    private val requests = mutableListOf<Request>()
    private lateinit var logs: MockedStatic<KiyoriLogger>

    @Before
    fun setUp() {
        logs = Mockito.mockStatic(KiyoriLogger::class.java)
    }

    @After
    fun tearDown() {
        controls.forEach { it.release.countDown() }
        requests.forEach { it.thread.interrupt() }
        requests.forEach {
            it.thread.join(5_000)
            assertFalse("MCP request thread must terminate", it.thread.isAlive)
        }
        logs.close()
    }

    @Test
    fun `unknown server cannot create a client`() {
        val manager = MCPManager { error("Unregistered service must not create a client") }
        assertNull(manager.getOrCreateClient("missing"))
        assertFalse(manager.isServerRegistered("missing"))
        assertTrue(manager.getLastConnectionFailureReason("missing")!!.contains("not registered"))
    }

    @Test
    fun `concurrent calls for one service share a single connected client`() {
        val client = controlled(block = true)
        val factoryCalls = AtomicInteger()
        val manager = MCPManager { factoryCalls.incrementAndGet(); client.client }
        manager.registerServer("one", config("one"))
        val first = request { manager.getOrCreateClient("one") }
        assertTrue(client.entered.await(5, TimeUnit.SECONDS))
        val second = request { manager.getOrCreateClient("one") }
        awaitWaiting(second)
        assertEquals(1, factoryCalls.get())
        client.release.countDown()
        assertSame(client.client, first.result())
        assertSame(client.client, second.result())
        assertEquals(1, client.connectCalls.get())
        assertEquals(1, factoryCalls.get())
    }

    @Test
    fun `connecting one service does not block another or configuration reads`() {
        val slow = controlled(block = true)
        val fast = controlled()
        val manager = MCPManager { if (it == "slow") slow.client else fast.client }
        manager.registerServer("slow", config("slow"))
        manager.registerServer("fast", config("fast"))
        val first = request { manager.getOrCreateClient("slow") }
        assertTrue(slow.entered.await(5, TimeUnit.SECONDS))
        assertEquals(setOf("slow", "fast"), manager.getRegisteredServers().keys)
        assertSame(fast.client, request { manager.getOrCreateClient("fast") }.result())
        slow.release.countDown()
        assertSame(slow.client, first.result())
    }

    @Test
    fun `registration change rejects old success and serializes the replacement`() {
        val old = controlled(block = true)
        val fresh = controlled()
        val clients = ConcurrentLinkedQueue(listOf(old.client, fresh.client))
        val manager = MCPManager { clients.remove() }
        manager.registerServer("one", config("one"))
        val first = request { manager.getOrCreateClient("one") }
        assertTrue(old.entered.await(5, TimeUnit.SECONDS))
        val updated = config("one").copy(description = "updated")
        manager.registerServer("one", updated)
        assertEquals(updated, manager.getRegisteredServers()["one"])
        val second = request { manager.getOrCreateClient("one") }
        awaitWaiting(second)
        assertEquals(0, fresh.connectCalls.get())
        old.release.countDown()
        assertNull(first.result())
        assertSame(fresh.client, second.result())
        assertFalse(old.connected.get())
        assertNull(manager.getLastConnectionFailureReason("one"))
    }

    @Test
    fun `unregister during connection prevents late publication`() {
        val old = controlled(block = true)
        val manager = MCPManager { old.client }
        manager.registerServer("one", config("one"))
        val pending = request { manager.getOrCreateClient("one") }
        assertTrue(old.entered.await(5, TimeUnit.SECONDS))
        manager.unregisterServer("one")
        assertTrue(manager.getRegisteredServers().isEmpty())
        assertNull(manager.getOrCreateClient("one"))
        old.release.countDown()
        assertNull(pending.result())
        assertFalse(old.connected.get())
        assertFalse(manager.isServerRegistered("one"))
    }

    @Test
    fun `unregister during client construction prevents the connection from starting`() {
        val old = controlled(block = true)
        val manager = MCPManager {
            old.entered.countDown()
            check(old.release.await(10, TimeUnit.SECONDS))
            old.client
        }
        manager.registerServer("one", config("one"))
        val pending = request { manager.getOrCreateClient("one") }
        assertTrue(old.entered.await(5, TimeUnit.SECONDS))
        manager.unregisterServer("one")
        old.release.countDown()
        assertNull(pending.result())
        assertEquals(0, old.connectCalls.get())
        assertEquals(1, old.disconnectCalls.get())
    }

    @Test
    fun `immediate reinstall retains serialization until obsolete calls drain`() {
        val old = controlled(block = true)
        val fresh = controlled()
        val clients = ConcurrentLinkedQueue(listOf(old.client, fresh.client))
        val manager = MCPManager { clients.remove() }
        manager.registerServer("one", config("one"))
        val first = request { manager.getOrCreateClient("one") }
        assertTrue(old.entered.await(5, TimeUnit.SECONDS))
        manager.unregisterServer("one")
        manager.registerServer("one", config("one"))
        val second = request { manager.getOrCreateClient("one") }
        awaitWaiting(second)
        assertEquals(0, fresh.connectCalls.get())
        old.release.countDown()
        assertNull(first.result())
        assertSame(fresh.client, second.result())
    }

    @Test
    fun `late failure cannot overwrite the new registration reason`() {
        val old = controlled(block = true).apply { succeeds = false }
        val fresh = controlled()
        val clients = ConcurrentLinkedQueue(listOf(old.client, fresh.client))
        val manager = MCPManager { clients.remove() }
        manager.registerServer("one", config("one"))
        val first = request { manager.getOrCreateClient("one") }
        assertTrue(old.entered.await(5, TimeUnit.SECONDS))
        manager.registerServer("one", config("one"))
        old.release.countDown()
        assertNull(first.result())
        assertNull(manager.getLastConnectionFailureReason("one"))
        assertSame(fresh.client, manager.getOrCreateClient("one"))
    }

    @Test
    fun `shutdown invalidates pending requests but preserves registered configuration`() {
        val old = controlled(block = true)
        val fresh = controlled()
        val clients = ConcurrentLinkedQueue(listOf(old.client, fresh.client))
        val manager = MCPManager { clients.remove() }
        val configuration = config("one")
        manager.registerServer("one", configuration)
        val first = request { manager.getOrCreateClient("one") }
        assertTrue(old.entered.await(5, TimeUnit.SECONDS))
        manager.shutdown()
        assertEquals(mapOf("one" to configuration), manager.getRegisteredServers())
        old.release.countDown()
        assertNull(first.result())
        assertFalse(old.connected.get())
        assertSame(fresh.client, manager.getOrCreateClient("one"))
    }

    @Test
    fun `shutdown disconnects published clients and clears previous failures`() {
        val first = controlled()
        val failed = controlled().apply { succeeds = false }
        val manager = MCPManager { if (it == "one") first.client else failed.client }
        manager.registerServer("one", config("one"))
        manager.registerServer("failed", config("failed"))
        assertSame(first.client, manager.getOrCreateClient("one"))
        assertNull(manager.getOrCreateClient("failed"))
        assertEquals("controlled failure", manager.getLastConnectionFailureReason("failed"))
        manager.shutdown()
        assertFalse(first.connected.get())
        assertNull(manager.getLastConnectionFailureReason("failed"))
        assertEquals(2, manager.getRegisteredServers().size)
    }

    @Test
    fun `failed cached reconnect attempts once and permits a later caller to connect`() {
        val old = controlled()
        val fresh = controlled()
        val clients = ConcurrentLinkedQueue(listOf(old.client, fresh.client))
        val manager = MCPManager { clients.remove() }
        manager.registerServer("one", config("one"))
        assertSame(old.client, manager.getOrCreateClient("one"))
        old.connected.set(false)
        old.succeeds = false
        assertNull(manager.getOrCreateClient("one"))
        assertEquals(2, old.connectCalls.get())
        assertEquals(0, fresh.connectCalls.get())
        assertEquals("controlled failure", manager.getLastConnectionFailureReason("one"))
        assertSame(fresh.client, manager.getOrCreateClient("one"))
        assertNull(manager.getLastConnectionFailureReason("one"))
    }

    @Test
    fun `cancelled connect propagates without retaining client or failure state`() {
        val cancelled = controlled().apply { error = CancellationException("cancelled") }
        val fresh = controlled()
        val clients = ConcurrentLinkedQueue(listOf(cancelled.client, fresh.client))
        val manager = MCPManager { clients.remove() }
        manager.registerServer("one", config("one"))
        assertThrows(CancellationException::class.java) { manager.getOrCreateClient("one") }
        assertEquals(1, cancelled.disconnectCalls.get())
        assertNull(manager.getLastConnectionFailureReason("one"))
        assertSame(fresh.client, manager.getOrCreateClient("one"))
    }

    @Test
    fun `interrupted lock waiter does not block subsequent registrations`() {
        val first = controlled(block = true)
        val manager = MCPManager { first.client }
        manager.registerServer("one", config("one"))
        val connecting = request { manager.getOrCreateClient("one") }
        assertTrue(first.entered.await(5, TimeUnit.SECONDS))
        val waiting = request { manager.getOrCreateClient("one") }
        awaitWaiting(waiting)
        waiting.thread.interrupt()
        val thrown = assertThrows(ExecutionException::class.java) { waiting.result() }
        assertTrue(thrown.cause is InterruptedException)
        assertTrue(waiting.interrupted.get())
        manager.unregisterServer("one")
        first.release.countDown()
        assertNull(connecting.result())
        manager.registerServer("one", config("one"))
        assertSame(first.client, manager.getOrCreateClient("one"))
    }

    @Test
    fun `factory errors publish only a type and allow subsequent attempts`() {
        val calls = AtomicInteger()
        val fresh = controlled()
        val manager = MCPManager {
            if (calls.getAndIncrement() == 0) throw IllegalStateException("private details")
            fresh.client
        }
        manager.registerServer("one", config("one"))
        assertNull(manager.getOrCreateClient("one"))
        assertEquals("Bridge client connection failed: IllegalStateException", manager.getLastConnectionFailureReason("one"))
        assertSame(fresh.client, manager.getOrCreateClient("one"))
    }

    @Test
    fun `caller mutations cannot alter a registered configuration snapshot`() {
        val capabilities = mutableListOf("tools")
        val extras = mutableMapOf("mode" to "stdio")
        val manager = MCPManager { error("No connection expected") }
        manager.registerServer("one", config("one").copy(capabilities = capabilities, extraData = extras))
        capabilities.clear()
        extras.clear()
        val registered = manager.getRegisteredServers().getValue("one")
        assertEquals(listOf("tools"), registered.capabilities)
        assertEquals(mapOf("mode" to "stdio"), registered.extraData)
    }

    private fun config(name: String) = MCPServerConfig(name, "mcp://plugin/$name", "test", listOf("tools"), emptyMap())

    private fun controlled(block: Boolean = false): ControlledClient = ControlledClient(block).also { controls.add(it) }

    private class ControlledClient(block: Boolean) {
        val client: MCPBridgeClient = mock()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(if (block) 1 else 0)
        val connected = AtomicBoolean(false)
        val connectCalls = AtomicInteger()
        val disconnectCalls = AtomicInteger()
        @Volatile var succeeds = true
        @Volatile var error: Exception? = null

        init {
            whenever(client.isConnected()).thenAnswer { connected.get() }
            whenever(client.getLastConnectionFailureDetail()).thenReturn("controlled failure")
            doAnswer { connected.set(false); disconnectCalls.incrementAndGet(); null }.whenever(client).disconnect()
            runBlocking {
                whenever(client.connect()).thenAnswer {
                    connectCalls.incrementAndGet()
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Controlled connection was not released" }
                    error?.let { throw it }
                    connected.set(succeeds)
                    succeeds
                }
            }
        }
    }

    private class Request(val future: FutureTask<MCPBridgeClient?>, val thread: Thread, val interrupted: AtomicBoolean) {
        fun result(): MCPBridgeClient? = future.get(5, TimeUnit.SECONDS)
    }

    private fun request(action: () -> MCPBridgeClient?): Request {
        val interrupted = AtomicBoolean()
        val task = FutureTask(Callable {
            Mockito.mockStatic(KiyoriLogger::class.java).use {
                try {
                    action()
                } finally {
                    interrupted.set(Thread.currentThread().isInterrupted)
                }
            }
        })
        return Request(task, Thread(task, "MCPManagerTest"), interrupted).also {
            requests.add(it)
            it.thread.start()
        }
    }

    private fun awaitWaiting(request: Request) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (request.thread.state != Thread.State.WAITING && System.nanoTime() < deadline) {
            assertFalse("Request completed before reaching the connection lock", request.future.isDone)
            Thread.yield()
        }
        assertEquals(Thread.State.WAITING, request.thread.state)
    }
}
