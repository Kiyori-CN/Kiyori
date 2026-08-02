package com.kiyori.app.startup

import com.ai.assistance.operit.ui.common.NavItem
import com.kiyori.app.shell.KiyoriShellExternalDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class KiyoriMainContentHostTest {
    @Test
    fun `shortcut takes precedence and every content request is projected exactly`() {
        val pendingRequests = KiyoriMainPendingRequests()
        val routeArgs = mapOf<String, Any?>("path" to "/tmp/file", "line" to 7, "extra" to null)

        pendingRequests.updateCurrentMainNavItem(NavItem.Terminal)
        pendingRequests.recordShortcut(NavItem.Settings, requestId = 11L)
        pendingRequests.recordRoute("editor", routeArgs, requestId = 12L)
        pendingRequests.recordBrowser("https://example.com", requestId = 13L)
        pendingRequests.recordShellDestination(
            KiyoriShellExternalDestination.DOWNLOADS,
            requestId = 14L,
        )

        val projection: KiyoriMainContentRequestProjection =
            projectKiyoriMainContentRequests(pendingRequests)

        assertEquals(NavItem.Settings, projection.initialNavItem)
        assertEquals(NavItem.Settings, projection.shortcutNavRequest)
        assertEquals(11L, projection.shortcutNavRequestId)
        assertEquals("editor", projection.routeNavRequest)
        assertSame(routeArgs, projection.routeNavArgs)
        assertEquals(12L, projection.routeNavRequestId)
        assertEquals("https://example.com", projection.browserOpenRequest)
        assertEquals(13L, projection.browserOpenRequestId)
        assertEquals(
            KiyoriShellExternalDestination.DOWNLOADS,
            projection.kiyoriShellDestinationRequest,
        )
        assertEquals(14L, projection.kiyoriShellRequestId)
    }

    @Test
    fun `current navigation is used when no shortcut request exists`() {
        val pendingRequests = KiyoriMainPendingRequests()
        pendingRequests.updateCurrentMainNavItem(NavItem.Terminal)

        val projection = projectKiyoriMainContentRequests(pendingRequests)

        assertEquals(NavItem.Terminal, projection.initialNavItem)
        assertNull(projection.shortcutNavRequest)
        assertEquals(0L, projection.shortcutNavRequestId)
    }

    @Test
    fun `projection does not consume or rewrite pending requests`() {
        val pendingRequests = KiyoriMainPendingRequests()
        val routeArgs = mapOf<String, Any?>("mode" to "preview")

        pendingRequests.recordShortcut(NavItem.Settings, requestId = 21L)
        pendingRequests.recordRoute("workspace", routeArgs, requestId = 22L)
        pendingRequests.recordBrowser("https://example.org", requestId = 23L)

        projectKiyoriMainContentRequests(pendingRequests)

        assertEquals(NavItem.Settings, pendingRequests.shortcutNavItem)
        assertEquals(21L, pendingRequests.shortcutRequestId)
        assertEquals("workspace", pendingRequests.routeId)
        assertSame(routeArgs, pendingRequests.routeArgs)
        assertEquals(22L, pendingRequests.routeRequestId)
        assertEquals("https://example.org", pendingRequests.browserUrl)
        assertEquals(23L, pendingRequests.browserRequestId)
    }
}
