package com.ai.assistance.operit.data.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 真正经过 native cursor；Kotlin 直接构造或 JSON 兼容测试覆盖不到旧库缺字段。 */
class MemoryObjectBoxCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun absentMetadataSurvivesNativeReadReopenAndUpdate() {
        val directory = temporary.newFolder("memory-db")
        val id: Long
        val chunkId: Long
        MyObjectBox.builder().directory(directory).build().use { store ->
            val box = store.boxFor(Memory::class.java)
            // ObjectBox 不存储 null 属性，与旧 schema 中尚不存在属性的记录具有相同缺值语义。
            id = box.put(Memory(title = "旧文档", content = "摘要", isDocumentNode = true))
            val chunk = DocumentChunk(content = "原始正文")
            chunk.memory.targetId = id
            chunkId = store.boxFor(DocumentChunk::class.java).put(chunk)
            assertEquals("knowledge", MemoryLibraryPolicy.kind(box.all.single()))
        }
        MyObjectBox.builder().directory(directory).build().use { store ->
            val box = store.boxFor(Memory::class.java)
            val memory = box.all.single()
            assertEquals(id, memory.id)
            assertNull(memory.libraryKind)
            assertNull(memory.embeddingModelKey)
            assertEquals("other", MemoryLibraryPolicy.category(memory))
            assertEquals("knowledge", MemoryLibraryPolicy.kind(memory))
            assertEquals("原始正文", memory.documentChunks.single().content)
            val chunk = store.boxFor(DocumentChunk::class.java).get(chunkId)
            assertNull(chunk.embeddingContentHash)
            assertFalse(MemoryLibraryPolicy.compatible(Embedding(floatArrayOf(1f)), chunk.embeddingModelKey,
                chunk.embeddingContentHash, CloudEmbeddingConfig(), chunk.content))
            memory.category = "fact"
            memory.archived = true
            box.put(memory)
            val restored = box.get(id)
            assertEquals(memory.uuid, restored.uuid)
            assertEquals("fact", MemoryLibraryPolicy.category(restored))
            assertTrue(restored.archived)
            assertEquals(chunkId, restored.documentChunks.single().id)
        }
    }

    @Test fun plainLegacyMemoryKeepsItsScope() {
        MyObjectBox.builder().directory(temporary.newFolder("plain-db")).build().use { store ->
            val box = store.boxFor(Memory::class.java)
            box.put(Memory(title = "旧记忆", content = "保留正文"))
            val memory = box.query().build().use { it.findFirst()!! }
            assertTrue(MemoryLibraryPolicy.matches(memory, "memory"))
            assertFalse(MemoryLibraryPolicy.matches(memory, "knowledge"))
            assertEquals("保留正文", memory.content)
        }
    }
}
