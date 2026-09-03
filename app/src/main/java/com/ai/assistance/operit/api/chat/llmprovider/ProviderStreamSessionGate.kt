package com.ai.assistance.operit.api.chat.llmprovider

/**
 * Owns the cancellable handles of one provider stream generation.
 *
 * Provider streams are eagerly collected outside the message collector Job. A cancelled upstream
 * can therefore finish after the next turn has started. Every bind and clear operation is scoped to
 * a session so that a stale upstream cannot cancel, clear, or inherit the cancellation state of a
 * newer request.
 */
internal class ProviderStreamSessionGate {
    internal class Session internal constructor(
        val generation: Long,
    ) {
        @Volatile
        internal var cancelled: Boolean = false
    }

    private data class BoundHandle(
        val session: Session,
        val owner: Any,
        val cancel: () -> Unit,
    )

    private val lock = Any()
    private var nextGeneration = 0L
    private var activeSession: Session? = null
    private var activeCall: BoundHandle? = null
    private var activeResponse: BoundHandle? = null

    fun begin(): Session {
        val staleHandles: List<BoundHandle>
        val session: Session
        synchronized(lock) {
            activeSession?.cancelled = true
            staleHandles = listOfNotNull(activeResponse, activeCall)
            activeResponse = null
            activeCall = null
            session = Session(generation = ++nextGeneration)
            activeSession = session
        }
        staleHandles.forEach { handle -> runCatching(handle.cancel) }
        return session
    }

    fun bindCall(session: Session, owner: Any, cancel: () -> Unit) {
        bind(session, owner, cancel, isResponse = false)
    }

    fun bindResponse(session: Session, owner: Any, cancel: () -> Unit) {
        bind(session, owner, cancel, isResponse = true)
    }

    fun clearCall(session: Session, owner: Any) {
        clear(session, owner, isResponse = false)
    }

    fun clearResponse(session: Session, owner: Any) {
        clear(session, owner, isResponse = true)
    }

    fun isCancelled(session: Session): Boolean = session.cancelled

    fun cancelActive(): Boolean {
        val handles: List<BoundHandle>
        synchronized(lock) {
            val session = activeSession ?: return false
            if (session.cancelled && activeResponse == null && activeCall == null) {
                return false
            }
            session.cancelled = true
            handles = listOfNotNull(activeResponse, activeCall)
            activeResponse = null
            activeCall = null
        }
        handles.forEach { handle -> runCatching(handle.cancel) }
        return true
    }

    fun complete(session: Session) {
        synchronized(lock) {
            if (activeSession !== session) return
            activeSession = null
            activeCall = null
            activeResponse = null
        }
    }

    private fun bind(
        session: Session,
        owner: Any,
        cancel: () -> Unit,
        isResponse: Boolean,
    ) {
        val replaced: BoundHandle?
        val accepted: Boolean
        synchronized(lock) {
            accepted = activeSession === session && !session.cancelled
            if (!accepted) {
                replaced = null
            } else {
                val binding = BoundHandle(session, owner, cancel)
                if (isResponse) {
                    replaced = activeResponse
                    activeResponse = binding
                } else {
                    replaced = activeCall
                    activeCall = binding
                }
            }
        }
        if (!accepted) {
            runCatching(cancel)
        } else {
            replaced?.let { handle -> runCatching(handle.cancel) }
        }
    }

    private fun clear(session: Session, owner: Any, isResponse: Boolean) {
        synchronized(lock) {
            val binding = if (isResponse) activeResponse else activeCall
            if (binding?.session !== session || binding.owner !== owner) return
            if (isResponse) {
                activeResponse = null
            } else {
                activeCall = null
            }
        }
    }
}
