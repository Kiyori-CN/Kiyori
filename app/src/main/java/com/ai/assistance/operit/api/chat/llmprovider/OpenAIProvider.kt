package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryProjector
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiProtocol
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ProviderExecutionEntity
import com.ai.assistance.operit.data.model.ProviderExecutionStatus
import com.ai.assistance.operit.data.model.ProviderTransportKind
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.dao.ProviderEventAppendOutcome
import com.ai.assistance.operit.data.audit.ConversationAuditProviderAttemptRecorder
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.ai.assistance.operit.data.repository.ProviderExecutionRepository
import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.StreamingJsonXmlConverter
import com.ai.assistance.operit.util.TokenCacheManager
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.MessageFailureDiagnosticSource
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.withEventChannel
import com.ai.assistance.operit.util.stream.stream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser

internal object OpenAIChatRequestFeatureCompiler {
    fun apply(
        requestJson: JSONObject,
        compiledRequest: CompiledModelRequest,
    ) {
        val effort = compiledRequest.reasoningEffort ?: return
        requestJson.put("reasoning_effort", effort.wireValue)
    }
}

/**
 * OpenAI API格式的实现，支持标准OpenAI接口和兼容此格式的其他提供商
 *
 * ## enableToolCall 参数说明
 *
 * `enableToolCall` 用于启用/禁用 OpenAI Tool Call API 原生格式。
 *
 * ### 工作原理
 *
 * 当 `enableToolCall = true` 时，本Provider会执行双向格式转换：
 *
 * 1. **发送请求前**：将内部XML格式的工具调用转换为OpenAI Tool Call格式
 *    - `<tool name="xxx"><param name="yyy">value</param></tool>`
 *    - → `{"tool_calls": [{"function": {"name": "xxx", "arguments": "{\"yyy\": \"value\"}"}}]}`
 *
 * 2. **接收响应后**：将API返回的Tool Call格式转换回XML格式
 *    - API返回的tool_calls对象 → XML格式
 *    - 保持上层代码对XML格式的兼容性
 *
 * ### 历史记录处理
 *
 * - **Assistant消息**：XML工具调用 → OpenAI `tool_calls` 字段
 * - **User消息**：XML `tool_result` → OpenAI `role: "tool"` 消息
 * - **tool_call_id追踪**：自动生成和匹配ID，确保工具调用与结果正确关联
 *
 * ### 适用场景
 *
 * - 使用支持原生Tool Call API的模型（GPT-4、Claude、Qwen等）
 * - 需要更结构化的工具调用处理
 * - 希望利用模型的自动工具选择功能
 *
 * ### 注意事项
 *
 * - 默认值为 `false`，需要显式启用
 * - 启用后会自动添加 `tools` 和 `tool_choice` 到请求体
 * - 流式响应中也支持增量工具调用数据的处理
 *
 * @param enableToolCall 是否启用Tool Call API格式转换（默认false）
 */
open class OpenAIProvider(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    val modelName: String,
    private val client: OkHttpClient,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.OPENAI,
    private val capabilityProviderType: ApiProviderType = providerType,
    private val endpointProtocol: ApiProtocol = ApiProtocol.fromProviderType(providerType),
    private val modelListProtocol: ApiProtocol = ApiProtocol.PROVIDER_NATIVE,
    protected val supportsVision: Boolean = false, // 是否支持图片处理
    protected val supportsAudio: Boolean = false, // 是否支持音频输入
    protected val supportsVideo: Boolean = false, // 是否支持视频输入
    val enableToolCall: Boolean = false // 是否启用Tool Call接口
) : AIService, ProviderUsageReporting {
    // private val client: OkHttpClient = HttpClientFactory.instance

    protected val JSON = "application/json".toMediaType()

    private val streamSessionGate = ProviderStreamSessionGate()

    /**
     * 由客户端错误（如4xx状态码）触发的API异常，是否重试由统一策略决定
     */
    class NonRetriableException(
        message: String,
        override val statusCode: Int,
        cause: Throwable? = null
    ) : IOException(message, cause), HttpStatusCodeException

    private class OpenAiHttpResponseException(
        message: String,
        override val statusCode: Int,
    ) : IOException(message), HttpStatusCodeException

    // Token缓存管理器
    val tokenCacheManager = TokenCacheManager()

    @Volatile
    private var latestProviderUsageSnapshot: ProviderUsageSnapshot? = null

    @Synchronized
    override fun consumeLatestProviderUsageSnapshot(): ProviderUsageSnapshot? {
        val snapshot = latestProviderUsageSnapshot
        latestProviderUsageSnapshot = null
        return snapshot
    }

    protected open val useResponsesApi: Boolean = false
    protected open val supportsResponsesStreamResumption: Boolean = false
    /** 仅为 JVM 故障注入提供确定的执行边界；生产实现始终使用 IO dispatcher。 */
    protected open val responseExecutionDispatcher: CoroutineDispatcher = Dispatchers.IO
    protected val modelCapabilityProfile: ModelCapabilityProfile by lazy {
        ModelCapabilityResolver.resolve(
            providerType = capabilityProviderType,
            modelName = modelName,
            apiEndpoint = apiEndpoint,
            providerIdentityType = providerType,
        )
    }
    private val usesAtMostOnceResponsesSubmission: Boolean
        get() =
            useResponsesApi &&
                modelCapabilityProfile.executionPersistence ==
                    ExecutionPersistenceCapability.RESPONSES_AT_MOST_ONCE

    /**
     * 生产环境始终使用唯一的 ProviderExecutionRepository。
     *
     * 保持这个创建边界可覆写，是为了让 JVM 故障注入直接执行真实 Responses 协调器，同时
     * 精确观察它发出的持久化状态变更；测试实现不能进入应用运行时。
     */
    internal open fun createResponsesExecutionPersistence(
        context: Context,
    ): OpenAIResponsesExecutionPersistence =
        RepositoryOpenAIResponsesExecutionPersistence(
            ProviderExecutionRepository.from(context)
        )

    // 公开token计数
    override val inputTokenCount: Int
        get() = tokenCacheManager.totalInputTokenCount
    override val outputTokenCount: Int
        get() = tokenCacheManager.outputTokenCount
    override val cachedInputTokenCount: Int
        get() = tokenCacheManager.cachedInputTokenCount

    // 供应商:模型标识符
    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    private suspend fun applyUsageToCounters(
        usage: JSONObject?,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit
    ) {
        val parsed = OpenAIResponsesPayloadAdapter.parseUsageCounts(usage) ?: return
        tokenCacheManager.updateActualTokens(parsed.actualInputTokens, parsed.cachedInputTokens)
        tokenCacheManager.setOutputTokens(parsed.outputTokens)
        latestProviderUsageSnapshot =
            ProviderUsageSnapshot(
                providerModel = providerModel,
                protocol = endpointProtocol,
                totalInputTokens = parsed.totalInputTokens.toLong(),
                uncachedInputTokens = parsed.actualInputTokens.toLong(),
                cacheReadTokens = parsed.cachedInputTokens.toLong(),
                cacheWriteTokens = parsed.cacheWriteTokens.toLong(),
                outputTokens = parsed.outputTokens.toLong(),
                reasoningTokens = parsed.reasoningTokens.toLong(),
                cacheMetricState = parsed.cacheMetricState,
                source = ProviderUsageSource.PROVIDER,
            )
        onTokensUpdated(
            parsed.totalInputTokens,
            parsed.cachedInputTokens,
            tokenCacheManager.outputTokenCount
        )
    }

    private fun buildOpenAiErrorDetail(error: JSONObject, fallback: String): String {
        val message = error.optString("message", "").trim().ifEmpty { fallback }
        val type = error.optString("type", "").trim()
        val code = error.opt("code")?.toString()?.trim().orEmpty()

        if (type.isEmpty() && code.isEmpty()) {
            return message
        }

        return buildString {
            append(message)
            append(" [")
            if (type.isNotEmpty()) {
                append("type=").append(type)
            }
            if (type.isNotEmpty() && code.isNotEmpty()) {
                append(", ")
            }
            if (code.isNotEmpty()) {
                append("code=").append(code)
            }
            append("]")
        }
    }

    private fun throwIfOpenAiErrorPayload(context: Context, jsonResponse: JSONObject) {
        val error = jsonResponse.optJSONObject("error") ?: return
        val errorSummary =
            LlmLogPrivacy.summarizeProviderError(
                JSONObject().put("error", error).toString()
            )
        val detail = errorSummary.exceptionDetail()
        val exceptionMessage = context.getString(R.string.openai_error_response_failed, detail)

        AppLogger.e(
            "AIService",
            "【发送消息】响应中包含错误对象: ${errorSummary.format()}"
        )
        if (useResponsesApi) {
            throw OpenAIResponsesTerminalException(
                eventType = jsonResponse.optString("type", "error"),
                message = exceptionMessage,
            )
        }
        throw IOException(exceptionMessage)
    }

    // 重置token计数
    override fun resetTokenCounts() {
        tokenCacheManager.resetTokenCounts()
    }

    protected open fun customizeFinalRequestObject(
        requestObject: JSONObject,
        messagesArray: JSONArray,
        toolsJson: String?
    ) {
    }

    protected open fun applyAuthenticationHeaders(
        builder: Request.Builder,
        currentApiKey: String
    ) {
        if (currentApiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $currentApiKey")
        }
    }

     override fun cancelStreaming() {
         streamSessionGate.cancelActive()
     }

     override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
         val apiKey = apiKeyProvider.getApiKey()
         return if (modelListProtocol == ApiProtocol.PROVIDER_NATIVE) {
             ModelListFetcher.getModelsList(
                 context = context,
                 apiKey = apiKey,
                 apiEndpoint = apiEndpoint,
                 apiProviderType = providerType,
             )
         } else {
             ModelListFetcher.getModelsList(
                 context = context,
                 apiKey = apiKey,
                 apiEndpoint = apiEndpoint,
                 apiProviderType = providerType,
                 apiProtocol = modelListProtocol,
             )
         }
     }

     override suspend fun testConnection(context: Context): Result<String> {
         return try {
             val testHistory =
                 listOf(
                     PromptTurn(kind = PromptTurnKind.SYSTEM, content = "You are a helpful assistant."),
                     PromptTurn(kind = PromptTurnKind.USER, content = "Hi")
                 )
             val stream =
                 sendMessage(
                     context,
                     testHistory,
                     emptyList(),
                     false,
                     onTokensUpdated = { _, _, _ -> },
                     onNonFatalError = {},
                     enableRetry = false
                 )

             stream.collect { _ -> }
             Result.success(context.getString(R.string.openai_connection_success))
         } catch (e: Exception) {
             AppLogger.e("AIService", "连接测试失败", e)
             Result.failure(IOException(context.getString(R.string.openai_connection_test_failed, e.message ?: ""), e))
         }
     }

    protected fun logFinalOutput(tag: String, content: CharSequence, prefix: String = "Final output: ") {
        AppLogger.d(
            tag,
            "${prefix.trimEnd()} ${LlmLogPrivacy.summarizeText(content).format()}"
        )
    }

    private fun getOutputImagesDir(): File {
        return OperitPaths.outputImagesDir()
    }

    private fun fileExtensionForImageMime(mimeType: String): String {
        return when (mimeType.lowercase().substringBefore(';')) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "png"
        }
    }

    private fun outputMimeTypeFromFormat(format: String?): String {
        return when (format?.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/png"
        }
    }

    private fun writeOutputImage(bytes: ByteArray, mimeType: String, prefix: String): Uri? {
        return try {
            val dir = getOutputImagesDir()
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val ext = fileExtensionForImageMime(mimeType)
            val fileName = "${prefix}_${System.currentTimeMillis()}.$ext"
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            AppLogger.e("AIService", "保存输出图片失败", e)
            null
        }
    }

    private suspend fun downloadBytes(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            response.use {
                if (!it.isSuccessful) return null
                val body = it.body ?: return null
                body.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun emitImageMarkdown(emitter: StreamEmitter, imageUri: Uri, alt: String) {
        val safeAlt = alt.ifBlank { "image" }
        emitter.emitContent("\n![${safeAlt}](${imageUri})\n")
    }

    private data class ImageBufferState(
        val bytes: ByteArrayOutputStream = ByteArrayOutputStream(),
        var mimeType: String = "image/png"
    )

    private suspend fun flushImageBuffers(state: StreamingState, emitter: StreamEmitter) {
        if (state.imageBuffers.isEmpty()) return
        val pending = state.imageBuffers.toMap()
        state.imageBuffers.clear()
        pending.forEach { (index, bufferState) ->
            val bytes = bufferState.bytes.toByteArray()
            if (bytes.isNotEmpty()) {
                val uri = writeOutputImage(bytes, bufferState.mimeType, "openai_image_$index")
                if (uri != null) {
                    emitImageMarkdown(emitter, uri, "openai_image_$index")
                }
            }
        }
    }

    private suspend fun tryHandleOpenAiImageResponse(
        json: JSONObject,
        emitter: StreamEmitter,
        state: StreamingState?
    ): Boolean {
        val dataArr = json.optJSONArray("data")
        if (dataArr != null && dataArr.length() > 0) {
            for (i in 0 until dataArr.length()) {
                val obj = dataArr.optJSONObject(i) ?: continue
                val b64 = obj.optString("b64_json", "")
                val url = obj.optString("url", "")
                val mimeType = outputMimeTypeFromFormat(obj.optString("output_format", "").ifBlank { null })
                if (b64.isNotEmpty()) {
                    val bytes = try {
                        Base64.decode(b64, Base64.DEFAULT)
                    } catch (_: Exception) {
                        null
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val uri = writeOutputImage(bytes, mimeType, "openai_image_$i")
                        if (uri != null) {
                            emitImageMarkdown(emitter, uri, "openai_image_$i")
                        }
                    }
                } else if (url.isNotEmpty()) {
                    val bytes = downloadBytes(url)
                    if (bytes != null && bytes.isNotEmpty()) {
                        val uri = writeOutputImage(bytes, mimeType, "openai_image_$i")
                        if (uri != null) {
                            emitImageMarkdown(emitter, uri, "openai_image_$i")
                        }
                    }
                }
            }
            return true
        }

        val eventType = json.optString("type", "")
        if (eventType.startsWith("image_generation.")) {
            val b64 = json.optString("b64_json", "")
            val idx = json.optInt("partial_image_index", 0)
            val format = json.optString("output_format", "").ifBlank { null }
            val mimeType = outputMimeTypeFromFormat(format)
            if (state != null && b64.isNotEmpty()) {
                val decoded = try {
                    Base64.decode(b64, Base64.DEFAULT)
                } catch (_: Exception) {
                    null
                }
                if (decoded != null) {
                    val buf = state.imageBuffers.getOrPut(idx) { ImageBufferState() }
                    buf.mimeType = mimeType
                    buf.bytes.write(decoded)
                }
                if (eventType != "image_generation.partial_image") {
                    flushImageBuffers(state, emitter)
                }
            }
            return true
        }

        val outputArr = json.optJSONArray("output")
        if (outputArr != null && outputArr.length() > 0) {
            var handledAny = false
            for (i in 0 until outputArr.length()) {
                val item = outputArr.optJSONObject(i) ?: continue
                val contentArr = item.optJSONArray("content") ?: continue
                for (j in 0 until contentArr.length()) {
                    val part = contentArr.optJSONObject(j) ?: continue
                    val partType = part.optString("type", "")
                    if (partType == "output_text" || partType == "text") {
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) {
                            emitter.emitContent(text)
                            handledAny = true
                        }
                    }
                    val mimeType = part.optString("mime_type", part.optString("mimeType", "image/png"))
                    val b64 = part.optString("b64_json", part.optString("data", ""))
                    val imageUrlObj = part.optJSONObject("image_url")
                    val url = part.optString("url", imageUrlObj?.optString("url", "") ?: part.optString("image_url", ""))
                    val isImage = partType.contains("image") || mimeType.startsWith("image/")
                    if (isImage) {
                        if (b64.isNotEmpty()) {
                            val bytes = try {
                                Base64.decode(b64, Base64.DEFAULT)
                            } catch (_: Exception) {
                                null
                            }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val uri = writeOutputImage(bytes, mimeType, "openai_image_${i}_$j")
                                if (uri != null) {
                                    emitImageMarkdown(emitter, uri, "openai_image_${i}_$j")
                                    handledAny = true
                                }
                            }
                        } else if (url.isNotEmpty()) {
                            val bytes = downloadBytes(url)
                            if (bytes != null && bytes.isNotEmpty()) {
                                val uri = writeOutputImage(bytes, mimeType, "openai_image_${i}_$j")
                                if (uri != null) {
                                    emitImageMarkdown(emitter, uri, "openai_image_${i}_$j")
                                    handledAny = true
                                }
                            }
                        }
                    }
                }
            }
            return handledAny
        }

        return false
    }

    /**
     * 解析服务器返回的内容，不再需要处理<think>标签
     */
    private fun parseResponse(content: String): String {
        return content
    }

    // 创建请求体
    protected open fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>> = emptyList(),
        enableThinking: Boolean = false,
        stream: Boolean = true,
        availableTools: List<ToolPrompt>? = null,
        preserveThinkInHistory: Boolean = false
    ): RequestBody {
        val jsonString =
            createRequestBodyInternal(context, chatHistory, modelParameters, stream, availableTools, preserveThinkInHistory)
        if (!supportsOpenAiChatReasoningEffort()) {
            return createJsonRequestBody(jsonString)
        }
        val requestJson = JSONObject(jsonString)
        applyOpenAiChatReasoning(context, requestJson, enableThinking)
        return createJsonRequestBody(requestJson.toString())
    }

    private fun applyOpenAiChatReasoning(
        context: Context,
        requestJson: JSONObject,
        enableThinking: Boolean
    ) {
        if (!supportsOpenAiChatReasoningEffort()) {
            return
        }

        val compiledRequest = compileModelRequest(context, enableThinking)
        val effort = compiledRequest.reasoningEffort ?: return
        OpenAIChatRequestFeatureCompiler.apply(
            requestJson = requestJson,
            compiledRequest = compiledRequest,
        )
        AppLogger.d(
            "OpenAIProvider",
            "OpenAI Chat Completions reasoning_effort=${effort.wireValue}, " +
                "profile=${compiledRequest.profile.profileId}"
        )
    }

    private fun supportsOpenAiChatReasoningEffort(): Boolean =
        modelCapabilityProfile.reasoningWireFormat == ReasoningWireFormat.CHAT_COMPLETIONS

    protected fun compileModelRequest(
        context: Context,
        enableThinking: Boolean,
    ): CompiledModelRequest {
        val qualityLevel =
            runBlocking {
                ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            }
        return ModelRequestCompiler.compile(
            profile = modelCapabilityProfile,
            intent =
                UserExecutionIntent(
                    enableThinking = enableThinking,
                    thinkingQualityLevel = qualityLevel,
                ),
        )
    }

    protected fun createJsonRequestBody(jsonString: String): RequestBody {
        return jsonString.toByteArray(Charsets.UTF_8).toRequestBody(JSON)
    }

    /**
     * 内部方法，用于构建请求体的JSON字符串，以便子类可以重用和扩展。
     */
    protected fun createRequestBodyInternal(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>> = emptyList(),
        stream: Boolean = true,
        availableTools: List<ToolPrompt>? = null,
        preserveThinkInHistory: Boolean = false
    ): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", stream) // 根据stream参数设置

        // 添加已启用的模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                val mappedApiName =
                    if (useResponsesApi) {
                        OpenAIResponsesPayloadAdapter.mapParameterNameForResponses(param.apiName)
                    } else {
                        param.apiName
                    }
                when (param.valueType) {
                    com.ai.assistance.operit.data.model.ParameterValueType.INT ->
                        jsonObject.put(mappedApiName, param.currentValue as Int)

                    com.ai.assistance.operit.data.model.ParameterValueType.FLOAT ->
                        jsonObject.put(mappedApiName, param.currentValue as Float)

                    com.ai.assistance.operit.data.model.ParameterValueType.STRING ->
                        jsonObject.put(mappedApiName, param.currentValue as String)

                    com.ai.assistance.operit.data.model.ParameterValueType.BOOLEAN ->
                        jsonObject.put(mappedApiName, param.currentValue as Boolean)

                    com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                        val raw = param.currentValue.toString().trim()
                        val parsed: Any? = try {
                            when {
                                raw.startsWith("{") -> JSONObject(raw)
                                raw.startsWith("[") -> JSONArray(raw)
                                else -> null
                            }
                        } catch (e: Exception) {
                            AppLogger.w("AIService", "OBJECT参数解析失败: ${param.apiName}", e)
                            null
                        }
                        if (parsed != null) {
                            jsonObject.put(mappedApiName, parsed)
                        } else {
                            // 解析失败则按字符串传递，避免崩溃
                            jsonObject.put(mappedApiName, raw)
                        }
                    }
                }
                AppLogger.d("AIService", "添加参数 ${param.apiName} = ${param.currentValue}")
            }
        }

        // 当工具为空时，将enableToolCall视为false
        val effectiveEnableToolCall =
            enableToolCall && availableTools != null && availableTools.isNotEmpty()

        // 如果启用Tool Call且传入了工具列表，添加tools定义
        var toolsJson: String? = null
        if (effectiveEnableToolCall) {
            val tools = buildToolDefinitions(availableTools)
            if (tools.length() > 0) {
                jsonObject.put("tools", tools)
                jsonObject.put("tool_choice", "auto") // 让模型自动决定是否使用工具
                toolsJson =
                    ProviderToolCallIdentityContract.canonicalJsonText(
                        tools.toString()
                    ) // 保存稳定工具定义用于 token 计算
                AppLogger.d("AIService", "Tool Call已启用，添加了 ${tools.length()} 个工具定义")
            }
        }

        // 使用新的核心逻辑构建消息并获取token计数
        val (messagesArray, tokenCount) = buildMessagesAndCountTokens(
            context,
            chatHistory,
            effectiveEnableToolCall,
            toolsJson,
            preserveThinkInHistory
        )
        jsonObject.put("messages", messagesArray)

        val finalRequestObject =
            if (useResponsesApi) {
                OpenAIResponsesPayloadAdapter.toResponsesRequest(jsonObject)
            } else {
                jsonObject
            }

        customizeFinalRequestObject(finalRequestObject, messagesArray, toolsJson)

        return ProviderToolCallIdentityContract.canonicalJsonText(
            finalRequestObject.toString()
        )
    }

    protected open fun comparableRoleForTurn(turn: PromptTurn): String {
        return when (turn.kind) {
            PromptTurnKind.SYSTEM -> "system"
            PromptTurnKind.USER -> "user"
            PromptTurnKind.ASSISTANT -> "assistant"
            PromptTurnKind.TOOL_CALL -> "tool_call"
            PromptTurnKind.TOOL_RESULT -> "tool_result"
            PromptTurnKind.SUMMARY -> "summary"
        }
    }

    protected open fun providerRoleForTurn(turn: PromptTurn): String {
        return when (turn.kind) {
            PromptTurnKind.SYSTEM -> "system"
            PromptTurnKind.USER,
            PromptTurnKind.SUMMARY,
            PromptTurnKind.TOOL_RESULT -> "user"
            PromptTurnKind.ASSISTANT,
            PromptTurnKind.TOOL_CALL -> "assistant"
        }
    }

    protected open fun comparableContentForTurn(
        turn: PromptTurn,
        preserveThinkInHistory: Boolean
    ): String {
        return if (!preserveThinkInHistory && turn.kind == PromptTurnKind.ASSISTANT) {
            ChatUtils.removeThinkingContent(turn.content)
        } else {
            turn.content
        }
    }

    protected open fun buildComparableHistory(
        chatHistory: List<PromptTurn>,
        preserveThinkInHistory: Boolean
    ): List<Pair<String, String>> {
        return chatHistory.map { turn ->
            comparableRoleForTurn(turn) to comparableContentForTurn(turn, preserveThinkInHistory)
        }
    }

    protected open fun buildEffectiveHistory(
        chatHistory: List<PromptTurn>
    ): List<PromptTurn> {
        return chatHistory
    }

    protected open fun prepareHistoryForProvider(
        chatHistory: List<PromptTurn>,
        useToolCall: Boolean
    ): List<PromptTurn> {
        val replaySafeHistory =
            AssistantReplayHistoryProjector.replaySafeHistory(
                history = buildEffectiveHistory(chatHistory),
                allowTypedToolHistory = useToolCall,
            )
        return StructuredToolCallBridge.compileHistoryForProvider(
            replaySafeHistory,
            useToolCall = useToolCall
        )
    }

    protected open fun generatedToolCallId(ordinal: Int): String {
        return generatedShortToolCallId("tool_call:$ordinal", ordinal)
    }

    protected fun calculateAndStoreInputTokens(
        providerReadyHistory: List<PromptTurn>,
        toolsJson: String? = null,
        preserveThinkInHistory: Boolean = false
    ): Int {
        val comparableHistory = buildComparableHistory(providerReadyHistory, preserveThinkInHistory)
        return tokenCacheManager.calculateInputTokens(comparableHistory, toolsJson)
    }

    protected fun audioFormatFromMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg" -> "ogg"
            "audio/webm" -> "webm"
            else -> mimeType.substringAfter("/", "wav")
        }
    }

    protected open fun buildInputAudioPayload(link: MediaLink): JSONObject {
        return JSONObject().apply {
            put("data", link.base64Data)
            put("format", audioFormatFromMime(link.mimeType))
        }
    }

    /**
     * 构建content字段（可能是字符串或数组）
     * @param text 要处理的文本内容
     * @return 纯文本字符串或包含图片和文本的JSONArray
     */
    fun buildContentField(context: Context, text: String): Any {
        val hasImages = MediaLinkParser.hasImageLinks(text)
        val hasMedia = MediaLinkParser.hasMediaLinks(text)

        val mediaLinks = if (hasMedia) MediaLinkParser.extractMediaLinks(text) else emptyList()
        val imageLinks = if (hasImages) MediaLinkParser.extractImageLinks(text) else emptyList()

        val audioLinks = mediaLinks.filter { it.type == "audio" }
        val videoLinks = mediaLinks.filter { it.type == "video" }

        val hasSupportedMedia =
            (supportsAudio && audioLinks.isNotEmpty()) || (supportsVideo && videoLinks.isNotEmpty())

        var textWithoutLinks = text
        if (hasMedia) {
            textWithoutLinks = MediaLinkParser.removeMediaLinks(textWithoutLinks)
        }
        if (hasImages) {
            textWithoutLinks = MediaLinkParser.removeImageLinks(textWithoutLinks)
        }
        textWithoutLinks = textWithoutLinks.trim()

        if (audioLinks.isNotEmpty() && !supportsAudio) {
            AppLogger.w("AIService", "检测到音频链接，但当前Provider不支持音频多模态输入，已移除音频。原始文本长度: ${text.length}, 处理后: ${textWithoutLinks.length}")
        }
        if (videoLinks.isNotEmpty() && !supportsVideo) {
            AppLogger.w("AIService", "检测到视频链接，但当前Provider不支持视频多模态输入，已移除视频。原始文本长度: ${text.length}, 处理后: ${textWithoutLinks.length}")
        }
        if (imageLinks.isNotEmpty() && !supportsVision) {
            AppLogger.w("AIService", "检测到图片链接，但当前Provider不支持图片处理，已移除图片。原始文本长度: ${text.length}, 处理后: ${textWithoutLinks.length}")
        }

        val hasAnySupportedRichContent = hasSupportedMedia || (supportsVision && imageLinks.isNotEmpty())
        if (!hasAnySupportedRichContent) {
            if (textWithoutLinks.isNotEmpty()) return textWithoutLinks

            return when {
                audioLinks.isNotEmpty() || videoLinks.isNotEmpty() -> context.getString(R.string.openai_audio_video_omitted)
                imageLinks.isNotEmpty() -> context.getString(R.string.openai_image_omitted)
                else -> ""
            }
        }

        val contentArray = JSONArray()

        if (supportsAudio) {
            audioLinks.forEach { link ->
                contentArray.put(JSONObject().apply {
                    put("type", "input_audio")
                    put("input_audio", buildInputAudioPayload(link))
                })
            }
        }

        if (supportsVideo) {
            videoLinks.forEach { link ->
                contentArray.put(JSONObject().apply {
                    put("type", "video_url")
                    put(
                        "video_url",
                        JSONObject().apply {
                            put("url", "data:${link.mimeType};base64,${link.base64Data}")
                        }
                    )
                })
            }
        }

        if (supportsVision) {
            imageLinks.forEach { link ->
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:${link.mimeType};base64,${link.base64Data}")
                    })
                })
            }
        }

        if (textWithoutLinks.isNotEmpty()) {
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", textWithoutLinks)
            })
        }

        return contentArray
    }

    /**
     * 构建消息列表并计算token（核心逻辑）
     * @param message 用户消息
     * @param chatHistory 聊天历史
     * @param useToolCall 是否启用Tool Call格式转换（会根据工具可用性动态决定）
     * @param toolsJson 工具定义的JSON字符串，用于token计算
     * @return Pair(消息列表JSONArray, 输入token计数)
     */
    protected fun buildMessagesAndCountTokens(
        context: Context,
        chatHistory: List<PromptTurn>,
        useToolCall: Boolean = false,
        toolsJson: String? = null,
        preserveThinkInHistory: Boolean = false
    ): Pair<JSONArray, Int> {
        val messagesArray = JSONArray()
        val providerReadyHistory = prepareHistoryForProvider(chatHistory, useToolCall)

        // 使用TokenCacheManager计算token数量（包含工具定义）
        val tokenCount =
            calculateAndStoreInputTokens(
                providerReadyHistory,
                toolsJson,
                preserveThinkInHistory
            )
        val effectiveHistory = providerReadyHistory

        var queuedAssistantToolText: String? = null
        var queuedToolCalls = JSONArray()
        val queuedToolCallIds = mutableListOf<String>()
        val openToolCallIds = mutableListOf<String>()
        val toolHistoryState = ProviderToolHistoryState()

        fun appendQueuedAssistantToolText(text: String) {
            if (text.isBlank()) return
            queuedAssistantToolText =
                if (queuedAssistantToolText.isNullOrBlank()) {
                    text
                } else {
                    queuedAssistantToolText + "\n" + text
                }
        }

        fun queueToolCalls(textContent: String, toolCalls: JSONArray) {
            appendQueuedAssistantToolText(textContent)
            for (i in 0 until toolCalls.length()) {
                val sourceToolCall = toolCalls.optJSONObject(i) ?: continue
                val toolCall = JSONObject(sourceToolCall.toString())
                val callId = toolCall.optString("id", "").trim()
                require(callId.isNotEmpty()) {
                    "Compiled tool call must have a stable call ID"
                }
                queuedToolCalls.put(toolCall)
                queuedToolCallIds.add(callId)
            }
        }

        fun emitQueuedToolCallsIfNeeded() {
            if (queuedToolCalls.length() == 0) return

            val historyMessage = JSONObject()
            historyMessage.put("role", "assistant")
            val effectiveContent = when {
                !queuedAssistantToolText.isNullOrBlank() -> queuedAssistantToolText
                else -> null
            }
            if (effectiveContent != null) {
                historyMessage.put("content", buildContentField(context, effectiveContent))
            } else {
                historyMessage.put("content", null)
            }
            historyMessage.put("tool_calls", queuedToolCalls)
            messagesArray.put(historyMessage)

            openToolCallIds.addAll(queuedToolCallIds)
            toolHistoryState.acceptToolCalls(
                callIds = queuedToolCallIds.toList(),
                boundary = "assistant_tool_calls",
            )
            queuedAssistantToolText = null
            queuedToolCalls = JSONArray()
            queuedToolCallIds.clear()
        }

        fun requireNoOpenToolCalls(reason: String) {
            emitQueuedToolCallsIfNeeded()
            toolHistoryState.requireClosed(reason)
            openToolCallIds.clear()
        }

        // 添加聊天历史
        if (effectiveHistory.isNotEmpty()) {
            for (turn in effectiveHistory) {
                val content = comparableContentForTurn(turn, preserveThinkInHistory)
                // 当启用Tool Call API时，转换XML格式的工具调用
                if (useToolCall) {
                    when (turn.kind) {
                        PromptTurnKind.SYSTEM -> {
                            requireNoOpenToolCalls("system_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", buildContentField(context, content))
                                }
                            )
                        }

                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY -> {
                            requireNoOpenToolCalls("user_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, content))
                                }
                            )
                        }

                        PromptTurnKind.ASSISTANT -> {
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(content)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                requireNoOpenToolCalls("assistant_tool_call_before_result")
                                queueToolCalls(textContent, toolCalls)
                            } else {
                                requireNoOpenToolCalls("assistant_boundary")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put(
                                            "content",
                                            if (content.isBlank()) JSONObject.NULL else buildContentField(context, content),
                                        )
                                    }
                                )
                            }
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(content)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                requireNoOpenToolCalls("typed_tool_call_before_result")
                                queueToolCalls(textContent, toolCalls)
                            } else {
                                throw ProviderToolHistoryProtocolException(
                                    violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                                    detail = "Typed TOOL_CALL has no structured payload",
                                )
                            }
                        }

                        PromptTurnKind.TOOL_RESULT -> {
                            emitQueuedToolCallsIfNeeded()
                            val (textContent, toolResults) = parseXmlToolResults(content)
                            val resultsList =
                                toolResults
                                    ?: throw ProviderToolHistoryProtocolException(
                                        violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                                        detail = "Typed TOOL_RESULT has no structured payload",
                                    )
                            toolHistoryState.acceptToolResults(
                                resultCount = resultsList.size,
                                boundary = "tool_result",
                            )
                            repeat(resultsList.size) { index ->
                                val (_, resultContent) = resultsList[index]
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "tool")
                                        put("tool_call_id", openToolCallIds[index])
                                        put("content", resultContent)
                                    }
                                )
                            }
                            repeat(resultsList.size) {
                                openToolCallIds.removeAt(0)
                            }
                            if (textContent.isNotEmpty()) {
                                toolHistoryState.requireClosed("tool_result_text_boundary")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "user")
                                        put("content", buildContentField(context, textContent))
                                    }
                                )
                            }
                        }
                    }
                } else {
                    requireNoOpenToolCalls("tool_call_api_disabled")
                    if (turn.kind == PromptTurnKind.TOOL_CALL || turn.kind == PromptTurnKind.TOOL_RESULT) {
                        throw ProviderToolHistoryProtocolException(
                            violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                            detail = "Typed ${turn.kind} cannot be replayed when native tool calls are disabled",
                        )
                    }
                    val role = providerRoleForTurn(turn)
                    // 不启用Tool Call API时，保持原样
                    val historyMessage = JSONObject()
                    historyMessage.put("role", role)

                    historyMessage.put(
                        "content",
                        if (role == "assistant" && content.isBlank()) {
                            JSONObject.NULL
                        } else {
                            buildContentField(context, content)
                        },
                    )
                    messagesArray.put(historyMessage)
                }
            }
        }

        requireNoOpenToolCalls("history_end")

        return Pair(messagesArray, tokenCount)
    }

    override suspend fun calculateInputTokens(
        chatHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?
    ): Int {
        // 构建工具定义的JSON字符串
        val toolsJson =
            if (enableToolCall && availableTools != null && availableTools.isNotEmpty()) {
                val tools = buildToolDefinitions(availableTools)
                if (tools.length() > 0) tools.toString() else null
            } else {
                null
        }
        // 使用TokenCacheManager计算token数量
        return tokenCacheManager.calculateInputTokens(
            buildComparableHistory(chatHistory, preserveThinkInHistory = false),
            toolsJson,
            updateState = false
        )
    }

    // ==================== Tool Call 支持 ====================

    /**
     * 从ToolPrompt列表构建Tool Call的JSON Schema定义
     */
    fun buildToolDefinitions(toolPrompts: List<ToolPrompt>): JSONArray {
        val tools = JSONArray()
        val sortedTools = toolPrompts.sortedBy { tool -> tool.name }
        val duplicateToolName =
            sortedTools
                .groupingBy { tool -> tool.name }
                .eachCount()
                .entries
                .firstOrNull { entry -> entry.value > 1 }
                ?.key
        require(duplicateToolName == null) {
            "OpenAI-compatible tool definitions contain duplicate name: $duplicateToolName"
        }

        for (tool in sortedTools) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    // 组合description和details作为完整描述
                    val fullDescription = if (tool.details.isNotEmpty()) {
                        "${tool.description}\n${tool.details}"
                    } else {
                        tool.description
                    }
                    put("description", fullDescription)

                    // 只使用结构化参数
                    val structuredParameters = tool.parametersStructured ?: emptyList()
                    val parametersSchema =
                        buildSchemaFromStructured(structuredParameters)
                    val strictCompatible =
                        modelCapabilityProfile.toolSchema ==
                            ToolSchemaCapability.STRICT_WHEN_SCHEMA_COMPATIBLE &&
                            structuredParameters.all { parameter -> parameter.required }
                    if (strictCompatible) {
                        parametersSchema.put("additionalProperties", false)
                        put("strict", true)
                    }
                    put("parameters", parametersSchema)
                })
            })
        }

        return tools
    }

    /**
     * 从结构化参数构建JSON Schema
     */
    private fun buildSchemaFromStructured(params: List<com.ai.assistance.operit.data.model.ToolParameterSchema>): JSONObject {
        val schema = JSONObject().apply {
            put("type", "object")
        }

        val properties = JSONObject()
        val required = JSONArray()
        val sortedParams = params.sortedBy { parameter -> parameter.name }
        val duplicateParameterName =
            sortedParams
                .groupingBy { parameter -> parameter.name }
                .eachCount()
                .entries
                .firstOrNull { entry -> entry.value > 1 }
                ?.key
        require(duplicateParameterName == null) {
            "OpenAI-compatible tool schema contains duplicate parameter: $duplicateParameterName"
        }

        for (param in sortedParams) {
            properties.put(param.name, JSONObject().apply {
                put("type", param.type)
                put("description", param.description)
                if (param.default != null) {
                    put("default", param.default)
                }
            })

            if (param.required) {
                required.put(param.name)
            }
        }

        schema.put("properties", properties)
        // OpenAI-compatible validators expect `required` to keep its array type even when empty.
        schema.put("required", required)

        return schema
    }

    /**
     * 将API返回的tool_calls转换为XML格式
     * 这样上层代码无需修改，继续使用XML解析逻辑
     * @param toolCalls tool_calls JSON数组
     * @param isStreaming 是否为流式响应（流式响应中tool_calls是增量的）
     */
    private fun convertToolCallsToXml(
        toolCalls: JSONArray,
        _isStreaming: Boolean = false,
    ): String {
        val xml = StringBuilder()

        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.getJSONObject(i)
            val function = toolCall.optJSONObject("function") ?: continue

            // 流式响应中，name和arguments可能不在同一个delta中
            val name = function.optString("name", "")
            if (name.isEmpty()) {
                // 如果没有name，说明这是增量更新，跳过
                continue
            }

            val argumentsJson = function.optString("arguments", "")

            // 解析参数JSON
            val params = if (argumentsJson.isNotEmpty()) {
                try {
                    JSONObject(argumentsJson)
                } catch (e: Exception) {
                    AppLogger.w("OpenAIProvider", "Failed to parse tool arguments: $argumentsJson", e)
                    JSONObject()
                }
            } else {
                JSONObject()
            }

            // 构建XML格式
            val toolTagName = ChatMarkupRegex.generateRandomToolTagName()
            xml.append("\n<$toolTagName name=\"${escapeXml(name)}\"")
            xml.append(" provider_name=\"${escapeXml(providerType.name)}\"")
            toolCall.optString("id", "").takeIf { it.isNotBlank() }?.let { callId ->
                xml.append(" provider_call_id=\"${escapeXml(callId)}\"")
            }
            xml.append(">")

            // 添加所有参数
            val keys = params.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = params.get(key)
                // 必须对值进行XML转义，否则会破坏XML结构
                val escapedValue = escapeXml(value.toString())
                xml.append("\n<param name=\"$key\">$escapedValue</param>")
            }

            xml.append("\n</$toolTagName>\n")
        }

        return xml.toString()
    }

    /**
     * XML转义/反转义工具
     */
    private object XmlEscaper {
        fun escape(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun unescape(text: String): String {
            return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }

    private fun sanitizeToolCallId(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) {
            return "call00000"
        }
        if (cleaned.length == 9) {
            return cleaned
        }
        if (cleaned.length > 9) {
            return cleaned.takeLast(9)
        }

        val filler = stableIdHashPart(raw)
        return (cleaned + filler + "000000000").take(9)
    }

    protected fun generatedShortToolCallId(seed: String, ordinal: Int): String {
        return sanitizeToolCallId("${stableIdHashPart(seed)}_$ordinal")
    }

    private fun stableIdHashPart(raw: String): String {
        val hash = raw.hashCode()
        val positive = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)
        var base = positive.toString(36)
        base = base.filter { it.isLetterOrDigit() }.lowercase()
        return if (base.isEmpty()) "0" else base
    }

    // 向后兼容的快捷方法
    private fun escapeXml(text: String) = XmlEscaper.escape(text)

    /**
     * 字符串非空且非"null"检查
     */
    private fun String.isNotNullOrEmpty() = this.isNotEmpty() && this != "null"

    /**
     * Tool Call流式输出状态管理
     */
    private data class ToolCallState(
        val emitted: MutableMap<Int, Boolean> = mutableMapOf(),
        val nameEmitted: MutableMap<Int, Boolean> = mutableMapOf(),
        val parser: MutableMap<Int, StreamingJsonXmlConverter> = mutableMapOf(),
        val closed: MutableMap<Int, Boolean> = mutableMapOf(),
        val fedLength: MutableMap<Int, Int> = mutableMapOf(),
        val tagNames: MutableMap<Int, String> = mutableMapOf(),
        val providerCallIdByIndex: MutableMap<Int, String> = mutableMapOf(),
        val indexByProviderCallId: MutableMap<String, Int> = mutableMapOf(),
    ) {
        fun getParser(index: Int) = parser.getOrPut(index) { StreamingJsonXmlConverter() }

        fun getTagName(index: Int) =
            tagNames.getOrPut(index) { ChatMarkupRegex.generateRandomToolTagName() }

        fun bindProviderCallId(
            index: Int,
            callId: String,
        ) {
            if (callId.isBlank()) return

            val existingCallId = providerCallIdByIndex[index]
            if (existingCallId != null && existingCallId != callId) {
                throw OpenAIResponsesProtocolException(
                    "Responses output index $index changed tool call identity " +
                        "from $existingCallId to $callId"
                )
            }
            val existingIndex = indexByProviderCallId[callId]
            if (existingIndex != null && existingIndex != index) {
                throw OpenAIResponsesProtocolException(
                    "Responses tool call $callId appeared at output indices " +
                        "$existingIndex and $index"
                )
            }
            providerCallIdByIndex[index] = callId
            indexByProviderCallId[callId] = index
        }

        fun resolveTerminalIndex(
            terminalOutputIndex: Int,
            callId: String,
        ): Int =
            OpenAIResponsesTerminalSnapshot.resolveToolCallIndex(
                terminalOutputIndex = terminalOutputIndex,
                callId = callId,
                indexByCallId = indexByProviderCallId,
                callIdByIndex = providerCallIdByIndex,
            )

        fun clear() {
            emitted.clear()
            nameEmitted.clear()
            parser.clear()
            closed.clear()
            fedLength.clear()
            tagNames.clear()
            providerCallIdByIndex.clear()
            indexByProviderCallId.clear()
        }
    }

    /**
     * 流式内容发送辅助类
     */
    private inner class StreamEmitter(
        private val receivedContent: StringBuilder,
        private val emit: suspend (String) -> Unit,
        private val eventChannel: com.ai.assistance.operit.util.stream.MutableSharedStream<TextStreamEvent>,
        private val onTokensUpdated: suspend (Int, Int, Int) -> Unit,
        private var streamingState: StreamingState? = null,
    ) {
        private val savepointLengths = mutableMapOf<String, Int>()

        fun bindStreamingState(state: StreamingState) {
            streamingState = state
        }

        suspend fun emitContent(content: String, reasoning: Boolean = false) {
            if (content.isNotNullOrEmpty()) {
                emit(content)
                receivedContent.append(content)
                streamingState?.let { state ->
                    if (reasoning) state.reasoningCharacterCount += content.length
                    else state.visibleCharacterCount += content.length
                }
                tokenCacheManager.addOutputTokens(ChatUtils.estimateTokenCount(content))
                onTokensUpdated(
                    tokenCacheManager.totalInputTokenCount,
                    tokenCacheManager.cachedInputTokenCount,
                    tokenCacheManager.outputTokenCount
                )
            }
        }

        suspend fun emitThinkContent(thinkContent: String, tag: String = "think") {
            if (thinkContent.isNotNullOrEmpty()) {
                val wrapped = "<$tag>$thinkContent</$tag>"
                emit(wrapped)
                receivedContent.append(wrapped)
                streamingState?.let { state ->
                    state.reasoningCharacterCount += thinkContent.length
                }
                tokenCacheManager.addOutputTokens(ChatUtils.estimateTokenCount(thinkContent))
                onTokensUpdated(
                    tokenCacheManager.totalInputTokenCount,
                    tokenCacheManager.cachedInputTokenCount,
                    tokenCacheManager.outputTokenCount
                )
            }
        }

        suspend fun emitTag(tag: String) {
            emit(tag)
            receivedContent.append(tag)
        }

        suspend fun emitSavepoint(id: String) {
            savepointLengths[id] = receivedContent.length
            eventChannel.emit(TextStreamEvent(TextStreamEventType.SAVEPOINT, id))
        }

        fun getSavepointLength(id: String): Int? = savepointLengths[id]

        suspend fun emitRollback(id: String): Boolean {
            val savepointLength = savepointLengths[id] ?: return false
            if (receivedContent.length > savepointLength) {
                receivedContent.setLength(savepointLength)
            }
            eventChannel.emit(TextStreamEvent(TextStreamEventType.ROLLBACK, id))
            return true
        }

        /**
         * 处理 StreamingJsonXmlConverter 事件，转换为 XML 输出
         */
        suspend fun handleJsonEvents(events: List<StreamingJsonXmlConverter.Event>) {
            for (event in events) {
                when (event) {
                    is StreamingJsonXmlConverter.Event.Tag -> emitTag(event.text)
                    is StreamingJsonXmlConverter.Event.Content -> emitContent(event.text)
                }
            }
        }
    }

    /**
     * 创建 Tool Call 累积对象
     */
    private fun createToolCallAccumulator(index: Int): JSONObject {
        return JSONObject().apply {
            put("index", index)
            put("id", "")
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "")
                put("arguments", "")
            })
        }
    }

    /**
     * 检查是否已被取消，如果是则抛出异常
     */
    private fun checkCancellation(
        context: Context,
        session: ProviderStreamSessionGate.Session,
        exception: Exception? = null,
    ) {
        if (streamSessionGate.isCancelled(session)) {
            AppLogger.d("AIService", "请求被用户取消，停止重试。")
            throw UserCancellationException(context.getString(R.string.openai_error_request_cancelled), exception)
        }
    }

    private fun resolveRetryErrorText(context: Context, exception: Exception): String {
        return when (exception) {
            is SocketTimeoutException -> context.getString(R.string.openai_error_timeout)
            is UnknownHostException -> context.getString(R.string.openai_error_cannot_resolve_host)
            else -> exception.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.openai_error_network_interrupted)
        }
    }

    /**
     * 处理可重试错误的统一逻辑
     */
    private suspend fun handleRetryableError(
        context: Context,
        session: ProviderStreamSessionGate.Session,
        exception: Exception,
        retryCount: Int,
        maxRetries: Int,
        enableRetry: Boolean,
        onNonFatalError: suspend (String) -> Unit,
        buildRetryMessage: (String, Int) -> String
    ): Int {
        if (exception is UserCancellationException || exception is CancellationException) {
            throw exception
        }
        checkCancellation(context, session, exception)

        val errorText = resolveRetryErrorText(context, exception)

        if (!enableRetry) {
            throw IOException(errorText, exception)
        }

        val newRetryCount = retryCount + 1
        if (newRetryCount > maxRetries) {
            AppLogger.e("AIService", "【发送消息】$errorText 且达到最大重试次数($maxRetries)", exception)
            throw IOException(
                context.getString(R.string.openai_error_connection_timeout, maxRetries, errorText),
                exception
            )
        }

        val retryDelayMs = LlmRetryPolicy.nextDelayMs(newRetryCount)
        AppLogger.w("AIService", "【发送消息】$errorText，将在 ${retryDelayMs}ms 后进行第 $newRetryCount 次重试...", exception)
        if (!shouldSuppressKeyPoolRateLimitNotice(apiKeyProvider, exception, "AIService")) {
            onNonFatalError(buildRetryMessage(errorText, newRetryCount))
        }
        delay(retryDelayMs)

        return newRetryCount
    }

    /**
     * Responses POST 只有提交前传输证据或明确拒绝才能证明可以重试。
     *
     * 兼容端点没有声明官方 Background/sequence resume，因此 408、409、5xx 和提交后传输异常必须
     * 结束当前回合并保留已确认内容，不能进入普通 OpenAI 流的整轮回滚与重新 POST。
     */
    private suspend fun handleAtMostOnceResponsesFailure(
        context: Context,
        session: ProviderStreamSessionGate.Session,
        exception: Exception,
        retryCount: Int,
        maxRetries: Int,
        enableRetry: Boolean,
        onNonFatalError: suspend (String) -> Unit,
        providerRequestContext: ProviderRequestContext?,
        transportDiagnostics: LlmTransportDiagnostics?,
    ): Int {
        if (exception is UserCancellationException || exception is CancellationException) {
            throw exception
        }
        checkCancellation(context, session, exception)

        // 明确终态与本地解析错误不是“是否提交未知”；不能用网络重试改变这些事实。
        if (exception is OpenAIResponsesTerminalException ||
            exception is OpenAIResponsesProtocolException ||
            exception is OpenAIResponsesEventProcessingException
        ) {
            throw exception
        }
        if (exception is IOException && isSafePreSubmissionRetry(transportDiagnostics)) {
            return handleRetryableError(
                context, session, exception, retryCount, maxRetries, enableRetry, onNonFatalError,
            ) { errorText, retryNumber ->
                "【Responses请求尚未提交，正在进行第${retryNumber}次重试：$errorText】"
            }
        }
        val action =
            if (exception is HttpStatusCodeException) {
                OpenAIResponsesHttpFailurePolicy.classifySubmission(exception.statusCode)
            } else {
                OpenAIResponsesHttpFailurePolicy.SubmissionAction.SUBMISSION_UNKNOWN
            }
        return when (action) {
            OpenAIResponsesHttpFailurePolicy.SubmissionAction.RETRY_EXPLICIT_REJECTION ->
                handleRetryableError(
                    context = context,
                    session = session,
                    exception = exception,
                    retryCount = retryCount,
                    maxRetries = maxRetries,
                    enableRetry = enableRetry,
                    onNonFatalError = onNonFatalError,
                ) { errorText, retryNumber ->
                    "【Responses提交被明确拒绝，正在进行第${retryNumber}次重试：$errorText】"
                }

            OpenAIResponsesHttpFailurePolicy.SubmissionAction.FAIL ->
                throw if (exception is IOException) {
                    exception
                } else {
                    IOException(exception.message, exception)
                }

            OpenAIResponsesHttpFailurePolicy.SubmissionAction.SUBMISSION_UNKNOWN ->
                throw OpenAIResponsesSubmissionUnknownException(
                    localExecutionId = providerRequestContext?.localExecutionId,
                    cause =
                        if (exception is IOException) {
                            exception
                        } else {
                            IOException(exception.message, exception)
                        },
                    transportDiagnostics = transportDiagnostics,
                )
        }
    }

    private fun appendTransportDiagnostics(
        message: String?,
        diagnostics: LlmTransportDiagnostics?,
    ): String? {
        val diagnosticSummary = diagnostics?.summary() ?: return message
        val messageText = message?.take(1024)?.trim().orEmpty()
        return if (messageText.isEmpty()) {
            diagnosticSummary
        } else {
            "$messageText | $diagnosticSummary"
        }
    }

    protected fun wrapPackageToolCallsWithProxy(toolCalls: JSONArray): JSONArray {
        val wrappedToolCalls = JSONArray()
        var wrappedCount = 0

        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.optJSONObject(i) ?: continue
            val function = toolCall.optJSONObject("function")
            if (function == null) {
                wrappedToolCalls.put(toolCall)
                continue
            }

            val toolName = function.optString("name", "")
            if (!toolName.contains(":") || toolName == "package_proxy") {
                wrappedToolCalls.put(toolCall)
                continue
            }

            val rawArguments = function.optString("arguments", "{}")
            val originalArguments = JSONObject(if (rawArguments.isBlank()) "{}" else rawArguments)
            val proxyArguments = JSONObject().apply {
                put("tool_name", toolName)
                put("params", originalArguments)
            }

            val wrappedFunction = JSONObject(function.toString()).apply {
                put("name", "package_proxy")
                put(
                    "arguments",
                    ProviderToolCallIdentityContract.canonicalJsonText(
                        proxyArguments.toString()
                    ),
                )
            }

            val wrappedToolCall = JSONObject(toolCall.toString()).apply {
                put("function", wrappedFunction)
            }
            wrappedToolCalls.put(wrappedToolCall)
            wrappedCount++
        }

        if (wrappedCount > 0) {
            AppLogger.d("AIService", "已代理封装 $wrappedCount 个带冒号工具调用到 package_proxy")
        }
        return wrappedToolCalls
    }

    /**
     * 解析XML格式的tool调用，转换为OpenAI Tool Call格式
     * @return Pair<文本内容, tool_calls数组>
     */
    open fun parseXmlToolCalls(content: String): Pair<String, JSONArray?> {
        val matches = ChatMarkupRegex.toolCallPattern.findAll(content)

        if (!matches.any()) {
            return Pair(content, null)
        }

        val toolCalls = JSONArray()
        var textContent = content
        var callIndex = 0
        val providerCallSignatures =
            linkedMapOf<ProviderToolCallKey, ProviderToolCallSignature>()

        matches.forEach { match ->
            val toolName = match.groupValues[2]
            val toolBody = match.groupValues[3]
            val openingTag = match.value.substringBefore('>', "")

            // 解析参数
            val params = JSONObject()

            ChatMarkupRegex.toolParamPattern.findAll(toolBody).forEach { paramMatch ->
                val paramName = paramMatch.groupValues[1]
                val paramValue = XmlEscaper.unescape(paramMatch.groupValues[2].trim())
                params.put(paramName, paramValue)
            }

            val providerCallId =
                extractXmlAttribute(openingTag, "provider_call_id")
            val providerName =
                extractXmlAttribute(openingTag, "provider_name")
                    ?: providerType.name
            val argumentsJson =
                ProviderToolCallIdentityContract.canonicalJsonText(
                    params.toString()
                )
            val providerIdentity =
                ProviderToolCallIdentityContract.key(providerName, providerCallId)
            if (providerIdentity != null) {
                val signature =
                    ProviderToolCallIdentityContract.signature(
                        toolName = toolName,
                        argumentsJson = argumentsJson,
                    )
                val existing = providerCallSignatures[providerIdentity]
                if (existing != null) {
                    ProviderToolCallIdentityContract.requireSame(
                        providerIdentity,
                        existing,
                        signature,
                    )
                    callIndex++
                    textContent = textContent.replace(match.value, "")
                    return@forEach
                }
                providerCallSignatures[providerIdentity] = signature
            }
            val callId =
                if (providerCallId != null) {
                    providerCallId
                } else {
                    val toolNamePart = sanitizeToolCallId(toolName)
                    val hashPart = stableIdHashPart("${toolName}:${argumentsJson}")
                    sanitizeToolCallId("call_${toolNamePart}_${hashPart}_$callIndex")
                }
            toolCalls.put(JSONObject().apply {
                put("id", callId)
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", argumentsJson)
                })
            })

            callIndex++

            // 从文本内容中移除tool标签
            textContent = textContent.replace(match.value, "")
        }

        return Pair(textContent.trim(), toolCalls)
    }

    protected fun extractXmlAttribute(
        openingTag: String,
        attributeName: String,
    ): String? {
        if (openingTag.isEmpty()) {
            return null
        }
        val match =
            Regex("""\b${Regex.escape(attributeName)}="([^"]*)"""")
                .find(openingTag)
                ?: return null
        return XmlEscaper.unescape(match.groupValues[1]).takeIf { it.isNotBlank() }
    }

    /**
     * 解析带 provider identity 的 tool_result。DeepSeek Chat 要求 tool_call_id 与原始
     * assistant tool call 一致；缺少 provider_call_id 时返回 null，由调用方按已验证的
     * provider 顺序匹配，而不是生成一个新的 ID。
     */
    internal fun parseXmlToolResultRecords(
        content: String,
    ): Pair<String, List<ProviderToolResultRecord>?> {
        val matches = ChatMarkupRegex.toolResultTagWithAttrs.findAll(content).toList()
        if (matches.isEmpty()) {
            return Pair(content, null)
        }

        val results = mutableListOf<ProviderToolResultRecord>()
        var textContent = content
        matches.forEach { match ->
            val attributes = match.groupValues[2]
            val fullContent = match.groupValues[3].trim()
            val contentMatch = ChatMarkupRegex.contentTag.find(fullContent)
            val resultContent = contentMatch?.groupValues?.get(1)?.trim() ?: fullContent
            val callId = extractXmlAttribute(attributes, "provider_call_id")
            // `name` is the display name of a proxied invocation. The provider protocol identity
            // is persisted separately so DeepSeek can match package_proxy results before submit.
            val toolName =
                extractXmlAttribute(attributes, "provider_tool_name")
                    ?: extractXmlAttribute(attributes, "name")
            results +=
                ProviderToolResultRecord(
                    callId = callId,
                    toolName = toolName,
                    content = resultContent,
                )
            textContent = textContent.replace(match.value, "").trim()
        }

        return Pair(textContent.trim(), results)
    }

    /**
     * 解析XML格式的tool_result，转换为OpenAI Tool消息格式
     * @return List<Pair<tool_call_id, result_content>>
     */
    fun parseXmlToolResults(content: String): Pair<String, List<Pair<String, String>>?> {
        // 匹配带属性的tool_result标签，例如: <tool_result name="..." status="...">...</tool_result>
        val matches = ChatMarkupRegex.toolResultAnyPattern.findAll(content)

        if (!matches.any()) {
            return Pair(content, null)
        }

        val results = mutableListOf<Pair<String, String>>()
        var textContent = content
        var resultIndex = 0

        matches.forEach { match ->
            // 提取<content>标签内的内容，如果有的话
            val fullContent = match.groupValues[2].trim()
            val contentMatch = ChatMarkupRegex.contentTag.find(fullContent)
            val resultContent = if (contentMatch != null) {
                contentMatch.groupValues[1].trim()
            } else {
                fullContent
            }

            // 生成一个tool_call_id（这里需要与之前的call对应，但因为历史记录可能不完整，我们使用索引）
            results.add(Pair("call_result_${resultIndex}", resultContent))

            // 从文本内容中移除tool_result标签（包括前后的空白符）
            textContent = textContent.replace(match.value, "").trim()
            resultIndex++
        }

        // trim 确保移除所有空白字符
        return Pair(textContent.trim(), results)
    }

    // 创建请求
    private suspend fun createRequest(
        requestBody: RequestBody,
        requestTraceId: String,
        stream: Boolean,
        attemptNumber: Int,
        localExecutionId: String? = null,
    ): Request {
        val currentApiKey = apiKeyProvider.getApiKey().trim()
        val endpointUrl = EndpointCompleter.completeEndpoint(apiEndpoint, endpointProtocol)
        val requestSummary = LlmLogPrivacy.summarizeRequestBody(requestBody)
        val traceContext =
            LlmRequestTraceContext(
                requestId = requestTraceId,
                provider = providerType.name,
                model = modelName,
                stream = stream,
                attempt = attemptNumber,
                endpointLabel = endpointUrl.substringBefore('?'),
                localExecutionId = localExecutionId,
                requestSummary = requestSummary,
            )
        val builder = Request.Builder()
            .url(endpointUrl)
            .tag(LlmRequestTraceContext::class.java, traceContext)
            .addHeader("Content-Type", "application/json")

        applyAuthenticationHeaders(builder, currentApiKey)

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }
        applyResponsesAcceptHeader(builder, stream)

        val request = builder.post(requestBody).build()
        AppLogger.d(
            "AIService",
            "[req=$requestTraceId] Request trace summary: provider=${traceContext.provider}, " +
                "model=${traceContext.model}, stream=$stream, attempt=$attemptNumber, " +
                "endpoint=${traceContext.endpointLabel}, ${requestSummary.format()}"
        )
        return request
    }

    private suspend fun createResponsesResumeRequest(
        responseId: String,
        startingAfter: Long,
        requestTraceId: String,
        attemptNumber: Int,
        localExecutionId: String? = null,
    ): Request {
        require(responseId.isNotBlank()) { "responseId must not be blank" }
        require(startingAfter >= 0L) { "startingAfter must not be negative" }
        val currentApiKey = apiKeyProvider.getApiKey().trim()
        val endpointUrl = EndpointCompleter.completeEndpoint(apiEndpoint, endpointProtocol)
        val resumeUrl =
            OpenAIResponsesResumeUrl.build(
                responsesEndpoint = endpointUrl,
                responseId = responseId,
                startingAfter = startingAfter,
            )
        val traceContext =
            LlmRequestTraceContext(
                requestId = requestTraceId,
                provider = providerType.name,
                model = modelName,
                stream = true,
                attempt = attemptNumber,
                endpointLabel = resumeUrl.newBuilder().query(null).build().toString(),
                localExecutionId = localExecutionId,
            )
        val builder =
            Request.Builder()
                .url(resumeUrl)
                .get()
                .tag(LlmRequestTraceContext::class.java, traceContext)

        applyAuthenticationHeaders(builder, currentApiKey)
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }
        applyResponsesAcceptHeader(builder, stream = true)
        val request = builder.build()
        AppLogger.d(
            "AIService",
            "[req=$requestTraceId] Resume response=${responseId.takeLast(12)}, " +
                "startingAfter=$startingAfter, attempt=$attemptNumber",
        )
        return request
    }

    /**
     * Responses 的默认内容协商必须与 stream wire 一致。显式自定义 Accept 仍由用户持有，避免
     * 在同一个请求里追加两个相互冲突的值。
     */
    private fun applyResponsesAcceptHeader(
        builder: Request.Builder,
        stream: Boolean,
    ) {
        if (!useResponsesApi || customHeaders.keys.any { it.equals("Accept", ignoreCase = true) }) {
            return
        }
        builder.header(
            "Accept",
            if (stream) "text/event-stream" else "application/json",
        )
    }

    private suspend fun cancelResponsesExecution(responseId: String) {
        require(responseId.isNotBlank()) { "responseId must not be blank" }
        val currentApiKey = apiKeyProvider.getApiKey().trim()
        val endpointUrl = EndpointCompleter.completeEndpoint(apiEndpoint, endpointProtocol)
        val cancelUrl =
            endpointUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment(responseId)
                .addPathSegment("cancel")
                .build()
        val builder =
            Request.Builder()
                .url(cancelUrl)
                .post("{}".toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")

        applyAuthenticationHeaders(builder, currentApiKey)
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        val cancelCall = client.newCall(builder.build())
        withContext(Dispatchers.IO) {
            cancelCall.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    val errorSummary = LlmLogPrivacy.summarizeProviderError(errorBody)
                    throw OpenAiHttpResponseException(
                        message =
                            "Responses cancel failed with HTTP ${response.code}: " +
                                errorSummary.exceptionDetail(),
                        statusCode = response.code,
                    )
                }
            }
        }
    }

    private fun requestBodySha256(requestBody: RequestBody): String {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return MessageDigest
            .getInstance("SHA-256")
            .digest(buffer.readByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private suspend fun recordProviderAttemptAudit(
        context: Context,
        request: Request,
        providerRequestContext: ProviderRequestContext?,
        eventType: String,
        summary: String,
        attemptNumber: Int,
        requestFingerprint: String?,
        submissionState: String? = null,
        retrySafety: String? = null,
        transport: LlmTransportDiagnostics? = null,
        providerCallId: String? = null,
        streamingState: StreamingState? = null,
        rollbackCharacters: Int? = null,
        failureCode: String? = null,
        throwable: Throwable? = null,
        terminalState: String? = null,
        completeness: ConversationAuditCompletenessStatus =
            ConversationAuditCompletenessStatus.IN_PROGRESS,
    ) {
        withContext(NonCancellable) {
            try {
                ConversationAuditProviderAttemptRecorder.record(
                    context = context,
                    requestContext = providerRequestContext,
                    eventType = eventType,
                    summary = summary,
                    provider = providerType.name,
                    model = modelName,
                    requestTraceId =
                        request.tag(LlmRequestTraceContext::class.java)?.requestId
                            ?: "unknown",
                    endpointLabel =
                        request.url.newBuilder().query(null).build().toString(),
                    method = request.method,
                    stream = request.tag(LlmRequestTraceContext::class.java)?.stream ?: false,
                    attemptNumber = attemptNumber,
                    requestFingerprint = requestFingerprint,
                    submissionState = submissionState,
                    retrySafety = retrySafety,
                    transport = transport,
                    providerCallId = providerCallId ?: streamingState?.remoteResponseId,
                    chunkCount = streamingState?.chunkCount,
                    receivedCharacters = streamingState?.let { state ->
                        state.reasoningCharacterCount + state.visibleCharacterCount
                    },
                    reasoningCharacters = streamingState?.reasoningCharacterCount,
                    visibleCharacters = streamingState?.visibleCharacterCount,
                    rollbackCharacters = rollbackCharacters,
                    failureCode = failureCode,
                    throwable = throwable,
                    terminalState = terminalState,
                    completeness = completeness,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (auditError: Exception) {
                AppLogger.e(
                    "AIService",
                    "Provider attempt 审计写入失败: eventType=$eventType",
                    auditError,
                )
            }
        }
    }

    private fun submissionStateFor(
        diagnostics: LlmTransportDiagnostics?,
    ): String =
        when {
            diagnostics == null -> "UNKNOWN"
            diagnostics.responseBodyStarted -> "RESPONSE_BODY_STARTED"
            diagnostics.responseHeadersReceived -> "RESPONSE_HEADERS_RECEIVED"
            diagnostics.requestBodyStarted || diagnostics.requestBodyBytes >= 0L ->
                "REQUEST_BODY_SENT"
            diagnostics.stage == LlmTransportStage.DNS_LOOKUP ||
                diagnostics.stage == LlmTransportStage.CONNECTING ||
                diagnostics.stage == LlmTransportStage.TLS_HANDSHAKE -> "NOT_SUBMITTED"
            else -> "SUBMISSION_UNKNOWN"
        }

    private fun isSafePreSubmissionRetry(
        diagnostics: LlmTransportDiagnostics?,
    ): Boolean =
        diagnostics != null &&
            !diagnostics.requestBodyStarted &&
            !diagnostics.responseHeadersReceived &&
            !diagnostics.responseBodyStarted &&
            (
                diagnostics.stage in
                    setOf(
                        LlmTransportStage.DNS_LOOKUP,
                        LlmTransportStage.CONNECTING,
                        LlmTransportStage.TLS_HANDSHAKE,
                    ) || diagnostics.connectionFailed
            )

    private fun retrySafetyForFailure(
        diagnostics: LlmTransportDiagnostics?,
    ): String =
        when {
            diagnostics == null -> "NO_RETRY_DIAGNOSTICS_UNAVAILABLE"
            diagnostics.responseBodyStarted || diagnostics.responseHeadersReceived ->
                "NO_RETRY_AFTER_SUBMISSION"
            diagnostics.requestBodyStarted || diagnostics.requestBodyBytes >= 0L ->
                "NO_RETRY_REQUEST_BODY_SENT"
            isSafePreSubmissionRetry(diagnostics) -> "SAFE_PRE_SUBMISSION_RETRY"
            else -> "NO_RETRY_SUBMISSION_UNKNOWN"
        }

    private fun wrapChatTransportFailure(
        failure: Exception,
        providerRequestContext: ProviderRequestContext?,
        diagnostics: LlmTransportDiagnostics?,
    ): Exception {
        if (failure is MessageFailureDiagnosticSource || providerRequestContext == null) {
            return failure
        }
        val ioFailure = failure as? IOException ?: return failure
        return OpenAIChatTransportFailure(
            localExecutionId = providerRequestContext.localExecutionId,
            transportDiagnostics = diagnostics,
            cause = ioFailure,
        )
    }

    private data class ResponsesPersistenceSession(
        val repository: OpenAIResponsesExecutionPersistence,
        val executionState: OpenAIResponsesExecutionState,
    )

    /**
     * 流式响应处理状态
     */
    private data class StreamingState(
        var chunkCount: Int = 0,
        var reasoningCharacterCount: Int = 0,
        var remoteResponseId: String? = null,
        var visibleCharacterCount: Int = 0,
        var lastLogTime: Long = System.currentTimeMillis(),
        var isInReasoningMode: Boolean = false,
        var hasEmittedThinkStart: Boolean = false,
        var hasEmittedRegularContent: Boolean = false,
        val streamedRegularContent: StringBuilder = StringBuilder(),
        val reasoningProjection: OpenAIResponsesReasoningProjection =
            OpenAIResponsesReasoningProjection(),
        var reasoningObserved: Boolean = false,
        var isFirstResponse: Boolean = true,
        val accumulatedToolCalls: MutableMap<Int, JSONObject> = mutableMapOf(),
        val toolCallState: ToolCallState = ToolCallState(),
        var lastProcessedToolIndex: Int? = null,
        val imageBuffers: MutableMap<Int, ImageBufferState> = mutableMapOf(),
        var providerResponseId: String? = null,
        var hasCompletedResponsesTerminal: Boolean = false,
    )

    /**
     * 处理工具切换：关闭前一个工具
     */
    private suspend fun handleToolSwitch(
        prevIndex: Int,
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        if (state.toolCallState.closed[prevIndex] != true && 
            state.toolCallState.nameEmitted[prevIndex] == true) {
            closeToolCallIfOpen(prevIndex, state, emitter)
            AppLogger.d("AIService", "检测到工具切换，关闭前一个工具 index=$prevIndex")
        }
    }

    /**
     * 处理单个工具调用的增量数据
     */
    private suspend fun processToolCallChunk(
        index: Int,
        deltaCall: JSONObject,
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        // 获取或创建该index的累积对象
        val accumulated = state.accumulatedToolCalls.getOrPut(index) {
            createToolCallAccumulator(index)
        }

        // 更新id和type
        deltaCall.optString("id", "").trim().let {
            if (it.isNotEmpty()) {
                state.toolCallState.bindProviderCallId(index, it)
                accumulated.put("id", it)
            }
        }
        deltaCall.optString("type", "").let {
            if (it.isNotEmpty()) accumulated.put("type", it)
        }

        // 处理function字段
        val deltaFunction = deltaCall.optJSONObject("function") ?: return
        val accFunction = accumulated.getJSONObject("function")
        
        // 处理工具名
        val name = deltaFunction.optString("name", "")
        if (name.isNotEmpty()) {
            accFunction.put("name", name)
            // 流式输出开始标签
            if (state.toolCallState.nameEmitted[index] != true) {
                val toolTagName = state.toolCallState.getTagName(index)
                val toolStartTag = if (state.toolCallState.emitted[index] != true) {
                    state.toolCallState.emitted[index] = true
                    buildString {
                        append("\n<")
                        append(toolTagName)
                        append(" name=\"")
                        append(escapeXml(name))
                        append("\"")
                        append(" provider_name=\"")
                        append(escapeXml(providerType.name))
                        append("\"")
                        accumulated.optString("id", "").takeIf { it.isNotBlank() }?.let { callId ->
                            append(" provider_call_id=\"")
                            append(escapeXml(callId))
                            append("\"")
                        }
                        state.providerResponseId?.takeIf { it.isNotBlank() }?.let { responseId ->
                            append(" provider_response_id=\"")
                            append(escapeXml(responseId))
                            append("\"")
                        }
                        append(">")
                    }
                } else {
                    ""
                }
                if (toolStartTag.isNotEmpty()) {
                    emitter.emitTag(toolStartTag)
                }
                state.toolCallState.nameEmitted[index] = true

                // 如果参数先到，工具名后到，在此处一次性补喂已累计参数
                val canonicalArgs = accFunction.optString("arguments", "")
                if (canonicalArgs.isNotEmpty()) {
                    feedParserFromCanonical(index, canonicalArgs, state, emitter)
                }
            }
        }
        
        // 处理参数
        val args = deltaFunction.optString("arguments", "")
        if (args.isNotEmpty()) {
            val currentArgs = accFunction.optString("arguments", "")
            val mergedArgs = mergeCanonicalArgs(currentArgs, args)
            val changed = mergedArgs != currentArgs
            if (changed) {
                accFunction.put("arguments", mergedArgs)
                if (state.toolCallState.nameEmitted[index] == true) {
                    feedParserFromCanonical(index, mergedArgs, state, emitter)
                }
            }
        }
    }

    /**
     * 合并 tool arguments（单一路径）：
     * - 默认将 incoming 视为增量追加；
     * - 若 incoming 为前缀扩展快照（incoming startsWith existing），则直接替换为快照。
     */
    private fun mergeCanonicalArgs(existing: String, incoming: String): String {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming

        // 增量通道：默认直接追加；若供应商偶发回传完整快照，则直接切换为快照值。
        return if (incoming.startsWith(existing)) incoming else existing + incoming
    }

    /**
     * 基于 canonical arguments 与 fedLength 游标，向解析器仅喂入新增部分。
     */
    private suspend fun feedParserFromCanonical(
        index: Int,
        canonicalArgs: String,
        state: StreamingState,
        emitter: StreamEmitter
    ): Int {
        val previousFedLength = (state.toolCallState.fedLength[index] ?: 0).coerceAtLeast(0)
        val safeFedLength = previousFedLength.coerceAtMost(canonicalArgs.length)
        if (safeFedLength == canonicalArgs.length) {
            state.toolCallState.fedLength[index] = safeFedLength
            return 0
        }

        val deltaToFeed = canonicalArgs.substring(safeFedLength)
        val events = state.toolCallState.getParser(index).feed(deltaToFeed)
        emitter.handleJsonEvents(events)
        state.toolCallState.fedLength[index] = canonicalArgs.length
        return deltaToFeed.length
    }

    private fun getAccumulatedToolArguments(state: StreamingState, index: Int): String {
        return state.accumulatedToolCalls[index]
            ?.optJSONObject("function")
            ?.optString("arguments", "")
            ?: ""
    }

    /**
     * 处理工具调用的增量数据
     */
    private suspend fun processToolCallsDelta(
        toolCallsDeltas: JSONArray,
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        // 如果正在思考模式，收到工具调用时应先关闭思考标签
        if (state.isInReasoningMode) {
            state.isInReasoningMode = false
            emitter.emitTag("</think>")
            state.hasEmittedThinkStart = false
        }

        for (i in 0 until toolCallsDeltas.length()) {
            val deltaCall = toolCallsDeltas.getJSONObject(i)
            val index = deltaCall.optInt("index", -1)
            if (index < 0) continue

            // 检测工具切换
            if (state.lastProcessedToolIndex != null && state.lastProcessedToolIndex != index) {
                handleToolSwitch(state.lastProcessedToolIndex!!, state, emitter)
            }
            state.lastProcessedToolIndex = index

            // Chat Completions 的 tool_calls.arguments 为增量片段。
            processToolCallChunk(index, deltaCall, state, emitter)
        }
    }

    private suspend fun closeToolCallIfOpen(
        index: Int,
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        if (state.toolCallState.closed[index] == true || state.toolCallState.nameEmitted[index] != true) {
            return
        }

        val accumulatedArgsBeforeFlush = getAccumulatedToolArguments(state, index)
        val toolTagName =
            requireNotNull(state.toolCallState.tagNames[index]) {
                "Missing tool XML tag name for streaming tool call index=$index"
            }

        val parser = state.toolCallState.getParser(index)
        val events = parser.flush()
        emitter.handleJsonEvents(events)

        if (parser.hasUnfinishedParam()) {
            val parsedAsJson = runCatching { JSONObject(accumulatedArgsBeforeFlush) }.isSuccess
            if (parsedAsJson) {
                AppLogger.w(
                    "AIService",
                    "Tool 参数解析器状态未闭合，但累计 arguments 已是合法 JSON，强制补全标签收尾，index=$index"
                )
                emitter.emitTag("</param>")
                emitter.emitTag("\n</$toolTagName>")
                state.toolCallState.closed[index] = true
                return
            }

            AppLogger.w(
                "AIService",
                "检测到未完成的 tool 参数，跳过自动补 </tool>，index=$index, argsLen=${accumulatedArgsBeforeFlush.length}"
            )
            return
        }

        emitter.emitTag("\n</$toolTagName>")
        state.toolCallState.closed[index] = true
    }

    private fun hasOpenToolCalls(state: StreamingState): Boolean {
        return state.toolCallState.nameEmitted.any { (index, emitted) ->
            emitted && state.toolCallState.closed[index] != true
        }
    }

    private suspend fun closeAllOpenToolCalls(
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        if (!hasOpenToolCalls(state)) return

        val sortedIndices = state.accumulatedToolCalls.keys.sorted()
        for (index in sortedIndices) {
            closeToolCallIfOpen(index, state, emitter)
        }
    }

    private fun hasResponsesReasoningItem(responseObj: JSONObject?): Boolean {
        val output = responseObj?.optJSONArray("output") ?: return false
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type", "") == "reasoning") {
                return true
            }
        }
        return false
    }

    private fun extractResponsesReasoningText(responseObj: JSONObject?): String {
        val output = responseObj?.optJSONArray("output") ?: return ""
        val chunks = mutableListOf<String>()

        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type", "") != "reasoning") {
                continue
            }

            val itemText = extractResponsesReasoningTextFromItem(item)
            if (itemText.isNotEmpty()) {
                chunks.add(itemText)
            }
        }

        return chunks.joinToString("\n\n")
    }

    private fun extractResponsesReasoningTextFromItem(item: JSONObject): String {
        val chunks = mutableListOf<String>()

        val summaryArray = item.optJSONArray("summary")
        if (summaryArray != null) {
            for (i in 0 until summaryArray.length()) {
                val summaryPart = summaryArray.optJSONObject(i) ?: continue
                val text = summaryPart.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    chunks.add(text)
                }
            }
        }

        val contentArray = item.optJSONArray("content")
        if (contentArray != null) {
            for (i in 0 until contentArray.length()) {
                val contentPart = contentArray.optJSONObject(i) ?: continue
                val text = contentPart.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    chunks.add(text)
                }
            }
        }

        return chunks.joinToString("\n\n")
    }

    private suspend fun reconcileCompletedResponsesOutput(
        responseObj: JSONObject?,
        state: StreamingState,
        emitter: StreamEmitter,
    ) {
        responseObj ?: return
        val parsed = OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(responseObj)
        val terminalText = parsed.textChunks.joinToString("")
        val missingText =
            OpenAIResponsesTerminalSnapshot.missingTextSuffix(
                streamedText = state.streamedRegularContent.toString(),
                terminalText = terminalText,
            )
        if (missingText.isNotEmpty()) {
            processContentDelta("", missingText, state, emitter)
        }

        if (!enableToolCall) {
            return
        }
        val terminalOutput = responseObj.optJSONArray("output") ?: return
        for (terminalOutputIndex in 0 until terminalOutput.length()) {
            val item = terminalOutput.optJSONObject(terminalOutputIndex) ?: continue
            if (item.optString("type", "") != "function_call") {
                continue
            }

            val callId = item.optString("call_id", item.optString("id", "")).trim()
            val index =
                state.toolCallState.resolveTerminalIndex(
                    terminalOutputIndex = terminalOutputIndex,
                    callId = callId,
                )
            val accumulated = state.accumulatedToolCalls[index]
            val existingFunction = accumulated?.optJSONObject("function")
            val existingName = existingFunction?.optString("name", "").orEmpty()
            val terminalName = item.optString("name", "")
            val diagnosticCallId = callId.ifBlank { "output_index_$index" }
            OpenAIResponsesTerminalSnapshot.requireCompatibleToolName(
                callId = diagnosticCallId,
                streamedName = existingName,
                terminalName = terminalName,
            )

            val existingArguments = existingFunction?.optString("arguments", "").orEmpty()
            val terminalArguments = item.optString("arguments", "")
            val argumentsUpdate =
                OpenAIResponsesTerminalSnapshot.toolArgumentsUpdate(
                    callId = diagnosticCallId,
                    streamedArguments = existingArguments,
                    terminalArguments = terminalArguments,
                )
            if (state.toolCallState.closed[index] == true) {
                if (argumentsUpdate.isNotEmpty()) {
                    throw OpenAIResponsesProtocolException(
                        "Responses tool call $diagnosticCallId received terminal arguments " +
                            "after its XML projection was closed"
                    )
                }
                continue
            }

            val completedCall =
                JSONObject().apply {
                    put("index", index)
                    if (callId.isNotEmpty()) {
                        put("id", callId)
                    }
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            if (terminalName.isNotEmpty()) {
                                put("name", terminalName)
                            }
                            if (argumentsUpdate.isNotEmpty()) {
                                put("arguments", argumentsUpdate)
                            }
                        }
                    )
                }
            processToolCallChunk(index, completedCall, state, emitter)
            state.lastProcessedToolIndex = index
        }
    }

    private suspend fun processResponsesStreamingEvent(
        context: Context,
        jsonResponse: JSONObject,
        state: StreamingState,
        emitter: StreamEmitter,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit
    ) {
        val eventType = jsonResponse.optString("type", "")

        val observedResponseId = jsonResponse.optJSONObject("response")
            ?.optString("id", "")?.takeIf { it.isNotBlank() }
            ?: jsonResponse.optString("response_id", "").takeIf { it.isNotBlank() }
        if (observedResponseId != null) {
            if (state.remoteResponseId != null && state.remoteResponseId != observedResponseId) {
                throw OpenAIResponsesProtocolException("Responses stream changed response identity")
            }
            state.remoteResponseId = observedResponseId
        }

        if (eventType.startsWith("response.image_generation_call.")) {
            val normalized = JSONObject(jsonResponse.toString())
            normalized.put(
                "type",
                eventType.removePrefix("response.").replace("image_generation_call.", "image_generation.")
            )
            if (tryHandleOpenAiImageResponse(normalized, emitter, state)) {
                return
            }
        }

        if (
            OpenAIResponsesReasoningEventPolicy.isReasoningLifecycleEvent(
                eventType = eventType,
                payload = jsonResponse,
            )
        ) {
            state.reasoningObserved = true
            ensureResponsesReasoningProjectionStarted(state, emitter)
        }

        when (eventType) {
            "response.created",
            "response.queued",
            "response.in_progress",
            -> Unit

            "response.reasoning_summary_part.added" -> Unit

            "response.output_text.delta" -> {
                val delta = jsonResponse.optString("delta", "")
                if (delta.isNotEmpty()) {
                    processContentDelta("", delta, state, emitter)
                }
            }

            "response.reasoning_text.delta", "response.reasoning_summary_text.delta" -> {
                val delta = jsonResponse.optString("delta", "")
                val projectedText =
                    state.reasoningProjection.acceptDelta(
                        partKey =
                            OpenAIResponsesReasoningEventPolicy.partKey(
                                eventType = eventType,
                                payload = jsonResponse,
                            ),
                        outputIndex = jsonResponse.optInt("output_index", -1),
                        delta = delta,
                    )
                if (projectedText.isNotEmpty()) {
                    processContentDelta(projectedText, "", state, emitter)
                }
            }

            "response.reasoning_text.done", "response.reasoning_summary_text.done" -> {
                val text = jsonResponse.optString("text", "")
                val projectedText =
                    state.reasoningProjection.acceptCompletedPart(
                        partKey =
                            OpenAIResponsesReasoningEventPolicy.partKey(
                                eventType = eventType,
                                payload = jsonResponse,
                            ),
                        outputIndex = jsonResponse.optInt("output_index", -1),
                        completedText = text,
                    )
                if (projectedText.isNotEmpty()) {
                    emitCompletedResponsesReasoningText(projectedText, state, emitter)
                }
            }

            "response.reasoning_summary_part.done" -> {
                val part = jsonResponse.optJSONObject("part")
                val text = part?.optString("text", "") ?: ""
                val projectedText =
                    state.reasoningProjection.acceptCompletedPart(
                        partKey =
                            OpenAIResponsesReasoningEventPolicy.partKey(
                                eventType = eventType,
                                payload = jsonResponse,
                            ),
                        outputIndex = jsonResponse.optInt("output_index", -1),
                        completedText = text,
                    )
                if (projectedText.isNotEmpty()) {
                    emitCompletedResponsesReasoningText(projectedText, state, emitter)
                }
            }

            "response.output_item.added", "response.output_item.done" -> {
                val outputIndex = jsonResponse.optInt("output_index", -1)
                val item = jsonResponse.optJSONObject("item")
                if (outputIndex < 0 || item == null) {
                    return
                }

                if (item.optString("type", "") == "reasoning") {
                    val itemReasoningText =
                        if (eventType == "response.output_item.done") {
                            extractResponsesReasoningTextFromItem(item)
                        } else {
                            ""
                        }
                    val projectedText =
                        if (eventType == "response.output_item.done") {
                            state.reasoningProjection.acceptCompletedOutputItem(
                                outputIndex = outputIndex,
                                completedText = itemReasoningText,
                            )
                        } else {
                            ""
                        }
                    if (projectedText.isNotEmpty()) {
                        emitCompletedResponsesReasoningText(projectedText, state, emitter)
                    }
                    if (eventType == "response.output_item.done") {
                        OpenAIResponsesPayloadAdapter.createReasoningMetadataTag(item)?.let { metadataTag ->
                            if (state.isInReasoningMode) {
                                state.isInReasoningMode = false
                                emitter.emitTag("</think>")
                                state.hasEmittedThinkStart = false
                            }
                            emitter.emitTag(metadataTag)
                        }
                    }
                    return
                }

                if (!enableToolCall || item.optString("type", "") != "function_call") {
                    return
                }

                val functionObj = JSONObject().apply {
                    val name = item.optString("name", "")
                    if (name.isNotEmpty()) {
                        put("name", name)
                    }
                }

                val deltaCall = JSONObject().apply {
                    put("index", outputIndex)
                    val callId = item.optString("call_id", item.optString("id", ""))
                    if (callId.isNotEmpty()) {
                        put("id", callId)
                    }
                    put("type", "function")
                    put("function", functionObj)
                }

                // 对于 output_item 事件，仅更新工具元信息（name/id）。
                // 参数统一由 response.function_call_arguments.delta 通道累积，避免快照+增量混拼导致 JSON 破坏。
                processToolCallChunk(outputIndex, deltaCall, state, emitter)
                state.lastProcessedToolIndex = outputIndex
                // 某些供应商会先发送 output_item.done，随后才发送 function_call_arguments.delta。
                // 因此不在 output_item.done 阶段关闭工具调用，改由
                // response.function_call_arguments.done / response.completed 统一收口。
            }

            "response.function_call_arguments.delta" -> {
                if (!enableToolCall) return
                val outputIndex = jsonResponse.optInt("output_index", -1)
                if (outputIndex < 0) return

                val deltaCall = JSONObject().apply {
                    put("index", outputIndex)
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            val name = jsonResponse.optString("name", "")
                            if (name.isNotEmpty()) {
                                put("name", name)
                            }
                            val delta = jsonResponse.optString("delta", "")
                            if (delta.isNotEmpty()) {
                                put("arguments", delta)
                            }
                        }
                    )
                }

                processToolCallChunk(outputIndex, deltaCall, state, emitter)
                state.lastProcessedToolIndex = outputIndex
            }

            "response.function_call_arguments.done" -> {
                if (!enableToolCall) return
                val outputIndex = jsonResponse.optInt("output_index", -1)
                if (outputIndex >= 0) {
                    val accumulated = state.accumulatedToolCalls[outputIndex]
                    val existingFunction = accumulated?.optJSONObject("function")
                    val existingName = existingFunction?.optString("name", "").orEmpty()
                    val completedName = jsonResponse.optString("name", "")
                    val callId =
                        jsonResponse.optString("call_id", "").trim().ifBlank {
                            state.toolCallState.providerCallIdByIndex[outputIndex].orEmpty()
                        }
                    val diagnosticCallId = callId.ifBlank { "output_index_$outputIndex" }
                    OpenAIResponsesTerminalSnapshot.requireCompatibleToolName(
                        callId = diagnosticCallId,
                        streamedName = existingName,
                        terminalName = completedName,
                    )

                    val existingArguments =
                        existingFunction?.optString("arguments", "").orEmpty()
                    val completedArguments = jsonResponse.optString("arguments", "")
                    val argumentsUpdate =
                        OpenAIResponsesTerminalSnapshot.toolArgumentsUpdate(
                            callId = diagnosticCallId,
                            streamedArguments = existingArguments,
                            terminalArguments = completedArguments,
                        )
                    if (
                        callId.isNotEmpty() ||
                            completedName.isNotEmpty() ||
                            argumentsUpdate.isNotEmpty()
                    ) {
                        // arguments.done 是该工具参数的最终快照。若兼容 endpoint 没有发送
                        // delta，必须先把完整参数交给同一个增量解析器，再关闭 XML 标签。
                        val completedCall =
                            JSONObject().apply {
                                put("index", outputIndex)
                                if (callId.isNotEmpty()) {
                                    put("id", callId)
                                }
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        if (completedName.isNotEmpty()) {
                                            put("name", completedName)
                                        }
                                        if (argumentsUpdate.isNotEmpty()) {
                                            put("arguments", argumentsUpdate)
                                        }
                                    }
                                )
                            }
                        processToolCallChunk(outputIndex, completedCall, state, emitter)
                    }
                    closeToolCallIfOpen(outputIndex, state, emitter)
                    state.lastProcessedToolIndex = outputIndex
                }
            }

            "response.completed" -> {
                val responseObj = jsonResponse.optJSONObject("response")
                val usage = responseObj?.optJSONObject("usage")
                val reasoningTokens =
                    usage
                        ?.optJSONObject("output_tokens_details")
                        ?.optInt("reasoning_tokens", 0)
                        ?: 0
                if (reasoningTokens > 0 || hasResponsesReasoningItem(responseObj)) {
                    state.reasoningObserved = true
                }

                val lateReasoningText = extractResponsesReasoningText(responseObj)
                val projectedReasoningText =
                    state.reasoningProjection.acceptTerminalSnapshot(lateReasoningText)
                if (projectedReasoningText.isNotEmpty()) {
                    emitCompletedResponsesReasoningText(
                        projectedReasoningText,
                        state,
                        emitter,
                    )
                }

                if (state.isInReasoningMode) {
                    state.isInReasoningMode = false
                    emitter.emitTag("</think>")
                    state.hasEmittedThinkStart = false
                }

                // completed 携带完整 response 快照。部分兼容网关会省略中间文本或 function_call
                // 事件，因此这里只补齐尚未发出的尾部，不撤回或重复已经确认的内容。
                reconcileCompletedResponsesOutput(responseObj, state, emitter)
                closeAllOpenToolCalls(state, emitter)
                applyUsageToCounters(usage, onTokensUpdated)
                state.hasCompletedResponsesTerminal = true
            }

            "response.failed", "response.error" -> {
                val error = jsonResponse.optJSONObject("error")
                val responseObj = jsonResponse.optJSONObject("response")
                val errorMessage =
                    error?.optString("message", "")
                        ?.takeIf { it.isNotBlank() }
                        ?: responseObj?.optJSONObject("error")
                            ?.optString("message", "")
                            ?.takeIf { it.isNotBlank() }
                        ?: responseObj?.optString("status", "")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "Responses stream failed with status: $it" }
                        ?: "Responses stream returned $eventType"

                val errorSummary =
                    LlmLogPrivacy.summarizeProviderError(
                        JSONObject()
                            .put(
                                "error",
                                JSONObject().put("message", errorMessage),
                            )
                            .toString()
                    )
                AppLogger.w(
                    "AIService",
                    "Responses流式事件错误: ${errorSummary.format()}"
                )
                throw OpenAIResponsesTerminalException(
                    eventType = eventType,
                    message =
                    context.getString(
                        R.string.openai_error_response_failed,
                        errorSummary.exceptionDetail(),
                    )
                )
            }

            "response.incomplete", "response.cancelled" -> {
                val responseObj = jsonResponse.optJSONObject("response")
                val details =
                    responseObj
                        ?.optJSONObject("incomplete_details")
                        ?.optString("reason", "")
                        ?.takeIf { it.isNotBlank() }
                        ?: responseObj
                            ?.optJSONObject("error")
                            ?.optString("message", "")
                            ?.takeIf { it.isNotBlank() }
                        ?: responseObj?.optString("status", "")
                            ?.takeIf { it.isNotBlank() }
                        ?: eventType
                closeAllOpenToolCalls(state, emitter)
                throw OpenAIResponsesTerminalException(
                    eventType = eventType,
                    message = context.getString(R.string.openai_error_response_failed, details)
                )
            }
        }
    }

    private suspend fun persistResponsesEventBeforeEmission(
        payload: JSONObject,
        session: ResponsesPersistenceSession,
        streamingState: StreamingState,
    ): Boolean {
        val executionState = session.executionState
        val event = executionState.parseEvent(payload)
        if (event.sequenceNumber > executionState.lastAppliedSequence) {
            executionState.applyNewEvent(event, payload)
        }
        val now = System.currentTimeMillis()
        val outcome =
            session.repository.appendEvent(
                localExecutionId = executionState.localExecutionId,
                remoteResponseId = event.remoteResponseId,
                sequenceNumber = event.sequenceNumber,
                eventType = event.type,
                payloadJson = event.payloadJson,
                nextMessageState = executionState.toMessageProviderState(now),
                terminalEventType = executionState.terminalEventType,
                lastErrorCode = executionState.lastErrorCode,
                lastErrorMessage = executionState.lastErrorMessage,
                completedAt = now.takeIf { executionState.isTerminal },
                receivedAt = now,
            )
        if (outcome is ProviderEventAppendOutcome.Duplicate) {
            return false
        }
        streamingState.providerResponseId = event.remoteResponseId
        return true
    }

    /**
     * 处理完成原因
     */
    private suspend fun handleFinishReason(
        finishReason: String,
        state: StreamingState,
        emitter: StreamEmitter,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit
    ) {
        val normalizedFinishReason = finishReason.trim()
        if (normalizedFinishReason.isEmpty() ||
            normalizedFinishReason.equals("null", ignoreCase = true) ||
            normalizedFinishReason.equals("none", ignoreCase = true)
        ) {
            return
        }

        if (hasOpenToolCalls(state)) {
            closeAllOpenToolCalls(state, emitter)
            AppLogger.d("AIService", "Tool Call流式收尾，finish_reason=$normalizedFinishReason")

            onTokensUpdated(
                tokenCacheManager.totalInputTokenCount,
                tokenCacheManager.cachedInputTokenCount,
                tokenCacheManager.outputTokenCount
            )

            // 清空累积器
            state.accumulatedToolCalls.clear()
            state.lastProcessedToolIndex = null
        }
    }

    /**
     * 处理内容增量（思考和常规内容）
     */
    private suspend fun processContentDelta(
        reasoningContent: String,
        regularContent: String,
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        val hasReasoning = reasoningContent.isNotNullOrEmpty()
        val hasRegular = regularContent.isNotNullOrEmpty()

        // 处理思考内容
        if (hasReasoning && !state.hasEmittedRegularContent) {
            if (!state.isInReasoningMode) {
                state.isInReasoningMode = true
                if (!state.hasEmittedThinkStart) {
                    emitter.emitTag("<think>")
                    state.hasEmittedThinkStart = true
                }
            }
            emitter.emitContent(reasoningContent, reasoning = true)
        }
        // 处理常规内容
        if (hasRegular) {
            // 如果之前在思考模式，现在切换到了常规内容，需要关闭思考标签
            if (state.isInReasoningMode) {
                state.isInReasoningMode = false
                emitter.emitTag("</think>")
                state.hasEmittedThinkStart = false
            }

            // 硬切策略：正文一旦开始输出，后续到达的推理内容全部忽略
            state.hasEmittedRegularContent = true
            state.streamedRegularContent.append(regularContent)

            // 当收到第一个有效内容时，标记不再是首次响应
            if (state.isFirstResponse) {
                state.isFirstResponse = false
                AppLogger.d("AIService", "【发送消息】收到首个有效内容片段")
            }

            emitter.emitContent(regularContent)
        }
    }

    private suspend fun emitCompletedResponsesReasoningText(
        reasoningText: String,
        state: StreamingState,
        emitter: StreamEmitter
    ) {
        if (state.hasEmittedRegularContent) {
            emitter.emitThinkContent(reasoningText)
        } else {
            processContentDelta(reasoningText, "", state, emitter)
        }
    }

    private suspend fun ensureResponsesReasoningProjectionStarted(
        state: StreamingState,
        emitter: StreamEmitter,
    ) {
        if (state.hasEmittedRegularContent || state.isInReasoningMode) {
            return
        }
        state.isInReasoningMode = true
        if (!state.hasEmittedThinkStart) {
            emitter.emitTag("<think>")
            state.hasEmittedThinkStart = true
        }
    }

    /**
     * 处理单个响应块
     */
    private suspend fun processResponseChunk(
        jsonResponse: JSONObject,
        state: StreamingState,
        emitter: StreamEmitter,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit
    ) {
        val usage = jsonResponse.optJSONObject("usage")
        val choices = jsonResponse.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            applyUsageToCounters(usage, onTokensUpdated)
            return
        }

        val choice = choices.getJSONObject(0)

        // 处理delta格式（流式响应）
        val delta = choice.optJSONObject("delta")
        if (delta != null) {
            val finishReason =
                if (choice.has("finish_reason") && !choice.isNull("finish_reason")) {
                    choice.optString("finish_reason", "").trim()
                } else {
                    ""
                }

            // 处理工具调用
            val toolCallsDeltas = delta.optJSONArray("tool_calls")
            if (toolCallsDeltas != null && toolCallsDeltas.length() > 0 && enableToolCall) {
                processToolCallsDelta(toolCallsDeltas, state, emitter)
            }

            // 处理完成原因
            if (finishReason.isNotEmpty()) {
                handleFinishReason(finishReason, state, emitter, onTokensUpdated)
            }

            // 处理内容
            val reasoningContent = delta.optString("reasoning_content", "").ifBlank {
                delta.optString("reasoning", "")
            }
            val regularContent = delta.optString("content", "")
            processContentDelta(reasoningContent, regularContent, state, emitter)
        }
        // 处理message格式（非流式响应）
        else {
            val message = choice.optJSONObject("message")
            if (message != null) {
                val reasoningContent = message.optString("reasoning_content", "").ifBlank {
                    message.optString("reasoning", "")
                }
                val regularContent = message.optString("content", "")

                // 先处理思考内容（如果有）
                if (reasoningContent.isNotNullOrEmpty() && !state.hasEmittedRegularContent) {
                    emitter.emitThinkContent(reasoningContent)
                }
                // 然后处理常规内容
                if (regularContent.isNotNullOrEmpty()) {
                    state.hasEmittedRegularContent = true
                    emitter.emitContent(regularContent)
                }
            }
        }

        applyUsageToCounters(usage, onTokensUpdated)
    }

    /**
     * 处理流式响应
     */
    private suspend fun processStreamingResponse(
        reader: java.io.BufferedReader,
        emitter: StreamEmitter,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        context: Context,
        session: ProviderStreamSessionGate.Session,
        state: StreamingState = StreamingState(),
        responsesPersistenceSession: ResponsesPersistenceSession? = null,
    ) {
        try {
            // 使用 while 循环读取流式响应
            val eventReader = ServerSentEventReader(reader)
            while (true) {
                checkCancellation(context, session)
                val data = eventReader.readData()?.trim() ?: break
                if (data.isEmpty()) continue
                if (data == "[DONE]") {
                    flushImageBuffers(state, emitter)
                    closeAllOpenToolCalls(state, emitter)
                    if (state.isInReasoningMode) {
                        state.isInReasoningMode = false
                        emitter.emitTag("</think>")
                        state.hasEmittedThinkStart = false
                    }
                    AppLogger.d("AIService", "【发送消息】收到流结束标记[DONE]")
                    break
                }

                state.chunkCount++
                // 每10个块或500ms记录一次日志
                val currentTime = System.currentTimeMillis()
                if (state.chunkCount % 10 == 0 || currentTime - state.lastLogTime > 500) {
                    state.lastLogTime = currentTime
                }

                try {
                    val jsonResponse = try {
                        JSONObject(data)
                    } catch (malformed: org.json.JSONException) {
                        // 若尾事件也被网络截断，保留真实传输原因；只有完整连接上的坏 JSON
                        // 才归类为协议处理错误，不能让缓冲尾部交付掩盖原始 EOF。
                        throw eventReader.pendingReadFailure ?: malformed
                    }
                    throwIfOpenAiErrorPayload(context, jsonResponse)

                    if (useResponsesApi) {
                        if (
                            responsesPersistenceSession != null &&
                                !persistResponsesEventBeforeEmission(
                                    payload = jsonResponse,
                                    session = responsesPersistenceSession,
                                    streamingState = state,
                                )
                        ) {
                            continue
                        }
                        processResponsesStreamingEvent(context, jsonResponse, state, emitter, onTokensUpdated)
                        // response.completed 已是终态；不能再等待中转关闭 socket 或额外 [DONE]，
                        // 否则后续 idle timeout 会把已完成回答误报为传输中断。
                        if (state.hasCompletedResponsesTerminal || responsesPersistenceSession?.executionState?.isTerminal == true) {
                            break
                        }
                        continue
                    }

                    if (!jsonResponse.has("choices")) {
                        val handled = tryHandleOpenAiImageResponse(jsonResponse, emitter, state)
                        if (handled) {
                            continue
                        }
                    }
                    processResponseChunk(jsonResponse, state, emitter, onTokensUpdated)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    if (useResponsesApi) {
                        throw OpenAIResponsesEventProcessingException(
                            message = "Responses event processing failed",
                            cause = e,
                        )
                    }
                    AppLogger.w(
                        "AIService",
                        "【发送消息】JSON解析错误: ${e.javaClass.simpleName}; " +
                            LlmLogPrivacy.summarizeText(data).format()
                    )
                }
            }

            val persistedResponsesState = responsesPersistenceSession?.executionState
            if (persistedResponsesState != null && !persistedResponsesState.isTerminal) {
                val responseId =
                    persistedResponsesState.remoteResponseId
                        ?: throw OpenAIResponsesMissingTerminalException(
                            "Responses stream ended before response.created"
                        )
                throw OpenAIResponsesTransportInterruptedException(
                    responseId = responseId,
                    lastAppliedSequence = persistedResponsesState.lastAppliedSequence,
                    cause = IOException("Responses stream ended without a terminal event"),
                )
            }
            val responsesCompleted =
                state.hasCompletedResponsesTerminal ||
                    persistedResponsesState?.status == ProviderExecutionStatus.COMPLETED
            if (useResponsesApi && !responsesCompleted) {
                // Responses 没有 Chat Completions 的 [DONE] 成功合同。缺少 response.completed 时
                // 必须保留未知提交语义，否则干净 EOF 会被消息层错误写成 Completed。
                throw OpenAIResponsesMissingTerminalException(
                    "Responses stream ended without response.completed"
                )
            }
            
            closeAllOpenToolCalls(state, emitter)

            AppLogger.d(
                "AIService",
                "【发送消息】响应流处理完成，总块数: ${state.chunkCount}，输出token: ${tokenCacheManager.outputTokenCount}"
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消（外层 scope 取消），直接退出
            AppLogger.d("AIService", "【发送消息】协程已取消")
            throw e
        } catch (e: IOException) {
            // 捕获IO异常，可能是由于 response.close() 导致的取消，也可能是网络中断
            if (streamSessionGate.isCancelled(session)) {
                AppLogger.d("AIService", "【发送消息】流式传输已被用户取消")
                throw UserCancellationException(context.getString(R.string.openai_error_request_cancelled), e)
            } else {
                // 最终是否允许重试由外层 attempt 提交边界决定，避免在读取已提交响应后重复 POST。
                AppLogger.e("AIService", "【发送消息】流式读取时发生 IO 异常，交由 attempt 边界判定", e)
                throw e
            }
        } finally {
            runCatching { flushImageBuffers(state, emitter) }
            // 确保 reader 被关闭
            try {
                reader.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private suspend fun executeResumableResponsesStream(
        context: Context,
        session: ProviderStreamSessionGate.Session,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        providerRequestContext: ProviderRequestContext,
        emitChunk: suspend (String) -> Unit,
        eventChannel: MutableSharedStream<TextStreamEvent>,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
    ) {
        AppLogger.d(
            "AIService",
            "【Responses可恢复执行】准备编译请求，localExecution=${providerRequestContext.localExecutionId.takeLast(12)}, " +
                "hop=${providerRequestContext.hopOrdinal}",
        )
        val requestBody =
            createRequestBody(
                context = context,
                chatHistory = chatHistory,
                modelParameters = modelParameters,
                enableThinking = enableThinking,
                stream = true,
                availableTools = availableTools,
                preserveThinkInHistory = preserveThinkInHistory,
            )
        onTokensUpdated(
            tokenCacheManager.totalInputTokenCount,
            tokenCacheManager.cachedInputTokenCount,
            tokenCacheManager.outputTokenCount,
        )

        val repository = createResponsesExecutionPersistence(context)
        val createdAt = System.currentTimeMillis()
        val executionState =
            OpenAIResponsesExecutionState.create(
                requestContext = providerRequestContext,
                provider = providerType.name,
                modelName = modelName,
                createdAt = createdAt,
            )
        repository.createExecution(
            execution =
                ProviderExecutionEntity(
                    localExecutionId = providerRequestContext.localExecutionId,
                    chatId = providerRequestContext.chatId,
                    messageTimestamp = providerRequestContext.messageTimestamp,
                    variantIndex = providerRequestContext.variantIndex,
                    hopOrdinal = providerRequestContext.hopOrdinal,
                    provider = providerType.name,
                    modelName = modelName,
                    transportKind = ProviderTransportKind.RESPONSES.name,
                    requestFingerprint = requestBodySha256(requestBody),
                    status = ProviderExecutionStatus.SUBMITTING.name,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            initialMessageState = executionState.toMessageProviderState(createdAt),
        )

        val receivedContent = StringBuilder()
        val streamingState = StreamingState()
        val emitter =
            StreamEmitter(
                receivedContent = receivedContent,
                emit = emitChunk,
                eventChannel = eventChannel,
                onTokensUpdated = onTokensUpdated,
                streamingState = streamingState,
            )
        val persistenceSession =
            ResponsesPersistenceSession(
                repository = repository,
                executionState = executionState,
            )
        val maxRetries = LlmRetryPolicy.MAX_RETRY_ATTEMPTS
        var retryCount = 0

        while (true) {
            checkCancellation(context, session)
            val responseId = executionState.remoteResponseId
            val attemptNumber = retryCount + 1
            val requestTraceId =
                if (responseId == null) {
                    "resp_submit_${attemptNumber}_${UUID.randomUUID().toString().take(8)}"
                } else {
                    "resp_resume_${attemptNumber}_${UUID.randomUUID().toString().take(8)}"
                }
            val request =
                if (responseId == null) {
                    createRequest(
                        requestBody = requestBody,
                        requestTraceId = requestTraceId,
                        stream = true,
                        attemptNumber = attemptNumber,
                        localExecutionId = providerRequestContext.localExecutionId,
                    )
                } else {
                    createResponsesResumeRequest(
                        responseId = responseId,
                        startingAfter = executionState.lastAppliedSequence,
                        requestTraceId = requestTraceId,
                        attemptNumber = attemptNumber,
                        localExecutionId = providerRequestContext.localExecutionId,
                    )
            }
            val call = client.newCall(request)
            streamSessionGate.bindCall(session, call) {
                if (!call.isCanceled()) call.cancel()
            }
            val transportDiagnostics = request
                .tag(LlmRequestTraceContext::class.java)
                ?.state
            recordProviderAttemptAudit(
                context = context,
                request = request,
                providerRequestContext = providerRequestContext,
                eventType = "PROVIDER_ATTEMPT_CREATED",
                summary =
                    if (responseId == null) {
                        "已创建 Responses 提交 attempt"
                    } else {
                        "已创建 Responses 续接 attempt"
                    },
                attemptNumber = attemptNumber,
                requestFingerprint =
                    request.tag(LlmRequestTraceContext::class.java)
                        ?.requestSummary
                        ?.requestDigest,
                submissionState = if (responseId == null) "NOT_STARTED" else "RESUME_REQUEST",
                retrySafety = if (responseId == null) "UNDECIDED" else "RESUME_SAME_RESPONSE",
                transport = transportDiagnostics?.snapshot(),
                providerCallId = responseId,
                streamingState = streamingState,
            )

            try {
                withContext(responseExecutionDispatcher) {
                    val response = call.execute()
                    streamSessionGate.bindResponse(session, response) { response.close() }
                    try {
                        response.use {
                            recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_RESPONSE_HEADERS_RECEIVED",
                            summary = "Responses attempt 已收到响应头",
                            attemptNumber = attemptNumber,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = "RESPONSE_HEADERS_RECEIVED",
                            retrySafety = "NO_RETRY_AFTER_SUBMISSION",
                            transport = transportDiagnostics?.snapshotForHttpStatus(response.code),
                            providerCallId = executionState.remoteResponseId,
                            streamingState = streamingState,
                        )
                        if (!response.isSuccessful) {
                            val errorBody =
                                response.body?.string()
                                    ?: context.getString(R.string.openai_error_no_error_details)
                            val errorSummary = LlmLogPrivacy.summarizeProviderError(errorBody)
                            throw OpenAiHttpResponseException(
                                message =
                                    context.getString(
                                        R.string.openai_error_api_request_failed_with_status,
                                        response.code,
                                        errorSummary.exceptionDetail(),
                                    ),
                                statusCode = response.code,
                            )
                        }

                        val responseBody =
                            response.body
                                ?: throw IOException(
                                    context.getString(R.string.openai_error_response_empty)
                                )
                        recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_RESPONSE_BODY_STARTED",
                            summary = "Responses attempt 开始读取响应体",
                            attemptNumber = attemptNumber,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = "RESPONSE_BODY_STARTED",
                            retrySafety = "NO_RETRY_AFTER_SUBMISSION",
                            transport = transportDiagnostics?.snapshot(),
                            providerCallId = executionState.remoteResponseId,
                            streamingState = streamingState,
                        )
                            processStreamingResponse(
                                reader = responseBody.charStream().buffered(),
                                emitter = emitter,
                                onTokensUpdated = onTokensUpdated,
                                context = context,
                                session = session,
                                state = streamingState,
                                responsesPersistenceSession = persistenceSession,
                            )
                        }
                    } finally {
                        streamSessionGate.clearResponse(session, response)
                    }
                }

                when (executionState.status) {
                    ProviderExecutionStatus.COMPLETED -> {
                        recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_ATTEMPT_COMPLETED",
                            summary = "Responses attempt 完成并收到明确终态",
                            attemptNumber = attemptNumber,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = "COMPLETED",
                            retrySafety = "NO_RETRY",
                            transport = transportDiagnostics?.snapshot(),
                            providerCallId = executionState.remoteResponseId,
                            streamingState = streamingState,
                            terminalState = "COMPLETED",
                            completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                        )
                        logFinalOutput(
                            "AIService",
                            receivedContent,
                            "Resumable Responses final output summary: ",
                        )
                        return
                    }

                    ProviderExecutionStatus.FAILED,
                    ProviderExecutionStatus.INCOMPLETE,
                    ProviderExecutionStatus.CANCELLED,
                    ProviderExecutionStatus.EXPIRED,
                    -> throw IOException(
                        executionState.lastErrorMessage
                            ?: "Responses execution ended with ${executionState.status}"
                    )

                    else -> throw OpenAIResponsesProtocolException(
                        "Responses stream returned without a terminal state"
                    )
                }
            } catch (error: UserCancellationException) {
                recordProviderAttemptAudit(
                    context = context,
                    request = request,
                    providerRequestContext = providerRequestContext,
                    eventType = "PROVIDER_ATTEMPT_CANCELLED",
                    summary = "Responses attempt 被用户取消",
                    attemptNumber = attemptNumber,
                    requestFingerprint =
                        request.tag(LlmRequestTraceContext::class.java)
                            ?.requestSummary
                            ?.requestDigest,
                    submissionState = submissionStateFor(transportDiagnostics?.snapshot(error)),
                    retrySafety = "NO_RETRY_USER_CANCELLED",
                    transport = transportDiagnostics?.snapshot(error),
                    providerCallId = executionState.remoteResponseId,
                    streamingState = streamingState,
                    failureCode = "USER_STOP",
                    throwable = error,
                    terminalState = "CANCELLED",
                    completeness = ConversationAuditCompletenessStatus.PARTIAL,
                )
                withContext(NonCancellable) {
                    finalizeResponsesCancellation(
                        context = context,
                        repository = repository,
                        executionState = executionState,
                    )
                }
                throw error
            } catch (error: CancellationException) {
                recordProviderAttemptAudit(
                    context = context,
                    request = request,
                    providerRequestContext = providerRequestContext,
                    eventType = "PROVIDER_ATTEMPT_CANCELLED",
                    summary = "Responses attempt 收到取消信号",
                    attemptNumber = attemptNumber,
                    requestFingerprint =
                        request.tag(LlmRequestTraceContext::class.java)
                            ?.requestSummary
                            ?.requestDigest,
                    submissionState = submissionStateFor(transportDiagnostics?.snapshot(error)),
                    retrySafety = "NO_RETRY_CANCELLATION",
                    transport = transportDiagnostics?.snapshot(error),
                    providerCallId = executionState.remoteResponseId,
                    streamingState = streamingState,
                    failureCode =
                        if (streamSessionGate.isCancelled(session)) "USER_STOP" else "CANCELLATION",
                    throwable = error,
                    terminalState = "CANCELLED",
                    completeness = ConversationAuditCompletenessStatus.PARTIAL,
                )
                if (streamSessionGate.isCancelled(session)) {
                    withContext(NonCancellable) {
                        finalizeResponsesCancellation(
                            context = context,
                            repository = repository,
                            executionState = executionState,
                        )
                    }
                }
                throw error
            } catch (error: Exception) {
                val attemptDiagnosticsSnapshot = transportDiagnostics?.snapshot(error)
                recordProviderAttemptAudit(
                    context = context,
                    request = request,
                    providerRequestContext = providerRequestContext,
                    eventType = "PROVIDER_ATTEMPT_FAILED",
                    summary = "Responses attempt 失败",
                    attemptNumber = attemptNumber,
                    requestFingerprint =
                        request.tag(LlmRequestTraceContext::class.java)
                            ?.requestSummary
                            ?.requestDigest,
                    submissionState = submissionStateFor(attemptDiagnosticsSnapshot),
                    retrySafety =
                        if (executionState.remoteResponseId != null) {
                            "RESUME_SAME_RESPONSE"
                        } else {
                            "NO_RETRY_SUBMISSION_UNKNOWN"
                        },
                    transport = attemptDiagnosticsSnapshot,
                    providerCallId = executionState.remoteResponseId,
                    streamingState = streamingState,
                    failureCode =
                        attemptDiagnosticsSnapshot?.diagnosticCode
                            ?: error.javaClass.simpleName,
                    throwable = error,
                    terminalState = "FAILED",
                    completeness = ConversationAuditCompletenessStatus.PARTIAL,
                )
                if (executionState.isTerminal) {
                    throw error
                }

                when {
                    error is OpenAIResponsesTerminalException -> {
                        repository.updateStatus(
                            localExecutionId = providerRequestContext.localExecutionId,
                            status = ProviderExecutionStatus.FAILED,
                            lastErrorCode = error.eventType,
                            lastErrorMessage = error.message,
                            completedAt = System.currentTimeMillis(),
                        )
                        throw error
                    }
                    error is OpenAIResponsesEventProcessingException -> {
                        repository.updateStatus(
                            localExecutionId = providerRequestContext.localExecutionId,
                            status = ProviderExecutionStatus.FAILED,
                            lastErrorCode = "RESPONSES_PROTOCOL_ERROR",
                            lastErrorMessage = error.cause?.message ?: error.message,
                            completedAt = System.currentTimeMillis(),
                        )
                        throw error
                    }

                    executionState.remoteResponseId != null -> {
                        if (error is HttpStatusCodeException) {
                            when (
                                OpenAIResponsesHttpFailurePolicy.classifyResume(
                                    error.statusCode
                                )
                            ) {
                                OpenAIResponsesHttpFailurePolicy.ResumeAction.FAIL -> {
                                    repository.updateStatus(
                                        localExecutionId =
                                            providerRequestContext.localExecutionId,
                                        status = ProviderExecutionStatus.FAILED,
                                        lastErrorCode = "HTTP_${error.statusCode}",
                                        lastErrorMessage = error.message,
                                        completedAt = System.currentTimeMillis(),
                                    )
                                    throw error
                                }

                                OpenAIResponsesHttpFailurePolicy.ResumeAction.EXPIRE -> {
                                    repository.updateStatus(
                                        localExecutionId =
                                            providerRequestContext.localExecutionId,
                                        status = ProviderExecutionStatus.EXPIRED,
                                        lastErrorCode = "HTTP_${error.statusCode}",
                                        lastErrorMessage = error.message,
                                        completedAt = System.currentTimeMillis(),
                                    )
                                    throw error
                                }

                                OpenAIResponsesHttpFailurePolicy.ResumeAction.RETRY_SAME_RESPONSE ->
                                    Unit
                            }
                        }
                        val interrupted =
                            if (error is OpenAIResponsesTransportInterruptedException) {
                                error
                            } else {
                                OpenAIResponsesTransportInterruptedException(
                                    responseId = requireNotNull(executionState.remoteResponseId),
                                    lastAppliedSequence = executionState.lastAppliedSequence,
                                    cause =
                                        if (error is IOException) {
                                            error
                                        } else {
                                            IOException(error.message, error)
                                        },
                                )
                            }
                        repository.updateStatus(
                            localExecutionId = providerRequestContext.localExecutionId,
                            status = ProviderExecutionStatus.DISCONNECTED,
                            lastErrorCode = "STREAM_INTERRUPTED",
                            lastErrorMessage = interrupted.cause?.message ?: interrupted.message,
                        )
                        retryCount =
                            handleRetryableError(
                                context = context,
                                session = session,
                                exception = interrupted,
                                retryCount = retryCount,
                                maxRetries = maxRetries,
                                enableRetry = enableRetry,
                                onNonFatalError = onNonFatalError,
                            ) { errorText, retryNumber ->
                                "【Responses连接中断，正在续接同一响应（第${retryNumber}次）：$errorText】"
                            }
                        recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_RETRY_SCHEDULED",
                            summary = "Responses 将续接同一 response",
                            attemptNumber = attemptNumber,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = "RESPONSE_BODY_INTERRUPTED",
                            retrySafety = "RESUME_SAME_RESPONSE",
                            transport = attemptDiagnosticsSnapshot,
                            providerCallId = executionState.remoteResponseId,
                            streamingState = streamingState,
                            failureCode = "STREAM_INTERRUPTED",
                            completeness = ConversationAuditCompletenessStatus.PARTIAL,
                        )
                        repository.beginResume(providerRequestContext.localExecutionId)
                    }

                    error is HttpStatusCodeException -> {
                        when (
                            OpenAIResponsesHttpFailurePolicy.classifySubmission(
                                error.statusCode
                            )
                        ) {
                            OpenAIResponsesHttpFailurePolicy.SubmissionAction.RETRY_EXPLICIT_REJECTION -> {
                                retryCount =
                                    handleRetryableError(
                                        context = context,
                                        session = session,
                                        exception = error,
                                        retryCount = retryCount,
                                        maxRetries = maxRetries,
                                        enableRetry = enableRetry,
                                        onNonFatalError = onNonFatalError,
                                    ) { errorText, retryNumber ->
                                        "【Responses提交被明确拒绝，正在进行第${retryNumber}次重试：$errorText】"
                                    }
                                recordProviderAttemptAudit(
                                    context = context,
                                    request = request,
                                    providerRequestContext = providerRequestContext,
                                    eventType = "PROVIDER_RETRY_SCHEDULED",
                                    summary = "Responses 收到明确拒绝，按策略重新提交",
                                    attemptNumber = attemptNumber,
                                    requestFingerprint =
                                        request.tag(LlmRequestTraceContext::class.java)
                                            ?.requestSummary
                                            ?.requestDigest,
                                    submissionState = "PROVIDER_REJECTED",
                                    retrySafety = "EXPLICIT_PROVIDER_REJECTION",
                                    transport = attemptDiagnosticsSnapshot,
                                    streamingState = streamingState,
                                    failureCode = "HTTP_${error.statusCode}",
                                    completeness = ConversationAuditCompletenessStatus.PARTIAL,
                                )
                            }

                            OpenAIResponsesHttpFailurePolicy.SubmissionAction.FAIL -> {
                                repository.updateStatus(
                                    localExecutionId =
                                        providerRequestContext.localExecutionId,
                                    status = ProviderExecutionStatus.FAILED,
                                    lastErrorCode = "HTTP_${error.statusCode}",
                                    lastErrorMessage = error.message,
                                    completedAt = System.currentTimeMillis(),
                                )
                                throw error
                            }

                            OpenAIResponsesHttpFailurePolicy.SubmissionAction.SUBMISSION_UNKNOWN -> {
                                val diagnostics =
                                    transportDiagnostics?.snapshotForHttpStatus(error.statusCode)
                                repository.updateStatus(
                                    localExecutionId =
                                        providerRequestContext.localExecutionId,
                                    status = ProviderExecutionStatus.SUBMISSION_UNKNOWN,
                                    lastErrorCode = "HTTP_${error.statusCode}_SUBMISSION_UNKNOWN",
                                    lastErrorMessage =
                                        appendTransportDiagnostics(error.message, diagnostics),
                                )
                                throw OpenAIResponsesSubmissionUnknownException(
                                    localExecutionId =
                                        providerRequestContext.localExecutionId,
                                    cause =
                                        if (error is IOException) {
                                            error
                                        } else {
                                            IOException(error.message, error)
                                        },
                                    transportDiagnostics = diagnostics,
                                )
                            }
                        }
                    }

                    else -> {
                        val ioError =
                            if (error is IOException) {
                                error
                            } else {
                                IOException(error.message, error)
                            }
                        val diagnostics = transportDiagnostics?.snapshot(ioError)
                        repository.updateStatus(
                            localExecutionId = providerRequestContext.localExecutionId,
                            status = ProviderExecutionStatus.SUBMISSION_UNKNOWN,
                            lastErrorCode = "SUBMISSION_UNKNOWN",
                            lastErrorMessage =
                                appendTransportDiagnostics(ioError.message, diagnostics),
                        )
                        throw OpenAIResponsesSubmissionUnknownException(
                            localExecutionId = providerRequestContext.localExecutionId,
                            cause = ioError,
                            transportDiagnostics = diagnostics,
                        )
                    }
                }
            } finally {
                streamSessionGate.clearCall(session, call)
            }
        }
    }

    private suspend fun finalizeResponsesCancellation(
        context: Context,
        repository: OpenAIResponsesExecutionPersistence,
        executionState: OpenAIResponsesExecutionState,
    ) {
        val now = System.currentTimeMillis()
        repository.updateStatus(
            localExecutionId = executionState.localExecutionId,
            status = ProviderExecutionStatus.CANCELLING,
            updatedAt = now,
        )
        val responseId = executionState.remoteResponseId
        if (responseId == null) {
            repository.updateStatus(
                localExecutionId = executionState.localExecutionId,
                status = ProviderExecutionStatus.CANCELLING,
                lastErrorCode = "REMOTE_RESPONSE_ID_UNKNOWN",
                lastErrorMessage =
                    "Local cancellation occurred before response.created; remote cancellation is pending",
            )
            return
        }

        try {
            cancelResponsesExecution(responseId)
            repository.updateStatus(
                localExecutionId = executionState.localExecutionId,
                status = ProviderExecutionStatus.CANCELLED,
                completedAt = System.currentTimeMillis(),
            )
        } catch (cancelError: Exception) {
            repository.updateStatus(
                localExecutionId = executionState.localExecutionId,
                status = ProviderExecutionStatus.CANCELLING,
                lastErrorCode = "REMOTE_CANCEL_FAILED",
                lastErrorMessage = cancelError.message,
            )
            AppLogger.e(
                "AIService",
                context.getString(R.string.openai_error_request_cancelled),
                cancelError,
            )
            throw cancelError
        }
    }

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        providerRequestContext: ProviderRequestContext?,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean
    ): Stream<String> {
        val eventChannel = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val responseStream = stream {
            val session = streamSessionGate.begin()
            try {
            latestProviderUsageSnapshot = null
            // 重置输出token计数（输入token由TokenCacheManager管理）
            tokenCacheManager.addOutputTokens(-tokenCacheManager.outputTokenCount)
            onTokensUpdated(
                tokenCacheManager.totalInputTokenCount,
                tokenCacheManager.cachedInputTokenCount,
                tokenCacheManager.outputTokenCount
            )

            AppLogger.d(
                "AIService",
                "【发送消息】开始处理sendMessage请求，历史记录数量: ${chatHistory.size}，最后一条长度: ${chatHistory.lastOrNull()?.content?.length ?: 0}"
            )

            val useResumableResponses =
                stream &&
                    useResponsesApi &&
                    supportsResponsesStreamResumption &&
                    providerRequestContext != null
            AppLogger.d(
                "AIService",
                "request routing: provider=${providerType.name}, model=$modelName, " +
                    "stream=$stream, useResponsesApi=$useResponsesApi, " +
                    "contractAuthority=${modelCapabilityProfile.providerContractAuthority}, " +
                    "executionPersistence=${modelCapabilityProfile.executionPersistence}, " +
                    "supportsResponsesStreamResumption=$supportsResponsesStreamResumption, " +
                    "providerRequestContextPresent=${providerRequestContext != null}, " +
                    "localExecutionSuffix=${providerRequestContext?.localExecutionId?.takeLast(12)}, " +
                    "resumableResponses=$useResumableResponses"
            )

            if (useResumableResponses) {
                executeResumableResponsesStream(
                    context = context,
                    session = session,
                    chatHistory = chatHistory,
                    modelParameters = modelParameters,
                    enableThinking = enableThinking,
                    availableTools = availableTools,
                    preserveThinkInHistory = preserveThinkInHistory,
                    providerRequestContext = providerRequestContext,
                    emitChunk = ::emit,
                    eventChannel = eventChannel,
                    onTokensUpdated = onTokensUpdated,
                    onNonFatalError = onNonFatalError,
                    enableRetry = enableRetry,
                )
                return@stream
            }

            val maxRetries = LlmRetryPolicy.MAX_RETRY_ATTEMPTS
            var retryCount = 0
            var lastException: Exception? = null

            // 用于保存当前 attempt 已接收到的内容；一旦需要重试，会整体回滚到请求起点
            val receivedContent = StringBuilder()
            val emitter = StreamEmitter(receivedContent, ::emit, eventChannel, onTokensUpdated)
            val requestSavepointId = "attempt_${UUID.randomUUID().toString().replace("-", "")}"
            emitter.emitSavepoint(requestSavepointId)

            while (retryCount <= maxRetries) {
                // 在循环开始时检查是否已被取消
                checkCancellation(context, session)
                val attemptStreamingState = StreamingState()
                emitter.bindStreamingState(attemptStreamingState)
                var responsesSubmissionStarted = false
                var transportTraceState: LlmRequestTraceState? = null
                var attemptRequest: Request? = null

                try {
                    if (retryCount > 0) {
                        AppLogger.d(
                            "AIService",
                            "【重试】原子回滚后重新请求，本轮已撤回内容长度: ${receivedContent.length}"
                        )
                    }

                    val currentHistory = chatHistory

                AppLogger.d(
                    "AIService",
                    "【发送消息】准备构建请求体，模型参数数量: ${modelParameters.size}，已启用参数: ${modelParameters.count { it.isEnabled }}"
                )
                // 直接传递原始历史记录给createRequestBody，让具体的Provider决定如何处理（例如Deepseek需要保留<think>标签）
                val requestBody = createRequestBody(
                    context,
                    currentHistory,
                    modelParameters,
                    enableThinking,
                    stream,
                    availableTools,
                    preserveThinkInHistory
                )
                onTokensUpdated(
                    tokenCacheManager.totalInputTokenCount,
                    tokenCacheManager.cachedInputTokenCount,
                    tokenCacheManager.outputTokenCount
                )
                val attemptNumber = retryCount + 1
                val requestTraceId = "llm_${attemptNumber}_${UUID.randomUUID().toString().substring(0, 8)}"
                val request =
                    createRequest(
                        requestBody = requestBody,
                        requestTraceId = requestTraceId,
                        stream = stream,
                        attemptNumber = attemptNumber,
                        localExecutionId = providerRequestContext?.localExecutionId,
                    )
                attemptRequest = request
                transportTraceState =
                    request.tag(LlmRequestTraceContext::class.java)?.state
                recordProviderAttemptAudit(
                    context = context,
                    request = request,
                    providerRequestContext = providerRequestContext,
                    eventType = "PROVIDER_ATTEMPT_CREATED",
                    summary = "已创建第 ${attemptNumber} 次 Provider HTTP attempt",
                    attemptNumber = attemptNumber,
                    requestFingerprint =
                        request.tag(LlmRequestTraceContext::class.java)
                            ?.requestSummary
                            ?.requestDigest,
                    submissionState = "NOT_STARTED",
                    retrySafety = "UNDECIDED",
                    transport = transportTraceState?.snapshot(),
                    streamingState = attemptStreamingState,
                )
                AppLogger.d(
                    "AIService",
                    "[req=$requestTraceId] 【发送消息】请求体构建完成，目标模型: $modelName，API端点: $apiEndpoint"
                )

                AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】准备连接到AI服务...")

                // Call 绑定到本轮 session；旧回合的 finally 不能清除或取消新回合句柄。
                val call = client.newCall(request)
                streamSessionGate.bindCall(session, call) {
                    if (!call.isCanceled()) call.cancel()
                }

                AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】正在建立连接到服务器...")

                // 确保在IO线程执行网络请求和响应体读取
                AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】切换到IO线程执行网络请求")
                withContext(responseExecutionDispatcher) {
                    val executeStartNs = System.nanoTime()
                    AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】进入 call.execute()，开始等待响应头")
                    responsesSubmissionStarted = true
                    val response = call.execute()
                    val executeElapsedMs = (System.nanoTime() - executeStartNs) / 1_000_000
                    AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】call.execute() 返回，耗时=${executeElapsedMs}ms")

                    // Response 与同一 session 绑定，停止时同时关闭响应体并取消 Call。
                    streamSessionGate.bindResponse(session, response) { response.close() }
                    recordProviderAttemptAudit(
                        context = context,
                        request = request,
                        providerRequestContext = providerRequestContext,
                        eventType = "PROVIDER_RESPONSE_HEADERS_RECEIVED",
                        summary = "第 ${attemptNumber} 次 Provider attempt 已收到响应头",
                        attemptNumber = attemptNumber,
                        requestFingerprint =
                            request.tag(LlmRequestTraceContext::class.java)
                                ?.requestSummary
                                ?.requestDigest,
                        submissionState = "RESPONSE_HEADERS_RECEIVED",
                        retrySafety = "NO_RETRY_AFTER_SUBMISSION",
                        transport = transportTraceState?.snapshotForHttpStatus(response.code),
                        streamingState = attemptStreamingState,
                    )

                    try {
                        if (!response.isSuccessful) {
                            val errorBody =
                                response.body?.string()
                                    ?: context.getString(R.string.openai_error_no_error_details)
                            val errorSummary = LlmLogPrivacy.summarizeProviderError(errorBody)
                            AppLogger.e(
                                "AIService",
                                "【发送消息】API请求失败: ${errorSummary.format(response.code)}"
                            )
                            if (useResponsesApi) {
                                throw OpenAiHttpResponseException(
                                    context.getString(
                                        R.string.openai_error_api_request_failed_with_status,
                                        response.code,
                                        errorSummary.exceptionDetail(),
                                    ),
                                    statusCode = response.code,
                                )
                            }
                            // 4xx错误仍保留单独的异常类型，具体是否重试由统一策略决定
                            if (response.code in 400..499) {
                                throw NonRetriableException(
                                    context.getString(
                                        R.string.openai_error_api_request_failed_with_status,
                                        response.code,
                                        errorSummary.exceptionDetail(),
                                    ),
                                    statusCode = response.code
                                )
                            }
                            // 明确的5xx响应沿用既有服务端错误重试合同；传输中断则由提交边界单独判定。
                            throw OpenAiHttpResponseException(
                                context.getString(
                                    R.string.openai_error_api_request_failed_with_status,
                                    response.code,
                                    errorSummary.exceptionDetail(),
                                ),
                                statusCode = response.code,
                            )
                        }

                        AppLogger.d(
                            "AIService",
                            "[req=$requestTraceId] 【发送消息】连接成功(状态码: ${response.code})，准备处理响应..."
                        )
                        val responseBody = response.body ?: throw IOException(context.getString(R.string.openai_error_response_empty))

                        // 根据stream参数处理响应
                        if (stream) {
                            AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】开始读取流式响应")
                            val reader = responseBody.charStream().buffered()
                            recordProviderAttemptAudit(
                                context = context,
                                request = request,
                                providerRequestContext = providerRequestContext,
                                eventType = "PROVIDER_RESPONSE_BODY_STARTED",
                                summary = "第 ${attemptNumber} 次 Provider attempt 开始读取响应体",
                                attemptNumber = attemptNumber,
                                requestFingerprint =
                                    request.tag(LlmRequestTraceContext::class.java)
                                        ?.requestSummary
                                        ?.requestDigest,
                                submissionState = "RESPONSE_BODY_STARTED",
                                retrySafety = "NO_RETRY_AFTER_SUBMISSION",
                                transport = transportTraceState?.snapshot(),
                                streamingState = attemptStreamingState,
                            )
                            processStreamingResponse(
                                reader,
                                emitter,
                                onTokensUpdated,
                                context,
                                session,
                                state = attemptStreamingState,
                            )
                        } else {
                            AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】开始读取非流式响应")
                            val responseText = responseBody.string()
                            AppLogger.d("AIService", "[req=$requestTraceId] 收到完整响应，长度: ${responseText.length}")

                            var hasEmittedRegularContent = false

                            try {
                                val jsonResponse = JSONObject(responseText)
                                throwIfOpenAiErrorPayload(context, jsonResponse)
                                val handledImages = tryHandleOpenAiImageResponse(jsonResponse, emitter, null)

                                if (useResponsesApi) {
                                    val parsed = OpenAIResponsesPayloadAdapter.parseNonStreamingResponse(jsonResponse)

                                    parsed.reasoningChunks.forEach { reasoningChunk ->
                                        if (reasoningChunk.isNotEmpty()) {
                                            emitter.emitThinkContent(reasoningChunk)
                                        }
                                    }
                                    parsed.reasoningMetadataTags.forEach { metadataTag ->
                                        emitter.emitTag(metadataTag)
                                    }

                                    if (!handledImages) {
                                        parsed.textChunks.forEach { textChunk ->
                                            if (textChunk.isNotEmpty()) {
                                                hasEmittedRegularContent = true
                                                emitter.emitContent(textChunk)
                                            }
                                        }
                                    }

                                    if (parsed.toolCalls.length() > 0 && enableToolCall) {
                                        val xmlToolCalls =
                                            convertToolCallsToXml(
                                                toolCalls = parsed.toolCalls,
                                            )
                                        if (xmlToolCalls.isNotEmpty()) {
                                            emitter.emitContent(xmlToolCalls)
                                            AppLogger.d(
                                                "AIService",
                                                "Tool Call转XML (Responses非流式): " +
                                                    LlmLogPrivacy.summarizeText(xmlToolCalls).format()
                                            )
                                        }
                                    }
                                } else if (!handledImages) {
                                    val choices = jsonResponse.getJSONArray("choices")

                                    if (choices.length() > 0) {
                                        val choice = choices.getJSONObject(0)
                                        val messageObj = choice.optJSONObject("message")

                                        if (messageObj != null) {
                                            // 检查是否有tool_calls（Tool Call API）
                                            val toolCalls = messageObj.optJSONArray("tool_calls")
                                            if (toolCalls != null && toolCalls.length() > 0 && enableToolCall) {
                                                val xmlToolCalls = convertToolCallsToXml(toolCalls)
                                                if (xmlToolCalls.isNotEmpty()) {
                                                    emitter.emitContent(xmlToolCalls)
                                                    AppLogger.d(
                                                        "AIService",
                                                        "Tool Call转XML (非流式): " +
                                                            LlmLogPrivacy.summarizeText(xmlToolCalls).format()
                                                    )
                                                }
                                            }

                                            val reasoningContent =
                                                messageObj.optString("reasoning_content", "")
                                            val regularContent = messageObj.optString("content", "")

                                            // 处理思考内容（如果有）
                                            if (reasoningContent.isNotNullOrEmpty() && !hasEmittedRegularContent) {
                                                emitter.emitThinkContent(reasoningContent)
                                            }

                                            // 处理常规内容
                                            if (regularContent.isNotNullOrEmpty()) {
                                                hasEmittedRegularContent = true
                                                emitter.emitContent(regularContent)
                                            }
                                        }
                                    }
                                }

                                applyUsageToCounters(jsonResponse.optJSONObject("usage"), onTokensUpdated)

                                AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】非流式响应处理完成")
                            } catch (e: IOException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.e("AIService", "【发送消息】解析非流式响应失败", e)
                                throw IOException(context.getString(R.string.openai_error_parse_response_failed, e.message ?: ""), e)
                            }
                        }
                    } finally {
                        response.close()
                        streamSessionGate.clearResponse(session, response)
                        AppLogger.d("AIService", "[req=$requestTraceId] 【发送消息】关闭响应连接")
                    }
                }

                recordProviderAttemptAudit(
                    context = context,
                    request = request,
                    providerRequestContext = providerRequestContext,
                    eventType = "PROVIDER_ATTEMPT_COMPLETED",
                    summary = "第 ${attemptNumber} 次 Provider HTTP attempt 完成",
                    attemptNumber = attemptNumber,
                    requestFingerprint =
                        request.tag(LlmRequestTraceContext::class.java)
                            ?.requestSummary
                            ?.requestDigest,
                    submissionState = "COMPLETED",
                    retrySafety = "NO_RETRY",
                    transport = transportTraceState?.snapshot(),
                    streamingState = attemptStreamingState,
                    terminalState = "COMPLETED",
                    completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                )

                // 清理活跃引用
                streamSessionGate.clearCall(session, call)
                AppLogger.d("AIService", "【发送消息】响应处理完成，已清理活跃引用")
                logFinalOutput("AIService", receivedContent, "Final output summary: ")

                // 成功处理后返回
                AppLogger.d(
                    "AIService",
                    "【发送消息】请求成功完成，输入token: ${tokenCacheManager.totalInputTokenCount}(缓存:${tokenCacheManager.cachedInputTokenCount})，输出token: ${tokenCacheManager.outputTokenCount}"
                )
                return@stream
            } catch (e: Exception) {
                lastException = e
                if (e is CancellationException) {
                    attemptRequest?.let { request ->
                        val cancellationDiagnostics = transportTraceState?.snapshot(e)
                        recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_ATTEMPT_CANCELLED",
                            summary = "Provider HTTP attempt 收到取消信号",
                            attemptNumber =
                                request.tag(LlmRequestTraceContext::class.java)?.attempt
                                    ?: retryCount + 1,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = submissionStateFor(cancellationDiagnostics),
                            retrySafety = "NO_RETRY_CANCELLATION",
                            transport = cancellationDiagnostics,
                            streamingState = attemptStreamingState,
                            failureCode =
                                if (streamSessionGate.isCancelled(session)) "USER_STOP" else "CANCELLATION",
                            throwable = e,
                            terminalState = "CANCELLED",
                            completeness = ConversationAuditCompletenessStatus.PARTIAL,
                        )
                    }
                    throw e
                }
                val request = attemptRequest ?: throw e
                val attemptDiagnostics = transportTraceState?.snapshot(e)
                recordProviderAttemptAudit(
                    context = context,
                    request = request,
                    providerRequestContext = providerRequestContext,
                    eventType = "PROVIDER_ATTEMPT_FAILED",
                    summary = "第 ${request.tag(LlmRequestTraceContext::class.java)?.attempt ?: retryCount + 1} 次 Provider HTTP attempt 失败",
                    attemptNumber = request.tag(LlmRequestTraceContext::class.java)?.attempt ?: retryCount + 1,
                    requestFingerprint =
                        request.tag(LlmRequestTraceContext::class.java)
                            ?.requestSummary
                            ?.requestDigest,
                    submissionState = submissionStateFor(attemptDiagnostics),
                    retrySafety = retrySafetyForFailure(attemptDiagnostics),
                    transport = attemptDiagnostics,
                    streamingState = attemptStreamingState,
                    failureCode = attemptDiagnostics?.diagnosticCode ?: e.javaClass.simpleName,
                    throwable = e,
                    terminalState = "FAILED",
                    completeness = ConversationAuditCompletenessStatus.PARTIAL,
                )
                // 请求体编译和本地协议校验发生在任何 HTTP 提交之前。相同输入不会因网络重试
                // 变得合法，且包装成“连接超时”会掩盖真正的工具历史错误。
                if (!responsesSubmissionStarted) {
                    throw e
                }
                val chatFailure =
                    wrapChatTransportFailure(
                        failure = e,
                        providerRequestContext = providerRequestContext,
                        diagnostics = attemptDiagnostics,
                    )
                if (usesAtMostOnceResponsesSubmission) {
                    retryCount =
                        handleAtMostOnceResponsesFailure(
                            context = context,
                            session = session,
                            exception = e,
                            retryCount = retryCount,
                            maxRetries = maxRetries,
                            enableRetry = enableRetry,
                            onNonFatalError = onNonFatalError,
                            providerRequestContext = providerRequestContext,
                            transportDiagnostics =
                                transportTraceState
                                    ?.takeUnless {
                                        e is OpenAIResponsesProtocolException ||
                                            e is OpenAIResponsesMissingTerminalException ||
                                            e is OpenAIResponsesEventProcessingException
                                    }
                                    ?.snapshot(e),
                        )
                } else {
                    val canRetry = isSafePreSubmissionRetry(attemptDiagnostics)
                    if (!canRetry) {
                        recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_RETRY_SUPPRESSED",
                            summary =
                                "第 ${request.tag(LlmRequestTraceContext::class.java)?.attempt ?: retryCount + 1} 次 attempt 提交状态未知，停止自动重试",
                            attemptNumber =
                                request.tag(LlmRequestTraceContext::class.java)?.attempt
                                    ?: retryCount + 1,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = submissionStateFor(attemptDiagnostics),
                            retrySafety = "NO_RETRY_SUBMISSION_UNKNOWN",
                            transport = attemptDiagnostics,
                            streamingState = attemptStreamingState,
                            failureCode =
                                attemptDiagnostics?.diagnosticCode
                                    ?: "SUBMISSION_UNKNOWN",
                            completeness = ConversationAuditCompletenessStatus.PARTIAL,
                        )
                        throw chatFailure
                    }

                    val willRetry = enableRetry && retryCount < maxRetries
                    if (!willRetry) {
                        recordProviderAttemptAudit(
                            context = context,
                            request = request,
                            providerRequestContext = providerRequestContext,
                            eventType = "PROVIDER_RETRY_SUPPRESSED",
                            summary = "未提交 attempt 不再继续重试",
                            attemptNumber =
                                request.tag(LlmRequestTraceContext::class.java)?.attempt
                                    ?: retryCount + 1,
                            requestFingerprint =
                                request.tag(LlmRequestTraceContext::class.java)
                                    ?.requestSummary
                                    ?.requestDigest,
                            submissionState = submissionStateFor(attemptDiagnostics),
                            retrySafety = "RETRY_DISABLED_OR_EXHAUSTED",
                            transport = attemptDiagnostics,
                            streamingState = attemptStreamingState,
                            completeness = ConversationAuditCompletenessStatus.PARTIAL,
                        )
                        throw chatFailure
                    }
                    recordProviderAttemptAudit(
                        context = context,
                        request = request,
                        providerRequestContext = providerRequestContext,
                        eventType = "PROVIDER_RETRY_SCHEDULED",
                        summary =
                            "第 ${request.tag(LlmRequestTraceContext::class.java)?.attempt ?: retryCount + 1} 次 attempt 尚未提交，允许重试",
                        attemptNumber =
                            request.tag(LlmRequestTraceContext::class.java)?.attempt
                                ?: retryCount + 1,
                        requestFingerprint =
                            request.tag(LlmRequestTraceContext::class.java)
                                ?.requestSummary
                                ?.requestDigest,
                        submissionState = submissionStateFor(attemptDiagnostics),
                        retrySafety = "SAFE_PRE_SUBMISSION_RETRY",
                        transport = attemptDiagnostics,
                        streamingState = attemptStreamingState,
                        completeness = ConversationAuditCompletenessStatus.PARTIAL,
                    )
                    val beforeRollbackCharacters = receivedContent.length
                    emitter.emitRollback(requestSavepointId)
                    recordProviderAttemptAudit(
                        context = context,
                        request = request,
                        providerRequestContext = providerRequestContext,
                        eventType = "PROVIDER_ROLLBACK_APPLIED",
                        summary =
                            "已回滚未提交 attempt 的暂存输出",
                        attemptNumber =
                            request.tag(LlmRequestTraceContext::class.java)?.attempt
                                ?: retryCount,
                        requestFingerprint =
                            request.tag(LlmRequestTraceContext::class.java)
                                ?.requestSummary
                                ?.requestDigest,
                        submissionState = submissionStateFor(attemptDiagnostics),
                        retrySafety = "SAFE_PRE_SUBMISSION_RETRY",
                        transport = attemptDiagnostics,
                        streamingState = attemptStreamingState,
                        rollbackCharacters = beforeRollbackCharacters,
                        completeness = ConversationAuditCompletenessStatus.PARTIAL,
                    )
                    retryCount = handleRetryableError(
                        context,
                        session,
                        e,
                        retryCount,
                        maxRetries,
                        enableRetry,
                        onNonFatalError
                    ) { errorText, retryNumber ->
                        "【${context.getString(R.string.openai_retry_with_count, errorText, retryNumber)}】"
                    }
                }
            }
            }

            // 所有重试都失败
            lastException?.let { ex ->
                AppLogger.e(
                    "AIService",
                    "【发送消息】重试失败，请检查网络连接，最大重试次数: $maxRetries",
                    ex
                )
            } ?: AppLogger.e(
                "AIService",
                "【发送消息】重试失败，请检查网络连接，最大重试次数: $maxRetries"
            )
            throw IOException(
                context.getString(
                    R.string.openai_error_connection_timeout,
                    maxRetries,
                    lastException?.message ?: context.getString(R.string.openai_error_network_interrupted)
                )
            )
            } finally {
                streamSessionGate.complete(session)
            }
        }
        return responseStream.withEventChannel(eventChannel)
    }
}
