package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.DisplayMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerLocation
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPane
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPaneState
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerSortMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.TabItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerBackAction
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerBackAction
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerParentPath
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileManagerViewModel(private val context: Context) : ViewModel() {
    private val initialStoragePath = Environment.getExternalStorageDirectory().absolutePath

    private var leftPane by mutableStateOf(FileManagerPaneState(initialStoragePath, null))
    private var rightPane by mutableStateOf(FileManagerPaneState(initialStoragePath, null))

    var activePane by mutableStateOf(FileManagerPane.LEFT)
        private set

    val leftPaneState: FileManagerPaneState
        get() = leftPane
    val rightPaneState: FileManagerPaneState
        get() = rightPane
    val initialPath: String
        get() = initialStoragePath

    val currentPath: String
        get() = paneState(activePane).path
    val currentEnvironment: String?
        get() = paneState(activePane).environment
    val files: List<FileItem>
        get() = paneState(activePane).files
    val isLoading: Boolean
        get() = paneState(activePane).isLoading
    val error: String?
        get() = paneState(activePane).error

    // 选择状态属于文件管理器会话，并由活动窗格的长按菜单消费。
    var selectedFile by mutableStateOf<FileItem?>(null)
    var selectedFiles = mutableStateListOf<FileItem>()
    var isMultiSelectMode by mutableStateOf(false)

    // 剪贴板状态
    var clipboardFiles = mutableStateListOf<FileItem>()
    var isCutOperation by mutableStateOf(false)
    var clipboardSourcePath by mutableStateOf<String?>(null)
    var clipboardSourceEnvironment by mutableStateOf<String?>(null)

    // 显示状态
    var itemSize by mutableStateOf(1f)
    val minItemSize = 0.5f
    val maxItemSize = 1.3f
    val itemSizeStep = 0.1f
    var displayMode by mutableStateOf(DisplayMode.TWO_COLUMNS)
    var showHiddenFiles by mutableStateOf(true)
    var sortMode by mutableStateOf(FileManagerSortMode.NAME)

    // 每个窗格的滚动位置按路径保存，切换目录后不会把另一窗格的位置覆盖。
    val leftScrollPositions = mutableStateMapOf<String, Int>()
    val rightScrollPositions = mutableStateMapOf<String, Int>()

    // 标签页状态保留既有外部行为，当前活动标签跟随活动窗格路径。
    var tabs = mutableStateListOf(TabItem(initialStoragePath, context.getString(R.string.file_manager_home), null))
    var activeTabIndex by mutableStateOf(0)

    // 上下文菜单状态
    var showBottomActionMenu by mutableStateOf(false)
    var contextMenuFile by mutableStateOf<FileItem?>(null)

    // 对话框状态
    var showNewFolderDialog by mutableStateOf(false)
    var newFolderName by mutableStateOf("")
    var showCompressDialog by mutableStateOf(false)
    var compressFileName by mutableStateOf("")

    // 搜索状态
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults = mutableStateListOf<FileItem>()
    var showSearchDialog by mutableStateOf(false)
    var searchDialogQuery by mutableStateOf("")
    var showSearchResultsDialog by mutableStateOf(false)
    var isCaseSensitive by mutableStateOf(false)
    var useWildcard by mutableStateOf(true)

    private val toolHandler by lazy { AIToolHandler.getInstance(context) }

    private fun paneState(pane: FileManagerPane): FileManagerPaneState =
        if (pane == FileManagerPane.LEFT) leftPane else rightPane

    private fun updatePane(
        pane: FileManagerPane,
        transform: (FileManagerPaneState) -> FileManagerPaneState,
    ) {
        if (pane == FileManagerPane.LEFT) {
            leftPane = transform(leftPane)
        } else {
            rightPane = transform(rightPane)
        }
    }

    private fun withEnvParams(
        base: List<ToolParameter>,
        environment: String? = currentEnvironment,
    ): List<ToolParameter> {
        if (environment.isNullOrBlank()) return base
        return base + ToolParameter("environment", environment)
    }

    fun saveScrollPosition(pane: FileManagerPane, path: String, index: Int) {
        val positions = if (pane == FileManagerPane.LEFT) leftScrollPositions else rightScrollPositions
        positions[path] = index
    }

    fun scrollPosition(pane: FileManagerPane, path: String): Int {
        val positions = if (pane == FileManagerPane.LEFT) leftScrollPositions else rightScrollPositions
        return positions[path] ?: 0
    }

    fun paneCanGoBack(pane: FileManagerPane = activePane): Boolean =
        paneState(pane).backStack.isNotEmpty()

    fun paneCanGoForward(pane: FileManagerPane = activePane): Boolean =
        paneState(pane).forwardStack.isNotEmpty()

    fun activatePane(pane: FileManagerPane) {
        if (activePane != pane) {
            activePane = pane
            selectedFile = null
            selectedFiles.clear()
            isMultiSelectMode = false
        }
    }

    fun toggleSelection(file: FileItem) {
        if (file.name == "..") return
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        selectedFile = null
        isMultiSelectMode = true
    }

    fun selectAll() {
        selectedFiles.clear()
        selectedFiles.addAll(files.filter { file -> file.name != ".." })
        isMultiSelectMode = true
    }

    fun toggleHiddenFiles() {
        showHiddenFiles = !showHiddenFiles
        loadPaneDirectory(FileManagerPane.LEFT)
        loadPaneDirectory(FileManagerPane.RIGHT)
    }

    fun cycleSortMode() {
        sortMode = when (sortMode) {
            FileManagerSortMode.NAME -> FileManagerSortMode.SIZE
            FileManagerSortMode.SIZE -> FileManagerSortMode.MODIFIED
            FileManagerSortMode.MODIFIED -> FileManagerSortMode.NAME
        }
        loadPaneDirectory(FileManagerPane.LEFT)
        loadPaneDirectory(FileManagerPane.RIGHT)
    }

    fun swapPanes() {
        val previousLeft = leftPane
        leftPane = rightPane
        rightPane = previousLeft
        activePane = if (activePane == FileManagerPane.LEFT) FileManagerPane.RIGHT else FileManagerPane.LEFT
        selectedFile = null
        selectedFiles.clear()
        isMultiSelectMode = false
    }

    // 加载任意一个窗格的目录。结果只写回发起请求时的 pane/path，避免切换窗格后旧请求覆盖新目录。
    fun loadPaneDirectory(
        pane: FileManagerPane,
        path: String = paneState(pane).path,
        environment: String? = paneState(pane).environment,
        postLoadError: String? = null,
    ) {
        updatePane(pane) { state -> state.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val listFilesTool =
                        AITool(
                            name = "list_files",
                            parameters = withEnvParams(
                                listOf(ToolParameter("path", path)),
                                environment,
                            ),
                        )
                    AppLogger.d("ToolboxFileManager", "execute list_files path=$path env=$environment")
                    val result = toolHandler.executeTool(listFilesTool)
                    AppLogger.d(
                        "ToolboxFileManager",
                        "result list_files success=${result.success} error=${result.error}",
                    )
                    withContext(Dispatchers.Main) {
                        val state = paneState(pane)
                        if (state.path != path || state.environment != environment) return@withContext
                        if (result.success) {
                            val directoryListing = result.result as DirectoryListingData
                            val fileList = directoryListing.entries.map { entry ->
                                FileItem(
                                    name = entry.name,
                                    isDirectory = entry.isDirectory,
                                    size = entry.size,
                                    lastModified = entry.lastModified.toLongOrNull() ?: 0,
                                )
                            }
                            val visibleFiles = fileList
                                .filter { file -> showHiddenFiles || !file.name.startsWith(".") }
                                .let { entries ->
                                    when (sortMode) {
                                        FileManagerSortMode.NAME -> entries.sortedWith(
                                            compareByDescending<FileItem> { it.isDirectory }
                                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                                        )
                                        FileManagerSortMode.SIZE -> entries.sortedWith(
                                            compareByDescending<FileItem> { it.isDirectory }
                                                .thenByDescending { it.size }
                                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                                        )
                                        FileManagerSortMode.MODIFIED -> entries.sortedWith(
                                            compareByDescending<FileItem> { it.isDirectory }
                                                .thenByDescending { it.lastModified }
                                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                                        )
                                    }
                                }
                            updatePane(pane) { current ->
                                current.copy(
                                    files = listOf(FileItem("..", true, 0, 0)) + visibleFiles,
                                    isLoading = false,
                                    error = postLoadError,
                                )
                            }
                        } else {
                            updatePane(pane) { current ->
                                current.copy(
                                    isLoading = false,
                                    error = result.error ?: context.getString(R.string.file_manager_operation_failed),
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileManagerViewModel", "Error loading directory", e)
                    withContext(Dispatchers.Main) {
                        updatePane(pane) { current ->
                            if (current.path != path || current.environment != environment) {
                                current
                            } else {
                                current.copy(
                                    isLoading = false,
                                    error = "Error: ${e.message}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadCurrentDirectory(
        path: String = currentPath,
        environment: String? = currentEnvironment,
    ) {
        loadPaneDirectory(activePane, path, environment)
    }

    private fun navigatePaneTo(
        pane: FileManagerPane,
        path: String,
        environment: String?,
        recordHistory: Boolean,
    ) {
        val current = paneState(pane)
        if (current.path == path && current.environment == environment) {
            loadPaneDirectory(pane, path, environment)
            return
        }
        val location = FileManagerLocation(current.path, current.environment)
        updatePane(pane) {
            it.copy(
                path = path,
                environment = environment,
                backStack = if (recordHistory) it.backStack + location else it.backStack,
                forwardStack = if (recordHistory) emptyList() else it.forwardStack,
                error = null,
            )
        }
        syncActiveTab(pane)
        loadPaneDirectory(pane, path, environment)
    }

    fun navigateToDirectory(dir: FileItem) {
        if (!dir.isDirectory) return
        if (dir.name == "..") {
            navigateUp()
            return
        }
        navigatePaneTo(
            pane = activePane,
            path = buildPath(currentPath, dir.name),
            environment = currentEnvironment,
            recordHistory = true,
        )
    }

    // 活动窗格到达手机存储初始目录后，向上按钮保持在边界，不越出文件管理器的初始状态。
    fun navigateUp(): Boolean {
        val state = paneState(activePane)
        val parentPath = fileManagerParentPath(state.path) ?: return false
        if (state.path == initialStoragePath && state.environment == null) return false
        navigatePaneTo(activePane, parentPath, state.environment, recordHistory = true)
        return true
    }

    fun navigateBack(): Boolean {
        val pane = activePane
        val state = paneState(pane)
        if (fileManagerBackAction(state, initialStoragePath) == FileManagerBackAction.EXIT) return false
        val previous = state.backStack.lastOrNull()
        if (previous == null) {
            return navigateUp()
        }
        val current = FileManagerLocation(state.path, state.environment)
        updatePane(pane) {
            it.copy(
                path = previous.path,
                environment = previous.environment,
                backStack = it.backStack.dropLast(1),
                forwardStack = it.forwardStack + current,
                error = null,
            )
        }
        syncActiveTab(pane)
        loadPaneDirectory(pane, previous.path, previous.environment)
        return true
    }

    fun navigateForward(): Boolean {
        val pane = activePane
        val state = paneState(pane)
        val next = state.forwardStack.lastOrNull() ?: return false
        val current = FileManagerLocation(state.path, state.environment)
        updatePane(pane) {
            it.copy(
                path = next.path,
                environment = next.environment,
                backStack = it.backStack + current,
                forwardStack = it.forwardStack.dropLast(1),
                error = null,
            )
        }
        syncActiveTab(pane)
        loadPaneDirectory(pane, next.path, next.environment)
        return true
    }

    fun navigateToPath(path: String, environment: String? = currentEnvironment) {
        if (path.isEmpty()) return
        val normalizedPath = when {
            path == "/" -> "/"
            path.endsWith("/") -> path.dropLast(1)
            else -> path
        }
        navigatePaneTo(activePane, normalizedPath, environment, recordHistory = true)
    }

    private fun syncActiveTab(pane: FileManagerPane) {
        if (pane != activePane || activeTabIndex !in tabs.indices) return
        val state = paneState(pane)
        val updatedTabs = tabs.toMutableList()
        updatedTabs[activeTabIndex] = updatedTabs[activeTabIndex].copy(
            path = state.path,
            environment = state.environment,
        )
        tabs.clear()
        tabs.addAll(updatedTabs)
    }

    fun addTab(
        path: String = initialStoragePath,
        title: String = context.getString(R.string.file_manager_new_tab),
    ) {
        tabs.add(TabItem(path, title, null))
        activeTabIndex = tabs.size - 1
        navigatePaneTo(activePane, path, null, recordHistory = true)
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1 || index !in tabs.indices) return
        tabs.removeAt(index)
        activeTabIndex = activeTabIndex.coerceAtMost(tabs.lastIndex)
        val tab = tabs[activeTabIndex]
        navigatePaneTo(activePane, tab.path, tab.environment, recordHistory = true)
    }

    fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        val tab = tabs[index]
        navigatePaneTo(activePane, tab.path, tab.environment, recordHistory = true)
    }

    fun createNewFolder(folderName: String) {
        val operationPane = activePane
        val operationPath = currentPath
        val operationEnvironment = currentEnvironment
        viewModelScope.launch {
            try {
                val fullPath = buildPath(operationPath, folderName)
                val createFolderTool =
                    AITool(
                        name = "make_directory",
                        parameters = withEnvParams(
                            listOf(ToolParameter("path", fullPath)),
                            operationEnvironment,
                        ),
                    )
                AppLogger.d("ToolboxFileManager", "execute make_directory path=$fullPath env=$operationEnvironment")
                val result = toolHandler.executeTool(createFolderTool)
                AppLogger.d(
                    "ToolboxFileManager",
                    "result make_directory success=${result.success} error=${result.error}",
                )
                if (result.success) {
                    loadPaneDirectory(operationPane, operationPath, operationEnvironment)
                } else {
                    setPaneError(operationPane, result.error ?: context.getString(R.string.file_manager_operation_failed))
                }
            } catch (e: Exception) {
                AppLogger.e("FileManagerViewModel", "Error creating folder", e)
                setPaneError(operationPane, "Error: ${e.message}")
            }
        }
    }

    fun searchFiles(query: String) {
        if (query.isBlank()) {
            isSearching = false
            searchResults.clear()
            return
        }
        val searchPane = activePane
        val searchPath = currentPath
        val searchEnvironment = currentEnvironment
        viewModelScope.launch {
            setPaneLoading(searchPane, true)
            try {
                val searchPattern = if (useWildcard) "*$query*" else query
                val searchTool =
                    AITool(
                        name = "find_files",
                        parameters = withEnvParams(
                            listOf(
                                ToolParameter("path", searchPath),
                                ToolParameter("pattern", searchPattern),
                                ToolParameter("case_sensitive", isCaseSensitive.toString()),
                            ),
                            searchEnvironment,
                        ),
                    )
                AppLogger.d(
                    "ToolboxFileManager",
                    "execute find_files path=$searchPath env=$searchEnvironment pattern=$searchPattern",
                )
                val result = withContext(Dispatchers.IO) { toolHandler.executeTool(searchTool) }
                AppLogger.d(
                    "ToolboxFileManager",
                    "result find_files success=${result.success} error=${result.error}",
                )
                if (result.success) {
                    val findResult = result.result as FindFilesResultData
                    val fileList = withContext(Dispatchers.IO) {
                        findResult.files.map { filePath ->
                            val fileName = filePath.substringAfterLast("/")
                            val isDir = try {
                                val fileInfoTool =
                                    AITool(
                                        name = "file_info",
                                        parameters = withEnvParams(
                                            listOf(ToolParameter("path", filePath)),
                                            searchEnvironment,
                                        ),
                                    )
                                val fileInfoResult = toolHandler.executeTool(fileInfoTool)
                                if (fileInfoResult.success) {
                                    (fileInfoResult.result as FileInfoData).fileType == "directory"
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                AppLogger.e("FileManagerViewModel", "Error reading search result metadata", e)
                                false
                            }
                            FileItem(fileName, isDir, fullPath = filePath)
                        }
                    }
                    isSearching = true
                    searchResults.clear()
                    searchResults.addAll(fileList)
                    showSearchResultsDialog = true
                } else {
                    setPaneError(searchPane, result.error ?: context.getString(R.string.file_manager_search_failed))
                }
            } catch (e: Exception) {
                AppLogger.e("FileManagerViewModel", "Error searching files", e)
                setPaneError(searchPane, context.getString(R.string.file_manager_search_error, e.message ?: "Unknown"))
            } finally {
                setPaneLoading(searchPane, false)
            }
        }
    }

    fun navigateToFileDirectory(filePath: String) {
        val directoryPath = filePath.substringBeforeLast("/")
        if (directoryPath.isEmpty()) return
        navigatePaneTo(activePane, directoryPath, currentEnvironment, recordHistory = true)
        showSearchResultsDialog = false
        isSearching = false
    }

    fun setClipboard(files: List<FileItem>, isCut: Boolean) {
        clipboardFiles.clear()
        clipboardFiles.addAll(files)
        clipboardSourcePath = currentPath
        clipboardSourceEnvironment = currentEnvironment
        isCutOperation = isCut
    }

    fun pasteFiles() {
        val sourcePath = clipboardSourcePath ?: return
        if (clipboardFiles.isEmpty()) return
        val operationPane = activePane
        val targetPath = currentPath
        val targetEnvironment = currentEnvironment
        val sourceEnvironment = clipboardSourceEnvironment
        viewModelScope.launch {
            setPaneLoading(operationPane, true)
            var operationError: String? = null
            try {
                withContext(Dispatchers.IO) {
                    clipboardFiles.forEach { file ->
                        val fullSourcePath = buildPath(sourcePath, file.name)
                        val fullTargetPath = buildPath(targetPath, file.name)
                        val copyParams = listOf(
                            ToolParameter("source", fullSourcePath),
                            ToolParameter("destination", fullTargetPath),
                        ).let { base ->
                            if (sourceEnvironment != targetEnvironment) {
                                base +
                                    ToolParameter("source_environment", sourceEnvironment ?: "android") +
                                    ToolParameter("dest_environment", targetEnvironment ?: "android")
                            } else {
                                withEnvParams(base, targetEnvironment)
                            }
                        }
                        val copyTool =
                            AITool(
                                name = "copy_file",
                                parameters = copyParams,
                            )
                        AppLogger.d(
                            "ToolboxFileManager",
                            "execute copy_file src=$fullSourcePath dst=$fullTargetPath srcEnv=$sourceEnvironment dstEnv=$targetEnvironment",
                        )
                        val result = toolHandler.executeTool(copyTool)
                        AppLogger.d(
                            "ToolboxFileManager",
                            "result copy_file success=${result.success} error=${result.error}",
                        )
                        if (result.success && isCutOperation) {
                            val deleteTool =
                                AITool(
                                    name = "delete_file",
                                    parameters = withEnvParams(
                                        listOf(ToolParameter("path", fullSourcePath)),
                                        sourceEnvironment,
                                    ),
                                )
                            val deleteResult = toolHandler.executeTool(deleteTool)
                            AppLogger.d(
                                "ToolboxFileManager",
                                "result delete_file success=${deleteResult.success} error=${deleteResult.error}",
                            )
                        }
                        if (!result.success) {
                            operationError = result.error ?: context.getString(R.string.file_manager_operation_failed)
                        }
                    }
                }
                loadPaneDirectory(operationPane, targetPath, targetEnvironment, operationError)
            } catch (e: Exception) {
                AppLogger.e("FileManagerViewModel", "Error performing file operation", e)
                setPaneError(operationPane, "Error: ${e.message}")
            }
        }
    }

    private fun setPaneLoading(pane: FileManagerPane, isLoading: Boolean) {
        updatePane(pane) { it.copy(isLoading = isLoading, error = if (isLoading) null else it.error) }
    }

    private fun setPaneError(pane: FileManagerPane, message: String) {
        updatePane(pane) { it.copy(error = message, isLoading = false) }
    }

    private fun buildPath(parentPath: String, childName: String): String =
        if (parentPath == "/") "/$childName" else "$parentPath/$childName"

    init {
        loadPaneDirectory(FileManagerPane.LEFT)
        loadPaneDirectory(FileManagerPane.RIGHT)
    }
}
