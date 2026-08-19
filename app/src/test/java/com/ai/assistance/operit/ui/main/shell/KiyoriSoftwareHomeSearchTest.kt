package com.ai.assistance.operit.ui.main.shell

import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import com.kiyori.app.shell.KIYORI_HOME_ATTACHMENT_ICON_ALPHA
import com.kiyori.app.shell.KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP
import com.kiyori.app.shell.KIYORI_HOME_BRAND_ICON_SIZE_DP
import com.kiyori.app.shell.KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP
import com.kiyori.app.shell.KIYORI_HOME_COMPACT_MAX_WIDTH_DP
import com.kiyori.app.shell.KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP
import com.kiyori.app.shell.KIYORI_HOME_EXPANDED_MAX_WIDTH_DP
import com.kiyori.app.shell.KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP
import com.kiyori.app.shell.KIYORI_HOME_MEDIUM_MAX_WIDTH_DP
import com.kiyori.app.shell.KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP
import com.kiyori.app.shell.KIYORI_HOME_MODE_SEGMENT_WIDTH_DP
import com.kiyori.app.shell.KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP
import com.kiyori.app.shell.KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS
import com.kiyori.app.shell.KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP
import com.kiyori.app.shell.KIYORI_HOME_SELECTED_DARK_COLOR
import com.kiyori.app.shell.KIYORI_HOME_SELECTED_LIGHT_COLOR
import com.kiyori.app.shell.KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP
import com.kiyori.app.shell.KIYORI_HOME_TOOL_ICON_SIZE_DP
import com.kiyori.app.shell.KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION
import com.kiyori.app.shell.KiyoriSoftwareHomeHeightLayout
import com.kiyori.app.shell.KiyoriSoftwareHomeLayout
import com.kiyori.app.shell.KiyoriSoftwareHomeMode
import com.kiyori.app.shell.KiyoriSoftwareHomePrimaryTarget
import com.kiyori.app.shell.KiyoriWebSearchRequest
import com.kiyori.app.shell.resolveKiyoriSoftwareHomeHeightLayout
import com.kiyori.app.shell.resolveKiyoriSoftwareHomeLayout
import com.kiyori.app.shell.resolveKiyoriSoftwareHomePrimaryTarget
import com.kiyori.app.shell.resolveKiyoriSoftwareHomeSearchTopY
import com.kiyori.app.shell.resolveKiyoriWebSearchRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSoftwareHomeSearchTest {
    @Test
    fun `home visual dimensions keep the compact refinement contract`() {
        assertEquals(120, KIYORI_HOME_MODE_SEGMENT_WIDTH_DP)
        assertEquals(32, KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP)
        assertEquals(18, KIYORI_HOME_BRAND_ICON_SIZE_DP)
        assertEquals(114, KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP)
        assertEquals(96, KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP)
        assertEquals(24, KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP)
        assertEquals(48, KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP)
        assertEquals(72, KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP)
        assertEquals(544, KIYORI_HOME_COMPACT_MAX_WIDTH_DP)
        assertEquals(584, KIYORI_HOME_MEDIUM_MAX_WIDTH_DP)
        assertEquals(624, KIYORI_HOME_EXPANDED_MAX_WIDTH_DP)
        assertEquals(1, KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP)
        assertEquals(20, KIYORI_HOME_TOOL_ICON_SIZE_DP)
        assertEquals(24, KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP)
        assertEquals(0.9f, KIYORI_HOME_ATTACHMENT_ICON_ALPHA, 0f)
        assertEquals(
            listOf(
                Color(0xFF54C878),
                Color(0xFF45B9D4),
                Color(0xFF8277DA),
                Color(0xFFF09A6C),
                Color(0xFF54C878),
            ),
            KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS,
        )
        assertEquals(Color(0xFF2F6FED), KIYORI_HOME_SELECTED_LIGHT_COLOR)
        assertEquals(Color(0xFF79A7FF), KIYORI_HOME_SELECTED_DARK_COLOR)
    }

    @Test
    fun `home layout changes at tablet and expanded width gates`() {
        assertEquals(
            KiyoriSoftwareHomeLayout.COMPACT,
            resolveKiyoriSoftwareHomeLayout(599f),
        )
        assertEquals(
            KiyoriSoftwareHomeLayout.MEDIUM,
            resolveKiyoriSoftwareHomeLayout(600f),
        )
        assertEquals(
            KiyoriSoftwareHomeLayout.MEDIUM,
            resolveKiyoriSoftwareHomeLayout(839f),
        )
        assertEquals(
            KiyoriSoftwareHomeLayout.EXPANDED,
            resolveKiyoriSoftwareHomeLayout(840f),
        )
    }

    @Test
    fun `short viewport layout changes below 440 dp`() {
        assertEquals(
            KiyoriSoftwareHomeHeightLayout.SHORT,
            resolveKiyoriSoftwareHomeHeightLayout(439f),
        )
        assertEquals(
            KiyoriSoftwareHomeHeightLayout.REGULAR,
            resolveKiyoriSoftwareHomeHeightLayout(440f),
        )
    }

    @Test
    fun `regular home places the search top border on the golden ratio point`() {
        assertEquals(0.382f, KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION, 0f)
        assertEquals(
            229.2f,
            resolveKiyoriSoftwareHomeSearchTopY(
                availableHeight = 600f,
                titleHeight = 44f,
                titleSpacing = 16f,
            ),
            0.001f,
        )
    }

    @Test
    fun `short home keeps the title visible when the golden point is too high`() {
        assertEquals(
            52f,
            resolveKiyoriSoftwareHomeSearchTopY(
                availableHeight = 120f,
                titleHeight = 44f,
                titleSpacing = 8f,
            ),
            0f,
        )
    }

    @Test
    fun `home mode selects the matching primary destination`() {
        assertEquals(
            KiyoriSoftwareHomePrimaryTarget.WEB_SEARCH,
            resolveKiyoriSoftwareHomePrimaryTarget(KiyoriSoftwareHomeMode.SEARCH),
        )
        assertEquals(
            KiyoriSoftwareHomePrimaryTarget.AI_HOME,
            resolveKiyoriSoftwareHomePrimaryTarget(KiyoriSoftwareHomeMode.AI),
        )
    }

    @Test
    fun `blank home search does not create a browser request`() {
        assertNull(
            resolveKiyoriWebSearchRequest(
                "   ",
                WebSessionSearchEngine.BING,
                WebSessionProfile.NORMAL,
                KiyoriBrowserSearchSource.SOFTWARE_HOME,
            )
        )
    }

    @Test
    fun `home search trims the input and resolves a host for a new session`() {
        assertEquals(
            KiyoriWebSearchRequest(
                query = "example.com/docs",
                targetUrl = "https://example.com/docs",
                profile = WebSessionProfile.INCOGNITO,
                engineId = WebSessionSearchEngine.BING.id,
                source = KiyoriBrowserSearchSource.SOFTWARE_HOME,
            ),
            resolveKiyoriWebSearchRequest(
                rawQuery = "  example.com/docs  ",
                searchEngine = WebSessionSearchEngine.BING,
                profile = WebSessionProfile.INCOGNITO,
                source = KiyoriBrowserSearchSource.SOFTWARE_HOME,
            ),
        )
    }

    @Test
    fun `every search host declares system Back ownership explicitly`() {
        val fullScreenSearchSource =
            repositoryFile(
                "app/src/main/java/com/kiyori/app/shell/KiyoriBrowserSearch.kt",
            ).readText()
        val browserScreenSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/" +
                    "browser/WebSessionBrowserScreen.kt",
            ).readText()
        val searchScreenSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/" +
                    "browser/WebSessionBrowserTopBar.kt",
            ).readText()
        val ownershipSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/" +
                    "browser/WebSessionBrowserBackOwnership.kt",
            ).readText()

        assertTrue(fullScreenSearchSource.contains("systemBackEnabled = true"))
        assertTrue(
            browserScreenSource.contains(
                "systemBackEnabled = LocalWebSessionBrowserSystemBackEnabled.current",
            ),
        )
        assertTrue(searchScreenSource.contains("systemBackEnabled: Boolean"))
        assertTrue(searchScreenSource.contains("enabled = systemBackEnabled"))
        assertFalse(
            searchScreenSource.contains(
                "enabled = LocalWebSessionBrowserSystemBackEnabled.current",
            ),
        )
        assertTrue(ownershipSource.contains("staticCompositionLocalOf<Boolean>"))
        assertTrue(ownershipSource.contains("error("))
        assertFalse(
            Regex("""staticCompositionLocalOf<Boolean>\s*\{\s*true\s*\}""")
                .containsMatchIn(ownershipSource),
        )
    }

    @Test
    fun `every shared bookmark and history host declares system Back ownership explicitly`() {
        val browserScreenSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/" +
                    "browser/WebSessionBrowserScreen.kt",
            ).readText()
        val historyHostSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/" +
                    "KiyoriHistoryDrawerHost.kt",
            ).readText()
        val bookmarkHostSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/" +
                    "KiyoriBookmarkDrawerHost.kt",
            ).readText()
        val historySheetSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/" +
                    "browser/WebSessionHistorySheet.kt",
            ).readText()
        val bookmarkSheetSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/" +
                    "browser/WebSessionBookmarkSheet.kt",
            ).readText()
        val browserOwnerArgument =
            "systemBackEnabled = LocalWebSessionBrowserSystemBackEnabled.current"

        assertEquals(
            3,
            Regex(Regex.escape(browserOwnerArgument)).findAll(browserScreenSource).count(),
        )
        assertTrue(historyHostSource.contains("systemBackEnabled = isVisible"))
        assertTrue(bookmarkHostSource.contains("systemBackEnabled = isVisible"))
        assertTrue(historySheetSource.contains("systemBackEnabled: Boolean"))
        assertTrue(bookmarkSheetSource.contains("systemBackEnabled: Boolean"))
        assertTrue(historySheetSource.contains("enabled = systemBackEnabled && batchMode"))
        assertTrue(bookmarkSheetSource.contains("systemBackEnabled &&"))
        assertFalse(
            historySheetSource.contains("LocalWebSessionBrowserSystemBackEnabled.current"),
        )
        assertFalse(
            bookmarkSheetSource.contains("LocalWebSessionBrowserSystemBackEnabled.current"),
        )
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
