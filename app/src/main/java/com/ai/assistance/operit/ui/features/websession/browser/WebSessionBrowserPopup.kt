package com.ai.assistance.operit.ui.features.websession.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val WebSessionBrowserPopupShape = RoundedCornerShape(10.dp)
internal val WebSessionBrowserPopupElevation = 8.dp
internal val WebSessionBrowserPopupItemHeight = 40.dp

@Composable
internal fun WebSessionBrowserPopupScrim(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
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
) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        onClick = onClick,
        modifier = modifier.height(WebSessionBrowserPopupItemHeight),
        contentPadding = PaddingValues(horizontal = 14.dp),
    )
}

@Composable
internal fun WebSessionBrowserModalDialog(
    onDismissRequest: () -> Unit,
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
                    .safeDrawingPadding()
                    .padding(20.dp),
            contentAlignment = Alignment.Center,
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
