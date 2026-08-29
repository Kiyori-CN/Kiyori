package com.kiyori.design.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Kiyori 的纯 Material 根主题。
 *
 * 调用方必须先解析颜色、字体和背景效果；这里仅保持统一的 MaterialTheme 与根背景组合顺序。
 */
@Composable
fun KiyoriTheme(
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    typography: Typography = KiyoriTypography,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = KiyoriMaterialShapes,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .then(modifier),
        ) {
            content()
        }
    }
}
