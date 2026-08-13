package com.ai.assistance.operit.api.chat.llmprovider

import java.io.IOException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response

internal enum class OpenAIHostedWebSearchRequestPhase(val wireValue: String) {
    CREATED("created"),
    WAITING_RATE_LIMIT("waiting_rate_limit"),
    WAITING_CONCURRENCY("waiting_concurrency"),
    PREPARING_HTTP("preparing_http"),
    REQUEST_HEADERS("request_headers"),
    REQUEST_BODY("request_body"),
    WAITING_RESPONSE_HEADERS("waiting_response_headers"),
    READING_RESPONSE_BODY("reading_response_body"),
    PARSING_RESPONSE("parsing_response"),
    DELIVERING_CALLBACK("delivering_callback"),
    TERMINAL("terminal"),
}

internal enum class OpenAIHostedWebSearchSubmissionState(val wireValue: String) {
    NOT_SENT("not_sent"),
    SUBMISSION_UNKNOWN("submission_unknown"),
    RESPONSE_STARTED("response_started"),
}

internal enum class OpenAIHostedWebSearchCancellationOwner(val wireValue: String) {
    TOOLPKG("toolpkg"),
    EXECUTION_OWNER("execution_owner"),
    COROUTINE("coroutine"),
    BRIDGE("bridge"),
    GATEWAY("gateway"),
}

internal enum class OpenAIHostedWebSearchTerminalOutcome {
    SUCCESS,
    FAILURE,
    CANCELLED,
}

internal data class OpenAIHostedWebSearchRequestSnapshot(
    val phase: OpenAIHostedWebSearchRequestPhase,
    val submissionState: OpenAIHostedWebSearchSubmissionState,
    val cancelOwner: String?,
    val elapsedMs: Long,
    val configuredTimeoutMs: Long?,
    val queueWaitMs: Long?,
    val httpElapsedMs: Long?,
    val responseHeaderWaitMs: Long?,
    val responseBodyReadMs: Long?,
    val parseMs: Long?,
    val providerRequestId: String?,
)

internal data class OpenAIHostedWebSearchRequestSettlement(
    val outcome: OpenAIHostedWebSearchTerminalOutcome,
    val cancelOwner: String?,
    val cancelReason: String?,
    val snapshot: OpenAIHostedWebSearchRequestSnapshot,
) {
    fun cancellationException(): OpenAIHostedWebSearchException =
        OpenAIHostedWebSearchException(
            code = OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
            message = cancelReason ?: "OpenAI Web Search request was cancelled.",
            phase = snapshot.phase.wireValue,
            cancelOwner = cancelOwner,
            submissionState = snapshot.submissionState.wireValue,
            elapsedMs = snapshot.elapsedMs,
            configuredTimeoutMs = snapshot.configuredTimeoutMs,
            queueWaitMs = snapshot.queueWaitMs,
            providerRequestId = snapshot.providerRequestId,
        )
}

/**
 * Owns the observable lifecycle of one hosted-search request.
 *
 * Cancellation and worker completion must use this same state owner. Otherwise a worker can
 * produce a successful result while its execution owner concurrently cancels the request, and
 * callback delivery becomes dependent on thread timing instead of one explicit terminal decision.
 */
internal class OpenAIHostedWebSearchRequestLifecycle(
    val requestId: String,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private enum class State {
        ACTIVE,
        CANCEL_REQUESTED,
        TERMINAL,
    }

    private val lock = Any()
    private val startedAtNs = nanoTime()
    private var state = State.ACTIVE
    private var phase = OpenAIHostedWebSearchRequestPhase.CREATED
    private var submissionState = OpenAIHostedWebSearchSubmissionState.NOT_SENT
    private var cancelOwner: String? = null
    private var cancelReason: String? = null
    private var configuredTimeoutMs: Long? = null
    private var queueWaitMs: Long? = null
    private var providerRequestId: String? = null
    private var httpStartedAtNs: Long? = null
    private var httpCompletedAtNs: Long? = null
    private var requestBodyCompletedAtNs: Long? = null
    private var responseHeadersStartedAtNs: Long? = null
    private var responseBodyReadStartedAtNs: Long? = null
    private var responseBodyReadCompletedAtNs: Long? = null
    private var parseStartedAtNs: Long? = null
    private var parseCompletedAtNs: Long? = null
    private var terminalAtNs: Long? = null
    private var locationDiagnostics =
        OpenAIHostedWebSearchLocationDiagnostics(
            requested = false,
            configured = false,
            applied = false,
            precision = "none",
        )

    fun configureHttpTimeout(timeoutMs: Long) {
        synchronized(lock) {
            configuredTimeoutMs = timeoutMs
        }
    }

    fun recordQueueWait(waitMs: Long) {
        synchronized(lock) {
            queueWaitMs = waitMs.coerceAtLeast(0L)
        }
    }

    fun markPhase(nextPhase: OpenAIHostedWebSearchRequestPhase) {
        synchronized(lock) {
            if (state != State.TERMINAL) {
                phase = nextPhase
            }
        }
    }

    fun markSubmissionUnknown() {
        synchronized(lock) {
            if (
                state != State.TERMINAL &&
                    submissionState == OpenAIHostedWebSearchSubmissionState.NOT_SENT
            ) {
                submissionState = OpenAIHostedWebSearchSubmissionState.SUBMISSION_UNKNOWN
            }
        }
    }

    fun markResponseStarted() {
        synchronized(lock) {
            if (state != State.TERMINAL) {
                submissionState = OpenAIHostedWebSearchSubmissionState.RESPONSE_STARTED
            }
        }
    }

    fun recordProviderRequestId(value: String?) {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return
        synchronized(lock) {
            if (state != State.TERMINAL) {
                providerRequestId = normalized
            }
        }
    }

    fun configureLocation(
        requested: Boolean,
        configured: Boolean,
        applied: Boolean,
        precision: String,
    ) {
        require(!applied || (requested && configured))
        synchronized(lock) {
            locationDiagnostics =
                OpenAIHostedWebSearchLocationDiagnostics(
                    requested = requested,
                    configured = configured,
                    applied = applied,
                    precision = precision,
                )
        }
    }

    fun markResponseBodyReadStarted() {
        synchronized(lock) {
            if (state != State.TERMINAL && responseBodyReadStartedAtNs == null) {
                responseBodyReadStartedAtNs = nanoTime()
                phase = OpenAIHostedWebSearchRequestPhase.READING_RESPONSE_BODY
            }
        }
    }

    fun markResponseBodyReadCompleted() {
        synchronized(lock) {
            if (state != State.TERMINAL && responseBodyReadCompletedAtNs == null) {
                val completedAtNs = nanoTime()
                responseBodyReadCompletedAtNs = completedAtNs
                httpCompletedAtNs = completedAtNs
            }
        }
    }

    fun markParseStarted() {
        synchronized(lock) {
            if (state != State.TERMINAL && parseStartedAtNs == null) {
                parseStartedAtNs = nanoTime()
                phase = OpenAIHostedWebSearchRequestPhase.PARSING_RESPONSE
            }
        }
    }

    fun markParseCompleted() {
        synchronized(lock) {
            if (state != State.TERMINAL && parseCompletedAtNs == null) {
                parseCompletedAtNs = nanoTime()
            }
        }
    }

    fun executionDiagnostics(): OpenAIHostedWebSearchExecutionDiagnostics =
        synchronized(lock) {
            val snapshot = snapshotLocked()
            OpenAIHostedWebSearchExecutionDiagnostics(
                totalElapsedMs = snapshot.elapsedMs,
                queueWaitMs = snapshot.queueWaitMs,
                httpElapsedMs = snapshot.httpElapsedMs,
                responseHeaderWaitMs = snapshot.responseHeaderWaitMs,
                responseBodyReadMs = snapshot.responseBodyReadMs,
                parseMs = snapshot.parseMs,
                // Delivery is still in progress when the final JSON is produced. Reporting a
                // fabricated zero would make field diagnostics look more precise than they are.
                callbackDeliveryMs = null,
                providerRequestId = snapshot.providerRequestId,
                submissionState = snapshot.submissionState,
                location = locationDiagnostics,
            )
        }

    fun requestCancellation(
        owner: OpenAIHostedWebSearchCancellationOwner,
        reason: String,
    ): Boolean =
        synchronized(lock) {
            if (state != State.ACTIVE) {
                return@synchronized false
            }
            state = State.CANCEL_REQUESTED
            cancelOwner = owner.wireValue
            cancelReason = reason.trim().takeIf(String::isNotEmpty)
            true
        }

    fun isCancellationRequested(): Boolean =
        synchronized(lock) {
            state == State.CANCEL_REQUESTED
        }

    fun settle(
        proposedOutcome: OpenAIHostedWebSearchTerminalOutcome,
    ): OpenAIHostedWebSearchRequestSettlement? =
        synchronized(lock) {
            if (state == State.TERMINAL) {
                return@synchronized null
            }
            val terminalOutcome =
                if (state == State.CANCEL_REQUESTED) {
                    OpenAIHostedWebSearchTerminalOutcome.CANCELLED
                } else {
                    proposedOutcome
                }
            terminalAtNs = nanoTime()
            val terminalSnapshot = snapshotLocked()
            state = State.TERMINAL
            phase = OpenAIHostedWebSearchRequestPhase.TERMINAL
            OpenAIHostedWebSearchRequestSettlement(
                outcome = terminalOutcome,
                cancelOwner = cancelOwner,
                cancelReason = cancelReason,
                snapshot = terminalSnapshot,
            )
        }

    fun snapshot(): OpenAIHostedWebSearchRequestSnapshot =
        synchronized(lock) {
            snapshotLocked()
        }

    fun eventListener(): EventListener =
        object : EventListener() {
            override fun callStart(call: Call) {
                synchronized(lock) {
                    if (state != State.TERMINAL) {
                        if (httpStartedAtNs == null) {
                            httpStartedAtNs = nanoTime()
                        }
                        phase = OpenAIHostedWebSearchRequestPhase.PREPARING_HTTP
                    }
                }
            }

            override fun requestHeadersStart(call: Call) {
                markPhase(OpenAIHostedWebSearchRequestPhase.REQUEST_HEADERS)
            }

            override fun requestHeadersEnd(call: Call, request: Request) {
                markPhase(OpenAIHostedWebSearchRequestPhase.REQUEST_BODY)
            }

            override fun requestBodyStart(call: Call) {
                markPhase(OpenAIHostedWebSearchRequestPhase.REQUEST_BODY)
                markSubmissionUnknown()
            }

            override fun requestBodyEnd(call: Call, byteCount: Long) {
                synchronized(lock) {
                    if (state != State.TERMINAL) {
                        if (
                            submissionState ==
                                OpenAIHostedWebSearchSubmissionState.NOT_SENT
                        ) {
                            submissionState =
                                OpenAIHostedWebSearchSubmissionState.SUBMISSION_UNKNOWN
                        }
                        requestBodyCompletedAtNs = nanoTime()
                        phase =
                            OpenAIHostedWebSearchRequestPhase.WAITING_RESPONSE_HEADERS
                    }
                }
            }

            override fun responseHeadersStart(call: Call) {
                synchronized(lock) {
                    if (state != State.TERMINAL) {
                        submissionState =
                            OpenAIHostedWebSearchSubmissionState.RESPONSE_STARTED
                        responseHeadersStartedAtNs = nanoTime()
                        phase =
                            OpenAIHostedWebSearchRequestPhase.WAITING_RESPONSE_HEADERS
                    }
                }
            }

            override fun responseHeadersEnd(call: Call, response: Response) {
                recordProviderRequestId(providerRequestId(response))
                markPhase(OpenAIHostedWebSearchRequestPhase.READING_RESPONSE_BODY)
            }

            override fun responseBodyStart(call: Call) {
                markPhase(OpenAIHostedWebSearchRequestPhase.READING_RESPONSE_BODY)
            }

            override fun callFailed(call: Call, ioe: IOException) = Unit
        }

    private fun snapshotLocked(): OpenAIHostedWebSearchRequestSnapshot =
        (terminalAtNs ?: nanoTime()).let { nowNs ->
            OpenAIHostedWebSearchRequestSnapshot(
                phase = phase,
                submissionState = submissionState,
                cancelOwner = cancelOwner,
                elapsedMs = elapsedMillis(startedAtNs, nowNs),
                configuredTimeoutMs = configuredTimeoutMs,
                queueWaitMs = queueWaitMs,
                httpElapsedMs =
                    durationMillis(httpStartedAtNs, httpCompletedAtNs),
                responseHeaderWaitMs =
                    durationMillis(
                        requestBodyCompletedAtNs,
                        responseHeadersStartedAtNs,
                    ),
                responseBodyReadMs =
                    durationMillis(
                        responseBodyReadStartedAtNs,
                        responseBodyReadCompletedAtNs,
                    ),
                parseMs =
                    durationMillis(
                        parseStartedAtNs,
                        parseCompletedAtNs,
                    ),
                providerRequestId = providerRequestId,
            )
        }

    private fun durationMillis(
        startedAtNs: Long?,
        completedAtNs: Long?,
    ): Long? =
        if (startedAtNs == null || completedAtNs == null) {
            null
        } else {
            elapsedMillis(startedAtNs, completedAtNs)
        }

    private fun elapsedMillis(
        startedAtNs: Long,
        completedAtNs: Long,
    ): Long =
        ((completedAtNs - startedAtNs) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(0L)

    private fun providerRequestId(response: Response): String? =
        PROVIDER_REQUEST_ID_HEADERS
            .asSequence()
            .mapNotNull { headerName -> response.header(headerName)?.trim() }
            .firstOrNull(String::isNotEmpty)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        val PROVIDER_REQUEST_ID_HEADERS =
            listOf(
                "x-request-id",
                "request-id",
                "openai-request-id",
                "x-trace-id",
                "trace-id",
                "cf-ray",
            )
    }
}
