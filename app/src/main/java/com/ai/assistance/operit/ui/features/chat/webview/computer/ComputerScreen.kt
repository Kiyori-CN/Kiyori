package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.rememberTerminalEnv
import com.ai.assistance.operit.terminal.main.TerminalScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ComputerScreen(
    systemBackEnabled: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val rootView = LocalView.current

    // CanvasTerminalView owns a native input connection. When this overlay is removed, Compose
    // focus cleanup alone does not necessarily release that connection before the next chat frame;
    // explicitly hide it at the window boundary to prevent a stale terminal IME and blank chat
    // viewport after returning from the terminal.
    DisposableEffect(rootView) {
        onDispose {
            rootView.clearFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(rootView.windowToken, 0)
        }
    }
    
    // Create a TerminalManager and TerminalEnv instance for the terminal
    val terminalManager = remember { TerminalManager.getInstance(context) }
    val terminalEnv = rememberTerminalEnv(terminalManager)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            // SurfaceView 在厂商合成或 setup 路由切换的瞬间可能暂时没有可见内容；
            // 宿主必须保持不透明，避免下层 AI 对话残影露出。
            .background(Color.Black)
            // A passive pointer node keeps hit testing on this foreground sibling. It must not
            // consume sub-slop movement: setup/settings need vertical drag accumulation and the
            // ancestor Shell pager intentionally owns horizontal navigation.
            .isolateTerminalPointerPath()
    ) {
        // Show the terminal interface instead of the web desktop
        TerminalScreen(
            env = terminalEnv,
            // The terminal keeps the window stationary and owns its local IME layout. The AI
            // chat host owns the Activity soft-input mode, so the embedded terminal must not
            // restore the manifest mode when it leaves composition.
            useLocalImeHandling = true,
            manageHostWindowSoftInputMode = false,
            systemBackEnabled = systemBackEnabled,
            onClose = onClose,
        )
    }
}

private fun Modifier.isolateTerminalPointerPath(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.changes.none { change -> change.pressed }) {
                break
            }
        }
    }
}
