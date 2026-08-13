package com.ai.assistance.operit.core.tools.javascript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsToolPkgTerminalErrorPolicyTest {
    @Test
    fun `nested configuration rejection is one warning with stable code`() {
        val secretEndpoint = "https://private.example.test/search?token=hidden"
        val decision =
            JsToolPkgTerminalErrorPolicy.decide(
                rawError =
                    """
                    {
                      "success": false,
                      "error": {
                        "code": "CONFIG_SOURCE_INVALID",
                        "message": "[CONFIG_SOURCE_INVALID] Invalid endpoint $secretEndpoint"
                      }
                    }
                    """.trimIndent(),
                diagnostic =
                    PendingJsErrorDiagnostic(
                        errorType = "Error",
                        errorLine = 12,
                        stackPresent = true,
                    ),
            )

        assertEquals(JsToolPkgTerminalErrorSeverity.WARNING, decision.severity)
        assertEquals("CONFIG_SOURCE_INVALID", decision.code)
        assertEquals("coded_failure", decision.messageClass)
        assertFalse(decision.format().contains(secretEndpoint))
        assertFalse(decision.format().contains("Invalid endpoint"))
        assertTrue(decision.format().contains("stack_present=true"))
    }

    @Test
    fun `message prefix identifies invalid argument rejection`() {
        val decision =
            JsToolPkgTerminalErrorPolicy.decide(
                rawError =
                    """{"success":false,"message":"[INVALID_ARGUMENT] query is required"}""",
                diagnostic = null,
            )

        assertEquals(JsToolPkgTerminalErrorSeverity.WARNING, decision.severity)
        assertEquals("INVALID_ARGUMENT", decision.code)
        assertEquals("coded_failure", decision.messageClass)
    }

    @Test
    fun `unknown script failure keeps bounded structural diagnostics only`() {
        val sensitiveValue = "private-user-value"
        val diagnostic =
            JsToolPkgTerminalErrorPolicy.captureDiagnostic(
                errorType = "Type Error: $sensitiveValue",
                errorLine = -4,
                errorStack = "at search (private-source.ts:10)",
            )
        val decision =
            JsToolPkgTerminalErrorPolicy.decide(
                rawError =
                    """{"success":false,"message":"Script error: $sensitiveValue"}""",
                diagnostic = diagnostic,
            )

        assertEquals(JsToolPkgTerminalErrorSeverity.ERROR, decision.severity)
        assertNull(decision.code)
        assertEquals("script_error", decision.messageClass)
        assertEquals(0, diagnostic.errorLine)
        assertTrue(diagnostic.stackPresent)
        assertFalse(decision.format().contains(sensitiveValue))
        assertFalse(decision.format().contains("private-source.ts"))
    }

    @Test
    fun `error type is length bounded and tokenized`() {
        val diagnostic =
            JsToolPkgTerminalErrorPolicy.captureDiagnostic(
                errorType = " Error Type ".repeat(20),
                errorLine = 9,
                errorStack = "",
            )

        assertTrue(diagnostic.errorType.length <= 80)
        assertEquals("Error", diagnostic.errorType)
        assertEquals(9, diagnostic.errorLine)
        assertFalse(diagnostic.stackPresent)
    }
}
