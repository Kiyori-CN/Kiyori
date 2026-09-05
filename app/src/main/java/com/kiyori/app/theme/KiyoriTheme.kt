package com.kiyori.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.LocalLiquidGlassBackdrop
import com.ai.assistance.operit.ui.theme.LocalWaterGlassState
import com.ai.assistance.operit.ui.theme.createCustomTypography
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import com.kiyori.design.theme.resolveKiyoriColorScheme
import com.kiyori.platform.window.KiyoriApplicationSystemBars
import com.kiyori.platform.window.KiyoriStatusBarAppearanceScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/**
 * Kiyori 应用根主题宿主。
 *
 * 这里集中读取全局主题与字体偏好并创建 Glass 状态，纯 Material 组合和 Android system-bar
 * 副作用分别委派给 design 与 platform owner，避免再次形成并行状态源。
 */
@Composable
fun KiyoriTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = false)
    val themeMode by
        preferencesManager.themeMode.collectAsState(
            initial = UserPreferencesManager.THEME_MODE_LIGHT,
        )
    val statusBarHidden by preferencesManager.statusBarHidden.collectAsState(initial = false)

    val useCustomFont by preferencesManager.useCustomFont.collectAsState(initial = false)
    val fontType by
        preferencesManager.fontType.collectAsState(
            initial = UserPreferencesManager.FONT_TYPE_SYSTEM,
        )
    val systemFontName by
        preferencesManager.systemFontName.collectAsState(
            initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT,
        )
    val customFontPath by preferencesManager.customFontPath.collectAsState(initial = null)
    val fontScale by preferencesManager.fontScale.collectAsState(initial = 1.0f)
    val customTypography =
        remember(useCustomFont, fontType, systemFontName, customFontPath, fontScale) {
            createCustomTypography(
                context = context,
                useCustomFont = useCustomFont,
                fontType = fontType,
                systemFontName = systemFontName,
                customFontPath = customFontPath,
                fontScale = fontScale,
            )
        }

    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        if (useSystemTheme) {
            systemDarkTheme
        } else {
            themeMode == UserPreferencesManager.THEME_MODE_DARK
        }
    val colorScheme = resolveKiyoriColorScheme(darkTheme)

    val liquidGlassBackdrop = rememberLayerBackdrop()
    val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null
    KiyoriStatusBarAppearanceScope {
        KiyoriApplicationSystemBars(
            darkTheme = darkTheme,
            navigationBarColor = colorScheme.background,
            statusBarHidden = statusBarHidden,
        )
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            com.kiyori.design.theme.KiyoriTheme(
                colorScheme = colorScheme,
                modifier =
                    Modifier
                        .layerBackdrop(liquidGlassBackdrop)
                        .then(
                            if (waterGlassState != null) {
                                Modifier.liquefiable(waterGlassState)
                            } else {
                                Modifier
                            },
                        ),
                typography = customTypography,
                content = content,
            )
        }
    }
}
