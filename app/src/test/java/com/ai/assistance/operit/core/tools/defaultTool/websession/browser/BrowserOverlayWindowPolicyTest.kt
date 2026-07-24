package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserOverlayWindowPolicyTest {
    @Test
    fun `expanded overlay draws through system bar bounds`() {
        val flags = BrowserOverlayWindowPolicy.expandedFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }

    @Test
    fun `only pre Android 13 back key up is handled by legacy path`() {
        assertTrue(
            BrowserOverlayWindowPolicy.shouldHandleLegacyBack(
                sdkInt = Build.VERSION_CODES.S_V2,
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                isCanceled = false,
            )
        )
        assertFalse(
            BrowserOverlayWindowPolicy.shouldHandleLegacyBack(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_UP,
                isCanceled = false,
            )
        )
        assertFalse(
            BrowserOverlayWindowPolicy.shouldHandleLegacyBack(
                sdkInt = Build.VERSION_CODES.S_V2,
                keyCode = KeyEvent.KEYCODE_BACK,
                action = KeyEvent.ACTION_DOWN,
                isCanceled = false,
            )
        )
    }

    @Test
    fun `minimized host cannot accept input`() {
        val flags = BrowserOverlayWindowPolicy.minimizedFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }
}
