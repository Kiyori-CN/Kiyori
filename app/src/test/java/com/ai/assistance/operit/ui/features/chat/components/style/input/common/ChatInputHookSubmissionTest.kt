package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock

class ChatInputHookSubmissionTest {
    private val context = mock<Context>()
    private fun input() = ChatInputHookContext(context, ChatInputEvents.SUBMIT_REQUESTED, text = "draft")
    private fun hook(name: String, action: suspend (ChatInputHookContext) -> ChatInputHookResult?) =
        object : ChatInputHook {
            override val id = "test-$name"
            override suspend fun onEvent(context: ChatInputHookContext) = action(context)
        }

    @Test fun pluginFailureStopsFollowingHooksWithoutExposingOriginalError() = runTest {
        var followingCalls = 0
        val failed = hook("failure") { throw IllegalArgumentException("private draft detail") }
        val following = hook("following") { followingCalls++; null }
        ChatInputHookRegistry.register(failed)
        ChatInputHookRegistry.register(following)
        mockStatic(Log::class.java).use {
            try {
                try {
                    ChatInputHookRegistry.dispatchSubmitRequested(input())
                    fail("Expected submission failure")
                } catch (failure: ChatInputSubmitHookException) {
                    assertFalse(failure.message.orEmpty().contains("private draft"))
                    assertNull(failure.cause)
                }
                assertEquals(0, followingCalls)
            } finally {
                ChatInputHookRegistry.unregister(failed.id)
                ChatInputHookRegistry.unregister(following.id)
            }
        }
    }

    @Test fun cancellationIsPropagatedUnchanged() = runTest {
        val cancelled = CancellationException("cancel")
        val registered = hook("cancel") { throw cancelled }
        ChatInputHookRegistry.register(registered)
        try {
            try {
                ChatInputHookRegistry.dispatchSubmitRequested(input())
                fail("Expected cancellation")
            } catch (actual: CancellationException) {
                assertSame(cancelled, actual)
            }
        } finally { ChatInputHookRegistry.unregister(registered.id) }
    }

    @Test fun replacementStillFlowsThroughSubsequentExplicitApproval() = runTest {
        val replacement = hook("replacement") { ChatInputHookResult(ChatInputSubmitActions.REPLACE, "edited") }
        var followingText: String? = null
        val approval = hook("approval") { followingText = it.text; null }
        ChatInputHookRegistry.register(replacement)
        ChatInputHookRegistry.register(approval)
        try {
            val result = ChatInputHookRegistry.dispatchSubmitRequested(input())
            assertEquals("edited", followingText)
            assertEquals("edited", result.text)
            assertEquals(ChatInputSubmitActions.ALLOW, result.action)
        } finally {
            ChatInputHookRegistry.unregister(replacement.id)
            ChatInputHookRegistry.unregister(approval.id)
        }
    }
}
