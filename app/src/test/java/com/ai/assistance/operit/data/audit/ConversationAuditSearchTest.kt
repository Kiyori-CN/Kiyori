package com.ai.assistance.operit.data.audit

import org.junit.Assert.*
import org.junit.Test

class ConversationAuditSearchTest {
    @Test fun matchesMultipleTermsAcrossMetadataAndBody() {
        val match = ConversationAuditSearchQuery("SSH timeout").match(listOf("TOOL_CALL_FAILED ssh", "remote command TIMEOUT"))
        assertEquals("remote command TIMEOUT", match)
        assertNull(ConversationAuditSearchQuery("ssh missing").match(listOf("ssh timeout")))
    }

    @Test fun searchesLiteralRegexAndJsonCharacters() {
        assertEquals("[a-z]+ C:\\tmp", ConversationAuditSearchQuery("[a-z]+ C:\\tmp").match(listOf("[a-z]+ C:\\tmp")))
        assertNull(ConversationAuditSearchQuery("[a-z]+").match(listOf("abc")))
    }

    @Test fun excerptFindsDeepErrorsWithoutReturningFullPayload() {
        val result = requireNotNull(ConversationAuditSearchQuery("传输中断").match(listOf("x".repeat(10000) + "传输中断" + "y".repeat(10000))))
        assertTrue(result.contains("传输中断"))
        assertTrue(result.length <= 242)
    }

    @Test fun whitespaceDoesNotBecomeAnUnboundedSearch() {
        assertNull(ConversationAuditSearchQuery(" \n\t ").match(listOf("anything")))
        assertEquals("timeout", ConversationAuditSearchQuery(" timeout\n timeout ").match(listOf("timeout")))
    }

    @Test fun observationalExportCannotOverwriteActiveOrBrokenStatus() {
        com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus.entries.forEach { current ->
            assertEquals(current, ConversationAuditCompletenessPolicy.merge(current,
                com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus.COMPLETE, preserveCurrent = true))
        }
    }
}
