package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingQueueSubmissionTest {
    @Test fun cancelledScopeRestoresEvenBeforeBodyStarts() = runTest {
        val parent = Job().apply { cancel() }
        var restored = 0
        var started = false
        launchPendingQueueSubmission(CoroutineScope(coroutineContext + parent), { restored++ }, { _, _ -> fail() }) {
            started = true
        }.join()
        assertFalse(started)
        assertEquals(1, restored)
    }

    @Test fun cancellationWhilePluginIsSuspendedRestoresExactlyOnce() = runTest {
        var restored = 0
        val job = launchPendingQueueSubmission(this, { restored++ }, { _, _ -> fail() }) { awaitCancellation() }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(1, restored)
    }

    @Test fun dispatchBoundaryPreventsRestoringAnUnknownSubmission() = runTest {
        var restored = 0
        val job = launchPendingQueueSubmission(this, { restored++ }, { _, _ -> fail() }) { mark ->
            mark()
            awaitCancellation()
        }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(0, restored)
    }

    @Test fun rejectedPreparationReportsFailureAndRestores() = runTest {
        var restored = 0
        val failure = IllegalStateException("test failure")
        var reported: Exception? = null
        launchPendingQueueSubmission(this, { restored++ }, { error, dispatched ->
            assertFalse(dispatched)
            reported = error
        }) { throw failure }.join()
        assertSame(failure, reported)
        assertEquals(1, restored)
    }

    @Test fun explicitPluginConsumptionDoesNotRestore() = runTest {
        var restored = 0
        launchPendingQueueSubmission(this, { restored++ }, { _, _ -> fail() }) { mark -> mark() }.join()
        assertEquals(0, restored)
    }

    @Test fun failureAfterDispatchReportsUnconfirmedResultWithoutRestoring() = runTest {
        var restored = 0
        var reported = false
        launchPendingQueueSubmission(this, { restored++ }, { _, dispatched ->
            assertTrue(dispatched)
            reported = true
        }) { mark ->
            mark()
            throw IllegalStateException("after dispatch")
        }.join()
        assertTrue(reported)
        assertEquals(0, restored)
    }
}
