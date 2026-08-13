package com.ai.assistance.operit.api.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIForegroundServiceCancellationTest {
    @Test
    fun serviceScopeCancellation_isRethrownWithoutUnexpectedFailureCallback() = runBlocking {
        val observerEntered = CompletableDeferred<Unit>()
        val unexpectedFailures = mutableListOf<Exception>()

        val observerJob =
            launch {
                runServicePreferenceObserver(
                    observe = {
                        observerEntered.complete(Unit)
                        awaitCancellation()
                    },
                    onUnexpectedFailure = { unexpectedFailures += it },
                )
            }

        observerEntered.await()
        observerJob.cancelAndJoin()

        assertTrue(observerJob.isCancelled)
        assertTrue(unexpectedFailures.isEmpty())
    }

    @Test
    fun nonCancellationFailure_isReportedWithOriginalException() = runBlocking {
        val expected = IllegalStateException("preference flow failed")
        var reported: Exception? = null

        runServicePreferenceObserver(
            observe = { throw expected },
            onUnexpectedFailure = { reported = it },
        )

        assertEquals(expected, reported)
    }
}
