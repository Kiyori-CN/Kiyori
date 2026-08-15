package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNetworkLogPolicyTest {
    @Test
    fun `classification uses main frame accept header and URL extension`() {
        assertEquals(
            BrowserNetworkRequestCategory.WEB,
            classifyBrowserNetworkRequest(
                url = "https://example.com/watch.mp4",
                acceptHeader = "video/mp4",
                isMainFrame = true,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.VIDEO,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/live/master.M3U8?token=1",
                acceptHeader = "*/*",
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.AUDIO,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/stream",
                acceptHeader = "audio/aac",
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.IMAGE,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/cover.AVIF#preview",
                acceptHeader = null,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.WEB,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/app.css?v=3",
                acceptHeader = "*/*",
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.OTHER,
            classifyBrowserNetworkRequest(
                url = "https://api.example.com/binary",
                acceptHeader = "application/octet-stream",
                isMainFrame = false,
            ),
        )
    }

    @Test
    fun `filter returns newest first and searches URL or method`() {
        val entries =
            listOf(
                entry("GET", "https://example.com/index.html", BrowserNetworkRequestCategory.WEB, 1L),
                entry("POST", "https://api.example.com/media.mp4", BrowserNetworkRequestCategory.VIDEO, 2L),
                entry("GET", "https://img.example.com/cover.png", BrowserNetworkRequestCategory.IMAGE, 3L),
            )

        assertEquals(
            listOf(3L, 2L, 1L),
            filterBrowserNetworkLogEntries(entries, category = null, query = "").map { it.timestamp },
        )
        assertEquals(
            listOf(2L),
            filterBrowserNetworkLogEntries(entries, BrowserNetworkRequestCategory.VIDEO, "").map { it.timestamp },
        )
        assertEquals(
            listOf(2L),
            filterBrowserNetworkLogEntries(entries, category = null, query = "post").map { it.timestamp },
        )
        assertEquals(
            listOf(3L),
            filterBrowserNetworkLogEntries(entries, category = null, query = "COVER").map { it.timestamp },
        )

        val blocked =
            entries +
                WebSessionBrowserNetworkEntry(
                    method = "GET",
                    url = "https://ads.example.com/banner.js",
                    isMainFrame = false,
                    isStatic = true,
                    category = BrowserNetworkRequestCategory.WEB,
                    timestamp = 4L,
                    blocked = true,
                    blockingRule = "||ads.example.com^",
                    blockingSourceName = "My filters",
                )
        assertEquals(
            listOf(4L),
            filterBrowserNetworkLogEntries(
                entries = blocked,
                category = null,
                query = "",
                blockedOnly = true,
            ).map { it.timestamp },
        )
        assertEquals(
            listOf(4L),
            filterBrowserNetworkLogEntries(
                entries = blocked,
                category = null,
                query = "filters",
            ).map { it.timestamp },
        )
    }

    @Test
    fun `element interception entries participate in blocked and selector filtering`() {
        val elementEntry =
            WebSessionBrowserNetworkEntry(
                method = "DOM",
                url = "https://news.example.org/article",
                isMainFrame = true,
                isStatic = false,
                category = BrowserNetworkRequestCategory.OTHER,
                timestamp = 5L,
                kind = BrowserNetworkLogEntryKind.ELEMENT,
                blocked = true,
                blockingRule = "video.ad",
                blockingSourceName = "自定义元素规则",
                elementSelector = "video.ad",
            )

        assertEquals(
            listOf(5L),
            filterBrowserNetworkLogEntries(
                entries = listOf(elementEntry),
                category = null,
                query = "",
                blockedOnly = true,
            ).map { it.timestamp },
        )
        assertEquals(
            listOf(5L),
            filterBrowserNetworkLogEntries(
                entries = listOf(elementEntry),
                category = null,
                query = "video.ad",
            ).map { it.timestamp },
        )
    }

    @Test
    fun `ad marking navigation policy exposes three distinct user choices`() {
        assertEquals(
            listOf(
                BrowserAdMarkingNavigationPolicy.DEFAULT,
                BrowserAdMarkingNavigationPolicy.ASK,
                BrowserAdMarkingNavigationPolicy.BLOCK,
            ),
            browserAdMarkingSelectablePolicies(),
        )
        assertEquals(
            "allow",
            BrowserAdMarkingNavigationPolicy.DEFAULT.toJavascriptValue(),
        )
        assertEquals(
            "ask",
            BrowserAdMarkingNavigationPolicy.ASK.toJavascriptValue(),
        )
        assertEquals(
            "block",
            BrowserAdMarkingNavigationPolicy.BLOCK.toJavascriptValue(),
        )
    }

    @Test
    fun `normal browsing navigation policy only acts on cross domain targets`() {
        val currentPage = "https://www.example.com/article"
        val sameDomain = "https://example.com/next"
        val otherDomain = "https://outside.test/landing"

        BrowserAdMarkingNavigationPolicy.entries.forEach { policy ->
            assertEquals(
                BrowserExternalNavigationDecision.ALLOW,
                resolveBrowserExternalNavigationDecision(
                    policy = policy,
                    pageUrl = currentPage,
                    targetUrl = sameDomain,
                ),
            )
        }
        assertEquals(
            BrowserExternalNavigationDecision.ALLOW,
            resolveBrowserExternalNavigationDecision(
                policy = BrowserAdMarkingNavigationPolicy.DEFAULT,
                pageUrl = currentPage,
                targetUrl = otherDomain,
            ),
        )
        assertEquals(
            BrowserExternalNavigationDecision.ASK,
            resolveBrowserExternalNavigationDecision(
                policy = BrowserAdMarkingNavigationPolicy.ASK,
                pageUrl = currentPage,
                targetUrl = otherDomain,
            ),
        )
        assertEquals(
            BrowserExternalNavigationDecision.BLOCK,
            resolveBrowserExternalNavigationDecision(
                policy = BrowserAdMarkingNavigationPolicy.BLOCK,
                pageUrl = currentPage,
                targetUrl = otherDomain,
            ),
        )
    }

    @Test
    fun `third party comparison keeps exact hosts and subdomains together`() {
        assertFalse(
            isThirdPartyBrowserNetworkRequest(
                pageUrl = "https://example.com/page",
                requestUrl = "https://static.example.com/app.js",
            ),
        )
        assertFalse(
            isThirdPartyBrowserNetworkRequest(
                pageUrl = "https://www.example.com/page",
                requestUrl = "https://example.com/api",
            ),
        )
        assertTrue(
            isThirdPartyBrowserNetworkRequest(
                pageUrl = "https://example.com/page",
                requestUrl = "https://cdn.other.test/app.js",
            ),
        )
        assertFalse(
            isThirdPartyBrowserNetworkRequest(
                pageUrl = "about:blank",
                requestUrl = "https://cdn.other.test/app.js",
            ),
        )
    }

    @Test
    fun `compact URL preserves host and obeys requested width`() {
        val url =
            "https://media.example.com/very/long/path/to/a/resource/master.m3u8?token=abcdefghijklmnopqrstuvwxyz"
        val compact = compactBrowserNetworkLogUrl(url, maxLength = 48)

        assertTrue(compact.startsWith("media.example.com/..."))
        assertTrue(compact.length <= 48)
        assertEquals("https://example.com/a", compactBrowserNetworkLogUrl("https://example.com/a", 48))
    }

    private fun entry(
        method: String,
        url: String,
        category: BrowserNetworkRequestCategory,
        timestamp: Long,
    ): WebSessionBrowserNetworkEntry =
        WebSessionBrowserNetworkEntry(
            method = method,
            url = url,
            isMainFrame = false,
            isStatic = false,
            category = category,
            timestamp = timestamp,
        )
}
