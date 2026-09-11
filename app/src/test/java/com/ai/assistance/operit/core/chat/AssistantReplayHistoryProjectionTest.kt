package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReplayHistoryProjectionTest {
    @Test
    fun emptyAssistant_isNotReplayable() {
        assertEquals(
            AssistantReplayEligibility.NONE,
            AssistantReplayHistoryProjector.eligibility(""),
        )
        assertFalse(AssistantReplayHistoryProjector.isDurable("   \n\t"))
    }

    @Test
    fun reasoningOnlyAssistant_isLocalOnlyAndNotReplayable() {
        assertEquals(
            AssistantReplayEligibility.LOCAL_ONLY_REASONING,
            AssistantReplayHistoryProjector.eligibility("<think>reasoning</think>"),
        )
        assertFalse(AssistantReplayHistoryProjector.isReplayable("<think>reasoning</think>"))
        assertTrue(AssistantReplayHistoryProjector.isDurable("<think>reasoning</think>"))
        assertEquals(
            AssistantReplayEligibility.LOCAL_ONLY_REASONING,
            AssistantReplayHistoryProjector.eligibility(
                "<THINKING>reasoning</THINKING><meta provider=\"anthropic_content_blocks\">state</meta>"
            ),
        )
    }

    @Test
    fun reasoningWithVisibleAnswer_isReplayable() {
        assertEquals(
            AssistantReplayEligibility.REPLAYABLE,
            AssistantReplayHistoryProjector.eligibility(
                "<think>reasoning</think>\nvisible answer"
            ),
        )
    }

    @Test
    fun statusOnlyAssistant_isNotReplayable() {
        assertEquals(
            AssistantReplayEligibility.NONE,
            AssistantReplayHistoryProjector.eligibility("<status>working</status>"),
        )
    }

    @Test
    fun completeToolTransaction_isReplayableWithoutVisibleProse() {
        val content = call("run", "call-1") + result("run", "call-1")

        assertEquals(
            AssistantReplayEligibility.REPLAYABLE,
            AssistantReplayHistoryProjector.eligibility(content),
        )
    }

    @Test
    fun incompleteToolTransaction_isNotReplayableWhenItHasNoSafePrefix() {
        assertEquals(
            AssistantReplayEligibility.NONE,
            AssistantReplayHistoryProjector.eligibility(call("run", "call-1")),
        )
    }

    @Test
    fun incompleteToolTransaction_replaysOnlyItsSafePrefix() {
        val projection =
            AssistantReplayHistoryProjector.project("safe prefix\n" + call("run", "call-1"))

        assertEquals("safe prefix\n", projection.content)
        assertEquals(
            AssistantReplayEligibility.REPLAYABLE,
            AssistantReplayHistoryProjector.eligibility(projection.content),
        )
    }

    @Test
    fun repairPolicy_excludesOnlyUnversionedEmptyAssistantRows() {
        val empty = ChatMessage(sender = "ai", content = "", timestamp = 1L)
        val statusOnly =
            ChatMessage(sender = "ai", content = "<status>working</status>", timestamp = 2L)
        val reasoningOnly =
            ChatMessage(sender = "ai", content = "<think>reasoning</think>", timestamp = 3L)
        val emptyWithVariant =
            ChatMessage(sender = "ai", content = "", timestamp = 4L, variantCount = 2)
        val user = ChatMessage(sender = "user", content = "", timestamp = 5L)
        val nonBaseSelection =
            ChatMessage(
                sender = "ai",
                content = "",
                timestamp = 6L,
                selectedVariantIndex = 1,
            )

        assertEquals(
            listOf(empty, statusOnly),
            AssistantReplayHistoryRepairPolicy.excludedMessages(
                listOf(empty, statusOnly, reasoningOnly, emptyWithVariant, user, nonBaseSelection)
            ),
        )
    }

    @Test
    fun plainText_isUnchanged() {
        assertUnchanged("answer")
    }

    @Test
    fun completeSingleToolTransaction_isUnchanged() {
        assertUnchanged(
            "before" + call("run", "call-1") + result("run", "call-1") + "after"
        )
    }

    @Test
    fun threeParallelCalls_withoutResults_truncatesWholeTransaction() {
        val prefix = "before\n"
        val content = prefix + call("one", "1") + call("two", "2") + call("three", "3")

        val projection = AssistantReplayHistoryProjector.project(content)

        assertEquals(prefix, projection.content)
        assertEquals(3, projection.pendingToolCallCount)
        assertEquals(AssistantReplayHistoryProjectionReason.MISSING_TOOL_RESULT, projection.reason)
    }

    @Test
    fun threeParallelCalls_withOneOrTwoResults_truncateWholeTransaction() {
        val calls = call("one", "1") + call("two", "2") + call("three", "3")
        listOf(
            result("one", "1"),
            result("one", "1") + result("two", "2"),
        ).forEach { partialResults ->
            val projection = AssistantReplayHistoryProjector.project("safe" + calls + partialResults)
            assertEquals("safe", projection.content)
            assertTrue(projection.changed)
        }
    }

    @Test
    fun threeParallelCalls_withAllResults_areUnchanged() {
        assertUnchanged(
            call("one", "1") + call("two", "2") + call("three", "3") +
                result("one", "1") + result("two", "2") + result("three", "3")
        )
    }

    @Test
    fun fourParallelCalls_completedOutOfOrder_areCanonicalizedWithoutTruncation() {
        val calls =
            call("one", "1") + call("two", "2") + call("three", "3") + call("four", "4")
        val completionOrder =
            result("three", "3", "third") +
                result("one", "1", "first") +
                result("four", "4", "fourth") +
                result("two", "2", "second")
        val callOrder =
            result("one", "1", "first") +
                result("two", "2", "second") +
                result("three", "3", "third") +
                result("four", "4", "fourth")

        val projection = AssistantReplayHistoryProjector.project(calls + completionOrder + "done")

        assertEquals(calls + callOrder + "done", projection.content)
        assertTrue(projection.changed)
        assertFalse(projection.truncated)
        assertEquals(1, projection.reorderedTransactionCount)
        assertEquals(0, projection.removedCharacterCount)
        assertEquals(
            calls + callOrder + "done",
            AssistantReplayHistoryProjector.requireClosed(
                calls + completionOrder + "done",
                "assistant_completion",
            ),
        )
    }

    @Test
    fun parallelCallsWithoutProviderIds_areMatchedByNameAndCanonicalized() {
        val calls = call("one", null) + call("two", null)
        val completionOrder = result("two", null, "second") + result("one", null, "first")
        val callOrder = result("one", null, "first") + result("two", null, "second")

        val projection = AssistantReplayHistoryProjector.project(calls + completionOrder)

        assertEquals(calls + callOrder, projection.content)
        assertEquals(1, projection.reorderedTransactionCount)
        assertFalse(projection.truncated)
    }

    @Test
    fun proxyResultName_matchesDeclaredTargetName() {
        val proxyCall =
            "<tool_exec name=\"package_proxy\" provider_call_id=\"1\">" +
                "<param name=\"tool_name\">terminal:run</param></tool_exec>"

        val projection = AssistantReplayHistoryProjector.project(
            proxyCall + result("terminal:run", "1")
        )

        assertFalse(projection.truncated)
        assertTrue(projection.changed)
        assertEquals(0, projection.removedCharacterCount)
        assertTrue(projection.content.contains("name=\"terminal:run\""))
        assertTrue(projection.content.contains("provider_tool_name=\"package_proxy\""))
    }

    @Test
    fun duplicateParallelToolNames_areCanonicalizedByProviderCallId() {
        val calls = call("read_file", "1") + call("read_file", "2")
        val completionOrder =
            result(
                name = "read_file",
                callId = "2",
                payload = "second",
                providerToolName = "read_file",
            ) +
                result(
                    name = "read_file",
                    callId = "1",
                    payload = "first",
                    providerToolName = "read_file",
                )
        val callOrder =
            result("read_file", "1", "first", "read_file") +
                result("read_file", "2", "second", "read_file")

        val projection = AssistantReplayHistoryProjector.project(calls + completionOrder)

        assertEquals(calls + callOrder, projection.content)
        assertEquals(1, projection.reorderedTransactionCount)
        assertFalse(projection.truncated)
    }

    @Test
    fun streamingIntermediateResults_areRemovedAndOnlyTerminalResultClosesTheCall() {
        val toolCall = call("terminal", "1")
        val start = result("terminal", "1", "start", providerResultTerminal = false)
        val chunk = result("terminal", "1", "chunk", providerResultTerminal = false)
        val terminal = result("terminal", "1", "complete", providerResultTerminal = true)

        val projection = AssistantReplayHistoryProjector.project(toolCall + start + chunk + terminal)

        assertEquals(toolCall + terminal, projection.content)
        assertTrue(projection.changed)
        assertFalse(projection.truncated)
        assertEquals(start.length + chunk.length, projection.removedCharacterCount)

        val secondProjection = AssistantReplayHistoryProjector.project(projection.content)
        assertFalse(secondProjection.changed)
        assertEquals(projection.content, secondProjection.content)
    }

    @Test
    fun streamingCallWithoutTerminalResult_truncatesTheWholeTransaction() {
        val toolCall = call("terminal", "1")
        val intermediate = result("terminal", "1", "chunk", providerResultTerminal = false)

        val projection = AssistantReplayHistoryProjector.project("safe" + toolCall + intermediate)

        assertEquals("safe", projection.content)
        assertTrue(projection.truncated)
        assertEquals(AssistantReplayHistoryProjectionReason.MISSING_TOOL_RESULT, projection.reason)
    }

    @Test
    fun completeFirstTransactionAndIncompleteSecond_keepsOnlyFirstTransaction() {
        val first = call("one", "1") + result("one", "1")
        val projection = AssistantReplayHistoryProjector.project(first + "middle" + call("two", "2"))

        assertEquals(first + "middle", projection.content)
    }

    @Test
    fun nonBlankTextInsideToolTransaction_truncatesFromTheCall() {
        val projection =
            AssistantReplayHistoryProjector.project(
                "safe" + call("run", "1") + "not a tool result" + result("run", "1")
            )

        assertEquals("safe", projection.content)
        assertEquals(
            AssistantReplayHistoryProjectionReason.TOOL_TRANSACTION_TEXT_BOUNDARY,
            projection.reason,
        )
    }

    @Test
    fun truncatedOpeningOrClosingTag_truncatesFromItsTransactionStart() {
        listOf(
            "safe<tool_exec name=\"run\">",
            "safe<tool_exec name=\"run\"><param name=\"x\">1</param></tool_ex",
        ).forEach { content ->
            val projection = AssistantReplayHistoryProjector.project(content)
            assertEquals("safe", projection.content)
            assertEquals(
                AssistantReplayHistoryProjectionReason.INCOMPLETE_TOOL_MARKUP,
                projection.reason,
            )
        }
    }

    @Test
    fun resultWithoutCall_truncatesFromResult() {
        val projection = AssistantReplayHistoryProjector.project("safe" + result("run", "1"))

        assertEquals("safe", projection.content)
        assertEquals(
            AssistantReplayHistoryProjectionReason.TOOL_RESULT_WITHOUT_CALL,
            projection.reason,
        )
    }

    @Test
    fun nameMismatch_truncatesWholeTransaction() {
        val projection =
            AssistantReplayHistoryProjector.project("safe" + call("run", "1") + result("read", "1"))

        assertEquals("safe", projection.content)
        assertEquals(
            AssistantReplayHistoryProjectionReason.TOOL_RESULT_NAME_MISMATCH,
            projection.reason,
        )
    }

    @Test
    fun providerCallIdMismatch_truncatesWholeTransaction() {
        val projection =
            AssistantReplayHistoryProjector.project("safe" + call("run", "1") + result("run", "2"))

        assertEquals("safe", projection.content)
        assertEquals(
            AssistantReplayHistoryProjectionReason.TOOL_RESULT_CALL_ID_MISMATCH,
            projection.reason,
        )
    }

    @Test
    fun missingProviderCallIdOnOneSide_isAccepted() {
        assertUnchanged(call("run", "1") + result("run", null))
    }

    @Test
    fun providerNameBeforeToolName_doesNotReplaceTheToolName() {
        val call =
            "<tool_exec provider_name=\"OPENAI\" name=\"run\" provider_call_id=\"1\">" +
                "</tool_exec>"

        assertUnchanged(call + result("run", "1"))
    }

    @Test
    fun requireClosed_rejectsIncompleteTransaction() {
        assertThrows(AssistantReplayHistoryProtocolException::class.java) {
            AssistantReplayHistoryProjector.requireClosed(call("run", "1"), "test")
        }
    }

    @Test
    fun repairPolicy_repairsEveryUnclosedReplayMessageRegardlessOfCompletedAt() {
        val incomplete = call("run", "1")
        val messages =
            listOf(
                ChatMessage(sender = "ai", content = incomplete, timestamp = 10L, completedAt = 0L),
                ChatMessage(sender = "ai", content = incomplete, timestamp = 20L, completedAt = 30L),
                ChatMessage(sender = "ai", content = incomplete, timestamp = 40L, completedAt = 50L),
                ChatMessage(sender = "ai", content = call("run", "1") + result("run", "1"), timestamp = 60L),
                ChatMessage(sender = "user", content = incomplete, timestamp = 70L),
            )

        val repairs =
            AssistantReplayHistoryRepairPolicy.plan(
                messages = messages,
            )

        assertEquals(listOf(10L, 20L, 40L), repairs.map { it.message.timestamp })
        assertTrue(repairs.all { it.projection.content.isEmpty() })
    }

    @Test
    fun repairPolicy_repairsCompletedHistoryEndWithoutFailureEvidence() {
        val message =
            ChatMessage(
                sender = "ai",
                content = "safe" + call("run", "1"),
                timestamp = 20L,
                completedAt = 30L,
            )

        val repairs =
            AssistantReplayHistoryRepairPolicy.plan(
                messages = listOf(message),
            )

        assertEquals(1, repairs.size)
        assertEquals("safe", repairs.single().projection.content)
    }

    @Test
    fun repairPolicy_preservesTransactionClosedByFollowingPersistedAssistantMessage() {
        val messages =
            listOf(
                ChatMessage(sender = "user", content = "run", timestamp = 10L),
                ChatMessage(
                    sender = "ai",
                    content = call("run", "1"),
                    timestamp = 20L,
                    completedAt = 50L,
                ),
                ChatMessage(
                    sender = "ai",
                    content = result("run", "1") + "done",
                    timestamp = 30L,
                ),
            )

        val repairs =
            AssistantReplayHistoryRepairPolicy.plan(
                messages = messages,
            )

        assertTrue(repairs.isEmpty())
    }

    @Test
    fun repairPolicy_rejectsCrossMessageClosureAfterOutOfOrderPartialResult() {
        val messages =
            listOf(
                ChatMessage(
                    sender = "ai",
                    content = call("one", "1") + call("two", "2") + result("two", "2"),
                    timestamp = 20L,
                ),
                ChatMessage(
                    sender = "ai",
                    content = result("one", "1"),
                    timestamp = 30L,
                ),
            )

        val repairs = AssistantReplayHistoryRepairPolicy.plan(messages)

        assertEquals(listOf(20L, 30L), repairs.map { it.message.timestamp })
        assertTrue(repairs.all { it.projection.content.isEmpty() })
    }

    @Test
    fun repairPolicy_rejectsCrossMessageClosureWhenSourceContainsIntermediateResult() {
        val messages =
            listOf(
                ChatMessage(
                    sender = "ai",
                    content =
                        call("run", "1") +
                            result(
                                "run",
                                "1",
                                payload = "chunk",
                                providerResultTerminal = false,
                            ),
                    timestamp = 20L,
                ),
                ChatMessage(
                    sender = "ai",
                    content = result("run", "1", payload = "complete"),
                    timestamp = 30L,
                ),
            )

        val repairs = AssistantReplayHistoryRepairPolicy.plan(messages)

        assertEquals(listOf(20L, 30L), repairs.map { it.message.timestamp })
        assertTrue(repairs.all { it.projection.content.isEmpty() })
    }

    @Test
    fun repairPolicy_rejectsCrossMessageLegacyProxyDisplayAlias() {
        val proxyCall =
            "<tool_exec name=\"package_proxy\" provider_call_id=\"1\">" +
                "<param name=\"tool_name\">terminal:run</param></tool_exec>"
        val messages =
            listOf(
                ChatMessage(sender = "ai", content = proxyCall, timestamp = 20L),
                ChatMessage(
                    sender = "ai",
                    content = result("terminal:run", "1"),
                    timestamp = 30L,
                ),
            )

        val repairs = AssistantReplayHistoryRepairPolicy.plan(messages)

        assertEquals(listOf(20L, 30L), repairs.map { it.message.timestamp })
        assertTrue(repairs.all { it.projection.content.isEmpty() })
    }

    @Test
    fun repairPolicy_rejectsCrossMessageClosureWhenEarlierTransactionNeedsCanonicalization() {
        val completedCalls = call("one", "1") + call("two", "2")
        val completedOutOfOrder = result("two", "2") + result("one", "1")
        val openCall = call("three", "3")
        val messages =
            listOf(
                ChatMessage(
                    sender = "ai",
                    content = completedCalls + completedOutOfOrder + openCall,
                    timestamp = 20L,
                ),
                ChatMessage(
                    sender = "ai",
                    content = result("three", "3"),
                    timestamp = 30L,
                ),
            )

        val repairs = AssistantReplayHistoryRepairPolicy.plan(messages)

        assertEquals(listOf(20L, 30L), repairs.map { it.message.timestamp })
        assertEquals(
            completedCalls + result("one", "1") + result("two", "2"),
            repairs.first().projection.content,
        )
        assertEquals("", repairs.last().projection.content)
    }

    @Test
    fun explicitProviderNameCannotUseProxyDisplayAlias() {
        val proxyCall =
            "<tool_exec name=\"package_proxy\" provider_call_id=\"1\">" +
                "<param name=\"tool_name\">terminal:run</param></tool_exec>"
        val invalidResult =
            result(
                name = "terminal:run",
                callId = "1",
                providerToolName = "terminal:run",
            )

        val projection = AssistantReplayHistoryProjector.project(proxyCall + invalidResult)

        assertTrue(projection.truncated)
        assertEquals(
            AssistantReplayHistoryProjectionReason.TOOL_RESULT_NAME_MISMATCH,
            projection.reason,
        )
    }

    @Test
    fun repairPolicy_doesNotTreatUserMarkupAsCrossMessageToolResult() {
        val messages =
            listOf(
                ChatMessage(
                    sender = "ai",
                    content = "safe" + call("run", "1"),
                    timestamp = 20L,
                    completedAt = 30L,
                ),
                ChatMessage(
                    sender = "user",
                    content = result("run", "1"),
                    timestamp = 40L,
                ),
            )

        val repairs = AssistantReplayHistoryRepairPolicy.plan(messages)

        assertEquals(listOf(20L), repairs.map { it.message.timestamp })
        assertEquals("safe", repairs.single().projection.content)
    }

    @Test
    fun repairPolicy_repairsIncompleteCrossMessageAssistantTransactionAndOrphanResult() {
        val messages =
            listOf(
                ChatMessage(sender = "user", content = "run", timestamp = 10L),
                ChatMessage(
                    sender = "ai",
                    content = call("one", "1") + call("two", "2"),
                    timestamp = 20L,
                    completedAt = 0L,
                ),
                ChatMessage(
                    sender = "ai",
                    content = result("one", "1"),
                    timestamp = 30L,
                ),
            )

        val repairs =
            AssistantReplayHistoryRepairPolicy.plan(
                messages = messages,
            )

        assertEquals(listOf(20L, 30L), repairs.map { it.message.timestamp })
        assertTrue(repairs.all { it.projection.content.isEmpty() })
    }

    @Test
    fun repairPolicy_canonicalizesCompletedParallelHistoryWithoutFailureEvidence() {
        val calls = call("one", "1") + call("two", "2")
        val completionOrder = result("two", "2", "second") + result("one", "1", "first")
        val callOrder = result("one", "1", "first") + result("two", "2", "second")
        val message =
            ChatMessage(
                sender = "ai",
                content = calls + completionOrder,
                timestamp = 20L,
                completedAt = 30L,
            )

        val repairs =
            AssistantReplayHistoryRepairPolicy.plan(
                messages = listOf(message),
            )

        assertEquals(1, repairs.size)
        assertEquals(calls + callOrder, repairs.single().projection.content)
        assertFalse(repairs.single().projection.truncated)
    }

    @Test
    fun parallelPreviewAttachmentsMoveAfterClosedResultsWithoutLosingFinalAnswer() {
        val calls = call("preview", "1") + call("read", "2")
        val image = "\n<link type=\"image\" id=\"office-page-3\"></link>\n"
        val first = result("read", "2")
        val second = result("preview", "1")
        val answer = "测评完成，交付全部文件。"
        val prefix = "历史".repeat(100_000)
        val projection = AssistantReplayHistoryProjector.project(
            prefix + calls + first + image + second + answer
        )

        assertFalse(projection.truncated)
        assertEquals(prefix + calls + second + first + image + answer, projection.content)
        assertEquals(1, projection.reorderedTransactionCount)
        assertUnchanged(projection.content)
        assertEquals(projection.content, AssistantReplayHistoryProjector.requireClosed(
            projection.content, "assistant_completion"
        ))
    }

    @Test
    fun streamingPreviewAttachmentsWaitForRealTerminalResult() {
        val toolCall = call("preview", "1")
        val image = "\n<link type=\"image\" id=\"page-1\"></link>\n"
        val intermediate = result("preview", "1", providerResultTerminal = false)
        val terminal = result("preview", "1", providerResultTerminal = true)
        val projection = AssistantReplayHistoryProjector.project(
            toolCall + intermediate + image + terminal + "完成"
        )
        assertFalse(projection.truncated)
        assertEquals(toolCall + terminal + image + "完成", projection.content)
        assertUnchanged(projection.content)
        assertTrue(AssistantReplayHistoryProjector.project(toolCall + intermediate + image).truncated)
    }

    @Test
    fun attachmentsDoNotExcuseTextMissingResultsOrMismatchedIdentity() {
        val image = "<link type=\"image\" id=\"page-1\"></link>"
        val first = call("one", "1") + call("two", "2") + result("one", "1")
        listOf(
            first + image + "unexpected text" + result("two", "2"),
            first + image + result("two", "wrong"),
            first + image + call("three", "3"),
            call("one", "1") + image + result("one", "1"),
        ).forEach { assertTrue(AssistantReplayHistoryProjector.project(it).truncated) }
    }

    private fun assertUnchanged(content: String) {
        val projection = AssistantReplayHistoryProjector.project(content)
        assertFalse(projection.changed)
        assertFalse(projection.truncated)
        assertEquals(content, projection.content)
        assertEquals(0, projection.removedCharacterCount)
        assertEquals(0, projection.reorderedTransactionCount)
    }

    private fun call(name: String, callId: String?): String {
        val callIdAttribute = callId?.let { " provider_call_id=\"$it\"" }.orEmpty()
        return "<tool_exec name=\"$name\"$callIdAttribute><param name=\"x\">1</param></tool_exec>"
    }

    private fun result(
        name: String,
        callId: String?,
        payload: String = "ok",
        providerToolName: String? = null,
        providerResultTerminal: Boolean? = null,
    ): String {
        val callIdAttribute = callId?.let { " provider_call_id=\"$it\"" }.orEmpty()
        val providerToolNameAttribute =
            providerToolName?.let { " provider_tool_name=\"$it\"" }.orEmpty()
        val providerResultTerminalAttribute =
            providerResultTerminal?.let { " provider_result_terminal=\"$it\"" }.orEmpty()
        return "<tool_result_exec name=\"$name\"$providerToolNameAttribute$callIdAttribute " +
            "status=\"success\"$providerResultTerminalAttribute>" +
            "<content>$payload</content></tool_result_exec>"
    }
}
