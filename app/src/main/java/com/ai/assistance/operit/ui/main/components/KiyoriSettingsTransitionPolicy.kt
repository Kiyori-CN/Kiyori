package com.ai.assistance.operit.ui.main.components

import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.screens.Screen

/**
 * Settings-owned routes render an opaque Settings surface over the retained AI host.
 *
 * Keeping either side of that boundary in AppContent's crossfade would briefly expose the
 * retained conversation page while the new Settings route is being composed. The route source is
 * the authoritative boundary because it survives the Shell/Router handoff and covers both the
 * Settings root and its native children, including the real permission owner.
 */
internal fun shouldCrossfadeKiyoriRouteTransition(
    previousRouteEntry: RouteEntry,
    currentRouteEntry: RouteEntry,
    previousScreen: Screen,
    currentScreen: Screen,
): Boolean =
    previousRouteEntry.source != RouteEntrySource.KIYORI_SETTINGS &&
        currentRouteEntry.source != RouteEntrySource.KIYORI_SETTINGS &&
        previousScreen.participatesInCrossfadeTransition &&
        currentScreen.participatesInCrossfadeTransition
