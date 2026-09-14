package com.kiyori.platform.network

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MihomoTemporaryResourceTest {
    @Test
    fun `cancelling during blocking acquisition releases resource even when return dispatch is cancelled`() = runBlocking {
        val acquired = CountDownLatch(1)
        val returnResource = CountDownLatch(1)
        val released = AtomicInteger()
        val resource = Any()
        var used = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            withTemporaryMihomoResource(
                acquire = {
                    acquired.countDown()
                    check(returnResource.await(5, TimeUnit.SECONDS))
                    resource
                },
                release = { assertSame(resource, it); released.incrementAndGet() },
            ) { used = true }
        }
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            returnResource.countDown()
            job.join()
        }
        assertTrue(job.isCancelled)
        assertFalse(used)
        assertEquals(1, released.get())
    }

    @Test
    fun `cancelling an active probe still releases exactly once`() = runBlocking {
        val active = kotlinx.coroutines.CompletableDeferred<Unit>()
        val released = AtomicInteger()
        val job = launch {
            withTemporaryMihomoResource(acquire = { Any() }, release = { released.incrementAndGet() }) {
                active.complete(Unit)
                awaitCancellation()
            }
        }
        active.await()
        job.cancelAndJoin()
        assertEquals(1, released.get())
    }

    @Test
    fun `operation and acquisition failures preserve original cause and correct cleanup`() = runBlocking {
        val failure = IOException("synthetic failure")
        val released = AtomicInteger()
        for (failAcquire in listOf(true, false)) {
            try {
                withTemporaryMihomoResource(
                    acquire = { if (failAcquire) throw failure else Any() },
                    release = { released.incrementAndGet() },
                ) { throw failure }
                fail("expected original failure")
            } catch (actual: IOException) {
                // 协程调试栈恢复可以复制异常，但原始异常必须仍在因果链中。
                assertEquals(failure.message, actual.message)
                assertTrue(generateSequence<Throwable>(actual) { it.cause }.any { it === failure })
            }
        }
        assertEquals(1, released.get())
    }
}
