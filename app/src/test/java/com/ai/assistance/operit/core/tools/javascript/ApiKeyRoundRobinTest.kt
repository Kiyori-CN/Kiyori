package com.ai.assistance.operit.core.tools.javascript

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ApiKeyRoundRobinTest {
    @Before
    fun setUp() {
        ApiKeyRoundRobin.resetForTest()
    }

    @After
    fun tearDown() {
        ApiKeyRoundRobin.resetForTest()
    }

    @Test
    fun nextIndex_rotatesIndependentlyByNamespace() {
        assertEquals(listOf(0, 1, 2, 0), List(4) { ApiKeyRoundRobin.nextIndex("tavily", 3) })
        assertEquals(listOf(0, 1), List(2) { ApiKeyRoundRobin.nextIndex("serpapi", 2) })
        assertEquals(1, ApiKeyRoundRobin.nextIndex("tavily", 3))
    }

    @Test
    fun nextIndex_usesCurrentKeyCount() {
        repeat(3) { ApiKeyRoundRobin.nextIndex("provider", 4) }

        assertEquals(1, ApiKeyRoundRobin.nextIndex("provider", 2))
        assertEquals(0, ApiKeyRoundRobin.nextIndex("provider", 2))
    }

    @Test
    fun nextIndex_isAtomicAcrossConcurrentCallers() {
        val executor = Executors.newFixedThreadPool(12)
        try {
            val results =
                executor.invokeAll(
                    List(800) {
                        Callable { ApiKeyRoundRobin.nextIndex("concurrent", 4) }
                    }
                ).map { it.get() }

            assertEquals(mapOf(0 to 200, 1 to 200, 2 to 200, 3 to 200), results.groupingBy { it }.eachCount())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun nextIndex_rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiKeyRoundRobin.nextIndex("  ", 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiKeyRoundRobin.nextIndex("provider", 0)
        }
    }
}
