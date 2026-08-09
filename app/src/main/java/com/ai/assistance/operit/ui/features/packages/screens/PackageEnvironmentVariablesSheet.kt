package com.ai.assistance.operit.ui.features.packages.screens

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.ui.components.KiyoriDraggableBottomDrawer
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors

@Composable
internal fun PackageEnvironmentVariablesSheet(
    packages: List<ToolPackage>,
    currentValues: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val groups =
        sortPackageEnvironmentVariableGroups(
            packages.map { toolPackage ->
                val categoryLabel =
                    normalizePackageEnvironmentCategoryLabel(toolPackage.category)
                PackageEnvironmentVariableGroup(
                    packageName = toolPackage.name,
                    displayName = toolPackage.displayName.resolve(context).ifBlank { toolPackage.name },
                    categoryKey = packageEnvironmentCategoryKey(categoryLabel),
                    categoryLabel = categoryLabel,
                    variables =
                        toolPackage.env.map { envVar ->
                            PackageEnvironmentVariableItem(
                                name = envVar.name,
                                description = envVar.description.resolve(context),
                                required = envVar.required,
                                defaultValue = envVar.defaultValue,
                            )
                        },
                )
            },
        )

    val variableNames = remember(groups) { distinctPackageEnvironmentVariableNames(groups) }
    var editableValues by
        remember(variableNames, currentValues) {
            mutableStateOf(
                variableNames.associateWith { variableName ->
                    currentValues[variableName].orEmpty()
                },
            )
        }
    var selectedCategoryKey by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedPackageNames by
        remember(groups, currentValues) {
            mutableStateOf(initialExpandedPackageNames(groups, currentValues))
        }
    var drawerVisible by remember { mutableStateOf(true) }

    val categories = remember(groups) { buildPackageEnvironmentCategories(groups) }
    val filteredGroups =
        remember(groups, selectedCategoryKey, query) {
            filterPackageEnvironmentVariableGroups(
                groups = groups,
                selectedCategoryKey = selectedCategoryKey,
                query = query,
            )
        }

    LaunchedEffect(categories, selectedCategoryKey) {
        if (
            selectedCategoryKey != null &&
                categories.none { category -> category.key == selectedCategoryKey }
        ) {
            selectedCategoryKey = null
        }
    }

    val requestClose = { drawerVisible = false }
    Dialog(
        onDismissRequest = requestClose,
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            // 遮罩和动画由共享抽屉唯一持有；关闭 Dialog 默认 dim，避免两层暗化导致视觉漂移。
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        }
        BackHandler(onBack = requestClose)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            KiyoriDraggableBottomDrawer(
                isVisible = drawerVisible,
                partialVisibleFraction =
                    resolvePackageEnvironmentDrawerPartialVisibleFraction(
                        widthDp = maxWidth.value,
                        heightDp = maxHeight.value,
                    ),
                onDismissRequest = requestClose,
                onHidden = onDismiss,
            ) {
                PackageEnvironmentVariablesSheetContent(
                    groups = groups,
                    filteredGroups = filteredGroups,
                    categories = categories,
                    selectedCategoryKey = selectedCategoryKey,
                    query = query,
                    editableValues = editableValues,
                    expandedPackageNames = expandedPackageNames,
                    onCategorySelected = { categoryKey -> selectedCategoryKey = categoryKey },
                    onQueryChange = { newQuery -> query = newQuery },
                    onClearQuery = { query = "" },
                    onTogglePackage = { packageName ->
                        expandedPackageNames =
                            expandedPackageNames.toMutableSet().apply {
                                if (!add(packageName)) {
                                    remove(packageName)
                                }
                            }
                    },
                    onValueChange = { variableName, value ->
                        editableValues =
                            editableValues.toMutableMap().apply {
                                this[variableName] = value
                            }
                    },
                    onCancel = requestClose,
                    onSave = {
                        onConfirm(editableValues)
                        requestClose()
                    },
                )
            }
        }
    }
}

@Composable
private fun PackageEnvironmentVariablesSheetContent(
    groups: List<PackageEnvironmentVariableGroup>,
    filteredGroups: List<PackageEnvironmentVariableGroup>,
    categories: List<PackageEnvironmentVariableCategory>,
    selectedCategoryKey: String?,
    query: String,
    editableValues: Map<String, String>,
    expandedPackageNames: Set<String>,
    onCategorySelected: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onTogglePackage: (String) -> Unit,
    onValueChange: (String, String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val variableNames = remember(groups) { distinctPackageEnvironmentVariableNames(groups) }
    val requiredVariableNames =
        remember(groups) {
            groups
                .asSequence()
                .flatMap { group -> group.variables.asSequence() }
                .filter { variable -> variable.required }
                .map { variable -> variable.name }
                .distinct()
                .toList()
        }
    val missingRequiredVariableCount =
        requiredVariableNames.count { variableName ->
            editableValues[variableName].isNullOrBlank()
        }

    Column(modifier = Modifier.fillMaxSize()) {
        PackageEnvironmentVariablesHeader(
            packageCount = groups.size,
            variableCount = variableNames.size,
            missingRequiredVariableCount = missingRequiredVariableCount,
            onClose = onCancel,
        )
        PackageEnvironmentSearchField(
            value = query,
            onValueChange = onQueryChange,
            onClear = onClearQuery,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        PackageEnvironmentCategoryRow(
            allPackageCount = groups.size,
            categories = categories,
            selectedCategoryKey = selectedCategoryKey,
            onCategorySelected = onCategorySelected,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            when {
                groups.isEmpty() ->
                    PackageEnvironmentEmptyState(
                        message = stringResource(R.string.pkg_no_env_vars),
                    )
                filteredGroups.isEmpty() ->
                    PackageEnvironmentEmptyState(
                        message = stringResource(R.string.pkg_env_empty_filter),
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                start = 12.dp,
                                top = 8.dp,
                                end = 12.dp,
                                bottom = 10.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = filteredGroups,
                            key = { group -> group.packageName },
                        ) { group ->
                            val isExpanded =
                                query.isNotBlank() || group.packageName in expandedPackageNames
                            PackageEnvironmentVariableGroupCard(
                                group = group,
                                values = editableValues,
                                expanded = isExpanded,
                                toggleEnabled = query.isBlank(),
                                onToggle = { onTogglePackage(group.packageName) },
                                onValueChange = onValueChange,
                            )
                        }
                    }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.pkg_cancel))
            }
            Button(
                onClick = onSave,
                enabled = groups.isNotEmpty(),
            ) {
                Text(text = stringResource(R.string.pkg_save))
            }
        }
    }
}

@Composable
private fun PackageEnvironmentVariablesHeader(
    packageCount: Int,
    variableCount: Int,
    missingRequiredVariableCount: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KiyoriSemanticIconBadge(
            imageVector = Icons.Filled.Settings,
            tone = KiyoriSemanticTone.CYAN,
            contentDescription = null,
            containerSize = 36.dp,
            iconSize = 19.dp,
            shape = RoundedCornerShape(11.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.pkg_config_env_vars),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        R.string.pkg_env_summary,
                        packageCount,
                        variableCount,
                        missingRequiredVariableCount,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

@Composable
private fun PackageEnvironmentSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KiyoriSemanticTone.CYAN.resolveColors()
    Surface(
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                    ),
                cursorBrush = SolidColor(colors.icon),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.pkg_env_search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageEnvironmentCategoryRow(
    allPackageCount: Int,
    categories: List<PackageEnvironmentVariableCategory>,
    selectedCategoryKey: String?,
    onCategorySelected: (String?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PackageEnvironmentCategoryChip(
            label = stringResource(R.string.pkg_env_category_all),
            packageCount = allPackageCount,
            visual = null,
            selected = selectedCategoryKey == null,
            onClick = { onCategorySelected(null) },
        )
        categories.forEach { category ->
            PackageEnvironmentCategoryChip(
                label = category.label,
                packageCount = category.packageCount,
                visual = resolvePackageCategoryVisual(category.label),
                selected = selectedCategoryKey == category.key,
                onClick = { onCategorySelected(category.key) },
            )
        }
    }
}

@Composable
private fun PackageEnvironmentCategoryChip(
    label: String,
    packageCount: Int,
    visual: PackageCategoryVisual?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val allColors = KiyoriSemanticTone.CYAN.resolveColors()
    val categoryColors = visual?.resolveColors()
    val accentColor = categoryColors?.icon ?: allColors.icon
    val selectedContainer = categoryColors?.container ?: allColors.container
    Surface(
        modifier = Modifier.height(34.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) selectedContainer else MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (selected) {
                        accentColor
                    } else {
                        accentColor.copy(alpha = 0.34f)
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = visual?.icon?.toImageVector() ?: Icons.Filled.Apps,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.pkg_env_category_count, label, packageCount),
                color = accentColor,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PackageEnvironmentVariableGroupCard(
    group: PackageEnvironmentVariableGroup,
    values: Map<String, String>,
    expanded: Boolean,
    toggleEnabled: Boolean,
    onToggle: () -> Unit,
    onValueChange: (String, String) -> Unit,
) {
    val visual = resolvePackageCategoryVisual(group.categoryLabel)
    val colors = visual.resolveColors()
    val configuredCount =
        group.variables.count { variable -> values[variable.name].isNullOrBlank().not() }
    val missingRequiredCount =
        group.variables.count { variable ->
            variable.required && values[variable.name].isNullOrBlank()
        }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "PackageEnvironmentGroupChevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.icon.copy(alpha = 0.2f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = toggleEnabled,
                            role = Role.Button,
                            onClick = onToggle,
                        )
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PackageCategoryIconBadge(
                    visual = visual,
                    contentDescription = null,
                    containerSize = 32.dp,
                    iconSize = 18.dp,
                    shape = RoundedCornerShape(9.dp),
                )
                Column(
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = group.displayName,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PackageEnvironmentStatusChip(
                            text = group.categoryLabel,
                            containerColor = colors.container,
                            contentColor = colors.icon,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (group.displayName != group.packageName) {
                            Text(
                                text = group.packageName,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text =
                                if (missingRequiredCount > 0) {
                                    stringResource(
                                        R.string.pkg_env_missing_required,
                                        missingRequiredCount,
                                    )
                                } else {
                                    stringResource(
                                        R.string.pkg_env_group_summary,
                                        group.variables.size,
                                        configuredCount,
                                    )
                                },
                            modifier =
                                if (group.displayName == group.packageName) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier
                                },
                            color =
                                if (missingRequiredCount > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            fontWeight =
                                if (missingRequiredCount > 0) {
                                    FontWeight.Medium
                                } else {
                                    FontWeight.Normal
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.icon.copy(alpha = 0.82f),
                    modifier = Modifier.size(20.dp).rotate(chevronRotation),
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                group.variables.forEachIndexed { index, variable ->
                    PackageEnvironmentVariableEditor(
                        variable = variable,
                        value = values[variable.name].orEmpty(),
                        visual = visual,
                        onValueChange = { value -> onValueChange(variable.name, value) },
                    )
                    if (index < group.variables.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 11.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageEnvironmentVariableEditor(
    variable: PackageEnvironmentVariableItem,
    value: String,
    visual: PackageCategoryVisual,
    onValueChange: (String) -> Unit,
) {
    val categoryColors = visual.resolveColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = variable.name,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (variable.required) {
                PackageEnvironmentStatusChip(
                    text = stringResource(R.string.pkg_env_required),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                PackageEnvironmentStatusChip(
                    text = stringResource(R.string.pkg_env_optional),
                    containerColor = categoryColors.container,
                    contentColor = categoryColors.icon,
                )
            }
        }
        if (variable.description.isNotBlank() || variable.defaultValue != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (variable.description.isNotBlank()) {
                    Text(
                        text = variable.description,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                variable.defaultValue?.let { defaultValue ->
                    Text(
                        text = stringResource(R.string.pkg_default, defaultValue),
                        modifier =
                            if (variable.description.isNotBlank()) {
                                Modifier.weight(0.42f, fill = false)
                            } else {
                                Modifier.weight(1f)
                            },
                        color = categoryColors.icon,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PackageEnvironmentValueField(
            value = value,
            onValueChange = onValueChange,
            placeholder =
                stringResource(
                    if (variable.required) {
                        R.string.pkg_input_required
                    } else {
                        R.string.pkg_input_optional
                    },
                ),
            accentColor = categoryColors.icon,
        )
    }
}

@Composable
private fun PackageEnvironmentValueField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = if (focused) 1.5.dp else 1.dp,
                color =
                    if (focused) {
                        accentColor
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                    ),
                cursorBrush = SolidColor(accentColor),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun PackageEnvironmentStatusChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.widthIn(max = 116.dp),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun PackageEnvironmentEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KiyoriSemanticIconBadge(
                imageVector = Icons.Filled.Check,
                tone = KiyoriSemanticTone.CYAN,
                contentDescription = null,
                containerSize = 46.dp,
                iconSize = 24.dp,
                shape = CircleShape,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}
