package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPageSourceSupportTest {
    @Test
    fun `capture script preserves document identity and removes Kiyori selection UI`() {
        val script = buildBrowserPageSourceCaptureScript("document-\"one")

        assertTrue(script.contains("document.doctype"))
        assertTrue(script.contains("<!DOCTYPE "))
        assertTrue(script.contains("[data-operit-selection-ui='true']"))
        assertTrue(script.contains("node.remove()"))
        assertTrue(script.contains("__kiyoriPageSourceDocumentToken"))
        assertTrue(script.contains("document-\\\"one"))
        assertTrue(script.contains("source.length > $BROWSER_PAGE_SOURCE_MAX_CHARS"))
    }

    @Test
    fun `capture result parses the JSON string returned by WebView`() {
        val rawValue =
            """"{\"ok\":true,\"source\":\"<!DOCTYPE html>\\n<html></html>\",\"url\":\"https://example.com/page\",\"title\":\"Example\",\"documentToken\":\"token-1\"}""""

        val result = parseBrowserPageSourceCaptureResult(rawValue)

        assertNull(result.errorCode)
        assertEquals("<!DOCTYPE html>\n<html></html>", result.capture?.source)
        assertEquals("https://example.com/page", result.capture?.pageUrl)
        assertEquals("Example", result.capture?.pageTitle)
        assertEquals("token-1", result.capture?.documentToken)
    }

    @Test
    fun `capture result preserves explicit size errors`() {
        val rawValue =
            """"{\"ok\":false,\"error\":\"source_too_large\",\"sourceLength\":1600001}""""

        val result = parseBrowserPageSourceCaptureResult(rawValue)

        assertNull(result.capture)
        assertEquals("source_too_large", result.errorCode)
        assertEquals(1_600_001, result.sourceLength)
    }

    @Test
    fun `source validation rejects empty oversized and null-containing buffers`() {
        assertEquals(
            BrowserPageSourceValidationFailure.EMPTY,
            validateBrowserPageSource(" \n\t"),
        )
        assertEquals(
            BrowserPageSourceValidationFailure.TOO_LARGE,
            validateBrowserPageSource("a".repeat(BROWSER_PAGE_SOURCE_MAX_CHARS + 1)),
        )
        assertEquals(
            BrowserPageSourceValidationFailure.CONTAINS_NULL,
            validateBrowserPageSource("<html>\u0000</html>"),
        )
        assertNull(validateBrowserPageSource("<!DOCTYPE html>\n<html></html>"))
    }

    @Test
    fun `layout analysis reports minified long lines without changing source`() {
        val longLine = "<script>${"x".repeat(BROWSER_PAGE_SOURCE_LONG_LINE_CHARS)}</script>"
        val source = "<html>\n$longLine\n<footer>short</footer>"

        val stats = analyzeBrowserPageSourceLayout(source)

        assertEquals(3, stats.lineCount)
        assertEquals(longLine.length, stats.longestLineCharacters)
        assertEquals(1, stats.longLineCount)
        assertEquals("<", source.substring(browserPageSourceLineStartOffset(source, 2), browserPageSourceLineStartOffset(source, 2) + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `line start lookup rejects a line outside the current source`() {
        browserPageSourceLineStartOffset("<html>\n</html>", 3)
    }

    @Test
    fun `apply script quotes edited HTML and requires the captured document token`() {
        val source =
            """
            <!DOCTYPE html>
            <html><body data-label="A 'quote'"><script>const value = "</script>";</script></body></html>
            """.trimIndent()
        val script =
            buildBrowserPageSourceApplyScript(
                source = source,
                expectedDocumentToken = "expected-\"token",
                nextDocumentToken = "next-token",
            )

        assertTrue(script.contains("__kiyoriPageSourceDocumentToken"))
        assertTrue(script.contains("!== expectedToken"))
        assertTrue(script.contains("document_changed"))
        assertTrue(script.contains("new DOMParser().parseFromString(source, \"text/html\")"))
        assertTrue(script.contains("document.open()"))
        assertTrue(script.contains("document.write(source)"))
        assertTrue(script.contains("document.close()"))
        assertTrue(script.contains("expected-\\\"token"))
        assertTrue(script.contains("\\n"))
        assertTrue(script.contains("\\\"</script>\\\""))
    }

    @Test
    fun `apply result distinguishes success from document replacement`() {
        val successRaw =
            """"{\"ok\":true,\"documentToken\":\"token-2\",\"url\":\"https://example.com/edited\",\"title\":\"Edited\"}""""
        val changedRaw =
            """"{\"ok\":false,\"error\":\"document_changed\"}""""

        val success = parseBrowserPageSourceApplyResult(successRaw)
        val changed = parseBrowserPageSourceApplyResult(changedRaw)

        assertTrue(success.applied)
        assertEquals("token-2", success.documentToken)
        assertEquals("https://example.com/edited", success.pageUrl)
        assertEquals("Edited", success.pageTitle)
        assertFalse(changed.applied)
        assertEquals("document_changed", changed.errorCode)
    }

    @Test
    fun `apply result rejects success without the next document token`() {
        val rawValue =
            """"{\"ok\":true,\"url\":\"https://example.com/edited\",\"title\":\"Edited\"}""""

        val result = parseBrowserPageSourceApplyResult(rawValue)

        assertFalse(result.applied)
        assertEquals("invalid_result", result.errorCode)
    }

    @Test
    fun `diff reports detailed line changes for normal pages`() {
        val diff =
            buildBrowserPageSourceDiff(
                original = "<html>\n<body>before</body>\n</html>",
                revised = "<html>\n<body>after</body>\n<footer>new</footer>\n</html>",
            )

        assertTrue(diff.hasDetailedDiff)
        assertEquals(2, diff.additions)
        assertEquals(1, diff.deletions)
        assertTrue(diff.unifiedDiff.contains("--- captured-page.html"))
        assertTrue(diff.unifiedDiff.contains("+++ edited-page.html"))
    }

    @Test
    fun `diff avoids constructing a detailed patch for very large pages`() {
        val original = "a".repeat(150_001)
        val revised = "b".repeat(150_001)

        val diff = buildBrowserPageSourceDiff(original, revised)

        assertFalse(diff.hasDetailedDiff)
        assertNull(diff.additions)
        assertNull(diff.deletions)
        assertTrue(diff.unifiedDiff.isEmpty())
        assertEquals(original.length, diff.originalCharacters)
        assertEquals(revised.length, diff.revisedCharacters)
    }
}
