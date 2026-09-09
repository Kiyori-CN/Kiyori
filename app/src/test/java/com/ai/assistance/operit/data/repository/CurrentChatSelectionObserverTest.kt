package com.ai.assistance.operit.data.repository

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentChatSelectionObserverTest {
    @Test fun initialReadFailureIsNotAnEmptySelectionAndDoesNotRetryItself() = runTest {
        var reads = 0
        val observer = CurrentChatSelectionObserver(backgroundScope) {
            flow { reads++; throw IOException("private path") }
        }
        assertSame(ChatSelectionReadState.Loading, observer.state.value)
        runCurrent()
        val failure = observer.state.value as ChatSelectionReadState.Failed
        assertEquals("IOException", failure.errorType)
        runCurrent()
        assertEquals(1, reads)
        assertSame(failure, observer.state.value)
    }

    @Test fun failureAfterValidSelectionPreservesLastWindowProjection() = runTest {
        val observer = CurrentChatSelectionObserver(backgroundScope) {
            flow { emit("chat-a"); throw IOException() }
        }
        runCurrent()
        assertEquals("chat-a", observer.currentChatId.value)
        assertTrue(observer.state.value is ChatSelectionReadState.Failed)
    }

    @Test fun retryConsumesOnlyItsFailureAndReadsTheRealNewValue() = runTest {
        var reads = 0
        val observer = CurrentChatSelectionObserver(backgroundScope) {
            flow { if (++reads == 1) throw IOException() else emit("chat-b") }
        }
        runCurrent()
        val first = observer.state.value as ChatSelectionReadState.Failed
        observer.retry(first)
        observer.retry(first)
        runCurrent()
        assertEquals(2, reads)
        assertEquals(ChatSelectionReadState.Ready("chat-b"), observer.state.value)
        assertEquals("chat-b", observer.currentChatId.value)
    }

    @Test fun oldRetryCannotConsumeANewerReadFailure() = runTest {
        var reads = 0
        val observer = CurrentChatSelectionObserver(backgroundScope) {
            flow { reads++; throw IOException() }
        }
        runCurrent()
        val first = observer.state.value as ChatSelectionReadState.Failed
        observer.retry(first)
        runCurrent()
        val second = observer.state.value
        observer.retry(first)
        runCurrent()
        assertEquals(2, reads)
        assertSame(second, observer.state.value)
    }

    @Test fun realNullClearsSelectionAndContinuesObservingLaterWrites() = runTest {
        val source = MutableSharedFlow<String?>()
        val observer = CurrentChatSelectionObserver(backgroundScope) { source }
        runCurrent()
        source.emit("chat-a")
        runCurrent()
        source.emit(null)
        runCurrent()
        assertNull(observer.currentChatId.value)
        assertEquals(ChatSelectionReadState.Ready(null), observer.state.value)
        source.emit("chat-b")
        runCurrent()
        assertEquals("chat-b", observer.currentChatId.value)
    }

    @Test fun cancelledObservationDoesNotProduceRecoverableFailure() = runTest {
        val observer = CurrentChatSelectionObserver(backgroundScope) {
            flow { emit("chat-a"); throw CancellationException() }
        }
        runCurrent()
        assertEquals(ChatSelectionReadState.Ready("chat-a"), observer.state.value)
    }
}
