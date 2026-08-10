package com.ai.assistance.operit.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriStartupChatPolicyTest {
    @Test
    fun `fresh install creates the first real chat without a model requirement`() {
        assertTrue(
            shouldCreateKiyoriStartupChat(
                currentChatId = null,
                currentChatExists = false,
                startWithNewChat = false,
            ),
        )
    }

    @Test
    fun `a stale persisted current chat id is repaired with a new chat`() {
        assertTrue(
            shouldCreateKiyoriStartupChat(
                currentChatId = "missing-chat",
                currentChatExists = false,
                startWithNewChat = false,
            ),
        )
    }

    @Test
    fun `a valid current chat remains selected by default`() {
        assertFalse(
            shouldCreateKiyoriStartupChat(
                currentChatId = "existing-chat",
                currentChatExists = true,
                startWithNewChat = false,
            ),
        )
    }

    @Test
    fun `the explicit launch preference creates a new chat when one already exists`() {
        assertTrue(
            shouldCreateKiyoriStartupChat(
                currentChatId = "existing-chat",
                currentChatExists = true,
                startWithNewChat = true,
            ),
        )
    }
}
