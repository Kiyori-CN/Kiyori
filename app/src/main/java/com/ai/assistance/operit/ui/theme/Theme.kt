package com.ai.assistance.operit.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/**
 * Kiyori 的应用根主题只负责浅色、深色和跟随系统三种模式。
 *
 * AI 对话背景与局部外观由 AIChatScreen 自己持有；如果继续在根层绘制背景，设置页和工具页会
 * 被迫进入透明 Surface，并再次形成一套能够影响全应用的用户配色路径。
 */
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
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
    val colorScheme = resolveThemeColorScheme(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as ComponentActivity
            val window = activity.window
            val transparentBarColor = android.graphics.Color.TRANSPARENT
            val statusBarStyle =
                if (darkTheme) {
                    SystemBarStyle.dark(transparentBarColor)
                } else {
                    SystemBarStyle.light(transparentBarColor, transparentBarColor)
                }
            val navigationBarColor = colorScheme.background.toArgb()
            val navigationBarStyle =
                if (darkTheme) {
                    SystemBarStyle.dark(navigationBarColor)
                } else {
                    SystemBarStyle.light(navigationBarColor, navigationBarColor)
                }

            activity.enableEdgeToEdge(statusBarStyle, navigationBarStyle)
            val insetsController =
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (statusBarHidden) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = customTypography,
    ) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                        .layerBackdrop(liquidGlassBackdrop)
                        .then(
                            if (waterGlassState != null) {
                                Modifier.liquefiable(waterGlassState)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                content()
            }
        }
    }
}
