package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 消息实体类，用于Room数据库存储聊天消息 */
@Entity(
        tableName = "messages",
        foreignKeys =
                [
                        ForeignKey(
                                entity = ChatEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["chatId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("chatId"), Index(value = ["chatId", "timestamp"])]
)
data class MessageEntity(
        @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
        val chatId: String,
        val sender: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val orderIndex: Int, // 保持消息顺序
        val roleName: String = "", // 角色名字段
        val selectedVariantIndex: Int = 0,
        val provider: String = "", // 供应商
        val modelName: String = "", // 模型名称
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
        val displayMode: String = ChatMessageDisplayMode.NORMAL.name,
        val isFavorite: Boolean = false,
) {
    /** 转换为ChatMessage对象（供UI层使用） */
    fun toChatMessage(): ChatMessage {
        return ChatMessage(
            sender = sender, 
            content = content, 
            timestamp = timestamp,
            roleName = roleName,
            selectedVariantIndex = selectedVariantIndex,
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
            displayMode =
                runCatching { ChatMessageDisplayMode.valueOf(displayMode) }
                    .getOrDefault(ChatMessageDisplayMode.NORMAL),
            isFavorite = isFavorite,
        )
    }

    companion object {
        /** 从ChatMessage创建MessageEntity */
        fun fromChatMessage(
            chatId: String,
            message: ChatMessage,
            orderIndex: Int,
            messageId: Long = 0
        ): MessageEntity {
            return MessageEntity(
                    messageId = messageId,
                    chatId = chatId,
                    sender = message.sender,
                    content = message.content,
                    timestamp = message.timestamp,
                    orderIndex = orderIndex,
                    roleName = message.roleName,
                    selectedVariantIndex = message.selectedVariantIndex,
                    provider = message.provider,
                    modelName = message.modelName,
                    inputTokens = message.inputTokens,
                    outputTokens = message.outputTokens,
                    cachedInputTokens = message.cachedInputTokens,
                    providerRequestCount = message.providerRequestCount,
                    providerUsageRequestCount = message.providerUsageRequestCount,
                    providerCacheMetricRequestCount =
                            message.providerCacheMetricRequestCount,
                    providerCacheMetricPromptTokens =
                            message.providerCacheMetricPromptTokens,
                    providerTotalInputTokens = message.providerTotalInputTokens,
                    providerUncachedInputTokens =
                            message.providerUncachedInputTokens,
                    providerCacheReadTokens = message.providerCacheReadTokens,
                    providerCacheWriteTokens = message.providerCacheWriteTokens,
                    providerOutputTokens = message.providerOutputTokens,
                    providerReasoningTokens = message.providerReasoningTokens,
                    sentAt = message.sentAt,
                    outputDurationMs = message.outputDurationMs,
                    waitDurationMs = message.waitDurationMs,
                    completedAt = message.completedAt,
                    displayMode = message.displayMode.name,
                    isFavorite = message.isFavorite,
            )
        }
    }
}
