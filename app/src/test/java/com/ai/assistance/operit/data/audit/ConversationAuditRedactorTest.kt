package com.ai.assistance.operit.data.audit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAuditRedactorTest {
    @Test
    fun `json credentials and nested bearer tokens are removed`() {
        val result =
            ConversationAuditRedactor.redactText(
                """
                {
                  "Authorization": "Bearer top-secret",
                  "nested": {
                    "api_key": "key-value",
                    "message": "Authorization: Bearer another-secret"
                  }
                }
                """.trimIndent(),
                mediaType = "application/json",
            )

        assertTrue(result.replacementCount >= 3)
        assertTrue(result.value.contains("[REDACTED:credential]"))
        assertFalse(result.value.contains("top-secret"))
        assertFalse(result.value.contains("key-value"))
        assertFalse(result.value.contains("another-secret"))
    }

    @Test
    fun `credential URL query is removed without deleting ordinary parameters`() {
        val result =
            ConversationAuditRedactor.redactText(
                "https://example.test/search?q=kiyori&access_token=secret"
            )

        assertTrue(result.value.contains("q=kiyori"))
        assertTrue(result.value.contains("access_token=%5BREDACTED:credential%5D"))
        assertFalse(result.value.contains("secret"))
    }

    @Test
    fun `custom credential headers and URL user info are removed`() {
        val result =
            ConversationAuditRedactor.redactText(
                """
                {
                  "X-Api-Key": "header-secret",
                  "endpoint": "https://user:password@example.test/v1"
                }
                """.trimIndent(),
                mediaType = "application/json",
            )

        assertTrue(result.value.contains("[REDACTED:credential]"))
        assertFalse(result.value.contains("header-secret"))
        assertFalse(result.value.contains("user:password"))
    }

    @Test
    fun `basic authorization set cookie and pem private keys are removed`() {
        val result =
            ConversationAuditRedactor.redactText(
                """
                Authorization: Basic dXNlcjpwYXNzd29yZA==
                Set-Cookie: session=secret-cookie
                -----BEGIN PRIVATE KEY-----
                secret-private-material
                -----END PRIVATE KEY-----
                """.trimIndent()
            )

        assertTrue(result.replacementCount >= 3)
        assertFalse(result.value.contains("dXNlcjpwYXNzd29yZA=="))
        assertFalse(result.value.contains("secret-cookie"))
        assertFalse(result.value.contains("secret-private-material"))
    }
}
