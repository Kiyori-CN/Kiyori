package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.ChatMarkupRegex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCompactionContractTest {
    private val route =
        ConversationCompactionRouteIdentity(
            configId = "deepseek",
            modelIndex = 1,
            providerTypeId = "DEEPSEEK",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            modelName = "deepseek-reasoner",
        )

    @Test
    fun historyWithoutCompletedAiTurnIsRejected() {
        val result =
            ConversationCompactionContract.plan(
                chatId = "chat",
                messages = listOf(message("user", "question", 10L)),
                routeIdentity = route,
            )

        assertRejected(
            expected = ConversationCompactionRejection.NO_COMPLETED_ASSISTANT_TURN,
            actual = result,
        )
    }

    @Test
    fun completeUserAiTurnProducesStableReplacementRange() {
        val messages =
            listOf(
                message("user", "question", 10L),
                message("ai", "answer", 20L),
            )

        val ready =
            ConversationCompactionContract.plan("chat", messages, route)
                as ConversationCompactionPlanResult.Ready

        assertEquals(10L, ready.plan.snapshot.rangeStartTimestamp)
        assertEquals(20L, ready.plan.snapshot.rangeEndTimestamp)
        assertEquals(20L, ready.plan.snapshot.beforeAnchorTimestamp)
        assertEquals(null, ready.plan.snapshot.afterAnchorTimestamp)
        assertEquals(2, ready.plan.snapshot.sourceMessageCount)
        assertEquals(64, ready.plan.snapshot.rangeDigest.length)
        assertTrue(ready.plan.tailMessages.isEmpty())
    }

    @Test
    fun openToolCallRejectsCompaction() {
        val messages =
            listOf(
                message("user", "run", 10L),
                message("ai", toolCall("call_1", "shell"), 20L),
            )

        val result = ConversationCompactionContract.plan("chat", messages, route)

        assertRejected(
            expected = ConversationCompactionRejection.TOOL_HISTORY_INVALID,
            actual = result,
        )
    }

    @Test
    fun parallelToolCallsRequireEveryResultBeforeBoundary() {
        val complete =
            listOf(
                message("user", "run both", 10L),
                message(
                    "ai",
                    toolCall("call_1", "first") +
                        toolCall("call_2", "second") +
                        toolResult("first", "one") +
                        toolResult("second", "two"),
                    20L,
                ),
            )
        val incomplete =
            listOf(
                complete[0],
                complete[1].copy(
                    content =
                        toolCall("call_1", "first") +
                            toolCall("call_2", "second") +
                            toolResult("first", "one")
                ),
            )

        assertTrue(
            ConversationCompactionContract.plan("chat", complete, route)
                is ConversationCompactionPlanResult.Ready
        )
        assertRejected(
            expected = ConversationCompactionRejection.TOOL_HISTORY_INVALID,
            actual = ConversationCompactionContract.plan("chat", incomplete, route),
        )
    }

    @Test
    fun toolTransactionMaySpanPersistedMessagesWhenNamesAndIdsMatch() {
        val messages =
            listOf(
                message("user", "run shell", 10L),
                message("ai", toolCall("call_1", "shell"), 20L),
                message("user", toolResult("shell", "done", providerCallId = "call_1"), 30L),
                message("ai", "completed", 40L),
            )

        assertTrue(
            ConversationCompactionContract.plan("chat", messages, route)
                is ConversationCompactionPlanResult.Ready
        )
    }

    @Test
    fun toolResultNameMismatchRejectsCompaction() {
        val messages =
            listOf(
                message("user", "run shell", 10L),
                message("ai", toolCall("call_1", "shell"), 20L),
                message("user", toolResult("browser", "done"), 30L),
                message("ai", "completed", 40L),
            )

        assertRejected(
            expected = ConversationCompactionRejection.TOOL_HISTORY_INVALID,
            actual = ConversationCompactionContract.plan("chat", messages, route),
        )
    }

    @Test
    fun toolResultCallIdMismatchRejectsCompaction() {
        val messages =
            listOf(
                message("user", "run shell", 10L),
                message("ai", toolCall("call_1", "shell"), 20L),
                message(
                    "user",
                    toolResult("shell", "done", providerCallId = "call_2"),
                    30L,
                ),
                message("ai", "completed", 40L),
            )

        assertRejected(
            expected = ConversationCompactionRejection.TOOL_HISTORY_INVALID,
            actual = ConversationCompactionContract.plan("chat", messages, route),
        )
    }

    @Test
    fun trailingUserMessageRemainsOutsideReplacementRange() {
        val messages =
            listOf(
                message("user", "first", 10L),
                message("ai", "first answer", 20L),
                message("user", "pending follow-up", 30L),
            )

        val ready =
            ConversationCompactionContract.plan("chat", messages, route)
                as ConversationCompactionPlanResult.Ready

        assertEquals(listOf(10L, 20L), ready.plan.sourceMessages.map { it.timestamp })
        assertEquals(listOf(30L), ready.plan.tailMessages.map { it.timestamp })
        assertEquals(30L, ready.plan.snapshot.afterAnchorTimestamp)
    }

    @Test
    fun tailAppendIsAllowedWhenOriginalRangeIsUnchanged() {
        val source =
            listOf(
                message("user", "question", 10L),
                message("ai", "answer", 20L),
            )
        val snapshot =
            (
                ConversationCompactionContract.plan("chat", source, route)
                    as ConversationCompactionPlanResult.Ready
            ).plan.snapshot

        val decision =
            ConversationCompactionContract.validateCommit(
                snapshot = snapshot,
                chatId = "chat",
                currentRangeMessages = source,
                currentAfterAnchor = message("user", "new tail", 30L),
                currentRouteIdentity = route,
            )

        assertEquals(30L, (decision as ConversationCompactionCommitDecision.Allow).afterAnchorTimestamp)
    }

    @Test
    fun editDeleteReorderAndVariantSwitchChangeTheRangeDigest() {
        val source =
            listOf(
                message("user", "question", 10L),
                message("ai", "answer", 20L),
            )
        val snapshot =
            (
                ConversationCompactionContract.plan("chat", source, route)
                    as ConversationCompactionPlanResult.Ready
            ).plan.snapshot
        val changedRanges =
            listOf(
                source.map { if (it.timestamp == 20L) it.copy(content = "edited") else it },
                source.drop(1),
                source.reversed(),
                source.map {
                    if (it.timestamp == 20L) {
                        it.copy(selectedVariantIndex = 1, variantCount = 2, content = "variant")
                    } else {
                        it
                    }
                },
            )

        changedRanges.forEach { changed ->
            val decision =
                ConversationCompactionContract.validateCommit(
                    snapshot = snapshot,
                    chatId = "chat",
                    currentRangeMessages = changed,
                    currentAfterAnchor = null,
                    currentRouteIdentity = route,
                )
            assertCommitRejected(
                expected = ConversationCompactionRejection.RANGE_CHANGED,
                actual = decision,
            )
        }
    }

    @Test
    fun routeChangeRejectsCommit() {
        val source =
            listOf(
                message("user", "question", 10L),
                message("ai", "answer", 20L),
            )
        val snapshot =
            (
                ConversationCompactionContract.plan("chat", source, route)
                    as ConversationCompactionPlanResult.Ready
            ).plan.snapshot

        val decision =
            ConversationCompactionContract.validateCommit(
                snapshot = snapshot,
                chatId = "chat",
                currentRangeMessages = source,
                currentAfterAnchor = null,
                currentRouteIdentity = route.copy(modelName = "deepseek-chat"),
            )

        assertCommitRejected(
            expected = ConversationCompactionRejection.ROUTE_CHANGED,
            actual = decision,
        )
    }

    @Test
    fun adjacentSummaryRejectsCommit() {
        val source =
            listOf(
                message("user", "question", 10L),
                message("ai", "answer", 20L),
            )
        val snapshot =
            (
                ConversationCompactionContract.plan("chat", source, route)
                    as ConversationCompactionPlanResult.Ready
            ).plan.snapshot

        val decision =
            ConversationCompactionContract.validateCommit(
                snapshot = snapshot,
                chatId = "chat",
                currentRangeMessages = source,
                currentAfterAnchor = message("summary", "newer summary", 30L),
                currentRouteIdentity = route,
            )

        assertCommitRejected(
            expected = ConversationCompactionRejection.ALREADY_REPLACED,
            actual = decision,
        )
    }

    @Test
    fun summaryCheckpointStripsPrivateReplayAndThinkingButRejectsToolMarkup() {
        val valid =
            ConversationCompactionContract.sanitizeSummaryCheckpoint(
                "<think>private</think>" +
                    ChatMarkupRegex.openAiResponsesReasoningMetaTag("opaque") +
                    "safe summary"
            )
        val tool =
            ConversationCompactionContract.sanitizeSummaryCheckpoint(
                "summary " + toolCall("call_1", "shell")
            )

        assertEquals(
            "safe summary",
            (valid as ConversationSummaryCheckpointResult.Valid).content,
        )
        assertEquals(
            ConversationSummaryCheckpointRejection.TOOL_MARKUP_PRESENT,
            (tool as ConversationSummaryCheckpointResult.Rejected).reason,
        )
        assertFalse(
            (valid.content).contains("<meta")
        )
    }

    private fun message(
        sender: String,
        content: String,
        timestamp: Long,
    ): ChatMessage =
        ChatMessage(
            sender = sender,
            content = content,
            timestamp = timestamp,
        )

    private fun toolCall(callId: String, name: String): String =
        """
        <tool_A1 name="$name" provider_name="DEEPSEEK" provider_call_id="$callId">
        <param name="value">x</param>
        </tool_A1>
        """.trimIndent()

    private fun toolResult(
        name: String,
        content: String,
        providerCallId: String? = null,
    ): String {
        val providerCallIdAttribute =
            providerCallId?.let { " provider_call_id=\"$it\"" }.orEmpty()
        return (
            """<tool_result_A1 name="$name"$providerCallIdAttribute status="success">""" +
                "<content>$content</content></tool_result_A1>"
        )
    }

    private fun assertRejected(
        expected: ConversationCompactionRejection,
        actual: ConversationCompactionPlanResult,
    ) {
        assertEquals(expected, (actual as ConversationCompactionPlanResult.Rejected).reason)
    }

    private fun assertCommitRejected(
        expected: ConversationCompactionRejection,
        actual: ConversationCompactionCommitDecision,
    ) {
        assertEquals(expected, (actual as ConversationCompactionCommitDecision.Reject).reason)
    }
}
