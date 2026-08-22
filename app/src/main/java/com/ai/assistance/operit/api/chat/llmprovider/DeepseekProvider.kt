package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 针对DeepSeek模型的特定API Provider。
 * 继承自OpenAIProvider，以重用大部分兼容逻辑，但特别处理了`reasoning_content`参数。
 * 当启用推理模式时，会将assistant消息中的<think>标签内容提取出来作为reasoning_content字段。
 */
open class DeepseekProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: com.ai.assistance.operit.data.model.ApiProviderType = com.ai.assistance.operit.data.model.ApiProviderType.DEEPSEEK,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : OpenAIProvider(
        apiEndpoint = apiEndpoint,
        apiKeyProvider = apiKeyProvider,
        modelName = modelName,
        client = client,
        customHeaders = customHeaders,
        providerType = providerType,
        supportsVision = supportsVision,
        supportsAudio = supportsAudio,
        supportsVideo = supportsVideo,
        enableToolCall = enableToolCall
    ) {

    /**
     * 重写创建请求体的方法，以支持DeepSeek的`reasoning_content`参数。
     * 当启用推理模式时，需要特殊处理消息格式。
     */
    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        fun applyThinkingParamsIfNeeded(jsonObject: JSONObject) {
            val thinkingObject = jsonObject.optJSONObject("thinking") ?: JSONObject()
            val thinkingType = if (enableThinking) "enabled" else "disabled"
            thinkingObject.put("type", thinkingType)
            jsonObject.put("thinking", thinkingObject)

            if (!enableThinking) {
                AppLogger.d("DeepseekProvider", "DeepSeek thinking mode explicitly set to disabled")
                return
            }

            val effort = resolveDeepseekThinkingEffort(context)
            if (effort != null && !jsonObject.has("reasoning_effort")) {
                jsonObject.put("reasoning_effort", effort)
            }
        }

        // 如果未启用推理模式，直接使用父类的实现
        // 推理模式固定开启，需要特殊处理
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", stream)
        if (stream) {
            jsonObject.put(
                "stream_options",
                JSONObject().put("include_usage", true),
            )
        }

        // DeepSeek Thinking Mode 默认开启，关闭时也必须显式发送 thinking.type=disabled。
        applyThinkingParamsIfNeeded(jsonObject)

        // 添加已启用的模型参数
        val enabledParameters = modelParameters.filter { it.isEnabled }.sortedBy { it.apiName }
        val duplicateParameterName =
            enabledParameters
                .groupingBy { it.apiName }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
        require(duplicateParameterName == null) {
            "DeepSeek request contains duplicate model parameter: $duplicateParameterName"
        }
        val reservedParameterName =
            enabledParameters.firstOrNull { it.apiName in DEEPSEEK_RESERVED_REQUEST_FIELDS }?.apiName
        require(reservedParameterName == null) {
            "DeepSeek model parameter conflicts with request field: $reservedParameterName"
        }
        for (param in enabledParameters) {
            when (param.valueType) {
                com.ai.assistance.operit.data.model.ParameterValueType.INT ->
                    jsonObject.put(param.apiName, param.currentValue as Int)
                com.ai.assistance.operit.data.model.ParameterValueType.FLOAT ->
                    jsonObject.put(param.apiName, param.currentValue as Float)
                com.ai.assistance.operit.data.model.ParameterValueType.STRING ->
                    jsonObject.put(param.apiName, param.currentValue as String)
                com.ai.assistance.operit.data.model.ParameterValueType.BOOLEAN ->
                    jsonObject.put(param.apiName, param.currentValue as Boolean)
                com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                    val raw = param.currentValue.toString().trim()
                    val parsed: Any? =
                        try {
                            when {
                                raw.startsWith("{") -> JSONObject(raw)
                                raw.startsWith("[") -> JSONArray(raw)
                                else -> null
                            }
                        } catch (e: Exception) {
                            AppLogger.w("DeepseekProvider", "OBJECT参数解析失败: ${param.apiName}", e)
                            throw IllegalArgumentException(
                                "DeepSeek OBJECT parameter is not valid JSON: ${param.apiName}",
                                e,
                            )
                        }
                    require(parsed != null) {
                        "DeepSeek OBJECT parameter must be a JSON object or array: ${param.apiName}"
                    }
                    jsonObject.put(param.apiName, parsed)
                }
            }
        }

        // 当工具为空时，将enableToolCall视为false
        val effectiveEnableToolCall = enableToolCall && availableTools != null && availableTools.isNotEmpty()

        // 如果启用Tool Call且传入了工具列表，添加tools定义
        var toolsJson: String? = null
        if (effectiveEnableToolCall) {
            val tools = buildToolDefinitions(availableTools)
            if (tools.length() > 0) {
                jsonObject.put("tools", tools)
                jsonObject.put("tool_choice", "auto")
                toolsJson = tools.toString()
            }
        }

        val providerReadyHistory = prepareHistoryForProvider(chatHistory, effectiveEnableToolCall)
        calculateAndStoreInputTokens(
            providerReadyHistory,
            toolsJson,
            preserveThinkInHistory = true
        )

        // 使用特殊的消息构建方法（支持reasoning_content）
        val messagesArray =
            buildMessagesWithReasoning(
                context,
                providerReadyHistory,
                effectiveEnableToolCall
            )
        jsonObject.put("messages", messagesArray)

        return createJsonRequestBody(
            ProviderToolCallIdentityContract.canonicalJsonText(jsonObject.toString()),
        )
    }

    /**
     * 构建支持reasoning_content的消息数组
     * 对于assistant角色的消息，提取<think>标签内容作为reasoning_content
     */
    private fun buildMessagesWithReasoning(
        context: Context,
        effectiveHistory: List<PromptTurn>,
        useToolCall: Boolean
    ): JSONArray {
        val messagesArray = JSONArray()

        var queuedAssistantToolText: String? = null
        var queuedAssistantReasoning: String? = null
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

        fun appendQueuedAssistantReasoning(reasoningContent: String) {
            if (reasoningContent.isBlank()) return
            queuedAssistantReasoning =
                if (queuedAssistantReasoning.isNullOrBlank()) {
                    reasoningContent
                } else {
                    queuedAssistantReasoning + "\n" + reasoningContent
                }
        }

        fun queueToolCalls(textContent: String, toolCalls: JSONArray, reasoningContent: String = "") {
            appendQueuedAssistantToolText(textContent)
            appendQueuedAssistantReasoning(reasoningContent)
            for (i in 0 until toolCalls.length()) {
                val sourceToolCall = toolCalls.optJSONObject(i) ?: continue
                val toolCall = JSONObject(sourceToolCall.toString())
                val callId = toolCall.optString("id", "").trim()
                if (callId.isEmpty()) {
                    throw ProviderToolHistoryProtocolException(
                        violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                        detail = "DeepSeek tool call has no provider call ID",
                    )
                }
                toolCall.put("id", callId)
                queuedToolCalls.put(toolCall)
                queuedToolCallIds.add(callId)
            }
        }

        fun emitQueuedToolCallsIfNeeded() {
            if (queuedToolCalls.length() == 0) return

            messagesArray.put(
                JSONObject().apply {
                    put("role", "assistant")
                    put("reasoning_content", queuedAssistantReasoning.orEmpty())
                    if (!queuedAssistantToolText.isNullOrBlank()) {
                        put("content", buildContentField(context, queuedAssistantToolText!!))
                    } else {
                        put("content", null)
                    }
                    put("tool_calls", queuedToolCalls)
                }
            )

            openToolCallIds.addAll(queuedToolCallIds)
            toolHistoryState.acceptToolCalls(
                callIds = queuedToolCallIds.toList(),
                boundary = "deepseek_assistant_tool_calls",
            )
            queuedAssistantToolText = null
            queuedAssistantReasoning = null
            queuedToolCalls = JSONArray()
            queuedToolCallIds.clear()
        }

        fun requireNoOpenToolCalls(reason: String) {
            emitQueuedToolCallsIfNeeded()
            toolHistoryState.requireClosed(reason)
            openToolCallIds.clear()
        }

        if (effectiveHistory.isNotEmpty()) {
            for (turn in effectiveHistory) {
                val originalContent = comparableContentForTurn(turn, preserveThinkInHistory = true)
                if (useToolCall) {
                    when (turn.kind) {
                        PromptTurnKind.SYSTEM -> {
                            requireNoOpenToolCalls("system_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY -> {
                            requireNoOpenToolCalls("user_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.ASSISTANT -> {
                            val (content, reasoningContent) = ChatUtils.extractThinkingContent(originalContent)
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(content)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                requireNoOpenToolCalls("assistant_tool_call_before_result")
                                queueToolCalls(textContent, toolCalls, reasoningContent)
                            } else {
                                requireNoOpenToolCalls("assistant_boundary")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put("reasoning_content", reasoningContent)
                                        put(
                                            "content",
                                            if (content.isBlank()) JSONObject.NULL else buildContentField(context, content),
                                        )
                                    }
                                )
                            }
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(originalContent)
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
                                    detail = "DeepSeek typed TOOL_CALL has no structured payload",
                                )
                            }
                        }

                        PromptTurnKind.TOOL_RESULT -> {
                            emitQueuedToolCallsIfNeeded()
                            val (textContent, toolResults) = parseXmlToolResults(originalContent)
                            val resultsList =
                                toolResults
                                    ?: throw ProviderToolHistoryProtocolException(
                                        violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                                        detail = "DeepSeek typed TOOL_RESULT has no structured payload",
                                    )
                            toolHistoryState.acceptToolResults(
                                resultCount = resultsList.size,
                                boundary = "deepseek_tool_result",
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
                                toolHistoryState.requireClosed("deepseek_tool_result_text_boundary")
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
                    when (turn.kind) {
                        PromptTurnKind.SYSTEM -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.ASSISTANT -> {
                            val (content, reasoningContent) = ChatUtils.extractThinkingContent(originalContent)
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "assistant")
                                    put("reasoning_content", reasoningContent)
                                    put(
                                        "content",
                                        if (content.isBlank()) JSONObject.NULL else buildContentField(context, content),
                                    )
                                }
                            )
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            throw ProviderToolHistoryProtocolException(
                                violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                                detail = "DeepSeek TOOL_CALL cannot be replayed when native tool calls are disabled",
                            )
                        }

                        PromptTurnKind.TOOL_RESULT -> {
                            throw ProviderToolHistoryProtocolException(
                                violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                                detail = "DeepSeek TOOL_RESULT cannot be replayed when native tool calls are disabled",
                            )
                        }
                    }
                }
            }
        }

        requireNoOpenToolCalls("history_end")
        return messagesArray
    }

    private fun resolveDeepseekThinkingEffort(context: Context): String? {
        val qualityLevel = runCatching {
            runBlocking {
                ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            }
        }.getOrElse {
            AppLogger.w(
                "DeepseekProvider",
                "Failed to read thinking quality level for DeepSeek, using provider default",
                it
            )
            return null
        }

        val efforts = listOf("low", "high", "max", "max", "max")
        val qualityIndex = qualityLevel.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            ApiPreferences.MAX_THINKING_QUALITY_LEVEL
        ) - 1
        return efforts[qualityIndex]
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
        // 直接调用父类的sendMessage实现
        return super.sendMessage(context, chatHistory, modelParameters, enableThinking, stream, availableTools, preserveThinkInHistory, providerRequestContext, onTokensUpdated, onNonFatalError, enableRetry)
    }

    private companion object {
        val DEEPSEEK_RESERVED_REQUEST_FIELDS =
            setOf(
                "messages",
                "model",
                "reasoning_effort",
                "stream",
                "stream_options",
                "thinking",
                "tool_choice",
                "tools",
            )
    }
}
