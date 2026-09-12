package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.defaultTool.standard.FileCopyErrorCode
import com.ai.assistance.operit.data.model.AITool
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
class FileManagerWorkLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context: Context = mock()
    private val store = ViewModelStore()
    private lateinit var log: MockedStatic<Log>
    private val requests = mutableListOf<AITool>()
    private val responses = mutableListOf<CompletableDeferred<ToolResult>>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        log = Mockito.mockStatic(Log::class.java)
        whenever(context.getString(R.string.file_manager_home)).thenReturn("Home")
    }

    @After fun cleanup() {
        responses.forEach { it.complete(ToolResult("cancelled", false, StringResultData(""))) }
        store.clear()
        scheduler.runCurrent()
        Dispatchers.resetMain()
        log.close()
    }

    private fun model(ignoreCancellation: Boolean = false, listedNames: List<String> = emptyList()): FileManagerViewModel =
        FileManagerViewModel(context, "/storage/test", dispatcher) { tool ->
            if (tool.name == "list_files") {
                ToolResult(tool.name, true, DirectoryListingData("/", listedNames.map { DirectoryListingData.FileEntry(it, false, 0, "rw", "") }))
            } else {
                requests += tool
                val response = CompletableDeferred<ToolResult>()
                responses += response
                if (ignoreCancellation) withContext(NonCancellable) { response.await() } else response.await()
            }
        }.also { store.put("manager", it); scheduler.runCurrent() }

    private fun success(name: String) = ToolResult(name, true, FileOperationData(name, path = "/", successful = true, details = "ok"))
    private fun found(path: String) = ToolResult("find_files", true, FindFilesResultData("/", "*", listOf(path)))

    @Test fun `invalid names never submit filesystem work and valid unicode is preserved`() = runTest(dispatcher) {
        val model = model()
        listOf("", "  ", ".", "..", "../escape", "a/b", "a\\b", "a\u0000b", "a\nb").forEach {
            model.createNewFile(it)
            scheduler.runCurrent()
            assertNotNull(model.creationError)
        }
        assertTrue(requests.isEmpty())
        model.createNewFile(" 报告 📄.txt ")
        scheduler.runCurrent()
        assertEquals("/storage/test/ 报告 📄.txt ", requests.single().parameters.single { it.name == "path" }.value)
        responses.single().complete(success("create_file"))
        scheduler.runCurrent()
        assertNull(model.creationError)
    }

    @Test fun `creation freezes destination and duplicate taps cannot submit twice`() = runTest(dispatcher) {
        val model = model()
        model.beginCreateEntry()
        model.navigateToPath("/elsewhere", "linux")
        model.createNewFolder("new")
        model.createNewFolder("other")
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertEquals("/storage/test/new", requests.single().parameters.single { it.name == "path" }.value)
        assertFalse(requests.single().parameters.any { it.name == "environment" })
        responses.single().complete(success("make_directory"))
        scheduler.runCurrent()
        assertEquals("/elsewhere", model.currentPath)
        assertEquals("linux", model.currentEnvironment)
        assertFalse(model.showNewEntryDialog)
        assertFalse(model.isCreating)
    }

    @Test fun `creation failure keeps dialog and user input for correction`() = runTest(dispatcher) {
        val model = model()
        model.beginCreateEntry()
        model.newEntryName = "report.txt"
        model.createNewFile(model.newEntryName)
        scheduler.runCurrent()
        responses.single().complete(ToolResult("create_file", false, StringResultData(""), "Already exists"))
        scheduler.runCurrent()
        assertEquals("report.txt", model.newEntryName)
        assertEquals("Already exists", model.creationError)
        assertTrue(model.showNewEntryDialog)
        assertFalse(model.isCreating)
    }

    @Test fun `empty file request selects the strict create route`() = runTest(dispatcher) {
        val model = model()
        model.beginCreateEntry()
        model.createNewFile("empty.txt")
        scheduler.runCurrent()
        assertEquals("no_replace", requests.single().parameters.single { it.name == "create_mode" }.value)
        assertFalse(requests.single().parameters.any { it.name == "new" })
        responses.single().complete(success("create_file"))
        scheduler.runCurrent()
    }

    @Test fun `unknown creation result retains input and forbids resubmission until dismissed`() = runTest(dispatcher) {
        val model = model()
        model.beginCreateEntry()
        model.newEntryName = "unknown"
        model.createNewFile("unknown")
        scheduler.runCurrent()
        responses.single().completeExceptionally(IllegalStateException("result lost"))
        scheduler.runCurrent()
        assertTrue(model.creationUnknown)
        assertEquals("unknown", model.newEntryName)
        model.createNewFile("again")
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        model.dismissCreateEntry()
        assertFalse(model.showNewEntryDialog)
        model.beginCreateEntry()
        assertFalse(model.creationUnknown)
    }

    @Test fun `creation result survives owner cleanup without duplicate or false cancellation`() = runTest(dispatcher) {
        val model = model()
        model.beginCreateEntry()
        model.createNewFolder("created")
        scheduler.runCurrent()
        store.clear()
        scheduler.runCurrent()
        assertTrue(model.isCreating)
        responses.single().complete(success("make_directory"))
        scheduler.runCurrent()
        assertFalse(model.isCreating)
        assertFalse(model.creationUnknown)
        assertFalse(model.showNewEntryDialog)
    }

    @Test fun `new search rejects late noncancellable result and never issues per-item metadata calls`() = runTest(dispatcher) {
        val model = model(ignoreCancellation = true)
        model.searchFiles("old")
        scheduler.runCurrent()
        model.searchFiles("new")
        scheduler.runCurrent()
        responses[1].complete(found("/storage/test/new"))
        scheduler.runCurrent()
        responses[0].complete(found("/storage/test/old"))
        scheduler.runCurrent()
        assertEquals(listOf("new"), model.searchResults.map { it.name })
        assertFalse(model.isSearching)
        assertEquals(listOf("find_files", "find_files"), requests.map { it.name })
        assertNull(model.searchError)
    }

    @Test fun `cancelled search cannot reopen result dialog`() = runTest(dispatcher) {
        val model = model(ignoreCancellation = true)
        model.searchFiles("query")
        scheduler.runCurrent()
        model.cancelSearch()
        responses.single().complete(found("/old"))
        scheduler.runCurrent()
        assertFalse(model.isSearching)
        assertFalse(model.showSearchResultsDialog)
        assertTrue(model.searchResults.isEmpty())
        assertFalse(model.leftPaneState.isLoading)
    }

    @Test fun `search result navigation retains original pane and environment including root files`() = runTest(dispatcher) {
        val model = model()
        model.navigateToPath("/", "repo:documents")
        scheduler.runCurrent()
        model.searchFiles("query")
        scheduler.runCurrent()
        responses.single().complete(found("/result.txt"))
        scheduler.runCurrent()
        model.activatePane(FileManagerPane.RIGHT)
        model.navigateToFileDirectory("/result.txt")
        scheduler.runCurrent()
        assertEquals(FileManagerPane.LEFT, model.activePane)
        assertEquals("/", model.currentPath)
        assertEquals("repo:documents", model.currentEnvironment)
        assertFalse(model.showSearchResultsDialog)
    }

    @Test fun `search failure has its own error and does not replace directory contents`() = runTest(dispatcher) {
        val model = model()
        val before = model.leftPaneState
        model.searchFiles("query")
        scheduler.runCurrent()
        responses.single().complete(ToolResult("find_files", false, StringResultData(""), "Denied"))
        scheduler.runCurrent()
        assertNotNull(model.searchError)
        assertEquals(before, model.leftPaneState)
        assertFalse(model.isSearching)
    }
    @Test fun `failed copy never deletes source and records failure independently of directory state`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("report", false)), isCut = false)
        model.navigateToPath("/destination")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        assertFalse(model.leftPaneState.isLoading)
        responses.single().complete(ToolResult("copy_file", false, FileOperationData("copy", path = "/", successful = false, details = "No space"), "No space"))
        scheduler.runCurrent()
        assertEquals(listOf("copy_file"), requests.map { it.name })
        assertEquals(FileManagerTransferOutcome.FAILED, model.transferState.results.single().outcome)
        assertEquals("No space", model.transferState.results.single().message)
        assertEquals(1, model.clipboardFiles.size)
        assertFalse(model.transferState.running)
    }

    @Test fun `cut requests use atomic move and failure retains clipboard without copy or delete`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("folder", true)), isCut = true)
        model.navigateToPath("/destination")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        val request = requests.single()
        assertEquals("move_file", request.name)
        assertEquals("move_no_replace", request.parameters.single { it.name == "move_mode" }.value)
        responses.single().complete(ToolResult("move_file", false,
            com.ai.assistance.operit.core.tools.FileOperationData("move", path = "/folder", successful = false,
                details = "Cross filesystem", errorCode = com.ai.assistance.operit.core.tools.defaultTool.standard.FileCopyErrorCode.UNSUPPORTED), "Cross filesystem"))
        scheduler.runCurrent()
        assertEquals(listOf("move_file"), requests.map { it.name })
        assertEquals(FileManagerTransferOutcome.FAILED, model.transferState.results.single().outcome)
        assertEquals(1, model.clipboardFiles.size)
        assertFalse(model.transferState.running)
    }

    @Test fun `transfer captures clipboard and cut mode while rejecting duplicate submission`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("original", false)), isCut = false)
        model.navigateToPath("/destination")
        scheduler.runCurrent()
        model.pasteFiles()
        model.pasteFiles()
        scheduler.runCurrent()
        model.setClipboard(listOf(FileItem("new clipboard", false)), isCut = true)
        model.navigateToPath("/later")
        scheduler.runCurrent()
        responses.single().complete(success("copy_file"))
        scheduler.runCurrent()
        assertEquals(listOf("copy_file"), requests.map { it.name })
        assertEquals("/storage/test/original", requests.single().parameters.single { it.name == "source" }.value)
        assertEquals("/destination/original", requests.single().parameters.single { it.name == "destination" }.value)
        assertEquals("/later", model.currentPath)
        assertEquals("new clipboard", model.clipboardFiles.single().name)
        assertEquals(FileManagerTransferOutcome.COMPLETED, model.transferState.results.single().outcome)
    }

    @Test fun `same location and directory into descendant are rejected before tool dispatch`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("report", false)), isCut = true)
        model.pasteFiles()
        scheduler.runCurrent()
        assertTrue(requests.isEmpty())
        assertTrue(model.transferState.results.isEmpty())
        assertTrue(model.openError!!.contains("无需移动"))
        model.setClipboard(listOf(FileItem("folder", true)), isCut = false)
        model.navigateToPath("/storage/test/folder/child")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        assertTrue(requests.isEmpty())
        assertTrue(model.transferState.results.isEmpty())
        assertTrue(model.openError!!.contains("子目录"))
    }



    private fun conflict(stagingPath: String? = null) = ToolResult("copy_file", false,
        FileOperationData("copy", path = "/source", successful = false, details = "exists",
            errorCode = FileCopyErrorCode.CONFLICT, stagingPath = stagingPath), "exists")

    @Test fun `confirmation freezes batch source destination and mode before navigation changes`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a.txt", false)), false)
        model.navigateToPath("/target", "android")
        scheduler.runCurrent()
        model.requestPaste()
        model.setClipboard(listOf(FileItem("replacement", true)), true)
        model.navigateToPath("/later", "linux")
        scheduler.runCurrent()
        model.confirmPaste()
        model.confirmPaste()
        scheduler.runCurrent()
        val params = requests.single().parameters.associate { it.name to it.value }
        assertEquals("/storage/test/a.txt", params["source"])
        assertEquals("/target/a.txt", params["destination"])
        assertEquals("android", params["source_environment"])
        assertEquals("android", params["dest_environment"])
        assertEquals("no_replace", params["copy_mode"])
        responses.single().complete(success("copy_file"))
        scheduler.runCurrent()
        assertEquals("/later", model.currentPath)
        assertEquals("replacement", model.clipboardFiles.single().name)
        assertNull(model.pendingCopy)
    }

    @Test fun `stop waits for current result then records later items as not started`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false), FileItem("b", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        model.stopTransferAfterCurrent()
        assertTrue(model.transferState.running)
        responses.single().complete(success("copy_file"))
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertEquals(listOf(FileManagerTransferOutcome.COMPLETED, FileManagerTransferOutcome.NOT_STARTED),
            model.transferState.results.map { it.outcome })
        assertFalse(model.transferState.running)
    }

    @Test fun `conflict waits then renamed copy uses frozen item after clipboard replacement`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", true)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        responses[0].complete(conflict())
        scheduler.runCurrent()
        assertTrue(model.copyConflict!!.directory)
        assertTrue(model.transferState.running)
        model.setClipboard(listOf(FileItem("other", false)), false)
        model.resolveCopyConflict("../bad")
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        model.resolveCopyConflict("new name")
        scheduler.runCurrent()
        assertEquals("/target/new name", requests[1].parameters.single { it.name == "destination" }.value)
        assertEquals("true", requests[1].parameters.single { it.name == "recursive" }.value)
        responses[1].complete(success("copy_file"))
        scheduler.runCurrent()
        assertNull(model.copyConflict)
        assertEquals("a", model.transferState.results.single().name)
        assertEquals("/target/new name", model.transferState.results.single().destination)
        assertEquals("other", model.clipboardFiles.single().name)
    }

    @Test fun `skip conflict continues next item without reissuing first copy`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false), FileItem("b", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        responses[0].complete(conflict())
        scheduler.runCurrent()
        model.resolveCopyConflict(null)
        scheduler.runCurrent()
        assertEquals("/storage/test/b", requests[1].parameters.single { it.name == "source" }.value)
        responses[1].complete(success("copy_file"))
        scheduler.runCurrent()
        assertEquals(listOf(FileManagerTransferOutcome.SKIPPED, FileManagerTransferOutcome.COMPLETED),
            model.transferState.results.map { it.outcome })
    }

    @Test fun `stop while waiting for conflict never retries and releases dialog`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        responses.single().complete(conflict())
        scheduler.runCurrent()
        model.stopTransferAfterCurrent()
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertNull(model.copyConflict)
        assertFalse(model.transferState.running)
        assertEquals(FileManagerTransferOutcome.NOT_STARTED, model.transferState.results.single().outcome)
    }

    @Test fun `transport exception is unknown and is never automatically retried`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        responses.single().completeExceptionally(java.io.IOException("lost result"))
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertEquals(FileManagerTransferOutcome.UNKNOWN, model.transferState.results.single().outcome)
        assertNull(model.copyConflict)
        assertFalse(model.transferState.running)
    }

    @Test fun `view model clearing waits for current side effect and does not start next`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false), FileItem("b", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        store.clear()
        responses.single().complete(success("copy_file"))
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertEquals(FileManagerTransferOutcome.COMPLETED, model.transferState.results.first().outcome)
        assertEquals(FileManagerTransferOutcome.NOT_STARTED, model.transferState.results.last().outcome)
        assertFalse(model.transferState.running)
    }

    @Test fun `same directory copy offers rename without dispatching source onto itself`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a.txt", false)), false)
        model.pasteFiles()
        scheduler.runCurrent()
        assertTrue(requests.isEmpty())
        assertNotNull(model.copyConflict)
        model.resolveCopyConflict("a copy.txt")
        scheduler.runCurrent()
        assertEquals("/storage/test/a copy.txt", requests.single().parameters.single { it.name == "destination" }.value)
        responses.single().complete(success("copy_file"))
        scheduler.runCurrent()
        assertEquals(FileManagerTransferOutcome.COMPLETED, model.transferState.results.single().outcome)
    }

    @Test fun `conflict with retained staging is a visible failure not an automatic resubmission`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        responses.single().complete(conflict("/target/.kiyori-copy-leftover"))
        scheduler.runCurrent()
        assertNull(model.copyConflict)
        assertEquals(FileManagerTransferOutcome.FAILED, model.transferState.results.single().outcome)
        assertEquals("/target/.kiyori-copy-leftover", model.transferState.results.single().stagingPath)
    }

    @Test fun `copy name suggestions preserve dot files and final extension`() {
        assertEquals("report (副本).txt", fileManagerCopyName("report.txt", false))
        assertEquals(".gitignore (副本)", fileManagerCopyName(".gitignore", false))
        assertEquals("archive.tar (副本 2).gz", fileManagerCopyName("archive.tar.gz", false, 2))
        assertEquals("folder.ext (副本)", fileManagerCopyName("folder.ext", true))
    }


    private fun prepareRename(model: FileManagerViewModel, name: String = "old.txt") {
        model.contextMenuFile = FileItem(name, false)
        model.contextMenuPane = FileManagerPane.LEFT
        model.beginRenameContextItem()
        model.updateRenameName("new.txt")
    }

    @Test fun `rename freezes original location and rejects duplicate confirmation`() = runTest(dispatcher) {
        val model = model()
        prepareRename(model)
        model.navigateToPath("/later", "linux")
        scheduler.runCurrent()
        model.confirmRename()
        model.confirmRename()
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertEquals("move_file", requests.single().name)
        val params = requests.single().parameters.associate { it.name to it.value }
        assertEquals("/storage/test/old.txt", params["source"])
        assertEquals("/storage/test/new.txt", params["destination"])
        assertEquals("rename_no_replace", params["move_mode"])
        assertFalse(params.containsKey("environment"))
        responses.single().complete(success("move_file"))
        scheduler.runCurrent()
        assertNull(model.renameState.request)
        assertEquals("/later", model.currentPath)
        assertEquals("linux", model.currentEnvironment)
    }

    @Test fun `rename invalid names and same name cannot submit`() = runTest(dispatcher) {
        val model = model()
        prepareRename(model)
        listOf("", "../escape", "old.txt").forEach {
            model.updateRenameName(it)
            model.confirmRename()
            scheduler.runCurrent()
            assertNotNull(model.renameState.error)
        }
        assertTrue(requests.isEmpty())
    }

    @Test fun `rename conflict keeps typed name and original request for correction`() = runTest(dispatcher) {
        val model = model()
        prepareRename(model)
        model.confirmRename()
        scheduler.runCurrent()
        model.updateRenameName("should not change")
        model.dismissRename()
        responses.single().complete(ToolResult("move_file", false, StringResultData(""), "Already exists"))
        scheduler.runCurrent()
        assertEquals("new.txt", model.renameState.newName)
        assertEquals("old.txt", model.renameState.request!!.name)
        assertEquals("Already exists", model.renameState.error)
        assertFalse(model.renameState.running)
        model.updateRenameName("corrected.txt")
        assertNull(model.renameState.error)
    }

    @Test fun `unknown rename result disables resubmission until user checks directory`() = runTest(dispatcher) {
        val model = model()
        prepareRename(model)
        model.confirmRename()
        scheduler.runCurrent()
        responses.single().completeExceptionally(java.io.IOException("lost result"))
        scheduler.runCurrent()
        assertTrue(model.renameState.unknown)
        model.updateRenameName("retry")
        model.confirmRename()
        scheduler.runCurrent()
        assertEquals(1, requests.size)
        assertEquals("new.txt", model.renameState.newName)
        model.dismissRename()
        assertNull(model.renameState.request)
    }

    @Test fun `copy and create cannot start while rename is submitted`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("copied", false)), false)
        prepareRename(model)
        model.confirmRename()
        scheduler.runCurrent()
        model.pasteFiles()
        model.createNewFile("created")
        scheduler.runCurrent()
        assertEquals(listOf("move_file"), requests.map { it.name })
        responses.single().complete(success("move_file"))
        scheduler.runCurrent()
        assertFalse(model.isWriting)
    }

    @Test fun `successful rename removes obsolete selected name in both matching panes`() = runTest(dispatcher) {
        val model = model(listedNames = listOf("old.txt"))
        model.toggleSelection(FileItem("old.txt", false))
        model.activatePane(FileManagerPane.RIGHT)
        model.toggleSelection(FileItem("old.txt", false))
        assertEquals(1, model.selectedFiles.size)
        prepareRename(model)
        model.confirmRename()
        scheduler.runCurrent()
        responses.single().complete(success("move_file"))
        scheduler.runCurrent()
        assertTrue(model.selectedFiles.isEmpty())
        model.activatePane(FileManagerPane.LEFT)
        assertTrue(model.selectedFiles.isEmpty())
    }

    @Test fun `illegal argument thrown after submission is unknown rather than a preflight rejection`() = runTest(dispatcher) {
        val model = model()
        model.setClipboard(listOf(FileItem("a", false)), false)
        model.navigateToPath("/target")
        scheduler.runCurrent()
        model.pasteFiles()
        scheduler.runCurrent()
        responses.single().completeExceptionally(IllegalArgumentException("notification failed after write"))
        scheduler.runCurrent()
        assertEquals(FileManagerTransferOutcome.UNKNOWN, model.transferState.results.single().outcome)
    }
}
