package com.ai.assistance.operit.util

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("MissingOnRenderProcessGone") // The callback below is the shared implementation.
internal open class RenderProcessSafeWebViewClient(
    private val ownerTag: String,
) : WebViewClient() {
    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean = handleWebViewRenderProcessGone(view, detail, ownerTag)
}

/**
 * A terminated renderer cannot be reused. Remove its WebView from the view tree before
 * destroying it so the renderer failure is contained instead of crashing the host process.
 */
internal fun handleWebViewRenderProcessGone(
    view: WebView?,
    detail: RenderProcessGoneDetail?,
    ownerTag: String,
): Boolean {
    AppLogger.e(
        ownerTag,
        "WebView renderer terminated: didCrash=${detail?.didCrash() == true}",
    )
    view ?: return true
    try {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.destroy()
    } catch (error: Exception) {
        AppLogger.e(ownerTag, "Failed to dispose terminated WebView", error)
    }
    return true
}
