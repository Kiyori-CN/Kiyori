package com.ai.assistance.operit.data.dao

import com.ai.assistance.operit.data.model.MessageVariantEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatContentDaoBatchTest {
    @Test fun emptyDoesNotQueryAndLargeSparseSelectionStaysExactOrderedAndUnique() = runBlocking {
        val dao = RecordingDao()
        assertTrue(dao.getVariantsForMessages("chat", emptyList()).isEmpty())
        assertTrue(dao.batches.isEmpty())
        val selected = (0L..2100L).map { it * 1_000_000L }
        val result = dao.getVariantsForMessages("chat", selected.reversed() + selected.take(8))
        assertEquals(selected.flatMap { listOf(it, it) }, result.map { it.messageTimestamp })
        assertEquals(listOf(998, 998, 105), dao.batches.map { it.size })
        assertEquals(selected, dao.batches.flatten())
        assertTrue(result.chunked(2).all { it.map { row -> row.variantIndex } == listOf(1, 2) })
    }

    @Test fun sqliteBoundaryAndLongContentMaterializationArePreserved() = runBlocking {
        for (size in listOf(998, 999)) {
            val dao = RecordingDao()
            val result = dao.getVariantsForMessages("chat", (1L..size.toLong()).toList())
            assertEquals(size * 2, result.size)
            assertEquals(if (size == 998) 1 else 2, dao.batches.size)
        }
        val dao = RecordingDao(longContent = true)
        val result = dao.getVariantsForMessages("chat", listOf(1L))
        assertEquals("a".repeat(65536) + "tail", result.first().content)
    }

    @Test fun failedBatchPropagatesInsteadOfReturningPartialSuccess() = runBlocking {
        val dao = RecordingDao(failAfterFirst = true)
        try {
            dao.getVariantsForMessages("chat", (1L..999L).toList())
            fail("expected second batch failure")
        } catch (error: IllegalStateException) {
            assertEquals("database read failed", error.message)
        }
    }

    private class RecordingDao(val longContent: Boolean = false, val failAfterFirst: Boolean = false) : ChatContentDao() {
        override suspend fun getSelectedContentCharacterCountsByChat(): List<ChatContentCharacterCount> = error("Unexpected aggregate query")
        val batches = mutableListOf<List<Long>>()
        override suspend fun queryVariantsForMessages(chatId: String, messageTimestamps: List<Long>): List<MessageVariantContentRow> {
            check(messageTimestamps.size + 1 <= 999) { "too many SQL variables" }
            if (failAfterFirst && batches.isNotEmpty()) error("database read failed")
            batches += messageTimestamps
            return messageTimestamps.sorted().flatMap { timestamp ->
                (1..2).map { index ->
                    val content = if (longContent) "a".repeat(65536) else "body"
                    MessageVariantContentRow(
                        MessageVariantEntity(chatId = chatId, messageTimestamp = timestamp, variantIndex = index, content = content),
                        if (longContent) 65540L else 4L,
                    )
                }
            }
        }
        override suspend fun queryMessageVariantContentChunk(variantId: Long, startCharacter: Long, characterCount: Int): String? = "tail"
        override suspend fun queryMessagesForChat(chatId: String): List<MessageContentRow> = error("Unexpected query: queryMessagesForChat")
        override suspend fun queryMessagesForChatFromTimestampAsc(chatId: String, startTimestampInclusive: Long): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatFromTimestampAsc")
        override suspend fun queryMessagesForChatWindowAsc(chatId: String, startTimestampInclusive: Long, endTimestampInclusive: Long): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatWindowAsc")
        override suspend fun queryMessagesForChatAsc(chatId: String, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatAsc")
        override suspend fun queryMessagesForChatDesc(chatId: String, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatDesc")
        override suspend fun queryMessagesForChatAscRange(chatId: String, offset: Int, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatAscRange")
        override suspend fun queryMessagesForChatDescRange(chatId: String, offset: Int, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatDescRange")
        override suspend fun queryMessagesForChatAfterTimestampExclusiveAsc(chatId: String, afterTimestampExclusive: Long, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatAfterTimestampExclusiveAsc")
        override suspend fun queryMessagesForChatInRangeAsc(chatId: String, afterTimestampExclusive: Long?, beforeTimestampExclusive: Long?, upToTimestampInclusive: Long?): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatInRangeAsc")
        override suspend fun queryMessagesForChatBeforeTimestampDesc(chatId: String, maxTimestamp: Long, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatBeforeTimestampDesc")
        override suspend fun queryMessagesForChatBeforeTimestampExclusiveDesc(chatId: String, beforeTimestampExclusive: Long, limit: Int): List<MessageContentRow> = error("Unexpected query: queryMessagesForChatBeforeTimestampExclusiveDesc")
        override suspend fun queryMessageByTimestamp(chatId: String, timestamp: Long): MessageContentRow? = error("Unexpected query: queryMessageByTimestamp")
        override suspend fun queryMessageContentChunk(messageId: Long, startCharacter: Long, characterCount: Int): String? = error("Unexpected query: queryMessageContentChunk")
        override suspend fun queryVariantsForChat(chatId: String): List<MessageVariantContentRow> = error("Unexpected query: queryVariantsForChat")
        override suspend fun queryVariantsForMessage(chatId: String, messageTimestamp: Long): List<MessageVariantContentRow> = error("Unexpected query: queryVariantsForMessage")
        override suspend fun queryVariantForMessage(chatId: String, messageTimestamp: Long, variantIndex: Int): MessageVariantContentRow? = error("Unexpected query: queryVariantForMessage")
    }
}
