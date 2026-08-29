package com.ai.assistance.operit.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme
import com.kiyori.design.theme.KiyoriTypography
import com.kiyori.design.theme.KiyoriMaterialShapes

@Composable
fun OperitUtilityTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = false)
    val themeMode by
        preferencesManager.themeMode.collectAsState(initial = UserPreferencesManager.THEME_MODE_LIGHT)
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        if (useSystemTheme) {
            systemDarkTheme
        } else {
            themeMode == UserPreferencesManager.THEME_MODE_DARK
        }

    MaterialTheme(
        colorScheme = resolveThemeColorScheme(darkTheme),
        typography = KiyoriTypography,
        shapes = KiyoriMaterialShapes,
        content = content,
    )
}
