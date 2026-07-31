package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptMatcherTest {
    @Test
    fun `script without positive rules matches every non-excluded page`() {
        val metadata = ParsedUserscriptMetadata(name = "Global")

        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://example.com/path",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `match pattern ignores query and fragment`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "Exact path",
                matches = listOf("https://example.com/path"),
            )

        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://example.com/path?query=1#result",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `regular expression include and exclude are supported`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "Regex",
                includes = listOf("/^https:\\/\\/example\\.com\\/items\\/\\d+$/"),
                excludes = listOf("/\\/items\\/13$/"),
            )

        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://example.com/items/12",
                isTopFrame = true,
            ),
        )
        assertFalse(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://example.com/items/13",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `all urls match pattern covers standard schemes`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "All URLs",
                matches = listOf("<all_urls>"),
            )

        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://example.com/",
                isTopFrame = true,
            ),
        )
        assertFalse(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "about:blank",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `noframes still rejects child frames`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "Top frame only",
                noFrames = true,
            )

        assertFalse(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://example.com/",
                isTopFrame = false,
            ),
        )
    }

    @Test
    fun `http star and explicit ports match common userscript rules`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "Ports",
                matches =
                    listOf(
                        "http*://*.example.com:8443/*",
                        "https://secure.example.com:443/*",
                    ),
            )

        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://api.example.com:8443/path",
                isTopFrame = true,
            ),
        )
        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://secure.example.com/path",
                isTopFrame = true,
            ),
        )
        assertFalse(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://api.example.com:9443/path",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `host suffix and wildcard tld patterns are supported`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "Hosts",
                matches =
                    listOf(
                        "https://.example.com/*",
                        "https://www.example.*/*",
                    ),
            )

        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://deep.api.example.com/path",
                isTopFrame = true,
            ),
        )
        assertTrue(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://www.example.org/path",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `regular expression flags and url include fragment handling are supported`() {
        val caseInsensitive =
            ParsedUserscriptMetadata(
                name = "Regex flags",
                includes = listOf("/^https:\\/\\/example\\.com\\/ITEMS\\/\\d+$/i"),
            )
        val urlLike =
            ParsedUserscriptMetadata(
                name = "URL include",
                includes = listOf("https://example.com/path*"),
            )

        assertTrue(
            UserscriptMatcher.matches(
                metadata = caseInsensitive,
                pageUrl = "https://example.com/items/12",
                isTopFrame = true,
            ),
        )
        assertTrue(
            UserscriptMatcher.matches(
                metadata = urlLike,
                pageUrl = "https://example.com/path?query=1#section",
                isTopFrame = true,
            ),
        )
    }

    @Test
    fun `connect domain grants include subdomains and respect explicit ports`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "Connect",
                connects = listOf("example.net", "api.example.org:8443"),
            )

        assertTrue(
            UserscriptMatcher.isConnectAllowed(
                metadata = metadata,
                pageUrl = "https://page.example.com/",
                targetUrl = "https://cdn.example.net/data",
            ),
        )
        assertTrue(
            UserscriptMatcher.isConnectAllowed(
                metadata = metadata,
                pageUrl = "https://page.example.com/",
                targetUrl = "https://api.example.org:8443/data",
            ),
        )
        assertFalse(
            UserscriptMatcher.isConnectAllowed(
                metadata = metadata,
                pageUrl = "https://page.example.com/",
                targetUrl = "https://api.example.org:9443/data",
            ),
        )
    }

    @Test
    fun `diagnostics expose the exclusion rule that won`() {
        val result =
            UserscriptMatcher.diagnose(
                metadata =
                    ParsedUserscriptMetadata(
                        name = "Diagnostic",
                        matches = listOf("https://example.com/*"),
                        excludeMatches = listOf("https://example.com/private/*"),
                    ),
                pageUrl = "https://example.com/private/account",
                isTopFrame = true,
            )

        assertFalse(result.matches)
        assertEquals(UserscriptMatchReason.EXCLUDED_BY_MATCH, result.reason)
        assertEquals("https://example.com/private/*", result.rule)
    }

    @Test
    fun `light novel library real match rules cover wenku8 reader domains`() {
        val metadata =
            ParsedUserscriptMetadata(
                name = "轻小说文库+",
                matches =
                    listOf(
                        "http*://*.wenku8.com/*",
                        "http*://*.wenku8.net/*",
                        "http*://*.wenku8.cc/*",
                    ),
            )

        listOf(
            "https://www.wenku8.net/novel/3/3197/158328.htm",
            "https://www.wenku8.com/book/3197.htm",
            "http://m.wenku8.cc/modules/article/reader.php?aid=3197",
        ).forEach { pageUrl ->
            val result =
                UserscriptMatcher.diagnose(
                    metadata = metadata,
                    pageUrl = pageUrl,
                    isTopFrame = true,
                )

            assertTrue(pageUrl, result.matches)
            assertEquals(UserscriptMatchReason.MATCHED_BY_MATCH, result.reason)
        }

        assertFalse(
            UserscriptMatcher.matches(
                metadata = metadata,
                pageUrl = "https://greasyfork.org/zh-CN/scripts/539514",
                isTopFrame = true,
            ),
        )
    }
}
