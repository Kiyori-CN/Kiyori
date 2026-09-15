package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.KiyoriActionRole
import com.ai.assistance.operit.ui.components.KiyoriToolbarAction
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState

@Composable
internal fun MemoryTopBarActions(
    state: MemoryUiState, isImporting: Boolean, onGraph: () -> Unit,
    onFilter: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit,
) {
    val enabled = !isImporting && !state.isSaving
    val hasFilters = state.showArchived || state.categoryFilter != null || state.tagFilter != null ||
        state.diaryStatusFilter != null
    KiyoriToolbarAction(Icons.Outlined.Settings, stringResource(R.string.library_settings),
        KiyoriActionRole.CONFIGURE, enabled, onClick = onSettings)
    KiyoriToolbarAction(Icons.Outlined.Tune, stringResource(R.string.library_filter),
        KiyoriActionRole.ORGANIZE, enabled, selected = hasFilters || state.sortByTitle,
        badge = hasFilters, onClick = onFilter)
    KiyoriToolbarAction(if (state.showGraph) Icons.Outlined.FolderOpen else Icons.Outlined.AccountTree,
        stringResource(if (state.showGraph) R.string.library_folders else R.string.library_graph),
        KiyoriActionRole.NAVIGATE, enabled, selected = state.showGraph, onClick = onGraph)
    KiyoriToolbarAction(Icons.Outlined.Refresh, stringResource(R.string.library_refresh),
        KiyoriActionRole.EXECUTE, enabled && !state.isLoading, onClick = onRefresh)
}
