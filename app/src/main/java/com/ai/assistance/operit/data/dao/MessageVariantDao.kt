package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ai.assistance.operit.data.model.MessageVariantEntity

@Dao
interface MessageVariantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: MessageVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<MessageVariantEntity>)

    @Query(
        """
        INSERT INTO message_variants (
            chatId,
            messageTimestamp,
            variantIndex,
            content,
            roleName,
            provider,
            modelName,
            inputTokens,
            outputTokens,
            cachedInputTokens,
            sentAt,
            outputDurationMs,
            waitDurationMs,
            completedAt
        )
        SELECT
            :targetChatId,
            messageTimestamp,
            variantIndex,
            content,
            roleName,
            provider,
            modelName,
            inputTokens,
            outputTokens,
            cachedInputTokens,
            sentAt,
            outputDurationMs,
            waitDurationMs,
            completedAt
        FROM message_variants
        WHERE chatId = :sourceChatId
            AND (:upToTimestampInclusive IS NULL OR messageTimestamp <= :upToTimestampInclusive)
        """
    )
    suspend fun copyVariantsToChat(
        sourceChatId: String,
        targetChatId: String,
        upToTimestampInclusive: Long?,
    )

    @Update
    suspend fun updateVariant(variant: MessageVariantEntity)

    @Query(
        """
        UPDATE message_variants
        SET content = :content
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        """
    )
    suspend fun updateVariantContent(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
        content: String,
    ): Int

    @Query(
        """
        DELETE FROM provider_executions
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        """
    )
    suspend fun deleteProviderExecutionForVariant(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    )

    @Query(
        "DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND variantIndex = :variantIndex"
    )
    suspend fun deleteVariantRow(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    )

    @Transaction
    suspend fun deleteVariant(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ) {
        deleteProviderExecutionForVariant(chatId, messageTimestamp, variantIndex)
        deleteVariantRow(chatId, messageTimestamp, variantIndex)
    }

    @Query(
        """
        DELETE FROM provider_executions
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex > 0
        """
    )
    suspend fun deleteAlternateProviderExecutionsForMessage(
        chatId: String,
        messageTimestamp: Long,
    )

    @Query("DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp")
    suspend fun deleteVariantRowsForMessage(chatId: String, messageTimestamp: Long)

    @Transaction
    suspend fun deleteVariantsForMessage(chatId: String, messageTimestamp: Long) {
        deleteAlternateProviderExecutionsForMessage(chatId, messageTimestamp)
        deleteVariantRowsForMessage(chatId, messageTimestamp)
    }

    @Query(
        """
        DELETE FROM provider_executions
        WHERE chatId = :chatId
            AND messageTimestamp >= :messageTimestamp
            AND variantIndex > 0
        """
    )
    suspend fun deleteAlternateProviderExecutionsFrom(
        chatId: String,
        messageTimestamp: Long,
    )

    @Query("DELETE FROM message_variants WHERE chatId = :chatId AND messageTimestamp >= :messageTimestamp")
    suspend fun deleteVariantRowsFrom(chatId: String, messageTimestamp: Long)

    @Transaction
    suspend fun deleteVariantsFrom(chatId: String, messageTimestamp: Long) {
        deleteAlternateProviderExecutionsFrom(chatId, messageTimestamp)
        deleteVariantRowsFrom(chatId, messageTimestamp)
    }

    @Query("DELETE FROM provider_executions WHERE chatId = :chatId AND variantIndex > 0")
    suspend fun deleteAllAlternateProviderExecutionsForChat(chatId: String)

    @Query("DELETE FROM message_variants WHERE chatId = :chatId")
    suspend fun deleteAllVariantRowsForChat(chatId: String)

    @Transaction
    suspend fun deleteAllVariantsForChat(chatId: String) {
        deleteAllAlternateProviderExecutionsForChat(chatId)
        deleteAllVariantRowsForChat(chatId)
    }
}
