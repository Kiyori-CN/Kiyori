package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationAuditCompletenessPolicyTest {
    @Test
    fun `native live audit can move between in progress and complete`() {
        assertEquals(
            ConversationAuditCompletenessStatus.IN_PROGRESS,
            ConversationAuditCompletenessPolicy.merge(
                current = ConversationAuditCompletenessStatus.COMPLETE,
                requested = ConversationAuditCompletenessStatus.IN_PROGRESS,
            ),
        )
        assertEquals(
            ConversationAuditCompletenessStatus.COMPLETE,
            ConversationAuditCompletenessPolicy.merge(
                current = ConversationAuditCompletenessStatus.IN_PROGRESS,
                requested = ConversationAuditCompletenessStatus.COMPLETE,
            ),
        )
    }

    @Test
    fun `later successful events cannot erase historical or imported incompleteness`() {
        assertEquals(
            ConversationAuditCompletenessStatus.BASIC,
            ConversationAuditCompletenessPolicy.merge(
                current = ConversationAuditCompletenessStatus.BASIC,
                requested = ConversationAuditCompletenessStatus.COMPLETE,
            ),
        )
        assertEquals(
            ConversationAuditCompletenessStatus.SOURCE_UNVERIFIED,
            ConversationAuditCompletenessPolicy.merge(
                current = ConversationAuditCompletenessStatus.SOURCE_UNVERIFIED,
                requested = ConversationAuditCompletenessStatus.IN_PROGRESS,
            ),
        )
    }

    @Test
    fun `a more severe integrity failure still advances the status`() {
        assertEquals(
            ConversationAuditCompletenessStatus.CONTENT_CORRUPTED,
            ConversationAuditCompletenessPolicy.merge(
                current = ConversationAuditCompletenessStatus.PARTIAL,
                requested = ConversationAuditCompletenessStatus.CONTENT_CORRUPTED,
            ),
        )
    }
}
