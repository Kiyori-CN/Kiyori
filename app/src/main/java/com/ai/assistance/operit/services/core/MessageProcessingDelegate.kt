package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.AssistantResponseCompletionPolicy
import com.ai.assistance.operit.api.chat.AssistantTurnDiagnostics
import com.ai.assistance.operit.api.chat.AssistantTurnFailureKind
import com.ai.assistance.operit.api.chat.AssistantTurnFailurePolicy
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ProviderRequestContext
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryProjector
import com.ai.assistance.operit.core.chat.AssistantTurnCancellationPolicy
import com.ai.assistance.operit.core.chat.AssistantTurnCancellationSource
import com.ai.assistance.operit.core.chat.AssistantTurnCancellationTerminal
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.agent.PhoneAgentJobRegistry
import com.ai.assistance.operit.data.audit.ConversationAuditEventRequest
import com.ai.assistance.operit.data.audit.ConversationAuditPayloadInput
import com.ai.assistance.operit.data.audit.ConversationAuditRepository
import com.ai.assistance.operit.data.audit.resolveConversationAuditTextChange
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.model.InputProcessingState as EnhancedInputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.SecondaryStreamObservation
import com.ai.assistance.operit.util.stream.claimPrimaryMessageFailureOwner
import com.ai.assistance.operit.util.stream.collectForMessageFailureOwner
import com.ai.assistance.operit.util.stream.extractMessageFailureExecutionId
import com.ai.assistance.operit.util.stream.observeSecondaryStream
import com.ai.assistance.operit.util.stream.snapshotMessageFailure
import com.ai.assistance.operit.util.TtsSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getFullChatHistory: suspend (String) -> List<ChatMessage>,
        private val getRuntimeChatHistory: suspend (String) -> List<ChatMessage>,
        private val hasUserMessage: suspend (String) -> Boolean,
        private val addMessageToChat: suspend (String, ChatMessage) -> Unit,
        private val saveCurrentChat: suspend () -> Unit,
        private val showErrorMessage: (String) -> Unit,
        private val updateChatTitle: (chatId: String, title: String) -> Unit,
        private val getChatTitle: (chatId: String) -> String?,
        private val onTurnComplete:
            suspend (chatId: String?, service: EnhancedAIService, nextWindowSize: Int?, turnOptions: ChatTurnOptions) -> Unit,
        private val onTokenLimitExceeded: suspend (
            chatId: String?,
            roleCardId: String?,
            isGroupOrchestrationTurn: Boolean,
            groupParticipantNamesText: String?
        ) -> Unit,
        // 添加自动朗读相关的回调
        private val getIsAutoReadEnabled: () -> Boolean,
        private var speakMessageHandler: (String, Boolean) -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
        private const val STREAM_SCROLL_THROTTLE_MS = 200L
        private const val STREAM_PERSIST_INTERVAL_MS = 1000L

        internal fun completeInterruptedMessage(
            streamingMessage: ChatMessage,
            finalContent: String,
            snapshot: TurnCancellationSnapshot?,
            completedAt: Long,
        ): ChatMessage {
            val replaySafeContent =
                closeInterruptedThinkingMarkup(
                    AssistantReplayHistoryProjector.project(finalContent).content
                )
            val messageWithMetrics =
                snapshot?.let { stats ->
                    streamingMessage.copy(
                        inputTokens = stats.inputTokens,
                        outputTokens = stats.outputTokens,
                        cachedInputTokens = stats.cachedInputTokens,
                        sentAt = stats.sentAt.takeIf { it > 0L } ?: streamingMessage.sentAt,
                        outputDurationMs = stats.outputDurationMs,
                        waitDurationMs = stats.waitDurationMs,
                    )
                } ?: streamingMessage
            return messageWithMetrics.copy(
                content = replaySafeContent,
                contentStream = null,
                completedAt = completedAt,
            )
        }

        /**
         * 将失败回合的已接收正文转换为可重放的部分消息。
         *
         * 空正文不产生消息，避免把只有传输错误的回合投影成一条看似成功的空回答。
         */
        internal fun projectFailedAssistantMessage(
            streamingMessage: ChatMessage,
            finalContent: String,
            snapshot: TurnCancellationSnapshot?,
            completedAt: Long,
        ): ChatMessage? {
            val replaySafeContent =
                closeInterruptedThinkingMarkup(
                    AssistantReplayHistoryProjector.project(finalContent).content
                )
            val (visibleContent, thinkingContent) =
                ChatUtils.extractThinkingContent(replaySafeContent)
            if (visibleContent.isBlank() && thinkingContent.isBlank()) {
                return null
            }
            return completeInterruptedMessage(
                streamingMessage = streamingMessage,
                finalContent = replaySafeContent,
                snapshot = snapshot,
                completedAt = completedAt,
            )
        }

        /** Provider 已经发出 `<think>` 时，只补齐本地展示标签；这不代表远端执行完成。 */
        private fun closeInterruptedThinkingMarkup(content: String): String {
            val lastOpen = content.lastIndexOf("<think>")
            val lastClose = content.lastIndexOf("</think>")
            return if (lastOpen > lastClose) "$content</think>" else content
        }

        internal data class TurnFailureTerminal(
            val terminalOutcome: String,
            val finalInputState: EnhancedInputProcessingState.Error,
            val shouldNotifyTurnComplete: Boolean,
        )

        internal fun createTurnFailureTerminal(
            message: String,
            failureKind: AssistantTurnFailureKind,
        ): TurnFailureTerminal {
            return TurnFailureTerminal(
                terminalOutcome = failureKind.terminalOutcome,
                finalInputState = EnhancedInputProcessingState.Error(message),
                shouldNotifyTurnComplete = false,
            )
        }

        internal fun applyWaifuTurnMetrics(
            messages: List<ChatMessage>,
            sourceMessage: ChatMessage,
        ): List<ChatMessage> {
            val sourceProviderUsage = sourceMessage.toProviderUsageAggregate()
            return messages.mapIndexed { index, message ->
                val messageWithMetrics =
                    message.copy(
                        inputTokens = sourceMessage.inputTokens,
                        outputTokens = sourceMessage.outputTokens,
                        cachedInputTokens = sourceMessage.cachedInputTokens,
                        sentAt = sourceMessage.sentAt,
                        outputDurationMs = sourceMessage.outputDurationMs,
                        waitDurationMs = sourceMessage.waitDurationMs,
                        completedAt = sourceMessage.completedAt,
                    )
                // Waifu 模式不持久化普通 aiMessage。整轮 provider usage 必须由最后一个
                // 实际分段唯一持有，否则按消息汇总时会重复计费或完全丢失该回合。
                messageWithMetrics.withProviderUsageAggregate(
                    if (index == messages.lastIndex) {
                        sourceProviderUsage
                    } else {
                        ProviderUsageAggregate()
                    }
                )
            }
        }
    }



    private fun fallbackConversationTitle(userText: String, attachments: List<AttachmentInfo>): String {
        return attachments.firstOrNull()?.fileName?.trim()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.new_conversation)
    }

    private fun launchConversationTitleGeneration(
        chatId: String,
        userText: String,
        attachments: List<AttachmentInfo>,
        fallbackTitle: String
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val generatedTitle = EnhancedAIService.getChatInstance(context, chatId)
                    .generateConversationTitle(
                        userText = userText,
                        attachmentFileNames = attachments.map { it.fileName }
                    )
                    .trim()
                if (generatedTitle.isNotBlank() && getChatTitle(chatId) == fallbackTitle) {
                    updateChatTitle(chatId, generatedTitle)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成对话标题失败", e)
            }
        }
    }

    // 角色卡管理器
    private val characterCardManager = CharacterCardManager.getInstance(context)
    
    // 模型配置管理器
    private val modelConfigManager = ModelConfigManager(context)
    
    // 功能配置管理器，用于获取正确的模型配置ID
    private val functionalConfigManager = FunctionalConfigManager(context)
    private val conversationAuditRepository = ConversationAuditRepository.from(context)
    private val conversationAuditGson: Gson =
        GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    private val _userMessage = MutableStateFlow(TextFieldValue(""))
    val userMessage: StateFlow<TextFieldValue> = _userMessage.asStateFlow()
    private val userMessageDraftsByChatId = ConcurrentHashMap<String, TextFieldValue>()
    @Volatile
    private var activeDraftChatId: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeStreamingChatIds = MutableStateFlow<Set<String>>(emptySet())
    val activeStreamingChatIds: StateFlow<Set<String>> = _activeStreamingChatIds.asStateFlow()

    private val _inputProcessingStateByChatId =
        MutableStateFlow<Map<String, EnhancedInputProcessingState>>(emptyMap())
    val inputProcessingStateByChatId: StateFlow<Map<String, EnhancedInputProcessingState>> =
        _inputProcessingStateByChatId.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    private val _nonFatalErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nonFatalErrorEvent = _nonFatalErrorEvent.asSharedFlow()

    /** 通过现有聊天通知流展示 ToolPkg Hook 非致命超时，不创建第二套提示通道。 */
    fun reportNonFatalError(message: String) {
        if (message.isBlank()) {
            return
        }
        coroutineScope.launch {
            _nonFatalErrorEvent.emit(message)
        }
    }

    private val _turnCompleteCounterByChatId = MutableStateFlow<Map<String, Long>>(emptyMap())
    val turnCompleteCounterByChatId: StateFlow<Map<String, Long>> =
        _turnCompleteCounterByChatId.asStateFlow()
    private val _currentTurnToolInvocationCountByChatId =
        MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentTurnToolInvocationCountByChatId: StateFlow<Map<String, Int>> =
        _currentTurnToolInvocationCountByChatId.asStateFlow()

    private data class ActiveStreamingTurn(
        val message: ChatMessage,
        val segmentedMessages: MutableList<ChatMessage>? = null,
    )

    // 当前活跃的AI响应流
    private data class ChatRuntime(
        var sendJob: Job? = null,
        var responseStream: SharedStream<String>? = null,
        // 取消收尾必须持有运行态对象；Room 不保存 contentStream，重载后无法识别当前流消息。
        var activeStreamingTurn: ActiveStreamingTurn? = null,
        var streamCollectionJob: Job? = null,
        var stateCollectionJob: Job? = null,
        var currentTurnOptions: ChatTurnOptions = ChatTurnOptions(),
        var requestSentAt: Long = 0L,
        var requestStartElapsed: Long = 0L,
        var firstResponseElapsed: Long? = null,
        val turnSequence: AtomicLong = AtomicLong(0L),
        @Volatile var activeTurnId: Long = 0L,
        val cancellationMutex: Mutex = Mutex(),
        @Volatile var cancellationInProgress: Boolean = false,
        val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )

    /**
     * 普通发送的跨阶段状态载体。
     *
     * 发送过程包含模型配置读取、消息持久化、Responses 提交、流式观察和收尾等多个挂起点。
     * 如果把这些值全部作为一个 suspend 方法的局部变量，Kotlin 会把它们展开到同一个 DEX
     * continuation，vivo Android 16 的 ART 解释器可能在恢复这个巨大状态机时触发 unused-e6。
     * 将状态集中到对象字段后，每个阶段的 continuation 只需要保存对象引用，不再复制整轮局部变量。
     */
    private class SendUserMessageTurnState(
        val attachments: List<AttachmentInfo>,
        val chatId: String,
        val originalMessageText: String,
        val proxySenderNameOverride: String?,
        val workspacePath: String?,
        val workspaceEnv: String?,
        val promptFunctionType: PromptFunctionType,
        val roleCardId: String,
        val enableThinking: Boolean,
        val enableMemoryAutoUpdate: Boolean,
        val maxTokens: Int,
        val tokenUsageThreshold: Double,
        val replyToMessage: ChatMessage?,
        val isAutoContinuation: Boolean,
        val enableSummary: Boolean,
        val chatModelConfigIdOverride: String?,
        val chatModelIndexOverride: Int?,
        val memorySpaceIdOverride: String?,
        val suppressUserMessageInHistory: Boolean,
        val isGroupOrchestrationTurn: Boolean,
        val groupParticipantNamesText: String?,
        val turnOptions: ChatTurnOptions,
        val chatRuntime: ChatRuntime,
        val turnId: Long,
    ) {
        val activeChatId: String = chatId
        val effectivePersistTurn: Boolean = turnOptions.persistTurn
        val effectiveHideUserMessage: Boolean =
            effectivePersistTurn && turnOptions.hideUserMessage

        var sendUserMessageStartTime: Long = 0L
        var finalMessageContent: String = ""
        var userMessageAdded: Boolean = false
        lateinit var userMessage: ChatMessage
        var toolHandler: AIToolHandler? = null
        var workspaceToolHookSession: WorkspaceBackupManager.WorkspaceToolHookSession? = null

        lateinit var service: EnhancedAIService
        var serviceForTurnComplete: EnhancedAIService? = null
        var characterName: String? = null
        var avatarUri: String? = null
        var currentRoleName: String = ""
        lateinit var chatHistory: List<ChatMessage>
        var requestMessageContent: String = ""
        var provider: String = ""
        var modelName: String = ""
        var effectiveMaxTokens: Int = 0
        var effectiveTokenUsageThreshold: Double = Double.MAX_VALUE
        var effectiveOnTokenLimitExceeded: (suspend () -> Unit)? = null

        var isWaifuModeEnabled: Boolean = false
        var waifuCharDelay: Int = 0
        var waifuRemovePunctuation: Boolean = false
        lateinit var sharedCharStream: SharedStream<String>
        lateinit var aiMessage: ChatMessage
        val waifuEmittedMessages: MutableList<ChatMessage> = mutableListOf()
        var didStreamAutoRead: Boolean = false

        var requestSentAt: Long = 0L
        var requestStartElapsed: Long = 0L
        var responseStartTime: Long = 0L
        var firstResponseElapsed: Long? = null
        var turnInputTokens: Int = 0
        var turnOutputTokens: Int = 0
        var turnCachedInputTokens: Int = 0
        var turnProviderUsage: ProviderUsageAggregate = ProviderUsageAggregate()
        var calculateNextWindowSize: (suspend () -> Int?)? = null

        var shouldNotifyTurnComplete: Boolean = false
        var finalInputStateAfterSend: EnhancedInputProcessingState? = null
        var cancellationToPropagate: kotlinx.coroutines.CancellationException? = null
        var receivedChunkCount: Int = 0
        var lastAuditedContent: String = ""
        var terminalOutcome: String = "unknown"
        var terminalCancellationSource: AssistantTurnCancellationSource? = null
        var providerRequestContextPresent: Boolean = false
        var providerRequestContext: ProviderRequestContext? = null
        var terminalFailure: Throwable? = null

        fun hasAiMessage(): Boolean = this::aiMessage.isInitialized

        fun visibleContentLength(): Int {
            return if (this::aiMessage.isInitialized) aiMessage.content.length else 0
        }
    }

    private val chatRuntimes = ConcurrentHashMap<String, ChatRuntime>()
    private val lastScrollEmitMsByChatKey = ConcurrentHashMap<String, AtomicLong>()
    private val suppressIdleCompletedStateByChatId = ConcurrentHashMap<String, Boolean>()
    private val pendingAsyncSummaryUiByChatId = ConcurrentHashMap<String, Boolean>()

    private fun chatKey(chatId: String?): String = chatId ?: "__DEFAULT_CHAT__"

    private fun tryEmitScrollToBottomThrottled(chatId: String?) {
        val key = chatKey(chatId)
        val now = System.currentTimeMillis()
        val last = lastScrollEmitMsByChatKey.getOrPut(key) { AtomicLong(0L) }
        val prev = last.get()
        if (now - prev >= STREAM_SCROLL_THROTTLE_MS && last.compareAndSet(prev, now)) {
            _scrollToBottomEvent.tryEmit(Unit)
        }
    }

    private fun forceEmitScrollToBottom(chatId: String?) {
        val key = chatKey(chatId)
        lastScrollEmitMsByChatKey.getOrPut(key) { AtomicLong(0L) }.set(System.currentTimeMillis())
        _scrollToBottomEvent.tryEmit(Unit)
    }

    private fun runtimeFor(chatId: String?): ChatRuntime {
        val key = chatKey(chatId)
        return chatRuntimes[key] ?: ChatRuntime().also { chatRuntimes[key] = it }
    }

    private fun updateGlobalLoadingState() {
        val anyLoading = chatRuntimes.values.any { it.isLoading.value }
        val activeChatIds = chatRuntimes
            .filter { (_, runtime) -> runtime.isLoading.value }
            .keys
            .filter { it != "__DEFAULT_CHAT__" }
            .toSet()

        _activeStreamingChatIds.value = activeChatIds
        _isLoading.value = anyLoading
    }

    private fun isTerminalInputState(state: EnhancedInputProcessingState): Boolean {
        return state is EnhancedInputProcessingState.Idle ||
            state is EnhancedInputProcessingState.Completed
    }

    private fun setChatInputProcessingState(chatId: String?, state: EnhancedInputProcessingState) {
        if (chatId != null &&
            runtimeFor(chatId).isLoading.value &&
            isTerminalInputState(state)
        ) {
            return
        }
        if (chatId != null && suppressIdleCompletedStateByChatId.containsKey(chatId)) {
            if (isTerminalInputState(state)) {
                return
            }
        }
        if (state !is EnhancedInputProcessingState.ExecutingTool &&
            state !is EnhancedInputProcessingState.Summarizing
        ) {
            ToolProgressBus.clear()
        }
        val key = chatKey(chatId)
        val map = _inputProcessingStateByChatId.value.toMutableMap()
        map[key] = state
        _inputProcessingStateByChatId.value = map
    }

    fun setSuppressIdleCompletedStateForChat(chatId: String, suppress: Boolean) {
        if (suppress) {
            suppressIdleCompletedStateByChatId[chatId] = true
        } else {
            suppressIdleCompletedStateByChatId.remove(chatId)
        }
    }

    fun setPendingAsyncSummaryUiForChat(chatId: String, pending: Boolean) {
        if (pending) {
            pendingAsyncSummaryUiByChatId[chatId] = true
        } else {
            pendingAsyncSummaryUiByChatId.remove(chatId)
        }
    }

    fun setInputProcessingStateForChat(chatId: String, state: EnhancedInputProcessingState) {
        setChatInputProcessingState(chatId, state)
    }

    suspend fun buildUserMessageContentForGroupOrchestration(
        messageText: String,
        attachments: List<AttachmentInfo>,
        workspacePath: String?,
        workspaceEnv: String?,
        replyToMessage: ChatMessage?,
        chatId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val totalStartTime = messageTimingNow()
        val configId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
        val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
        val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
        val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
        val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing

        val finalMessageContent = AIMessageManager.buildUserMessageContent(
            context = context,
            messageText = messageText,
            attachments = attachments,
            workspacePath = workspacePath,
            workspaceEnv = workspaceEnv,
            replyToMessage = replyToMessage,
            enableDirectImageProcessing = enableDirectImageProcessing,
            enableDirectAudioProcessing = enableDirectAudioProcessing,
            enableDirectVideoProcessing = enableDirectVideoProcessing,
            chatId = chatId,
            onHookTimeout = { pluginIdentifier ->
                reportNonFatalError(
                    context.getString(
                        R.string.toolpkg_hook_timeout_continue_sending_with_plugin,
                        pluginIdentifier
                    )
                )
            }
        )
        logMessageTiming(
            stage = "delegate.groupOrchestration.buildUserMessageContent",
            startTimeMs = totalStartTime,
            details = "attachments=${attachments.size}, configId=$configId, finalLength=${finalMessageContent.length}"
        )
        finalMessageContent
    }

    fun getResponseStream(chatId: String): SharedStream<String>? {
        return chatRuntimes[chatKey(chatId)]?.responseStream
    }

    private fun resolveFinalContent(aiMessage: ChatMessage): String {
        val sharedStream = aiMessage.contentStream as? SharedStream<String>
        val replayChunks = sharedStream?.replayCache
        val eventCarrier = aiMessage.contentStream as? TextStreamEventCarrier

        return if (eventCarrier?.eventChannel?.replayCache?.isNotEmpty() == true) {
            aiMessage.content
        } else if (!replayChunks.isNullOrEmpty()) {
            replayChunks.joinToString(separator = "")
        } else {
            aiMessage.content
        }
    }

    private fun ChatMessage.withTurnMetrics(
        inputTokens: Int,
        outputTokens: Int,
        cachedInputTokens: Int,
        sentAt: Long,
        outputDurationMs: Long,
        waitDurationMs: Long
    ): ChatMessage {
        return copy(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs
        )
    }

    internal data class TurnCancellationSnapshot(
        val inputTokens: Int,
        val outputTokens: Int,
        val cachedInputTokens: Int,
        val sentAt: Long,
        val outputDurationMs: Long,
        val waitDurationMs: Long,
    )

    private fun readCurrentTurnCancellationSnapshot(chatId: String): TurnCancellationSnapshot? {
        val service = EnhancedAIService.getChatInstance(context, chatId)
        val runtime = runtimeFor(chatId)
        return runCatching {
            val snapshot = service.captureCurrentTurnTokenSnapshot()
            val sentAt = runtime.requestSentAt
            val firstResponseElapsed = runtime.firstResponseElapsed
            val waitDurationMs =
                if (runtime.requestStartElapsed > 0L && firstResponseElapsed != null) {
                    (firstResponseElapsed - runtime.requestStartElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            val outputDurationMs =
                if (firstResponseElapsed != null) {
                    (messageTimingNow() - firstResponseElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            TurnCancellationSnapshot(
                inputTokens = snapshot.inputTokens,
                outputTokens = snapshot.outputTokens,
                cachedInputTokens = snapshot.cachedInputTokens,
                sentAt = sentAt,
                outputDurationMs = outputDurationMs,
                waitDurationMs = waitDurationMs,
            )
        }.onFailure {
            AppLogger.w(TAG, "读取取消请求的统计快照失败", it)
        }.getOrNull()
    }

    private suspend fun detachStreamingAiMessage(
        chatId: String,
        activeTurn: ActiveStreamingTurn,
        snapshot: TurnCancellationSnapshot? = null,
    ): ChatMessage {
        val streamingMessage = activeTurn.message
        val finalContent = resolveFinalContent(streamingMessage)
        val completedAt = System.currentTimeMillis()
        val finalMessage =
            completeInterruptedMessage(
                streamingMessage = streamingMessage,
                finalContent = finalContent,
                snapshot = snapshot,
                completedAt = completedAt,
            )
        streamingMessage.content = finalMessage.content
        streamingMessage.contentStream = null
        val messages = getRuntimeChatHistory(chatId)
        withContext(Dispatchers.Main) {
            snapshot?.let { stats ->
                val matchingUserMessage =
                    messages.lastOrNull { message ->
                        message.sender == "user" &&
                            message.sentAt == (stats.sentAt.takeIf { it > 0L } ?: streamingMessage.sentAt)
                    }
                if (matchingUserMessage != null) {
                    addMessageToChat(
                        chatId,
                        matchingUserMessage.withTurnMetrics(
                            inputTokens = stats.inputTokens,
                            outputTokens = stats.outputTokens,
                            cachedInputTokens = stats.cachedInputTokens,
                            sentAt = stats.sentAt.takeIf { it > 0L } ?: matchingUserMessage.sentAt,
                            outputDurationMs = stats.outputDurationMs,
                            waitDurationMs = stats.waitDurationMs,
                        ),
                    )
                }
            }
            val segmentedMessages = activeTurn.segmentedMessages
            if (segmentedMessages == null) {
                addMessageToChat(chatId, finalMessage)
            } else {
                segmentedMessages.forEach { segmentMessage ->
                    addMessageToChat(
                        chatId,
                        segmentMessage.copy(
                            inputTokens = finalMessage.inputTokens,
                            outputTokens = finalMessage.outputTokens,
                            cachedInputTokens = finalMessage.cachedInputTokens,
                            sentAt = finalMessage.sentAt,
                            outputDurationMs = finalMessage.outputDurationMs,
                            waitDurationMs = finalMessage.waitDurationMs,
                            completedAt = completedAt,
                        ),
                    )
                }
            }
        }
        return finalMessage
    }

    /**
     * 失败回合只保存已经收到且可安全重放的 assistant 正文。
     *
     * Responses 正文中断时提交状态仍然未知，不能重发原 POST；消息层必须在清理活动流前固定
     * 当前共享流内容，并让后续输入从没有未闭合工具事务的历史继续。
     */
    private suspend fun persistFailedAssistantProjection(
        state: SendUserMessageTurnState,
    ): ChatMessage? {
        if (!state.effectivePersistTurn || !state.hasAiMessage()) {
            return null
        }
        val activeTurn = state.chatRuntime.activeStreamingTurn ?: return null
        val finalContent = resolveFinalContent(activeTurn.message)
        projectFailedAssistantMessage(
            streamingMessage = activeTurn.message,
            finalContent = finalContent,
            snapshot = null,
            completedAt = System.currentTimeMillis(),
        ) ?: return null
        val persistedMessage =
            detachStreamingAiMessage(
                chatId = state.chatId,
                activeTurn = activeTurn,
                snapshot = readCurrentTurnCancellationSnapshot(state.chatId),
            )
        // detachStreamingAiMessage 使用同一 replay 投影；保留其实际返回值作为失败回合的唯一消息快照。
        state.aiMessage = persistedMessage
        return persistedMessage
    }

    private suspend fun cancelMessageInternal(
        chatId: String,
        keepPartialResponse: Boolean,
        expectedTurnId: Long? = null,
        source: AssistantTurnCancellationSource,
    ) {
        val chatRuntime = runtimeFor(chatId)
        chatRuntime.cancellationMutex.withLock {
            val turnId = expectedTurnId ?: chatRuntime.activeTurnId
            if (!chatRuntime.isLoading.value || chatRuntime.activeTurnId != turnId) {
                return@withLock
            }

            chatRuntime.cancellationInProgress = true
            val currentTurnOptions = chatRuntime.currentTurnOptions
            val activeTurn =
                if (keepPartialResponse) chatRuntime.activeStreamingTurn else null
            val cancellationSnapshot =
                if (keepPartialResponse) readCurrentTurnCancellationSnapshot(chatId) else null
            val jobsToCancel =
                linkedSetOf<Job>().apply {
                    chatRuntime.sendJob?.let { add(it) }
                    chatRuntime.stateCollectionJob?.let { add(it) }
                    chatRuntime.streamCollectionJob?.let { add(it) }
                }

            try {
                clearCurrentTurnToolInvocationCount(chatId)
                AIMessageManager.cancelOperation(
                    chatId = chatId,
                    source = source,
                    operationId = turnId,
                )

                jobsToCancel.forEach { job -> job.cancel() }
                jobsToCancel.forEach { job ->
                    try {
                        job.join()
                    } catch (_: kotlinx.coroutines.CancellationException) {
                    }
                }

                if (activeTurn != null) {
                    detachStreamingAiMessage(
                        chatId = chatId,
                        activeTurn = activeTurn,
                        snapshot = cancellationSnapshot,
                    )
                }

                if (currentTurnOptions.persistTurn) {
                    withContext(Dispatchers.IO) { saveCurrentChat() }
                }
            } finally {
                chatRuntime.cancellationInProgress = false
                if (chatRuntime.activeTurnId == turnId) {
                    chatRuntime.sendJob = null
                    chatRuntime.stateCollectionJob = null
                    chatRuntime.streamCollectionJob = null
                    chatRuntime.responseStream = null
                    chatRuntime.activeStreamingTurn = null
                    chatRuntime.currentTurnOptions = ChatTurnOptions()
                    chatRuntime.requestSentAt = 0L
                    chatRuntime.requestStartElapsed = 0L
                    chatRuntime.firstResponseElapsed = null
                    chatRuntime.isLoading.value = false
                    updateGlobalLoadingState()
                    setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
                }
            }
        }
    }

    fun cancelMessage(chatId: String) {
        val expectedTurnId = runtimeFor(chatId).activeTurnId
        coroutineScope.launch(Dispatchers.IO) {
            cancelMessageInternal(
                chatId = chatId,
                keepPartialResponse = true,
                expectedTurnId = expectedTurnId,
                source = AssistantTurnCancellationSource.USER_STOP,
            )
        }
    }

    suspend fun cancelMessageForDestructiveMutation(chatId: String) {
        cancelMessageInternal(
            chatId = chatId,
            keepPartialResponse = false,
            source = AssistantTurnCancellationSource.DESTRUCTIVE_HISTORY_MUTATION,
        )
    }

    init {
        AppLogger.d(TAG, "MessageProcessingDelegate初始化: 创建滚动事件流")
    }

    fun setActiveDraftChat(chatId: String?) {
        val previousChatId = activeDraftChatId
        if (previousChatId == chatId) {
            return
        }

        val currentValue = _userMessage.value
        if (previousChatId != null) {
            saveUserMessageDraft(previousChatId, currentValue)
        }

        activeDraftChatId = chatId
        if (chatId == null) {
            _userMessage.value = TextFieldValue("")
            return
        }

        val savedDraft = userMessageDraftsByChatId[chatId]
        if (savedDraft != null) {
            _userMessage.value = savedDraft
            return
        }

        if (previousChatId == null && currentValue.text.isNotEmpty()) {
            saveUserMessageDraft(chatId, currentValue)
            _userMessage.value = currentValue
            return
        }

        _userMessage.value = TextFieldValue("")
    }

    fun updateUserMessage(message: String) {
        setUserMessageDraft(TextFieldValue(message))
    }

    fun updateUserMessage(value: TextFieldValue) {
        setUserMessageDraft(value)
    }

    private fun setUserMessageDraft(value: TextFieldValue) {
        _userMessage.value = value
        val chatId = activeDraftChatId
        if (chatId != null) {
            saveUserMessageDraft(chatId, value)
        }
    }

    private fun saveUserMessageDraft(chatId: String, value: TextFieldValue) {
        if (value.text.isEmpty()) {
            userMessageDraftsByChatId.remove(chatId)
            return
        }

        userMessageDraftsByChatId[chatId] = value
    }

    private fun clearUserMessageDraft(chatId: String) {
        userMessageDraftsByChatId.remove(chatId)
        if (activeDraftChatId == chatId) {
            _userMessage.value = TextFieldValue("")
        }
    }

    fun scrollToBottom() {
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun getTurnCompleteCounter(chatId: String): Long {
        return _turnCompleteCounterByChatId.value[chatId] ?: 0L
    }

    fun isChatLoading(chatId: String): Boolean {
        return runtimeFor(chatId).isLoading.value
    }

    suspend fun awaitChatTurnIdleForHistoryBoundary(chatId: String) {
        runtimeFor(chatId).isLoading.first { isLoading -> !isLoading }
    }

    fun setSpeakMessageHandler(handler: (String, Boolean) -> Unit) {
        speakMessageHandler = handler
    }

    private fun resetCurrentTurnToolInvocationCount(chatId: String) {
        val updated = _currentTurnToolInvocationCountByChatId.value.toMutableMap()
        updated[chatId] = 0
        _currentTurnToolInvocationCountByChatId.value = updated
    }

    private fun incrementCurrentTurnToolInvocationCount(chatId: String) {
        val updated = _currentTurnToolInvocationCountByChatId.value.toMutableMap()
        updated[chatId] = (updated[chatId] ?: 0) + 1
        _currentTurnToolInvocationCountByChatId.value = updated
    }

    private fun clearCurrentTurnToolInvocationCount(chatId: String) {
        val updated = _currentTurnToolInvocationCountByChatId.value.toMutableMap()
        updated.remove(chatId)
        _currentTurnToolInvocationCountByChatId.value = updated
    }

    fun sendUserMessage(
            attachments: List<AttachmentInfo> = emptyList(),
            chatId: String,
            messageTextOverride: String? = null,
            proxySenderNameOverride: String? = null,
            workspacePath: String? = null,
            workspaceEnv: String? = null,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            roleCardId: String,
            enableThinking: Boolean = false,
            enableMemoryAutoUpdate: Boolean = true,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            replyToMessage: ChatMessage? = null, // 新增回复消息参数
            isAutoContinuation: Boolean = false, // 标识是否为自动续写
            enableSummary: Boolean = true,
            chatModelConfigIdOverride: String? = null,
            chatModelIndexOverride: Int? = null,
            memorySpaceIdOverride: String? = null,
            suppressUserMessageInHistory: Boolean = false,
            isGroupOrchestrationTurn: Boolean = false,
            groupParticipantNamesText: String? = null,
            turnOptions: ChatTurnOptions = ChatTurnOptions()
    ) {
        val rawMessageText = messageTextOverride ?: _userMessage.value.text
        // 群组编排模式下，允许空消息（后续成员不需要用户消息）
        if (rawMessageText.isBlank() && attachments.isEmpty() && !isAutoContinuation && !isGroupOrchestrationTurn) {
            AppLogger.d(
                TAG,
                "sendUserMessage忽略: 空消息且无附件, chatId=$chatId, autoContinuation=$isAutoContinuation"
            )
            return
        }
        val chatRuntime = runtimeFor(chatId)
        if (chatRuntime.isLoading.value) {
            AppLogger.w(
                TAG,
                "sendUserMessage忽略: chat正在处理中, chatId=$chatId, roleCardId=$roleCardId, override=${!messageTextOverride.isNullOrBlank()}, suppressUserMessageInHistory=$suppressUserMessageInHistory"
            )
            return
        }
        val turnId = chatRuntime.turnSequence.incrementAndGet()
        chatRuntime.activeTurnId = turnId

        val originalMessageText = rawMessageText.trim()
        var messageText = originalMessageText
        
        if (messageTextOverride == null) {
            clearUserMessageDraft(chatId)
        }
        resetCurrentTurnToolInvocationCount(chatId)
        chatRuntime.responseStream = null
        chatRuntime.activeStreamingTurn = null
        chatRuntime.isLoading.value = true
        chatRuntime.currentTurnOptions = turnOptions
        updateGlobalLoadingState()
        setChatInputProcessingState(chatId, EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)))

        val sendJob =
            coroutineScope.launch(Dispatchers.IO) {
                executeSendUserMessageTurn(
                    attachments = attachments,
                    chatId = chatId,
                    originalMessageText = originalMessageText,
                    proxySenderNameOverride = proxySenderNameOverride,
                    workspacePath = workspacePath,
                    workspaceEnv = workspaceEnv,
                    promptFunctionType = promptFunctionType,
                    roleCardId = roleCardId,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    replyToMessage = replyToMessage,
                    isAutoContinuation = isAutoContinuation,
                    enableSummary = enableSummary,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                    suppressUserMessageInHistory = suppressUserMessageInHistory,
                    isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                    groupParticipantNamesText = groupParticipantNamesText,
                    turnOptions = turnOptions,
                    chatRuntime = chatRuntime,
                    turnId = turnId,
                )
            }
        chatRuntime.sendJob = sendJob
    }

    /**
     * 执行单轮普通发送。
     *
     * 这里必须保持为独立 suspend 入口，不能重新内联进 launch：vivo Android 16 的 ART
     * 会在解释原先包含整轮状态机的巨大 invokeSuspend 时遇到非法 opcode 并直接 SIGABRT。
     * 独立入口让启动 Job 的 invokeSuspend 只承担一次稳定调用，后续阶段可以继续按 DEX 证据拆分。
     */
    private suspend fun executeSendUserMessageTurn(
        attachments: List<AttachmentInfo>,
        chatId: String,
        originalMessageText: String,
        proxySenderNameOverride: String?,
        workspacePath: String?,
        workspaceEnv: String?,
        promptFunctionType: PromptFunctionType,
        roleCardId: String,
        enableThinking: Boolean,
        enableMemoryAutoUpdate: Boolean,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        replyToMessage: ChatMessage?,
        isAutoContinuation: Boolean,
        enableSummary: Boolean,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?,
        memorySpaceIdOverride: String?,
        suppressUserMessageInHistory: Boolean,
        isGroupOrchestrationTurn: Boolean,
        groupParticipantNamesText: String?,
        turnOptions: ChatTurnOptions,
        chatRuntime: ChatRuntime,
        turnId: Long,
    ) {
            val state =
                SendUserMessageTurnState(
                    attachments = attachments,
                    chatId = chatId,
                    originalMessageText = originalMessageText,
                    proxySenderNameOverride = proxySenderNameOverride,
                    workspacePath = workspacePath,
                    workspaceEnv = workspaceEnv,
                    promptFunctionType = promptFunctionType,
                    roleCardId = roleCardId,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    replyToMessage = replyToMessage,
                    isAutoContinuation = isAutoContinuation,
                    enableSummary = enableSummary,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                    suppressUserMessageInHistory = suppressUserMessageInHistory,
                    isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                    groupParticipantNamesText = groupParticipantNamesText,
                    turnOptions = turnOptions,
                    chatRuntime = chatRuntime,
                    turnId = turnId,
                )
            state.sendUserMessageStartTime = messageTimingNow()
            try {
                prepareSendUserMessageTurn(state)
                prepareAssistantRequest(state)
                submitAssistantResponse(state)
                startAndAwaitAssistantResponseCollection(state)
                completeAssistantResponse(state)
                state.terminalOutcome = "completed"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    val cancellation =
                        resolveAssistantTurnCancellation(
                            chatId = state.chatId,
                            turnId = state.turnId,
                            error = e,
                        )
                    state.finalInputStateAfterSend = cancellation.terminalState
                    state.shouldNotifyTurnComplete = false
                    state.cancellationToPropagate = e
                    state.terminalCancellationSource = cancellation.source
                    state.terminalOutcome =
                        if (cancellation.source ==
                            AssistantTurnCancellationSource.USER_STOP ||
                            cancellation.source ==
                            AssistantTurnCancellationSource.DESTRUCTIVE_HISTORY_MUTATION
                        ) {
                            "user_cancelled"
                        } else {
                            "unexpected_cancelled"
                        }
                    if (
                        state.effectivePersistTurn &&
                            cancellation.source != AssistantTurnCancellationSource.USER_STOP &&
                            cancellation.source !=
                                AssistantTurnCancellationSource.DESTRUCTIVE_HISTORY_MUTATION
                    ) {
                        withContext(NonCancellable) {
                            state.chatRuntime.activeStreamingTurn?.let { activeTurn ->
                                runCatching {
                                    detachStreamingAiMessage(
                                        chatId = state.chatId,
                                        activeTurn = activeTurn,
                                        snapshot = readCurrentTurnCancellationSnapshot(state.chatId),
                                    )
                                }.onFailure { persistenceError ->
                                    AppLogger.e(
                                        TAG,
                                        "非预期取消回合的安全部分消息持久化失败",
                                        persistenceError,
                                    )
                                }
                            }
                            runCatching { saveCurrentChat() }
                                .onFailure { persistenceError ->
                                    AppLogger.e(
                                        TAG,
                                        "非预期取消回合的聊天状态保存失败",
                                        persistenceError,
                                    )
                                }
                        }
                    }
                    if (state.effectivePersistTurn) {
                        withContext(NonCancellable) {
                            runCatching {
                                conversationAuditRepository.appendEvent(
                                    ConversationAuditEventRequest(
                                        chatId = state.chatId,
                                        category = "PROVIDER",
                                        eventType = "PROVIDER_TERMINAL",
                                        actor = "KIYORI",
                                        summary =
                                            "回合已取消，来源为 ${cancellation.source.name}",
                                        messageTimestamp =
                                            state.aiMessage.timestamp.takeIf { it > 0L },
                                        variantIndex = 0,
                                        terminalState = "CANCELLED",
                                        completeness =
                                            ConversationAuditCompletenessStatus.PARTIAL,
                                        payloads =
                                            listOf(
                                                ConversationAuditPayloadInput.text(
                                                    label = "cancellation",
                                                    role = "error",
                                                    value = e.stackTraceToString(),
                                                    mediaType =
                                                        "text/x-java-stacktrace",
                                                )
                                            ),
                                    )
                                )
                                conversationAuditRepository.seal(
                                    state.chatId,
                                    reason = "TURN_CANCELLED",
                                )
                            }.onFailure { auditError ->
                                AppLogger.e(
                                    TAG,
                                    "取消回合的审计终态写入失败",
                                    auditError,
                                )
                            }
                        }
                    }
                } else {
                    handleAssistantTurnFailure(state, e)
                }
            } finally {
                ensureAssistantTurnTerminalOutcome(state)
                val finalizeMessageStartTime = messageTimingNow()
                val deferTurnCompleteToAsyncJob =
                    if (
                        state.cancellationToPropagate == null &&
                            state.terminalOutcome == "completed"
                    ) {
                        finalizeMessageAndNotify(
                            chatId = state.chatId,
                            activeChatId = state.activeChatId,
                            aiMessageProvider = { state.aiMessage },
                            isWaifuModeEnabled = state.isWaifuModeEnabled,
                            skipFinalAutoRead =
                                state.didStreamAutoRead && !state.isWaifuModeEnabled,
                            syncWaifuMessageMetrics = { sourceMessage ->
                                syncWaifuMessageMetrics(state, sourceMessage)
                            },
                            calculateNextWindowSize = state.calculateNextWindowSize,
                            turnOptions = state.turnOptions
                        )
                    } else {
                        AppLogger.d(
                            TAG,
                            "非成功回合不执行 Completed 消息收尾: chatId=${state.activeChatId}, " +
                                "outcome=${state.terminalOutcome}"
                        )
                        false
                    }
                logMessageTiming(
                    stage = "delegate.finalizeMessage",
                    startTimeMs = finalizeMessageStartTime,
                    details = "chatId=${state.activeChatId}, notifyTurnComplete=${state.shouldNotifyTurnComplete}"
                )

                state.workspaceToolHookSession?.let { session ->
                    val cleanupWorkspaceHookStartTime = messageTimingNow()
                    state.toolHandler?.let { toolHandler ->
                        runCatching { toolHandler.removeToolHook(session) }
                            .onFailure {
                                AppLogger.w(TAG, "Failed to remove workspace hook", it)
                            }
                    }
                    runCatching { session.close() }
                        .onFailure {
                            AppLogger.w(TAG, "Failed to close workspace hook session", it)
                        }
                    logMessageTiming(
                        stage = "delegate.cleanupWorkspaceHook",
                        startTimeMs = cleanupWorkspaceHookStartTime,
                        details = "chatId=${state.activeChatId}"
                    )
                }

                val cleanupRuntimeStartTime = messageTimingNow()
                cleanupRuntimeAfterSend(state.chatId, state.chatRuntime, state.turnId)
                logMessageTiming(
                    stage = "delegate.cleanupRuntime",
                    startTimeMs = cleanupRuntimeStartTime,
                    details = "chatId=${state.activeChatId}"
                )

                if (!deferTurnCompleteToAsyncJob) {
                    state.finalInputStateAfterSend?.let { terminalState ->
                        setChatInputProcessingState(state.chatId, terminalState)
                    }
                }

                if (state.shouldNotifyTurnComplete && !deferTurnCompleteToAsyncJob) {
                    state.serviceForTurnComplete?.let { service ->
                        notifyTurnComplete(
                            state.chatId,
                            state.activeChatId,
                            service,
                            state.calculateNextWindowSize,
                            state.turnOptions
                        )
                    }
                }

                logMessageTiming(
                    stage = "delegate.sendUserMessage.total",
                    startTimeMs = state.sendUserMessageStartTime,
                    details = "chatId=${state.activeChatId}, addedUserMessage=${state.userMessageAdded}, enableSummary=${state.enableSummary}, persistTurn=${state.turnOptions.persistTurn}"
                )
                AppLogger.d(
                    TAG,
                    buildString {
                        append("turn terminal: outcome=${state.terminalOutcome}, ")
                        append("cancellationSource=${state.terminalCancellationSource}, ")
                        append("chunks=${state.receivedChunkCount}, ")
                        append("visibleChars=${state.visibleContentLength()}, ")
                        state.terminalFailure?.let { failure ->
                            val diagnostics =
                                snapshotMessageFailure(
                                    failure = failure,
                                    phase = "message_owner",
                                )
                            append("diagnosticCode=${diagnostics.diagnosticCode}, ")
                            append("failurePhase=${diagnostics.phase}, ")
                            append("executionRef=${diagnostics.executionRef}, ")
                            append("observerCount=${diagnostics.secondaryObserverCount}, ")
                            append(
                                "propagationBoundaryCount=" +
                                    "${diagnostics.propagationBoundaryCount}, "
                            )
                        }
                        append("provider=${state.provider}, model=${state.modelName}, ")
                        append(
                            "providerRequestContextPresent=" +
                                state.providerRequestContextPresent
                        )
                    }
                )
                val currentJob = coroutineContext[Job]
                if (currentJob != null && state.chatRuntime.sendJob === currentJob) {
                    state.chatRuntime.sendJob = null
                }
            }
            state.cancellationToPropagate?.let { throw it }
    }

    private suspend fun prepareSendUserMessageTurn(
        state: SendUserMessageTurnState,
    ) {
        val isFirstMessage = !hasUserMessage(state.chatId)
        val titleFallback =
            if (state.effectivePersistTurn && isFirstMessage) {
                fallbackConversationTitle(state.originalMessageText, state.attachments).also { fallbackTitle ->
                    updateChatTitle(state.chatId, fallbackTitle)
                }
            } else {
                null
            }

        AppLogger.d(TAG, "开始处理用户消息：附件数量=${state.attachments.size}")
        if (state.effectivePersistTurn) {
            conversationAuditRepository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = state.chatId,
                    category = "USER",
                    eventType = "USER_INPUT_SUBMITTED",
                    actor = "USER",
                    summary = "用户提交了原始输入",
                    completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "raw_input",
                                role = "user",
                                value = state.originalMessageText,
                            ),
                            ConversationAuditPayloadInput.text(
                                label = "submission_context",
                                role = "context",
                                value =
                                    conversationAuditGson.toJson(
                                        mapOf(
                                            "proxySenderName" to
                                                state.proxySenderNameOverride,
                                            "replyToMessageTimestamp" to
                                                state.replyToMessage?.timestamp,
                                            "workspacePath" to state.workspacePath,
                                            "workspaceEnv" to state.workspaceEnv,
                                            "attachments" to
                                                state.attachments.map { attachment ->
                                                    mapOf(
                                                        "fileName" to attachment.fileName,
                                                        "mimeType" to attachment.mimeType,
                                                        "fileSize" to attachment.fileSize,
                                                        "expandedContentLength" to
                                                            attachment.content.length,
                                                    )
                                                },
                                        )
                                    ),
                                mediaType = "application/json",
                            ),
                        ),
                )
            )
        }

        val configId =
            state.chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
                ?: functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
        val loadModelConfigStartTime = messageTimingNow()
        val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
        val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
        val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
        val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing
        AppLogger.d(TAG, "直接图片处理状态: $enableDirectImageProcessing (配置ID: $configId)")
        logMessageTiming(
            stage = "delegate.loadModelConfig",
            startTimeMs = loadModelConfigStartTime,
            details = "chatId=${state.chatId}, configId=$configId"
        )

        val buildUserMessageStartTime = messageTimingNow()
        state.finalMessageContent =
            AIMessageManager.buildUserMessageContent(
                context = context,
                messageText = state.originalMessageText,
                proxySenderName = state.proxySenderNameOverride,
                attachments = state.attachments,
                workspacePath = state.workspacePath,
                workspaceEnv = state.workspaceEnv,
                replyToMessage = state.replyToMessage,
                enableDirectImageProcessing = enableDirectImageProcessing,
                enableDirectAudioProcessing = enableDirectAudioProcessing,
                enableDirectVideoProcessing = enableDirectVideoProcessing,
                chatId = state.chatId,
                roleCardId = state.roleCardId,
                onHookTimeout = { pluginIdentifier ->
                    reportNonFatalError(
                        context.getString(
                            R.string.toolpkg_hook_timeout_continue_sending_with_plugin,
                            pluginIdentifier
                        )
                    )
                }
            )
        logMessageTiming(
            stage = "delegate.buildUserMessageContent",
            startTimeMs = buildUserMessageStartTime,
            details = "chatId=${state.chatId}, attachments=${state.attachments.size}, finalLength=${state.finalMessageContent.length}"
        )

        // 自动继续和群组编排的空消息仍然只参与请求，不写入聊天历史。
        val shouldAddUserMessageToChat =
            state.effectivePersistTurn &&
                !state.suppressUserMessageInHistory &&
                !(state.isAutoContinuation &&
                    state.originalMessageText.isBlank() &&
                    state.attachments.isEmpty()) &&
                !(state.isGroupOrchestrationTurn &&
                    state.originalMessageText.isBlank() &&
                    state.attachments.isEmpty())
        state.userMessage =
            ChatMessage(
                sender = "user",
                content = state.finalMessageContent,
                roleName = context.getString(R.string.message_role_user),
                displayMode =
                    if (state.effectiveHideUserMessage) {
                        ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
                    } else {
                        ChatMessageDisplayMode.NORMAL
                    }
            )
        if (state.effectivePersistTurn) {
            conversationAuditRepository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = state.chatId,
                    category = "CONTEXT",
                    eventType = "USER_INPUT_TRANSFORMED",
                    actor = "KIYORI",
                    summary = "用户输入已完成代理发送者、回复、工作区和附件转换",
                    messageTimestamp = state.userMessage.timestamp,
                    variantIndex = 0,
                    completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "final_user_message",
                                role = "user",
                                value = state.finalMessageContent,
                            )
                        ),
                )
            )
        }

        val toolHandler = AIToolHandler.getInstance(context)
        state.toolHandler = toolHandler

        // 在消息发送期间临时挂载 workspace hook，结束后由统一收尾阶段卸载。
        if (!state.workspacePath.isNullOrBlank()) {
            val attachWorkspaceHookStartTime = messageTimingNow()
            try {
                val session =
                    WorkspaceBackupManager.getInstance(context)
                        .createWorkspaceToolHookSession(
                            workspacePath = state.workspacePath,
                            workspaceEnv = state.workspaceEnv,
                            messageTimestamp = state.userMessage.timestamp,
                            chatId = state.chatId
                        )
                state.workspaceToolHookSession = session
                toolHandler.addToolHook(session)
                AppLogger.d(
                    TAG,
                    "Workspace hook attached for timestamp=${state.userMessage.timestamp}, path=${state.workspacePath}"
                )
                logMessageTiming(
                    stage = "delegate.attachWorkspaceHook",
                    startTimeMs = attachWorkspaceHookStartTime,
                    details = "chatId=${state.chatId}, workspacePath=${state.workspacePath}"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to attach workspace hook", e)
                _nonFatalErrorEvent.emit(
                    context.getString(R.string.message_workspace_sync_failed, e.message)
                )
            }
        }

        if (shouldAddUserMessageToChat) {
            val addUserMessageStartTime = messageTimingNow()
            addMessageToChat(state.chatId, state.userMessage)
            state.userMessageAdded = true
            logMessageTiming(
                stage = "delegate.addUserMessageToChat",
                startTimeMs = addUserMessageStartTime,
                details = "chatId=${state.chatId}, contentLength=${state.userMessage.content.length}"
            )
            titleFallback?.let { fallbackTitle ->
                launchConversationTitleGeneration(
                    chatId = state.chatId,
                    userText = state.originalMessageText,
                    attachments = state.attachments,
                    fallbackTitle = fallbackTitle
                )
            }
        }
    }

    private suspend fun prepareAssistantRequest(
        state: SendUserMessageTurnState,
    ) {
        val acquireServiceStartTime = messageTimingNow()
        val service = EnhancedAIService.getChatInstance(context, state.activeChatId)
        state.service = service
        state.serviceForTurnComplete = service
        logMessageTiming(
            stage = "delegate.acquireService",
            startTimeMs = acquireServiceStartTime,
            details = "chatId=${state.activeChatId}, reusedChatInstance=true"
        )

        // 清除上一次可能残留的 Error 状态，避免 StateFlow 重放导致新一轮发送立即再次触发弹窗。
        service.setInputProcessingState(
            EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing))
        )

        state.chatRuntime.stateCollectionJob?.cancel()
        state.chatRuntime.stateCollectionJob =
            coroutineScope.launch {
                var lastErrorMessage: String? = null
                service.inputProcessingState.collect { inputState ->
                    setChatInputProcessingState(state.activeChatId, inputState)

                    if (inputState is EnhancedInputProcessingState.Error) {
                        val msg = inputState.message
                        if (msg != lastErrorMessage) {
                            lastErrorMessage = msg
                            withContext(Dispatchers.Main) {
                                showErrorMessage(msg)
                            }
                        }
                    } else {
                        lastErrorMessage = null
                    }
                }
            }

        state.responseStartTime = messageTimingNow()
        val userPreferencesManager = UserPreferencesManager.getInstance(context)

        val loadRoleInfoStartTime = messageTimingNow()
        val (characterName, avatarUri) =
            try {
                val roleCard = characterCardManager.getCharacterCardFlow(state.roleCardId).first()
                val avatar =
                    userPreferencesManager.getAiAvatarForCharacterCardFlow(roleCard.id).first()
                Pair(roleCard.name, avatar)
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取角色信息失败: ${e.message}", e)
                Pair(null, null)
            }
        state.characterName = characterName
        state.avatarUri = avatarUri
        state.currentRoleName = characterName ?: "Kiyori"
        logMessageTiming(
            stage = "delegate.loadRoleInfo",
            startTimeMs = loadRoleInfoStartTime,
            details = "chatId=${state.activeChatId}, roleCardId=${state.roleCardId}, roleName=${state.currentRoleName}"
        )
        state.calculateNextWindowSize = {
            runCatching {
                AIMessageManager.calculateStableContextWindow(
                    enhancedAiService = service,
                    chatId = state.activeChatId,
                    messageContent = "",
                    chatHistory = getRuntimeChatHistory(state.activeChatId),
                    workspacePath = state.workspacePath,
                    workspaceEnv = state.workspaceEnv,
                    promptFunctionType = state.promptFunctionType,
                    roleCardId = state.roleCardId,
                    currentRoleName = state.currentRoleName,
                    splitHistoryByRole = true,
                    groupOrchestrationMode = state.isGroupOrchestrationTurn,
                    groupParticipantNamesText = state.groupParticipantNamesText,
                    chatModelConfigIdOverride = state.chatModelConfigIdOverride,
                    chatModelIndexOverride = state.chatModelIndexOverride,
                    memorySpaceIdOverride = state.memorySpaceIdOverride,
                    publishEstimate = false
                )
            }.onFailure {
                AppLogger.w(TAG, "回合结束后重算上下文窗口失败", it)
            }.getOrNull()
        }

        val loadChatHistoryStartTime = messageTimingNow()
        state.chatHistory = getRuntimeChatHistory(state.activeChatId)
        logMessageTiming(
            stage = "delegate.loadChatHistory",
            startTimeMs = loadChatHistoryStartTime,
            details = "chatId=${state.activeChatId}, size=${state.chatHistory.size}"
        )

        state.effectiveMaxTokens = state.maxTokens
        val effectiveEnableSummary = state.enableSummary && state.effectivePersistTurn
        state.effectiveTokenUsageThreshold =
            if (effectiveEnableSummary) state.tokenUsageThreshold else Double.MAX_VALUE
        state.effectiveOnTokenLimitExceeded =
            if (effectiveEnableSummary) {
                suspend {
                    onTokenLimitExceeded(
                        state.activeChatId,
                        state.roleCardId,
                        state.isGroupOrchestrationTurn,
                        state.groupParticipantNamesText
                    )
                }
            } else {
                null
            }

        state.requestMessageContent =
            if (state.isGroupOrchestrationTurn &&
                state.finalMessageContent.trimStart().isNotEmpty() &&
                !state.finalMessageContent.trimStart().startsWith("[From user]")
            ) {
                "[From user]\n${state.finalMessageContent}"
            } else {
                state.finalMessageContent
            }

        val loadProviderModelStartTime = messageTimingNow()
        val (provider, modelName) =
            try {
                service.getDisplayProviderAndModelForFunction(
                    functionType = FunctionType.CHAT,
                    chatModelConfigIdOverride = state.chatModelConfigIdOverride,
                    chatModelIndexOverride = state.chatModelIndexOverride
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取provider和model信息失败: ${e.message}", e)
                Pair("", "")
            }
        state.provider = provider
        state.modelName = modelName
        logMessageTiming(
            stage = "delegate.loadProviderModel",
            startTimeMs = loadProviderModelStartTime,
            details = "chatId=${state.activeChatId}, provider=$provider, model=$modelName"
        )
        if (state.effectivePersistTurn) {
            conversationAuditRepository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = state.activeChatId,
                    category = "PROVIDER",
                    eventType = "PROVIDER_REQUEST_PREPARED",
                    actor = "KIYORI",
                    summary = "已生成交给 Provider 的请求上下文",
                    messageTimestamp = state.userMessage.timestamp,
                    variantIndex = 0,
                    completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "request_message",
                                role = "user",
                                value = state.requestMessageContent,
                            ),
                            ConversationAuditPayloadInput.text(
                                label = "history",
                                role = "context",
                                value =
                                    conversationAuditGson.toJson(
                                        state.chatHistory.map { message ->
                                            mapOf(
                                                "sender" to message.sender,
                                                "content" to message.content,
                                                "timestamp" to message.timestamp,
                                                "roleName" to message.roleName,
                                                "selectedVariantIndex" to
                                                    message.selectedVariantIndex,
                                                "provider" to message.provider,
                                                "modelName" to message.modelName,
                                            )
                                        }
                                    ),
                                mediaType = "application/json",
                            ),
                            ConversationAuditPayloadInput.text(
                                label = "request_parameters",
                                role = "context",
                                value =
                                    conversationAuditGson.toJson(
                                        mapOf(
                                            "provider" to state.provider,
                                            "modelName" to state.modelName,
                                            "promptFunctionType" to
                                                state.promptFunctionType.name,
                                            "roleCardId" to state.roleCardId,
                                            "currentRoleName" to state.currentRoleName,
                                            "enableThinking" to state.enableThinking,
                                            "enableMemoryAutoUpdate" to
                                                state.enableMemoryAutoUpdate,
                                            "maxTokens" to state.effectiveMaxTokens,
                                            "tokenUsageThreshold" to
                                                state.effectiveTokenUsageThreshold,
                                            "chatModelConfigIdOverride" to
                                                state.chatModelConfigIdOverride,
                                            "chatModelIndexOverride" to
                                                state.chatModelIndexOverride,
                                            "memorySpaceIdOverride" to
                                                state.memorySpaceIdOverride,
                                        )
                                    ),
                                mediaType = "application/json",
                            ),
                        ),
                )
            )
        }

        val waifuPreferences = WaifuPreferences.getInstance(context)
        state.isWaifuModeEnabled = waifuPreferences.enableWaifuModeFlow.first()
        state.waifuCharDelay = waifuPreferences.waifuCharDelayFlow.first()
        state.waifuRemovePunctuation =
            if (state.isWaifuModeEnabled) {
                waifuPreferences.waifuRemovePunctuationFlow.first()
            } else {
                false
            }

        state.requestSentAt = System.currentTimeMillis()
        state.requestStartElapsed = messageTimingNow()
        state.chatRuntime.requestSentAt = state.requestSentAt
        state.chatRuntime.requestStartElapsed = state.requestStartElapsed
        state.chatRuntime.firstResponseElapsed = null
        if (state.userMessageAdded) {
            state.userMessage = state.userMessage.copy(sentAt = state.requestSentAt)
            addMessageToChat(state.chatId, state.userMessage)
        }
    }

    private suspend fun submitAssistantResponse(
        state: SendUserMessageTurnState,
    ) {
        val prepareResponseStreamStartTime = messageTimingNow()
        val aiMessageTimestamp = ChatMessageTimestampAllocator.next()
        val providerRequestContext =
            if (state.effectivePersistTurn) {
                ProviderRequestContext.create(
                    chatId = state.activeChatId,
                    messageTimestamp = aiMessageTimestamp,
                    variantIndex = 0,
                )
            } else {
                null
            }
        state.providerRequestContext = providerRequestContext
        state.providerRequestContextPresent = providerRequestContext != null
        val responseStream =
            AIMessageManager.sendMessage(
                enhancedAiService = state.service,
                chatId = state.activeChatId,
                messageContent = state.requestMessageContent,
                chatHistory =
                    if (state.isGroupOrchestrationTurn &&
                        state.userMessageAdded &&
                        state.chatHistory.isNotEmpty()
                    ) {
                        state.chatHistory.subList(0, state.chatHistory.size - 1)
                    } else {
                        state.chatHistory
                    },
                workspacePath = state.workspacePath,
                promptFunctionType = state.promptFunctionType,
                enableThinking = state.enableThinking,
                enableMemoryAutoUpdate = state.enableMemoryAutoUpdate,
                maxTokens = state.effectiveMaxTokens,
                tokenUsageThreshold = state.effectiveTokenUsageThreshold,
                onNonFatalError = { error ->
                    _nonFatalErrorEvent.emit(error)
                },
                onTokenLimitExceeded = state.effectiveOnTokenLimitExceeded,
                characterName = state.characterName,
                avatarUri = state.avatarUri,
                roleCardId = state.roleCardId,
                currentRoleName = state.currentRoleName,
                splitHistoryByRole = true,
                groupOrchestrationMode = state.isGroupOrchestrationTurn,
                groupParticipantNamesText = state.groupParticipantNamesText,
                proxySenderName = state.proxySenderNameOverride,
                onToolInvocation = {
                    incrementCurrentTurnToolInvocationCount(state.chatId)
                },
                notifyReplyOverride = state.turnOptions.notifyReply,
                chatModelConfigIdOverride = state.chatModelConfigIdOverride,
                chatModelIndexOverride = state.chatModelIndexOverride,
                memorySpaceIdOverride = state.memorySpaceIdOverride,
                disableWarning = state.turnOptions.disableWarning,
                providerRequestContext = providerRequestContext,
                operationId = state.turnId,
            )
        if (state.effectivePersistTurn) {
            conversationAuditRepository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = state.activeChatId,
                    category = "PROVIDER",
                    eventType = "PROVIDER_REQUEST_SUBMITTED",
                    actor = "KIYORI",
                    summary = "请求已交给 Provider 执行管线",
                    messageTimestamp = aiMessageTimestamp,
                    variantIndex = 0,
                    completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                )
            )
        }
        state.sharedCharStream = responseStream
        state.chatRuntime.responseStream = responseStream
        state.aiMessage =
            ChatMessage(
                sender = "ai",
                contentStream = responseStream,
                timestamp = aiMessageTimestamp,
                roleName = state.currentRoleName,
                provider = state.provider,
                modelName = state.modelName,
                sentAt = state.requestSentAt
            )
        if (state.effectivePersistTurn) {
            state.chatRuntime.activeStreamingTurn =
                ActiveStreamingTurn(
                    message = state.aiMessage,
                    segmentedMessages =
                        if (state.isWaifuModeEnabled) state.waifuEmittedMessages else null,
                )
        }
        logMessageTiming(
            stage = "delegate.prepareResponseStream",
            startTimeMs = prepareResponseStreamStartTime,
            details = "chatId=${state.activeChatId}, requestLength=${state.requestMessageContent.length}, history=${state.chatHistory.size}"
        )
        AppLogger.d(
            TAG,
            "创建带流的AI消息, stream is null: ${state.aiMessage.contentStream == null}, timestamp: ${state.aiMessage.timestamp}"
        )

        if (!state.isWaifuModeEnabled) {
            withContext(Dispatchers.Main) {
                if (state.effectivePersistTurn) {
                    addMessageToChat(state.chatId, state.aiMessage)
                }
            }
        }
    }

    private suspend fun emitWaifuSegment(
        state: SendUserMessageTurnState,
        segment: String,
    ) {
        if (segment.isBlank()) return

        val interrupt = state.waifuEmittedMessages.isEmpty()
        val segmentMessage =
            ChatMessage(
                sender = "ai",
                content = segment,
                contentStream = null,
                timestamp = ChatMessageTimestampAllocator.next(),
                roleName = state.currentRoleName,
                provider = state.provider,
                modelName = state.modelName,
                sentAt = state.requestSentAt
            )

        withContext(Dispatchers.Main) {
            state.waifuEmittedMessages += segmentMessage
            if (state.effectivePersistTurn) {
                addMessageToChat(state.chatId, segmentMessage)
            }
            if (getIsAutoReadEnabled()) {
                state.didStreamAutoRead = true
                AppLogger.d(
                    TAG,
                    "autoRead[waifuStream] interrupt=$interrupt len=${segment.length}"
                )
                speakMessageHandler(segment, interrupt)
            }
            tryEmitScrollToBottomThrottled(state.chatId)
        }
    }

    private suspend fun syncWaifuMessageMetrics(
        state: SendUserMessageTurnState,
        sourceMessage: ChatMessage,
    ) {
        if (!state.effectivePersistTurn || state.waifuEmittedMessages.isEmpty()) return

        withContext(Dispatchers.Main) {
            val updatedMessages =
                applyWaifuTurnMetrics(
                    messages = state.waifuEmittedMessages,
                    sourceMessage = sourceMessage,
                )
            updatedMessages.forEachIndexed { index, updatedMessage ->
                state.waifuEmittedMessages[index] = updatedMessage
                addMessageToChat(state.chatId, updatedMessage)
            }
        }
    }

    private suspend fun startAndAwaitAssistantResponseCollection(
        state: SendUserMessageTurnState,
    ) {
        val streamCollectionResult = CompletableDeferred<Throwable?>()
        state.chatRuntime.streamCollectionJob =
            coroutineScope.launch(Dispatchers.IO) {
                collectAssistantResponseStream(state, streamCollectionResult)
            }

        val streamCollectionError = streamCollectionResult.await()
        if (streamCollectionError != null) {
            throw streamCollectionError
        }
        logMessageTiming(
            stage = "delegate.sharedStreamComplete",
            startTimeMs = state.responseStartTime,
            details = "chatId=${state.activeChatId}"
        )
    }

    private suspend fun collectAssistantResponseStream(
        state: SendUserMessageTurnState,
        streamCollectionResult: CompletableDeferred<Throwable?>,
    ) {
        collectForMessageFailureOwner(streamCollectionResult) {
            var hasLoggedFirstChunk = false
            var lastStreamingPersistAt = 0L
            val revisionTracker = TextStreamRevisionTracker()
            val revisionMutex = Mutex()
            val autoReadBuffer = StringBuilder()
            var isFirstAutoReadSegment = true
            val autoReadStream =
                if (!state.isWaifuModeEnabled) {
                    WaifuMessageProcessor.streamTtsText(state.sharedCharStream)
                } else {
                    null
                }
            val revisableStream = state.sharedCharStream as? TextStreamEventCarrier

            fun flushAutoReadSegment(segment: String, interrupt: Boolean) {
                val trimmed = segment.trim()
                if (trimmed.isNotEmpty()) {
                    state.didStreamAutoRead = true
                    AppLogger.d(
                        TAG,
                        "autoRead[flush] interrupt=$interrupt didStreamAutoRead=${state.didStreamAutoRead} rawLen=${segment.length} trimmedLen=${trimmed.length}"
                    )
                    speakMessageHandler(trimmed, interrupt)
                } else if (segment.isNotEmpty()) {
                    AppLogger.d(
                        TAG,
                        "autoRead[flush.skipBlank] rawLen=${segment.length}"
                    )
                }
            }

            fun tryFlushAutoRead() {
                if (!getIsAutoReadEnabled()) return
                if (state.isWaifuModeEnabled) return
                while (true) {
                    val bufferBefore = autoReadBuffer.length
                    val cutIdx = TtsSegmenter.nextSegmentEnd(autoReadBuffer)
                    if (cutIdx < 0) return

                    val seg = autoReadBuffer.substring(0, cutIdx)
                    autoReadBuffer.delete(0, cutIdx)
                    AppLogger.d(
                        TAG,
                        "autoRead[cut] cutIdx=$cutIdx bufferBefore=$bufferBefore bufferAfter=${autoReadBuffer.length} firstSegment=$isFirstAutoReadSegment rawLen=${seg.length}"
                    )

                    flushAutoReadSegment(seg, interrupt = isFirstAutoReadSegment)
                    isFirstAutoReadSegment = false
                }
            }

            fun claimStreamingSnapshot(): Boolean {
                if (!state.effectivePersistTurn || state.isWaifuModeEnabled) return false
                val now = messageTimingNow()
                if (now - lastStreamingPersistAt < STREAM_PERSIST_INTERVAL_MS) {
                    return false
                }
                lastStreamingPersistAt = now
                return true
            }

            val autoReadJob =
                autoReadStream?.let { stream ->
                    launch {
                        var observedChars = 0
                        observeSecondaryStream(
                            observation = {
                                SecondaryStreamObservation(
                                    observerName = "auto_read",
                                    phase = "secondary_auto_read",
                                    terminalOutcome = "main_stream_failure",
                                    chunks = observedChars,
                                    visibleChars = observedChars,
                                )
                            },
                            onFailure = { secondaryFailure ->
                                AppLogger.w(
                                    TAG,
                                    secondaryFailure.format(),
                                )
                            }
                        ) {
                            stream.collect { char ->
                                observedChars += 1
                                autoReadBuffer.append(char)
                                tryFlushAutoRead()
                            }
                        }
                    }
                }
            val waifuSegmentsJob =
                if (state.isWaifuModeEnabled) {
                    launch {
                        var observedSegments = 0
                        var observedChars = 0
                        observeSecondaryStream(
                            observation = {
                                SecondaryStreamObservation(
                                    observerName = "waifu_segments",
                                    phase = "secondary_waifu_segments",
                                    terminalOutcome = "main_stream_failure",
                                    chunks = observedSegments,
                                    visibleChars = observedChars,
                                )
                            },
                            onFailure = { secondaryFailure ->
                                AppLogger.w(
                                    TAG,
                                    secondaryFailure.format(),
                                )
                            }
                        ) {
                            WaifuMessageProcessor.streamSegmentsWithTypingQueue(
                                sourceStream = state.sharedCharStream,
                                removePunctuation = state.waifuRemovePunctuation,
                                charDelayMs = state.waifuCharDelay
                            ).collect { segment ->
                                observedSegments += 1
                                observedChars += segment.length
                                emitWaifuSegment(state, segment)
                            }
                        }
                    }
                } else {
                    null
                }

            val revisionJob =
                revisableStream?.let { carrier ->
                    launch {
                        var observedEvents = 0
                        var observedVisibleChars = 0
                        observeSecondaryStream(
                            observation = {
                                SecondaryStreamObservation(
                                    observerName = "revision_events",
                                    phase = "secondary_revision_events",
                                    terminalOutcome = "main_stream_failure",
                                    chunks = observedEvents,
                                    visibleChars = observedVisibleChars,
                                )
                            },
                            onFailure = { secondaryFailure ->
                                AppLogger.w(
                                    TAG,
                                    secondaryFailure.format(),
                                )
                            }
                        ) {
                            carrier.eventChannel.collect { event ->
                                observedEvents += 1
                                when (event.eventType) {
                                    TextStreamEventType.SAVEPOINT -> {
                                        revisionMutex.withLock {
                                            revisionTracker.savepoint(event.id)
                                            observedVisibleChars =
                                                revisionTracker.currentContent().length
                                        }
                                    }

                                    TextStreamEventType.ROLLBACK -> {
                                        val rollbackResult =
                                            revisionMutex.withLock {
                                                revisionTracker.rollback(event.id)?.let { content ->
                                                    content.toString() to claimStreamingSnapshot()
                                                }
                                            } ?: return@collect
                                        val (snapshot, shouldPersist) = rollbackResult
                                        observedVisibleChars = snapshot.length

                                        state.aiMessage.content = snapshot

                                        if (shouldPersist) {
                                            persistStreamingSnapshot(state, snapshot)
                                        }
                                        if (!state.isWaifuModeEnabled) {
                                            tryEmitScrollToBottomThrottled(state.chatId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            try {
                state.sharedCharStream.collect { chunk ->
                    state.receivedChunkCount += 1
                    if (!hasLoggedFirstChunk) {
                        hasLoggedFirstChunk = true
                        if (state.firstResponseElapsed == null) {
                            state.firstResponseElapsed = messageTimingNow()
                            state.chatRuntime.firstResponseElapsed = state.firstResponseElapsed
                        }
                        logMessageTiming(
                            stage = "delegate.firstResponseChunk",
                            startTimeMs = state.responseStartTime,
                            details = "chatId=${state.activeChatId}, firstChunkLength=${chunk.length}"
                        )
                    }
                    val contentSnapshot =
                        revisionMutex.withLock {
                            val liveContent = revisionTracker.append(chunk)
                            // Claim the interval before materializing the mutable buffer;
                            // checking afterward recreates the original quadratic copying.
                            if (claimStreamingSnapshot()) liveContent.toString() else null
                        }
                    if (contentSnapshot != null) {
                        state.aiMessage.content = contentSnapshot
                        persistStreamingSnapshot(state, contentSnapshot)
                    }
                    if (!state.isWaifuModeEnabled) {
                        tryEmitScrollToBottomThrottled(state.chatId)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    revisionJob?.cancelAndJoin()
                    // 取消时也必须先发布最终修订快照，否则随后持久化的统计会绑定到过期正文。
                    state.aiMessage.content =
                        revisionMutex.withLock {
                            revisionTracker.currentContent().toString()
                        }
                }
            }

            autoReadJob?.join()
            waifuSegmentsJob?.join()

            if (getIsAutoReadEnabled() && !state.isWaifuModeEnabled) {
                val remaining = autoReadBuffer.toString()
                autoReadBuffer.clear()
                AppLogger.d(
                    TAG,
                    "autoRead[remaining] firstSegment=$isFirstAutoReadSegment rawLen=${remaining.length} trimmedLen=${remaining.trim().length}"
                )
                flushAutoReadSegment(remaining, interrupt = isFirstAutoReadSegment)
            }
        }
    }

    private suspend fun persistStreamingSnapshot(
        state: SendUserMessageTurnState,
        contentSnapshot: String,
    ) {
        if (state.effectivePersistTurn) {
            val textChange =
                resolveConversationAuditTextChange(
                    previous = state.lastAuditedContent,
                    current = contentSnapshot,
                )
            if (textChange != null) {
                conversationAuditRepository.appendEvent(
                    ConversationAuditEventRequest(
                        chatId = state.chatId,
                        category = "PROVIDER",
                        eventType = textChange.eventType,
                        actor = "PROVIDER",
                        summary =
                            if (textChange.isRevision) {
                                "Provider 修订了已接收的流式文本"
                            } else {
                                "收到 ${textChange.value.length} 个可见文本字符"
                            },
                        messageTimestamp = state.aiMessage.timestamp,
                        variantIndex = 0,
                        completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                        payloads =
                            listOf(
                                ConversationAuditPayloadInput.text(
                                    label = textChange.payloadLabel,
                                    role = "assistant",
                                    value = textChange.value,
                                )
                            ),
                    )
                )
                state.lastAuditedContent = contentSnapshot
            }
        }
        addMessageToChat(
            state.chatId,
            state.aiMessage.copy(
                content = AssistantReplayHistoryProjector.project(contentSnapshot).content
            )
        )
    }

    private suspend fun completeAssistantResponse(
        state: SendUserMessageTurnState,
    ) {
        val finalContent = resolveFinalContent(state.aiMessage)
        val stateAfterStream =
            _inputProcessingStateByChatId.value[chatKey(state.chatId)]
        AppLogger.d(
            TAG,
            "assistant completion invariant: chunks=${state.receivedChunkCount}, " +
                "visibleChars=${finalContent.length}, provider=${state.provider}, " +
                "model=${state.modelName}, " +
                "providerRequestContextPresent=${state.providerRequestContextPresent}, " +
                "lastInputState=${stateAfterStream?.javaClass?.simpleName ?: "none"}"
        )
        if (stateAfterStream is EnhancedInputProcessingState.Error) {
            // 服务状态已经明确失败时不能继续把共享流的正常关闭解释为成功；保留服务提供的真实错误。
            throw IllegalStateException(stateAfterStream.message)
        }
        val replaySafeFinalContent =
            AssistantReplayHistoryProjector.requireClosed(
                content = finalContent,
                boundary = "assistant_completion",
            )
        // Provider、共享流或服务层即使以正常完成返回，消息最终 owner 也只能在存在可交付内容时
        // 写入 Completed；否则目标设备会表现为加载结束但没有正文和错误。
        AssistantResponseCompletionPolicy.requireContent(
            content = replaySafeFinalContent,
            emptyResponseMessage =
                context.getString(
                    R.string.openai_error_stream_output_empty,
                    AssistantTurnDiagnostics.STREAM_EMPTY_TERMINATION,
                ),
        )
        state.aiMessage.content = replaySafeFinalContent

        runCatching {
            state.turnInputTokens = state.service.getCurrentInputTokenCount()
            state.turnOutputTokens = state.service.getCurrentOutputTokenCount()
            state.turnCachedInputTokens = state.service.getCurrentCachedInputTokenCount()
            state.turnProviderUsage = state.service.getCurrentProviderUsageAggregate()
        }.onFailure {
            AppLogger.w(TAG, "读取本轮 token 统计失败", it)
        }

        val waitDurationMs =
            if (state.requestStartElapsed > 0L && state.firstResponseElapsed != null) {
                (state.firstResponseElapsed!! - state.requestStartElapsed).coerceAtLeast(0L)
            } else {
                0L
            }
        val outputDurationMs =
            if (state.firstResponseElapsed != null) {
                (messageTimingNow() - state.firstResponseElapsed!!).coerceAtLeast(0L)
            } else {
                0L
            }

        if (state.requestSentAt > 0L) {
            if (state.userMessageAdded) {
                state.userMessage =
                    state.userMessage.withTurnMetrics(
                        inputTokens = state.turnInputTokens,
                        outputTokens = state.turnOutputTokens,
                        cachedInputTokens = state.turnCachedInputTokens,
                        sentAt = state.requestSentAt,
                        outputDurationMs = outputDurationMs,
                        waitDurationMs = waitDurationMs,
                    )
                addMessageToChat(state.chatId, state.userMessage)
            }

            state.aiMessage =
                state.aiMessage.withTurnMetrics(
                    inputTokens = state.turnInputTokens,
                    outputTokens = state.turnOutputTokens,
                    cachedInputTokens = state.turnCachedInputTokens,
                    sentAt = state.requestSentAt,
                    outputDurationMs = outputDurationMs,
                    waitDurationMs = waitDurationMs,
                ).withProviderUsageAggregate(state.turnProviderUsage)
        }
        state.aiMessage = state.aiMessage.copy(completedAt = System.currentTimeMillis())
        if (state.effectivePersistTurn) {
            conversationAuditRepository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = state.chatId,
                    category = "PROVIDER",
                    eventType = "PROVIDER_TERMINAL",
                    actor = "PROVIDER",
                    summary = "Provider 回合已正常完成",
                    messageTimestamp = state.aiMessage.timestamp,
                    variantIndex = 0,
                    terminalState = "COMPLETED",
                    completeness = ConversationAuditCompletenessStatus.COMPLETE,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "usage",
                                role = "metadata",
                                value =
                                    conversationAuditGson.toJson(
                                        mapOf(
                                            "inputTokens" to state.turnInputTokens,
                                            "outputTokens" to state.turnOutputTokens,
                                            "cachedInputTokens" to
                                                state.turnCachedInputTokens,
                                            "sentAt" to state.aiMessage.sentAt,
                                            "waitDurationMs" to
                                                state.aiMessage.waitDurationMs,
                                            "outputDurationMs" to
                                                state.aiMessage.outputDurationMs,
                                            "completedAt" to
                                                state.aiMessage.completedAt,
                                        )
                                    ),
                                mediaType = "application/json",
                            )
                        ),
                )
            )
            conversationAuditRepository.appendEvent(
                ConversationAuditEventRequest(
                    chatId = state.chatId,
                    category = "ASSISTANT",
                    eventType = "ASSISTANT_PROJECTION_UPDATED",
                    actor = "KIYORI",
                    summary = "最终 AI 可见回答已提交到聊天投影",
                    messageTimestamp = state.aiMessage.timestamp,
                    variantIndex = 0,
                    terminalState = "COMPLETED",
                    completeness = ConversationAuditCompletenessStatus.COMPLETE,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "assistant_output",
                                role = "assistant",
                                value = state.aiMessage.content,
                            )
                        ),
                )
            )
            conversationAuditRepository.seal(
                state.chatId,
                reason = "TURN_COMPLETED",
            )
        }

        if (state.isWaifuModeEnabled) {
            syncWaifuMessageMetrics(state, state.aiMessage)
        }

        state.shouldNotifyTurnComplete = true
        state.finalInputStateAfterSend = EnhancedInputProcessingState.Completed

        if (pendingAsyncSummaryUiByChatId.containsKey(state.chatId)) {
            setSuppressIdleCompletedStateForChat(state.chatId, true)
            state.finalInputStateAfterSend =
                EnhancedInputProcessingState.Summarizing(
                    context.getString(R.string.message_summarizing)
                )
        }

        logMessageTiming(
            stage = "delegate.responseProcessingComplete",
            startTimeMs = state.responseStartTime,
            details = "chatId=${state.activeChatId}, waifu=${state.isWaifuModeEnabled}, autoRead=${state.didStreamAutoRead}"
        )
    }

    private suspend fun handleAssistantTurnFailure(
        state: SendUserMessageTurnState,
        error: Exception,
    ) {
        val failureKind = AssistantTurnFailurePolicy.classify(error)
        val failureExecutionId = extractMessageFailureExecutionId(error)
        val causeMessage =
            error.message
                ?.takeIf { it.isNotBlank() }
                ?: error::class.java.simpleName
        val partialProjection =
            withContext(NonCancellable) {
                runCatching { persistFailedAssistantProjection(state) }
                    .onFailure {
                        AppLogger.e(TAG, "失败回合的部分 assistant 投影持久化失败", it)
                    }
                    .getOrNull()
            }
        if (partialProjection == null) {
            withContext(NonCancellable) {
                state.chatRuntime.activeStreamingTurn?.message?.contentStream = null
                if (state.hasAiMessage()) {
                    state.aiMessage.contentStream = null
                }
            }
        }
        val userMessage =
            if (partialProjection != null) {
                context.getString(R.string.message_send_failed_partial, causeMessage)
            } else {
                context.getString(R.string.message_send_failed, causeMessage)
            }
        val terminal = createTurnFailureTerminal(userMessage, failureKind)
        state.terminalOutcome = terminal.terminalOutcome
        state.finalInputStateAfterSend = terminal.finalInputState
        state.shouldNotifyTurnComplete = terminal.shouldNotifyTurnComplete
        state.terminalFailure = error
        val ownerClaim =
            claimPrimaryMessageFailureOwner(
                failure = error,
                phase = "message_owner",
            )
        val ownerMessage =
            "发送消息时出错: outcome=${terminal.terminalOutcome}, " +
                ownerClaim.diagnostics.format()
        if (ownerClaim.shouldLogCause) {
            AppLogger.e(TAG, ownerMessage, error)
        } else {
            AppLogger.e(TAG, ownerMessage)
        }
        // 清理前后都由消息最终 owner 持有 Error。原 Job 若已被上游取消，普通挂起提交可能不会
        // 执行，最终 UI 就会再次只看到 Idle，因此这里只提交终态和用户提示。
        withContext(NonCancellable) {
            if (state.effectivePersistTurn) {
                partialProjection?.let { partialMessage ->
                    runCatching {
                        conversationAuditRepository.appendEvent(
                            ConversationAuditEventRequest(
                                chatId = state.chatId,
                                category = "ASSISTANT",
                                eventType = "ASSISTANT_PROJECTION_UPDATED",
                                actor = "KIYORI",
                                summary = "传输中断，已保留已收到的部分 AI 回答",
                                messageTimestamp = partialMessage.timestamp,
                                variantIndex = partialMessage.selectedVariantIndex,
                                localExecutionId = failureExecutionId,
                                terminalState = "FAILED",
                                completeness = ConversationAuditCompletenessStatus.PARTIAL,
                                failureCode = "ASSISTANT_PARTIAL_PROJECTION",
                                payloads =
                                    listOf(
                                        ConversationAuditPayloadInput.text(
                                            label = "assistant_partial_output",
                                            role = "assistant",
                                            value = partialMessage.content,
                                        )
                                    ),
                            )
                        )
                    }.onFailure { auditError ->
                        AppLogger.e(TAG, "失败回合的部分 assistant 投影审计写入失败", auditError)
                    }
                }
                runCatching {
                    conversationAuditRepository.appendThrowable(
                        chatId = state.chatId,
                        eventType = "PROVIDER_TERMINAL_ERROR",
                        summary = "Provider 回合失败：${failureKind.name}",
                        throwable = error,
                        messageTimestamp =
                            state.takeIf { it.hasAiMessage() }
                                ?.aiMessage
                                ?.timestamp
                                ?.takeIf { it > 0L },
                        variantIndex = 0,
                        localExecutionId = failureExecutionId,
                        completeness = ConversationAuditCompletenessStatus.PARTIAL,
                    )
                    conversationAuditRepository.seal(
                        state.chatId,
                        reason = "TURN_FAILED",
                    )
                }.onFailure { auditError ->
                    AppLogger.e(TAG, "失败回合的审计终态写入失败", auditError)
                }
            }
            setChatInputProcessingState(state.chatId, terminal.finalInputState)
            withContext(Dispatchers.Main) {
                showErrorMessage(userMessage)
            }
        }
    }

    private suspend fun ensureAssistantTurnTerminalOutcome(
        state: SendUserMessageTurnState,
    ) {
        if (state.terminalOutcome != "unknown") return

        val message =
            context.getString(
                R.string.message_send_terminal_missing,
                AssistantTurnDiagnostics.TURN_TERMINAL_MISSING,
            )
        val terminal =
            createTurnFailureTerminal(
                message = message,
                failureKind = AssistantTurnFailureKind.TERMINAL_MISSING,
            )
        state.terminalOutcome = terminal.terminalOutcome
        state.finalInputStateAfterSend = terminal.finalInputState
        state.shouldNotifyTurnComplete = terminal.shouldNotifyTurnComplete
        AppLogger.e(
            TAG,
            "turn terminal invariant failed: " +
                "diagnosticCode=${AssistantTurnDiagnostics.TURN_TERMINAL_MISSING}, " +
                "chunks=${state.receivedChunkCount}, visibleChars=${state.visibleContentLength()}, " +
                "provider=${state.provider}, model=${state.modelName}"
        )
        withContext(NonCancellable) {
            setChatInputProcessingState(state.chatId, terminal.finalInputState)
            withContext(Dispatchers.Main) {
                showErrorMessage(message)
            }
        }
    }

    suspend fun regenerateAiMessageVariant(
        chatId: String,
        targetMessageTimestamp: Long,
        targetVariantIndex: Int,
        requestMessageContent: String,
        requestHistory: List<ChatMessage>,
        workspacePath: String?,
        promptFunctionType: PromptFunctionType,
        roleCardId: String,
        currentRoleName: String,
        enableThinking: Boolean,
        enableMemoryAutoUpdate: Boolean,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?,
        memorySpaceIdOverride: String?,
        groupOrchestrationMode: Boolean,
        groupParticipantNamesText: String?,
        onVariantPreviewStarted: suspend (ChatMessage) -> Unit,
        onVariantReady: suspend (ChatMessage) -> Unit,
    ) {
        val chatRuntime = runtimeFor(chatId)
        if (chatRuntime.isLoading.value) {
            throw IllegalStateException(context.getString(R.string.chat_regenerate_busy))
        }
        val turnId = chatRuntime.turnSequence.incrementAndGet()
        chatRuntime.activeTurnId = turnId

        val currentJob = coroutineContext[Job] ?: throw IllegalStateException("Missing coroutine job")
        var serviceForTerminalCleanup: EnhancedAIService? = null
        var shouldResetInputStateToIdle = false
        chatRuntime.sendJob = currentJob
        resetCurrentTurnToolInvocationCount(chatId)
        chatRuntime.isLoading.value = true
        updateGlobalLoadingState()
        setChatInputProcessingState(
            chatId,
            EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)),
        )
        var terminalState: EnhancedInputProcessingState? = null
        var exceptionToPropagate: Exception? = null

        try {
            val service = EnhancedAIService.getChatInstance(context, chatId)
            serviceForTerminalCleanup = service
            service.setInputProcessingState(
                EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing))
            )

            chatRuntime.stateCollectionJob?.cancel()
            chatRuntime.stateCollectionJob =
                coroutineScope.launch {
                    var lastErrorMessage: String? = null
                    service.inputProcessingState.collect { state ->
                        setChatInputProcessingState(chatId, state)

                        if (state is EnhancedInputProcessingState.Error) {
                            val msg = state.message
                            if (msg != lastErrorMessage) {
                                lastErrorMessage = msg
                                withContext(Dispatchers.Main) {
                                    showErrorMessage(msg)
                                }
                            }
                        } else {
                            lastErrorMessage = null
                        }
                    }
                }

            val (provider, modelName) =
                service.getDisplayProviderAndModelForFunction(
                    functionType = FunctionType.CHAT,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                )

            var firstResponseElapsed: Long? = null
            val requestSentAt = System.currentTimeMillis()
            val requestStartElapsed = messageTimingNow()
            val effectiveRequestMessageContent =
                if (groupOrchestrationMode &&
                    requestMessageContent.trimStart().isNotEmpty() &&
                    !requestMessageContent.trimStart().startsWith("[From user]")
                ) {
                    "[From user]\n$requestMessageContent"
                } else {
                    requestMessageContent
                }

            val responseStream =
                AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    chatId = chatId,
                    messageContent = effectiveRequestMessageContent,
                    chatHistory = requestHistory,
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    onNonFatalError = { error -> _nonFatalErrorEvent.emit(error) },
                    characterName = currentRoleName,
                    roleCardId = roleCardId,
                    currentRoleName = currentRoleName,
                    splitHistoryByRole = true,
                    groupOrchestrationMode = groupOrchestrationMode,
                    groupParticipantNamesText = groupParticipantNamesText,
                    onToolInvocation = { incrementCurrentTurnToolInvocationCount(chatId) },
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                    providerRequestContext =
                        ProviderRequestContext.create(
                            chatId = chatId,
                            messageTimestamp = targetMessageTimestamp,
                            variantIndex = targetVariantIndex,
                        ),
                    operationId = turnId,
                )

            val sharedResponseStream = responseStream
            chatRuntime.responseStream = sharedResponseStream

            val aiMessage =
                ChatMessage(
                    sender = "ai",
                    contentStream = sharedResponseStream,
                    timestamp = targetMessageTimestamp,
                    roleName = currentRoleName,
                    provider = provider,
                    modelName = modelName,
                    sentAt = requestSentAt,
                )
            onVariantPreviewStarted(aiMessage)

            coroutineScope {
                val revisableStream = sharedResponseStream as? TextStreamEventCarrier
                val revisionTracker = TextStreamRevisionTracker()
                val revisionMutex = Mutex()

                val revisionJob =
                    revisableStream?.let { carrier ->
                        launch {
                            carrier.eventChannel.collect { event ->
                                when (event.eventType) {
                                    TextStreamEventType.SAVEPOINT -> {
                                        revisionMutex.withLock {
                                            revisionTracker.savepoint(event.id)
                                        }
                                    }

                                    TextStreamEventType.ROLLBACK -> {
                                        val snapshot =
                                            revisionMutex.withLock {
                                                revisionTracker.rollback(event.id)?.toString()
                                            } ?: return@collect
                                        aiMessage.content = snapshot
                                    }
                                }
                            }
                        }
                    }

                sharedResponseStream.collect { chunk ->
                    if (firstResponseElapsed == null) {
                        firstResponseElapsed = messageTimingNow()
                    }
                    revisionMutex.withLock {
                        revisionTracker.append(chunk)
                    }
                }

                revisionJob?.cancelAndJoin()
                aiMessage.content =
                    revisionMutex.withLock {
                        revisionTracker.currentContent().toString()
                    }
            }

            val finalContent =
                AssistantReplayHistoryProjector.requireClosed(
                    content = resolveFinalContent(aiMessage),
                    boundary = "regeneration_completion",
                )
            var turnInputTokens = 0
            var turnOutputTokens = 0
            var turnCachedInputTokens = 0
            var turnProviderUsage = ProviderUsageAggregate()
            runCatching {
                turnInputTokens = service.getCurrentInputTokenCount()
                turnOutputTokens = service.getCurrentOutputTokenCount()
                turnCachedInputTokens = service.getCurrentCachedInputTokenCount()
                turnProviderUsage = service.getCurrentProviderUsageAggregate()
            }.onFailure {
                AppLogger.w(TAG, "读取重新生成 token 统计失败", it)
            }

            val waitDurationMs =
                if (firstResponseElapsed != null) {
                    (firstResponseElapsed - requestStartElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            val outputDurationMs =
                if (firstResponseElapsed != null) {
                    (messageTimingNow() - firstResponseElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }

            val completedAt = System.currentTimeMillis()
            onVariantReady(
                aiMessage.withTurnMetrics(
                    inputTokens = turnInputTokens,
                    outputTokens = turnOutputTokens,
                    cachedInputTokens = turnCachedInputTokens,
                    sentAt = requestSentAt,
                    outputDurationMs = outputDurationMs,
                    waitDurationMs = waitDurationMs,
                ).withProviderUsageAggregate(turnProviderUsage).copy(
                    content = finalContent,
                    contentStream = null,
                    completedAt = completedAt,
                )
            )
            terminalState = EnhancedInputProcessingState.Completed
            shouldResetInputStateToIdle = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                terminalState =
                    resolveAssistantTurnCancellation(
                        chatId = chatId,
                        turnId = turnId,
                        error = e,
                    ).terminalState
            } else {
                AppLogger.e(TAG, "单条重新生成失败", e)
                setChatInputProcessingState(
                    chatId,
                    EnhancedInputProcessingState.Error(
                        context.getString(R.string.chat_regenerate_single_failed, e.message ?: "")
                    ),
                )
            }
            exceptionToPropagate = e
        } finally {
            if (chatRuntime.activeTurnId == turnId) {
                clearCurrentTurnToolInvocationCount(chatId)
                if (chatRuntime.sendJob === currentJob) {
                    chatRuntime.sendJob = null
                }
                chatRuntime.stateCollectionJob?.cancel()
                chatRuntime.stateCollectionJob = null
                chatRuntime.responseStream = null
                chatRuntime.activeStreamingTurn = null
                if (!chatRuntime.cancellationInProgress) {
                    chatRuntime.isLoading.value = false
                    updateGlobalLoadingState()
                }
                terminalState?.let { state ->
                    setChatInputProcessingState(chatId, state)
                }
                if (shouldResetInputStateToIdle) {
                    serviceForTerminalCleanup?.setInputProcessingState(EnhancedInputProcessingState.Idle)
                    setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
                }
            }
        }
        exceptionToPropagate?.let { throw it }
    }

    private data class AssistantTurnCancellationHandling(
        val source: AssistantTurnCancellationSource,
        val terminalState: EnhancedInputProcessingState,
    )

    private suspend fun resolveAssistantTurnCancellation(
        chatId: String,
        turnId: Long,
        error: kotlinx.coroutines.CancellationException,
    ): AssistantTurnCancellationHandling {
        val source = AIMessageManager.consumeCancellationSource(chatId, turnId)
        val decision = AssistantTurnCancellationPolicy.resolve(source)
        if (decision.terminal == AssistantTurnCancellationTerminal.IDLE) {
            AppLogger.d(
                TAG,
                "消息发送按预期取消: source=${decision.source}, turnId=$turnId"
            )
            return AssistantTurnCancellationHandling(
                source = decision.source,
                terminalState = EnhancedInputProcessingState.Idle,
            )
        }

        val message = context.getString(R.string.message_send_interrupted_unexpected)
        val errorState = EnhancedInputProcessingState.Error(message)
        AppLogger.e(
            TAG,
            "消息发送被非预期取消: source=${decision.source}, turnId=$turnId",
            error,
        )
        // 原发送 Job 已处于 cancelled 状态；这里只在 NonCancellable 中提交必要的终态和提示，
        // 不能重新执行请求、工具或其他业务逻辑。
        withContext(NonCancellable) {
            setChatInputProcessingState(chatId, errorState)
            withContext(Dispatchers.Main) {
                showErrorMessage(message)
            }
        }
        return AssistantTurnCancellationHandling(
            source = decision.source,
            terminalState = errorState,
        )
    }

    private suspend fun notifyTurnComplete(
        chatId: String?,
        activeChatId: String?,
        service: EnhancedAIService,
        calculateNextWindowSize: (suspend () -> Int?)? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions()
    ) {
        if (!chatId.isNullOrBlank()) {
            val updated = _turnCompleteCounterByChatId.value.toMutableMap()
            updated[chatId] = (updated[chatId] ?: 0L) + 1L
            _turnCompleteCounterByChatId.value = updated
        }
        val nextWindowSize = calculateNextWindowSize?.invoke()
        AppLogger.d(
            TAG,
            "回合完成: chatId=$activeChatId, nextWindow=$nextWindowSize, service=${service.javaClass.simpleName}"
        )
        onTurnComplete(activeChatId, service, nextWindowSize, turnOptions)
    }

    private suspend fun finalizeMessageAndNotify(
        chatId: String?,
        activeChatId: String?,
        aiMessageProvider: () -> ChatMessage,
        isWaifuModeEnabled: Boolean,
        skipFinalAutoRead: Boolean,
        syncWaifuMessageMetrics: suspend (ChatMessage) -> Unit,
        calculateNextWindowSize: (suspend () -> Int?)? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions()
    ): Boolean {
        try {
            val aiMessage = aiMessageProvider()
            // 优先使用共享流的全量重放缓存重建最终文本，避免完成信号早于收集协程处理尾部字符时丢字。
            val finalContent = resolveFinalContent(aiMessage)
            val replaySafeContent =
                AssistantReplayHistoryProjector.project(finalContent).content
            aiMessage.content = replaySafeContent
            val completedAt = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                if (isWaifuModeEnabled) {
                    syncWaifuMessageMetrics(aiMessage.copy(completedAt = completedAt))
                    forceEmitScrollToBottom(chatId)
                } else {
                    // 普通模式，直接清理流
                    val finalMessage =
                        aiMessage.copy(
                            content = replaySafeContent,
                            contentStream = null,
                            completedAt = completedAt,
                        )
                    withContext(Dispatchers.Main) {
                        if (turnOptions.persistTurn && chatId != null) {
                            addMessageToChat(chatId, finalMessage)
                        }
                        AppLogger.d(
                            TAG,
                            "autoRead[final] enabled=${getIsAutoReadEnabled()} skipFinalAutoRead=$skipFinalAutoRead len=${replaySafeContent.length}"
                        )
                        // 如果启用了自动朗读，则朗读完整消息
                        if (getIsAutoReadEnabled() && !skipFinalAutoRead) {
                            speakMessageHandler(replaySafeContent, true)
                        }
                        forceEmitScrollToBottom(chatId)
                    }
                }
            }
        } catch (e: UninitializedPropertyAccessException) {
            AppLogger.d(TAG, "AI消息未初始化，跳过流清理步骤")
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLogger.d(TAG, "消息收尾阶段被取消，跳过waifu收尾处理")
            throw e
        } catch (e: Exception) {
            // 收尾失败时不能再次保存未经投影的正文，否则下一轮会重新暴露未闭合工具事务。
            AppLogger.e(TAG, "消息收尾失败，未写入不安全的部分消息", e)
        }
        return false
    }

    private fun cleanupRuntimeAfterSend(
        chatId: String,
        chatRuntime: ChatRuntime,
        turnId: Long,
    ) {
        if (chatRuntime.activeTurnId != turnId) return
        chatRuntime.streamCollectionJob = null
        chatRuntime.stateCollectionJob?.cancel()
        chatRuntime.stateCollectionJob = null
        chatRuntime.responseStream = null
        chatRuntime.activeStreamingTurn = null
        chatRuntime.currentTurnOptions = ChatTurnOptions()
        chatRuntime.requestSentAt = 0L
        chatRuntime.requestStartElapsed = 0L
        chatRuntime.firstResponseElapsed = null

        if (!chatRuntime.cancellationInProgress) {
            chatRuntime.isLoading.value = false
            updateGlobalLoadingState()
        }
        clearCurrentTurnToolInvocationCount(chatId)
    }

    /**
     * 刷新聚合后的加载状态。
     * 仅重新计算全局/按会话的加载派生值，不会直接改写具体 chat 的 isLoading。
     */
    fun refreshGlobalLoadingState() {
        updateGlobalLoadingState()
    }
}
