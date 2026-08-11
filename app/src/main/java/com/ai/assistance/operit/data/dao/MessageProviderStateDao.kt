package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.ai.assistance.operit.data.model.MessageProviderStateEntity

@Dao
interface MessageProviderStateDao {
    @Query(
        """
        SELECT * FROM message_provider_states
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        LIMIT 1
        """
    )
    suspend fun getState(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): MessageProviderStateEntity?

    @Query(
        """
        SELECT * FROM message_provider_states
        WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp
        ORDER BY variantIndex ASC
        """
    )
    suspend fun getStatesForMessage(
        chatId: String,
        messageTimestamp: Long,
    ): List<MessageProviderStateEntity>

    @Query(
        """
        SELECT * FROM message_provider_states
        WHERE remoteResponseId = :remoteResponseId
        LIMIT 1
        """
    )
    suspend fun getStateByRemoteResponse(
        remoteResponseId: String,
    ): MessageProviderStateEntity?
}
