package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.moveModelName
import com.ai.assistance.operit.data.model.parseModelNameInput
import com.ai.assistance.operit.data.model.serializeModelNames
import com.ai.assistance.operit.ui.common.copyPlainTextToClipboard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val COLLAPSED_MODEL_TAG_ROW_COUNT = 3
private val MODEL_TAG_HORIZONTAL_SPACING = 7.dp
private val MODEL_TAG_VERTICAL_SPACING = 8.dp

internal data class ModelTagFlowRowPlan(
    val modelIndexes: List<Int>,
    val includesExpandIndicator: Boolean
)

internal data class ModelTagFlowPlan(
    val rows: List<ModelTagFlowRowPlan>,
    val hiddenModelCount: Int
)

internal fun planModelTagFlow(
    modelWidths: List<Int>,
    availableWidth: Int,
    horizontalSpacing: Int,
    maxRows: Int,
    expandIndicatorWidth: Int?
): ModelTagFlowPlan {
    require(availableWidth > 0)
    require(horizontalSpacing >= 0)
    require(maxRows > 0)
    require(modelWidths.all { width -> width in 0..availableWidth })
    require(expandIndicatorWidth == null || expandIndicatorWidth in 0..availableWidth)

    val rows = mutableListOf<MutableList<Int>>()
    val rowWidths = mutableListOf<Int>()

    for ((index, width) in modelWidths.withIndex()) {
        if (rows.isEmpty()) {
            rows += mutableListOf(index)
            rowWidths += width
            continue
        }

        val currentRowIndex = rows.lastIndex
        val proposedWidth =
            rowWidths[currentRowIndex].toLong() +
                horizontalSpacing.toLong() +
                width.toLong()
        if (proposedWidth <= availableWidth.toLong()) {
            rows[currentRowIndex] += index
            rowWidths[currentRowIndex] = proposedWidth.toInt()
        } else if (rows.size < maxRows) {
            rows += mutableListOf(index)
            rowWidths += width
        } else {
            break
        }
    }

    val visibleModelCount = rows.sumOf { row -> row.size }
    val hasHiddenModels = visibleModelCount < modelWidths.size
    if (!hasHiddenModels || expandIndicatorWidth == null) {
        return ModelTagFlowPlan(
            rows =
                rows.map { row ->
                    ModelTagFlowRowPlan(
                        modelIndexes = row.toList(),
                        includesExpandIndicator = false
                    )
                },
            hiddenModelCount = modelWidths.size - visibleModelCount
        )
    }

    if (rows.size < maxRows) {
        rows.add(mutableListOf())
        rowWidths.add(0)
    }

    val indicatorRowIndex = rows.lastIndex
    val indicatorRow = rows[indicatorRowIndex]
    while (
        indicatorRow.isNotEmpty() &&
            rowWidths[indicatorRowIndex].toLong() +
                horizontalSpacing.toLong() +
                expandIndicatorWidth.toLong() >
            availableWidth.toLong()
    ) {
        val removedIndex = indicatorRow.removeAt(indicatorRow.lastIndex)
        val removedWidth = modelWidths[removedIndex]
        rowWidths[indicatorRowIndex] -=
            if (indicatorRow.isEmpty()) {
                removedWidth
            } else {
                horizontalSpacing + removedWidth
            }
    }

    return ModelTagFlowPlan(
        rows =
            rows.mapIndexed { index, row ->
                ModelTagFlowRowPlan(
                    modelIndexes = row.toList(),
                    includesExpandIndicator = index == indicatorRowIndex
                )
            },
        hiddenModelCount = modelWidths.size - rows.sumOf { row -> row.size }
    )
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
internal fun ModelNameTagEditor(
    models: List<String>,
    canAddManually: Boolean,
    canFetchFromUpstream: Boolean,
    isFetchingFromUpstream: Boolean,
    onAddModels: (List<String>) -> Unit,
    onFetchFromUpstream: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onReorderModels: (List<String>) -> Unit,
    onClearModels: () -> Unit,
    showNotification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(models.size) {
        if (models.isEmpty()) {
            expanded = false
            showSortSheet = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.model_tags_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.model_tags_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(R.string.model_tags_count, models.size),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        if (models.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border =
                    BorderStroke(
                        0.7.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.model_tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.model_tags_empty_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                border =
                    BorderStroke(
                        0.7.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CompleteModelTagFlow(
                        models = models,
                        expanded = expanded,
                        modelContent = { index, modelName ->
                            ModelNameTag(
                                modelName = modelName,
                                isTestModel = index == 0,
                                onCopy = {
                                    context.copyPlainTextToClipboard(
                                        label = "Kiyori model name",
                                        text = modelName
                                    )
                                    showNotification(
                                        resources.getString(
                                            R.string.model_copy_success,
                                            modelName
                                        )
                                    )
                                },
                                onDelete = { onDeleteModel(modelName) },
                                onLongPress = {
                                    if (models.size > 1) {
                                        showSortSheet = true
                                    }
                                }
                            )
                        },
                        expandIndicator = { hiddenModelCount ->
                            ModelOverflowChip(
                                label =
                                    stringResource(
                                        R.string.model_expand_remaining,
                                        hiddenModelCount
                                    ),
                                onClick = { expanded = true }
                            )
                        }
                    )

                    if (expanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { expanded = false },
                                contentPadding =
                                    PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.model_collapse))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showAddDialog = true },
                enabled = canAddManually,
                modifier =
                    Modifier
                        .weight(0.94f)
                        .height(ModelSettingsActionHeight),
                shape = ModelSettingsActionShape,
                contentPadding =
                    PaddingValues(horizontal = ModelSettingsActionHorizontalPadding)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ModelSettingsActionIconSize)
                )
                Spacer(modifier = Modifier.width(ModelSettingsActionContentSpacing))
                Text(
                    text = stringResource(R.string.model_add_action),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            FilledTonalButton(
                onClick = onFetchFromUpstream,
                enabled = canFetchFromUpstream && !isFetchingFromUpstream,
                modifier =
                    Modifier
                        .weight(1.06f)
                        .height(ModelSettingsActionHeight),
                shape = ModelSettingsActionShape,
                contentPadding =
                    PaddingValues(horizontal = ModelSettingsActionHorizontalPadding)
            ) {
                if (isFetchingFromUpstream) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ModelSettingsActionIconSize),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(ModelSettingsActionIconSize)
                    )
                }
                Spacer(modifier = Modifier.width(ModelSettingsActionContentSpacing))
                Text(
                    text = stringResource(R.string.model_fetch_action),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Box {
                Surface(
                    onClick = { showMoreMenu = true },
                    enabled = models.isNotEmpty(),
                    modifier = Modifier.size(ModelSettingsActionHeight),
                    shape = ModelSettingsActionShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border =
                        BorderStroke(
                            0.8.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = stringResource(R.string.model_more_actions),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.model_sort_action)) },
                        leadingIcon = {
                            Icon(Icons.Default.Reorder, contentDescription = null)
                        },
                        enabled = models.size > 1,
                        onClick = {
                            showMoreMenu = false
                            showSortSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.model_copy_all)) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            showMoreMenu = false
                            context.copyPlainTextToClipboard(
                                label = "Kiyori model names",
                                text = serializeModelNames(models)
                            )
                            showNotification(
                                resources.getString(
                                    R.string.model_copy_all_success,
                                    models.size
                                )
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.model_clear_all_action)) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        },
                        onClick = {
                            showMoreMenu = false
                            onClearModels()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ModelManualAddDialog(
            onDismissRequest = { showAddDialog = false },
            onConfirm = { input ->
                val parsedModels = parseModelNameInput(input)
                if (parsedModels.isNotEmpty()) {
                    onAddModels(parsedModels)
                    showAddDialog = false
                }
            }
        )
    }

    if (showSortSheet && models.size > 1) {
        ModelSortSheet(
            models = models,
            onDismissRequest = { showSortSheet = false },
            onConfirm = { sortedModels ->
                onReorderModels(sortedModels)
                showSortSheet = false
            }
        )
    }
}

private enum class ModelTagFlowSlot {
    Models,
    IndicatorSizing
}

private data class ModelTagFlowIndicatorSlot(
    val hiddenModelCount: Int
)

@Composable
private fun CompleteModelTagFlow(
    models: List<String>,
    expanded: Boolean,
    modelContent: @Composable (Int, String) -> Unit,
    expandIndicator: @Composable (Int) -> Unit
) {
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        require(constraints.hasBoundedWidth)
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val modelPlaceables =
            subcompose(ModelTagFlowSlot.Models) {
                models.forEachIndexed { index, modelName ->
                    modelContent(index, modelName)
                }
            }.map { measurable -> measurable.measure(childConstraints) }
        // “还有 N 个”的宽度会反过来影响第三行能放下多少普通标签。
        // 先用最大可能计数预留完整宽度，再按最终放置结果组合真实计数，避免裁切或读取延迟布局状态。
        val sizingIndicatorPlaceable =
            if (!expanded && models.isNotEmpty()) {
                subcompose(ModelTagFlowSlot.IndicatorSizing) {
                    expandIndicator(models.size)
                }.single().measure(childConstraints)
            } else {
                null
            }
        val horizontalSpacing = MODEL_TAG_HORIZONTAL_SPACING.roundToPx()
        val verticalSpacing = MODEL_TAG_VERTICAL_SPACING.roundToPx()
        val plan =
            planModelTagFlow(
                modelWidths = modelPlaceables.map { placeable -> placeable.width },
                availableWidth = constraints.maxWidth,
                horizontalSpacing = horizontalSpacing,
                maxRows = if (expanded) Int.MAX_VALUE else COLLAPSED_MODEL_TAG_ROW_COUNT,
                expandIndicatorWidth = sizingIndicatorPlaceable?.width
            )
        val indicatorPlaceable =
            if (plan.hiddenModelCount > 0) {
                val reservedWidth = requireNotNull(sizingIndicatorPlaceable).width
                subcompose(ModelTagFlowIndicatorSlot(plan.hiddenModelCount)) {
                    expandIndicator(plan.hiddenModelCount)
                }.single().measure(
                    childConstraints.copy(
                        minWidth = reservedWidth,
                        maxWidth = reservedWidth
                    )
                )
            } else {
                null
            }
        val rowHeights =
            plan.rows.map { row ->
                maxOf(
                    row.modelIndexes.maxOfOrNull { index -> modelPlaceables[index].height } ?: 0,
                    if (row.includesExpandIndicator) {
                        requireNotNull(indicatorPlaceable).height
                    } else {
                        0
                    }
                )
            }
        val contentHeight =
            rowHeights.sum() +
                verticalSpacing * (plan.rows.size - 1).coerceAtLeast(0)

        layout(
            width = constraints.maxWidth,
            height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            var y = 0
            plan.rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.modelIndexes.forEach { modelIndex ->
                    val placeable = modelPlaceables[modelIndex]
                    placeable.placeRelative(x = x, y = y)
                    x += placeable.width + horizontalSpacing
                }
                if (row.includesExpandIndicator) {
                    requireNotNull(indicatorPlaceable).placeRelative(x = x, y = y)
                }
                y += rowHeights[rowIndex] + verticalSpacing
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelNameTag(
    modelName: String,
    isTestModel: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier =
            modifier
                .heightIn(min = 36.dp)
                .combinedClickable(
                    onClick = onCopy,
                    onLongClick = onLongPress
                ),
        shape = shape,
        color =
            if (isTestModel) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            BorderStroke(
                0.8.dp,
                if (isTestModel) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = 10.dp,
                    top = 4.dp,
                    end = 2.dp,
                    bottom = 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTestModel) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.model_test_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = modelName,
                modifier = Modifier.weight(1f, fill = false),
                softWrap = true,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (isTestModel) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription =
                        stringResource(
                            R.string.model_delete_accessibility,
                            modelName
                        ),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelOverflowChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ModelManualAddDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val parsedModels = remember(input) { parseModelNameInput(input) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.model_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.model_add_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.model_add_placeholder)) },
                    minLines = 3,
                    maxLines = 7
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(input) },
                enabled = parsedModels.isNotEmpty()
            ) {
                Text(
                    text =
                        if (parsedModels.size > 1) {
                            "${stringResource(R.string.model_add_confirm)} (${parsedModels.size})"
                        } else {
                            stringResource(R.string.model_add_confirm)
                        }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSortSheet(
    models: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var orderedModels by remember(models) { mutableStateOf(models) }
    val listState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            orderedModels = moveModelName(orderedModels, from.index, to.index)
        }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.model_sort_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.model_sort_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = orderedModels,
                    key = { _, modelName -> modelName }
                ) { index, modelName ->
                    ReorderableItem(
                        reorderableState,
                        key = modelName
                    ) { isDragging ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isDragging) {
                                            Modifier.shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                            shape = RoundedCornerShape(14.dp),
                            color =
                                if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (index == 0) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        modifier =
                                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color =
                                            if (index == 0) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = modelName,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight =
                                            if (index == 0) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            }
                                    )
                                    if (index == 0) {
                                        Text(
                                            text = stringResource(R.string.model_test_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription =
                                        stringResource(
                                            R.string.model_drag_accessibility,
                                            modelName
                                        ),
                                    modifier =
                                        Modifier
                                            .size(28.dp)
                                            .longPressDraggableHandle(),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onConfirm(orderedModels) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(R.string.model_sort_done))
                }
            }
        }
    }
}
