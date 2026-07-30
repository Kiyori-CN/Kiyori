package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

val KiyoriBrowserLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF202124),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8EAED),
        onPrimaryContainer = Color(0xFF202124),
        inversePrimary = Color(0xFFE8EAED),
        secondary = Color(0xFF5F6368),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF1F3F4),
        onSecondaryContainer = Color(0xFF202124),
        tertiary = Color(0xFF3C4043),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF1F3F4),
        onTertiaryContainer = Color(0xFF202124),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF202124),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF202124),
        surfaceVariant = Color(0xFFE8EAED),
        onSurfaceVariant = Color(0xFF5F6368),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF303134),
        inverseOnSurface = Color(0xFFF1F3F4),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF9AA0A6),
        outlineVariant = Color(0xFFDADCE0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFDADCE0),
        surfaceContainer = Color(0xFFF7F7F7),
        surfaceContainerHigh = Color(0xFFF1F3F4),
        surfaceContainerHighest = Color(0xFFE8EAED),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        primaryFixed = Color(0xFFE8EAED),
        primaryFixedDim = Color(0xFFDADCE0),
        onPrimaryFixed = Color(0xFF202124),
        onPrimaryFixedVariant = Color(0xFF5F6368),
        secondaryFixed = Color(0xFFF1F3F4),
        secondaryFixedDim = Color(0xFFE8EAED),
        onSecondaryFixed = Color(0xFF202124),
        onSecondaryFixedVariant = Color(0xFF5F6368),
        tertiaryFixed = Color(0xFFF1F3F4),
        tertiaryFixedDim = Color(0xFFE8EAED),
        onTertiaryFixed = Color(0xFF202124),
        onTertiaryFixedVariant = Color(0xFF5F6368),
    )

val KiyoriBrowserDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFF1F3F4),
        onPrimary = Color(0xFF202124),
        primaryContainer = Color(0xFF303134),
        onPrimaryContainer = Color(0xFFF1F3F4),
        inversePrimary = Color(0xFF202124),
        secondary = Color(0xFFBDC1C6),
        onSecondary = Color(0xFF202124),
        secondaryContainer = Color(0xFF3C4043),
        onSecondaryContainer = Color(0xFFF1F3F4),
        tertiary = Color(0xFF9AA0A6),
        onTertiary = Color(0xFF202124),
        tertiaryContainer = Color(0xFF282A2D),
        onTertiaryContainer = Color(0xFFE8EAED),
        background = Color(0xFF121212),
        onBackground = Color(0xFFE8EAED),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFE8EAED),
        surfaceVariant = Color(0xFF303134),
        onSurfaceVariant = Color(0xFFBDC1C6),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFF1F3F4),
        inverseOnSurface = Color(0xFF202124),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        outline = Color(0xFF9AA0A6),
        outlineVariant = Color(0xFF3C4043),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF303134),
        surfaceDim = Color(0xFF0D0D0D),
        surfaceContainer = Color(0xFF202124),
        surfaceContainerHigh = Color(0xFF282A2D),
        surfaceContainerHighest = Color(0xFF303134),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainerLowest = Color(0xFF0D0D0D),
        primaryFixed = Color(0xFFE8EAED),
        primaryFixedDim = Color(0xFFDADCE0),
        onPrimaryFixed = Color(0xFF202124),
        onPrimaryFixedVariant = Color(0xFF5F6368),
        secondaryFixed = Color(0xFFF1F3F4),
        secondaryFixedDim = Color(0xFFE8EAED),
        onSecondaryFixed = Color(0xFF202124),
        onSecondaryFixedVariant = Color(0xFF5F6368),
        tertiaryFixed = Color(0xFFF1F3F4),
        tertiaryFixedDim = Color(0xFFE8EAED),
        onTertiaryFixed = Color(0xFF202124),
        onTertiaryFixedVariant = Color(0xFF5F6368),
    )

val KiyoriLightColorScheme =
    KiyoriBrowserLightColorScheme.copy(
        primary = Color(0xFF1E88E5),
        onPrimary = Color(0xFF0A1929),
        primaryContainer = Color(0xFFD8ECFF),
        onPrimaryContainer = Color(0xFF12324A),
        inversePrimary = Color(0xFF90CAF9),
        secondary = Color(0xFF536D79),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE4EDF1),
        onSecondaryContainer = Color(0xFF20343D),
        primaryFixed = Color(0xFFD8ECFF),
        primaryFixedDim = Color(0xFF90CAF9),
        onPrimaryFixed = Color(0xFF12324A),
        onPrimaryFixedVariant = Color(0xFF0B5F94),
        secondaryFixed = Color(0xFFE4EDF1),
        secondaryFixedDim = Color(0xFFB9CBD4),
        onSecondaryFixed = Color(0xFF20343D),
        onSecondaryFixedVariant = Color(0xFF374D57),
    )

val KiyoriDarkColorScheme =
    KiyoriBrowserDarkColorScheme.copy(
        primary = Color(0xFF90CAF9),
        onPrimary = Color(0xFF0A2638),
        primaryContainer = Color(0xFF0B5F94),
        onPrimaryContainer = Color(0xFFD8ECFF),
        inversePrimary = Color(0xFF1E88E5),
        secondary = Color(0xFFB9CBD4),
        onSecondary = Color(0xFF20343D),
        secondaryContainer = Color(0xFF374D57),
        onSecondaryContainer = Color(0xFFE4EDF1),
        primaryFixed = Color(0xFFD8ECFF),
        primaryFixedDim = Color(0xFF90CAF9),
        onPrimaryFixed = Color(0xFF12324A),
        onPrimaryFixedVariant = Color(0xFF0B5F94),
        secondaryFixed = Color(0xFFE4EDF1),
        secondaryFixedDim = Color(0xFFB9CBD4),
        onSecondaryFixed = Color(0xFF20343D),
        onSecondaryFixedVariant = Color(0xFF374D57),
    )

fun resolveThemeColorScheme(
    context: Context,
    snapshot: ThemePreferenceSnapshot,
): ColorScheme =
    resolveThemeColorScheme(darkTheme = resolveDarkTheme(context, snapshot))

fun resolveThemeColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) KiyoriDarkColorScheme else KiyoriLightColorScheme

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
