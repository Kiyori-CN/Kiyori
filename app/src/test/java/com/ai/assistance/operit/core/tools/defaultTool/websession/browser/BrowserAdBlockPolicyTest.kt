package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun `subscription parser keeps standard generic selectors and rejects unsupported actions`() {
        val result =
            parseBrowserAdBlockSubscription(
                text =
                    """
                    ##.generic-ad
                    example.org##.site-ad
                    example.org#?#div:has-text(Sponsored)
                    ||redirect.example^${'$'}redirect=noopjs
                    ||cdn.example^${'$'}script,third-party
                    """.trimIndent(),
                subscriptionId = "sub",
                subscriptionName = "Compatibility",
            )

        assertEquals(1, result.networkRules.size)
        assertEquals(2, result.elementRules.size)
        assertEquals("", result.elementRules.first().domainExpression)
        assertEquals(2, result.ignoredLineCount)
    }

    @Test
    fun `single pass subscription compilation matches parse then compile semantics`() {
        val text =
            """
            [Adblock Plus 2.0]
            ! comment
            ||ads.example.com^
            @@||ads.example.com/allowed^
            /tracker-[0-9]+\.gif/
            ||metrics.example.com^${'$'}badfilter
            example.org##.advert
            example.org#@#.sponsored
            example.org#?#div:has-text(Sponsored)
            malformed_domain##.broken
            """.trimIndent()
        val parsed =
            parseBrowserAdBlockSubscription(
                text = text,
                subscriptionId = "sub",
                subscriptionName = "Test list",
            )
        val legacyRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                id = "sub",
                networkRules = parsed.networkRules,
                elementRules = parsed.elementRules,
            )
        val compiled =
            text
                .reader()
                .buffered()
                .use { reader ->
                    compileBrowserAdBlockSubscription(
                        reader = reader,
                        subscriptionId = "sub",
                        subscriptionName = "Test list",
                    )
                }

        assertEquals(parsed.toRuleCounts(), compiled.counts)
        assertEquals(
            legacyRuleSet.networkRules.map(CompiledBrowserAdBlockNetworkRule::spec),
            compiled.ruleSet.networkRules.map(CompiledBrowserAdBlockNetworkRule::spec),
        )
        assertEquals(
            legacyRuleSet.elementRules.map(CompiledBrowserAdBlockElementRule::spec),
            compiled.ruleSet.elementRules.map(CompiledBrowserAdBlockElementRule::spec),
        )
        assertEquals(legacyRuleSet.badFilters, compiled.ruleSet.badFilters)

        val legacyMatcher =
            BrowserAdBlockMatcher.fromCompiled(
                enabled = true,
                allowlistedDomains = emptyList(),
                ruleSets = listOf(legacyRuleSet),
            )
        val compiledMatcher =
            BrowserAdBlockMatcher.fromCompiled(
                enabled = true,
                allowlistedDomains = emptyList(),
                ruleSets = listOf(compiled.ruleSet),
            )
        val requests =
            listOf(
                "https://ads.example.com/banner.js",
                "https://ads.example.com/allowed/banner.js",
                "https://cdn.example.com/tracker-42.gif",
                "https://metrics.example.com/pixel.gif",
                "https://cdn.example.com/content.js",
            )
        requests.forEach { requestUrl ->
            assertEquals(
                legacyMatcher.decide("https://example.org/article", requestUrl),
                compiledMatcher.decide("https://example.org/article", requestUrl),
            )
        }
        assertEquals(
            legacyMatcher.elementDecisionsForPage("https://example.org/article"),
            compiledMatcher.elementDecisionsForPage("https://example.org/article"),
        )
    }

    @Test
    fun `single pass compilation handles large mixed subscriptions without parsed spec lists`() {
        val ruleCount = 5_000
        val text =
            buildString {
                repeat(ruleCount) { index ->
                    append("||ads-")
                    append(index)
                    append(".example^")
                    append('\n')
                    append("##.sponsored-")
                    append(index)
                    append('\n')
                }
            }
        val compiled =
            text
                .reader()
                .buffered()
                .use { reader ->
                    compileBrowserAdBlockSubscription(
                        reader = reader,
                        subscriptionId = "large",
                        subscriptionName = "Large list",
                    )
                }

        assertEquals(ruleCount, compiled.counts.networkBlockingRuleCount)
        assertEquals(ruleCount, compiled.counts.elementBlockingRuleCount)
        assertEquals(ruleCount, compiled.ruleSet.networkRules.size)
        assertEquals(ruleCount, compiled.ruleSet.elementRules.size)
    }

    @Test
    fun `resource and third party options constrain request blocking`() {
        val matcher =
            matcher(
                networkRules =
                    listOf(
                        networkRule(
                            id = "third-party-script",
                            rule = "||cdn.example.net^${'$'}script,third-party",
                        ),
                    ),
            )

        assertEquals(
            "third-party-script",
            matcher
                .decide(
                    requestContext(
                        pageUrl = "https://news.example.com/article",
                        requestUrl = "https://cdn.example.net/ad.js",
                        resourceType = BrowserAdBlockResourceType.SCRIPT,
                    ),
                )
                ?.ruleId,
        )
        assertNull(
            matcher.decide(
                requestContext(
                    pageUrl = "https://news.example.com/article",
                    requestUrl = "https://cdn.example.net/banner.png",
                    resourceType = BrowserAdBlockResourceType.IMAGE,
                ),
            ),
        )
        assertNull(
            matcher.decide(
                requestContext(
                    pageUrl = "https://news.example.com/article",
                    requestUrl = "https://cdn.example.com/ad.js",
                    resourceType = BrowserAdBlockResourceType.SCRIPT,
                ),
            ),
        )
    }

    @Test
    fun `document and generic hide exceptions apply to the page`() {
        val documentWhitelisted =
            matcher(
                networkRules =
                    listOf(
                        networkRule("block", "tracking"),
                        networkRule("document", "@@||news.example.org^${'$'}document"),
                    ),
                elementRules = listOf(elementRule("generic", "", ".advert")),
            )

        assertNull(
            documentWhitelisted.decide(
                requestContext(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://cdn.test/tracking.js",
                    resourceType = BrowserAdBlockResourceType.SCRIPT,
                ),
            ),
        )
        assertTrue(
            documentWhitelisted
                .elementDecisionsForPage("https://news.example.org/article")
                .isEmpty(),
        )

        val genericHideWhitelisted =
            matcher(
                networkRules =
                    listOf(
                        networkRule(
                            "generic-hide",
                            "@@||news.example.org^${'$'}generichide",
                        ),
                    ),
                elementRules =
                    listOf(
                        elementRule("generic", "", ".advert"),
                        elementRule("specific", "example.org", ".sponsored"),
                    ),
            )

        assertEquals(
            listOf(".sponsored"),
            genericHideWhitelisted.selectorsForPage("https://news.example.org/article"),
        )
    }

    @Test
    fun `badfilter disables its matching rule and important block overrides regular exception`() {
        val disabled =
            matcher(
                networkRules =
                    listOf(
                        networkRule("block", "||ads.example.org^"),
                        networkRule("disable", "||ads.example.org^${'$'}badfilter"),
                    ),
            )
        assertNull(
            disabled.decide(
                "https://news.example.org",
                "https://ads.example.org/banner.js",
            ),
        )

        val important =
            matcher(
                networkRules =
                    listOf(
                        networkRule("block", "||ads.example.org^${'$'}important"),
                        networkRule("allow", "@@||ads.example.org^"),
                    ),
            )
        assertEquals(
            "block",
            important
                .decide(
                    "https://news.example.org",
                    "https://ads.example.org/banner.js",
                )
                ?.ruleId,
        )
    }

    @Test
    fun `badfilter applies across enabled compiled subscription rule sets`() {
        val targetRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                networkRules =
                    listOf(
                        networkRule(
                            id = "target",
                            rule = "||cdn77.org^*.mp4|",
                            source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                        ),
                    ),
                elementRules = emptyList(),
            )
        val badFilterRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                networkRules =
                    listOf(
                        networkRule(
                            id = "disable",
                            rule = "||cdn77.org^*.mp4|${'$'}badfilter",
                            source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                        ),
                    ),
                elementRules = emptyList(),
            )
        val matcher =
            BrowserAdBlockMatcher.fromCompiled(
                enabled = true,
                allowlistedDomains = emptyList(),
                ruleSets = listOf(targetRuleSet, badFilterRuleSet),
            )

        assertNull(
            matcher.decide(
                pageUrl = "https://video.example.org",
                requestUrl = "https://cdn77.org/assets/ad.mp4",
            ),
        )
    }

    @Test
    fun `subscription badfilter does not disable an explicit custom rule`() {
        val customRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                networkRules = listOf(networkRule("custom", "||ads.example.org^")),
                elementRules = emptyList(),
            )
        val subscriptionRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                networkRules =
                    listOf(
                        networkRule(
                            id = "subscription-disable",
                            rule = "||ads.example.org^${'$'}badfilter",
                            source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                        ),
                    ),
                elementRules = emptyList(),
            )
        val matcher =
            BrowserAdBlockMatcher.fromCompiled(
                enabled = true,
                allowlistedDomains = emptyList(),
                ruleSets = listOf(customRuleSet, subscriptionRuleSet),
            )

        assertEquals(
            "custom",
            matcher
                .decide(
                    pageUrl = "https://news.example.org",
                    requestUrl = "https://ads.example.org/banner.js",
                )
                ?.ruleId,
        )
    }

    @Test
    fun `excluded badfilter is rejected and cannot disable another rule`() {
        val result =
            parseBrowserAdBlockSubscription(
                text =
                    """
                    ||ads.example.org^
                    ||ads.example.org^${'$'}~badfilter
                    """.trimIndent(),
                subscriptionId = "sub",
                subscriptionName = "Invalid badfilter",
            )
        val matcher =
            matcher(
                networkRules = result.networkRules,
            )

        assertEquals(1, result.networkRules.size)
        assertEquals(1, result.ignoredLineCount)
        assertEquals(
            "sub:network:0",
            matcher
                .decide(
                    pageUrl = "https://news.example.org",
                    requestUrl = "https://ads.example.org/banner.js",
                )
                ?.ruleId,
        )
    }

    @Test
    fun `malformed domain expressions are rejected instead of becoming broader rules`() {
        val result =
            parseBrowserAdBlockSubscription(
                text =
                    """
                    tracking${'$'}domain=example.org|bad_domain
                    bad_domain##.advert
                    example.org,##.sponsored
                    """.trimIndent(),
                subscriptionId = "sub",
                subscriptionName = "Malformed domains",
            )

        assertTrue(result.networkRules.isEmpty())
        assertTrue(result.elementRules.isEmpty())
        assertEquals(3, result.ignoredLineCount)
        assertTrue(
            matcher(
                elementRules =
                    listOf(
                        elementRule(
                            id = "invalid-domain",
                            domain = "bad_domain",
                            selector = ".advert",
                        ),
                    ),
            ).selectorsForPage("https://example.org").isEmpty(),
        )
    }

    @Test
    fun `main frame requests are never blocked and request type classification uses headers`() {
        val matcher =
            matcher(networkRules = listOf(networkRule("block", "tracking")))
        assertNull(
            matcher.decide(
                requestContext(
                    pageUrl = "https://example.org",
                    requestUrl = "https://tracking.example",
                    resourceType = BrowserAdBlockResourceType.DOCUMENT,
                    isMainFrame = true,
                ),
            ),
        )
        assertEquals(
            BrowserAdBlockResourceType.SCRIPT,
            classifyBrowserAdBlockResourceType(
                requestUrl = "https://cdn.example/app",
                requestHeaders = mapOf("Sec-Fetch-Dest" to "script"),
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserAdBlockResourceType.XMLHTTPREQUEST,
            classifyBrowserAdBlockResourceType(
                requestUrl = "https://api.example/data",
                requestHeaders = mapOf("X-Requested-With" to "XMLHttpRequest"),
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserAdBlockResourceType.SUBDOCUMENT,
            classifyBrowserAdBlockResourceType(
                requestUrl = "https://frame.example/document",
                requestHeaders = mapOf("Sec-Fetch-Dest" to "document"),
                isMainFrame = false,
            ),
        )
        assertEquals(
            BrowserAdBlockResourceType.OBJECT,
            classifyBrowserAdBlockResourceType(
                requestUrl = "https://media.example/embed",
                requestHeaders = mapOf("Sec-Fetch-Dest" to "embed"),
                isMainFrame = false,
            ),
        )
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
    fun `element decisions preserve rule identity and source`() {
        val decisions =
            matcher(
                elementRules =
                    listOf(
                        BrowserAdBlockElementRuleSpec(
                            id = "custom-element",
                            domainExpression = "example.org",
                            selector = "video.ad",
                            source = BrowserAdBlockRuleSource.CUSTOM,
                            sourceName = "Custom elements",
                        ),
                    ),
            ).elementDecisionsForPage("https://news.example.org/article")

        assertEquals(1, decisions.size)
        assertEquals("custom-element", decisions.single().ruleId)
        assertEquals("video.ad", decisions.single().selector)
        assertEquals("Custom elements", decisions.single().sourceName)
    }

    @Test
    fun `hiker element paths remain readable and participate in page decisions`() {
        val hikerPath =
            "#playlist-scroll&&.playlist-hover-wrap,1&&.playlist-video-card,0"
        val decisions =
            matcher(
                elementRules =
                    listOf(
                        elementRule(
                            id = "hiker-element",
                            domain = "hanime1.me",
                            selector = hikerPath,
                        ),
                    ),
            ).elementDecisionsForPage("https://hanime1.me/watch?v=407186")

        assertTrue(isValidBrowserAdBlockSelector("#shareBtn-title"))
        assertTrue(isValidBrowserAdBlockSelector(hikerPath))
        assertEquals(hikerPath, decisions.single().selector)
    }

    @Test
    fun `suggested rules match representative hiker network log ad urls`() {
        val adUrls =
            listOf(
                "https://poweredby.jads.co/js/jads.js",
                "https://www.googletagmanager.com/gtag/js?id=UA-125786247-2",
                "https://cdn.impactserving.com/Scripts/infinity.js.aspx?guid=75b4ac7f-9a66-41df-8b31-822964ff008b",
                "https://creative.mayzaent.com/static/2cbf80284858ddaccb601f7115a75422/s/widgets/v4/Universal/main.05aec2a05a4001ef960c.js",
                "https://creative.mayzaent.com/static/2cbf80284858ddaccb601f7115a75422/s/widgets/v4/Universal/main.dcaef09e0f767b95990b.css",
            )

        adUrls.forEachIndexed { index, url ->
            val rule = suggestBrowserAdBlockNetworkRule(url)
            assertEquals(
                "network-$index",
                matcher(
                    networkRules = listOf(networkRule("network-$index", rule)),
                ).decide(
                    pageUrl = "https://hanime1.me/",
                    requestUrl = url,
                )?.ruleId,
            )
        }
    }

    @Test
    fun `domain targeting only matches explicit domain scoped rules`() {
        assertTrue(
            isBrowserAdBlockNetworkRuleTargetedAtDomain(
                rule = "||example.org^",
                domain = "news.example.org",
            ),
        )
        assertTrue(
            isBrowserAdBlockNetworkRuleTargetedAtDomain(
                rule = "tracking\$domain=example.org|~shop.example.org",
                domain = "news.example.org",
            ),
        )
        assertTrue(
            isBrowserAdBlockNetworkRuleTargetedAtDomain(
                rule = "|https://ads.example.org/banner.gif|",
                domain = "ads.example.org",
            ),
        )
        assertFalse(
            isBrowserAdBlockNetworkRuleTargetedAtDomain(
                rule = "||ads.example.org^",
                domain = "example.org",
            ),
        )
        assertFalse(
            isBrowserAdBlockNetworkRuleTargetedAtDomain(
                rule = "||ads.example.org^\$domain=other.test",
                domain = "ads.example.org",
            ),
        )
        assertFalse(
            isBrowserAdBlockNetworkRuleTargetedAtDomain(
                rule = "tracking-pixel",
                domain = "example.org",
            ),
        )
    }

    @Test
    fun `domain normalization rejects malformed values`() {
        assertEquals("example.org", normalizeBrowserAdBlockDomainInput("https://Example.org/path"))
        assertEquals("sub.example.org", normalizeBrowserAdBlockDomainInput("sub.example.org:443"))
        assertNull(normalizeBrowserAdBlockDomainInput("-invalid.example.org"))
        assertNull(normalizeBrowserAdBlockDomainInput("not a domain"))
    }

    @Test
    fun `aggregate token index keeps common short prefixes selective`() {
        val ruleSets =
            List(5) { setIndex ->
                BrowserAdBlockCompiledRuleSet.compile(
                    id = "set-$setIndex",
                    networkRules =
                        List(1_000) { ruleIndex ->
                            networkRule(
                                id = "$setIndex-$ruleIndex",
                                rule =
                                    "ads-$setIndex-${ruleIndex.toString().padStart(5, '0')}-payload",
                                source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                            )
                        },
                    elementRules = emptyList(),
                )
            }
        val matcher =
            BrowserAdBlockMatcher.fromCompiled(
                enabled = true,
                allowlistedDomains = emptyList(),
                ruleSets = ruleSets,
            )

        assertEquals(
            0,
            matcher.debugNetworkCandidateCount(
                requestContext(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://cdn.example.org/assets/ads-none.js",
                    resourceType = BrowserAdBlockResourceType.SCRIPT,
                ),
            ),
        )
    }

    @Test
    fun `combined runtime partitions preserve global rule priority and element exceptions`() {
        val customRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                id = "custom",
                networkRules =
                    listOf(
                        networkRule("block", "||ads.example.org^"),
                        networkRule(
                            "important",
                            "||ads.example.org/forced.js${'$'}important",
                        ),
                    ),
                elementRules =
                    listOf(
                        elementRule("custom-element", "example.org", ".shared-ad"),
                    ),
            )
        val subscriptionRuleSet =
            BrowserAdBlockCompiledRuleSet.compile(
                id = "subscription",
                networkRules =
                    listOf(
                        networkRule(
                            id = "exception",
                            rule = "@@||ads.example.org^",
                            source = BrowserAdBlockRuleSource.SUBSCRIPTION,
                        ),
                    ),
                elementRules =
                    listOf(
                        elementRule(
                            id = "subscription-element-exception",
                            domain = "example.org",
                            selector = ".shared-ad",
                            exception = true,
                        ),
                    ),
            )
        val matcher =
            BrowserAdBlockEngine
                .combine(
                    listOf(
                        BrowserAdBlockEngine.compile(listOf(customRuleSet)),
                        BrowserAdBlockEngine.compile(listOf(subscriptionRuleSet)),
                    ),
                ).createMatcher(
                    enabled = true,
                    allowlistedDomains = emptyList(),
                    activeRuleSetIds = listOf("custom", "subscription"),
                )

        assertNull(
            matcher.decide(
                pageUrl = "https://news.example.org/article",
                requestUrl = "https://ads.example.org/banner.js",
            ),
        )
        assertEquals(
            "important",
            matcher
                .decide(
                    pageUrl = "https://news.example.org/article",
                    requestUrl = "https://ads.example.org/forced.js",
                )
                ?.ruleId,
        )
        assertTrue(
            matcher
                .elementDecisionsForPage("https://news.example.org/article")
                .isEmpty(),
        )
    }

    @Test
    fun `element decisions reuse the immutable page cache`() {
        val matcher =
            matcher(
                elementRules =
                    listOf(
                        elementRule("generic", "", ".generic-ad"),
                        elementRule("domain", "example.org", "#domain-ad"),
                    ),
            )

        val first = matcher.elementDecisionsForPage("https://www.example.org/article")
        val second = matcher.elementDecisionsForPage("https://www.example.org/article")

        assertEquals(listOf(".generic-ad", "#domain-ad"), first.map { it.selector })
        assertSame(first, second)
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
        source: BrowserAdBlockRuleSource = BrowserAdBlockRuleSource.CUSTOM,
    ): BrowserAdBlockNetworkRuleSpec =
        BrowserAdBlockNetworkRuleSpec(
            id = id,
            rule = rule,
            source = source,
            sourceName =
                if (source == BrowserAdBlockRuleSource.CUSTOM) {
                    "Custom"
                } else {
                    "Subscription"
                },
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

    private fun requestContext(
        pageUrl: String,
        requestUrl: String,
        resourceType: BrowserAdBlockResourceType,
        isMainFrame: Boolean = false,
    ): BrowserAdBlockRequestContext =
        BrowserAdBlockRequestContext(
            pageUrl = pageUrl,
            requestUrl = requestUrl,
            resourceType = resourceType,
            isMainFrame = isMainFrame,
        )
}
