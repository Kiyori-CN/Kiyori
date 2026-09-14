package com.ai.assistance.operit.util.vector

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VectorIndexPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savedIndexCanBeReopenedAndQueried() {
        val file = temporary.root.resolve("memory.idx")
        val index = VectorIndexManager<IndexItem<Long, Long>, Long>(2, 4, file)
        index.addItem(IndexItem(1L, floatArrayOf(1f, 0f), 1L, 1L))
        index.addItem(IndexItem(2L, floatArrayOf(0f, 1f), 1L, 2L))
        index.save()
        index.close()
        val reopened = VectorIndexManager<IndexItem<Long, Long>, Long>(2, 4, file)
        assertEquals(1L, reopened.findNearest(floatArrayOf(1f, 0f), 1).single().value)
        assertEquals(2, reopened.size())
        reopened.close()
        assertEquals(listOf("memory.idx"), temporary.root.list()?.toList())
    }

    @Test fun corruptIndexFailsWithoutDeletingEvidenceOrPretendingItIsEmpty() {
        val file = temporary.newFile("bad.idx")
        file.writeText("corrupt")
        assertThrows(Exception::class.java) { VectorIndexManager<IndexItem<Long, Long>, Long>(2, 4, file) }
        assertEquals("corrupt", file.readText())
    }

    @Test fun replacingIndexPublishesTheWholeNewSnapshot() {
        val file = temporary.root.resolve("memory.idx")
        val index = VectorIndexManager<IndexItem<Long, Long>, Long>(2, 4, file)
        index.addItem(IndexItem(1L, floatArrayOf(1f, 0f), 1L, 1L))
        index.save()
        index.addItem(IndexItem(2L, floatArrayOf(0f, 1f), 1L, 2L))
        index.save()
        index.close()
        val reopened = VectorIndexManager<IndexItem<Long, Long>, Long>(2, 4, file)
        assertEquals(2, reopened.size())
        reopened.close()
        assertTrue(temporary.root.listFiles().orEmpty().none { it.extension == "tmp" })
    }
}
