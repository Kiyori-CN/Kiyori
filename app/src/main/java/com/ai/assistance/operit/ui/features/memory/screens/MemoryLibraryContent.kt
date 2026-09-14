package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryLibraryContent(
    state: MemoryUiState,
    viewModel: MemoryViewModel,
    spaceName: String,
    onFolders: () -> Unit,
    onImport: () -> Unit
) {
    var organizeMenu by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = state.searchQuery.isNotBlank() || state.showArchived || state.categoryFilter != null || state.tagFilter != null || state.selectedFolderPath.isNotBlank()
    val knowledge = state.libraryKind == MemoryLibraryPolicy.KNOWLEDGE
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onFolders, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(spaceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            IconButton(onClick = { viewModel.loadMemoryGraph() }, enabled = !state.isLoading) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.library_refresh))
            }
            IconButton(onClick = { viewModel.showSearchSettingsDialog(true) }) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.library_settings))
            }
        }
        PrimaryTabRow(selectedTabIndex = if (knowledge) 1 else 0) {
            Tab(selected = !knowledge, onClick = { viewModel.setLibraryKind(MemoryLibraryPolicy.MEMORY) }, text = { Text(stringResource(R.string.library_memories)) })
            Tab(selected = knowledge, onClick = { viewModel.setLibraryKind(MemoryLibraryPolicy.KNOWLEDGE) }, text = { Text(stringResource(R.string.library_knowledge)) })
        }
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) IconButton(onClick = {
                    viewModel.onSearchQueryChange("")
                    viewModel.searchMemories()
                }) { Icon(Icons.Outlined.Close, stringResource(R.string.library_clear_search)) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); viewModel.searchMemories() })
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.selectedFolderPath.ifBlank { stringResource(R.string.library_all_folders) },
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { viewModel.searchMemories() }, enabled = !state.isLoading) { Text(stringResource(R.string.library_search_action)) }
            Box {
                IconButton(onClick = { organizeMenu = true }) { Icon(Icons.Outlined.Tune, stringResource(R.string.library_organize)) }
                DropdownMenu(expanded = organizeMenu, onDismissRequest = { organizeMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.library_all_categories)) }, onClick = { viewModel.setCategoryFilter(null); organizeMenu = false })
                    MemoryLibraryPolicy.categories.forEach { category ->
                        DropdownMenuItem(text = { Text(memoryCategoryLabel(category)) }, onClick = { viewModel.setCategoryFilter(category); organizeMenu = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(if (state.sortByTitle) R.string.library_sort_recent else R.string.library_sort_title)) }, onClick = { viewModel.setSortByTitle(!state.sortByTitle); organizeMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.library_all_tags)) }, onClick = { viewModel.setTagFilter(null); organizeMenu = false })
                    state.availableTags.forEach { tag ->
                        DropdownMenuItem(text = { Text("# $tag") }, onClick = { viewModel.setTagFilter(tag); organizeMenu = false })
                    }
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.showArchived, onClick = { viewModel.setArchivedFilter(!state.showArchived) }, label = { Text(stringResource(R.string.library_archived)) }) }
            item { FilterChip(selected = state.showGraph, onClick = { viewModel.setGraphVisible(!state.showGraph) }, label = { Text(stringResource(R.string.library_graph)) }) }
            if (filtered) item { InputChip(selected = false, onClick = viewModel::resetFilters, label = { Text(stringResource(R.string.library_reset_filters)) }) }
            state.categoryFilter?.let { category -> item { InputChip(selected = true, onClick = { viewModel.setCategoryFilter(null) }, label = { Text(memoryCategoryLabel(category)) }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) }) } }
            state.tagFilter?.let { tag -> item { InputChip(selected = true, onClick = { viewModel.setTagFilter(null) }, label = { Text("# $tag") }, trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) }) } }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.searchMemories() }) { Text(stringResource(R.string.library_retry)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library_count, state.memories.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            if (knowledge) TextButton(onClick = onImport, enabled = !state.isLoading) {
                Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.library_import))
            }
            TextButton(onClick = { viewModel.startEditing(null) }, enabled = !state.isLoading) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(if (knowledge) R.string.library_new_note else R.string.library_new_memory))
            }
        }
        if (state.showGraph) {
            Text(stringResource(R.string.library_graph_limit), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
            GraphVisualizer(
                graph = state.graph, modifier = Modifier.weight(1f).fillMaxWidth(),
                selectedNodeId = state.selectedNodeId, boxSelectedNodeIds = state.boxSelectedNodeIds,
                isBoxSelectionMode = state.isBoxSelectionMode, linkingNodeIds = state.linkingNodeIds,
                selectedEdgeId = state.selectedEdge?.id, onNodeClick = viewModel::selectNode,
                onEdgeClick = viewModel::selectEdge, onNodesSelected = viewModel::addNodesToSelection
            )
        } else if (state.memories.isEmpty() && !state.isLoading && state.error == null) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (knowledge) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Psychology, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(if (filtered) R.string.library_no_results else R.string.library_empty), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (filtered) R.string.library_filter_hint else if (knowledge) R.string.library_knowledge_hint else R.string.library_memory_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (filtered) OutlinedButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.library_reset_filters)) }
            }
        } else {
            val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.memories, key = { it.id }) { memory ->
                    OutlinedCard(onClick = { viewModel.selectMemory(memory) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(memory.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(memory.content.take(400), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(memoryCategoryLabel(MemoryLibraryPolicy.category(memory)) + " · " + dateFormat.format(memory.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.memory_source) + ": " + when {
                                memory.source.startsWith("chat:") -> stringResource(R.string.library_source_chat)
                                memory.source == "user_input" -> stringResource(R.string.library_source_manual)
                                memory.source == "document_import" -> stringResource(R.string.library_source_document)
                                else -> memory.source
                            }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!memory.folderPath.isNullOrBlank()) Text(memory.folderPath.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val tags = remember(memory) { memory.tags.map { it.name }.take(5) }
                            if (tags.isNotEmpty()) Text(tags.joinToString("  ") { "# $it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val indexed = remember(memory.embeddingModelKey, memory.embeddingContentHash, memory.updatedAt, state.cloudEmbeddingConfig) {
                                MemoryLibraryPolicy.compatible(memory.embedding, memory.embeddingModelKey, memory.embeddingContentHash, state.cloudEmbeddingConfig, "${memory.title}\n${memory.content}")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (!state.cloudEmbeddingConfig.enabled) R.string.library_index_local else if (indexed) R.string.library_index_ready else R.string.library_index_pending), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { viewModel.archiveMemory(memory) }, enabled = !state.isSaving) { Text(stringResource(if (memory.archived) R.string.library_restore else R.string.library_archive)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
