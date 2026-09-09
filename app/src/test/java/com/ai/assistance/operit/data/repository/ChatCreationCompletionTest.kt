package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatCreationCompletionTest {
    private val chat = ChatHistory(id = "created", title = "group", messages = emptyList())

    @Test fun waitsForCreationBeforeSelectingOrReportingSuccess() = runTest {
        val creation = CompletableDeferred<ChatHistory>()
        var selected = false
        val result = async { completeChatCreation({ creation.await() }, { selected = true; true }) }
        runCurrent()
        assertFalse(selected)
        assertFalse(result.isCompleted)
        creation.complete(chat)
        assertEquals(ChatGroupCreationResult(chat.id, true), result.await())
    }

    @Test fun createFailureIsNotReportedAsAnExistingChat() = runTest {
        val original = IllegalStateException("create failed")
        try {
            completeChatCreation({ throw original }, { fail("must not select"); false })
            fail("expected failure")
        } catch (failure: IllegalStateException) { assertSame(original, failure) }
    }

    @Test fun changedSelectionReportsCreatedWithoutReplacingIt() = runTest {
        assertEquals(ChatGroupCreationResult(chat.id, false), completeChatCreation({ chat }, { false }))
    }

    @Test fun selectionFailureKeepsCreatedIdentityWithoutPrivateCause() = runTest {
        try {
            completeChatCreation({ chat }, { throw IllegalStateException("private storage detail") })
            fail("expected partial completion")
        } catch (partial: ChatCreationSelectionException) {
            assertEquals(chat.id, partial.createdChatId)
            assertEquals("IllegalStateException", partial.failureType)
            assertNull(partial.cause)
            assertFalse(partial.message.orEmpty().contains("private storage detail"))
        }
    }

    @Test fun cancellationBeforeCreationRemainsCancellation() = runTest {
        val cancelled = CancellationException("cancelled")
        try {
            completeChatCreation({ throw cancelled }, { fail(); false })
            fail("expected cancellation")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }

    @Test fun cancellationAfterCreationIsNotAnOrdinaryFailure() = runTest {
        val cancelled = CancellationException("cancelled")
        try {
            completeChatCreation({ chat }, { throw cancelled })
            fail("expected cancellation")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
}
