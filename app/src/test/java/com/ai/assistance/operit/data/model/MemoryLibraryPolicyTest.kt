package com.ai.assistance.operit.data.model

import org.junit.Assert.*
import org.junit.Test

class MemoryLibraryPolicyTest {
    private val config = CloudEmbeddingConfig(true, "https://example.com/v1", "local-test", "model-a")

    @Test fun legacyDocumentsAreKnowledgeWithoutRewritingIds() {
        val memory = Memory(id = 7, uuid = "stable", isDocumentNode = true)
        assertEquals("knowledge", MemoryLibraryPolicy.kind(memory))
        assertEquals("stable", memory.uuid)
        assertEquals(7L, memory.id)
    }

    @Test fun legacyTextRemainsMemory() {
        assertEquals("memory", MemoryLibraryPolicy.kind(Memory()))
    }

    @Test fun diaryIsAFirstClassKind() {
        assertEquals(MemoryLibraryPolicy.DIARY, MemoryLibraryPolicy.kind(Memory(libraryKind = MemoryLibraryPolicy.DIARY)))
        assertTrue(MemoryLibraryPolicy.matches(Memory(libraryKind = MemoryLibraryPolicy.DIARY), MemoryLibraryPolicy.DIARY))
        assertFalse(MemoryLibraryPolicy.matches(Memory(libraryKind = MemoryLibraryPolicy.DIARY), MemoryLibraryPolicy.MEMORY))
    }

    @Test fun archiveAndTypeAreIndependentScopes() {
        val archived = Memory(title = "fact", archived = true)
        assertFalse(MemoryLibraryPolicy.matches(archived, null))
        assertTrue(MemoryLibraryPolicy.matches(archived, "memory", archived = true))
        assertFalse(MemoryLibraryPolicy.matches(archived, "knowledge", archived = true))
        assertFalse(MemoryLibraryPolicy.matches(Memory(title = ".folder_placeholder"), null))
    }

    @Test fun sameDimensionDoesNotMakeDifferentModelsCompatible() {
        val vector = Embedding(floatArrayOf(1f, 2f))
        val key = MemoryLibraryPolicy.modelKey(config)
        val hash = MemoryLibraryPolicy.digest("fact")
        assertTrue(MemoryLibraryPolicy.compatible(vector, key, hash, config, "fact"))
        assertFalse(MemoryLibraryPolicy.compatible(vector, key, hash, config.copy(model = "model-b"), "fact"))
        assertFalse(MemoryLibraryPolicy.compatible(vector, key, hash, config.copy(endpoint = "https://other.example/v1"), "fact"))
    }

    @Test fun changingCredentialsDoesNotInvalidateVectors() {
        assertEquals(MemoryLibraryPolicy.modelKey(config), MemoryLibraryPolicy.modelKey(config.copy(apiKey = "rotated")))
    }

    @Test fun changedContentAndUnknownProvenanceAreRejected() {
        val vector = Embedding(floatArrayOf(1f))
        assertFalse(MemoryLibraryPolicy.compatible(vector, "", "", config, "fact"))
        assertFalse(MemoryLibraryPolicy.compatible(vector, MemoryLibraryPolicy.modelKey(config), MemoryLibraryPolicy.digest("old"), config, "new"))
    }

    @Test fun invalidVectorsNeverEnterTheIndex() {
        listOf(floatArrayOf(), floatArrayOf(0f, 0f), floatArrayOf(Float.NaN), floatArrayOf(Float.POSITIVE_INFINITY)).forEach {
            assertFalse(MemoryLibraryPolicy.validVector(it))
        }
        assertTrue(MemoryLibraryPolicy.validVector(floatArrayOf(-0.1f, 0.1f)))
    }

    @Test fun longUnbrokenTextHasBoundedOverlappingCoverage() {
        val text = (0..9999).joinToString("") { "$it 中" }
        val chunks = MemoryLibraryPolicy.chunks(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= MemoryLibraryPolicy.CHUNK_CHARS })
        val restored = chunks.first() + chunks.drop(1).joinToString("") { it.drop(MemoryLibraryPolicy.CHUNK_OVERLAP) }
        assertEquals(text, restored)
        assertEquals(chunks, MemoryLibraryPolicy.chunks(text))
    }

    @Test fun paragraphBoundariesPreserveFinalContent() {
        val text = ("段落内容。\n".repeat(2000)) + "最终证据"
        val chunks = MemoryLibraryPolicy.chunks(text)
        assertTrue(chunks.last().endsWith("最终证据"))
        assertTrue(chunks.all { it.length <= 1800 })
    }

    @Test fun surrogatePairsAreNotSplit() {
        MemoryLibraryPolicy.chunks("😀".repeat(4000)).forEach {
            assertFalse(Character.isLowSurrogate(it.first()))
            assertFalse(Character.isHighSurrogate(it.last()))
        }
    }

    @Test(expected = IllegalArgumentException::class) fun emptyDocumentFailsExplicitly() {
        MemoryLibraryPolicy.chunks(" \n ")
    }

    @Test(expected = IllegalArgumentException::class) fun oversizedDocumentFailsInsteadOfTruncating() {
        MemoryLibraryPolicy.chunks("a".repeat(MemoryLibraryPolicy.MAX_DOCUMENT_CHARS + 1))
    }
}
