package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils

enum class AssistantReplayHistoryProjectionReason {
    INCOMPLETE_TOOL_MARKUP,
    TOOL_CALL_WITHOUT_NAME,
    TOOL_RESULT_WITHOUT_NAME,
    TOOL_RESULT_WITHOUT_CALL,
    TOOL_CALL_BEFORE_RESULTS_COMPLETE,
    TOOL_TRANSACTION_TEXT_BOUNDARY,
    TOOL_RESULT_NAME_MISMATCH,
    TOOL_RESULT_CALL_ID_MISMATCH,
    MISSING_TOOL_RESULT,
}

/** Provider replay classification for a durable assistant projection. */
enum class AssistantReplayEligibility {
    NONE,
    LOCAL_ONLY_REASONING,
    REPLAYABLE,
}

data class AssistantReplayHistoryProjection(
    val content: String,
    val originalLength: Int,
    val truncationIndex: Int? = null,
    val reason: AssistantReplayHistoryProjectionReason? = null,
    val pendingToolCallCount: Int = 0,
    val pendingToolCalls: List<AssistantReplayPendingToolCall> = emptyList(),
    val reorderedTransactionCount: Int = 0,
    val crossMessageClosureEligible: Boolean = false,
) {
    val changed: Boolean
        get() = content.length != originalLength || reorderedTransactionCount > 0

    val truncated: Boolean
        get() = truncationIndex != null

    val removedCharacterCount: Int
        get() = (originalLength - content.length).coerceAtLeast(0)
}

class AssistantReplayHistoryProtocolException(
    val projection: AssistantReplayHistoryProjection,
    boundary: String,
) : IllegalStateException(
    "Assistant replay history is not closed at $boundary: " +
        "reason=${projection.reason}, pending=${projection.pendingToolCallCount}, " +
        "truncationIndex=${projection.truncationIndex}"
)

data class AssistantReplayPendingToolCall(
    val name: String,
    val providerCallId: String?,
    val acceptedResultNames: Set<String> = setOf(name),
)

data class AssistantReplayHistoryRepair(
    val message: ChatMessage,
    val projection: AssistantReplayHistoryProjection,
)

data class AssistantReplayHistoryRepairReport(
    val inspectedMessageCount: Int,
    val candidateCount: Int,
    val repairedMessageCount: Int,
    val removedCharacterCount: Int,
    val reorderedTransactionCount: Int,
)

/**
 * Builds the only model-replay-safe projection of a streamed assistant message.
 *
 * The live stream and audit log retain the original bytes. Durable chat history may expose only
 * complete call/result transactions because every provider adapter treats a user boundary with
 * open calls as a protocol violation. The projector never invents results or retries tools.
 */
object AssistantReplayHistoryProjector {
    /**
     * Classifies a projected assistant without inventing content. Thinking-only output is retained
     * as local evidence but must never become the next provider assistant turn.
     */
    fun eligibility(content: String): AssistantReplayEligibility {
        val projection = project(content)
        val contentWithoutThinking =
            ChatUtils.removeThinkingContent(projection.content)
                .replace(ChatMarkupRegex.metaTag, " ")
                .replace(ChatMarkupRegex.statusTag, " ")
                .replace(ChatMarkupRegex.statusSelfClosingTag, " ")
                .trim()
        if (contentWithoutThinking.isNotBlank()) {
            return AssistantReplayEligibility.REPLAYABLE
        }
        if (content.contains("<think", ignoreCase = true) && projection.content.isNotBlank()) {
            return AssistantReplayEligibility.LOCAL_ONLY_REASONING
        }
        return AssistantReplayEligibility.NONE
    }

    fun isReplayable(content: String): Boolean =
        eligibility(content) == AssistantReplayEligibility.REPLAYABLE

    fun isDurable(content: String): Boolean = eligibility(content) != AssistantReplayEligibility.NONE

    fun replaySafeHistory(
        history: List<PromptTurn>,
        allowTypedToolHistory: Boolean,
    ): List<PromptTurn> =
        history.mapNotNull { turn ->
            if (turn.kind != PromptTurnKind.ASSISTANT) {
                turn
            } else {
                val contentWithoutCompleteCalls =
                    ChatMarkupRegex.toolCallPattern.replace(turn.content, "")
                val hasCompleteTypedToolCall =
                    allowTypedToolHistory &&
                        contentWithoutCompleteCalls != turn.content &&
                        !ChatMarkupRegex.containsToolTag(contentWithoutCompleteCalls)
                if (hasCompleteTypedToolCall) {
                    return@mapNotNull turn
                }
                val projection = project(turn.content)
                projection.content.takeIf(::isReplayable)
                    ?.let { safeContent -> turn.copy(content = safeContent) }
            }
        }

    fun project(content: String): AssistantReplayHistoryProjection {
        val transactionCalls = mutableListOf<TransactionCall>()
        val resultRanges = mutableListOf<IntRange>()
        val replacements = mutableListOf<ContentReplacement>()
        val transactionImageSuffixes = mutableListOf<String>()
        var transactionStart: Int? = null
        var resultsStarted = false
        var transactionResultsInProviderOrder = true
        var reorderedTransactionCount = 0
        var cursor = 0

        while (cursor < content.length) {
            val marker = TOOL_LIKE_TAG_MARKER.find(content, cursor) ?: break
            if (
                transactionCalls.isNotEmpty() &&
                    content.substring(cursor, marker.range.first).isNotBlank()
            ) {
                val betweenResults = content.substring(cursor, marker.range.first)
                // 预览工具将图片链接放在结果信封外。只接纳紧随真实结果的纯图片附件；
                // 等全部调用闭合后再移到事务末尾，避免附件被误判为 assistant 文本边界。
                if (
                    resultsStarted && MediaLinkParser.hasImageLinks(betweenResults) &&
                        MediaLinkParser.removeImageLinks(betweenResults).isBlank()
                ) {
                    transactionImageSuffixes.add(betweenResults)
                    replacements.add(
                        ContentReplacement(cursor until marker.range.first, "")
                    )
                } else {
                    return truncate(
                        content = content,
                        index = requireNotNull(transactionStart),
                        reason =
                            AssistantReplayHistoryProjectionReason.TOOL_TRANSACTION_TEXT_BOUNDARY,
                        transactionCalls = transactionCalls,
                        replacements = replacements,
                        reorderedTransactionCount = reorderedTransactionCount,
                    )
                }
            }
            val block = ChatMarkupRegex.toolOrToolResultBlock.find(content, marker.range.first)
            if (block == null || block.range.first != marker.range.first) {
                return truncate(
                    content = content,
                    index = transactionStart ?: marker.range.first,
                    reason = AssistantReplayHistoryProjectionReason.INCOMPLETE_TOOL_MARKUP,
                    transactionCalls = transactionCalls,
                    replacements = replacements,
                    reorderedTransactionCount = reorderedTransactionCount,
                )
            }

            val tagName = ChatMarkupRegex.extractOpeningTagName(block.value)
            when (ChatMarkupRegex.normalizeToolLikeTagName(tagName)) {
                "tool" -> {
                    if (resultsStarted) {
                        return truncate(
                            content = content,
                            index = requireNotNull(transactionStart),
                            reason =
                                AssistantReplayHistoryProjectionReason
                                    .TOOL_CALL_BEFORE_RESULTS_COMPLETE,
                            transactionCalls = transactionCalls,
                            replacements = replacements,
                            reorderedTransactionCount = reorderedTransactionCount,
                        )
                    }
                    val toolName = extractToolName(block.value)
                        ?: return truncate(
                            content = content,
                            index = transactionStart ?: block.range.first,
                            reason =
                                AssistantReplayHistoryProjectionReason.TOOL_CALL_WITHOUT_NAME,
                            transactionCalls = transactionCalls,
                            replacements = replacements,
                            reorderedTransactionCount = reorderedTransactionCount,
                        )
                    if (transactionStart == null) {
                        transactionStart = block.range.first
                    }
                    transactionCalls.add(
                        TransactionCall(
                            descriptor =
                                AssistantReplayPendingToolCall(
                                    name = toolName,
                                    providerCallId = extractProviderCallId(block.value),
                                    acceptedResultNames = acceptedResultNames(toolName, block.value),
                                ),
                        )
                    )
                }

                "tool_result" -> {
                    val start = transactionStart ?: block.range.first
                    if (transactionCalls.none { it.result == null }) {
                        return truncate(
                            content = content,
                            index = start,
                            reason =
                                AssistantReplayHistoryProjectionReason.TOOL_RESULT_WITHOUT_CALL,
                            transactionCalls = transactionCalls,
                            replacements = replacements,
                            reorderedTransactionCount = reorderedTransactionCount,
                        )
                    }
                    val explicitProviderToolName = extractProviderToolName(block.value)
                    val resultName = explicitProviderToolName ?: extractToolName(block.value)
                        ?: return truncate(
                            content = content,
                            index = start,
                            reason =
                                AssistantReplayHistoryProjectionReason.TOOL_RESULT_WITHOUT_NAME,
                            transactionCalls = transactionCalls,
                            replacements = replacements,
                            reorderedTransactionCount = reorderedTransactionCount,
                        )
                    val resultCallId = extractProviderCallId(block.value)
                    val matchIndex =
                        findMatchingCallIndex(
                            calls = transactionCalls,
                            resultName = resultName,
                            resultCallId = resultCallId,
                            allowDisplayAlias = explicitProviderToolName == null,
                        )
                    if (matchIndex == CALL_ID_MISMATCH) {
                        return truncate(
                            content = content,
                            index = start,
                            reason =
                                AssistantReplayHistoryProjectionReason
                                    .TOOL_RESULT_CALL_ID_MISMATCH,
                            transactionCalls = transactionCalls,
                            replacements = replacements,
                            reorderedTransactionCount = reorderedTransactionCount,
                        )
                    }
                    if (matchIndex == NO_MATCH) {
                        return truncate(
                            content = content,
                            index = start,
                            reason =
                                AssistantReplayHistoryProjectionReason
                                    .TOOL_RESULT_NAME_MISMATCH,
                            transactionCalls = transactionCalls,
                            replacements = replacements,
                            reorderedTransactionCount = reorderedTransactionCount,
                        )
                    }
                    if (!ChatMarkupRegex.isProviderTerminalToolResult(block.value)) {
                        replacements.add(ContentReplacement(range = block.range, value = ""))
                        resultsStarted = true
                        cursor = block.range.last + 1
                        continue
                    }
                    resultsStarted = true
                    val nextProviderResultIndex =
                        transactionCalls.indexOfFirst { it.result == null }
                    if (matchIndex != nextProviderResultIndex) {
                        transactionResultsInProviderOrder = false
                    }
                    val matchedCall = transactionCalls[matchIndex].descriptor
                    val canonicalResult =
                        if (
                            explicitProviderToolName == null &&
                                resultName != matchedCall.name
                        ) {
                            addProviderToolNameAttribute(block.value, matchedCall.name)
                        } else {
                            block.value
                        }
                    transactionCalls[matchIndex].result =
                        MatchedToolResult(range = block.range, value = canonicalResult)
                    resultRanges.add(block.range)
                    if (transactionCalls.all { it.result != null }) {
                        val movedImages = transactionImageSuffixes.isNotEmpty()
                        if (transactionImageSuffixes.isNotEmpty()) {
                            replacements.add(
                                ContentReplacement(
                                    (block.range.last + 1)..block.range.last,
                                    transactionImageSuffixes.joinToString(""),
                                )
                            )
                            reorderedTransactionCount += 1
                            transactionImageSuffixes.clear()
                        }
                        val resultsInCallOrder =
                            transactionCalls.map { requireNotNull(it.result).value }
                        val resultRangesInCallOrder =
                            transactionCalls.map { requireNotNull(it.result).range }
                        if (resultRangesInCallOrder != resultRanges) {
                            resultRanges.zip(resultsInCallOrder).forEach { (range, value) ->
                                replacements.add(ContentReplacement(range = range, value = value))
                            }
                            if (!movedImages) reorderedTransactionCount += 1
                        } else {
                            transactionCalls.forEach { call ->
                                val result = requireNotNull(call.result)
                                if (result.value != content.substring(result.range)) {
                                    replacements.add(
                                        ContentReplacement(
                                            range = result.range,
                                            value = result.value,
                                        )
                                    )
                                }
                            }
                        }
                        transactionCalls.clear()
                        resultRanges.clear()
                        transactionStart = null
                        resultsStarted = false
                        transactionResultsInProviderOrder = true
                    }
                }

                else -> {
                    return truncate(
                        content = content,
                        index = transactionStart ?: marker.range.first,
                        reason = AssistantReplayHistoryProjectionReason.INCOMPLETE_TOOL_MARKUP,
                        transactionCalls = transactionCalls,
                        replacements = replacements,
                        reorderedTransactionCount = reorderedTransactionCount,
                    )
                }
            }
            cursor = block.range.last + 1
        }

        if (
            transactionCalls.any { it.result == null } &&
                content.substring(cursor).isNotBlank()
        ) {
            return truncate(
                content = content,
                index = requireNotNull(transactionStart),
                reason = AssistantReplayHistoryProjectionReason.TOOL_TRANSACTION_TEXT_BOUNDARY,
                transactionCalls = transactionCalls,
                replacements = replacements,
                reorderedTransactionCount = reorderedTransactionCount,
                transactionResultsInProviderOrder = transactionResultsInProviderOrder,
            )
        }
        if (transactionCalls.any { it.result == null }) {
            return truncate(
                content = content,
                index = requireNotNull(transactionStart),
                reason = AssistantReplayHistoryProjectionReason.MISSING_TOOL_RESULT,
                transactionCalls = transactionCalls,
                replacements = replacements,
                reorderedTransactionCount = reorderedTransactionCount,
                transactionResultsInProviderOrder = transactionResultsInProviderOrder,
            )
        }
        return AssistantReplayHistoryProjection(
            content = applyReplacements(content, content.length, replacements),
            originalLength = content.length,
            reorderedTransactionCount = reorderedTransactionCount,
        )
    }

    fun requireClosed(content: String, boundary: String): String {
        val projection = project(content)
        if (projection.truncated) {
            throw AssistantReplayHistoryProtocolException(projection, boundary)
        }
        return projection.content
    }

    private fun findMatchingCallIndex(
        calls: List<TransactionCall>,
        resultName: String,
        resultCallId: String?,
        allowDisplayAlias: Boolean,
    ): Int {
        val unresolvedCalls = calls.withIndex().filter { it.value.result == null }
        fun acceptsResultName(call: TransactionCall): Boolean =
            if (allowDisplayAlias) {
                resultName in call.descriptor.acceptedResultNames
            } else {
                resultName == call.descriptor.name
            }
        if (resultCallId != null) {
            val unresolvedCallsWithIds =
                unresolvedCalls.filter { it.value.descriptor.providerCallId != null }
            if (unresolvedCallsWithIds.isNotEmpty()) {
                val identityMatch =
                    unresolvedCallsWithIds.firstOrNull {
                        it.value.descriptor.providerCallId == resultCallId
                    } ?: return CALL_ID_MISMATCH
                return if (acceptsResultName(identityMatch.value)) {
                    identityMatch.index
                } else {
                    NO_MATCH
                }
            }
        }
        return unresolvedCalls
            .firstOrNull { acceptsResultName(it.value) }
            ?.index
            ?: NO_MATCH
    }

    private fun acceptedResultNames(toolName: String, block: String): Set<String> {
        if (toolName != "package_proxy" && toolName != "proxy") {
            return setOf(toolName)
        }
        val proxiedName =
            TOOL_NAME_PARAM
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        return if (proxiedName == null) setOf(toolName) else setOf(toolName, proxiedName)
    }

    private fun extractToolName(block: String): String? =
        NAME_ATTRIBUTE
            .find(block.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun extractProviderCallId(block: String): String? =
        PROVIDER_CALL_ID_ATTRIBUTE
            .find(block.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun extractProviderToolName(block: String): String? =
        PROVIDER_TOOL_NAME_ATTRIBUTE
            .find(block.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun truncate(
        content: String,
        index: Int,
        reason: AssistantReplayHistoryProjectionReason,
        transactionCalls: List<TransactionCall>,
        replacements: List<ContentReplacement>,
        reorderedTransactionCount: Int,
        transactionResultsInProviderOrder: Boolean = true,
    ): AssistantReplayHistoryProjection =
        AssistantReplayHistoryProjection(
            content = applyReplacements(content, index, replacements),
            originalLength = content.length,
            truncationIndex = index,
            reason = reason,
            pendingToolCallCount = transactionCalls.count { it.result == null },
            pendingToolCalls =
                transactionCalls.mapNotNull { call ->
                    call.descriptor.takeIf { call.result == null }
                },
            reorderedTransactionCount = reorderedTransactionCount,
            crossMessageClosureEligible =
                reason == AssistantReplayHistoryProjectionReason.MISSING_TOOL_RESULT &&
                    replacements.isEmpty() &&
                    transactionResultsInProviderOrder,
        )

    private fun addProviderToolNameAttribute(block: String, providerToolName: String): String {
        val openingTagEnd = block.indexOf('>')
        check(openingTagEnd >= 0) { "Complete tool result has no opening-tag boundary" }
        val attribute =
            " provider_tool_name=\"${escapeXmlAttribute(providerToolName)}\""
        return block.substring(0, openingTagEnd) + attribute + block.substring(openingTagEnd)
    }

    private fun escapeXmlAttribute(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun applyReplacements(
        content: String,
        endExclusive: Int,
        replacements: List<ContentReplacement>,
    ): String {
        val projected = StringBuilder(content.substring(0, endExclusive))
        replacements
            .asSequence()
            .filter { it.range.last < endExclusive }
            .sortedByDescending { it.range.first }
            .forEach { replacement ->
                projected.replace(
                    replacement.range.first,
                    replacement.range.last + 1,
                    replacement.value,
                )
            }
        return projected.toString()
    }

    private data class TransactionCall(
        val descriptor: AssistantReplayPendingToolCall,
        var result: MatchedToolResult? = null,
    )

    private data class MatchedToolResult(
        val range: IntRange,
        val value: String,
    )

    private data class ContentReplacement(
        val range: IntRange,
        val value: String,
    )

    private val TOOL_LIKE_TAG_MARKER =
        Regex(
            "</?(?:${ChatMarkupRegex.TOOL_RESULT_TAG_NAME_REGEX_SOURCE}|" +
                "${ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE})\\b",
            RegexOption.IGNORE_CASE,
        )
    private val PROVIDER_CALL_ID_ATTRIBUTE =
        Regex("""\bprovider_call_id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val PROVIDER_TOOL_NAME_ATTRIBUTE =
        Regex("""\bprovider_tool_name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val NAME_ATTRIBUTE =
        Regex("""(?:^|\s)name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TOOL_NAME_PARAM =
        Regex(
            """<param\s+name\s*=\s*["']tool_name["']\s*>([\s\S]*?)</param>""",
            RegexOption.IGNORE_CASE,
        )
    private const val NO_MATCH = -1
    private const val CALL_ID_MISMATCH = -2
}

object AssistantReplayHistoryRepairPolicy {
    fun excludedMessages(
        messages: List<ChatMessage>,
    ): List<ChatMessage> =
        messages.filter { message ->
            message.sender == "ai" &&
                message.variantCount == 1 &&
                message.selectedVariantIndex == 0 &&
                AssistantReplayHistoryProjector.eligibility(message.content) ==
                    AssistantReplayEligibility.NONE
        }

    fun plan(
        messages: List<ChatMessage>,
    ): List<AssistantReplayHistoryRepair> {
        val preservedCrossMessageIndices = mutableSetOf<Int>()
        return messages.mapIndexedNotNull { messageIndex, message ->
            if (message.sender != "ai" || messageIndex in preservedCrossMessageIndices) {
                return@mapIndexedNotNull null
            }
            val projection = AssistantReplayHistoryProjector.project(message.content)
            if (!projection.changed) {
                return@mapIndexedNotNull null
            }
            if (!projection.truncated) {
                return@mapIndexedNotNull AssistantReplayHistoryRepair(
                    message = message,
                    projection = projection,
                )
            }
            val followingClosure =
                findFollowingMessageClosure(messages, messageIndex, projection)
            if (followingClosure != null) {
                preservedCrossMessageIndices.add(messageIndex)
                preservedCrossMessageIndices.addAll(followingClosure)
                return@mapIndexedNotNull null
            }
            AssistantReplayHistoryRepair(message = message, projection = projection)
        }
    }

    /**
     * Recognizes legacy history where calls and their results were persisted in adjacent AI
     * messages. Only AI messages are eligible because the request compiler treats tool-like markup
     * in user or summary messages as ordinary text. Consumed result messages must also be preserved;
     * projecting them independently would misclassify their results as orphaned.
     */
    private fun findFollowingMessageClosure(
        messages: List<ChatMessage>,
        messageIndex: Int,
        projection: AssistantReplayHistoryProjection,
    ): Set<Int>? {
        if (
            projection.reason != AssistantReplayHistoryProjectionReason.MISSING_TOOL_RESULT ||
                projection.pendingToolCalls.isEmpty() ||
                !projection.crossMessageClosureEligible
        ) {
            return null
        }
        val pendingCalls = projection.pendingToolCalls.toMutableList()
        val consumedMessageIndices = linkedSetOf<Int>()
        for (followingIndex in (messageIndex + 1)..messages.lastIndex) {
            val message = messages[followingIndex]
            if (message.sender != "ai") {
                return null
            }
            val blocks = ChatMarkupRegex.toolOrToolResultBlock.findAll(message.content).toList()
            if (blocks.isEmpty()) {
                return null
            }
            consumedMessageIndices.add(followingIndex)
            var cursor = 0
            for (block in blocks) {
                if (
                    pendingCalls.isNotEmpty() &&
                        message.content.substring(cursor, block.range.first).isNotBlank()
                ) {
                    return null
                }
                when (
                    ChatMarkupRegex.normalizeToolLikeTagName(
                        ChatMarkupRegex.extractOpeningTagName(block.value)
                    )
                ) {
                    "tool" -> return null
                    "tool_result" -> {
                        if (!ChatMarkupRegex.isProviderTerminalToolResult(block.value)) {
                            return null
                        }
                        val resultName =
                            ChatMarkupRegex.extractToolResultProtocolName(block.value)
                            ?: return null
                        val resultCallId =
                            extractAttribute(block.value, PROVIDER_CALL_ID_ATTRIBUTE)
                        val matchIndex =
                            findPendingCallIndex(
                                pendingCalls = pendingCalls,
                                resultName = resultName,
                                resultCallId = resultCallId,
                                // Cross-message preservation cannot rewrite the result message.
                                // A legacy display alias therefore is not yet replay-safe here.
                                allowDisplayAlias = false,
                            )
                        // Cross-message history is preserved only when it already satisfies the
                        // FIFO result order consumed by Chat Completions-style providers.
                        if (matchIndex != 0) return null
                        pendingCalls.removeAt(0)
                        if (pendingCalls.isEmpty()) {
                            val remainingContent =
                                message.content.substring(block.range.last + 1)
                            if (ChatMarkupRegex.containsAnyToolLikeTag(remainingContent)) {
                                return null
                            }
                            return consumedMessageIndices
                        }
                    }
                    else -> return null
                }
                cursor = block.range.last + 1
            }
            val unmatchedContent =
                ChatMarkupRegex.toolOrToolResultBlock.replace(message.content, " ")
            if (ChatMarkupRegex.containsAnyToolLikeTag(unmatchedContent)) {
                return null
            }
            if (pendingCalls.isNotEmpty() && message.content.substring(cursor).isNotBlank()) {
                return null
            }
        }
        return null
    }

    private fun findPendingCallIndex(
        pendingCalls: List<AssistantReplayPendingToolCall>,
        resultName: String,
        resultCallId: String?,
        allowDisplayAlias: Boolean,
    ): Int {
        fun acceptsResultName(call: AssistantReplayPendingToolCall): Boolean =
            if (allowDisplayAlias) {
                resultName in call.acceptedResultNames
            } else {
                resultName == call.name
            }
        if (resultCallId != null && pendingCalls.any { it.providerCallId != null }) {
            val identityIndex = pendingCalls.indexOfFirst { it.providerCallId == resultCallId }
            if (identityIndex < 0) return -1
            return identityIndex.takeIf {
                acceptsResultName(pendingCalls[identityIndex])
            } ?: -1
        }
        return pendingCalls.indexOfFirst(::acceptsResultName)
    }

    private fun extractAttribute(block: String, pattern: Regex): String? =
        pattern
            .find(block.substringBefore('>'))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private val PROVIDER_CALL_ID_ATTRIBUTE =
        Regex("""\bprovider_call_id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
}
