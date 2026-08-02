package com.kiyori.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun KiyoriSemanticTone.resolveColors(): KiyoriSemanticColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return resolveKiyoriSemanticColors(this, isDark)
}

@Composable
internal fun kiyoriWeatherSunColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return resolveKiyoriWeatherSunColor(isDark)
}
