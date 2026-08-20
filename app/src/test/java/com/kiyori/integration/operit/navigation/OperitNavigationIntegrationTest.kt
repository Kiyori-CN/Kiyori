package com.kiyori.integration.operit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperitNavigationIntegrationTest {
    @Test
    fun `unknown route waits until ToolPkg discovery is complete`() {
        assertEquals(
            true,
            shouldDeferPendingOperitRoute(
                routeKnown = false,
                toolPkgRuntimeInitializationComplete = false,
            ),
        )
        assertEquals(
            false,
            shouldDeferPendingOperitRoute(
                routeKnown = true,
                toolPkgRuntimeInitializationComplete = false,
            ),
        )
        assertEquals(
            false,
            shouldDeferPendingOperitRoute(
                routeKnown = false,
                toolPkgRuntimeInitializationComplete = true,
            ),
        )
    }

    @Test
    fun `host navigation root owns any args for its route`() {
        val entries =
            listOf(
                navigationEntry(
                    entryId = "main.host",
                    routeId = "host.route",
                    kind = NavigationEntryKind.HOST,
                ),
            )

        val entry =
            entries.toOperitExternalRouteEntry(
                routeId = "host.route",
                routeArgs = mapOf("ignored" to true),
                source = RouteEntrySource.SCRIPT,
            )

        assertEquals("host.route", entry.routeId)
        assertEquals(emptyMap<String, Any?>(), entry.args)
        assertEquals(RouteEntrySource.SCRIPT, entry.source)
        assertEquals("main.host", entry.navigationRootEntryId)
        assertEquals("kiyori.ai.root:main.host", entry.instanceId)
    }

    @Test
    fun `plugin navigation root requires exact args and otherwise stays external`() {
        val registeredArgs = mapOf<String, Any?>("module" to "alpha")
        val entries =
            listOf(
                navigationEntry(
                    entryId = "toolpkg:demo:alpha",
                    routeId = "toolpkg.route",
                    routeArgs = registeredArgs,
                    kind = NavigationEntryKind.PLUGIN,
                ),
            )

        val registeredEntry =
            entries.toOperitExternalRouteEntry(
                routeId = "toolpkg.route",
                routeArgs = registeredArgs,
                source = RouteEntrySource.AI_DRAWER,
            )
        val externalEntry =
            entries.toOperitExternalRouteEntry(
                routeId = "toolpkg.route",
                routeArgs = mapOf("module" to "beta"),
                source = RouteEntrySource.SCRIPT,
            )

        assertEquals("toolpkg:demo:alpha", registeredEntry.navigationRootEntryId)
        assertEquals(registeredArgs, registeredEntry.args)
        assertNull(externalEntry.navigationRootEntryId)
        assertEquals(mapOf("module" to "beta"), externalEntry.args)
        assertEquals(RouteEntrySource.SCRIPT, externalEntry.source)
    }

    private fun navigationEntry(
        entryId: String,
        routeId: String,
        routeArgs: Map<String, Any?> = emptyMap(),
        kind: NavigationEntryKind,
    ): NavigationEntrySpec =
        NavigationEntrySpec(
            entryId = entryId,
            routeId = routeId,
            surface = NavigationSurface.MAIN_SIDEBAR_AI,
            title = entryId,
            icon = Icons.Default.Home,
            routeArgs = routeArgs,
            kind = kind,
        )
}
