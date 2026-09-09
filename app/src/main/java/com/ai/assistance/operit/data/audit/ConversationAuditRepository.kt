package com.ai.assistance.operit.data.audit

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.ai.assistance.operit.data.model.ConversationAuditEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventPayloadEntity
import com.ai.assistance.operit.data.model.ConversationAuditPayloadEntity
import com.ai.assistance.operit.data.model.ConversationAuditSealEntity
import com.ai.assistance.operit.data.model.ConversationAuditVisibility
import com.ai.assistance.operit.data.model.ConversationMessageProjectionEntity
import com.ai.assistance.operit.data.model.ConversationMessageRevisionEntity
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import com.ai.assistance.operit.data.model.OperitArchivedConversationAudit
import com.ai.assistance.operit.data.model.OperitArchivedConversationMessageRevision
import com.ai.assistance.operit.util.ChatUtils
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 对话审计事件、payload、完整性链和封印的唯一写入 owner。
 *
 * 调用方只有在本仓储提交成功后才能把对应内容投影到聊天 UI。
 */
class ConversationAuditRepository private constructor(
    private val database: AppDatabase,
    private val payloadStore: ConversationAuditPayloadStore,
    private val crypto: ConversationAuditCrypto,
) {
    private val dao = database.conversationAuditDao()
    private val chatMutexes = ConcurrentHashMap<String, Mutex>()
    /**
     * Serializes payload file writes, Room reference commits, failure cleanup, and collection.
     * Without one lifecycle lock, cleanup can delete a file between its atomic write and the
     * transaction that records its reference, or a second cleanup can observe already-deleted
     * metadata and turn an idempotent operation into a fatal exception.
     */
    private val payloadLifecycleMutex = Mutex()

    suspend fun ensureAudit(
        chatId: String,
        createdAt: Long,
        completeness: ConversationAuditCompletenessStatus =
            ConversationAuditCompletenessStatus.COMPLETE,
    ): ConversationAuditEntity {
        val now = System.currentTimeMillis()
        dao.insertAuditIfAbsent(
            ConversationAuditEntity(
                chatId = chatId,
                schemaVersion = SCHEMA_VERSION,
                completenessStatus = completeness.name,
                eventCount = 0L,
                lastSequenceNumber = 0L,
                chainHeadSha256 = "",
                latestSealSequenceNumber = 0L,
                createdAt = createdAt,
                updatedAt = maxOf(createdAt, now),
            )
        )
        return requireNotNull(dao.getAudit(chatId)) {
            "Conversation audit was not created for chat $chatId"
        }
    }

    suspend fun appendEvent(request: ConversationAuditEventRequest): ConversationAuditEventEntity {
        return payloadLifecycleMutex.withLock {
            chatMutex(request.chatId).withLock {
                appendEventLocked(request)
            }
        }
    }

    /**
     * Persist a recording gap when an audit append itself fails.
     *
     * This deliberately changes only the existing audit root: creating a synthetic event would
     * require the same failing payload/transaction path and could falsely claim that the missing
     * event was captured. A later append keeps the interrupted completeness because the merge
     * policy treats RECORDING_INTERRUPTED as monotonic.
     */
    suspend fun markRecordingInterrupted(
        chatId: String,
        failureCode: String = "RECORDING_INTERRUPTED",
        updatedAt: Long = System.currentTimeMillis(),
    ): Boolean =
        chatMutex(chatId).withLock {
            val audit = dao.getAudit(chatId) ?: return@withLock false
            val currentStatus =
                ConversationAuditCompletenessStatus.valueOf(audit.completenessStatus)
            val mergedStatus =
                ConversationAuditCompletenessPolicy.merge(
                    current = currentStatus,
                    requested = ConversationAuditCompletenessStatus.RECORDING_INTERRUPTED,
                )
            dao.updateCompleteness(
                chatId = chatId,
                completenessStatus = mergedStatus.name,
                lastFailureCode = failureCode,
                updatedAt = maxOf(updatedAt, audit.updatedAt),
            ) == 1
        }

    /**
     * 在固定锁序下原子提交业务投影变更与审计事件。
     *
     * mutation 先在 Room 事务内执行，随后追加事件；任一步失败都会回滚数据库事务。payload 文件
     * 已在获取审计 Mutex 前原子提交，事务失败时由无引用 payload 回收器清理。
     */
    suspend fun <T> mutateAndAppendEvent(
        request: ConversationAuditEventRequest,
        mutation: suspend () -> T,
    ): ConversationAuditMutationResult<T> {
        return payloadLifecycleMutex.withLock {
            val storedPayloads = storePayloads(request)
            chatMutex(request.chatId).withLock {
                database.withTransaction {
                    val value = mutation()
                    val event = appendEventInsideTransaction(request, storedPayloads)
                    ConversationAuditMutationResult(value = value, event = event)
                }
            }
        }
    }

    /**
     * 修订用户输入或 AI 当前 variant。
     *
     * 首次修订会先固化旧正文为 revision 0；新修订、审计事件、当前投影和聊天正文随后在同一
     * Room 事务中提交。这样聊天气泡可以变化，但原始事实不会被覆盖。
     */
    suspend fun reviseMessage(
        request: ConversationMessageRevisionRequest,
    ): ConversationMessageRevisionResult =
        payloadLifecycleMutex.withLock {
            chatMutex(request.chatId).withLock {
            val baseMessage =
                requireNotNull(
                    database
                        .chatContentDao()
                        .getMessageByTimestamp(request.chatId, request.messageTimestamp)
                ) {
                    "Message ${request.messageTimestamp} does not exist in chat ${request.chatId}"
                }
            require(baseMessage.sender == "user" || baseMessage.sender == "ai") {
                "Only user and AI messages can be revised"
            }
            require(baseMessage.sender == "ai" || request.variantIndex == 0) {
                "User messages cannot have answer variants"
            }
            val currentContent =
                if (request.variantIndex == 0) {
                    baseMessage.content
                } else {
                    requireNotNull(
                        database
                            .chatContentDao()
                            .getVariantForMessage(
                                request.chatId,
                                request.messageTimestamp,
                                request.variantIndex,
                            )
                    ) {
                        "Variant ${request.variantIndex} does not exist for message " +
                            request.messageTimestamp
                    }.content
                }
            require(request.newContent != currentContent) {
                "The revised message content is unchanged"
            }
            // 编辑弹窗绑定打开时的正文；不能覆盖等待保存期间由另一入口写入的新修订。
            check(request.expectedContent == null || request.expectedContent == currentContent) {
                "Message content changed while editing; reopen the message before saving"
            }

            val eventRequest =
                ConversationAuditEventRequest(
                    chatId = request.chatId,
                    category = request.category,
                    eventType = request.eventType,
                    actor = request.actor,
                    summary = request.summary,
                    messageTimestamp = request.messageTimestamp,
                    variantIndex = request.variantIndex,
                    terminalState = request.terminalState,
                    completeness = request.completeness,
                    failureCode = request.failureCode,
                    occurredAt = request.occurredAt,
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "before",
                                role = baseMessage.sender,
                                value = currentContent,
                            ),
                            ConversationAuditPayloadInput.text(
                                label = "after",
                                role = baseMessage.sender,
                                value = request.newContent,
                            ),
                        ) + request.additionalPayloads,
                )
            val storedPayloads = storePayloads(eventRequest)
            database.withTransaction {
                val event = appendEventInsideTransaction(eventRequest, storedPayloads)
                val latestRevision =
                    dao.getLatestRevision(
                        chatId = request.chatId,
                        messageTimestamp = request.messageTimestamp,
                        variantIndex = request.variantIndex,
                    )
                val beforePayload = storedPayloads[0].entity
                val afterPayload = storedPayloads[1].entity
                val baselineRevision =
                    if (latestRevision == null) {
                        val revision =
                            ConversationMessageRevisionEntity(
                            revisionId = UUID.randomUUID().toString(),
                            chatId = request.chatId,
                            messageTimestamp = request.messageTimestamp,
                            variantIndex = request.variantIndex,
                            revisionNumber = 0,
                            sender = baseMessage.sender,
                            contentPayloadSha256 = beforePayload.payloadSha256,
                            previousRevisionId = null,
                            auditEventId = event.eventId,
                            source = "ORIGINAL_CAPTURED_ON_FIRST_EDIT",
                            createdAt = request.occurredAt,
                        )
                        dao.insertRevision(revision)
                        revision
                    } else {
                        null
                    }
                val previousRevision = latestRevision ?: requireNotNull(baselineRevision)
                val revision =
                    ConversationMessageRevisionEntity(
                        revisionId = UUID.randomUUID().toString(),
                        chatId = request.chatId,
                        messageTimestamp = request.messageTimestamp,
                        variantIndex = request.variantIndex,
                        revisionNumber = previousRevision.revisionNumber + 1,
                        sender = baseMessage.sender,
                        contentPayloadSha256 = afterPayload.payloadSha256,
                        previousRevisionId = previousRevision.revisionId,
                        auditEventId = event.eventId,
                        source = request.source,
                        createdAt = request.occurredAt,
                    )
                dao.insertRevision(revision)
                dao.upsertProjection(
                    ConversationMessageProjectionEntity(
                        chatId = request.chatId,
                        messageTimestamp = request.messageTimestamp,
                        variantIndex = request.variantIndex,
                        currentRevisionId = revision.revisionId,
                        estimatedTokenCount = ChatUtils.estimateTokenCount(request.newContent),
                        updatedAt = request.occurredAt,
                    )
                )
                val updatedRows =
                    if (request.variantIndex == 0) {
                        database.messageDao().updateMessageContentByTimestamp(
                            chatId = request.chatId,
                            timestamp = request.messageTimestamp,
                            content = request.newContent,
                        )
                    } else {
                        database.messageVariantDao().updateVariantContent(
                            chatId = request.chatId,
                            messageTimestamp = request.messageTimestamp,
                            variantIndex = request.variantIndex,
                            content = request.newContent,
                        )
                    }
                check(updatedRows == 1) {
                    "The message projection disappeared while applying its revision"
                }
                database.chatDao().getChatById(request.chatId)?.let { chat ->
                    database.chatDao().updateChatMetadata(
                        chatId = request.chatId,
                        title = chat.title,
                        timestamp = request.occurredAt,
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize,
                    )
                }
                val seal = sealInsideTransaction(request.chatId, reason = request.sealReason)
                ConversationMessageRevisionResult(
                    event = event,
                    revision = revision,
                    projection =
                        requireNotNull(
                            dao.getProjection(
                                request.chatId,
                                request.messageTimestamp,
                                request.variantIndex,
                            )
                        ),
                    seal = seal,
                )
            }
            }
        }

    suspend fun appendThrowable(
        chatId: String,
        eventType: String,
        summary: String,
        throwable: Throwable,
        messageTimestamp: Long? = null,
        variantIndex: Int? = null,
        localExecutionId: String? = null,
        completeness: ConversationAuditCompletenessStatus =
            ConversationAuditCompletenessStatus.PARTIAL,
        occurredAt: Long = System.currentTimeMillis(),
    ): ConversationAuditEventEntity =
        appendEvent(
            ConversationAuditEventRequest(
                chatId = chatId,
                category = "ERROR",
                eventType = eventType,
                actor = "KIYORI",
                summary = summary,
                messageTimestamp = messageTimestamp,
                variantIndex = variantIndex,
                localExecutionId = localExecutionId,
                terminalState = "FAILED",
                completeness = completeness,
                failureCode = eventType,
                occurredAt = occurredAt,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "throwable",
                            role = "error",
                            value = throwable.stackTraceToString(),
                            mediaType = "text/x-java-stacktrace",
                        )
                    ),
            )
        )

    suspend fun appendUserAnnotation(
        chatId: String,
        text: String,
        parentEventId: String? = null,
    ): ConversationAuditEventEntity {
        require(text.isNotBlank()) { "Conversation audit annotation must not be blank" }
        val audit = requireNotNull(dao.getAudit(chatId)) {
            "Conversation audit does not exist: $chatId"
        }
        val event =
            appendEvent(
                ConversationAuditEventRequest(
                    chatId = chatId,
                    category = "USER_NOTE",
                    eventType = "USER_ANNOTATION_ADDED",
                    actor = "USER",
                    summary = "用户为对话审计添加了注释",
                    parentEventId = parentEventId,
                    completeness =
                        ConversationAuditCompletenessStatus.valueOf(
                            audit.completenessStatus
                        ),
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "annotation",
                                role = "user_note",
                                value = text,
                            )
                        ),
                )
            )
        seal(chatId, reason = "USER_ANNOTATION_ADDED")
        return event
    }

    suspend fun seal(
        chatId: String,
        reason: String,
    ): ConversationAuditSealEntity =
        chatMutex(chatId).withLock {
            database.withTransaction {
                sealInsideTransaction(chatId, reason)
            }
        }

    suspend fun verify(chatId: String): ConversationAuditVerificationResult =
        chatMutex(chatId).withLock {
            verifyLocked(chatId)
        }

    private suspend fun verifyLocked(chatId: String): ConversationAuditVerificationResult {
        val events = dao.getAllEvents(chatId)
        val audit =
            requireNotNull(dao.getAudit(chatId)) {
                "Conversation audit does not exist: $chatId"
            }
        if (audit.eventCount != events.size.toLong()) {
            return ConversationAuditVerificationResult.Invalid(
                eventId = null,
                reason = "event_count_mismatch",
            )
        }
        if (audit.lastSequenceNumber != events.size.toLong()) {
            return ConversationAuditVerificationResult.Invalid(
                eventId = null,
                reason = "last_sequence_mismatch",
            )
        }

        var previousHash = ConversationAuditHasher.EMPTY_CHAIN_SHA256
        val verifiedPayloadHashes = mutableSetOf<String>()
        val payloadsBySha256 = mutableMapOf<String, ConversationAuditPayloadEntity>()
        for (event in events) {
            if (event.previousEventSha256 != previousHash) {
                return ConversationAuditVerificationResult.Invalid(
                    eventId = event.eventId,
                    reason = "previous_event_hash_mismatch",
                )
            }
            val eventPayloadRefs = dao.getEventPayloads(event.eventId)
            eventPayloadRefs.forEach { ref ->
                val payload =
                    payloadsBySha256.getOrPut(ref.payloadSha256) {
                        dao.getPayload(ref.payloadSha256)
                            ?: return ConversationAuditVerificationResult.Invalid(
                                eventId = event.eventId,
                                reason = "payload_metadata_missing:${ref.payloadSha256}",
                            )
                    }
                if (verifiedPayloadHashes.add(ref.payloadSha256)) {
                    try {
                        payloadStore.read(payload)
                    } catch (error: ConversationAuditKeyUnavailableException) {
                        return ConversationAuditVerificationResult.Invalid(
                            eventId = event.eventId,
                            reason = "payload_key_unavailable:${ref.payloadSha256}",
                        )
                    } catch (error: Exception) {
                        return ConversationAuditVerificationResult.Invalid(
                            eventId = event.eventId,
                            reason = "payload_content_invalid:${ref.payloadSha256}",
                        )
                    }
                }
            }
            val refs =
                eventPayloadRefs.map { ref ->
                    ConversationAuditHashPayloadRef(
                        label = ref.label,
                        ordinal = ref.ordinal,
                        role = ref.role,
                        payloadSha256 = ref.payloadSha256,
                        mediaType = payloadsBySha256.getValue(ref.payloadSha256).mediaType,
                        encoding = payloadsBySha256.getValue(ref.payloadSha256).encoding,
                        plainByteCount =
                            payloadsBySha256.getValue(ref.payloadSha256).plainByteCount,
                    )
                }
            val expected =
                ConversationAuditHasher.eventHash(
                    ConversationAuditHashInput(
                        previousEventSha256 = event.previousEventSha256,
                        eventId = event.eventId,
                        chatId = event.chatId,
                        sequenceNumber = event.sequenceNumber,
                        occurredAt = event.occurredAt,
                        recordedAt = event.recordedAt,
                        category = event.category,
                        eventType = event.eventType,
                        actor = event.actor,
                        summary = event.summary,
                        messageTimestamp = event.messageTimestamp,
                        variantIndex = event.variantIndex,
                        localExecutionId = event.localExecutionId,
                        providerCallId = event.providerCallId,
                        parentEventId = event.parentEventId,
                        sourceChatId = event.sourceChatId,
                        sourceEventId = event.sourceEventId,
                        visibility = event.visibility,
                        terminalState = event.terminalState,
                        payloadRefs = refs,
                    )
                )
            if (expected != event.eventSha256) {
                return ConversationAuditVerificationResult.Invalid(
                    eventId = event.eventId,
                    reason = "event_hash_mismatch",
                )
            }
            previousHash = event.eventSha256
        }

        val expectedChainHead =
            if (events.isEmpty()) {
                ""
            } else {
                previousHash
            }
        if (audit.chainHeadSha256 != expectedChainHead) {
            return ConversationAuditVerificationResult.Invalid(
                eventId = events.lastOrNull()?.eventId,
                reason = "chain_head_mismatch",
            )
        }
        val seals = dao.getAllSeals(chatId)
        val eventHashesBySequence = events.associate { event -> event.sequenceNumber to event.eventSha256 }
        val localContinuationSequence =
            events
                .asSequence()
                .filter { event -> event.eventType == "IMPORTED_CONTINUATION" }
                .maxOfOrNull { event -> event.sequenceNumber }
        val hasLocalSeal =
            seals.any { seal ->
                localContinuationSequence == null ||
                    seal.sequenceNumber >= localContinuationSequence
            }
        val localSigningPublicKey =
            if (hasLocalSeal) {
                try {
                    crypto.requireSigningPublicKeyBase64()
                } catch (error: ConversationAuditKeyUnavailableException) {
                    return ConversationAuditVerificationResult.Invalid(
                        eventId = null,
                        reason = "signing_key_unavailable",
                    )
                }
            } else {
                null
            }
        for (seal in seals) {
            if (eventHashesBySequence[seal.sequenceNumber] != seal.rootSha256) {
                return ConversationAuditVerificationResult.Invalid(
                    eventId = null,
                    reason = "seal_root_mismatch",
                )
            }
            val isLocalSeal =
                localContinuationSequence == null ||
                    seal.sequenceNumber >= localContinuationSequence
            if (isLocalSeal && seal.publicKeyBase64 != localSigningPublicKey) {
                return ConversationAuditVerificationResult.Invalid(
                    eventId = null,
                    reason = "local_signing_key_mismatch",
                )
            }
            val valid =
                try {
                    crypto.verify(
                        value = seal.rootSha256.toByteArray(Charsets.US_ASCII),
                        signatureBase64 = seal.signatureBase64,
                        publicKeyBase64 = seal.publicKeyBase64,
                        algorithm = seal.signatureAlgorithm,
                    )
                } catch (error: Exception) {
                    false
                }
            if (!valid) {
                return ConversationAuditVerificationResult.Invalid(
                    eventId = null,
                    reason = "seal_signature_mismatch",
                )
            }
        }
        val latestSeal = seals.lastOrNull()
        if (audit.latestSealSequenceNumber != (latestSeal?.sequenceNumber ?: 0L)) {
            return ConversationAuditVerificationResult.Invalid(
                eventId = null,
                reason = "latest_seal_cursor_mismatch",
            )
        }
        return ConversationAuditVerificationResult.Valid(
            eventCount = events.size,
            chainHeadSha256 = expectedChainHead,
            latestSealSequenceNumber = latestSeal?.sequenceNumber,
        )
    }

    suspend fun loadEventPayloads(eventId: String): List<ConversationAuditLoadedPayload> =
        dao.getEventPayloads(eventId).map { ref ->
            val payload = requireNotNull(dao.getPayload(ref.payloadSha256))
            ConversationAuditLoadedPayload(
                label = ref.label,
                ordinal = ref.ordinal,
                role = ref.role,
                mediaType = payload.mediaType,
                encoding = payload.encoding,
                bytes = payloadStore.read(payload),
            )
        }

    suspend fun getEventPage(
        chatId: String,
        beforeSequenceExclusive: Long?,
        limit: Int,
    ): List<ConversationAuditEventEntity> {
        require(limit in 1..MAX_EVENT_PAGE_SIZE) {
            "Conversation audit event page size must be within 1..$MAX_EVENT_PAGE_SIZE"
        }
        return dao.getEventPage(chatId, beforeSequenceExclusive, limit)
    }

    fun observeAudit(chatId: String): Flow<ConversationAuditEntity?> = dao.observeAudit(chatId)

    fun observeLastEvent(chatId: String): Flow<ConversationAuditEventEntity?> =
        dao.observeLastEvent(chatId)

    suspend fun getEventsAfterSequence(
        chatId: String,
        afterSequenceExclusive: Long,
    ): List<ConversationAuditEventEntity> {
        require(afterSequenceExclusive >= 0L) {
            "Conversation audit sequence cursor must not be negative"
        }
        return dao.getEventsAfterSequence(chatId, afterSequenceExclusive)
    }

    suspend fun getAudit(chatId: String): ConversationAuditEntity? = dao.getAudit(chatId)

    suspend fun getStoredPayloadBytesForChat(chatId: String): Long =
        dao.getStoredPayloadBytesForChat(chatId)

    suspend fun getAllEvents(chatId: String): List<ConversationAuditEventEntity> =
        dao.getAllEvents(chatId)

    suspend fun getAllRevisions(
        chatId: String,
    ): List<ConversationMessageRevisionEntity> = dao.getAllRevisionsForChat(chatId)

    suspend fun getAllProjections(
        chatId: String,
    ): List<ConversationMessageProjectionEntity> = dao.getAllProjectionsForChat(chatId)

    suspend fun getAllSeals(chatId: String): List<ConversationAuditSealEntity> =
        dao.getAllSeals(chatId)

    suspend fun createExportSnapshot(
        chatId: String,
        sealReason: String,
    ): ConversationAuditExportSnapshot =
        chatMutex(chatId).withLock {
            database.withTransaction {
                sealInsideTransaction(chatId, sealReason)
            }
            val verification = verifyLocked(chatId)
            require(verification is ConversationAuditVerificationResult.Valid) {
                val invalid = verification as ConversationAuditVerificationResult.Invalid
                "Conversation audit verification failed before export: ${invalid.reason}"
            }
            val chat = requireNotNull(database.chatDao().getChatById(chatId)) {
                "Chat does not exist: $chatId"
            }
            val audit = requireNotNull(dao.getAudit(chatId)) {
                "Conversation audit does not exist: $chatId"
            }
            val events =
                if (audit.lastSequenceNumber == 0L) {
                    emptyList()
                } else {
                    dao.getEventsThroughSequence(chatId, audit.lastSequenceNumber)
                }
            val eventIds = events.mapTo(mutableSetOf()) { event -> event.eventId }
            val eventPayloads =
                events.associate { event ->
                    event.eventId to dao.getEventPayloads(event.eventId)
                }
            val revisions =
                dao.getAllRevisionsForChat(chatId)
                    .filter { revision -> revision.auditEventId in eventIds }
            val revisionIds = revisions.mapTo(mutableSetOf()) { revision -> revision.revisionId }
            val projections =
                dao.getAllProjectionsForChat(chatId)
                    .filter { projection -> projection.currentRevisionId in revisionIds }
            val seals =
                dao.getAllSeals(chatId)
                    .filter { seal -> seal.sequenceNumber <= audit.lastSequenceNumber }
            require(audit.latestSealSequenceNumber == audit.lastSequenceNumber) {
                "Conversation audit export snapshot is not sealed at its chain head"
            }
            val payloadHashes =
                buildSet {
                    eventPayloads.values.flatten().forEach { ref -> add(ref.payloadSha256) }
                    revisions.forEach { revision -> add(revision.contentPayloadSha256) }
                }
            val payloads =
                payloadHashes.associateWith { payloadSha256 ->
                    val entity = requireNotNull(dao.getPayload(payloadSha256)) {
                        "Conversation audit payload metadata is missing: $payloadSha256"
                    }
                    ConversationAuditSnapshotPayload(
                        entity = entity,
                        bytes = payloadStore.read(entity),
                    )
                }
            ConversationAuditExportSnapshot(
                chat = chat,
                messages = database.chatContentDao().getMessagesForChat(chatId),
                variants = database.chatContentDao().getVariantsForChat(chatId),
                audit = audit,
                cutoffEventId = events.lastOrNull()?.eventId,
                events = events,
                eventPayloads = eventPayloads,
                revisions = revisions,
                projections = projections,
                seals = seals,
                payloads = payloads,
                exportedAt = System.currentTimeMillis(),
            )
        }

    /**
     * 导入普通聊天归档 v3 或 `.kiyori-audit` 中的可移植审计。
     *
     * 原事件、payload 引用和原签名保持不变；导入完成后追加本机
     * `IMPORTED_CONTINUATION` 事件并使用本机签名密钥封印。外部公钥没有本机信任锚，因此即使
     * 签名数学验证通过，整条链仍明确标记为来源未验证。
     */
    suspend fun importPortableAudit(
        chatId: String,
        archive: OperitArchivedConversationAudit,
    ): ConversationAuditImportResult {
        val validated = validatePortableAudit(chatId, archive)
        return payloadLifecycleMutex.withLock {
            val importedPayloads = mutableListOf<ConversationAuditPayloadEntity>()
            val continuationRequest =
                ConversationAuditEventRequest(
                chatId = chatId,
                category = "IMPORT_EXPORT",
                eventType = "IMPORTED_CONTINUATION",
                actor = "KIYORI",
                summary = "已验证并导入外部审计链，后续事件由本机链段继续记录",
                completeness = ConversationAuditCompletenessStatus.SOURCE_UNVERIFIED,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "import_provenance",
                            role = "metadata",
                            value =
                                """
                                {
                                  "sourceCompleteness": "${archive.audit.completenessStatus}",
                                  "sourceEventCount": ${archive.events.size},
                                  "sourceSealCount": ${archive.seals.size},
                                  "sourceChainHeadSha256": "${archive.audit.chainHeadSha256}",
                                  "signatureValidation": "mathematically_valid_external_key"
                                }
                                """.trimIndent(),
                            mediaType = "application/json",
                        )
                    ),
                )
            var continuationPayloads = emptyList<StoredPayload>()
            try {
                archive.payloads.forEach { payload ->
                val bytes = validated.payloadBytes.getValue(payload.payloadSha256)
                val stored =
                    payloadStore.write(
                        bytes = bytes,
                        mediaType = payload.mediaType,
                        encoding = payload.encoding,
                        createdAt = payload.createdAt,
                    )
                check(stored.payloadSha256 == payload.payloadSha256) {
                    "Imported conversation audit payload hash changed while storing"
                }
                payloadStore.read(stored)
                importedPayloads += stored
            }
                continuationPayloads = storePayloads(continuationRequest)

                val (continuation, localSeal) =
                    chatMutex(chatId).withLock {
                    database.withTransaction {
                    require(database.chatDao().getChatById(chatId) != null) {
                        "Chat does not exist: $chatId"
                    }
                    require(dao.getAudit(chatId) == null) {
                        "Chat $chatId already has a conversation audit"
                    }
                    val importedRevisionsById =
                        archive.revisions.associateBy { revision -> revision.revisionId }
                    val importedPayloadsBySha256 =
                        archive.payloads.associateBy { payload -> payload.payloadSha256 }
                    archive.projections.forEach { projection ->
                        val revision =
                            requireNotNull(importedRevisionsById[projection.currentRevisionId]) {
                                "Imported conversation projection references a missing revision"
                            }
                        val payload =
                            requireNotNull(
                                importedPayloadsBySha256[revision.contentPayloadSha256]
                            ) {
                                "Imported conversation projection references a missing payload"
                            }
                        require(payload.encoding.equals("utf-8", ignoreCase = true)) {
                            "Imported conversation message revision is not UTF-8 text"
                        }
                        val projectedContent =
                            validated.payloadBytes
                                .getValue(revision.contentPayloadSha256)
                                .toString(Charsets.UTF_8)
                        val persistedContent =
                            if (projection.variantIndex == 0) {
                                requireNotNull(
                                    database
                                        .chatContentDao()
                                        .getMessageByTimestamp(
                                            chatId,
                                            projection.messageTimestamp,
                                        )
                                ) {
                                    "Imported conversation projection has no chat message"
                                }.content
                            } else {
                                requireNotNull(
                                    database
                                        .chatContentDao()
                                        .getVariantForMessage(
                                            chatId,
                                            projection.messageTimestamp,
                                            projection.variantIndex,
                                        )
                                ) {
                                    "Imported conversation projection has no chat variant"
                                }.content
                            }
                        require(projectedContent == persistedContent) {
                            "Imported chat projection disagrees with its signed audit revision"
                        }
                    }
                    importedPayloads.forEach { payload ->
                        dao.insertPayloadIfAbsent(payload)
                    }
                    val root = archive.audit
                    check(
                        dao.insertAuditIfAbsent(
                            ConversationAuditEntity(
                                chatId = chatId,
                                schemaVersion = root.schemaVersion,
                                completenessStatus =
                                    ConversationAuditCompletenessStatus.SOURCE_UNVERIFIED.name,
                                eventCount = root.eventCount,
                                lastSequenceNumber = root.lastSequenceNumber,
                                chainHeadSha256 = root.chainHeadSha256,
                                latestSealSequenceNumber = root.latestSealSequenceNumber,
                                createdAt = root.createdAt,
                                updatedAt = root.updatedAt,
                                lastFailureCode = root.lastFailureCode,
                                legacyReconstructionLevel =
                                    "IMPORTED_SOURCE_UNVERIFIED:${root.completenessStatus}",
                            )
                        ) != -1L
                    ) {
                        "Imported conversation audit root already exists"
                    }
                    archive.events.forEach { source ->
                        dao.insertEvent(
                            ConversationAuditEventEntity(
                                eventId = source.eventId,
                                chatId = source.chatId,
                                sequenceNumber = source.sequenceNumber,
                                occurredAt = source.occurredAt,
                                recordedAt = source.recordedAt,
                                category = source.category,
                                eventType = source.eventType,
                                actor = source.actor,
                                summary = source.summary,
                                messageTimestamp = source.messageTimestamp,
                                variantIndex = source.variantIndex,
                                localExecutionId = source.localExecutionId,
                                providerCallId = source.providerCallId,
                                parentEventId = source.parentEventId,
                                sourceChatId = source.sourceChatId,
                                sourceEventId = source.sourceEventId,
                                previousEventSha256 = source.previousEventSha256,
                                eventSha256 = source.eventSha256,
                                visibility = source.visibility,
                                terminalState = source.terminalState,
                            )
                        )
                    }
                    if (archive.eventPayloads.isNotEmpty()) {
                        dao.insertEventPayloads(
                            archive.eventPayloads.map { source ->
                                ConversationAuditEventPayloadEntity(
                                    eventId = source.eventId,
                                    payloadSha256 = source.payloadSha256,
                                    label = source.label,
                                    ordinal = source.ordinal,
                                    role = source.role,
                                )
                            }
                        )
                    }
                    archive.revisions.forEach { source ->
                        dao.insertRevision(
                            ConversationMessageRevisionEntity(
                                revisionId = source.revisionId,
                                chatId = source.chatId,
                                messageTimestamp = source.messageTimestamp,
                                variantIndex = source.variantIndex,
                                revisionNumber = source.revisionNumber,
                                sender = source.sender,
                                contentPayloadSha256 = source.contentPayloadSha256,
                                previousRevisionId = source.previousRevisionId,
                                auditEventId = source.auditEventId,
                                source = source.source,
                                createdAt = source.createdAt,
                            )
                        )
                    }
                    archive.projections.forEach { source ->
                        dao.upsertProjection(
                            ConversationMessageProjectionEntity(
                                chatId = source.chatId,
                                messageTimestamp = source.messageTimestamp,
                                variantIndex = source.variantIndex,
                                currentRevisionId = source.currentRevisionId,
                                estimatedTokenCount = source.estimatedTokenCount,
                                updatedAt = source.updatedAt,
                            )
                        )
                    }
                    archive.seals.forEach { source ->
                        dao.insertSeal(
                            ConversationAuditSealEntity(
                                sealId = source.sealId,
                                chatId = source.chatId,
                                sequenceNumber = source.sequenceNumber,
                                rootSha256 = source.rootSha256,
                                signatureAlgorithm = source.signatureAlgorithm,
                                signatureBase64 = source.signatureBase64,
                                publicKeyBase64 = source.publicKeyBase64,
                                reason = source.reason,
                                createdAt = source.createdAt,
                            )
                        )
                    }
                    val continuation =
                        appendEventInsideTransaction(
                            request = continuationRequest,
                            storedPayloads = continuationPayloads,
                        )
                    val localSeal =
                        sealInsideTransaction(
                            chatId = chatId,
                            reason = "IMPORTED_CONTINUATION",
                        )
                    continuation to localSeal
                }
            }
            ConversationAuditImportResult(
                    importedEventCount = archive.events.size,
                    importedPayloadCount = archive.payloads.size,
                    importedSealCount = archive.seals.size,
                    continuationEvent = continuation,
                    localSeal = localSeal,
                )
            } catch (error: Exception) {
                (importedPayloads + continuationPayloads.map { stored -> stored.entity })
                    .distinctBy { payload -> payload.payloadSha256 }
                    .forEach { payload ->
                    if (dao.getPayload(payload.payloadSha256) == null) {
                        payloadStore.delete(payload)
                    }
                }
                throw error
            }
        }
    }

    private fun validatePortableAudit(
        chatId: String,
        archive: OperitArchivedConversationAudit,
    ): ValidatedPortableAudit {
        require(archive.schemaVersion == SCHEMA_VERSION) {
            "Unsupported conversation audit archive schema: ${archive.schemaVersion}"
        }
        val root = archive.audit
        require(root.chatId == chatId) { "Conversation audit chat ID does not match its chat" }
        require(root.schemaVersion == archive.schemaVersion) {
            "Conversation audit schema versions disagree"
        }
        ConversationAuditCompletenessStatus.valueOf(root.completenessStatus)

        val payloadMetadataBySha256 =
            archive.payloads.associateBy { payload -> payload.payloadSha256 }
        require(payloadMetadataBySha256.size == archive.payloads.size) {
            "Conversation audit archive contains duplicate payloads"
        }
        val payloadBytes =
            archive.payloads.associate { payload ->
                require(payload.payloadSha256.matches(Regex("[0-9a-f]{64}"))) {
                    "Conversation audit payload SHA-256 is invalid"
                }
                val bytes = Base64.getDecoder().decode(payload.bytesBase64)
                require(bytes.size.toLong() == payload.plainByteCount) {
                    "Conversation audit payload byte count mismatch: ${payload.payloadSha256}"
                }
                require(ConversationAuditHasher.sha256(bytes) == payload.payloadSha256) {
                    "Conversation audit payload hash mismatch: ${payload.payloadSha256}"
                }
                if (payload.encoding.equals("utf-8", ignoreCase = true)) {
                    val text = bytes.toString(Charsets.UTF_8)
                    require(
                        ConversationAuditRedactor.redactText(
                            value = text,
                            mediaType = payload.mediaType,
                        ).value == text
                    ) {
                        "Conversation audit import contains a credential-bearing text payload"
                    }
                }
                payload.payloadSha256 to bytes
            }
        val events = archive.events.sortedBy { event -> event.sequenceNumber }
        require(events.size.toLong() == root.eventCount) {
            "Conversation audit event count does not match its root"
        }
        require(events.map { event -> event.eventId }.distinct().size == events.size) {
            "Conversation audit archive contains duplicate event IDs"
        }
        val refsByEvent = archive.eventPayloads.groupBy { ref -> ref.eventId }
        val eventsById = events.associateBy { event -> event.eventId }
        val eventIds = eventsById.keys
        require(refsByEvent.keys.all { eventId -> eventId in eventIds }) {
            "Conversation audit archive contains an event payload reference for a missing event"
        }
        require(
            archive.eventPayloads
                .map { ref -> Triple(ref.eventId, ref.label, ref.ordinal) }
                .distinct()
                .size == archive.eventPayloads.size
        ) {
            "Conversation audit archive contains duplicate event payload references"
        }

        var previousHash = ConversationAuditHasher.EMPTY_CHAIN_SHA256
        val seenEventIds = mutableSetOf<String>()
        events.forEachIndexed { index, event ->
            require(event.chatId == chatId) { "Conversation audit event belongs to another chat" }
            require(event.sequenceNumber == index + 1L) {
                "Conversation audit sequence is not contiguous"
            }
            require(event.previousEventSha256 == previousHash) {
                "Conversation audit previous-event hash mismatch at ${event.eventId}"
            }
            event.parentEventId?.let { parentEventId ->
                require(parentEventId in seenEventIds) {
                    "Conversation audit event parent does not precede the child"
                }
            }
            val refs = refsByEvent[event.eventId].orEmpty()
            refs.sortedBy { ref -> ref.ordinal }.forEachIndexed { ordinal, ref ->
                require(ref.ordinal == ordinal) {
                    "Conversation audit event payload ordinals are not contiguous"
                }
            }
            refs.forEach { ref ->
                require(ref.payloadSha256 in payloadBytes) {
                    "Conversation audit event references a missing payload"
                }
            }
            val expected =
                ConversationAuditHasher.eventHash(
                    ConversationAuditHashInput(
                        previousEventSha256 = event.previousEventSha256,
                        eventId = event.eventId,
                        chatId = event.chatId,
                        sequenceNumber = event.sequenceNumber,
                        occurredAt = event.occurredAt,
                        recordedAt = event.recordedAt,
                        category = event.category,
                        eventType = event.eventType,
                        actor = event.actor,
                        summary = event.summary,
                        messageTimestamp = event.messageTimestamp,
                        variantIndex = event.variantIndex,
                        localExecutionId = event.localExecutionId,
                        providerCallId = event.providerCallId,
                        parentEventId = event.parentEventId,
                        sourceChatId = event.sourceChatId,
                        sourceEventId = event.sourceEventId,
                        visibility = event.visibility,
                        terminalState = event.terminalState,
                        payloadRefs =
                            refs.map { ref ->
                                val payload =
                                    payloadMetadataBySha256.getValue(ref.payloadSha256)
                                ConversationAuditHashPayloadRef(
                                    label = ref.label,
                                    ordinal = ref.ordinal,
                                    role = ref.role,
                                    payloadSha256 = ref.payloadSha256,
                                    mediaType = payload.mediaType,
                                    encoding = payload.encoding,
                                    plainByteCount = payload.plainByteCount,
                                )
                            },
                    )
                )
            require(expected == event.eventSha256) {
                "Conversation audit event hash mismatch at ${event.eventId}"
            }
            previousHash = event.eventSha256
            seenEventIds += event.eventId
        }
        require(root.lastSequenceNumber == events.size.toLong()) {
            "Conversation audit last sequence does not match its events"
        }
        require(
            root.chainHeadSha256 ==
                if (events.isEmpty()) "" else previousHash
        ) {
            "Conversation audit chain head does not match its events"
        }

        val referencedPayloads =
            buildSet {
                archive.eventPayloads.forEach { ref -> add(ref.payloadSha256) }
                archive.revisions.forEach { revision -> add(revision.contentPayloadSha256) }
            }
        require(referencedPayloads == payloadBytes.keys) {
            "Conversation audit payload set contains missing or unreferenced entries"
        }

        val revisionsById = archive.revisions.associateBy { revision -> revision.revisionId }
        require(revisionsById.size == archive.revisions.size) {
            "Conversation audit archive contains duplicate revision IDs"
        }
        val latestRevisionByKey =
            mutableMapOf<Triple<String, Long, Int>, OperitArchivedConversationMessageRevision>()
        archive.revisions
            .groupBy { revision ->
                Triple(revision.chatId, revision.messageTimestamp, revision.variantIndex)
            }
            .forEach { (key, revisions) ->
                require(key.first == chatId) {
                    "Conversation audit revision belongs to another chat"
                }
                val ordered = revisions.sortedBy { revision -> revision.revisionNumber }
                ordered.forEachIndexed { index, revision ->
                    require(revision.revisionNumber == index) {
                        "Conversation audit revision sequence is not contiguous"
                    }
                    require(revision.auditEventId in seenEventIds) {
                        "Conversation audit revision references a missing event"
                    }
                    val revisionEvent = eventsById.getValue(revision.auditEventId)
                    require(
                        revisionEvent.eventType == "MESSAGE_REVISED" &&
                            revisionEvent.messageTimestamp == revision.messageTimestamp &&
                            revisionEvent.variantIndex == revision.variantIndex
                    ) {
                        "Conversation audit revision does not match its revision event"
                    }
                    require(revision.contentPayloadSha256 in payloadBytes) {
                        "Conversation audit revision references a missing payload"
                    }
                    require(
                        refsByEvent.getValue(revision.auditEventId).any { ref ->
                            ref.payloadSha256 == revision.contentPayloadSha256 &&
                                ref.role == revision.sender
                        }
                    ) {
                        "Conversation audit revision content is not bound to its revision event"
                    }
                    val expectedPrevious = ordered.getOrNull(index - 1)?.revisionId
                    require(revision.previousRevisionId == expectedPrevious) {
                        "Conversation audit revision previous pointer is invalid"
                    }
                }
                latestRevisionByKey[key] = ordered.last()
            }
        val projectionKeys =
            archive.projections.map { projection ->
                Triple(projection.chatId, projection.messageTimestamp, projection.variantIndex)
            }
        require(projectionKeys.distinct().size == projectionKeys.size) {
            "Conversation audit archive contains duplicate projection keys"
        }
        archive.projections.forEach { projection ->
            require(projection.chatId == chatId) {
                "Conversation audit projection belongs to another chat"
            }
            val currentRevision = revisionsById[projection.currentRevisionId]
            require(currentRevision != null) {
                "Conversation audit projection references a missing revision"
            }
            require(
                currentRevision.chatId == projection.chatId &&
                    currentRevision.messageTimestamp == projection.messageTimestamp &&
                    currentRevision.variantIndex == projection.variantIndex
            ) {
                "Conversation audit projection references another message revision chain"
            }
            require(
                latestRevisionByKey.getValue(
                    Triple(
                        projection.chatId,
                        projection.messageTimestamp,
                        projection.variantIndex,
                    )
                ).revisionId == projection.currentRevisionId
            ) {
                "Conversation audit projection does not reference the latest revision"
            }
        }

        val eventHashesBySequence = events.associate { event -> event.sequenceNumber to event.eventSha256 }
        require(archive.seals.map { seal -> seal.sealId }.distinct().size == archive.seals.size) {
            "Conversation audit archive contains duplicate seal IDs"
        }
        require(
            archive.seals.map { seal -> seal.sequenceNumber }.distinct().size ==
                archive.seals.size
        ) {
            "Conversation audit archive contains duplicate sealed sequences"
        }
        archive.seals.forEach { seal ->
            require(seal.chatId == chatId) { "Conversation audit seal belongs to another chat" }
            require(eventHashesBySequence[seal.sequenceNumber] == seal.rootSha256) {
                "Conversation audit seal root does not match its sequence"
            }
            require(
                crypto.verify(
                    value = seal.rootSha256.toByteArray(Charsets.US_ASCII),
                    signatureBase64 = seal.signatureBase64,
                    publicKeyBase64 = seal.publicKeyBase64,
                    algorithm = seal.signatureAlgorithm,
                )
            ) {
                "Conversation audit seal signature is invalid"
            }
        }
        val latestSealSequence = archive.seals.maxOfOrNull { seal -> seal.sequenceNumber } ?: 0L
        require(root.latestSealSequenceNumber == latestSealSequence) {
            "Conversation audit latest seal cursor does not match its seals"
        }
        require(archive.seals.isNotEmpty()) {
            "Conversation audit archive has no cryptographic seal"
        }
        return ValidatedPortableAudit(payloadBytes)
    }

    suspend fun inheritAuditForBranch(
        sourceChatId: String,
        targetChatId: String,
        upToMessageTimestampInclusive: Long?,
        createdAt: Long,
    ): ConversationAuditEventEntity {
        val sourceAudit = requireNotNull(dao.getAudit(sourceChatId)) {
            "Source conversation audit does not exist: $sourceChatId"
        }
        val sourceCompleteness =
            ConversationAuditCompletenessStatus.valueOf(sourceAudit.completenessStatus)
        val sourceEvents =
            if (upToMessageTimestampInclusive == null) {
                dao.getAllEvents(sourceChatId)
            } else {
                val cutoff =
                    dao.getLastSequenceAtMessageTimestamp(
                        sourceChatId,
                        upToMessageTimestampInclusive,
                    ) ?: 0L
                if (cutoff == 0L) {
                    emptyList()
                } else {
                    dao.getEventsThroughSequence(sourceChatId, cutoff)
                }
            }
        val inheritedEventIds = mutableMapOf<String, String>()
        sourceEvents.forEach { sourceEvent ->
            val payloads =
                loadEventPayloads(sourceEvent.eventId).map { payload ->
                    ConversationAuditPayloadInput(
                        label = payload.label,
                        role = payload.role,
                        bytes = payload.bytes,
                        mediaType = payload.mediaType,
                        encoding = payload.encoding,
                        isText = payload.encoding.equals("utf-8", ignoreCase = true),
                    )
                }
            val inherited =
                appendEvent(
                    ConversationAuditEventRequest(
                        chatId = targetChatId,
                        category = sourceEvent.category,
                        eventType = sourceEvent.eventType,
                        actor = sourceEvent.actor,
                        summary = sourceEvent.summary,
                        messageTimestamp = sourceEvent.messageTimestamp,
                        variantIndex = sourceEvent.variantIndex,
                        localExecutionId = sourceEvent.localExecutionId,
                        providerCallId = sourceEvent.providerCallId,
                        parentEventId =
                            sourceEvent.parentEventId?.let(inheritedEventIds::get),
                        sourceChatId = sourceEvent.sourceChatId ?: sourceChatId,
                        sourceEventId = sourceEvent.sourceEventId ?: sourceEvent.eventId,
                        visibility =
                            ConversationAuditVisibility.valueOf(sourceEvent.visibility),
                        terminalState = sourceEvent.terminalState,
                        completeness = sourceCompleteness,
                        occurredAt = sourceEvent.occurredAt,
                        payloads = payloads,
                    )
                )
            inheritedEventIds[sourceEvent.eventId] = inherited.eventId
        }
        return appendEvent(
            ConversationAuditEventRequest(
                chatId = targetChatId,
                category = "BRANCH",
                eventType = "BRANCH_CREATED",
                actor = "USER",
                summary = "已从对话 $sourceChatId 创建自包含分支",
                sourceChatId = sourceChatId,
                completeness = sourceCompleteness,
                occurredAt = createdAt,
                payloads =
                    listOf(
                        ConversationAuditPayloadInput.text(
                            label = "branch_cutoff",
                            role = "metadata",
                            value =
                                """
                                {
                                  "sourceChatId": "$sourceChatId",
                                  "upToMessageTimestampInclusive": ${
                                    upToMessageTimestampInclusive ?: "null"
                                },
                                  "inheritedEventCount": ${sourceEvents.size}
                                }
                                """.trimIndent(),
                            mediaType = "application/json",
                        )
                    ),
            )
        )
    }

    suspend fun reconstructLegacyAuditIfNeeded(chatId: String): ConversationAuditEntity =
        payloadLifecycleMutex.withLock {
            chatMutex(chatId).withLock chatLock@{
            val chat = requireNotNull(database.chatDao().getChatById(chatId)) {
                "Chat does not exist: $chatId"
            }
            val existingAudit = dao.getAudit(chatId)
            if (existingAudit != null && existingAudit.eventCount > 0L) {
                return@chatLock existingAudit
            }
            val completeness =
                existingAudit
                    ?.let { audit ->
                        ConversationAuditCompletenessStatus.valueOf(
                            audit.completenessStatus
                        )
                    }
                    ?: ConversationAuditCompletenessStatus.BASIC
            val messages = database.chatContentDao().getMessagesForChat(chatId)
            val variants = database.chatContentDao().getVariantsForChat(chatId)
            val reconstructionRequests =
                buildList {
                    add(
                        ConversationAuditEventRequest(
                    chatId = chatId,
                    category = "SESSION",
                    eventType = "CHAT_CREATED",
                    actor = "KIYORI",
                    summary = "历史对话已建立基础审计记录",
                    completeness = completeness,
                    occurredAt = chat.createdAt,
                        )
                    )
                    messages.forEach { message ->
                        add(
                            ConversationAuditEventRequest(
                                chatId = chatId,
                                category =
                                    if (message.sender == "user") "USER" else "ASSISTANT",
                                eventType = "HISTORICAL_MESSAGE_RECONSTRUCTED",
                                actor =
                                    if (message.sender == "user") "USER" else "KIYORI",
                                summary = "由历史聊天消息重建；历史版本未记录完整处理链",
                                messageTimestamp = message.timestamp,
                                variantIndex = message.selectedVariantIndex,
                                completeness = completeness,
                                occurredAt = message.timestamp,
                                payloads =
                                    listOf(
                                        ConversationAuditPayloadInput.text(
                                            label = "message",
                                            role = message.sender,
                                            value = message.content,
                                        )
                                    ),
                            )
                        )
                    }
                    variants.forEach { variant ->
                        add(
                            ConversationAuditEventRequest(
                                chatId = chatId,
                                category = "VARIANT",
                                eventType = "HISTORICAL_VARIANT_RECONSTRUCTED",
                                actor = "KIYORI",
                                summary = "由历史 AI variant 重建",
                                messageTimestamp = variant.messageTimestamp,
                                variantIndex = variant.variantIndex,
                                completeness = completeness,
                                occurredAt = variant.messageTimestamp,
                                payloads =
                                    listOf(
                                        ConversationAuditPayloadInput.text(
                                            label = "variant",
                                            role = "ai",
                                            value = variant.content,
                                        )
                                    ),
                            )
                        )
                    }
                    add(
                        ConversationAuditEventRequest(
                    chatId = chatId,
                    category = "INTEGRITY",
                    eventType = "HISTORICAL_FIELDS_MISSING",
                    actor = "KIYORI",
                    summary = "历史版本未记录的字段已明确标记，不进行推断或伪造",
                    completeness = completeness,
                    occurredAt = maxOf(chat.createdAt, System.currentTimeMillis()),
                    payloads =
                        listOf(
                            ConversationAuditPayloadInput.text(
                                label = "missing_fields",
                                role = "metadata",
                                value =
                                    """
                                    [
                                      "完整系统提示词",
                                      "Prompt Hook 逐步转换",
                                      "ToolPkg 消息处理逐步转换",
                                      "完整工具 schema",
                                      "部分工具结果与异常",
                                      "Provider 未持久化语义事件"
                                    ]
                                    """.trimIndent(),
                                mediaType = "application/json",
                            )
                        ),
                        )
                    )
                }
            val storedRequests = mutableListOf<Pair<ConversationAuditEventRequest, List<StoredPayload>>>()
            try {
                reconstructionRequests.forEach { request ->
                    storedRequests += request to storePayloads(request)
                }
                database.withTransaction {
                    val audit =
                        dao.getAudit(chatId)
                            ?: ensureAuditInsideTransaction(
                                chatId = chatId,
                                createdAt = chat.createdAt,
                                completeness = ConversationAuditCompletenessStatus.BASIC,
                                legacyReconstructionLevel =
                                    ConversationAuditCompletenessStatus.BASIC.name,
                            )
                    check(audit.eventCount == 0L) {
                        "Conversation audit changed while legacy reconstruction was prepared"
                    }
                    storedRequests.forEach { (request, storedPayloads) ->
                        appendEventInsideTransaction(request, storedPayloads)
                    }
                    sealInsideTransaction(chatId, reason = "LEGACY_RECONSTRUCTED")
                }
            } catch (error: Exception) {
                storedRequests
                    .flatMap { (_, storedPayloads) ->
                        storedPayloads.map { stored -> stored.entity }
                    }
                    .distinctBy { payload -> payload.payloadSha256 }
                    .forEach { payload ->
                        if (dao.getPayload(payload.payloadSha256) == null) {
                            payloadStore.delete(payload)
                        }
                    }
                throw error
            }
            return@withLock requireNotNull(dao.getAudit(chatId))
            }
        }

    suspend fun getTotalStoredPayloadBytes(): Long = dao.getTotalStoredPayloadBytes()

    suspend fun getStorageSummary(): ConversationAuditStorageSummary {
        val largest = dao.getLargestAuditByStoredBytes()
        return ConversationAuditStorageSummary(
            auditCount = dao.getAuditCount(),
            abnormalAuditCount = dao.getAbnormalAuditCount(),
            totalStoredPayloadBytes = dao.getTotalStoredPayloadBytes(),
            largestChatId = largest?.chatId,
            largestChatStoredPayloadBytes = largest?.storedByteCount ?: 0L,
        )
    }

    suspend fun cleanupUnreferencedPayloads(): Int =
        payloadLifecycleMutex.withLock {
            val unreferenced = dao.getUnreferencedPayloads()
            unreferenced.forEach { payload ->
                payloadStore.delete(payload)
                check(dao.deletePayloadMetadata(payload.payloadSha256) == 1) {
                    "Unreferenced conversation audit payload metadata disappeared"
                }
            }
            unreferenced.size
        }

    private suspend fun ensureAuditInsideTransaction(
        chatId: String,
        createdAt: Long,
        completeness: ConversationAuditCompletenessStatus =
            ConversationAuditCompletenessStatus.COMPLETE,
        legacyReconstructionLevel: String? = null,
    ): ConversationAuditEntity {
        dao.insertAuditIfAbsent(
            ConversationAuditEntity(
                chatId = chatId,
                schemaVersion = SCHEMA_VERSION,
                completenessStatus = completeness.name,
                eventCount = 0L,
                lastSequenceNumber = 0L,
                chainHeadSha256 = "",
                latestSealSequenceNumber = 0L,
                createdAt = createdAt,
                updatedAt = maxOf(createdAt, System.currentTimeMillis()),
                legacyReconstructionLevel = legacyReconstructionLevel,
            )
        )
        return requireNotNull(dao.getAudit(chatId))
    }

    private suspend fun storePayloads(
        request: ConversationAuditEventRequest,
    ): List<StoredPayload> =
        request.payloads.map { payload ->
            val bytes =
                if (payload.isText) {
                    ConversationAuditRedactor
                        .redactText(
                            value = payload.bytes.toString(Charsets.UTF_8),
                            mediaType = payload.mediaType,
                        )
                        .value
                        .toByteArray(Charsets.UTF_8)
                } else {
                    payload.bytes
                }
            StoredPayload(
                input = payload,
                entity =
                    payloadStore.write(
                        bytes = bytes,
                        mediaType = payload.mediaType,
                        encoding = payload.encoding,
                        createdAt = request.occurredAt,
                    ),
            )
        }

    private suspend fun appendEventLocked(
        request: ConversationAuditEventRequest,
    ): ConversationAuditEventEntity {
        val storedPayloads = storePayloads(request)
        return database.withTransaction {
            appendEventInsideTransaction(request, storedPayloads)
        }
    }

    private suspend fun appendEventInsideTransaction(
        request: ConversationAuditEventRequest,
        storedPayloads: List<StoredPayload>,
    ): ConversationAuditEventEntity {
        val audit =
            dao.getAudit(request.chatId)
                ?: ensureAuditInsideTransaction(
                    chatId = request.chatId,
                    createdAt = request.occurredAt,
                )
        val previousHash =
            audit.chainHeadSha256.ifEmpty {
                ConversationAuditHasher.EMPTY_CHAIN_SHA256
            }
        val sequenceNumber = audit.lastSequenceNumber + 1L
        val eventId = UUID.randomUUID().toString()
        val recordedAt = maxOf(request.occurredAt, System.currentTimeMillis())
        val payloadRefs =
            storedPayloads.mapIndexed { index, stored ->
                ConversationAuditHashPayloadRef(
                    label = stored.input.label,
                    ordinal = index,
                    role = stored.input.role,
                    payloadSha256 = stored.entity.payloadSha256,
                    mediaType = stored.entity.mediaType,
                    encoding = stored.entity.encoding,
                    plainByteCount = stored.entity.plainByteCount,
                )
            }
        val eventHash =
            ConversationAuditHasher.eventHash(
                ConversationAuditHashInput(
                    previousEventSha256 = previousHash,
                    eventId = eventId,
                    chatId = request.chatId,
                    sequenceNumber = sequenceNumber,
                    occurredAt = request.occurredAt,
                    recordedAt = recordedAt,
                    category = request.category,
                    eventType = request.eventType,
                    actor = request.actor,
                    summary = request.summary,
                    messageTimestamp = request.messageTimestamp,
                    variantIndex = request.variantIndex,
                    localExecutionId = request.localExecutionId,
                    providerCallId = request.providerCallId,
                    parentEventId = request.parentEventId,
                    sourceChatId = request.sourceChatId,
                    sourceEventId = request.sourceEventId,
                    visibility = request.visibility.name,
                    terminalState = request.terminalState,
                    payloadRefs = payloadRefs,
                )
            )
        val event =
            ConversationAuditEventEntity(
                eventId = eventId,
                chatId = request.chatId,
                sequenceNumber = sequenceNumber,
                occurredAt = request.occurredAt,
                recordedAt = recordedAt,
                category = request.category,
                eventType = request.eventType,
                actor = request.actor,
                summary = request.summary,
                messageTimestamp = request.messageTimestamp,
                variantIndex = request.variantIndex,
                localExecutionId = request.localExecutionId,
                providerCallId = request.providerCallId,
                parentEventId = request.parentEventId,
                sourceChatId = request.sourceChatId,
                sourceEventId = request.sourceEventId,
                previousEventSha256 = previousHash,
                eventSha256 = eventHash,
                visibility = request.visibility.name,
                terminalState = request.terminalState,
            )
        storedPayloads.forEach { stored -> dao.insertPayloadIfAbsent(stored.entity) }
        dao.insertEvent(event)
        if (payloadRefs.isNotEmpty()) {
            dao.insertEventPayloads(
                payloadRefs.map { ref ->
                    ConversationAuditEventPayloadEntity(
                        eventId = eventId,
                        payloadSha256 = ref.payloadSha256,
                        label = ref.label,
                        ordinal = ref.ordinal,
                        role = ref.role,
                    )
                }
            )
        }
        val advanced =
            dao.advanceAudit(
                chatId = request.chatId,
                expectedSequenceNumber = audit.lastSequenceNumber,
                expectedChainHeadSha256 = audit.chainHeadSha256,
                newSequenceNumber = sequenceNumber,
                newChainHeadSha256 = eventHash,
                completenessStatus =
                    ConversationAuditCompletenessPolicy.merge(
                        current = ConversationAuditCompletenessStatus.valueOf(
                            audit.completenessStatus
                        ),
                        requested = request.completeness,
                    ).name,
                updatedAt = recordedAt,
                lastFailureCode = request.failureCode,
            )
        check(advanced == 1) {
            "Conversation audit cursor changed while appending event for ${request.chatId}"
        }
        return event
    }

    private suspend fun sealInsideTransaction(
        chatId: String,
        reason: String,
    ): ConversationAuditSealEntity {
        val audit = requireNotNull(dao.getAudit(chatId))
        require(audit.lastSequenceNumber > 0L) {
            "Cannot seal an empty conversation audit"
        }
        val existingSeal = dao.getLatestSeal(chatId)
        if (existingSeal?.sequenceNumber == audit.lastSequenceNumber) {
            require(existingSeal.rootSha256 == audit.chainHeadSha256) {
                "Existing conversation audit seal does not match the current chain head"
            }
            return existingSeal
        }
        val signature =
            crypto.sign(audit.chainHeadSha256.toByteArray(Charsets.US_ASCII))
        val seal =
            ConversationAuditSealEntity(
                sealId = UUID.randomUUID().toString(),
                chatId = chatId,
                sequenceNumber = audit.lastSequenceNumber,
                rootSha256 = audit.chainHeadSha256,
                signatureAlgorithm = signature.algorithm,
                signatureBase64 = signature.signatureBase64,
                publicKeyBase64 = signature.publicKeyBase64,
                reason = reason,
                createdAt = System.currentTimeMillis(),
            )
        dao.insertSeal(seal)
        check(
            dao.markSealed(
                chatId = chatId,
                sequenceNumber = audit.lastSequenceNumber,
                updatedAt = seal.createdAt,
            ) == 1
        ) {
            "Conversation audit advanced before its seal was committed"
        }
        return seal
    }

    private fun chatMutex(chatId: String): Mutex =
        chatMutexes.computeIfAbsent(chatId) { Mutex() }

    private data class StoredPayload(
        val input: ConversationAuditPayloadInput,
        val entity: ConversationAuditPayloadEntity,
    )

    private data class ValidatedPortableAudit(
        val payloadBytes: Map<String, ByteArray>,
    )

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_EVENT_PAGE_SIZE = 500
        @Volatile
        private var INSTANCE: ConversationAuditRepository? = null

        fun from(context: Context): ConversationAuditRepository {
            val applicationContext = context.applicationContext
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: run {
                            val crypto = AndroidConversationAuditCrypto()
                            ConversationAuditRepository(
                                database = AppDatabase.getDatabase(applicationContext),
                                payloadStore =
                                    ConversationAuditPayloadStore(
                                        context = applicationContext,
                                        crypto = crypto,
                                    ),
                                crypto = crypto,
                            ).also { repository ->
                                INSTANCE = repository
                            }
                        }
                }
        }
    }
}

data class ConversationAuditEventRequest(
    val chatId: String,
    val category: String,
    val eventType: String,
    val actor: String,
    val summary: String,
    val messageTimestamp: Long? = null,
    val variantIndex: Int? = null,
    val localExecutionId: String? = null,
    val providerCallId: String? = null,
    val parentEventId: String? = null,
    val sourceChatId: String? = null,
    val sourceEventId: String? = null,
    val visibility: ConversationAuditVisibility = ConversationAuditVisibility.TIMELINE,
    val terminalState: String? = null,
    val completeness: ConversationAuditCompletenessStatus =
        ConversationAuditCompletenessStatus.COMPLETE,
    val failureCode: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val payloads: List<ConversationAuditPayloadInput> = emptyList(),
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(category.isNotBlank()) { "category must not be blank" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(actor.isNotBlank()) { "actor must not be blank" }
        require(summary.isNotBlank()) { "summary must not be blank" }
        require(occurredAt > 0L) { "occurredAt must be positive" }
    }
}

data class ConversationMessageRevisionRequest(
    val chatId: String,
    val messageTimestamp: Long,
    val variantIndex: Int,
    val newContent: String,
    val source: String = "USER_EDIT",
    val summary: String = "消息内容已修订",
    val category: String = "REVISION",
    val eventType: String = "MESSAGE_REVISED",
    val actor: String = "USER",
    val terminalState: String? = null,
    val completeness: ConversationAuditCompletenessStatus =
        ConversationAuditCompletenessStatus.COMPLETE,
    val failureCode: String? = null,
    val additionalPayloads: List<ConversationAuditPayloadInput> = emptyList(),
    val sealReason: String = "MESSAGE_REVISED",
    val occurredAt: Long = System.currentTimeMillis(),
    val expectedContent: String? = null,
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(messageTimestamp > 0L) { "messageTimestamp must be positive" }
        require(variantIndex >= 0) { "variantIndex must not be negative" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(summary.isNotBlank()) { "summary must not be blank" }
        require(category.isNotBlank()) { "category must not be blank" }
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(actor.isNotBlank()) { "actor must not be blank" }
        require(sealReason.isNotBlank()) { "sealReason must not be blank" }
        require(occurredAt > 0L) { "occurredAt must be positive" }
    }
}

data class ConversationMessageRevisionResult(
    val event: ConversationAuditEventEntity,
    val revision: ConversationMessageRevisionEntity,
    val projection: ConversationMessageProjectionEntity,
    val seal: ConversationAuditSealEntity,
)

data class ConversationAuditMutationResult<T>(
    val value: T,
    val event: ConversationAuditEventEntity,
)

data class ConversationAuditImportResult(
    val importedEventCount: Int,
    val importedPayloadCount: Int,
    val importedSealCount: Int,
    val continuationEvent: ConversationAuditEventEntity,
    val localSeal: ConversationAuditSealEntity,
)

data class ConversationAuditStorageSummary(
    val auditCount: Int,
    val abnormalAuditCount: Int,
    val totalStoredPayloadBytes: Long,
    val largestChatId: String?,
    val largestChatStoredPayloadBytes: Long,
)

data class ConversationAuditSnapshotPayload(
    val entity: ConversationAuditPayloadEntity,
    val bytes: ByteArray,
)

data class ConversationAuditExportSnapshot(
    val chat: ChatEntity,
    val messages: List<MessageEntity>,
    val variants: List<MessageVariantEntity>,
    val audit: ConversationAuditEntity,
    val cutoffEventId: String?,
    val events: List<ConversationAuditEventEntity>,
    val eventPayloads: Map<String, List<ConversationAuditEventPayloadEntity>>,
    val revisions: List<ConversationMessageRevisionEntity>,
    val projections: List<ConversationMessageProjectionEntity>,
    val seals: List<ConversationAuditSealEntity>,
    val payloads: Map<String, ConversationAuditSnapshotPayload>,
    val exportedAt: Long,
)

data class ConversationAuditPayloadInput(
    val label: String,
    val role: String,
    val bytes: ByteArray,
    val mediaType: String,
    val encoding: String,
    val isText: Boolean,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(role.isNotBlank()) { "role must not be blank" }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
        require(encoding.isNotBlank()) { "encoding must not be blank" }
    }

    companion object {
        fun text(
            label: String,
            role: String,
            value: String,
            mediaType: String = "text/plain",
        ): ConversationAuditPayloadInput =
            ConversationAuditPayloadInput(
                label = label,
                role = role,
                bytes = value.toByteArray(Charsets.UTF_8),
                mediaType = mediaType,
                encoding = "utf-8",
                isText = true,
            )

        fun binary(
            label: String,
            role: String,
            bytes: ByteArray,
            mediaType: String,
        ): ConversationAuditPayloadInput =
            ConversationAuditPayloadInput(
                label = label,
                role = role,
                bytes = bytes,
                mediaType = mediaType,
                encoding = "binary",
                isText = false,
            )
    }
}

data class ConversationAuditLoadedPayload(
    val label: String,
    val ordinal: Int,
    val role: String,
    val mediaType: String,
    val encoding: String,
    val bytes: ByteArray,
)

sealed interface ConversationAuditVerificationResult {
    data class Valid(
        val eventCount: Int,
        val chainHeadSha256: String,
        val latestSealSequenceNumber: Long?,
    ) : ConversationAuditVerificationResult

    data class Invalid(
        val eventId: String?,
        val reason: String,
    ) : ConversationAuditVerificationResult
}
