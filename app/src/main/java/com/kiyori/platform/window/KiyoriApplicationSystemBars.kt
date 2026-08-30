package com.kiyori.platform.window

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private var statusBarDarkIconsOverride by mutableStateOf<Boolean?>(null)

/** 页面只声明状态栏图标明暗，实际 Window 写入仍由 KiyoriApplicationSystemBars 统一完成。 */
@Composable
fun KiyoriStatusBarAppearanceOverride(darkIcons: Boolean?) {
    DisposableEffect(darkIcons) {
        statusBarDarkIconsOverride = darkIcons
        onDispose {
            if (statusBarDarkIconsOverride == darkIcons) {
                statusBarDarkIconsOverride = null
            }
        }
    }
}

/** 应用进程根界面的 edge-to-edge 与 system-bar 唯一副作用 owner。 */
@Composable
fun KiyoriApplicationSystemBars(
    darkTheme: Boolean,
    navigationBarColor: Color,
    statusBarHidden: Boolean,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as ComponentActivity
            val window = activity.window
            val transparentBarColor = android.graphics.Color.TRANSPARENT
            val statusBarStyle = when (statusBarDarkIconsOverride) {
                true -> SystemBarStyle.light(transparentBarColor, transparentBarColor)
                false -> SystemBarStyle.dark(transparentBarColor)
                null -> if (darkTheme) {
                    SystemBarStyle.dark(transparentBarColor)
                } else {
                    SystemBarStyle.light(transparentBarColor, transparentBarColor)
                }
            }
            val navigationBarArgb = navigationBarColor.toArgb()
            val navigationBarStyle =
                if (darkTheme) {
                    SystemBarStyle.dark(navigationBarArgb)
                } else {
                    SystemBarStyle.light(navigationBarArgb, navigationBarArgb)
                }

            activity.enableEdgeToEdge(statusBarStyle, navigationBarStyle)
            val insetsController =
                WindowCompat.getInsetsController(window, window.decorView)
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
}
