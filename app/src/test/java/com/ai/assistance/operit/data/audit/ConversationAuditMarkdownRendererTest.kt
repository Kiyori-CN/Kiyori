package com.ai.assistance.operit.data.audit

import com.ai.assistance.operit.data.model.*
import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class ConversationAuditMarkdownRendererTest {
    private val hash = "a".repeat(64)
    private fun snapshot() = ConversationAuditExportSnapshot(
        chat = ChatEntity(id = "chat", title = "diagnostics"),
        messages = listOf(MessageEntity(chatId = "chat", sender = "ai", content = "original", timestamp = 1L, orderIndex = 0)),
        variants = emptyList(),
        audit = ConversationAuditEntity(chatId = "chat", schemaVersion = 1, completenessStatus = "PARTIAL",
            eventCount = 0, lastSequenceNumber = 0, chainHeadSha256 = "", latestSealSequenceNumber = 0, createdAt = 1, updatedAt = 2,
            lastFailureCode = "STREAM_INTERRUPTED"),
        cutoffEventId = null, events = emptyList(), eventPayloads = emptyMap(), revisions = emptyList(),
        projections = emptyList(), seals = emptyList(), payloads = emptyMap(), exportedAt = 1000,
    )
    private fun render(snapshot: ConversationAuditExportSnapshot): String = StringWriter().also {
        ConversationAuditMarkdownRenderer.write(snapshot, it, inlinePayloads = true, externalShare = true)
    }.toString()

    @Test fun repeatedLargeHookPayloadIsExpandedOnceAndEveryEventKeepsItsReference() {
        val body = "unique large hook body\n" + "history content ".repeat(800)
        val base = snapshotWithPayload(body, 100)
        val before = base.payloads.getValue(hash).bytes.copyOf()
        val text = render(base)
        assertEquals(1, Regex("unique large hook body").findAll(text).count())
        assertEquals(99, Regex(Regex.escape("[查看首次正文](#payload-$hash)")).findAll(text).count())
        assertEquals(1, Regex("##### payload-$hash").findAll(text).count())
        assertTrue("Repeated hook bodies must not dominate the export", text.length < 200_000)
        assertTrue(text.contains("### 100. HOOK / PROMPT_HOOK_COMPLETED"))
        assertTrue(text.contains("\"sequenceNumber\":100"))
        assertArrayEquals(before, base.payloads.getValue(hash).bytes)
        assertEquals(text, render(base))
    }

    @Test fun revisionAndEventShareOneBodyButChangedPayloadIsNotOmitted() {
        val original = snapshotWithPayload("first unique body", 2)
        val secondHash = "b".repeat(64)
        val changedBytes = "changed unique body".toByteArray()
        val first = original.payloads.getValue(hash)
        val changed = ConversationAuditSnapshotPayload(first.entity.copy(
            payloadSha256 = secondHash, plainByteCount = changedBytes.size.toLong()), changedBytes)
        val current = original.copy(
            revisions = listOf(ConversationMessageRevisionEntity(revisionId = "rev", chatId = "chat", messageTimestamp = 1,
                variantIndex = 0, revisionNumber = 0, sender = "ai", contentPayloadSha256 = hash,
                auditEventId = "event1", source = "TEST", createdAt = 1)),
            payloads = original.payloads + (secondHash to changed),
            eventPayloads = original.eventPayloads + ("event2" to listOf(
                ConversationAuditEventPayloadEntity("event2", secondHash, "hook_output", 0, "context"))),
        )
        val text = render(current)
        assertEquals(1, Regex("first unique body").findAll(text).count())
        assertEquals(1, Regex("changed unique body").findAll(text).count())
        assertTrue(text.contains("[查看首次正文](#payload-$hash)"))
        assertTrue(text.contains("##### payload-$secondHash"))
    }

    @Test fun repeatedPayloadIsRedactedBeforeItsSingleExpansionAndPackageKeepsPaths() {
        val current = snapshotWithPayload("password=never-export-this", 2)
        val text = render(current)
        assertFalse(text.contains("never-export-this"))
        assertTrue(text.contains("REDACTED"))
        assertTrue(text.contains("[查看首次正文](#payload-$hash)"))
        val packaged = StringWriter().also {
            ConversationAuditMarkdownRenderer.write(current, it, inlinePayloads = false, externalShare = false)
        }.toString()
        assertEquals(2, Regex(Regex.escape("payloads/$hash.txt")).findAll(packaged).count())
        assertFalse(packaged.contains("##### payload-"))
        assertFalse(packaged.contains("never-export-this"))
    }

    private fun snapshotWithPayload(text: String, eventCount: Int): ConversationAuditExportSnapshot {
        val bytes = text.toByteArray()
        val payload = ConversationAuditPayloadEntity(hash, "payload", bytes.size.toLong(), 100,
            "text/plain", "utf-8", "none", "AES/GCM/NoPadding", "key", "nonce", 1)
        val events = (1..eventCount).map { number ->
            ConversationAuditEventEntity(eventId = "event$number", chatId = "chat", sequenceNumber = number.toLong(),
                occurredAt = 1, recordedAt = 1, category = "HOOK", eventType = "PROMPT_HOOK_COMPLETED", actor = "hook",
                summary = "hook completed", previousEventSha256 = hash, eventSha256 = hash, visibility = "VISIBLE")
        }
        return snapshot().copy(events = events,
            payloads = mapOf(hash to ConversationAuditSnapshotPayload(payload, bytes)),
            eventPayloads = events.associate { event -> event.eventId to listOf(
                ConversationAuditEventPayloadEntity(event.eventId, hash, "hook_output", 0, "context")) })
    }

    @Test fun selectedVariantAndItsUsageAreTheCurrentConversation() {
        val base = snapshot()
        val current = base.copy(messages = listOf(base.messages.single().copy(selectedVariantIndex = 1)),
            variants = listOf(MessageVariantEntity(chatId = "chat", messageTimestamp = 1, variantIndex = 1,
                content = "selected answer", providerRequestCount = 2, providerUsageRequestCount = 1,
                providerCacheMetricRequestCount = 1, providerCacheReadTokens = 500, providerCacheMetricPromptTokens = 1000)))
        val text = render(current)
        val conversation = text.substringBefore("### 历史 AI variant")
        assertTrue(conversation.contains("selected answer"))
        assertFalse(conversation.contains("original"))
        assertTrue(conversation.contains("usage coverage: 1/2"))
        assertTrue(conversation.contains("read=500"))
        val history = text.substringAfter("### 历史 AI variant")
        assertTrue(history.contains("variant=0"))
        assertTrue(history.contains("original"))
    }

    @Test fun missingUsageIsNotReportedAsZeroCacheHits() {
        val text = render(snapshot())
        assertTrue(text.contains("provider cache: not_reported"))
        assertTrue(text.contains("1970-01-01T00:00:01Z"))
        assertTrue(text.contains("STREAM_INTERRUPTED"))
    }

    @Test fun revisionOnlyPayloadAndProjectionAreIncludedWithSafeFences() {
        val bytes = "original with ```` fence".toByteArray()
        val payload = ConversationAuditPayloadEntity(hash, "payload", bytes.size.toLong(), 100,
            "text/plain", "utf-8", "none", "AES/GCM/NoPadding", "key", "nonce", 1)
        val base = snapshot().copy(
            revisions = listOf(ConversationMessageRevisionEntity(revisionId = "rev", chatId = "chat", messageTimestamp = 1,
                variantIndex = 0, revisionNumber = 0, sender = "ai", contentPayloadSha256 = hash,
                auditEventId = "event", source = "TEST", createdAt = 1)),
            projections = listOf(ConversationMessageProjectionEntity("chat", 1, 0, "rev", 3, 1)),
            payloads = mapOf(hash to ConversationAuditSnapshotPayload(payload, bytes)),
        )
        val text = render(base)
        assertTrue(text.contains("`````text\noriginal with ```` fence\n`````"))
        assertTrue(text.contains("current projection: timestamp=1, variant=0, revision=rev"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingSelectedVariantDoesNotSilentlyExportAnotherAnswer() {
        val base = snapshot()
        render(base.copy(messages = listOf(base.messages.single().copy(selectedVariantIndex = 9))))
    }

    @Test fun packageProjectionAndTimelineRedactCredentialsWithoutMutatingSnapshot() {
        val base = snapshot()
        val current = base.copy(
            chat = base.chat.copy(title = "password=title-secret"),
            messages = listOf(base.messages.single().copy(content = "api_key=base-secret")),
            variants = listOf(MessageVariantEntity(chatId = "chat", messageTimestamp = 1,
                variantIndex = 1, content = "Bearer variant-secret")),
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val before = current.toOperitArchivedConversationAudit()
        val archiveText = current.toCredentialRedactedChatJson(json)
        val restored = json.decodeFromString<OperitArchivedChat>(archiveText)
        val timeline = StringWriter().also {
            ConversationAuditMarkdownRenderer.write(current, it, inlinePayloads = false, externalShare = false)
        }.toString()
        for (secret in listOf("title-secret", "base-secret", "variant-secret")) {
            assertFalse(archiveText.contains(secret))
            assertFalse(timeline.contains(secret))
        }
        assertTrue(archiveText.contains("REDACTED:credential"))
        assertEquals("chat", restored.id)
        assertEquals("api_key=base-secret", current.messages.single().content)
        assertEquals(before, current.toOperitArchivedConversationAudit())
    }
}
