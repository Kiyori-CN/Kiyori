package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.audit.ConversationAuditHasher
import com.ai.assistance.operit.util.ChatMarkupRegex

/**
 * Provider-visible projection for oversized tool results.
 *
 * The persisted ChatMessage remains the source of truth. This projection is only used while
 * preparing a summary request, so a large result cannot consume the whole summary context. The
 * result tag and its identity attributes stay intact; only the payload body is replaced by a
 * deterministic head/middle/tail view.
 */
data class ConversationToolResultPruningReport(
    val inspectedResultCount: Int = 0,
    val prunedResultCount: Int = 0,
    val originalPayloadChars: Long = 0L,
    val projectedPayloadChars: Long = 0L,
) {
    init {
        require(inspectedResultCount >= 0) { "inspectedResultCount must not be negative" }
        require(prunedResultCount >= 0) { "prunedResultCount must not be negative" }
        require(prunedResultCount <= inspectedResultCount) {
            "prunedResultCount must not exceed inspectedResultCount"
        }
        require(originalPayloadChars >= 0L) { "originalPayloadChars must not be negative" }
        require(projectedPayloadChars >= 0L) { "projectedPayloadChars must not be negative" }
        require(projectedPayloadChars <= originalPayloadChars) {
            "projectedPayloadChars must not exceed originalPayloadChars"
        }
    }

    val omittedPayloadChars: Long
        get() = originalPayloadChars - projectedPayloadChars

    operator fun plus(other: ConversationToolResultPruningReport): ConversationToolResultPruningReport =
        ConversationToolResultPruningReport(
            inspectedResultCount =
                saturatedIntSum(inspectedResultCount, other.inspectedResultCount),
            prunedResultCount = saturatedIntSum(prunedResultCount, other.prunedResultCount),
            originalPayloadChars =
                saturatedLongSum(originalPayloadChars, other.originalPayloadChars),
            projectedPayloadChars =
                saturatedLongSum(projectedPayloadChars, other.projectedPayloadChars),
        )

    private fun saturatedIntSum(left: Int, right: Int): Int =
        (left.toLong() + right.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun saturatedLongSum(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

data class ConversationToolResultPruningResult(
    val content: String,
    val report: ConversationToolResultPruningReport,
)

object ConversationToolResultPruner {
    const val MAX_PAYLOAD_CHARS = 16_384
    private const val HEAD_PAYLOAD_CHARS = 8_192
    private const val TAIL_PAYLOAD_CHARS = 4_096

    init {
        require(HEAD_PAYLOAD_CHARS + TAIL_PAYLOAD_CHARS < MAX_PAYLOAD_CHARS) {
            "Tool result projection must reserve space for its omission marker"
        }
    }

    fun pruneContent(content: String): ConversationToolResultPruningResult {
        if (content.isBlank()) {
            return ConversationToolResultPruningResult(
                content = content,
                report = ConversationToolResultPruningReport(),
            )
        }

        var report = ConversationToolResultPruningReport()
        val projected =
            ChatMarkupRegex.toolResultAnyPattern.replace(content) { match ->
                val result = projectResultBlock(match.value)
                report += result.report
                result.content
            }
        return ConversationToolResultPruningResult(content = projected, report = report)
    }

    private fun projectResultBlock(block: String): ConversationToolResultPruningResult {
        val openingEnd = block.indexOf('>')
        val closingStart = block.lastIndexOf("</")
        if (openingEnd < 0 || closingStart <= openingEnd) {
            return ConversationToolResultPruningResult(
                content = block,
                report =
                    ConversationToolResultPruningReport(
                        inspectedResultCount = 1,
                    ),
            )
        }

        val body = block.substring(openingEnd + 1, closingStart)
        val payloadMatch =
            ChatMarkupRegex.contentTag.find(body)
                ?: ChatMarkupRegex.errorTag.find(body)
        val payload =
            payloadMatch?.groupValues?.getOrNull(1)
                ?: body
        val inspectedReport =
            ConversationToolResultPruningReport(
                inspectedResultCount = 1,
                originalPayloadChars = payload.length.toLong(),
                projectedPayloadChars = payload.length.toLong(),
            )
        if (payload.length <= MAX_PAYLOAD_CHARS) {
            return ConversationToolResultPruningResult(
                content = block,
                report = inspectedReport,
            )
        }

        val digest = ConversationAuditHasher.sha256(payload)
        val marker =
            "[tool_result_pruned omitted_chars=${payload.length - HEAD_PAYLOAD_CHARS - TAIL_PAYLOAD_CHARS} " +
                "sha256=$digest]"
        val projectedPayload =
            payload.take(HEAD_PAYLOAD_CHARS) +
                marker +
                payload.takeLast(TAIL_PAYLOAD_CHARS)
        val projectedBody =
            if (payloadMatch == null) {
                projectedPayload
            } else {
                val payloadOpenEnd =
                    payloadMatch.range.first +
                        payloadMatch.value.indexOf('>') +
                        1
                val payloadCloseStart =
                    payloadMatch.range.first +
                        payloadMatch.value.lastIndexOf("</")
                body.replaceRange(
                    payloadOpenEnd,
                    payloadCloseStart,
                    projectedPayload,
                )
            }
        return ConversationToolResultPruningResult(
            content =
                block.substring(0, openingEnd + 1) +
                    projectedBody +
                    block.substring(closingStart),
            report =
                ConversationToolResultPruningReport(
                    inspectedResultCount = 1,
                    prunedResultCount = 1,
                    originalPayloadChars = payload.length.toLong(),
                    projectedPayloadChars = projectedPayload.length.toLong(),
                ),
        )
    }
}
