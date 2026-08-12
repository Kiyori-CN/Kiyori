package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIHostedWebSearchHttpFailurePolicyTest {
    @Test
    fun classifiesAuthenticationRateLimitAndOtherHttpFailures() {
        listOf(401, 403).forEach { status ->
            val failure = OpenAIHostedWebSearchHttpFailurePolicy.exception(status)
            assertEquals(OpenAIHostedWebSearchErrorCode.AUTH_REJECTED, failure.code)
            assertFalse(failure.retryable)
            assertEquals(status, failure.httpStatus)
        }

        val rateLimit = OpenAIHostedWebSearchHttpFailurePolicy.exception(429)
        assertEquals(OpenAIHostedWebSearchErrorCode.RATE_LIMITED, rateLimit.code)
        assertTrue(rateLimit.retryable)

        val serverFailure = OpenAIHostedWebSearchHttpFailurePolicy.exception(503)
        assertEquals(OpenAIHostedWebSearchErrorCode.OPENAI_HTTP_FAILURE, serverFailure.code)
        assertTrue(serverFailure.retryable)

        val badRequest = OpenAIHostedWebSearchHttpFailurePolicy.exception(400)
        assertEquals(OpenAIHostedWebSearchErrorCode.OPENAI_HTTP_FAILURE, badRequest.code)
        assertFalse(badRequest.retryable)
    }

    @Test
    fun parsesBoundedProviderDiagnosticsAndRedactsCredentials() {
        val details =
            OpenAIHostedWebSearchHttpFailurePolicy.parseDetails(
                responseBody =
                    """
                    {
                      "error": {
                        "type": "upstream_error",
                        "code": "search_failed",
                        "message": "Authorization: Bearer secret-value; key sk-sensitive12345678"
                      }
                    }
                    """.trimIndent(),
                providerRequestId = "request-123",
            )

        assertEquals("upstream_error", details.providerErrorType)
        assertEquals("search_failed", details.providerErrorCode)
        assertEquals("request-123", details.providerRequestId)
        assertFalse(requireNotNull(details.providerMessage).contains("secret-value"))
        assertFalse(requireNotNull(details.providerMessage).contains("sk-sensitive"))
        assertTrue(requireNotNull(details.providerMessage).contains("<redacted>"))

        val failure =
            OpenAIHostedWebSearchHttpFailurePolicy.exception(
                statusCode = 502,
                details = details,
            )
        assertEquals("upstream_error", failure.providerErrorType)
        assertEquals("search_failed", failure.providerErrorCode)
        assertEquals("request-123", failure.providerRequestId)
        assertTrue(failure.message.contains("Provider message:"))
        assertTrue(failure.message.contains("Provider request ID: request-123."))
    }

    @Test
    fun rejectsUnsafeProviderRequestIdentifier() {
        val details =
            OpenAIHostedWebSearchHttpFailurePolicy.parseDetails(
                responseBody = """{"message":"failed"}""",
                providerRequestId = "request-123\nAuthorization: secret",
            )

        assertNull(details.providerRequestId)
    }
}
