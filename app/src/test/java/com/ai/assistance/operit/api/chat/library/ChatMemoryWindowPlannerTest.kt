package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryWindowPlannerTest {
    @Test
    fun `plans ordered nonblank user and assistant messages`() {
        val messages = listOf(
            ChatMessage(sender = "ai", content = "answer", timestamp = 3),
            ChatMessage(sender = "system", content = "ignore", timestamp = 0),
            ChatMessage(sender = "user", content = "question", timestamp = 1),
            ChatMessage(sender = "user", content = "", timestamp = 2),
        )

        val windows = ChatMemoryWindowPlanner.plan(messages, 8)

        assertEquals(listOf("question", "answer"), windows.single().messages.map { it.content })
        assertEquals(2, windows.single().sourceMessageCount)
    }

    @Test
    fun `clamps window size and retains every source message exactly once`() {
        val messages = (0 until 17).map { index ->
            ChatMessage(
                sender = if (index % 2 == 0) "user" else "assistant",
                content = "message-$index",
                timestamp = index.toLong(),
            )
        }

        val windows = ChatMemoryWindowPlanner.plan(messages, 1)
        val retained = windows.flatMap { it.messages }.map { it.content }

        assertEquals(messages.map { it.content }, retained)
        assertTrue(windows.all { it.sourceMessageCount <= ChatMemoryWindowPlanner.MIN_WINDOW_MESSAGE_COUNT })
    }

    @Test
    fun `hidden placeholder and unsupported messages are excluded`() {
        val messages = listOf(
            ChatMessage(
                sender = "user",
                content = "hidden",
                timestamp = 1,
                displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER,
            ),
            ChatMessage(sender = "user", content = "visible", timestamp = 2),
            ChatMessage(sender = "tool", content = "tool output", timestamp = 3),
        )

        val windows = ChatMemoryWindowPlanner.plan(messages, 32)

        assertEquals(listOf("visible"), windows.single().messages.map { it.content })
    }

    @Test
    fun `repeats the turn user as context when assistant replies exceed a window`() {
        val messages = listOf(ChatMessage(sender = "user", content = "question", timestamp = 0)) +
            (1..10).map { index ->
                ChatMessage(sender = "assistant", content = "answer-$index", timestamp = index.toLong())
            }

        val windows = ChatMemoryWindowPlanner.plan(messages, 8)

        assertEquals(2, windows.size)
        assertEquals(8, windows[0].sourceMessageCount)
        assertEquals(4, windows[1].messages.size)
        assertEquals(3, windows[1].sourceMessageCount)
        assertEquals("question", windows[1].messages.first().content)
        assertEquals(11, windows.sumOf { it.sourceMessageCount })
    }

    @Test
    fun `starts a new window at the next user turn when the current window is full`() {
        val messages = (0 until 6).flatMap { turn ->
            listOf(
                ChatMessage(sender = "user", content = "user-$turn", timestamp = (turn * 2).toLong()),
                ChatMessage(sender = "assistant", content = "assistant-$turn", timestamp = (turn * 2 + 1).toLong()),
            )
        }

        val windows = ChatMemoryWindowPlanner.plan(messages, 8)

        assertEquals(2, windows.size)
        assertEquals(listOf("user-0", "user-1", "user-2", "user-3"), windows[0].messages.filter { it.sender == "user" }.map { it.content })
        assertEquals(listOf("user-4", "user-5"), windows[1].messages.filter { it.sender == "user" }.map { it.content })
        assertFalse(windows[0].messages.any { it.content == "user-4" })
        assertEquals(messages.size, windows.sumOf { it.sourceMessageCount })
    }

    @Test
    fun `hidden summary placeholders do not shift source window boundaries`() {
        val visible = (0 until 10).flatMap { turn ->
            listOf(
                ChatMessage(sender = "user", content = "user-$turn", timestamp = (turn * 2).toLong()),
                ChatMessage(sender = "assistant", content = "assistant-$turn", timestamp = (turn * 2 + 1).toLong()),
            )
        }
        val withPlaceholders = visible.flatMap { message ->
            listOf(
                message,
                ChatMessage(
                    sender = "assistant",
                    content = "summary-${message.timestamp}",
                    timestamp = message.timestamp + 100,
                    displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER,
                ),
            )
        }

        val expected = ChatMemoryWindowPlanner.plan(visible, 8)
        val actual = ChatMemoryWindowPlanner.plan(withPlaceholders, 8)

        assertEquals(expected.map { it.sourceMessageCount }, actual.map { it.sourceMessageCount })
        assertEquals(expected.map { it.messages.map(ChatMessage::content) }, actual.map { it.messages.map(ChatMessage::content) })
    }

    @Test
    fun `plans a 2000 turn chat without losing source messages`() {
        val messages = (0 until 2000).flatMap { turn ->
            listOf(
                ChatMessage(sender = "user", content = "user-$turn", timestamp = (turn * 2).toLong()),
                ChatMessage(sender = "assistant", content = "assistant-$turn", timestamp = (turn * 2 + 1).toLong()),
            )
        }

        val windows = ChatMemoryWindowPlanner.plan(messages, 32)

        assertEquals(messages.size, windows.sumOf { it.sourceMessageCount })
        assertEquals(125, windows.size)
        assertTrue(windows.all { it.sourceMessageCount in 1..32 })
    }
}
