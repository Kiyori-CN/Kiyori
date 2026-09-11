package com.ai.assistance.operit.ui.features.websession.browser

import com.kiyori.design.theme.kiyoriSurfaceColors

import com.kiyori.design.theme.KiyoriSurfaceTokens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiyori.design.theme.KiyoriUiShapes

internal val WebSessionBrowserPopupShape = KiyoriUiShapes.control
internal val WebSessionBrowserPopupElevation = KiyoriSurfaceTokens.popupElevation
internal val WebSessionBrowserPopupItemHeight = 40.dp

@Composable
internal fun WebSessionBrowserDialogHeader(
    icon: ImageVector,
    tone: WebSessionBrowserMenuTone,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WebSessionBrowserMenuIconBadge(
            imageVector = icon,
            tone = tone,
            contentDescription = null,
            containerSize = 34.dp,
            iconSize = 18.dp,
            shape = KiyoriUiShapes.control,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun WebSessionBrowserDialogSurface(
    icon: ImageVector,
    tone: WebSessionBrowserMenuTone,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = KiyoriUiShapes.dialog,
        color = kiyoriSurfaceColors().popup,
        border = BorderStroke(KiyoriSurfaceTokens.outlineWidth, kiyoriSurfaceColors().outline),
        tonalElevation = KiyoriSurfaceTokens.flatElevation,
        shadowElevation = KiyoriSurfaceTokens.popupElevation,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            WebSessionBrowserDialogHeader(
                icon = icon,
                tone = tone,
                title = title,
            )
            HorizontalDivider(color = kiyoriSurfaceColors().outline)
            content()
        }
    }
}

@Composable
internal fun WebSessionBrowserPopupScrim(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = KiyoriSurfaceTokens.popupScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
    )
}

@Composable
internal fun WebSessionBrowserDropdownItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(WebSessionBrowserPopupItemHeight),
        contentPadding = PaddingValues(horizontal = 14.dp),
    )
}

@Composable
internal fun WebSessionBrowserModalDialog(
    onDismissRequest: () -> Unit,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        com.ai.assistance.operit.ui.components.KiyoriDialogOwnsScrim()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(kiyoriSurfaceColors().modalScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
                    .safeDrawingPadding()
                    .padding(20.dp),
            contentAlignment = contentAlignment,
        ) {
            Box(
                modifier =
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                content = content,
            )
        }
    }
}
