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
class FileManagerActionLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context: Context = mock()
    private val store = ViewModelStore()
    private lateinit var log: MockedStatic<Log>
    private val requests = mutableListOf<AITool>()
    private var gate: CompletableDeferred<Unit>? = null
    private var readGate: CompletableDeferred<Unit>? = null
    private var failWrite = false
    private var failRead = false
    private var malformedWrite = false
    @Before fun setup() { whenever(context.filesDir).thenReturn(java.io.File("/private")); whenever(context.getExternalFilesDir(null)).thenReturn(java.io.File("/storage/test/app")); Dispatchers.setMain(dispatcher); log = Mockito.mockStatic(Log::class.java); whenever(context.getString(R.string.file_manager_home)).thenReturn("Home") }
    @After fun cleanup() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain(); log.close() }
    private fun model() = FileManagerViewModel(context, "/storage/test", dispatcher) { tool ->
        if (tool.name == "list_files") ToolResult(tool.name, true, DirectoryListingData("/", listOf(
            DirectoryListingData.FileEntry("note.txt", false, 3, "rw", "2026-09-10"),
            DirectoryListingData.FileEntry("second.txt", false, 5, "rw", "2026-09-10"),
        ))) else {
            requests += tool
            if (tool.name == "file_info") {
                readGate?.await()
                if (failRead) throw java.io.IOException("inspection failed")
                ToolResult(tool.name, true, FileInspectionData("/storage/test/note.txt", false, 3, 1, 0, 123, true, true, "fingerprint"))
            } else {
                gate?.await()
                if (failWrite) throw java.io.IOException("unknown")
                ToolResult(tool.name, true, if (malformedWrite) StringResultData("ok") else FileOperationData(tool.name, "android", "/", true, "done"))
            }
        }
    }.also { store.put("manager", it); scheduler.runCurrent(); it.contextMenuFile = it.files.single { f -> f.name == "note.txt" }; it.contextMenuPane = FileManagerPane.LEFT }

    @Test fun `context transfer uses selection only when pressed item belongs to it`() = runTest(dispatcher) {
        val model = model()
        model.selectAll()
        model.beginContextTransfer(false)
        assertEquals(2, model.transferDraft!!.files.size)
        model.dismissTransferDraft()
        model.clearActiveSelection()
        model.toggleSelection(model.files.single { it.name == "second.txt" })
        model.beginContextTransfer(true)
        assertEquals(listOf("note.txt"), model.transferDraft!!.files.map { it.name })
        assertTrue(model.transferDraft!!.move)
    }

    @Test fun `transfer captures source then lets user browse either pane`() = runTest(dispatcher) {
        val model = model(); model.beginContextTransfer(false)
        model.browseTransferDestination(); model.navigateToPath("/destination"); scheduler.runCurrent(); model.requestPaste()
        assertEquals("/storage/test", model.pendingCopy!!.source.path)
        assertEquals("/destination", model.pendingCopy!!.destination.path)
        assertFalse(model.pendingCopy!!.moveRequested)
        assertTrue(requests.isEmpty())
    }
    @Test fun `dual pane transfer freezes other destination before pane changes`() = runTest(dispatcher) {
        val model = model(); model.activatePane(FileManagerPane.RIGHT); model.navigateToPath("/target"); scheduler.runCurrent()
        model.beginContextTransfer(true)
        model.activatePane(FileManagerPane.RIGHT); model.navigateToPath("/later"); scheduler.runCurrent()
        model.useOtherTransferDestination()
        assertEquals("/target", model.pendingCopy!!.destination.path)
        assertTrue(model.pendingCopy!!.moveRequested)
    }
    @Test fun `move dispatches atomic tool once and clears only consumed cut items`() = runTest(dispatcher) {
        val model = model(); model.beginContextTransfer(true); model.browseTransferDestination()
        model.navigateToPath("/target"); scheduler.runCurrent(); model.requestPaste(); model.confirmPaste(); scheduler.runCurrent()
        val move = requests.single()
        assertEquals("move_file", move.name)
        assertEquals("move_no_replace", move.parameters.single { it.name == "move_mode" }.value)
        assertTrue(model.transferState.move); assertTrue(model.clipboardFiles.isEmpty())
    }
    @Test fun `cancelled destination dialog does not replace existing clipboard`() = runTest(dispatcher) {
        val model = model(); model.setClipboard(listOf(FileItem("existing", false)), false)
        model.beginContextTransfer(true); model.dismissTransferDraft()
        assertEquals("existing", model.clipboardFiles.single().name)
        assertFalse(model.isCutOperation)
    }
    @Test fun `unstructured move result is unknown and removed from cut clipboard`() = runTest(dispatcher) {
        val model = model(); model.beginContextTransfer(true); model.browseTransferDestination()
        model.navigateToPath("/target"); scheduler.runCurrent(); malformedWrite = true
        model.requestPaste(); model.confirmPaste(); scheduler.runCurrent()
        assertEquals(FileManagerTransferOutcome.UNKNOWN, model.transferState.results.single().outcome)
        assertTrue(model.clipboardFiles.isEmpty())
    }
    @Test fun `delete confirmation freezes fingerprint path and blocks duplicate or dismissal`() = runTest(dispatcher) {
        val model = model(); model.beginContextAction(FileManagerActionKind.DELETE); scheduler.runCurrent()
        model.navigateToPath("/other"); scheduler.runCurrent()
        gate = CompletableDeferred(); model.confirmContextAction(); model.confirmContextAction(); scheduler.runCurrent()
        assertTrue(model.isWriting); model.dismissAction(); assertNotNull(model.actionState)
        val delete = requests.single { it.name == "delete_file" }
        assertEquals("/storage/test/note.txt", delete.parameters.single { it.name == "path" }.value)
        assertEquals("fingerprint", delete.parameters.single { it.name == "fingerprint" }.value)
        gate!!.complete(Unit); scheduler.runCurrent(); assertTrue(model.actionState!!.completed)
    }
    @Test fun `unknown deletion cannot be submitted again`() = runTest(dispatcher) {
        val model = model(); model.beginContextAction(FileManagerActionKind.DELETE); scheduler.runCurrent()
        failWrite = true; model.confirmContextAction(); scheduler.runCurrent(); model.confirmContextAction(); scheduler.runCurrent()
        assertTrue(model.actionState!!.unknown); assertEquals(1, requests.count { it.name == "delete_file" })
    }
    @Test fun `closing property inspection suppresses late results`() = runTest(dispatcher) {
        val model = model(); readGate = CompletableDeferred(); model.beginContextAction(FileManagerActionKind.PROPERTIES); scheduler.runCurrent()
        model.dismissAction(); readGate!!.complete(Unit); scheduler.runCurrent(); assertNull(model.actionState)
    }
    @Test fun `compression rejects traversal before tool submission`() = runTest(dispatcher) {
        val model = model(); model.beginContextAction(FileManagerActionKind.ZIP); scheduler.runCurrent()
        model.updateActionName("../unsafe.zip"); model.confirmContextAction(); scheduler.runCurrent()
        assertFalse(requests.any { it.name == "zip_files" }); assertNotNull(model.actionState!!.error)
    }

    @Test fun `failed reinspection invalidates prior deletion confirmation`() = runTest(dispatcher) {
        val model = model(); model.beginContextAction(FileManagerActionKind.DELETE); scheduler.runCurrent()
        failRead = true; model.readActionInspection(false); scheduler.runCurrent()
        model.confirmContextAction(); scheduler.runCurrent()
        assertNull(model.actionState!!.inspection)
        assertNotNull(model.actionState!!.error)
        assertFalse(requests.any { it.name == "delete_file" })
    }

    @Test fun `unstructured zip success never opens sharing or permits retry`() = runTest(dispatcher) {
        val model = model(); model.beginContextAction(FileManagerActionKind.ZIP, shareAfter = true); scheduler.runCurrent()
        malformedWrite = true; model.confirmContextAction(); scheduler.runCurrent()
        assertFalse(model.actionState!!.completed); assertTrue(model.actionState!!.unknown)
        assertNotNull(model.actionState!!.error); assertNull(model.pendingShare)
        model.confirmContextAction(); scheduler.runCurrent()
        assertEquals(1, requests.count { it.name == "zip_files" })
    }

    @Test fun `batch deletion freezes all selected sources and records each result`() = runTest(dispatcher) {
        val model = model(); model.selectAll(); model.beginContextAction(FileManagerActionKind.DELETE); scheduler.runCurrent()
        assertEquals(2, model.actionState!!.inspections.size)
        model.activatePane(FileManagerPane.RIGHT); model.navigateToPath("/other"); scheduler.runCurrent()
        model.confirmContextAction(); scheduler.runCurrent()
        assertEquals(listOf("/storage/test/note.txt", "/storage/test/second.txt"), requests.filter { it.name == "delete_file" }.map { tool -> tool.parameters.single { it.name == "path" }.value })
        assertEquals(2, model.actionState!!.results.size)
        assertTrue(model.actionState!!.completed)
    }

    @Test fun `unknown batch deletion stops before the next source and cannot replay`() = runTest(dispatcher) {
        val model = model(); model.selectAll(); model.beginContextAction(FileManagerActionKind.DELETE); scheduler.runCurrent()
        failWrite = true; model.confirmContextAction(); scheduler.runCurrent()
        assertEquals(listOf(FileManagerTransferOutcome.UNKNOWN, FileManagerTransferOutcome.NOT_STARTED), model.actionState!!.results.map { it.outcome })
        model.readActionInspection(false); model.confirmContextAction(); scheduler.runCurrent()
        assertEquals(1, requests.count { it.name == "delete_file" })
    }

    @Test fun `batch compression sends every selected source in one atomic archive request`() = runTest(dispatcher) {
        val model = model(); model.selectAll(); model.beginContextAction(FileManagerActionKind.ZIP); scheduler.runCurrent()
        model.updateActionName("archive.zip"); model.confirmContextAction(); scheduler.runCurrent()
        val request = requests.single { it.name == "zip_files" }
        assertEquals("[\"/storage/test/note.txt\",\"/storage/test/second.txt\"]", request.parameters.single { it.name == "sources" }.value)
        assertTrue(model.actionState!!.completed)
    }

    @Test fun `sharing multiple files opens one compression confirmation`() = runTest(dispatcher) {
        val model = model(); model.selectAll(); model.shareContextItem(); scheduler.runCurrent()
        assertEquals(2, model.actionState!!.files.size)
        assertTrue(model.actionState!!.shareAfter)
        assertNull(model.pendingShare)
    }

    @Test fun `right context never reads same named selection from left pane`() = runTest(dispatcher) {
        val model = model(); model.selectAll()
        model.activatePane(FileManagerPane.RIGHT); model.navigateToPath("/right"); scheduler.runCurrent()
        model.contextMenuPane = FileManagerPane.RIGHT; model.contextMenuFile = model.files.first { it.name == "note.txt" }
        model.beginContextAction(FileManagerActionKind.DELETE); scheduler.runCurrent()
        assertEquals(1, model.actionState!!.files.size)
        assertEquals("/right", model.actionState!!.location.path)
    }
}
