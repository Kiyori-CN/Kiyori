package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import kotlinx.coroutines.runBlocking
import androidx.datastore.core.CorruptionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionRecoveryPolicyTest {
    private val search =
        BrowserSessionSearchRecovery(
            query = "Kiyori browser",
            engineId = "google",
            source = KiyoriBrowserSearchSource.SOFTWARE_HOME,
            requestedUrl = "https://www.google.com/search?q=Kiyori",
            resolvedResultUrl = "https://www.google.com/search?q=Kiyori",
            submittedAt = 30L,
        )

    private val snapshot =
        BrowserSessionRecoverySnapshot(
            snapshotId = "snapshot-1",
            capturedAt = 40L,
            activeWindowId = "window-2",
            windows =
                listOf(
                    BrowserSessionRecoveryWindow(
                        windowId = "window-1",
                        currentUrl = "https://example.com",
                        title = "Example",
                        createdAt = 1L,
                        lastActivatedAt = 10L,
                        creationReason = BrowserWindowCreationReason.MANUAL_NEW_WINDOW,
                    ),
                    BrowserSessionRecoveryWindow(
                        windowId = "window-2",
                        currentUrl = "https://www.google.com/search?q=Kiyori",
                        title = "Search",
                        createdAt = 2L,
                        lastActivatedAt = 40L,
                        creationReason = BrowserWindowCreationReason.SOFTWARE_HOME_SEARCH,
                        lastSearch = search,
                    ),
                ),
        )

    @Test
    fun `all disabled opens home and disables persistence`() {
        val settings = BrowserRecoverySettings()

        assertFalse(shouldPersistBrowserRecovery(settings))
        assertEquals(
            BrowserLaunchRestorationPlan.OpenConfiguredHome,
            resolveBrowserLaunchRestorationPlan(settings, snapshot),
        )
    }

    @Test
    fun `search restore follows automatic and confirmation modes`() {
        val automatic =
            resolveBrowserLaunchRestorationPlan(
                settings =
                    BrowserRecoverySettings(
                        restoreLastSearchResultEnabled = true,
                    ),
                snapshot = snapshot,
            ) as BrowserLaunchRestorationPlan.RestoreSearchResult
        assertEquals(search, automatic.search)
        assertFalse(automatic.requiresConfirmation)

        val confirmed =
            resolveBrowserLaunchRestorationPlan(
                settings =
                    BrowserRecoverySettings(
                        restoreLastSearchResultEnabled = true,
                        askBeforeRestoringPagesEnabled = true,
                    ),
                snapshot = snapshot,
            ) as BrowserLaunchRestorationPlan.RestoreSearchResult
        assertTrue(confirmed.requiresConfirmation)
    }

    @Test
    fun `retain multiple windows outranks search and follows ask switch`() {
        val plan =
            resolveBrowserLaunchRestorationPlan(
                settings =
                    BrowserRecoverySettings(
                        restoreLastSearchResultEnabled = true,
                        retainMultipleWindowsEnabled = true,
                    ),
                snapshot = snapshot,
            ) as BrowserLaunchRestorationPlan.RestoreAllWindows

        assertEquals(snapshot, plan.snapshot)
        assertFalse(plan.requiresConfirmation)

        val confirmed =
            resolveBrowserLaunchRestorationPlan(
                settings =
                    BrowserRecoverySettings(
                        askBeforeRestoringPagesEnabled = true,
                        retainMultipleWindowsEnabled = true,
                    ),
                snapshot = snapshot,
            ) as BrowserLaunchRestorationPlan.RestoreAllWindows
        assertTrue(confirmed.requiresConfirmation)
    }

    @Test
    fun `ask-only restores the active page after confirmation`() {
        val plan =
            resolveBrowserLaunchRestorationPlan(
                settings =
                    BrowserRecoverySettings(
                        askBeforeRestoringPagesEnabled = true,
                    ),
                snapshot = snapshot,
            ) as BrowserLaunchRestorationPlan.RestoreActivePage

        assertEquals("window-2", plan.window.windowId)
    }

    @Test
    fun `snapshot validation rejects duplicate windows invalid openers and cycles`() {
        requireBrowserSessionRecoverySnapshot(snapshot)

        assertThrows(IllegalArgumentException::class.java) {
            requireBrowserSessionRecoverySnapshot(
                snapshot.copy(
                    windows = snapshot.windows + snapshot.windows.first(),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireBrowserSessionRecoverySnapshot(
                snapshot.copy(
                    windows =
                        snapshot.windows.map { window ->
                            if (window.windowId == "window-2") {
                                window.copy(
                                    creationReason =
                                        BrowserWindowCreationReason
                                            .HOME_CROSS_SITE_USER_NAVIGATION,
                                    openerHomeWindowId = "missing",
                                )
                            } else {
                                window
                            }
                        },
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireBrowserSessionRecoverySnapshot(
                snapshot.copy(
                    windows =
                        listOf(
                            snapshot.windows[0].copy(
                                creationReason =
                                    BrowserWindowCreationReason
                                        .HOME_CROSS_SITE_USER_NAVIGATION,
                                openerHomeWindowId = "window-2",
                            ),
                            snapshot.windows[1].copy(
                                creationReason =
                                    BrowserWindowCreationReason
                                        .HOME_CROSS_SITE_USER_NAVIGATION,
                                openerHomeWindowId = "window-1",
                            ),
                        ),
                ),
            )
        }
    }

    @Test
    fun `snapshot projection excludes incognito and narrows to the selected recovery scope`() {
        val incognito =
            BrowserSessionRecoveryCandidate(
                profile = WebSessionProfile.INCOGNITO,
                window =
                    snapshot.windows.first().copy(
                        windowId = "incognito-window",
                        currentUrl = "https://private.example",
                    ),
            )
        val candidates =
            snapshot.windows.map { window ->
                BrowserSessionRecoveryCandidate(
                    profile = WebSessionProfile.NORMAL,
                    window = window,
                )
            } + incognito

        val searchProjection =
            buildBrowserSessionRecoverySnapshot(
                settings =
                    BrowserRecoverySettings(
                        restoreLastSearchResultEnabled = true,
                    ),
                snapshotId = "search-projection",
                capturedAt = 50L,
                activeWindowId = "window-1",
                candidates = candidates,
            )
        assertEquals(listOf("window-2"), searchProjection?.windows?.map { it.windowId })

        val askProjection =
            buildBrowserSessionRecoverySnapshot(
                settings = BrowserRecoverySettings(askBeforeRestoringPagesEnabled = true),
                snapshotId = "ask-projection",
                capturedAt = 50L,
                activeWindowId = "window-1",
                candidates = candidates,
            )
        assertEquals(listOf("window-1"), askProjection?.windows?.map { it.windowId })

        val retainedProjection =
            buildBrowserSessionRecoverySnapshot(
                settings = BrowserRecoverySettings(retainMultipleWindowsEnabled = true),
                snapshotId = "retained-projection",
                capturedAt = 50L,
                activeWindowId = "window-2",
                candidates = candidates,
            )
        assertEquals(
            listOf("window-1", "window-2"),
            retainedProjection?.windows?.map { it.windowId },
        )
    }

    @Test
    fun `snapshot projection drops opener relations outside the retained scope`() {
        val child =
            BrowserSessionRecoveryWindow(
                windowId = "window-child",
                currentUrl = "https://child.example",
                title = "Child",
                createdAt = 3L,
                lastActivatedAt = 50L,
                creationReason = BrowserWindowCreationReason.HOME_CROSS_SITE_USER_NAVIGATION,
                openerHomeWindowId = "window-1",
                lastSearch =
                    search.copy(
                        submittedAt = 50L,
                        requestedUrl = "https://search.example/?q=child",
                        resolvedResultUrl = "https://search.example/?q=child",
                    ),
            )
        val projection =
            buildBrowserSessionRecoverySnapshot(
                settings =
                    BrowserRecoverySettings(
                        restoreLastSearchResultEnabled = true,
                    ),
                snapshotId = "child-projection",
                capturedAt = 60L,
                activeWindowId = "window-child",
                candidates =
                    listOf(
                        BrowserSessionRecoveryCandidate(
                            profile = WebSessionProfile.NORMAL,
                            window = snapshot.windows.first(),
                        ),
                        BrowserSessionRecoveryCandidate(
                            profile = WebSessionProfile.NORMAL,
                            window = child,
                        ),
                    ),
            )

        assertEquals(listOf("window-child"), projection?.windows?.map { it.windowId })
        assertEquals(null, projection?.windows?.single()?.openerHomeWindowId)
    }

    @Test
    fun `recovery serializer round trips valid envelopes and rejects malformed data`() {
        val bytes = ByteArrayOutputStream()
        runBlocking {
            BrowserSessionRecoverySerializer.writeTo(
                BrowserSessionRecoveryEnvelope(snapshot = snapshot),
                bytes,
            )
        }
        val decoded =
            runBlocking {
                BrowserSessionRecoverySerializer.readFrom(
                    ByteArrayInputStream(bytes.toByteArray()),
                )
            }
        assertEquals(snapshot, decoded.snapshot)
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                BrowserSessionRecoverySerializer.readFrom(
                    ByteArrayInputStream("{".encodeToByteArray()),
                )
            }
        }
    }
}
