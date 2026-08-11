package com.ai.assistance.operit.util.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotStreamFailurePropagationTest {
    @Test
    fun eagerlySharedStream_replaysUpstreamFailureWithoutCrashingOwnerScope() = runBlocking {
        val expected = IllegalStateException("response.created sequence mismatch")
        val uncaughtFailure = CompletableDeferred<Throwable>()
        val scope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.Unconfined +
                    CoroutineExceptionHandler { _, error ->
                        uncaughtFailure.complete(error)
                    }
            )
        val source =
            object : Stream<Int> {
                override val isLocked: Boolean = false
                override val bufferedCount: Int = 0

                override suspend fun lock() = Unit

                override suspend fun unlock() = Unit

                override fun clearBuffer() = Unit

                override suspend fun collect(collector: StreamCollector<Int>) {
                    throw expected
                }
            }

        try {
            val shared = source.share(scope = scope, replay = Int.MAX_VALUE)
            val collectorFailure =
                runCatching {
                    withTimeout(1_000L) {
                        shared.collect { }
                    }
                }.exceptionOrNull()

            assertTrue(collectorFailure is IllegalStateException)
            assertEquals(expected.message, collectorFailure?.message)
            assertFalse(uncaughtFailure.isCompleted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun messageOwnedCollector_transfersFailureWithoutCrashingCollectorScope() = runBlocking {
        val expected = IllegalStateException("upstream request failed")
        val uncaughtFailure = CompletableDeferred<Throwable>()
        val completion = CompletableDeferred<Throwable?>()
        val secondaryFailure = CompletableDeferred<Throwable>()
        val scope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.Unconfined +
                    CoroutineExceptionHandler { _, error ->
                        uncaughtFailure.complete(error)
                    }
            )
        val shared = MutableSharedStreamImpl<Int>()

        try {
            val collectorJob =
                scope.launch {
                    collectForMessageFailureOwner(completion) {
                        shared.collect { }
                    }
                }
            val observerJob =
                scope.launch {
                    observeSecondaryStream(
                        onFailure = { failure ->
                            secondaryFailure.complete(failure)
                        }
                    ) {
                        shared.collect { }
                    }
                }
            withTimeout(1_000L) {
                while (shared.subscriptionCount < 2) {
                    yield()
                }
            }
            shared.close(expected)
            val ownerFailure = withTimeout(1_000L) { completion.await() }
            val observerFailure = withTimeout(1_000L) { secondaryFailure.await() }
            withTimeout(1_000L) { collectorJob.join() }
            withTimeout(1_000L) { observerJob.join() }

            assertTrue(ownerFailure is IllegalStateException)
            assertEquals(expected.message, ownerFailure?.message)
            assertTrue(observerFailure is IllegalStateException)
            assertEquals(expected.message, observerFailure.message)
            assertFalse(collectorJob.isCancelled)
            assertFalse(observerJob.isCancelled)
            assertFalse(uncaughtFailure.isCompleted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun messageOwnedCollector_preservesCancellationWithoutUncaughtFailure() = runBlocking {
        val uncaughtFailure = CompletableDeferred<Throwable>()
        val completion = CompletableDeferred<Throwable?>()
        val collectorEntered = CompletableDeferred<Unit>()
        val scope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.Unconfined +
                    CoroutineExceptionHandler { _, error ->
                        uncaughtFailure.complete(error)
                    }
            )

        try {
            val collectorJob =
                scope.launch {
                    collectForMessageFailureOwner(completion) {
                        collectorEntered.complete(Unit)
                        awaitCancellation()
                    }
                }
            withTimeout(1_000L) { collectorEntered.await() }
            collectorJob.cancel()
            withTimeout(1_000L) { collectorJob.join() }
            val ownerFailure = withTimeout(1_000L) { completion.await() }

            assertTrue(ownerFailure is kotlinx.coroutines.CancellationException)
            assertTrue(collectorJob.isCancelled)
            assertFalse(uncaughtFailure.isCompleted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failureOwnedTask_awaitsFailureWithoutTriggeringUncaughtHandler() = runBlocking {
        val expected = IllegalStateException("tool follow-up failed")
        val uncaughtFailure = CompletableDeferred<Throwable>()
        val startedJob = CompletableDeferred<kotlinx.coroutines.Job>()
        val scope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.Unconfined +
                    CoroutineExceptionHandler { _, error ->
                        uncaughtFailure.complete(error)
                    }
        )

        try {
            val deferred =
                scope.async<Unit> {
                    throw expected
                }
            val failure =
                runCatching {
                    startedJob.complete(deferred)
                    awaitFailureOwnedTask(deferred)
                }.exceptionOrNull()

            assertTrue(startedJob.isCompleted)
            assertTrue(failure is IllegalStateException)
            assertEquals(expected.message, failure?.message)
            assertFalse(uncaughtFailure.isCompleted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failureOwnedTaskScope_survivesOneFailureAndRunsTheNextTask() = runBlocking {
        val scope = newFailureOwnedTaskScope(Dispatchers.Unconfined)

        try {
            val firstFailure =
                runCatching {
                    awaitFailureOwnedTask(
                        scope.async<Unit> {
                            throw IllegalStateException("first tool follow-up failed")
                        }
                    )
                }.exceptionOrNull()

            assertTrue(firstFailure is IllegalStateException)
            assertTrue(scope.coroutineContext[kotlinx.coroutines.Job]?.isActive == true)
            assertEquals(
                "second turn completed",
                awaitFailureOwnedTask(
                    scope.async {
                        "second turn completed"
                    }
                ),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun secondaryObserver_preservesCancellationWithoutReportingFailure() = runBlocking {
        val reportedFailure = CompletableDeferred<Throwable>()
        val observerEntered = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        try {
            val observerJob =
                scope.launch {
                    observeSecondaryStream(
                        onFailure = { failure ->
                            reportedFailure.complete(failure)
                        }
                    ) {
                        observerEntered.complete(Unit)
                        awaitCancellation()
                    }
                }
            withTimeout(1_000L) { observerEntered.await() }
            observerJob.cancel()
            withTimeout(1_000L) { observerJob.join() }

            assertTrue(observerJob.isCancelled)
            assertFalse(reportedFailure.isCompleted)
        } finally {
            scope.cancel()
        }
    }
}
