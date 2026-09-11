package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerSelectionLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val store = ViewModelStore()
    private val context: Context = mock()
    private lateinit var log: MockedStatic<Log>
    private var entries = listOf(entry("alpha"), entry("beta"), entry(".hidden"))
    private var reads = 0
    private var failRead = false

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        log = Mockito.mockStatic(Log::class.java)
        whenever(context.getString(R.string.file_manager_home)).thenReturn("Home")
    }
    @After fun cleanup() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain(); log.close() }
    private fun model() = FileManagerViewModel(context, "/storage/test", dispatcher) { tool ->
        reads++
        if (failRead) throw IllegalStateException("offline")
        ToolResult(tool.name, true, DirectoryListingData("/", entries))
    }.also { store.put("manager", it); scheduler.runCurrent() }

    @Test fun `clicking the last selected item restores opening and resets the swipe anchor`() = runTest(dispatcher) {
        val model = model()
        val alpha = model.files.single { it.name == "alpha" }
        val beta = model.files.single { it.name == "beta" }
        model.clickEntry(alpha)
        assertEquals("alpha", model.pendingOpen!!.name)
        model.finishOpen(model.pendingOpen!!.id)
        assertFalse(model.isMultiSelectMode)
        model.selectFile(alpha)
        model.clickEntry(beta)
        assertEquals(setOf("alpha", "beta"), model.selectedFiles.map { it.name }.toSet())
        assertNull(model.pendingOpen)
        model.clickEntry(alpha)
        model.clickEntry(beta)
        assertTrue(model.selectedFiles.isEmpty())
        assertFalse(model.isMultiSelectMode)
        model.clickEntry(beta)
        assertEquals("beta", model.pendingOpen!!.name)
        model.finishOpen(model.pendingOpen!!.id)
        model.selectFile(beta)
        assertEquals(listOf("beta"), model.selectedFiles.map { it.name })
        assertNull(model.pendingOpen)
        assertTrue(model.navigateBack())
        assertFalse(model.isMultiSelectMode)
        model.clickEntry(alpha)
        assertEquals("alpha", model.pendingOpen!!.name)
    }

    @Test fun `repeated swipe selects without toggling and fills mixed ranges in either order`() = runTest(dispatcher) {
        entries = listOf("a", "b", "c", "d", "e", "f").map { entry(it) }
        val model = model()
        for ((anchor, target) in listOf("b" to "e", "e" to "b")) {
            model.clearActiveSelection()
            fun item(name: String) = model.files.single { it.name == name }
            model.selectFile(item(anchor))
            model.selectFile(item(anchor))
            assertEquals(listOf(anchor), model.selectedFiles.map { it.name })
            model.clickEntry(item("c"))
            model.clickEntry(item("f"))
            model.selectFile(item(target))
            assertEquals(setOf("b", "c", "d", "e", "f"), model.selectedFiles.map { it.name }.toSet())
            assertEquals(5, model.selectedFiles.size)
            model.clickEntry(item("d"))
            model.selectFile(item(target))
            assertFalse(model.selectedFiles.any { it.name == "d" })
            model.selectFile(item(anchor))
            assertEquals(setOf("b", "c", "d", "e", "f"), model.selectedFiles.map { it.name }.toSet())
            assertEquals(5, model.selectedFiles.size)
        }
    }

    @Test fun `empty pane stays in open mode across switching and refresh while back clears other pane`() = runTest(dispatcher) {
        val model = model()
        val alpha = model.files.single { it.name == "alpha" }
        model.selectFile(alpha)
        model.clickEntry(alpha)
        model.activatePane(FileManagerPane.RIGHT)
        assertFalse(model.isMultiSelectMode)
        model.selectFile(model.files.single { it.name == "beta" })
        model.activatePane(FileManagerPane.LEFT)
        model.loadCurrentDirectory()
        scheduler.runCurrent()
        assertFalse(model.isMultiSelectMode)
        assertTrue(model.selectedFiles.isEmpty())
        assertTrue(model.navigateBack())
        assertFalse(model.selectionModeForPane(FileManagerPane.LEFT))
        assertFalse(model.selectionModeForPane(FileManagerPane.RIGHT))
        assertTrue(model.selectionForPane(FileManagerPane.RIGHT).isEmpty())
        assertEquals("/storage/test", model.currentPath)
    }

    @Test fun `clicking selection empty lets back clear filter immediately`() = runTest(dispatcher) {
        val model = model()
        val alpha = model.files.single { it.name == "alpha" }
        model.selectFile(alpha)
        model.clickEntry(alpha)
        model.setDirectoryFilter("beta")
        assertTrue(model.navigateBack())
        assertFalse(model.isMultiSelectMode)
        assertEquals("", model.filterQuery)
        model.selectFile(model.files.single { it.name == "beta" })
        assertEquals(listOf("beta"), model.selectedFiles.map { it.name })
    }

    @Test fun `each completed swipe range releases its anchor for the next independent pair`() = runTest(dispatcher) {
        entries = listOf("a", "b", "c", "d", "e", "f").map { entry(it) }
        val model = model()
        fun swipe(name: String) = model.selectFile(model.files.single { it.name == name })
        swipe("a")
        swipe("b")
        swipe("f")
        assertEquals(setOf("a", "b", "f"), model.selectedFiles.map { it.name }.toSet())
        swipe("e")
        assertEquals(setOf("a", "b", "e", "f"), model.selectedFiles.map { it.name }.toSet())
        // 已选项也可以成为新区间首项，但不能继续使用上一段起点跨过空缺。
        swipe("b")
        assertEquals(setOf("a", "b", "e", "f"), model.selectedFiles.map { it.name }.toSet())
        swipe("c")
        assertEquals(setOf("a", "b", "c", "e", "f"), model.selectedFiles.map { it.name }.toSet())
    }

    @Test fun `swipe range follows current visible order and never selects hidden or parent entries`() = runTest(dispatcher) {
        entries = listOf("a", "b", "c", ".hidden").map { entry(it) }
        val model = model()
        model.navigateToPath("/storage/test/child")
        scheduler.runCurrent()
        model.toggleHiddenFiles()
        model.selectFile(model.files.single { it.name == "a" })
        model.toggleSortDirection()
        scheduler.runCurrent()
        model.selectFile(model.files.single { it.name == "c" })
        assertEquals(setOf("a", "b", "c"), model.selectedFiles.map { it.name }.toSet())
        model.selectFile(FileItem("..", true))
        assertEquals(3, model.selectedFiles.size)
        model.clickEntry(FileItem("..", true))
        assertEquals("/storage/test", model.currentPath)
        assertFalse(model.isMultiSelectMode)
    }

    @Test fun `removed swipe anchor starts a new range and panes never share anchors`() = runTest(dispatcher) {
        val model = model()
        model.selectFile(model.files.single { it.name == ".hidden" })
        model.activatePane(FileManagerPane.RIGHT)
        model.selectFile(model.files.single { it.name == "beta" })
        assertEquals(listOf("beta"), model.selectedFiles.map { it.name })
        model.activatePane(FileManagerPane.LEFT)
        entries = listOf(entry("alpha"), entry("beta"))
        model.loadCurrentDirectory()
        scheduler.runCurrent()
        model.selectFile(model.files.single { it.name == "beta" })
        assertEquals(listOf("beta"), model.selectedFiles.map { it.name })
    }

    @Test fun `directory clicks select in mode and open after back`() = runTest(dispatcher) {
        entries = listOf(entry("alpha"), DirectoryListingData.FileEntry("folder", true, 0, "rw", ""))
        val model = model()
        model.selectFile(model.files.single { it.name == "alpha" })
        val folder = model.files.single { it.name == "folder" }
        model.clickEntry(folder)
        assertEquals("/storage/test", model.currentPath)
        assertEquals(setOf("alpha", "folder"), model.selectedFiles.map { it.name }.toSet())
        model.navigateBack()
        model.clickEntry(folder)
        assertEquals("/storage/test/folder", model.currentPath)
    }

    @Test fun `bottom menu preserves selections in both panes and captures current target`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        val leftSelection = model.selectedFiles.toList()
        model.activatePane(FileManagerPane.RIGHT)
        model.toggleSelection(model.files.first { it.name == "beta" })
        model.openActionMenu()
        assertTrue(model.showBottomActionMenu)
        assertEquals(FileManagerPane.RIGHT, model.contextMenuPane)
        assertEquals("beta", model.contextMenuFile!!.name)
        assertEquals(listOf("beta"), model.selectedFiles.map { it.name })
        assertEquals(leftSelection, model.selectionForPane(FileManagerPane.LEFT))
        model.showBottomActionMenu = false
        assertEquals(listOf("beta"), model.selectedFiles.map { it.name })
    }

    @Test fun `bottom menu without selection discards previous long press object`() = runTest(dispatcher) {
        val model = model()
        model.contextMenuFile = model.files.first()
        model.openActionMenu()
        assertTrue(model.showBottomActionMenu)
        assertNull(model.contextMenuFile)
        assertTrue(model.selectedFiles.isEmpty())
        model.beginContextTransfer(false)
        assertNull(model.transferDraft)
    }

    @Test fun `history navigation clears selection before source location changes`() = runTest(dispatcher) {
        val model = model()
        model.navigateToPath("/other")
        scheduler.runCurrent()
        model.selectAll()
        assertEquals(3, model.selectedFiles.size)
        model.navigateBackDirectory()
        assertTrue(model.selectedFiles.isEmpty())
        assertFalse(model.isMultiSelectMode)
        scheduler.runCurrent()
        model.selectAll()
        model.navigateForward()
        assertTrue(model.selectedFiles.isEmpty())
        assertFalse(model.isMultiSelectMode)
    }

    @Test fun `refresh drops deleted selection and replaces retained metadata`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        entries = listOf(entry("alpha", size = 99))
        model.loadCurrentDirectory()
        scheduler.runCurrent()
        assertEquals(listOf("alpha"), model.selectedFiles.map { it.name })
        assertEquals(99L, model.selectedFiles.single().size)
        entries = emptyList()
        model.loadCurrentDirectory()
        scheduler.runCurrent()
        assertFalse(model.isMultiSelectMode)
        assertTrue(model.files.isEmpty())
    }

    @Test fun `filter is pane local makes no IO and removes hidden batch candidates`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        val baseline = reads
        model.setDirectoryFilter("ALP")
        assertEquals(listOf("alpha"), model.files.map { it.name })
        assertEquals(listOf("alpha"), model.selectedFiles.map { it.name })
        model.activatePane(FileManagerPane.RIGHT)
        assertEquals("", model.filterQuery)
        assertEquals(3, model.files.size)
        model.activatePane(FileManagerPane.LEFT)
        assertEquals("ALP", model.filterQuery)
        model.setDirectoryFilter("")
        assertEquals(baseline, reads)
        assertEquals(1, model.selectedFiles.size)
    }

    @Test fun `hidden toggle and invert selection operate only on visible entries`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        model.toggleHiddenFiles()
        assertEquals(2, model.selectedFiles.size)
        model.invertSelection()
        assertTrue(model.selectedFiles.isEmpty())
        model.invertSelection()
        assertEquals(listOf("alpha", "beta"), model.selectedFiles.map { it.name })
        model.setDirectoryFilter("nothing")
        model.selectAll()
        assertFalse(model.isMultiSelectMode)
    }

    @Test fun `filter changes during load apply to the published snapshot and back clears filter`() = runTest(dispatcher) {
        val model = model()
        entries = listOf(entry("alpha2"), entry("beta2"))
        model.loadCurrentDirectory()
        model.setDirectoryFilter("alpha")
        scheduler.runCurrent()
        assertEquals(listOf("alpha2"), model.files.map { it.name })
        assertTrue(model.navigateBack())
        assertEquals("/storage/test", model.currentPath)
        assertEquals(2, model.files.size)
    }

    @Test fun `filtered scrolling cannot erase the unfiltered position or accept a stale projection`() = runTest(dispatcher) {
        val model = model()
        val location = FileManagerLocation(model.currentPath, null)
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition(2, 12))
        model.setDirectoryFilter("alpha")
        assertEquals(FileManagerScrollPosition(), model.scrollPosition(FileManagerPane.LEFT, location))
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition(0, 5))
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition(99, 99), filter = "")
        model.setDirectoryFilter("")
        assertEquals(FileManagerScrollPosition(2, 12), model.scrollPosition(FileManagerPane.LEFT, location))
    }

    @Test fun `both panes retain their own selection when focus switches`() = runTest(dispatcher) {
        val model = model()
        model.toggleSelection(model.files.single { it.name == "alpha" })
        model.activatePane(FileManagerPane.RIGHT)
        model.toggleSelection(model.files.single { it.name == "beta" })
        assertEquals(listOf("alpha"), model.selectionForPane(FileManagerPane.LEFT).map { it.name })
        assertEquals(listOf("beta"), model.selectionForPane(FileManagerPane.RIGHT).map { it.name })
        model.activatePane(FileManagerPane.LEFT)
        model.beginCopySelection()
        assertEquals(listOf("alpha"), model.clipboardFiles.map { it.name })
        assertEquals(listOf("beta"), model.selectionForPane(FileManagerPane.RIGHT).map { it.name })
    }

    @Test fun `failed refresh cannot leave selectable stale entries or a writable old selection`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        failRead = true
        model.loadCurrentDirectory()
        model.invertSelection()
        scheduler.runCurrent()
        assertTrue(model.selectedFiles.isEmpty())
        model.selectAll()
        model.beginCopySelection()
        assertTrue(model.clipboardFiles.isEmpty())
        assertNotNull(model.error)
    }

    @Test fun `sort UI chooses useful defaults and toggles direction without reversing directories`() = runTest(dispatcher) {
        entries = listOf(entry("item10", 10), entry("item2", 2))
        val model = model()
        assertEquals(listOf("item2", "item10"), model.files.map { it.name })
        model.selectSortMode(FileManagerSortMode.SIZE)
        scheduler.runCurrent()
        assertTrue(model.sortDescending)
        assertEquals(listOf("item10", "item2"), model.files.map { it.name })
        model.toggleSortDirection()
        scheduler.runCurrent()
        assertEquals(listOf("item2", "item10"), model.files.map { it.name })
    }

    private fun entry(name: String, size: Long = 1) = DirectoryListingData.FileEntry(name, false, size, "rw", "")

    @Test fun `clear active selection preserves the other pane while system back clears the session`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        model.activatePane(FileManagerPane.RIGHT)
        model.selectAll()
        model.clearActiveSelection()
        assertTrue(model.selectedFiles.isEmpty())
        assertEquals(3, model.selectionForPane(FileManagerPane.LEFT).size)
        assertTrue(model.navigateBack())
        assertTrue(model.selectionForPane(FileManagerPane.LEFT).isEmpty())
    }

    @Test fun `context single selection does not extend the previous swipe range or toggle an existing item`() = runTest(dispatcher) {
        val model = model()
        model.selectFile(model.files.single { it.name == ".hidden" })
        val beta = model.files.single { it.name == "beta" }
        model.addSingleSelection(beta)
        model.addSingleSelection(beta)
        assertEquals(listOf(".hidden", "beta"), model.selectedFiles.map { it.name })
    }

    @Test fun `selection resolves stale gesture objects against the latest directory snapshot`() = runTest(dispatcher) {
        val model = model()
        val stale = model.files.single { it.name == "alpha" }
        entries = listOf(entry("alpha", 99))
        model.loadCurrentDirectory()
        scheduler.runCurrent()
        model.selectFile(stale)
        assertEquals(99L, model.selectedFiles.single().size)
        model.clearActiveSelection()
        model.toggleSelection(stale)
        assertEquals(99L, model.selectedFiles.single().size)
        model.clearActiveSelection()
        model.addSingleSelection(stale)
        assertEquals(99L, model.selectedFiles.single().size)
    }

    @Test fun `single selection ignores vanished entries and the parent action`() = runTest(dispatcher) {
        val model = model()
        model.addSingleSelection(FileItem("gone", false))
        model.addSingleSelection(FileItem("..", true))
        assertTrue(model.selectedFiles.isEmpty())
        assertFalse(model.isMultiSelectMode)
    }
}
