package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserClickNavigationPolicyTest {
    @Test
    fun `ordinary http link expects navigation`() {
        assertTrue(
            expectsNavigation(
                target =
                    BrowserClickTargetInfo(
                        href = "https://www.iana.org/help/example-domains",
                        target = "",
                        isDownload = false,
                    ),
            ),
        )
    }

    @Test
    fun `same page fragment and blank target expect navigation`() {
        assertTrue(
            expectsNavigation(
                target =
                    BrowserClickTargetInfo(
                        href = "https://example.com/#details",
                        target = "",
                        isDownload = false,
                    ),
            ),
        )
    }

    @Test
    fun `blank target opens a new tab even when href equals the current page`() {
        assertTrue(
            expectsNavigation(
                target =
                    BrowserClickTargetInfo(
                        href = "https://example.com/",
                        target = "_blank",
                        isDownload = false,
                    ),
            ),
        )
    }

    @Test
    fun `download modified and non-primary clicks do not claim navigation`() {
        val target =
            BrowserClickTargetInfo(
                href = "https://example.com/file.zip",
                target = "",
                isDownload = true,
            )
        assertFalse(expectsNavigation(target = target))
        assertFalse(expectsNavigation(target = target.copy(isDownload = false), button = "middle"))
        assertFalse(
            expectsNavigation(
                target = target.copy(isDownload = false),
                modifiers = setOf("Control"),
            ),
        )
        assertFalse(
            expectsNavigation(
                target = target.copy(isDownload = false),
                doubleClick = true,
            ),
        )
    }

    @Test
    fun `javascript links keep click semantics without a navigation promise`() {
        assertFalse(
            expectsNavigation(
                target =
                    BrowserClickTargetInfo(
                        href = "javascript:void(0)",
                        target = "",
                        isDownload = false,
                    ),
            ),
        )
    }

    private fun expectsNavigation(
        target: BrowserClickTargetInfo?,
        button: String = "left",
        doubleClick: Boolean = false,
        modifiers: Set<String> = emptySet(),
    ): Boolean =
        BrowserClickNavigationPolicy.expectsNavigation(
            target = target,
            initialUrl = "https://example.com/",
            button = button,
            doubleClick = doubleClick,
            modifiers = modifiers,
        )
}
