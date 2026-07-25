package com.ai.assistance.operit.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_AUTO
import com.ai.assistance.operit.ui.theme.Typography
import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme

@Composable
fun OperitUtilityTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = false)
    val themeMode by
        preferencesManager.themeMode.collectAsState(initial = UserPreferencesManager.THEME_MODE_LIGHT)
    val useCustomColors by preferencesManager.useCustomColors.collectAsState(initial = false)
    val customPrimaryColor by preferencesManager.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryColor by preferencesManager.customSecondaryColor.collectAsState(initial = null)
    val onColorMode by preferencesManager.onColorMode.collectAsState(initial = ON_COLOR_MODE_AUTO)
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        if (useSystemTheme) {
            systemDarkTheme
        } else {
            themeMode == UserPreferencesManager.THEME_MODE_DARK
        }

    MaterialTheme(
        colorScheme =
            resolveThemeColorScheme(
                darkTheme = darkTheme,
                useCustomColors = useCustomColors,
                customPrimaryColor = customPrimaryColor,
                customSecondaryColor = customSecondaryColor,
                onColorMode = onColorMode,
            ),
        typography = Typography,
        content = content,
    )
}
