package com.ai.assistance.operit.data.audit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAuditExternalShareRedactorTest {
    @Test
    fun `external review pseudonymizes private paths accounts and device identifiers`() {
        val result =
            ConversationAuditExternalShareRedactor.redact(
                """
                {
                  "username": "alice",
                  "deviceId": "phone-123",
                  "email": "alice@example.test",
                  "windows": "C:\\Users\\alice\\secret\\trace.txt",
                  "android": "/data/user/0/com.kiyori/files/private.txt"
                }
                """.trimIndent(),
                mediaType = "application/json",
            )

        assertTrue(result.replacementCount >= 5)
        assertTrue(result.value.contains("[IDENTITY:"))
        assertTrue(result.value.contains("[ACCOUNT:"))
        assertTrue(result.value.contains("[PRIVATE_PATH:"))
        assertFalse(result.value.contains("alice@example.test"))
        assertFalse(result.value.contains("phone-123"))
        assertFalse(result.value.contains("C:\\\\Users\\\\alice"))
        assertFalse(result.value.contains("/data/user/0/com.kiyori"))
    }
}
