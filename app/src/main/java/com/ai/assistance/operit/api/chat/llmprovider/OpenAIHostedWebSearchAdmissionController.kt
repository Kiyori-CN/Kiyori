package com.ai.assistance.operit.api.chat.llmprovider

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stable, process-wide admission owner for hosted Web Search requests.
 *
 * Configuration changes update this controller in place. Replacing a limiter or semaphore when a
 * setting changes would leave old requests behind a different gate and permit both generations to
 * enter HTTP concurrently.
 */
internal class OpenAIHostedWebSearchAdmissionController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    internal data class Snapshot(
        val activeCount: Int,
        val queuedCount: Int,
        val requestTimestampsInWindow: Int,
    )

    internal class Permit internal constructor(
        private val controller: OpenAIHostedWebSearchAdmissionController,
    ) {
        private val released = AtomicBoolean(false)

        suspend fun release() {
            if (released.compareAndSet(false, true)) {
                controller.releasePermit()
            }
        }
    }

    private data class Waiter(
        val lifecycle: OpenAIHostedWebSearchRequestLifecycle,
        val enqueuedAtMs: Long,
        val deadlineMs: Long,
        val result: CompletableDeferred<Permit>,
        var admittedPermit: Permit? = null,
        var delivered: Boolean = false,
    )

    private val mutex = Mutex()
    private val waiters = ArrayDeque<Waiter>()
    private val requestTimestamps = ArrayDeque<Long>()
    private var activeCount = 0
    private var maxConcurrentRequests = 1
    private var requestsPerMinute = 0
    private var wakeJob: Job? = null

    suspend fun acquire(
        maxConcurrentRequests: Int,
        requestsPerMinute: Int,
        queueTimeoutMs: Long,
        lifecycle: OpenAIHostedWebSearchRequestLifecycle,
    ): Permit {
        require(maxConcurrentRequests > 0) { "maxConcurrentRequests must be positive" }
        require(requestsPerMinute >= 0) { "requestsPerMinute must not be negative" }
        require(queueTimeoutMs > 0L) { "queueTimeoutMs must be positive" }

        val enqueuedAtMs = nowMs()
        val waiter =
            Waiter(
                lifecycle = lifecycle,
                enqueuedAtMs = enqueuedAtMs,
                deadlineMs = saturatedAdd(enqueuedAtMs, queueTimeoutMs),
                result = CompletableDeferred(),
            )
        mutex.withLock {
            this.maxConcurrentRequests = maxConcurrentRequests
            this.requestsPerMinute = requestsPerMinute
            waiters.addLast(waiter)
            drainLocked(nowMs())
            scheduleWakeLocked(nowMs())
        }

        try {
            val permit = waiter.result.await()
            mutex.withLock {
                waiter.delivered = true
            }
            return permit
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    val removed = waiters.remove(waiter)
                    if (removed) {
                        waiter.lifecycle.recordQueueWait(nowMs() - waiter.enqueuedAtMs)
                    } else if (!waiter.delivered && waiter.admittedPermit != null) {
                        activeCount = (activeCount - 1).coerceAtLeast(0)
                    }
                    waiter.lifecycle.requestCancellation(
                        owner = OpenAIHostedWebSearchCancellationOwner.COROUTINE,
                        reason =
                            cancellation.message
                                ?.trim()
                                ?.takeIf(String::isNotEmpty)
                                ?: "OpenAI Web Search admission wait was cancelled.",
                    )
                    drainLocked(nowMs())
                    scheduleWakeLocked(nowMs())
                }
            }
            throw cancellation
        }
    }

    suspend fun snapshot(): Snapshot =
        mutex.withLock {
            pruneRequestTimestampsLocked(nowMs())
            Snapshot(
                activeCount = activeCount,
                queuedCount = waiters.size,
                requestTimestampsInWindow = requestTimestamps.size,
            )
        }

    private suspend fun releasePermit() {
        mutex.withLock {
            check(activeCount > 0) { "OpenAI Web Search admission permit was over-released" }
            activeCount -= 1
            drainLocked(nowMs())
            scheduleWakeLocked(nowMs())
        }
    }

    private fun drainLocked(now: Long) {
        pruneRequestTimestampsLocked(now)
        expireWaitersLocked(now)

        while (waiters.isNotEmpty()) {
            val concurrencyAvailable = activeCount < maxConcurrentRequests
            val rateAvailable =
                requestsPerMinute == 0 || requestTimestamps.size < requestsPerMinute
            if (!concurrencyAvailable || !rateAvailable) {
                val phase =
                    if (!rateAvailable) {
                        OpenAIHostedWebSearchRequestPhase.WAITING_RATE_LIMIT
                    } else {
                        OpenAIHostedWebSearchRequestPhase.WAITING_CONCURRENCY
                    }
                waiters.forEach { waiter -> waiter.lifecycle.markPhase(phase) }
                return
            }

            val waiter = waiters.removeFirst()
            waiter.lifecycle.recordQueueWait(now - waiter.enqueuedAtMs)
            activeCount += 1
            if (requestsPerMinute > 0) {
                requestTimestamps.addLast(now)
            }
            val permit = Permit(this)
            waiter.admittedPermit = permit
            waiter.result.complete(permit)
        }
    }

    private fun expireWaitersLocked(now: Long) {
        val iterator = waiters.iterator()
        while (iterator.hasNext()) {
            val waiter = iterator.next()
            if (now < waiter.deadlineMs) {
                continue
            }
            iterator.remove()
            val queueWaitMs = (now - waiter.enqueuedAtMs).coerceAtLeast(0L)
            waiter.lifecycle.recordQueueWait(queueWaitMs)
            val snapshot = waiter.lifecycle.snapshot()
            waiter.result.completeExceptionally(
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.QUEUE_TIMEOUT,
                    message = "OpenAI Web Search admission wait timed out.",
                    phase = snapshot.phase.wireValue,
                    submissionState = snapshot.submissionState.wireValue,
                    elapsedMs = snapshot.elapsedMs,
                    queueWaitMs = queueWaitMs,
                )
            )
        }
    }

    private fun pruneRequestTimestampsLocked(now: Long) {
        while (
            requestTimestamps.isNotEmpty() &&
                now - requestTimestamps.first() >= windowMs
        ) {
            requestTimestamps.removeFirst()
        }
    }

    private fun scheduleWakeLocked(now: Long) {
        wakeJob?.cancel()
        wakeJob = null
        if (waiters.isEmpty()) {
            return
        }

        var nextWakeAt = waiters.minOf { waiter -> waiter.deadlineMs }
        if (
            requestsPerMinute > 0 &&
                requestTimestamps.size >= requestsPerMinute &&
                requestTimestamps.isNotEmpty()
        ) {
            nextWakeAt = minOf(nextWakeAt, saturatedAdd(requestTimestamps.first(), windowMs))
        }
        val delayMs = (nextWakeAt - now).coerceAtLeast(1L)
        wakeJob =
            scope.launch {
                delay(delayMs)
                mutex.withLock {
                    wakeJob = null
                    val currentTime = nowMs()
                    drainLocked(currentTime)
                    scheduleWakeLocked(currentTime)
                }
            }
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    internal companion object {
        private const val DEFAULT_WINDOW_MS = 60_000L
        private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val shared = OpenAIHostedWebSearchAdmissionController(scope = sharedScope)
    }
}
