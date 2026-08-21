package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** API提供商类型枚举 */
@Serializable
enum class ApiProviderType {
        OPENAI, // OpenAI (GPT系列)
        OPENAI_RESPONSES, // OpenAI Responses API
        OPENAI_RESPONSES_GENERIC, // OpenAI Responses通用（自定义端点）
        OPENAI_GENERIC, // OpenAI通用（自定义端点）
        ANTHROPIC, // Anthropic (Claude系列)
        ANTHROPIC_GENERIC, // Anthropic通用（自定义端点）
        XAI, // xAI（Grok系列）
        GOOGLE, // Google (Gemini系列)
        GEMINI_GENERIC, // Gemini通用（自定义端点）
        BAIDU, // 百度 (文心一言系列)
        ALIYUN, // 阿里云 (通义千问系列)
        XUNFEI, // 讯飞 (星火认知系列)
        ZHIPU, // 智谱AI (ChatGLM系列)
        BAICHUAN, // 百川大模型
        MOONSHOT, // 月之暗面大模型
        MIMO, // Xiaomi MiMo
        DEEPSEEK, // Deepseek大模型
        MISTRAL, // Mistral AI (Codestral等)
        SILICONFLOW, // 硅基流动
        IFLOW, // iFlow
        OPENROUTER, // OpenRouter (多模型聚合)
        FOUR_ROUTER, // 4Router
        NOUS_PORTAL, // Nous Portal / Inference API
        INFINIAI, // 无问芯穹
        ALIPAY_BAILING, // 支付宝百灵大模型
        DOUBAO, // 豆包（火山模型）
        NVIDIA, // NVIDIA API Catalog / NIM
        LMSTUDIO, // LM Studio本地模型服务
        OLLAMA, // Ollama 本地/私有部署服务（OpenAI兼容）
        OPENAI_LOCAL, // OpenAI兼容本地模型服务
        MNN, // MNN本地推理引擎
        LLAMA_CPP, // llama.cpp 本地推理引擎
        PPINFRA, // 派欧云
        NOVITA, // Novita AI
        OTHER; // 其他提供商（自定义端点）

        companion object {
                fun fromProviderTypeId(providerTypeId: String): ApiProviderType? {
                        val normalized = providerTypeId.trim()
                        if (normalized.isEmpty()) {
                                return null
                        }
                        return values().firstOrNull {
                                it.name.equals(normalized, ignoreCase = true)
                        }
                }
        }
}

/**
 * API 线协议类型。
 *
 * 供应商身份仍由 [ApiProviderType] 和 [ModelConfigData.apiProviderTypeId] 保留，
 * 协议不再依赖用户可见的重复供应商枚举项。
 */
@Serializable
enum class ApiProtocol {
        OPENAI_CHAT_COMPLETIONS,
        OPENAI_RESPONSES,
        ANTHROPIC_MESSAGES,
        PROVIDER_NATIVE;

        companion object {
                fun fromProviderType(providerType: ApiProviderType): ApiProtocol {
                        return when (providerType) {
                                ApiProviderType.OPENAI_RESPONSES,
                                ApiProviderType.OPENAI_RESPONSES_GENERIC -> OPENAI_RESPONSES
                                ApiProviderType.ANTHROPIC,
                                ApiProviderType.ANTHROPIC_GENERIC -> ANTHROPIC_MESSAGES
                                ApiProviderType.GOOGLE,
                                ApiProviderType.GEMINI_GENERIC,
                                ApiProviderType.MNN,
                                ApiProviderType.LLAMA_CPP -> PROVIDER_NATIVE
                                else -> OPENAI_CHAT_COMPLETIONS
                        }
                }
        }
}

object ModelConfigDefaults {
        const val DEFAULT_CONTEXT_LENGTH = 64.0f
        const val DEFAULT_MAX_CONTEXT_LENGTH = 200.0f
        const val DEFAULT_ENABLE_MAX_CONTEXT_MODE = false
        const val DEFAULT_ENABLE_TOOL_CALL = true
        const val DEFAULT_SUMMARY_TOKEN_THRESHOLD = 0.70f
        const val DEFAULT_ENABLE_SUMMARY = true
        const val DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT = true
        const val DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD = 16
}

/** 表示完整的模型配置，包括API设置和模型参数 */
@Serializable
data class ModelConfigData(
        val id: String,
        val name: String,

        // API设置
        val apiKey: String = "",
        val apiEndpoint: String = "",
        val modelName: String = "",
        val apiProviderType: ApiProviderType = ApiProviderType.DEEPSEEK,
        val apiProviderTypeId: String = apiProviderType.name,
        val apiProtocol: ApiProtocol = ApiProtocol.fromProviderType(apiProviderType),

        // 多API Key支持
        val useMultipleApiKeys: Boolean = false, // 是否启用多API Key模式
        val apiKeyPool: List<ApiKeyInfo> = emptyList(), // API Key池
        val currentKeyIndex: Int = 0, // 当前使用的Key索引
        val keyRotationMode: String = "ROUND_ROBIN", // 轮询模式: ROUND_ROBIN / RANDOM

        // 是否包含自定义参数
        val hasCustomParameters: Boolean = false,

        // 模型参数的enabled状态
        val maxTokensEnabled: Boolean = false,
        val temperatureEnabled: Boolean = false,
        val topPEnabled: Boolean = false,
        val topKEnabled: Boolean = false,
        val presencePenaltyEnabled: Boolean = false,
        val frequencyPenaltyEnabled: Boolean = false,
        val repetitionPenaltyEnabled: Boolean = false,

        // 模型参数值
        val maxTokens: Int = 4096,
        val temperature: Float = 1.0f,
        val topP: Float = 1.0f,
        val topK: Int = 0,
        val presencePenalty: Float = 0.0f,
        val frequencyPenalty: Float = 0.0f,
        val repetitionPenalty: Float = 1.0f,

        // 自定义参数JSON字符串
        val customParameters: String = "[]",

        // 自定义请求头JSON字符串
        val customHeaders: String = "{}",

        // 上下文/总结配置
        val contextLength: Float = ModelConfigDefaults.DEFAULT_CONTEXT_LENGTH,
        val maxContextLength: Float = ModelConfigDefaults.DEFAULT_MAX_CONTEXT_LENGTH,
        val enableMaxContextMode: Boolean = ModelConfigDefaults.DEFAULT_ENABLE_MAX_CONTEXT_MODE,
        val summaryTokenThreshold: Float = ModelConfigDefaults.DEFAULT_SUMMARY_TOKEN_THRESHOLD,
        val enableSummary: Boolean = ModelConfigDefaults.DEFAULT_ENABLE_SUMMARY,
        val enableSummaryByMessageCount: Boolean =
                ModelConfigDefaults.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT,
        val summaryMessageCountThreshold: Int =
                ModelConfigDefaults.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD,
        // 自定义总结规则
        val summaryCustomRules: String = "",

        // MNN特定配置
        // 注意：MNN模型路径会根据modelName自动构建，不需要单独存储
        val mnnForwardType: Int = 0, // 前向计算类型 (CPU/GPU等)
        val mnnThreadCount: Int = 4, // 推理线程数

        // llama.cpp 特定配置
        val llamaThreadCount: Int = 4, // 推理线程数
        val llamaContextSize: Int = 2048, // n_ctx
        val llamaBatchSize: Int = 512, // n_batch
        val llamaUBatchSize: Int = 512, // n_ubatch
        val llamaGpuLayers: Int = 0, // n_gpu_layers
        val llamaUseMmap: Boolean = false, // Android上默认关闭，减少mmap导致的兼容性问题
        val llamaFlashAttention: Boolean = false, // Android上默认关闭，更接近PocketPal安全值
        val llamaKvUnified: Boolean = true, // 单并发聊天默认开启统一KV缓存
        val llamaOffloadKqv: Boolean = false, // 仅在启用GPU层时有意义

        // 图片处理配置
        val enableDirectImageProcessing: Boolean = false, // 是否启用直接图片处理

        // 音频/视频处理配置
        val enableDirectAudioProcessing: Boolean = false, // 是否启用直接音频处理
        val enableDirectVideoProcessing: Boolean = false, // 是否启用直接视频处理

        // Gemini特定配置
        val enableGoogleSearch: Boolean = false, // 是否启用Google Search Grounding (仅Gemini支持)

        // Claude特定配置
        val enableClaude1hPromptCache: Boolean = false, // 是否启用1小时提示缓存TTL (仅Claude支持)

        // Tool Call配置
        val enableToolCall: Boolean = ModelConfigDefaults.DEFAULT_ENABLE_TOOL_CALL, // 是否启用Tool Call接口调用工具（使用模型原生工具调用而非XML格式）

        // 请求频率限制配置
        val requestLimitPerMinute: Int = 0, // 每分钟最大请求次数，0表示不限流
        val maxConcurrentRequests: Int = 0 // 最大并发请求数，0表示不限制
)

/** 简化版的模型配置数据，用于列表显示 */
@Serializable
data class ModelConfigSummary(
        val id: String,
        val name: String,
        val modelName: String = "",
        val apiEndpoint: String = "",
        val apiProviderType: ApiProviderType = ApiProviderType.DEEPSEEK,
        val apiProtocol: ApiProtocol = ApiProtocol.fromProviderType(apiProviderType),
        val modelIndex: Int = 0 // 当modelName包含多个模型（逗号分隔）时，选择第几个模型（从0开始）
)

/**
 * 解析旧配置中没有独立协议字段时仍可确定的协议信息。
 *
 * 只有原始 JSON 缺失 apiProtocol 时才执行推导；已经持久化的显式协议必须原样保留。
 * Novita 的旧配置只有一个 provider ID，但端点已经明确表达了 Anthropic 路径。
 */
fun Json.decodeModelConfigDataWithLegacyProtocol(rawConfig: String): ModelConfigData {
    val persistedFields = parseToJsonElement(rawConfig).jsonObject
    val decoded = decodeFromString<ModelConfigData>(rawConfig)
    if ("apiProtocol" in persistedFields) {
        return decoded
    }

    if (
        decoded.apiProviderType == ApiProviderType.NOVITA &&
            decoded.apiEndpoint.contains("/anthropic/", ignoreCase = true)
    ) {
        return decoded.copy(apiProtocol = ApiProtocol.ANTHROPIC_MESSAGES)
    }
    return decoded
}

private val MODEL_NAME_INPUT_SEPARATOR = Regex("[,，\\r\\n]+")

/**
 * 规范化模型名称列表。
 *
 * 模型顺序同时定义测试模型与外部索引语义，因此只移除空项和完全重复项，
 * 不做排序、大小写折叠或模型名改写。
 */
fun normalizeModelNames(modelNames: Iterable<String>): List<String> {
    val seen = LinkedHashSet<String>()
    modelNames.forEach { rawName ->
        val modelName = rawName.trim()
        if (modelName.isNotEmpty()) {
            seen.add(modelName)
        }
    }
    return seen.toList()
}

/** 解析手动粘贴内容；支持英文逗号、中文逗号和换行。 */
fun parseModelNameInput(input: String): List<String> {
    if (input.isBlank()) return emptyList()
    return normalizeModelNames(input.split(MODEL_NAME_INPUT_SEPARATOR))
}

/** 将模型名称列表写回既有逗号字符串边界。 */
fun serializeModelNames(modelNames: Iterable<String>): String {
    return normalizeModelNames(modelNames).joinToString(",")
}

/** 在不改变既有顺序的前提下追加新模型。 */
fun mergeModelNames(
    currentModels: Iterable<String>,
    addedModels: Iterable<String>
): List<String> {
    return normalizeModelNames(currentModels + addedModels)
}

data class UpstreamModelSelectionChange(
    val nextModels: List<String>,
    val addedModels: List<String>,
    val removedModels: List<String>
) {
    val hasChanges: Boolean
        get() = addedModels.isNotEmpty() || removedModels.isNotEmpty()
}

/**
 * 将上游选择应用到当前模型列表。
 *
 * 本次上游未返回的模型不属于选择范围，必须保留；仍被选中的当前模型保持原顺序，
 * 新选模型按上游顺序追加，取消选择只移除当前上游范围内的模型。
 */
fun reconcileUpstreamModelSelection(
    currentModels: Iterable<String>,
    upstreamModels: Iterable<String>,
    selectedUpstreamModels: Iterable<String>
): UpstreamModelSelectionChange {
    val normalizedCurrentModels = normalizeModelNames(currentModels)
    val normalizedUpstreamModels = normalizeModelNames(upstreamModels)
    val upstreamModelSet = normalizedUpstreamModels.toSet()
    val selectedModelSet =
        normalizeModelNames(selectedUpstreamModels)
            .filterTo(LinkedHashSet(), upstreamModelSet::contains)
    val currentModelSet = normalizedCurrentModels.toSet()
    val retainedModels =
        normalizedCurrentModels.filter { modelName ->
            modelName !in upstreamModelSet || modelName in selectedModelSet
        }
    val addedModels =
        normalizedUpstreamModels.filter { modelName ->
            modelName in selectedModelSet && modelName !in currentModelSet
        }
    val removedModels =
        normalizedCurrentModels.filter { modelName ->
            modelName in upstreamModelSet && modelName !in selectedModelSet
        }

    return UpstreamModelSelectionChange(
        nextModels = retainedModels + addedModels,
        addedModels = addedModels,
        removedModels = removedModels
    )
}

/** 将一个模型移动到目标索引，供标签排序面板使用。 */
fun moveModelName(
    modelNames: List<String>,
    fromIndex: Int,
    toIndex: Int
): List<String> {
    require(fromIndex in modelNames.indices) { "fromIndex is outside the model list" }
    require(toIndex in modelNames.indices) { "toIndex is outside the model list" }
    if (fromIndex == toIndex) return modelNames
    return modelNames.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

/**
 * 根据模型名重映射索引。
 *
 * 重排时目标模型仍在新列表中，索引直接跟随模型名移动。删除已绑定模型时，
 * 调用方必须在用户确认后显式提供 replacementModelName；本函数不会自行猜测替代模型。
 */
fun remapModelIndex(
    oldModels: List<String>,
    newModels: List<String>,
    requestedIndex: Int,
    replacementModelName: String? = null
): Int {
    require(oldModels.isNotEmpty()) { "Cannot remap an index from an empty model list" }
    val oldIndex = if (requestedIndex in oldModels.indices) requestedIndex else 0
    val selectedModelName = oldModels[oldIndex]
    val retainedIndex = newModels.indexOf(selectedModelName)
    if (retainedIndex >= 0) {
        return retainedIndex
    }

    requireNotNull(replacementModelName) {
        "Model '$selectedModelName' was removed without an explicit replacement"
    }
    val replacementIndex = newModels.indexOf(replacementModelName)
    require(replacementIndex >= 0) {
        "Replacement model '$replacementModelName' is not present in the new model list"
    }
    return replacementIndex
}

/** 从逗号分隔的模型名称字符串中根据索引获取具体模型 */
fun getModelByIndex(modelName: String, index: Int): String {
    if (modelName.isEmpty()) return ""
    val models = getModelList(modelName)
    return if (index >= 0 && index < models.size) models[index] else models.getOrNull(0) ?: ""
}

/** 获取模型列表 */
fun getModelList(modelName: String): List<String> {
    if (modelName.isEmpty()) return emptyList()
    return normalizeModelNames(modelName.split(","))
}

/** 
 * 计算有效的模型索引（处理越界情况）
 * 如果索引超出范围，自动返回0（第一个模型）
 * @param modelName 逗号分隔的模型名称字符串
 * @param requestedIndex 请求的索引
 * @return 有效的索引值（0到模型数量-1之间）
 */
fun getValidModelIndex(modelName: String, requestedIndex: Int): Int {
    val modelList = getModelList(modelName)
    return if (requestedIndex >= 0 && requestedIndex < modelList.size) {
        requestedIndex
    } else {
        0 // 索引越界时使用第一个
    }
}
