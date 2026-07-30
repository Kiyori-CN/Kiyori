package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone
import com.ai.assistance.operit.ui.theme.resolveColors

@Composable
fun KiyoriSemanticIconBadge(
    imageVector: ImageVector,
    tone: KiyoriSemanticTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
) {
    val colors = tone.resolveColors()
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.size(containerSize),
        shape = shape,
        color = colors.container.copy(alpha = alpha),
        contentColor = colors.icon.copy(alpha = alpha),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = colors.icon.copy(alpha = alpha),
            )
        }
    }
}

@Composable
fun KiyoriSemanticIconBadge(
    painter: Painter,
    tone: KiyoriSemanticTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
) {
    val colors = tone.resolveColors()
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        modifier = modifier.size(containerSize),
        shape = shape,
        color = colors.container.copy(alpha = alpha),
        contentColor = colors.icon.copy(alpha = alpha),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = colors.icon.copy(alpha = alpha),
            )
        }
    }
}
