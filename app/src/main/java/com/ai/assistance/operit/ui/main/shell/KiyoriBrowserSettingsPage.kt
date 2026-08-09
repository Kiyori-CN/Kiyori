package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.standard.CookiePrivacyManager
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.DEFAULT_BROWSER_HOME_URL
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.AUTOMATIC_FLOATING_MINIMUM_DURATION_OPTIONS_MILLIS
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserCredentialVaultSnapshot
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatAutomaticFloatingMinimumDuration
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.formatWebTextZoomPercent
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserHomeUrl
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.parseAutomaticFloatingDurationSeconds
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptExecutionWorld
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptListItem
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.UserscriptPageRuntimeState
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.isUserscriptRuntimePermissionActionEnabled
import com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.ui.WebSessionUserscriptUiState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionUserscriptWorkbenchTab
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

internal enum class KiyoriBrowserSettingsAction {
    TOGGLE_USER_SCRIPTS_ALLOWED,
    OPEN_PLUGIN_CENTER,
    OPEN_PLUGIN_PERMISSIONS,
    OPEN_PLUGIN_DIAGNOSTICS,
    OPEN_HOME_CUSTOMIZATION,
    TOGGLE_RETURN_WITHOUT_RELOAD,
    TOGGLE_FORCE_PAGE_ZOOM,
    OPEN_WEB_TEXT_SIZE,
    TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
    TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
    SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION,
    TOGGLE_WEB_PAGE_OPEN_APP,
    TOGGLE_WEB_PAGE_GEOLOCATION,
    OPEN_PASSWORD_MANAGER,
    CLEAR_COOKIES,
}

internal const val KIYORI_BROWSER_SETTINGS_PAGE_TITLE = "网页浏览器设置"

internal data class KiyoriBrowserSettingsEntrySpec(
    val title: String,
    val description: String,
    val kind: KiyoriSettingsRowKind,
    val action: KiyoriBrowserSettingsAction,
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
            description = "管理网页插件、用户脚本授权、声明权限和运行诊断",
            entries =
                listOf(
                    browserToggle(
                        title = "允许用户脚本",
                        description = "允许已启用脚本在匹配网页中运行；关闭后保留脚本和启用状态",
                        action = KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED,
                    ),
                    browserNavigation(
                        title = "插件中心",
                        description = "查看当前网页中的插件提供者和已安装插件",
                        action = KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER,
                    ),
                    browserNavigation(
                        title = "插件权限与网站范围",
                        description = "查看每个脚本声明的 GM 权限、联网范围和页面规则",
                        action = KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS,
                    ),
                    browserNavigation(
                        title = "脚本诊断与日志",
                        description = "查看当前页命中、执行异常、排除规则和保留日志",
                        action = KiyoriBrowserSettingsAction.OPEN_PLUGIN_DIAGNOSTICS,
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "主页与导航",
            description = "管理主页入口与网页历史返回时的加载方式",
            entries =
                listOf(
                    browserNavigation(
                        title = "网页主页自定义",
                        description = "设置浏览器主页按钮和新会话使用的入口地址",
                        action = KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION,
                    ),
                    browserToggle(
                        title = "返回不重载",
                        description = "网页后退时使用历史缓存，减少重新请求和页面状态丢失",
                        action = KiyoriBrowserSettingsAction.TOGGLE_RETURN_WITHOUT_RELOAD,
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "网页显示",
            description = "控制网页缩放限制和站点正文的显示比例",
            entries =
                listOf(
                    browserToggle(
                        title = "强制页面缩放",
                        description = "忽略网页禁止缩放声明，始终允许双指缩放页面",
                        action = KiyoriBrowserSettingsAction.TOGGLE_FORCE_PAGE_ZOOM,
                    ),
                    browserNavigation(
                        title = "网页文字大小",
                        description = "调整所有网页正文的文字缩放比例并实时预览",
                        action = KiyoriBrowserSettingsAction.OPEN_WEB_TEXT_SIZE,
                    ),
                ),
        ),
        KiyoriBrowserSettingsGroupSpec(
            title = "网站权限与数据",
            description = "管理网页外部能力、网站凭据和普通浏览数据",
            entries =
                listOf(
                    browserToggle(
                        title = "允许网页打开应用",
                        description = "允许网页通过外部链接唤起已安装应用",
                        action = KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP,
                    ),
                    browserToggle(
                        title = "允许网页获取位置",
                        description = "允许网页在系统授权后请求设备位置",
                        action = KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION,
                    ),
                    browserNavigation(
                        title = "网站密码管理",
                        description = "管理普通窗口中安全保存并自动填充的网站账号密码",
                        action = KiyoriBrowserSettingsAction.OPEN_PASSWORD_MANAGER,
                    ),
                    browserNavigation(
                        title = "清除网站 Cookie",
                        description = "清除搜索工具、网页访问和内置浏览器保存的普通网站 Cookie",
                        action = KiyoriBrowserSettingsAction.CLEAR_COOKIES,
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
                        action = KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
                    ),
                    browserToggle(
                        title = "自动悬浮播放",
                        description = "发现推荐视频后自动打开浏览器悬浮播放器",
                        action =
                            KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
                    ),
                    browserNavigation(
                        title = "自动悬浮最小时长",
                        description = "短于该时长的视频不会自动打开小窗；直播不受此限制",
                        action =
                            KiyoriBrowserSettingsAction
                                .SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION,
                    ),
                ),
        ),
    )

private fun browserNavigation(
    title: String,
    description: String,
    action: KiyoriBrowserSettingsAction,
): KiyoriBrowserSettingsEntrySpec =
    KiyoriBrowserSettingsEntrySpec(
        title = title,
        description = description,
        kind = KiyoriSettingsRowKind.NAVIGATION,
        action = action,
    )

private fun browserToggle(
    title: String,
    description: String,
    action: KiyoriBrowserSettingsAction,
): KiyoriBrowserSettingsEntrySpec =
    KiyoriBrowserSettingsEntrySpec(
        title = title,
        description = description,
        kind = KiyoriSettingsRowKind.TOGGLE,
        action = action,
    )

private enum class KiyoriBrowserSettingsSubPage {
    HOME_CUSTOMIZATION,
    PLUGIN_PERMISSIONS,
    WEB_TEXT_SIZE,
    PASSWORD_MANAGER,
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
    val credentialVaultState by coordinator.browserCredentialVaultState.collectAsState()
    val userscriptState by coordinator.userscriptState.collectAsState()
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    var subPageName by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearCookieConfirm by rememberSaveable { mutableStateOf(false) }
    var settingsSelection by remember { mutableStateOf<KiyoriSettingsSelection?>(null) }
    var showCustomFloatingDurationDialog by rememberSaveable { mutableStateOf(false) }
    var customFloatingDurationSeconds by rememberSaveable { mutableStateOf("60") }
    val subPage = subPageName?.let(KiyoriBrowserSettingsSubPage::valueOf)

    fun closeCurrentPage() {
        if (subPage == null) {
            onBack()
        } else {
            subPageName = null
        }
    }

    fun openBrowserPluginRoute(openRoute: () -> Unit) {
        // Browser Settings 可能覆盖在仍挂载的 Browser Home 上。必须先关闭 Shell child，
        // 否则插件路由只会在设置页背后切换，用户看不到当前标签页和目标抽屉。
        runBrowserPluginRouteFromSettings(
            onCloseSettings = onBack,
            onOpenRoute = openRoute,
        )
    }

    BackHandler(onBack = ::closeCurrentPage)

    when (subPage) {
        null ->
            KiyoriBrowserSettingsDetailPage(
                settings = settings,
                userscriptState = userscriptState,
                onBack = ::closeCurrentPage,
                onSetUserScriptsAllowed = coordinator::setUserScriptsAllowed,
                onOpenPluginCenter = {
                    openBrowserPluginRoute(coordinator::openPluginCenter)
                },
                onOpenPluginPermissions = {
                    subPageName = KiyoriBrowserSettingsSubPage.PLUGIN_PERMISSIONS.name
                },
                onOpenPluginDiagnostics = {
                    openBrowserPluginRoute {
                        coordinator.openUserscriptManager(
                            WebSessionUserscriptWorkbenchTab.CURRENT_PAGE,
                        )
                    }
                },
                onOpenHomeCustomization = {
                    subPageName = KiyoriBrowserSettingsSubPage.HOME_CUSTOMIZATION.name
                },
                onSetReturnWithoutReloadEnabled =
                    coordinator::setReturnWithoutReloadEnabled,
                onSetForcePageZoomEnabled = coordinator::setForcePageZoomEnabled,
                onOpenWebTextSize = {
                    subPageName = KiyoriBrowserSettingsSubPage.WEB_TEXT_SIZE.name
                },
                onSetShowMediaCandidateBadge = coordinator::setShowMediaCandidateBadge,
                onSetAutomaticFloatingPlaybackEnabled =
                    coordinator::setAutomaticFloatingPlaybackEnabled,
                onSelectAutomaticFloatingMinimumDuration = {
                    settingsSelection =
                        automaticFloatingMinimumDurationSelection(
                            settings = settings,
                            onSelect = coordinator::setAutomaticFloatingMinimumDurationMillis,
                            onCustom = {
                                customFloatingDurationSeconds =
                                    (settings.automaticFloatingMinimumDurationMillis / 1_000L)
                                        .toString()
                                showCustomFloatingDurationDialog = true
                            },
                        )
                },
                onSetAllowWebPageOpenApp = coordinator::setAllowWebPageOpenApp,
                onSetAllowWebPageGeolocation = coordinator::setAllowWebPageGeolocation,
                onOpenPasswordManager = {
                    subPageName = KiyoriBrowserSettingsSubPage.PASSWORD_MANAGER.name
                },
                onClearCookies = { showClearCookieConfirm = true },
                credentialVaultState = credentialVaultState,
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
                onOpenUserscriptDetail = { scriptId ->
                    openBrowserPluginRoute {
                        coordinator.openUserscriptDetail(scriptId)
                    }
                },
                modifier = modifier,
            )
        KiyoriBrowserSettingsSubPage.WEB_TEXT_SIZE ->
            KiyoriBrowserTextSizePage(
                currentPercent = settings.webTextZoomPercent,
                onBack = ::closeCurrentPage,
                onSetPercent = coordinator::setWebTextZoomPercent,
                modifier = modifier,
            )
        KiyoriBrowserSettingsSubPage.PASSWORD_MANAGER ->
            KiyoriBrowserPasswordManagerPage(
                settings = settings,
                vaultState = credentialVaultState,
                onBack = ::closeCurrentPage,
                onSetPasswordSavingEnabled =
                    coordinator::setWebsitePasswordSavingEnabled,
                onLoadCredential = coordinator::browserCredential,
                onUpdateCredential = coordinator::updateBrowserCredential,
                onDeleteCredential = coordinator::deleteBrowserCredential,
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
    settingsSelection?.let { selection ->
        KiyoriSettingsSelectionSheet(
            selection = selection,
            onDismiss = { settingsSelection = null },
            onSelect = { option ->
                option.onSelect()
                settingsSelection = null
            },
        )
    }
    if (showCustomFloatingDurationDialog) {
        val parsedDuration =
            parseAutomaticFloatingDurationSeconds(customFloatingDurationSeconds)
        AlertDialog(
            onDismissRequest = { showCustomFloatingDurationDialog = false },
            title = { Text("自定义自动悬浮时长") },
            text = {
                OutlinedTextField(
                    value = customFloatingDurationSeconds,
                    onValueChange = { value ->
                        if (value.length <= 5 && value.all(Char::isDigit)) {
                            customFloatingDurationSeconds = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError =
                        customFloatingDurationSeconds.isNotBlank() && parsedDuration == null,
                    label = { Text("最小时长") },
                    suffix = { Text("秒") },
                    supportingText = {
                        Text(
                            if (
                                customFloatingDurationSeconds.isNotBlank() &&
                                    parsedDuration == null
                            ) {
                                "请输入 1–86400 秒"
                            } else {
                                "可精确到 1 秒，最长 24 小时"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = parsedDuration != null,
                    onClick = {
                        parsedDuration?.let(
                            coordinator::setAutomaticFloatingMinimumDurationMillis,
                        )
                        showCustomFloatingDurationDialog = false
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFloatingDurationDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

internal fun runBrowserPluginRouteFromSettings(
    onCloseSettings: () -> Unit,
    onOpenRoute: () -> Unit,
) {
    onCloseSettings()
    onOpenRoute()
}

@Composable
private fun KiyoriBrowserSettingsDetailPage(
    settings: WebSessionBrowserSettings,
    userscriptState: WebSessionUserscriptUiState,
    onBack: () -> Unit,
    onSetUserScriptsAllowed: (Boolean) -> Unit,
    onOpenPluginCenter: () -> Unit,
    onOpenPluginPermissions: () -> Unit,
    onOpenPluginDiagnostics: () -> Unit,
    onOpenHomeCustomization: () -> Unit,
    onSetReturnWithoutReloadEnabled: (Boolean) -> Unit,
    onSetForcePageZoomEnabled: (Boolean) -> Unit,
    onOpenWebTextSize: () -> Unit,
    onSetShowMediaCandidateBadge: (Boolean) -> Unit,
    onSetAutomaticFloatingPlaybackEnabled: (Boolean) -> Unit,
    onSelectAutomaticFloatingMinimumDuration: () -> Unit,
    onSetAllowWebPageOpenApp: (Boolean) -> Unit,
    onSetAllowWebPageGeolocation: (Boolean) -> Unit,
    onOpenPasswordManager: () -> Unit,
    onClearCookies: () -> Unit,
    credentialVaultState: BrowserCredentialVaultSnapshot,
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
                        isBrowserSettingRuntimeEnabled(entry, userscriptState, settings)
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
                                savedCredentialCount =
                                    credentialVaultState.credentials.size,
                                credentialVaultLoading =
                                    credentialVaultState.isLoading,
                                credentialVaultAvailable =
                                    credentialVaultState.isAvailable,
                            ),
                        checked = checked,
                        enabled = enabled,
                        onClick = {
                            when (entry.action) {
                                KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED ->
                                    onSetUserScriptsAllowed(!checked)
                                KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER ->
                                    onOpenPluginCenter()
                                KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS ->
                                    onOpenPluginPermissions()
                                KiyoriBrowserSettingsAction.OPEN_PLUGIN_DIAGNOSTICS ->
                                    onOpenPluginDiagnostics()
                                KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION ->
                                    onOpenHomeCustomization()
                                KiyoriBrowserSettingsAction.TOGGLE_RETURN_WITHOUT_RELOAD ->
                                    onSetReturnWithoutReloadEnabled(!checked)
                                KiyoriBrowserSettingsAction.TOGGLE_FORCE_PAGE_ZOOM ->
                                    onSetForcePageZoomEnabled(!checked)
                                KiyoriBrowserSettingsAction.OPEN_WEB_TEXT_SIZE ->
                                    onOpenWebTextSize()
                                KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY ->
                                    onSetShowMediaCandidateBadge(!checked)
                                KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK ->
                                    onSetAutomaticFloatingPlaybackEnabled(!checked)
                                KiyoriBrowserSettingsAction
                                    .SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION ->
                                    onSelectAutomaticFloatingMinimumDuration()
                                KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP ->
                                    onSetAllowWebPageOpenApp(!checked)
                                KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION ->
                                    onSetAllowWebPageGeolocation(!checked)
                                KiyoriBrowserSettingsAction.OPEN_PASSWORD_MANAGER ->
                                    onOpenPasswordManager()
                                KiyoriBrowserSettingsAction.CLEAR_COOKIES ->
                                    onClearCookies()
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

internal fun isBrowserSettingRuntimeEnabled(
    entry: KiyoriBrowserSettingsEntrySpec,
    userscriptState: WebSessionUserscriptUiState,
    settings: WebSessionBrowserSettings = WebSessionBrowserSettings(),
): Boolean =
    when (entry.action) {
        KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED ->
            isUserscriptRuntimePermissionActionEnabled(
                runtimeSupported = userscriptState.supportState.isSupported,
                userScriptsAllowed = userscriptState.userScriptsAllowed,
            )
        KiyoriBrowserSettingsAction.SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION ->
            settings.automaticFloatingPlaybackEnabled
        else -> true
    }

internal fun browserSettingValue(
    entry: KiyoriBrowserSettingsEntrySpec,
    settings: WebSessionBrowserSettings,
    userscriptState: WebSessionUserscriptUiState = WebSessionUserscriptUiState(),
    savedCredentialCount: Int = 0,
    credentialVaultLoading: Boolean = false,
    credentialVaultAvailable: Boolean = true,
): String? =
    if (entry.kind != KiyoriSettingsRowKind.NAVIGATION) {
        null
    } else {
        when (entry.action) {
            KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER ->
                browserPluginCenterSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS ->
                browserPluginPermissionSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_PLUGIN_DIAGNOSTICS ->
                browserPluginDiagnosticsSummary(userscriptState)
            KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION ->
                formatBrowserHomeUrl(settings.homeUrl)
            KiyoriBrowserSettingsAction.OPEN_WEB_TEXT_SIZE ->
                formatWebTextZoomPercent(settings.webTextZoomPercent)
            KiyoriBrowserSettingsAction.SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION ->
                formatAutomaticFloatingMinimumDuration(
                    settings.automaticFloatingMinimumDurationMillis,
                )
            KiyoriBrowserSettingsAction.OPEN_PASSWORD_MANAGER ->
                when {
                    credentialVaultLoading -> "解锁中"
                    credentialVaultAvailable -> "$savedCredentialCount 项"
                    else -> "不可用"
                }
            KiyoriBrowserSettingsAction.CLEAR_COOKIES -> null
            KiyoriBrowserSettingsAction.TOGGLE_USER_SCRIPTS_ALLOWED,
            KiyoriBrowserSettingsAction.TOGGLE_RETURN_WITHOUT_RELOAD,
            KiyoriBrowserSettingsAction.TOGGLE_FORCE_PAGE_ZOOM,
            KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
            KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
            KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP,
            KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION -> null
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
        KiyoriBrowserSettingsAction.TOGGLE_RETURN_WITHOUT_RELOAD ->
            settings.returnWithoutReloadEnabled
        KiyoriBrowserSettingsAction.TOGGLE_FORCE_PAGE_ZOOM ->
            settings.forcePageZoomEnabled
        KiyoriBrowserSettingsAction.TOGGLE_SEARCH_BAR_SNIFFER_ENTRY ->
            settings.showMediaCandidateBadge
        KiyoriBrowserSettingsAction.TOGGLE_AUTOMATIC_FLOATING_PLAYBACK ->
            settings.automaticFloatingPlaybackEnabled
        KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_OPEN_APP ->
            settings.allowWebPageOpenApp
        KiyoriBrowserSettingsAction.TOGGLE_WEB_PAGE_GEOLOCATION ->
            settings.allowWebPageGeolocation
        KiyoriBrowserSettingsAction.OPEN_PLUGIN_CENTER,
        KiyoriBrowserSettingsAction.OPEN_PLUGIN_PERMISSIONS,
        KiyoriBrowserSettingsAction.OPEN_PLUGIN_DIAGNOSTICS,
        KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION,
        KiyoriBrowserSettingsAction.OPEN_WEB_TEXT_SIZE,
        KiyoriBrowserSettingsAction.SELECT_AUTOMATIC_FLOATING_MINIMUM_DURATION,
        KiyoriBrowserSettingsAction.OPEN_PASSWORD_MANAGER,
        KiyoriBrowserSettingsAction.CLEAR_COOKIES -> false
    }

internal fun automaticFloatingMinimumDurationSelection(
    settings: WebSessionBrowserSettings,
    onSelect: (Long) -> Unit,
    onCustom: () -> Unit,
): KiyoriSettingsSelection {
    val currentDuration = settings.automaticFloatingMinimumDurationMillis
    return KiyoriSettingsSelection(
        title = "自动悬浮最小时长",
        currentValue = formatAutomaticFloatingMinimumDuration(currentDuration),
        options =
            AUTOMATIC_FLOATING_MINIMUM_DURATION_OPTIONS_MILLIS.map { durationMillis ->
                KiyoriSettingsSelectionOption(
                    label = formatAutomaticFloatingMinimumDuration(durationMillis),
                    description = "视频时长达到此值后才允许自动打开小窗",
                    selected = currentDuration == durationMillis,
                    onSelect = { onSelect(durationMillis) },
                )
            } +
                KiyoriSettingsSelectionOption(
                    label = "自定义时长",
                    description = "输入 1–86400 秒，可精确到 1 秒",
                    selected =
                        currentDuration !in
                            AUTOMATIC_FLOATING_MINIMUM_DURATION_OPTIONS_MILLIS,
                    onSelect = onCustom,
                ),
    )
}

internal fun browserPluginCenterSummary(state: WebSessionUserscriptUiState): String =
    "1 个插件 · ${state.installedScripts.size} 个脚本"

internal fun browserPluginLogSummary(state: WebSessionUserscriptUiState): String =
    "${state.recentLogs.size} 条保留日志"

internal fun browserPluginDiagnosticsSummary(state: WebSessionUserscriptUiState): String =
    "${browserPluginCurrentPageSummary(state)} · ${browserPluginLogSummary(state)}"

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
                description = browserPluginRuntimeAuthorizationDescription(state),
            ) {
                KiyoriSettingsRow(
                    title = "允许用户脚本",
                    description = "允许已启用脚本在匹配网页中运行",
                    kind = KiyoriSettingsRowKind.TOGGLE,
                    checked = state.userScriptsAllowed,
                    enabled =
                        isUserscriptRuntimePermissionActionEnabled(
                            runtimeSupported = state.supportState.isSupported,
                            userScriptsAllowed = state.userScriptsAllowed,
                        ),
                    onClick = { onSetUserScriptsAllowed(!state.userScriptsAllowed) },
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
                    Text(
                        text = "暂无已安装脚本；可从插件中心右上角加号通过 URL、本地文件或插件库安装",
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun browserPluginRuntimeAuthorizationDescription(
    state: WebSessionUserscriptUiState,
): String =
    buildString {
        append(if (state.supportState.isSupported) "当前运行环境可用" else "当前运行环境不可用")
        state.supportState.reason
            ?.takeIf(String::isNotBlank)
            ?.let { reason ->
                append("：")
                append(reason)
            }
        append("；总授权由 userscript registry 唯一持有，关闭后不会删除脚本或修改启用状态")
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
    var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }

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
                    onClick = { showResetConfirmDialog = true },
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

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "恢复为空白页？",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Text(
                    text = "确认后将清除当前自定义主页，并把浏览器主页恢复为 about:blank。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onReset()
                    },
                ) {
                    Text(
                        text = "恢复",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
