package com.kiyori.integration.operit.navigation

import com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteSpec

enum class AiDrawerSelectionEffect {
    CLOSE_ONLY,
    REPLACE_PRIMARY,
}

enum class AiTopBarMode {
    DRAWER,
    BACK,
}

fun resolveAiDrawerSelection(
    currentEntryId: String?,
    targetEntryId: String,
): AiDrawerSelectionEffect =
    if (currentEntryId == targetEntryId) {
        AiDrawerSelectionEffect.CLOSE_ONLY
    } else {
        AiDrawerSelectionEffect.REPLACE_PRIMARY
    }

fun resolveAiTopBarMode(routeEntry: RouteEntry): AiTopBarMode =
    if (
        routeEntry.navigationRootEntryId != null &&
            routeEntry.source != RouteEntrySource.KIYORI_SETTINGS
    ) {
        AiTopBarMode.DRAWER
    } else {
        AiTopBarMode.BACK
    }

fun hasSameAiSettingsSourceFamily(
    currentSource: RouteEntrySource,
    targetSource: RouteEntrySource,
): Boolean =
    (currentSource == RouteEntrySource.KIYORI_SETTINGS) ==
        (targetSource == RouteEntrySource.KIYORI_SETTINGS)

private const val AI_NAVIGATION_ROOT_INSTANCE_PREFIX = "kiyori.ai.root:"

fun NavigationEntrySpec.toAiPrimaryRouteEntry(source: RouteEntrySource): RouteEntry {
    val routeEntry =
        RouteEntry(
            routeId = routeId,
            args = routeArgs,
            source = source,
            navigationRootEntryId = entryId,
        )
    return when (kind) {
        NavigationEntryKind.HOST ->
            routeEntry.copy(instanceId = "$AI_NAVIGATION_ROOT_INSTANCE_PREFIX$entryId")
        NavigationEntryKind.PLUGIN -> routeEntry
    }
}

fun NavigationEntrySpec.preservesAiPrimaryStack(routeSpec: RouteSpec): Boolean {
    require(routeSpec.routeId == routeId) {
        "Route spec ${routeSpec.routeId} does not belong to navigation entry $entryId"
    }
    return kind == NavigationEntryKind.HOST || routeSpec.keepAlive
}

fun buildAiPrimaryStack(
    targetRoot: RouteEntry,
    savedStack: List<RouteEntry>?,
    restoreChildren: Boolean,
): List<RouteEntry> {
    if (savedStack == null || !restoreChildren) {
        return listOf(targetRoot)
    }
    return buildList {
        add(targetRoot)
        addAll(savedStack.drop(1))
    }
}
