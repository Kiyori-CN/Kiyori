package com.ai.assistance.operit.ui.main

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class AiHomeQuickAction {
    FOCUS_INPUT,
    OPEN_ATTACHMENTS,
    START_VOICE_SESSION,
    CAPTURE_PHOTO,
}

internal data class PendingAiHomeAction(
    val requestId: Long,
    val action: AiHomeQuickAction,
    val isAiHomeReady: Boolean,
)

internal class PendingAiHomeActionStore {
    private val requestIds = AtomicLong(0L)
    private val _pendingAction = MutableStateFlow<PendingAiHomeAction?>(null)
    val pendingAction: StateFlow<PendingAiHomeAction?> = _pendingAction.asStateFlow()

    fun request(action: AiHomeQuickAction): Long {
        val requestId = requestIds.incrementAndGet()
        _pendingAction.value =
            PendingAiHomeAction(
                requestId = requestId,
                action = action,
                isAiHomeReady = false,
            )
        return requestId
    }

    fun markAiHomeReady() {
        _pendingAction.update { pending -> pending?.copy(isAiHomeReady = true) }
    }

    fun consume(requestId: Long) {
        _pendingAction.update { pending ->
            if (pending?.requestId == requestId) null else pending
        }
    }
}

internal object PendingAiHomeActionHandler {
    private val store = PendingAiHomeActionStore()
    val pendingAction: StateFlow<PendingAiHomeAction?> = store.pendingAction

    fun request(action: AiHomeQuickAction): Long = store.request(action)

    fun markAiHomeReady() = store.markAiHomeReady()

    fun consume(requestId: Long) = store.consume(requestId)
}
