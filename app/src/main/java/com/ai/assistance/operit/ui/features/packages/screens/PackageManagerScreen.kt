package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.util.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.EnvVarScope
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ToolPkgHostEnvironmentRepository
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.dialogs.CreateScriptDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.PackagesList
import com.ai.assistance.operit.ui.features.packages.market.MarketInstallStateStore
import com.ai.assistance.operit.ui.features.packages.market.PluginCreationIntent
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.ai.assistance.operit.ui.main.components.LocalOpenKiyoriNetworkProxy
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.R
private data class ExternalPackageImportResult(
    val message: String,
    val availablePackages: Map<String, ToolPackage>,
    val allAvailablePackages: Map<String, ToolPackage>,
    val pluginContainers: Map<String, PackageManager.ToolPkgContainerDetails>,
    val importedPackages: List<String>,
    val packageLoadErrors: Map<String, String>,
    val packageLoadErrorInfos: List<PackageManager.PackageLoadErrorInfo>,
    val newPackageLoadErrors: Map<String, String>
)

private data class PackageManagerSnapshot(
    val availablePackages: Map<String, ToolPackage>,
    val allAvailablePackages: Map<String, ToolPackage>,
    val pluginContainers: Map<String, PackageManager.ToolPkgContainerDetails>,
    val importedPackages: List<String>,
    val packageLoadErrors: Map<String, String>,
    val packageLoadErrorInfos: List<PackageManager.PackageLoadErrorInfo>
)

internal fun aiExtensionsTabOrder(): List<PackageTab> =
    listOf(
        PackageTab.PACKAGES,
        PackageTab.PLUGINS,
        PackageTab.SKILLS,
        PackageTab.MCP,
    )

private suspend fun loadPackageManagerSnapshot(
    context: android.content.Context,
    packageManager: PackageManager,
): PackageManagerSnapshot =
    withContext(Dispatchers.IO) {
        PackageManagerSnapshot(
            availablePackages =
                packageManager.getExecutableAvailablePackages(forceRefresh = true),
            allAvailablePackages = packageManager.getAvailablePackages(),
            pluginContainers =
                packageManager
                    .getToolPkgPluginContainerDetails(context)
                    .associateBy { it.packageName },
            importedPackages = packageManager.getEnabledPackageNames(),
            packageLoadErrors = packageManager.getPackageLoadErrors(),
            packageLoadErrorInfos = packageManager.getPackageLoadErrorInfos(),
        )
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageManagerScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToArtifactMarket: (PublishArtifactType) -> Unit = {},
    onStartPluginCreation: (PluginCreationIntent) -> Unit = {},
    onOpenToolPkgPluginConfig: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val packageManager = remember {
        PackageManager.getInstance(context, toolHandler)
    }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }

    val envPreferences = remember { EnvPreferences.getInstance(context) }
    val toolPkgHostEnvironmentRepository =
        remember { ToolPkgHostEnvironmentRepository.getInstance(context) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val allAvailablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val pluginContainers =
        remember { mutableStateOf<Map<String, PackageManager.ToolPkgContainerDetails>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    // UI展示用的导入状态列表，与后端状态分离
    val visibleImportedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var selectedToolPackageName by remember { mutableStateOf<String?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state
    var selectedTab by rememberSaveable { mutableStateOf(PackageTab.PACKAGES) }
    var pluginSearchInput by rememberSaveable { mutableStateOf("") }
    var pluginSearchQuery by rememberSaveable { mutableStateOf("") }
    var filteredPluginContainers by remember {
        mutableStateOf<Map<String, PackageManager.ToolPkgContainerDetails>>(emptyMap())
    }
    var isPluginSearchFiltering by remember { mutableStateOf(false) }
    var packageSearchInput by rememberSaveable { mutableStateOf("") }
    var packageSearchQuery by rememberSaveable { mutableStateOf("") }
    var filteredAvailablePackages by remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    var isPackageSearchFiltering by remember { mutableStateOf(false) }
    var skillSearchInput by rememberSaveable { mutableStateOf("") }
    var skillSearchQuery by rememberSaveable { mutableStateOf("") }
    var mcpSearchInput by rememberSaveable { mutableStateOf("") }
    var mcpSearchQuery by rememberSaveable { mutableStateOf("") }

    // Environment variables drawer state
    var showEnvSheet by remember { mutableStateOf(false) }
    var envVariables by
        remember {
            mutableStateOf<Map<PackageEnvironmentVariableKey, String>>(emptyMap())
        }

    val packageLoadErrors = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val packageLoadErrorInfos =
        remember { mutableStateOf<List<PackageManager.PackageLoadErrorInfo>>(emptyList()) }
    var showPackageLoadErrorsDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var pluginOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var skillOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCreateScriptDialog by remember { mutableStateOf(false) }
    var createScriptRequirement by rememberSaveable { mutableStateOf("") }
    val packageSnapshotMutex = remember { Mutex() }
    val artifactCatalogRevision by MarketInstallStateStore.artifactCatalogRevision.collectAsState()
    var observedArtifactCatalogRevision by remember {
        mutableLongStateOf(artifactCatalogRevision)
    }

    fun applyPackageManagerSnapshot(snapshot: PackageManagerSnapshot) {
        availablePackages.value = snapshot.availablePackages
        allAvailablePackages.value = snapshot.allAvailablePackages
        pluginContainers.value = snapshot.pluginContainers
        importedPackages.value = snapshot.importedPackages
        packageLoadErrors.value = snapshot.packageLoadErrors
        packageLoadErrorInfos.value = snapshot.packageLoadErrorInfos
        visibleImportedPackages.value = snapshot.importedPackages.toList()
    }

    suspend fun refreshPackageManagerSnapshot(
        showSuccessMessage: Boolean,
        showFailureMessage: Boolean,
    ): Boolean =
        packageSnapshotMutex.withLock {
            isLoading = true
            try {
                applyPackageManagerSnapshot(
                    loadPackageManagerSnapshot(
                        context = context,
                        packageManager = packageManager,
                    )
                )
                if (showSuccessMessage) {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.package_manager_refresh_success)
                    )
                }
                true
            } catch (e: Exception) {
                AppLogger.e("PackageManagerScreen", "Failed to refresh package snapshot", e)
                if (showFailureMessage) {
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.package_manager_refresh_failed,
                            e.message ?: resources.getString(R.string.unknown_error),
                        )
                    )
                }
                false
            } finally {
                isLoading = false
            }
        }

    val environmentPackages by remember {
        derivedStateOf {
            val packagesMap = allAvailablePackages.value
            val imported = importedPackages.value.toSet()

            imported
                .mapNotNull { packageName ->
                    packagesMap[packageName]
                }
                .filter { toolPackage -> toolPackage.env.isNotEmpty() }
        }
    }
    var requestedEnvironmentPackageName by remember {
        mutableStateOf<String?>(null)
    }
    val visibleEnvironmentPackages by remember {
        derivedStateOf {
            val requested = requestedEnvironmentPackageName
            if (requested == null) {
                environmentPackages
            } else {
                listOfNotNull(allAvailablePackages.value[requested])
                    .filter { toolPackage -> toolPackage.env.isNotEmpty() }
            }
        }
    }

    val environmentVariableKeys by remember {
        derivedStateOf {
            visibleEnvironmentPackages
                .flatMap { toolPackage ->
                    toolPackage.env.map { envVar ->
                        when (envVar.scope) {
                            EnvVarScope.GLOBAL ->
                                PackageEnvironmentVariableKey.global(envVar.name)
                            EnvVarScope.PACKAGE ->
                                PackageEnvironmentVariableKey.packageScoped(
                                    packageName = toolPackage.name,
                                    variableName = envVar.name,
                                )
                        }
                    }
                }
                .distinct()
                .sortedWith(
                    compareBy<PackageEnvironmentVariableKey>(
                        { key -> key.scope.ordinal },
                        { key -> key.packageName },
                        { key -> key.variableName },
                    ),
                )
        }
    }

    LaunchedEffect(pluginSearchInput) {
        delay(320)
        pluginSearchQuery = pluginSearchInput.trim()
    }

    LaunchedEffect(packageSearchInput) {
        delay(320)
        packageSearchQuery = packageSearchInput.trim()
    }

    LaunchedEffect(skillSearchInput) {
        delay(320)
        skillSearchQuery = skillSearchInput.trim()
    }

    LaunchedEffect(mcpSearchInput) {
        delay(320)
        mcpSearchQuery = mcpSearchInput.trim()
    }

    LaunchedEffect(pluginContainers.value, pluginSearchQuery) {
        val pluginsMap = pluginContainers.value
        val searchText = pluginSearchQuery.trim()
        if (searchText.isEmpty()) {
            filteredPluginContainers = pluginsMap
            isPluginSearchFiltering = false
            return@LaunchedEffect
        }

        isPluginSearchFiltering = true
        filteredPluginContainers =
            withContext(Dispatchers.Default) {
                pluginsMap.filter { (packageName, details) ->
                    pluginMatchesSearch(
                        packageName = packageName,
                        details = details,
                        searchText = searchText
                    )
                }
            }
        isPluginSearchFiltering = false
    }

    LaunchedEffect(availablePackages.value, packageSearchQuery) {
        val packagesMap = availablePackages.value
        val searchText = packageSearchQuery.trim()
        if (searchText.isEmpty()) {
            filteredAvailablePackages = packagesMap
            isPackageSearchFiltering = false
            return@LaunchedEffect
        }

        isPackageSearchFiltering = true
        filteredAvailablePackages =
            withContext(Dispatchers.Default) {
                packagesMap.filter { (packageName, toolPackage) ->
                    packageMatchesSearch(
                        context = context,
                        packageName = packageName,
                        toolPackage = toolPackage,
                        searchText = searchText
                    )
                }
            }
        isPackageSearchFiltering = false
    }

    // File picker launcher for importing external packages
    val packageFilePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val fileName: String? =
                            withContext(Dispatchers.IO) {
                                var name: String? = null
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex("_display_name")
                                    if (cursor.moveToFirst() && nameIndex >= 0) {
                                        name = cursor.getString(nameIndex)
                                    }
                                }
                                name
                            }

                        if (fileName == null) {
                            snackbarHostState.showSnackbar(resources.getString(R.string.no_filename))
                            return@launch
                        }

                        // 根据当前选中的标签页处理不同类型的文件
                        when (selectedTab) {
                            PackageTab.PLUGINS,
                            PackageTab.PACKAGES -> {
                                val fileNameNonNull = fileName
                                val lowerFileName = fileNameNonNull.lowercase()
                                val supported =
                                    when (selectedTab) {
                                        PackageTab.PLUGINS ->
                                            lowerFileName.endsWith(".toolpkg")
                                        PackageTab.PACKAGES ->
                                            lowerFileName.endsWith(".js") ||
                                                lowerFileName.endsWith(".ts") ||
                                                lowerFileName.endsWith(".hjson")
                                        else -> false
                                    }
                                if (!supported) {
                                    snackbarHostState.showSnackbar(
                                        message =
                                            resources.getString(
                                                if (selectedTab == PackageTab.PLUGINS) {
                                                    R.string.plugin_toolpkg_only
                                                } else {
                                                    R.string.package_script_only
                                                }
                                            )
                                    )
                                    return@launch
                                }

                                isLoading = true
                                val loadResult =
                                    withContext(Dispatchers.IO) {
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val tempFile = File(context.cacheDir, fileNameNonNull)
                                        try {
                                            inputStream?.use { input ->
                                                tempFile.outputStream().use { output -> input.copyTo(output) }
                                            }

                                            val errorsBeforeImport = packageManager.getPackageLoadErrors()
                                            val importMessage = packageManager.addPackageFileFromExternalStorage(tempFile.absolutePath)

                                            val available = packageManager.getExecutableAvailablePackages(forceRefresh = true)
                                            val allAvailable = packageManager.getAvailablePackages()
                                            val plugins =
                                                packageManager
                                                    .getToolPkgPluginContainerDetails(context)
                                                    .associateBy { it.packageName }
                                            val imported = packageManager.getEnabledPackageNames()
                                            val errors = packageManager.getPackageLoadErrors()
                                            val errorInfos = packageManager.getPackageLoadErrorInfos()
                                            val newErrors =
                                                errors.filter { (key, value) -> errorsBeforeImport[key] != value }

                                            ExternalPackageImportResult(
                                                message = importMessage,
                                                availablePackages = available,
                                                allAvailablePackages = allAvailable,
                                                pluginContainers = plugins,
                                                importedPackages = imported,
                                                packageLoadErrors = errors,
                                                packageLoadErrorInfos = errorInfos,
                                                newPackageLoadErrors = newErrors
                                            )
                                        } finally {
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                        }
                                    }

                                availablePackages.value = loadResult.availablePackages
                                allAvailablePackages.value = loadResult.allAvailablePackages
                                pluginContainers.value = loadResult.pluginContainers
                                importedPackages.value = loadResult.importedPackages
                                packageLoadErrors.value = loadResult.packageLoadErrors
                                packageLoadErrorInfos.value = loadResult.packageLoadErrorInfos
                                visibleImportedPackages.value = importedPackages.value.toList()
                                isLoading = false

                                val importSucceeded =
                                    loadResult.message.startsWith(
                                        prefix = "Successfully imported",
                                        ignoreCase = true
                                    )

                                if (importSucceeded) {
                                    snackbarHostState.showSnackbar(
                                        message =
                                            resources.getString(
                                                if (selectedTab == PackageTab.PLUGINS) {
                                                    R.string.external_plugin_imported
                                                } else {
                                                    R.string.external_package_imported
                                                }
                                            )
                                    )
                                } else {
                                    importErrorMessage =
                                        buildString {
                                            append(loadResult.message)
                                            if (loadResult.newPackageLoadErrors.isNotEmpty()) {
                                                append("\n\n")
                                                append(
                                                    loadResult.newPackageLoadErrors
                                                        .toSortedMap()
                                                        .entries
                                                        .joinToString(separator = "\n\n") { (packageName, errorText) ->
                                                            "$packageName:\n$errorText"
                                                        }
                                                )
                                            }
                                        }
                                }
                            }
                            else -> {
                                snackbarHostState.showSnackbar(resources.getString(R.string.current_tab_not_support_import))
                            }
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        AppLogger.e("PackageManagerScreen", "Failed to import file", e)
                        importErrorMessage =
                            resources.getString(
                                R.string.import_failed,
                                e.message ?: resources.getString(R.string.unknown_error)
                            ) + "\n\n" + e.stackTraceToString()
                    }
                }
            }

        }

    // Load packages
    LaunchedEffect(Unit) {
        if (
            refreshPackageManagerSnapshot(
                showSuccessMessage = false,
                showFailureMessage = false,
            )
        ) {
            pluginOrder = apiPreferences.getPluginOrder()
            skillOrder = apiPreferences.getSkillOrder()
            val requestedPackageName = ToolPkgHostEnvironmentEditRequestStore.consume()
            if (requestedPackageName != null) {
                requestedEnvironmentPackageName = requestedPackageName
                val requestedPackage =
                    allAvailablePackages.value[requestedPackageName]
                        ?.takeIf { toolPackage -> toolPackage.env.isNotEmpty() }
                if (requestedPackage != null) {
                    envVariables =
                        requestedPackage.env.associate { envVar ->
                            val key =
                                when (envVar.scope) {
                                    EnvVarScope.GLOBAL ->
                                        PackageEnvironmentVariableKey.global(envVar.name)
                                    EnvVarScope.PACKAGE ->
                                        PackageEnvironmentVariableKey.packageScoped(
                                            packageName = requestedPackage.name,
                                            variableName = envVar.name,
                                        )
                                }
                            key to
                                when (key.scope) {
                                    EnvVarScope.GLOBAL ->
                                        envPreferences.getEnv(key.variableName).orEmpty()
                                    EnvVarScope.PACKAGE ->
                                        toolPkgHostEnvironmentRepository
                                            .getValue(
                                                containerPackageName =
                                                    requireNotNull(key.ownerPackageName),
                                                variableName = key.variableName,
                                            )
                                            .orEmpty()
                                }
                        }
                    showEnvSheet = true
                } else {
                    requestedEnvironmentPackageName = null
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.openai_web_search_configuration_unavailable
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(artifactCatalogRevision) {
        if (artifactCatalogRevision == observedArtifactCatalogRevision) {
            return@LaunchedEffect
        }

        observedArtifactCatalogRevision = artifactCatalogRevision
        refreshPackageManagerSnapshot(
            showSuccessMessage = false,
            showFailureMessage = true,
        )
    }

    val activeSearchInput =
        when (selectedTab) {
            PackageTab.PLUGINS -> pluginSearchInput
            PackageTab.PACKAGES -> packageSearchInput
            PackageTab.SKILLS -> skillSearchInput
            PackageTab.MCP -> mcpSearchInput
        }
    val activeSearchPlaceholderRes =
        when (selectedTab) {
            PackageTab.PLUGINS -> R.string.plugin_market_search_placeholder
            PackageTab.PACKAGES -> R.string.package_market_search_placeholder
            PackageTab.SKILLS -> R.string.skill_market_search_placeholder
            PackageTab.MCP -> R.string.mcp_market_search_placeholder
        }
    val activeSearchApplying =
        when (selectedTab) {
            PackageTab.PLUGINS ->
                pluginSearchInput.trim() != pluginSearchQuery || isPluginSearchFiltering
            PackageTab.PACKAGES ->
                packageSearchInput.trim() != packageSearchQuery || isPackageSearchFiltering
            PackageTab.SKILLS -> skillSearchInput.trim() != skillSearchQuery
            PackageTab.MCP -> mcpSearchInput.trim() != mcpSearchQuery
        }

    val onActiveSearchQueryChanged: (String) -> Unit = { query ->
        when (selectedTab) {
            PackageTab.PLUGINS -> pluginSearchInput = query
            PackageTab.PACKAGES -> packageSearchInput = query
            PackageTab.SKILLS -> skillSearchInput = query
            PackageTab.MCP -> mcpSearchInput = query
        }
    }

    BindPackageManagerTopBarActions(
        selectedTab = selectedTab,
        isRefreshing = isLoading,
        onEnvironmentClick = {
            envVariables =
                environmentVariableKeys.associateWith { key ->
                    when (key.scope) {
                        EnvVarScope.GLOBAL ->
                            envPreferences.getEnv(key.variableName).orEmpty()
                        EnvVarScope.PACKAGE ->
                            toolPkgHostEnvironmentRepository
                                .getValue(
                                    containerPackageName = requireNotNull(key.ownerPackageName),
                                    variableName = key.variableName,
                                )
                                .orEmpty()
                    }
                }
            showEnvSheet = true
        },
        onMarketClick = {
            onNavigateToArtifactMarket(
                if (selectedTab == PackageTab.PLUGINS) {
                    PublishArtifactType.PACKAGE
                } else {
                    PublishArtifactType.SCRIPT
                }
            )
        },
        onImportClick = { packageFilePicker.launch("*/*") },
        onCreateScriptClick = {
            showCreateScriptDialog = true
        },
        onRefreshClick = {
            scope.launch {
                refreshPackageManagerSnapshot(
                    showSuccessMessage = true,
                    showFailureMessage = true,
                )
            }
        },
    )
    val selectedTabColors = packageManagerTabTone(selectedTab).resolveColors()
    val tabOrder = aiExtensionsTabOrder()
    val selectedTabIndex = tabOrder.indexOf(selectedTab)

    CustomScaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    snackbarData = data
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            PackageManagerSearchField(
                query = activeSearchInput,
                onQueryChange = onActiveSearchQueryChanged,
                placeholderRes = activeSearchPlaceholderRes,
                isSearching = activeSearchApplying,
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                },
                indicator = {
                    if (selectedTabIndex in tabOrder.indices) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                            height = 2.dp,
                            color = selectedTabColors.icon
                        )
                    }
                }
            ) {
                // 脚本标签
                Tab(
                    selected = selectedTab == PackageTab.PACKAGES,
                    onClick = { selectedTab = PackageTab.PACKAGES },
                    modifier = Modifier.height(48.dp)
                ) {
                    PackageManagerTabLabel(
                        tab = PackageTab.PACKAGES,
                        title = resources.getString(R.string.ai_extensions_tab_scripts),
                        icon = Icons.Default.Extension,
                        selected = selectedTab == PackageTab.PACKAGES,
                    )
                }

                // 插件标签
                Tab(
                    selected = selectedTab == PackageTab.PLUGINS,
                    onClick = { selectedTab = PackageTab.PLUGINS },
                    modifier = Modifier.height(48.dp)
                ) {
                    PackageManagerTabLabel(
                        tab = PackageTab.PLUGINS,
                        title = resources.getString(R.string.nav_group_plugins),
                        icon = Icons.Default.Apps,
                        selected = selectedTab == PackageTab.PLUGINS,
                    )
                }

                // Skill 标签
                Tab(
                    selected = selectedTab == PackageTab.SKILLS,
                    onClick = { selectedTab = PackageTab.SKILLS },
                    modifier = Modifier.height(48.dp)
                ) {
                    PackageManagerTabLabel(
                        tab = PackageTab.SKILLS,
                        title = resources.getString(R.string.ai_extensions_tab_skill),
                        icon = Icons.Default.Build,
                        selected = selectedTab == PackageTab.SKILLS,
                    )
                }

                // MCP标签
                Tab(
                    selected = selectedTab == PackageTab.MCP,
                    onClick = { selectedTab = PackageTab.MCP },
                    modifier = Modifier.height(48.dp)
                ) {
                    PackageManagerTabLabel(
                        tab = PackageTab.MCP,
                        title = resources.getString(R.string.mcp),
                        icon = Icons.Default.Cloud,
                        selected = selectedTab == PackageTab.MCP,
                    )
                }
            }

            if (
                (selectedTab == PackageTab.PLUGINS || selectedTab == PackageTab.PACKAGES) &&
                    packageLoadErrorInfos.value.isNotEmpty()
            ) {
                PackageLoadErrorsBanner(
                    errorCount = packageLoadErrorInfos.value.size,
                    onClick = { showPackageLoadErrorsDialog = true },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    PackageTab.PLUGINS -> {
                        PluginTabContent(
                            plugins = filteredPluginContainers,
                            enabledPackageNames = visibleImportedPackages.value,
                            isLoading = isLoading,
                            isSearchActive = pluginSearchQuery.isNotBlank(),
                            onPluginClick = { packageName ->
                                selectedPackage = packageName
                                showDetails = true
                            },
                            onTogglePlugin = { details, isChecked ->
                                val currentImported =
                                    visibleImportedPackages.value.toMutableList()
                                if (isChecked) {
                                    if (!currentImported.contains(details.packageName)) {
                                        currentImported.add(details.packageName)
                                    }
                                } else {
                                    currentImported.remove(details.packageName)
                                    details.subpackages.forEach { subpackage ->
                                        currentImported.remove(subpackage.packageName)
                                    }
                                }
                                visibleImportedPackages.value = currentImported

                                scope.launch {
                                    try {
                                        val updatedImported =
                                            withContext(Dispatchers.IO) {
                                                if (isChecked) {
                                                    packageManager.enablePackage(details.packageName)
                                                } else {
                                                    packageManager.disablePackage(details.packageName)
                                                }
                                                packageManager.getEnabledPackageNames()
                                            }

                                        importedPackages.value = updatedImported
                                        visibleImportedPackages.value = updatedImported.toList()
                                    } catch (e: Exception) {
                                        AppLogger.e(
                                            "PackageManagerScreen",
                                            if (isChecked) "Failed to enable plugin" else "Failed to disable plugin",
                                            e
                                        )
                                        visibleImportedPackages.value = importedPackages.value
                                        snackbarHostState.showSnackbar(
                                            message =
                                                if (isChecked) {
                                                    resources.getString(R.string.plugin_enable_failed)
                                                } else {
                                                    resources.getString(R.string.plugin_disable_failed)
                                                }
                                        )
                                    }
                                }
                            },
                            pluginOrder = pluginOrder,
                            onSavePluginOrder = { newOrder ->
                                pluginOrder = newOrder
                                scope.launch {
                                    apiPreferences.savePluginOrder(newOrder)
                                }
                            },
                        )
                    }

                    PackageTab.PACKAGES -> {
                        PackageTabContent(
                            packages = filteredAvailablePackages,
                            enabledPackageNames = visibleImportedPackages.value,
                            isLoading = isLoading,
                            isSearchActive = packageSearchQuery.isNotBlank(),
                            onPackageClick = { packageName ->
                                selectedPackage = packageName
                                showDetails = true
                            },
                            onTogglePackage = { packageName, isChecked ->
                                val currentImported =
                                    visibleImportedPackages.value.toMutableList()
                                if (isChecked) {
                                    if (!currentImported.contains(packageName)) {
                                        currentImported.add(packageName)
                                    }
                                } else {
                                    currentImported.remove(packageName)
                                }
                                visibleImportedPackages.value = currentImported

                                scope.launch {
                                    try {
                                        val updatedImported =
                                            withContext(Dispatchers.IO) {
                                                if (isChecked) {
                                                    packageManager.enablePackage(packageName)
                                                } else {
                                                    packageManager.disablePackage(packageName)
                                                }
                                                packageManager.getEnabledPackageNames()
                                            }

                                        importedPackages.value = updatedImported
                                        visibleImportedPackages.value = updatedImported.toList()
                                    } catch (e: Exception) {
                                        AppLogger.e(
                                            "PackageManagerScreen",
                                            if (isChecked) "Failed to import package" else "Failed to remove package",
                                            e
                                        )
                                        visibleImportedPackages.value = importedPackages.value
                                        snackbarHostState.showSnackbar(
                                            message =
                                                if (isChecked) {
                                                    resources.getString(R.string.package_import_failed)
                                                } else {
                                                    resources.getString(R.string.package_remove_failed)
                                                }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    PackageTab.SKILLS -> {
                        SkillConfigScreen(
                            skillRepository = skillRepository,
                            snackbarHostState = snackbarHostState,
                            onNavigateToSkillMarket = onNavigateToSkillMarket,
                            searchQuery = skillSearchQuery,
                            skillOrder = skillOrder,
                            onSaveSkillOrder = { newOrder ->
                                skillOrder = newOrder
                                scope.launch {
                                    apiPreferences.saveSkillOrder(newOrder)
                                }
                            },
                        )
                    }

                    PackageTab.MCP -> {
                        MCPConfigScreen(
                            onNavigateToMCPMarket = onNavigateToMCPMarket,
                            searchQuery = mcpSearchQuery
                        )
                    }
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                    packageName = selectedPackage!!,
                    packageDescription = allAvailablePackages.value[selectedPackage]?.description?.resolve(context)
                        ?: "",
                    toolPackage = allAvailablePackages.value[selectedPackage],
                    packageManager = packageManager,
                    onRunScript = { toolPackageName, tool ->
                        selectedToolPackageName = toolPackageName
                        selectedTool = tool
                        showScriptExecution = true
                    },
                    onOpenToolPkgPluginConfig = { containerPackageName, uiModuleId, title, keepAlive ->
                        showDetails = false
                        onOpenToolPkgPluginConfig(containerPackageName, uiModuleId, title, keepAlive)
                    },
                    onDismiss = {
                        showDetails = false
                        scope.launch {
                            val imported = withContext(Dispatchers.IO) { packageManager.getEnabledPackageNames() }
                            importedPackages.value = imported
                            visibleImportedPackages.value = imported.toList()
                        }
                    },
                    onPackageDeleted = {
                        showDetails = false
                        scope.launch {
                            AppLogger.d(
                                "PackageManagerScreen",
                                "onPackageDeleted callback triggered. Refreshing package lists."
                            )
                            if (
                                refreshPackageManagerSnapshot(
                                    showSuccessMessage = false,
                                    showFailureMessage = true,
                                )
                            ) {
                                snackbarHostState.showSnackbar("Package deleted successfully.")
                            }
                        }
                    }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                    packageName = selectedToolPackageName ?: selectedPackage!!,
                    tool = selectedTool!!,
                    packageManager = packageManager,
                    initialResult = scriptExecutionResult,
                    onExecuted = { result -> scriptExecutionResult = result },
                    onDismiss = {
                        showScriptExecution = false
                        scriptExecutionResult = null
                        selectedToolPackageName = null
                    }
                )
            }

            if (showEnvSheet) {
                PackageEnvironmentVariablesSheet(
                    packages = visibleEnvironmentPackages,
                    currentValues = envVariables,
                    onOpenNetworkProxy = LocalOpenKiyoriNetworkProxy.current,
                    onDismiss = {
                        showEnvSheet = false
                        requestedEnvironmentPackageName = null
                    },
                    onConfirm = { updated ->
                        val mergedGlobalValues =
                            envPreferences.getAllEnv().toMutableMap().apply {
                                updated.forEach { (key, value) ->
                                    if (key.scope != EnvVarScope.GLOBAL) {
                                        return@forEach
                                    }
                                    if (value.isBlank()) {
                                        remove(key.variableName)
                                    } else {
                                        this[key.variableName] = value
                                    }
                                }
                            }
                        envPreferences.setAllEnv(mergedGlobalValues)
                        updated.forEach { (key, value) ->
                            if (key.scope == EnvVarScope.PACKAGE) {
                                toolPkgHostEnvironmentRepository.setValue(
                                    containerPackageName =
                                        requireNotNull(key.ownerPackageName),
                                    variableName = key.variableName,
                                    value = value,
                                )
                            }
                        }
                        envVariables = updated
                    }
                )
            }

            if (showPackageLoadErrorsDialog) {
                PackageLoadErrorsDialog(
                    errorInfos = packageLoadErrorInfos.value,
                    onDeleteSource = { sourcePath ->
                        scope.launch {
                            val deleted =
                                withContext(Dispatchers.IO) {
                                    packageManager.deleteExternalPackageSource(sourcePath)
                                }
                            if (!deleted) {
                                snackbarHostState.showSnackbar(
                                    message = resources.getString(R.string.package_conflict_delete_failed)
                                )
                                return@launch
                            }

                            val refreshed =
                                refreshPackageManagerSnapshot(
                                    showSuccessMessage = false,
                                    showFailureMessage = true,
                                )

                            if (refreshed && packageLoadErrorInfos.value.isEmpty()) {
                                showPackageLoadErrorsDialog = false
                            }
                            if (refreshed) {
                                snackbarHostState.showSnackbar(
                                    message = resources.getString(R.string.package_conflict_delete_success)
                                )
                            }
                        }
                    },
                    onDismiss = { showPackageLoadErrorsDialog = false }
                )
            }

            importErrorMessage?.let { errorMessage ->
                ErrorDialog(
                    errorMessage = errorMessage,
                    onDismiss = { importErrorMessage = null }
                )
            }

            if (showCreateScriptDialog) {
                CreateScriptDialog(
                    requirement = createScriptRequirement,
                    onRequirementChange = { createScriptRequirement = it },
                    onDismiss = { showCreateScriptDialog = false },
                    onConfirm = {
                        val requirement = createScriptRequirement.trim()
                        if (requirement.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    resources.getString(R.string.create_script_requirement_empty)
                                )
                            }
                        } else {
                            showCreateScriptDialog = false
                            createScriptRequirement = ""
                            onStartPluginCreation(PluginCreationIntent.Fresh(requirement))
                        }
                    }
                )
            }
        }
    }
}

internal fun packageManagerTabTone(tab: PackageTab): KiyoriSemanticTone =
    when (tab) {
        PackageTab.PLUGINS -> KiyoriSemanticTone.PURPLE
        PackageTab.PACKAGES -> KiyoriSemanticTone.CYAN
        PackageTab.SKILLS -> KiyoriSemanticTone.ORANGE
        PackageTab.MCP -> KiyoriSemanticTone.BLUE
    }

@Composable
private fun PackageManagerTabLabel(
    tab: PackageTab,
    title: String,
    icon: ImageVector,
    selected: Boolean,
) {
    val tone = packageManagerTabTone(tab)
    val colors = tone.resolveColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = icon,
            tone = tone,
            contentDescription = null,
            containerSize = 28.dp,
            iconSize = 15.dp,
            shape = RoundedCornerShape(9.dp),
            enabled = true,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false,
            color =
                if (selected) {
                    colors.icon
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}


