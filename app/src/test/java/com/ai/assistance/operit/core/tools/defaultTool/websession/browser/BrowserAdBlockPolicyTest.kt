package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAdBlockPolicyTest {
    @Test
    fun `plain and host anchored rules block matching requests`() {
        val matcher =
            matcher(
                networkRules =
                    listOf(
                        networkRule("plain", "tracking-pixel"),
                        networkRule("host", "||ads.example.com^"),
                    ),
            )

        assertEquals(
            "plain",
            matcher
                .decide(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://cdn.example.org/tracking-pixel.gif",
                )
                ?.ruleId,
        )
        assertEquals(
            "host",
            matcher
                .decide(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://static.ads.example.com/banner.js",
                )
                ?.ruleId,
        )
        assertNull(
            matcher.decide(
                pageUrl = "https://news.example.org/article",
                requestUrl = "https://example.com/content.js",
            ),
        )
    }

    @Test
    fun `exception rule overrides matching block rule`() {
        val matcher =
            matcher(
                networkRules =
                    listOf(
                        networkRule("block", "||ads.example.com^"),
                        networkRule("allow", "@@||ads.example.com/allowed^"),
                    ),
            )

        assertNull(
            matcher.decide(
                pageUrl = "https://news.example.org/article",
                requestUrl = "https://ads.example.com/allowed/banner.js",
            ),
        )
        assertEquals(
            "block",
            matcher
                .decide(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://ads.example.com/blocked/banner.js",
                )
                ?.ruleId,
        )
    }

    @Test
    fun `allowlisted domain disables network and element blocking for subdomains`() {
        val matcher =
            matcher(
                allowlistedDomains = listOf("example.org"),
                networkRules = listOf(networkRule("block", "tracking")),
                elementRules = listOf(elementRule("element", "example.org", ".advert")),
            )

        assertNull(
            matcher.decide(
                pageUrl = "https://m.example.org/article",
                requestUrl = "https://cdn.test/tracking.js",
            ),
        )
        assertTrue(matcher.selectorsForPage("https://m.example.org/article").isEmpty())
    }

    @Test
    fun `domain option scopes a network rule to the page host`() {
        val matcher =
            matcher(
                networkRules =
                    listOf(
                        networkRule(
                            id = "scoped",
                            rule = "tracking\$domain=example.org|~shop.example.org",
                        ),
                    ),
            )

        assertEquals(
            "scoped",
            matcher
                .decide(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://cdn.test/tracking.js",
                )
                ?.ruleId,
        )
        assertNull(
            matcher.decide(
                pageUrl = "https://shop.example.org/product",
                requestUrl = "https://cdn.test/tracking.js",
            ),
        )
        assertNull(
            matcher.decide(
                pageUrl = "https://other.test/article",
                requestUrl = "https://cdn.test/tracking.js",
            ),
        )
    }

    @Test
    fun `subscription parser separates network cosmetic and exception rules`() {
        val result =
            parseBrowserAdBlockSubscription(
                text =
                    """
                    [Adblock Plus 2.0]
                    ! comment
                    ||ads.example.com^
                    @@||ads.example.com/allowed^
                    example.org##.advert
                    example.org#@#.sponsored
                    example.org##div{display:none}
                    """.trimIndent(),
                subscriptionId = "sub",
                subscriptionName = "Test list",
            )

        assertEquals(2, result.networkRules.size)
        assertEquals(2, result.elementRules.size)
        assertEquals(1, result.ignoredLineCount)
        assertFalse(result.elementRules.first().exception)
        assertTrue(result.elementRules.last().exception)
    }

    @Test
    fun `cosmetic exception removes matching selector only on its domain`() {
        val matcher =
            matcher(
                elementRules =
                    listOf(
                        elementRule("global", "", ".advert"),
                        elementRule("sponsored", "example.org", ".sponsored"),
                        elementRule(
                            id = "exception",
                            domain = "news.example.org",
                            selector = ".advert",
                            exception = true,
                        ),
                    ),
            )

        assertEquals(
            listOf(".sponsored"),
            matcher.selectorsForPage("https://news.example.org/article"),
        )
        assertEquals(
            listOf(".advert", ".sponsored"),
            matcher.selectorsForPage("https://shop.example.org/product"),
        )
    }

    @Test
    fun `domain normalization rejects malformed values`() {
        assertEquals("example.org", normalizeBrowserAdBlockDomainInput("https://Example.org/path"))
        assertEquals("sub.example.org", normalizeBrowserAdBlockDomainInput("sub.example.org:443"))
        assertNull(normalizeBrowserAdBlockDomainInput("-invalid.example.org"))
        assertNull(normalizeBrowserAdBlockDomainInput("not a domain"))
    }

    private fun matcher(
        allowlistedDomains: List<String> = emptyList(),
        networkRules: List<BrowserAdBlockNetworkRuleSpec> = emptyList(),
        elementRules: List<BrowserAdBlockElementRuleSpec> = emptyList(),
    ): BrowserAdBlockMatcher =
        BrowserAdBlockMatcher.compile(
            enabled = true,
            allowlistedDomains = allowlistedDomains,
            networkRules = networkRules,
            elementRules = elementRules,
        )

    private fun networkRule(
        id: String,
        rule: String,
    ): BrowserAdBlockNetworkRuleSpec =
        BrowserAdBlockNetworkRuleSpec(
            id = id,
            rule = rule,
            source = BrowserAdBlockRuleSource.CUSTOM,
            sourceName = "Custom",
        )

    private fun elementRule(
        id: String,
        domain: String,
        selector: String,
        exception: Boolean = false,
    ): BrowserAdBlockElementRuleSpec =
        BrowserAdBlockElementRuleSpec(
            id = id,
            domainExpression = domain,
            selector = selector,
            source = BrowserAdBlockRuleSource.CUSTOM,
            sourceName = "Custom",
            exception = exception,
        )
}
