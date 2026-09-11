package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge
import com.kiyori.design.theme.KiyoriSemanticTone
import com.kiyori.design.theme.KiyoriUiShapes
import com.kiyori.design.theme.resolveColors

internal const val WEB_SESSION_DRAWER_HEADER_HEIGHT_DP = 52
internal const val WEB_SESSION_DRAWER_HEADER_START_PADDING_DP = 18
internal const val WEB_SESSION_DRAWER_HEADER_END_PADDING_DP = 8
internal const val WEB_SESSION_DRAWER_HEADER_ICON_SIZE_DP = 34
internal const val WEB_SESSION_DRAWER_HEADER_TITLE_GAP_DP = 8
internal const val WEB_SESSION_DRAWER_TITLE_ACTION_SIZE_DP = 40
internal val WebSessionDrawerTitleActionShape = KiyoriUiShapes.control
internal val WebSessionDrawerTitleActionContentAlignment = Alignment.Center

@Composable
internal fun WebSessionDrawerHeader(
    title: String,
    leadingIcon: ImageVector,
    tone: WebSessionBrowserMenuTone,
    modifier: Modifier = Modifier,
    countText: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleTakesRemainingSpace: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WEB_SESSION_DRAWER_HEADER_HEIGHT_DP.dp)
                .padding(
                    start =
                        if (navigationIcon == null) {
                            WEB_SESSION_DRAWER_HEADER_START_PADDING_DP.dp
                        } else {
                            4.dp
                        },
                    end = WEB_SESSION_DRAWER_HEADER_END_PADDING_DP.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.invoke()
        WebSessionBrowserMenuIconBadge(
            imageVector = leadingIcon,
            tone = tone,
            contentDescription = null,
            containerSize = WEB_SESSION_DRAWER_HEADER_ICON_SIZE_DP.dp,
            iconSize = 18.dp,
            shape = KiyoriUiShapes.control,
        )
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(start = WEB_SESSION_DRAWER_HEADER_TITLE_GAP_DP.dp)
                    .then(
                        if (titleTakesRemainingSpace) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        },
                    ),
        )
        titleActions()
        if (!countText.isNullOrBlank()) {
            Text(
                text = countText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (!titleTakesRemainingSpace) {
            Spacer(modifier = Modifier.weight(1f))
        }
        actions()
    }
}

@Composable
internal fun WebSessionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    tone: KiyoriSemanticTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    WebSessionSearchFieldContent(
        value = value,
        onValueChange = onValueChange,
        onClear = onClear,
        placeholder = placeholder,
        cursorColor = colors.icon,
        modifier = modifier,
    )
}

@Composable
internal fun WebSessionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    tone: WebSessionBrowserMenuTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    WebSessionSearchFieldContent(
        value = value,
        onValueChange = onValueChange,
        onClear = onClear,
        placeholder = placeholder,
        cursorColor = colors.icon,
        modifier = modifier,
    )
}

@Composable
private fun WebSessionSearchFieldContent(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    cursorColor: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = KiyoriUiShapes.control,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
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
                cursorBrush = SolidColor(cursorColor),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun WebSessionFilterChip(
    label: String,
    selected: Boolean,
    tone: KiyoriSemanticTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    WebSessionFilterChipContent(
        label = label,
        selected = selected,
        iconColor = colors.icon,
        containerColor = colors.container,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun WebSessionFilterChip(
    label: String,
    selected: Boolean,
    tone: WebSessionBrowserMenuTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    WebSessionFilterChipContent(
        label = label,
        selected = selected,
        iconColor = colors.icon,
        containerColor = colors.container,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun WebSessionFilterChipContent(
    label: String,
    selected: Boolean,
    iconColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(36.dp)
                .clickable(onClick = onClick),
        shape = KiyoriUiShapes.control,
        color = if (selected) containerColor else MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                if (selected) iconColor else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) iconColor else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun WebSessionSheetScaffold(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            navigationIcon?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        content()
    }
}

@Composable
internal fun WebSessionSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    tone: KiyoriSemanticTone = KiyoriSemanticTone.BLUE,
) {
    val colors = tone.resolveColors()
    WebSessionSectionLabelContent(
        text = text,
        color = colors.icon,
        modifier = modifier,
    )
}

@Composable
internal fun WebSessionSectionLabel(
    text: String,
    tone: WebSessionBrowserMenuTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.resolveColors()
    WebSessionSectionLabelContent(
        text = text,
        color = colors.icon,
        modifier = modifier,
    )
}

@Composable
private fun WebSessionSectionLabelContent(
    text: String,
    color: Color,
    modifier: Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 2.dp)
    )
}

@Composable
internal fun WebSessionItemCard(
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    highlightTone: KiyoriSemanticTone = KiyoriSemanticTone.BLUE,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = highlightTone.resolveColors()
    WebSessionItemCardContent(
        onClick = onClick,
        highlighted = highlighted,
        highlightIconColor = colors.icon,
        highlightContainerColor = colors.container,
        modifier = modifier,
        content = content,
    )
}

@Composable
internal fun WebSessionItemCard(
    highlightTone: WebSessionBrowserMenuTone,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = highlightTone.resolveColors()
    WebSessionItemCardContent(
        onClick = onClick,
        highlighted = highlighted,
        highlightIconColor = colors.icon,
        highlightContainerColor = colors.container,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun WebSessionItemCardContent(
    onClick: (() -> Unit)?,
    highlighted: Boolean,
    highlightIconColor: Color,
    highlightContainerColor: Color,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = KiyoriUiShapes.field
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        shape = shape,
        color =
            if (highlighted) {
                highlightContainerColor
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor =
            if (highlighted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (highlighted) {
                        highlightIconColor.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
            ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        content()
    }
}

@Composable
internal fun WebSessionEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    tone: KiyoriSemanticTone = KiyoriSemanticTone.BLUE,
) {
    WebSessionEmptyStateContent(
        title = title,
        message = message,
        modifier = modifier,
        badge = {
            KiyoriSemanticIconBadge(
                imageVector = icon,
                tone = tone,
                contentDescription = null,
                containerSize = 44.dp,
                iconSize = 24.dp,
                shape = CircleShape,
            )
        },
    )
}

@Composable
internal fun WebSessionEmptyState(
    icon: ImageVector,
    title: String,
    tone: WebSessionBrowserMenuTone,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    WebSessionEmptyStateContent(
        title = title,
        message = message,
        modifier = modifier,
        badge = {
            WebSessionBrowserMenuIconBadge(
                imageVector = icon,
                tone = tone,
                contentDescription = null,
                containerSize = 44.dp,
                iconSize = 24.dp,
                shape = CircleShape,
            )
        },
    )
}

@Composable
private fun WebSessionEmptyStateContent(
    title: String,
    message: String?,
    modifier: Modifier,
    badge: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KiyoriUiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            badge()

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
