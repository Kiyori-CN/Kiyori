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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.resolveColors
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
    val selectedProfileTone = resolveWebSessionBrowserProfileTone(selectedProfile)
    val selectedProfileColors = selectedProfileTone.resolveColors()
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
                        profile = WebSessionProfile.NORMAL,
                        title = stringResource(R.string.web_session_normal_window),
                        count = normalCount,
                        selected = selectedProfile == WebSessionProfile.NORMAL,
                        enabled = true,
                        onClick = { onProfileChange(WebSessionProfile.NORMAL) },
                        modifier = Modifier.weight(1f),
                    )
                    WindowProfileSelector(
                        profile = WebSessionProfile.INCOGNITO,
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
                            tone = selectedProfileTone,
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
                            tone = selectedProfileTone,
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
                                selectedProfileColors.container
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        contentColor =
                            if (canUseSelectedProfile) {
                                selectedProfileColors.icon
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        border =
                            if (canUseSelectedProfile) {
                                BorderStroke(1.dp, selectedProfileColors.icon.copy(alpha = 0.56f))
                            } else {
                                null
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
    profile: WebSessionProfile,
    title: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = resolveWebSessionBrowserProfileTone(profile)
    val colors = tone.resolveColors()
    Column(
        modifier =
            modifier
                .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
                .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color =
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        selected -> colors.icon
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color =
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        selected -> colors.icon
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(start = 7.dp),
                maxLines = 1,
            )
        }
        Box(
            modifier =
                Modifier
                    .width(48.dp)
                    .height(if (selected) 3.dp else 2.dp)
                    .background(
                        color =
                            when {
                                !enabled ->
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                selected -> colors.icon
                                else -> colors.icon.copy(alpha = 0.32f)
                            },
                        shape = CircleShape,
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
    tone: KiyoriSemanticTone,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.container,
            border = BorderStroke(1.dp, colors.icon.copy(alpha = 0.3f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.icon,
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
    val profileTone = resolveWebSessionBrowserProfileTone(tab.profile)
    val profileColors = profileTone.resolveColors()
    val borderColor =
        if (tab.isActive) {
            profileColors.icon
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
        }
    val thumbnail =
        remember(tab.thumbnail, tab.thumbnailUpdatedAt) {
            tab.thumbnail?.asImageBitmap()
        }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        color =
            if (tab.isActive) {
                profileColors.container.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = BorderStroke(if (tab.isActive) 2.dp else 1.dp, borderColor),
        tonalElevation = if (tab.isActive) 1.dp else 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(BROWSER_TAB_THUMBNAIL_ASPECT_RATIO)
                        .background(profileColors.container.copy(alpha = 0.38f)),
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
    val colors = resolveWebSessionBrowserProfileTone(tab.profile).resolveColors()
    Surface(
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color = colors.container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = identity,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.icon,
            )
        }
    }
}

internal fun resolveWebSessionBrowserProfileTone(
    profile: WebSessionProfile,
): KiyoriSemanticTone =
    when (profile) {
        WebSessionProfile.NORMAL -> KiyoriSemanticTone.BLUE
        WebSessionProfile.INCOGNITO -> KiyoriSemanticTone.PURPLE
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
    val tone = if (isError) KiyoriSemanticTone.RED else KiyoriSemanticTone.BLUE
    val colors = tone.resolveColors()
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color =
                if (enabled) {
                    colors.container
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            contentColor =
                if (enabled) {
                    colors.icon
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}
