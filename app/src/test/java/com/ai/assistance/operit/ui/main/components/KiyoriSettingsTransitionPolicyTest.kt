package com.ai.assistance.operit.ui.main.components

import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.screens.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriSettingsTransitionPolicyTest {
    @Test
    fun `entering Settings disables retained AI crossfade`() {
        assertFalse(
            shouldCrossfadeKiyoriRouteTransition(
                previousRouteEntry = RouteEntry(routeId = "native.ai_chat"),
                currentRouteEntry =
                    RouteEntry(
                        routeId = "native.settings",
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    ),
                previousScreen = Screen.AiChat,
                currentScreen = Screen.Settings,
            ),
        )
    }

    @Test
    fun `moving between Settings-owned roots stays opaque`() {
        assertFalse(
            shouldCrossfadeKiyoriRouteTransition(
                previousRouteEntry =
                    RouteEntry(
                        routeId = "native.settings",
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    ),
                currentRouteEntry =
                    RouteEntry(
                        routeId = "native.shizuku_commands",
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    ),
                previousScreen = Screen.Settings,
                currentScreen = Screen.ShizukuCommands,
            ),
        )
    }

    @Test
    fun `leaving Settings does not expose the retained AI page during replacement`() {
        assertFalse(
            shouldCrossfadeKiyoriRouteTransition(
                previousRouteEntry =
                    RouteEntry(
                        routeId = "native.shizuku_commands",
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    ),
                currentRouteEntry = RouteEntry(routeId = "native.ai_chat"),
                previousScreen = Screen.ShizukuCommands,
                currentScreen = Screen.AiChat,
            ),
        )
    }

    @Test
    fun `ordinary compatible native routes keep their existing crossfade contract`() {
        assertTrue(
            shouldCrossfadeKiyoriRouteTransition(
                previousRouteEntry = RouteEntry(routeId = "native.ai_chat"),
                currentRouteEntry = RouteEntry(routeId = "native.memory"),
                previousScreen = Screen.AiChat,
                currentScreen = Screen.MemoryBase,
            ),
        )
    }

    @Test
    fun `a screen that opts out still disables crossfade`() {
        assertFalse(
            shouldCrossfadeKiyoriRouteTransition(
                previousRouteEntry = RouteEntry(routeId = "native.ai_chat"),
                currentRouteEntry = RouteEntry(routeId = "native.avatar_settings"),
                previousScreen = Screen.AiChat,
                currentScreen = Screen.AvatarSettings,
            ),
        )
    }
}
