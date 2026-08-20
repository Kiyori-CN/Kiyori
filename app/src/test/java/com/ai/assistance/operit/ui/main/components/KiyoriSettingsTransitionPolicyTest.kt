package com.ai.assistance.operit.ui.main.components

import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.screens.Screen
import org.junit.Assert.assertEquals
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
                        routeId = "native.data_management_settings",
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    ),
                previousScreen = Screen.Settings,
                currentScreen = Screen.DataManagementSettings,
            ),
        )
    }

    @Test
    fun `leaving Settings does not expose the retained AI page during replacement`() {
        assertFalse(
            shouldCrossfadeKiyoriRouteTransition(
                previousRouteEntry =
                    RouteEntry(
                        routeId = "native.data_management_settings",
                        source = RouteEntrySource.KIYORI_SETTINGS,
                    ),
                currentRouteEntry = RouteEntry(routeId = "native.ai_chat"),
                previousScreen = Screen.DataManagementSettings,
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

    @Test
    fun `direct replacement policy remains locked after the route key settles`() {
        assertFalse(
            resolveKiyoriActiveTransitionCrossfade(
                pendingRouteChangeAllowsCrossfade = null,
                lastTransitionAllowsCrossfade = false,
            ),
        )
        assertTrue(
            resolveKiyoriActiveTransitionCrossfade(
                pendingRouteChangeAllowsCrossfade = true,
                lastTransitionAllowsCrossfade = false,
            ),
        )
    }

    @Test
    fun `direct replacement bypasses tween alpha for every retained screen`() {
        assertEquals(
            1f,
            resolveKiyoriCachedScreenAlpha(
                isCurrentScreen = true,
                crossfadeAlpha = 0f,
                allowCrossfade = false,
            ),
            0f,
        )
        assertEquals(
            0f,
            resolveKiyoriCachedScreenAlpha(
                isCurrentScreen = false,
                crossfadeAlpha = 1f,
                allowCrossfade = false,
            ),
            0f,
        )
    }

    @Test
    fun `crossfade keeps following each screen visibility state`() {
        assertEquals(
            0.35f,
            resolveKiyoriCachedScreenAlpha(
                isCurrentScreen = true,
                crossfadeAlpha = 0.35f,
                allowCrossfade = true,
            ),
            0f,
        )
        assertEquals(
            0.65f,
            resolveKiyoriCachedScreenAlpha(
                isCurrentScreen = false,
                crossfadeAlpha = 0.65f,
                allowCrossfade = true,
            ),
            0f,
        )
    }

    @Test
    fun `top bar content is resolved only from the active cached screen`() {
        val values =
            mapOf(
                "kiyori.ai_home" to "ai-actions",
                "permissions-route" to "permission-actions",
            )

        assertEquals(
            "permission-actions",
            resolveKiyoriRouteScopedTopBarValue(
                currentScreenKey = "permissions-route",
                valuesByScreenKey = values,
            ),
        )
        assertEquals(
            "ai-actions",
            resolveKiyoriRouteScopedTopBarValue(
                currentScreenKey = "kiyori.ai_home",
                valuesByScreenKey = values,
            ),
        )
        assertEquals(
            null,
            resolveKiyoriRouteScopedTopBarValue(
                currentScreenKey = "new-route-without-actions",
                valuesByScreenKey = values,
            ),
        )
    }
}
