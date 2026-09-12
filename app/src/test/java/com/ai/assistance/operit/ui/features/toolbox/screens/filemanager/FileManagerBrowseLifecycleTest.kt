package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerBrowseLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context: Context = mock()
    private val store = ViewModelStore()
    private lateinit var log: MockedStatic<Log>
    private val requests = mutableListOf<AITool>()
    @Before fun setup() { Dispatchers.setMain(dispatcher); log = Mockito.mockStatic(Log::class.java); whenever(context.getString(R.string.file_manager_home)).thenReturn("Home") }
    @After fun cleanup() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain(); log.close() }
    private fun listing() = DirectoryListingData("/", listOf(
        DirectoryListingData.FileEntry("a.pdf", false, 2, "rw", "1700000000"),
        DirectoryListingData.FileEntry("b.jpg", false, 8, "rw", "1700000100"),
        DirectoryListingData.FileEntry("folder", true, 0, "rw", "0"),
    ))
    private fun model(execute: suspend (AITool) -> ToolResult = { ToolResult(it.name, true, listing()) }) =
        FileManagerViewModel(context, "/root", dispatcher) { tool -> requests += tool; execute(tool) }
            .also { store.put("manager", it); scheduler.runCurrent() }

    @Test fun `filter commits only the captured pane without IO and removes invisible selection`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        val before = requests.size
        val draft = FileManagerFilterDraft(formats = "pdf", maximum = "3", unit = FileManagerSizeUnit.B)
        model.activatePane(FileManagerPane.RIGHT)
        model.applyDirectoryFilter(FileManagerPane.LEFT, FileManagerLocation("/root", null), draft)
        assertEquals(before, requests.size)
        assertEquals(listOf("a.pdf"), model.leftPaneState.files.map { it.name })
        assertEquals(3, model.rightPaneState.files.size)
        assertEquals(listOf("a.pdf"), model.selectionForPane(FileManagerPane.LEFT).map { it.name })
        model.activatePane(FileManagerPane.LEFT)
        model.refreshPane(); scheduler.runCurrent()
        assertEquals(draft, model.leftPaneState.filterDraft)
        model.navigateToPath("/elsewhere"); scheduler.runCurrent()
        assertFalse(model.leftPaneState.hasFilter)
        model.applyDirectoryFilter(FileManagerPane.LEFT, FileManagerLocation("/root", null), draft)
        assertFalse(model.leftPaneState.hasFilter)
    }
    @Test fun `sorting is pane local and swap transports selection scroll filters history and active content`() = runTest(dispatcher) {
        val model = model()
        model.navigateToPath("/left"); scheduler.runCurrent()
        model.applySort(FileManagerPane.LEFT, FileManagerSortMode.SIZE, true); scheduler.runCurrent()
        model.selectAll()
        model.applyDirectoryFilter(FileManagerPane.LEFT, FileManagerLocation("/left", null), FileManagerFilterDraft(formats = "jpg"))
        model.saveScrollPosition(FileManagerPane.LEFT, FileManagerLocation("/left", null), FileManagerScrollPosition(1, 12))
        model.activatePane(FileManagerPane.RIGHT); model.navigateToPath("/right", "linux"); scheduler.runCurrent()
        model.activatePane(FileManagerPane.LEFT)
        val left = model.leftPaneState; val right = model.rightPaneState
        model.swapPanes()
        assertEquals(FileManagerPane.RIGHT, model.activePane)
        assertEquals(left.path, model.rightPaneState.path)
        assertEquals(right.environment, model.leftPaneState.environment)
        assertEquals(left.backStack, model.rightPaneState.backStack)
        assertEquals(FileManagerSortMode.SIZE, model.rightPaneState.sortMode)
        assertEquals(FileManagerSortMode.NAME, model.leftPaneState.sortMode)
        assertEquals(left.filter, model.rightPaneState.filter)
        assertEquals(listOf("b.jpg"), model.selectionForPane(FileManagerPane.RIGHT).map { it.name })
        assertEquals(FileManagerScrollPosition(1, 12), model.scrollPosition(FileManagerPane.RIGHT, FileManagerLocation("/left", null)))
    }
    @Test fun `late noncancellable directory completion cannot cross swapped panes`() = runTest(dispatcher) {
        val late = CompletableDeferred<ToolResult>()
        var hold = false
        val model = model { tool -> if (hold) { hold = false; withContext(NonCancellable) { late.await() } } else ToolResult(tool.name, true, listing()) }
        model.navigateToPath("/left"); scheduler.runCurrent()
        hold = true; model.refreshPane(FileManagerPane.LEFT); scheduler.runCurrent()
        model.swapPanes(); scheduler.runCurrent()
        late.complete(ToolResult("list_files", true, DirectoryListingData("/left", listOf(DirectoryListingData.FileEntry("stale", false, 1, "", "")))))
        scheduler.runCurrent()
        assertEquals("/root", model.leftPaneState.path)
        assertEquals("/left", model.rightPaneState.path)
        assertFalse(model.leftPaneState.files.any { it.name == "stale" })
        assertFalse(model.rightPaneState.files.any { it.name == "stale" })
    }
    @Test fun `same-location swap rejects scroll saved by the old presentation`() = runTest(dispatcher) {
        val model = model()
        val location = FileManagerLocation("/root", null)
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition(1, 10))
        model.saveScrollPosition(FileManagerPane.RIGHT, location, FileManagerScrollPosition(2, 20))
        val before = model.leftPaneState.presentationVersion
        model.swapPanes()
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition(1, 10), "", before)
        assertEquals(FileManagerScrollPosition(2, 20), model.scrollPosition(FileManagerPane.LEFT, location))
    }
    @Test fun `search dialog captures originating pane and saved result is checked before navigation`() = runTest(dispatcher) {
        val model = model { tool -> if (tool.name == "find_files") ToolResult(tool.name, true, FileSearchData("/root", emptyList(), 0, 0, emptyList())) else ToolResult(tool.name, true, listing()) }
        model.beginSearchDialog(); model.searchDialogQuery = "a"
        model.activatePane(FileManagerPane.RIGHT); model.navigateToPath("/right"); scheduler.runCurrent()
        model.submitSearchDialog(); scheduler.runCurrent()
        assertEquals("/root", requests.last { it.name == "find_files" }.parameters.first { it.name == "path" }.value)
        model.openSearchRecord(FileManagerSearchRecord("old", 1, "gone", FileManagerSearchForm(), FileManagerLocation("/saved", null),
            listOf(FileItem("gone.pdf", false, fullPath = "/saved/gone.pdf")), 1, "完成"))
        val before = model.currentPath
        model.navigateToFileDirectory("/saved/gone.pdf"); scheduler.runCurrent()
        assertEquals(before, model.currentPath)
        assertTrue(model.searchError.orEmpty().contains("失效"))
        assertEquals("/saved", requests.last().parameters.first { it.name == "path" }.value)
    }
}
