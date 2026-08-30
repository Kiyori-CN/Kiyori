package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileContextMenu
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerBottomBar
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerDualPane
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerStorageDrawer
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerStorageEntry
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerTopBar
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.SearchDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.SearchResultsDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.defaultFileManagerStorageEntries
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPane
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.window.KiyoriStatusBarAppearanceOverride
import kotlinx.coroutines.launch
import NewFolderDialog

private const val FILE_MANAGER_TAG = "ToolboxFileManager"

@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KiyoriStatusBarAppearanceOverride(darkIcons = false)
    val context = LocalContext.current
    val viewModel = remember { FileManagerViewModel(context) }
    val toolHandler = AIToolHandler.getInstance(context)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showStorageDrawer by remember { mutableStateOf(false) }
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())

    // 页面 Back 只在弹层 owner 之后取得优先级；目录层级耗尽才交还 Shell/Router。
    BackHandler {
        if (!viewModel.navigateBack()) {
            onBack()
        }
    }

    LaunchedEffect(showStorageDrawer) {
        if (showStorageDrawer) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) showStorageDrawer = false
    }
    LaunchedEffect(viewModel.leftPaneState.path) {
        leftListState.scrollToItem(viewModel.scrollPosition(FileManagerPane.LEFT, viewModel.leftPaneState.path))
    }
    LaunchedEffect(viewModel.rightPaneState.path) {
        rightListState.scrollToItem(viewModel.scrollPosition(FileManagerPane.RIGHT, viewModel.rightPaneState.path))
    }
    LaunchedEffect(leftListState.firstVisibleItemIndex, viewModel.leftPaneState.path) {
        viewModel.saveScrollPosition(
            FileManagerPane.LEFT,
            viewModel.leftPaneState.path,
            leftListState.firstVisibleItemIndex,
        )
    }
    LaunchedEffect(rightListState.firstVisibleItemIndex, viewModel.rightPaneState.path) {
        viewModel.saveScrollPosition(
            FileManagerPane.RIGHT,
            viewModel.rightPaneState.path,
            rightListState.firstVisibleItemIndex,
        )
    }

    var pendingBookmarkUri by remember { mutableStateOf<Uri?>(null) }
    var bookmarkName by remember { mutableStateOf("") }
    var bookmarkNameError by remember { mutableStateOf<String?>(null) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }

    fun queryRepoBookmarkName(uri: Uri): String {
        fun normalizeName(raw: String): String = raw.trim()
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\s+"), "_")
            .ifBlank { "repo" }

        val providerLabel = runCatching {
            val authority = uri.authority ?: return@runCatching null
            val provider = context.packageManager.resolveContentProvider(authority, 0)
            provider?.applicationInfo?.loadLabel(context.packageManager)?.toString()?.trim()
        }.getOrNull()
        return normalizeName(providerLabel?.takeIf { it.isNotBlank() } ?: uri.authority ?: "repo")
    }

    val addBookmarkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            pendingBookmarkUri = uri
            bookmarkName = queryRepoBookmarkName(uri)
            bookmarkNameError = null
            showBookmarkDialog = true
        } catch (e: Exception) {
            AppLogger.e(FILE_MANAGER_TAG, "持久化 SAF 书签权限失败", e)
            Toast.makeText(context, R.string.file_manager_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    val workspacePath = context.filesDir.resolve("workspace").absolutePath
    val storageEntries = remember(safBookmarks, workspacePath) {
        defaultFileManagerStorageEntries(workspacePath) + safBookmarks.map { bookmark ->
            FileManagerStorageEntry(
                title = bookmark.name,
                path = "/",
                environment = "repo:${bookmark.name}",
                subtitle = bookmark.uri,
                bookmarkUri = bookmark.uri,
            )
        }
    }
    val activeFiles = viewModel.files
    val folderCount = activeFiles.count { file -> file.isDirectory && file.name != ".." }
    val fileCount = activeFiles.count { file -> !file.isDirectory }
    val storageLabel = remember(context) { readStorageLabel(context) }
    val selectedCount = viewModel.selectedFiles.size + if (viewModel.selectedFile != null) 1 else 0

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FileManagerStorageDrawer(
                    entries = storageEntries,
                    onSelect = { entry ->
                        showStorageDrawer = false
                        viewModel.navigateToPath(entry.path, entry.environment)
                    },
                    onAddBookmark = {
                        showStorageDrawer = false
                        addBookmarkLauncher.launch(null)
                    },
                    onDeleteBookmark = { entry ->
                        entry.bookmarkUri?.let { bookmarkUri ->
                            val uri = runCatching { Uri.parse(bookmarkUri) }.getOrNull()
                            if (uri != null) {
                                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                try {
                                    context.contentResolver.releasePersistableUriPermission(uri, flags)
                                } catch (e: Exception) {
                                    AppLogger.w(FILE_MANAGER_TAG, "释放 SAF 书签权限失败", e)
                                }
                            }
                            scope.launch {
                                apiPreferences.removeSafBookmark(bookmarkUri)
                                if (viewModel.currentEnvironment == entry.environment) {
                                    viewModel.navigateToPath(viewModel.initialPath, null)
                                }
                            }
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 文件管理器顶栏需要绘制到状态栏物理顶边，内容和底栏各自消费安全区。
                .background(androidx.compose.ui.graphics.Color(0xFFFAFAFA)),
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                FileManagerTopBar(
                    currentPath = viewModel.currentPath,
                    folderCount = folderCount,
                    fileCount = fileCount,
                    selectedCount = selectedCount,
                    storageLabel = storageLabel,
                    isSearching = viewModel.isSearching,
                    onExitFileManager = onBack,
                    onPathClick = {
                        pathInput = viewModel.currentPath
                        showPathDialog = true
                    },
                    onOpenStorageDrawer = { showStorageDrawer = true },
                    onRefresh = {
                        viewModel.loadPaneDirectory(FileManagerPane.LEFT)
                        viewModel.loadPaneDirectory(FileManagerPane.RIGHT)
                    },
                    onShowSearchDialog = {
                        viewModel.searchDialogQuery = ""
                        viewModel.showSearchDialog = true
                    },
                    onSelectAll = { viewModel.selectAll() },
                    onToggleHiddenFiles = { viewModel.toggleHiddenFiles() },
                    onSelectSort = { viewModel.cycleSortMode() },
                    onOpenLinux = { viewModel.navigateToPath("/", "linux") },
                    onNewFolder = {
                        viewModel.newFolderName = ""
                        viewModel.showNewFolderDialog = true
                    },
                    onExitSearch = {
                        viewModel.searchQuery = ""
                        viewModel.isSearching = false
                        viewModel.searchResults.clear()
                    },
                )
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    FileManagerDualPane(
                        left = viewModel.leftPaneState,
                        right = viewModel.rightPaneState,
                        activePane = viewModel.activePane,
                        leftListState = leftListState,
                        rightListState = rightListState,
                        itemSize = viewModel.itemSize,
                        isMultiSelectMode = viewModel.isMultiSelectMode,
                        selectedFiles = viewModel.selectedFiles,
                        selectedFile = viewModel.selectedFile,
                        onPaneClick = viewModel::activatePane,
                        onItemClick = { pane, file ->
                            viewModel.activatePane(pane)
                            if (viewModel.isMultiSelectMode) {
                                if (file.name == "..") {
                                    viewModel.navigateUp()
                                } else {
                                    viewModel.toggleSelection(file)
                                }
                            } else if (file.isDirectory) {
                                viewModel.navigateToDirectory(file)
                            } else if (viewModel.selectedFile == file) {
                                viewModel.selectedFile = null
                            } else {
                                viewModel.selectedFile = file
                            }
                        },
                        onItemLongClick = { pane, file ->
                            viewModel.activatePane(pane)
                            if (viewModel.isMultiSelectMode) {
                                if (file.name != ".." && viewModel.selectedFiles.contains(file)) {
                                    viewModel.contextMenuFile = file
                                    viewModel.showBottomActionMenu = true
                                } else if (file.name != "..") {
                                    viewModel.toggleSelection(file)
                                }
                            } else if (file.name != "..") {
                                viewModel.contextMenuFile = file
                                viewModel.showBottomActionMenu = true
                            }
                        },
                        onItemSwipeRight = { pane, file ->
                            viewModel.activatePane(pane)
                            viewModel.selectFile(file)
                        },
                    )
                }
                FileManagerBottomBar(
                    canGoBack = viewModel.paneCanGoBack(),
                    canGoForward = viewModel.paneCanGoForward(),
                    activePane = viewModel.activePane,
                    onBack = { viewModel.navigateBack() },
                    onForward = { viewModel.navigateForward() },
                    onNew = {
                        viewModel.newFolderName = ""
                        viewModel.showNewFolderDialog = true
                    },
                    onMirrorPath = viewModel::mirrorActivePaneToOther,
                    onNavigateUp = { viewModel.navigateUp() },
                )
            }
            LoadingOverlay(isLoading = viewModel.isLoading)
        }
    }

    if (showBookmarkDialog && pendingBookmarkUri != null) {
        AlertDialog(
            onDismissRequest = {
                showBookmarkDialog = false
                pendingBookmarkUri = null
                bookmarkNameError = null
            },
            title = { Text("添加本地存储") },
            text = {
                TextField(
                    value = bookmarkName,
                    onValueChange = {
                        bookmarkName = it
                        bookmarkNameError = null
                    },
                    singleLine = true,
                    label = { Text("名称") },
                    isError = bookmarkNameError != null,
                    supportingText = { bookmarkNameError?.let { error -> Text(error) } },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingBookmarkUri ?: return@TextButton
                        val name = bookmarkName.trim()
                        if (name.isEmpty()) {
                            bookmarkNameError = context.getString(R.string.repo_bookmark_name_empty)
                            return@TextButton
                        }
                        if (safBookmarks.any { bookmark ->
                                bookmark.uri != uri.toString() && bookmark.name.equals(name, ignoreCase = true)
                            }) {
                            bookmarkNameError = context.getString(R.string.repo_bookmark_name_exists)
                            return@TextButton
                        }
                        scope.launch {
                            apiPreferences.addSafBookmark(uri.toString(), name)
                            showBookmarkDialog = false
                            pendingBookmarkUri = null
                            bookmarkNameError = null
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBookmarkDialog = false
                    pendingBookmarkUri = null
                    bookmarkNameError = null
                }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (showPathDialog) {
        AlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("跳转") },
            text = {
                TextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    singleLine = true,
                    label = { Text("路径") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = pathInput.trim()
                        if (path.isNotEmpty()) viewModel.navigateToPath(path)
                        showPathDialog = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            val clipText = clipboard?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                            if (!clipText.isNullOrBlank()) pathInput = clipText
                        },
                    ) { Text("粘贴") }
                    TextButton(onClick = { showPathDialog = false }) { Text("取消") }
                }
            },
        )
    }

    SearchDialog(
        showDialog = viewModel.showSearchDialog,
        searchQuery = viewModel.searchDialogQuery,
        onQueryChange = { viewModel.searchDialogQuery = it },
        isCaseSensitive = viewModel.isCaseSensitive,
        onCaseSensitiveChange = { viewModel.isCaseSensitive = it },
        useWildcard = viewModel.useWildcard,
        onWildcardChange = { viewModel.useWildcard = it },
        onSearch = {
            viewModel.searchQuery = viewModel.searchDialogQuery
            viewModel.showSearchDialog = false
            viewModel.searchFiles(viewModel.searchDialogQuery)
        },
        onDismiss = { viewModel.showSearchDialog = false },
    )
    SearchResultsDialog(
        showDialog = viewModel.showSearchResultsDialog,
        searchResults = viewModel.searchResults,
        onNavigateToFileDirectory = viewModel::navigateToFileDirectory,
        onDismiss = { viewModel.showSearchResultsDialog = false },
    )
    NewFolderDialog(
        showDialog = viewModel.showNewFolderDialog,
        folderName = viewModel.newFolderName,
        onFolderNameChange = { viewModel.newFolderName = it },
        onCreateFolder = {
            if (viewModel.newFolderName.isNotBlank()) {
                viewModel.createNewFolder(viewModel.newFolderName)
                viewModel.showNewFolderDialog = false
            }
        },
        onDismiss = { viewModel.showNewFolderDialog = false },
    )
    FileContextMenu(
        showMenu = viewModel.showBottomActionMenu,
        onDismissRequest = { viewModel.showBottomActionMenu = false },
        contextMenuFile = viewModel.contextMenuFile,
        isMultiSelectMode = viewModel.isMultiSelectMode,
        selectedFiles = viewModel.selectedFiles,
        currentPath = viewModel.currentPath,
        currentEnvironment = viewModel.currentEnvironment,
        onFilesUpdated = {
            viewModel.loadPaneDirectory(FileManagerPane.LEFT)
            viewModel.loadPaneDirectory(FileManagerPane.RIGHT)
        },
        toolHandler = toolHandler,
        onPaste = viewModel::pasteFiles,
        onCopy = { files -> viewModel.setClipboard(files, false) },
        onCut = { files -> viewModel.setClipboard(files, true) },
        onOpen = { file ->
            toolHandler.executeTool(
                AITool(
                    name = "open_file",
                    parameters = listOf(ToolParameter("path", viewModel.currentPath + "/" + file.name)) +
                        (viewModel.currentEnvironment?.let { environment -> listOf(ToolParameter("environment", environment)) }
                            ?: emptyList()),
                ),
            )
        },
        onShare = { file ->
            toolHandler.executeTool(
                AITool(
                    name = "share_file",
                    parameters = listOf(ToolParameter("path", viewModel.currentPath + "/" + file.name)) +
                        (viewModel.currentEnvironment?.let { environment -> listOf(ToolParameter("environment", environment)) }
                            ?: emptyList()),
                ),
            )
        },
    )
}

@Composable
private fun LoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        )
    }
}

private fun readStorageLabel(context: Context): String {
    val storage = StatFs(Environment.getExternalStorageDirectory().absolutePath)
    fun compactSize(bytes: Long): String =
        Formatter.formatFileSize(context, bytes)
            .replace(" GB", "G")
            .replace(" MB", "M")
            .replace(" KB", "K")
    return "存储: ${compactSize(storage.availableBytes)}/${compactSize(storage.totalBytes)}"
}
