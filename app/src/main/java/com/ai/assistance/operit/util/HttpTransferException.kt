package com.ai.assistance.operit.util

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

internal class HttpTransferException(
    val code: String,
    val endpoint: String,
    val phase: String,
    val attempt: Int,
    val retryable: Boolean,
    val causeType: String,
    val httpStatus: Int? = null,
    range: String? = null,
    headers: Map<String, String> = emptyMap(),
) : IOException(
    "[$code] $phase $endpoint; " +
        "http_status=${httpStatus ?: "none"}; attempts=$attempt; cause_type=$causeType; " +
        "range=${range ?: "none"}; " +
        "referer_present=${headers.keys.any { it.equals("Referer", true) }}; " +
        "user_agent_present=${headers.keys.any { it.equals("User-Agent", true) }}"
) {
    companion object {
        fun endpoint(url: URL): String = URL(url.protocol, url.host, url.port, url.path).toString()

        fun from(
            error: IOException,
            url: URL,
            phase: String,
            attempt: Int,
            range: String? = null,
            headers: Map<String, String> = emptyMap(),
        ): HttpTransferException {
            if (error is HttpTransferException) return error
            val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
            val certificate = causes.firstOrNull { it is CertificateException || it is SSLPeerUnverifiedException }
            val tls = causes.any { it is SSLException }
            val handshake = causes.any { it is SSLHandshakeException }
            val timeout = causes.any { it is SocketTimeoutException }
            val interrupted = causes.any { it is EOFException || it is SocketException }
            val code = when {
                certificate != null -> "TLS_CERTIFICATE_FAILED"
                handshake -> "TLS_HANDSHAKE_FAILED"
                tls -> "TLS_FAILED"
                timeout -> "NETWORK_TIMEOUT"
                interrupted -> "CONNECTION_FAILED"
                else -> "NETWORK_IO_FAILED"
            }
            return HttpTransferException(
                code, endpoint(url), if (handshake || certificate != null) "tls_handshake" else phase,
                attempt, certificate == null && (timeout || interrupted),
                (certificate ?: causes.last()).javaClass.simpleName, range = range, headers = headers,
            )
        }

        fun http(url: URL, phase: String, status: Int, attempt: Int, range: String?, headers: Map<String, String>) =
            HttpTransferException(
                when (status) {
                    401 -> "HTTP_UNAUTHORIZED"
                    403 -> "HTTP_FORBIDDEN"
                    412, 429 -> "RISK_CONTROL"
                    else -> "HTTP_FAILED"
                },
                endpoint(url), phase, attempt, status in setOf(500, 502, 503, 504),
                "HttpStatus", status, range, headers,
            )
    }
}
