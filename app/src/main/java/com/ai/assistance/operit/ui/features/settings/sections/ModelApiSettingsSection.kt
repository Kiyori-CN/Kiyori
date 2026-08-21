package com.ai.assistance.operit.ui.features.settings.sections

import android.annotation.SuppressLint
import android.content.res.Resources
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.api.chat.llmprovider.LlamaProvider
import com.ai.assistance.operit.api.chat.llmprovider.ModelListFetcher
import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.collects.ProviderProtocolDetectionResult
import com.ai.assistance.operit.data.collects.ProviderProtocolDetectionSource
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.mergeModelNames
import com.ai.assistance.operit.data.model.reconcileUpstreamModelSelection
import com.ai.assistance.operit.data.model.serializeModelNames
import com.ai.assistance.operit.data.model.UpstreamModelSelectionChange
import com.ai.assistance.operit.data.preferences.ModelConfigBindingImpact
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigModelBindingCoordinator
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistry
import com.ai.assistance.operit.ui.common.input.bringIntoViewOnImeFocus
import com.ai.assistance.operit.ui.features.settings.DebouncedModelConfigAutoSaveEffect
import com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.settings.components.ModelNameTagEditor
import com.ai.assistance.operit.ui.features.settings.components.UpstreamModelPickerSheet
import com.ai.assistance.operit.util.LocationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val TAG = "ModelApiSettings"

private val modelApiSettingsSaveMutex = Mutex()

private data class PendingModelDeletion(
    val modelName: String,
    val impact: ModelConfigBindingImpact,
    val nextModels: List<String>
)

private data class PendingUpstreamModelSelection(
    val change: UpstreamModelSelectionChange,
    val impact: ModelConfigBindingImpact
)

private sealed interface ModelClearDialogState {
    data object Checking : ModelClearDialogState

    data class Confirm(
        val modelCount: Int
    ) : ModelClearDialogState

    data class Blocked(
        val bindingCount: Int
    ) : ModelClearDialogState
}

@Composable
@SuppressLint("MissingPermission")
fun ModelApiSettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        saveCoordinator: ModelConfigSaveCoordinator,
        showNotification: (String) -> Unit,
        showUndoableNotification: suspend (String) -> Boolean,
        navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val modelBindingCoordinator =
        remember(context) { ModelConfigModelBindingCoordinator(context) }

    // 区域告警可见性
    var showRegionWarning by remember { mutableStateOf(false) }

    fun getDefaultModelName(
        providerTypeId: String,
        protocol: ApiProtocol = ApiProtocol.PROVIDER_NATIVE,
    ): String {
        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId) ?: return ""
        val resolvedProtocol =
            if (protocol == ApiProtocol.PROVIDER_NATIVE) {
                ApiProtocol.fromProviderType(providerType)
            } else {
                protocol
            }
        return ApiProviderConfigs.getDefaultModelName(providerType, resolvedProtocol)
    }

    fun getEndpointOptions(
        providerTypeId: String,
        protocol: ApiProtocol,
    ): List<Pair<String, String>>? {
        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId) ?: return null
        return ApiProviderConfigs.getEndpointOptions(providerType, protocol)
            ?.map { it.endpoint to it.label }
    }

    // 检查当前模型名称是否是某个提供商的默认值
    fun isDefaultModelName(modelName: String): Boolean {
        return ApiProviderConfigs.isDefaultModelName(modelName)
    }

    // API编辑状态
    var apiEndpointInput by remember(config.id) { mutableStateOf(config.apiEndpoint) }
    var apiKeyInput by remember(config.id) { mutableStateOf(config.apiKey) }
    var modelNamesInput by remember(config.id) { mutableStateOf(getModelList(config.modelName)) }
    var modelBindingReplacementName by remember(config.id) { mutableStateOf<String?>(null) }
    var pendingModelDeletion by remember(config.id) {
        mutableStateOf<PendingModelDeletion?>(null)
    }
    var pendingUpstreamModelSelection by remember(config.id) {
        mutableStateOf<PendingUpstreamModelSelection?>(null)
    }
    var modelClearDialogState by remember(config.id) {
        mutableStateOf<ModelClearDialogState?>(null)
    }
    var selectedProviderTypeId by remember(config.id) { mutableStateOf(config.apiProviderTypeId) }
    var selectedApiProtocol by remember(config.id) { mutableStateOf(config.apiProtocol) }
    var hasInitializedProviderEndpointSync by remember(config.id) { mutableStateOf(false) }
    var previousProviderTypeId by remember(config.id) { mutableStateOf(config.apiProviderTypeId) }
    var previousApiProtocol by remember(config.id) { mutableStateOf(config.apiProtocol) }
    var protocolDetectionMessage by remember(config.id) { mutableStateOf<String?>(null) }
    val selectedApiProviderRoute = ApiProviderType.fromProviderTypeId(selectedProviderTypeId)
    val selectedApiProvider =
        selectedApiProviderRoute?.let(ModelApiProviderPresentationPolicy::canonicalProvider)

    // MNN特定配置状态
    var mnnForwardTypeInput by remember(config.id) { mutableStateOf(config.mnnForwardType) }
    var mnnThreadCountInput by remember(config.id) { mutableStateOf(config.mnnThreadCount.toString()) }

    // llama.cpp 常用配置状态
    var llamaThreadCountInput by remember(config.id) { mutableStateOf(config.llamaThreadCount.toString()) }
    var llamaContextSizeInput by remember(config.id) { mutableStateOf(config.llamaContextSize.toString()) }
    var llamaGpuLayersInput by remember(config.id) { mutableStateOf(config.llamaGpuLayers.toString()) }

    // 图片处理配置状态
    var enableDirectImageProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectImageProcessing) }

    var enableDirectAudioProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectAudioProcessing) }

    var enableDirectVideoProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectVideoProcessing) }
    
    // Google Search Grounding 配置状态 (仅Gemini)
    var enableGoogleSearchInput by remember(config.id) { mutableStateOf(config.enableGoogleSearch) }

    // Claude 1小时提示缓存配置状态 (仅Claude)
    var enableClaude1hPromptCacheInput by remember(config.id) {
        mutableStateOf(config.enableClaude1hPromptCache)
    }
    
    // Tool Call配置状态
    var enableToolCallInput by remember(config.id) { mutableStateOf(config.enableToolCall) }

    data class ApiAutoSaveState(
        val apiEndpoint: String,
        val apiKey: String,
        val modelName: String,
        val providerTypeId: String,
        val provider: ApiProviderType,
        val protocol: ApiProtocol,
        val mnnForwardType: Int,
        val mnnThreadCount: Int,
        val llamaThreadCount: Int,
        val llamaContextSize: Int,
        val llamaGpuLayers: Int,
        val enableDirectImageProcessing: Boolean,
        val enableDirectAudioProcessing: Boolean,
        val enableDirectVideoProcessing: Boolean,
        val enableGoogleSearch: Boolean,
        val enableClaude1hPromptCache: Boolean,
        val enableToolCall: Boolean,
        val replacementModelName: String?,
    )

    // 保存设置的通用函数
    suspend fun persist(state: ApiAutoSaveState): ModelConfigBindingImpact {
        return modelApiSettingsSaveMutex.withLock {
            val impact =
                modelBindingCoordinator.reconcileAndPersist(
                    configId = config.id,
                    nextModels = getModelList(state.modelName),
                    replacementModelName = state.replacementModelName
                ) {
                    withContext(Dispatchers.IO) {
                        configManager.updateApiSettingsFull(
                            configId = config.id,
                            apiKey = state.apiKey,
                            apiEndpoint = state.apiEndpoint,
                            modelName = state.modelName,
                            apiProviderType = state.provider,
                            apiProviderTypeId = state.providerTypeId,
                            apiProtocol = state.protocol,
                            mnnForwardType = state.mnnForwardType,
                            mnnThreadCount = state.mnnThreadCount,
                            llamaThreadCount = state.llamaThreadCount,
                            llamaContextSize = state.llamaContextSize,
                            llamaGpuLayers = state.llamaGpuLayers,
                            enableDirectImageProcessing = state.enableDirectImageProcessing,
                            enableDirectAudioProcessing = state.enableDirectAudioProcessing,
                            enableDirectVideoProcessing = state.enableDirectVideoProcessing,
                            enableGoogleSearch = state.enableGoogleSearch,
                            enableClaude1hPromptCache = state.enableClaude1hPromptCache,
                            enableToolCall = state.enableToolCall,
                        )
                    }
                }

            EnhancedAIService.refreshAllServices(configManager.appContext)
            impact
        }
    }

    fun buildAutoSaveState(): ApiAutoSaveState {
        return ApiAutoSaveState(
            apiEndpoint = apiEndpointInput,
            apiKey = apiKeyInput,
            modelName = serializeModelNames(modelNamesInput),
            providerTypeId = selectedProviderTypeId,
            provider = selectedApiProviderRoute ?: ApiProviderType.OTHER,
            protocol = selectedApiProtocol,
            mnnForwardType = mnnForwardTypeInput,
            mnnThreadCount = mnnThreadCountInput.toIntOrNull() ?: 4,
            llamaThreadCount = llamaThreadCountInput.toIntOrNull()?.coerceAtLeast(1) ?: 4,
            llamaContextSize = llamaContextSizeInput.toIntOrNull()?.coerceAtLeast(1) ?: 2048,
            llamaGpuLayers = llamaGpuLayersInput.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            enableDirectImageProcessing = enableDirectImageProcessingInput,
            enableDirectAudioProcessing = enableDirectAudioProcessingInput,
            enableDirectVideoProcessing = enableDirectVideoProcessingInput,
            enableGoogleSearch = enableGoogleSearchInput,
            enableClaude1hPromptCache = enableClaude1hPromptCacheInput,
            enableToolCall = enableToolCallInput,
            replacementModelName = modelBindingReplacementName,
        )
    }

    suspend fun flushSettings(showSuccess: Boolean): ModelConfigBindingImpact {
        val state = buildAutoSaveState()
        try {
            AppLogger.d(
                TAG,
                ModelApiProviderPresentationPolicy.formatSettingsSaveLog(
                    provider = state.provider,
                    modelCount = getModelList(state.modelName).size,
                    credentialConfigured = state.apiKey.isNotBlank(),
                )
            )
            val impact = persist(state)
            AppLogger.d(TAG, "API设置保存完成并刷新服务")
            if (showSuccess) {
                showNotification(resources.getString(R.string.api_settings_saved))
            }
            return impact
        } catch (e: Exception) {
            if (showSuccess) {
                showNotification((e.message ?: resources.getString(R.string.save_failed)))
            } else {
                AppLogger.e(TAG, "API设置自动保存失败: ${e.message}", e)
            }
            throw e
        }
    }

    RegisterModelConfigSaveAction(
        coordinator = saveCoordinator,
        key = "api:${config.id}",
        action = { showSuccess -> flushSettings(showSuccess) }
    )

    DebouncedModelConfigAutoSaveEffect(
        effectKey = config.id,
        valueProvider = { buildAutoSaveState() },
        persist = { state -> persist(state) },
        onError = { e ->
            showNotification((e.message ?: resources.getString(R.string.auto_save_failed)))
        }
    )

    // 根据API提供商获取默认的API端点URL
    fun getDefaultApiEndpoint(
        providerType: ApiProviderType,
        protocol: ApiProtocol = ApiProtocol.fromProviderType(providerType),
    ): String {
        return ApiProviderConfigs.getDefaultApiEndpoint(providerType, protocol)
    }

    // 添加一个函数检查当前API端点是否为某个提供商的默认端点
    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return ApiProviderConfigs.isDefaultApiEndpoint(endpoint)
    }

    fun syncMoonshotModelForEndpoint(endpoint: String) {
        if (selectedApiProvider != ApiProviderType.MOONSHOT) {
            return
        }

        val moonshotDefaultModel = getDefaultModelName(ApiProviderType.MOONSHOT.name)
        val isKimiCodeEndpoint = endpoint.contains("api.kimi.com/coding/v1", ignoreCase = true)
        val currentSingleModel = modelNamesInput.singleOrNull()

        if (isKimiCodeEndpoint) {
            if (modelNamesInput.isEmpty() || currentSingleModel == moonshotDefaultModel) {
                modelNamesInput = listOf("kimi-for-coding")
                modelBindingReplacementName = "kimi-for-coding"
            }
        } else if (currentSingleModel == "kimi-for-coding" && moonshotDefaultModel.isNotEmpty()) {
            modelNamesInput = listOf(moonshotDefaultModel)
            modelBindingReplacementName = moonshotDefaultModel
        }
    }

    LaunchedEffect(selectedApiProvider) {
        val isInternationalProvider =
            selectedApiProvider?.let(ModelApiProviderPresentationPolicy::section) ==
                ProviderSelectionSection.INTERNATIONAL
        showRegionWarning =
            isInternationalProvider && LocationUtils.isDeviceInMainlandChina(context)
    }

    // 当API提供商或协议改变时更新端点。
    LaunchedEffect(selectedProviderTypeId, selectedApiProtocol) {
        AppLogger.d("ModelApiSettingsSection", "API提供商或协议改变")
        val shouldSyncEndpointByProviderChange = hasInitializedProviderEndpointSync
        hasInitializedProviderEndpointSync = true
        if (!shouldSyncEndpointByProviderChange) {
            // 首次进入页面时保留持久化配置，避免把用户已选择的端点覆盖成默认值。
            return@LaunchedEffect
        }

        if (selectedApiProvider == null) {
            previousProviderTypeId = selectedProviderTypeId
            previousApiProtocol = selectedApiProtocol
            return@LaunchedEffect
        }

        val previousProvider = ApiProviderType.fromProviderTypeId(previousProviderTypeId)
        val previousDefaultEndpoint =
            previousProvider
                ?.let { getDefaultApiEndpoint(it, previousApiProtocol) }
                .orEmpty()
        val shouldApplyNewProviderDefault =
            apiEndpointInput.isEmpty() ||
                isDefaultApiEndpoint(apiEndpointInput) ||
                (previousDefaultEndpoint.isNotEmpty() && apiEndpointInput == previousDefaultEndpoint)

        if (shouldApplyNewProviderDefault) {
            apiEndpointInput = getDefaultApiEndpoint(selectedApiProvider, selectedApiProtocol)
        }
        previousProviderTypeId = selectedProviderTypeId
        previousApiProtocol = selectedApiProtocol
    }

    // 模型列表状态
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelsDialog by remember { mutableStateOf(false) }
    var modelsList by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var showEndpointDialog by remember(config.id) { mutableStateOf(false) }

    // 检查是否未填写API密钥（仅用于UI显示）
    val isUsingDefaultApiKey = apiKeyInput.isBlank()
    val providerRequiresApiKey =
        ApiProviderConfigs.requiresApiKey(selectedProviderTypeId, apiEndpointInput)
    val isMnnProvider = selectedApiProvider == ApiProviderType.MNN
    val isLlamaProvider = selectedApiProvider == ApiProviderType.LLAMA_CPP
    val isToolPkgProvider = selectedApiProvider == null
    val canRequestModelList =
        isToolPkgProvider ||
            isMnnProvider ||
            isLlamaProvider ||
            (
                apiEndpointInput.isNotBlank() &&
                    (!providerRequiresApiKey || (!isUsingDefaultApiKey && apiKeyInput.isNotBlank()))
            )
    val endpointOptions = getEndpointOptions(selectedProviderTypeId, selectedApiProtocol)
    val selectableEndpointOptions =
        when {
            endpointOptions != null -> endpointOptions
            selectedApiProvider != null -> {
                val defaultEndpoint = getDefaultApiEndpoint(selectedApiProvider, selectedApiProtocol)
                if (defaultEndpoint.isNotBlank()) {
                    listOf(defaultEndpoint to defaultEndpoint)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }

    suspend fun fetchAvailableModels(): Result<List<ModelOption>> {
        return when {
            isMnnProvider -> ModelListFetcher.getMnnLocalModels(context)
            isLlamaProvider -> ModelListFetcher.getLlamaLocalModels(context)
            isToolPkgProvider -> runCatching {
                val service =
                    AIServiceFactory.createService(
                        config =
                            config.copy(
                                apiKey = apiKeyInput,
                                apiEndpoint = apiEndpointInput,
                                modelName = serializeModelNames(modelNamesInput),
                                apiProviderType = ApiProviderType.OTHER,
                                apiProviderTypeId = selectedProviderTypeId,
                                apiProtocol = selectedApiProtocol,
                                enableDirectImageProcessing = enableDirectImageProcessingInput,
                                enableDirectAudioProcessing = enableDirectAudioProcessingInput,
                                enableDirectVideoProcessing = enableDirectVideoProcessingInput,
                                enableGoogleSearch = enableGoogleSearchInput,
                                enableClaude1hPromptCache = enableClaude1hPromptCacheInput,
                                enableToolCall = enableToolCallInput
                            ),
                        modelConfigManager = configManager,
                        context = context
                    )
                try {
                    service.getModelsList(context).getOrThrow()
                } finally {
                    service.release()
                }
            }

            else ->
                ModelListFetcher.getModelsList(
                    context,
                    apiKeyInput,
                    apiEndpointInput,
                    selectedApiProviderRoute,
                    selectedApiProtocol,
                )
        }
    }

    fun requestAvailableModels() {
        AppLogger.d(
            TAG,
            "请求上游模型列表 - API端点: $apiEndpointInput, API类型: $selectedProviderTypeId"
        )
        val gettingModelsText = resources.getString(R.string.getting_models_list)
        val getModelsFailedText = resources.getString(R.string.get_models_list_failed)
        val defaultConfigNoModelsText =
            resources.getString(R.string.default_config_no_models_list)
        val fillEndpointKeyText = resources.getString(R.string.fill_endpoint_and_key)
        val modelsListSuccessText = resources.getString(R.string.models_list_success)

        scope.launch {
            if (canRequestModelList) {
                showNotification(gettingModelsText)
                isLoadingModels = true
                modelLoadError = null
                try {
                    val result = fetchAvailableModels()
                    if (result.isSuccess) {
                        val models = result.getOrThrow()
                        modelsList = models
                        modelLoadError = null
                        showModelsDialog = true
                        showNotification(modelsListSuccessText.format(models.size))
                    } else {
                        val error = requireNotNull(result.exceptionOrNull())
                        val failureText =
                            getModelsFailedText.format(error.toString())
                        modelLoadError = failureText
                        showNotification(failureText)
                    }
                } catch (error: Exception) {
                    AppLogger.e(TAG, "获取模型列表发生异常", error)
                    val failureText = getModelsFailedText.format(error.toString())
                    modelLoadError = failureText
                    showNotification(failureText)
                } finally {
                    isLoadingModels = false
                }
            } else if (
                !isToolPkgProvider &&
                    isUsingDefaultApiKey &&
                    providerRequiresApiKey
            ) {
                showNotification(defaultConfigNoModelsText)
            } else {
                showNotification(fillEndpointKeyText)
            }
        }
    }

    fun refreshAvailableModels() {
        if (!canRequestModelList || isLoadingModels) return

        scope.launch {
            isLoadingModels = true
            try {
                val result = fetchAvailableModels()
                if (result.isSuccess) {
                    modelsList = result.getOrThrow()
                    modelLoadError = null
                } else {
                    val error = requireNotNull(result.exceptionOrNull())
                    val failureText =
                        resources.getString(
                            R.string.refresh_models_list_failed,
                            error.toString()
                        )
                    modelLoadError = failureText
                    showNotification(failureText)
                }
            } catch (error: Exception) {
                AppLogger.e(TAG, "刷新模型列表发生异常", error)
                val failureText =
                    resources.getString(
                        R.string.refresh_models_list_failed,
                        error.toString()
                    )
                modelLoadError = failureText
                showNotification(failureText)
            } finally {
                isLoadingModels = false
            }
        }
    }

    fun addModels(addedModels: List<String>) {
        val mergedModels = mergeModelNames(modelNamesInput, addedModels)
        val addedCount = mergedModels.size - modelNamesInput.size
        if (addedCount == 0) {
            showNotification(resources.getString(R.string.model_add_no_new_items))
            return
        }
        modelNamesInput = mergedModels
        modelBindingReplacementName = null
        showNotification(resources.getString(R.string.model_add_result, addedCount))
    }

    suspend fun applyUpstreamModelSelection(
        change: UpstreamModelSelectionChange,
        impact: ModelConfigBindingImpact
    ) {
        val previousModels = modelNamesInput
        val previousReplacement = modelBindingReplacementName
        modelNamesInput = change.nextModels
        modelBindingReplacementName =
            if (impact.hasBindings) {
                require(change.nextModels.isNotEmpty()) {
                    "Cannot replace bindings when the model list is empty"
                }
                change.nextModels.first()
            } else {
                null
            }

        try {
            flushSettings(showSuccess = false)
            modelBindingReplacementName = null
            if (selectedApiProvider == ApiProviderType.MNN) {
                AppLogger.d(
                    TAG,
                    "应用MNN模型选择: ${serializeModelNames(change.nextModels)}"
                )
            }
            showNotification(
                resources.getString(
                    R.string.model_upstream_change_applied,
                    change.addedModels.size,
                    change.removedModels.size
                )
            )
        } catch (error: Exception) {
            modelNamesInput = previousModels
            modelBindingReplacementName = previousReplacement
            AppLogger.e(TAG, "应用上游模型选择失败", error)
            showNotification("${resources.getString(R.string.save_failed)}: $error")
        }
    }

    fun requestUpstreamModelSelection(selectedModels: Set<String>) {
        scope.launch {
            try {
                flushSettings(showSuccess = false)
                val change =
                    reconcileUpstreamModelSelection(
                        currentModels = modelNamesInput,
                        upstreamModels = modelsList.map(ModelOption::id),
                        selectedUpstreamModels = selectedModels
                    )
                if (!change.hasChanges) {
                    showModelsDialog = false
                    return@launch
                }

                val impact =
                    modelBindingCoordinator.inspectModelBindings(
                        configId = config.id,
                        modelNames = change.removedModels
                    )
                if (impact.hasBindings && change.nextModels.isEmpty()) {
                    showNotification(
                        resources.getString(
                            R.string.model_upstream_remove_bound_blocked,
                            impact.totalCount
                        )
                    )
                    return@launch
                }

                showModelsDialog = false
                if (impact.hasBindings) {
                    pendingUpstreamModelSelection =
                        PendingUpstreamModelSelection(
                            change = change,
                            impact = impact
                        )
                } else {
                    applyUpstreamModelSelection(change = change, impact = impact)
                }
            } catch (error: Exception) {
                AppLogger.e(TAG, "检查上游模型选择失败", error)
                showNotification("${resources.getString(R.string.save_failed)}: $error")
            }
        }
    }

    suspend fun applyModelDeletion(
        modelName: String,
        impact: ModelConfigBindingImpact,
        nextModels: List<String>
    ) {
        val deletedIndex = modelNamesInput.indexOf(modelName)
        if (deletedIndex < 0) return

        val previousModels = modelNamesInput
        val previousReplacement = modelBindingReplacementName
        modelNamesInput = nextModels
        modelBindingReplacementName =
            if (impact.hasBindings) nextModels.first() else null

        val appliedImpact =
            try {
                flushSettings(showSuccess = false)
            } catch (error: Exception) {
                modelNamesInput = previousModels
                modelBindingReplacementName = previousReplacement
                AppLogger.e(TAG, "删除模型保存失败", error)
                showNotification("${resources.getString(R.string.save_failed)}: $error")
                return
            }

        val shouldUndo =
            showUndoableNotification(
                resources.getString(R.string.model_deleted, modelName)
            )
        if (!shouldUndo) return

        val restoredModels =
            modelNamesInput.toMutableList().apply {
                if (modelName !in this) {
                    add(deletedIndex.coerceAtMost(size), modelName)
                }
            }
        modelNamesInput = restoredModels
        modelBindingReplacementName = null
        try {
            flushSettings(showSuccess = false)
            modelBindingCoordinator.restoreBindings(
                configId = config.id,
                modelName = modelName,
                impact = appliedImpact
            )
            EnhancedAIService.refreshAllServices(configManager.appContext)
        } catch (error: Exception) {
            AppLogger.e(TAG, "撤销模型删除失败", error)
            showNotification("${resources.getString(R.string.save_failed)}: $error")
        }
    }

    fun requestModelDeletion(modelName: String) {
        scope.launch {
            try {
                flushSettings(showSuccess = false)
                val nextModels = modelNamesInput.filterNot { it == modelName }
                val impact =
                    modelBindingCoordinator.inspectModelBindings(
                        configId = config.id,
                        modelName = modelName
                    )
                if (impact.hasBindings && nextModels.isEmpty()) {
                    showNotification(
                        resources.getString(
                            R.string.model_delete_last_bound_blocked,
                            modelName
                        )
                    )
                    return@launch
                }
                if (impact.hasBindings) {
                    pendingModelDeletion =
                        PendingModelDeletion(
                            modelName = modelName,
                            impact = impact,
                            nextModels = nextModels
                        )
                } else {
                    applyModelDeletion(
                        modelName = modelName,
                        impact = impact,
                        nextModels = nextModels
                    )
                }
            } catch (error: Exception) {
                AppLogger.e(TAG, "检查模型绑定失败", error)
                showNotification("${resources.getString(R.string.save_failed)}: $error")
            }
        }
    }

    fun requestClearModels() {
        if (modelClearDialogState != null) return
        // 页面级通知位于长列表末尾，不能承担当前视口中的关键反馈；异步检查开始前先展示模态状态。
        modelClearDialogState = ModelClearDialogState.Checking
        scope.launch {
            try {
                flushSettings(showSuccess = false)
                val impact = modelBindingCoordinator.inspectConfigBindings(config.id)
                modelClearDialogState =
                    if (impact.hasBindings) {
                        ModelClearDialogState.Blocked(impact.totalCount)
                    } else {
                        ModelClearDialogState.Confirm(modelNamesInput.size)
                    }
            } catch (error: Exception) {
                modelClearDialogState = null
                AppLogger.e(TAG, "检查模型列表绑定失败", error)
                showNotification("${resources.getString(R.string.save_failed)}: $error")
            }
        }
    }

    suspend fun clearModelsWithUndo() {
        val previousModels = modelNamesInput
        val previousReplacement = modelBindingReplacementName
        modelNamesInput = emptyList()
        modelBindingReplacementName = null
        try {
            flushSettings(showSuccess = false)
        } catch (error: Exception) {
            modelNamesInput = previousModels
            modelBindingReplacementName = previousReplacement
            AppLogger.e(TAG, "清空模型列表失败", error)
            showNotification("${resources.getString(R.string.save_failed)}: $error")
            return
        }

        val shouldUndo =
            showUndoableNotification(resources.getString(R.string.model_cleared))
        if (!shouldUndo) return

        modelNamesInput = mergeModelNames(modelNamesInput, previousModels)
        modelBindingReplacementName = null
        try {
            flushSettings(showSuccess = false)
        } catch (error: Exception) {
            AppLogger.e(TAG, "撤销清空模型列表失败", error)
            showNotification("${resources.getString(R.string.save_failed)}: $error")
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
            SettingsSectionHeader(
                    icon = Icons.Default.Api,
                    title = stringResource(R.string.api_settings)
            )

            var showApiProviderSheet by remember { mutableStateOf(false) }
            var showApiProtocolSheet by remember { mutableStateOf(false) }
            val protocolOptions =
                selectedApiProviderRoute
                    ?.let(ModelApiProviderPresentationPolicy::protocolOptions)
                    .orEmpty()

            SettingsSelectorRow(
                    title = stringResource(R.string.api_provider),
                    subtitle = stringResource(R.string.select_api_provider),
                    value = getProviderDisplayName(selectedProviderTypeId, resources),
                    valueSummary =
                        getProviderSummary(
                            providerTypeId = selectedProviderTypeId,
                            resources = resources,
                        ),
                     onClick = { showApiProviderSheet = true }
            )

            if (protocolOptions.size > 1) {
                val selectedProtocolOption =
                    protocolOptions.firstOrNull { it.protocol == selectedApiProtocol }
                        ?: protocolOptions.first()
                SettingsSelectorRow(
                    title = stringResource(R.string.api_protocol),
                    subtitle = stringResource(R.string.select_api_protocol),
                    value =
                        getProtocolDisplayName(
                            protocol = selectedProtocolOption.protocol,
                            provider = selectedApiProvider,
                            resources = resources,
                        ),
                    valueSummary = getProtocolSummary(selectedProtocolOption.protocol, resources),
                    onClick = {
                        protocolDetectionMessage = null
                        showApiProtocolSheet = true
                    },
                )
            }

            if (showApiProviderSheet) {
                ApiProviderSelectionSheet(
                        selectedProviderTypeId =
                            selectedApiProvider?.name ?: selectedProviderTypeId,
                        onDismissRequest = { showApiProviderSheet = false },
                        onProviderSelected = { provider ->
                            selectedProviderTypeId = provider.id
                            protocolDetectionMessage = null
                            selectedApiProtocol =
                                ModelApiProviderPresentationPolicy.defaultProtocol(
                                    ApiProviderType.fromProviderTypeId(provider.id)
                                        ?: ApiProviderType.OTHER
                                )

                            val providerDefaultModel =
                                getDefaultModelName(provider.id, selectedApiProtocol)
                            val currentSingleModel = modelNamesInput.singleOrNull()
                            val shouldUseProviderDefault =
                                providerDefaultModel.isNotEmpty() &&
                                    (
                                        modelNamesInput.isEmpty() ||
                                            (
                                                currentSingleModel != null &&
                                                    isDefaultModelName(currentSingleModel)
                                            )
                                    )
                            if (shouldUseProviderDefault) {
                                modelNamesInput = listOf(providerDefaultModel)
                                modelBindingReplacementName = providerDefaultModel
                            }

                            showApiProviderSheet = false
                        }
                )
            }

            if (showApiProtocolSheet) {
                ApiProtocolSelectionSheet(
                    options = protocolOptions,
                    selectedProtocol = selectedApiProtocol,
                    provider = selectedApiProvider ?: ApiProviderType.OTHER,
                    resources = resources,
                    detectionMessage = protocolDetectionMessage,
                    onDismissRequest = { showApiProtocolSheet = false },
                    onAutoDetect = {
                        val provider = selectedApiProvider ?: ApiProviderType.OTHER
                        when (
                            val result =
                                ApiProviderConfigs.detectProtocol(
                                    providerType = provider,
                                    apiEndpoint = apiEndpointInput,
                                )
                        ) {
                            is ProviderProtocolDetectionResult.Resolved -> {
                                selectedApiProtocol = result.protocol
                                selectedProviderTypeId = provider.name
                                protocolDetectionMessage = null
                                showApiProtocolSheet = false
                                showNotification(
                                    resources.getString(
                                        R.string.api_protocol_auto_detected_detail,
                                        getProtocolDisplayName(
                                            protocol = result.protocol,
                                            provider = provider,
                                            resources = resources,
                                        ),
                                        getProtocolDetectionSourceDisplayName(
                                            source = result.source,
                                            resources = resources,
                                        ),
                                    )
                                )
                            }

                            ProviderProtocolDetectionResult.RequiresManualSelection ->
                                run {
                                    protocolDetectionMessage =
                                        resources.getString(
                                            R.string.api_protocol_auto_detect_unresolved
                                        )
                                }
                        }
                    },
                    onProtocolSelected = { protocol ->
                        selectedApiProtocol = protocol
                        selectedProviderTypeId =
                            selectedApiProvider?.name ?: selectedProviderTypeId
                        protocolDetectionMessage = null
                        showApiProtocolSheet = false
                    },
                )
            }

            AnimatedVisibility(visible = showRegionWarning) {
                SettingsInfoBanner(text = stringResource(R.string.overseas_provider_warning))
            }

            if (isMnnProvider) {
                MnnSettingsBlock(
                        mnnForwardTypeInput = mnnForwardTypeInput,
                        onForwardTypeSelected = { mnnForwardTypeInput = it },
                        mnnThreadCountInput = mnnThreadCountInput,
                        onThreadCountChange = { input ->
                            if (input.isEmpty() || input.toIntOrNull() != null) {
                                mnnThreadCountInput = input
                            }
                        },
                        navigateToMnnModelDownload = navigateToMnnModelDownload
                )
            } else if (isLlamaProvider) {
                LlamaSettingsBlock(
                    llamaThreadCountInput = llamaThreadCountInput,
                    onThreadCountChange = { input ->
                        if (input.isEmpty() || input.toIntOrNull() != null) {
                            llamaThreadCountInput = input
                        }
                    },
                    llamaContextSizeInput = llamaContextSizeInput,
                    onContextSizeChange = { input ->
                        if (input.isEmpty() || input.toIntOrNull() != null) {
                            llamaContextSizeInput = input
                        }
                    },
                    llamaGpuLayersInput = llamaGpuLayersInput,
                    onGpuLayersChange = { input ->
                        if (input.isEmpty() || input.toIntOrNull() != null) {
                            llamaGpuLayersInput = input
                        }
                    }
                )
            } else {
                SettingsTextField(
                        title = stringResource(R.string.api_endpoint),
                        subtitle = stringResource(R.string.api_endpoint_placeholder),
                        value = apiEndpointInput,
                        onValueChange = {
                            apiEndpointInput = it.replace("\n", "").replace("\r", "").replace(" ", "")
                        },
                        enabled = true,
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                        ),
                        trailingContent =
                                if (selectableEndpointOptions.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { showEndpointDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else {
                                    null
                                }
                )

                if (showEndpointDialog && selectableEndpointOptions.isNotEmpty()) {
                    Dialog(onDismissRequest = { showEndpointDialog = false }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.api_endpoint),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                selectableEndpointOptions.forEach { (endpoint, label) ->
                                    val isSelected = apiEndpointInput == endpoint
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    apiEndpointInput = endpoint
                                                    syncMoonshotModelForEndpoint(endpoint)
                                                    showEndpointDialog = false
                                                }
                                                .background(
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface
                                                    }
                                                )
                                                .padding(vertical = 10.dp, horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = endpoint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (label.isNotBlank() && label != endpoint) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }

            val completedEndpoint =
                selectedApiProviderRoute?.let {
                    EndpointCompleter.completeEndpoint(apiEndpointInput, selectedApiProtocol)
                } ?: apiEndpointInput
            if (completedEndpoint != apiEndpointInput) {
                Text(
                    text = stringResource(R.string.actual_request_url, completedEndpoint),
                    style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.endpoint_completion_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val apiKeyInteractionSource = remember { MutableInteractionSource() }
                val isApiKeyFocused by apiKeyInteractionSource.collectIsFocusedAsState()

                SettingsTextField(
                        title = stringResource(R.string.api_key),
                        subtitle =
                                if (isUsingDefaultApiKey)
                                        stringResource(R.string.api_key_placeholder_default)
                                else
                                        stringResource(R.string.api_key_placeholder_custom),
                        value = if (isUsingDefaultApiKey) "" else apiKeyInput,
                        onValueChange = {
                            val filteredInput = it.replace("\n", "").replace("\r", "").replace(" ", "")
                            apiKeyInput = filteredInput
                        },
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                        ),
                        visualTransformation = if (isApiKeyFocused || apiKeyInput.isEmpty()) VisualTransformation.None else ApiKeyVisualTransformation(),
                        interactionSource = apiKeyInteractionSource
                )
            }
            ModelNameTagEditor(
                models = modelNamesInput,
                canAddManually = true,
                canFetchFromUpstream = true,
                isFetchingFromUpstream = isLoadingModels,
                onAddModels = ::addModels,
                onFetchFromUpstream = ::requestAvailableModels,
                onDeleteModel = ::requestModelDeletion,
                onReorderModels = { orderedModels ->
                    modelNamesInput = orderedModels
                    modelBindingReplacementName = null
                },
                onClearModels = ::requestClearModels,
                showNotification = showNotification
            )

            SettingsSwitchRow(
                title = stringResource(R.string.enable_direct_image_processing),
                subtitle = stringResource(R.string.enable_direct_image_processing_desc),
                checked = enableDirectImageProcessingInput,
                onCheckedChange = { enableDirectImageProcessingInput = it }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.enable_direct_audio_processing),
                subtitle = stringResource(R.string.enable_direct_audio_processing_desc),
                checked = enableDirectAudioProcessingInput,
                onCheckedChange = { enableDirectAudioProcessingInput = it }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.enable_direct_video_processing),
                subtitle = stringResource(R.string.enable_direct_video_processing_desc),
                checked = enableDirectVideoProcessingInput,
                onCheckedChange = { enableDirectVideoProcessingInput = it }
            )
            
            // Google Search Grounding 开关 (仅Gemini支持)
            if (selectedApiProvider == ApiProviderType.GOOGLE ||
                selectedApiProvider == ApiProviderType.GEMINI_GENERIC) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_google_search),
                        subtitle = stringResource(R.string.enable_google_search_desc),
                            checked = enableGoogleSearchInput,
                            onCheckedChange = { enableGoogleSearchInput = it }
                    )
            }

            // Claude 1小时提示缓存开关 (仅Claude支持)
            if (selectedApiProvider == ApiProviderType.ANTHROPIC ||
                selectedApiProvider == ApiProviderType.ANTHROPIC_GENERIC) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_claude_1h_prompt_cache),
                        subtitle = stringResource(R.string.enable_claude_1h_prompt_cache_desc),
                        checked = enableClaude1hPromptCacheInput,
                        onCheckedChange = { enableClaude1hPromptCacheInput = it }
                    )
            }
            
            // Tool Call 开关
            SettingsSwitchRow(
                title = stringResource(R.string.enable_tool_call),
                subtitle = stringResource(R.string.enable_tool_call_desc),
                checked = enableToolCallInput,
                onCheckedChange = { enableToolCallInput = it }
            )

        }
    }

    if (showModelsDialog) {
        UpstreamModelPickerSheet(
            models = modelsList,
            existingModels = modelNamesInput.toSet(),
            isRefreshing = isLoadingModels,
            loadError = modelLoadError,
            onRefresh = ::refreshAvailableModels,
            onApplySelection = { selectedModels ->
                showModelsDialog = false
                requestUpstreamModelSelection(selectedModels)
            },
            onDismissRequest = { showModelsDialog = false }
        )
    }

    pendingUpstreamModelSelection?.let { pending ->
        val replacementModelName = pending.change.nextModels.first()
        AlertDialog(
            onDismissRequest = { pendingUpstreamModelSelection = null },
            title = { Text(stringResource(R.string.model_upstream_bound_change_title)) },
            text = {
                Text(
                    text =
                        stringResource(
                            R.string.model_upstream_bound_change_message,
                            pending.change.removedModels.size,
                            pending.impact.functionTypes.size,
                            pending.impact.characterCards.size,
                            replacementModelName
                        )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingUpstreamModelSelection = null
                        scope.launch {
                            applyUpstreamModelSelection(
                                change = pending.change,
                                impact = pending.impact
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.model_upstream_apply_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpstreamModelSelection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingModelDeletion?.let { pending ->
        val replacementModelName = pending.nextModels.first()
        AlertDialog(
            onDismissRequest = { pendingModelDeletion = null },
            title = { Text(stringResource(R.string.model_delete_bound_title)) },
            text = {
                Text(
                    text =
                        stringResource(
                            R.string.model_delete_bound_message,
                            pending.modelName,
                            pending.impact.functionTypes.size,
                            pending.impact.characterCards.size,
                            replacementModelName
                        )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingModelDeletion = null
                        scope.launch {
                            applyModelDeletion(
                                modelName = pending.modelName,
                                impact = pending.impact,
                                nextModels = pending.nextModels
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModelDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    modelClearDialogState?.let { clearDialogState ->
        AlertDialog(
            onDismissRequest = {
                if (clearDialogState !is ModelClearDialogState.Checking) {
                    modelClearDialogState = null
                }
            },
            title = {
                Text(
                    stringResource(
                        if (clearDialogState is ModelClearDialogState.Blocked) {
                            R.string.model_clear_bound_title
                        } else {
                            R.string.model_clear_all_title
                        }
                    )
                )
            },
            text = {
                when (clearDialogState) {
                    ModelClearDialogState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.model_clear_checking))
                        }
                    }

                    is ModelClearDialogState.Confirm -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.model_clear_all_message,
                                    clearDialogState.modelCount
                                )
                        )
                    }

                    is ModelClearDialogState.Blocked -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.model_clear_bound_blocked,
                                    clearDialogState.bindingCount
                                )
                        )
                    }
                }
            },
            confirmButton = {
                when (clearDialogState) {
                    ModelClearDialogState.Checking -> Unit

                    is ModelClearDialogState.Confirm -> {
                        Button(
                            onClick = {
                                modelClearDialogState = null
                                scope.launch { clearModelsWithUndo() }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                        ) {
                            Text(stringResource(R.string.model_clear_all_action))
                        }
                    }

                    is ModelClearDialogState.Blocked -> {
                        TextButton(onClick = { modelClearDialogState = null }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            },
            dismissButton = {
                if (clearDialogState is ModelClearDialogState.Confirm) {
                    TextButton(onClick = { modelClearDialogState = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

private fun getBuiltInProviderDisplayName(provider: ApiProviderType, resources: Resources): String {
    return when (ModelApiProviderPresentationPolicy.canonicalProvider(provider)) {
        ApiProviderType.OPENAI -> resources.getString(R.string.provider_openai)
        ApiProviderType.ANTHROPIC -> resources.getString(R.string.provider_anthropic)
        ApiProviderType.XAI -> resources.getString(R.string.provider_xai)
        ApiProviderType.GOOGLE -> resources.getString(R.string.provider_google)
        ApiProviderType.BAIDU -> resources.getString(R.string.provider_baidu)
        ApiProviderType.ALIYUN -> resources.getString(R.string.provider_aliyun)
        ApiProviderType.XUNFEI -> resources.getString(R.string.provider_xunfei)
        ApiProviderType.ZHIPU -> resources.getString(R.string.provider_zhipu)
        ApiProviderType.BAICHUAN -> resources.getString(R.string.provider_baichuan)
        ApiProviderType.MOONSHOT -> resources.getString(R.string.provider_moonshot)
        ApiProviderType.MIMO -> resources.getString(R.string.provider_mimo)
        ApiProviderType.DEEPSEEK -> resources.getString(R.string.provider_deepseek)
        ApiProviderType.MISTRAL -> resources.getString(R.string.provider_mistral)
        ApiProviderType.SILICONFLOW -> resources.getString(R.string.provider_siliconflow)
        ApiProviderType.IFLOW -> resources.getString(R.string.provider_iflow)
        ApiProviderType.OPENROUTER -> resources.getString(R.string.provider_openrouter)
        ApiProviderType.FOUR_ROUTER -> resources.getString(R.string.provider_4router)
        ApiProviderType.NOUS_PORTAL -> resources.getString(R.string.provider_nous_portal)
        ApiProviderType.INFINIAI -> resources.getString(R.string.provider_infiniai)
        ApiProviderType.ALIPAY_BAILING -> resources.getString(R.string.provider_alipay_bailing)
        ApiProviderType.DOUBAO -> resources.getString(R.string.provider_doubao)
        ApiProviderType.NVIDIA -> resources.getString(R.string.provider_nvidia)
        ApiProviderType.LMSTUDIO -> resources.getString(R.string.provider_lmstudio)
        ApiProviderType.OLLAMA -> resources.getString(R.string.provider_ollama)
        ApiProviderType.OPENAI_LOCAL -> resources.getString(R.string.provider_openai_local)
        ApiProviderType.MNN -> resources.getString(R.string.provider_mnn)
        ApiProviderType.LLAMA_CPP -> resources.getString(R.string.provider_llama_cpp)
        ApiProviderType.PPINFRA -> resources.getString(R.string.provider_ppinfra)
        ApiProviderType.NOVITA -> resources.getString(R.string.provider_novita)
        ApiProviderType.OTHER -> resources.getString(R.string.provider_other)
        else -> resources.getString(R.string.provider_other)
    }
}

private fun getProviderDisplayName(providerTypeId: String, resources: Resources): String {
    val builtInProvider = ApiProviderType.fromProviderTypeId(providerTypeId)
    if (builtInProvider != null) {
        return getBuiltInProviderDisplayName(builtInProvider, resources)
    }
    return ToolPkgAiProviderRegistry.get(providerTypeId)?.displayName ?: providerTypeId
}

private fun getProviderSummary(providerTypeId: String, resources: Resources): String {
    val builtInProvider = ApiProviderType.fromProviderTypeId(providerTypeId)
    if (builtInProvider != null) {
        return getBuiltInProviderSummary(
            ModelApiProviderPresentationPolicy.canonicalProvider(builtInProvider),
            resources,
        )
    }
    val toolPkgProvider = ToolPkgAiProviderRegistry.get(providerTypeId)
    return toolPkgProvider?.description?.trim()?.takeIf(String::isNotEmpty)
        ?: resources.getString(
            R.string.provider_summary_toolpkg,
            providerTypeId,
        )
}

private fun getProviderSelectionOptions(resources: Resources): List<ProviderSelectionOption> {
    val builtInProviders =
        ModelApiProviderPresentationPolicy
            .orderBuiltInProviders(ApiProviderType.entries)
            .map { provider ->
            ProviderSelectionOption(
                id = provider.name,
                displayName = getBuiltInProviderDisplayName(provider, resources),
                summary = getBuiltInProviderSummary(provider, resources),
                section = ModelApiProviderPresentationPolicy.section(provider),
                endpointKind =
                    ModelApiProviderPresentationPolicy.endpointKind(provider),
            )
        }
    val toolPkgProviders =
        ToolPkgAiProviderRegistry.list().map { provider ->
            ProviderSelectionOption(
                id = provider.providerId,
                displayName = provider.displayName,
                summary =
                    provider.description.trim().ifEmpty {
                        resources.getString(
                            R.string.provider_summary_toolpkg,
                            provider.providerId,
                        )
                    },
                section = ProviderSelectionSection.TOOLPKG,
                endpointKind = ProviderEndpointKind.OTHER,
            )
        }
    return builtInProviders + toolPkgProviders
}

private fun getBuiltInProviderSummary(
    provider: ApiProviderType,
    resources: Resources,
): String {
    val canonicalProvider = ModelApiProviderPresentationPolicy.canonicalProvider(provider)
    return ModelApiProviderPresentationPolicy
        .protocolOptions(canonicalProvider)
        .joinToString(separator = " · ") { option ->
            getProtocolDisplayName(
                protocol = option.protocol,
                provider = canonicalProvider,
                resources = resources,
            )
        }
}

private fun getProviderSectionDisplayName(
    section: ProviderSelectionSection,
    resources: Resources,
): String =
    when (section) {
        ProviderSelectionSection.DOMESTIC ->
            resources.getString(R.string.provider_section_domestic)

        ProviderSelectionSection.INTERNATIONAL ->
            resources.getString(R.string.provider_section_international)

        ProviderSelectionSection.LOCAL_AND_CUSTOM ->
            resources.getString(R.string.provider_section_local_and_custom)

        ProviderSelectionSection.TOOLPKG ->
            resources.getString(R.string.provider_section_toolpkg)
    }


@Composable
internal fun SettingsSectionHeader(
        icon: ImageVector,
        title: String,
        subtitle: String? = null
) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SettingsInfoBanner(text: String) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
internal fun SettingsTextField(
    title: String,
    subtitle: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null,
    valueFilter: ((String) -> String)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    unitText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val inputEnabled = enabled && !readOnly

    Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = backgroundColor,
                        tonalElevation = 0.dp
                ) {
                    BasicTextField(
                            value = value,
                            onValueChange = { newValue ->
                                if (!inputEnabled) return@BasicTextField
                                val filtered = valueFilter?.invoke(newValue) ?: newValue
                                onValueChange(filtered)
                            },
                            singleLine = singleLine,
                            enabled = inputEnabled,
                            keyboardOptions = keyboardOptions,
                            keyboardActions = keyboardActions,
                            visualTransformation = visualTransformation,
                            interactionSource = resolvedInteractionSource,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnImeFocus(),
                            textStyle =
                                    TextStyle(
                                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                    ),
                            decorationBox = { innerTextField ->
                                Row(
                                        modifier =
                                                Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (value.isEmpty()) {
                                            Text(
                                                    text = placeholder,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                    unitText?.let {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                    )
                }
                trailingContent?.invoke()
            }
        }
    }
}

@Composable
private fun SettingsSelectorRow(
        title: String,
        subtitle: String,
        value: String,
        valueSummary: String? = null,
        onClick: () -> Unit
) {
    val semanticDescription =
        listOfNotNull(
            title,
            value,
            valueSummary,
            subtitle.takeIf { valueSummary == null },
        ).joinToString(". ")
    Surface(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .semantics(mergeDescendants = true) {
                        contentDescription = semanticDescription
                    }
                    .clickable { onClick() },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (valueSummary == null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                                .padding(end = 8.dp)
                                .weight(0.5f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Text(
                        text = valueSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean = true
) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun MnnSettingsBlock(
        mnnForwardTypeInput: Int,
        onForwardTypeSelected: (Int) -> Unit,
        mnnThreadCountInput: String,
        onThreadCountChange: (String) -> Unit,
        navigateToMnnModelDownload: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsInfoBanner(text = stringResource(R.string.mnn_local_model_tip))

        navigateToMnnModelDownload?.let { navigate ->
            Button(
                    onClick = navigate,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
            ) {
                Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.mnn_model_download))
            }
        }

        var showForwardTypeDialog by remember { mutableStateOf(false) }

        SettingsSelectorRow(
                title = stringResource(R.string.mnn_forward_type),
                subtitle = stringResource(R.string.select),
                value = forwardTypeName(mnnForwardTypeInput),
                onClick = { showForwardTypeDialog = true }
        )

        if (showForwardTypeDialog) {
            Dialog(onDismissRequest = { showForwardTypeDialog = false }) {
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                text = stringResource(R.string.mnn_forward_type),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                        )
                        listOf(
                                0 to "CPU",
                                3 to "OpenCL",
                                4 to "Auto",
                                6 to "OpenGL",
                                7 to "Vulkan"
                        ).forEach { (type, name) ->
                            Surface(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                onForwardTypeSelected(type)
                                                showForwardTypeDialog = false
                                            },
                                    shape = RoundedCornerShape(8.dp),
                                    color =
                                            if (mnnForwardTypeInput == type)
                                                    MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                        text = name,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsTextField(
                title = stringResource(R.string.mnn_thread_count),
                value = mnnThreadCountInput,
                onValueChange = onThreadCountChange,
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )
    }
}

@Composable
private fun LlamaSettingsBlock(
        llamaThreadCountInput: String,
        onThreadCountChange: (String) -> Unit,
        llamaContextSizeInput: String,
        onContextSizeChange: (String) -> Unit,
        llamaGpuLayersInput: String,
        onGpuLayersChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsInfoBanner(text = stringResource(R.string.llama_local_model_tip))

        SettingsInfoBanner(
            text =
                stringResource(R.string.llama_local_model_download_tip) +
                    "\n" +
                    stringResource(
                        R.string.llama_local_model_dir,
                        LlamaProvider.getModelsDir().absolutePath
                    )
        )

        SettingsTextField(
                title = stringResource(R.string.llama_thread_count),
                value = llamaThreadCountInput,
                onValueChange = onThreadCountChange,
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )

        SettingsTextField(
                title = stringResource(R.string.llama_context_size),
                subtitle = stringResource(R.string.llama_context_size_subtitle),
                value = llamaContextSizeInput,
                onValueChange = onContextSizeChange,
                placeholder = "2048",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )

        SettingsTextField(
                title = stringResource(R.string.llama_gpu_layers),
                subtitle = stringResource(R.string.llama_gpu_layers_subtitle),
                value = llamaGpuLayersInput,
                onValueChange = onGpuLayersChange,
                placeholder = "0",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )

        SettingsInfoBanner(text = stringResource(R.string.llama_auto_config_tip))
    }
}

private fun forwardTypeName(type: Int): String {
    return when (type) {
        0 -> "CPU"
        3 -> "OpenCL"
        4 -> "Auto"
        6 -> "OpenGL"
        7 -> "Vulkan"
        else -> "CPU"
    }
}

private fun getProtocolDisplayName(
    protocol: ApiProtocol,
    provider: ApiProviderType?,
    resources: Resources,
): String {
    return when (protocol) {
        ApiProtocol.OPENAI_CHAT_COMPLETIONS ->
            resources.getString(R.string.api_protocol_openai_chat)
        ApiProtocol.OPENAI_RESPONSES ->
            resources.getString(R.string.api_protocol_openai_responses)
        ApiProtocol.ANTHROPIC_MESSAGES ->
            resources.getString(R.string.api_protocol_anthropic_messages)
        ApiProtocol.PROVIDER_NATIVE -> {
            when (provider) {
                ApiProviderType.GOOGLE ->
                    resources.getString(R.string.api_protocol_gemini_native)
                ApiProviderType.MNN ->
                    resources.getString(R.string.api_protocol_mnn_native)
                ApiProviderType.LLAMA_CPP ->
                    resources.getString(R.string.api_protocol_llama_cpp_native)
                else -> resources.getString(R.string.api_protocol_provider_native)
            }
        }
    }
}

private fun getProtocolSummary(protocol: ApiProtocol, resources: Resources): String {
    return when (protocol) {
        ApiProtocol.OPENAI_CHAT_COMPLETIONS ->
            resources.getString(R.string.api_protocol_openai_chat_summary)
        ApiProtocol.OPENAI_RESPONSES ->
            resources.getString(R.string.api_protocol_openai_responses_summary)
        ApiProtocol.ANTHROPIC_MESSAGES ->
            resources.getString(R.string.api_protocol_anthropic_messages_summary)
        ApiProtocol.PROVIDER_NATIVE ->
            resources.getString(R.string.api_protocol_provider_native_summary)
    }
}

private fun getProtocolDetectionSourceDisplayName(
    source: ProviderProtocolDetectionSource,
    resources: Resources,
): String {
    return when (source) {
        ProviderProtocolDetectionSource.SINGLE_SUPPORTED_PROTOCOL ->
            resources.getString(R.string.api_protocol_detection_source_single)
        ProviderProtocolDetectionSource.PROVIDER_DEFAULT ->
            resources.getString(R.string.api_protocol_detection_source_provider_default)
        ProviderProtocolDetectionSource.KNOWN_ENDPOINT ->
            resources.getString(R.string.api_protocol_detection_source_known_endpoint)
        ProviderProtocolDetectionSource.KNOWN_BASE_ENDPOINT ->
            resources.getString(R.string.api_protocol_detection_source_known_base_endpoint)
        ProviderProtocolDetectionSource.EXPLICIT_ENDPOINT_PATH ->
            resources.getString(R.string.api_protocol_detection_source_explicit_path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiProtocolSelectionSheet(
    options: List<ProviderProtocolOption>,
    selectedProtocol: ApiProtocol,
    provider: ApiProviderType,
    resources: Resources,
    detectionMessage: String?,
    onDismissRequest: () -> Unit,
    onAutoDetect: () -> Unit,
    onProtocolSelected: (ApiProtocol) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 620.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = resources.getString(R.string.select_api_protocol_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                item {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(onClick = onAutoDetect),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = resources.getString(R.string.api_protocol_auto_detect),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text =
                                    resources.getString(
                                        R.string.api_protocol_auto_detect_summary
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            detectionMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                items(options.size) { index ->
                    val option = options[index]
                    val isSelected = option.protocol == selectedProtocol
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onProtocolSelected(option.protocol) },
                        shape = RoundedCornerShape(16.dp),
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text =
                                        getProtocolDisplayName(
                                            protocol = option.protocol,
                                            provider = provider,
                                            resources = resources,
                                        ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight =
                                        if (isSelected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                )
                                Text(
                                    text = getProtocolSummary(option.protocol, resources),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiProviderSelectionSheet(
        selectedProviderTypeId: String,
        onDismissRequest: () -> Unit,
        onProviderSelected: (ProviderSelectionOption) -> Unit
) {
    val resources = LocalResources.current
    val providers = remember(resources) { getProviderSelectionOptions(resources) }
    var searchQuery by remember { mutableStateOf("") }
    
    val providerRows = remember(providers, searchQuery) {
        ModelApiProviderPresentationPolicy.buildRows(
            providers.filter { provider ->
                ModelApiProviderPresentationPolicy.matchesSearch(
                    option = provider,
                    query = searchQuery,
                )
            }
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp, max = 720.dp)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
            ) {
                // 标题和搜索框
                Text(
                        stringResource(R.string.select_api_provider_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 搜索框
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_providers), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clear),
                                            modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                )

                // 提供商列表
                androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f)
                ) {
                    items(providerRows.size) { index ->
                        when (val row = providerRows[index]) {
                            is ProviderSelectionRow.Header ->
                                Text(
                                    text =
                                        getProviderSectionDisplayName(
                                            section = row.section,
                                            resources = resources,
                                        ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 4.dp,
                                                end = 4.dp,
                                                top = 12.dp,
                                                bottom = 4.dp,
                                            ),
                                )

                            is ProviderSelectionRow.Option -> {
                                val provider = row.provider
                                val isSelected = provider.id == selectedProviderTypeId
                                Surface(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription =
                                                        "${provider.displayName}. ${provider.summary}"
                                                }
                                                .clickable { onProviderSelected(provider) },
                                        shape = RoundedCornerShape(8.dp),
                                        color =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            }
                                ) {
                                    Row(
                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                                modifier = Modifier
                                                        .size(32.dp)
                                                        .background(
                                                                getProviderColor(provider.id),
                                                                CircleShape
                                                        ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                    text = provider.displayName.firstOrNull()?.toString() ?: "?",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                    text = provider.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = provider.summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
            }
    }
}

// 为不同提供商生成不同的颜色
@Composable
private fun getProviderColor(providerTypeId: String): androidx.compose.ui.graphics.Color {
    val provider = ApiProviderType.fromProviderTypeId(providerTypeId)
    if (provider == null) {
        val palette = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
        val paletteIndex = kotlin.math.abs(providerTypeId.lowercase().hashCode()) % palette.size
        return palette[paletteIndex]
    }
    return when (ModelApiProviderPresentationPolicy.canonicalProvider(provider)) {
        ApiProviderType.OPENAI -> MaterialTheme.colorScheme.primary
        ApiProviderType.ANTHROPIC -> MaterialTheme.colorScheme.tertiary
        ApiProviderType.XAI -> MaterialTheme.colorScheme.onSurface
        ApiProviderType.GOOGLE -> MaterialTheme.colorScheme.secondary
        ApiProviderType.BAIDU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ApiProviderType.ALIYUN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        ApiProviderType.XUNFEI -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        ApiProviderType.ZHIPU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ApiProviderType.BAICHUAN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        ApiProviderType.MOONSHOT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        ApiProviderType.MIMO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.66f)
        ApiProviderType.DEEPSEEK -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ApiProviderType.MISTRAL -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
        ApiProviderType.SILICONFLOW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        ApiProviderType.IFLOW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
        ApiProviderType.OPENROUTER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        ApiProviderType.FOUR_ROUTER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.56f)
        ApiProviderType.NOUS_PORTAL -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.52f)
        ApiProviderType.INFINIAI -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ApiProviderType.ALIPAY_BAILING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        ApiProviderType.DOUBAO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        ApiProviderType.NVIDIA -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        ApiProviderType.LMSTUDIO -> MaterialTheme.colorScheme.tertiary
        ApiProviderType.OLLAMA -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
        ApiProviderType.OPENAI_LOCAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        ApiProviderType.MNN -> MaterialTheme.colorScheme.secondary
        ApiProviderType.LLAMA_CPP -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        ApiProviderType.PPINFRA -> MaterialTheme.colorScheme.primaryContainer
        ApiProviderType.NOVITA -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
        ApiProviderType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}
