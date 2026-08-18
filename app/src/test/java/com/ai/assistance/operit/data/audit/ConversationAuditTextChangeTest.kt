package com.ai.assistance.operit.data.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAuditTextChangeTest {
    @Test
    fun appendOnlySnapshotProducesSuffixDelta() {
        val change = requireNotNull(resolveConversationAuditTextChange("hello", "hello world"))

        assertEquals("PROVIDER_TEXT_DELTA", change.eventType)
        assertEquals("text_delta", change.payloadLabel)
        assertEquals(" world", change.value)
        assertFalse(change.isRevision)
    }

    @Test
    fun rewrittenPrefixProducesFullRevisionEvenWhenLengthGrows() {
        val change = requireNotNull(resolveConversationAuditTextChange("hello", "hullo world"))

        assertEquals("PROVIDER_TEXT_REVISION", change.eventType)
        assertEquals("text_revision", change.payloadLabel)
        assertEquals("hullo world", change.value)
        assertTrue(change.isRevision)
    }

    @Test
    fun shortenedSnapshotProducesFullRevision() {
        val change = requireNotNull(resolveConversationAuditTextChange("hello world", "hello"))

        assertEquals("PROVIDER_TEXT_REVISION", change.eventType)
        assertEquals("hello", change.value)
        assertTrue(change.isRevision)
    }

    @Test
    fun unchangedSnapshotProducesNoEvent() {
        assertNull(resolveConversationAuditTextChange("same", "same"))
    }
}
