package com.ai.assistance.operit.api.chat.llmprovider

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAIHostedWebSearchAdmissionControllerTest {
    @Test
    fun fifoQueueAdmitsWaitersInRegistrationOrder() = runTest {
        val controller = controller()
        val first =
            controller.acquire(
                maxConcurrentRequests = 1,
                requestsPerMinute = 0,
                queueTimeoutMs = 10_000,
                lifecycle = lifecycle("first"),
            )
        val order = mutableListOf<String>()
        val second =
            async {
                controller.acquire(
                    maxConcurrentRequests = 1,
                    requestsPerMinute = 0,
                    queueTimeoutMs = 10_000,
                    lifecycle = lifecycle("second"),
                ).also { order += "second" }
            }
        val third =
            async {
                controller.acquire(
                    maxConcurrentRequests = 1,
                    requestsPerMinute = 0,
                    queueTimeoutMs = 10_000,
                    lifecycle = lifecycle("third"),
                ).also { order += "third" }
            }
        runCurrent()

        assertFalse(second.isCompleted)
        assertFalse(third.isCompleted)
        first.release()
        runCurrent()
        assertEquals(listOf("second"), order)
        assertFalse(third.isCompleted)

        second.await().release()
        runCurrent()
        assertEquals(listOf("second", "third"), order)
        third.await().release()
    }

    @Test
    fun queueTimeoutNeverAdmitsOrMarksSubmission() = runTest {
        val controller = controller()
        val first =
            controller.acquire(
                maxConcurrentRequests = 1,
                requestsPerMinute = 0,
                queueTimeoutMs = 10_000,
                lifecycle = lifecycle("first"),
            )
        val queuedLifecycle = lifecycle("queued")
        val queued =
            async {
                try {
                    controller.acquire(
                        maxConcurrentRequests = 1,
                        requestsPerMinute = 0,
                        queueTimeoutMs = 1_000,
                        lifecycle = queuedLifecycle,
                    )
                    throw AssertionError("Expected queue timeout")
                } catch (error: OpenAIHostedWebSearchException) {
                    error
                }
            }
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        val failure = queued.await()
        assertEquals(OpenAIHostedWebSearchErrorCode.QUEUE_TIMEOUT, failure.code)
        assertEquals("waiting_concurrency", failure.phase)
        assertEquals("not_sent", failure.submissionState)
        assertTrue(requireNotNull(failure.queueWaitMs) >= 1_000L)
        assertEquals(1, controller.snapshot().activeCount)
        assertEquals(0, controller.snapshot().queuedCount)
        first.release()
    }

    @Test
    fun loweringConcurrencyDoesNotInterruptActiveRequests() = runTest {
        val controller = controller()
        val first =
            controller.acquire(
                maxConcurrentRequests = 2,
                requestsPerMinute = 0,
                queueTimeoutMs = 10_000,
                lifecycle = lifecycle("first"),
            )
        val second =
            controller.acquire(
                maxConcurrentRequests = 2,
                requestsPerMinute = 0,
                queueTimeoutMs = 10_000,
                lifecycle = lifecycle("second"),
            )
        val third =
            async {
                controller.acquire(
                    maxConcurrentRequests = 1,
                    requestsPerMinute = 0,
                    queueTimeoutMs = 10_000,
                    lifecycle = lifecycle("third"),
                )
            }
        runCurrent()
        assertEquals(2, controller.snapshot().activeCount)
        assertEquals(1, controller.snapshot().queuedCount)

        first.release()
        runCurrent()
        assertFalse(third.isCompleted)
        assertEquals(1, controller.snapshot().activeCount)

        second.release()
        runCurrent()
        assertTrue(third.isCompleted)
        third.await().release()
    }

    @Test
    fun cancellingQueuedWaiterRemovesOnlyThatWaiter() = runTest {
        val controller = controller()
        val first =
            controller.acquire(
                maxConcurrentRequests = 1,
                requestsPerMinute = 0,
                queueTimeoutMs = 10_000,
                lifecycle = lifecycle("first"),
            )
        val cancelledLifecycle = lifecycle("cancelled")
        val cancelled =
            launch {
                controller.acquire(
                    maxConcurrentRequests = 1,
                    requestsPerMinute = 0,
                    queueTimeoutMs = 10_000,
                    lifecycle = cancelledLifecycle,
                ).release()
            }
        runCurrent()
        assertEquals(1, controller.snapshot().queuedCount)

        cancelled.cancelAndJoin()
        runCurrent()

        assertEquals(1, controller.snapshot().activeCount)
        assertEquals(0, controller.snapshot().queuedCount)
        assertEquals("coroutine", cancelledLifecycle.snapshot().cancelOwner)
        assertEquals(
            OpenAIHostedWebSearchSubmissionState.NOT_SENT,
            cancelledLifecycle.snapshot().submissionState,
        )
        first.release()
    }

    @Test
    fun rpmHotUpdateRetainsWindowAndAdmitsOnlyNewCapacity() = runTest {
        val controller = controller(windowMs = 60_000)
        controller.acquire(
            maxConcurrentRequests = 2,
            requestsPerMinute = 1,
            queueTimeoutMs = 10_000,
            lifecycle = lifecycle("first"),
        ).release()

        val second =
            async {
                controller.acquire(
                    maxConcurrentRequests = 2,
                    requestsPerMinute = 1,
                    queueTimeoutMs = 10_000,
                    lifecycle = lifecycle("second"),
                )
            }
        runCurrent()
        assertFalse(second.isCompleted)

        val third =
            async {
                try {
                    controller.acquire(
                        maxConcurrentRequests = 2,
                        requestsPerMinute = 2,
                        queueTimeoutMs = 10_000,
                        lifecycle = lifecycle("third"),
                    )
                    throw AssertionError("Expected third queue timeout")
                } catch (error: OpenAIHostedWebSearchException) {
                    error
                }
            }
        runCurrent()

        assertTrue(second.isCompleted)
        assertFalse(third.isCompleted)
        assertEquals(2, controller.snapshot().requestTimestampsInWindow)
        second.await().release()

        advanceTimeBy(10_001)
        runCurrent()
        val thirdFailure = third.await()
        assertEquals(OpenAIHostedWebSearchErrorCode.QUEUE_TIMEOUT, thirdFailure.code)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        windowMs: Long = 60_000,
    ): OpenAIHostedWebSearchAdmissionController =
        OpenAIHostedWebSearchAdmissionController(
            scope = this,
            nowMs = { testScheduler.currentTime },
            windowMs = windowMs,
        )

    private fun lifecycle(requestId: String): OpenAIHostedWebSearchRequestLifecycle =
        OpenAIHostedWebSearchRequestLifecycle(requestId)
}
