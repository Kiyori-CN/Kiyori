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
            BrowserNetworkRequestCategory.STYLE,
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
    fun `known resource extensions outrank mixed browser accept ranges`() {
        val navigationAccept =
            "text/html,application/xhtml+xml,application/xml;q=0.9," +
                "image/avif,image/webp,image/apng,*/*;q=0.8"
        val imageAccept = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

        assertEquals(
            BrowserNetworkRequestCategory.WEB,
            classifyBrowserNetworkRequest(
                url = "https://example.com/frame.html?embedded=1",
                acceptHeader = imageAccept,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.SCRIPT,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/runtime.js?v=1",
                acceptHeader = navigationAccept,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.STYLE,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/theme.css",
                acceptHeader = imageAccept,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.DATA,
            classifyBrowserNetworkRequest(
                url = "https://api.example.com/config.json",
                acceptHeader = imageAccept,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.IMAGE,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/logo.SVG?theme=dark",
                acceptHeader = navigationAccept,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.FONT,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/site.woff2",
                acceptHeader = imageAccept,
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.DATA,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/app.webmanifest",
                acceptHeader = imageAccept,
                isMainFrame = false,
            ),
        )
    }

    @Test
    fun `extensionless requests use preferred concrete accept media range`() {
        assertEquals(
            BrowserNetworkRequestCategory.WEB,
            classifyBrowserNetworkRequest(
                url = "https://example.com/render",
                acceptHeader =
                    "application/json;q=0.4,text/html;q=0.9,image/avif;q=0.8,*/*;q=0.1",
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.IMAGE,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/asset",
                acceptHeader = "image/avif,image/webp,image/svg+xml,image/*,*/*;q=0.8",
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.SCRIPT,
            classifyBrowserNetworkRequest(
                url = "https://cdn.example.com/runtime",
                acceptHeader = "application/javascript,*/*;q=0.1",
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserNetworkRequestCategory.OTHER,
            classifyBrowserNetworkRequest(
                url = "https://api.example.com/opaque",
                acceptHeader = "image/png;q=0,*/*",
                isMainFrame = false,
            ),
        )
    }

    @Test
    fun `filter groups the static resource directory and searches URL or method`() {
        val entries =
            listOf(
                entry("GET", "https://example.com/index.html", BrowserNetworkRequestCategory.WEB, 1L),
                entry("POST", "https://api.example.com/media.mp4", BrowserNetworkRequestCategory.VIDEO, 2L),
                entry("GET", "https://img.example.com/cover.png", BrowserNetworkRequestCategory.IMAGE, 3L),
            )

        assertEquals(
            listOf(2L, 3L, 1L),
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
    fun `image viewer snapshot keeps current filtered request images and selected page`() {
        val selected =
            entry(
                method = "GET",
                url = "https://img.example.com/selected.png",
                category = BrowserNetworkRequestCategory.IMAGE,
                timestamp = 3L,
            )
        val snapshot =
            buildBrowserNetworkImageViewerSnapshot(
                entries =
                    listOf(
                        entry(
                            method = "GET",
                            url = "https://img.example.com/first.jpg",
                            category = BrowserNetworkRequestCategory.IMAGE,
                            timestamp = 1L,
                        ),
                        WebSessionBrowserNetworkEntry(
                            method = "DOM",
                            url = "https://img.example.com/element.png",
                            isMainFrame = false,
                            isStatic = false,
                            category = BrowserNetworkRequestCategory.IMAGE,
                            timestamp = 2L,
                            kind = BrowserNetworkLogEntryKind.ELEMENT,
                        ),
                        selected,
                        entry(
                            method = "GET",
                            url = "data:image/png;base64,AA",
                            category = BrowserNetworkRequestCategory.IMAGE,
                            timestamp = 4L,
                        ),
                        entry(
                            method = "GET",
                            url = "https://example.com/page",
                            category = BrowserNetworkRequestCategory.WEB,
                            timestamp = 5L,
                        ),
                    ),
                selectedResourceIdentity = selected.resourceIdentity,
            )

        assertEquals(
            listOf(
                "https://img.example.com/first.jpg",
                "https://img.example.com/selected.png",
            ),
            requireNotNull(snapshot).items.map(BrowserImageViewerItem::url),
        )
        assertEquals(1, snapshot.initialPage)
        assertEquals(
            null,
            buildBrowserNetworkImageViewerSnapshot(
                entries = listOf(selected),
                selectedResourceIdentity = "https://img.example.com/missing.png",
            ),
        )
    }

    @Test
    fun `network image request identity preserves observed headers and fills missing session values`() {
        val observed =
            buildBrowserNetworkRequestHeaders(
                observedHeaders =
                    mapOf(
                        "user-agent" to "Observed UA",
                        "COOKIE" to "observed=1",
                        "Referer" to "https://observed.example/page",
                    ),
                appliedUserAgent = "Session UA",
                cookie = "session=1",
                pageUrl = "https://page.example/article",
            )
        assertEquals("Observed UA", observed.headerValue("User-Agent"))
        assertEquals("observed=1", observed.headerValue("Cookie"))
        assertEquals("https://observed.example/page", observed.headerValue("Referer"))

        val filled =
            buildBrowserNetworkRequestHeaders(
                observedHeaders = mapOf("Accept" to "image/avif,image/webp"),
                appliedUserAgent = "Session UA",
                cookie = "session=1",
                pageUrl = "https://page.example/article",
            )
        assertEquals("Session UA", filled.headerValue("User-Agent"))
        assertEquals("session=1", filled.headerValue("Cookie"))
        assertEquals("https://page.example/article", filled.headerValue("Referer"))

        val nonHttpPage =
            buildBrowserNetworkRequestHeaders(
                observedHeaders = emptyMap(),
                appliedUserAgent = "",
                cookie = null,
                pageUrl = "about:blank",
            )
        assertFalse(nonHttpPage.keys.any { it.equals("Referer", ignoreCase = true) })
        assertFalse(nonHttpPage.keys.any { it.equals("User-Agent", ignoreCase = true) })
        assertFalse(nonHttpPage.keys.any { it.equals("Cookie", ignoreCase = true) })
    }

    @Test
    fun `image viewer drag alpha is symmetric monotonic and dismisses beyond touch slop`() {
        assertEquals(1f, browserImageViewerBackgroundAlpha(0f, 1_000f))
        val shortDrag = browserImageViewerBackgroundAlpha(100f, 1_000f)
        val longDrag = browserImageViewerBackgroundAlpha(300f, 1_000f)
        assertEquals(shortDrag, browserImageViewerBackgroundAlpha(-100f, 1_000f))
        assertTrue(shortDrag < 1f)
        assertTrue(longDrag < shortDrag)
        assertTrue(longDrag >= 0f)
        assertEquals(1f, browserImageViewerBackgroundAlpha(100f, 0f))

        assertFalse(shouldDismissBrowserImageViewer(verticalOffsetPx = 8f, touchSlopPx = 8f))
        assertTrue(shouldDismissBrowserImageViewer(verticalOffsetPx = 9f, touchSlopPx = 8f))
        assertTrue(shouldDismissBrowserImageViewer(verticalOffsetPx = -9f, touchSlopPx = 8f))
    }

    @Test
    fun `image viewer pinch scale is bounded and starts at fit scale`() {
        assertEquals(1f, BROWSER_IMAGE_VIEWER_MIN_SCALE)
        assertEquals(5f, BROWSER_IMAGE_VIEWER_MAX_SCALE)
        assertEquals(
            BROWSER_IMAGE_VIEWER_MIN_SCALE,
            clampBrowserImageViewerScale(0.25f),
        )
        assertEquals(
            BROWSER_IMAGE_VIEWER_MAX_SCALE,
            clampBrowserImageViewerScale(8f),
        )
        assertEquals(2.5f, clampBrowserImageViewerScale(2.5f))
    }

    @Test
    fun `resource identity removes fragment but preserves signed query variants`() {
        assertEquals(
            "https://example.com/media.mp4?token=one",
            normalizeBrowserResourceIdentityUrl(
                "HTTPS://EXAMPLE.COM:443/media.mp4?token=one#preview",
            ),
        )
        assertTrue(
            normalizeBrowserResourceIdentityUrl(
                "https://example.com/media.mp4?token=one",
            ) !=
                normalizeBrowserResourceIdentityUrl(
                    "https://example.com/media.mp4?token=two",
                ),
        )
    }

    @Test
    fun `same document resource merges into one stable directory entry`() {
        val first =
            BrowserNetworkRequestEntry(
                method = "GET",
                url = "https://cdn.example.com/file.bin#first",
                isMainFrame = false,
                isStatic = true,
                category = BrowserNetworkRequestCategory.OTHER,
                headers = mapOf("Accept" to "*/*"),
                documentToken = "document-1",
                timestamp = 10L,
                firstSeenAt = 10L,
                lastSeenAt = 10L,
            )
        val second =
            BrowserNetworkRequestEntry(
                method = "GET",
                url = "https://cdn.example.com/file.bin#second",
                isMainFrame = false,
                isStatic = true,
                category = BrowserNetworkRequestCategory.VIDEO,
                headers = mapOf("Accept" to "video/mp4", "Referer" to "https://example.com/"),
                documentToken = "document-1",
                timestamp = 20L,
                firstSeenAt = 20L,
                lastSeenAt = 20L,
            )

        val merged = mergeBrowserNetworkResourceEntry(first, second)
        assertEquals(2, merged.requestCount)
        assertEquals(BrowserNetworkRequestCategory.VIDEO, merged.category)
        assertEquals(10L, merged.firstSeenAt)
        assertEquals(20L, merged.lastSeenAt)
        assertEquals("video/mp4", merged.headers["Accept"])
        assertEquals("https://example.com/", merged.headers["Referer"])
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

    private fun Map<String, String>.headerValue(name: String): String? =
        entries.firstOrNull { entry -> entry.key.equals(name, ignoreCase = true) }?.value
}
