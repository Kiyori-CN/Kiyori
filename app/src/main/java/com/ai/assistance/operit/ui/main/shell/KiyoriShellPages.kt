package com.ai.assistance.operit.ui.main.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen
import kotlinx.coroutines.launch

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
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(bottom = 72.dp),
    ) {
        val layout = resolveKiyoriSoftwareHomeLayout(maxWidth.value)
        when (layout) {
            KiyoriSoftwareHomeLayout.COMPACT ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(0.3f))
                    KiyoriHomeBrandHero(compact = true)
                    Spacer(modifier = Modifier.height(30.dp))
                    KiyoriHomeSearchCard(
                        onSearchClick = onSearchClick,
                        onAiClick = onAiClick,
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.weight(0.7f))
                }

            KiyoriSoftwareHomeLayout.MEDIUM,
            KiyoriSoftwareHomeLayout.EXPANDED ->
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 1080.dp)
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    if (layout == KiyoriSoftwareHomeLayout.EXPANDED) {
                                        56.dp
                                    } else {
                                        32.dp
                                    },
                            ),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KiyoriHomeBrandHero(
                        compact = false,
                        modifier = Modifier.weight(0.9f),
                    )
                    KiyoriHomeSearchCard(
                        onSearchClick = onSearchClick,
                        onAiClick = onAiClick,
                        modifier = Modifier.weight(1.1f).widthIn(max = 520.dp),
                    )
                }
        }
    }
}

internal enum class KiyoriSoftwareHomeLayout {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal fun resolveKiyoriSoftwareHomeLayout(widthDp: Float): KiyoriSoftwareHomeLayout =
    when {
        widthDp >= 840f -> KiyoriSoftwareHomeLayout.EXPANDED
        widthDp >= 600f -> KiyoriSoftwareHomeLayout.MEDIUM
        else -> KiyoriSoftwareHomeLayout.COMPACT
    }

@Composable
private fun KiyoriHomeBrandHero(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_kiyori_app_icon),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(if (compact) 72.dp else 88.dp),
        )
        Text(
            text = "Kiyori",
            style =
                if (compact) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.displayMedium
                },
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.kiyori_shell_home_subtitle),
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun KiyoriHomeSearchCard(
    onSearchClick: () -> Unit,
    onAiClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSearchClick,
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.kiyori_shell_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
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
        shape = RoundedCornerShape(14.dp),
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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

@Composable
internal fun KiyoriFullScreenWebSearchPage(
    onBack: () -> Unit,
    onSubmitSearch: (KiyoriWebSearchRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val browserCoordinator = remember(context) { BrowserPresentationCoordinator.getInstance(context) }
    var profileState by
        remember(browserCoordinator) {
            mutableStateOf(browserCoordinator.newSessionProfileState())
        }
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    val searchHistory by historyStore.searchHistoryFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var isEnginePanelVisible by rememberSaveable { mutableStateOf(false) }
    var selectedProfile by rememberSaveable { mutableStateOf(profileState.defaultProfile) }
    val profileNotice =
        when {
            !profileState.incognitoAvailability.isAvailable ->
                when (profileState.incognitoAvailability) {
                    WebSessionIncognitoAvailability.PROFILE_RESET_FAILED ->
                        stringResource(R.string.web_session_incognito_reset_failed)
                    WebSessionIncognitoAvailability.UNSUPPORTED ->
                        stringResource(R.string.web_session_incognito_unavailable_summary)
                    WebSessionIncognitoAvailability.AVAILABLE -> null
                }
            selectedProfile == WebSessionProfile.INCOGNITO ->
                stringResource(R.string.web_session_incognito_ai_notice)
            else -> null
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        WebSessionBrowserSearchScreen(
            currentUrl = "",
            searchEngine = searchEngine,
            searchHistory = searchHistory,
            draft = query,
            isEnginePanelVisible = isEnginePanelVisible,
            onDraftChange = { query = it },
            onEnginePanelVisibleChange = { isEnginePanelVisible = it },
            onBack = onBack,
            onSubmit = {
                profileState = browserCoordinator.newSessionProfileState()
                if (
                    selectedProfile == WebSessionProfile.NORMAL ||
                        profileState.incognitoAvailability.isAvailable
                ) {
                    resolveKiyoriWebSearchRequest(query, searchEngine, selectedProfile)
                        ?.let(onSubmitSearch)
                }
            },
            onSelectEngine = { engine ->
                scope.launch { historyStore.setSearchEngine(engine) }
            },
            onOpenSearchRecord = { record ->
                profileState = browserCoordinator.newSessionProfileState()
                if (
                    selectedProfile == WebSessionProfile.NORMAL ||
                        profileState.incognitoAvailability.isAvailable
                ) {
                    onSubmitSearch(
                        KiyoriWebSearchRequest(
                            query = record.query,
                            targetUrl = record.targetUrl,
                            profile = selectedProfile,
                        ),
                    )
                }
            },
            onDeleteSearchRecord = { recordId ->
                scope.launch { historyStore.deleteSearchHistory(recordId) }
            },
            onClearSearchHistory = {
                scope.launch { historyStore.clearSearchHistory() }
            },
            onCopyCurrentUrl = {},
            onOpenCurrentUrl = {},
            onUseCurrentUrl = {},
            profileNotice = profileNotice,
            trailingAction = {
                KiyoriSearchProfileAction(
                    selectedProfile = selectedProfile,
                    incognitoAvailability = profileState.incognitoAvailability,
                    onToggle = {
                        val requestedProfile =
                            if (selectedProfile == WebSessionProfile.INCOGNITO) {
                                WebSessionProfile.NORMAL
                            } else {
                                WebSessionProfile.INCOGNITO
                            }
                        if (browserCoordinator.setDefaultSessionProfile(requestedProfile)) {
                            selectedProfile = requestedProfile
                        }
                        profileState = browserCoordinator.newSessionProfileState()
                    },
                )
            },
            modifier = Modifier.fillMaxHeight().widthIn(max = 920.dp).fillMaxWidth(),
        )
    }
}

internal data class KiyoriWebSearchRequest(
    val query: String,
    val targetUrl: String,
    val profile: WebSessionProfile,
)

internal fun resolveKiyoriWebSearchRequest(
    rawQuery: String,
    searchEngine: WebSessionSearchEngine,
    profile: WebSessionProfile,
): KiyoriWebSearchRequest? {
    val query = rawQuery.trim()
    if (query.isBlank()) {
        return null
    }
    return KiyoriWebSearchRequest(
        query = query,
        targetUrl = BrowserAddressResolver.resolve(query, searchEngine),
        profile = profile,
    )
}

@Composable
private fun KiyoriSearchProfileAction(
    selectedProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
    onToggle: () -> Unit,
) {
    val enabled = incognitoAvailability.isAvailable
    val selected = selectedProfile == WebSessionProfile.INCOGNITO
    Surface(
        modifier =
            Modifier
                .size(40.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onToggle),
        shape = CircleShape,
        color =
            when {
                !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        contentColor =
            when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = stringResource(R.string.web_session_incognito_mode),
                modifier = Modifier.size(20.dp),
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
