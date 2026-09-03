package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.chat.AssistantReplayHistoryProjector
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.HttpLogSanitizer
import com.ai.assistance.operit.util.StreamingJsonXmlConverter
import com.ai.assistance.operit.util.TokenCacheManager
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.withEventChannel
import com.ai.assistance.operit.util.stream.stream
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser

internal object GeminiUsagePayloadAdapter {
    data class UsageCounts(
        val totalInputTokens: Int?,
        val uncachedInputTokens: Int?,
        val cachedInputTokens: Int?,
        val outputTokens: Int?,
        val reasoningTokens: Int?,
        val cacheMetricState: ProviderCacheMetricState,
    )

    fun parse(usageMetadata: JSONObject?): UsageCounts? {
        usageMetadata ?: return null

        val promptPresent = usageMetadata.has("promptTokenCount")
        val cachePresent = usageMetadata.has("cachedContentTokenCount")
        val candidatesPresent = usageMetadata.has("candidatesTokenCount")
        val thoughtsPresent = usageMetadata.has("thoughtsTokenCount")
        if (!promptPresent && !cachePresent && !candidatesPresent && !thoughtsPresent) {
            return null
        }

        val rawPromptTokens = usageMetadata.optInt("promptTokenCount", 0)
        val rawCachedTokens = usageMetadata.optInt("cachedContentTokenCount", 0)
        val rawCandidateTokens = usageMetadata.optInt("candidatesTokenCount", 0)
        val rawThoughtTokens = usageMetadata.optInt("thoughtsTokenCount", 0)
        val totalInputTokens = rawPromptTokens.coerceAtLeast(0).takeIf { promptPresent }
        val cachedInputTokens =
            rawCachedTokens
                .coerceAtLeast(0)
                .coerceAtMost(totalInputTokens ?: Int.MAX_VALUE)
                .takeIf { cachePresent }
        val uncachedInputTokens =
            totalInputTokens?.let { total ->
                (total - (cachedInputTokens ?: 0)).coerceAtLeast(0)
            }
        val reasoningTokens = rawThoughtTokens.coerceAtLeast(0).takeIf { thoughtsPresent }
        val outputTokens =
            if (candidatesPresent || thoughtsPresent) {
                (
                    rawCandidateTokens.coerceAtLeast(0).toLong() +
                        rawThoughtTokens.coerceAtLeast(0).toLong()
                    ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } else {
                null
            }

        return UsageCounts(
            totalInputTokens = totalInputTokens,
            uncachedInputTokens = uncachedInputTokens,
            cachedInputTokens = cachedInputTokens,
            outputTokens = outputTokens,
            reasoningTokens = reasoningTokens,
            cacheMetricState =
                when {
                    rawPromptTokens < 0 ||
                        rawCachedTokens < 0 ||
                        rawCandidateTokens < 0 ||
                        rawThoughtTokens < 0 ||
                        (
                            promptPresent &&
                                cachePresent &&
                                rawCachedTokens > rawPromptTokens
                            ) ->
                        ProviderCacheMetricState.INVALID
                    cachePresent -> ProviderCacheMetricState.REPORTED
                    else -> ProviderCacheMetricState.NOT_REPORTED
                },
        )
    }
}

/** Google Gemini API的实现 支持标准Gemini接口流式传输 */
class GeminiProvider(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val modelName: String,
    private val client: OkHttpClient,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.GOOGLE,
    private val enableGoogleSearch: Boolean = false,
    private val enableToolCall: Boolean = false // 是否启用Tool Call接口（预留，Gemini有原生tool支持）
) : AIService, ProviderUsageReporting, ProviderReplayMetadataConsumer {
    override val consumedReplayMetadataKinds: Set<ProviderReplayMetadataKind> =
        setOf(
            ProviderReplayMetadataKind.GEMINI_THOUGHT_SIGNATURE,
            ProviderReplayMetadataKind.GEMINI_CONTENT_PARTS,
        )

    companion object {
        private const val TAG = "GeminiProvider"
        private const val DEBUG = true // 开启调试日志
    }

    // HTTP客户端
    // private val client: OkHttpClient = HttpClientFactory.instance

    private val JSON = "application/json".toMediaType()

    private val streamSessionGate = ProviderStreamSessionGate()

    /**
     * 由客户端错误（如4xx状态码）触发的API异常，是否重试由统一策略决定
     */
    class NonRetriableException(
        message: String,
        override val statusCode: Int,
        cause: Throwable? = null
    ) : IOException(message, cause), HttpStatusCodeException

    // Token计数
    private val tokenCacheManager = TokenCacheManager()

    @Volatile
    private var latestProviderUsageSnapshot: ProviderUsageSnapshot? = null

    @Synchronized
    override fun consumeLatestProviderUsageSnapshot(): ProviderUsageSnapshot? {
        val snapshot = latestProviderUsageSnapshot
        latestProviderUsageSnapshot = null
        return snapshot
    }

    override val inputTokenCount: Int
        get() = tokenCacheManager.totalInputTokenCount
    override val cachedInputTokenCount: Int
        get() = tokenCacheManager.cachedInputTokenCount
    override val outputTokenCount: Int
        get() = tokenCacheManager.outputTokenCount

    // 供应商:模型标识符
    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    // 取消当前流式传输
    override fun cancelStreaming() {
        if (streamSessionGate.cancelActive()) {
            AppLogger.d(TAG, "已取消当前 Gemini 流式会话")
        }
    }

    // 重置Token计数
    override fun resetTokenCounts() {
        tokenCacheManager.resetTokenCounts()
        latestProviderUsageSnapshot = null
    }

    override suspend fun calculateInputTokens(
            chatHistory: List<PromptTurn>,
            availableTools: List<ToolPrompt>?
    ): Int {
        // 构建工具定义的JSON字符串
        val toolsJson = buildToolsJson(availableTools)
        val replaySafeHistory =
            AssistantReplayHistoryProjector.replaySafeHistory(
                history = chatHistory,
                allowTypedToolHistory = enableToolCall,
            )
        val comparableHistory =
            ChatUtils.stripGeminiThoughtSignatureMeta(
                replaySafeHistory.map { turn ->
                    val comparableRole =
                        when (turn.kind) {
                            PromptTurnKind.SYSTEM -> "system"
                            PromptTurnKind.USER -> "user"
                            PromptTurnKind.ASSISTANT -> "assistant"
                            PromptTurnKind.TOOL_CALL -> "tool_call"
                            PromptTurnKind.TOOL_RESULT -> "tool_result"
                            PromptTurnKind.SUMMARY -> "summary"
                        }
                    val comparableContent =
                        if (turn.kind == PromptTurnKind.ASSISTANT) {
                            ChatUtils.removeThinkingContent(turn.content)
                        } else {
                            turn.content
                        }
                    comparableRole to comparableContent
                }
            )
        return tokenCacheManager.calculateInputTokens(
            comparableHistory,
            toolsJson,
            updateState = false
        )
    }
    
    /**
     * 构建工具定义的JSON字符串，用于token计算
     */
    private fun buildToolsJson(availableTools: List<ToolPrompt>?): String? {
        if (!enableToolCall || availableTools == null || availableTools.isEmpty()) {
            return if (enableGoogleSearch) {
                // 只有 Google Search
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                }.toString()
            } else {
                null
            }
        }
        
        val tools = JSONArray()
        
        // 添加 Function Calling 工具
        val functionDeclarations = buildToolDefinitionsForGemini(availableTools)
        if (functionDeclarations.length() > 0) {
            tools.put(JSONObject().apply {
                put("function_declarations", functionDeclarations)
            })
        }
        
        // 添加 Google Search grounding 工具（如果启用）
        if (enableGoogleSearch) {
            tools.put(JSONObject().apply {
                put("googleSearch", JSONObject())
            })
        }
        
        return if (tools.length() > 0) tools.toString() else null
    }

    // ==================== Tool Call 支持 ====================
    
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

    private data class GeminiThoughtSignaturePayload(
        val contentWithoutMeta: String,
        val thoughtSignatures: List<String>,
    )

    private data class GeminiFunctionCallPayload(
        val textContent: String,
        val functionCalls: List<JSONObject>,
        val thoughtSignatures: List<String>,
    )

    private fun encodeGeminiThoughtSignature(signature: String): String {
        return Base64.encodeToString(signature.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decodeGeminiThoughtSignature(signatureBase64: String): String? {
        return try {
            String(Base64.decode(signatureBase64, Base64.DEFAULT), Charsets.UTF_8)
                .takeIf { it.isNotEmpty() }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Gemini thoughtSignature metadata is not valid base64",
                e,
            )
        }
    }

    private fun extractGeminiThoughtSignaturePayload(content: String): GeminiThoughtSignaturePayload {
        val signaturesBase64 = ChatMarkupRegex.extractGeminiThoughtSignatures(content)
        val contentWithoutMeta = ChatMarkupRegex.removeGeminiThoughtSignatureMeta(content)
        val thoughtSignatures =
            signaturesBase64.map { signatureBase64 ->
                decodeGeminiThoughtSignature(signatureBase64)
                    ?: throw IllegalArgumentException(
                        "Gemini thoughtSignature metadata is empty",
                    )
            }
        return GeminiThoughtSignaturePayload(
            contentWithoutMeta = contentWithoutMeta,
            thoughtSignatures = thoughtSignatures,
        )
    }

    private fun appendGeminiThoughtSignatureMeta(
        contentBuilder: StringBuilder,
        thoughtSignature: String
    ) {
        if (contentBuilder.isNotEmpty() && contentBuilder[contentBuilder.length - 1] != '\n') {
            contentBuilder.append('\n')
        }
        contentBuilder.append(
            ChatMarkupRegex.geminiThoughtSignatureMetaTag(
                encodeGeminiThoughtSignature(thoughtSignature)
            )
        )
    }

    private fun JSONObject.optGeminiThoughtSignature(): String? {
        val camelCase = optString("thoughtSignature", "").trim()
        if (camelCase.isNotEmpty()) {
            return camelCase
        }
        val snakeCase = optString("thought_signature", "").trim()
        if (snakeCase.isNotEmpty()) {
            return snakeCase
        }
        return null
    }
    
    /**
     * 解析XML格式的tool调用，转换为Gemini FunctionCall格式
     * @return 文本内容、functionCall对象列表、以及挂在Part级别的thought signature
     */
    private fun parseXmlToolCalls(content: String): GeminiFunctionCallPayload {
        if (!enableToolCall) {
            if (ChatMarkupRegex.containsToolTag(content)) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                    detail = "Gemini history contains a tool call while native tools are disabled",
                )
            }
            return GeminiFunctionCallPayload(
                textContent = content,
                functionCalls = emptyList(),
                thoughtSignatures = emptyList(),
            )
        }

        val thoughtSignaturePayload = extractGeminiThoughtSignaturePayload(content)
        val sanitizedContent = thoughtSignaturePayload.contentWithoutMeta
        val matches = ChatMarkupRegex.toolCallPattern.findAll(sanitizedContent).toList()
        
        if (matches.isEmpty()) {
            return GeminiFunctionCallPayload(
                textContent = sanitizedContent,
                functionCalls = emptyList(),
                thoughtSignatures = emptyList(),
            )
        }

        if (thoughtSignaturePayload.thoughtSignatures.size > matches.size) {
            throw ProviderToolHistoryProtocolException(
                violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                detail =
                    "Gemini history contains ${thoughtSignaturePayload.thoughtSignatures.size} " +
                        "thought signatures for ${matches.size} function calls",
            )
        }

        val functionCalls =
            matches.map { match ->
                val toolName = match.groupValues[2]
                val toolBody = match.groupValues[3]

                val args = JSONObject()
                ChatMarkupRegex.toolParamPattern.findAll(toolBody).forEach { paramMatch ->
                    val paramName = paramMatch.groupValues[1]
                    val paramValue = XmlEscaper.unescape(paramMatch.groupValues[2].trim())
                    args.put(paramName, paramValue)
                }

                AppLogger.d(TAG, "XML→GeminiFunctionCall: $toolName")
                JSONObject().apply {
                    put("name", toolName)
                    put("args", args)
                }
            }

        var textContent = sanitizedContent
        matches.forEach { match ->
            textContent = textContent.replace(match.value, "").trim()
        }
        
        return GeminiFunctionCallPayload(
            textContent = textContent,
            functionCalls = functionCalls,
            thoughtSignatures = thoughtSignaturePayload.thoughtSignatures,
        )
    }
    
    /**
     * 解析XML格式的tool_result，转换为Gemini FunctionResponse格式
     * @return Pair<文本内容, functionResponse对象列表>
     */
    private fun parseXmlToolResults(content: String): Pair<String, List<JSONObject>?> {
        if (!enableToolCall) {
            if (ChatMarkupRegex.containsToolResultTag(content)) {
                throw ProviderToolHistoryProtocolException(
                    violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                    detail = "Gemini history contains a tool result while native tools are disabled",
                )
            }
            return Pair(content, null)
        }
        
        val matches = ChatMarkupRegex.toolResultWithNameAnyPattern.findAll(content)
        
        if (!matches.any()) {
            return Pair(content, null)
        }
        
        val functionResponses = mutableListOf<JSONObject>()
        var textContent = content
        
        matches.forEach { match ->
            val toolName =
                ChatMarkupRegex.extractToolResultProtocolName(match.value)
                    ?: match.groupValues[2]
            val fullContent = match.groupValues[3].trim()
            val contentMatch = ChatMarkupRegex.contentTag.find(fullContent)
            val resultContent = if (contentMatch != null) {
                contentMatch.groupValues[1].trim()
            } else {
                fullContent
            }
            
            // 构建functionResponse对象（Gemini格式）
            val functionResponse = JSONObject().apply {
                put("name", toolName)
                put("response", JSONObject().apply {
                    put("result", resultContent)
                })
            }
            
            functionResponses.add(functionResponse)
            AppLogger.d(TAG, "解析Gemini functionResponse: $toolName, content length=${resultContent.length}")
            
            textContent = textContent.replace(match.value, "").trim()
        }
        
        return Pair(textContent, functionResponses)
    }
    
    /**
     * 从ToolPrompt列表构建Gemini格式的Function Declarations
     */
    private fun buildToolDefinitionsForGemini(toolPrompts: List<ToolPrompt>): JSONArray {
        val duplicateTool =
            toolPrompts
                .groupingBy { it.name.trim() }
                .eachCount()
                .entries
                .firstOrNull { (name, count) -> name.isEmpty() || count > 1 }
        if (duplicateTool != null) {
            throw IllegalArgumentException(
                "Gemini tool declarations contain an empty or duplicate name '${duplicateTool.key}'"
            )
        }
        val functionDeclarations = JSONArray()
        
        for (tool in toolPrompts.sortedBy { it.name }) {
            functionDeclarations.put(JSONObject().apply {
                put("name", tool.name)
                // 组合description和details作为完整描述
                val fullDescription = if (tool.details.isNotEmpty()) {
                    "${tool.description}\n${tool.details}"
                } else {
                    tool.description
                }
                put("description", fullDescription)
                
                // 使用结构化参数构建schema
                val parametersSchema = buildSchemaFromStructured(tool.parametersStructured ?: emptyList())
                put("parameters", parametersSchema)
            })
        }
        
        return functionDeclarations
    }
    
    /**
     * 从结构化参数构建JSON Schema（Gemini格式）
     */
    private fun buildSchemaFromStructured(params: List<com.ai.assistance.operit.data.model.ToolParameterSchema>): JSONObject {
        val duplicateParameter =
            params
                .groupingBy { it.name.trim() }
                .eachCount()
                .entries
                .firstOrNull { (name, count) -> name.isEmpty() || count > 1 }
        if (duplicateParameter != null) {
            throw IllegalArgumentException(
                "Gemini tool schema contains an empty or duplicate parameter '${duplicateParameter.key}'"
            )
        }
        val schema = JSONObject().apply {
            put("type", "object")
        }
        
        val properties = JSONObject()
        val required = JSONArray()
        
        for (param in params.sortedBy { it.name }) {
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
        if (required.length() > 0) {
            schema.put("required", required)
        }
        
        return schema
    }
    
    /**
     * 构建包含文本和图片的parts数组
     */
    private fun buildPartsArray(text: String): JSONArray {
        val partsArray = JSONArray()

        val hasImages = MediaLinkParser.hasImageLinks(text)
        val hasMedia = MediaLinkParser.hasMediaLinks(text)

        if (hasImages || hasMedia) {
            val imageLinks = if (hasImages) MediaLinkParser.extractImageLinks(text) else emptyList()
            val mediaLinks = if (hasMedia) MediaLinkParser.extractMediaLinks(text) else emptyList()

            var textWithoutLinks = text
            if (hasImages) {
                textWithoutLinks = MediaLinkParser.removeImageLinks(textWithoutLinks)
            }
            if (hasMedia) {
                textWithoutLinks = MediaLinkParser.removeMediaLinks(textWithoutLinks)
            }
            textWithoutLinks = textWithoutLinks.trim()

            // 添加媒体（音频/视频）
            mediaLinks.forEach { link ->
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", link.mimeType)
                        put("data", link.base64Data)
                    })
                })
            }

            // 添加图片
            imageLinks.forEach { link ->
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", link.mimeType)
                        put("data", link.base64Data)
                    })
                })
            }

            // 添加文本（如果有）
            if (textWithoutLinks.isNotEmpty()) {
                partsArray.put(JSONObject().apply {
                    put("text", textWithoutLinks)
                })
            }
        } else {
            // 纯文本消息
            partsArray.put(JSONObject().apply {
                put("text", text)
            })
        }
        
        return partsArray
    }

    private fun buildContentsAndCountTokens(
            chatHistory: List<PromptTurn>,
            toolsJson: String? = null,
            preserveThinkInHistory: Boolean = false
    ): Pair<Pair<JSONArray, JSONObject?>, Int> {
        val contentsArray = JSONArray()
        var systemInstruction: JSONObject? = null

        val replaySafeHistory =
            AssistantReplayHistoryProjector.replaySafeHistory(
                history = chatHistory,
                allowTypedToolHistory = enableToolCall,
            )
        val providerReadyHistory =
            StructuredToolCallBridge.compileHistoryForProvider(
                replaySafeHistory,
                useToolCall = enableToolCall
            )

        // 使用TokenCacheManager计算token数量
        val sanitizedHistoryForTokenCount =
            ChatUtils.stripGeminiThoughtSignatureMeta(
                providerReadyHistory.map { turn ->
                    val comparableRole =
                        when (turn.kind) {
                            PromptTurnKind.SYSTEM -> "system"
                            PromptTurnKind.USER -> "user"
                            PromptTurnKind.ASSISTANT -> "assistant"
                            PromptTurnKind.TOOL_CALL -> "tool_call"
                            PromptTurnKind.TOOL_RESULT -> "tool_result"
                            PromptTurnKind.SUMMARY -> "summary"
                        }
                    val comparableContent =
                        if (!preserveThinkInHistory && turn.kind == PromptTurnKind.ASSISTANT) {
                            ChatUtils.removeThinkingContent(turn.content)
                        } else {
                            turn.content
                        }
                    comparableRole to comparableContent
                }
            )
        val tokenCount = tokenCacheManager.calculateInputTokens(
            sanitizedHistoryForTokenCount,
            toolsJson
        )

        val effectiveHistory = providerReadyHistory

        // Find and process system message first
        val systemMessages = effectiveHistory.filter { it.kind == PromptTurnKind.SYSTEM }
        if (systemMessages.isNotEmpty()) {
            val systemContent = systemMessages.joinToString("\n\n") { it.content }
            logDebug("发现系统消息: ${systemContent.take(50)}...")

            systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemContent) })
                })
            }
        }

        // Process the rest of the history
        val historyWithoutSystem = effectiveHistory.filter { it.kind != PromptTurnKind.SYSTEM }
        var queuedAssistantToolText: String? = null
        val queuedAssistantThoughtSignatures = mutableListOf<String>()
        val queuedFunctionCalls = mutableListOf<JSONObject>()
        val toolHistoryState = GeminiToolHistoryState()

        fun appendParts(target: JSONArray, parts: JSONArray) {
            for (index in 0 until parts.length()) {
                target.put(parts.get(index))
            }
        }

        fun appendQueuedAssistantToolText(text: String) {
            if (text.isBlank()) return
            queuedAssistantToolText =
                if (queuedAssistantToolText.isNullOrBlank()) {
                    text
                } else {
                    queuedAssistantToolText + "\n" + text
                }
        }

        fun queueFunctionCalls(
            textContent: String,
            functionCalls: List<JSONObject>,
            thoughtSignatures: List<String>,
        ) {
            appendQueuedAssistantToolText(textContent)
            queuedAssistantThoughtSignatures.addAll(thoughtSignatures)
            queuedFunctionCalls.addAll(functionCalls)
        }

        fun emitQueuedFunctionCallsIfNeeded() {
            if (queuedFunctionCalls.isEmpty()) return

            val partsArray = JSONArray()
            if (!queuedAssistantToolText.isNullOrBlank()) {
                appendParts(partsArray, buildPartsArray(queuedAssistantToolText!!))
            }
            queuedFunctionCalls.forEachIndexed { index, functionCall ->
                partsArray.put(
                    JSONObject().apply {
                        put("functionCall", functionCall)
                        queuedAssistantThoughtSignatures.getOrNull(index)?.let { signature ->
                            put("thoughtSignature", signature)
                        }
                    }
                )
            }

            contentsArray.put(
                JSONObject().apply {
                    put("role", "model")
                    put("parts", partsArray)
                }
            )

            val callSnapshots =
                queuedFunctionCalls.mapIndexed { index, functionCall ->
                    GeminiFunctionCallSnapshot(
                        name = functionCall.optString("name", "").trim(),
                        canonicalArguments =
                            canonicalGeminiJsonObject(
                                functionCall.optJSONObject("args")?.toString() ?: "{}"
                            ),
                        thoughtSignature = queuedAssistantThoughtSignatures.getOrNull(index),
                    )
                }
            toolHistoryState.acceptFunctionCalls(
                calls = callSnapshots,
                boundary = "gemini_model_function_call",
            )
            queuedAssistantThoughtSignatures.clear()
            queuedFunctionCalls.clear()
            queuedAssistantToolText = null
        }

        fun buildFunctionResponseSnapshots(
            responses: List<JSONObject>,
        ): List<GeminiFunctionResponseSnapshot> {
            return responses.map { response ->
                GeminiFunctionResponseSnapshot(
                    name = response.optString("name", "").trim(),
                    canonicalResponse =
                        canonicalGeminiJsonObject(
                            response.optJSONObject("response")?.toString() ?: "{}"
                        ),
                )
            }
        }

        for (turn in historyWithoutSystem) {
            val content =
                if (!preserveThinkInHistory && turn.kind == PromptTurnKind.ASSISTANT) {
                    ChatUtils.removeThinkingContent(turn.content)
                } else {
                    turn.content
                }
            val contentPartSnapshots =
                GeminiContentPartReplayCodec.extractSnapshots(content)
            if (contentPartSnapshots.isNotEmpty()) {
                if (
                    turn.kind != PromptTurnKind.ASSISTANT &&
                        turn.kind != PromptTurnKind.TOOL_CALL
                ) {
                    throw ProviderToolHistoryProtocolException(
                        violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                        detail =
                            "Gemini content-part metadata appears on ${turn.kind} instead of a model turn",
                    )
                }
                emitQueuedFunctionCallsIfNeeded()
                val replayParts =
                    GeminiContentPartReplayCodec.replayPartsForModel(
                        snapshots = contentPartSnapshots,
                        currentModelName = modelName,
                    )
                val replayCalls =
                    GeminiContentPartReplayCodec.functionCalls(replayParts)
                if (replayCalls.isEmpty()) {
                    if (turn.kind == PromptTurnKind.TOOL_CALL) {
                        throw ProviderToolHistoryProtocolException(
                            violation =
                                ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                            detail =
                                "Gemini typed tool-call metadata contains no functionCall Part",
                        )
                    }
                    toolHistoryState.requireClosed("gemini_replay_model_boundary")
                } else {
                    if (!enableToolCall) {
                        throw ProviderToolHistoryProtocolException(
                            violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                            detail =
                                "Gemini replay contains functionCall Parts while tools are disabled",
                        )
                    }
                    toolHistoryState.acceptFunctionCalls(
                        calls = replayCalls,
                        boundary = "gemini_content_part_replay",
                    )
                }
                contentsArray.put(
                    JSONObject().apply {
                        put("role", "model")
                        put("parts", replayParts)
                    }
                )
                continue
            }
            val contentWithoutGeminiMeta = ChatMarkupRegex.removeGeminiThoughtSignatureMeta(content)

            if (enableToolCall) {
                when (turn.kind) {
                    PromptTurnKind.ASSISTANT -> {
                        val functionCallPayload = parseXmlToolCalls(content)
                        if (functionCallPayload.functionCalls.isNotEmpty()) {
                            emitQueuedFunctionCallsIfNeeded()
                            queueFunctionCalls(
                                functionCallPayload.textContent,
                                functionCallPayload.functionCalls,
                                functionCallPayload.thoughtSignatures,
                            )
                        } else {
                            emitQueuedFunctionCallsIfNeeded()
                            toolHistoryState.requireClosed("gemini_assistant_boundary")
                            contentsArray.put(
                                JSONObject().apply {
                                    put("role", "model")
                                    put("parts", buildPartsArray(contentWithoutGeminiMeta))
                                }
                            )
                        }
                    }

                    PromptTurnKind.TOOL_CALL -> {
                        val functionCallPayload = parseXmlToolCalls(content)
                        if (functionCallPayload.functionCalls.isEmpty()) {
                            throw ProviderToolHistoryProtocolException(
                                violation = ProviderToolHistoryViolation.TOOL_CALL_WITHOUT_PAYLOAD,
                                detail = "Gemini typed tool-call turn contains no function call",
                            )
                        }
                        emitQueuedFunctionCallsIfNeeded()
                        queueFunctionCalls(
                            functionCallPayload.textContent,
                            functionCallPayload.functionCalls,
                            functionCallPayload.thoughtSignatures,
                        )
                    }

                    PromptTurnKind.USER,
                    PromptTurnKind.SUMMARY -> {
                        emitQueuedFunctionCallsIfNeeded()
                        toolHistoryState.requireClosed("gemini_user_boundary")
                        val partsArray = JSONArray()
                        appendParts(partsArray, buildPartsArray(contentWithoutGeminiMeta))
                        contentsArray.put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", partsArray)
                            }
                        )
                    }

                    PromptTurnKind.TOOL_RESULT -> {
                        emitQueuedFunctionCallsIfNeeded()
                        val (textContent, functionResponses) = parseXmlToolResults(contentWithoutGeminiMeta)
                        val responsesList =
                            functionResponses
                                ?: throw ProviderToolHistoryProtocolException(
                                    violation =
                                        ProviderToolHistoryViolation.TOOL_RESULT_WITHOUT_PAYLOAD,
                                    detail = "Gemini tool-result turn contains no function responses",
                                )
                        toolHistoryState.acceptFunctionResponses(
                            responses = buildFunctionResponseSnapshots(responsesList),
                            boundary = "gemini_function_response",
                        )
                        val partsArray = JSONArray()
                        responsesList.forEach { response ->
                                partsArray.put(
                                    JSONObject().apply {
                                        put("functionResponse", response)
                                    }
                                )
                                logDebug("历史XML→GeminiFunctionResponse: ${response.optString("name")}")
                            }

                        if (textContent.isNotEmpty()) {
                            appendParts(partsArray, buildPartsArray(textContent))
                        }
                        contentsArray.put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", partsArray)
                            }
                        )
                    }

                    PromptTurnKind.SYSTEM -> Unit
                }
            } else {
                if (ChatMarkupRegex.containsAnyToolLikeTag(content)) {
                    throw ProviderToolHistoryProtocolException(
                        violation = ProviderToolHistoryViolation.TOOL_PROTOCOL_DISABLED,
                        detail = "Gemini history contains structured tool markup while tools are disabled",
                    )
                }
                val geminiRole =
                    when (turn.kind) {
                        PromptTurnKind.ASSISTANT,
                        PromptTurnKind.TOOL_CALL -> "model"
                        else -> "user"
                    }
                contentsArray.put(
                    JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", buildPartsArray(contentWithoutGeminiMeta))
                    }
                )
            }
        }

        emitQueuedFunctionCallsIfNeeded()
        toolHistoryState.requireClosed("gemini_history_end")

        return Pair(Pair(contentsArray, systemInstruction), tokenCount)
    }

    // 工具函数：分块打印大型文本日志
    private fun logLargeString(tag: String, message: String, prefix: String = "") {
        AppLogger.d(
            tag,
            "${prefix.trimEnd()} ${LlmLogPrivacy.summarizeText(message).format()}"
        )
    }

    private fun logFinalOutput(content: CharSequence, prefix: String = "Gemini final output: ") {
        logLargeString(TAG, content.toString(), prefix)
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
             logError("保存输出图片失败", e)
             null
         }
     }

    // 日志辅助方法
    private fun logDebug(message: String) {
        if (DEBUG) {
            AppLogger.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            AppLogger.e(TAG, message, throwable)
        } else {
            AppLogger.e(TAG, message)
        }
    }

    private fun buildGeminiErrorDetail(error: JSONObject, fallback: String): String {
        val message = error.optString("message", "").trim().ifEmpty { fallback }
        val status = error.opt("status")?.toString()?.trim().orEmpty()
        val code = error.opt("code")?.toString()?.trim().orEmpty()

        if (status.isEmpty() && code.isEmpty()) {
            return message
        }

        return buildString {
            append(message)
            append(" [")
            if (status.isNotEmpty()) {
                append("status=").append(status)
            }
            if (status.isNotEmpty() && code.isNotEmpty()) {
                append(", ")
            }
            if (code.isNotEmpty()) {
                append("code=").append(code)
            }
            append("]")
        }
    }

    private fun throwIfGeminiErrorPayload(context: Context, json: JSONObject) {
        val error = json.optJSONObject("error") ?: return
        val detail = buildGeminiErrorDetail(error, context.getString(R.string.gemini_unknown_error))
        val exceptionMessage = context.getString(R.string.gemini_error_response_failed, detail)

        logError("API返回错误: $detail")
        throw IOException(exceptionMessage)
    }

    private fun resolveRetryErrorText(context: Context, exception: Exception): String {
        return when (exception) {
            is SocketTimeoutException -> context.getString(R.string.provider_error_timeout)
            is UnknownHostException -> context.getString(R.string.provider_error_unknown_host)
            else -> exception.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.provider_error_network_interrupted)
        }
    }

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
        if (exception is UserCancellationException || exception is kotlinx.coroutines.CancellationException) {
            throw exception
        }
        if (streamSessionGate.isCancelled(session)) {
            logError("请求被用户取消，停止重试。", exception)
            throw UserCancellationException(context.getString(R.string.gemini_error_request_cancelled), exception)
        }

        val explicitlyRejected =
            exception is HttpStatusCodeException &&
                OpenAIResponsesHttpFailurePolicy.classifySubmission(exception.statusCode) ==
                    OpenAIResponsesHttpFailurePolicy.SubmissionAction.RETRY_EXPLICIT_REJECTION
        if (!explicitlyRejected) {
            throw exception
        }

        val errorText = resolveRetryErrorText(context, exception)

        if (!enableRetry) {
            throw IOException(errorText, exception)
        }

        val newRetryCount = retryCount + 1
        if (newRetryCount > maxRetries) {
            logError("$errorText 且达到最大重试次数($maxRetries)", exception)
            throw IOException(
                context.getString(R.string.gemini_error_connection_timeout, maxRetries, errorText),
                exception
            )
        }

        val retryDelayMs = LlmRetryPolicy.nextDelayMs(newRetryCount)
        AppLogger.w(TAG, "$errorText，将在 ${retryDelayMs}ms 后进行第 $newRetryCount 次重试...", exception)
        if (!shouldSuppressKeyPoolRateLimitNotice(apiKeyProvider, exception, TAG)) {
            onNonFatalError(buildRetryMessage(errorText, newRetryCount))
        }
        delay(retryDelayMs)
        return newRetryCount
    }

    /** 发送消息到Gemini API */
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
        val requestId = System.currentTimeMillis().toString()
        // 重置输出token计数（保留输入历史缓存）
        tokenCacheManager.addOutputTokens(-tokenCacheManager.outputTokenCount)
        
        onTokensUpdated(
                tokenCacheManager.totalInputTokenCount,
                tokenCacheManager.cachedInputTokenCount,
                tokenCacheManager.outputTokenCount
        )

        AppLogger.d(TAG, "发送消息到Gemini API, 模型: $modelName")

        val maxRetries = LlmRetryPolicy.MAX_RETRY_ATTEMPTS
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()
        val requestSavepointId = "attempt_${UUID.randomUUID().toString().replace("-", "")}"

        suspend fun emitSavepoint(id: String) {
            eventChannel.emit(TextStreamEvent(TextStreamEventType.SAVEPOINT, id))
        }

        suspend fun emitRollback(id: String) {
            if (receivedContent.isNotEmpty()) {
                receivedContent.setLength(0)
            }
            eventChannel.emit(TextStreamEvent(TextStreamEventType.ROLLBACK, id))
        }

        // 捕获stream collector的引用
        val streamCollector = this

        // 状态更新函数 - 在Stream中我们使用emit来传递连接状态
        val emitConnectionStatus: (String) -> Unit = { status ->
            // 这里可以根据需要处理连接状态，例如记录日志
            logDebug("连接状态: $status")
        }

        emitConnectionStatus(context.getString(R.string.gemini_connecting))
        emitSavepoint(requestSavepointId)

        while (retryCount <= maxRetries) {
            // 在循环开始时检查是否已被取消
            if (streamSessionGate.isCancelled(session)) {
                logError("请求被用户取消，停止重试。")
                throw UserCancellationException(context.getString(R.string.gemini_error_request_cancelled))
            }
            val responseAttemptState = GeminiResponseAttemptState(modelName)
            var attemptCall: Call? = null
            
            try {
                if (retryCount > 0) {
                    AppLogger.d(
                        TAG,
                        "【Gemini 重试】原子回滚后重新请求，本轮已撤回内容长度: ${receivedContent.length}"
                    )
                }

                val requestBody = createRequestBody(context, chatHistory, modelParameters, enableThinking, availableTools, preserveThinkInHistory)
                onTokensUpdated(
                        tokenCacheManager.totalInputTokenCount,
                        tokenCacheManager.cachedInputTokenCount,
                        tokenCacheManager.outputTokenCount
                )
                val request = createRequest(context, requestBody, stream, requestId) // 根据stream参数决定使用流式还是非流式

                val call = client.newCall(request)
                attemptCall = call
                streamSessionGate.bindCall(session, call) {
                    if (!call.isCanceled()) call.cancel()
                }

                emitConnectionStatus(context.getString(R.string.gemini_connecting))

                val startTime = System.currentTimeMillis()
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = call.execute()
                    streamSessionGate.bindResponse(session, response) { response.close() }
                    try {
                        val duration = System.currentTimeMillis() - startTime
                        AppLogger.d(TAG, "收到初始响应, 耗时: ${duration}ms, 状态码: ${response.code}")

                        emitConnectionStatus(context.getString(R.string.gemini_connected_success))

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: context.getString(R.string.gemini_error_no_error_details)
                            val errorSummary = LlmLogPrivacy.summarizeProviderError(errorBody)
                            logError("API请求失败: ${errorSummary.format(response.code)}")
                            // 4xx错误仍保留单独的异常类型，具体是否重试由统一策略决定
                            if (response.code in 400..499) {
                                throw NonRetriableException(
                                    context.getString(
                                        R.string.gemini_error_api_request_failed,
                                        response.code,
                                        errorSummary.exceptionDetail(),
                                    ),
                                    statusCode = response.code
                                )
                            }
                            // 5xx 只能证明服务端失败，不能证明请求未被接受；不得重复 POST。
                            throw IOException(
                                context.getString(
                                    R.string.gemini_error_api_request_failed,
                                    response.code,
                                    errorSummary.exceptionDetail(),
                                )
                            )
                        }

                        // 根据stream参数处理响应
                        if (stream) {
                            // 处理流式响应
                            processStreamingResponse(
                                context = context,
                                session = session,
                                response = response,
                                streamCollector = streamCollector,
                                requestId = requestId,
                                onTokensUpdated = onTokensUpdated,
                                receivedContent = receivedContent,
                                responseAttemptState = responseAttemptState,
                            )
                        } else {
                            // 处理非流式响应并转换为Stream
                            processNonStreamingResponse(
                                context = context,
                                response = response,
                                streamCollector = streamCollector,
                                requestId = requestId,
                                onTokensUpdated = onTokensUpdated,
                                receivedContent = receivedContent,
                                responseAttemptState = responseAttemptState,
                            )
                        }
                    } finally {
                        response.close()
                        streamSessionGate.clearResponse(session, response)
                        AppLogger.d(TAG, "关闭响应连接")
                    }
                }

                logFinalOutput(receivedContent, "Gemini final output summary: ")
                return@stream
            } catch (e: Exception) {
                lastException = e
                emitRollback(requestSavepointId)
                retryCount = handleRetryableError(
                    context,
                    session,
                    e,
                    retryCount,
                    maxRetries,
                    enableRetry,
                    onNonFatalError
                ) { errorText, retryNumber ->
                    context.getString(R.string.provider_error_retry_message, errorText, retryNumber)
                }
            } finally {
                attemptCall?.let { call -> streamSessionGate.clearCall(session, call) }
            }
        }

        logError("重试${maxRetries}次后仍然失败", lastException)
        throw IOException(
            context.getString(
                R.string.gemini_error_connection_timeout,
                maxRetries,
                lastException?.message ?: context.getString(R.string.provider_error_network_interrupted)
            )
        )
        } finally {
            streamSessionGate.complete(session)
        }
        }
        return responseStream.withEventChannel(eventChannel)
    }

    /** 创建可测试、稳定序列化的请求 JSON；HTTP 层只负责把该对象编码为请求体。 */
    internal fun createRequestJson(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            availableTools: List<ToolPrompt>? = null,
            preserveThinkInHistory: Boolean = false
    ): JSONObject {
        val json = JSONObject()

        // 添加工具定义
        val tools = JSONArray()
        
        // 添加 Function Calling 工具（如果启用且有可用工具）
        if (enableToolCall && availableTools != null && availableTools.isNotEmpty()) {
            val functionDeclarations = buildToolDefinitionsForGemini(availableTools)
            if (functionDeclarations.length() > 0) {
                tools.put(JSONObject().apply {
                    put("function_declarations", functionDeclarations)
                })
                logDebug("已添加 ${functionDeclarations.length()} 个 Function Declarations")
            }
        }
        
        // 添加 Google Search grounding 工具（如果启用）
        if (enableGoogleSearch) {
            tools.put(JSONObject().apply {
                put("googleSearch", JSONObject())
            })
            logDebug("已启用 Google Search Grounding")
        }
        
        // 将 tools 添加到请求中，并保存用于token计算
        val toolsJson = if (tools.length() > 0) {
            json.put("tools", tools)
            tools.toString()
        } else {
            null
        }

        val (contentsResult, _) = buildContentsAndCountTokens(chatHistory, toolsJson, preserveThinkInHistory)
        val (contentsArray, systemInstruction) = contentsResult

        if (systemInstruction != null) {
            json.put("systemInstruction", systemInstruction)
        }
        json.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 如果启用了思考模式，则为Gemini模型添加特定的`thinkingConfig`参数
        if (enableThinking) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("includeThoughts", true)
            generationConfig.put("thinkingConfig", thinkingConfig)
            logDebug("已为Gemini模型启用“思考模式”。")
        }

        // 添加模型参数
        val duplicateParameter =
            modelParameters
                .filter { it.isEnabled }
                .groupingBy { it.apiName.trim() }
                .eachCount()
                .entries
                .firstOrNull { (name, count) -> name.isEmpty() || count > 1 }
        if (duplicateParameter != null) {
            throw IllegalArgumentException(
                "Gemini request contains an empty or duplicate parameter '${duplicateParameter.key}'"
            )
        }
        for (param in modelParameters.filter { it.isEnabled }.sortedBy { it.apiName }) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            generationConfig.put(
                                    "temperature",
                                    (param.currentValue as Number).toFloat()
                            )
                    "top_p" ->
                            generationConfig.put("topP", (param.currentValue as Number).toFloat())
                    "top_k" -> generationConfig.put("topK", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            generationConfig.put(
                                    "maxOutputTokens",
                                    (param.currentValue as Number).toInt()
                            )
                    else -> {
                        when (param.valueType) {
                            com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                                val raw = param.currentValue.toString().trim()
                                val parsed: Any =
                                    try {
                                        when {
                                            raw.startsWith("{") -> JSONObject(raw)
                                            raw.startsWith("[") -> JSONArray(raw)
                                            else ->
                                                throw IllegalArgumentException(
                                                    "Gemini OBJECT parameter ${param.apiName} is not JSON"
                                                )
                                        }
                                } catch (e: Exception) {
                                    logError("Gemini OBJECT参数解析失败: ${param.apiName}", e)
                                    throw IllegalArgumentException(
                                        "Gemini OBJECT parameter ${param.apiName} is invalid JSON",
                                        e,
                                    )
                                }
                                if (param.category == ParameterCategory.OTHER) {
                                    json.put(param.apiName, parsed)
                                } else {
                                    generationConfig.put(param.apiName, parsed)
                                }
                            }
                            else -> generationConfig.put(param.apiName, param.currentValue)
                        }
                    }
                }
            }
        }

        json.put("generationConfig", generationConfig)
        return json
    }

    /** 创建请求体 */
    private fun createRequestBody(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            availableTools: List<ToolPrompt>? = null,
            preserveThinkInHistory: Boolean = false
    ): RequestBody {
        val jsonString =
            createRequestJson(
                context = context,
                chatHistory = chatHistory,
                modelParameters = modelParameters,
                enableThinking = enableThinking,
                availableTools = availableTools,
                preserveThinkInHistory = preserveThinkInHistory,
            ).toString()
        return jsonString.toByteArray(Charsets.UTF_8).toRequestBody(JSON)
    }

    /** 创建HTTP请求 */
    private suspend fun createRequest(
            context: Context,
            requestBody: RequestBody,
            isStreaming: Boolean,
            requestId: String
    ): Request {
        // 确定请求URL
        val baseUrl = determineBaseUrl(apiEndpoint)
        val method = if (isStreaming) "streamGenerateContent" else "generateContent"
        val requestUrl = "$baseUrl/v1beta/models/$modelName:$method"

        AppLogger.d(TAG, "请求URL: $requestUrl")

        // 创建Request Builder
        val builder = Request.Builder()

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        // 添加API密钥
        val currentApiKey = apiKeyProvider.getApiKey()
        val finalUrl =
                if (requestUrl.contains("?")) {
                    "$requestUrl&key=$currentApiKey"
                } else {
                    "$requestUrl?key=$currentApiKey"
                }

        val request = builder.url(finalUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

        AppLogger.d(
            TAG,
            "Gemini request summary: ${LlmLogPrivacy.summarizeRequestBody(requestBody).format()}"
        )
        AppLogger.d(
            TAG,
            context.getString(
                R.string.gemini_request_headers,
                HttpLogSanitizer.headersForLog(request.headers)
            )
        )
        return request
    }

    /** 确定基础URL */
    private fun determineBaseUrl(endpoint: String): String {
        return try {
            val url = URL(endpoint)
            val port = if (url.port != -1) ":${url.port}" else ""
            "${url.protocol}://${url.host}${port}"
        } catch (e: Exception) {
            logError("解析API端点失败", e)
            throw IllegalArgumentException("Gemini API endpoint is invalid", e)
        }
    }

    /** 处理API流式响应 */
    private suspend fun processStreamingResponse(
            context: Context,
            session: ProviderStreamSessionGate.Session,
            response: Response,
            streamCollector: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            receivedContent: StringBuilder,
            responseAttemptState: GeminiResponseAttemptState,
    ) {
        AppLogger.d(TAG, "开始处理响应流")
        val responseBody = response.body ?: throw IOException(context.getString(R.string.gemini_response_empty))
        val reader = responseBody.charStream().buffered()

        // 注意：不再使用fullContent累积所有内容
        var lineCount = 0
        var dataCount = 0
        var jsonCount = 0
        var contentCount = 0

        // 恢复JSON累积逻辑，用于处理分段JSON
        val completeJsonBuilder = StringBuilder()
        var isCollectingJson = false
        var jsonDepth = 0
        var jsonStartSymbol = ' ' // 记录JSON是以 { 还是 [ 开始的

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    lineCount++
                    // 检查是否已取消
                    if (streamSessionGate.isCancelled(session)) {
                        throw UserCancellationException(
                            context.getString(R.string.gemini_error_request_cancelled)
                        )
                    }

                    // 处理SSE数据
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        dataCount++

                        // 跳过结束标记
                        if (data == "[DONE]") {
                            logDebug("收到流结束标记 [DONE]")
                            return@forEach
                        }

                        try {
                            // 立即解析每个SSE数据行的JSON
                            val json = JSONObject(data)
                            jsonCount++

                            val content =
                                extractContentFromJson(
                                    context = context,
                                    json = json,
                                    requestId = requestId,
                                    onTokensUpdated = onTokensUpdated,
                                    responseAttemptState = responseAttemptState,
                                )
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("提取SSE内容，长度: ${content.length}")
                                receivedContent.append(content)

                                // 只发送新增的内容
                                streamCollector.emit(content)
                            }
                        } catch (e: IOException) {
                            throw e
                        } catch (e: Exception) {
                            logError("解析SSE响应数据失败: ${e.message}", e)
                            throw e
                        }
                    } else if (line.trim().isNotEmpty()) {
                        // 处理可能分段的JSON数据
                        val trimmedLine = line.trim()

                        // 检查是否开始收集JSON
                        if (!isCollectingJson &&
                                        (trimmedLine.startsWith("{") || trimmedLine.startsWith("["))
                        ) {
                            isCollectingJson = true
                            jsonDepth = 0
                            completeJsonBuilder.clear()
                            jsonStartSymbol = trimmedLine[0]
                            logDebug("开始收集JSON，起始符号: $jsonStartSymbol")
                        }

                        if (isCollectingJson) {
                            completeJsonBuilder.append(trimmedLine)

                            // 更新JSON深度
                            for (char in trimmedLine) {
                                if (char == '{' || char == '[') jsonDepth++
                                if (char == '}' || char == ']') jsonDepth--
                            }

                            // 尝试作为完整JSON解析
                            val possibleComplete = completeJsonBuilder.toString()
                            try {
                                if (jsonDepth == 0) {
                                    logDebug("尝试解析完整JSON: ${possibleComplete.take(50)}...")
                                    val jsonContent =
                                            if (jsonStartSymbol == '[') {
                                                JSONArray(possibleComplete)
                                            } else {
                                                JSONObject(possibleComplete)
                                            }

                                    // 解析成功，处理内容
                                    logDebug("成功解析完整JSON，长度: ${possibleComplete.length}")

                                    when (jsonContent) {
                                        is JSONArray -> {
                                            // 处理JSON数组
                                            for (i in 0 until jsonContent.length()) {
                                                val jsonObject = jsonContent.optJSONObject(i)
                                                if (jsonObject != null) {
                                                    jsonCount++
                                                    val content =
                                                            extractContentFromJson(
                                                                context = context,
                                                                json = jsonObject,
                                                                requestId = requestId,
                                                                onTokensUpdated = onTokensUpdated,
                                                                responseAttemptState =
                                                                    responseAttemptState,
                                                            )
                                                    if (content.isNotEmpty()) {
                                                        contentCount++
                                                        logDebug(
                                                                "从JSON数组[$i]提取内容，长度: ${content.length}"
                                                        )
                                                        receivedContent.append(content)

                                                        // 只发送这个单独对象产生的内容
                                                        streamCollector.emit(content)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 解析成功后重置收集器
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            } catch (e: IOException) {
                                throw e
                            } catch (e: Exception) {
                                // JSON尚未完整，继续收集
                                if (jsonDepth > 0) {
                                    // 仍在收集，这是预期的
                                    logDebug("继续收集JSON，当前深度: $jsonDepth")
                                } else {
                                    // 深度为0但解析失败，可能是无效JSON
                                    logError("JSON解析失败: ${e.message}", e)
                                    throw e
                                }
                            }
                        }
                    }
                }
            }

            AppLogger.d(TAG, "响应处理完成: 共${lineCount}行, ${jsonCount}个JSON块, 提取${contentCount}个内容块")

            // 检查是否还有未解析完的JSON
            if (isCollectingJson && completeJsonBuilder.isNotEmpty()) {
                try {
                    val finalJson = completeJsonBuilder.toString()
                    AppLogger.d(TAG, "处理最终收集的JSON，长度: ${finalJson.length}")

                    val jsonContent =
                            if (jsonStartSymbol == '[') {
                                JSONArray(finalJson)
                            } else {
                                JSONObject(finalJson)
                            }
                    // 处理内容
                    when (jsonContent) {
                        is JSONArray -> {
                            for (i in 0 until jsonContent.length()) {
                                val jsonObject = jsonContent.optJSONObject(i) ?: continue
                                jsonCount++
                                val content =
                                    extractContentFromJson(
                                        context = context,
                                        json = jsonObject,
                                        requestId = requestId,
                                        onTokensUpdated = onTokensUpdated,
                                        responseAttemptState = responseAttemptState,
                                    )
                                if (content.isNotEmpty()) {
                                    contentCount++
                                    logDebug("从最终JSON数组[$i]提取内容，长度: ${content.length}")
                                    receivedContent.append(content)
                                    streamCollector.emit(content)
                                }
                            }
                        }
                        is JSONObject -> {
                            jsonCount++
                            val content =
                                extractContentFromJson(
                                    context = context,
                                    json = jsonContent,
                                    requestId = requestId,
                                    onTokensUpdated = onTokensUpdated,
                                    responseAttemptState = responseAttemptState,
                                )
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("从最终JSON对象提取内容，长度: ${content.length}")
                                receivedContent.append(content)
                                streamCollector.emit(content)
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    logError("解析最终收集的JSON失败: ${e.message}", e)
                    throw e
                }
            }

            // 确保思考模式正确结束
            if (responseAttemptState.isInThinkingMode) {
                logDebug("流结束时仍在思考模式，添加结束标签")
                receivedContent.append("</think>")
                streamCollector.emit("</think>")
                responseAttemptState.isInThinkingMode = false
            }
            responseAttemptState.finishMetadataTag()?.let { metadataTag ->
                receivedContent.append(metadataTag)
                streamCollector.emit(metadataTag)
                contentCount++
            }
            
            // 确保至少发送一次内容
            if (contentCount == 0) {
                throw IOException(context.getString(R.string.gemini_response_empty))
            }
        } catch (e: Exception) {
            logError("处理响应时发生异常: ${e.message}", e)
            throw e
        }
    }

    /** 处理API非流式响应 */
    private suspend fun processNonStreamingResponse(
            context: Context,
            response: Response,
            streamCollector: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            receivedContent: StringBuilder,
            responseAttemptState: GeminiResponseAttemptState,
    ) {
        AppLogger.d(TAG, "开始处理非流式响应")
        val responseBody = response.body ?: throw IOException(context.getString(R.string.gemini_response_empty))
        
        try {
            val responseText = responseBody.string()
            logDebug("收到非流式响应；${LlmLogPrivacy.summarizeText(responseText).format()}")
            
            // 解析JSON响应
            val json = JSONObject(responseText)
            
            // 提取内容
            val content =
                extractContentFromJson(
                    context = context,
                    json = json,
                    requestId = requestId,
                    onTokensUpdated = onTokensUpdated,
                    responseAttemptState = responseAttemptState,
                )
            val finalContent = StringBuilder(content)
            if (responseAttemptState.isInThinkingMode) {
                finalContent.append("</think>")
                responseAttemptState.isInThinkingMode = false
            }
            responseAttemptState.finishMetadataTag()?.let(finalContent::append)
            
            if (finalContent.isNotEmpty()) {
                receivedContent.append(finalContent)
                
                // 直接发送整个内容块，下游会自己处理
                streamCollector.emit(finalContent.toString())
                
                logDebug("非流式响应处理完成，总长度: ${finalContent.length}")
            } else {
                throw IOException(context.getString(R.string.gemini_response_empty))
            }
        } catch (e: Exception) {
            logError("处理非流式响应时发生异常: ${e.message}", e)
            throw e
        }
    }

    /** 从Gemini响应JSON中提取内容 */
    private suspend fun extractContentFromJson(
        context: Context,
        json: JSONObject,
        requestId: String,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        responseAttemptState: GeminiResponseAttemptState,
    ): String {
        val contentBuilder = StringBuilder()
        val searchSourcesBuilder = StringBuilder()

        try {
            throwIfGeminiErrorPayload(context, json)

            // 提取候选项
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                logDebug("未找到候选项")
                return ""
            }

            // 处理第一个candidate
            val candidate = candidates.getJSONObject(0)
            
            // 提取 Google Search grounding metadata（搜索来源信息）
            if (enableGoogleSearch) {
                val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                if (groundingMetadata != null) {
                    // 提取搜索查询
                    val webSearchQueries = groundingMetadata.optJSONArray("webSearchQueries")
                    if (webSearchQueries != null && webSearchQueries.length() > 0) {
                        searchSourcesBuilder.append("\n<search>\n\n")
                        searchSourcesBuilder.append(context.getString(R.string.gemini_search_sources_title))

                        for (i in 0 until webSearchQueries.length()) {
                            val query = webSearchQueries.optString(i)
                            searchSourcesBuilder.append(context.getString(R.string.gemini_search_query, query))
                            logDebug("搜索查询 [$i]: $query")
                        }
                        
                        // 提取搜索结果的URL来源
                        val groundingSupports = groundingMetadata.optJSONArray("groundingSupports")
                        if (groundingSupports != null && groundingSupports.length() > 0) {
                            searchSourcesBuilder.append(context.getString(R.string.gemini_reference_sources_title))
                            
                            for (i in 0 until groundingSupports.length()) {
                                val support = groundingSupports.getJSONObject(i)
                                val segment = support.optJSONObject("segment")
                                val groundingChunkIndices = support.optJSONArray("groundingChunkIndices")
                                
                                // 如果有chunk indices，提取对应的URL
                                if (groundingChunkIndices != null) {
                                    for (j in 0 until groundingChunkIndices.length()) {
                                        val chunkIndex = groundingChunkIndices.getInt(j)
                                        val retrievalMetadata = groundingMetadata.optJSONObject("retrievalMetadata")
                                        if (retrievalMetadata != null) {
                                            val webDynamicRetrievalScore = retrievalMetadata.optDouble("webDynamicRetrievalScore", -1.0)
                                            if (webDynamicRetrievalScore > 0) {
                                                logDebug("搜索动态检索分数: $webDynamicRetrievalScore")
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 提取 grounding chunks（包含URL）
                            val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                            if (groundingChunks != null && groundingChunks.length() > 0) {
                                for (i in 0 until groundingChunks.length()) {
                                    val chunk = groundingChunks.getJSONObject(i)
                                    val web = chunk.optJSONObject("web")
                                    if (web != null) {
                                        val uri = web.optString("uri", "")
                                        val title = web.optString("title", "")
                                        if (uri.isNotEmpty()) {
                                            if (title.isNotEmpty()) {
                                                searchSourcesBuilder.append("${i + 1}. [${title}](${uri})\n")
                                            } else {
                                                searchSourcesBuilder.append("${i + 1}. <${uri}>\n")
                                            }
                                            logDebug("搜索来源 [$i]: $title - $uri")
                                        }
                                    }
                                }
                            }
                        }
                        
                        searchSourcesBuilder.append("\n</search>\n\n")
                    }
                }
            }

            // 检查finish_reason
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty() && finishReason != "STOP") {
                logDebug("收到完成原因: $finishReason")
            }

            // 提取content对象
            val content = candidate.optJSONObject("content")
            if (content == null) {
                logDebug("未找到content对象")
                return ""
            }

            // 提取parts数组
            val parts = content.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                logDebug("未找到parts数组或为空")
                return ""
            }
            responseAttemptState.appendResponseParts(parts)

            // 遍历parts，提取text内容和functionCall
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                val isThought = part.optBoolean("thought", false)
                val functionCall = part.optJSONObject("functionCall")

                 val inlineData = part.optJSONObject("inline_data") ?: part.optJSONObject("inlineData")
                 if (inlineData != null) {
                     val mimeType = inlineData.optString("mime_type", inlineData.optString("mimeType", ""))
                     val b64 = inlineData.optString("data", "")
                     if (mimeType.startsWith("image/", ignoreCase = true) && b64.isNotEmpty()) {
                         if (responseAttemptState.isInThinkingMode) {
                             contentBuilder.append("</think>")
                             responseAttemptState.isInThinkingMode = false
                         }
                         val bytes = try {
                             Base64.decode(b64, Base64.DEFAULT)
                         } catch (_: Exception) {
                             null
                         }
                         if (bytes != null && bytes.isNotEmpty()) {
                             val uri = writeOutputImage(bytes, mimeType, "gemini_image_$i")
                             if (uri != null) {
                                 contentBuilder.append("\n![gemini_image_$i](${uri})\n")
                             }
                         }
                         continue
                     }
                 }

                // 处理 functionCall（流式转换为XML）
                if (functionCall != null && enableToolCall) {
                    val toolName = functionCall.optString("name", "")
                    if (toolName.isNotEmpty()) {
                        // 工具调用必须在思考模式之外，如果当前在思考中，先关闭
                        if (responseAttemptState.isInThinkingMode) {
                            contentBuilder.append("</think>")
                            responseAttemptState.isInThinkingMode = false
                            logDebug("检测到工具调用，提前结束思考模式")
                        }
                        
                        // 输出工具开始标签
                        val toolTagName = ChatMarkupRegex.generateRandomToolTagName()
                        contentBuilder.append("\n<$toolTagName name=\"$toolName\">")
                        
                        // 使用 StreamingJsonXmlConverter 流式转换参数
                        val args = functionCall.optJSONObject("args")
                        if (args != null) {
                            val converter = StreamingJsonXmlConverter()
                            val argsJson = args.toString()
                            val events = converter.feed(argsJson)
                            events.forEach { event ->
                                when (event) {
                                    is StreamingJsonXmlConverter.Event.Tag -> contentBuilder.append(event.text)
                                    is StreamingJsonXmlConverter.Event.Content -> contentBuilder.append(event.text)
                                }
                            }
                            // 刷新剩余内容
                            val flushEvents = converter.flush()
                            flushEvents.forEach { event ->
                                when (event) {
                                    is StreamingJsonXmlConverter.Event.Tag -> contentBuilder.append(event.text)
                                    is StreamingJsonXmlConverter.Event.Content -> contentBuilder.append(event.text)
                                }
                            }
                        }
                        
                        // 输出工具结束标签
                        contentBuilder.append("\n</$toolTagName>\n")
                        logDebug("Gemini FunctionCall流式转XML: $toolName")

                    }
                }

                if (text.isNotEmpty()) {
                    // 处理思考模式状态切换
                    if (isThought && !responseAttemptState.isInThinkingMode) {
                        // 开始思考模式
                        contentBuilder.append("<think>")
                        responseAttemptState.isInThinkingMode = true
                        logDebug("开始思考模式")
                    } else if (!isThought && responseAttemptState.isInThinkingMode) {
                        // 结束思考模式
                        contentBuilder.append("</think>")
                        responseAttemptState.isInThinkingMode = false
                        logDebug("结束思考模式")
                    }
                    
                    // 添加文本内容
                    contentBuilder.append(text)
                    
                    if (isThought) {
                        logDebug("提取思考内容，长度=${text.length}")
                    } else {
                        logDebug("提取文本，长度=${text.length}")
                    }

                    // 估算token
                    val tokens = ChatUtils.estimateTokenCount(text)
                    tokenCacheManager.addOutputTokens(tokens)
                    onTokensUpdated(
                            tokenCacheManager.totalInputTokenCount,
                            tokenCacheManager.cachedInputTokenCount,
                            tokenCacheManager.outputTokenCount
                    )
                }
            }

            // 提取实际的token使用数据
            GeminiUsagePayloadAdapter.parse(json.optJSONObject("usageMetadata"))?.let { parsed ->
                val previous = latestProviderUsageSnapshot
                val totalInputTokens =
                    parsed.totalInputTokens?.toLong()
                        ?: previous?.totalInputTokens
                        ?: 0L
                val cachedInputTokens =
                    parsed.cachedInputTokens?.toLong()
                        ?: previous?.cacheReadTokens
                        ?: 0L
                val uncachedInputTokens =
                    parsed.uncachedInputTokens?.toLong()
                        ?: previous?.uncachedInputTokens
                        ?: (totalInputTokens - cachedInputTokens).coerceAtLeast(0L)
                val outputTokens =
                    parsed.outputTokens?.toLong()
                        ?: previous?.outputTokens
                        ?: tokenCacheManager.outputTokenCount.toLong()
                val reasoningTokens =
                    parsed.reasoningTokens?.toLong()
                        ?: previous?.reasoningTokens
                        ?: 0L
                val cacheMetricState =
                    when {
                        parsed.cacheMetricState == ProviderCacheMetricState.INVALID ||
                            previous?.cacheMetricState == ProviderCacheMetricState.INVALID ->
                            ProviderCacheMetricState.INVALID
                        parsed.cacheMetricState == ProviderCacheMetricState.REPORTED ||
                            previous?.cacheMetricState == ProviderCacheMetricState.REPORTED ->
                            ProviderCacheMetricState.REPORTED
                        else -> ProviderCacheMetricState.NOT_REPORTED
                    }
                val snapshot =
                    ProviderUsageSnapshot(
                        providerModel = providerModel,
                        protocol = com.ai.assistance.operit.data.model.ApiProtocol.PROVIDER_NATIVE,
                        totalInputTokens = uncachedInputTokens + cachedInputTokens,
                        uncachedInputTokens = uncachedInputTokens,
                        cacheReadTokens = cachedInputTokens,
                        cacheWriteTokens = 0L,
                        outputTokens = outputTokens,
                        reasoningTokens = reasoningTokens,
                        cacheMetricState = cacheMetricState,
                        source = ProviderUsageSource.PROVIDER,
                    )
                latestProviderUsageSnapshot = snapshot

                tokenCacheManager.updateActualTokens(
                    actualInput =
                        uncachedInputTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    cachedInput =
                        cachedInputTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
                parsed.outputTokens?.let {
                    tokenCacheManager.setOutputTokens(it)
                }

                logDebug(
                    "API实际Token使用: 输入=${snapshot.uncachedInputTokens}, " +
                        "缓存=${snapshot.cacheReadTokens}, 输出=${snapshot.outputTokens}, " +
                        "推理=${snapshot.reasoningTokens}, " +
                        "cache_metric=${snapshot.cacheMetricState}"
                )

                onTokensUpdated(
                    snapshot.totalInputTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    snapshot.cacheReadTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    tokenCacheManager.outputTokenCount
                )
            }

            // 将搜索来源拼接到内容最前面
            val finalContent = if (searchSourcesBuilder.isNotEmpty()) {
                searchSourcesBuilder.toString() + contentBuilder.toString()
            } else {
                contentBuilder.toString()
            }
            
            return finalContent
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            logError("提取内容时发生错误: ${e.message}", e)
            throw e
        }
    }

    /** 获取模型列表 */
    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
            context = context,
            apiKey = apiKeyProvider.getApiKey(),
            apiEndpoint = apiEndpoint,
            apiProviderType = ApiProviderType.GOOGLE
        )
    }

    override suspend fun testConnection(context: Context): Result<String> {
        return try {
            // 通过发送一条短消息来测试完整的连接、认证和API端点。
            // 这比getModelsList更可靠，因为它直接命中了聊天API。
            // 提供一个通用的系统提示，以防止某些需要它的模型出现错误。
            val testHistory = listOf("system" to "You are a helpful assistant.").toPromptTurns()
            val stream = sendMessage(
                context,
                testHistory + PromptTurn(kind = PromptTurnKind.USER, content = "Hi"),
                emptyList(),
                false,
                false,
                null,
                onTokensUpdated = { _, _, _ -> },
                onNonFatalError = {},
                enableRetry = false
            )

            // 消耗流以确保连接有效。
            // 对 "Hi" 的响应应该很短，所以这会很快完成。
            var hasReceivedData = false
            stream.collect {
                hasReceivedData = true
            }

            // 某些情况下，即使连接成功，也可能不会返回任何数据（例如，如果模型只处理了提示而没有生成响应）。
            // 因此，只要不抛出异常，我们就认为连接成功。
            Result.success(context.getString(R.string.gemini_connection_success))
        } catch (e: Exception) {
            logError("连接测试失败", e)
            Result.failure(IOException(context.getString(R.string.gemini_connection_test_failed, e.message ?: ""), e))
        }
    }
}
