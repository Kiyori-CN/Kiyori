package com.ai.assistance.operit.ui.main.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.unit.LayoutDirection
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryCategory
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteRuntime
import com.ai.assistance.operit.ui.main.navigation.RouteSpec
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.matchesNavigationRoot
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.app.shell.KiyoriBrowserReturnTarget
import com.kiyori.app.shell.KiyoriShellBackResult
import com.kiyori.app.shell.KiyoriShellBackTransition
import com.kiyori.app.shell.KiyoriShellChild
import com.kiyori.app.shell.KiyoriShellExternalDestination
import com.kiyori.app.shell.KiyoriShellState
import com.kiyori.app.shell.PrimaryDestination
import com.kiyori.app.shell.SoftwareHomePage
import com.kiyori.app.shell.calculateKiyoriAiHostTranslation
import com.kiyori.app.shell.calculateKiyoriAiDrawerWidthDp
import com.kiyori.app.shell.calculateKiyoriPagerPageOffset
import com.kiyori.app.shell.kiyoriStartupBeyondViewportPageCount
import com.kiyori.app.shell.openExternalChild
import com.kiyori.app.shell.openExternalDestination
import com.kiyori.app.shell.resolveKiyoriAiDrawerTone
import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedFinalScale
import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedSpringDampingRatio
import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedStartScale
import com.kiyori.app.shell.resolveKiyoriBottomBarAlpha
import com.kiyori.app.shell.restoreKiyoriShellState
import com.kiyori.app.shell.shouldComposeKiyoriAiHost
import com.kiyori.app.shell.shouldEnableKiyoriShellBackHandler
import com.kiyori.app.shell.shouldNotifyKiyoriAiHomeSettledForInitialPage
import com.kiyori.app.shell.shouldPresentKiyoriBookmarkDrawer
import com.kiyori.app.shell.shouldPresentKiyoriDownloadDrawer
import com.kiyori.app.shell.shouldPresentKiyoriHistoryDrawer
import com.kiyori.app.shell.shouldProvideKiyoriSettingsTheme
import com.kiyori.app.shell.shouldReverseKiyoriPagerDrag
import com.kiyori.app.shell.toKiyoriShellSaveableValues
import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation
import com.kiyori.integration.operit.navigation.AiDrawerSelectionEffect
import com.kiyori.integration.operit.navigation.AiTopBarMode
import com.kiyori.integration.operit.navigation.buildAiPrimaryStack
import com.kiyori.integration.operit.navigation.hasSameAiSettingsSourceFamily
import com.kiyori.integration.operit.navigation.preservesAiPrimaryStack
import com.kiyori.integration.operit.navigation.resolveAiDrawerSelection
import com.kiyori.integration.operit.navigation.resolveAiTopBarMode
import com.kiyori.integration.operit.navigation.toAiPrimaryRouteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriShellStateTest {
    @Test
    fun `launch state is software home center with bottom navigation`() {
        val state = KiyoriShellState()

        assertEquals(PrimaryDestination.SOFTWARE_HOME, state.primaryDestination)
        assertEquals(SoftwareHomePage.HOME, state.softwareHomePage)
        assertTrue(state.showsBottomBar)
    }

    @Test
    fun `saveable Shell owner preserves shared drawers and every navigation field`() {
        val states =
            listOf(
                KiyoriShellState(
                    softwareHomePage = SoftwareHomePage.MINUS_ONE,
                ).openDownloadDrawer(),
                KiyoriShellState(
                    softwareHomePage = SoftwareHomePage.MINUS_ONE,
                ).openBookmarkDrawer(),
                KiyoriShellState(
                    softwareHomePage = SoftwareHomePage.MINUS_ONE,
                ).openHistoryDrawer(),
                KiyoriShellState(
                    primaryDestination = PrimaryDestination.SETTINGS_HOME,
                    softwareHomePage = SoftwareHomePage.AI_HOME,
                    child = KiyoriShellChild.DOWNLOAD_SETTINGS,
                    childBackTarget = KiyoriShellChild.BROWSER_SETTINGS,
                    isAiDrawerOpen = true,
                    browserReturnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                    browserExitPresentation =
                        KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
                ),
                KiyoriShellState(
                    primaryDestination = PrimaryDestination.BROWSER_HOME,
                    browserReturnTarget = KiyoriBrowserReturnTarget.SETTINGS_HOME,
                ),
            )

        states.forEach { state ->
            assertEquals(
                state,
                restoreKiyoriShellState(state.toKiyoriShellSaveableValues()),
            )
        }
    }

    @Test
    fun `minus one and AI home hide bottom navigation`() {
        assertFalse(
            KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE).showsBottomBar,
        )
        assertFalse(
            KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME).showsBottomBar,
        )
    }

    @Test
    fun `bottom navigation follows the pager while leaving minus one or AI home`() {
        assertEquals(
            0.18f,
            resolveKiyoriBottomBarAlpha(
                state = KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE),
                aiHostIsRoot = true,
                centerPageOffset = 0.82f,
            ),
            0.0001f,
        )
        assertEquals(
            0.36f,
            resolveKiyoriBottomBarAlpha(
                state = KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME),
                aiHostIsRoot = true,
                centerPageOffset = -0.64f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveKiyoriBottomBarAlpha(
                state = KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME),
                aiHostIsRoot = false,
                centerPageOffset = -0.2f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `non browser root destinations show bottom navigation`() {
        PrimaryDestination.entries
            .filterNot { destination ->
                destination == PrimaryDestination.SOFTWARE_HOME ||
                    destination == PrimaryDestination.BROWSER_HOME
            }
            .forEach { destination ->
                assertTrue(
                    KiyoriShellState(primaryDestination = destination).showsBottomBar,
                )
            }
    }

    @Test
    fun `browser home owns immersive chrome and hides bottom navigation`() {
        assertFalse(
            KiyoriShellState(
                primaryDestination = PrimaryDestination.BROWSER_HOME,
            ).showsBottomBar,
        )
    }

    @Test
    fun `browser home remembers AI source and exits back to AI home`() {
        val browserState =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )

        assertEquals(PrimaryDestination.BROWSER_HOME, browserState.primaryDestination)
        assertEquals(KiyoriBrowserReturnTarget.AI_HOME, browserState.browserReturnTarget)
        assertEquals(
            KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            browserState.browserExitPresentation,
        )
        assertEquals(
            KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME),
            browserState.exitBrowser(),
        )
    }

    @Test
    fun `browser home opened from software home exits to center home`() {
        val browserState = KiyoriShellState().openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME)

        assertEquals(
            KiyoriBrowserExitPresentation.CLOSE,
            browserState.browserExitPresentation,
        )
        assertEquals(
            KiyoriShellState(),
            browserState.exitBrowser(),
        )
    }

    @Test
    fun `browser home restores every top-level entry page`() {
        val cases =
            listOf(
                Triple(
                    KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE),
                    KiyoriBrowserReturnTarget.MINUS_ONE_PAGE,
                    KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE),
                ),
                Triple(
                    KiyoriShellState(),
                    KiyoriBrowserReturnTarget.SOFTWARE_HOME,
                    KiyoriShellState(),
                ),
                Triple(
                    KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME),
                    KiyoriBrowserReturnTarget.AI_HOME,
                    KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME),
                ),
                Triple(
                    KiyoriShellState(primaryDestination = PrimaryDestination.MINI_APP_HOME),
                    KiyoriBrowserReturnTarget.MINI_APP_HOME,
                    KiyoriShellState(primaryDestination = PrimaryDestination.MINI_APP_HOME),
                ),
                Triple(
                    KiyoriShellState(primaryDestination = PrimaryDestination.FILE_MANAGEMENT_HOME),
                    KiyoriBrowserReturnTarget.FILE_MANAGEMENT_HOME,
                    KiyoriShellState(primaryDestination = PrimaryDestination.FILE_MANAGEMENT_HOME),
                ),
                Triple(
                    KiyoriShellState(primaryDestination = PrimaryDestination.SETTINGS_HOME),
                    KiyoriBrowserReturnTarget.SETTINGS_HOME,
                    KiyoriShellState(primaryDestination = PrimaryDestination.SETTINGS_HOME),
                ),
            )

        cases.forEach { (source, expectedReturnTarget, expectedRestoredState) ->
            val browserState =
                source.openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME)

            assertEquals(PrimaryDestination.BROWSER_HOME, browserState.primaryDestination)
            assertEquals(expectedReturnTarget, browserState.browserReturnTarget)
            assertEquals(expectedRestoredState, browserState.exitBrowser())
        }
    }

    @Test
    fun `browser home requires a recorded app entry page`() {
        val invalidBrowserState =
            KiyoriShellState(primaryDestination = PrimaryDestination.BROWSER_HOME)

        assertThrows(IllegalStateException::class.java) {
            invalidBrowserState.exitBrowser()
        }
        assertThrows(IllegalStateException::class.java) {
            invalidBrowserState.openExternalDestination(
                KiyoriShellExternalDestination.BROWSER_HOME,
            )
        }
    }

    @Test
    fun `external browser action preserves AI home as the return target`() {
        val browserState =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME)

        assertEquals(PrimaryDestination.BROWSER_HOME, browserState.primaryDestination)
        assertEquals(KiyoriBrowserReturnTarget.AI_HOME, browserState.browserReturnTarget)
        assertEquals(
            KiyoriBrowserExitPresentation.CLOSE,
            browserState.browserExitPresentation,
        )
        assertEquals(
            KiyoriShellState(softwareHomePage = SoftwareHomePage.AI_HOME),
            browserState.exitBrowser(),
        )
    }

    @Test
    fun `indicator restore returns to the current owner and minimizes again`() {
        val browserState =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openExternalDestination(
                KiyoriShellExternalDestination.BROWSER_HOME_FROM_MINIMIZED_INDICATOR,
            )

        assertEquals(KiyoriBrowserReturnTarget.AI_HOME, browserState.browserReturnTarget)
        assertEquals(
            KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            browserState.browserExitPresentation,
        )
    }

    @Test
    fun `external downloads action opens the shared drawer over the current owner`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.BROWSER_HOME,
                browserReturnTarget = KiyoriBrowserReturnTarget.AI_HOME,
            ).openExternalDestination(KiyoriShellExternalDestination.DOWNLOADS)

        assertEquals(PrimaryDestination.BROWSER_HOME, state.primaryDestination)
        assertEquals(KiyoriBrowserReturnTarget.AI_HOME, state.browserReturnTarget)
        assertTrue(state.isDownloadDrawerOpen)
        assertFalse(state.isBookmarkDrawerOpen)
        assertFalse(state.isAiDrawerOpen)
    }

    @Test
    fun `repeated external browser action retains the existing browser return target`() {
        val browserState =
            KiyoriShellState()
                .openBrowser(
                    returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                    exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
                )
                .openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME)

        assertEquals(KiyoriBrowserReturnTarget.AI_HOME, browserState.browserReturnTarget)
        assertEquals(
            KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            browserState.browserExitPresentation,
        )
    }

    @Test
    fun `child destination hides bottom navigation and Back restores owner`() {
        val state =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
                child = KiyoriShellChild.FULL_SCREEN_WEB_SEARCH,
            )

        assertFalse(state.showsBottomBar)
        assertEquals(
            KiyoriShellBackTransition(
                state = state.copy(child = null),
                result = KiyoriShellBackResult.CONSUMED,
            ),
            state.handleBack(),
        )
    }

    @Test
    fun `settings child roots provide theme across page overlays`() {
        assertFalse(shouldProvideKiyoriSettingsTheme(null))
        assertFalse(
            shouldProvideKiyoriSettingsTheme(KiyoriShellChild.FULL_SCREEN_WEB_SEARCH),
        )
        assertFalse(
            shouldProvideKiyoriSettingsTheme(KiyoriShellChild.SETTINGS_HOME),
        )
        listOf(
            KiyoriShellChild.BROWSER_SETTINGS,
            KiyoriShellChild.DOWNLOAD_SETTINGS,
            KiyoriShellChild.PLAYER_SETTINGS,
            KiyoriShellChild.AD_BLOCKER_SETTINGS,
        ).forEach { child ->
            assertTrue(shouldProvideKiyoriSettingsTheme(child))
        }
    }

    @Test
    fun `closing browser settings reveals the same browser owner and exit contract`() {
        val browserState =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        val settingsState = browserState.openChild(KiyoriShellChild.BROWSER_SETTINGS)

        assertEquals(PrimaryDestination.BROWSER_HOME, settingsState.primaryDestination)
        assertEquals(KiyoriShellChild.BROWSER_SETTINGS, settingsState.child)
        assertEquals(browserState, settingsState.closeChild())
    }

    @Test
    fun `source preserving settings home hides bottom navigation and restores browser owner`() {
        val browserState =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        val settingsHome = browserState.openChild(KiyoriShellChild.SETTINGS_HOME)

        assertEquals(PrimaryDestination.BROWSER_HOME, settingsHome.primaryDestination)
        assertEquals(KiyoriShellChild.SETTINGS_HOME, settingsHome.child)
        assertFalse(settingsHome.showsBottomBar)
        assertEquals(browserState, settingsHome.closeChild())
    }

    @Test
    fun `settings details return through source preserving settings home`() {
        val aiOwner =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            )
        val settingsHome = aiOwner.openChild(KiyoriShellChild.SETTINGS_HOME)
        val browserSettings =
            settingsHome.openNestedChild(KiyoriShellChild.BROWSER_SETTINGS)

        assertEquals(KiyoriShellChild.BROWSER_SETTINGS, browserSettings.child)
        assertEquals(KiyoriShellChild.SETTINGS_HOME, browserSettings.childBackTarget)
        assertEquals(settingsHome, browserSettings.closeChild())
        assertEquals(aiOwner, browserSettings.closeChild().closeChild())
    }

    @Test
    fun `ad blocker settings detail returns through settings home`() {
        val owner =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.HOME,
            )
        val settingsHome = owner.openChild(KiyoriShellChild.SETTINGS_HOME)
        val adBlockSettings =
            settingsHome.openNestedChild(KiyoriShellChild.AD_BLOCKER_SETTINGS)

        assertEquals(KiyoriShellChild.AD_BLOCKER_SETTINGS, adBlockSettings.child)
        assertEquals(KiyoriShellChild.SETTINGS_HOME, adBlockSettings.childBackTarget)
        assertEquals(settingsHome, adBlockSettings.closeChild())
        assertEquals(owner, adBlockSettings.closeChild().closeChild())
    }

    @Test
    fun `external browser settings request uses settings home owner`() {
        assertEquals(
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SETTINGS_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
                child = KiyoriShellChild.BROWSER_SETTINGS,
            ),
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openExternalChild(KiyoriShellChild.BROWSER_SETTINGS),
        )
    }

    @Test
    fun `shared download drawer hides bottom navigation and Back restores its owner`() {
        val owner =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.MINUS_ONE,
            )
        val drawer = owner.openDownloadDrawer()

        assertTrue(drawer.isDownloadDrawerOpen)
        assertFalse(drawer.showsBottomBar)
        assertEquals(
            KiyoriShellBackTransition(
                state = owner,
                result = KiyoriShellBackResult.CONSUMED,
            ),
            drawer.handleBack(),
        )
    }

    @Test
    fun `shared bookmark drawer is mutually exclusive and Back restores minus one`() {
        val owner = KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE)
        val bookmarkDrawer = owner.openDownloadDrawer().openBookmarkDrawer()

        assertTrue(bookmarkDrawer.isBookmarkDrawerOpen)
        assertFalse(bookmarkDrawer.isDownloadDrawerOpen)
        assertFalse(bookmarkDrawer.showsBottomBar)
        assertEquals(
            KiyoriShellBackTransition(
                state = owner,
                result = KiyoriShellBackResult.CONSUMED,
            ),
            bookmarkDrawer.handleBack(),
        )
    }

    @Test
    fun `shared history drawer is mutually exclusive and Back restores minus one`() {
        val owner = KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE)
        val historyDrawer = owner.openBookmarkDrawer().openHistoryDrawer()

        assertTrue(historyDrawer.isHistoryDrawerOpen)
        assertFalse(historyDrawer.isBookmarkDrawerOpen)
        assertFalse(historyDrawer.isDownloadDrawerOpen)
        assertFalse(historyDrawer.showsBottomBar)
        assertEquals(
            KiyoriShellBackTransition(
                state = owner,
                result = KiyoriShellBackResult.CONSUMED,
            ),
            historyDrawer.handleBack(),
        )
    }

    @Test
    fun `history web routes own their Shell transition while accepted media closes the drawer`() {
        listOf(
            WebSessionHistoryCategory.WEB,
            WebSessionHistoryCategory.NOVEL,
            WebSessionHistoryCategory.OTHER,
        ).forEach { category ->
            assertFalse(
                shouldDismissKiyoriHistoryDrawerAfterEntryOpen(
                    category = category,
                    accepted = true,
                ),
            )
        }
        listOf(
            WebSessionHistoryCategory.VIDEO,
            WebSessionHistoryCategory.MUSIC,
        ).forEach { category ->
            assertTrue(
                shouldDismissKiyoriHistoryDrawerAfterEntryOpen(
                    category = category,
                    accepted = true,
                ),
            )
            assertFalse(
                shouldDismissKiyoriHistoryDrawerAfterEntryOpen(
                    category = category,
                    accepted = false,
                ),
            )
        }
    }

    @Test
    fun `hidden download drawer host is absent after its exit animation`() {
        assertFalse(
            shouldComposeKiyoriDownloadDrawer(
                isVisible = false,
                keepMountedUntilHidden = false,
            ),
        )
        assertTrue(
            shouldComposeKiyoriDownloadDrawer(
                isVisible = true,
                keepMountedUntilHidden = false,
            ),
        )
        assertFalse(
            shouldComposeKiyoriBookmarkDrawer(
                isVisible = false,
                keepMountedUntilHidden = false,
            ),
        )
        assertTrue(
            shouldComposeKiyoriBookmarkDrawer(
                isVisible = true,
                keepMountedUntilHidden = false,
            ),
        )
        assertFalse(
            shouldComposeKiyoriHistoryDrawer(
                isVisible = false,
                keepMountedUntilHidden = false,
            ),
        )
        assertTrue(
            shouldComposeKiyoriHistoryDrawer(
                isVisible = true,
                keepMountedUntilHidden = false,
            ),
        )
        assertTrue(
            shouldComposeKiyoriDownloadDrawer(
                isVisible = false,
                keepMountedUntilHidden = true,
            ),
        )
    }

    @Test
    fun `shared download drawer presentation is independent of retained AI route depth`() {
        assertTrue(
            shouldPresentKiyoriDownloadDrawer(
                isDownloadDrawerOpen = true,
                aiHostIsRoot = true,
            ),
        )
        assertTrue(
            shouldPresentKiyoriDownloadDrawer(
                isDownloadDrawerOpen = true,
                aiHostIsRoot = false,
            ),
        )
        assertFalse(
            shouldPresentKiyoriDownloadDrawer(
                isDownloadDrawerOpen = false,
                aiHostIsRoot = true,
            ),
        )
        assertTrue(
            shouldEnableKiyoriShellBackHandler(
                aiHostIsRoot = false,
                isAiDrawerOpen = false,
                isBookmarkDrawerOpen = false,
                isHistoryDrawerOpen = false,
                isDownloadDrawerOpen = true,
            ),
        )
        assertTrue(
            shouldPresentKiyoriBookmarkDrawer(
                isBookmarkDrawerOpen = true,
                aiHostIsRoot = false,
            ),
        )
        assertTrue(
            shouldPresentKiyoriHistoryDrawer(
                isHistoryDrawerOpen = true,
                aiHostIsRoot = false,
            ),
        )
    }

    @Test
    fun `settings home downloader entry opens settings directly`() {
        val owner =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SETTINGS_HOME,
            )
        val settings = owner.openChild(KiyoriShellChild.DOWNLOAD_SETTINGS)

        assertEquals(KiyoriShellChild.DOWNLOAD_SETTINGS, settings.child)
        assertEquals(null, settings.childBackTarget)
        assertEquals(owner, settings.closeChild())
    }

    @Test
    fun `external download child requests use settings home owner`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openExternalChild(KiyoriShellChild.DOWNLOAD_SETTINGS)

        assertEquals(PrimaryDestination.SETTINGS_HOME, state.primaryDestination)
        assertEquals(SoftwareHomePage.AI_HOME, state.softwareHomePage)
        assertEquals(KiyoriShellChild.DOWNLOAD_SETTINGS, state.child)
        assertEquals(null, state.childBackTarget)
    }

    @Test
    fun `Back closes AI drawer before child and page navigation`() {
        val state =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
                child = KiyoriShellChild.FULL_SCREEN_WEB_SEARCH,
                isAiDrawerOpen = true,
            )

        assertEquals(
            KiyoriShellBackTransition(
                state = state.closeAiDrawer(),
                result = KiyoriShellBackResult.CONSUMED,
            ),
            state.handleBack(),
        )
    }

    @Test
    fun `AI drawer width follows window classes and separating fold`() {
        assertEquals(300f, calculateKiyoriAiDrawerWidthDp(400f), 0f)
        assertEquals(320f, calculateKiyoriAiDrawerWidthDp(600f), 0f)
        assertEquals(320f, calculateKiyoriAiDrawerWidthDp(839f), 0f)
        assertEquals(360f, calculateKiyoriAiDrawerWidthDp(840f), 0f)
        assertEquals(
            280f,
            calculateKiyoriAiDrawerWidthDp(
                windowWidthDp = 700f,
                separatingFoldLeftDp = 280f,
            ),
            0f,
        )
    }

    @Test
    fun `bottom navigation selected icons grow from the previous fill size to original visual size`() {
        val expectedScales =
            mapOf(
                PrimaryDestination.SOFTWARE_HOME to (1f to 1.1f),
                PrimaryDestination.BROWSER_HOME to (1f to 1.1f),
                PrimaryDestination.MINI_APP_HOME to (1f to 1.12f),
                PrimaryDestination.FILE_MANAGEMENT_HOME to (1f to 1.1f),
                PrimaryDestination.SETTINGS_HOME to (0.9f to 1f),
            )

        expectedScales.forEach { (destination, scales) ->
            assertEquals(
                scales.first,
                resolveKiyoriBottomNavigationSelectedStartScale(destination),
            )
            assertEquals(
                scales.second,
                resolveKiyoriBottomNavigationSelectedFinalScale(destination),
            )
        }
    }

    @Test
    fun `bottom navigation enlarges the spring peak except for settings`() {
        val enlargedPeakDestinations =
            listOf(
                PrimaryDestination.SOFTWARE_HOME,
                PrimaryDestination.BROWSER_HOME,
                PrimaryDestination.MINI_APP_HOME,
                PrimaryDestination.FILE_MANAGEMENT_HOME,
            )

        enlargedPeakDestinations.forEach { destination ->
            assertEquals(
                0.42f,
                resolveKiyoriBottomNavigationSelectedSpringDampingRatio(destination),
            )
        }
        assertEquals(
            0.55f,
            resolveKiyoriBottomNavigationSelectedSpringDampingRatio(
                PrimaryDestination.SETTINGS_HOME,
            ),
        )
    }

    @Test
    fun `AI drawer uses stable semantic tones for every built in destination`() {
        val expected =
            mapOf(
                "main.ai_chat" to KiyoriSemanticTone.BLUE,
                "main.assistant_config" to KiyoriSemanticTone.PINK,
                "main.memory_base" to KiyoriSemanticTone.GREEN,
                "main.packages" to KiyoriSemanticTone.PURPLE,
                "main.shizuku_commands" to KiyoriSemanticTone.RED,
                "main.workflow" to KiyoriSemanticTone.ORANGE,
                "main.settings" to KiyoriSemanticTone.BLUE,
                "main.toolbox" to KiyoriSemanticTone.CYAN,
            )

        expected.forEach { (entryId, tone) ->
            assertEquals(tone, resolveKiyoriAiDrawerTone(testAiDrawerEntry(entryId)))
        }
    }

    @Test
    fun `plugin drawer tone is deterministic and stays in the semantic vocabulary`() {
        val entry = testAiDrawerEntry("toolpkg:demo:dashboard", NavigationSurface.MAIN_SIDEBAR_PLUGINS)
        val tone = resolveKiyoriAiDrawerTone(entry)

        assertEquals(tone, resolveKiyoriAiDrawerTone(entry.copy()))
        assertTrue(tone in KiyoriSemanticTone.entries)
    }

    @Test
    fun `AI drawer replaces another primary route`() {
        assertEquals(
            AiDrawerSelectionEffect.REPLACE_PRIMARY,
            resolveAiDrawerSelection(
                currentEntryId = "main.packages",
                targetEntryId = "main.workflow",
            ),
        )
    }

    @Test
    fun `restoring an AI primary stack keeps its child route`() {
        val oldRoot = RouteEntry(instanceId = "old", routeId = "native.packages")
        val targetRoot = RouteEntry(instanceId = "target", routeId = "native.workflow")
        val targetChild = RouteEntry(instanceId = "child", routeId = "native.workflow_detail")
        val routerState = AppRouterState(oldRoot)

        routerState.restoreStack(listOf(targetRoot, targetChild))

        assertEquals(listOf(targetRoot, targetChild), routerState.backStack)
        assertEquals(targetChild, routerState.currentEntry)
    }

    @Test
    fun `host navigation root matches route id without comparing internal args`() {
        val entry =
            NavigationEntrySpec(
                entryId = "main.ai_chat",
                routeId = "native.ai_chat",
                surface = NavigationSurface.MAIN_SIDEBAR_AI,
                title = "AI",
                icon = Icons.Default.Home,
            )

        assertTrue(
            entry.matchesNavigationRoot(
                routeId = "native.ai_chat",
                routeArgs = mapOf("_native_screen" to "AiChat"),
            ),
        )
        assertFalse(entry.matchesNavigationRoot("native.settings", emptyMap()))
    }

    @Test
    fun `plugin navigation root requires its registered args`() {
        val entry =
            NavigationEntrySpec(
                entryId = "toolpkg:demo:dashboard",
                routeId = "toolpkg.demo.dashboard",
                surface = NavigationSurface.MAIN_SIDEBAR_PLUGINS,
                title = "Dashboard",
                icon = Icons.Default.Home,
                routeArgs = mapOf("section" to "root"),
                kind = NavigationEntryKind.PLUGIN,
            )

        assertTrue(
            entry.matchesNavigationRoot(
                routeId = "toolpkg.demo.dashboard",
                routeArgs = mapOf("section" to "root"),
            ),
        )
        assertFalse(entry.matchesNavigationRoot("toolpkg.demo.dashboard", emptyMap()))
    }

    @Test
    fun `AI top bar mode follows explicit current route identity`() {
        val root =
            RouteEntry(
                routeId = "native.ai_chat",
                source = RouteEntrySource.SCRIPT,
                navigationRootEntryId = "main.ai_chat",
            )

        assertEquals(AiTopBarMode.DRAWER, resolveAiTopBarMode(root))
        assertEquals(
            AiTopBarMode.BACK,
            resolveAiTopBarMode(root.copy(source = RouteEntrySource.KIYORI_SETTINGS)),
        )
        assertEquals(
            AiTopBarMode.BACK,
            resolveAiTopBarMode(RouteEntry(routeId = "native.ai_detail")),
        )
    }

    @Test
    fun `AI settings source families separate Kiyori Settings from AI entries`() {
        assertTrue(
            hasSameAiSettingsSourceFamily(
                RouteEntrySource.DEFAULT,
                RouteEntrySource.AI_DRAWER,
            ),
        )
        assertTrue(
            hasSameAiSettingsSourceFamily(
                RouteEntrySource.SCRIPT,
                RouteEntrySource.AI_DRAWER,
            ),
        )
        assertFalse(
            hasSameAiSettingsSourceFamily(
                RouteEntrySource.AI_DRAWER,
                RouteEntrySource.KIYORI_SETTINGS,
            ),
        )
    }

    @Test
    fun `AI settings opened from Kiyori Settings returns to Settings Home`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
                isAiDrawerOpen = true,
            )

        assertEquals(
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SETTINGS_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ),
            state.returnFromKiyoriAiSettings(),
        )
    }

    @Test
    fun `AI primary stack restoration follows explicit restore decision`() {
        val targetRoot =
            RouteEntry(
                instanceId = "settings-root",
                routeId = "native.settings",
                source = RouteEntrySource.AI_DRAWER,
                navigationRootEntryId = "main.settings",
            )
        val savedStack =
            listOf(
                targetRoot,
                RouteEntry(instanceId = "child", routeId = "native.settings.detail"),
            )

        assertEquals(
            listOf(targetRoot),
            buildAiPrimaryStack(
                targetRoot = targetRoot,
                savedStack = savedStack,
                restoreChildren = false,
            ),
        )
        assertEquals(
            listOf(targetRoot, savedStack.last()),
            buildAiPrimaryStack(
                targetRoot = targetRoot,
                savedStack = savedStack,
                restoreChildren = true,
            ),
        )
    }

    @Test
    fun `host AI primary roots keep a fixed instance and always preserve their stack`() {
        val entry = testNavigationEntry(kind = NavigationEntryKind.HOST)
        val routeSpec = testRouteSpec(keepAlive = false)

        assertEquals(
            entry.toAiPrimaryRouteEntry(RouteEntrySource.DEFAULT).instanceId,
            entry.toAiPrimaryRouteEntry(RouteEntrySource.AI_DRAWER).instanceId,
        )
        assertTrue(entry.preservesAiPrimaryStack(routeSpec))
    }

    @Test
    fun `plugin AI primary roots preserve their stack only when keepAlive is declared`() {
        val entry = testNavigationEntry(kind = NavigationEntryKind.PLUGIN)

        assertFalse(entry.preservesAiPrimaryStack(testRouteSpec(keepAlive = false)))
        assertTrue(entry.preservesAiPrimaryStack(testRouteSpec(keepAlive = true)))
    }

    @Test
    fun `non keepAlive plugin creates a new root and does not restore its old child stack`() {
        val entry = testNavigationEntry(kind = NavigationEntryKind.PLUGIN)
        val oldRoot = entry.toAiPrimaryRouteEntry(RouteEntrySource.AI_DRAWER)
        val newRoot = entry.toAiPrimaryRouteEntry(RouteEntrySource.AI_DRAWER)
        val oldChild = RouteEntry(instanceId = "old-child", routeId = "plugin.child")

        assertNotEquals(oldRoot.instanceId, newRoot.instanceId)
        assertEquals(
            listOf(newRoot),
            buildAiPrimaryStack(
                targetRoot = newRoot,
                savedStack = listOf(oldRoot, oldChild),
                restoreChildren = entry.preservesAiPrimaryStack(testRouteSpec(keepAlive = false)),
            ),
        )
    }

    @Test
    fun `selecting current AI drawer route only closes drawer`() {
        assertEquals(
            AiDrawerSelectionEffect.CLOSE_ONLY,
            resolveAiDrawerSelection(
                currentEntryId = "main.workflow",
                targetEntryId = "main.workflow",
            ),
        )
    }

    private fun testNavigationEntry(kind: NavigationEntryKind): NavigationEntrySpec =
        NavigationEntrySpec(
            entryId = "toolpkg:demo:dashboard",
            routeId = "toolpkg.demo.dashboard",
            surface = NavigationSurface.MAIN_SIDEBAR_PLUGINS,
            title = "Dashboard",
            icon = Icons.Default.Home,
            kind = kind,
        )

    private fun testAiDrawerEntry(
        entryId: String,
        surface: NavigationSurface = NavigationSurface.MAIN_SIDEBAR_AI,
    ): NavigationEntrySpec =
        NavigationEntrySpec(
            entryId = entryId,
            routeId = "test.$entryId",
            surface = surface,
            title = entryId,
            icon = Icons.Default.Home,
        )

    private fun testRouteSpec(keepAlive: Boolean): RouteSpec =
        RouteSpec(
            routeId = "toolpkg.demo.dashboard",
            runtime = RouteRuntime.TOOLPKG_COMPOSE_DSL,
            keepAlive = keepAlive,
        )

    @Test
    fun `Back from side home page returns to software home center`() {
        listOf(SoftwareHomePage.MINUS_ONE, SoftwareHomePage.AI_HOME).forEach { page ->
            val transition = KiyoriShellState(softwareHomePage = page).handleBack()

            assertEquals(KiyoriShellBackResult.CONSUMED, transition.result)
            assertEquals(SoftwareHomePage.HOME, transition.state.softwareHomePage)
        }
    }

    @Test
    fun `Back from another root returns to software home center`() {
        val transition =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.FILE_MANAGEMENT_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).handleBack()

        assertEquals(KiyoriShellBackResult.CONSUMED, transition.result)
        assertEquals(PrimaryDestination.SOFTWARE_HOME, transition.state.primaryDestination)
        assertEquals(SoftwareHomePage.HOME, transition.state.softwareHomePage)
    }

    @Test
    fun `Back from software home center requests exit`() {
        val state = KiyoriShellState()

        assertEquals(
            KiyoriShellBackTransition(
                state = state,
                result = KiyoriShellBackResult.REQUEST_EXIT,
            ),
            state.handleBack(),
        )
    }

    @Test
    fun `selecting software home always returns its center page`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.BROWSER_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            )

        assertEquals(
            KiyoriShellState(),
            state.selectPrimary(PrimaryDestination.SOFTWARE_HOME),
        )
    }

    @Test
    fun `AI host translation follows the pager without transforms`() {
        assertEquals(1080f, calculateKiyoriAiHostTranslation(-1f, 1080f), 0f)
        assertEquals(0f, calculateKiyoriAiHostTranslation(0f, 1080f), 0f)
        assertEquals(-540f, calculateKiyoriAiHostTranslation(0.5f, 1080f), 0f)
    }

    @Test
    fun `AI host drag uses the same direction convention as horizontal pager`() {
        assertTrue(shouldReverseKiyoriPagerDrag(LayoutDirection.Ltr))
        assertFalse(shouldReverseKiyoriPagerDrag(LayoutDirection.Rtl))
    }

    @Test
    fun `startup first frame composes only the visible home page`() {
        assertEquals(0, kiyoriStartupBeyondViewportPageCount(startupPreloadReady = false))
        assertFalse(
            shouldComposeKiyoriAiHost(
                aiHostIsRoot = true,
                softwareHomePage = SoftwareHomePage.HOME,
                startupPreloadReady = false,
            )
        )
    }

    @Test
    fun `startup preloading restores adjacent pages and AI host after first frame`() {
        assertEquals(1, kiyoriStartupBeyondViewportPageCount(startupPreloadReady = true))
        assertTrue(
            shouldComposeKiyoriAiHost(
                aiHostIsRoot = true,
                softwareHomePage = SoftwareHomePage.HOME,
                startupPreloadReady = true,
            )
        )
    }

    @Test
    fun `AI destinations never wait for software home startup preloading`() {
        assertTrue(
            shouldComposeKiyoriAiHost(
                aiHostIsRoot = false,
                softwareHomePage = SoftwareHomePage.HOME,
                startupPreloadReady = false,
            )
        )
        assertTrue(
            shouldComposeKiyoriAiHost(
                aiHostIsRoot = true,
                softwareHomePage = SoftwareHomePage.AI_HOME,
                startupPreloadReady = false,
            )
        )
    }

    @Test
    fun `AI home readiness only fires for an already settled requested AI page`() {
        assertTrue(
            shouldNotifyKiyoriAiHomeSettledForInitialPage(
                initialSettledPage = SoftwareHomePage.AI_HOME,
                requestedPage = SoftwareHomePage.AI_HOME,
            ),
        )
        assertFalse(
            shouldNotifyKiyoriAiHomeSettledForInitialPage(
                initialSettledPage = SoftwareHomePage.HOME,
                requestedPage = SoftwareHomePage.AI_HOME,
            ),
        )
        assertFalse(
            shouldNotifyKiyoriAiHomeSettledForInitialPage(
                initialSettledPage = SoftwareHomePage.AI_HOME,
                requestedPage = SoftwareHomePage.HOME,
            ),
        )
    }
}
