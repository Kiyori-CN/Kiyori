package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerLocation
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPane
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerScrollPosition
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerSortMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerDirectoryLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(scheduler, "main")
    private val directoryDispatcher = StandardTestDispatcher(scheduler, "directory")
    private val context: Context = mock()
    private val store = ViewModelStore()
    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        logMock = Mockito.mockStatic(Log::class.java)
        whenever(context.getString(R.string.file_manager_home)).thenReturn("Home")
        whenever(context.getString(R.string.file_manager_operation_failed)).thenReturn("Operation failed")
    }

    @After
    fun tearDown() {
        store.clear()
        scheduler.runCurrent()
        Dispatchers.resetMain()
        logMock.close()
    }

    @Test
    fun `same path refresh cannot publish an older noncancellable result`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory(ignoreCancellationAt = setOf(0))
        val model = createModel(directory)
        scheduler.runCurrent()
        model.loadPaneDirectory(FileManagerPane.LEFT)
        scheduler.runCurrent()

        directory.requests[2].response.complete(listing("fresh"))
        directory.requests[1].response.complete(listing("right"))
        scheduler.runCurrent()
        directory.requests[0].response.complete(listing("stale"))
        scheduler.runCurrent()

        assertEquals(listOf("..", "fresh"), model.leftPaneState.files.map { it.name })
        assertEquals(listOf("..", "right"), model.rightPaneState.files.map { it.name })
        assertFalse(model.leftPaneState.isLoading)
        assertNull(model.leftPaneState.error)
    }

    @Test
    fun `refresh cancels only its pane and keeps the other pending request`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory()
        val model = createModel(directory)
        scheduler.runCurrent()
        model.loadPaneDirectory(FileManagerPane.LEFT)
        scheduler.runCurrent()

        assertTrue(directory.requests[0].cancelled)
        assertFalse(directory.requests[1].cancelled)
        assertTrue(model.leftPaneState.isLoading)
        assertTrue(model.rightPaneState.isLoading)
        directory.requests[1].response.complete(listing("right"))
        directory.requests[2].response.complete(listing("left"))
        scheduler.runCurrent()
        assertEquals("right", model.rightPaneState.files.last().name)
        assertEquals("left", model.leftPaneState.files.last().name)
    }

    @Test
    fun `navigation clears old entries and late previous location cannot overwrite returned location`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory(ignoreCancellationAt = setOf(2))
        val model = createModel(directory)
        scheduler.runCurrent()
        directory.requests[0].response.complete(listing("initial"))
        directory.requests[1].response.complete(listing())
        scheduler.runCurrent()

        model.navigateToPath("/storage/other")
        assertTrue(model.leftPaneState.files.isEmpty())
        scheduler.runCurrent()
        model.navigateBackDirectory()
        scheduler.runCurrent()
        directory.requests[3].response.complete(listing("returned"))
        scheduler.runCurrent()
        directory.requests[2].response.complete(listing("other"))
        scheduler.runCurrent()

        assertEquals(INITIAL_PATH, model.leftPaneState.path)
        assertEquals(listOf("..", "returned"), model.leftPaneState.files.map { it.name })
    }

    @Test
    fun `late success cannot replace a current failure with apparent success`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory(ignoreCancellationAt = setOf(0))
        val model = createModel(directory)
        scheduler.runCurrent()
        model.loadPaneDirectory(FileManagerPane.LEFT)
        scheduler.runCurrent()
        directory.requests[2].response.complete(failure("Permission denied"))
        directory.requests[1].response.complete(listing())
        scheduler.runCurrent()
        directory.requests[0].response.complete(listing("stale"))
        scheduler.runCurrent()

        assertEquals("Permission denied", model.leftPaneState.error)
        assertTrue(model.leftPaneState.files.isEmpty())
        assertFalse(model.leftPaneState.isLoading)
    }

    @Test
    fun `current exception is visible and store cleanup cancels both pending directory reads`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory()
        val model = createModel(directory)
        scheduler.runCurrent()
        directory.requests[0].response.completeExceptionally(IllegalStateException("Directory unavailable"))
        scheduler.runCurrent()
        assertEquals("Error: Directory unavailable", model.leftPaneState.error)
        assertFalse(model.leftPaneState.isLoading)

        model.loadPaneDirectory(FileManagerPane.LEFT)
        scheduler.runCurrent()
        store.clear()
        scheduler.runCurrent()

        assertTrue(directory.requests[1].cancelled)
        assertTrue(directory.requests[2].cancelled)
    }

    @Test
    fun `directory work retains tool parameters filtering order and timestamps on its dispatcher`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory()
        val model = createModel(directory)
        scheduler.runCurrent()
        model.showHiddenFiles = false
        model.sortMode = FileManagerSortMode.SIZE
        model.navigateToPath("/", "repo:documents")
        scheduler.runCurrent()
        val request = directory.requests[2]
        request.response.complete(
            ToolResult(
                "list_files", true,
                DirectoryListingData(
                    path = "/",
                    entries = listOf(
                        entry("small", size = 1),
                        entry(".hidden", size = 99),
                        entry("folder", isDirectory = true),
                        entry("large", size = 50, lastModified = "1700000000"),
                    ),
                ),
            ),
        )
        directory.requests[1].response.complete(listing())
        scheduler.runCurrent()

        assertSame(directoryDispatcher, request.dispatcher)
        assertEquals("list_files", request.tool.name)
        assertEquals(mapOf("path" to "/", "environment" to "repo:documents"), request.tool.parameters.associate { it.name to it.value })
        assertEquals(listOf("..", "folder", "large", "small"), model.leftPaneState.files.map { it.name })
        val large = model.leftPaneState.files.single { it.name == "large" }
        assertEquals(1_700_000_000_000L, large.lastModified)
        assertEquals("1700000000", large.lastModifiedLabel)
    }

    @Test
    fun `scroll positions isolate panes and environments and reject a departed location write`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory()
        val model = createModel(directory)
        scheduler.runCurrent()
        directory.requests[0].response.complete(listing("left"))
        directory.requests[1].response.complete(listing("right"))
        scheduler.runCurrent()
        val local = FileManagerLocation(INITIAL_PATH, null)
        val remote = FileManagerLocation(INITIAL_PATH, "repo:documents")
        model.saveScrollPosition(FileManagerPane.LEFT, local, FileManagerScrollPosition(8, 12))
        model.saveScrollPosition(FileManagerPane.RIGHT, local, FileManagerScrollPosition(3, 9))
        model.navigateToPath(INITIAL_PATH, remote.environment)
        scheduler.runCurrent()
        directory.requests[2].response.complete(listing("remote"))
        scheduler.runCurrent()
        model.saveScrollPosition(FileManagerPane.LEFT, remote, FileManagerScrollPosition(2, 6))
        model.saveScrollPosition(FileManagerPane.LEFT, local, FileManagerScrollPosition(99, 99))

        assertEquals(FileManagerScrollPosition(8, 12), model.scrollPosition(FileManagerPane.LEFT, local))
        assertEquals(FileManagerScrollPosition(3, 9), model.scrollPosition(FileManagerPane.RIGHT, local))
        assertEquals(FileManagerScrollPosition(2, 6), model.scrollPosition(FileManagerPane.LEFT, remote))
        assertEquals(FileManagerScrollPosition(), model.scrollPosition(FileManagerPane.RIGHT, remote))
    }

    @Test
    fun `loading and error layouts cannot erase a successful directory scroll position`() = runTest(mainDispatcher) {
        val directory = ControlledDirectory()
        val model = createModel(directory)
        scheduler.runCurrent()
        directory.requests[0].response.complete(listing("left"))
        directory.requests[1].response.complete(listing())
        scheduler.runCurrent()
        val location = FileManagerLocation(INITIAL_PATH, null)
        val recorded = FileManagerScrollPosition(12, 37)
        model.saveScrollPosition(FileManagerPane.LEFT, location, recorded)

        model.loadPaneDirectory(FileManagerPane.LEFT)
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition())
        assertEquals(recorded, model.scrollPosition(FileManagerPane.LEFT, location))
        scheduler.runCurrent()
        directory.requests[2].response.complete(failure("Permission denied"))
        scheduler.runCurrent()
        model.saveScrollPosition(FileManagerPane.LEFT, location, FileManagerScrollPosition())
        assertEquals(recorded, model.scrollPosition(FileManagerPane.LEFT, location))
    }

    private fun createModel(directory: ControlledDirectory): FileManagerViewModel =
        FileManagerViewModel(context, INITIAL_PATH, directoryDispatcher, directory::execute).also {
            store.put("file-manager", it)
        }

    private class ControlledDirectory(private val ignoreCancellationAt: Set<Int> = emptySet()) {
        val requests = mutableListOf<Request>()

        suspend fun execute(tool: AITool): ToolResult {
            val index = requests.size
            val request = Request(tool, currentCoroutineContext()[ContinuationInterceptor])
            requests += request
            return try {
                if (index in ignoreCancellationAt) {
                    withContext(NonCancellable) { request.response.await() }
                } else {
                    request.response.await()
                }
            } catch (error: CancellationException) {
                request.cancelled = true
                throw error
            }
        }
    }

    private class Request(val tool: AITool, val dispatcher: ContinuationInterceptor?) {
        val response = CompletableDeferred<ToolResult>()
        var cancelled = false
    }

    companion object {
        private const val INITIAL_PATH = "/storage/test"

        private fun entry(name: String, isDirectory: Boolean = false, size: Long = 0, lastModified: String = "") =
            DirectoryListingData.FileEntry(name, isDirectory, size, "rw", lastModified)

        private fun listing(vararg names: String) = ToolResult(
            "list_files", true, DirectoryListingData(INITIAL_PATH, names.map { entry(it) }),
        )

        private fun failure(message: String) = ToolResult("list_files", false, StringResultData(""), message)
    }
}
