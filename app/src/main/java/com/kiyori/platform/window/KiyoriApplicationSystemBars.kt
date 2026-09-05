package com.kiyori.platform.window

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 应用进程根界面的 edge-to-edge 与 system-bar 唯一副作用 owner。 */
@Composable
fun KiyoriApplicationSystemBars(
    darkTheme: Boolean,
    navigationBarColor: Color,
    statusBarHidden: Boolean,
) {
    val view = LocalView.current
    // 必须在 composition 读取，页面声明变化才能重新调度唯一 Window 副作用。
    val darkStatusBarIcons = LocalKiyoriStatusBarAppearance.current.darkIcons ?: !darkTheme
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as ComponentActivity
            val window = activity.window
            val transparentBarColor = android.graphics.Color.TRANSPARENT
            val statusBarStyle = if (darkStatusBarIcons) {
                SystemBarStyle.light(transparentBarColor, transparentBarColor)
            } else {
                SystemBarStyle.dark(transparentBarColor)
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
