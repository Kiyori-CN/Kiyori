package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDisplaySettingsPolicyTest {
    @Test
    fun `web text zoom accepts only the fixed five percent scale`() {
        assertTrue(isSupportedWebTextZoomPercent(50))
        assertTrue(isSupportedWebTextZoomPercent(100))
        assertTrue(isSupportedWebTextZoomPercent(135))
        assertTrue(isSupportedWebTextZoomPercent(200))
        assertFalse(isSupportedWebTextZoomPercent(45))
        assertFalse(isSupportedWebTextZoomPercent(103))
        assertFalse(isSupportedWebTextZoomPercent(205))
    }

    @Test
    fun `return without reload changes only the selected Back cache mode`() {
        assertEquals(
            WebSettings.LOAD_CACHE_ELSE_NETWORK,
            resolveBrowserBackCacheMode(
                returnWithoutReloadEnabled = true,
                currentCacheMode = WebSettings.LOAD_DEFAULT,
            ),
        )
        assertEquals(
            WebSettings.LOAD_NO_CACHE,
            resolveBrowserBackCacheMode(
                returnWithoutReloadEnabled = false,
                currentCacheMode = WebSettings.LOAD_NO_CACHE,
            ),
        )
    }

    @Test
    fun `production viewport script completes reversible DOM lifecycle`() {
        val fixture = sequenceOf(
            java.io.File("tools/browser/viewport_override.fixture.mjs"),
            java.io.File("../tools/browser/viewport_override.fixture.mjs"),
        ).first { it.isFile }
        val scripts = org.json.JSONObject()
            .put("off", browserViewportOverrideScript(false, false))
            .put("zoom", browserViewportOverrideScript(true, false))
            .put("desktop", browserViewportOverrideScript(false, true))
            .put("both", browserViewportOverrideScript(true, true))
        val process = ProcessBuilder("node", fixture.canonicalPath).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(scripts.toString()) }
        val ended = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
        if (!ended) process.destroyForcibly()
        assertTrue("Viewport DOM lifecycle timed out", ended)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.exitValue())
        assertTrue(output, output.contains("PASS:"))
    }

    @Test
    fun `desktop overview is disabled only while forced page zoom is enabled`() {
        assertFalse(
            shouldUseBrowserOverviewMode(
                usesDesktopLayout = true,
                forcePageZoomEnabled = true,
                viewportWidthCssPx = null,
            ),
        )
        assertTrue(
            shouldUseBrowserOverviewMode(
                usesDesktopLayout = true,
                forcePageZoomEnabled = false,
                viewportWidthCssPx = null,
            ),
        )
        assertFalse(
            shouldUseBrowserOverviewMode(
                usesDesktopLayout = false,
                forcePageZoomEnabled = true,
                viewportWidthCssPx = null,
            ),
        )
        assertFalse(
            shouldUseBrowserOverviewMode(
                usesDesktopLayout = true,
                forcePageZoomEnabled = false,
                viewportWidthCssPx = 900,
            ),
        )
    }

    // D-pc-ua-layout: 会话已经有 AI 显式指定的视口宽度时，PC UA 不应再抢着改写页面 viewport，
    // 与 useWideViewPort 共用的判定必须保持一致，否则两处代码会逐渐分叉出不一致的行为。
    @Test
    fun `desktop viewport override defers to an explicit session viewport width`() {
        assertTrue(
            shouldOverrideDesktopViewportForPage(usesDesktopLayout = true, viewportWidthCssPx = null),
        )
        assertFalse(
            shouldOverrideDesktopViewportForPage(usesDesktopLayout = true, viewportWidthCssPx = 900),
        )
        assertFalse(
            shouldOverrideDesktopViewportForPage(usesDesktopLayout = false, viewportWidthCssPx = null),
        )
    }
}
