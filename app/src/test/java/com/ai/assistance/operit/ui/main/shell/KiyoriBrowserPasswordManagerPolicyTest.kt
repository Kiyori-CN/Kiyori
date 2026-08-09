package com.ai.assistance.operit.ui.main.shell

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserSavedCredentialSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class KiyoriBrowserPasswordManagerPolicyTest {
    private val credentials =
        listOf(
            BrowserSavedCredentialSummary(
                id = "one",
                origin = "https://accounts.example.com",
                pageUrl = "https://accounts.example.com/login",
                host = "accounts.example.com",
                username = "alice@example.com",
                updatedAtEpochMillis = 2L,
            ),
            BrowserSavedCredentialSummary(
                id = "two",
                origin = "https://forum.example.net",
                pageUrl = "https://forum.example.net/member.php",
                host = "forum.example.net",
                username = "Bob",
                updatedAtEpochMillis = 1L,
            ),
        )

    @Test
    fun `password manager searches website URL and account without reordering`() {
        assertEquals(credentials, filterBrowserCredentialSummaries(credentials, ""))
        assertEquals(
            listOf("one"),
            filterBrowserCredentialSummaries(credentials, "ALICE").map { item -> item.id },
        )
        assertEquals(
            listOf("two"),
            filterBrowserCredentialSummaries(credentials, "member.php").map { item -> item.id },
        )
        assertEquals(
            listOf("one", "two"),
            filterBrowserCredentialSummaries(credentials, "example").map { item -> item.id },
        )
    }
}
