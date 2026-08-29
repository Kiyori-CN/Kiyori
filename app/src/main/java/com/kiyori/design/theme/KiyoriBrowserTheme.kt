package com.kiyori.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

/** Keeps browser chrome neutral while the surrounding AI and app shell use the accent palette. */
@Composable
fun KiyoriBrowserTheme(content: @Composable () -> Unit) {
    val parentColorScheme = MaterialTheme.colorScheme
    val parentTypography = MaterialTheme.typography
    val browserColorScheme =
        if (parentColorScheme.background.luminance() < 0.5f) {
            KiyoriBrowserDarkColorScheme
        } else {
            KiyoriBrowserLightColorScheme
        }

    MaterialTheme(
        colorScheme = browserColorScheme,
        typography = parentTypography,
        shapes = KiyoriMaterialShapes,
        content = content,
    )
}
