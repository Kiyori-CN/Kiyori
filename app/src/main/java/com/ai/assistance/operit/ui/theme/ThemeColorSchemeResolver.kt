package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.kiyori.design.theme.resolveKiyoriColorScheme

fun resolveThemeColorScheme(
    context: Context,
    snapshot: ThemePreferenceSnapshot,
): ColorScheme =
    resolveThemeColorScheme(darkTheme = resolveDarkTheme(context, snapshot))

fun resolveThemeColorScheme(darkTheme: Boolean): ColorScheme =
    resolveKiyoriColorScheme(darkTheme)

private fun resolveDarkTheme(
    context: Context,
    snapshot: ThemePreferenceSnapshot,
): Boolean {
    if (!snapshot.useSystemTheme) {
        return snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK
    }
    return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}
