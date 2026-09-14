package com.ai.assistance.operit.data.model

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import io.objectbox.BoxStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import android.util.Log
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.util.UUID

class MemoryRepositoryCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun withRepository(block: suspend (MemoryRepository, BoxStore) -> Unit) = runBlocking(Dispatchers.IO) {
        val profile = "test-${UUID.randomUUID()}"
        val context = mock(Context::class.java)
        val preferences = mock(SharedPreferences::class.java)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.filesDir).thenReturn(temporary.root)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences)
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        // 只替换测试空间的 Android 建库入口，Repository 使用真实 native Box 和事务。
        val field = ObjectBoxManager::class.java.getDeclaredField("stores").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stores = field.get(ObjectBoxManager) as MutableMap<String, BoxStore>
        stores[profile] = store
        // Android 日志由测试桩接收；数据库和仓库逻辑保持真实。同步 IO 路径在同一线程开关库。
        val logging = mockStatic(Log::class.java)
        try { block(MemoryRepository(context, profile), store) }
        finally {
            store.closeThreadResources()
            ObjectBoxManager.close(profile)
            logging.close()
        }
    }

    @Test fun legacyStatisticsCrossBatchBoundariesWithoutLosingRecords() = withRepository { repository, store ->
        val memories = (0..259).map { Memory(title = "旧条目 $it", content = "正文", embedding = Embedding(floatArrayOf(1f, 2f))) }
        store.boxFor(Memory::class.java).put(memories)
        store.boxFor(DocumentChunk::class.java).put((0..129).map { DocumentChunk(content = "区块 $it") })
        val usage = repository.getEmbeddingDimensionUsage()
        assertEquals(260, usage.memoryTotal)
        assertEquals(260, usage.memoryMissing)
        assertEquals(260, usage.memoryDimensions.single().count)
        assertEquals(130, usage.chunkTotal)
        assertEquals(130, usage.chunkMissing)
    }

    @Test fun localCreateEditArchivePreserveIdentityAndTags() = withRepository { repository, store ->
        val memory = requireNotNull(repository.createMemory("标题", "正文", tags = listOf("项目", "项目", " 决策 "), category = "decision"))
        assertEquals(setOf("项目", "决策"), store.boxFor(Memory::class.java).get(memory.id).tags.map { it.name }.toSet())
        val updated = requireNotNull(repository.updateMemory(memory, "新标题", "新正文", newTags = listOf("事实")))
        assertEquals(memory.uuid, updated.uuid)
        assertEquals(listOf("事实"), store.boxFor(Memory::class.java).get(memory.id).tags.map { it.name })
        repository.setArchived(memory.id, true)
        assertTrue(store.boxFor(Memory::class.java).get(memory.id).archived)
        repository.setArchived(memory.id, false)
        assertFalse(store.boxFor(Memory::class.java).get(memory.id).archived)
    }

    @Test fun legacyDocumentBackupRoundTripRetainsChunksAndDefaults() = withRepository { repository, store ->
        val box = store.boxFor(Memory::class.java)
        val document = Memory(title = "旧资料", content = "摘要", isDocumentNode = true)
        box.put(document)
        store.boxFor(DocumentChunk::class.java).put(DocumentChunk(content = "完整证据").also { it.memory.target = document })
        val encoded = repository.exportMemoriesToJson()
        val decoded = kotlinx.serialization.json.Json.decodeFromString<MemoryExportData>(encoded)
        val item = decoded.memories.single()
        assertEquals("knowledge", item.libraryKind)
        assertEquals("other", item.category)
        assertEquals(listOf("完整证据"), item.chunks)
        val result = repository.importMemoriesFromJson(encoded, ImportStrategy.CREATE_NEW)
        assertEquals(1, result.newMemories)
        val restored = box.all.first { it.id != document.id }
        assertNotEquals(document.uuid, restored.uuid)
        assertTrue(restored.isDocumentNode)
        assertEquals("完整证据", restored.documentChunks.single().content)
    }

    @Test fun textAndTagSearchRespectKindArchiveAndDocumentWildcards() = withRepository { repository, store ->
        val box = store.boxFor(Memory::class.java)
        val ordinary = Memory(title = "普通条目", content = "nebula evidence")
        ordinary.tags.add(MemoryTag(name = "quasar"))
        val archived = Memory(title = "nebula archived", archived = true)
        val document = Memory(title = "资料", content = "摘要", isDocumentNode = true)
        box.put(listOf(ordinary, archived, document))
        store.boxFor(DocumentChunk::class.java).put(DocumentChunk(content = "alpha middle omega").also { it.memory.target = document })
        assertEquals(listOf(ordinary.id), repository.searchMemories("nebula", semanticWeight = 0f, edgeWeight = 0f).map { it.id })
        assertEquals(listOf(ordinary.id), repository.searchMemories("quasar", semanticWeight = 0f, edgeWeight = 0f).map { it.id })
        assertTrue(repository.searchMemories("nebula", libraryKind = "knowledge", semanticWeight = 0f, edgeWeight = 0f).isEmpty())
        assertEquals(listOf(document.id), repository.searchMemories("alpha*omega", libraryKind = "knowledge", semanticWeight = 0f, edgeWeight = 0f).map { it.id })
        assertEquals(listOf(archived.id), repository.searchMemories("nebula", archived = true, semanticWeight = 0f, edgeWeight = 0f).map { it.id })
    }

    @Test fun folderRenameAndRemovalCoverDescendantsWithoutDeletingContent() = withRepository { repository, store ->
        val box = store.boxFor(Memory::class.java)
        val parent = Memory(title = "父目录条目", folderPath = "项目")
        val child = Memory(title = "子目录资料", folderPath = "项目/资料", isDocumentNode = true)
        val sibling = Memory(title = "相近名称", folderPath = "项目备份")
        box.put(listOf(parent, child, sibling))
        val chunk = DocumentChunk(content = "保留正文").also { it.memory.target = child }
        store.boxFor(DocumentChunk::class.java).put(chunk)
        assertTrue(repository.renameFolder("项目", "新项目"))
        assertEquals("新项目/资料", box.get(child.id).folderPath)
        assertEquals("项目备份", box.get(sibling.id).folderPath)
        repository.deleteFolder("新项目")
        assertNull(box.get(parent.id).folderPath)
        assertNull(box.get(child.id).folderPath)
        assertEquals("项目备份", box.get(sibling.id).folderPath)
        assertEquals("保留正文", store.boxFor(DocumentChunk::class.java).get(chunk.id).content)
        assertEquals(3L, box.count())
    }
}
