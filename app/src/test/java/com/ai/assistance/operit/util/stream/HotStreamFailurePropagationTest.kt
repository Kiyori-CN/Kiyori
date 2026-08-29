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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotStreamFailurePropagationTest {
    private class DiagnosticFailure(
        message: String,
        override val messageFailureExecutionId: String? = "execution-123456789",
        override val messageFailureDiagnosticCode: String = "LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED",
        override val messageFailurePhase: String = "WAITING_FOR_RESPONSE_HEADERS",
    ) : IllegalStateException(message), MessageFailureDiagnosticSource

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
        val secondaryFailure = CompletableDeferred<SecondaryStreamFailure>()
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
                        observation = {
                            SecondaryStreamObservation(
                                observerName = "test_secondary",
                                phase = "test_secondary_phase",
                                terminalOutcome = "main_stream_failure",
                                chunks = 0,
                                visibleChars = 0,
                            )
                        },
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
            assertEquals("test_secondary", observerFailure.observation.observerName)
            assertEquals("test_secondary_phase", observerFailure.observation.phase)
            assertEquals(0, observerFailure.observation.chunks)
            assertEquals(0, observerFailure.observation.visibleChars)
            assertEquals("IllegalStateException", observerFailure.diagnostics.failureType)
            assertFalse(observerFailure.format().contains(expected.message.orEmpty()))
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
                        observation = {
                            SecondaryStreamObservation(
                                observerName = "cancelled_secondary",
                                phase = "test_cancelled_phase",
                                terminalOutcome = "cancelled",
                                chunks = 0,
                                visibleChars = 0,
                            )
                        },
                        onFailure = { failure ->
                            reportedFailure.complete(IllegalStateException(failure.format()))
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

    @Test
    fun primaryOwner_logsCauseOnlyOnceAndMergesPropagationBoundaries() {
        val failure = DiagnosticFailure("private provider failure")

        val firstBoundary = recordPropagatedMessageFailure(
            boundaryName = "stream_builder",
            phase = "stream_collect",
            failure = failure,
        )
        val secondBoundary = recordPropagatedMessageFailure(
            boundaryName = "markdown_split",
            phase = "markdown_split",
            failure = failure,
        )
        val firstOwner = claimPrimaryMessageFailureOwner(failure, "message_owner")
        val secondOwner = claimPrimaryMessageFailureOwner(failure, "message_owner")

        assertEquals("on-123456789", firstBoundary.executionRef)
        assertEquals("LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED", firstBoundary.diagnosticCode)
        assertEquals("WAITING_FOR_RESPONSE_HEADERS", firstBoundary.phase)
        assertEquals(1, firstBoundary.propagationBoundaryCount)
        assertEquals(2, secondBoundary.propagationBoundaryCount)
        assertTrue(firstOwner.shouldLogCause)
        assertFalse(secondOwner.shouldLogCause)
        assertEquals(firstOwner.diagnostics.causeFingerprint, secondOwner.diagnostics.causeFingerprint)
        assertTrue(firstOwner.diagnostics.primaryOwnerClaimed)
    }

    @Test
    fun executionIdExtractor_usesNestedDiagnosticSourceWithoutGuessing() {
        val diagnosticFailure =
            DiagnosticFailure(
                message = "private provider failure",
                messageFailureExecutionId = "execution-last-provider-hop",
            )
        val wrapped = IllegalArgumentException("message owner wrapper", diagnosticFailure)

        assertEquals(
            "execution-last-provider-hop",
            extractMessageFailureExecutionId(wrapped),
        )
        assertEquals(
            null,
            extractMessageFailureExecutionId(IllegalStateException("no diagnostic identity")),
        )
        assertEquals(
            null,
            extractMessageFailureExecutionId(
                DiagnosticFailure(
                    message = "blank diagnostic identity",
                    messageFailureExecutionId = " ",
                )
            ),
        )
    }

    @Test
    fun logicalFailureKey_mergesDifferentWrappersWithTheSameExecutionAndPhase() {
        val source =
            DiagnosticFailure(
                message = "private provider failure",
                messageFailureExecutionId = "execution-wrapper-test",
            )
        val firstWrapper = IllegalStateException("first wrapper", source)
        val secondWrapper = IllegalArgumentException("second wrapper", source)

        val firstBoundary =
            recordPropagatedMessageFailure(
                boundaryName = "first_boundary",
                phase = "ignored_by_diagnostic_source",
                failure = firstWrapper,
            )
        val secondBoundary =
            recordPropagatedMessageFailure(
                boundaryName = "second_boundary",
                phase = "ignored_by_diagnostic_source",
                failure = secondWrapper,
            )
        val firstOwner = claimPrimaryMessageFailureOwner(firstWrapper, "message_owner")
        val secondOwner = claimPrimaryMessageFailureOwner(secondWrapper, "message_owner")

        assertEquals(1, firstBoundary.propagationBoundaryCount)
        assertEquals(2, secondBoundary.propagationBoundaryCount)
        assertTrue(firstOwner.shouldLogCause)
        assertFalse(secondOwner.shouldLogCause)
        assertEquals(
            firstOwner.diagnostics.causeFingerprint,
            secondOwner.diagnostics.causeFingerprint,
        )
    }

    @Test
    fun logicalFailureKey_keepsDifferentPhasesIndependent() {
        val headersFailure =
            DiagnosticFailure(
                message = "headers failure",
                messageFailureExecutionId = "execution-phase-test",
                messageFailurePhase = "WAITING_FOR_RESPONSE_HEADERS",
            )
        val bodyFailure =
            DiagnosticFailure(
                message = "body failure",
                messageFailureExecutionId = "execution-phase-test",
                messageFailurePhase = "RESPONSE_BODY",
            )

        val headersOwner = claimPrimaryMessageFailureOwner(headersFailure, "message_owner")
        val bodyOwner = claimPrimaryMessageFailureOwner(bodyFailure, "message_owner")

        assertTrue(headersOwner.shouldLogCause)
        assertTrue(bodyOwner.shouldLogCause)
        assertEquals("WAITING_FOR_RESPONSE_HEADERS", headersOwner.diagnostics.phase)
        assertEquals("RESPONSE_BODY", bodyOwner.diagnostics.phase)
    }

    @Test
    fun secondaryObserver_recordsObserverCountWithoutRetainingThrowableInSummary() = runBlocking {
        val expected = DiagnosticFailure("do not log this message")
        val reported = CompletableDeferred<SecondaryStreamFailure>()

        observeSecondaryStream(
            observation = {
                SecondaryStreamObservation(
                    observerName = "summary_only",
                    phase = "secondary_test",
                    terminalOutcome = "main_stream_failure",
                    chunks = 2,
                    visibleChars = 8,
                )
            },
            onFailure = { reported.complete(it) },
        ) {
            throw expected
        }

        val failure = reported.await()
        assertEquals(2, failure.observation.chunks)
        assertEquals(8, failure.observation.visibleChars)
        assertEquals(1, failure.diagnostics.secondaryObserverCount)
        assertFalse(failure.format().contains(expected.message.orEmpty()))
        assertNotNull(failure.diagnostics.causeFingerprint)
    }
}
