package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDiagnosticModelsTest {
    @Test
    fun `diagnostic log retains one thousand newest entries with stable sequence ids`() {
        val log = BrowserDiagnosticLog()

        repeat(1_001) { index -> log.append(entry(timestamp = 10L, event = "EVENT_$index")) }

        val snapshot = log.snapshot()
        assertEquals(1_000, snapshot.size)
        assertEquals("EVENT_1", snapshot.first().event)
        assertEquals("EVENT_1000", snapshot.last().event)
        assertEquals(2L, snapshot.first().sequence)
        assertEquals(1_001L, snapshot.last().sequence)
        assertEquals(snapshot.size, snapshot.map(BrowserDiagnosticEntry::sequence).toSet().size)
    }

    @Test
    fun `diagnostic log serializes concurrent append without loss or duplicate sequence`() {
        val log = BrowserDiagnosticLog(maxEntries = 2_000)
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) { worker ->
            executor.execute {
                repeat(200) { index ->
                    log.append(entry(event = "WORKER_${worker}_$index"))
                }
            }
        }
        executor.shutdown()

        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        val snapshot = log.snapshot()
        assertEquals(1_600, snapshot.size)
        assertEquals(1_600, snapshot.map(BrowserDiagnosticEntry::sequence).toSet().size)
        assertEquals((1L..1_600L).toList(), snapshot.map(BrowserDiagnosticEntry::sequence).sorted())
    }

    @Test
    fun `diagnostic log clears only the requested session or all sessions`() {
        val log = BrowserDiagnosticLog(maxEntries = 10)
        log.append(entry(sessionId = "session-a", event = "A1"))
        log.append(entry(sessionId = "session-b", event = "B1"))
        log.append(entry(sessionId = "session-a", event = "A2"))

        log.clear(BrowserDiagnosticScope.CURRENT_SESSION, "session-a")
        assertEquals(listOf("B1"), log.snapshot().map(BrowserDiagnosticEntry::event))

        log.clear(BrowserDiagnosticScope.CURRENT_SESSION, null)
        assertEquals(1, log.snapshot().size)

        log.clear(BrowserDiagnosticScope.ALL_SESSIONS, null)
        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun `URL projection strips identity fragment query values and sensitive path segments`() {
        assertEquals(
            "https://example.com/account/token/<redacted>?token=<redacted>&mode=<redacted>",
            sanitizeBrowserDiagnosticUrl(
                "https://user:password@Example.COM:443/account/token/secret-value?token=secret&mode=full#private",
            ),
        )
        assertEquals(
            "example.com/path?token=<redacted>&mode=<redacted>",
            sanitizeBrowserDiagnosticUrl("example.com/path?token=secret&mode=full#private"),
        )
        assertEquals("data:<redacted>", sanitizeBrowserDiagnosticUrl("data:text/plain,private-body"))
        assertEquals("file:<redacted>", sanitizeBrowserDiagnosticUrl("file:///private/account.txt"))
    }

    @Test
    fun `entry projection redacts sensitive fields headers paths and long messages`() {
        val sanitized =
            sanitizeBrowserDiagnosticEntry(
                entry(
                    event = "MAIN_DOCUMENT_ERROR",
                    message =
                        "Authorization=\"Bearer auth secret\" token=token-secret " +
                            "password=\"password secret\" https://example.com/path?q=private " +
                            "x".repeat(500),
                    details =
                        linkedMapOf(
                            "Authorization" to "Bearer auth-secret",
                            "Cookie" to "session=cookie-secret",
                            "accessToken" to "token-secret",
                            "password" to "password-secret",
                            "signature" to "signature-secret",
                            "requestHeaders" to "Authorization=header-secret",
                            "privatePath" to "/private/account.txt",
                            "pageTitle" to "private page title",
                            "body" to "private page body",
                            "requestUrl" to "https://example.com/path?token=url-secret",
                            "result" to "ordinary-value",
                        ),
                ),
            )

        assertEquals("<redacted>", sanitized.details.getValue("Authorization"))
        assertEquals("<redacted>", sanitized.details.getValue("Cookie"))
        assertEquals("<redacted>", sanitized.details.getValue("accessToken"))
        assertEquals("<redacted>", sanitized.details.getValue("password"))
        assertEquals("<redacted>", sanitized.details.getValue("signature"))
        assertEquals("<redacted>", sanitized.details.getValue("requestHeaders"))
        assertEquals("<redacted>", sanitized.details.getValue("privatePath"))
        assertEquals("<redacted>", sanitized.details.getValue("pageTitle"))
        assertEquals("<redacted>", sanitized.details.getValue("body"))
        assertEquals(
            "https://example.com/path?token=<redacted>",
            sanitized.details.getValue("requestUrl"),
        )
        assertEquals("ordinary-value", sanitized.details.getValue("result"))
        assertTrue(sanitized.message.length <= 280)
        listOf(
            "auth-secret",
            "auth secret",
            "token-secret",
            "password secret",
            "private",
        ).forEach { secret -> assertFalse(sanitized.message.contains(secret)) }
    }

    @Test
    fun `message projection redacts complete cookie and authorization header values`() {
        val sanitized =
            sanitizeBrowserDiagnosticEntry(
                entry(
                    message =
                        "Cookie: session=cookie-secret; preference=private value\n" +
                            "Authorization: Bearer auth-secret with-spaces\n" +
                            "standalone Bearer standalone-secret\n" +
                            "status=failed",
                ),
            ).message

        assertTrue(sanitized.contains("Cookie=<redacted>"))
        assertTrue(sanitized.contains("Authorization=<redacted>"))
        assertTrue(sanitized.contains("status=failed"))
        listOf(
            "cookie-secret",
            "preference",
            "private value",
            "auth-secret",
            "with-spaces",
            "standalone-secret",
        )
            .forEach { secret -> assertFalse(sanitized.contains(secret)) }
    }

    @Test
    fun `filter applies scope level category query and stable newest first ordering`() {
        val entries =
            listOf(
                entry(
                    timestamp = 20L,
                    sequence = 2L,
                    sessionId = "session-a",
                    level = BrowserDiagnosticLevel.WARNING,
                    category = BrowserDiagnosticCategory.NAVIGATION,
                    event = "NAVIGATION_COMMITTED",
                    host = "example.com",
                ),
                entry(
                    timestamp = 20L,
                    sequence = 3L,
                    sessionId = "session-a",
                    level = BrowserDiagnosticLevel.ERROR,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "RENDERER_GONE",
                    details = mapOf("didCrash" to "true"),
                ),
                entry(
                    timestamp = 30L,
                    sequence = 4L,
                    sessionId = "session-b",
                    level = BrowserDiagnosticLevel.ERROR,
                    category = BrowserDiagnosticCategory.WEBVIEW,
                    event = "MAIN_DOCUMENT_ERROR",
                ),
            )

        assertEquals(
            listOf("RENDERER_GONE", "NAVIGATION_COMMITTED"),
            filterBrowserDiagnosticEntries(
                entries = entries,
                scope = BrowserDiagnosticScope.CURRENT_SESSION,
                activeSessionId = "session-a",
                level = null,
                category = null,
                query = "",
            ).map(BrowserDiagnosticEntry::event),
        )
        assertEquals(
            listOf("MAIN_DOCUMENT_ERROR", "RENDERER_GONE"),
            filterBrowserDiagnosticEntries(
                entries = entries,
                scope = BrowserDiagnosticScope.ALL_SESSIONS,
                activeSessionId = "session-a",
                level = BrowserDiagnosticLevel.ERROR,
                category = BrowserDiagnosticCategory.WEBVIEW,
                query = "",
            ).map(BrowserDiagnosticEntry::event),
        )
        assertEquals(
            listOf("RENDERER_GONE"),
            filterBrowserDiagnosticEntries(
                entries = entries,
                scope = BrowserDiagnosticScope.ALL_SESSIONS,
                activeSessionId = null,
                level = null,
                category = null,
                query = "didCrash",
            ).map(BrowserDiagnosticEntry::event),
        )
    }

    @Test
    fun `diagnostic report is chronological and reapplies redaction`() {
        val report =
            formatBrowserDiagnosticReport(
                listOf(
                    entry(
                        timestamp = 20L,
                        sequence = 2L,
                        event = "SECOND",
                        details = mapOf("Authorization" to "Bearer auth-secret"),
                    ),
                    entry(
                        timestamp = 10L,
                        sequence = 1L,
                        event = "FIRST",
                        details = mapOf("requestUrl" to "https://example.com/?token=url-secret"),
                    ),
                ),
            )

        assertTrue(report.indexOf("FIRST") < report.indexOf("SECOND"))
        assertTrue(report.contains("Authorization=<redacted>"))
        assertTrue(report.contains("token=<redacted>"))
        assertFalse(report.contains("auth-secret"))
        assertFalse(report.contains("url-secret"))
    }

    private fun entry(
        timestamp: Long = 1L,
        sequence: Long = 0L,
        level: BrowserDiagnosticLevel = BrowserDiagnosticLevel.INFO,
        category: BrowserDiagnosticCategory = BrowserDiagnosticCategory.SESSION,
        event: String = "SESSION_CREATED",
        sessionId: String? = "session-a",
        host: String = "",
        message: String = "",
        details: Map<String, String> = emptyMap(),
    ): BrowserDiagnosticEntry =
        BrowserDiagnosticEntry(
            timestamp = timestamp,
            sequence = sequence,
            level = level,
            category = category,
            event = event,
            sessionId = sessionId,
            profile = WebSessionProfile.NORMAL,
            documentToken = "document-a",
            host = host,
            message = message,
            details = details,
        )
}
