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
class FileManagerContentLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val store = ViewModelStore()
    private val context: Context = mock()
    private lateinit var log: MockedStatic<Log>
    private val calls = mutableListOf<AITool>()
    private var readGate: CompletableDeferred<Unit>? = null
    private var saveGate: CompletableDeferred<Unit>? = null
    private var failSave = false
    private var failRead = false
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        log = Mockito.mockStatic(Log::class.java)
        whenever(context.getString(R.string.file_manager_home)).thenReturn("Home")
    }
    @After fun cleanup() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain(); log.close() }
    private fun model() = FileManagerViewModel(context, "/storage/test", dispatcher) { tool ->
        calls += tool
        when (tool.name) {
            "list_files" -> ToolResult(tool.name, true, DirectoryListingData("/storage/test", listOf(
                DirectoryListingData.FileEntry("note.txt", false, 10, "rw-r--r--", "2026-09-10"),
                DirectoryListingData.FileEntry("movie.mp4", false, 10, "rw-r--r--", "2026-09-10"),
            )))
            "read_file_full" -> {
                readGate?.await()
                if (failRead) ToolResult(tool.name, false, StringResultData(""), "read error")
                else ToolResult(tool.name, true, FileContentData("/storage/test/note.txt", "original", 8))
            }
            "create_file" -> {
                saveGate?.await()
                if (failSave) throw java.io.IOException("lost result")
                ToolResult(tool.name, true, FileOperationData("create", "android", "/copy", true, "done"))
            }
            else -> error(tool.name)
        }
    }.also { store.put("manager", it); scheduler.runCurrent() }
    private fun openText(model: FileManagerViewModel) { model.openEntry(model.files.first { it.name == "note.txt" }); scheduler.runCurrent() }

    @Test fun `normal media open creates one immutable request without selecting`() = runTest(dispatcher) {
        val model = model()
        val file = model.files.first { it.name == "movie.mp4" }
        model.openEntry(file)
        val request = model.pendingOpen!!
        model.openEntry(file)
        assertSame(request, model.pendingOpen)
        assertEquals(FileManagerOpenKind.MEDIA, request.kind)
        assertTrue(model.selectedFiles.isEmpty())
        model.navigateToPath("/other")
        assertEquals("/storage/test/movie.mp4", request.path)
        model.finishOpen(request.id)
        assertNull(model.pendingOpen)
    }
    @Test fun `text uses bounded read and closing cancels late publication`() = runTest(dispatcher) {
        val model = model()
        readGate = CompletableDeferred()
        openText(model)
        assertTrue(model.textDocument!!.loading)
        model.closeTextDocument()
        readGate!!.complete(Unit)
        scheduler.runCurrent()
        assertNull(model.textDocument)
        assertTrue(calls.any { it.parameters.any { p -> p.name == "read_mode" && p.value == "bounded_utf8" } })
    }
    @Test fun `save copy freezes original location and prevents duplicate submission`() = runTest(dispatcher) {
        val model = model(); openText(model)
        model.updateTextDocument("edited")
        saveGate = CompletableDeferred()
        model.saveTextCopy("copy.txt"); model.saveTextCopy("other.txt")
        scheduler.runCurrent()
        assertTrue(model.isWriting)
        model.navigateToPath("/other")
        model.closeTextDocument()
        assertNotNull(model.textDocument)
        saveGate!!.complete(Unit); scheduler.runCurrent()
        val writes = calls.filter { it.name == "create_file" }
        assertEquals(1, writes.size)
        assertEquals("/storage/test/copy.txt", writes.single().parameters.first { it.name == "path" }.value)
        assertEquals("no_replace_text", writes.single().parameters.first { it.name == "create_mode" }.value)
        assertFalse(model.textDocument!!.dirty)
        assertFalse(model.isWriting)
    }
    @Test fun `original name and traversal never write`() = runTest(dispatcher) {
        val model = model(); openText(model)
        model.saveTextCopy("note.txt"); model.saveTextCopy("../bad.txt"); scheduler.runCurrent()
        assertFalse(calls.any { it.name == "create_file" })
        assertNotNull(model.textDocument!!.error)
    }
    @Test fun `unknown save retains draft and blocks retry`() = runTest(dispatcher) {
        val model = model(); openText(model); model.updateTextDocument("draft")
        failSave = true
        model.saveTextCopy("copy.txt"); scheduler.runCurrent()
        assertTrue(model.textDocument!!.unknown)
        assertEquals("draft", model.textDocument!!.content)
        model.saveTextCopy("copy2.txt"); scheduler.runCurrent()
        assertEquals(1, calls.count { it.name == "create_file" })
    }
    @Test fun `failed read cannot save an empty replacement`() = runTest(dispatcher) {
        val model = model(); failRead = true; openText(model)
        model.updateTextDocument("fake"); model.saveTextCopy("copy.txt"); scheduler.runCurrent()
        assertFalse(model.textDocument!!.readable)
        assertFalse(calls.any { it.name == "create_file" })
    }
    @Test fun `content routing handles uppercase media TypeScript CSV and extensionless text`() {
        fun kind(name: String) = fileManagerOpenKind(FileItem(name, false))
        assertEquals(FileManagerOpenKind.MEDIA, kind("MOVIE.MKV"))
        listOf("app.ts", "data.csv", "README", ".gitignore").forEach { assertEquals(FileManagerOpenKind.TEXT, kind(it)) }
        assertEquals(FileManagerOpenKind.SYSTEM, kind("file.zip"))
    }
    @Test fun `virtual locations reject content opening without dispatching a local file`() = runTest(dispatcher) {
        val model = model()
        model.navigateToPath("/", "linux"); scheduler.runCurrent()
        openText(model)
        assertNull(model.textDocument)
        assertNull(model.pendingOpen)
        assertNotNull(model.openError)
        assertFalse(calls.any { it.name == "read_file_full" })
    }
}
