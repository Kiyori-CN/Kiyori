package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.audit.ConversationAuditHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolResultPrunerTest {
    @Test
    fun smallToolResultKeepsItsOriginalMarkupAndIsNotPruned() {
        val content =
            """<tool_result_A1 name="shell" provider_call_id="call_1" status="success"><content>ok</content></tool_result_A1>"""

        val result = ConversationToolResultPruner.pruneContent(content)

        assertEquals(content, result.content)
        assertEquals(1, result.report.inspectedResultCount)
        assertEquals(0, result.report.prunedResultCount)
        assertEquals(2L, result.report.originalPayloadChars)
        assertEquals(2L, result.report.projectedPayloadChars)
    }

    @Test
    fun largeToolResultUsesDeterministicHeadMiddleTailProjection() {
        val payload =
            "HEAD".repeat(3_000) +
                "MIDDLE".repeat(2_000) +
                "TAIL".repeat(2_000)
        val content =
            """<tool_result_A1 name="shell" provider_call_id="call_1" status="success"><content>$payload</content></tool_result_A1>"""

        val result = ConversationToolResultPruner.pruneContent(content)
        val digest = ConversationAuditHasher.sha256(payload)

        assertTrue(result.content.contains("""name="shell""""))
        assertTrue(result.content.contains("""provider_call_id="call_1""""))
        assertTrue(result.content.contains("[tool_result_pruned"))
        assertTrue(result.content.contains("sha256=$digest"))
        assertTrue(result.content.contains(payload.take(8_192)))
        assertTrue(result.content.contains(payload.takeLast(4_096)))
        assertFalse(result.content.contains(payload))
        assertEquals(1, result.report.inspectedResultCount)
        assertEquals(1, result.report.prunedResultCount)
        assertEquals(payload.length.toLong(), result.report.originalPayloadChars)
        assertEquals(
            result.report.originalPayloadChars - result.report.omittedPayloadChars,
            result.report.projectedPayloadChars,
        )
    }

    @Test
    fun multipleToolResultsAggregateWithoutChangingUnrelatedText() {
        val first = "a".repeat(ConversationToolResultPruner.MAX_PAYLOAD_CHARS + 1)
        val second = "b".repeat(ConversationToolResultPruner.MAX_PAYLOAD_CHARS + 1)
        val content =
            "prefix " +
                """<tool_result_A1 name="first" status="success"><content>$first</content></tool_result_A1>""" +
                " middle " +
                """<tool_result_A2 name="second" status="success"><content>$second</content></tool_result_A2>""" +
                " suffix"

        val result = ConversationToolResultPruner.pruneContent(content)

        assertTrue(result.content.startsWith("prefix "))
        assertTrue(result.content.endsWith(" suffix"))
        assertEquals(2, result.report.inspectedResultCount)
        assertEquals(2, result.report.prunedResultCount)
        assertTrue(result.report.omittedPayloadChars > 0L)
        assertTrue(
            result.report.projectedPayloadChars < result.report.originalPayloadChars
        )
    }
}
