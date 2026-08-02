package com.kiyori.integration.operit.navigation

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.navigation.AppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.AppRouteDiscoveryGateway
import com.ai.assistance.operit.ui.main.navigation.AppRouterGateway
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.matchesNavigationRoot
import com.ai.assistance.operit.ui.main.screens.Screen

internal class OperitNavigationRuntimeState(
    val packageManager: PackageManager,
) {
    var navigationRevision by mutableStateOf(0)
        private set

    fun invalidateNavigationModel() {
        navigationRevision += 1
    }
}

internal class OperitNavigationIntegration(
    val navigationModel: AppNavigationModel,
    private val runtimeState: OperitNavigationRuntimeState,
) {
    val toolPkgRuntimeListenerKey: Any
        get() = runtimeState

    fun initialEntry(navItem: NavItem): RouteEntry =
        AppRouteCatalog.initialEntry(navItem)

    fun resolveScreen(entry: RouteEntry): Screen? =
        AppRouteCatalog.resolveScreen(navigationModel, entry)

    fun toEntry(
        screen: Screen,
        source: RouteEntrySource = RouteEntrySource.DEFAULT,
    ): RouteEntry =
        AppRouteCatalog.toEntry(screen = screen, source = source)

    fun installToolPkgRuntimeChangeListener(): () -> Unit {
        val listener =
            PackageManager.ToolPkgRuntimeChangeListener { _ ->
                runtimeState.invalidateNavigationModel()
            }
        runtimeState.packageManager.addToolPkgRuntimeChangeListener(listener)
        return {
            runtimeState.packageManager.removeToolPkgRuntimeChangeListener(listener)
        }
    }

    fun runToolPkgNavigationEntryAction(
        containerPackageName: String,
        entryId: String,
        functionName: String,
        inlineFunctionSource: String?,
        eventPayload: Map<String, Any?>,
    ): Result<Any?> =
        runtimeState.packageManager.runToolPkgNavigationEntryAction(
            containerPackageName = containerPackageName,
            entryId = entryId,
            functionName = functionName,
            inlineFunctionSource = inlineFunctionSource,
            eventPayload = eventPayload,
        )
}

@Composable
internal fun rememberOperitNavigationIntegration(
    context: Context,
    configuration: Configuration,
): OperitNavigationIntegration {
    val runtimeState =
        remember {
            OperitNavigationRuntimeState(
                packageManager =
                    PackageManager.getInstance(
                        context,
                        AIToolHandler.getInstance(context),
                    ),
            )
        }
    val navigationModel =
        remember(context, configuration, runtimeState.navigationRevision) {
            AppRouteCatalog.build(
                context = context,
                packageManager = runtimeState.packageManager,
            )
        }
    return remember(runtimeState, navigationModel) {
        OperitNavigationIntegration(
            navigationModel = navigationModel,
            runtimeState = runtimeState,
        )
    }
}

@Composable
internal fun OperitNavigationIntegrationEffects(
    integration: OperitNavigationIntegration,
    routerState: AppRouterState,
    aiDrawerEntries: List<NavigationEntrySpec>,
    onNavigationStarted: () -> Unit,
) {
    DisposableEffect(integration.toolPkgRuntimeListenerKey) {
        val removeListener = integration.installToolPkgRuntimeChangeListener()
        onDispose {
            removeListener()
        }
    }
    DisposableEffect(routerState, integration.navigationModel, aiDrawerEntries) {
        AppRouterGateway.install(
            handler = { routeId, args, source ->
                val routeSpec =
                    integration.navigationModel.routesById[routeId] ?: return@install
                onNavigationStarted()
                val navigationRoot =
                    aiDrawerEntries.findOperitNavigationRoot(routeId, args)
                if (navigationRoot == null) {
                    routerState.navigate(
                        routeId = routeId,
                        args = args,
                        source = source,
                        routeSpec = routeSpec,
                    )
                } else {
                    routerState.resetTo(navigationRoot.toAiPrimaryRouteEntry(source))
                }
            },
            reset = { routeId, args, source ->
                integration.navigationModel.routesById[routeId] ?: return@install
                onNavigationStarted()
                routerState.resetTo(
                    aiDrawerEntries.toOperitExternalRouteEntry(
                        routeId = routeId,
                        routeArgs = args,
                        source = source,
                    ),
                )
            },
        )
        AppRouteDiscoveryGateway.install {
            integration.navigationModel.routes
        }
        onDispose {
            AppRouterGateway.clear()
            AppRouteDiscoveryGateway.clear()
        }
    }
}

internal fun List<NavigationEntrySpec>.findOperitNavigationRoot(
    routeId: String,
    routeArgs: Map<String, Any?>,
): NavigationEntrySpec? =
    firstOrNull { entry -> entry.matchesNavigationRoot(routeId, routeArgs) }

internal fun List<NavigationEntrySpec>.toOperitExternalRouteEntry(
    routeId: String,
    routeArgs: Map<String, Any?>,
    source: RouteEntrySource,
): RouteEntry =
    findOperitNavigationRoot(routeId, routeArgs)?.toAiPrimaryRouteEntry(source)
        ?: RouteEntry(
            routeId = routeId,
            args = routeArgs,
            source = source,
        )
