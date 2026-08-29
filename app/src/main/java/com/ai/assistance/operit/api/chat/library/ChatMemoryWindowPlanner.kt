package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode

/** Splits persisted chat turns into bounded, ordered memory-analysis windows. */
internal object ChatMemoryWindowPlanner {
    const val DEFAULT_WINDOW_MESSAGE_COUNT = 32
    const val MIN_WINDOW_MESSAGE_COUNT = 8
    const val MAX_WINDOW_MESSAGE_COUNT = 48

    data class Window(
        val messages: List<ChatMessage>,
        val sourceMessageCount: Int,
    )

    fun plan(messages: List<ChatMessage>, windowMessageCount: Int): List<Window> {
        val boundedWindowSize =
            windowMessageCount.coerceIn(MIN_WINDOW_MESSAGE_COUNT, MAX_WINDOW_MESSAGE_COUNT)
        val sourceMessages = messages
            .asSequence()
            .filter { it.content.isNotBlank() }
            .filter { it.displayMode != ChatMessageDisplayMode.HIDDEN_PLACEHOLDER }
            .filter { it.sender == "user" || it.sender == "ai" || it.sender == "assistant" }
            .sortedBy { it.timestamp }
            .toList()

        val windows = mutableListOf<Window>()
        val pendingSourceMessages = mutableListOf<ChatMessage>()
        val pendingContextMessages = mutableListOf<ChatMessage>()
        var currentTurnUser: ChatMessage? = null

        fun emitWindow() {
            if (pendingSourceMessages.isEmpty() || currentTurnUser == null) return
            windows += Window(
                messages = pendingContextMessages + pendingSourceMessages,
                sourceMessageCount = pendingSourceMessages.size,
            )
            pendingSourceMessages.clear()
            pendingContextMessages.clear()
        }

        sourceMessages.forEach { message ->
            when (message.sender) {
                "user" -> {
                    if (
                        pendingSourceMessages.isNotEmpty() &&
                        pendingSourceMessages.size >= boundedWindowSize - 1
                    ) {
                        emitWindow()
                    }
                    if (pendingSourceMessages.isEmpty()) {
                        currentTurnUser = null
                    }
                    currentTurnUser = message
                    pendingSourceMessages += message
                }

                "ai", "assistant" -> {
                    val turnUser = currentTurnUser ?: return@forEach
                    if (pendingSourceMessages.size >= boundedWindowSize) {
                        emitWindow()
                        pendingContextMessages += turnUser
                    }
                    pendingSourceMessages += message
                }
            }
        }

        emitWindow()
        return windows
    }
}
