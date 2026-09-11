@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.ai.assistance.operit.ui.features.settings.screens

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiyori.design.theme.KiyoriUiShapes
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.ApiKeyPoolAvailabilityTester
import com.ai.assistance.operit.api.chat.llmprovider.ModelConfigConnectionTester
import com.ai.assistance.operit.api.chat.llmprovider.ModelConnectionTestOutcome
import com.ai.assistance.operit.api.chat.llmprovider.ModelConnectionTestType
import com.ai.assistance.operit.ui.features.settings.components.ExpandableStatusText
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.settings.rememberModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.sections.AdvancedSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelApiSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelParametersSection
import com.ai.assistance.operit.ui.features.settings.sections.SettingsInfoBanner
import com.ai.assistance.operit.ui.features.settings.sections.SettingsSectionHeader
import com.ai.assistance.operit.ui.features.settings.sections.SettingsSwitchRow
import com.ai.assistance.operit.ui.features.settings.sections.SettingsTextField
import com.ai.assistance.operit.ui.features.settings.components.ModelSettingsActionContentSpacing
import com.ai.assistance.operit.ui.features.settings.components.ModelSettingsActionHeight
import com.ai.assistance.operit.ui.features.settings.components.ModelSettingsActionHorizontalPadding
import com.ai.assistance.operit.ui.features.settings.components.ModelSettingsActionIconSize
import com.ai.assistance.operit.ui.features.settings.components.ModelSettingsActionShape
import com.ai.assistance.operit.ui.main.shell.KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsWorkspacePage
import com.ai.assistance.operit.ui.main.shell.kiyoriSettingsOutlinedTextFieldColors
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.design.theme.LocalKiyoriSettingsColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class HeaderPreset(val nameResId: Int, val headers: Map<String, String>)
private data class PendingModelConfigDeletion(val id: String, val name: String)

private sealed interface HeaderEntriesState {
    data class Ready(val entries: List<Pair<String, String>>) : HeaderEntriesState
    data object InvalidPersistedJson : HeaderEntriesState
}

private const val MODEL_CONFIG_LOG_TAG = "ModelConfigScreen"

private val headerPresets =
    listOf(
        HeaderPreset(
            nameResId = R.string.headers_preset_android_browser,
            headers =
                mapOf(
                    "User-Agent" to
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                )
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_desktop_browser_win,
            headers =
                mapOf(
                    "User-Agent" to
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
                )
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_lang_zh,
            headers = mapOf("Accept-Language" to "zh-CN,zh;q=0.9")
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_lang_en,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.9")
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_cmcc_gateway,
            headers = mapOf("X-Forwarded-For" to "211.136.1.10", "Via" to "CMNET")
        ),
        HeaderPreset(
            nameResId = R.string.headers_preset_us_la,
            headers =
                mapOf(
                    "Accept-Language" to "en-US,en;q=0.9",
                    "X-Forwarded-For" to "38.107.226.5"
                )
        )
    )

private fun parseHeaderEntries(headersJson: String): HeaderEntriesState {
    return try {
        HeaderEntriesState.Ready(
            if (headersJson.isBlank() || headersJson == "{}") {
                emptyList()
            } else {
                val jsonObject = JSONObject(headersJson)
                buildList {
                    for (key in jsonObject.keys()) {
                        add(key to jsonObject.getString(key))
                    }
                }
            },
        )
    } catch (error: Exception) {
        AppLogger.e(MODEL_CONFIG_LOG_TAG, "Failed to parse custom headers", error)
        HeaderEntriesState.InvalidPersistedJson
    }
}

private fun serializeHeaderEntries(headers: List<Pair<String, String>>): String {
    return JSONObject().apply {
        headers.forEach { (key, value) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isNotEmpty()) {
                put(normalizedKey, value)
            }
        }
    }.toString()
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun ModelConfigScreen(
    onBackPressed: () -> Unit,
    navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val settingsColors = LocalKiyoriSettingsColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configManager = remember { ModelConfigManager(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val saveCoordinator = rememberModelConfigSaveCoordinator()
    val snackbarHostState = remember { SnackbarHostState() }

    // 配置状态
    val configList = configManager.configListFlow.collectAsState(initial = listOf("default")).value
    // 进入页面时默认选中“对话功能”当前绑定的模型配置
    var selectedConfigId by remember { mutableStateOf(ModelConfigManager.DEFAULT_CONFIG_ID) }
    val selectedConfig = remember { mutableStateOf<ModelConfigData?>(null) }
    val keyAvailabilityTester =
        remember(selectedConfigId) {
            ApiKeyPoolAvailabilityTester(selectedConfigId, configManager)
        }

    // 配置名称映射
    val configNameMap = remember { mutableStateMapOf<String, String>() }

    // UI状态
    var showAddConfigDialog by remember { mutableStateOf(false) }
    var showRenameConfigDialog by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var newConfigName by remember { mutableStateOf("") }
    var renameConfigName by remember { mutableStateOf("") }
    var pendingDeletion by remember { mutableStateOf<PendingModelConfigDeletion?>(null) }

    // 连接测试状态
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ConnectionTestItem>?>(null) }
    var testedModelName by remember { mutableStateOf("") }
    var connectionTestJob by remember { mutableStateOf<Job?>(null) }
    var activeConnectionTestService by remember { mutableStateOf<AIService?>(null) }

    DisposableEffect(keyAvailabilityTester) {
        onDispose { keyAvailabilityTester.close() }
    }

    // 初始化配置，并默认定位到“对话功能模型”所使用的配置
    LaunchedEffect(Unit) {
        configManager.initializeIfNeeded()
        functionalConfigManager.initializeIfNeeded()

        val chatConfigId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
        val availableConfigIds = configManager.configListFlow.first()
        selectedConfigId =
            availableConfigIds.firstOrNull { it == chatConfigId }
                ?: availableConfigIds.firstOrNull()
                ?: ModelConfigManager.DEFAULT_CONFIG_ID
    }

    // 加载所有配置名称
    LaunchedEffect(configList) {
        configList.forEach { id ->
            val config = configManager.getModelConfigFlow(id).first()
            configNameMap[id] = config.name
        }
    }

    // 加载选中的配置
    LaunchedEffect(selectedConfigId) {
        testResults = null
        testedModelName = ""
        selectedConfig.value = null
        configManager.getModelConfigFlow(selectedConfigId).collect { config ->
            selectedConfig.value = config
        }
    }

    // 显示通知消息
    fun showNotification(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    DisposableEffect(lifecycleOwner, saveCoordinator) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    saveCoordinator.flushAllInBackground(showSuccess = false)
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 主界面内容
    KiyoriSettingsWorkspacePage(
        title = stringResource(R.string.kiyori_ai_settings_model_api),
        onBack = onBackPressed,
        snackbarHostState = snackbarHostState,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = settingsColors.cardBackground),
                    border =
                        BorderStroke(
                            0.7.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.select_model_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            OutlinedButton(
                                onClick = { showAddConfigDialog = true },
                                shape = KiyoriUiShapes.control,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 40.dp),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    stringResource(R.string.new_action),
                                    fontSize = 12.sp,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        val selectedConfigName =
                            configNameMap[selectedConfigId]
                                ?: stringResource(R.string.default_profile)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = 0.5.dp,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedConfigName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                @OptIn(ExperimentalAnimationApi::class)
                                AnimatedContent(
                                    targetState = isDropdownExpanded,
                                    transitionSpec = {
                                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                                    }
                                ) { expanded ->
                                    Icon(
                                        if (expanded) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.select_config),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedConfigId != "default") {
                                OutlinedButton(
                                    onClick = {
                                        renameConfigName = selectedConfig.value?.name ?: ""
                                        showRenameConfigDialog = true
                                    },
                                    contentPadding =
                                        PaddingValues(
                                            horizontal =
                                                ModelSettingsActionHorizontalPadding
                                        ),
                                    modifier =
                                        Modifier
                                            .weight(0.92f)
                                            .height(ModelSettingsActionHeight),
                                    shape = ModelSettingsActionShape
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(ModelSettingsActionIconSize)
                                    )
                                    Spacer(
                                        modifier =
                                            Modifier.width(ModelSettingsActionContentSpacing)
                                    )
                                    Text(
                                        text = stringResource(R.string.rename_action),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val currentConfig = selectedConfig.value
                                        if (currentConfig != null) {
                                            pendingDeletion =
                                                PendingModelConfigDeletion(
                                                    id = currentConfig.id,
                                                    name = currentConfig.name,
                                                )
                                        }
                                    },
                                    contentPadding =
                                        PaddingValues(
                                            horizontal =
                                                ModelSettingsActionHorizontalPadding
                                        ),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                    border =
                                        BorderStroke(
                                            0.8.dp,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                                        ),
                                    modifier =
                                        Modifier
                                            .weight(0.82f)
                                            .height(ModelSettingsActionHeight),
                                    shape = ModelSettingsActionShape
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(ModelSettingsActionIconSize)
                                    )
                                    Spacer(
                                        modifier =
                                            Modifier.width(ModelSettingsActionContentSpacing)
                                    )
                                    Text(
                                        text = stringResource(R.string.delete_action),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (isTestingConnection) {
                                        activeConnectionTestService?.cancelStreaming()
                                        connectionTestJob?.cancel()
                                        return@FilledTonalButton
                                    }

                                    connectionTestJob = scope.launch {
                                        try {
                                            isTestingConnection = true
                                            val results = mutableListOf<ConnectionTestItem>()
                                            try {
                                                saveCoordinator.flushAll(showSuccess = false)

                                                val latestConfig =
                                                    configManager.getModelConfig(selectedConfigId)

                                                latestConfig?.let { config ->
                                                    val report =
                                                        ModelConfigConnectionTester.run(
                                                            context = context,
                                                            modelConfigManager = configManager,
                                                            config = config,
                                                            onActiveServiceChanged = {
                                                                activeConnectionTestService = it
                                                            }
                                                        )
                                                    testedModelName = report.testedModelName

                                                    report.items.forEach { item ->
                                                        results.add(
                                                            ConnectionTestItem(
                                                                labelResId = item.type.toLabelResId(),
                                                                type = item.type,
                                                                outcome = item.outcome,
                                                                error = item.error
                                                            )
                                                        )
                                                    }
                                                } ?: run {
                                                    results.add(
                                                        ConnectionTestItem(
                                                            labelResId = R.string.test_item_chat,
                                                            type = ModelConnectionTestType.CHAT,
                                                            outcome = ModelConnectionTestOutcome.FAILED,
                                                            error = context.getString(
                                                                R.string.no_config_selected
                                                            )
                                                        )
                                                    )
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                results.add(
                                                    ConnectionTestItem(
                                                        labelResId = R.string.test_item_chat,
                                                        type = ModelConnectionTestType.CHAT,
                                                        outcome = ModelConnectionTestOutcome.FAILED,
                                                        error = e.message
                                                    )
                                                )
                                            }
                                            testResults = results
                                        } finally {
                                            activeConnectionTestService = null
                                            isTestingConnection = false
                                            connectionTestJob = null
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .weight(
                                            if (selectedConfigId == "default") {
                                                1f
                                            } else {
                                                1.16f
                                            }
                                        )
                                        .height(ModelSettingsActionHeight),
                                contentPadding =
                                    PaddingValues(
                                        horizontal =
                                            ModelSettingsActionHorizontalPadding
                                    ),
                                shape = ModelSettingsActionShape,
                                enabled =
                                    isTestingConnection ||
                                        selectedConfig.value?.let { selected ->
                                            getModelList(selected.modelName).isNotEmpty()
                                        } == true
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(ModelSettingsActionIconSize),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(ModelSettingsActionIconSize)
                                    )
                                }
                                Spacer(
                                    modifier =
                                        Modifier.width(ModelSettingsActionContentSpacing)
                                )
                                Text(
                                    text =
                                        stringResource(
                                            if (isTestingConnection) R.string.cancel
                                            else R.string.test_model
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = testResults != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            testResults?.let { results ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (testedModelName.isNotEmpty()) {
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.tested_model_name,
                                                        testedModelName
                                                    ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        results.forEachIndexed { index, item ->
                                            val statusText = item.statusText(context)
                                            val contentColor =
                                                when (item.outcome) {
                                                    ModelConnectionTestOutcome.PASSED ->
                                                        MaterialTheme.colorScheme.primary
                                                    ModelConnectionTestOutcome.UNVERIFIED ->
                                                        MaterialTheme.colorScheme.tertiary
                                                    ModelConnectionTestOutcome.FAILED ->
                                                        MaterialTheme.colorScheme.error
                                                }
                                            val icon =
                                                when (item.outcome) {
                                                    ModelConnectionTestOutcome.PASSED ->
                                                        Icons.Default.CheckCircle
                                                    ModelConnectionTestOutcome.UNVERIFIED ->
                                                        Icons.Default.Info
                                                    ModelConnectionTestOutcome.FAILED ->
                                                        Icons.Default.Warning
                                                }

                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = contentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(item.labelResId),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                ExpandableStatusText(
                                                    text = statusText,
                                                    color = contentColor,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (index != results.lastIndex) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.width(280.dp),
                        properties = PopupProperties(focusable = true)
                    ) {
                        configList.forEach { configId ->
                            val configName =
                                configNameMap[configId] ?: stringResource(R.string.unnamed_profile)
                            val isSelected = configId == selectedConfigId

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = configName,
                                        fontWeight =
                                            if (isSelected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                        color =
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon =
                                    if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = stringResource(R.string.selected_desc),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                onClick = {
                                    selectedConfigId = configId
                                    isDropdownExpanded = false
                                },
                                colors =
                                    MenuDefaults.itemColors(
                                        textColor =
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                    ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            if (configId != configList.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }

            selectedConfig.value?.let { config ->
                item {
                    ModelApiSettingsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        showNotification = { message -> showNotification(message) },
                        showUndoableNotification = { message ->
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = context.getString(R.string.undo),
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            ) == SnackbarResult.ActionPerformed
                        },
                        navigateToMnnModelDownload = navigateToMnnModelDownload
                    )
                }

                item {
                    ContextSummarySettingsSection(
                        config = config,
                        configManager = configManager,
                        scope = scope,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    ModelParametersSection(
                        config = config,
                        configManager = configManager,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    CustomHeadersSettingsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    AdvancedSettingsSection(
                        config = config,
                        configManager = configManager,
                        saveCoordinator = saveCoordinator,
                        keyAvailabilityTester = keyAvailabilityTester,
                        showNotification = { message -> showNotification(message) }
                    )
                }
            }

        }

        // 新建配置对话框
        if (showAddConfigDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddConfigDialog = false
                    newConfigName = ""
                },
                title = {
                    Text(
                        stringResource(R.string.new_model_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.new_model_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newConfigName,
                            onValueChange = { newConfigName = it },
                            label = {
                                Text(
                                    stringResource(R.string.model_config_name),
                                    fontSize = 12.sp
                                )
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.model_config_name_placeholder),
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape =
                                RoundedCornerShape(
                                    KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp
                                ),
                            colors = kiyoriSettingsOutlinedTextFieldColors(),
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newConfigName.isNotBlank()) {
                                scope.launch {
                                    val configId = configManager.createConfig(newConfigName)
                                    selectedConfigId = configId
                                    showAddConfigDialog = false
                                    newConfigName = ""
                                    showNotification(context.getString(R.string.new_config_created))
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(stringResource(R.string.create_action), fontSize = 13.sp) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddConfigDialog = false
                            newConfigName = ""
                        }
                    ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        // 重命名配置对话框
        if (showRenameConfigDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRenameConfigDialog = false
                    renameConfigName = ""
                },
                title = {
                    Text(
                        stringResource(R.string.rename_model_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.rename_model_config_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameConfigName,
                            onValueChange = { renameConfigName = it },
                            label = {
                                Text(
                                    stringResource(R.string.model_config_name),
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape =
                                RoundedCornerShape(
                                    KIYORI_SETTINGS_FIELD_CORNER_RADIUS_DP.dp
                                ),
                            colors = kiyoriSettingsOutlinedTextFieldColors(),
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameConfigName.isNotBlank()) {
                                scope.launch {
                                    configManager.updateConfigBase(
                                        selectedConfigId,
                                        renameConfigName
                                    )
                                    configNameMap[selectedConfigId] = renameConfigName
                                    showRenameConfigDialog = false
                                    renameConfigName = ""
                                    showNotification(context.getString(R.string.config_renamed))
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(stringResource(R.string.confirm_rename), fontSize = 13.sp) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRenameConfigDialog = false
                            renameConfigName = ""
                        }
                    ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        pendingDeletion?.let { deletion ->
            AlertDialog(
                onDismissRequest = { pendingDeletion = null },
                title = { Text(stringResource(R.string.model_config_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.model_config_delete_message,
                            deletion.name,
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeletion = null
                            scope.launch {
                                configManager.deleteConfig(deletion.id)
                                selectedConfigId = ModelConfigManager.DEFAULT_CONFIG_ID
                                showNotification(context.getString(R.string.config_deleted))
                            }
                        },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text(stringResource(R.string.delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletion = null }) {
                        Text(stringResource(R.string.cancel_action))
                    }
                },
            )
        }

        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomHeadersSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator,
    showNotification: (String) -> Unit
) {
    val latestConfig by rememberUpdatedState(config)
    var headersState by remember(config.id) {
        mutableStateOf(parseHeaderEntries(config.customHeaders))
    }
    var headersExpanded by rememberSaveable(config.id) { mutableStateOf(false) }
    var showHeaderPresetsMenu by remember { mutableStateOf(false) }
    val saveFailedText = stringResource(R.string.save_failed)
    val saveMutex = remember(config.id) { Mutex() }

    LaunchedEffect(config.id, config.customHeaders) {
        headersState = parseHeaderEntries(config.customHeaders)
    }

    suspend fun persistHeaders(serializedHeaders: String) {
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                configManager.updateCustomHeaders(latestConfig.id, serializedHeaders)
                EnhancedAIService.refreshAllServices(configManager.appContext)
            }
        }
    }

    val readyHeadersState = headersState as? HeaderEntriesState.Ready
    val latestHeadersState by rememberUpdatedState(headersState)
    RegisterModelConfigSaveAction(
        coordinator = saveCoordinator,
        key = "headers:${config.id}",
        action = {
            val current = latestHeadersState
            if (current is HeaderEntriesState.Ready) {
                persistHeaders(serializeHeaderEntries(current.entries))
            }
        }
    )

    if (readyHeadersState != null) {
        DebouncedModelConfigAutoSaveEffect(
            effectKey = config.id to (headersState is HeaderEntriesState.Ready),
            valueProvider = {
                val current = headersState
                check(current is HeaderEntriesState.Ready) {
                    "Custom header auto-save requires a valid persisted JSON state"
                }
                serializeHeaderEntries(current.entries)
            },
            persist = { serializedHeaders -> persistHeaders(serializedHeaders) },
            onError = {
                showNotification(saveFailedText)
            }
        )
    }

    val configuredHeadersCount =
        readyHeadersState?.entries?.count { it.first.trim().isNotEmpty() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = readyHeadersState != null) {
                        headersExpanded = !headersExpanded
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_custom_headers),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_custom_headers_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (configuredHeadersCount != null && configuredHeadersCount > 0) {
                    Text(
                        text =
                            stringResource(
                                R.string.headers_configured_count,
                                configuredHeadersCount
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector =
                        if (headersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = headersExpanded && readyHeadersState != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = { showHeaderPresetsMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.headers_load_preset))
                            }

                            OutlinedButton(
                                onClick = {
                                    val current = headersState as HeaderEntriesState.Ready
                                    headersState =
                                        HeaderEntriesState.Ready(current.entries + ("" to ""))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.headers_add_header))
                            }
                        }

                        DropdownMenu(
                            expanded = showHeaderPresetsMenu,
                            onDismissRequest = { showHeaderPresetsMenu = false }
                        ) {
                            headerPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(preset.nameResId)) },
                                    onClick = {
                                        val current = headersState as HeaderEntriesState.Ready
                                        val mergedHeaders =
                                            current.entries
                                                .associate { it.first to it.second }
                                                .toMutableMap()
                                        mergedHeaders.putAll(preset.headers)
                                        headersState =
                                            HeaderEntriesState.Ready(mergedHeaders.toList())
                                        showHeaderPresetsMenu = false
                                    }
                                )
                            }
                        }
                    }

                    readyHeadersState?.entries?.forEachIndexed { index, header ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = header.first,
                                onValueChange = { newValue ->
                                    headersState =
                                        HeaderEntriesState.Ready(
                                            readyHeadersState.entries.toMutableList().also {
                                            it[index] = newValue to header.second
                                            }
                                        )
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.headers_key_label)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = header.second,
                                onValueChange = { newValue ->
                                    headersState =
                                        HeaderEntriesState.Ready(
                                            readyHeadersState.entries.toMutableList().also {
                                            it[index] = header.first to newValue
                                            }
                                        )
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.headers_value_label)) },
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    headersState =
                                        HeaderEntriesState.Ready(
                                            readyHeadersState.entries
                                                .toMutableList()
                                                .apply { removeAt(index) }
                                        )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription =
                                        stringResource(R.string.headers_delete_header)
                                )
                            }
                        }
                    }
                }
            }

            if (headersState == HeaderEntriesState.InvalidPersistedJson) {
                Text(
                    text = stringResource(R.string.model_config_custom_headers_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ContextSummarySettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    scope: CoroutineScope,
    showNotification: (String) -> Unit
) {
    val latestConfig by rememberUpdatedState(config)
    var contextLengthInput by remember(config.id) { mutableStateOf(formatFloatValue(config.contextLength)) }
    var maxContextLengthInput by remember(config.id) { mutableStateOf(formatFloatValue(config.maxContextLength)) }
    var contextError by remember { mutableStateOf<String?>(null) }

    var enableSummary by remember(config.id) { mutableStateOf(config.enableSummary) }
    var summaryTokenThresholdInput by remember(config.id) { mutableStateOf(formatFloatValue(config.summaryTokenThreshold)) }
    var enableSummaryByMessageCount by remember(config.id) { mutableStateOf(config.enableSummaryByMessageCount) }
    var summaryMessageCountThresholdInput by remember(config.id) { mutableStateOf(config.summaryMessageCountThreshold.toString()) }
    var summaryError by remember { mutableStateOf<String?>(null) }

    var contextExpanded by rememberSaveable { mutableStateOf(false) }
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }

    val errorValidContextLength = stringResource(id = R.string.model_config_error_valid_context_length)
    val errorValidMaxContextLength = stringResource(id = R.string.model_config_error_valid_max_context_length)
    val errorSaveFailed = stringResource(id = R.string.model_config_error_save_failed)
    val errorSummaryThresholdRange = stringResource(id = R.string.model_config_error_summary_threshold_range)
    val errorValidMessageCount = stringResource(id = R.string.model_config_error_valid_message_count)

    LaunchedEffect(config.id, config.contextLength) {
        contextLengthInput = formatFloatValue(config.contextLength)
    }
    LaunchedEffect(config.id, config.maxContextLength) {
        maxContextLengthInput = formatFloatValue(config.maxContextLength)
    }
    LaunchedEffect(config.id, config.enableSummary) {
        enableSummary = config.enableSummary
    }
    LaunchedEffect(config.id, config.summaryTokenThreshold) {
        summaryTokenThresholdInput = formatFloatValue(config.summaryTokenThreshold)
    }
    LaunchedEffect(config.id, config.enableSummaryByMessageCount) {
        enableSummaryByMessageCount = config.enableSummaryByMessageCount
    }
    LaunchedEffect(config.id, config.summaryMessageCountThreshold) {
        summaryMessageCountThresholdInput = config.summaryMessageCountThreshold.toString()
    }

    LaunchedEffect(config.id) {
        snapshotFlow { contextLengthInput to maxContextLengthInput }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { (contextText, maxText) ->
                val contextValue = contextText.toFloatOrNull()
                val maxValue = maxText.toFloatOrNull()

                when {
                    contextValue == null || contextValue <= 0f -> {
                        contextError = errorValidContextLength
                    }

                    maxValue == null || maxValue <= 0f -> {
                        contextError = errorValidMaxContextLength
                    }

                    else -> {
                        val current = latestConfig
                        val isNoOp =
                            current.contextLength == contextValue &&
                                    current.maxContextLength == maxValue
                        if (isNoOp) {
                            contextError = null
                            return@collectLatest
                        }

                        try {
                            configManager.updateContextSettings(
                                configId = current.id,
                                contextLength = contextValue,
                                maxContextLength = maxValue,
                                enableMaxContextMode = current.enableMaxContextMode
                            )
                            contextError = null
                        } catch (e: Exception) {
                            AppLogger.e(
                                MODEL_CONFIG_LOG_TAG,
                                "Failed to save context settings",
                                e,
                            )
                            contextError = errorSaveFailed
                        }
                    }
                }
            }
    }

    LaunchedEffect(config.id) {
        snapshotFlow {
            listOf(
                enableSummary,
                summaryTokenThresholdInput,
                enableSummaryByMessageCount,
                summaryMessageCountThresholdInput
            )
        }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest {
                val current = latestConfig
                if (!enableSummary) {
                    if (current.enableSummary) {
                        try {
                            configManager.updateSummarySettings(
                                configId = current.id,
                                enableSummary = false,
                                summaryTokenThreshold = current.summaryTokenThreshold,
                                enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                                summaryMessageCountThreshold = current.summaryMessageCountThreshold
                            )
                            summaryError = null
                        } catch (e: Exception) {
                            AppLogger.e(
                                MODEL_CONFIG_LOG_TAG,
                                "Failed to disable automatic summary",
                                e,
                            )
                            summaryError = errorSaveFailed
                        }
                    }
                    return@collectLatest
                }

                val threshold = summaryTokenThresholdInput.toFloatOrNull()
                val messageCount = summaryMessageCountThresholdInput.toIntOrNull()

                when {
                    threshold == null || threshold <= 0f || threshold >= 1f -> {
                        summaryError = errorSummaryThresholdRange
                    }

                    enableSummaryByMessageCount && (messageCount == null || messageCount <= 0) -> {
                        summaryError = errorValidMessageCount
                    }

                    else -> {
                        val nextMessageCount =
                            if (enableSummaryByMessageCount) messageCount
                                ?: current.summaryMessageCountThreshold
                            else current.summaryMessageCountThreshold

                        val isNoOp =
                            current.enableSummary == enableSummary &&
                                    current.summaryTokenThreshold == threshold &&
                                    current.enableSummaryByMessageCount == enableSummaryByMessageCount &&
                                    current.summaryMessageCountThreshold == nextMessageCount
                        if (isNoOp) {
                            summaryError = null
                            return@collectLatest
                        }

                        try {
                            configManager.updateSummarySettings(
                                configId = current.id,
                                enableSummary = enableSummary,
                                summaryTokenThreshold = threshold,
                                enableSummaryByMessageCount = enableSummaryByMessageCount,
                                summaryMessageCountThreshold = nextMessageCount
                            )
                            summaryError = null
                        } catch (e: Exception) {
                            AppLogger.e(
                                MODEL_CONFIG_LOG_TAG,
                                "Failed to save automatic summary settings",
                                e,
                            )
                            summaryError = errorSaveFailed
                        }
                    }
                }
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { contextExpanded = !contextExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.settings_context_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (contextExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (contextExpanded) stringResource(id = R.string.model_config_collapse) else stringResource(id = R.string.model_config_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = contextExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsInfoBanner(text = stringResource(id = R.string.settings_context_card_content))

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_context_length),
                            subtitle = stringResource(id = R.string.settings_context_length_subtitle),
                            value = contextLengthInput,
                            onValueChange = {
                                contextLengthInput = it
                                contextError = null
                            },
                            unitText = "K",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            )
                        )

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_max_context_length),
                            subtitle = stringResource(id = R.string.settings_max_context_length_subtitle),
                            value = maxContextLengthInput,
                            onValueChange = {
                                maxContextLengthInput = it
                                contextError = null
                            },
                            unitText = "K",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            )
                        )

                        contextError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { summaryExpanded = !summaryExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Summarize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.settings_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (summaryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (summaryExpanded) stringResource(id = R.string.model_config_collapse) else stringResource(id = R.string.model_config_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = summaryExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSwitchRow(
                            title = stringResource(id = R.string.settings_enable_summary),
                            subtitle = stringResource(id = R.string.settings_enable_summary_desc),
                            checked = enableSummary,
                            onCheckedChange = { enableSummary = it }
                        )

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_summary_threshold),
                            subtitle = stringResource(id = R.string.settings_summary_threshold_subtitle),
                            value = summaryTokenThresholdInput,
                            onValueChange = {
                                summaryTokenThresholdInput = it
                                summaryError = null
                            },
                            enabled = enableSummary,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            )
                        )

                        SettingsSwitchRow(
                            title = stringResource(id = R.string.settings_enable_summary_by_message_count),
                            subtitle = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
                            checked = enableSummaryByMessageCount,
                            onCheckedChange = { enableSummaryByMessageCount = it },
                            enabled = enableSummary
                        )

                        SettingsTextField(
                            title = stringResource(id = R.string.settings_summary_message_count_threshold),
                            subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
                            value = summaryMessageCountThresholdInput,
                            onValueChange = {
                                summaryMessageCountThresholdInput = it
                                summaryError = null
                            },
                            unitText = stringResource(id = R.string.model_config_unit_items),
                            enabled = enableSummary && enableSummaryByMessageCount,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            )
                        )

                        summaryError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ConnectionTestItem(
    val labelResId: Int,
    val type: ModelConnectionTestType,
    val outcome: ModelConnectionTestOutcome,
    val error: String? = null
) {
    fun statusText(context: android.content.Context): String {
        return when (outcome) {
            ModelConnectionTestOutcome.PASSED ->
                context.getString(
                    when (type) {
                        ModelConnectionTestType.IMAGE ->
                            R.string.test_media_image_understood
                        ModelConnectionTestType.AUDIO ->
                            R.string.test_media_audio_understood
                        ModelConnectionTestType.VIDEO ->
                            R.string.test_media_video_understood
                        else -> R.string.test_connection_success
                    }
                )
            ModelConnectionTestOutcome.UNVERIFIED ->
                context.getString(
                    when (type) {
                        ModelConnectionTestType.IMAGE ->
                            R.string.test_media_image_unverified
                        ModelConnectionTestType.AUDIO ->
                            R.string.test_media_audio_unverified
                        ModelConnectionTestType.VIDEO ->
                            R.string.test_media_video_unverified
                        else -> R.string.test_media_unverified
                    }
                )
            ModelConnectionTestOutcome.FAILED ->
                context.getString(
                    R.string.test_connection_failed,
                    error ?: ""
                )
        }
    }
}

private fun ModelConnectionTestType.toLabelResId(): Int {
    return when (this) {
        ModelConnectionTestType.CHAT -> R.string.test_item_chat
        ModelConnectionTestType.TOOL_CALL -> R.string.test_item_toolcall
        ModelConnectionTestType.IMAGE -> R.string.test_item_image
        ModelConnectionTestType.AUDIO -> R.string.test_item_audio
        ModelConnectionTestType.VIDEO -> R.string.test_item_video
    }
}

private fun formatFloatValue(value: Float): String {
    return if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.2f", value)
}
