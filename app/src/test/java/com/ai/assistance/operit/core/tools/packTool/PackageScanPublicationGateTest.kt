package com.ai.assistance.operit.core.tools.packTool

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PackageScanPublicationGateTest {
    @Test
    fun `late old scan cannot overwrite cache registry or notify listeners`() {
        val gate = PackageScanPublicationGate(Any())
        val old = gate.begin()
        val oldScanned = CountDownLatch(1)
        val finishOld = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val publications = mutableListOf<String>()
        try {
            val oldWork = executor.submit(Callable {
                oldScanned.countDown()
                check(finishOld.await(5, TimeUnit.SECONDS))
                gate.publish(old) { publications += "old cache/registry/listeners" }
            })
            assertTrue(oldScanned.await(5, TimeUnit.SECONDS))
            val newest = gate.begin()
            assertTrue(gate.publish(newest) { publications += "new cache/registry/listeners" })
            finishOld.countDown()
            assertFalse(oldWork.get(5, TimeUnit.SECONDS))
            assertEquals(listOf("new cache/registry/listeners"), publications)
        } finally {
            finishOld.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `new request invalidates old result even before new scan finishes`() {
        val gate = PackageScanPublicationGate(Any())
        val old = gate.begin()
        val latest = gate.begin()
        assertFalse(gate.publish(old) { fail("old request published") })
        assertTrue(gate.publish(latest) {})
    }

    @Test
    fun `executor start order does not change request order`() {
        val gate = PackageScanPublicationGate(Any())
        val firstRequest = gate.begin()
        val secondRequest = gate.begin()
        val queued = listOf(
            { gate.publish(firstRequest) { fail("delayed first job published") } },
            { gate.publish(secondRequest) {} },
        )
        assertTrue(queued[1]())
        assertFalse(queued[0]())
    }

    @Test
    fun `cache invalidation prevents in flight scan from restoring removed entry`() {
        val lock = Any()
        val gate = PackageScanPublicationGate(lock)
        val request = gate.begin()
        var cache = "installed"
        gate.invalidate {
            assertTrue(Thread.holdsLock(lock))
            cache = "removed"
        }
        assertFalse(gate.publish(request) { cache = "installed" })
        assertEquals("removed", cache)
        assertTrue(gate.publish(gate.begin()) { cache = "fresh scan" })
        assertEquals("fresh scan", cache)
    }

    @Test
    fun `publication uses registry monitor and propagates failure`() {
        val lock = Any()
        val gate = PackageScanPublicationGate(lock)
        val failure = IllegalStateException("registry update failed")
        val thrown = assertThrows(IllegalStateException::class.java) {
            gate.publish(gate.begin()) {
                assertTrue(Thread.holdsLock(lock))
                throw failure
            }
        }
        assertSame(failure, thrown)
        assertTrue(gate.publish(gate.begin()) {})
    }
}
