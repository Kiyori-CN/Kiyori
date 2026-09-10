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
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerSearchLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context: Context = mock()
    private val store = ViewModelStore()
    private lateinit var log: MockedStatic<Log>
    private val requests = mutableListOf<AITool>()
    private val responses = mutableListOf<CompletableDeferred<ToolResult>>()
    @Before fun setup() { Dispatchers.setMain(dispatcher); log = Mockito.mockStatic(Log::class.java); whenever(context.getString(R.string.file_manager_home)).thenReturn("Home") }
    @After fun cleanup() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain(); log.close() }
    private fun model() = FileManagerViewModel(context, "/storage/test", dispatcher) { tool ->
        if (tool.name == "list_files") ToolResult(tool.name, true, DirectoryListingData("/", listOf(
            DirectoryListingData.FileEntry("note.txt", false, 3, "rw", "2026-09-10"),
        ))) else {
            requests += tool
            val result = CompletableDeferred<ToolResult>(); responses += result
            withContext(NonCancellable) { result.await() }
        }
    }.also { store.put("manager", it); scheduler.runCurrent() }
    private fun found(path: String, directory: Boolean = false, limitations: List<String> = emptyList()) = ToolResult("find_files", true,
        FileSearchData("/storage/test", listOf(FileSearchEntry(path, directory, 8, 123)), 5, 2, limitations))

    @Test fun `search captures advanced settings and location without per result calls`() = runTest(dispatcher) {
        val model = model()
        model.searchForm = FileManagerSearchForm(recursive = true, content = "needle", sizePreset = FileSearchSizePreset.SMALL, caseSensitive = true)
        model.searchFiles("note"); scheduler.runCurrent()
        model.searchForm = FileManagerSearchForm(); model.navigateToPath("/later"); scheduler.runCurrent()
        val options = Json.decodeFromString<FileSearchOptions>(requests.single().parameters.single { it.name == "search_options" }.value)
        assertTrue(options.recursive); assertTrue(options.caseSensitive); assertEquals("needle", options.content)
        assertEquals("/storage/test", requests.single().parameters.single { it.name == "path" }.value)
        responses.single().complete(found("/storage/test/note.txt", limitations = listOf("limited"))); scheduler.runCurrent()
        assertEquals(8L, model.searchResults.single().size)
        assertEquals(listOf("limited"), model.searchLimitations)
        assertTrue(model.searchSummary.contains("扫描 5 项")); assertEquals(1, requests.size)
    }
    @Test fun `latest typed search result wins and cancellation cannot reopen results`() = runTest(dispatcher) {
        val model = model(); model.searchFiles("old"); scheduler.runCurrent(); model.searchFiles("new"); scheduler.runCurrent()
        responses[1].complete(found("/storage/test/new")); scheduler.runCurrent()
        responses[0].complete(found("/storage/test/old")); scheduler.runCurrent()
        assertEquals("new", model.searchResults.single().name)
        model.cancelSearch(); assertTrue(model.searchResults.isEmpty()); assertTrue(model.searchLimitations.isEmpty())
    }
    @Test fun `directory results enter directory while files locate their parent with filter`() = runTest(dispatcher) {
        val model = model(); model.searchFiles("folder"); scheduler.runCurrent()
        responses[0].complete(found("/storage/test/folder", true)); scheduler.runCurrent()
        model.activatePane(FileManagerPane.RIGHT); model.navigateToFileDirectory("/storage/test/folder"); scheduler.runCurrent()
        assertEquals(FileManagerPane.LEFT, model.activePane); assertEquals("/storage/test/folder", model.currentPath)
        model.searchFiles("note"); scheduler.runCurrent(); responses[1].complete(found("/storage/test/folder/note.txt")); scheduler.runCurrent()
        model.navigateToFileDirectory("/storage/test/folder/note.txt"); scheduler.runCurrent()
        assertEquals("note.txt", model.filterQuery)
        model.beginSearchDialog(); assertEquals("note.txt", model.searchDialogQuery)
    }
    @Test fun `invalid conditions never submit and basic remote search retains depth and case semantics`() = runTest(dispatcher) {
        val model = model(); model.searchForm = FileManagerSearchForm(nameMode = FileSearchNameMode.REGEX)
        model.searchFiles("["); scheduler.runCurrent(); assertTrue(requests.isEmpty())
        model.searchForm = FileManagerSearchForm(caseSensitive = true); model.navigateToPath("/", "repo:docs"); scheduler.runCurrent()
        model.searchFiles("note"); scheduler.runCurrent()
        assertEquals("false", requests.single().parameters.single { it.name == "case_insensitive" }.value)
        assertEquals("0", requests.single().parameters.single { it.name == "max_depth" }.value)
        assertFalse(requests.single().parameters.any { it.name == "search_mode" })
        responses.single().complete(ToolResult("find_files", true, FindFilesResultData("/", "note", emptyList(), "repo:docs"))); scheduler.runCurrent()
    }
    @Test fun `unsupported remote conditions are errors and are never silently ignored`() = runTest(dispatcher) {
        val model = model(); model.navigateToPath("/", "linux"); scheduler.runCurrent()
        model.searchForm = FileManagerSearchForm(content = "private")
        model.searchFiles("note"); scheduler.runCurrent()
        assertTrue(requests.isEmpty()); assertNotNull(model.searchError); assertFalse(model.isSearching)
    }
    @Test fun `search form reopens with prior conditions and unlisted result cannot navigate`() = runTest(dispatcher) {
        val model = model(); model.searchForm = FileManagerSearchForm(recursive = true)
        model.searchFiles("note"); scheduler.runCurrent(); responses.single().complete(found("/storage/test/note.txt")); scheduler.runCurrent()
        model.navigateToFileDirectory("/untrusted/path"); assertEquals("/storage/test", model.currentPath)
        model.editSearch(); assertTrue(model.showSearchDialog); assertFalse(model.showSearchResultsDialog)
        assertTrue(model.searchForm.recursive); assertEquals("note", model.searchDialogQuery)
    }
}
