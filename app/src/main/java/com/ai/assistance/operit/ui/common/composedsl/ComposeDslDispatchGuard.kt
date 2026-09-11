package com.ai.assistance.operit.ui.common.composedsl

/** 只保存进行中的调用；完成或废止的旧 ticket 永远不能重新获得发布资格。 */
internal class ComposeDslDispatchGuard {
    private var nextTicket = 1L
    private var closed = false
    private val active = mutableSetOf<Long>()

    @Synchronized
    fun begin(): Long? = if (closed) null else nextTicket++.also(active::add)

    @Synchronized
    fun isActive(ticket: Long): Boolean = !closed && ticket in active

    @Synchronized
    fun complete(ticket: Long): Boolean = active.remove(ticket)

    @Synchronized
    fun reset() { active.clear() }

    @Synchronized
    fun close() { closed = true; active.clear() }

    @Synchronized
    fun isOpen(): Boolean = !closed
}
