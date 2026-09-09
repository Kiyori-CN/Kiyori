package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageSelectionTest {
    @Test fun `prepend and deletion preserve selected message identity`() {
        val selected = setOf(30L)
        assertEquals(setOf(1), projectSelectedMessageIndices(listOf(20L, 30L), selected))
        assertEquals(setOf(2), projectSelectedMessageIndices(listOf(10L, 20L, 30L), selected))
        assertEquals(setOf(0), projectSelectedMessageIndices(listOf(30L, 40L), selected))
    }

    @Test fun `evicted or deleted selections never select a replacement row`() {
        assertEquals(emptySet<Int>(), projectSelectedMessageIndices(listOf(40L, 50L), setOf(30L)))
        assertEquals(setOf(0), projectSelectedMessageIndices(listOf(30L, 40L), setOf(30L, 50L)))
    }
}
