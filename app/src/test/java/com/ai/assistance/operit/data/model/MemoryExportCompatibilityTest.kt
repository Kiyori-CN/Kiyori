package com.ai.assistance.operit.data.model

import java.util.Date
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MemoryExportCompatibilityTest {
    @Test fun oldBackupDefaultsToActiveMemory() {
        val memory = Json.decodeFromString<SerializableMemory>("""{"uuid":"old","title":"t","content":"c","contentType":"text/plain","source":"user_input","credibility":0.8,"importance":0.5,"folderPath":null,"createdAt":1,"updatedAt":2,"tagNames":[]}""")
        assertFalse(memory.archived)
        assertFalse(memory.isDocumentNode)
        assertEquals("", memory.libraryKind)
    }

    @Test fun knowledgeChunksAndArchiveSurviveRoundTripWithoutCredentialsOrVectors() {
        val memory = SerializableMemory("stable", "资料", "摘要", "text/plain", "document_import", 0.8f, 0.5f, "项目", Date(1), Date(2), listOf("协议"), "knowledge", "fact", true, true, "content://document/1", listOf("第一块", "第二块"))
        val encoded = Json.encodeToString(memory)
        assertEquals(memory, Json.decodeFromString<SerializableMemory>(encoded))
        assertFalse(encoded.contains("embedding"))
        assertFalse(encoded.contains("apiKey"))
    }
}
