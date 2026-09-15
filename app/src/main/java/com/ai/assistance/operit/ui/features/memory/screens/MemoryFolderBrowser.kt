package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryFolderBrowsePolicy
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.kiyori.design.theme.KiyoriUiShapes
import java.text.DateFormat

/**
 * 逐级浏览：面包屑固定在内容上方，返回上级与进入子目录使用同一份目录事实。
 * 搜索或筛选生效时改为整棵子树的平铺结果，与文件管理器的“在当前位置搜索”一致。
 */
@Composable
internal fun MemoryFolderBreadcrumb(
    currentPath: String,
    enabled: Boolean,
    onNavigate: (String) -> Unit,
) {
    val crumbs = remember(currentPath) { MemoryFolderBrowsePolicy.breadcrumb(currentPath) }
    val parent = remember(currentPath) { MemoryFolderBrowsePolicy.parentOf(currentPath) }
    val atRoot = crumbs.isEmpty()
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onNavigate(parent) }, enabled = enabled && !atRoot) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.library_folder_up), Modifier.size(20.dp))
        }
        LazyRow(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                CrumbChip(stringResource(R.string.library_folder_root), atRoot, enabled) { onNavigate("") }
            }
            items(crumbs, key = { it.path }) { crumb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CrumbChip(crumb.name, crumb.path == currentPath, enabled) { onNavigate(crumb.path) }
                }
            }
        }
    }
}

@Composable
private fun CrumbChip(label: String, current: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled && !current,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

@Composable
internal fun ColumnScope.MemoryFolderList(
    state: MemoryUiState,
    viewModel: MemoryViewModel,
    dateFormat: DateFormat,
    searching: Boolean,
    onCreateFolder: () -> Unit,
) {
    val childFolders = remember(state.folderPaths, state.selectedFolderPath) {
        MemoryFolderBrowsePolicy.childFolders(state.folderPaths, state.selectedFolderPath)
    }
    val entries = remember(state.memories, state.selectedFolderPath, searching) {
        if (searching) state.memories else MemoryFolderBrowsePolicy.directEntries(state.memories, state.selectedFolderPath)
    }
    val folders = if (searching) emptyList() else childFolders
    // 范围切换回到结果起点，单纯刷新数据保持阅读位置。
    key(state.libraryKind, state.appliedSearchQuery, state.selectedFolderPath, state.showArchived,
        state.categoryFilter, state.tagFilter, state.sortByTitle) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (searching) stringResource(R.string.library_count, entries.size)
                        else stringResource(R.string.library_folder_count, folders.size, entries.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            if (state.sortByTitle) R.string.library_sort_title
                            else if (state.appliedSearchQuery.isNotBlank()) R.string.library_sort_relevance
                            else R.string.library_sort_recent
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(folders, key = { "folder:$it" }) { path ->
                FolderRow(
                    path = path,
                    count = MemoryFolderBrowsePolicy.subtreeCount(state.memories, path),
                    onOpen = { viewModel.selectFolder(path) },
                )
            }
            items(entries, key = { it.id }) { memory ->
                MemoryLibraryCard(memory, dateFormat, showFolder = searching) { viewModel.selectMemory(memory) }
            }
            if (folders.isEmpty() && entries.isEmpty()) item {
                EmptyFolderNotice(searching = searching, onCreateFolder = onCreateFolder)
            }
        }
    }
}

@Composable
private fun FolderRow(path: String, count: Int, onOpen: () -> Unit) {
    Card(
        onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = KiyoriUiShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                path.substringAfterLast('/'), Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.library_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyFolderNotice(searching: Boolean, onCreateFolder: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (searching) Icons.Outlined.Home else Icons.Outlined.Folder, null,
            Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(if (searching) R.string.library_no_results else R.string.library_folder_empty),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(if (searching) R.string.library_filter_hint else R.string.library_folder_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!searching) TextButton(onClick = onCreateFolder) { Text(stringResource(R.string.foldernav_new_folder)) }
    }
}
