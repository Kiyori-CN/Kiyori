package com.ai.assistance.operit.api.chat.llmprovider

import java.security.MessageDigest

internal enum class LlmTransportStage {
    CREATED,
    DNS_LOOKUP,
    CONNECTING,
    TLS_HANDSHAKE,
    REQUEST_HEADERS,
    REQUEST_BODY,
    WAITING_FOR_RESPONSE_HEADERS,
    RESPONSE_HEADERS,
    RESPONSE_BODY,
    COMPLETED,
    FAILED,
}

/**
 * Per-call transport state shared by OkHttp callbacks and the provider error boundary.
 *
 * The state intentionally contains only protocol phase and bounded correlation metadata. It must
 * never become a second request log: prompt content, request headers, credentials, query strings,
 * and response bodies do not belong here.
 */
internal class LlmRequestTraceState {
    private val startedAtNs = System.nanoTime()

    private fun elapsedMs(): Long = (System.nanoTime() - startedAtNs) / 1_000_000

    @Volatile private var proxyType: String? = null
    @Volatile private var addressFamily: String? = null
    @Volatile private var callCancelled = false
    @Volatile private var requestBodySentAtMs: Long? = null
    @Volatile private var responseHeadersAtMs: Long? = null
    @Volatile private var clientRequestIdHash: String? = null
    @Volatile private var requestIdHash: String? = null
    @Volatile
    var stage: LlmTransportStage = LlmTransportStage.CREATED
        private set

    @Volatile
    var protocol: String? = null
        private set

    @Volatile
    var tlsVersion: String? = null
        private set

    @Volatile
    var cipherSuite: String? = null
        private set

    @Volatile
    var requestBodyBytes: Long = -1L
        private set

    @Volatile
    var requestBodyStarted: Boolean = false
        private set

    @Volatile
    var responseHeadersReceived: Boolean = false
        private set

    @Volatile
    var responseBodyStarted: Boolean = false
        private set

    @Volatile
    var responseStatusCode: Int? = null
        private set

    @Volatile
    var connectionReused: Boolean? = null
        private set

    @Volatile
    var connectionFailed: Boolean = false
        private set

    @Volatile
    var responseCorrelationId: String? = null
        private set

    @Volatile
    private var connectStarted = false

    fun markDnsLookup() {
        stage = LlmTransportStage.DNS_LOOKUP
    }

    fun markConnecting() {
        connectStarted = true
        stage = LlmTransportStage.CONNECTING
    }

    fun markTlsHandshake() {
        stage = LlmTransportStage.TLS_HANDSHAKE
    }

    fun markTlsEstablished(version: String?, cipher: String?) {
        tlsVersion = version
        cipherSuite = cipher
        stage = LlmTransportStage.CONNECTING
    }

    fun markProtocol(protocolName: String?) {
        protocol = protocolName
    }

    fun markConnectionFailed() {
        connectionFailed = true
        stage = LlmTransportStage.FAILED
    }

    fun markConnectionAcquired(
        protocolName: String,
        actualProxyType: String? = null,
        actualAddressFamily: String? = null,
    ) {
        // 建连失败后可能在同一 Call 内完成另一条连接；历史失败不能覆盖最终已提交的事实。
        connectionFailed = false
        protocol = protocolName
        proxyType = actualProxyType
        addressFamily = actualAddressFamily
        connectionReused = !connectStarted
        stage = LlmTransportStage.REQUEST_HEADERS
    }

    fun markRequestHeaders() {
        stage = LlmTransportStage.REQUEST_HEADERS
    }

    fun markRequestBody() {
        requestBodyStarted = true
        stage = LlmTransportStage.REQUEST_BODY
    }

    fun markRequestBodyCompleted(bytes: Long) {
        requestBodyBytes = bytes
        requestBodySentAtMs = elapsedMs()
        stage = LlmTransportStage.WAITING_FOR_RESPONSE_HEADERS
    }

    fun markResponseHeadersStarted() {
        stage = LlmTransportStage.RESPONSE_HEADERS
    }

    fun markResponseHeadersCompleted(
        statusCode: Int,
        correlationId: String?,
    ) {
        responseHeadersReceived = true
        responseStatusCode = statusCode
        responseHeadersAtMs = elapsedMs()
        responseCorrelationId = correlationId
        stage = LlmTransportStage.RESPONSE_HEADERS
    }

    fun markResponseBodyStarted() {
        responseBodyStarted = true
        stage = LlmTransportStage.RESPONSE_BODY
    }

    fun markResponseBodyCompleted() {
        stage = LlmTransportStage.COMPLETED
    }

    fun markCallCompleted() {
        stage = LlmTransportStage.COMPLETED
    }

    fun markCallCancelled() {
        callCancelled = true
    }

    fun markRequestCorrelation(clientRequestId: String?, requestId: String?) {
        // 显式自定义头可能包含用户数据，仅保留不可逆哈希用于跨端比对。
        clientRequestIdHash = LlmTransportDiagnostics.redactCorrelationId(clientRequestId)
        requestIdHash = LlmTransportDiagnostics.redactCorrelationId(requestId)
    }

    @Synchronized
    fun snapshot(failure: Throwable? = null): LlmTransportDiagnostics {
        val diagnosticCode =
            when {
                failure == null && stage == LlmTransportStage.COMPLETED ->
                    "LLM_TRANSPORT_COMPLETED"
                failure == null && !connectionFailed ->
                    "LLM_TRANSPORT_IN_PROGRESS"
                connectionFailed ->
                    "LLM_TRANSPORT_CONNECT_FAILED"
                responseBodyStarted ->
                    "LLM_TRANSPORT_RESPONSE_BODY_INTERRUPTED"
                responseHeadersReceived ->
                    "LLM_TRANSPORT_RESPONSE_HEADERS_RECEIVED_FAILURE"
                requestBodyBytes >= 0L ->
                    "LLM_TRANSPORT_RESPONSE_HEADERS_NOT_RECEIVED"
                stage == LlmTransportStage.REQUEST_BODY ->
                    "LLM_TRANSPORT_REQUEST_BODY_INTERRUPTED"
                else ->
                    "LLM_TRANSPORT_FAILED"
            }

        return LlmTransportDiagnostics(
            diagnosticCode = diagnosticCode,
            stage = stage,
            protocol = protocol,
            tlsVersion = tlsVersion,
            cipherSuite = cipherSuite,
            requestBodyBytes = requestBodyBytes,
            requestBodyStarted = requestBodyStarted,
            responseHeadersReceived = responseHeadersReceived,
            responseBodyStarted = responseBodyStarted,
            responseStatusCode = responseStatusCode,
            connectionReused = connectionReused,
            connectionFailed = connectionFailed,
            failureType = failure?.javaClass?.simpleName,
            responseCorrelationId = responseCorrelationId,
            proxyType = proxyType,
            addressFamily = addressFamily,
            callCancelled = callCancelled,
            elapsedMs = elapsedMs(),
            requestBodySentAtMs = requestBodySentAtMs,
            responseHeadersAtMs = responseHeadersAtMs,
            clientRequestIdHash = clientRequestIdHash,
            requestIdHash = requestIdHash,
        )
    }

    fun snapshotForHttpStatus(statusCode: Int): LlmTransportDiagnostics {
        val base = snapshot()
        return base.copy(
            diagnosticCode = "LLM_TRANSPORT_HTTP_STATUS_$statusCode",
            responseHeadersReceived = true,
            responseStatusCode = statusCode,
        )
    }
}

internal data class LlmTransportDiagnostics(
    val diagnosticCode: String,
    val stage: LlmTransportStage,
    val protocol: String?,
    val tlsVersion: String?,
    val cipherSuite: String?,
    val requestBodyBytes: Long,
    val requestBodyStarted: Boolean,
    val responseHeadersReceived: Boolean,
    val responseBodyStarted: Boolean,
    val responseStatusCode: Int?,
    val connectionReused: Boolean?,
    val connectionFailed: Boolean,
    val failureType: String?,
    val responseCorrelationId: String?,
    val proxyType: String? = null,
    val addressFamily: String? = null,
    val callCancelled: Boolean = false,
    val elapsedMs: Long = 0L,
    val requestBodySentAtMs: Long? = null,
    val responseHeadersAtMs: Long? = null,
    val clientRequestIdHash: String? = null,
    val requestIdHash: String? = null,
) {
    fun summary(): String =
        buildString {
            append("diagnosticCode=").append(diagnosticCode)
            append(", stage=").append(stage.name)
            append(", protocol=").append(protocol ?: "unknown")
            append(", tls=").append(tlsVersion ?: "unknown")
            append(", bodyBytes=").append(requestBodyBytes)
            append(", requestBodyStarted=").append(requestBodyStarted)
            append(", responseHeadersReceived=").append(responseHeadersReceived)
            append(", responseBodyStarted=").append(responseBodyStarted)
            append(", status=").append(responseStatusCode ?: "none")
            append(", connectionReused=").append(connectionReused ?: "unknown")
            append(", connectionFailed=").append(connectionFailed)
            append(", proxyType=").append(proxyType ?: "unknown")
            append(", addressFamily=").append(addressFamily ?: "unknown")
            append(", callCancelled=").append(callCancelled)
            append(", elapsedMs=").append(elapsedMs)
            requestBodySentAtMs?.let { append(", requestBodySentAtMs=").append(it) }
            responseHeadersAtMs?.let { append(", responseHeadersAtMs=").append(it) }
            clientRequestIdHash?.let { append(", clientRequestIdHash=").append(it) }
            requestIdHash?.let { append(", requestIdHash=").append(it) }
            failureType?.let { append(", failureType=").append(it) }
            responseCorrelationId?.let { append(", responseCorrelationId=").append(it) }
        }

    companion object {
        fun redactCorrelationId(raw: String?): String? {
            val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
