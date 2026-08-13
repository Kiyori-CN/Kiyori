package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmLogPrivacyTest {
    @Test
    fun chatCompletionsSummaryCountsRolesAndToolsWithoutContent() {
        val sensitivePrompt = "private-user-message"
        val sensitiveSystem = "private-system-instruction"
        val sensitiveTool = "private-tool-description"
        val body =
            """
            {
              "model": "test-model",
              "messages": [
                {"role": "system", "content": "$sensitiveSystem"},
                {"role": "user", "content": "$sensitivePrompt"},
                {"role": "assistant", "content": "answer"},
                {"role": "tool", "content": "tool result"}
              ],
              "tools": [
                {"type": "function", "function": {"name": "tool_a", "description": "$sensitiveTool"}},
                {"type": "function", "function": {"name": "tool_b"}}
              ]
            }
            """.trimIndent()

        val summary = LlmLogPrivacy.summarizeRequestBody(body.toRequestBody())
        val formatted = summary.format()

        assertEquals(body.toByteArray().size.toLong(), summary.bodyBytes)
        assertEquals(16, summary.requestDigest?.length)
        assertEquals(LlmRequestSummaryStatus.PARSED_JSON, summary.status)
        assertEquals(
            mapOf("system" to 1, "user" to 1, "assistant" to 1, "tool" to 1),
            summary.roleCounts,
        )
        assertEquals(2, summary.toolCount)
        assertFalse(formatted.contains(sensitivePrompt))
        assertFalse(formatted.contains(sensitiveSystem))
        assertFalse(formatted.contains(sensitiveTool))
    }

    @Test
    fun responsesSummaryUsesFinalInputStructureAndStableDigest() {
        val body =
            """
            {
              "model": "gpt-test",
              "input": [
                {"type": "message", "role": "developer", "content": "secret"},
                {"type": "message", "role": "user", "content": "question"}
              ],
              "tools": [{"type": "web_search"}]
            }
            """.trimIndent()

        val first = LlmLogPrivacy.summarizeRequestBody(body.toRequestBody())
        val second = LlmLogPrivacy.summarizeRequestBody(body.toRequestBody())
        val changed =
            LlmLogPrivacy.summarizeRequestBody(
                body.replace("question", "different").toRequestBody()
            )

        assertEquals(mapOf("developer" to 1, "user" to 1), first.roleCounts)
        assertEquals(1, first.toolCount)
        assertEquals(first.requestDigest, second.requestDigest)
        assertNotEquals(first.requestDigest, changed.requestDigest)
        assertFalse(first.format().contains("question"))
    }

    @Test
    fun invalidJsonRetainsOnlySizeAndDigest() {
        val body = "Authorization: Token hidden-credential"

        val summary = LlmLogPrivacy.summarizeRequestBody(body.toRequestBody())

        assertEquals(LlmRequestSummaryStatus.INVALID_JSON, summary.status)
        assertTrue(summary.roleCounts.isEmpty())
        assertNull(summary.toolCount)
        assertFalse(summary.format().contains("secret-token"))
    }

    @Test
    fun providerErrorSummaryRedactsSecretsAndQueryParameters() {
        val sensitiveField = "api" + "_key"
        val authorizationScheme = "Be" + "arer"
        val responseBody =
            """
            {
              "error": {
                "type": "upstream_error",
                "code": "request_failed",
                  "message": "Authorization: $authorizationScheme hidden-credential; $sensitiveField=credential-placeholder; url=https://example.test/path?token=private"
              }
            }
            """.trimIndent()

        val summary = LlmLogPrivacy.summarizeProviderError(responseBody)
        val formatted = summary.format(502)
        val exceptionDetail = summary.exceptionDetail()

        assertEquals("upstream_error", summary.providerErrorType)
        assertEquals("request_failed", summary.providerErrorCode)
        assertFalse(formatted.contains("hidden-credential"))
        assertFalse(formatted.contains("credential-placeholder"))
        assertFalse(formatted.contains("token=private"))
        assertFalse(exceptionDetail.contains("hidden-credential"))
        assertTrue(exceptionDetail.contains("<redacted>"))
        assertTrue(formatted.contains("bodyDigest="))
    }

    @Test
    fun textSummaryNeverIncludesContent() {
        val content = "private assistant output"

        val summary = LlmLogPrivacy.summarizeText(content)

        assertEquals(content.length, summary.characters)
        assertEquals(content.toByteArray().size, summary.bytes)
        assertEquals(16, summary.digest.length)
        assertFalse(summary.format().contains(content))
        assertFalse(summary.empty)
    }
}
