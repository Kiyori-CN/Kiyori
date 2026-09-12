package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerHistoryStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileManagerHistoryStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val location = FileManagerLocation("/storage/test", null)
    private fun record(id: Int) = FileManagerSearchRecord("s$id", id.toLong(), "query$id", FileManagerSearchForm(recursive = true), location,
        listOf(FileItem("x.pdf", false, 42, fullPath = "/storage/test/x.pdf")), 1, "完成")

    @Test fun `search snapshots and advanced form survive recreation with retention and explicit truncation`() = runBlocking {
        val file = File(temp.root, "history.json")
        val store = FileManagerHistoryStore(file)
        repeat(22) { store.putSearch(record(it)) }
        store.putSearch(record(23).copy(results = List(1002) { FileItem("file$it", false) }, total = 1002))
        val restored = FileManagerHistoryStore(file); restored.load()
        assertEquals(20, restored.state.value.searches.size)
        assertEquals(1000, restored.state.value.searches.first().results.size)
        assertTrue(restored.state.value.searches.first().limitations.any { it.contains("1000") })
        assertTrue(restored.state.value.searches[1].form.recursive)
        restored.removeSearch("s23")
        restored.removeSearch(null)
        assertTrue(restored.state.value.searches.isEmpty())
    }
    @Test fun `interrupted writes become unknown and history reads never run tasks`() = runBlocking {
        val file = File(temp.root, "history.json")
        val store = FileManagerHistoryStore(file)
        store.putTask(FileManagerTaskRecord("t", 1, "移动", location, location, 2))
        val restored = FileManagerHistoryStore(file); restored.load()
        assertEquals("结果待确认", restored.state.value.tasks.single().status)
        assertNotNull(restored.state.value.tasks.single().finishedAt)
        restored.removeTask(null)
        assertTrue(restored.state.value.tasks.isEmpty())
    }
    @Test fun `concurrent task and search updates do not lose either history`() = runBlocking {
        val store = FileManagerHistoryStore(File(temp.root, "history.json"))
        coroutineScope {
            launch { repeat(10) { store.putSearch(record(it)) } }
            launch { repeat(110) { store.putTask(FileManagerTaskRecord("t$it", it.toLong(), "复制", location, location, 1, "完成", finishedAt = 5)) } }
        }
        assertEquals(10, store.state.value.searches.size)
        assertEquals(100, store.state.value.tasks.size)
        assertEquals("t109", store.state.value.tasks.first().id)
    }
    @Test fun `corrupt history is not overwritten by new writes`() = runBlocking {
        val file = File(temp.root, "history.json").apply { writeText("not-json") }
        val store = FileManagerHistoryStore(file)
        assertTrue(runCatching { store.putSearch(record(1)) }.isFailure)
        assertEquals("not-json", file.readText())
    }
    @Test fun `deleted searches cannot be resurrected by late completion`() = runBlocking {
        val store = FileManagerHistoryStore(File(temp.root, "history.json"))
        store.putSearch(record(1).copy(status = "搜索中"))
        store.removeSearch("s1")
        store.putSearch(record(1))
        assertTrue(store.state.value.searches.isEmpty())
        store.putSearch(record(3)); store.putSearch(record(2))
        assertEquals(listOf("s3", "s2"), store.state.value.searches.map { it.id })
    }
    @Test fun `task aggregate distinguishes partial canceled failed and unknown`() {
        fun result(outcome: FileManagerTransferOutcome) = FileManagerTransferItemResult("a", outcome)
        assertEquals("部分完成", fileManagerTaskStatus(listOf(result(FileManagerTransferOutcome.COMPLETED), result(FileManagerTransferOutcome.FAILED))))
        assertEquals("结果待确认", fileManagerTaskStatus(listOf(result(FileManagerTransferOutcome.UNKNOWN))))
        assertEquals("已取消", fileManagerTaskStatus(listOf(result(FileManagerTransferOutcome.NOT_STARTED))))
    }
}
