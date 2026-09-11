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
                        diagnosticsOnly = format == ConversationAuditExportFormat.AI_DIAGNOSTICS_MARKDOWN,
                    )
                val exportDir = KiyoriPaths.conversationAuditExportsDir()
                val timestamp =
                    SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
                        .format(Date(snapshot.exportedAt))
                val safeTitle =
                    ConversationAuditRedactor.redactText(snapshot.chat.title).value
                        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                        .trim()
                        .take(48)
                        .ifBlank { "conversation" }
                // 导入的聊天 ID 不一定是 UUID，不能把其中的路径分隔符拼入导出目标。
                val chatSuffix = ConversationAuditHasher.sha256(snapshot.chat.id.toByteArray(Charsets.UTF_8)).take(8)
                val baseName = "$timestamp-$safeTitle-$chatSuffix"
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
                            preserveCompleteness = true,
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
                        snapshot.toCredentialRedactedChatJson(archiveJson),
                    )
                    zip.writeEntry(
                        "audit.json",
                        archiveJson.encodeToString(
                            snapshot.toOperitArchivedConversationAudit()
                        ),
                    )
                    zip.writeEntry(
                        "timeline.md",
                        java.io.StringWriter().also { writer ->
                            ConversationAuditMarkdownRenderer.write(snapshot, writer, inlinePayloads = false, externalShare = false)
                        }.toString(),
                    )
                    zip.writeEntry(
                        "events.jsonl",
                        snapshot.events.joinToString("\n") { event ->
                            ConversationAuditMarkdownRenderer.eventJson(
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
                                ConversationAuditMarkdownRenderer.payloadPath(payloadSha256, payload),
                                requireNotNull(payload.bytes) { "Complete audit payload was omitted" },
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
                val writer = output.bufferedWriter(Charsets.UTF_8)
                ConversationAuditMarkdownRenderer.write(snapshot, writer, inlinePayloads = true, externalShare = true)
                writer.flush()
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
                    .put("path", ConversationAuditMarkdownRenderer.payloadPath(payloadSha256, payload))
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
                "Signed payloads retain their original redacted bytes. Unsigned chat projections and readable timeline text are credential-redacted again at export; recognized patterns are covered, not arbitrary secrets.",
            )
            .put(
                "aiReviewMarkdown",
                "The AI diagnostics Markdown is UTF-8 plaintext, additionally pseudonymizes private paths and identity fields, and is not encrypted.",
            )

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
