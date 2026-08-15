package com.ai.assistance.operit.ui.main.shell

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockCustomElementRule
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockCustomNetworkRule
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockRuntimePhase
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockRuntimeStatus
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockState
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockSubscription
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockSubscriptionGroup
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockSubscriptionRefreshOrigin
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockSubscriptionRefreshProgress
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.browserAdBlockSubscriptionRefreshErrorMessage
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
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
    val state by store.state.collectAsState()
    val runtimeStatus by store.runtimeStatus.collectAsState()
    val refreshingSubscriptionIds by store.refreshingSubscriptionIds.collectAsState()
    val subscriptionRefreshProgress by store.subscriptionRefreshProgress.collectAsState()
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(KiyoriAdBlockSettingsSection.OVERVIEW) }
    var searchQuery by remember(section) { mutableStateOf("") }
    var editorRequest by remember { mutableStateOf<KiyoriAdBlockEditorRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<KiyoriAdBlockDeleteRequest?>(null) }
    var expandedElementDomains by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedSubscriptionIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun commitMutation(
        action: String,
        mutation: () -> Unit,
    ): Boolean {
        if (!runtimeStatus.ready) {
            Toast.makeText(
                context,
                browserAdBlockRuntimeStatusDescription(runtimeStatus),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        return try {
            mutation()
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
    }

    fun refreshSubscription(subscription: BrowserAdBlockSubscription) {
        if (!runtimeStatus.ready) {
            Toast.makeText(
                context,
                browserAdBlockRuntimeStatusDescription(runtimeStatus),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (subscription.id in refreshingSubscriptionIds || subscriptionRefreshProgress != null) {
            return
        }
        scope.launch {
            store.refreshSubscription(subscription.id).fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        "订阅“${subscription.name}”已更新",
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
                        browserAdBlockSubscriptionRefreshErrorMessage(error),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    fun refreshAllSubscriptions() {
        if (!runtimeStatus.ready) {
            Toast.makeText(
                context,
                browserAdBlockRuntimeStatusDescription(runtimeStatus),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (subscriptionRefreshProgress != null || refreshingSubscriptionIds.isNotEmpty()) {
            return
        }
        scope.launch {
            try {
                val summary = store.refreshAllEnabledSubscriptions()
                val message =
                    if (summary.failedCount == 0) {
                        "已更新 ${summary.refreshedCount} 个订阅"
                    } else {
                        "更新完成：成功 ${summary.refreshedCount}，失败 ${summary.failedCount}"
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "KiyoriAdBlockSettings",
                    "Failed to refresh all ad-block subscriptions",
                    error,
                )
                Toast.makeText(context, "订阅更新失败", Toast.LENGTH_SHORT).show()
            }
        }
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
    val subscriptionBatchActionsEnabled =
        runtimeStatus.ready && subscriptionRefreshProgress == null

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
                        runtimeStatus = runtimeStatus,
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
                    val proSubscriptions =
                        state.subscriptions.filter { subscription ->
                            subscription.group == BrowserAdBlockSubscriptionGroup.PRO
                        }
                    val adblockPlusSubscriptions =
                        state.subscriptions.filter { subscription ->
                            subscription.group == BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS
                        }
                    KiyoriSettingsGroupSection(
                        title = "内置拦截方案",
                        description = "两组规则共用同一运行时；已开启不等于已就绪，首次缺少本地规则时会立即同步",
                    ) {
                        KiyoriSettingsRow(
                            title = "广告拦截器 Pro",
                            description = "My AdFilters、AdGuard 中文过滤器、AdRules Lite",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.Public,
                            iconTone = KiyoriSemanticTone.BLUE,
                            value = subscriptionGroupSummary(proSubscriptions),
                            onClick = {
                                section = KiyoriAdBlockSettingsSection.SUBSCRIPTIONS
                            },
                        )
                        KiyoriSettingsDivider()
                        KiyoriSettingsRow(
                            title = "Adblock Plus",
                            description = "EasyList China + EasyList 与可接受广告例外规则",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.Shield,
                            iconTone = KiyoriSemanticTone.GREEN,
                            value = subscriptionGroupSummary(adblockPlusSubscriptions),
                            onClick = {
                                section = KiyoriAdBlockSettingsSection.SUBSCRIPTIONS
                            },
                        )
                        KiyoriSettingsDivider()
                        KiyoriSettingsRow(
                            title = "订阅与更新",
                            description = "自动更新、全部刷新、逐条状态和自定义订阅",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.Refresh,
                            iconTone = KiyoriSemanticTone.PURPLE,
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
                    val groupedRules =
                        filtered
                            .groupBy(BrowserAdBlockCustomElementRule::domain)
                            .toSortedMap()
                    AdBlockManagedListSection(
                        title = "按域名整理",
                        description =
                            "共 ${state.customElementRules.map { it.domain }.distinct().size} 个域名、" +
                                "${state.customElementRules.size} 条规则",
                    ) {
                        groupedRules.entries.forEachIndexed { groupIndex, (domain, rules) ->
                            val expanded =
                                query.isNotBlank() || domain in expandedElementDomains
                            AdBlockElementDomainHeaderRow(
                                domain = domain,
                                enabledRuleCount = rules.count { rule -> rule.enabled },
                                totalRuleCount = rules.size,
                                expanded = expanded,
                                onClick = {
                                    expandedElementDomains =
                                        if (expanded) {
                                            expandedElementDomains - domain
                                        } else {
                                            expandedElementDomains + domain
                                        }
                                },
                            )
                            if (expanded) {
                                rules.forEachIndexed { ruleIndex, rule ->
                                    KiyoriSettingsDivider()
                                    AdBlockManagedRow(
                                        title = rule.selector,
                                        description = "手动元素规则",
                                        enabled = rule.enabled,
                                        codeStyle = true,
                                        modifier = Modifier.padding(start = 14.dp),
                                        onToggle = {
                                            commitMutation("toggle element rule") {
                                                store.setElementRuleEnabled(
                                                    rule.id,
                                                    !rule.enabled,
                                                )
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
                                    if (ruleIndex == rules.lastIndex && groupIndex != groupedRules.size - 1) {
                                        KiyoriSettingsDivider()
                                    }
                                }
                            } else if (groupIndex != groupedRules.size - 1) {
                                KiyoriSettingsDivider()
                            }
                        }
                        if (groupedRules.isEmpty()) {
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
                item(key = "ad_block_subscription_dashboard") {
                    AdBlockSubscriptionDashboard(
                        state = state,
                        runtimeStatus = runtimeStatus,
                        refreshProgress = subscriptionRefreshProgress,
                    )
                }
                item(key = "ad_block_subscription_controls") {
                    KiyoriSettingsGroupSection(
                        title = "订阅更新",
                        description = "首次缺少本地规则时自动同步；手动更新只在完整下载并解析后替换当前版本",
                    ) {
                        KiyoriSettingsRow(
                            title = "自动更新内置订阅",
                            description = "应用启动时检查已启用且到期的内置订阅，开启新订阅时立即同步",
                            kind = KiyoriSettingsRowKind.TOGGLE,
                            icon = Icons.Filled.Refresh,
                            iconTone = KiyoriSemanticTone.BLUE,
                            checked = state.autoUpdateBuiltInSubscriptions,
                            onClick = {
                                commitMutation("toggle built-in subscription updates") {
                                    store.setAutoUpdateBuiltInSubscriptions(
                                        !state.autoUpdateBuiltInSubscriptions,
                                    )
                                }
                            },
                        )
                        KiyoriSettingsDivider()
                        KiyoriSettingsRow(
                            title =
                                if (subscriptionRefreshProgress != null) {
                                    refreshProgressTitle(subscriptionRefreshProgress)
                                } else {
                                    "更新已启用订阅"
                                },
                            description =
                                subscriptionRefreshProgress?.let { progress ->
                                    "已完成 ${progress.completedCount}/${progress.totalCount}，" +
                                        "成功 ${progress.refreshedCount}，失败 ${progress.failedCount}"
                                } ?: "按顺序下载并验证当前已启用的内置与自定义订阅",
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Filled.Public,
                            iconTone = KiyoriSemanticTone.PURPLE,
                            value =
                                subscriptionRefreshProgress?.let { progress ->
                                    "${progress.completedCount}/${progress.totalCount}"
                                },
                            enabled = subscriptionBatchActionsEnabled,
                            onClick = ::refreshAllSubscriptions,
                        )
                    }
                }
                item(key = "ad_block_subscription_search") {
                    AdBlockSearchAndAddSection(
                        description = "搜索名称或地址；加号用于添加自定义 HTTP(S) 纯文本订阅",
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editorRequest = KiyoriAdBlockEditorRequest.Subscription(null)
                        },
                    )
                }
                item(key = "ad_block_subscription_pro") {
                    val query = searchQuery.trim()
                    val subscriptions =
                        state.subscriptions.filter { subscription ->
                            subscription.group == BrowserAdBlockSubscriptionGroup.PRO
                        }
                    AdBlockSubscriptionGroupSection(
                        title = "广告拦截器 Pro",
                        description = "三条高覆盖中文与通用过滤列表",
                        subscriptions = subscriptions,
                        query = query,
                        group = BrowserAdBlockSubscriptionGroup.PRO,
                        refreshingSubscriptionIds = refreshingSubscriptionIds,
                        refreshProgress = subscriptionRefreshProgress,
                        expandedSubscriptionIds = expandedSubscriptionIds,
                        actionsEnabled = subscriptionBatchActionsEnabled,
                        onToggleGroup = { enabled ->
                            commitMutation("toggle Pro subscriptions") {
                                store.setSubscriptionGroupEnabled(
                                    BrowserAdBlockSubscriptionGroup.PRO,
                                    enabled,
                                )
                            }
                        },
                        onToggleSubscription = { subscription ->
                            commitMutation("toggle ad-block subscription") {
                                store.setSubscriptionEnabled(
                                    subscription.id,
                                    !subscription.enabled,
                                )
                            }
                        },
                        onRefreshSubscription = ::refreshSubscription,
                        onToggleExpanded = { id ->
                            expandedSubscriptionIds =
                                if (id in expandedSubscriptionIds) {
                                    expandedSubscriptionIds - id
                                } else {
                                    expandedSubscriptionIds + id
                                }
                        },
                    )
                }
                item(key = "ad_block_subscription_abp") {
                    val query = searchQuery.trim()
                    val subscriptions =
                        state.subscriptions.filter { subscription ->
                            subscription.group == BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS
                        }
                    AdBlockSubscriptionGroupSection(
                        title = "Adblock Plus",
                        description = "EasyList 中国规则与可接受广告例外规则",
                        subscriptions = subscriptions,
                        query = query,
                        group = BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS,
                        refreshingSubscriptionIds = refreshingSubscriptionIds,
                        refreshProgress = subscriptionRefreshProgress,
                        expandedSubscriptionIds = expandedSubscriptionIds,
                        actionsEnabled = subscriptionBatchActionsEnabled,
                        onToggleGroup = { enabled ->
                            commitMutation("toggle Adblock Plus subscriptions") {
                                store.setSubscriptionGroupEnabled(
                                    BrowserAdBlockSubscriptionGroup.ADBLOCK_PLUS,
                                    enabled,
                                )
                            }
                        },
                        onToggleSubscription = { subscription ->
                            commitMutation("toggle ad-block subscription") {
                                store.setSubscriptionEnabled(
                                    subscription.id,
                                    !subscription.enabled,
                                )
                            }
                        },
                        onRefreshSubscription = ::refreshSubscription,
                        onToggleExpanded = { id ->
                            expandedSubscriptionIds =
                                if (id in expandedSubscriptionIds) {
                                    expandedSubscriptionIds - id
                                } else {
                                    expandedSubscriptionIds + id
                                }
                        },
                    )
                }
                item(key = "ad_block_subscription_custom") {
                    val query = searchQuery.trim()
                    val subscriptions =
                        state.subscriptions.filter { subscription ->
                            subscription.group == BrowserAdBlockSubscriptionGroup.CUSTOM &&
                                (
                                    subscription.name.contains(query, ignoreCase = true) ||
                                        subscription.url.contains(query, ignoreCase = true)
                                    )
                        }
                    AdBlockManagedListSection(
                        title = "自定义订阅",
                        description = "可添加、改名、换址、启停、刷新或删除",
                    ) {
                        subscriptions.forEachIndexed { index, subscription ->
                            AdBlockSubscriptionRow(
                                subscription = subscription,
                                expanded = subscription.id in expandedSubscriptionIds,
                                refreshing = subscription.id in refreshingSubscriptionIds,
                                refreshProgress = subscriptionRefreshProgress,
                                actionsEnabled = subscriptionBatchActionsEnabled,
                                onToggle = {
                                    commitMutation("toggle custom ad-block subscription") {
                                        store.setSubscriptionEnabled(
                                            subscription.id,
                                            !subscription.enabled,
                                        )
                                    }
                                },
                                onRefresh = {
                                    refreshSubscription(subscription)
                                },
                                onToggleExpanded = {
                                    expandedSubscriptionIds =
                                        if (subscription.id in expandedSubscriptionIds) {
                                            expandedSubscriptionIds - subscription.id
                                        } else {
                                            expandedSubscriptionIds + subscription.id
                                        }
                                },
                                onEdit = {
                                    editorRequest =
                                        KiyoriAdBlockEditorRequest.Subscription(subscription)
                                },
                                onDelete = {
                                    deleteRequest =
                                        KiyoriAdBlockDeleteRequest.Subscription(subscription)
                                },
                            )
                            if (index != subscriptions.lastIndex) {
                                KiyoriSettingsDivider()
                            }
                        }
                        if (subscriptions.isEmpty()) {
                            AdBlockEmptyRow("没有符合条件的自定义订阅")
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
                var savedSubscription: BrowserAdBlockSubscription? = null
                if (
                    commitMutation("save ad-block subscription") {
                        savedSubscription =
                            store.addOrUpdateSubscription(
                                id = existing?.id,
                                name = name,
                                url = url,
                                enabled = existing?.enabled ?: true,
                            )
                    }
                ) {
                    editorRequest = null
                    val saved = savedSubscription
                    if (saved != null && (existing == null || existing.url != saved.url)) {
                        refreshSubscription(saved)
                    }
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
    runtimeStatus: BrowserAdBlockRuntimeStatus,
    onToggleEnabled: () -> Unit,
) {
    KiyoriSettingsGroupSection(
        title = "拦截状态",
        description = "请求级网址过滤与页面元素隐藏共用此总开关",
    ) {
        KiyoriSettingsRow(
            title = "启用广告拦截",
            description =
                if (!runtimeStatus.ready) {
                    browserAdBlockRuntimeStatusDescription(runtimeStatus)
                } else if (!state.enabled) {
                    "规则与订阅仍会保留，但不执行拦截"
                } else if (state.activeNetworkRuleCount + state.activeElementRuleCount > 0) {
                    "规则引擎已就绪，请求线程直接读取不可变快照"
                } else {
                    "拦截已开启，等待已启用订阅完成同步"
                },
            kind = KiyoriSettingsRowKind.TOGGLE,
            icon = Icons.Filled.Block,
            iconTone = KiyoriSemanticTone.RED,
            checked = state.enabled,
            enabled = runtimeStatus.ready,
            onClick = onToggleEnabled,
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "本地规则引擎",
            value = browserAdBlockRuntimeStatusValue(runtimeStatus),
            description = browserAdBlockRuntimeStatusDescription(runtimeStatus),
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "当前进程已拦截",
            value = state.blockedRequestCount.toString(),
            description = "从本次应用进程启动后累计的请求数量",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "已启用的网址规则",
            value = state.activeNetworkRuleCount.toString(),
            description =
                "阻断 ${state.activeNetworkRuleCount} 条，例外 ${state.activeNetworkExceptionRuleCount} 条；总开关关闭时不执行",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "已启用的元素规则",
            value = state.activeElementRuleCount.toString(),
            description =
                "隐藏 ${state.activeElementRuleCount} 条，例外 ${state.activeElementExceptionRuleCount} 条；总开关关闭时不执行",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "内置订阅就绪",
            value = "${state.readyBuiltInSubscriptionCount}/5",
            description = "已完成本地同步；是否参与拦截仍由各组与单条开关决定",
        )
    }
}

@Composable
private fun AdBlockSubscriptionDashboard(
    state: BrowserAdBlockState,
    runtimeStatus: BrowserAdBlockRuntimeStatus,
    refreshProgress: BrowserAdBlockSubscriptionRefreshProgress?,
) {
    val enabledCount = state.subscriptions.count(BrowserAdBlockSubscription::enabled)
    val readyCount = state.subscriptions.count(BrowserAdBlockSubscription::hasLocalRules)
    val activeRuleCount =
        state.subscriptions
            .filter(BrowserAdBlockSubscription::enabled)
            .sumOf(BrowserAdBlockSubscription::effectiveRuleCount)
    KiyoriSettingsGroupSection(
        title = "订阅状态",
        description = "先看规则是否已就绪，再决定是否启用；开关开启但未同步时不会产生拦截效果",
    ) {
        AdBlockMetricRow(
            title = "运行时状态",
            value = browserAdBlockRuntimeStatusValue(runtimeStatus),
            description = browserAdBlockRuntimeStatusDescription(runtimeStatus),
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "已启用订阅",
            value = "$enabledCount/${state.subscriptions.size}",
            description = "参与统一广告拦截运行时的订阅数量",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "已就绪订阅",
            value = "$readyCount/${state.subscriptions.size}",
            description = "已经完成下载、解析并拥有本地规则载荷",
        )
        KiyoriSettingsDivider()
        AdBlockMetricRow(
            title = "当前生效规则",
            value = formatAdBlockRuleCount(activeRuleCount),
            description = "已启用订阅的阻断、例外和网页元素规则总数",
        )
        refreshProgress?.let { progress ->
            val currentName =
                progress.currentSubscriptionId?.let { id ->
                    state.subscriptions
                        .singleOrNull { subscription -> subscription.id == id }
                        ?.name
                } ?: "准备中"
            KiyoriSettingsDivider()
            AdBlockMetricRow(
                title = refreshProgressTitle(progress),
                value = "${progress.completedCount}/${progress.totalCount}",
                description =
                    "成功 ${progress.refreshedCount} · 失败 ${progress.failedCount}" +
                        " · 正在处理 $currentName",
            )
        }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("搜索") },
            )
            TextButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "新增",
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun AdBlockSubscriptionGroupSection(
    title: String,
    description: String,
    subscriptions: List<BrowserAdBlockSubscription>,
    query: String,
    group: BrowserAdBlockSubscriptionGroup,
    refreshingSubscriptionIds: Set<String>,
    refreshProgress: BrowserAdBlockSubscriptionRefreshProgress?,
    expandedSubscriptionIds: Set<String>,
    actionsEnabled: Boolean,
    onToggleGroup: (Boolean) -> Unit,
    onToggleSubscription: (BrowserAdBlockSubscription) -> Unit,
    onRefreshSubscription: (BrowserAdBlockSubscription) -> Unit,
    onToggleExpanded: (String) -> Unit,
) {
    val enabledCount = subscriptions.count(BrowserAdBlockSubscription::enabled)
    val readyCount = subscriptions.count(BrowserAdBlockSubscription::hasLocalRules)
    val allEnabled = subscriptions.isNotEmpty() && enabledCount == subscriptions.size
    val visibleSubscriptions =
        subscriptions.filter { subscription ->
            subscription.name.contains(query, ignoreCase = true) ||
                subscription.url.contains(query, ignoreCase = true)
        }
    AdBlockManagedListSection(
        title = title,
        description = description,
    ) {
        KiyoriSettingsRow(
            title = "启用整组",
            description =
                "已开启 $enabledCount / ${subscriptions.size} · " +
                    "已就绪 $readyCount / ${subscriptions.size}",
            kind = KiyoriSettingsRowKind.TOGGLE,
            icon = Icons.Filled.Shield,
            iconTone =
                if (group == BrowserAdBlockSubscriptionGroup.PRO) {
                    KiyoriSemanticTone.BLUE
                } else {
                    KiyoriSemanticTone.GREEN
                },
            checked = allEnabled,
            enabled = subscriptions.isNotEmpty() && actionsEnabled,
            onClick = {
                onToggleGroup(!allEnabled)
            },
        )
        if (subscriptions.isNotEmpty()) {
            KiyoriSettingsDivider()
        }
        visibleSubscriptions.forEachIndexed { index, subscription ->
            AdBlockSubscriptionRow(
                subscription = subscription,
                expanded = subscription.id in expandedSubscriptionIds,
                refreshing = subscription.id in refreshingSubscriptionIds,
                refreshProgress = refreshProgress,
                actionsEnabled = actionsEnabled,
                onToggle = {
                    onToggleSubscription(subscription)
                },
                onRefresh = {
                    onRefreshSubscription(subscription)
                },
                onToggleExpanded = {
                    onToggleExpanded(subscription.id)
                },
            )
            if (index != visibleSubscriptions.lastIndex) {
                KiyoriSettingsDivider()
            }
        }
        if (visibleSubscriptions.isEmpty()) {
            AdBlockEmptyRow("没有符合条件的内置订阅")
        }
    }
}

private enum class AdBlockSubscriptionVisualStatus {
    DISABLED,
    WAITING,
    READY,
    UPDATING,
    ERROR,
}

@Composable
private fun AdBlockSubscriptionRow(
    subscription: BrowserAdBlockSubscription,
    expanded: Boolean,
    refreshing: Boolean,
    refreshProgress: BrowserAdBlockSubscriptionRefreshProgress?,
    actionsEnabled: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExpanded: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = LocalKiyoriSettingsColors.current
    val visualStatus =
        when {
            refreshing -> AdBlockSubscriptionVisualStatus.UPDATING
            !subscription.enabled -> AdBlockSubscriptionVisualStatus.DISABLED
            subscription.lastError != null -> AdBlockSubscriptionVisualStatus.ERROR
            subscription.hasLocalRules -> AdBlockSubscriptionVisualStatus.READY
            else -> AdBlockSubscriptionVisualStatus.WAITING
        }
    val statusText =
        when (visualStatus) {
            AdBlockSubscriptionVisualStatus.DISABLED ->
                if (subscription.hasLocalRules) {
                    "已停用 · ${formatAdBlockRuleCount(subscription.effectiveRuleCount)} 条规则"
                } else {
                    "已停用 · 尚未同步"
                }
            AdBlockSubscriptionVisualStatus.WAITING -> "等待首次同步"
            AdBlockSubscriptionVisualStatus.READY ->
                "已就绪 · ${formatAdBlockRuleCount(subscription.effectiveRuleCount)} 条规则"
            AdBlockSubscriptionVisualStatus.UPDATING ->
                refreshProgress?.let { progress ->
                    "正在同步 · ${progress.completedCount + 1}/${progress.totalCount}"
                } ?: "正在同步"
            AdBlockSubscriptionVisualStatus.ERROR ->
                if (subscription.hasLocalRules) {
                    "更新失败 · ${formatAdBlockRuleCount(subscription.effectiveRuleCount)} 条规则仍可用"
                } else {
                    "更新失败 · 展开查看原因"
                }
        }
    val statusColor =
        when (visualStatus) {
            AdBlockSubscriptionVisualStatus.ERROR -> MaterialTheme.colorScheme.error
            AdBlockSubscriptionVisualStatus.UPDATING -> MaterialTheme.colorScheme.tertiary
            AdBlockSubscriptionVisualStatus.READY -> colors.accent
            AdBlockSubscriptionVisualStatus.DISABLED -> colors.secondaryText
            AdBlockSubscriptionVisualStatus.WAITING -> colors.secondaryText
        }
    val statusIcon =
        when (visualStatus) {
            AdBlockSubscriptionVisualStatus.ERROR -> Icons.Filled.ErrorOutline
            AdBlockSubscriptionVisualStatus.UPDATING -> Icons.Filled.HourglassEmpty
            AdBlockSubscriptionVisualStatus.READY -> Icons.Filled.CheckCircle
            AdBlockSubscriptionVisualStatus.DISABLED -> Icons.Filled.Shield
            AdBlockSubscriptionVisualStatus.WAITING -> Icons.Filled.HourglassEmpty
        }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onToggleExpanded)
                        .padding(end = 8.dp),
            ) {
                Text(
                    text = subscription.name,
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
            Switch(
                checked = subscription.enabled,
                onCheckedChange = { onToggle() },
                enabled = actionsEnabled && !refreshing,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = colors.accentContent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.cardBackground,
                        uncheckedTrackColor = colors.disabledTrack,
                    ),
                modifier = Modifier.width(48.dp),
            )
            IconButton(
                onClick = onRefresh,
                enabled = actionsEnabled && !refreshing,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "更新订阅",
                        tint = colors.mutedIcon,
                    )
                }
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起详情" else "展开详情",
                    tint = colors.mutedIcon,
                )
            }
        }
        if (expanded) {
            AdBlockSubscriptionDetails(
                subscription = subscription,
                actionsEnabled = actionsEnabled && !refreshing,
                onRefresh = onRefresh,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun AdBlockSubscriptionDetails(
    subscription: BrowserAdBlockSubscription,
    actionsEnabled: Boolean,
    onRefresh: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = "订阅地址",
            color = colors.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
        )
        Text(
            text = subscription.url,
            color = colors.primaryText,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = colors.divider)
        Row(modifier = Modifier.fillMaxWidth()) {
            AdBlockSubscriptionRuleStat(
                title = "网址阻断",
                value = subscription.networkBlockingRuleCount,
                modifier = Modifier.weight(1f),
            )
            AdBlockSubscriptionRuleStat(
                title = "网址例外",
                value = subscription.networkExceptionRuleCount,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            AdBlockSubscriptionRuleStat(
                title = "元素隐藏",
                value = subscription.elementBlockingRuleCount,
                modifier = Modifier.weight(1f),
            )
            AdBlockSubscriptionRuleStat(
                title = "元素例外",
                value = subscription.elementExceptionRuleCount,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "忽略 ${subscription.ignoredLineCount} 行 · ${subscription.lastUpdatedAt?.let(::formatAdBlockTime) ?: "尚未成功更新"}",
            color = colors.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        subscription.lastError?.takeIf(String::isNotBlank)?.let { error ->
            Text(
                text = "错误：$error",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onRefresh, enabled = actionsEnabled) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Text("立即更新", modifier = Modifier.padding(start = 3.dp))
            }
            onEdit?.let { edit ->
                TextButton(onClick = edit, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Text("编辑", modifier = Modifier.padding(start = 3.dp))
                }
            }
            onDelete?.let { delete ->
                TextButton(onClick = delete, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdBlockSubscriptionRuleStat(
    title: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = title,
            color = colors.secondaryText,
            fontSize = 11.sp,
        )
        Text(
            text = formatAdBlockRuleCount(value),
            color = colors.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
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
private fun AdBlockElementDomainHeaderRow(
    domain: String,
    enabledRuleCount: Int,
    totalRuleCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = domain,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "已启用 $enabledRuleCount / 共 $totalRuleCount 条",
                color = colors.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "收起规则" else "展开规则",
            tint = colors.mutedIcon,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AdBlockManagedRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean? = null,
    codeStyle: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
) {
    val colors = LocalKiyoriSettingsColors.current
    val placeActionsOnSecondLine =
        enabled != null && (onEdit != null || onDelete != null)
    val rowActionsEnabled = actionsEnabled && !refreshing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    enabled = rowActionsEnabled,
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
            if (!placeActionsOnSecondLine) {
                AdBlockManagedActionButtons(
                    onRefresh = onRefresh,
                    refreshing = refreshing,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    enabled = rowActionsEnabled,
                )
            }
        }
        if (placeActionsOnSecondLine) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdBlockManagedActionButtons(
                    onRefresh = onRefresh,
                    refreshing = refreshing,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    enabled = rowActionsEnabled,
                )
            }
        }
    }
}

@Composable
private fun AdBlockManagedActionButtons(
    onRefresh: (() -> Unit)?,
    refreshing: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    enabled: Boolean,
) {
    val colors = LocalKiyoriSettingsColors.current
    onRefresh?.let { refresh ->
        IconButton(onClick = refresh, enabled = enabled) {
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
        IconButton(onClick = edit, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "编辑",
                tint = colors.mutedIcon,
            )
        }
    }
    onDelete?.let { delete ->
        IconButton(onClick = delete, enabled = enabled) {
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
                description = "保存只记录订阅地址；返回列表后点击刷新才会下载自定义规则。",
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
                    label = { Text("HTTP(S) 订阅地址") },
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

private fun subscriptionGroupSummary(
    subscriptions: List<BrowserAdBlockSubscription>,
): String {
    val readyCount = subscriptions.count(BrowserAdBlockSubscription::hasLocalRules)
    return "$readyCount/${subscriptions.size} 就绪"
}

private fun browserAdBlockRuntimeStatusValue(
    status: BrowserAdBlockRuntimeStatus,
): String =
    when (status.phase) {
        BrowserAdBlockRuntimePhase.READING_SETTINGS -> "读取中"
        BrowserAdBlockRuntimePhase.COMPILING_RULES ->
            "${status.completedSubscriptionCount}/${status.totalSubscriptionCount}"
        BrowserAdBlockRuntimePhase.READY -> "已就绪"
        BrowserAdBlockRuntimePhase.FAILED -> "失败"
    }

private fun browserAdBlockRuntimeStatusDescription(
    status: BrowserAdBlockRuntimeStatus,
): String =
    when (status.phase) {
        BrowserAdBlockRuntimePhase.READING_SETTINGS ->
            "正在后台读取广告拦截设置，页面和浏览器请求不会等待"
        BrowserAdBlockRuntimePhase.COMPILING_RULES ->
            "正在后台编译本地规则 ${status.completedSubscriptionCount}/${status.totalSubscriptionCount}，完成后一次性启用"
        BrowserAdBlockRuntimePhase.READY ->
            "本地规则已编译完成，浏览器请求直接读取不可变快照"
        BrowserAdBlockRuntimePhase.FAILED ->
            status.errorMessage ?: "广告拦截规则初始化失败"
    }

private fun refreshProgressTitle(
    progress: BrowserAdBlockSubscriptionRefreshProgress?,
): String =
    when (progress?.origin) {
        BrowserAdBlockSubscriptionRefreshOrigin.AUTOMATIC -> "正在自动同步"
        BrowserAdBlockSubscriptionRefreshOrigin.ACTIVATION -> "正在同步新启用订阅"
        BrowserAdBlockSubscriptionRefreshOrigin.MANUAL_ALL -> "正在更新已启用订阅"
        null -> "正在更新订阅"
    }

private fun formatAdBlockRuleCount(value: Int): String =
    NumberFormat.getIntegerInstance().format(value)

private fun formatAdBlockTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
