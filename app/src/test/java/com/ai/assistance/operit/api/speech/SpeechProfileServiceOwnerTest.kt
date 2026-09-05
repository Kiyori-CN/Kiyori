package com.ai.assistance.operit.api.speech

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SpeechProfileServiceOwnerTest {
    private data class Configuration(val id: String, val model: String)
    private class Engine(val configuration: Configuration) {
        var closes = 0
        fun close() { closes++ }
    }

    private val initial = Configuration("same-profile", "model-a")

    @Test
    fun `same configuration shares engine and edited parameters replace it`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine>(Engine::close)
        val first = owner.get({ initial }, ::Engine)
        assertSame(first, owner.get({ initial.copy() }, ::Engine))
        val changed = initial.copy(model = "model-b")
        val second = owner.get({ changed }, ::Engine)
        assertNotSame(first, second)
        assertEquals(changed, second.configuration)
        assertEquals(1, first.closes)
        owner.reset()
        owner.reset()
        assertEquals(1, second.closes)
    }

    @Test
    fun `failed creation propagates and never returns already closed engine`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine>(Engine::close)
        val first = owner.get({ initial }, ::Engine)
        val failure = IllegalStateException("selected engine unavailable")
        val changed = initial.copy(model = "model-b")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            owner.get({ changed }) { throw failure }
        })
        assertEquals(1, first.closes)
        owner.reset()
        assertEquals(1, first.closes)
        val next = owner.get({ initial }, ::Engine)
        assertNotSame(first, next)
    }

    @Test
    fun `shutdown failure removes cached engine without constructing replacement`() {
        val failure = IllegalStateException("shutdown failed")
        val owner = SpeechProfileServiceOwner<Configuration, Engine> {
            it.close()
            throw failure
        }
        val first = owner.get({ initial }, ::Engine)
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            owner.get({ initial.copy(model = "model-b") }) {
                fail("replacement must not start after failed shutdown")
                Engine(it)
            }
        })
        owner.reset()
        assertEquals(1, first.closes)
    }

    @Test
    fun `failed reset does not retain or close old engine again`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine> {
            it.close()
            error("shutdown failed")
        }
        val first = owner.get({ initial }, ::Engine)
        assertThrows(IllegalStateException::class.java) { owner.reset() }
        owner.reset()
        assertEquals(1, first.closes)
        assertNotSame(first, owner.get({ initial }, ::Engine))
    }

    @Test
    fun `configuration is read once and same snapshot creates cached service`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine>(Engine::close)
        var reads = 0
        val result = owner.get({ reads++; initial.copy(model = "model-$reads") }, ::Engine)
        assertEquals(1, reads)
        assertEquals("model-1", result.configuration.model)
    }

    @Test
    fun `read failure propagates without shutting down existing engine`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine>(Engine::close)
        val first = owner.get({ initial }, ::Engine)
        assertThrows(IllegalStateException::class.java) {
            owner.get({ error("preferences unavailable") }, ::Engine)
        }
        assertEquals(0, first.closes)
        assertSame(first, owner.get({ initial }, ::Engine))
    }

    @Test
    fun `concurrent callers publish just one configured engine`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine>(Engine::close)
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val creations = AtomicInteger()
        try {
            val calls = (1..8).map {
                executor.submit(Callable {
                    check(start.await(5, TimeUnit.SECONDS))
                    owner.get({ initial }) { configuration ->
                        creations.incrementAndGet()
                        Engine(configuration)
                    }
                })
            }
            start.countDown()
            val engines = calls.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, creations.get())
            engines.forEach { assertSame(engines.first(), it) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `reset racing construction closes published engine before next acquire`() {
        val owner = SpeechProfileServiceOwner<Configuration, Engine>(Engine::close)
        val executor = Executors.newFixedThreadPool(2)
        val creating = CountDownLatch(1)
        val releaseCreation = CountDownLatch(1)
        val resetting = CountDownLatch(1)
        try {
            val created = executor.submit(Callable {
                owner.get({ initial }) {
                    creating.countDown()
                    check(releaseCreation.await(5, TimeUnit.SECONDS))
                    Engine(it)
                }
            })
            assertTrue(creating.await(5, TimeUnit.SECONDS))
            val reset = executor.submit {
                resetting.countDown()
                owner.reset()
            }
            assertTrue(resetting.await(5, TimeUnit.SECONDS))
            releaseCreation.countDown()
            val first = created.get(5, TimeUnit.SECONDS)
            reset.get(5, TimeUnit.SECONDS)
            assertEquals(1, first.closes)
            assertNotSame(first, owner.get({ initial }, ::Engine))
        } finally {
            releaseCreation.countDown()
            executor.shutdownNow()
        }
    }
}
