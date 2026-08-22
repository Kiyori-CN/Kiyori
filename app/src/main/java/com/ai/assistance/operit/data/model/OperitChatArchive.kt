package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.util.LocalDateTimeSerializer
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class OperitChatArchive(
    val archiveType: String = ARCHIVE_TYPE,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val chats: List<OperitArchivedChat>,
) {
    companion object {
        const val ARCHIVE_TYPE = "operit_chat_archive"
        const val CURRENT_FORMAT_VERSION = 4
    }
}

@Serializable
data class OperitArchivedChat(
    val id: String,
    val title: String,
    val messages: List<OperitArchivedMessage>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val currentWindowSize: Int = 0,
    val providerRequestCount: Int = 0,
    val providerUsageRequestCount: Int = 0,
    val providerCacheMetricRequestCount: Int = 0,
    val providerCacheMetricPromptTokens: Long = 0L,
    val providerTotalInputTokens: Long = 0L,
    val providerUncachedInputTokens: Long = 0L,
    val providerCacheReadTokens: Long = 0L,
    val providerCacheWriteTokens: Long = 0L,
    val providerOutputTokens: Long = 0L,
    val providerReasoningTokens: Long = 0L,
    val group: String? = null,
    val displayOrder: Long = 0L,
    val workspace: String? = null,
    val workspaceEnv: String? = null,
    val parentChatId: String? = null,
    val characterCardName: String? = null,
    val characterGroupId: String? = null,
    val locked: Boolean = false,
    val pinned: Boolean = false,
    val conversationAudit: OperitArchivedConversationAudit? = null,
) {
    fun toChatHistory(): ChatHistory {
        return ChatHistory(
            id = id,
            title = title,
            messages = messages.map { it.baseMessage },
            createdAt = createdAt,
            updatedAt = updatedAt,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            currentWindowSize = currentWindowSize,
            providerRequestCount = providerRequestCount,
            providerUsageRequestCount = providerUsageRequestCount,
            providerCacheMetricRequestCount = providerCacheMetricRequestCount,
            providerCacheMetricPromptTokens = providerCacheMetricPromptTokens,
            providerTotalInputTokens = providerTotalInputTokens,
            providerUncachedInputTokens = providerUncachedInputTokens,
            providerCacheReadTokens = providerCacheReadTokens,
            providerCacheWriteTokens = providerCacheWriteTokens,
            providerOutputTokens = providerOutputTokens,
            providerReasoningTokens = providerReasoningTokens,
            group = group,
            displayOrder = displayOrder,
            workspace = workspace,
            workspaceEnv = workspaceEnv,
            parentChatId = parentChatId,
            characterCardName = characterCardName,
            characterGroupId = characterGroupId,
            locked = locked,
            pinned = pinned,
        )
    }

    companion object {
        fun fromChatHistory(
            history: ChatHistory,
            messages: List<OperitArchivedMessage>,
            conversationAudit: OperitArchivedConversationAudit?,
        ): OperitArchivedChat {
            return OperitArchivedChat(
                id = history.id,
                title = history.title,
                messages = messages,
                createdAt = history.createdAt,
                updatedAt = history.updatedAt,
                inputTokens = history.inputTokens,
                outputTokens = history.outputTokens,
                currentWindowSize = history.currentWindowSize,
                providerRequestCount = history.providerRequestCount,
                providerUsageRequestCount = history.providerUsageRequestCount,
                providerCacheMetricRequestCount =
                    history.providerCacheMetricRequestCount,
                providerCacheMetricPromptTokens =
                    history.providerCacheMetricPromptTokens,
                providerTotalInputTokens = history.providerTotalInputTokens,
                providerUncachedInputTokens = history.providerUncachedInputTokens,
                providerCacheReadTokens = history.providerCacheReadTokens,
                providerCacheWriteTokens = history.providerCacheWriteTokens,
                providerOutputTokens = history.providerOutputTokens,
                providerReasoningTokens = history.providerReasoningTokens,
                group = history.group,
                displayOrder = history.displayOrder,
                workspace = history.workspace,
                workspaceEnv = history.workspaceEnv,
                parentChatId = history.parentChatId,
                characterCardName = history.characterCardName,
                characterGroupId = history.characterGroupId,
                locked = history.locked,
                pinned = history.pinned,
                conversationAudit = conversationAudit,
            )
        }
    }
}

@Serializable
data class OperitArchivedMessage(
    val baseMessage: ChatMessage,
    val variants: List<OperitArchivedMessageVariant> = emptyList(),
)

@Serializable
data class OperitArchivedMessageVariant(
    val variantIndex: Int,
    val content: String,
    val roleName: String = "",
    val provider: String = "",
    val modelName: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedInputTokens: Int = 0,
    val providerRequestCount: Int = 0,
    val providerUsageRequestCount: Int = 0,
    val providerCacheMetricRequestCount: Int = 0,
    val providerCacheMetricPromptTokens: Long = 0L,
    val providerTotalInputTokens: Long = 0L,
    val providerUncachedInputTokens: Long = 0L,
    val providerCacheReadTokens: Long = 0L,
    val providerCacheWriteTokens: Long = 0L,
    val providerOutputTokens: Long = 0L,
    val providerReasoningTokens: Long = 0L,
    val sentAt: Long = 0L,
    val outputDurationMs: Long = 0L,
    val waitDurationMs: Long = 0L,
    val completedAt: Long = 0L,
) {
    fun toEntity(chatId: String, messageTimestamp: Long): MessageVariantEntity {
        return MessageVariantEntity(
            chatId = chatId,
            messageTimestamp = messageTimestamp,
            variantIndex = variantIndex,
            content = content,
            roleName = roleName,
            provider = provider,
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            providerRequestCount = providerRequestCount,
            providerUsageRequestCount = providerUsageRequestCount,
            providerCacheMetricRequestCount = providerCacheMetricRequestCount,
            providerCacheMetricPromptTokens = providerCacheMetricPromptTokens,
            providerTotalInputTokens = providerTotalInputTokens,
            providerUncachedInputTokens = providerUncachedInputTokens,
            providerCacheReadTokens = providerCacheReadTokens,
            providerCacheWriteTokens = providerCacheWriteTokens,
            providerOutputTokens = providerOutputTokens,
            providerReasoningTokens = providerReasoningTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs,
            completedAt = completedAt,
        )
    }

    companion object {
        fun fromEntity(entity: MessageVariantEntity): OperitArchivedMessageVariant {
            return OperitArchivedMessageVariant(
                variantIndex = entity.variantIndex,
                content = entity.content,
                roleName = entity.roleName,
                provider = entity.provider,
                modelName = entity.modelName,
                inputTokens = entity.inputTokens,
                outputTokens = entity.outputTokens,
                cachedInputTokens = entity.cachedInputTokens,
                providerRequestCount = entity.providerRequestCount,
                providerUsageRequestCount = entity.providerUsageRequestCount,
                providerCacheMetricRequestCount =
                    entity.providerCacheMetricRequestCount,
                providerCacheMetricPromptTokens =
                    entity.providerCacheMetricPromptTokens,
                providerTotalInputTokens = entity.providerTotalInputTokens,
                providerUncachedInputTokens = entity.providerUncachedInputTokens,
                providerCacheReadTokens = entity.providerCacheReadTokens,
                providerCacheWriteTokens = entity.providerCacheWriteTokens,
                providerOutputTokens = entity.providerOutputTokens,
                providerReasoningTokens = entity.providerReasoningTokens,
                sentAt = entity.sentAt,
                outputDurationMs = entity.outputDurationMs,
                waitDurationMs = entity.waitDurationMs,
                completedAt = entity.completedAt,
            )
        }
    }
}
