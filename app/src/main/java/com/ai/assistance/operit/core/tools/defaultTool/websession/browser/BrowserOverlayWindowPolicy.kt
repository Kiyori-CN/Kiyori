package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager

internal object BrowserOverlayWindowPolicy {
    fun expandedFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    fun minimizedFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    fun usesOnBackInvokedDispatcher(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU

    fun shouldHandleLegacyBack(
        sdkInt: Int,
        keyCode: Int,
        action: Int,
        isCanceled: Boolean,
    ): Boolean =
        !usesOnBackInvokedDispatcher(sdkInt) &&
            keyCode == KeyEvent.KEYCODE_BACK &&
            action == KeyEvent.ACTION_UP &&
            !isCanceled
}
