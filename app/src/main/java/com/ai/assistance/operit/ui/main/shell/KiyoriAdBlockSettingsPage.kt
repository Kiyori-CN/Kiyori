package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockCustomElementRule
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockCustomNetworkRule
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockSubscription
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.applyBrowserAdBlockRulesToAllSessionsOnMain
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

internal const val KIYORI_AD_BLOCK_SETTINGS_PAGE_TITLE = "广告拦截器"

private enum class KiyoriAdBlockSettingsSection(
    val title: String,
) {
    OVERVIEW(KIYORI_AD_BLOCK_SETTINGS_PAGE_TITLE),
    NETWORK_RULES("网址过滤"),
    ELEMENT_RULES("网页元素"),
    ALLOWLIST("站点白名单"),
    SUBSCRIPTIONS("广告拦截订阅"),
}

private sealed interface KiyoriAdBlockEditorRequest {
    data class NetworkRule(
        val rule: BrowserAdBlockCustomNetworkRule?,
    ) : KiyoriAdBlockEditorRequest

    data class ElementRule(
        val rule: BrowserAdBlockCustomElementRule?,
    ) : KiyoriAdBlockEditorRequest

    data object AllowlistDomain : KiyoriAdBlockEditorRequest

    data class Subscription(
        val subscription: BrowserAdBlockSubscription?,
    ) : KiyoriAdBlockEditorRequest
}

private sealed interface KiyoriAdBlockDeleteRequest {
    val title: String

    data class NetworkRule(
        val rule: BrowserAdBlockCustomNetworkRule,
        override val title: String = "删除网址过滤规则",
    ) : KiyoriAdBlockDeleteRequest

    data class ElementRule(
        val rule: BrowserAdBlockCustomElementRule,
        override val title: String = "删除网页元素规则",
    ) : KiyoriAdBlockDeleteRequest

    data class AllowlistDomain(
        val domain: String,
        override val title: String = "移出站点白名单",
    ) : KiyoriAdBlockDeleteRequest

    data class Subscription(
        val subscription: BrowserAdBlockSubscription,
        override val title: String = "删除广告拦截订阅",
    ) : KiyoriAdBlockDeleteRequest
}

@Composable
internal fun KiyoriAdBlockSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember(appContext) { BrowserAdBlockStore.getInstance(appContext) }
    val browserTools =
        remember(appContext) { StandardBrowserSessionTools.getSharedInstance(appContext) }
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(KiyoriAdBlockSettingsSection.OVERVIEW) }
    var searchQuery by remember(section) { mutableStateOf("") }
    var editorRequest by remember { mutableStateOf<KiyoriAdBlockEditorRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<KiyoriAdBlockDeleteRequest?>(null) }
    var refreshingSubscriptionId by remember { mutableStateOf<String?>(null) }

    fun applyRuntimeRules() {
        browserTools.applyBrowserAdBlockRulesToAllSessionsOnMain()
    }

    fun commitMutation(
        action: String,
        mutation: () -> Unit,
    ): Boolean =
        try {
            mutation()
            applyRuntimeRules()
            true
        } catch (error: Exception) {
            AppLogger.e("KiyoriAdBlockSettings", "Failed to $action", error)
            Toast.makeText(
                context,
                "广告拦截设置保存失败",
                Toast.LENGTH_SHORT,
            ).show()
            false
        }

    val requestBack = {
        if (section == KiyoriAdBlockSettingsSection.OVERVIEW) {
            onBack()
        } else {
            section = KiyoriAdBlockSettingsSection.OVERVIEW
        }
    }
    BackHandler(enabled = section != KiyoriAdBlockSettingsSection.OVERVIEW) {
        section = KiyoriAdBlockSettingsSection.OVERVIEW
    }

    KiyoriCollapsingSettingsPage(
        title = section.title,
        onBack = requestBack,
        modifier = modifier,
    ) {
        when (section) {
            KiyoriAdBlockSettingsSection.OVERVIEW -> {
                item(key = "ad_block_overview_status") {
                    KiyoriAdBlockOverviewStatus(
                        state = state,
                        onToggleEnabled = {
                            commitMutation("toggle browser ad blocking") {
                                store.setEnabled(!state.enabled)
                            }
                        },
                    )
                }
                item(key = "ad_block_overview_rules") {
                    KiyoriSettingsGroupSection(
                        title = "规则与站点",
                        description = "所有入口共同使用同一份规则；修改后会立即应用到现有浏览器窗口",
                    ) {
                        KiyoriSettingsRow(
                            title = "网址过滤",
                            description = "拦截广告脚本、图片、接口和其他请求地址",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.LinkOff,
                            iconTone = KiyoriSemanticTone.RED,
                            value = state.customNetworkRules.size.toString(),
                            onClick = {
                                section = KiyoriAdBlockSettingsSection.NETWORK_RULES
                            },
                        )
                        KiyoriSettingsDivider()
                        KiyoriSettingsRow(
                            title = "网页元素",
                            description = "按站点隐藏广告容器、浮层和干扰组件",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.VisibilityOff,
                            iconTone = KiyoriSemanticTone.PURPLE,
                            value = state.customElementRules.size.toString(),
                            onClick = {
                                section = KiyoriAdBlockSettingsSection.ELEMENT_RULES
                            },
                        )
                        KiyoriSettingsDivider()
                        KiyoriSettingsRow(
                            title = "站点白名单",
                            description = "白名单站点不会执行请求拦截或元素隐藏",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.Shield,
                            iconTone = KiyoriSemanticTone.GREEN,
                            value = state.allowlistedDomains.size.toString(),
                            onClick = {
                                section = KiyoriAdBlockSettingsSection.ALLOWLIST
                            },
                        )
                    }
                }
                item(key = "ad_block_overview_subscriptions") {
                    KiyoriSettingsGroupSection(
                        title = "广告拦截订阅",
                        description = "订阅由你显式添加并手动刷新；Kiyori 不内置未经确认的远程列表",
                    ) {
                        KiyoriSettingsRow(
                            title = "订阅管理",
                            description = "添加、启停、编辑和刷新 Adblock Plus 兼容规则",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.Public,
                            iconTone = KiyoriSemanticTone.BLUE,
                            value = state.subscriptions.size.toString(),
                            onClick = {
                                section = KiyoriAdBlockSettingsSection.SUBSCRIPTIONS
                            },
                        )
                    }
                }
            }

            KiyoriAdBlockSettingsSection.NETWORK_RULES -> {
                item(key = "ad_block_network_controls") {
                    AdBlockSearchAndAddSection(
                        description = "普通文本为包含匹配，也支持 ||host^、|prefix、suffix|、*、^、正则和 @@ 例外规则",
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editorRequest = KiyoriAdBlockEditorRequest.NetworkRule(null)
                        },
                    )
                }
                item(key = "ad_block_network_list") {
                    val filtered =
                        state.customNetworkRules.filter { rule ->
                            rule.rule.contains(searchQuery.trim(), ignoreCase = true)
                        }
                    AdBlockManagedListSection(
                        title = "自定义规则",
                        description = "共 ${state.customNetworkRules.size} 条，已启用 ${state.customNetworkRules.count { it.enabled }} 条",
                    ) {
                        filtered.forEachIndexed { index, rule ->
                            AdBlockManagedRow(
                                title = rule.rule,
                                description = "自定义网址过滤规则",
                                enabled = rule.enabled,
                                codeStyle = true,
                                onToggle = {
                                    commitMutation("toggle network rule") {
                                        store.setNetworkRuleEnabled(rule.id, !rule.enabled)
                                    }
                                },
                                onEdit = {
                                    editorRequest =
                                        KiyoriAdBlockEditorRequest.NetworkRule(rule)
                                },
                                onDelete = {
                                    deleteRequest =
                                        KiyoriAdBlockDeleteRequest.NetworkRule(rule)
                                },
                            )
                            if (index != filtered.lastIndex) KiyoriSettingsDivider()
                        }
                        if (filtered.isEmpty()) {
                            AdBlockEmptyRow("没有符合条件的网址过滤规则")
                        }
                    }
                }
            }

            KiyoriAdBlockSettingsSection.ELEMENT_RULES -> {
                item(key = "ad_block_element_controls") {
                    AdBlockSearchAndAddSection(
                        description = "每条规则由站点域名和 CSS selector 组成；浏览器“标记广告”也会写入这里",
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editorRequest = KiyoriAdBlockEditorRequest.ElementRule(null)
                        },
                    )
                }
                item(key = "ad_block_element_list") {
                    val query = searchQuery.trim()
                    val filtered =
                        state.customElementRules.filter { rule ->
                            rule.domain.contains(query, ignoreCase = true) ||
                                rule.selector.contains(query, ignoreCase = true)
                        }
                    AdBlockManagedListSection(
                        title = "自定义元素规则",
                        description = "共 ${state.customElementRules.size} 条，已启用 ${state.customElementRules.count { it.enabled }} 条",
                    ) {
                        filtered.forEachIndexed { index, rule ->
                            AdBlockManagedRow(
                                title = rule.domain,
                                description = rule.selector,
                                enabled = rule.enabled,
                                codeStyle = true,
                                onToggle = {
                                    commitMutation("toggle element rule") {
                                        store.setElementRuleEnabled(rule.id, !rule.enabled)
                                    }
                                },
                                onEdit = {
                                    editorRequest =
                                        KiyoriAdBlockEditorRequest.ElementRule(rule)
                                },
                                onDelete = {
                                    deleteRequest =
                                        KiyoriAdBlockDeleteRequest.ElementRule(rule)
                                },
                            )
                            if (index != filtered.lastIndex) KiyoriSettingsDivider()
                        }
                        if (filtered.isEmpty()) {
                            AdBlockEmptyRow("没有符合条件的网页元素规则")
                        }
                    }
                }
            }

            KiyoriAdBlockSettingsSection.ALLOWLIST -> {
                item(key = "ad_block_allowlist_controls") {
                    AdBlockSearchAndAddSection(
                        description = "填写主机名，例如 example.com；其子域名会一并跳过广告拦截",
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editorRequest = KiyoriAdBlockEditorRequest.AllowlistDomain
                        },
                    )
                }
                item(key = "ad_block_allowlist_list") {
                    val filtered =
                        state.allowlistedDomains.filter { domain ->
                            domain.contains(searchQuery.trim(), ignoreCase = true)
                        }
                    AdBlockManagedListSection(
                        title = "已允许站点",
                        description = "共 ${state.allowlistedDomains.size} 个站点",
                    ) {
                        filtered.forEachIndexed { index, domain ->
                            AdBlockManagedRow(
                                title = domain,
                                description = "请求拦截与网页元素隐藏均不执行",
                                codeStyle = true,
                                onDelete = {
                                    deleteRequest =
                                        KiyoriAdBlockDeleteRequest.AllowlistDomain(domain)
                                },
                            )
                            if (index != filtered.lastIndex) KiyoriSettingsDivider()
                        }
                        if (filtered.isEmpty()) {
                            AdBlockEmptyRow("没有符合条件的白名单站点")
                        }
                    }
                }
            }

            KiyoriAdBlockSettingsSection.SUBSCRIPTIONS -> {
                item(key = "ad_block_subscription_controls") {
                    AdBlockSearchAndAddSection(
                        description = "添加可信的 HTTPS 纯文本订阅；只有点击刷新时才会下载规则",
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editorRequest = KiyoriAdBlockEditorRequest.Subscription(null)
                        },
                    )
                }
                item(key = "ad_block_subscription_list") {
                    val query = searchQuery.trim()
                    val filtered =
                        state.subscriptions.filter { subscription ->
                            subscription.name.contains(query, ignoreCase = true) ||
                                subscription.url.contains(query, ignoreCase = true)
                        }
                    AdBlockManagedListSection(
                        title = "订阅列表",
                        description = "共 ${state.subscriptions.size} 个订阅，规则仅在启用时生效",
                    ) {
                        filtered.forEachIndexed { index, subscription ->
                            AdBlockManagedRow(
                                title = subscription.name,
                                description = subscriptionDescription(subscription),
                                enabled = subscription.enabled,
                                onToggle = {
                                    commitMutation("toggle ad-block subscription") {
                                        store.setSubscriptionEnabled(
                                            subscription.id,
                                            !subscription.enabled,
                                        )
                                    }
                                },
                                onRefresh = {
                                    if (refreshingSubscriptionId == null) {
                                        refreshingSubscriptionId = subscription.id
                                        scope.launch {
                                            val result =
                                                store.refreshSubscription(subscription.id)
                                            refreshingSubscriptionId = null
                                            result.fold(
                                                onSuccess = {
                                                    applyRuntimeRules()
                                                    Toast.makeText(
                                                        context,
                                                        "订阅“${subscription.name}”已刷新",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                                onFailure = { error ->
                                                    AppLogger.e(
                                                        "KiyoriAdBlockSettings",
                                                        "Failed to refresh subscription ${subscription.id}",
                                                        error,
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        error.message
                                                            ?.takeIf(String::isNotBlank)
                                                            ?: "订阅刷新失败",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                            )
                                        }
                                    }
                                },
                                refreshing = refreshingSubscriptionId == subscription.id,
                                onEdit = {
                                    editorRequest =
                                        KiyoriAdBlockEditorRequest.Subscription(subscription)
                                },
                                onDelete = {
                                    deleteRequest =
                                        KiyoriAdBlockDeleteRequest.Subscription(subscription)
                                },
                            )
                            if (index != filtered.lastIndex) KiyoriSettingsDivider()
                        }
                        if (filtered.isEmpty()) {
                            AdBlockEmptyRow("没有符合条件的广告拦截订阅")
                        }
                    }
                }
            }
        }
    }

    editorRequest?.let { request ->
        KiyoriAdBlockEditorDialog(
            request = request,
            onDismiss = { editorRequest = null },
            onSaveNetworkRule = { existing, value ->
                if (
                    commitMutation("save network rule") {
                        store.addOrUpdateNetworkRule(
                            id = existing?.id,
                            rule = value,
                            enabled = existing?.enabled ?: true,
                        )
                    }
                ) {
                    editorRequest = null
                }
            },
            onSaveElementRule = { existing, domain, selector ->
                if (
                    commitMutation("save element rule") {
                        store.addOrUpdateElementRule(
                            id = existing?.id,
                            domain = domain,
                            selector = selector,
                            enabled = existing?.enabled ?: true,
                        )
                    }
                ) {
                    editorRequest = null
                }
            },
            onSaveAllowlistDomain = { domain ->
                if (
                    commitMutation("save allowlist domain") {
                        store.addAllowlistedDomain(domain)
                    }
                ) {
                    editorRequest = null
                }
            },
            onSaveSubscription = { existing, name, url ->
                if (
                    commitMutation("save ad-block subscription") {
                        store.addOrUpdateSubscription(
                            id = existing?.id,
                            name = name,
                            url = url,
                            enabled = existing?.enabled ?: true,
                        )
                    }
                ) {
                    editorRequest = null
                }
            },
        )
    }

    deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            title = { Text(request.title) },
            text = {
                Text(
                    when (request) {
                        is KiyoriAdBlockDeleteRequest.NetworkRule -> request.rule.rule
                        is KiyoriAdBlockDeleteRequest.ElementRule ->
                            "${request.rule.domain}\n${request.rule.selector}"
                        is KiyoriAdBlockDeleteRequest.AllowlistDomain -> request.domain
                        is KiyoriAdBlockDeleteRequest.Subscription ->
                            "${request.subscription.name}\n${request.subscription.url}"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val removed =
                            commitMutation("delete ad-block entry") {
                                when (request) {
                                    is KiyoriAdBlockDeleteRequest.NetworkRule ->
                                        store.removeNetworkRule(request.rule.id)
                                    is KiyoriAdBlockDeleteRequest.ElementRule ->
                                        store.removeElementRule(request.rule.id)
                                    is KiyoriAdBlockDeleteRequest.AllowlistDomain ->
                                        store.removeAllowlistedDomain(request.domain)
                                    is KiyoriAdBlockDeleteRequest.Subscription ->
                                        store.removeSubscription(request.subscription.id)
                                }
                            }
                        if (removed) deleteRequest = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRequest = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun KiyoriAdBlockOverviewStatus(
    state: BrowserAdBlockState,
    onToggleEnabled: () -> Unit,
) {
    KiyoriSettingsGroupSection(
        title = "拦截状态",
        description = "请求级网址过滤与页面元素隐藏共用此总开关",
    ) {
        KiyoriSettingsRow(
            title = "启用广告拦截",
            description =
                if (state.enabled) {
                    "正在对普通浏览器窗口应用已启用规则"
                } else {
                    "规则与订阅仍会保留，但不执行拦截"
                },
            kind = KiyoriSettingsRowKind.TOGGLE,
            icon = Icons.Filled.Block,
            iconTone = KiyoriSemanticTone.RED,
            checked = state.enabled,
            onClick = onToggleEnabled,
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "当前进程已拦截",
            value = state.blockedRequestCount.toString(),
            description = "从本次应用进程启动后累计的请求数量",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "生效中的网址规则",
            value = state.activeNetworkRuleCount.toString(),
            description = "自定义规则与已启用订阅的有效网址规则合计",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "生效中的元素规则",
            value = state.activeElementRuleCount.toString(),
            description = "自定义规则与已启用订阅的元素隐藏规则合计",
        )
    }
}

@Composable
private fun AdBlockMetricRow(
    title: String,
    value: String,
    description: String,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = value,
            color = colors.accent,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AdBlockSearchAndAddSection(
    description: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    KiyoriSettingsGroupSection(
        title = "查找与新增",
        description = description,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            singleLine = true,
            label = { Text("搜索") },
            trailingIcon = {
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "新增",
                    )
                }
            },
        )
    }
}

@Composable
private fun AdBlockManagedListSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    KiyoriSettingsGroupSection(
        title = title,
        description = description,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun AdBlockManagedRow(
    title: String,
    description: String,
    enabled: Boolean? = null,
    codeStyle: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (codeStyle) FontFamily.Monospace else FontFamily.Default,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = if (codeStyle) FontFamily.Monospace else FontFamily.Default,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        enabled?.let { checked ->
            Switch(
                checked = checked,
                onCheckedChange = { onToggle?.invoke() },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = colors.accentContent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.cardBackground,
                        uncheckedTrackColor = colors.disabledTrack,
                    ),
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        onRefresh?.let { refresh ->
            IconButton(onClick = refresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = colors.mutedIcon,
                    )
                }
            }
        }
        onEdit?.let { edit ->
            IconButton(onClick = edit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = colors.mutedIcon,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AdBlockEmptyRow(message: String) {
    val colors = LocalKiyoriSettingsColors.current
    Text(
        text = message,
        color = colors.secondaryText,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
    )
}

@Composable
private fun KiyoriAdBlockEditorDialog(
    request: KiyoriAdBlockEditorRequest,
    onDismiss: () -> Unit,
    onSaveNetworkRule: (BrowserAdBlockCustomNetworkRule?, String) -> Unit,
    onSaveElementRule: (BrowserAdBlockCustomElementRule?, String, String) -> Unit,
    onSaveAllowlistDomain: (String) -> Unit,
    onSaveSubscription: (BrowserAdBlockSubscription?, String, String) -> Unit,
) {
    when (request) {
        is KiyoriAdBlockEditorRequest.NetworkRule -> {
            var value by remember(request.rule?.id) {
                mutableStateOf(request.rule?.rule.orEmpty())
            }
            AdBlockEditorDialogFrame(
                title = if (request.rule == null) "新增网址过滤规则" else "编辑网址过滤规则",
                description = "输入请求地址匹配规则。例外规则以 @@ 开头。",
                confirmEnabled = value.trim().isNotEmpty(),
                onDismiss = onDismiss,
                onConfirm = { onSaveNetworkRule(request.rule, value) },
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("规则") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
            }
        }

        is KiyoriAdBlockEditorRequest.ElementRule -> {
            var domain by remember(request.rule?.id) {
                mutableStateOf(request.rule?.domain.orEmpty())
            }
            var selector by remember(request.rule?.id) {
                mutableStateOf(request.rule?.selector.orEmpty())
            }
            AdBlockEditorDialogFrame(
                title = if (request.rule == null) "新增网页元素规则" else "编辑网页元素规则",
                description = "域名决定规则作用范围，CSS selector 决定隐藏的页面节点。",
                confirmEnabled = domain.trim().isNotEmpty() && selector.trim().isNotEmpty(),
                onDismiss = onDismiss,
                onConfirm = { onSaveElementRule(request.rule, domain, selector) },
            ) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("站点域名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = selector,
                    onValueChange = { selector = it },
                    label = { Text("CSS selector") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
            }
        }

        KiyoriAdBlockEditorRequest.AllowlistDomain -> {
            var domain by remember { mutableStateOf("") }
            AdBlockEditorDialogFrame(
                title = "添加站点白名单",
                description = "输入主机名，不需要协议和路径。",
                confirmEnabled = domain.trim().isNotEmpty(),
                onDismiss = onDismiss,
                onConfirm = { onSaveAllowlistDomain(domain) },
            ) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("站点域名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        is KiyoriAdBlockEditorRequest.Subscription -> {
            var name by remember(request.subscription?.id) {
                mutableStateOf(request.subscription?.name.orEmpty())
            }
            var url by remember(request.subscription?.id) {
                mutableStateOf(request.subscription?.url.orEmpty())
            }
            AdBlockEditorDialogFrame(
                title =
                    if (request.subscription == null) {
                        "新增广告拦截订阅"
                    } else {
                        "编辑广告拦截订阅"
                    },
                description = "保存只记录订阅地址；返回列表后点击刷新才会下载规则。",
                confirmEnabled = name.trim().isNotEmpty() && url.trim().isNotEmpty(),
                onDismiss = onDismiss,
                onConfirm = {
                    onSaveSubscription(request.subscription, name, url)
                },
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("HTTPS 订阅地址") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }
        }
    }
}

@Composable
private fun AdBlockEditorDialogFrame(
    title: String,
    description: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                fields()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun subscriptionDescription(subscription: BrowserAdBlockSubscription): String {
    val updated =
        subscription.lastUpdatedAt?.let { timestamp ->
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(timestamp))
        } ?: "尚未刷新"
    val ruleCount = subscription.networkRules.size + subscription.elementRules.size
    val status =
        subscription.lastError?.takeIf(String::isNotBlank)?.let { error ->
            "错误：$error"
        } ?: "更新时间：$updated"
    return "${subscription.url}\n规则 $ruleCount 条 · 忽略 ${subscription.ignoredLineCount} 行\n$status"
}
