package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionSiteSettingsPolicyTest {
    @Test
    fun `site settings normalize only exact HTTP and HTTPS hosts`() {
        assertEquals(
            "sub.example.com",
            normalizeWebSessionSiteSettingsDomain("https://Sub.Example.com/path?q=1"),
        )
        assertEquals(
            "example.com",
            normalizeWebSessionSiteSettingsDomain("example.com"),
        )
        assertEquals(
            "xn--bcher-kva.example",
            normalizeWebSessionSiteSettingsDomain("https://bücher.example"),
        )
        assertEquals(
            "localhost",
            normalizeWebSessionSiteSettingsDomain("http://localhost:8080/path"),
        )
        assertEquals(
            "127.0.0.1",
            normalizeWebSessionSiteSettingsDomain("https://127.0.0.1/test"),
        )
        assertNull(normalizeWebSessionSiteSettingsDomain("about:blank"))
        assertNull(normalizeWebSessionSiteSettingsDomain("file:///tmp/page.html"))
        assertNull(normalizeWebSessionSiteSettingsDomain("ftp://example.com/file"))
        assertNull(normalizeWebSessionSiteSettingsDomain(""))
    }

    @Test
    fun `global disabled always wins and site rules only further disable`() {
        val settings =
            WebSessionBrowserSettings(
                siteSettingsRules =
                    listOf(
                        WebSessionSiteSettingsRule(
                            domain = "example.com",
                            disabledFeatures = setOf(WebSessionSiteFeature.FORCE_PAGE_ZOOM),
                        ),
                    ),
            )

        assertFalse(
            resolveWebSessionSiteFeatureEnabled(
                settings = settings,
                domainOrUrl = "https://example.com/page",
                feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                globalEnabled = false,
            ),
        )
        assertFalse(
            resolveWebSessionSiteFeatureEnabled(
                settings = settings,
                domainOrUrl = "https://example.com/page",
                feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                globalEnabled = true,
            ),
        )
        assertTrue(
            resolveWebSessionSiteFeatureEnabled(
                settings = settings,
                domainOrUrl = "https://other.example.com/page",
                feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                globalEnabled = true,
            ),
        )
    }

    @Test
    fun `site rules use exact hosts and delete the last disabled feature`() {
        val withZoomDisabled =
            updateWebSessionSiteSettingsRules(
                rules = emptyList(),
                domainOrUrl = "https://sub.example.com/page",
                feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                disabled = true,
            )
        val withTwoFeatures =
            updateWebSessionSiteSettingsRules(
                rules = withZoomDisabled,
                domainOrUrl = "sub.example.com",
                feature = WebSessionSiteFeature.USER_SCRIPTS,
                disabled = true,
            )

        assertEquals(1, withTwoFeatures.size)
        assertEquals(
            setOf(
                WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                WebSessionSiteFeature.USER_SCRIPTS,
            ),
            withTwoFeatures.single().disabledFeatures,
        )
        assertNull(
            WebSessionBrowserSettings(siteSettingsRules = withTwoFeatures)
                .siteSettingsRule("https://example.com"),
        )

        val withOnlyScriptsDisabled =
            updateWebSessionSiteSettingsRules(
                rules = withTwoFeatures,
                domainOrUrl = "sub.example.com",
                feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                disabled = false,
            )
        val empty =
            updateWebSessionSiteSettingsRules(
                rules = withOnlyScriptsDisabled,
                domainOrUrl = "sub.example.com",
                feature = WebSessionSiteFeature.USER_SCRIPTS,
                disabled = false,
            )

        assertEquals(
            setOf(WebSessionSiteFeature.USER_SCRIPTS),
            withOnlyScriptsDisabled.single().disabledFeatures,
        )
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `site rules persist all stable feature ids deterministically`() {
        val rules =
            listOf(
                WebSessionSiteSettingsRule(
                    domain = "b.example.com",
                    disabledFeatures =
                        setOf(
                            WebSessionSiteFeature.AUTOMATIC_FLOATING_PLAYBACK,
                            WebSessionSiteFeature.SWIPE_HISTORY_NAVIGATION,
                            WebSessionSiteFeature.USER_SCRIPTS,
                        ),
                ),
                WebSessionSiteSettingsRule(
                    domain = "a.example.com",
                    disabledFeatures =
                        setOf(WebSessionSiteFeature.WEBSITE_PASSWORD_SAVING),
                ),
            )

        val encoded = encodeWebSessionSiteSettingsRules(rules)
        val decoded = decodeWebSessionSiteSettingsRules(encoded)

        assertEquals(rules.sortedBy(WebSessionSiteSettingsRule::domain), decoded)
        assertTrue(encoded.indexOf("a.example.com") < encoded.indexOf("b.example.com"))
        assertTrue(encoded.contains("\"automatic_floating_playback\""))
        assertTrue(encoded.contains("\"swipe_history_navigation\""))
        assertTrue(encoded.contains("\"user_scripts\""))
        assertTrue(encoded.contains("\"website_password_saving\""))
    }

    @Test
    fun `fresh browser settings have no site rules`() {
        assertTrue(FRESH_INSTALL_BROWSER_SETTINGS.siteSettingsRules.isEmpty())
    }

    @Test
    fun `ad block allowlist reports the most specific covering domain`() {
        assertEquals(
            "sub.example.com",
            resolveBrowserAdBlockAllowlistedDomain(
                host = "sub.example.com",
                domains = listOf("example.com", "sub.example.com"),
            ),
        )
        assertEquals(
            "example.com",
            resolveBrowserAdBlockAllowlistedDomain(
                host = "child.example.com",
                domains = listOf("example.com"),
            ),
        )
        assertNull(
            resolveBrowserAdBlockAllowlistedDomain(
                host = "another.test",
                domains = listOf("example.com"),
            ),
        )
    }
}
