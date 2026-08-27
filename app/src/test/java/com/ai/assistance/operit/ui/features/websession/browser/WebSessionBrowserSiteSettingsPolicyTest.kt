package com.ai.assistance.operit.ui.features.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionBrowserSiteSettingsPolicyTest {
    @Test
    fun `site settings expose five groups and twelve negative controls`() {
        assertEquals(
            listOf(1, 2, 4, 3, 2),
            webSessionSiteSettingSections.map { section -> section.settings.size },
        )
        assertEquals(
            WebSessionSiteSettingId.entries.toList(),
            webSessionSiteSettingSections.flatMap(WebSessionSiteSettingSection::settings)
                .map(WebSessionSiteSettingSpec::id),
        )
        assertTrue(
            webSessionSiteSettingSections.all { section ->
                section.title.isNotBlank() &&
                    section.description.isNotBlank() &&
                    section.settings.all { setting ->
                        setting.title.isNotBlank() && setting.description.isNotBlank()
                    }
            },
        )
    }

    @Test
    fun `site setting global values come from their unique owners`() {
        val settings =
            WebSessionBrowserSettings(
                forcePageZoomEnabled = false,
                swipeHistoryNavigationEnabled = true,
                websitePasswordSavingEnabled = false,
                automaticFloatingPlaybackEnabled = true,
            )
        val adBlockState = BrowserAdBlockState(enabled = false)

        assertFalse(
            isWebSessionSiteSettingGloballyEnabled(
                id = WebSessionSiteSettingId.AD_BLOCKING,
                browserSettings = settings,
                adBlockState = adBlockState,
                userScriptsAllowed = true,
            ),
        )
        assertFalse(
            isWebSessionSiteSettingGloballyEnabled(
                id = WebSessionSiteSettingId.FORCE_PAGE_ZOOM,
                browserSettings = settings,
                adBlockState = adBlockState,
                userScriptsAllowed = true,
            ),
        )
        assertFalse(
            isWebSessionSiteSettingGloballyEnabled(
                id = WebSessionSiteSettingId.WEBSITE_PASSWORD_SAVING,
                browserSettings = settings,
                adBlockState = adBlockState,
                userScriptsAllowed = true,
            ),
        )
        assertTrue(
            isWebSessionSiteSettingGloballyEnabled(
                id = WebSessionSiteSettingId.SWIPE_HISTORY_NAVIGATION,
                browserSettings = settings,
                adBlockState = adBlockState,
                userScriptsAllowed = false,
            ),
        )
        assertFalse(
            isWebSessionSiteSettingGloballyEnabled(
                id = WebSessionSiteSettingId.USER_SCRIPTS,
                browserSettings = settings,
                adBlockState = adBlockState,
                userScriptsAllowed = false,
            ),
        )
        assertTrue(
            isWebSessionSiteSettingGloballyEnabled(
                id = WebSessionSiteSettingId.AUTOMATIC_FLOATING_PLAYBACK,
                browserSettings = settings,
                adBlockState = adBlockState,
                userScriptsAllowed = false,
            ),
        )
    }
}
