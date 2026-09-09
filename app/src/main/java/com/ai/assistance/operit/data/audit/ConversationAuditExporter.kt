package com.ai.assistance.operit.data.audit

import android.content.Context
import com.ai.assistance.operit.data.model.ConversationAuditCompletenessStatus
import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventPayloadEntity
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

data class ConversationAuditExportResult(
    val file: File,
    val format: ConversationAuditExportFormat,
    val cutoffEventId: String?,
    val eventCount: Int,
)

class ConversationAuditExportRecordingException(val exportedFile: File, cause: Exception) :
    IllegalStateException("Export file was created but its audit completion could not be recorded", cause)

enum class ConversationAuditExportFormat {
    AI_DIAGNOSTICS_MARKDOWN,
    COMPLETE_AUDIT_PACKAGE,
}

/** 明文 AI 诊断 Markdown 与完整 `.kiyori-audit` 的唯一导出实现。 */
class ConversationAuditExporter(context: Context) {
    private val applicationContext = context.applicationContext
    private val repository = ConversationAuditRepository.from(applicationContext)
    private val archiveJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    suspend fun export(
        chatId: String,
        format: ConversationAuditExportFormat =
            ConversationAuditExportFormat.AI_DIAGNOSTICS_MARKDOWN,
    ): ConversationAuditExportResult =
        exportMutex.withLock {
            withContext(Dispatchers.IO) {
                val snapshot =
                    repository.createExportSnapshot(
                        chatId = chatId,
                        sealReason = "EXPORT_SNAPSHOT",
                    )
                val exportDir = KiyoriPaths.conversationAuditExportsDir()
                val timestamp =
                    SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
                        .format(Date(snapshot.exportedAt))
                val safeTitle =
                    snapshot.chat.title
                        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                        .trim()
                        .take(48)
                        .ifBlank { "conversation" }
                val baseName = "$timestamp-$safeTitle-${snapshot.chat.id.take(8)}"
                val target =
                    when (format) {
                        ConversationAuditExportFormat.AI_DIAGNOSTICS_MARKDOWN ->
                            File(exportDir, "$baseName-ai-diagnostics.md")
                        ConversationAuditExportFormat.COMPLETE_AUDIT_PACKAGE ->
                            File(exportDir, "$baseName-complete-audit.kiyori-audit")
                    }
                currentCoroutineContext().ensureActive()
                when (format) {
                    ConversationAuditExportFormat.AI_DIAGNOSTICS_MARKDOWN ->
                        writeMarkdown(snapshot, target)
                    ConversationAuditExportFormat.COMPLETE_AUDIT_PACKAGE ->
                        writePackage(snapshot, target)
                }
                try {
                    repository.appendEvent(
                        ConversationAuditEventRequest(
                            chatId = chatId,
                            category = "IMPORT_EXPORT",
                            eventType = "AUDIT_EXPORTED",
                            actor = "USER",
                            summary =
                                when (format) {
                                    ConversationAuditExportFormat.AI_DIAGNOSTICS_MARKDOWN ->
                                        "已导出明文 AI 诊断 Markdown"
                                    ConversationAuditExportFormat.COMPLETE_AUDIT_PACKAGE ->
                                        "已导出完整签名审计包"
                                },
                            completeness =
                                ConversationAuditCompletenessStatus.valueOf(
                                    snapshot.audit.completenessStatus
                                ),
                            payloads =
                                listOf(
                                    ConversationAuditPayloadInput.text(
                                        label = "export_manifest",
                                        role = "metadata",
                                        value =
                                            JSONObject()
                                                .put("file", target.name)
                                                .put("format", format.name)
                                                .put(
                                                    "cutoffEventId",
                                                    snapshot.cutoffEventId ?: JSONObject.NULL,
                                                )
                                                .put("eventCount", snapshot.events.size)
                                                .toString(),
                                        mediaType = "application/json",
                                    )
                                ),
                        )
                    )
                    repository.seal(chatId, reason = "AUDIT_EXPORTED")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw ConversationAuditExportRecordingException(target, error)
                }
                ConversationAuditExportResult(
                    file = target,
                    format = format,
                    cutoffEventId = snapshot.cutoffEventId,
                    eventCount = snapshot.events.size,
                )
            }
        }

    private fun writePackage(
        snapshot: ConversationAuditExportSnapshot,
        target: File,
    ) {
        writeConversationAuditExportFile(target) { temporary ->
            FileOutputStream(temporary).use { output ->
                val zip = ZipOutputStream(output)
                try {
                    zip.writeEntry("manifest.json", manifest(snapshot).toString(2))
                    zip.writeEntry(
                        "chat.json",
                        archiveJson.encodeToString(
                            snapshot.toOperitArchivedChat(includeAudit = false)
                        ),
                    )
                    zip.writeEntry(
                        "audit.json",
                        archiveJson.encodeToString(
                            snapshot.toOperitArchivedConversationAudit()
                        ),
                    )
                    zip.writeEntry(
                        "timeline.md",
                        timelineMarkdown(
                            snapshot = snapshot,
                            inlinePayloads = false,
                            externalShare = false,
                        ),
                    )
                    zip.writeEntry(
                        "events.jsonl",
                        snapshot.events.joinToString("\n") { event ->
                            eventJson(
                                event = event,
                                refs = snapshot.eventPayloads[event.eventId].orEmpty(),
                            ).toString()
                        } + if (snapshot.events.isEmpty()) "" else "\n",
                    )
                    zip.writeEntry(
                        "revisions.jsonl",
                        snapshot.revisions.joinToString("\n") { revision ->
                            JSONObject()
                                .put("revisionId", revision.revisionId)
                                .put("chatId", revision.chatId)
                                .put("messageTimestamp", revision.messageTimestamp)
                                .put("variantIndex", revision.variantIndex)
                                .put("revisionNumber", revision.revisionNumber)
                                .put("sender", revision.sender)
                                .put("contentPayloadSha256", revision.contentPayloadSha256)
                                .put(
                                    "previousRevisionId",
                                    revision.previousRevisionId ?: JSONObject.NULL,
                                )
                                .put("auditEventId", revision.auditEventId)
                                .put("source", revision.source)
                                .put("createdAt", revision.createdAt)
                                .toString()
                        } + if (snapshot.revisions.isEmpty()) "" else "\n",
                    )
                    zip.writeEntry(
                        "projections.jsonl",
                        snapshot.projections.joinToString("\n") { projection ->
                            JSONObject()
                                .put("chatId", projection.chatId)
                                .put("messageTimestamp", projection.messageTimestamp)
                                .put("variantIndex", projection.variantIndex)
                                .put("currentRevisionId", projection.currentRevisionId)
                                .put("estimatedTokenCount", projection.estimatedTokenCount)
                                .put("updatedAt", projection.updatedAt)
                                .toString()
                        } + if (snapshot.projections.isEmpty()) "" else "\n",
                    )
                    zip.writeEntry("integrity.json", integrity(snapshot).toString(2))
                    zip.writeEntry("redaction-report.json", redactionReport().toString(2))
                    snapshot.payloads
                        .toSortedMap()
                        .forEach { (payloadSha256, payload) ->
                            zip.writeEntry(
                                payloadPath(payloadSha256, payload),
                                payload.bytes,
                            )
                        }
                    zip.finish()
                    zip.flush()
                    output.fd.sync()
                } finally {
                    zip.close()
                }
            }
        }
    }

    private fun writeMarkdown(
        snapshot: ConversationAuditExportSnapshot,
        target: File,
    ) {
        writeConversationAuditExportFile(target) { temporary ->
            FileOutputStream(temporary).use { output ->
                output.write(
                    timelineMarkdown(
                        snapshot = snapshot,
                        inlinePayloads = true,
                        externalShare = true,
                    ).toByteArray(Charsets.UTF_8)
                )
                output.fd.sync()
            }
        }
    }

    private fun manifest(snapshot: ConversationAuditExportSnapshot): JSONObject {
        val payloadIndex = JSONArray()
        snapshot.payloads.toSortedMap().forEach { (payloadSha256, payload) ->
            payloadIndex.put(
                JSONObject()
                    .put("payloadSha256", payloadSha256)
                    .put("path", payloadPath(payloadSha256, payload))
                    .put("mediaType", payload.entity.mediaType)
                    .put("encoding", payload.entity.encoding)
                    .put("plainByteCount", payload.entity.plainByteCount)
            )
        }
        return JSONObject()
            .put("archiveType", "kiyori_conversation_audit")
            .put("formatVersion", 1)
            .put("chatId", snapshot.chat.id)
            .put("title", snapshot.chat.title)
            .put("exportedAt", snapshot.exportedAt)
            .put("cutoffEventId", snapshot.cutoffEventId ?: JSONObject.NULL)
            .put("cutoffSequenceNumber", snapshot.audit.lastSequenceNumber)
            .put("completenessStatus", snapshot.audit.completenessStatus)
            .put(
                "executionState",
                when (snapshot.audit.completenessStatus) {
                    ConversationAuditCompletenessStatus.IN_PROGRESS.name -> "IN_PROGRESS"
                    ConversationAuditCompletenessStatus.COMPLETE.name -> "TERMINAL"
                    else -> "TERMINAL_UNKNOWN"
                },
            )
            .put("eventCount", snapshot.events.size)
            .put("revisionCount", snapshot.revisions.size)
            .put("sealCount", snapshot.seals.size)
            .put("payloads", payloadIndex)
    }

    private fun integrity(snapshot: ConversationAuditExportSnapshot): JSONObject {
        val seals = JSONArray()
        snapshot.seals.forEach { seal ->
            seals.put(
                JSONObject()
                    .put("sealId", seal.sealId)
                    .put("sequenceNumber", seal.sequenceNumber)
                    .put("rootSha256", seal.rootSha256)
                    .put("signatureAlgorithm", seal.signatureAlgorithm)
                    .put("signatureBase64", seal.signatureBase64)
                    .put("publicKeyBase64", seal.publicKeyBase64)
                    .put("reason", seal.reason)
                    .put("createdAt", seal.createdAt)
            )
        }
        return JSONObject()
            .put("chainHeadSha256", snapshot.audit.chainHeadSha256)
            .put("lastSequenceNumber", snapshot.audit.lastSequenceNumber)
            .put("latestSealSequenceNumber", snapshot.audit.latestSealSequenceNumber)
            .put("seals", seals)
    }

    private fun redactionReport(): JSONObject =
        JSONObject()
            .put("policyVersion", 1)
            .put("credentialsPersisted", false)
            .put(
                "alwaysRedacted",
                JSONArray(
                    listOf(
                        "API Key",
                        "Authorization",
                        "Cookie",
                        "password",
                        "private key",
                        "access token",
                        "request signature",
                        "credential URL parameter",
                    )
                ),
            )
            .put(
                "note",
                "Payloads in this export are the already-redacted plaintext represented by the signed audit chain.",
            )
            .put(
                "aiReviewMarkdown",
                "The AI diagnostics Markdown is UTF-8 plaintext, additionally pseudonymizes private paths and identity fields, and is not encrypted.",
            )

    private fun timelineMarkdown(
        snapshot: ConversationAuditExportSnapshot,
        inlinePayloads: Boolean,
        externalShare: Boolean,
    ): String =
        buildString {
            appendLine("# Kiyori AI 对话审计")
            appendLine()
            appendLine("- Chat ID: `${snapshot.chat.id}`")
            appendLine(
                "- 标题: " +
                    redactForReview(
                        value = snapshot.chat.title,
                        mediaType = "text/plain",
                        externalShare = externalShare,
                    )
            )
            appendLine("- 完整性: `${snapshot.audit.completenessStatus}`")
            appendLine("- 导出时间: `${snapshot.exportedAt}`")
            appendLine("- cutoffEventId: `${snapshot.cutoffEventId ?: "none"}`")
            appendLine("- 事件数: ${snapshot.events.size}")
            if (inlinePayloads) {
                appendLine("- 导出格式: 明文 UTF-8 Markdown（未加密）")
            }
            appendLine("- 链头: `${snapshot.audit.chainHeadSha256}`")
            appendLine()
            appendLine("> 此文档只描述 Kiyori 实际可观察并持久化的事实，不包含 Provider 未返回的内部推理。")
            if (externalShare) {
                appendLine(
                    "> 此 AI 审阅版本额外假名化账户标识和私有路径；完整本机复现数据位于 `.kiyori-audit` 包。"
                )
            }
            appendLine()
            appendLine("## 当前对话")
            appendLine()
            appendLine(
                "消息数：${snapshot.messages.size}；历史 AI variant：${snapshot.variants.size}。" +
                    if (externalShare) {
                        "正文对应导出快照中的当前投影，已按外部 AI 审阅规则处理。"
                    } else {
                        "正文对应导出快照中的当前投影。"
                    }
            )
            appendLine()
            snapshot.messages.sortedBy { it.orderIndex }.forEachIndexed { index, message ->
                appendLine(
                    "### ${index + 1}. ${message.sender} · timestamp=${message.timestamp}"
                )
                appendLine()
                appendLine(
                    "- provider: " +
                        redactForReview(
                            value = message.provider.ifBlank { "not_recorded" },
                            mediaType = "text/plain",
                            externalShare = externalShare,
                        )
                )
                appendLine(
                    "- model: " +
                        redactForReview(
                            value = message.modelName.ifBlank { "not_recorded" },
                            mediaType = "text/plain",
                            externalShare = externalShare,
                        )
                )
                appendLine(
                    "- tokens: input=${message.inputTokens}, output=${message.outputTokens}, " +
                        "cachedInput=${message.cachedInputTokens}"
                )
                val messageText =
                    redactForReview(
                        value = message.content,
                        mediaType = "text/markdown",
                        externalShare = externalShare,
                    )
                val messageFence = markdownFence(messageText)
                appendLine("${messageFence}text")
                appendLine(messageText)
                appendLine(messageFence)
                appendLine()
            }
            if (snapshot.variants.isNotEmpty()) {
                appendLine("### 历史 AI variant")
                appendLine()
                snapshot.variants
                    .sortedWith(compareBy({ it.messageTimestamp }, { it.variantIndex }))
                    .forEach { variant ->
                        appendLine(
                            "- timestamp=${variant.messageTimestamp}, variant=${variant.variantIndex}, " +
                                "provider=" +
                                redactForReview(
                                    value = variant.provider.ifBlank { "not_recorded" },
                                    mediaType = "text/plain",
                                    externalShare = externalShare,
                                ) +
                                ", model=" +
                                redactForReview(
                                    value = variant.modelName.ifBlank { "not_recorded" },
                                    mediaType = "text/plain",
                                    externalShare = externalShare,
                                )
                        )
                        val variantText =
                            redactForReview(
                                value = variant.content,
                                mediaType = "text/markdown",
                                externalShare = externalShare,
                            )
                        val variantFence = markdownFence(variantText)
                        appendLine("${variantFence}text")
                        appendLine(variantText)
                        appendLine(variantFence)
                        appendLine()
                    }
            }
            if (snapshot.revisions.isNotEmpty() || snapshot.projections.isNotEmpty()) {
                appendLine("## 修订与当前投影")
                appendLine()
                appendLine("- revisions: ${snapshot.revisions.size}")
                appendLine("- projections: ${snapshot.projections.size}")
                snapshot.revisions.forEach { revision ->
                    appendLine(
                        "- revision=${revision.revisionId}, timestamp=${revision.messageTimestamp}, " +
                            "variant=${revision.variantIndex}, sender=${revision.sender}, " +
                            "source=${revision.source}, auditEvent=${revision.auditEventId}"
                    )
                }
                appendLine()
            }
            appendLine("## 审计事件时间线")
            appendLine()
            snapshot.events.forEach { event ->
                appendLine(
                    "### ${event.sequenceNumber}. ${event.category} / ${event.eventType}"
                )
                appendLine()
                appendLine("- eventId: `${event.eventId}`")
                appendLine("- occurredAt: `${event.occurredAt}`")
                appendLine("- actor: `${event.actor}`")
                event.messageTimestamp?.let { appendLine("- messageTimestamp: `$it`") }
                event.variantIndex?.let { appendLine("- variantIndex: `$it`") }
                event.localExecutionId?.let { appendLine("- localExecutionId: `$it`") }
                event.providerCallId?.let { appendLine("- providerCallId: `$it`") }
                event.terminalState?.let { appendLine("- terminalState: `$it`") }
                appendLine(
                    "- 摘要: " +
                        redactForReview(
                            value = event.summary,
                            mediaType = "text/plain",
                            externalShare = externalShare,
                        )
                )
                appendLine("- eventSha256: `${event.eventSha256}`")
                appendLine()
                snapshot.eventPayloads[event.eventId].orEmpty().forEach { ref ->
                    val payload = requireNotNull(snapshot.payloads[ref.payloadSha256])
                    appendLine(
                        "#### payload `${ref.label}` (${ref.role}, ${payload.entity.mediaType})"
                    )
                    appendLine()
                    if (inlinePayloads && payload.entity.encoding == "utf-8") {
                        val payloadText =
                            redactForReview(
                                value = payload.bytes.toString(Charsets.UTF_8),
                                mediaType = payload.entity.mediaType,
                                externalShare = externalShare,
                            )
                        val fence = markdownFence(payloadText)
                        appendLine("${fence}text")
                        appendLine(payloadText)
                        appendLine(fence)
                    } else {
                        appendLine(
                            "- 路径: `${payloadPath(ref.payloadSha256, payload)}`"
                        )
                        appendLine("- SHA-256: `${ref.payloadSha256}`")
                        appendLine("- 字节数: ${payload.entity.plainByteCount}")
                    }
                    appendLine()
                }
            }
            appendLine("## 机器可读事件 JSONL")
            appendLine()
            appendLine("每行对应一个已封印事件；payload 正文在上方按关联关系展开。")
            appendLine()
            val eventJsonLines =
                snapshot.events.joinToString("\n") { event ->
                    redactForReview(
                        value =
                            eventJson(
                                event = event,
                                refs = snapshot.eventPayloads[event.eventId].orEmpty(),
                            ).toString(),
                        mediaType = "application/json",
                        externalShare = externalShare,
                    )
                }
            val jsonlFence = markdownFence(eventJsonLines)
            appendLine("${jsonlFence}jsonl")
            appendLine(eventJsonLines)
            appendLine(jsonlFence)
        }

    private fun redactForReview(
        value: String,
        mediaType: String,
        externalShare: Boolean,
    ): String =
        if (externalShare) {
            ConversationAuditExternalShareRedactor.redact(
                value = value,
                mediaType = mediaType,
            ).value
        } else {
            value
        }

    private fun markdownFence(value: String): String {
        val longestRun =
            Regex("`+")
                .findAll(value)
                .maxOfOrNull { match -> match.value.length }
                ?: 0
        return "`".repeat(maxOf(3, longestRun + 1))
    }

    private fun eventJson(
        event: ConversationAuditEventEntity,
        refs: List<ConversationAuditEventPayloadEntity>,
    ): JSONObject {
        val payloadRefs = JSONArray()
        refs.forEach { ref ->
            payloadRefs.put(
                JSONObject()
                    .put("payloadSha256", ref.payloadSha256)
                    .put("label", ref.label)
                    .put("ordinal", ref.ordinal)
                    .put("role", ref.role)
            )
        }
        return JSONObject()
            .put("eventId", event.eventId)
            .put("chatId", event.chatId)
            .put("sequenceNumber", event.sequenceNumber)
            .put("occurredAt", event.occurredAt)
            .put("recordedAt", event.recordedAt)
            .put("category", event.category)
            .put("eventType", event.eventType)
            .put("actor", event.actor)
            .put("summary", event.summary)
            .put("messageTimestamp", event.messageTimestamp ?: JSONObject.NULL)
            .put("variantIndex", event.variantIndex ?: JSONObject.NULL)
            .put("localExecutionId", event.localExecutionId ?: JSONObject.NULL)
            .put("providerCallId", event.providerCallId ?: JSONObject.NULL)
            .put("parentEventId", event.parentEventId ?: JSONObject.NULL)
            .put("sourceChatId", event.sourceChatId ?: JSONObject.NULL)
            .put("sourceEventId", event.sourceEventId ?: JSONObject.NULL)
            .put("previousEventSha256", event.previousEventSha256)
            .put("eventSha256", event.eventSha256)
            .put("visibility", event.visibility)
            .put("terminalState", event.terminalState ?: JSONObject.NULL)
            .put("payloadRefs", payloadRefs)
    }

    private fun payloadPath(
        payloadSha256: String,
        payload: ConversationAuditSnapshotPayload,
    ): String =
        "payloads/$payloadSha256." +
            if (payload.entity.encoding.equals("utf-8", ignoreCase = true)) "txt" else "bin"

    private fun ZipOutputStream.writeEntry(
        path: String,
        content: String,
    ) {
        writeEntry(path, content.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.writeEntry(
        path: String,
        content: ByteArray,
    ) {
        require(!path.startsWith('/') && !path.contains("..")) {
            "Unsafe conversation audit ZIP entry: $path"
        }
        putNextEntry(ZipEntry(path))
        write(content)
        closeEntry()
    }

    private companion object {
        val exportMutex = Mutex()
    }
}
