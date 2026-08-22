package com.ai.assistance.operit.core.tools.javascript

import androidx.annotation.Keep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Process-wide cursors keep API-key rotation correct across the pooled JavaScript engines. */
@Keep
object ApiKeyRoundRobin {
    private val cursors = ConcurrentHashMap<String, AtomicLong>()

    @JvmStatic
    fun nextIndex(namespace: String, keyCount: Int): Int {
        val normalizedNamespace = namespace.trim()
        require(normalizedNamespace.isNotEmpty()) { "Round-robin namespace must not be blank" }
        require(keyCount > 0) { "Key count must be positive" }

        val ticket =
            cursors.computeIfAbsent(normalizedNamespace) { AtomicLong(0L) }
                .getAndUpdate { current -> if (current == Long.MAX_VALUE) 0L else current + 1L }
        return Math.floorMod(ticket, keyCount.toLong()).toInt()
    }

    internal fun resetForTest() {
        cursors.clear()
    }
}
