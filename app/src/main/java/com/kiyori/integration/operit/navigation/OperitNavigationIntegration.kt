package com.kiyori.integration.operit.navigation

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.kiyori.platform.logging.KiyoriLogger

private const val TAG = "OperitNavigation"

internal enum class ToolPkgRuntimeInitializationState {
    PENDING,
    READY,
    FAILED,
}

internal class OperitNavigationRuntimeState(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val packageManagerDelegate =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            PackageManager.getInstance(
                appContext,
                AIToolHandler.getInstance(appContext),
            )
        }

    var navigationRevision by mutableIntStateOf(0)
        private set
    var initializationState by mutableStateOf(ToolPkgRuntimeInitializationState.PENDING)
        private set

    val packageManager: PackageManager
        get() = packageManagerDelegate.value

    val runtimeReady: Boolean
        get() = initializationState == ToolPkgRuntimeInitializationState.READY

    val initializationComplete: Boolean
        get() = initializationState != ToolPkgRuntimeInitializationState.PENDING

    suspend fun awaitInitialization() {
        packageManager.awaitInitialization()
    }

    fun markInitializationReady() {
        initializationState = ToolPkgRuntimeInitializationState.READY
    }

    fun markInitializationFailed() {
        initializationState = ToolPkgRuntimeInitializationState.FAILED
    }

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

    val toolPkgRuntimeReady: Boolean
        get() = runtimeState.runtimeReady

    val toolPkgRuntimeInitializationComplete: Boolean
        get() = runtimeState.initializationComplete

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
            OperitNavigationRuntimeState(context)
        }
    LaunchedEffect(runtimeState) {
        // Dynamic package discovery is useful after the shell exists, but competing asset and
        // external-package scans with the first two frames visibly delays the software home.
        withFrameNanos { }
        withFrameNanos { }
        try {
            runtimeState.awaitInitialization()
            runtimeState.markInitializationReady()
        } catch (error: IllegalStateException) {
            runtimeState.markInitializationFailed()
            KiyoriLogger.e(
                TAG,
                "ToolPkg navigation initialization failed",
                error,
            )
        }
    }
    val navigationModel =
        remember(
            context,
            configuration,
            runtimeState.navigationRevision,
            runtimeState.initializationState,
        ) {
            if (runtimeState.runtimeReady) {
                AppRouteCatalog.build(
                    context = context,
                    packageManager = runtimeState.packageManager,
                )
            } else {
                AppRouteCatalog.buildStatic(context)
            }
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
    if (integration.toolPkgRuntimeReady) {
        DisposableEffect(integration.toolPkgRuntimeListenerKey) {
            val removeListener = integration.installToolPkgRuntimeChangeListener()
            onDispose {
                removeListener()
            }
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

internal fun shouldDeferPendingOperitRoute(
    routeKnown: Boolean,
    toolPkgRuntimeInitializationComplete: Boolean,
): Boolean =
    !routeKnown && !toolPkgRuntimeInitializationComplete

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
