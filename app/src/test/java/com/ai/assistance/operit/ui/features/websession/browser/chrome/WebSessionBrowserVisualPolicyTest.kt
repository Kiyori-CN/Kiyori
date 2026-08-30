package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.ui.Alignment
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.ui.features.websession.browser.WEB_SESSION_DRAWER_HEADER_END_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.WEB_SESSION_DRAWER_HEADER_HEIGHT_DP
import com.ai.assistance.operit.ui.features.websession.browser.WEB_SESSION_DRAWER_HEADER_ICON_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.WEB_SESSION_DRAWER_HEADER_START_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.WEB_SESSION_DRAWER_HEADER_TITLE_GAP_DP
import com.ai.assistance.operit.ui.features.websession.browser.WEB_SESSION_DRAWER_TITLE_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionDrawerTitleActionContentAlignment
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionDrawerTitleActionShape
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSessionBrowserVisualPolicyTest {
    @Test
    fun `normal and incognito window pages use distinct semantic tones`() {
        assertEquals(
            KiyoriSemanticTone.BLUE,
            resolveWebSessionBrowserProfileTone(WebSessionProfile.NORMAL),
        )
        assertEquals(
            KiyoriSemanticTone.PURPLE,
            resolveWebSessionBrowserProfileTone(WebSessionProfile.INCOGNITO),
        )
    }

    @Test
    fun `browser child drawers share one header geometry`() {
        assertEquals(52, WEB_SESSION_DRAWER_HEADER_HEIGHT_DP)
        assertEquals(18, WEB_SESSION_DRAWER_HEADER_START_PADDING_DP)
        assertEquals(8, WEB_SESSION_DRAWER_HEADER_END_PADDING_DP)
        assertEquals(34, WEB_SESSION_DRAWER_HEADER_ICON_SIZE_DP)
        assertEquals(8, WEB_SESSION_DRAWER_HEADER_TITLE_GAP_DP)
    }

    @Test
    fun `drawer title action uses shared control feedback geometry`() {
        assertEquals(40, WEB_SESSION_DRAWER_TITLE_ACTION_SIZE_DP)
        assertEquals(KiyoriUiShapes.control, WebSessionDrawerTitleActionShape)
        assertEquals(Alignment.Center, WebSessionDrawerTitleActionContentAlignment)
    }
}
