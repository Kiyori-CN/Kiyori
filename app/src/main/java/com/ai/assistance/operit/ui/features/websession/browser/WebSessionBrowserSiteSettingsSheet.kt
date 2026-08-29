package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserSettings
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSiteFeature
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveBrowserAdBlockAllowlistedDomain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.resolveWebSessionNetworkProxyDisabledDomain
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.siteSettingsRule

internal enum class WebSessionSiteSettingId {
    NETWORK_PROXY,
    AD_BLOCKING,
    USER_SCRIPTS,
    RETURN_WITHOUT_RELOAD,
    SWIPE_HISTORY_NAVIGATION,
    FORCE_PAGE_ZOOM,
    WEB_ELEMENT_LONG_PRESS_MENU,
    WEB_PAGE_OPEN_APP,
    WEB_PAGE_GEOLOCATION,
    WEBSITE_PASSWORD_SAVING,
    MEDIA_CANDIDATE_BADGE,
    AUTOMATIC_FLOATING_PLAYBACK,
}

internal data class WebSessionSiteSettingSpec(
    val id: WebSessionSiteSettingId,
    val title: String,
    val description: String,
    val feature: WebSessionSiteFeature?,
)

internal data class WebSessionSiteSettingSection(
    val title: String,
    val description: String,
    val settings: List<WebSessionSiteSettingSpec>,
)

internal val webSessionSiteSettingSections =
    listOf(
        WebSessionSiteSettingSection(
            title = "网络",
            description = "控制当前域名是否经过 Kiyori 应用内代理",
            settings =
                listOf(
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.NETWORK_PROXY,
                        title = "禁用网络代理",
                        description = "当前域名及其子域名始终直连，优先于应用代理和订阅规则",
                        feature = WebSessionSiteFeature.DISABLE_NETWORK_PROXY,
                    ),
                ),
        ),
        WebSessionSiteSettingSection(
            title = "内容与脚本",
            description = "控制当前域名的内容过滤和网页脚本运行",
            settings =
                listOf(
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.AD_BLOCKING,
                        title = "禁用广告拦截",
                        description = "不再对当前域名应用网络过滤和元素隐藏规则",
                        feature = null,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.USER_SCRIPTS,
                        title = "禁用用户脚本",
                        description = "阻止脚本注入、菜单命令、桥接能力和脚本网络规则",
                        feature = WebSessionSiteFeature.USER_SCRIPTS,
                    ),
                ),
        ),
        WebSessionSiteSettingSection(
            title = "页面与交互",
            description = "限制当前域名的页面返回、缩放和元素操作能力",
            settings =
                listOf(
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.RETURN_WITHOUT_RELOAD,
                        title = "禁用返回不重载",
                        description = "返回此域名页面时不再强制使用历史缓存",
                        feature = WebSessionSiteFeature.RETURN_WITHOUT_RELOAD,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.SWIPE_HISTORY_NAVIGATION,
                        title = "禁用左右滑动前进后退",
                        description = "避免当前域名的横向轮播和画布手势触发浏览器导航",
                        feature = WebSessionSiteFeature.SWIPE_HISTORY_NAVIGATION,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.FORCE_PAGE_ZOOM,
                        title = "禁用强制页面缩放",
                        description = "遵从当前域名网页自身声明的缩放限制",
                        feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.WEB_ELEMENT_LONG_PRESS_MENU,
                        title = "禁用网页元素长按菜单",
                        description = "不在当前域名显示 Kiyori 的链接、图片和元素操作菜单",
                        feature = WebSessionSiteFeature.WEB_ELEMENT_LONG_PRESS_MENU,
                    ),
                ),
        ),
        WebSessionSiteSettingSection(
            title = "权限与隐私",
            description = "收紧当前域名可使用的外部能力和本地凭据能力",
            settings =
                listOf(
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.WEB_PAGE_OPEN_APP,
                        title = "禁止网页打开应用",
                        description = "阻止当前域名通过外部链接唤起已安装应用",
                        feature = WebSessionSiteFeature.WEB_PAGE_OPEN_APP,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.WEB_PAGE_GEOLOCATION,
                        title = "禁止网页获取位置",
                        description = "拒绝当前域名发起的网页定位请求",
                        feature = WebSessionSiteFeature.WEB_PAGE_GEOLOCATION,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.WEBSITE_PASSWORD_SAVING,
                        title = "禁用网站密码保存与填充",
                        description = "不在当前域名捕获、读取或自动填充已保存的网站密码",
                        feature = WebSessionSiteFeature.WEBSITE_PASSWORD_SAVING,
                    ),
                ),
        ),
        WebSessionSiteSettingSection(
            title = "音视频",
            description = "限制当前域名的资源嗅探提示和自动悬浮播放",
            settings =
                listOf(
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.MEDIA_CANDIDATE_BADGE,
                        title = "隐藏搜索栏嗅探入口",
                        description = "发现音视频资源时不在搜索栏显示资源入口",
                        feature = WebSessionSiteFeature.MEDIA_CANDIDATE_BADGE,
                    ),
                    WebSessionSiteSettingSpec(
                        id = WebSessionSiteSettingId.AUTOMATIC_FLOATING_PLAYBACK,
                        title = "禁用自动悬浮播放",
                        description = "当前域名发现推荐视频后不自动打开悬浮播放器",
                        feature = WebSessionSiteFeature.AUTOMATIC_FLOATING_PLAYBACK,
                    ),
                ),
        ),
    )

internal fun isWebSessionSiteSettingGloballyEnabled(
    id: WebSessionSiteSettingId,
    browserSettings: WebSessionBrowserSettings,
    adBlockState: BrowserAdBlockState,
    userScriptsAllowed: Boolean,
    networkProxyEnabled: Boolean = true,
): Boolean =
    when (id) {
        WebSessionSiteSettingId.NETWORK_PROXY -> networkProxyEnabled
        WebSessionSiteSettingId.AD_BLOCKING -> adBlockState.enabled
        WebSessionSiteSettingId.USER_SCRIPTS -> userScriptsAllowed
        WebSessionSiteSettingId.RETURN_WITHOUT_RELOAD ->
            browserSettings.returnWithoutReloadEnabled
        WebSessionSiteSettingId.SWIPE_HISTORY_NAVIGATION ->
            browserSettings.swipeHistoryNavigationEnabled
        WebSessionSiteSettingId.FORCE_PAGE_ZOOM ->
            browserSettings.forcePageZoomEnabled
        WebSessionSiteSettingId.WEB_ELEMENT_LONG_PRESS_MENU ->
            browserSettings.webElementLongPressMenuEnabled
        WebSessionSiteSettingId.WEB_PAGE_OPEN_APP ->
            browserSettings.allowWebPageOpenApp
        WebSessionSiteSettingId.WEB_PAGE_GEOLOCATION ->
            browserSettings.allowWebPageGeolocation
        WebSessionSiteSettingId.WEBSITE_PASSWORD_SAVING ->
            browserSettings.websitePasswordSavingEnabled
        WebSessionSiteSettingId.MEDIA_CANDIDATE_BADGE ->
            browserSettings.showMediaCandidateBadge
        WebSessionSiteSettingId.AUTOMATIC_FLOATING_PLAYBACK ->
            browserSettings.automaticFloatingPlaybackEnabled
    }

@Composable
internal fun WebSessionBrowserSiteSettingsSheet(
    domain: String?,
    browserSettings: WebSessionBrowserSettings,
    adBlockState: BrowserAdBlockState,
    userScriptsAllowed: Boolean,
    onSetFeatureDisabled: (String, WebSessionSiteFeature, Boolean) -> Unit,
    onSetAdBlockingDisabled: (String, Boolean) -> Unit,
    onClearSiteSettings: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    networkProxyEnabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WebSessionDrawerHeader(
                title = stringResource(R.string.web_session_site_config),
                leadingIcon = Icons.Filled.Security,
                tone = WebSessionBrowserMenuTone.SITE_CONFIG,
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.web_session_back),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )

            if (domain == null) {
                WebSessionEmptyState(
                    icon = Icons.Filled.Language,
                    title = "当前页面没有可配置的网站域名",
                    message = "网站配置只适用于 HTTP 或 HTTPS 页面；本地文件、空白页和内部页面不会创建站点规则。",
                    tone = WebSessionBrowserMenuTone.SITE_CONFIG,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            } else {
                WebSessionSiteSettingsContent(
                    domain = domain,
                    browserSettings = browserSettings,
                    adBlockState = adBlockState,
                    userScriptsAllowed = userScriptsAllowed,
                    networkProxyEnabled = networkProxyEnabled,
                    onSetFeatureDisabled = onSetFeatureDisabled,
                    onSetAdBlockingDisabled = onSetAdBlockingDisabled,
                    onClearSiteSettings = onClearSiteSettings,
                )
            }
        }
    }
}

@Composable
private fun WebSessionSiteSettingsContent(
    domain: String,
    browserSettings: WebSessionBrowserSettings,
    adBlockState: BrowserAdBlockState,
    userScriptsAllowed: Boolean,
    networkProxyEnabled: Boolean,
    onSetFeatureDisabled: (String, WebSessionSiteFeature, Boolean) -> Unit,
    onSetAdBlockingDisabled: (String, Boolean) -> Unit,
    onClearSiteSettings: (String) -> Unit,
) {
    val toneColors = WebSessionBrowserMenuTone.SITE_CONFIG.resolveColors()
    val exactAdBlockDisabled = domain in adBlockState.allowlistedDomains
    val matchedAdBlockDomain =
        resolveBrowserAdBlockAllowlistedDomain(domain, adBlockState.allowlistedDomains)
    val siteRule = browserSettings.siteSettingsRule(domain)
    val exactNetworkProxyDisabled =
        WebSessionSiteFeature.DISABLE_NETWORK_PROXY in siteRule?.disabledFeatures.orEmpty()
    val matchedNetworkProxyDomain =
        resolveWebSessionNetworkProxyDisabledDomain(domain, browserSettings.siteSettingsRules)
    val hasExactSiteSettings = siteRule != null || exactAdBlockDisabled

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp,
            top = 4.dp,
            end = 14.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "site-settings-summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = toneColors.container.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, toneColors.icon.copy(alpha = 0.18f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = domain,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            "这里的开关只会进一步限制当前域名。未设置时遵从全局；" +
                                "全局关闭的能力不能在这里重新开启。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        items(
            items = webSessionSiteSettingSections,
            key = WebSessionSiteSettingSection::title,
        ) { section ->
            WebSessionSiteSettingsSectionCard(
                section = section,
                domain = domain,
                browserSettings = browserSettings,
                adBlockState = adBlockState,
                userScriptsAllowed = userScriptsAllowed,
                networkProxyEnabled = networkProxyEnabled,
                matchedAdBlockDomain = matchedAdBlockDomain,
                exactAdBlockDisabled = exactAdBlockDisabled,
                matchedNetworkProxyDomain = matchedNetworkProxyDomain,
                exactNetworkProxyDisabled = exactNetworkProxyDisabled,
                onSetFeatureDisabled = onSetFeatureDisabled,
                onSetAdBlockingDisabled = onSetAdBlockingDisabled,
            )
        }

        item(key = "userscript-refresh-note") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text =
                        "用户脚本禁用后会立即阻断新的脚本注入、桥接和联网能力；" +
                            "刷新当前页后，才能清除已经执行的页面脚本所造成的界面影响。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                )
            }
        }

        if (hasExactSiteSettings) {
            item(key = "clear-site-settings") {
                TextButton(
                    onClick = {
                        if (siteRule != null) {
                            onClearSiteSettings(domain)
                        }
                        if (exactAdBlockDisabled) {
                            onSetAdBlockingDisabled(domain, false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "清除当前域名单独配置",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSessionSiteSettingsSectionCard(
    section: WebSessionSiteSettingSection,
    domain: String,
    browserSettings: WebSessionBrowserSettings,
    adBlockState: BrowserAdBlockState,
    userScriptsAllowed: Boolean,
    networkProxyEnabled: Boolean,
    matchedAdBlockDomain: String?,
    exactAdBlockDisabled: Boolean,
    matchedNetworkProxyDomain: String?,
    exactNetworkProxyDisabled: Boolean,
    onSetFeatureDisabled: (String, WebSessionSiteFeature, Boolean) -> Unit,
    onSetAdBlockingDisabled: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = section.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = section.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                section.settings.forEachIndexed { index, setting ->
                    val globalEnabled =
                        isWebSessionSiteSettingGloballyEnabled(
                            id = setting.id,
                            browserSettings = browserSettings,
                            adBlockState = adBlockState,
                            userScriptsAllowed = userScriptsAllowed,
                            networkProxyEnabled = networkProxyEnabled,
                        )
                    val inheritedAdBlockDisabled =
                        setting.id == WebSessionSiteSettingId.AD_BLOCKING &&
                            matchedAdBlockDomain != null &&
                            !exactAdBlockDisabled
                    val inheritedNetworkProxyDisabled =
                        setting.id == WebSessionSiteSettingId.NETWORK_PROXY &&
                            matchedNetworkProxyDomain != null &&
                            !exactNetworkProxyDisabled
                    val siteDisabled =
                        when (setting.id) {
                            WebSessionSiteSettingId.AD_BLOCKING -> matchedAdBlockDomain != null
                            WebSessionSiteSettingId.NETWORK_PROXY -> matchedNetworkProxyDomain != null
                            else ->
                                setting.feature in
                                    browserSettings
                                        .siteSettingsRule(domain)
                                        ?.disabledFeatures
                                        .orEmpty()
                        }
                    WebSessionSiteSettingRow(
                        setting = setting,
                        globalEnabled = globalEnabled,
                        siteDisabled = siteDisabled,
                        inheritedDisabledDomain =
                            when {
                                inheritedAdBlockDisabled -> matchedAdBlockDomain
                                inheritedNetworkProxyDisabled -> matchedNetworkProxyDomain
                                else -> null
                            },
                        enabled = !inheritedAdBlockDisabled && !inheritedNetworkProxyDisabled,
                        onCheckedChange = { disabled ->
                            if (setting.id == WebSessionSiteSettingId.AD_BLOCKING) {
                                onSetAdBlockingDisabled(domain, disabled)
                            } else {
                                onSetFeatureDisabled(
                                    domain,
                                    requireNotNull(setting.feature),
                                    disabled,
                                )
                            }
                        },
                    )
                    if (index != section.settings.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            thickness = 0.6.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSessionSiteSettingRow(
    setting: WebSessionSiteSettingSpec,
    globalEnabled: Boolean,
    siteDisabled: Boolean,
    inheritedDisabledDomain: String?,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val toneColors = WebSessionBrowserMenuTone.SITE_CONFIG.resolveColors()
    val statusText =
        when {
            inheritedDisabledDomain != null ->
                "由 $inheritedDisabledDomain 的上级域名规则禁用"
            !globalEnabled && siteDisabled ->
                "全局已关闭 · 已保留当前域名禁用"
            !globalEnabled ->
                "全局已关闭"
            siteDisabled ->
                "已对当前域名禁用"
            else ->
                "遵从全局 · 当前启用"
        }
    val statusColor =
        when {
            inheritedDisabledDomain != null || siteDisabled -> toneColors.icon
            globalEnabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = setting.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = setting.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Switch(
            checked = siteDisabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = toneColors.icon,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor =
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
                    disabledCheckedTrackColor = toneColors.icon.copy(alpha = 0.42f),
                ),
        )
    }
}
