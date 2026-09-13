package com.ai.assistance.operit.ui.features.websession.browser

import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserMediaCandidateVideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMediaFormatFilterTest {
    @Test
    fun audioRemovalReturnsToAllEvenWhenVideoFormatsHaveNotChanged() {
        val formats = BrowserMediaCandidateVideoFormat.entries.toList()
        assertEquals("AUDIO", resolveBrowserMediaFormatFilter("AUDIO", formats, true))
        assertEquals("ALL", resolveBrowserMediaFormatFilter("AUDIO", formats, false))
    }

    @Test
    fun clearingCandidatesInvalidatesAudioAndVideoSelections() {
        assertEquals("ALL", resolveBrowserMediaFormatFilter("AUDIO", emptyList(), false))
        BrowserMediaCandidateVideoFormat.entries.forEach {
            assertEquals("ALL", resolveBrowserMediaFormatFilter(it.name, emptyList(), false))
        }
    }

    @Test
    fun availableVideoSelectionAndAllRemainStable() {
        BrowserMediaCandidateVideoFormat.entries.forEach {
            assertEquals(it.name, resolveBrowserMediaFormatFilter(it.name, listOf(it), true))
        }
        assertEquals("ALL", resolveBrowserMediaFormatFilter("ALL", emptyList(), false))
        assertEquals("ALL", resolveBrowserMediaFormatFilter("removed", emptyList(), true))
    }
}
