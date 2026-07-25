package com.ai.assistance.operit.ui.features.websession.browser.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BROWSER_TAB_THUMBNAIL_ASPECT_RATIO
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionBrowserTab
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionIncognitoAvailability
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import java.net.URI
import kotlinx.coroutines.delay

@Composable
internal fun WebSessionBrowserTabOverview(
    isVisible: Boolean,
    tabs: List<WebSessionBrowserTab>,
    selectedProfile: WebSessionProfile,
    incognitoAvailability: WebSessionIncognitoAvailability,
    columnCount: Int,
    onDismissRequest: () -> Unit,
    onHidden: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onProfileChange: (WebSessionProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(180)
            onHidden()
        }
    }

    val normalCount = tabs.count { tab -> tab.profile == WebSessionProfile.NORMAL }
    val incognitoCount = tabs.count { tab -> tab.profile == WebSessionProfile.INCOGNITO }
    val visibleTabs = tabs.filter { tab -> tab.profile == selectedProfile }
    val canUseSelectedProfile =
        selectedProfile == WebSessionProfile.NORMAL || incognitoAvailability.isAvailable

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxSize(),
        enter =
            fadeIn(tween(180)) +
                slideInVertically(tween(180)) { fullHeight -> fullHeight / 24 },
        exit =
            fadeOut(tween(150)) +
                slideOutVertically(tween(150)) { fullHeight -> fullHeight / 30 },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WindowProfileSelector(
                        title = stringResource(R.string.web_session_normal_window),
                        count = normalCount,
                        selected = selectedProfile == WebSessionProfile.NORMAL,
                        enabled = true,
                        onClick = { onProfileChange(WebSessionProfile.NORMAL) },
                        modifier = Modifier.weight(1f),
                    )
                    WindowProfileSelector(
                        title = stringResource(R.string.web_session_incognito_title),
                        count = incognitoCount,
                        selected = selectedProfile == WebSessionProfile.INCOGNITO,
                        enabled = incognitoAvailability.isAvailable,
                        onClick = { onProfileChange(WebSessionProfile.INCOGNITO) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!incognitoAvailability.isAvailable) {
                    IncognitoAvailabilityNotice(
                        availability = incognitoAvailability,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                    )
                }

                when {
                    !canUseSelectedProfile ->
                        BrowserTabEmptyState(
                            icon = Icons.Filled.VisibilityOff,
                            title = stringResource(R.string.web_session_incognito_unavailable),
                            description = incognitoAvailability.description(),
                            modifier = Modifier.weight(1f),
                        )

                    visibleTabs.isEmpty() ->
                        BrowserTabEmptyState(
                            icon =
                                if (selectedProfile == WebSessionProfile.INCOGNITO) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Language
                                },
                            title =
                                stringResource(
                                    R.string.web_session_no_profile_tabs,
                                    selectedProfile.displayName(),
                                ),
                            description = stringResource(R.string.web_session_new_tab),
                            modifier = Modifier.weight(1f),
                        )

                    else ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnCount.coerceAtLeast(1)),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                items = visibleTabs,
                                key = { _, tab -> tab.sessionId },
                            ) { index, tab ->
                                BrowserTabOverviewCard(
                                    tabNumber = index + 1,
                                    tab = tab,
                                    onSelect = { onSelectTab(tab.sessionId) },
                                    onClose = { onCloseTab(tab.sessionId) },
                                )
                            }
                        }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 28.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabOverviewBottomAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.web_session_back),
                        enabled = true,
                        onClick = onDismissRequest,
                    )
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color =
                            if (canUseSelectedProfile) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        contentColor =
                            if (canUseSelectedProfile) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        tonalElevation = 1.dp,
                        shadowElevation = if (canUseSelectedProfile) 4.dp else 0.dp,
                    ) {
                        IconButton(
                            enabled = canUseSelectedProfile,
                            onClick = onNewTab,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.web_session_new_tab),
                            )
                        }
                    }
                    TabOverviewBottomAction(
                        icon = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.web_session_clear_profile_tabs),
                        enabled = visibleTabs.isNotEmpty(),
                        onClick = onCloseAllTabs,
                        isError = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowProfileSelector(
    title: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
                .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color =
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Surface(
                shape = CircleShape,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .size(width = 48.dp, height = 2.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape,
                    ),
        )
    }
}

@Composable
private fun IncognitoAvailabilityNotice(
    availability: WebSessionIncognitoAvailability,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f),
    ) {
        Text(
            text = availability.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun BrowserTabEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp).size(34.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun BrowserTabOverviewCard(
    tabNumber: Int,
    tab: WebSessionBrowserTab,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val borderColor =
        if (tab.isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    val thumbnail =
        remember(tab.thumbnail, tab.thumbnailUpdatedAt) {
            tab.thumbnail?.asImageBitmap()
        }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (tab.isActive) 2.dp else 1.dp, borderColor),
        tonalElevation = if (tab.isActive) 1.dp else 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(BROWSER_TAB_THUMBNAIL_ASPECT_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    BrowserTabIdentity(tab = tab)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tabNumber.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                    contentColor = Color.White,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tab.title.ifBlank { stringResource(R.string.web_session_new_tab) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tab.url.ifBlank { "about:blank" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BrowserTabIdentity(tab: WebSessionBrowserTab) {
    val identity = remember(tab.title, tab.url) { tabIdentity(tab) }
    Surface(
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color =
            if (tab.profile == WebSessionProfile.INCOGNITO) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = identity,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color =
                    if (tab.profile == WebSessionProfile.INCOGNITO) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
            )
        }
    }
}

@Composable
private fun WebSessionProfile.displayName(): String =
    when (this) {
        WebSessionProfile.NORMAL -> stringResource(R.string.web_session_normal_window)
        WebSessionProfile.INCOGNITO -> stringResource(R.string.web_session_incognito_title)
    }

@Composable
private fun WebSessionIncognitoAvailability.description(): String =
    when (this) {
        WebSessionIncognitoAvailability.AVAILABLE ->
            stringResource(R.string.web_session_incognito_ai_notice)
        WebSessionIncognitoAvailability.UNSUPPORTED ->
            stringResource(R.string.web_session_incognito_unavailable_summary)
        WebSessionIncognitoAvailability.PROFILE_RESET_FAILED ->
            stringResource(R.string.web_session_incognito_reset_failed)
    }

private fun tabIdentity(tab: WebSessionBrowserTab): String {
    val source =
        runCatching { URI(tab.url).host }
            .getOrNull()
            ?.takeIf { host -> host.isNotBlank() }
            ?: tab.title
    return source.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: "K"
}

@Composable
private fun TabOverviewBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isError: Boolean = false,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint =
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
