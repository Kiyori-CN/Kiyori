package com.ai.assistance.operit.ui.features.chat.details

import org.junit.Assert.*
import org.junit.Test

class AuditPagedTextTest {
    @Test fun pagesPreserveTextAndUnicodeAtBoundaries() {
        val text = "x".repeat(AUDIT_TEXT_PAGE_SIZE - 1) + "😀" + "y".repeat(AUDIT_TEXT_PAGE_SIZE) + "尾"
        val pages = (0..2).map { auditTextPage(text, it) }
        assertEquals(text, pages.joinToString(""))
        assertTrue(pages.all { it.length <= AUDIT_TEXT_PAGE_SIZE + 1 })
        assertFalse(pages.any { it.firstOrNull()?.isLowSurrogate() == true || it.lastOrNull()?.isHighSurrogate() == true })
    }
    @Test fun emptyAndExactBoundaryTextRemainReadable() {
        assertEquals("", auditTextPage("", 0))
        val text = "a".repeat(AUDIT_TEXT_PAGE_SIZE)
        assertEquals(text, auditTextPage(text, 0))
        assertEquals("", auditTextPage(text, 1))
    }
}
