package com.ai.assistance.operit.ui.features.memory.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SelectAll

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import android.provider.OpenableColumns
import android.widget.Toast
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Folder

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSettingsDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSimulationDialog
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen

@Composable
fun MemorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Default.Folder, 
                contentDescription = "Toggle Folders",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.memory_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                onSearch()
            })
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.memory_search_settings_title),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

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

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getMemorySpaceFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var showFolderNavigator by remember { mutableStateOf(false) }

    LaunchedEffect(activeProfileId) { selectedProfileId = activeProfileId }

    val viewModel: MemoryViewModel =
        viewModel(
            key = selectedProfileId, // Recreate ViewModel when profile changes
            factory = MemoryViewModelFactory(context, selectedProfileId)
        )
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isCurrentScreen = LocalIsCurrentScreen.current

    LaunchedEffect(isCurrentScreen, selectedProfileId) {
        if (isCurrentScreen) {
            viewModel.loadMemoryGraph()
            viewModel.loadFolderPaths()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { fileUri ->
                scope.launch {
                    var tempFile: File? = null
                    try {
                        // More robust file name extraction
                        val (fileName, mimeType) = withContext(Dispatchers.IO) {
                            // Execute ContentResolver operations on IO thread
                            var extractedFileName = "Untitled"
                            context.contentResolver.query(fileUri, null, null, null, null)
                                ?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val displayNameIndex =
                                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                        if (displayNameIndex != -1) {
                                            extractedFileName = cursor.getString(displayNameIndex)
                                        }
                                    }
                                }

                            val extractedMimeType = context.contentResolver.getType(fileUri)
                            Pair(extractedFileName, extractedMimeType)
                        }

                        if (mimeType != null && mimeType.startsWith("text")) {
                            val content = withContext(Dispatchers.IO) {
                                val inputStream =
                                    context.contentResolver.openInputStream(fileUri)
                                        ?: throw IOException("ContentResolver returned no input stream")
                                inputStream.bufferedReader().use { reader ->
                                    val buffer = CharArray(8192)
                                    val text = StringBuilder()
                                    while (true) {
                                        val count = reader.read(buffer)
                                        if (count < 0) break
                                        require(text.length + count <= 2_000_000) { "文档超过 200 万字符，请拆分后导入" }
                                        text.append(buffer, 0, count)
                                    }
                                    text.toString() }
                            }
                            viewModel.importDocument(fileName, fileUri.toString(), content)
                        } else {
                            // For binary files, use the tool
                            val extension =
                                fileName
                                    .substringAfterLast('.', missingDelimiterValue = "")
                                    .filter(Char::isLetterOrDigit)
                                    .take(12)
                            val tempFileName =
                                buildString {
                                    append("memory_import_")
                                    append(UUID.randomUUID())
                                    if (extension.isNotEmpty()) {
                                        append('.')
                                        append(extension)
                                    }
                                }
                            tempFile = File(context.cacheDir, tempFileName)
                            withContext(Dispatchers.IO) {
                                val inputStream =
                                    context.contentResolver.openInputStream(fileUri)
                                        ?: throw IOException("ContentResolver returned no input stream")
                                inputStream.use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        val buffer = ByteArray(8192)
                                        var copied = 0L
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            copied += count
                                            require(copied <= 32L * 1024 * 1024) { "资料超过 32 MB，请拆分后导入" }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                            }

                            val result = withContext(Dispatchers.IO) {
                                val toolHandler = AIToolHandler.getInstance(context)
                                val tool = AITool(
                                    name = "read_file_full",
                                    parameters = listOf(ToolParameter("path", tempFile.absolutePath))
                                )
                                toolHandler.executeTool(tool)
                            }

                            if (result.success) {
                                // Assuming result.result can be cast to StringResultData
                                val resultData = result.result
                                val content = if (resultData is StringResultData) {
                                    resultData.value
                                } else {
                                    resultData.toString()
                                }
                                viewModel.importDocument(fileName, fileUri.toString(), content)
                            } else {
                                throw IOException("read_file_full failed: ${result.error}")
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (e: Exception) {
                        AppLogger.e("MemoryScreen", "Error processing file: $fileUri", e)
                        Toast.makeText(
                            context,
                            resources.getString(
                                R.string.memory_error_import_document,
                                e.message ?: e.javaClass.simpleName,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        tempFile?.let { file ->
                            if (file.exists() && !file.delete()) {
                                AppLogger.w(
                                    "MemoryScreen",
                                    "Unable to delete temporary import file: ${file.name}",
                                )
                            }
                        }
                    }
                }
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
                if (uiState.isBoxSelectionMode) {
                    FloatingActionButton(
                        onClick = { viewModel.showBatchDeleteConfirm() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete Selected")
                    }
                }

                if (uiState.showGraph) {
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
                    Icon(Icons.Default.SelectAll, contentDescription = "Toggle Box Selection Mode")
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
                    Icon(Icons.Default.Link, contentDescription = "Toggle Linking Mode")
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
                onFolders = { showFolderNavigator = true },
                onImport = {
                    filePickerLauncher.launch(arrayOf("text/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                }
            )
            BackHandler(enabled = isCurrentScreen && showFolderNavigator) { showFolderNavigator = false }
            if (showFolderNavigator) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)).clickable { showFolderNavigator = false })
            }
            // 左侧文件夹导航 (Overlay)
            AnimatedVisibility(
                visible = showFolderNavigator,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                FolderNavigator(
                    folderPaths = uiState.folderPaths,
                    selectedFolderPath = uiState.selectedFolderPath,
                    onFolderSelected = { folderPath -> viewModel.selectFolder(folderPath); showFolderNavigator = false },
                    onFolderRename = { oldPath, newPath ->
                        viewModel.renameFolder(
                            oldPath,
                            newPath
                        )
                    },
                    onFolderDelete = { folderPath -> viewModel.deleteFolder(folderPath) },
                    onFolderCreate = { folderPath -> viewModel.createFolder(folderPath) },
                    onRefresh = { viewModel.refreshFolderList() },
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { id ->
                        scope.launch {
                            preferencesManager.setActiveMemorySpace(id)
                            selectedProfileId = id
                        }
                    },
                    onMemorySpaceCreate = { name ->
                        scope.launch {
                            val id = preferencesManager.createMemorySpace(name)
                            preferencesManager.setActiveMemorySpace(id)
                            selectedProfileId = id
                        }
                    },
                    onMemorySpaceRename = { id, name ->
                        scope.launch {
                            val space = preferencesManager.getMemorySpaceFlow(id).first()
                            preferencesManager.updateMemorySpace(space.copy(name = name))
                            profileNameMap[id] = name
                        }
                    },
                    onMemorySpaceDelete = { id ->
                        scope.launch {
                            preferencesManager.deleteMemorySpace(id)
                            profileNameMap.remove(id)
                            selectedProfileId = "default"
                        }
                    },
                    onDismissRequest = { showFolderNavigator = false }
                )
            }

            // 对话框层
            if (uiState.isSearchSettingsDialogVisible) {
                MemorySearchSettingsDialog(
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
                // 当chunks列表变化时，同步状态
                LaunchedEffect(uiState.selectedDocumentChunks) {
                    uiState.selectedDocumentChunks.forEach { chunk ->
                        if (chunk.id !in chunkStates) chunkStates[chunk.id] = chunk.content
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
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) }
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
    }}
