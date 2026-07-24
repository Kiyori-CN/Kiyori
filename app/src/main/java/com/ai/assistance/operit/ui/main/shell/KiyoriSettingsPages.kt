package com.ai.assistance.operit.ui.main.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

internal enum class KiyoriSettingsHomeLayout {
    COMPACT,
    EXPANDED,
}

internal fun resolveKiyoriSettingsHomeLayout(widthDp: Float): KiyoriSettingsHomeLayout =
    if (widthDp < 840f) {
        KiyoriSettingsHomeLayout.COMPACT
    } else {
        KiyoriSettingsHomeLayout.EXPANDED
    }

@Composable
internal fun KiyoriSettingsHomePage(
    title: String,
    onOpenAiSettings: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        val layout = resolveKiyoriSettingsHomeLayout(maxWidth.value)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 22.dp,
                end = 20.dp,
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 1080.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.kiyori_settings_home_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val contentModifier = Modifier.fillMaxWidth().widthIn(max = 1080.dp)
                if (layout == KiyoriSettingsHomeLayout.EXPANDED) {
                    Row(
                        modifier = contentModifier,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        KiyoriSettingsGroup(
                            title = stringResource(R.string.kiyori_settings_group_browse_content),
                            modifier = Modifier.weight(1f),
                        ) {
                            KiyoriSettingsEntryRow(
                                title = stringResource(R.string.kiyori_settings_web_browser),
                                summary = stringResource(R.string.kiyori_settings_web_browser_summary),
                                icon = Icons.Filled.Language,
                                iconColor = MaterialTheme.colorScheme.primary,
                                onClick = onOpenBrowserSettings,
                            )
                        }
                        KiyoriSettingsGroup(
                            title = stringResource(R.string.kiyori_settings_group_ai_system),
                            modifier = Modifier.weight(1f),
                        ) {
                            KiyoriSettingsEntryRow(
                                title = stringResource(R.string.kiyori_shell_ai_settings),
                                summary = stringResource(R.string.kiyori_settings_ai_summary),
                                icon = Icons.Filled.AutoAwesome,
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                onClick = onOpenAiSettings,
                            )
                        }
                    }
                } else {
                    Column(modifier = contentModifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        KiyoriSettingsGroup(
                            title = stringResource(R.string.kiyori_settings_group_browse_content),
                        ) {
                            KiyoriSettingsEntryRow(
                                title = stringResource(R.string.kiyori_settings_web_browser),
                                summary = stringResource(R.string.kiyori_settings_web_browser_summary),
                                icon = Icons.Filled.Language,
                                iconColor = MaterialTheme.colorScheme.primary,
                                onClick = onOpenBrowserSettings,
                            )
                        }
                        KiyoriSettingsGroup(
                            title = stringResource(R.string.kiyori_settings_group_ai_system),
                        ) {
                            KiyoriSettingsEntryRow(
                                title = stringResource(R.string.kiyori_shell_ai_settings),
                                summary = stringResource(R.string.kiyori_settings_ai_summary),
                                icon = Icons.Filled.AutoAwesome,
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                onClick = onOpenAiSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KiyoriBrowserSettingsPage(
    onBack: () -> Unit,
    onOpenSearchHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coordinator = remember(context) {
        BrowserPresentationCoordinator.getInstance(context.applicationContext)
    }
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    val searchHistory by historyStore.searchHistoryFlow.collectAsState(initial = emptyList())
    var runtimeState by remember(coordinator) { mutableStateOf(coordinator.browserSettingsState()) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }

    KiyoriSettingsScaffold(
        title = stringResource(R.string.web_session_browser_settings),
        subtitle = stringResource(R.string.kiyori_browser_settings_summary),
        onBack = onBack,
        modifier = modifier,
    ) {
        KiyoriSettingsSection(title = stringResource(R.string.kiyori_browser_settings_search_section)) {
            KiyoriSettingsValueRow(
                title = stringResource(R.string.web_session_search_engine),
                summary = searchEngine.displayName,
                icon = Icons.Filled.Search,
                onClick = { showSearchEngineDialog = true },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
            KiyoriSettingsValueRow(
                title = stringResource(R.string.web_session_search_history),
                summary =
                    stringResource(
                        R.string.kiyori_browser_settings_search_history_count,
                        searchHistory.size,
                    ),
                icon = Icons.Filled.Search,
                onClick = onOpenSearchHistory,
            )
        }

        KiyoriSettingsSection(title = stringResource(R.string.kiyori_browser_settings_window_section)) {
            Text(
                text = stringResource(R.string.kiyori_browser_settings_default_profile),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            KiyoriSettingsChoicePair(
                first =
                    KiyoriSettingsChoice(
                        title = stringResource(R.string.web_session_normal_window),
                        summary = stringResource(R.string.kiyori_browser_settings_normal_profile_summary),
                        icon = Icons.Filled.Language,
                        selected = runtimeState.defaultProfile == WebSessionProfile.NORMAL,
                        enabled = true,
                        onClick = {
                            coordinator.setDefaultSessionProfile(WebSessionProfile.NORMAL)
                            runtimeState = coordinator.browserSettingsState()
                        },
                    ),
                second =
                    KiyoriSettingsChoice(
                        title = stringResource(R.string.web_session_incognito_title),
                        summary =
                            if (runtimeState.incognitoAvailability.isAvailable) {
                                stringResource(R.string.kiyori_browser_settings_incognito_profile_summary)
                            } else {
                                runtimeState.incognitoAvailability.localizedDescription()
                            },
                        icon = Icons.Filled.VisibilityOff,
                        selected = runtimeState.defaultProfile == WebSessionProfile.INCOGNITO,
                        enabled = runtimeState.incognitoAvailability.isAvailable,
                        onClick = {
                            coordinator.setDefaultSessionProfile(WebSessionProfile.INCOGNITO)
                            runtimeState = coordinator.browserSettingsState()
                        },
                    ),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.kiyori_browser_settings_user_agent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            KiyoriSettingsChoicePair(
                first =
                    KiyoriSettingsChoice(
                        title = stringResource(R.string.kiyori_browser_settings_desktop_mode),
                        summary = stringResource(R.string.kiyori_browser_settings_desktop_mode_summary),
                        icon = Icons.Filled.Computer,
                        selected = runtimeState.isDesktopMode,
                        enabled = true,
                        onClick = {
                            coordinator.setDesktopMode(true)
                            runtimeState = coordinator.browserSettingsState()
                        },
                    ),
                second =
                    KiyoriSettingsChoice(
                        title = stringResource(R.string.kiyori_browser_settings_mobile_mode),
                        summary = stringResource(R.string.kiyori_browser_settings_mobile_mode_summary),
                        icon = Icons.Filled.PhoneAndroid,
                        selected = !runtimeState.isDesktopMode,
                        enabled = true,
                        onClick = {
                            coordinator.setDesktopMode(false)
                            runtimeState = coordinator.browserSettingsState()
                        },
                    ),
            )
        }

        KiyoriSettingsSection(title = stringResource(R.string.kiyori_browser_settings_site_data_section)) {
            KiyoriSettingsInfoBlock(
                title = stringResource(R.string.web_session_normal_window),
                text = stringResource(R.string.kiyori_browser_settings_normal_data_summary),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            KiyoriSettingsInfoBlock(
                title = stringResource(R.string.web_session_incognito_title),
                text = stringResource(R.string.web_session_incognito_ai_notice),
            )
        }
    }

    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text(stringResource(R.string.web_session_search_engine)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(WebSessionSearchEngine.entries, key = { engine -> engine.id }) { engine ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.RadioButton) {
                                        scope.launch { historyStore.setSearchEngine(engine) }
                                        showSearchEngineDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = engine == searchEngine,
                                onClick = null,
                            )
                            Text(text = engine.displayName, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun KiyoriBrowserSearchHistoryPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val records by historyStore.searchHistoryFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        KiyoriSettingsTopBar(
            title = stringResource(R.string.web_session_search_history),
            subtitle = stringResource(R.string.kiyori_browser_search_history_summary),
            onBack = onBack,
            action = {
                TextButton(
                    enabled = records.isNotEmpty(),
                    onClick = { scope.launch { historyStore.clearSearchHistory() } },
                ) {
                    Text(stringResource(R.string.web_session_clear_search_history))
                }
            },
        )
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.kiyori_browser_search_history_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(records, key = { record -> record.id }) { record ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.query,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = record.targetUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = dateFormat.format(Date(record.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { scope.launch { historyStore.deleteSearchHistory(record.id) } },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KiyoriSettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        KiyoriSettingsTopBar(title = title, subtitle = subtitle, onBack = onBack)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun KiyoriSettingsTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            action?.invoke()
        }
    }
}

@Composable
private fun KiyoriSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            content()
        }
    }
}

@Composable
private fun KiyoriSettingsEntryRow(
    title: String,
    summary: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = iconColor.copy(alpha = 0.14f),
            contentColor = iconColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KiyoriSettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun KiyoriSettingsValueRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class KiyoriSettingsChoice(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun KiyoriSettingsChoicePair(
    first: KiyoriSettingsChoice,
    second: KiyoriSettingsChoice,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KiyoriSettingsChoiceCard(first, Modifier.weight(1f))
        KiyoriSettingsChoiceCard(second, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun KiyoriSettingsChoiceCard(
    choice: KiyoriSettingsChoice,
    modifier: Modifier,
) {
    Surface(
        onClick = choice.onClick,
        enabled = choice.enabled,
        modifier = modifier.heightIn(min = 126.dp),
        shape = RoundedCornerShape(16.dp),
        color =
            if (choice.selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor =
            if (choice.enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            },
        border =
            BorderStroke(
                if (choice.selected) 2.dp else 1.dp,
                if (choice.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(imageVector = choice.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = choice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = choice.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun KiyoriSettingsInfoBlock(
    title: String,
    text: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WebSessionIncognitoAvailability.localizedDescription(): String =
    when (this) {
        WebSessionIncognitoAvailability.AVAILABLE ->
            stringResource(R.string.kiyori_browser_settings_incognito_profile_summary)
        WebSessionIncognitoAvailability.UNSUPPORTED ->
            stringResource(R.string.web_session_incognito_unavailable_summary)
        WebSessionIncognitoAvailability.PROFILE_RESET_FAILED ->
            stringResource(R.string.web_session_incognito_reset_failed)
    }
