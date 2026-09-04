package com.ai.assistance.operit.util

import java.io.EOFException
import java.net.URL
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTransferExceptionTest {
    @Test
    fun certificateFailureIsNeverRetryableAndDoesNotLeakMessages() {
        val error = SSLHandshakeException("secret-token").apply { initCause(CertificateExpiredException("private-value")) }
        val result = HttpTransferException.from(error, URL("https://user:password@example.com/file?signature=private#token"), "request", 1)
        assertEquals("TLS_CERTIFICATE_FAILED", result.code)
        assertEquals("tls_handshake", result.phase)
        assertEquals("CertificateExpiredException", result.causeType)
        assertEquals("https://example.com/file", result.endpoint)
        assertFalse(result.retryable)
        assertFalse(result.message!!.contains("private"))
        assertFalse(result.message!!.contains("secret"))
    }

    @Test
    fun unexplainedTlsFailureStopsButTransportEofMayRetry() {
        val error = SSLHandshakeException("closed")
        assertFalse(HttpTransferException.from(error, URL("https://example.com/"), "request", 1).retryable)
        error.initCause(EOFException("closed"))
        val result = HttpTransferException.from(error, URL("https://example.com/"), "request", 1)
        assertTrue(result.retryable)
        assertEquals("TLS_HANDSHAKE_FAILED", result.code)
    }
}
