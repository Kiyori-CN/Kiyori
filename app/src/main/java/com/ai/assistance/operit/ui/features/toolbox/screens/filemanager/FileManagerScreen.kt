package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Language
import androidx.compose.ui.graphics.luminance
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerJoinPath
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerIsLocal
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kiyori.design.theme.calculateKiyoriDrawerWidthDp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerLocation
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerScrollPosition
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.window.KiyoriStatusBarAppearanceOverride
import com.kiyori.design.theme.KiyoriSettingsTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.catch
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerDestinationDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerActionDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerActionKind
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerNewEntryDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerCopyConfirmation
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerCopyConflictDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerTransferDetails
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerRenameDialog

private const val FILE_MANAGER_TAG = "ToolboxFileManager"

@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    sessionViewModel: FileManagerViewModel? = null,
    onOpenAiDialogue: () -> Unit,
    onOpenBrowser: (() -> Unit)? = null,
) {
    // 与设置页相同的主题必须包住全部弹层，避免兄弟弹层回到外层主题。
    KiyoriSettingsTheme { FileManagerContent(onBack, onOpenSettings, modifier, sessionViewModel, onOpenAiDialogue, onOpenBrowser) }
}

@Composable
private fun FileManagerContent(onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier, sessionViewModel: FileManagerViewModel?, onOpenAiDialogue: () -> Unit, onOpenBrowser: (() -> Unit)?) {
    var showToolbox by remember { mutableStateOf(false) }
    var showHiddenDrawer by remember { mutableStateOf(false) }
    var browsePane by remember { mutableStateOf(FileManagerPane.LEFT) }
    var browseState by remember { mutableStateOf<com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPaneState?>(null) }
    var browseKind by remember { mutableStateOf("") }
    var showSearchHistory by remember { mutableStateOf(false) }
    var taskPage by remember { mutableStateOf<Boolean?>(null) }
    KiyoriStatusBarAppearanceOverride(darkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
    val context = LocalContext.current
    val viewModel = sessionViewModel ?: rememberFileManagerViewModel(context)
    val settingsOwner = remember(context) { com.ai.assistance.operit.data.preferences.FileManagerPreferences.getInstance(context) }
    val settings by settingsOwner.state.collectAsState()
    var managedEntry by remember { mutableStateOf<FileManagerStorageEntry?>(null) }
    var editedEntry by remember { mutableStateOf<FileManagerStorageEntry?>(null) }
    var sortedCategory by remember { mutableStateOf<String?>(null) }
    var sortedNetworkGroup by remember { mutableStateOf<String?>(null) }
    var entryEditError by remember { mutableStateOf<String?>(null) }
    var entrySaving by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, settings.refreshIntervalSeconds) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (settings.refreshIntervalSeconds == 0) return@repeatOnLifecycle
            while (true) {
                viewModel.refreshVisibleDirectories()
                kotlinx.coroutines.delay(settings.refreshIntervalSeconds * 1_000L)
            }
        }
    }
    val scope = rememberCoroutineScope()
    var storageDrawerOpen by remember { mutableStateOf(false) }
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()
    // 合并可见项变化但不因每次滑动取消正在统计的目录，避免大目录反复从头扫描。
    listOf(FileManagerPane.LEFT to leftListState, FileManagerPane.RIGHT to rightListState).forEach { (pane, list) ->
        LaunchedEffect(viewModel, pane, list, lifecycleOwner, settings.showDirectorySizes) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!settings.showDirectorySizes) return@repeatOnLifecycle
                var checkedRevision: Pair<FileManagerLocation, Long>? = null
                snapshotFlow {
                    val state = if (pane == FileManagerPane.LEFT) viewModel.leftPaneState else viewModel.rightPaneState
                    Triple(FileManagerLocation(state.path, state.environment), state.directoryRevision,
                        if (list.isScrollInProgress) emptyList() else list.layoutInfo.visibleItemsInfo.mapNotNull { state.files.getOrNull(it.index)?.name })
                }.conflate().collect { (location, revision, names) ->
                    val nextRevision = location to revision
                    val revalidate = checkedRevision != nextRevision
                    if (names.isNotEmpty()) {
                        kotlinx.coroutines.delay(150)
                        val current = if (pane == FileManagerPane.LEFT) viewModel.leftPaneState else viewModel.rightPaneState
                        if (!list.isScrollInProgress && FileManagerLocation(current.path, current.environment) == location) {
                            viewModel.loadVisibleDirectorySizes(pane, names, revalidate = revalidate)
                            checkedRevision = nextRevision
                        }
                    }
                }
            }
        }
    }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())
    var bookmarkError by remember { mutableStateOf<String?>(null) }
    val fileBookmarkFlow = remember(apiPreferences) { apiPreferences.fileBookmarksFlow.catch {
        bookmarkError = "书签读取失败，请检查存储；原有书签未覆盖"; emit(emptyList())
    } }
    val fileBookmarks by fileBookmarkFlow.collectAsState(initial = emptyList())
    val fileWorkspaces by remember(apiPreferences) { apiPreferences.fileWorkspacesFlow.catch {
        bookmarkError = "工作区读取失败，原有记录未覆盖"; emit(emptyList())
    } }.collectAsState(initial = emptyList())
    val fileNetworks by remember(apiPreferences) { apiPreferences.fileNetworksFlow.catch { bookmarkError = "网络位置读取失败"; emit(emptyList()) } }.collectAsState(initial = emptyList())
    val networkGroups by remember(apiPreferences) { apiPreferences.fileNetworkGroupsFlow.catch { bookmarkError = "网络分组读取失败"; emit(emptyList()) } }.collectAsState(initial = emptyList())
    var showNetworkDialog by remember { mutableStateOf(false) }
    var editingNetwork by remember { mutableStateOf<com.ai.assistance.operit.core.tools.defaultTool.standard.NetworkStorageProfile?>(null) }
    var showNetworkGroup by remember { mutableStateOf(false) }
    var networkGroupName by remember { mutableStateOf("") }
    var addingWorkspace by remember { mutableStateOf(false) }
    var showOpenWith by remember { mutableStateOf(false) }
    var pendingFileBookmark by remember { mutableStateOf<ApiPreferences.FileBookmark?>(null) }
    var fileBookmarkName by remember { mutableStateOf("") }
    var savingBookmark by remember { mutableStateOf(false) }
    var pendingStorageRemoval by remember { mutableStateOf<FileManagerStorageEntry?>(null) }
    var removingStorage by remember { mutableStateOf(false) }
    var showExitWhileCopying by remember { mutableStateOf(false) }
    val exitFileManager = {
        if (viewModel.isWriting) showExitWhileCopying = true else onBack()
    }

    // 页面 Back 只在弹层 owner 之后取得优先级；目录层级耗尽才交还 Shell/Router。
    BackHandler {
        if (!viewModel.navigateBack()) {
            exitFileManager()
        }
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

    val workspacePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).resolve("Kiyori/workspace").absolutePath
    val rawStorageEntries = remember(safBookmarks, fileBookmarks, fileWorkspaces, fileNetworks, workspacePath) {
        defaultFileManagerStorageEntries(workspacePath) + safBookmarks.map { bookmark ->
            FileManagerStorageEntry(
                title = bookmark.name,
                path = "/",
                environment = "repo:${bookmark.name}",
                subtitle = storageBookmarkDisplayPath(bookmark.uri),
                bookmarkUri = bookmark.uri,
            )
        } + fileBookmarks.map { bookmark ->
            FileManagerStorageEntry(bookmark.name, bookmark.path, bookmark.environment,
                subtitle = bookmark.path, fileBookmark = bookmark, category = "书签")
        } + fileWorkspaces.map { workspace ->
            FileManagerStorageEntry(workspace.name, workspace.path, workspace.environment,
                subtitle = workspace.path, fileBookmark = workspace, category = "工作区")
        } + fileNetworks.map { profile ->
            FileManagerStorageEntry(profile.name, "/", "network:${profile.id}", subtitle = profile.protocol.name,
                category = "网络", network = profile)
        }
    }
    val storageEntries = remember(rawStorageEntries, settings) { projectFileManagerStorageEntries(rawStorageEntries, settings) }
    val activeFiles = viewModel.files
    val folderCount = activeFiles.count { file -> file.isDirectory && file.name != ".." }
    val fileCount = activeFiles.count { file -> !file.isDirectory }
    val storageLabel = rememberStorageLabel(viewModel)
    val selectedCount = viewModel.selectedFiles.size

    androidx.compose.runtime.CompositionLocalProvider(LocalFileManagerDisplaySettings provides settings) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val storageDrawerWidth = calculateKiyoriDrawerWidthDp(maxWidth.value).dp
    FileManagerModalStorageDrawer(
        isOpen = storageDrawerOpen,
        width = storageDrawerWidth,
        onDismiss = { storageDrawerOpen = false },
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(storageDrawerWidth).statusBarsPadding(),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                FileManagerStorageDrawer(
                    entries = storageEntries,
                    showBookmarks = settings.showBookmarks, showWorkspaces = settings.showWorkspaces,
                    currentPath = viewModel.currentPath, currentEnvironment = viewModel.currentEnvironment,
                    onDismiss = { scope.launch { storageDrawerOpen = false } },
                    networkGroups = networkGroups,
                    onAddNetwork = { editingNetwork = null; showNetworkDialog = true },
                    onAddNetworkGroup = { networkGroupName = ""; showNetworkGroup = true },
                    onRecycleBin = {
                        val pane = viewModel.activePane
                        scope.launch { storageDrawerOpen = false; viewModel.activatePane(pane); viewModel.openRecycleBin() }
                    },
                    onSelect = { entry ->
                        scope.launch { storageDrawerOpen = false }
                        if (entry.category == "工作区" && entry.fileBookmark == null) scope.launch {
                            try {
                                if (settings.defaultWorkspacePath.isBlank()) withContext(Dispatchers.IO) { com.ai.assistance.operit.util.OperitPaths.workspaceDir() }
                                viewModel.navigateToPath(entry.path, entry.environment)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                AppLogger.e(FILE_MANAGER_TAG, "打开默认工作区失败", failure)
                                bookmarkError = "无法打开默认工作区，请检查目录与存储权限"
                            }
                        }
                        val bookmark = entry.fileBookmark
                        if (bookmark != null && !bookmark.directory) {
                            viewModel.navigateToPath(bookmark.path.substringBeforeLast('/').ifBlank { "/" }, bookmark.environment)
                            viewModel.setDirectoryFilter(bookmark.path.substringAfterLast('/'))
                        } else if (entry.category != "工作区" || entry.fileBookmark != null) viewModel.navigateToPath(entry.path, entry.environment)
                    },
                    onAddBookmark = {
                        scope.launch { storageDrawerOpen = false }
                        addBookmarkLauncher.launch(null)
                    },
                    onManageEntry = { managedEntry = it },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 文件管理器顶栏需要绘制到状态栏物理顶边，内容和底栏各自消费安全区。
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            FileManagerPaneScrollEffect(viewModel, FileManagerPane.LEFT, leftListState)
            FileManagerPaneScrollEffect(viewModel, FileManagerPane.RIGHT, rightListState)
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                FileManagerTopBar(
                    currentPath = viewModel.currentPath,
                    folderCount = folderCount,
                    fileCount = fileCount,
                    selectedCount = selectedCount,
                    storageLabel = storageLabel,
                    isSearching = viewModel.isSearching,
                    refreshing = viewModel.activePaneState.refreshing,
                    hasFilter = viewModel.activePaneState.hasFilter,
                    totalCount = viewModel.activePaneState.entries.count { it.name != "." && it.name != ".." && (viewModel.showHiddenFiles || !it.name.startsWith('.')) },
                    onExitFileManager = exitFileManager,
                    onPathClick = { pathInput = viewModel.currentPath; showPathDialog = true },
                    onOpenStorageDrawer = { scope.launch { storageDrawerOpen = true } },
                    onShowSearchDialog = viewModel::beginSearchDialog,
                    onShowFilter = { browsePane = viewModel.activePane; browseState = viewModel.activePaneState; browseKind = "filter" },
                    onShowSort = { browsePane = viewModel.activePane; browseState = viewModel.activePaneState; browseKind = "sort" },
                    onRefresh = { viewModel.refreshPane() },
                )
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    FileManagerDualPane(
                        left = viewModel.leftPaneState,
                        right = viewModel.rightPaneState,
                        activePane = viewModel.activePane,
                        leftListState = leftListState,
                        rightListState = rightListState,
                        itemSize = viewModel.itemSize,
                        leftSelectedFiles = viewModel.selectionForPane(FileManagerPane.LEFT),
                        rightSelectedFiles = viewModel.selectionForPane(FileManagerPane.RIGHT),
                        leftSelectionMode = viewModel.selectionModeForPane(FileManagerPane.LEFT),
                        rightSelectionMode = viewModel.selectionModeForPane(FileManagerPane.RIGHT),
                        onPaneClick = viewModel::activatePane,
                        onRetry = { pane -> viewModel.loadPaneDirectory(pane) },
                        onRefresh = { pane -> viewModel.refreshPane(pane) },
                        onAdjustFilter = { pane ->
                            viewModel.activatePane(pane); browsePane = pane; browseState = viewModel.activePaneState; browseKind = "filter"
                        },
                        onClearFilter = { pane ->
                            val state = if (pane == FileManagerPane.LEFT) viewModel.leftPaneState else viewModel.rightPaneState
                            viewModel.applyDirectoryFilter(pane, FileManagerLocation(state.path, state.environment),
                                com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerFilterDraft())
                        },
                        onItemClick = { pane, file ->
                            viewModel.activatePane(pane)
                            viewModel.clickEntry(file)
                        },
                        onItemLongClick = { pane, file ->
                            viewModel.activatePane(pane)
                            if (file.name != "..") {
                                viewModel.contextMenuPane = pane
                                viewModel.contextMenuFile = file
                                viewModel.showBottomActionMenu = true
                            }
                        },
                        onItemToggleSelection = { pane, file ->
                            viewModel.activatePane(pane)
                            viewModel.toggleSelection(file)
                        },
                        onItemSwipeRight = { pane, file ->
                            viewModel.activatePane(pane)
                            viewModel.selectFile(file)
                        },
                    )
                }
                com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerClipboardBar(
                    count = viewModel.clipboardFiles.size, move = viewModel.isCutOperation,
                    canPaste = viewModel.canPasteHere, writing = viewModel.isWriting,
                    onPaste = viewModel::requestPaste, onClear = viewModel::clearClipboard,
                )
                FileManagerBottomBar(
                    canGoBack = viewModel.paneCanGoBack(),
                    canGoForward = viewModel.paneCanGoForward(),
                    activePane = viewModel.activePane,
                    onBack = { viewModel.navigateBackDirectory() },
                    onForward = { viewModel.navigateForward() },
                    onNew = viewModel::beginCreateEntry,
                    onMirrorPath = viewModel::mirrorActivePaneToOther,
                    onOpenMenu = viewModel::openActionMenu,
                    canCreate = viewModel.canCreateHere,
                )
            }

        }
    }

    }
    }


    managedEntry?.let { entry -> FileManagerStorageMenu(entry, onDismiss = { managedEntry = null }) { action ->
        managedEntry = null
        when (action) {
            "编辑", "重命名" -> if (entry.network != null) { editingNetwork = entry.network; showNetworkDialog = true }
                else { entryEditError = null; editedEntry = entry }
            "删除" -> pendingStorageRemoval = entry
            "隐藏" -> settingsOwner.update { it.copy(drawerHidden = it.drawerHidden + entry.storageId,
                drawerNames = it.drawerNames + (entry.storageId to entry.title)) }
            "排序" -> { sortedCategory = entry.category; sortedNetworkGroup = entry.network?.group }
            "创建快捷方式" -> try {
                requestFileManagerShortcut(context, entry)
                Toast.makeText(context, "已请求创建，请在桌面提示中确认", Toast.LENGTH_LONG).show()
            } catch (failure: Exception) {
                AppLogger.e(FILE_MANAGER_TAG, "创建存储快捷方式失败", failure)
                bookmarkError = failure.message ?: "创建快捷方式失败"
            }
        }
    } }
    editedEntry?.let { entry -> FileManagerStorageEditDialog(entry, entrySaving, entryEditError, { editedEntry = null }) { name, path ->
        entrySaving = true
        scope.launch {
            try {
                val original = entry.fileBookmark
                if (original != null) apiPreferences.editFileManagerBookmark(original, original.copy(name = name, path = path), entry.category == "工作区")
                else settingsOwner.update { it.copy(drawerNames = it.drawerNames + (entry.storageId to name),
                    defaultWorkspacePath = if (entry.storageId == "default-workspace") path else it.defaultWorkspacePath) }
                editedEntry = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                AppLogger.e(FILE_MANAGER_TAG, "编辑存储入口失败", failure)
                entryEditError = failure.message ?: "保存失败，请重试"
            } finally { entrySaving = false }
        }
    } }
    sortedCategory?.let { category -> FileManagerStorageSortDialog(
        storageEntries.filter { category != "网络" || it.network?.group == sortedNetworkGroup }, category, { sortedCategory = null }) { ids ->
        settingsOwner.update { it.copy(drawerOrder = it.drawerOrder.filterNot { id -> id in ids } + ids) }
        sortedCategory = null
    } }
    FileManagerContentHost(viewModel)
    pendingStorageRemoval?.let { entry ->
        AlertDialog(onDismissRequest = { if (!removingStorage) pendingStorageRemoval = null }, title = { Text("移除${entry.title}？") },
            text = { Text("仅移除此存储入口或收藏记录，不删除文件。${if (entry.bookmarkUri != null) "此应用会释放对应的目录授权。" else ""}") },
            confirmButton = { TextButton(enabled = !removingStorage && !viewModel.isWriting, onClick = {
                removingStorage = true
                scope.launch {
                    try {
                        if (entry.network == null && entry.fileBookmark == null && entry.bookmarkUri == null) {
                            settingsOwner.update { it.copy(drawerRemoved = it.drawerRemoved + entry.storageId) }
                        }
                        entry.network?.let { apiPreferences.removeFileNetwork(it.id) }
                        entry.fileBookmark?.let { if (entry.category == "工作区") apiPreferences.removeFileWorkspace(it) else apiPreferences.removeFileBookmark(it) }
                        entry.bookmarkUri?.let { uri ->
                            apiPreferences.removeSafBookmark(uri)
                            try { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                            catch (failure: Exception) { bookmarkError = "入口已移除，但目录授权未能释放，可在系统设置中检查" }
                        }
                        if (entry.network != null || entry.bookmarkUri != null) viewModel.detachStorageEnvironment(entry.environment)
                        pendingStorageRemoval = null
                    } catch (failure: Exception) { bookmarkError = "移除失败，请重试；未删除任何文件" }
                    finally { removingStorage = false }
                }
            }) { Text(if (removingStorage) "正在移除" else "移除入口") } },
            dismissButton = { TextButton(enabled = !removingStorage, onClick = { pendingStorageRemoval = null }) { Text("取消") } })
    }
    FileManagerDestinationDialog(viewModel.transferDraft, viewModel::browseTransferDestination,
        viewModel::useOtherTransferDestination, viewModel::dismissTransferDraft)
    FileManagerActionDialog(viewModel.actionState, !viewModel.isWriting, viewModel::dismissAction,
        viewModel::updateActionName, viewModel::confirmContextAction, viewModel::readActionInspection,
        viewModel::stopActionAfterCurrent, viewModel::openActionRecycleBin) { value ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("文件信息", value))
    }
    bookmarkError?.let { message -> AlertDialog(onDismissRequest = { bookmarkError = null }, title = { Text("存储入口") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { bookmarkError = null }) { Text("知道了") } }) }
    pendingFileBookmark?.let { bookmark ->
        AlertDialog(onDismissRequest = { if (!savingBookmark) pendingFileBookmark = null }, title = { Text(if (addingWorkspace) "添加工作区" else "添加书签") },
            text = { androidx.compose.foundation.layout.Column {
                Text(bookmark.path)
                androidx.compose.material3.OutlinedTextField(fileBookmarkName, { fileBookmarkName = it }, singleLine = true,
                    label = { Text(if (addingWorkspace) "工作区名称" else "书签名称") }, enabled = !savingBookmark)
            } },
            confirmButton = { TextButton(enabled = !savingBookmark && fileBookmarkName.isNotBlank(), onClick = {
                savingBookmark = true
                scope.launch {
                    try {
                        val entry = bookmark.copy(name = fileBookmarkName.trim())
                        if (addingWorkspace) apiPreferences.addFileWorkspace(entry) else {
                            val saved = apiPreferences.addFileBookmark(entry, atTop = settings.newBookmarksOnTop)
                            if (settings.newBookmarksOnTop) {
                                val id = FileManagerStorageEntry(saved.name, saved.path, saved.environment, fileBookmark = saved, category = "书签").storageId
                                settingsOwner.update { it.copy(drawerOrder = listOf(id) + it.drawerOrder.filterNot { old -> old == id }) }
                            }
                        }
                        pendingFileBookmark = null
                    }
                    catch (error: Exception) { bookmarkError = "添加书签失败，请重试" }
                    finally { savingBookmark = false }
                }
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { pendingFileBookmark = null }, enabled = !savingBookmark) { Text("取消") } })
    }

    if (showExitWhileCopying) {
        AlertDialog(
            onDismissRequest = { showExitWhileCopying = false },
            title = { Text("文件操作仍在进行") },
            text = { Text(if (viewModel.transferState.running) "可继续浏览，或等待当前项目完成后停止。完成前请保留文件管理器页面。" else "正在保存操作结果，请等待完成后再退出。") },
            confirmButton = { TextButton(onClick = { showExitWhileCopying = false }) { Text("继续浏览") } },
            dismissButton = { if (viewModel.transferState.running) TextButton(onClick = {
                viewModel.stopTransferAfterCurrent()
                showExitWhileCopying = false
                viewModel.showTransferDetails = true
            }) { Text("完成当前项后停止") } },
        )
    }
    FileManagerCopyConfirmation(viewModel.pendingCopy, viewModel::confirmPaste, viewModel::dismissPaste)
    FileManagerRenameDialog(viewModel.renameState, viewModel::updateRenameName, viewModel::confirmRename, viewModel::dismissRename)
    FileManagerTransferDetails(
        visible = viewModel.showTransferDetails && viewModel.copyConflict == null,
        state = viewModel.transferState,
        onDismiss = { viewModel.showTransferDetails = false },
        onStop = viewModel::stopTransferAfterCurrent,
        onOpenDestination = viewModel::openTransferDestination,
    )
    FileManagerCopyConflictDialog(
        conflict = viewModel.copyConflict,
        destination = viewModel.transferState.destination,
        directory = viewModel.copyConflict?.directory == true,
        onResolve = viewModel::resolveCopyConflict,
        move = viewModel.transferState.move,
        onStop = viewModel::stopTransferAfterCurrent,
    )
    if (showBookmarkDialog && pendingBookmarkUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (savingBookmark) return@AlertDialog
                showBookmarkDialog = false
                pendingBookmarkUri = null
                bookmarkNameError = null
            },
            title = { Text("添加本地存储") },
            text = {
                androidx.compose.foundation.layout.Column {
                Text(storageBookmarkDisplayPath(pendingBookmarkUri.toString()), style = MaterialTheme.typography.bodySmall)
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
                    enabled = !savingBookmark,
                )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !savingBookmark,
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
                        savingBookmark = true
                        scope.launch {
                            try {
                                apiPreferences.addSafBookmark(uri.toString(), name)
                                showBookmarkDialog = false
                                pendingBookmarkUri = null
                                bookmarkNameError = null
                            } catch (failure: Exception) { bookmarkNameError = "保存失败，请重试" }
                            finally { savingBookmark = false }
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(enabled = !savingBookmark, onClick = {
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
        form = viewModel.searchForm,
        onFormChange = { viewModel.searchForm = it },
        location = viewModel.searchDialogLocationLabel,
        onHistory = { showSearchHistory = true },
        paneLabel = viewModel.searchDialogPaneLabel,
        onSearch = { viewModel.showSearchDialog = false; viewModel.submitSearchDialog() },
        onDismiss = { viewModel.showSearchDialog = false },
    )
    SearchResultsDialog(
        showDialog = viewModel.showSearchResultsDialog,
        searchResults = viewModel.searchResults,
        onNavigateToFileDirectory = viewModel::navigateToFileDirectory,
        onDismiss = viewModel::cancelSearch,
        isSearching = viewModel.isSearching,
        error = viewModel.searchError,
        summary = viewModel.searchSummary,
        limitations = viewModel.searchLimitations,
        onEditSearch = viewModel::editSearch,
        onRepeatSearch = viewModel::repeatSavedSearch,
    )
    FileManagerNewEntryDialog(
        showDialog = viewModel.showNewEntryDialog,
        entryName = viewModel.newEntryName,
        onEntryNameChange = { viewModel.newEntryName = it; viewModel.creationError = null },
        onCreateFile = { viewModel.createNewFile(viewModel.newEntryName) },
        onCreateFolder = { viewModel.createNewFolder(viewModel.newEntryName) },
        onDismiss = viewModel::dismissCreateEntry,
        isCreating = viewModel.isCreating,
        unknown = viewModel.creationUnknown,
        error = viewModel.creationError,
    )
    if (showNetworkDialog) com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerNetworkDialog(
        editingNetwork, networkGroups, onDismiss = { showNetworkDialog = false },
    )
    if (showNetworkGroup) AlertDialog(onDismissRequest = { showNetworkGroup = false }, title = { Text("添加网络分组") },
        text = { androidx.compose.material3.OutlinedTextField(networkGroupName, { networkGroupName = it }, singleLine = true, label = { Text("分组名称") }) },
        confirmButton = { TextButton(enabled = networkGroupName.isNotBlank(), onClick = {
            scope.launch {
                try { apiPreferences.addFileNetworkGroup(networkGroupName); showNetworkGroup = false }
                catch (failure: Exception) { bookmarkError = "添加分组失败" }
            }
        }) { Text("添加") } }, dismissButton = { TextButton(onClick = { showNetworkGroup = false }) { Text("取消") } })
    if (showOpenWith) AlertDialog(
        onDismissRequest = { showOpenWith = false }, title = { Text("打开方式") },
        text = { androidx.compose.foundation.layout.Column {
            listOf("文本编辑器" to com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerOpenKind.TEXT,
                "内置播放器" to com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerOpenKind.MEDIA,
                "图片查看器" to com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerOpenKind.IMAGE,
                "其他应用" to com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerOpenKind.SYSTEM).forEach { (label, kind) ->
                TextButton(onClick = {
                    showOpenWith = false
                    viewModel.activatePane(viewModel.contextMenuPane)
                    viewModel.contextMenuFile?.let { viewModel.openEntry(it, kind) }
                }) { Text(label) }
            }
        } }, confirmButton = { TextButton(onClick = { showOpenWith = false }) { Text("取消") } },
    )
    browseState?.let { frozen ->
        if (browseKind == "filter") com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerFilterDrawer(
            browsePane, frozen, onDismiss = { browseKind = ""; browseState = null },
            onApply = { draft -> viewModel.applyDirectoryFilter(browsePane, FileManagerLocation(frozen.path, frozen.environment), draft) })
        if (browseKind == "sort") com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerSortDrawer(
            browsePane, frozen, onDismiss = { browseKind = ""; browseState = null },
            onApply = { mode, descending -> viewModel.applySort(browsePane, mode, descending) })
    }
    if (showSearchHistory) com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerSearchHistoryDrawer(
        viewModel.history.searches, viewModel.historyError, { showSearchHistory = false }, viewModel::openSearchRecord, viewModel::removeSearchRecord)
    taskPage?.let { recent -> com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerTaskDrawer(
        recent, viewModel.history.tasks, viewModel.transferState, viewModel.actionState, viewModel.isWriting,
        viewModel.copyConflict != null, viewModel.historyError, { taskPage = null },
        { viewModel.showTransferDetails = true }, viewModel::removeTaskRecord,
        { location -> viewModel.navigateToPath(location.path, location.environment) }) }
    if (showHiddenDrawer) com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.FileManagerHiddenDrawer(viewModel) { showHiddenDrawer = false }
    if (showToolbox) com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxDrawer(
        onDismiss = { showToolbox = false },
        actions = listOf(
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction("AI对话", androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Chat,
                com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.AI_DIALOGUE, !viewModel.isWriting, onClick = onOpenAiDialogue),
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction("浏览器", androidx.compose.material.icons.Icons.Rounded.Language,
                com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.ADD_BOOKMARK, !viewModel.isWriting && onOpenBrowser != null,
                onClick = { onOpenBrowser?.invoke() }),
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction("取消粘贴", Icons.Rounded.ContentPasteOff, com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.DIAGNOSTICS,
                viewModel.clipboardFiles.isNotEmpty() && !viewModel.isWriting, onClick = viewModel::clearClipboard),
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction("隐藏文件", Icons.Rounded.Visibility, com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.ADD_BOOKMARK,
                onClick = { showHiddenDrawer = true }),
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction("交换窗口", Icons.Rounded.SwapHoriz, com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.TOOLBOX, !viewModel.isWriting, onClick = {
                viewModel.saveScrollPosition(FileManagerPane.LEFT, FileManagerLocation(viewModel.leftPaneState.path, viewModel.leftPaneState.environment),
                    FileManagerScrollPosition(leftListState.firstVisibleItemIndex, leftListState.firstVisibleItemScrollOffset))
                viewModel.saveScrollPosition(FileManagerPane.RIGHT, FileManagerLocation(viewModel.rightPaneState.path, viewModel.rightPaneState.environment),
                    FileManagerScrollPosition(rightListState.firstVisibleItemIndex, rightListState.firstVisibleItemScrollOffset))
                viewModel.swapPanes()
            }),
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction(if (viewModel.transferState.running) "传输任务 · 1" else "传输任务", Icons.Rounded.SyncAlt, com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.NETWORK_LOG,
                onClick = { taskPage = false }),
            com.ai.assistance.operit.ui.features.websession.browser.chrome.KiyoriToolboxAction("最近任务", Icons.Rounded.History, com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserMenuTone.DIAGNOSTICS, onClick = { taskPage = true }),
        ),
    )
    val contextState = if (viewModel.contextMenuPane == FileManagerPane.LEFT) viewModel.leftPaneState else viewModel.rightPaneState
    val contextItems = viewModel.contextItems()
    val contextPath = viewModel.contextMenuFile?.let { it.recycledOriginalPath ?: fileManagerJoinPath(contextState.path, it.name) } ?: contextState.path
    FileContextMenu(
        showMenu = viewModel.showBottomActionMenu,
        onDismissRequest = { viewModel.showBottomActionMenu = false },
        contextMenuFile = viewModel.contextMenuFile,
        onExit = exitFileManager,
        onSettings = { if (viewModel.isWriting) showExitWhileCopying = true else onOpenSettings() },
        fullPath = contextPath,
        selectionCount = contextItems.size,
        sourceIsLeft = viewModel.contextMenuPane == FileManagerPane.LEFT,
        recycleBin = contextState.environment == "recycle",
        onRestore = { viewModel.beginContextAction(FileManagerActionKind.RESTORE) },
        canSelect = !contextState.isLoading && contextState.error == null && contextState.files.any { it.name != ".." },
        allArchives = contextItems.isNotEmpty() && contextItems.all { !it.isDirectory && it.name.endsWith(".zip", ignoreCase = true) },
        onClearSelection = { viewModel.activatePane(viewModel.contextMenuPane); viewModel.clearActiveSelection() },
        onSelectAll = { viewModel.activatePane(viewModel.contextMenuPane); viewModel.selectAll() },
        onPaste = { viewModel.activatePane(viewModel.contextMenuPane); viewModel.requestPaste() },
        canPaste = viewModel.clipboardFiles.isNotEmpty() && !viewModel.isWriting && fileManagerIsLocal(contextState.environment) &&
            fileManagerIsLocal(viewModel.clipboardSourceEnvironment) && !contextState.isLoading && contextState.error == null,
        environmentLabel = contextState.environment ?: "手机",
        onCopy = { viewModel.beginContextTransfer(false) },
        onMove = { viewModel.beginContextTransfer(true) },
        onDelete = { viewModel.beginContextAction(if (contextState.environment == "recycle") FileManagerActionKind.PURGE else FileManagerActionKind.DELETE) },
        onTools = { showToolbox = true },
        onZip = { viewModel.beginContextAction(FileManagerActionKind.ZIP) },
        onProperties = { viewModel.beginContextAction(FileManagerActionKind.PROPERTIES) },
        onShare = viewModel::shareContextItem,
        localActions = contextState.environment.isNullOrBlank() || contextState.environment == "android",
        allSelected = contextState.files.any { it.name != ".." } && contextState.files.filter { it.name != ".." }.all { file -> viewModel.selectionForPane(viewModel.contextMenuPane).any { it.name == file.name } },
        onExtract = { viewModel.beginContextAction(FileManagerActionKind.EXTRACT) },
        onInvertSelection = { viewModel.activatePane(viewModel.contextMenuPane); viewModel.invertSelection() },
        onWorkspace = {
            viewModel.contextMenuFile?.takeIf { it.isDirectory }?.let { file ->
                addingWorkspace = true
                pendingFileBookmark = ApiPreferences.FileBookmark(file.name, contextPath, contextState.environment, true)
                fileBookmarkName = file.name
            }
        },
        onBookmark = {
            addingWorkspace = false
            viewModel.contextMenuFile?.let { file ->
                pendingFileBookmark = ApiPreferences.FileBookmark(file.name, contextPath, contextState.environment, file.isDirectory)
                fileBookmarkName = file.name
            }
        },
        onRename = viewModel::beginRenameContextItem,
        writing = viewModel.isWriting,
        onOpen = { showOpenWith = true },
        onSelect = {
            viewModel.activatePane(viewModel.contextMenuPane)
            viewModel.contextMenuFile?.let { viewModel.addSingleSelection(it) }
            viewModel.showBottomActionMenu = false
        },
    )
}

private fun storageBookmarkDisplayPath(rawUri: String): String {
    val uri = Uri.parse(rawUri)
    if (uri.authority != "com.android.externalstorage.documents") return rawUri
    val document = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return rawUri
    val volume = document.substringBefore(':')
    val relative = document.substringAfter(':', "")
    val root = if (volume == "primary") Environment.getExternalStorageDirectory().absolutePath else "/storage/$volume"
    return if (relative.isEmpty()) root else "$root/$relative"
}

@Composable
internal fun rememberFileManagerViewModel(context: Context): FileManagerViewModel {
    val store = remember { ViewModelStore() }
    val applicationContext = context.applicationContext
    val viewModel = remember(store, applicationContext) {
        ViewModelProvider(
            store,
            viewModelFactory { initializer { FileManagerViewModel(applicationContext, settingsStore = com.ai.assistance.operit.data.preferences.FileManagerPreferences.getInstance(applicationContext),
                historyStore = com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerHistoryStore.getInstance(applicationContext)) } },
        )[FileManagerViewModel::class.java]
    }
    // 页面有独立退出语义；清理 store 才会取消 ViewModel 的目录读取和文件工作。
    DisposableEffect(store) {
        onDispose { store.clear() }
    }
    return viewModel
}

@Composable
private fun FileManagerPaneScrollEffect(
    viewModel: FileManagerViewModel,
    pane: FileManagerPane,
    listState: LazyListState,
) {
    val state = if (pane == FileManagerPane.LEFT) viewModel.leftPaneState else viewModel.rightPaneState
    val location = FileManagerLocation(state.path, state.environment)
    val latestState by androidx.compose.runtime.rememberUpdatedState(state)
    LaunchedEffect(viewModel, pane, location, listState, state.isLoading, state.error, state.scrollKey, state.presentationVersion) {
        if (state.isLoading || state.error != null) return@LaunchedEffect
        // effect 随成功状态提交后运行，并等待新列表布局，不能对加载占位项恢复后立即保存。
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it == latestState.files.size }
        val position = viewModel.scrollPosition(pane, location, state.scrollKey)
        listState.scrollToItem(position.index.coerceAtMost((state.files.size - 1).coerceAtLeast(0)), position.offset)
        snapshotFlow {
            FileManagerScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collect { current ->
            viewModel.saveScrollPosition(pane, location, current, state.scrollKey, state.presentationVersion)
        }
    }
}

@Composable
private fun rememberStorageLabel(viewModel: FileManagerViewModel): String {
    val lifecycleOwner = LocalLifecycleOwner.current
    val path = viewModel.currentPath
    val environment = viewModel.currentEnvironment
    val label by produceState("", viewModel, lifecycleOwner, path, environment) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = if (environment.isNullOrBlank() || environment == "android") {
                    withContext(Dispatchers.IO) { readStorageLabel(path) }
                } else "储存: 未提供"
                kotlinx.coroutines.delay(3_000)
            }
        }
    }
    return label
}

private fun readStorageLabel(path: String): String = try {
    val storage = StatFs(path)
    val usedBytes = (storage.totalBytes - storage.availableBytes).coerceAtLeast(0L)
    "储存: ${compactStorageSize(usedBytes)}/${compactStorageSize(storage.totalBytes)}"
} catch (_: IllegalArgumentException) {
    "储存: 不可用"
} catch (_: SecurityException) {
    "储存: 无权限"
}

private fun compactStorageSize(bytes: Long): String {
    val units = arrayOf("B", "K", "M", "G", "T")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()}B"
    } else {
        String.format(java.util.Locale.US, "%.2f%s", value, units[unitIndex])
    }
}
