package com.kiyori.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 材质只消费当前主题的表面色；不创建独立色板或改写 AI 的个性化输入背景。 */
object KiyoriSurfaceTokens {
    val flatElevation = 0.dp
    val popupElevation = 3.dp
    val sheetElevation = 3.dp
    val outlineWidth = 0.5.dp
    const val modalScrimAlpha = 0.32f
    const val popupScrimAlpha = 0.16f
}

@Immutable
data class KiyoriSurfaceColors(
    val card: Color,
    val popup: Color,
    val sheet: Color,
    val outline: Color,
    val modalScrim: Color,
)

internal fun resolveKiyoriSurfaceColors(scheme: ColorScheme): KiyoriSurfaceColors =
    KiyoriSurfaceColors(
        card = scheme.surfaceContainerLow,
        popup = scheme.surfaceContainerHigh,
        sheet = scheme.surfaceContainerLow,
        outline = scheme.outlineVariant,
        modalScrim = scheme.scrim.copy(alpha = KiyoriSurfaceTokens.modalScrimAlpha),
    )

@Composable
fun kiyoriSurfaceColors(): KiyoriSurfaceColors = resolveKiyoriSurfaceColors(MaterialTheme.colorScheme)
