package com.ai.assistance.operit.ui.main.components

import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.screens.Screen

/**
 * Settings-owned routes render an opaque Settings surface over the retained AI host.
 *
 * Keeping either side of that boundary in AppContent's crossfade would briefly expose the
 * retained conversation page while the new Settings route is being composed. The route source is
 * the authoritative boundary because it survives the Shell/Router handoff and covers the
 * remaining Operit-owned Settings roots and their native children.
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

/**
 * Keep the most recently applied route policy until another route change is observed.
 *
 * A direct replacement updates the route key before retained screens finish publishing their
 * hidden visibility state. Restoring crossfade merely because the keys now match would let those
 * screens animate from their previous opaque value and reappear behind the destination.
 */
internal fun resolveKiyoriActiveTransitionCrossfade(
    pendingRouteChangeAllowsCrossfade: Boolean?,
    lastTransitionAllowsCrossfade: Boolean,
): Boolean =
    pendingRouteChangeAllowsCrossfade ?: lastTransitionAllowsCrossfade

/**
 * Direct replacement owns the final rendered alpha instead of an animation target.
 *
 * Passing 0f or 1f into a tween still interpolates from the previous value. Bypassing that
 * interpolation is required so a retained AI screen and its internal drawer cannot survive for
 * another frame after a Settings-owned destination takes foreground ownership.
 */
internal fun resolveKiyoriCachedScreenAlpha(
    isCurrentScreen: Boolean,
    crossfadeAlpha: Float,
    allowCrossfade: Boolean,
): Float =
    when {
        !allowCrossfade && isCurrentScreen -> 1f
        !allowCrossfade -> 0f
        else -> crossfadeAlpha
    }

/**
 * Top-bar content belongs to the cached screen that published it.
 *
 * Retained screens can continue composing after navigation. Looking up only the active screen key
 * prevents a late publication from the retained AI Home from appearing on a newly selected route.
 */
internal fun <T> resolveKiyoriRouteScopedTopBarValue(
    currentScreenKey: String,
    valuesByScreenKey: Map<String, T>,
): T? = valuesByScreenKey[currentScreenKey]
