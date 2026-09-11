package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.ConversationAuditEventEntity
import com.ai.assistance.operit.data.model.ConversationAuditEventPayloadEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** 逐段输出诊断文本，避免在完整快照之外再建立整个 Markdown 的字符串和 UTF-8 副本。 */
internal object ConversationAuditMarkdownRenderer {
    fun write(
        snapshot: ConversationAuditExportSnapshot,
        writer: java.io.Writer,
        inlinePayloads: Boolean,
        externalShare: Boolean,
    ) {
        val emittedPayloads = mutableSetOf<String>()
        writer.apply {
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
            appendLine("- 导出时间: `${Instant.ofEpochMilli(snapshot.exportedAt)}`（UTC；epochMs=${snapshot.exportedAt}）")
            appendLine("- 最近记录异常: `${snapshot.audit.lastFailureCode ?: "none"}`")
            appendLine("- cutoffEventId: `${snapshot.cutoffEventId ?: "none"}`")
            appendLine("- 事件数: ${snapshot.events.size}")
            if (inlinePayloads) {
                appendLine("- 导出格式: 明文 UTF-8 Markdown（未加密）")
                appendLine("- 相同 SHA-256 的 payload 正文仅展开一次，后续引用链接到本文件首次正文；事件与修订均保留。")
                appendLine("- 诊断为有界阅读投影：payload 每项最多 64 KiB、合计最多 8 MiB，优先保留失败证据；消息正文最多展示首尾各 32 Ki 字符。完整正文请另行导出 `.kiyori-audit`。")
            }
            appendLine("- 链头: `${snapshot.audit.chainHeadSha256}`")
            appendLine()
            appendLine("> 此文档只描述 Kiyori 实际可观察并持久化的事实，不包含 Provider 未返回的内部推理。")
            appendLine("> 记录完整性不代表回答成功；传输失败与用户停止分别按事件解释。未报告的用量或缓存指标不是零命中。")
            appendLine("> SSH 与远程 Windows 仅包含工具提交和实际返回；断线后远端进程是否继续、屏幕以外的状态不由本地审计保证。")
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
            val variantsByMessage = snapshot.variants.groupBy { it.messageTimestamp }
            snapshot.messages.sortedBy { it.orderIndex }.forEachIndexed { index, entity ->
                val variants = variantsByMessage[entity.timestamp].orEmpty()
                val selected = variants.firstOrNull { it.variantIndex == entity.selectedVariantIndex }
                require(entity.selectedVariantIndex == 0 || selected != null) { "Selected audit message variant is missing" }
                val message = selected?.applyTo(entity.toChatMessage(), variants.size + 1) ?: entity.toChatMessage()
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
                appendLine("- selectedVariant: ${message.selectedVariantIndex}; completedAt=${message.completedAt}; waitMs=${message.waitDurationMs}; outputMs=${message.outputDurationMs}")
                appendLine("- provider usage coverage: ${message.providerUsageRequestCount}/${message.providerRequestCount}; cache metric coverage: ${message.providerCacheMetricRequestCount}/${message.providerUsageRequestCount}")
                if (message.providerUsageRequestCount > 0) {
                    appendLine("- provider tokens: input=${message.providerTotalInputTokens}, uncachedInput=${message.providerUncachedInputTokens}, output=${message.providerOutputTokens}, reasoning=${message.providerReasoningTokens}")
                } else appendLine("- provider tokens: not_reported（上方 tokens 为历史兼容字段，不代表供应商本轮明确报告）")
                if (message.providerCacheMetricRequestCount > 0) {
                    appendLine("- provider cache: read=${message.providerCacheReadTokens}, write=${message.providerCacheWriteTokens}, measuredPrompt=${message.providerCacheMetricPromptTokens}")
                } else appendLine("- provider cache: not_reported")
                val messageText = boundedMessage(
                    redactForReview(
                        value = message.content,
                        mediaType = "text/markdown",
                        externalShare = externalShare,
                    ), externalShare)
                val messageFence = markdownFence(messageText)
                appendLine("${messageFence}text")
                appendLine(messageText)
                appendLine(messageFence)
                appendLine()
            }
            if (snapshot.variants.isNotEmpty()) {
                appendLine("### 历史 AI variant")
                appendLine()
                // 基础版本存储于 messages，不在 message_variants。当前投影改为选中版本后，
                // 必须把已不再显示的 variant 0 纳入历史，否则诊断文档会丢失最初回答。
                val baseVariants = snapshot.messages.filter { it.sender == "ai" && it.selectedVariantIndex != 0 }.map {
                    MessageVariantEntity.fromChatMessage(it.chatId, it.timestamp, 0, it.toChatMessage())
                }
                (baseVariants + snapshot.variants)
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
                        val variantText = boundedMessage(
                            redactForReview(
                                value = variant.content,
                                mediaType = "text/markdown",
                                externalShare = externalShare,
                            ), externalShare)
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
                    val payload = requireNotNull(snapshot.payloads[revision.contentPayloadSha256])
                    if (inlinePayloads && payload.entity.encoding == "utf-8") {
                        writeInlinePayload(revision.contentPayloadSha256, payload, emittedPayloads, externalShare)
                    } else appendLine("  payload: `${payloadPath(revision.contentPayloadSha256, payload)}`")
                }
                snapshot.projections.forEach { projection ->
                    appendLine("- current projection: timestamp=${projection.messageTimestamp}, variant=${projection.variantIndex}, revision=${projection.currentRevisionId}, estimatedTokens=${projection.estimatedTokenCount}")
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
                appendLine("- occurredAt: `${Instant.ofEpochMilli(event.occurredAt)}`（epochMs=${event.occurredAt}）; recordedAt=${event.recordedAt}")
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
                        writeInlinePayload(ref.payloadSha256, payload, emittedPayloads, externalShare)
                    } else if (inlinePayloads) {
                        appendLine("- 二进制附件未内嵌 Markdown；实际字节仅在完整审计包中。")
                        appendLine("- SHA-256: `${ref.payloadSha256}`；字节数: ${payload.entity.plainByteCount}")
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
            appendLine("每行对应一个已封印事件；payload 正文在上方首次引用处展开，其余关联保留 SHA-256 与正文链接。")
            appendLine()
            // JSON 字符串会转义换行，不会生成独立围栏行；逐事件写入避免整条时间线副本。
            appendLine("```jsonl")
            snapshot.events.forEach { event ->
                appendLine(
                    redactForReview(
                        value = eventJson(event, snapshot.eventPayloads[event.eventId].orEmpty()).toString(),
                        mediaType = "application/json",
                        externalShare = externalShare,
                    )
                )
            }
            appendLine("```")
        }

    }

    private fun java.io.Writer.writeInlinePayload(
        hash: String,
        payload: ConversationAuditSnapshotPayload,
        emittedPayloads: MutableSet<String>,
        externalShare: Boolean,
    ) {
        // 存储层已按正文哈希去重，导出也必须复用同一身份。长历史在 Hook 开始/完成与
        // 反复发送中会被多次引用；逐引用展开会让明文暴增。只缩减展示，不删除封印证据。
        appendLine("- SHA-256: `$hash`；字节数: ${payload.entity.plainByteCount}")
        if (!emittedPayloads.add(hash)) {
            appendLine("- 正文与此前相同：[查看首次正文](#payload-$hash)")
            return
        }
        appendLine()
        appendLine("##### payload-$hash")
        appendLine()
        val bytes = payload.bytes
        if (bytes == null || bytes.size > MAX_DIAGNOSTIC_PAYLOAD_BYTES) {
            appendLine("- 大型正文未展开或达到总预算（每项 64 KiB、合计 8 MiB）；SHA-256 对应完整原文，请另行导出 `.kiyori-audit`。")
            return
        }
        val text = redactForReview(bytes.toString(Charsets.UTF_8), payload.entity.mediaType, externalShare)
        val fence = markdownFence(text)
        appendLine("${fence}text")
        appendLine(text)
        appendLine(fence)
    }

    private fun boundedMessage(text: String, enabled: Boolean): String {
        if (!enabled || text.length <= 65536) return text
        // 脱敏完成后截取首尾，避免截断凭据边界；尾部保留最终错误与结论。
        return text.take(32768) + "\n[诊断省略中间 ${text.length - 65536} 字符；完整正文见审计包]\n" + text.takeLast(32768)
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
            // 当前聊天投影来自普通消息表，不能假定它已经经过审计 payload 脱敏。
            ConversationAuditRedactor.redactText(value, mediaType).value
        }

    private fun markdownFence(value: String): String {
        val longestRun =
            Regex("`+")
                .findAll(value)
                .maxOfOrNull { match -> match.value.length }
                ?: 0
        return "`".repeat(maxOf(3, longestRun + 1))
    }

    fun eventJson(
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

    fun payloadPath(
        payloadSha256: String,
        payload: ConversationAuditSnapshotPayload,
    ): String =
        "payloads/$payloadSha256." +
            if (payload.entity.encoding.equals("utf-8", ignoreCase = true)) "txt" else "bin"

}
