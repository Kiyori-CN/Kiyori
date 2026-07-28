package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView

internal class WebSessionWebViewHost {
    private var container: FrameLayout? = null
    private var activeWebView: WebView? = null

    fun attachContainer(target: FrameLayout) {
        container = target
        reattach()
    }

    fun detachContainer(target: FrameLayout) {
        if (container === target) {
            target.removeAllViews()
            container = null
        }
    }

    fun setActiveWebView(webView: WebView?) {
        if (activeWebView !== webView) {
            detachActiveWebView()
        }
        activeWebView = webView
        reattach()
    }

    fun currentWebView(): WebView? = activeWebView

    fun isAssignedTo(webView: WebView?): Boolean = activeWebView === webView

    fun detachActiveWebView(): WebView? {
        val detached = activeWebView
        if (detached != null && detached.parent === container) {
            container?.removeView(detached)
        }
        activeWebView = null
        return detached
    }

    fun clear() {
        container?.removeAllViews()
        container = null
        activeWebView = null
    }

    private fun reattach() {
        val target = container ?: return
        val webView = activeWebView

        if (webView == null) {
            target.removeAllViews()
            return
        }

        val parent = webView.parent
        if (parent is ViewGroup && parent !== target) {
            parent.removeView(webView)
            check(webView.parent == null) {
                "Active WebView still has a parent after presentation detach"
            }
        }

        if (target.childCount == 1 && target.getChildAt(0) === webView) {
            return
        }

        target.removeAllViews()
        if (webView.parent == null) {
            target.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }
}
