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
