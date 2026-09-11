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
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.app.shell.KiyoriBrowserReturnTarget
import com.kiyori.app.shell.KiyoriShellBackResult
import com.kiyori.app.shell.KiyoriShellBackTransition
import com.kiyori.app.shell.KiyoriShellChild
import com.kiyori.app.shell.KiyoriShellExternalDestination
import com.kiyori.app.shell.KiyoriShellState
import com.kiyori.app.shell.KiyoriSettingsNavigationState
import com.kiyori.app.shell.KiyoriSettingsOrigin
import com.kiyori.app.shell.KiyoriSettingsPresentation
import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import com.kiyori.app.shell.PrimaryDestination
import com.kiyori.app.shell.SoftwareHomePage
import com.kiyori.app.shell.calculateKiyoriAiHostTranslation
import com.kiyori.app.shell.calculateKiyoriAiDrawerWidthDp
import com.kiyori.app.shell.calculateKiyoriPagerPageOffset
import com.kiyori.app.shell.countKiyoriAiDrawerToolboxEntries
import com.kiyori.app.shell.kiyoriStartupBeyondViewportPageCount
import com.kiyori.app.shell.isKiyoriBottomNavigationSettingsHome
import com.kiyori.app.shell.isKiyoriSettingsBackOwnedByShell
import com.kiyori.app.shell.openExternalDestination
import com.kiyori.app.shell.resolveKiyoriAiDrawerTone
import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedFinalScale
import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedSpringDampingRatio
import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedStartScale
import com.kiyori.app.shell.resolveKiyoriBottomBarAlpha
import com.kiyori.app.shell.restoreKiyoriShellState
import com.kiyori.app.shell.shouldAcceptKiyoriHomePagerInput
import com.kiyori.app.shell.shouldAnimateKiyoriShellChildOverlay
import com.kiyori.app.shell.shouldComposeKiyoriAiHost
import com.kiyori.app.shell.shouldElevateKiyoriAiHost
import com.kiyori.app.shell.shouldEnableKiyoriBrowserHostBackHandler
import com.kiyori.app.shell.shouldEnableKiyoriAiHostSystemBack
import com.kiyori.app.shell.shouldEnableKiyoriShellBackHandler
import com.kiyori.app.shell.shouldEnableKiyoriSettingsHostBackHandler
import com.kiyori.app.shell.shouldNotifyKiyoriAiHomeSettledForInitialPage
import com.kiyori.app.shell.shouldPresentKiyoriBookmarkDrawer
import com.kiyori.app.shell.shouldPresentKiyoriDownloadDrawer
import com.kiyori.app.shell.shouldPresentKiyoriHistoryDrawer
import com.kiyori.app.shell.shouldPresentKiyoriPluginLoading
import com.kiyori.app.shell.shouldPresentKiyoriSettingsOverlay
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
import com.kiyori.app.KiyoriSettingsNavigationContext
import com.kiyori.app.popKiyoriRouterBackStack
import com.kiyori.app.resolveKiyoriSettingsNavigationContext
import com.kiyori.app.shouldEnableKiyoriAppBackHandler
import com.kiyori.app.shouldEnableKiyoriOperitSettingsBackHandler
import com.kiyori.app.shouldRestoreKiyoriSettingsAfterRouterPop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriShellStateTest {
    @org.junit.Test
    fun fileManagerSettingsReturnsToRetainedSessionWithoutBottomBar() {
        val manager = KiyoriShellState().openFileManager()
        val settings = manager.openSettings(origin = KiyoriSettingsOrigin.FILE_MANAGER)
        assertEquals(null, settings.child)
        assertFalse(settings.showsBottomBar)
        assertTrue(settings.fileManagerSessionOpen)
        assertEquals(KiyoriSettingsPresentation.SOURCE_OVERLAY, settings.settingsNavigation?.presentation)
        assertEquals(manager, settings.handleBack().state)
    }

    @Test fun fileManagerNestedSettingsPreservesItsOriginalSettingsParent() {
        val parent = KiyoriShellState().openSettings(KiyoriSettingsOrigin.BOTTOM_NAVIGATION).openSettingsRoute(KiyoriSettingsRoute.MORE_FEATURES)
        val manager = parent.openFileManager()
        val nested = manager.openSettings(KiyoriSettingsOrigin.FILE_MANAGER)
        assertEquals(nested, restoreKiyoriShellState(nested.toKiyoriShellSaveableValues()))
        assertEquals(manager, nested.closeSettingsRoute())
        assertEquals(parent, nested.closeSettingsRoute().closeChild())
    }

    @Test fun fileManagerMinimizationSurvivesSaveRestoreAndIndependentBrowserNavigation() {
        val minimized = KiyoriShellState().openFileManager().minimizeFileManager()
        assertEquals(SoftwareHomePage.AI_HOME, minimized.softwareHomePage)
        assertTrue(minimized.fileManagerMinimized)
        assertEquals(minimized, restoreKiyoriShellState(minimized.toKiyoriShellSaveableValues()))
        val browser = minimized.openBrowser(KiyoriBrowserReturnTarget.AI_HOME)
        assertTrue(browser.fileManagerMinimized)
        val restored = browser.openFileManager()
        assertFalse(restored.fileManagerMinimized); assertTrue(restored.fileManagerSessionOpen)
        assertEquals(browser.browserReturnTarget, restored.browserReturnTarget)
        assertFalse(restored.closeChild().fileManagerSessionOpen)
    }

    @Test
    fun `settings presentation assigns Back ownership to its active host`() {
        val presentations =
            listOf(
                KiyoriSettingsPresentation.PRIMARY_ROOT to true,
                KiyoriSettingsPresentation.SOURCE_OVERLAY to true,
                KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_HOME to false,
                KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL to false,
                KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE to false,
            )

        presentations.forEach { (presentation, shellOwnsBack) ->
            val navigation =
                KiyoriSettingsNavigationState.start(
                    origin =
                        if (presentation == KiyoriSettingsPresentation.PRIMARY_ROOT) {
                            KiyoriSettingsOrigin.BOTTOM_NAVIGATION
                        } else {
                            KiyoriSettingsOrigin.BROWSER_HOME
                        },
                    sessionId = presentation.name,
                ).copy(presentation = presentation)

            assertEquals(shellOwnsBack, isKiyoriSettingsBackOwnedByShell(navigation))
        }

        assertFalse(isKiyoriSettingsBackOwnedByShell(null))
    }

    @Test
    fun `root and settings host BackHandlers keep every Shell settings surface above its owner`() {
        val bottomSettingsHome =
            KiyoriShellState().openSettings(KiyoriSettingsOrigin.BOTTOM_NAVIGATION)
        val bottomDownload =
            bottomSettingsHome.openSettingsRoute(KiyoriSettingsRoute.DOWNLOAD)
        val browserSettingsHome =
            KiyoriShellState()
                .openBrowser(
                    returnTarget = KiyoriBrowserReturnTarget.SOFTWARE_HOME,
                    exitPresentation = KiyoriBrowserExitPresentation.CLOSE,
                ).openSettings(KiyoriSettingsOrigin.BROWSER_HOME)
        val browserAdBlock =
            browserSettingsHome
                .openSettingsRoute(KiyoriSettingsRoute.AD_BLOCK_OVERVIEW)
                .openSettingsRoute(KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS)
        val operitSettings =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openSettings(KiyoriSettingsOrigin.AI_HOST)
                .showSettingsOperitRoute()
        val browserWorkspace =
            browserSettingsHome
                .openSettingsRoute(KiyoriSettingsRoute.BROWSER)
                .suspendSettingsForBrowserWorkspace()
        val settingsStates =
            listOf(
                Triple(bottomSettingsHome, false, true),
                Triple(bottomDownload, false, true),
                Triple(browserSettingsHome, false, true),
                Triple(browserAdBlock, false, true),
                Triple(operitSettings, false, false),
                Triple(browserWorkspace, false, false),
            )

        settingsStates.forEach { (settingsState, rootEnabled, settingsHostEnabled) ->
            assertEquals(
                rootEnabled,
                shouldEnableKiyoriShellBackHandler(
                    aiHostIsRoot = true,
                    isAiDrawerOpen = false,
                    isBookmarkDrawerOpen = false,
                    isHistoryDrawerOpen = false,
                    isDownloadDrawerOpen = false,
                    settingsNavigation = settingsState.settingsNavigation,
                ),
            )
            assertEquals(
                settingsHostEnabled,
                shouldEnableKiyoriSettingsHostBackHandler(
                    settingsNavigation = settingsState.settingsNavigation,
                ),
            )
        }

        assertTrue(
            shouldEnableKiyoriShellBackHandler(
                aiHostIsRoot = true,
                isAiDrawerOpen = false,
                isBookmarkDrawerOpen = false,
                isHistoryDrawerOpen = false,
                isDownloadDrawerOpen = false,
                settingsNavigation = null,
            ),
        )
        assertFalse(shouldEnableKiyoriSettingsHostBackHandler(null))
    }

    @Test
    fun `App root and Operit host BackHandlers keep settings above Browser and below page guards`() {
        val settingsStates =
            listOf(
                KiyoriSettingsNavigationState
                    .start(
                        origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                        sessionId = "bottom-settings",
                    ),
                KiyoriSettingsNavigationState
                    .start(
                        origin = KiyoriSettingsOrigin.BROWSER_HOME,
                        sessionId = "browser-settings",
                    ),
                KiyoriSettingsNavigationState
                    .start(
                        origin = KiyoriSettingsOrigin.AI_HOST,
                        sessionId = "browser-workspace-settings",
                    )
                    .suspendForBrowserWorkspace(),
            )
        val operitPresentation = KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL

        settingsStates.forEach { settingsNavigation ->
            assertFalse(
                shouldEnableKiyoriAppBackHandler(
                    currentScreenIsAiChat = false,
                    isAiDrawerOpen = false,
                    settingsPresentation = settingsNavigation.presentation,
                ),
            )
            assertFalse(
                shouldEnableKiyoriOperitSettingsBackHandler(
                    currentScreenIsAiChat = false,
                    isAiDrawerOpen = false,
                    settingsPresentation = settingsNavigation.presentation,
                ),
            )
        }
        assertFalse(
            shouldEnableKiyoriAppBackHandler(
                currentScreenIsAiChat = false,
                isAiDrawerOpen = false,
                settingsPresentation = operitPresentation,
            ),
        )
        assertTrue(
            shouldEnableKiyoriOperitSettingsBackHandler(
                currentScreenIsAiChat = false,
                isAiDrawerOpen = false,
                settingsPresentation = operitPresentation,
            ),
        )
        assertTrue(
            shouldEnableKiyoriAppBackHandler(
                currentScreenIsAiChat = false,
                isAiDrawerOpen = false,
                settingsPresentation = null,
            ),
        )
        assertFalse(
            shouldEnableKiyoriAppBackHandler(
                currentScreenIsAiChat = true,
                isAiDrawerOpen = false,
                settingsPresentation = null,
            ),
        )
        assertFalse(
            shouldEnableKiyoriOperitSettingsBackHandler(
                currentScreenIsAiChat = true,
                isAiDrawerOpen = false,
                settingsPresentation = operitPresentation,
            ),
        )
        assertFalse(
            shouldEnableKiyoriOperitSettingsBackHandler(
                currentScreenIsAiChat = false,
                isAiDrawerOpen = true,
                settingsPresentation = operitPresentation,
            ),
        )
    }

    @Test
    fun `Browser system Back is disabled for every visible settings surface`() {
        val browserOwner =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.SOFTWARE_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.CLOSE,
            )
        val settingsHome = browserOwner.openSettings(KiyoriSettingsOrigin.BROWSER_HOME)
        val shellSettingsDetail =
            settingsHome.openSettingsRoute(KiyoriSettingsRoute.DOWNLOAD)
        val operitSettingsDetail = settingsHome.showSettingsOperitRoute()
        val browserWorkspace =
            settingsHome
                .openSettingsRoute(KiyoriSettingsRoute.BROWSER)
                .suspendSettingsForBrowserWorkspace()

        assertTrue(shouldEnableKiyoriBrowserHostBackHandler(browserOwner))
        assertFalse(shouldEnableKiyoriBrowserHostBackHandler(settingsHome))
        assertFalse(shouldEnableKiyoriBrowserHostBackHandler(shellSettingsDetail))
        assertFalse(shouldEnableKiyoriBrowserHostBackHandler(operitSettingsDetail))
        assertTrue(shouldEnableKiyoriBrowserHostBackHandler(browserWorkspace))
        assertFalse(
            shouldEnableKiyoriBrowserHostBackHandler(browserOwner.openBookmarkDrawer()),
        )
        assertFalse(
            shouldEnableKiyoriBrowserHostBackHandler(browserOwner.openHistoryDrawer()),
        )
        assertFalse(
            shouldEnableKiyoriBrowserHostBackHandler(browserOwner.openDownloadDrawer()),
        )
        assertFalse(
            shouldEnableKiyoriBrowserHostBackHandler(browserOwner.openAiDrawer()),
        )
    }

    @Test
    fun `settings and Browser presentations stay above retained AI route depth`() {
        listOf(
            KiyoriSettingsPresentation.PRIMARY_ROOT,
            KiyoriSettingsPresentation.SOURCE_OVERLAY,
            KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE,
        ).forEach { presentation ->
            assertFalse(
                shouldElevateKiyoriAiHost(
                    aiHostIsRoot = false,
                    settingsPresentation = presentation,
                ),
            )
        }
        assertTrue(
            shouldElevateKiyoriAiHost(
                aiHostIsRoot = false,
                settingsPresentation = KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL,
            ),
        )
        assertTrue(
            shouldElevateKiyoriAiHost(
                aiHostIsRoot = false,
                settingsPresentation = null,
            ),
        )
        KiyoriSettingsPresentation.entries.forEach { presentation ->
            assertFalse(
                shouldElevateKiyoriAiHost(
                    aiHostIsRoot = true,
                    settingsPresentation = presentation,
                ),
            )
        }
    }

    @Test
    fun `plugin loading progress is limited to visible AI surfaces`() {
        val aiHome =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
            )
        assertTrue(
            shouldPresentKiyoriPluginLoading(
                state = aiHome,
                currentScreenIsAiChat = true,
            ),
        )
        assertTrue(
            shouldPresentKiyoriPluginLoading(
                state = aiHome.openAiDrawer(),
                currentScreenIsAiChat = true,
            ),
        )
        assertTrue(
            shouldPresentKiyoriPluginLoading(
                state = aiHome,
                currentScreenIsAiChat = false,
            ),
        )

        val settingsDetail =
            aiHome
                .openSettings(KiyoriSettingsOrigin.AI_HOST)
                .showSettingsOperitRoute()
        assertTrue(
            shouldPresentKiyoriPluginLoading(
                state = settingsDetail,
                currentScreenIsAiChat = false,
            ),
        )

        listOf(
            KiyoriShellState(),
            KiyoriShellState(softwareHomePage = SoftwareHomePage.MINUS_ONE),
            aiHome.openChild(KiyoriShellChild.FULL_SCREEN_WEB_SEARCH),
            KiyoriShellState().selectPrimary(PrimaryDestination.BROWSER_HOME),
            KiyoriShellState().selectPrimary(PrimaryDestination.MINI_APP_HOME),
            KiyoriShellState().selectPrimary(PrimaryDestination.FILE_MANAGEMENT_HOME),
            KiyoriShellState().selectPrimary(PrimaryDestination.SETTINGS_HOME),
            aiHome.openBookmarkDrawer(),
            aiHome.openHistoryDrawer(),
            aiHome.openDownloadDrawer(),
        ).forEach { state ->
            assertFalse(
                shouldPresentKiyoriPluginLoading(
                    state = state,
                    currentScreenIsAiChat = true,
                ),
            )
        }

        assertFalse(
            shouldPresentKiyoriPluginLoading(
                state = settingsDetail.suspendSettingsForBrowserWorkspace(),
                currentScreenIsAiChat = false,
            ),
        )
    }

    @Test
    fun `settings child navigation inherits only the active Operit settings session`() {
        val settingsNavigation =
            KiyoriSettingsNavigationState
                .start(
                    origin = KiyoriSettingsOrigin.AI_HOST,
                    sessionId = "settings-ai",
                )
                .showOperitRoute()

        assertEquals(
            KiyoriSettingsNavigationContext(
                source = RouteEntrySource.KIYORI_SETTINGS,
                navigationContextId = "settings-ai",
            ),
            resolveKiyoriSettingsNavigationContext(
                requestedSource = RouteEntrySource.DEFAULT,
                requestedNavigationContextId = null,
                settingsPresentation = settingsNavigation.presentation,
                settingsSessionId = settingsNavigation.sessionId,
            ),
        )
        assertEquals(
            KiyoriSettingsNavigationContext(
                source = RouteEntrySource.AI_DRAWER,
                navigationContextId = "explicit-context",
            ),
            resolveKiyoriSettingsNavigationContext(
                requestedSource = RouteEntrySource.AI_DRAWER,
                requestedNavigationContextId = "explicit-context",
                settingsPresentation = settingsNavigation.presentation,
                settingsSessionId = settingsNavigation.sessionId,
            ),
        )
        assertEquals(
            KiyoriSettingsNavigationContext(
                source = RouteEntrySource.KIYORI_SETTINGS,
                navigationContextId = null,
            ),
            resolveKiyoriSettingsNavigationContext(
                requestedSource = RouteEntrySource.KIYORI_SETTINGS,
                requestedNavigationContextId = null,
                settingsPresentation = settingsNavigation.presentation,
                settingsSessionId = settingsNavigation.sessionId,
            ),
        )
        assertEquals(
            KiyoriSettingsNavigationContext(
                source = RouteEntrySource.DEFAULT,
                navigationContextId = null,
            ),
            resolveKiyoriSettingsNavigationContext(
                requestedSource = RouteEntrySource.DEFAULT,
                requestedNavigationContextId = null,
                settingsPresentation = KiyoriSettingsPresentation.SOURCE_OVERLAY,
                settingsSessionId = settingsNavigation.sessionId,
            ),
        )
    }

    @Test
    fun `every Browser Operit settings category restores visible Settings Home before Browser`() {
        val browserOwner =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        val retainedAiDetail =
            RouteEntry(
                instanceId = "retained-ai-detail",
                routeId = "native.memory_base",
                source = RouteEntrySource.DEFAULT,
            )
        val categoryScreens =
            listOf(
                Screen.AccountConnectionsSettings,
                Screen.Settings,
                Screen.TextToSpeechSettings,
                Screen.SpeechToTextSettings,
                Screen.AppearanceSettings,
                Screen.DataManagementSettings,
            )

        categoryScreens.forEach { screen ->
            val settingsDetail =
                browserOwner
                    .openSettings(KiyoriSettingsOrigin.BROWSER_HOME)
                    .showSettingsOperitRoute()
            val settingsSessionId = checkNotNull(settingsDetail.settingsNavigation).sessionId
            val categoryRoute =
                ScreenRouteRegistry
                    .toEntry(
                        screen = screen,
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    )
            val routerState = AppRouterState(retainedAiDetail)
            routerState.navigate(
                routeId = categoryRoute.routeId,
                args = categoryRoute.args,
                source = categoryRoute.source,
                navigationContextId = settingsSessionId,
            )

            assertEquals(RouteEntrySource.KIYORI_SETTINGS, routerState.currentEntry.source)
            assertEquals(settingsSessionId, routerState.currentEntry.navigationContextId)

            val restoredSettingsHome =
                popKiyoriRouterBackStack(
                    routerState = routerState,
                    shellState = settingsDetail,
                )
            assertEquals(retainedAiDetail, routerState.currentEntry)
            assertEquals(
                KiyoriSettingsPresentation.SOURCE_OVERLAY,
                restoredSettingsHome.settingsNavigation?.presentation,
            )
            assertFalse(
                shouldElevateKiyoriAiHost(
                    aiHostIsRoot = false,
                    settingsPresentation = restoredSettingsHome.settingsNavigation?.presentation,
                ),
            )

            val returnToBrowser = restoredSettingsHome.handleBack()
            assertEquals(KiyoriShellBackResult.CONSUMED, returnToBrowser.result)
            assertEquals(browserOwner, returnToBrowser.state)
        }
    }

    @Test
    fun `Operit settings router restores Settings Home only after its category root leaves`() {
        val settingsSessionId = "settings-browser"
        val underlyingBrowserOwner =
            RouteEntry(
                instanceId = "ai-owner",
                routeId = "native.ai_chat",
                source = RouteEntrySource.DEFAULT,
            )
        val settingsCategoryRoot =
            RouteEntry(
                instanceId = "settings-root",
                routeId = "native.settings",
                source = RouteEntrySource.KIYORI_SETTINGS,
                navigationContextId = settingsSessionId,
            )
        val settingsChild =
            RouteEntry(
                instanceId = "settings-child",
                routeId = "native.model_prompts",
                source = RouteEntrySource.KIYORI_SETTINGS,
                navigationContextId = settingsSessionId,
            )
        val settingsGrandchild =
            RouteEntry(
                instanceId = "settings-grandchild",
                routeId = "native.tag_market",
                source = RouteEntrySource.KIYORI_SETTINGS,
                navigationContextId = settingsSessionId,
            )
        val routerState = AppRouterState(underlyingBrowserOwner)
        routerState.restoreStack(
            listOf(
                underlyingBrowserOwner,
                settingsCategoryRoot,
                settingsChild,
                settingsGrandchild,
            ),
        )

        val restoreDecisions = mutableListOf<Boolean>()
        while (routerState.canPop) {
            val backStack = routerState.backStack
            restoreDecisions +=
                shouldRestoreKiyoriSettingsAfterRouterPop(
                    currentEntry = routerState.currentEntry,
                    previousEntry = backStack[backStack.lastIndex - 1],
                    settingsSessionId = settingsSessionId,
                )
            routerState.pop()
        }

        assertEquals(listOf(false, false, true), restoreDecisions)
        assertEquals(underlyingBrowserOwner, routerState.currentEntry)
        assertFalse(
            shouldRestoreKiyoriSettingsAfterRouterPop(
                currentEntry = settingsCategoryRoot,
                previousEntry = underlyingBrowserOwner,
                settingsSessionId = "another-settings-session",
            ),
        )
    }

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
                    settingsNavigation =
                        KiyoriSettingsNavigationState
                            .start(
                                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                                sessionId = "settings-save",
                            ).push(KiyoriSettingsRoute.DOWNLOAD),
                    isAiDrawerOpen = true,
                    browserReturnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                    browserExitPresentation =
                        KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
                ),
                KiyoriShellState(
                    primaryDestination = PrimaryDestination.BROWSER_HOME,
                    browserReturnTarget = KiyoriBrowserReturnTarget.SETTINGS_HOME,
                ),
                KiyoriShellState()
                    .openSettings(KiyoriSettingsOrigin.AI_HOST)
                    .openSettingsRoute(KiyoriSettingsRoute.MORE_FEATURES)
                    .openSettingsRoute(KiyoriSettingsRoute.AGREEMENT)
                    .openFileManager(),
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
    fun `settings shows bottom navigation only on the bottom-entry settings home`() {
        val bottomSettingsHome =
            KiyoriShellState().openSettings(KiyoriSettingsOrigin.BOTTOM_NAVIGATION)
        val browserSettingsHome =
            KiyoriShellState()
                .openBrowser(
                    returnTarget = KiyoriBrowserReturnTarget.SOFTWARE_HOME,
                    exitPresentation = KiyoriBrowserExitPresentation.CLOSE,
                ).openSettings(KiyoriSettingsOrigin.BROWSER_HOME)
        val aiSettingsHome =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openSettings(KiyoriSettingsOrigin.AI_HOST)
        val hiddenSettingsStates =
            listOf(
                bottomSettingsHome.openSettingsRoute(KiyoriSettingsRoute.DOWNLOAD),
                bottomSettingsHome
                    .openSettingsRoute(KiyoriSettingsRoute.AD_BLOCK_OVERVIEW)
                    .openSettingsRoute(KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS),
                browserSettingsHome,
                aiSettingsHome,
                bottomSettingsHome.showSettingsOperitRoute(),
                bottomSettingsHome
                    .openSettingsRoute(KiyoriSettingsRoute.BROWSER)
                    .suspendSettingsForBrowserWorkspace(),
            )

        assertTrue(isKiyoriBottomNavigationSettingsHome(bottomSettingsHome))
        assertEquals(
            1f,
            resolveKiyoriBottomBarAlpha(
                state = bottomSettingsHome,
                aiHostIsRoot = true,
                centerPageOffset = 0f,
            ),
            0.0001f,
        )
        hiddenSettingsStates.forEach { settingsState ->
            assertFalse(isKiyoriBottomNavigationSettingsHome(settingsState))
            assertEquals(
                0f,
                resolveKiyoriBottomBarAlpha(
                    state = settingsState,
                    aiHostIsRoot = true,
                    centerPageOffset = 0f,
                ),
                0.0001f,
            )
        }
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
    fun `browser home preserves the bottom settings session when opened from settings`() {
        val settingsHome = KiyoriShellState().selectPrimary(PrimaryDestination.SETTINGS_HOME)
        val browserState =
            settingsHome.openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME)

        assertEquals(PrimaryDestination.BROWSER_HOME, browserState.primaryDestination)
        assertEquals(
            KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_HOME,
            browserState.settingsNavigation?.presentation,
        )
        assertEquals(settingsHome, browserState.exitBrowser())
    }

    @Test
    fun `settings surface route starts a session when a restored settings home has none`() {
        val restoredSettingsHome =
            KiyoriShellState(primaryDestination = PrimaryDestination.SETTINGS_HOME)

        val moreFeatures =
            restoredSettingsHome.openSettingsSurfaceRoute(KiyoriSettingsRoute.MORE_FEATURES)

        assertEquals(KiyoriSettingsRoute.MORE_FEATURES, moreFeatures.settingsNavigation?.currentRoute)
        assertEquals(KiyoriSettingsOrigin.BOTTOM_NAVIGATION, moreFeatures.settingsNavigation?.origin)
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
        assertTrue(shouldAnimateKiyoriShellChildOverlay(state))
        assertEquals(
            KiyoriShellBackTransition(
                state = state.copy(child = null),
                result = KiyoriShellBackResult.CONSUMED,
            ),
            state.handleBack(),
        )
    }

    @Test
    fun `file toolbox browser keeps the file session minimized and restorable`() {
        val files = KiyoriShellState().openFileManager()
        val browser = files.minimizeFileManager().openBrowser(KiyoriBrowserReturnTarget.AI_HOME)

        assertEquals(PrimaryDestination.BROWSER_HOME, browser.primaryDestination)
        assertTrue(browser.fileManagerSessionOpen)
        assertTrue(browser.fileManagerMinimized)
        assertEquals(null, browser.child)
        val restored = browser.openFileManager()
        assertEquals(KiyoriShellChild.FILE_MANAGER, restored.child)
        assertFalse(restored.fileManagerMinimized)
        assertEquals(KiyoriBrowserReturnTarget.AI_HOME, restored.browserReturnTarget)
        assertTrue(browser.exitBrowser().fileManagerMinimized)
    }

    @Test
    fun `toolbox password settings hides bottom navigation and returns to the same browser`() {
        val browser = KiyoriShellState().openBrowser(KiyoriBrowserReturnTarget.AI_HOME)
        val passwords = browser.openSettings(
            origin = KiyoriSettingsOrigin.BROWSER_HOME,
            initialRoute = KiyoriSettingsRoute.BROWSER_PASSWORD_MANAGER,
        )

        assertFalse(passwords.showsBottomBar)
        assertEquals(KiyoriSettingsRoute.BROWSER_PASSWORD_MANAGER, passwords.settingsNavigation?.currentRoute)
        val settingsHome = passwords.closeSettingsRoute()
        assertEquals(KiyoriSettingsRoute.HOME, settingsHome.settingsNavigation?.currentRoute)
        assertFalse(settingsHome.showsBottomBar)
        assertEquals(browser, settingsHome.closeSettingsRoute())
    }

    @Test
    fun `file manager child preserves its settings session and exclusively owns the foreground`() {
        val settingsOwner =
            KiyoriShellState(
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openSettings(KiyoriSettingsOrigin.AI_HOST)
                .openSettingsRoute(KiyoriSettingsRoute.MORE_FEATURES)
        val fileManager = settingsOwner.openFileManager()

        assertEquals(KiyoriShellChild.FILE_MANAGER, fileManager.child)
        assertEquals(settingsOwner.settingsNavigation, fileManager.settingsNavigation)
        assertFalse(shouldAnimateKiyoriShellChildOverlay(fileManager))
        assertFalse(shouldPresentKiyoriSettingsOverlay(fileManager))
        assertEquals(settingsOwner, fileManager.closeChild())
        assertEquals(
            KiyoriShellBackTransition(
                state = settingsOwner,
                result = KiyoriShellBackResult.CONSUMED,
            ),
            fileManager.handleBack(),
        )
    }

    @Test
    fun `file management home opens the shared file manager child and returns to the same root`() {
        val owner =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.FILE_MANAGEMENT_HOME,
            )
        val fileManager = owner.openFileManager()

        assertEquals(KiyoriShellChild.FILE_MANAGER, fileManager.child)
        assertFalse(fileManager.showsBottomBar)
        assertEquals(owner, fileManager.closeChild())
    }

    @Test
    fun `file manager settings return and minimized restore never play child entrance`() {
        val manager = KiyoriShellState(primaryDestination = PrimaryDestination.FILE_MANAGEMENT_HOME).openFileManager()
        val settings = manager.openSettings(KiyoriSettingsOrigin.FILE_MANAGER)
        assertTrue(shouldPresentKiyoriSettingsOverlay(settings))
        assertFalse(settings.showsBottomBar)
        val restored = settings.closeSettingsRoute()
        assertEquals(manager, restored)
        assertFalse(shouldAnimateKiyoriShellChildOverlay(restored))
        assertFalse(shouldAnimateKiyoriShellChildOverlay(manager.minimizeFileManager().openFileManager()))
    }

    @Test
    fun `settings surfaces never enter the animated Shell child host`() {
        val bottomHome =
            KiyoriShellState().selectPrimary(PrimaryDestination.SETTINGS_HOME)
        val bottomShellDetail =
            bottomHome.openSettingsRoute(KiyoriSettingsRoute.BROWSER)
        val bottomOperitDetail = bottomHome.showSettingsOperitRoute()
        val browserOwner =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.SOFTWARE_HOME,
            )
        val sourceHome = browserOwner.openSettings(KiyoriSettingsOrigin.BROWSER_HOME)
        val sourceShellDetail =
            sourceHome.openSettingsRoute(KiyoriSettingsRoute.BROWSER)
        val sourceOperitDetail = sourceHome.showSettingsOperitRoute()

        listOf(
            bottomHome,
            bottomShellDetail,
            bottomOperitDetail,
            sourceHome,
            sourceShellDetail,
            sourceOperitDetail,
        ).forEach { state ->
            assertFalse(shouldAnimateKiyoriShellChildOverlay(state))
        }

        assertFalse(shouldPresentKiyoriSettingsOverlay(bottomHome))
        assertTrue(shouldPresentKiyoriSettingsOverlay(bottomShellDetail))
        assertFalse(shouldPresentKiyoriSettingsOverlay(bottomOperitDetail))
        assertTrue(shouldPresentKiyoriSettingsOverlay(sourceHome))
        assertTrue(shouldPresentKiyoriSettingsOverlay(sourceShellDetail))
        assertFalse(shouldPresentKiyoriSettingsOverlay(sourceOperitDetail))
        assertFalse(shouldPresentKiyoriSettingsOverlay(sourceShellDetail.openFileManager()))
    }

    @Test
    fun `home pager input is disabled while another Shell surface owns interaction`() {
        val home = KiyoriShellState()

        assertTrue(
            shouldAcceptKiyoriHomePagerInput(
                state = home,
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home,
                aiHostIsRoot = false,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home.openChild(KiyoriShellChild.FULL_SCREEN_WEB_SEARCH),
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home.openSettings(KiyoriSettingsOrigin.AI_HOST),
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home.openAiDrawer(),
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home.openBookmarkDrawer(),
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home.openHistoryDrawer(),
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state = home.openDownloadDrawer(),
                aiHostIsRoot = true,
            ),
        )
        assertFalse(
            shouldAcceptKiyoriHomePagerInput(
                state =
                    home.openBrowser(
                        returnTarget = KiyoriBrowserReturnTarget.SOFTWARE_HOME,
                        exitPresentation = KiyoriBrowserExitPresentation.CLOSE,
                    ),
                aiHostIsRoot = true,
            ),
        )
    }

    @Test
    fun `retained AI host owns system Back only while AI Home is settled and visible`() {
        val aiHome = KiyoriShellState().showSoftwareHomePage(SoftwareHomePage.AI_HOME)

        assertTrue(
            shouldEnableKiyoriAiHostSystemBack(
                state = aiHome,
                aiHostIsRoot = true,
                settledPagerPage = SoftwareHomePage.AI_HOME,
            ),
        )
        assertFalse(
            shouldEnableKiyoriAiHostSystemBack(
                state = aiHome,
                aiHostIsRoot = true,
                settledPagerPage = SoftwareHomePage.HOME,
            ),
        )
        assertFalse(
            shouldEnableKiyoriAiHostSystemBack(
                state = aiHome.showSoftwareHomePage(SoftwareHomePage.HOME),
                aiHostIsRoot = true,
                settledPagerPage = SoftwareHomePage.HOME,
            ),
        )
        assertFalse(
            shouldEnableKiyoriAiHostSystemBack(
                state = aiHome.openAiDrawer(),
                aiHostIsRoot = true,
                settledPagerPage = SoftwareHomePage.AI_HOME,
            ),
        )
    }

    @Test
    fun `settings child roots provide theme across page overlays`() {
        assertFalse(shouldProvideKiyoriSettingsTheme(null))
        assertFalse(
            shouldProvideKiyoriSettingsTheme(KiyoriSettingsRoute.HOME),
        )
        listOf(
            KiyoriSettingsRoute.MORE_FEATURES,
            KiyoriSettingsRoute.AGREEMENT,
            KiyoriSettingsRoute.BROWSER,
            KiyoriSettingsRoute.DOWNLOAD,
            KiyoriSettingsRoute.PLAYER,
            KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
        ).forEach { route ->
            assertTrue(shouldProvideKiyoriSettingsTheme(route))
        }
    }

    @Test
    fun `closing browser settings reveals the same browser owner and exit contract`() {
        val browserState =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        val settingsState =
            browserState.openSettings(
                origin = KiyoriSettingsOrigin.BROWSER_HOME,
                initialRoute = KiyoriSettingsRoute.BROWSER,
            )

        assertEquals(PrimaryDestination.BROWSER_HOME, settingsState.primaryDestination)
        assertEquals(KiyoriSettingsRoute.BROWSER, settingsState.settingsNavigation?.currentRoute)
        val settingsHome = settingsState.closeSettingsRoute()
        assertEquals(KiyoriSettingsRoute.HOME, settingsHome.settingsNavigation?.currentRoute)
        assertEquals(browserState, settingsHome.closeSettingsRoute())
    }

    @Test
    fun `source preserving settings home hides bottom navigation and restores browser owner`() {
        val browserState =
            KiyoriShellState().openBrowser(
                returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
            )
        val settingsHome =
            browserState.openSettings(KiyoriSettingsOrigin.BROWSER_HOME)

        assertEquals(PrimaryDestination.BROWSER_HOME, settingsHome.primaryDestination)
        assertEquals(KiyoriSettingsRoute.HOME, settingsHome.settingsNavigation?.currentRoute)
        assertFalse(settingsHome.showsBottomBar)
        assertEquals(browserState, settingsHome.closeSettingsRoute())
    }

    @Test
    fun `settings details return through source preserving settings home`() {
        val aiOwner =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            )
        val settingsHome = aiOwner.openSettings(KiyoriSettingsOrigin.AI_HOST)
        val browserSettings =
            settingsHome
                .openSettingsRoute(KiyoriSettingsRoute.BROWSER)
                .openSettingsRoute(KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION)

        assertEquals(
            KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION,
            browserSettings.settingsNavigation?.currentRoute,
        )
        assertEquals(
            KiyoriSettingsRoute.BROWSER,
            browserSettings.closeSettingsRoute().settingsNavigation?.currentRoute,
        )
        assertEquals(
            settingsHome,
            browserSettings.closeSettingsRoute().closeSettingsRoute(),
        )
        assertEquals(
            aiOwner,
            browserSettings
                .closeSettingsRoute()
                .closeSettingsRoute()
                .closeSettingsRoute(),
        )
    }

    @Test
    fun `ad blocker settings detail returns through settings home`() {
        val owner =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.HOME,
            )
        val settingsHome = owner.openSettings(KiyoriSettingsOrigin.AI_HOST)
        val adBlockSettings =
            settingsHome
                .openSettingsRoute(KiyoriSettingsRoute.AD_BLOCK_OVERVIEW)
                .openSettingsRoute(KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS)

        assertEquals(
            KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS,
            adBlockSettings.settingsNavigation?.currentRoute,
        )
        assertEquals(
            KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
            adBlockSettings.closeSettingsRoute().settingsNavigation?.currentRoute,
        )
        assertEquals(
            settingsHome,
            adBlockSettings.closeSettingsRoute().closeSettingsRoute(),
        )
    }

    @Test
    fun `every Shell settings route returns level by level to its bottom Browser or AI owner`() {
        val owners =
            listOf(
                KiyoriSettingsOrigin.BOTTOM_NAVIGATION to KiyoriShellState(),
                KiyoriSettingsOrigin.BROWSER_HOME to
                    KiyoriShellState().openBrowser(
                        returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                        exitPresentation = KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
                    ),
                KiyoriSettingsOrigin.AI_HOST to
                    KiyoriShellState(
                        softwareHomePage = SoftwareHomePage.AI_HOME,
                    ),
            )
        val routePaths =
            listOf(
                listOf(KiyoriSettingsRoute.MORE_FEATURES),
                listOf(
                    KiyoriSettingsRoute.MORE_FEATURES,
                    KiyoriSettingsRoute.AGREEMENT,
                ),
                listOf(KiyoriSettingsRoute.BROWSER),
                listOf(
                    KiyoriSettingsRoute.BROWSER,
                    KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION,
                ),
                listOf(
                    KiyoriSettingsRoute.BROWSER,
                    KiyoriSettingsRoute.BROWSER_PLUGIN_PERMISSIONS,
                ),
                listOf(
                    KiyoriSettingsRoute.BROWSER,
                    KiyoriSettingsRoute.BROWSER_TEXT_SIZE,
                ),
                listOf(
                    KiyoriSettingsRoute.BROWSER,
                    KiyoriSettingsRoute.BROWSER_PASSWORD_MANAGER,
                ),
                listOf(KiyoriSettingsRoute.DOWNLOAD),
                listOf(KiyoriSettingsRoute.PLAYER),
                listOf(KiyoriSettingsRoute.AD_BLOCK_OVERVIEW),
                listOf(
                    KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                    KiyoriSettingsRoute.AD_BLOCK_URL_RULES,
                ),
                listOf(
                    KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                    KiyoriSettingsRoute.AD_BLOCK_ELEMENT_RULES,
                ),
                listOf(
                    KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                    KiyoriSettingsRoute.AD_BLOCK_ALLOW_LIST,
                ),
                listOf(
                    KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                    KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS,
                ),
            )

        owners.forEach { (origin, owner) ->
            routePaths.forEach { routePath ->
                var state = owner.openSettings(origin)
                routePath.forEach { route ->
                    state = state.openSettingsRoute(route)
                }

                routePath.asReversed().forEach { expectedRoute ->
                    assertEquals(expectedRoute, state.settingsNavigation?.currentRoute)
                    val transition = state.handleBack()
                    assertEquals(KiyoriShellBackResult.CONSUMED, transition.result)
                    state = transition.state
                }

                assertEquals(KiyoriSettingsRoute.HOME, state.settingsNavigation?.currentRoute)
                val returnToOwner = state.handleBack()
                assertEquals(KiyoriShellBackResult.CONSUMED, returnToOwner.result)
                assertEquals(owner, returnToOwner.state)
            }
        }
    }

    @Test
    fun `external browser settings request uses settings home owner`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openExternalDestination(KiyoriShellExternalDestination.BROWSER_SETTINGS)

        assertEquals(PrimaryDestination.BROWSER_HOME, state.primaryDestination)
        assertEquals(
            KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
            state.settingsNavigation?.origin,
        )
        assertEquals(
            KiyoriSettingsRoute.BROWSER,
            state.settingsNavigation?.currentRoute,
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
                settingsNavigation = null,
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
            ).openSettings(KiyoriSettingsOrigin.BOTTOM_NAVIGATION)
        val settings = owner.openSettingsRoute(KiyoriSettingsRoute.DOWNLOAD)

        assertEquals(KiyoriSettingsRoute.DOWNLOAD, settings.settingsNavigation?.currentRoute)
        assertEquals(owner, settings.closeSettingsRoute())
    }

    @Test
    fun `external download child requests use settings home owner`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
            ).openExternalDestination(KiyoriShellExternalDestination.DOWNLOAD_SETTINGS)

        assertEquals(PrimaryDestination.BROWSER_HOME, state.primaryDestination)
        assertEquals(SoftwareHomePage.AI_HOME, state.softwareHomePage)
        assertEquals(KiyoriSettingsRoute.DOWNLOAD, state.settingsNavigation?.currentRoute)
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
                "main.memory_base" to KiyoriSemanticTone.GREEN,
                "main.packages" to KiyoriSemanticTone.PURPLE,
                "main.workflow" to KiyoriSemanticTone.ORANGE,
                "main.settings" to KiyoriSemanticTone.BLUE,
                "main.toolbox" to KiyoriSemanticTone.CYAN,
            )

        expected.forEach { (entryId, tone) ->
            assertEquals(tone, resolveKiyoriAiDrawerTone(testAiDrawerEntry(entryId)))
        }
    }

    @Test
    fun `AI drawer toolbox badge counts host and plugin toolbox entries`() {
        val entries =
            listOf(
                testAiDrawerEntry(
                    entryId = "toolbox.host",
                    surface = NavigationSurface.TOOLBOX,
                ),
                testAiDrawerEntry(
                    entryId = "toolpkg:demo:tool",
                    surface = NavigationSurface.TOOLBOX,
                ),
                testAiDrawerEntry(
                    entryId = "main.workflow",
                    surface = NavigationSurface.MAIN_SIDEBAR_TOOLS,
                ),
            )

        assertEquals(2, countKiyoriAiDrawerToolboxEntries(entries))
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
    fun `AI settings opened from Kiyori Settings restores the same settings session`() {
        val state =
            KiyoriShellState(
                primaryDestination = PrimaryDestination.SOFTWARE_HOME,
                softwareHomePage = SoftwareHomePage.AI_HOME,
                isAiDrawerOpen = true,
            ).openSettings(KiyoriSettingsOrigin.AI_HOST)
                .openSettingsRoute(KiyoriSettingsRoute.BROWSER)
                .showSettingsOperitRoute()

        assertEquals(
            KiyoriSettingsPresentation.SOURCE_OVERLAY,
            state.restoreSettingsAfterOperitRoute().settingsNavigation?.presentation,
        )
        assertEquals(
            KiyoriSettingsRoute.BROWSER,
            state.restoreSettingsAfterOperitRoute().settingsNavigation?.currentRoute,
        )
    }

    @Test
    fun `native permission center returns to more features in the same settings session`() {
        val moreFeatures =
            KiyoriShellState()
                .openSettings(KiyoriSettingsOrigin.BOTTOM_NAVIGATION)
                .openSettingsRoute(KiyoriSettingsRoute.MORE_FEATURES)
        val permissionCenter =
            moreFeatures.openSettingsRoute(KiyoriSettingsRoute.PERMISSIONS)

        assertEquals(
            KiyoriSettingsPresentation.PRIMARY_ROOT,
            permissionCenter.settingsNavigation?.presentation,
        )
        assertEquals(
            KiyoriSettingsRoute.PERMISSIONS,
            permissionCenter.settingsNavigation?.currentRoute,
        )
        val restored = permissionCenter.closeSettingsRoute()
        assertEquals(moreFeatures, restored)
        assertEquals(
            KiyoriSettingsRoute.HOME,
            restored.closeSettingsRoute().settingsNavigation?.currentRoute,
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
