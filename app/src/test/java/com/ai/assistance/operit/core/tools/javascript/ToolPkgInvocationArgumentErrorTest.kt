package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgInvocationArgumentErrorTest {
    @Test
    fun `structured rejection contains stable invocation metadata`() {
        val error =
            ToolPkgInvocationArgumentError(
                toolName = "openai_web_search:openai_search",
                field = "allowed_domains",
                expectedType = "array",
                reason = ToolPkgInvocationArgumentReason.INVALID_TYPE,
            )
        val json = JSONObject(error.toJson())

        assertEquals("INVALID_ARGUMENT", json.getString("code"))
        assertEquals("openai_web_search:openai_search", json.getString("tool"))
        assertEquals("allowed_domains", json.getString("field"))
        assertEquals("array", json.getString("expected_type"))
        assertEquals("INVALID_TYPE", json.getString("reason"))
        assertTrue(error.formatLog().contains("code=INVALID_ARGUMENT"))
    }

    @Test
    fun `rejection never includes raw argument or parser text`() {
        val privateValue = """["private-domain.test""""
        val parserMessage = "Unterminated array at character 22"
        val error =
            ToolPkgInvocationArgumentError(
                toolName = "openai_web_search:openai_search",
                field = "allowed_domains",
                expectedType = "array",
                reason = ToolPkgInvocationArgumentReason.INVALID_TYPE,
            )

        assertFalse(error.toJson().contains(privateValue))
        assertFalse(error.toJson().contains(parserMessage))
        assertFalse(error.formatLog().contains(privateValue))
        assertFalse(error.formatLog().contains(parserMessage))
    }
}
