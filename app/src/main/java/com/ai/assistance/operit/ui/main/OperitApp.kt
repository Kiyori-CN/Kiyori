package com.ai.assistance.operit.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.navigation.AppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.AppRouteCatalog
import com.ai.assistance.operit.ui.main.navigation.AppRouteDiscoveryGateway
import com.ai.assistance.operit.ui.main.navigation.AppRouterGateway
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.LocalRouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.shell.AiCenterDestination
import com.ai.assistance.operit.ui.main.shell.KiyoriAppShell
import com.ai.assistance.operit.ui.main.shell.KiyoriShellChild
import com.ai.assistance.operit.ui.main.shell.KiyoriShellState
import com.ai.assistance.operit.ui.main.shell.PrimaryDestination
import com.ai.assistance.operit.ui.main.shell.SoftwareHomePage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.RowScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 为TopAppBar的actions提供CompositionLocal
// 它允许子组件（如AIChatScreen）向上提供它们的action Composable
val LocalTopBarActions = compositionLocalOf<(@Composable (RowScope.() -> Unit)) -> Unit> { {} }

class TopBarTitleContent(val content: @Composable () -> Unit)

val LocalTopBarTitleContent = compositionLocalOf<(TopBarTitleContent?) -> Unit> { {} }
val LocalAppNavigationModel = compositionLocalOf<AppNavigationModel?> { null }

private const val TAG = "OperitApp"
private const val EXIT_CONFIRM_WINDOW_MILLIS = 2_000L

@Composable
fun OperitApp(
    initialNavItem: NavItem = NavItem.AiChat,
    shortcutNavRequest: NavItem? = null,
    shortcutNavRequestId: Long = 0L,
    routeNavRequest: String? = null,
    routeNavArgs: Map<String, Any?> = emptyMap(),
    routeNavRequestId: Long = 0L,
    onShortcutNavHandled: (Long) -> Unit = {},
    onCurrentNavItemChanged: (NavItem) -> Unit = {},
    onRouteNavHandled: (Long) -> Unit = {}
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) {
        context as? Activity ?: error("OperitApp must be hosted by an Activity")
    }
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    var navigationRevision by remember { mutableStateOf(0) }
    val configuration = LocalConfiguration.current
    val navigationModel = remember(context, configuration, navigationRevision) { AppRouteCatalog.build(context) }
    var primaryDestinationName by rememberSaveable {
        mutableStateOf(PrimaryDestination.SOFTWARE_HOME.name)
    }
    var softwareHomePageName by rememberSaveable {
        mutableStateOf(SoftwareHomePage.HOME.name)
    }
    var shellChildName by rememberSaveable { mutableStateOf<String?>(null) }
    val shellState =
        KiyoriShellState(
            primaryDestination = PrimaryDestination.valueOf(primaryDestinationName),
            softwareHomePage = SoftwareHomePage.valueOf(softwareHomePageName),
            child = shellChildName?.let(KiyoriShellChild::valueOf),
        )
    val updateShellState: (KiyoriShellState) -> Unit = { nextState ->
        primaryDestinationName = nextState.primaryDestination.name
        softwareHomePageName = nextState.softwareHomePage.name
        shellChildName = nextState.child?.name
    }

    val routerState = remember {
        AppRouterState(AppRouteCatalog.initialEntry(initialNavItem))
    }
    val routeBackGuardRegistry = remember { RouteBackGuardRegistry() }
    val currentRouteEntry = routerState.currentEntry
    val currentScreen = AppRouteCatalog.resolveScreen(navigationModel, currentRouteEntry) ?: Screen.AiChat
    val selectedItem = currentScreen.navItem
    val pluginSidebarEntries =
        remember(navigationModel) {
            navigationModel.navigationEntries.filter {
                it.surface == NavigationSurface.MAIN_SIDEBAR_PLUGINS
            }
        }

    // 跟踪是否是返回操作
    var isNavigatingBack by remember { mutableStateOf(false) }

    // 用于存储由子屏幕提供的TopAppBar Actions
    var topBarActions by remember { mutableStateOf<@Composable RowScope.() -> Unit>({}) }
    var topBarTitleContent by remember { mutableStateOf<TopBarTitleContent?>(null) }
    var lastHandledShortcutRequestId by remember { mutableStateOf(0L) }
    var lastHandledRouteRequestId by remember { mutableStateOf(0L) }

    LaunchedEffect(selectedItem) {
        selectedItem?.let { navItem ->
            onCurrentNavItemChanged(navItem)
        }
    }

    LaunchedEffect(shortcutNavRequestId, shortcutNavRequest) {
        val requestNavItem = shortcutNavRequest
        if (requestNavItem == null || shortcutNavRequestId == 0L) {
            return@LaunchedEffect
        }
        if (shortcutNavRequestId == lastHandledShortcutRequestId) {
            return@LaunchedEffect
        }

        val targetEntry = AppRouteCatalog.initialEntry(requestNavItem)
        isNavigatingBack = false
        routerState.resetTo(targetEntry)
        lastHandledShortcutRequestId = shortcutNavRequestId
        onShortcutNavHandled(shortcutNavRequestId)
    }

    LaunchedEffect(routeNavRequestId, routeNavRequest, routeNavArgs, navigationModel) {
        val requestRouteId = routeNavRequest?.trim().orEmpty()
        if (requestRouteId.isBlank() || routeNavRequestId == 0L) {
            return@LaunchedEffect
        }
        if (routeNavRequestId == lastHandledRouteRequestId) {
            return@LaunchedEffect
        }
        if (navigationModel.routesById[requestRouteId] == null) {
            AppLogger.w(TAG, "Ignored pending route navigation for unknown routeId=$requestRouteId")
            lastHandledRouteRequestId = routeNavRequestId
            onRouteNavHandled(routeNavRequestId)
            return@LaunchedEffect
        }
        isNavigatingBack = false
        routerState.resetTo(
            com.ai.assistance.operit.ui.main.navigation.RouteEntry(
                routeId = requestRouteId,
                args = routeNavArgs,
                source = RouteEntrySource.DEFAULT
            )
        )
        lastHandledRouteRequestId = routeNavRequestId
        onRouteNavHandled(routeNavRequestId)
    }

    // 当currentScreen改变时，检查是否需要清空TopBarActions
    // 这是为了解决从有action的屏幕导航到无action的屏幕时，action残留的问题
    LaunchedEffect(currentScreen) {
        if (currentScreen !is Screen.AiChat && currentScreen !is Screen.TokenConfig) {
            topBarActions = {}
        }
        topBarTitleContent = null
    }

    // Navigation functions
    fun navigateTo(
        newScreen: Screen,
        source: RouteEntrySource = RouteEntrySource.DEFAULT,
    ) {
        isNavigatingBack = false
        val nextEntry =
            AppRouteCatalog.toEntry(
                screen = newScreen,
                source = source,
            )
        if (currentRouteEntry.routeId == nextEntry.routeId && currentRouteEntry.args == nextEntry.args) {
            return
        }
        routerState.navigate(
            routeId = nextEntry.routeId,
            args = nextEntry.args,
            source = nextEntry.source,
            routeSpec = navigationModel.routesById[nextEntry.routeId],
        )
    }

    fun performGoBack() {
        if (routerState.canPop) {
            isNavigatingBack = true
            routerState.pop()
        } else if (currentScreen !is Screen.AiChat) {
            isNavigatingBack = true
            routerState.resetTo(AppRouteCatalog.toEntry(Screen.AiChat))
        }
    }

    var isBackRequestInProgress by remember { mutableStateOf(false) }

    fun requestGoBack() {
        val requestedRouteInstanceId = routerState.currentEntry.instanceId
        if (!routeBackGuardRegistry.hasGuard(requestedRouteInstanceId)) {
            performGoBack()
            return
        }
        if (isBackRequestInProgress) {
            return
        }

        isBackRequestInProgress = true
        scope.launch {
            try {
                val canNavigateBack =
                    routeBackGuardRegistry.canNavigateBack(requestedRouteInstanceId)
                if (
                    canNavigateBack &&
                        routerState.currentEntry.instanceId == requestedRouteInstanceId
                ) {
                    performGoBack()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "返回处理失败", e)
            } finally {
                isBackRequestInProgress = false
            }
        }
    }

    fun navigateToNavigationEntry(entry: NavigationEntrySpec) {
        val action = entry.action
        if (action != null) {
            val ownerPackageName = entry.ownerPackageName ?: return
            scope.launch(Dispatchers.IO) {
                packageManager.runToolPkgNavigationEntryAction(
                    containerPackageName = ownerPackageName,
                    entryId = entry.entryId,
                    functionName = action.functionName,
                    inlineFunctionSource = action.functionSource,
                    eventPayload =
                        mapOf(
                            "entryId" to entry.entryId,
                            "routeId" to entry.routeId,
                            "surface" to entry.surface.name.lowercase(),
                            "title" to entry.title,
                            "description" to entry.description
                        )
                ).onFailure { error ->
                    AppLogger.e(
                        TAG,
                        "ToolPkg navigation action failed: entryId=${entry.entryId}, package=$ownerPackageName",
                        error
                    )
                }
            }
            return
        }
        if (currentRouteEntry.routeId == entry.routeId && currentRouteEntry.args == entry.routeArgs) {
            return
        }
        isNavigatingBack = false
        routerState.navigate(
            routeId = entry.routeId,
            args = entry.routeArgs,
            source = RouteEntrySource.AI_CENTER,
            routeSpec = navigationModel.routesById[entry.routeId],
        )
    }

    // Function to navigate to TokenConfig, treated as sub-navigation.
    fun navigateToTokenConfig() {
        navigateTo(Screen.TokenConfig)
    }

    BackHandler(enabled = currentScreen !is Screen.AiChat, onBack = { requestGoBack() })

    val canGoBack = routerState.canPop || currentScreen !is Screen.AiChat

    var isLoading by remember { mutableStateOf(false) }
    var isAiHomeGestureBlocked by remember { mutableStateOf(false) }
    val isWideLayout = configuration.screenWidthDp >= 600
    var lastExitAttemptAt by remember { mutableLongStateOf(0L) }

    // Get FPS counter display setting
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val showFpsCounter = displayPreferencesManager.showFpsCounter.collectAsState(initial = false).value
    val enableNavigationAnimation =
        displayPreferencesManager.enableNavigationAnimation
            .collectAsState(initial = true)
            .value

    // Create an instance of MCPRepository
    val mcpRepository = remember { MCPRepository(context) }

    // Initialize MCP plugin status
    LaunchedEffect(Unit) {
        launch {
            // First scan local installed plugins
            mcpRepository.syncInstalledStatus()
        }
    }

    // Main app container
    Box(modifier = Modifier.fillMaxSize()) {
        DisposableEffect(packageManager) {
            val listener = PackageManager.ToolPkgRuntimeChangeListener { _ ->
                navigationRevision += 1
            }
            packageManager.addToolPkgRuntimeChangeListener(listener)
            onDispose {
                packageManager.removeToolPkgRuntimeChangeListener(listener)
            }
        }
        DisposableEffect(routerState, navigationModel) {
            AppRouterGateway.install(
                handler = { routeId, args, source ->
                    val routeSpec = navigationModel.routesById[routeId] ?: return@install
                    isNavigatingBack = false
                    routerState.navigate(routeId = routeId, args = args, source = source, routeSpec = routeSpec)
                },
                reset = { routeId, args, source ->
                    navigationModel.routesById[routeId] ?: return@install
                    isNavigatingBack = false
                    routerState.resetTo(
                        com.ai.assistance.operit.ui.main.navigation.RouteEntry(
                            routeId = routeId,
                            args = args,
                            source = source
                        )
                    )
                }
            )
            AppRouteDiscoveryGateway.install {
                navigationModel.routes
            }
            onDispose {
                AppRouterGateway.clear()
                AppRouteDiscoveryGateway.clear()
            }
        }
        CompositionLocalProvider(
            LocalAppNavigationModel provides navigationModel,
            LocalRouteBackGuardRegistry provides routeBackGuardRegistry,
            LocalTopBarActions provides { actions: @Composable RowScope.() -> Unit ->
                topBarActions = actions
            },
            LocalTopBarTitleContent provides { titleContent ->
                topBarTitleContent = titleContent
            }
        ) {
            KiyoriAppShell(
                state = shellState,
                onStateChange = updateShellState,
                aiHostIsRoot = currentScreen is Screen.AiChat,
                aiHomeGestureBlocked = isAiHomeGestureBlocked,
                selectedAiRouteId = currentRouteEntry.routeId,
                pluginEntries = pluginSidebarEntries,
                onAiCenterDestinationSelected = { destination ->
                    val screen =
                        when (destination) {
                            AiCenterDestination.PACKAGES -> Screen.Packages
                            AiCenterDestination.PERMISSIONS -> Screen.ShizukuCommands
                            AiCenterDestination.WORKFLOW -> Screen.Workflow
                            AiCenterDestination.ASSISTANT_CONFIG -> Screen.AssistantConfig
                            AiCenterDestination.MEMORY -> Screen.MemoryBase
                            AiCenterDestination.TOOLBOX -> Screen.Toolbox
                            AiCenterDestination.AI_SETTINGS -> Screen.Settings
                        }
                    navigateTo(screen, source = RouteEntrySource.AI_CENTER)
                },
                onPluginEntrySelected = ::navigateToNavigationEntry,
                onRequestExit = {
                    val now = System.currentTimeMillis()
                    if (now - lastExitAttemptAt <= EXIT_CONFIRM_WINDOW_MILLIS) {
                        activity.finishAffinity()
                    } else {
                        lastExitAttemptAt = now
                        Toast.makeText(
                            context,
                            R.string.kiyori_shell_exit_hint,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                aiHost = {
                    AppContent(
                        currentRouteEntry = currentRouteEntry,
                        currentScreen = currentScreen,
                        selectedItem = selectedItem,
                        isWideLayout = isWideLayout,
                        isLoading = isLoading,
                        navController = navController,
                        showFpsCounter = showFpsCounter,
                        enableNavigationAnimation = enableNavigationAnimation,
                        onScreenChange = { screen -> navigateTo(screen) },
                        onOpenNavigation = {
                            updateShellState(
                                shellState
                                    .showSoftwareHomePage(SoftwareHomePage.AI_HOME)
                                    .openChild(KiyoriShellChild.AI_CENTER),
                            )
                        },
                        navigateToTokenConfig = ::navigateToTokenConfig,
                        onGestureConsumed = { consumed ->
                            isAiHomeGestureBlocked = consumed
                        },
                        canGoBack = canGoBack,
                        onGoBack = ::requestGoBack,
                        isNavigatingBack = isNavigatingBack,
                        actions = { topBarActions() },
                        titleContent = topBarTitleContent,
                    )
                },
            )
        }

    }
}
