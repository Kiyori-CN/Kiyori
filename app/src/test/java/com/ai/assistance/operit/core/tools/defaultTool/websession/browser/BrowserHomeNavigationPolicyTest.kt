package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHomeNavigationPolicyTest {
    @Test
    fun `browser home URL comparison normalizes browser-equivalent roots`() {
        assertTrue(
            areBrowserHomeUrlsEquivalent(
                "https://EXAMPLE.com:443",
                "https://example.com/",
            ),
        )
        assertTrue(areBrowserHomeUrlsEquivalent("ABOUT:BLANK", "about:blank"))
        assertFalse(
            areBrowserHomeUrlsEquivalent(
                "https://example.com/?mode=one",
                "https://example.com/?mode=two",
            ),
        )
    }

    @Test
    fun `redirected browser home remains the root after loading completes`() {
        val configuredHome = "https://start.example/home"
        val pending = BrowserHomeNavigationState().begin(configuredHome)

        assertTrue(
            isAtConfiguredBrowserHome(
                currentUrl = "https://previous.example/result",
                configuredHomeUrl = configuredHome,
                navigationState = pending,
            ),
        )

        val completed = pending.complete("https://landing.example/")
        assertTrue(
            isAtConfiguredBrowserHome(
                currentUrl = "https://landing.example/",
                configuredHomeUrl = configuredHome,
                navigationState = completed,
            ),
        )
        assertFalse(
            isAtConfiguredBrowserHome(
                currentUrl = "https://landing.example/article",
                configuredHomeUrl = configuredHome,
                navigationState = completed,
            ),
        )
    }

    @Test
    fun `each browser window keeps an independent home root`() {
        val configuredHome = "https://home.example/"
        val firstWindow =
            BrowserHomeNavigationState()
                .begin(configuredHome)
                .complete("https://resolved-home.example/")
        val secondWindow = BrowserHomeNavigationState()

        assertTrue(
            isAtConfiguredBrowserHome(
                currentUrl = "https://resolved-home.example/",
                configuredHomeUrl = configuredHome,
                navigationState = firstWindow,
            ),
        )
        assertFalse(
            isAtConfiguredBrowserHome(
                currentUrl = "https://search.example/result",
                configuredHomeUrl = configuredHome,
                navigationState = secondWindow,
            ),
        )
    }
}
