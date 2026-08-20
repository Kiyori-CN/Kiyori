package com.kiyori.integration.operit.navigation

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_NAV_SURFACE_MAIN_SIDEBAR_PLUGINS
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_NAV_SURFACE_TOOLBOX
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.icons.MaterialIconNameResolver
import com.ai.assistance.operit.ui.main.navigation.AppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.NavigationEntryActionSpec
import com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteRuntime
import com.ai.assistance.operit.ui.main.navigation.RouteSpec
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry

object AppRouteCatalog {
    fun buildStatic(context: Context): AppNavigationModel =
        buildModel(
            context = context,
            toolPkgRoutes = emptyList(),
            toolPkgNavigationEntries = emptyList(),
        )

    fun build(
        context: Context,
        packageManager: PackageManager,
    ): AppNavigationModel {
        val toolPkgRoutes =
            packageManager.getToolPkgUiRoutes(resolveContext = context).map { route ->
                RouteSpec(
                    routeId = route.routeId,
                    runtime = RouteRuntime.TOOLPKG_COMPOSE_DSL,
                    title = route.title,
                    icon = Icons.Default.Extension,
                    ownerPackageName = route.containerPackageName,
                    toolPkgUiModuleId = route.uiModuleId,
                    keepAlive = route.keepAlive
                )
            }
        val toolPkgNavigationEntries =
            packageManager.getToolPkgNavigationEntries(resolveContext = context).mapNotNull { entry ->
                val surface =
                    when (entry.surface.trim().lowercase()) {
                        TOOLPKG_NAV_SURFACE_TOOLBOX -> NavigationSurface.TOOLBOX
                        TOOLPKG_NAV_SURFACE_MAIN_SIDEBAR_PLUGINS ->
                            NavigationSurface.MAIN_SIDEBAR_PLUGINS
                        else -> null
                    } ?: return@mapNotNull null
                NavigationEntrySpec(
                    entryId = "toolpkg:${entry.containerPackageName}:${entry.entryId}",
                    routeId = entry.routeId,
                    surface = surface,
                    title = entry.title,
                    description = entry.description,
                    icon = MaterialIconNameResolver.resolveOrDefault(entry.icon, Icons.Default.Extension),
                    order = entry.order,
                    action =
                        entry.action?.let { action ->
                            NavigationEntryActionSpec(
                                functionName = action.functionName,
                                functionSource = action.functionSource
                            )
                        },
                    kind = NavigationEntryKind.PLUGIN,
                    ownerPackageName = entry.containerPackageName
                )
            }

        return buildModel(
            context = context,
            toolPkgRoutes = toolPkgRoutes,
            toolPkgNavigationEntries = toolPkgNavigationEntries,
        )
    }

    private fun buildModel(
        context: Context,
        toolPkgRoutes: List<RouteSpec>,
        toolPkgNavigationEntries: List<NavigationEntrySpec>,
    ): AppNavigationModel {
        return AppNavigationModel(
            routes = ScreenRouteRegistry.hostRouteSpecs(context) + toolPkgRoutes,
            navigationEntries =
                (
                    ScreenRouteRegistry.mainSidebarEntries(context) +
                        ScreenRouteRegistry.toolboxEntries(context) +
                        toolPkgNavigationEntries
                    )
                    .sortedWith(
                        compareBy<NavigationEntrySpec>({ it.surface.ordinal }, { it.order }, { it.title })
                    )
        )
    }

    fun resolveScreen(model: AppNavigationModel, entry: RouteEntry): Screen? {
        ScreenRouteRegistry.screenFromEntry(entry)?.let { return it }

        val spec = model.routesById[entry.routeId] ?: return null
        if (spec.runtime != RouteRuntime.TOOLPKG_COMPOSE_DSL) {
            return null
        }
        val containerPackageName = spec.ownerPackageName ?: return null
        val uiModuleId = spec.toolPkgUiModuleId ?: return null
        return Screen.ToolPkgComposeDsl(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId,
            title = spec.title ?: uiModuleId,
            keepAlive = spec.keepAlive
        )
    }

    fun initialEntry(navItem: NavItem): RouteEntry {
        return ScreenRouteRegistry.initialEntry(navItem)
    }

    fun toEntry(
        screen: Screen,
        source: RouteEntrySource = RouteEntrySource.DEFAULT
    ): RouteEntry {
        return ScreenRouteRegistry.toEntry(screen = screen, source = source)
    }
}
