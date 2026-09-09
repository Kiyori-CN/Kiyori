package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ai.assistance.operit.data.model.ConversationAuditEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventPayloadEntity
import com.ai.assistance.operit.data.model.ConversationAuditPayloadEntity
import com.ai.assistance.operit.data.model.ConversationAuditSealEntity
import com.ai.assistance.operit.data.model.ConversationMessageProjectionEntity
import com.ai.assistance.operit.data.model.ConversationMessageRevisionEntity
import kotlinx.coroutines.flow.Flow

data class ConversationAuditStoredBytesByChat(
    val chatId: String,
    val storedByteCount: Long,
)

@Dao
interface ConversationAuditDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuditIfAbsent(audit: ConversationAuditEntity): Long

    @Query("SELECT * FROM conversation_audits WHERE chatId = :chatId")
    suspend fun getAudit(chatId: String): ConversationAuditEntity?

    @Query("SELECT * FROM conversation_audits WHERE chatId = :chatId")
    fun observeAudit(chatId: String): Flow<ConversationAuditEntity?>

    @Query(
        """
        SELECT * FROM conversation_audits
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getAllAudits(): List<ConversationAuditEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayloadIfAbsent(payload: ConversationAuditPayloadEntity): Long

    @Query(
        "SELECT * FROM conversation_audit_payloads WHERE payloadSha256 = :payloadSha256"
    )
    suspend fun getPayload(payloadSha256: String): ConversationAuditPayloadEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: ConversationAuditEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEventPayloads(refs: List<ConversationAuditEventPayloadEntity>)

    @Query(
        """
        SELECT * FROM conversation_audit_events
        WHERE chatId = :chatId
        ORDER BY sequenceNumber DESC
        LIMIT 1
        """
    )
    suspend fun getLastEvent(chatId: String): ConversationAuditEventEntity?

    @Query(
        """
        SELECT * FROM conversation_audit_events
        WHERE chatId = :chatId
        ORDER BY sequenceNumber DESC
        LIMIT 1
        """
    )
    fun observeLastEvent(chatId: String): Flow<ConversationAuditEventEntity?>

    @Query(
        """
        SELECT * FROM conversation_audit_events
        WHERE chatId = :chatId
            AND (:beforeSequenceExclusive IS NULL OR sequenceNumber < :beforeSequenceExclusive)
        ORDER BY sequenceNumber DESC
        LIMIT :limit
        """
    )
    suspend fun getEventPage(
        chatId: String,
        beforeSequenceExclusive: Long?,
        limit: Int,
    ): List<ConversationAuditEventEntity>

    @Query(
        """
        SELECT * FROM conversation_audit_events
        WHERE chatId = :chatId
            AND sequenceNumber > :afterSequenceExclusive
        ORDER BY sequenceNumber ASC
        """
    )
    suspend fun getEventsAfterSequence(
        chatId: String,
        afterSequenceExclusive: Long,
    ): List<ConversationAuditEventEntity>

    @Query(
        """
        SELECT * FROM conversation_audit_events
        WHERE chatId = :chatId
        ORDER BY sequenceNumber ASC
        """
    )
    suspend fun getAllEvents(chatId: String): List<ConversationAuditEventEntity>

    @Query(
        """
        SELECT * FROM conversation_audit_events
        WHERE chatId = :chatId
            AND sequenceNumber <= :throughSequenceInclusive
        ORDER BY sequenceNumber ASC
        """
    )
    suspend fun getEventsThroughSequence(
        chatId: String,
        throughSequenceInclusive: Long,
    ): List<ConversationAuditEventEntity>

    @Query(
        """
        SELECT MAX(sequenceNumber)
        FROM conversation_audit_events
        WHERE chatId = :chatId
            AND messageTimestamp IS NOT NULL
            AND messageTimestamp <= :messageTimestampInclusive
        """
    )
    suspend fun getLastSequenceAtMessageTimestamp(
        chatId: String,
        messageTimestampInclusive: Long,
    ): Long?

    @Query(
        """
        SELECT * FROM conversation_audit_event_payloads
        WHERE eventId = :eventId
        ORDER BY ordinal ASC
        """
    )
    suspend fun getEventPayloads(eventId: String): List<ConversationAuditEventPayloadEntity>

    @Query(
        """
        UPDATE conversation_audits
        SET completenessStatus = :completenessStatus,
            eventCount = eventCount + 1,
            lastSequenceNumber = :newSequenceNumber,
            chainHeadSha256 = :newChainHeadSha256,
            updatedAt = :updatedAt,
            lastFailureCode = COALESCE(:lastFailureCode, lastFailureCode)
        WHERE chatId = :chatId
            AND lastSequenceNumber = :expectedSequenceNumber
            AND chainHeadSha256 = :expectedChainHeadSha256
        """
    )
    suspend fun advanceAudit(
        chatId: String,
        expectedSequenceNumber: Long,
        expectedChainHeadSha256: String,
        newSequenceNumber: Long,
        newChainHeadSha256: String,
        completenessStatus: String,
        updatedAt: Long,
        lastFailureCode: String?,
    ): Int

    @Query(
        """
        UPDATE conversation_audits
        SET completenessStatus = :completenessStatus,
            lastFailureCode = COALESCE(:lastFailureCode, lastFailureCode),
            updatedAt = :updatedAt
        WHERE chatId = :chatId
        """
    )
    suspend fun updateCompleteness(
        chatId: String,
        completenessStatus: String,
        lastFailureCode: String?,
        updatedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: ConversationMessageRevisionEntity)

    @Upsert
    suspend fun upsertProjection(projection: ConversationMessageProjectionEntity)

    @Query(
        """
        SELECT * FROM conversation_message_projections
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        """
    )
    suspend fun getProjection(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): ConversationMessageProjectionEntity?

    @Query(
        """
        SELECT * FROM conversation_message_revisions
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        ORDER BY revisionNumber ASC
        """
    )
    suspend fun getRevisions(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): List<ConversationMessageRevisionEntity>

    @Query(
        """
        SELECT * FROM conversation_message_revisions
        WHERE chatId = :chatId
            AND messageTimestamp = :messageTimestamp
            AND variantIndex = :variantIndex
        ORDER BY revisionNumber DESC
        LIMIT 1
        """
    )
    suspend fun getLatestRevision(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): ConversationMessageRevisionEntity?

    @Query(
        """
        SELECT * FROM conversation_message_revisions
        WHERE chatId = :chatId
        ORDER BY messageTimestamp ASC, variantIndex ASC, revisionNumber ASC
        """
    )
    suspend fun getAllRevisionsForChat(chatId: String): List<ConversationMessageRevisionEntity>

    @Query(
        """
        SELECT * FROM conversation_message_projections
        WHERE chatId = :chatId
        ORDER BY messageTimestamp ASC, variantIndex ASC
        """
    )
    suspend fun getAllProjectionsForChat(chatId: String): List<ConversationMessageProjectionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSeal(seal: ConversationAuditSealEntity)

    @Query(
        """
        UPDATE conversation_audits
        SET latestSealSequenceNumber = :sequenceNumber,
            updatedAt = :updatedAt
        WHERE chatId = :chatId
            AND lastSequenceNumber = :sequenceNumber
        """
    )
    suspend fun markSealed(
        chatId: String,
        sequenceNumber: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT * FROM conversation_audit_seals
        WHERE chatId = :chatId
        ORDER BY sequenceNumber DESC
        LIMIT 1
        """
    )
    suspend fun getLatestSeal(chatId: String): ConversationAuditSealEntity?

    @Query(
        """
        SELECT * FROM conversation_audit_seals
        WHERE chatId = :chatId
        ORDER BY sequenceNumber ASC
        """
    )
    suspend fun getAllSeals(chatId: String): List<ConversationAuditSealEntity>

    @Query(
        """
        SELECT payload.*
        FROM conversation_audit_payloads AS payload
        WHERE NOT EXISTS (
            SELECT 1 FROM conversation_audit_event_payloads AS event_ref
            WHERE event_ref.payloadSha256 = payload.payloadSha256
        ) AND NOT EXISTS (
            SELECT 1 FROM conversation_message_revisions AS revision_ref
            WHERE revision_ref.contentPayloadSha256 = payload.payloadSha256
        )
        """
    )
    suspend fun getUnreferencedPayloads(): List<ConversationAuditPayloadEntity>

    /** 必须与聊天删除处于同一事务，级联删除后无法再恢复这份候选集合。 */
    @Query(
        """
        SELECT event_ref.payloadSha256 AS payloadSha256
        FROM conversation_audit_events AS event
        INNER JOIN conversation_audit_event_payloads AS event_ref ON event_ref.eventId = event.eventId
        WHERE event.chatId = :chatId
        UNION
        SELECT contentPayloadSha256 AS payloadSha256
        FROM conversation_message_revisions
        WHERE chatId = :chatId
        """
    )
    suspend fun getPayloadHashesForChat(chatId: String): List<String>

    /** 主键限定候选；两个 NOT EXISTS 在已有引用索引上找到首个引用即停止。 */
    @Query(
        """
        SELECT payload.* FROM conversation_audit_payloads AS payload
        WHERE payload.payloadSha256 IN (:payloadHashes)
        AND NOT EXISTS (
            SELECT 1 FROM conversation_audit_event_payloads AS event_ref
            WHERE event_ref.payloadSha256 = payload.payloadSha256
        ) AND NOT EXISTS (
            SELECT 1 FROM conversation_message_revisions AS revision_ref
            WHERE revision_ref.contentPayloadSha256 = payload.payloadSha256
        )
        """
    )
    suspend fun getUnreferencedPayloadsAmong(payloadHashes: List<String>): List<ConversationAuditPayloadEntity>

    @Query(
        "DELETE FROM conversation_audit_payloads WHERE payloadSha256 = :payloadSha256"
    )
    suspend fun deletePayloadMetadata(payloadSha256: String): Int

    @Query(
        """
        SELECT COALESCE(SUM(storedByteCount), 0)
        FROM conversation_audit_payloads
        """
    )
    suspend fun getTotalStoredPayloadBytes(): Long

    @Query("SELECT COUNT(*) FROM conversation_audits")
    suspend fun getAuditCount(): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM conversation_audits
        WHERE completenessStatus NOT IN ('COMPLETE', 'IN_PROGRESS')
        """
    )
    suspend fun getAbnormalAuditCount(): Int

    @Query(
        """
        SELECT refs.chatId AS chatId, SUM(payload.storedByteCount) AS storedByteCount
        FROM (
            SELECT event.chatId AS chatId, event_ref.payloadSha256 AS payloadSha256
            FROM conversation_audit_events AS event
            INNER JOIN conversation_audit_event_payloads AS event_ref
                ON event_ref.eventId = event.eventId
            UNION
            SELECT revision.chatId AS chatId, revision.contentPayloadSha256 AS payloadSha256
            FROM conversation_message_revisions AS revision
        ) AS refs
        INNER JOIN conversation_audit_payloads AS payload
            ON payload.payloadSha256 = refs.payloadSha256
        GROUP BY refs.chatId
        ORDER BY storedByteCount DESC, refs.chatId ASC
        LIMIT 1
        """
    )
    suspend fun getLargestAuditByStoredBytes(): ConversationAuditStoredBytesByChat?

    @Query(
        """
        SELECT COALESCE(SUM(payload.storedByteCount), 0)
        FROM (
            SELECT event.chatId AS chatId, event_ref.payloadSha256 AS payloadSha256
            FROM conversation_audit_events AS event
            INNER JOIN conversation_audit_event_payloads AS event_ref
                ON event_ref.eventId = event.eventId
            UNION
            SELECT revision.chatId AS chatId, revision.contentPayloadSha256 AS payloadSha256
            FROM conversation_message_revisions AS revision
        ) AS refs
        INNER JOIN conversation_audit_payloads AS payload
            ON payload.payloadSha256 = refs.payloadSha256
        WHERE refs.chatId = :chatId
        """
    )
    suspend fun getStoredPayloadBytesForChat(chatId: String): Long
}
