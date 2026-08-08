package com.kiyori.app

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.LocalAppNavigationModel
import com.ai.assistance.operit.ui.main.navigation.LocalOpenBrowser
import com.ai.assistance.operit.ui.main.navigation.LocalRouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.LocalTopBarActions
import com.ai.assistance.operit.ui.main.navigation.LocalTopBarTitleContent
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.navigation.NavigationSurface
import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource
import com.ai.assistance.operit.ui.main.navigation.RouteBackGuardRegistry
import com.ai.assistance.operit.ui.main.navigation.TopBarTitleContent
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.features.browser.appshell.KiyoriBrowserHome
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.main.AiHomeQuickAction
import com.ai.assistance.operit.ui.main.PendingAiHomeActionHandler
import com.kiyori.platform.logging.KiyoriLogger
import com.ai.assistance.operit.util.NetworkUtils
import com.kiyori.app.shell.KiyoriAppShell
import com.kiyori.app.shell.KiyoriBrowserReturnTarget
import com.kiyori.app.shell.KiyoriWebSearchRequest
import com.kiyori.app.shell.KiyoriShellChild
import com.kiyori.app.shell.KiyoriShellExternalDestination
import com.kiyori.app.shell.KiyoriShellState
import com.kiyori.app.shell.KiyoriShellStateSaver
import com.kiyori.app.shell.PrimaryDestination
import com.kiyori.app.shell.SoftwareHomePage
import com.kiyori.app.shell.openExternalChild
import com.kiyori.app.shell.openExternalDestination
import com.kiyori.app.shell.resolveKiyoriWebSearchRequest
import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation
import com.kiyori.integration.operit.navigation.AiDrawerSelectionEffect
import com.kiyori.integration.operit.navigation.AiTopBarMode
import com.kiyori.integration.operit.navigation.OperitNavigationIntegrationEffects
import com.kiyori.integration.operit.navigation.buildAiPrimaryStack
import com.kiyori.integration.operit.navigation.hasSameAiSettingsSourceFamily
import com.kiyori.integration.operit.navigation.preservesAiPrimaryStack
import com.kiyori.integration.operit.navigation.rememberOperitNavigationIntegration
import com.kiyori.integration.operit.navigation.resolveAiDrawerSelection
import com.kiyori.integration.operit.navigation.resolveAiTopBarMode
import com.kiyori.integration.operit.navigation.toOperitExternalRouteEntry
import com.kiyori.integration.operit.navigation.toAiPrimaryRouteEntry
import androidx.compose.foundation.layout.RowScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "KiyoriApp"
private const val EXIT_CONFIRM_WINDOW_MILLIS = 2_000L
private data class NetworkStateSnapshot(
    val isAvailable: Boolean,
    val type: String,
)

@Composable
fun KiyoriApp(
    initialNavItem: NavItem = NavItem.AiChat,
    shortcutNavRequest: NavItem? = null,
    shortcutNavRequestId: Long = 0L,
    routeNavRequest: String? = null,
    routeNavArgs: Map<String, Any?> = emptyMap(),
    routeNavRequestId: Long = 0L,
    browserOpenRequest: String? = null,
    browserOpenRequestId: Long = 0L,
    kiyoriShellDestinationRequest: KiyoriShellExternalDestination? = null,
    kiyoriShellRequestId: Long = 0L,
    onShortcutNavHandled: (Long) -> Unit = {},
    onCurrentNavItemChanged: (NavItem) -> Unit = {},
    onRouteNavHandled: (Long) -> Unit = {},
    onBrowserOpenHandled: (Long) -> Unit = {},
    onKiyoriShellRequestHandled: (Long) -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val browserHistoryStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val browserCoordinator =
        remember(context) { BrowserPresentationCoordinator.getInstance(context.applicationContext) }
    val browserWindowCount by browserCoordinator.browserWindowCount.collectAsState()
    val activity = remember(context) {
        context as? Activity ?: error("KiyoriApp must be hosted by an Activity")
    }
    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val operitNavigation =
        rememberOperitNavigationIntegration(
            context = context,
            configuration = configuration,
        )
    val navigationModel = operitNavigation.navigationModel
    var shellState by rememberSaveable(stateSaver = KiyoriShellStateSaver) {
        mutableStateOf(KiyoriShellState())
    }
    val updateShellState: (KiyoriShellState) -> Unit = { nextState ->
        shellState = nextState
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
            val initialEntry = operitNavigation.initialEntry(initialNavItem)
            AppRouterState(
                aiDrawerEntries.toOperitExternalRouteEntry(
                    routeId = initialEntry.routeId,
                    routeArgs = initialEntry.args,
                    source = RouteEntrySource.DEFAULT,
                ),
            )
        }
    val savedAiPrimaryStacks = remember { mutableMapOf<String, List<RouteEntry>>() }
    val routeBackGuardRegistry = remember { RouteBackGuardRegistry() }
    val currentRouteEntry = routerState.currentEntry
    val currentScreen = operitNavigation.resolveScreen(currentRouteEntry) ?: Screen.AiChat
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
    var lastHandledShortcutRequestId by remember { mutableLongStateOf(0L) }
    var lastHandledRouteRequestId by remember { mutableLongStateOf(0L) }
    var lastHandledBrowserOpenRequestId by remember { mutableLongStateOf(0L) }
    var lastHandledKiyoriShellRequestId by remember { mutableLongStateOf(0L) }
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

        val targetEntry = operitNavigation.initialEntry(requestNavItem)
        isNavigatingBack = false
        routerState.resetTo(
            aiDrawerEntries.toOperitExternalRouteEntry(
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
            KiyoriLogger.w(TAG, "Ignored pending route navigation for unknown routeId=$requestRouteId")
            lastHandledRouteRequestId = routeNavRequestId
            onRouteNavHandled(routeNavRequestId)
            return@LaunchedEffect
        }
        isNavigatingBack = false
        routerState.resetTo(
            aiDrawerEntries.toOperitExternalRouteEntry(
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
        updateShellState(
            shellState.openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME),
        )
        lastHandledBrowserOpenRequestId = browserOpenRequestId
        onBrowserOpenHandled(browserOpenRequestId)
    }

    LaunchedEffect(kiyoriShellRequestId, kiyoriShellDestinationRequest) {
        val destination = kiyoriShellDestinationRequest ?: return@LaunchedEffect
        if (
            kiyoriShellRequestId == 0L ||
                kiyoriShellRequestId == lastHandledKiyoriShellRequestId
        ) {
            return@LaunchedEffect
        }
        isNavigatingBack = false
        routerState.resetTo(
            aiChatDrawerEntry.toAiPrimaryRouteEntry(RouteEntrySource.DEFAULT),
        )
        updateShellState(shellState.openExternalDestination(destination))
        lastHandledKiyoriShellRequestId = kiyoriShellRequestId
        onKiyoriShellRequestHandled(kiyoriShellRequestId)
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
            operitNavigation.toEntry(
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

    fun openKiyoriSettingsRoot(
        screen: Screen,
        rootId: String,
    ) {
        saveCurrentAiPrimaryStack()
        isNavigatingBack = false
        routerState.resetTo(
            operitNavigation
                .toEntry(
                    screen = screen,
                    source = RouteEntrySource.KIYORI_SETTINGS,
                ).copy(
                    instanceId = "kiyori.settings.root:$rootId",
                    navigationRootEntryId = "kiyori.settings.$rootId",
                ),
        )
        updateShellState(
            shellState.selectPrimary(PrimaryDestination.SETTINGS_HOME),
        )
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
                updateShellState(shellState.returnFromKiyoriAiSettings())
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
                KiyoriLogger.e(TAG, "返回处理失败", e)
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
            operitNavigation.runToolPkgNavigationEntryAction(
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
                KiyoriLogger.e(
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

    fun submitWebSearch(request: KiyoriWebSearchRequest) {
        val createdSessionId =
            browserCoordinator.openUrlInNewSession(
                url = request.targetUrl,
                profile = request.profile,
            )
        if (createdSessionId != null) {
            if (request.profile.shouldPersistBrowserHistory) {
                scope.launch {
                    browserHistoryStore.addSearchHistory(request.query, request.targetUrl)
                }
            }
            updateShellState(
                shellState.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME),
            )
        }
    }

    fun openAiHome(action: AiHomeQuickAction?) {
        replaceAiPrimary(aiChatDrawerEntry, RouteEntrySource.DEFAULT)
        action?.let(PendingAiHomeActionHandler::request)
        updateShellState(
            shellState.showSoftwareHomePage(SoftwareHomePage.AI_HOME),
        )
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
    val isWideLayout = with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp
    var lastExitAttemptAt by remember { mutableLongStateOf(0L) }
    var isNetworkAvailable by remember { mutableStateOf(false) }
    var networkType by remember { mutableStateOf(resources.getString(R.string.not_connected)) }

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

    LaunchedEffect(context.applicationContext) {
        // LaunchedEffect 会在首帧绘制前启动；等待两个帧信号，确保首页已经独立画出一帧，
        // 再开始 MCP 配置读取与安装目录扫描，避免磁盘工作与首帧争用资源。
        withFrameNanos { }
        withFrameNanos { }
        withContext(Dispatchers.IO) {
            MCPRepository(context.applicationContext).syncInstalledStatus()
        }
    }

    // Main app container
    Box(modifier = Modifier.fillMaxSize()) {
        OperitNavigationIntegrationEffects(
            integration = operitNavigation,
            routerState = routerState,
            aiDrawerEntries = aiDrawerEntries,
            onNavigationStarted = {
                isNavigatingBack = false
            },
        )
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
                updateShellState(
                    shellState.openBrowser(
                        returnTarget = KiyoriBrowserReturnTarget.AI_HOME,
                        exitPresentation =
                            KiyoriBrowserExitPresentation.MINIMIZED_INDICATOR,
                    ),
                )
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
                browserWindowCount = browserWindowCount,
                onAiDrawerEntrySelected = ::selectAiDrawerEntry,
                onOpenAiHome = { openAiHome(AiHomeQuickAction.FOCUS_INPUT) },
                onAiQuickAction = { action -> openAiHome(action) },
                onAiHomeSettled = PendingAiHomeActionHandler::markAiHomeReady,
                onWeatherSearch = { city ->
                    scope.launch {
                        val request =
                            resolveKiyoriWebSearchRequest(
                                rawQuery = resources.getString(R.string.kiyori_home_weather_query, city),
                                searchEngine = browserHistoryStore.searchEngineFlow.first(),
                                profile = browserCoordinator.newSessionProfileState().defaultProfile,
                            )
                        request?.let(::submitWebSearch)
                    }
                },
                onOpenBrowserWindows = {
                    browserCoordinator.openWindowOverview()
                    updateShellState(
                        shellState.openBrowser(KiyoriBrowserReturnTarget.SOFTWARE_HOME),
                    )
                },
                onOpenBookmark = browserCoordinator::openUrl,
                onOpenBookmarkInTab = browserCoordinator::openUrlInSiblingSession,
                onOpenAccountConnectionsFromKiyoriSettings = {
                    openKiyoriSettingsRoot(
                        screen = Screen.AccountConnectionsSettings,
                        rootId = "account_connections",
                    )
                },
                onOpenAiAssistantFromKiyoriSettings = {
                    replaceAiPrimary(
                        aiSettingsDrawerEntry,
                        RouteEntrySource.KIYORI_SETTINGS,
                    )
                    updateShellState(
                        shellState.selectPrimary(PrimaryDestination.SETTINGS_HOME),
                    )
                },
                onOpenSpeechServicesFromKiyoriSettings = {
                    openKiyoriSettingsRoot(
                        screen = Screen.SpeechServicesSettings,
                        rootId = "speech_services",
                    )
                },
                onOpenBrowserSettingsFromKiyoriSettings = {
                    updateShellState(
                        shellState.openChild(KiyoriShellChild.BROWSER_SETTINGS),
                    )
                },
                onOpenAppearanceSettingsFromKiyoriSettings = {
                    openKiyoriSettingsRoot(
                        screen = Screen.AppearanceSettings,
                        rootId = "appearance",
                    )
                },
                onOpenDataSettingsFromKiyoriSettings = {
                    openKiyoriSettingsRoot(
                        screen = Screen.DataManagementSettings,
                        rootId = "data_management",
                    )
                },
                onSubmitWebSearch = ::submitWebSearch,
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
                            openAiHome(AiHomeQuickAction.FOCUS_INPUT)
                        },
                        onOpenBrowserSettings = {
                            updateShellState(
                                shellState.openChild(KiyoriShellChild.BROWSER_SETTINGS),
                            )
                        },
                        onOpenDownloadSettings = {
                            updateShellState(
                                shellState.openChild(KiyoriShellChild.DOWNLOAD_SETTINGS),
                            )
                        },
                        onCloseBrowser = {
                            updateShellState(shellState.exitBrowser())
                        },
                        exitPresentation = shellState.browserExitPresentation,
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
