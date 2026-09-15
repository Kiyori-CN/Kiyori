package com.ai.assistance.operit.ui.features.memory.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.rememberDelayedLoading
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryFolderBrowsePolicy
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.kiyori.design.theme.KiyoriUiShapes
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.navigation.LocalTopBarActions
import java.text.DateFormat

@Composable
internal fun memoryCategoryLabel(category: String): String = stringResource(when (category) {
    "preference" -> R.string.library_category_preference
    "fact" -> R.string.library_category_fact
    "decision" -> R.string.library_category_decision
    "experience" -> R.string.library_category_experience
    "event" -> R.string.library_category_event
    else -> R.string.library_category_other
})

@Composable
internal fun memorySourceLabel(source: String): String = when {
    source.startsWith("chat:") -> stringResource(R.string.library_source_chat)
    source == "user_input" -> stringResource(R.string.library_source_manual)
    source == "document_import" -> stringResource(R.string.library_source_document)
    source.isBlank() -> stringResource(R.string.library_source_unknown)
    else -> source
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryLibraryContent(
    state: MemoryUiState, viewModel: MemoryViewModel, spaceName: String,
    onFolders: () -> Unit, onImport: () -> Unit, isImporting: Boolean = false
) {
    var showFilters by remember(viewModel) { mutableStateOf(false) }
    var addMenu by remember(viewModel) { mutableStateOf(false) }
    var showCreateFolder by remember(viewModel) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    LaunchedEffect(isCurrentScreen) {
        if (!isCurrentScreen) { showFilters = false; addMenu = false; showCreateFolder = false }
    }
    val setTopBarActions = LocalTopBarActions.current
    val latestState = rememberUpdatedState(state)
    val latestImporting = rememberUpdatedState(isImporting)
    // 壳按 screenKey 保存动作。闭包读取最新状态，空间切换重新绑定 ViewModel，避免旧空间响应点击。
    LaunchedEffect(isCurrentScreen, viewModel) {
        if (isCurrentScreen) setTopBarActions {
            MemoryTopBarActions(
                state = latestState.value,
                isImporting = latestImporting.value,
                onGraph = { keyboard?.hide(); viewModel.setGraphVisible(!latestState.value.showGraph) },
                onFilter = { keyboard?.hide(); showFilters = true },
                onSettings = { keyboard?.hide(); viewModel.showSearchSettingsDialog(true) },
                onRefresh = { keyboard?.hide(); viewModel.refresh() },
            )
        }
    }
    val showLoading = rememberDelayedLoading(state.isLoading)
    val knowledge = state.libraryKind == MemoryLibraryPolicy.KNOWLEDGE
    val pendingQuery = state.searchQuery.trim() != state.appliedSearchQuery
    val filterCount = listOf(state.showArchived, state.categoryFilter != null, state.tagFilter != null).count { it }
    // 文件夹本身是新建目标；空文件夹也必须提供添加入口，不能只允许重置位置。
    val filtered = state.appliedSearchQuery.isNotBlank() || filterCount > 0
    val onlyArchive = state.showArchived && filterCount == 1 && state.appliedSearchQuery.isBlank() && state.selectedFolderPath.isBlank()
    val busy = isImporting || state.isSaving
    val search = { keyboard?.hide(); viewModel.searchMemories() }
    val create = { viewModel.startEditing(null) }
    val primaryLabel = stringResource(if (knowledge) R.string.library_import else R.string.library_new_memory)
    // 搜索或筛选生效时展示整棵子树的平铺结果；否则逐级浏览当前目录。
    val browsingSearch = state.appliedSearchQuery.isNotBlank() || filterCount > 0
    // 目录层级耗尽才把 Back 交还给壳；弹层仍由各自的宿主先处理。
    BackHandler(enabled = isCurrentScreen && !state.showGraph && state.selectedFolderPath.isNotBlank() && !busy) {
        viewModel.selectFolder(MemoryFolderBrowsePolicy.parentOf(state.selectedFolderPath))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 空间入口与内容类型同排，长空间名省略；目录层级交给下方面包屑。
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 当前目录由下方面包屑负责，这里只表达“在哪个空间”，两处不再重复同一路径。
                TextButton(onClick = onFolders, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.Start) {
                        Text(stringResource(R.string.library_space), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(spaceName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                SingleChoiceSegmentedButtonRow(Modifier.weight(1.15f)) {
                    listOf(R.string.library_memories, R.string.library_knowledge).forEachIndexed { index, label ->
                        SegmentedButton(selected = knowledge == (index == 1),
                            onClick = { viewModel.setLibraryKind(if (index == 1) MemoryLibraryPolicy.KNOWLEDGE else MemoryLibraryPolicy.MEMORY) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2), enabled = !busy,
                            icon = {}
                        ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            TextField(value = state.searchQuery, onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.library_search), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                textStyle = MaterialTheme.typography.bodyMedium, shape = KiyoriUiShapes.field,
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                singleLine = true,
                leadingIcon = { IconButton(onClick = search) { Icon(Icons.Outlined.Search, stringResource(R.string.library_search_action)) } },
                trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = {
                    viewModel.onSearchQueryChange(""); viewModel.searchMemories()
                }) { Icon(Icons.Outlined.Close, stringResource(R.string.library_clear_search)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }))
            if (!state.showGraph) MemoryFolderBreadcrumb(state.selectedFolderPath, enabled = !busy) { viewModel.selectFolder(it) }
            if (filterCount > 0) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.showArchived) item { ActiveFilter(stringResource(R.string.library_archived)) { viewModel.setArchivedFilter(false) } }
                state.categoryFilter?.let { category -> item { ActiveFilter(memoryCategoryLabel(category)) { viewModel.setCategoryFilter(null) } } }
                state.tagFilter?.let { tag -> item { ActiveFilter("# $tag") { viewModel.setTagFilter(null) } } }
            }
            // 加载状态保留用于禁用操作，视觉反馈延迟展示；快速本地刷新不闪烁。
            // 进度位于固定层，不插入列表布局，已有内容保持阅读位置。
            if (!isImporting && state.canRetryImport) {
                Text(state.importFailures.joinToString("\n"), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3)
                TextButton(onClick = viewModel::retryImport) { Text(stringResource(R.string.library_import_retry_failed)) }
            }
            when {
                isImporting -> LibraryEmptyState(R.string.library_importing, message = state.importProgress, knowledge = true) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.cancel_action)) }
                }
                pendingQuery -> LibraryEmptyState(R.string.library_search_ready, R.string.library_search_ready_hint, search = true) {
                    Button(onClick = search) { Text(stringResource(R.string.library_search_action)) }
                }
                state.error != null -> LibraryEmptyState(R.string.library_load_error, message = state.error, error = true) {
                    Button(onClick = search, enabled = !state.isLoading) { Text(stringResource(R.string.library_retry)) }
                }
                state.isLoading && (state.memories.isEmpty() || state.showGraph) -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showLoading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                // 空目录不能吞掉整页：当前位置没有条目但仍有子目录时，逐级浏览必须留在列表里。
                state.memories.isEmpty() && (state.showGraph || browsingSearch || state.folderPaths.isEmpty()) -> LibraryEmptyState(
                    if (onlyArchive) R.string.library_archive_empty else if (filtered) R.string.library_no_results else if (knowledge) R.string.library_knowledge_empty else R.string.library_empty,
                    if (onlyArchive) R.string.library_archive_hint else if (filtered) R.string.library_filter_hint else if (knowledge) R.string.library_knowledge_hint else R.string.library_memory_hint,
                    knowledge = knowledge
                ) {
                    if (filtered) OutlinedButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.library_reset_filters)) }
                    else {
                        Button(onClick = if (knowledge) onImport else create, enabled = !busy) {
                            Icon(if (knowledge) Icons.Outlined.UploadFile else Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(primaryLabel)
                        }
                        if (knowledge) TextButton(onClick = create, enabled = !busy) { Text(stringResource(R.string.library_new_note)) }
                    }
                }
                state.showGraph -> {
                    Text(stringResource(R.string.library_graph_limit), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GraphVisualizer(graph = state.graph, modifier = Modifier.weight(1f).fillMaxWidth(),
                        selectedNodeId = state.selectedNodeId, boxSelectedNodeIds = state.boxSelectedNodeIds,
                        isBoxSelectionMode = state.isBoxSelectionMode, linkingNodeIds = state.linkingNodeIds,
                        selectedEdgeId = state.selectedEdge?.id, onNodeClick = viewModel::selectNode,
                        onEdgeClick = viewModel::selectEdge, onNodesSelected = viewModel::addNodesToSelection)
                }
                else -> {
                    val locale = LocalConfiguration.current.locales[0]
                    val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
                    MemoryFolderList(state, viewModel, dateFormat, searching = browsingSearch,
                        onCreateFolder = { showCreateFolder = true })
                }
            }
        }
        if (showLoading && state.memories.isNotEmpty() && !state.showGraph) {
            CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(16.dp).size(24.dp), strokeWidth = 2.dp)
        }
        if ((state.memories.isNotEmpty() || state.folderPaths.isNotEmpty()) && !state.showGraph && !pendingQuery && state.error == null && !busy) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                ExtendedFloatingActionButton(onClick = { addMenu = true },
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.library_add)) })
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    if (knowledge) DropdownMenuItem(text = { Text(stringResource(R.string.library_import)) }, leadingIcon = { Icon(Icons.Outlined.UploadFile, null) }, onClick = { addMenu = false; onImport() })
                    DropdownMenuItem(text = { Text(stringResource(if (knowledge) R.string.library_new_note else R.string.library_new_memory)) },
                        leadingIcon = { Icon(if (knowledge) Icons.Outlined.EditNote else Icons.Outlined.Add, null) }, onClick = { addMenu = false; create() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.foldernav_new_folder)) },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { addMenu = false; showCreateFolder = true })
                }
            }
        }
    }
    if (showFilters && isCurrentScreen) MemoryFilterSheet(state, viewModel) { showFilters = false }
    if (showCreateFolder && isCurrentScreen) {
        FolderCreateDialog(
            parentPath = state.selectedFolderPath,
            folderPaths = state.folderPaths,
            onDismiss = { showCreateFolder = false },
            onCreate = { path -> viewModel.createFolder(path) },
        )
    }
}

@Composable
private fun ActiveFilter(label: String, onRemove: () -> Unit) {
    InputChip(selected = true, onClick = onRemove, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
        trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.library_remove_filter), Modifier.size(16.dp)) })
}

@Composable
private fun ColumnScope.LibraryEmptyState(
    title: Int, hint: Int? = null, message: String? = null,
    knowledge: Boolean = false, search: Boolean = false, error: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                Icon(when { error -> Icons.Outlined.ErrorOutline; search -> Icons.Outlined.Search; knowledge -> Icons.AutoMirrored.Outlined.MenuBook; else -> Icons.Outlined.Psychology },
                    null, Modifier.padding(18.dp).size(28.dp), tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(message ?: hint?.let { stringResource(it) }.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            actions()
        }
    }
}

@Composable
internal fun MemoryLibraryCard(memory: Memory, dateFormat: DateFormat, showFolder: Boolean = true, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = KiyoriUiShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(memory.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (memory.isDocumentNode) Icon(Icons.Outlined.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(memory.content.take(400), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(listOf(memoryCategoryLabel(MemoryLibraryPolicy.category(memory)), memorySourceLabel(memory.source), dateFormat.format(memory.updatedAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tags = memory.tags.map { it.name }.take(3)
            if (tags.isNotEmpty()) Text(tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 逐级浏览时所在目录就是当前位置，重复展示只会占掉摘要空间；跨目录结果才需要标出归属。
            if (showFolder && !memory.folderPath.isNullOrBlank()) Text(memory.folderPath.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
