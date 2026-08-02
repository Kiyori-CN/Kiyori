package com.kiyori.app.startup

import android.net.Uri
import com.ai.assistance.operit.ui.common.NavItem
import com.kiyori.app.shell.KiyoriShellExternalDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class KiyoriMainPendingRequestsTest {
    @Test
    fun `browser and shell requests clear only after matching request id`() {
        val requests = KiyoriMainPendingRequests()

        requests.recordBrowser("https://example.com", requestId = 11L)
        requests.recordShellDestination(
            KiyoriShellExternalDestination.DOWNLOADS,
            requestId = 12L,
        )

        requests.consumeBrowser(handledRequestId = 10L)
        requests.consumeShell(handledRequestId = 13L)

        assertEquals("https://example.com", requests.browserUrl)
        assertEquals(11L, requests.browserRequestId)
        assertEquals(
            KiyoriShellExternalDestination.DOWNLOADS,
            requests.shellDestination,
        )
        assertEquals(12L, requests.shellRequestId)

        requests.consumeBrowser(handledRequestId = 11L)
        requests.consumeShell(handledRequestId = 12L)

        assertNull(requests.browserUrl)
        assertEquals(0L, requests.browserRequestId)
        assertNull(requests.shellDestination)
        assertEquals(0L, requests.shellRequestId)
    }

    @Test
    fun `shortcut and route requests preserve current navigation and route arguments`() {
        val requests = KiyoriMainPendingRequests()
        val routeArgs = mapOf<String, Any?>("path" to "/tmp/file", "line" to 7)

        requests.recordShortcut(NavItem.Settings, requestId = 21L)
        requests.recordRoute("editor", routeArgs, requestId = 22L)

        assertEquals(NavItem.Settings, requests.shortcutNavItem)
        assertEquals(NavItem.Settings, requests.currentMainNavItem)
        assertEquals(21L, requests.shortcutRequestId)
        assertEquals("editor", requests.routeId)
        assertSame(routeArgs, requests.routeArgs)
        assertEquals(22L, requests.routeRequestId)

        requests.consumeShortcut(handledRequestId = 20L)
        requests.consumeRoute(handledRequestId = 23L)
        assertEquals(NavItem.Settings, requests.shortcutNavItem)
        assertEquals("editor", requests.routeId)

        requests.consumeShortcut(handledRequestId = 21L)
        requests.consumeRoute(handledRequestId = 22L)

        assertNull(requests.shortcutNavItem)
        assertEquals(0L, requests.shortcutRequestId)
        assertEquals(NavItem.Settings, requests.currentMainNavItem)
        assertNull(requests.routeId)
        assertEquals(emptyMap<String, Any?>(), requests.routeArgs)
        assertEquals(0L, requests.routeRequestId)

        requests.updateCurrentMainNavItem(NavItem.Terminal)
        assertEquals(NavItem.Terminal, requests.currentMainNavItem)
    }

    @Test
    fun `shared content and OAuth state keep existing independent clear semantics`() {
        val requests = KiyoriMainPendingRequests()
        val firstUri = mock<Uri>()
        val secondUri = mock<Uri>()
        val authUri = mock<Uri>()

        requests.recordSharedFiles(listOf(firstUri, secondUri))
        requests.recordSharedText("shared text")
        requests.recordGitHubAuth(authUri)

        assertEquals(listOf(firstUri, secondUri), requests.sharedFileUris)
        assertEquals("shared text", requests.sharedText)
        assertSame(authUri, requests.takeGitHubAuthUri())
        assertNull(requests.gitHubAuthUri)

        requests.clearSharedFiles()
        assertNull(requests.sharedFileUris)
        assertEquals("shared text", requests.sharedText)

        requests.recordSharedFiles(listOf(firstUri))
        requests.clearSharedFilesAndText()
        assertNull(requests.sharedFileUris)
        assertNull(requests.sharedText)

        requests.recordSharedText("another text")
        requests.clearSharedText()
        assertNull(requests.sharedText)
    }
}
