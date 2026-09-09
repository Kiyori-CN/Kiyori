package com.ai.assistance.operit.ui.features.packages.components.dialogs.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 标准标签语义与最小触控高度，样式沿用宿主主题。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPServerDetailsTabs(selectedTabIndex: Int, onTabSelected: (Int) -> Unit) {
    SecondaryTabRow(selectedTabIndex = selectedTabIndex.coerceIn(0, 1), modifier = Modifier.fillMaxWidth()) {
        listOf(R.string.mcp_plugin_details, R.string.mcp_config_settings).forEachIndexed { index, label ->
            Tab(selected = selectedTabIndex == index, onClick = { onTabSelected(index) },
                modifier = Modifier.heightIn(min = 48.dp), text = { Text(stringResource(label)) })
        }
    }
}
