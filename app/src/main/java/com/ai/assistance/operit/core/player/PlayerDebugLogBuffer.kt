package com.ai.assistance.operit.core.player

internal object PlayerDebugLogBuffer {
    private const val MAX_LINES = 600
    private val lock = Any()
    private val lines = ArrayDeque<String>(MAX_LINES)

    fun clear() {
        synchronized(lock) {
            lines.clear()
        }
    }

    fun append(tag: String, message: String) {
        val normalizedMessage = message
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
        if (normalizedMessage.isBlank()) return
        val line = "${System.currentTimeMillis()} $tag: $normalizedMessage"
        synchronized(lock) {
            while (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun snapshot(): String = synchronized(lock) { lines.joinToString("\n") }
}
