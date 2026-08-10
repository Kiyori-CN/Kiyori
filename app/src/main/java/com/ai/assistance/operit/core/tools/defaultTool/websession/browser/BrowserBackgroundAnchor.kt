package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import com.ai.assistance.operit.util.AppLogger

/**
 * Keeps the active WebView attached while Browser Home is not composed.
 *
 * This object owns no browser state. The WindowManager root is invisible, non-focusable and
 * non-touchable; its only child is the same WebView owned by the active
 * StandardBrowserSessionTools WebSession. Its layout follows the active session viewport so page
 * JavaScript and browser tools observe the same geometry.
 */
internal class BrowserBackgroundAnchor(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val webViewHost = WebSessionWebViewHost()
    private val container =
        FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = false
            isFocusable = false
            webViewHost.attachContainer(this)
        }

    private var params: WindowManager.LayoutParams? = null
    private var viewportSize: BrowserViewportSize? = null

    val isAttached: Boolean
        get() = params != null

    val isWindowAttachedToRoot: Boolean
        get() = container.isAttachedToWindow

    fun ensureAttached(x: Int, y: Int): Boolean {
        if (isAttached) {
            moveTo(x, y)
            return true
        }
        // WindowManager.removeView() completes through ViewRoot on the render pipeline. Re-adding
        // the same root before its detach is observed can overlap two Surface lifecycles.
        if (container.isAttachedToWindow) {
            return false
        }
        val layoutParams = createLayoutParams(x, y)
        windowManager.addView(container, layoutParams)
        params = layoutParams
        return true
    }

    fun setActiveWebView(webView: WebView?) {
        webViewHost.setActiveWebView(webView)
    }

    fun setViewportSize(width: Int?, height: Int?) {
        require((width == null) == (height == null)) {
            "Viewport width and height must be set together"
        }
        viewportSize =
            if (width == null) {
                null
            } else {
                BrowserViewportPolicy.requestedSize(width, requireNotNull(height))
            }
        webViewHost.setViewportSize(width, height)
        val size = currentWindowSize()
        val layoutParams = params ?: return
        if (layoutParams.width == size.width && layoutParams.height == size.height) {
            return
        }
        layoutParams.width = size.width
        layoutParams.height = size.height
        if (container.windowToken != null) {
            windowManager.updateViewLayout(container, layoutParams)
        }
    }

    fun detachActiveWebView(): WebView? = webViewHost.detachActiveWebView()

    fun isAssignedTo(webView: WebView?): Boolean = webViewHost.isAssignedTo(webView)

    fun moveTo(x: Int, y: Int) {
        val layoutParams = params ?: return
        if (layoutParams.x == x && layoutParams.y == y) {
            return
        }
        layoutParams.x = x
        layoutParams.y = y
        if (container.windowToken != null) {
            windowManager.updateViewLayout(container, layoutParams)
        }
    }

    fun detachWindow() {
        if (!isAttached) {
            return
        }
        windowManager.removeView(container)
        params = null
    }

    fun destroy() {
        webViewHost.clear()
        try {
            detachWindow()
        } catch (error: Exception) {
            AppLogger.w("BrowserBackgroundAnchor", "Failed to destroy browser background anchor", error)
            params = null
        }
    }

    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val size = currentWindowSize()
        return WindowManager.LayoutParams(
            size.width,
            size.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BrowserBackgroundAnchorPolicy.anchorFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun currentWindowSize(): BrowserViewportSize =
        BrowserViewportPolicy.hostSize(
            requestedWidth = viewportSize?.width,
            requestedHeight = viewportSize?.height,
            defaultWidth = appContext.resources.displayMetrics.widthPixels,
            defaultHeight = appContext.resources.displayMetrics.heightPixels,
        )
}
