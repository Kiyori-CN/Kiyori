package com.ai.assistance.operit.data.audit

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptHookAuditObservation
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 把同步 Prompt Hook 调度边界接入持久化审计。
 *
 * Hook API 本身是同步的，因此观察器会阻塞到事件落盘完成。若关键审计写入失败，异常继续向上传播，
 * 本轮请求不会带着不可追溯的 Hook 转换继续发给 Provider。
 */
object ConversationAuditPromptHookBridge {
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) {
            return
        }
        synchronized(this) {
            if (installed) {
                return
            }
            val applicationContext = context.applicationContext
            val repository = ConversationAuditRepository.from(applicationContext)
            val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
            PromptHookRegistry.setAuditObserver { observation ->
                val chatId = observation.input.chatId?.takeIf { value -> value.isNotBlank() }
                    ?: return@setAuditObserver
                runBlocking(Dispatchers.IO) {
                    appendObservation(
                        repository = repository,
                        gson = gson,
                        chatId = chatId,
                        observation = observation,
                    )
                }
            }
            installed = true
        }
    }

    private suspend fun appendObservation(
        repository: ConversationAuditRepository,
        gson: Gson,
        chatId: String,
        observation: PromptHookAuditObservation,
    ) {
        repository.appendEvent(
            ConversationAuditEventRequest(
                chatId = chatId,
                category = "HOOK",
                eventType = "PROMPT_HOOK_STARTED",
                actor = observation.hookId,
                summary =
                    "${observation.hookLabel} ${observation.hookId} 开始处理 " +
                        observation.input.stage,
                completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                occurredAt = observation.startedAt,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "hook_input",
                            role = "context",
                            value = gson.toJson(snapshot(observation.input)),
                            mediaType = "application/json",
                        )
                    ),
            )
        )
        val error = observation.error
        repository.appendEvent(
            ConversationAuditEventRequest(
                chatId = chatId,
                category = "HOOK",
                eventType =
                    if (error == null) {
                        "PROMPT_HOOK_COMPLETED"
                    } else {
                        "PROMPT_HOOK_FAILED"
                    },
                actor = observation.hookId,
                summary =
                    if (error == null) {
                        "${observation.hookLabel} ${observation.hookId} 已完成"
                    } else {
                        "${observation.hookLabel} ${observation.hookId} 执行失败"
                    },
                terminalState = if (error == null) "COMPLETED" else "FAILED",
                completeness = ConversationAuditCompletenessStatus.IN_PROGRESS,
                failureCode = error?.javaClass?.simpleName,
                occurredAt = observation.completedAt,
                payloads =
                    if (error == null) {
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "hook_output",
                                role = "context",
                                value = gson.toJson(snapshot(requireNotNull(observation.output))),
                                mediaType = "application/json",
                            )
                        )
                    } else {
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "hook_error",
                                role = "error",
                                value = error.stackTraceToString(),
                                mediaType = "text/x-java-stacktrace",
                            )
                        )
                    },
            )
        )
    }

    private fun snapshot(context: PromptHookContext): Map<String, Any?> =
        linkedMapOf(
            "stage" to context.stage,
            "chatId" to context.chatId,
            "functionType" to context.functionType,
            "promptFunctionType" to context.promptFunctionType,
            "useEnglish" to context.useEnglish,
            "rawInput" to context.rawInput,
            "processedInput" to context.processedInput,
            "chatHistory" to
                context.chatHistory.map { turn ->
                    mapOf(
                        "kind" to turn.kind.name,
                        "role" to turn.role,
                        "content" to turn.content,
                        "toolName" to turn.toolName,
                        "metadata" to turn.metadata,
                    )
                },
            "preparedHistory" to
                context.preparedHistory.map { turn ->
                    mapOf(
                        "kind" to turn.kind.name,
                        "role" to turn.role,
                        "content" to turn.content,
                        "toolName" to turn.toolName,
                        "metadata" to turn.metadata,
                    )
                },
            "systemPrompt" to context.systemPrompt,
            "toolPrompt" to context.toolPrompt,
            "modelParameters" to context.modelParameters,
            "availableTools" to context.availableTools,
            "metadata" to context.metadata,
        )
}
