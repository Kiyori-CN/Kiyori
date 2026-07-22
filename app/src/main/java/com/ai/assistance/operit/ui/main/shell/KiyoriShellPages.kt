package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

private data class PrimaryDestinationVisual(
    val destination: PrimaryDestination,
    val labelResId: Int,
    val icon: ImageVector,
)

private val primaryDestinationVisuals =
    listOf(
        PrimaryDestinationVisual(
            PrimaryDestination.SOFTWARE_HOME,
            R.string.kiyori_shell_software_home,
            Icons.Default.Home,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.BROWSER_HOME,
            R.string.kiyori_shell_browser_home,
            Icons.Default.Language,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.MINI_APP_HOME,
            R.string.kiyori_shell_mini_app_home,
            Icons.Default.Apps,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.FILE_MANAGEMENT_HOME,
            R.string.kiyori_shell_file_management_home,
            Icons.Default.Folder,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.SETTINGS_HOME,
            R.string.kiyori_shell_settings_home,
            Icons.Default.Settings,
        ),
    )

@Composable
internal fun KiyoriSoftwareHomePage(
    onSearchClick: () -> Unit,
    onAiClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.36f))
        Text(
            text = "Kiyori",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSearchClick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.kiyori_shell_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KiyoriSearchModeButton(
                        icon = Icons.Default.Search,
                        label = stringResource(R.string.kiyori_shell_search_action),
                        selected = true,
                        onClick = onSearchClick,
                    )
                    KiyoriSearchModeButton(
                        icon = Icons.Default.AutoAwesome,
                        label = stringResource(R.string.kiyori_shell_ai_action),
                        selected = false,
                        onClick = onAiClick,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(0.64f))
        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun KiyoriSearchModeButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun KiyoriMinusOnePage() {
    val entries =
        listOf(
            R.string.kiyori_shell_favorites to Icons.Default.Favorite,
            R.string.kiyori_shell_bookmarks to Icons.Default.Bookmark,
            R.string.kiyori_shell_history to Icons.Default.History,
            R.string.kiyori_shell_downloads to Icons.Default.Download,
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.kiyori_shell_minus_one),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        entries.chunked(2).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowEntries.forEach { (labelResId, icon) ->
                    Surface(
                        modifier = Modifier.weight(1f).height(88.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(labelResId),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun KiyoriPrimaryRootPage(
    destination: PrimaryDestination,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = primaryDestinationVisuals.single { item -> item.destination == destination }
    if (destination == PrimaryDestination.SETTINGS_HOME) {
        KiyoriSettingsHomePage(
            title = stringResource(visual.labelResId),
            onOpenAiSettings = onOpenAiSettings,
            modifier = modifier,
        )
        return
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background).padding(bottom = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(visual.labelResId),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun KiyoriSettingsHomePage(
    title: String,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .padding(bottom = 72.dp),
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = onOpenAiSettings,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.kiyori_shell_ai_settings),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KiyoriFullScreenWebSearchPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text(stringResource(R.string.kiyori_shell_web_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.app_content_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.kiyori_shell_search_hint)) },
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

@Composable
internal fun KiyoriBottomNavigation(
    selectedDestination: PrimaryDestination,
    alpha: Float,
    onDestinationSelected: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            primaryDestinationVisuals.forEach { item ->
                val label = stringResource(item.labelResId)
                NavigationBarItem(
                    selected = selectedDestination == item.destination,
                    onClick = { onDestinationSelected(item.destination) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = label,
                        )
                    },
                    alwaysShowLabel = false,
                )
            }
        }
    }
}
