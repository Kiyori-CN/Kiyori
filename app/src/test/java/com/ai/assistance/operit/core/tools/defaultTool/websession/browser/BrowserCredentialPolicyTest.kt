package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCredentialPolicyTest {
    @Test
    fun `credential vault snapshot separates loading available and unavailable states`() {
        assertFalse(BrowserCredentialVaultSnapshot(isLoading = true).isAvailable)
        assertTrue(BrowserCredentialVaultSnapshot().isAvailable)
        assertFalse(
            BrowserCredentialVaultSnapshot(errorMessage = "vault unavailable").isAvailable,
        )
    }

    @Test
    fun `credential origins are exact normalized HTTP and HTTPS authorities`() {
        assertEquals(
            "https://example.com",
            normalizeBrowserCredentialOrigin("https://Example.com:443/login?token=secret"),
        )
        assertEquals(
            "http://example.com",
            normalizeBrowserCredentialOrigin("http://example.com:80/account"),
        )
        assertEquals(
            "https://example.com:8443",
            normalizeBrowserCredentialOrigin("https://example.com:8443/login"),
        )
        assertNull(normalizeBrowserCredentialOrigin("ftp://example.com/login"))
        assertNull(normalizeBrowserCredentialOrigin("https://user@example.com/login"))
        assertNull(normalizeBrowserCredentialOrigin("about:blank"))
    }

    @Test
    fun `credential display URLs remove query and fragment secrets`() {
        assertEquals(
            "https://example.com/accounts/login",
            sanitizeBrowserCredentialPageUrl(
                "https://example.com/accounts/login?access_token=secret#callback",
            ),
        )
    }

    @Test
    fun `javascript credential literals escape quotes controls and line separators`() {
        assertEquals(
            "\"quote\\\" slash\\\\ line\\n\\u2028\\u2029\"",
            quoteBrowserJavascriptString("quote\" slash\\ line\n\u2028\u2029"),
        )
    }

    @Test
    fun `credential capture and autofill scripts never submit the page`() {
        val capture =
            browserCredentialCaptureScript(
                documentToken = "document-token",
                enabled = true,
            )
        val disabled =
            browserCredentialCaptureScript(
                documentToken = "document-token",
                enabled = false,
            )
        val autofill =
            browserCredentialAutofillScript(
                BrowserSavedCredential(
                    id = "51dd9427-a3a6-46e7-a834-5c2ff94b2ce8",
                    origin = "https://example.com",
                    pageUrl = "https://example.com/login",
                    username = "user@example.com",
                    password = "p@ss\"word",
                    usernameSelector = "#account",
                    passwordSelector = "#password",
                    updatedAtEpochMillis = 1L,
                ),
            )

        assertTrue(capture.contains("KiyoriCredentialBridge.capture"))
        assertTrue(capture.contains("passwords.length !== 1"))
        assertTrue(capture.contains("document.querySelectorAll(idSelector).length === 1"))
        assertTrue(capture.contains("document.addEventListener(\"submit\""))
        assertTrue(capture.contains("document.addEventListener(\"keydown\""))
        assertTrue(disabled.contains("if (!false)"))
        assertTrue(autofill.contains("document.querySelector"))
        assertTrue(autofill.contains("MutationObserver"))
        assertTrue(autofill.contains("dispatchEvent(new Event(\"input\""))
        assertTrue(autofill.contains("user@example.com"))
        assertFalse(capture.contains(".submit()"))
        assertFalse(autofill.contains(".submit()"))
    }

    @Test
    fun `credential vault codec encrypts the full record and round trips`() {
        val credential =
            BrowserSavedCredential(
                id = "51dd9427-a3a6-46e7-a834-5c2ff94b2ce8",
                origin = "https://example.com",
                pageUrl = "https://example.com/login",
                username = "alice@example.com",
                password = "vault-secret",
                usernameSelector = "#account",
                passwordSelector = "#password",
                updatedAtEpochMillis = 10L,
            )

        val encoded =
            encodeEncryptedBrowserCredentialVault(
                credentials = listOf(credential),
                cipher = TestBrowserCredentialCipher,
            )

        assertFalse(encoded.contains("alice@example.com"))
        assertFalse(encoded.contains("vault-secret"))
        assertFalse(encoded.contains("https://example.com"))
        assertEquals(
            listOf(credential),
            decodeEncryptedBrowserCredentialVault(
                rawPayload = encoded,
                cipher = TestBrowserCredentialCipher,
            ),
        )
    }

    private object TestBrowserCredentialCipher : BrowserCredentialRecordCipher {
        override fun encrypt(
            recordId: String,
            plaintext: ByteArray,
        ): EncryptedBrowserCredential =
            EncryptedBrowserCredential(
                ivBase64 = recordId,
                ciphertextBase64 = Base64.getEncoder().encodeToString(plaintext),
            )

        override fun decrypt(
            recordId: String,
            ivBase64: String,
            ciphertextBase64: String,
        ): ByteArray {
            assertEquals(recordId, ivBase64)
            return Base64.getDecoder().decode(
                ciphertextBase64.toByteArray(StandardCharsets.UTF_8),
            )
        }
    }
}
