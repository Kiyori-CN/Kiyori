package com.ai.assistance.operit.ui.main.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager as ToolPackageManager
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import com.kiyori.platform.logging.KiyoriLogger
import com.kiyori.platform.network.KiyoriMihomoProbePhase
import com.kiyori.platform.network.KiyoriMihomoRuntimePhase
import com.kiyori.platform.network.KiyoriNetworkConnectionMode
import com.kiyori.platform.network.KiyoriNetworkErrorCode
import com.kiyori.platform.network.KiyoriNetworkException
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.KiyoriNetworkOverrideMode
import com.kiyori.platform.network.KiyoriNetworkProxyConfig
import com.kiyori.platform.network.KiyoriNetworkProxyManager
import com.kiyori.platform.network.KiyoriNetworkProxyPolicy
import com.kiyori.platform.network.KiyoriNetworkProxyStoreState
import com.kiyori.platform.network.KiyoriNetworkSettingsAppliedException
import com.kiyori.platform.network.KiyoriProxySubscription
import com.kiyori.platform.network.KiyoriSubscriptionSourceType
import com.kiyori.platform.network.MihomoConfigSanitizer
import com.kiyori.platform.network.MihomoNodeTestResult
import com.kiyori.platform.network.MihomoNodeTestStatus
import com.kiyori.platform.network.MihomoProxyGroupSummary
import com.kiyori.platform.network.MihomoRuntimeGroupState
import com.kiyori.platform.network.MihomoRuntimeNodeState
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "KiyoriNetworkProxyPage"

private enum class NetworkProxyPageSection(
    val title: String,
) {
    OVERVIEW("网络代理"),
    MODULES("模块连接"),
    GROUPS("策略组与节点"),
}

private enum class NetworkProxyNodeLayout(
    val label: String,
    val columns: Int,
) {
    SINGLE("单列", 1),
    DOUBLE("双列", 2),
    MULTI("多列", 3),
}

private enum class NetworkProxyNodeSort(
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
    data class Module(val module: KiyoriNetworkModule) : ModeSelection

    data class Script(val packageName: String) : ModeSelection
}

private data class GroupActionTarget(
    val subscriptionId: String,
    val group: MihomoProxyGroupSummary,
)

private data class GroupSelectionTarget(
    val subscriptionId: String,
    val group: MihomoProxyGroupSummary,
)

private data class SelectedYaml(
    val displayName: String,
    val text: String,
)

private val networkProxyModeOptions =
    listOf(
        NetworkProxyModeOption("跟随默认", "使用页面顶部的默认连接模式", KiyoriNetworkOverrideMode.INHERIT),
        NetworkProxyModeOption("直连", "绕过 Kiyori 内嵌 Mihomo；仍受系统 VPN 影响", KiyoriNetworkOverrideMode.DIRECT),
        NetworkProxyModeOption("代理", "经过当前订阅的 Kiyori 内嵌 Mihomo", KiyoriNetworkOverrideMode.PROXY),
    )

private val networkProxyModules =
    listOf(
        KiyoriNetworkModule.AI_SERVICES to "AI 主模型与语音",
        KiyoriNetworkModule.AI_TOOLS to "AI 工具",
        KiyoriNetworkModule.BROWSER to "浏览器",
        KiyoriNetworkModule.DOWNLOADS to "下载",
        KiyoriNetworkModule.PLAYER to "播放器",
        KiyoriNetworkModule.SCRIPTS to "传统脚本",
        KiyoriNetworkModule.APP_SERVICES to "Kiyori 在线服务",
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
    var currentSubscriptionSheetVisible by remember { mutableStateOf(false) }
    var inspectedSubscriptionId by remember { mutableStateOf<String?>(null) }
    var copyUrlSubscriptionId by remember { mutableStateOf<String?>(null) }
    var deleteSubscriptionId by remember { mutableStateOf<String?>(null) }
    var resetDialogVisible by remember { mutableStateOf(false) }
    var groupActionTarget by remember { mutableStateOf<GroupActionTarget?>(null) }
    var groupSelectionTarget by remember { mutableStateOf<GroupSelectionTarget?>(null) }
    var pendingModeSelection by remember { mutableStateOf<ModeSelection?>(null) }
    var scriptRulesVisible by remember { mutableStateOf(false) }
    var scriptPackageDialogVisible by remember { mutableStateOf(false) }
    var scriptPackageDraft by remember { mutableStateOf("") }
    var discoveredScriptPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var scriptCatalogRefreshing by remember { mutableStateOf(false) }
    var testUrlDialogVisible by remember { mutableStateOf(false) }
    var testUrlDraft by remember { mutableStateOf(config?.testUrl ?: KiyoriNetworkProxyConfig.DEFAULT_TEST_URL) }
    var pageSection by remember { mutableStateOf(NetworkProxyPageSection.OVERVIEW) }
    var selectedGroupTabName by remember { mutableStateOf<String?>(null) }
    var nodeSearchQuery by remember { mutableStateOf("") }
    var nodeLayout by remember { mutableStateOf(NetworkProxyNodeLayout.SINGLE) }
    var nodeSort by remember { mutableStateOf(NetworkProxyNodeSort.DEFAULT) }
    var nodeLayoutMenuVisible by remember { mutableStateOf(false) }
    var nodeSortMenuVisible by remember { mutableStateOf(false) }

    val inspectedSubscription =
        config?.subscriptions?.firstOrNull { subscription -> subscription.id == inspectedSubscriptionId }
            ?: activeSubscription
            ?: config?.subscriptions?.firstOrNull()
    val controlsEnabled = config != null && activeOperation == null
    val activeRuntimeGroups =
        runtimeState.groups.takeIf { groups -> runtimeState.subscriptionId == activeSubscription?.id }
            .orEmpty()
    val activeProxySelection =
        activeSubscription?.let { subscription ->
            resolveActiveProxySelection(subscription, activeRuntimeGroups)
        }

    val sectionSubscription = inspectedSubscription
    val sectionGroups = sectionSubscription?.let(::subscriptionGroups).orEmpty()
    val selectedGroupTab =
        sectionGroups.firstOrNull { group -> group.name == selectedGroupTabName }
            ?: sectionGroups.firstOrNull()

    BackHandler(enabled = pageSection != NetworkProxyPageSection.OVERVIEW) {
        pageSection = NetworkProxyPageSection.OVERVIEW
    }

    LaunchedEffect(pageSection) {
        nodeLayoutMenuVisible = false
        nodeSortMenuVisible = false
        nodeSearchQuery = ""
    }

    fun refreshScriptCatalog() {
        if (scriptCatalogRefreshing) return
        scope.launch {
            scriptCatalogRefreshing = true
            try {
                discoveredScriptPackages =
                    withContext(Dispatchers.IO) {
                        val packageManager =
                            ToolPackageManager.getInstance(
                                context,
                                AIToolHandler.getInstance(context),
                            )
                        val available = packageManager.getAvailablePackages(forceRefresh = true)
                        val enabled = packageManager.getEnabledPackageNames().toSet()
                        enabled
                            .asSequence()
                            .filter { packageName -> packageName in available }
                            .filterNot(packageManager::isToolPkgContainer)
                            .filterNot(packageManager::isToolPkgSubpackage)
                            .sorted()
                            .toList()
                    }
            } catch (error: Exception) {
                KiyoriLogger.e(TAG, "Failed to load enabled script packages", error)
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

    LaunchedEffect(config?.activeSubscriptionId, config?.subscriptions?.map(KiyoriProxySubscription::id)) {
        val inspectedStillExists = config?.subscriptions?.any { it.id == inspectedSubscriptionId } == true
        if (!inspectedStillExists) {
            inspectedSubscriptionId = config?.activeSubscriptionId ?: config?.subscriptions?.firstOrNull()?.id
        }
    }
    LaunchedEffect(config?.testUrl) {
        if (!testUrlDialogVisible) {
            testUrlDraft = config?.testUrl ?: KiyoriNetworkProxyConfig.DEFAULT_TEST_URL
        }
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
                        val added = manager.addLocalYaml(selected.text, selected.displayName)
                        inspectedSubscriptionId = added.id
                    }
                    is YamlImportTarget.Replace -> {
                        manager.replaceLocalSubscription(target.subscriptionId, selected.text, selected.displayName)
                        inspectedSubscriptionId = target.subscriptionId
                    }
                }
            }
        }

    key(pageSection) {
        KiyoriCollapsingSettingsPage(
            title = pageSection.title,
            onBack = {
                if (pageSection == NetworkProxyPageSection.OVERVIEW) onBack() else pageSection = NetworkProxyPageSection.OVERVIEW
            },
            modifier = modifier,
            headerAction = {
                if (
                    pageSection == NetworkProxyPageSection.GROUPS &&
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
                                onClick = { nodeLayoutMenuVisible = true },
                            ) {
                                Icon(Icons.Default.ViewModule, contentDescription = "节点布局")
                            }
                            DropdownMenu(
                                expanded = nodeLayoutMenuVisible,
                                onDismissRequest = { nodeLayoutMenuVisible = false },
                            ) {
                                NetworkProxyNodeLayout.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            nodeLayout = option
                                            nodeLayoutMenuVisible = false
                                        },
                                        trailingIcon = {
                                            if (nodeLayout == option) {
                                                Icon(Icons.Default.Check, contentDescription = "当前布局")
                                            }
                                        },
                                    )
                                }
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
                } else {
                    Row {
                        IconButton(
                            enabled = controlsEnabled,
                            onClick = {
                                runOperation(
                                    NetworkProxyOperation("refresh_runtime", NetworkProxyOperationArea.STATUS, "正在刷新运行状态"),
                                    "运行状态已刷新。",
                                ) { manager.refreshRuntimeState() }
                            },
                        ) {
                            if (activeOperation?.id == "refresh_runtime") {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新状态")
                            }
                        }
                        IconButton(
                            enabled = controlsEnabled,
                            onClick = {
                                runOperation(
                                    NetworkProxyOperation("test_connection", NetworkProxyOperationArea.STATUS, "正在测试当前代理"),
                                    "当前代理连接测试通过。",
                                ) { manager.testActiveProxyConnection() }
                            },
                        ) {
                            if (activeOperation?.id == "test_connection") {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = "测试当前代理")
                            }
                        }
                    }
                }
            },
        ) {
        if (pageSection == NetworkProxyPageSection.OVERVIEW) item(key = "network_proxy_status") {
            KiyoriSettingsGroupSection(
                title = "代理状态",
                description = "只影响 Kiyori 进程内已接入模块，不改变其他应用的网络。",
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
                if (config == null) {
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "重置不可读设置",
                        description = "删除无法解密的代理配置并重新初始化",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Delete,
                        iconTone = KiyoriSemanticTone.RED,
                        enabled = activeOperation == null,
                        onClick = { resetDialogVisible = true },
                    )
                }
                NetworkProxyOperationFeedback(
                    area = NetworkProxyOperationArea.STATUS,
                    operation = activeOperation,
                    feedback = feedback,
                    runtimeError = runtimeState.phase == KiyoriMihomoRuntimePhase.ERROR,
                )
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) item(key = "network_proxy_current_route") {
            KiyoriSettingsGroupSection(
                title = "当前路由",
                description = "显示当前订阅中从“默认代理”根组解析出的实际策略链和最终节点。",
            ) {
                if (activeSubscription == null) {
                    Text(
                        text = "尚未选择当前订阅。开启代理前，请先导入并选择一条 Clash / Mihomo 订阅。",
                        color = LocalKiyoriSettingsColors.current.secondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                } else {
                    KiyoriSettingsRow(
                        title = "当前订阅",
                        description = subscriptionSourceLabel(activeSubscription),
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.CheckCircle,
                        iconTone = KiyoriSemanticTone.CYAN,
                        value = activeSubscription.displayName,
                        enabled = controlsEnabled,
                        onClick = { currentSubscriptionSheetVisible = true },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "策略链",
                        description =
                            activeProxySelection?.chain?.joinToString(" → ")
                                ?: "尚未从根策略组读到当前选择",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.BLUE,
                        value = activeProxySelection?.finalGroupName?.let(::groupDisplayName),
                        enabled = controlsEnabled && activeProxySelection?.finalGroupName != null,
                        onClick = {
                            activeProxySelection?.finalGroupName?.let { groupName ->
                                inspectedSubscriptionId = activeSubscription.id
                                selectedGroupTabName = groupName
                                pageSection = NetworkProxyPageSection.GROUPS
                            }
                        },
                    )
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = "当前节点",
                        description =
                            activeProxySelection?.selectedItemName?.let {
                                "由 ${activeProxySelection.finalGroupName?.let(::groupDisplayName) ?: "当前组"} 选中"
                            } ?: "当前组还没有可用节点选择",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Speed,
                        iconTone = KiyoriSemanticTone.GREEN,
                        value = activeProxySelection?.selectedItemName ?: "未选择",
                        enabled = controlsEnabled && activeProxySelection?.finalGroupName != null,
                        onClick = {
                            activeProxySelection?.finalGroupName?.let { groupName ->
                                inspectedSubscriptionId = activeSubscription.id
                                selectedGroupTabName = groupName
                                pageSection = NetworkProxyPageSection.GROUPS
                            }
                        },
                    )
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) item(key = "network_proxy_subscriptions") {
            KiyoriSettingsGroupSection(
                title = "订阅库",
                description = "多条 Clash / Mihomo 配置独立保存；只有当前订阅进入主代理运行时。",
            ) {
                KiyoriSettingsRow(
                    title = "当前订阅",
                    description = activeSubscription?.let(::subscriptionDescription) ?: "尚未选择订阅",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    icon = Icons.Default.CheckCircle,
                    iconTone = KiyoriSemanticTone.CYAN,
                    value = activeSubscription?.displayName,
                    enabled = controlsEnabled && config.subscriptions.isNotEmpty(),
                    onClick = { currentSubscriptionSheetVisible = true },
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "添加订阅地址",
                    description = "下载 Clash.Meta YAML，校验通过后加入订阅库",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    icon = Icons.Default.Add,
                    iconTone = KiyoriSemanticTone.BLUE,
                    enabled = controlsEnabled,
                    onClick = { openSubscriptionEditor(SubscriptionEditor.AddUrl) },
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "导入 YAML 文件",
                    description = "从本地文件创建独立订阅，不保留其他条目的 URL",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    icon = Icons.Default.Upload,
                    iconTone = KiyoriSemanticTone.GREEN,
                    enabled = controlsEnabled,
                    onClick = {
                        yamlImportTarget = YamlImportTarget.Add
                        filePicker.launch(arrayOf("*/*"))
                    },
                )
                config?.subscriptions?.forEach { subscription ->
                    KiyoriSettingsDivider()
                    KiyoriSettingsRow(
                        title = subscription.displayName,
                        description = subscriptionListDescription(subscription),
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Cloud,
                        iconTone = if (subscription.id == config.activeSubscriptionId) KiyoriSemanticTone.CYAN else KiyoriSemanticTone.BLUE,
                        value = if (subscription.id == config.activeSubscriptionId) "使用中" else null,
                        enabled = controlsEnabled,
                        onClick = { subscriptionActionsId = subscription.id },
                    )
                }
                NetworkProxyOperationFeedback(
                    area = NetworkProxyOperationArea.SUBSCRIPTIONS,
                    operation = activeOperation,
                    feedback = feedback,
                    runtimeError = false,
                )
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) item(key = "network_proxy_default_mode") {
            KiyoriSettingsGroupSection(
                title = "默认连接",
                description = "模块选择“跟随默认”时使用此模式；总开关关闭时所有模块按直连处理。",
            ) {
                NetworkProxySegmentedMode(
                    selected = config?.defaultMode ?: KiyoriNetworkConnectionMode.DIRECT,
                    enabled = controlsEnabled,
                    onSelect = { mode ->
                        runOperation(
                            NetworkProxyOperation("default_mode", NetworkProxyOperationArea.ROUTING, "正在保存默认连接"),
                            "默认连接模式已保存。",
                        ) { manager.updateConfig { it.copy(defaultMode = mode) } }
                    },
                )
                NetworkProxyOperationFeedback(NetworkProxyOperationArea.ROUTING, activeOperation, feedback, false)
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) {
            item(key = "network_proxy_modules_summary") {
                val configuredModuleCount = config?.moduleModes?.size ?: 0
                KiyoriSettingsGroupSection(
                    title = "模块连接",
                    description = "七个联网模块集中管理；需要单独设置时进入模块连接。",
                ) {
                    KiyoriSettingsRow(
                        title = "模块连接模式",
                        description = "总开关 → 模块覆盖 → 传统脚本包覆盖",
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Tune,
                        iconTone = KiyoriSemanticTone.BLUE,
                        value = if (configuredModuleCount == 0) "跟随默认" else "$configuredModuleCount 个覆盖",
                        enabled = controlsEnabled,
                        onClick = { pageSection = NetworkProxyPageSection.MODULES },
                    )
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.MODULES) {
            item(key = "network_proxy_modules_detail") {
                KiyoriSettingsGroupSection(
                    title = "模块连接",
                    description = "先应用总开关，再应用模块覆盖；直连仍可能经过 Android 系统 VPN。",
                ) {
                    networkProxyModules.forEachIndexed { index, (module, label) ->
                        val override = config?.moduleModes?.get(module) ?: KiyoriNetworkOverrideMode.INHERIT
                        KiyoriSettingsRow(
                            title = label,
                            description = networkProxyModuleDescription(module),
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Default.Tune,
                            iconTone = KiyoriSemanticTone.BLUE,
                            value = networkProxyOverrideLabel(override),
                            enabled = controlsEnabled,
                            onClick = { pendingModeSelection = ModeSelection.Module(module) },
                        )
                        if (index != networkProxyModules.lastIndex) KiyoriSettingsDivider()
                    }
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) {
            item(key = "network_proxy_groups_summary") {
                val routeDescription =
                    activeProxySelection?.chain?.joinToString(" → ") ?: "尚未选择订阅中的策略组和节点"
                KiyoriSettingsGroupSection(
                    title = "策略组与节点",
                    description = "按订阅分组查看节点；进入后用横向标签切换策略组。",
                ) {
                    KiyoriSettingsRow(
                        title = "打开策略组与节点",
                        description = routeDescription,
                        kind = KiyoriSettingsRowKind.NAVIGATION,
                        icon = Icons.Default.Speed,
                        iconTone = KiyoriSemanticTone.GREEN,
                        value = activeProxySelection?.selectedItemName ?: "未选择",
                        enabled = controlsEnabled && config.subscriptions.isNotEmpty(),
                        onClick = {
                            inspectedSubscriptionId = activeSubscription?.id ?: config?.subscriptions?.firstOrNull()?.id
                            selectedGroupTabName = activeProxySelection?.finalGroupName
                            pageSection = NetworkProxyPageSection.GROUPS
                        },
                    )
                    NetworkProxyOperationFeedback(
                        area = NetworkProxyOperationArea.GROUPS,
                        operation = activeOperation,
                        feedback = feedback,
                        runtimeError = probeState.phase == KiyoriMihomoProbePhase.ERROR,
                    )
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.GROUPS) {
            item(key = "network_proxy_groups_detail") {
                val subscription = sectionSubscription
                KiyoriSettingsGroupSection(
                    title = subscription?.displayName ?: "策略组与节点",
                    description = "横向切换分组；选择当前项目与测速是两个独立操作。",
                ) {
                    if (subscription == null) {
                        Text(
                            "添加订阅后可管理策略组和节点。",
                            color = LocalKiyoriSettingsColors.current.secondaryText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        val runtimeMatches = runtimeState.subscriptionId == subscription.id
                        val runtimeGroups = if (runtimeMatches) runtimeState.groups else emptyList()
                        KiyoriSettingsRow(
                            title = "查看订阅",
                            description = subscriptionSourceLabel(subscription),
                            kind = KiyoriSettingsRowKind.NAVIGATION,
                            icon = Icons.Default.Cloud,
                            iconTone = KiyoriSemanticTone.CYAN,
                            value = subscriptionDescription(subscription),
                            enabled = controlsEnabled,
                            onClick = { currentSubscriptionSheetVisible = true },
                        )
                        KiyoriSettingsDivider()
                        NetworkProxyGroupTabs(
                            groups = sectionGroups,
                            selectedGroupName = selectedGroupTab?.name,
                            enabled = controlsEnabled,
                            onSelect = {
                                group ->
                                selectedGroupTabName = group.name
                                nodeSearchQuery = ""
                            },
                        )
                        OutlinedTextField(
                            value = nodeSearchQuery,
                            onValueChange = { nodeSearchQuery = it },
                            singleLine = true,
                            enabled = controlsEnabled,
                            placeholder = { Text("搜索当前分组节点") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon =
                                if (nodeSearchQuery.isBlank()) {
                                    null
                                } else {
                                    {
                                        IconButton(onClick = { nodeSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                                        }
                                    }
                                },
                            colors = kiyoriSettingsOutlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        selectedGroupTab?.let { group ->
                            val liveGroup = runtimeGroups.firstOrNull { it.name == group.name }
                            val currentItem = liveGroup?.currentItem ?: subscription.selectedGroupItems[group.name]
                            val allNodes =
                                displayNodes(subscription, runtimeState.nodes.takeIf { runtimeMatches }.orEmpty())
                                    .filter { node -> group.name in node.groupNames }
                            val query = nodeSearchQuery.trim().lowercase(Locale.ROOT)
                            val nodes =
                                allNodes.filter { node ->
                                    query.isBlank() ||
                                        node.name.lowercase(Locale.ROOT).contains(query) ||
                                        node.type.lowercase(Locale.ROOT).contains(query)
                                }
                            val displayedNodes =
                                when (nodeSort) {
                                    NetworkProxyNodeSort.DEFAULT -> nodes
                                    NetworkProxyNodeSort.NAME -> nodes.sortedBy { it.name.lowercase(Locale.ROOT) }
                                    NetworkProxyNodeSort.DELAY ->
                                        nodes.sortedWith(
                                            compareBy<MihomoNodeTestResult> { it.delayMillis ?: Long.MAX_VALUE }
                                                .thenBy { it.name.lowercase(Locale.ROOT) },
                                        )
                                }
                            KiyoriSettingsDivider()
                            KiyoriSettingsRow(
                                title = "当前项目",
                                description = groupDescription(group, liveGroup),
                                kind = KiyoriSettingsRowKind.NAVIGATION,
                                icon = Icons.Default.Tune,
                                iconTone = if (group.manuallySelectable) KiyoriSemanticTone.CYAN else KiyoriSemanticTone.ORANGE,
                                value = currentItem ?: "未选择",
                                enabled = controlsEnabled && group.manuallySelectable,
                                onClick = { groupActionTarget = GroupActionTarget(subscription.id, group) },
                            )
                            KiyoriSettingsDivider()
                            KiyoriSettingsRow(
                                title = "刷新节点与分组",
                                description = "读取 provider 动态节点；不切换当前订阅",
                                kind = KiyoriSettingsRowKind.NAVIGATION,
                                icon = Icons.Default.Refresh,
                                iconTone = KiyoriSemanticTone.CYAN,
                                enabled = controlsEnabled,
                                onClick = {
                                    runOperation(
                                        NetworkProxyOperation("refresh_nodes", NetworkProxyOperationArea.GROUPS, "正在探测 ${subscription.displayName}"),
                                        "节点与分组已刷新。",
                                    ) { manager.refreshSubscriptionNodes(subscription.id) }
                                },
                            )
                            KiyoriSettingsDivider()
                            if (allNodes.isEmpty()) {
                                Text(
                                    "当前分组没有已发现的静态或动态节点。",
                                    color = LocalKiyoriSettingsColors.current.secondaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(18.dp),
                                )
                            } else if (displayedNodes.isEmpty()) {
                                Text(
                                    "没有匹配当前搜索条件的节点。",
                                    color = LocalKiyoriSettingsColors.current.secondaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(18.dp),
                                )
                            } else {
                                NetworkProxyNodeGrid(
                                    nodes = displayedNodes,
                                    layout = nodeLayout,
                                    selectedNodeName = currentItem,
                                    selectable = group.manuallySelectable,
                                    enabled = controlsEnabled,
                                    onSelect = { node ->
                                        runOperation(
                                            NetworkProxyOperation("select_node", NetworkProxyOperationArea.GROUPS, "正在切换节点"),
                                            "已切换到 ${node.name}。",
                                        ) { manager.selectGroup(subscription.id, group.name, node.name) }
                                    },
                                    onTest = { node ->
                                        runOperation(
                                            NetworkProxyOperation("test_node", NetworkProxyOperationArea.GROUPS, "正在测试节点"),
                                            "${node.name} 测速完成。",
                                        ) { manager.testSubscriptionNode(subscription.id, node.name) }
                                    },
                                )
                            }
                            KiyoriSettingsDivider()
                            KiyoriSettingsRow(
                                title = "测试整组",
                                description = "使用 Mihomo group 延迟接口并发测试本组节点",
                                kind = KiyoriSettingsRowKind.NAVIGATION,
                                icon = Icons.Default.Speed,
                                iconTone = KiyoriSemanticTone.GREEN,
                                enabled = controlsEnabled,
                                onClick = {
                                    runOperation(
                                        NetworkProxyOperation("test_group", NetworkProxyOperationArea.GROUPS, "正在测试 ${groupDisplayName(group.name)}"),
                                        "${groupDisplayName(group.name)} 测速完成。",
                                    ) { manager.testSubscriptionGroup(subscription.id, group.name) }
                                },
                            )
                        }
                    }
                    NetworkProxyOperationFeedback(
                        area = NetworkProxyOperationArea.GROUPS,
                        operation = activeOperation,
                        feedback = feedback,
                        runtimeError = probeState.phase == KiyoriMihomoProbePhase.ERROR,
                    )
                }
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) item(key = "network_proxy_scripts") {
            val scriptCount = config?.scriptModes?.size ?: 0
            KiyoriSettingsGroupSection(
                title = "脚本规则",
                description = "仅对已启用的传统 JsEngine 脚本提供包级覆盖；ToolPkg 归入 AI 工具。",
            ) {
                KiyoriSettingsRow(
                    title = "逐脚本连接模式",
                    description =
                        when {
                            scriptCatalogRefreshing -> "正在读取已启用脚本…"
                            discoveredScriptPackages.isEmpty() && scriptCount == 0 ->
                                "当前没有已启用的传统脚本；仍可手动添加包名规则"
                            else ->
                                "已发现 ${discoveredScriptPackages.size} 个脚本，已配置 $scriptCount 个覆盖"
                        },
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    icon = Icons.Default.Tune,
                    iconTone = KiyoriSemanticTone.PURPLE,
                    enabled = controlsEnabled,
                    onClick = {
                        refreshScriptCatalog()
                        scriptRulesVisible = true
                    },
                )
                NetworkProxyOperationFeedback(NetworkProxyOperationArea.SCRIPTS, activeOperation, feedback, false)
            }
        }

        if (pageSection == NetworkProxyPageSection.OVERVIEW) item(key = "network_proxy_advanced") {
            KiyoriSettingsGroupSection(
                title = "高级",
                description = "外部 Clash 使用 Android VPN 时无需填写 mixed-port；并存会形成双层链路。",
            ) {
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
                    description = "代理模块先经过 Kiyori Mihomo，再经过外部 Clash/VPN",
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
                    title = "测试地址",
                    description = "用于单节点、整组和代理连通性测试",
                    kind = KiyoriSettingsRowKind.NAVIGATION,
                    icon = Icons.Default.Speed,
                    iconTone = KiyoriSemanticTone.CYAN,
                    value = config?.testUrl,
                    enabled = controlsEnabled,
                    onClick = { testUrlDialogVisible = true },
                )
                KiyoriSettingsDivider()
                KiyoriSettingsRow(
                    title = "重置网络代理",
                    description = "停止核心并删除订阅库、模块规则和加密配置",
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
    }

    currentSubscriptionSheetVisible.takeIf { it }?.let {
        val subscriptions = config?.subscriptions.orEmpty()
        KiyoriSettingsSelectionSheet(
            selection =
                KiyoriSettingsSelection(
                    title = "当前订阅",
                    currentValue = activeSubscription?.displayName ?: "未选择",
                    options =
                        subscriptions.map { subscription ->
                            KiyoriSettingsSelectionOption(
                                label = subscription.displayName,
                                description = subscriptionListDescription(subscription),
                                selected = subscription.id == config?.activeSubscriptionId,
                                onSelect = {
                                    runOperation(
                                        NetworkProxyOperation("switch_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在切换当前订阅"),
                                        "当前订阅已切换为 ${subscription.displayName}。",
                                    ) {
                                        manager.switchActiveSubscription(subscription.id)
                                        inspectedSubscriptionId = subscription.id
                                    }
                                },
                            )
                        },
                ),
            onDismiss = { currentSubscriptionSheetVisible = false },
            onSelect = { option -> option.onSelect() },
            searchable = true,
        )
    }

    subscriptionActionsId?.let { subscriptionId ->
        val subscription = config?.subscriptions?.firstOrNull { it.id == subscriptionId }
        if (subscription == null) {
            subscriptionActionsId = null
        } else {
            val isActive = subscription.id == config.activeSubscriptionId
            KiyoriSettingsSelectionSheet(
                selection =
                    KiyoriSettingsSelection(
                        title = subscription.displayName,
                        currentValue = if (isActive) "当前订阅" else subscriptionSourceLabel(subscription),
                        options =
                            buildList {
                                add(KiyoriSettingsSelectionOption("查看分组与节点", subscriptionDescription(subscription), false) {
                                    subscriptionActionsId = null
                                    inspectedSubscriptionId = subscription.id
                                    selectedGroupTabName = null
                                    pageSection = NetworkProxyPageSection.GROUPS
                                })
                                if (!isActive) {
                                    add(KiyoriSettingsSelectionOption("设为当前订阅", "切换主代理使用的配置", false) {
                                        runOperation(NetworkProxyOperation("switch_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在切换当前订阅"), "当前订阅已切换为 ${subscription.displayName}。") {
                                            manager.switchActiveSubscription(subscription.id)
                                            inspectedSubscriptionId = subscription.id
                                        }
                                    })
                                }
                                if (subscription.sourceType == KiyoriSubscriptionSourceType.URL) {
                                    add(KiyoriSettingsSelectionOption("更新订阅", "从已保存地址下载并原子替换节点", false) {
                                        runOperation(NetworkProxyOperation("update_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在下载、校验并更新订阅"), "${subscription.displayName} 已更新。") {
                                            manager.updateUrlSubscription(subscription.id)
                                        }
                                    })
                                    add(KiyoriSettingsSelectionOption("编辑名称与地址", "地址变化时会重新下载和校验", false) { openSubscriptionEditor(SubscriptionEditor.Edit(subscription.id)) })
                                    add(KiyoriSettingsSelectionOption("复制订阅地址", "链接可能包含访问凭据", false) { copyUrlSubscriptionId = subscription.id })
                                } else {
                                    add(KiyoriSettingsSelectionOption("重新导入 YAML", "选择新文件并原子替换此条目", false) {
                                        yamlImportTarget = YamlImportTarget.Replace(subscription.id)
                                        filePicker.launch(arrayOf("*/*"))
                                    })
                                    add(KiyoriSettingsSelectionOption("重命名", "修改订阅库中的显示名称", false) { openSubscriptionEditor(SubscriptionEditor.Edit(subscription.id)) })
                                }
                                add(KiyoriSettingsSelectionOption("创建副本", "生成新 ID，不切换当前订阅", false) {
                                    runOperation(NetworkProxyOperation("duplicate_subscription", NetworkProxyOperationArea.SUBSCRIPTIONS, "正在创建订阅副本"), "已创建 ${subscription.displayName} 的副本。") {
                                        manager.duplicateSubscription(subscription.id, "${subscription.displayName} 副本")
                                    }
                                })
                                add(KiyoriSettingsSelectionOption("删除", "删除前需要二次确认", false) { deleteSubscriptionId = subscription.id })
                            },
                    ),
                onDismiss = { subscriptionActionsId = null },
                onSelect = { option -> option.onSelect() },
            )
        }
    }

    groupActionTarget?.let { target ->
        val subscription = config?.subscriptions?.firstOrNull { it.id == target.subscriptionId }
        if (subscription == null) {
            groupActionTarget = null
        } else {
            val options = availableGroupItems(subscription, target.group, runtimeState.groups.takeIf { runtimeState.subscriptionId == subscription.id }.orEmpty())
            val runtimeGroup =
                runtimeState.groups
                    .takeIf { runtimeState.subscriptionId == subscription.id }
                    ?.firstOrNull { group -> group.name == target.group.name }
            KiyoriSettingsSelectionSheet(
                selection =
                    KiyoriSettingsSelection(
                        title = groupDisplayName(target.group.name),
                        currentValue =
                            runtimeGroup?.currentItem
                                ?: subscription.selectedGroupItems[target.group.name]
                                ?: "未选择",
                        options =
                            buildList {
                                if (target.group.manuallySelectable && options.isNotEmpty()) {
                                    add(KiyoriSettingsSelectionOption("选择组内项目", "保存此订阅的策略选择", false) {
                                        groupActionTarget = null
                                        groupSelectionTarget = GroupSelectionTarget(subscription.id, target.group)
                                    })
                                }
                                add(KiyoriSettingsSelectionOption("查看组内节点", "按此组筛选节点清单", false) {
                                    groupActionTarget = null
                                    inspectedSubscriptionId = subscription.id
                                    selectedGroupTabName = target.group.name
                                    pageSection = NetworkProxyPageSection.GROUPS
                                })
                                add(KiyoriSettingsSelectionOption("测试整组", "使用 Mihomo /group 延迟接口并发测速", false) {
                                    groupActionTarget = null
                                    runOperation(NetworkProxyOperation("test_group", NetworkProxyOperationArea.GROUPS, "正在测试 ${groupDisplayName(target.group.name)}"), "${groupDisplayName(target.group.name)} 测速完成。") {
                                        manager.testSubscriptionGroup(subscription.id, target.group.name)
                                    }
                                })
                            },
                    ),
                onDismiss = { groupActionTarget = null },
                onSelect = { option -> option.onSelect() },
            )
        }
    }

    groupSelectionTarget?.let { target ->
        val subscription = config?.subscriptions?.firstOrNull { it.id == target.subscriptionId }
        if (subscription == null) {
            groupSelectionTarget = null
        } else {
            val runtimeGroups = runtimeState.groups.takeIf { runtimeState.subscriptionId == subscription.id }.orEmpty()
            val options = availableGroupItems(subscription, target.group, runtimeGroups)
            val currentItem =
                runtimeGroups.firstOrNull { group -> group.name == target.group.name }?.currentItem
                    ?: subscription.selectedGroupItems[target.group.name]
            KiyoriSettingsSelectionSheet(
                selection =
                    KiyoriSettingsSelection(
                        title = "选择 ${groupDisplayName(target.group.name)}",
                        currentValue = currentItem ?: "未选择",
                        options = options.map { item -> KiyoriSettingsSelectionOption(item, selected = item == currentItem, onSelect = {}) },
                    ),
                onDismiss = { groupSelectionTarget = null },
                onSelect = { option ->
                    groupSelectionTarget = null
                    runOperation(NetworkProxyOperation("select_group", NetworkProxyOperationArea.GROUPS, "正在保存策略组选择"), "策略组已切换为 ${option.label}。") {
                        manager.selectGroup(subscription.id, target.group.name, option.label)
                    }
                },
                searchable = true,
            )
        }
    }

    pendingModeSelection?.let { selection ->
        val currentOverride =
            when (selection) {
                is ModeSelection.Module -> config?.moduleModes?.get(selection.module) ?: KiyoriNetworkOverrideMode.INHERIT
                is ModeSelection.Script -> config?.scriptModes?.get(selection.packageName) ?: KiyoriNetworkOverrideMode.INHERIT
            }
        KiyoriSettingsSelectionSheet(
            selection =
                KiyoriSettingsSelection(
                    title = when (selection) {
                        is ModeSelection.Module -> "${networkProxyModuleLabel(selection.module)}连接模式"
                        is ModeSelection.Script -> "${selection.packageName}连接模式"
                    },
                    currentValue = networkProxyOverrideLabel(currentOverride),
                    options =
                        networkProxyModeOptions.map { option ->
                            KiyoriSettingsSelectionOption(
                                label = option.label,
                                description =
                                    if (selection is ModeSelection.Script && option.mode == KiyoriNetworkOverrideMode.INHERIT) {
                                        "移除此脚本的单独规则，跟随传统脚本模块"
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
                val area = if (selection is ModeSelection.Script) NetworkProxyOperationArea.SCRIPTS else NetworkProxyOperationArea.ROUTING
                runOperation(NetworkProxyOperation("connection_mode", area, "正在保存连接模式"), "连接模式已保存。") {
                    manager.updateConfig { current ->
                        when (selection) {
                            is ModeSelection.Module ->
                                current.copy(
                                    moduleModes =
                                        if (selectedMode == KiyoriNetworkOverrideMode.INHERIT) current.moduleModes - selection.module
                                        else current.moduleModes + (selection.module to selectedMode),
                                )
                            is ModeSelection.Script ->
                                current.copy(
                                    scriptModes =
                                        if (selectedMode == KiyoriNetworkOverrideMode.INHERIT) current.scriptModes - selection.packageName
                                        else current.scriptModes + (selection.packageName to selectedMode),
                                )
                        }
                    }
                }
            },
        )
    }

    if (scriptRulesVisible) {
        val configuredScriptPackages = config?.scriptModes?.keys.orEmpty()
        val scriptPackages =
            (discoveredScriptPackages + configuredScriptPackages)
                .distinct()
                .sorted()
        KiyoriSettingsSelectionSheet(
            selection =
                KiyoriSettingsSelection(
                    title = "脚本规则",
                    currentValue =
                        if (scriptCatalogRefreshing) "正在刷新" else "已发现 ${scriptPackages.size} 个",
                    options =
                        buildList {
                            add(
                                KiyoriSettingsSelectionOption(
                                    label = if (scriptCatalogRefreshing) "正在刷新脚本清单" else "刷新脚本清单",
                                    description = "重新读取已启用的传统 JsEngine 包，不改变任何连接规则",
                                    selected = false,
                                    onSelect = { refreshScriptCatalog() },
                                ),
                            )
                            add(
                                KiyoriSettingsSelectionOption(
                                    "手动添加脚本包",
                                    "输入包名后设置单独连接模式；规则不会自动启用脚本",
                                    false,
                                ) { scriptPackageDialogVisible = true },
                            )
                            if (scriptPackages.isEmpty()) {
                                add(
                                    KiyoriSettingsSelectionOption(
                                        "没有已启用的传统脚本",
                                        "ToolPkg 不显示在这里；请先到扩展 → 脚本启用脚本，或手动添加包名",
                                        false,
                                    ) {},
                                )
                            }
                            scriptPackages.forEach { packageName ->
                                val mode = config?.scriptModes?.get(packageName)
                                val isDiscovered = packageName in discoveredScriptPackages
                                add(
                                    KiyoriSettingsSelectionOption(
                                        label = packageName,
                                        description =
                                            if (mode == null) {
                                                if (isDiscovered) "未设置覆盖，跟随传统脚本模块" else "规则仍保留，但当前脚本目录未找到"
                                            } else {
                                                "覆盖：${networkProxyOverrideLabel(mode)}"
                                            },
                                        selected = mode != null,
                                        onSelect = { pendingModeSelection = ModeSelection.Script(packageName) },
                                    ),
                                )
                            }
                        },
                ),
            onDismiss = { scriptRulesVisible = false },
            onSelect = { option -> option.onSelect() },
            searchable = true,
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
                        val added = manager.addSubscriptionUrl(editorUrl, editorName)
                        inspectedSubscriptionId = added.id
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

    copyUrlSubscriptionId?.let { subscriptionId ->
        val subscription = config?.subscriptions?.firstOrNull { it.id == subscriptionId }
        if (subscription == null) {
            copyUrlSubscriptionId = null
        } else {
            AlertDialog(
                onDismissRequest = { copyUrlSubscriptionId = null },
                title = { Text("复制订阅地址？") },
                text = { Text("订阅链接可能包含访问凭据。确认后会写入 Android 系统剪贴板。") },
                confirmButton = {
                    Button(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Clash subscription", subscription.subscriptionUrl))
                        feedback = NetworkProxyFeedback(NetworkProxyOperationArea.SUBSCRIPTIONS, "订阅地址已复制。", false)
                        copyUrlSubscriptionId = null
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("复制")
                    }
                },
                dismissButton = { TextButton(onClick = { copyUrlSubscriptionId = null }) { Text("取消") } },
            )
        }
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

    if (testUrlDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (activeOperation == null) testUrlDialogVisible = false },
            title = { Text("测试地址") },
            text = {
                OutlinedTextField(
                    value = testUrlDraft,
                    onValueChange = { testUrlDraft = it },
                    singleLine = true,
                    label = { Text("HTTPS URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = kiyoriSettingsOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                Button(
                    enabled = activeOperation == null && testUrlDraft.trim().startsWith("https://"),
                    onClick = {
                        val value = testUrlDraft.trim()
                        runOperation(NetworkProxyOperation("test_url", NetworkProxyOperationArea.ADVANCED, "正在保存测试地址"), "测试地址已保存。", onSuccess = { testUrlDialogVisible = false }) {
                            manager.updateConfig { it.copy(testUrl = value) }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(enabled = activeOperation == null, onClick = { testUrlDialogVisible = false }) { Text("取消") } },
        )
    }

    if (scriptPackageDialogVisible) {
        val validPackageName = isValidScriptPackageName(scriptPackageDraft.trim())
        AlertDialog(
            onDismissRequest = { scriptPackageDialogVisible = false },
            title = { Text("添加脚本规则") },
            text = {
                OutlinedTextField(
                    value = scriptPackageDraft,
                    onValueChange = { scriptPackageDraft = it },
                    singleLine = true,
                    label = { Text("脚本包名") },
                    isError = scriptPackageDraft.isNotBlank() && !validPackageName,
                    supportingText = if (scriptPackageDraft.isNotBlank() && !validPackageName) ({ Text("仅允许字母、数字、点、下划线、冒号和连字符") }) else null,
                    colors = kiyoriSettingsOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                Button(enabled = validPackageName, onClick = {
                    val packageName = scriptPackageDraft.trim()
                    scriptPackageDialogVisible = false
                    scriptPackageDraft = ""
                    pendingModeSelection = ModeSelection.Script(packageName)
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { scriptPackageDialogVisible = false }) { Text("取消") } },
        )
    }

    if (resetDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (activeOperation == null) resetDialogVisible = false },
            title = { Text("重置网络代理？") },
            text = { Text("这会停止内嵌 Mihomo，并删除全部订阅、模块规则和加密代理配置。") },
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

private fun availableGroupItems(
    subscription: KiyoriProxySubscription,
    group: MihomoProxyGroupSummary,
    runtimeGroups: List<MihomoRuntimeGroupState>,
): List<String> {
    val live = runtimeGroups.firstOrNull { it.name == group.name }?.allItems.orEmpty()
    val discovered = subscription.nodeTests.filter { group.name in it.groupNames }.map(MihomoNodeTestResult::name)
    return (live + group.options + discovered).distinct()
}

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

private fun networkProxyModuleLabel(module: KiyoriNetworkModule): String =
    networkProxyModules.first { it.first == module }.second

private fun networkProxyModuleDescription(module: KiyoriNetworkModule): String =
    when (module) {
        KiyoriNetworkModule.AI_SERVICES -> "模型列表、主模型、云端 embedding、语音和实时语音"
        KiyoriNetworkModule.AI_TOOLS -> "普通 AI 工具的 HTTP 请求和 web visit"
        KiyoriNetworkModule.BROWSER -> "WebView、用户脚本资源和浏览器辅助请求"
        KiyoriNetworkModule.DOWNLOADS -> "浏览器下载、模型和扩展资源下载"
        KiyoriNetworkModule.PLAYER -> "mpv 的 HTTP、HLS/DASH、字幕和封面请求"
        KiyoriNetworkModule.SCRIPTS -> "传统 JsEngine 脚本宿主网络，可按包覆盖"
        KiyoriNetworkModule.APP_SERVICES -> "GitHub、市场、天气、规则订阅和网络图片"
    }

private fun networkProxyOverrideLabel(mode: KiyoriNetworkOverrideMode): String =
    when (mode) {
        KiyoriNetworkOverrideMode.INHERIT -> "跟随默认"
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
        phase == KiyoriMihomoRuntimePhase.RUNNING -> "运行中 · ${activeSubscription.displayName}"
        phase == KiyoriMihomoRuntimePhase.STARTING -> "正在启动 ${activeSubscription.displayName}"
        phase == KiyoriMihomoRuntimePhase.STOPPING -> "正在停止内嵌 Mihomo"
        phase == KiyoriMihomoRuntimePhase.ERROR -> "运行失败；设置仍保留"
        else -> "已开启；等待代理模块发起请求"
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

private fun isValidScriptPackageName(value: String): Boolean =
    value.matches(Regex("[A-Za-z0-9._:-]{1,200}"))

@Composable
private fun NetworkProxyNodeGrid(
    nodes: List<MihomoNodeTestResult>,
    layout: NetworkProxyNodeLayout,
    selectedNodeName: String?,
    selectable: Boolean,
    enabled: Boolean,
    onSelect: (MihomoNodeTestResult) -> Unit,
    onTest: (MihomoNodeTestResult) -> Unit,
) {
    if (layout.columns == 1) {
        nodes.forEachIndexed { index, node ->
            NetworkProxyNodeRow(
                node = node,
                selected = node.name == selectedNodeName,
                selectable = selectable,
                enabled = enabled,
                onSelect = { onSelect(node) },
                onTest = { onTest(node) },
            )
            if (index != nodes.lastIndex) KiyoriSettingsDivider()
        }
    } else {
        val rows = nodes.chunked(layout.columns)
        rows.forEachIndexed { rowIndex, rowNodes ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowNodes.forEach { node ->
                    NetworkProxyNodeTile(
                        node = node,
                        selected = node.name == selectedNodeName,
                        selectable = selectable,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelect(node) },
                        onTest = { onTest(node) },
                    )
                }
                repeat(layout.columns - rowNodes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (rowIndex != rows.lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NetworkProxyNodeTile(
    node: MihomoNodeTestResult,
    selected: Boolean,
    selectable: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onTest: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Column(
        modifier =
            modifier
                .alpha(if (enabled) 1f else 0.42f)
                .background(colors.pageBackground, RoundedCornerShape(10.dp))
                .border(BorderStroke(0.6.dp, colors.divider), RoundedCornerShape(10.dp))
                .clickable(enabled = enabled && selectable, onClick = onSelect)
                .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = node.name,
                color = colors.primaryText,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "当前节点",
                    tint = colors.accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(
                enabled = enabled,
                onClick = onTest,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = "测速 ${node.name}",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = nodeDelayLabel(node),
            color = colors.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text =
                if (selectable) {
                    "点击切换 · ${node.type}"
                } else {
                    "自动策略 · ${node.type}"
                },
            color = colors.secondaryText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun NetworkProxyNodeRow(
    node: MihomoNodeTestResult,
    selected: Boolean,
    selectable: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
) {
    val colors = LocalKiyoriSettingsColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.42f)
                .clickable(enabled = enabled && selectable, onClick = onSelect)
                .padding(start = 18.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(
                        if (selected) colors.accent.copy(alpha = 0.16f) else colors.pageBackground,
                        RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.mutedIcon,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (selectable) {
                        "点击节点切换 · ${nodeDescription(node)}"
                    } else {
                        "自动策略组由 Mihomo 决定 · ${nodeDescription(node)}"
                    },
                color = colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = nodeDelayLabel(node),
            color = colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
        IconButton(
            enabled = enabled,
            onClick = onTest,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = "测速 ${node.name}",
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            KiyoriNetworkConnectionMode.DIRECT to "直连",
            KiyoriNetworkConnectionMode.PROXY to "代理",
        ).forEach { (mode, label) ->
            val isSelected = selected == mode
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
