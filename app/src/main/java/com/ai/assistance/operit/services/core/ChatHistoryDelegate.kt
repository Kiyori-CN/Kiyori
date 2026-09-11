package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.WorkspaceRenameResult
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.ChatGroupTarget
import com.ai.assistance.operit.data.repository.ChatGroupCreationResult
import com.ai.assistance.operit.data.repository.completeChatCreation
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.ChatMessageTimestampAllocator
import com.ai.assistance.operit.data.model.ProviderUsageAggregate
import com.ai.assistance.operit.core.chat.ConversationCompactionCommitResult
import com.ai.assistance.operit.core.chat.ConversationCompactionSnapshot
import com.ai.assistance.operit.core.chat.ConversationCompactionRouteIdentity
import com.ai.assistance.operit.core.chat.ConversationCompactionUsage
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryRepairReport
import com.ai.assistance.operit.core.chat.ConversationToolResultPruningReport
import com.ai.assistance.operit.data.model.toProviderUsageAggregate
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgChatMessageHookBridge
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import com.ai.assistance.operit.data.repository.ChatSelectionReadState
import kotlinx.coroutines.flow.collectLatest

/** 委托类，负责管理聊天历史相关功能 */
class ChatHistoryDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val selectionMode: ChatSelectionMode = ChatSelectionMode.FOLLOW_GLOBAL,
        private val onTokenStatisticsLoaded:
            (
                chatId: String,
                inputTokens: Int,
                outputTokens: Int,
                windowSize: Int,
                providerUsage: ProviderUsageAggregate,
            ) -> Unit,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val ensureAiServiceAvailable: () -> Unit = {}, // 确保AI服务可用的回调
        private val getChatStatistics: () -> Triple<Long, Long, Long> = { Triple(0L, 0L, 0L) }, // 获取（输入token, 输出token, 窗口大小）
        private val getProviderUsageAggregate: () -> ProviderUsageAggregate,
        private val onScrollToBottom: () -> Unit = {}, // 滚动到底部事件回调
        private val onSelectionFailed: () -> Unit = {},
) {
    companion object {
        private const val TAG = "ChatHistoryDelegate"
        private const val DISPLAY_WINDOW_QUERY_BATCH_SIZE = 80
        // This constant is now in AIMessageManager
        // private const val SUMMARY_CHUNK_SIZE = 8

        private fun Long.toPersistedTokenCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private val chatHistoryManager = ChatHistoryManager.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context) // 新增
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val isInitialized = AtomicBoolean(false)
    private val historyUpdateMutex = Mutex()
    private val selectionMutex = Mutex()
    private var beforeDestructiveHistoryMutation: (suspend (String) -> Unit)? = null
    private var afterDestructiveHistoryMutation: (suspend (String) -> Unit)? = null

    // This is no longer needed here as summary logic is moved.
    // private val apiPreferences = ApiPreferences(context)

    // State flows
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()
    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()
    private val currentChatWindow = CurrentChatWindowController(_currentChatId, _chatHistory)
    val currentChatSelectionReadState = chatHistoryManager.currentChatSelectionReadState

    fun retryCurrentChatSelection(expected: ChatSelectionReadState.Failed) =
        chatHistoryManager.retryCurrentChatSelection(expected)

    val chatWindowLoadFailure: StateFlow<ChatWindowLoadFailure?> = currentChatWindow.loadFailure
    val hasOlderDisplayHistory: StateFlow<Boolean> = currentChatWindow.hasOlderDisplayHistory
    val hasNewerDisplayHistory: StateFlow<Boolean> = currentChatWindow.hasNewerDisplayHistory
    val isLoadingDisplayWindow: StateFlow<Boolean> = currentChatWindow.isLoadingDisplayWindow
    private val latestDisplayPageCountByChatId = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun setBeforeDestructiveHistoryMutation(handler: suspend (String) -> Unit) {
        beforeDestructiveHistoryMutation = handler
    }

    fun setAfterDestructiveHistoryMutation(handler: suspend (String) -> Unit) {
        afterDestructiveHistoryMutation = handler
    }

    private suspend fun prepareChatForDestructiveMutation(chatId: String) {
        beforeDestructiveHistoryMutation?.invoke(chatId)
    }

    private suspend fun finishDestructiveHistoryMutation(chatId: String) {
        afterDestructiveHistoryMutation?.invoke(chatId)
    }

    private fun clearCurrentChatHistoryInMemory() {
        currentChatWindow.clearMessages()
    }

    private fun setCurrentChatMessagesInMemory(
        chatId: String,
        messages: List<ChatMessage>,
        hasOlderPersistedHistory: Boolean? = null,
        hasNewerPersistedHistory: Boolean? = null,
    ) {
        currentChatWindow.applyMessages(
            chatId = chatId,
            messages = messages,
            hasOlderPersistedHistory = hasOlderPersistedHistory,
            hasNewerPersistedHistory = hasNewerPersistedHistory,
        )
    }

    private suspend fun refreshCurrentChatDisplayFlags(
        chatId: String,
    ) {
        val snapshot = currentChatWindow.snapshot(chatId) ?: return
        val loadResult = buildCurrentChatLoadResult(chatId, snapshot.messages)
        currentCoroutineContext().ensureActive()
        currentChatWindow.applyFlags(snapshot, loadResult)
    }

    private suspend fun buildCurrentChatLoadResult(
        chatId: String,
        messages: List<ChatMessage>,
    ): CurrentChatWindowLoadResult {
        val displayStartTimestamp = messages.firstOrNull()?.timestamp
        val displayEndTimestamp = messages.lastOrNull()?.timestamp
        val hasOlderPersistedHistory =
            displayStartTimestamp != null &&
                chatHistoryManager.hasMessagesBefore(chatId, displayStartTimestamp)
        val hasNewerPersistedHistory =
            displayEndTimestamp != null &&
                chatHistoryManager.hasMessagesAfter(chatId, displayEndTimestamp)
        return CurrentChatWindowLoadResult(
            messages = messages,
            hasOlderPersistedHistory = hasOlderPersistedHistory,
            hasNewerPersistedHistory = hasNewerPersistedHistory,
        )
    }

    private suspend fun applyCurrentChatDisplayWindow(
        request: CurrentChatWindowRequest,
        messages: List<ChatMessage>,
    ): List<ChatMessage> {
        val chatId = request.chatId
        val loadResult = buildCurrentChatLoadResult(chatId, messages)
        currentCoroutineContext().ensureActive()
        if (!currentChatWindow.applyLoadResult(request, loadResult)) {
            throw CancellationException("Chat window read was superseded")
        }
        if (loadResult.messages.isEmpty()) {
            latestDisplayPageCountByChatId.remove(chatId)
        } else if (!loadResult.hasNewerPersistedHistory) {
            latestDisplayPageCountByChatId[chatId] =
                countDisplayPages(loadResult.messages).coerceIn(1, MAX_DISPLAY_PAGE_COUNT)
        }
        return loadResult.messages
    }

    private suspend fun collectNewestDisplayPages(
        chatId: String,
        pageCount: Int,
        endTimestampInclusive: Long? = null,
    ): List<ChatMessage> {
        val collectedMessagesDesc = mutableListOf<ChatMessage>()
        var beforeTimestampExclusive: Long? = null

        while (true) {
            val batch =
                if (endTimestampInclusive != null && beforeTimestampExclusive == null) {
                    chatHistoryManager.loadChatMessagesDescUpTo(
                        chatId = chatId,
                        maxTimestampInclusive = endTimestampInclusive,
                        limit = DISPLAY_WINDOW_QUERY_BATCH_SIZE,
                    )
                } else {
                    chatHistoryManager.loadChatMessagesDesc(
                        chatId = chatId,
                        limit = DISPLAY_WINDOW_QUERY_BATCH_SIZE,
                        beforeTimestampExclusive = beforeTimestampExclusive,
                    )
                }
            if (batch.isEmpty()) {
                break
            }

            collectedMessagesDesc += batch
            val currentAscendingMessages = collectedMessagesDesc.asReversed()
            if (countDisplayPages(currentAscendingMessages) >= pageCount) {
                return takeNewestDisplayPages(currentAscendingMessages, pageCount)
            }

            beforeTimestampExclusive = batch.lastOrNull()?.timestamp
            if (batch.size < DISPLAY_WINDOW_QUERY_BATCH_SIZE || beforeTimestampExclusive == null) {
                break
            }
        }

        return takeNewestDisplayPages(collectedMessagesDesc.asReversed(), pageCount)
    }

    private suspend fun collectOlderDisplayPagesBefore(
        chatId: String,
        beforeTimestampExclusive: Long,
        pageCount: Int,
    ): List<ChatMessage> {
        val collectedMessagesDesc = mutableListOf<ChatMessage>()
        var nextBeforeTimestampExclusive = beforeTimestampExclusive

        while (true) {
            val batch =
                chatHistoryManager.loadChatMessagesDesc(
                    chatId = chatId,
                    limit = DISPLAY_WINDOW_QUERY_BATCH_SIZE,
                    beforeTimestampExclusive = nextBeforeTimestampExclusive,
                )
            if (batch.isEmpty()) {
                break
            }

            collectedMessagesDesc += batch
            val currentAscendingMessages = collectedMessagesDesc.asReversed()
            if (countDisplayPages(currentAscendingMessages) >= pageCount) {
                return takeNewestDisplayPages(currentAscendingMessages, pageCount)
            }

            nextBeforeTimestampExclusive = batch.lastOrNull()?.timestamp ?: break
            if (batch.size < DISPLAY_WINDOW_QUERY_BATCH_SIZE) {
                break
            }
        }

        return takeNewestDisplayPages(collectedMessagesDesc.asReversed(), pageCount)
    }

    private suspend fun collectNewerDisplayPagesAfter(
        chatId: String,
        afterTimestampExclusive: Long,
        pageCount: Int,
    ): List<ChatMessage> {
        val collectedMessagesAsc = mutableListOf<ChatMessage>()
        var nextAfterTimestampExclusive = afterTimestampExclusive

        while (true) {
            val batch =
                chatHistoryManager.loadChatMessagesAscAfter(
                    chatId = chatId,
                    afterTimestampExclusive = nextAfterTimestampExclusive,
                    limit = DISPLAY_WINDOW_QUERY_BATCH_SIZE,
                )
            if (batch.isEmpty()) {
                break
            }

            collectedMessagesAsc += batch
            if (countDisplayPages(collectedMessagesAsc) >= pageCount) {
                return takeOldestDisplayPages(collectedMessagesAsc, pageCount)
            }

            nextAfterTimestampExclusive = batch.lastOrNull()?.timestamp ?: break
            if (batch.size < DISPLAY_WINDOW_QUERY_BATCH_SIZE) {
                break
            }
        }

        return takeOldestDisplayPages(collectedMessagesAsc, pageCount)
    }

    private suspend fun loadLatestCurrentChatDisplayWindow(
        chatId: String,
        pageCount: Int = 1,
        request: CurrentChatWindowRequest = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.REFRESH, replace = true)
            ?: throw CancellationException("Chat selection changed"),
    ): List<ChatMessage> {
        return withWindowRequest(request) { applyCurrentChatDisplayWindow(
            request = request,
            messages = collectNewestDisplayPages(chatId, pageCount.coerceIn(1, MAX_DISPLAY_PAGE_COUNT)),
        ) }
    }

    private suspend fun <T> withWindowRequest(request: CurrentChatWindowRequest, block: suspend () -> T): T {
        try {
            return block()
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (failure: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            throw failure
        }
    }

    private suspend fun reloadCurrentChatDisplayHistory(
        chatId: String,
        request: CurrentChatWindowRequest = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.REFRESH, replace = true)
            ?: throw CancellationException("Chat selection changed"),
    ): List<ChatMessage> {
        return withWindowRequest(request) {
            val currentMessages = request.messages
            if (currentMessages.isEmpty()) {
                return@withWindowRequest loadLatestCurrentChatDisplayWindow(chatId, request = request)
            }

            val currentPageCount = countDisplayPages(currentMessages).coerceIn(1, MAX_DISPLAY_PAGE_COUNT)
            val reloadedMessages =
                if (request.hasNewerHistory) {
                    val displayEndTimestamp = currentMessages.lastOrNull()?.timestamp
                    if (displayEndTimestamp == null) {
                        collectNewestDisplayPages(chatId, currentPageCount)
                    } else {
                        collectNewestDisplayPages(
                            chatId = chatId,
                            pageCount = currentPageCount,
                            endTimestampInclusive = displayEndTimestamp,
                        )
                    }
                } else {
                    collectNewestDisplayPages(chatId, currentPageCount)
                }

            applyCurrentChatDisplayWindow(request, reloadedMessages)
        }
    }

    private suspend fun runDestructiveHistoryMutation(
        chatId: String,
        mutation: suspend () -> Boolean
    ) {
        prepareChatForDestructiveMutation(chatId)
        val didMutate = historyUpdateMutex.withLock { mutation() }
        if (didMutate) {
            finishDestructiveHistoryMutation(chatId)
        }
    }

    private suspend fun runCurrentChatDestructiveHistoryMutation(
        mismatchMessage: String,
        expectedChatId: String? = _currentChatId.value,
        mutation: suspend (String) -> Boolean
    ): Boolean {
        val chatIdSnapshot = expectedChatId ?: return false
        if (_currentChatId.value != chatIdSnapshot) return false
        prepareChatForDestructiveMutation(chatIdSnapshot)
        val didMutate =
            historyUpdateMutex.withLock {
                val currentChatId = _currentChatId.value
                if (currentChatId != chatIdSnapshot) {
                    AppLogger.w(
                        TAG,
                        "$mismatchMessage: expected=$chatIdSnapshot, actual=$currentChatId"
                    )
                    return@withLock false
                }
                mutation(chatIdSnapshot)
            }
        if (didMutate) {
            finishDestructiveHistoryMutation(chatIdSnapshot)
        }
        return didMutate
    }

    suspend fun getChatHistory(chatId: String): List<ChatMessage> =
        chatHistoryManager.loadChatMessages(chatId)

    suspend fun getRuntimeChatHistory(chatId: String): List<ChatMessage> =
        chatHistoryManager.loadRuntimeChatMessages(chatId)

    suspend fun getRuntimeChatHistoryForCompaction(chatId: String): List<ChatMessage> =
        chatHistoryManager.loadRuntimeChatMessagesForCompaction(chatId)

    suspend fun repairAssistantReplayHistory(
        chatId: String,
    ): AssistantReplayHistoryRepairReport {
        val report =
            historyUpdateMutex.withLock {
                chatHistoryManager.repairAssistantReplayHistory(chatId)
            }
        if (report.repairedMessageCount > 0 && chatId == _currentChatId.value) {
            reloadCurrentChatDisplayHistory(chatId)
        }
        return report
    }

    suspend fun getMessagePredecessorTimestamp(chatId: String, targetTimestamp: Long): Long? =
        chatHistoryManager.getMessagePredecessorTimestamp(chatId, targetTimestamp)

    suspend fun getRuntimeChatHistoryUpTo(
        chatId: String,
        upToTimestampInclusive: Long
    ): List<ChatMessage> =
        chatHistoryManager.loadRuntimeChatMessagesUpTo(chatId, upToTimestampInclusive)

    suspend fun getCurrentRuntimeChatHistorySnapshot(): List<ChatMessage> {
        val chatId = _currentChatId.value ?: return emptyList()
        return chatHistoryManager.loadRuntimeChatMessages(chatId)
    }

    suspend fun loadMessagesForCompactionInsertion(
        chatId: String,
        beforeTimestampExclusive: Long? = null,
        upToTimestampInclusive: Long? = null,
    ): List<ChatMessage> =
        chatHistoryManager.loadMessagesForCompactionInsertion(
            chatId = chatId,
            beforeTimestampExclusive = beforeTimestampExclusive,
            upToTimestampInclusive = upToTimestampInclusive,
        )

    suspend fun recordConversationCompactionRequested(
        snapshot: ConversationCompactionSnapshot,
    ) {
        chatHistoryManager.recordConversationCompactionRequested(snapshot)
    }

    suspend fun recordConversationCompactionGenerated(
        snapshot: ConversationCompactionSnapshot,
        summaryMessage: ChatMessage,
        usage: ConversationCompactionUsage,
        pruningReport: ConversationToolResultPruningReport,
    ) {
        chatHistoryManager.recordConversationCompactionGenerated(
            snapshot = snapshot,
            summaryMessage = summaryMessage,
            usage = usage,
            pruningReport = pruningReport,
        )
    }

    suspend fun commitConversationCompaction(
        snapshot: ConversationCompactionSnapshot,
        summaryMessage: ChatMessage,
        usage: ConversationCompactionUsage,
        pruningReport: ConversationToolResultPruningReport,
        currentRouteIdentity: ConversationCompactionRouteIdentity,
    ): ConversationCompactionCommitResult {
        val result =
            chatHistoryManager.commitConversationCompaction(
                snapshot = snapshot,
                summaryMessage = summaryMessage,
                usage = usage,
                pruningReport = pruningReport,
                currentRouteIdentity = currentRouteIdentity,
            )
        if (
            result.persistedSummaryMessage != null &&
                snapshot.chatId == _currentChatId.value
        ) {
            reloadCurrentChatDisplayHistory(snapshot.chatId)
        }
        return result
    }

    suspend fun loadChatMessageLocatorPreviews(
        chatId: String,
        query: String = "",
    ): List<ChatMessageLocatorPreview> =
        chatHistoryManager.loadChatMessageLocatorPreviews(chatId, query)

    suspend fun hasUserMessage(chatId: String): Boolean = chatHistoryManager.hasUserMessage(chatId)

    suspend fun revealMessageForCurrentChat(targetTimestamp: Long): Boolean {
        if (_chatHistory.value.any { it.timestamp == targetTimestamp }) {
            return true
        }

        val chatId = _currentChatId.value ?: return false
        val request = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.LOCATE, targetTimestamp)
            ?: return false
        return revealMessage(request)
    }

    private suspend fun revealMessage(request: CurrentChatWindowRequest): Boolean {
        val chatId = request.chatId
        val targetTimestamp = checkNotNull(request.targetTimestamp)
        return try {
            val locatorEntries = chatHistoryManager.loadChatMessageLocatorPreviews(chatId)
            val pageRanges = resolveDisplayPageRanges(locatorEntries)
            val targetPageIndex =
                pageRanges.indexOfFirst { range ->
                    targetTimestamp in range.startTimestampInclusive..range.endTimestampInclusive
                }
            if (targetPageIndex < 0) {
                currentChatWindow.finishLoad(request, failed = false)
                return false
            }

            val windowStartPageIndex =
                if (targetPageIndex < pageRanges.lastIndex) {
                    targetPageIndex
                } else {
                    (targetPageIndex - (MAX_DISPLAY_PAGE_COUNT - 1)).coerceAtLeast(0)
                }
            val windowEndPageIndex =
                (windowStartPageIndex + MAX_DISPLAY_PAGE_COUNT - 1).coerceAtMost(pageRanges.lastIndex)
            val revealedMessages =
                chatHistoryManager.loadChatMessagesWindow(
                    chatId = chatId,
                    startTimestampInclusive = pageRanges[windowStartPageIndex].startTimestampInclusive,
                    endTimestampInclusive = pageRanges[windowEndPageIndex].endTimestampInclusive,
                )
            applyCurrentChatDisplayWindow(request, revealedMessages)
            _chatHistory.value.any { it.timestamp == targetTimestamp }
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (e: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            AppLogger.e(TAG, "定位当前聊天消息失败: ${e.javaClass.simpleName}")
            false
        }
    }

    suspend fun loadOlderMessagesForCurrentChat(): Boolean {
        val chatId = _currentChatId.value ?: return false
        val request = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.OLDER) ?: return false
        return loadOlderMessages(request)
    }

    private suspend fun loadOlderMessages(request: CurrentChatWindowRequest): Boolean {
        val chatId = request.chatId
        val currentMessages = request.messages
        val displayStartTimestamp = currentMessages.firstOrNull()?.timestamp
        if (displayStartTimestamp == null || !request.hasOlderHistory) {
            currentChatWindow.finishLoad(request, failed = false)
            return false
        }

        return try {
            val currentPageCount = countDisplayPages(currentMessages).coerceIn(1, MAX_DISPLAY_PAGE_COUNT)
            val olderPage =
                collectOlderDisplayPagesBefore(
                    chatId = chatId,
                    beforeTimestampExclusive = displayStartTimestamp,
                    pageCount = 1,
                )
            if (olderPage.isEmpty()) {
                applyCurrentChatDisplayWindow(request, currentMessages)
                false
            } else {
                val retainedCurrentMessages =
                    if (currentPageCount < MAX_DISPLAY_PAGE_COUNT) {
                        currentMessages
                    } else {
                        takeOldestDisplayPages(currentMessages, MAX_DISPLAY_PAGE_COUNT - 1)
                    }
                applyCurrentChatDisplayWindow(
                    request = request,
                    messages = olderPage + retainedCurrentMessages,
                )
                true
            }
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (e: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            AppLogger.e(TAG, "加载当前聊天更早历史失败: ${e.javaClass.simpleName}")
            false
        }
    }

    suspend fun loadNewerMessagesForCurrentChat(): Boolean {
        val chatId = _currentChatId.value ?: return false
        val request = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.NEWER) ?: return false
        return loadNewerMessages(request)
    }

    private suspend fun loadNewerMessages(request: CurrentChatWindowRequest): Boolean {
        val chatId = request.chatId
        val currentMessages = request.messages
        if (currentMessages.isEmpty()) {
            currentChatWindow.finishLoad(request, failed = false)
            return false
        }
        val currentPageCount = countDisplayPages(currentMessages).coerceIn(1, MAX_DISPLAY_PAGE_COUNT)
        if (
            !request.hasNewerHistory &&
                currentPageCount <= 1
        ) {
            currentChatWindow.finishLoad(request, failed = false)
            return false
        }

        return try {
            val retainedNewestMessages =
                takeNewestDisplayPages(currentMessages, (currentPageCount - 1).coerceAtLeast(0))
            val newerPage =
                currentMessages.lastOrNull()?.timestamp?.let { displayEndTimestamp ->
                    if (request.hasNewerHistory) {
                        collectNewerDisplayPagesAfter(
                            chatId = chatId,
                            afterTimestampExclusive = displayEndTimestamp,
                            pageCount = 1,
                        )
                    } else {
                        emptyList()
                    }
                }.orEmpty()

            val nextMessages =
                when {
                    newerPage.isNotEmpty() -> retainedNewestMessages + newerPage
                    retainedNewestMessages.isNotEmpty() -> retainedNewestMessages
                    else -> takeNewestDisplayPages(currentMessages, 1)
                }
            applyCurrentChatDisplayWindow(request, nextMessages)
            true
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (e: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            AppLogger.e(TAG, "加载当前聊天更新历史失败: ${e.javaClass.simpleName}")
            false
        }
    }

    suspend fun showLatestMessagesForCurrentChat(): Boolean {
        val chatId = _currentChatId.value ?: return false
        if (!currentChatWindow.hasNewerDisplayHistoryNow() && _chatHistory.value.isNotEmpty()) {
            return false
        }
        val request = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.LATEST) ?: return false
        return showLatestMessages(request)
    }

    private suspend fun showLatestMessages(request: CurrentChatWindowRequest): Boolean {
        val chatId = request.chatId
        return try {
            loadLatestCurrentChatDisplayWindow(chatId, request = request)
            true
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (e: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            AppLogger.e(TAG, "切换到当前聊天最新窗口失败: ${e.javaClass.simpleName}")
            false
        }
    }

    fun dismissChatWindowLoadFailure(expected: ChatWindowLoadFailure) {
        currentChatWindow.dismissFailure(expected)
    }

    suspend fun retryChatWindowLoad(expected: ChatWindowLoadFailure) {
        // 消费错误和借出新请求必须原子完成；等待历史锁期间切chat也不改变重试目标。
        val request = currentChatWindow.beginRetry(expected) ?: return
        when (expected.operation) {
            ChatWindowLoadOperation.SELECT -> loadSelectedChat(expected.chatId, request)
            ChatWindowLoadOperation.REFRESH -> {
                try {
                    withWindowRequest(request) {
                        historyUpdateMutex.withLock { reloadCurrentChatDisplayHistory(expected.chatId, request) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLogger.e(TAG, "Chat window retry failed: ${failure.javaClass.simpleName}")
                }
            }
            ChatWindowLoadOperation.OLDER -> loadOlderMessages(request)
            ChatWindowLoadOperation.NEWER -> loadNewerMessages(request)
            ChatWindowLoadOperation.LATEST -> showLatestMessages(request)
            ChatWindowLoadOperation.LOCATE -> revealMessage(request)
        }
    }

    private val _showChatHistorySelector = MutableStateFlow(false)
    val showChatHistorySelector: StateFlow<Boolean> = _showChatHistorySelector.asStateFlow()

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()


    // This is no longer the responsibility of this delegate
    // private var summarizationPerformed = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        coroutineScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                _chatHistories.value = histories
                val currentId = _currentChatId.value
                if (currentId != null && histories.none { it.id == currentId }) {
                    val snapshot = currentChatWindow.snapshot(currentId)
                    try {
                        if (!chatHistoryManager.chatExists(currentId)) {
                            val cleared = selectionMode == ChatSelectionMode.LOCAL_ONLY ||
                                chatHistoryManager.compareAndSetCurrentChatId(currentId, null)
                            if (cleared) currentChatWindow.withCurrentChat(currentId) { currentChatWindow.selectChat(null) }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        AppLogger.e(TAG, "Chat existence check failed: ${failure.javaClass.simpleName}")
                        snapshot?.let { currentChatWindow.reportFailure(it, ChatWindowLoadOperation.REFRESH) }
                    }
                }
            }
        }

        coroutineScope.launch {
            var localInitialized = false
            currentChatSelectionReadState.collectLatest { state ->
                when (state) {
                    ChatSelectionReadState.Loading -> Unit
                    is ChatSelectionReadState.Failed -> onSelectionFailed()
                    is ChatSelectionReadState.Ready -> {
                        if (selectionMode == ChatSelectionMode.FOLLOW_GLOBAL) {
                            loadSelectedChat(state.chatId)
                        } else if (!localInitialized) {
                            // 首次有效读取才能初始化浮窗；初始化不能迟到覆盖用户已经选择的窗口。
                            selectionMutex.withLock {
                                if (_currentChatId.value == null) loadSelectedChat(state.chatId)
                                localInitialized = true
                            }
                        }
                    }
                }
            }
        }

        // 监听活跃目标变更：仅当当前为角色卡时才同步开场白
        coroutineScope.launch {
            activePromptManager.activePromptFlow.collectLatest { activePrompt ->
                if (activePrompt is ActivePrompt.CharacterCard) {
                    val chatId = _currentChatId.value ?: return@collectLatest
                    val snapshot = currentChatWindow.snapshot(chatId) ?: return@collectLatest
                    try {
                        syncOpeningStatementIfNoUserMessage(chatId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        currentChatWindow.reportFailure(snapshot, ChatWindowLoadOperation.SELECT)
                        AppLogger.e(TAG, "Opening statement sync failed: ${failure.javaClass.simpleName}")
                    }
                }
            }
        }
    }

    private suspend fun loadSelectedChat(chatId: String?, retryRequest: CurrentChatWindowRequest? = null): Boolean {
        if (retryRequest == null) currentChatWindow.selectChat(chatId)
        if (chatId == null) {
            return true
        }
        val request = retryRequest ?: currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.SELECT, replace = true) ?: return false
        try {
            if (!chatHistoryManager.chatExists(chatId)) {
                val cleared = selectionMode == ChatSelectionMode.LOCAL_ONLY ||
                    chatHistoryManager.compareAndSetCurrentChatId(chatId, null)
                if (cleared) currentChatWindow.withCurrentChat(chatId) { currentChatWindow.selectChat(null) }
                currentChatWindow.finishLoad(request, failed = false)
                return false
            }
            return loadChatMessages(chatId, request)
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (failure: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            AppLogger.e(TAG, "Chat selection load failed: ${failure.javaClass.simpleName}")
            return false
        }
    }

    private suspend fun loadChatMessages(
        chatId: String,
        request: CurrentChatWindowRequest = currentChatWindow.beginLoad(chatId, ChatWindowLoadOperation.SELECT, replace = true)
            ?: throw CancellationException("Chat selection changed"),
    ): Boolean {
        var openingSnapshot: CurrentChatWindowSnapshot? = null
        try {
            val initialPageCount = latestDisplayPageCountByChatId[chatId] ?: 1
            loadLatestCurrentChatDisplayWindow(chatId, pageCount = initialPageCount, request = request)
            openingSnapshot = currentChatWindow.snapshot(chatId) ?: return false
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                onTokenStatisticsLoaded(chatId, selectedChat.inputTokens, selectedChat.outputTokens,
                    selectedChat.currentWindowSize, selectedChat.toProviderUsageAggregate())
            }
            syncOpeningStatementIfNoUserMessage(chatId)
            return _currentChatId.value == chatId
        } catch (cancelled: CancellationException) {
            currentChatWindow.finishLoad(request, failed = false)
            throw cancelled
        } catch (failure: Exception) {
            currentChatWindow.finishLoad(request, failed = true)
            openingSnapshot?.let { currentChatWindow.reportFailure(it, ChatWindowLoadOperation.SELECT) }
            AppLogger.e(TAG, "Chat message load failed: ${failure.javaClass.simpleName}")
            return false
        }
    }

    /**
     * 智能重新加载聊天消息，通过 timestamp 匹配已存在的消息，保持原实例不变
     * 这样可以防止UI重组，提高性能
     * 
     * @param chatId 聊天ID
     */
    suspend fun reloadChatMessagesSmart(chatId: String) {
        historyUpdateMutex.withLock {
            try {
                val reloadedMessages = reloadCurrentChatDisplayHistory(chatId)
                AppLogger.d(TAG, "智能重新加载聊天 $chatId 完成: ${reloadedMessages.size} 条消息")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "Chat reload failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun syncOpeningStatementIfNoUserMessage(chatId: String) {
        AppLogger.d(TAG, "开始同步开场白，聊天ID: $chatId")

        historyUpdateMutex.withLock {
            if (_currentChatId.value != chatId) return@withLock
            // 首次读取未完成时，空窗口并不意味着数据库没有开场白；交给读取完成后的同步。
            if (isLoadingDisplayWindow.value || chatWindowLoadFailure.value != null) return@withLock
            val chatMeta = _chatHistories.value.firstOrNull { it.id == chatId }
            if (!chatMeta?.characterGroupId.isNullOrBlank()) {
                AppLogger.d(TAG, "聊天 $chatId 绑定群组角色卡，跳过开场白同步")
                return@withLock
            }

            val hasUserMessage = chatHistoryManager.hasUserMessage(chatId)
            
            AppLogger.d(
                TAG,
                "从数据库检查消息 - 内存消息数: ${_chatHistory.value.size}, 是否有用户消息: $hasUserMessage",
            )
            
            if (hasUserMessage) {
                AppLogger.d(TAG, "聊天 $chatId 已存在用户消息，跳过开场白同步")
                return@withLock
            }

            val boundCardName = chatMeta?.characterCardName
            val boundCard = boundCardName?.let { characterCardManager.findCharacterCardByName(it) }
            val activePrompt = activePromptManager.getActivePrompt()
            val activeCard = when (activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                is ActivePrompt.CharacterGroup -> null
            }
            val effectiveCard = boundCard ?: activeCard

            // 如果没有有效的角色卡，使用默认角色卡
            if (effectiveCard == null) {
                AppLogger.d(TAG, "没有有效的角色卡，跳过开场白处理")
                return@withLock
            }

            val opening = effectiveCard.openingStatement
            val roleName = effectiveCard.name
            if (boundCard == null && boundCardName != null) {
                AppLogger.w(TAG, "绑定角色卡未找到，回退使用当前活跃角色卡: $boundCardName")
            }
            AppLogger.d(TAG, "获取角色卡信息 - 名称: $roleName, 开场白长度: ${opening.length}, 是否为空: ${opening.isBlank()}, 绑定角色卡: $boundCardName")

            // 使用数据库中的消息作为基准，但优先使用内存中的消息（如果已加载）
            val snapshot = currentChatWindow.snapshot(chatId) ?: return@withLock
            if (isLoadingDisplayWindow.value || chatWindowLoadFailure.value != null) return@withLock
            val currentMessages = snapshot.messages.toMutableList()
            val existingIndex = currentMessages.indexOfFirst { it.sender == "ai" }
            AppLogger.d(TAG, "当前消息数量: ${currentMessages.size}, 现有AI消息索引: $existingIndex")

            if (existingIndex >= 0) {
                val existing = currentMessages[existingIndex]
                val isOpeningMessage = existing.provider.isBlank() && existing.modelName.isBlank()
                if (opening.isNotBlank()) {
                    if (isOpeningMessage) {
                        if (existing.content != opening || existing.roleName != roleName) {
                            AppLogger.d(TAG, "更新现有开场白消息 - 原内容长度: ${existing.content.length}, 新内容长度: ${opening.length}, 原角色名: ${existing.roleName}, 新角色名: $roleName")
                            val updated = existing.copy(content = opening, roleName = roleName)
                            currentMessages[existingIndex] = updated
                            chatHistoryManager.updateMessage(chatId, updated)
                            currentCoroutineContext().ensureActive()
                            currentChatWindow.applySnapshot(snapshot, currentMessages)
                            AppLogger.d(TAG, "开场白消息更新完成")
                        } else {
                            AppLogger.d(TAG, "开场白内容未变化，无需更新")
                        }
                    } else {
                        AppLogger.d(TAG, "已有AI消息非开场白，跳过同步")
                    }
                } else {
                    if (isOpeningMessage) {
                        AppLogger.d(TAG, "开场白为空，删除现有AI开场白消息，时间戳: ${existing.timestamp}")
                        currentMessages.removeAt(existingIndex)
                        chatHistoryManager.deleteMessage(chatId, existing.timestamp)
                        currentCoroutineContext().ensureActive()
                        currentChatWindow.applySnapshot(snapshot, currentMessages)
                        AppLogger.d(TAG, "AI消息删除完成")
                    } else {
                        AppLogger.d(TAG, "开场白为空但现有AI消息非开场白，跳过删除")
                    }
                }
            } else if (opening.isNotBlank()) {
                val openingMessage = ChatMessage(
                    sender = "ai",
                    content = opening,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = roleName,
                    provider = "", // 开场白不是AI生成，使用空值
                    modelName = "" // 开场白不是AI生成，使用空值
                )
                AppLogger.d(TAG, "添加新开场白消息 - 时间戳: ${openingMessage.timestamp}, 角色名: $roleName, 内容长度: ${opening.length}")
                currentMessages.add(openingMessage)
                chatHistoryManager.addMessage(chatId, openingMessage)
                currentCoroutineContext().ensureActive()
                currentChatWindow.applySnapshot(snapshot, currentMessages)
                AppLogger.d(TAG, "开场白消息添加完成，当前消息总数: ${currentMessages.size}")
            } else {
                AppLogger.d(TAG, "无现有AI消息且开场白为空，无需操作")
            }
        }
        
        AppLogger.d(TAG, "开场白同步完成，聊天ID: $chatId")
    }

    /** 创建新的聊天 */
    fun createNewChat(
        characterCardName: String? = null,
        characterGroupId: String? = null,
        group: String? = null,
        inheritGroupFromCurrent: Boolean = true,
        setAsCurrentChat: Boolean = true,
        characterCardId: String? = null
    ) {
        coroutineScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                actualContextWindowSize = windowSize,
                providerUsage = getProviderUsageAggregate(),
            ) // 使用获取到的完整统计数据

            // 获取当前对话ID，以便继承分组
            val currentChatId = _currentChatId.value
            val inheritGroupFromChatId = if (inheritGroupFromCurrent) currentChatId else null
            
            // 获取当前活跃的角色卡
            val activePrompt = activePromptManager.getActivePrompt()
            val activeCard = when (activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                is ActivePrompt.CharacterGroup -> null
            }
            val resolvedCard =
                if (characterGroupId.isNullOrBlank()) {
                    characterCardId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { characterCardManager.getCharacterCard(it) }
                        ?: activeCard
                } else {
                    null  // 群组模式下不使用角色卡
                }

            // 确定角色卡名称：如果参数指定了则使用参数，否则使用目标角色卡
            val effectiveCharacterCardName =
                if (characterGroupId.isNullOrBlank()) {
                    characterCardName ?: resolvedCard?.name
                } else {
                    null  // 群组模式下不使用角色卡名称
                }

            val shouldSyncCurrentChatToGlobal =
                selectionMode == ChatSelectionMode.FOLLOW_GLOBAL && setAsCurrentChat

            // 创建新对话，如果有当前对话则继承其分组，并绑定角色卡
            val newChat = chatHistoryManager.createNewChat(
                group = group,
                inheritGroupFromChatId = inheritGroupFromChatId,
                characterCardName = effectiveCharacterCardName,
                characterGroupId = characterGroupId,
                setAsCurrentChat = shouldSyncCurrentChatToGlobal
            )

            // --- 新增：检查并添加开场白（群组模式跳过） ---
            if (characterGroupId.isNullOrBlank() && characterCardName == null && resolvedCard != null && resolvedCard.openingStatement.isNotBlank()) {
                val openingMessage = ChatMessage(
                    sender = "ai",
                    content = resolvedCard.openingStatement,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = resolvedCard.name, // 使用角色卡的名称
                    provider = "", // 开场白不是AI生成，使用空值
                    modelName = "" // 开场白不是AI生成，使用空值
                )
                // 保存带开场白的消息到数据库
                chatHistoryManager.addMessage(newChat.id, openingMessage)
            }
            // --- 结束 ---
            
            // 等待数据库Flow更新，确保新对话在列表中（最多等待500ms）
            withTimeoutOrNull(500) {
                _chatHistories.first { histories ->
                    histories.any { it.id == newChat.id }
                }
            }
            
            if (setAsCurrentChat) {
                if (selectionMode == ChatSelectionMode.FOLLOW_GLOBAL) {
                    // FOLLOW_GLOBAL 由 currentChatId 的 collector 负责驱动切换与加载。
                    chatHistoryManager.setCurrentChatId(newChat.id)
                } else {
                    // LOCAL_ONLY 不写回全局 currentChatId，只切换悬浮窗自己的本地会话。
                    currentChatWindow.selectChat(newChat.id)
                    loadChatMessages(newChat.id)
                }
                onTokenStatisticsLoaded(
                    newChat.id,
                    0,
                    0,
                    0,
                    newChat.toProviderUsageAggregate(),
                )
            }
        }
    }

    /** 兼容无需等待的服务入口，错误仍须反馈，不能抛出未处理的launch异常。 */
    fun switchChat(chatId: String, syncToGlobal: Boolean = true) {
        coroutineScope.launch {
            try {
                if (!switchChatAwait(chatId, syncToGlobal)) onSelectionFailed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e(TAG, "Chat switch failed: ${failure.javaClass.simpleName}")
                onSelectionFailed()
            }
        }
    }

    /** 串行完成原聊天保存与目标选择；离开UI只取消等待，不拆断服务持有的提交。 */
    suspend fun switchChatAwait(
        chatId: String,
        syncToGlobal: Boolean = true,
        stillRequested: () -> Boolean = { true },
    ): Boolean {
        val requesterJob = currentCoroutineContext().job
        return coroutineScope.async {
            selectionMutex.withLock {
                if (!stillRequested()) return@withLock false
                val origin = _currentChatId.value
                val (inputTokens, outputTokens, windowSize) = getChatStatistics()
                val usage = getProviderUsageAggregate()
                val persistedOrigin = if (syncToGlobal) chatHistoryManager.readPersistedCurrentChatId() else null
                if (!chatHistoryManager.chatExists(chatId)) return@withLock false
                if (origin != null) {
                    saveCurrentChat(inputTokens, outputTokens, windowSize, usage, chatIdOverride = origin)
                }
                if (!stillRequested() || _currentChatId.value != origin) return@withLock false
                if (syncToGlobal) {
                    if (!chatHistoryManager.compareAndSetCurrentChatId(persistedOrigin, chatId)) return@withLock false
                    if (selectionMode == ChatSelectionMode.FOLLOW_GLOBAL) {
                        // 等待实际选择投影，不以固定延时或消息加载完成代替选择提交。
                        val observed = withTimeoutOrNull(5_000) {
                            combine(currentChatId, currentChatSelectionReadState) { id, state -> id to state }
                                .first { (id, state) ->
                                    id == chatId || id != origin || state is ChatSelectionReadState.Failed
                                }
                        }
                        if (observed?.first != chatId || observed.second is ChatSelectionReadState.Failed) {
                            return@withLock false
                        }
                    }
                }
                if (!syncToGlobal || selectionMode == ChatSelectionMode.LOCAL_ONLY) {
                    // 保存由服务持有；本地窗口读取则跟随调用者取消，撤销过期 source 的发布资格。
                    if (!stillRequested() || !withContext(requesterJob) { loadSelectedChat(chatId) }) {
                        return@withLock false
                    }
                }
                if (_currentChatId.value == chatId && stillRequested()) {
                    onScrollToBottom()
                    true
                } else false
            }
        }.await()
    }

    /** 创建对话分支 */
    fun createBranch(upToMessageTimestamp: Long? = null) {
        coroutineScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                actualContextWindowSize = windowSize,
                providerUsage = getProviderUsageAggregate(),
            ) // 保存当前聊天

            val currentChatId = _currentChatId.value
            if (currentChatId != null) {
                // 创建分支
                val branchChat = chatHistoryManager.createBranch(currentChatId, upToMessageTimestamp)
                currentChatWindow.selectChat(branchChat.id)
                loadChatMessages(branchChat.id)
                
                // 加载分支的 token 统计（继承自父对话）
                onTokenStatisticsLoaded(
                    branchChat.id,
                    branchChat.inputTokens,
                    branchChat.outputTokens,
                    branchChat.currentWindowSize,
                    branchChat.toProviderUsageAggregate(),
                )
                
                delay(200)
                onScrollToBottom()
            }
        }
    }

    private data class ChatDeletionReplacementTarget(
        val characterCardName: String? = null,
        val characterCardId: String? = null,
        val characterGroupId: String? = null,
        val includeUnboundChats: Boolean = false
    )

    private suspend fun resolveDeletionReplacementTarget(chat: ChatHistory): ChatDeletionReplacementTarget {
        val normalizedGroupId = chat.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        if (!normalizedGroupId.isNullOrBlank()) {
            return ChatDeletionReplacementTarget(characterGroupId = normalizedGroupId)
        }

        val normalizedCardName = chat.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        if (!normalizedCardName.isNullOrBlank()) {
            val matchedCard = runCatching {
                characterCardManager.findCharacterCardByName(normalizedCardName)
            }.getOrNull()
            return ChatDeletionReplacementTarget(
                characterCardName = normalizedCardName,
                characterCardId = matchedCard?.id,
                includeUnboundChats = matchedCard?.isDefault == true
            )
        }

        return when (val activePrompt = runCatching { activePromptManager.getActivePrompt() }.getOrNull()) {
            is ActivePrompt.CharacterGroup -> {
                ChatDeletionReplacementTarget(
                    characterGroupId = activePrompt.id.trim().takeIf { it.isNotBlank() }
                )
            }

            is ActivePrompt.CharacterCard -> {
                val activeCard = runCatching {
                    characterCardManager.getCharacterCard(activePrompt.id)
                }.getOrNull()
                if (activeCard != null) {
                    ChatDeletionReplacementTarget(
                        characterCardName = activeCard.name,
                        characterCardId = activeCard.id,
                        includeUnboundChats = activeCard.isDefault
                    )
                } else {
                    ChatDeletionReplacementTarget()
                }
            }

            null -> ChatDeletionReplacementTarget()
        }
    }

    private fun matchesDeletionReplacementTarget(
        history: ChatHistory,
        target: ChatDeletionReplacementTarget
    ): Boolean {
        val historyGroupId = history.characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        val historyCardName = history.characterCardName?.trim()?.takeIf { it.isNotBlank() }

        if (!target.characterGroupId.isNullOrBlank()) {
            return historyGroupId == target.characterGroupId
        }

        if (!target.characterCardName.isNullOrBlank()) {
            if (!historyGroupId.isNullOrBlank()) {
                return false
            }
            return if (target.includeUnboundChats) {
                historyCardName == null || historyCardName == target.characterCardName
            } else {
                historyCardName == target.characterCardName
            }
        }

        return historyGroupId == null && historyCardName == null
    }

    private fun findLatestDeletionReplacementChat(
        deletingChatId: String,
        target: ChatDeletionReplacementTarget,
        excludedIds: Set<String> = emptySet(),
    ): ChatHistory? {
        return _chatHistories.value
            .asSequence()
            .filter { history -> history.id != deletingChatId }
            .filter { history -> history.id !in excludedIds }
            .filter { history -> matchesDeletionReplacementTarget(history, target) }
            .maxByOrNull { history -> history.updatedAt }
    }

    private suspend fun awaitCurrentChatSelection(chatId: String, timeoutMs: Long = 1200L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (_currentChatId.value != chatId) {
                delay(20)
            }
            true
        } ?: false
    }

    private suspend fun awaitCurrentChatChangeFrom(
        previousChatId: String,
        timeoutMs: Long = 1200L
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (_currentChatId.value == previousChatId || _currentChatId.value == null) {
                delay(20)
            }
            true
        } ?: false
    }

    private suspend fun moveCurrentChatAwayBeforeDeletion(
        currentChat: ChatHistory,
        excludedIds: Set<String> = emptySet(),
        inheritGroup: Boolean = true,
    ): Boolean {
        val target = resolveDeletionReplacementTarget(currentChat)
        val replacementChat = findLatestDeletionReplacementChat(currentChat.id, target, excludedIds)
        if (replacementChat != null) {
            switchChat(
                replacementChat.id,
                syncToGlobal = selectionMode == ChatSelectionMode.FOLLOW_GLOBAL
            )
            return awaitCurrentChatSelection(replacementChat.id)
        }

        createNewChat(
            characterCardName = target.characterCardName,
            characterGroupId = target.characterGroupId,
            inheritGroupFromCurrent = inheritGroup,
            setAsCurrentChat = true,
            characterCardId = target.characterCardId
        )
        return awaitCurrentChatChangeFrom(currentChat.id)
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String, onResult: (Boolean) -> Unit = {}) {
        coroutineScope.launch {
            val deleted = try {
                deleteChatHistoryAwait(chatId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e(TAG, "Chat deletion failed: ${failure.javaClass.simpleName}")
                false
            }
            onResult(deleted)
        }
    }

    /** 持久化由服务持有；UI离开只取消等待，不把已开始的删除中断在清理之前。 */
    suspend fun deleteChatHistoryAwait(chatId: String): Boolean = coroutineScope.async {
        if (!chatHistoryManager.canDeleteChatHistory(chatId)) return@async false
        prepareChatForDestructiveMutation(chatId)
        return@async if (chatId == _currentChatId.value) {
                    val currentChat = _chatHistories.value.firstOrNull { it.id == chatId }
                    if (currentChat == null || !moveCurrentChatAwayBeforeDeletion(currentChat)) {
                        false
                    } else {
                        chatHistoryManager.deleteChatHistory(chatId)
                    }
                } else {
                    chatHistoryManager.deleteChatHistory(chatId)
                }
    }.await()

    /** 删除单条消息 */
    fun deleteMessage(index: Int) {
        coroutineScope.launch {
            runCurrentChatDestructiveHistoryMutation("删除消息时当前会话已变化，放弃操作") { chatId ->
                val currentMessages = _chatHistory.value.toMutableList()
                if (index < 0 || index >= currentMessages.size) {
                    return@runCurrentChatDestructiveHistoryMutation false
                }

                val messageToDelete = currentMessages[index]
                chatHistoryManager.deleteMessage(chatId, messageToDelete.timestamp)
                reloadCurrentChatDisplayHistory(chatId)
                true
            }
        }
    }

    fun deleteMessageByTimestamp(chatId: String, timestamp: Long) {
        coroutineScope.launch {
            runDestructiveHistoryMutation(chatId) {
                chatHistoryManager.deleteMessage(chatId, timestamp)

                if (_currentChatId.value == chatId) {
                    reloadCurrentChatDisplayHistory(chatId)
                }
                true
            }
        }
    }

    suspend fun deleteMessagesByTimestamps(chatId: String, timestamps: List<Long>) {
        if (timestamps.isEmpty()) {
            return
        }

        runDestructiveHistoryMutation(chatId) {
            timestamps.distinct().forEach { timestamp ->
                chatHistoryManager.deleteMessage(chatId, timestamp)
            }

            if (_currentChatId.value == chatId) {
                reloadCurrentChatDisplayHistory(chatId)
            }
            true
        }
    }

    fun setMessageFavorite(timestamp: Long, isFavorite: Boolean) {
        coroutineScope.launch {
            val chatId = _currentChatId.value ?: return@launch
            val shouldReloadCurrentChat =
                historyUpdateMutex.withLock {
                    chatHistoryManager.setMessageFavorite(chatId, timestamp, isFavorite)
                    chatId == _currentChatId.value
                }
            if (shouldReloadCurrentChat && chatId == _currentChatId.value) {
                reloadCurrentChatDisplayHistory(chatId)
            }
        }
    }

    suspend fun deleteMessageVariant(timestamp: Long, variantIndex: Int) {
        val chatId = _currentChatId.value ?: throw IllegalStateException("No active chat")
        val shouldReloadCurrentChat =
            historyUpdateMutex.withLock {
                chatHistoryManager.deleteMessageVariant(chatId, timestamp, variantIndex)
                chatId == _currentChatId.value
            }
        if (shouldReloadCurrentChat && chatId == _currentChatId.value) {
            reloadCurrentChatDisplayHistory(chatId)
        }
    }

    /** 从指定索引删除后续所有消息 */
    suspend fun deleteMessagesFrom(index: Int) {
        runCurrentChatDestructiveHistoryMutation("批量删除后续消息时当前会话已变化，放弃操作") { chatId ->
                val currentMessages = _chatHistory.value
                if (index < 0 || index >= currentMessages.size) {
                    return@runCurrentChatDestructiveHistoryMutation false
                }

                val messageToStartDeletingFrom = currentMessages[index]
                chatHistoryManager.deleteMessagesFrom(chatId, messageToStartDeletingFrom.timestamp)
                reloadCurrentChatDisplayHistory(chatId)
                true
            }
    }

    suspend fun selectMessageVariant(timestamp: Long, selectedVariantIndex: Int) {
        val chatId = _currentChatId.value ?: throw IllegalStateException("No active chat")
        val shouldReloadCurrentChat = historyUpdateMutex.withLock {
            chatHistoryManager.selectMessageVariant(chatId, timestamp, selectedVariantIndex)
            chatId == _currentChatId.value
        }
        if (shouldReloadCurrentChat && chatId == _currentChatId.value) {
            reloadCurrentChatDisplayHistory(chatId)
        }
    }

    suspend fun reviseMessage(
        message: ChatMessage,
        chatId: String = _currentChatId.value ?: throw IllegalStateException("No active chat"),
        expectedContent: String? = null,
    ) {
        val shouldReloadCurrentChat =
            historyUpdateMutex.withLock {
                chatHistoryManager.reviseMessage(chatId, message, expectedContent)
                chatId == _currentChatId.value
            }
        if (shouldReloadCurrentChat && chatId == _currentChatId.value) {
            reloadCurrentChatDisplayHistory(chatId)
        }
    }

    suspend fun addMessageVariant(
        timestamp: Long,
        message: ChatMessage,
        chatIdOverride: String? = null,
    ): Int {
        val chatId = chatIdOverride ?: _currentChatId.value ?: throw IllegalStateException("No active chat")
        val isCurrentChat = chatId == _currentChatId.value
        val selectedVariantIndex = historyUpdateMutex.withLock {
            val persistedMessage =
                chatHistoryManager.addMessageVariant(chatId, timestamp, message)
            ToolPkgChatMessageHookBridge.dispatchMessagePersisted(chatId, persistedMessage)
            persistedMessage.selectedVariantIndex
        }
        if (isCurrentChat && chatId == _currentChatId.value) {
            reloadCurrentChatDisplayHistory(chatId)
        }
        return selectedVariantIndex
    }

    /** 清空当前聊天 */
    fun clearCurrentChat(onResult: (Boolean) -> Unit = {}) {
        coroutineScope.launch {
            val chatId = _currentChatId.value
            if (chatId == null) {
                createNewChat()
                onResult(false)
                return@launch
            }

            if (!chatHistoryManager.canDeleteChatHistory(chatId)) {
                onResult(false)
                return@launch
            }
            prepareChatForDestructiveMutation(chatId)
            val currentChat = _chatHistories.value.firstOrNull { it.id == chatId }
            val deleted =
                if (currentChat == null || !moveCurrentChatAwayBeforeDeletion(currentChat)) {
                    false
                } else {
                    chatHistoryManager.deleteChatHistory(chatId)
                }
            onResult(deleted)
        }
    }

    /** 保存当前聊天到持久存储 */
    suspend fun saveCurrentChat(
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        actualContextWindowSize: Long = 0L,
        providerUsage: ProviderUsageAggregate,
        chatIdOverride: String? = null
    ) {
        val chatId = chatIdOverride ?: _currentChatId.value
        chatId?.let {
            if (
                    _chatHistory.value.isNotEmpty() ||
                    inputTokens != 0L ||
                    outputTokens != 0L ||
                    actualContextWindowSize != 0L ||
                    providerUsage.requestCount != 0
            ) {
                chatHistoryManager.updateChatTokenCounts(
                    it,
                    inputTokens.toPersistedTokenCount(),
                    outputTokens.toPersistedTokenCount(),
                    actualContextWindowSize.toPersistedTokenCount(),
                    providerUsage,
                )
            }
        }
    }

    /** 绑定聊天到工作区 */
    suspend fun bindChatToWorkspace(chatId: String, workspace: String, workspaceEnv: String?) = coroutineScope.async {
        // 持久化与其内存投影属于服务 owner；调用页面离开只取消等待，不能截断提交后的投影。
        // 1. Update the database
        chatHistoryManager.updateChatWorkspace(chatId, workspace, workspaceEnv)

        // 2. Manually update the UI state to reflect the change immediately
        val updatedHistories = _chatHistories.value.map {
            if (it.id == chatId) {
                it.copy(workspace = workspace, workspaceEnv = workspaceEnv, updatedAt = LocalDateTime.now())
            } else {
                it
            }
        }
        _chatHistories.value = updatedHistories
    }.await()

    /** 更新聊天绑定的角色卡 */
    fun updateChatCharacterCard(chatId: String, characterCardName: String?) {
        updateChatCharacterBinding(chatId, characterCardName, null)
    }

    /** 更新聊天绑定的群组角色卡 */
    fun updateChatCharacterGroup(chatId: String, characterGroupId: String?) {
        updateChatCharacterBinding(chatId, null, characterGroupId)
    }

    /** 同时更新聊天绑定的角色卡与群组 */
    fun updateChatCharacterBinding(
        chatId: String,
        characterCardName: String?,
        characterGroupId: String?
    ) {
        coroutineScope.launch {
            chatHistoryManager.updateChatCharacterBinding(chatId, characterCardName, characterGroupId)

            val updatedHistories = _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(
                        characterCardName = characterCardName,
                        characterGroupId = characterGroupId,
                        updatedAt = LocalDateTime.now()
                    )
                } else {
                    it
                }
            }
            _chatHistories.value = updatedHistories
        }
    }

    /** 解绑聊天的工作区 */
    suspend fun unbindChatFromWorkspace(chatId: String, expectedWorkspace: String, expectedEnvironment: String?) = coroutineScope.async {
        // 1. Update the database (set workspace to null)
        chatHistoryManager.updateChatWorkspace(chatId, null, null, expectedWorkspace, expectedEnvironment)

        // 2. Manually update the UI state to reflect the change immediately
        val updatedHistories = _chatHistories.value.map {
            if (it.id == chatId) {
                it.copy(workspace = null, workspaceEnv = null, updatedAt = LocalDateTime.now())
            } else {
                it
            }
        }
        _chatHistories.value = updatedHistories
    }.await()

    /** 服务持有提交；投影仅由已有 Room Flow 更新，避免迟到返回覆盖较新的数据库观察值。 */
    suspend fun editChatMetadata(
        chatId: String,
        original: com.ai.assistance.operit.data.repository.ChatMetadataSnapshot,
        edited: com.ai.assistance.operit.data.repository.ChatMetadataSnapshot,
    ) = coroutineScope.async {
        chatHistoryManager.editChatMetadata(chatId, original, edited)
        Unit
    }.await()

    /** 更新聊天标题 */
    fun updateChatTitle(chatId: String, title: String) {
        coroutineScope.launch {
            // 更新数据库
            chatHistoryManager.updateChatTitle(chatId, title)

            // 更新UI状态
            val updatedHistories =
                    _chatHistories.value.map {
                        if (it.id == chatId) {
                            it.copy(title = title, updatedAt = LocalDateTime.now())
                        } else {
                            it
                        }
                    }
            _chatHistories.value = updatedHistories
        }
    }

    suspend fun renameWorkspaceAndChat(
        chatId: String,
        newWorkspaceName: String
    ): WorkspaceRenameResult {
        val result = chatHistoryManager.renameManagedWorkspace(chatId, newWorkspaceName)
        _chatHistories.value =
            _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(
                        title = result.workspaceName,
                        workspace = result.workspacePath,
                        workspaceEnv = result.workspaceEnv,
                        updatedAt = LocalDateTime.now()
                    )
                } else {
                    it
                }
            }
        return result
    }

    /**
     * 向聊天历史添加或更新消息。
     *
     * @param message 待添加或更新的消息
     * @param chatIdOverride 可选：指定聊天会话ID（不使用`currentChatId`）
     *
     * 行为逻辑：
     *   - 已存在同时间戳消息：更新内存与数据库（保持UI与持久层一致）。
     *   - 不存在：追加到内存，并持久化。
     */
    private fun upsertCurrentChatMessageInMemory(chatId: String, message: ChatMessage): Boolean = currentChatWindow.withCurrentChat(chatId) {
        val currentMessages = _chatHistory.value
        val existingIndex = currentMessages.indexOfFirst { it.timestamp == message.timestamp }

        if (existingIndex >= 0) {
            if (message.contentStream == null || currentMessages[existingIndex].contentStream == null) {
                AppLogger.d(TAG, "更新当前会话内存消息, ts: ${message.timestamp}")
                setCurrentChatMessagesInMemory(
                    chatId = chatId,
                    messages = currentMessages.mapIndexed { index, existingMessage ->
                        if (index == existingIndex) {
                            message
                        } else {
                            existingMessage
                        }
                    },
                )
            } else {
                // stream对象原地更新仍比在途SQL快照新，也必须撤销旧读取资格。
                currentChatWindow.applyMessages(chatId, currentMessages)
            }
            return@withCurrentChat true
        }

        if (currentChatWindow.hasPersistedNewerHistoryNow()) {
            AppLogger.d(TAG, "当前显示窗口不是最新窗口，跳过内存追加消息, ts: ${message.timestamp}")
            currentChatWindow.applyMessages(chatId, currentMessages)
            return@withCurrentChat false
        }

        val currentPageCount = countDisplayPages(currentMessages).coerceIn(1, MAX_DISPLAY_PAGE_COUNT)
        val updatedMessages = currentMessages + message
        val windowMessages = takeNewestDisplayPages(updatedMessages, currentPageCount)
        AppLogger.d(TAG, "向当前会话内存追加消息, ts: ${message.timestamp}")
        setCurrentChatMessagesInMemory(
            chatId = chatId,
            messages = windowMessages,
            hasOlderPersistedHistory = currentChatWindow.hasPersistedOlderHistoryNow(),
            hasNewerPersistedHistory = false,
        )
        return@withCurrentChat false
    } ?: false

    suspend fun addMessageToChat(message: ChatMessage, chatIdOverride: String? = null) {
        historyUpdateMutex.withLock {
            val targetChatId = chatIdOverride ?: _currentChatId.value ?: return@withLock

            val isCurrentChat = (targetChatId == _currentChatId.value)

            if (message.isVariantPreview) {
                if (isCurrentChat) {
                    upsertCurrentChatMessageInMemory(targetChatId, message)
                }
                return@withLock
            }

            if (!isCurrentChat) {
                    // 非当前会话：使用“更新或插入”语义，避免每个chunk都插入新消息
                chatHistoryManager.updateMessage(targetChatId, message)
                ToolPkgChatMessageHookBridge.dispatchMessagePersisted(targetChatId, message)
                return@withLock
            }

            val didUpdateVisibleMessage = upsertCurrentChatMessageInMemory(targetChatId, message)
            val isVisibleNewMessage =
                !currentChatWindow.hasPersistedNewerHistoryNow() &&
                    _chatHistory.value.any { it.timestamp == message.timestamp }

            if (didUpdateVisibleMessage) {
                chatHistoryManager.updateMessage(targetChatId, message)
                ToolPkgChatMessageHookBridge.dispatchMessagePersisted(targetChatId, message)
            } else {
                AppLogger.d(
                    TAG,
                    "添加新消息到聊天 $targetChatId, isCurrent=$isCurrentChat, stream is null: ${message.contentStream == null}, ts: ${message.timestamp}"
                )
                if (isVisibleNewMessage) {
                    chatHistoryManager.addMessage(targetChatId, message)
                    ToolPkgChatMessageHookBridge.dispatchMessagePersisted(targetChatId, message)
                    refreshCurrentChatDisplayFlags(targetChatId)
                } else {
                    chatHistoryManager.updateMessage(targetChatId, message)
                    ToolPkgChatMessageHookBridge.dispatchMessagePersisted(targetChatId, message)
                }
            }
        }
    }

    /**
     * 异步向聊天历史添加或更新消息（供不需要等待完成的场景使用）
     */
    fun addMessageToChatAsync(message: ChatMessage, chatIdOverride: String? = null) {
        coroutineScope.launch {
            addMessageToChat(message, chatIdOverride)
        }
    }

    /**
     * 截断聊天记录，会同步删除数据库中指定时间戳之后的消息，并从 SQL 重新加载当前显示窗口。
     *
     * @param timestampOfFirstDeletedMessage 用于删除数据库记录的起始时间戳。如果为null，则清空所有消息。
     */
    suspend fun truncateChatHistory(
        timestampOfFirstDeletedMessage: Long?,
        expectedChatId: String? = _currentChatId.value,
        afterTruncate: suspend () -> Unit = {},
        beforeTruncate: suspend () -> Unit = {},
    ): Boolean {
        var cleanupFailure: Exception? = null
        val truncated = runCurrentChatDestructiveHistoryMutation(
            "截断聊天历史时当前会话已变化，放弃操作",
            expectedChatId = expectedChatId,
        ) { chatIdSnapshot ->
            // 先停止该对话的执行，再完成关联工作区恢复；恢复失败绝不能继续删除历史。
            beforeTruncate()
            if (timestampOfFirstDeletedMessage != null) {
                // 从数据库中删除指定时间戳之后的消息
                chatHistoryManager.deleteMessagesFrom(
                        chatIdSnapshot,
                        timestampOfFirstDeletedMessage
                )
            } else {
                // 如果时间戳为空，则清除该聊天的所有消息
                chatHistoryManager.clearChatMessages(chatIdSnapshot)
            }

            try {
                afterTruncate()
            } catch (error: Exception) {
                // SQL 已提交，即使备份清理失败也必须刷新投影并完成执行状态清理，再报告错误。
                cleanupFailure = error
            }

            if (_currentChatId.value != chatIdSnapshot) {
                // 工作区 I/O 期间可以切换页面，已固定目标的操作仍只完成旧对话。
                return@runCurrentChatDestructiveHistoryMutation true
            } else if (timestampOfFirstDeletedMessage == null) {
                currentChatWindow.withCurrentChat(chatIdSnapshot) { clearCurrentChatHistoryInMemory() }
            } else {
                reloadCurrentChatDisplayHistory(chatIdSnapshot)
            }
            true
        }
        cleanupFailure?.let { throw it }
        return truncated
    }

    /** 服务持有提交，列表只由Room Flow投影；UI取消等待不拆开排序与绑定事务。 */
    suspend fun moveChat(move: com.ai.assistance.operit.data.repository.ChatOrderMove) = coroutineScope.async {
        chatHistoryManager.moveChat(move)
    }.await()

    /** 重命名分组 */
    suspend fun renameChatGroup(target: ChatGroupTarget, newName: String) = coroutineScope.async {
        chatHistoryManager.renameChatGroup(target, newName)
    }.await()

    suspend fun deleteChatGroup(target: ChatGroupTarget, deleteChats: Boolean): Set<String> = coroutineScope.async {
        val members = chatHistoryManager.getChatGroupMembers(target)
        if (deleteChats) {
            val deletingIds = members.filterNot { it.locked }.map { it.id }.toSet()
            deletingIds.forEach { prepareChatForDestructiveMutation(it) }
            val selectedId = _currentChatId.value
            if (selectedId in deletingIds) {
                val current = checkNotNull(_chatHistories.value.firstOrNull { it.id == selectedId }) {
                    "Current chat metadata unavailable before group deletion"
                }
                check(moveCurrentChatAwayBeforeDeletion(current, deletingIds, inheritGroup = false)) {
                    "Current chat selection did not move before group deletion"
                }
            }
        }
        chatHistoryManager.deleteChatGroup(target, members, deleteChats)
    }.await()

    /** 创建新分组（通过创建新聊天实现） */
    suspend fun createGroupAwait(groupName: String, characterCardName: String?, characterGroupId: String?): ChatGroupCreationResult {
        val originChatId = _currentChatId.value
        val (inputTokens, outputTokens, windowSize) = getChatStatistics()
        val usage = getProviderUsageAggregate()
        return coroutineScope.async {
            require(groupName.isNotBlank())
            require(characterCardName == null || characterGroupId == null)
            if (originChatId != null) {
                saveCurrentChat(inputTokens, outputTokens, windowSize, usage, chatIdOverride = originChatId)
            }
            completeChatCreation(
                create = {
                    chatHistoryManager.createNewChat(
                        group = groupName.trim(),
                        characterCardName = characterCardName,
                        characterGroupId = characterGroupId,
                        setAsCurrentChat = false,
                    )
                },
                select = { created ->
                    // 持久化期间用户可以从其他入口切chat；迟到创建不能夺回当前选择。
                    if (_currentChatId.value != originChatId) false
                    else if (selectionMode == ChatSelectionMode.FOLLOW_GLOBAL) {
                        chatHistoryManager.compareAndSetCurrentChatId(originChatId, created.id)
                    } else {
                        currentChatWindow.selectChat(created.id)
                        loadChatMessages(created.id)
                        true
                    }
                },
            )
        }.await()
    }

    // This function is moved to AIMessageManager
    /*
    fun shouldGenerateSummary(
        messages: List<ChatMessage>,
        currentTokens: Int,
        maxTokens: Int
    ): Boolean { ... }
    */

    // This function is moved to AIMessageManager
    /*
    suspend fun summarizeMemory(messages: List<ChatMessage>) { ... }
    */
    
    /** 切换是否显示聊天历史选择器 */
    fun toggleChatHistorySelector() {
        _showChatHistorySelector.value = !_showChatHistorySelector.value
    }

    /** 显示或隐藏聊天历史选择器 */
    fun showChatHistorySelector(show: Boolean) {
        _showChatHistorySelector.value = show
    }

    // This function is moved to AIMessageManager and renamed to getMemoryFromMessages
    /*
    fun getMemory(includePlanInfo: Boolean = true): List<Pair<String, String>> { ... }
    */

    /** 获取EnhancedAIService实例 */
    private fun getEnhancedAiService(): EnhancedAIService? {
        // 使用构造函数中传入的callback获取EnhancedAIService实例
        return getEnhancedAiService.invoke()
    }

    /** 通过回调获取当前token统计数据 */
    private fun getCurrentTokenCounts(): Pair<Long, Long> {
        // 使用构造函数中传入的回调获取当前token统计数据
        val stats = getChatStatistics()
        return Pair(stats.first, stats.second)
    }
}
