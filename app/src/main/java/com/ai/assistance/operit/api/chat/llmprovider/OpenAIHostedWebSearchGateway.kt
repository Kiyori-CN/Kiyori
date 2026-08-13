package com.ai.assistance.operit.api.chat.llmprovider

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class OpenAIHostedWebSearchExecution(
    val result: OpenAIHostedWebSearchResult,
    val diagnostics: OpenAIHostedWebSearchResponseParser.Diagnostics,
)

internal class OpenAIHostedWebSearchGateway(
    private val baseHttpClient: OkHttpClient = SharedHttpClient.instance,
    private val admissionController: OpenAIHostedWebSearchAdmissionController =
        OpenAIHostedWebSearchAdmissionController.shared,
) {
    private data class ActiveCall(
        val call: Call,
        val lifecycle: OpenAIHostedWebSearchRequestLifecycle,
    )

    private val activeCalls = ConcurrentHashMap<String, ActiveCall>()

    suspend fun execute(
        binding: OpenAIHostedWebSearchBinding,
        request: OpenAIHostedWebSearchEffectiveRequest,
        lifecycle: OpenAIHostedWebSearchRequestLifecycle =
            OpenAIHostedWebSearchRequestLifecycle(request.requestId),
    ): OpenAIHostedWebSearchExecution {
        lifecycle.configureLocation(
            requested = request.locationRequested,
            configured = request.locationConfigured,
            applied = request.location != null,
            precision = request.locationPrecision,
        )
        // Domain-filter support is a provider-contract decision. Rejecting here keeps an
        // unsupported relay request out of both the admission queue and the HTTP transport.
        OpenAIHostedWebSearchDomainPolicy.requireRequestSupported(
            providerContract = binding.providerContract,
            request = request,
        )
        val permit =
            admissionController.acquire(
                maxConcurrentRequests = binding.maxConcurrentRequests,
                requestsPerMinute = binding.requestsPerMinute,
                queueTimeoutMs =
                    TimeUnit.SECONDS.toMillis(binding.queueTimeoutSeconds.toLong()),
                lifecycle = lifecycle,
            )
        return try {
            executeSingleRequest(binding, request, lifecycle)
        } finally {
            permit.release()
        }
    }

    fun cancel(requestId: String): Boolean {
        val active = activeCalls[requestId.trim()] ?: return false
        if (
            !active.lifecycle.requestCancellation(
                owner = OpenAIHostedWebSearchCancellationOwner.GATEWAY,
                reason = "OpenAI Web Search request was cancelled.",
            )
        ) {
            return false
        }
        active.call.cancel()
        return true
    }

    fun cancelTransport(requestId: String): Boolean =
        activeCalls[requestId.trim()]?.let { active ->
            active.call.cancel()
            true
        } ?: false

    private suspend fun executeSingleRequest(
        binding: OpenAIHostedWebSearchBinding,
        request: OpenAIHostedWebSearchEffectiveRequest,
        lifecycle: OpenAIHostedWebSearchRequestLifecycle,
    ): OpenAIHostedWebSearchExecution {
        val payload = OpenAIHostedWebSearchRequestCompiler.compile(binding, request)
        val timeoutMs = TimeUnit.SECONDS.toMillis(binding.timeoutSeconds.toLong())
        lifecycle.configureHttpTimeout(timeoutMs)
        lifecycle.markPhase(OpenAIHostedWebSearchRequestPhase.PREPARING_HTTP)
        val httpClient =
            baseHttpClient
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(binding.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .connectTimeout(
                    minOf(binding.timeoutSeconds, MAX_CONNECT_TIMEOUT_SECONDS).toLong(),
                    TimeUnit.SECONDS,
                )
                .readTimeout(binding.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(
                    minOf(binding.timeoutSeconds, MAX_WRITE_TIMEOUT_SECONDS).toLong(),
                    TimeUnit.SECONDS,
                )
                .eventListener(lifecycle.eventListener())
                .build()
        val requestBuilder =
            Request.Builder()
                .url(binding.endpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(
                    binding.authHeaderName,
                    listOf(binding.authScheme, binding.apiKey)
                        .filter(String::isNotBlank)
                        .joinToString(" "),
                )
        binding.extraHeaders.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        val call =
            httpClient.newCall(
                requestBuilder
                    .post(
                        payload
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build()
            )
        activeCalls[request.requestId] = ActiveCall(call = call, lifecycle = lifecycle)
        try {
            val responseJson = awaitResponse(call, lifecycle)
            lifecycle.markParseStarted()
            val parsed =
                try {
                    OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                        responseJson = responseJson,
                        request = request,
                        binding = binding,
                    )
                } finally {
                    lifecycle.markParseCompleted()
                }
            return OpenAIHostedWebSearchExecution(
                result =
                    parsed.result.copy(
                        executionDiagnostics = lifecycle.executionDiagnostics()
                    ),
                diagnostics = parsed.diagnostics,
            )
        } finally {
            activeCalls.remove(
                request.requestId,
                ActiveCall(call = call, lifecycle = lifecycle),
            )
        }
    }

    private suspend fun awaitResponse(
        call: Call,
        lifecycle: OpenAIHostedWebSearchRequestLifecycle,
    ): JSONObject =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cause ->
                lifecycle.requestCancellation(
                    owner = OpenAIHostedWebSearchCancellationOwner.COROUTINE,
                    reason =
                        cause?.message
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: "OpenAI Web Search coroutine was cancelled.",
                )
                call.cancel()
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isActive) {
                            return
                        }
                        val failure =
                            classifyTransportFailure(
                                exception = e,
                                lifecycle = lifecycle,
                                networkMessage =
                                    "OpenAI Web Search network request failed.",
                            )
                        continuation.resumeWithException(failure)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { closedResponse ->
                            if (!continuation.isActive) {
                                return
                            }
                            if (!closedResponse.isSuccessful) {
                                // Preserve only a bounded, redacted provider diagnostic. Reading the
                                // relay's JSON error before closing the response is essential for
                                // distinguishing an upstream search failure from authentication or
                                // request-contract rejection without exposing credentials.
                                val failureDetails =
                                    OpenAIHostedWebSearchHttpFailurePolicy.parseDetails(
                                        responseBody = readErrorResponseBody(closedResponse),
                                        providerRequestId =
                                            providerRequestId(closedResponse),
                                    )
                                continuation.resumeWithException(
                                    OpenAIHostedWebSearchHttpFailurePolicy.exception(
                                        statusCode = closedResponse.code,
                                        details = failureDetails,
                                    )
                                )
                                return
                            }
                            try {
                                lifecycle.markResponseBodyReadStarted()
                                val responseText =
                                    try {
                                        readResponseBody(
                                            response = closedResponse,
                                            requestId = lifecycle.requestId,
                                        )
                                    } finally {
                                        lifecycle.markResponseBodyReadCompleted()
                                    }
                                continuation.resume(JSONObject(responseText))
                            } catch (exception: Exception) {
                                val failure =
                                    when {
                                        exception is OpenAIHostedWebSearchException ->
                                            exception

                                        exception is IOException ->
                                            classifyTransportFailure(
                                                exception = exception,
                                                lifecycle = lifecycle,
                                                networkMessage =
                                                    "OpenAI Web Search response transfer failed.",
                                            )

                                        else ->
                                            OpenAIHostedWebSearchException(
                                                code =
                                                    OpenAIHostedWebSearchErrorCode
                                                        .RESPONSE_SCHEMA_INVALID,
                                                message =
                                                    "OpenAI Web Search returned invalid JSON.",
                                                cause = exception,
                                            )
                                    }
                                continuation.resumeWithException(failure)
                            }
                        }
                    }
                }
            )
        }

    private fun classifyTransportFailure(
        exception: IOException,
        lifecycle: OpenAIHostedWebSearchRequestLifecycle,
        networkMessage: String,
    ): OpenAIHostedWebSearchException {
        val snapshot = lifecycle.snapshot()
        // OkHttp's call timeout cancels the Call before reporting InterruptedIOException("timeout").
        // Timeout must therefore be identified before any cancellation state is considered.
        val timedOut =
            exception is SocketTimeoutException ||
                (
                    exception is InterruptedIOException &&
                        exception.message.orEmpty().contains("timeout", ignoreCase = true)
                )
        return when {
            timedOut ->
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.REQUEST_TIMEOUT,
                    message = "OpenAI Web Search request timed out.",
                    phase = snapshot.phase.wireValue,
                    submissionState = snapshot.submissionState.wireValue,
                    elapsedMs = snapshot.elapsedMs,
                    configuredTimeoutMs = snapshot.configuredTimeoutMs,
                    queueWaitMs = snapshot.queueWaitMs,
                    providerRequestId = snapshot.providerRequestId,
                    cause = exception,
                )

            lifecycle.isCancellationRequested() ->
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
                    message = "OpenAI Web Search request was cancelled.",
                    phase = snapshot.phase.wireValue,
                    cancelOwner = snapshot.cancelOwner,
                    submissionState = snapshot.submissionState.wireValue,
                    elapsedMs = snapshot.elapsedMs,
                    configuredTimeoutMs = snapshot.configuredTimeoutMs,
                    queueWaitMs = snapshot.queueWaitMs,
                    providerRequestId = snapshot.providerRequestId,
                    cause = exception,
                )

            else ->
                OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.NETWORK_FAILURE,
                    message = networkMessage,
                    phase = snapshot.phase.wireValue,
                    submissionState = snapshot.submissionState.wireValue,
                    elapsedMs = snapshot.elapsedMs,
                    configuredTimeoutMs = snapshot.configuredTimeoutMs,
                    queueWaitMs = snapshot.queueWaitMs,
                    providerRequestId = snapshot.providerRequestId,
                    cause = exception,
                )
        }
    }

    private fun readResponseBody(response: Response, requestId: String): String {
        val body =
            response.body
                ?: throw OpenAIHostedWebSearchException(
                    code = OpenAIHostedWebSearchErrorCode.RESPONSE_SCHEMA_INVALID,
                    message = "OpenAI Web Search returned an empty response body.",
                )
        val declaredLength = body.contentLength()
        if (declaredLength > OpenAIHostedWebSearchContract.MAX_RESPONSE_BYTES) {
            throw responseTooLarge(requestId)
        }
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                totalBytes += read
                if (totalBytes > OpenAIHostedWebSearchContract.MAX_RESPONSE_BYTES) {
                    throw responseTooLarge(requestId)
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun readErrorResponseBody(response: Response): String {
        val body = response.body ?: return ""
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (totalBytes < OpenAIHostedWebSearchContract.MAX_ERROR_RESPONSE_BYTES) {
                val maximumRead =
                    minOf(
                        buffer.size.toLong(),
                        OpenAIHostedWebSearchContract.MAX_ERROR_RESPONSE_BYTES - totalBytes,
                    ).toInt()
                val read = input.read(buffer, 0, maximumRead)
                if (read < 0) {
                    break
                }
                totalBytes += read
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun providerRequestId(response: Response): String? =
        PROVIDER_REQUEST_ID_HEADERS
            .asSequence()
            .mapNotNull { headerName -> response.header(headerName)?.trim() }
            .firstOrNull(String::isNotEmpty)

    private fun responseTooLarge(requestId: String): OpenAIHostedWebSearchException =
        OpenAIHostedWebSearchException(
            code = OpenAIHostedWebSearchErrorCode.RESPONSE_TOO_LARGE,
            message = "OpenAI Web Search response exceeded the 4 MiB limit for $requestId.",
        )

    companion object {
        private const val MAX_CONNECT_TIMEOUT_SECONDS = 30
        private const val MAX_WRITE_TIMEOUT_SECONDS = 60
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PROVIDER_REQUEST_ID_HEADERS =
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
