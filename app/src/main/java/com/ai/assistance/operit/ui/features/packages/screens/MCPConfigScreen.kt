package com.ai.assistance.operit.ui.features.packages.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.validateMcpRemoteEndpoint
import com.ai.assistance.operit.data.mcp.validateMcpHeaders
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer
import com.ai.assistance.operit.ui.features.packages.components.dialogs.MCPServerDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.MCPPackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPCommandsEditDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPDeployConfirmDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPDeployProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPInstallProgressDialog
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPDeployViewModel
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPViewModel
import com.ai.assistance.operit.data.mcp.plugins.MCPBridge
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import org.json.JSONObject

import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.InstallProgress
import com.ai.assistance.operit.ui.features.startup.screens.LocalPluginLoadingState
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

internal fun mcpImportTabIndices(): IntRange = 0..3

/** MCP配置屏幕 - 极简风格界面，专注于插件快速部署 */
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPConfigScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val resources = LocalResources.current
    val activity = context as? androidx.activity.ComponentActivity
    val mcpLocalServer = remember { MCPLocalServer.getInstance(context) }

    val scope = rememberCoroutineScope()

    val pluginLoadingState = LocalPluginLoadingState.current

    val viewModel: MCPViewModel = viewModel(key = "kiyori-mcp-manager", factory = MCPViewModel.Factory(context.applicationContext))
    val mcpRepository = viewModel.repository
    val deployViewModel: MCPDeployViewModel = viewModel(key = "kiyori-mcp-deploy",
        factory = MCPDeployViewModel.Factory(context.applicationContext, mcpRepository))

    // 状态收集
    val serverStatusMap = mcpLocalServer.serverStatus.collectAsState().value
    val installProgress by viewModel.installProgress.collectAsState()
    val installResult by viewModel.installResult.collectAsState()
    val currentInstallingPlugin by viewModel.currentServer.collectAsState()
    val configurationReadError by mcpLocalServer.configurationReadError.collectAsState()
    var reloadFailed by remember { mutableStateOf(false) }
    var reloadingConfig by remember { mutableStateOf(false) }
    val mcpConfigSnapshot = mcpLocalServer.mcpConfig.collectAsState().value
    val discoveredInstalledPluginIds = mcpRepository.installedPluginIds.collectAsState().value
    val configuredPluginIds = remember(mcpConfigSnapshot) {
        mcpConfigSnapshot.mcpServers.keys.toSet()
    }
    val remotePluginIds = remember(mcpConfigSnapshot) {
        mcpConfigSnapshot.pluginMetadata
            .filterValues { metadata -> metadata.type == "remote" }
            .keys
            .toSet()
    }
    val visiblePluginIds = remember(configuredPluginIds, remotePluginIds, discoveredInstalledPluginIds) {
        configuredPluginIds + remotePluginIds + discoveredInstalledPluginIds
    }

    // 部署状态
    val deploymentStatus by deployViewModel.deploymentStatus.collectAsState()
    val outputMessages by deployViewModel.outputMessages.collectAsState()
    val currentDeployingPlugin by deployViewModel.currentDeployingPlugin.collectAsState()
    val environmentVariables by deployViewModel.environmentVariables.collectAsState()
    


    // 标记是否已经执行过初始化时的自动启动
    var initialAutoStartPerformed = remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var isToolsLoading by remember { mutableStateOf(true) }
    var pendingPluginId by remember { mutableStateOf<String?>(null) }
    var toolRefreshTrigger by remember { mutableStateOf(0) }

    // Freeze list order within this screen session (avoid jumping when status changes)
    var lockedPluginOrder by remember { mutableStateOf<List<String>?>(null) }

    var refreshFailed by remember { mutableStateOf(false) }
    suspend fun refreshMcpScreen() {
        if (isRefreshing) return
        isRefreshing = true
        refreshFailed = false
        try {
            mcpRepository.syncBridgeStatus()
            mcpRepository.refreshPluginList()
            lockedPluginOrder = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            refreshFailed = true
            isToolsLoading = false
        } finally {
            isRefreshing = false
        }
    }

    fun awaitPluginVisible(pluginId: String, onDone: () -> Unit) {
        pendingPluginId = pluginId
        scope.launch {
            try {
                refreshMcpScreen()
                withTimeoutOrNull(20_000) {
                    mcpLocalServer.mcpConfig.first { config ->
                        config.mcpServers.containsKey(pluginId) ||
                            config.pluginMetadata.containsKey(pluginId)
                    }
                }
            } finally {
                pendingPluginId = null
                onDone()
            }
        }
    }

    // 在应用启动时检查自动启动设置，而不是等待UI完全加载
    LaunchedEffect(Unit) {
        // 仅在首次加载时执行一次
        if (!initialAutoStartPerformed.value) {
            com.ai.assistance.operit.util.AppLogger.d("MCPConfigScreen", "初始化 - 检查服务器状态")

            refreshMcpScreen()

            // 只记录服务器状态，不再重复启动服务器(已由 Application 中的 initAndAutoStartPlugins 控制)
            val anyServerRunning = visiblePluginIds.any { pluginId ->
                mcpLocalServer.isServerLikelyRunning(pluginId)
            }
            if (anyServerRunning) {
                com.ai.assistance.operit.util.AppLogger.d("MCPConfigScreen", "MCP服务器已在运行")
            } else {
                com.ai.assistance.operit.util.AppLogger.d("MCPConfigScreen", "MCP服务器未运行")
            }

            // 读取并记录已安装的MCP插件列表，但不执行任何操作
            com.ai.assistance.operit.util.AppLogger.d("MCPConfigScreen", "已安装的MCP插件列表:")
            visiblePluginIds.forEach { pluginId ->
                try {
                    val isEnabled = mcpLocalServer.isServerEnabled(pluginId) // 从配置读取
                    com.ai.assistance.operit.util.AppLogger.d("MCPConfigScreen", "插件ID: $pluginId, 已启用: $isEnabled")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    com.ai.assistance.operit.util.AppLogger.e("MCPConfigScreen", "无法读取插件 $pluginId 的启用状态: ${e.message}")
                }
            }

            initialAutoStartPerformed.value = true
            toolRefreshTrigger++
        }
    }

    // 界面状态
    var selectedPluginForDetails by remember {
        mutableStateOf<MCPLocalServer.PluginMetadata?>(
                null
        )
    }
    var selectedPluginForToolDetails by remember {
        mutableStateOf<MCPLocalServer.PluginMetadata?>(null)
    }
    var pluginToDeploy by remember { mutableStateOf<String?>(null) }

    // 添加新的状态变量来跟踪对话框展示
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCustomCommandsDialog by remember { mutableStateOf(false) }

    // 添加导入对话框状态
    var showImportDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var pluginNameInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    // 新增：导入方式选择和压缩包路径
    var importTabIndex by remember { mutableStateOf(0) } // 0: 仓库导入, 1: 压缩包导入
    var zipFilePath by remember { mutableStateOf("") }
    var isSelectingZip by remember { mutableStateOf(false) }

    // 新增：远程服务相关状态
    var remoteEndpointInput by remember { mutableStateOf("") }
    var remoteConnectionType by remember { mutableStateOf("httpStream") }
    var remoteConnectionTypeExpanded by remember { mutableStateOf(false) }
    var remoteBearerToken by remember { mutableStateOf("") }
    var remoteHeaders by remember { mutableStateOf<List<EditableHeader>>(emptyList()) }
    
    // 新增：配置导入相关状态
    var configJsonInput by remember { mutableStateOf("") }

    // 新增：远程服务编辑对话框状态
    var showRemoteEditDialog by remember { mutableStateOf(false) }
    var editingRemoteServer by remember { mutableStateOf<MCPLocalServer.PluginMetadata?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) isSelectingZip = false
        else scope.launch {
            try {
                val displayName = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                    }.orEmpty().ifBlank { "ZIP" }
                }
                if (showImportDialog && importTabIndex == 1) {
                    zipFilePath = displayName
                    viewModel.setSelectedZipUri(uri)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, context.getString(R.string.mcp_zip_selection_failed), Toast.LENGTH_LONG).show() }
            finally { isSelectingZip = false }
        }
    }

    val importDialogMaxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.65f).dp



    // Effect to fetch and display tools when MCP servers start
    val isPluginLoading by pluginLoadingState.isVisible.collectAsState()
    val wasPluginLoading = remember { mutableStateOf(isPluginLoading) }

    LaunchedEffect(isPluginLoading) {
        if (wasPluginLoading.value && !isPluginLoading) {
            // Loading has just finished, trigger a refresh.
            isToolsLoading = true
            lockedPluginOrder = null
            toolRefreshTrigger++
        }
        wasPluginLoading.value = isPluginLoading
    }
    
    // 存储每个插件的工具信息
    var pluginToolsMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    // 计算插件启动统计 - 只统计已启用的插件
    val totalEnabledPlugins = remember(visiblePluginIds, mcpConfigSnapshot) {
        visiblePluginIds.count { pluginId -> mcpLocalServer.isServerEnabled(pluginId) }
    }
    val successfulToolRequests = remember(pluginToolsMap, mcpConfigSnapshot, visiblePluginIds) {
        pluginToolsMap.count { (id, tools) -> id in visiblePluginIds && tools.isNotEmpty() && mcpLocalServer.isServerEnabled(id) }
    }

    val computedSortedPluginIds = remember(
        visiblePluginIds,
        pluginToolsMap,
        serverStatusMap,
        mcpConfigSnapshot
    ) {
        visiblePluginIds
            .toList()
            .sortedWith(
                compareBy<String> { pluginId ->
                    val enabled = mcpLocalServer.isServerEnabled(pluginId)
                    val loaded = pluginToolsMap[pluginId]?.isNotEmpty() == true
                    when {
                        enabled && loaded -> 0
                        enabled -> 1
                        else -> 2
                    }
                }.thenBy { pluginId ->
                    getPluginDisplayName(pluginId, mcpRepository).lowercase(Locale.getDefault())
                }
            )
    }

    // Lock order once tools are loaded (so the initial "good" sort is applied, then frozen)
    LaunchedEffect(isToolsLoading, visiblePluginIds, toolRefreshTrigger) {
        if (lockedPluginOrder == null && visiblePluginIds.isNotEmpty() && !isToolsLoading) {
            lockedPluginOrder = computedSortedPluginIds
        }
    }

    val sortedPluginIds = remember(lockedPluginOrder, visiblePluginIds, computedSortedPluginIds) {
        val visibleSet = visiblePluginIds.toSet()
        val base = (lockedPluginOrder ?: computedSortedPluginIds)
        val kept = base.filter { visibleSet.contains(it) }
        val missing = visibleSet - kept.toSet()
        if (missing.isEmpty()) {
            kept
        } else {
            kept + missing.sortedBy { pluginId ->
                getPluginDisplayName(pluginId, mcpRepository).lowercase(Locale.getDefault())
            }
        }
    }

    val displayedPluginIds = remember(
        sortedPluginIds,
        searchQuery,
        mcpConfigSnapshot,
        pluginToolsMap
    ) {
        val searchText = searchQuery.trim()
        if (searchText.isEmpty()) {
            sortedPluginIds
        } else {
            sortedPluginIds.filter { pluginId ->
                mcpPluginMatchesSearch(
                    pluginId = pluginId,
                    displayName = getPluginDisplayName(pluginId, mcpRepository),
                    metadata = mcpConfigSnapshot.pluginMetadata[pluginId],
                    toolNames = pluginToolsMap[pluginId],
                    searchText = searchText
                )
            }
        }
    }

    LaunchedEffect(toolRefreshTrigger) {
        if (toolRefreshTrigger == 0) {
            return@LaunchedEffect
        }

        isToolsLoading = true
        if (visiblePluginIds.isEmpty()) {
            AppLogger.d("MCPConfigScreen", "No configured plugins, clearing tool list.")
            pluginToolsMap = emptyMap()
            isToolsLoading = false
            return@LaunchedEffect
        }

        AppLogger.d("MCPConfigScreen", "Fetching tools for configured runtime-ready services...")

        val toolsMap = mutableMapOf<String, List<String>>()

        try {
            val bridgeServiceTools = parseMCPServiceToolNames(
                MCPBridge.getInstance(context).listMcpServices()
            )

            for (pluginId in visiblePluginIds) {
                try {
                    val metadata = mcpConfigSnapshot.pluginMetadata[pluginId]
                    val isRemote = metadata?.type == "remote"
                    val isDeployed = if (isRemote) true else mcpLocalServer.isPluginRuntimeReady(pluginId)
                    if (!isDeployed) {
                        AppLogger.d("MCPConfigScreen", "Plugin $pluginId runtime directory is not ready, skip tool fetch.")
                        continue
                    }

                    val toolNames = bridgeServiceTools[pluginId].orEmpty()

                    if (toolNames.isNotEmpty()) {
                        toolsMap[pluginId] = toolNames
                        AppLogger.d("MCPConfigScreen", "Plugin $pluginId has ${toolNames.size} tools: ${toolNames.joinToString(", ")}")
                    } else {
                        AppLogger.d("MCPConfigScreen", "Plugin $pluginId: no tools found.")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    AppLogger.e("MCPConfigScreen", "Error getting tools for plugin $pluginId: ${e.message}")
                }
            }

            // 更新工具映射
            pluginToolsMap = toolsMap

            if (toolsMap.isNotEmpty()) {
                val totalTools = toolsMap.values.sumOf { it.size }
                AppLogger.i("MCPConfigScreen", "Loaded $totalTools tools from ${toolsMap.size} plugins")
            } else {
                AppLogger.i("MCPConfigScreen", "No tools found for any installed plugins.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e("MCPConfigScreen", "Error fetching tools", e)
            Toast.makeText(context, context.getString(R.string.tools_load_error, e.message), Toast.LENGTH_SHORT).show()
        } finally {
            isToolsLoading = false
        }
    }


    // 监听部署状态变化，当成功时显示提示
    LaunchedEffect(deploymentStatus) {
        if (deploymentStatus is MCPDeployer.DeploymentStatus.Success) {
            currentDeployingPlugin?.let { pluginId ->
                Toast.makeText(context, context.getString(R.string.plugin_deployed_success, getPluginDisplayName(pluginId, mcpRepository)), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 可见详情持有固定目标和打开时的配置；保存直接接收正文，不经延迟 effect 回传。
    selectedPluginForDetails?.takeIf { isCurrentScreen }?.let { selected ->
        key(selected.id) {
            val installedPath = viewModel.getInstalledPath(selected.id)
            val initialConfig = remember { mcpLocalServer.getPluginConfig(selected.id) }
            var expectedConfig by remember { mutableStateOf(initialConfig) }
            MCPServerDetailsDialog(
                server = selected,
                onDismiss = { selectedPluginForDetails = null },
                onInstall = {},
                onUninstall = { server ->
                    viewModel.uninstallServer(server)
                    selectedPluginForDetails = null
                },
                installedPath = installedPath,
                pluginConfig = initialConfig,
                onSaveConfig = { draft ->
                    val saved = mcpLocalServer.savePluginConfig(selected.id, draft, expectedConfig)
                    if (saved) expectedConfig = draft
                    saved
                }
            )
        }
    }

    if (isCurrentScreen && selectedPluginForToolDetails != null) {
        val installedPath = viewModel.getInstalledPath(selectedPluginForToolDetails!!.id)
        MCPPackageDetailsDialog(
                server = selectedPluginForToolDetails!!,
                installedPath = installedPath,
                onDismiss = { selectedPluginForToolDetails = null }
        )
    }

    // 部署确认对话框 - 新增
    if (isCurrentScreen && showConfirmDialog && pluginToDeploy != null) {
        com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPDeployConfirmDialog(
                pluginName = getPluginDisplayName(pluginToDeploy!!, mcpRepository),
                onDismissRequest = {
                    showConfirmDialog = false
                    pluginToDeploy = null
                },
                onConfirm = {
                    // 在协程内部复制当前的pluginId避免外部状态变化导致空指针异常
                    val pluginId = pluginToDeploy!!
                    
                    // 使用默认命令部署（会自动获取命令）
                    deployViewModel.deployPlugin(pluginId)

                    // 重置状态
                    showConfirmDialog = false
                    pluginToDeploy = null
                },
                onCustomize = {
                    // 先关闭确认对话框，然后显示命令编辑对话框
                    showConfirmDialog = false
                    showCustomCommandsDialog = true

                }
        )
    }

    if (isCurrentScreen && showCustomCommandsDialog && pluginToDeploy != null) {
        val pluginId = pluginToDeploy!!
        val commandState by deployViewModel.commandState.collectAsState()
        key(pluginId) {
            var requested by remember { mutableStateOf(false) }
            LaunchedEffect(pluginId) { requested = true; deployViewModel.getDeployCommands(pluginId) }
            MCPCommandsEditDialog(
                pluginName = getPluginDisplayName(pluginId, mcpRepository),
                commands = if (commandState.pluginId == pluginId) commandState.commands else emptyList(),
                isLoading = !requested || commandState.pluginId != pluginId || commandState.loading,
                loadError = commandState.error.takeIf { commandState.pluginId == pluginId },
                onRetry = { scope.launch { deployViewModel.getDeployCommands(pluginId) } },
                onDismissRequest = { showCustomCommandsDialog = false; pluginToDeploy = null },
                onConfirm = { commands ->
                    deployViewModel.deployPluginWithCommands(pluginId, commands)
                    showCustomCommandsDialog = false
                    pluginToDeploy = null
                }
            )
        }
    }

    // 新增：远程服务编辑对话框
    if (isCurrentScreen && showRemoteEditDialog && editingRemoteServer != null) {
        RemoteServerEditDialog(
            server = editingRemoteServer!!,
            onDismiss = {
                showRemoteEditDialog = false
                editingRemoteServer = null
            },
            onSave = { updatedServer ->
                viewModel.updateRemoteServer(updatedServer, editingRemoteServer!!)
                showRemoteEditDialog = false
                editingRemoteServer = null
                Toast.makeText(context, context.getString(R.string.remote_service_updated, updatedServer.name), Toast.LENGTH_SHORT).show()
            },
            onRegenerateDescription = { server, pluginName ->
                viewModel.generatePluginDescription(
                    server = server,
                    pluginName = pluginName
                )
            }
        )
    }


    // 部署进度对话框
    if (isCurrentScreen && currentDeployingPlugin != null) {
        MCPDeployProgressDialog(
                deploymentStatus = deploymentStatus,
                isDeploying = deployViewModel.isDeploying.collectAsState().value,
                omittedOutputLines = deployViewModel.omittedOutputLines.collectAsState().value,
                onDismissRequest = { deployViewModel.resetDeploymentState() },
                onRetry = {
                    currentDeployingPlugin?.let { pluginId ->
                        deployViewModel.deployPlugin(pluginId)
                    }
                },
                pluginName = currentDeployingPlugin?.let { getPluginDisplayName(it, mcpRepository) } ?: "",
                outputMessages = outputMessages,
                environmentVariables = environmentVariables,
                onEnvironmentVariablesChange = { newEnvVars ->
                    deployViewModel.setEnvironmentVariables(newEnvVars)
                }
        )
    }

    // 安装进度对话框
    if (isCurrentScreen && installProgress != null && currentInstallingPlugin != null) {
        val isUninstallOperation by viewModel.isUninstallOperation.collectAsState()
        MCPInstallProgressDialog(
                installProgress = installProgress,
                onDismissRequest = { viewModel.resetInstallState() },
                onRetry = { viewModel.retryLastOperation() },
                result = installResult,
                                        serverName = currentInstallingPlugin?.name ?: stringResource(R.string.mcp_plugin),
                // 添加操作类型参数：卸载/安装
                operationType = if (isUninstallOperation) stringResource(R.string.uninstall) else stringResource(R.string.install)
        )
    }

    // 导入插件对话框
    if (isCurrentScreen && showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            title = { Text(stringResource(R.string.import_or_connect_mcp_service)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = importDialogMaxContentHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 添加顶部导入方式选择
                    Column {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = importTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            edgePadding = 8.dp,
                            modifier = Modifier.height(48.dp),
                            divider = {},
                            indicator = {
                                if (importTabIndex in mcpImportTabIndices()) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(importTabIndex)
                                    )
                                }
                            }
                        ) {
                        Tab(
                            selected = importTabIndex == 0,
                            onClick = { if (!isImporting) importTabIndex = 0 },
                            modifier = Modifier.height(48.dp),
                            text = { 
                                Text(
                                    stringResource(R.string.import_from_repo),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                ) 
                            }
                        )
                        Tab(
                            selected = importTabIndex == 1,
                            onClick = { if (!isImporting) importTabIndex = 1 },
                            modifier = Modifier.height(48.dp),
                            text = { 
                                Text(
                                    stringResource(R.string.import_from_zip),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                ) 
                            }
                        )
                        Tab(
                            selected = importTabIndex == 2,
                            onClick = { if (!isImporting) importTabIndex = 2 },
                            modifier = Modifier.height(48.dp),
                            text = { 
                                Text(
                                    stringResource(R.string.connect_remote_service),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                ) 
                            }
                        )
                        Tab(
                            selected = importTabIndex == 3,
                            onClick = { if (!isImporting) importTabIndex = 3 },
                            text = {
                                Text(
                                    stringResource(R.string.mcp_config_import),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                        
                        // 滚动提示
                        if (importTabIndex < 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.mcp_more_options),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.mcp_swipe_for_more),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when (importTabIndex) {
                        0 -> {
                            // 从仓库导入
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.enter_repo_info), 
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        showImportDialog = false
                                        onNavigateToMCPMarket()
                                    }
                                ) {
                                    Text(stringResource(R.string.get_mcp))
                                }
                            }
                            
                            OutlinedTextField(
                                enabled = !isImporting,
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                label = { Text(stringResource(R.string.repo_link)) },
                                placeholder = { Text("https://github.com/username/repo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                        }
                        1 -> {
                            // 从压缩包导入
                            Text(stringResource(R.string.select_mcp_plugin_zip), style = MaterialTheme.typography.bodyMedium)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                enabled = !isImporting,
                                    value = zipFilePath,
                                    onValueChange = { /* 只读 */ },
                                    label = { Text(stringResource(R.string.plugin_zip_file)) },
                                    placeholder = { Text(stringResource(R.string.select_zip_file)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = true
                                )
                                
                                IconButton(enabled = !isImporting && !isSelectingZip, onClick = {
                                    isSelectingZip = true
                                    try { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                                    catch (_: Exception) {
                                        isSelectingZip = false
                                        Toast.makeText(context, context.getString(R.string.mcp_zip_selection_failed), Toast.LENGTH_LONG).show()
                                    }
                                }) {
                                    Icon(Icons.Outlined.Folder, contentDescription = stringResource(R.string.select_file))
                                }
                            }
                        }
                        2 -> {
                            // 连接远程服务
                            Text(stringResource(R.string.enter_remote_service_info), style = MaterialTheme.typography.bodyMedium)

                            OutlinedTextField(
                                enabled = !isImporting,
                                value = remoteEndpointInput,
                                onValueChange = { remoteEndpointInput = it },
                                label = { Text(stringResource(R.string.host_address)) },
                                placeholder = { Text("http://127.0.0.1:8752") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val connectionTypes = listOf("httpStream", "sse")
                            ExposedDropdownMenuBox(
                                expanded = remoteConnectionTypeExpanded,
                                onExpandedChange = { if (!isImporting) remoteConnectionTypeExpanded = !remoteConnectionTypeExpanded },
                            ) {
                                OutlinedTextField(
                                enabled = !isImporting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                    value = remoteConnectionType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.connection_type)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = remoteConnectionTypeExpanded) },
                                )
                                ExposedDropdownMenu(
                                    expanded = remoteConnectionTypeExpanded,
                                    onDismissRequest = { remoteConnectionTypeExpanded = false },
                                ) {
                                    connectionTypes.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                remoteConnectionType = selectionOption
                                                remoteConnectionTypeExpanded = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                enabled = !isImporting,
                                value = remoteBearerToken,
                                visualTransformation = PasswordVisualTransformation(),
                                onValueChange = { remoteBearerToken = it },
                                label = { Text(stringResource(R.string.mcp_remote_bearer_token)) },
                                placeholder = { Text(stringResource(R.string.mcp_remote_bearer_token_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            RemoteHeadersEditor(
                                headers = remoteHeaders,
                                onHeadersChange = { remoteHeaders = it },
                                enabled = !isImporting
                            )
                        }
                        3 -> {
                            // 配置导入
                            Text(stringResource(R.string.mcp_paste_config_json), style = MaterialTheme.typography.bodyMedium)

                            OutlinedTextField(
                                enabled = !isImporting,
                                value = configJsonInput,
                                onValueChange = { configJsonInput = it },
                                label = { Text(stringResource(R.string.mcp_config_content)) },
                                placeholder = { Text("{\n  \"mcpServers\": {\n    \"playwright\": {\n      \"command\": \"npx\",\n      \"args\": [\"@playwright/mcp@latest\"]\n    }\n  }\n}") },
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                maxLines = 8
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            TextButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    intent.setDataAndType(android.net.Uri.parse(mcpLocalServer.getConfigFilePath()), "application/json")
                                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    try {
                                        context.startActivity(intent)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        val fileIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                        fileIntent.setDataAndType(android.net.Uri.parse("file://${mcpLocalServer.getConfigFilePath()}"), "*/*")
                                        fileIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        try {
                                            context.startActivity(android.content.Intent.createChooser(fileIntent, context.getString(R.string.mcp_open_config_file)))
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, context.getString(R.string.mcp_config_file_location, mcpLocalServer.getConfigFilePath()), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.mcp_open_config_file), fontSize = 12.sp)
                            }
                        }
                    }
                    
                    if (importTabIndex != 3) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.service_metadata), style = MaterialTheme.typography.titleSmall)
                        
                        OutlinedTextField(
                                enabled = !isImporting,
                            value = pluginNameInput,
                            onValueChange = { newValue ->
                                // 只允许英文字母、数字和下划线
                                val filtered = newValue.filter { it.isLetterOrDigit() || it == '_' }
                                pluginNameInput = filtered
                            },
                            label = { Text(stringResource(R.string.plugin_name)) },
                            placeholder = { Text(stringResource(R.string.my_mcp_plugin)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { 
                                Text(
                                    stringResource(R.string.plugin_name_description),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isRemote = importTabIndex == 2
                        val isConfigImport = importTabIndex == 3
                        val isRepoImport = importTabIndex == 0 && repoUrlInput.isNotBlank() && pluginNameInput.isNotBlank()
                        val isZipImport = importTabIndex == 1 && zipFilePath.isNotBlank() && pluginNameInput.isNotBlank()
                        val isRemoteConnect = isRemote && remoteEndpointInput.isNotBlank() && pluginNameInput.isNotBlank()

                        if (isConfigImport) {
                            if (configJsonInput.isNotBlank()) {
                                val submittedConfig = configJsonInput
                                isImporting = true
                                scope.launch {
                                    try {
                                        val result = mcpLocalServer.mergeConfigFromJson(submittedConfig)
                                        result.onSuccess { count ->
                                            AppLogger.i("MCPConfigScreen", "配置导入成功，合并了 $count 个服务器")
                                            Toast.makeText(context, context.getString(R.string.mcp_merged_servers, count), Toast.LENGTH_SHORT).show()
                                            refreshMcpScreen()
                                            configJsonInput = ""
                                            showImportDialog = false
                                        }.onFailure { error ->
                                            AppLogger.e("MCPConfigScreen", "配置导入失败: ${error.message}", error)
                                            Toast.makeText(context, context.getString(R.string.mcp_merge_failed, error.message ?: context.getString(R.string.unknown_error)), Toast.LENGTH_LONG).show()
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        AppLogger.e("MCPConfigScreen", "配置导入异常", e)
                                        Toast.makeText(context, context.getString(R.string.mcp_import_exception, e.message ?: context.getString(R.string.unknown_error)), Toast.LENGTH_LONG).show()
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.mcp_please_enter_config), Toast.LENGTH_SHORT).show()
                            }
                        } else if (isRepoImport || isZipImport || isRemoteConnect) {
                            // 检查插件ID是否冲突
                            val proposedId = pluginNameInput.trim().replace(" ", "_").lowercase(Locale.ROOT)
                            if (visiblePluginIds.contains(proposedId)) {
                                Toast.makeText(context, context.getString(R.string.plugin_already_exists, pluginNameInput), Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (isRemote) {
                                try {
                                    validateMcpRemoteEndpoint(remoteEndpointInput.trim(), remoteConnectionType)
                                    validateMcpHeaders(remoteHeaders.map { it.key.trim() to it.value })
                                } catch (_: IllegalArgumentException) {
                                    Toast.makeText(context, context.getString(R.string.mcp_edit_save_failed), Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                            }
                            isImporting = true
                            // 生成一个唯一的ID，移除 "import_" 前缀
                            val importId = proposedId
                            
                            // 创建服务器对象（描述将由自动生成功能填充）
                            val server = MCPLocalServer.PluginMetadata(
                                id = importId,
                                name = pluginNameInput,
                                description = "", // 将由自动生成功能填充
                                logoUrl = "",
                                author = "",
                                isInstalled = isRemote, // 远程服务视为"已安装"
                                version = "1.0.0",
                                updatedAt = "",
                                longDescription = "", // 将由自动生成功能填充
                                repoUrl = if (importTabIndex == 0) repoUrlInput else "",
                                type = if(isRemote) "remote" else "local",
                                endpoint = if(isRemote) remoteEndpointInput else null,
                                connectionType = if(isRemote) remoteConnectionType else "httpStream",
                                bearerToken = if(isRemote && remoteBearerToken.isNotBlank()) remoteBearerToken else null,
                                headers = if(isRemote) remoteHeaders.toHeaderMap() else null
                            )
                            
                            if(isRemote){
                                // 对于远程服务，直接保存到仓库
                                scope.launch {
                                    try {
                                        validateMcpRemoteEndpoint(server.endpoint?.trim(), server.connectionType)
                                        viewModel.addRemoteServer(server.copy(endpoint = server.endpoint?.trim()))
                                        showImportDialog = false
                                        pluginNameInput = ""
                                        remoteEndpointInput = ""
                                        remoteBearerToken = ""
                                        remoteHeaders = emptyList()
                                        Toast.makeText(context, context.getString(R.string.remote_service_added, server.name), Toast.LENGTH_SHORT).show()
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) {
                                        Toast.makeText(context, context.getString(R.string.mcp_edit_save_failed), Toast.LENGTH_LONG).show()
                                    } finally { isImporting = false }
                                }
                                return@Button
                            } else {
                                // 本地插件走安装流程
                            if (importTabIndex == 0) {
                                viewModel.installServerWithObject(server)
                            } else {
                                viewModel.installServerFromZip(server, zipFilePath)
                                }
                            }
                            
                            // 清空输入并关闭对话框
                            repoUrlInput = ""
                            pluginNameInput = ""
                            zipFilePath = ""
                            remoteEndpointInput = ""
                            remoteConnectionType = "httpStream"
                            remoteConnectionTypeExpanded = false
                            remoteBearerToken = ""
                            remoteHeaders = emptyList()
                            showImportDialog = false

                            awaitPluginVisible(importId) {
                                isImporting = false
                            }
                        } else {
                            val errorMessage = when (importTabIndex) {
                                0 -> context.getString(R.string.enter_repo_link_and_name)
                                1 -> context.getString(R.string.select_zip_and_enter_name)
                                else -> resources.getString(R.string.enter_complete_remote_info)
                            }
                            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isImporting && 
                             ((importTabIndex == 0 && repoUrlInput.isNotBlank() && pluginNameInput.isNotBlank()) ||
                              (importTabIndex == 1 && zipFilePath.isNotBlank() && pluginNameInput.isNotBlank()) ||
                              (importTabIndex == 2 && remoteEndpointInput.isNotBlank() && pluginNameInput.isNotBlank()) ||
                              (importTabIndex == 3 && configJsonInput.isNotBlank()))
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(when(importTabIndex) {
                        2 -> stringResource(R.string.connect)
                        3 -> stringResource(R.string.mcp_merge_config)
                        else -> stringResource(R.string.import_action)
                    })
                }
            },
            dismissButton = {
                TextButton(enabled = !isImporting, onClick = {
                    repoUrlInput = ""
                    pluginNameInput = ""
                    zipFilePath = ""
                    remoteEndpointInput = ""
                    remoteConnectionType = "httpStream"
                    remoteConnectionTypeExpanded = false
                    remoteBearerToken = ""
                    remoteHeaders = emptyList()
                    configJsonInput = ""
                    showImportDialog = false 
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    val isAnyLoading =
        isRefreshing || isImporting || isPluginLoading || pendingPluginId != null

    val isFullscreenLoading =
        isToolsLoading || (visiblePluginIds.isEmpty() && (isAnyLoading || !initialAutoStartPerformed.value))

    BindMcpTopBarActions(
        isBusy = configurationReadError || isAnyLoading || isFullscreenLoading,
        isRefreshing = isRefreshing || isToolsLoading,
        isStarting = isPluginLoading,
        onStartClick = {
            val lifecycleScope = activity?.lifecycleScope
            if (lifecycleScope != null) {
                pluginLoadingState.reset()
                pluginLoadingState.show()
                pluginLoadingState.initializeMCPServer(context, lifecycleScope)
            } else {
                Toast.makeText(
                    context,
                    resources.getString(R.string.plugin_loading_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onMarketClick = onNavigateToMCPMarket,
        onImportClick = { showImportDialog = true },
        onRefreshClick = {
            scope.launch {
                refreshMcpScreen()
                toolRefreshTrigger++
            }
        },
    )

    if (configurationReadError) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mcp_configuration_read_failed), color = MaterialTheme.colorScheme.error)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(mcpLocalServer.getConfigFilePath(), style = MaterialTheme.typography.bodySmall)
            }
            if (reloadFailed) Text(stringResource(R.string.mcp_configuration_reload_failed), color = MaterialTheme.colorScheme.error)
            Button(enabled = !reloadingConfig, onClick = {
                reloadingConfig = true
                reloadFailed = false
                scope.launch {
                    try { mcpLocalServer.reloadConfigurations(); refreshMcpScreen(); toolRefreshTrigger++ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { reloadFailed = true }
                    finally { reloadingConfig = false }
                }
            }) { Text(stringResource(R.string.mcp_retry)) }
        }
        return
    }

    if (isFullscreenLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 主界面内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding =
                PaddingValues(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = 24.dp,
                ),
        ) {
                    if (refreshFailed) item {
                        Text(stringResource(R.string.mcp_configuration_reload_failed), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(16.dp))
                    }
                    // 状态指示器
                    item {
                        val statusTone =
                            when {
                                totalEnabledPlugins == 0 -> KiyoriSemanticTone.CYAN
                                successfulToolRequests == totalEnabledPlugins ->
                                    KiyoriSemanticTone.GREEN
                                successfulToolRequests > 0 -> KiyoriSemanticTone.ORANGE
                                else -> KiyoriSemanticTone.RED
                            }
                        val statusColors = statusTone.resolveColors()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.mcp_management),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = statusColors.icon,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                    Text(
                                        text = "${successfulToolRequests}/$totalEnabledPlugins",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    
                    // 插件列表标题
                    if (displayedPluginIds.isNotEmpty()) {
                        
                        // 插件列表
                        items(items = displayedPluginIds, key = { it }) { pluginId ->
                            val pluginInfo = remember(pluginId, mcpConfigSnapshot) {
                                mcpRepository.getInstalledPluginInfo(pluginId)
                            }
                            val isRemote = pluginInfo?.type == "remote"
                            val invalidConfigReason: String? = null

                            // 获取插件部署状态（通过检查Linux文件系统）
                            val deploySuccessState = remember(pluginId) {
                                mutableStateOf(false)
                            }

                            // 获取插件启用状态 - 从配置读取
                            val pluginEnabledState = remember(pluginId) {
                                mutableStateOf(mcpLocalServer.isServerEnabled(pluginId))
                            }

                            // 获取插件运行状态
                            val pluginRunningState = remember(pluginId) {
                                mutableStateOf(mcpLocalServer.isServerLikelyRunning(pluginId))
                            }
                            
                            // 检查运行目录就绪状态
                            LaunchedEffect(pluginId) {
                                deploySuccessState.value = mcpLocalServer.isPluginRuntimeReady(pluginId)
                            }
                            
                            // 监听服务器状态变化
                            LaunchedEffect(pluginId) {
                                mcpLocalServer.serverStatus.collect { _ ->
                                    pluginRunningState.value = mcpLocalServer.isServerLikelyRunning(pluginId)
                                    // 重新检查运行目录就绪状态
                                    deploySuccessState.value = mcpLocalServer.isPluginRuntimeReady(pluginId)
                                }
                            }
                            
                            // 监听配置变化（isEnabled状态）
                            LaunchedEffect(pluginId) {
                                mcpLocalServer.mcpConfig.collect { _ ->
                                    pluginEnabledState.value = mcpLocalServer.isServerEnabled(pluginId)
                                }
                            }

                            var updatingEnabled by remember(pluginId) { mutableStateOf(false) }
                            PluginListItem(
                                    pluginId = pluginId,
                                    displayName = getPluginDisplayName(pluginId, mcpRepository),
                                    isOfficial = pluginId.startsWith("official_"),
                                    isRemote = isRemote, // 传递插件类型
                                    toolNames = pluginToolsMap[pluginId] ?: emptyList(), // 传递工具信息
                                    onClick = {
                                        selectedPluginForDetails = getPluginAsServer(
                                            pluginId,
                                            mcpRepository,
                                            mcpConfigSnapshot,
                                            discoveredInstalledPluginIds,
                                            context
                                        )
                                    },
                                    onToolsClick = {
                                        selectedPluginForToolDetails = getPluginAsServer(
                                            pluginId,
                                            mcpRepository,
                                            mcpConfigSnapshot,
                                            discoveredInstalledPluginIds,
                                            context
                                        )
                                    },
                                    onDeploy = {
                                        pluginToDeploy = pluginId
                                        showConfirmDialog = true // 显示确认对话框而不是直接进入命令编辑
                                    },
                                    onEdit = {
                                        // 设置要编辑的服务器并显示对话框
                                        val serverToEdit = getPluginAsServer(
                                            pluginId,
                                            mcpRepository,
                                            mcpConfigSnapshot,
                                            discoveredInstalledPluginIds,
                                            context
                                        )
                                        if(serverToEdit != null){
                                            editingRemoteServer = serverToEdit
                                            showRemoteEditDialog = true
                                        }
                                    },
                                    isEnabled = pluginEnabledState.value,
                                    isUpdatingEnabled = updatingEnabled,
                                    onEnabledChange = { isChecked ->
                                        if (!updatingEnabled) {
                                            updatingEnabled = true
                                            scope.launch {
                                                try { mcpLocalServer.setServerEnabled(pluginId, isChecked) }
                                                catch (cancelled: CancellationException) { throw cancelled }
                                                catch (_: Exception) {
                                                    Toast.makeText(context, resources.getString(R.string.save_failed), Toast.LENGTH_LONG).show()
                                                } finally { updatingEnabled = false }
                                            }
                                        }
                                    },
                                    isRunning = pluginRunningState.value,
                                    isDeployed = deploySuccessState.value,
                                    isConfigValid = isRemote || deploySuccessState.value,
                                    invalidConfigReason = invalidConfigReason
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    } else {
                        // 无插件提示
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Extension,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            stringResource(
                                                if (searchQuery.isBlank()) {
                                                    R.string.no_plugins
                                                } else {
                                                    R.string.no_matching_plugins_found
                                                }
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            stringResource(R.string.use_import_function_to_add),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
        }
    }
}

private fun parseMCPServiceToolNames(listResponse: JSONObject?): Map<String, List<String>> {
    if (listResponse?.optBoolean("success", false) != true) {
        return emptyMap()
    }

    val services = listResponse.optJSONObject("result")?.optJSONArray("services") ?: return emptyMap()
    val serviceTools = mutableMapOf<String, List<String>>()

    for (serviceIndex in 0 until services.length()) {
        val service = services.optJSONObject(serviceIndex) ?: continue
        val serviceName = service.optString("name", "").trim()
        if (serviceName.isEmpty()) {
            continue
        }

        val tools = service.optJSONArray("tools") ?: continue
        val toolNames = mutableListOf<String>()
        for (toolIndex in 0 until tools.length()) {
            val toolName = tools.optJSONObject(toolIndex)
                ?.optString("name", "")
                ?.trim()
                .orEmpty()
            if (toolName.isNotEmpty()) {
                toolNames.add(toolName)
            }
        }

        if (toolNames.isNotEmpty()) {
            serviceTools[serviceName] = toolNames.distinct()
        }
    }

    return serviceTools
}

// 从插件ID中提取显示名称
private fun getPluginDisplayName(pluginId: String, mcpRepository: MCPRepository): String {
    val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
    val originalName = pluginInfo?.name

    if (originalName != null && originalName.isNotBlank()) {
        return originalName
    }

    return when {
        pluginId.contains("/") -> pluginId.split("/").last().replace("-", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        pluginId.startsWith("official_") ->
            pluginId.removePrefix("official_").replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        else -> pluginId
    }
}

private fun mcpPluginMatchesSearch(
    pluginId: String,
    displayName: String,
    metadata: MCPLocalServer.PluginMetadata?,
    toolNames: List<String>?,
    searchText: String
): Boolean {
    val searchableText =
        buildList {
            add(pluginId)
            add(displayName)
            metadata?.let { pluginMetadata ->
                add(pluginMetadata.id)
                add(pluginMetadata.name)
                add(pluginMetadata.description)
                add(pluginMetadata.author)
                add(pluginMetadata.version)
                add(pluginMetadata.longDescription)
                add(pluginMetadata.repoUrl)
                add(pluginMetadata.type)
                pluginMetadata.endpoint?.let { add(it) }
            }
            toolNames?.forEach { toolName -> add(toolName) }
        }

    return searchableText.any { text -> text.contains(searchText, ignoreCase = true) }
}

// 获取插件元数据
private fun getPluginAsServer(
    pluginId: String,
    mcpRepository: MCPRepository,
    mcpConfigSnapshot: MCPLocalServer.MCPConfig,
    discoveredInstalledPluginIds: Set<String>,
    context: Context
): MCPLocalServer.PluginMetadata? {
    val metadataFromConfig = mcpConfigSnapshot.pluginMetadata[pluginId]
    val pluginInfo = metadataFromConfig ?: mcpRepository.getInstalledPluginInfo(pluginId)
    val isRemote = pluginInfo?.type == "remote"
    val isInstalled =
        pluginId in discoveredInstalledPluginIds ||
            isRemote ||
            (pluginInfo?.isInstalled == true)

    // 尝试从内存中的服务器列表查找
    val existingServer = mcpRepository.mcpServers.value.find { it.id == pluginId }

    // 如果在列表中找到，直接使用
    if (existingServer != null) {
        return existingServer.copy(isInstalled = existingServer.isInstalled || isInstalled)
    }

    val displayName = getPluginDisplayName(pluginId, mcpRepository)

    return MCPLocalServer.PluginMetadata(
        id = pluginId,
        name = displayName,
        description = pluginInfo?.description ?: context.getString(R.string.local_installed_plugin),
        logoUrl = "",
        author = pluginInfo?.author ?: context.getString(R.string.local_installation),
        isInstalled = isInstalled,
        version = pluginInfo?.version ?: context.getString(R.string.local_version),
        updatedAt = "",
        longDescription = pluginInfo?.longDescription
            ?: (pluginInfo?.description ?: context.getString(R.string.local_installed_plugin)),
        repoUrl = pluginInfo?.repoUrl ?: "",
        type = pluginInfo?.type ?: "local",
        endpoint = pluginInfo?.endpoint,
        connectionType = pluginInfo?.connectionType
    )
}

@Composable
private fun PluginListItem(
    pluginId: String,
    displayName: String,
    isOfficial: Boolean,
    isRemote: Boolean,
    toolNames: List<String>,
    onClick: () -> Unit,
    onToolsClick: () -> Unit,
    onDeploy: () -> Unit,
    onEdit: () -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    isRunning: Boolean = false,
    isDeployed: Boolean = false,
    isConfigValid: Boolean = true,
    isUpdatingEnabled: Boolean = false,
    invalidConfigReason: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 主要信息行：图标 + 名称 + 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 紧凑的插件图标
                Box(modifier = Modifier.size(32.dp)) {
                    KiyoriSemanticIconBadge(
                        imageVector = Icons.Default.Extension,
                        tone = KiyoriSemanticTone.BLUE,
                        contentDescription = null,
                        containerSize = 30.dp,
                        iconSize = 17.dp,
                        shape = RoundedCornerShape(8.dp),
                    )

                    // 运行状态指示点
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .background(
                                    color = KiyoriSemanticTone.GREEN.resolveColors().icon,
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 插件名称和状态
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        // 状态标签
                        if (isOfficial) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.official),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        
                        if (isRemote) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.remote),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        
                        if (isDeployed && !isRemote) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.deployed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        if (!invalidConfigReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = stringResource(R.string.mcp_config_invalid_tag),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    if (!invalidConfigReason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = invalidConfigReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 紧凑的开关
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange,
                    enabled = isConfigValid && !isUpdatingEnabled,
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // 工具标签区域（如果有）
            if (toolNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                        .clickable(onClick = onToolsClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(toolNames.take(5)) { toolName ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = toolName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (toolNames.size > 5) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.more) + "${toolNames.size - 5}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 操作按钮区域。远程服务只显示编辑，本地服务额外显示部署；两者共享同一操作行。
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 主要操作按钮
                    if (!isRemote) {
                        OutlinedButton(
                            onClick = onDeploy,
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDeployed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (isDeployed) stringResource(R.string.redeploy) else stringResource(R.string.deploy),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    // 编辑按钮
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.edit),
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerEditDialog(
    server: MCPLocalServer.PluginMetadata,
    onDismiss: () -> Unit,
    onSave: suspend (MCPLocalServer.PluginMetadata) -> Unit,
    onRegenerateDescription: suspend (MCPLocalServer.PluginMetadata, String) -> Result<String>
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var name by remember(server.id) { mutableStateOf(server.name) }
    var description by remember(server.id) { mutableStateOf(server.description) }
    var endpoint by remember(server.id) { mutableStateOf(server.endpoint ?: "") }
    var connectionType by remember(server.id) { mutableStateOf(server.connectionType ?: "httpStream") }
    var bearerToken by remember(server.id) { mutableStateOf(server.bearerToken ?: "") }
    var headers by remember(server.id) { mutableStateOf(server.headers.toEditableHeaders()) }
    val connectionTypes = listOf("httpStream", "sse")
    var expanded by remember(server.id) { mutableStateOf(false) }
    var isRegeneratingDescription by remember(server.id) { mutableStateOf(false) }
    var isSaving by remember(server.id) { mutableStateOf(false) }
    var saveError by remember(server.id) { mutableStateOf(false) }
    val isRemote = server.type == "remote"
    var discardRequested by remember(server.id) { mutableStateOf(false) }
    val dirty = name != server.name || description != server.description ||
        (isRemote && (endpoint != server.endpoint.orEmpty() || connectionType != (server.connectionType ?: "httpStream") ||
            bearerToken != server.bearerToken.orEmpty() || headers.map { it.key to it.value } != server.headers.orEmpty().toList()))
    val requestDismiss: () -> Unit = {
        if (!isSaving) { if (dirty) discardRequested = true else onDismiss() }
    }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if(isRemote) stringResource(R.string.edit_remote_service) else stringResource(R.string.edit_plugin_info)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (saveError) Text(stringResource(R.string.mcp_edit_save_failed), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isSaving && !isRegeneratingDescription,
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    enabled = !isSaving && !isRegeneratingDescription,
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isRegeneratingDescription = true
                                try {
                                    onRegenerateDescription(server, name)
                                        .onSuccess { generatedDescription ->
                                            description = generatedDescription
                                            Toast.makeText(
                                                context,
                                                resources.getString(R.string.mcp_regenerate_description_success),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                resources.getString(
                                                    R.string.mcp_regenerate_description_failed,
                                                    error.message ?: resources.getString(R.string.unknown_error)
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                } finally {
                                    isRegeneratingDescription = false
                                }
                            }
                        },
                        enabled = !isSaving && !isRegeneratingDescription && name.isNotBlank()
                    ) {
                        if (isRegeneratingDescription) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (isRegeneratingDescription) {
                                    R.string.mcp_regenerating_description
                                } else {
                                    R.string.mcp_regenerate_description
                                }
                            )
                        )
                    }
                }
                if(isRemote) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                    enabled = !isSaving,
                        label = { Text(stringResource(R.string.host_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (!isSaving) expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            value = connectionType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.connection_type)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            connectionTypes.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        connectionType = selectionOption
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = bearerToken,
                        visualTransformation = PasswordVisualTransformation(),
                        onValueChange = { bearerToken = it },
                    enabled = !isSaving,
                        label = { Text(stringResource(R.string.mcp_remote_bearer_token)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.mcp_remote_bearer_token_hint)) }
                    )

                    RemoteHeadersEditor(
                        headers = headers,
                        onHeadersChange = { headers = it },
                        enabled = !isSaving
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    saveError = false
                    try {
                        if (isRemote) {
                            validateMcpRemoteEndpoint(endpoint.trim(), connectionType)
                            validateMcpHeaders(headers.map { it.key.trim() to it.value })
                        }
                    } catch (_: IllegalArgumentException) {
                        saveError = true
                        return@Button
                    }
                    val normalizedName = name.trim()
                    val normalizedDescription = description.trim()
                    val updatedServer = server.copy(
                        name = normalizedName,
                        description = normalizedDescription,
                        longDescription = normalizedDescription,
                        endpoint = if(isRemote) endpoint.trim() else server.endpoint,
                        connectionType = if(isRemote) connectionType else server.connectionType,
                        bearerToken = if(isRemote && bearerToken.isNotBlank()) bearerToken else null,
                        headers = if(isRemote) headers.toHeaderMap() else server.headers
                    )
                    isSaving = true
                    scope.launch {
                        try { onSave(updatedServer) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { saveError = true }
                        finally { isSaving = false }
                    }
                },
                enabled = !isSaving && !isRegeneratingDescription && name.isNotBlank() && (if (isRemote) endpoint.isNotBlank() else true)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = requestDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
    if (discardRequested) {
        AlertDialog(onDismissRequest = { discardRequested = false },
            title = { Text(stringResource(R.string.mcp_unsaved_config)) },
            text = { Text(stringResource(R.string.mcp_discard_config)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { discardRequested = false }) { Text(stringResource(R.string.cancel)) } })
    }

}

private data class EditableHeader(
    val id: String = UUID.randomUUID().toString(),
    val key: String = "",
    val value: String = ""
)

private fun Map<String, String>?.toEditableHeaders(): List<EditableHeader> {
    return this
        ?.map { (key, value) -> EditableHeader(key = key, value = value) }
        .orEmpty()
}

private fun List<EditableHeader>.toHeaderMap(): Map<String, String>? {
    val headerEntries = LinkedHashMap<String, String>()

    for (header in this) {
        val key = header.key.trim()
        if (key.isBlank()) {
            continue
        }
        headerEntries[key] = header.value
    }

    return headerEntries.ifEmpty { null }
}

@Composable
private fun RemoteHeadersEditor(
    headers: List<EditableHeader>,
    onHeadersChange: (List<EditableHeader>) -> Unit,
    enabled: Boolean = true
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.mcp_remote_custom_headers),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.mcp_remote_custom_headers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        headers.forEachIndexed { index, header ->
            key(header.id) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = header.key,
                        enabled = enabled,
                        onValueChange = { newKey ->
                            onHeadersChange(
                                headers.toMutableList().apply {
                                    this[index] = this[index].copy(key = newKey)
                                }
                            )
                        },
                        label = { Text(stringResource(R.string.mcp_remote_header_name)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = header.value,
                        enabled = enabled,
                        visualTransformation = PasswordVisualTransformation(),
                        onValueChange = { newValue ->
                            onHeadersChange(
                                headers.toMutableList().apply {
                                    this[index] = this[index].copy(value = newValue)
                                }
                            )
                        },
                        label = { Text(stringResource(R.string.mcp_remote_header_value)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        enabled = enabled,
                        onClick = {
                            onHeadersChange(headers.toMutableList().apply { removeAt(index) })
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.mcp_remote_remove_header)
                        )
                    }
                }
            }
        }

        OutlinedButton(
            enabled = enabled,
            onClick = {
                onHeadersChange(headers + EditableHeader())
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.mcp_remote_add_header))
        }
    }
}
