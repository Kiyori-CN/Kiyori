package com.kiyori.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.main.AiHomeQuickAction
import com.ai.assistance.operit.ui.common.gestures.AiContentHorizontalGestureOwnership
import com.ai.assistance.operit.ui.common.gestures.LocalAiContentHorizontalGestureOwnership
import com.ai.assistance.operit.ui.main.components.LocalKiyoriAiHostSystemBackEnabled
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriLegalDocument
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriLegalDocumentScreen
import com.ai.assistance.operit.ui.features.agreement.screens.KiyoriLegalDocumentsScreen
import com.ai.assistance.operit.ui.features.about.screens.KiyoriOpenSourceLicensesPage
import com.ai.assistance.operit.ui.main.components.LocalKiyoriOpenFileManager
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.KiyoriFileManagerMinimizedIndicator
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.FileManagerScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.rememberFileManagerViewModel
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.shell.KiyoriBookmarkDrawerHost
import com.ai.assistance.operit.ui.main.shell.KiyoriBrowserSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriAdBlockSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriDownloadDrawerHost
import com.ai.assistance.operit.ui.main.shell.KiyoriDownloadSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriHistoryDrawerHost
import com.ai.assistance.operit.ui.main.shell.KiyoriMinusOnePage
import com.ai.assistance.operit.ui.main.shell.KiyoriMoreFeaturesSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriNetworkProxySettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriPlayerSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsHomePage
import com.kiyori.capability.browser.presentation.KiyoriBrowserWorkspaceRoute
import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute
import com.kiyori.design.theme.KiyoriBrowserTheme
import com.kiyori.design.theme.KiyoriSettingsTheme
import com.kiyori.integration.operit.onboarding.KiyoriPermissionsSettingsPage
import com.kiyori.integration.operit.onboarding.KiyoriOnboardingScreen
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
private fun KiyoriShellSurfaceThemeBoundary(
    settingsRoute: KiyoriSettingsRoute?,
    content: @Composable () -> Unit,
) {
    KiyoriBrowserTheme {
        // 设置页的选择面板与折叠列表是同级节点。主题边界必须包住完整子页，
        // 否则面板会在列表内部主题退出后读取不到 LocalKiyoriSettingsColors。
        if (shouldProvideKiyoriSettingsTheme(settingsRoute)) {
            KiyoriSettingsTheme(content)
        } else {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KiyoriAppShell(
    state: KiyoriShellState,
    onStateChange: (KiyoriShellState) -> Unit,
    aiHostIsRoot: Boolean,
    aiHomeGestureBlocked: Boolean,
    selectedAiEntryId: String?,
    aiDrawerEntries: List<NavigationEntrySpec>,
    isNetworkAvailable: Boolean,
    networkType: String,
    browserWindowCount: Int,
    onAiDrawerEntrySelected: (NavigationEntrySpec) -> Unit,
    onOpenAiHome: () -> Unit,
    onAiQuickAction: (AiHomeQuickAction) -> Unit,
    onAiHomeSettled: () -> Unit,
    onWeatherSearch: (String) -> Unit,
    onOpenBrowserWindows: () -> Unit,
    onQueueForegroundBrowserUrl: (String) -> Unit,
    onOpenBookmarkInTab: (String, Boolean) -> Unit,
    onOpenAccountConnectionsFromKiyoriSettings: () -> Unit,
    onOpenAiAssistantFromKiyoriSettings: () -> Unit,
    onOpenBrowserSettingsFromKiyoriSettings: () -> Unit,
    onOpenAppearanceSettingsFromKiyoriSettings: () -> Unit,
    onOpenDataSettingsFromKiyoriSettings: () -> Unit,
    onOpenBrowserWorkspace: (KiyoriBrowserWorkspaceRoute) -> Unit,
    onSubmitWebSearch: (KiyoriWebSearchRequest) -> Unit,
    onRequestExit: () -> Unit,
    browserHome: @Composable (Modifier, Boolean) -> Unit,
    aiHost: @Composable () -> Unit,
) {
    val pagerState =
        rememberPagerState(
            initialPage = state.softwareHomePage.pagerIndex,
            pageCount = { SoftwareHomePage.entries.size },
        )
    val latestState by rememberUpdatedState(state)
    val latestOnStateChange by rememberUpdatedState(onStateChange)
    val latestOnAiHomeSettled by rememberUpdatedState(onAiHomeSettled)
    val latestOnRequestExit by rememberUpdatedState(onRequestExit)
    val pagerFlingBehavior = PagerDefaults.flingBehavior(state = pagerState)
    val aiHomePagerGestureBridge =
        remember(pagerState) { KiyoriAiHomePagerGestureBridge(pagerState) }
    val aiContentHorizontalGestureOwnership =
        remember(aiHomePagerGestureBridge) {
            AiContentHorizontalGestureOwnership(
                onOwnershipChanged = aiHomePagerGestureBridge::updateContentGestureOwnership,
            )
        }

    var startupPreloadReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // LaunchedEffect 会在首帧绘制前启动；第二个帧信号到来时，首页首帧已经完成，
        // 此时再恢复相邻页和屏幕外 AI 页面的常驻预组合。
        withFrameNanos { }
        withFrameNanos { }
        startupPreloadReady = true
    }

    LaunchedEffect(state.primaryDestination, state.softwareHomePage) {
        if (
            state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
                pagerState.settledPage != state.softwareHomePage.pagerIndex
        ) {
            pagerState.animateScrollToPage(state.softwareHomePage.pagerIndex)
        }
    }

    LaunchedEffect(
        pagerState,
        state.primaryDestination,
        state.child,
        state.isAiDrawerOpen,
        state.isBookmarkDrawerOpen,
        state.isDownloadDrawerOpen,
        aiHostIsRoot,
    ) {
        if (
            state.primaryDestination != PrimaryDestination.SOFTWARE_HOME ||
                state.child != null ||
                state.isAiDrawerOpen ||
                state.isBookmarkDrawerOpen ||
                state.isDownloadDrawerOpen ||
                !aiHostIsRoot
        ) {
            return@LaunchedEffect
        }
        val initialSettledPage = SoftwareHomePage.fromPagerIndex(pagerState.settledPage)
        if (
            shouldNotifyKiyoriAiHomeSettledForInitialPage(
                initialSettledPage = initialSettledPage,
                requestedPage = latestState.softwareHomePage,
            )
        ) {
            latestOnAiHomeSettled()
        }
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            // When Browser Home is released, this collector restarts before the requested
            // software-home animation settles. Its first value can still be the old page;
            // ignore only that unchanged snapshot so a newly settled AI page is never discarded.
            .dropWhile { pageIndex -> pageIndex == initialSettledPage.pagerIndex }
            .collect { pageIndex ->
                val page = SoftwareHomePage.fromPagerIndex(pageIndex)
                if (latestState.softwareHomePage != page) {
                    latestOnStateChange(latestState.showSoftwareHomePage(page))
                }
                if (page == SoftwareHomePage.AI_HOME) {
                    latestOnAiHomeSettled()
                }
            }
    }

    val fileManagerUiState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    LaunchedEffect(state.fileManagerSessionOpen) {
        if (!state.fileManagerSessionOpen) fileManagerUiState.removeState("file-manager")
    }
    val fileManagerViewModel = if (state.fileManagerSessionOpen || state.child == KiyoriShellChild.FILE_MANAGER) rememberFileManagerViewModel(LocalContext.current) else null
    val shellChildOverlayVisible = shouldAnimateKiyoriShellChildOverlay(state)
    val dispatchShellBack: () -> Unit = {
        val transition = latestState.handleBack()
        when (transition.result) {
            KiyoriShellBackResult.CONSUMED -> latestOnStateChange(transition.state)
            KiyoriShellBackResult.REQUEST_EXIT -> latestOnRequestExit()
        }
    }

    BackHandler(
        enabled =
            shouldEnableKiyoriShellBackHandler(
                aiHostIsRoot = aiHostIsRoot,
                isAiDrawerOpen = state.isAiDrawerOpen,
                isBookmarkDrawerOpen = state.isBookmarkDrawerOpen,
                isHistoryDrawerOpen = state.isHistoryDrawerOpen,
                isDownloadDrawerOpen = state.isDownloadDrawerOpen,
                settingsNavigation = state.settingsNavigation,
            ),
        onBack = dispatchShellBack,
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val pagerReverseDirection = shouldReverseKiyoriPagerDrag(layoutDirection)
        val pagerAcceptsInput =
            !aiHomeGestureBlocked &&
                shouldAcceptKiyoriHomePagerInput(
                    state = state,
                    aiHostIsRoot = aiHostIsRoot,
                )
        val aiHomePagerGestureEnabled =
            pagerAcceptsInput &&
                pagerState.layoutInfo.pageSize > 0
        SideEffect {
            aiHomePagerGestureBridge.updateConfiguration(
                pageSizePx = pagerState.layoutInfo.pageSize.toFloat(),
                minimumFlingVelocityPxPerSecond =
                    with(density) {
                        KIYORI_HOME_PAGER_MIN_FLING_VELOCITY_DP_PER_SECOND.dp.toPx()
                    },
                layoutDirection = layoutDirection,
                externalGestureBlocked = aiHomeGestureBlocked,
            )
        }
        val aiPageOffset by remember(pagerState) {
            derivedStateOf {
                calculateKiyoriPagerPageOffset(
                    pagerState = pagerState,
                    page = SoftwareHomePage.AI_HOME.pagerIndex,
                )
            }
        }
        val centerPageOffset by remember(pagerState) {
            derivedStateOf {
                calculateKiyoriPagerPageOffset(
                    pagerState = pagerState,
                    page = SoftwareHomePage.HOME.pagerIndex,
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            userScrollEnabled = pagerAcceptsInput,
            flingBehavior = pagerFlingBehavior,
            beyondViewportPageCount = kiyoriStartupBeyondViewportPageCount(startupPreloadReady),
            key = { page -> page },
        ) { page ->
            when (SoftwareHomePage.fromPagerIndex(page)) {
                SoftwareHomePage.MINUS_ONE ->
                    KiyoriMinusOnePage(
                        onOpenBookmarkDrawer = {
                            latestOnStateChange(latestState.openBookmarkDrawer())
                        },
                        onOpenHistoryDrawer = {
                            latestOnStateChange(latestState.openHistoryDrawer())
                        },
                        onOpenDownloadDrawer = {
                            latestOnStateChange(latestState.openDownloadDrawer())
                        },
                        onClose = {
                            latestOnStateChange(
                                latestState.showSoftwareHomePage(SoftwareHomePage.HOME),
                            )
                        },
                    )
                SoftwareHomePage.HOME ->
                    KiyoriSoftwareHomePage(
                        browserWindowCount = browserWindowCount,
                        onSearchClick = {
                            onStateChange(
                                state.openChild(KiyoriShellChild.FULL_SCREEN_WEB_SEARCH),
                            )
                        },
                        onAiClick = onOpenAiHome,
                        onAiQuickAction = onAiQuickAction,
                        onWeatherSearch = onWeatherSearch,
                        onWindowsClick = onOpenBrowserWindows,
                    )
                SoftwareHomePage.AI_HOME -> Unit
            }
        }

        if (state.primaryDestination != PrimaryDestination.SOFTWARE_HOME) {
            if (state.primaryDestination == PrimaryDestination.BROWSER_HOME) {
                browserHome(
                    Modifier
                        .fillMaxSize()
                        .zIndex(4f),
                    shouldEnableKiyoriBrowserHostBackHandler(state),
                )
            } else {
                KiyoriPrimaryRootPage(
                    destination = state.primaryDestination,
                    onOpenAccountConnections = onOpenAccountConnectionsFromKiyoriSettings,
                    onOpenAiAssistant = onOpenAiAssistantFromKiyoriSettings,
                    onOpenBrowserSettings = onOpenBrowserSettingsFromKiyoriSettings,
                    onOpenFileManager = {
                        onStateChange(state.openFileManager())
                    },
                    onOpenDownloadSettings = {
                        onStateChange(
                            state.openSettings(
                                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                                initialRoute = KiyoriSettingsRoute.DOWNLOAD,
                            ),
                        )
                    },
                    onOpenPlayerSettings = {
                        onStateChange(
                            state.openSettings(
                                origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                                initialRoute = KiyoriSettingsRoute.PLAYER,
                            ),
                        )
                    },
                    onOpenAppearanceSettings = onOpenAppearanceSettingsFromKiyoriSettings,
                    onOpenDataSettings = onOpenDataSettingsFromKiyoriSettings,
                    onOpenMoreFeatures = {
                        onStateChange(
                            state.openSettingsSurfaceRoute(KiyoriSettingsRoute.MORE_FEATURES),
                        )
                    },
                    modifier = Modifier.fillMaxSize().zIndex(4f),
                )
            }
        }

        val forceAiHostFullscreen = !aiHostIsRoot
        val elevateAiHostAboveShellSurfaces =
            shouldElevateKiyoriAiHost(
                aiHostIsRoot = aiHostIsRoot,
                settingsPresentation = state.settingsNavigation?.presentation,
            )
        val aiHostTranslationX =
            if (forceAiHostFullscreen) {
                0f
            } else {
                calculateKiyoriAiHostTranslation(
                    pageOffset = aiPageOffset,
                    viewportWidthPx = viewportWidthPx,
                )
            }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .offset { IntOffset(aiHostTranslationX.roundToInt(), 0) }
                    .observeKiyoriAiHomePagerGesture(
                        bridge = aiHomePagerGestureBridge,
                        enabled = aiHomePagerGestureEnabled,
                    )
                    .scrollable(
                        state = aiHomePagerGestureBridge.scrollableState,
                        orientation = Orientation.Horizontal,
                        // HorizontalPager reverses LTR drag deltas before dispatching them to
                        // PagerState. The AI host sits above the pager, so it must use the same
                        // direction or a rightward drag is consumed against the last-page edge.
                        reverseDirection = pagerReverseDirection,
                        enabled = aiHomePagerGestureEnabled,
                        flingBehavior = aiHomePagerGestureBridge.flingBehavior,
                    )
                    .zIndex(if (elevateAiHostAboveShellSurfaces) 20f else 2f),
        ) {
            if (
                shouldComposeKiyoriAiHost(
                    aiHostIsRoot = aiHostIsRoot,
                    softwareHomePage = state.softwareHomePage,
                    startupPreloadReady = startupPreloadReady,
                )
            ) {
                CompositionLocalProvider(
                    LocalAiContentHorizontalGestureOwnership provides
                        aiContentHorizontalGestureOwnership,
                    LocalKiyoriAiHostSystemBackEnabled provides
                        shouldEnableKiyoriAiHostSystemBack(
                            state = state,
                            aiHostIsRoot = aiHostIsRoot,
                            settledPagerPage =
                                SoftwareHomePage.fromPagerIndex(pagerState.settledPage),
                        ),
                ) {
                    CompositionLocalProvider(LocalKiyoriOpenFileManager provides { onStateChange(latestState.openFileManager()) }) { aiHost() }
                }
            }
        }

        // 该处理器在 Browser/AI 宿主之后、具体设置页面之前注册。这样所有 Shell 设置页面
        // 都能压住底层宿主，页面内弹窗和未保存确认仍可在随后注册并取得最高优先级。
        BackHandler(
            enabled =
                state.child == null &&
                    shouldEnableKiyoriSettingsHostBackHandler(
                        settingsNavigation = state.settingsNavigation,
                    ),
            onBack = dispatchShellBack,
        )

        AnimatedVisibility(
            visible = shellChildOverlayVisible,
            modifier = Modifier.fillMaxSize().zIndex(12f),
            enter = fadeIn() + slideInVertically(initialOffsetY = { height -> height / 18 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { height -> height / 24 }),
        ) {
            KiyoriShellSurfaceThemeBoundary(
                settingsRoute = null,
            ) {
                when (state.child) {
                    KiyoriShellChild.FULL_SCREEN_WEB_SEARCH ->
                        KiyoriFullScreenWebSearchPage(
                            onBack = { onStateChange(state.closeChild()) },
                            onSubmitSearch = onSubmitWebSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    KiyoriShellChild.FILE_MANAGER, null -> Unit
                }
            }
        }

        // 文件页是固定工作表面。设置返回、悬浮球恢复都直接呈现，不能重播通用 child 的滑入动画。
        if (state.child == KiyoriShellChild.FILE_MANAGER) {
            Box(Modifier.fillMaxSize().zIndex(12f)) {
                KiyoriShellSurfaceThemeBoundary(settingsRoute = null) {
                    fileManagerUiState.SaveableStateProvider("file-manager") {
                        FileManagerScreen(
                            onBack = { onStateChange(state.closeChild()) },
                            onOpenSettings = { onStateChange(state.openSettings(origin = KiyoriSettingsOrigin.FILE_MANAGER)) },
                            sessionViewModel = fileManagerViewModel,
                            onOpenBrowser = {
                                onStateChange(latestState.minimizeFileManager().openBrowser(returnTarget = KiyoriBrowserReturnTarget.AI_HOME))
                            },
                            onOpenAiDialogue = {
                                onOpenAiHome()
                                onStateChange(state.minimizeFileManager())
                            },
                        )
                    }
                }
            }
        }

        if (state.fileManagerMinimized && state.child != KiyoriShellChild.FILE_MANAGER) {
            KiyoriFileManagerMinimizedIndicator(
                onRestore = { onStateChange(latestState.openFileManager()) },
                onClose = { onStateChange(latestState.closeMinimizedFileManager()) },
                modifier = Modifier.fillMaxSize().zIndex(15f),
            )
        }

        if (shouldPresentKiyoriSettingsOverlay(state)) {
            Box(
                modifier = Modifier.fillMaxSize().zIndex(12f),
            ) {
                // 设置 route 和 presentation 会在同一状态事务中变化。若这里继续复用
                // AnimatedVisibility，退出层会读取新的 HOME route 并与底部设置首页重复绘制，
                // 来源恢复时也会让设置首页重新淡入。设置 surface 必须直接呈现最终不透明状态。
                KiyoriShellSurfaceThemeBoundary(
                    settingsRoute = state.settingsNavigation?.currentRoute,
                ) {
                    when (state.settingsNavigation?.currentRoute) {
                        KiyoriSettingsRoute.HOME ->
                            KiyoriSettingsHomePage(
                                onOpenAccountConnections =
                                    onOpenAccountConnectionsFromKiyoriSettings,
                                onOpenAiAssistant = onOpenAiAssistantFromKiyoriSettings,
                                onOpenBrowserSettings = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.BROWSER),
                                    )
                                },
                                onOpenDownloadSettings = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.DOWNLOAD),
                                    )
                                },
                                onOpenPlayerSettings = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.PLAYER),
                                    )
                                },
                                onOpenAppearanceSettings =
                                    onOpenAppearanceSettingsFromKiyoriSettings,
                                onOpenDataSettings = onOpenDataSettingsFromKiyoriSettings,
                                onOpenMoreFeatures = {
                                    onStateChange(
                                        state.openSettingsSurfaceRoute(
                                            KiyoriSettingsRoute.MORE_FEATURES,
                                        ),
                                    )
                                },
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.MORE_FEATURES ->
                            KiyoriMoreFeaturesSettingsPage(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                onOpenPermissions = {
                                    onStateChange(
                                        state.openSettingsRoute(
                                            KiyoriSettingsRoute.PERMISSIONS,
                                        ),
                                    )
                                },
                                onOpenOnboardingReview = {
                                    onStateChange(state.openSettingsRoute(KiyoriSettingsRoute.ONBOARDING_REVIEW))
                                },
                                onOpenNetworkProxy = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.NETWORK_PROXY),
                                    )
                                },
                                onOpenOpenSource = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.OPEN_SOURCE_LICENSES),
                                    )
                                },
                                onOpenUserAgreement = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.USER_AGREEMENT),
                                    )
                                },
                                onOpenPrivacyPolicy = {
                                    onStateChange(
                                        state.openSettingsRoute(KiyoriSettingsRoute.PRIVACY_POLICY),
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.NETWORK_PROXY ->
                            KiyoriNetworkProxySettingsPage(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.PERMISSIONS ->
                            KiyoriPermissionsSettingsPage(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.ONBOARDING_REVIEW ->
                            KiyoriOnboardingScreen(
                                agreementAccepted = true,
                                onAgreementAccepted = {},
                                onComplete = { onStateChange(state.closeSettingsRoute()) },
                                startFromBeginning = true,
                                onExitReview = { onStateChange(state.closeSettingsRoute()) },
                            )
                        KiyoriSettingsRoute.AGREEMENT ->
                            KiyoriLegalDocumentsScreen(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.OPEN_SOURCE_LICENSES ->
                            KiyoriOpenSourceLicensesPage(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.USER_AGREEMENT ->
                            KiyoriLegalDocumentScreen(
                                document = KiyoriLegalDocument.USER_AGREEMENT,
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.PRIVACY_POLICY ->
                            KiyoriLegalDocumentScreen(
                                document = KiyoriLegalDocument.PRIVACY_POLICY,
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.BROWSER ->
                            KiyoriBrowserSettingsPage(
                                route = KiyoriSettingsRoute.BROWSER,
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                onNavigate = { route ->
                                    onStateChange(state.openSettingsRoute(route))
                                },
                                onOpenBrowserWorkspace = onOpenBrowserWorkspace,
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.DOWNLOAD ->
                            KiyoriDownloadSettingsPage(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.PLAYER ->
                            KiyoriPlayerSettingsPage(
                                onBack = { onStateChange(state.closeSettingsRoute()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        KiyoriSettingsRoute.AD_BLOCK_OVERVIEW,
                        KiyoriSettingsRoute.AD_BLOCK_URL_RULES,
                        KiyoriSettingsRoute.AD_BLOCK_ELEMENT_RULES,
                        KiyoriSettingsRoute.AD_BLOCK_ALLOW_LIST,
                        KiyoriSettingsRoute.AD_BLOCK_SUBSCRIPTIONS,
                        -> KiyoriAdBlockSettingsPage(
                            route = state.settingsNavigation.currentRoute,
                            onBack = { onStateChange(state.closeSettingsRoute()) },
                            onNavigate = { route ->
                                onStateChange(state.openSettingsRoute(route))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        KiyoriSettingsRoute.BROWSER_HOME_CUSTOMIZATION,
                        KiyoriSettingsRoute.BROWSER_PLUGIN_PERMISSIONS,
                        KiyoriSettingsRoute.BROWSER_TEXT_SIZE,
                        KiyoriSettingsRoute.BROWSER_PASSWORD_MANAGER,
                        -> KiyoriBrowserSettingsPage(
                            route = state.settingsNavigation.currentRoute,
                            onBack = { onStateChange(state.closeSettingsRoute()) },
                            onNavigate = { route ->
                                onStateChange(state.openSettingsRoute(route))
                            },
                            onOpenBrowserWorkspace = onOpenBrowserWorkspace,
                            modifier = Modifier.fillMaxSize(),
                        )
                        null -> Unit
                    }
                }
            }
        }

        val bottomBarAlpha =
            resolveKiyoriBottomBarAlpha(
                state = state,
                aiHostIsRoot = aiHostIsRoot,
                centerPageOffset = centerPageOffset,
            )
        if (bottomBarAlpha > 0.01f) {
                KiyoriBottomNavigation(
                    selectedDestination = state.primaryDestination,
                    alpha = bottomBarAlpha,
                    onDestinationSelected = { destination ->
                    onStateChange(
                        if (destination == PrimaryDestination.BROWSER_HOME) {
                            state.openExternalDestination(
                                KiyoriShellExternalDestination.BROWSER_HOME,
                            )
                        } else {
                            state.selectPrimary(destination)
                        }
                    )
                    },
                    modifier = Modifier.zIndex(10f),
                )
        }

        KiyoriDownloadDrawerHost(
            isVisible =
                shouldPresentKiyoriDownloadDrawer(
                    isDownloadDrawerOpen = state.isDownloadDrawerOpen,
                    aiHostIsRoot = aiHostIsRoot,
                ),
            onDismissRequest = {
                latestOnStateChange(latestState.closeDownloadDrawer())
            },
            onOpenDownloadSettings = {
                latestOnStateChange(
                    latestState
                        .closeDownloadDrawer()
                        .openSettings(
                            origin = KiyoriSettingsOrigin.BOTTOM_NAVIGATION,
                            initialRoute = KiyoriSettingsRoute.DOWNLOAD,
                        ),
                )
            },
            modifier = Modifier.fillMaxSize().zIndex(30f),
        )

        KiyoriHistoryDrawerHost(
            isVisible =
                shouldPresentKiyoriHistoryDrawer(
                    isHistoryDrawerOpen = state.isHistoryDrawerOpen,
                    aiHostIsRoot = aiHostIsRoot,
                ),
            onDismissRequest = {
                latestOnStateChange(latestState.closeHistoryDrawer())
            },
            onOpenWebHistory = { url ->
                onQueueForegroundBrowserUrl(url)
                latestOnStateChange(
                    latestState
                        .closeHistoryDrawer()
                        .openExternalDestination(KiyoriShellExternalDestination.BROWSER_HOME),
                )
            },
            modifier = Modifier.fillMaxSize().zIndex(30f),
        )

        KiyoriBookmarkDrawerHost(
            isVisible =
                shouldPresentKiyoriBookmarkDrawer(
                    isBookmarkDrawerOpen = state.isBookmarkDrawerOpen,
                    aiHostIsRoot = aiHostIsRoot,
                ),
            onDismissRequest = {
                latestOnStateChange(latestState.closeBookmarkDrawer())
            },
            onOpenBookmark = { url ->
                onQueueForegroundBrowserUrl(url)
                latestOnStateChange(
                    latestState
                        .closeBookmarkDrawer()
                        .openExternalDestination(
                            KiyoriShellExternalDestination.BROWSER_HOME,
                        ),
                )
            },
            onOpenBookmarkInTab = { url, active ->
                onOpenBookmarkInTab(url, active)
                if (active) {
                    latestOnStateChange(
                        latestState.openExternalDestination(
                            KiyoriShellExternalDestination.BROWSER_HOME,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(30f),
        )

        KiyoriModalAiDrawer(
            isOpen = state.isAiDrawerOpen,
            selectedEntryId = selectedAiEntryId,
            navigationEntries = aiDrawerEntries,
            isNetworkAvailable = isNetworkAvailable,
            networkType = networkType,
            onDismiss = { onStateChange(state.closeAiDrawer()) },
            onEntrySelected = onAiDrawerEntrySelected,
            modifier = Modifier.fillMaxSize().zIndex(40f),
        )
    }
}

internal fun kiyoriStartupBeyondViewportPageCount(startupPreloadReady: Boolean): Int =
    if (startupPreloadReady) 1 else 0

internal fun shouldComposeKiyoriAiHost(
    aiHostIsRoot: Boolean,
    softwareHomePage: SoftwareHomePage,
    startupPreloadReady: Boolean,
): Boolean =
    startupPreloadReady || !aiHostIsRoot || softwareHomePage == SoftwareHomePage.AI_HOME

internal fun shouldElevateKiyoriAiHost(
    aiHostIsRoot: Boolean,
    settingsPresentation: KiyoriSettingsPresentation?,
): Boolean {
    if (aiHostIsRoot) {
        return false
    }
    // 非根 AI 路由需要保持挂载，但只有它本身是当前展示 owner 时才能覆盖 Shell。
    // 否则 Browser 来源恢复出的设置首页会被旧 AI 页面遮住，后续 Back 也会落到错误宿主。
    return when (settingsPresentation) {
        null,
        KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL,
        -> true
        KiyoriSettingsPresentation.PRIMARY_ROOT,
        KiyoriSettingsPresentation.SOURCE_OVERLAY,
        KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_HOME,
        KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE,
        -> false
    }
}

internal fun shouldPresentKiyoriPluginLoading(
    state: KiyoriShellState,
    currentScreenIsAiChat: Boolean,
): Boolean {
    if (
        state.child != null ||
            state.isBookmarkDrawerOpen ||
            state.isHistoryDrawerOpen ||
            state.isDownloadDrawerOpen
    ) {
        return false
    }
    if (!currentScreenIsAiChat) {
        return shouldElevateKiyoriAiHost(
            aiHostIsRoot = false,
            settingsPresentation = state.settingsNavigation?.presentation,
        )
    }
    return state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
        state.softwareHomePage == SoftwareHomePage.AI_HOME &&
        state.settingsNavigation == null
}

internal fun shouldNotifyKiyoriAiHomeSettledForInitialPage(
    initialSettledPage: SoftwareHomePage,
    requestedPage: SoftwareHomePage,
): Boolean =
    initialSettledPage == SoftwareHomePage.AI_HOME &&
        requestedPage == SoftwareHomePage.AI_HOME

internal fun shouldProvideKiyoriSettingsTheme(route: KiyoriSettingsRoute?): Boolean =
    route != null && route != KiyoriSettingsRoute.HOME

internal fun shouldPresentKiyoriSettingsOverlay(state: KiyoriShellState): Boolean {
    // Settings 会话在文件管理 child 打开期间仍需保留，但不能继续组合在同一层级抢占绘制和 Back。
    // child 关闭后，原 route 与来源会话会从同一个 KiyoriShellState 直接恢复。
    if (state.child != null) {
        return false
    }
    val navigation = state.settingsNavigation ?: return false
    if (
        navigation.presentation == KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL ||
            navigation.presentation == KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_HOME ||
            navigation.presentation ==
            KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE
    ) {
        return false
    }
    return !(
        navigation.origin == KiyoriSettingsOrigin.BOTTOM_NAVIGATION &&
            navigation.presentation == KiyoriSettingsPresentation.PRIMARY_ROOT &&
            navigation.currentRoute == KiyoriSettingsRoute.HOME
    )
}

internal fun shouldAnimateKiyoriShellChildOverlay(state: KiyoriShellState): Boolean =
    state.child == KiyoriShellChild.FULL_SCREEN_WEB_SEARCH

internal fun shouldAcceptKiyoriHomePagerInput(
    state: KiyoriShellState,
    aiHostIsRoot: Boolean,
): Boolean =
    aiHostIsRoot &&
        state.primaryDestination == PrimaryDestination.SOFTWARE_HOME &&
        state.child == null &&
        state.settingsNavigation == null &&
        !state.isAiDrawerOpen &&
        !state.isBookmarkDrawerOpen &&
        !state.isHistoryDrawerOpen &&
        !state.isDownloadDrawerOpen

internal fun shouldEnableKiyoriAiHostSystemBack(
    state: KiyoriShellState,
    aiHostIsRoot: Boolean,
    settledPagerPage: SoftwareHomePage,
): Boolean =
    shouldAcceptKiyoriHomePagerInput(
        state = state,
        aiHostIsRoot = aiHostIsRoot,
    ) &&
        state.softwareHomePage == SoftwareHomePage.AI_HOME &&
        settledPagerPage == SoftwareHomePage.AI_HOME

internal fun calculateKiyoriAiHostTranslation(
    pageOffset: Float,
    viewportWidthPx: Float,
): Float = -pageOffset * viewportWidthPx

internal fun shouldReverseKiyoriPagerDrag(layoutDirection: LayoutDirection): Boolean =
    layoutDirection == LayoutDirection.Ltr

internal fun shouldPresentKiyoriDownloadDrawer(
    isDownloadDrawerOpen: Boolean,
    aiHostIsRoot: Boolean,
): Boolean {
    // The download drawer belongs to the Shell. The retained AI route may be a child screen while
    // Minus-One is visible, so AI route depth must never cancel or hide this explicit user action.
    return isDownloadDrawerOpen
}

internal fun shouldPresentKiyoriBookmarkDrawer(
    isBookmarkDrawerOpen: Boolean,
    aiHostIsRoot: Boolean,
): Boolean = isBookmarkDrawerOpen

internal fun shouldPresentKiyoriHistoryDrawer(
    isHistoryDrawerOpen: Boolean,
    aiHostIsRoot: Boolean,
): Boolean = isHistoryDrawerOpen

internal fun shouldEnableKiyoriShellBackHandler(
    aiHostIsRoot: Boolean,
    isAiDrawerOpen: Boolean,
    isBookmarkDrawerOpen: Boolean,
    isHistoryDrawerOpen: Boolean,
    isDownloadDrawerOpen: Boolean,
    settingsNavigation: KiyoriSettingsNavigationState?,
): Boolean =
    settingsNavigation == null &&
        (
            isBookmarkDrawerOpen ||
                isHistoryDrawerOpen ||
                isDownloadDrawerOpen ||
                (aiHostIsRoot && !isAiDrawerOpen)
        )

internal fun shouldEnableKiyoriSettingsHostBackHandler(
    settingsNavigation: KiyoriSettingsNavigationState?,
): Boolean = isKiyoriSettingsBackOwnedByShell(settingsNavigation)

/**
 * Browser Home stays composed behind Settings so its WebView and window state remain intact.
 *
 * Its BackHandlers must follow the visible Shell surface instead of callback registration age;
 * otherwise a Browser callback created after the long-lived Shell callbacks consumes Back through
 * the hidden webpage history. A suspended settings session intentionally hands ownership to the
 * Browser workspace until that workspace closes.
 */
internal fun shouldEnableKiyoriBrowserHostBackHandler(state: KiyoriShellState): Boolean {
    if (
        state.primaryDestination != PrimaryDestination.BROWSER_HOME ||
            state.child != null ||
            state.isAiDrawerOpen ||
            state.isBookmarkDrawerOpen ||
            state.isHistoryDrawerOpen ||
            state.isDownloadDrawerOpen
    ) {
        return false
    }
    return when (state.settingsNavigation?.presentation) {
        null,
        KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_WORKSPACE,
        KiyoriSettingsPresentation.SUSPENDED_FOR_BROWSER_HOME,
        -> true
        KiyoriSettingsPresentation.PRIMARY_ROOT,
        KiyoriSettingsPresentation.SOURCE_OVERLAY,
        KiyoriSettingsPresentation.OPERIT_ROUTE_DETAIL,
        -> false
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun calculateKiyoriPagerPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

internal fun resolveKiyoriBottomBarAlpha(
    state: KiyoriShellState,
    aiHostIsRoot: Boolean,
    centerPageOffset: Float,
): Float =
    when {
        !aiHostIsRoot ||
            state.child != null ||
            (
                state.settingsNavigation != null &&
                    !isKiyoriBottomNavigationSettingsHome(state)
            ) ||
            state.isAiDrawerOpen ||
            state.isBookmarkDrawerOpen ||
            state.isHistoryDrawerOpen ||
            state.isDownloadDrawerOpen ->
            0f
        state.primaryDestination == PrimaryDestination.SOFTWARE_HOME ->
            // The pager is the source of truth during a drag. Waiting for softwareHomePage to
            // settle would keep the navigation tree absent until the gesture has already ended.
            (1f - abs(centerPageOffset)).coerceIn(0f, 1f)
        !state.showsBottomBar -> 0f
        else -> 1f
    }

internal fun isKiyoriBottomNavigationSettingsHome(state: KiyoriShellState): Boolean {
    val navigation = state.settingsNavigation ?: return false
    return state.primaryDestination == PrimaryDestination.SETTINGS_HOME &&
        navigation.origin == KiyoriSettingsOrigin.BOTTOM_NAVIGATION &&
        navigation.presentation == KiyoriSettingsPresentation.PRIMARY_ROOT &&
        navigation.currentRoute == KiyoriSettingsRoute.HOME
}
