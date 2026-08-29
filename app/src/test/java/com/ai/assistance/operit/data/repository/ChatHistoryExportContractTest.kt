package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryExportContractTest {
    @Test
    fun `selection keeps source order and excludes unselected chats`() {
        val histories = listOf(history("a"), history("b"), history("c"))

        val selected = selectChatHistoriesForExport(histories, setOf("c", "a", "missing"))

        assertEquals(listOf("a", "c"), selected.map { it.id })
        assertEquals(histories, selectChatHistoriesForExport(histories, null))
        assertTrue(selectChatHistoriesForExport(histories, emptySet()).isEmpty())
    }

    @Test
    fun `long text progress is monotonic and completes at one`() {
        val updates = mutableListOf<ChatExportProgress>()
        val reporter = ChatExportProgressReporter(
            totalCharacters = 10L,
            onProgress = updates::add,
            updateCharacterCount = 4L,
        )

        reporter.report(force = true)
        reporter.recordCharacters(3L)
        reporter.recordCharacters(1L)
        reporter.recordCharacters(20L)
        reporter.complete()

        assertEquals(listOf(0L, 4L, 10L), updates.map { it.processedCharacters })
        assertTrue(updates.zipWithNext().all { (left, right) -> left.progress <= right.progress })
        assertEquals(1f, updates.last().progress)
        assertEquals(10L, updates.last().totalCharacters)
    }

    private fun history(id: String): ChatHistory {
        return ChatHistory(id = id, title = id, messages = emptyList())
    }
}
