package com.kiyori.platform.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal class KiyoriStatusBarAppearanceState {
    private data class Request(val owner: Any, val darkIcons: Boolean?)

    private var requests by mutableStateOf(emptyList<Request>())

    val darkIcons: Boolean?
        get() = requests.lastOrNull()?.darkIcons

    fun update(owner: Any, darkIcons: Boolean?) {
        val index = requests.indexOfFirst { it.owner === owner }
        val request = Request(owner, darkIcons)
        // 更新已有页面不能让它越过后来进入的页面；层级只由声明的进入/退出改变。
        requests = if (index < 0) {
            requests + request
        } else {
            requests.toMutableList().apply { this[index] = request }
        }
    }

    fun remove(owner: Any) {
        requests = requests.filterNot { it.owner === owner }
    }
}

internal val LocalKiyoriStatusBarAppearance = staticCompositionLocalOf<KiyoriStatusBarAppearanceState> {
    error("Status bar appearance requires a Kiyori root theme")
}

@Composable
internal fun KiyoriStatusBarAppearanceScope(content: @Composable () -> Unit) {
    val appearance = remember { KiyoriStatusBarAppearanceState() }
    CompositionLocalProvider(LocalKiyoriStatusBarAppearance provides appearance, content = content)
}

/** 页面只声明图标明暗；每个根窗口持有自己的声明，Window 写入由 SystemBars 统一完成。 */
@Composable
fun KiyoriStatusBarAppearanceOverride(darkIcons: Boolean?) {
    val appearance = LocalKiyoriStatusBarAppearance.current
    val owner = remember { Any() }
    SideEffect { appearance.update(owner, darkIcons) }
    DisposableEffect(appearance, owner) {
        onDispose { appearance.remove(owner) }
    }
}
