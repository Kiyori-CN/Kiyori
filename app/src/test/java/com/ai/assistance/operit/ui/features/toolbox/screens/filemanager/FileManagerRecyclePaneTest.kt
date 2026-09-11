package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.defaultTool.standard.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerRecyclePaneTest {
    @get:Rule val folder = TemporaryFolder()
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val context: Context = mock()
    private val store = ViewModelStore()
    private lateinit var log: MockedStatic<Log>
    private val requests = mutableListOf<AITool>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher); log = Mockito.mockStatic(Log::class.java)
        whenever(context.filesDir).thenReturn(folder.newFolder("internal"))
        whenever(context.getExternalFilesDir(null)).thenReturn(folder.newFolder("external"))
        whenever(context.getString(R.string.file_manager_home)).thenReturn("Home")
    }
    @After fun cleanup() { store.clear(); scheduler.runCurrent(); Dispatchers.resetMain(); log.close() }

    private fun model(): FileManagerViewModel = FileManagerViewModel(context, "/storage/test", dispatcher) { tool ->
        requests += tool
        ToolResult(tool.name, true, DirectoryListingData("/", emptyList()))
    }.also { store.put("manager", it); scheduler.runCurrent() }

    private fun seed() {
        fileRecycleRoots(context).forEachIndexed { index, root ->
            val parent = folder.newFolder("source$index").toPath()
            val source = Files.writeString(parent.resolve("same.txt"), "content $index")
            LocalFileRecycleBin.recycle(source, root, inspectManagedTree(source).fingerprint) { from, to -> Files.move(from, to) }
        }
    }

    @Test fun `both storage roots share one pane with unique selection identities`() = runTest(dispatcher) {
        seed(); val model = model(); model.openRecycleBin(); scheduler.runCurrent()
        assertEquals("/回收站", model.currentPath)
        assertEquals(2, model.files.size)
        assertEquals(listOf("same.txt", "same.txt"), model.files.map { it.displayName })
        assertEquals(2, model.files.map { it.name }.distinct().size)
        assertEquals(2, model.files.map { it.recycledOriginalPath }.distinct().size)
        model.toggleSelection(model.files.first())
        assertEquals(1, model.selectedFiles.size)
        model.mirrorActivePaneToOther(); scheduler.runCurrent()
        assertTrue(model.selectionForPane(FileManagerPane.RIGHT).isEmpty())
        assertEquals(2, model.rightPaneState.files.size)
        assertFalse(model.canCreateHere)
        model.beginCreateEntry(); assertFalse(model.showNewEntryDialog)
        model.setClipboard(listOf(FileItem("x", false)), false); model.requestPaste()
        assertNull(model.pendingCopy)
    }

    @Test fun `empty recycle bin is an empty successful location and back preserves the other pane`() = runTest(dispatcher) {
        val model = model(); model.openRecycleBin(); scheduler.runCurrent()
        assertTrue(model.files.isEmpty()); assertNull(model.error)
        assertFalse(model.canNavigateUp(FileManagerPane.LEFT))
        model.navigateBackDirectory(); scheduler.runCurrent()
        assertEquals("/storage/test", model.currentPath)
        assertEquals("/storage/test", model.rightPaneState.path)
        assertEquals(FileManagerBackAction.INITIAL_STORAGE, fileManagerBackAction(FileManagerPaneState("/回收站", "recycle"), "/storage/test"))
    }

    @Test fun `removing storage detaches both panes histories and its clipboard`() = runTest(dispatcher) {
        val model = model()
        model.navigateToPath("/", "repo:documents"); scheduler.runCurrent()
        model.setClipboard(listOf(FileItem("note", false)), false)
        model.mirrorActivePaneToOther(); scheduler.runCurrent()
        model.detachStorageEnvironment("repo:documents"); scheduler.runCurrent()
        assertNull(model.leftPaneState.environment); assertNull(model.rightPaneState.environment)
        assertTrue(model.clipboardFiles.isEmpty())
        assertTrue(model.leftPaneState.backStack.none { it.environment == "repo:documents" })
        assertTrue(model.rightPaneState.backStack.none { it.environment == "repo:documents" })
    }
}
