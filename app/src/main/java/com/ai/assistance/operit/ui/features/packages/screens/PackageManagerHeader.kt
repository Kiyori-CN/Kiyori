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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
}

internal fun packageManagerTopBarActions(tab: PackageTab): List<PackageManagerTopBarAction> =
    when (tab) {
        PackageTab.PLUGINS,
        PackageTab.PACKAGES ->
            listOf(
                PackageManagerTopBarAction.ENVIRONMENT,
                PackageManagerTopBarAction.MARKET,
                PackageManagerTopBarAction.ADD,
                PackageManagerTopBarAction.REFRESH,
            )

        PackageTab.SKILLS,
        PackageTab.MCP -> emptyList()
    }

@Composable
internal fun BindPackageManagerTopBarActions(
    selectedTab: PackageTab,
    isRefreshing: Boolean,
    onEnvironmentClick: () -> Unit,
    onMarketClick: () -> Unit,
    onAddClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val setTopBarActions = LocalTopBarActions.current
    val isCurrentScreen = LocalIsCurrentScreen.current
    val appBarContentColor = LocalAppBarContentColor.current
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestIsRefreshing by rememberUpdatedState(isRefreshing)
    val latestOnEnvironmentClick by rememberUpdatedState(onEnvironmentClick)
    val latestOnMarketClick by rememberUpdatedState(onMarketClick)
    val latestOnAddClick by rememberUpdatedState(onAddClick)
    val latestOnRefreshClick by rememberUpdatedState(onRefreshClick)

    LaunchedEffect(isCurrentScreen, selectedTab, isRefreshing, appBarContentColor) {
        if (!isCurrentScreen) {
            return@LaunchedEffect
        }

        setTopBarActions {
            packageManagerTopBarActions(latestSelectedTab).forEach { action ->
                PackageManagerTopBarActionButton(
                    action = action,
                    selectedTab = latestSelectedTab,
                    isRefreshing = latestIsRefreshing,
                    contentColor = appBarContentColor,
                    onEnvironmentClick = latestOnEnvironmentClick,
                    onMarketClick = latestOnMarketClick,
                    onAddClick = latestOnAddClick,
                    onRefreshClick = latestOnRefreshClick,
                )
            }
        }
    }
}

@Composable
private fun RowScope.PackageManagerTopBarActionButton(
    action: PackageManagerTopBarAction,
    selectedTab: PackageTab,
    isRefreshing: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    onEnvironmentClick: () -> Unit,
    onMarketClick: () -> Unit,
    onAddClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    when (action) {
        PackageManagerTopBarAction.ENVIRONMENT -> {
            IconButton(onClick = onEnvironmentClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.pkg_manage_env_vars),
                    tint = contentColor,
                )
            }
        }

        PackageManagerTopBarAction.MARKET -> {
            IconButton(onClick = onMarketClick) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription =
                        stringResource(
                            if (selectedTab == PackageTab.PLUGINS) {
                                R.string.screen_title_package_market
                            } else {
                                R.string.screen_title_script_market
                            }
                        ),
                    tint = contentColor,
                )
            }
        }

        PackageManagerTopBarAction.ADD -> {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription =
                        stringResource(
                            if (selectedTab == PackageTab.PLUGINS) {
                                R.string.import_external_plugin
                            } else {
                                R.string.import_external_package
                            }
                        ),
                    tint = contentColor,
                )
            }
        }

        PackageManagerTopBarAction.REFRESH -> {
            IconButton(
                onClick = onRefreshClick,
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = contentColor,
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
                imageVector = Icons.Rounded.Search,
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
                            imageVector = Icons.Default.Close,
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
