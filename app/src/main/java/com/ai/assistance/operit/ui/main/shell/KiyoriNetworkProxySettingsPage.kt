package com.ai.assistance.operit.ui.main.shell

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager as ToolPackageManager
import com.ai.assistance.operit.ui.features.packages.screens.ScriptPackageCatalogEntry
import com.ai.assistance.operit.ui.features.packages.screens.buildScriptPackageCatalog
import com.ai.assistance.operit.ui.features.packages.screens.resolvePackageCategoryVisual
import com.ai.assistance.operit.ui.features.packages.screens.toImageVector
import com.ai.assistance.operit.ui.common.copyPlainTextToClipboard
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.platform.logging.KiyoriLogger
import com.kiyori.platform.network.KiyoriMihomoProbePhase
import com.kiyori.platform.network.KiyoriMihomoRuntimePhase
import com.kiyori.platform.network.KiyoriNetworkConnectionMode
import com.kiyori.platform.network.KiyoriNetworkErrorCode
import com.kiyori.platform.network.KiyoriNetworkException
import com.kiyori.platform.network.KiyoriNetworkOverrideMode
import com.kiyori.platform.network.KiyoriNetworkProxyConfig
import com.kiyori.platform.network.KiyoriNetworkProxyLogEntry
import com.kiyori.platform.network.KiyoriNetworkProxyLogLevel
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import com.kiyori.platform.network.KiyoriNetworkProxyPolicy
import com.kiyori.platform.network.KiyoriNetworkProxyRule
import com.kiyori.platform.network.KiyoriNetworkProxyStoreState
import com.kiyori.platform.network.KiyoriNetworkRuleMode
import com.kiyori.platform.network.KiyoriNetworkRuleType
import com.kiyori.platform.network.KiyoriNetworkSettingsAppliedException
import com.kiyori.platform.network.KiyoriProxySubscription
import com.kiyori.platform.network.KiyoriSubscriptionSourceType
import com.kiyori.platform.network.MihomoConfigSanitizer
import com.kiyori.platform.network.MihomoNodeTestResult
import com.kiyori.platform.network.MihomoNodeTestStatus
import com.kiyori.platform.network.MihomoProxyGroupSummary
import com.kiyori.platform.network.MihomoRuntimeGroupState
import com.kiyori.platform.network.MihomoRuntimeNodeState
import com.kiyori.platform.network.isKiyoriNetworkRulePatternValid
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "KiyoriNetworkProxyPage"
internal const val NETWORK_PROXY_NODE_SELECTION_TITLE = "节点选择"
internal const val NETWORK_PROXY_HEADER_ACTION_WIDTH_DP = 96

private enum class NetworkProxyPageSection(
    val title: String,
) {
    OVERVIEW("网络代理"),
    CURRENT_NODE(NETWORK_PROXY_NODE_SELECTION_TITLE),
    SUBSCRIPTIONS("订阅管理"),
    RULES("规则管理"),
    SCRIPTS("脚本规则"),
    LOGS("代理日志"),
}

internal enum class NetworkProxyNodeSort(
    val label: String,
) {
    DEFAULT("默认"),
    NAME("名称"),
    DELAY("延迟"),
}

private enum class NetworkProxyOperationArea {
    STATUS,
    SUBSCRIPTIONS,
    ROUTING,
    GROUPS,
    SCRIPTS,
    LOGS,
    ADVANCED,
}

private data class NetworkProxyOperation(
    val id: String,
    val area: NetworkProxyOperationArea,
    val label: String,
)

private data class NetworkProxyFeedback(
    val area: NetworkProxyOperationArea,
    val message: String,
    val isError: Boolean,
)

private data class NetworkProxyModeOption(
    val label: String,
    val description: String,
    val mode: KiyoriNetworkOverrideMode,
)

private sealed interface SubscriptionEditor {
    data object AddUrl : SubscriptionEditor

    data class Edit(val subscriptionId: String) : SubscriptionEditor
}

private sealed interface YamlImportTarget {
    data object Add : YamlImportTarget

    data class Replace(val subscriptionId: String) : YamlImportTarget
}

private sealed interface ModeSelection {
    data class Script(
        val packageName: String,
        val displayName: String,
    ) : ModeSelection
}

private sealed interface CustomRuleEditor {
    data object Add : CustomRuleEditor

    data class Edit(val ruleId: String) : CustomRuleEditor
}

private data class SubscriptionRuleEditor(
    val subscriptionId: String,
    val ruleIndex: Int,
)

private data class SelectedYaml(
    val displayName: String,
    val text: String,
)

private val networkProxyModeOptions =
    listOf(
        NetworkProxyModeOption("跟随模式", "使用上方代理模式", KiyoriNetworkOverrideMode.INHERIT),
        NetworkProxyModeOption("直连", "绕过 Kiyori 内嵌 Mihomo；仍受系统 VPN 影响", KiyoriNetworkOverrideMode.DIRECT),
        NetworkProxyModeOption("代理", "经过内嵌 Mihomo，并使用上方代理模式", KiyoriNetworkOverrideMode.PROXY),
    )

@Composable
internal fun KiyoriNetworkProxySettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { KiyoriNetworkProxyManager.getInstance(context) }
    val storeState by manager.configState.collectAsState()
    val runtimeState by manager.runtimeState.collectAsState()
    val probeState by manager.probeState.collectAsState()
    val proxyLogs by manager.logEntries.collectAsState()
    val config = (storeState as? KiyoriNetworkProxyStoreState.Ready)?.config
    val activeSubscription = config?.let(KiyoriNetworkProxyPolicy::activeSubscription)

    var activeOperation by remember { mutableStateOf<NetworkProxyOperation?>(null) }
    var feedback by remember { mutableStateOf<NetworkProxyFeedback?>(null) }
    var subscriptionEditor by remember { mutableStateOf<SubscriptionEditor?>(null) }
    var editorName by remember { mutableStateOf("") }
    var editorUrl by remember { mutableStateOf("") }
    var editorUrlVisible by remember { mutableStateOf(false) }
    var yamlImportTarget by remember { mutableStateOf<YamlImportTarget?>(null) }
    var subscriptionActionsId by remember { mutableStateOf<String?>(null) }
    var deleteSubscriptionId by remember { mutableStateOf<String?>(null) }
    var resetDialogVisible by remember { mutableStateOf(false) }
    var clearLogDialogVisible by remember { mutableStateOf(false) }
    var pendingLogExportText by remember { mutableStateOf<String?>(null) }
    var pendingModeSelection by remember { mutableStateOf<ModeSelection?>(null) }
    var discoveredScriptCatalog by remember {
        mutableStateOf<List<ScriptPackageCatalogEntry>>(emptyList())
    }
    var scriptCatalogRefreshing by remember { mutableStateOf(false) }
    var customRuleEditor by remember { mutableStateOf<CustomRuleEditor?>(null) }
    var customRulePattern by remember { mutableStateOf("") }
    var customRuleType by remember { mutableStateOf(KiyoriNetworkRuleType.DOMAIN) }
    var customRuleMode by remember { mutableStateOf(KiyoriNetworkRuleMode.PROXY) }
    var customRuleTypeMenuVisible by remember { mutableStateOf(false) }
    var deleteCustomRuleId by remember { mutableStateOf<String?>(null) }
    var subscriptionRuleEditor by remember { mutableStateOf<SubscriptionRuleEditor?>(null) }
    var subscriptionRuleText by remember { mutableStateOf("") }
    var pageSection by remember { mutableStateOf(NetworkProxyPageSection.OVERVIEW) }
    var selectedGroupTabName by remember { mutableStateOf<String?>(null) }
    var nodeSearchQuery by remember { mutableStateOf("") }
    var nodeSort by remember { mutableStateOf(NetworkProxyNodeSort.DEFAULT) }
    var nodeSortMenuVisible by remember { mutableStateOf(false) }
    var ruleSearchQuery by remember { mutableStateOf("") }

    val controlsEnabled = config != null && activeOperation == null
    val activeRuntimeGroups =
        runtimeState.groups.takeIf { groups -> runtimeState.subscriptionId == activeSubscription?.id }
            .orEmpty()
    val activeProxySelection =
        activeSubscription?.let { subscription ->
            resolveActiveProxySelection(subscription, activeRuntimeGroups)
        }

    val sectionSubscription = activeSubscription
    val sectionGroups = sectionSubscription?.let(::subscriptionGroups).orEmpty()
    val selectedGroupTab =
        sectionGroups.firstOrNull { group -> group.name == selectedGroupTabName }
            ?: sectionGroups.firstOrNull()

    BackHandler(enabled = pageSection != NetworkProxyPageSection.OVERVIEW) {
        pageSection = NetworkProxyPageSection.OVERVIEW
    }

    LaunchedEffect(pageSection) {
        nodeSortMenuVisible = false
        nodeSearchQuery = ""
        ruleSearchQuery = ""
    }

    LaunchedEffect(sectionSubscription?.id) {
        selectedGroupTabName = null
        nodeSearchQuery = ""
    }

    fun refreshScriptCatalog() {
        if (scriptCatalogRefreshing) return
        scope.launch {
            scriptCatalogRefreshing = true
            try {
                val (packages, enabledPackageNames) = withContext(Dispatchers.IO) {
                        val packageManager =
                            ToolPackageManager.getInstance(
                                context,
                                AIToolHandler.getInstance(context),
                            )
                        val available = packageManager.getAvailablePackages(forceRefresh = true)
                        val installedScripts =
                            available.filterKeys { packageName ->
                                !packageManager.isToolPkgContainer(packageName) &&
                                    !packageManager.isToolPkgSubpackage(packageName)
                            }
                        val currentConfig = manager.currentConfig()
                        val staleRules = currentConfig.scriptModes.keys - installedScripts.keys
                        if (staleRules.isNotEmpty()) {
                            manager.updateConfig { config ->
                                config.copy(scriptModes = config.scriptModes - staleRules)
                            }
                        }
                        installedScripts to packageManager.getEnabledPackageNames().toSet()
                    }
                discoveredScriptCatalog =
                    buildScriptPackageCatalog(
                        packages = packages,
                        enabledPackageNames = enabledPackageNames,
                        context = context,
                    )
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Failed to load installed script packages", error)
                feedback =
                    NetworkProxyFeedback(
                        NetworkProxyOperationArea.SCRIPTS,
                        "脚本清单读取失败，请稍后重试。",
                        isError = true,
                    )
            } finally {
                scriptCatalogRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshScriptCatalog()
    }

    fun runOperation(
        operation: NetworkProxyOperation,
        successMessage: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (activeOperation != null) return
        scope.launch {
            activeOperation = operation
            feedback = null
            try {
                block()
                feedback = NetworkProxyFeedback(operation.area, successMessage, isError = false)
                onSuccess()
            } catch (error: KiyoriNetworkSettingsAppliedException) {
                feedback =
                    NetworkProxyFeedback(
                        operation.area,
                        "设置已保存，但代理运行未生效：${networkProxyUserMessage(error.runtimeFailure)}",
                        isError = true,
                    )
                onSuccess()
            } catch (error: KiyoriNetworkException) {
                feedback =
                    NetworkProxyFeedback(
                        operation.area,
                        networkProxyUserMessage(error),
                        isError = true,
                    )
                KiyoriLogger.e(TAG, "Proxy operation ${operation.id} failed with ${error.code.name}")
            } catch (error: Exception) {
                feedback =
                    NetworkProxyFeedback(
                        operation.area,
                        unexpectedOperationMessage(operation.area),
                        isError = true,
                    )
                KiyoriLogger.e(
                    TAG,
                    "Proxy operation ${operation.id} failed with ${error::class.java.simpleName}",
                )
            } finally {
                activeOperation = null
            }
        }
    }

    fun openSubscriptionEditor(editor: SubscriptionEditor) {
        feedback = null
        subscriptionEditor = editor
        editorUrlVisible = false
        when (editor) {
            SubscriptionEditor.AddUrl -> {
                editorName = ""
                editorUrl = ""
            }
            is SubscriptionEditor.Edit -> {
                val subscription = config?.subscriptions?.firstOrNull { it.id == editor.subscriptionId }
                editorName = subscription?.displayName.orEmpty()
                editorUrl = subscription?.subscriptionUrl.orEmpty()
            }
        }
    }

    fun openCustomRuleEditor(editor: CustomRuleEditor) {
        customRuleEditor = editor
        when (editor) {
            CustomRuleEditor.Add -> {
                customRulePattern = ""
                customRuleType = KiyoriNetworkRuleType.DOMAIN
                customRuleMode = KiyoriNetworkRuleMode.PROXY
            }
            is CustomRuleEditor.Edit -> {
                val rule = config?.customRules?.firstOrNull { it.id == editor.ruleId }
                customRulePattern = rule?.pattern.orEmpty()
                customRuleType = rule?.type ?: KiyoriNetworkRuleType.DOMAIN
                customRuleMode = rule?.mode ?: KiyoriNetworkRuleMode.PROXY
            }
        }
    }

    fun openSubscriptionRuleEditor(subscriptionId: String, ruleIndex: Int, rawRule: String) {
        subscriptionRuleEditor = SubscriptionRuleEditor(subscriptionId, ruleIndex)
        subscriptionRuleText = rawRule
    }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val target = yamlImportTarget
            yamlImportTarget = null
            if (uri == null || target == null) return@rememberLauncherForActivityResult
            val operation = NetworkProxyOperation("yaml_import", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在读取、校验并保存 YAML")
            runOperation(
                operation = operation,
                successMessage = if (target is YamlImportTarget.Add) "YAML 已加入订阅库。" else "本地订阅已更新。",
            ) {
                val selected = withContext(Dispatchers.IO) { readNetworkProxyYaml(context, uri) }
                when (target) {
                    YamlImportTarget.Add -> {
                        manager.addLocalYaml(selected.text, selected.displayName)
                    }
                    is YamlImportTarget.Replace -> {
                        manager.replaceLocalSubscription(target.subscriptionId, selected.text, selected.displayName)
                    }
                }
            }
        }

    val logExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            val text = pendingLogExportText
            pendingLogExportText = null
            if (uri == null || text == null) return@rememberLauncherForActivityResult
            runOperation(
                NetworkProxyOperation("export_log", NetworkProxyOperationArea.LOGS, "正在导出代理日志"),
                "代理日志已导出。",
            ) {
                withContext(Dispatchers.IO) { writeNetworkProxyLog(context, uri, text) }
            }
        }

    key(pageSection) {
        KiyoriCollapsingSettingsPage(
            title = pageSection.title,
            onBack = {
                if (pageSection == NetworkProxyPageSection.OVERVIEW) onBack() else pageSection = NetworkProxyPageSection.OVERVIEW
            },
            modifier = modifier,
            headerActionWidth = NETWORK_PROXY_HEADER_ACTION_WIDTH_DP.dp,
            headerAction = {
                if (
                    pageSection == NetworkProxyPageSection.CURRENT_NODE &&
                        sectionSubscription != null &&
                        selectedGroupTab != null
                ) {
                    val headerSubscription = sectionSubscription
                    val headerGroup = selectedGroupTab
                    Row {
                        IconButton(
                            enabled = controlsEnabled,
                            onClick = {
                                runOperation(
                                    NetworkProxyOperation("test_group_header", NetworkProxyOperationArea.GROUPS, "正在测试 ${groupDisplayName(headerGroup.name)}"),
                                    "${groupDisplayName(headerGroup.name)} 测速完成。",
                                ) { manager.testSubscriptionGroup(headerSubscription.id, headerGroup.name) }
                            },
                        ) {
                            if (activeOperation?.id == "test_group_header") {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = "测速当前分组")
                            }
                        }
                        Box {
                            IconButton(
                                enabled = controlsEnabled,
                                onClick = { nodeSortMenuVisible = true },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "节点排序")
                            }
                            DropdownMenu(
                                expanded = nodeSortMenuVisible,
                                onDismissRequest = { nodeSortMenuVisible = false },
                            ) {
                                NetworkProxyNodeSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            nodeSort = option
                                            nodeSortMenuVisible = false
                                        },
                                        trailingIcon = {
                                            if (nodeSort == option) {
                                                Icon(Icons.Default.Check, contentDescription = "当前排序")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (pageSection == NetworkProxyPageSection.LOGS) {
                    Row {
                        IconButton(
                            enabled = proxyLogs.isNotEmpty() && activeOperation == null,
                            onClick = {
                                runOperation(
                                    NetworkProxyOperation("copy_log", NetworkProxyOperationArea.LOGS, "正在复制代理日志"),
                                    "代理日志已复制。",
                                ) {
                                    context.copyPlainTextToClipboard("Kiyori 代理日志", manager.exportLogText())
                                }
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制代理日志")
                        }
                        IconButton(
                            enabled = proxyLogs.isNotEmpty() && activeOperation == null,
                            onClick = {
                                pendingLogExportText = manager.exportLogText()
                                val timestamp =
                                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                                logExportLauncher.launch("kiyori-network-proxy-$timestamp.txt")
                            },
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "导出代理日志")
                        }
                    }
                }
                if (pageSection == NetworkProxyPageSection.SCRIPTS) {
                    IconButton(
                        enabled = controlsEnabled && !scriptCatalogRefreshing,
                        onClick = { refreshScriptCatalog() },
                    ) {
                        if (scriptCatalogRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新脚本")
                        }
                    }
                }
            },
        ) {
        if (pageSection == NetworkProxyPageSection.OVERVIEW) {
            item(key = "network_proxy_overview_status") {
                KiyoriSettingsGroupSection(
                    title = "应用内代理",
                    description = "只影响 Kiyori 进程内已接入模块；不会改变其他应用的网络。",
                ) {
                    KiyoriSettingsRow(
                        title = "启用应用内代理",
                        description = networkProxyRuntimeDescription(config, runtimeState.phase, activeSubscription),
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.VpnKey,
                        iconTone = KiyoriSemanticTone.CYAN,
                        checked = config?.enabled == true,
                        enabled = controlsEnabled,
                        onClick = {
                            val nextEnabled = config?.enabled != true
                            runOperation(
                                NetworkProxyOperation("toggle_enabled", NetworkProxyOperationArea.STATUS, "正在保存并应用代理状态"),
                                if (nextEnabled) "应用内代理已开启。" else "应用内代理已关闭。",
                            ) { manager.updateConfig { current -> current.copy(enabled = nextEnabled) } }
                        },
                    )
                    KiyoriSettingsDivider()
                    Text(
                        text = "代理模式",
                        color = LocalKiyoriSettingsColors.current.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp),
                    )
                    Text(
                        text = "规则按域名细分；全局让 Kiyori 请求统一进入当前策略组；直连不经过内嵌 Mihomo。",
                        color = LocalKiyoriSettingsColors.current.secondaryText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp, end = 18.dp),
                    )
                    NetworkProxySegmentedMode(
                        selected = config?.defaultMode ?: KiyoriNetworkConnectionMode.DIRECT,
                        enabled = controlsEnabled,
                        onSelect = { mode ->
                            runOperation(
                                NetworkProxyOperation("default_mode", NetworkProxyOperationArea.ROUTING, "正在保存代理模式"),
                                "代理模式已保存。",
                            ) { manager.updateConfig { it.copy(defaultMode = mode) } }
                        },
                    )
                    NetworkProxyOperationFeedback(NetworkProxyOperationArea.STATUS, activeOperation, feedback, runtimeState.phase == KiyoriMihomoRuntimePhase.ERROR)
                    NetworkProxyOperationFeedback(NetworkProxyOperationArea.ROUTING, activeOperation, feedback, false)
                }
            }
            item(key = "network_proxy_overview_connections") {
                KiyoriSettingsGroupSection(
                    title = "连接范围",
                    description = "进入对应子页面管理节点选择、订阅、规则和脚本规则。",
                ) {
                    val nodeSelectionDescription =
                        activeProxySelection?.let { selection ->
                            buildString {
                                append("当前：")
                                append(selection.selectedItemName ?: selection.chain.lastOrNull() ?: "未选择")
                                if (selection.chain.size > 1) {
                                    append(" · ")
                                    append(selection.chain.dropLast(1).joinToString(" → "))
                                }
                            }
                        } ?: "选择当前订阅中的策略组和节点"
                    KiyoriSettingsRow(
                        title = NETWORK_PROXY_NODE_SELECTION_TITLE,
                        description = nodeSelectionDescription,
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Speed,
                        iconTone = KiyoriSemanticTone.GREEN,
                        enabled = controlsEnabled,
                        onClick = {
                            selectedGroupTabName = activeProxySelection?.finalGroupName
                            pageSection = NetworkProxyPageSection.CURRENT_NODE
                        },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "订阅管理",
                        description = activeSubscription?.let(::subscriptionDescription) ?: "尚未导入 Clash / Mihomo 订阅",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Cloud,
                        iconTone = KiyoriSemanticTone.CYAN,
                        value = activeSubscription?.displayName,
                        enabled = controlsEnabled,
                        onClick = { pageSection = NetworkProxyPageSection.SUBSCRIPTIONS },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "规则管理",
                        description =
                            activeSubscription?.let { subscription ->
                                "订阅规则 ${subscription.summary.ruleCount} 条 · 自定义规则 ${config.customRules.size} 条"
                            } ?: "自定义规则 ${config?.customRules?.size ?: 0} 条；导入订阅后读取订阅规则",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        value = config?.customRules?.size?.takeIf { it > 0 }?.let { "$it 条自定义" },
                        enabled = controlsEnabled,
                        onClick = { pageSection = NetworkProxyPageSection.RULES },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "脚本规则",
                        description =
                            when {
                                scriptCatalogRefreshing -> "正在读取已安装脚本…"
                                discoveredScriptCatalog.isEmpty() -> "当前没有已安装的传统脚本"
                                else -> "已发现 ${discoveredScriptCatalog.size} 个脚本，已配置 ${config?.scriptModes?.size ?: 0} 个规则"
                            },
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.PURPLE,
                        value = config?.scriptModes?.size?.takeIf { it > 0 }?.let { "$it 个覆盖" },
                        enabled = controlsEnabled,
                        onClick = {
                            refreshScriptCatalog()
                            pageSection = NetworkProxyPageSection.SCRIPTS
                        },
                    )
                }
            }
            item(key = "network_proxy_overview_advanced") {
                KiyoriSettingsGroupSection(
                    title = "网络选项",
                    description = "直连仍可能经过 Android 系统 VPN；并存时 Kiyori Mihomo 位于应用请求链路内。",
                ) {
                    KiyoriSettingsRow(
                        title = "代理日志",
                        description =
                            if (proxyLogs.isEmpty()) {
                                "当前进程暂无代理日志"
                            } else {
                                "当前进程最近 ${proxyLogs.size} 条脱敏记录"
                            },
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Description,
                        iconTone = KiyoriSemanticTone.BLUE,
                        value = proxyLogs.lastOrNull()?.let { proxyLogLevelLabel(it.level) },
                        enabled = activeOperation == null,
                        onClick = { pageSection = NetworkProxyPageSection.LOGS },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "代理局域网地址",
                        description = "允许私有地址进入 Kiyori 内嵌代理",
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.ORANGE,
                        checked = config?.proxyPrivateNetworks == true,
                        enabled = controlsEnabled,
                        onClick = {
                            runOperation(
                                NetworkProxyOperation("private_networks", NetworkProxyOperationArea.ADVANCED, "正在保存局域网路由"),
                                "局域网路由设置已保存。",
                            ) { manager.updateConfig { it.copy(proxyPrivateNetworks = !it.proxyPrivateNetworks) } }
                        },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "允许与系统 VPN 并存",
                        description = "应用请求先经过 Kiyori Mihomo，再经过外部 Clash/VPN",
                        kind = KiyoriSettingsRowKind.TOGGLE,
                        icon = Icons.Default.VpnKey,
                        iconTone = KiyoriSemanticTone.RED,
                        checked = config?.allowConcurrentSystemVpn == true,
                        enabled = controlsEnabled,
                        onClick = {
                            runOperation(
                                NetworkProxyOperation("vpn_coexistence", NetworkProxyOperationArea.ADVANCED, "正在保存 VPN 并存设置"),
                                "VPN 并存设置已保存。",
                            ) { manager.updateConfig { it.copy(allowConcurrentSystemVpn = !it.allowConcurrentSystemVpn) } }
                        },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "重置网络代理",
                        description = "停止核心并删除订阅、模块规则和加密配置",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Delete,
                        iconTone = KiyoriSemanticTone.RED,
                        enabled = activeOperation == null,
                        onClick = { resetDialogVisible = true },
                    )
                    NetworkProxyOperationFeedback(NetworkProxyOperationArea.ADVANCED, activeOperation, feedback, false)
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.LOGS) {
            item(key = "network_proxy_logs") {
                KiyoriSettingsGroupSection(
                    title = "当前进程日志",
                    description = "最多保留最近 300 条。核心输出已遮蔽凭据、私有路径和订阅地址；复制或导出由你主动触发。",
                ) {
                    if (proxyLogs.isEmpty()) {
                        Text(
                            text = "暂无日志。导入、启用代理、切换节点或测速后，相关状态会显示在这里。",
                            color = LocalKiyoriSettingsColors.current.secondaryText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = networkProxyLogDisplayText(proxyLogs),
                                color = LocalKiyoriSettingsColors.current.primaryText,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                            )
                        }
                        KiyoriSettingsDivider()
                        TextButton(
                            enabled = activeOperation == null,
                            onClick = { clearLogDialogVisible = true },
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("清空日志")
                        }
                    }
                    NetworkProxyOperationFeedback(
                        NetworkProxyOperationArea.LOGS,
                        activeOperation,
                        feedback,
                        false,
                    )
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.CURRENT_NODE) {
            item(key = "network_proxy_current_node") {
                val subscription = sectionSubscription
                KiyoriSettingsGroupSection(
                    title = subscription?.displayName ?: NETWORK_PROXY_NODE_SELECTION_TITLE,
                    description = subscription?.let { "${subscriptionSourceLabel(it)} · ${subscriptionDescription(it)}" }
                        ?: "请先在订阅管理中导入并切换一份 Clash / Mihomo 订阅。",
                ) {
                    if (subscription == null) {
                        Text(
                            text = "当前没有可用订阅。",
                            color = LocalKiyoriSettingsColors.current.secondaryText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        OutlinedTextField(
                            value = nodeSearchQuery,
                            onValueChange = { nodeSearchQuery = it },
                            singleLine = true,
                            enabled = controlsEnabled,
                            placeholder = { Text("搜索当前分组节点") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (nodeSearchQuery.isBlank()) null else {
                                { IconButton(onClick = { nodeSearchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "清除搜索") } }
                            },
                            colors = kiyoriSettingsOutlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        NetworkProxyGroupTabs(
                            groups = sectionGroups,
                            selectedGroupName = selectedGroupTab?.name,
                            enabled = controlsEnabled,
                            onSelect = { group ->
                                selectedGroupTabName = group.name
                                nodeSearchQuery = ""
                            },
                        )
                        selectedGroupTab?.let { group ->
                            val runtimeMatches = runtimeState.subscriptionId == subscription.id
                            val runtimeGroups = runtimeState.groups.takeIf { runtimeMatches }.orEmpty()
                            val liveGroup = runtimeGroups.firstOrNull { it.name == group.name }
                            val currentItem = liveGroup?.currentItem ?: subscription.selectedGroupItems[group.name]
                            val allNodes = displayGroupNodes(subscription, group, runtimeState.nodes.takeIf { runtimeMatches }.orEmpty())
                            val query = nodeSearchQuery.trim().lowercase(Locale.ROOT)
                            val filteredNodes = allNodes.filter { node ->
                                query.isBlank() || node.name.lowercase(Locale.ROOT).contains(query) || node.type.lowercase(Locale.ROOT).contains(query)
                            }
                            val displayedNodes = sortNetworkProxyNodes(filteredNodes, nodeSort)
                            Text(
                                text = "${groupDisplayName(group.name)} · ${groupDescription(group, liveGroup)} · 已选择：${currentItem ?: "未选择"}",
                                color = LocalKiyoriSettingsColors.current.secondaryText,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            KiyoriSettingsDivider()
                            when {
                                allNodes.isEmpty() -> Text("当前分组没有可显示的节点。", color = LocalKiyoriSettingsColors.current.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
                                displayedNodes.isEmpty() -> Text("没有匹配当前搜索条件的节点。", color = LocalKiyoriSettingsColors.current.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
                                else -> NetworkProxyNodeList(
                                    nodes = displayedNodes,
                                    selectedNodeName = currentItem,
                                    selectable = group.manuallySelectable,
                                    enabled = controlsEnabled,
                                    onSelect = { node ->
                                         runOperation(NetworkProxyOperation("select_node", NetworkProxyOperationArea.GROUPS, "正在切换：${node.name}"), "已切换到 ${node.name}。") {
                                            manager.selectGroup(subscription.id, group.name, node.name)
                                        }
                                    },
                                    onTest = { node ->
                                         runOperation(NetworkProxyOperation("test_node", NetworkProxyOperationArea.GROUPS, "正在测速：${node.name}"), "${node.name} 测速完成。") {
                                            if (node.type == "策略组") {
                                                manager.testSubscriptionGroup(subscription.id, node.name)
                                            } else {
                                                manager.testSubscriptionNode(subscription.id, node.name)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    NetworkProxyOperationFeedback(NetworkProxyOperationArea.GROUPS, activeOperation, feedback, probeState.phase == KiyoriMihomoProbePhase.ERROR)
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.SUBSCRIPTIONS) {
            item(key = "network_proxy_subscriptions") {
                KiyoriSettingsGroupSection(
                    title = "订阅管理",
                    description = "保存多份 Clash / Mihomo 订阅；点击订阅行立即切换当前配置，右侧菜单管理条目。",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = controlsEnabled,
                            onClick = { openSubscriptionEditor(SubscriptionEditor.AddUrl) },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加订阅地址", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = controlsEnabled,
                            onClick = { yamlImportTarget = YamlImportTarget.Add; filePicker.launch(arrayOf("*/*")) },
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入 YAML 文件", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    val subscriptions = config?.subscriptions.orEmpty()
                    if (subscriptions.isEmpty()) {
                        Text("还没有订阅。可添加订阅地址或导入本地 YAML 文件。", color = LocalKiyoriSettingsColors.current.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
                    } else {
                        subscriptions.forEachIndexed { index, subscription ->
                            if (index > 0) KiyoriSettingsDivider()
                            NetworkProxySubscriptionRow(
                                subscription = subscription,
                                active = subscription.id == config?.activeSubscriptionId,
                                enabled = controlsEnabled,
                                menuExpanded = subscriptionActionsId == subscription.id,
                                onSelect = {
                                    if (subscription.id == config?.activeSubscriptionId) {
                                        feedback = NetworkProxyFeedback(NetworkProxyOperationArea.SUBSCRIPTIONS, "${subscription.displayName} 已是当前订阅。", false)
                                    } else {
                                        runOperation(NetworkProxyOperation("switch_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在切换当前订阅"), "当前订阅已切换为 ${subscription.displayName}。") {
                                            manager.switchActiveSubscription(subscription.id)
                                        }
                                    }
                                },
                                onOpenMenu = { subscriptionActionsId = subscription.id },
                                onDismissMenu = { subscriptionActionsId = null },
                                onUpdate = {
                                    subscriptionActionsId = null
                                    if (subscription.sourceType == KiyoriSubscriptionSourceType.URL) {
                                        runOperation(NetworkProxyOperation("update_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在下载、校验并更新订阅"), "${subscription.displayName} 已更新。") { manager.updateUrlSubscription(subscription.id) }
                                    } else {
                                        yamlImportTarget = YamlImportTarget.Replace(subscription.id)
                                        filePicker.launch(arrayOf("*/*"))
                                    }
                                },
                                onEdit = { subscriptionActionsId = null; openSubscriptionEditor(SubscriptionEditor.Edit(subscription.id)) },
                                onCopy = {
                                    subscriptionActionsId = null
                                    runOperation(NetworkProxyOperation("duplicate_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在复制订阅"), "已复制 ${subscription.displayName}。") {
                                        manager.duplicateSubscription(subscription.id, "${subscription.displayName} 副本")
                                    }
                                },
                                onDelete = { subscriptionActionsId = null; deleteSubscriptionId = subscription.id },
                            )
                        }
                    }
                    NetworkProxyOperationFeedback(NetworkProxyOperationArea.SUBSCRIPTIONS, activeOperation, feedback, false)
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.RULES) {
            item(key = "network_proxy_rules_search") {
                KiyoriSettingsGroupSection(
                    title = "规则管理",
                    description = "自定义规则优先于当前订阅规则；订阅更新会覆盖订阅规则编辑，不影响自定义规则。",
                ) {
                    OutlinedTextField(
                        value = ruleSearchQuery,
                        onValueChange = { ruleSearchQuery = it },
                        singleLine = true,
                        enabled = controlsEnabled,
                        placeholder = { Text("搜索规则类型、匹配内容或目标") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (ruleSearchQuery.isBlank()) null else {
                            { IconButton(onClick = { ruleSearchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "清除搜索") } }
                        },
                        colors = kiyoriSettingsOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            item(key = "network_proxy_custom_rules_header") {
                KiyoriSettingsGroupSection(
                    title = "自定义规则",
                    description = "由你创建并单独加密保存，始终排在订阅规则前。",
                ) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        enabled = controlsEnabled,
                        onClick = { openCustomRuleEditor(CustomRuleEditor.Add) },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加自定义规则", fontSize = 12.sp, maxLines = 1)
                    }
                    if (filteredCustomRules(config?.customRules.orEmpty(), ruleSearchQuery).isEmpty()) {
                        Text(
                            text = if (config?.customRules.isNullOrEmpty()) "暂无自定义规则。" else "没有匹配的自定义规则。",
                            color = LocalKiyoriSettingsColors.current.secondaryText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
            items(
                items = filteredCustomRules(config?.customRules.orEmpty(), ruleSearchQuery),
                key = KiyoriNetworkProxyRule::id,
            ) { rule ->
                KiyoriSettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    CustomRuleRow(
                        rule = rule,
                        enabled = controlsEnabled,
                        onEdit = { openCustomRuleEditor(CustomRuleEditor.Edit(rule.id)) },
                        onDelete = { deleteCustomRuleId = rule.id },
                        onToggle = {
                            runOperation(
                                NetworkProxyOperation("toggle_custom_rule", NetworkProxyOperationArea.ROUTING, "正在更新规则状态"),
                                "规则状态已更新。",
                            ) {
                                manager.updateCustomRule(rule.id, rule.pattern, rule.type, rule.mode, !rule.enabled)
                            }
                        },
                    )
                }
            }
            item(key = "network_proxy_subscription_rules_header") {
                KiyoriSettingsGroupSection(
                    title = "当前订阅规则",
                    description =
                        activeSubscription?.let {
                            "${it.displayName} · ${it.rules.size} 条；点击规则可编辑，更新订阅时覆盖。"
                        } ?: "尚未选择订阅。",
                ) {
                    if (filteredSubscriptionRules(activeSubscription?.rules.orEmpty(), ruleSearchQuery).isEmpty()) {
                        Text(
                            text = if (activeSubscription?.rules.isNullOrEmpty()) "当前订阅没有可执行规则。" else "没有匹配的订阅规则。",
                            color = LocalKiyoriSettingsColors.current.secondaryText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                    NetworkProxyOperationFeedback(NetworkProxyOperationArea.ROUTING, activeOperation, feedback, false)
                }
            }
            items(
                items = filteredSubscriptionRules(activeSubscription?.rules.orEmpty(), ruleSearchQuery),
                key = { indexedRule -> "${activeSubscription?.id}:${indexedRule.index}" },
            ) { indexedRule ->
                KiyoriSettingsGroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SubscriptionRuleRow(
                        rule = indexedRule.value,
                        enabled = controlsEnabled,
                        onEdit = {
                            activeSubscription?.let { subscription ->
                                openSubscriptionRuleEditor(subscription.id, indexedRule.index, indexedRule.value)
                            }
                        },
                    )
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.SCRIPTS) {
            if (discoveredScriptCatalog.isEmpty()) {
                item(key = "network_proxy_scripts_empty") {
                    KiyoriSettingsGroupSection(
                        title = "脚本规则",
                        description = "只显示已安装的传统 JsEngine 脚本，启用状态不影响识别；ToolPkg 统一归入 AI 工具。",
                    ) {
                        Text(
                            "当前没有已安装的传统脚本。",
                            color = LocalKiyoriSettingsColors.current.secondaryText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
            discoveredScriptCatalog
                .groupBy(ScriptPackageCatalogEntry::categoryKey)
                .forEach { (categoryKey, scriptsInCategory) ->
                    item(key = "network_proxy_scripts_category_$categoryKey") {
                        val categoryLabel = scriptsInCategory.first().categoryLabel
                        val categoryVisual = resolvePackageCategoryVisual(categoryLabel)
                        KiyoriSettingsGroupSection(
                            title = categoryLabel,
                            description = "${scriptsInCategory.size} 个已安装脚本；未启用脚本也可预先配置规则。",
                        ) {
                            scriptsInCategory.forEachIndexed { index, script ->
                                if (index > 0) KiyoriSettingsDivider()
                                val mode = config?.scriptModes?.get(script.packageName)
                                val scriptDescription =
                                    listOfNotNull(
                                        if (script.enabled) "已启用" else "未启用",
                                        script.packageName,
                                        script.description.takeIf(String::isNotBlank),
                                    ).joinToString(" · ")
                                KiyoriSettingsRow(
                                    title = script.displayName,
                                    description = scriptDescription,
                                    kind = KiyoriSettingsRowKind.NAVIGATION,
                                    icon = categoryVisual.icon.toImageVector(),
                                    iconTone = KiyoriSemanticTone.BLUE,
                                    value = networkProxyOverrideLabel(mode ?: KiyoriNetworkOverrideMode.INHERIT),
                                    enabled = controlsEnabled,
                                    onClick = {
                                        pendingModeSelection =
                                            ModeSelection.Script(script.packageName, script.displayName)
                                    },
                                )
                            }
                        }
                    }
                }
            item(key = "network_proxy_scripts_feedback") {
                NetworkProxyOperationFeedback(
                    NetworkProxyOperationArea.SCRIPTS,
                    activeOperation,
                    feedback,
                    false,
                )
            }
        }
        }
    }

    pendingModeSelection?.let { selection ->
        val currentOverride =
            when (selection) {
                is ModeSelection.Script -> config?.scriptModes?.get(selection.packageName) ?: KiyoriNetworkOverrideMode.INHERIT
            }
        KiyoriSettingsSelectionSheet(
            selection =
                KiyoriSettingsSelection(
                    title = "${selection.displayName}脚本规则",
                    currentValue = networkProxyOverrideLabel(currentOverride),
                    options =
                        networkProxyModeOptions.map { option ->
                            KiyoriSettingsSelectionOption(
                                label = option.label,
                                description =
                                    if (option.mode == KiyoriNetworkOverrideMode.INHERIT) {
                                        "移除此脚本的单独规则，跟随代理模式"
                                    } else {
                                        option.description
                                    },
                                selected = option.mode == currentOverride,
                                onSelect = {},
                            )
                        },
                ),
            onDismiss = { pendingModeSelection = null },
            onSelect = { option ->
                val selectedMode = networkProxyModeOptions.first { it.label == option.label }.mode
                runOperation(NetworkProxyOperation("script_rule", NetworkProxyOperationArea.SCRIPTS, "正在保存脚本规则"), "脚本规则已保存。") {
                    manager.updateConfig { current ->
                        current.copy(
                            scriptModes =
                                if (selectedMode == KiyoriNetworkOverrideMode.INHERIT) current.scriptModes - selection.packageName
                                else current.scriptModes + (selection.packageName to selectedMode),
                        )
                    }
                }
            },
        )
    }

    SubscriptionEditorDialog(
        editor = subscriptionEditor,
        config = config,
        name = editorName,
        url = editorUrl,
        urlVisible = editorUrlVisible,
        busy = activeOperation?.area == NetworkProxyOperationArea.SUBSCRIPTIONS,
        errorMessage = feedback?.takeIf { it.area == NetworkProxyOperationArea.SUBSCRIPTIONS && it.isError }?.message,
        onNameChange = { editorName = it },
        onUrlChange = { editorUrl = it },
        onToggleUrlVisibility = { editorUrlVisible = !editorUrlVisible },
        onDismiss = { if (activeOperation == null) subscriptionEditor = null },
        onConfirm = { editor ->
            val operation = NetworkProxyOperation("save_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在下载、校验并保存订阅")
            when (editor) {
                SubscriptionEditor.AddUrl ->
                    runOperation(operation, "订阅已加入订阅库。", onSuccess = { subscriptionEditor = null }) {
                        manager.addSubscriptionUrl(editorUrl, editorName)
                    }
                is SubscriptionEditor.Edit -> {
                    val subscription = config?.subscriptions?.firstOrNull { it.id == editor.subscriptionId }
                        ?: return@SubscriptionEditorDialog
                    runOperation(operation, "${editorName.trim()} 已保存。", onSuccess = { subscriptionEditor = null }) {
                        if (subscription.sourceType == KiyoriSubscriptionSourceType.URL) {
                            manager.editUrlSubscription(subscription.id, editorName, editorUrl)
                        } else {
                            manager.renameSubscription(subscription.id, editorName)
                        }
                    }
                }
            }
        },
    )

    customRuleEditor?.let { editor ->
        val editingRule =
            (editor as? CustomRuleEditor.Edit)?.let { selected ->
                config?.customRules?.firstOrNull { it.id == selected.ruleId }
            }
        val validPattern = isCustomRuleInputValid(customRulePattern, customRuleType)
        AlertDialog(
            onDismissRequest = { if (activeOperation == null) customRuleEditor = null },
            title = { Text(if (editor is CustomRuleEditor.Add) "添加自定义规则" else "编辑自定义规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customRulePattern,
                        onValueChange = { customRulePattern = it },
                        label = { Text("匹配内容") },
                        placeholder = { Text(customRulePatternExample(customRuleType)) },
                        singleLine = true,
                        isError = customRulePattern.isNotBlank() && !validPattern,
                        supportingText = {
                            Text(customRuleTypeDescription(customRuleType))
                        },
                    )
                    Text("规则类型", fontSize = 13.sp, color = LocalKiyoriSettingsColors.current.secondaryText)
                    Box {
                        OutlinedButton(
                            onClick = { customRuleTypeMenuVisible = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(customRuleTypeLabel(customRuleType), modifier = Modifier.weight(1f))
                            Icon(Icons.Default.MoreVert, contentDescription = "选择规则类型")
                        }
                        DropdownMenu(
                            expanded = customRuleTypeMenuVisible,
                            onDismissRequest = { customRuleTypeMenuVisible = false },
                        ) {
                            customRuleEditorTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(customRuleTypeLabel(type)) },
                                    onClick = {
                                        customRuleType = type
                                        customRuleTypeMenuVisible = false
                                    },
                                    trailingIcon = {
                                        if (customRuleType == type) {
                                            Icon(Icons.Default.Check, contentDescription = "当前类型")
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Text("匹配动作", fontSize = 13.sp, color = LocalKiyoriSettingsColors.current.secondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            KiyoriNetworkRuleMode.PROXY to "代理",
                            KiyoriNetworkRuleMode.DIRECT to "直连",
                        ).forEach { (mode, label) ->
                            val selected = customRuleMode == mode
                            TextButton(
                                onClick = { customRuleMode = mode },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = if (selected) "✓ $label" else label,
                                    color = if (selected) LocalKiyoriSettingsColors.current.accent else LocalKiyoriSettingsColors.current.primaryText,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validPattern && activeOperation == null,
                    onClick = {
                        val operation = NetworkProxyOperation("save_custom_rule", NetworkProxyOperationArea.ROUTING, "正在保存自定义规则")
                        runOperation(operation, "自定义规则已保存。", onSuccess = { customRuleEditor = null }) {
                            if (editingRule == null) {
                                manager.addCustomRule(customRulePattern, customRuleType, customRuleMode)
                            } else {
                                manager.updateCustomRule(editingRule.id, customRulePattern, customRuleType, customRuleMode, editingRule.enabled)
                            }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { customRuleEditor = null }, enabled = activeOperation == null) { Text("取消") }
            },
        )
    }

    subscriptionRuleEditor?.let { editor ->
        val subscription = config?.subscriptions?.firstOrNull { it.id == editor.subscriptionId }
        val ruleStillExists = editor.ruleIndex in subscription?.rules.orEmpty().indices
        AlertDialog(
            onDismissRequest = { if (activeOperation == null) subscriptionRuleEditor = null },
            title = { Text("编辑当前订阅规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${subscription?.displayName.orEmpty()} · 第 ${editor.ruleIndex + 1} 条",
                        color = LocalKiyoriSettingsColors.current.secondaryText,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = subscriptionRuleText,
                        onValueChange = { subscriptionRuleText = it },
                        label = { Text("Mihomo 规则") },
                        minLines = 2,
                        maxLines = 5,
                        isError = subscriptionRuleText.isBlank() || subscriptionRuleText.any(Char::isISOControl),
                        supportingText = { Text("保存时重新校验规则类型、匹配内容、目标和依赖；更新订阅会覆盖此编辑。") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        activeOperation == null &&
                            ruleStillExists &&
                            subscriptionRuleText.isNotBlank() &&
                            subscriptionRuleText.none(Char::isISOControl),
                    onClick = {
                        runOperation(
                            NetworkProxyOperation("save_subscription_rule", NetworkProxyOperationArea.ROUTING, "正在校验并保存订阅规则"),
                            "订阅规则已保存。",
                            onSuccess = { subscriptionRuleEditor = null },
                        ) {
                            manager.updateSubscriptionRule(
                                subscriptionId = editor.subscriptionId,
                                ruleIndex = editor.ruleIndex,
                                rawRule = subscriptionRuleText,
                            )
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    enabled = activeOperation == null,
                    onClick = { subscriptionRuleEditor = null },
                ) { Text("取消") }
            },
        )
    }

    deleteCustomRuleId?.let { ruleId ->
        val rule = config?.customRules?.firstOrNull { it.id == ruleId }
        AlertDialog(
            onDismissRequest = { deleteCustomRuleId = null },
            title = { Text("删除自定义规则？") },
            text = { Text(rule?.pattern.orEmpty()) },
            confirmButton = {
                TextButton(
                    enabled = activeOperation == null,
                    onClick = {
                        runOperation(
                            NetworkProxyOperation("delete_custom_rule", NetworkProxyOperationArea.ROUTING, "正在删除自定义规则"),
                            "自定义规则已删除。",
                            onSuccess = { deleteCustomRuleId = null },
                        ) { manager.removeCustomRule(ruleId) }
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteCustomRuleId = null }) { Text("取消") } },
        )
    }

    deleteSubscriptionId?.let { subscriptionId ->
        val subscription = config?.subscriptions?.firstOrNull { it.id == subscriptionId }
        if (subscription == null) {
            deleteSubscriptionId = null
        } else {
            AlertDialog(
                onDismissRequest = { deleteSubscriptionId = null },
                title = { Text("删除 ${subscription.displayName}？") },
                text = { Text("将删除该订阅的加密 YAML、策略选择和测速记录。正在使用时必须先切换或关闭代理。") },
                confirmButton = {
                    Button(onClick = {
                        deleteSubscriptionId = null
                        runOperation(NetworkProxyOperation("delete_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在删除订阅"), "订阅已删除。") {
                            manager.removeSubscription(subscription.id)
                        }
                    }) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { deleteSubscriptionId = null }) { Text("取消") } },
            )
        }
    }

    if (resetDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (activeOperation == null) resetDialogVisible = false },
            title = { Text("重置网络代理？") },
            text = { Text("这会停止内嵌 Mihomo，并删除全部订阅、脚本规则、自定义规则和加密代理配置。") },
            confirmButton = {
                Button(enabled = activeOperation == null, onClick = {
                    runOperation(NetworkProxyOperation("reset", NetworkProxyOperationArea.ADVANCED, "正在重置网络代理"), "网络代理设置已重置。", onSuccess = { resetDialogVisible = false }) {
                        manager.reset()
                    }
                }) { Text("重置") }
            },
            dismissButton = { TextButton(enabled = activeOperation == null, onClick = { resetDialogVisible = false }) { Text("取消") } },
        )
    }

    if (clearLogDialogVisible) {
        AlertDialog(
            onDismissRequest = { clearLogDialogVisible = false },
            title = { Text("清空代理日志？") },
            text = { Text("这会删除当前进程内的全部代理日志，不会修改订阅、节点或连接模式。") },
            confirmButton = {
                Button(
                    onClick = {
                        manager.clearLog()
                        clearLogDialogVisible = false
                        feedback =
                            NetworkProxyFeedback(
                                NetworkProxyOperationArea.LOGS,
                                "代理日志已清空。",
                                isError = false,
                            )
                    },
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearLogDialogVisible = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SubscriptionEditorDialog(
    editor: SubscriptionEditor?,
    config: KiyoriNetworkProxyConfig?,
    name: String,
    url: String,
    urlVisible: Boolean,
    busy: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onToggleUrlVisibility: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (SubscriptionEditor) -> Unit,
) {
    editor ?: return
    val existing =
        (editor as? SubscriptionEditor.Edit)?.let { target ->
            config?.subscriptions?.firstOrNull { it.id == target.subscriptionId }
        }
    val requiresUrl = editor is SubscriptionEditor.AddUrl || existing?.sourceType == KiyoriSubscriptionSourceType.URL
    val nameValid = name.isBlank() || name.trim().length <= KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH
    val urlValid = !requiresUrl || url.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                when {
                    editor is SubscriptionEditor.AddUrl -> "添加订阅地址"
                    requiresUrl -> "编辑订阅"
                    else -> "重命名订阅"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(if (editor is SubscriptionEditor.AddUrl) "名称（可选）" else "名称") },
                    isError = !nameValid,
                    supportingText = if (!nameValid) ({ Text("名称不能超过 ${KiyoriNetworkProxyConfig.MAX_SUBSCRIPTION_NAME_LENGTH} 个字符") }) else null,
                    colors = kiyoriSettingsOutlinedTextFieldColors(),
                )
                if (requiresUrl) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        singleLine = true,
                        enabled = !busy,
                        label = { Text("HTTPS 订阅 URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        visualTransformation = if (urlVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(enabled = !busy, onClick = onToggleUrlVisibility) {
                                Icon(
                                    if (urlVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (urlVisible) "隐藏订阅地址" else "显示订阅地址",
                                )
                            }
                        },
                        isError = url.isNotBlank() && !urlValid,
                        colors = kiyoriSettingsOutlinedTextFieldColors(),
                    )
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在下载、清洗、校验并加密保存", fontSize = 12.sp)
                    }
                }
                errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        },
        confirmButton = {
            Button(enabled = !busy && nameValid && urlValid, onClick = { onConfirm(editor) }) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (editor is SubscriptionEditor.AddUrl) "导入" else "保存")
                }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NetworkProxyOperationFeedback(
    area: NetworkProxyOperationArea,
    operation: NetworkProxyOperation?,
    feedback: NetworkProxyFeedback?,
    runtimeError: Boolean,
) {
    val active = operation?.takeIf { it.area == area }
    val result = feedback?.takeIf { it.area == area }
    if (active == null && result == null && !runtimeError) return
    HorizontalDivider(color = LocalKiyoriSettingsColors.current.divider)
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            active != null -> CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
            result?.isError == true || runtimeError -> Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            else -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LocalKiyoriSettingsColors.current.accent)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = active?.label ?: result?.message ?: "Mihomo 运行异常，请检查当前订阅与 VPN 设置。",
            color = if (result?.isError == true || runtimeError) MaterialTheme.colorScheme.error else LocalKiyoriSettingsColors.current.secondaryText,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

internal data class KiyoriActiveProxySelection(
    val chain: List<String>,
    val finalGroupName: String?,
    val selectedItemName: String?,
)

/**
 * Projects Mihomo's nested group selections into the one route the user actually cares about.
 * The root group can select another group, so showing only its `now` value is insufficient: the
 * terminal node must be resolved through the group graph and cycles must remain visible as an
 * unresolved route instead of being presented as a fabricated node.
 */
internal fun resolveActiveProxySelection(
    subscription: KiyoriProxySubscription,
    runtimeGroups: List<MihomoRuntimeGroupState>,
): KiyoriActiveProxySelection {
    val groups = subscriptionGroups(subscription).associateBy(MihomoProxyGroupSummary::name)
    val runtimeByName = runtimeGroups.associateBy(MihomoRuntimeGroupState::name)
    val chain = mutableListOf<String>()
    val visited = mutableSetOf<String>()
    var groupName: String? = MihomoConfigSanitizer.ROUTE_GROUP_NAME

    while (groupName != null) {
        if (!visited.add(groupName)) {
            return KiyoriActiveProxySelection(
                chain = chain + "循环引用",
                finalGroupName = null,
                selectedItemName = null,
            )
        }
        val group = groups[groupName] ?: break
        chain += groupDisplayName(group.name)
        val selectedItem =
            runtimeByName[group.name]?.currentItem?.takeIf(String::isNotBlank)
                ?: subscription.selectedGroupItems[group.name]?.takeIf(String::isNotBlank)
        if (selectedItem == null) {
            return KiyoriActiveProxySelection(
                chain = chain,
                finalGroupName = group.name,
                selectedItemName = null,
            )
        }
        val selectedGroup = groups[selectedItem]
        if (selectedGroup == null) {
            return KiyoriActiveProxySelection(
                chain = chain + selectedItem,
                finalGroupName = group.name,
                selectedItemName = selectedItem,
            )
        }
        groupName = selectedGroup.name
    }
    return KiyoriActiveProxySelection(
        chain = chain,
        finalGroupName = null,
        selectedItemName = null,
    )
}

private fun subscriptionGroups(subscription: KiyoriProxySubscription): List<MihomoProxyGroupSummary> =
    (listOf(rootGroupSummary(subscription)) + subscription.summary.groups)
        .distinctBy(MihomoProxyGroupSummary::name)

private fun rootGroupSummary(subscription: KiyoriProxySubscription): MihomoProxyGroupSummary =
    MihomoProxyGroupSummary(
        name = MihomoConfigSanitizer.ROUTE_GROUP_NAME,
        type = "select",
        options = subscription.summary.rootCandidates,
        providerNames = subscription.summary.providerNames.takeIf { subscription.summary.rootCandidates.isEmpty() }.orEmpty(),
        manuallySelectable = true,
    )

private fun displayNodes(
    subscription: KiyoriProxySubscription,
    runtimeNodes: List<MihomoRuntimeNodeState>,
): List<MihomoNodeTestResult> {
    val stored = subscription.nodeTests.associateBy(MihomoNodeTestResult::name).toMutableMap()
    subscription.summary.staticProxies.forEach { proxy ->
        stored.putIfAbsent(
            proxy.name,
            MihomoNodeTestResult(
                name = proxy.name,
                type = proxy.type,
                groupNames =
                    subscriptionGroups(subscription)
                        .filter { group -> proxy.name in group.options }
                        .map(MihomoProxyGroupSummary::name),
            ),
        )
    }
    runtimeNodes.forEach { node ->
        val previous = stored[node.name]
        stored[node.name] =
            MihomoNodeTestResult(
                name = node.name,
                type = node.type,
                groupNames = node.groupNames,
                delayMillis = node.delayMillis ?: previous?.delayMillis,
                status = if (node.delayMillis != null) MihomoNodeTestStatus.SUCCESS else previous?.status ?: MihomoNodeTestStatus.UNTESTED,
                testedAtEpochMillis = previous?.testedAtEpochMillis ?: 0L,
            )
    }
    return stored.values.toList()
}

private fun displayGroupNodes(
    subscription: KiyoriProxySubscription,
    group: MihomoProxyGroupSummary,
    runtimeNodes: List<MihomoRuntimeNodeState>,
): List<MihomoNodeTestResult> {
    val knownGroups = subscriptionGroups(subscription).associateBy(MihomoProxyGroupSummary::name)
    val nodes =
        displayNodes(subscription, runtimeNodes)
            .filter { node -> group.name in node.groupNames }
            .associateBy(MihomoNodeTestResult::name)
            .toMutableMap()
    group.options.forEach { option ->
        if (option !in nodes) {
            nodes[option] =
                MihomoNodeTestResult(
                    name = option,
                    type = if (option in knownGroups) "策略组" else "节点",
                    groupNames = listOf(group.name),
                )
        }
    }
    return nodes.values.toList()
}

/**
 * Applies the node-page order without changing the source order used by the default view.
 * Delay sorting deliberately puts every non-success result after measured nodes so an old or
 * missing delay can never look faster than an actual measurement.
 */
internal fun sortNetworkProxyNodes(
    nodes: List<MihomoNodeTestResult>,
    sort: NetworkProxyNodeSort,
): List<MihomoNodeTestResult> {
    if (sort == NetworkProxyNodeSort.DEFAULT) return nodes
    return nodes.withIndex()
        .sortedWith(
            Comparator { left, right ->
                when (sort) {
                    NetworkProxyNodeSort.NAME ->
                        compareValuesBy(
                            left,
                            right,
                            { it.value.name.lowercase(Locale.ROOT) },
                            { it.value.name },
                            { it.index },
                        )
                    NetworkProxyNodeSort.DELAY ->
                        compareValuesBy(
                            left,
                            right,
                            { indexed ->
                                if (indexed.value.status == MihomoNodeTestStatus.SUCCESS && indexed.value.delayMillis != null) 0 else 1
                            },
                            { indexed -> indexed.value.delayMillis ?: Int.MAX_VALUE },
                            { indexed -> indexed.value.name.lowercase(Locale.ROOT) },
                            { it.index },
                        )
                    NetworkProxyNodeSort.DEFAULT -> left.index.compareTo(right.index)
                }
            },
        )
        .map(IndexedValue<MihomoNodeTestResult>::value)
}

private fun subscriptionDescription(subscription: KiyoriProxySubscription): String =
    "${subscription.summary.proxyCount} 节点 · ${subscription.summary.groupCount} 组 · ${subscription.summary.isolatedProxyCount} 隔离"

private fun subscriptionListDescription(subscription: KiyoriProxySubscription): String {
    val source = subscriptionSourceLabel(subscription)
    val usage = subscription.usage
    val remaining =
        if (usage?.totalBytes != null && usage.downloadBytes != null && usage.uploadBytes != null) {
            (usage.totalBytes - usage.downloadBytes - usage.uploadBytes).coerceAtLeast(0L)
        } else {
            null
        }
    return buildString {
        append(source)
        append(" · ")
        append(subscriptionDescription(subscription))
        remaining?.let { append(" · 剩余 ${formatSubscriptionBytes(it)}") }
    }
}

private fun subscriptionSourceLabel(subscription: KiyoriProxySubscription): String =
    when (subscription.sourceType) {
        KiyoriSubscriptionSourceType.URL -> maskedSubscriptionHost(subscription.subscriptionUrl)
        KiyoriSubscriptionSourceType.LOCAL_FILE -> subscription.sourceLabel.ifBlank { "本地 YAML" }
    }

private fun nodeInventoryDescription(
    subscription: KiyoriProxySubscription,
    runtimeNodes: List<MihomoRuntimeNodeState>,
): String {
    val nodes = displayNodes(subscription, runtimeNodes)
    val tested = nodes.count { it.status != MihomoNodeTestStatus.UNTESTED }
    return "${nodes.size} 个节点 · $tested 个有测速记录"
}

private fun nodeDescription(node: MihomoNodeTestResult): String {
    val status =
        when (node.status) {
            MihomoNodeTestStatus.UNTESTED -> "未测速"
            MihomoNodeTestStatus.SUCCESS -> node.delayMillis?.let { "${it} ms" } ?: "成功"
            MihomoNodeTestStatus.TIMEOUT -> "超时"
            MihomoNodeTestStatus.FAILED -> "失败"
        }
    val groups = node.groupNames.joinToString("、").ifBlank { "未分组" }
    return "${node.type} · $status · $groups"
}

private fun nodeDelayLabel(node: MihomoNodeTestResult): String =
    when (node.status) {
        MihomoNodeTestStatus.UNTESTED -> "未测速"
        MihomoNodeTestStatus.SUCCESS -> node.delayMillis?.let { "${it} ms" } ?: "成功"
        MihomoNodeTestStatus.TIMEOUT -> "超时"
        MihomoNodeTestStatus.FAILED -> "失败"
    }

private fun groupDescription(
    group: MihomoProxyGroupSummary,
    runtime: MihomoRuntimeGroupState?,
): String {
    val count = runtime?.allItems?.size ?: group.options.size
    return if (group.manuallySelectable) "手动选择 · $count 个项目" else "${group.type} · $count 个项目"
}

private fun groupDisplayName(name: String): String =
    if (name == MihomoConfigSanitizer.ROUTE_GROUP_NAME) "默认代理" else name

private fun networkProxyOverrideLabel(mode: KiyoriNetworkOverrideMode): String =
    when (mode) {
        KiyoriNetworkOverrideMode.INHERIT -> "跟随模式"
        KiyoriNetworkOverrideMode.DIRECT -> "直连"
        KiyoriNetworkOverrideMode.PROXY -> "代理"
    }

private fun networkProxyRuntimeDescription(
    config: KiyoriNetworkProxyConfig?,
    phase: KiyoriMihomoRuntimePhase,
    activeSubscription: KiyoriProxySubscription?,
): String =
    when {
        config == null -> "加密配置不可读取，可在本页重置"
        !config.enabled -> "已关闭；Kiyori 使用系统网络或外部 VPN"
        !KiyoriNetworkProxyPolicy.hasConfiguredProxyRoute(config) -> "已开启；当前所有模块均配置为直连"
        activeSubscription == null -> "已开启；需要选择当前订阅"
        phase == KiyoriMihomoRuntimePhase.RUNNING -> "运行中 · ${networkProxyConnectionModeLabel(config.defaultMode)} · ${activeSubscription.displayName}"
        phase == KiyoriMihomoRuntimePhase.STARTING -> "正在启动 ${activeSubscription.displayName}"
        phase == KiyoriMihomoRuntimePhase.STOPPING -> "正在停止内嵌 Mihomo"
        phase == KiyoriMihomoRuntimePhase.ERROR -> "运行失败；设置仍保留"
        else -> "已开启；等待代理模块发起请求"
    }

private fun networkProxyConnectionModeLabel(mode: KiyoriNetworkConnectionMode): String =
    when (mode) {
        KiyoriNetworkConnectionMode.RULE -> "规则"
        KiyoriNetworkConnectionMode.GLOBAL,
        KiyoriNetworkConnectionMode.PROXY,
        -> "全局"
        KiyoriNetworkConnectionMode.DIRECT -> "直连"
    }

private fun networkProxyLogDisplayText(entries: List<KiyoriNetworkProxyLogEntry>): String {
    val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    return entries.joinToString(separator = "\n\n") { entry ->
        buildString {
            append(timeFormat.format(Date(entry.timestampEpochMillis)))
            append("  ")
            append(proxyLogLevelLabel(entry.level))
            append("  ")
            append(entry.source)
            append('\n')
            append(entry.message)
        }
    }
}

private fun proxyLogLevelLabel(level: KiyoriNetworkProxyLogLevel): String =
    when (level) {
        KiyoriNetworkProxyLogLevel.INFO -> "信息"
        KiyoriNetworkProxyLogLevel.WARNING -> "警告"
        KiyoriNetworkProxyLogLevel.ERROR -> "错误"
    }

private fun networkProxyUserMessage(error: KiyoriNetworkException): String =
    when (error.code) {
        KiyoriNetworkErrorCode.CONFIG_MISSING -> "请选择有效订阅，并至少为一个模块启用代理。"
        KiyoriNetworkErrorCode.CONFIG_INVALID -> "Clash / Mihomo 配置未通过结构或核心校验，现有配置未被覆盖。"
        KiyoriNetworkErrorCode.SETTINGS_WRITE_FAILED -> "Android Keystore 或私有配置文件写入失败，本次设置未保存。"
        KiyoriNetworkErrorCode.SUBSCRIPTION_FORMAT -> "订阅服务没有返回 Clash.Meta YAML，请检查订阅类型。"
        KiyoriNetworkErrorCode.SUBSCRIPTION_FAILED -> "订阅连接失败。首次导入时若订阅主机不可直连，请选择已下载的 Clash YAML。"
        KiyoriNetworkErrorCode.SUBSCRIPTION_DUPLICATE -> "该订阅地址已存在；可编辑已有条目或创建副本。"
        KiyoriNetworkErrorCode.SUBSCRIPTION_NOT_FOUND -> "订阅已不存在，请刷新订阅库。"
        KiyoriNetworkErrorCode.SUBSCRIPTION_IN_USE -> "该订阅正在被代理路线使用，请先切换订阅或关闭应用内代理。"
        KiyoriNetworkErrorCode.VPN_CONFLICT -> "检测到外部系统 VPN；请在高级设置中明确允许并存。"
        KiyoriNetworkErrorCode.CORE_MISSING -> "当前 APK 未包含内嵌 Mihomo 核心。"
        KiyoriNetworkErrorCode.CORE_START_FAILED -> "内嵌 Mihomo 无法启动或 Controller 不可用。"
        KiyoriNetworkErrorCode.GROUP_SELECTION_INVALID -> "策略组或所选项目已失效，请刷新节点后重新选择。"
        KiyoriNetworkErrorCode.PROXY_CONNECT_FAILED -> "节点或策略组连接测试失败，请查看测速状态。"
        KiyoriNetworkErrorCode.HTTP_FAILED -> "当前网络请求失败。"
        KiyoriNetworkErrorCode.WEBVIEW_UNSUPPORTED -> "当前 Android System WebView 不支持应用内代理。"
    }

private fun unexpectedOperationMessage(area: NetworkProxyOperationArea): String =
    when (area) {
        NetworkProxyOperationArea.SUBSCRIPTIONS -> "订阅操作未完成，请检查文件访问权限或稍后重试。"
        NetworkProxyOperationArea.GROUPS -> "节点或策略组操作未完成。"
        else -> "网络代理操作未完成，请稍后重试。"
    }

private fun maskedSubscriptionHost(rawUrl: String): String =
    runCatching { Uri.parse(rawUrl).host.orEmpty().ifBlank { "远端订阅" } }.getOrDefault("远端订阅")

private fun formatSubscriptionBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "${DecimalFormat("#,##0.#").format(value)} ${units[unitIndex]}"
}

@Composable
private fun NetworkProxyNodeList(
    nodes: List<MihomoNodeTestResult>,
    selectedNodeName: String?,
    selectable: Boolean,
    enabled: Boolean,
    onSelect: (MihomoNodeTestResult) -> Unit,
    onTest: (MihomoNodeTestResult) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    nodes.forEachIndexed { index, node ->
        val selected = node.name == selectedNodeName
        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.42f).clickable(enabled = enabled && selectable) { onSelect(node) }.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                )
                Text(
                    text = buildString {
                        append(node.type)
                        append(" · ")
                        append(nodeDelayLabel(node))
                        append(" · ")
                        append(if (selectable) "点击切换" else "自动策略组由 Mihomo 决定")
                    },
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "已选择节点", tint = colors.accent, modifier = Modifier.padding(horizontal = 4.dp).size(19.dp))
            }
            IconButton(enabled = enabled, onClick = { onTest(node) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Speed, contentDescription = "测速 ${node.name}", tint = colors.accent, modifier = Modifier.size(20.dp))
            }
        }
        if (index != nodes.lastIndex) KiyoriSettingsDivider()
    }
}

@Composable
private fun NetworkProxySubscriptionRow(
    subscription: KiyoriProxySubscription,
    active: Boolean,
    enabled: Boolean,
    menuExpanded: Boolean,
    onSelect: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.42f).clickable(enabled = enabled, onClick = onSelect).padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.displayName,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subscriptionListDescription(subscription),
                color = colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (active) {
            Text("使用中", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Box {
            IconButton(enabled = enabled, onClick = onOpenMenu, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "管理 ${subscription.displayName}", tint = colors.mutedIcon)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("更新") }, onClick = onUpdate)
                DropdownMenuItem(text = { Text("编辑") }, onClick = onEdit)
                DropdownMenuItem(text = { Text("复制") }, onClick = onCopy)
                DropdownMenuItem(text = { Text("删除") }, onClick = onDelete)
            }
        }
    }
}

private fun isCustomRuleInputValid(
    raw: String,
    type: KiyoriNetworkRuleType,
): Boolean = isKiyoriNetworkRulePatternValid(raw, type)

private val customRuleEditorTypes =
    KiyoriNetworkRuleType.entries.filterNot { type ->
        type in setOf(
            KiyoriNetworkRuleType.GEOSITE,
            KiyoriNetworkRuleType.IP_ASN,
            KiyoriNetworkRuleType.GEOIP,
            KiyoriNetworkRuleType.SRC_GEOIP,
            KiyoriNetworkRuleType.SRC_IP_ASN,
            KiyoriNetworkRuleType.RULE_SET,
            KiyoriNetworkRuleType.SUB_RULE,
            KiyoriNetworkRuleType.MATCH,
        )
    }

private fun customRuleTypeLabel(type: KiyoriNetworkRuleType): String =
    when (type) {
        KiyoriNetworkRuleType.DOMAIN -> "完整域名 · DOMAIN"
        KiyoriNetworkRuleType.DOMAIN_SUFFIX -> "域名后缀 · DOMAIN-SUFFIX"
        KiyoriNetworkRuleType.DOMAIN_KEYWORD -> "域名关键字 · DOMAIN-KEYWORD"
        KiyoriNetworkRuleType.IP_CIDR -> "IPv4 网段 · IP-CIDR"
        KiyoriNetworkRuleType.IP_CIDR6 -> "IPv6 网段 · IP-CIDR6"
        KiyoriNetworkRuleType.GEOIP -> "IP 国家/地区 · GEOIP"
        else -> type.wireName
    }

private fun customRuleTypeDescription(type: KiyoriNetworkRuleType): String =
    when (type) {
        KiyoriNetworkRuleType.DOMAIN -> "仅匹配这个完整域名"
        KiyoriNetworkRuleType.DOMAIN_SUFFIX -> "匹配该域名及其所有子域名"
        KiyoriNetworkRuleType.DOMAIN_KEYWORD -> "域名中包含此关键字时命中"
        KiyoriNetworkRuleType.IP_CIDR,
        KiyoriNetworkRuleType.IP_CIDR6,
        KiyoriNetworkRuleType.SRC_IP_CIDR,
        -> "使用 CIDR 表示法，例如 203.0.113.0/24"
        KiyoriNetworkRuleType.GEOIP,
        KiyoriNetworkRuleType.SRC_GEOIP,
        -> "使用国家或地区代码，例如 CN"
        else -> "填写 ${type.wireName} 的 Mihomo 匹配内容"
    }

private fun customRulePatternExample(type: KiyoriNetworkRuleType): String =
    when (type) {
        KiyoriNetworkRuleType.DOMAIN,
        KiyoriNetworkRuleType.DOMAIN_SUFFIX,
        -> "例如 example.com"
        KiyoriNetworkRuleType.DOMAIN_KEYWORD -> "例如 bilibili"
        KiyoriNetworkRuleType.IP_CIDR -> "例如 203.0.113.0/24"
        KiyoriNetworkRuleType.IP_CIDR6 -> "例如 2001:db8::/32"
        KiyoriNetworkRuleType.GEOIP,
        KiyoriNetworkRuleType.SRC_GEOIP,
        -> "例如 CN"
        else -> "输入匹配内容"
    }

private fun filteredCustomRules(
    rules: List<KiyoriNetworkProxyRule>,
    rawQuery: String,
): List<KiyoriNetworkProxyRule> {
    val query = rawQuery.trim().lowercase(Locale.ROOT)
    if (query.isBlank()) return rules
    return rules.filter { rule ->
        rule.pattern.lowercase(Locale.ROOT).contains(query) ||
            rule.type.wireName.lowercase(Locale.ROOT).contains(query) ||
            rule.mode.name.lowercase(Locale.ROOT).contains(query) ||
            customRuleTypeLabel(rule.type).lowercase(Locale.ROOT).contains(query)
    }
}

private fun filteredSubscriptionRules(
    rules: List<String>,
    rawQuery: String,
): List<IndexedValue<String>> {
    val query = rawQuery.trim().lowercase(Locale.ROOT)
    return rules.withIndex().filter { indexed ->
        query.isBlank() || indexed.value.lowercase(Locale.ROOT).contains(query)
    }
}

@Composable
private fun CustomRuleRow(
    rule: KiyoriNetworkProxyRule,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.42f).clickable(enabled = enabled, onClick = onEdit).padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.pattern,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (rule.mode) {
                    KiyoriNetworkRuleMode.DIRECT -> "直连"
                    KiyoriNetworkRuleMode.PROXY -> "代理"
                } + " · " + customRuleTypeLabel(rule.type),
                color = colors.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() }, enabled = enabled)
        TextButton(enabled = enabled, onClick = onDelete) { Text("删除") }
    }
}

@Composable
private fun SubscriptionRuleRow(
    rule: String,
    enabled: Boolean,
    onEdit: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val kind = rule.substringBefore(',').trim().ifBlank { "未知类型" }
    val target = rule.substringAfterLast(',').trim().ifBlank { "未知目标" }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.42f)
                .clickable(enabled = enabled, onClick = onEdit)
                .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = rule,
            color = colors.primaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = true,
        )
        Text(
            text = "$kind · 目标 $target · 点击编辑",
            color = colors.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun NetworkProxyGroupTabs(
    groups: List<MihomoProxyGroupSummary>,
    selectedGroupName: String?,
    enabled: Boolean,
    onSelect: (MihomoProxyGroupSummary) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            groups.forEach { group ->
                val selected = group.name == selectedGroupName
                Column(
                    modifier =
                        Modifier
                            .clickable(enabled = enabled) { onSelect(group) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = groupDisplayName(group.name),
                        color = if (selected) colors.accent else colors.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
    }
}

@Composable
private fun NetworkProxySegmentedMode(
    selected: KiyoriNetworkConnectionMode,
    enabled: Boolean,
    onSelect: (KiyoriNetworkConnectionMode) -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    val normalizedSelected =
        if (selected == KiyoriNetworkConnectionMode.PROXY) {
            KiyoriNetworkConnectionMode.GLOBAL
        } else {
            selected
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            KiyoriNetworkConnectionMode.RULE to "规则",
            KiyoriNetworkConnectionMode.GLOBAL to "全局",
            KiyoriNetworkConnectionMode.DIRECT to "直连",
        ).forEach { (mode, label) ->
            val isSelected = normalizedSelected == mode
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) colors.accent else colors.pageBackground,
                            RoundedCornerShape(12.dp),
                        ).clickable(enabled = enabled) { onSelect(mode) }
                        .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = if (isSelected) colors.accentContent else colors.primaryText,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private suspend fun readNetworkProxyYaml(
    context: android.content.Context,
    uri: Uri,
): SelectedYaml {
    val displayName =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim().orEmpty() else ""
            }.orEmpty()
            .ifBlank { "Local YAML" }
            .take(160)
    val input =
        context.contentResolver.openInputStream(uri)
            ?: throw KiyoriNetworkException(
                KiyoriNetworkErrorCode.CONFIG_INVALID,
                "The selected YAML file could not be opened.",
            )
    val text =
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (output.size() + read > MihomoConfigSanitizer.MAX_YAML_BYTES) {
                    throw KiyoriNetworkException(
                        KiyoriNetworkErrorCode.CONFIG_INVALID,
                        "The selected YAML file exceeds the 4 MiB limit.",
                    )
                }
                output.write(buffer, 0, read)
            }
            MihomoConfigSanitizer.decodeUtf8(
                output.toByteArray(),
                KiyoriNetworkErrorCode.CONFIG_INVALID,
            )
        }
    return SelectedYaml(displayName = displayName, text = text)
}

private fun writeNetworkProxyLog(
    context: android.content.Context,
    uri: Uri,
    text: String,
) {
    val stream =
        context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("The selected proxy log document cannot be opened for writing.")
    stream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(text) }
}
