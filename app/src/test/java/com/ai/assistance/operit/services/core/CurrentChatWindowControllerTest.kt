package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class CurrentChatWindowControllerTest {
    private val chatId = MutableStateFlow<String?>("a")
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val window = CurrentChatWindowController(chatId, messages)
    private fun message(timestamp: Long, content: String = "message") =
        ChatMessage(sender = "ai", content = content, timestamp = timestamp)
    private fun result(vararg items: ChatMessage) = CurrentChatWindowLoadResult(items.toList(), false, false)
    private fun begin(operation: ChatWindowLoadOperation = ChatWindowLoadOperation.SELECT) =
        requireNotNull(window.beginLoad(requireNotNull(chatId.value), operation))

    @Test fun switchClearsOldMessagesAndRejectsOldResult() {
        window.applyMessages("a", listOf(message(1)))
        val old = begin()
        window.selectChat("b")
        assertTrue(messages.value.isEmpty())
        assertFalse(window.applyLoadResult(old, result(message(2))))
        assertEquals("b", chatId.value)
    }

    @Test fun returningToSameChatDoesNotReviveAnOldRequest() {
        val old = begin()
        window.selectChat("b")
        window.selectChat("a")
        val latest = begin()
        assertFalse(window.applyLoadResult(old, result(message(1))))
        assertTrue(window.isLoadingDisplayWindow.value)
        assertTrue(window.applyLoadResult(latest, result(message(2))))
        assertEquals(listOf(message(2)), messages.value)
    }

    @Test fun oldSuccessFailureAndCancellationCannotSettleReplacement() {
        val old = begin()
        val latest = requireNotNull(window.beginLoad("a", ChatWindowLoadOperation.REFRESH, replace = true))
        assertFalse(window.applyLoadResult(old, result(message(1))))
        window.finishLoad(old, failed = true)
        window.finishLoad(old, failed = false)
        assertTrue(window.isLoadingDisplayWindow.value)
        assertNull(window.loadFailure.value)
        assertTrue(window.applyLoadResult(latest, result(message(2))))
    }

    @Test fun duplicatePagingCannotStartAnotherRead() {
        begin(ChatWindowLoadOperation.OLDER)
        assertNull(window.beginLoad("a", ChatWindowLoadOperation.NEWER))
        assertNull(window.beginLoad("b", ChatWindowLoadOperation.SELECT, replace = true))
        assertTrue(window.isLoadingDisplayWindow.value)
    }

    @Test fun runtimeUpdateKeepsNewContentAndInvalidatesBorrowedRead() {
        window.applyMessages("a", listOf(message(1, "initial")))
        val old = begin(ChatWindowLoadOperation.OLDER)
        window.applyMessages("a", listOf(message(1, "streamed")))
        assertFalse(window.applyLoadResult(old, result(message(1, "stale"))))
        window.finishLoad(old, failed = false)
        assertEquals("streamed", messages.value.single().content)
        assertTrue(requireNotNull(window.loadFailure.value).historyChanged)
        assertEquals(ChatWindowLoadOperation.OLDER, window.loadFailure.value?.operation)
        assertFalse(window.isLoadingDisplayWindow.value)
    }

    @Test fun staleOpeningAndFlagsCannotOverwriteRuntimeUpdate() {
        window.applyMessages("a", listOf(message(1)))
        val snapshot = requireNotNull(window.snapshot("a"))
        window.applyMessages("a", listOf(message(2)), hasOlderPersistedHistory = false)
        assertFalse(window.applySnapshot(snapshot, listOf(message(3))))
        window.applyFlags(snapshot, CurrentChatWindowLoadResult(snapshot.messages, true, true))
        assertEquals(listOf(message(2)), messages.value)
        assertFalse(window.hasOlderDisplayHistory.value)
        assertFalse(window.hasNewerDisplayHistory.value)
    }

    @Test fun staleSnapshotCannotPublishAfterRoundTripSelection() {
        val snapshot = requireNotNull(window.snapshot("a"))
        window.selectChat("b")
        window.selectChat("a")
        assertFalse(window.applySnapshot(snapshot, listOf(message(1))))
        window.reportFailure(snapshot, ChatWindowLoadOperation.SELECT)
        assertNull(window.loadFailure.value)
    }

    @Test fun cancellationEndsOnlyItsRequestWithoutOrdinaryFailure() {
        val request = begin()
        window.finishLoad(request, failed = false)
        assertFalse(window.isLoadingDisplayWindow.value)
        assertNull(window.loadFailure.value)
        assertFalse(window.applyLoadResult(request, result(message(1))))
    }

    @Test fun retrySuccessClearsFailureAndUpdatesFlags() {
        val failed = begin()
        window.finishLoad(failed, failed = true)
        assertFalse(window.isLoadingDisplayWindow.value)
        assertNotNull(window.loadFailure.value)
        val retry = begin()
        assertNull(window.loadFailure.value)
        assertTrue(window.applyLoadResult(retry, CurrentChatWindowLoadResult(listOf(message(2)), true, true)))
        assertFalse(window.isLoadingDisplayWindow.value)
        assertTrue(window.hasOlderDisplayHistory.value)
        assertTrue(window.hasNewerDisplayHistory.value)
    }

    @Test fun lateDismissCannotConsumeRepeatedFailure() {
        window.finishLoad(begin(), failed = true)
        val old = requireNotNull(window.loadFailure.value)
        window.finishLoad(begin(), failed = true)
        val latest = requireNotNull(window.loadFailure.value)
        assertFalse(window.dismissFailure(old))
        assertSame(latest, window.loadFailure.value)
        assertTrue(window.dismissFailure(latest))
        assertNull(window.loadFailure.value)
    }

    @Test fun nullSelectionClearsMessagesFlagsAndLoading() {
        window.applyMessages("a", listOf(message(1)), true, true)
        val request = begin()
        window.selectChat(null)
        assertNull(chatId.value)
        assertTrue(messages.value.isEmpty())
        assertFalse(window.isLoadingDisplayWindow.value)
        assertFalse(window.hasOlderDisplayHistory.value)
        assertFalse(window.hasNewerDisplayHistory.value)
        assertFalse(window.applyMessages("a", listOf(message(2))))
        window.finishLoad(request, failed = true)
        assertNull(window.loadFailure.value)
    }

    @Test fun retryClaimsFailureOnceAndKeepsOriginalTarget() {
        val failed = requireNotNull(window.beginLoad("a", ChatWindowLoadOperation.LOCATE, 123))
        window.finishLoad(failed, failed = true)
        val failure = requireNotNull(window.loadFailure.value)
        val retry = requireNotNull(window.beginRetry(failure))
        assertEquals("a", retry.chatId)
        assertEquals(123L, retry.targetTimestamp)
        assertEquals(ChatWindowLoadOperation.LOCATE, retry.operation)
        assertNull(window.beginRetry(failure))
        window.selectChat("b")
        assertFalse(window.applyLoadResult(retry, result(message(123))))
        assertTrue(messages.value.isEmpty())
    }

    @Test fun clearingHistoryInvalidatesPendingRead() {
        val old = begin()
        window.clearMessages()
        assertFalse(window.applyLoadResult(old, result(message(1))))
        assertTrue(messages.value.isEmpty())
        assertFalse(window.isLoadingDisplayWindow.value)
    }

    @Test fun readBorrowsMessagesAndBoundaryFlagsTogether() {
        window.applyMessages("a", listOf(message(1)), true, false)
        val request = begin()
        window.applyMessages("a", listOf(message(2)), false, true)
        assertEquals(listOf(message(1)), request.messages)
        assertTrue(request.hasOlderHistory)
        assertFalse(request.hasNewerHistory)
    }
}
