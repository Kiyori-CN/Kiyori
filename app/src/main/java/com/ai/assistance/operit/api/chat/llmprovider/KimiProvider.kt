package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kimi K2.5 Provider (Moonshot API).
 * Mirrors DeepseekProvider behavior for reasoning_content handling when thinking is enabled.
 */
open class KimiProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.MOONSHOT,
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

    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        fun applyThinkingParams(jsonObject: JSONObject) {
            jsonObject.put(
                "thinking",
                JSONObject().apply {
                    put("type", if (enableThinking) "enabled" else "disabled")
                }
            )
        }

        if (!enableThinking) {
            val baseRequestBodyJson =
                super.createRequestBodyInternal(context, chatHistory, modelParameters, stream, availableTools, preserveThinkInHistory)
            val jsonObject = JSONObject(baseRequestBodyJson)
            applyThinkingParams(jsonObject)
            return createJsonRequestBody(jsonObject.toString())
        }

        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", stream)
        applyThinkingParams(jsonObject)

        for (param in modelParameters) {
            if (param.isEnabled) {
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
                        val parsed: Any? = try {
                            when {
                                raw.startsWith("{") -> JSONObject(raw)
                                raw.startsWith("[") -> JSONArray(raw)
                                else -> null
                            }
                        } catch (e: Exception) {
                            AppLogger.w("KimiProvider", "OBJECT参数解析失败: ${param.apiName}", e)
                            null
                        }
                        if (parsed != null) {
                            jsonObject.put(param.apiName, parsed)
                        } else {
                            jsonObject.put(param.apiName, raw)
                        }
                    }
                }
            }
        }

        val effectiveEnableToolCall = enableToolCall && availableTools != null && availableTools.isNotEmpty()

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
        val messagesArray =
            buildMessagesWithReasoning(
                context,
                providerReadyHistory,
                effectiveEnableToolCall
            )
        jsonObject.put("messages", messagesArray)

        return createJsonRequestBody(jsonObject.toString())
    }

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
                        detail = "Kimi structured tool call has no stable call ID",
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
                boundary = "kimi_assistant_tool_calls",
            )
            queuedAssistantToolText = null
            queuedAssistantReasoning = null
            queuedToolCalls = JSONArray()
            queuedToolCallIds.clear()
        }

        fun requireNoOpenToolCalls(boundary: String) {
            emitQueuedToolCallsIfNeeded()
            toolHistoryState.requireClosed(boundary)
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
                                if (openToolCallIds.isNotEmpty()) {
                                    requireNoOpenToolCalls("assistant_tool_call_before_result")
                                }
                                queueToolCalls(textContent, toolCalls, reasoningContent)
                            } else {
                                requireNoOpenToolCalls("assistant_boundary")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put("reasoning_content", reasoningContent)
                                        put(
                                            "content",
                                            if (content.isBlank()) JSONObject.NULL
                                            else buildContentField(context, content),
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
                                if (openToolCallIds.isNotEmpty()) {
                                    requireNoOpenToolCalls("typed_tool_call_before_result")
                                }
                                queueToolCalls(textContent, toolCalls)
                            } else {
                                throw ProviderToolHistoryProtocolException(
                                    violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                                    detail = "Kimi typed TOOL_CALL has no structured payload",
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
                                        detail = "Kimi typed TOOL_RESULT has no structured payload",
                                    )

                            if (resultsList.isNotEmpty() && openToolCallIds.isNotEmpty()) {
                                toolHistoryState.acceptToolResults(
                                    resultCount = resultsList.size,
                                    boundary = "kimi_tool_result",
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
                                    toolHistoryState.requireClosed("kimi_tool_result_text_boundary")
                                    messagesArray.put(
                                        JSONObject().apply {
                                            put("role", "user")
                                            put("content", buildContentField(context, textContent))
                                        }
                                    )
                                }
                            } else {
                                throw ProviderToolHistoryProtocolException(
                                    violation = ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_CALL,
                                    detail = "Kimi TOOL_RESULT has no pending tool call",
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
                                        if (content.isBlank()) JSONObject.NULL
                                        else buildContentField(context, content),
                                    )
                                }
                            )
                        }

                        PromptTurnKind.TOOL_CALL,
                        PromptTurnKind.TOOL_RESULT -> {
                            throw ProviderToolHistoryProtocolException(
                                violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                                detail = "Kimi typed tool history cannot be replayed with native tools disabled",
                            )
                        }
                    }
                }
            }
        }

        requireNoOpenToolCalls("history_end")
        return messagesArray
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
        return super.sendMessage(
            context,
            chatHistory,
            modelParameters,
            enableThinking,
            stream,
            availableTools,
            preserveThinkInHistory,
            providerRequestContext,
            onTokensUpdated,
            onNonFatalError,
            enableRetry
        )
    }
}
