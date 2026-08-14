package com.ai.assistance.operit.ui.features.packages.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.navigation.LocalTopBarActions

internal enum class PackageManagerTopBarAction {
    ENVIRONMENT,
    MARKET,
    ADD,
    REFRESH,
    ERROR,
    START,
}

internal enum class ScriptAddMenuAction {
    CREATE_SCRIPT,
    IMPORT_SCRIPT_PACKAGE,
}

internal fun packageManagerTopBarActions(
    tab: PackageTab,
    hasSkillLoadErrors: Boolean = false,
): List<PackageManagerTopBarAction> =
    when (tab) {
        PackageTab.PLUGINS,
        PackageTab.PACKAGES ->
            listOf(
                PackageManagerTopBarAction.ENVIRONMENT,
                PackageManagerTopBarAction.MARKET,
                PackageManagerTopBarAction.ADD,
                PackageManagerTopBarAction.REFRESH,
            )

        PackageTab.SKILLS ->
            buildList {
                if (hasSkillLoadErrors) {
                    add(PackageManagerTopBarAction.ERROR)
                }
                add(PackageManagerTopBarAction.MARKET)
                add(PackageManagerTopBarAction.ADD)
                add(PackageManagerTopBarAction.REFRESH)
            }

        PackageTab.MCP ->
            listOf(
                PackageManagerTopBarAction.START,
                PackageManagerTopBarAction.MARKET,
                PackageManagerTopBarAction.ADD,
                PackageManagerTopBarAction.REFRESH,
            )
    }

internal fun scriptAddMenuActions(): List<ScriptAddMenuAction> =
    listOf(
        ScriptAddMenuAction.CREATE_SCRIPT,
        ScriptAddMenuAction.IMPORT_SCRIPT_PACKAGE,
    )

@Composable
internal fun BindPackageManagerTopBarActions(
    selectedTab: PackageTab,
    isRefreshing: Boolean,
    onEnvironmentClick: () -> Unit,
    onMarketClick: () -> Unit,
    onImportClick: () -> Unit,
    onCreateScriptClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    BindPackageTopBarActions(
        selectedTab = selectedTab,
        isBusy = isRefreshing,
        isRefreshing = isRefreshing,
        isStarting = false,
        hasSkillLoadErrors = false,
        primaryTabsOnly = true,
        onEnvironmentClick = onEnvironmentClick,
        onMarketClick = onMarketClick,
        onImportClick = onImportClick,
        onCreateScriptClick = onCreateScriptClick,
        onRefreshClick = onRefreshClick,
    )
}

@Composable
internal fun BindSkillTopBarActions(
    isBusy: Boolean,
    isRefreshing: Boolean,
    hasLoadErrors: Boolean,
    onLoadErrorsClick: () -> Unit,
    onMarketClick: () -> Unit,
    onImportClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    BindPackageTopBarActions(
        selectedTab = PackageTab.SKILLS,
        isBusy = isBusy,
        isRefreshing = isRefreshing,
        isStarting = false,
        hasSkillLoadErrors = hasLoadErrors,
        primaryTabsOnly = false,
        onMarketClick = onMarketClick,
        onImportClick = onImportClick,
        onRefreshClick = onRefreshClick,
        onErrorClick = onLoadErrorsClick,
    )
}

@Composable
internal fun BindMcpTopBarActions(
    isBusy: Boolean,
    isRefreshing: Boolean,
    isStarting: Boolean,
    onStartClick: () -> Unit,
    onMarketClick: () -> Unit,
    onImportClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    BindPackageTopBarActions(
        selectedTab = PackageTab.MCP,
        isBusy = isBusy,
        isRefreshing = isRefreshing,
        isStarting = isStarting,
        hasSkillLoadErrors = false,
        primaryTabsOnly = false,
        onStartClick = onStartClick,
        onMarketClick = onMarketClick,
        onImportClick = onImportClick,
        onRefreshClick = onRefreshClick,
    )
}

@Composable
private fun BindPackageTopBarActions(
    selectedTab: PackageTab,
    isBusy: Boolean,
    isRefreshing: Boolean,
    isStarting: Boolean,
    hasSkillLoadErrors: Boolean,
    primaryTabsOnly: Boolean,
    onEnvironmentClick: () -> Unit = {},
    onMarketClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onCreateScriptClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onErrorClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
) {
    val setTopBarActions = LocalTopBarActions.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val appBarContentColor = LocalAppBarContentColor.current
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestIsBusy by rememberUpdatedState(isBusy)
    val latestIsRefreshing by rememberUpdatedState(isRefreshing)
    val latestIsStarting by rememberUpdatedState(isStarting)
    val latestHasSkillLoadErrors by rememberUpdatedState(hasSkillLoadErrors)
    val latestOnEnvironmentClick by rememberUpdatedState(onEnvironmentClick)
    val latestOnMarketClick by rememberUpdatedState(onMarketClick)
    val latestOnImportClick by rememberUpdatedState(onImportClick)
    val latestOnCreateScriptClick by rememberUpdatedState(onCreateScriptClick)
    val latestOnRefreshClick by rememberUpdatedState(onRefreshClick)
    val latestOnErrorClick by rememberUpdatedState(onErrorClick)
    val latestOnStartClick by rememberUpdatedState(onStartClick)

    LaunchedEffect(
        isCurrentScreen,
        selectedTab,
        isBusy,
        isRefreshing,
        isStarting,
        hasSkillLoadErrors,
        primaryTabsOnly,
        appBarContentColor,
    ) {
        val ownsTopBar =
            if (primaryTabsOnly) {
                selectedTab == PackageTab.PLUGINS || selectedTab == PackageTab.PACKAGES
            } else {
                selectedTab == PackageTab.SKILLS || selectedTab == PackageTab.MCP
            }
        // 标签切换由新 owner 覆盖顶栏，路由离开由宿主统一清理；这里主动清空会与子页面绑定竞争。
        if (!isCurrentScreen || !ownsTopBar) {
            return@LaunchedEffect
        }

        setTopBarActions {
            packageManagerTopBarActions(
                tab = latestSelectedTab,
                hasSkillLoadErrors = latestHasSkillLoadErrors,
            ).forEach { action ->
                PackageManagerTopBarActionButton(
                    action = action,
                    selectedTab = latestSelectedTab,
                    isBusy = latestIsBusy,
                    isRefreshing = latestIsRefreshing,
                    isStarting = latestIsStarting,
                    contentColor = appBarContentColor,
                    onEnvironmentClick = latestOnEnvironmentClick,
                    onMarketClick = latestOnMarketClick,
                    onImportClick = latestOnImportClick,
                    onCreateScriptClick = latestOnCreateScriptClick,
                    onRefreshClick = latestOnRefreshClick,
                    onErrorClick = latestOnErrorClick,
                    onStartClick = latestOnStartClick,
                )
            }
        }
    }
}

@Composable
private fun RowScope.PackageManagerTopBarActionButton(
    action: PackageManagerTopBarAction,
    selectedTab: PackageTab,
    isBusy: Boolean,
    isRefreshing: Boolean,
    isStarting: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    onEnvironmentClick: () -> Unit,
    onMarketClick: () -> Unit,
    onImportClick: () -> Unit,
    onCreateScriptClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onErrorClick: () -> Unit,
    onStartClick: () -> Unit,
) {
    val isPrimaryTab =
        selectedTab == PackageTab.PLUGINS || selectedTab == PackageTab.PACKAGES
    val childActionEnabled = isPrimaryTab || !isBusy
    val disabledContentColor = contentColor.copy(alpha = 0.38f)

    when (action) {
        PackageManagerTopBarAction.ENVIRONMENT -> {
            IconButton(onClick = onEnvironmentClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.pkg_manage_env_vars),
                    tint = contentColor,
                )
            }
        }

        PackageManagerTopBarAction.MARKET -> {
            IconButton(
                onClick = onMarketClick,
                enabled = childActionEnabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Store,
                    contentDescription =
                        stringResource(
                            when (selectedTab) {
                                PackageTab.PLUGINS -> R.string.screen_title_package_market
                                PackageTab.PACKAGES -> R.string.screen_title_script_market
                                PackageTab.SKILLS -> R.string.screen_title_skill_market
                                PackageTab.MCP -> R.string.mcp_market
                            }
                        ),
                    tint = if (childActionEnabled) contentColor else disabledContentColor,
                )
            }
        }

        PackageManagerTopBarAction.ADD -> {
            var scriptMenuExpanded by remember(selectedTab) { mutableStateOf(false) }

            Box {
                IconButton(
                    enabled = childActionEnabled,
                    onClick = {
                        if (selectedTab == PackageTab.PACKAGES) {
                            scriptMenuExpanded = true
                        } else {
                            onImportClick()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription =
                            stringResource(
                                if (selectedTab == PackageTab.PLUGINS) {
                                    R.string.import_external_plugin
                                } else if (selectedTab == PackageTab.PACKAGES) {
                                    R.string.script_add_actions
                                } else {
                                    R.string.import_action
                                }
                            ),
                        tint = if (childActionEnabled) contentColor else disabledContentColor,
                    )
                }

                DropdownMenu(
                    expanded = selectedTab == PackageTab.PACKAGES && scriptMenuExpanded,
                    onDismissRequest = { scriptMenuExpanded = false },
                ) {
                    scriptAddMenuActions().forEach { menuAction ->
                        when (menuAction) {
                            ScriptAddMenuAction.CREATE_SCRIPT ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.create_script)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoMode,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        scriptMenuExpanded = false
                                        onCreateScriptClick()
                                    },
                                )

                            ScriptAddMenuAction.IMPORT_SCRIPT_PACKAGE ->
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.import_script_package))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.UploadFile,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        scriptMenuExpanded = false
                                        onImportClick()
                                    },
                                )
                        }
                    }
                }
            }
        }

        PackageManagerTopBarAction.REFRESH -> {
            IconButton(
                onClick = onRefreshClick,
                enabled = !isBusy,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = if (isBusy) disabledContentColor else contentColor,
                    )
                }
            }
        }

        PackageManagerTopBarAction.ERROR -> {
            IconButton(onClick = onErrorClick) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = stringResource(R.string.error_occurred_simple),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        PackageManagerTopBarAction.START -> {
            IconButton(
                onClick = onStartClick,
                enabled = !isBusy,
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.start_plugin),
                        tint = if (isBusy) disabledContentColor else contentColor,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PackageManagerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    @StringRes placeholderRes: Int,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(placeholderRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(horizontal = 9.dp)
                                .size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                query.isNotEmpty() -> {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.clear),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PackageLoadErrorsBanner(
    errorCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = stringResource(R.string.package_manager_load_errors_summary, errorCount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
