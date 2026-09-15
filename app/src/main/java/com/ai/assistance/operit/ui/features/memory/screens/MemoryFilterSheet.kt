package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MemoryDiaryPolicy
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.kiyori.design.theme.KiyoriUiShapes
import com.ai.assistance.operit.ui.components.KiyoriDrawerScaffold
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MemoryFilterSheet(state: MemoryUiState, viewModel: MemoryViewModel, onDismiss: () -> Unit) {
    var tagQuery by remember { mutableStateOf("") }
    val diary = state.libraryKind == MemoryLibraryPolicy.DIARY
    val tagOptions = remember(state.availableTags, state.tagFilter) {
        // 已选标签在当前查询变成空结果时仍可查看和取消，不能突然从选择器消失。
        (state.availableTags + listOfNotNull(state.tagFilter)).distinct().sorted()
    }
    val matchingTags = remember(tagOptions, tagQuery) { tagOptions.filter { it.contains(tagQuery, ignoreCase = true) } }
    val title = stringResource(R.string.library_organize)
    KiyoriModalBottomDrawer(onDismissRequest = onDismiss, modifier = Modifier.semantics { paneTitle = title }) { dismissDrawer ->
        KiyoriDrawerScaffold(
            title = title,
            onClose = dismissDrawer,
            scrollableContent = false,
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.resetOrganizationFilters(); tagQuery = "" }) { Text(stringResource(R.string.library_reset_filters)) }
                    Button(onClick = dismissDrawer) { Text(stringResource(R.string.library_done)) }
                }
            },
        ) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.library_sort), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !state.sortByTitle, onClick = { viewModel.setSortByTitle(false) }, label = { Text(stringResource(if (state.appliedSearchQuery.isBlank()) R.string.library_sort_recent else R.string.library_sort_relevance)) })
                        FilterChip(selected = state.sortByTitle, onClick = { viewModel.setSortByTitle(true) }, label = { Text(stringResource(R.string.library_sort_title)) })
                    }
                }
                // 主题是记忆的分类语义；日记按推进状态筛选，知识按主题归类仍然成立。
                // 给日记也列一遍“偏好/事实/决策”只会提供一组永远筛不出东西的条件。
                if (!diary) item {
                    Text(stringResource(R.string.library_category), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.categoryFilter == null, onClick = { viewModel.setCategoryFilter(null) }, label = { Text(stringResource(R.string.library_all_categories)) })
                        MemoryLibraryPolicy.categories.forEach { category ->
                            FilterChip(selected = state.categoryFilter == category, onClick = { viewModel.setCategoryFilter(category) }, label = { Text(memoryCategoryLabel(category)) })
                        }
                    }
                }
                if (diary) item {
                    Text(stringResource(R.string.library_diary_status), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.diaryStatusFilter == null, onClick = { viewModel.setDiaryStatusFilter(null) },
                            label = { Text(stringResource(R.string.library_diary_status_all)) })
                        listOf(MemoryDiaryPolicy.STATUS_ACTIVE, MemoryDiaryPolicy.STATUS_CLOSED).forEach { status ->
                            FilterChip(selected = state.diaryStatusFilter == status, onClick = { viewModel.setDiaryStatusFilter(status) },
                                label = { Text(diaryStatusLabel(status)) })
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
                if (tagOptions.size > 8 || tagQuery.isNotEmpty()) item {
                    OutlinedTextField(value = tagQuery, onValueChange = { tagQuery = it }, placeholder = { Text(stringResource(R.string.library_find_tag)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = KiyoriUiShapes.field)
                }
                item { TagChoice(stringResource(R.string.library_all_tags), state.tagFilter == null) { viewModel.setTagFilter(null) } }
                items(matchingTags, key = { it }) { tag ->
                    TagChoice(tag, state.tagFilter == tag) { viewModel.setTagFilter(tag) }
                }
                if (matchingTags.isEmpty()) item {
                    Text(stringResource(if (tagQuery.isBlank()) R.string.library_no_tags else R.string.library_no_results),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
