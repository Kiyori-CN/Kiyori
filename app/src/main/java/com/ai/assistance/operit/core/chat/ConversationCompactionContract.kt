package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolCallDescriptor
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolHistoryState
import com.ai.assistance.operit.api.chat.llmprovider.ProviderToolResultDescriptor
import com.ai.assistance.operit.data.audit.ConversationAuditHasher
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils

data class ConversationCompactionRouteIdentity(
    val configId: String,
    val modelIndex: Int,
    val providerTypeId: String,
    val protocol: ApiProtocol,
    val modelName: String,
) {
    init {
        require(configId.isNotBlank()) { "configId must not be blank" }
        require(modelIndex >= 0) { "modelIndex must not be negative" }
        require(providerTypeId.isNotBlank()) { "providerTypeId must not be blank" }
        require(modelName.isNotBlank()) { "modelName must not be blank" }
    }
}

data class ConversationCompactionSnapshot(
    val chatId: String,
    val rangeStartTimestamp: Long,
    val rangeEndTimestamp: Long,
    val beforeAnchorTimestamp: Long,
    val afterAnchorTimestamp: Long?,
    val rangeDigest: String,
    val routeIdentity: ConversationCompactionRouteIdentity,
    val sourceMessageCount: Int,
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(rangeStartTimestamp > 0L) { "rangeStartTimestamp must be positive" }
        require(rangeEndTimestamp >= rangeStartTimestamp) {
            "rangeEndTimestamp must not precede rangeStartTimestamp"
        }
        require(beforeAnchorTimestamp == rangeEndTimestamp) {
            "beforeAnchorTimestamp must equal rangeEndTimestamp"
        }
        require(afterAnchorTimestamp == null || afterAnchorTimestamp > beforeAnchorTimestamp) {
            "afterAnchorTimestamp must follow beforeAnchorTimestamp"
        }
        require(rangeDigest.length == 64) { "rangeDigest must be a SHA-256 value" }
        require(sourceMessageCount > 0) { "sourceMessageCount must be positive" }
    }
}

data class ConversationCompactionPlan(
    val snapshot: ConversationCompactionSnapshot,
    val sourceMessages: List<ChatMessage>,
    val tailMessages: List<ChatMessage>,
)

data class ConversationCompactionCommitResult(
    val decision: ConversationCompactionCommitDecision,
    val persistedSummaryMessage: ChatMessage? = null,
)

data class ConversationCompactionUsage(
    val providerModel: String,
    val inputTokens: Int,
    val cacheReadTokens: Int,
    val outputTokens: Int,
) {
    init {
        require(providerModel.isNotBlank()) { "providerModel must not be blank" }
        require(inputTokens >= 0) { "inputTokens must not be negative" }
        require(cacheReadTokens >= 0) { "cacheReadTokens must not be negative" }
        require(outputTokens >= 0) { "outputTokens must not be negative" }
        require(cacheReadTokens <= inputTokens) {
            "cacheReadTokens must not exceed inputTokens"
        }
    }
}

data class GeneratedConversationSummaryContent(
    val content: String,
    val usage: ConversationCompactionUsage,
) {
    init {
        require(content.isNotBlank()) { "Generated summary content must not be blank" }
    }
}

data class GeneratedConversationSummary(
    val message: ChatMessage,
    val usage: ConversationCompactionUsage,
    val pruningReport: ConversationToolResultPruningReport =
        ConversationToolResultPruningReport(),
) {
    init {
        require(message.sender == "summary") { "Generated summary message must use sender=summary" }
    }
}

enum class ConversationCompactionRejection {
    EMPTY_HISTORY,
    MESSAGE_ORDER_INVALID,
    NO_COMPLETED_ASSISTANT_TURN,
    NO_COMPLETE_USER_ASSISTANT_TURN,
    TOOL_HISTORY_INVALID,
    CHAT_CHANGED,
    RANGE_CHANGED,
    ROUTE_CHANGED,
    BOUNDARY_INVALID,
    ALREADY_REPLACED,
}

sealed interface ConversationCompactionPlanResult {
    data class Ready(val plan: ConversationCompactionPlan) : ConversationCompactionPlanResult

    data class Rejected(
        val reason: ConversationCompactionRejection,
        val detail: String,
    ) : ConversationCompactionPlanResult
}

sealed interface ConversationCompactionCommitDecision {
    data class Allow(val afterAnchorTimestamp: Long?) : ConversationCompactionCommitDecision

    data class Reject(
        val reason: ConversationCompactionRejection,
        val detail: String,
    ) : ConversationCompactionCommitDecision
}

enum class ConversationSummaryCheckpointRejection {
    EMPTY,
    TOOL_MARKUP_PRESENT,
    PRIVATE_METADATA_PRESENT,
}

sealed interface ConversationSummaryCheckpointResult {
    data class Valid(val content: String) : ConversationSummaryCheckpointResult

    data class Rejected(
        val reason: ConversationSummaryCheckpointRejection,
        val detail: String,
    ) : ConversationSummaryCheckpointResult
}

class ConversationSummaryGenerationException(
    val reason: ConversationSummaryCheckpointRejection,
    detail: String,
) : IllegalStateException("Conversation summary generation failed [$reason]: $detail")

/**
 * 长上下文压缩的纯合同。
 *
 * 它只选择并校验将被 summary 投影替换的历史范围，不持有数据库或协程状态。真实持久化仍由
 * ChatHistoryManager 负责；调用方必须在写入 summary 的同一临界区内重新加载范围并再次调用
 * [validateCommit]，否则异步生成期间的编辑、变体切换或路由变化可能提交过期摘要。
 */
object ConversationCompactionContract {
    fun plan(
        chatId: String,
        messages: List<ChatMessage>,
        routeIdentity: ConversationCompactionRouteIdentity,
        afterAnchorTimestampOverride: Long? = null,
    ): ConversationCompactionPlanResult {
        if (messages.isEmpty()) {
            return rejected(
                ConversationCompactionRejection.EMPTY_HISTORY,
                "Runtime history is empty",
            )
        }
        val orderError = validateMessageOrder(messages)
        if (orderError != null) {
            return rejected(ConversationCompactionRejection.MESSAGE_ORDER_INVALID, orderError)
        }
        if (messages.drop(1).any { it.sender == "summary" }) {
            return rejected(
                ConversationCompactionRejection.ALREADY_REPLACED,
                "Runtime history contains a summary inside the candidate window",
            )
        }

        validateReplayHistory(messages)?.let { detail ->
            return rejected(ConversationCompactionRejection.TOOL_HISTORY_INVALID, detail)
        }

        val boundaryIndex = messages.indexOfLast { it.sender == "ai" }
        if (boundaryIndex < 0) {
            return rejected(
                ConversationCompactionRejection.NO_COMPLETED_ASSISTANT_TURN,
                "No completed AI message is available as a compaction boundary",
            )
        }

        val sourceMessages = messages.subList(0, boundaryIndex + 1).toList()
        val messagesAfterPreviousSummary =
            sourceMessages.dropWhile { it.sender == "summary" }
        if (
            messagesAfterPreviousSummary.none { it.sender == "user" } ||
                messagesAfterPreviousSummary.none { it.sender == "ai" }
        ) {
            return rejected(
                ConversationCompactionRejection.NO_COMPLETE_USER_ASSISTANT_TURN,
                "The candidate range does not contain a complete user-to-AI turn",
            )
        }
        validateReplayHistory(sourceMessages)?.let { detail ->
            return rejected(ConversationCompactionRejection.BOUNDARY_INVALID, detail)
        }

        val tailMessages = messages.drop(boundaryIndex + 1)
        val rangeStartTimestamp = sourceMessages.first().timestamp
        val rangeEndTimestamp = sourceMessages.last().timestamp
        val afterAnchorTimestamp =
            afterAnchorTimestampOverride ?: tailMessages.firstOrNull()?.timestamp
        if (afterAnchorTimestamp != null && afterAnchorTimestamp <= rangeEndTimestamp) {
            return rejected(
                ConversationCompactionRejection.BOUNDARY_INVALID,
                "The after-anchor must follow the replacement range",
            )
        }
        return ConversationCompactionPlanResult.Ready(
            ConversationCompactionPlan(
                snapshot =
                    ConversationCompactionSnapshot(
                        chatId = chatId,
                        rangeStartTimestamp = rangeStartTimestamp,
                        rangeEndTimestamp = rangeEndTimestamp,
                        beforeAnchorTimestamp = rangeEndTimestamp,
                        afterAnchorTimestamp = afterAnchorTimestamp,
                        rangeDigest = digest(sourceMessages),
                        routeIdentity = routeIdentity,
                        sourceMessageCount = sourceMessages.size,
                    ),
                sourceMessages = sourceMessages,
                tailMessages = tailMessages,
            )
        )
    }

    fun validateCommit(
        snapshot: ConversationCompactionSnapshot,
        chatId: String,
        currentRangeMessages: List<ChatMessage>,
        currentAfterAnchor: ChatMessage?,
        currentRouteIdentity: ConversationCompactionRouteIdentity,
    ): ConversationCompactionCommitDecision {
        if (chatId != snapshot.chatId) {
            return commitRejected(
                ConversationCompactionRejection.CHAT_CHANGED,
                "Compaction chat changed from ${snapshot.chatId} to $chatId",
            )
        }
        if (currentRouteIdentity != snapshot.routeIdentity) {
            return commitRejected(
                ConversationCompactionRejection.ROUTE_CHANGED,
                "The main chat provider route changed while the summary was generated",
            )
        }
        if (
            currentRangeMessages.size != snapshot.sourceMessageCount ||
                currentRangeMessages.firstOrNull()?.timestamp != snapshot.rangeStartTimestamp ||
                currentRangeMessages.lastOrNull()?.timestamp != snapshot.rangeEndTimestamp ||
                digest(currentRangeMessages) != snapshot.rangeDigest
        ) {
            return commitRejected(
                ConversationCompactionRejection.RANGE_CHANGED,
                "The compaction source range changed while the summary was generated",
            )
        }
        validateMessageOrder(currentRangeMessages)?.let { detail ->
            return commitRejected(ConversationCompactionRejection.BOUNDARY_INVALID, detail)
        }
        validateReplayHistory(currentRangeMessages)?.let { detail ->
            return commitRejected(ConversationCompactionRejection.BOUNDARY_INVALID, detail)
        }
        if (currentRangeMessages.lastOrNull()?.sender != "ai") {
            return commitRejected(
                ConversationCompactionRejection.BOUNDARY_INVALID,
                "The compaction boundary no longer ends at a completed AI message",
            )
        }
        if (
            snapshot.afterAnchorTimestamp != null &&
                currentAfterAnchor?.timestamp != snapshot.afterAnchorTimestamp
        ) {
            return commitRejected(
                ConversationCompactionRejection.RANGE_CHANGED,
                "The message immediately after the compaction range changed",
            )
        }
        if (currentAfterAnchor?.sender == "summary") {
            return commitRejected(
                ConversationCompactionRejection.ALREADY_REPLACED,
                "A summary already exists next to the compaction range",
            )
        }
        return ConversationCompactionCommitDecision.Allow(
            afterAnchorTimestamp = currentAfterAnchor?.timestamp
        )
    }

    fun sanitizeSummaryCheckpoint(content: String): ConversationSummaryCheckpointResult {
        val withoutReplayMetadata =
            ChatUtils.stripProviderReplayMetadata(
                content = content,
                retainedKinds = emptySet(),
            )
        val sanitized =
            ChatUtils.removeThinkingContent(withoutReplayMetadata)
                .trim()
        if (sanitized.isBlank()) {
            return ConversationSummaryCheckpointResult.Rejected(
                reason = ConversationSummaryCheckpointRejection.EMPTY,
                detail = "Summary checkpoint is empty after private content is removed",
            )
        }
        if (ChatMarkupRegex.containsAnyToolLikeTag(sanitized)) {
            return ConversationSummaryCheckpointResult.Rejected(
                reason = ConversationSummaryCheckpointRejection.TOOL_MARKUP_PRESENT,
                detail = "Summary checkpoint contains tool call or tool result markup",
            )
        }
        if (ChatMarkupRegex.metaTag.containsMatchIn(sanitized)) {
            return ConversationSummaryCheckpointResult.Rejected(
                reason = ConversationSummaryCheckpointRejection.PRIVATE_METADATA_PRESENT,
                detail = "Summary checkpoint contains private metadata markup",
            )
        }
        return ConversationSummaryCheckpointResult.Valid(sanitized)
    }

    fun requireSafeSummaryCheckpoint(content: String): String {
        return when (val result = sanitizeSummaryCheckpoint(content)) {
            is ConversationSummaryCheckpointResult.Valid -> result.content
            is ConversationSummaryCheckpointResult.Rejected ->
                throw ConversationSummaryGenerationException(
                    reason = result.reason,
                    detail = result.detail,
                )
        }
    }

    fun digest(messages: List<ChatMessage>): String {
        val canonical =
            buildString {
                messages.forEachIndexed { index, message ->
                    appendField(index.toString())
                    appendField(message.timestamp.toString())
                    appendField(message.sender)
                    appendField(message.content)
                    appendField(message.roleName)
                    appendField(message.selectedVariantIndex.toString())
                    appendField(message.variantCount.toString())
                    appendField(message.provider)
                    appendField(message.modelName)
                    appendField(message.displayMode.name)
                }
            }
        return ConversationAuditHasher.sha256(canonical)
    }

    private fun validateMessageOrder(messages: List<ChatMessage>): String? {
        messages.zipWithNext().forEach { (previous, current) ->
            if (previous.timestamp >= current.timestamp) {
                return "Message timestamps must be strictly increasing"
            }
        }
        return null
    }

    private fun validateReplayHistory(messages: List<ChatMessage>): String? {
        return runCatching {
            val state = ProviderToolHistoryState()
            messages.forEachIndexed { messageIndex, message ->
                val blocks = ChatMarkupRegex.toolOrToolResultBlock.findAll(message.content).toList()
                if (blocks.isEmpty()) {
                    if (
                        ChatMarkupRegex.containsAnyToolLikeTag(message.content) ||
                            message.sender == "summary" ||
                            (message.sender == "user" && message.content.isNotBlank()) ||
                            (message.sender == "ai" && message.content.isNotBlank())
                    ) {
                        state.requireClosed("message_${messageIndex}_${message.sender}")
                    }
                    if (ChatMarkupRegex.containsAnyToolLikeTag(message.content)) {
                        error("Tool markup is incomplete at message $messageIndex")
                    }
                    return@forEachIndexed
                }

                val pendingCalls = mutableListOf<ProviderToolCallDescriptor>()

                fun flushToolCalls() {
                    if (pendingCalls.isEmpty()) {
                        return
                    }
                    state.acceptToolCallDescriptors(
                        calls = pendingCalls.toList(),
                        boundary = "message_${messageIndex}_tool_calls",
                    )
                    pendingCalls.clear()
                }

                blocks.forEachIndexed { blockIndex, match ->
                    val normalizedTagName =
                        ChatMarkupRegex.normalizeToolLikeTagName(
                            ChatMarkupRegex.extractOpeningTagName(match.value)
                        )
                    when (normalizedTagName) {
                        "tool" -> {
                            val toolName =
                                ChatMarkupRegex.nameAttr
                                    .find(match.value)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: error("Tool call has no name at message $messageIndex")
                            val providerCallId =
                                PROVIDER_CALL_ID_ATTRIBUTE
                                    .find(match.value.substringBefore('>'))
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.trim()
                                    .orEmpty()
                            val stableCallId =
                                providerCallId.ifBlank {
                                    "compaction_${message.timestamp}_${blockIndex}_" +
                                        ConversationAuditHasher.sha256(match.value).take(12)
                                }
                            pendingCalls.add(
                                ProviderToolCallDescriptor(
                                    callId = stableCallId,
                                    toolName = toolName,
                                )
                            )
                        }
                        "tool_result" -> {
                            flushToolCalls()
                            val resultName =
                                ChatMarkupRegex.nameAttr
                                    .find(match.value)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.trim()
                            val resultCallId =
                                PROVIDER_CALL_ID_ATTRIBUTE
                                    .find(match.value.substringBefore('>'))
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.trim()
                            state.acceptNamedToolResults(
                                results =
                                    listOf(
                                        ProviderToolResultDescriptor(
                                            callId = resultCallId,
                                            toolName = resultName,
                                        )
                                    ),
                                boundary = "message_${messageIndex}_tool_result_$blockIndex",
                            )
                        }
                        else -> error("Unknown tool markup at message $messageIndex")
                    }
                }
                flushToolCalls()

                val unmatchedContent =
                    ChatMarkupRegex.toolOrToolResultBlock.replace(message.content, " ")
                if (ChatMarkupRegex.containsAnyToolLikeTag(unmatchedContent)) {
                    error("Tool markup is incomplete at message $messageIndex")
                }
                if (
                    message.sender == "summary" ||
                        (message.sender == "user" && unmatchedContent.isNotBlank())
                ) {
                    state.requireClosed("message_${messageIndex}_${message.sender}_text")
                }
            }
            state.requireClosed("history_end")
        }.exceptionOrNull()?.message ?: return null
    }

    private fun rejected(
        reason: ConversationCompactionRejection,
        detail: String,
    ): ConversationCompactionPlanResult.Rejected =
        ConversationCompactionPlanResult.Rejected(reason = reason, detail = detail)

    private fun commitRejected(
        reason: ConversationCompactionRejection,
        detail: String,
    ): ConversationCompactionCommitDecision.Reject =
        ConversationCompactionCommitDecision.Reject(reason = reason, detail = detail)

    private fun StringBuilder.appendField(value: String) {
        append(value.toByteArray(Charsets.UTF_8).size)
        append(':')
        append(value)
        append('\n')
    }

    private val PROVIDER_CALL_ID_ATTRIBUTE =
        Regex("""\bprovider_call_id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
}
