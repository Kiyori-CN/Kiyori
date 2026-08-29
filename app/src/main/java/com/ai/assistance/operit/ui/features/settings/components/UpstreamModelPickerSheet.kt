package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.ui.components.KiyoriModalBottomDrawer
import com.kiyori.design.theme.KiyoriUiShapes

@Composable
internal fun UpstreamModelPickerSheet(
    models: List<ModelOption>,
    existingModels: Set<String>,
    isRefreshing: Boolean,
    loadError: String?,
    onRefresh: () -> Unit,
    onApplySelection: (Set<String>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val availableModelIds = remember(models) { models.map(ModelOption::id).toSet() }
    val existingUpstreamModelIds =
        remember(existingModels, availableModelIds) {
            existingModels.intersect(availableModelIds)
        }
    var selectedModels by remember {
        mutableStateOf<Set<String>>(existingUpstreamModelIds)
    }
    var deselectedExistingModels by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    val filteredModels =
        remember(searchQuery, models) {
            val normalizedQuery = searchQuery.trim()
            if (normalizedQuery.isEmpty()) {
                models
            } else {
                models.filter { model ->
                    model.id.contains(normalizedQuery, ignoreCase = true) ||
                        model.name.contains(normalizedQuery, ignoreCase = true)
                }
            }
        }
    val filteredModelIds =
        remember(filteredModels) {
            filteredModels.map(ModelOption::id).toSet()
        }
    val areAllFilteredModelsSelected =
        filteredModelIds.isNotEmpty() &&
            selectedModels.containsAll(filteredModelIds)
    val addedModelCount = (selectedModels - existingModels).size
    val removedModelCount = (existingUpstreamModelIds - selectedModels).size
    val hasSelectionChanges = addedModelCount > 0 || removedModelCount > 0

    fun updateSelection(
        modelIds: Set<String>,
        selected: Boolean
    ) {
        selectedModels =
            if (selected) {
                selectedModels + modelIds
            } else {
                selectedModels - modelIds
            }
        val affectedExistingModels = modelIds.intersect(existingModels)
        deselectedExistingModels =
            if (selected) {
                deselectedExistingModels - affectedExistingModels
            } else {
                deselectedExistingModels + affectedExistingModels
            }
    }

    LaunchedEffect(availableModelIds, existingModels) {
        val nextExistingUpstreamModelIds = existingModels.intersect(availableModelIds)
        deselectedExistingModels =
            deselectedExistingModels.intersect(nextExistingUpstreamModelIds)
        selectedModels =
            selectedModels.intersect(availableModelIds) +
                (nextExistingUpstreamModelIds - deselectedExistingModels)
    }

    KiyoriModalBottomDrawer(onDismissRequest = onDismissRequest) { dismissDrawer ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 720.dp)
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.model_upstream_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.model_upstream_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(44.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh_models_list),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 14.dp, end = 20.dp)
                        .heightIn(min = 56.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_models),
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = KiyoriUiShapes.field
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text =
                        stringResource(R.string.models_displayed, filteredModels.size) +
                            if (searchQuery.isNotEmpty()) {
                                stringResource(R.string.models_displayed_filtered)
                            } else {
                                ""
                            },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filteredModelIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            updateSelection(
                                modelIds = filteredModelIds,
                                selected = !areAllFilteredModelsSelected
                            )
                        }
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (areAllFilteredModelsSelected) {
                                        R.string.model_upstream_unselect_filtered
                                    } else {
                                        R.string.model_upstream_select_filtered
                                    }
                                )
                        )
                    }
                }
            }

            if (filteredModels.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text =
                                if (loadError == null) {
                                    stringResource(R.string.no_models_found)
                                } else {
                                    loadError
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 12.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = filteredModels) { model ->
                        val isExisting = model.id in existingModels
                        val isSelected = model.id in selectedModels
                        val containerColor =
                            when {
                                isExisting && !isSelected ->
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
                                isExisting ->
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            }

                        Surface(
                            onClick = {
                                updateSelection(
                                    modelIds = setOf(model.id),
                                    selected = !isSelected
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = KiyoriUiShapes.control,
                            color = containerColor,
                            border =
                                BorderStroke(
                                    0.7.dp,
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else if (isExisting) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                    }
                                )
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        updateSelection(
                                            modelIds = setOf(model.id),
                                            selected = checked
                                        )
                                    },
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight =
                                            if (isSelected || isExisting) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        color =
                                            if (isExisting && !isSelected) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (model.name != model.id) {
                                        Text(
                                            text = model.id,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isExisting) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    if (isSelected) {
                                                        R.string.model_upstream_current
                                                    } else {
                                                        R.string.model_upstream_will_remove
                                                    }
                                                ),
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp
                                                ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (hasSelectionChanges) {
                Text(
                    text =
                        stringResource(
                            R.string.model_upstream_change_summary,
                            addedModelCount,
                            removedModelCount
                        ),
                    modifier =
                        Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 8.dp
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = dismissDrawer,
                    modifier = Modifier.height(46.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onApplySelection(selectedModels)
                        dismissDrawer()
                    },
                    enabled = hasSelectionChanges,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.model_upstream_apply_changes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
