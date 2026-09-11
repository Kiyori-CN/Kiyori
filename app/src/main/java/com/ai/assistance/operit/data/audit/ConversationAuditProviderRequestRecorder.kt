package com.ai.assistance.operit.data.audit

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.ProviderRequestContext
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在真正进入 Provider 前固化最终语义请求。
 *
 * 这里记录 Provider 能看到的语义输入，不记录 TCP/SSE 字节、API Key、认证头原文或 Cookie。
 * 持久化调用必须先于 [com.ai.assistance.operit.api.chat.llmprovider.AIService.sendMessage] 成功；
 * 否则继续发出请求会重新制造不可追溯的黑盒。
 */
object ConversationAuditProviderRequestRecorder {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private val credentialIdentifier =
        Regex(
            "(?i)(authorization|cookie|password|passwd|api[_-]?key|access[_-]?token|" +
                "refresh[_-]?token|auth[_-]?token|client[_-]?secret|private[_-]?key|" +
                "request[_-]?signature|signature|secret)"
        )

    suspend fun record(
        context: Context,
        providerRequestContext: ProviderRequestContext?,
        requestHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        availableTools: List<ToolPrompt>?,
        providerModel: String,
        modelConfig: ModelConfigData,
        enableThinking: Boolean,
        stream: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        // 非持久化独立任务没有聊天审计身份；其诊断仍由原有 Provider 日志 owner 负责。
        val requestContext = providerRequestContext ?: return@withContext
        val snapshot =
            buildSnapshot(
                providerRequestContext = requestContext,
                requestHistory = requestHistory,
                modelParameters = modelParameters,
                availableTools = availableTools,
                providerModel = providerModel,
                modelConfig = modelConfig,
                enableThinking = enableThinking,
                stream = stream,
            )
        ConversationAuditRepository.from(context).appendEvent(
            ConversationAuditEventRequest(
                chatId = requestContext.chatId,
                category = "PROVIDER",
                eventType = "FINAL_PROVIDER_SEMANTIC_REQUEST",
                actor = providerModel.substringBefore(':').ifBlank { "PROVIDER" },
                summary =
                    "已固化第 ${requestContext.hopOrdinal + 1} 次 Provider 语义请求：" +
                        providerModel,
                messageTimestamp = requestContext.messageTimestamp,
                variantIndex = requestContext.variantIndex,
                localExecutionId = requestContext.localExecutionId,
                completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "provider_semantic_request",
                            role = "request",
                            value = gson.toJson(snapshot),
                            mediaType = "application/json",
                        )
                    ),
            )
        )
    }

    internal fun buildSnapshot(
        providerRequestContext: ProviderRequestContext,
        requestHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        availableTools: List<ToolPrompt>?,
        providerModel: String,
        modelConfig: ModelConfigData,
        enableThinking: Boolean,
        stream: Boolean,
    ): Map<String, Any?> =
        linkedMapOf(
            "providerModel" to providerModel,
            "enableThinking" to enableThinking,
            "stream" to stream,
            "providerRequestContext" to
                linkedMapOf(
                    "localExecutionId" to providerRequestContext.localExecutionId,
                    "chatId" to providerRequestContext.chatId,
                    "messageTimestamp" to providerRequestContext.messageTimestamp,
                    "variantIndex" to providerRequestContext.variantIndex,
                    "hopOrdinal" to providerRequestContext.hopOrdinal,
                ),
            "modelConfig" to modelConfigSnapshot(modelConfig),
            "modelParameters" to modelParameters.map(::modelParameterSnapshot),
            "availableTools" to availableTools?.map(::toolSnapshot),
            "requestHistory" to requestHistory.map(::promptTurnSnapshot),
        )

    private fun modelConfigSnapshot(config: ModelConfigData): Map<String, Any?> =
        linkedMapOf(
            "id" to config.id,
            "name" to config.name,
            "apiEndpoint" to ConversationAuditRedactor.redactText(config.apiEndpoint).value,
            "modelName" to config.modelName,
            "apiProviderType" to config.apiProviderType.name,
            "apiProviderTypeId" to config.apiProviderTypeId,
            "apiKeyConfigured" to config.apiKey.isNotEmpty(),
            "useMultipleApiKeys" to config.useMultipleApiKeys,
            "apiKeyPoolSize" to config.apiKeyPool.size,
            "currentKeyIndex" to config.currentKeyIndex,
            "keyRotationMode" to config.keyRotationMode,
            "hasCustomParameters" to config.hasCustomParameters,
            "maxTokensEnabled" to config.maxTokensEnabled,
            "temperatureEnabled" to config.temperatureEnabled,
            "topPEnabled" to config.topPEnabled,
            "topKEnabled" to config.topKEnabled,
            "presencePenaltyEnabled" to config.presencePenaltyEnabled,
            "frequencyPenaltyEnabled" to config.frequencyPenaltyEnabled,
            "repetitionPenaltyEnabled" to config.repetitionPenaltyEnabled,
            "maxTokens" to config.maxTokens,
            "temperature" to config.temperature,
            "topP" to config.topP,
            "topK" to config.topK,
            "presencePenalty" to config.presencePenalty,
            "frequencyPenalty" to config.frequencyPenalty,
            "repetitionPenalty" to config.repetitionPenalty,
            "customParameters" to config.customParameters,
            "customHeaders" to config.customHeaders,
            "contextLength" to config.contextLength,
            "maxContextLength" to config.maxContextLength,
            "enableMaxContextMode" to config.enableMaxContextMode,
            "summaryTokenThreshold" to config.summaryTokenThreshold,
            "enableSummary" to config.enableSummary,
            "enableSummaryByMessageCount" to config.enableSummaryByMessageCount,
            "summaryMessageCountThreshold" to config.summaryMessageCountThreshold,
            "summaryCustomRules" to config.summaryCustomRules,
            "mnnForwardType" to config.mnnForwardType,
            "mnnThreadCount" to config.mnnThreadCount,
            "llamaThreadCount" to config.llamaThreadCount,
            "llamaContextSize" to config.llamaContextSize,
            "llamaBatchSize" to config.llamaBatchSize,
            "llamaUBatchSize" to config.llamaUBatchSize,
            "llamaGpuLayers" to config.llamaGpuLayers,
            "llamaUseMmap" to config.llamaUseMmap,
            "llamaFlashAttention" to config.llamaFlashAttention,
            "llamaKvUnified" to config.llamaKvUnified,
            "llamaOffloadKqv" to config.llamaOffloadKqv,
            "enableDirectImageProcessing" to config.enableDirectImageProcessing,
            "enableDirectAudioProcessing" to config.enableDirectAudioProcessing,
            "enableDirectVideoProcessing" to config.enableDirectVideoProcessing,
            "enableGoogleSearch" to config.enableGoogleSearch,
            "enableClaude1hPromptCache" to config.enableClaude1hPromptCache,
            "enableToolCall" to config.enableToolCall,
            "requestLimitPerMinute" to config.requestLimitPerMinute,
            "maxConcurrentRequests" to config.maxConcurrentRequests,
        )

    private fun modelParameterSnapshot(parameter: ModelParameter<*>): Map<String, Any?> {
        val credentialBearing =
            sequenceOf(parameter.id, parameter.name, parameter.apiName)
                .any { value -> credentialIdentifier.containsMatchIn(value) }
        return linkedMapOf(
            "id" to parameter.id,
            "name" to parameter.name,
            "apiName" to parameter.apiName,
            "description" to parameter.description,
            "defaultValue" to
                if (credentialBearing) "[REDACTED:credential]" else parameter.defaultValue,
            "currentValue" to
                if (credentialBearing) "[REDACTED:credential]" else parameter.currentValue,
            "isEnabled" to parameter.isEnabled,
            "valueType" to parameter.valueType.name,
            "minValue" to parameter.minValue,
            "maxValue" to parameter.maxValue,
            "category" to parameter.category.name,
            "isCustom" to parameter.isCustom,
        )
    }

    private fun toolSnapshot(tool: ToolPrompt): Map<String, Any?> =
        linkedMapOf(
            "name" to tool.name,
            "description" to tool.description,
            "parameters" to tool.parameters,
            "parametersStructured" to
                tool.parametersStructured?.map { parameter ->
                    val credentialBearing =
                        credentialIdentifier.containsMatchIn(parameter.name)
                    linkedMapOf(
                        "name" to parameter.name,
                        "type" to parameter.type,
                        "description" to parameter.description,
                        "required" to parameter.required,
                        "default" to
                            if (credentialBearing && parameter.default != null) {
                                "[REDACTED:credential]"
                            } else {
                                parameter.default
                            },
                    )
                },
            "details" to tool.details,
            "notes" to tool.notes,
        )

    private fun promptTurnSnapshot(turn: PromptTurn): Map<String, Any?> =
        linkedMapOf(
            "kind" to turn.kind.name,
            "role" to turn.role,
            "content" to turn.content,
            "toolName" to turn.toolName,
            "metadata" to turn.metadata,
        )
}
