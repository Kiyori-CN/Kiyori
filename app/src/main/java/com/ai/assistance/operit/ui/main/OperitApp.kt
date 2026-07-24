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
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
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
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.matchesNavigationRoot
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.shell.AiDrawerSelectionEffect
import com.ai.assistance.operit.ui.main.shell.AiSettingsEntrySource
import com.ai.assistance.operit.ui.main.shell.AiTopBarMode
import com.ai.assistance.operit.ui.main.shell.KiyoriAppShell
import com.ai.assistance.operit.ui.main.shell.KiyoriShellChild
import com.ai.assistance.operit.ui.main.shell.KiyoriBrowserReturnTarget
import com.ai.assistance.operit.ui.main.shell.KiyoriShellState
import com.ai.assistance.operit.ui.features.browser.appshell.KiyoriBrowserHome
import com.ai.assistance.operit.ui.main.shell.PrimaryDestination
import com.ai.assistance.operit.ui.main.shell.SoftwareHomePage
import com.ai.assistance.operit.ui.main.shell.resolveAiDrawerSelection
import com.ai.assistance.operit.ui.main.shell.resolveAiTopBarMode
import com.ai.assistance.operit.ui.main.shell.hasSameAiSettingsSourceFamily
import com.ai.assistance.operit.ui.main.shell.buildAiPrimaryStack
import com.ai.assistance.operit.ui.main.shell.preservesAiPrimaryStack
import com.ai.assistance.operit.ui.main.shell.toAiPrimaryRouteEntry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.NetworkUtils
import androidx.compose.foundation.layout.RowScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 为TopAppBar的actions提供CompositionLocal
// 它允许子组件（如AIChatScreen）向上提供它们的action Composable
val LocalTopBarActions = compositionLocalOf<(@Composable (RowScope.() -> Unit)) -> Unit> { {} }
val LocalOpenBrowser = compositionLocalOf<() -> Unit> { {} }

class TopBarTitleContent(val content: @Composable () -> Unit)

val LocalTopBarTitleContent = compositionLocalOf<(TopBarTitleContent?) -> Unit> { {} }
val LocalAppNavigationModel = compositionLocalOf<AppNavigationModel?> { null }

private const val TAG = "OperitApp"
private const val EXIT_CONFIRM_WINDOW_MILLIS = 2_000L
private data class NetworkStateSnapshot(
    val isAvailable: Boolean,
    val type: String,
)

private fun List<NavigationEntrySpec>.findAiNavigationRoot(
    routeId: String,
    routeArgs: Map<String, Any?>,
): NavigationEntrySpec? =
    firstOrNull { entry -> entry.matchesNavigationRoot(routeId, routeArgs) }

private fun List<NavigationEntrySpec>.toExternalRouteEntry(
    routeId: String,
    routeArgs: Map<String, Any?>,
    source: RouteEntrySource,
): RouteEntry =
    findAiNavigationRoot(routeId, routeArgs)?.toAiPrimaryRouteEntry(source)
        ?: RouteEntry(
            routeId = routeId,
            args = routeArgs,
            source = source,
        )

@Composable
fun OperitApp(
    initialNavItem: NavItem = NavItem.AiChat,
    shortcutNavRequest: NavItem? = null,
    shortcutNavRequestId: Long = 0L,
    routeNavRequest: String? = null,
    routeNavArgs: Map<String, Any?> = emptyMap(),
    routeNavRequestId: Long = 0L,
    browserOpenRequest: String? = null,
    browserOpenRequestId: Long = 0L,
    onShortcutNavHandled: (Long) -> Unit = {},
    onCurrentNavItemChanged: (NavItem) -> Unit = {},
    onRouteNavHandled: (Long) -> Unit = {},
    onBrowserOpenHandled: (Long) -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val browserHistoryStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
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
    var isAiDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var browserReturnTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    val shellState =
        KiyoriShellState(
            primaryDestination = PrimaryDestination.valueOf(primaryDestinationName),
            softwareHomePage = SoftwareHomePage.valueOf(softwareHomePageName),
            child = shellChildName?.let(KiyoriShellChild::valueOf),
            isAiDrawerOpen = isAiDrawerOpen,
            browserReturnTarget = browserReturnTargetName?.let(KiyoriBrowserReturnTarget::valueOf),
        )
    val updateShellState: (KiyoriShellState) -> Unit = { nextState ->
        primaryDestinationName = nextState.primaryDestination.name
        softwareHomePageName = nextState.softwareHomePage.name
        shellChildName = nextState.child?.name
        isAiDrawerOpen = nextState.isAiDrawerOpen
        browserReturnTargetName = nextState.browserReturnTarget?.name
    }

    val aiDrawerEntries =
        remember(navigationModel) {
            navigationModel.navigationEntries.filter { entry ->
                entry.surface == NavigationSurface.MAIN_SIDEBAR_AI ||
                    entry.surface == NavigationSurface.MAIN_SIDEBAR_TOOLS ||
                    entry.surface == NavigationSurface.MAIN_SIDEBAR_PLUGINS ||
                    entry.entryId == "main.settings"
            }
        }
    val aiChatDrawerEntry =
        remember(aiDrawerEntries) {
            aiDrawerEntries.single { entry -> entry.entryId == "main.ai_chat" }
        }
    val aiSettingsDrawerEntry =
        remember(aiDrawerEntries) {
            aiDrawerEntries.single { entry -> entry.entryId == "main.settings" }
        }
    val routerState =
        remember {
            val initialEntry = AppRouteCatalog.initialEntry(initialNavItem)
            AppRouterState(
                aiDrawerEntries.toExternalRouteEntry(
                    routeId = initialEntry.routeId,
                    routeArgs = initialEntry.args,
                    source = RouteEntrySource.DEFAULT,
                ),
            )
        }
    val savedAiPrimaryStacks = remember { mutableMapOf<String, List<RouteEntry>>() }
    val routeBackGuardRegistry = remember { RouteBackGuardRegistry() }
    val currentRouteEntry = routerState.currentEntry
    val currentScreen = AppRouteCatalog.resolveScreen(navigationModel, currentRouteEntry) ?: Screen.AiChat
    val selectedItem = currentScreen.navItem
    val currentAiPrimaryEntryId = routerState.backStack.first().navigationRootEntryId

    LaunchedEffect(aiDrawerEntries) {
        savedAiPrimaryStacks.keys.retainAll(aiDrawerEntries.mapTo(mutableSetOf()) { it.entryId })
    }

    // 跟踪是否是返回操作
    var isNavigatingBack by remember { mutableStateOf(false) }

    // 用于存储由子屏幕提供的TopAppBar Actions
    var topBarActions by remember { mutableStateOf<@Composable RowScope.() -> Unit>({}) }
    var topBarTitleContent by remember { mutableStateOf<TopBarTitleContent?>(null) }
    var lastHandledShortcutRequestId by remember { mutableStateOf(0L) }
    var lastHandledRouteRequestId by remember { mutableStateOf(0L) }
    var lastHandledBrowserOpenRequestId by remember { mutableStateOf(0L) }
    var isAiDrawerSelectionConsumed by remember { mutableStateOf(false) }

    LaunchedEffect(shellState.isAiDrawerOpen) {
        if (shellState.isAiDrawerOpen) {
            isAiDrawerSelectionConsumed = false
        }
    }

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
        routerState.resetTo(
            aiDrawerEntries.toExternalRouteEntry(
                routeId = targetEntry.routeId,
                routeArgs = targetEntry.args,
                source = RouteEntrySource.DEFAULT,
            ),
        )
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
            aiDrawerEntries.toExternalRouteEntry(
                routeId = requestRouteId,
                routeArgs = routeNavArgs,
                source = RouteEntrySource.DEFAULT,
            ),
        )
        lastHandledRouteRequestId = routeNavRequestId
        onRouteNavHandled(routeNavRequestId)
    }

    LaunchedEffect(browserOpenRequestId, browserOpenRequest) {
        val targetUrl = browserOpenRequest?.trim().orEmpty()
        if (targetUrl.isBlank() || browserOpenRequestId == 0L) {
            return@LaunchedEffect
        }
        if (browserOpenRequestId == lastHandledBrowserOpenRequestId) {
            return@LaunchedEffect
        }

        BrowserPresentationCoordinator.getInstance(context).openUrl(targetUrl)
        updateShellState(shellState.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME))
        lastHandledBrowserOpenRequestId = browserOpenRequestId
        onBrowserOpenHandled(browserOpenRequestId)
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

    fun saveCurrentAiPrimaryStack() {
        val entryId = routerState.backStack.first().navigationRootEntryId
        if (entryId == null) {
            return
        }
        val entry = aiDrawerEntries.firstOrNull { candidate -> candidate.entryId == entryId }
        if (entry == null) {
            savedAiPrimaryStacks.remove(entryId)
            return
        }
        val routeSpec =
            requireNotNull(navigationModel.routesById[entry.routeId]) {
                "Missing route spec ${entry.routeId} for navigation entry ${entry.entryId}"
            }
        if (entry.preservesAiPrimaryStack(routeSpec)) {
            savedAiPrimaryStacks[entryId] = routerState.backStack.toList()
        } else {
            savedAiPrimaryStacks.remove(entryId)
        }
    }

    fun replaceAiPrimary(
        entry: NavigationEntrySpec,
        source: RouteEntrySource,
    ) {
        // Each drawer root owns its child stack; replacing roots must not flatten another section.
        saveCurrentAiPrimaryStack()
        val routeSpec =
            requireNotNull(navigationModel.routesById[entry.routeId]) {
                "Missing route spec ${entry.routeId} for navigation entry ${entry.entryId}"
            }
        val preservesStack = entry.preservesAiPrimaryStack(routeSpec)
        val savedStack = if (preservesStack) savedAiPrimaryStacks[entry.entryId] else null
        if (!preservesStack) {
            savedAiPrimaryStacks.remove(entry.entryId)
        }
        val targetRoot = entry.toAiPrimaryRouteEntry(source)
        val restoreChildren =
            savedStack != null &&
                (
                    entry.entryId != aiSettingsDrawerEntry.entryId ||
                        hasSameAiSettingsSourceFamily(savedStack.first().source, source)
                    )
        val targetStack = buildAiPrimaryStack(targetRoot, savedStack, restoreChildren)
        isNavigatingBack = false
        routerState.restoreStack(targetStack)
    }

    fun performGoBack() {
        if (routerState.canPop) {
            isNavigatingBack = true
            routerState.pop()
        } else if (currentScreen !is Screen.AiChat) {
            isNavigatingBack = true
            val rootSource = routerState.backStack.first().source
            if (rootSource == RouteEntrySource.KIYORI_SETTINGS) {
                saveCurrentAiPrimaryStack()
                routerState.resetTo(
                    aiChatDrawerEntry.toAiPrimaryRouteEntry(RouteEntrySource.DEFAULT),
                )
                updateShellState(
                    shellState.returnFromAiSettings(AiSettingsEntrySource.KIYORI_SETTINGS),
                )
            } else {
                replaceAiPrimary(aiChatDrawerEntry, RouteEntrySource.DEFAULT)
                updateShellState(
                    shellState.showSoftwareHomePage(SoftwareHomePage.AI_HOME),
                )
            }
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

    fun runToolPkgNavigationEntryAction(entry: NavigationEntrySpec) {
        val action = entry.action
        requireNotNull(action) { "Navigation entry ${entry.entryId} has no action" }
        val ownerPackageName =
            requireNotNull(entry.ownerPackageName) {
                "Action navigation entry ${entry.entryId} has no owner package"
            }
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
                        "description" to entry.description,
                    ),
            ).onFailure { error ->
                AppLogger.e(
                    TAG,
                    "ToolPkg navigation action failed: entryId=${entry.entryId}, package=$ownerPackageName",
                    error,
                )
            }
        }
    }

    fun selectAiDrawerEntry(entry: NavigationEntrySpec) {
        if (!shellState.isAiDrawerOpen || isAiDrawerSelectionConsumed) {
            return
        }
        isAiDrawerSelectionConsumed = true
        val action = entry.action
        if (action != null) {
            updateShellState(shellState.closeAiDrawer())
            runToolPkgNavigationEntryAction(entry)
            return
        }
        when (resolveAiDrawerSelection(currentAiPrimaryEntryId, entry.entryId)) {
            AiDrawerSelectionEffect.CLOSE_ONLY -> Unit
            AiDrawerSelectionEffect.REPLACE_PRIMARY ->
                replaceAiPrimary(entry, RouteEntrySource.AI_DRAWER)
        }
        updateShellState(shellState.closeAiDrawer())
    }

    // Function to navigate to TokenConfig, treated as sub-navigation.
    fun navigateToTokenConfig() {
        navigateTo(Screen.TokenConfig)
    }

    BackHandler(
        enabled = currentScreen !is Screen.AiChat && !shellState.isAiDrawerOpen,
        onBack = { requestGoBack() },
    )

    val aiTopBarMode = resolveAiTopBarMode(currentRouteEntry)
    val showNavigationMenu = aiTopBarMode == AiTopBarMode.DRAWER

    var isLoading by remember { mutableStateOf(false) }
    var isAiHomeGestureBlocked by remember { mutableStateOf(false) }
    val isWideLayout = configuration.screenWidthDp >= 600
    var lastExitAttemptAt by remember { mutableLongStateOf(0L) }
    var isNetworkAvailable by remember { mutableStateOf(false) }
    var networkType by remember { mutableStateOf(context.getString(R.string.not_connected)) }

    LaunchedEffect(context.applicationContext) {
        while (true) {
            val snapshot =
                withContext(Dispatchers.IO) {
                    NetworkStateSnapshot(
                        isAvailable = NetworkUtils.isNetworkAvailable(context.applicationContext),
                        type = NetworkUtils.getNetworkType(context.applicationContext),
                    )
                }
            isNetworkAvailable = snapshot.isAvailable
            networkType = snapshot.type
            delay(10_000)
        }
    }

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
        DisposableEffect(routerState, navigationModel, aiDrawerEntries) {
            AppRouterGateway.install(
                handler = { routeId, args, source ->
                    val routeSpec = navigationModel.routesById[routeId] ?: return@install
                    isNavigatingBack = false
                    val navigationRoot = aiDrawerEntries.findAiNavigationRoot(routeId, args)
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
                    navigationModel.routesById[routeId] ?: return@install
                    isNavigatingBack = false
                    routerState.resetTo(
                        aiDrawerEntries.toExternalRouteEntry(
                            routeId = routeId,
                            routeArgs = args,
                            source = source,
                        ),
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
            },
            LocalOpenBrowser provides {
                BrowserPresentationCoordinator.getInstance(context).prepareBrowserForAiHome()
                updateShellState(shellState.openBrowser(KiyoriBrowserReturnTarget.AI_HOME))
            },
        ) {
            KiyoriAppShell(
                state = shellState,
                onStateChange = updateShellState,
                aiHostIsRoot = currentScreen is Screen.AiChat,
                aiHomeGestureBlocked = isAiHomeGestureBlocked,
                selectedAiEntryId = currentAiPrimaryEntryId,
                aiDrawerEntries = aiDrawerEntries,
                isNetworkAvailable = isNetworkAvailable,
                networkType = networkType,
                onAiDrawerEntrySelected = ::selectAiDrawerEntry,
                onOpenAiSettingsFromKiyoriSettings = {
                    replaceAiPrimary(
                        aiSettingsDrawerEntry,
                        RouteEntrySource.KIYORI_SETTINGS,
                    )
                    updateShellState(
                        shellState.selectPrimary(PrimaryDestination.SETTINGS_HOME),
                    )
                },
                onSubmitWebSearch = { request ->
                    val createdSessionId =
                        BrowserPresentationCoordinator.getInstance(context)
                        .openUrlInNewSession(
                            url = request.targetUrl,
                            profile = request.profile,
                        )
                    if (createdSessionId != null) {
                        scope.launch {
                            browserHistoryStore.addSearchHistory(request.query, request.targetUrl)
                        }
                        updateShellState(
                            shellState.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME),
                        )
                    }
                },
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
                browserHome = { modifier ->
                    KiyoriBrowserHome(
                        onExitBrowser = {
                            updateShellState(
                                shellState.exitBrowser(),
                            )
                        },
                        onOpenAiDialogue = {
                            updateShellState(
                                shellState.showSoftwareHomePage(SoftwareHomePage.AI_HOME),
                            )
                        },
                        onCloseBrowser = {
                            updateShellState(shellState.exitBrowser())
                        },
                        modifier = modifier,
                    )
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
                                    .openAiDrawer(),
                            )
                        },
                        navigateToTokenConfig = ::navigateToTokenConfig,
                        onGestureConsumed = { consumed ->
                            isAiHomeGestureBlocked = consumed
                        },
                        showNavigationMenu = showNavigationMenu,
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
