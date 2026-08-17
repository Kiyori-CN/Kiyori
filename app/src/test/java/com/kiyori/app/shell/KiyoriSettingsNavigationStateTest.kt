package com.kiyori.app.shell

import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSettingsNavigationStateTest {
    @Test
    fun `bottom settings route stack returns one level at a time`() {
        val state =
            KiyoriSettingsNavigationState
                .start(
                    origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                    sessionId = "settings-1",
                ).push(KiyoriSettingsRoute.BROWSER)
                .push(KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION)

        assertEquals(
            KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION,
            state.currentRoute,
        )
        assertEquals(KiyoriSettingsRoute.BROWSER, state.popRoute().currentRoute)
        assertEquals(
            KiyoriSettingsRoute.HOME,
            state.popRoute().popRoute().currentRoute,
        )
    }

    @Test
    fun `browser and AI settings preserve source-overlay presentation`() {
        listOf(
            KiyoriSettingsOrigin.BROWSER_HOME,
            KiyoriSettingsOrigin.AI_HOST,
            KiyoriSettingsOrigin.EXTERNAL_BROWSER_PRESENTATION,
        ).forEach { origin ->
            val state =
                KiyoriSettingsNavigationState.start(
                    origin = origin,
                    sessionId = origin.name,
                )
            assertEquals(KiyoriSettingsPresentation.SOURCE_OVERLAY, state.presentation)
            assertFalse(state.canPopRoute)
        }
    }

    @Test
    fun `Operit and browser workspace presentations restore the origin presentation`() {
        val sourceState =
            KiyoriSettingsNavigationState
                .start(
                    origin = KiyoriSettingsOrigin.AI_HOST,
                    sessionId = "settings-ai",
                ).push(KiyoriSettingsRoute.BROWSER)

        assertEquals(
            KiyoriSettingsPresentation.SOURCE_OVERLAY,
            sourceState.showOperitRoute().restoreSettingsPresentation().presentation,
        )
        assertEquals(
            KiyoriSettingsPresentation.SOURCE_OVERLAY,
            sourceState.suspendForBrowserWorkspace().restoreSettingsPresentation().presentation,
        )

        val bottomState =
            KiyoriSettingsNavigationState.start(
                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                sessionId = "settings-bottom",
            )
        assertEquals(
            KiyoriSettingsPresentation.PRIMARY_ROOT,
            bottomState.showOperitRoute().restoreSettingsPresentation().presentation,
        )
    }

    @Test
    fun `settings stack rejects duplicate roots and an empty stack`() {
        assertThrows(IllegalArgumentException::class.java) {
            KiyoriSettingsNavigationState(
                sessionId = "settings",
                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                routes = emptyList(),
                presentation = KiyoriSettingsPresentation.PRIMARY_ROOT,
            )
        }
        val root =
            KiyoriSettingsNavigationState.start(
                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                sessionId = "settings",
            )
        assertThrows(IllegalArgumentException::class.java) {
            root.push(KiyoriSettingsRoute.HOME)
        }
        assertTrue(root.routes.single() == KiyoriSettingsRoute.HOME)
    }

    @Test
    fun `browser workspace token freezes the exact settings return contract`() {
        val routes =
            listOf(
                KiyoriSettingsRoute.HOME,
                KiyoriSettingsRoute.BROWSER,
                KiyoriSettingsRoute.BROWSER_PLUGIN_PERMISSIONS,
            )
        val token =
            BrowserWorkspaceReturnToken(
                settingsSessionId = "settings-browser",
                settingsRoutes = routes,
                sourceBrowserSessionId = "browser-window",
                initialPluginRouteId = "userscript-detail:7",
            )

        assertEquals("settings-browser", token.settingsSessionId)
        assertEquals(routes, token.settingsRoutes)
        assertEquals("browser-window", token.sourceBrowserSessionId)
        assertEquals("userscript-detail:7", token.initialPluginRouteId)
    }
}
