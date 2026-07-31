package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

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
}
