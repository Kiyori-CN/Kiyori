package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.kiyori.design.theme.KiyoriUiShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MemoryFilterSheet(state: MemoryUiState, viewModel: MemoryViewModel, onDismiss: () -> Unit) {
    var tagQuery by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = KiyoriUiShapes.sheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.library_organize), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.memory_close)) }
            }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.library_sort), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !state.sortByTitle, onClick = { viewModel.setSortByTitle(false) }, label = { Text(stringResource(if (state.appliedSearchQuery.isBlank()) R.string.library_sort_recent else R.string.library_sort_relevance)) })
                        FilterChip(selected = state.sortByTitle, onClick = { viewModel.setSortByTitle(true) }, label = { Text(stringResource(R.string.library_sort_title)) })
                    }
                }
                item {
                    Text(stringResource(R.string.library_category), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.categoryFilter == null, onClick = { viewModel.setCategoryFilter(null) }, label = { Text(stringResource(R.string.library_all_categories)) })
                        MemoryLibraryPolicy.categories.forEach { category ->
                            FilterChip(selected = state.categoryFilter == category, onClick = { viewModel.setCategoryFilter(category) }, label = { Text(memoryCategoryLabel(category)) })
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().selectable(selected = state.showArchived, role = Role.Switch, onClick = { viewModel.setArchivedFilter(!state.showArchived) }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(stringResource(R.string.library_archive_only), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.library_archive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.showArchived, onCheckedChange = null)
                    }
                    HorizontalDivider()
                }
                item { Text(stringResource(R.string.memory_tags), style = MaterialTheme.typography.titleSmall) }
                if (state.availableTags.size > 8) item {
                    OutlinedTextField(value = tagQuery, onValueChange = { tagQuery = it }, placeholder = { Text(stringResource(R.string.library_find_tag)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = KiyoriUiShapes.field)
                }
                item { TagChoice(stringResource(R.string.library_all_tags), state.tagFilter == null) { viewModel.setTagFilter(null) } }
                items(state.availableTags.filter { it.contains(tagQuery, ignoreCase = true) }, key = { it }) { tag ->
                    TagChoice(tag, state.tagFilter == tag) { viewModel.setTagFilter(tag) }
                }
                if (state.availableTags.isEmpty()) item { Text(stringResource(R.string.library_no_tags), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.resetFilters(); tagQuery = "" }) { Text(stringResource(R.string.library_reset_filters)) }
                Button(onClick = onDismiss) { Text(stringResource(R.string.library_done)) }
            }
        }
    }
}

@Composable
private fun TagChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
