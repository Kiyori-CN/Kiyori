package com.ai.assistance.operit.ui.features.memory.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SelectAll

import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.BatchDeleteConfirmDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.DocumentViewDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.LinkMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemoryInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EdgeInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditEdgeDialog
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.widget.Toast

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemoryLibrarySettingsSheet
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSimulationDialog
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val profileList by preferencesManager.memorySpaceListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
    preferencesManager.activeMemorySpaceIdFlow.collectAsState(initial = "default")

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    val selectedProfileId = activeProfileId
    var showFolderNavigator by rememberSaveable { mutableStateOf(false) }

    val viewModel: MemoryViewModel =
        viewModel(
            key = selectedProfileId, // Recreate ViewModel when profile changes
            factory = MemoryViewModelFactory(context, selectedProfileId)
        )
    val uiState by viewModel.uiState.collectAsState()
    val isImporting = uiState.isImporting
    var importProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var importFolderPath by rememberSaveable { mutableStateOf("") }
    // 系统文件选择器可能经历 Activity 重建，保存目标身份而不是仅保存对象引用。
    val pendingImportViewModel = importProfileId?.let { id ->
        viewModel<MemoryViewModel>(key = id, factory = MemoryViewModelFactory(context, id))
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isCurrentScreen = LocalIsCurrentScreen.current
    LaunchedEffect(isCurrentScreen) {
        if (!isCurrentScreen) showFolderNavigator = false
    }
    var spaceBusy by remember { mutableStateOf(false) }
    var spaceError by remember { mutableStateOf<String?>(null) }
    fun manageSpace(action: suspend () -> Unit) {
        if (spaceBusy) return
        spaceBusy = true
        spaceError = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { spaceError = resources.getString(R.string.library_space_error, e.message ?: e.javaClass.simpleName) }
            finally { spaceBusy = false }
        }
    }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        try {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getMemorySpaceFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { spaceError = resources.getString(R.string.library_space_error, e.message ?: e.javaClass.simpleName) }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            val target = pendingImportViewModel
            importProfileId = null
            if (uris.isNotEmpty()) {
                if (target == null) Toast.makeText(context, resources.getString(R.string.library_import_retry), Toast.LENGTH_LONG).show()
                else target.importDocuments(uris, importFolderPath)
            }
        }
    )

    CustomScaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 只有在框选模式下才显示"确认删除"按钮
                if (uiState.showGraph && !uiState.isLoading && uiState.isBoxSelectionMode && uiState.boxSelectedNodeIds.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { viewModel.showBatchDeleteConfirm() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.library_delete_selected))
                    }
                }

                if (uiState.showGraph && !uiState.isLoading && uiState.memories.isNotEmpty() && !showFolderNavigator) {
                // 框选模式切换按钮
                FloatingActionButton(
                    onClick = {
                        com.ai.assistance.operit.util.AppLogger.d(
                            "MemoryScreen",
                            "Box selection button clicked. Current mode: ${uiState.isBoxSelectionMode}, toggling to ${!uiState.isBoxSelectionMode}"
                        )
                        viewModel.toggleBoxSelectionMode(!uiState.isBoxSelectionMode)
                    },
                    containerColor = if (uiState.isBoxSelectionMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.library_select_nodes))
                }

                FloatingActionButton(
                    onClick = {
                        com.ai.assistance.operit.util.AppLogger.d(
                            "MemoryScreen",
                            "Linking button clicked. Current mode: ${uiState.isLinkingMode}, toggling to ${!uiState.isLinkingMode}"
                        )
                        viewModel.toggleLinkingMode(!uiState.isLinkingMode)
                    },
                    containerColor = if (uiState.isLinkingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = stringResource(R.string.library_link_nodes))
                }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            MemoryLibraryContent(
                state = uiState,
                viewModel = viewModel,
                spaceName = profileNameMap[selectedProfileId] ?: selectedProfileId,
                onFolders = { keyboardController?.hide(); showFolderNavigator = true },
                isImporting = isImporting,
                onImport = {
                    importProfileId = selectedProfileId
                    importFolderPath = uiState.selectedFolderPath
                    filePickerLauncher.launch(arrayOf("text/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                }
            )
            if (showFolderNavigator && isCurrentScreen) {
                KiyoriModalBottomDrawer(
                    onDismissRequest = { showFolderNavigator = false },
                ) { dismissDrawer ->
                FolderNavigator(
                    folderPaths = uiState.folderPaths,
                    selectedFolderPath = uiState.selectedFolderPath,
                    // 先结束共享退出动画再卸载，目录状态仍由同一 ViewModel 持有。
                    onFolderSelected = { folderPath -> viewModel.selectFolder(folderPath); dismissDrawer() },
                    onFolderRename = { oldPath, newPath ->
                        viewModel.renameFolder(
                            oldPath,
                            newPath
                        )
                    },
                    onFolderDelete = { folderPath -> viewModel.deleteFolder(folderPath) },
                    onFolderCreate = { folderPath -> viewModel.createFolder(folderPath) },
                    isBusy = spaceBusy || uiState.isSaving || isImporting,
                    isLoading = uiState.isLoading,
                    error = spaceError ?: uiState.error,
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { id ->
                        manageSpace {
                            preferencesManager.setActiveMemorySpace(id)
                        }
                    },
                    onMemorySpaceCreate = { name ->
                        manageSpace {
                            val id = preferencesManager.createMemorySpace(name)
                            preferencesManager.setActiveMemorySpace(id)
                        }
                    },
                    onMemorySpaceRename = { id, name ->
                        manageSpace {
                            val space = preferencesManager.getMemorySpaceFlow(id).first()
                            preferencesManager.updateMemorySpace(space.copy(name = name))
                            profileNameMap[id] = name
                        }
                    },
                    onMemorySpaceDelete = { id ->
                        manageSpace {
                            preferencesManager.deleteMemorySpace(id)
                            profileNameMap.remove(id)
                        }
                    },
                    onDismissRequest = dismissDrawer
                )
                }
            }

            // 对话框层
            if (isCurrentScreen) {
            if (uiState.isSearchSettingsDialogVisible) {
                MemoryLibrarySettingsSheet(
                    currentConfig = uiState.searchConfig,
                    autoSaveIntervalMinutes = uiState.autoSaveIntervalMinutes,
                    memoryExtractionCustomRules = uiState.memoryExtractionCustomRules,
                    cloudConfig = uiState.cloudEmbeddingConfig,
                    dimensionUsage = uiState.embeddingDimensionUsage,
                    rebuildProgress = uiState.embeddingRebuildProgress,
                    error = uiState.embeddingUsageError ?: uiState.error,
                    isUsageLoading = uiState.isEmbeddingUsageLoading,
                    isSaving = uiState.isSaving,
                    onRetryUsage = viewModel::refreshEmbeddingDimensionUsage,
                    isRebuilding = uiState.isEmbeddingRebuildRunning,
                    onDismiss = { viewModel.showSearchSettingsDialog(false) },
                    onSave = {
                            config,
                            cloudConfig,
                            autoSaveIntervalMinutes,
                            memoryExtractionCustomRules ->
                        viewModel.saveSearchSettings(
                            config,
                            cloudConfig,
                            autoSaveIntervalMinutes,
                            memoryExtractionCustomRules
                        )
                    },
                    onRebuild = { viewModel.rebuildVectorIndex() },
                    onSimulateSearch = { viewModel.openSearchSimulationDialog() }
                )
            }

            if (uiState.isSearchSimulationDialogVisible) {
                MemorySearchSimulationDialog(
                    query = uiState.searchSimulationQuery,
                    isRunning = uiState.isSearchSimulationRunning,
                    result = uiState.searchSimulationResult,
                    error = uiState.searchSimulationError,
                    onQueryChange = { viewModel.onSearchSimulationQueryChange(it) },
                    onRun = { viewModel.runSearchSimulation() },
                    onDismiss = { viewModel.showSearchSimulationDialog(false) }
                )
            }

            if (uiState.isDocumentViewOpen && uiState.selectedMemory != null) {
                var memoryTitle by remember(uiState.selectedMemory?.id) { mutableStateOf(uiState.selectedMemory!!.title) }
                val chunkStates = remember(uiState.selectedMemory?.id) {
                    mutableStateMapOf<Long, String>().apply {
                        uiState.selectedDocumentChunks.forEach { put(it.id, it.content) }
                    }
                }
                val originalChunks = remember(uiState.selectedMemory?.id) {
                    mutableStateMapOf<Long, String>().apply {
                        uiState.selectedDocumentChunks.forEach { put(it.id, it.content) }
                    }
                }
                // 当chunks列表变化时，同步状态
                LaunchedEffect(uiState.selectedDocumentChunks) {
                    uiState.selectedDocumentChunks.forEach { chunk ->
                        if (chunk.id !in chunkStates) chunkStates[chunk.id] = chunk.content
                        if (chunk.id !in originalChunks) originalChunks[chunk.id] = chunk.content
                    }
                }

                DocumentViewDialog(
                    memoryTitle = memoryTitle,
                    onTitleChange = { memoryTitle = it },
                    chunks = uiState.selectedDocumentChunks,
                    chunkStates = chunkStates,
                    onChunkChange = { id, content -> chunkStates[id] = content },
                    searchQuery = uiState.documentSearchQuery,
                    onSearchQueryChange = { viewModel.onDocumentSearchQueryChange(it) },
                    onPerformSearch = { viewModel.performSearchInDocument() },
                    onDismiss = { viewModel.closeDocumentView() },
                    isSaving = uiState.isSaving,
                    error = uiState.error,
                    onSave = { viewModel.saveDocument(memoryTitle, chunkStates.toMap()) },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    onArchive = { viewModel.archiveMemory(uiState.selectedMemory!!) },
                    archived = uiState.selectedMemory!!.archived,
                    isDirty = memoryTitle != uiState.selectedMemory!!.title || chunkStates.any { (id, content) -> originalChunks[id] != content },
                    folderPath = uiState.selectedMemory?.folderPath ?: ""
                )
            } else if (uiState.selectedMemory != null) {
                MemoryInfoDialog(
                    memory = uiState.selectedMemory!!,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditing(uiState.selectedMemory)
                        viewModel.clearSelection() // 关闭当前对话框
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    onArchive = { viewModel.archiveMemory(uiState.selectedMemory!!) },
                    isSaving = uiState.isSaving,
                    error = uiState.error,
                    cloudConfig = uiState.cloudEmbeddingConfig
                )
            }

            val selectedEdge = uiState.selectedEdge
            if (selectedEdge != null) {
                EdgeInfoDialog(
                    edge = selectedEdge,
                    graph = uiState.graph,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditingEdge(selectedEdge)
                        viewModel.clearSelection() // 同样, 点击编辑后关闭
                    },
                    onDelete = { viewModel.deleteEdge(selectedEdge.id) }
                )
            }

            if (uiState.linkingNodeIds.size == 2) {
                val sourceNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[0] }
                val targetNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[1] }
                if (sourceNode != null && targetNode != null) {
                    LinkMemoryDialog(
                        sourceNodeLabel = sourceNode.label,
                        targetNodeLabel = targetNode.label,
                        onDismiss = { viewModel.toggleLinkingMode(false) },
                        onLink = { type, weight, description ->
                            viewModel.linkMemories(
                                sourceNode.id,
                                targetNode.id,
                                type,
                                weight,
                                description
                            )
                        }
                    )
                }
            }

            if (uiState.isEditing) {
                EditMemoryDialog(
                    memory = uiState.editingMemory,
                    initialFolderPath = uiState.selectedFolderPath,
                    libraryKind = uiState.libraryKind,
                    allFolderPaths = uiState.folderPaths,
                    isSaving = uiState.isSaving,
                    error = uiState.error,
                    onDismiss = { viewModel.cancelEditing() },
                    onSave = { memory, title, content, contentType, source, credibility, importance, folderPath, tags, category ->
                        if (memory == null) {
                            // 创建新记忆的逻辑（如果需要的话）
                             viewModel.createMemory(title, content, contentType, source, credibility, importance, folderPath, tags, category)
                        } else {
                            viewModel.updateMemory(
                                memory = memory,
                                newTitle = title,
                                newContent = content,
                                newContentType = contentType,
                                newSource = source,
                                newCredibility = credibility,
                                newImportance = importance,
                                newFolderPath = folderPath,
                                newTags = tags,
                                newCategory = category
                            )
                        }
                    }
                )
            }

            val editingEdge = uiState.editingEdge
            if (uiState.isEditingEdge && editingEdge != null) {
                EditEdgeDialog(
                    edge = editingEdge,
                    onDismiss = { viewModel.cancelEditingEdge() },
                    onSave = { type, weight, description ->
                        viewModel.updateEdge(editingEdge, type, weight, description)
                    }
                )
            }

            if (uiState.showBatchDeleteConfirm) {
                BatchDeleteConfirmDialog(
                    selectedCount = uiState.boxSelectedNodeIds.size,
                    onDismiss = { viewModel.dismissBatchDeleteConfirm() },
                    onConfirm = { viewModel.deleteSelectedNodes() }
                )
            }
            }
        }
    }
}
