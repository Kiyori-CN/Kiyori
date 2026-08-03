package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/**
 * Keeps workspace WebView gestures inside the preview until the gesture ends and exposes a real
 * click contract for accessibility services.
 */
internal class ParentInterceptingWebView(context: Context) : WebView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMoved = false
    private var hadMultiTouch = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touchDownX = event.x
                touchDownY = event.y
                touchMoved = false
                hadMultiTouch = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                touchMoved = true
                hadMultiTouch = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (
                    abs(event.x - touchDownX) > touchSlop ||
                        abs(event.y - touchDownY) > touchSlop
                ) {
                    touchMoved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!touchMoved && !hadMultiTouch) {
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }

        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touchMoved = false
            hadMultiTouch = false
        }
        return handled
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
