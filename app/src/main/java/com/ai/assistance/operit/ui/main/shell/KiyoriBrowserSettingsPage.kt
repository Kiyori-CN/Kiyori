package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.DEFAULT_BROWSER_HOME_URL
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.isSupportedBrowserHomeUrl

internal enum class KiyoriBrowserSettingsAction {
    NONE,
    OPEN_HOME_CUSTOMIZATION,
    TOGGLE_SEARCH_BAR_SNIFFER_ENTRY,
    TOGGLE_AUTOMATIC_FLOATING_PLAYBACK,
    TOGGLE_WEB_PAGE_OPEN_APP,
    TOGGLE_WEB_PAGE_GEOLOCATION,
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
            title = "插件与会话",
            description = "管理网页扩展以及标签返回、启动恢复等会话行为",
            entries =
                listOf(
                    browserNavigation(
                        title = "网页插件管理",
                        description = "管理已安装的网页脚本与扩展能力",
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
}

@Composable
internal fun KiyoriBrowserSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator =
        remember(context) {
            BrowserPresentationCoordinator.getInstance(context.applicationContext)
        }
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val settings by coordinator.browserSettings.collectAsState()
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    var subPageName by rememberSaveable { mutableStateOf<String?>(null) }
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
                onBack = ::closeCurrentPage,
                onOpenHomeCustomization = {
                    subPageName = KiyoriBrowserSettingsSubPage.HOME_CUSTOMIZATION.name
                },
                onSetShowMediaCandidateBadge = coordinator::setShowMediaCandidateBadge,
                onSetAutomaticFloatingPlaybackEnabled =
                    coordinator::setAutomaticFloatingPlaybackEnabled,
                onSetAllowWebPageOpenApp = coordinator::setAllowWebPageOpenApp,
                onSetAllowWebPageGeolocation = coordinator::setAllowWebPageGeolocation,
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
    }
}

@Composable
private fun KiyoriBrowserSettingsDetailPage(
    settings: WebSessionBrowserSettings,
    onBack: () -> Unit,
    onOpenHomeCustomization: () -> Unit,
    onSetShowMediaCandidateBadge: (Boolean) -> Unit,
    onSetAutomaticFloatingPlaybackEnabled: (Boolean) -> Unit,
    onSetAllowWebPageOpenApp: (Boolean) -> Unit,
    onSetAllowWebPageGeolocation: (Boolean) -> Unit,
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
                    val enabled = isBrowserSettingEnabled(entry)
                    val checked = browserSettingToggleValue(entry, settings)
                    KiyoriSettingsRow(
                        title = entry.title,
                        description = entry.description,
                        kind = entry.kind,
                        value = browserSettingValue(entry, settings),
                        checked = checked,
                        enabled = enabled,
                        onClick = {
                            when (entry.action) {
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

internal fun browserSettingValue(
    entry: KiyoriBrowserSettingsEntrySpec,
    settings: WebSessionBrowserSettings,
): String? =
    if (entry.kind != KiyoriSettingsRowKind.NAVIGATION) {
        null
    } else {
        when (entry.action) {
            KiyoriBrowserSettingsAction.OPEN_HOME_CUSTOMIZATION ->
                formatBrowserHomeUrl(settings.homeUrl)
            KiyoriBrowserSettingsAction.NONE -> entry.value ?: "未接入"
            else -> entry.value
        }
    }

private fun browserSettingToggleValue(
    entry: KiyoriBrowserSettingsEntrySpec,
    settings: WebSessionBrowserSettings,
): Boolean =
    when (entry.action) {
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

internal fun formatBrowserHomeUrl(url: String): String =
    if (url.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true)) "空白页" else url

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
        containerColor = Color.White,
        title = {
            Text(
                text = "自定义主页入口",
                color = Color(0xFF292825),
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
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF667EEA),
                        focusedLabelColor = Color(0xFF667EEA),
                        cursorColor = Color(0xFF667EEA),
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(inputValue.trim()) }) {
                Text(
                    text = "保存",
                    color = Color(0xFF667EEA),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = Color(0xFF77736E),
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        shape = RoundedCornerShape(22.dp),
    )
}
