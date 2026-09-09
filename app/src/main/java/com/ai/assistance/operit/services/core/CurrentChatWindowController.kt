package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CurrentChatWindowLoadResult(
    val messages: List<ChatMessage>,
    val hasOlderPersistedHistory: Boolean,
    val hasNewerPersistedHistory: Boolean,
)

enum class ChatWindowLoadOperation { SELECT, REFRESH, OLDER, NEWER, LATEST, LOCATE }

data class ChatWindowLoadFailure(
    val chatId: String,
    val operation: ChatWindowLoadOperation,
    val targetTimestamp: Long? = null,
    val historyChanged: Boolean = false,
)

internal data class CurrentChatWindowRequest(
    val chatId: String,
    val revision: Long,
    val operation: ChatWindowLoadOperation,
    val targetTimestamp: Long? = null,
    val messages: List<ChatMessage>,
    val hasOlderHistory: Boolean,
    val hasNewerHistory: Boolean,
)

internal data class CurrentChatWindowSnapshot(val chatId: String, val revision: Long, val messages: List<ChatMessage>)

internal class CurrentChatWindowController(
    private val chatIdFlow: MutableStateFlow<String?>,
    private val chatHistoryFlow: MutableStateFlow<List<ChatMessage>>,
) {
    private var revision = 0L
    private var activeRequest: CurrentChatWindowRequest? = null
    private val _loadFailure = MutableStateFlow<ChatWindowLoadFailure?>(null)
    val loadFailure: StateFlow<ChatWindowLoadFailure?> = _loadFailure.asStateFlow()
    private var displayStartTimestamp: Long? = null
    private var displayEndTimestamp: Long? = null

    private var hasOlderPersistedHistory = false
    private var hasNewerPersistedHistory = false

    private val _hasOlderDisplayHistory = MutableStateFlow(false)
    val hasOlderDisplayHistory: StateFlow<Boolean> = _hasOlderDisplayHistory.asStateFlow()

    private val _hasNewerDisplayHistory = MutableStateFlow(false)
    val hasNewerDisplayHistory: StateFlow<Boolean> = _hasNewerDisplayHistory.asStateFlow()

    private val _isLoadingDisplayWindow = MutableStateFlow(false)
    val isLoadingDisplayWindow: StateFlow<Boolean> = _isLoadingDisplayWindow.asStateFlow()

    @Synchronized
    fun selectChat(chatId: String?) {
        if (chatIdFlow.value == chatId) return
        reset()
        chatHistoryFlow.value = emptyList()
        chatIdFlow.value = chatId
    }

    @Synchronized
    fun clearMessages() {
        reset()
        chatHistoryFlow.value = emptyList()
    }

    @Synchronized
    fun snapshot(chatId: String): CurrentChatWindowSnapshot? =
        if (chatIdFlow.value == chatId) CurrentChatWindowSnapshot(chatId, revision, chatHistoryFlow.value.toList()) else null

    @Synchronized
    fun <T> withCurrentChat(chatId: String, block: () -> T): T? =
        if (chatIdFlow.value == chatId) block() else null

    @Synchronized
    fun applySnapshot(snapshot: CurrentChatWindowSnapshot, messages: List<ChatMessage>): Boolean {
        if (chatIdFlow.value != snapshot.chatId || revision != snapshot.revision) return false
        return applyMessages(snapshot.chatId, messages)
    }

    @Synchronized
    fun applyFlags(snapshot: CurrentChatWindowSnapshot, result: CurrentChatWindowLoadResult) {
        if (chatIdFlow.value != snapshot.chatId || revision != snapshot.revision) return
        applyMessagesInsideLock(snapshot.messages, result.hasOlderPersistedHistory, result.hasNewerPersistedHistory)
    }

    @Synchronized
    fun reportFailure(snapshot: CurrentChatWindowSnapshot, operation: ChatWindowLoadOperation) {
        if (chatIdFlow.value != snapshot.chatId || revision != snapshot.revision) return
        _loadFailure.value = ChatWindowLoadFailure(snapshot.chatId, operation)
    }

    @Synchronized
    fun dismissFailure(expected: ChatWindowLoadFailure): Boolean {
        // 同一聊天再次失败也属于新事件，迟到的关闭/重试不能消费新错误。
        if (_loadFailure.value !== expected || chatIdFlow.value != expected.chatId) return false
        _loadFailure.value = null
        return true
    }

    @Synchronized
    fun beginRetry(expected: ChatWindowLoadFailure): CurrentChatWindowRequest? {
        if (activeRequest != null || !dismissFailure(expected)) return null
        return beginLoad(expected.chatId, expected.operation, expected.targetTimestamp)
    }

    @Synchronized
    fun reset() {
        revision++
        activeRequest = null
        _loadFailure.value = null
        displayStartTimestamp = null
        displayEndTimestamp = null
        hasOlderPersistedHistory = false
        hasNewerPersistedHistory = false
        _hasOlderDisplayHistory.value = false
        _hasNewerDisplayHistory.value = false
        _isLoadingDisplayWindow.value = false
    }

    @Synchronized
    fun beginLoad(
        chatId: String,
        operation: ChatWindowLoadOperation,
        targetTimestamp: Long? = null,
        replace: Boolean = false,
    ): CurrentChatWindowRequest? {
        if (chatIdFlow.value != chatId || (activeRequest != null && !replace)) return null
        val request = CurrentChatWindowRequest(
            chatId, ++revision, operation, targetTimestamp,
            chatHistoryFlow.value.toList(), hasOlderPersistedHistory, hasNewerPersistedHistory,
        )
        activeRequest = request
        _isLoadingDisplayWindow.value = true
        _loadFailure.value = null
        return request
    }

    @Synchronized
    fun isCurrent(request: CurrentChatWindowRequest): Boolean =
        chatIdFlow.value == request.chatId && revision == request.revision

    @Synchronized
    fun applyLoadResult(
        request: CurrentChatWindowRequest,
        result: CurrentChatWindowLoadResult,
    ): Boolean {
        if (!isCurrent(request)) return false
        applyMessagesInsideLock(
            messages = result.messages,
            hasOlderPersistedHistory = result.hasOlderPersistedHistory,
            hasNewerPersistedHistory = result.hasNewerPersistedHistory,
        )
        _isLoadingDisplayWindow.value = false
        activeRequest = null
        _loadFailure.value = null
        revision++
        return true
    }

    @Synchronized
    fun applyMessages(
        chatId: String,
        messages: List<ChatMessage>,
        hasOlderPersistedHistory: Boolean? = null,
        hasNewerPersistedHistory: Boolean? = null,
    ): Boolean {
        if (chatIdFlow.value != chatId) return false
        // 运行时更新比已借出的读取快照新，不能让迟到分页覆盖刚收到的内容。
        activeRequest?.let {
            _loadFailure.value = ChatWindowLoadFailure(it.chatId, it.operation, it.targetTimestamp, historyChanged = true)
        }
        revision++
        activeRequest = null
        _isLoadingDisplayWindow.value = false
        applyMessagesInsideLock(messages, hasOlderPersistedHistory, hasNewerPersistedHistory)
        return true
    }

    private fun applyMessagesInsideLock(
        messages: List<ChatMessage>,
        hasOlderPersistedHistory: Boolean?,
        hasNewerPersistedHistory: Boolean?,
    ) {
        chatHistoryFlow.value = messages
        displayStartTimestamp = messages.firstOrNull()?.timestamp
        displayEndTimestamp = messages.lastOrNull()?.timestamp
        if (hasOlderPersistedHistory != null) {
            this.hasOlderPersistedHistory = hasOlderPersistedHistory
        }
        if (hasNewerPersistedHistory != null) {
            this.hasNewerPersistedHistory = hasNewerPersistedHistory
        }
        if (messages.isEmpty()) {
            this.hasOlderPersistedHistory = false
            this.hasNewerPersistedHistory = false
            _hasOlderDisplayHistory.value = false
            _hasNewerDisplayHistory.value = false
            return
        }

        _hasOlderDisplayHistory.value = this.hasOlderPersistedHistory
        _hasNewerDisplayHistory.value = this.hasNewerPersistedHistory
    }

    @Synchronized fun currentDisplayStartTimestamp(): Long? = displayStartTimestamp

    @Synchronized fun currentDisplayEndTimestamp(): Long? = displayEndTimestamp

    @Synchronized fun hasPersistedOlderHistoryNow(): Boolean = hasOlderPersistedHistory

    @Synchronized fun hasPersistedNewerHistoryNow(): Boolean = hasNewerPersistedHistory

    fun hasNewerDisplayHistoryNow(): Boolean = _hasNewerDisplayHistory.value

    @Synchronized
    fun finishLoad(request: CurrentChatWindowRequest, failed: Boolean) {
        if (!isCurrent(request)) return
        activeRequest = null
        _isLoadingDisplayWindow.value = false
        if (failed) _loadFailure.value = ChatWindowLoadFailure(request.chatId, request.operation, request.targetTimestamp)
        revision++
    }
}
