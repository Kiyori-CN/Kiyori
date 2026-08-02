package com.kiyori.app.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.startup.screens.LocalPluginLoadingState
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingState
import com.kiyori.app.KiyoriApp
import com.kiyori.app.shell.KiyoriShellExternalDestination

/**
 * MainActivity 当前一次内容组合所需的请求参数投影。
 *
 * 该值只把唯一 [KiyoriMainPendingRequests] 的当前事实映射为 [KiyoriApp] 参数；它不缓存、
 * 持久化或清除任何请求。保持投影无状态可避免在 Activity 与 App root 之间形成第二 owner。
 */
internal data class KiyoriMainContentRequestProjection(
    val initialNavItem: NavItem,
    val shortcutNavRequest: NavItem?,
    val shortcutNavRequestId: Long,
    val routeNavRequest: String?,
    val routeNavArgs: Map<String, Any?>,
    val routeNavRequestId: Long,
    val browserOpenRequest: String?,
    val browserOpenRequestId: Long,
    val kiyoriShellDestinationRequest: KiyoriShellExternalDestination?,
    val kiyoriShellRequestId: Long,
)

internal fun projectKiyoriMainContentRequests(
    pendingRequests: KiyoriMainPendingRequests,
): KiyoriMainContentRequestProjection =
    KiyoriMainContentRequestProjection(
        initialNavItem = pendingRequests.shortcutNavItem ?: pendingRequests.currentMainNavItem,
        shortcutNavRequest = pendingRequests.shortcutNavItem,
        shortcutNavRequestId = pendingRequests.shortcutRequestId,
        routeNavRequest = pendingRequests.routeId,
        routeNavArgs = pendingRequests.routeArgs,
        routeNavRequestId = pendingRequests.routeRequestId,
        browserOpenRequest = pendingRequests.browserUrl,
        browserOpenRequestId = pendingRequests.browserRequestId,
        kiyoriShellDestinationRequest = pendingRequests.shellDestination,
        kiyoriShellRequestId = pendingRequests.shellRequestId,
    )

/**
 * 装配 MainActivity 的唯一 Kiyori 内容根。
 *
 * pending 内容必须先按原顺序转交，再读取 request projection；否则首次分享或外部导航可能
 * 与旧 Activity 实现产生不同的消费时机。插件状态仍由 Activity 持有，本 host 只提供同一实例。
 */
@Composable
internal fun KiyoriMainContentHost(
    pendingRequests: KiyoriMainPendingRequests,
    sharedContentCoordinator: KiyoriMainSharedContentCoordinator,
    pluginLoadingState: PluginLoadingState,
) {
    sharedContentCoordinator.processPendingSharedFiles()
    sharedContentCoordinator.processPendingSharedText()
    val contentRequests = projectKiyoriMainContentRequests(pendingRequests)

    CompositionLocalProvider(
        LocalPluginLoadingState provides pluginLoadingState,
    ) {
        KiyoriApp(
            initialNavItem = contentRequests.initialNavItem,
            shortcutNavRequest = contentRequests.shortcutNavRequest,
            shortcutNavRequestId = contentRequests.shortcutNavRequestId,
            routeNavRequest = contentRequests.routeNavRequest,
            routeNavArgs = contentRequests.routeNavArgs,
            routeNavRequestId = contentRequests.routeNavRequestId,
            browserOpenRequest = contentRequests.browserOpenRequest,
            browserOpenRequestId = contentRequests.browserOpenRequestId,
            kiyoriShellDestinationRequest = contentRequests.kiyoriShellDestinationRequest,
            kiyoriShellRequestId = contentRequests.kiyoriShellRequestId,
            onShortcutNavHandled = { handledRequestId ->
                pendingRequests.consumeShortcut(handledRequestId)
            },
            onCurrentNavItemChanged = { navItem ->
                pendingRequests.updateCurrentMainNavItem(navItem)
            },
            onRouteNavHandled = { handledRequestId ->
                pendingRequests.consumeRoute(handledRequestId)
            },
            onBrowserOpenHandled = { handledRequestId ->
                pendingRequests.consumeBrowser(handledRequestId)
            },
            onKiyoriShellRequestHandled = { handledRequestId ->
                pendingRequests.consumeShell(handledRequestId)
            },
        )
    }
}
