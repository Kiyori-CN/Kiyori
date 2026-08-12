package com.ai.assistance.operit.api.chat.llmprovider

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Call
import okhttp3.Callback
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
) {
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val cancelledRequestIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun execute(
        binding: OpenAIHostedWebSearchBinding,
        request: OpenAIHostedWebSearchEffectiveRequest,
    ): OpenAIHostedWebSearchExecution {
        val pluginRateLimiter =
            binding.requestsPerMinute.takeIf { value -> value > 0 }?.let { limit ->
                RateLimiterRegistry.getOrCreate(
                    key = "${OpenAIHostedWebSearchContract.TOOLPKG_ID}:rpm",
                    maxRequestsPerMinute = limit,
                )
            }
        val modelRateLimiter =
            binding.modelConfigId
                ?.takeIf { binding.modelConfigRequestsPerMinute > 0 }
                ?.let { configId ->
                    RateLimiterRegistry.getOrCreate(
                        key = configId,
                        maxRequestsPerMinute = binding.modelConfigRequestsPerMinute,
                    )
                }
        val pluginSemaphore =
            RequestConcurrencyRegistry.getOrCreate(
                key = "${OpenAIHostedWebSearchContract.TOOLPKG_ID}:concurrency",
                maxConcurrentRequests = binding.maxConcurrentRequests,
            )
        val modelSemaphore =
            binding.modelConfigId
                ?.takeIf { binding.modelConfigMaxConcurrentRequests > 0 }
                ?.let { configId ->
                    RequestConcurrencyRegistry.getOrCreate(
                        key = configId,
                        maxConcurrentRequests = binding.modelConfigMaxConcurrentRequests,
                    )
                }

        pluginRateLimiter?.acquire()
        modelRateLimiter?.acquire()
        return withConcurrencyLimits(pluginSemaphore, modelSemaphore) {
            executeSingleRequest(binding, request)
        }
    }

    fun cancel(requestId: String): Boolean =
        activeCalls[requestId.trim()]?.let { call ->
            cancelledRequestIds.add(requestId.trim())
            call.cancel()
            true
        } ?: false

    private suspend fun executeSingleRequest(
        binding: OpenAIHostedWebSearchBinding,
        request: OpenAIHostedWebSearchEffectiveRequest,
    ): OpenAIHostedWebSearchExecution {
        val payload = OpenAIHostedWebSearchRequestCompiler.compile(binding, request)
        val httpClient =
            baseHttpClient
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(binding.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .connectTimeout(binding.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(binding.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(binding.timeoutSeconds.toLong(), TimeUnit.SECONDS)
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
        activeCalls[request.requestId] = call
        try {
            val responseJson = awaitResponse(call, request.requestId)
            val parsed =
                OpenAIHostedWebSearchResponseParser.parseWithDiagnostics(
                    responseJson = responseJson,
                    request = request,
                    binding = binding,
                )
            return OpenAIHostedWebSearchExecution(
                result = parsed.result,
                diagnostics = parsed.diagnostics,
            )
        } finally {
            activeCalls.remove(request.requestId, call)
            cancelledRequestIds.remove(request.requestId)
        }
    }

    private suspend fun awaitResponse(call: Call, requestId: String): JSONObject =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancelledRequestIds.add(requestId)
                call.cancel()
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isActive) {
                            return
                        }
                        val failure =
                            when {
                                requestId in cancelledRequestIds || call.isCanceled() ->
                                    OpenAIHostedWebSearchException(
                                        code =
                                            OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
                                        message = "OpenAI Web Search request was cancelled.",
                                        cause = e,
                                    )

                                e is SocketTimeoutException ->
                                    OpenAIHostedWebSearchException(
                                        code =
                                            OpenAIHostedWebSearchErrorCode.REQUEST_TIMEOUT,
                                        message = "OpenAI Web Search request timed out.",
                                        retryable = true,
                                        cause = e,
                                    )

                                else ->
                                    OpenAIHostedWebSearchException(
                                        code =
                                            OpenAIHostedWebSearchErrorCode.NETWORK_FAILURE,
                                        message = "OpenAI Web Search network request failed.",
                                        retryable = true,
                                        cause = e,
                                    )
                            }
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
                                val responseText =
                                    readResponseBody(
                                        response = closedResponse,
                                        requestId = requestId,
                                    )
                                continuation.resume(JSONObject(responseText))
                            } catch (exception: Exception) {
                                val failure =
                                    when {
                                        exception is OpenAIHostedWebSearchException ->
                                            exception

                                        requestId in cancelledRequestIds || call.isCanceled() ->
                                            OpenAIHostedWebSearchException(
                                                code =
                                                    OpenAIHostedWebSearchErrorCode
                                                        .REQUEST_CANCELLED,
                                                message =
                                                    "OpenAI Web Search request was cancelled.",
                                                cause = exception,
                                            )

                                        exception is SocketTimeoutException ||
                                            exception is InterruptedIOException ->
                                            OpenAIHostedWebSearchException(
                                                code =
                                                    OpenAIHostedWebSearchErrorCode
                                                        .REQUEST_TIMEOUT,
                                                message =
                                                    "OpenAI Web Search request timed out.",
                                                retryable = true,
                                                cause = exception,
                                            )

                                        exception is IOException ->
                                            OpenAIHostedWebSearchException(
                                                code =
                                                    OpenAIHostedWebSearchErrorCode
                                                        .NETWORK_FAILURE,
                                                message =
                                                    "OpenAI Web Search response transfer failed.",
                                                retryable = true,
                                                cause = exception,
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

    private suspend fun <T> withConcurrencyLimits(
        pluginSemaphore: Semaphore,
        modelSemaphore: Semaphore?,
        block: suspend () -> T,
    ): T {
        var pluginAcquired = false
        var modelAcquired = false
        try {
            pluginSemaphore.acquire()
            pluginAcquired = true
            modelSemaphore?.acquire()
            modelAcquired = modelSemaphore != null
            return block()
        } catch (cancellation: CancellationException) {
            throw OpenAIHostedWebSearchException(
                code = OpenAIHostedWebSearchErrorCode.REQUEST_CANCELLED,
                message = "OpenAI Web Search request was cancelled.",
                cause = cancellation,
            )
        } finally {
            if (modelAcquired) {
                modelSemaphore?.release()
            }
            if (pluginAcquired) {
                pluginSemaphore.release()
            }
        }
    }

    companion object {
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
