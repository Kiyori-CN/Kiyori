package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryUiState
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.rememberKiyoriUiTokens
import com.kiyori.design.theme.resolveColors

/** 与 AI 对话共用壳顶栏、40dp 触控尺寸、默认 24dp 图标与语义色，不另设页面工具栏。 */
@Composable
internal fun MemoryTopBarActions(
    state: MemoryUiState,
    isImporting: Boolean,
    onGraph: () -> Unit,
    onFilter: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val enabled = !isImporting && !state.isSaving
    val hasFilters = state.showArchived || state.categoryFilter != null || state.tagFilter != null
    MemoryTopBarAction(Icons.Outlined.AccountTree,
        stringResource(if (state.showGraph) R.string.library_list else R.string.library_graph),
        KiyoriSemanticTone.BLUE, enabled, selected = state.showGraph, onClick = onGraph)
    MemoryTopBarAction(Icons.Outlined.Tune, stringResource(R.string.library_filter),
        KiyoriSemanticTone.ORANGE, enabled, selected = hasFilters || state.sortByTitle,
        badge = hasFilters, onClick = onFilter)
    MemoryTopBarAction(Icons.Outlined.Settings, stringResource(R.string.library_settings),
        KiyoriSemanticTone.GREEN, enabled, onClick = onSettings)
    MemoryTopBarAction(Icons.Outlined.Refresh, stringResource(R.string.library_refresh),
        KiyoriSemanticTone.PURPLE, enabled && !state.isLoading, onClick = onRefresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryTopBarAction(
    icon: ImageVector,
    label: String,
    tone: KiyoriSemanticTone,
    enabled: Boolean,
    selected: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = tone.resolveColors().icon
    val tokens = rememberKiyoriUiTokens()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(tokens.touchTarget).clip(KiyoriUiShapes.control)
                .semantics { this.selected = selected },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                contentColor = ink,
                disabledContentColor = ink.copy(alpha = 0.38f),
            ),
        ) {
            BadgedBox(badge = { if (badge) Badge() }) { Icon(icon, contentDescription = label) }
        }
    }
}
