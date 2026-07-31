package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.standard.CookiePrivacyManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.DEFAULT_BROWSER_HOME_URL
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserHomeUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserscriptWorkbenchTab
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

internal enum class KiyoriBrowserSettingsAction {
    NONE,
    TOGGLE_USER_SCRIPTS_ALLOWED,
    OPEN_PLUGIN_CENTER,
    OPEN_USERSCRIPT_MANAGER,
    OPEN_PLUGIN_PERMISSIONS,
    OPEN_CURRENT_PAGE_PLUGIN_DIAGNOSTICS,
    OPEN_USERSCRIPT_LOGS,
    OPEN_HOME_CUSTOMIZATION,
    TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
    TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
    TOGGLE_WEB_PAGE_OPEN_APP,
    TOGGLE_WEB_PAGE_GEOLOCATION,
    CLEAR_COOKIES,
}

internal const val KIYORI_BROWSER_SETTINGS_PAGE_TITLE = "网页浏览器设置"

internal data class KiyoriBrowserSettingsEntrySpec(
    val title: String,
    val description: String,
    val kind: KiyoriSettingsRowKind,
    val value: String? = null,
    val staticToggleValue: Boolean = false,
    val action: KiyoriBrowserSettingsAction = KiyoriBrowserSettingsAction.NONE,
)

internal data class KiyoriBrowserSettingsGroupSpec(
    val title: String,
    val description: String,
    val entries: List<KiyoriBrowserSettingsEntrySpec>,
)

internal val kiyoriBrowserSettingsGroups =
    listOf(
        KiyoriBrowserSettingsGroupSpec(
            title = "网页插件与脚本",
            description = "管理用户脚本运行授权、声明权限、网站范围和当前页诊断",
            entries =
                listOf(
                    browserToggle(
                        title = "允许用户脚本",
                        description = "允许已启用脚本在匹配网页中运行；关闭后保留脚本和启用状态",
                        staticToggleValue = false,
                        action = KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED,
                    ),
                    browserNavigation(
                        title = "插件中心",
                        description = "查看当前网页中的插件提供者和已安装插件",
                        action = KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER,
                    ),
                    browserNavigation(
                        title = "油猴脚本管理",
                        description = "安装、更新、编辑、启停或删除用户脚本",
                        action = KiyoriBrowserSettingsAction.OPEN_USERSCRIPT_MANAGER,
                    ),
                    browserNavigation(
                        title = "插件权限与网站范围",
                        description = "查看每个脚本声明的 GM 权限、联网范围和页面规则",
                        action = KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS,
                    ),
                    browserNavigation(
                        title = "当前页脚本诊断",
                        description = "查看当前网页中的命中、执行、异常和排除规则",
                        action = KiyoriBrowserSettingsAction.OPEN_CURRENT_PAGE_PLUGIN_DIAGNOSTICS,
                    ),
                    browserNavigation(
                        title = "脚本日志",
                        description = "查看、复制或导出用户脚本运行日志",
                        action = KiyoriBrowserSettingsAction.OPEN_USERSCRIPT_LOGS,
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "主页、标签与手势",
            description = "调整主页入口、标签展示和浏览器导航手势",
            entries =
                listOf(
                    browserNavigation(
                        title = "网页主页自定义",
                        description = "设置浏览器主页按钮和新会话使用的入口地址",
                        action = KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION,
                    ),
                    browserNavigation(
                        title = "标签栏样式",
                        description = "选择网页标签在浏览器中的展示方式",
                        value = "图文卡片",
                    ),
                    browserToggle(
                        title = "返回不重载",
                        description = "返回标签时保留页面状态，避免重新加载",
                        staticToggleValue = false,
                    ),
                    browserNavigation(
                        title = "启动时恢复标签",
                        description = "选择启动浏览器时恢复标签的策略",
                        value = "不恢复",
                    ),
                    browserToggle(
                        title = "手势前进后退",
                        description = "在网页内容区通过横向手势切换历史记录",
                        staticToggleValue = true,
                    ),
                    browserToggle(
                        title = "底部上滑手势",
                        description = "从浏览器底部上滑打开快捷操作",
                        staticToggleValue = true,
                    ),
                    browserToggle(
                        title = "搜索引擎切换条",
                        description = "文本搜索后显示可横向切换的搜索引擎条",
                        staticToggleValue = true,
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "音视频嗅探",
            description = "控制视频资源入口、自动悬浮播放和候选识别策略",
            entries =
                listOf(
                    browserToggle(
                        title = "搜索栏嗅探入口",
                        description = "发现可播放视频后，在搜索栏右侧显示视频资源球",
                        staticToggleValue = true,
                        action = KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
                    ),
                    browserToggle(
                        title = "自动悬浮播放",
                        description = "发现推荐视频后自动打开浏览器悬浮播放器",
                        staticToggleValue = true,
                        action =
                            KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
                    ),
                    browserNavigation(
                        title = "悬浮嗅探模式",
                        description = "配置候选视频的自动识别与展示策略",
                    ),
                    browserNavigation(
                        title = "嗅探规则管理",
                        description = "管理视频格式、地址与站点识别规则",
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "网站权限与数据",
            description = "管理网页调用外部能力、位置服务和站点数据",
            entries =
                listOf(
                    browserToggle(
                        title = "允许网页打开应用",
                        description = "允许网页通过外部链接唤起已安装应用",
                        staticToggleValue = true,
                        action = KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP,
                    ),
                    browserToggle(
                        title = "允许网页获取位置",
                        description = "允许网页在系统授权后请求设备位置",
                        staticToggleValue = true,
                        action = KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION,
                    ),
                    browserNavigation(
                        title = "清除网站 Cookie",
                        description = "清除搜索工具、网页访问和内置浏览器保存的普通网站 Cookie",
                        action = KiyoriBrowserSettingsAction.CLEAR_COOKIES,
                    ),
                    browserNavigation(
                        title = "网页翻译接口",
                        description = "选择网页翻译请求使用的服务",
                        value = "百度翻译",
                    ),
                    browserNavigation(
                        title = "网站配置管理",
                        description = "按站点查看和管理浏览器配置",
                    ),
                    browserNavigation(
                        title = "网站密码管理",
                        description = "查看浏览器保存的网站登录信息",
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "显示与高级",
            description = "调整网页显示、User-Agent、代理和调试能力",
            entries =
                listOf(
                    browserNavigation(
                        title = "网页字体大小",
                        description = "调整网页内容的默认文字缩放比例",
                    ),
                    browserToggle(
                        title = "强制页面缩放",
                        description = "允许缩放网页明确禁止缩放的页面",
                        staticToggleValue = false,
                    ),
                    browserNavigation(
                        title = "腾讯 X5 调试",
                        description = "查看腾讯 X5 内核的调试与诊断入口",
                    ),
                    browserNavigation(
                        title = "User-Agent 设置",
                        description = "设置全局或指定网站使用的浏览器标识",
                        value = "浏览器内设置",
                    ),
                    browserNavigation(
                        title = "浏览器代理",
                        description = "管理网页请求使用的网络代理",
                    ),
                    browserToggle(
                        title = "强制新窗口打开",
                        description = "将网页弹出的新窗口固定为独立标签",
                        staticToggleValue = false,
                    ),
                ),
        ),
    )

private fun browserNavigation(
    title: String,
    description: String,
    value: String? = null,
    action: KiyoriBrowserSettingsAction = KiyoriBrowserSettingsAction.NONE,
): KiyoriBrowserSettingsEntrySpec =
    KiyoriBrowserSettingsEntrySpec(
        title = title,
        description = description,
        kind = KiyoriSettingsRowKind.NAVIGATION,
        value = value,
        action = action,
    )

private fun browserToggle(
    title: String,
    description: String,
    staticToggleValue: Boolean,
    action: KiyoriBrowserSettingsAction = KiyoriBrowserSettingsAction.NONE,
): KiyoriBrowserSettingsEntrySpec =
    KiyoriBrowserSettingsEntrySpec(
        title = title,
        description = description,
        kind = KiyoriSettingsRowKind.TOGGLE,
        staticToggleValue = staticToggleValue,
        action = action,
    )

private enum class KiyoriBrowserSettingsSubPage {
    HOME_CUSTOMIZATION,
    PLUGIN_PERMISSIONS,
}

@Composable
internal fun KiyoriBrowserSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coordinator =
        remember(context) {
            BrowserPresentationCoordinator.getInstance(context.applicationContext)
        }
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settings by coordinator.browserSettings.collectAsState()
    val userscriptState by coordinator.userscriptState.collectAsState()
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    var subPageName by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearCookieConfirm by rememberSaveable { mutableStateOf(false) }
    val subPage = subPageName?.let(KiyoriBrowserSettingsSubPage::valueOf)

    fun closeCurrentPage() {
        if (subPage == null) {
            onBack()
        } else {
            subPageName = null
        }
    }

    BackHandler(onBack = ::closeCurrentPage)

    when (subPage) {
        null ->
            KiyoriBrowserSettingsDetailPage(
                settings = settings,
                userscriptState = userscriptState,
                onBack = ::closeCurrentPage,
                onSetUserScriptsAllowed = coordinator::setUserScriptsAllowed,
                onOpenPluginCenter = coordinator::openPluginCenter,
                onOpenUserscriptManager = {
                    coordinator.openUserscriptManager(WebSessionUserscriptWorkbenchTab.INSTALLED)
                },
                onOpenPluginPermissions = {
                    subPageName = KiyoriBrowserSettingsSubPage.PLUGIN_PERMISSIONS.name
                },
                onOpenCurrentPagePluginDiagnostics = {
                    coordinator.openUserscriptManager(WebSessionUserscriptWorkbenchTab.CURRENT_PAGE)
                },
                onOpenUserscriptLogs = {
                    coordinator.openUserscriptManager(WebSessionUserscriptWorkbenchTab.LOGS)
                },
                onOpenHomeCustomization = {
                    subPageName = KiyoriBrowserSettingsSubPage.HOME_CUSTOMIZATION.name
                },
                onSetShowMediaCandidateBadge = coordinator::setShowMediaCandidateBadge,
                onSetAutomaticFloatingPlaybackEnabled =
                    coordinator::setAutomaticFloatingPlaybackEnabled,
                onSetAllowWebPageOpenApp = coordinator::setAllowWebPageOpenApp,
                onSetAllowWebPageGeolocation = coordinator::setAllowWebPageGeolocation,
                onClearCookies = { showClearCookieConfirm = true },
                modifier = modifier,
            )
        KiyoriBrowserSettingsSubPage.HOME_CUSTOMIZATION ->
            KiyoriBrowserHomepageCustomizationPage(
                currentHomeUrl = settings.homeUrl,
                onBack = ::closeCurrentPage,
                onSave = { value ->
                    val resolvedUrl = BrowserAddressResolver.resolve(value, searchEngine)
                    if (!isSupportedBrowserHomeUrl(resolvedUrl)) {
                        Toast.makeText(
                            context,
                            "自定义主页入口格式无效",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@KiyoriBrowserHomepageCustomizationPage false
                    }
                    coordinator.setBrowserHomeUrl(resolvedUrl)
                    Toast.makeText(
                        context,
                        "自定义主页入口已保存",
                        Toast.LENGTH_SHORT,
                    ).show()
                    true
                },
                onReset = {
                    coordinator.setBrowserHomeUrl(DEFAULT_BROWSER_HOME_URL)
                    Toast.makeText(
                        context,
                        "已恢复为空白页",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = modifier,
            )
        KiyoriBrowserSettingsSubPage.PLUGIN_PERMISSIONS ->
            KiyoriBrowserPluginPermissionsPage(
                state = userscriptState,
                onBack = ::closeCurrentPage,
                onSetUserScriptsAllowed = coordinator::setUserScriptsAllowed,
                onOpenPluginCenter = coordinator::openPluginCenter,
                onOpenUserscriptManager = {
                    coordinator.openUserscriptManager(WebSessionUserscriptWorkbenchTab.INSTALLED)
                },
                onOpenCurrentPagePluginDiagnostics = {
                    coordinator.openUserscriptManager(WebSessionUserscriptWorkbenchTab.CURRENT_PAGE)
                },
                onOpenUserscriptLogs = {
                    coordinator.openUserscriptManager(WebSessionUserscriptWorkbenchTab.LOGS)
                },
                onOpenUserscriptDetail = coordinator::openUserscriptDetail,
                modifier = modifier,
            )
    }

    if (showClearCookieConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCookieConfirm = false },
            title = { Text(stringResource(R.string.clear_cookies_dialog_title)) },
            text = { Text(stringResource(R.string.clear_cookies_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCookieConfirm = false
                        scope.launch {
                            try {
                                CookiePrivacyManager.clearAllCookies()
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.clear_cookies_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } catch (error: Exception) {
                                AppLogger.e(
                                    "KiyoriBrowserSettings",
                                    "Failed to clear cookies",
                                    error,
                                )
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.clear_cookies_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.clear_cookies_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookieConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun KiyoriBrowserSettingsDetailPage(
    settings: WebSessionBrowserSettings,
    userscriptState: WebSessionUserscriptUiState,
    onBack: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onOpenPluginCenter: () -> Unit,
    onOpenUserscriptManager: () -> Unit,
    onOpenPluginPermissions: () -> Unit,
    onOpenCurrentPagePluginDiagnostics: () -> Unit,
    onOpenUserscriptLogs: () -> Unit,
    onOpenHomeCustomization: () -> Unit,
    onSetShowMediaCandidateBadge: (Boolean) -> Unit,
    onSetAutomaticFloatingPlaybackEnabled: (Boolean) -> Unit,
    onSetAllowWebPageOpenApp: (Boolean) -> Unit,
    onSetAllowWebPageGeolocation: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriCollapsingSettingsPage(
        title = KIYORI_BROWSER_SETTINGS_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        items(kiyoriBrowserSettingsGroups, key = KiyoriBrowserSettingsGroupSpec::title) { group ->
            KiyoriSettingsGroupSection(
                title = group.title,
                description = group.description,
            ) {
                group.entries.forEachIndexed { index, entry ->
                    val enabled =
                        isBrowserSettingEnabled(entry) &&
                            isBrowserSettingRuntimeEnabled(entry, userscriptState)
                    val checked =
                        browserSettingToggleValue(
                            entry = entry,
                            settings = settings,
                            userscriptState = userscriptState,
                        )
                    KiyoriSettingsRow(
                        title = entry.title,
                        description = entry.description,
                        kind = entry.kind,
                        value =
                            browserSettingValue(
                                entry = entry,
                                settings = settings,
                                userscriptState = userscriptState,
                            ),
                        checked = checked,
                        enabled = enabled,
                        onClick = {
                            when (entry.action) {
                                KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED ->
                                    onSetUserScriptsAllowed(!checked)
                                KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER ->
                                    onOpenPluginCenter()
                                KiyoriBrowserSettingsAction.OPEN_USERSCRIPT_MANAGER ->
                                    onOpenUserscriptManager()
                                KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS ->
                                    onOpenPluginPermissions()
                                KiyoriBrowserSettingsAction.OPEN_CURRENT_PAGE_PLUGIN_DIAGNOSTICS ->
                                    onOpenCurrentPagePluginDiagnostics()
                                KiyoriBrowserSettingsAction.OPEN_USERSCRIPT_LOGS ->
                                    onOpenUserscriptLogs()
                                KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION ->
                                    onOpenHomeCustomization()
                                KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY ->
                                    onSetShowMediaCandidateBadge(!checked)
                                KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK ->
                                    onSetAutomaticFloatingPlaybackEnabled(!checked)
                                KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP ->
                                    onSetAllowWebPageOpenApp(!checked)
                                KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION ->
                                    onSetAllowWebPageGeolocation(!checked)
                                KiyoriBrowserSettingsAction.CLEAR_COOKIES ->
                                    onClearCookies()
                                KiyoriBrowserSettingsAction.NONE ->
                                    error("Disabled browser setting must not receive clicks")
                            }
                        },
                    )
                    if (index != group.entries.lastIndex) {
                        KiyoriSettingsDivider()
                    }
                }
            }
        }
    }
}

internal fun isBrowserSettingEnabled(entry: KiyoriBrowserSettingsEntrySpec): Boolean =
    entry.action != KiyoriBrowserSettingsAction.NONE

internal fun isBrowserSettingRuntimeEnabled(
    entry: KiyoriBrowserSettingsEntrySpec,
    userscriptState: WebSessionUserscriptUiState,
): Boolean =
    when (entry.action) {
        KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED ->
            userscriptState.supportState.isSupported
        else -> true
    }

internal fun browserSettingValue(
    entry: KiyoriBrowserSettingsEntrySpec,
    settings: WebSessionBrowserSettings,
    userscriptState: WebSessionUserscriptUiState = WebSessionUserscriptUiState(),
): String? =
    if (entry.kind != KiyoriSettingsRowKind.NAVIGATION) {
        null
    } else {
        when (entry.action) {
            KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER ->
                browserPluginCenterSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_USERSCRIPT_MANAGER ->
                browserPluginManagementSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS ->
                browserPluginPermissionSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_CURRENT_PAGE_PLUGIN_DIAGNOSTICS ->
                browserPluginCurrentPageSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_USERSCRIPT_LOGS ->
                browserPluginLogSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION ->
                formatBrowserHomeUrl(settings.homeUrl)
            KiyoriBrowserSettingsAction.CLEAR_COOKIES -> null
            KiyoriBrowserSettingsAction.NONE -> entry.value ?: "未接入"
            else -> entry.value
        }
    }

private fun browserSettingToggleValue(
    entry: KiyoriBrowserSettingsEntrySpec,
    settings: WebSessionBrowserSettings,
    userscriptState: WebSessionUserscriptUiState,
): Boolean =
    when (entry.action) {
        KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED ->
            userscriptState.userScriptsAllowed
        KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY ->
            settings.showMediaCandidateBadge
        KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK ->
            settings.automaticFloatingPlaybackEnabled
        KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP ->
            settings.allowWebPageOpenApp
        KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION ->
            settings.allowWebPageGeolocation
        else -> entry.staticToggleValue
    }

internal fun browserPluginManagementSummary(state: WebSessionUserscriptUiState): String =
    "已安装 ${state.installedScripts.size} · 已启用 " +
        state.installedScripts.count(UserscriptListItem::enabled)

internal fun browserPluginCenterSummary(state: WebSessionUserscriptUiState): String =
    "1 个插件 · ${state.installedScripts.size} 个脚本"

internal fun browserPluginLogSummary(state: WebSessionUserscriptUiState): String =
    "${state.recentLogs.size} 条保留日志"

internal fun browserPluginPermissionSummary(state: WebSessionUserscriptUiState): String {
    if (state.installedScripts.isEmpty()) {
        return "暂无已安装脚本"
    }
    val grantCount = state.installedScripts.sumOf { script -> script.grants.distinct().size }
    val siteCount =
        state.installedScripts.sumOf { script ->
            (script.matches + script.includes + script.connects).distinct().size
        }
    return "${state.installedScripts.size} 个脚本 · $grantCount 项权限 · $siteCount 个范围"
}

internal fun browserPluginCurrentPageSummary(state: WebSessionUserscriptUiState): String {
    if (!state.supportState.isSupported) {
        return "运行环境不可用"
    }
    if (!state.userScriptsAllowed) {
        return "用户脚本未授权"
    }
    if (state.currentPageUrl.isNullOrBlank() || state.currentPageUrl == "about:blank") {
        return "无活动页面"
    }
    val statuses = state.currentPageStatuses.values
    val errorCount = statuses.count { status -> status.state == UserscriptPageRuntimeState.ERROR }
    if (errorCount > 0) {
        return "$errorCount 个异常"
    }
    val activeCount =
        statuses.count { status ->
            status.state in
                setOf(
                    UserscriptPageRuntimeState.MATCHED,
                    UserscriptPageRuntimeState.QUEUED,
                    UserscriptPageRuntimeState.RUNNING,
                    UserscriptPageRuntimeState.SUCCESS,
                )
        }
    val notMatchedCount =
        statuses.count { status -> status.state == UserscriptPageRuntimeState.NOT_MATCHED }
    return when {
        activeCount > 0 && notMatchedCount > 0 ->
            "$activeCount 个命中 · $notMatchedCount 个未命中"
        activeCount > 0 -> "$activeCount 个命中"
        notMatchedCount > 0 -> "$notMatchedCount 个未命中"
        else -> "当前页无脚本状态"
    }
}

internal fun browserPluginScriptPermissionDescription(script: UserscriptListItem): String {
    val executionWorld =
        when (script.executionWorld) {
            UserscriptExecutionWorld.PAGE -> "页面世界"
            UserscriptExecutionWorld.ISOLATED -> "隔离世界"
            null -> "执行世界未确定"
        }
    val pageRules =
        (script.matches + script.includes + script.excludes + script.excludeMatches)
            .distinct()
    val connectRules = script.connects.distinct()
    return buildList {
        add(executionWorld)
        add("${script.grants.distinct().size} 项权限")
        add(
            if (pageRules.isEmpty()) {
                "页面：全部页面"
            } else {
                "页面：" + summarizeBrowserPluginRules(pageRules)
            },
        )
        add(
            if (connectRules.isEmpty()) {
                "联网：仅页面同源"
            } else {
                "联网：" + summarizeBrowserPluginRules(connectRules)
            },
        )
        if (script.unknownGrants.isNotEmpty()) {
            add("${script.unknownGrants.size} 项未知权限")
        }
        if (script.blockedReasons.isNotEmpty()) {
            add("${script.blockedReasons.size} 项阻塞")
        }
    }.joinToString(" · ")
}

private fun summarizeBrowserPluginRules(rules: List<String>): String {
    val visible = rules.take(2).joinToString("、")
    return if (rules.size > 2) {
        "$visible 等 ${rules.size} 项"
    } else {
        visible
    }
}

internal fun formatBrowserHomeUrl(url: String): String =
    if (url.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true)) "空白页" else url

@Composable
private fun KiyoriBrowserPluginPermissionsPage(
    state: WebSessionUserscriptUiState,
    onBack: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onOpenPluginCenter: () -> Unit,
    onOpenUserscriptManager: () -> Unit,
    onOpenCurrentPagePluginDiagnostics: () -> Unit,
    onOpenUserscriptLogs: () -> Unit,
    onOpenUserscriptDetail: (Long) -> Unit,
    modifier: Modifier,
) {
    KiyoriCollapsingSettingsPage(
        title = "插件权限与网站范围",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            KiyoriSettingsGroupSection(
                title = "运行授权",
                description = "总授权由 userscript registry 唯一持有；关闭后不会删除脚本或修改启用状态",
            ) {
                KiyoriSettingsRow(
                    title = "允许用户脚本",
                    description = "允许已启用脚本在匹配网页中运行",
                    kind = KiyoriSettingsRowKind.TOGGLE,
                    checked = state.userScriptsAllowed,
                    enabled = state.supportState.isSupported,
                    onClick = { onSetUserScriptsAllowed(!state.userScriptsAllowed) },
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "运行环境",
                    description =
                        state.supportState.reason
                            ?: "当前 Android System WebView 支持用户脚本运行时",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = if (state.supportState.isSupported) "可用" else "不可用",
                    enabled = false,
                    onClick = {},
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "当前页脚本诊断",
                    description = "查看命中规则、执行状态、错误堆栈和页面菜单",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = browserPluginCurrentPageSummary(state),
                    onClick = onOpenCurrentPagePluginDiagnostics,
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "脚本日志",
                    description = "查看、复制或导出当前保留的全部用户脚本日志",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = browserPluginLogSummary(state),
                    onClick = onOpenUserscriptLogs,
                )
            }
        }
        item {
            KiyoriSettingsGroupSection(
                title = "插件管理",
                description = "权限由脚本 metadata 声明；安装或更新时必须经过权限和网站范围审查",
            ) {
                KiyoriSettingsRow(
                    title = "插件中心",
                    description = "查看当前网页中的插件提供者和已安装插件",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = browserPluginCenterSummary(state),
                    onClick = onOpenPluginCenter,
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "油猴脚本管理",
                    description = "安装、更新、编辑、启停或删除用户脚本",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = browserPluginManagementSummary(state),
                    onClick = onOpenUserscriptManager,
                )
            }
        }
        item {
            KiyoriSettingsGroupSection(
                title = "脚本声明权限与网站",
                description =
                    if (state.installedScripts.isEmpty()) {
                        "尚未安装网页脚本"
                    } else {
                        "点击脚本进入完整管理页；这里不把脚本声明权限伪装成独立授权开关"
                    },
            ) {
                if (state.installedScripts.isEmpty()) {
                    KiyoriSettingsRow(
                        title = "暂无已安装脚本",
                        description = "可从油猴脚本管理右上角加号通过 URL、本地文件或插件库安装",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        value = "0",
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    state.installedScripts.forEachIndexed { index, script ->
                        KiyoriSettingsRow(
                            title = script.name,
                            description = browserPluginScriptPermissionDescription(script),
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            value =
                                when {
                                    script.blockedReasons.isNotEmpty() -> "不可运行"
                                    script.unknownGrants.isNotEmpty() -> "需审查"
                                    script.enabled -> "已启用"
                                    else -> "已停用"
                                },
                            onClick = { onOpenUserscriptDetail(script.id) },
                        )
                        if (index != state.installedScripts.lastIndex) {
                            KiyoriSettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriBrowserHomepageCustomizationPage(
    currentHomeUrl: String,
    onBack: () -> Unit,
    onSave: (String) -> Boolean,
    onReset: () -> Unit,
    modifier: Modifier,
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }

    KiyoriCollapsingSettingsPage(
        title = "网页主页自定义",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            KiyoriSettingsGroupSection(
                title = "主页入口",
                description = "浏览器主页按钮和新会话会使用这里保存的地址",
            ) {
                KiyoriSettingsRow(
                    title = "当前主页",
                    description = "支持完整 HTTP/HTTPS 地址或 about:blank 空白页",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = formatBrowserHomeUrl(currentHomeUrl),
                    onClick = { showEditDialog = true },
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "恢复为空白页",
                    description = "清除自定义主页，并将入口恢复为 about:blank",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    value = "about:blank",
                    onClick = onReset,
                )
            }
        }
    }

    if (showEditDialog) {
        KiyoriBrowserHomepageEditDialog(
            initialValue = currentHomeUrl,
            onDismiss = { showEditDialog = false },
            onConfirm = { value ->
                if (onSave(value)) {
                    showEditDialog = false
                }
            },
        )
    }
}

@Composable
private fun KiyoriBrowserHomepageEditDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var inputValue by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "自定义主页入口",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
        },
        text = {
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                label = { Text("入口地址") },
                placeholder = { Text("输入网址或 about:blank") },
                supportingText = {
                    Text("支持完整 HTTP/HTTPS 地址，也可以填写 about:blank。")
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(inputValue.trim()) }) {
                Text(
                    text = "保存",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        shape = RoundedCornerShape(22.dp),
    )
}
