package com.ai.assistance.operit.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDeletionCompletionTest {
    @Test fun rejectedDeletionDoesNotRunCleanup() = runTest {
        assertFalse(completeChatDeletion({ false }, { fail("cleanup must not run") }))
    }

    @Test fun successfulDeletionWaitsForCleanup() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val result = async { completeChatDeletion({ true }, { cleanup.await() }) }
        runCurrent()
        assertFalse(result.isCompleted)
        cleanup.complete(Unit)
        assertTrue(result.await())
    }

    @Test fun failureBeforeDeletionIsNotReportedAsDeleted() = runTest {
        val original = IllegalStateException("database unavailable")
        try {
            completeChatDeletion({ throw original }, { fail("cleanup must not run") })
            fail("expected failure")
        } catch (failure: IllegalStateException) {
            assertSame(original, failure)
            assertFalse(failure is ChatDeletionCleanupException)
        }
    }

    @Test fun cleanupFailureReportsPartialCompletionWithoutSensitiveCause() = runTest {
        try {
            completeChatDeletion({ true }, { throw IllegalStateException("private file detail") })
            fail("expected partial completion")
        } catch (partial: ChatDeletionCleanupException) {
            assertEquals("IllegalStateException", partial.failureType)
            assertNull(partial.cause)
            assertFalse(partial.message.orEmpty().contains("private file detail"))
        }
    }

    @Test fun cancellationBeforeDeletionPropagatesWithoutCleanup() = runTest {
        val cancelled = CancellationException("cancelled")
        try {
            completeChatDeletion({ throw cancelled }, { fail("cleanup must not run") })
            fail("expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test fun cancellationDuringCleanupRemainsCancellation() = runTest {
        val cancelled = CancellationException("cancelled")
        try {
            completeChatDeletion({ true }, { throw cancelled })
            fail("expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
